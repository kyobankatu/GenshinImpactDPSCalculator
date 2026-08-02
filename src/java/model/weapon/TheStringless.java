package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * The Stringless with Lv. 90 stats and refinement-aware Skill/Burst damage.
 */
public class TheStringless extends Weapon {
    private final int refinement;
    private final double actionDamageBonus;

    /**
     * Constructs an R5 The Stringless.
     */
    public TheStringless() {
        this(5);
    }

    /**
     * Constructs The Stringless at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheStringless(int refinement) {
        super("The Stringless", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.actionDamageBonus = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 165.0);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Applies Arrowless Song to Skill and Burst damage only.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.SKILL_DMG_BONUS, actionDamageBonus);
        stats.add(StatType.BURST_DMG_BONUS, actionDamageBonus);
    }
}
