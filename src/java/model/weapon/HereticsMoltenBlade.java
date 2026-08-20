package model.weapon;

import java.util.ArrayList;
import java.util.List;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.MovementAwareWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Heretic's Molten Blade with an explicit one-second movement-distance contract. */
public final class HereticsMoltenBlade extends Weapon
        implements ActionTriggeredWeaponEffect,
        MovementAwareWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        SwitchAwareWeaponEffect {
    private static final double LOOKBACK_DURATION = 1.0;
    private static final double MAX_DISTANCE = 18.0;
    private static final double EFFECT_DURATION = 14.0;
    private static final double ACTIVATION_COOLDOWN = 14.0;

    private final int refinement;
    private final double minimumAttackBonus;
    private final double maximumAttackBonus;
    private final List<MovementSample> movementSamples = new ArrayList<>();
    private Character owner;
    private CombatSimulator simulator;
    private double effectStartTime = Double.NEGATIVE_INFINITY;
    private double effectUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationAt = Double.NEGATIVE_INFINITY;

    /** Constructs Heretic's Molten Blade at refinement rank five. */
    public HereticsMoltenBlade() {
        this(5);
    }

    /** Constructs Heretic's Molten Blade at the selected refinement rank. */
    public HereticsMoltenBlade(int refinement) {
        super("Heretic's Molten Blade", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        minimumAttackBonus = 0.135 + 0.045 * refinement;
        maximumAttackBonus = minimumAttackBonus * 2.0;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds one weapon instance to its equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null
                || equippedOwner.getWeapon() != this
                || !sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Heretic owner must equip this weapon in the target party");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Heretic is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Records an explicit movement segment completed by the equipped owner. */
    @Override
    public void onMovement(
            Character movingOwner,
            double distanceMeters,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (movingOwner != owner || activeSimulator != simulator) {
            return;
        }
        if (currentTime + 1e-9 < effectStartTime
                || currentTime >= effectUntil) {
            return;
        }
        movementSamples.add(new MovementSample(currentTime, distanceMeters));
    }

    /** Opens the distance-scaled ATK window on an accepted Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (user != owner
                || activeSimulator != simulator
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        if (currentTime < nextActivationAt) {
            return;
        }
        movementSamples.clear();
        effectStartTime = currentTime;
        effectUntil = currentTime + EFFECT_DURATION;
        nextActivationAt = currentTime + ACTIVATION_COOLDOWN;
    }

    /** Returns movement recorded in the preceding rolling one-second window. */
    public double getRecentMovementDistance(double currentTime) {
        double distance = 0.0;
        for (MovementSample sample : movementSamples) {
            if (sample.time > currentTime - LOOKBACK_DURATION + 1e-9
                    && sample.time <= currentTime + 1e-9) {
                distance += sample.distanceMeters;
            }
        }
        return distance;
    }

    /** Applies the most recent completed one-second movement sample. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < effectUntil
                && currentTime + 1e-9 >= effectStartTime + LOOKBACK_DURATION) {
            stats.add(StatType.ATK_PERCENT,
                    calculateAttackBonus(currentTime));
        }
    }

    /** Removes the effect immediately when the owner leaves the field. */
    @Override
    public void onSwitchOut(Character user, CombatSimulator activeSimulator) {
        if (user == owner && activeSimulator == simulator) {
            effectUntil = Double.NEGATIVE_INFINITY;
            effectStartTime = Double.NEGATIVE_INFINITY;
            movementSamples.clear();
        }
    }

    /** Captures movement history and every runtime boundary. */
    @Override
    public State captureWeaponState() {
        return new HereticState(
                this,
                movementSamples,
                effectStartTime,
                effectUntil,
                nextActivationAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof HereticState)) {
            throw new IllegalArgumentException("Heretic state type is invalid");
        }
        HereticState restored = (HereticState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Heretic state belongs to another weapon instance");
        }
        movementSamples.clear();
        movementSamples.addAll(restored.movementSamples);
        effectStartTime = restored.effectStartTime;
        effectUntil = restored.effectUntil;
        nextActivationAt = restored.nextActivationAt;
    }

    private double calculateAttackBonus(double currentTime) {
        double completedSeconds = Math.floor(
                currentTime - effectStartTime + 1e-9);
        double sampleEnd = effectStartTime + completedSeconds;
        double sampleStart = sampleEnd - LOOKBACK_DURATION;
        double distance = 0.0;
        for (MovementSample sample : movementSamples) {
            if (sample.time > sampleStart + 1e-9
                    && sample.time <= sampleEnd + 1e-9) {
                distance += sample.distanceMeters;
            }
        }
        double ratio = Math.min(1.0, distance / MAX_DISTANCE);
        return minimumAttackBonus
                + (maximumAttackBonus - minimumAttackBonus) * ratio;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Heretic refinement must be between 1 and 5");
        }
    }

    /** One explicit completed movement segment. */
    private static final class MovementSample {
        private final double time;
        private final double distanceMeters;

        private MovementSample(double time, double distanceMeters) {
            this.time = time;
            this.distanceMeters = distanceMeters;
        }
    }

    /** Immutable Heretic runtime state. */
    private static final class HereticState implements State {
        private final HereticsMoltenBlade source;
        private final List<MovementSample> movementSamples;
        private final double effectStartTime;
        private final double effectUntil;
        private final double nextActivationAt;

        private HereticState(
                HereticsMoltenBlade source,
                List<MovementSample> movementSamples,
                double effectStartTime,
                double effectUntil,
                double nextActivationAt) {
            this.source = source;
            this.movementSamples = new ArrayList<>(movementSamples);
            this.effectStartTime = effectStartTime;
            this.effectUntil = effectUntil;
            this.nextActivationAt = nextActivationAt;
        }
    }
}
