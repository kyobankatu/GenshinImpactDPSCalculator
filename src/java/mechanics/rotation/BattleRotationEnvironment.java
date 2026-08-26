package mechanics.rotation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import mechanics.rl.ActionResult;
import mechanics.rl.BattleEnvironment;
import model.type.CharacterId;
import simulation.CombatSimulator;
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
    private final RestoreMode restoreMode;
    private final List<Integer> actionHistory = new ArrayList<>();
    private final Map<CharacterId, Double> cyclicReferenceEnergy =
            new EnumMap<>(CharacterId.class);
    private long resetGeneration;
    private int invalidActionCount;
    private RotationStep currentStep;
    private boolean closed;

    /** Creates an adapter for one immutable scenario. */
    public BattleRotationEnvironment(RotationScenario scenario) {
        this(scenario, scenario != null && scenario.getSnapshotSafety().admitted
                ? RestoreMode.AUDITED_DIRECT : RestoreMode.REPLAY);
    }

    /** Creates an adapter with an explicit restore implementation for benchmarks. */
    public BattleRotationEnvironment(
            RotationScenario scenario,
            RestoreMode restoreMode) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        if (restoreMode == null) {
            throw new IllegalArgumentException("restoreMode must not be null");
        }
        if (restoreMode != RestoreMode.REPLAY
                && !scenario.getSnapshotSafety().admitted) {
            throw new IllegalArgumentException(
                    "direct restore requires an admitted scenario");
        }
        this.scenario = scenario;
        this.battleEnvironment = new BattleEnvironment(scenario.getEpisodeFactory());
        this.ownerId = NEXT_OWNER_ID.getAndIncrement();
        this.restoreMode = restoreMode;
    }

    @Override
    public RotationStep reset() {
        ensureOpen();
        BattleEnvironment.ResetResult result = resetBattleEnvironment();
        resetGeneration++;
        invalidActionCount = 0;
        cyclicReferenceEnergy.clear();
        actionHistory.clear();
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        RotationObjective.Score score = scenario.getObjective().evaluate(
                battleEnvironment.getSimulator(), invalidActionCount);
        long stateHash = hashState(
                simulatorSnapshot,
                battleEnvironment.saveBranchState(),
                result.actionMask,
                cyclicReferenceEnergy);
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
        double previousTime = currentStep.objective.elapsedSeconds;
        ActionResult result = battleEnvironment.step(actionId);
        if (!result.validAction) {
            invalidActionCount++;
        }
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        BattleEnvironment.BranchStateSnapshot branchState = battleEnvironment.saveBranchState();
        captureCyclicReference(previousTime);
        RotationObjective.Score score = currentObjective();
        long stateHash = hashState(
                simulatorSnapshot,
                branchState,
                result.actionMask,
                cyclicReferenceEnergy);
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
        long stateHash = hashState(
                simulatorSnapshot,
                branchState,
                currentStep.legalActionMask,
                cyclicReferenceEnergy);
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
                stateHash,
                simulatorSnapshot,
                branchState,
                cyclicReferenceEnergy);
    }

    @Override
    public RotationStep restore(Snapshot snapshot) {
        ensureReady();
        BattleSnapshot battleSnapshot = requireOwnedSnapshot(snapshot);
        if (restoreMode == RestoreMode.REPLAY) {
            return restoreByReplay(battleSnapshot);
        }
        return restoreDirect(battleSnapshot);
    }

    @Override
    public int restoreSimulatorCallCost(Snapshot snapshot) {
        ensureReady();
        BattleSnapshot battleSnapshot = requireOwnedSnapshot(snapshot);
        if (restoreMode == RestoreMode.REPLAY) {
            return battleSnapshot.actionHistory.length;
        }
        return 0;
    }

    /** Restores one branch by replay as a direct-restore correctness oracle. */
    public RotationStep restoreByReplayForAudit(Snapshot snapshot) {
        ensureReady();
        return restoreByReplay(requireOwnedSnapshot(snapshot));
    }

    private RotationStep restoreByReplay(BattleSnapshot battleSnapshot) {
        rebuildFromHistory(battleSnapshot.actionHistory);
        if (invalidActionCount != battleSnapshot.invalidActionCount) {
            throw new IllegalStateException("replayed invalid-action count mismatch");
        }
        long restoredHash = hashState(
                battleEnvironment.saveSnapshot(),
                battleEnvironment.saveBranchState(),
                battleSnapshot.step.legalActionMask,
                cyclicReferenceEnergy);
        if (restoredHash != battleSnapshot.stateHash) {
            throw new IllegalStateException("restored state hash mismatch");
        }
        currentStep = battleSnapshot.step.copy();
        return currentStep.copy();
    }

    private RotationStep restoreDirect(BattleSnapshot battleSnapshot) {
        battleEnvironment.restoreSnapshot(
                battleSnapshot.simulatorSnapshot,
                battleSnapshot.branchState);
        if (scenario.schedulesKqmsEnemyParticles()) {
            battleEnvironment.getSimulator().getEnergyDistributor()
                    .restoreKQMSEnemyParticles(
                            scenario.getCycleDurationSeconds(),
                            battleSnapshot.simulatorSnapshot.currentTime);
        }
        invalidActionCount = battleSnapshot.invalidActionCount;
        cyclicReferenceEnergy.clear();
        cyclicReferenceEnergy.putAll(battleSnapshot.cyclicReferenceEnergy);
        actionHistory.clear();
        for (int actionId : battleSnapshot.actionHistory) {
            actionHistory.add(actionId);
        }
        long restoredHash = hashState(
                battleEnvironment.saveSnapshot(),
                battleEnvironment.saveBranchState(),
                battleSnapshot.step.legalActionMask,
                cyclicReferenceEnergy);
        if (restoredHash != battleSnapshot.stateHash) {
            throw new IllegalStateException("direct restored state hash mismatch");
        }
        currentStep = battleSnapshot.step.copy();
        return currentStep.copy();
    }

    @Override
    public RotationScenario scenario() {
        return scenario;
    }

    /** Returns the active simulator after reset for audited boundary metrics. */
    public CombatSimulator getSimulator() {
        ensureReady();
        return battleEnvironment.getSimulator();
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

    private BattleSnapshot requireOwnedSnapshot(Snapshot snapshot) {
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
        return battleSnapshot;
    }

    private void rebuildFromHistory(int[] history) {
        BattleEnvironment.ResetResult result = resetBattleEnvironment();
        invalidActionCount = 0;
        cyclicReferenceEnergy.clear();
        actionHistory.clear();
        SimulatorSnapshot simulatorSnapshot = battleEnvironment.saveSnapshot();
        RotationObjective.Score score = scenario.getObjective().evaluate(
                battleEnvironment.getSimulator(), invalidActionCount);
        long stateHash = hashState(
                simulatorSnapshot,
                battleEnvironment.saveBranchState(),
                result.actionMask,
                cyclicReferenceEnergy);
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

    private BattleEnvironment.ResetResult resetBattleEnvironment() {
        BattleEnvironment.ResetResult result = battleEnvironment.reset(
                false, scenario.getPreferredPartyId());
        if (scenario.schedulesKqmsEnemyParticles()) {
            battleEnvironment.getSimulator().getEnergyDistributor()
                    .scheduleKQMSEnemyParticles(scenario.getCycleDurationSeconds());
        }
        return result;
    }

    private void captureCyclicReference(double previousTime) {
        if (scenario.getCycleCount() <= 1 || !cyclicReferenceEnergy.isEmpty()) {
            return;
        }
        double boundary = scenario.getCycleDurationSeconds();
        CombatSimulator simulator = battleEnvironment.getSimulator();
        if (previousTime < boundary && simulator.getCurrentTime() >= boundary) {
            for (model.entity.Character character : simulator.getPartyMembers()) {
                cyclicReferenceEnergy.put(
                        character.getCharacterId(), character.getCurrentEnergy());
            }
        }
    }

    private RotationObjective.Score currentObjective() {
        if (cyclicReferenceEnergy.isEmpty()) {
            return scenario.getObjective().evaluate(
                    battleEnvironment.getSimulator(), invalidActionCount);
        }
        return scenario.getObjective().evaluateAgainstEnergyReference(
                battleEnvironment.getSimulator(),
                invalidActionCount,
                cyclicReferenceEnergy);
    }

    private static long hashState(
            SimulatorSnapshot simulatorSnapshot,
            BattleEnvironment.BranchStateSnapshot branchState,
            double[] actionMask,
            Map<CharacterId, Double> cyclicReferenceEnergy) {
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
        for (CharacterId characterId : CharacterId.values()) {
            Double reference = cyclicReferenceEnergy.get(characterId);
            if (reference != null) {
                hash = mix(hash, characterId.ordinal());
                hash = mix(hash, Double.doubleToLongBits(reference));
            }
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        long mixed = hash ^ value;
        return mixed * 0x100000001b3L;
    }

    /** Branch restoration implementation selected by production or benchmark callers. */
    public enum RestoreMode {
        /** Typed direct restore allowed only for audited exact loadouts. */
        AUDITED_DIRECT,
        /** Reset and replay retained as the correctness and performance baseline. */
        REPLAY
    }

    private static final class BattleSnapshot implements Snapshot {
        private final long ownerId;
        private final long resetGeneration;
        private final String scenarioFingerprint;
        private final int[] actionHistory;
        private final int invalidActionCount;
        private final RotationStep step;
        private final long stateHash;
        private final SimulatorSnapshot simulatorSnapshot;
        private final BattleEnvironment.BranchStateSnapshot branchState;
        private final Map<CharacterId, Double> cyclicReferenceEnergy;

        private BattleSnapshot(
                long ownerId,
                long resetGeneration,
                String scenarioFingerprint,
                List<Integer> actionHistory,
                int invalidActionCount,
                RotationStep step,
                long stateHash,
                SimulatorSnapshot simulatorSnapshot,
                BattleEnvironment.BranchStateSnapshot branchState,
                Map<CharacterId, Double> cyclicReferenceEnergy) {
            this.ownerId = ownerId;
            this.resetGeneration = resetGeneration;
            this.scenarioFingerprint = scenarioFingerprint;
            this.actionHistory = actionHistory.stream().mapToInt(Integer::intValue).toArray();
            this.invalidActionCount = invalidActionCount;
            this.step = step;
            this.stateHash = stateHash;
            this.simulatorSnapshot = simulatorSnapshot;
            this.branchState = branchState;
            this.cyclicReferenceEnergy = Map.copyOf(cyclicReferenceEnergy);
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
