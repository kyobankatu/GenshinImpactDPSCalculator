package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Skyward Atlas with Lv. 90 stats and its static elemental damage bonus.
 *
 * <p>The Favor of the Clouds autonomous damage proc is unavailable because
 * the simulator has no autonomous cloud-attack runtime entity.</p>
 */
public class SkywardAtlas extends StaticElementalDamageWeapon {
    /** Constructs Skyward Atlas at refinement rank five. */
    public SkywardAtlas() {
        this(5);
    }

    /**
     * Constructs Skyward Atlas at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SkywardAtlas(int refinement) {
        super(
                "Skyward Atlas",
                WeaponType.CATALYST,
                674.0,
                StatType.ATK_PERCENT,
                0.331,
                refinement);
    }
}
