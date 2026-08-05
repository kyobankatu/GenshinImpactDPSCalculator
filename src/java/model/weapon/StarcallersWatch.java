package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Starcaller's Watch with its permanent Elemental Mastery branch.
 *
 * <p>Lv. 90 metadata and the R1-R5 unconditional EM bonus follow pinned gcsim
 * {@code ef41805d}. The current runtime has no general player-shield creation
 * event, so Mirror of Night's active-character DMG window remains inactive
 * rather than being synthesized from unrelated actions.</p>
 */
public final class StarcallersWatch extends Weapon {
    private final int refinement;
    private final double permanentElementalMastery;
    private final double mirrorOfNightDamageBonus;

    /** Constructs Starcaller's Watch at refinement rank five. */
    public StarcallersWatch() {
        this(5);
    }

    /**
     * Constructs Starcaller's Watch at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public StarcallersWatch(int refinement) {
        super("Starcaller's Watch", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Starcaller's Watch refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        permanentElementalMastery = 75.0 + 25.0 * refinement;
        mirrorOfNightDamageBonus = 0.21 + 0.07 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 265.0);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Elemental Mastery bonus. */
    public double getPermanentElementalMastery() {
        return permanentElementalMastery;
    }

    /** Returns the source-backed but currently inactive Mirror DMG value. */
    public double getMirrorOfNightDamageBonus() {
        return mirrorOfNightDamageBonus;
    }

    /** Applies only the representable permanent Elemental Mastery branch. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ELEMENTAL_MASTERY,
                permanentElementalMastery);
    }
}
