package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Iron Sting sword with active-owner elemental-damage stacks.
 */
public class IronSting extends Weapon
        implements SimulatorInitializedWeaponEffect, DamageTriggeredWeaponEffect {
    private static final int MAX_STACKS = 2;
    private static final double STACK_COOLDOWN = 1.0;
    private static final double STACK_DURATION = 6.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double expiration = Double.NEGATIVE_INFINITY;
    private double nextStackTime = Double.NEGATIVE_INFINITY;

    /** Constructs Iron Sting at refinement rank five. */
    public IronSting() {
        this(5);
    }

    /**
     * Constructs Iron Sting at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public IronSting(int refinement) {
        super("Iron Sting", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.damageBonusPerStack = 0.045 + 0.015 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 165.0);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and simulator used by the active-field trigger gate. */
    @Override
    public void initializeForSimulator(Character owner, CombatSimulator sim) {
        if (this.simulator != null && (this.owner != owner || this.simulator != sim)) {
            throw new IllegalStateException("Iron Sting is already bound to another simulator");
        }
        this.owner = owner;
        this.simulator = sim;
    }

    /** Gains one stack after eligible positive elemental damage at exact CT. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || sim.getActiveCharacter() != owner
                || action.getDamagePercent() <= 0.0
                || action.getElement() == Element.PHYSICAL
                || currentTime < nextStackTime) {
            return;
        }
        if (currentTime >= expiration) {
            stackCount = 0;
        }
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        expiration = currentTime + STACK_DURATION;
        nextStackTime = currentTime + STACK_COOLDOWN;
    }

    /** Applies all active all-damage stacks before exact expiry. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < expiration) {
            stats.add(StatType.DMG_BONUS_ALL, damageBonusPerStack * stackCount);
        }
    }
}
