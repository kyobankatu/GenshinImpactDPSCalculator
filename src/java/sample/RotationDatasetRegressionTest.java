package sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.ObservationEncoder;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.ExpertDatasetReader;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetProvenance;
import mechanics.rotation.ExpertDatasetWriter;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationStep;
import mechanics.rotation.RotationTraceCompletion;
import mechanics.rotation.RotationTraceDeduplicator;
import model.type.CharacterId;
import model.type.StatType;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Regression checks for transactional expert datasets and exact replay. */
public class RotationDatasetRegressionTest {
    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        TotalOptimizationResult build = fixtureBuild();
        RotationScenario scenario = scenario(build);
        int[] actions = legalActions(scenario);
        ExpertDatasetRecord first = ExpertDatasetRecord.capture(
                "tiny-0", scenario, "RaidenParty", "train", 16, 0,
                provenance(scenario, build, 0), actions);
        ExpertDatasetRecord second = ExpertDatasetRecord.capture(
                "tiny-1", scenario, "RaidenParty", "train", 16, 1,
                provenance(scenario, build, 1), actions);
        ExpertDatasetRecord third = ExpertDatasetRecord.capture(
                "tiny-2", scenario, "RaidenParty", "train", 16, 2,
                provenance(scenario, build, 2), actions);
        PartyDefinition secondDefinition = PartyCatalog.require(
                "HuTaoXianyunVaporize");
        TotalOptimizationResult secondBuild = PartyBuildResolver.require(secondDefinition);
        RotationScenario secondScenario = scenario(secondDefinition, secondBuild, 2468L);
        ExpertDatasetRecord fourth = ExpertDatasetRecord.capture(
                "hu-tao-0",
                secondScenario,
                secondDefinition.name(),
                "train",
                16,
                0,
                provenance(secondDefinition, secondScenario, secondBuild, 0),
                legalActions(secondScenario));
        assertCompressedMultiShardRoundTrip(List.of(first, second, third, fourth));
        assertManifestSurvivesRejectedWrite(first);
        assertBrokenLineageRejected(second);
        assertCorruptionRejected(first);
        assertInvalidRecordsRejected(first, scenario, build, actions);
        assertTraceCompletion(scenario, build, actions);
        assertTraceDeduplication();
        System.out.println("RotationDatasetRegressionTest passed");
    }

    private static void assertTraceCompletion(
            RotationScenario scenario,
            TotalOptimizationResult build,
            int[] terminalActions) {
        int[] partial = Arrays.copyOf(terminalActions, terminalActions.length - 1);
        int[] completed = RotationTraceCompletion.complete(scenario, partial);
        if (completed.length <= partial.length) {
            throw new AssertionError("Trace completion did not append terminal waits");
        }
        ExpertDatasetRecord.capture(
                "completed-trace",
                scenario,
                "RaidenParty",
                "train",
                16,
                0,
                provenance(scenario, build, 0),
                completed).replayAndValidate();
        expectFailure(
                () -> RotationTraceCompletion.complete(scenario, new int[0]),
                "empty trace completion");
    }

    private static void assertTraceDeduplication() {
        RotationTraceDeduplicator deduplicator = new RotationTraceDeduplicator();
        int[] root = new int[20];
        if (!deduplicator.tryRetain(root)) {
            throw new AssertionError("Trace deduplicator rejected its root");
        }
        root[0] = 1;
        if (deduplicator.tryRetain(root)) {
            throw new AssertionError("Trace deduplicator leaked its retained array");
        }
        int[] distinct = root.clone();
        distinct[1] = 1;
        if (!deduplicator.tryRetain(distinct)) {
            throw new AssertionError("Trace deduplicator rejected a distinct trace");
        }
        expectFailure(() -> deduplicator.tryRetain(new int[0]), "empty trace");
    }

    private static void assertCompressedMultiShardRoundTrip(
            List<ExpertDatasetRecord> records) throws Exception {
        Path directory = Files.createTempDirectory("rotation-dataset-roundtrip-");
        ExpertDatasetWriter.write(directory, records, 2);
        List<ExpertDatasetRecord> loaded = ExpertDatasetReader.read(
                directory.resolve(ExpertDatasetWriter.MANIFEST_FILE));
        if (loaded.size() != records.size()) {
            throw new AssertionError("Dataset round trip changed record count");
        }
        for (int index = 0; index < loaded.size(); index++) {
            if (!records.get(index).getRecordHash().equals(loaded.get(index).getRecordHash())) {
                throw new AssertionError("Dataset round trip changed a record hash");
            }
            loaded.get(index).replayAndValidate();
        }
        long shardCount;
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            shardCount = paths.filter(path -> path.getFileName().toString().endsWith(".jsonl.gz"))
                    .count();
        }
        if (shardCount != 2L) {
            throw new AssertionError("Expected two compressed shards but found " + shardCount);
        }
        Path duplicate = Files.createTempDirectory("rotation-dataset-deterministic-");
        ExpertDatasetWriter.write(duplicate, records, 2);
        if (!Arrays.equals(
                Files.readAllBytes(directory.resolve(ExpertDatasetWriter.MANIFEST_FILE)),
                Files.readAllBytes(duplicate.resolve(ExpertDatasetWriter.MANIFEST_FILE)))) {
            throw new AssertionError("Identical datasets produced different manifests");
        }
    }

    private static void assertBrokenLineageRejected(
            ExpertDatasetRecord child) throws Exception {
        Path directory = Files.createTempDirectory("rotation-dataset-lineage-");
        expectFailure(() -> writeUnchecked(directory, List.of(child), 1),
                "missing lineage parent");
        if (Files.exists(directory.resolve(ExpertDatasetWriter.MANIFEST_FILE))) {
            throw new AssertionError("Broken lineage published a manifest");
        }
    }

    private static void assertManifestSurvivesRejectedWrite(
            ExpertDatasetRecord record) throws Exception {
        Path directory = Files.createTempDirectory("rotation-dataset-atomic-");
        ExpertDatasetWriter.write(directory, List.of(record), 1);
        Path manifest = directory.resolve(ExpertDatasetWriter.MANIFEST_FILE);
        byte[] before = Files.readAllBytes(manifest);
        expectFailure(() -> writeUnchecked(directory, List.of(record, record), 1),
                "duplicate ID write");
        if (!Arrays.equals(before, Files.readAllBytes(manifest))) {
            throw new AssertionError("Rejected write replaced a valid manifest");
        }
        ExpertDatasetReader.read(manifest);
    }

    private static void assertCorruptionRejected(ExpertDatasetRecord record) throws Exception {
        Path directory = Files.createTempDirectory("rotation-dataset-corrupt-");
        mechanics.rotation.DatasetManifest manifest = ExpertDatasetWriter.write(
                directory, List.of(record), 1);
        Path shard = directory.resolve(manifest.getShards().get(0).getFileName());
        Files.write(shard, new byte[] {1, 2, 3, 4});
        expectFailure(() -> readUnchecked(directory.resolve(ExpertDatasetWriter.MANIFEST_FILE)),
                "corrupt shard");
    }

    private static void assertInvalidRecordsRejected(
            ExpertDatasetRecord fixture,
            RotationScenario scenario,
            TotalOptimizationResult build,
            int[] actions) {
        double[] observation = new double[ObservationEncoder.OBSERVATION_SIZE];
        double[] mask = new double[PolicyAction.SIZE];
        double[] policy = new double[PolicyAction.SIZE];
        double[] q = new double[PolicyAction.SIZE];
        mask[PolicyAction.NORMAL.getId()] = 1.0;
        policy[PolicyAction.BURST.getId()] = 1.0;
        ExpertDatasetRecord.Decision masked = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("masked", "train", build, masked),
                "masked action");

        observation[0] = Double.NaN;
        mask[PolicyAction.BURST.getId()] = 1.0;
        ExpertDatasetRecord.Decision nonFinite = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("non-finite", "train", build, nonFinite),
                "non-finite observation");

        observation[0] = 0.0;
        ExpertDatasetRecord.Decision valid = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("bad-split", "test", build, valid),
                "unknown split");

        if (actions.length < 2) {
            throw new AssertionError("Partial replay fixture requires multiple decisions");
        }
        expectFailure(() -> ExpertDatasetRecord.capture(
                "partial",
                scenario,
                "RaidenParty",
                "train",
                16,
                0,
                provenance(scenario, build, 0),
                Arrays.copyOf(actions, actions.length - 1)),
                "partial replay");

        assertTamperedRecordRejected(fixture, "schemaVersion", 1, "old schema");
        assertTamperedRecordRejected(
                fixture, "actionLayoutRevision", -1, "stale action layout");
        JsonObject missingProvenance = GSON.toJsonTree(fixture).getAsJsonObject();
        missingProvenance.remove("provenance");
        expectFailure(() -> GSON.fromJson(
                missingProvenance, ExpertDatasetRecord.class).validate(),
                "missing provenance");

        JsonObject missingSourceHash = GSON.toJsonTree(fixture).getAsJsonObject();
        missingSourceHash.getAsJsonObject("provenance").remove("sourceContentHash");
        expectFailure(() -> GSON.fromJson(
                missingSourceHash, ExpertDatasetRecord.class).validate(),
                "missing source hash");

        JsonObject nullBuildMap = GSON.toJsonTree(fixture).getAsJsonObject();
        nullBuildMap.getAsJsonObject("provenance").add("partyRolls", null);
        expectFailure(() -> GSON.fromJson(
                nullBuildMap, ExpertDatasetRecord.class).validate(),
                "null build map");

        JsonObject mismatchedBuild = GSON.toJsonTree(fixture).getAsJsonObject();
        mismatchedBuild.getAsJsonObject("provenance")
                .addProperty("buildFingerprint", "optimized-kqms-v1:wrong");
        expectFailure(() -> GSON.fromJson(
                mismatchedBuild, ExpertDatasetRecord.class).validate(),
                "mismatched build fingerprint");
    }

    private static void assertTamperedRecordRejected(
            ExpertDatasetRecord fixture,
            String field,
            int value,
            String message) {
        JsonObject object = GSON.toJsonTree(fixture).getAsJsonObject();
        object.addProperty(field, value);
        expectFailure(() -> GSON.fromJson(object, ExpertDatasetRecord.class).validate(),
                message);
    }

    private static ExpertDatasetRecord syntheticRecord(
            String id,
            String split,
            TotalOptimizationResult build,
            ExpertDatasetRecord.Decision decision) {
        RotationObjective.Score score = new RotationObjective(0.0, 0.0, 0.0, 0.0)
                .evaluate(0.0, 1.0, 0.0, 0);
        return new ExpertDatasetRecord(
                id,
                "synthetic",
                "RaidenParty",
                split,
                1L,
                1.0,
                1,
                1,
                0,
                provenance(
                        PartyCatalog.require("RaidenParty"),
                        "synthetic",
                        1L,
                        build,
                        0),
                List.of(decision),
                ExpertDatasetRecord.Objective.from(score));
    }

    private static RotationScenario scenario(TotalOptimizationResult build) {
        return scenario(PartyCatalog.require("RaidenParty"), build, 1357L);
    }

    private static RotationScenario scenario(
            PartyDefinition definition,
            TotalOptimizationResult build,
            long seed) {
        return RotationScenario.forPartyBuild(
                definition,
                build,
                new EpisodeConfig(),
                1.0,
                1,
                seed,
                RotationObjective.cyclicDamage());
    }

    private static int[] legalActions(RotationScenario scenario) {
        List<Integer> actions = new ArrayList<>();
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep step = environment.reset();
            while (!step.done && actions.size() < 100) {
                int action = firstLegal(step.legalActionMask);
                actions.add(action);
                step = environment.step(action);
            }
            if (!step.done) {
                throw new AssertionError("Dataset fixture did not reach its horizon");
            }
        }
        return actions.stream().mapToInt(Integer::intValue).toArray();
    }

    private static TotalOptimizationResult fixtureBuild() {
        return PartyBuildResolver.require(PartyCatalog.require("RaidenParty"));
    }

    private static ExpertDatasetProvenance provenance(
            RotationScenario scenario,
            TotalOptimizationResult build,
            int rank) {
        return provenance(
                PartyCatalog.require("RaidenParty"), scenario, build, rank);
    }

    private static ExpertDatasetProvenance provenance(
            PartyDefinition definition,
            RotationScenario scenario,
            TotalOptimizationResult build,
            int rank) {
        return provenance(
                definition,
                scenario.getFingerprint(),
                scenario.getSeed(),
                build,
                rank);
    }

    private static ExpertDatasetProvenance provenance(
            PartyDefinition definition,
            String scenarioFingerprint,
            long seed,
            TotalOptimizationResult build,
            int rank) {
        Map<String, Double> erTargets = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> partyRolls = new LinkedHashMap<>();
        for (CharacterId characterId : CharacterId.values()) {
            Double er = build.erTargets.get(characterId);
            if (er == null) {
                continue;
            }
            erTargets.put(characterId.name(), er);
            Map<String, Integer> rolls = new LinkedHashMap<>();
            for (StatType statType : StatType.values()) {
                Integer count = build.partyRolls.get(characterId).get(statType);
                if (count != null) {
                    rolls.put(statType.name(), count);
                }
            }
            partyRolls.put(characterId.name(), rolls);
        }
        return new ExpertDatasetProvenance(
                "fixture-source",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of("fixture-reference"),
                "accepted",
                rank == 0 ? List.of() : List.of("tiny-0"),
                "unguided-mcts",
                rank,
                1,
                0,
                0.0,
                0.0,
                0.0,
                List.of(11L, 13L, 17L, 19L, 23L),
                seed,
                TotalOptimizationResult.BUILD_MODE,
                ExpertDatasetProvenance.ARTIFACT_STANDARD_REVISION,
                build.getBuildFingerprint(),
                rollHash(partyRolls),
                definition.loadoutFingerprint(),
                Arrays.stream(definition.partyOrder())
                        .map(CharacterId::name)
                        .collect(Collectors.toList()),
                "primary:" + definition.partyOrder()[0].name(),
                scenarioFingerprint,
                erTargets,
                partyRolls);
    }

    private static String rollHash(Map<String, Map<String, Integer>> rolls) {
        StringBuilder canonical = new StringBuilder();
        for (String character : new java.util.TreeSet<>(rolls.keySet())) {
            canonical.append(character).append('{');
            Map<String, Integer> characterRolls = rolls.get(character);
            for (String stat : new java.util.TreeSet<>(characterRolls.keySet())) {
                canonical.append(stat).append('=').append(characterRolls.get(stat)).append(';');
            }
            canonical.append('}');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int firstLegal(double[] mask) {
        for (int action = 0; action < mask.length; action++) {
            if (mask[action] > 0.5) {
                return action;
            }
        }
        throw new AssertionError("No legal action in dataset fixture");
    }

    private static void expectFailure(ThrowingRunnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        } catch (Exception exception) {
            throw new AssertionError("Unexpected exception for " + message, exception);
        }
    }

    private static void writeUnchecked(
            Path directory,
            List<ExpertDatasetRecord> records,
            int recordsPerShard) {
        try {
            ExpertDatasetWriter.write(directory, records, recordsPerShard);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void readUnchecked(Path manifest) {
        try {
            ExpertDatasetReader.read(manifest);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
