package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SwitchAwareWeaponEffect;
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
import simulation.event.SimpleTimerEvent;

/**
 * King's Squire bow with a timed EM state and one end-of-state Physical proc.
 */
public class KingsSquire extends Weapon
        implements ActionTriggeredWeaponEffect, SwitchAwareWeaponEffect {
    private static final String PROC_NAME = "King's Squire Forest Instruction";
    private static final double DURATION = 12.0;
    private static final double ACTIVATION_COOLDOWN = 20.0;

    private final int refinement;
    private final double elementalMasteryBonus;
    private final double procMotionValue;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;
    private long generation;

    /** Constructs King's Squire at refinement rank five. */
    public KingsSquire() {
        this(5);
    }

    /**
     * Constructs King's Squire at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public KingsSquire(int refinement) {
        super("King's Squire", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryBonus = 40.0 + 20.0 * refinement;
        this.procMotionValue = 0.80 + 0.20 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.ATK_PERCENT, 0.551);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Opens the EM state and schedules its single natural-expiry proc. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        double currentTime = sim.getCurrentTime();
        boolean eligibleAction = request.getKey() == CharacterActionKey.SKILL
                || request.getKey() == CharacterActionKey.BURST;
        if (sim.getActiveCharacter() != user
                || !eligibleAction
                || currentTime < nextActivationTime) {
            return;
        }
        activeUntil = currentTime + DURATION;
        nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
        long scheduledGeneration = ++generation;
        sim.registerEvent(new SimpleTimerEvent(activeUntil, DURATION) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                if (generation == scheduledGeneration) {
                    endState(user, activeSim);
                }
                finish();
            }
        });
    }

    /** Ends the active state immediately when its owner leaves the field. */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
        if (sim.getCurrentTime() < activeUntil) {
            endState(user, sim);
        }
    }

    /** Applies Teachings of the Forest during its half-open state. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }

    private void endState(Character user, CombatSimulator sim) {
        generation++;
        activeUntil = Double.NEGATIVE_INFINITY;
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
