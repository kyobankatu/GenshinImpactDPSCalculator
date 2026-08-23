package sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetWriter;
import mechanics.rotation.ExpertTrajectory;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Generates one transactional expert dataset campaign shard set. */
public class GenerateRotationDataset {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "output/rotation_dataset");
        String partyName = args.length > 1 ? args[1] : "RaidenParty";
        String strategyName = args.length > 2 ? args[2] : "evolution";
        int callBudget = args.length > 3 ? Integer.parseInt(args[3]) : 2000;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 1234L;
        Collection<PartyDefinition> definitions = "all".equalsIgnoreCase(partyName)
                ? PartyCatalog.rlEnabled()
                : List.of(PartyCatalog.require(partyName));
        if (definitions.size() > 1 && args.length > 5) {
            throw new IllegalArgumentException(
                    "A campaign dataset must use each definition's declared split");
        }
        List<ExpertDatasetRecord> records = new ArrayList<>();
        int totalSimulatorCalls = 0;
        int scenarioIndex = 0;
        for (PartyDefinition definition : definitions) {
            long scenarioSeed = seed + scenarioIndex++;
            String split = args.length > 5
                    ? args[5]
                    : definition.datasetSplit().getWireName();
            RotationScenario scenario = RotationScenario.forParty(
                    definition,
                    new EpisodeConfig(),
                    definition.rotationCycleSeconds(),
                    1,
                    scenarioSeed,
                    RotationObjective.cyclicDamage());
            RotationSearchStrategy.Result result = strategy(strategyName).search(
                    () -> new BattleRotationEnvironment(scenario),
                    RotationSearchConfig.defaults(scenarioSeed, callBudget));
            totalSimulatorCalls += result.simulatorCalls;
            for (int rank = 0; rank < result.archive.size(); rank++) {
                ExpertTrajectory trajectory = result.archive.get(rank);
                records.add(ExpertDatasetRecord.capture(
                        definition.name() + "-" + scenarioSeed + "-" + rank,
                        scenario,
                        definition.name(),
                        split,
                        callBudget,
                        rank,
                        trajectory.getActions()));
            }
        }
        ExpertDatasetWriter.write(output, records, 64);
        System.out.println("dataset=" + output.resolve(ExpertDatasetWriter.MANIFEST_FILE));
        System.out.println("records=" + records.size());
        System.out.println("simulatorCalls=" + totalSimulatorCalls);
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
