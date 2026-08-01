package model.weapon;

import java.util.Objects;
import java.util.function.DoubleSupplier;

import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.ActionType;
import model.type.WeaponType;
import simulation.action.AttackAction;
import model.type.Element;

/**
 * Skyward Spine polearm with a passive CRIT/attack-speed bonus and Vacuum
 * Blade proc.
 */
public class SkywardSpine extends Weapon implements DamageTriggeredWeaponEffect {
    private final DoubleSupplier procDraw;
    private double lastVacuumTime = -10.0;

    /**
     * Constructs Skyward Spine with Lv 90 base stats and stochastic proc draws.
     */
    public SkywardSpine() {
        this(Math::random);
    }

    /**
     * Constructs Skyward Spine with an explicit proc draw source.
     *
     * @param procDraw source of chance values, where values below {@code 0.5}
     *                 trigger Vacuum Blade
     * @throws NullPointerException if {@code procDraw} is null
     */
    public SkywardSpine(DoubleSupplier procDraw) {
        super("Skyward Spine", new StatsContainer());
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 674); // Lv90
        s.add(StatType.ENERGY_RECHARGE, 0.368);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * Applies the weapon's constant CRIT Rate and attack speed bonuses.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Blackwing R1
        stats.add(StatType.CRIT_RATE, 0.08);
        stats.add(StatType.ATK_SPD, 0.12);
    }

    /**
     * Attempts to trigger Vacuum Blade after normal or charged attack damage.
     *
     * @param user the character who dealt the damage
     * @param action the triggering attack action
     * @param currentTime simulation time in seconds at the damage event
     * @param sim the active combat simulator
     */
    @Override
    public void onDamage(Character user, AttackAction action, double currentTime, simulation.CombatSimulator sim) {
        if (action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.CHARGE) {
            if (currentTime - lastVacuumTime >= 2.0) {
                // 50% Chance
                if (procDraw.getAsDouble() < 0.5) {
                    lastVacuumTime = currentTime;

                    // Trigger Vacuum Blade
                    // 40% ATK as DMG
                    AttackAction vacuum = new AttackAction(
                            "Vacuum Blade", 0.40, Element.PHYSICAL, StatType.BASE_ATK, StatType.PHYSICAL_DMG_BONUS, 0.0,
                            ActionType.OTHER);

                    java.util.List<mechanics.buff.Buff> buffs = sim.getApplicableBuffs(user);
                    double dmg = mechanics.formula.DamageCalculator.calculateDamage(user, sim.getEnemy(), vacuum, buffs,
                            currentTime, 1.0, sim);

                    if (sim.isLoggingEnabled()) {
                        System.out.println("   [Weapon] Skyward Spine Vacuum Blade triggered!");
                        System.out.println(String.format("   -> Damage: %,.0f", dmg));
                    }
                    sim.recordDamage(user.getCharacterId(), dmg);
                }
            }
        }
    }
}
