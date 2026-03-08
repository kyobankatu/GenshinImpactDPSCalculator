package model.type;

/**
 * Enumeration of every stat key used throughout the simulation.
 *
 * <p>This enum is the single source of truth for all stat identifiers.
 * Whenever a new stat is required (for a new character, weapon, artifact, or
 * mechanic) it must be added here first, then referenced from
 * {@link model.stats.StatsContainer}, {@code DamageCalculator}, and/or
 * {@code CombatSimulator} as appropriate.
 *
 * <p>Constants are grouped by functional area. Each group is described below.
 */
public enum StatType {

    // -----------------------------------------------------------------------
    // Primary stats: HP, ATK, DEF.
    // Each primary has three components:
    //   BASE_*    – flat value derived from character level/ascension
    //   *_PERCENT – additive percent bonus from artifacts/weapons (e.g. 0.466 = 46.6 %)
    //   *_FLAT    – flat bonus added after the percent scaling step
    // Final value = BASE * (1 + PERCENT) + FLAT
    // -----------------------------------------------------------------------

    /** Character's base HP from level/ascension. */
    BASE_HP,
    /** Additive HP percent bonus (e.g. from HP% sands). */
    HP_PERCENT,
    /** Flat HP bonus added after percent scaling. */
    HP_FLAT,

    /** Character's base ATK (character base + weapon base). */
    BASE_ATK,
    /** Additive ATK percent bonus. */
    ATK_PERCENT,
    /** Flat ATK bonus added after percent scaling. */
    ATK_FLAT,

    /** Character's base DEF from level/ascension. */
    BASE_DEF,
    /** Additive DEF percent bonus. */
    DEF_PERCENT,
    /** Flat DEF bonus added after percent scaling. */
    DEF_FLAT,

    // -----------------------------------------------------------------------
    // Core offensive / utility stats
    // -----------------------------------------------------------------------

    /** Crit rate as a decimal (e.g. 0.05 = 5 %). */
    CRIT_RATE,
    /** Crit DMG as a decimal (e.g. 0.50 = 50 %). */
    CRIT_DMG,
    /** Elemental Mastery flat value; amplifies reactions. */
    ELEMENTAL_MASTERY,
    /** Energy Recharge as a decimal (e.g. 1.0 = 100 %). */
    ENERGY_RECHARGE,

    // -----------------------------------------------------------------------
    // Elemental and Physical DMG Bonus%
    // Additive bonuses that scale outgoing damage for the matching element.
    // Also used as keys for enemy resistance values in Enemy.
    // -----------------------------------------------------------------------

    /** Pyro DMG Bonus%, also the resistance map key for Pyro RES. */
    PYRO_DMG_BONUS,
    /** Hydro DMG Bonus%, also the resistance map key for Hydro RES. */
    HYDRO_DMG_BONUS,
    /** Anemo DMG Bonus%, also the resistance map key for Anemo RES. */
    ANEMO_DMG_BONUS,
    /** Electro DMG Bonus%, also the resistance map key for Electro RES. */
    ELECTRO_DMG_BONUS,
    /** Dendro DMG Bonus%, also the resistance map key for Dendro RES. */
    DENDRO_DMG_BONUS,
    /** Cryo DMG Bonus%, also the resistance map key for Cryo RES. */
    CRYO_DMG_BONUS,
    /** Geo DMG Bonus%, also the resistance map key for Geo RES. */
    GEO_DMG_BONUS,
    /** Physical DMG Bonus%, also the resistance map key for Physical RES. */
    PHYSICAL_DMG_BONUS,
    /** Outgoing healing bonus%. */
    HEALING_BONUS,

    // -----------------------------------------------------------------------
    // Reaction-specific DMG Bonus stats
    // Applied on top of the standard DMG Bonus% for specific reaction types.
    // -----------------------------------------------------------------------

    /** DMG bonus that applies specifically to Electro-Charged hits. */
    ELECTRO_CHARGED_DMG_BONUS,

    // -----------------------------------------------------------------------
    // Custom "Lunar" mechanics (non-canonical / original content)
    // These stats support the Ineffa / Flins / Columbina character kit and have
    // no counterpart in the official game. See CLAUDE.md for full descriptions.
    // -----------------------------------------------------------------------

    /** DMG bonus for Lunar Charged attacks. */
    LUNAR_CHARGED_DMG_BONUS,
    /** DMG bonus for the Lunar Bloom reaction variant. */
    LUNAR_BLOOM_DMG_BONUS,
    /** DMG bonus for the Lunar Crystallize reaction variant. */
    LUNAR_CRYSTALLIZE_DMG_BONUS,
    /** Generic Lunar reaction DMG bonus applied by Columbina's Burst. */
    LUNAR_REACTION_DMG_BONUS_ALL, // Generic Lunar Bonus (Columbina Burst)
    /** Ascendant Blessing team bonus granted by Moonsign mechanics. */
    LUNAR_MOONSIGN_BONUS, // Ascendant Blessing (Moonsign Team Bonus)
    /** Additive base damage bonus used by Ineffa and Flins (applied before DMG%). */
    LUNAR_BASE_BONUS, // Additive Base (Ineffa/Flins)
    /**
     * Independent final damage multiplier used by Columbina's passive.
     * Scales as {@code (EM / 2000) * 1.5}; displayed as {@code ColMult} in
     * formula debug logs.
     */
    LUNAR_MULTIPLIER, // Independent Multiplier (Columbina Passive)
    /** Unique damage bonus exclusive to Flins. */
    LUNAR_UNIQUE_BONUS, // Unique Bonus (Flins)
    /**
     * Additional CRIT DMG that applies only on the Lunar Reaction DMG path.
     * Added to {@code CRIT_DMG} before the crit multiplier step in
     * {@link mechanics.formula.DamageCalculator} when
     * {@code action.isLunarConsidered()} is {@code true}.
     */
    LUNAR_REACTION_CRIT_DMG,

    // -----------------------------------------------------------------------
    // Transformative reaction bonus
    // -----------------------------------------------------------------------

    /** DMG bonus that applies to Swirl damage instances. */
    SWIRL_DMG_BONUS,

    // -----------------------------------------------------------------------
    // Attack-type and ability-specific DMG Bonus / Crit stats
    // -----------------------------------------------------------------------

    /** DMG bonus that applies to Normal Attack hits. */
    NORMAL_ATTACK_DMG_BONUS,
    /** DMG bonus that applies to Charged Attack hits. */
    CHARGED_ATTACK_DMG_BONUS,
    /** DMG bonus that applies to Elemental Skill hits. */
    SKILL_DMG_BONUS,
    /** Crit rate bonus that applies specifically to Elemental Skill hits. */
    SKILL_CRIT_RATE,
    /** DMG bonus that applies to Elemental Burst hits. */
    BURST_DMG_BONUS,
    /** Crit rate bonus that applies specifically to Elemental Burst hits. */
    BURST_CRIT_RATE,
    /** Attack speed bonus (multiplicative on animation duration). */
    ATK_SPD,

    /** Generic all-damage DMG bonus% applied to every damage instance. */
    DMG_BONUS_ALL,
    /** Flat damage added to the Additive Bonus step of the damage formula. */
    FLAT_DMG_BONUS,

    // -----------------------------------------------------------------------
    // Debuffs / Enemy mitigation reduction stats
    // -----------------------------------------------------------------------

    /**
     * DEF Ignore%; removes a portion of the enemy's effective DEF
     * independently of DEF Shred (see DamageCalculator DEF formula).
     */
    DEF_IGNORE,
    /** Generic RES Shred% applied to the enemy's current element resistance. */
    RES_SHRED,
    /** Pyro-specific RES Shred%. */
    PYRO_RES_SHRED,
    /** Hydro-specific RES Shred%. */
    HYDRO_RES_SHRED,
    /** Cryo-specific RES Shred%. */
    CRYO_RES_SHRED,
    /** Electro-specific RES Shred%. */
    ELECTRO_RES_SHRED,
    /** Anemo-specific RES Shred%. */
    ANEMO_RES_SHRED,
    /** Geo-specific RES Shred%. */
    GEO_RES_SHRED,
    /** Dendro-specific RES Shred%. */
    DENDRO_RES_SHRED,
    /** Physical-specific RES Shred%. */
    PHYS_RES_SHRED,
    /** Cooldown reduction% for character skills and bursts. */
    CD_REDUCTION
}
