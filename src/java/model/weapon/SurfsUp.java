package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.ActionTriggeredWeaponEffect;
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
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Surf's Up with Scorching Summer Normal-damage stacks. */
public final class SurfsUp extends Weapon implements
        ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int DISPLAYED_MAX_STACKS = 4;
    private static final int TEMPORARY_MAX_STACKS = 5;
    private static final double WINDOW_DURATION = 14.0;
    private static final double ACTIVATION_COOLDOWN = 15.0;
    private static final double STACK_GATE = 1.5;
    private static final double TEMPORARY_OVER_CAP_DURATION = 0.5;

    private final int refinement;
    private final double hpBonus;
    private final double normalDamageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stacks;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;
    private double nextLossTime = Double.NEGATIVE_INFINITY;
    private double nextGainTime = Double.NEGATIVE_INFINITY;
    private double temporaryOverCapUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Surf's Up at refinement rank five. */
    public SurfsUp() {
        this(5);
    }

    /** Constructs Surf's Up at the selected refinement rank. */
    public SurfsUp(int refinement) {
        super("Surf's Up", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        hpBonus = 0.15 + 0.05 * refinement;
        normalDamageBonusPerStack = 0.09 + 0.03 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Max HP bonus. */
    public double getHpBonus() {
        return hpBonus;
    }

    /** Returns the Normal damage bonus represented by one stack. */
    public double getNormalDamageBonusPerStack() {
        return normalDamageBonusPerStack;
    }

    /** Returns the raw live stack count, including a temporary fifth stack. */
    public int getStackCount(double currentTime) {
        normalizeAt(currentTime);
        return stacks;
    }

    /** Binds one equipped owner and registers actual reaction callbacks. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Surf's Up owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Surf's Up is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Surf's Up equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Applies permanent HP and up to four displayed Normal-damage stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        normalizeAt(currentTime);
        stats.add(StatType.HP_PERCENT, hpBonus);
        if (currentTime < activeUntil && stacks > 0) {
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS,
                    Math.min(DISPLAYED_MAX_STACKS, stacks)
                            * normalDamageBonusPerStack);
        }
    }

    /** Opens four stacks on an eligible active-owner Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.SKILL
                || activeSimulator.getActiveCharacter() != owner
                || activeSimulator.getCurrentTime() < nextActivationTime) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        stacks = DISPLAYED_MAX_STACKS;
        activeUntil = currentTime + WINDOW_DURATION;
        nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
        temporaryOverCapUntil = Double.NEGATIVE_INFINITY;
    }

    /** Loses one stack after an accepted active-owner Normal hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || activeSimulator.getActiveCharacter() != owner
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || action.getActionType() != ActionType.NORMAL) {
            return;
        }
        normalizeAt(currentTime);
        if (currentTime >= activeUntil || currentTime < nextLossTime) {
            return;
        }
        if (stacks > 0) {
            stacks--;
        }
        if (stacks <= DISPLAYED_MAX_STACKS) {
            temporaryOverCapUntil = Double.NEGATIVE_INFINITY;
        }
        nextLossTime = currentTime + STACK_GATE;
    }

    /** Gains one stack after an actual active-owner Vaporize reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(source, activeSimulator)
                || activeSimulator.getActiveCharacter() != owner
                || result == null
                || result.getKind() != ReactionResult.Kind.VAPORIZE) {
            return;
        }
        normalizeAt(currentTime);
        if (currentTime >= activeUntil || currentTime < nextGainTime) {
            return;
        }
        if (stacks < TEMPORARY_MAX_STACKS) {
            stacks++;
        }
        if (stacks == TEMPORARY_MAX_STACKS) {
            temporaryOverCapUntil = currentTime
                    + TEMPORARY_OVER_CAP_DURATION;
        }
        nextGainTime = currentTime + STACK_GATE;
    }

    /** Captures all stack, window, cooldown, and over-cap timestamps. */
    @Override
    public State captureWeaponState() {
        return new SurfsUpState(
                this,
                stacks,
                activeUntil,
                nextActivationTime,
                nextLossTime,
                nextGainTime,
                temporaryOverCapUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof SurfsUpState)) {
            throw new IllegalArgumentException("Surf's Up state type is invalid");
        }
        SurfsUpState restored = (SurfsUpState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Surf's Up state belongs to another instance");
        }
        stacks = restored.stacks;
        activeUntil = restored.activeUntil;
        nextActivationTime = restored.nextActivationTime;
        nextLossTime = restored.nextLossTime;
        nextGainTime = restored.nextGainTime;
        temporaryOverCapUntil = restored.temporaryOverCapUntil;
    }

    private void normalizeAt(double currentTime) {
        if (currentTime >= activeUntil) {
            stacks = 0;
            temporaryOverCapUntil = Double.NEGATIVE_INFINITY;
            return;
        }
        if (stacks == TEMPORARY_MAX_STACKS
                && currentTime >= temporaryOverCapUntil) {
            stacks = DISPLAYED_MAX_STACKS;
            temporaryOverCapUntil = Double.NEGATIVE_INFINITY;
        }
    }

    private boolean isBoundOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && user == owner
                && activeSimulator == simulator
                && owner.getWeapon() == this;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Surf's Up refinement must be between 1 and 5");
        }
    }

    /** Immutable mutable-state payload tied to one Surf's Up instance. */
    private static final class SurfsUpState implements State {
        private final SurfsUp source;
        private final int stacks;
        private final double activeUntil;
        private final double nextActivationTime;
        private final double nextLossTime;
        private final double nextGainTime;
        private final double temporaryOverCapUntil;

        private SurfsUpState(
                SurfsUp source,
                int stacks,
                double activeUntil,
                double nextActivationTime,
                double nextLossTime,
                double nextGainTime,
                double temporaryOverCapUntil) {
            this.source = source;
            this.stacks = stacks;
            this.activeUntil = activeUntil;
            this.nextActivationTime = nextActivationTime;
            this.nextLossTime = nextLossTime;
            this.nextGainTime = nextGainTime;
            this.temporaryOverCapUntil = temporaryOverCapUntil;
        }
    }
}
