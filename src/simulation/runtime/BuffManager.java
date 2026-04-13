package simulation.runtime;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.entity.Character;
import simulation.CombatSimulator;

/**
 * Owns simulator-managed team buffs, field buffs, and buff collection rules.
 *
 * <p>This extracts buff storage and applicable-buff assembly from
 * {@link CombatSimulator} so the simulator can delegate buff policy rather than
 * embedding it directly.
 */
public class BuffManager {
    private final CombatSimulator sim;
    private final List<Buff> teamBuffs = new ArrayList<>();
    private final List<Buff> fieldBuffs = new ArrayList<>();

    /**
     * Creates a buff manager bound to the given simulator.
     *
     * @param sim active simulator whose party and active-character state are consulted
     */
    public BuffManager(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Adds a team-wide buff.
     *
     * @param buff buff to add
     */
    public void applyTeamBuff(Buff buff) {
        teamBuffs.add(buff);
    }

    /**
     * Adds a team-wide buff after removing any existing team buff with the same name.
     *
     * @param buff buff to add without stacking duplicate names
     */
    public void applyTeamBuffNoStack(Buff buff) {
        if (buff.getId() == BuffId.CUSTOM) {
            removeTeamBuffsByDisplayName(buff.getName());
        } else {
            removeTeamBuffsById(buff.getId());
        }
        teamBuffs.add(buff);
    }

    /**
     * Adds a field-only buff that applies only to the active character.
     *
     * @param buff field buff to add
     */
    public void applyFieldBuff(Buff buff) {
        fieldBuffs.add(buff);
    }

    /**
     * Collects all buffs currently applicable to the given character.
     *
     * <p>Sources aggregated are simulator-owned team buffs, weapon-provided team buffs,
     * character-provided team buffs, and active-character-only field buffs.
     *
     * @param character character whose applicable buffs are being queried
     * @return collected buff list
     */
    public List<Buff> getApplicableBuffs(Character character) {
        List<Buff> buffs = new ArrayList<>();
        for (Buff buff : teamBuffs) {
            if (buff.appliesToCharacter(character.getName(), character.getElement())) {
                buffs.add(buff);
            }
        }

        for (Character member : sim.getPartyMembers()) {
            if (member.getWeapon() != null) {
                buffs.addAll(member.getWeapon().getTeamBuffs(member));
            }
            buffs.addAll(member.getTeamBuffs());
        }

        if (character == sim.getActiveCharacter()) {
            buffs.addAll(fieldBuffs);
        }
        return buffs;
    }

    /**
     * Returns all buffs currently applicable to the active character.
     *
     * @return active character buff list
     */
    public List<Buff> getActiveCharacterBuffs() {
        return getApplicableBuffs(sim.getActiveCharacter());
    }

    /**
     * Returns the live simulator-owned team-buff list for package-local collaborators.
     *
     * @return mutable team-buff list
     */
    public List<Buff> getTeamBuffList() {
        return teamBuffs;
    }

    /**
     * Removes all simulator-owned team buffs with the given name.
     *
     * @param buffName buff name to remove
     */
    public void removeTeamBuffsById(BuffId buffId) {
        teamBuffs.removeIf(buff -> buff.getId() == buffId);
    }

    private void removeTeamBuffsByDisplayName(String buffName) {
        teamBuffs.removeIf(buff -> buff.getName().equals(buffName));
    }
}
