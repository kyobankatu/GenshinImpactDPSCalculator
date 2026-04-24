package model.weapon;

import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.ActionType;
import model.type.WeaponType;
import simulation.action.AttackAction;
import model.type.Element;

public class SkywardSpine extends Weapon implements DamageTriggeredWeaponEffect {
    public SkywardSpine() {
        super("Skyward Spine", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 674); // Lv90
        s.add(StatType.ENERGY_RECHARGE, 0.368);
        this.weaponType = WeaponType.POLEARM;
    }

    private double lastVacuumTime = -10.0;

    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Blackwing R1
        stats.add(StatType.CRIT_RATE, 0.08);
        stats.add(StatType.ATK_SPD, 0.12);
    }

    @Override
    public void onDamage(Character user, AttackAction action, double currentTime, simulation.CombatSimulator sim) {
        if (action.getActionType() == ActionType.NORMAL || action.getActionType() == ActionType.CHARGE) {
            if (currentTime - lastVacuumTime >= 2.0) {
                // 50% Chance
                if (Math.random() < 0.5) {
                    lastVacuumTime = currentTime;

                    // Trigger Vacuum Blade
                    // 40% ATK as DMG
                    AttackAction vacuum = new AttackAction(
                            "Vacuum Blade", 0.40, Element.PHYSICAL, StatType.BASE_ATK, StatType.PHYSICAL_DMG_BONUS, 0.0,
                            ActionType.OTHER);

                    java.util.List<mechanics.buff.Buff> buffs = sim.getApplicableBuffs(user);
                    double dmg = mechanics.formula.DamageCalculator.calculateDamage(user, sim.getEnemy(), vacuum, buffs,
                            currentTime, 1.0, sim);

                    System.out.println(String.format("   [Weapon] Skyward Spine Vacuum Blade triggered!"));
                    System.out.println(String.format("   -> Damage: %,.0f", dmg));
                    sim.recordDamage(user.getName(), dmg);
                }
            }
        }
    }
}
