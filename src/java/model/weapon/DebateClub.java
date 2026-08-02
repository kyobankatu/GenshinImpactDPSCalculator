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
 * Debate Club claymore with a Skill-opened Blunt Conclusion proc window.
 */
public class DebateClub extends Weapon
        implements ActionTriggeredWeaponEffect, DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "Debate Club Blunt Conclusion";
    private static final double WINDOW_DURATION = 15.0;
    private static final double PROC_COOLDOWN = 3.0;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS =
            EnumSet.of(ActionType.NORMAL, ActionType.CHARGE);

    private final int refinement;
    private final double procMotionValue;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /** Constructs Debate Club at refinement rank five. */
    public DebateClub() {
        this(5);
    }

    /**
     * Constructs Debate Club at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public DebateClub(int refinement) {
        super("Debate Club", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.45 + 0.15 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 401.0);
        getStats().set(StatType.ATK_PERCENT, 0.352);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Opens or refreshes Blunt Conclusion when the active owner uses a Skill. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() == user
                && request.getKey() == CharacterActionKey.SKILL) {
            activeUntil = sim.getCurrentTime() + WINDOW_DURATION;
        }
    }

    /** Resolves Blunt Conclusion from an eligible hit during its active window. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || currentTime >= activeUntil
                || currentTime < nextProcTime
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())) {
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
