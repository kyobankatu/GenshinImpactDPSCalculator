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
        /** Simulation time of the last successful element application. Initialised to {@code -10.0} to allow immediate first application. */
        double lastAppTime = -10.0;

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
                state.hitCount++;
            } else {
                state.hitCount++;
                if (state.hitCount >= 3) {
                    apply = true;
                    state.hitCount = 0;
                }
            }
        }

        return apply;
    }
}
