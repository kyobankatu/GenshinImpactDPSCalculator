package simulation.party;

import java.util.HashMap;
import java.util.Map;

import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

abstract class AbstractPartyDefinition implements PartyDefinition {
    protected static Map<CharacterId, Double> safeErTargets(Map<CharacterId, Double> erTargets) {
        return erTargets != null ? erTargets : new HashMap<>();
    }

    protected static Map<CharacterId, Map<StatType, Integer>> safeRolls(
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        return partyManualRolls != null ? partyManualRolls : new HashMap<>();
    }

    protected static void normal(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.NORMAL));
    }

    protected static void charge(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.CHARGE));
    }

    protected static void skill(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.SKILL));
    }

    protected static void burst(CombatSimulator sim, CharacterId characterId) {
        sim.performAction(characterId, CharacterActionRequest.of(CharacterActionKey.BURST));
    }
}
