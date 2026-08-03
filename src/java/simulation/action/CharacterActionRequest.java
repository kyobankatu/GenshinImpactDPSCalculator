package simulation.action;

import java.util.Objects;

/**
 * Typed request for a non-hit character action.
 *
 * <p>This is the simulator's canonical dispatch payload for top-level character
 * actions such as Normal, Skill, or Burst.
 */
public final class CharacterActionRequest {
    private final CharacterActionKey key;
    private final SkillActionMode skillMode;

    private CharacterActionRequest(
            CharacterActionKey key,
            SkillActionMode skillMode) {
        this.key = Objects.requireNonNull(key, "key");
        this.skillMode = Objects.requireNonNull(skillMode, "skillMode");
    }

    /**
     * Creates a legacy-compatible request. Skill requests default to Press.
     *
     * @param key top-level action key
     * @return immutable action request
     */
    public static CharacterActionRequest of(CharacterActionKey key) {
        return new CharacterActionRequest(key, SkillActionMode.PRESS);
    }

    /**
     * Creates an explicit Press or Hold Skill request.
     *
     * @param mode requested Skill activation mode
     * @return immutable Skill request
     */
    public static CharacterActionRequest skill(SkillActionMode mode) {
        return new CharacterActionRequest(CharacterActionKey.SKILL, mode);
    }

    /** @return requested top-level action key */
    public CharacterActionKey getKey() {
        return key;
    }

    /**
     * Returns Skill mode metadata. Non-Skill and legacy requests carry Press.
     *
     * @return immutable Skill activation mode
     */
    public SkillActionMode getSkillMode() {
        return skillMode;
    }

    /** @return presentation-only action label */
    public String getLogLabel() {
        switch (key) {
            case NORMAL:
                return "attack";
            case CHARGE:
                return "charge";
            case SKILL:
                return skillMode == SkillActionMode.HOLD
                        ? "skill hold" : "skill";
            case BURST:
                return "burst";
            case DASH:
                return "dash";
            case PLUNGE:
                return "plunge";
            default:
                return key.name().toLowerCase();
        }
    }
}
