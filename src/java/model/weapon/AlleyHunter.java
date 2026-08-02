package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/** Alley Hunter bow with fixed-cadence off-field growth and on-field decay. */
public class AlleyHunter extends Weapon implements SimulatorInitializedWeaponEffect {
    private static final int MAX_STACKS = 10;
    private static final int ACTIVE_GRACE_TICKS = 4;
    private static final double TICK_INTERVAL = 1.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private int consecutiveActiveTicks;

    /** Constructs Alley Hunter at refinement rank five. */
    public AlleyHunter() {
        this(5);
    }

    /**
     * Constructs Alley Hunter at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AlleyHunter(int refinement) {
        super("Alley Hunter", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.damageBonusPerStack = 0.015 + 0.005 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the current Oppidan Ambush stack count. */
    public int getStackCount() {
        return stackCount;
    }

    /** Registers the passive's fixed one-second combat cadence. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Alley Hunter is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.registerEvent(new SimpleTimerEvent(
                sim.getCurrentTime() + TICK_INTERVAL, TICK_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                if (activeSimulator.getActiveCharacter() != owner) {
                    consecutiveActiveTicks = 0;
                    stackCount = Math.min(MAX_STACKS, stackCount + 1);
                    return;
                }
                consecutiveActiveTicks++;
                if (consecutiveActiveTicks > ACTIVE_GRACE_TICKS) {
                    stackCount = Math.max(0, stackCount - 2);
                }
            }
        });
    }

    /** Applies the current Oppidan Ambush all-damage bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DMG_BONUS_ALL, damageBonusPerStack * stackCount);
    }
}
