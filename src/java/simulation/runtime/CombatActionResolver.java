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

    /**
     * Creates a resolver bound to the given simulator instance.
     *
     * @param sim active combat simulator whose runtime state will be read and updated
     */
    public CombatActionResolver(CombatSimulator sim) {
        this.sim = sim;
        this.reactionEffectScheduler = new ReactionEffectScheduler(sim);
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

            double reactionMulti = 1.0;
            sim.getEnemy().updateAuras(sim.getCurrentTime());
            if (applied) {
                tryTriggerShatter(c, characterId, action, context);
                tryTriggerDendroCoreReaction(c, characterId, action, context);
                tryApplyCatalyzeAdditiveReaction(c, action, context);
            }
            if (applied && action.getGaugeUnits() > 0) {
                reactionMulti = resolveGaugeAndReactions(c, characterId, action, context);
            } else if (!applied && action.getGaugeUnits() > 0 && sim.isLoggingEnabled()) {
                System.out.println(String.format("   [ICD] Applied blocked (%s)", action.getICDTag()));
            }

            finalizeActionDamage(c, charName, action, reactionMulti, context);
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
                attacker, sim.getEnemy(), action, applicableBuffBuffer, sim.getCurrentTime());
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
                sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED), character);
                break;
            case BLOOM:
                sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM), character);
                break;
            case CRYSTALLIZE:
                sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.CRYSTALLIZE), character);
                break;
        }
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
        boolean reactionTriggered = false;
        double reactionMulti = 1.0;

        StatsContainer stats = getReactionStats(attacker, action, context);
        double em = stats.get(StatType.ELEMENTAL_MASTERY);

        for (Element aura : currentAuras) {
            ReactionResult result = ReactionCalculator.calculate(
                    trigger, aura, em, 90, getReactionBonus(trigger, aura, stats));
            if (result.getType() == ReactionResult.Type.NONE) {
                continue;
            }
            result = convertToLunarIfEligible(result);

            reactionTriggered = true;
            sim.notifyReaction(result, attacker);

            if (result.getType() == ReactionResult.Type.AMP) {
                reactionMulti = handleAmplifyingReaction(trigger, aura, action, result);
            } else if (result.isStateful()) {
                handleStatefulReaction(attacker, characterId, trigger, aura, action, result, stats, context);
            } else if (result.getType() == ReactionResult.Type.TRANSFORMATIVE) {
                handleTransformativeReaction(attacker, characterId, action, trigger, aura, result, stats, context);
            }
        }

        if (!reactionTriggered) {
            applyTriggerAuraIfPersistent(trigger, action.getGaugeUnits());
        }

        return reactionMulti;
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

    private void tryTriggerShatter(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context) {
        boolean canShatter = sim.getEnemy().isFrozen()
                && (action.getElement() == Element.GEO || action.isShatterTrigger());
        if (!canShatter) {
            return;
        }
        StatsContainer stats = getReactionStats(attacker, action, context);
        ReactionResult result = ReactionCalculator.calculateShatter(
                stats.get(StatType.ELEMENTAL_MASTERY), 90, 0.0);
        sim.notifyReaction(result, attacker);
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
        sim.getEnemy().clearFreezeAura();
    }

    private double getReactionBonus(Element trigger, Element aura, StatsContainer stats) {
        if (trigger == Element.ANEMO
                && (aura == Element.PYRO || aura == Element.HYDRO || aura == Element.ELECTRO || aura == Element.CRYO)) {
            return stats.get(StatType.SWIRL_DMG_BONUS);
        }
        if ((trigger == Element.HYDRO && aura == Element.DENDRO)
                || (trigger == Element.DENDRO && aura == Element.HYDRO)) {
            return stats.get(StatType.BLOOM_DMG_BONUS);
        }
        return 0.0;
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
     * @param action  source action
     * @param result  resolved reaction result
     * @return amplifying damage multiplier
     */
    private double handleAmplifyingReaction(
            Element trigger,
            Element aura,
            AttackAction action,
            ReactionResult result) {
        double reactionMulti = result.getAmpMultiplier();
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Multi %.2f",
                    trigger, aura, result.getName(), reactionMulti));
        }

        double consumption = action.getGaugeUnits();
        boolean isReverse = (trigger == Element.PYRO && aura == Element.HYDRO)
                || (trigger == Element.CRYO && aura == Element.PYRO);
        double modifier = isReverse ? 0.5 : 2.0;
        sim.getEnemy().reduceAura(aura, consumption * modifier, sim.getCurrentTime());
        return reactionMulti;
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
     */
    private void handleTransformativeReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            Element trigger,
            Element aura,
            ReactionResult result,
            StatsContainer stats,
            ActionResolutionContext context) {
        Element reactionElement = getTransformativeReactionElement(result);

        if (!result.isElectroCharged()) {
            sim.getEnemy().reduceAura(
                    aura, getAuraConsumption(action, result, trigger, aura), sim.getCurrentTime());
        }

        double resFactor = resolveImpactResistance(context, reactionElement);
        double reactBonus = result.isElectroCharged()
                ? stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS)
                : 0.0;
        double preResistanceDamage = result.getTransformDamage() * (1.0 + reactBonus);
        double transDmg = preResistanceDamage * resFactor;

        boolean isLunar = result.getKind() == ReactionResult.Kind.LUNAR_CHARGED;
        String reactionLabel = isLunar ? "Lunar-Charged" : result.getName();
        double triggerDmg = isLunar ? reactionEffectScheduler.computeInitialLunarChargedDamage() : transDmg;

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Damage: %,.0f",
                    trigger, aura, reactionLabel, triggerDmg));
        }

        sim.recordDamage(characterId, triggerDmg);
        if (sim.isLoggingEnabled()) {
            sim.getCombatLogSink().log(
                    sim.getCurrentTime(), attacker.getName(), reactionLabel, triggerDmg,
                    reactionLabel, triggerDmg, sim.getEnemy().getAuraMap(sim.getCurrentTime()));
        }

        if (result.isElectroCharged()) {
            reactionEffectScheduler.scheduleElectroCharged(
                    trigger, action.getGaugeUnits(), preResistanceDamage, isLunar);
        } else {
            if (result.getKind() == ReactionResult.Kind.SUPERCONDUCT) {
                applySuperconductPhysicalResShred();
            }
        }
    }

    private void handleStatefulReaction(Character attacker, CharacterId characterId, Element trigger, Element aura,
            AttackAction action, ReactionResult result, StatsContainer stats, ActionResolutionContext context) {
        if (result.getKind() == ReactionResult.Kind.FROZEN) {
            double freezeUnits = Math.min(action.getGaugeUnits(),
                    Math.max(0.5, sim.getEnemy().getAuraUnits(aura, sim.getCurrentTime())));
            sim.getEnemy().setFreezeAura(freezeUnits);
            sim.getEnemy().reduceAura(aura, action.getGaugeUnits(), sim.getCurrentTime());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Frozen (%.1f U)",
                        trigger, aura, freezeUnits));
            }
        } else if (result.getKind() == ReactionResult.Kind.CRYSTALLIZE) {
            sim.getEnemy().reduceAura(
                    aura, getAuraConsumption(action, result, trigger, aura), sim.getCurrentTime());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> %s",
                        trigger, aura, result.getName()));
            }
        } else if (result.getKind() == ReactionResult.Kind.LUNAR_CRYSTALLIZE) {
            sim.getEnemy().reduceAura(
                    aura, getAuraConsumption(action, result, trigger, aura), sim.getCurrentTime());
            handleLunarCrystallize(attacker, characterId, trigger, aura, result);
        } else if (result.getKind() == ReactionResult.Kind.BURNING) {
            double resFactor = resolveImpactResistance(context, Element.PYRO);
            double tickDamage = result.getTransformDamage() * resFactor;
            sim.getEnemy().reduceAura(aura, action.getGaugeUnits(), sim.getCurrentTime());
            reactionEffectScheduler.scheduleBurning(characterId, result.getTransformDamage());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Burning Tick Damage: %,.0f",
                        trigger, aura, tickDamage));
            }
        } else if (result.getKind() == ReactionResult.Kind.BLOOM
                || result.getKind() == ReactionResult.Kind.LUNAR_BLOOM) {
            double resFactor = resolveImpactResistance(context, Element.DENDRO);
            double coreDamage = result.getTransformDamage() * resFactor;
            sim.getEnemy().reduceAura(
                    aura, getAuraConsumption(action, result, trigger, aura), sim.getCurrentTime());
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
        } else if (result.getKind() == ReactionResult.Kind.QUICKEN) {
            double quickenGauge = Math.min(
                    sim.getEnemy().getAuraUnits(aura, sim.getCurrentTime()), action.getGaugeUnits());
            double duration = Math.max(0.0, quickenGauge) * 5.0 + 6.0;
            sim.setQuickenEndTime(sim.getCurrentTime() + duration);
            sim.getEnemy().reduceAura(aura, action.getGaugeUnits(), sim.getCurrentTime());
            if (sim.isLoggingEnabled()) {
                System.out.println(String.format(
                        "   [Reaction] %s on %s -> Quicken (%.1fs)",
                        trigger, aura, duration));
            }
        }
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
        sim.notifyReaction(
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

    /**
     * Returns the source-gauge amount consumed from an existing aura.
     *
     * <p>Anemo and Geo triggers use the sourced 0.5 unit modifier for Swirl and
     * both Crystallize variants. Bloom uses its directional ratio: Hydro on
     * Dendro consumes 0.5 times the source gauge, while Dendro on Hydro consumes
     * 2.0 times the source gauge. Other reaction families retain their existing
     * source-gauge consumption policy.
     *
     * @param action source action carrying pre-tax gauge units
     * @param result typed reaction outcome
     * @param trigger trigger element
     * @param aura existing aura element
     * @return aura gauge units to subtract
     */
    private double getAuraConsumption(
            AttackAction action,
            ReactionResult result,
            Element trigger,
            Element aura) {
        switch (result.getKind()) {
            case SWIRL:
            case CRYSTALLIZE:
            case LUNAR_CRYSTALLIZE:
                return action.getGaugeUnits() * 0.5;
            case BLOOM:
            case LUNAR_BLOOM:
                if (trigger == Element.HYDRO && aura == Element.DENDRO) {
                    return action.getGaugeUnits() * 0.5;
                }
                if (trigger == Element.DENDRO && aura == Element.HYDRO) {
                    return action.getGaugeUnits() * 2.0;
                }
                return action.getGaugeUnits();
            default:
                return action.getGaugeUnits();
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
