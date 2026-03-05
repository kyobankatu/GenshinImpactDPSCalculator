package model.entity;

import model.stats.StatsContainer;

/**
 * Represents an artifact set (e.g. "Emblem of Severed Fate 4pc") equipped on a
 * {@link Character}.
 *
 * <p>An artifact set contributes fixed stats via {@link #getStats()} and may
 * modify stats dynamically through {@link #applyPassive}. Event hooks allow
 * set bonuses that trigger on burst use, reactions, character switches, or
 * damage events.
 *
 * <p>All hook methods have no-op default implementations. Specific set bonuses
 * should subclass {@code ArtifactSet} and override only the relevant hooks.
 */
public class ArtifactSet {
    private String name; // e.g., "Emblem of Severed Fate (4pc)"
    private StatsContainer stats;

    /**
     * Constructs an artifact set with the given name and flat stat container.
     *
     * @param name  display name of the set (e.g. "Emblem of Severed Fate (4pc)")
     * @param stats stats contributed by the set's fixed bonuses (main stats,
     *              2pc bonus)
     */
    public ArtifactSet(String name, StatsContainer stats) {
        this.name = name;
        this.stats = stats;
    }

    /**
     * Returns the artifact set's flat stat container.
     *
     * @return stats container
     */
    public StatsContainer getStats() {
        return stats;
    }

    /**
     * Returns the artifact set's display name.
     *
     * @return artifact set name
     */
    public String getName() {
        return name;
    }

    /**
     * Applies the artifact set's dynamic passive bonus to the provided stats
     * container. Called during stat compilation after flat stats have been
     * merged. Example: Emblem 4pc converts ER% into Burst DMG%.
     * Default implementation is a no-op.
     *
     * @param totalStats the aggregated stats container to mutate in-place
     */
    public void applyPassive(StatsContainer totalStats) {
        // Default: do nothing
    }

    /**
     * Called by the simulator when the equipped character uses their elemental
     * burst. Use this hook for set bonuses that proc on burst use.
     * Default implementation is a no-op.
     *
     * @param sim the active combat simulator
     */
    public void onBurst(simulation.CombatSimulator sim) {
        // Default: do nothing
    }

    /**
     * Called by the simulator when an elemental reaction is triggered on or by
     * the equipped character. Use this hook for reaction-triggered set bonuses.
     * Default implementation is a no-op.
     *
     * @param sim       the active combat simulator
     * @param result    the reaction result describing the reaction type and data
     * @param triggerCh the character whose hit triggered the reaction
     * @param owner     the character who has this artifact set equipped
     */
    public void onReaction(simulation.CombatSimulator sim, mechanics.reaction.ReactionResult result,
            model.entity.Character triggerCh, model.entity.Character owner) {
        // Default: do nothing
    }

    /**
     * Called by the simulator when the equipped character switches onto the
     * field. Use this hook for on-switch-in set bonuses.
     * Default implementation is a no-op.
     *
     * @param sim   the active combat simulator
     * @param owner the character who has this artifact set equipped
     */
    public void onSwitchIn(simulation.CombatSimulator sim, model.entity.Character owner) {
        // Default: do nothing
    }

    /**
     * Called by the simulator when the equipped character switches off the
     * field. Use this hook for on-switch-out set bonuses.
     * Default implementation is a no-op.
     *
     * @param sim   the active combat simulator
     * @param owner the character who has this artifact set equipped
     */
    public void onSwitchOut(simulation.CombatSimulator sim, model.entity.Character owner) {
        // Default: do nothing
    }

    /**
     * Called by the simulator after the equipped character deals a damage
     * instance. Use this hook for on-hit set bonuses (e.g. stacking mechanics).
     * Default implementation is a no-op.
     *
     * @param sim    the active combat simulator
     * @param action the attack action that produced the damage
     * @param damage the final damage value dealt
     * @param owner  the character who has this artifact set equipped
     */
    public void onDamage(simulation.CombatSimulator sim, simulation.action.AttackAction action, double damage,
            model.entity.Character owner) {
        // Default: do nothing
    }
}
