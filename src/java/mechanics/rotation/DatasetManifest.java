package mechanics.rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable manifest published after all content-addressed dataset shards. */
public final class DatasetManifest {
    private final int schemaVersion;
    private final String simulatorRevision;
    private final int totalRecords;
    private final List<Shard> shards;

    /** Creates and validates one dataset manifest. */
    public DatasetManifest(List<Shard> shards) {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("manifest requires at least one shard");
        }
        this.schemaVersion = ExpertDatasetRecord.SCHEMA_VERSION;
        this.simulatorRevision = ExpertDatasetRecord.SIMULATOR_REVISION;
        this.shards = List.copyOf(shards);
        int count = 0;
        for (Shard shard : shards) {
            shard.validate();
            count += shard.recordCount;
        }
        this.totalRecords = count;
        validate();
    }

    /** Validates revisions, counts, unique shard names, and split isolation. */
    public void validate() {
        if (schemaVersion != ExpertDatasetRecord.SCHEMA_VERSION
                || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(simulatorRevision)
                || shards == null || shards.isEmpty() || totalRecords <= 0) {
            throw new IllegalArgumentException("Dataset manifest revision or count mismatch");
        }
        Set<String> fileNames = new HashSet<>();
        Map<String, String> fingerprintSplits = new HashMap<>();
        int counted = 0;
        for (Shard shard : shards) {
            if (shard == null) {
                throw new IllegalArgumentException("Dataset manifest contains a null shard");
            }
            shard.validate();
            if (!fileNames.add(shard.fileName)) {
                throw new IllegalArgumentException("Duplicate dataset shard: " + shard.fileName);
            }
            counted += shard.recordCount;
            for (FingerprintSplit entry : shard.fingerprintSplits) {
                String previous = fingerprintSplits.putIfAbsent(entry.fingerprint, entry.split);
                if (previous != null && !previous.equals(entry.split)) {
                    throw new IllegalArgumentException(
                            "Scenario fingerprint appears in multiple splits: " + entry.fingerprint);
                }
            }
        }
        if (counted != totalRecords) {
            throw new IllegalArgumentException("Dataset manifest totalRecords mismatch");
        }
    }

    public List<Shard> getShards() {
        return List.copyOf(shards);
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    /** One content-addressed compressed JSONL shard. */
    public static final class Shard {
        private final String fileName;
        private final String sha256;
        private final int recordCount;
        private final List<FingerprintSplit> fingerprintSplits;

        /** Creates one shard descriptor from its records. */
        public Shard(
                String fileName,
                String sha256,
                int recordCount,
                List<FingerprintSplit> fingerprintSplits) {
            this.fileName = fileName;
            this.sha256 = sha256;
            this.recordCount = recordCount;
            this.fingerprintSplits = fingerprintSplits == null
                    ? null : List.copyOf(fingerprintSplits);
            validate();
        }

        private void validate() {
            if (fileName == null || !fileName.matches("shard-[0-9a-f]{64}\\.jsonl\\.gz")
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                    || !fileName.equals("shard-" + sha256 + ".jsonl.gz")
                    || recordCount <= 0 || fingerprintSplits == null
                    || fingerprintSplits.isEmpty()) {
                throw new IllegalArgumentException("Invalid dataset shard descriptor");
            }
            for (FingerprintSplit entry : fingerprintSplits) {
                entry.validate();
            }
        }

        public String getFileName() {
            return fileName;
        }

        public String getSha256() {
            return sha256;
        }

        public int getRecordCount() {
            return recordCount;
        }
    }

    /** One scenario fingerprint and its exclusive split. */
    public static final class FingerprintSplit {
        private final String fingerprint;
        private final String split;

        /** Creates one split-isolation descriptor. */
        public FingerprintSplit(String fingerprint, String split) {
            this.fingerprint = fingerprint;
            this.split = split;
            validate();
        }

        private void validate() {
            if (fingerprint == null || fingerprint.isBlank()
                    || (!"train".equals(split)
                            && !"validation".equals(split)
                            && !"holdout".equals(split))) {
                throw new IllegalArgumentException("Invalid fingerprint split descriptor");
            }
        }
    }

    /** Builds deterministic unique fingerprint/split entries for a shard. */
    static List<FingerprintSplit> fingerprintSplits(List<ExpertDatasetRecord> records) {
        Map<String, String> splits = new java.util.TreeMap<>();
        for (ExpertDatasetRecord record : records) {
            String previous = splits.putIfAbsent(
                    record.getScenarioFingerprint(), record.getSplit());
            if (previous != null && !previous.equals(record.getSplit())) {
                throw new IllegalArgumentException(
                        "Scenario fingerprint appears in multiple splits: "
                                + record.getScenarioFingerprint());
            }
        }
        List<FingerprintSplit> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : splits.entrySet()) {
            entries.add(new FingerprintSplit(entry.getKey(), entry.getValue()));
        }
        return entries;
    }
}
