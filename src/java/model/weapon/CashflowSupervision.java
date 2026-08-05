package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Cashflow Supervision with its unconditional ATK branch. */
public final class CashflowSupervision extends Weapon {
    private final int refinement;
    private final double attackBonus;
    private final double normalDamagePerStack;
    private final double chargedDamagePerStack;
    private final double attackSpeedBonus;

    /** Constructs Cashflow Supervision at refinement rank five. */
    public CashflowSupervision() {
        this(5);
    }

    /** Constructs Cashflow Supervision at the selected refinement rank. */
    public CashflowSupervision(int refinement) {
        super("Cashflow Supervision", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Cashflow Supervision refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackBonus = 0.12 + 0.04 * refinement;
        normalDamagePerStack = attackBonus;
        chargedDamagePerStack = 0.105 + 0.035 * refinement;
        attackSpeedBonus = 0.06 + 0.02 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the represented unconditional ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the inactive HP-change Normal bonus per stack. */
    public double getNormalDamagePerStack() {
        return normalDamagePerStack;
    }

    /** Returns the inactive HP-change Charged bonus per stack. */
    public double getChargedDamagePerStack() {
        return chargedDamagePerStack;
    }

    /** Returns the inactive three-stack Normal ATK SPD value. */
    public double getAttackSpeedBonus() {
        return attackSpeedBonus;
    }

    /** Applies only the representable unconditional ATK branch. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
    }
}
