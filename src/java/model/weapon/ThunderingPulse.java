package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Thundering Pulse bow with Normal-hit, Skill, and live-Energy emblem stacks.
 */
public class ThunderingPulse extends Weapon
        implements ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect {
    private static final double NORMAL_STACK_DURATION = 5.0;
    private static final double SKILL_STACK_DURATION = 10.0;

    private final int refinement;
    private final double emblemTier;
    private Character owner;
    private CombatSimulator simulator;
    private double normalStackUntil = Double.NEGATIVE_INFINITY;
    private double skillStackUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Thundering Pulse at refinement rank five. */
    public ThunderingPulse() {
        this(5);
    }

    /**
     * Constructs Thundering Pulse at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ThunderingPulse(int refinement) {
        super("Thundering Pulse", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.emblemTier = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
        getStats().set(StatType.ATK_PERCENT, 0.15 + 0.05 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner whose live Energy determines the third emblem stack. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Thundering Pulse is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Opens the ten-second emblem stack before active-owner Skill resolution. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && request.getKey() == CharacterActionKey.SKILL) {
            skillStackUntil = sim.getCurrentTime() + SKILL_STACK_DURATION;
        }
    }

    /** Refreshes the five-second stack after positive active-owner Normal damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && action.getActionType() == ActionType.NORMAL
                && action.getDamagePercent() > 0.0) {
            normalStackUntil = currentTime + NORMAL_STACK_DURATION;
        }
    }

    /** Applies the Normal damage tier for the three independent stack sources. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null) {
            return;
        }
        int stackCount = 0;
        if (currentTime < normalStackUntil) {
            stackCount++;
        }
        if (currentTime < skillStackUntil) {
            stackCount++;
        }
        if (owner.getCurrentEnergy() < owner.getMaxEnergy()) {
            stackCount++;
        }
        if (stackCount == 0) {
            return;
        }
        double tierMultiplier = stackCount == 3 ? 10.0 / 3.0 : stackCount;
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, emblemTier * tierMultiplier);
    }
}
