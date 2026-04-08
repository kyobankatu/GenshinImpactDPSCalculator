package simulation.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import mechanics.buff.Buff;
import mechanics.formula.DamageCalculator;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.ActionType;
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

    /**
     * Creates a resolver bound to the given simulator instance.
     *
     * @param sim active combat simulator whose runtime state will be read and updated
     */
    public CombatActionResolver(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Resolves all damage and elemental effects of an {@link AttackAction} without
     * advancing simulation time.
     *
     * <p>This is used by the simulator for immediate action execution and by periodic
     * timer events whose timing is managed elsewhere.
     *
     * @param charName the name of the character performing the action
     * @param action   the {@link AttackAction} to resolve
     * @throws RuntimeException if no character with {@code charName} exists in the party
     */
    public void resolveWithoutTimeAdvance(String charName, AttackAction action) {
        Character c = sim.getCharacter(charName);
        if (c == null) {
            throw new RuntimeException("Character not found: " + charName);
        }

        normalizeIcd(action);

        boolean applied = sim.getIcdManager().checkApplication(
                charName, action.getICDTag(), action.getICDType(), sim.getCurrentTime());

        notifyLunarAction(action, c);

        double reactionMulti = 1.0;
        if (applied && action.getGaugeUnits() > 0) {
            reactionMulti = resolveGaugeAndReactions(c, charName, action);
        } else if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   [ICD] Applied blocked (%s)", action.getICDTag()));
        }

        finalizeActionDamage(c, charName, action, reactionMulti);
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
        String reactionType = "Lunar-" + action.getLunarReactionType();
        sim.notifyReaction(ReactionResult.transform(0.0, reactionType), character);
    }

    private double resolveGaugeAndReactions(Character attacker, String charName, AttackAction action) {
        Element trigger = action.getElement();
        Set<Element> currentAuras = sim.getEnemy().getActiveAuras();
        boolean reactionTriggered = false;
        double reactionMulti = 1.0;

        StatsContainer stats = getReactionStats(attacker, action);
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
                handleTransformativeReaction(attacker, charName, action, trigger, aura, result, stats);
            }
        }

        if (!reactionTriggered) {
            applyTriggerAuraIfPersistent(trigger, action.getGaugeUnits());
        }

        return reactionMulti;
    }

    private StatsContainer getReactionStats(Character attacker, AttackAction action) {
        if (action.isUseSnapshot()) {
            return attacker.getSnapshot();
        }

        StatsContainer stats = attacker.getEffectiveStats(sim.getCurrentTime());
        List<Buff> buffs = sim.getApplicableBuffs(attacker);
        for (Buff buff : buffs) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
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
            String charName,
            AttackAction action,
            Element trigger,
            Element aura,
            ReactionResult result,
            StatsContainer stats) {
        Element reactionElement = getTransformativeReactionElement(result);

        if (!result.getName().equals("Electro-Charged")) {
            sim.getEnemy().reduceAura(aura, action.getGaugeUnits());
        }

        double res = sim.getEnemy().getRes(reactionElement.getBonusStatType());
        double resFactor = DamageCalculator.calculateResMulti(res, getResShred(stats, reactionElement));
        double reactBonus = result.getName().equals("Electro-Charged")
                ? stats.get(StatType.ELECTRO_CHARGED_DMG_BONUS)
                : 0.0;
        double transDmg = result.getTransformDamage() * (1.0 + reactBonus) * resFactor;

        boolean isLunar = result.getName().equals("Electro-Charged")
                && sim.getMoonsign() != CombatSimulator.Moonsign.NONE;
        String reactionLabel = isLunar ? "Lunar-Charged" : result.getName();
        double triggerDmg = isLunar ? computeInitialLunarChargedDamage() : transDmg;

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Reaction] %s on %s -> %s Damage: %,.0f",
                    trigger, aura, reactionLabel, triggerDmg));
        }

        sim.recordDamage(charName, triggerDmg);
        sim.getCombatLogSink().log(
                sim.getCurrentTime(), charName, reactionLabel, triggerDmg,
                reactionLabel, triggerDmg, sim.getEnemy().getAuraMap());

        if (result.getName().equals("Electro-Charged")) {
            handleElectroChargedState(trigger, action.getGaugeUnits(), transDmg, isLunar);
        } else {
            sim.getEnemy().setAura(aura, 0);
        }
    }

    private Element getTransformativeReactionElement(ReactionResult result) {
        if (result.getName().equals("Electro-Charged")) {
            return Element.ELECTRO;
        }
        return Element.PYRO;
    }

    private double getResShred(StatsContainer stats, Element reactionElement) {
        double resShred = stats.get(StatType.RES_SHRED);
        switch (reactionElement) {
            case PYRO:
                return resShred + stats.get(StatType.PYRO_RES_SHRED);
            case HYDRO:
                return resShred + stats.get(StatType.HYDRO_RES_SHRED);
            case CRYO:
                return resShred + stats.get(StatType.CRYO_RES_SHRED);
            case ELECTRO:
                return resShred + stats.get(StatType.ELECTRO_RES_SHRED);
            case ANEMO:
                return resShred + stats.get(StatType.ANEMO_RES_SHRED);
            case GEO:
                return resShred + stats.get(StatType.GEO_RES_SHRED);
            case DENDRO:
                return resShred + stats.get(StatType.DENDRO_RES_SHRED);
            case PHYSICAL:
                return resShred + stats.get(StatType.PHYS_RES_SHRED);
            default:
                return resShred;
        }
    }

    private double computeInitialLunarChargedDamage() {
        List<Double> potentialDamages = new ArrayList<>();
        for (Character member : sim.getPartyMembers()) {
            StatsContainer stats = member.getEffectiveStats(sim.getCurrentTime());
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
            double resVal = sim.getEnemy().getRes(StatType.ELECTRO_DMG_BONUS);
            double resMult;
            if (resVal < 0) {
                resMult = 1.0 - (resVal / 2.0);
            } else if (resVal < 0.75) {
                resMult = 1.0 - resVal;
            } else {
                resMult = 1.0 / (1.0 + 4.0 * resVal);
            }

            double damage = 1.8 * 1446.85 * (1.0 + baseBonus) * (1.0 + uniqueBonus)
                    * (1.0 + emBonus) * critMult * resMult * columbinaMult;
            potentialDamages.add(damage);
        }

        potentialDamages.sort(Collections.reverseOrder());
        double[] weights = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };
        double total = 0.0;
        for (int i = 0; i < potentialDamages.size() && i < 4; i++) {
            total += potentialDamages.get(i) * weights[i];
        }
        return total;
    }

    private void handleElectroChargedState(Element trigger, double gaugeUnits, double transDmg, boolean isLunar) {
        if (isLunar) {
            sim.setThundercloudEndTime(sim.getCurrentTime() + 6.0);
        }

        if (!sim.isECTimerRunning()) {
            sim.setECTimerRunning(true);
            sim.registerEvent(createElectroChargedTickEvent(transDmg, isLunar));
        }

        sim.getEnemy().setAura(trigger, gaugeUnits);
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
                    finalDamage = computeTickLunarChargedDamage(simContext);
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
                            ReactionResult.transform(finalDamage, "Thundercloud-Strike"),
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

    private double computeTickLunarChargedDamage(CombatSimulator simContext) {
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

            double resVal = simContext.getEnemy().getRes(StatType.ELECTRO_DMG_BONUS);
            double resMult;
            if (resVal < 0) {
                resMult = 1.0 - (resVal / 2.0);
            } else if (resVal < 0.75) {
                resMult = 1.0 - resVal;
            } else {
                resMult = 1.0 / (1.0 + 4.0 * resVal);
            }

            double damage = 1.8 * 1446.85 * (1.0 + baseBonus) * (1.0 + uniqueBonus)
                    * (1.0 + emBonus) * critMult * resMult * columbinaMult;
            potentialDamages.add(damage);
        }

        potentialDamages.sort(Collections.reverseOrder());
        double[] weights = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };
        double total = 0.0;
        for (int i = 0; i < potentialDamages.size() && i < 4; i++) {
            total += potentialDamages.get(i) * weights[i];
        }
        return total;
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

    private void finalizeActionDamage(Character attacker, String charName, AttackAction action, double reactionMulti) {
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("[T=%.1f] %s uses %s",
                    sim.getCurrentTime(), charName, action.getName()));
        }

        List<Buff> activeBuffs = sim.getApplicableBuffs(attacker);
        double damage = DamageCalculator.calculateDamage(
                attacker, sim.getEnemy(), action, activeBuffs, sim.getCurrentTime(), reactionMulti, sim);

        if (attacker.getWeapon() != null) {
            attacker.getWeapon().onDamage(attacker, action, sim.getCurrentTime(), sim);
        }

        if (action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.CHARGE) {
            applyExpectedNormalAttackEnergy(attacker);
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("   -> Damage: %,.0f", damage));
        }
        sim.recordDamage(charName, damage);

        if (attacker.getArtifacts() != null) {
            for (ArtifactSet artifact : attacker.getArtifacts()) {
                if (artifact != null) {
                    artifact.onDamage(sim, action, damage, attacker);
                }
            }
        }

        sim.getCombatLogSink().log(
                sim.getCurrentTime(), charName, action.getName(), damage,
                (reactionMulti > 1.0 ? "Amp x" + String.format("%.2f", reactionMulti) : "None"),
                0.0, sim.getEnemy().getAuraMap(), action.getDebugFormula());
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
}
