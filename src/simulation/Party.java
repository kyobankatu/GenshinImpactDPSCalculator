package simulation;

import model.entity.Character;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the active four-character party in a simulation run.
 * Tracks all party members by name and maintains a reference to the currently
 * on-field (active) character. Character switch-out callbacks are intentionally
 * delegated to {@link CombatSimulator#switchCharacter} so that the simulator can
 * supply the necessary combat context.
 */
public class Party {
    private Map<String, Character> members = new HashMap<>();
    private Character activeCharacter;

    /**
     * Adds a character to the party, keyed by their name.
     * If this is the first character added, they become the active character.
     *
     * @param character the character to add
     */
    public void addMember(Character character) {
        members.put(character.getName(), character);
        if (activeCharacter == null) {
            activeCharacter = character;
        }
    }

    /**
     * Returns the party member with the given name, or {@code null} if not found.
     *
     * @param name the character's name as registered via {@link #addMember}
     * @return the matching {@link Character}, or {@code null}
     */
    public Character getMember(String name) {
        return members.get(name);
    }

    /**
     * Returns the character currently on-field.
     *
     * @return the active {@link Character}
     */
    public Character getActiveCharacter() {
        return activeCharacter;
    }

    /**
     * Switches the active character to the party member with the given name.
     * Does nothing if the name is not in the party.
     * Note: switch-out side effects (e.g. {@code onSwitchOut}) are handled by
     * {@link CombatSimulator#switchCharacter} to preserve combat context.
     *
     * @param name the name of the character to switch to
     */
    public void switchCharacter(String name) {
        if (members.containsKey(name)) {
            // Note: onSwitchOut logic is handled in CombatSimulator to provide context
            activeCharacter = members.get(name);
        }
    }

    /**
     * Directly applies a buff to every member of the party.
     *
     * @param buff the {@link mechanics.buff.Buff} to distribute
     */
    public void applyTeamBuff(mechanics.buff.Buff buff) {
        for (Character c : members.values()) {
            c.addBuff(buff);
        }
    }

    /**
     * Returns a view of all party members.
     *
     * @return collection of all {@link Character} objects in the party
     */
    public java.util.Collection<Character> getMembers() {
        return members.values();
    }
}
