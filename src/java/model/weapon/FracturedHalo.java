package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Fractured Halo's representable Elemental Skill/Burst ATK window.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. Active-owner Skill or Burst use grants ATK for 20
 * seconds. Electrifying Edict remains inactive because the runtime has no
 * typed player-shield creation event.</p>
 */
public final class FracturedHalo extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 20.0;

    private final int refinement;
    private final double attackBonus;
    private final double lunarChargedTeamBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Fractured Halo at refinement rank five. */
    public FracturedHalo() {
        this(5);
    }

    /**
     * Constructs Fractured Halo at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FracturedHalo(int refinement) {
        super("Fractured Halo", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Fractured Halo refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackBonus = 0.18 + 0.06 * refinement;
        lunarChargedTeamBonus = 0.30 + 0.10 * refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_DMG, 0.662);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the represented ATK-window value. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the source-backed but currently inactive team bonus value. */
    public double getLunarChargedTeamBonus() {
        return lunarChargedTeamBonus;
    }

    /** Returns whether the half-open ATK window is active. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current half-open expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Applies the live owner-only ATK bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isWindowActive(currentTime)) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
        }
    }

    /** Binds one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Fractured Halo owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Fractured Halo is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Fractured Halo equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens or refreshes the window on active-owner Skill or Burst use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || (request.getKey() != CharacterActionKey.SKILL
                        && request.getKey() != CharacterActionKey.BURST)) {
            return;
        }
        activeFrom = activeSimulator.getCurrentTime();
        activeUntil = activeFrom + WINDOW_DURATION;
    }

    /** Captures exact ATK-window boundaries. */
    @Override
    public State captureWeaponState() {
        return new FracturedHaloState(this, activeFrom, activeUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FracturedHaloState)) {
            throw new IllegalArgumentException(
                    "Fractured Halo state type is invalid");
        }
        FracturedHaloState restored = (FracturedHaloState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Fractured Halo state belongs to another instance");
        }
        activeFrom = restored.activeFrom;
        activeUntil = restored.activeUntil;
    }

    private boolean isBoundActiveOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == user
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static final class FracturedHaloState implements State {
        private final FracturedHalo source;
        private final double activeFrom;
        private final double activeUntil;

        private FracturedHaloState(
                FracturedHalo source,
                double activeFrom,
                double activeUntil) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
        }
    }
}
