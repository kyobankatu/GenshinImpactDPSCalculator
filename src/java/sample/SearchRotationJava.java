package sample;

import java.util.Arrays;

import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RecordedExpertPolicyPrior;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import simulation.party.PartyCatalog;

/** Runs one bounded expert rotation search from the command line. */
public class SearchRotationJava {
    public static void main(String[] args) throws Exception {
        String partyName = args.length > 0 ? args[0] : "RaidenParty";
        String strategyName = args.length > 1 ? args[1] : "evolution";
        int callBudget = args.length > 2 ? Integer.parseInt(args[2]) : 2000;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 1234L;
        RotationSearchStrategy strategy = strategy(strategyName);
        RotationScenario scenario = RotationScenario.forParty(
                PartyCatalog.require(partyName),
                new EpisodeConfig(),
                20.0,
                1,
                seed,
                RotationObjective.cyclicDamage());
        RotationSearchConfig config = RotationSearchConfig.defaults(seed, callBudget);
        if (args.length > 4) {
            config = config.withPrior(new RecordedExpertPolicyPrior(
                    java.nio.file.Path.of(args[4]), scenario.getFingerprint()));
        }
        RotationSearchStrategy.Result result = strategy.search(
                () -> new BattleRotationEnvironment(scenario),
                config);
        System.out.println("strategy=" + strategyName);
        System.out.println("party=" + partyName);
        System.out.println("simulatorCalls=" + result.simulatorCalls);
        System.out.println("objective=" + result.best.getObjective().objectiveScore);
        System.out.println("actions=" + Arrays.toString(result.best.getActions()));
        System.out.println("archiveSize=" + result.archive.size());
    }

    private static RotationSearchStrategy strategy(String name) {
        if ("evolution".equalsIgnoreCase(name)) {
            return new EvolutionaryRotationSearcher();
        }
        if ("mcts".equalsIgnoreCase(name)) {
            return new MctsRotationSearcher();
        }
        throw new IllegalArgumentException("Unknown search strategy: " + name);
    }
}
