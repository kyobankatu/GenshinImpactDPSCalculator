package model.entity;

import java.util.List;

import mechanics.buff.Buff;
import simulation.CombatSimulator;

/**
 * Capability for artifact sets that contribute dynamically resolved team-wide
 * buffs.
 */
public interface ArtifactTeamBuffProvider {
    /**
     * Returns the team-wide buffs supplied by this artifact set.
     *
     * @param owner character equipping the artifact set
     * @param sim active simulator used to resolve party-dependent effects
     * @return artifact-provided team buffs, never {@code null}
     */
    List<Buff> getArtifactTeamBuffs(Character owner, CombatSimulator sim);
}
