package model.weapon;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
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
        implements SimulatorInitializedWeaponEffect {
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
        sim.registerEvent(new SimpleTimerEvent(FIRST_TRIGGER_TIME, TRIGGER_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
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
}
