package sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mechanics.optimization.TotalOptimizationResult;
import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetProvenance;
import mechanics.rotation.ExpertDatasetWriter;
import mechanics.rotation.ExpertPolicyPrior;
import mechanics.rotation.ExpertTrajectory;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import mechanics.rotation.RotationSeedEvaluation;
import mechanics.rotation.RotationSourceCatalog;
import mechanics.rotation.RotationTeacherQualityReport;
import mechanics.rotation.RotationTeacherQualityReport.Arm;
import mechanics.rotation.RotationTeacherQualityReport.ScenarioResult;
import mechanics.rotation.SourcedRotationSeed;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Generates one transactional expert dataset campaign shard set. */
public class GenerateRotationDataset {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            throw new IllegalArgumentException(
                    "Usage: output party strategy budget seed split quality-report");
        }
        Path output = Path.of(args.length > 0 ? args[0] : "output/rotation_dataset");
        String partyName = args.length > 1 ? args[1] : "RaidenParty";
        String strategyName = args.length > 2 ? args[2] : "evolution";
        int callBudget = args.length > 3 ? Integer.parseInt(args[3]) : 2000;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 1234L;
        RotationTeacherQualityReport qualityReport =
                RotationTeacherQualityReport.read(Path.of(args[6]));
        RotationSourceCatalog sourceCatalog = RotationSourceCatalog.loadDefault();
        boolean campaign = "all".equalsIgnoreCase(partyName);
        Collection<PartyDefinition> definitions = "all".equalsIgnoreCase(partyName)
                ? PartyCatalog.rlEnabled()
                : List.of(PartyCatalog.require(partyName));
        List<ExpertDatasetRecord> records = new ArrayList<>();
        int totalSimulatorCalls = 0;
        int scenarioIndex = 0;
        for (PartyDefinition definition : definitions) {
            long scenarioSeed = seed + scenarioIndex++;
            String split = campaign
                    ? definition.datasetSplit().getWireName() : args[5];
            SourcedRotationSeed sourceSeed = sourceSeedFor(
                    sourceCatalog, definition, campaign);
            if (sourceSeed == null) {
                continue;
            }
            TotalOptimizationResult build = RotationSeedEvaluation.resolveBuild(sourceSeed);
            RotationScenario scenario = RotationScenario.forPartyBuild(
                    definition,
                    build,
                    new EpisodeConfig(),
                    definition.rotationCycleSeconds(),
                    2,
                    scenarioSeed,
                    RotationObjective.cyclicDamage());
            ScenarioResult quality = qualityReport.findScenario(scenario.getFingerprint());
            if (quality == null || !quality.isPublishable()) {
                if (campaign) {
                    continue;
                }
                quality = qualityReport.requirePublishable(scenario.getFingerprint());
            }
            Arm requestedArm = teacherArm(strategyName);
            if (quality.getRetainedTeacher() != requestedArm) {
                throw new IllegalArgumentException(
                        "Requested strategy does not match retained teacher for "
                                + definition.name());
            }
            if (quality.getRetainedTeacherSummary().getSimulatorCallsPerTrial()
                    != callBudget) {
                throw new IllegalArgumentException(
                        "Requested call budget does not match teacher evidence for "
                                + definition.name());
            }
            int[] humanActions = humanActions(
                    RotationSeedEvaluation.evaluate(
                            sourceSeed, definition, build, 2, scenarioSeed));
            int maxActions = Math.max(
                    humanActions.length,
                    (int) Math.ceil(scenario.getHorizonSeconds() / 0.1) + 1);
            RotationSearchConfig searchConfig = new RotationSearchConfig(
                    callBudget,
                    maxActions,
                    8,
                    12,
                    3,
                    Math.sqrt(2.0),
                    scenarioSeed,
                    ExpertPolicyPrior.uniform(),
                    () -> false,
                    List.of(humanActions));
            RotationSearchStrategy.Result result = strategy(strategyName).search(
                    () -> new BattleRotationEnvironment(scenario),
                    searchConfig);
            if (!result.publishable || result.simulatorCalls != callBudget) {
                throw new IllegalStateException(
                        "Qualified teacher did not reproduce a publishable exact-budget result");
            }
            totalSimulatorCalls += result.simulatorCalls;
            for (int rank = 0; rank < result.archive.size(); rank++) {
                ExpertTrajectory trajectory = result.archive.get(rank);
                String recordId = definition.name() + "-" + scenarioSeed + "-" + rank;
                ExpertDatasetProvenance provenance =
                        ExpertDatasetProvenance.capture(
                                sourceSeed,
                                definition,
                                build,
                                scenario,
                                quality,
                                result,
                                rank,
                                rank == 0
                                        ? List.of()
                                        : List.of(definition.name() + "-"
                                                + scenarioSeed + "-0"));
                records.add(ExpertDatasetRecord.capture(
                        recordId,
                        scenario,
                        definition.name(),
                        split,
                        callBudget,
                        rank,
                        provenance,
                        trajectory.getActions()));
            }
        }
        if (records.isEmpty()) {
            throw new IllegalStateException(
                    "Quality report admitted no dataset records for the request");
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

    private static Arm teacherArm(String name) {
        if ("evolution".equalsIgnoreCase(name)) {
            return Arm.UNGUIDED_EVOLUTIONARY;
        }
        if ("mcts".equalsIgnoreCase(name)) {
            return Arm.UNGUIDED_MCTS;
        }
        throw new IllegalArgumentException("Unknown search strategy: " + name);
    }

    private static SourcedRotationSeed sourceSeedFor(
            RotationSourceCatalog catalog,
            PartyDefinition definition,
            boolean campaign) {
        SourcedRotationSeed found = null;
        for (SourcedRotationSeed sourceSeed : catalog.getSeeds()) {
            if (!sourceSeed.isUsable()
                    || !sourceSeed.getPartyName().equals(definition.name())
                    || !definition.supportsExactSnapshotRestore()) {
                continue;
            }
            if (found != null) {
                throw new IllegalStateException(
                        "Multiple usable source seeds for " + definition.name());
            }
            found = sourceSeed;
        }
        if (found == null && !campaign) {
            throw new IllegalArgumentException(
                    "No usable source seed for " + definition.name());
        }
        return found;
    }

    private static int[] humanActions(RotationSeedEvaluation.Result replay) {
        List<Integer> actions = new ArrayList<>();
        for (RotationSeedEvaluation.CycleResult cycle : replay.cycles) {
            actions.addAll(cycle.executedActions);
        }
        return actions.stream().mapToInt(Integer::intValue).toArray();
    }
}
