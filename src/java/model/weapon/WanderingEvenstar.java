package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import mechanics.buff.BuffId;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import mechanics.buff.Buff;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/**
 * Wandering Evenstar catalyst with an Elemental Mastery to ATK conversion
 * passive.
 */
public class WanderingEvenstar extends Weapon implements SimulatorInitializedWeaponEffect {
    private static final double FIRST_TRIGGER_TIME = 64.0 / 60.0;
    private static final double TRIGGER_INTERVAL = 10.0;
    private static final double BUFF_DURATION = 12.0;
    private static final double OWNER_ATK_RATIO = 0.48;
    private static final double ALLY_SHARE_RATIO = 0.30;

    private boolean initialized;
    private SnapshotAtkBuff ownerBuff;
    private SnapshotAtkBuff allyBuff;

    /**
     * Constructs Wandering Evenstar with Lv 90 base stats.
     */
    public WanderingEvenstar() {
        super("Wandering Evenstar", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.ELEMENTAL_MASTERY, 165);
        this.weaponType = WeaponType.CATALYST;
    }

    /**
     * Registers Wildling Nightstar's first 64-frame activation and ten-second
     * resnapshot cadence.
     *
     * @param owner owner equipped with Wandering Evenstar
     * @param sim simulator containing the owner
     */
    @Override
    public void initializeForSimulator(Character owner, CombatSimulator sim) {
        if (initialized) {
            return;
        }
        initialized = true;
        sim.registerEvent(new SimpleTimerEvent(FIRST_TRIGGER_TIME, TRIGGER_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                refreshSnapshot(owner, activeSimulator);
            }
        });
    }

    private void refreshSnapshot(Character owner, CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        StatsContainer effectiveStats = owner.getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(effectiveStats, currentTime);
            }
        }
        double ownerEM = effectiveStats.get(StatType.ELEMENTAL_MASTERY);

        if (ownerBuff == null) {
            ownerBuff = new SnapshotAtkBuff("Wildling Nightstar (Owner)");
            ownerBuff.sourcedBy(owner.getCharacterId());
            owner.addBuff(ownerBuff);

            allyBuff = new SnapshotAtkBuff("Wildling Nightstar (Party)");
            allyBuff.exclude(owner.getCharacterId());
            allyBuff.sourcedBy(owner.getCharacterId());
            sim.applyTeamBuff(allyBuff);
        }

        ownerBuff.refresh(ownerEM * OWNER_ATK_RATIO, currentTime);
        allyBuff.refresh(ownerEM * OWNER_ATK_RATIO * ALLY_SHARE_RATIO, currentTime);
    }

    /**
     * Mutable flat-ATK snapshot refreshed by one equipped Evenstar instance.
     */
    private static final class SnapshotAtkBuff extends Buff {
        private double atkBonus;

        private SnapshotAtkBuff(String name) {
            super(name, BuffId.WANDERING_EVENSTAR_WILDLING_NIGHTSTAR, 0.0, 0.0);
        }

        private void refresh(double newAtkBonus, double currentTime) {
            this.atkBonus = newAtkBonus;
            this.startTime = currentTime;
            this.expirationTime = currentTime + BUFF_DURATION;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(StatType.ATK_FLAT, atkBonus);
        }
    }
}
