package simulation.party;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import mechanics.element.ResonanceManager;
import mechanics.optimization.ArtifactOptimizer;
import mechanics.rotation.PolicyAction;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
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
    private final Map<CharacterId, Double> minimumEnergyRecharge;

    /** Creates one exact C0/base-loadout scenario. */
    CuratedPartyDefinition(
            String name,
            DatasetSplit datasetSplit,
            double cycleSeconds,
            List<Supplier<Character>> characterFactories,
            int[] baselineActions,
            Map<CharacterId, Set<PolicyAction>> requiredActions) {
        this(
                name,
                datasetSplit,
                cycleSeconds,
                characterFactories,
                baselineActions,
                requiredActions,
                Map.of());
    }

    /** Creates one exact scenario with optional per-character ER floors. */
    CuratedPartyDefinition(
            String name,
            DatasetSplit datasetSplit,
            double cycleSeconds,
            List<Supplier<Character>> characterFactories,
            int[] baselineActions,
            Map<CharacterId, Set<PolicyAction>> requiredActions,
            Map<CharacterId, Double> minimumEnergyRecharge) {
        if (characterFactories == null || characterFactories.size() != 4) {
            throw new IllegalArgumentException("Curated party requires four character factories");
        }
        if (minimumEnergyRecharge == null) {
            throw new IllegalArgumentException("Minimum ER targets must not be null");
        }
        this.name = name;
        this.datasetSplit = datasetSplit;
        this.cycleSeconds = cycleSeconds;
        this.characterFactories = List.copyOf(characterFactories);
        this.baselineActions = baselineActions.clone();
        this.requiredActions = copyRequiredActions(requiredActions);
        this.minimumEnergyRecharge = copyMinimumEnergyRecharge(minimumEnergyRecharge);
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
                    .append('-').append(weapon).append("-artifact-kqms-generic-v1");
        }
        for (CharacterId characterId : this.minimumEnergyRecharge.keySet()) {
            boolean present = false;
            for (CharacterId partyCharacter : partyOrder) {
                present |= partyCharacter == characterId;
            }
            if (!present) {
                throw new IllegalArgumentException("Minimum ER character is not in party: " + characterId);
            }
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
    public final Map<CharacterId, Double> minimumEnergyRechargeTargets() {
        return minimumEnergyRecharge;
    }

    @Override
    public final Map<CharacterId, List<StatType>> optimizationTargets() {
        return Map.of(
                partyOrder[0],
                List.of(
                        StatType.CRIT_RATE,
                        StatType.CRIT_DMG,
                        StatType.ATK_PERCENT,
                        StatType.ELEMENTAL_MASTERY));
    }

    @Override
    public final CombatSimulator createSimulator(
            Map<CharacterId, Double> erTargets,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        if (erTargets == null || partyManualRolls == null) {
            throw new IllegalArgumentException("Curated party build maps must not be null: " + name);
        }
        CombatSimulator simulator = new CombatSimulator();
        simulator.setEnemy(new Enemy(90));
        for (Supplier<Character> factory : characterFactories) {
            Character character = factory.get();
            equipKqmsArtifacts(
                    character,
                    Math.max(
                            erTargets.getOrDefault(character.getCharacterId(), 1.0),
                            minimumEnergyRecharge.getOrDefault(character.getCharacterId(), 1.0)),
                    partyManualRolls);
            simulator.addCharacter(character);
        }
        ResonanceManager.applyResonances(simulator);
        simulator.updateMoonsign();
        return simulator;
    }

    @Override
    public final void executeRotation(CombatSimulator simulator) {
        simulator.getEnergyDistributor().scheduleKQMSEnemyParticles(cycleSeconds);
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

    private static Map<CharacterId, Double> copyMinimumEnergyRecharge(
            Map<CharacterId, Double> source) {
        Map<CharacterId, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<CharacterId, Double> entry : source.entrySet()) {
            Double value = entry.getValue();
            if (entry.getKey() == null || value == null || !Double.isFinite(value) || value < 1.0) {
                throw new IllegalArgumentException("Invalid minimum ER target");
            }
            copy.put(entry.getKey(), value);
        }
        return Map.copyOf(copy);
    }

    private static void equipKqmsArtifacts(
            Character character,
            double minimumEnergyRecharge,
            Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
        ArtifactOptimizer.OptimizationConfig config = new ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = minimumEnergyRecharge > 1.60
                ? StatType.ENERGY_RECHARGE
                : StatType.ATK_PERCENT;
        config.mainStatGoblet = character.getElement().getBonusStatType();
        config.mainStatCirclet = StatType.CRIT_RATE;
        config.subStatPriority = List.of(
                StatType.ENERGY_RECHARGE,
                StatType.CRIT_RATE,
                StatType.CRIT_DMG,
                StatType.ATK_PERCENT,
                StatType.ELEMENTAL_MASTERY,
                StatType.HP_PERCENT,
                StatType.DEF_PERCENT);
        config.minER = minimumEnergyRecharge;
        config.manualRolls = partyManualRolls.get(character.getCharacterId());
        StatsContainer weaponStats = character.getWeapon() == null
                ? new StatsContainer()
                : character.getWeapon().getStats();
        ArtifactOptimizer.OptimizationResult result;
        try {
            result = ArtifactOptimizer.generate(
                    config,
                    character.getBaseStats(),
                    weaponStats,
                    new StatsContainer());
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(
                    "Curated KQMS build is infeasible for " + character.getCharacterId(),
                    exception);
        }
        character.setArtifacts(new ArtifactSet("KQMS Generic Baseline", result.stats));
        character.setArtifactRolls(result.rolls);
    }
}
