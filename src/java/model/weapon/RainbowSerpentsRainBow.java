package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Rainbow Serpent's Rain Bow with an off-field-hit ATK window.
 */
public class RainbowSerpentsRainBow extends Weapon
        implements DamageTriggeredWeaponEffect {
    private static final double DURATION = 8.0;

    private final int refinement;
    private final double attackBonus;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Rainbow Serpent's Rain Bow at refinement rank five. */
    public RainbowSerpentsRainBow() {
        this(5);
    }

    /**
     * Constructs Rainbow Serpent's Rain Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RainbowSerpentsRainBow(int refinement) {
        super("Rainbow Serpent's Rain Bow", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackBonus = 0.21 + 0.07 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Refreshes Astral Whispers after positive off-field damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() > 0.0 && sim.getActiveCharacter() != user) {
            activeUntil = currentTime + DURATION;
        }
    }

    /** Applies the off-field-triggered ATK bonus before exact expiry. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
        }
    }
}
