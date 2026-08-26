package mechanics.rotation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mechanics.optimization.TotalOptimizationResult;
import mechanics.rotation.RotationTeacherQualityReport.Arm;
import mechanics.rotation.RotationTeacherQualityReport.ArmSummary;
import mechanics.rotation.RotationTeacherQualityReport.ScenarioResult;
import model.type.CharacterId;
import model.type.StatType;
import simulation.party.PartyDefinition;

/** Frozen source, search, and optimized-build provenance for one expert label. */
public final class ExpertDatasetProvenance {
    public static final String ARTIFACT_STANDARD_REVISION =
            "artifact-kqms-generic-v1";

    private final String sourceSeedId;
    private final String sourceContentHash;
    private final List<String> sourceIds;
    private final String sourceAdaptationStatus;
    private final List<String> parentRecordIds;
    private final String searchMode;
    private final int feasibilityRank;
    private final int completedCandidates;
    private final int completedGenerations;
    private final double humanMedianObjective;
    private final double randomMedianObjective;
    private final double teacherMedianObjective;
    private final List<Long> qualitySearchSeeds;
    private final long searchSeed;
    private final String buildMode;
    private final String artifactStandardRevision;
    private final String buildFingerprint;
    private final String rollHash;
    private final String partyLoadoutFingerprint;
    private final List<String> partyCharacters;
    private final String partyArchetype;
    private final String scenarioBuildFingerprint;
    private final Map<String, Double> erTargets;
    private final Map<String, Map<String, Integer>> partyRolls;

    /** Creates and validates one complete provenance payload. */
    public ExpertDatasetProvenance(
            String sourceSeedId,
            String sourceContentHash,
            List<String> sourceIds,
            String sourceAdaptationStatus,
            List<String> parentRecordIds,
            String searchMode,
            int feasibilityRank,
            int completedCandidates,
            int completedGenerations,
            double humanMedianObjective,
            double randomMedianObjective,
            double teacherMedianObjective,
            List<Long> qualitySearchSeeds,
            long searchSeed,
            String buildMode,
            String artifactStandardRevision,
            String buildFingerprint,
            String rollHash,
            String partyLoadoutFingerprint,
            List<String> partyCharacters,
            String partyArchetype,
            String scenarioBuildFingerprint,
            Map<String, Double> erTargets,
            Map<String, Map<String, Integer>> partyRolls) {
        this.sourceSeedId = sourceSeedId;
        this.sourceContentHash = sourceContentHash;
        this.sourceIds = sourceIds == null ? null : List.copyOf(sourceIds);
        this.sourceAdaptationStatus = sourceAdaptationStatus;
        this.parentRecordIds = parentRecordIds == null
                ? null : List.copyOf(parentRecordIds);
        this.searchMode = searchMode;
        this.feasibilityRank = feasibilityRank;
        this.completedCandidates = completedCandidates;
        this.completedGenerations = completedGenerations;
        this.humanMedianObjective = humanMedianObjective;
        this.randomMedianObjective = randomMedianObjective;
        this.teacherMedianObjective = teacherMedianObjective;
        this.qualitySearchSeeds = qualitySearchSeeds == null
                ? null : sortedSeeds(qualitySearchSeeds);
        this.searchSeed = searchSeed;
        this.buildMode = buildMode;
        this.artifactStandardRevision = artifactStandardRevision;
        this.buildFingerprint = buildFingerprint;
        this.rollHash = rollHash;
        this.partyLoadoutFingerprint = partyLoadoutFingerprint;
        this.partyCharacters = partyCharacters == null
                ? null : List.copyOf(partyCharacters);
        this.partyArchetype = partyArchetype;
        this.scenarioBuildFingerprint = scenarioBuildFingerprint;
        this.erTargets = immutableErTargets(erTargets);
        this.partyRolls = immutablePartyRolls(partyRolls);
        validate();
    }

    /** Captures one retained search result and its exact frozen build. */
    public static ExpertDatasetProvenance capture(
            SourcedRotationSeed sourceSeed,
            PartyDefinition definition,
            TotalOptimizationResult build,
            RotationScenario scenario,
            ScenarioResult quality,
            RotationSearchStrategy.Result searchResult,
            int feasibilityRank,
            List<String> parentRecordIds) {
        if (sourceSeed == null
                || definition == null
                || build == null
                || scenario == null
                || quality == null
                || searchResult == null) {
            throw new IllegalArgumentException("Complete teacher provenance inputs are required");
        }
        Arm teacher = quality.getRetainedTeacher();
        ArmSummary human = quality.getSummaries().get(Arm.HUMAN_SEED.ordinal());
        ArmSummary random = quality.getSummaries().get(
                Arm.DETERMINISTIC_RANDOM.ordinal());
        ArmSummary retained = quality.getRetainedTeacherSummary();
        Map<String, Double> erTargets = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> partyRolls = new LinkedHashMap<>();
        List<Long> qualitySearchSeeds = new ArrayList<>();
        for (RotationTeacherQualityReport.Trial trial : quality.getTrials()) {
            if (trial.getArm() == Arm.HUMAN_SEED) {
                qualitySearchSeeds.add(trial.getSearchSeed());
            }
        }
        List<String> partyCharacters = new ArrayList<>();
        for (CharacterId characterId : definition.partyOrder()) {
            partyCharacters.add(characterId.name());
        }
        for (CharacterId characterId : CharacterId.values()) {
            Double er = build.erTargets.get(characterId);
            if (er == null) {
                continue;
            }
            erTargets.put(characterId.name(), er);
            Map<String, Integer> rolls = new LinkedHashMap<>();
            for (StatType statType : StatType.values()) {
                Integer count = build.partyRolls.get(characterId).get(statType);
                if (count != null) {
                    rolls.put(statType.name(), count);
                }
            }
            partyRolls.put(characterId.name(), rolls);
        }
        return new ExpertDatasetProvenance(
                sourceSeed.getSeedId(),
                sourceSeed.getContentHash(),
                sourceSeed.getSourceIds(),
                sourceSeed.getAdaptationStatus().name().toLowerCase(),
                parentRecordIds,
                teacher.getWireName(),
                feasibilityRank,
                searchResult.statistics.completedTrajectories,
                searchResult.statistics.completedGenerations,
                human.getMedianObjective(),
                random.getMedianObjective(),
                retained.getMedianObjective(),
                qualitySearchSeeds,
                scenario.getSeed(),
                TotalOptimizationResult.BUILD_MODE,
                ARTIFACT_STANDARD_REVISION,
                build.getBuildFingerprint(),
                calculateRollHash(partyRolls),
                definition.loadoutFingerprint(),
                partyCharacters,
                "primary:" + partyCharacters.get(0),
                scenario.getFingerprint(),
                erTargets,
                partyRolls);
    }

    /** Validates source identity, search evidence, and reconstructable build inputs. */
    public void validate() {
        requireText(sourceSeedId, "sourceSeedId");
        requireHash(sourceContentHash, "sourceContentHash");
        if (sourceIds == null
                || sourceIds.isEmpty()
                || Set.copyOf(sourceIds).size() != sourceIds.size()) {
            throw new IllegalArgumentException("Dataset source IDs are invalid");
        }
        for (String sourceId : sourceIds) {
            requireText(sourceId, "sourceId");
        }
        if (!"accepted".equals(sourceAdaptationStatus)
                && !"adapted".equals(sourceAdaptationStatus)) {
            throw new IllegalArgumentException("Source seed is not usable: " + sourceSeedId);
        }
        if (parentRecordIds == null) {
            throw new IllegalArgumentException("parentRecordIds must not be null");
        }
        for (String parentRecordId : parentRecordIds) {
            requireText(parentRecordId, "parentRecordId");
        }
        Arm arm = Arm.fromWireName(searchMode);
        if (arm != Arm.UNGUIDED_EVOLUTIONARY && arm != Arm.UNGUIDED_MCTS) {
            throw new IllegalArgumentException("Dataset search mode is not a teacher arm");
        }
        if (feasibilityRank < 0 || completedCandidates <= 0 || completedGenerations < 0) {
            throw new IllegalArgumentException("Invalid search accounting provenance");
        }
        if ((feasibilityRank == 0) != parentRecordIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only the feasibility-rank root may omit parent lineage");
        }
        if (arm == Arm.UNGUIDED_EVOLUTIONARY && completedGenerations == 0) {
            throw new IllegalArgumentException("Evolutionary provenance has no complete generation");
        }
        if (!Double.isFinite(humanMedianObjective)
                || !Double.isFinite(randomMedianObjective)
                || !Double.isFinite(teacherMedianObjective)
                || teacherMedianObjective < humanMedianObjective
                || teacherMedianObjective < randomMedianObjective) {
            throw new IllegalArgumentException("Teacher baseline provenance failed");
        }
        if (qualitySearchSeeds == null
                || qualitySearchSeeds.size()
                        < RotationTeacherQualityReport.MINIMUM_SEARCH_SEEDS) {
            throw new IllegalArgumentException("Teacher quality seed provenance is incomplete");
        }
        for (int index = 1; index < qualitySearchSeeds.size(); index++) {
            if (qualitySearchSeeds.get(index - 1).equals(qualitySearchSeeds.get(index))) {
                throw new IllegalArgumentException("Teacher quality seed provenance is duplicated");
            }
        }
        if (!TotalOptimizationResult.BUILD_MODE.equals(buildMode)
                || !ARTIFACT_STANDARD_REVISION.equals(artifactStandardRevision)) {
            throw new IllegalArgumentException("Dataset build revision mismatch");
        }
        requireText(partyLoadoutFingerprint, "partyLoadoutFingerprint");
        if (partyCharacters == null
                || partyCharacters.size() != 4
                || Set.copyOf(partyCharacters).size() != partyCharacters.size()) {
            throw new IllegalArgumentException("Dataset party characters are invalid");
        }
        for (String character : partyCharacters) {
            CharacterId.valueOf(character);
        }
        if (!(("primary:" + partyCharacters.get(0)).equals(partyArchetype))) {
            throw new IllegalArgumentException("Dataset party archetype is invalid");
        }
        requireText(scenarioBuildFingerprint, "scenarioBuildFingerprint");
        requireHash(rollHash, "rollHash");
        TotalOptimizationResult build = reconstructBuild();
        if (!build.getBuildFingerprint().equals(buildFingerprint)
                || !calculateRollHash(partyRolls).equals(rollHash)) {
            throw new IllegalArgumentException("Dataset frozen build fingerprint mismatch");
        }
    }

    /** Reconstructs the exact optimizer result without rerunning optimization. */
    public TotalOptimizationResult reconstructBuild() {
        if (erTargets == null || partyRolls == null) {
            throw new IllegalArgumentException("Dataset frozen build maps are required");
        }
        Map<CharacterId, Double> typedEr = new EnumMap<>(CharacterId.class);
        Map<CharacterId, Map<StatType, Integer>> typedRolls =
                new EnumMap<>(CharacterId.class);
        for (Map.Entry<String, Double> entry : erTargets.entrySet()) {
            CharacterId characterId = CharacterId.valueOf(entry.getKey());
            typedEr.put(characterId, entry.getValue());
            Map<String, Integer> storedRolls = partyRolls.get(entry.getKey());
            if (storedRolls == null) {
                throw new IllegalArgumentException(
                        "Dataset roll map omits " + entry.getKey());
            }
            Map<StatType, Integer> rolls = new EnumMap<>(StatType.class);
            for (Map.Entry<String, Integer> roll : storedRolls.entrySet()) {
                rolls.put(StatType.valueOf(roll.getKey()), roll.getValue());
            }
            typedRolls.put(characterId, rolls);
        }
        if (partyRolls.size() != typedRolls.size()) {
            throw new IllegalArgumentException("Dataset build maps cover different characters");
        }
        return new TotalOptimizationResult(typedEr, typedRolls);
    }

    public String getSourceSeedId() {
        return sourceSeedId;
    }

    public String getSourceContentHash() {
        return sourceContentHash;
    }

    public List<String> getParentRecordIds() {
        return parentRecordIds;
    }

    public String getSearchMode() {
        return searchMode;
    }

    public int getFeasibilityRank() {
        return feasibilityRank;
    }

    public long getSearchSeed() {
        return searchSeed;
    }

    public String getBuildFingerprint() {
        return buildFingerprint;
    }

    public String getPartyLoadoutFingerprint() {
        return partyLoadoutFingerprint;
    }

    public List<String> getPartyCharacters() {
        return partyCharacters;
    }

    public String getScenarioBuildFingerprint() {
        return scenarioBuildFingerprint;
    }

    private static Map<String, Double> immutableErTargets(
            Map<String, Double> source) {
        if (source == null) {
            return null;
        }
        Map<String, Double> copy = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            copy.put(key, source.get(key));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<Long> sortedSeeds(List<Long> source) {
        List<Long> copy = new ArrayList<>(source);
        if (copy.contains(null)) {
            throw new IllegalArgumentException("Teacher quality seeds must not contain null");
        }
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, Map<String, Integer>> immutablePartyRolls(
            Map<String, Map<String, Integer>> source) {
        if (source == null) {
            return null;
        }
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        List<String> characters = new ArrayList<>(source.keySet());
        Collections.sort(characters);
        for (String character : characters) {
            Map<String, Integer> sourceRolls = source.get(character);
            if (sourceRolls == null) {
                copy.put(character, null);
                continue;
            }
            Map<String, Integer> rolls = new LinkedHashMap<>();
            List<String> stats = new ArrayList<>(sourceRolls.keySet());
            Collections.sort(stats);
            for (String stat : stats) {
                rolls.put(stat, sourceRolls.get(stat));
            }
            copy.put(character, Collections.unmodifiableMap(rolls));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String calculateRollHash(
            Map<String, Map<String, Integer>> rolls) {
        StringBuilder canonical = new StringBuilder();
        for (String character : new java.util.TreeSet<>(rolls.keySet())) {
            Map<String, Integer> characterRolls = rolls.get(character);
            canonical.append(character).append('{');
            for (String stat : new java.util.TreeSet<>(characterRolls.keySet())) {
                canonical.append(stat).append('=').append(characterRolls.get(stat)).append(';');
            }
            canonical.append('}');
        }
        return ExpertDatasetRecord.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be SHA-256");
        }
    }
}
