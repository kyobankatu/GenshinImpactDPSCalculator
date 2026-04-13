package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionRequest;

/**
 * Prospector's Shovel (custom weapon).
 *
 * <p><b>Base stats (Lv 90):</b> 510 Base ATK, 41.3% ATK (substat).
 *
 * <p><b>Swift and Sure (R5):</b>
 * <ul>
 *   <li>Electro-Charged DMG +96% (always active).</li>
 *   <li>Lunar-Charged DMG +48% — only active when Moonsign is
 *       {@link simulation.CombatSimulator.Moonsign#ASCENDANT_GLEAM}.</li>
 * </ul>
 */
public class ProspectorShovel extends Weapon {

    /** Cached simulator reference used to check the current Moonsign state. */
    private simulation.CombatSimulator simRef = null;

    public ProspectorShovel() {
        super("Prospector's Shovel", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.ATK_PERCENT, 0.413);
        this.weaponType = WeaponType.POLEARM;
    }

    /** Caches the simulator reference on first action for Moonsign checks. */
    @Override
    public void onAction(Character user, CharacterActionRequest request, simulation.CombatSimulator sim) {
        if (simRef == null) {
            simRef = sim;
        }
    }

    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // R5: Electro-Charged DMG +96% (unconditional)
        stats.add(StatType.ELECTRO_CHARGED_DMG_BONUS, 0.96);

        // R5: Lunar-Charged DMG +48% — only when Moonsign is Ascendant Gleam
        if (simRef != null
                && simRef.getMoonsign() == simulation.CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, 0.48);
        }
    }
}
