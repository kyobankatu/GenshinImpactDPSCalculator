package model.weapon;

import mechanics.reaction.ReactionResult;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Mappa Mare with Lv. 90 stats and refinement-aware Infusion Scroll stacks.
 */
public class MappaMare extends ReactionWindowWeapon {
    /**
     * Constructs an R5 Mappa Mare.
     */
    public MappaMare() {
        this(5);
    }

    /**
     * Constructs Mappa Mare at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MappaMare(int refinement) {
        super("Mappa Mare", WeaponType.CATALYST, 565.0,
                StatType.ELEMENTAL_MASTERY, 110.0, refinement, 5,
                result -> result.getKind() != ReactionResult.Kind.NONE,
                0.06 + 0.02 * refinement,
                10.0,
                2,
                StatType.PYRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                StatType.ANEMO_DMG_BONUS,
                StatType.ELECTRO_DMG_BONUS,
                StatType.DENDRO_DMG_BONUS,
                StatType.CRYO_DMG_BONUS,
                StatType.GEO_DMG_BONUS);
    }
}
