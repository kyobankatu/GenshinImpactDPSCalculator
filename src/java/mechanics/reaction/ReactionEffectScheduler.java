package mechanics.reaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mechanics.formula.ResistanceCalculator;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.runtime.ReactionState;
import simulation.event.TimerEvent;

/**
 * Owns reaction follow-up scheduling policies such as Electro-Charged ticks.
 */
public class ReactionEffectScheduler {
    private static final double[] LUNAR_CHARGED_WEIGHTS = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };
    private static final int DENDRO_CORE_DAMAGE_HIT_CAP = 2;
    private static final double DENDRO_CORE_DAMAGE_WINDOW = 0.5;

    private final CombatSimulator sim;
    private final List<Double> recentDendroCoreDamageTimes = new ArrayList<>();

    public ReactionEffectScheduler(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Applies Electro-Charged state transitions and registers periodic tick events.
     *
     * @param trigger    triggering element
     * @param gaugeUnits pre-tax source gauge to apply as the coexisting aura
     * @param preResistanceDamage standard Electro-Charged tick damage before RES
     * @param isLunar    whether Thundercloud/Lunar-Charged policy is active
     */
    public void scheduleElectroCharged(
            Element trigger, double gaugeUnits, double preResistanceDamage, boolean isLunar) {
        if (isLunar) {
            sim.setThundercloudEndTime(sim.getCurrentTime() + 6.0);
        }

        if (!sim.isECTimerRunning()) {
            sim.setECTimerRunning(true);
            sim.registerEvent(createElectroChargedTickEvent(preResistanceDamage, isLunar));
        }

        sim.getEnemy().applyAura(trigger, gaugeUnits, sim.getCurrentTime());
    }

    /**
     * Computes the immediate Lunar-Charged reaction damage from the current party.
     *
     * @return weighted Lunar-Charged damage
     */
    public double computeInitialLunarChargedDamage() {
        return computeWeightedLunarReactionDamage(sim, Element.ELECTRO, StatType.LUNAR_CHARGED_DMG_BONUS);
    }

    public double computeLunarCrystallizeHarmonyDamage() {
        return computeWeightedLunarReactionDamage(sim, Element.GEO, StatType.LUNAR_CRYSTALLIZE_DMG_BONUS);
    }

    /**
     * Starts or refreshes simplified Burning ticks for a single non-attacking target.
     *
     * @param ownerId    character credited with Burning damage
     * @param preResistanceDamage damage per 0.25 s Burning tick before RES
     */
    public void scheduleBurning(CharacterId ownerId, double preResistanceDamage) {
        sim.setBurningEndTime(sim.getCurrentTime() + 2.0);
        sim.getEnemy().setAura(
                Element.PYRO,
                Math.max(1.0, sim.getEnemy().getAuraUnits(Element.PYRO, sim.getCurrentTime())),
                sim.getCurrentTime());
        if (!sim.isBurningTimerRunning()) {
            sim.setBurningTimerRunning(true);
            sim.registerEvent(createBurningTickEvent(ownerId, preResistanceDamage));
        }
    }

    /**
     * Creates a Dendro Core and schedules its delayed Bloom explosion.
     *
     * @param ownerId character credited with the Bloom core explosion
     * @param preResistanceDamage Dendro damage before impact-time RES
     */
    public void createDendroCore(CharacterId ownerId, double preResistanceDamage) {
        if (sim.getDendroCores().size() >= 5) {
            ReactionState.DendroCoreState oldest = sim.removeOldestDendroCore();
            if (oldest != null) {
                explodeDendroCore(oldest, "Bloom");
            }
        }
        ReactionState.DendroCoreState core = sim.addDendroCore(ownerId, preResistanceDamage);
        sim.registerEvent(createDendroCoreExpiryEvent(core.id));
    }

    /**
     * Consumes Dendro Cores for Hyperbloom/Burgeon in the single-target abstraction.
     *
     * @param ownerId       character credited with the reaction
     * @param preResistanceDamage Dendro damage before impact-time RES
     * @param reactionLabel display label
     * @param maxCores      maximum cores consumed by this hit
     * @return number of consumed cores
     */
    public int consumeDendroCores(
            CharacterId ownerId,
            double preResistanceDamage,
            String reactionLabel,
            int maxCores) {
        int consumed = 0;
        while (consumed < maxCores && !sim.getDendroCores().isEmpty()) {
            sim.removeOldestDendroCore();
            recordDendroCoreDamage(ownerId, reactionLabel, preResistanceDamage);
            consumed++;
        }
        return consumed;
    }

    private TimerEvent createElectroChargedTickEvent(double preResistanceDamage, boolean isLunar) {
        return new TimerEvent() {
            private double nextTick = sim.getCurrentTime() + (isLunar ? 2.0 : 1.0);

            @Override
            public void tick(CombatSimulator simContext) {
                boolean shouldTick = isLunar
                        ? (simContext.getCurrentTime() <= simContext.getThundercloudEndTime())
                        : (simContext.getEnemy().getAuraUnits(Element.HYDRO, simContext.getCurrentTime()) > 0
                                && simContext.getEnemy().getAuraUnits(Element.ELECTRO, simContext.getCurrentTime()) > 0);
                if (!shouldTick) {
                    simContext.setECTimerRunning(false);
                    nextTick = Double.MAX_VALUE;
                    return;
                }

                String label = "Electro-Charged Tick";
                double finalDamage = applyCurrentResistance(
                        preResistanceDamage, Element.ELECTRO, simContext);
                if (isLunar) {
                    label = "Lunar-Charged Reaction";
                    finalDamage = computeWeightedLunarReactionDamage(
                            simContext, Element.ELECTRO, StatType.LUNAR_CHARGED_DMG_BONUS);
                }

                if (simContext.isLoggingEnabled()) {
                    System.out.println(String.format("   [DoT] %s Damage: %,.0f", label, finalDamage));
                }

                simContext.recordDamage("Thundercloud", finalDamage);
                simContext.getCombatLogSink().log(
                        simContext.getCurrentTime(), "Thundercloud", label, finalDamage,
                        label, finalDamage, simContext.getEnemy().getAuraMap(simContext.getCurrentTime()));

                if (isLunar) {
                    simContext.notifyReaction(
                            ReactionResult.transform(
                                    finalDamage,
                                    "Thundercloud-Strike",
                                    ReactionResult.Kind.THUNDERCLOUD_STRIKE),
                            simContext.getActiveCharacter());
                }

                simContext.getEnemy().reduceAura(Element.HYDRO, 0.4, simContext.getCurrentTime());
                simContext.getEnemy().reduceAura(Element.ELECTRO, 0.4, simContext.getCurrentTime());
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

    private TimerEvent createBurningTickEvent(CharacterId ownerId, double preResistanceDamage) {
        return new TimerEvent() {
            private double nextTick = sim.getCurrentTime() + 0.25;

            @Override
            public void tick(CombatSimulator simContext) {
                if (simContext.getCurrentTime() > simContext.getBurningEndTime()) {
                    simContext.setBurningTimerRunning(false);
                    nextTick = Double.MAX_VALUE;
                    return;
                }

                double tickDamage = applyCurrentResistance(
                        preResistanceDamage, Element.PYRO, simContext);
                if (simContext.isLoggingEnabled()) {
                    System.out.println(String.format("   [DoT] Burning Damage: %,.0f", tickDamage));
                }
                simContext.recordDamage(ownerId, tickDamage);
                simContext.getCombatLogSink().log(
                        simContext.getCurrentTime(), ownerId.getDisplayName(), "Burning", tickDamage,
                        "Burning", tickDamage, simContext.getEnemy().getAuraMap(simContext.getCurrentTime()));

                nextTick += 0.25;
            }

            @Override
            public double getNextTickTime() {
                return nextTick;
            }

            @Override
            public boolean isFinished(double time) {
                return nextTick == Double.MAX_VALUE || time > sim.getBurningEndTime() + 1.0;
            }
        };
    }

    private TimerEvent createDendroCoreExpiryEvent(int coreId) {
        return new TimerEvent() {
            private double nextTick = findExpiryTime(coreId);
            private boolean finished = false;

            @Override
            public void tick(CombatSimulator simContext) {
                ReactionState.DendroCoreState core = findCore(coreId);
                if (core != null) {
                    simContext.removeDendroCore(coreId);
                    explodeDendroCore(core, "Bloom");
                }
                finished = true;
                nextTick = Double.MAX_VALUE;
            }

            @Override
            public double getNextTickTime() {
                return nextTick;
            }

            @Override
            public boolean isFinished(double time) {
                return finished;
            }
        };
    }

    private double findExpiryTime(int coreId) {
        ReactionState.DendroCoreState core = findCore(coreId);
        return core != null ? core.expiryTime : Double.MAX_VALUE;
    }

    private ReactionState.DendroCoreState findCore(int coreId) {
        for (ReactionState.DendroCoreState core : sim.getDendroCores()) {
            if (core.id == coreId) {
                return core;
            }
        }
        return null;
    }

    private void explodeDendroCore(ReactionState.DendroCoreState core, String label) {
        recordDendroCoreDamage(core.ownerId, label, core.preResistanceDamage);
    }

    private void recordDendroCoreDamage(
            CharacterId ownerId, String label, double preResistanceDamage) {
        if (!canRecordDendroCoreDamage()) {
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format("   [Reaction] %s Damage capped by same-target core limit", label));
            }
            return;
        }
        double damage = applyCurrentResistance(preResistanceDamage, Element.DENDRO, sim);
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [Reaction] %s Damage: %,.0f", label, damage));
        }
        sim.recordDamage(ownerId, damage);
        sim.getCombatLogSink().log(
                sim.getCurrentTime(), ownerId.getDisplayName(), label, damage,
                label, damage, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
    }

    private boolean canRecordDendroCoreDamage() {
        double now = sim.getCurrentTime();
        recentDendroCoreDamageTimes.removeIf(time -> now - time >= DENDRO_CORE_DAMAGE_WINDOW);
        if (recentDendroCoreDamageTimes.size() >= DENDRO_CORE_DAMAGE_HIT_CAP) {
            return false;
        }
        recentDendroCoreDamageTimes.add(now);
        return true;
    }

    private double computeWeightedLunarReactionDamage(
            CombatSimulator simContext, Element damageElement, StatType reactionBonusStat) {
        List<Double> potentialDamages = new ArrayList<>();
        for (Character member : simContext.getPartyMembers()) {
            StatsContainer stats = member.getEffectiveStats(simContext.getCurrentTime());
            double baseBonus = stats.get(StatType.LUNAR_BASE_BONUS);
            double uniqueBonus = stats.get(StatType.LUNAR_UNIQUE_BONUS)
                    + stats.get(reactionBonusStat)
                    + stats.get(StatType.LUNAR_REACTION_DMG_BONUS_ALL);
            if (reactionBonusStat == StatType.LUNAR_CHARGED_DMG_BONUS) {
                uniqueBonus += stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS);
            }
            double columbinaMult = 1.0 + stats.get(StatType.LUNAR_MULTIPLIER);
            double em = stats.get(StatType.ELEMENTAL_MASTERY);
            double emBonus = (2.78 * em) / (em + 1400.0);
            double cr = stats.get(StatType.CRIT_RATE);
            double cd = stats.get(StatType.CRIT_DMG);
            double critMult = 1.0 + (Math.min(cr, 1.0) * cd);
            double damage = 1.8 * 1446.85 * (1.0 + baseBonus) * (1.0 + uniqueBonus)
                    * (1.0 + emBonus) * critMult * columbinaMult;
            potentialDamages.add(damage);
        }

        potentialDamages.sort(Collections.reverseOrder());
        double total = 0.0;
        for (int i = 0; i < potentialDamages.size() && i < LUNAR_CHARGED_WEIGHTS.length; i++) {
            total += potentialDamages.get(i) * LUNAR_CHARGED_WEIGHTS[i];
        }
        return applyCurrentResistance(total, damageElement, simContext);
    }

    private double applyCurrentResistance(
            double preResistanceDamage, Element damageElement, CombatSimulator simContext) {
        double multiplier = ResistanceCalculator.calculateMultiplier(
                simContext.getEnemy(), simContext.getTeamBuffList(),
                simContext.getCurrentTime(), damageElement);
        return preResistanceDamage * multiplier;
    }
}
