package sample;

import mechanics.buff.BuffId;
import model.artifact.TheExile;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for The Exile's non-stacking flat-Energy sequence. */
public class TheExileRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs metadata, timing, gating, replacement, and lifecycle checks. */
    public static void main(String[] args) {
        testMetadataAndSuppliedStats();
        testAcceptedBurstTimingAndOwnerExclusion();
        testEnergyCapAndAccounting();
        testInsufficientEnergyGate();
        testSameOwnerRefresh();
        testMultipleWearersDoNotStack();
        testLifecycleGuards();
        System.out.println("TheExileRegressionTest passed");
    }

    /** Verifies fixed stats, identity, supplied-container preservation, and null rejection. */
    private static void testMetadataAndSuppliedStats() {
        TheExile fresh = new TheExile();
        assertEquals("The Exile", fresh.getName(), "The Exile name");
        assertClose(0.20, fresh.getStats().get(StatType.ENERGY_RECHARGE),
                "The Exile Energy Recharge");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 40.0);
        TheExile preserved = new TheExile(supplied);
        assertTrue(preserved.getStats() == supplied,
                "The Exile should retain the supplied container");
        assertClose(40.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                "The Exile supplied stat preservation");

        boolean rejected = false;
        try {
            new TheExile(null);
        } catch (NullPointerException expected) {
            rejected = true;
        }
        assertTrue(rejected, "The Exile should reject null supplied stats");
    }

    /** Verifies accepted Burst dispatch and exact 2/4/6-second ticks. */
    private static void testAcceptedBurstTimingAndOwnerExclusion() {
        TheExile artifact = new TheExile();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter firstAlly = character(CharacterId.AMBER);
        TestCharacter secondAlly = character(CharacterId.LISA);
        CombatSimulator sim = simulatorWith(owner, firstAlly, secondAlly);
        zeroEnergy(owner, firstAlly, secondAlly);
        owner.restoreCurrentEnergy(owner.getMaxEnergy());

        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.0, firstAlly.getCurrentEnergy(),
                "The Exile should not tick at Burst time");
        sim.advanceTime(1.999);
        assertClose(0.0, firstAlly.getCurrentEnergy(),
                "The Exile should not tick before two seconds");
        sim.advanceTime(0.001);
        assertClose(2.0, firstAlly.getCurrentEnergy(),
                "The Exile first tick boundary");
        assertClose(2.0, secondAlly.getCurrentEnergy(),
                "The Exile should affect every ally");
        assertClose(0.0, owner.getCurrentEnergy(),
                "The Exile should exclude its owner");
        sim.advanceTime(2.0);
        assertClose(4.0, firstAlly.getCurrentEnergy(),
                "The Exile second tick boundary");
        sim.advanceTime(2.0);
        assertClose(6.0, firstAlly.getCurrentEnergy(),
                "The Exile third tick boundary");
    }

    /** Verifies flat Energy bypasses ER scaling, respects cap, and records no particles. */
    private static void testEnergyCapAndAccounting() {
        TheExile artifact = new TheExile();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        ally.restoreCurrentEnergy(ally.getMaxEnergy() - 1.0);
        artifact.onBurst(sim);
        sim.advanceTime(6.0);
        assertClose(ally.getMaxEnergy(), ally.getCurrentEnergy(),
                "The Exile should respect the Energy cap");
        assertClose(0.0, ally.getTotalParticleEnergy(),
                "The Exile should not record particle Energy");
        assertClose(6.0, ally.getTotalFlatEnergy(),
                "The Exile should record three flat Energy grants");
    }

    /** Verifies the action gateway suppresses the callback when Burst Energy is insufficient. */
    private static void testInsufficientEnergyGate() {
        TheExile artifact = new TheExile();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        zeroEnergy(owner, ally);
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        sim.advanceTime(6.0);
        assertClose(0.0, ally.getCurrentEnergy(),
                "Rejected Burst should not start The Exile");
        assertTrue(sim.getTeamBuffList().stream().noneMatch(
                buff -> buff.getId() == BuffId.THE_EXILE_4PC_SEQUENCE),
                "Rejected Burst should not create a sequence marker");
    }

    /** Verifies refreshing one wearer invalidates every old pending tick. */
    private static void testSameOwnerRefresh() {
        TheExile artifact = new TheExile();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        zeroEnergy(owner, ally);
        artifact.onBurst(sim);
        sim.advanceTime(1.0);
        artifact.onBurst(sim);
        sim.advanceTime(1.0);
        assertClose(0.0, ally.getCurrentEnergy(),
                "Refresh should invalidate the old first tick");
        sim.advanceTime(1.0);
        assertClose(2.0, ally.getCurrentEnergy(),
                "Refreshed sequence first tick");
        sim.advanceTime(4.0);
        assertClose(6.0, ally.getCurrentEnergy(),
                "Refreshed sequence should grant exactly three ticks");
    }

    /** Verifies a second wearer replaces, rather than stacks with, the active sequence. */
    private static void testMultipleWearersDoNotStack() {
        TheExile first = new TheExile();
        TheExile second = new TheExile();
        TestCharacter firstOwner = character(CharacterId.SUCROSE, first);
        TestCharacter secondOwner = character(CharacterId.AMBER, second);
        TestCharacter ally = character(CharacterId.LISA);
        CombatSimulator sim = simulatorWith(firstOwner, secondOwner, ally);
        zeroEnergy(firstOwner, secondOwner, ally);
        first.onBurst(sim);
        sim.advanceTime(1.0);
        second.onBurst(sim);
        sim.advanceTime(6.0);
        assertClose(6.0, firstOwner.getCurrentEnergy(),
                "Replacement sequence should affect the previous wearer");
        assertClose(0.0, secondOwner.getCurrentEnergy(),
                "Replacement sequence should exclude its own wearer");
        assertClose(6.0, ally.getCurrentEnergy(),
                "Multiple Exile sets should not stack");
    }

    /** Verifies unbound/wrong-simulator callbacks and cross-binding lifecycle rules. */
    private static void testLifecycleGuards() {
        TheExile unbound = new TheExile();
        CombatSimulator unrelated = new CombatSimulator();
        unbound.onBurst(unrelated);
        assertTrue(unrelated.getTeamBuffList().isEmpty(),
                "Unbound The Exile callback should be inert");

        TheExile artifact = new TheExile();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        zeroEnergy(owner, ally);
        artifact.initializeForSimulator(owner, sim, true);
        artifact.onBurst(unrelated);
        unrelated.advanceTime(6.0);
        assertClose(0.0, ally.getCurrentEnergy(),
                "Wrong-simulator callback should be inert");

        boolean nullRejected = false;
        try {
            new TheExile().initializeForSimulator(null, sim, false);
        } catch (IllegalArgumentException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "The Exile should reject a null owner");

        boolean crossBindingRejected = false;
        try {
            artifact.initializeForSimulator(owner, unrelated, true);
        } catch (IllegalStateException expected) {
            crossBindingRejected = true;
        }
        assertTrue(crossBindingRejected,
                "The Exile should reject cross-simulator reuse");
    }

    /** Creates a quiet simulator containing the supplied party in order. */
    private static CombatSimulator simulatorWith(TestCharacter... party) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        for (TestCharacter character : party) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Creates a deterministic test character with optional artifacts. */
    private static TestCharacter character(
            CharacterId characterId,
            ArtifactSet... artifacts) {
        return new TestCharacter(characterId, artifacts);
    }

    /** Resets current Energy after simulator party initialization. */
    private static void zeroEnergy(TestCharacter... characters) {
        for (TestCharacter character : characters) {
            character.restoreCurrentEnergy(0.0);
        }
    }

    /** Asserts two floating-point values are equal within test tolerance. */
    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts a boolean condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal deterministic character for Energy and Burst-gate checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, ArtifactSet... equippedArtifacts) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.ANEMO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = equippedArtifacts;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
            setBurstCD(0.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public void onAction(CharacterActionRequest request, CombatSimulator sim) {
            if (request.getKey() == CharacterActionKey.BURST) {
                markBurstUsed(sim.getCurrentTime());
            }
        }
    }
}
