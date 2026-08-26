package sample;

import java.util.List;

import mechanics.rl.EpisodeConfig;
import mechanics.rotation.BattleRotationEnvironment;
import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSnapshotSafety;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Verifies fail-closed admission for snapshot-backed rotation search. */
public class RotationSnapshotSafetyRegressionTest {
    private static final List<String> REJECTED_AUDIT_CANDIDATES = List.of(
            "HuTaoXianyunVaporize",
            "XiaoFurina",
            "XiaoLanYan",
            "NaviaDoubleHydro",
            "AlhaithamYelanQuickbloom",
            "AyatoXilonenMonoHydro");

    public static void main(String[] args) {
        for (String partyName : REJECTED_AUDIT_CANDIDATES) {
            assertRejected(PartyCatalog.require(partyName));
        }
        assertRejected(PartyCatalog.require("RaidenParty"));
        assertSearchRejected("HuTaoXianyunVaporize", true);
        assertSearchRejected("RaidenParty", false);
        System.out.println("RotationSnapshotSafetyRegressionTest passed");
    }

    private static void assertRejected(PartyDefinition definition) {
        RotationSnapshotSafety.Assessment assessment = RotationSnapshotSafety.assess(definition);
        if (assessment.admitted) {
            throw new AssertionError("Unaudited party was admitted: " + definition.name());
        }
        if (!assessment.loadoutFingerprint.equals(definition.loadoutFingerprint())) {
            throw new AssertionError("Snapshot assessment changed the loadout fingerprint");
        }
    }

    private static void assertSearchRejected(String partyName, boolean evolutionary) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        RotationScenario scenario = RotationScenario.forParty(
                definition,
                new EpisodeConfig(),
                definition.rotationCycleSeconds(),
                1,
                7788L,
                RotationObjective.cyclicDamage());
        Runnable search = evolutionary
                ? () -> new EvolutionaryRotationSearcher().search(
                        () -> new BattleRotationEnvironment(scenario),
                        RotationSearchConfig.defaults(7788L, 64))
                : () -> new MctsRotationSearcher().search(
                        () -> new BattleRotationEnvironment(scenario),
                        RotationSearchConfig.defaults(7788L, 64));
        expectFailure(search, partyName + " search");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            if (!expected.getMessage().contains("snapshot admission rejected")) {
                throw new AssertionError("Unexpected rejection for " + message, expected);
            }
        }
    }
}
