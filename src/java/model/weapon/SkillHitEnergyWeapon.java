package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.event.SimpleTimerEvent;

/**
 * Shared Samurai Conduct Skill bonus, delayed Energy drain, and periodic refund.
 */
public abstract class SkillHitEnergyWeapon extends Weapon
        implements DamageTriggeredWeaponEffect {
    private static final double DRAIN_DELAY = 23.0 / 60.0;
    private static final double PROC_COOLDOWN = 10.0;
    private static final double RECOVERY_INTERVAL = 2.0;
    private static final int RECOVERY_TICKS = 3;

    private final int refinement;
    private final double recoveryPerTick;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Samurai Conduct weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     */
    protected SkillHitEnergyWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Samurai Conduct refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.recoveryPerTick = 2.5 + 0.5 * refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
        getStats().set(StatType.SKILL_DMG_BONUS, 0.045 + 0.015 * refinement);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Starts one delayed drain and three recovery ticks after eligible Skill damage.
     *
     * @param user weapon owner who dealt the Skill hit
     * @param action resolved damage action
     * @param currentTime hit time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() <= 0.0
                || (action.getActionType() != ActionType.SKILL
                        && !action.isCountsAsSkillDmg())
                || currentTime < nextProcTime) {
            return;
        }
        nextProcTime = currentTime + PROC_COOLDOWN;

        sim.registerEvent(new SimpleTimerEvent(
                currentTime + DRAIN_DELAY, DRAIN_DELAY) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                user.spendEnergy(3.0);
                finish();
            }
        });
        sim.registerEvent(new SimpleTimerEvent(
                currentTime + RECOVERY_INTERVAL, RECOVERY_INTERVAL) {
            private int remainingTicks = RECOVERY_TICKS;

            @Override
            public void onTick(CombatSimulator activeSim) {
                user.receiveFlatEnergy(recoveryPerTick);
                remainingTicks--;
                if (remainingTicks == 0) {
                    finish();
                }
            }
        });
    }
}
