package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Sequence of Solitude bow with an immediate Max-HP Physical proc. */
public class SequenceOfSolitude extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final String PROC_NAME = "Sequence of Solitude Silent Trigger";
    private static final double PROC_COOLDOWN = 15.0;

    private final int refinement;
    private final double procMotionValue;
    private Character owner;
    private CombatSimulator simulator;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /** Constructs Sequence of Solitude at refinement rank five. */
    public SequenceOfSolitude() {
        this(5);
    }

    /**
     * Constructs Sequence of Solitude at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SequenceOfSolitude(int refinement) {
        super("Sequence of Solitude", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.30 + 0.10 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.HP_PERCENT, 0.413);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the active owner eligible to trigger Silent Trigger. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Sequence of Solitude is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Resolves an eligible Max-HP proc immediately at exact cooldown. */
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
                || action.getName().equals(PROC_NAME)
                || currentTime < nextProcTime) {
            return;
        }
        nextProcTime = currentTime + PROC_COOLDOWN;
        AttackAction proc = new AttackAction(
                PROC_NAME,
                procMotionValue,
                Element.PHYSICAL,
                StatType.BASE_HP,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), proc);
    }
}
