package sample;

import java.util.EnumSet;

import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.weapon.ExaiphanesBlade;
import model.weapon.Frostbreath;
import model.weapon.HereticsMoltenBlade;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for source-sensitive Version 7.0 weapons. */
public final class VersionSevenSpecialWeaponRegressionTest {
    private static final double EPS = 1e-6;

    private VersionSevenSpecialWeaponRegressionTest() {
    }

    /** Runs all special Version 7.0 weapon regression cases. */
    public static void main(String[] args) {
        testFrostbreathTypedTriggerAndCooldown();
        testFrostbreathSnapshot();
        testExaiphanesTravelerContractAndHitWindow();
        testExaiphanesRefinementAndResonanceHistory();
        testHereticMovementScalingAndSwitchRemoval();
        testHereticMovementValidationAndSnapshot();
        testInvalidRefinements();
        System.out.println("VersionSevenSpecialWeaponRegressionTest passed");
    }

    private static void testFrostbreathTypedTriggerAndCooldown() {
        Frostbreath weapon = new Frostbreath(1);
        TestCharacter owner = character(CharacterId.ALYOSHA, Element.ELECTRO, weapon);
        TestCharacter ally = character(CharacterId.ODETTE, Element.CRYO, null);
        CombatSimulator sim = simulator(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        ally.restoreCurrentEnergy(0.0);

        sim.notifyReaction(
                ReactionResult.amp(2.0, "Melt", ReactionResult.Kind.MELT),
                owner);
        assertEquals(0.20, passive(weapon, 0.0).get(StatType.ATK_PERCENT), EPS,
                "Frostbreath R1 owner ATK");
        assertEquals(6.0, ally.getCurrentEnergy(), EPS,
                "Frostbreath restores non-owner Energy");
        assertEquals(0.0, owner.getCurrentEnergy(), EPS,
                "Frostbreath excludes its owner from Energy recovery");

        sim.advanceTime(15.0);
        assertEquals(0.0, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "Frostbreath ATK window expires before cooldown");
        sim.notifyReaction(
                ReactionResult.state("Quicken", ReactionResult.Kind.QUICKEN, null),
                owner);
        assertEquals(6.0, ally.getCurrentEnergy(), EPS,
                "unrelated reaction does not consume or activate Frostbreath");
        sim.advanceTime(1.0);
        sim.notifyReaction(
                ReactionResult.transform(
                        0.0, "Swirl-Cryo", ReactionResult.Kind.SWIRL,
                        Element.CRYO, Element.CRYO),
                owner);
        assertEquals(12.0, ally.getCurrentEnergy(), EPS,
                "Frostbreath accepts typed Cryo Swirl at cooldown boundary");
    }

    private static void testFrostbreathSnapshot() {
        Frostbreath weapon = new Frostbreath(5);
        TestCharacter owner = character(CharacterId.ALYOSHA, Element.ELECTRO, weapon);
        TestCharacter ally = character(CharacterId.ODETTE, Element.CRYO, null);
        CombatSimulator sim = simulator(owner, ally);
        sim.notifyReaction(
                ReactionResult.state("Frozen", ReactionResult.Kind.FROZEN, null),
                owner);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(15.0);
        sim.restoreSnapshot(snapshot);
        assertEquals(0.40, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "snapshot restores Frostbreath ATK window");
    }

    private static void testExaiphanesTravelerContractAndHitWindow() {
        ExaiphanesBlade weapon = new ExaiphanesBlade(1);
        TestCharacter traveler = character(
                CharacterId.TRAVELER, Element.CRYO, weapon);
        CombatSimulator sim = simulator(traveler);
        traveler.restoreCurrentEnergy(0.0);

        sim.performActionWithoutTimeAdvance(
                CharacterId.TRAVELER, positiveHit("Traveler hit"));
        assertEquals(0.16, passive(weapon, 0.0).get(StatType.ATK_PERCENT), EPS,
                "Exaiphanes official R1 hit-window ATK");
        assertEquals(3.0, traveler.getCurrentEnergy(), EPS,
                "Exaiphanes R1 Energy recovery");
        sim.performActionWithoutTimeAdvance(
                CharacterId.TRAVELER, positiveHit("Traveler second hit"));
        assertEquals(3.0, traveler.getCurrentEnergy(), EPS,
                "Exaiphanes Energy observes five-second cooldown");
        sim.advanceTime(8.0);
        assertEquals(0.0, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "Exaiphanes ATK window expires at eight seconds");

        boolean rejected = false;
        try {
            simulator(character(CharacterId.ODETTE, Element.CRYO,
                    new ExaiphanesBlade(1)));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, "Exaiphanes rejects non-Traveler owners");
    }

    private static void testExaiphanesRefinementAndResonanceHistory() {
        ExaiphanesBlade r2 = new ExaiphanesBlade(
                2,
                EnumSet.of(Element.ANEMO, Element.GEO, Element.CRYO));
        assertEquals(0.18, passive(r2, 0.0).get(StatType.CRIT_DMG), EPS,
                "R2+ Exaiphanes grants 6 percent CRIT DMG per resonated element");
        ExaiphanesBlade r1 = new ExaiphanesBlade(
                1,
                EnumSet.of(Element.ANEMO, Element.GEO, Element.CRYO));
        assertEquals(0.0, passive(r1, 0.0).get(StatType.CRIT_DMG), EPS,
                "R1 Exaiphanes excludes resonance-history CRIT DMG");
    }

    private static void testHereticMovementScalingAndSwitchRemoval() {
        HereticsMoltenBlade weapon = new HereticsMoltenBlade(1);
        TestCharacter owner = character(CharacterId.ODETTE, Element.CRYO, weapon);
        TestCharacter ally = character(CharacterId.ALYOSHA, Element.ELECTRO, null);
        CombatSimulator sim = simulator(owner, ally);
        sim.recordMovement(CharacterId.ODETTE, 9.0);
        sim.performAction(
                CharacterId.ODETTE,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(0.27, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "Heretic linearly interpolates halfway movement");
        sim.switchCharacter(CharacterId.ALYOSHA);
        assertEquals(0.0, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "Heretic effect is removed on switch-out");
    }

    private static void testHereticMovementValidationAndSnapshot() {
        HereticsMoltenBlade weapon = new HereticsMoltenBlade(5);
        TestCharacter owner = character(CharacterId.ODETTE, Element.CRYO, weapon);
        CombatSimulator sim = simulator(owner);
        sim.recordMovement(CharacterId.ODETTE, 30.0);
        sim.performAction(
                CharacterId.ODETTE,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(0.72, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "Heretic caps movement at eighteen meters");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(14.0);
        sim.restoreSnapshot(snapshot);
        assertEquals(0.72, passive(weapon, sim.getCurrentTime())
                .get(StatType.ATK_PERCENT), EPS,
                "snapshot restores Heretic sampled bonus");

        boolean rejected = false;
        try {
            sim.recordMovement(CharacterId.ODETTE, -1.0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, "negative movement distance is rejected");
    }

    private static void testInvalidRefinements() {
        assertInvalidRefinement(() -> new Frostbreath(0), "Frostbreath");
        assertInvalidRefinement(() -> new ExaiphanesBlade(6), "Exaiphanes");
        assertInvalidRefinement(() -> new HereticsMoltenBlade(0), "Heretic");
    }

    private static AttackAction positiveHit(String name) {
        return new AttackAction(
                name, 1.0, Element.CRYO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, ActionType.SKILL);
    }

    private static StatsContainer passive(
            model.entity.Weapon weapon, double currentTime) {
        StatsContainer stats = new StatsContainer();
        weapon.applyPassive(stats, currentTime);
        return stats;
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            model.entity.Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulator(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static void assertInvalidRefinement(Runnable constructor, String label) {
        boolean rejected = false;
        try {
            constructor.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, label + " invalid refinement is rejected");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Minimal weapon owner fixture. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement,
                model.entity.Weapon equippedWeapon) {
            characterId = id;
            name = id.getDisplayName();
            element = characterElement;
            weapon = equippedWeapon;
            artifacts = new ArtifactSet[0];
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
