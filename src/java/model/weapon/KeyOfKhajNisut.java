package model.weapon;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Key of Khaj-Nisut with shared-duration owner and team EM bonuses.
 *
 * <p>Only true on-field Skill hits from the bound owner gain Grand Hymn. At
 * each eligible hit, the complete owner EM value is recalculated from current
 * final Max HP and the resulting stack count. All stacks share one refreshed
 * 20-second duration. Reaching or refreshing three stacks also replaces the
 * typed team EM buff with a value calculated from current final Max HP.
 */
public final class KeyOfKhajNisut extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double STACK_COOLDOWN = 0.3;
    private static final double STACK_DURATION = 20.0;
    private static final int MAX_STACKS = 3;
    private static final double EPSILON = 1e-9;

    private final int refinement;
    private final double hpBonus;
    private final double ownerEmRatio;
    private final double teamEmRatio;

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double ownerEmBonus;
    private double stacksExpireAt = Double.NEGATIVE_INFINITY;
    private double nextStackTime = Double.NEGATIVE_INFINITY;

    /** Constructs Key of Khaj-Nisut at refinement rank five. */
    public KeyOfKhajNisut() {
        this(5);
    }

    /**
     * Constructs Key of Khaj-Nisut at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public KeyOfKhajNisut(int refinement) {
        super("Key of Khaj-Nisut", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Key of Khaj-Nisut refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.hpBonus = 0.15 + 0.05 * refinement;
        this.ownerEmRatio = 0.0009 + 0.0003 * refinement;
        this.teamEmRatio = 0.0015 + 0.0005 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.HP_PERCENT, 0.662 + hpBonus);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional HP bonus from the passive. */
    public double getHpBonus() {
        return hpBonus;
    }

    /** Returns the owner EM ratio per active stack. */
    public double getOwnerEmRatio() {
        return ownerEmRatio;
    }

    /** Returns the three-stack team EM ratio. */
    public double getTeamEmRatio() {
        return teamEmRatio;
    }

    /** Returns the active stack count at the supplied simulation time. */
    public int getStackCount(double currentTime) {
        expireStacksAt(currentTime);
        return stackCount;
    }

    /** Binds this mutable weapon instance to exactly one owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Key of Khaj-Nisut is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Key of Khaj-Nisut equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Acquires or refreshes Grand Hymn after an eligible Skill hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || simulator == null
                || sim.getActiveCharacter() != owner
                || action == null
                || !action.isHitEffectTrigger()
                || !isSkillHit(action)
                || currentTime + EPSILON < nextStackTime) {
            return;
        }

        expireStacksAt(currentTime);
        double maxHp = resolveCurrentMaxHp(currentTime);
        if (stackCount < MAX_STACKS) {
            stackCount++;
        }
        ownerEmBonus = maxHp * ownerEmRatio * stackCount;
        stacksExpireAt = currentTime + STACK_DURATION;
        nextStackTime = currentTime + STACK_COOLDOWN;

        if (stackCount == MAX_STACKS) {
            double teamEmBonus = maxHp * teamEmRatio;
            SimpleBuff teamBuff = new SimpleBuff(
                    "Key of Khaj-Nisut Grand Hymn",
                    BuffId.KEY_OF_KHAJ_NISUT_TEAM_EM,
                    STACK_DURATION,
                    currentTime,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            teamEmBonus));
            teamBuff.sourcedBy(owner.getCharacterId());
            sim.applyTeamBuffNoStack(teamBuff);
        }
    }

    /** Adds the active owner-only Grand Hymn EM value. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireStacksAt(currentTime);
        stats.add(StatType.ELEMENTAL_MASTERY, ownerEmBonus);
    }

    /** Captures complete mutable Grand Hymn state. */
    @Override
    public State captureWeaponState() {
        return new KeyState(
                this,
                stackCount,
                ownerEmBonus,
                stacksExpireAt,
                nextStackTime);
    }

    /** Restores only immutable state captured from this weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof KeyState)) {
            throw new IllegalArgumentException(
                    "Key of Khaj-Nisut state type is invalid");
        }
        KeyState keyState = (KeyState) state;
        if (keyState.source != this) {
            throw new IllegalArgumentException(
                    "Key of Khaj-Nisut state belongs to another weapon instance");
        }
        stackCount = keyState.stackCount;
        ownerEmBonus = keyState.ownerEmBonus;
        stacksExpireAt = keyState.stacksExpireAt;
        nextStackTime = keyState.nextStackTime;
    }

    private boolean isSkillHit(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    private double resolveCurrentMaxHp(double currentTime) {
        StatsContainer stats = owner.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.getTotalHp();
    }

    private void expireStacksAt(double currentTime) {
        if (stackCount == 0 || currentTime < stacksExpireAt) {
            return;
        }
        stackCount = 0;
        ownerEmBonus = 0.0;
        stacksExpireAt = Double.NEGATIVE_INFINITY;
    }

    /** Immutable state payload tied to one weapon instance. */
    private static final class KeyState implements State {
        private final KeyOfKhajNisut source;
        private final int stackCount;
        private final double ownerEmBonus;
        private final double stacksExpireAt;
        private final double nextStackTime;

        private KeyState(
                KeyOfKhajNisut source,
                int stackCount,
                double ownerEmBonus,
                double stacksExpireAt,
                double nextStackTime) {
            this.source = source;
            this.stackCount = stackCount;
            this.ownerEmBonus = ownerEmBonus;
            this.stacksExpireAt = stacksExpireAt;
            this.nextStackTime = nextStackTime;
        }
    }
}
