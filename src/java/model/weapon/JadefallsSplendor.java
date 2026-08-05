package model.weapon;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
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
import simulation.event.SimpleTimerEvent;

/**
 * Jadefall's Splendor's representable Primordial Jade Regalia branch.
 *
 * <p>An accepted active-owner Burst snapshots the owner's Max HP, opens a
 * three-second corresponding-element DMG Bonus window, and schedules one flat
 * Energy restoration 142 frames later. A later activation replaces the
 * pending Energy restoration instead of stacking it, matching pinned gcsim
 * {@code ef41805d}.</p>
 *
 * <p>The current runtime has no general player-shield creation event, so the
 * shield-created activation branch is excluded rather than synthesized.
 * Hitlag extension is unavailable; the represented duration is an exact
 * half-open simulation-time window.</p>
 */
public final class JadefallsSplendor extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double REGALIA_DURATION = 3.0;
    private static final double ENERGY_DELAY = 142.0 / 60.0;
    private static final double EPSILON = 1e-9;

    private final int refinement;
    private final double energyRestoration;
    private final double damageBonusPerThousandHp;
    private final double maximumDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double regaliaFrom = Double.POSITIVE_INFINITY;
    private double regaliaUntil = Double.NEGATIVE_INFINITY;
    private double elementalDamageBonus;
    private List<PendingEnergy> pendingEnergy = new ArrayList<>();

    /** Constructs Jadefall's Splendor at refinement rank five. */
    public JadefallsSplendor() {
        this(5);
    }

    /**
     * Constructs Jadefall's Splendor at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public JadefallsSplendor(int refinement) {
        super("Jadefall's Splendor", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Jadefall's Splendor refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        energyRestoration = 4.0 + 0.5 * refinement;
        damageBonusPerThousandHp = 0.001 + 0.002 * refinement;
        maximumDamageBonus = 0.04 + 0.08 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.HP_PERCENT, 0.496);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the flat Energy restored by one represented activation. */
    public double getEnergyRestoration() {
        return energyRestoration;
    }

    /** Returns corresponding-element DMG Bonus per 1,000 Max HP. */
    public double getDamageBonusPerThousandHp() {
        return damageBonusPerThousandHp;
    }

    /** Returns the refinement-specific elemental DMG Bonus cap. */
    public double getMaximumDamageBonus() {
        return maximumDamageBonus;
    }

    /** Returns the snapshotted elemental DMG Bonus for the current window. */
    public double getElementalDamageBonus() {
        return elementalDamageBonus;
    }

    /** Returns whether Primordial Jade Regalia is active at the given time. */
    public boolean isRegaliaActive(double currentTime) {
        return currentTime >= regaliaFrom && currentTime < regaliaUntil;
    }

    /** Returns the exact expiration timestamp of the current Regalia window. */
    public double getRegaliaUntil() {
        return regaliaUntil;
    }

    /** Returns the number of unresolved Energy restorations, at most one. */
    public int getPendingEnergyCount() {
        return pendingEnergy.size();
    }

    /** Applies the snapshotted bonus only to the owner's corresponding element. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (owner == null || !isRegaliaActive(currentTime)) {
            return;
        }
        stats.add(owner.getElement().getBonusStatType(), elementalDamageBonus);
    }

    /** Binds this mutable passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Jadefall's Splendor owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Jadefall's Splendor is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Jadefall's Splendor equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /**
     * Activates Regalia from an accepted active-owner Burst.
     *
     * <p>Max HP is captured before the new damage window is installed. The
     * source formula uses continuous Max HP rather than complete 1,000-HP
     * increments.</p>
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        StatsContainer hpSnapshot = owner.getEffectiveStats(currentTime);
        for (Buff buff : activeSimulator.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(hpSnapshot, currentTime);
            }
        }
        double maximumHp = hpSnapshot.getTotalHp();
        elementalDamageBonus = Math.min(
                maximumDamageBonus,
                maximumHp / 1000.0 * damageBonusPerThousandHp);
        regaliaFrom = currentTime;
        regaliaUntil = currentTime + REGALIA_DURATION;

        pendingEnergy.clear();
        queueEnergy(new PendingEnergy(currentTime + ENERGY_DELAY));
    }

    /** Captures the active window and its single replaceable Energy task. */
    @Override
    public State captureWeaponState() {
        return new JadefallState(
                this,
                regaliaFrom,
                regaliaUntil,
                elementalDamageBonus,
                pendingEnergy);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof JadefallState)) {
            throw new IllegalArgumentException(
                    "Jadefall's Splendor state type is invalid");
        }
        JadefallState restored = (JadefallState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Jadefall's Splendor state belongs to another instance");
        }
        regaliaFrom = restored.regaliaFrom;
        regaliaUntil = restored.regaliaUntil;
        elementalDamageBonus = restored.elementalDamageBonus;
        pendingEnergy = copyPendingEnergy(restored.pendingEnergy);
        if (simulator == null) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        pendingEnergy.removeIf(pending ->
                pending.time < currentTime - EPSILON);
        for (PendingEnergy pending : new ArrayList<>(pendingEnergy)) {
            scheduleEnergy(pending);
        }
    }

    private void queueEnergy(PendingEnergy pending) {
        pendingEnergy.add(pending);
        scheduleEnergy(pending);
    }

    private void scheduleEnergy(PendingEnergy pending) {
        simulator.registerEvent(new SimpleTimerEvent(pending.time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                if (!pendingEnergy.remove(pending)) {
                    return;
                }
                owner.receiveFlatEnergy(energyRestoration);
            }
        });
    }

    private boolean isBoundActiveOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == actor
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static List<PendingEnergy> copyPendingEnergy(
            List<PendingEnergy> source) {
        List<PendingEnergy> copy = new ArrayList<>();
        for (PendingEnergy pending : source) {
            copy.add(new PendingEnergy(pending.time));
        }
        return copy;
    }

    private static final class PendingEnergy {
        private final double time;

        private PendingEnergy(double time) {
            this.time = time;
        }
    }

    /** Immutable Regalia state tied to one Jadefall's Splendor instance. */
    private static final class JadefallState implements State {
        private final JadefallsSplendor source;
        private final double regaliaFrom;
        private final double regaliaUntil;
        private final double elementalDamageBonus;
        private final List<PendingEnergy> pendingEnergy;

        private JadefallState(
                JadefallsSplendor source,
                double regaliaFrom,
                double regaliaUntil,
                double elementalDamageBonus,
                List<PendingEnergy> pendingEnergy) {
            this.source = source;
            this.regaliaFrom = regaliaFrom;
            this.regaliaUntil = regaliaUntil;
            this.elementalDamageBonus = elementalDamageBonus;
            this.pendingEnergy = copyPendingEnergy(pendingEnergy);
        }
    }
}
