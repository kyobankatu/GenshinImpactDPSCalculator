package model.standards;

/**
 * KQM Standards (KQMS) constants used as the baseline assumptions for all
 * benchmark simulations and artifact optimizations.
 *
 * <p>These values reproduce the
 * <a href="https://compendium.keqingmains.com/">Keqing Mains Compendium</a>
 * standard assumptions, ensuring that DPS comparisons between builds and
 * characters are made on an equal footing.
 *
 * <p>All constants are {@code public static final} and should be referenced
 * directly (no instantiation required).
 */
public class KQMSConstants {

    // -----------------------------------------------------------------------
    // Artifact substat roll values (average of all possible roll tiers)
    // -----------------------------------------------------------------------

    /** Average flat HP substat value per roll. */
    public static final double HP_FLAT = 253.94;
    /** Average flat ATK substat value per roll. */
    public static final double ATK_FLAT = 16.54;
    /** Average flat DEF substat value per roll. */
    public static final double DEF_FLAT = 19.68;

    /** Average HP% substat value per roll (4.96 %). */
    public static final double HP_PERCENT = 0.0496; // 4.96%
    /** Average ATK% substat value per roll (4.96 %). */
    public static final double ATK_PERCENT = 0.0496;
    /** Average DEF% substat value per roll (6.20 %). */
    public static final double DEF_PERCENT = 0.0620;

    /** Average Crit Rate substat value per roll (3.31 %). */
    public static final double CRIT_RATE = 0.0331; // 3.31%
    /** Average Crit DMG substat value per roll (6.62 %). */
    public static final double CRIT_DMG = 0.0662; // 6.62%
    /** Average Elemental Mastery substat value per roll. */
    public static final double ELEMENTAL_MASTERY = 19.82;
    /** Average Energy Recharge substat value per roll (5.51 %). */
    public static final double ENERGY_RECHARGE = 0.0551; // 5.51%

    // -----------------------------------------------------------------------
    // Simulation standards
    // -----------------------------------------------------------------------

    /** Standard character level used in DEF multiplier calculations. */
    public static final int CHAR_LEVEL = 90;
    /** Standard enemy level used in DEF multiplier calculations. */
    public static final int ENEMY_LEVEL = 100;
    /** Standard enemy base resistance for all elements (10 %). */
    public static final double ENEMY_RESISTANCE_BASE = 0.10; // 10%

    // -----------------------------------------------------------------------
    // Artifact roll budget for the optimizer
    // -----------------------------------------------------------------------

    /**
     * Number of "liquid" (freely allocated) substat rolls available for
     * optimization across all five artifact pieces.
     */
    public static final int LIQUID_ROLLS = 20;
    /**
     * Number of fixed rolls pre-allocated to each useful substat before
     * the liquid budget is distributed.
     */
    public static final int FIXED_ROLLS = 2; // Per useful stat
}
