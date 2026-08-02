package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Mistsplitter Reforged sword with hit, Burst, and live-Energy emblem stacks.
 */
public class MistsplitterReforged extends Weapon
        implements ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect {
    private static final double HIT_STACK_DURATION = 5.0;
    private static final double BURST_STACK_DURATION = 10.0;

    private final int refinement;
    private final double emblemTier;
    private Character owner;
    private CombatSimulator simulator;
    private double hitStackUntil = Double.NEGATIVE_INFINITY;
    private double burstStackUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Mistsplitter Reforged at refinement rank five. */
    public MistsplitterReforged() {
        this(5);
    }

    /**
     * Constructs Mistsplitter Reforged at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MistsplitterReforged(int refinement) {
        super("Mistsplitter Reforged", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.emblemTier = 0.06 + 0.02 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
        double elementalBonus = 0.09 + 0.03 * refinement;
        for (Element element : Element.values()) {
            if (element != Element.PHYSICAL) {
                getStats().set(element.getBonusStatType(), elementalBonus);
            }
        }
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner whose element and Energy determine live emblem effects. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Mistsplitter Reforged is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Opens the ten-second emblem stack before active-owner Burst resolution. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && request.getKey() == CharacterActionKey.BURST) {
            burstStackUntil = sim.getCurrentTime() + BURST_STACK_DURATION;
        }
    }

    /** Refreshes the five-second stack after positive elemental Normal/Charged damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        boolean eligibleType = action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.CHARGE;
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && eligibleType
                && action.getElement() != Element.PHYSICAL
                && action.getDamagePercent() > 0.0) {
            hitStackUntil = currentTime + HIT_STACK_DURATION;
        }
    }

    /** Applies the owner-element emblem tier for the three independent sources. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null || owner.getElement() == Element.PHYSICAL) {
            return;
        }
        int stackCount = 0;
        if (currentTime < hitStackUntil) {
            stackCount++;
        }
        if (currentTime < burstStackUntil) {
            stackCount++;
        }
        if (owner.getCurrentEnergy() < owner.getMaxEnergy()) {
            stackCount++;
        }
        if (stackCount == 0) {
            return;
        }
        double tierMultiplier = stackCount == 3 ? 3.5 : stackCount;
        stats.add(owner.getElement().getBonusStatType(), emblemTier * tierMultiplier);
    }
}
