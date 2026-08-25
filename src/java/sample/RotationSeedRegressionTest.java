package sample;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import mechanics.rl.QuietExecution;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationSeedEvaluation;
import mechanics.rotation.RotationSeedEvaluation.Result;
import mechanics.rotation.RotationSourceCatalog;
import mechanics.rotation.SourcedRotationSeed;
import mechanics.rotation.SourcedRotationSeed.AdaptationStatus;
import model.type.CharacterId;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Regression checks for uninterrupted multi-cycle source-seed replay. */
public final class RotationSeedRegressionTest {
    private RotationSeedRegressionTest() {
    }

    public static void main(String[] args) {
        QuietExecution.call(() -> {
            PartyDefinition definition = PartyCatalog.require("RaidenParty");
            TotalOptimizationResult build = PartyBuildResolver.require(definition);
            assertDeterministicAlternatingReplay(definition, build);
            assertInvalidSeedsRejected(definition, build);
            assertTrackedCatalogReplay();
            return null;
        });
        System.out.println("RotationSeedRegressionTest passed");
    }

    private static void assertTrackedCatalogReplay() {
        RotationSourceCatalog catalog;
        try {
            catalog = RotationSourceCatalog.loadDefault();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to load tracked rotation catalog", exception);
        }
        for (SourcedRotationSeed seed : catalog.getSeeds()) {
            if (!seed.isUsable()) {
                continue;
            }
            int cycleCount = Math.max(3, seed.getCycleActions().size() * 2);
            PartyDefinition definition = PartyCatalog.require(seed.getPartyName());
            TotalOptimizationResult build = RotationSeedEvaluation.resolveBuild(seed);
            Result result = RotationSeedEvaluation.evaluate(
                    seed,
                    definition,
                    build,
                    cycleCount,
                    seed.getSeedId().hashCode());
            if (result.cycles.size() != cycleCount || !result.cyclicEnergyFeasible
                    || result.steadyCycleDps <= 0.0) {
                throw new AssertionError("Tracked seed failed cyclic replay: " + seed.getSeedId());
            }
            assertNoWorseThanDefinitionBaseline(seed, definition, build, result, cycleCount);
        }
    }

    private static void assertNoWorseThanDefinitionBaseline(
            SourcedRotationSeed sourceSeed,
            PartyDefinition definition,
            TotalOptimizationResult build,
            Result sourceResult,
            int cycleCount) {
        // One definition form cannot provide a phase-matched alternating baseline.
        if (sourceSeed.getCycleActions().size() > 1) {
            return;
        }
        SourcedRotationSeed baseline = new SourcedRotationSeed(
                sourceSeed.getSeedId() + "-definition-baseline",
                definition.name(),
                definition.loadoutFingerprint(),
                sourceSeed.getSourceIds(),
                AdaptationStatus.ADAPTED,
                "Definition-owned comparison under the source seed's frozen build",
                new int[0],
                List.of(definition.baselinePolicyActions()),
                sourceSeed.getErTargets(),
                sourceSeed.getSourceAssumptions(),
                sourceSeed.getSimulatorAssumptions());
        Result baselineResult;
        try {
            baselineResult = RotationSeedEvaluation.evaluate(
                    baseline,
                    definition,
                    build,
                    cycleCount,
                    sourceSeed.getSeedId().hashCode());
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains(baseline.getSeedId())
                    && (message.contains("unavailable action")
                            || message.contains("ending Energy decays"))) {
                return;
            }
            throw new AssertionError("Definition baseline comparison failed unexpectedly for "
                    + sourceSeed.getSeedId(), exception);
        }
        if (sourceResult.steadyCycleDps + 1.0e-9 < baselineResult.steadyCycleDps) {
            throw new AssertionError("Source seed regressed below definition baseline: "
                    + sourceSeed.getSeedId() + " source=" + sourceResult.steadyCycleDps
                    + " baseline=" + baselineResult.steadyCycleDps);
        }
    }

    private static void assertDeterministicAlternatingReplay(
            PartyDefinition definition,
            TotalOptimizationResult build) {
        SourcedRotationSeed seed = seed(
                definition,
                build,
                "alternating-replay",
                AdaptationStatus.ADAPTED,
                new int[] {PolicyAction.SKILL_PRESS.getId()},
                List.of(
                        new int[] {PolicyAction.NORMAL.getId()},
                        new int[] {PolicyAction.CHARGE.getId()}));
        Result first = RotationSeedEvaluation.evaluate(seed, definition, build, 3, 71L);
        Result second = RotationSeedEvaluation.evaluate(seed, definition, build, 3, 71L);
        assertEquals(3, first.cycles.size(), "cycle count");
        assertEquals(build.getBuildFingerprint(), first.buildFingerprint, "build fingerprint");
        assertEquals(PolicyAction.SKILL_PRESS.getId(),
                first.cycles.get(0).executedActions.get(0), "one-time opener");
        assertEquals(PolicyAction.NORMAL.getId(),
                first.cycles.get(0).executedActions.get(1), "first cycle action");
        assertEquals(PolicyAction.CHARGE.getId(),
                first.cycles.get(1).executedActions.get(0), "alternate cycle action");
        assertEquals(PolicyAction.NORMAL.getId(),
                first.cycles.get(2).executedActions.get(0), "repeated cycle action");
        for (int index = 0; index < first.cycles.size(); index++) {
            RotationSeedEvaluation.CycleResult expected = first.cycles.get(index);
            RotationSeedEvaluation.CycleResult actual = second.cycles.get(index);
            assertBits(expected.damage, actual.damage, "cycle damage");
            assertBits(expected.elapsedSeconds, actual.elapsedSeconds, "cycle boundary");
            assertEquals(expected.endingEnergy, actual.endingEnergy, "ending Energy");
            assertEquals(expected.executedActions, actual.executedActions, "action trace");
            assertEquals(expected.stateHash, actual.stateHash, "state hash");
        }
        assertNear(definition.rotationCycleSeconds(),
                first.cycles.get(0).elapsedSeconds, 0.100001, "first boundary");
        assertNear(definition.rotationCycleSeconds() * 3.0,
                first.cycles.get(2).elapsedSeconds, 0.100001, "final boundary");
        if (!first.cyclicEnergyFeasible || first.steadyCycleDps < 0.0) {
            throw new AssertionError("Valid alternating replay was not energy feasible");
        }
    }

    private static void assertInvalidSeedsRejected(
            PartyDefinition definition,
            TotalOptimizationResult build) {
        expectFailure(() -> RotationSeedEvaluation.evaluate(
                seed(definition, build, "rapid-swap", AdaptationStatus.ADAPTED,
                        new int[] {PolicyAction.SWAP_SLOT_1.getId()},
                        List.of(new int[] {PolicyAction.SWAP_SLOT_2.getId()})),
                definition, build, 2, 72L), "rapid-swap", "unavailable action");
        expectFailure(() -> RotationSeedEvaluation.evaluate(
                seed(definition, build, "burst-reuse", AdaptationStatus.ADAPTED,
                        new int[0],
                        List.of(new int[] {
                                PolicyAction.BURST.getId(),
                                PolicyAction.BURST.getId()})),
                definition, build, 2, 73L), "burst-reuse", "unavailable action");

        Map<String, Double> insufficientEr = erTargets(build);
        CharacterId constrained = definition.partyOrder()[0];
        insufficientEr.put(constrained.name(), 1.0);
        SourcedRotationSeed staleEr = seed(
                definition,
                "stale-er",
                AdaptationStatus.ADAPTED,
                new int[0],
                List.of(new int[] {PolicyAction.NORMAL.getId()}),
                insufficientEr);
        expectFailure(() -> RotationSeedEvaluation.evaluate(
                staleEr, definition, build, 2, 74L), "stale-er", "declared ER");

        int[] excessiveActions = new int[100];
        java.util.Arrays.fill(excessiveActions, PolicyAction.NORMAL.getId());
        expectFailure(() -> RotationSeedEvaluation.evaluate(
                seed(definition, build, "horizon-overrun", AdaptationStatus.ADAPTED,
                        new int[0], List.of(excessiveActions)),
                definition, build, 2, 75L), "horizon-overrun", "horizon");

        SourcedRotationSeed rejected = seed(
                definition,
                build,
                "rejected-seed",
                AdaptationStatus.REJECTED,
                new int[0],
                List.of(new int[] {PolicyAction.NORMAL.getId()}));
        expectFailure(() -> RotationSeedEvaluation.evaluate(
                rejected, definition, build, 2, 76L), "rejected-seed", "rejected");
        expectFailure(() -> RotationSeedEvaluation.resolveBuild(rejected),
                "rejected-seed", "rejected");
    }

    private static SourcedRotationSeed seed(
            PartyDefinition definition,
            TotalOptimizationResult build,
            String id,
            AdaptationStatus status,
            int[] opener,
            List<int[]> cycles) {
        return seed(definition, id, status, opener, cycles, erTargets(build));
    }

    private static SourcedRotationSeed seed(
            PartyDefinition definition,
            String id,
            AdaptationStatus status,
            int[] opener,
            List<int[]> cycles,
            Map<String, Double> erTargets) {
        return new SourcedRotationSeed(
                id,
                definition.name(),
                definition.loadoutFingerprint(),
                List.of("regression-fixture"),
                status,
                status == AdaptationStatus.ACCEPTED ? "" : "Regression-only action adaptation",
                opener,
                cycles,
                erTargets,
                List.of("single target"),
                List.of("full starting Energy is explicit in the scenario fingerprint"));
    }

    private static Map<String, Double> erTargets(TotalOptimizationResult build) {
        Map<String, Double> targets = new LinkedHashMap<>();
        for (Map.Entry<CharacterId, Double> entry : build.erTargets.entrySet()) {
            targets.put(entry.getKey().name(), entry.getValue());
        }
        return targets;
    }

    private static void expectFailure(
            Runnable action,
            String seedId,
            String expectedMessage) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + seedId);
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().contains(seedId)
                    || !exception.getMessage().contains(expectedMessage)) {
                throw new AssertionError("Unexpected failure for " + seedId + ": "
                        + exception.getMessage());
            }
        }
    }

    private static void assertBits(double expected, double actual, String message) {
        if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }

    private static void assertNear(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("Expected " + message + " near " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }
}
