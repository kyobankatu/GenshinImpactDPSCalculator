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
 * Shared refinement-aware Frost Burial Physical proc.
 */
public abstract class FrostBurialWeapon extends Weapon
        implements DamageTriggeredWeaponEffect {
    private static final double PROC_COOLDOWN = 10.0;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS =
            EnumSet.of(ActionType.NORMAL, ActionType.CHARGE);

    private final int refinement;
    private final double procChance;
    private final double normalMotionValue;
    private final double cryoMotionValue;
    private final DoubleSupplier procDraw;
    private final String procActionName;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Frost Burial weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    protected FrostBurialWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            DoubleSupplier procDraw) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Frost Burial refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procChance = 0.5 + 0.1 * refinement;
        this.normalMotionValue = 0.65 + 0.15 * refinement;
        this.cryoMotionValue = 1.6 + 0.4 * refinement;
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        this.procActionName = name + " Frost Burial";
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

    /** Resolves an eligible Frost Burial proc through the normal damage pipeline. */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(procActionName)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())
                || currentTime < nextProcTime
                || procDraw.getAsDouble() >= procChance) {
            return;
        }

        nextProcTime = currentTime + PROC_COOLDOWN;
        boolean cryoAffected = sim.getEnemy() != null
                && sim.getEnemy().getAuraUnits(Element.CRYO, currentTime) > 0.0;
        AttackAction proc = new AttackAction(
                procActionName,
                cryoAffected ? cryoMotionValue : normalMotionValue,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(user.getCharacterId(), proc);
    }
}
