package model.weapon;

import mechanics.buff.BuffId;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Xiphos' Moonlight sword with periodic EM-to-Energy-Recharge team conversion.
 */
public class XiphosMoonlight extends TimedElementalMasteryTeamStatWeapon {
    /** Constructs Xiphos' Moonlight at refinement rank five. */
    public XiphosMoonlight() {
        this(5);
    }

    /**
     * Constructs Xiphos' Moonlight at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public XiphosMoonlight(int refinement) {
        super("Xiphos' Moonlight", WeaponType.SWORD, refinement,
                "Jinni's Whisper",
                BuffId.XIPHOS_MOONLIGHT_JINNIS_WHISPER,
                StatType.NON_CONVERTING_ENERGY_RECHARGE,
                0.00027 + 0.00009 * refinement);
    }
}
