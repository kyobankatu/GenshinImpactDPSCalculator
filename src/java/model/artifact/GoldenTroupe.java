package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.SwitchAwareArtifact;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Golden Troupe artifact set with a field-state-dependent Skill DMG bonus.
 *
 * <p>The fixed stats contain the 2-piece 20% bonus and the unconditional
 * 4-piece 25% bonus. A further 25% applies while the owner is off field and
 * lingers for the half-open interval {@code [switchTime, switchTime + 2)}
 * after a standard switch in.</p>
 */
public class GoldenTroupe extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, SwitchAwareArtifact {
    private static final double SWITCH_IN_LINGER_DURATION = 2.0;

    private Character owner;
    private CombatSimulator simulator;
    private double switchInBonusUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Golden Troupe with a fresh fixed-stat container. */
    public GoldenTroupe() {
        this(new StatsContainer());
    }

    /**
     * Constructs Golden Troupe while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public GoldenTroupe(StatsContainer stats) {
        super("Golden Troupe", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.SKILL_DMG_BONUS, 0.45);
    }

    /**
     * Binds the set to its owner and simulator for field-state evaluation.
     *
     * <p>The initial field state is read from the bound simulator when stats
     * are assembled. Repeating the same binding is idempotent; an instance
     * cannot be rebound to a different owner or simulator.</p>
     *
     * @param equippedOwner character carrying this artifact set
     * @param sim simulator containing the equipped owner
     * @param startsActive whether the owner starts as the active character
     */
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
                        "Golden Troupe is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Starts the two-second on-field lingering window after a standard switch.
     *
     * @param sim simulator dispatching the switch
     * @param equippedOwner character receiving the switch-in callback
     */
    @Override
    public void onSwitchIn(CombatSimulator sim, Character equippedOwner) {
        validateCallback(equippedOwner, sim);
        switchInBonusUntil = sim.getCurrentTime() + SWITCH_IN_LINGER_DURATION;
    }

    /**
     * Makes the off-field bonus immediately available after a standard switch.
     *
     * @param sim simulator dispatching the switch
     * @param equippedOwner character receiving the switch-out callback
     */
    @Override
    public void onSwitchOut(CombatSimulator sim, Character equippedOwner) {
        validateCallback(equippedOwner, sim);
        switchInBonusUntil = Double.NEGATIVE_INFINITY;
    }

    /**
     * Applies the additional 25% Skill DMG bonus from current field state.
     *
     * <p>The active character is queried directly so fixture-only calls to
     * {@link CombatSimulator#setActiveCharacter(model.type.CharacterId)} remain
     * coherent even though they intentionally skip switch callbacks.</p>
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null) {
            return;
        }
        boolean isOffField = simulator.getActiveCharacter() != owner;
        boolean isLingeringOnField = simulator.getActiveCharacter() == owner
                && simulator.getCurrentTime() < switchInBonusUntil;
        if (isOffField || isLingeringOnField) {
            totalStats.add(StatType.SKILL_DMG_BONUS, 0.25);
        }
    }

    /** Validates that a switch callback belongs to this artifact binding. */
    private void validateCallback(Character callbackOwner, CombatSimulator callbackSimulator) {
        if (owner == null || simulator == null) {
            throw new IllegalStateException("Golden Troupe must be initialized before switch callbacks");
        }
        if (owner != callbackOwner || simulator != callbackSimulator) {
            throw new IllegalStateException("Golden Troupe received a callback for another binding");
        }
    }
}
