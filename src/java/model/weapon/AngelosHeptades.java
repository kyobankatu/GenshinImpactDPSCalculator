package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Angelos Heptades with its unconditional Pathfinder's Light ATK branch.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. The runtime has no typed player-shield creation event, so
 * the shield-triggered Energy and dynamic party damage branches remain
 * inactive rather than being approximated from unrelated actions.</p>
 */
public final class AngelosHeptades extends Weapon {
    private final int refinement;
    private final double permanentAttackBonus;
    private final double shieldEnergyRecovery;
    private final double damageBonusPerThousandAttack;
    private final double maximumDamageBonus;

    /** Constructs Angelos Heptades at refinement rank five. */
    public AngelosHeptades() {
        this(5);
    }

    /**
     * Constructs Angelos Heptades at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AngelosHeptades(int refinement) {
        super("Angelos Heptades", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Angelos Heptades refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        permanentAttackBonus = 0.09 + 0.03 * refinement;
        shieldEnergyRecovery = 13.0 + refinement;
        damageBonusPerThousandAttack = 0.07 + 0.03 * refinement;
        maximumDamageBonus = 0.18 + 0.08 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 741.0);
        getStats().set(StatType.ATK_PERCENT, 0.165);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the represented unconditional ATK bonus. */
    public double getPermanentAttackBonus() {
        return permanentAttackBonus;
    }

    /** Returns the source-backed but inactive shield Energy value. */
    public double getShieldEnergyRecovery() {
        return shieldEnergyRecovery;
    }

    /** Returns the inactive damage bonus per 1,000 owner ATK. */
    public double getDamageBonusPerThousandAttack() {
        return damageBonusPerThousandAttack;
    }

    /** Returns the inactive dynamic party damage cap. */
    public double getMaximumDamageBonus() {
        return maximumDamageBonus;
    }

    /** Applies only the representable permanent ATK branch. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, permanentAttackBonus);
    }
}
