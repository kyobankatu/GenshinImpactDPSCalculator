package model.weapon;

import java.util.Collections;
import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;

/**
 * Athame Artis sword with a Burst-hit owner window and active-ally support.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. A positive resolved owner Burst hit opens a half-open
 * three-second window. During that window the owner gains ATK, while only the
 * currently active non-owner party member receives the separate support ATK
 * bonus. The provider evaluates the active character live, which preserves
 * switch routing without reproducing gcsim's one-second polling delay.</p>
 *
 * <p>The source-backed Burst CRIT DMG value is retained as typed weapon data,
 * but is not added to generic {@link StatType#CRIT_DMG}: this baseline has no
 * Burst-specific CRIT DMG stat or pre-damage action-aware weapon-stat hook, and
 * treating it as generic CRIT DMG would incorrectly buff every action.
 * Hexerei amplification is likewise inactive because no typed Hexerei party
 * state exists.</p>
 */
public final class AthameArtis extends Weapon
        implements DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        WeaponTeamBuffProvider {
    private static final double WINDOW_DURATION = 3.0;

    private final int refinement;
    private final double burstCriticalDamageBonus;
    private final double ownerAttackBonus;
    private final double activeAllyAttackBonus;
    private final Buff activeAllyBuff;

    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Athame Artis at refinement rank five. */
    public AthameArtis() {
        this(5);
    }

    /**
     * Constructs Athame Artis at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AthameArtis(int refinement) {
        super("Athame Artis", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        burstCriticalDamageBonus = 0.12 + 0.04 * refinement;
        ownerAttackBonus = 0.15 + 0.05 * refinement;
        activeAllyAttackBonus = 0.12 + 0.04 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);

        activeAllyBuff = new Buff("Athame Artis (Active Ally)") {
            @Override
            public boolean appliesToCharacter(Character character) {
                return simulator != null
                        && character != null
                        && character != owner
                        && character == simulator.getActiveCharacter()
                        && simulator.getPartyMembers().contains(character);
            }

            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                if (isWindowActive(currentTime)) {
                    stats.add(StatType.ATK_PERCENT, activeAllyAttackBonus);
                }
            }
        };
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the source-backed Burst-only CRIT DMG value. */
    public double getBurstCriticalDamageBonus() {
        return burstCriticalDamageBonus;
    }

    /** Returns the owner ATK value granted by the three-second window. */
    public double getOwnerAttackBonus() {
        return ownerAttackBonus;
    }

    /** Returns the active non-owner ally ATK support value. */
    public double getActiveAllyAttackBonus() {
        return activeAllyAttackBonus;
    }

    /** Returns whether the owner window is active at the supplied time. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current half-open expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Returns whether unsupported Hexerei amplification is active. */
    public boolean isHexereiAmplificationActive() {
        return false;
    }

    /** Binds this mutable weapon to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Athame Artis owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Athame Artis is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Athame Artis equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeAllyBuff.sourcedBy(owner.getCharacterId());
        activeSimulator.addDamageListener(this);
    }

    /**
     * Opens or refreshes the window after a positive resolved owner Burst hit.
     *
     * <p>The listener route exposes final direct damage, so zero-damage and
     * non-hit actions fail closed. Off-field owner Burst damage remains eligible,
     * matching the source's actor-based trigger.</p>
     */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isEligibleBurstHit(actor, action, damage)) {
            return;
        }
        activeFrom = currentTime;
        activeUntil = currentTime + WINDOW_DURATION;
    }

    /** Applies the live owner-only ATK window without generic CRIT DMG leakage. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isWindowActive(currentTime)) {
            stats.add(StatType.ATK_PERCENT, ownerAttackBonus);
        }
    }

    /** Returns the live active-ally provider for this exact bound owner. */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (simulator == null
                || equippedOwner != owner
                || equippedOwner.getWeapon() != this) {
            return Collections.emptyList();
        }
        return Collections.singletonList(activeAllyBuff);
    }

    /** Captures exact owner-window boundaries. */
    @Override
    public State captureWeaponState() {
        return new AthameState(this, activeFrom, activeUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof AthameState)) {
            throw new IllegalArgumentException(
                    "Athame Artis state type is invalid");
        }
        AthameState restored = (AthameState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Athame Artis state belongs to another instance");
        }
        activeFrom = restored.activeFrom;
        activeUntil = restored.activeUntil;
    }

    private boolean isEligibleBurstHit(
            Character actor,
            AttackAction action,
            double damage) {
        if (simulator == null
                || owner == null
                || owner.getWeapon() != this
                || actor != owner
                || !simulator.getPartyMembers().contains(actor)
                || action == null
                || !action.isHitEffectTrigger()
                || !(damage > 0.0)) {
            return false;
        }
        return action.getActionType() == ActionType.BURST
                || action.isCountsAsBurstDmg();
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Athame Artis refinement must be between 1 and 5");
        }
    }

    /** Immutable runtime state tied to one Athame Artis instance. */
    private static final class AthameState implements State {
        private final AthameArtis source;
        private final double activeFrom;
        private final double activeUntil;

        private AthameState(
                AthameArtis source,
                double activeFrom,
                double activeUntil) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
        }
    }
}
