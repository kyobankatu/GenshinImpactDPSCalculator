package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Kirara;
import model.entity.Character;
import model.entity.Enemy;
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
import simulation.action.SkillActionMode;

/** Focused checks for Kirara's stationary Cardamom offensive slice. */
public final class KiraraRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KiraraRegressionTest() {
    }

    /** Runs identity, basics, Skill, Burst, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testNormalAndChargedBasics();
        testSkillParticlesA4AndC6();
        testBurstCardamomsAndTalentConstellations();
        testC4NominalShieldGate();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("KiraraRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Kirara kirara = kirara(0);
        assertEquals(CharacterId.KIRARA, kirara.getCharacterId(),
                "Kirara typed identity");
        assertEquals(CharacterId.KIRARA, CharacterId.fromNumericId(72),
                "Kirara numeric identity");
        assertEquals(CharacterId.KIRARA, CharacterId.fromName("Kirara"),
                "Kirara display-name identity");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KIRARA.getRegion(), "Kirara region");
        assertEquals(Element.DENDRO, kirara.getElement(), "Kirara element");
        assertClose(12180.0,
                kirara.getBaseStats().get(StatType.BASE_HP),
                "Kirara base HP");
        assertClose(223.0,
                kirara.getBaseStats().get(StatType.BASE_ATK),
                "Kirara base ATK");
        assertClose(546.0,
                kirara.getBaseStats().get(StatType.BASE_DEF),
                "Kirara base DEF");
        assertClose(0.24,
                kirara.getBaseStats().get(StatType.HP_PERCENT),
                "Kirara ascension HP");
        assertClose(60.0, kirara.getEnergyCost(), "Kirara Energy cost");
        assertClose(8.0, kirara.getSkillCD(), "Kirara Skill cooldown");
        assertClose(15.0, kirara.getBurstCD(), "Kirara Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation, kirara(constellation).getConstellation(),
                    "Kirara constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Kirara/Kirara_Status.csv"), 28);
        assertCsvShape(Path.of(
                "config/characters/Kirara/Kirara_Multipliers.csv"), 14);
        assertThrows(IllegalArgumentException.class,
                () -> kirara(-1),
                "Kirara rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> kirara(7),
                "Kirara rejects constellation above C6");

        CombatSimulator simulator = simulatorWith(kirara);
        assertThrows(IllegalArgumentException.class,
                () -> kirara.onAction(null, simulator),
                "Kirara rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.KIRARA,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Kirara rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Kirara rejects unsupported Plunge");
    }

    private static void testNormalAndChargedBasics() {
        Kirara kirara = kirara(0);
        CombatSimulator simulator = simulatorWith(kirara);
        List<ActionRecord> records = captureActions(simulator);
        double[] expectedNormals = {
            0.880060, 0.851620, 0.467048, 0.700572, 1.346160
        };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(expectedNormals.length, records.size(),
                "Kirara Normal hit count");
        for (int hit = 0; hit < expectedNormals.length; hit++) {
            assertClose(expectedNormals[hit],
                    records.get(hit).action.getDamagePercent(),
                    "Kirara Normal multiplier " + hit);
            assertEquals(Element.PHYSICAL,
                    records.get(hit).action.getElement(),
                    "Kirara Normal element " + hit);
        }

        records.clear();
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(3, records.size(), "Kirara Charged hit count");
        assertClose(castTime + 20.0 * FRAME, records.get(0).time,
                "Kirara Charged first hit timing");
        assertClose(0.411116, records.get(0).action.getDamagePercent(),
                "Kirara Charged first multiplier");
        assertClose(0.822232, records.get(2).action.getDamagePercent(),
                "Kirara Charged third multiplier");
        assertEquals(ActionType.CHARGE,
                records.get(2).action.getActionType(),
                "Kirara Charged action type");
    }

    private static void testSkillParticlesA4AndC6() {
        Kirara c0 = kirara(0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureDendroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(records,
                "Tail-Flicking Flying Kick").get(0);
        assertClose(14.0 * FRAME, skill.time,
                "Kirara Skill hit timing");
        assertClose(1.768000, skill.action.getDamagePercent(),
                "Kirara C0 Skill multiplier");
        assertClose(12180.0 * 1.24 * 0.000004,
                skill.action.getExtraBonuses().get(StatType.DMG_BONUS_ALL),
                "Kirara A4 Skill bonus");
        assertEquals(1, particles.size(),
                "Kirara Skill emits one particle packet");
        assertClose(3.0, particles.get(0),
                "Kirara Skill emits three fixed particles");
        assertClose(8.0 + 12.0 * FRAME,
                c0.getSkillCooldownEndTime(),
                "Kirara Skill cooldown starts at frame twelve");
        assertTrue(c0.isNominalShieldActive(simulator.getCurrentTime()),
                "Kirara Skill opens nominal shield window");

        Kirara c6 = kirara(6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        AttackAction normal = startingWith(c6Records, "Boxcutter N")
                .get(0).action;
        assertClose(0.12,
                normal.getStatSnapshot().get(StatType.PHYSICAL_DMG_BONUS),
                "Kirara C6 grants team Physical bonus in pinned source");
    }

    private static void testBurstCardamomsAndTalentConstellations() {
        Kirara c0 = kirara(0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        c0.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Kirara Burst spends Energy at frame seven");
        assertClose(15.0, c0.getBurstCooldownEndTime(),
                "Kirara Burst cooldown starts on cast");
        assertClose(9.694080,
                named(records, "Secret Art: Surprise Dispatch")
                        .get(0).action.getDamagePercent(),
                "Kirara C0 Burst multiplier");
        assertEquals(6, c0.getActiveCardamomCount(),
                "Kirara C0 creates six Cardamoms");
        advanceTo(simulator, 180.0 * FRAME);
        List<ActionRecord> mines = named(records,
                "Cat Grass Cardamom Explosion");
        assertEquals(2, mines.size(),
                "Kirara default early Cardamom hits");
        assertClose(180.0 * FRAME, mines.get(0).time,
                "Kirara early Cardamom timing");
        advanceTo(simulator, 855.0 * FRAME);
        mines = named(records, "Cat Grass Cardamom Explosion");
        assertEquals(6, mines.size(), "Kirara resolves all C0 Cardamoms");
        assertClose(18.0 * FRAME,
                mines.get(5).time - mines.get(4).time,
                "Kirara expiring Cardamom spacing");
        assertEquals(0, c0.getActiveCardamomCount(),
                "Kirara clears resolved Cardamoms");

        Kirara c1 = kirara(1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        c1.restoreCurrentEnergy(60.0);
        perform(c1Simulator, CharacterActionKey.BURST);
        assertEquals(7, c1.getActiveCardamomCount(),
                "Kirara C1 adds one Cardamom at base Max HP");

        Kirara c5 = kirara(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        assertClose(2.080000,
                named(c5Records, "Tail-Flicking Flying Kick")
                        .get(0).action.getDamagePercent(),
                "Kirara C3 raises Skill talent");
        advanceTo(c5Simulator, c5.getSkillCooldownEndTime());
        c5.restoreCurrentEnergy(60.0);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(11.404800,
                named(c5Records, "Secret Art: Surprise Dispatch")
                        .get(0).action.getDamagePercent(),
                "Kirara C5 raises Burst talent");
    }

    private static void testC4NominalShieldGate() {
        Kirara kirara = kirara(4);
        CombatSimulator simulator = simulatorWith(kirara);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(0, named(records, "Small Cat Grass Cardamom").size(),
                "Kirara C4 requires nominal shield state");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> c4 = named(records, "Small Cat Grass Cardamom");
        assertEquals(1, c4.size(), "Kirara C4 triggers under shield state");
        assertClose(2.0, c4.get(0).action.getDamagePercent(),
                "Kirara C4 multiplier");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, named(records, "Small Cat Grass Cardamom").size(),
                "Kirara C4 observes shared cooldown");
        simulator.advanceTime(3.8);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Small Cat Grass Cardamom").size(),
                "Kirara C4 retriggers after cooldown");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Kirara kirara = kirara(1);
        CombatSimulator simulator = simulatorWith(kirara);
        List<ActionRecord> records = captureActions(simulator);
        kirara.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 908.0 * FRAME);
        int expectedFutureHits = named(
                records, "Cat Grass Cardamom Explosion").size();
        double expectedDamage = simulator.getTotalDamage();
        assertEquals(7, expectedFutureHits,
                "Kirara original branch resolves seven C1 Cardamoms");

        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 908.0 * FRAME);
        assertEquals(7,
                named(records, "Cat Grass Cardamom Explosion").size(),
                "Kirara restored branch resolves each Cardamom once");
        assertClose(expectedDamage, simulator.getTotalDamage(),
                "Kirara restored branch preserves total damage");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 908.0 * FRAME);
        assertEquals(7,
                named(records, "Cat Grass Cardamom Explosion").size(),
                "Kirara repeated restore keeps one future event sequence");

        Kirara foreign = kirara(0);
        assertTrue(!kirara.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Kirara rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> kirara.restoreCharacterState(null, simulator),
                "Kirara rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(kirara),
                "Kirara rejects cross-simulator reuse");
    }

    private static Kirara kirara(int constellation) {
        return new Kirara(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
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
                CharacterId.KIRARA, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KIRARA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureDendroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
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

    private static List<ActionRecord> startingWith(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
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
            assertTrue(lines.get(index).startsWith("Kirara,"),
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

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
