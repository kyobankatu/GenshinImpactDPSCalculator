package mechanics.rl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import model.entity.FormStateProvider;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.Element;
import simulation.CombatSimulator;

/**
 * Encodes simulator state into a fixed-size observation vector.
 *
 * <p>Layout: {@code NUM_CHARS} blocks of {@link #FEATURES_PER_CHARACTER} features
 * followed by {@link #GLOBAL_FEATURES} global features.
 *
 * <p>Per-character block (29 dims):
 * <ul>
 *   <li>[0–9] dynamic: energy ratio, isActive, skill readiness, burst readiness,
 *       isFormActive, active-buff ratio, max-buff-remaining, time-since-last-active,
 *       on-field fraction, cumulative damage share</li>
 *   <li>[10–17] static: element one-hot (8 dims, {@link Element#values()} order)</li>
 *   <li>[18–28] static: capability and value-curve profile scores</li>
 * </ul>
 *
 * <p>Global block (7 dims): swap readiness, time remaining, PYRO/HYDRO/ELECTRO/ANEMO
 * aura, thundercloud active.
 */
public class ObservationEncoder {
    public static final int NUM_CHARS = 4;
    /** Number of dynamic features per character slot. */
    public static final int CHAR_DYNAMIC_FEATURES = 10;
    /** Number of static features per character slot (8 element one-hot + capability scores). */
    public static final int CHAR_STATIC_FEATURES = 8 + CapabilityProfile.SIZE;
    /** Total features per character slot. */
    public static final int FEATURES_PER_CHARACTER = CHAR_DYNAMIC_FEATURES + CHAR_STATIC_FEATURES;
    /** Number of global features appended after all character blocks. */
    public static final int GLOBAL_FEATURES = 7;
    /** Total observation vector length. */
    public static final int OBSERVATION_SIZE = FEATURES_PER_CHARACTER * NUM_CHARS + GLOBAL_FEATURES;

    private static final String DEFAULT_PROFILES_PATH = "config/capability_profiles/profiles.json";

    private final CapabilityProfileStore profileStore;

    /**
     * Constructs an encoder that loads capability profiles from the default path
     * ({@code config/capability_profiles/profiles.json}).
     */
    public ObservationEncoder() {
        this(new CapabilityProfileStore(DEFAULT_PROFILES_PATH));
    }

    /**
     * Constructs an encoder with an explicitly provided capability profile store.
     *
     * @param profileStore capability profile store; must not be null
     */
    public ObservationEncoder(CapabilityProfileStore profileStore) {
        this.profileStore = profileStore != null ? profileStore : CapabilityProfileStore.empty();
    }

    /**
     * Encodes the current simulator state into a new observation array.
     *
     * @param sim          current combat simulator
     * @param config       episode configuration
     * @param lastSwapTime simulation time of the most recent swap action
     * @return observation array of length {@link #OBSERVATION_SIZE}
     */
    public double[] encode(CombatSimulator sim, EpisodeConfig config, double lastSwapTime) {
        double[] observation = new double[OBSERVATION_SIZE];
        fillObservation(sim, config, lastSwapTime, observation);
        return observation;
    }

    /**
     * Fills an existing observation array in-place.
     *
     * @param sim          current combat simulator
     * @param config       episode configuration
     * @param lastSwapTime simulation time of the most recent swap action
     * @param observation  target array; must have length {@link #OBSERVATION_SIZE}
     */
    public void fillObservation(CombatSimulator sim, EpisodeConfig config, double lastSwapTime, double[] observation) {
        fillObservation(sim, config, lastSwapTime, null, null, observation);
    }

    /**
     * Fills an existing observation array in-place with per-slot history features.
     *
     * @param sim                current combat simulator
     * @param config             episode configuration
     * @param lastSwapTime       simulation time of the most recent swap action
     * @param slotLastActiveTime last sim time each slot was on-field; null falls back to zeros
     * @param slotOnFieldTime    cumulative on-field seconds per slot; null falls back to zeros
     * @param observation        target array; must have length {@link #OBSERVATION_SIZE}
     */
    public void fillObservation(CombatSimulator sim, EpisodeConfig config, double lastSwapTime,
            double[] slotLastActiveTime, double[] slotOnFieldTime, double[] observation) {
        double now = sim.getCurrentTime();
        double totalDamage = sim.getTotalDamage();
        int index = 0;

        for (int slot = 0; slot < config.partyOrder.length; slot++) {
            CharacterId id = config.partyOrder[slot];
            Character character = sim.getCharacter(id);
            if (character == null) {
                index += FEATURES_PER_CHARACTER;
                continue;
            }

            // --- Dynamic features (10) ---
            double energyCost = Math.max(1.0, character.getEnergyCost());
            observation[index++] = clamp01(character.getCurrentEnergy() / energyCost);
            observation[index++] = sim.getActiveCharacter() != null
                    && sim.getActiveCharacter().getCharacterId() == id ? 1.0 : 0.0;
            double skillCD = Math.max(1.0, character.getSkillCD());
            observation[index++] = 1.0 - clamp01(character.getSkillCDRemaining(now) / skillCD);
            double burstCD = Math.max(1.0, character.getBurstCD());
            observation[index++] = 1.0 - clamp01(character.getBurstCDRemaining(now) / burstCD);
            observation[index++] = isFormActive(character, now) ? 1.0 : 0.0;
            observation[index++] = clamp01(countActiveBuffs(character, now) / 6.0);
            // history-derived dynamic features
            observation[index++] = maxBuffRemaining(character, now);
            double lastActive = slotLastActiveTime != null ? slotLastActiveTime[slot] : -config.maxEpisodeTime;
            observation[index++] = clamp01((now - lastActive) / Math.max(1.0, config.maxEpisodeTime));
            double onField = slotOnFieldTime != null ? slotOnFieldTime[slot] : 0.0;
            observation[index++] = clamp01(onField / Math.max(1.0, now));
            observation[index++] = totalDamage > 0.0
                    ? clamp01(sim.getDamageByCharacter(id) / totalDamage)
                    : 0.25;

            // --- Static features: element one-hot (8) ---
            Element charElement = character.getElement();
            for (Element el : Element.values()) {
                observation[index++] = (charElement == el) ? 1.0 : 0.0;
            }

            // --- Static features: capability scores ---
            double[] profile = profileStore.getProfile(id);
            for (int profileIndex = 0; profileIndex < CapabilityProfile.SIZE; profileIndex++) {
                observation[index++] = profile[profileIndex];
            }
        }

        Enemy enemy = sim.getEnemy();
        observation[index++] = clamp01((now - lastSwapTime) / Math.max(1.0, config.swapCooldown));
        observation[index++] = clamp01((config.maxEpisodeTime - now) / config.maxEpisodeTime);
        observation[index++] = normalizedAura(enemy, Element.PYRO);
        observation[index++] = normalizedAura(enemy, Element.HYDRO);
        observation[index++] = normalizedAura(enemy, Element.ELECTRO);
        observation[index++] = normalizedAura(enemy, Element.ANEMO);
        observation[index] = sim.getThundercloudEndTime() > now ? 1.0 : 0.0;
    }

    private boolean isFormActive(Character character, double currentTime) {
        return character instanceof FormStateProvider
                && ((FormStateProvider) character).isFormActive(currentTime);
    }

    private double countActiveBuffs(Character character, double currentTime) {
        int count = 0;
        for (mechanics.buff.Buff buff : character.getActiveBuffs()) {
            if (!buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    private double maxBuffRemaining(Character character, double currentTime) {
        double maxRemaining = 0.0;
        for (mechanics.buff.Buff buff : character.getActiveBuffs()) {
            if (!buff.isExpired(currentTime)) {
                maxRemaining = Math.max(maxRemaining, buff.getRemainingDuration(currentTime));
            }
        }
        return clamp01(maxRemaining / 15.0);
    }

    private double normalizedAura(Enemy enemy, Element element) {
        if (enemy == null) {
            return 0.0;
        }
        return clamp01(enemy.getAuraUnits(element) / 2.0);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Loads and caches character capability profiles from a JSON file.
     *
     * <p>Profiles are read once at construction time and never reloaded.
     * If a character ID is not found, all scores default to zero
     * and a warning is printed at most once per missing ID per JVM process.
     */
    public static class CapabilityProfileStore {
        private static final double[] ZERO_PROFILE = CapabilityProfile.zeros();
        private static final Set<String> WARNED_IDS = Collections.synchronizedSet(new java.util.HashSet<>());

        private final Map<String, double[]> profiles = new LinkedHashMap<>();
        private final boolean strict;

        public static CapabilityProfileStore empty() {
            return new CapabilityProfileStore(false);
        }

        private CapabilityProfileStore(boolean strict) {
            this.strict = strict;
        }

        /**
         * Constructs a store by loading profiles from the given file path.
         * If the file cannot be read, an empty store is used and a warning is printed.
         *
         * @param path path to the capability profiles JSON file
         */
        public CapabilityProfileStore(String path) {
            this.strict = true;
            loadFromFile(path);
        }

        /**
         * Returns the capability profile for the given character ID.
         * If no profile is found, returns zeros and logs a one-time warning.
         *
         * @param id character ID
         * @return capability profile values in {@link CapabilityProfile} order
         */
        public double[] getProfile(CharacterId id) {
            double[] profile = profiles.get(id.name());
            if (profile == null) {
                if (strict) {
                    throw new IllegalStateException("Missing capability profile for " + id.name());
                }
                if (WARNED_IDS.add(id.name())) {
                    System.out.println("[ObservationEncoder] WARNING: no capability profile for "
                            + id.name() + "; using zeros");
                }
                return ZERO_PROFILE;
            }
            return profile;
        }

        private void loadFromFile(String path) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        throw new IllegalStateException("Capability profile entry must be an object: " + entry.getKey());
                    }
                    double[] profile = CapabilityProfile.zeros();
                    JsonObject block = entry.getValue().getAsJsonObject();
                    profile[CapabilityProfile.OFF_FIELD_DPS_RATIO] = extractDouble(block, "off_field_dps_ratio");
                    profile[CapabilityProfile.TEAM_BUFF_SCORE] = extractDouble(block, "team_buff_score");
                    profile[CapabilityProfile.SELF_ENHANCEMENT_SCORE] = extractDouble(block, "self_enhancement_score");
                    profile[CapabilityProfile.ENERGY_GENERATION_SCORE] = extractDouble(block, "energy_generation_score");
                    profile[CapabilityProfile.ENTRY_VALUE_SCORE] = extractDouble(block, "entry_value_score");
                    profile[CapabilityProfile.SUSTAIN_VALUE_3_ACTIONS] = extractDouble(block, "sustain_value_3_actions");
                    profile[CapabilityProfile.SUSTAIN_VALUE_6_ACTIONS] = extractDouble(block, "sustain_value_6_actions");
                    profile[CapabilityProfile.EXIT_COST_SCORE] = extractDouble(block, "exit_cost_score");
                    profile[CapabilityProfile.REENTRY_COST_SCORE] = extractDouble(block, "reentry_cost_score");
                    profile[CapabilityProfile.ON_FIELD_DPS_SCORE] = extractDouble(block, "on_field_dps_score");
                    profile[CapabilityProfile.BURST_WINDOW_SCORE] = extractDouble(block, "burst_window_score");
                    profiles.put(entry.getKey(), profile);
                }
                System.out.println("[ObservationEncoder] Loaded capability profiles for: " + profiles.keySet());
            } catch (IOException | IllegalStateException | JsonParseException e) {
                if (strict) {
                    throw new IllegalStateException("Could not load capability profiles from " + path, e);
                }
                System.out.println("[ObservationEncoder] WARNING: could not load profiles from "
                        + path + ": " + e.getMessage());
            }
        }

        private static double extractDouble(JsonObject block, String key) {
            JsonElement value = block.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                return value.getAsDouble();
            }
            return 0.0;
        }
    }
}
