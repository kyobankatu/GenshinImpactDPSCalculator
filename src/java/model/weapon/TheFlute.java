package model.weapon;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

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
 * The Flute sword with independently expiring Harmonics and a five-stack proc.
 */
public class TheFlute extends Weapon implements DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "The Flute Chord";
    private static final double HARMONIC_COOLDOWN = 0.5;
    private static final double HARMONIC_DURATION = 30.0;
    private static final int REQUIRED_HARMONICS = 5;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS =
            EnumSet.of(ActionType.NORMAL, ActionType.CHARGE);

    private final int refinement;
    private final double procMotionValue;
    private final Deque<Double> harmonicExpirations = new ArrayDeque<>();
    private double nextHarmonicTime = Double.NEGATIVE_INFINITY;

    /** Constructs The Flute at refinement rank five. */
    public TheFlute() {
        this(5);
    }

    /**
     * Constructs The Flute at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheFlute(int refinement) {
        super("The Flute", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.75 + 0.25 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Gains Harmonics and consumes five to resolve Chord. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())
                || currentTime < nextHarmonicTime) {
            return;
        }
        while (!harmonicExpirations.isEmpty()
                && harmonicExpirations.peekFirst() <= currentTime) {
            harmonicExpirations.removeFirst();
        }
        harmonicExpirations.addLast(currentTime + HARMONIC_DURATION);
        nextHarmonicTime = currentTime + HARMONIC_COOLDOWN;
        if (harmonicExpirations.size() < REQUIRED_HARMONICS) {
            return;
        }

        harmonicExpirations.clear();
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
