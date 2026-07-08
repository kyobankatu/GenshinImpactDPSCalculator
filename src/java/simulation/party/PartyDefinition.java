package simulation.party;

import java.util.List;
import java.util.Map;

import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Single source of truth for one runnable and optionally RL-trainable party.
 */
public interface PartyDefinition {
    String name();

    String displayName();

    CharacterId[] partyOrder();

    Map<CharacterId, List<StatType>> optimizationTargets();

    CombatSimulator createSimulator(
            Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls);

    void executeRotation(CombatSimulator sim);

    default boolean rlEnabled() {
        return true;
    }

    default boolean publishDocsReport() {
        return false;
    }
}
