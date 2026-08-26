package sample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.ObservationEncoder;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertDatasetReader;
import mechanics.rotation.ExpertDatasetRecord;
import mechanics.rotation.ExpertPolicyPrior;
import mechanics.rotation.ExpertTrajectory;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RecordedExpertPolicyPrior;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import mechanics.rotation.RotationStep;
import simulation.party.DatasetSplit;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/**
 * Benchmarks rotation search methods under an exact shared simulator-call budget.
 *
 * <p>
 * The Java {@link BattleRotationEnvironment} is the scoring authority for every
 * candidate. Random, unguided evolutionary, and recorded-prior-guided search
 * consume the same number of measured action steps for each catalog scenario and
 * seed. The required dataset is replayed completely before any result is
 * published, and split-specific prior provenance is validated against it.
 */
public final class BenchmarkRotationSearch {
    private static final String BENCHMARK_REVISION = "rotation-search-benchmark-v1";
    private static final int REPORT_SCHEMA_VERSION = 1;
    private static final int DEFAULT_CALL_BUDGET = 128;
    private static final int DEFAULT_MAX_ACTIONS = 64;
    private static final int DEFAULT_ARCHIVE_SIZE = 8;
    private static final int POPULATION_SIZE = 12;
    private static final int ELITE_COUNT = 3;
    private static final int RESTORE_REPETITIONS = 5;
    private static final int RESTORE_CALL_BUDGET = 4096;
    private static final long[] DEFAULT_SEEDS = {104729L, 130363L, 155921L};
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private BenchmarkRotationSearch() {
    }

    /**
     * Runs the catalog benchmark and atomically publishes one JSON report.
     *
     * @param args strict flag/value pairs; supported flags are {@code --output},
     *        {@code --split}, {@code --seeds}, {@code --budget},
     *        {@code --max-actions}, {@code --archive-size}, {@code --dataset},
     *        {@code --training-prior}, {@code --evaluation-prior}, and
     *        {@code --model-traces}
     * @throws Exception when an input, replay, simulator result, budget, or atomic
     *         publication contract is invalid
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runRestoreThroughputBenchmark();
            return;
        }
        Options options = Options.parse(args);
        List<PartyDefinition> definitions = selectDefinitions(options.split);
        FingerprintSplits fingerprintSplits = FingerprintSplits.fromCatalog();
        DatasetReplay datasetReplay = replayDataset(
                options.datasetManifest, PartyCatalog.rlEnabled());
        ModelTraceArtifact modelTraces = ModelTraceArtifact.load(
                options.modelTraces, datasetReplay.sourceHash, fingerprintSplits);
        PriorArtifact trainingPrior = PriorArtifact.load(
                options.trainingPrior,
                RecordedExpertPolicyPrior.TRAINING_DATASET_STATES,
                datasetReplay.sourceHash,
                fingerprintSplits);
        PriorArtifact evaluationPrior = PriorArtifact.load(
                options.evaluationPrior,
                RecordedExpertPolicyPrior.EVALUATION_PROBE_STATES,
                datasetReplay.sourceHash,
                fingerprintSplits);
        RepositoryState repository = RepositoryState.capture();
        List<BenchmarkMetric> metrics = new ArrayList<>();

        for (PartyDefinition definition : definitions) {
            for (long seed : options.seeds) {
                RotationScenario scenario = createScenario(definition, seed);
                SearchOutcome random = runRandomSearch(scenario, options);
                SearchOutcome evolutionary = runEvolutionarySearch(
                        scenario, options, ExpertPolicyPrior.uniform(), false);
                PriorArtifact artifact = definition.datasetSplit() == DatasetSplit.TRAIN
                        ? trainingPrior : evaluationPrior;
                RecordedExpertPolicyPrior recordedPrior = artifact.priorFor(scenario);
                SearchOutcome guided = runEvolutionarySearch(
                        scenario, options, recordedPrior, true);
                if (recordedPrior.getHitCount() <= 0L) {
                    throw new IllegalStateException(
                            "Guided search used only prior fallback for "
                                    + scenario.getFingerprint());
                }
                SearchOutcome modelOnly = runModelTrace(scenario, modelTraces);
                requireEqualBudget(random, evolutionary, guided, options.callBudget);
                metrics.add(BenchmarkMetric.from(
                        "deterministic-random", definition, scenario, random,
                        datasetReplay, null));
                metrics.add(BenchmarkMetric.from(
                        "unguided-evolutionary", definition, scenario, evolutionary,
                        datasetReplay, null));
                metrics.add(BenchmarkMetric.from(
                        "policy-guided", definition, scenario, guided,
                        datasetReplay, artifact.revision()));
                metrics.add(BenchmarkMetric.from(
                        "model-only", definition, scenario, modelOnly,
                        datasetReplay, modelTraces.revision()));
            }
        }

        modelTraces.requireUnchanged();
        BenchmarkReport report = new BenchmarkReport(
                repository,
                options,
                datasetReplay,
                fingerprintSplits,
                definitions,
                modelTraces.checkpointProvenance(),
                metrics,
                List.of());
        report.validate();
        writeAtomically(options.output, GSON.toJson(report) + System.lineSeparator());
        System.out.println("report=" + options.output.toAbsolutePath().normalize());
        System.out.println("scenarios=" + definitions.size());
        System.out.println("measurements=" + metrics.size());
    }

    private static void runRestoreThroughputBenchmark() {
        PartyDefinition definition = PartyCatalog.require(
                "HuTaoXianyunVaporize");
        RotationScenario warmScenario = createScenario(
                definition, DEFAULT_SEEDS[0]);
        RotationSearchConfig warmConfig = restoreBenchmarkConfig(
                definition, warmScenario);
        runTimedMcts(
                warmScenario,
                warmConfig,
                BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT);
        runTimedMcts(
                warmScenario,
                warmConfig,
                BattleRotationEnvironment.RestoreMode.REPLAY);
        long[] directNanos = new long[RESTORE_REPETITIONS];
        long[] replayNanos = new long[RESTORE_REPETITIONS];
        long[] macroNanos = new long[RESTORE_REPETITIONS];
        int[] completedTrajectories = new int[RESTORE_REPETITIONS];
        int[] completedMacro = new int[RESTORE_REPETITIONS];
        int[] evaluatedDefault = new int[RESTORE_REPETITIONS];
        int[] evaluatedMacro = new int[RESTORE_REPETITIONS];
        double[] directCompletedPerSecond =
                new double[RESTORE_REPETITIONS];
        double[] replayCompletedPerSecond =
                new double[RESTORE_REPETITIONS];
        double[] defaultEvaluatedPerSecond =
                new double[RESTORE_REPETITIONS];
        double[] macroEvaluatedPerSecond =
                new double[RESTORE_REPETITIONS];
        double[] defaultObjectives = new double[RESTORE_REPETITIONS];
        double[] macroObjectives = new double[RESTORE_REPETITIONS];
        for (int repetition = 0; repetition < RESTORE_REPETITIONS; repetition++) {
            RotationScenario scenario = createScenario(
                    definition, DEFAULT_SEEDS[repetition % DEFAULT_SEEDS.length]);
            RotationSearchConfig config = restoreBenchmarkConfig(
                    definition, scenario);
            TimedSearch direct;
            TimedSearch replay;
            if (repetition % 2 == 0) {
                direct = runTimedMcts(
                        scenario,
                        config,
                        BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT);
                replay = runTimedMcts(
                        scenario,
                        config,
                        BattleRotationEnvironment.RestoreMode.REPLAY);
            } else {
                replay = runTimedMcts(
                        scenario,
                        config,
                        BattleRotationEnvironment.RestoreMode.REPLAY);
                direct = runTimedMcts(
                        scenario,
                        config,
                        BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT);
            }
            TimedSearch macro = runTimedMcts(
                    scenario,
                    config.withMaxWaitRunLength(10),
                    BattleRotationEnvironment.RestoreMode.AUDITED_DIRECT);
            requireMatchedBudgetAndQuality(direct.result, replay.result);
            directNanos[repetition] = direct.elapsedNanos;
            replayNanos[repetition] = replay.elapsedNanos;
            macroNanos[repetition] = macro.elapsedNanos;
            completedTrajectories[repetition] =
                    direct.result.statistics.completedTrajectories;
            completedMacro[repetition] =
                    macro.result.statistics.completedTrajectories;
            evaluatedDefault[repetition] =
                    direct.result.statistics.evaluatedTrajectories;
            evaluatedMacro[repetition] =
                    macro.result.statistics.evaluatedTrajectories;
            directCompletedPerSecond[repetition] = perSecond(
                    completedTrajectories[repetition], direct.elapsedNanos);
            replayCompletedPerSecond[repetition] = perSecond(
                    completedTrajectories[repetition], replay.elapsedNanos);
            defaultEvaluatedPerSecond[repetition] = perSecond(
                    evaluatedDefault[repetition], direct.elapsedNanos);
            macroEvaluatedPerSecond[repetition] = perSecond(
                    evaluatedMacro[repetition], macro.elapsedNanos);
            defaultObjectives[repetition] =
                    direct.result.best.getObjective().objectiveScore;
            macroObjectives[repetition] =
                    macro.result.best.getObjective().objectiveScore;
        }
        long medianDirect = median(directNanos);
        long medianReplay = median(replayNanos);
        if (medianDirect >= medianReplay) {
            throw new IllegalStateException(
                    "Audited direct restore did not improve median throughput: direct="
                            + medianDirect + "ns replay=" + medianReplay + "ns");
        }
        if (median(directCompletedPerSecond)
                <= median(replayCompletedPerSecond)) {
            throw new IllegalStateException(
                    "Direct restore did not improve completed trajectory throughput");
        }
        boolean macroRetained = median(macroEvaluatedPerSecond)
                > median(defaultEvaluatedPerSecond)
                && median(macroObjectives) >= median(defaultObjectives);
        System.out.println("restoreBenchmarkParty=" + definition.name());
        System.out.println("repetitions=" + RESTORE_REPETITIONS);
        System.out.println("simulatorCalls=" + RESTORE_CALL_BUDGET);
        System.out.println("completedTrajectories="
                + Arrays.toString(completedTrajectories));
        System.out.println("completedMacro="
                + Arrays.toString(completedMacro));
        System.out.println("evaluatedDefault="
                + Arrays.toString(evaluatedDefault));
        System.out.println("evaluatedMacro=" + Arrays.toString(evaluatedMacro));
        System.out.println("medianDirectNanos=" + medianDirect);
        System.out.println("medianReplayNanos=" + medianReplay);
        System.out.println("medianMacroNanos=" + median(macroNanos));
        System.out.println("speedup="
                + String.format(Locale.ROOT, "%.3f", (double) medianReplay / medianDirect));
        System.out.println("medianDirectCompletedPerSecond="
                + format(median(directCompletedPerSecond)));
        System.out.println("medianReplayCompletedPerSecond="
                + format(median(replayCompletedPerSecond)));
        System.out.println("medianDefaultEvaluatedPerSecond="
                + format(median(defaultEvaluatedPerSecond)));
        System.out.println("medianMacroEvaluatedPerSecond="
                + format(median(macroEvaluatedPerSecond)));
        System.out.println("medianDefaultObjective="
                + format(median(defaultObjectives)));
        System.out.println("medianMacroObjective="
                + format(median(macroObjectives)));
        System.out.println("macroRetained=" + macroRetained);
    }

    private static RotationSearchConfig restoreBenchmarkConfig(
            PartyDefinition definition,
            RotationScenario scenario) {
        return new RotationSearchConfig(
                RESTORE_CALL_BUDGET,
                Math.max(DEFAULT_MAX_ACTIONS,
                        definition.baselinePolicyActions().length),
                DEFAULT_ARCHIVE_SIZE,
                POPULATION_SIZE,
                ELITE_COUNT,
                Math.sqrt(2.0),
                scenario.getSeed(),
                ExpertPolicyPrior.uniform(),
                () -> false,
                List.of(definition.baselinePolicyActions()));
    }

    private static TimedSearch runTimedMcts(
            RotationScenario scenario,
            RotationSearchConfig config,
            BattleRotationEnvironment.RestoreMode restoreMode) {
        long start = System.nanoTime();
        RotationSearchStrategy.Result result = new MctsRotationSearcher().search(
                () -> new BattleRotationEnvironment(scenario, restoreMode),
                config);
        long elapsedNanos = System.nanoTime() - start;
        if (result.simulatorCalls != RESTORE_CALL_BUDGET) {
            throw new IllegalStateException(
                    "Restore benchmark did not consume the exact call budget");
        }
        return new TimedSearch(result, elapsedNanos);
    }

    private static void requireMatchedBudgetAndQuality(
            RotationSearchStrategy.Result direct,
            RotationSearchStrategy.Result replay) {
        if (direct.simulatorCalls != replay.simulatorCalls
                || direct.simulatorCalls != RESTORE_CALL_BUDGET) {
            throw new IllegalStateException(
                    "Direct and replay restore budgets diverged");
        }
        if (direct.best.getObjective().objectiveScore
                < replay.best.getObjective().objectiveScore) {
            throw new IllegalStateException(
                    "Direct restore reduced best terminal objective quality");
        }
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static double perSecond(int count, long elapsedNanos) {
        return count * 1_000_000_000.0 / elapsedNanos;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class TimedSearch {
        private final RotationSearchStrategy.Result result;
        private final long elapsedNanos;

        private TimedSearch(
                RotationSearchStrategy.Result result,
                long elapsedNanos) {
            this.result = result;
            this.elapsedNanos = elapsedNanos;
        }
    }

    private static RotationScenario createScenario(PartyDefinition definition, long seed) {
        return RotationScenario.forParty(
                definition,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                1,
                seed,
                RotationObjective.cyclicDamage());
    }

    private static List<PartyDefinition> selectDefinitions(String splitName) {
        Collection<PartyDefinition> selected;
        if ("all".equals(splitName)) {
            selected = PartyCatalog.rlEnabled();
        } else {
            selected = PartyCatalog.rlEnabled(parseSplit(splitName));
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("No catalog scenarios selected for split " + splitName);
        }
        List<PartyDefinition> definitions = List.copyOf(selected);
        if ("all".equals(splitName)) {
            Set<DatasetSplit> present = new HashSet<>();
            for (PartyDefinition definition : definitions) {
                present.add(definition.datasetSplit());
            }
            if (present.size() != DatasetSplit.values().length) {
                throw new IllegalStateException("Catalog benchmark does not cover every dataset split");
            }
        }
        return definitions;
    }

    private static DatasetSplit parseSplit(String value) {
        for (DatasetSplit split : DatasetSplit.values()) {
            if (split.getWireName().equals(value)) {
                return split;
            }
        }
        throw new IllegalArgumentException("Unknown dataset split: " + value);
    }

    /**
     * Replays every stored record and verifies that selected fingerprints retain
     * their current catalog split before benchmarking.
     */
    private static DatasetReplay replayDataset(
            Path manifestPath,
            List<PartyDefinition> selectedDefinitions) throws IOException {
        List<ExpertDatasetRecord> records = ExpertDatasetReader.read(manifestPath);
        Set<String> selectedFingerprints = new HashSet<>();
        for (PartyDefinition definition : selectedDefinitions) {
            RotationScenario scenario = createScenario(definition, 0L);
            selectedFingerprints.add(scenario.getFingerprint());
        }
        int selectedRecordCount = 0;
        for (ExpertDatasetRecord record : records) {
            record.replayAndValidate();
            if (selectedFingerprints.contains(record.getScenarioFingerprint())) {
                selectedRecordCount++;
                DatasetSplit expected = splitForFingerprint(
                        record.getScenarioFingerprint(), selectedDefinitions);
                if (!expected.getWireName().equals(record.getSplit())) {
                    throw new IllegalStateException(
                            "Dataset split does not match the current catalog: "
                                    + record.getScenarioFingerprint());
                }
            }
        }
        if (selectedRecordCount == 0) {
            throw new IllegalStateException("Dataset contains no records for selected scenarios");
        }
        byte[] manifestBytes = Files.readAllBytes(manifestPath);
        return new DatasetReplay(
                manifestPath.toAbsolutePath().normalize().toString(),
                ExpertDatasetRecord.SCHEMA_VERSION,
                ExpertDatasetRecord.SIMULATOR_REVISION,
                sha256(manifestBytes),
                records.size(),
                records.size(),
                selectedRecordCount,
                1.0);
    }

    private static DatasetSplit splitForFingerprint(
            String fingerprint,
            List<PartyDefinition> definitions) {
        for (PartyDefinition definition : definitions) {
            if (createScenario(definition, 0L).getFingerprint().equals(fingerprint)) {
                return definition.datasetSplit();
            }
        }
        throw new IllegalStateException("Selected dataset fingerprint is not catalog-backed: " + fingerprint);
    }

    /** Runs a deterministic legal random baseline with exact action-step accounting. */
    private static SearchOutcome runRandomSearch(
            RotationScenario scenario,
            Options options) {
        long start = System.nanoTime();
        Random random = new Random(scenario.getSeed());
        List<Candidate> archive = new ArrayList<>();
        List<Double> incumbentHistory = new ArrayList<>();
        Set<String> sequences = new HashSet<>();
        int calls = 0;

        try (RotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            while (calls < options.callBudget) {
                RotationStep step = environment.reset();
                List<Integer> actions = new ArrayList<>();
                while (!step.done
                        && actions.size() < options.maxActions
                        && calls < options.callBudget) {
                    int actionId = randomLegalAction(step, random);
                    step = environment.step(actionId);
                    calls++;
                    if (!step.validAction) {
                        throw new IllegalStateException(
                                "Random baseline executed an illegal action " + actionId);
                    }
                    actions.add(actionId);
                }
                if (actions.isEmpty()) {
                    throw new IllegalStateException("Random baseline produced an empty trajectory");
                }
                Candidate candidate = new Candidate(toIntArray(actions), step.objective, step.done);
                retainCandidate(archive, sequences, candidate, options.archiveSize);
                appendIncumbent(incumbentHistory, archive.get(0).objective.objectiveScore);
            }
        }
        if (archive.isEmpty() || calls != options.callBudget) {
            throw new IllegalStateException("Random baseline failed exact simulator-call accounting");
        }
        return new SearchOutcome(
                archive, incumbentHistory, calls, System.nanoTime() - start);
    }

    /** Runs the evolutionary teacher with uniform or recorded policy guidance. */
    private static SearchOutcome runEvolutionarySearch(
            RotationScenario scenario,
            Options options,
            ExpertPolicyPrior prior,
            boolean forceInitialPriorRollout) {
        RotationSearchConfig config = new RotationSearchConfig(
                options.callBudget,
                options.maxActions,
                options.archiveSize,
                POPULATION_SIZE,
                ELITE_COUNT,
                Math.sqrt(2.0),
                scenario.getSeed(),
                prior,
                () -> false,
                forceInitialPriorRollout
                        ? List.<int[]>of(new int[0]) : List.of());
        TrackingRotationEnvironment tracking = new TrackingRotationEnvironment(
                new BattleRotationEnvironment(scenario));
        long start = System.nanoTime();
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                () -> tracking,
                config);
        long wallTimeNanos = System.nanoTime() - start;
        if (result.cancelled || result.simulatorCalls != options.callBudget) {
            throw new IllegalStateException("Evolutionary search did not consume its exact budget");
        }
        List<Candidate> archive = new ArrayList<>();
        for (ExpertTrajectory trajectory : result.archive) {
            archive.add(new Candidate(
                    trajectory.getActions(),
                    trajectory.getObjective(),
                    trajectory.isComplete()));
        }
        List<Double> incumbentHistory = tracking.getIncumbentHistory();
        if (incumbentHistory.isEmpty()
                || Double.doubleToLongBits(incumbentHistory.get(incumbentHistory.size() - 1))
                        != Double.doubleToLongBits(result.best.getObjective().objectiveScore)) {
            throw new IllegalStateException("Evolutionary incumbent history is incomplete");
        }
        return new SearchOutcome(
                archive, incumbentHistory, result.simulatorCalls, wallTimeNanos);
    }

    /** Replays one immutable model trace as a single model-only evaluation call. */
    private static SearchOutcome runModelTrace(
            RotationScenario scenario,
            ModelTraceArtifact artifact) throws IOException {
        int[] trace = artifact.traceFor(scenario.getFingerprint());
        List<Integer> executed = new ArrayList<>();
        long start = System.nanoTime();
        RotationStep step;
        try (RotationEnvironment environment = new BattleRotationEnvironment(scenario)) {
            step = environment.reset();
            for (int actionId : trace) {
                if (step.done) {
                    break;
                }
                PolicyAction.fromId(actionId);
                step = environment.step(actionId);
                executed.add(actionId);
                if (!step.validAction) {
                    throw new IllegalStateException(
                            "Model trace executed an illegal action " + actionId
                                    + " for " + scenario.getFingerprint());
                }
            }
        }
        if (executed.isEmpty()) {
            throw new IllegalStateException(
                    "Model trace executed no actions for " + scenario.getFingerprint());
        }
        if (!step.done) {
            throw new IllegalStateException(
                    "Model trace exhausted before terminal state for "
                            + scenario.getFingerprint());
        }
        Candidate candidate = new Candidate(toIntArray(executed), step.objective, true);
        return new SearchOutcome(
                List.of(candidate),
                List.of(step.objective.objectiveScore),
                1,
                System.nanoTime() - start);
    }

    private static int randomLegalAction(RotationStep step, Random random) {
        int legalCount = 0;
        for (double value : step.legalActionMask) {
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("Simulator returned a non-finite legal-action mask");
            }
            if (value > 0.5) {
                legalCount++;
            }
        }
        if (legalCount == 0) {
            throw new IllegalStateException("Non-terminal simulator state has no legal action");
        }
        int selected = random.nextInt(legalCount);
        for (int actionId = 0; actionId < step.legalActionMask.length; actionId++) {
            if (step.legalActionMask[actionId] > 0.5 && selected-- == 0) {
                PolicyAction.fromId(actionId);
                return actionId;
            }
        }
        throw new IllegalStateException("Failed to resolve a sampled legal action");
    }

    private static void retainCandidate(
            List<Candidate> archive,
            Set<String> sequences,
            Candidate candidate,
            int capacity) {
        String key = Arrays.toString(candidate.actions);
        if (!sequences.add(key)) {
            return;
        }
        archive.add(candidate);
        archive.sort(Candidate.ORDER);
        if (archive.size() > capacity) {
            Candidate removed = archive.remove(archive.size() - 1);
            sequences.remove(Arrays.toString(removed.actions));
        }
    }

    private static void appendIncumbent(List<Double> history, double score) {
        requireFinite(score, "archive incumbent score");
        if (!history.isEmpty() && score < history.get(history.size() - 1)) {
            throw new IllegalStateException("Archive incumbent score regressed");
        }
        history.add(score);
    }

    private static void requireEqualBudget(
            SearchOutcome random,
            SearchOutcome evolutionary,
            SearchOutcome guided,
            int expectedBudget) {
        if (random.simulatorCalls != expectedBudget
                || evolutionary.simulatorCalls != expectedBudget
                || guided.simulatorCalls != expectedBudget
                || random.simulatorCalls != evolutionary.simulatorCalls
                || random.simulatorCalls != guided.simulatorCalls) {
            throw new IllegalStateException(
                    "Search methods used unequal simulator-call budgets: random="
                            + random.simulatorCalls + " evolutionary="
                            + evolutionary.simulatorCalls + " guided="
                            + guided.simulatorCalls);
        }
    }

    private static double archiveDiversity(List<Candidate> archive) {
        if (archive.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        int pairs = 0;
        for (int left = 0; left < archive.size(); left++) {
            for (int right = left + 1; right < archive.size(); right++) {
                int[] first = archive.get(left).actions;
                int[] second = archive.get(right).actions;
                int denominator = Math.max(first.length, second.length);
                total += denominator == 0
                        ? 0.0 : (double) editDistance(first, second) / denominator;
                pairs++;
            }
        }
        return total / pairs;
    }

    private static int editDistance(int[] first, int[] second) {
        int[] previous = new int[second.length + 1];
        for (int index = 0; index <= second.length; index++) {
            previous[index] = index;
        }
        for (int left = 1; left <= first.length; left++) {
            int[] current = new int[second.length + 1];
            current[0] = left;
            for (int right = 1; right <= second.length; right++) {
                int substitution = first[left - 1] == second[right - 1] ? 0 : 1;
                current[right] = Math.min(
                        Math.min(current[right - 1] + 1, previous[right] + 1),
                        previous[right - 1] + substitution);
            }
            previous = current;
        }
        return previous[second.length];
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static void writeAtomically(Path output, String json) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output path has no parent directory: " + output);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + absolute.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic report publication is unsupported for " + output, exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(name + " must be finite");
        }
    }

    /** Records actual candidate-boundary incumbent scores without changing search. */
    private static final class TrackingRotationEnvironment implements RotationEnvironment {
        private final RotationEnvironment delegate;
        private final List<Double> incumbentHistory = new ArrayList<>();
        private int candidateActions;
        private double candidateScore;
        private double incumbentScore = Double.NEGATIVE_INFINITY;
        private boolean closed;

        private TrackingRotationEnvironment(RotationEnvironment delegate) {
            this.delegate = delegate;
        }

        @Override
        public RotationStep reset() {
            finishCandidate();
            candidateActions = 0;
            return delegate.reset();
        }

        @Override
        public RotationStep step(int actionId) {
            RotationStep step = delegate.step(actionId);
            candidateActions++;
            candidateScore = step.objective.objectiveScore;
            return step;
        }

        @Override
        public RotationStep current() {
            return delegate.current();
        }

        @Override
        public Snapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public RotationStep restore(Snapshot snapshot) {
            return delegate.restore(snapshot);
        }

        @Override
        public RotationScenario scenario() {
            return delegate.scenario();
        }

        @Override
        public void close() {
            if (!closed) {
                finishCandidate();
                closed = true;
                delegate.close();
            }
        }

        private void finishCandidate() {
            if (candidateActions == 0) {
                return;
            }
            requireFinite(candidateScore, "tracked candidate score");
            incumbentScore = Math.max(incumbentScore, candidateScore);
            appendIncumbent(incumbentHistory, incumbentScore);
            candidateActions = 0;
        }

        private List<Double> getIncumbentHistory() {
            if (!closed) {
                throw new IllegalStateException("Tracked environment is still open");
            }
            return List.copyOf(incumbentHistory);
        }
    }

    private static final class Options {
        private final Path output;
        private final String split;
        private final long[] seeds;
        private final int callBudget;
        private final int maxActions;
        private final int archiveSize;
        private final Path datasetManifest;
        private final Path trainingPrior;
        private final Path evaluationPrior;
        private final Path modelTraces;

        private Options(
                Path output,
                String split,
                long[] seeds,
                int callBudget,
                int maxActions,
                int archiveSize,
                Path datasetManifest,
                Path trainingPrior,
                Path evaluationPrior,
                Path modelTraces) {
            this.output = output;
            this.split = split;
            this.seeds = seeds.clone();
            this.callBudget = callBudget;
            this.maxActions = maxActions;
            this.archiveSize = archiveSize;
            this.datasetManifest = datasetManifest;
            this.trainingPrior = trainingPrior;
            this.evaluationPrior = evaluationPrior;
            this.modelTraces = modelTraces;
        }

        /** Parses strict flag/value pairs and rejects unknown or repeated flags. */
        private static Options parse(String[] args) {
            Path output = Path.of("output/rotation_search_benchmark.json");
            String split = "all";
            long[] seeds = DEFAULT_SEEDS.clone();
            int budget = DEFAULT_CALL_BUDGET;
            int maxActions = DEFAULT_MAX_ACTIONS;
            int archiveSize = DEFAULT_ARCHIVE_SIZE;
            Path dataset = null;
            Path trainingPrior = null;
            Path evaluationPrior = null;
            Path modelTraces = null;
            Set<String> seen = new HashSet<>();

            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Benchmark arguments must be flag/value pairs");
            }
            for (int index = 0; index < args.length; index += 2) {
                String flag = args[index];
                String value = args[index + 1];
                if (!seen.add(flag)) {
                    throw new IllegalArgumentException("Duplicate benchmark flag: " + flag);
                }
                switch (flag) {
                    case "--output":
                        output = requirePath(value, flag);
                        break;
                    case "--split":
                        split = value.toLowerCase(Locale.ROOT);
                        if (!"all".equals(split)) {
                            parseSplit(split);
                        }
                        break;
                    case "--seeds":
                        seeds = parseSeeds(value);
                        break;
                    case "--budget":
                        budget = parsePositiveInt(value, flag);
                        break;
                    case "--max-actions":
                        maxActions = parsePositiveInt(value, flag);
                        break;
                    case "--archive-size":
                        archiveSize = parsePositiveInt(value, flag);
                        break;
                    case "--dataset":
                        dataset = requirePath(value, flag);
                        break;
                    case "--training-prior":
                        trainingPrior = requirePath(value, flag);
                        break;
                    case "--evaluation-prior":
                        evaluationPrior = requirePath(value, flag);
                        break;
                    case "--model-traces":
                        modelTraces = requirePath(value, flag);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown benchmark flag: " + flag);
                }
            }
            if (dataset == null || trainingPrior == null || evaluationPrior == null
                    || modelTraces == null) {
                throw new IllegalArgumentException(
                        "--dataset, --training-prior, --evaluation-prior, and "
                                + "--model-traces are required");
            }
            return new Options(
                    output,
                    split,
                    seeds,
                    budget,
                    maxActions,
                    archiveSize,
                    dataset,
                    trainingPrior,
                    evaluationPrior,
                    modelTraces);
        }

        private static Path requirePath(String value, String flag) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(flag + " path must not be blank");
            }
            return Path.of(value);
        }

        private static int parsePositiveInt(String value, String flag) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed <= 0) {
                    throw new IllegalArgumentException(flag + " must be positive");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(flag + " must be an integer", exception);
            }
        }

        private static long[] parseSeeds(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("--seeds must not be empty");
            }
            String[] tokens = value.split(",", -1);
            long[] parsed = new long[tokens.length];
            Set<Long> unique = new HashSet<>();
            for (int index = 0; index < tokens.length; index++) {
                try {
                    parsed[index] = Long.parseLong(tokens[index]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid benchmark seed: " + tokens[index], exception);
                }
                if (!unique.add(parsed[index])) {
                    throw new IllegalArgumentException("Duplicate benchmark seed: " + parsed[index]);
                }
            }
            return parsed;
        }
    }

    private static final class SearchOutcome {
        private final List<Candidate> archive;
        private final List<Double> incumbentHistory;
        private final int simulatorCalls;
        private final long wallTimeNanos;

        private SearchOutcome(
                List<Candidate> archive,
                List<Double> incumbentHistory,
                int simulatorCalls,
                long wallTimeNanos) {
            if (archive == null || archive.isEmpty()
                    || incumbentHistory == null || incumbentHistory.isEmpty()
                    || simulatorCalls <= 0 || wallTimeNanos < 0L) {
                throw new IllegalArgumentException("Invalid search outcome");
            }
            this.archive = List.copyOf(archive);
            this.incumbentHistory = List.copyOf(incumbentHistory);
            this.simulatorCalls = simulatorCalls;
            this.wallTimeNanos = wallTimeNanos;
            double previous = Double.NEGATIVE_INFINITY;
            for (double score : incumbentHistory) {
                requireFinite(score, "incumbent history score");
                if (score < previous) {
                    throw new IllegalArgumentException("Incumbent history regressed");
                }
                previous = score;
            }
        }

        private Candidate best() {
            return archive.get(0);
        }
    }

    private static final class Candidate {
        private static final Comparator<Candidate> ORDER = (left, right) -> {
            int score = Double.compare(
                    right.objective.objectiveScore,
                    left.objective.objectiveScore);
            if (score != 0) {
                return score;
            }
            int length = Math.min(left.actions.length, right.actions.length);
            for (int index = 0; index < length; index++) {
                int action = Integer.compare(left.actions[index], right.actions[index]);
                if (action != 0) {
                    return action;
                }
            }
            return Integer.compare(left.actions.length, right.actions.length);
        };

        private final int[] actions;
        private final RotationObjective.Score objective;
        private final boolean complete;

        private Candidate(int[] actions, RotationObjective.Score objective, boolean complete) {
            if (actions == null || actions.length == 0 || objective == null) {
                throw new IllegalArgumentException("Candidate actions and objective are required");
            }
            requireFinite(objective.objectiveScore, "candidate objective");
            this.actions = actions.clone();
            this.objective = objective;
            this.complete = complete;
        }
    }

    /** Catalog-owned scenario fingerprints serialized by immutable dataset split. */
    private static final class FingerprintSplits {
        private final List<String> train;
        private final List<String> validation;
        private final List<String> holdout;

        private FingerprintSplits(
                List<String> train,
                List<String> validation,
                List<String> holdout) {
            this.train = List.copyOf(train);
            this.validation = List.copyOf(validation);
            this.holdout = List.copyOf(holdout);
            if (train.isEmpty() || validation.isEmpty() || holdout.isEmpty()) {
                throw new IllegalStateException("Every catalog dataset split must be non-empty");
            }
            Set<String> all = new HashSet<>();
            if (!addUnique(all, train) || !addUnique(all, validation)
                    || !addUnique(all, holdout)) {
                throw new IllegalStateException("Catalog fingerprint appears in multiple splits");
            }
        }

        private static FingerprintSplits fromCatalog() {
            List<String> train = new ArrayList<>();
            List<String> validation = new ArrayList<>();
            List<String> holdout = new ArrayList<>();
            for (PartyDefinition definition : PartyCatalog.rlEnabled()) {
                String fingerprint = createScenario(definition, 0L).getFingerprint();
                switch (definition.datasetSplit()) {
                    case TRAIN:
                        train.add(fingerprint);
                        break;
                    case VALIDATION:
                        validation.add(fingerprint);
                        break;
                    case HOLDOUT:
                        holdout.add(fingerprint);
                        break;
                    default:
                        throw new IllegalStateException(
                                "Unknown catalog split: " + definition.datasetSplit());
                }
            }
            return new FingerprintSplits(train, validation, holdout);
        }

        private Set<String> fingerprints(DatasetSplit split) {
            switch (split) {
                case TRAIN:
                    return Set.copyOf(train);
                case VALIDATION:
                    return Set.copyOf(validation);
                case HOLDOUT:
                    return Set.copyOf(holdout);
                default:
                    throw new IllegalArgumentException("Unknown dataset split: " + split);
            }
        }

        private static boolean addUnique(Set<String> all, List<String> values) {
            for (String value : values) {
                if (value == null || value.isBlank() || !all.add(value)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Immutable model action traces and train-only checkpoint provenance. */
    private static final class ModelTraceArtifact {
        private static final int SCHEMA_VERSION = 1;
        private static final Set<String> PAYLOAD_FIELDS = Set.of(
                "schemaVersion",
                "simulatorRevision",
                "checkpointRevision",
                "datasetSourceHash",
                "trainingFingerprints",
                "normalizationFingerprints",
                "traces");
        private static final Set<String> TRACE_FIELDS = Set.of(
                "partyName", "scenarioFingerprint", "actionTrace");

        private final Path path;
        private final String artifactHash;
        private final String checkpointRevision;
        private final String datasetSourceHash;
        private final List<String> trainingFingerprints;
        private final List<String> normalizationFingerprints;
        private final Map<String, int[]> tracesByFingerprint;

        private ModelTraceArtifact(
                Path path,
                String artifactHash,
                String checkpointRevision,
                String datasetSourceHash,
                List<String> trainingFingerprints,
                List<String> normalizationFingerprints,
                Map<String, int[]> tracesByFingerprint) {
            this.path = path;
            this.artifactHash = artifactHash;
            this.checkpointRevision = checkpointRevision;
            this.datasetSourceHash = datasetSourceHash;
            this.trainingFingerprints = List.copyOf(trainingFingerprints);
            this.normalizationFingerprints = List.copyOf(normalizationFingerprints);
            Map<String, int[]> copied = new HashMap<>();
            for (Map.Entry<String, int[]> entry : tracesByFingerprint.entrySet()) {
                copied.put(entry.getKey(), entry.getValue().clone());
            }
            this.tracesByFingerprint = Map.copyOf(copied);
        }

        /** Loads all catalog traces and rejects unknown fields or provenance leaks. */
        private static ModelTraceArtifact load(
                Path path,
                String expectedDatasetHash,
                FingerprintSplits splits) throws IOException {
            if (path == null || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Model trace artifact does not exist: " + path);
            }
            byte[] bytes = Files.readAllBytes(path);
            JsonObject root;
            ModelTracePayload payload;
            try {
                JsonElement parsed = JsonParser.parseString(
                        new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new IllegalArgumentException("Model trace artifact must be a JSON object");
                }
                root = parsed.getAsJsonObject();
                requireExactFields(root, PAYLOAD_FIELDS, "model trace artifact");
                payload = GSON.fromJson(root, ModelTracePayload.class);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Malformed model trace artifact: " + path, exception);
            }
            if (payload == null
                    || payload.schemaVersion != SCHEMA_VERSION
                    || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(payload.simulatorRevision)
                    || payload.checkpointRevision == null
                    || payload.checkpointRevision.isBlank()
                    || !expectedDatasetHash.equals(payload.datasetSourceHash)
                    || payload.trainingFingerprints == null
                    || payload.normalizationFingerprints == null
                    || payload.traces == null) {
                throw new IllegalArgumentException(
                        "Model trace revision or provenance mismatch: " + path);
            }
            Set<String> catalogTraining = splits.fingerprints(DatasetSplit.TRAIN);
            Set<String> declaredTraining = PriorArtifact.checkedSet(
                    payload.trainingFingerprints, "model training fingerprints");
            Set<String> declaredNormalization = PriorArtifact.checkedSet(
                    payload.normalizationFingerprints, "model normalization fingerprints");
            if (!declaredTraining.equals(catalogTraining)
                    || !declaredNormalization.equals(catalogTraining)
                    || !declaredTraining.equals(declaredNormalization)) {
                throw new IllegalArgumentException(
                        "Model checkpoint provenance is not the exact catalog train set");
            }

            List<PartyDefinition> definitions = PartyCatalog.rlEnabled();
            if (definitions.isEmpty() || payload.traces.length != definitions.size()) {
                throw new IllegalArgumentException(
                        "Model trace artifact must contain every catalog scenario");
            }
            Map<String, PartyDefinition> catalogByFingerprint = new HashMap<>();
            for (PartyDefinition definition : definitions) {
                String fingerprint = createScenario(definition, 0L).getFingerprint();
                if (catalogByFingerprint.put(fingerprint, definition) != null) {
                    throw new IllegalStateException("Duplicate catalog scenario fingerprint");
                }
            }
            Map<String, int[]> traces = new HashMap<>();
            for (int index = 0; index < payload.traces.length; index++) {
                JsonElement traceElement = root.getAsJsonArray("traces").get(index);
                if (!traceElement.isJsonObject()) {
                    throw new IllegalArgumentException("Model trace entry must be an object");
                }
                requireExactFields(
                        traceElement.getAsJsonObject(), TRACE_FIELDS, "model trace entry");
                ModelTraceEntry entry = payload.traces[index];
                PartyDefinition definition = entry == null
                        ? null : catalogByFingerprint.get(entry.scenarioFingerprint);
                if (definition == null
                        || entry.partyName == null
                        || !definition.name().equals(entry.partyName)
                        || entry.actionTrace == null
                        || entry.actionTrace.length == 0
                        || traces.put(entry.scenarioFingerprint, entry.actionTrace.clone()) != null) {
                    throw new IllegalArgumentException(
                            "Model trace entry is missing, duplicated, or not catalog-backed");
                }
                for (int actionId : entry.actionTrace) {
                    PolicyAction.fromId(actionId);
                }
            }
            if (!traces.keySet().equals(catalogByFingerprint.keySet())) {
                throw new IllegalArgumentException(
                        "Model trace artifact does not cover the exact catalog");
            }
            ModelTraceArtifact artifact = new ModelTraceArtifact(
                    path.toAbsolutePath().normalize(),
                    sha256(bytes),
                    payload.checkpointRevision,
                    payload.datasetSourceHash,
                    Arrays.asList(payload.trainingFingerprints),
                    Arrays.asList(payload.normalizationFingerprints),
                    traces);
            artifact.requireUnchanged();
            return artifact;
        }

        private int[] traceFor(String scenarioFingerprint) throws IOException {
            requireUnchanged();
            int[] trace = tracesByFingerprint.get(scenarioFingerprint);
            if (trace == null || trace.length == 0) {
                throw new IllegalStateException(
                        "Model trace is unavailable for " + scenarioFingerprint);
            }
            return trace.clone();
        }

        private void requireUnchanged() throws IOException {
            if (!Files.isRegularFile(path)
                    || !artifactHash.equals(sha256(Files.readAllBytes(path)))) {
                throw new IllegalStateException(
                        "Model trace artifact changed after validation: " + path);
            }
        }

        private CheckpointProvenance checkpointProvenance() {
            return new CheckpointProvenance(
                    checkpointRevision,
                    datasetSourceHash,
                    trainingFingerprints,
                    normalizationFingerprints);
        }

        private String revision() {
            return "schema=" + SCHEMA_VERSION + ":checkpoint=" + checkpointRevision
                    + ":sha256=" + artifactHash;
        }

        private static void requireExactFields(
                JsonObject object,
                Set<String> expected,
                String name) {
            if (!object.keySet().equals(expected)) {
                throw new IllegalArgumentException(
                        name + " fields mismatch: expected=" + expected
                                + " actual=" + object.keySet());
            }
        }
    }

    private static final class ModelTracePayload {
        private int schemaVersion;
        private String simulatorRevision;
        private String checkpointRevision;
        private String datasetSourceHash;
        private String[] trainingFingerprints;
        private String[] normalizationFingerprints;
        private ModelTraceEntry[] traces;
    }

    private static final class ModelTraceEntry {
        private String partyName;
        private String scenarioFingerprint;
        private int[] actionTrace;
    }

    /** JSON wire shape consumed by the Python generalization evaluator. */
    private static final class CheckpointProvenance {
        private final String checkpointRevision;
        private final String datasetSourceHash;
        private final List<String> trainingFingerprints;
        private final List<String> normalizationFingerprints;

        private CheckpointProvenance(
                String checkpointRevision,
                String datasetSourceHash,
                List<String> trainingFingerprints,
                List<String> normalizationFingerprints) {
            this.checkpointRevision = checkpointRevision;
            this.datasetSourceHash = datasetSourceHash;
            this.trainingFingerprints = List.copyOf(trainingFingerprints);
            this.normalizationFingerprints = List.copyOf(normalizationFingerprints);
        }
    }

    /** Validated split-specific recorded prior and its immutable provenance. */
    private static final class PriorArtifact {
        private final Path path;
        private final String sourceKind;
        private final String datasetSourceHash;
        private final String artifactHash;

        private PriorArtifact(
                Path path,
                String sourceKind,
                String datasetSourceHash,
                String artifactHash) {
            this.path = path;
            this.sourceKind = sourceKind;
            this.datasetSourceHash = datasetSourceHash;
            this.artifactHash = artifactHash;
        }

        /** Loads metadata and rejects stale, leaking, or incomplete split coverage. */
        private static PriorArtifact load(
                Path path,
                String expectedSourceKind,
                String expectedDatasetHash,
                FingerprintSplits splits) throws IOException {
            if (path == null || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Recorded policy prior does not exist: " + path);
            }
            byte[] bytes = Files.readAllBytes(path);
            PriorPayload payload;
            try {
                payload = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), PriorPayload.class);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Malformed recorded policy prior: " + path, exception);
            }
            if (payload == null
                    || payload.schemaVersion != RecordedExpertPolicyPrior.SCHEMA_VERSION
                    || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(payload.simulatorRevision)
                    || payload.actionLayoutRevision != PolicyAction.LAYOUT_REVISION
                    || payload.observationSchemaRevision != ObservationEncoder.SCHEMA_REVISION
                    || !expectedSourceKind.equals(payload.sourceKind)
                    || !expectedDatasetHash.equals(payload.datasetSourceHash)
                    || payload.trainingFingerprints == null
                    || payload.entries == null || payload.entries.length == 0) {
                throw new IllegalArgumentException(
                        "Recorded policy prior revision or provenance mismatch: " + path);
            }
            Set<String> declaredTraining = checkedSet(
                    payload.trainingFingerprints, "prior training fingerprints");
            Set<String> catalogTraining = splits.fingerprints(DatasetSplit.TRAIN);
            if (!declaredTraining.equals(catalogTraining)) {
                throw new IllegalArgumentException(
                        "Recorded policy prior training fingerprints are stale");
            }
            Set<String> allowed = new HashSet<>();
            if (RecordedExpertPolicyPrior.TRAINING_DATASET_STATES.equals(expectedSourceKind)) {
                allowed.addAll(catalogTraining);
            } else {
                allowed.addAll(splits.fingerprints(DatasetSplit.VALIDATION));
                allowed.addAll(splits.fingerprints(DatasetSplit.HOLDOUT));
            }
            Set<String> covered = new HashSet<>();
            for (PriorEntry entry : payload.entries) {
                if (entry == null || entry.scenarioFingerprint == null
                        || !allowed.contains(entry.scenarioFingerprint)) {
                    throw new IllegalArgumentException(
                            "Recorded policy prior entry violates catalog split provenance");
                }
                covered.add(entry.scenarioFingerprint);
            }
            if (!covered.equals(allowed)) {
                throw new IllegalArgumentException(
                        "Recorded policy prior does not cover every required scenario");
            }
            return new PriorArtifact(
                    path.toAbsolutePath().normalize(),
                    expectedSourceKind,
                    expectedDatasetHash,
                    sha256(bytes));
        }

        private RecordedExpertPolicyPrior priorFor(RotationScenario scenario) throws IOException {
            if (!artifactHash.equals(sha256(Files.readAllBytes(path)))) {
                throw new IllegalStateException(
                        "Recorded policy prior changed after validation: " + path);
            }
            RecordedExpertPolicyPrior prior = new RecordedExpertPolicyPrior(
                    path, scenario.getFingerprint());
            if (!sourceKind.equals(prior.getSourceKind())
                    || !datasetSourceHash.equals(prior.getDatasetSourceHash())
                    || prior.getKnownStateCount() <= 0) {
                throw new IllegalStateException("Recorded policy prior changed after validation");
            }
            return prior;
        }

        private String revision() {
            return "schema=" + RecordedExpertPolicyPrior.SCHEMA_VERSION
                    + ":source=" + sourceKind + ":sha256=" + artifactHash;
        }

        private static Set<String> checkedSet(String[] values, String name) {
            Set<String> result = new HashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank() || !result.add(value)) {
                    throw new IllegalArgumentException(name + " contain blanks or duplicates");
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return result;
        }
    }

    private static final class PriorPayload {
        private int schemaVersion;
        private String simulatorRevision;
        private int actionLayoutRevision;
        private int observationSchemaRevision;
        private String sourceKind;
        private String datasetSourceHash;
        private String[] trainingFingerprints;
        private PriorEntry[] entries;
    }

    private static final class PriorEntry {
        private String scenarioFingerprint;
    }

    private static final class DatasetReplay {
        private final String manifest;
        private final int schemaVersion;
        private final String simulatorRevision;
        private final String sourceHash;
        private final int totalRecords;
        private final int replayedRecords;
        private final int selectedScenarioRecords;
        private final double replayRate;

        private DatasetReplay(
                String manifest,
                int schemaVersion,
                String simulatorRevision,
                String sourceHash,
                int totalRecords,
                int replayedRecords,
                int selectedScenarioRecords,
                double replayRate) {
            this.manifest = manifest;
            this.schemaVersion = schemaVersion;
            this.simulatorRevision = simulatorRevision;
            this.sourceHash = sourceHash;
            this.totalRecords = totalRecords;
            this.replayedRecords = replayedRecords;
            this.selectedScenarioRecords = selectedScenarioRecords;
            this.replayRate = replayRate;
        }

        private void validate() {
            if (manifest == null || simulatorRevision == null
                    || !sourceHash.matches("[0-9a-f]{64}")
                    || schemaVersion != ExpertDatasetRecord.SCHEMA_VERSION
                    || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(simulatorRevision)
                    || totalRecords <= 0 || replayedRecords != totalRecords
                    || selectedScenarioRecords <= 0
                    || Double.doubleToLongBits(replayRate)
                            != Double.doubleToLongBits(1.0)) {
                throw new IllegalStateException("Dataset replay is incomplete or stale");
            }
        }

        private String revision() {
            return "schema=" + schemaVersion + ":simulator=" + simulatorRevision
                    + ":sha256=" + sourceHash;
        }
    }

    private static final class RepositoryState {
        private final String gitRevision;
        private final boolean dirtyTree;
        private final String javaVersion;
        private final String operatingSystem;
        private final String architecture;
        private final int availableProcessors;

        private RepositoryState(
                String gitRevision,
                boolean dirtyTree,
                String javaVersion,
                String operatingSystem,
                String architecture,
                int availableProcessors) {
            this.gitRevision = gitRevision;
            this.dirtyTree = dirtyTree;
            this.javaVersion = javaVersion;
            this.operatingSystem = operatingSystem;
            this.architecture = architecture;
            this.availableProcessors = availableProcessors;
        }

        private static RepositoryState capture() throws IOException, InterruptedException {
            String revision = runGit("rev-parse", "HEAD").trim();
            if (!revision.matches("[0-9a-f]{40}")) {
                throw new IllegalStateException("Git revision is missing or malformed");
            }
            boolean dirty = !runGit("status", "--porcelain")
                    .trim().isEmpty();
            return new RepositoryState(
                    revision,
                    dirty,
                    System.getProperty("java.version"),
                    System.getProperty("os.name") + " " + System.getProperty("os.version"),
                    System.getProperty("os.arch"),
                    Runtime.getRuntime().availableProcessors());
        }

        private static String runGit(String... arguments) throws IOException, InterruptedException {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(Arrays.asList(arguments));
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Git metadata command failed: " + output.trim());
            }
            return output;
        }
    }

    private static final class BenchmarkMetric {
        private final String method;
        private final long seed;
        private final String split;
        private final String scenarioFingerprint;
        private final double horizonSeconds;
        private final int simulatorCalls;
        private final long wallTimeNanos;
        private final double totalDamage;
        private final double dps;
        private final double energyDeficit;
        private final int invalidActionCount;
        private final double invalidActionRate;
        private final double objectiveScore;
        private final boolean cyclicEnergyFeasible;
        private final boolean complete;
        private final int archiveSize;
        private final double archiveDiversity;
        private final List<Double> archiveScores;
        private final int[] bestFoundActions;
        private final String simulatorRevision;
        private final String datasetRevision;
        private final String priorRevision;

        private BenchmarkMetric(
                String method,
                long seed,
                String split,
                String scenarioFingerprint,
                double horizonSeconds,
                int simulatorCalls,
                long wallTimeNanos,
                double totalDamage,
                double dps,
                double energyDeficit,
                int invalidActionCount,
                double invalidActionRate,
                double objectiveScore,
                boolean cyclicEnergyFeasible,
                boolean complete,
                int archiveSize,
                double archiveDiversity,
                List<Double> archiveScores,
                int[] bestFoundActions,
                String simulatorRevision,
                String datasetRevision,
                String priorRevision) {
            this.method = method;
            this.seed = seed;
            this.split = split;
            this.scenarioFingerprint = scenarioFingerprint;
            this.horizonSeconds = horizonSeconds;
            this.simulatorCalls = simulatorCalls;
            this.wallTimeNanos = wallTimeNanos;
            this.totalDamage = totalDamage;
            this.dps = dps;
            this.energyDeficit = energyDeficit;
            this.invalidActionCount = invalidActionCount;
            this.invalidActionRate = invalidActionRate;
            this.objectiveScore = objectiveScore;
            this.cyclicEnergyFeasible = cyclicEnergyFeasible;
            this.complete = complete;
            this.archiveSize = archiveSize;
            this.archiveDiversity = archiveDiversity;
            this.archiveScores = List.copyOf(archiveScores);
            this.bestFoundActions = bestFoundActions.clone();
            this.simulatorRevision = simulatorRevision;
            this.datasetRevision = datasetRevision;
            this.priorRevision = priorRevision;
        }

        private static BenchmarkMetric from(
                String method,
                PartyDefinition definition,
                RotationScenario scenario,
                SearchOutcome outcome,
                DatasetReplay datasetReplay,
                String priorRevision) {
            Candidate best = outcome.best();
            RotationObjective.Score score = best.objective;
            double invalidRate = (double) score.invalidActionCount / best.actions.length;
            List<Double> archiveScores = "model-only".equals(method)
                    ? List.of() : outcome.incumbentHistory;
            BenchmarkMetric metric = new BenchmarkMetric(
                    method,
                    scenario.getSeed(),
                    definition.datasetSplit().getWireName(),
                    scenario.getFingerprint(),
                    scenario.getHorizonSeconds(),
                    outcome.simulatorCalls,
                    outcome.wallTimeNanos,
                    score.totalDamage,
                    score.dps,
                    score.energyDeficit,
                    score.invalidActionCount,
                    invalidRate,
                    score.objectiveScore,
                    score.cyclicEnergyFeasible,
                    best.complete,
                    outcome.archive.size(),
                    archiveDiversity(outcome.archive),
                    archiveScores,
                    best.actions,
                    ExpertDatasetRecord.SIMULATOR_REVISION,
                    datasetReplay.revision(),
                    priorRevision);
            metric.validate();
            return metric;
        }

        private void validate() {
            if (method == null || split == null || scenarioFingerprint == null
                    || simulatorRevision == null || simulatorCalls <= 0
                    || wallTimeNanos < 0L || invalidActionCount < 0
                    || archiveSize <= 0
                    || bestFoundActions.length == 0) {
                throw new IllegalStateException("Benchmark metric metadata is invalid");
            }
            if (!"model-only".equals(method) && archiveScores.isEmpty()) {
                throw new IllegalStateException("Search metric archive history is empty");
            }
            requireFinite(horizonSeconds, "horizonSeconds");
            requireFinite(totalDamage, "totalDamage");
            requireFinite(dps, "dps");
            requireFinite(energyDeficit, "energyDeficit");
            requireFinite(invalidActionRate, "invalidActionRate");
            requireFinite(objectiveScore, "objectiveScore");
            requireFinite(archiveDiversity, "archiveDiversity");
            double previous = Double.NEGATIVE_INFINITY;
            for (double archiveScore : archiveScores) {
                requireFinite(archiveScore, "archiveScores");
                if (archiveScore < previous) {
                    throw new IllegalStateException("Archive incumbent history regressed");
                }
                previous = archiveScore;
            }
            if (horizonSeconds <= 0.0 || totalDamage < 0.0 || dps < 0.0
                    || energyDeficit < 0.0 || invalidActionRate < 0.0
                    || invalidActionRate > 1.0 || archiveDiversity < 0.0
                    || archiveDiversity > 1.0) {
                throw new IllegalStateException("Benchmark metric is outside its valid range");
            }
            for (int actionId : bestFoundActions) {
                PolicyAction.fromId(actionId);
            }
        }
    }

    private static final class BenchmarkReport {
        private final int schemaVersion = REPORT_SCHEMA_VERSION;
        private final String benchmarkRevision = BENCHMARK_REVISION;
        private final String simulatorRevision = ExpertDatasetRecord.SIMULATOR_REVISION;
        private final String generatedAt = Instant.now().toString();
        private final RepositoryState repository;
        private final String selectedSplit;
        private final long[] seeds;
        private final int simulatorCallBudgetPerMethod;
        private final int maxActionsPerTrajectory;
        private final int archiveCapacity;
        private final String simulatorCallDefinition = "one measured RotationEnvironment.step";
        private final String archiveDiversityDefinition =
                "mean pairwise normalized Levenshtein distance over retained action sequences";
        private final DatasetReplay datasetReplay;
        private final FingerprintSplits fingerprintSplits;
        private final List<String> selectedScenarioFingerprints;
        private final CheckpointProvenance checkpointProvenance;
        private final List<BenchmarkMetric> metrics;
        private final List<String> unsupportedComparisons;

        private BenchmarkReport(
                RepositoryState repository,
                Options options,
                DatasetReplay datasetReplay,
                FingerprintSplits fingerprintSplits,
                List<PartyDefinition> definitions,
                CheckpointProvenance checkpointProvenance,
                List<BenchmarkMetric> metrics,
                List<String> unsupportedComparisons) {
            this.repository = repository;
            this.selectedSplit = options.split;
            this.seeds = options.seeds.clone();
            this.simulatorCallBudgetPerMethod = options.callBudget;
            this.maxActionsPerTrajectory = options.maxActions;
            this.archiveCapacity = options.archiveSize;
            this.datasetReplay = datasetReplay;
            this.fingerprintSplits = fingerprintSplits;
            List<String> selected = new ArrayList<>();
            for (PartyDefinition definition : definitions) {
                selected.add(createScenario(definition, 0L).getFingerprint());
            }
            this.selectedScenarioFingerprints = List.copyOf(selected);
            this.checkpointProvenance = checkpointProvenance;
            this.metrics = List.copyOf(metrics);
            this.unsupportedComparisons = List.copyOf(unsupportedComparisons);
        }

        private void validate() {
            if (repository == null || selectedSplit == null || seeds.length == 0
                    || simulatorCallBudgetPerMethod <= 0 || maxActionsPerTrajectory <= 0
                    || archiveCapacity <= 0 || datasetReplay == null
                    || fingerprintSplits == null || selectedScenarioFingerprints.isEmpty()
                    || checkpointProvenance == null || metrics.isEmpty()
                    || schemaVersion != REPORT_SCHEMA_VERSION
                    || !BENCHMARK_REVISION.equals(benchmarkRevision)
                    || !ExpertDatasetRecord.SIMULATOR_REVISION.equals(simulatorRevision)) {
                throw new IllegalStateException("Benchmark report metadata is invalid");
            }
            datasetReplay.validate();
            Set<String> methods = new HashSet<>();
            Set<String> actualRuns = new HashSet<>();
            Set<String> selectedFingerprints = Set.copyOf(selectedScenarioFingerprints);
            Set<Long> selectedSeeds = new HashSet<>();
            for (long seed : seeds) {
                if (!selectedSeeds.add(seed)) {
                    throw new IllegalStateException("Benchmark report contains duplicate seeds");
                }
            }
            for (BenchmarkMetric metric : metrics) {
                metric.validate();
                methods.add(metric.method);
                if (!selectedFingerprints.contains(metric.scenarioFingerprint)
                        || !selectedSeeds.contains(metric.seed)) {
                    throw new IllegalStateException("Benchmark metric is outside selected coverage");
                }
                String runKey = metric.method + "\n" + metric.scenarioFingerprint
                        + "\n" + metric.seed;
                if (!actualRuns.add(runKey)) {
                    throw new IllegalStateException("Duplicate benchmark metric: " + runKey);
                }
                int expectedCalls = "model-only".equals(metric.method)
                        ? 1 : simulatorCallBudgetPerMethod;
                if (metric.simulatorCalls != expectedCalls) {
                    throw new IllegalStateException("Report contains an unequal simulator-call budget");
                }
            }
            Set<String> expectedMethods = Set.of(
                    "deterministic-random",
                    "unguided-evolutionary",
                    "policy-guided",
                    "model-only");
            if (!methods.equals(expectedMethods) || !unsupportedComparisons.isEmpty()) {
                throw new IllegalStateException("Benchmark method coverage is incomplete");
            }
            Set<String> expectedRuns = new HashSet<>();
            for (String fingerprint : selectedScenarioFingerprints) {
                for (long seed : seeds) {
                    for (String method : expectedMethods) {
                        expectedRuns.add(method + "\n" + fingerprint + "\n" + seed);
                    }
                }
            }
            if (!actualRuns.equals(expectedRuns)) {
                throw new IllegalStateException("Benchmark scenario/seed coverage is incomplete");
            }
        }
    }
}
