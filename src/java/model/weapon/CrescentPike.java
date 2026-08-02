package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.ParticleListener;
import simulation.action.AttackAction;

/**
 * Crescent Pike polearm with a particle-triggered Physical follow-up window.
 *
 * <p>The particle listener's active-owner notification is the simulator's
 * current pickup boundary. A positive Normal or Charged damage event during the
 * resulting {@code [pickup, pickup + 5)} window produces one zero-duration
 * Physical {@link ActionType#OTHER} follow-up at the same simulation time.</p>
 */
public class CrescentPike extends Weapon
        implements SimulatorInitializedWeaponEffect, ParticleListener, DamageListener {
    private static final String PROC_NAME = "Crescent Pike Infusion Needle";
    private static final double PROC_DURATION = 5.0;

    private final int refinement;
    private final double procMotionValue;
    private Character owner;
    private CombatSimulator simulator;
    private double procWindowStart = Double.POSITIVE_INFINITY;
    private double procWindowEnd = Double.NEGATIVE_INFINITY;

    /** Constructs Crescent Pike at refinement rank five. */
    public CrescentPike() {
        this(5);
    }

    /**
     * Constructs Crescent Pike at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CrescentPike(int refinement) {
        super("Crescent Pike", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.15 + 0.05 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.PHYSICAL_DMG_BONUS, 0.345);
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
     * Binds the equipped owner and registers one particle and damage listener.
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
                        "Crescent Pike is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addParticleListener(this);
        sim.addDamageListener(this);
    }

    /**
     * Opens or refreshes the proc window when the active owner receives a
     * positive particle notification.
     *
     * @param element generated particle element; every element is eligible
     * @param count generated particle count, which must be positive
     * @param time particle notification time in simulation seconds
     */
    @Override
    public void onParticle(Element element, double count, double time) {
        if (simulator == null
                || simulator.getActiveCharacter() != owner
                || !(count > 0.0)) {
            return;
        }
        procWindowStart = time;
        procWindowEnd = time + PROC_DURATION;
    }

    /**
     * Produces one nonrecursive Physical follow-up for an eligible damage event.
     *
     * @param actor character attributed with the resolved damage
     * @param action resolved action that may trigger the follow-up
     * @param damage positive final direct damage required to trigger
     * @param time damage-event time in simulation seconds
     */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator == null
                || actor != owner
                || simulator.getActiveCharacter() != owner
                || action == null
                || !(damage > 0.0)
                || !(action.getDamagePercent() > 0.0)
                || time < procWindowStart
                || time >= procWindowEnd
                || PROC_NAME.equals(action.getName())
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.CHARGE)) {
            return;
        }
        AttackAction proc = new AttackAction(
                PROC_NAME,
                procMotionValue,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        simulator.performActionWithoutTimeAdvance(owner.getCharacterId(), proc);
    }
}
