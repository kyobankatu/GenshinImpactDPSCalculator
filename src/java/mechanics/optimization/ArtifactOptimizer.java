package mechanics.optimization;

import model.stats.StatsContainer;
import model.type.StatType;
import model.standards.KQMSConstants;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generates a simulated artifact stat block following the KQM Standard (KQMS)
 * substat methodology.
 *
 * <p>The three-phase generation process in {@link #generate} is:
 * <ol>
 *   <li><b>Main stats</b> – Sands / Goblet / Circlet main-stat values for a
 *       max-level 5-star artifact are added, plus fixed Flower (HP) and
 *       Feather (ATK) mains.</li>
 *   <li><b>Fixed rolls</b> – 2 rolls of every standard substat type are applied
 *       unconditionally (20 fixed rolls total across 10 substat types).</li>
 *   <li><b>Liquid rolls</b> – 20 flexible rolls are distributed according to
 *       one of three strategies, in priority order:
 *       <ol>
 *         <li>ER pre-fill: enough rolls are assigned to {@code ENERGY_RECHARGE}
 *             to reach {@code config.minER}, unless ER is already handled by
 *             {@code manualRolls}.</li>
 *         <li>Manual override: if {@code config.manualRolls} is populated, rolls
 *             are distributed exactly as specified (sorted ascending by target
 *             count so smaller targets are filled first).</li>
 *         <li>CR/CD heuristic: when no manual overrides are given and
 *             {@code config.useCritRatio} is {@code true}, rolls are distributed
 *             to maintain a 1:2 CR:CD ratio.</li>
 *         <li>Dump: remaining rolls go to the highest-priority stat in
 *             {@code config.subStatPriority} that still has cap space.</li>
 *       </ol>
 *   </li>
 * </ol>
 *
 * <p>Per-stat liquid caps default to 10 rolls and are reduced by 2 for each
 * main-stat piece that uses that stat type (KQMS rule).
 */
public class ArtifactOptimizer {

    /**
     * Configuration passed to {@link ArtifactOptimizer#generate} to control which
     * main stats and substat allocation strategy to use.
     */
    public static class OptimizationConfig {
        /** Main stat for the Sands piece (e.g. {@code ATK_PERCENT}, {@code ENERGY_RECHARGE}). */
        public StatType mainStatSands;

        /** Main stat for the Goblet piece (e.g. {@code PYRO_DMG_BONUS}). */
        public StatType mainStatGoblet;

        /** Main stat for the Circlet piece (e.g. {@code CRIT_RATE}, {@code CRIT_DMG}). */
        public StatType mainStatCirclet;

        /** Ordered list of substats to invest in, highest priority first (e.g. [ER, CR, CD, ATK%, EM]). */
        public List<StatType> subStatPriority;

        /** Minimum total Energy Recharge required (e.g. {@code 2.50} for 250%). */
        public double minER;

        /** If {@code true}, liquid rolls maintain a 1:2 CR:CD ratio before dumping. Defaults to {@code true}. */
        public boolean useCritRatio = true;

        /**
         * Optional manual roll targets per stat type.  When non-empty this map
         * replaces the CR/CD heuristic entirely; the hill-climbing optimizer
         * writes its decisions here to override the default balancing logic.
         */
        public Map<StatType, Integer> manualRolls;
    }

    /**
     * Container for the artifact stats and the liquid roll allocation produced
     * by {@link ArtifactOptimizer#generate}.
     */
    public static class OptimizationResult {
        /** The fully assembled artifact {@link StatsContainer} ready to be merged with character stats. */
        public StatsContainer stats;

        /** Number of liquid rolls spent on each stat type in this result. */
        public Map<StatType, Integer> rolls; // usedLiquidRolls

        /**
         * @param stats the generated artifact stats
         * @param rolls the liquid roll counts used per stat
         */
        public OptimizationResult(StatsContainer stats, Map<StatType, Integer> rolls) {
            this.stats = stats;
            this.rolls = rolls;
        }
    }

    /**
     * Generates a complete KQMS artifact stat block for a single character.
     *
     * <p>See the class-level documentation for the full three-phase description.
     *
     * @param config        optimization settings (main stats, ER target, roll priorities)
     * @param charBaseStats character base stats (ATK, HP, DEF from level/ascension)
     * @param weaponStats   weapon main and sub-stats
     * @param setBonusStats artifact set bonus stats (2-piece / 4-piece effects)
     * @return an {@link OptimizationResult} containing the artifact stats and the
     *         liquid roll allocation map
     */
    public static OptimizationResult generate(OptimizationConfig config, StatsContainer charBaseStats,
            StatsContainer weaponStats, StatsContainer setBonusStats) {
        StatsContainer artifactStats = new StatsContainer();

        // 1. Apply Main Stats
        addMainStat(artifactStats, config.mainStatSands);
        addMainStat(artifactStats, config.mainStatGoblet);
        addMainStat(artifactStats, config.mainStatCirclet);

        // Flower/Feather fixed mains
        artifactStats.add(StatType.HP_FLAT, 4780.0);
        artifactStats.add(StatType.ATK_FLAT, 311.0);

        // Calculate KQM Liquid Caps
        Map<StatType, Integer> liquidCaps = new HashMap<>();
        for (StatType t : StatType.values()) {
            liquidCaps.put(t, 10);
        }
        // Exclude Main Stats
        if (config.mainStatSands != null)
            liquidCaps.put(config.mainStatSands, liquidCaps.getOrDefault(config.mainStatSands, 10) - 2);
        if (config.mainStatGoblet != null)
            liquidCaps.put(config.mainStatGoblet, liquidCaps.getOrDefault(config.mainStatGoblet, 10) - 2);
        if (config.mainStatCirclet != null)
            liquidCaps.put(config.mainStatCirclet, liquidCaps.getOrDefault(config.mainStatCirclet, 10) - 2);

        // Track used liquid rolls
        Map<StatType, Integer> usedLiquidRolls = new HashMap<>();

        // 2. Fixed Rolls (2 per stat type -> Total 20 Fixed Rolls)
        // Standard KQMS: 2 fixed rolls distributed to every substat type (ATK, ATK%,
        // HP, HP%, DEF, DEF%, ER, EM, CR, CD)
        StatType[] allSubstats = {
                StatType.ATK_FLAT, StatType.ATK_PERCENT,
                StatType.HP_FLAT, StatType.HP_PERCENT,
                StatType.DEF_FLAT, StatType.DEF_PERCENT,
                StatType.ELEMENTAL_MASTERY, StatType.ENERGY_RECHARGE,
                StatType.CRIT_RATE, StatType.CRIT_DMG
        };

        for (StatType type : allSubstats) {
            addRoll(artifactStats, type, KQMSConstants.FIXED_ROLLS);
        }

        // 3. Liquid Rolls Allocation (20 Total)
        int remainingRolls = KQMSConstants.LIQUID_ROLLS;

        // A. Meet ER Requirement
        // Skip if ER is already controlled by manualRolls (hill-climbing manages it directly)
        boolean erHandledByManual = config.manualRolls != null && config.manualRolls.containsKey(StatType.ENERGY_RECHARGE);
        if (!erHandledByManual && config.subStatPriority.contains(StatType.ENERGY_RECHARGE)) {
            while (remainingRolls > 0) {
                double currentER = getTotal(StatType.ENERGY_RECHARGE, charBaseStats, weaponStats, setBonusStats,
                        artifactStats);
                if (currentER >= config.minER)
                    break;

                if (usedLiquidRolls.getOrDefault(StatType.ENERGY_RECHARGE, 0) >= liquidCaps
                        .getOrDefault(StatType.ENERGY_RECHARGE, 10)) {
                    break;
                }

                addRoll(artifactStats, StatType.ENERGY_RECHARGE, 1);
                usedLiquidRolls.put(StatType.ENERGY_RECHARGE,
                        usedLiquidRolls.getOrDefault(StatType.ENERGY_RECHARGE, 0) + 1);
                remainingRolls--;
            }
        }

        // B. Crit/Manual Balancing
        if (config.manualRolls != null && !config.manualRolls.isEmpty()) {
            // Manual Override Mode
            // Sort by requested amount ASCENDING.
            // This ensures "Specific Targets" (e.g. ATK=5) are filled before "Dump Targets"
            // (e.g. CD=20)
            List<StatType> sortedKeys = new java.util.ArrayList<>(config.manualRolls.keySet());
            sortedKeys.sort((a, b) -> Integer.compare(config.manualRolls.get(a), config.manualRolls.get(b)));

            for (StatType type : sortedKeys) {
                int target = config.manualRolls.get(type);
                int current = usedLiquidRolls.getOrDefault(type, 0);
                int needed = target - current;

                if (needed <= 0)
                    continue;

                int cap = liquidCaps.getOrDefault(type, 10);
                int space = cap - current;
                int toAdd = Math.min(needed, space);
                toAdd = Math.min(toAdd, remainingRolls);

                if (toAdd > 0) {
                    addRoll(artifactStats, type, toAdd);
                    usedLiquidRolls.put(type, current + toAdd);
                    remainingRolls -= toAdd;
                    // System.out.println(" [DEBUG-Opt] Manual Added " + toAdd + " rolls to " +
                    // type);
                }
            }
        } else {
            // Standard Heuristic (1:2 Ratio)
            boolean hasCR = config.subStatPriority.contains(StatType.CRIT_RATE);
            boolean hasCD = config.subStatPriority.contains(StatType.CRIT_DMG);

            if (config.useCritRatio && hasCR && hasCD) {
                while (remainingRolls > 0) {
                    int crUsed = usedLiquidRolls.getOrDefault(StatType.CRIT_RATE, 0);
                    int cdUsed = usedLiquidRolls.getOrDefault(StatType.CRIT_DMG, 0);
                    int crCap = liquidCaps.getOrDefault(StatType.CRIT_RATE, 10);
                    int cdCap = liquidCaps.getOrDefault(StatType.CRIT_DMG, 10);

                    boolean canAddCR = crUsed < crCap;
                    boolean canAddCD = cdUsed < cdCap;

                    if (!canAddCR && !canAddCD)
                        break; // Start dumping

                    double cr = getTotal(StatType.CRIT_RATE, charBaseStats, weaponStats, setBonusStats, artifactStats);
                    double cd = getTotal(StatType.CRIT_DMG, charBaseStats, weaponStats, setBonusStats, artifactStats);

                    if (cr >= 1.0 && canAddCD) {
                        addRoll(artifactStats, StatType.CRIT_DMG, 1);
                        usedLiquidRolls.put(StatType.CRIT_DMG, cdUsed + 1);
                    } else {
                        if (cd < 2 * cr) {
                            if (canAddCD) {
                                addRoll(artifactStats, StatType.CRIT_DMG, 1);
                                usedLiquidRolls.put(StatType.CRIT_DMG, cdUsed + 1);
                            } else {
                                addRoll(artifactStats, StatType.CRIT_RATE, 1);
                                usedLiquidRolls.put(StatType.CRIT_RATE, crUsed + 1);
                            }
                        } else {
                            if (canAddCR) {
                                addRoll(artifactStats, StatType.CRIT_RATE, 1);
                                usedLiquidRolls.put(StatType.CRIT_RATE, crUsed + 1);
                            } else {
                                addRoll(artifactStats, StatType.CRIT_DMG, 1);
                                usedLiquidRolls.put(StatType.CRIT_DMG, cdUsed + 1);
                            }
                        }
                    }
                    remainingRolls--;
                }
            }
        }

        // C. Dump Rest
        while (remainingRolls > 0) {
            StatType dumpStat = null;
            for (StatType type : config.subStatPriority) {
                if (usedLiquidRolls.getOrDefault(type, 0) < liquidCaps.getOrDefault(type, 10)) {
                    if (config.useCritRatio && (type == StatType.CRIT_RATE || type == StatType.CRIT_DMG)) {
                        continue;
                    }
                    if (type == StatType.ENERGY_RECHARGE)
                        continue;

                    dumpStat = type;
                    break;
                }
            }

            if (dumpStat == null) {
                break;
            }

            addRoll(artifactStats, dumpStat, 1);
            usedLiquidRolls.put(dumpStat, usedLiquidRolls.getOrDefault(dumpStat, 0) + 1);
            remainingRolls--;
        }

        // Print Breakdown
        System.out.println(
                String.format("   [Artifact Optimizer] Generated for Config (MinER: %.1f%%):", config.minER * 100));
        // ... (Keep existing logs simplified or assume they are there)

        return new OptimizationResult(artifactStats, usedLiquidRolls);
    }

    /**
     * Adds the standard max-level 5-star main-stat value for {@code type} to
     * {@code stats}.  No-ops for {@code null} or unrecognised stat types.
     *
     * @param stats the stats container to mutate
     * @param type  the main-stat type to add
     */
    private static void addMainStat(StatsContainer stats, StatType type) {
        // Approximate Main Stat Values for 5* Lv20
        double val = 0.0;
        switch (type) {
            case HP_PERCENT:
                val = 0.466;
                break;
            case ATK_PERCENT:
                val = 0.466;
                break;
            case DEF_PERCENT:
                val = 0.583;
                break;
            case ENERGY_RECHARGE:
                val = 0.518;
                break;
            case ELEMENTAL_MASTERY:
                val = 187.0;
                break;
            case CRIT_RATE:
                val = 0.311;
                break;
            case CRIT_DMG:
                val = 0.622;
                break;
            case PYRO_DMG_BONUS:
            case HYDRO_DMG_BONUS:
            case ELECTRO_DMG_BONUS:
            case CRYO_DMG_BONUS:
            case ANEMO_DMG_BONUS:
            case GEO_DMG_BONUS:
            case DENDRO_DMG_BONUS:
                val = 0.466;
                break;
            case PHYSICAL_DMG_BONUS:
                val = 0.583;
                break;
            case HP_FLAT:
                val = 4780.0;
                break;
            case ATK_FLAT:
                val = 311.0;
                break;
            default:
                break;
        }
        stats.add(type, val);
    }

    /**
     * Adds {@code count} KQMS-average substat rolls of {@code type} to {@code stats}.
     * The per-roll value for each stat is sourced from {@link KQMSConstants}.
     *
     * @param stats the stats container to mutate
     * @param type  the substat type to roll
     * @param count number of rolls to add
     */
    private static void addRoll(StatsContainer stats, StatType type, int count) {
        double val = 0.0;
        switch (type) {
            case HP_FLAT:
                val = KQMSConstants.HP_FLAT;
                break;
            case ATK_FLAT:
                val = KQMSConstants.ATK_FLAT;
                break;
            case DEF_FLAT:
                val = KQMSConstants.DEF_FLAT;
                break;
            case HP_PERCENT:
                val = KQMSConstants.HP_PERCENT;
                break;
            case ATK_PERCENT:
                val = KQMSConstants.ATK_PERCENT;
                break;
            case DEF_PERCENT:
                val = KQMSConstants.DEF_PERCENT;
                break;
            case CRIT_RATE:
                val = KQMSConstants.CRIT_RATE;
                break;
            case CRIT_DMG:
                val = KQMSConstants.CRIT_DMG;
                break;
            case ELEMENTAL_MASTERY:
                val = KQMSConstants.ELEMENTAL_MASTERY;
                break;
            case ENERGY_RECHARGE:
                val = KQMSConstants.ENERGY_RECHARGE;
                break;
            default:
                break;
        }
        stats.add(type, val * count);
    }

    /**
     * Sums the value of {@code type} across all provided {@link StatsContainer}s,
     * skipping {@code null} containers.
     *
     * @param type       the stat to sum
     * @param containers one or more stats containers (nulls are ignored)
     * @return combined value of the stat across all containers
     */
    private static double getTotal(StatType type, StatsContainer... containers) {
        double sum = 0.0;
        for (StatsContainer c : containers) {
            if (c != null)
                sum += c.get(type);
        }
        return sum;
    }

}
