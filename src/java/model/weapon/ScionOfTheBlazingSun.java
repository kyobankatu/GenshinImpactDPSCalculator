package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Scion of the Blazing Sun bow with an immediate Sunfire Arrow and Heartsearer.
 *
 * <p>The simulator currently models one enemy, so Heartsearer is represented as
 * an owner-wide Charged Attack bonus while its affected-target window is active.</p>
 */
public class ScionOfTheBlazingSun extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final String PROC_NAME =
            "Scion of the Blazing Sun Sunfire Arrow";
    private static final double ACTIVATION_COOLDOWN = 10.0;
    private static final double HEARTSEARER_DURATION = 10.0;

    private final int refinement;
    private final double procMotionValue;
    private final double chargedDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double heartsearerUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs Scion of the Blazing Sun at refinement rank five. */
    public ScionOfTheBlazingSun() {
        this(5);
    }

    /**
     * Constructs Scion of the Blazing Sun at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ScionOfTheBlazingSun(int refinement) {
        super("Scion of the Blazing Sun", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.procMotionValue = 0.45 + 0.15 * refinement;
        this.chargedDamageBonus = 0.21 + 0.07 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.CRIT_RATE, 0.184);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and simulator that may activate Heartsearer. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Scion of the Blazing Sun is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Resolves Sunfire Arrow before opening Heartsearer after an eligible hit.
     */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim != simulator
                || user != owner
                || sim.getActiveCharacter() != owner
                || action.getDamagePercent() <= 0.0
                || action.getActionType() != ActionType.CHARGE
                || action.getName().equals(PROC_NAME)
                || currentTime < nextActivationTime) {
            return;
        }

        nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
        AttackAction proc = new AttackAction(
                PROC_NAME,
                procMotionValue,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), proc);
        heartsearerUntil = currentTime + HEARTSEARER_DURATION;
    }

    /** Applies Heartsearer's affected-target Charged Attack damage bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < heartsearerUntil) {
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, chargedDamageBonus);
        }
    }
}
