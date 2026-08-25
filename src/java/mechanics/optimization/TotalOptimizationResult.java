package mechanics.optimization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import model.type.StatType;
import model.type.CharacterId;

/**
 * Holds the complete results of the {@link OptimizerPipeline} after both
 * Phase 1 (ER calibration) and Phase 2 (DPS hill-climbing) have finished.
 *
 * <p>Pass this result to the final simulation factory to reproduce the
 * optimized party configuration.
 */
public class TotalOptimizationResult {
    /** Revision identifying the aggregate KQMS allocator and optimizer contract. */
    public static final String BUILD_MODE = "optimized-kqms-v1";

    /**
     * Converged minimum Energy Recharge targets per character id.
     * Values are fractional (e.g. {@code 2.50} represents 250% ER).
     */
    public final Map<CharacterId, Double> erTargets;

    /**
     * Final merged liquid roll allocation per character id.
     * Each inner map contains {@code StatType -> rollCount} pairs and
     * includes both the DPS-optimized rolls and the pre-reserved ER rolls.
     */
    public final Map<CharacterId, Map<StatType, Integer>> partyRolls;

    private final String buildHash;

    /**
     * @param erTargets   converged ER targets produced by Phase 1
     * @param partyRolls  final merged roll map produced after Phase 2
     */
    public TotalOptimizationResult(Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyRolls) {
        if (erTargets == null || partyRolls == null || erTargets.isEmpty() || partyRolls.isEmpty()) {
            throw new IllegalArgumentException("Optimized ER targets and party rolls must not be empty");
        }
        this.erTargets = immutableErTargets(erTargets);
        this.partyRolls = immutablePartyRolls(partyRolls);
        if (!this.erTargets.keySet().equals(this.partyRolls.keySet())) {
            throw new IllegalArgumentException("Optimized ER targets and roll characters must match");
        }
        this.buildHash = calculateBuildHash();
    }

    /** Returns the stable build identity included in scenario and dataset fingerprints. */
    public String getBuildFingerprint() {
        return BUILD_MODE + ":" + buildHash;
    }

    private String calculateBuildHash() {
        StringBuilder canonical = new StringBuilder(BUILD_MODE);
        for (CharacterId characterId : CharacterId.values()) {
            if (!erTargets.containsKey(characterId)) {
                continue;
            }
            canonical.append("|er:").append(characterId.name()).append('=')
                    .append(Double.toHexString(erTargets.get(characterId)));
            Map<StatType, Integer> rolls = partyRolls.get(characterId);
            for (StatType statType : StatType.values()) {
                if (rolls.containsKey(statType)) {
                    canonical.append("|roll:").append(characterId.name()).append(':')
                            .append(statType.name()).append('=').append(rolls.get(statType));
                }
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Map<CharacterId, Double> immutableErTargets(Map<CharacterId, Double> source) {
        Map<CharacterId, Double> copy = new EnumMap<>(CharacterId.class);
        for (Map.Entry<CharacterId, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || !Double.isFinite(entry.getValue()) || entry.getValue() < 1.0) {
                throw new IllegalArgumentException("Invalid optimized ER target");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<CharacterId, Map<StatType, Integer>> immutablePartyRolls(
            Map<CharacterId, Map<StatType, Integer>> source) {
        Map<CharacterId, Map<StatType, Integer>> copy = new LinkedHashMap<>();
        for (CharacterId characterId : CharacterId.values()) {
            Map<StatType, Integer> sourceRolls = source.get(characterId);
            if (sourceRolls == null) {
                continue;
            }
            Map<StatType, Integer> rolls = new EnumMap<>(StatType.class);
            for (Map.Entry<StatType, Integer> entry : sourceRolls.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0) {
                    throw new IllegalArgumentException("Invalid optimized artifact roll");
                }
                rolls.put(entry.getKey(), entry.getValue());
            }
            copy.put(characterId, Collections.unmodifiableMap(rolls));
        }
        return Collections.unmodifiableMap(copy);
    }
}
