package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Azurelight sword with a live-Energy post-Skill ATK and CRIT DMG window.
 */
public class Azurelight extends Weapon
        implements ActionTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final double DURATION = 12.0;

    private final int refinement;
    private final double attackBonus;
    private final double zeroEnergyCritDamage;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Azurelight at refinement rank five. */
    public Azurelight() {
        this(5);
    }

    /**
     * Constructs Azurelight at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Azurelight(int refinement) {
        super("Azurelight", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackBonus = 0.18 + 0.06 * refinement;
        this.zeroEnergyCritDamage = 0.30 + 0.10 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner whose live Energy controls the secondary branch. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Azurelight is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Opens or refreshes Whitehill's Bestowal before the triggering Skill resolves. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && request.getKey() == CharacterActionKey.SKILL) {
            activeUntil = sim.getCurrentTime() + DURATION;
        }
    }

    /** Applies one ATK bonus and the additional zero-Energy ATK/CRIT branch. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime >= activeUntil) {
            return;
        }
        stats.add(StatType.ATK_PERCENT, attackBonus);
        if (owner != null && owner.getCurrentEnergy() <= 0.0) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
            stats.add(StatType.CRIT_DMG, zeroEnergyCritDamage);
        }
    }
}
