package sample;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import mechanics.rl.GenericRLSimulatorFactory;
import mechanics.rl.RLPartyRegistry;
import model.entity.ArtifactSet;
import model.entity.Character;
import simulation.CombatSimulator;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/**
 * Regression checks for catalog-backed sample and RL party creation.
 */
public class PartyCatalogRegressionTest {
    public static void main(String[] args) {
        assertCatalogContains("FlinsParty2");
        assertCatalogContains("RaidenParty");
        assertCatalogContains("FlinsParty");
        assertRlRegistryContains("FlinsParty2");
        assertRlRegistryContains("RaidenParty");
        assertRlDoesNotContain("FlinsParty");
        assertDefinitionAndRlFactoryMatch("FlinsParty2");
        assertDefinitionAndRlFactoryMatch("RaidenParty");
        assertFreshRlSimulators("FlinsParty2");
        System.out.println("PartyCatalogRegressionTest passed");
    }

    private static void assertDefinitionAndRlFactoryMatch(String partyName) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        CombatSimulator sampleSim = definition.createSimulator(null, null);
        CombatSimulator rlSim = GenericRLSimulatorFactory.create(definition);

        assertEquals(sampleSim.getEnemy().getLevel(), rlSim.getEnemy().getLevel(), partyName + " enemy level");
        assertEquals(Arrays.toString(definition.partyOrder()), Arrays.toString(RLPartyRegistry.require(partyName).getPartyOrder()),
                partyName + " party order");
        assertEquals(fingerprint(sampleSim), fingerprint(rlSim), partyName + " setup fingerprint");
    }

    private static void assertFreshRlSimulators(String partyName) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        CombatSimulator first = GenericRLSimulatorFactory.create(definition);
        CombatSimulator second = GenericRLSimulatorFactory.create(definition);
        if (first == second) {
            throw new AssertionError("Expected fresh simulator instances for " + partyName);
        }
        if (first.getPartyMembers().iterator().next() == second.getPartyMembers().iterator().next()) {
            throw new AssertionError("Expected fresh character instances for " + partyName);
        }
    }

    private static String fingerprint(CombatSimulator sim) {
        return sim.getPartyMembers().stream()
                .map(PartyCatalogRegressionTest::characterFingerprint)
                .collect(Collectors.joining("|"));
    }

    private static String characterFingerprint(Character character) {
        String weapon = character.getWeapon() != null ? character.getWeapon().getName() : "-";
        String artifacts = character.getArtifacts() != null
                ? Arrays.stream(character.getArtifacts())
                        .map(PartyCatalogRegressionTest::artifactName)
                        .collect(Collectors.joining(","))
                : "-";
        return character.getCharacterId() + ":" + character.getName() + ":" + weapon + ":" + artifacts + ":"
                + character.getArtifactRolls();
    }

    private static String artifactName(ArtifactSet artifact) {
        return artifact != null ? artifact.getName() : "-";
    }

    private static void assertCatalogContains(String partyName) {
        PartyCatalog.require(partyName);
    }

    private static void assertRlRegistryContains(String partyName) {
        RLPartyRegistry.require(partyName);
    }

    private static void assertRlDoesNotContain(String partyName) {
        try {
            RLPartyRegistry.require(partyName);
            throw new AssertionError("Expected party not to be RL registered: " + partyName);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
        }
    }
}
