package simulation.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mechanics.buff.Buff;
import mechanics.formula.DamageCalculator;
import mechanics.formula.ResistanceCalculator;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionEffectScheduler;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.DamageTriggeredWeaponEffect;
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
    private final CombatSimulator sim;
    private final ReactionEffectScheduler reactionEffectScheduler;
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
        String charName = c.getName();
        ActionResolutionContext context = createContext(c, action);

        normalizeIcd(action);

        boolean applied = sim.getIcdManager().checkApplication(
                characterId.name(), action.getICDTag(), action.getICDType(), sim.getCurrentTime());

        notifyLunarAction(action, c);

        double reactionMulti = 1.0;
        if (applied && action.getGaugeUnits() > 0) {
            reactionMulti = resolveGaugeAndReactions(c, characterId, action, context);
        } else if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [ICD] Applied blocked (%s)", action.getICDTag()));
        }

        finalizeActionDamage(c, charName, action, reactionMulti, context);
    }

    private ActionResolutionContext createContext(Character attacker, AttackAction action) {
        applicableBuffBuffer.clear();
        applicableBuffBuffer.addAll(sim.getApplicableBuffs(attacker));
        StatsContainer resolvedStats = action.isUseSnapshot()
                ? null
                : DamageCalculator.resolveStats(attacker, action, applicableBuffBuffer, sim.getCurrentTime());
        return new ActionResolutionContext(new ArrayList<>(applicableBuffBuffer), resolvedStats);
    }

    private void normalizeIcd(AttackAction action) {
        if (action.getICDTag() == null) {
            action.setICD(action.getICDType(), ICDTag.None, action.getGaugeUnits());
        }
        if (action.getICDType() == null) {
            action.setICD(ICDType.Standard, action.getICDTag(), action.getGaugeUnits());
        }
    }

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

    private double resolveGaugeAndReactions(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            ActionResolutionContext context) {
        Element trigger = action.getElement();
        Set<Element> currentAuras = sim.getEnemy().getActiveAuras();
        boolean reactionTriggered = false;
        double reactionMulti = 1.0;

        StatsContainer stats = getReactionStats(attacker, action, context);
        double em = stats.get(StatType.ELEMENTAL_MASTERY);
        double swirlBonus = stats.get(StatType.SWIRL_DMG_BONUS);

        for (Element aura : currentAuras) {
            ReactionResult result = ReactionCalculator.calculate(trigger, aura, em, 90, swirlBonus);
            if (result.getType() == ReactionResult.Type.NONE) {
                continue;
            }

            reactionTriggered = true;
            sim.notifyReaction(result, attacker);

            if (result.getType() == ReactionResult.Type.AMP) {
                reactionMulti = handleAmplifyingReaction(trigger, aura, action, result);
            } else if (result.getType() == ReactionResult.Type.TRANSFORMATIVE) {
                handleTransformativeReaction(attacker, characterId, action, trigger, aura, result, stats);
            }
        }

        if (!reactionTriggered) {
            applyTriggerAuraIfPersistent(trigger, action.getGaugeUnits());
        }

        return reactionMulti;
    }

    private StatsContainer getReactionStats(Character attacker, AttackAction action, ActionResolutionContext context) {
        if (action.isUseSnapshot()) {
            return attacker.getSnapshot();
        }
        return context.resolvedStats;
    }

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
        sim.getEnemy().reduceAura(aura, consumption * modifier);
        return reactionMulti;
    }

    private void handleTransformativeReaction(
            Character attacker,
            CharacterId characterId,
            AttackAction action,
            Element trigger,
            Element aura,
            ReactionResult result,
            StatsContainer stats) {
        Element reactionElement = getTransformativeReactionElement(result);

        if (!result.isElectroCharged()) {
            sim.getEnemy().reduceAura(aura, action.getGaugeUnits());
        }

        double res = sim.getEnemy().getRes(reactionElement.getBonusStatType());
        double resFactor = DamageCalculator.calculateResMulti(
                res, ResistanceCalculator.getTotalResShred(stats, reactionElement));
        double reactBonus = result.isElectroCharged()
                ? stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS)
                : 0.0;
        double transDmg = result.getTransformDamage() * (1.0 + reactBonus) * resFactor;

        boolean isLunar = result.isElectroCharged()
                && sim.getMoonsign() != CombatSimulator.Moonsign.NONE;
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
                    reactionLabel, triggerDmg, sim.getEnemy().getAuraMap());
        }

        if (result.isElectroCharged()) {
            reactionEffectScheduler.scheduleElectroCharged(trigger, action.getGaugeUnits(), transDmg, isLunar);
        } else {
            sim.getEnemy().setAura(aura, 0);
        }
    }

    private Element getTransformativeReactionElement(ReactionResult result) {
        if (result.isElectroCharged()) {
            return Element.ELECTRO;
        }
        return Element.PYRO;
    }

    private void applyTriggerAuraIfPersistent(Element trigger, double gaugeUnits) {
        if (trigger == Element.PHYSICAL || trigger == Element.ANEMO || trigger == Element.GEO) {
            return;
        }
        sim.getEnemy().setAura(trigger, gaugeUnits);
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [Aura] Applied %s (%.1f U)", trigger, gaugeUnits));
        }
    }

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

        if (attacker.getWeapon() instanceof DamageTriggeredWeaponEffect) {
            ((DamageTriggeredWeaponEffect) attacker.getWeapon()).onDamage(
                    attacker, action, sim.getCurrentTime(), sim);
        }

        if (action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.CHARGE) {
            applyExpectedNormalAttackEnergy(attacker);
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   -> Damage: %,.0f", damage));
        }
        sim.recordDamage(attacker.getCharacterId(), damage);

        if (attacker.getArtifacts() != null) {
            for (ArtifactSet artifact : attacker.getArtifacts()) {
                if (artifact instanceof DamageTriggeredArtifactEffect) {
                    ((DamageTriggeredArtifactEffect) artifact).onDamage(sim, action, damage, attacker);
                }
            }
        }

        if (sim.isLoggingEnabled()) {
            sim.getCombatLogSink().log(
                    sim.getCurrentTime(), charName, action.getName(), damage,
                    (reactionMulti > 1.0 ? "Amp x" + String.format("%.2f", reactionMulti) : "None"),
                    0.0, sim.getEnemy().getAuraMap(), action.getDebugFormula());
        }
    }

    private void applyExpectedNormalAttackEnergy(Character attacker) {
        if (attacker.getWeapon() == null) {
            return;
        }
        double naEnergy = attacker.getWeapon().getExpectedNAEnergyPerHit();
        if (naEnergy > 0) {
            attacker.receiveFlatEnergy(naEnergy);
        }
    }

    private static final class ActionResolutionContext {
        private final List<Buff> applicableBuffs;
        private final StatsContainer resolvedStats;

        private ActionResolutionContext(List<Buff> applicableBuffs, StatsContainer resolvedStats) {
            this.applicableBuffs = applicableBuffs;
            this.resolvedStats = resolvedStats;
        }
    }
}
