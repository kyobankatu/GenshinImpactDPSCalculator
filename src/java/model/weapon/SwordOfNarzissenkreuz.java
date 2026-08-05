package model.weapon;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import model.entity.Character;
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
import simulation.DamageListener;
import simulation.action.AttackAction;
import simulation.event.SimpleTimerEvent;

/**
 * Sword of Narzissenkreuz with its non-Arkhe Hero's Blade damage branch.
 *
 * <p>Lv. 90 metadata and passive timing follow pinned gcsim
 * {@code ef41805d}. A positive hit-effect Normal, Charged, or Plunging hit by
 * the active equipped owner reserves one Physical blast after 0.1 seconds and
 * starts a 12-second cooldown. The represented blast scales from final ATK at
 * 160/200/240/280/320% for refinement ranks 1-5.</p>
 *
 * <p>The runtime does not model Pneuma or Ousia combat interactions. The
 * selected {@link EnergyBlastType} is therefore configuration metadata only;
 * both selections resolve the same Physical blast. Current supported native
 * Arkhe characters fail closed. Geometry, hitlag extension, and stamina are
 * intentionally outside this weapon-local slice.</p>
 */
public final class SwordOfNarzissenkreuz extends Weapon implements
        DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double BLAST_DELAY = 0.1;
    private static final double PROC_COOLDOWN = 12.0;
    private static final String BLAST_ACTION_NAME =
            "Sword of Narzissenkreuz Energy Blast";
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS = EnumSet.of(
            ActionType.NORMAL,
            ActionType.CHARGE,
            ActionType.PLUNGE);
    private static final EnumSet<CharacterId> KNOWN_ARKHE_CHARACTERS =
            EnumSet.of(
                    CharacterId.FREMINET,
                    CharacterId.LYNETTE,
                    CharacterId.CHARLOTTE);

    /** Selectable energy-blast alignment retained as configuration metadata. */
    public enum EnergyBlastType {
        PNEUMA,
        OUSIA
    }

    private final int refinement;
    private final EnergyBlastType energyBlastType;
    private final double blastMotionValue;
    private Character owner;
    private CombatSimulator simulator;
    private double nextProcTime = Double.NEGATIVE_INFINITY;
    private List<PendingBlast> pendingBlasts = new ArrayList<>();
    private long timerEpoch;

    /** Constructs an R5 Ousia-configured Sword of Narzissenkreuz. */
    public SwordOfNarzissenkreuz() {
        this(5, EnergyBlastType.OUSIA);
    }

    /** Constructs the selected refinement with the default Ousia metadata. */
    public SwordOfNarzissenkreuz(int refinement) {
        this(refinement, EnergyBlastType.OUSIA);
    }

    /**
     * Constructs Sword of Narzissenkreuz with selected refinement and alignment.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param energyBlastType typed Pneuma/Ousia configuration metadata
     */
    public SwordOfNarzissenkreuz(
            int refinement,
            EnergyBlastType energyBlastType) {
        super("Sword of Narzissenkreuz", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.energyBlastType = Objects.requireNonNull(
                energyBlastType,
                "energyBlastType");
        blastMotionValue = 1.2 + 0.4 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the typed blast selection, which currently has no damage effect. */
    public EnergyBlastType getEnergyBlastType() {
        return energyBlastType;
    }

    /** Returns the refinement-specific final-ATK blast multiplier. */
    public double getBlastMotionValue() {
        return blastMotionValue;
    }

    /** Returns the next exact timestamp at which an eligible hit may trigger. */
    public double getNextProcTime() {
        return nextProcTime;
    }

    /** Returns the number of unresolved delayed blasts. */
    public int getPendingBlastCount() {
        return pendingBlasts.size();
    }

    /** Returns whether the supplied supported character has native Arkhe. */
    public static boolean isKnownArkheCharacter(CharacterId characterId) {
        return characterId != null
                && KNOWN_ARKHE_CHARACTERS.contains(characterId);
    }

    /** Binds mutable proc state to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Sword of Narzissenkreuz owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Sword of Narzissenkreuz is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Sword of Narzissenkreuz equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener(this);
    }

    /**
     * Reserves one delayed blast after an eligible active-owner damage event.
     *
     * @param actor character attributed with the direct hit
     * @param action resolved action carrying typed hit eligibility
     * @param damage final direct damage dealt to the enemy
     * @param currentTime triggering hit time in simulation seconds
     */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isEligibleTrigger(actor, action, damage, currentTime)) {
            return;
        }
        nextProcTime = currentTime + PROC_COOLDOWN;
        PendingBlast pending = new PendingBlast(currentTime + BLAST_DELAY);
        pendingBlasts.add(pending);
        scheduleBlast(pending);
    }

    /** Captures cooldown and every unresolved blast reservation. */
    @Override
    public State captureWeaponState() {
        return new NarzissenkreuzState(
                this,
                nextProcTime,
                pendingBlasts);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof NarzissenkreuzState)) {
            throw new IllegalArgumentException(
                    "Sword of Narzissenkreuz state type is invalid");
        }
        NarzissenkreuzState restored = (NarzissenkreuzState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Sword of Narzissenkreuz state belongs to another instance");
        }
        timerEpoch++;
        nextProcTime = restored.nextProcTime;
        pendingBlasts = copyPendingBlasts(restored.pendingBlasts);
        if (simulator != null) {
            for (PendingBlast pending : new ArrayList<>(pendingBlasts)) {
                scheduleBlast(pending);
            }
        }
    }

    private boolean isEligibleTrigger(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        return simulator != null
                && owner != null
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner)
                && simulator.getActiveCharacter() == owner
                && !isKnownArkheCharacter(owner.getCharacterId())
                && action != null
                && action.isHitEffectTrigger()
                && action.getDamagePercent() > 0.0
                && ELIGIBLE_ACTIONS.contains(action.getActionType())
                && damage > 0.0
                && currentTime >= nextProcTime;
    }

    private void scheduleBlast(PendingBlast pending) {
        long scheduledEpoch = timerEpoch;
        simulator.registerEvent(new SimpleTimerEvent(
                pending.fireAt,
                BLAST_DELAY) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                if (scheduledEpoch != timerEpoch
                        || !pendingBlasts.remove(pending)) {
                    return;
                }
                AttackAction blast = new AttackAction(
                        BLAST_ACTION_NAME,
                        blastMotionValue,
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.PHYSICAL_DMG_BONUS,
                        0.0,
                        false,
                        ActionType.OTHER);
                activeSimulator.performActionWithoutTimeAdvance(
                        owner.getCharacterId(),
                        blast);
            }
        });
    }

    private static List<PendingBlast> copyPendingBlasts(
            List<PendingBlast> source) {
        List<PendingBlast> copy = new ArrayList<>();
        for (PendingBlast pending : source) {
            copy.add(new PendingBlast(pending.fireAt));
        }
        return copy;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Sword of Narzissenkreuz refinement must be between 1 and 5");
        }
    }

    /** Immutable delayed-blast reservation. */
    private static final class PendingBlast {
        private final double fireAt;

        private PendingBlast(double fireAt) {
            this.fireAt = fireAt;
        }
    }

    /** Immutable cooldown and pending-task state tied to one weapon instance. */
    private static final class NarzissenkreuzState implements State {
        private final SwordOfNarzissenkreuz source;
        private final double nextProcTime;
        private final List<PendingBlast> pendingBlasts;

        private NarzissenkreuzState(
                SwordOfNarzissenkreuz source,
                double nextProcTime,
                List<PendingBlast> pendingBlasts) {
            this.source = source;
            this.nextProcTime = nextProcTime;
            this.pendingBlasts = copyPendingBlasts(pendingBlasts);
        }
    }
}
