package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.ElementalReactionTriggeredWeaponEffect;
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
 * Verdict with its permanent ATK passive and representable Seal mechanics.
 *
 * <p>Actual party Lunar-Crystallize reactions grant one Seal at most once per
 * second. Up to two Seals share a refreshable 15-second lifetime, and each
 * grants Skill DMG according to refinement. The first owner Skill hit starts a
 * 0.2-second damage window, after which every Seal is consumed.</p>
 *
 * <p>Crystallize shard pickup and shield creation are not observable through
 * the current simulator contracts, so ordinary Crystallize reactions do not
 * grant Seals. Durations are exact half-open simulation-time windows because
 * hitlag extension is unavailable.</p>
 */
public final class Verdict extends Weapon implements
        DamageTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_SEALS = 2;
    private static final double SEAL_DURATION = 15.0;
    private static final double SEAL_GAIN_COOLDOWN = 1.0;
    private static final double CONSUMPTION_DELAY = 0.2;

    private final int refinement;
    private final double attackBonus;
    private final double skillDamageBonusPerSeal;
    private Character owner;
    private CombatSimulator simulator;
    private int sealCount;
    private double sealsExpireAt = Double.NEGATIVE_INFINITY;
    private double nextSealGainAt = Double.NEGATIVE_INFINITY;
    private double consumeSealsAt = Double.POSITIVE_INFINITY;

    /** Constructs Verdict at refinement rank five. */
    public Verdict() {
        this(5);
    }

    /**
     * Constructs Verdict at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Verdict(int refinement) {
        super("Verdict", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Verdict refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackBonus = 0.15 + 0.05 * refinement;
        skillDamageBonusPerSeal = 0.135 + 0.045 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the Skill DMG bonus granted by each Seal. */
    public double getSkillDamageBonusPerSeal() {
        return skillDamageBonusPerSeal;
    }

    /** Returns the number of Seals alive at the supplied simulation time. */
    public int getSealCount(double currentTime) {
        expireSealsAt(currentTime);
        return sealCount;
    }

    /** Returns the shared Seal expiration timestamp. */
    public double getSealsExpireAt() {
        return sealsExpireAt;
    }

    /** Applies permanent ATK and the current Seal-derived Skill bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireSealsAt(currentTime);
        stats.add(StatType.ATK_PERCENT, attackBonus);
        stats.add(
                StatType.SKILL_DMG_BONUS,
                skillDamageBonusPerSeal * sealCount);
    }

    /** Binds this mutable weapon to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Verdict owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Verdict is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Verdict equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /**
     * Gains one Seal from an actual party Lunar-Crystallize reaction.
     *
     * <p>The one-second acquisition cooldown is shared across the party. A
     * successful gain at the cap still refreshes the shared Seal lifetime.</p>
     */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (!isBoundPartySource(source, activeSimulator)
                || result == null
                || result.getKind()
                        != ReactionResult.Kind.LUNAR_CRYSTALLIZE
                || time < nextSealGainAt) {
            return;
        }
        expireSealsAt(time);
        if (sealCount < MAX_SEALS) {
            sealCount++;
        }
        sealsExpireAt = time + SEAL_DURATION;
        nextSealGainAt = time + SEAL_GAIN_COOLDOWN;
    }

    /** Starts one shared 0.2-second consumption delay after an owner Skill hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || (action.getActionType() != ActionType.SKILL
                        && !action.isCountsAsSkillDmg())) {
            return;
        }
        expireSealsAt(currentTime);
        if (sealCount > 0
                && consumeSealsAt == Double.POSITIVE_INFINITY) {
            consumeSealsAt = currentTime + CONSUMPTION_DELAY;
        }
    }

    /** Captures Seals, both timing gates, and pending consumption state. */
    @Override
    public State captureWeaponState() {
        return new VerdictState(
                this,
                sealCount,
                sealsExpireAt,
                nextSealGainAt,
                consumeSealsAt);
    }

    /** Restores state captured from this exact Verdict instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof VerdictState)) {
            throw new IllegalArgumentException(
                    "Verdict state type is invalid");
        }
        VerdictState restored = (VerdictState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Verdict state belongs to another instance");
        }
        sealCount = restored.sealCount;
        sealsExpireAt = restored.sealsExpireAt;
        nextSealGainAt = restored.nextSealGainAt;
        consumeSealsAt = restored.consumeSealsAt;
    }

    private boolean isBoundOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && actor == owner
                && owner.getWeapon() == this;
    }

    private boolean isBoundPartySource(
            Character source,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && source != null
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(source);
    }

    private void expireSealsAt(double currentTime) {
        if (sealCount == 0) {
            return;
        }
        if (currentTime >= sealsExpireAt
                || currentTime >= consumeSealsAt) {
            sealCount = 0;
            sealsExpireAt = Double.NEGATIVE_INFINITY;
            consumeSealsAt = Double.POSITIVE_INFINITY;
        }
    }

    /** Immutable runtime state tied to one Verdict instance. */
    private static final class VerdictState implements State {
        private final Verdict source;
        private final int sealCount;
        private final double sealsExpireAt;
        private final double nextSealGainAt;
        private final double consumeSealsAt;

        private VerdictState(
                Verdict source,
                int sealCount,
                double sealsExpireAt,
                double nextSealGainAt,
                double consumeSealsAt) {
            this.source = source;
            this.sealCount = sealCount;
            this.sealsExpireAt = sealsExpireAt;
            this.nextSealGainAt = nextSealGainAt;
            this.consumeSealsAt = consumeSealsAt;
        }
    }
}
