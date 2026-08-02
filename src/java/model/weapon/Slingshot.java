package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Slingshot with Lv. 90 metadata and refinement-aware Slingshot damage.
 *
 * <p>The combat resolver resolves attacks immediately and attack actions have
 * no projectile flight time. Supported Normal and Charged hits therefore land
 * within 0.3 seconds and receive {@code 36/42/48/54/60%} damage at R1-R5. The
 * canonical {@code -10%} branch for longer flight times is unavailable until
 * the runtime represents launch and impact timing separately.</p>
 */
public class Slingshot extends Weapon {
    private final int refinement;
    private final double closeRangeDamageBonus;

    /** Constructs an R5 Slingshot. */
    public Slingshot() {
        this(5);
    }

    /**
     * Constructs Slingshot at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Slingshot(int refinement) {
        super("Slingshot", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.closeRangeDamageBonus = 0.30 + 0.06 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 354.0);
        getStats().set(StatType.CRIT_RATE, 0.312);
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
     * Applies the within-0.3-second bonus to Normal and Charged Attack damage.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, closeRangeDamageBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, closeRangeDamageBonus);
    }
}
