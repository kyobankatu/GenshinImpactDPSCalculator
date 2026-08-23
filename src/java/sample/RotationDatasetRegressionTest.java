package sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.ObservationEncoder;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.ExpertDatasetReader;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetWriter;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationStep;
import simulation.party.PartyCatalog;

/** Regression checks for transactional expert datasets and exact replay. */
public class RotationDatasetRegressionTest {
    public static void main(String[] args) throws Exception {
        RotationScenario scenario = scenario();
        int[] actions = legalActions(scenario, 2);
        ExpertDatasetRecord first = ExpertDatasetRecord.capture(
                "tiny-0", scenario, "RaidenParty", "train", 16, 0, actions);
        ExpertDatasetRecord second = ExpertDatasetRecord.capture(
                "tiny-1", scenario, "RaidenParty", "train", 16, 1, actions);
        ExpertDatasetRecord third = ExpertDatasetRecord.capture(
                "tiny-2", scenario, "RaidenParty", "train", 16, 2, actions);
        assertCompressedMultiShardRoundTrip(List.of(first, second, third));
        assertManifestSurvivesRejectedWrite(first);
        assertCorruptionRejected(first);
        assertInvalidRecordsRejected();
        System.out.println("RotationDatasetRegressionTest passed");
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

    private static void assertInvalidRecordsRejected() {
        double[] observation = new double[ObservationEncoder.OBSERVATION_SIZE];
        double[] mask = new double[PolicyAction.SIZE];
        double[] policy = new double[PolicyAction.SIZE];
        double[] q = new double[PolicyAction.SIZE];
        mask[PolicyAction.NORMAL.getId()] = 1.0;
        policy[PolicyAction.BURST.getId()] = 1.0;
        ExpertDatasetRecord.Decision masked = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("masked", "train", masked), "masked action");

        observation[0] = Double.NaN;
        mask[PolicyAction.BURST.getId()] = 1.0;
        ExpertDatasetRecord.Decision nonFinite = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("non-finite", "train", nonFinite),
                "non-finite observation");

        observation[0] = 0.0;
        ExpertDatasetRecord.Decision valid = new ExpertDatasetRecord.Decision(
                observation, mask, PolicyAction.BURST.getId(), policy, q, 1L, true);
        expectFailure(() -> syntheticRecord("bad-split", "test", valid), "unknown split");
    }

    private static ExpertDatasetRecord syntheticRecord(
            String id,
            String split,
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
                List.of(decision),
                ExpertDatasetRecord.Objective.from(score));
    }

    private static RotationScenario scenario() {
        return RotationScenario.forParty(
                PartyCatalog.require("RaidenParty"),
                new EpisodeConfig(),
                1.0,
                1,
                1357L,
                RotationObjective.cyclicDamage());
    }

    private static int[] legalActions(RotationScenario scenario, int limit) {
        List<Integer> actions = new ArrayList<>();
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep step = environment.reset();
            while (!step.done && actions.size() < limit) {
                int action = firstLegal(step.legalActionMask);
                actions.add(action);
                step = environment.step(action);
            }
        }
        return actions.stream().mapToInt(Integer::intValue).toArray();
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
