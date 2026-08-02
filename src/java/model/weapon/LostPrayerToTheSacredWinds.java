package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/** Lost Prayer to the Sacred Winds catalyst with fixed combat-time stacks. */
public class LostPrayerToTheSacredWinds extends Weapon
        implements SimulatorInitializedWeaponEffect, SwitchAwareWeaponEffect {
    private static final int MAX_STACKS = 4;
    private static final double STACK_INTERVAL = 4.0;

    private final int refinement;
    private final double elementalBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;

    /** Constructs Lost Prayer to the Sacred Winds at refinement rank five. */
    public LostPrayerToTheSacredWinds() {
        this(5);
    }

    /**
     * Constructs Lost Prayer to the Sacred Winds at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LostPrayerToTheSacredWinds(int refinement) {
        super("Lost Prayer to the Sacred Winds", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalBonusPerStack = 0.06 + 0.02 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the current Lost Prayer stack count. */
    public int getStackCount() {
        return stackCount;
    }

    /** Registers the fixed four-second combat cadence for this weapon instance. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Lost Prayer is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.registerEvent(new SimpleTimerEvent(
                sim.getCurrentTime() + STACK_INTERVAL, STACK_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                if (activeSimulator.getActiveCharacter() == owner) {
                    stackCount = Math.min(MAX_STACKS, stackCount + 1);
                }
            }
        });
    }

    /** Clears all stacks immediately when the owner leaves the field. */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
        if (sim == simulator && user == owner) {
            stackCount = 0;
        }
    }

    /** Applies current stacks to every elemental damage type. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null || stackCount == 0) {
            return;
        }
        for (Element element : Element.values()) {
            if (element != Element.PHYSICAL) {
                stats.add(element.getBonusStatType(),
                        elementalBonusPerStack * stackCount);
            }
        }
    }
}
