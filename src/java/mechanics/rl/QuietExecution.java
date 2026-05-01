package mechanics.rl;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Small helper to mute stdout during high-throughput rollout execution.
 */
public final class QuietExecution {
    private static final PrintStream ORIGINAL_STDOUT = System.out;
    private static final PrintStream MUTED_STDOUT = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {
            // discard rollout chatter
        }
    });
    private static final Object STDOUT_LOCK = new Object();
    private static int muteDepth = 0;

    private QuietExecution() {
    }

    public static <T> T call(java.util.concurrent.Callable<T> callable) {
        muteStdout();
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            restoreStdout();
        }
    }

    private static void muteStdout() {
        synchronized (STDOUT_LOCK) {
            if (muteDepth++ == 0) {
                System.setOut(MUTED_STDOUT);
            }
        }
    }

    private static void restoreStdout() {
        synchronized (STDOUT_LOCK) {
            if (--muteDepth == 0) {
                System.setOut(ORIGINAL_STDOUT);
            }
        }
    }
}
