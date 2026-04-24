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

    public static String domKey(CharacterId characterId) {
        return characterId.name().toLowerCase();
    }

    public static final class ReportCharacterView {
        public final CharacterId id;
        public final String domKey;
        public final String displayName;

        public ReportCharacterView(CharacterId id, String domKey, String displayName) {
            this.id = id;
            this.domKey = domKey;
            this.displayName = displayName;
        }
    }

    public static final class ReportStatsSnapshot {
        public final double time;
        public final Map<String, ReportCharacterStats> characters;

        public ReportStatsSnapshot(double time, Map<String, ReportCharacterStats> characters) {
            this.time = time;
            this.characters = characters;
        }
    }

    public static final class ReportCharacterStats {
        public final double atk;
        public final double hp;
        public final double def;
        public final double cr;
        public final double cd;
        public final double er;
        public final double em;
        public final double dmg;
        public final List<String> buffs;

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
