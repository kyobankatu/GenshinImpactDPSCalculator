package model.weapon;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.DoubleSupplier;

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
 * Shared immediate Physical weapon proc with typed hit, chance, and cooldown gates.
 */
public abstract class DirectDamageProcWeapon extends Weapon
        implements DamageTriggeredWeaponEffect {
    private final int refinement;
    private final EnumSet<ActionType> eligibleActions;
    private final double procChance;
    private final double procCooldown;
    private final double procMotionValue;
    private final DoubleSupplier procDraw;
    private final String procActionName;

    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one direct Physical proc weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param eligibleActions action types that may trigger the proc
     * @param procChance probability in the range (0, 1]
     * @param procCooldown cooldown after a successful proc in seconds
     * @param procMotionValue extra Physical damage as a fraction of ATK
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    protected DirectDamageProcWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            EnumSet<ActionType> eligibleActions,
            double procChance,
            double procCooldown,
            double procMotionValue,
            DoubleSupplier procDraw) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Direct proc weapon refinement must be between 1 and 5");
        }
        if (eligibleActions.isEmpty()
                || procChance <= 0.0
                || procChance > 1.0
                || procCooldown <= 0.0
                || procMotionValue <= 0.0) {
            throw new IllegalArgumentException("Direct proc weapon definition is invalid");
        }
        this.refinement = refinement;
        this.eligibleActions = EnumSet.copyOf(eligibleActions);
        this.procChance = procChance;
        this.procCooldown = procCooldown;
        this.procMotionValue = procMotionValue;
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        this.procActionName = name + " Proc";
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Resolves an eligible successful proc immediately through the damage pipeline.
     *
     * @param user weapon owner who dealt the triggering hit
     * @param action resolved triggering action
     * @param currentTime triggering hit time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() <= 0.0
                || action.getName().equals(procActionName)
                || !eligibleActions.contains(action.getActionType())
                || currentTime < nextProcTime) {
            return;
        }
        if (procDraw.getAsDouble() >= procChance) {
            return;
        }
        nextProcTime = currentTime + procCooldown;
        AttackAction proc = new AttackAction(
                procActionName,
                procMotionValue,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(user.getCharacterId(), proc);
    }
}
