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

    /**
     * Writes the given HTML to {@code output/<filePath>}, creating or overwriting
     * the file. IO errors are logged via stack trace and otherwise ignored.
     *
     * @param filePath report file name relative to the {@code output/} directory
     * @param html     rendered HTML document
     */
    static void write(String filePath, String html) {
        try (PrintWriter out = new PrintWriter(new FileWriter("output/" + filePath))) {
            out.write(html);
            System.out.println("Generated HTML Report: output/" + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
