package model.weapon;

import java.util.Objects;
import java.util.function.DoubleSupplier;

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
import simulation.event.SimpleTimerEvent;

/** The Viridescent Hunt bow with an injected eight-hit Cyclone proc. */
public class TheViridescentHunt extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final String PROC_NAME = "The Viridescent Hunt Cyclone";
    private static final double PROC_CHANCE = 0.50;
    private static final double TICK_INTERVAL = 0.5;
    private static final int TICK_COUNT = 8;

    private final int refinement;
    private final double procMotionValue;
    private final double activationCooldown;
    private final DoubleSupplier procDraw;
    private Character owner;
    private CombatSimulator simulator;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs The Viridescent Hunt at refinement rank five with stochastic draws. */
    public TheViridescentHunt() {
        this(5, Math::random);
    }

    /**
     * Constructs The Viridescent Hunt at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheViridescentHunt(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs R5 The Viridescent Hunt with an explicit proc draw source.
     *
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public TheViridescentHunt(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs The Viridescent Hunt with selected refinement and proc draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public TheViridescentHunt(int refinement, DoubleSupplier procDraw) {
        super("The Viridescent Hunt", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.30 + 0.10 * refinement;
        this.activationCooldown = 15.0 - refinement;
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the active owner eligible to create Cyclones. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "The Viridescent Hunt is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Draws for a Cyclone after positive active-owner Normal/Charged hits. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        boolean eligibleType = action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.CHARGE;
        if (sim != simulator
                || user != owner
                || sim.getActiveCharacter() != owner
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !eligibleType
                || currentTime < nextActivationTime
                || procDraw.getAsDouble() >= PROC_CHANCE) {
            return;
        }
        nextActivationTime = currentTime + activationCooldown;
        sim.registerEvent(new SimpleTimerEvent(
                currentTime + TICK_INTERVAL, TICK_INTERVAL) {
            private int remainingTicks = TICK_COUNT;

            @Override
            public void onTick(CombatSimulator activeSimulator) {
                AttackAction proc = new AttackAction(
                        PROC_NAME,
                        procMotionValue,
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.PHYSICAL_DMG_BONUS,
                        0.0,
                        ActionType.OTHER);
                activeSimulator.performActionWithoutTimeAdvance(
                        owner.getCharacterId(), proc);
                remainingTicks--;
                if (remainingTicks == 0) {
                    finish();
                }
            }
        });
    }
}
