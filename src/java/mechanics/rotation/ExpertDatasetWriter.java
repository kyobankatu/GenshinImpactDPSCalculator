package mechanics.rotation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Transactional writer for content-addressed compressed expert datasets. */
public final class ExpertDatasetWriter {
    public static final String MANIFEST_FILE = "manifest.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson LINE_GSON = new Gson();

    private ExpertDatasetWriter() {
    }

    /** Writes shards first and atomically replaces the manifest last. */
    public static DatasetManifest write(
            Path directory,
            List<ExpertDatasetRecord> records,
            int recordsPerShard) throws IOException {
        if (directory == null || records == null || records.isEmpty()) {
            throw new IllegalArgumentException("directory and non-empty records are required");
        }
        if (recordsPerShard <= 0) {
            throw new IllegalArgumentException("recordsPerShard must be positive");
        }
        Set<String> recordIds = new HashSet<>();
        for (ExpertDatasetRecord record : records) {
            record.validate();
            if (!recordIds.add(record.getRecordId())) {
                throw new IllegalArgumentException("Duplicate dataset record ID: " + record.getRecordId());
            }
        }
        Files.createDirectories(directory);
        List<DatasetManifest.Shard> shards = new ArrayList<>();
        for (int offset = 0; offset < records.size(); offset += recordsPerShard) {
            List<ExpertDatasetRecord> shardRecords = records.subList(
                    offset, Math.min(records.size(), offset + recordsPerShard));
            shards.add(writeShard(directory, shardRecords));
        }
        DatasetManifest manifest = new DatasetManifest(shards);
        Path temporaryManifest = directory.resolve(
                ".manifest-" + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(
                    temporaryManifest,
                    GSON.toJson(manifest) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.move(
                    temporaryManifest,
                    directory.resolve(MANIFEST_FILE),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryManifest);
        }
        return manifest;
    }

    private static DatasetManifest.Shard writeShard(
            Path directory,
            List<ExpertDatasetRecord> records) throws IOException {
        Path temporary = directory.resolve(".shard-" + UUID.randomUUID() + ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(temporary)),
                    StandardCharsets.UTF_8))) {
                for (ExpertDatasetRecord record : records) {
                    writer.write(LINE_GSON.toJson(record));
                    writer.newLine();
                }
            }
            byte[] bytes = Files.readAllBytes(temporary);
            String hash = ExpertDatasetRecord.sha256(bytes);
            String fileName = "shard-" + hash + ".jsonl.gz";
            Path target = directory.resolve(fileName);
            if (Files.exists(target)) {
                if (!hash.equals(ExpertDatasetRecord.sha256(Files.readAllBytes(target)))) {
                    throw new IOException("Existing content-addressed shard has wrong hash: " + target);
                }
                Files.delete(temporary);
            } else {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
            return new DatasetManifest.Shard(
                    fileName,
                    hash,
                    records.size(),
                    DatasetManifest.fingerprintSplits(records));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
