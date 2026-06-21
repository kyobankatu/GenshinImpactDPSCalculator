package visualization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Copies local report image assets into a web-published report directory.
 */
final class ReportAssetPublisher {
    private ReportAssetPublisher() {
    }

    static void publishDocsAssets(List<ReportViewAdapter.ReportCharacterView> characters) {
        if (characters == null || characters.isEmpty()) {
            return;
        }
        Path root = Path.of("docs", "assets", "report");
        for (ReportViewAdapter.ReportCharacterView character : characters) {
            copyIfExists(
                    Path.of("config", "characters", ReportViewAdapter.assetKey(character.displayName), "face.png"),
                    root.resolve(Path.of("characters", ReportViewAdapter.assetKey(character.displayName), "face.png")));
            if (character.weaponName != null && !character.weaponName.isBlank() && !"-".equals(character.weaponName)) {
                copyIfExists(
                        Path.of("config", "weapons", ReportViewAdapter.assetKey(character.weaponName), "icon.png"),
                        root.resolve(Path.of("weapons", ReportViewAdapter.assetKey(character.weaponName), "icon.png")));
            }
            for (ReportViewAdapter.ReportAssetView artifact : character.artifactSets) {
                copyIfExists(
                        Path.of("config", "artifacts", ReportViewAdapter.assetKey(artifact.displayName), "flower.png"),
                        root.resolve(Path.of("artifacts", ReportViewAdapter.assetKey(artifact.displayName),
                                "flower.png")));
            }
        }
    }

    private static void copyIfExists(Path source, Path destination) {
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
