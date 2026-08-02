package model.weapon;

import java.util.EnumSet;

import model.entity.ActionTriggeredWeaponEffect;
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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Skyward Pride claymore with a post-Burst eight-blade Physical proc state.
 */
public class SkywardPride extends Weapon
        implements ActionTriggeredWeaponEffect, DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "Skyward Pride Vacuum Blade";
    private static final double STATE_DURATION = 20.0;
    private static final int MAX_BLADES = 8;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS =
            EnumSet.of(ActionType.NORMAL, ActionType.CHARGE);

    private final int refinement;
    private final double procMotionValue;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private int remainingBlades;

    /** Constructs Skyward Pride at refinement rank five. */
    public SkywardPride() {
        this(5);
    }

    /**
     * Constructs Skyward Pride at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SkywardPride(int refinement) {
        super("Skyward Pride", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.60 + 0.20 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.368);
        getStats().set(StatType.DMG_BONUS_ALL, 0.06 + 0.02 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Replaces the active state with eight blades after active-owner Burst use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() == user
                && request.getKey() == CharacterActionKey.BURST) {
            activeUntil = sim.getCurrentTime() + STATE_DURATION;
            remainingBlades = MAX_BLADES;
        }
    }

    /** Generates one nonrecursive Physical blade after each eligible hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || currentTime >= activeUntil
                || remainingBlades <= 0
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())) {
            return;
        }
        remainingBlades--;
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
