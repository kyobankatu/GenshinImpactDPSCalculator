package model.weapon;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Shared Golden Majesty hit stacks for the four Liyue five-star weapons.
 *
 * <p>Eligible on-field Normal, Charged, Skill, and Burst hits gain one ATK
 * stack after damage resolution. The shared stack window is refreshed to eight
 * seconds and the trigger is gated for 0.3 seconds. Both pieces of state live
 * as typed owner buffs, so ordinary simulator snapshot restore reconstructs
 * the stack count, window, and trigger gate without weapon-local counters.
 *
 * <p>Shield Strength and the shielded 100% stack enhancement are intentionally
 * inactive because the simulator does not expose player shield state.
 */
public abstract class GoldenMajestyWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect,
        DamageTriggeredWeaponEffect {
    private static final int MAX_STACKS = 5;
    private static final double STACK_DURATION = 8.0;
    private static final double STACK_COOLDOWN = 0.3;

    private final int refinement;
    private final double shieldStrengthBonus;
    private final double attackBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;

    /**
     * Constructs one Lv. 90 Golden Majesty family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param refinement refinement rank in the inclusive range 1-5
     */
    protected GoldenMajestyWeapon(
            String name,
            WeaponType weaponType,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Golden Majesty refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.shieldStrengthBonus = 0.15 + 0.05 * refinement;
        this.attackBonusPerStack = 0.03 + 0.01 * refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.ATK_PERCENT, 0.496);
    }

    /** Returns the selected refinement rank. */
    public final int getRefinement() {
        return refinement;
    }

    /** Returns the sourced but currently inactive Shield Strength coefficient. */
    public final double getShieldStrengthBonus() {
        return shieldStrengthBonus;
    }

    /** Returns the representable unshielded ATK bonus granted per stack. */
    public final double getAttackBonusPerStack() {
        return attackBonusPerStack;
    }

    /** Binds one stateful weapon instance to one owner and simulator. */
    @Override
    public final void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        getName() + " is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Gains or refreshes one unshielded ATK stack after an eligible owner hit.
     *
     * <p>Zero-damage attacks remain eligible because this callback represents
     * a resolved target hit. Skill- and Burst-classified follow-ups are accepted
     * even when their direct action category is {@link ActionType#OTHER}.
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || sim.getActiveCharacter() != owner
                || action == null
                || !isEligibleHit(action)
                || hasActiveBuff(
                        BuffId.GOLDEN_MAJESTY_STACK_COOLDOWN,
                        currentTime)) {
            return;
        }

        GoldenMajestyStackBuff current = findStackBuff(currentTime);
        int stackCount = current == null ? 1
                : Math.min(MAX_STACKS, current.getStackCount() + 1);

        owner.removeBuff(BuffId.GOLDEN_MAJESTY_ATK_STACKS);
        owner.removeBuff(BuffId.GOLDEN_MAJESTY_STACK_COOLDOWN);
        owner.addBuff(new GoldenMajestyStackBuff(
                stackCount, attackBonusPerStack, currentTime));
        owner.addBuff(new SimpleBuff(
                "Golden Majesty Stack Cooldown",
                BuffId.GOLDEN_MAJESTY_STACK_COOLDOWN,
                STACK_COOLDOWN,
                currentTime,
                stats -> {
                    // This typed marker carries timing only.
                }));
    }

    /** Applies no unconditional passive beyond the weapon's fixed substat. */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        // Shield Strength has no representable player-state stat.
    }

    /** Returns whether one resolved action belongs to a sourced hit category. */
    private boolean isEligibleHit(AttackAction action) {
        ActionType actionType = action.getActionType();
        return actionType == ActionType.NORMAL
                || actionType == ActionType.CHARGE
                || actionType == ActionType.SKILL
                || actionType == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }

    /** Returns whether an owner marker remains active at the supplied time. */
    private boolean hasActiveBuff(BuffId id, double currentTime) {
        if (owner == null) {
            return false;
        }
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the active immutable stack buff, or {@code null}. */
    private GoldenMajestyStackBuff findStackBuff(double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff instanceof GoldenMajestyStackBuff
                    && !buff.isExpired(currentTime)) {
                return (GoldenMajestyStackBuff) buff;
            }
        }
        return null;
    }

    /** Immutable visible stack state retained directly by character snapshots. */
    private static final class GoldenMajestyStackBuff extends SimpleBuff {
        private final int stackCount;

        private GoldenMajestyStackBuff(
                int stackCount,
                double attackBonusPerStack,
                double currentTime) {
            super(
                    "Golden Majesty ATK (" + stackCount + ")",
                    BuffId.GOLDEN_MAJESTY_ATK_STACKS,
                    STACK_DURATION,
                    currentTime,
                    stats -> stats.add(
                            StatType.ATK_PERCENT,
                            attackBonusPerStack * stackCount));
            this.stackCount = stackCount;
        }

        private int getStackCount() {
            return stackCount;
        }
    }
}
