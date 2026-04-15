package mechanics.analysis;

import simulation.CombatSimulator;
import simulation.event.TimerEvent;
import model.entity.Character;
import model.type.CharacterId;
import model.stats.StatsContainer;
import model.type.StatType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import mechanics.buff.Buff;

/**
 * Periodically records effective character stats and active buff names during a
 * simulation run, producing a series of {@link StatsSnapshot} objects.
 *
 * <p>Recording is driven by a {@link TimerEvent} registered with the
 * {@link CombatSimulator}.  The event fires at intervals defined by
 * {@code interval} seconds and captures the effective stats (including all
 * applicable team and character buffs) for every party member at that moment.
 *
 * <p>The collected snapshots are later used by {@link visualization.HtmlReportGenerator}
 * to populate the interactive stats slider in the HTML report.
 */
public class StatsRecorder {
    private List<StatsSnapshot> snapshots = new ArrayList<>();
    private double interval;
    private CombatSimulator sim;

    /**
     * Creates a new recorder attached to the given simulator.
     *
     * @param sim      the combat simulator to record stats from
     * @param interval time in seconds between successive snapshots
     */
    public StatsRecorder(CombatSimulator sim, double interval) {
        this.sim = sim;
        this.interval = interval;
    }

    /**
     * Registers a periodic {@link TimerEvent} with the simulator that calls
     * {@link #recordSnapshot} at each tick.  Must be called before the
     * simulation rotation is executed.
     */
    public void startRecording() {
        sim.registerEvent(new TimerEvent() {
            private double nextTick = -0.0001; // Start immediately (at 0) basically

            @Override
            public double getNextTickTime() {
                return nextTick;
            }

            @Override
            public void tick(CombatSimulator s) {
                recordSnapshot(s);
                nextTick = s.getCurrentTime() + interval;
            }

            @Override
            public boolean isFinished(double currentTime) {
                // Run indefinitely until sim stops
                return false;
            }
        });
    }

    /**
     * Captures the current effective stats and active buff names for every
     * party member and stores the result as a new {@link StatsSnapshot}.
     *
     * @param s the combat simulator at the moment of capture
     */
    private void recordSnapshot(CombatSimulator s) {
        Map<String, Map<StatType, Double>> charStats = new HashMap<>();
        Map<String, List<String>> charBuffs = new HashMap<>();

        for (Character c : s.getPartyMembers()) {
            StatsContainer effStats = c.getEffectiveStats(s.getCurrentTime());

            // Apply Dynamic Team Buffs (to match damage calculation logic)
            List<Buff> applicableBuffs = s.getApplicableBuffs(c);
            if (c.getCharacterId() == CharacterId.INEFFA && s.getCurrentTime() > 1.9 && s.getCurrentTime() < 2.2) {
                System.out.println("[StatsRecorder] Buffs on Ineffa at " + s.getCurrentTime() + ":");
                if (applicableBuffs != null) {
                    for (Buff b : applicableBuffs) {
                        System.out
                                .println("   - " + b.getName() + " (Expired: " + b.isExpired(s.getCurrentTime()) + ")");
                    }
                }
            }

            List<String> buffNames = new ArrayList<>();
            Set<String> seenBuffKeys = new HashSet<>();
            if (applicableBuffs != null) {
                for (Buff b : applicableBuffs) {
                    if (!b.isExpired(s.getCurrentTime())) {
                        b.apply(effStats, s.getCurrentTime());
                        if (seenBuffKeys.add(b.getLogicKey())) {
                            buffNames.add(b.getDisplayName());
                        }
                    }
                }
            }
            // Also collect character's own activeBuffs (artifact/weapon buffs added via addBuff)
            for (Buff b : c.getActiveBuffs()) {
                if (!b.isExpired(s.getCurrentTime()) && seenBuffKeys.add(b.getLogicKey())) {
                    buffNames.add(b.getDisplayName());
                }
            }
            charBuffs.put(c.getName(), buffNames);

            Map<StatType, Double> statMap = new HashMap<>();

            // Capture relevant stats
            statMap.put(StatType.BASE_ATK, effStats.get(StatType.BASE_ATK));
            statMap.put(StatType.ATK_PERCENT, effStats.get(StatType.ATK_PERCENT));
            statMap.put(StatType.ATK_FLAT, effStats.get(StatType.ATK_FLAT));

            statMap.put(StatType.BASE_HP, effStats.get(StatType.BASE_HP));
            statMap.put(StatType.HP_PERCENT, effStats.get(StatType.HP_PERCENT));
            statMap.put(StatType.HP_FLAT, effStats.get(StatType.HP_FLAT));

            statMap.put(StatType.BASE_DEF, effStats.get(StatType.BASE_DEF));
            statMap.put(StatType.DEF_PERCENT, effStats.get(StatType.DEF_PERCENT));
            statMap.put(StatType.DEF_FLAT, effStats.get(StatType.DEF_FLAT));

            statMap.put(StatType.CRIT_RATE, effStats.get(StatType.CRIT_RATE));
            statMap.put(StatType.CRIT_DMG, effStats.get(StatType.CRIT_DMG));
            statMap.put(StatType.ENERGY_RECHARGE, effStats.get(StatType.ENERGY_RECHARGE));
            statMap.put(StatType.ELEMENTAL_MASTERY, effStats.get(StatType.ELEMENTAL_MASTERY));

            statMap.put(StatType.PYRO_DMG_BONUS, effStats.get(StatType.PYRO_DMG_BONUS));
            statMap.put(StatType.HYDRO_DMG_BONUS, effStats.get(StatType.HYDRO_DMG_BONUS));
            statMap.put(StatType.ELECTRO_DMG_BONUS, effStats.get(StatType.ELECTRO_DMG_BONUS));
            statMap.put(StatType.CRYO_DMG_BONUS, effStats.get(StatType.CRYO_DMG_BONUS));
            statMap.put(StatType.ANEMO_DMG_BONUS, effStats.get(StatType.ANEMO_DMG_BONUS));
            statMap.put(StatType.GEO_DMG_BONUS, effStats.get(StatType.GEO_DMG_BONUS));
            statMap.put(StatType.DENDRO_DMG_BONUS, effStats.get(StatType.DENDRO_DMG_BONUS));

            charStats.put(c.getName(), statMap);
        }

        snapshots.add(new StatsSnapshot(s.getCurrentTime(), charStats, charBuffs));
    }

    /**
     * Returns the list of snapshots collected since recording started.
     *
     * @return unmodifiable view of all recorded {@link StatsSnapshot} objects
     */
    public List<StatsSnapshot> getSnapshots() {
        return snapshots;
    }
}
