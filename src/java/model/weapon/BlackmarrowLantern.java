package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** Blackmarrow Lantern catalyst with live Moonsign Bloom bonuses. */
public class BlackmarrowLantern extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double bloomDamageBonus;
    private final double lunarBloomDamageBonus;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Blackmarrow Lantern at refinement rank five. */
    public BlackmarrowLantern() {
        this(5);
    }

    /**
     * Constructs Blackmarrow Lantern at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackmarrowLantern(int refinement) {
        super("Blackmarrow Lantern", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.bloomDamageBonus = 0.36 + 0.12 * refinement;
        this.lunarBloomDamageBonus = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 221.0);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the live Moonsign state used by Ascendant Gleam. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Blackmarrow Lantern is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies unconditional Bloom/Lunar-Bloom and live Ascendant Lunar-Bloom. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.BLOOM_DMG_BONUS, bloomDamageBonus);
        stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, lunarBloomDamageBonus);
        if (simulator != null
                && simulator.getMoonsign()
                == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            stats.add(StatType.LUNAR_BLOOM_DMG_BONUS,
                    lunarBloomDamageBonus);
        }
    }
}
