package mechanics.rotation;

import java.util.ArrayList;
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
                            mutate(parent, random, config),
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

    private int[] mutate(
            int[] parent,
            Random random,
            RotationSearchConfig config) {
        List<RotationWaitGene> child = new ArrayList<>(
                RotationWaitGene.compress(
                        parent, config.maxWaitRunLength));
        switch (random.nextInt(6)) {
            case 0:
                insert(child, random, config);
                break;
            case 1:
                delete(child, random);
                break;
            case 2:
                replace(child, random, config);
                break;
            case 3:
                move(child, random);
                break;
            case 4:
                reverseSubsequence(child, random);
                break;
            default:
                resizeWait(child, random, config);
                break;
        }
        return RotationWaitGene.expand(
                child, config.maxActions, config.maxWaitRunLength);
    }

    private void insert(
            List<RotationWaitGene> genes,
            Random random,
            RotationSearchConfig config) {
        int available = config.maxActions - expandedLength(genes);
        if (available <= 0) {
            replace(genes, random, config);
            return;
        }
        genes.add(
                random.nextInt(genes.size() + 1),
                randomGene(random, config, available));
    }

    private void delete(List<RotationWaitGene> genes, Random random) {
        if (genes.size() > 1) {
            genes.remove(random.nextInt(genes.size()));
        }
    }

    private void replace(
            List<RotationWaitGene> genes,
            Random random,
            RotationSearchConfig config) {
        int index = random.nextInt(genes.size());
        int available = config.maxActions - expandedLength(genes)
                + genes.get(index).getRunLength();
        genes.set(index, randomGene(random, config, available));
    }

    private void move(List<RotationWaitGene> genes, Random random) {
        if (genes.size() < 2) {
            return;
        }
        RotationWaitGene gene = genes.remove(random.nextInt(genes.size()));
        genes.add(random.nextInt(genes.size() + 1), gene);
    }

    private void reverseSubsequence(
            List<RotationWaitGene> genes,
            Random random) {
        if (genes.size() < 2) {
            return;
        }
        int first = random.nextInt(genes.size());
        int second = random.nextInt(genes.size());
        int start = Math.min(first, second);
        int end = Math.max(first, second);
        while (start < end) {
            RotationWaitGene value = genes.get(start);
            genes.set(start++, genes.get(end));
            genes.set(end--, value);
        }
    }

    private void resizeWait(
            List<RotationWaitGene> genes,
            Random random,
            RotationSearchConfig config) {
        List<Integer> waits = new ArrayList<>();
        for (int index = 0; index < genes.size(); index++) {
            if (genes.get(index).getActionId()
                    == PolicyAction.WAIT_SHORT.getId()) {
                waits.add(index);
            }
        }
        if (waits.isEmpty()) {
            replace(genes, random, config);
            return;
        }
        int index = waits.get(random.nextInt(waits.size()));
        RotationWaitGene current = genes.get(index);
        int available = Math.min(
                config.maxWaitRunLength,
                config.maxActions - expandedLength(genes)
                        + current.getRunLength());
        int runLength = available == 1
                ? 1 : 1 + random.nextInt(available);
        genes.set(index, RotationWaitGene.of(
                PolicyAction.WAIT_SHORT.getId(), runLength));
    }

    private RotationWaitGene randomGene(
            Random random,
            RotationSearchConfig config,
            int available) {
        int actionId = random.nextInt(PolicyAction.SIZE);
        int maximum = Math.min(config.maxWaitRunLength, available);
        int runLength = actionId == PolicyAction.WAIT_SHORT.getId()
                && maximum > 1 ? 1 + random.nextInt(maximum) : 1;
        return RotationWaitGene.of(actionId, runLength);
    }

    private int expandedLength(List<RotationWaitGene> genes) {
        int length = 0;
        for (RotationWaitGene gene : genes) {
            length += gene.getRunLength();
        }
        return length;
    }
}
