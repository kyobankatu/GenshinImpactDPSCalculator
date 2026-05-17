package visualization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mechanics.analysis.StatsSnapshot;
import model.entity.Character;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Adapts typed simulation/analysis data into report-facing labels and DOM keys.
 *
 * <p>Display names, HTML-safe row keys, and report-oriented stat aggregates are
 * boundary concerns owned by the visualization layer, not by combat logic.
 */
public final class ReportViewAdapter {
    private ReportViewAdapter() {
    }

    /**
     * Builds report-facing views for every active party member.
     *
     * @param sim source combat simulator; may be {@code null}
     * @return an ordered list of {@link ReportCharacterView} entries, or an empty
     *         list when {@code sim} is {@code null}
     */
    public static List<ReportCharacterView> partyCharacters(CombatSimulator sim) {
        List<ReportCharacterView> characters = new ArrayList<>();
        if (sim == null) {
            return characters;
        }
        for (Character character : sim.getPartyMembers()) {
            characters.add(new ReportCharacterView(character.getCharacterId(),
                    domKey(character.getCharacterId()),
                    character.getName()));
        }
        return characters;
    }

    /**
     * Adapts analysis stat snapshots into report-friendly snapshots keyed by DOM
     * identifier.
     *
     * @param statsHistory raw stat snapshots from the analysis layer; may be
     *                     {@code null}
     * @return a list of adapted snapshots, or an empty list when no history is
     *         provided
     */
    public static List<ReportStatsSnapshot> statsHistory(List<StatsSnapshot> statsHistory) {
        if (statsHistory == null) {
            return Collections.emptyList();
        }

        List<ReportStatsSnapshot> adapted = new ArrayList<>();
        for (StatsSnapshot snapshot : statsHistory) {
            Map<String, ReportCharacterStats> reportCharacters = new LinkedHashMap<>();
            for (Map.Entry<CharacterId, Map<StatType, Double>> entry : snapshot.characterStats.entrySet()) {
                CharacterId characterId = entry.getKey();
                Map<StatType, Double> stats = entry.getValue();
                List<String> buffs = snapshot.characterBuffs != null
                        ? snapshot.characterBuffs.getOrDefault(characterId, Collections.emptyList())
                        : Collections.emptyList();
                reportCharacters.put(domKey(characterId), new ReportCharacterStats(stats, buffs));
            }
            adapted.add(new ReportStatsSnapshot(snapshot.time, reportCharacters));
        }
        return adapted;
    }

    /**
     * Converts a {@link CharacterId} into the HTML-safe key used as the DOM row
     * identifier in the report.
     *
     * @param characterId character identifier (must be non-null)
     * @return the lowercased enum name suitable for use as a DOM id suffix
     */
    public static String domKey(CharacterId characterId) {
        return characterId.name().toLowerCase();
    }

    /**
     * View object describing a single party member for report rendering.
     */
    public static final class ReportCharacterView {
        /** Underlying character identifier. */
        public final CharacterId id;
        /** DOM-safe key derived from {@link #id}. */
        public final String domKey;
        /** Human-readable name used in tables and legends. */
        public final String displayName;

        /**
         * Creates a new character view.
         *
         * @param id          underlying character identifier
         * @param domKey      DOM-safe key (typically {@link #domKey(CharacterId)})
         * @param displayName human-readable display name
         */
        public ReportCharacterView(CharacterId id, String domKey, String displayName) {
            this.id = id;
            this.domKey = domKey;
            this.displayName = displayName;
        }
    }

    /**
     * Adapted snapshot of party stats at a single point in simulation time, keyed
     * by DOM identifier.
     */
    public static final class ReportStatsSnapshot {
        /** Simulation time (seconds) the snapshot was taken at. */
        public final double time;
        /** Per-character stats, keyed by DOM identifier. */
        public final Map<String, ReportCharacterStats> characters;

        /**
         * Creates a new adapted stats snapshot.
         *
         * @param time       simulation time in seconds
         * @param characters per-character stats keyed by DOM identifier
         */
        public ReportStatsSnapshot(double time, Map<String, ReportCharacterStats> characters) {
            this.time = time;
            this.characters = characters;
        }
    }

    /**
     * Pre-computed display-ready stat values (e.g. ATK with bonuses, crit
     * percentages) for a single character at one snapshot.
     */
    public static final class ReportCharacterStats {
        /** Effective ATK after percent and flat bonuses. */
        public final double atk;
        /** Effective HP after percent and flat bonuses. */
        public final double hp;
        /** Effective DEF after percent and flat bonuses. */
        public final double def;
        /** Crit Rate expressed as a percentage value (e.g. 65.0). */
        public final double cr;
        /** Crit DMG expressed as a percentage value (e.g. 180.0). */
        public final double cd;
        /** Energy Recharge expressed as a percentage value (e.g. 150.0). */
        public final double er;
        /** Elemental Mastery as a raw value. */
        public final double em;
        /** Strongest elemental DMG% bonus across all elements, as a percent. */
        public final double dmg;
        /** Active buff display names at the snapshot time. */
        public final List<String> buffs;

        /**
         * Reduces a raw stat map and buff list into report-ready values.
         *
         * @param stats raw stat map keyed by {@link StatType}; may be {@code null}
         * @param buffs active buff display names for the same snapshot
         */
        public ReportCharacterStats(Map<StatType, Double> stats, List<String> buffs) {
            Map<StatType, Double> safeStats = stats != null ? stats : new EnumMap<>(StatType.class);
            this.atk = safeStats.getOrDefault(StatType.BASE_ATK, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.ATK_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.ATK_FLAT, 0.0);
            this.hp = safeStats.getOrDefault(StatType.BASE_HP, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.HP_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.HP_FLAT, 0.0);
            this.def = safeStats.getOrDefault(StatType.BASE_DEF, 0.0)
                    * (1 + safeStats.getOrDefault(StatType.DEF_PERCENT, 0.0))
                    + safeStats.getOrDefault(StatType.DEF_FLAT, 0.0);
            this.cr = safeStats.getOrDefault(StatType.CRIT_RATE, 0.0) * 100;
            this.cd = safeStats.getOrDefault(StatType.CRIT_DMG, 0.0) * 100;
            this.er = safeStats.getOrDefault(StatType.ENERGY_RECHARGE, 0.0) * 100;
            this.em = safeStats.getOrDefault(StatType.ELEMENTAL_MASTERY, 0.0);
            this.dmg = strongestElementalBonusPercent(safeStats);
            this.buffs = buffs;
        }

        private double strongestElementalBonusPercent(Map<StatType, Double> stats) {
            return Math.max(stats.getOrDefault(StatType.PYRO_DMG_BONUS, 0.0),
                    Math.max(stats.getOrDefault(StatType.HYDRO_DMG_BONUS, 0.0),
                            Math.max(stats.getOrDefault(StatType.ELECTRO_DMG_BONUS, 0.0),
                                    Math.max(stats.getOrDefault(StatType.CRYO_DMG_BONUS, 0.0),
                                            Math.max(stats.getOrDefault(StatType.ANEMO_DMG_BONUS, 0.0),
                                                    Math.max(stats.getOrDefault(StatType.GEO_DMG_BONUS, 0.0),
                                                            stats.getOrDefault(StatType.DENDRO_DMG_BONUS, 0.0)))))))
                    * 100;
        }
    }
}
