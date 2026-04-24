package simulation;

import model.entity.Character;
import model.type.CharacterId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the active four-character party in a simulation run.
 * Tracks all party members by name and maintains a reference to the currently
 * on-field (active) character. Character switch-out callbacks are intentionally
 * delegated to {@link CombatSimulator#switchCharacter} so that the simulator can
 * supply the necessary combat context.
 */
public class Party {
    private final Map<CharacterId, Character> members = new LinkedHashMap<>();
    private final Map<String, CharacterId> idsByName = new LinkedHashMap<>();
    private Character activeCharacter;

    /**
     * Adds a character to the party, keyed by their name.
     * If this is the first character added, they become the active character.
     *
     * @param character the character to add
     */
    public void addMember(Character character) {
        members.put(character.getCharacterId(), character);
        idsByName.put(character.getName(), character.getCharacterId());
        if (activeCharacter == null) {
            activeCharacter = character;
        }
    }

    public Character getMember(CharacterId id) {
        return members.get(id);
    }

    /**
     * Returns the party member with the given name, or {@code null} if not found.
     *
     * @param name the character's name as registered via {@link #addMember}
     * @return the matching {@link Character}, or {@code null}
     */
    public Character getMember(String name) {
        CharacterId id = idsByName.get(name);
        return id != null ? members.get(id) : null;
    }

    public CharacterId resolveCharacterId(String name) {
        CharacterId id = idsByName.get(name);
        return id != null ? id : CharacterId.fromName(name);
    }

    /**
     * Returns the character currently on-field.
     *
     * @return the active {@link Character}
     */
    public Character getActiveCharacter() {
        return activeCharacter;
    }

    public void switchCharacter(CharacterId id) {
        Character target = members.get(id);
        if (target != null) {
            // Note: onSwitchOut logic is handled in CombatSimulator to provide context
            activeCharacter = target;
        }
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
        switchCharacter(resolveCharacterId(name));
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
