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
    /** Yelan's Exquisite Throw rule: 2 seconds or three suppressed hits. */
    YelanBurst,
    /** Yelan's Breakthrough Barb rule: 0.3 seconds or four suppressed hits. */
    YelanBreakthrough,
    /** Tighnari Clusterbloom rule: 2.5 seconds or four suppressed hits. */
    TighnariClusterbloom,
    /** Alhaitham Projection rule: 12 seconds or two suppressed hits. */
    AlhaithamProjection,
    /** Alhaitham infused Charged rule: two-second time gate only. */
    AlhaithamCharged,
    /** Nahida Tri-Karma Purification rule: one-second time gate only. */
    NahidaTriKarma,
    /** Nilou's Tranquility Aura rule: 1.9-second time gate only. */
    NilouTranquility,
    /** Wanderer's C6 follow-up rule: two-second time gate only. */
    WandererC6,
    /** Emilie's Lumidouce Case rule: two-second time gate only. */
    EmilieLumidouce,
    /** Sigewinne's Bubblebalm rule: two-second time gate only. */
    SigewinneBubblebalm,
    /** Sigewinne's Burst pulse rule: 1.9-second time gate only. */
    SigewinneBurst,
    /** Ororon's Soundwave rule: three-second time gate only. */
    OroronSoundwave,
    /** Reserved for custom ICD rules not yet implemented. */
    Special // Custom rules (not implemented yet)
}
