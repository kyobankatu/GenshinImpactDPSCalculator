package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.SwitchAwareArtifact;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.event.TimerEvent;

/**
 * Husk of Opulent Dreams artifact set with Curiosity stack management.
 *
 * <p>The fixed two-piece bonus grants 30% DEF. On-field Geo hits can grant one
 * Curiosity stack every 0.3 seconds, while an off-field owner gains one stack
 * every three seconds. Each of up to four stacks grants 6% DEF and 6% Geo DMG
 * Bonus. Six seconds after the last eligible gain, one stack is lost every
 * three seconds until another gain refreshes the decay schedule.</p>
 */
public class HuskOfOpulentDreams extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, DamageTriggeredArtifactEffect,
        SwitchAwareArtifact {
    private static final int MAX_STACKS = 4;
    private static final double STACK_DEF_BONUS = 0.06;
    private static final double STACK_GEO_BONUS = 0.06;
    private static final double ON_FIELD_HIT_COOLDOWN = 0.3;
    private static final double OFF_FIELD_GAIN_INTERVAL = 3.0;
    private static final double FIRST_DECAY_DELAY = 6.0;
    private static final double DECAY_INTERVAL = 3.0;

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private boolean offField;
    private double nextOnFieldGainTime = Double.NEGATIVE_INFINITY;
    private double nextOffFieldGainTime = Double.POSITIVE_INFINITY;
    private long offFieldGeneration;
    private long decayGeneration;

    /** Constructs Husk of Opulent Dreams with a fresh fixed-stat container. */
    public HuskOfOpulentDreams() {
        this(new StatsContainer());
    }

    /**
     * Constructs Husk of Opulent Dreams while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public HuskOfOpulentDreams(StatsContainer stats) {
        super("Husk of Opulent Dreams", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.DEF_PERCENT, 0.30);
    }

    /**
     * Binds Curiosity state to one owner and simulator.
     *
     * <p>Repeating the identical binding is idempotent. Reusing an instance for
     * another owner or simulator is rejected. An owner who starts off field
     * schedules the first Curiosity gain three seconds after initialization.</p>
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
                        "Husk of Opulent Dreams is already bound to another simulator");
            }
            return;
        }

        owner = equippedOwner;
        simulator = sim;
        offField = !startsActive;
        if (offField) {
            restartOffFieldCadence(sim.getCurrentTime());
        }
    }

    /**
     * Grants or refreshes Curiosity for an eligible on-field Geo hit.
     *
     * <p>Zero-damage hits remain eligible. Null actions, non-Geo hits, off-field
     * hits, mismatched bindings, and hits inside the 0.3-second cooldown are
     * inert.</p>
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the damage
     * @param damage final post-mitigation damage, which does not gate the effect
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onDamage(
            CombatSimulator sim,
            AttackAction action,
            double damage,
            Character callbackOwner) {
        if (!matchesBinding(callbackOwner, sim)
                || action == null
                || action.getElement() != Element.GEO
                || sim.getActiveCharacter() != owner) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (currentTime < nextOnFieldGainTime) {
            return;
        }
        nextOnFieldGainTime = currentTime + ON_FIELD_HIT_COOLDOWN;
        gainStack(currentTime);
    }

    /**
     * Stops the off-field cadence when the owner enters the field.
     *
     * @param sim simulator dispatching the switch
     * @param equippedOwner character receiving the callback
     */
    @Override
    public void onSwitchIn(CombatSimulator sim, Character equippedOwner) {
        if (!matchesBinding(equippedOwner, sim)) {
            return;
        }
        offField = false;
        nextOffFieldGainTime = Double.POSITIVE_INFINITY;
        offFieldGeneration++;
    }

    /**
     * Restarts the three-second off-field cadence when the owner leaves the field.
     *
     * @param sim simulator dispatching the switch
     * @param equippedOwner character receiving the callback
     */
    @Override
    public void onSwitchOut(CombatSimulator sim, Character equippedOwner) {
        if (!matchesBinding(equippedOwner, sim)) {
            return;
        }
        offField = true;
        restartOffFieldCadence(sim.getCurrentTime());
    }

    /** Applies the dynamic DEF and Geo bonuses from active Curiosity stacks. */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null || stackCount == 0) {
            return;
        }
        totalStats.add(StatType.DEF_PERCENT, STACK_DEF_BONUS * stackCount);
        totalStats.add(StatType.GEO_DMG_BONUS, STACK_GEO_BONUS * stackCount);
    }

    /** Returns the current Curiosity stack count for focused regression checks. */
    public int getStackCount() {
        return stackCount;
    }

    /** Returns whether a callback belongs to the initialized artifact binding. */
    private boolean matchesBinding(Character callbackOwner, CombatSimulator callbackSimulator) {
        return owner != null && simulator != null
                && owner == callbackOwner && simulator == callbackSimulator;
    }

    /** Starts a new off-field generation and schedules its first gain. */
    private void restartOffFieldCadence(double currentTime) {
        long generation = ++offFieldGeneration;
        nextOffFieldGainTime = currentTime + OFF_FIELD_GAIN_INTERVAL;
        scheduleOffFieldGain(nextOffFieldGainTime, generation);
    }

    /** Registers one immutable one-shot event for an off-field gain. */
    private void scheduleOffFieldGain(double triggerTime, long generation) {
        simulator.registerEvent(new TimerEvent() {
            @Override
            public double getNextTickTime() {
                return triggerTime;
            }

            @Override
            public void tick(CombatSimulator activeSimulator) {
                processOffFieldGain(activeSimulator, generation);
            }

            @Override
            public boolean isFinished(double currentTime) {
                return true;
            }
        });
    }

    /** Processes one due off-field gain and advances the cadence generation. */
    private void processOffFieldGain(CombatSimulator activeSimulator, long generation) {
        if (activeSimulator != simulator
                || generation != offFieldGeneration
                || !offField
                || activeSimulator.getActiveCharacter() == owner) {
            return;
        }

        double currentTime = activeSimulator.getCurrentTime();
        long nextGeneration = ++offFieldGeneration;
        nextOffFieldGainTime = currentTime + OFF_FIELD_GAIN_INTERVAL;
        gainStack(currentTime);
        scheduleOffFieldGain(nextOffFieldGainTime, nextGeneration);
    }

    /** Adds one stack up to the cap and refreshes the six-second decay delay. */
    private void gainStack(double currentTime) {
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        scheduleDecay(currentTime + FIRST_DECAY_DELAY);
    }

    /** Registers one immutable one-shot event for the current decay generation. */
    private void scheduleDecay(double triggerTime) {
        long generation = ++decayGeneration;
        simulator.registerEvent(new TimerEvent() {
            @Override
            public double getNextTickTime() {
                return triggerTime;
            }

            @Override
            public void tick(CombatSimulator activeSimulator) {
                processDecay(activeSimulator, generation);
            }

            @Override
            public boolean isFinished(double currentTime) {
                return true;
            }
        });
    }

    /**
     * Removes one stack or gives precedence to an off-field gain due at this time.
     */
    private void processDecay(CombatSimulator activeSimulator, long generation) {
        if (activeSimulator != simulator || generation != decayGeneration) {
            return;
        }

        double currentTime = activeSimulator.getCurrentTime();
        if (offField
                && activeSimulator.getActiveCharacter() != owner
                && nextOffFieldGainTime <= currentTime) {
            processOffFieldGain(activeSimulator, offFieldGeneration);
            return;
        }

        stackCount = Math.max(0, stackCount - 1);
        if (stackCount > 0) {
            scheduleDecay(currentTime + DECAY_INTERVAL);
        }
    }
}
