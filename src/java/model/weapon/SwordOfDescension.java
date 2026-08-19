package model.weapon;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.DoubleSupplier;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Fixed-R1 Sword of Descension with its Traveler affinity and Physical proc.
 *
 * <p>Lv. 90 metadata and the passive follow the pinned KQM sword catalog and
 * Genshin Optimizer {@code 61c5556a}. The PlayStation-only passive is modeled
 * as enabled because the simulator has no runtime platform concept. Every
 * Traveler element and either twin use the canonical typed
 * {@link CharacterId#TRAVELER} identity.</p>
 */
public final class SwordOfDescension extends Weapon implements
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final String PROC_NAME = "Sword of Descension Proc";
    private static final int REFINEMENT = 1;
    private static final double TRAVELER_FLAT_ATTACK = 66.0;
    private static final double PROC_CHANCE = 0.50;
    private static final double PROC_COOLDOWN = 10.0;
    private static final double PROC_MOTION_VALUE = 2.0;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS =
            EnumSet.of(ActionType.NORMAL, ActionType.CHARGE);

    private final DoubleSupplier procDraw;
    private Character owner;
    private CombatSimulator simulator;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /** Constructs the fixed-R1 weapon with stochastic proc draws. */
    public SwordOfDescension() {
        this(Math::random);
    }

    /**
     * Constructs the fixed-R1 weapon with an injectable proc source.
     *
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public SwordOfDescension(DoubleSupplier procDraw) {
        super("Sword of Descension", new StatsContainer());
        this.procDraw = Objects.requireNonNull(procDraw, "procDraw");
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 440.0);
        getStats().set(StatType.ATK_PERCENT, 0.352);
    }

    /** Returns the weapon's fixed refinement rank. */
    public int getRefinement() {
        return REFINEMENT;
    }

    /** Returns the explicit simulator boundary for the platform passive. */
    public boolean isPlatformPassiveEnabled() {
        return true;
    }

    /** Applies the affinity bonus only to the canonical typed Traveler owner. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner != null
                && owner.getCharacterId() == CharacterId.TRAVELER) {
            stats.add(StatType.ATK_FLAT, TRAVELER_FLAT_ATTACK);
        }
    }

    /** Binds mutable proc state to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Sword of Descension owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Sword of Descension is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Sword of Descension equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Resolves one eligible successful proc through the normal damage path. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (owner == null
                || simulator != activeSimulator
                || user != owner
                || owner.getWeapon() != this
                || simulator.getActiveCharacter() != owner
                || action == null
                || action.getDamagePercent() <= 0.0
                || !action.isHitEffectTrigger()
                || PROC_NAME.equals(action.getName())
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())
                || currentTime < nextProcTime
                || procDraw.getAsDouble() >= PROC_CHANCE) {
            return;
        }
        nextProcTime = currentTime + PROC_COOLDOWN;
        AttackAction proc = new AttackAction(
                PROC_NAME,
                PROC_MOTION_VALUE,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        simulator.performActionWithoutTimeAdvance(
                owner.getCharacterId(), proc);
    }

    /** Captures the complete mutable cooldown state. */
    @Override
    public State captureWeaponState() {
        return new DescensionState(this, nextProcTime);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof DescensionState)) {
            throw new IllegalArgumentException(
                    "Sword of Descension state type is invalid");
        }
        DescensionState restored = (DescensionState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Sword of Descension state belongs to another instance");
        }
        nextProcTime = restored.nextProcTime;
    }

    private static final class DescensionState implements State {
        private final SwordOfDescension source;
        private final double nextProcTime;

        private DescensionState(
                SwordOfDescension source,
                double nextProcTime) {
            this.source = source;
            this.nextProcTime = nextProcTime;
        }
    }
}
