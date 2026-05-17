package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Skyward Blade sword with a burst-triggered attack-speed buff and proc damage.
 */
public class SkywardBlade extends Weapon implements ActionTriggeredWeaponEffect {
    /**
     * Constructs Skyward Blade with Lv 90 base stats.
     */
    public SkywardBlade() {
        super("Skyward Blade", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 608); // Lv90
        s.add(StatType.ENERGY_RECHARGE, 0.551);
        this.weaponType = WeaponType.SWORD;
    }

    private double buffEndTime = 0.0;
    // 20% ATK DMG at R1
    private double procMotionValue = 0.20;

    /**
     * Applies the weapon's constant CRIT Rate bonus and its temporary
     * post-burst attack speed buff when active.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Sky-Piercing Fang
        // CR +4% (R1)
        stats.add(StatType.CRIT_RATE, 0.04);

        if (currentTime < buffEndTime) {
            // ATK SPD +10%
            stats.add(StatType.ATK_SPD, 0.10);
        }
    }

    /**
     * Activates the post-burst buff window and triggers the additional physical
     * hit on normal or charged actions during that window.
     *
     * @param user the character performing the action
     * @param request the requested character action
     * @param sim the active combat simulator
     */
    @Override
    public void onAction(model.entity.Character user, CharacterActionRequest request, simulation.CombatSimulator sim) {
        // Trigger on Burst
        if (request.getKey() == CharacterActionKey.BURST) {
            buffEndTime = sim.getCurrentTime() + 12.0;
            System.out.println(String.format("   [Weapon] Skyward Blade Buff Activated (Ends at %.1fs)", buffEndTime));
            return;
        }

        // Trigger on Normal/Charged hits
        // Check if buff is active and action is Normal or Charge
        if (sim.getCurrentTime() < buffEndTime) {
            if (request.getKey() == CharacterActionKey.NORMAL || request.getKey() == CharacterActionKey.CHARGE) {
                // Deal Additional Physical DMG
                simulation.action.AttackAction proc = new simulation.action.AttackAction(
                        "Skyward Blade Proc",
                        procMotionValue,
                        model.type.Element.PHYSICAL,
                        StatType.ATK_PERCENT // Scales with ATK
                );
                // Procs usually don't have animation time themselves, they just happen
                sim.performActionWithoutTimeAdvance(user.getName(), proc);
            }
        }
    }
}
