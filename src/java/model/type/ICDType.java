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
    /** Aino's enhanced Ducky rule: 1.8-second time gate only. */
    AinoDucky,
    /** Escoffier's Skill attacks: 1.5-second time gate only. */
    ESCOFFIER_SKILL,
    /** Citlali's Frostfall Storm rule: 1.5-second time gate only. */
    CitlaliFrostfallStorm,
    /** Kinich Loop Shot rule: two seconds or four hits. */
    KinichLoopShot,
    /** Kinich Scalespiker rule: 1.2 seconds or four hits. */
    KinichScalespikerCannon,
    /** Arlecchino's Charged Attack: 0.5-second time gate only. */
    ArlecchinoCharged,
    /** Arlecchino's Skill group: ten seconds or every third hit. */
    ArlecchinoElementalArt,
    /** Furina Salon rule: 30 seconds or first and every other tagged hit. */
    FurinaSalonSolitaire,
    /** Mizuki Dreamdrifter rule: 1.2-second time gate only. */
    YumemizukiMizukiDreamdrifter,
    /** Clorinde's coordinated Shade attacks: one-second time gate only. */
    ClorindeElementalArt,
    /** Durin's black Skill recast: 0.3-second time gate only. */
    DurinBlackSkill,
    /** Durin's white Burst ticks: 1.5-second time gate only. */
    DurinWhiteBurst,
    /** Durin's black Burst ticks: two-second time gate only. */
    DurinBlackBurst,
    /** Chasca shell groups apply on alternating hits or after 1.5 seconds. */
    ChascaAlternating,
    /** Reserved for custom ICD rules not yet implemented. */
    Special // Custom rules (not implemented yet)
}
