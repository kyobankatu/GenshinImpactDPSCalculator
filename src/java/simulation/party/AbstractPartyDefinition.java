package simulation.party;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import mechanics.rotation.PolicyAction;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

abstract class AbstractPartyDefinition implements PartyDefinition {
    private static final int BASELINE_SWAP_WAIT_ACTIONS = 10;

    @Override
    public DatasetSplit datasetSplit() {
        return DatasetSplit.TRAIN;
    }

    @Override
    public String loadoutFingerprint() {
        StringBuilder fingerprint = new StringBuilder("loadout-v1");
        for (CharacterId characterId : partyOrder()) {
            fingerprint.append(':').append(characterId.name());
        }
        return fingerprint.toString();
    }

    @Override
    public double rotationCycleSeconds() {
        return 20.0;
    }

    @Override
    public int[] baselinePolicyActions() {
        return new int[0];
    }

    @Override
    public Map<CharacterId, Set<PolicyAction>> requiredActionCapabilities() {
        Map<CharacterId, Set<PolicyAction>> required = new LinkedHashMap<>();
        for (CharacterId characterId : partyOrder()) {
            required.put(characterId, Set.of(PolicyAction.NORMAL));
        }
        return required;
    }

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

    protected static int[] policyActions(PolicyAction... actions) {
        int swapCount = 0;
        for (PolicyAction action : actions) {
            if (action.isSwap()) {
                swapCount++;
            }
        }
        int[] ids = new int[actions.length
                + Math.max(0, swapCount - 1) * BASELINE_SWAP_WAIT_ACTIONS];
        int offset = 0;
        boolean previousSwapSeen = false;
        for (PolicyAction action : actions) {
            if (action.isSwap() && previousSwapSeen) {
                for (int wait = 0; wait < BASELINE_SWAP_WAIT_ACTIONS; wait++) {
                    ids[offset++] = PolicyAction.WAIT_SHORT.getId();
                }
            }
            ids[offset++] = action.getId();
            previousSwapSeen |= action.isSwap();
        }
        return ids;
    }
}
