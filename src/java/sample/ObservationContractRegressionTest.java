package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import mechanics.rl.CapabilityProfile;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.GenericRLSimulatorFactory;
import mechanics.rl.LoadoutFeatureEncoder;
import mechanics.rl.ObservationEncoder;
import mechanics.rl.PrivilegedStateEncoder;
import mechanics.rl.bridge.BatchProtocol;
import model.artifact.NoblesseOblige;
import model.artifact.ViridescentVenerer;
import model.character.Sucrose;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.weapon.SkywardAtlas;
import model.weapon.TheCatch;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Regression checks for loadout-aware observation and schema contracts. */
public class ObservationContractRegressionTest {
    private static final int LOADOUT_OFFSET = ObservationEncoder.CHAR_DYNAMIC_FEATURES
            + model.type.Element.values().length + CapabilityProfile.SIZE;

    public static void main(String[] args) throws Exception {
        assertSchemaDimensions();
        assertLoadoutDeltasAreIsolated();
        assertConstellationAndRefinementAreDistinct();
        assertSnapshotReproducesFeatures();
        assertMalformedInputsFailClosed();
        System.out.println("ObservationContractRegressionTest passed");
    }

    private static void assertSchemaDimensions() {
        assertEquals(12, BatchProtocol.VERSION, "batch protocol version");
        assertEquals(2, ObservationEncoder.SCHEMA_REVISION, "observation revision");
        assertEquals(2, PrivilegedStateEncoder.SCHEMA_REVISION, "privileged revision");
        assertEquals(1, LoadoutFeatureEncoder.SCHEMA_REVISION, "loadout revision");
        assertEquals(1, CapabilityProfile.SCHEMA_REVISION, "capability revision");
        assertEquals(70, ObservationEncoder.FEATURES_PER_CHARACTER, "character features");
        assertEquals(287, ObservationEncoder.OBSERVATION_SIZE, "observation size");
        assertEquals(187, PrivilegedStateEncoder.STATE_SIZE, "privileged size");
    }

    private static void assertLoadoutDeltasAreIsolated() {
        PartyDefinition definition = PartyCatalog.require("RaidenParty");
        CombatSimulator simulator = GenericRLSimulatorFactory.create(definition);
        EpisodeConfig config = new EpisodeConfig().withPartyOrder(definition.partyOrder());
        ObservationEncoder encoder = new ObservationEncoder();
        double[] baseline = encoder.encode(simulator, config, -999.0);

        int slot = 2;
        simulator.getCharacter(CharacterId.XIANGLING).setWeapon(new TheCatch(1));
        double[] refinementChanged = encoder.encode(simulator, config, -999.0);
        assertOnlyLoadoutBlockDiffers(baseline, refinementChanged, slot, "refinement");

        simulator.getCharacter(CharacterId.XIANGLING).setArtifacts(
                new NoblesseOblige(new StatsContainer()));
        double[] artifactChanged = encoder.encode(simulator, config, -999.0);
        assertOnlyLoadoutBlockDiffers(refinementChanged, artifactChanged, slot, "artifact");
    }

    private static void assertConstellationAndRefinementAreDistinct() {
        LoadoutFeatureEncoder encoder = new LoadoutFeatureEncoder();
        double[] c0 = new double[LoadoutFeatureEncoder.SIZE];
        double[] c6 = new double[LoadoutFeatureEncoder.SIZE];
        Sucrose lowConstellation = new Sucrose(
                new SkywardAtlas(1), new ViridescentVenerer(), 0, () -> 1.0);
        Sucrose highConstellation = new Sucrose(
                new SkywardAtlas(1), new ViridescentVenerer(), 6, () -> 1.0);
        encoder.fill(lowConstellation, c0, 0);
        encoder.fill(highConstellation, c6, 0);
        assertSingleDifference(c0, c6, 0, "constellation");

        double[] r1 = c0.clone();
        double[] r5 = new double[LoadoutFeatureEncoder.SIZE];
        lowConstellation.setWeapon(new SkywardAtlas(5));
        encoder.fill(lowConstellation, r5, 0);
        if (Arrays.equals(r1, r5) || r1[4] == r5[4]) {
            throw new AssertionError("Refinement-only loadouts were not distinct");
        }
    }

    private static void assertSnapshotReproducesFeatures() {
        PartyDefinition definition = PartyCatalog.require("FlinsParty2");
        CombatSimulator simulator = GenericRLSimulatorFactory.create(definition);
        EpisodeConfig config = new EpisodeConfig().withPartyOrder(definition.partyOrder());
        ObservationEncoder observationEncoder = new ObservationEncoder();
        PrivilegedStateEncoder privilegedEncoder = new PrivilegedStateEncoder();
        double[] expectedObservation = observationEncoder.encode(simulator, config, -999.0);
        double[] expectedPrivileged = privilegedEncoder.encode(simulator, config);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(0.5);
        simulator.restoreSnapshot(snapshot);
        assertArrayEquals(expectedObservation,
                observationEncoder.encode(simulator, config, -999.0), "restored observation");
        assertArrayEquals(expectedPrivileged,
                privilegedEncoder.encode(simulator, config), "restored privileged state");
    }

    private static void assertMalformedInputsFailClosed() throws IOException {
        PartyDefinition definition = PartyCatalog.require("RaidenParty");
        CombatSimulator simulator = GenericRLSimulatorFactory.create(definition);
        EpisodeConfig config = new EpisodeConfig().withPartyOrder(definition.partyOrder());
        ObservationEncoder encoder = new ObservationEncoder();
        expectFailure(
                () -> encoder.fillObservation(simulator, config, -999.0, new double[3]),
                "short observation target");

        Path incompleteProfiles = Files.createTempFile("observation-profiles", ".json");
        try {
            Files.writeString(incompleteProfiles, "{}");
            ObservationEncoder incomplete = new ObservationEncoder(
                    new ObservationEncoder.CapabilityProfileStore(incompleteProfiles.toString()));
            expectFailure(
                    () -> incomplete.encode(simulator, config, -999.0),
                    "missing capability profile");
        } finally {
            Files.deleteIfExists(incompleteProfiles);
        }

        Sucrose fixture = new Sucrose(
                new SkywardAtlas(), new ViridescentVenerer(), 0, () -> 1.0);
        fixture.setWeapon(new Weapon("fixture", new StatsContainer()));
        expectFailure(
                () -> new LoadoutFeatureEncoder().fill(
                        fixture, new double[LoadoutFeatureEncoder.SIZE], 0),
                "missing typed weapon category");

        TheCatch invalidStats = new TheCatch();
        invalidStats.getStats().set(StatType.CRIT_RATE, Double.NaN);
        fixture.setWeapon(invalidStats);
        expectFailure(
                () -> new LoadoutFeatureEncoder().fill(
                        fixture, new double[LoadoutFeatureEncoder.SIZE], 0),
                "non-finite weapon stat");
    }

    private static void assertOnlyLoadoutBlockDiffers(
            double[] before,
            double[] after,
            int slot,
            String context) {
        int start = slot * ObservationEncoder.FEATURES_PER_CHARACTER + LOADOUT_OFFSET;
        int end = start + LoadoutFeatureEncoder.SIZE;
        boolean foundDifference = false;
        for (int index = 0; index < before.length; index++) {
            if (Double.doubleToLongBits(before[index]) == Double.doubleToLongBits(after[index])) {
                continue;
            }
            if (index < start || index >= end) {
                throw new AssertionError(context + " changed feature outside loadout block at " + index);
            }
            foundDifference = true;
        }
        if (!foundDifference) {
            throw new AssertionError(context + " did not change the loadout block");
        }
    }

    private static void assertSingleDifference(
            double[] before,
            double[] after,
            int expectedIndex,
            String context) {
        for (int index = 0; index < before.length; index++) {
            boolean differs = Double.doubleToLongBits(before[index]) != Double.doubleToLongBits(after[index]);
            if (differs != (index == expectedIndex)) {
                throw new AssertionError(context + " unexpected feature delta at " + index);
            }
        }
    }

    private static void expectFailure(Runnable action, String context) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + context);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }

    private static void assertArrayEquals(double[] expected, double[] actual, String context) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(context + " mismatch");
        }
    }

    private static void assertEquals(int expected, int actual, String context) {
        if (expected != actual) {
            throw new AssertionError(
                    "Expected " + context + " to be " + expected + " but was " + actual);
        }
    }
}
