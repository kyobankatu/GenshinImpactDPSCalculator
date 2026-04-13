package mechanics.reaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mechanics.formula.ResistanceCalculator;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.event.TimerEvent;

/**
 * Owns reaction follow-up scheduling policies such as Electro-Charged ticks.
 */
public class ReactionEffectScheduler {
    private static final double[] LUNAR_CHARGED_WEIGHTS = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };

    private final CombatSimulator sim;

    public ReactionEffectScheduler(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Applies Electro-Charged state transitions and registers periodic tick events.
     *
     * @param trigger    triggering element
     * @param gaugeUnits gauge to apply as lingering aura
     * @param transDmg   standard Electro-Charged tick damage
     * @param isLunar    whether Thundercloud/Lunar-Charged policy is active
     */
    public void scheduleElectroCharged(Element trigger, double gaugeUnits, double transDmg, boolean isLunar) {
        if (isLunar) {
            sim.setThundercloudEndTime(sim.getCurrentTime() + 6.0);
        }

        if (!sim.isECTimerRunning()) {
            sim.setECTimerRunning(true);
            sim.registerEvent(createElectroChargedTickEvent(transDmg, isLunar));
        }

        sim.getEnemy().setAura(trigger, gaugeUnits);
    }

    /**
     * Computes the immediate Lunar-Charged reaction damage from the current party.
     *
     * @return weighted Lunar-Charged damage
     */
    public double computeInitialLunarChargedDamage() {
        return computeWeightedLunarChargedDamage(sim);
    }

    private TimerEvent createElectroChargedTickEvent(double transDmg, boolean isLunar) {
        return new TimerEvent() {
            private double nextTick = sim.getCurrentTime() + (isLunar ? 2.0 : 1.0);

            @Override
            public void tick(CombatSimulator simContext) {
                boolean shouldTick = isLunar
                        ? (simContext.getCurrentTime() <= simContext.getThundercloudEndTime())
                        : (simContext.getEnemy().getAuraUnits(Element.HYDRO) > 0
                                && simContext.getEnemy().getAuraUnits(Element.ELECTRO) > 0);
                if (!shouldTick) {
                    simContext.setECTimerRunning(false);
                    nextTick = Double.MAX_VALUE;
                    return;
                }

                String label = "Electro-Charged Tick";
                double finalDamage = transDmg;
                if (isLunar) {
                    label = "Lunar-Charged Reaction";
                    finalDamage = computeWeightedLunarChargedDamage(simContext);
                }

                if (simContext.isLoggingEnabled()) {
                    System.out.println(String.format("   [DoT] %s Damage: %,.0f", label, finalDamage));
                }

                simContext.recordDamage("Thundercloud", finalDamage);
                simContext.getCombatLogSink().log(
                        simContext.getCurrentTime(), "Thundercloud", label, finalDamage,
                        label, finalDamage, simContext.getEnemy().getAuraMap());

                if (isLunar) {
                    simContext.notifyReaction(
                            ReactionResult.transform(
                                    finalDamage,
                                    "Thundercloud-Strike",
                                    ReactionResult.Kind.THUNDERCLOUD_STRIKE),
                            simContext.getActiveCharacter());
                }

                simContext.getEnemy().reduceAura(Element.HYDRO, 0.4);
                simContext.getEnemy().reduceAura(Element.ELECTRO, 0.4);
                nextTick += (isLunar ? 2.0 : 1.0);
            }

            @Override
            public double getNextTickTime() {
                return nextTick;
            }

            @Override
            public boolean isFinished(double time) {
                return nextTick == Double.MAX_VALUE || time > 1000;
            }
        };
    }

    private double computeWeightedLunarChargedDamage(CombatSimulator simContext) {
        List<Double> potentialDamages = new ArrayList<>();
        for (Character member : simContext.getPartyMembers()) {
            StatsContainer stats = member.getEffectiveStats(simContext.getCurrentTime());
            double baseBonus = stats.get(StatType.LUNAR_BASE_BONUS);
            double uniqueBonus = stats.get(StatType.LUNAR_UNIQUE_BONUS)
                    + stats.get(StatType.LUNAR_CHARGED_DMG_BONUS)
                    + stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS)
                    + stats.get(StatType.LUNAR_REACTION_DMG_BONUS_ALL);
            double columbinaMult = 1.0 + stats.get(StatType.LUNAR_MULTIPLIER);
            double em = stats.get(StatType.ELEMENTAL_MASTERY);
            double emBonus = (2.78 * em) / (em + 1400.0);
            double cr = stats.get(StatType.CRIT_RATE);
            double cd = stats.get(StatType.CRIT_DMG);
            double critMult = 1.0 + (Math.min(cr, 1.0) * cd);
            double resMult = ResistanceCalculator.calculateResMulti(
                    simContext.getEnemy().getRes(StatType.ELECTRO_DMG_BONUS), 0.0);

            double damage = 1.8 * 1446.85 * (1.0 + baseBonus) * (1.0 + uniqueBonus)
                    * (1.0 + emBonus) * critMult * resMult * columbinaMult;
            potentialDamages.add(damage);
        }

        potentialDamages.sort(Collections.reverseOrder());
        double total = 0.0;
        for (int i = 0; i < potentialDamages.size() && i < LUNAR_CHARGED_WEIGHTS.length; i++) {
            total += potentialDamages.get(i) * LUNAR_CHARGED_WEIGHTS[i];
        }
        return total;
    }
}
