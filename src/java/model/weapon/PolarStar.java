package model.weapon;

import java.util.EnumMap;
import java.util.EnumSet;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Polar Star bow with four independently refreshed typed hit stacks.
 */
public class PolarStar extends Weapon implements DamageTriggeredWeaponEffect {
    private static final double STACK_DURATION = 12.0;
    private static final EnumSet<ActionType> STACK_TYPES = EnumSet.of(
            ActionType.NORMAL,
            ActionType.CHARGE,
            ActionType.SKILL,
            ActionType.BURST);

    private final int refinement;
    private final double attackPerStackTier;
    private final EnumMap<ActionType, Double> expirations =
            new EnumMap<>(ActionType.class);

    /** Constructs Polar Star at refinement rank five. */
    public PolarStar() {
        this(5);
    }

    /**
     * Constructs Polar Star at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PolarStar(int refinement) {
        super("Polar Star", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackPerStackTier = 0.075 + 0.025 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
        getStats().set(StatType.SKILL_DMG_BONUS, 0.09 + 0.03 * refinement);
        getStats().set(StatType.BURST_DMG_BONUS, 0.09 + 0.03 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Refreshes the independently keyed stack after positive active-owner damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() == user
                && action.getDamagePercent() > 0.0
                && STACK_TYPES.contains(action.getActionType())) {
            expirations.put(action.getActionType(), currentTime + STACK_DURATION);
        }
    }

    /** Applies Ashen Nightstar ATK for the number of live typed stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int stackCount = 0;
        for (ActionType type : STACK_TYPES) {
            if (currentTime < expirations.getOrDefault(
                    type, Double.NEGATIVE_INFINITY)) {
                stackCount++;
            }
        }
        if (stackCount == 0) {
            return;
        }
        double tierMultiplier = stackCount == 4 ? 4.8 : stackCount;
        stats.add(StatType.ATK_PERCENT, attackPerStackTier * tierMultiplier);
    }
}
