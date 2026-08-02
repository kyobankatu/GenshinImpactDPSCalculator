package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;

/** Uraku Misugiri sword with an active-character Geo damage window. */
public class UrakuMisugiri extends Weapon
        implements DamageListener, SimulatorInitializedWeaponEffect {
    private static final double GEO_WINDOW_DURATION = 15.0;

    private final int refinement;
    private final double normalDamageBonus;
    private final double skillDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double geoWindowUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Uraku Misugiri at refinement rank five. */
    public UrakuMisugiri() {
        this(5);
    }

    /**
     * Constructs Uraku Misugiri at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public UrakuMisugiri(int refinement) {
        super("Uraku Misugiri", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.normalDamageBonus = 0.12 + 0.04 * refinement;
        this.skillDamageBonus = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
        getStats().set(StatType.DEF_PERCENT, 0.15 + 0.05 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds and registers one listener for attributed party damage. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Uraku Misugiri is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addDamageListener(this);
    }

    /** Opens the window after positive Geo damage by the current active member. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != null
                && actor == simulator.getActiveCharacter()
                && action.getElement() == Element.GEO
                && damage > 0.0) {
            geoWindowUntil = time + GEO_WINDOW_DURATION;
        }
    }

    /** Applies base action bonuses and doubles only those bonuses in the Geo window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        double multiplier = currentTime < geoWindowUntil ? 2.0 : 1.0;
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS,
                normalDamageBonus * multiplier);
        stats.add(StatType.SKILL_DMG_BONUS,
                skillDamageBonus * multiplier);
    }
}
