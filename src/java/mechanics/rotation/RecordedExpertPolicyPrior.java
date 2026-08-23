package mechanics.rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import mechanics.rl.ObservationEncoder;

/** Strict state-hash policy prior exported by the Python model. */
public final class RecordedExpertPolicyPrior implements ExpertPolicyPrior {
    public static final int SCHEMA_VERSION = 2;
    public static final String TRAINING_DATASET_STATES = "training-dataset-states";
    public static final String EVALUATION_PROBE_STATES = "evaluation-probe-states";

    private final Map<Long, double[]> weightsByStateHash;
    private final String sourceKind;
    private final String datasetSourceHash;
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong fallbackCount = new AtomicLong();

    /** Loads one scenario-specific prior artifact with uniform unknown-state fallback. */
    public RecordedExpertPolicyPrior(Path path, String scenarioFingerprint) throws IOException {
        if (path == null || scenarioFingerprint == null || scenarioFingerprint.isBlank()) {
            throw new IllegalArgumentException("path and scenarioFingerprint are required");
        }
        Payload payload;
        try {
            payload = new Gson().fromJson(Files.readString(path), Payload.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed recorded policy prior: " + path, exception);
        }
        if (payload == null
                || payload.schemaVersion != SCHEMA_VERSION
                || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(
                        payload.simulatorRevision)
                || payload.actionLayoutRevision != PolicyAction.LAYOUT_REVISION
                || payload.observationSchemaRevision != ObservationEncoder.SCHEMA_REVISION
                || (!TRAINING_DATASET_STATES.equals(payload.sourceKind)
                        && !EVALUATION_PROBE_STATES.equals(payload.sourceKind))
                || payload.datasetSourceHash == null
                || !payload.datasetSourceHash.matches("[0-9a-f]{64}")
                || payload.trainingFingerprints == null
                || payload.trainingFingerprints.length == 0
                || payload.entries == null) {
            throw new IllegalArgumentException("Recorded policy prior revision mismatch: " + path);
        }
        Set<String> trainingFingerprints = new HashSet<>();
        for (String fingerprint : payload.trainingFingerprints) {
            if (fingerprint == null || fingerprint.isBlank()
                    || !trainingFingerprints.add(fingerprint)) {
                throw new IllegalArgumentException(
                        "Recorded policy prior has invalid training fingerprints");
            }
        }
        this.sourceKind = payload.sourceKind;
        this.datasetSourceHash = payload.datasetSourceHash;
        this.weightsByStateHash = new HashMap<>();
        for (Entry entry : payload.entries) {
            if (entry == null || entry.scenarioFingerprint == null
                    || entry.scenarioFingerprint.isBlank()
                    || entry.stateHash == null || entry.weights == null
                    || entry.weights.length != PolicyAction.SIZE) {
                throw new IllegalArgumentException("Invalid recorded policy prior entry: " + path);
            }
            boolean trainingEntry = trainingFingerprints.contains(
                    entry.scenarioFingerprint);
            if (TRAINING_DATASET_STATES.equals(sourceKind) != trainingEntry) {
                throw new IllegalArgumentException(
                        "Recorded policy prior entry violates split provenance");
            }
            long stateHash;
            try {
                stateHash = Long.parseLong(entry.stateHash);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid policy prior state hash", exception);
            }
            double total = 0.0;
            for (double weight : entry.weights) {
                if (!Double.isFinite(weight) || weight < 0.0) {
                    throw new IllegalArgumentException("Recorded policy prior has invalid weight");
                }
                total += weight;
            }
            if (total <= 0.0) {
                throw new IllegalArgumentException("Recorded policy prior has zero state");
            }
            if (scenarioFingerprint.equals(entry.scenarioFingerprint)
                    && weightsByStateHash.put(stateHash, entry.weights.clone()) != null) {
                throw new IllegalArgumentException("Recorded policy prior has zero or duplicate state");
            }
        }
        if (weightsByStateHash.isEmpty()) {
            throw new IllegalArgumentException(
                    "Recorded policy prior has no states for scenario: "
                            + scenarioFingerprint);
        }
    }

    @Override
    public double[] weights(RotationStep step) {
        double[] weights = weightsByStateHash.get(step.stateHash);
        if (weights != null) {
            hitCount.incrementAndGet();
            return weights.clone();
        }
        fallbackCount.incrementAndGet();
        return ExpertPolicyPrior.uniform().weights(step);
    }

    public int getKnownStateCount() {
        return weightsByStateHash.size();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public String getDatasetSourceHash() {
        return datasetSourceHash;
    }

    private static final class Payload {
        private int schemaVersion;
        private String simulatorRevision;
        private int actionLayoutRevision;
        private int observationSchemaRevision;
        private String sourceKind;
        private String datasetSourceHash;
        private String[] trainingFingerprints;
        private Entry[] entries;
    }

    private static final class Entry {
        private String scenarioFingerprint;
        private String stateHash;
        private double[] weights;
    }
}
