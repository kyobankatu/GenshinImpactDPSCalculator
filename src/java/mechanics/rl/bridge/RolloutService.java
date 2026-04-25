package mechanics.rl.bridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import mechanics.rl.ActionSpace;
import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.FlinsParty2RLSimulatorFactory;
import mechanics.rl.ObservationEncoder;

/**
 * Local-only binary rollout service used by the Python learner.
 */
public class RolloutService {
    private final int port;
    private final String bindHost;
    private final EpisodeConfig config;
    private final int rolloutWorkers;
    private final Map<Integer, VectorizedEnvironment> runners = new HashMap<>();
    private int nextRunnerId = 1;
    private long runnerCreateCalls;
    private long runnerCreateNanos;
    private long resetCalls;
    private long resetNanos;
    private long stepCalls;
    private long stepNanos;
    private long resetWriteNanos;
    private long stepWriteNanos;

    public RolloutService(int port, EpisodeConfig config) {
        this(port, "127.0.0.1", config, 0);
    }

    public RolloutService(int port, String bindHost, EpisodeConfig config) {
        this(port, bindHost, config, 0);
    }

    public RolloutService(int port, String bindHost, EpisodeConfig config, int rolloutWorkers) {
        this.port = port;
        this.bindHost = bindHost;
        this.config = config;
        this.rolloutWorkers = rolloutWorkers;
    }

    public void serveForever() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port, 1, InetAddress.getByName(bindHost))) {
            String workerLabel = rolloutWorkers > 0 ? Integer.toString(rolloutWorkers) : "auto";
            System.out.printf("RL rollout service listening on %s:%d (workers=%s)%n", bindHost, port, workerLabel);
            while (true) {
                try (Socket socket = serverSocket.accept();
                        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                    if (!handleClient(in, out)) {
                        return;
                    }
                }
            }
        }
    }

    private boolean handleClient(DataInputStream in, DataOutputStream out) throws IOException {
        while (true) {
            int command;
            try {
                command = in.readInt();
            } catch (IOException e) {
                return true;
            }
            switch (command) {
                case BatchProtocol.CMD_HELLO:
                    out.writeInt(BatchProtocol.VERSION);
                    out.writeInt(ObservationEncoder.OBSERVATION_SIZE);
                    out.writeInt(ActionSpace.SIZE);
                    out.flush();
                    break;
                case BatchProtocol.CMD_CREATE_RUNNER:
                    int count = in.readInt();
                    int runnerId = nextRunnerId++;
                    long createStart = System.nanoTime();
                    runners.put(runnerId, new VectorizedEnvironment(
                            count, FlinsParty2RLSimulatorFactory.supplier(), config, rolloutWorkers));
                    runnerCreateCalls++;
                    runnerCreateNanos += System.nanoTime() - createStart;
                    out.writeInt(runnerId);
                    out.flush();
                    break;
                case BatchProtocol.CMD_RESET_RUNNER:
                    long resetStart = System.nanoTime();
                    VectorizedEnvironment.RunnerResetResult resetResult = handleReset(in.readInt(), in.readBoolean());
                    resetCalls++;
                    resetNanos += System.nanoTime() - resetStart;
                    long resetWriteStart = System.nanoTime();
                    writeReset(out, resetResult);
                    resetWriteNanos += System.nanoTime() - resetWriteStart;
                    break;
                case BatchProtocol.CMD_STEP_RUNNER:
                    int runner = in.readInt();
                    VectorizedEnvironment environment = getRunner(runner);
                    int[] actions = new int[environment.size()];
                    for (int i = 0; i < actions.length; i++) {
                        actions[i] = in.readInt();
                    }
                    long stepStart = System.nanoTime();
                    RunnerStepResult stepResult = environment.step(actions);
                    stepCalls++;
                    stepNanos += System.nanoTime() - stepStart;
                    long stepWriteStart = System.nanoTime();
                    writeStep(out, stepResult);
                    stepWriteNanos += System.nanoTime() - stepWriteStart;
                    break;
                case BatchProtocol.CMD_CLOSE_RUNNER:
                    int closeRunnerId = in.readInt();
                    VectorizedEnvironment closed = runners.remove(closeRunnerId);
                    if (closed != null) {
                        System.out.printf("Closed runner %d: %s%n", closeRunnerId,
                                closed.metricsSnapshot().toSummaryString());
                        closed.close();
                    }
                    out.writeBoolean(true);
                    out.flush();
                    break;
                case BatchProtocol.CMD_SHUTDOWN:
                    for (VectorizedEnvironment vectorizedEnvironment : runners.values()) {
                        vectorizedEnvironment.close();
                    }
                    printServiceMetrics();
                    System.out.println("Battle environment metrics: " + BattleEnvironment.timingSnapshot().toSummaryString());
                    out.writeBoolean(true);
                    out.flush();
                    return false;
                default:
                    throw new IOException("Unknown command: " + command);
            }
        }
    }

    private VectorizedEnvironment.RunnerResetResult handleReset(int runnerId, boolean generateReport) {
        return getRunner(runnerId).reset(generateReport);
    }

    private VectorizedEnvironment getRunner(int runnerId) {
        VectorizedEnvironment runner = runners.get(runnerId);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown runner id: " + runnerId);
        }
        return runner;
    }

    private void writeReset(DataOutputStream out, VectorizedEnvironment.RunnerResetResult result) throws IOException {
        out.writeInt(result.observations.length);
        writeMatrix(out, result.observations);
        writeMatrix(out, result.actionMasks);
        out.flush();
    }

    private void writeStep(DataOutputStream out, RunnerStepResult result) throws IOException {
        out.writeInt(result.observations.length);
        writeMatrix(out, result.observations);
        writeMatrix(out, result.actionMasks);
        writeVector(out, result.rewards);
        writeBooleanVector(out, result.dones);
        writeBooleanVector(out, result.validActions);
        writeVector(out, result.damageDeltas);
        writeVector(out, result.totalDamages);
        writeVector(out, result.episodeRewards);
        writeVector(out, result.episodeDamages);
        writeIntVector(out, result.episodeSteps);
        writeIntVector(out, result.liveSteps);
        out.flush();
    }

    private void writeMatrix(DataOutputStream out, double[][] values) throws IOException {
        out.writeInt(values[0].length);
        for (double[] row : values) {
            for (double value : row) {
                out.writeDouble(value);
            }
        }
    }

    private void writeVector(DataOutputStream out, double[] values) throws IOException {
        for (double value : values) {
            out.writeDouble(value);
        }
    }

    private void writeBooleanVector(DataOutputStream out, boolean[] values) throws IOException {
        for (boolean value : values) {
            out.writeBoolean(value);
        }
    }

    private void writeIntVector(DataOutputStream out, int[] values) throws IOException {
        for (int value : values) {
            out.writeInt(value);
        }
    }

    private void printServiceMetrics() {
        System.out.printf(
                "Rollout service metrics: createCalls=%d meanCreateMs=%.3f resetCalls=%d meanResetMs=%.3f stepCalls=%d meanStepMs=%.3f meanResetWriteMs=%.3f meanStepWriteMs=%.3f%n",
                runnerCreateCalls,
                averageMillis(runnerCreateNanos, runnerCreateCalls),
                resetCalls,
                averageMillis(resetNanos, resetCalls),
                stepCalls,
                averageMillis(stepNanos, stepCalls),
                averageMillis(resetWriteNanos, resetCalls),
                averageMillis(stepWriteNanos, stepCalls));
    }

    private double averageMillis(long nanos, long calls) {
        return calls == 0 ? 0.0 : (nanos / 1_000_000.0) / calls;
    }
}
