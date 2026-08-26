package mechanics.rotation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import mechanics.rl.ObservationEncoder;

/** Persistent local-only client for bounded batched policy-value inference. */
public final class PolicyValueInferenceClient implements PolicyValueAdvisor {
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private final Gson gson = new Gson();
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Contract contract;
    private volatile boolean closed;

    /** Opens one loopback-only connection with explicit frozen fingerprints. */
    public PolicyValueInferenceClient(
            String host,
            int port,
            String datasetSourceHash,
            String checkpointFingerprint,
            int connectTimeoutMillis) throws IOException {
        if (host == null || port <= 0 || port > 65535
                || connectTimeoutMillis <= 0
                || !isHash(datasetSourceHash)
                || !isHash(checkpointFingerprint)) {
            throw new IllegalArgumentException("Invalid policy-value client configuration");
        }
        InetAddress address = InetAddress.getByName(host);
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Policy-value inference must remain loopback-only");
        }
        this.contract = Contract.expected(datasetSourceHash, checkpointFingerprint);
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(address, port), connectTimeoutMillis);
        socket.setTcpNoDelay(true);
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    @Override
    public synchronized List<PolicyValueEstimate> advise(
            List<Query> queries,
            long timeoutMillis) {
        if (closed) {
            throw new AdvisorException(
                    PolicyValueEstimate.Diagnostic.UNAVAILABLE,
                    "policy-value client is closed");
        }
        if (queries == null || queries.isEmpty() || timeoutMillis <= 0L
                || timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid policy-value client batch");
        }
        try {
            socket.setSoTimeout((int) timeoutMillis);
            RequestPayload request = RequestPayload.from(contract, queries);
            byte[] encoded = gson.toJson(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (encoded.length > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("Policy-value request exceeds frame limit");
            }
            output.writeInt(encoded.length);
            output.write(encoded);
            output.flush();
            int responseSize = input.readInt();
            if (responseSize <= 0 || responseSize > MAX_FRAME_BYTES) {
                throw new IllegalArgumentException("Policy-value response frame size is invalid");
            }
            byte[] responseBytes = input.readNBytes(responseSize);
            if (responseBytes.length != responseSize) {
                throw new EOFException("Truncated policy-value response frame");
            }
            ResponsePayload response = gson.fromJson(
                    new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8),
                    ResponsePayload.class);
            validateResponse(response, queries.size());
            List<PolicyValueEstimate> estimates = new ArrayList<>();
            for (int index = 0; index < queries.size(); index++) {
                ResponseItem item = response.items[index];
                Query query = queries.get(index);
                if (item.requestId != query.getRequestId()
                        || item.diagnostic != null && !"none".equals(item.diagnostic)) {
                    throw new IllegalArgumentException(
                            "Policy-value response order or diagnostic mismatch");
                }
                estimates.add(PolicyValueEstimate.validated(
                        item.requestId,
                        item.policyPrior,
                        item.valueEstimate,
                        item.recurrentState,
                        query.getStep().legalActionMask));
            }
            return List.copyOf(estimates);
        } catch (SocketTimeoutException exception) {
            closeQuietly();
            throw new AdvisorException(
                    PolicyValueEstimate.Diagnostic.TIMEOUT,
                    "policy-value inference timed out");
        } catch (IOException exception) {
            closeQuietly();
            throw new AdvisorException(
                    PolicyValueEstimate.Diagnostic.UNAVAILABLE,
                    "policy-value service unavailable: " + exception.getMessage());
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new AdvisorException(
                    PolicyValueEstimate.Diagnostic.INVALID_RESPONSE,
                    exception.getMessage());
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void validateResponse(ResponsePayload response, int expectedItems) {
        if (response == null || response.schemaVersion != SCHEMA_VERSION
                || !"response".equals(response.kind)
                || !contract.matches(response.contract)
                || response.items == null || response.items.length != expectedItems) {
            throw new IllegalArgumentException("Policy-value response contract mismatch");
        }
    }

    private void closeQuietly() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // The client is already unusable and close remains idempotent.
        }
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static final class RequestPayload {
        private int schemaVersion;
        private String kind;
        private Contract contract;
        private RequestItem[] items;

        private static RequestPayload from(Contract contract, List<Query> queries) {
            RequestPayload payload = new RequestPayload();
            payload.schemaVersion = SCHEMA_VERSION;
            payload.kind = "request";
            payload.contract = contract;
            payload.items = new RequestItem[queries.size()];
            for (int index = 0; index < queries.size(); index++) {
                payload.items[index] = RequestItem.from(queries.get(index));
            }
            return payload;
        }
    }

    private static final class RequestItem {
        private long requestId;
        private String stateHash;
        private double[] observation;
        private double[] legalActionMask;
        private double[] recurrentState;

        private static RequestItem from(Query query) {
            RequestItem item = new RequestItem();
            RotationStep step = query.getStep();
            item.requestId = query.getRequestId();
            item.stateHash = Long.toString(step.stateHash);
            item.observation = step.observation;
            item.legalActionMask = step.legalActionMask;
            item.recurrentState = query.getRecurrentState();
            return item;
        }
    }

    private static final class ResponsePayload {
        private int schemaVersion;
        private String kind;
        private Contract contract;
        private ResponseItem[] items;
    }

    private static final class ResponseItem {
        private long requestId;
        private double[] policyPrior;
        private Double valueEstimate;
        private double[] recurrentState;
        private String diagnostic;
    }

    private static final class Contract {
        private String simulatorRevision;
        private int datasetSchemaVersion;
        private String datasetSourceHash;
        private int actionLayoutRevision;
        private int observationSchemaRevision;
        private int modelRevision;
        private String checkpointFingerprint;

        private static Contract expected(String datasetHash, String checkpointHash) {
            Contract value = new Contract();
            value.simulatorRevision = ExpertDatasetRecord.SIMULATOR_REVISION;
            value.datasetSchemaVersion = ExpertDatasetRecord.SCHEMA_VERSION;
            value.datasetSourceHash = datasetHash;
            value.actionLayoutRevision = PolicyAction.LAYOUT_REVISION;
            value.observationSchemaRevision = ObservationEncoder.SCHEMA_REVISION;
            value.modelRevision = MODEL_REVISION;
            value.checkpointFingerprint = checkpointHash;
            return value;
        }

        private boolean matches(Contract other) {
            return other != null
                    && simulatorRevision.equals(other.simulatorRevision)
                    && datasetSchemaVersion == other.datasetSchemaVersion
                    && datasetSourceHash.equals(other.datasetSourceHash)
                    && actionLayoutRevision == other.actionLayoutRevision
                    && observationSchemaRevision == other.observationSchemaRevision
                    && modelRevision == other.modelRevision
                    && checkpointFingerprint.equals(other.checkpointFingerprint);
        }
    }
}
