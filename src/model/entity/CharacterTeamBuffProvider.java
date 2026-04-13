package model.entity;

import java.util.List;

import mechanics.buff.Buff;

/**
 * Capability for characters that contribute team-wide buffs outside their own stats.
 */
public interface CharacterTeamBuffProvider {
    List<Buff> getTeamBuffs();
}
