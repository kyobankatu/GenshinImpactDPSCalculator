package model.weapon;

import java.util.ArrayList;
import java.util.List;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/**
 * Prototype Amber's owner-Energy branch.
 *
 * <p>An accepted active-owner Burst queues flat Energy at 2, 4, and 6 seconds.
 * Metadata, refinement values, and pulse timing follow pinned gcsim
 * {@code ef41805d}. Party percentage healing is intentionally deferred.</p>
 */
public final class PrototypeAmber extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double[] PULSE_DELAYS = { 2.0, 4.0, 6.0 };
    private static final double EPSILON = 1e-9;

    private final int refinement;
    private final double energyPerPulse;
    private Character owner;
    private CombatSimulator simulator;
    private List<PendingPulse> pendingPulses = new ArrayList<>();

    /** Constructs Prototype Amber at refinement rank five. */
    public PrototypeAmber() {
        this(5);
    }

    /** Constructs Prototype Amber at the selected refinement rank. */
    public PrototypeAmber(int refinement) {
        super("Prototype Amber", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Prototype Amber refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        energyPerPulse = 3.5 + 0.5 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.HP_PERCENT, 0.413);
    }

    /** Returns refinement rank in the inclusive range 1-5. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns flat Energy restored by each of the three pulses. */
    public double getEnergyPerPulse() {
        return energyPerPulse;
    }

    /** Returns the number of unresolved owner-Energy pulses. */
    public int getPendingPulseCount() {
        return pendingPulses.size();
    }

    /** This represented branch has no continuously applied passive stat. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
    }

    /** Binds one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Prototype Amber owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Prototype Amber is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Prototype Amber equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Queues three Energy pulses after an accepted active-owner Burst. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        for (double delay : PULSE_DELAYS) {
            queuePulse(new PendingPulse(currentTime + delay));
        }
    }

    /** Captures all unresolved pulse timestamps. */
    @Override
    public State captureWeaponState() {
        return new PrototypeAmberState(this, pendingPulses);
    }

    /** Restores surviving pulses without allowing stale events to duplicate. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof PrototypeAmberState)) {
            throw new IllegalArgumentException(
                    "Prototype Amber state type is invalid");
        }
        PrototypeAmberState restored = (PrototypeAmberState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Prototype Amber state belongs to another instance");
        }
        pendingPulses = copyPulses(restored.pendingPulses);
        if (simulator == null) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        pendingPulses.removeIf(pulse ->
                pulse.time < currentTime - EPSILON);
        for (PendingPulse pulse : new ArrayList<>(pendingPulses)) {
            schedulePulse(pulse);
        }
    }

    private void queuePulse(PendingPulse pulse) {
        pendingPulses.add(pulse);
        schedulePulse(pulse);
    }

    private void schedulePulse(PendingPulse pulse) {
        simulator.registerEvent(new SimpleTimerEvent(pulse.time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                if (!pendingPulses.remove(pulse)) {
                    return;
                }
                owner.receiveFlatEnergy(energyPerPulse);
            }
        });
    }

    private boolean isBoundActiveOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static List<PendingPulse> copyPulses(
            List<PendingPulse> source) {
        List<PendingPulse> copy = new ArrayList<>();
        for (PendingPulse pulse : source) {
            copy.add(new PendingPulse(pulse.time));
        }
        return copy;
    }

    private static final class PendingPulse {
        private final double time;

        private PendingPulse(double time) {
            this.time = time;
        }
    }

    private static final class PrototypeAmberState implements State {
        private final PrototypeAmber source;
        private final List<PendingPulse> pendingPulses;

        private PrototypeAmberState(
                PrototypeAmber source,
                List<PendingPulse> pendingPulses) {
            this.source = source;
            this.pendingPulses = copyPulses(pendingPulses);
        }
    }
}
