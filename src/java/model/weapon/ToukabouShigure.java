package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Toukabou Shigure sword with a single-enemy Cursed Parasol damage window.
 *
 * <p>The simulator's immortal single-enemy model represents the mark as an
 * owner-wide damage bonus during its target window. Enemy-defeat cooldown
 * resets are therefore outside the modeled combat boundary.</p>
 */
public class ToukabouShigure extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final double MARK_DURATION = 10.0;
    private static final double ACTIVATION_COOLDOWN = 15.0;

    private final int refinement;
    private final double markedTargetDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double markedUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs Toukabou Shigure at refinement rank five. */
    public ToukabouShigure() {
        this(5);
    }

    /**
     * Constructs Toukabou Shigure at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ToukabouShigure(int refinement) {
        super("Toukabou Shigure", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.markedTargetDamageBonus = 0.12 + 0.04 * refinement;
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

    /**
     * Binds the only owner and simulator permitted to activate the mark.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Toukabou Shigure is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies Cursed Parasol after an eligible active-owner hit resolves.
     *
     * <p>Damage hooks run after stat resolution, so the triggering hit is not
     * affected by the newly opened window.</p>
     */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim != simulator
                || user != owner
                || sim.getActiveCharacter() != owner
                || action.getDamagePercent() <= 0.0
                || currentTime < nextActivationTime) {
            return;
        }
        markedUntil = currentTime + MARK_DURATION;
        nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
    }

    /**
     * Applies the marked-target all-damage bonus before exact expiry.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < markedUntil) {
            stats.add(StatType.DMG_BONUS_ALL, markedTargetDamageBonus);
        }
    }
}
