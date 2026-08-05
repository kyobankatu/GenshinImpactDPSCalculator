package model.type;

/**
 * Labels the ICD group that an elemental application belongs to.
 *
 * <p>Each distinct {@code ICDTag} value maintains its own independent hit
 * counter and timestamp within {@code ICDManager}. Hits sharing the same tag
 * consume the same ICD window; hits with different tags are tracked separately
 * and do not interfere with each other's cooldowns.
 *
 * <p>Generic tags map to the standard attack-type groups. Character-specific
 * tags are provided for skills whose ICD grouping differs from the generic
 * category (e.g. Xingqiu's Rain Sword orbital hits are separate from his
 * Normal Attack ICD).
 */
public enum ICDTag {
    /**
     * No tag. When {@link ICDType} is {@link ICDType#Standard} and no specific
     * tag applies, hits share a common unnamed ICD window.
     */
    None, // No tag (usually means shared Standard ICD if Type is Standard)
    /** ICD group for Normal Attack hits. */
    NormalAttack,
    /** ICD group for Charged Attack hits. */
    ChargedAttack,
    /** ICD group for Plunge Attack hits. */
    PlungeAttack,
    /** ICD group for Elemental Skill hits. */
    ElementalSkill,
    /** ICD group for Elemental Burst hits. */
    ElementalBurst,

    // -----------------------------------------------------------------------
    // Character-specific ICD tags
    // Used when a character's sub-skill has an ICD group separate from the
    // generic ability category above.
    // -----------------------------------------------------------------------

    /** Xiangling's Pyronado spin hits (separate from her Skill ICD). */
    Xiangling_Pyronado,
    /** Xingqiu's Rain Sword sword-rain hits during Normal Attacks. */
    Xingqiu_Raincutter,
    /** Xingqiu's zero-damage Rain Sword orbital contact pulses. */
    Xingqiu_Orbital,
    /** Raiden Shogun's Musou Isshin Normal Attack hits during Burst state. */
    Raiden_MusouIsshin,
    /** Bennett's Inspiration Field DoT talent hits. */
    Bennett_Talent,
    /** Columbina's initial cast hit. */
    Columbina_Cast,
    /** Columbina's Moonreel follow-up hits. */
    Columbina_Moonreel,
    /** Collei's Floral Sidewinder Sprout ticks. */
    Collei_Sprout,
    /** Yelan's Exquisite Throw projectiles. */
    Yelan_ExquisiteThrow,
    /** Yelan's Breakthrough Barb projectiles. */
    Yelan_Breakthrough,
    /** Tighnari's four shared-ICD Clusterbloom projectiles. */
    Tighnari_Clusterbloom,
    /** Cyno A1 Duststalker Bolts share one dedicated application group. */
    Cyno_DuststalkerBolt,
    /** Cyno C6 Duststalker Bolts use a separate standard application group. */
    Cyno_C6_DuststalkerBolt,
    /** Alhaitham Projection Attacks share their dedicated application group. */
    Alhaitham_Projection,
    /** Alhaitham's infused Charged hits share their time-only group. */
    Alhaitham_Charged,
    /** Kazuha's C6-infused Normal and Charged Attacks. */
    Kazuha_C6_Infusion,
    /** Charlotte's Snappy/Focused mark ticks. */
    Charlotte_Mark,
    /** Charlotte's Kamera Burst field ticks. */
    Charlotte_Kamera,
    /** Nahida's Tri-Karma Purification application group. */
    Nahida_TriKarma,
    /** Nahida C6 Karmic Oblivion application group. */
    Nahida_C6,
    /** Ayaka's fifth Normal uses a separate application group. */
    Ayaka_NormalFive,
    /** Ayaka's three Charged Attack hits share a dedicated group. */
    Ayaka_Charged,
    /** Ayaka's Senho exit application uses a dedicated group. */
    Ayaka_Dash,
    /** Nilou's Tranquility Aura application group. */
    Nilou_TranquilityAura,
    /** Baizhu C2 Gossamer Sprite uses an independent application group. */
    Baizhu_C2,
    /** Wanderer's C6 follow-up Normal Attacks use an independent group. */
    Wanderer_C6,
    /** Emilie's Lumidouce Case attacks share a private application group. */
    Emilie_Lumidouce
}
