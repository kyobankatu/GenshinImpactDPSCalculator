package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import mechanics.rl.ActionSpace;
import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.GenericRLSimulatorFactory;
import mechanics.rl.RLAction;
import mechanics.rl.SinglePartyRLEpisodeFactory;
import mechanics.rl.bridge.BatchProtocol;
import mechanics.rotation.ActionCapabilityStore;
import mechanics.rotation.PolicyAction;
import simulation.CombatSimulator;
import simulation.party.PartyCatalog;
import simulation.party.PartyDefinition;

/** Regression checks for the complete versioned policy action contract. */
public class RotationActionRegressionTest {
    public static void main(String[] args) throws Exception {
        assertStableIds();
        assertCurrentPartyMasksAndActions();
        assertWaitDuration();
        assertUnavailableActionsMaskedAndRejected();
        assertCapabilityJsonValidation();
        System.out.println("RotationActionRegressionTest passed");
    }

    private static void assertStableIds() {
        PolicyAction[] expected = {
                PolicyAction.NORMAL,
                PolicyAction.CHARGE,
                PolicyAction.PLUNGE,
                PolicyAction.SKILL_PRESS,
                PolicyAction.SKILL_HOLD,
                PolicyAction.BURST,
                PolicyAction.WAIT_SHORT,
                PolicyAction.SWAP_SLOT_0,
                PolicyAction.SWAP_SLOT_1,
                PolicyAction.SWAP_SLOT_2,
                PolicyAction.SWAP_SLOT_3
        };
        assertEquals(11, PolicyAction.SIZE, "policy action size");
        assertEquals(PolicyAction.SIZE, RLAction.SIZE, "RL alias size");
        assertEquals(2, PolicyAction.LAYOUT_REVISION, "action layout revision");
        assertEquals(11, BatchProtocol.VERSION, "batch protocol version");
        for (int id = 0; id < expected.length; id++) {
            assertEquals(id, expected[id].getId(), expected[id] + " id");
            assertEquals(id, RLAction.fromId(id).getId(), "RL alias id " + id);
        }
        expectFailure(() -> PolicyAction.fromId(-1), "negative action id");
        expectFailure(() -> RLAction.fromId(PolicyAction.SIZE), "out-of-range action id");
    }

    private static void assertCurrentPartyMasksAndActions() {
        for (String partyName : new String[]{"FlinsParty2", "RaidenParty"}) {
            BattleEnvironment environment = environment(partyName, new EpisodeConfig());
            BattleEnvironment.ResetResult reset = environment.reset(false);
            assertLegal(reset.actionMask, PolicyAction.NORMAL, partyName);
            assertLegal(reset.actionMask, PolicyAction.CHARGE, partyName);
            assertLegal(reset.actionMask, PolicyAction.PLUNGE, partyName);
            assertLegal(reset.actionMask, PolicyAction.SKILL_PRESS, partyName);
            assertMasked(reset.actionMask, PolicyAction.SKILL_HOLD, partyName);
            assertLegal(reset.actionMask, PolicyAction.BURST, partyName);
            assertLegal(reset.actionMask, PolicyAction.WAIT_SHORT, partyName);
            assertMasked(reset.actionMask, PolicyAction.SWAP_SLOT_0, partyName);
            assertLegal(reset.actionMask, PolicyAction.SWAP_SLOT_1, partyName);
            assertLegal(reset.actionMask, PolicyAction.SWAP_SLOT_2, partyName);
            assertLegal(reset.actionMask, PolicyAction.SWAP_SLOT_3, partyName);

            assertDamagingAction(environment(partyName, new EpisodeConfig()), PolicyAction.CHARGE, partyName);
            assertDamagingAction(environment(partyName, new EpisodeConfig()), PolicyAction.PLUNGE, partyName);
        }
    }

    private static void assertWaitDuration() {
        EpisodeConfig config = new EpisodeConfig().withWaitActionTime(0.25);
        BattleEnvironment environment = environment("RaidenParty", config);
        environment.reset(false);
        double damageBefore = environment.getSimulator().getTotalDamage();
        mechanics.rl.ActionResult result = environment.step(PolicyAction.WAIT_SHORT.getId());
        if (!result.validAction) {
            throw new AssertionError("Wait action must be legal");
        }
        assertClose(0.25, result.timeDelta, "Wait duration");
        assertClose(damageBefore, result.totalDamage, "Wait damage");
    }

    private static void assertUnavailableActionsMaskedAndRejected() {
        BattleEnvironment environment = environment("RaidenParty", new EpisodeConfig());
        BattleEnvironment.ResetResult reset = environment.reset(false);
        assertMasked(reset.actionMask, PolicyAction.SKILL_HOLD, "unsupported Hold");
        mechanics.rl.ActionResult forced = environment.step(PolicyAction.SKILL_HOLD.getId());
        if (forced.validAction) {
            throw new AssertionError("Forced unsupported Hold action was accepted");
        }

        EpisodeConfig noEnergy = new EpisodeConfig(
                PartyCatalog.require("RaidenParty").partyOrder(),
                20.0,
                0.1,
                1.0,
                1000.0,
                0.35,
                0.10,
                0.03,
                25000.0,
                false,
                false,
                0.0);
        BattleEnvironment emptyBurst = environment("RaidenParty", noEnergy);
        emptyBurst.reset(false);
        emptyBurst.getSimulator().getActiveCharacter().spendEnergy(999.0);
        double[] emptyMask = emptyBurst.getActionSpace().createMask(
                emptyBurst.getSimulator(), emptyBurst.getLastSwapTime(), emptyBurst.getConfig());
        assertMasked(emptyMask, PolicyAction.BURST, "insufficient-energy Burst");
    }

    private static void assertCapabilityJsonValidation() throws IOException {
        Path malformed = Files.createTempFile("action-capabilities-malformed", ".json");
        Path missing = Files.createTempFile("action-capabilities-missing", ".json");
        try {
            Files.writeString(malformed,
                    "{\"revision\":1,\"characters\":{\"RAIDEN_SHOGUN\":[\"WAIT_SHORT\"]}}");
            expectFailure(() -> new ActionCapabilityStore(malformed), "non-character capability");

            Files.writeString(missing,
                    "{\"revision\":1,\"characters\":{\"BENNETT\":[\"NORMAL\"]}}");
            ActionSpace actionSpace = new ActionSpace(new ActionCapabilityStore(missing));
            CombatSimulator simulator = GenericRLSimulatorFactory.create(PartyCatalog.require("RaidenParty"));
            expectFailure(
                    () -> actionSpace.createMask(simulator, -999.0, new EpisodeConfig()),
                    "missing active-character capabilities");
        } finally {
            Files.deleteIfExists(malformed);
            Files.deleteIfExists(missing);
        }
    }

    private static BattleEnvironment environment(String partyName, EpisodeConfig baseConfig) {
        PartyDefinition definition = PartyCatalog.require(partyName);
        EpisodeConfig config = baseConfig.withPartyOrder(definition.partyOrder());
        return new BattleEnvironment(new SinglePartyRLEpisodeFactory(
                GenericRLSimulatorFactory.spec(definition), config));
    }

    private static void assertDamagingAction(
            BattleEnvironment environment,
            PolicyAction action,
            String partyName) {
        environment.reset(false);
        mechanics.rl.ActionResult result = environment.step(action.getId());
        if (!result.validAction || result.damageDelta <= 0.0) {
            throw new AssertionError(partyName + " " + action + " did not execute damage");
        }
    }

    private static void assertLegal(double[] mask, PolicyAction action, String context) {
        if (mask[action.getId()] <= 0.5) {
            throw new AssertionError(context + " expected legal " + action);
        }
    }

    private static void assertMasked(double[] mask, PolicyAction action, String context) {
        if (mask[action.getId()] > 0.5) {
            throw new AssertionError(context + " expected masked " + action);
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

    private static void assertEquals(Object expected, Object actual, String context) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + context + " to be " + expected + " but was " + actual);
        }
    }

    private static void assertClose(double expected, double actual, String context) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Expected " + context + " to be " + expected + " but was " + actual);
        }
    }
}
