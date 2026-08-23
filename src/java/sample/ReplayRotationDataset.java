package sample;

import java.nio.file.Path;
import java.util.List;

import mechanics.rotation.ExpertDatasetReader;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetWriter;

/** Replays every manifest record against the current simulator revision. */
public class ReplayRotationDataset {
    public static void main(String[] args) throws Exception {
        Path manifest = Path.of(args.length > 0
                ? args[0] : "output/rotation_dataset/" + ExpertDatasetWriter.MANIFEST_FILE);
        List<ExpertDatasetRecord> records = ExpertDatasetReader.read(manifest);
        for (ExpertDatasetRecord record : records) {
            record.replayAndValidate();
        }
        System.out.println("replayed=" + records.size());
        System.out.println("status=exact");
    }
}
