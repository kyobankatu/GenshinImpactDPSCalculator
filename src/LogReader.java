import java.nio.file.*;
import java.nio.charset.*;
import java.io.*;

public class LogReader {
    public static void main(String[] args) throws Exception {
        // Try UTF-16LE
        // PowerShell redirection > creates UTF-16LE files
        try (BufferedReader br = Files.newBufferedReader(Paths.get("output_refactor_check.txt"),
                StandardCharsets.UTF_16LE)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("[HillClimb]") || line.contains("Joint") || line.contains("Optimization")
                        || line.contains("Artifact Substat Rolls")) {
                    System.out.println(line);
                }
            }
        }
    }
}
