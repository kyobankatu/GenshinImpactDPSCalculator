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
 * Fading Twilight bow with a hit-driven three-state damage cycle.
 */
public class FadingTwilight extends Weapon implements DamageTriggeredWeaponEffect {
    private static final double STATE_COOLDOWN = 7.0;

    private final int refinement;
    private final double[] stateBonuses;
    private int stateIndex;
    private double nextStateTime = Double.NEGATIVE_INFINITY;

    /** Constructs Fading Twilight at refinement rank five. */
    public FadingTwilight() {
        this(5);
    }

    /**
     * Constructs Fading Twilight at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FadingTwilight(int refinement) {
        super("Fading Twilight", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.stateBonuses = new double[] {
                0.045 + 0.015 * refinement,
                0.075 + 0.025 * refinement,
                0.105 + 0.035 * refinement
        };
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.306);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Advances to the next state after a positive hit at exact seven-second CT. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() <= 0.0 || currentTime < nextStateTime) {
            return;
        }
        stateIndex = (stateIndex + 1) % stateBonuses.length;
        nextStateTime = currentTime + STATE_COOLDOWN;
    }

    /** Applies the current Evengleam, Afterglow, or Dawnblaze damage bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DMG_BONUS_ALL, stateBonuses[stateIndex]);
    }
}
