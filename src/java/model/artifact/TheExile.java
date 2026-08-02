package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.event.TimerEvent;

/**
 * The Exile artifact set with a Burst-triggered party Energy sequence.
 *
 * <p>The fixed two-piece bonus grants 20% Energy Recharge. After the bound
 * owner uses an accepted Elemental Burst, the four-piece bonus restores two
 * flat Energy to every other current party member at two, four, and six
 * seconds. Recasting or triggering another copy replaces the typed sequence
 * marker, making already scheduled ticks from the previous sequence inert.</p>
 *
 * <p>Pending timer events follow the simulator-wide snapshot boundary: clock
 * restore clears the event queue rather than serializing artifact timers.</p>
 */
public class TheExile extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, BurstTriggeredArtifactEffect {
    private static final double EFFECT_DURATION = 6.0;
    private static final double TICK_INTERVAL = 2.0;
    private static final int TICK_COUNT = 3;
    private static final double PARTY_FLAT_ENERGY = 2.0;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs The Exile with a fresh fixed-stat container. */
    public TheExile() {
        this(new StatsContainer());
    }

    /**
     * Constructs The Exile while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public TheExile(StatsContainer stats) {
        super("The Exile", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    /**
     * Binds this set to exactly one owner and simulator.
     *
     * @param equippedOwner character carrying this artifact set
     * @param sim simulator containing the equipped owner
     * @param startsActive whether the owner starts as the active character
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "The Exile is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Starts a fresh non-stacking flat-Energy sequence.
     *
     * @param sim simulator dispatching the accepted Burst
     */
    @Override
    public void onBurst(CombatSimulator sim) {
        if (simulator == null || sim != simulator) {
            return;
        }

        Buff sequenceMarker = new SimpleBuff(
                "The Exile: Four-Piece Sequence",
                BuffId.THE_EXILE_4PC_SEQUENCE,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> {
                }).sourcedBy(owner.getCharacterId());
        sim.applyTeamBuffNoStack(sequenceMarker);

        for (int tick = 1; tick <= TICK_COUNT; tick++) {
            sim.registerEvent(new EnergyTick(
                    sim.getCurrentTime() + tick * TICK_INTERVAL,
                    simulator,
                    owner,
                    sequenceMarker));
        }
    }

    /** One-shot Energy tick owned by one exact non-stacking sequence marker. */
    private static final class EnergyTick implements TimerEvent {
        private final double tickTime;
        private final CombatSimulator simulator;
        private final Character owner;
        private final Buff sequenceMarker;

        private EnergyTick(
                double tickTime,
                CombatSimulator simulator,
                Character owner,
                Buff sequenceMarker) {
            this.tickTime = tickTime;
            this.simulator = simulator;
            this.owner = owner;
            this.sequenceMarker = sequenceMarker;
        }

        @Override
        public double getNextTickTime() {
            return tickTime;
        }

        @Override
        public void tick(CombatSimulator sim) {
            if (sim != simulator || !sim.getTeamBuffList().contains(sequenceMarker)) {
                return;
            }
            for (Character partyMember : sim.getPartyMembers()) {
                if (partyMember != owner) {
                    partyMember.receiveFlatEnergy(PARTY_FLAT_ENERGY);
                }
            }
        }

        @Override
        public boolean isFinished(double currentTime) {
            return true;
        }
    }
}
