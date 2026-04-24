package model.type;

/**
 * Defines the Internal Cooldown (ICD) behaviour for an elemental application.
 *
 * <p>
 * ICD controls how frequently a character's hits can apply an elemental
 * aura to enemies. The {@link mechanics.element.ICDManager} uses this type
 * together with
 * {@link ICDTag} to track per-group hit counters and timestamps.
 */
public enum ICDType {
    /** Every hit applies the element; no cooldown restriction. */
    None, // Every hit applies element
    /**
     * Standard Genshin ICD rule: element is applied on the 1st hit of a group,
     * then suppressed until either 2.5 seconds have elapsed or 3 hits in the
     * same ICD group have been recorded, whichever comes first.
     */
    Standard, // 2.5s or 3 hits
    /** Reserved for custom ICD rules not yet implemented. */
    Special // Custom rules (not implemented yet)
}
