package model.weapon;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
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

/** Engulfing Lightning with final-ER ATK conversion and Burst ER. */
public class EngulfingLightning extends Weapon
        implements SimulatorInitializedWeaponEffect, ActionTriggeredWeaponEffect {
    private static final double BURST_ER_DURATION = 12.0;

    private final int refinement;
    private final double burstEnergyRecharge;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Engulfing Lightning at refinement rank five. */
    public EngulfingLightning() {
        this(5);
    }

    /**
     * Constructs Engulfing Lightning at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EngulfingLightning(int refinement) {
        super("Engulfing Lightning", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.burstEnergyRecharge = 0.25 + 0.05 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.551);
        getStats().set(
                StatType.ENERGY_RECHARGE_TO_ATK_PERCENT_RATIO,
                0.21 + 0.07 * refinement);
        getStats().set(
                StatType.ENERGY_RECHARGE_TO_ATK_PERCENT_CAP,
                0.70 + 0.10 * refinement);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the ordinary ER granted for 12 seconds after Burst use. */
    public double getBurstEnergyRecharge() {
        return burstEnergyRecharge;
    }

    /** Binds this mutable weapon to exactly one owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Engulfing Lightning is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies or refreshes the post-gate Burst ER window. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (owner == null
                || simulator == null
                || user != owner
                || sim != simulator
                || request == null
                || request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        owner.removeBuff(BuffId.ENGULFING_LIGHTNING_ER);
        owner.addBuff(new SimpleBuff(
                "Engulfing Lightning: Burst Energy Recharge",
                BuffId.ENGULFING_LIGHTNING_ER,
                BURST_ER_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(
                        StatType.ENERGY_RECHARGE,
                        burstEnergyRecharge)).sourcedBy(owner.getCharacterId()));
    }
}
