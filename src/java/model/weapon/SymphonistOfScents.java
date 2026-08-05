package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Symphonist of Scents with its field-aware unconditional ATK branches.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. The owner gains one ATK tier at all times and a second
 * equal tier while off field. The healing-initiated owner/recipient ATK window
 * remains inactive because the runtime has no typed player-heal event.</p>
 */
public final class SymphonistOfScents extends Weapon implements
        SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double attackTier;
    private final double unavailableHealingAttackBonus;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Symphonist of Scents at refinement rank five. */
    public SymphonistOfScents() {
        this(5);
    }

    /**
     * Constructs Symphonist of Scents at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SymphonistOfScents(int refinement) {
        super("Symphonist of Scents", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Symphonist of Scents refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackTier = 0.09 + 0.03 * refinement;
        unavailableHealingAttackBonus = 0.24 + 0.08 * refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns one unconditional or off-field ATK tier. */
    public double getAttackTier() {
        return attackTier;
    }

    /** Returns the source-backed but inactive healing-window ATK value. */
    public double getUnavailableHealingAttackBonus() {
        return unavailableHealingAttackBonus;
    }

    /** Returns whether the bound owner is currently off field. */
    public boolean isOwnerOffField() {
        return simulator != null
                && owner != null
                && simulator.getActiveCharacter() != owner;
    }

    /** Applies one permanent ATK tier and a second live off-field tier. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackTier);
        if (isOwnerOffField()) {
            stats.add(StatType.ATK_PERCENT, attackTier);
        }
    }

    /** Binds the field-state lookup to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Symphonist of Scents owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Symphonist of Scents is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Symphonist of Scents equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }
}
