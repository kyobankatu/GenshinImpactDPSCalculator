package model.weapon;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Thrilling Tales of Dragon Slayers with its refinement-aware switch buff.
 *
 * <p>When the bound on-field owner performs a standard switch, the resolved
 * incoming character receives one ten-second ATK buff. The passive has a
 * twenty-second cooldown and replaces an existing Legacy buff on that target.</p>
 */
public class ThrillingTalesOfDragonSlayers extends Weapon
        implements SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        SwitchAwareWeaponEffect {
    private static final double BUFF_DURATION = 10.0;
    private static final double COOLDOWN = 20.0;

    private final int refinement;
    private final double attackPercentBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double lastActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs Thrilling Tales of Dragon Slayers at refinement rank five. */
    public ThrillingTalesOfDragonSlayers() {
        this(5);
    }

    /**
     * Constructs Thrilling Tales of Dragon Slayers at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ThrillingTalesOfDragonSlayers(int refinement) {
        super("Thrilling Tales of Dragon Slayers", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackPercentBonus = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 401.0);
        getStats().set(StatType.HP_PERCENT, 0.352);
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
     * Binds this weapon instance to exactly one owner and simulator.
     *
     * @param equippedOwner character carrying this weapon
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
                        "Thrilling Tales of Dragon Slayers is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Leaves the legacy callback inert because Legacy requires a resolved target.
     *
     * @param user outgoing weapon owner
     * @param sim active simulator
     */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
    }

    /**
     * Applies Legacy to an eligible incoming character at the switch timestamp.
     *
     * <p>The cooldown advances only after a valid target receives the replacement
     * buff. This callback runs before the party changes its active character.</p>
     *
     * @param user outgoing weapon owner
     * @param incoming resolved character entering the field
     * @param sim simulator dispatching the standard switch
     */
    @Override
    public void onSwitchOut(Character user, Character incoming, CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || user != owner
                || incoming == null
                || incoming == owner
                || sim.getActiveCharacter() != owner) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (currentTime < lastActivationTime + COOLDOWN) {
            return;
        }

        incoming.removeBuff(BuffId.THRILLING_TALES_LEGACY);
        Buff legacy = new SimpleBuff(
                "Thrilling Tales: Legacy",
                BuffId.THRILLING_TALES_LEGACY,
                BUFF_DURATION,
                currentTime,
                stats -> stats.add(StatType.ATK_PERCENT, attackPercentBonus));
        legacy.sourcedBy(owner.getCharacterId());
        incoming.addBuff(legacy);
        lastActivationTime = currentTime;
    }

    /** Captures the Legacy activation cooldown for branch rollback. */
    @Override
    public State captureWeaponState() {
        return new ThrillingTalesState(this, lastActivationTime);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ThrillingTalesState)) {
            throw new IllegalArgumentException(
                    "Thrilling Tales state type is invalid");
        }
        ThrillingTalesState restored = (ThrillingTalesState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Thrilling Tales state belongs to another instance");
        }
        lastActivationTime = restored.lastActivationTime;
    }

    /** Immutable Legacy cooldown snapshot. */
    private static final class ThrillingTalesState implements State {
        private final ThrillingTalesOfDragonSlayers source;
        private final double lastActivationTime;

        private ThrillingTalesState(
                ThrillingTalesOfDragonSlayers source,
                double lastActivationTime) {
            this.source = source;
            this.lastActivationTime = lastActivationTime;
        }
    }
}
