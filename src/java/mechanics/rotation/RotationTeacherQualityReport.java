package mechanics.rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Deterministic scenario-local quality gate for rotation teacher searches.
 *
 * <p>
 * Structural comparison errors fail before a report can be created. A complete
 * comparison whose retained teacher loses to a baseline remains reportable, but
 * receives explicit rejection reasons and cannot publish dataset labels.
 */
public final class RotationTeacherQualityReport {
    public static final int SCHEMA_VERSION = 1;
    public static final int MINIMUM_SEARCH_SEEDS = 5;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final int schemaVersion;
    private final List<ScenarioResult> scenarios;

    /** Creates and validates one deterministically ordered report. */
    public RotationTeacherQualityReport(List<ScenarioResult> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("At least one scenario result is required");
        }
        List<ScenarioResult> sorted = new ArrayList<>(scenarios);
        sorted.sort(Comparator.comparing(ScenarioResult::getScenarioFingerprint));
        Set<String> fingerprints = new LinkedHashSet<>();
        for (ScenarioResult scenario : sorted) {
            if (scenario == null) {
                throw new IllegalArgumentException("Scenario result must not be null");
            }
            if (!fingerprints.add(scenario.getScenarioFingerprint())) {
                throw new IllegalArgumentException(
                        "Duplicate scenario result: " + scenario.getScenarioFingerprint());
            }
        }
        this.schemaVersion = SCHEMA_VERSION;
        this.scenarios = Collections.unmodifiableList(sorted);
    }

    /** Reads, reconstructs, and canonicalizes a report before accepting it. */
    public static RotationTeacherQualityReport read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "Teacher quality report does not exist: " + path);
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        int version = root.get("schemaVersion").getAsInt();
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Teacher quality schema revision mismatch: " + version);
        }
        JsonArray scenarioPayloads = root.getAsJsonArray("scenarios");
        if (scenarioPayloads == null) {
            throw new IllegalArgumentException("Teacher quality scenarios are required");
        }
        List<ScenarioResult> scenarios = new ArrayList<>();
        for (JsonElement scenarioElement : scenarioPayloads) {
            JsonObject scenario = scenarioElement.getAsJsonObject();
            JsonArray trialPayloads = scenario.getAsJsonArray("trials");
            if (trialPayloads == null) {
                throw new IllegalArgumentException("Teacher quality trials are required");
            }
            List<Trial> trials = new ArrayList<>();
            for (JsonElement trialElement : trialPayloads) {
                JsonObject trial = trialElement.getAsJsonObject();
                trials.add(new Trial(
                        Arm.fromWireName(trial.get("arm").getAsString()),
                        trial.get("searchSeed").getAsLong(),
                        trial.get("simulatorCalls").getAsInt(),
                        trial.get("bestObjective").getAsDouble(),
                        trial.get("completedTrajectories").getAsInt(),
                        trial.get("completedGenerations").getAsInt(),
                        trial.get("distinctTrajectories").getAsInt(),
                        trial.get("cyclicFeasible").getAsBoolean()));
            }
            scenarios.add(new ScenarioResult(
                    scenario.get("scenarioFingerprint").getAsString(), trials));
        }
        RotationTeacherQualityReport report = new RotationTeacherQualityReport(scenarios);
        JsonElement canonicalInput = JsonParser.parseString(Files.readString(path));
        JsonElement canonicalReport = JsonParser.parseString(report.toJson());
        if (!canonicalReport.equals(canonicalInput)) {
            throw new IllegalArgumentException(
                    "Teacher quality report contains stale or derived-field drift");
        }
        return report;
    }

    /** Returns the stable schema revision. */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** Returns scenario results sorted by fingerprint. */
    public List<ScenarioResult> getScenarios() {
        return scenarios;
    }

    /** Returns whether every reported scenario passed its local quality gate. */
    public boolean allScenariosPublishable() {
        for (ScenarioResult scenario : scenarios) {
            if (!scenario.isPublishable()) {
                return false;
            }
        }
        return true;
    }

    /** Requires a scenario to exist and to have passed its local quality gate. */
    public ScenarioResult requirePublishable(String scenarioFingerprint) {
        ScenarioResult scenario = findScenario(scenarioFingerprint);
        if (scenario == null) {
            throw new IllegalArgumentException(
                    "Teacher quality report does not contain " + scenarioFingerprint);
        }
        if (!scenario.isPublishable()) {
            throw new IllegalStateException(
                    "Teacher quality gate rejected " + scenarioFingerprint
                            + ": " + scenario.getRejectionReasons());
        }
        return scenario;
    }

    /** Returns a matching scenario or {@code null} when it was not compared. */
    public ScenarioResult findScenario(String scenarioFingerprint) {
        for (ScenarioResult scenario : scenarios) {
            if (scenario.getScenarioFingerprint().equals(scenarioFingerprint)) {
                return scenario;
            }
        }
        return null;
    }

    /** Serializes only deterministic evidence; wall-clock measurements are excluded. */
    public String toJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", schemaVersion);
        payload.put("scenarios", scenarios);
        return GSON.toJson(payload);
    }

    /** Comparison arms required for every scenario and search seed. */
    public enum Arm {
        HUMAN_SEED("human-seed"),
        DETERMINISTIC_RANDOM("deterministic-random"),
        UNGUIDED_EVOLUTIONARY("unguided-evolutionary"),
        UNGUIDED_MCTS("unguided-mcts");

        private final String wireName;

        Arm(String wireName) {
            this.wireName = wireName;
        }

        public String getWireName() {
            return wireName;
        }

        /** Resolves one stable report name. */
        public static Arm fromWireName(String wireName) {
            for (Arm arm : values()) {
                if (arm.wireName.equals(wireName)) {
                    return arm;
                }
            }
            throw new IllegalArgumentException(
                    "Unknown teacher comparison arm: " + wireName);
        }
    }

    /** Typed reasons that exclude a complete scenario comparison from publication. */
    public enum RejectionReason {
        TEACHER_BELOW_HUMAN,
        TEACHER_BELOW_RANDOM,
        TEACHER_NOT_CYCLIC
    }

    /** One exact-budget comparison cell. */
    public static final class Trial {
        private final String arm;
        private final long searchSeed;
        private final int simulatorCalls;
        private final double bestObjective;
        private final int completedTrajectories;
        private final int completedGenerations;
        private final int distinctTrajectories;
        private final boolean cyclicFeasible;

        /** Creates one fully accounted comparison cell. */
        public Trial(
                Arm arm,
                long searchSeed,
                int simulatorCalls,
                double bestObjective,
                int completedTrajectories,
                int completedGenerations,
                int distinctTrajectories,
                boolean cyclicFeasible) {
            if (arm == null) {
                throw new IllegalArgumentException("Comparison arm is required");
            }
            if (simulatorCalls <= 0) {
                throw new IllegalArgumentException("simulatorCalls must be positive");
            }
            if (!Double.isFinite(bestObjective)) {
                throw new IllegalArgumentException("bestObjective must be finite");
            }
            if (completedTrajectories <= 0) {
                throw new IllegalArgumentException(
                        "Every comparison cell needs a complete trajectory");
            }
            if (completedGenerations < 0 || distinctTrajectories <= 0) {
                throw new IllegalArgumentException(
                        "Generation and diversity counts must be valid");
            }
            if (arm == Arm.UNGUIDED_EVOLUTIONARY && completedGenerations == 0) {
                throw new IllegalArgumentException(
                        "Evolutionary comparison requires a complete generation");
            }
            this.arm = arm.getWireName();
            this.searchSeed = searchSeed;
            this.simulatorCalls = simulatorCalls;
            this.bestObjective = bestObjective;
            this.completedTrajectories = completedTrajectories;
            this.completedGenerations = completedGenerations;
            this.distinctTrajectories = distinctTrajectories;
            this.cyclicFeasible = cyclicFeasible;
        }

        public Arm getArm() {
            for (Arm candidate : Arm.values()) {
                if (candidate.getWireName().equals(arm)) {
                    return candidate;
                }
            }
            throw new IllegalStateException("Unknown comparison arm: " + arm);
        }

        public long getSearchSeed() {
            return searchSeed;
        }

        public int getSimulatorCalls() {
            return simulatorCalls;
        }

        public double getBestObjective() {
            return bestObjective;
        }

        public int getCompletedTrajectories() {
            return completedTrajectories;
        }

        public int getCompletedGenerations() {
            return completedGenerations;
        }

        public int getDistinctTrajectories() {
            return distinctTrajectories;
        }

        public boolean isCyclicFeasible() {
            return cyclicFeasible;
        }
    }

    /** Deterministic aggregate for one arm inside one scenario. */
    public static final class ArmSummary {
        private final String arm;
        private final double bestObjective;
        private final double medianObjective;
        private final int simulatorCallsPerTrial;
        private final int completedTrajectories;
        private final int completedGenerations;
        private final int distinctTrajectories;
        private final int cyclicFeasibleTrials;
        private final int trialCount;

        private ArmSummary(Arm comparisonArm, List<Trial> trials) {
            List<Double> objectives = new ArrayList<>();
            double best = Double.NEGATIVE_INFINITY;
            int complete = 0;
            int generations = 0;
            int diversity = 0;
            int cyclic = 0;
            for (Trial trial : trials) {
                objectives.add(trial.bestObjective);
                best = Math.max(best, trial.bestObjective);
                complete += trial.completedTrajectories;
                generations += trial.completedGenerations;
                diversity += trial.distinctTrajectories;
                if (trial.cyclicFeasible) {
                    cyclic++;
                }
            }
            objectives.sort(Double::compare);
            this.arm = comparisonArm.getWireName();
            this.bestObjective = best;
            this.medianObjective = median(objectives);
            this.simulatorCallsPerTrial = trials.get(0).simulatorCalls;
            this.completedTrajectories = complete;
            this.completedGenerations = generations;
            this.distinctTrajectories = diversity;
            this.cyclicFeasibleTrials = cyclic;
            this.trialCount = trials.size();
        }

        public Arm getArm() {
            for (Arm candidate : Arm.values()) {
                if (candidate.getWireName().equals(arm)) {
                    return candidate;
                }
            }
            throw new IllegalStateException("Unknown comparison arm: " + arm);
        }

        public double getBestObjective() {
            return bestObjective;
        }

        public double getMedianObjective() {
            return medianObjective;
        }

        public int getSimulatorCallsPerTrial() {
            return simulatorCallsPerTrial;
        }

        public int getCompletedTrajectories() {
            return completedTrajectories;
        }

        public int getCompletedGenerations() {
            return completedGenerations;
        }

        public int getDistinctTrajectories() {
            return distinctTrajectories;
        }

        public int getCyclicFeasibleTrials() {
            return cyclicFeasibleTrials;
        }

        public int getTrialCount() {
            return trialCount;
        }
    }

    /** Complete matched-budget result and publication decision for one scenario. */
    public static final class ScenarioResult {
        private final String scenarioFingerprint;
        private final String retainedTeacher;
        private final List<Trial> trials;
        private final List<ArmSummary> summaries;
        private final double advantageOverHumanMedian;
        private final double advantageOverRandomMedian;
        private final boolean publishable;
        private final List<RejectionReason> rejectionReasons;

        /** Validates complete cells and applies the scenario-local quality gate. */
        public ScenarioResult(String scenarioFingerprint, List<Trial> trials) {
            if (scenarioFingerprint == null || scenarioFingerprint.isBlank()) {
                throw new IllegalArgumentException("scenarioFingerprint must not be blank");
            }
            if (trials == null) {
                throw new IllegalArgumentException("Comparison trials are required");
            }
            EnumMap<Arm, List<Trial>> byArm = validateCells(trials);
            List<Trial> orderedTrials = new ArrayList<>(trials);
            orderedTrials.sort(Comparator
                    .comparing((Trial trial) -> trial.getArm().ordinal())
                    .thenComparingLong(Trial::getSearchSeed));
            List<ArmSummary> orderedSummaries = new ArrayList<>();
            for (Arm arm : Arm.values()) {
                orderedSummaries.add(new ArmSummary(arm, byArm.get(arm)));
            }
            ArmSummary human = orderedSummaries.get(Arm.HUMAN_SEED.ordinal());
            ArmSummary random = orderedSummaries.get(Arm.DETERMINISTIC_RANDOM.ordinal());
            Arm retainedTeacher = selectTeacher(orderedSummaries);
            ArmSummary teacher = orderedSummaries.get(retainedTeacher.ordinal());
            double humanAdvantage = teacher.medianObjective - human.medianObjective;
            double randomAdvantage = teacher.medianObjective - random.medianObjective;
            List<RejectionReason> reasons = new ArrayList<>();
            if (humanAdvantage < 0.0) {
                reasons.add(RejectionReason.TEACHER_BELOW_HUMAN);
            }
            if (randomAdvantage < 0.0) {
                reasons.add(RejectionReason.TEACHER_BELOW_RANDOM);
            }
            if (teacher.cyclicFeasibleTrials != teacher.trialCount) {
                reasons.add(RejectionReason.TEACHER_NOT_CYCLIC);
            }
            this.scenarioFingerprint = scenarioFingerprint;
            this.retainedTeacher = retainedTeacher.getWireName();
            this.trials = Collections.unmodifiableList(orderedTrials);
            this.summaries = Collections.unmodifiableList(orderedSummaries);
            this.advantageOverHumanMedian = humanAdvantage;
            this.advantageOverRandomMedian = randomAdvantage;
            this.publishable = reasons.isEmpty();
            this.rejectionReasons = Collections.unmodifiableList(reasons);
        }

        public String getScenarioFingerprint() {
            return scenarioFingerprint;
        }

        public Arm getRetainedTeacher() {
            for (Arm arm : Arm.values()) {
                if (arm.getWireName().equals(retainedTeacher)) {
                    return arm;
                }
            }
            throw new IllegalStateException("Unknown retained teacher: " + retainedTeacher);
        }

        public List<Trial> getTrials() {
            return trials;
        }

        public List<ArmSummary> getSummaries() {
            return summaries;
        }

        /** Returns the aggregate evidence for the automatically retained teacher. */
        public ArmSummary getRetainedTeacherSummary() {
            return summaries.get(getRetainedTeacher().ordinal());
        }

        public double getAdvantageOverHumanMedian() {
            return advantageOverHumanMedian;
        }

        public double getAdvantageOverRandomMedian() {
            return advantageOverRandomMedian;
        }

        public boolean isPublishable() {
            return publishable;
        }

        public List<RejectionReason> getRejectionReasons() {
            return rejectionReasons;
        }

        private static Arm selectTeacher(List<ArmSummary> summaries) {
            ArmSummary evolutionary = summaries.get(
                    Arm.UNGUIDED_EVOLUTIONARY.ordinal());
            ArmSummary mcts = summaries.get(Arm.UNGUIDED_MCTS.ordinal());
            return mcts.medianObjective > evolutionary.medianObjective
                    ? Arm.UNGUIDED_MCTS : Arm.UNGUIDED_EVOLUTIONARY;
        }

        private static EnumMap<Arm, List<Trial>> validateCells(List<Trial> trials) {
            EnumMap<Arm, List<Trial>> byArm = new EnumMap<>(Arm.class);
            for (Arm arm : Arm.values()) {
                byArm.put(arm, new ArrayList<>());
            }
            Set<String> cells = new LinkedHashSet<>();
            for (Trial trial : trials) {
                if (trial == null) {
                    throw new IllegalArgumentException("Comparison trial must not be null");
                }
                String cell = trial.getArm().name() + ":" + trial.searchSeed;
                if (!cells.add(cell)) {
                    throw new IllegalArgumentException("Duplicate comparison cell: " + cell);
                }
                byArm.get(trial.getArm()).add(trial);
            }
            Set<Long> expectedSeeds = null;
            Map<Long, Integer> expectedBudgets = new LinkedHashMap<>();
            for (Arm arm : Arm.values()) {
                List<Trial> armTrials = byArm.get(arm);
                armTrials.sort(Comparator.comparingLong(Trial::getSearchSeed));
                if (armTrials.size() < MINIMUM_SEARCH_SEEDS) {
                    throw new IllegalArgumentException(
                            "Missing comparison cells for " + arm.getWireName());
                }
                Set<Long> armSeeds = new LinkedHashSet<>();
                for (Trial trial : armTrials) {
                    armSeeds.add(trial.searchSeed);
                    Integer budget = expectedBudgets.putIfAbsent(
                            trial.searchSeed, trial.simulatorCalls);
                    if (budget != null && budget != trial.simulatorCalls) {
                        throw new IllegalArgumentException(
                                "Unequal simulator-call budget for seed " + trial.searchSeed);
                    }
                }
                if (expectedSeeds == null) {
                    expectedSeeds = armSeeds;
                } else if (!expectedSeeds.equals(armSeeds)) {
                    throw new IllegalArgumentException(
                            "Comparison arms do not use identical search seeds");
                }
            }
            return byArm;
        }
    }

    private static double median(List<Double> sortedValues) {
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
    }
}
