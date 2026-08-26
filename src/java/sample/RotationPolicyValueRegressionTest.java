package sample;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import mechanics.rotation.ExpertPolicyPrior;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.PolicyValueAdvisor;
import mechanics.rotation.PolicyValueEstimate;
import mechanics.rotation.PolicyValueInferenceClient;
import mechanics.rotation.RecordedPolicyValueAdvisor;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationStep;

/** Regression executable for the versioned policy-value advisor boundary. */
public final class RotationPolicyValueRegressionTest {
    private static final String DATASET_HASH =
            "1b57a2f27296dd66e5f0336dddf6ad4a9d3b0020c60290705f5adece0c0a6495";
    private static final String CHECKPOINT_HASH =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final Path FIXTURE = Path.of(
            "src/python/rl/tests/fixtures/policy_value_v1.json");

    private RotationPolicyValueRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        assertUniformAndCompatibilityAdapters();
        assertRecordedBatchAndRecurrentRoundTrip();
        assertTypedFallbacks();
        assertInferenceClientRoundTripAndTimeout();
        assertInvalidOutputsRejected();
        assertStaleAndMalformedArtifactsRejected();
        if (args.length > 0) {
            assertLiveService(args);
        }
        System.out.println("RotationPolicyValueRegressionTest passed");
    }

    private static void assertUniformAndCompatibilityAdapters() {
        PolicyValueAdvisor.Query query = query(1L, 42L);
        PolicyValueEstimate uniform = PolicyValueAdvisor.uniform()
                .advise(List.of(query), 100L).get(0);
        assertNear(uniform.getPolicyPrior()[PolicyAction.NORMAL.getId()], 0.5);
        assertNear(uniform.getPolicyPrior()[PolicyAction.WAIT_SHORT.getId()], 0.5);
        if (uniform.isFallback() || uniform.getValueEstimate() != null
                || !Arrays.equals(uniform.getRecurrentState(), new double[]{0.25, -0.5})) {
            throw new AssertionError("Uniform advisor metadata mismatch");
        }
        ExpertPolicyPrior prior = ExpertPolicyPrior.fromAdvisor(
                PolicyValueAdvisor.uniform(), 100L);
        if (!Arrays.equals(uniform.getPolicyPrior(), prior.weights(query.getStep()))) {
            throw new AssertionError("Legacy advisor adapter changed uniform policy");
        }
        RotationSearchConfig config = RotationSearchConfig.defaults(4L, 8)
                .withAdvisor(PolicyValueAdvisor.uniform(), 100L)
                .withInitialSeeds(List.of(new int[]{PolicyAction.NORMAL.getId()}));
        if (config.advisor == null
                || config.prior.weights(query.getStep()).length != PolicyAction.SIZE) {
            throw new AssertionError("Search config did not preserve advisor boundary");
        }
    }

    private static void assertRecordedBatchAndRecurrentRoundTrip() throws Exception {
        RecordedPolicyValueAdvisor advisor = new RecordedPolicyValueAdvisor(
                FIXTURE,
                "fixture-policy-value",
                DATASET_HASH,
                CHECKPOINT_HASH);
        PolicyValueEstimate estimate = advisor.advise(
                List.of(query(7L, 42L)), 100L).get(0);
        assertNear(estimate.getPolicyPrior()[PolicyAction.NORMAL.getId()], 2.0 / 3.0);
        assertNear(estimate.getPolicyPrior()[PolicyAction.WAIT_SHORT.getId()], 1.0 / 3.0);
        if (estimate.getPolicyPrior()[PolicyAction.CHARGE.getId()] != 0.0
                || estimate.getValueEstimate() == null
                || estimate.getValueEstimate() != 12.5
                || !Arrays.equals(estimate.getRecurrentState(), new double[]{0.5, -0.25})
                || advisor.getKnownStateCount() != 1
                || !DATASET_HASH.equals(advisor.getDatasetSourceHash())
                || !CHECKPOINT_HASH.equals(advisor.getCheckpointFingerprint())) {
            throw new AssertionError("Recorded advisor response mismatch");
        }
    }

    private static void assertTypedFallbacks() throws Exception {
        RecordedPolicyValueAdvisor recorded = new RecordedPolicyValueAdvisor(
                FIXTURE,
                "fixture-policy-value",
                DATASET_HASH,
                CHECKPOINT_HASH);
        PolicyValueEstimate unavailable = PolicyValueAdvisor.withUniformFallback(recorded)
                .advise(List.of(query(8L, 43L)), 100L).get(0);
        if (unavailable.getDiagnostic()
                        != PolicyValueEstimate.Diagnostic.UNAVAILABLE
                || unavailable.getValueEstimate() != null
                || !Arrays.equals(
                        unavailable.getRecurrentState(),
                        new double[]{0.25, -0.5})) {
            throw new AssertionError("Unavailable fallback metadata mismatch");
        }
        PolicyValueAdvisor slow = (queries, timeoutMillis) -> {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return PolicyValueAdvisor.uniform().advise(queries, timeoutMillis);
        };
        PolicyValueEstimate timeout = PolicyValueAdvisor.withUniformFallback(slow)
                .advise(List.of(query(9L, 42L)), 1L).get(0);
        if (timeout.getDiagnostic() != PolicyValueEstimate.Diagnostic.TIMEOUT) {
            throw new AssertionError("Late advisor did not produce typed timeout fallback");
        }
        PolicyValueAdvisor reversed = (queries, timeoutMillis) -> List.of(
                PolicyValueAdvisor.uniform().advise(
                        List.of(queries.get(1)), timeoutMillis).get(0),
                PolicyValueAdvisor.uniform().advise(
                        List.of(queries.get(0)), timeoutMillis).get(0));
        List<PolicyValueEstimate> reordered = PolicyValueAdvisor.withUniformFallback(reversed)
                .advise(List.of(query(10L, 42L), query(11L, 42L)), 100L);
        if (reordered.stream().anyMatch(estimate -> estimate.getDiagnostic()
                != PolicyValueEstimate.Diagnostic.INVALID_RESPONSE)) {
            throw new AssertionError("Response-order mismatch did not fail closed");
        }
        double[] alternateMask = new double[PolicyAction.SIZE];
        alternateMask[PolicyAction.CHARGE.getId()] = 1.0;
        double[] alternatePrior = alternateMask.clone();
        PolicyValueAdvisor wrongMask = (queries, timeoutMillis) -> List.of(
                PolicyValueEstimate.validated(
                        queries.get(0).getRequestId(),
                        alternatePrior,
                        null,
                        new double[0],
                        alternateMask));
        PolicyValueEstimate masked = PolicyValueAdvisor.withUniformFallback(wrongMask)
                .advise(List.of(query(12L, 42L)), 100L).get(0);
        if (masked.getDiagnostic()
                != PolicyValueEstimate.Diagnostic.INVALID_RESPONSE) {
            throw new AssertionError("Advisor changed the simulator legal mask");
        }
    }

    private static void assertInvalidOutputsRejected() {
        double[] mask = query(12L, 42L).getStep().legalActionMask;
        double[] valid = new double[PolicyAction.SIZE];
        valid[PolicyAction.NORMAL.getId()] = 1.0;
        expectFailure(() -> PolicyValueEstimate.validated(
                12L, new double[PolicyAction.SIZE - 1], null, null, mask));
        double[] nan = valid.clone();
        nan[0] = Double.NaN;
        expectFailure(() -> PolicyValueEstimate.validated(12L, nan, null, null, mask));
        double[] negative = valid.clone();
        negative[0] = -1.0;
        expectFailure(() -> PolicyValueEstimate.validated(12L, negative, null, null, mask));
        expectFailure(() -> PolicyValueEstimate.validated(
                12L, new double[PolicyAction.SIZE], null, null, mask));
        double[] masked = valid.clone();
        masked[PolicyAction.CHARGE.getId()] = 0.1;
        expectFailure(() -> PolicyValueEstimate.validated(12L, masked, null, null, mask));
        expectFailure(() -> PolicyValueEstimate.validated(
                12L, valid, Double.NaN, null, mask));
        expectFailure(() -> PolicyValueEstimate.validated(
                12L, valid, null, new double[]{Double.NaN}, mask));
    }

    private static void assertInferenceClientRoundTripAndTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = respondOnce(server, responseJson(13L), 0L);
            try (PolicyValueInferenceClient client = new PolicyValueInferenceClient(
                    "127.0.0.1",
                    server.getLocalPort(),
                    DATASET_HASH,
                    CHECKPOINT_HASH,
                    1000)) {
                PolicyValueEstimate estimate = client.advise(
                        List.of(query(13L, 42L)), 1000L).get(0);
                assertNear(estimate.getPolicyPrior()[PolicyAction.NORMAL.getId()], 2.0 / 3.0);
                assertNear(estimate.getPolicyPrior()[PolicyAction.WAIT_SHORT.getId()], 1.0 / 3.0);
                if (estimate.getValueEstimate() == null
                        || estimate.getValueEstimate() != 8.5
                        || !Arrays.equals(
                                estimate.getRecurrentState(),
                                new double[]{0.5, -0.25})) {
                    throw new AssertionError("Inference client response mismatch");
                }
            }
            responder.join(1000L);
        }
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = respondOnce(server, responseJson(14L), 30L);
            try (PolicyValueInferenceClient client = new PolicyValueInferenceClient(
                    "127.0.0.1",
                    server.getLocalPort(),
                    DATASET_HASH,
                    CHECKPOINT_HASH,
                    1000)) {
                PolicyValueEstimate timeout = PolicyValueAdvisor.withUniformFallback(client)
                        .advise(List.of(query(14L, 42L)), 1L).get(0);
                if (timeout.getDiagnostic() != PolicyValueEstimate.Diagnostic.TIMEOUT) {
                    throw new AssertionError("Inference timeout did not fail closed");
                }
            }
            responder.join(1000L);
        }
    }

    private static Thread respondOnce(
            ServerSocket server,
            String response,
            long delayMillis) {
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept();
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                int size = input.readInt();
                if (input.readNBytes(size).length != size) {
                    throw new AssertionError("Inference client request was truncated");
                }
                if (delayMillis > 0L) {
                    Thread.sleep(delayMillis);
                }
                byte[] encoded = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                output.writeInt(encoded.length);
                output.write(encoded);
                output.flush();
            } catch (java.io.IOException exception) {
                if (delayMillis == 0L) {
                    throw new IllegalStateException(exception);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        thread.start();
        return thread;
    }

    private static String responseJson(long requestId) {
        return "{\"schemaVersion\":1,\"kind\":\"response\",\"contract\":{"
                + "\"simulatorRevision\":\"rotation-simulator-v4\","
                + "\"datasetSchemaVersion\":2,\"datasetSourceHash\":\""
                + DATASET_HASH + "\",\"actionLayoutRevision\":2,"
                + "\"observationSchemaRevision\":2,\"modelRevision\":2,"
                + "\"checkpointFingerprint\":\"" + CHECKPOINT_HASH + "\"},"
                + "\"items\":[{\"requestId\":" + requestId
                + ",\"policyPrior\":[2,0,0,0,0,0,1,0,0,0,0],"
                + "\"valueEstimate\":8.5,\"recurrentState\":[0.5,-0.25],"
                + "\"diagnostic\":\"none\"}]}";
    }

    private static void assertStaleAndMalformedArtifactsRejected() throws Exception {
        expectFailure(() -> loadRecorded(DATASET_HASH, "c".repeat(64), FIXTURE));
        expectFailure(() -> loadRecorded("a".repeat(64), CHECKPOINT_HASH, FIXTURE));
        Path malformed = Files.createTempFile("policy-value-malformed", ".json");
        try {
            Files.writeString(malformed, "{truncated");
            expectFailure(() -> loadRecorded(DATASET_HASH, CHECKPOINT_HASH, malformed));
        } finally {
            Files.deleteIfExists(malformed);
        }
    }

    private static void assertLiveService(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Live regression requires host, port, dataset hash, and checkpoint hash");
        }
        try (PolicyValueInferenceClient client = new PolicyValueInferenceClient(
                args[0],
                Integer.parseInt(args[1]),
                args[2],
                args[3],
                1000)) {
            PolicyValueAdvisor.Query base = query(15L, 42L);
            PolicyValueAdvisor.Query live = new PolicyValueAdvisor.Query(
                    base.getRequestId(),
                    base.getStep(),
                    new double[0]);
            PolicyValueEstimate estimate = client.advise(List.of(live), 1000L).get(0);
            double total = Arrays.stream(estimate.getPolicyPrior()).sum();
            if (Math.abs(total - 1.0) > 1.0e-6
                    || estimate.getValueEstimate() == null
                    || estimate.getRecurrentState().length == 0) {
                throw new AssertionError("Live policy-value service response mismatch");
            }
        }
    }

    private static PolicyValueAdvisor.Query query(long requestId, long stateHash) {
        double[] mask = new double[PolicyAction.SIZE];
        mask[PolicyAction.NORMAL.getId()] = 1.0;
        mask[PolicyAction.WAIT_SHORT.getId()] = 1.0;
        return new PolicyValueAdvisor.Query(
                requestId,
                new RotationStep(
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
                                .evaluate(0.0, 0.0, 0.0, 0)),
                new double[]{0.25, -0.5});
    }

    private static void loadRecorded(
            String datasetHash,
            String checkpointHash,
            Path path) {
        try {
            new RecordedPolicyValueAdvisor(
                    path,
                    "fixture-policy-value",
                    datasetHash,
                    checkpointHash);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected policy-value validation failure");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }

    private static void assertNear(double actual, double expected) {
        if (Math.abs(actual - expected) > 1.0e-12) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
