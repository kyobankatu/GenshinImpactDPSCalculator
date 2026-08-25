package mechanics.rotation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Immutable, source-traceable human rotation seed.
 *
 * <p>
 * The content hash covers every field except {@code contentHash}. Runtime code
 * consumes stable action IDs and typed character identities only after catalog
 * validation has established an exact party and loadout match.
 */
public final class SourcedRotationSeed {
    /** Review state controlling whether a seed may enter search. */
    public enum AdaptationStatus {
        ACCEPTED,
        ADAPTED,
        REJECTED
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final String seedId;
    private final String partyName;
    private final String scenarioFingerprint;
    private final List<String> sourceIds;
    private final String adaptationStatus;
    private final String adaptationNote;
    private final int[] openerActions;
    private final List<int[]> cycleActions;
    private final Map<String, Double> erTargets;
    private final List<String> sourceAssumptions;
    private final List<String> simulatorAssumptions;
    private String contentHash;

    /** Creates and seals one reviewed rotation seed. */
    public SourcedRotationSeed(
            String seedId,
            String partyName,
            String scenarioFingerprint,
            List<String> sourceIds,
            AdaptationStatus adaptationStatus,
            String adaptationNote,
            int[] openerActions,
            List<int[]> cycleActions,
            Map<String, Double> erTargets,
            List<String> sourceAssumptions,
            List<String> simulatorAssumptions) {
        this.seedId = seedId;
        this.partyName = partyName;
        this.scenarioFingerprint = scenarioFingerprint;
        this.sourceIds = sourceIds == null ? null : List.copyOf(sourceIds);
        this.adaptationStatus = adaptationStatus == null
                ? null
                : adaptationStatus.name().toLowerCase();
        this.adaptationNote = adaptationNote;
        this.openerActions = openerActions == null ? null : openerActions.clone();
        this.cycleActions = copyCycles(cycleActions);
        this.erTargets = erTargets == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(erTargets));
        this.sourceAssumptions = sourceAssumptions == null
                ? null
                : List.copyOf(sourceAssumptions);
        this.simulatorAssumptions = simulatorAssumptions == null
                ? null
                : List.copyOf(simulatorAssumptions);
        this.contentHash = "";
        validate(false);
        this.contentHash = calculateContentHash();
    }

    /** Validates structure, stable action IDs, review state, and content hash. */
    public void validate() {
        validate(true);
    }

    private void validate(boolean requireHash) {
        requireText(seedId, "seedId");
        requireText(partyName, "partyName");
        requireText(scenarioFingerprint, "scenarioFingerprint");
        if (sourceIds == null || sourceIds.isEmpty()) {
            throw new IllegalArgumentException("sourceIds must not be empty: " + seedId);
        }
        for (String sourceId : sourceIds) {
            requireText(sourceId, "sourceId");
        }
        AdaptationStatus status = adaptationStatus();
        if (status == AdaptationStatus.ADAPTED && (adaptationNote == null || adaptationNote.isBlank())) {
            throw new IllegalArgumentException("Adapted seed requires an adaptation note: " + seedId);
        }
        if (openerActions == null || cycleActions == null || cycleActions.isEmpty()) {
            throw new IllegalArgumentException("Seed must define opener and cycle actions: " + seedId);
        }
        validateActions(openerActions);
        for (int[] cycle : cycleActions) {
            if (cycle == null || cycle.length == 0) {
                throw new IllegalArgumentException("Seed cycle must not be empty: " + seedId);
            }
            validateActions(cycle);
        }
        if (erTargets == null || erTargets.isEmpty()) {
            throw new IllegalArgumentException("Seed ER targets must not be empty: " + seedId);
        }
        for (Map.Entry<String, Double> entry : erTargets.entrySet()) {
            requireText(entry.getKey(), "ER target character");
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value < 1.0) {
                throw new IllegalArgumentException("Invalid ER target for " + entry.getKey() + ": " + seedId);
            }
        }
        if (sourceAssumptions == null || simulatorAssumptions == null) {
            throw new IllegalArgumentException("Seed assumption lists must not be null: " + seedId);
        }
        if (requireHash && (contentHash == null
                || !contentHash.matches("[0-9a-f]{64}")
                || !contentHash.equals(calculateContentHash()))) {
            throw new IllegalArgumentException("Seed content hash mismatch: " + seedId);
        }
    }

    /** Returns the canonical SHA-256 identity of this seed's reviewed content. */
    public String calculateContentHash() {
        JsonObject object = GSON.toJsonTree(this).getAsJsonObject();
        object.remove("contentHash");
        String canonical = canonicalize(object);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Returns whether the reviewed seed may enter replay or search. */
    public boolean isUsable() {
        return adaptationStatus() != AdaptationStatus.REJECTED;
    }

    private AdaptationStatus adaptationStatus() {
        if (adaptationStatus == null) {
            throw new IllegalArgumentException("adaptationStatus must not be null: " + seedId);
        }
        try {
            return AdaptationStatus.valueOf(adaptationStatus.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown adaptation status: " + adaptationStatus, exception);
        }
    }

    private static void validateActions(int[] actions) {
        for (int actionId : actions) {
            PolicyAction.fromId(actionId);
        }
    }

    private static List<int[]> copyCycles(List<int[]> cycles) {
        if (cycles == null) {
            return null;
        }
        List<int[]> copy = new ArrayList<>();
        for (int[] cycle : cycles) {
            copy.add(cycle == null ? null : cycle.clone());
        }
        return Collections.unmodifiableList(copy);
    }

    private static String canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<String> keys = new ArrayList<>(object.keySet());
            keys.sort(Comparator.naturalOrder());
            StringBuilder value = new StringBuilder("{");
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) {
                    value.append(',');
                }
                String key = keys.get(index);
                value.append(GSON.toJson(key)).append(':').append(canonicalize(object.get(key)));
            }
            return value.append('}').toString();
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < array.size(); index++) {
                if (index > 0) {
                    value.append(',');
                }
                value.append(canonicalize(array.get(index)));
            }
            return value.append(']').toString();
        }
        return GSON.toJson(element);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public String getSeedId() {
        return seedId;
    }

    public String getPartyName() {
        return partyName;
    }

    public String getScenarioFingerprint() {
        return scenarioFingerprint;
    }

    public List<String> getSourceIds() {
        return List.copyOf(sourceIds);
    }

    public AdaptationStatus getAdaptationStatus() {
        return adaptationStatus();
    }

    public String getContentHash() {
        return contentHash;
    }

    public int[] getOpenerActions() {
        return openerActions.clone();
    }

    public List<int[]> getCycleActions() {
        return copyCycles(cycleActions);
    }

    public Map<String, Double> getErTargets() {
        return erTargets;
    }
}
