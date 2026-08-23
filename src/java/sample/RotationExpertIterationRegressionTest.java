package sample;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.ObservationEncoder;
import mechanics.rl.PrivilegedStateEncoder;
import mechanics.rl.RLPartyRegistry;
import mechanics.rl.bridge.BatchProtocol;
import mechanics.rl.bridge.RolloutService;
import mechanics.rl.bridge.RunnerStepResult;
import mechanics.rl.bridge.VectorizedEnvironment;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RecordedExpertPolicyPrior;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationStep;

/** Regression checks for bounded expert-query snapshots and disconnect cleanup. */
public class RotationExpertIterationRegressionTest {
    public static void main(String[] args) throws Exception {
        assertSnapshotHandlesBoundedAndReleased();
        assertDisconnectClosesOwnedRunners();
        assertRecordedPolicyPriorAndUniformFallback();
        System.out.println("RotationExpertIterationRegressionTest passed");
    }

    private static void assertSnapshotHandlesBoundedAndReleased() {
        String previous = System.getProperty("rotation.snapshot.limit");
        System.setProperty("rotation.snapshot.limit", "4");
        try {
            VectorizedEnvironment environment = new VectorizedEnvironment(
                    1,
                    RLPartyRegistry.createEpisodeFactory(
                            new EpisodeConfig(), RLPartyRegistry.DEFAULT_SINGLE_PARTY),
                    1,
                    new ObservationEncoder(),
                    new PrivilegedStateEncoder(),
                    true);
            try {
                VectorizedEnvironment.RunnerResetResult reset = environment.reset(false);
                double[] mask = reset.actionMasks[0];
                int firstSnapshotId = -1;
                int latestSnapshotId = -1;
                for (int index = 0; index < 8; index++) {
                    int action = firstLegal(mask);
                    RunnerStepResult step = environment.step(new int[] {action});
                    if (index == 0) {
                        firstSnapshotId = step.vineSnapshotIds[0];
                    }
                    latestSnapshotId = step.vineSnapshotIds[0];
                    mask = step.actionMasks[0];
                    if (environment.snapshotCount() > environment.maxSnapshotHandles()) {
                        throw new AssertionError("Snapshot store exceeded its configured limit");
                    }
                }
                int evicted = firstSnapshotId;
                expectFailure(() -> environment.branchRolloutMulti(evicted, 1, 1, 0.99),
                        "evicted snapshot");
                double[] qValues = environment.branchRolloutMulti(
                        latestSnapshotId, 1, 1, 0.99);
                if (qValues.length == 0) {
                    throw new AssertionError("All-legal branch query returned no actions");
                }
                environment.releaseSnapshots();
                if (environment.snapshotCount() != 0) {
                    throw new AssertionError("Snapshot release retained handles");
                }
            } finally {
                environment.close();
            }
        } finally {
            if (previous == null) {
                System.clearProperty("rotation.snapshot.limit");
            } else {
                System.setProperty("rotation.snapshot.limit", previous);
            }
        }
    }

    private static void assertDisconnectClosesOwnedRunners() throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        RolloutService service = new RolloutService(
                port,
                "127.0.0.1",
                RLPartyRegistry.createEpisodeFactory(
                        new EpisodeConfig(), RLPartyRegistry.DEFAULT_SINGLE_PARTY),
                1,
                true);
        Thread thread = new Thread(() -> {
            try {
                service.serveForever();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }, "rollout-cleanup-regression");
        thread.start();
        waitForService(port);
        try (Socket socket = new Socket("127.0.0.1", port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {
            out.writeInt(BatchProtocol.CMD_CREATE_RUNNER);
            out.writeInt(1);
            out.flush();
            in.readInt();
        }
        waitForRunnerCount(service, 0);
        try (Socket socket = new Socket("127.0.0.1", port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {
            out.writeInt(BatchProtocol.CMD_SHUTDOWN);
            out.flush();
            if (!in.readBoolean()) {
                throw new AssertionError("Rollout service rejected shutdown");
            }
        }
        thread.join(5000L);
        if (thread.isAlive()) {
            throw new AssertionError("Rollout service did not stop after regression");
        }
    }

    private static void assertRecordedPolicyPriorAndUniformFallback() throws Exception {
        java.nio.file.Path priorPath = java.nio.file.Files.createTempFile(
                "recorded-policy-prior-", ".json");
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String json = "{\"schemaVersion\":2,\"simulatorRevision\":\""
                + ExpertDatasetRecord.SIMULATOR_REVISION
                + "\",\"actionLayoutRevision\":2,"
                + "\"observationSchemaRevision\":2,"
                + "\"sourceKind\":\"training-dataset-states\","
                + "\"datasetSourceHash\":\"" + hash
                + "\",\"trainingFingerprints\":[\"fixture\"],"
                + "\"entries\":[{\"scenarioFingerprint\":\"fixture\","
                + "\"stateHash\":\"42\",\"weights\":[1,0,0,0,0,0,0,0,0,0,0]}]}";
        java.nio.file.Files.writeString(priorPath, json);
        RecordedExpertPolicyPrior prior = new RecordedExpertPolicyPrior(priorPath, "fixture");
        RotationStep known = priorStep(42L);
        if (prior.weights(known)[PolicyAction.NORMAL.getId()] != 1.0) {
            throw new AssertionError("Recorded model prior was not used");
        }
        double[] fallback = prior.weights(priorStep(43L));
        if (fallback[PolicyAction.NORMAL.getId()] != 1.0
                || fallback[PolicyAction.WAIT_SHORT.getId()] != 1.0) {
            throw new AssertionError("Unknown policy state did not use uniform legal fallback");
        }
        if (prior.getKnownStateCount() != 1 || prior.getHitCount() != 1L
                || prior.getFallbackCount() != 1L
                || !RecordedExpertPolicyPrior.TRAINING_DATASET_STATES.equals(
                        prior.getSourceKind())
                || !hash.equals(prior.getDatasetSourceHash())) {
            throw new AssertionError("Recorded model prior diagnostics mismatch");
        }
        java.nio.file.Files.writeString(priorPath, json.replace(
                "\"actionLayoutRevision\":2", "\"actionLayoutRevision\":99"));
        expectFailure(() -> loadPriorUnchecked(priorPath), "stale recorded prior");
        java.nio.file.Files.writeString(priorPath, json.replace(
                "training-dataset-states", "evaluation-probe-states"));
        expectFailure(() -> loadPriorUnchecked(priorPath), "training split leak");
        java.nio.file.Files.writeString(priorPath, json.replace(
                ExpertDatasetRecord.SIMULATOR_REVISION, "rotation-simulator-stale"));
        expectFailure(() -> loadPriorUnchecked(priorPath), "stale simulator revision");
        java.nio.file.Files.writeString(priorPath, json);
        expectFailure(() -> loadPriorUnchecked(priorPath, "holdout"),
                "missing selected scenario states");
    }

    private static RotationStep priorStep(long stateHash) {
        double[] mask = new double[PolicyAction.SIZE];
        mask[PolicyAction.NORMAL.getId()] = 1.0;
        mask[PolicyAction.WAIT_SHORT.getId()] = 1.0;
        return new RotationStep(
                new double[287],
                new double[187],
                mask,
                0.0,
                false,
                true,
                0.0,
                0.0,
                0.0,
                -1,
                0,
                0,
                stateHash,
                new RotationObjective(0.0, 0.0, 0.0, 0.0)
                        .evaluate(0.0, 0.0, 0.0, 0));
    }

    private static void loadPriorUnchecked(java.nio.file.Path path) {
        loadPriorUnchecked(path, "fixture");
    }

    private static void loadPriorUnchecked(java.nio.file.Path path, String fingerprint) {
        try {
            new RecordedExpertPolicyPrior(path, fingerprint);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void waitForService(int port) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (java.io.IOException exception) {
                Thread.sleep(10L);
            }
        }
        throw new AssertionError("Rollout service did not start");
    }

    private static void waitForRunnerCount(RolloutService service, int expected)
            throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (service.activeRunnerCount() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Runner disconnect cleanup did not converge");
    }

    private static int firstLegal(double[] mask) {
        for (int action = 0; action < mask.length; action++) {
            if (mask[action] > 0.5) {
                return action;
            }
        }
        throw new AssertionError("No legal action in expert-query regression");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }
}
