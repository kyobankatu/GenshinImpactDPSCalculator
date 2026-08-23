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
        TopKTrajectoryArchive archive = new TopKTrajectoryArchive(config.archiveSize);
        Random random = new Random(config.seed);
        boolean cancelled = false;
        List<int[]> initialSeeds = config.getInitialSeeds();

        try (RotationEnvironment environment = requireEnvironment(environmentFactory)) {
            int seedIndex = 0;
            int candidateIndex = 0;
            while (budget.remaining() > 0) {
                int[] candidate;
                if (seedIndex < initialSeeds.size()) {
                    candidate = initialSeeds.get(seedIndex++).clone();
                } else if (archive.size() == 0
                        || candidateIndex % config.populationSize == 0) {
                    candidate = randomSequence(random, config.maxActions);
                } else {
                    List<ExpertTrajectory> parents = archive.trajectories();
                    int parentLimit = Math.min(
                            Math.min(config.populationSize, parents.size()),
                            Math.max(1, config.eliteCount));
                    int[] parent = parents.get(random.nextInt(parentLimit)).getActions();
                    candidate = mutate(parent, random, config.maxActions);
                }
                ExpertTrajectory trajectory = RotationSearchSupport.evaluate(
                        environment,
                        candidate,
                        config,
                        random,
                        budget);
                candidateIndex++;
                archive.add(trajectory);
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
