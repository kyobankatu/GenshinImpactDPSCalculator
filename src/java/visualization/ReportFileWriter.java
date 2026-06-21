package visualization;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes rendered reports to the output directory.
 */
final class ReportFileWriter {
    private ReportFileWriter() {
    }

    /**
     * Writes the given HTML to {@code output/<filePath>}, creating or overwriting
     * the file. IO errors are logged via stack trace and otherwise ignored.
     *
     * @param filePath report file name relative to the {@code output/} directory
     * @param html     rendered HTML document
     */
    static void write(String filePath, String html) {
        write(Path.of("output"), filePath, html);
    }

    /**
     * Writes the given HTML to a target directory, creating or overwriting the file.
     *
     * @param outputDirectory destination directory
     * @param filePath        report file name relative to {@code outputDirectory}
     * @param html            rendered HTML document
     */
    static void write(Path outputDirectory, String filePath, String html) {
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Path destination = outputDirectory.resolve(filePath);
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(destination.toFile()))) {
            out.write(html);
            System.out.println("Generated HTML Report: " + destination);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
