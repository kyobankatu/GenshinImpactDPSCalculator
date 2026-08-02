package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * "Ultimate Overlord's Mega Magic Sword" with assistance-aware ATK.
 *
 * <p>The six Melusine assistance objectives are represented as constructor
 * state because quest completion does not change during a combat simulation.</p>
 */
public class UltimateOverlordsMegaMagicSword extends Weapon {
    private static final int MAX_ASSISTED_MELUSINES = 6;

    private final int refinement;
    private final int assistedMelusines;
    private final double attackBonus;

    /** Constructs the R5 weapon with all six Melusine objectives complete. */
    public UltimateOverlordsMegaMagicSword() {
        this(5, MAX_ASSISTED_MELUSINES);
    }

    /**
     * Constructs the weapon with a selected refinement and assistance state.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param assistedMelusines completed Melusine objectives in the inclusive range 0-6
     */
    public UltimateOverlordsMegaMagicSword(int refinement, int assistedMelusines) {
        super("\"Ultimate Overlord's Mega Magic Sword\"", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        if (assistedMelusines < 0 || assistedMelusines > MAX_ASSISTED_MELUSINES) {
            throw new IllegalArgumentException(
                    "Assisted Melusines must be between 0 and 6");
        }
        this.refinement = refinement;
        this.assistedMelusines = assistedMelusines;
        double baseAttackBonus = 0.12 + 0.03 * (refinement - 1);
        this.attackBonus = baseAttackBonus
                * (1.0 + assistedMelusines / (double) MAX_ASSISTED_MELUSINES);
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.306);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the number of completed Melusine assistance objectives. */
    public int getAssistedMelusines() {
        return assistedMelusines;
    }

    /** Applies the base and assistance-derived ATK bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
    }
}
