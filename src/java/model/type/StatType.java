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
    /**
     * Flat ATK derived from final Max HP, expressed as an additive ratio.
     * Resolved lazily so HP sources merged later remain part of the conversion.
     */
    MAX_HP_TO_ATK_FLAT_RATIO,
    /** ATK% gained per point of ordinary Energy Recharge above base 100%. */
    ENERGY_RECHARGE_TO_ATK_PERCENT_RATIO,
    /** Maximum ATK% obtainable from Energy-Recharge-to-ATK conversion. */
    ENERGY_RECHARGE_TO_ATK_PERCENT_CAP,
    /** Flat ATK gained per point of final Elemental Mastery. */
    ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO,

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
    /** Additional CRIT DMG applied only to Electro damage instances. */
    ELECTRO_CRIT_DMG,
    /** Additional CRIT DMG applied only to Anemo damage instances. */
    ANEMO_CRIT_DMG,
    /** Additional CRIT DMG applied only to Geo damage instances. */
    GEO_CRIT_DMG,
    /** Additional CRIT DMG applied only to Cryo damage instances. */
    CRYO_CRIT_DMG,
    /** Additional CRIT DMG applied only to Physical damage instances. */
    PHYSICAL_CRIT_DMG,
    /** Elemental Mastery flat value; amplifies reactions. */
    ELEMENTAL_MASTERY,
    /** Energy Recharge as a decimal (e.g. 1.0 = 100 %). */
    ENERGY_RECHARGE,
    /**
     * Energy Recharge that affects particle recovery but is excluded from
     * Energy-Recharge-to-damage conversions such as Raiden A4 and Emblem.
     */
    NON_CONVERTING_ENERGY_RECHARGE,

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
    /** DMG bonus that applies specifically to Overloaded hits. */
    OVERLOAD_DMG_BONUS,
    /** DMG bonus that applies specifically to Superconduct hits. */
    SUPERCONDUCT_DMG_BONUS,
    /** DMG bonus that applies specifically to Burning ticks. */
    BURNING_DMG_BONUS,
    /** DMG bonus that applies specifically to Vaporize multipliers. */
    VAPORIZE_DMG_BONUS,
    /** DMG bonus that applies specifically to Melt multipliers. */
    MELT_DMG_BONUS,
    /** DMG bonus that applies specifically to Bloom core explosions. */
    BLOOM_DMG_BONUS,
    /** DMG bonus that applies specifically to Hyperbloom hits. */
    HYPERBLOOM_DMG_BONUS,
    /** DMG bonus that applies specifically to Burgeon hits. */
    BURGEON_DMG_BONUS,
    /** DMG bonus that applies specifically to Aggravate additive damage. */
    AGGRAVATE_DMG_BONUS,
    /** DMG bonus that applies specifically to Spread additive damage. */
    SPREAD_DMG_BONUS,

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
    /** Independent final multiplier for elevated Lunar damage. */
    LUNAR_MULTIPLIER,
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
    /** Additive damage bonus for Stellar-Conduct damage instances. */
    STELLAR_CONDUCT_DMG_BONUS,
    /** Additive damage bonus for Stellar-Swirl damage instances. */
    STELLAR_SWIRL_DMG_BONUS,
    /** Base-damage bonus for Stellar-Conduct before the reaction section. */
    STELLAR_CONDUCT_BASE_DMG_BONUS,
    /** Base-damage bonus for Stellar-Swirl before the reaction section. */
    STELLAR_SWIRL_BASE_DMG_BONUS,
    /** Independent final multiplier for Stellar-Conduct damage. */
    STELLAR_CONDUCT_MULTIPLIER,
    /** Independent final multiplier for Stellar-Swirl damage. */
    STELLAR_SWIRL_MULTIPLIER,
    /** CRIT Rate applying only to Stellar-Conduct damage. */
    STELLAR_CONDUCT_CRIT_RATE,
    /** CRIT Rate applying only to Stellar-Swirl damage. */
    STELLAR_SWIRL_CRIT_RATE,
    /** CRIT DMG applying only to Stellar-Conduct damage. */
    STELLAR_CONDUCT_CRIT_DMG,
    /** CRIT DMG applying only to Stellar-Swirl damage. */
    STELLAR_SWIRL_CRIT_DMG,
    /** Independent special damage bonus for Stellar-Conduct damage. */
    STELLAR_CONDUCT_SPECIAL_DMG_BONUS,
    /** Independent special damage bonus for Stellar-Swirl damage. */
    STELLAR_SWIRL_SPECIAL_DMG_BONUS,

    // -----------------------------------------------------------------------
    // Attack-type and ability-specific DMG Bonus / Crit stats
    // -----------------------------------------------------------------------

    /** DMG bonus that applies to Normal Attack hits. */
    NORMAL_ATTACK_DMG_BONUS,
    /** DMG bonus that applies only to non-Physical Normal Attack hits. */
    ELEMENTAL_NORMAL_ATTACK_DMG_BONUS,
    /** DMG bonus that applies to Charged Attack hits. */
    CHARGED_ATTACK_DMG_BONUS,
    /** DMG bonus that applies to Plunging Attack hits. */
    PLUNGING_ATTACK_DMG_BONUS,
    /** DMG bonus that applies to Elemental Skill hits. */
    SKILL_DMG_BONUS,
    /** Crit rate bonus that applies specifically to Elemental Skill hits. */
    SKILL_CRIT_RATE,
    /** Crit rate bonus that applies specifically to Charged Attack hits. */
    CHARGED_ATTACK_CRIT_RATE,
    /** Crit rate bonus that applies specifically to Plunging Attack hits. */
    PLUNGING_ATTACK_CRIT_RATE,
    /** Crit DMG bonus that applies specifically to Plunging Attack hits. */
    PLUNGING_ATTACK_CRIT_DMG,
    /** DMG bonus that applies to Elemental Burst hits. */
    BURST_DMG_BONUS,
    /** Crit rate bonus that applies specifically to Elemental Burst hits. */
    BURST_CRIT_RATE,
    /** Attack speed bonus (multiplicative on animation duration). */
    ATK_SPD,
    /** Attack speed bonus that applies only to Normal Attack animations. */
    NORMAL_ATTACK_SPD,

    /** Generic all-damage DMG bonus% applied to every damage instance. */
    DMG_BONUS_ALL,
    /** Flat damage added to the Additive Bonus step of the damage formula. */
    FLAT_DMG_BONUS,
    /** Final-DEF ratio added to Normal and Charged Attack base damage. */
    DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO,
    /** Final-DEF ratio added to Elemental Skill base damage. */
    DEF_TO_SKILL_FLAT_DMG_RATIO,
    /** Final-EM ratio added to Normal and Elemental Skill base damage. */
    ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO,
    /** Final-EM ratio added to Charged Attack base damage. */
    ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO,
    /** Final-ATK ratio added only to Normal Attack base damage. */
    NORMAL_ATTACK_ATK_FLAT_DMG_RATIO,
    /** Final-Max-HP ratio added only to Normal Attack base damage. */
    MAX_HP_TO_NORMAL_FLAT_DMG_RATIO,
    /** Elemental Skill DMG Bonus gained per point of final Elemental Mastery. */
    ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO,

    // -----------------------------------------------------------------------
    // Debuffs / Enemy mitigation reduction stats
    // -----------------------------------------------------------------------

    /** Additive enemy DEF reduction, capped at 90% by the damage formula. */
    ENEMY_DEF_REDUCTION,
    /**
     * DEF Ignore%; removes a portion of the enemy's effective DEF
     * independently of enemy DEF reduction (see DamageCalculator DEF formula).
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
