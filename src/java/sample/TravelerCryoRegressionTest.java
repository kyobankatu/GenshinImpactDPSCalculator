package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.TravelerCryo;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Cryo Traveler's Version 7.0 slice. */
public final class TravelerCryoRegressionTest {
    private static final double EPSILON = 1e-8;

    private TravelerCryoRegressionTest() {
    }

    /** Runs identity, action, Stellar, constellation, and snapshot checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndValidation();
        testSwordMultipliersAndGender();
        testStarFrostglowAndSnapshot();
        testRadianceBurstIcepointAndConstellations();
        System.out.println("TravelerCryoRegressionTest passed");
    }

    private static void testIdentityDataAndValidation()
            throws IOException {
        TravelerCryo traveler = new TravelerCryo(
                null, null, TravelerCryo.Gender.FEMALE, 6);
        assertEquals(CharacterId.TRAVELER, traveler.getCharacterId(),
                "Cryo Traveler canonical identity");
        assertEquals(Element.CRYO, traveler.getElement(),
                "Cryo Traveler element");
        assertClose(10874.9149,
                traveler.getBaseStats().get(StatType.BASE_HP),
                "Cryo Traveler base HP");
        assertClose(212.3972,
                traveler.getBaseStats().get(StatType.BASE_ATK),
                "Cryo Traveler base ATK");
        assertClose(682.5215,
                traveler.getBaseStats().get(StatType.BASE_DEF),
                "Cryo Traveler base DEF");
        assertClose(0.24,
                traveler.getBaseStats().get(StatType.ATK_PERCENT),
                "Cryo Traveler ascension ATK");
        assertClose(60.0, traveler.getEnergyCost(),
                "Cryo Traveler Burst cost");
        assertTrue(traveler.enablesStellarConduct(),
                "Cryo Traveler enables Stellar-Conduct");
        assertTrue(traveler.enablesStellarSwirl(),
                "Cryo Traveler enables Stellar-Swirl");
        assertTrue(!traveler.isAutomaticTimingRepresented(),
                "unsourced automatic timing fails closed");
        assertThrows(IllegalArgumentException.class,
                () -> new TravelerCryo(null, null, null, 0),
                "null gender is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new TravelerCryo(
                        null, null, TravelerCryo.Gender.MALE, 7),
                "invalid constellation is rejected");
        assertCsvShape(Path.of(
                "config/characters/TravelerCryo/TravelerCryo_Status.csv"),
                7);
        assertCsvShape(Path.of(
                "config/characters/TravelerCryo/TravelerCryo_Multipliers.csv"),
                29);
    }

    private static void testSwordMultipliersAndGender() {
        TravelerCryo female = new TravelerCryo(
                null, null, TravelerCryo.Gender.FEMALE, 0);
        CombatSimulator femaleSimulator = simulatorWith(female);
        List<AttackAction> femaleActions = captureActions(femaleSimulator);
        for (int step = 0; step < 5; step++) {
            perform(femaleSimulator, CharacterActionKey.NORMAL);
        }
        double[] expected = {
            0.816860, 0.797900, 0.973280, 1.071240, 1.300340
        };
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    femaleActions.get(index).getDamagePercent(),
                    "Cryo Traveler Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    femaleActions.get(index).getElement(),
                    "uninfused Traveler Normal element");
        }
        perform(femaleSimulator, CharacterActionKey.CHARGE);
        assertClose(1.027,
                femaleActions.get(5).getDamagePercent(),
                "female Charged hit one");
        assertClose(1.3272,
                femaleActions.get(6).getDamagePercent(),
                "female Charged hit two");

        TravelerCryo male = new TravelerCryo(
                null, null, TravelerCryo.Gender.MALE, 0);
        CombatSimulator maleSimulator = simulatorWith(male);
        List<AttackAction> maleActions = captureActions(maleSimulator);
        perform(maleSimulator, CharacterActionKey.CHARGE);
        assertClose(1.11548,
                maleActions.get(1).getDamagePercent(),
                "male Charged hit two");
    }

    private static void testStarFrostglowAndSnapshot() {
        TravelerCryo traveler = new TravelerCryo(
                null, null, TravelerCryo.Gender.FEMALE, 4);
        CombatSimulator simulator = simulatorWith(traveler);
        assertThrows(IllegalStateException.class,
                () -> traveler.fireIceCrystal(simulator),
                "ice crystal requires an active Star");
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(traveler.isFrostpierceStarActive(14.999),
                "C4 extends Star duration by 25 percent");
        traveler.fireIceCrystal(simulator);
        assertEquals(1, traveler.getFrostglowStacks(),
                "accepted ice crystal grants Frostglow");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.getStellarReactionManager().triggerStellarSwirl(0.0);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(0, traveler.getFrostglowStacks(),
                "Burst consumes Frostglow");
        simulator.restoreSnapshot(snapshot);
        assertEquals(1, traveler.getFrostglowStacks(),
                "snapshot restores Frostglow");
        assertTrue(traveler.isFrostpierceStarActive(14.999),
                "snapshot restores Star window");
    }

    private static void testRadianceBurstIcepointAndConstellations() {
        TravelerCryo traveler = new TravelerCryo(
                null, null, TravelerCryo.Gender.FEMALE, 6);
        TestCharacter ally = new TestCharacter(CharacterId.BENNETT);
        CombatSimulator simulator = simulatorWith(traveler, ally);
        List<AttackAction> actions = captureActions(simulator);
        simulator.getStellarReactionManager().triggerStellarConduct(0.0);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction infused = actions.get(actions.size() - 1);
        assertEquals(Element.CRYO, infused.getElement(),
                "A1 infuses Normal during Conduct Star");
        assertClose(1.616860, infused.getDamagePercent(),
                "A1 adds 80 percent ATK to Normal");

        for (int index = 0; index < 8; index++) {
            traveler.fireIceCrystal(simulator);
        }
        double structuralEm = traveler.getEffectiveStats(0.0)
                .get(StatType.ELEMENTAL_MASTERY);
        double c2Em = effectiveStats(simulator, traveler)
                .get(StatType.ELEMENTAL_MASTERY);
        assertClose(60.0, c2Em - structuralEm,
                "C2 ice crystal grants active character EM");
        perform(simulator, CharacterActionKey.BURST);
        List<AttackAction> javelins = named(actions, "Frostbound Javelin");
        assertEquals(5, javelins.size(),
                "eight Frostglow adds two Burst strikes");
        assertClose(0.624818 + 8.0 * 0.031241,
                javelins.get(0).getDamagePercent(),
                "Conduct Burst consumes Frostglow multiplier");
        assertEquals(AttackAction.StellarReactionType.CONDUCT,
                javelins.get(0).getStellarReactionType(),
                "Conduct has priority over Swirl radiance");
        assertClose(5.0, traveler.getCurrentEnergy(),
                "C1 restores Energy on first Stellar Burst hit");
        assertClose(0.40,
                effectiveStats(simulator, ally).get(
                        StatType.STELLAR_CONDUCT_DMG_BONUS),
                "C6 grants other members Stellar damage");

        while (traveler.getIcepointStacks() < 3) {
            simulator.advanceTime(2.0);
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.TRAVELER, stellarProbe());
        }
        int beforeCharge = actions.size();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(0, traveler.getIcepointStacks(),
                "Freezing Ice consumes three Icepoint");
        assertEquals(2, traveler.getFrostglowStacks(),
                "Freezing Ice grants two Frostglow");
        assertClose(1.027 + 1.40,
                actions.get(beforeCharge).getDamagePercent(),
                "Freezing Ice first hit multiplier");
        assertEquals(AttackAction.StellarReactionType.CONDUCT,
                actions.get(beforeCharge).getStellarReactionType(),
                "Freezing Ice follows current Radiance");
    }

    private static AttackAction stellarProbe() {
        AttackAction action = new AttackAction(
                "Traveler Stellar Probe",
                0.1,
                Element.CRYO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
        action.setStellarReactionType(
                AttackAction.StellarReactionType.CONDUCT);
        return action;
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static List<AttackAction> captureActions(
            CombatSimulator simulator) {
        List<AttackAction> actions = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.TRAVELER) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static List<AttackAction> named(
            List<AttackAction> actions,
            String prefix) {
        List<AttackAction> selected = new ArrayList<>();
        for (AttackAction action : actions) {
            if (action.getName().startsWith(prefix)) {
                selected.add(action);
            }
        }
        return selected;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.TRAVELER, CharacterActionRequest.of(key));
    }

    private static StatsContainer effectiveStats(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("TravelerCryo,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but caught "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Minimal distinct party member used for C6 support checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.PYRO;
            artifacts = new model.entity.ArtifactSet[0];
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
