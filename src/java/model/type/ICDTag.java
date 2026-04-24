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
    /** Xingqiu's Rain Sword orbital shield hits. */
    Xingqiu_Orbital,
    /** Raiden Shogun's Musou Isshin Normal Attack hits during Burst state. */
    Raiden_MusouIsshin,
    /** Bennett's Inspiration Field DoT talent hits. */
    Bennett_Talent,
    /** Columbina's initial cast hit. */
    Columbina_Cast,
    /** Columbina's Moonreel follow-up hits. */
    Columbina_Moonreel
}
