package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Rust with Lv. 90 stats and refinement-aware Normal/Charged damage modifiers.
 */
public class Rust extends Weapon {
    private final int refinement;
    private final double normalDamageBonus;

    /**
     * Constructs an R5 Rust.
     */
    public Rust() {
        this(5);
    }

    /**
     * Constructs Rust at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Rust(int refinement) {
        super("Rust", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.normalDamageBonus = 0.30 + 0.10 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
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
     * Applies Rapid Firing's Normal bonus and fixed Charged penalty.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, normalDamageBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, -0.10);
    }
}
