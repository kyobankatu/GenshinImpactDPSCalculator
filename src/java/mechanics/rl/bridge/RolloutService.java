package mechanics.rl.bridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import mechanics.rl.ActionSpace;
import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.RLEpisodeFactory;
import mechanics.rl.ObservationEncoder;
import mechanics.rl.PrivilegedStateEncoder;
import mechanics.rl.RLPartyRegistry;

/**
 * Local-only binary rollout service used by the Python learner.
 */
public class RolloutService {
    private final int port;
    private final String bindHost;
    private final int rolloutWorkers;
    private final boolean vineEnabled;
    private final ObservationEncoder observationEncoder;
    private final PrivilegedStateEncoder privilegedStateEncoder;
    private final RLEpisodeFactory episodeFactory;
    private final String[] partyNames;
    private final Map<Integer, VectorizedEnvironment> runners = new HashMap<>();
    private int nextRunnerId = 1;
    private volatile boolean vineSnapshotLogged = false;
    private long runnerCreateCalls;
    private long runnerCreateNanos;
    private long resetCalls;
    private long resetNanos;
    private long stepCalls;
    private long stepNanos;
    private long resetWriteNanos;
    private long stepWriteNanos;

    /**
     * Creates a rollout service bound to localhost with the default single-party registry.
     *
     * @param port TCP port to listen on
     * @param config episode configuration
     */
    public RolloutService(int port, EpisodeConfig config) {
        this(port, "127.0.0.1", RLPartyRegistry.createEpisodeFactory(config, RLPartyRegistry.DEFAULT_SINGLE_PARTY), 0);
    }

    /**
     * Creates a rollout service with the default single-party registry.
     *
     * @param port TCP port to listen on
     * @param bindHost interface address to bind
     * @param config episode configuration
     */
    public RolloutService(int port, String bindHost, EpisodeConfig config) {
        this(port, bindHost, RLPartyRegistry.createEpisodeFactory(config, RLPartyRegistry.DEFAULT_SINGLE_PARTY), 0);
    }

    /**
     * Creates a rollout service with the default single-party registry.
     *
     * @param port TCP port to listen on
     * @param bindHost interface address to bind
     * @param config episode configuration
     * @param rolloutWorkers number of rollout worker threads, or {@code 0} for auto
     */
    public RolloutService(int port, String bindHost, EpisodeConfig config, int rolloutWorkers) {
        this(port, bindHost, RLPartyRegistry.createEpisodeFactory(config, RLPartyRegistry.DEFAULT_SINGLE_PARTY), rolloutWorkers);
    }

    /**
     * Creates a rollout service backed by a custom episode factory.
     *
     * @param port TCP port to listen on
     * @param bindHost interface address to bind
     * @param episodeFactory factory used to create rollout episodes
     * @param rolloutWorkers number of rollout worker threads, or {@code 0} for auto
     */
    public RolloutService(int port, String bindHost, RLEpisodeFactory episodeFactory, int rolloutWorkers) {
        this(port, bindHost, episodeFactory, rolloutWorkers, false);
    }

    /**
     * Creates a rollout service backed by a custom episode factory.
     *
     * @param port TCP port to listen on
     * @param bindHost interface address to bind
     * @param episodeFactory factory used to create rollout episodes
     * @param rolloutWorkers number of rollout worker threads, or {@code 0} for auto
     * @param vineEnabled whether VinePPO snapshot support is enabled
     */
    public RolloutService(int port, String bindHost, RLEpisodeFactory episodeFactory, int rolloutWorkers,
            boolean vineEnabled) {
        this.port = port;
        this.bindHost = bindHost;
        this.rolloutWorkers = rolloutWorkers;
        this.vineEnabled = vineEnabled;
        this.episodeFactory = episodeFactory;
        this.partyNames = episodeFactory.getPartyNames();
        this.observationEncoder = new ObservationEncoder();
        this.privilegedStateEncoder = new PrivilegedStateEncoder();
    }

    /**
     * Starts the blocking server loop and serves clients until shutdown is requested.
     *
     * @throws IOException if the socket cannot be created or the client stream fails
     */
    public void serveForever() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port, 1, InetAddress.getByName(bindHost))) {
            String workerLabel = rolloutWorkers > 0 ? Integer.toString(rolloutWorkers) : "auto";
            System.out.printf("RL rollout service listening on %s:%d (workers=%s, vineEnabled=%b)%n", bindHost, port, workerLabel, vineEnabled);
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
                    out.writeInt(PrivilegedStateEncoder.STATE_SIZE);
                    out.writeInt(ObservationEncoder.FEATURES_PER_CHARACTER);
                    out.writeInt(ObservationEncoder.GLOBAL_FEATURES);
                    out.writeInt(ObservationEncoder.NUM_CHARS);
                    out.writeInt(partyNames.length);
                    for (String partyName : partyNames) {
                        writeString(out, partyName);
                    }
                    out.flush();
                    break;
                case BatchProtocol.CMD_CREATE_RUNNER:
                    int count = in.readInt();
                    int runnerId = nextRunnerId++;
                    long createStart = System.nanoTime();
                    runners.put(runnerId, new VectorizedEnvironment(
                            count, episodeFactory, rolloutWorkers,
                            observationEncoder, privilegedStateEncoder, vineEnabled));
                    runnerCreateCalls++;
                    runnerCreateNanos += System.nanoTime() - createStart;
                    out.writeInt(runnerId);
                    out.flush();
                    break;
                case BatchProtocol.CMD_RESET_RUNNER:
                    long resetStart = System.nanoTime();
                    VectorizedEnvironment.RunnerResetResult resetResult = handleReset(
                            in.readInt(), in.readBoolean(), in.readInt());
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
                    if (!vineSnapshotLogged && stepResult.vineSnapshotIds != null) {
                        for (int snapId : stepResult.vineSnapshotIds) {
                            if (snapId >= 0) {
                                System.out.printf("[RolloutService] First vine snapshot saved: snapId=%d%n", snapId);
                                vineSnapshotLogged = true;
                                break;
                            }
                        }
                    }
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
                case BatchProtocol.CMD_BRANCH_ROLLOUT:
                    int branchRunnerId = in.readInt();
                    int snapshotId = in.readInt();
                    int branchK = in.readInt();
                    int branchH = in.readInt();
                    double branchGamma = in.readDouble();
                    double[] qValues;
                    try {
                        qValues = getRunner(branchRunnerId).branchRolloutMulti(
                                snapshotId, branchK, branchH, branchGamma);
                    } catch (Exception e) {
                        System.err.printf("[RolloutService] branch_rollout error snap=%d: %s%n",
                                snapshotId, e);
                        e.printStackTrace(System.err);
                        qValues = new double[mechanics.rl.RLAction.SIZE];
                        java.util.Arrays.fill(qValues, Double.NaN);
                    }
                    out.writeInt(qValues.length);
                    for (double q : qValues) {
                        out.writeDouble(q);
                    }
                    out.flush();
                    break;
                case BatchProtocol.CMD_RELEASE_SNAPSHOTS:
                    int releaseRunnerId = in.readInt();
                    VectorizedEnvironment releaseRunner = runners.get(releaseRunnerId);
                    if (releaseRunner != null) {
                        releaseRunner.releaseSnapshots();
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

    private VectorizedEnvironment.RunnerResetResult handleReset(int runnerId, boolean generateReport,
            int preferredPartyId) {
        return getRunner(runnerId).reset(generateReport, preferredPartyId);
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
        writeMatrix(out, result.privilegedObservations);
        writeMatrix(out, result.actionMasks);
        writeIntVector(out, result.partyIds);
        out.flush();
    }

    private void writeStep(DataOutputStream out, RunnerStepResult result) throws IOException {
        out.writeInt(result.observations.length);
        writeMatrix(out, result.observations);
        writeMatrix(out, result.privilegedObservations);
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
        writeIntVector(out, result.partyIds);
        writeIntVector(out, result.episodePartyIds);
        writeVector(out, result.episodeRoleAlignmentScores);
        writeVector(out, result.episodeCarryAlignmentScores);
        writeVector(out, result.episodeOffFieldAlignmentScores);
        writeVector(out, result.episodeEntryAlignmentScores);
        writeVector(out, result.episodeStayAlignmentScores);
        writeMatrix(out, result.episodeExpectedRoleVectors);
        writeMatrix(out, result.episodeRealizedRoleVectors);
        writeIntVector(out, result.vineSnapshotIds);
        out.flush();
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
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
