package model.weapon;

import mechanics.reaction.ReactionResult;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Dark Iron Sword with Lv. 90 stats and its fixed Overloaded passive.
 */
public class DarkIronSword extends ReactionWindowWeapon {
    /**
     * Constructs the fixed-R1 Dark Iron Sword.
     */
    public DarkIronSword() {
        super("Dark Iron Sword", WeaponType.SWORD, 401.0,
                StatType.ELEMENTAL_MASTERY, 141.0, 1, 1,
                DarkIronSword::isOverloadedReaction,
                0.20,
                12.0,
                1,
                StatType.ATK_PERCENT);
    }

    private static boolean isOverloadedReaction(ReactionResult result) {
        switch (result.getKind()) {
            case OVERLOAD:
            case OVERLOADED:
            case SUPERCONDUCT:
            case ELECTRO_CHARGED:
            case QUICKEN:
            case AGGRAVATE:
            case HYPERBLOOM:
            case LUNAR_CHARGED:
                return true;
            case SWIRL:
                return result.getRelatedElement() == Element.ELECTRO;
            default:
                return false;
        }
    }
}
