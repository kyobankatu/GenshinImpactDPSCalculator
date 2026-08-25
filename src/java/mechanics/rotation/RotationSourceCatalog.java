package mechanics.rotation;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import model.type.CharacterId;
import simulation.party.DatasetSplit;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/**
 * Strict loader for the reviewed, offline human-rotation source catalog.
 *
 * <p>
 * Remote content never enters this class. The loader accepts only tracked JSON,
 * validates exact party/loadout identity, and exposes rejected entries only for
 * audit rather than simulator construction.
 */
public final class RotationSourceCatalog {
    public static final int SCHEMA_VERSION = 1;
    public static final Path DEFAULT_PATH = Path.of("config", "rotation_seeds", "catalog.json");

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final int schemaVersion;
    private final int actionLayoutRevision;
    private final List<SourceReference> sources;
    private final List<SourcedRotationSeed> seeds;

    /** Creates an immutable catalog and validates all cross-references. */
    public RotationSourceCatalog(
            int schemaVersion,
            int actionLayoutRevision,
            List<SourceReference> sources,
            List<SourcedRotationSeed> seeds) {
        this.schemaVersion = schemaVersion;
        this.actionLayoutRevision = actionLayoutRevision;
        this.sources = sources == null ? null : List.copyOf(sources);
        this.seeds = seeds == null ? null : List.copyOf(seeds);
        validate();
    }

    /** Reads and validates a catalog from a tracked local path. */
    public static RotationSourceCatalog read(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Rotation source catalog does not exist: " + path);
        }
        try {
            RotationSourceCatalog catalog = GSON.fromJson(Files.readString(path), RotationSourceCatalog.class);
            if (catalog == null) {
                throw new IllegalArgumentException("Rotation source catalog is empty: " + path);
            }
            catalog.validate();
            return catalog;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Malformed rotation source catalog: " + path, exception);
        }
    }

    /** Loads the tracked default catalog. */
    public static RotationSourceCatalog loadDefault() throws IOException {
        return read(DEFAULT_PATH);
    }

    /** Validates revisions, provenance, hashes, and exact party identities. */
    public void validate() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Rotation source schema revision mismatch: " + schemaVersion);
        }
        if (actionLayoutRevision != PolicyAction.LAYOUT_REVISION) {
            throw new IllegalArgumentException("Rotation action layout revision mismatch: "
                    + actionLayoutRevision);
        }
        if (sources == null || seeds == null) {
            throw new IllegalArgumentException("Rotation source lists must not be null");
        }
        Map<String, SourceReference> sourcesById = new HashMap<>();
        for (SourceReference source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("Rotation source must not be null");
            }
            source.validate();
            if (sourcesById.putIfAbsent(source.getSourceId(), source) != null) {
                throw new IllegalArgumentException("Duplicate rotation source ID: " + source.getSourceId());
            }
        }
        Set<String> seedIds = new HashSet<>();
        for (SourcedRotationSeed seed : seeds) {
            if (seed == null) {
                throw new IllegalArgumentException("Rotation seed must not be null");
            }
            seed.validate();
            if (!seedIds.add(seed.getSeedId())) {
                throw new IllegalArgumentException("Duplicate rotation seed ID: " + seed.getSeedId());
            }
            for (String sourceId : seed.getSourceIds()) {
                if (!sourcesById.containsKey(sourceId)) {
                    throw new IllegalArgumentException("Unknown source " + sourceId + " in " + seed.getSeedId());
                }
            }
            validateParty(seed);
        }
    }

    private static void validateParty(SourcedRotationSeed seed) {
        if (!seed.isUsable()) {
            return;
        }
        PartyDefinition definition = PartyCatalog.find(seed.getPartyName());
        if (definition == null) {
            throw new IllegalArgumentException("Unknown seed party: " + seed.getPartyName());
        }
        if (!definition.loadoutFingerprint().equals(seed.getScenarioFingerprint())) {
            throw new IllegalArgumentException("Seed loadout fingerprint mismatch: " + seed.getSeedId());
        }
        Set<String> expectedCharacters = new LinkedHashSet<>();
        for (CharacterId characterId : definition.partyOrder()) {
            expectedCharacters.add(characterId.name());
        }
        Set<String> actualCharacters = new LinkedHashSet<>();
        for (String characterName : seed.getErTargets().keySet()) {
            try {
                CharacterId characterId = CharacterId.valueOf(characterName);
                if (characterId == CharacterId.UNKNOWN) {
                    throw new IllegalArgumentException("Unknown ER target character: " + characterName);
                }
                actualCharacters.add(characterId.name());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown ER target character: " + characterName, exception);
            }
        }
        if (!expectedCharacters.equals(actualCharacters)) {
            throw new IllegalArgumentException("Seed ER targets do not match party order: " + seed.getSeedId()
                    + " expected=" + Arrays.toString(definition.partyOrder()));
        }
    }

    /** Returns the usable seed with the exact supplied ID. */
    public SourcedRotationSeed requireUsable(String seedId) {
        for (SourcedRotationSeed seed : seeds) {
            if (seed.getSeedId().equals(seedId)) {
                if (!seed.isUsable()) {
                    throw new IllegalArgumentException("Rotation seed is rejected: " + seedId);
                }
                return seed;
            }
        }
        throw new IllegalArgumentException("Unknown rotation seed: " + seedId);
    }

    /** Returns deterministic pretty JSON for round-trip and review tooling. */
    public String toJson() {
        return GSON.toJson(this);
    }

    public List<SourceReference> getSources() {
        return List.copyOf(sources);
    }

    public List<SourcedRotationSeed> getSeeds() {
        return List.copyOf(seeds);
    }

    /** Returns usable seeds whose exact party belongs to the requested split. */
    public List<SourcedRotationSeed> getUsableSeeds(DatasetSplit split) {
        if (split == null) {
            throw new IllegalArgumentException("Dataset split must not be null");
        }
        List<SourcedRotationSeed> matching = new ArrayList<>();
        for (SourcedRotationSeed seed : seeds) {
            if (!seed.isUsable()) {
                continue;
            }
            PartyDefinition definition = PartyCatalog.require(seed.getPartyName());
            if (definition.datasetSplit() == split) {
                matching.add(seed);
            }
        }
        return List.copyOf(matching);
    }

    /** Source metadata pinned at review time. */
    public static final class SourceReference {
        private final String sourceId;
        private final String title;
        private final String url;
        private final String publisher;
        private final String sourceRevision;
        private final String accessDate;
        private final int targetCount;
        private final List<String> loadoutAssumptions;

        /** Creates one immutable technical source reference. */
        public SourceReference(
                String sourceId,
                String title,
                String url,
                String publisher,
                String sourceRevision,
                String accessDate,
                int targetCount,
                List<String> loadoutAssumptions) {
            this.sourceId = sourceId;
            this.title = title;
            this.url = url;
            this.publisher = publisher;
            this.sourceRevision = sourceRevision;
            this.accessDate = accessDate;
            this.targetCount = targetCount;
            this.loadoutAssumptions = loadoutAssumptions == null
                    ? null
                    : List.copyOf(loadoutAssumptions);
            validate();
        }

        private void validate() {
            requireText(sourceId, "sourceId");
            requireText(title, "title");
            requireText(url, "url");
            requireText(publisher, "publisher");
            requireText(sourceRevision, "sourceRevision");
            requireText(accessDate, "accessDate");
            try {
                URI uri = new URI(url);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException("Rotation source URL must be HTTP(S): " + sourceId);
                }
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Malformed rotation source URL: " + sourceId, exception);
            }
            try {
                LocalDate.parse(accessDate);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Malformed rotation source access date: " + sourceId, exception);
            }
            if (targetCount <= 0 || loadoutAssumptions == null) {
                throw new IllegalArgumentException("Invalid rotation source assumptions: " + sourceId);
            }
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }

        public String getSourceId() {
            return sourceId;
        }
    }
}
