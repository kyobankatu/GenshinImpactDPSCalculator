package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Shared active-owner ATK/EM window triggered by reactions and optional Skill hits.
 */
public abstract class SkillHitOrReactionWindowWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect,
        CombatSimulator.ReactionListener,
        DamageTriggeredWeaponEffect {
    private final int refinement;
    private final boolean skillHitTriggers;
    private final double atkBonus;
    private final double emBonus;
    private final double duration;

    private Character owner;
    private boolean initialized;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one reaction and optional Skill-hit stat window.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param skillHitTriggers whether positive Skill damage also activates
     * @param duration active window duration in seconds
     */
    protected SkillHitOrReactionWindowWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            boolean skillHitTriggers,
            double duration) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Hybrid reaction-window refinement must be between 1 and 5");
        }
        if (duration <= 0.0) {
            throw new IllegalArgumentException(
                    "Hybrid reaction-window duration must be positive");
        }
        this.refinement = refinement;
        this.skillHitTriggers = skillHitTriggers;
        this.atkBonus = 0.09 + 0.03 * refinement;
        this.emBonus = 36.0 + 12.0 * refinement;
        this.duration = duration;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Binds the owner and registers attributed reaction handling once.
     *
     * @param equippedOwner owner equipped with this weapon
     * @param sim simulator containing the owner
     */
    @Override
    public final void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (initialized) {
            return;
        }
        owner = equippedOwner;
        sim.addReactionListener(this);
        initialized = true;
    }

    /**
     * Applies the active unequal ATK and EM bonuses.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ATK_PERCENT, atkBonus);
            stats.add(StatType.ELEMENTAL_MASTERY, emBonus);
        }
    }

    /**
     * Refreshes after an attributed active-owner reaction.
     *
     * @param result reaction result
     * @param source triggering character
     * @param time reaction time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (result.getKind() != ReactionResult.Kind.NONE
                && source == owner
                && sim.getActiveCharacter() == owner) {
            activeUntil = time + duration;
        }
    }

    /**
     * Optionally refreshes after positive active-owner Skill damage resolves.
     *
     * @param user weapon owner who dealt the hit
     * @param action resolved damage action
     * @param currentTime damage time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (skillHitTriggers
                && user == owner
                && sim.getActiveCharacter() == owner
                && action.getDamagePercent() > 0.0
                && (action.getActionType() == ActionType.SKILL
                        || action.isCountsAsSkillDmg())) {
            activeUntil = currentTime + duration;
        }
    }
}
