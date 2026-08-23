package mechanics.rotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/** Snapshot-backed deterministic MCTS with legal policy priors and call limits. */
public final class MctsRotationSearcher implements RotationSearchStrategy {
    @Override
    public Result search(
            Supplier<? extends RotationEnvironment> environmentFactory,
            RotationSearchConfig config) {
        if (environmentFactory == null || config == null) {
            throw new IllegalArgumentException("environmentFactory and config are required");
        }
        RotationSearchSupport.Budget budget = new RotationSearchSupport.Budget(
                config.simulatorCallBudget);
        TopKTrajectoryArchive archive = new TopKTrajectoryArchive(config.archiveSize);
        Random random = new Random(config.seed);
        boolean cancelled = false;

        try (RotationEnvironment environment = requireEnvironment(environmentFactory)) {
            RotationStep rootStep = environment.reset();
            Node root = new Node(
                    null,
                    -1,
                    rootStep,
                    environment.snapshot(),
                    0,
                    config.prior);
            while (budget.remaining() > 0) {
                int callsBefore = budget.used();
                if (!restore(environment, root, budget)) {
                    break;
                }
                Node node = root;
                List<Node> path = new ArrayList<>();
                path.add(root);

                while (!node.step.done && node.isFullyExpanded() && !node.children.isEmpty()) {
                    Node selected = selectChild(node, config.explorationConstant);
                    if (!restore(environment, selected, budget)) {
                        node = null;
                        break;
                    }
                    node = selected;
                    path.add(node);
                }
                if (node == null) {
                    break;
                }

                if (!node.step.done && node.depth < config.maxActions && budget.consume(1)) {
                    int actionId = node.sampleUnexpandedAction(random);
                    RotationStep expandedStep = environment.step(actionId);
                    if (!expandedStep.validAction) {
                        throw new IllegalStateException("MCTS expanded an illegal action");
                    }
                    Node child = new Node(
                            node,
                            actionId,
                            expandedStep,
                            environment.snapshot(),
                            node.depth + 1,
                            config.prior);
                    node.children.put(actionId, child);
                    node = child;
                    path.add(node);
                }

                List<Integer> actions = node.pathActions();
                RotationStep rolloutStep = node.step;
                while (!rolloutStep.done
                        && actions.size() < config.maxActions
                        && budget.consume(1)) {
                    int actionId = RotationSearchSupport.sampleLegal(
                            rolloutStep, config.prior, random);
                    rolloutStep = environment.step(actionId);
                    if (!rolloutStep.validAction) {
                        throw new IllegalStateException("MCTS rollout executed an illegal action");
                    }
                    actions.add(actionId);
                }
                if (!actions.isEmpty() && budget.used() > callsBefore) {
                    ExpertTrajectory trajectory = new ExpertTrajectory(
                            actions.stream().mapToInt(Integer::intValue).toArray(),
                            rolloutStep.objective,
                            rolloutStep.stateHash,
                            rolloutStep.done,
                            budget.used() - callsBefore);
                    archive.add(trajectory);
                    backpropagate(path, trajectory.getObjective().objectiveScore);
                }
                if (config.cancellation.getAsBoolean()) {
                    cancelled = true;
                    break;
                }
            }
        }
        return new Result(
                archive.best(),
                archive.trajectories(),
                budget.used(),
                cancelled);
    }

    private boolean restore(
            RotationEnvironment environment,
            Node node,
            RotationSearchSupport.Budget budget) {
        if (!budget.consume(node.depth)) {
            return false;
        }
        RotationStep restored = environment.restore(node.snapshot);
        if (restored.stateHash != node.step.stateHash) {
            throw new IllegalStateException("MCTS snapshot restore hash mismatch");
        }
        return true;
    }

    private Node selectChild(Node parent, double explorationConstant) {
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double minimumValue = Double.POSITIVE_INFINITY;
        double maximumValue = Double.NEGATIVE_INFINITY;
        for (Node child : parent.children.values()) {
            if (child.visits > 0) {
                double meanValue = child.totalValue / child.visits;
                minimumValue = Math.min(minimumValue, meanValue);
                maximumValue = Math.max(maximumValue, meanValue);
            }
        }
        for (Node child : parent.children.values()) {
            double meanValue = child.visits > 0 ? child.totalValue / child.visits : 0.0;
            double normalizedValue = maximumValue > minimumValue
                    ? (meanValue - minimumValue) / (maximumValue - minimumValue)
                    : 0.0;
            double exploration = explorationConstant
                    * child.priorProbability
                    * Math.sqrt(Math.max(1, parent.visits))
                    / (1.0 + child.visits);
            double score = normalizedValue + exploration;
            if (score > bestScore) {
                best = child;
                bestScore = score;
            }
        }
        if (best == null) {
            throw new IllegalStateException("MCTS parent has no selectable child");
        }
        return best;
    }

    private void backpropagate(List<Node> path, double value) {
        for (Node node : path) {
            node.visits++;
            node.totalValue += value;
        }
    }

    private RotationEnvironment requireEnvironment(
            Supplier<? extends RotationEnvironment> environmentFactory) {
        RotationEnvironment environment = environmentFactory.get();
        if (environment == null) {
            throw new IllegalArgumentException("environmentFactory returned null");
        }
        return environment;
    }

    private static final class Node {
        private final Node parent;
        private final int incomingAction;
        private final RotationStep step;
        private final RotationEnvironment.Snapshot snapshot;
        private final int depth;
        private final List<Integer> legalActions;
        private final double[] priorWeights;
        private final double priorProbability;
        private final Map<Integer, Node> children = new LinkedHashMap<>();
        private int visits;
        private double totalValue;

        private Node(
                Node parent,
                int incomingAction,
                RotationStep step,
                RotationEnvironment.Snapshot snapshot,
                int depth,
                ExpertPolicyPrior prior) {
            this.parent = parent;
            this.incomingAction = incomingAction;
            this.step = step.copy();
            this.snapshot = snapshot;
            this.depth = depth;
            this.legalActions = step.done
                    ? List.of() : RotationSearchSupport.legalActions(step);
            this.priorWeights = step.done
                    ? new double[step.legalActionMask.length]
                    : RotationSearchSupport.validatedWeights(step, prior);
            this.priorProbability = parent == null
                    ? 1.0 : normalizedPrior(parent, incomingAction);
        }

        private boolean isFullyExpanded() {
            return children.size() == legalActions.size();
        }

        private int sampleUnexpandedAction(Random random) {
            List<Integer> unexpanded = new ArrayList<>();
            for (int actionId : legalActions) {
                if (!children.containsKey(actionId)) {
                    unexpanded.add(actionId);
                }
            }
            return RotationSearchSupport.sampleFromActions(
                    step, unexpanded, priorWeights, random);
        }

        private List<Integer> pathActions() {
            List<Integer> reversed = new ArrayList<>();
            Node cursor = this;
            while (cursor.parent != null) {
                reversed.add(cursor.incomingAction);
                cursor = cursor.parent;
            }
            java.util.Collections.reverse(reversed);
            return reversed;
        }

        private static double normalizedPrior(Node parent, int actionId) {
            double total = 0.0;
            for (int legalAction : parent.legalActions) {
                total += parent.priorWeights[legalAction];
            }
            return parent.priorWeights[actionId] / total;
        }
    }
}
