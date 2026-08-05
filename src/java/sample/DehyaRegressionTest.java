package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Dehya;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused checks for Dehya's stationary Fiery Sanctum offensive slice. */
public final class DehyaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private DehyaRegressionTest() {
    }

    /** Runs identity, action, field, Burst, guard, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalAndPlungeActions();
        testSkillFieldRecastParticlesAndC1C2C5();
        testBurstSequenceAndConstellations();
        testSnapshotRollbackAndIsolation();
        System.out.println("DehyaRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        Dehya dehya = dehya(0, 0.5);
        assertEquals(CharacterId.DEHYA, dehya.getCharacterId(),
                "Dehya typed identity");
        assertEquals(CharacterId.DEHYA, CharacterId.fromNumericId(74),
                "Dehya numeric identity");
        assertEquals(CharacterId.DEHYA, CharacterId.fromName("Dehya"),
                "Dehya display-name identity");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.DEHYA.getRegion(), "Dehya region");
        assertEquals(Element.PYRO, dehya.getElement(), "Dehya element");
        assertClose(15675.0,
                dehya.getBaseStats().get(StatType.BASE_HP),
                "Dehya base HP");
        assertClose(265.0,
                dehya.getBaseStats().get(StatType.BASE_ATK),
                "Dehya base ATK");
        assertClose(628.0,
                dehya.getBaseStats().get(StatType.BASE_DEF),
                "Dehya base DEF");
        assertClose(0.288,
                dehya.getBaseStats().get(StatType.HP_PERCENT),
                "Dehya ascension HP");
        assertClose(70.0, dehya.getEnergyCost(), "Dehya Energy cost");
        assertClose(20.0, dehya.getSkillCD(), "Dehya Skill cooldown");
        assertClose(18.0, dehya.getBurstCD(), "Dehya Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    dehya(constellation, 0.5).getConstellation(),
                    "Dehya constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Dehya/Dehya_Status.csv"), 26);
        assertCsvShape(Path.of(
                "config/characters/Dehya/Dehya_Multipliers.csv"), 21);
        assertCsvValue("Fiery Sanctum Max HP C5", 0.020640);
        assertCsvValue("Incineration Drive ATK C3", 2.786000);

        assertThrows(IllegalArgumentException.class,
                () -> dehya(-1, 0.5),
                "Dehya rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> dehya(7, 0.5),
                "Dehya rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Dehya(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Dehya rejects null C6 randomness");

        CombatSimulator simulator = simulatorWith(dehya);
        assertThrows(IllegalArgumentException.class,
                () -> dehya.onAction(null, simulator),
                "Dehya rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.DEHYA,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Dehya rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Dehya rejects unsupported Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Dehya rejects movement action");
    }

    private static void testNormalAndPlungeActions() {
        Dehya dehya = dehya(0, 0.5);
        CombatSimulator simulator = simulatorWith(dehya);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            1.141234, 1.133745, 1.407875, 1.750703
        };
        for (int step = 0; step < expected.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = prefixed(records, "Sandstorm Assault N");
        assertEquals(4, normals.size(), "Dehya Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    normals.get(index).action.getDamagePercent(),
                    "Dehya Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Dehya Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    normals.get(index).action.getActionType(),
                    "Dehya Normal category " + index);
        }
        assertClose(22.0 * FRAME, normals.get(0).time,
                "Dehya N1 hit frame");
        assertClose((31.0 + 26.0) * FRAME, normals.get(1).time,
                "Dehya N2 hit frame");

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Dehya High Plunge hit count");
        assertClose(3.422517,
                records.get(0).action.getDamagePercent(),
                "Dehya High Plunge multiplier");
        assertTrue(records.get(0).action.isShatterTrigger(),
                "Dehya High Plunge remains blunt");
    }

    private static void testSkillFieldRecastParticlesAndC1C2C5() {
        Dehya dehya = dehya(5, 0.5);
        TestCharacter ally = new TestCharacter(
                CharacterId.NAHIDA, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(dehya, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);

        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord initial = named(
                records, "Molten Inferno: Indomitable Flame").get(0);
        assertClose(2.257600, initial.action.getDamagePercent(),
                "Dehya C5 initial Skill multiplier");
        assertEquals(ICDType.None, initial.action.getICDType(),
                "Dehya initial Skill has no ICD");
        assertClose(0.036
                        * initial.action.getStatSnapshot().getTotalHp(),
                initial.action.getAdditiveBaseDmgBonus(),
                "Dehya C1 initial Skill HP addition");
        assertTrue(dehya.isFierySanctumActive(
                simulator.getCurrentTime()),
                "Dehya initial Skill places field");
        assertClose(0.0,
                dehya.getSkillCDRemaining(simulator.getCurrentTime()),
                "Dehya field exposes one recast");

        double recastTime = simulator.getCurrentTime();
        double remaining = dehya.getFieldExpirationTime() - recastTime;
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord recast = named(
                records, "Molten Inferno: Ranging Flame").get(0);
        assertClose(2.656000, recast.action.getDamagePercent(),
                "Dehya C5 recast multiplier");
        double recastPlacement = recastTime + 41.0 * FRAME;
        assertClose(recastPlacement + remaining + 0.4 + 6.0,
                dehya.getFieldExpirationTime(),
                "Dehya C2 extends recreated field by six seconds");
        assertTrue(dehya.getSkillCDRemaining(
                simulator.getCurrentTime()) > 0.0,
                "Dehya recast is available only once");

        records.clear();
        performAllyHit(simulator, ally);
        simulator.advanceTime(3.0 * FRAME);
        List<ActionRecord> coordinated = named(
                records, "Fiery Sanctum Coordinated Attack");
        assertEquals(1, coordinated.size(),
                "Dehya field triggers one coordinated attack");
        AttackAction fieldHit = coordinated.get(0).action;
        assertClose(1.204000, fieldHit.getDamagePercent(),
                "Dehya C5 field ATK multiplier");
        assertClose((0.020640 + 0.036)
                        * fieldHit.getStatSnapshot().getTotalHp(),
                fieldHit.getAdditiveBaseDmgBonus(),
                "Dehya field combines talent and C1 Max HP additions");
        assertClose(2.5,
                dehya.getNextCoordinatedAllowedTime()
                        - (coordinated.get(0).time - 2.0 * FRAME),
                "Dehya coordinated attack gate duration");

        performAllyHit(simulator, ally);
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(1,
                named(records, "Fiery Sanctum Coordinated Attack").size(),
                "Dehya field rejects triggers inside 2.5-second gate");
        simulator.advanceTime(3.0);
        assertEquals(1, particles.size(),
                "Dehya coordinated attack makes one particle packet");
        assertClose(1.0, particles.get(0),
                "Dehya coordinated attack particle amount");

        Dehya c0 = dehya(0, 0.5);
        CombatSimulator c0Simulator = simulatorWith(c0, allyCopy());
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.SKILL);
        AttackAction c0Initial = named(
                c0Records, "Molten Inferno: Indomitable Flame")
                .get(0).action;
        assertClose(1.918960, c0Initial.getDamagePercent(),
                "Dehya pre-C5 Skill multiplier");
        assertClose(0.0, c0Initial.getAdditiveBaseDmgBonus(),
                "Dehya C1 HP addition does not leak into C0");
    }

    private static void testBurstSequenceAndConstellations() {
        Dehya c4 = dehya(4, 0.99);
        CombatSimulator simulator = simulatorWith(c4);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.advanceTime(6.0);
        List<ActionRecord> fists = prefixed(records, "Flame-Mane's Fist");
        List<ActionRecord> kicks = named(records, "Incineration Drive");
        assertEquals(6, fists.size(),
                "Dehya automatic Burst has six pre-C6 fists");
        assertEquals(1, kicks.size(),
                "Dehya automatic Burst ends in one kick");
        assertClose(1.974000,
                fists.get(0).action.getDamagePercent(),
                "Dehya C3 fist ATK multiplier");
        assertClose(2.786000,
                kicks.get(0).action.getDamagePercent(),
                "Dehya C3 kick ATK multiplier");
        assertClose((0.033840 + 0.060)
                        * fists.get(0).action.getStatSnapshot().getTotalHp(),
                fists.get(0).action.getAdditiveBaseDmgBonus(),
                "Dehya fist combines C3 and C1 Max HP additions");
        assertClose(10.5, c4.getTotalFlatEnergy(),
                "Dehya C4 restores Energy on six fists and one kick");
        assertClose(10.5, c4.getCurrentEnergy(),
                "Dehya Burst spends Energy then receives C4 refunds");

        Dehya c6 = dehya(6, 0.0);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.BURST);
        c6Simulator.advanceTime(9.0);
        List<ActionRecord> c6Fists = prefixed(
                c6Records, "Flame-Mane's Fist");
        assertEquals(4, c6.getC6Stacks(),
                "Dehya C6 extension stack cap");
        assertTrue(c6Fists.size() > fists.size(),
                "Dehya C6 critical draws extend fist sequence");
        assertClose(0.10,
                c6Fists.get(0).action.getExtraBonuses()
                        .getOrDefault(StatType.CRIT_RATE, 0.0),
                "Dehya C6 adds Burst CRIT Rate");
        assertClose(0.60,
                c6Fists.get(c6Fists.size() - 1).action
                        .getExtraBonuses()
                        .getOrDefault(StatType.CRIT_DMG, 0.0),
                "Dehya C6 reaches sixty percent Burst CRIT DMG");

        Dehya c0 = dehya(0, 0.0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        c0Simulator.advanceTime(6.0);
        AttackAction c0Fist = prefixed(
                c0Records, "Flame-Mane's Fist").get(0).action;
        assertClose(1.677900, c0Fist.getDamagePercent(),
                "Dehya pre-C3 fist multiplier");
        assertClose(0.028764
                        * c0Fist.getStatSnapshot().getTotalHp(),
                c0Fist.getAdditiveBaseDmgBonus(),
                "Dehya pre-C1 fist uses talent Max HP addition only");
        assertClose(0.0, c0.getTotalFlatEnergy(),
                "Dehya C4 Energy does not leak into C0");
    }

    private static void testSnapshotRollbackAndIsolation() {
        Dehya dehya = dehya(5, 0.5);
        TestCharacter ally = allyCopy();
        CombatSimulator simulator = simulatorWith(dehya, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        performAllyHit(simulator, ally);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(1,
                named(records, "Fiery Sanctum Coordinated Attack").size(),
                "Dehya queued field hit resolves before rollback");
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(1,
                named(records, "Fiery Sanctum Coordinated Attack").size(),
                "Dehya restored field hit resolves exactly once");
        assertTrue(dehya.isFierySanctumActive(
                simulator.getCurrentTime()),
                "Dehya rollback restores field window");

        Dehya foreign = dehya(0, 0.5);
        assertTrue(!dehya.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Dehya rejects foreign owner state");
        assertThrows(IllegalArgumentException.class,
                () -> dehya.restoreCharacterState(null, simulator),
                "Dehya rejects null state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(dehya),
                "Dehya rejects cross-simulator reuse");
    }

    private static Dehya dehya(
            int constellation,
            double criticalDraw) {
        return new Dehya(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> criticalDraw);
    }

    private static TestCharacter allyCopy() {
        return new TestCharacter(CharacterId.NAHIDA, Element.DENDRO);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.DEHYA, CharacterActionRequest.of(key));
    }

    private static void performAllyHit(
            CombatSimulator simulator,
            Character ally) {
        AttackAction action = new AttackAction(
                "Dehya Regression Ally Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.None, model.type.ICDTag.None, 0.0);
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(), action);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DEHYA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> capturePyroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
                records.add(count);
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> matching = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                matching.add(record);
            }
        }
        return matching;
    }

    private static List<ActionRecord> prefixed(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> matching = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                matching.add(record);
            }
        }
        return matching;
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
            assertTrue(lines.get(index).startsWith("Dehya,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        List<String> lines = Files.readAllLines(Path.of(
                "config/characters/Dehya/Dehya_Multipliers.csv"));
        for (String line : lines) {
            String[] columns = line.split(",", -1);
            if (columns.length == 6 && columns[2].equals(key)) {
                assertClose(expected, Double.parseDouble(columns[4]),
                        "Dehya CSV value " + key);
                return;
            }
        }
        throw new AssertionError("Missing Dehya CSV key " + key);
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
