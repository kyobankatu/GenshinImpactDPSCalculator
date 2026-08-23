package mechanics.rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import mechanics.rl.ActionResult;
import mechanics.rl.BattleEnvironment;
import model.type.CharacterId;
import simulation.SimulatorSnapshot;

/**
 * Adapts the existing Java RL battle environment to the search-facing rotation
 * contract while preserving complete branch state.
 */
public final class BattleRotationEnvironment implements RotationEnvironment {
    private static final AtomicLong NEXT_OWNER_ID = new AtomicLong(1L);

    private final RotationScenario scenario;
    private final BattleEnvironment battleEnvironment;
    private final long ownerId;
    private final List<Integer> actionHistory = new ArrayList<>();
    private long resetGeneration;
    private int invalidActionCount;
    private RotationStep currentStep;
    private boolean closed;

    /** Creates an adapter for one immutable scenario. */
    public BattleRotationEnvironment(RotationScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        this.scenario = scenario;
        this.battleEnvironment = new BattleEnvironment(scenario.getEpisodeFactory());
        this.ownerId = NEXT_OWNER_ID.getAndIncrement();
    }

    @Override
    public RotationStep reset() {
        ensureOpen();
        BattleEnvironment.ResetResult result = battleEnvironment.reset(
                false, scenario.getPreferredPartyId());
        resetGeneration++;
        invalidActionCount = 0;
        actionHistory.clear();
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        RotationObjective.Score score = scenario.getObjective().evaluate(
                battleEnvironment.getSimulator(), invalidActionCount);
        long stateHash = hashState(
                simulatorSnapshot,
                battleEnvironment.saveBranchState(),
                result.actionMask);
        currentStep = new RotationStep(
                result.observation,
                result.privilegedObservation,
                result.actionMask,
                0.0,
                false,
                true,
                0.0,
                0.0,
                0.0,
                -1,
                0,
                result.partyId,
                stateHash,
                score);
        return currentStep.copy();
    }

    @Override
    public RotationStep step(int actionId) {
        ensureReady();
        if (currentStep.done) {
            throw new IllegalStateException("cannot step a terminal rotation environment");
        }
        return executeStep(actionId, true);
    }

    private RotationStep executeStep(int actionId, boolean recordAction) {
        ActionResult result = battleEnvironment.step(actionId);
        if (!result.validAction) {
            invalidActionCount++;
        }
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        BattleEnvironment.BranchStateSnapshot branchState = battleEnvironment.saveBranchState();
        RotationObjective.Score score = scenario.getObjective().evaluate(
                battleEnvironment.getSimulator(), invalidActionCount);
        long stateHash = hashState(simulatorSnapshot, branchState, result.actionMask);
        currentStep = new RotationStep(
                result.observation,
                result.privilegedObservation,
                result.actionMask,
                result.reward,
                result.done,
                result.validAction,
                result.damageDelta,
                result.totalDamage,
                result.timeDelta,
                result.executedActionId,
                result.stepCount,
                battleEnvironment.getCurrentPartyId(),
                stateHash,
                score);
        if (recordAction) {
            actionHistory.add(actionId);
        }
        return currentStep.copy();
    }

    @Override
    public RotationStep current() {
        ensureReady();
        return currentStep.copy();
    }

    @Override
    public Snapshot snapshot() {
        ensureReady();
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        BattleEnvironment.BranchStateSnapshot branchState = battleEnvironment.saveBranchState();
        long stateHash = hashState(simulatorSnapshot, branchState, currentStep.legalActionMask);
        if (stateHash != currentStep.stateHash) {
            throw new IllegalStateException("current state changed outside the rotation environment");
        }
        return new BattleSnapshot(
                ownerId,
                resetGeneration,
                scenario.getFingerprint(),
                actionHistory,
                invalidActionCount,
                currentStep.copy(),
                stateHash);
    }

    @Override
    public RotationStep restore(Snapshot snapshot) {
        ensureReady();
        if (!(snapshot instanceof BattleSnapshot)) {
            throw new IllegalArgumentException("snapshot was not created by BattleRotationEnvironment");
        }
        BattleSnapshot battleSnapshot = (BattleSnapshot) snapshot;
        if (battleSnapshot.ownerId != ownerId) {
            throw new IllegalArgumentException("snapshot belongs to a different environment");
        }
        if (battleSnapshot.resetGeneration != resetGeneration) {
            throw new IllegalArgumentException("snapshot belongs to a different reset generation");
        }
        if (!scenario.getFingerprint().equals(battleSnapshot.scenarioFingerprint)) {
            throw new IllegalArgumentException("snapshot scenario fingerprint mismatch");
        }
        rebuildFromHistory(battleSnapshot.actionHistory);
        if (invalidActionCount != battleSnapshot.invalidActionCount) {
            throw new IllegalStateException("replayed invalid-action count mismatch");
        }
        long restoredHash = hashState(
                battleEnvironment.saveSnapshot(),
                battleEnvironment.saveBranchState(),
                battleSnapshot.step.legalActionMask);
        if (restoredHash != battleSnapshot.stateHash) {
            throw new IllegalStateException("restored state hash mismatch");
        }
        currentStep = battleSnapshot.step.copy();
        return currentStep.copy();
    }

    @Override
    public RotationScenario scenario() {
        return scenario;
    }

    @Override
    public void close() {
        closed = true;
        currentStep = null;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("rotation environment is closed");
        }
    }

    private void ensureReady() {
        ensureOpen();
        if (currentStep == null) {
            throw new IllegalStateException("rotation environment must be reset first");
        }
    }

    private void rebuildFromHistory(int[] history) {
        BattleEnvironment.ResetResult result = battleEnvironment.reset(
                false, scenario.getPreferredPartyId());
        invalidActionCount = 0;
        actionHistory.clear();
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        RotationObjective.Score score = scenario.getObjective().evaluate(
                battleEnvironment.getSimulator(), invalidActionCount);
        long stateHash = hashState(
                simulatorSnapshot,
                battleEnvironment.saveBranchState(),
                result.actionMask);
        currentStep = new RotationStep(
                result.observation,
                result.privilegedObservation,
                result.actionMask,
                0.0,
                false,
                true,
                0.0,
                0.0,
                0.0,
                -1,
                0,
                result.partyId,
                stateHash,
                score);
        for (int actionId : history) {
            if (currentStep.done) {
                throw new IllegalStateException("snapshot history continues after terminal state");
            }
            executeStep(actionId, true);
        }
    }

    private static long hashState(
            SimulatorSnapshot simulatorSnapshot,
            BattleEnvironment.BranchStateSnapshot branchState,
            double[] actionMask) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, Double.doubleToLongBits(simulatorSnapshot.currentTime));
        hash = mix(hash, Double.doubleToLongBits(simulatorSnapshot.rotationTime));
        hash = mix(hash, Double.doubleToLongBits(simulatorSnapshot.totalDamage));
        hash = mix(hash, simulatorSnapshot.activeCharacterId == null
                ? -1L : simulatorSnapshot.activeCharacterId.ordinal());
        hash = mix(hash, branchState.stepCount);
        hash = mix(hash, Double.doubleToLongBits(branchState.lastSwapTime));
        for (CharacterId characterId : CharacterId.values()) {
            SimulatorSnapshot.CharacterSnapshot characterSnapshot =
                    simulatorSnapshot.characters.get(characterId);
            if (characterSnapshot == null) {
                continue;
            }
            hash = mix(hash, characterId.ordinal());
            hash = mix(hash, Double.doubleToLongBits(characterSnapshot.currentEnergy));
            hash = mix(hash, Double.doubleToLongBits(characterSnapshot.skillCooldownEndTime));
            hash = mix(hash, Double.doubleToLongBits(characterSnapshot.burstCooldownEndTime));
        }
        for (double maskValue : actionMask) {
            hash = mix(hash, Double.doubleToLongBits(maskValue));
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ value;
        return mixed * 0x100000001b3L;
    }

    private static final class BattleSnapshot implements Snapshot {
        private final long ownerId;
        private final long resetGeneration;
        private final String scenarioFingerprint;
        private final int[] actionHistory;
        private final int invalidActionCount;
        private final RotationStep step;
        private final long stateHash;

        private BattleSnapshot(
                long ownerId,
                long resetGeneration,
                String scenarioFingerprint,
                List<Integer> actionHistory,
                int invalidActionCount,
                RotationStep step,
                long stateHash) {
            this.ownerId = ownerId;
            this.resetGeneration = resetGeneration;
            this.scenarioFingerprint = scenarioFingerprint;
            this.actionHistory = actionHistory.stream().mapToInt(Integer::intValue).toArray();
            this.invalidActionCount = invalidActionCount;
            this.step = step;
            this.stateHash = stateHash;
        }

        @Override
        public String getScenarioFingerprint() {
            return scenarioFingerprint;
        }

        @Override
        public long getStateHash() {
            return stateHash;
        }
    }
}
