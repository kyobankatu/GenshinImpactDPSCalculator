package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Song of the Vigil with reaction Energy recovery and a Stellar ATK window. */
public final class SongOfTheVigil extends VersionSevenReactionWindowWeapon {
    /** Constructs Song of the Vigil at refinement rank five. */
    public SongOfTheVigil() {
        this(5);
    }

    /**
     * Constructs Song of the Vigil at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SongOfTheVigil(int refinement) {
        super(
                "Song of the Vigil",
                WeaponType.POLEARM,
                565.0,
                StatType.ELEMENTAL_MASTERY,
                110.0,
                refinement,
                null,
                0.0,
                0.15 + 0.05 * refinement,
                3.0 + refinement,
                9.0,
                StatType.ATK_PERCENT);
    }
}
