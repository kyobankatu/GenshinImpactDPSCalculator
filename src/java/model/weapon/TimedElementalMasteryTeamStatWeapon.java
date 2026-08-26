package model.weapon;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/**
 * Shared periodic EM snapshot converted into owner and party stat buffs.
 */
public abstract class TimedElementalMasteryTeamStatWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect, SnapshotAwareWeaponEffect {
    private static final double EPSILON = 1e-9;
    private static final double FIRST_TRIGGER_TIME = 64.0 / 60.0;
    private static final double TRIGGER_INTERVAL = 10.0;
    private static final double BUFF_DURATION = 12.0;
    private static final double ALLY_SHARE_RATIO = 0.30;

    private final int refinement;
    private final String effectName;
    private final BuffId buffId;
    private final StatType convertedStat;
    private final double ownerConversionRatio;

    private Character owner;
    private CombatSimulator simulator;
    private SnapshotStatBuff ownerBuff;
    private SnapshotStatBuff allyBuff;
    private double nextTriggerTime = FIRST_TRIGGER_TIME;
    private long timerGeneration;

    /**
     * Constructs one timed EM team-stat weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param refinement refinement rank in the inclusive range 1-5
     * @param effectName passive effect display name
     * @param buffId typed passive identity
     * @param convertedStat stat derived from the EM snapshot
     * @param ownerConversionRatio owner stat granted per point of EM
     */
    protected TimedElementalMasteryTeamStatWeapon(
            String name,
            WeaponType weaponType,
            int refinement,
            String effectName,
            BuffId buffId,
            StatType convertedStat,
            double ownerConversionRatio) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.effectName = effectName;
        this.buffId = buffId;
        this.convertedStat = convertedStat;
        this.ownerConversionRatio = ownerConversionRatio;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 165.0);
    }

    /** Returns this weapon's refinement rank. */
    public final int getRefinement() {
        return refinement;
    }

    /** Registers the 64-frame initial snapshot and ten-second refresh cadence. */
    @Override
    public final void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Timed EM team-stat weapon is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        scheduleRefresh(nextTriggerTime);
    }

    /** Captures converted values, initialization state, and timer phase. */
    @Override
    public final State captureWeaponState() {
        boolean initialized = ownerBuff != null || allyBuff != null;
        if (initialized && (ownerBuff == null || allyBuff == null)) {
            throw new IllegalStateException(
                    "Timed EM weapon has incomplete buff state");
        }
        return new TimedEmState(
                this,
                initialized,
                ownerBuff,
                allyBuff,
                initialized ? ownerBuff.value : 0.0,
                initialized ? allyBuff.value : 0.0,
                nextTriggerTime);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public final void restoreWeaponState(State state) {
        if (!(state instanceof TimedEmState)) {
            throw new IllegalArgumentException(
                    "Timed EM weapon state type is invalid");
        }
        TimedEmState restored = (TimedEmState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Timed EM weapon state belongs to another instance");
        }
        if (simulator == null || owner == null) {
            throw new IllegalStateException(
                    "Timed EM weapon must be initialized before restore");
        }
        if (restored.nextTriggerTime < simulator.getCurrentTime() - EPSILON) {
            throw new IllegalArgumentException(
                    "Timed EM weapon trigger precedes restored clock");
        }
        if (restored.buffsInitialized) {
            if (!(restored.ownerBuff instanceof TimedElementalMasteryTeamStatWeapon.SnapshotStatBuff)
                    || !(restored.allyBuff instanceof TimedElementalMasteryTeamStatWeapon.SnapshotStatBuff)) {
                throw new IllegalStateException(
                        "Timed EM weapon cannot restore missing buff objects");
            }
            ownerBuff = (SnapshotStatBuff) restored.ownerBuff;
            allyBuff = (SnapshotStatBuff) restored.allyBuff;
            ownerBuff.value = restored.ownerValue;
            allyBuff.value = restored.allyValue;
        } else {
            ownerBuff = null;
            allyBuff = null;
        }
        nextTriggerTime = restored.nextTriggerTime;
        scheduleRefresh(nextTriggerTime);
    }

    private void scheduleRefresh(double triggerTime) {
        long scheduledGeneration = ++timerGeneration;
        simulator.registerEvent(new SimpleTimerEvent(
                triggerTime,
                TRIGGER_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                if (scheduledGeneration != timerGeneration) {
                    finish();
                    return;
                }
                nextTriggerTime = activeSimulator.getCurrentTime()
                        + TRIGGER_INTERVAL;
                refreshSnapshot(activeSimulator);
            }
        });
    }

    private void refreshSnapshot(CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        StatsContainer effectiveStats = owner.getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(effectiveStats, currentTime);
            }
        }
        double convertedValue = effectiveStats.get(StatType.ELEMENTAL_MASTERY)
                * ownerConversionRatio;

        if (ownerBuff == null) {
            ownerBuff = new SnapshotStatBuff(effectName + " (Owner)");
            ownerBuff.sourcedBy(owner.getCharacterId());
            owner.addBuff(ownerBuff);

            allyBuff = new SnapshotStatBuff(effectName + " (Party)");
            allyBuff.exclude(owner.getCharacterId());
            allyBuff.sourcedBy(owner.getCharacterId());
            sim.applyTeamBuff(allyBuff);
        }

        ownerBuff.refresh(convertedValue, currentTime);
        allyBuff.refresh(convertedValue * ALLY_SHARE_RATIO, currentTime);
    }

    /** Mutable typed-stat snapshot refreshed by one equipped weapon instance. */
    private final class SnapshotStatBuff extends Buff {
        private double value;

        private SnapshotStatBuff(String name) {
            super(name, buffId, 0.0, 0.0);
        }

        private void refresh(double newValue, double currentTime) {
            value = newValue;
            startTime = currentTime;
            expirationTime = currentTime + BUFF_DURATION;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(convertedStat, value);
        }
    }

    /** Immutable periodic conversion snapshot. */
    private static final class TimedEmState implements State {
        private final TimedElementalMasteryTeamStatWeapon source;
        private final boolean buffsInitialized;
        private final Buff ownerBuff;
        private final Buff allyBuff;
        private final double ownerValue;
        private final double allyValue;
        private final double nextTriggerTime;

        private TimedEmState(
                TimedElementalMasteryTeamStatWeapon source,
                boolean buffsInitialized,
                Buff ownerBuff,
                Buff allyBuff,
                double ownerValue,
                double allyValue,
                double nextTriggerTime) {
            this.source = source;
            this.buffsInitialized = buffsInitialized;
            this.ownerBuff = ownerBuff;
            this.allyBuff = allyBuff;
            this.ownerValue = ownerValue;
            this.allyValue = allyValue;
            this.nextTriggerTime = nextTriggerTime;
        }
    }
}
