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
 * Cloudforged bow with a two-stack Elemental Mastery window.
 *
 * <p>A successful typed Burst request is the simulator's observable boundary
 * for the owner's Energy decrease. Each eligible request grants one stack,
 * up to two, and refreshes the shared 18-second expiration.</p>
 */
public class Cloudforged extends Weapon
        implements ActionTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final int MAX_STACKS = 2;
    private static final double DURATION = 18.0;

    private final int refinement;
    private final double elementalMasteryPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Cloudforged at refinement rank five. */
    public Cloudforged() {
        this(5);
    }

    /**
     * Constructs Cloudforged at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Cloudforged(int refinement) {
        super("Cloudforged", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryPerStack = 30.0 + 10.0 * refinement;
        this.weaponType = WeaponType.BOW;
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
     * Returns the active stack count at the simulator's current time.
     *
     * @return active stack count in the inclusive range 0-2
     */
    public int getStackCount() {
        if (simulator == null || simulator.getCurrentTime() >= activeUntil) {
            return 0;
        }
        return stackCount;
    }

    /**
     * Binds the owner and simulator used to validate Burst notifications.
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
                        "Cloudforged is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Adds one stack after the bound owner's successful typed Burst request.
     *
     * <p>The action gateway invokes this hook only after its cooldown and
     * Energy gates succeed. Expired stacks are cleared before the new stack is
     * added, and every eligible Burst refreshes the shared duration.</p>
     *
     * @param user character performing the action
     * @param request typed action request
     * @param sim active combat simulator
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || user != owner
                || request == null
                || request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        if (currentTime >= activeUntil) {
            stackCount = 0;
        }
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        activeUntil = currentTime + DURATION;
    }

    /**
     * Applies Elemental Mastery from every stack before exact expiry.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryPerStack * stackCount);
        }
    }
}
