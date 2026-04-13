package model.entity;

import java.util.List;

import mechanics.buff.Buff;

/**
 * Capability for weapons that contribute team-wide buffs.
 */
public interface WeaponTeamBuffProvider {
    List<Buff> getTeamBuffs(Character owner);
}
