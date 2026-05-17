package model.entity;

import java.util.List;

import mechanics.buff.Buff;

/**
 * Capability for characters that contribute team-wide buffs outside their own stats.
 */
public interface CharacterTeamBuffProvider {
    /**
     * Returns the list of team-wide buffs this character contributes to all
     * party members (used by the stat assembler when computing other
     * characters' effective stats).
     *
     * @return list of team buffs (never {@code null})
     */
    List<Buff> getTeamBuffs();
}
