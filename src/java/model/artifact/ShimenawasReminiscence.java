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
import simulation.event.TimerEvent;

/**
 * Shimenawa's Reminiscence with its non-refreshable Skill-use attack window.
 *
 * <p>The two-piece bonus grants 18% ATK. If the bound owner has at least 15
 * Energy when casting a Skill, one ten-second window grants 50% Normal,
 * Charged, and Plunging Attack DMG. The Energy spend occurs seven frames after
 * the cast. Further Skill casts cannot refresh the window or spend Energy while
 * it remains active.</p>
 */
public class ShimenawasReminiscence extends ArtifactSet
        implements ActionTriggeredArtifactEffect, SimulatorInitializedArtifactEffect {
    private static final double ENERGY_COST = 15.0;
    private static final double ENERGY_SPEND_DELAY = 7.0 / 60.0;
    private static final double ATTACK_WINDOW_DURATION = 10.0;
    private static final double ATTACK_DAMAGE_BONUS = 0.50;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Shimenawa's Reminiscence with fresh fixed stats. */
    public ShimenawasReminiscence() {
        this(new StatsContainer());
    }

    /**
     * Constructs Shimenawa's Reminiscence while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ShimenawasReminiscence(StatsContainer stats) {
        super("Shimenawa's Reminiscence", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
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
                        "Shimenawa's Reminiscence is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Opens the attack window and schedules the delayed Energy spend.
     *
     * @param user artifact owner supplied by the action gateway
     * @param request accepted typed action request
     * @param sim simulator dispatching the action
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || user != owner
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }

        double castTime = sim.getCurrentTime();
        if (hasActiveWindow(castTime) || owner.getCurrentEnergy() < ENERGY_COST) {
            return;
        }

        owner.removeBuff(BuffId.SHIMENAWAS_REMINISCENCE_4PC);
        owner.addBuff(new SimpleBuff(
                "Shimenawa's Reminiscence: Four-Piece Bonus",
                BuffId.SHIMENAWAS_REMINISCENCE_4PC,
                ATTACK_WINDOW_DURATION,
                castTime,
                stats -> {
                    stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, ATTACK_DAMAGE_BONUS);
                    stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, ATTACK_DAMAGE_BONUS);
                    stats.add(StatType.PLUNGING_ATTACK_DMG_BONUS, ATTACK_DAMAGE_BONUS);
                }).sourcedBy(owner.getCharacterId()));
        scheduleEnergySpend(castTime + ENERGY_SPEND_DELAY);
    }

    /** Returns whether the typed attack window is active at the supplied time. */
    private boolean hasActiveWindow(double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == BuffId.SHIMENAWAS_REMINISCENCE_4PC
                    && buff.getStartTime() <= currentTime
                    && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    /** Registers the sourced seven-frame Energy spend as one immutable event. */
    private void scheduleEnergySpend(double triggerTime) {
        simulator.registerEvent(new TimerEvent() {
            @Override
            public double getNextTickTime() {
                return triggerTime;
            }

            @Override
            public void tick(CombatSimulator activeSimulator) {
                if (activeSimulator == simulator) {
                    owner.spendEnergy(ENERGY_COST);
                }
            }

            @Override
            public boolean isFinished(double currentTime) {
                return true;
            }
        });
    }
}
