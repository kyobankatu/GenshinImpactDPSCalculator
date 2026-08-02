package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Reliquary of Truth catalyst with intersecting Skill and Lunar-Bloom windows. */
public class ReliquaryOfTruth extends Weapon
        implements ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        CombatSimulator.ReactionListener {
    private static final double SKILL_WINDOW_DURATION = 12.0;
    private static final double LUNAR_WINDOW_DURATION = 4.0;
    private static final double INTERSECTION_MULTIPLIER = 1.5;

    private final int refinement;
    private final double skillElementalMastery;
    private final double lunarCriticalDamage;
    private Character owner;
    private CombatSimulator simulator;
    private double skillActiveUntil = Double.NEGATIVE_INFINITY;
    private double lunarActiveUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Reliquary of Truth at refinement rank five. */
    public ReliquaryOfTruth() {
        this(5);
    }

    /**
     * Constructs Reliquary of Truth at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ReliquaryOfTruth(int refinement) {
        super("Reliquary of Truth", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.skillElementalMastery = 60.0 + 20.0 * refinement;
        this.lunarCriticalDamage = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
        getStats().set(StatType.CRIT_RATE, 0.06 + 0.02 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and registers one attributed Lunar-Bloom listener. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Reliquary of Truth is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Opens the 12-second EM window before active-owner Skill resolution. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim == simulator
                && user == owner
                && sim.getActiveCharacter() == owner
                && request.getKey() == CharacterActionKey.SKILL) {
            skillActiveUntil = sim.getCurrentTime() + SKILL_WINDOW_DURATION;
        }
    }

    /** Opens the four-second CRIT DMG window for active-owner Lunar-Bloom. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim == simulator
                && source == owner
                && sim.getActiveCharacter() == owner
                && result.getKind() == ReactionResult.Kind.LUNAR_BLOOM) {
            lunarActiveUntil = time + LUNAR_WINDOW_DURATION;
        }
    }

    /** Applies each active result, scaling both by 1.5 while they overlap. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        boolean skillActive = currentTime < skillActiveUntil;
        boolean lunarActive = currentTime < lunarActiveUntil;
        double multiplier = skillActive && lunarActive
                ? INTERSECTION_MULTIPLIER : 1.0;
        if (skillActive) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    skillElementalMastery * multiplier);
        }
        if (lunarActive) {
            stats.add(StatType.CRIT_DMG,
                    lunarCriticalDamage * multiplier);
        }
    }
}
