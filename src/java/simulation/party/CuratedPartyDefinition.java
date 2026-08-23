package simulation.party;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import mechanics.element.ResonanceManager;
import mechanics.rotation.PolicyAction;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;

/** Shared immutable setup and baseline executor for curated RL scenarios. */
abstract class CuratedPartyDefinition extends AbstractPartyDefinition {
    private static final double WAIT_SECONDS = 0.1;

    private final String name;
    private final DatasetSplit datasetSplit;
    private final String loadoutFingerprint;
    private final double cycleSeconds;
    private final List<Supplier<Character>> characterFactories;
    private final CharacterId[] partyOrder;
    private final int[] baselineActions;
    private final Map<CharacterId, Set<PolicyAction>> requiredActions;

    /** Creates one exact C0/base-loadout scenario. */
    CuratedPartyDefinition(
            String name,
            DatasetSplit datasetSplit,
            double cycleSeconds,
            List<Supplier<Character>> characterFactories,
            int[] baselineActions,
            Map<CharacterId, Set<PolicyAction>> requiredActions) {
        if (characterFactories == null || characterFactories.size() != 4) {
            throw new IllegalArgumentException("Curated party requires four character factories");
        }
        this.name = name;
        this.datasetSplit = datasetSplit;
        this.cycleSeconds = cycleSeconds;
        this.characterFactories = List.copyOf(characterFactories);
        this.baselineActions = baselineActions.clone();
        this.requiredActions = copyRequiredActions(requiredActions);
        this.partyOrder = new CharacterId[characterFactories.size()];
        StringBuilder fingerprint = new StringBuilder("loadout-v1");
        for (int slot = 0; slot < characterFactories.size(); slot++) {
            Character character = characterFactories.get(slot).get();
            partyOrder[slot] = character.getCharacterId();
            String weapon = character.getWeapon() == null
                    ? "none"
                    : character.getWeapon().getClass().getSimpleName()
                            + "-r" + character.getWeapon().getRefinement();
            fingerprint.append(':').append(character.getCharacterId().name())
                    .append("-c").append(character.getConstellation())
                    .append('-').append(weapon).append("-artifact-none");
        }
        this.loadoutFingerprint = fingerprint.toString();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String displayName() {
        return "Rotation optimization scenario: " + name;
    }

    @Override
    public final CharacterId[] partyOrder() {
        return partyOrder.clone();
    }

    @Override
    public final DatasetSplit datasetSplit() {
        return datasetSplit;
    }

    @Override
    public final String loadoutFingerprint() {
        return loadoutFingerprint;
    }

    @Override
    public final double rotationCycleSeconds() {
        return cycleSeconds;
    }

    @Override
    public final int[] baselinePolicyActions() {
        return baselineActions.clone();
    }

    @Override
    public final Map<CharacterId, Set<PolicyAction>> requiredActionCapabilities() {
        return copyRequiredActions(requiredActions);
    }

    @Override
    public final Map<CharacterId, List<StatType>> optimizationTargets() {
        return Map.of();
    }

    @Override
    public final CombatSimulator createSimulator(
            Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setEnemy(new Enemy(90));
        for (Supplier<Character> factory : characterFactories) {
            simulator.addCharacter(factory.get());
        }
        ResonanceManager.applyResonances(simulator);
        simulator.updateMoonsign();
        return simulator;
    }

    @Override
    public final void executeRotation(CombatSimulator simulator) {
        for (int actionId : baselineActions) {
            PolicyAction action = PolicyAction.fromId(actionId);
            if (action.isSwap()) {
                simulator.switchCharacter(partyOrder[action.getTargetSlot()]);
            } else if (action.isWait()) {
                simulator.advanceTime(WAIT_SECONDS);
            } else {
                simulator.performAction(
                        simulator.getActiveCharacter().getCharacterId(),
                        action.getActionRequest());
            }
        }
        double remaining = cycleSeconds - simulator.getCurrentTime();
        if (remaining > 0.0) {
            simulator.advanceTime(remaining);
        }
    }

    protected static int[] actions(PolicyAction... actions) {
        return policyActions(actions);
    }

    protected static Map<CharacterId, Set<PolicyAction>> requires(
            CharacterId characterId,
            PolicyAction... actions) {
        return Map.of(characterId, Set.of(actions));
    }

    private static Map<CharacterId, Set<PolicyAction>> copyRequiredActions(
            Map<CharacterId, Set<PolicyAction>> source) {
        if (source == null) {
            throw new IllegalArgumentException("Required actions must not be null");
        }
        Map<CharacterId, Set<PolicyAction>> copy = new LinkedHashMap<>();
        for (Map.Entry<CharacterId, Set<PolicyAction>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return copy;
    }
}
