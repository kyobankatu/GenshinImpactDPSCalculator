package sample;

import java.util.Arrays;

import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationStep;
import simulation.party.PartyCatalog;

/** Regression checks for the search-facing rotation environment contract. */
public class RotationEnvironmentRegressionTest {
    public static void main(String[] args) {
        assertSnapshotReplay("FlinsParty2");
        assertSnapshotReplay("RaidenParty");
        assertDelayedSetupUsesTerminalValue();
        assertInvalidConfigurationRejected();
        assertForeignSnapshotRejected();
        assertPriorResetSnapshotRejected();
        assertTerminalAndClosedAccessRejected();
        System.out.println("RotationEnvironmentRegressionTest passed");
    }

    private static void assertSnapshotReplay(String partyName) {
        RotationScenario scenario = scenario(partyName, 20.0, 1);
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            RotationStep reset = environment.reset();
            int firstAction = firstLegalAction(reset.legalActionMask);
            environment.step(firstAction);
            RotationEnvironment.Snapshot branch = environment.snapshot();
            if (environment.restoreSimulatorCallCost(branch) != 1) {
                throw new AssertionError(
                        partyName + " replay restore cost should equal history depth");
            }
            int branchAction = firstLegalAction(environment.current().legalActionMask);
            RotationStep firstResult = environment.step(branchAction);

            RotationStep restored = environment.restore(branch);
            assertEquals(branch.getStateHash(), restored.stateHash, partyName + " restored hash");
            RotationStep replayResult = environment.step(branchAction);
            if (firstResult.stateHash != replayResult.stateHash) {
                throw new AssertionError(partyName + " replay hash changed: first="
                        + firstResult.stateHash + " replay=" + replayResult.stateHash
                        + " action=" + branchAction
                        + " damage=" + firstResult.totalDamage + "/" + replayResult.totalDamage
                        + " elapsed=" + firstResult.objective.elapsedSeconds + "/"
                        + replayResult.objective.elapsedSeconds
                        + " masks=" + Arrays.toString(firstResult.legalActionMask) + "/"
                        + Arrays.toString(replayResult.legalActionMask));
            }
            assertClose(firstResult.totalDamage, replayResult.totalDamage, partyName + " replay damage");
            assertClose(firstResult.objective.objectiveScore,
                    replayResult.objective.objectiveScore, partyName + " replay objective");
            if (!Arrays.equals(firstResult.legalActionMask, replayResult.legalActionMask)) {
                throw new AssertionError(partyName + " replay action mask changed");
            }
        }
    }

    private static void assertDelayedSetupUsesTerminalValue() {
        RotationObjective objective = new RotationObjective(0.0, 0.0, 1000.0, 0.0);
        RotationObjective.Score immediateAttack = objective.evaluate(100.0, 1.0, 0.0, 0);
        RotationObjective.Score setupThenBurst = objective.evaluate(1000.0, 5.0, 0.0, 0);
        if (setupThenBurst.objectiveScore <= immediateAttack.objectiveScore) {
            throw new AssertionError("Terminal objective discarded a delayed setup path");
        }
    }

    private static void assertInvalidConfigurationRejected() {
        expectFailure(() -> new RotationObjective(Double.NaN, 0.0, 0.0, 0.0),
                "non-finite objective");
        expectFailure(() -> new RotationObjective(0.0, -1.0, 0.0, 0.0),
                "negative objective penalty");
        expectFailure(() -> scenario("RaidenParty", Double.POSITIVE_INFINITY, 1),
                "non-finite horizon");
        expectFailure(() -> scenario("RaidenParty", 20.0, 0), "zero cycle count");
    }

    private static void assertForeignSnapshotRejected() {
        RotationScenario scenario = scenario("RaidenParty", 20.0, 1);
        try (BattleRotationEnvironment first = new BattleRotationEnvironment(scenario);
                BattleRotationEnvironment second = new BattleRotationEnvironment(scenario)) {
            first.reset();
            second.reset();
            RotationEnvironment.Snapshot snapshot = first.snapshot();
            expectFailure(() -> second.restore(snapshot), "foreign environment snapshot");
            expectFailure(
                    () -> second.restoreSimulatorCallCost(snapshot),
                    "foreign environment restore cost");
        }
    }

    private static void assertPriorResetSnapshotRejected() {
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(
                scenario("RaidenParty", 20.0, 1))) {
            environment.reset();
            RotationEnvironment.Snapshot snapshot = environment.snapshot();
            environment.reset();
            expectFailure(() -> environment.restore(snapshot), "prior reset snapshot");
            expectFailure(
                    () -> environment.restoreSimulatorCallCost(snapshot),
                    "prior reset restore cost");
        }
        expectFailure(
                () -> new BattleRotationEnvironment(
                        scenario("RaidenParty", 20.0, 1),
                        BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT),
                "unaudited direct restore mode");
    }

    private static void assertTerminalAndClosedAccessRejected() {
        BattleRotationEnvironment environment = new BattleRotationEnvironment(
                scenario("RaidenParty", 0.01, 1));
        RotationStep reset = environment.reset();
        RotationStep terminal = environment.step(firstLegalAction(reset.legalActionMask));
        if (!terminal.done) {
            throw new AssertionError("Expected short-horizon environment to terminate");
        }
        expectFailure(() -> environment.step(0), "step after terminal state");
        environment.close();
        expectFailure(environment::current, "closed environment access");
        expectFailure(environment::reset, "closed environment reset");
    }

    private static RotationScenario scenario(String partyName, double cycleSeconds, int cycleCount) {
        return RotationScenario.forParty(
                PartyCatalog.require(partyName),
                new EpisodeConfig(),
                cycleSeconds,
                cycleCount,
                1234L,
                RotationObjective.cyclicDamage());
    }

    private static int firstLegalAction(double[] mask) {
        for (int action = 0; action < mask.length; action++) {
            if (mask[action] > 0.5) {
                return action;
            }
        }
        throw new AssertionError("No legal action available");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }

    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }
}
