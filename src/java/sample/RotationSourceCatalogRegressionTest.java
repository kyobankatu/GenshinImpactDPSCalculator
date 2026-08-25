package sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationSourceCatalog;
import mechanics.rotation.SourcedRotationSeed;
import mechanics.rotation.SourcedRotationSeed.AdaptationStatus;
import simulation.party.DatasetSplit;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Regression checks for the fail-closed human rotation source catalog. */
public final class RotationSourceCatalogRegressionTest {
    private static final String FIXTURE_HASH =
            "273373cc8af131cc10c985d34ca0f6346b185b6859d294f2acfc2293f21c9ec8";

    private RotationSourceCatalogRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        assertTrackedPilotCatalogRoundTrips();
        assertCanonicalFixtureHash();
        assertDefensiveActions();
        assertAlteredHashRejected();
        assertInvalidCatalogsRejected();
        System.out.println("RotationSourceCatalogRegressionTest passed");
    }

    private static void assertTrackedPilotCatalogRoundTrips() throws Exception {
        RotationSourceCatalog catalog = RotationSourceCatalog.loadDefault();
        if (catalog.getSources().size() != 11 || catalog.getSeeds().size() != 11) {
            throw new AssertionError("Expected eleven reviewed rotation sources and seeds");
        }
        HashSet<String> parties = new HashSet<>();
        int rejectedCount = 0;
        for (SourcedRotationSeed seed : catalog.getSeeds()) {
            if (!parties.add(seed.getPartyName())) {
                throw new AssertionError("Invalid reviewed pilot seed: " + seed.getSeedId());
            }
            if (seed.getAdaptationStatus() == AdaptationStatus.REJECTED) {
                rejectedCount++;
                continue;
            }
            if (seed.getAdaptationStatus() != AdaptationStatus.ADAPTED
                    || catalog.requireUsable(seed.getSeedId()) != seed) {
                throw new AssertionError("Invalid usable pilot seed: " + seed.getSeedId());
            }
        }
        if (rejectedCount != 4) {
            throw new AssertionError("Expected four simulator-infeasible pilot seeds");
        }
        assertEquals(6, catalog.getUsableSeeds(DatasetSplit.TRAIN).size(), "train seeds");
        assertEquals(1, catalog.getUsableSeeds(DatasetSplit.VALIDATION).size(), "validation seeds");
        assertEquals(0, catalog.getUsableSeeds(DatasetSplit.HOLDOUT).size(), "holdout seeds");
        Path roundTrip = Files.createTempFile("rotation-source-catalog-", ".json");
        Files.writeString(roundTrip, catalog.toJson());
        RotationSourceCatalog loaded = RotationSourceCatalog.read(roundTrip);
        if (!catalog.toJson().equals(loaded.toJson())) {
            throw new AssertionError("Empty source catalog did not round trip deterministically");
        }
    }

    private static void assertCanonicalFixtureHash() {
        SourcedRotationSeed seed = fixtureSeed(AdaptationStatus.ACCEPTED, fingerprint(), cycle());
        if (!FIXTURE_HASH.equals(seed.getContentHash())) {
            throw new AssertionError("Cross-language seed hash changed: " + seed.getContentHash());
        }
        RotationSourceCatalog catalog = new RotationSourceCatalog(
                RotationSourceCatalog.SCHEMA_VERSION,
                PolicyAction.LAYOUT_REVISION,
                List.of(fixtureSource()),
                List.of(seed));
        if (catalog.requireUsable(seed.getSeedId()) != seed) {
            throw new AssertionError("Usable seed lookup changed identity");
        }
        SourcedRotationSeed adapted = fixtureSeed(
                AdaptationStatus.ADAPTED, "documented timing translation", fingerprint(), cycle());
        new RotationSourceCatalog(
                RotationSourceCatalog.SCHEMA_VERSION,
                PolicyAction.LAYOUT_REVISION,
                List.of(fixtureSource()),
                List.of(adapted)).requireUsable(adapted.getSeedId());
    }

    private static void assertDefensiveActions() {
        SourcedRotationSeed seed = fixtureSeed(AdaptationStatus.ACCEPTED, fingerprint(), cycle());
        int[] opener = seed.getOpenerActions();
        opener[0] = PolicyAction.BURST.getId();
        if (seed.getOpenerActions()[0] != PolicyAction.SKILL_PRESS.getId()) {
            throw new AssertionError("Seed opener leaked a mutable action array");
        }
        List<int[]> cycles = seed.getCycleActions();
        cycles.get(0)[0] = PolicyAction.NORMAL.getId();
        if (seed.getCycleActions().get(0)[0] != PolicyAction.SWAP_SLOT_1.getId()) {
            throw new AssertionError("Seed cycle leaked a mutable action array");
        }
    }

    private static void assertAlteredHashRejected() throws Exception {
        RotationSourceCatalog catalog = new RotationSourceCatalog(
                1,
                PolicyAction.LAYOUT_REVISION,
                List.of(fixtureSource()),
                List.of(fixtureSeed(AdaptationStatus.ACCEPTED, fingerprint(), cycle())));
        String corrupted = catalog.toJson().replace(
                FIXTURE_HASH,
                "0000000000000000000000000000000000000000000000000000000000000000");
        Path path = Files.createTempFile("rotation-source-corrupt-", ".json");
        Files.writeString(path, corrupted);
        expectFailure(() -> readUnchecked(path), "altered content hash");
    }

    private static void assertInvalidCatalogsRejected() {
        RotationSourceCatalog.SourceReference source = fixtureSource();
        SourcedRotationSeed seed = fixtureSeed(AdaptationStatus.ACCEPTED, fingerprint(), cycle());
        expectFailure(() -> new RotationSourceCatalog(
                2, PolicyAction.LAYOUT_REVISION, List.of(source), List.of(seed)), "stale schema");
        expectFailure(() -> new RotationSourceCatalog(
                1, PolicyAction.LAYOUT_REVISION + 1, List.of(source), List.of(seed)), "stale action revision");
        expectFailure(() -> new RotationSourceCatalog(
                1, PolicyAction.LAYOUT_REVISION, List.of(source, source), List.of(seed)), "duplicate source");
        expectFailure(() -> new RotationSourceCatalog(
                1, PolicyAction.LAYOUT_REVISION, List.of(source), List.of(seed, seed)), "duplicate seed");
        expectFailure(() -> new RotationSourceCatalog(
                1,
                PolicyAction.LAYOUT_REVISION,
                List.of(source),
                List.of(fixtureSeed(AdaptationStatus.ACCEPTED, "stale-loadout", cycle()))),
                "stale fingerprint");
        expectFailure(() -> fixtureSeed(
                AdaptationStatus.ACCEPTED, fingerprint(), new int[] {99}), "unknown action");
        expectFailure(() -> new RotationSourceCatalog.SourceReference(
                "bad-url", "Bad", "file:///tmp/input", "fixture", "1", "2026-08-25", 1, List.of()),
                "non-HTTP URL");
        expectFailure(() -> new RotationSourceCatalog.SourceReference(
                "blank-url", "Bad", "", "fixture", "1", "2026-08-25", 1, List.of()),
                "blank URL");
        expectFailure(() -> new RotationSourceCatalog.SourceReference(
                "bad-date", "Bad", "https://example.invalid", "fixture", "1", "", 1, List.of()),
                "missing access date");
        RotationSourceCatalog rejected = new RotationSourceCatalog(
                1,
                PolicyAction.LAYOUT_REVISION,
                List.of(source),
                List.of(new SourcedRotationSeed(
                        "rejected-unconstructable",
                        "UnavailableParty",
                        "review-only-fingerprint",
                        List.of("kqm-raiden-guide"),
                        AdaptationStatus.REJECTED,
                        "Regression-only rejected candidate",
                        new int[0],
                        List.of(cycle()),
                        erTargets(),
                        List.of("single target"),
                        List.of("unsupported local party"))));
        expectFailure(() -> rejected.requireUsable("rejected-unconstructable"),
                "rejected seed exposure");
    }

    private static RotationSourceCatalog.SourceReference fixtureSource() {
        return new RotationSourceCatalog.SourceReference(
                "kqm-raiden-guide",
                "Raiden Guide",
                "https://keqingmains.com/q/raiden-quickguide/",
                "KeqingMains",
                "2025-01-30",
                "2026-08-25",
                1,
                List.of("single target"));
    }

    private static SourcedRotationSeed fixtureSeed(
            AdaptationStatus status,
            String scenarioFingerprint,
            int[] cycleActions) {
        return fixtureSeed(status, "", scenarioFingerprint, cycleActions);
    }

    private static SourcedRotationSeed fixtureSeed(
            AdaptationStatus status,
            String adaptationNote,
            String scenarioFingerprint,
            int[] cycleActions) {
        return new SourcedRotationSeed(
                "fixture-raiden-national",
                "RaidenParty",
                scenarioFingerprint,
                List.of("kqm-raiden-guide"),
                status,
                adaptationNote,
                new int[] {PolicyAction.SKILL_PRESS.getId()},
                List.of(cycleActions),
                erTargets(),
                List.of("single target"),
                List.of("fixed enemy"));
    }

    private static int[] cycle() {
        return new int[] {
                PolicyAction.SWAP_SLOT_1.getId(), PolicyAction.BURST.getId(),
                PolicyAction.SKILL_PRESS.getId(), PolicyAction.SWAP_SLOT_3.getId(),
                PolicyAction.BURST.getId(), PolicyAction.SKILL_PRESS.getId(),
                PolicyAction.SWAP_SLOT_2.getId(), PolicyAction.BURST.getId(),
                PolicyAction.SKILL_PRESS.getId(), PolicyAction.SWAP_SLOT_0.getId(),
                PolicyAction.BURST.getId(), PolicyAction.NORMAL.getId(),
                PolicyAction.CHARGE.getId()
        };
    }

    private static Map<String, Double> erTargets() {
        Map<String, Double> targets = new LinkedHashMap<>();
        targets.put("RAIDEN_SHOGUN", 2.5);
        targets.put("XINGQIU", 1.8);
        targets.put("XIANGLING", 2.0);
        targets.put("BENNETT", 2.0);
        return targets;
    }

    private static String fingerprint() {
        PartyDefinition definition = PartyCatalog.require("RaidenParty");
        return definition.loadoutFingerprint();
    }

    private static void expectFailure(Runnable action, String description) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + description);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void readUnchecked(Path path) {
        try {
            RotationSourceCatalog.read(path);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " " + description + " but was " + actual);
        }
    }
}
