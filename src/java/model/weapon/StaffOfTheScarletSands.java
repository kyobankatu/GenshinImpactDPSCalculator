package model.weapon;

import mechanics.buff.Buff;
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
 * Staff of the Scarlet Sands with live EM conversion and snapshotted Skill-hit stacks.
 *
 * <p>The unconditional conversion is represented by
 * {@link StatType#ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO}, so EM merged after the
 * weapon passive remains part of the final ATK calculation. Each eligible
 * on-field Skill hit stores its own flat ATK value from the owner's effective
 * EM after that hit. The three stored values share one ten-second duration;
 * every eligible hit refreshes that duration, including hits at the stack cap.
 *
 * <p>The simulator models one enemy, so the multi-target counter used by gcsim
 * is intentionally omitted. Distinct Skill hits at the same timestamp may each
 * gain one stack.
 */
public final class StaffOfTheScarletSands extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 3;
    private static final double STACK_DURATION = 10.0;

    private final int refinement;
    private final double elementalMasteryConversionRatio;
    private final double stackConversionRatio;
    private final double[] stackFlatAttack = new double[MAX_STACKS];

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stacksExpireAt = Double.NEGATIVE_INFINITY;

    /** Constructs Staff of the Scarlet Sands at refinement rank five. */
    public StaffOfTheScarletSands() {
        this(5);
    }

    /**
     * Constructs Staff of the Scarlet Sands at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public StaffOfTheScarletSands(int refinement) {
        super("Staff of the Scarlet Sands", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Staff of the Scarlet Sands refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryConversionRatio = 0.39 + 0.13 * refinement;
        this.stackConversionRatio = 0.21 + 0.07 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_RATE, 0.441);
        getStats().set(
                StatType.ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO,
                elementalMasteryConversionRatio);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the dynamic unconditional EM-to-flat-ATK ratio. */
    public double getElementalMasteryConversionRatio() {
        return elementalMasteryConversionRatio;
    }

    /** Returns the snapshotted EM-to-flat-ATK ratio for each Skill-hit stack. */
    public double getStackConversionRatio() {
        return stackConversionRatio;
    }

    /**
     * Binds this mutable weapon instance to exactly one equipped owner and simulator.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
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
                        "Staff of the Scarlet Sands is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Staff of the Scarlet Sands equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Stores one post-hit EM snapshot and refreshes the shared stack duration.
     *
     * <p>Only a true Skill-damage hit from the bound on-field owner is eligible.
     * Zero-motion-value attacks remain eligible when explicitly marked as a
     * hit-effect trigger, while animation-only dummy casts are rejected.
     */
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
                || !isSkillHit(action)) {
            return;
        }

        expireStacksAt(currentTime);
        if (stackCount < MAX_STACKS) {
            double elementalMastery = resolveCurrentElementalMastery(currentTime);
            stackFlatAttack[stackCount] = elementalMastery * stackConversionRatio;
            stackCount++;
        }
        stacksExpireAt = currentTime + STACK_DURATION;
    }

    /** Applies the active snapshotted stack values as flat ATK. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireStacksAt(currentTime);
        double flatAttack = 0.0;
        for (int i = 0; i < stackCount; i++) {
            flatAttack += stackFlatAttack[i];
        }
        stats.add(StatType.ATK_FLAT, flatAttack);
    }

    /** Captures all mutable stack values and the shared expiry timestamp. */
    @Override
    public State captureWeaponState() {
        return new ScarletSandsState(
                this,
                stackFlatAttack,
                stackCount,
                stacksExpireAt);
    }

    /** Restores only an immutable state captured from this weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ScarletSandsState)) {
            throw new IllegalArgumentException(
                    "Staff of the Scarlet Sands state type is invalid");
        }
        ScarletSandsState scarletSandsState = (ScarletSandsState) state;
        if (scarletSandsState.source != this) {
            throw new IllegalArgumentException(
                    "Staff of the Scarlet Sands state belongs to another weapon instance");
        }
        System.arraycopy(
                scarletSandsState.stackFlatAttack,
                0,
                stackFlatAttack,
                0,
                MAX_STACKS);
        stackCount = scarletSandsState.stackCount;
        stacksExpireAt = scarletSandsState.stacksExpireAt;
    }

    /** Returns whether the action is directly or explicitly classified as Skill damage. */
    private boolean isSkillHit(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    /** Resolves EM from the same live owner and simulator buffs used by direct damage. */
    private double resolveCurrentElementalMastery(double currentTime) {
        StatsContainer stats = owner.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(owner)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(StatType.ELEMENTAL_MASTERY);
    }

    /** Clears every shared-duration stack at the exact expiry boundary. */
    private void expireStacksAt(double currentTime) {
        if (stackCount == 0 || currentTime < stacksExpireAt) {
            return;
        }
        for (int i = 0; i < MAX_STACKS; i++) {
            stackFlatAttack[i] = 0.0;
        }
        stackCount = 0;
        stacksExpireAt = Double.NEGATIVE_INFINITY;
    }

    /** Immutable complete stack state tied to its originating weapon instance. */
    private static final class ScarletSandsState implements State {
        private final StaffOfTheScarletSands source;
        private final double[] stackFlatAttack;
        private final int stackCount;
        private final double stacksExpireAt;

        private ScarletSandsState(
                StaffOfTheScarletSands source,
                double[] stackFlatAttack,
                int stackCount,
                double stacksExpireAt) {
            this.source = source;
            this.stackFlatAttack = stackFlatAttack.clone();
            this.stackCount = stackCount;
            this.stacksExpireAt = stacksExpireAt;
        }
    }
}
