package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ActionTriggeredArtifactEffect;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Crimson Witch of Flames with typed reaction bonuses and Skill stacks. */
public class CrimsonWitchOfFlames extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, ActionTriggeredArtifactEffect {
    private static final double STACK_DURATION = 10.0;
    private static final double PYRO_BONUS_PER_STACK = 0.075;
    private static final int MAX_STACKS = 3;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Crimson Witch of Flames with fresh stats. */
    public CrimsonWitchOfFlames() {
        this(new StatsContainer());
    }

    /**
     * Constructs Crimson Witch while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public CrimsonWitchOfFlames(StatsContainer stats) {
        super("Crimson Witch of Flames", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.PYRO_DMG_BONUS, 0.15);
        getStats().add(StatType.OVERLOAD_DMG_BONUS, 0.40);
        getStats().add(StatType.BURNING_DMG_BONUS, 0.40);
        getStats().add(StatType.BURGEON_DMG_BONUS, 0.40);
        getStats().add(StatType.VAPORIZE_DMG_BONUS, 0.15);
        getStats().add(StatType.MELT_DMG_BONUS, 0.15);
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
                throw new IllegalStateException(
                        "Crimson Witch of Flames is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Adds one Skill-use stack and refreshes the shared ten-second duration. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (owner == null
                || simulator == null
                || user != owner
                || sim != simulator
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }

        int stackCount = 0;
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff instanceof CrimsonWitchStackBuff
                    && !buff.isExpired(sim.getCurrentTime())) {
                stackCount = ((CrimsonWitchStackBuff) buff).getStackCount();
                break;
            }
        }
        owner.removeBuff(BuffId.CRIMSON_WITCH_4PC_PYRO_STACK);
        owner.addBuff(new CrimsonWitchStackBuff(
                Math.min(MAX_STACKS, stackCount + 1),
                sim.getCurrentTime()).sourcedBy(owner.getCharacterId()));
    }

    /** One refreshed typed buff carrying the current shared-duration stack count. */
    private static final class CrimsonWitchStackBuff extends SimpleBuff {
        private final int stackCount;

        private CrimsonWitchStackBuff(int stackCount, double currentTime) {
            super(
                    "Crimson Witch: Pyro Stack",
                    BuffId.CRIMSON_WITCH_4PC_PYRO_STACK,
                    STACK_DURATION,
                    currentTime,
                    stats -> stats.add(
                            StatType.PYRO_DMG_BONUS,
                            stackCount * PYRO_BONUS_PER_STACK));
            this.stackCount = stackCount;
        }

        private int getStackCount() {
            return stackCount;
        }
    }
}
