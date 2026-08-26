package mechanics.rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import mechanics.rl.ObservationEncoder;

/** Strict recorded policy-value artifact used for deterministic contract replay. */
public final class RecordedPolicyValueAdvisor implements PolicyValueAdvisor {
    private final Map<Long, Entry> entriesByStateHash = new HashMap<>();
    private final String datasetSourceHash;
    private final String checkpointFingerprint;

    /**
     * Loads one scenario from a versioned cross-language policy-value artifact.
     *
     * @param path artifact path
     * @param scenarioFingerprint selected simulator scenario
     * @param expectedDatasetHash frozen expert dataset SHA-256
     * @param expectedCheckpointFingerprint selected checkpoint SHA-256
     */
    public RecordedPolicyValueAdvisor(
            Path path,
            String scenarioFingerprint,
            String expectedDatasetHash,
            String expectedCheckpointFingerprint) throws IOException {
        if (path == null || isBlank(scenarioFingerprint)
                || !isHash(expectedDatasetHash)
                || !isHash(expectedCheckpointFingerprint)) {
            throw new IllegalArgumentException("Recorded advisor identity is invalid");
        }
        Payload payload;
        try {
            payload = new Gson().fromJson(Files.readString(path), Payload.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                    "Malformed recorded policy-value advisor: " + path,
                    exception);
        }
        validateContract(payload, expectedDatasetHash, expectedCheckpointFingerprint, path);
        this.datasetSourceHash = payload.contract.datasetSourceHash;
        this.checkpointFingerprint = payload.contract.checkpointFingerprint;
        Set<Long> stateHashes = new HashSet<>();
        int recurrentSize = -1;
        for (Entry entry : payload.entries) {
            if (entry == null || isBlank(entry.scenarioFingerprint)
                    || entry.stateHash == null || entry.policyPrior == null
                    || entry.recurrentStateIn == null || entry.recurrentStateOut == null) {
                throw new IllegalArgumentException("Invalid recorded advisor entry: " + path);
            }
            long stateHash;
            try {
                stateHash = Long.parseLong(entry.stateHash);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid recorded advisor state hash", exception);
            }
            if (recurrentSize < 0) {
                recurrentSize = entry.recurrentStateIn.length;
            }
            if (entry.recurrentStateIn.length != recurrentSize
                    || entry.recurrentStateOut.length != recurrentSize) {
                throw new IllegalArgumentException("Recorded advisor recurrent dimension mismatch");
            }
            if (entry.policyPrior.length != PolicyAction.SIZE) {
                throw new IllegalArgumentException("Recorded advisor policy dimension mismatch");
            }
            double total = 0.0;
            for (double weight : entry.policyPrior) {
                if (!Double.isFinite(weight) || weight < 0.0) {
                    throw new IllegalArgumentException("Recorded advisor policy is invalid");
                }
                total += weight;
            }
            if (!Double.isFinite(total) || total <= 0.0
                    || entry.valueEstimate != null
                    && !Double.isFinite(entry.valueEstimate)) {
                throw new IllegalArgumentException("Recorded advisor estimate is invalid");
            }
            requireFinite(entry.recurrentStateIn, "recorded recurrent input");
            requireFinite(entry.recurrentStateOut, "recorded recurrent output");
            if (scenarioFingerprint.equals(entry.scenarioFingerprint)) {
                if (!stateHashes.add(stateHash)) {
                    throw new IllegalArgumentException("Duplicate recorded advisor state");
                }
                entriesByStateHash.put(stateHash, entry);
            }
        }
        if (entriesByStateHash.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recorded advisor has no states for scenario: " + scenarioFingerprint);
        }
    }

    @Override
    public List<PolicyValueEstimate> advise(List<Query> queries, long timeoutMillis) {
        if (queries == null || queries.isEmpty() || timeoutMillis <= 0L) {
            throw new IllegalArgumentException("Recorded advisor batch is invalid");
        }
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        List<PolicyValueEstimate> estimates = new ArrayList<>();
        for (Query query : queries) {
            if (System.nanoTime() >= deadline) {
                throw new AdvisorException(
                        PolicyValueEstimate.Diagnostic.TIMEOUT,
                        "recorded advisor deadline exceeded");
            }
            RotationStep step = query.getStep();
            Entry entry = entriesByStateHash.get(step.stateHash);
            if (entry == null) {
                throw new AdvisorException(
                        PolicyValueEstimate.Diagnostic.UNAVAILABLE,
                        "recorded advisor state is unavailable");
            }
            if (!java.util.Arrays.equals(
                    query.getRecurrentState(), entry.recurrentStateIn)) {
                throw new AdvisorException(
                        PolicyValueEstimate.Diagnostic.INVALID_RESPONSE,
                        "recorded advisor recurrent input mismatch");
            }
            estimates.add(PolicyValueEstimate.validated(
                    query.getRequestId(),
                    entry.policyPrior,
                    entry.valueEstimate,
                    entry.recurrentStateOut,
                    step.legalActionMask));
        }
        return List.copyOf(estimates);
    }

    public int getKnownStateCount() {
        return entriesByStateHash.size();
    }

    public String getDatasetSourceHash() {
        return datasetSourceHash;
    }

    public String getCheckpointFingerprint() {
        return checkpointFingerprint;
    }

    private static void validateContract(
            Payload payload,
            String expectedDatasetHash,
            String expectedCheckpointFingerprint,
            Path path) {
        if (payload == null || payload.schemaVersion != SCHEMA_VERSION
                || payload.contract == null || payload.entries == null
                || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(
                        payload.contract.simulatorRevision)
                || payload.contract.datasetSchemaVersion
                        != ExpertDatasetRecord.SCHEMA_VERSION
                || payload.contract.actionLayoutRevision != PolicyAction.LAYOUT_REVISION
                || payload.contract.observationSchemaRevision
                        != ObservationEncoder.SCHEMA_REVISION
                || payload.contract.modelRevision != MODEL_REVISION
                || !expectedDatasetHash.equals(payload.contract.datasetSourceHash)
                || !expectedCheckpointFingerprint.equals(
                        payload.contract.checkpointFingerprint)) {
            throw new IllegalArgumentException(
                    "Recorded policy-value advisor revision mismatch: " + path);
        }
    }

    private static void requireFinite(double[] values, String name) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " contains non-finite value");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static final class Payload {
        private int schemaVersion;
        private Contract contract;
        private Entry[] entries;
    }

    private static final class Contract {
        private String simulatorRevision;
        private int datasetSchemaVersion;
        private String datasetSourceHash;
        private int actionLayoutRevision;
        private int observationSchemaRevision;
        private int modelRevision;
        private String checkpointFingerprint;
    }

    private static final class Entry {
        private String scenarioFingerprint;
        private String stateHash;
        private double[] policyPrior;
        private Double valueEstimate;
        private double[] recurrentStateIn;
        private double[] recurrentStateOut;
    }
}
