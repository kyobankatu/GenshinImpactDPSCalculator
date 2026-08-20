package model.weapon;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Exaiphanes Blade with explicit Traveler resonance history. */
public final class ExaiphanesBlade extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double ATTACK_WINDOW_DURATION = 8.0;
    private static final double ENERGY_COOLDOWN = 5.0;
    private static final double CRIT_DAMAGE_PER_RESONATED_ELEMENT = 0.06;
    private static final double[] ATTACK_BONUS = {0.16, 0.20, 0.24, 0.32, 0.40};
    private static final double[] ENERGY_RECOVERY = {3.0, 3.0, 5.0, 5.0, 5.0};

    private final int refinement;
    private final Set<Element> resonatedElements;
    private Character owner;
    private CombatSimulator simulator;
    private double attackWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextEnergyRecoveryAt = Double.NEGATIVE_INFINITY;

    /** Constructs an R5 blade with no declared resonance history. */
    public ExaiphanesBlade() {
        this(5, Collections.emptySet());
    }

    /** Constructs the selected refinement with no declared resonance history. */
    public ExaiphanesBlade(int refinement) {
        this(refinement, Collections.emptySet());
    }

    /**
     * Constructs a blade with the Traveler's explicitly configured resonated elements.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param resonatedElements elements with which this Traveler has resonated
     */
    public ExaiphanesBlade(int refinement, Set<Element> resonatedElements) {
        super("Exaiphanes Blade", new StatsContainer());
        validateRefinement(refinement);
        if (resonatedElements == null) {
            throw new IllegalArgumentException("Resonated elements are required");
        }
        this.refinement = refinement;
        EnumSet<Element> copy = EnumSet.noneOf(Element.class);
        for (Element element : resonatedElements) {
            if (element != null && element != Element.PHYSICAL) {
                copy.add(element);
            }
        }
        this.resonatedElements = Collections.unmodifiableSet(copy);
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
    }

    /** Returns immutable configured resonance history. */
    public Set<Element> getResonatedElements() {
        return resonatedElements;
    }

    /** Binds the weapon and rejects non-Traveler owners. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null
                || equippedOwner.getWeapon() != this
                || !sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Exaiphanes Blade owner must equip this weapon in the target party");
        }
        if (equippedOwner.getCharacterId() != CharacterId.TRAVELER) {
            throw new IllegalArgumentException(
                    "Exaiphanes Blade passive is exclusive to Traveler");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Exaiphanes Blade is already bound elsewhere");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Refreshes ATK and conditionally restores Energy after an owner hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (activeSimulator != simulator
                || user != owner
                || action == null
                || !action.isHitEffectTrigger()) {
            return;
        }
        attackWindowUntil = currentTime + ATTACK_WINDOW_DURATION;
        if (currentTime >= nextEnergyRecoveryAt) {
            owner.receiveFlatEnergy(ENERGY_RECOVERY[refinement - 1]);
            nextEnergyRecoveryAt = currentTime + ENERGY_COOLDOWN;
        }
    }

    /** Applies the hit window and R2+ resonance-history CRIT DMG. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < attackWindowUntil) {
            stats.add(StatType.ATK_PERCENT, ATTACK_BONUS[refinement - 1]);
        }
        if (refinement >= 2) {
            stats.add(
                    StatType.CRIT_DMG,
                    CRIT_DAMAGE_PER_RESONATED_ELEMENT * resonatedElements.size());
        }
    }

    /** Captures both runtime boundaries. */
    @Override
    public State captureWeaponState() {
        return new ExaiphanesState(
                this, attackWindowUntil, nextEnergyRecoveryAt);
    }

    /** Restores state captured from this exact blade instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ExaiphanesState)) {
            throw new IllegalArgumentException("Exaiphanes state type is invalid");
        }
        ExaiphanesState restored = (ExaiphanesState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Exaiphanes state belongs to another weapon instance");
        }
        attackWindowUntil = restored.attackWindowUntil;
        nextEnergyRecoveryAt = restored.nextEnergyRecoveryAt;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Exaiphanes refinement must be between 1 and 5");
        }
    }

    /** Immutable hit-window state. */
    private static final class ExaiphanesState implements State {
        private final ExaiphanesBlade source;
        private final double attackWindowUntil;
        private final double nextEnergyRecoveryAt;

        private ExaiphanesState(
                ExaiphanesBlade source,
                double attackWindowUntil,
                double nextEnergyRecoveryAt) {
            this.source = source;
            this.attackWindowUntil = attackWindowUntil;
            this.nextEnergyRecoveryAt = nextEnergyRecoveryAt;
        }
    }
}
