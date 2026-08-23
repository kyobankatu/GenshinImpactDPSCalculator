package simulation.party;

import java.util.List;
import java.util.Map;
import java.util.Set;

import mechanics.rotation.PolicyAction;
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

    /** Returns the immutable dataset partition for this exact scenario. */
    DatasetSplit datasetSplit();

    /** Returns a stable identity for character, constellation, weapon, and artifact choices. */
    String loadoutFingerprint();

    /** Returns the intended duration of one repeatable rotation cycle. */
    double rotationCycleSeconds();

    /** Returns an optional deterministic seed rotation in policy-action IDs. */
    int[] baselinePolicyActions();

    /** Returns actions whose exact support is required for this scenario. */
    Map<CharacterId, Set<PolicyAction>> requiredActionCapabilities();

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
