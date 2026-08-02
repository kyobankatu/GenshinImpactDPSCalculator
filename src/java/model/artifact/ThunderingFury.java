package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareArtifact;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/** Thundering Fury with reaction bonuses and on-field Skill cooldown reduction. */
public class ThunderingFury extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, ReactionAwareArtifact {
    private static final double TRIGGER_COOLDOWN = 0.8;
    private static final double SKILL_COOLDOWN_REDUCTION = 1.0;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Thundering Fury with fresh stats. */
    public ThunderingFury() {
        this(new StatsContainer());
    }

    /**
     * Constructs Thundering Fury while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ThunderingFury(StatsContainer stats) {
        super("Thundering Fury", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ELECTRO_DMG_BONUS, 0.15);
        getStats().add(StatType.OVERLOAD_DMG_BONUS, 0.40);
        getStats().add(StatType.ELECTRO_CHARGED_DMG_BONUS, 0.40);
        getStats().add(StatType.SUPERCONDUCT_DMG_BONUS, 0.40);
        getStats().add(StatType.HYPERBLOOM_DMG_BONUS, 0.40);
        getStats().add(StatType.AGGRAVATE_DMG_BONUS, 0.20);
        getStats().add(StatType.LUNAR_CHARGED_DMG_BONUS, 0.20);
    }

    /** Binds this mutable set to exactly one owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException("Thundering Fury is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Reduces Skill cooldown after an eligible on-field owner reaction. */
    @Override
    public void onReaction(
            CombatSimulator sim,
            ReactionResult result,
            Character triggerCharacter,
            Character callbackOwner) {
        if (owner == null
                || simulator == null
                || sim != simulator
                || triggerCharacter != owner
                || callbackOwner != owner
                || sim.getActiveCharacter() != owner
                || !isEligible(result)
                || hasTriggerCooldown(sim.getCurrentTime())) {
            return;
        }

        owner.addBuff(new SimpleBuff(
                "Thundering Fury: Trigger Cooldown",
                BuffId.THUNDERING_FURY_4PC_TRIGGER_COOLDOWN,
                TRIGGER_COOLDOWN,
                sim.getCurrentTime(),
                stats -> {
                }).sourcedBy(owner.getCharacterId()));
        owner.reduceSkillCooldown(
                sim.getCurrentTime(),
                SKILL_COOLDOWN_REDUCTION);
    }

    /** Returns whether a reaction is listed by the four-piece cooldown effect. */
    private boolean isEligible(ReactionResult result) {
        if (result == null || result.getKind() == null) {
            return false;
        }
        switch (result.getKind()) {
            case OVERLOAD:
            case OVERLOADED:
            case ELECTRO_CHARGED:
            case SUPERCONDUCT:
            case HYPERBLOOM:
            case AGGRAVATE:
            case QUICKEN:
            case LUNAR_CHARGED:
                return true;
            default:
                return false;
        }
    }

    /** Returns whether the owner-local 0.8-second trigger gate is active. */
    private boolean hasTriggerCooldown(double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == BuffId.THUNDERING_FURY_4PC_TRIGGER_COOLDOWN
                    && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }
}
