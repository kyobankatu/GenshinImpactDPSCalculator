package model.type;

/**
 * Classifies the type of action performed by a character during the simulation.
 *
 * <p>Used by {@code AttackAction} and CSV multiplier files to identify which
 * talent category a given hit belongs to, so that the correct DMG Bonus%
 * modifiers (e.g. {@link StatType#NORMAL_ATTACK_DMG_BONUS},
 * {@link StatType#BURST_DMG_BONUS}) and ICD rules are applied.
 *
 * <p>The values match the {@code AbilityType} header values in the character
 * CSV files (stored as uppercase strings).
 */
public enum ActionType {
    /** Normal Attack (left-click chain). */
    NORMAL,
    /** Charged Attack (stamina-consuming follow-up or hold attack). */
    CHARGE,
    /** Plunge Attack (falling aerial attack). */
    PLUNGE,
    /** Elemental Skill (E key). */
    SKILL,
    /** Elemental Burst (Q key). */
    BURST,
    /** Dash / sprint action; does not deal damage. */
    DASH,
    /** Catch-all for actions that do not fit the above categories. */
    OTHER
}
