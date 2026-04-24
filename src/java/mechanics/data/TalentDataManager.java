package mechanics.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TalentDataManager implements TalentDataSource {
    private static TalentDataManager instance;
    private Map<String, Double> values = new HashMap<>();

    /**
     * Returns the process-wide talent-data cache.
     *
     * <p>This remains a singleton intentionally for now because character setup reads
     * from the same static CSV dataset across many simulator instances. Phase 6 keeps
     * this as a shared cache and narrows the more behavior-heavy static utility
     * coupling first.
     */
    public static TalentDataManager getInstance() {
        if (instance == null) {
            instance = new TalentDataManager();
            instance.loadAllFromDirectory("config/characters");
        }
        return instance;
    }

    public void loadAllFromDirectory(String dirPath) {
        java.nio.file.Path startPath = java.nio.file.Paths.get(dirPath);
        if (!java.nio.file.Files.exists(startPath) || !java.nio.file.Files.isDirectory(startPath)) {
            System.out.println("Config directory not found or not a directory: " + dirPath);
            loadData("config/talent_data.csv"); // Fallback
            return;
        }

        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(startPath)) {
            stream.filter(p -> p.toString().endsWith(".csv"))
                    .forEach(p -> loadData(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadData(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String charName = parts[0].trim();
                    String key = parts[2].trim();
                    // String level = parts[3].trim(); // Not strictly used for keying yet, but
                    // helpful context
                    double val1 = Double.parseDouble(parts[4].trim());
                    // double val2 = parts.length > 5 ? Double.parseDouble(parts[5].trim()) : 0.0;

                    // Key format: "CharacterName.Key"
                    // Example: "Raiden Shogun.Musou Shinsetsu"
                    values.put(charName + "." + key, val1);

                    if (parts.length > 5 && !parts[5].trim().isEmpty()) {
                        double val2 = Double.parseDouble(parts[5].trim());
                        values.put(charName + "." + key + ".2", val2);
                    }
                }
            }
            System.out.println("Loaded Talent Data: " + values.size() + " entries.");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load talent data from " + filePath);
        }
    }

    public double get(String charName, String key, double defaultValue) {
        String lookup = charName + "." + key;
        return values.getOrDefault(lookup, defaultValue);
    }
}
