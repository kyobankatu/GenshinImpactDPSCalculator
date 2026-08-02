package model.weapon;

import mechanics.reaction.ReactionResult;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Emerald Orb with Lv. 90 stats and refinement-aware Rapids reactions.
 */
public class EmeraldOrb extends ReactionWindowWeapon {
    /**
     * Constructs an R5 Emerald Orb.
     */
    public EmeraldOrb() {
        this(5);
    }

    /**
     * Constructs Emerald Orb at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EmeraldOrb(int refinement) {
        super("Emerald Orb", WeaponType.CATALYST, 448.0,
                StatType.ELEMENTAL_MASTERY, 94.0, refinement, 5,
                EmeraldOrb::isRapidsReaction,
                0.15 + 0.05 * refinement,
                12.0,
                1,
                StatType.ATK_PERCENT);
    }

    private static boolean isRapidsReaction(ReactionResult result) {
        switch (result.getKind()) {
            case VAPORIZE:
            case ELECTRO_CHARGED:
            case FROZEN:
            case BLOOM:
            case LUNAR_CHARGED:
            case LUNAR_BLOOM:
                return true;
            case SWIRL:
                return result.getRelatedElement() == Element.HYDRO;
            default:
                return false;
        }
    }
}
