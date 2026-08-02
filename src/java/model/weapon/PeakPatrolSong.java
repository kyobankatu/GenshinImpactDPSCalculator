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
 * Peak Patrol Song sword with Songs of Flowers and Falling Feathers stacks.
 *
 * <p>Positive owner Normal and Plunging hits gain up to two stacks on a shared
 * six-second window with a {@code 0.1}-second trigger cooldown. Each stack grants
 * DEF and all seven elemental DMG Bonuses. Reaching or refreshing two stacks
 * snapshots the owner's final DEF after the self bonus and derives a capped,
 * 15-second seven-element party bonus. Physical and generic all-DMG stats are
 * intentionally excluded.</p>
 */
public final class PeakPatrolSong extends Weapon
        implements DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        WeaponTeamBuffProvider {
    private static final int MAX_STACKS = 2;
    private static final double STACK_DURATION = 6.0;
    private static final double TRIGGER_COOLDOWN = 0.1;
    private static final double TEAM_BUFF_DURATION = 15.0;
    private static final StatType[] ELEMENTAL_BONUS_STATS = {
        StatType.PYRO_DMG_BONUS,
        StatType.HYDRO_DMG_BONUS,
        StatType.ANEMO_DMG_BONUS,
        StatType.ELECTRO_DMG_BONUS,
        StatType.DENDRO_DMG_BONUS,
        StatType.CRYO_DMG_BONUS,
        StatType.GEO_DMG_BONUS
    };

    private final int refinement;
    private final double defenseBonusPerStack;
    private final double elementalBonusPerStack;
    private final double teamBonusPerThousandDefense;
    private final double teamBonusCap;
    private final Buff teamBuff;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stacksActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextTriggerAt = Double.NEGATIVE_INFINITY;
    private double teamBuffActiveUntil = Double.NEGATIVE_INFINITY;
    private double snapshottedTeamBonus;

    /** Constructs Peak Patrol Song at refinement rank five. */
    public PeakPatrolSong() {
        this(5);
    }

    /**
     * Constructs Peak Patrol Song at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PeakPatrolSong(int refinement) {
        super("Peak Patrol Song", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.defenseBonusPerStack = 0.06 + 0.02 * refinement;
        this.elementalBonusPerStack = 0.075 + 0.025 * refinement;
        this.teamBonusPerThousandDefense = 0.06 + 0.02 * refinement;
        this.teamBonusCap = 0.192 + 0.064 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.DEF_PERCENT, 0.827);
        this.teamBuff = new Buff("Peak Patrol Song (Party)") {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                if (currentTime < teamBuffActiveUntil) {
                    addElementalBonuses(stats, snapshottedTeamBonus);
                }
            }
        };
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the number of self stacks active at the supplied time. */
    public int getStackCount(double currentTime) {
        expireStacks(currentTime);
        return stackCount;
    }

    /** Returns the currently captured team bonus, including an expired snapshot. */
    public double getSnapshottedTeamBonus() {
        return snapshottedTeamBonus;
    }

    /** Binds this stateful passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Peak Patrol Song is already bound to another simulator");
            }
            return;
        }
        validateBinding(equippedOwner, sim);
        owner = equippedOwner;
        simulator = sim;
        teamBuff.sourcedBy(owner.getCharacterId());
        sim.addDamageListener(this);
    }

    /**
     * Gains or refreshes self stacks and snapshots a team bonus at two stacks.
     *
     * @param actor character attributed with the resolved hit
     * @param action resolved direct attack
     * @param damage positive final damage required to trigger
     * @param time hit time in simulation seconds
     */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (!isEligibleOwnerHit(actor, action, damage) || time < nextTriggerAt) {
            return;
        }
        expireStacks(time);
        if (stackCount < MAX_STACKS) {
            stackCount++;
        }
        stacksActiveUntil = time + STACK_DURATION;
        nextTriggerAt = time + TRIGGER_COOLDOWN;
        if (stackCount == MAX_STACKS) {
            snapshottedTeamBonus = Math.min(
                    teamBonusCap,
                    teamBonusPerThousandDefense * getOwnerFinalDefense(time) / 1000.0);
            teamBuffActiveUntil = time + TEAM_BUFF_DURATION;
        }
    }

    /** Applies the active self-stack DEF and seven elemental bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int activeStacks = getStackCount(currentTime);
        stats.add(StatType.DEF_PERCENT, defenseBonusPerStack * activeStacks);
        addElementalBonuses(stats, elementalBonusPerStack * activeStacks);
    }

    /** Returns the snapshotted team buff only for this weapon's bound owner. */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (simulator == null || equippedOwner != owner
                || equippedOwner.getWeapon() != this) {
            return Collections.emptyList();
        }
        return Collections.singletonList(teamBuff);
    }

    /** Captures self stacks, both windows, the trigger ICD, and the team value. */
    @Override
    public State captureWeaponState() {
        return new PeakState(
                this,
                stackCount,
                stacksActiveUntil,
                nextTriggerAt,
                teamBuffActiveUntil,
                snapshottedTeamBonus);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof PeakState)) {
            throw new IllegalArgumentException("Peak Patrol Song state type is invalid");
        }
        PeakState peakState = (PeakState) state;
        if (peakState.source != this) {
            throw new IllegalArgumentException(
                    "Peak Patrol Song state belongs to another weapon instance");
        }
        stackCount = peakState.stackCount;
        stacksActiveUntil = peakState.stacksActiveUntil;
        nextTriggerAt = peakState.nextTriggerAt;
        teamBuffActiveUntil = peakState.teamBuffActiveUntil;
        snapshottedTeamBonus = peakState.snapshottedTeamBonus;
    }

    private double getOwnerFinalDefense(double currentTime) {
        StatsContainer stats = owner.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.getTotalDef();
    }

    private boolean isEligibleOwnerHit(
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
                || !(action.getDamagePercent() > 0.0)
                || !(damage > 0.0)) {
            return false;
        }
        return action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.PLUNGE;
    }

    private void expireStacks(double currentTime) {
        if (currentTime >= stacksActiveUntil) {
            stackCount = 0;
        }
    }

    private static void addElementalBonuses(StatsContainer stats, double bonus) {
        for (StatType stat : ELEMENTAL_BONUS_STATS) {
            stats.add(stat, bonus);
        }
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Peak Patrol Song equipped");
        }
        if (!sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Weapon owner must belong to the target simulator party");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Peak Patrol Song refinement must be between 1 and 5");
        }
    }

    /** Immutable runtime state tied to one Peak Patrol Song instance. */
    private static final class PeakState implements State {
        private final PeakPatrolSong source;
        private final int stackCount;
        private final double stacksActiveUntil;
        private final double nextTriggerAt;
        private final double teamBuffActiveUntil;
        private final double snapshottedTeamBonus;

        private PeakState(
                PeakPatrolSong source,
                int stackCount,
                double stacksActiveUntil,
                double nextTriggerAt,
                double teamBuffActiveUntil,
                double snapshottedTeamBonus) {
            this.source = source;
            this.stackCount = stackCount;
            this.stacksActiveUntil = stacksActiveUntil;
            this.nextTriggerAt = nextTriggerAt;
            this.teamBuffActiveUntil = teamBuffActiveUntil;
            this.snapshottedTeamBonus = snapshottedTeamBonus;
        }
    }
}
