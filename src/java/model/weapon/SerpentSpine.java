package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/** Serpent Spine with a fixed combat-time, on-field stack cadence. */
public final class SerpentSpine extends Weapon
        implements SimulatorInitializedWeaponEffect, SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 5;
    private static final double STACK_INTERVAL = 4.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double nextCheckAt = Double.NEGATIVE_INFINITY;
    private int timerGeneration;

    /** Constructs Serpent Spine at refinement rank five. */
    public SerpentSpine() {
        this(5);
    }

    /**
     * Constructs Serpent Spine at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SerpentSpine(int refinement) {
        super("Serpent Spine", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Serpent Spine refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.damageBonusPerStack = 0.05 + 0.01 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the all-damage bonus granted by each stack. */
    public double getDamageBonusPerStack() {
        return damageBonusPerStack;
    }

    /** Returns the current persistent stack count. */
    public int getStackCount() {
        return stackCount;
    }

    /** Binds the owner and starts the global four-second check cadence. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Serpent Spine is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Serpent Spine equipped");
        }
        owner = equippedOwner;
        simulator = sim;
        nextCheckAt = sim.getCurrentTime() + STACK_INTERVAL;
        scheduleCadence();
    }

    /** Applies the damage bonus from every retained stack. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DMG_BONUS_ALL, damageBonusPerStack * stackCount);
    }

    /** Captures the stack count and the next fixed cadence boundary. */
    @Override
    public State captureWeaponState() {
        return new SpineState(this, stackCount, nextCheckAt);
    }

    /** Restores stack and cadence state, replacing any pre-restore timer generation. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof SpineState)) {
            throw new IllegalArgumentException("Serpent Spine state type is invalid");
        }
        SpineState spineState = (SpineState) state;
        if (spineState.source != this) {
            throw new IllegalArgumentException(
                    "Serpent Spine state belongs to another weapon instance");
        }
        stackCount = spineState.stackCount;
        nextCheckAt = spineState.nextCheckAt;
        if (simulator != null) {
            scheduleCadence();
        }
    }

    private void scheduleCadence() {
        int generation = ++timerGeneration;
        simulator.registerEvent(new SimpleTimerEvent(nextCheckAt, STACK_INTERVAL) {
            @Override
            public void onTick(CombatSimulator sim) {
                if (generation != timerGeneration) {
                    finish();
                    return;
                }
                if (sim.getActiveCharacter() == owner && stackCount < MAX_STACKS) {
                    stackCount++;
                }
                nextCheckAt += STACK_INTERVAL;
            }
        });
    }

    /** Immutable cadence state tied to one weapon instance. */
    private static final class SpineState implements State {
        private final SerpentSpine source;
        private final int stackCount;
        private final double nextCheckAt;

        private SpineState(SerpentSpine source, int stackCount, double nextCheckAt) {
            this.source = source;
            this.stackCount = stackCount;
            this.nextCheckAt = nextCheckAt;
        }
    }
}
