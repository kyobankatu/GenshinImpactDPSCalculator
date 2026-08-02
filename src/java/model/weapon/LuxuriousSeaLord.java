package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Luxurious Sea-Lord claymore with Burst DMG and an active-owner tuna proc.
 */
public class LuxuriousSeaLord extends Weapon
        implements DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "Luxurious Sea-Lord Tuna";
    private static final double PROC_COOLDOWN = 15.0;

    private final int refinement;
    private final double procMotionValue;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /** Constructs Luxurious Sea-Lord at refinement rank five. */
    public LuxuriousSeaLord() {
        this(5);
    }

    /**
     * Constructs Luxurious Sea-Lord at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LuxuriousSeaLord(int refinement) {
        super("Luxurious Sea-Lord", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.75 + 0.25 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.ATK_PERCENT, 0.551);
        getStats().set(StatType.BURST_DMG_BONUS, 0.09 + 0.03 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Resolves the tuna proc after eligible active-owner Burst damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        boolean burstDamage = action.getActionType() == ActionType.BURST
                || action.isCountsAsBurstDmg();
        if (sim.getActiveCharacter() != user
                || !burstDamage
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || currentTime < nextProcTime) {
            return;
        }
        nextProcTime = currentTime + PROC_COOLDOWN;
        AttackAction proc = new AttackAction(
                PROC_NAME,
                procMotionValue,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(user.getCharacterId(), proc);
    }
}
