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
    private static final double STANDARD_EC_TICK_INTERVAL = 1.0;
    private static final double STANDARD_EC_PREMATURE_TICK_THRESHOLD = 0.5;
    private static final double BURNING_TICK_INTERVAL = 0.25;
    private static final double BURNING_MIN_FUEL_DECAY_RATE = 0.4;
    private static final double TIMING_EPSILON = 1e-9;

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

        sim.getEnemy().applyAura(trigger, gaugeUnits, sim.getCurrentTime());
        if (!sim.isECTimerRunning()) {
            sim.setECTimerRunning(true);
            sim.registerEvent(createElectroChargedTickEvent(preResistanceDamage, isLunar));
        }
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
        scheduleBurning(ownerId, preResistanceDamage, false);
    }

    /**
     * Starts or refreshes Burning from the current Dendro-like fuel.
     *
     * @param ownerId character credited with Burning damage
     * @param preResistanceDamage damage per 0.25 s tick before RES
     * @param replaceFuel whether this application is a Dendro fuel overwrite
     */
    public void scheduleBurning(
            CharacterId ownerId,
            double preResistanceDamage,
            boolean replaceFuel) {
        double currentTime = sim.getCurrentTime();
        double dendroUnits = sim.getEnemy().getAuraUnits(Element.DENDRO, currentTime);
        ReactionState.QuickenState quickenState = sim.getQuickenState();
        double quickenUnits = quickenState != null
                ? quickenState.remainingUnitsAt(currentTime)
                : 0.0;
        double fuelUnits = replaceFuel
                ? dendroUnits
                : Math.max(dendroUnits, quickenUnits);
        if (fuelUnits <= 0.0) {
            sim.clearBurning();
            return;
        }
        double dendroDecayRate = sim.getEnemy().getAuraDecayRate(
                Element.DENDRO, currentTime);
        double quickenDecayRate = quickenState != null
                ? quickenState.decayRate
                : 0.0;
        double naturalDecayRate = Math.max(dendroDecayRate, quickenDecayRate);
        double fuelDecayRate = Math.max(
                BURNING_MIN_FUEL_DECAY_RATE, naturalDecayRate * 2.0);
        ReactionState.BurningState state = sim.getBurningState();
        boolean startsNewGeneration = state == null
                || state.remainingFuelAt(currentTime) <= TIMING_EPSILON;
        if (startsNewGeneration) {
            state = sim.startBurning(
                    ownerId, preResistanceDamage, fuelUnits, fuelDecayRate);
        } else {
            if (replaceFuel) {
                state = sim.replaceBurningFuel(fuelUnits, fuelDecayRate);
            }
            if (state != null) {
                state = sim.refreshBurningDamage(ownerId, preResistanceDamage);
            }
        }
        if (state == null) {
            return;
        }
        if (!sim.isBurningTimerRunning()) {
            sim.setBurningTimerRunning(true);
            sim.registerEvent(createBurningTickEvent(state.generation));
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
        if (isLunar) {
            return createLunarChargedTickEvent();
        }
        return createStandardElectroChargedTickEvent(preResistanceDamage);
    }

    private TimerEvent createStandardElectroChargedTickEvent(double preResistanceDamage) {
        return new TimerEvent() {
            private double lastDamageTime = sim.getCurrentTime();
            private double nominalTickTime = lastDamageTime + STANDARD_EC_TICK_INTERVAL;
            private double nextTick = getNextStandardElectroChargedWake(nominalTickTime);
            private boolean finished = false;

            @Override
            public void tick(CombatSimulator simContext) {
                double currentTime = simContext.getCurrentTime();
                boolean hydroActive = simContext.getEnemy().getAuraUnits(Element.HYDRO, currentTime) > 0.0;
                boolean electroActive = simContext.getEnemy().getAuraUnits(Element.ELECTRO, currentTime) > 0.0;

                if (!hydroActive || !electroActive) {
                    if (currentTime - lastDamageTime
                            > STANDARD_EC_PREMATURE_TICK_THRESHOLD + TIMING_EPSILON) {
                        recordElectroChargedTick(preResistanceDamage, false);
                        simContext.getEnemy().reduceAura(Element.HYDRO, 0.4, currentTime);
                        simContext.getEnemy().reduceAura(Element.ELECTRO, 0.4, currentTime);
                    }
                    finishElectroChargedEvent();
                    return;
                }

                if (currentTime + TIMING_EPSILON < nominalTickTime) {
                    nextTick = getNextStandardElectroChargedWake(nominalTickTime);
                    return;
                }

                recordElectroChargedTick(preResistanceDamage, false);
                lastDamageTime = currentTime;
                simContext.getEnemy().reduceAura(Element.HYDRO, 0.4, currentTime);
                simContext.getEnemy().reduceAura(Element.ELECTRO, 0.4, currentTime);

                hydroActive = simContext.getEnemy().getAuraUnits(Element.HYDRO, currentTime) > 0.0;
                electroActive = simContext.getEnemy().getAuraUnits(Element.ELECTRO, currentTime) > 0.0;
                if (!hydroActive || !electroActive) {
                    finishElectroChargedEvent();
                    return;
                }

                nominalTickTime = currentTime + STANDARD_EC_TICK_INTERVAL;
                nextTick = getNextStandardElectroChargedWake(nominalTickTime);
            }

            private void finishElectroChargedEvent() {
                sim.setECTimerRunning(false);
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

    private TimerEvent createLunarChargedTickEvent() {
        return new TimerEvent() {
            private double nextTick = sim.getCurrentTime() + 2.0;

            @Override
            public void tick(CombatSimulator simContext) {
                if (simContext.getCurrentTime() > simContext.getThundercloudEndTime()) {
                    simContext.setECTimerRunning(false);
                    nextTick = Double.MAX_VALUE;
                    return;
                }

                recordElectroChargedTick(0.0, true);
                simContext.getEnemy().reduceAura(Element.HYDRO, 0.4, simContext.getCurrentTime());
                simContext.getEnemy().reduceAura(Element.ELECTRO, 0.4, simContext.getCurrentTime());
                nextTick += 2.0;
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

    private double getNextStandardElectroChargedWake(double nominalTickTime) {
        double currentTime = sim.getCurrentTime();
        double hydroExpiry = sim.getEnemy().getAuraExpiryTime(Element.HYDRO, currentTime);
        double electroExpiry = sim.getEnemy().getAuraExpiryTime(Element.ELECTRO, currentTime);
        return Math.min(nominalTickTime, Math.min(hydroExpiry, electroExpiry));
    }

    private void recordElectroChargedTick(double preResistanceDamage, boolean isLunar) {
        String label = isLunar ? "Lunar-Charged Reaction" : "Electro-Charged Tick";
        double finalDamage = isLunar
                ? computeWeightedLunarReactionDamage(
                        sim, Element.ELECTRO, StatType.LUNAR_CHARGED_DMG_BONUS)
                : applyCurrentResistance(preResistanceDamage, Element.ELECTRO, sim);

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [DoT] %s Damage: %,.0f", label, finalDamage));
        }

        sim.recordDamage("Thundercloud", finalDamage);
        sim.getCombatLogSink().log(
                sim.getCurrentTime(), "Thundercloud", label, finalDamage,
                label, finalDamage, sim.getEnemy().getAuraMap(sim.getCurrentTime()));

        if (isLunar) {
            sim.notifyReaction(
                    ReactionResult.transform(
                            finalDamage,
                            "Thundercloud-Strike",
                            ReactionResult.Kind.THUNDERCLOUD_STRIKE),
                    sim.getActiveCharacter());
        }
    }

    private TimerEvent createBurningTickEvent(int generation) {
        return new TimerEvent() {
            private double nextDamageTime = sim.getCurrentTime() + BURNING_TICK_INTERVAL;
            private boolean finished = false;

            @Override
            public void tick(CombatSimulator simContext) {
                ReactionState.BurningState state = simContext.getBurningState();
                if (state == null || state.generation != generation) {
                    finish();
                    return;
                }

                double currentTime = simContext.getCurrentTime();
                double remainingFuel = state.remainingFuelAt(currentTime);
                boolean damageTick = currentTime + TIMING_EPSILON >= nextDamageTime
                        && currentTime <= state.getEndTime() + TIMING_EPSILON;
                if (damageTick) {
                    recordBurningTick(state);
                    nextDamageTime += BURNING_TICK_INTERVAL;
                }

                synchronizeBurningFuel(simContext, state, currentTime);

                if (remainingFuel <= TIMING_EPSILON) {
                    simContext.clearBurning();
                    finish();
                    return;
                }
                simContext.advanceBurning();
            }

            @Override
            public double getNextTickTime() {
                ReactionState.BurningState state = sim.getBurningState();
                if (state == null || state.generation != generation) {
                    return nextDamageTime;
                }
                return Math.min(nextDamageTime, state.getEndTime());
            }

            @Override
            public boolean isFinished(double time) {
                return finished;
            }

            private void finish() {
                finished = true;
                nextDamageTime = Double.MAX_VALUE;
            }
        };
    }

    private void synchronizeBurningFuel(
            CombatSimulator simContext,
            ReactionState.BurningState state,
            double currentTime) {
        double elapsed = Math.max(0.0, currentTime - state.lastUpdateTime);
        if (elapsed <= 0.0) {
            return;
        }

        double dendroNaturalRate = simContext.getEnemy().getAuraDecayRate(
                Element.DENDRO, currentTime);
        double dendroExtraDecay = Math.max(
                0.0, state.fuelDecayRate - dendroNaturalRate) * elapsed;
        if (dendroExtraDecay > TIMING_EPSILON) {
            simContext.getEnemy().reduceAura(
                    Element.DENDRO, dendroExtraDecay, currentTime);
        }
        double remainingDendro = simContext.getEnemy().getAuraUnits(
                Element.DENDRO, currentTime);
        if (remainingDendro > 0.0 && remainingDendro <= TIMING_EPSILON) {
            simContext.getEnemy().reduceAura(
                    Element.DENDRO, remainingDendro, currentTime);
        }

        ReactionState.QuickenState quickenState = simContext.getQuickenState();
        if (quickenState == null) {
            return;
        }
        double quickenExtraDecay = Math.max(
                0.0, state.fuelDecayRate - quickenState.decayRate) * elapsed;
        if (quickenExtraDecay > TIMING_EPSILON) {
            simContext.consumeQuicken(quickenExtraDecay);
        }
        ReactionState.QuickenState remainingQuicken = simContext.getQuickenState();
        if (remainingQuicken != null
                && remainingQuicken.remainingUnitsAt(currentTime) <= TIMING_EPSILON) {
            simContext.clearQuicken();
        }
    }

    private void recordBurningTick(ReactionState.BurningState state) {
        double tickDamage = applyCurrentResistance(
                state.preResistanceDamage, Element.PYRO, sim);
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [DoT] Burning Damage: %,.0f", tickDamage));
        }
        sim.recordDamage(state.ownerId, tickDamage);
        sim.getCombatLogSink().log(
                sim.getCurrentTime(), state.ownerId.getDisplayName(), "Burning", tickDamage,
                "Burning", tickDamage, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
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
