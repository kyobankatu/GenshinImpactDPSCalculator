package sample;

import model.artifact.EmblemOfSeveredFate;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.EngulfingLightning;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for Engulfing Lightning's final-ER conversion. */
public class EngulfingLightningRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs metadata, conversion, Burst-window, snapshot, and lifecycle checks. */
    public static void main(String[] args) {
        testMetadataAndRefinements();
        testLateMergedEnergyRechargeConversion();
        testConversionCapAndUnrelatedStats();
        testAcceptedBurstWindowAndEmblem();
        testBurstGateRefreshAndSnapshot();
        testLifecycleAndIndependentInstances();
        System.out.println("EngulfingLightningRegressionTest passed");
    }

    /** Verifies exact Lv. 90 metadata and every R1-R5 coefficient. */
    private static void testMetadataAndRefinements() {
        assertEquals(5, new EngulfingLightning().getRefinement(),
                "Engulfing default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            EngulfingLightning weapon = new EngulfingLightning(refinement);
            assertEquals("Engulfing Lightning", weapon.getName(),
                    "Engulfing name");
            assertEquals(WeaponType.POLEARM, weapon.getWeaponType(),
                    "Engulfing weapon type");
            assertEquals(refinement, weapon.getRefinement(),
                    "Engulfing selected refinement");
            assertClose(608.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Engulfing base ATK");
            assertClose(0.551,
                    weapon.getStats().get(StatType.ENERGY_RECHARGE),
                    "Engulfing Energy Recharge");
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getStats().get(
                            StatType.ENERGY_RECHARGE_TO_ATK_PERCENT_RATIO),
                    "Engulfing conversion ratio R" + refinement);
            assertClose(0.70 + 0.10 * refinement,
                    weapon.getStats().get(
                            StatType.ENERGY_RECHARGE_TO_ATK_PERCENT_CAP),
                    "Engulfing conversion cap R" + refinement);
            assertClose(0.25 + 0.05 * refinement,
                    weapon.getBurstEnergyRecharge(),
                    "Engulfing Burst ER R" + refinement);
        }
        assertThrows(() -> new EngulfingLightning(0), "refinement zero");
        assertThrows(() -> new EngulfingLightning(6), "refinement six");
    }

    /** Verifies artifact ER merged after weapon stats contributes to conversion. */
    private static void testLateMergedEnergyRechargeConversion() {
        EngulfingLightning weapon = new EngulfingLightning(1);
        StatsContainer character = new StatsContainer();
        character.set(StatType.BASE_ATK, 100.0);
        character.set(StatType.ENERGY_RECHARGE, 1.0);
        StatsContainer artifact = new StatsContainer();
        artifact.set(StatType.ENERGY_RECHARGE, 0.20);
        StatsContainer finalView = character.merge(weapon.getStats())
                .merge(artifact);

        double expectedAtkPercent = (1.0 + 0.551 + 0.20 - 1.0) * 0.28;
        assertClose((100.0 + 608.0) * (1.0 + expectedAtkPercent),
                finalView.getTotalAtk(),
                "Engulfing late-merged ER conversion");
        assertClose(0.0, finalView.get(StatType.ATK_PERCENT),
                "Derived ER conversion should not mutate ordinary ATK percent");
    }

    /** Verifies the refinement cap and exclusion of non-converting ER. */
    private static void testConversionCapAndUnrelatedStats() {
        EngulfingLightning weapon = new EngulfingLightning(1);
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 100.0);
        stats.set(StatType.ENERGY_RECHARGE, 5.0);
        stats.set(StatType.NON_CONVERTING_ENERGY_RECHARGE, 5.0);
        stats.set(StatType.ELEMENTAL_MASTERY, 77.0);
        StatsContainer finalView = stats.merge(weapon.getStats());
        assertClose((100.0 + 608.0) * 1.80, finalView.getTotalAtk(),
                "Engulfing R1 conversion cap");
        assertClose(77.0, finalView.get(StatType.ELEMENTAL_MASTERY),
                "Engulfing unrelated stat preservation");
    }

    /** Verifies accepted Burst ER is ordinary and contributes to Emblem. */
    private static void testAcceptedBurstWindowAndEmblem() {
        EngulfingLightning weapon = new EngulfingLightning(1);
        StatsContainer artifactStats = new StatsContainer();
        TestCharacter owner = new TestCharacter(
                CharacterId.SUCROSE,
                weapon,
                new EmblemOfSeveredFate(artifactStats));
        CombatSimulator sim = simulatorWith(owner);
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));

        StatsContainer active = owner.getEffectiveStats(sim.getCurrentTime());
        assertClose(2.051, active.getTotalEnergyRecharge(),
                "Engulfing accepted Burst ER");
        assertClose(0.0,
                active.get(StatType.NON_CONVERTING_ENERGY_RECHARGE),
                "Engulfing Burst ER should remain ordinary");
        assertClose(2.051 * 0.25, active.get(StatType.BURST_DMG_BONUS),
                "Engulfing Burst ER should contribute to Emblem");
        double expectedAtkPercent = (2.051 - 1.0) * 0.28;
        assertClose((100.0 + 608.0) * (1.0 + expectedAtkPercent),
                active.getTotalAtk(),
                "Engulfing Burst ER should contribute to ATK conversion");

        sim.advanceTime(11.999);
        assertClose(2.051,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                "Engulfing pre-expiry ER");
        sim.advanceTime(0.001);
        assertClose(1.751,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                "Engulfing exact ER expiry");
    }

    /** Verifies insufficient gate, refresh, and snapshot restoration. */
    private static void testBurstGateRefreshAndSnapshot() {
        EngulfingLightning weapon = new EngulfingLightning(1);
        TestCharacter owner = new TestCharacter(
                CharacterId.SUCROSE,
                weapon,
                new ArtifactSet("Blank", new StatsContainer()));
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(1.551,
                owner.getEffectiveStats(0.0).getTotalEnergyRecharge(),
                "Rejected Burst should not grant Engulfing ER");

        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        sim.advanceTime(5.0);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.BURST), sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(11.999);
        assertClose(1.851,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                "Refreshed Engulfing ER window");
        sim.advanceTime(0.001);
        assertClose(1.551,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                "Refreshed Engulfing exact expiry");
        sim.restoreSnapshot(snapshot);
        assertClose(1.851,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                "Engulfing snapshot-restored ER");
    }

    /** Verifies binding guards, wrong callbacks, and independent weapon state. */
    private static void testLifecycleAndIndependentInstances() {
        EngulfingLightning first = new EngulfingLightning(1);
        TestCharacter owner = new TestCharacter(
                CharacterId.SUCROSE,
                first,
                new ArtifactSet("Blank", new StatsContainer()));
        CombatSimulator sim = simulatorWith(owner);
        first.initializeForSimulator(owner, sim);
        CombatSimulator unrelated = new CombatSimulator();
        first.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.BURST), unrelated);
        assertClose(1.551,
                owner.getEffectiveStats(0.0).getTotalEnergyRecharge(),
                "Wrong-simulator Engulfing callback");

        boolean rejected = false;
        try {
            first.initializeForSimulator(owner, unrelated);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected, "Engulfing should reject cross-binding");

        EngulfingLightning independent = new EngulfingLightning(5);
        assertClose(0.551,
                independent.getStats().get(StatType.ENERGY_RECHARGE),
                "Engulfing instances should remain independent");
    }

    /** Creates a quiet simulator containing one owner. */
    private static CombatSimulator simulatorWith(TestCharacter owner) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.addCharacter(owner);
        return sim;
    }

    /** Asserts an invalid refinement is rejected. */
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    /** Asserts numeric equality within tolerance. */
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

    /** Asserts a condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal Burst-capable polearm owner. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                EngulfingLightning weapon,
                ArtifactSet artifact) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.ELECTRO;
            this.weapon = weapon;
            artifacts = new ArtifactSet[] { artifact };
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
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
