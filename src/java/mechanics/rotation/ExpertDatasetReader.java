package mechanics.rotation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/** Strict manifest and compressed-shard reader shared by replay tools. */
public final class ExpertDatasetReader {
    private static final Gson GSON = new Gson();

    private ExpertDatasetReader() {
    }

    /** Reads, hashes, validates, and returns every record in manifest order. */
    public static List<ExpertDatasetRecord> read(Path manifestPath) throws IOException {
        if (manifestPath == null || !Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("Dataset manifest does not exist: " + manifestPath);
        }
        DatasetManifest manifest;
        try {
            manifest = GSON.fromJson(Files.readString(manifestPath), DatasetManifest.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed dataset manifest", exception);
        }
        if (manifest == null) {
            throw new IllegalArgumentException("Dataset manifest is empty");
        }
        manifest.validate();
        Path directory = manifestPath.toAbsolutePath().getParent();
        List<ExpertDatasetRecord> records = new ArrayList<>();
        Set<String> recordIds = new HashSet<>();
        Map<String, String> fingerprintSplits = new HashMap<>();
        for (DatasetManifest.Shard shard : manifest.getShards()) {
            Path shardPath = directory.resolve(shard.getFileName()).normalize();
            if (!shardPath.getParent().equals(directory)) {
                throw new IllegalArgumentException("Dataset shard escapes its manifest directory");
            }
            byte[] bytes = Files.readAllBytes(shardPath);
            String actualHash = ExpertDatasetRecord.sha256(bytes);
            if (!actualHash.equals(shard.getSha256())) {
                throw new IllegalArgumentException("Dataset shard hash mismatch: " + shardPath);
            }
            int before = records.size();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(shardPath)),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        throw new IllegalArgumentException("Dataset shard contains a blank record");
                    }
                    ExpertDatasetRecord record = GSON.fromJson(line, ExpertDatasetRecord.class);
                    if (record == null) {
                        throw new IllegalArgumentException("Dataset shard contains a null record");
                    }
                    record.validateSourceLine(line);
                    if (!recordIds.add(record.getRecordId())) {
                        throw new IllegalArgumentException(
                                "Duplicate dataset record ID: " + record.getRecordId());
                    }
                    String previous = fingerprintSplits.putIfAbsent(
                            record.getScenarioFingerprint(), record.getSplit());
                    if (previous != null && !previous.equals(record.getSplit())) {
                        throw new IllegalArgumentException(
                                "Scenario fingerprint appears in multiple splits: "
                                        + record.getScenarioFingerprint());
                    }
                    records.add(record);
                }
            } catch (JsonParseException exception) {
                throw new IllegalArgumentException("Malformed dataset record in " + shardPath, exception);
            }
            if (records.size() - before != shard.getRecordCount()) {
                throw new IllegalArgumentException("Dataset shard record count mismatch: " + shardPath);
            }
        }
        if (records.size() != manifest.getTotalRecords()) {
            throw new IllegalArgumentException("Dataset manifest total record count mismatch");
        }
        return List.copyOf(records);
    }
}
