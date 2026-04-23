package visualization;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Writes rendered reports to the output directory.
 */
final class ReportFileWriter {
    private ReportFileWriter() {
    }

    static void write(String filePath, String html) {
        try (PrintWriter out = new PrintWriter(new FileWriter("output/" + filePath))) {
            out.write(html);
            System.out.println("Generated HTML Report: output/" + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
