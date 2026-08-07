package mechanics.element;

import model.type.ICDType;
import model.type.ICDTag;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks Internal Cooldown (ICD) state per character per skill group to
 * determine whether an elemental hit actually applies an element to the enemy.
 *
 * <p>Standard ICD rule (Genshin Impact):
 * <ul>
 *   <li>The first hit of a group always applies the element and resets both
 *       the timer and the hit counter.</li>
 *   <li>Subsequent hits in the same group apply the element only if at least
 *       2.5 seconds have elapsed since the last application, <em>or</em> if
 *       3 hits have accumulated since the last application (whichever comes
 *       first).</li>
 * </ul>
 *
 * <p>ICD groups are keyed by {@code "<charName>_<ICDTag.name()>"} so different
 * skills on the same character maintain independent cooldowns.
 */
public class ICDManager {

    /**
     * Mutable ICD tracking state for one (character, tag) group.
     */
    private static class ICDState {
        /** Last successful application; negative infinity admits every first hit. */
        double lastAppTime = Double.NEGATIVE_INFINITY;

        /** Number of hits counted since the last successful element application. */
        int hitCount = 0;
    }

    // Key: CharacterName + "_" + ICDTag.name()
    private Map<String, ICDState> states = new HashMap<>();

    /**
     * Returns a snapshot of all current ICD states.
     * Each entry maps the group key to a two-element array: {@code [lastAppTime, hitCount]}.
     *
     * @return copy of ICD state map
     */
    public Map<String, double[]> saveStates() {
        Map<String, double[]> copy = new HashMap<>();
        for (Map.Entry<String, ICDState> entry : states.entrySet()) {
            copy.put(entry.getKey(), new double[] { entry.getValue().lastAppTime, entry.getValue().hitCount });
        }
        return copy;
    }

    /**
     * Restores ICD states from a previously captured snapshot.
     *
     * @param saved snapshot produced by {@link #saveStates()}
     */
    public void restoreStates(Map<String, double[]> saved) {
        states.clear();
        for (Map.Entry<String, double[]> entry : saved.entrySet()) {
            ICDState state = new ICDState();
            state.lastAppTime = entry.getValue()[0];
            state.hitCount = (int) entry.getValue()[1];
            states.put(entry.getKey(), state);
        }
    }

    /**
     * Checks whether an elemental hit in the given ICD group should apply the
     * element, and updates the group's state accordingly.
     *
     * <p>Behaviour by {@link ICDType}:
     * <ul>
     *   <li>{@link ICDType#None} – always returns {@code true}; no state is
     *       modified (no ICD).</li>
     *   <li>{@link ICDType#Standard} – applies the standard 2.5s / 3-hit rule
     *       described in the class documentation.</li>
     *   <li>{@link ICDType#YelanBurst} – applies after 2 seconds or three
     *       suppressed hits.</li>
     *   <li>{@link ICDType#YelanBreakthrough} – applies after 0.3 seconds or
     *       four suppressed hits.</li>
     *   <li>{@link ICDType#TighnariClusterbloom} – applies after 2.5 seconds
     *       or four suppressed hits.</li>
     *   <li>{@link ICDType#AlhaithamProjection} – applies after 12 seconds
     *       or two suppressed hits.</li>
     *   <li>{@link ICDType#AlhaithamCharged} – applies after two seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#NahidaTriKarma} – applies after one second;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#NilouTranquility} – applies after 1.9 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#WandererC6} – applies after two seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#EmilieLumidouce} – applies after two seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#SigewinneBubblebalm} – applies after two seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#SigewinneBurst} – applies after 1.9 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#OroronSoundwave} – applies after three seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#AinoDucky} – applies after 1.8 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#ESCOFFIER_SKILL} – applies after 1.5 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#CitlaliFrostfallStorm} – applies after 1.5 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#KinichLoopShot} – applies after two seconds or
     *       after four hits in its private group.</li>
     *   <li>{@link ICDType#KinichScalespikerCannon} – applies after 1.2 seconds
     *       or after four hits in its private group.</li>
     *   <li>{@link ICDType#ArlecchinoCharged} – applies after 0.5 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#ArlecchinoElementalArt} – applies after ten
     *       seconds or after two suppressed hits.</li>
     *   <li>{@link ICDType#FurinaSalonSolitaire} – applies after 30 seconds or
     *       on the first and every other hit in each Salon Member tag.</li>
     *   <li>{@link ICDType#YumemizukiMizukiDreamdrifter} – applies after
     *       1.2 seconds; hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#ClorindeElementalArt} – applies after one second;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#DurinBlackSkill} – applies after 0.3 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#DurinWhiteBurst} – applies after 1.5 seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#DurinBlackBurst} – applies after two seconds;
     *       hit count never bypasses the time gate.</li>
     *   <li>{@link ICDType#ChascaAlternating} – applies after 1.5 seconds or
     *       after two hits in its private shell group.</li>
     * </ul>
     *
     * <p>{@code null} values for {@code type} or {@code tag} are silently
     * defaulted to {@link ICDType#Standard} and {@link ICDTag#None} respectively.
     *
     * @param charName    name of the attacking character
     * @param tag         ICD group tag identifying the skill/attack category
     * @param type        the ICD rule to apply
     * @param currentTime current simulation time in seconds
     * @return {@code true} if the element should be applied to the enemy,
     *         {@code false} otherwise
     */
    public boolean checkApplication(String charName, ICDTag tag, ICDType type, double currentTime) {
        if (type == null)
            type = ICDType.Standard;
        if (tag == null)
            tag = ICDTag.None;

        if (type == ICDType.None) {
            return true;
        }

        String key = charName + "_" + tag.name();

        if (!states.containsKey(key)) {
            states.put(key, new ICDState());
        }
        ICDState state = states.get(key);
        if (state == null) {
            state = new ICDState();
            states.put(key, state);
        }

        boolean apply = false;

        if (type == ICDType.Standard) {
            if (currentTime - state.lastAppTime >= 2.5) {
                apply = true;
                state.lastAppTime = currentTime;
                state.hitCount = 0;
            } else {
                state.hitCount++;
                if (state.hitCount >= 3) {
                    apply = true;
                    state.lastAppTime = currentTime;
                    state.hitCount = 0;
                }
            }
        } else if (type == ICDType.YelanBurst) {
            apply = checkCustomApplication(state, currentTime, 2.0, 3);
        } else if (type == ICDType.YelanBreakthrough) {
            apply = checkCustomApplication(state, currentTime, 0.3, 4);
        } else if (type == ICDType.TighnariClusterbloom) {
            apply = checkCustomApplication(state, currentTime, 2.5, 4);
        } else if (type == ICDType.AlhaithamProjection) {
            apply = checkCustomApplication(state, currentTime, 12.0, 2);
        } else if (type == ICDType.AlhaithamCharged) {
            apply = checkTimeOnlyApplication(state, currentTime, 2.0);
        } else if (type == ICDType.NahidaTriKarma) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.0);
        } else if (type == ICDType.NilouTranquility) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.9);
        } else if (type == ICDType.WandererC6) {
            apply = checkTimeOnlyApplication(state, currentTime, 2.0);
        } else if (type == ICDType.EmilieLumidouce) {
            apply = checkTimeOnlyApplication(state, currentTime, 2.0);
        } else if (type == ICDType.SigewinneBubblebalm) {
            apply = checkTimeOnlyApplication(state, currentTime, 2.0);
        } else if (type == ICDType.SigewinneBurst) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.9);
        } else if (type == ICDType.OroronSoundwave) {
            apply = checkTimeOnlyApplication(state, currentTime, 3.0);
        } else if (type == ICDType.AinoDucky) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.8);
        } else if (type == ICDType.ESCOFFIER_SKILL) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.5);
        } else if (type == ICDType.CitlaliFrostfallStorm) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.5);
        } else if (type == ICDType.KinichLoopShot) {
            apply = checkCustomApplication(state, currentTime, 2.0, 4);
        } else if (type == ICDType.KinichScalespikerCannon) {
            apply = checkCustomApplication(state, currentTime, 1.2, 4);
        } else if (type == ICDType.ArlecchinoCharged) {
            apply = checkTimeOnlyApplication(state, currentTime, 0.5);
        } else if (type == ICDType.ArlecchinoElementalArt) {
            apply = checkCustomApplication(state, currentTime, 10.0, 2);
        } else if (type == ICDType.FurinaSalonSolitaire) {
            apply = checkCustomApplication(state, currentTime, 30.0, 2);
        } else if (type == ICDType.YumemizukiMizukiDreamdrifter) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.2);
        } else if (type == ICDType.ClorindeElementalArt) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.0);
        } else if (type == ICDType.DurinBlackSkill) {
            apply = checkTimeOnlyApplication(state, currentTime, 0.3);
        } else if (type == ICDType.DurinWhiteBurst) {
            apply = checkTimeOnlyApplication(state, currentTime, 1.5);
        } else if (type == ICDType.DurinBlackBurst) {
            apply = checkTimeOnlyApplication(state, currentTime, 2.0);
        } else if (type == ICDType.ChascaAlternating) {
            apply = checkCustomApplication(state, currentTime, 1.5, 2);
        }

        return apply;
    }

    private boolean checkCustomApplication(
            ICDState state,
            double currentTime,
            double resetInterval,
            int suppressedHitThreshold) {
        if (currentTime - state.lastAppTime + 1e-9 >= resetInterval) {
            state.lastAppTime = currentTime;
            state.hitCount = 0;
            return true;
        }
        state.hitCount++;
        if (state.hitCount >= suppressedHitThreshold) {
            state.lastAppTime = currentTime;
            state.hitCount = 0;
            return true;
        }
        return false;
    }

    private boolean checkTimeOnlyApplication(
            ICDState state,
            double currentTime,
            double resetInterval) {
        if (currentTime - state.lastAppTime + 1e-9 < resetInterval) {
            return false;
        }
        state.lastAppTime = currentTime;
        state.hitCount = 0;
        return true;
    }
}
