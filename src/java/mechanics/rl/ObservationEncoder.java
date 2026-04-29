package mechanics.rl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.entity.BurstStateProvider;
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
 * <p>Per-character block (18 dims):
 * <ul>
 *   <li>[0–5] dynamic: energy ratio, isActive, skill readiness, burst readiness,
 *       isBurstActive, active-buff ratio</li>
 *   <li>[6–13] static: element one-hot (8 dims, {@link Element#values()} order)</li>
 *   <li>[14–17] static: off-field DPS ratio, team-buff score,
 *       self-enhancement score, energy-generation score</li>
 * </ul>
 *
 * <p>Global block (7 dims): swap readiness, time remaining, PYRO/HYDRO/ELECTRO/ANEMO
 * aura, thundercloud active.
 */
public class ObservationEncoder {
    /** Number of dynamic features per character slot. */
    public static final int CHAR_DYNAMIC_FEATURES = 6;
    /** Number of static features per character slot (8 element one-hot + 4 capability scores). */
    public static final int CHAR_STATIC_FEATURES = 12;
    /** Total features per character slot. */
    public static final int FEATURES_PER_CHARACTER = CHAR_DYNAMIC_FEATURES + CHAR_STATIC_FEATURES;
    /** Number of global features appended after all character blocks. */
    public static final int GLOBAL_FEATURES = 7;
    /** Total observation vector length. */
    public static final int OBSERVATION_SIZE = FEATURES_PER_CHARACTER * 4 + GLOBAL_FEATURES;

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
        double now = sim.getCurrentTime();
        int index = 0;

        for (CharacterId id : config.partyOrder) {
            Character character = sim.getCharacter(id);
            if (character == null) {
                index += FEATURES_PER_CHARACTER;
                continue;
            }

            // --- Dynamic features (6) ---
            double energyCost = Math.max(1.0, character.getEnergyCost());
            observation[index++] = clamp01(character.getCurrentEnergy() / energyCost);
            observation[index++] = sim.getActiveCharacter() != null
                    && sim.getActiveCharacter().getCharacterId() == id ? 1.0 : 0.0;
            double skillCD = Math.max(1.0, character.getSkillCD());
            observation[index++] = 1.0 - clamp01(character.getSkillCDRemaining(now) / skillCD);
            double burstCD = Math.max(1.0, character.getBurstCD());
            observation[index++] = 1.0 - clamp01(character.getBurstCDRemaining(now) / burstCD);
            observation[index++] = isBurstActive(character, now) ? 1.0 : 0.0;
            observation[index++] = clamp01(countActiveBuffs(character, now) / 6.0);

            // --- Static features: element one-hot (8) ---
            Element charElement = character.getElement();
            for (Element el : Element.values()) {
                observation[index++] = (charElement == el) ? 1.0 : 0.0;
            }

            // --- Static features: capability scores (4) ---
            double[] profile = profileStore.getProfile(id);
            observation[index++] = profile[0];
            observation[index++] = profile[1];
            observation[index++] = profile[2];
            observation[index++] = profile[3];
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

    private boolean isBurstActive(Character character, double currentTime) {
        return character instanceof BurstStateProvider
                && ((BurstStateProvider) character).isBurstActive(currentTime);
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
     * If a character ID is not found, all four scores default to {@code [0,0,0,0]}
     * and a warning is printed at most once per missing ID per JVM process.
     */
    public static class CapabilityProfileStore {
        private static final double[] ZERO_PROFILE = new double[]{0.0, 0.0, 0.0, 0.0};
        private static final Set<String> WARNED_IDS = Collections.synchronizedSet(new java.util.HashSet<>());

        private final Map<String, double[]> profiles = new LinkedHashMap<>();

        public static CapabilityProfileStore empty() {
            return new CapabilityProfileStore();
        }

        private CapabilityProfileStore() {
        }

        /**
         * Constructs a store by loading profiles from the given file path.
         * If the file cannot be read, an empty store is used and a warning is printed.
         *
         * @param path path to the capability profiles JSON file
         */
        public CapabilityProfileStore(String path) {
            loadFromFile(path);
        }

        /**
         * Returns the capability profile for the given character ID.
         * If no profile is found, returns {@code [0.0, 0.0, 0.0, 0.0]} and logs a one-time warning.
         *
         * @param id character ID
         * @return double array of length 4: [off_field_ratio, team_buff_score,
         *         self_enhancement_score, energy_gen_score]
         */
        public double[] getProfile(CharacterId id) {
            double[] profile = profiles.get(id.name());
            if (profile == null) {
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
                Pattern charBlock = Pattern.compile("\"(\\w+)\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
                Matcher matcher = charBlock.matcher(content);
                while (matcher.find()) {
                    String charName = matcher.group(1);
                    String block = matcher.group(2);
                    double[] profile = new double[4];
                    profile[0] = extractDouble(block, "off_field_dps_ratio");
                    profile[1] = extractDouble(block, "team_buff_score");
                    profile[2] = extractDouble(block, "self_enhancement_score");
                    profile[3] = extractDouble(block, "energy_generation_score");
                    profiles.put(charName, profile);
                }
                System.out.println("[ObservationEncoder] Loaded capability profiles for: " + profiles.keySet());
            } catch (IOException e) {
                System.out.println("[ObservationEncoder] WARNING: could not load profiles from "
                        + path + ": " + e.getMessage());
            }
        }

        private static double extractDouble(String block, String key) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.eE+\\-]+)");
            Matcher m = p.matcher(block);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
            return 0.0;
        }
    }
}
