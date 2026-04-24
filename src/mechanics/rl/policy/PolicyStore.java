package mechanics.rl.policy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * CSV persistence for tabular Java-native policies.
 */
public final class PolicyStore {
    private PolicyStore() {
    }

    public static void saveQLearningPolicy(QLearningPolicy policy, String path) {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("Failed to create policy directory: " + parent);
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            for (Map.Entry<String, double[]> entry : policy.getQTable().entrySet()) {
                out.print(escape(entry.getKey()));
                for (double value : entry.getValue()) {
                    out.print(",");
                    out.print(value);
                }
                out.println();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save policy: " + path, e);
        }
    }

    public static QLearningPolicy loadQLearningPolicy(String path, long seed) {
        QLearningPolicy policy = new QLearningPolicy(seed, 0.0, 0.0, 1.0, 0.0, 0.90);
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length != 8) {
                    continue;
                }
                double[] values = new double[7];
                for (int i = 0; i < values.length; i++) {
                    values[i] = Double.parseDouble(parts[i + 1]);
                }
                policy.getQTable().put(unescape(parts[0]), values);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load policy: " + path, e);
        }
        return policy;
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace(",", "%2C");
    }

    private static String unescape(String value) {
        return value.replace("%2C", ",").replace("%25", "%");
    }
}
