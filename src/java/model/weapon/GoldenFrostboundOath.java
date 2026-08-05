package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
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

/**
 * Golden Frostbound Oath's owner-only representable contract.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. The owner permanently gains DEF; a positive Skill or
 * direct Lunar-Crystallize hit, or an actual owner Lunar-Crystallize reaction,
 * opens a six-second Geo and Lunar-Crystallize damage window. The nearby
 * Moondrift team branch remains inactive because the simulator has no typed
 * construct geometry.</p>
 */
public final class GoldenFrostboundOath extends Weapon implements
        DamageListener,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 6.0;

    private final int refinement;
    private final double permanentDefenseBonus;
    private final double ownerDamageBonus;
    private final double unavailableTeamDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Golden Frostbound Oath at refinement rank five. */
    public GoldenFrostboundOath() {
        this(5);
    }

    /**
     * Constructs Golden Frostbound Oath at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public GoldenFrostboundOath(int refinement) {
        super("Golden Frostbound Oath", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Golden Frostbound Oath refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        permanentDefenseBonus = 0.12 + 0.04 * refinement;
        ownerDamageBonus = 0.30 + 0.10 * refinement;
        unavailableTeamDamageBonus = 0.15 + 0.05 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional DEF bonus. */
    public double getPermanentDefenseBonus() {
        return permanentDefenseBonus;
    }

    /** Returns the represented owner Geo and Lunar-Crystallize bonus. */
    public double getOwnerDamageBonus() {
        return ownerDamageBonus;
    }

    /** Returns the source-backed but unavailable Moondrift team value. */
    public double getUnavailableTeamDamageBonus() {
        return unavailableTeamDamageBonus;
    }

    /** Returns whether the half-open owner window is active. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current half-open expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Applies permanent DEF and the live owner-only damage window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.DEF_PERCENT, permanentDefenseBonus);
        if (isWindowActive(currentTime)) {
            stats.add(StatType.GEO_DMG_BONUS, ownerDamageBonus);
            stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS,
                    ownerDamageBonus);
        }
    }

    /** Binds one equipped owner and both typed trigger routes. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Golden Frostbound Oath owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Golden Frostbound Oath is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Golden Frostbound Oath equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener(this);
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Opens the owner window after a positive Skill or direct Lunar hit. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isBoundOwner(actor)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || damage <= 0.0
                || (!isSkillDamage(action)
                        && !isDirectLunarCrystallize(action))) {
            return;
        }
        openWindow(currentTime);
    }

    /** Opens the owner window on an actual owner Lunar-Crystallize reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (activeSimulator == simulator
                && isBoundOwner(source)
                && result != null
                && result.getKind()
                        == ReactionResult.Kind.LUNAR_CRYSTALLIZE) {
            openWindow(time);
        }
    }

    /** Captures exact owner-window boundaries. */
    @Override
    public State captureWeaponState() {
        return new GoldenFrostboundState(
                this, activeFrom, activeUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof GoldenFrostboundState)) {
            throw new IllegalArgumentException(
                    "Golden Frostbound Oath state type is invalid");
        }
        GoldenFrostboundState restored =
                (GoldenFrostboundState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Golden Frostbound Oath state belongs to another instance");
        }
        activeFrom = restored.activeFrom;
        activeUntil = restored.activeUntil;
    }

    private void openWindow(double currentTime) {
        activeFrom = currentTime;
        activeUntil = currentTime + WINDOW_DURATION;
    }

    private boolean isBoundOwner(Character actor) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner);
    }

    private static boolean isSkillDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    private static boolean isDirectLunarCrystallize(
            AttackAction action) {
        return action.isLunarConsidered()
                && action.getLunarReactionType()
                        == AttackAction.LunarReactionType.CRYSTALLIZE;
    }

    private static final class GoldenFrostboundState implements State {
        private final GoldenFrostboundOath source;
        private final double activeFrom;
        private final double activeUntil;

        private GoldenFrostboundState(
                GoldenFrostboundOath source,
                double activeFrom,
                double activeUntil) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
        }
    }
}
