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
 * End of the Line bow with a three-proc post-Skill Flowrider state.
 */
public class EndOfTheLine extends Weapon
        implements ActionTriggeredWeaponEffect, DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "End of the Line Flowrider";
    private static final double ACTIVATION_COOLDOWN = 12.0;
    private static final double DURATION = 15.0;
    private static final double PROC_COOLDOWN = 2.0;
    private static final int MAX_PROCS = 3;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS = EnumSet.of(
            ActionType.NORMAL,
            ActionType.CHARGE,
            ActionType.PLUNGE,
            ActionType.SKILL,
            ActionType.BURST);

    private final int refinement;
    private final double procMotionValue;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;
    private double nextProcTime = Double.NEGATIVE_INFINITY;
    private int remainingProcs;

    /** Constructs End of the Line at refinement rank five. */
    public EndOfTheLine() {
        this(5);
    }

    /**
     * Constructs End of the Line at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EndOfTheLine(int refinement) {
        super("End of the Line", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.60 + 0.20 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Opens a fresh three-proc Flowrider state at exact activation CT. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        if (sim.getActiveCharacter() == user
                && request.getKey() == CharacterActionKey.SKILL
                && currentTime >= nextActivationTime) {
            activeUntil = currentTime + DURATION;
            nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
            nextProcTime = currentTime;
            remainingProcs = MAX_PROCS;
        }
    }

    /** Generates one nonrecursive Physical Flowrider proc at exact two-second CT. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || currentTime >= activeUntil
                || currentTime < nextProcTime
                || remainingProcs <= 0
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())) {
            return;
        }
        remainingProcs--;
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
