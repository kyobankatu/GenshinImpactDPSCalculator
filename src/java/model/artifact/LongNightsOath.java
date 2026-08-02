package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Long Night's Oath with independently expiring Radiance stacks.
 *
 * <p>The fixed two-piece bonus grants 25% Plunging Attack DMG. Positive
 * Plunging, Charged, and Skill hits by the bound owner grant one, two, and two
 * Radiance stacks respectively after the hit. Each stack grants another 15%
 * Plunging Attack DMG, up to five stacks. The three trigger categories have
 * independent one-second cooldowns. Every stack retains its own six-second
 * duration from the hit that granted it.</p>
 */
public class LongNightsOath extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        DamageTriggeredArtifactEffect {
    private static final int MAX_STACKS = 5;
    private static final double STACK_DAMAGE_BONUS = 0.15;
    private static final double STACK_DURATION = 6.0;
    private static final double TRIGGER_COOLDOWN = 1.0;

    private Character owner;
    private CombatSimulator simulator;

    /** Categories with independent cooldowns and stack gains. */
    private enum TriggerCategory {
        PLUNGE(1, BuffId.LONG_NIGHTS_OATH_PLUNGE_COOLDOWN),
        CHARGED(2, BuffId.LONG_NIGHTS_OATH_CHARGED_COOLDOWN),
        SKILL(2, BuffId.LONG_NIGHTS_OATH_SKILL_COOLDOWN);

        private final int stackGain;
        private final BuffId cooldownId;

        TriggerCategory(int stackGain, BuffId cooldownId) {
            this.stackGain = stackGain;
            this.cooldownId = cooldownId;
        }
    }

    /** Constructs Long Night's Oath with fresh fixed stats. */
    public LongNightsOath() {
        this(new StatsContainer());
    }

    /**
     * Constructs Long Night's Oath while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public LongNightsOath(StatsContainer stats) {
        super("Long Night's Oath", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.PLUNGING_ATTACK_DMG_BONUS, 0.25);
    }

    /**
     * Binds this stateful set to exactly one owner and simulator.
     *
     * @param equippedOwner character carrying this set
     * @param sim simulator containing the owner
     * @param startsActive whether the owner starts active
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Long Night's Oath is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Grants and refreshes Radiance after an eligible positive owner hit.
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the hit
     * @param damage final post-mitigation damage
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onDamage(
            CombatSimulator sim,
            AttackAction action,
            double damage,
            Character callbackOwner) {
        if (!matchesBinding(callbackOwner, sim)
                || action == null
                || !(damage > 0.0)) {
            return;
        }

        TriggerCategory category = classify(action);
        if (category == null) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (hasActiveBuff(category.cooldownId, currentTime)) {
            return;
        }
        replaceCooldown(category.cooldownId, currentTime);

        int activeStacks = activeStackCount(currentTime);
        int gainedStacks = Math.min(category.stackGain, MAX_STACKS - activeStacks);
        if (gainedStacks <= 0) {
            return;
        }
        addRadianceStacks(gainedStacks, currentTime);
    }

    /** Returns whether a callback belongs to the initialized artifact binding. */
    private boolean matchesBinding(
            Character callbackOwner,
            CombatSimulator callbackSimulator) {
        return owner != null
                && simulator != null
                && owner == callbackOwner
                && simulator == callbackSimulator;
    }

    /** Classifies one hit using its natural category before fallback flags. */
    private TriggerCategory classify(AttackAction action) {
        ActionType actionType = action.getActionType();
        if (actionType == ActionType.PLUNGE) {
            return TriggerCategory.PLUNGE;
        }
        if (actionType == ActionType.CHARGE) {
            return TriggerCategory.CHARGED;
        }
        if (actionType == ActionType.SKILL || action.isCountsAsSkillDmg()) {
            return TriggerCategory.SKILL;
        }
        return null;
    }

    /** Counts Radiance stacks active at one simulator timestamp. */
    private int activeStackCount(double currentTime) {
        int count = 0;
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == BuffId.LONG_NIGHTS_OATH_RADIANCE_STACK
                    && !buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    /** Replaces the selected trigger category's typed cooldown marker. */
    private void replaceCooldown(BuffId cooldownId, double currentTime) {
        owner.removeBuff(cooldownId);
        owner.addBuff(new SimpleBuff(
                "Long Night's Oath: Trigger Cooldown",
                cooldownId,
                TRIGGER_COOLDOWN,
                currentTime,
                stats -> {
                }).sourcedBy(owner.getCharacterId()));
    }

    /** Adds independently expiring Radiance stacks from one accepted hit. */
    private void addRadianceStacks(int stackCount, double currentTime) {
        for (int i = 0; i < stackCount; i++) {
            owner.addBuff(new SimpleBuff(
                    "Long Night's Oath: Radiance",
                    BuffId.LONG_NIGHTS_OATH_RADIANCE_STACK,
                    STACK_DURATION,
                    currentTime,
                    stats -> stats.add(
                            StatType.PLUNGING_ATTACK_DMG_BONUS,
                            STACK_DAMAGE_BONUS))
                    .sourcedBy(owner.getCharacterId()));
        }
    }

    /** Returns whether one typed owner buff is active at the supplied time. */
    private boolean hasActiveBuff(BuffId id, double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }
}
