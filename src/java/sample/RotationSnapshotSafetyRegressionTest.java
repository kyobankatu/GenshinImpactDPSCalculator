package sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSnapshotSafety;
import mechanics.rotation.RotationStep;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Audits exact-loadout admission for snapshot-backed rotation search. */
public class RotationSnapshotSafetyRegressionTest {
    private static final List<String> ADMITTED_PARTIES = List.of(
            "HuTaoXianyunVaporize",
            "XiaoFurina",
            "XiaoLanYan",
            "NaviaDoubleHydro",
            "AlhaithamYelanQuickbloom",
            "AyatoXilonenMonoHydro");
    private static final List<String> REJECTED_PARTIES = List.of(
            "RaidenParty");

    public static void main(String[] args) {
        for (String partyName : ADMITTED_PARTIES) {
            PartyDefinition definition = PartyCatalog.require(partyName);
            assertAdmitted(definition);
            assertDirectRestoreEquivalent(definition);
        }
        for (String partyName : REJECTED_PARTIES) {
            assertRejected(PartyCatalog.require(partyName));
        }
        assertSearchAdmission("HuTaoXianyunVaporize", true);
        assertSearchAdmission("RaidenParty", false);
        System.out.println("RotationSnapshotSafetyRegressionTest passed");
    }

    private static void assertAdmitted(PartyDefinition definition) {
        RotationSnapshotSafety.Assessment assessment =
                RotationSnapshotSafety.assess(definition);
        if (!assessment.admitted
                || !assessment.loadoutFingerprint.equals(
                        definition.loadoutFingerprint())) {
            throw new AssertionError(
                    "Audited party was not admitted: " + definition.name());
        }
    }

    private static void assertRejected(PartyDefinition definition) {
        if (RotationSnapshotSafety.assess(definition).admitted) {
            throw new AssertionError(
                    "Unaudited party was admitted: " + definition.name());
        }
    }

    private static void assertSearchAdmission(
            String partyName,
            boolean admitted) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        RotationScenario scenario = RotationScenario.forParty(
                definition,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                1,
                7788L,
                RotationObjective.cyclicDamage());
        Runnable search = admitted
                ? () -> new EvolutionaryRotationSearcher().search(
                        () -> new BattleRotationEnvironment(scenario),
                        RotationSearchConfig.defaults(7788L, 64))
                : () -> new MctsRotationSearcher().search(
                        () -> new BattleRotationEnvironment(scenario),
                        RotationSearchConfig.defaults(7788L, 64));
        if (admitted) {
            search.run();
            return;
        }
        try {
            search.run();
            throw new AssertionError(
                    "Expected snapshot-search rejection for " + partyName);
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("snapshot admission rejected")) {
                throw new AssertionError(
                        "Unexpected search rejection for " + partyName,
                        expected);
            }
        }
    }

    private static void assertDirectRestoreEquivalent(PartyDefinition definition) {
        RotationScenario scenario = RotationScenario.forParty(
                definition,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                1,
                3400L + definition.name().hashCode(),
                RotationObjective.cyclicDamage());
        int[] actions = definition.baselinePolicyActions();
        int firstDepth = Math.min(4, Math.max(1, actions.length - 1));
        int secondDepth = Math.min(
                Math.max(firstDepth + 1, actions.length / 2),
                actions.length - 1);
        assertBranchEquivalent(scenario, actions, firstDepth, definition.name());
        if (secondDepth != firstDepth) {
            assertBranchEquivalent(
                    scenario, actions, secondDepth, definition.name());
        }
    }

    private static void assertBranchEquivalent(
            RotationScenario scenario,
            int[] actions,
            int branchDepth,
            String partyName) {
        try (BattleRotationEnvironment environment =
                new BattleRotationEnvironment(
                        scenario,
                        BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT)) {
            RotationStep step = environment.reset();
            for (int index = 0; index < branchDepth; index++) {
                requireLegal(step, actions[index], partyName, index);
                step = environment.step(actions[index]);
            }
            RotationEnvironment.Snapshot snapshot = environment.snapshot();
            if (environment.restoreSimulatorCallCost(snapshot) != 0) {
                throw new AssertionError(
                        "Audited direct restore consumed simulator calls for "
                                + partyName);
            }
            List<StepSignature> uninterrupted = executeSuffix(
                    environment, step, actions, branchDepth, partyName);
            RotationStep directBranch = environment.restore(snapshot);
            List<StepSignature> direct = executeSuffix(
                    environment, directBranch, actions, branchDepth, partyName);
            RotationStep replayedBranch =
                    environment.restoreByReplayForAudit(snapshot);
            List<StepSignature> replayed = executeSuffix(
                    environment, replayedBranch, actions, branchDepth, partyName);
            assertSameSuffix(
                    uninterrupted, direct, partyName, branchDepth, "direct");
            assertSameSuffix(
                    uninterrupted, replayed, partyName, branchDepth, "replay");
        }
    }

    private static List<StepSignature> executeSuffix(
            BattleRotationEnvironment environment,
            RotationStep initial,
            int[] actions,
            int start,
            String partyName) {
        List<StepSignature> signatures = new ArrayList<>();
        RotationStep step = initial;
        for (int index = start; index < actions.length && !step.done; index++) {
            requireLegal(step, actions[index], partyName, index);
            step = environment.step(actions[index]);
            signatures.add(new StepSignature(step));
        }
        int waitCount = 0;
        int waitAction = PolicyAction.WAIT_SHORT.getId();
        while (!step.done && waitCount < 1000) {
            requireLegal(
                    step, waitAction, partyName, actions.length + waitCount);
            step = environment.step(waitAction);
            signatures.add(new StepSignature(step));
            waitCount++;
        }
        if (!step.done) {
            throw new AssertionError(
                    "Audit did not reach terminal state for " + partyName);
        }
        return signatures;
    }

    private static void requireLegal(
            RotationStep step,
            int actionId,
            String partyName,
            int actionIndex) {
        if (actionId < 0
                || actionId >= step.legalActionMask.length
                || step.legalActionMask[actionId] < 0.5) {
            throw new AssertionError(
                    "Baseline action unavailable for " + partyName + " at "
                            + actionIndex + ": " + actionId);
        }
    }

    private static void assertSameSuffix(
            List<StepSignature> expected,
            List<StepSignature> actual,
            String partyName,
            int branchDepth,
            String restoreMode) {
        int count = Math.min(expected.size(), actual.size());
        for (int index = 0; index < count; index++) {
            if (!expected.get(index).equals(actual.get(index))) {
                throw new AssertionError(
                        restoreMode + " snapshot suffix changed for " + partyName
                                + " at depth " + branchDepth + ", suffix step "
                                + index + ": expected=" + expected.get(index)
                                + ", actual=" + actual.get(index));
            }
        }
        if (expected.size() != actual.size()) {
            throw new AssertionError(
                    restoreMode + " snapshot suffix length changed for "
                            + partyName + " at depth " + branchDepth
                            + ": expected=" + expected.size()
                            + ", actual=" + actual.size());
        }
    }

    private static final class StepSignature {
        private final long stateHash;
        private final boolean done;
        private final int actionId;
        private final double totalDamage;
        private final double elapsedSeconds;
        private final double energyDeficit;
        private final double objectiveScore;
        private final double[] legalActionMask;

        private StepSignature(RotationStep step) {
            stateHash = step.stateHash;
            done = step.done;
            actionId = step.actionId;
            totalDamage = step.objective.totalDamage;
            elapsedSeconds = step.objective.elapsedSeconds;
            energyDeficit = step.objective.energyDeficit;
            objectiveScore = step.objective.objectiveScore;
            legalActionMask = step.legalActionMask.clone();
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof StepSignature)) {
                return false;
            }
            StepSignature other = (StepSignature) value;
            return stateHash == other.stateHash
                    && done == other.done
                    && actionId == other.actionId
                    && Double.doubleToLongBits(totalDamage)
                            == Double.doubleToLongBits(other.totalDamage)
                    && Double.doubleToLongBits(elapsedSeconds)
                            == Double.doubleToLongBits(other.elapsedSeconds)
                    && Double.doubleToLongBits(energyDeficit)
                            == Double.doubleToLongBits(other.energyDeficit)
                    && Double.doubleToLongBits(objectiveScore)
                            == Double.doubleToLongBits(other.objectiveScore)
                    && Arrays.equals(legalActionMask, other.legalActionMask);
        }

        @Override
        public int hashCode() {
            int hash = Long.hashCode(stateHash);
            hash = 31 * hash + Boolean.hashCode(done);
            hash = 31 * hash + actionId;
            hash = 31 * hash + Double.hashCode(totalDamage);
            hash = 31 * hash + Double.hashCode(elapsedSeconds);
            hash = 31 * hash + Double.hashCode(energyDeficit);
            hash = 31 * hash + Double.hashCode(objectiveScore);
            return 31 * hash + Arrays.hashCode(legalActionMask);
        }

        @Override
        public String toString() {
            return "StepSignature{stateHash=" + stateHash
                    + ", done=" + done
                    + ", actionId=" + actionId
                    + ", totalDamage=" + totalDamage
                    + ", elapsedSeconds=" + elapsedSeconds
                    + ", energyDeficit=" + energyDeficit
                    + ", objectiveScore=" + objectiveScore
                    + '}';
        }
    }
}
