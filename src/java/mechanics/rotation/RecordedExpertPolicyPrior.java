package mechanics.rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import mechanics.rl.ObservationEncoder;

/** Strict state-hash policy prior exported by the Python model. */
public final class RecordedExpertPolicyPrior implements ExpertPolicyPrior {
    public static final int SCHEMA_VERSION = 1;

    private final Map<Long, double[]> weightsByStateHash;

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
                || payload.actionLayoutRevision != PolicyAction.LAYOUT_REVISION
                || payload.observationSchemaRevision != ObservationEncoder.SCHEMA_REVISION
                || payload.datasetSourceHash == null
                || !payload.datasetSourceHash.matches("[0-9a-f]{64}")
                || payload.entries == null) {
            throw new IllegalArgumentException("Recorded policy prior revision mismatch: " + path);
        }
        this.weightsByStateHash = new HashMap<>();
        for (Entry entry : payload.entries) {
            if (entry == null || !scenarioFingerprint.equals(entry.scenarioFingerprint)
                    || entry.stateHash == null || entry.weights == null
                    || entry.weights.length != PolicyAction.SIZE) {
                if (entry != null && !scenarioFingerprint.equals(entry.scenarioFingerprint)) {
                    continue;
                }
                throw new IllegalArgumentException("Invalid recorded policy prior entry: " + path);
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
            if (total <= 0.0 || weightsByStateHash.put(stateHash, entry.weights.clone()) != null) {
                throw new IllegalArgumentException("Recorded policy prior has zero or duplicate state");
            }
        }
    }

    @Override
    public double[] weights(RotationStep step) {
        double[] weights = weightsByStateHash.get(step.stateHash);
        if (weights != null) {
            return weights.clone();
        }
        return ExpertPolicyPrior.uniform().weights(step);
    }

    private static final class Payload {
        private int schemaVersion;
        private int actionLayoutRevision;
        private int observationSchemaRevision;
        private String datasetSourceHash;
        private Entry[] entries;
    }

    private static final class Entry {
        private String scenarioFingerprint;
        private String stateHash;
        private double[] weights;
    }
}
