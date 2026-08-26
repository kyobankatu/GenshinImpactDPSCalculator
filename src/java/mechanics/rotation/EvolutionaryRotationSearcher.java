package mechanics.rotation;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Deterministic legal evolutionary sequence search under an exact call budget. */
public final class EvolutionaryRotationSearcher implements RotationSearchStrategy {
    @Override
    public Result search(
            Supplier<? extends RotationEnvironment> environmentFactory,
            RotationSearchConfig config) {
        if (environmentFactory == null || config == null) {
            throw new IllegalArgumentException("environmentFactory and config are required");
        }
        RotationSearchSupport.Budget budget = new RotationSearchSupport.Budget(
                config.simulatorCallBudget);
        TopKTrajectoryArchive feasibleArchive = new TopKTrajectoryArchive(config.archiveSize);
        TopKTrajectoryArchive diagnosticArchive = new TopKTrajectoryArchive(config.archiveSize);
        RotationSearchStatistics.Mutable statistics = new RotationSearchStatistics.Mutable();
        Random random = new Random(config.seed);
        boolean cancelled = false;
        List<int[]> initialSeeds = config.getInitialSeeds();

        try (RotationEnvironment environment = requireEnvironment(environmentFactory)) {
            RotationSearchSupport.requireSearchAdmission(environment);
            for (int[] initialSeed : initialSeeds) {
                RotationSearchSupport.Evaluation evaluation;
                RotationEvaluationMode mode = initialSeed.length == 0
                        ? RotationEvaluationMode.REPAIR : RotationEvaluationMode.STRICT;
                try {
                    evaluation = RotationSearchSupport.evaluate(
                            environment,
                            initialSeed,
                            config,
                            random,
                            budget,
                            mode);
                } catch (IllegalArgumentException exception) {
                    statistics.recordRejectedTrajectory();
                    throw exception;
                }
                if (evaluation == null) {
                    break;
                }
                recordEvaluation(
                        evaluation,
                        true,
                        feasibleArchive,
                        diagnosticArchive,
                        statistics);
                if (config.cancellation.getAsBoolean()) {
                    cancelled = true;
                    break;
                }
            }
            int initialCandidates = 0;
            boolean initialPopulationComplete = true;
            while (!cancelled && initialCandidates < config.populationSize) {
                RotationSearchSupport.Evaluation evaluation = RotationSearchSupport.evaluate(
                        environment,
                        randomSequence(random, config.maxActions),
                        config,
                        random,
                        budget,
                        RotationEvaluationMode.REPAIR);
                if (evaluation == null) {
                    break;
                }
                initialCandidates++;
                initialPopulationComplete &= evaluation.trajectory.isComplete();
                recordEvaluation(
                        evaluation,
                        false,
                        feasibleArchive,
                        diagnosticArchive,
                        statistics);
                if (config.cancellation.getAsBoolean()) {
                    cancelled = true;
                }
            }
            if (initialCandidates == config.populationSize && initialPopulationComplete) {
                statistics.recordCompletedPopulation();
            }
            while (!cancelled
                    && initialCandidates == config.populationSize
                    && initialPopulationComplete) {
                List<ExpertTrajectory> parents = feasibleArchive.size() > 0
                        ? feasibleArchive.trajectories() : diagnosticArchive.trajectories();
                if (parents.isEmpty()) {
                    break;
                }
                int generationCandidates = 0;
                boolean generationComplete = true;
                while (generationCandidates < config.populationSize) {
                    int parentLimit = Math.min(
                            Math.min(config.populationSize, parents.size()),
                            Math.max(1, config.eliteCount));
                    int[] parent = parents.get(random.nextInt(parentLimit)).getActions();
                    RotationSearchSupport.Evaluation evaluation = RotationSearchSupport.evaluate(
                            environment,
                            mutate(parent, random, config.maxActions),
                            config,
                            random,
                            budget,
                            RotationEvaluationMode.REPAIR);
                    if (evaluation == null) {
                        break;
                    }
                    generationCandidates++;
                    generationComplete &= evaluation.trajectory.isComplete();
                    recordEvaluation(
                            evaluation,
                            false,
                            feasibleArchive,
                            diagnosticArchive,
                            statistics);
                    if (config.cancellation.getAsBoolean()) {
                        cancelled = true;
                        break;
                    }
                }
                if (generationCandidates != config.populationSize || !generationComplete) {
                    break;
                }
                statistics.recordCompletedGeneration();
            }
            while (!cancelled && budget.remaining() > 0) {
                RotationSearchSupport.Evaluation evaluation = RotationSearchSupport.evaluate(
                        environment,
                        randomSequence(random, config.maxActions),
                        config,
                        random,
                        budget,
                        RotationEvaluationMode.REPAIR);
                if (evaluation == null) {
                    break;
                }
                recordEvaluation(
                        evaluation,
                        false,
                        feasibleArchive,
                        diagnosticArchive,
                        statistics);
                if (config.cancellation.getAsBoolean()) {
                    cancelled = true;
                }
            }
        }
        return new Result(
                feasibleArchive.trajectories(),
                diagnosticArchive.trajectories(),
                statistics.freeze(budget.used()),
                cancelled);
    }

    private void recordEvaluation(
            RotationSearchSupport.Evaluation evaluation,
            boolean seed,
            TopKTrajectoryArchive feasibleArchive,
            TopKTrajectoryArchive diagnosticArchive,
            RotationSearchStatistics.Mutable statistics) {
        boolean diagnostic = !RotationTrajectoryRanker.INSTANCE.isPublishable(
                evaluation.trajectory);
        if (diagnostic) {
            diagnosticArchive.add(evaluation.trajectory);
        } else {
            feasibleArchive.add(evaluation.trajectory);
        }
        statistics.recordEvaluation(
                evaluation.trajectory,
                evaluation.repairedActions,
                seed,
                diagnostic);
    }

    private RotationEnvironment requireEnvironment(
            Supplier<? extends RotationEnvironment> environmentFactory) {
        RotationEnvironment environment = environmentFactory.get();
        if (environment == null) {
            throw new IllegalArgumentException("environmentFactory returned null");
        }
        return environment;
    }

    private int[] randomSequence(Random random, int maxActions) {
        int length = 1 + random.nextInt(Math.max(1, maxActions));
        int[] actions = new int[length];
        for (int index = 0; index < actions.length; index++) {
            actions[index] = random.nextInt(PolicyAction.SIZE);
        }
        return actions;
    }

    private int[] mutate(int[] parent, Random random, int maxActions) {
        int[] child = parent.clone();
        switch (random.nextInt(5)) {
            case 0:
                return insert(child, random, maxActions);
            case 1:
                return delete(child, random);
            case 2:
                child[random.nextInt(child.length)] = random.nextInt(PolicyAction.SIZE);
                return child;
            case 3:
                return move(child, random);
            default:
                return reverseSubsequence(child, random);
        }
    }

    private int[] insert(int[] actions, Random random, int maxActions) {
        if (actions.length >= maxActions) {
            int[] replaced = actions.clone();
            replaced[random.nextInt(replaced.length)] = random.nextInt(PolicyAction.SIZE);
            return replaced;
        }
        int offset = random.nextInt(actions.length + 1);
        int[] result = new int[actions.length + 1];
        System.arraycopy(actions, 0, result, 0, offset);
        result[offset] = random.nextInt(PolicyAction.SIZE);
        System.arraycopy(actions, offset, result, offset + 1, actions.length - offset);
        return result;
    }

    private int[] delete(int[] actions, Random random) {
        if (actions.length == 1) {
            return actions.clone();
        }
        return deleteAt(actions, random.nextInt(actions.length));
    }

    private int[] deleteAt(int[] actions, int offset) {
        int[] result = new int[actions.length - 1];
        System.arraycopy(actions, 0, result, 0, offset);
        System.arraycopy(actions, offset + 1, result, offset, actions.length - offset - 1);
        return result;
    }

    private int[] move(int[] actions, Random random) {
        if (actions.length < 2) {
            return actions.clone();
        }
        int from = random.nextInt(actions.length);
        int to = random.nextInt(actions.length);
        int value = actions[from];
        int[] result = deleteAt(actions, from);
        int[] expanded = new int[actions.length];
        int insertion = Math.min(to, result.length);
        System.arraycopy(result, 0, expanded, 0, insertion);
        expanded[insertion] = value;
        System.arraycopy(result, insertion, expanded, insertion + 1, result.length - insertion);
        return expanded;
    }

    private int[] reverseSubsequence(int[] actions, Random random) {
        int[] result = actions.clone();
        if (result.length < 2) {
            return result;
        }
        int first = random.nextInt(result.length);
        int second = random.nextInt(result.length);
        int start = Math.min(first, second);
        int end = Math.max(first, second);
        while (start < end) {
            int value = result[start];
            result[start++] = result[end];
            result[end--] = value;
        }
        return result;
    }
}
