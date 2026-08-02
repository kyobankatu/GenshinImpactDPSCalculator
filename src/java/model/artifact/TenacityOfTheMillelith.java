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
 * Tenacity of the Millelith with its Skill-hit team ATK window.
 *
 * <p>The fixed two-piece bonus grants 20% HP. After a Skill hit by the bound
 * owner, including an off-field or zero-damage hit, one non-stacking team buff
 * grants 20% ATK for three seconds. The owner-local trigger cooldown is 0.5
 * seconds. Shield Strength is outside the simulator's outgoing-damage model.</p>
 */
public class TenacityOfTheMillelith extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        DamageTriggeredArtifactEffect {
    private static final double TRIGGER_COOLDOWN = 0.5;
    private static final double TEAM_WINDOW_DURATION = 3.0;
    private static final double TEAM_ATK_BONUS = 0.20;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Tenacity of the Millelith with fresh fixed stats. */
    public TenacityOfTheMillelith() {
        this(new StatsContainer());
    }

    /**
     * Constructs Tenacity of the Millelith while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public TenacityOfTheMillelith(StatsContainer stats) {
        super("Tenacity of the Millelith", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HP_PERCENT, 0.20);
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
                        "Tenacity of the Millelith is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes the team ATK window after an eligible Skill hit.
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the hit
     * @param damage final post-mitigation damage, which may be zero
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
                || !isSkillDamage(action)) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (hasActiveCooldown(currentTime)) {
            return;
        }
        replaceCooldown(currentTime);

        SimpleBuff teamAttackWindow = new SimpleBuff(
                "Tenacity of the Millelith: Four-Piece Bonus",
                BuffId.TENACITY_OF_THE_MILLELITH_TEAM_ATK,
                TEAM_WINDOW_DURATION,
                currentTime,
                stats -> stats.add(StatType.ATK_PERCENT, TEAM_ATK_BONUS));
        sim.applyTeamBuffNoStack(
                teamAttackWindow.sourcedBy(owner.getCharacterId()));
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

    /** Returns whether the resolved hit uses Skill damage metadata. */
    private boolean isSkillDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    /** Replaces expired cooldown history with one owner-local typed marker. */
    private void replaceCooldown(double currentTime) {
        owner.removeBuff(BuffId.TENACITY_OF_THE_MILLELITH_TRIGGER_COOLDOWN);
        owner.addBuff(new SimpleBuff(
                "Tenacity of the Millelith: Trigger Cooldown",
                BuffId.TENACITY_OF_THE_MILLELITH_TRIGGER_COOLDOWN,
                TRIGGER_COOLDOWN,
                currentTime,
                stats -> {
                }).sourcedBy(owner.getCharacterId()));
    }

    /** Returns whether the owner-local trigger cooldown is active. */
    private boolean hasActiveCooldown(double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId()
                            == BuffId.TENACITY_OF_THE_MILLELITH_TRIGGER_COOLDOWN
                    && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }
}
