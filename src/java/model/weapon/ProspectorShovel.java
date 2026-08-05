package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Prospector's Shovel with separate standard and Lunar reaction bonuses.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned KQM TCL
 * {@code 80ba6241} and gcsim {@code ef41805d}. The base Electro-Charged and
 * Lunar-Charged bonuses are unconditional. Ascendant Gleam adds one more copy
 * of the refinement-scaled Lunar-Charged bonus.</p>
 */
public final class ProspectorShovel extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double electroChargedDamageBonus;
    private final double lunarChargedDamageBonus;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Prospector's Shovel at refinement rank five. */
    public ProspectorShovel() {
        this(5);
    }

    /**
     * Constructs Prospector's Shovel at the selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ProspectorShovel(int refinement) {
        super("Prospector's Shovel", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Prospector's Shovel refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        lunarChargedDamageBonus = 0.09 + 0.03 * refinement;
        electroChargedDamageBonus = lunarChargedDamageBonus * 4.0;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Electro-Charged DMG bonus. */
    public double getElectroChargedDamageBonus() {
        return electroChargedDamageBonus;
    }

    /** Returns each base or Ascendant Lunar-Charged DMG copy. */
    public double getLunarChargedDamageBonus() {
        return lunarChargedDamageBonus;
    }

    /** Applies both base bonuses and the conditional Ascendant Lunar copy. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(
                StatType.ELECTRO_CHARGED_DMG_BONUS,
                electroChargedDamageBonus);
        stats.add(
                StatType.LUNAR_CHARGED_DMG_BONUS,
                lunarChargedDamageBonus);
        if (simulator != null
                && simulator.getMoonsign()
                        == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            stats.add(
                    StatType.LUNAR_CHARGED_DMG_BONUS,
                    lunarChargedDamageBonus);
        }
    }

    /** Binds the Ascendant condition to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Prospector's Shovel owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Prospector's Shovel is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Prospector's Shovel equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }
}
