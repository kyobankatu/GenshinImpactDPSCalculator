package model.weapon;

import java.util.Objects;
import java.util.function.DoubleSupplier;

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
 * Shared Composed implementation for the Sacrificial weapon family.
 *
 * <p>
 * Positive direct Elemental Skill damage may reset the owner's applicable Skill
 * cooldown. Chance and internal cooldown follow refinement ranks 1-5, while an
 * injectable draw source keeps optimizer and regression scenarios reproducible.
 */
public abstract class SacrificialWeapon extends Weapon implements DamageTriggeredWeaponEffect {
    private static final double BASE_PROC_CHANCE = 0.30;
    private static final double PROC_CHANCE_PER_REFINEMENT = 0.10;
    private static final double[] PROC_COOLDOWNS = { 0.0, 30.0, 26.0, 22.0, 19.0, 16.0 };

    private final int refinement;
    private final double procChance;
    private final double procCooldown;
    private final DoubleSupplier procDraw;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Lv. 90 Sacrificial family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     * @throws IllegalArgumentException when refinement is outside 1-5
     * @throws NullPointerException when {@code procDraw} is null
     */
    protected SacrificialWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            DoubleSupplier procDraw) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Sacrificial refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procChance = BASE_PROC_CHANCE + PROC_CHANCE_PER_REFINEMENT * refinement;
        this.procCooldown = PROC_COOLDOWNS[refinement];
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
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
     * Attempts Composed after eligible Elemental Skill damage.
     *
     * @param user weapon owner who dealt the Skill damage
     * @param action resolved damage action
     * @param currentTime damage time in simulation seconds
     * @param sim active combat simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getActionType() != ActionType.SKILL
                || action.getDamagePercent() <= 0.0
                || currentTime < nextProcTime) {
            return;
        }
        if (procDraw.getAsDouble() >= procChance) {
            return;
        }

        user.resetSkillCooldown(currentTime);
        nextProcTime = currentTime + procCooldown;
        if (sim.isLoggingEnabled()) {
            System.out.println("   [Weapon] " + getName() + " reset Skill cooldown");
        }
    }
}
