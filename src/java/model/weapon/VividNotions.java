package model.weapon;

import java.util.ArrayList;
import java.util.List;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/** Vivid Notions with independent Plunging CRIT DMG action windows. */
public final class VividNotions extends Weapon
        implements ActionTriggeredWeaponEffect,
        DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 15.0;
    private static final double CANCELLATION_DELAY = 0.1;

    private final int refinement;
    private final double attackBonus;
    private final double plungeWindowCritDamage;
    private final double skillBurstWindowCritDamage;

    private Character owner;
    private CombatSimulator simulator;
    private double plungeActiveUntil = Double.NEGATIVE_INFINITY;
    private double skillBurstActiveUntil = Double.NEGATIVE_INFINITY;
    private long plungeGeneration;
    private long skillBurstGeneration;
    private long timerEpoch;
    private final List<PendingCancellation> pendingCancellations =
            new ArrayList<>();

    /** Constructs Vivid Notions at refinement rank five. */
    public VividNotions() {
        this(5);
    }

    /**
     * Constructs Vivid Notions at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public VividNotions(int refinement) {
        super("Vivid Notions", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.attackBonus = 0.21 + 0.07 * refinement;
        this.plungeWindowCritDamage = 0.21 + 0.07 * refinement;
        this.skillBurstWindowCritDamage = 0.30 + 0.10 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the Plunge-use window's Plunging CRIT DMG. */
    public double getPlungeWindowCritDamage() {
        return plungeWindowCritDamage;
    }

    /** Returns the Skill/Burst-use window's Plunging CRIT DMG. */
    public double getSkillBurstWindowCritDamage() {
        return skillBurstWindowCritDamage;
    }

    /** Binds this mutable weapon to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Vivid Notions is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addDamageListener(this);
    }

    /** Opens or refreshes the action-specific fifteen-second window. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (!isBoundCallback(user, sim) || request == null) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        if (request.getKey() == CharacterActionKey.PLUNGE) {
            plungeActiveUntil = currentTime + WINDOW_DURATION;
            plungeGeneration++;
        } else if (request.getKey() == CharacterActionKey.SKILL
                || request.getKey() == CharacterActionKey.BURST) {
            skillBurstActiveUntil = currentTime + WINDOW_DURATION;
            skillBurstGeneration++;
        }
    }

    /** Applies permanent ATK and the additive active Plunging CRIT DMG windows. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
        double critDamage = 0.0;
        if (currentTime < plungeActiveUntil) {
            critDamage += plungeWindowCritDamage;
        }
        if (currentTime < skillBurstActiveUntil) {
            critDamage += skillBurstWindowCritDamage;
        }
        stats.add(StatType.PLUNGING_ATTACK_CRIT_DMG, critDamage);
    }

    /** Schedules generation-safe cancellation after a positive owner Plunge hit. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isBoundActor(actor)
                || action == null
                || !action.isHitEffectTrigger()
                || damage <= 0.0
                || action.getActionType() != ActionType.PLUNGE) {
            return;
        }
        PendingCancellation pending = new PendingCancellation(
                currentTime + CANCELLATION_DELAY,
                plungeGeneration,
                skillBurstGeneration);
        pendingCancellations.add(pending);
        scheduleCancellation(pending);
    }

    /** Captures windows, generations, and outstanding cancellation reservations. */
    @Override
    public State captureWeaponState() {
        return new NotionsState(
                this,
                plungeActiveUntil,
                skillBurstActiveUntil,
                plungeGeneration,
                skillBurstGeneration,
                new ArrayList<>(pendingCancellations));
    }

    /** Restores state and recreates cancellation timers cleared by simulator rollback. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof NotionsState)) {
            throw new IllegalArgumentException("Vivid Notions state type is invalid");
        }
        NotionsState notionsState = (NotionsState) state;
        if (notionsState.source != this) {
            throw new IllegalArgumentException(
                    "Vivid Notions state belongs to another weapon instance");
        }
        timerEpoch++;
        plungeActiveUntil = notionsState.plungeActiveUntil;
        skillBurstActiveUntil = notionsState.skillBurstActiveUntil;
        plungeGeneration = notionsState.plungeGeneration;
        skillBurstGeneration = notionsState.skillBurstGeneration;
        pendingCancellations.clear();
        pendingCancellations.addAll(notionsState.pendingCancellations);
        if (simulator != null) {
            for (PendingCancellation pending : pendingCancellations) {
                scheduleCancellation(pending);
            }
        }
    }

    private void scheduleCancellation(PendingCancellation pending) {
        long scheduledEpoch = timerEpoch;
        simulator.registerEvent(new SimpleTimerEvent(
                pending.fireAt,
                CANCELLATION_DELAY) {
            @Override
            public void onTick(CombatSimulator sim) {
                if (scheduledEpoch != timerEpoch) {
                    finish();
                    return;
                }
                if (pending.plungeGeneration == plungeGeneration) {
                    plungeActiveUntil = Double.NEGATIVE_INFINITY;
                    plungeGeneration++;
                }
                if (pending.skillBurstGeneration == skillBurstGeneration) {
                    skillBurstActiveUntil = Double.NEGATIVE_INFINITY;
                    skillBurstGeneration++;
                }
                pendingCancellations.remove(pending);
                finish();
            }
        });
    }

    private boolean isBoundCallback(Character user, CombatSimulator sim) {
        return isBoundActor(user) && sim == simulator;
    }

    private boolean isBoundActor(Character actor) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Vivid Notions equipped");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Vivid Notions refinement must be between 1 and 5");
        }
    }

    /** Immutable delayed-cancellation payload. */
    private static final class PendingCancellation {
        private final double fireAt;
        private final long plungeGeneration;
        private final long skillBurstGeneration;

        private PendingCancellation(
                double fireAt,
                long plungeGeneration,
                long skillBurstGeneration) {
            this.fireAt = fireAt;
            this.plungeGeneration = plungeGeneration;
            this.skillBurstGeneration = skillBurstGeneration;
        }
    }

    /** Immutable runtime state tied to one weapon instance. */
    private static final class NotionsState implements State {
        private final VividNotions source;
        private final double plungeActiveUntil;
        private final double skillBurstActiveUntil;
        private final long plungeGeneration;
        private final long skillBurstGeneration;
        private final List<PendingCancellation> pendingCancellations;

        private NotionsState(
                VividNotions source,
                double plungeActiveUntil,
                double skillBurstActiveUntil,
                long plungeGeneration,
                long skillBurstGeneration,
                List<PendingCancellation> pendingCancellations) {
            this.source = source;
            this.plungeActiveUntil = plungeActiveUntil;
            this.skillBurstActiveUntil = skillBurstActiveUntil;
            this.plungeGeneration = plungeGeneration;
            this.skillBurstGeneration = skillBurstGeneration;
            this.pendingCancellations = pendingCancellations;
        }
    }
}
