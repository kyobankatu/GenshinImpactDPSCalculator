package sample;

import java.util.List;

import mechanics.buff.Buff;
import mechanics.formula.DamageCalculator;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.StellarReactionProvider;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.runtime.StellarReactionManager;

/** Regression checks for Stellar conversion, formula, field, and snapshot contracts. */
public final class StellarReactionRegressionTest {
    private static final double EPS = 1e-6;

    private StellarReactionRegressionTest() {
    }

    /** Runs all Stellar reaction regression cases. */
    public static void main(String[] args) {
        testStandardSuperconductWithoutProvider();
        testStellarConductConversionAndField();
        testStellarSwirlConversionAndRadiance();
        testPolestarApplicationWindowAndCap();
        testPolestarSnapshotRestore();
        testDirectStellarFormulaExcludesOrdinaryElementBonus();
        System.out.println("StellarReactionRegressionTest passed");
    }

    private static void testStandardSuperconductWithoutProvider() {
        CombatSimulator sim = createSimulator(new TestCharacter(
                CharacterId.ALYOSHA, Element.CRYO, false, false));
        sim.getEnemy().setAura(Element.ELECTRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.ALYOSHA,
                elementalAction("Cryo application", Element.CRYO));

        assertFalse(sim.getStellarReactionManager().isFieldActive(0.0),
                "Superconduct remains standard without a provider");
        assertTrue(sim.getTotalDamage() > 0.0,
                "standard Superconduct still deals transformative damage");
    }

    private static void testStellarConductConversionAndField() {
        CombatSimulator sim = createSimulator(new TestCharacter(
                CharacterId.ALYOSHA, Element.CRYO, true, false));
        sim.getEnemy().setAura(Element.ELECTRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.ALYOSHA,
                elementalAction("Cryo application", Element.CRYO));

        StellarReactionManager manager = sim.getStellarReactionManager();
        assertTrue(manager.isFieldActive(0.0),
                "Stellar-Conduct creates Polestar Field");
        assertEquals(1, manager.getRecordedApplications(0.0),
                "triggering Cryo application is recorded after field creation");
        assertEquals(0.0, sim.getTotalDamage(), EPS,
                "Stellar-Conduct creation itself deals no damage");

        StatsContainer fieldStats = applyBuffs(sim.getApplicableBuffs(
                sim.getCharacter(CharacterId.ALYOSHA)), 0.0);
        assertEquals(0.20, fieldStats.get(StatType.CRYO_DMG_BONUS), EPS,
                "zero-stack field grants baseline Cryo bonus");
        assertEquals(0.40, fieldStats.get(StatType.PHYS_RES_SHRED), EPS,
                "field grants Physical RES shred");

        sim.advanceTime(6.0);
        assertFalse(manager.isFieldActive(sim.getCurrentTime()),
                "Polestar Field expires at six seconds");
    }

    private static void testStellarSwirlConversionAndRadiance() {
        CombatSimulator sim = createSimulator(new TestCharacter(
                CharacterId.ODETTE, Element.ANEMO, false, true));
        sim.getEnemy().setAura(Element.CRYO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.ODETTE,
                elementalAction("Anemo application", Element.ANEMO));

        assertTrue(sim.getStellarReactionManager().hasStellarSwirlRadiance(0.0),
                "Stellar-Swirl refreshes its eight-second Radiance");
        assertTrue(sim.getTotalDamage() > 0.0,
                "generic Stellar-Swirl deals crit-capable reaction damage");
        sim.advanceTime(8.0);
        assertFalse(sim.getStellarReactionManager()
                        .hasStellarSwirlRadiance(sim.getCurrentTime()),
                "Stellar-Swirl Radiance expires at eight seconds");
    }

    private static void testPolestarApplicationWindowAndCap() {
        StellarReactionManager manager = new StellarReactionManager();
        manager.triggerStellarConduct(0.0);
        manager.recordElementApplication(CharacterId.ALYOSHA, Element.CRYO, 0.0);
        manager.recordElementApplication(CharacterId.ALYOSHA, Element.ELECTRO, 0.05);
        assertEquals(1, manager.getRecordedApplications(0.05),
                "per-character record ICD blocks sub-0.1-second applications");

        CharacterId[] sources = CharacterId.values();
        for (int i = 0; i < sources.length; i++) {
            manager.recordElementApplication(sources[i], Element.CRYO, 0.2);
        }
        assertEquals(12, manager.getRecordedApplications(0.2),
                "recorded applications cap at twelve");
        assertEquals(12, manager.getCurrentApplications(4.0),
                "recorded applications release on the four-second boundary");
    }

    private static void testPolestarSnapshotRestore() {
        CombatSimulator sim = createSimulator(new TestCharacter(
                CharacterId.ALYOSHA, Element.CRYO, true, false));
        StellarReactionManager manager = sim.getStellarReactionManager();
        manager.triggerStellarConduct(0.0);
        manager.recordElementApplication(CharacterId.ALYOSHA, Element.CRYO, 0.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(4.0);
        assertEquals(1, manager.getCurrentApplications(sim.getCurrentTime()),
                "window advances before restore");
        sim.restoreSnapshot(snapshot);
        assertEquals(0, manager.getCurrentApplications(sim.getCurrentTime()),
                "snapshot restores unreleased window state");
        assertEquals(1, manager.getRecordedApplications(sim.getCurrentTime()),
                "snapshot restores recorded applications");
    }

    private static void testDirectStellarFormulaExcludesOrdinaryElementBonus() {
        TestCharacter baseline = new TestCharacter(
                CharacterId.ODETTE, Element.CRYO, false, true);
        TestCharacter elementalBonus = new TestCharacter(
                CharacterId.ODETTE, Element.CRYO, false, true);
        elementalBonus.withStat(StatType.CRYO_DMG_BONUS, 1.0);

        double baselineDamage = calculateDirectStellarDamage(baseline);
        double elementalBonusDamage = calculateDirectStellarDamage(elementalBonus);
        assertEquals(baselineDamage, elementalBonusDamage, EPS,
                "direct Stellar formula excludes ordinary Cryo DMG Bonus");
    }

    private static double calculateDirectStellarDamage(TestCharacter character) {
        Enemy enemy = new Enemy(90);
        AttackAction action = new AttackAction(
                "Direct Stellar-Swirl",
                1.0,
                Element.CRYO,
                StatType.BASE_ATK);
        action.setStellarReactionType(AttackAction.StellarReactionType.SWIRL);
        return DamageCalculator.calculateDamage(
                character, enemy, action, List.of(), 0.0, 1.0, null);
    }

    private static CombatSimulator createSimulator(TestCharacter character) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(character);
        return sim;
    }

    private static AttackAction elementalAction(String name, Element element) {
        AttackAction action = new AttackAction(
                name, 0.0, element, StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        action.setHitEffectTrigger(false);
        return action;
    }

    private static StatsContainer applyBuffs(List<Buff> buffs, double currentTime) {
        StatsContainer stats = new StatsContainer();
        for (Buff buff : buffs) {
            buff.apply(stats, currentTime);
        }
        return stats;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Minimal character fixture exposing Stellar conversion capabilities. */
    private static final class TestCharacter extends Character
            implements StellarReactionProvider {
        private final boolean conduct;
        private final boolean swirl;

        private TestCharacter(
                CharacterId characterId,
                Element element,
                boolean conduct,
                boolean swirl) {
            this.characterId = characterId;
            this.name = characterId.getDisplayName();
            this.element = element;
            this.conduct = conduct;
            this.swirl = swirl;
            this.weapon = new Weapon("Test Weapon", new StatsContainer());
            this.artifacts = new ArtifactSet[0];
            this.baseStats.set(StatType.BASE_HP, 10000.0);
            this.baseStats.set(StatType.BASE_ATK, 1000.0);
            this.baseStats.set(StatType.BASE_DEF, 700.0);
            this.baseStats.set(StatType.CRIT_RATE, 0.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public boolean enablesStellarConduct() {
            return conduct;
        }

        @Override
        public boolean enablesStellarSwirl() {
            return swirl;
        }

        private TestCharacter withStat(StatType statType, double value) {
            baseStats.set(statType, value);
            return this;
        }
    }
}
