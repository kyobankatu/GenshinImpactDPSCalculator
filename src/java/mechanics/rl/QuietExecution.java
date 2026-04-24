package mechanics.rl;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Small helper to mute stdout during high-throughput rollout execution.
 */
public final class QuietExecution {
    private QuietExecution() {
    }

    public static <T> T call(java.util.concurrent.Callable<T> callable) {
        PrintStream original = System.out;
        PrintStream muted = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // discard rollout chatter
            }
        });
        System.setOut(muted);
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(original);
            muted.close();
        }
    }
}
