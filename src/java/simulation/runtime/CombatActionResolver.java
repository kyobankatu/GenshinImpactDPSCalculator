package simulation.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.formula.DamageCalculator;
import mechanics.formula.ResistanceCalculator;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionEffectScheduler;
import mechanics.reaction.ReactionPriority;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.event.TimerEvent;

/**
 * Resolves an {@link AttackAction} into damage, aura changes, reactions, and related
 * combat side effects without advancing simulation time.
 *
 * <p>This class extracts the heavy action-resolution logic out of
 * {@link CombatSimulator} so that the simulator can focus on orchestration,
 * timeline progression, and party-level state management.
 */
public class CombatActionResolver {
    /** Owning simulator. */
    private final CombatSimulator sim;
    /** Scheduler for follow-up effects of transformative reactions. */
    private final ReactionEffectScheduler reactionEffectScheduler;
    /** Reusable buffer for the list of buffs applicable to the resolving action. */
    private final List<Buff> applicableBuffBuffer = new ArrayList<>();
    /** Whether gauge resolution is processing Burning's own periodic Pyro packet. */
    private boolean resolvingBurningTickApplication;

    /**
     * Creates a resolver bound to the given simulator instance.
     *
     * @param sim active combat simulator whose runtime state will be read and updated
     */
    public CombatActionResolver(CombatSimulator sim) {
        this.sim = sim;
        this.reactionEffectScheduler = new ReactionEffectScheduler(sim);
    }

    /** Reconstructs the simulator-owned Burning timer after snapshot restore. */
    public void restoreBurningTimer(double nextTickTime) {
        reactionEffectScheduler.restoreBurningTimer(nextTickTime);
    }

    /**
     * Resolves all damage and elemental effects of an {@link AttackAction} without
     * advancing simulation time.
     *
     * <p>This is used by the simulator for immediate action execution and by periodic
     * timer events whose timing is managed elsewhere.
     *
     * @param characterId the character performing the action
     * @param action   the {@link AttackAction} to resolve
     * @throws RuntimeException if no character with {@code characterId} exists in the party
     */
    public void resolveWithoutTimeAdvance(CharacterId characterId, AttackAction action) {
        Character c = sim.getCharacter(characterId);
        if (c == null) {
            throw new RuntimeException("Character not found: " + characterId);
        }
        sim.pushBuffSource(characterId);
        try {
            String charName = c.getName();
            ActionResolutionContext context = createContext(c, action);
            action.setAdditiveBaseDmgBonus(0.0);
            action.setAdditiveReactionName(null);

            normalizeIcd(action);

            boolean applied = sim.getIcdManager().checkApplication(
                    characterId.name(), action.getICDTag(), action.getICDType(), sim.getCurrentTime());

            notifyLunarAction(action, c);
            notifyStellarAction(action, c);

            double reactionMulti = 1.0;
            sim.getEnemy().updateAuras(sim.getCurrentTime());
            if (applied) {
                tryTriggerShatter(c, characterId, action, context);
                tryTriggerDendroCoreReaction(c, characterId, action, context);
                tryApplyCatalyzeAdditiveReaction(c, action, context);
            }
            if (applied && action.getGaugeUnits() > 0) {
                reactionMulti = resolveGaugeAndReactions(c, characterId, action, context);
                sim.getStellarReactionManager().recordElementApplication(
                        characterId, action.getElement(), sim.getCurrentTime());
            } else if (!applied && action.getGaugeUnits() > 0 && sim.isLoggingEnabled()) {
                System.out.println(String.format("   [ICD] Applied blocked (%s)", action.getICDTag()));
            }

            finalizeActionDamage(c, charName, action, reactionMulti, context);
        } finally {
            sim.popBuffSource();
        }
    }

    /**
     * Resolves the 1U Pyro application carried by an accepted Burning tick.
     *
     * <p>Damage remains owned by {@link ReactionEffectScheduler}; this path only
     * applies reaction and Aura effects with the Burning applier's saved stats.
     * The target-wide 2-second ICD is checked by the caller.</p>
     *
     * @param state immutable Burning payload used by this tick
     * @return amplifying multiplier for the Burning damage packet
     */
    public double resolveBurningTickReactions(ReactionState.BurningState state) {
        if (state == null) {
            return 1.0;
        }
        Character attacker = sim.getCharacter(state.ownerId);
        if (attacker == null) {
            return 1.0;
        }
        AttackAction action = new AttackAction(
                "Burning Pyro Application",
                0.0,
                Element.PYRO,
                StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        StatsContainer reactionStats = state.getReactionStats();
        if (reactionStats != null) {
            action.setStatSnapshot(reactionStats);
        }

        sim.pushBuffSource(state.ownerId);
        try {
            ActionResolutionContext context = createContext(attacker, action);
            tryTriggerDendroCoreReaction(
                    attacker, state.ownerId, action, context);
            resolvingBurningTickApplication = true;
            double multiplier;
            try {
                multiplier = resolveGaugeAndReactions(
                        attacker, state.ownerId, action, context);
            } finally {
                resolvingBurningTickApplication = false;
            }
            sim.getStellarReactionManager().recordElementApplication(
                    state.ownerId, Element.PYRO, sim.getCurrentTime());
            return multiplier;
        } finally {
            sim.popBuffSource();
        }
    }

    /**
     * Builds the per-action resolution context (snapshot buffs + resolved stats).
     *
     * @param attacker attacking character
     * @param action   action being resolved
     * @return immutable context snapshot
     */
    private ActionResolutionContext createContext(Character attacker, AttackAction action) {
        applicableBuffBuffer.clear();
        applicableBuffBuffer.addAll(sim.getApplicableBuffs(attacker));
        StatsContainer resolvedStats = DamageCalculator.resolveTargetStats(
                attacker, sim.getEnemy(), action, applicableBuffBuffer,
                sim.getCurrentTime(), sim);
        return new ActionResolutionContext(
                new ArrayList<>(applicableBuffBuffer),
                new ArrayList<>(sim.getTeamBuffList()),
                resolvedStats);
    }

    /**
     * Fills in defaults for {@link ICDTag}/{@link ICDType} when an action omits them.
     *
     * @param action action whose ICD settings should be normalized
     */
    private void normalizeIcd(AttackAction action) {
        if (action.getICDTag() == null) {
            action.setICD(action.getICDType(), ICDTag.None, action.getGaugeUnits());
        }
        if (action.getICDType() == null) {
            action.setICD(ICDType.Standard, action.getICDTag(), action.getGaugeUnits());
        }
    }

    /**
     * Emits the appropriate Lunar reaction notification for actions flagged as Lunar.
     *
     * @param action    action being resolved
     * @param character actor that triggered the action
     */
    private void notifyLunarAction(AttackAction action, Character character) {
        if (!action.isLunarConsidered() || action.getLunarReactionType() == null) {
            return;
        }
        switch (action.getLunarReactionType()) {
            case CHARGED:
                sim.notifyDerivedReaction(
                        ReactionResult.lunar(
                                0.0, ReactionResult.LunarType.CHARGED),
                        character);
                break;
            case BLOOM:
                sim.notifyDerivedReaction(
                        ReactionResult.lunar(
                                0.0, ReactionResult.LunarType.BLOOM),
                        character);
                break;
            case CRYSTALLIZE:
                sim.notifyDerivedReaction(
                        ReactionResult.lunar(
                                0.0, ReactionResult.LunarType.CRYSTALLIZE),
                        character);
                break;
        }
    }

    /** Emits a typed reaction notification for direct Stellar damage. */
    private void notifyStellarAction(AttackAction action, Character character) {
        if (!action.isStellarConsidered()) {
            return;
        }
        ReactionResult.Kind kind = action.getStellarReactionType()
                == AttackAction.StellarReactionType.CONDUCT
                ? ReactionResult.Kind.STELLAR_CONDUCT
                : ReactionResult.Kind.STELLAR_SWIRL;
        sim.notifyDerivedReaction(
                ReactionResult.stellar(
                        0.0, kind, action.getElement(), action.getElement(), false),
                character);
    }

    /**
     * Drives gauge consumption and reaction resolution across currently active enemy auras.
     *
     * @param attacker    acting character
     * @param characterId acting character id
     * @param action      action being resolved
     * @param context     resolved per-action context
     * @return amplifying-reaction multiplier (1.0 when no amp reaction triggered)
     */
    private double resolveGaugeAndReactions(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context) {
        Element trigger = action.getElement();
        Set<Element> currentAuras = sim.getEnemy().getActiveAuras(sim.getCurrentTime());
        if (isBurningAuraReactiveTrigger(trigger) && sim.isBurningActive()) {
            currentAuras.add(Element.PYRO);
        }
        boolean frozenAtStart = sim.getEnemy().isFrozen(sim.getCurrentTime());
        if (trigger == Element.PYRO && frozenAtStart) {
            currentAuras.add(Element.CRYO);
        }
        double reactionMulti = 1.0;
        double remainingGaugeUnits = action.getGaugeUnits();
        boolean preventsTriggerAttachment = false;
        boolean suppressElectroCharged = trigger == Element.ELECTRO
                && frozenAtStart;

        StatsContainer stats = getReactionStats(attacker, action, context);
        double em = stats.get(StatType.ELEMENTAL_MASTERY);

        double quickenBloomConsumption = tryTriggerQuickenOnlyBloom(
                attacker, characterId, action, context, stats, currentAuras,
                remainingGaugeUnits);
        if (quickenBloomConsumption > 0.0) {
            remainingGaugeUnits -= quickenBloomConsumption;
            preventsTriggerAttachment = true;
        }
        tryTriggerQuickenOnlyBurning(
                attacker, characterId, action, context, stats, currentAuras);

        boolean frozenReactionChecked = false;
        for (Element aura : ReactionPriority.orderAuras(trigger, currentAuras)) {
            if (remainingGaugeUnits <= 0.0) {
                break;
            }
            if (trigger == Element.ELECTRO
                    && !frozenReactionChecked
                    && aura != Element.PYRO) {
                frozenReactionChecked = true;
                double frozenConsumption = tryTriggerFrozenGaugeReaction(
                        attacker, characterId, action, context, stats,
                        remainingGaugeUnits);
                if (frozenConsumption > 0.0) {
                    remainingGaugeUnits -= frozenConsumption;
                    preventsTriggerAttachment = true;
                    if (remainingGaugeUnits <= 0.0) {
                        break;
                    }
                }
            }
            if (suppressElectroCharged && aura == Element.HYDRO) {
                continue;
            }
            if (trigger == Element.PYRO
                    && frozenAtStart
                    && aura == Element.HYDRO) {
                continue;
            }
            ReactionResult result = ReactionCalculator.calculate(
                    trigger, aura, em, 90, getReactionBonus(trigger, aura, stats));
            if (result.getType() == ReactionResult.Type.NONE) {
                continue;
            }
            result = convertToStellarIfEligible(result);
            result = convertToLunarIfEligible(result);
            if (result.getKind() == ReactionResult.Kind.CRYSTALLIZE
                    && !sim.tryStartStandardCrystallizeCooldown()) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "   [ICD] %s on %s -> Crystallize blocked (global cooldown)",
                            trigger, aura));
                }
                continue;
            }
            if (result.getKind() == ReactionResult.Kind.BURNING
                    && sim.isBurningActive()) {
                if (!resolvingBurningTickApplication) {
                    boolean replacesFuel = trigger == Element.DENDRO;
                    if (replacesFuel) {
                        sim.synchronizeBurningFuel();
                        sim.advanceBurning();
                        sim.getEnemy().replaceAuraFromSource(
                                Element.DENDRO,
                                remainingGaugeUnits,
                                sim.getCurrentTime());
                    }
                    reactionEffectScheduler.scheduleBurning(
                            characterId, result.getTransformDamage(),
                            replacesFuel, stats);
                }
                continue;
            }

            boolean triggerWasUnreacted = !preventsTriggerAttachment;
            if (result.getKind() != ReactionResult.Kind.BURNING) {
                preventsTriggerAttachment = true;
            }
            sim.notifyReaction(result, attacker);

            if (result.getType() == ReactionResult.Type.AMP) {
                AmpReactionResolution resolution = handleAmplifyingReaction(
                        trigger, aura, remainingGaugeUnits, result);
                reactionMulti = resolution.multiplier;
                remainingGaugeUnits -= resolution.consumedGaugeUnits;
            } else if (result.isStateful()) {
                remainingGaugeUnits -= handleStatefulReaction(
                        attacker, characterId, trigger, aura, action, result,
                        stats, context, remainingGaugeUnits);
            } else if (result.getType() == ReactionResult.Type.TRANSFORMATIVE) {
                remainingGaugeUnits -= handleTransformativeReaction(
                        attacker, characterId, action, trigger, aura, result,
                        stats, context, remainingGaugeUnits,
                        triggerWasUnreacted);
            }
        }

        if (!frozenReactionChecked && remainingGaugeUnits > 0.0) {
            double frozenConsumption = tryTriggerFrozenGaugeReaction(
                    attacker, characterId, action, context, stats,
                    remainingGaugeUnits);
            if (frozenConsumption > 0.0) {
                remainingGaugeUnits -= frozenConsumption;
                preventsTriggerAttachment = true;
            }
        }

        if (!preventsTriggerAttachment && remainingGaugeUnits > 0.0) {
            applyTriggerAuraIfPersistent(trigger, remainingGaugeUnits);
        }

        return reactionMulti;
    }

    /** Result of one amplifying reaction including consumed source gauge. */
    private static final class AmpReactionResolution {
        private final double multiplier;
        private final double consumedGaugeUnits;

        private AmpReactionResolution(
                double multiplier, double consumedGaugeUnits) {
            this.multiplier = multiplier;
            this.consumedGaugeUnits = consumedGaugeUnits;
        }
    }

    private ReactionResult convertToLunarIfEligible(ReactionResult result) {
        if (!sim.hasLunarReactionConversion()) {
            return result;
        }
        if (result.getKind() == ReactionResult.Kind.ELECTRO_CHARGED) {
            return ReactionResult.lunar(
                    result.getTransformDamage(),
                    ReactionResult.LunarType.CHARGED,
                    result.getRelatedElement(),
                    Element.ELECTRO,
                    false,
                    false);
        }
        if (result.getKind() == ReactionResult.Kind.BLOOM) {
            return ReactionResult.lunar(
                    result.getTransformDamage(),
                    ReactionResult.LunarType.BLOOM,
                    result.getRelatedElement(),
                    Element.DENDRO,
                    true,
                    false);
        }
        if (result.getKind() == ReactionResult.Kind.CRYSTALLIZE && result.getRelatedElement() == Element.HYDRO) {
            return ReactionResult.lunar(
                    result.getTransformDamage(),
                    ReactionResult.LunarType.CRYSTALLIZE,
                    result.getRelatedElement(),
                    Element.GEO,
                    true,
                    false);
        }
        return result;
    }

    private ReactionResult convertToStellarIfEligible(ReactionResult result) {
        if (result.getKind() == ReactionResult.Kind.SUPERCONDUCT
                && sim.hasStellarConductConversion()) {
            return ReactionResult.stellar(
                    0.0,
                    ReactionResult.Kind.STELLAR_CONDUCT,
                    Element.CRYO,
                    Element.CRYO,
                    true);
        }
        if (result.getKind() == ReactionResult.Kind.SWIRL
                && result.getRelatedElement() == Element.CRYO
                && sim.hasStellarSwirlConversion()) {
            return ReactionResult.stellar(
                    ReactionCalculator.calculateStellarSwirlBaseDamage(90),
                    ReactionResult.Kind.STELLAR_SWIRL,
                    Element.CRYO,
                    Element.ANEMO,
                    false);
        }
        return result;
    }

    private void tryTriggerShatter(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context) {
        boolean canShatter = sim.getEnemy().isFrozen(sim.getCurrentTime())
                && (action.getElement() == Element.GEO || action.isShatterTrigger());
        if (!canShatter) {
            return;
        }
        StatsContainer stats = getReactionStats(attacker, action, context);
        ReactionResult result = ReactionCalculator.calculateShatter(
                stats.get(StatType.ELEMENTAL_MASTERY), 90, 0.0);
        sim.notifyReaction(result, attacker);
        sim.getEnemy().reduceFreezeAura(2.0, sim.getCurrentTime());
        if (!sim.tryStartShatterDamageSequence(characterId)) {
            if (sim.isLoggingEnabled()) {
                System.out.println(
                        "   [Reaction] Frozen target -> Shatter Damage blocked (damage sequence)");
            }
            return;
        }
        double resFactor = resolveImpactResistance(context, Element.PHYSICAL);
        double damage = result.getTransformDamage() * resFactor;
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [Reaction] Frozen target -> Shatter Damage: %,.0f", damage));
        }
        sim.recordDamage(characterId, damage);
        if (sim.isLoggingEnabled()) {
            sim.getCombatLogSink().log(
                    sim.getCurrentTime(), attacker.getName(), "Shatter", damage,
                    "Shatter", damage, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
        }
    }

    /**
     * Resolves reactions that treat the synthetic Frozen gauge as Cryo.
     *
     * <p>Electro consumes coexisting Cryo before Frozen, then exhausts its trigger
     * while producing one Superconduct. Anemo and Geo reach Frozen after their
     * ordinary Aura attempts and consume it at the 0.5 gauge modifier used by
     * Swirl and Crystallize.
     */
    private double tryTriggerFrozenGaugeReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context,
            StatsContainer stats,
            double availableGaugeUnits) {
        Element trigger = action.getElement();
        if (!sim.getEnemy().isFrozen(sim.getCurrentTime())
                || (trigger != Element.ELECTRO
                        && trigger != Element.ANEMO
                        && trigger != Element.GEO)) {
            return 0.0;
        }

        ReactionResult result = ReactionCalculator.calculate(
                trigger,
                Element.CRYO,
                stats.get(StatType.ELEMENTAL_MASTERY),
                90,
                getReactionBonus(trigger, Element.CRYO, stats));
        result = convertToStellarIfEligible(result);
        if (result.getType() == ReactionResult.Type.NONE) {
            return 0.0;
        }
        if (result.getKind() == ReactionResult.Kind.CRYSTALLIZE
                && !sim.tryStartStandardCrystallizeCooldown()) {
            return 0.0;
        }

        sim.notifyReaction(result, attacker);
        double modifier = trigger == Element.ELECTRO ? 1.0 : 0.5;
        double ordinaryConsumption = 0.0;
        double frozenSourceGaugeUnits = availableGaugeUnits;
        if (trigger == Element.ELECTRO) {
            ordinaryConsumption = sim.getEnemy().consumeAura(
                    Element.CRYO, availableGaugeUnits, modifier,
                    sim.getCurrentTime());
            frozenSourceGaugeUnits = Math.max(
                    0.0, availableGaugeUnits - ordinaryConsumption);
        }
        double frozenUnits = sim.getEnemy().getFreezeAuraUnits(
                sim.getCurrentTime());
        double frozenAuraConsumption = Math.min(
                frozenUnits, frozenSourceGaugeUnits * modifier);
        sim.getEnemy().reduceFreezeAura(
                frozenAuraConsumption, sim.getCurrentTime());
        double consumedGaugeUnits = trigger == Element.ELECTRO
                ? availableGaugeUnits
                : frozenAuraConsumption / modifier;

        if (result.isStateful()) {
            if (result.getKind() == ReactionResult.Kind.STELLAR_CONDUCT) {
                sim.getStellarReactionManager().triggerStellarConduct(
                        sim.getCurrentTime());
            }
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on Frozen -> %s",
                        trigger, result.getName()));
            }
        } else {
            handleTransformativeReaction(
                    attacker,
                    characterId,
                    action,
                    trigger,
                    Element.CRYO,
                    result,
                    stats,
                    context,
                    availableGaugeUnits,
                    false,
                    false);
        }
        return consumedGaugeUnits;
    }

    private double getReactionBonus(Element trigger, Element aura, StatsContainer stats) {
        if (isElementPair(trigger, aura, Element.PYRO, Element.HYDRO)) {
            return stats.get(StatType.VAPORIZE_DMG_BONUS);
        }
        if (isElementPair(trigger, aura, Element.PYRO, Element.CRYO)) {
            return stats.get(StatType.MELT_DMG_BONUS);
        }
        if (isElementPair(trigger, aura, Element.PYRO, Element.ELECTRO)) {
            return stats.get(StatType.OVERLOAD_DMG_BONUS);
        }
        if (isElementPair(trigger, aura, Element.CRYO, Element.ELECTRO)) {
            return stats.get(StatType.SUPERCONDUCT_DMG_BONUS);
        }
        if (isElementPair(trigger, aura, Element.PYRO, Element.DENDRO)) {
            return stats.get(StatType.BURNING_DMG_BONUS);
        }
        if (trigger == Element.ANEMO
                && (aura == Element.PYRO || aura == Element.HYDRO || aura == Element.ELECTRO || aura == Element.CRYO)) {
            return stats.get(StatType.SWIRL_DMG_BONUS);
        }
        if ((trigger == Element.HYDRO && aura == Element.DENDRO)
                || (trigger == Element.DENDRO && aura == Element.HYDRO)) {
            return getBloomReactionBonus(stats);
        }
        return 0.0;
    }

    /** Selects the bonus for the result that the current party will produce. */
    private double getBloomReactionBonus(StatsContainer stats) {
        StatType bonusType = sim.hasLunarReactionConversion()
                ? StatType.LUNAR_BLOOM_DMG_BONUS
                : StatType.BLOOM_DMG_BONUS;
        return stats.get(bonusType);
    }

    /** Returns whether two elements match an unordered reaction pair. */
    private boolean isElementPair(
            Element trigger,
            Element aura,
            Element first,
            Element second) {
        return (trigger == first && aura == second)
                || (trigger == second && aura == first);
    }

    private void tryTriggerDendroCoreReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context) {
        if (sim.getDendroCores().isEmpty()
                || (action.getElement() != Element.ELECTRO && action.getElement() != Element.PYRO)) {
            return;
        }

        StatsContainer stats = getReactionStats(attacker, action, context);
        ReactionResult result = action.getElement() == Element.ELECTRO
                ? ReactionCalculator.calculateHyperbloom(
                        stats.get(StatType.ELEMENTAL_MASTERY), 90, stats.get(StatType.HYPERBLOOM_DMG_BONUS))
                : ReactionCalculator.calculateBurgeon(
                        stats.get(StatType.ELEMENTAL_MASTERY), 90, stats.get(StatType.BURGEON_DMG_BONUS));
        int maxCores = action.getDendroCoreConsumptionLimit();
        if (maxCores <= 0) {
            maxCores = action.getElement() == Element.ELECTRO ? 1 : Integer.MAX_VALUE;
        }
        int consumed = reactionEffectScheduler.consumeDendroCores(
                characterId, result.getTransformDamage(), result.getName(), maxCores);
        if (consumed > 0) {
            sim.notifyReaction(result, attacker);
            if (sim.isLoggingEnabled()) {
                double damage = result.getTransformDamage()
                        * resolveImpactResistance(context, Element.DENDRO);
                System.out.println(String.format(
                        "   [Reaction] %s consumed %d Dendro Core(s) -> %s Damage: %,.0f each",
                        action.getElement(), consumed, result.getName(), damage));
            }
        }
    }

    /**
     * Returns the stats used for reaction computation, honoring snapshot semantics.
     *
     * @param attacker acting character
     * @param action   action being resolved
     * @param context  resolved per-action context
     * @return stats container to read EM / reaction bonuses from
     */
    private StatsContainer getReactionStats(Character attacker, AttackAction action, ActionResolutionContext context) {
        if (action.hasStatSnapshot()) {
            return action.getStatSnapshot();
        }
        if (action.isUseSnapshot()) {
            return attacker.getSnapshot();
        }
        return context.resolvedStats;
    }

    /**
     * Applies Melt/Vaporize aura consumption and returns the amplifying multiplier.
     *
     * @param trigger trigger element
     * @param aura    consumed aura element
     * @param availableGaugeUnits trigger gauge available to this reaction
     * @param result  resolved reaction result
     * @return multiplier and consumed trigger gauge
     */
    private AmpReactionResolution handleAmplifyingReaction(
            Element trigger,
            Element aura,
            double availableGaugeUnits,
            ReactionResult result) {
        double reactionMulti = result.getAmpMultiplier();
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Multi %.2f",
                    trigger, aura, result.getName(), reactionMulti));
        }

        boolean isReverse = (trigger == Element.PYRO && aura == Element.HYDRO)
                || (trigger == Element.CRYO && aura == Element.PYRO);
        double modifier = isReverse ? 0.5 : 2.0;
        double consumedGaugeUnits;
        if (trigger == Element.PYRO && aura == Element.CRYO) {
            double ordinaryConsumption = sim.getEnemy().consumeAura(
                    Element.CRYO, availableGaugeUnits, modifier,
                    sim.getCurrentTime());
            double frozenAuraConsumption = Math.min(
                    sim.getEnemy().getFreezeAuraUnits(sim.getCurrentTime()),
                    availableGaugeUnits * modifier);
            sim.getEnemy().reduceFreezeAura(
                    frozenAuraConsumption, sim.getCurrentTime());
            consumedGaugeUnits = Math.max(
                    ordinaryConsumption, frozenAuraConsumption / modifier);
        } else {
            consumedGaugeUnits = consumeReactiveAura(
                    aura, availableGaugeUnits, modifier);
        }
        return new AmpReactionResolution(reactionMulti, consumedGaugeUnits);
    }

    /**
     * Applies transformative-reaction damage (Swirl, Overload, Electro-Charged, etc.),
     * records the damage, and schedules follow-up effects when applicable.
     *
     * @param attacker    acting character
     * @param characterId acting character id
     * @param action      source action
     * @param trigger     trigger element
     * @param aura        consumed aura element
     * @param result      resolved reaction result
     * @param stats       stats used for reaction bonuses
     * @param context     start-of-hit buffs used for impact resistance
     * @param availableGaugeUnits trigger gauge available to this reaction
     * @param triggerWasUnreacted whether earlier reactions already claimed the trigger
     * @return trigger gauge consumed by this reaction
     */
    private double handleTransformativeReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            Element trigger,
            Element aura,
            ReactionResult result,
            StatsContainer stats,
            ActionResolutionContext context,
            double availableGaugeUnits,
            boolean triggerWasUnreacted) {
        return handleTransformativeReaction(
                attacker,
                characterId,
                action,
                trigger,
                aura,
                result,
                stats,
                context,
                availableGaugeUnits,
                triggerWasUnreacted,
                true);
    }

    private double handleTransformativeReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            Element trigger,
            Element aura,
            ReactionResult result,
            StatsContainer stats,
            ActionResolutionContext context,
            double availableGaugeUnits,
            boolean triggerWasUnreacted,
            boolean consumeOrdinaryAura) {
        Element reactionElement = getTransformativeReactionElement(result);
        double consumedGaugeUnits = 0.0;

        if (!result.isElectroCharged() && consumeOrdinaryAura) {
            double modifier = getAuraConsumptionModifier(result, trigger, aura);
            consumedGaugeUnits = consumeReactiveAura(
                    aura, availableGaugeUnits, modifier);
        }
        if (result.getKind() == ReactionResult.Kind.STELLAR_SWIRL) {
            sim.getStellarReactionManager().triggerStellarSwirl(sim.getCurrentTime());
        }

        if (result.getKind() == ReactionResult.Kind.SUPERCONDUCT) {
            applySuperconductPhysicalResShred();
        }

        if (result.isSwirl()
                && !sim.tryStartSwirlDamageSequence(
                        characterId, result.getRelatedElement())) {
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s Damage blocked (damage sequence)",
                        trigger, aura, result.getName()));
            }
            return consumedGaugeUnits;
        }

        if (isOverload(result)
                && !sim.tryStartOverloadDamageCooldown(characterId)) {
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s Damage blocked (damage sequence)",
                        trigger, aura, result.getName()));
            }
            return consumedGaugeUnits;
        }
        if (result.getKind() == ReactionResult.Kind.SUPERCONDUCT
                && !sim.tryStartSuperconductDamageSequence(characterId)) {
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s Damage blocked (damage sequence)",
                        trigger, aura, result.getName()));
            }
            return consumedGaugeUnits;
        }

        double resFactor = resolveImpactResistance(context, reactionElement);
        double reactBonus = result.isElectroCharged()
                ? stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS)
                : 0.0;
        double preResistanceDamage = result.getTransformDamage() * (1.0 + reactBonus);
        if (result.getKind() == ReactionResult.Kind.STELLAR_SWIRL) {
            preResistanceDamage = calculateStellarSwirlDamageBeforeResistance(
                    result.getTransformDamage(), stats);
            resFactor = resolveImpactResistance(context, Element.ANEMO);
        }
        double transDmg = preResistanceDamage * resFactor;

        boolean isLunar = result.getKind() == ReactionResult.Kind.LUNAR_CHARGED;
        String reactionLabel = isLunar ? "Lunar-Charged" : result.getName();
        double triggerDmg = isLunar ? reactionEffectScheduler.computeInitialLunarChargedDamage() : transDmg;

        if (result.getKind() == ReactionResult.Kind.ELECTRO_CHARGED
                && sim.isStandardElectroChargedActive()) {
            reactionEffectScheduler.scheduleElectroCharged(
                    characterId,
                    trigger,
                    triggerWasUnreacted ? availableGaugeUnits : 0.0,
                    preResistanceDamage,
                    false);
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s Damage deferred (active sequence)",
                        trigger, aura, reactionLabel));
            }
            return consumedGaugeUnits;
        }

        if (result.getKind() == ReactionResult.Kind.ELECTRO_CHARGED) {
            boolean damageAccepted =
                    sim.tryStartStandardElectroChargedDamageCooldown();
            reactionEffectScheduler.scheduleElectroCharged(
                    characterId,
                    trigger,
                    triggerWasUnreacted ? availableGaugeUnits : 0.0,
                    preResistanceDamage,
                    false);
            if (!damageAccepted) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "   [Reaction] %s on %s -> %s Damage blocked (target cooldown)",
                            trigger, aura, reactionLabel));
                }
                return consumedGaugeUnits;
            }
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Damage: %,.0f",
                    trigger, aura, reactionLabel, triggerDmg));
        }

        sim.recordDamage(characterId, triggerDmg);
        sim.notifyElementalIndirectDamage(
                attacker, reactionElement, triggerDmg);
        if (sim.isLoggingEnabled()) {
            sim.getCombatLogSink().log(
                    sim.getCurrentTime(), attacker.getName(), reactionLabel, triggerDmg,
                    reactionLabel, triggerDmg, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
        }

        if (isLunar) {
            reactionEffectScheduler.scheduleElectroCharged(
                    characterId,
                    trigger,
                    triggerWasUnreacted ? availableGaugeUnits : 0.0,
                    preResistanceDamage,
                    true);
        }
        return consumedGaugeUnits;
    }

    private boolean isOverload(ReactionResult result) {
        return result.getKind() == ReactionResult.Kind.OVERLOAD
                || result.getKind() == ReactionResult.Kind.OVERLOADED;
    }

    private double handleStatefulReaction(
            Character attacker,
            CharacterId characterId,
            Element trigger,
            Element aura,
            AttackAction action,
            ReactionResult result,
            StatsContainer stats,
            ActionResolutionContext context,
            double availableGaugeUnits) {
        double consumedGaugeUnits = 0.0;
        if (result.getKind() == ReactionResult.Kind.STELLAR_CONDUCT) {
            consumedGaugeUnits = consumeReactiveAura(
                    aura, availableGaugeUnits,
                    getAuraConsumptionModifier(result, trigger, aura));
            sim.getStellarReactionManager().triggerStellarConduct(sim.getCurrentTime());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Stellar-Conduct (Polestar Field)",
                        trigger, aura));
            }
        } else if (result.getKind() == ReactionResult.Kind.FROZEN) {
            consumedGaugeUnits = sim.getEnemy().consumeAura(
                    aura, availableGaugeUnits, 1.0, sim.getCurrentTime());
            double freezeUnits = 2.0 * consumedGaugeUnits;
            sim.getEnemy().applyFreezeAura(freezeUnits, sim.getCurrentTime());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Frozen (%.1f U)",
                        trigger, aura, freezeUnits));
            }
        } else if (result.getKind() == ReactionResult.Kind.CRYSTALLIZE) {
            consumedGaugeUnits = consumeReactiveAura(
                    aura, availableGaugeUnits,
                    getAuraConsumptionModifier(result, trigger, aura));
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s",
                        trigger, aura, result.getName()));
            }
        } else if (result.getKind() == ReactionResult.Kind.LUNAR_CRYSTALLIZE) {
            consumedGaugeUnits = consumeReactiveAura(
                    aura, availableGaugeUnits,
                    getAuraConsumptionModifier(result, trigger, aura));
            handleLunarCrystallize(attacker, characterId, trigger, aura, result);
        } else if (result.getKind() == ReactionResult.Kind.BURNING) {
            double resFactor = resolveImpactResistance(context, Element.PYRO);
            double tickDamage = result.getTransformDamage() * resFactor;
            boolean replacesFuel = trigger == Element.DENDRO;
            if (replacesFuel) {
                sim.getEnemy().replaceAuraFromSource(
                        Element.DENDRO, action.getGaugeUnits(), sim.getCurrentTime());
            }
            reactionEffectScheduler.scheduleBurning(
                    characterId, result.getTransformDamage(), replacesFuel, stats);
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Burning Tick Damage: %,.0f",
                        trigger, aura, tickDamage));
            }
        } else if (result.getKind() == ReactionResult.Kind.BLOOM
                || result.getKind() == ReactionResult.Kind.LUNAR_BLOOM) {
            consumedGaugeUnits = handleBloomReaction(
                    characterId, trigger, aura, result, context,
                    availableGaugeUnits, true);
        } else if (result.getKind() == ReactionResult.Kind.QUICKEN) {
            consumedGaugeUnits = sim.getEnemy().consumeAura(
                    aura, availableGaugeUnits, 1.0, sim.getCurrentTime());
            double quickenGauge = consumedGaugeUnits;
            ReactionState.QuickenState quickenState = sim.applyQuicken(quickenGauge);
            if (sim.isLoggingEnabled()) {
                double duration = quickenState != null
                        ? Math.max(0.0, quickenState.getEndTime() - sim.getCurrentTime())
                        : 0.0;
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Quicken (%.1fs)",
                        trigger, aura, duration));
            }
        }
        return consumedGaugeUnits;
    }

    private double tryTriggerQuickenOnlyBloom(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context,
            StatsContainer stats,
            Set<Element> currentAuras,
            double availableGaugeUnits) {
        if (action.getElement() != Element.HYDRO
                || currentAuras.contains(Element.DENDRO)
                || !sim.isQuickenActive()
                || sim.getQuickenState() == null) {
            return 0.0;
        }
        ReactionResult result = ReactionCalculator.calculate(
                Element.HYDRO,
                Element.DENDRO,
                stats.get(StatType.ELEMENTAL_MASTERY),
                90,
                getBloomReactionBonus(stats));
        result = convertToLunarIfEligible(result);
        sim.notifyReaction(result, attacker);
        return handleBloomReaction(
                characterId,
                Element.HYDRO,
                Element.DENDRO,
                result,
                context,
                availableGaugeUnits,
                false);
    }

    private boolean tryTriggerQuickenOnlyBurning(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context,
            StatsContainer stats,
            Set<Element> currentAuras) {
        if (action.getElement() != Element.PYRO
                || !currentAuras.isEmpty()
                || !sim.isQuickenActive()
                || sim.getQuickenState() == null) {
            return false;
        }
        ReactionResult result = ReactionCalculator.calculate(
                Element.PYRO,
                Element.DENDRO,
                stats.get(StatType.ELEMENTAL_MASTERY),
                90,
                getReactionBonus(Element.PYRO, Element.DENDRO, stats));
        sim.notifyReaction(result, attacker);
        handleStatefulReaction(
                attacker,
                characterId,
                Element.PYRO,
                Element.DENDRO,
                action,
                result,
                stats,
                context,
                action.getGaugeUnits());
        return true;
    }

    private double handleBloomReaction(
            CharacterId characterId,
            Element trigger,
            Element aura,
            ReactionResult result,
            ActionResolutionContext context,
            double availableGaugeUnits,
            boolean consumeEnemyAura) {
        double consumedGaugeUnits = 0.0;
        double modifier = getAuraConsumptionModifier(result, trigger, aura);
        if (consumeEnemyAura) {
            consumedGaugeUnits = sim.getEnemy().consumeAura(
                    aura, availableGaugeUnits, modifier, sim.getCurrentTime());
        }
        if (trigger == Element.HYDRO && sim.getQuickenState() != null) {
            double quickenUnits = sim.getQuickenState().remainingUnitsAt(
                    sim.getCurrentTime());
            double quickenAuraConsumption = Math.min(
                    quickenUnits, availableGaugeUnits * modifier);
            sim.consumeQuicken(quickenAuraConsumption);
            consumedGaugeUnits = Math.max(
                    consumedGaugeUnits, quickenAuraConsumption / modifier);
        }
        double resFactor = resolveImpactResistance(context, Element.DENDRO);
        double coreDamage = result.getTransformDamage() * resFactor;
        reactionEffectScheduler.createDendroCore(characterId, result.getTransformDamage());
        if (result.getKind() == ReactionResult.Kind.LUNAR_BLOOM) {
            sim.incrementVerdantDewCount();
            sim.incrementMoonridgeDewCount();
        }
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Core Damage: %,.0f",
                    trigger, aura, result.getName(), coreDamage));
        }
        return consumedGaugeUnits;
    }

    private void handleLunarCrystallize(
            Character attacker,
            CharacterId characterId,
            Element trigger,
            Element aura,
            ReactionResult result) {
        if (sim.getMoondriftCount() == 0) {
            sim.setMoondriftCount(3);
        }
        int triggerCount = sim.incrementLunarCrystallizeTriggerCount();
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> Lunar-Crystallize (Moondrifts: %d, Count: %d)",
                    trigger, aura, sim.getMoondriftCount(), triggerCount));
        }
        if (triggerCount % 3 != 0) {
            return;
        }
        double damage = reactionEffectScheduler.computeLunarCrystallizeHarmonyDamage();
        sim.recordDamage(characterId, damage);
        sim.getCombatLogSink().log(
                sim.getCurrentTime(), attacker.getName(), "Moondrift Harmony", damage,
                result.getName(), damage, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
        sim.notifyDerivedReaction(
                ReactionResult.lunar(
                        damage,
                        ReactionResult.LunarType.CRYSTALLIZE,
                        Element.HYDRO,
                        Element.GEO,
                        false,
                        true),
                attacker);
    }

    private void tryApplyCatalyzeAdditiveReaction(
            Character attacker,
            AttackAction action,
            ActionResolutionContext context) {
        if (!sim.isQuickenActive() || (action.getElement() != Element.ELECTRO && action.getElement() != Element.DENDRO)) {
            action.setAdditiveBaseDmgBonus(0.0);
            action.setAdditiveReactionName(null);
            return;
        }

        StatsContainer stats = getReactionStats(attacker, action, context);
        ReactionResult result = action.getElement() == Element.ELECTRO
                ? ReactionCalculator.calculateAggravate(
                        stats.get(StatType.ELEMENTAL_MASTERY), 90, stats.get(StatType.AGGRAVATE_DMG_BONUS))
                : ReactionCalculator.calculateSpread(
                        stats.get(StatType.ELEMENTAL_MASTERY), 90, stats.get(StatType.SPREAD_DMG_BONUS));
        action.setAdditiveBaseDmgBonus(result.getTransformDamage());
        action.setAdditiveReactionName(result.getName());
        sim.notifyReaction(result, attacker);
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] Quickened target -> %s Additive Base Damage: %,.0f",
                    result.getName(), result.getTransformDamage()));
        }
    }

    private void applySuperconductPhysicalResShred() {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Superconduct Physical RES Shred",
                BuffId.SUPERCONDUCT_PHYS_RES_SHRED,
                12.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.PHYS_RES_SHRED, 0.40)));
    }

    private double calculateStellarSwirlDamageBeforeResistance(
            double baseDamage,
            StatsContainer stats) {
        double em = stats.get(StatType.ELEMENTAL_MASTERY);
        double damageSection = 1.0
                + (6.0 * em) / (2000.0 + em)
                + stats.get(StatType.STELLAR_SWIRL_DMG_BONUS);
        double baseSection = 1.0 + stats.get(StatType.STELLAR_SWIRL_BASE_DMG_BONUS);
        double critRate = Math.min(
                1.0,
                stats.get(StatType.CRIT_RATE)
                        + stats.get(StatType.STELLAR_SWIRL_CRIT_RATE));
        double critDamage = stats.get(StatType.CRIT_DMG)
                + stats.get(StatType.STELLAR_SWIRL_CRIT_DMG)
                + stats.get(StatType.ANEMO_CRIT_DMG);
        double critMultiplier = 1.0 + critRate * critDamage;
        double defenseMultiplier = DamageCalculator.calculateDefMulti(
                90,
                sim.getEnemy().getLevel(),
                stats.get(StatType.ENEMY_DEF_REDUCTION),
                stats.get(StatType.DEF_IGNORE));
        double specialMultiplier = 1.0
                + stats.get(StatType.STELLAR_SWIRL_SPECIAL_DMG_BONUS);
        double finalMultiplier = 1.0 + stats.get(StatType.STELLAR_SWIRL_MULTIPLIER);
        return baseDamage
                * damageSection
                * baseSection
                * critMultiplier
                * defenseMultiplier
                * specialMultiplier
                * finalMultiplier;
    }

    /**
     * Resolves enemy resistance from the immutable team-buff list captured before
     * reaction callbacks for the current hit.
     *
     * @param context start-of-hit action context
     * @param element damage element whose resistance is being resolved
     * @return impact-time target resistance multiplier
     */
    private double resolveImpactResistance(ActionResolutionContext context, Element element) {
        return ResistanceCalculator.calculateMultiplier(
                sim.getEnemy(), context.resistanceBuffs, sim.getCurrentTime(), element);
    }

    /**
     * Returns the element used for transformative-reaction RES lookup.
     *
     * @param result reaction result
     * @return Electro for Electro-Charged, Pyro otherwise (current scope)
     */
    private Element getTransformativeReactionElement(ReactionResult result) {
        if (result.getDamageElement() != null) {
            return result.getDamageElement();
        }
        return Element.PYRO;
    }

    /** Returns whether the synthetic Burning Aura is eligible for this trigger. */
    private boolean isBurningAuraReactiveTrigger(Element trigger) {
        return trigger == Element.HYDRO
                || trigger == Element.CRYO
                || trigger == Element.ELECTRO
                || trigger == Element.GEO
                || trigger == Element.DENDRO;
    }

    /**
     * Consumes ordinary and synthetic Auras of one element in parallel.
     *
     * <p>Burning Aura is Pyro but is not stored in {@link model.entity.Enemy}'s
     * ordinary Aura map. Both payloads lose the same target amount and the
     * trigger spends only the larger proportional consumption.</p>
     */
    private double consumeReactiveAura(
            Element aura, double sourceGaugeUnits, double modifier) {
        double ordinaryConsumption = sim.getEnemy().consumeAura(
                aura, sourceGaugeUnits, modifier, sim.getCurrentTime());
        double burningConsumption = aura == Element.PYRO
                ? sim.consumeBurningAura(sourceGaugeUnits, modifier)
                : 0.0;
        return Math.max(ordinaryConsumption, burningConsumption);
    }

    /**
     * Returns the target-Aura consumption modifier for a reaction.
     *
     * <p>Anemo and Geo use 0.5 for Swirl and Crystallize. Bloom uses its
     * directional 0.5/2.0 ratio. Other families consume one Aura unit per
     * source unit.</p>
     *
     * @param result typed reaction outcome
     * @param trigger trigger element
     * @param aura existing aura element
     * @return target-Aura units consumed per source gauge unit
     */
    private double getAuraConsumptionModifier(
            ReactionResult result,
            Element trigger,
            Element aura) {
        switch (result.getKind()) {
            case SWIRL:
            case STELLAR_SWIRL:
            case CRYSTALLIZE:
            case LUNAR_CRYSTALLIZE:
                return 0.5;
            case BLOOM:
            case LUNAR_BLOOM:
                if (trigger == Element.HYDRO && aura == Element.DENDRO) {
                    return 0.5;
                }
                if (trigger == Element.DENDRO && aura == Element.HYDRO) {
                    return 2.0;
                }
                return 1.0;
            default:
                return 1.0;
        }
    }

    /**
     * Applies the trigger's source gauge as an aura when the element can persist.
     *
     * @param trigger    trigger element
     * @param gaugeUnits gauge units to apply
     */
    private void applyTriggerAuraIfPersistent(Element trigger, double gaugeUnits) {
        boolean applied = sim.getEnemy().applyAura(trigger, gaugeUnits, sim.getCurrentTime());
        if (applied && sim.isLoggingEnabled()) {
            System.out.println(String.format("   [Aura] Applied %s source (%.1f U)", trigger, gaugeUnits));
        }
    }

    /**
     * Computes final damage via {@link DamageCalculator}, records it, and triggers all
     * weapon/artifact on-damage hooks plus optional combat logging.
     *
     * @param attacker      acting character
     * @param charName      acting character display name
     * @param action        action being resolved
     * @param reactionMulti amplifying-reaction multiplier
     * @param context       resolved per-action context
     */
    private void finalizeActionDamage(
            Character attacker,
            String charName,
            AttackAction action,
            double reactionMulti,
            ActionResolutionContext context) {
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("[T=%.1f] %s uses %s",
                    sim.getCurrentTime(), charName, action.getName()));
        }

        double damage = DamageCalculator.calculateDamage(
                attacker, sim.getEnemy(), action, context.applicableBuffs, context.resolvedStats,
                sim.getCurrentTime(), reactionMulti, sim);

        if (action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.CHARGE) {
            applyExpectedNormalAttackEnergy(attacker);
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   -> Damage: %,.0f", damage));
        }
        sim.recordDamage(attacker.getCharacterId(), damage);
        sim.captureResolvedActionDamage(attacker.getCharacterId(), damage);
        sim.notifyDamage(attacker, action, damage);

        if (sim.isLoggingEnabled()) {
            String reactionLabel = action.getAdditiveReactionName() != null
                    ? action.getAdditiveReactionName()
                    : (reactionMulti > 1.0 ? "Amp x" + String.format("%.2f", reactionMulti) : "None");
            double reactionValue = action.getAdditiveReactionName() != null
                    ? action.getAdditiveBaseDmgBonus()
                    : 0.0;
            sim.getCombatLogSink().log(
                    sim.getCurrentTime(), charName, action.getName(), damage,
                    reactionLabel, reactionValue, sim.getEnemy().getAuraMap(sim.getCurrentTime()), action.getDebugFormula());
        }
    }

    /**
     * Adds the weapon's expected normal-attack energy generation to the attacker.
     *
     * @param attacker acting character
     */
    private void applyExpectedNormalAttackEnergy(Character attacker) {
        if (attacker.getWeapon() == null) {
            return;
        }
        double naEnergy = attacker.getWeapon().getExpectedNAEnergyPerHit();
        if (naEnergy > 0) {
            attacker.receiveFlatEnergy(naEnergy);
        }
    }

    /**
     * Immutable per-action context capturing the snapshot of applicable buffs and
     * resolved stats used during resolution.
     */
    private static final class ActionResolutionContext {
        /** Buffs that were applicable to the action when resolution started. */
        private final List<Buff> applicableBuffs;
        /** Enemy resistance effects that existed when resolution started. */
        private final List<Buff> resistanceBuffs;
        /** Resolved stats container, or {@code null} when the action uses snapshot semantics. */
        private final StatsContainer resolvedStats;

        /**
         * Creates a resolution context.
         *
         * @param applicableBuffs snapshot of applicable buffs
         * @param resistanceBuffs snapshot of enemy resistance effects
         * @param resolvedStats   resolved stats, or {@code null} for snapshot actions
         */
        private ActionResolutionContext(
                List<Buff> applicableBuffs,
                List<Buff> resistanceBuffs,
                StatsContainer resolvedStats) {
            this.applicableBuffs = applicableBuffs;
            this.resistanceBuffs = resistanceBuffs;
            this.resolvedStats = resolvedStats;
        }
    }
}
