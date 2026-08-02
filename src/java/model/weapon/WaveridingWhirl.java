package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Waveriding Whirl catalyst with a live-party post-Skill Max HP window.
 *
 * <p>Swimming Stamina Consumption is outside the combat simulator and is not
 * represented by this weapon.</p>
 */
public class WaveridingWhirl extends Weapon
        implements ActionTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final int MAX_HYDRO_ADDITIONS = 2;
    private static final double BUFF_DURATION = 10.0;
    private static final double ACTIVATION_COOLDOWN = 15.0;

    private final int refinement;
    private final double baseHpBonus;
    private final double hydroCharacterHpBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs Waveriding Whirl at refinement rank five. */
    public WaveridingWhirl() {
        this(5);
    }

    /**
     * Constructs Waveriding Whirl at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WaveridingWhirl(int refinement) {
        super("Waveriding Whirl", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.baseHpBonus = 0.15 + 0.05 * refinement;
        this.hydroCharacterHpBonus = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.613);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Binds the owner and live party used by the Hydro-member calculation.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Waveriding Whirl is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Opens the Max HP window on an eligible active-owner Skill use.
     *
     * @param user character performing the action
     * @param request typed action request
     * @param sim active combat simulator
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (sim != simulator
                || user != owner
                || sim.getActiveCharacter() != owner
                || request.getKey() != CharacterActionKey.SKILL
                || sim.getCurrentTime() < nextActivationTime) {
            return;
        }
        activeUntil = sim.getCurrentTime() + BUFF_DURATION;
        nextActivationTime = sim.getCurrentTime() + ACTIVATION_COOLDOWN;
    }

    /**
     * Applies the active Max HP bonus using the simulator's live party.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator == null || currentTime >= activeUntil) {
            return;
        }
        int hydroCharacterCount = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.HYDRO) {
                hydroCharacterCount++;
            }
        }
        int additions = Math.min(MAX_HYDRO_ADDITIONS, hydroCharacterCount);
        stats.add(StatType.HP_PERCENT,
                baseHpBonus + hydroCharacterHpBonus * additions);
    }
}
