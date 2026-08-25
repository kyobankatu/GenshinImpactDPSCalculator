package sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.GenericRLSimulatorFactory;
import mechanics.rl.QuietExecution;
import mechanics.rl.RLPartyRegistry;
import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertDatasetReader;
import mechanics.rotation.ExpertDatasetWriter;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import mechanics.rotation.RotationStep;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.party.DatasetSplit;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/**
 * Regression checks for catalog-backed sample and RL party creation.
 */
public class PartyCatalogRegressionTest {
    public static void main(String[] args) {
        QuietExecution.call(() -> {
            assertCampaignMetadata();
            List<ExpertDatasetRecord> records = new ArrayList<>();
            for (PartyDefinition definition : PartyCatalog.rlEnabled()) {
                assertDefinitionAndRlFactoryMatch(definition.name());
                assertFreshRlSimulators(definition.name());
                records.add(assertBaselineMask(definition));
                assertSnapshotAndUnseededSearch(definition);
            }
            assertDatasetShardReplay(records);
            assertAbnormalRegistrationsRejected();
            assertRlDoesNotContain("DisabledFixture");
            return null;
        });
        System.out.println("PartyCatalogRegressionTest passed");
    }

    private static void assertCampaignMetadata() {
        List<PartyDefinition> definitions = PartyCatalog.rlEnabled();
        if (definitions.size() < 10) {
            throw new AssertionError("Expected at least ten exact RL scenarios");
        }
        Set<String> names = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        Map<CharacterId, DatasetSplit> characterSplits = new EnumMap<>(CharacterId.class);
        for (PartyDefinition definition : definitions) {
            if (!names.add(definition.name())
                    || !fingerprints.add(definition.loadoutFingerprint())) {
                throw new AssertionError("Duplicate campaign metadata for " + definition.name());
            }
            if (definition.loadoutFingerprint().contains("artifact-none")) {
                throw new AssertionError("Curated scenario omits its artifact mode: " + definition.name());
            }
            if (definition.baselinePolicyActions().length == 0) {
                throw new AssertionError("Missing baseline policy actions for " + definition.name());
            }
            for (CharacterId characterId : definition.partyOrder()) {
                DatasetSplit previous = characterSplits.putIfAbsent(
                        characterId, definition.datasetSplit());
                if (previous != null && previous != definition.datasetSplit()) {
                    throw new AssertionError(
                            "Character profile crosses dataset splits: " + characterId);
                }
            }
            assertRlRegistryContains(definition.name());
        }
        Set<String> trainingNames = RLPartyRegistry.defaultTrainingSpecs().stream()
                .map(spec -> spec.getPartyName())
                .collect(Collectors.toSet());
        Set<String> expectedTrainingNames = PartyCatalog.rlEnabled(DatasetSplit.TRAIN).stream()
                .map(PartyDefinition::name)
                .collect(Collectors.toSet());
        assertEquals(expectedTrainingNames, trainingNames, "default train split");
        assertEquals(1, PartyCatalog.rlEnabled(DatasetSplit.VALIDATION).size(),
                "validation scenario count");
        assertEquals(2, PartyCatalog.rlEnabled(DatasetSplit.HOLDOUT).size(),
                "holdout scenario count");
    }

    private static ExpertDatasetRecord assertBaselineMask(PartyDefinition definition) {
        RotationScenario scenario = scenario(definition, 1000L);
        List<Integer> accepted = new ArrayList<>();
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep step = environment.reset();
            int actionIndex = 0;
            for (int actionId : definition.baselinePolicyActions()) {
                if (step.done || step.legalActionMask[actionId] < 0.5) {
                    throw new AssertionError(
                            "Illegal baseline action " + actionId + " at index "
                                    + actionIndex + " for " + definition.name()
                                    + " done=" + step.done
                                    + " elapsed=" + step.objective.elapsedSeconds
                                    + " mask=" + Arrays.toString(step.legalActionMask));
                }
                accepted.add(actionId);
                step = environment.step(actionId);
                if (!step.validAction) {
                    throw new AssertionError("Baseline action was rejected for " + definition.name());
                }
                actionIndex++;
            }
        }
        int[] actions = accepted.stream().mapToInt(Integer::intValue).toArray();
        return ExpertDatasetRecord.capture(
                "party-campaign-" + definition.name(),
                scenario,
                definition.name(),
                definition.datasetSplit().getWireName(),
                12,
                0,
                actions);
    }

    private static void assertDatasetShardReplay(List<ExpertDatasetRecord> records) {
        try {
            Path directory = Files.createTempDirectory("party-campaign-dataset-");
            ExpertDatasetWriter.write(directory, records, 1);
            List<ExpertDatasetRecord> loaded = ExpertDatasetReader.read(
                    directory.resolve(ExpertDatasetWriter.MANIFEST_FILE));
            if (loaded.size() != records.size()) {
                throw new AssertionError("Campaign dataset changed record count");
            }
            for (ExpertDatasetRecord record : loaded) {
                record.replayAndValidate();
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Campaign dataset round trip failed", exception);
        }
    }

    private static void assertSnapshotAndUnseededSearch(PartyDefinition definition) {
        RotationScenario scenario = scenario(definition, 2000L);
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep reset = environment.reset();
            int first = firstLegal(reset.legalActionMask);
            RotationStep branch = environment.step(first);
            RotationEnvironment.Snapshot snapshot = environment.snapshot();
            int second = firstLegal(branch.legalActionMask);
            environment.step(second);
            RotationStep restored = environment.restore(snapshot);
            if (restored.stateHash != branch.stateHash) {
                throw new AssertionError("Snapshot restore changed state for " + definition.name());
            }
        }
        RotationSearchConfig config = new RotationSearchConfig(
                12,
                6,
                2,
                4,
                1,
                Math.sqrt(2.0),
                scenario.getSeed(),
                mechanics.rotation.ExpertPolicyPrior.uniform(),
                () -> false,
                List.of());
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                () -> new BattleRotationEnvironment(scenario), config);
        if (result.simulatorCalls != config.simulatorCallBudget
                || result.best.getObjective().invalidActionCount != 0) {
            throw new AssertionError("Unseeded search contract failed for " + definition.name());
        }
    }

    private static RotationScenario scenario(PartyDefinition definition, long seed) {
        return RotationScenario.forParty(
                definition,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                1,
                seed,
                RotationObjective.cyclicDamage());
    }

    private static int firstLegal(double[] mask) {
        for (int actionId = 0; actionId < mask.length; actionId++) {
            if (mask[actionId] > 0.5) {
                return actionId;
            }
        }
        throw new AssertionError("No legal policy action");
    }

    private static void assertAbnormalRegistrationsRejected() {
        PartyDefinition base = PartyCatalog.require("RaidenParty");
        expectFailure(() -> PartyCatalog.register(new DelegatingDefinition(
                base, base.name(), base.loadoutFingerprint(), base.datasetSplit(),
                base.partyOrder(), base.requiredActionCapabilities())), "duplicate name");
        expectFailure(() -> PartyCatalog.register(new DelegatingDefinition(
                base, "DuplicateFingerprintFixture", base.loadoutFingerprint(),
                base.datasetSplit(), base.partyOrder(), base.requiredActionCapabilities())),
                "duplicate fingerprint");
        expectFailure(() -> PartyCatalog.register(new DelegatingDefinition(
                base, "CrossSplitFixture", base.loadoutFingerprint(), DatasetSplit.HOLDOUT,
                base.partyOrder(), base.requiredActionCapabilities())), "cross-split fingerprint");
        CharacterId[] missingParty = base.partyOrder();
        missingParty[0] = CharacterId.KAMISATO_AYAKA;
        expectFailure(() -> PartyCatalog.register(new DelegatingDefinition(
                base, "MissingCapabilityFixture", "missing-capability-fixture",
                DatasetSplit.TRAIN, missingParty, Map.of())), "missing capability");
        expectFailure(() -> PartyCatalog.register(new DelegatingDefinition(
                base, "UnsupportedActionFixture", "unsupported-action-fixture",
                DatasetSplit.TRAIN, base.partyOrder(),
                Map.of(CharacterId.RAIDEN_SHOGUN, Set.of(PolicyAction.SKILL_HOLD)))),
                "unsupported required action");
    }

    private static void assertDefinitionAndRlFactoryMatch(String partyName) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        TotalOptimizationResult build = PartyBuildResolver.require(definition);
        if (!build.getBuildFingerprint().startsWith(TotalOptimizationResult.BUILD_MODE + ":")) {
            throw new AssertionError("Unexpected optimized build mode for " + partyName);
        }
        if (build != PartyBuildResolver.require(definition)) {
            throw new AssertionError("Optimized build was not cached for " + partyName);
        }
        for (Map.Entry<CharacterId, Double> entry
                : definition.minimumEnergyRechargeTargets().entrySet()) {
            if (build.erTargets.getOrDefault(entry.getKey(), 0.0) < entry.getValue()) {
                throw new AssertionError("Optimized build omitted ER floor for "
                        + partyName + ": " + entry.getKey());
            }
        }
        assertBuildIsDeeplyImmutable(build, partyName);
        if (definition.loadoutFingerprint().contains("artifact-kqms-generic-v1")) {
            expectFailure(() -> definition.createSimulator(null, Map.of()),
                    partyName + " null ER build");
            expectFailure(() -> definition.createSimulator(Map.of(), null),
                    partyName + " null roll build");
        }
        CombatSimulator sampleSim = definition.createSimulator(build.erTargets, build.partyRolls);
        CombatSimulator rlSim = GenericRLSimulatorFactory.create(definition, build);

        assertEquals(sampleSim.getEnemy().getLevel(), rlSim.getEnemy().getLevel(), partyName + " enemy level");
        assertEquals(Arrays.toString(definition.partyOrder()), Arrays.toString(RLPartyRegistry.require(partyName).getPartyOrder()),
                partyName + " party order");
        assertEquals(fingerprint(sampleSim), fingerprint(rlSim), partyName + " setup fingerprint");
        assertStructuralStatsEqual(sampleSim, rlSim, partyName);
    }

    private static void assertBuildIsDeeplyImmutable(
            TotalOptimizationResult build,
            String partyName) {
        try {
            build.erTargets.clear();
            throw new AssertionError("ER targets are mutable for " + partyName);
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        Map<StatType, Integer> firstRolls = build.partyRolls.values().iterator().next();
        try {
            firstRolls.clear();
            throw new AssertionError("Artifact rolls are mutable for " + partyName);
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertFreshRlSimulators(String partyName) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        CombatSimulator first = GenericRLSimulatorFactory.create(definition);
        CombatSimulator second = GenericRLSimulatorFactory.create(definition);
        if (first == second) {
            throw new AssertionError("Expected fresh simulator instances for " + partyName);
        }
        if (first.getPartyMembers().iterator().next() == second.getPartyMembers().iterator().next()) {
            throw new AssertionError("Expected fresh character instances for " + partyName);
        }
    }

    private static String fingerprint(CombatSimulator sim) {
        return sim.getPartyMembers().stream()
                .map(PartyCatalogRegressionTest::characterFingerprint)
                .collect(Collectors.joining("|"));
    }

    private static void assertStructuralStatsEqual(
            CombatSimulator expected,
            CombatSimulator actual,
            String partyName) {
        List<Character> expectedParty = new ArrayList<>(expected.getPartyMembers());
        List<Character> actualParty = new ArrayList<>(actual.getPartyMembers());
        for (int slot = 0; slot < expectedParty.size(); slot++) {
            for (StatType statType : StatType.values()) {
                double expectedValue = expectedParty.get(slot).getStructuralStats(0.0).get(statType);
                double actualValue = actualParty.get(slot).getStructuralStats(0.0).get(statType);
                if (Double.doubleToLongBits(expectedValue) != Double.doubleToLongBits(actualValue)) {
                    throw new AssertionError("Structural stat mismatch for " + partyName
                            + " slot=" + slot + " stat=" + statType);
                }
            }
        }
    }

    private static String characterFingerprint(Character character) {
        String weapon = character.getWeapon() != null ? character.getWeapon().getName() : "-";
        String artifacts = character.getArtifacts() != null
                ? Arrays.stream(character.getArtifacts())
                        .map(PartyCatalogRegressionTest::artifactName)
                        .collect(Collectors.joining(","))
                : "-";
        return character.getCharacterId() + ":" + character.getName() + ":" + weapon + ":" + artifacts + ":"
                + character.getArtifactRolls();
    }

    private static String artifactName(ArtifactSet artifact) {
        return artifact != null ? artifact.getName() : "-";
    }

    private static void assertRlRegistryContains(String partyName) {
        RLPartyRegistry.require(partyName);
    }

    private static void assertRlDoesNotContain(String partyName) {
        try {
            RLPartyRegistry.require(partyName);
            throw new AssertionError("Expected party not to be RL registered: " + partyName);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }

    /** Metadata-adjustable wrapper used only for catalog rejection checks. */
    private static final class DelegatingDefinition implements PartyDefinition {
        private final PartyDefinition delegate;
        private final String name;
        private final String fingerprint;
        private final DatasetSplit split;
        private final CharacterId[] partyOrder;
        private final Map<CharacterId, Set<PolicyAction>> requiredActions;

        private DelegatingDefinition(
                PartyDefinition delegate,
                String name,
                String fingerprint,
                DatasetSplit split,
                CharacterId[] partyOrder,
                Map<CharacterId, Set<PolicyAction>> requiredActions) {
            this.delegate = delegate;
            this.name = name;
            this.fingerprint = fingerprint;
            this.split = split;
            this.partyOrder = partyOrder.clone();
            this.requiredActions = requiredActions;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String displayName() {
            return delegate.displayName();
        }

        @Override
        public CharacterId[] partyOrder() {
            return partyOrder.clone();
        }

        @Override
        public DatasetSplit datasetSplit() {
            return split;
        }

        @Override
        public String loadoutFingerprint() {
            return fingerprint;
        }

        @Override
        public double rotationCycleSeconds() {
            return delegate.rotationCycleSeconds();
        }

        @Override
        public int[] baselinePolicyActions() {
            return delegate.baselinePolicyActions();
        }

        @Override
        public Map<CharacterId, Set<PolicyAction>> requiredActionCapabilities() {
            return requiredActions;
        }

        @Override
        public Map<CharacterId, List<StatType>> optimizationTargets() {
            return delegate.optimizationTargets();
        }

        @Override
        public CombatSimulator createSimulator(
                Map<CharacterId, Double> erTargets,
                Map<CharacterId, Map<StatType, Integer>> partyManualRolls) {
            return delegate.createSimulator(erTargets, partyManualRolls);
        }

        @Override
        public void executeRotation(CombatSimulator simulator) {
            delegate.executeRotation(simulator);
        }
    }
}
