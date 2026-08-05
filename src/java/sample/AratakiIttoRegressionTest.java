package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.AratakiItto;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused checks for Itto's represented Superlative offensive slice. */
public final class AratakiIttoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private AratakiIttoRegressionTest() {
    }

    /** Runs identity, combo, Ushi, Burst, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalAndKesagiriSequence();
        testUshiParticlesAndReplacement();
        testBurstConversionConstellationsAndSwitch();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("AratakiIttoRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        AratakiItto itto = itto(0, 0.75);
        assertEquals(CharacterId.ARATAKI_ITTO, itto.getCharacterId(),
                "Itto typed identity");
        assertEquals(CharacterId.ARATAKI_ITTO,
                CharacterId.fromNumericId(70), "Itto numeric identity");
        assertEquals(CharacterId.ARATAKI_ITTO,
                CharacterId.fromName("Arataki Itto"),
                "Itto display-name identity");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.ARATAKI_ITTO.getRegion(), "Itto region");
        assertEquals(Element.GEO, itto.getElement(), "Itto element");
        assertClose(12858.0,
                itto.getBaseStats().get(StatType.BASE_HP), "Itto base HP");
        assertClose(227.0,
                itto.getBaseStats().get(StatType.BASE_ATK),
                "Itto base ATK");
        assertClose(959.0,
                itto.getBaseStats().get(StatType.BASE_DEF),
                "Itto base DEF");
        assertClose(0.242,
                itto.getBaseStats().get(StatType.CRIT_RATE),
                "Itto ascension CRIT Rate");
        assertClose(70.0, itto.getEnergyCost(), "Itto Energy cost");
        assertClose(10.0, itto.getSkillCD(), "Itto Skill cooldown");
        assertClose(18.0, itto.getBurstCD(), "Itto Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    itto(constellation, 0.75).getConstellation(),
                    "Itto constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/AratakiItto/"
                        + "AratakiItto_Status.csv"), 28);
        assertCsvShape(Path.of(
                "config/characters/AratakiItto/"
                        + "AratakiItto_Multipliers.csv"), 12);
        assertThrows(IllegalArgumentException.class,
                () -> itto(-1, 0.75),
                "Itto rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> itto(7, 0.75),
                "Itto rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new AratakiItto(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Itto rejects null random source");
        CombatSimulator simulator = simulatorWith(itto);
        assertThrows(IllegalArgumentException.class,
                () -> itto.onAction(null, simulator),
                "Itto rejects null action");
    }

    private static void testNormalAndKesagiriSequence() {
        AratakiItto itto = itto(0, 0.75);
        CombatSimulator simulator = simulatorWith(itto);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            1.455654, 1.403040, 1.683648, 2.153666
        };
        for (int step = 0; step < expected.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(4, records.size(), "Itto Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Itto Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Itto uninfused Normal element " + index);
        }
        assertEquals(3, itto.getSuperlativeStackCount(),
                "N2 and N4 grant three total stacks");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        perform(simulator, CharacterActionKey.CHARGE);
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(3, records.size(), "Itto Kesagiri hit count");
        assertClose(1.674800,
                records.get(0).action.getDamagePercent(),
                "First Itto combo Slash multiplier");
        assertClose(3.507600,
                records.get(2).action.getDamagePercent(),
                "Itto final Slash multiplier");
        assertClose(959.0 * 0.35,
                records.get(0).action.getAdditiveBaseDmgBonus(),
                "A4 adds cast-time DEF to Kesagiri");
        assertEquals(0, itto.getSuperlativeStackCount(),
                "C0 Kesagiri consumes all stacks");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals("Fight Club Legend Saichimonji Slash",
                records.get(0).action.getName(),
                "Zero stacks selects Saichimonji");
        assertEquals(ActionType.CHARGE,
                records.get(0).action.getActionType(),
                "Saichimonji remains Charged damage");
        assertClose(0.0,
                records.get(0).action.getAdditiveBaseDmgBonus(),
                "A4 excludes Saichimonji");
    }

    private static void testUshiParticlesAndReplacement() {
        AratakiItto itto = itto(3, 0.25);
        CombatSimulator simulator = simulatorWith(itto);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(itto.isUshiActive(simulator.getCurrentTime()),
                "Itto Skill deploys Ushi");
        assertClose(14.0 * FRAME, itto.getLastSkillTime(),
                "Itto Skill cooldown starts at release frame");
        assertEquals(1, itto.getSuperlativeStackCount(),
                "Ushi hit grants one stack");
        assertClose(6.144000,
                named(records, "Masatsu Zetsugi: Akaushi Burst")
                        .get(0).action.getDamagePercent(),
                "C3 raises Ushi talent multiplier");
        simulator.advanceTime(2.0);
        assertEquals(1, particles.size(), "Ushi creates one particle packet");
        assertClose(4.0, particles.get(0),
                "Low particle draw selects four Geo particles");
        advanceTo(simulator, 6.0);
        assertEquals(2, itto.getSuperlativeStackCount(),
                "Ushi exit grants one stack");
        assertTrue(!itto.isUshiActive(6.0),
                "Ushi expires at the half-open boundary");

        AratakiItto replacement = itto(0, 0.75);
        CombatSimulator replacementSimulator = simulatorWith(replacement);
        perform(replacementSimulator, CharacterActionKey.SKILL);
        replacementSimulator.advanceTime(1.0);
        replacement.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                replacementSimulator);
        assertEquals(2, replacement.getSuperlativeStackCount(),
                "Replacement Ushi hit grants the second stack");
        advanceTo(replacementSimulator, 6.0);
        assertEquals(2, replacement.getSuperlativeStackCount(),
                "Replaced Ushi does not grant an obsolete exit stack");
        advanceTo(replacementSimulator, 7.7);
        assertEquals(3, replacement.getSuperlativeStackCount(),
                "Replacement Ushi grants exactly one exit stack");
    }

    private static void testBurstConversionConstellationsAndSwitch() {
        AratakiItto itto = itto(6, 0.75);
        TestCharacter geoAlly = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        TestCharacter secondGeo = new TestCharacter(
                CharacterId.ZHONGLI, Element.GEO);
        CombatSimulator simulator = simulatorWith(
                itto, geoAlly, secondGeo);
        List<ActionRecord> records = captureActions(simulator);
        itto.restoreCurrentEnergy(70.0);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(itto.isBurstActive(simulator.getCurrentTime()),
                "Itto Burst activates Raging Oni King");
        assertClose(959.0 * 1.152,
                itto.getBurstDefAtkBonus(),
                "C5 snapshots talent-twelve DEF conversion");
        assertClose(18.0, itto.getCurrentEnergy(),
                "C2 restores six Energy for each of three Geo members");
        assertClose(13.5, itto.getBurstCooldownEndTime(),
                "C2 reduces Burst cooldown by 4.5 seconds");
        assertEquals(2, itto.getSuperlativeStackCount(),
                "C1 grants two initial stacks at frame seventy-five");
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction normal = named(
                records, "Fight Club Legend N1").get(0).action;
        assertEquals(Element.GEO, normal.getElement(),
                "Burst infuses Itto Normal with Geo");
        assertClose(959.0 * 1.152,
                normal.getStatSnapshot().get(StatType.ATK_FLAT),
                "Burst attacks preserve snapshotted DEF conversion");
        assertEquals(3, itto.getSuperlativeStackCount(),
                "Burst N1 grants one additional stack");
        perform(simulator, CharacterActionKey.CHARGE);
        AttackAction charged = named(
                records, "Arataki Kesagiri Combo Slash").get(0).action;
        assertClose(0.70,
                charged.getExtraBonuses().get(StatType.CRIT_DMG),
                "C6 grants Kesagiri CRIT DMG");

        simulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!itto.isBurstActive(simulator.getCurrentTime()),
                "Switch-out ends Itto Burst");
        assertClose(0.20,
                geoAlly.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "C4 grants party ATK after Burst ends");
        assertClose(0.20,
                geoAlly.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.DEF_PERCENT),
                "C4 grants party DEF after Burst ends");
        assertClose(0.0, itto.getBurstDefAtkBonus(),
                "Burst conversion clears on switch");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        AratakiItto itto = itto(0, 0.75);
        CombatSimulator simulator = simulatorWith(itto);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 6.0);
        assertEquals(2, itto.getSuperlativeStackCount(),
                "First timeline resolves Ushi exit");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 6.0);
        assertEquals(2, itto.getSuperlativeStackCount(),
                "Restored timeline resolves Ushi exit once");

        AratakiItto foreign = itto(0, 0.75);
        assertTrue(!itto.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Itto rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> itto.restoreCharacterState(null, simulator),
                "Itto rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(itto),
                "Itto rejects cross-simulator reuse");

        AratakiItto invalidRandom = itto(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.SKILL),
                "Itto rejects out-of-range particle draw");
    }

    private static AratakiItto itto(
            int constellation,
            double draw) {
        return new AratakiItto(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> draw);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.ARATAKI_ITTO,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ARATAKI_ITTO) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureGeoParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
                records.add(count);
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (name.equals(record.action.getName())) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Arataki Itto,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
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
