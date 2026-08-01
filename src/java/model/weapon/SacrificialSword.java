package model.weapon;

import java.util.Objects;
import java.util.function.DoubleSupplier;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * R5 Sacrificial Sword with Lv 90 stats and the Composed Skill-reset passive.
 */
public class SacrificialSword extends Weapon implements DamageTriggeredWeaponEffect {
    private static final double PROC_CHANCE = 0.8;
    private static final double PROC_COOLDOWN = 16.0;

    private final DoubleSupplier procDraw;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs R5 Sacrificial Sword with stochastic Composed draws.
     */
    public SacrificialSword() {
        this(Math::random);
    }

    /**
     * Constructs R5 Sacrificial Sword with an explicit Composed draw source.
     *
     * @param procDraw source of chance values; values below 0.8 trigger Composed
     * @throws NullPointerException if {@code procDraw} is null
     */
    public SacrificialSword(DoubleSupplier procDraw) {
        super("Sacrificial Sword", new StatsContainer());
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.ENERGY_RECHARGE, 0.613);
        this.weaponType = WeaponType.SWORD;
    }

    /**
     * Attempts R5 Composed after positive direct Elemental Skill damage.
     *
     * @param user        weapon owner who dealt the Skill damage
     * @param action      resolved damage action
     * @param currentTime damage time in simulation seconds
     * @param sim         active combat simulator
     */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getActionType() != ActionType.SKILL
                || action.getDamagePercent() <= 0.0
                || currentTime < nextProcTime) {
            return;
        }
        if (procDraw.getAsDouble() >= PROC_CHANCE) {
            return;
        }

        user.resetSkillCooldown(currentTime);
        nextProcTime = currentTime + PROC_COOLDOWN;
        if (sim.isLoggingEnabled()) {
            System.out.println("   [Weapon] Sacrificial Sword reset Skill cooldown");
        }
    }
}
