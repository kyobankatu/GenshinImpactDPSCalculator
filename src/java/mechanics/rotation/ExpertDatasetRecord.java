package mechanics.rotation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import mechanics.rl.ObservationEncoder;
import mechanics.rl.PrivilegedStateEncoder;
import simulation.party.PartyCatalog;

/** Versioned, hash-protected expert trajectory with per-decision labels. */
public final class ExpertDatasetRecord {
    public static final int SCHEMA_VERSION = 1;
    public static final String SIMULATOR_REVISION = "rotation-simulator-v1";

    private static final Gson GSON = new Gson();

    private final int schemaVersion;
    private final String simulatorRevision;
    private final String recordId;
    private String recordHash;
    private final String scenarioFingerprint;
    private final String partyName;
    private final String split;
    private final long seed;
    private final double cycleDurationSeconds;
    private final int cycleCount;
    private final int actionLayoutRevision;
    private final int observationSchemaRevision;
    private final int privilegedSchemaRevision;
    private final int searchBudget;
    private final int trajectoryRank;
    private final List<Decision> decisions;
    private final Objective terminalObjective;

    /** Creates and seals one trajectory record. */
    public ExpertDatasetRecord(
            String recordId,
            String scenarioFingerprint,
            String partyName,
            String split,
            long seed,
            double cycleDurationSeconds,
            int cycleCount,
            int searchBudget,
            int trajectoryRank,
            List<Decision> decisions,
            Objective terminalObjective) {
        this.schemaVersion = SCHEMA_VERSION;
        this.simulatorRevision = SIMULATOR_REVISION;
        this.recordId = recordId;
        this.recordHash = "";
        this.scenarioFingerprint = scenarioFingerprint;
        this.partyName = partyName;
        this.split = split;
        this.seed = seed;
        this.cycleDurationSeconds = cycleDurationSeconds;
        this.cycleCount = cycleCount;
        this.actionLayoutRevision = PolicyAction.LAYOUT_REVISION;
        this.observationSchemaRevision = ObservationEncoder.SCHEMA_REVISION;
        this.privilegedSchemaRevision = PrivilegedStateEncoder.SCHEMA_REVISION;
        this.searchBudget = searchBudget;
        this.trajectoryRank = trajectoryRank;
        this.decisions = decisions == null ? null : List.copyOf(decisions);
        this.terminalObjective = terminalObjective;
        validate(false);
        this.recordHash = calculateHash();
    }

    /** Replays actions and captures exact observations, masks, and terminal score. */
    public static ExpertDatasetRecord capture(
            String recordId,
            RotationScenario scenario,
            String partyName,
            String split,
            int searchBudget,
            int trajectoryRank,
            int[] actions) {
        if (scenario == null || actions == null) {
            throw new IllegalArgumentException("scenario and actions are required");
        }
        List<Decision> decisions = new ArrayList<>();
        RotationStep step;
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            step = environment.reset();
            for (int index = 0; index < actions.length; index++) {
                int actionId = actions[index];
                if (step.done) {
                    throw new IllegalArgumentException("actions continue after terminal state");
                }
                double[] policyTarget = new double[PolicyAction.SIZE];
                policyTarget[actionId] = 1.0;
                double[] qEstimates = new double[PolicyAction.SIZE];
                decisions.add(new Decision(
                        step.observation,
                        step.legalActionMask,
                        actionId,
                        policyTarget,
                        qEstimates,
                        step.stateHash,
                        index == 0));
                step = environment.step(actionId);
                if (!step.validAction) {
                    throw new IllegalArgumentException("capture encountered illegal action " + actionId);
                }
            }
        }
        return new ExpertDatasetRecord(
                recordId,
                scenario.getFingerprint(),
                partyName,
                split,
                scenario.getSeed(),
                scenario.getCycleDurationSeconds(),
                scenario.getCycleCount(),
                searchBudget,
                trajectoryRank,
                decisions,
                Objective.from(step.objective));
    }

    /** Validates hashes, revisions, dimensions, masks, and finite values. */
    public void validate() {
        validate(true);
    }

    /** Validates a record hash against its original compact JSONL bytes. */
    void validateSourceLine(String sourceLine) {
        validate(false);
        if (sourceLine == null) {
            throw new IllegalArgumentException("Dataset source line must not be null");
        }
        String hashSource = sourceLine.replaceFirst(
                "\\\"recordHash\\\":\\\"[0-9a-f]{64}\\\",", "");
        if (hashSource.equals(sourceLine)
                || !sha256(hashSource.getBytes(StandardCharsets.UTF_8)).equals(recordHash)) {
            throw new IllegalArgumentException("Dataset record hash mismatch: " + recordId);
        }
    }

    private void validate(boolean requireHash) {
        requireText(recordId, "recordId");
        requireText(scenarioFingerprint, "scenarioFingerprint");
        requireText(partyName, "partyName");
        requireText(split, "split");
        if (!"train".equals(split) && !"validation".equals(split) && !"holdout".equals(split)) {
            throw new IllegalArgumentException("Unknown dataset split: " + split);
        }
        if (schemaVersion != SCHEMA_VERSION
                || !SIMULATOR_REVISION.equals(simulatorRevision)
                || actionLayoutRevision != PolicyAction.LAYOUT_REVISION
                || observationSchemaRevision != ObservationEncoder.SCHEMA_REVISION
                || privilegedSchemaRevision != PrivilegedStateEncoder.SCHEMA_REVISION) {
            throw new IllegalArgumentException("Dataset record revision mismatch: " + recordId);
        }
        if (!Double.isFinite(cycleDurationSeconds) || cycleDurationSeconds <= 0.0
                || cycleCount <= 0 || searchBudget <= 0 || trajectoryRank < 0) {
            throw new IllegalArgumentException("Invalid dataset metadata: " + recordId);
        }
        if (decisions == null || decisions.isEmpty() || terminalObjective == null) {
            throw new IllegalArgumentException("Dataset trajectory must not be empty: " + recordId);
        }
        for (int index = 0; index < decisions.size(); index++) {
            decisions.get(index).validate(index == 0, recordId);
        }
        terminalObjective.validate(recordId);
        if (requireHash && !calculateHash().equals(recordHash)) {
            throw new IllegalArgumentException("Dataset record hash mismatch: " + recordId);
        }
    }

    /** Replays this record against the current simulator and requires exact labels. */
    public void replayAndValidate() {
        validate();
        RotationScenario scenario = RotationScenario.forParty(
                PartyCatalog.require(partyName),
                new mechanics.rl.EpisodeConfig(),
                cycleDurationSeconds,
                cycleCount,
                seed,
                RotationObjective.cyclicDamage());
        if (!scenarioFingerprint.equals(scenario.getFingerprint())) {
            throw new IllegalArgumentException("Scenario fingerprint is stale: " + recordId);
        }
        RotationStep step;
        try (BattleRotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            step = environment.reset();
            for (Decision decision : decisions) {
                decision.requireReplayState(step, recordId);
                step = environment.step(decision.actionId);
                if (!step.validAction) {
                    throw new IllegalStateException("Replay executed an illegal action: " + recordId);
                }
            }
        }
        terminalObjective.requireExact(step.objective, recordId);
    }

    /** Returns canonical SHA-256 over every field except recordHash. */
    public String calculateHash() {
        JsonObject object = GSON.toJsonTree(this).getAsJsonObject();
        object.remove("recordHash");
        return sha256(GSON.toJson(object).getBytes(StandardCharsets.UTF_8));
    }

    public String getRecordId() {
        return recordId;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public String getScenarioFingerprint() {
        return scenarioFingerprint;
    }

    public String getSplit() {
        return split;
    }

    public List<Decision> getDecisions() {
        return List.copyOf(decisions);
    }

    public Objective getTerminalObjective() {
        return terminalObjective;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireFinite(double[] values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " contains a non-finite value");
            }
        }
    }

    /** One recurrent decision label. */
    public static final class Decision {
        private final double[] observation;
        private final double[] legalActionMask;
        private final int actionId;
        private final double[] visitPolicyTarget;
        private final double[] qEstimates;
        private final long stateHash;
        private final boolean recurrentBoundary;

        /** Creates one defensive decision label. */
        public Decision(
                double[] observation,
                double[] legalActionMask,
                int actionId,
                double[] visitPolicyTarget,
                double[] qEstimates,
                long stateHash,
                boolean recurrentBoundary) {
            this.observation = observation == null ? null : observation.clone();
            this.legalActionMask = legalActionMask == null ? null : legalActionMask.clone();
            this.actionId = actionId;
            this.visitPolicyTarget = visitPolicyTarget == null ? null : visitPolicyTarget.clone();
            this.qEstimates = qEstimates == null ? null : qEstimates.clone();
            this.stateHash = stateHash;
            this.recurrentBoundary = recurrentBoundary;
        }

        private void validate(boolean first, String recordId) {
            requireFinite(observation, "observation");
            requireFinite(legalActionMask, "legalActionMask");
            requireFinite(visitPolicyTarget, "visitPolicyTarget");
            requireFinite(qEstimates, "qEstimates");
            if (observation.length != ObservationEncoder.OBSERVATION_SIZE
                    || legalActionMask.length != PolicyAction.SIZE
                    || visitPolicyTarget.length != PolicyAction.SIZE
                    || qEstimates.length != PolicyAction.SIZE) {
                throw new IllegalArgumentException("Dataset decision dimension mismatch: " + recordId);
            }
            PolicyAction.fromId(actionId);
            if (legalActionMask[actionId] <= 0.5) {
                throw new IllegalArgumentException("Dataset action is masked: " + recordId);
            }
            double probability = 0.0;
            for (int index = 0; index < PolicyAction.SIZE; index++) {
                if (legalActionMask[index] <= 0.5 && visitPolicyTarget[index] != 0.0) {
                    throw new IllegalArgumentException("Policy target assigns masked action: " + recordId);
                }
                if (visitPolicyTarget[index] < 0.0) {
                    throw new IllegalArgumentException("Policy target is negative: " + recordId);
                }
                probability += visitPolicyTarget[index];
            }
            if (Math.abs(probability - 1.0) > 1e-9 || recurrentBoundary != first) {
                throw new IllegalArgumentException("Invalid policy target or recurrent boundary: " + recordId);
            }
        }

        private void requireReplayState(RotationStep step, String recordId) {
            if (step.stateHash != stateHash
                    || !Arrays.equals(step.observation, observation)
                    || !Arrays.equals(step.legalActionMask, legalActionMask)) {
                throw new IllegalStateException("Dataset replay state mismatch: " + recordId);
            }
        }

        public double[] getObservation() {
            return observation.clone();
        }

        public double[] getLegalActionMask() {
            return legalActionMask.clone();
        }

        public int getActionId() {
            return actionId;
        }

        public double[] getVisitPolicyTarget() {
            return visitPolicyTarget.clone();
        }

        public double[] getQEstimates() {
            return qEstimates.clone();
        }

        public long getStateHash() {
            return stateHash;
        }

        public boolean isRecurrentBoundary() {
            return recurrentBoundary;
        }
    }

    /** Exact terminal objective decomposition. */
    public static final class Objective {
        private final double totalDamage;
        private final double dps;
        private final double elapsedSeconds;
        private final double energyDeficit;
        private final int invalidActionCount;
        private final boolean cyclicEnergyFeasible;
        private final double objectiveScore;

        private Objective(
                double totalDamage,
                double dps,
                double elapsedSeconds,
                double energyDeficit,
                int invalidActionCount,
                boolean cyclicEnergyFeasible,
                double objectiveScore) {
            this.totalDamage = totalDamage;
            this.dps = dps;
            this.elapsedSeconds = elapsedSeconds;
            this.energyDeficit = energyDeficit;
            this.invalidActionCount = invalidActionCount;
            this.cyclicEnergyFeasible = cyclicEnergyFeasible;
            this.objectiveScore = objectiveScore;
        }

        /** Converts the simulator objective into its persisted decomposition. */
        public static Objective from(RotationObjective.Score score) {
            return new Objective(
                    score.totalDamage,
                    score.dps,
                    score.elapsedSeconds,
                    score.energyDeficit,
                    score.invalidActionCount,
                    score.cyclicEnergyFeasible,
                    score.objectiveScore);
        }

        private void validate(String recordId) {
            double[] values = {
                totalDamage, dps, elapsedSeconds, energyDeficit, objectiveScore
            };
            requireFinite(values, "terminalObjective");
            if (totalDamage < 0.0 || elapsedSeconds < 0.0 || energyDeficit < 0.0
                    || invalidActionCount < 0) {
                throw new IllegalArgumentException("Invalid terminal objective: " + recordId);
            }
        }

        private void requireExact(RotationObjective.Score score, String recordId) {
            if (Double.doubleToLongBits(totalDamage) != Double.doubleToLongBits(score.totalDamage)
                    || Double.doubleToLongBits(dps) != Double.doubleToLongBits(score.dps)
                    || Double.doubleToLongBits(elapsedSeconds)
                            != Double.doubleToLongBits(score.elapsedSeconds)
                    || Double.doubleToLongBits(energyDeficit)
                            != Double.doubleToLongBits(score.energyDeficit)
                    || invalidActionCount != score.invalidActionCount
                    || cyclicEnergyFeasible != score.cyclicEnergyFeasible
                    || Double.doubleToLongBits(objectiveScore)
                            != Double.doubleToLongBits(score.objectiveScore)) {
                throw new IllegalStateException("Dataset terminal objective mismatch: " + recordId);
            }
        }

        public double getObjectiveScore() {
            return objectiveScore;
        }
    }
}
