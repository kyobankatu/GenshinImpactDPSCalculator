package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Jahoda;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused regressions for Jahoda's sourced fixed-target offensive slice. */
public final class JahodaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private JahodaRegressionTest() {
    }

    /** Runs static, timing, state, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndScope();
        testBowActionsAndProjectileTiming();
        testNaturalAndRecastFlaskBoundaries();
        testFullFlaskMeowballsParticlesAndC6();
        testBurstRobotsA1C2C3C4AndIcd();
        testSnapshotNoEnemyAndIsolation();
        System.out.println("JahodaRegressionTest passed");
    }

    private static void testIdentityDataAndScope() throws IOException {
        Jahoda jahoda = deterministicJahoda(6, 0.0);
        assertEquals(CharacterId.JAHODA, jahoda.getCharacterId(),
                "Jahoda typed identity");
        assertEquals(CharacterId.JAHODA, CharacterId.fromName("Jahoda"),
                "Jahoda name lookup");
        assertEquals(CharacterId.JAHODA, CharacterId.fromNumericId(111),
                "Jahoda numeric lookup");
        assertEquals(CharacterRegion.NOD_KRAI,
                CharacterId.JAHODA.getRegion(),
                "Jahoda region");
        assertEquals(Element.ANEMO, jahoda.getElement(),
                "Jahoda element");
        assertTrue(jahoda.isLunarCharacter(),
                "Jahoda contributes one Moonsign level");
        assertClose(9646.0,
                jahoda.getBaseStats().get(StatType.BASE_HP),
                "Jahoda base HP");
        assertClose(223.0,
                jahoda.getBaseStats().get(StatType.BASE_ATK),
                "Jahoda base ATK");
        assertClose(580.0,
                jahoda.getBaseStats().get(StatType.BASE_DEF),
                "Jahoda base DEF");
        assertClose(0.1846,
                jahoda.getBaseStats().get(StatType.HEALING_BONUS),
                "Jahoda ascension Healing Bonus");
        assertClose(70.0, jahoda.getEnergyCost(),
                "Jahoda Burst cost");
        assertClose(15.0, jahoda.getSkillCD(),
                "Jahoda Skill cooldown");
        assertClose(18.0, jahoda.getBurstCD(),
                "Jahoda Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Jahoda/Jahoda_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Jahoda/Jahoda_Multipliers.csv"), 42);
        assertCsvValue("Filled Treasure Flask C5", 4.24);
        assertCsvValue("Robot C3", 0.345328);
        assertCsvValue("C6 Crit Damage", 0.40);
        assertTrue(!jahoda.isHealingRepresented(),
                "Healing and current-HP gates are excluded");
        assertTrue(!jahoda.isMovementAndHitlagRepresented(),
                "Movement/contact/hitlag are excluded");
        assertTrue(!jahoda.isGadgetAndMultiTargetRepresented(),
                "Gadget and multi-target geometry are excluded");
        assertThrows(IllegalArgumentException.class,
                () -> new Jahoda(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Jahoda(null, null, 7),
                "Constellation above C6 is rejected");
    }

    private static void testBowActionsAndProjectileTiming() {
        Jahoda jahoda = deterministicJahoda(0, 0.9);
        CombatSimulator simulator = simulatorWith(jahoda);
        List<ActionRecord> records = captureJahodaActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose((35.0 + 52.0 + 99.0 + 85.0) * FRAME,
                simulator.getCurrentTime(),
                "Sourced bow action recovery");
        advanceTo(simulator,
                (35.0 + 52.0 + 99.0 + 95.0) * FRAME + EPSILON);

        assertEquals(5, records.size(),
                "Three Normal strings and one aimed shot resolve five hits");
        assertAction(records.get(0),
                "Strike While the Arrow's Hot N1",
                0.765636,
                24.0 * FRAME,
                ICDType.None,
                ICDTag.None,
                0.0);
        assertAction(records.get(1),
                "Strike While the Arrow's Hot N2",
                0.353320,
                (35.0 + 25.0) * FRAME,
                ICDType.None,
                ICDTag.None,
                0.0);
        assertAction(records.get(2),
                "Strike While the Arrow's Hot N2-2",
                0.353320,
                (35.0 + 39.0) * FRAME,
                ICDType.None,
                ICDTag.None,
                0.0);
        assertAction(records.get(3),
                "Strike While the Arrow's Hot N3",
                0.940606,
                (35.0 + 52.0 + 50.0) * FRAME,
                ICDType.None,
                ICDTag.None,
                0.0);
        assertAction(records.get(4),
                "Fully-Charged Aimed Shot",
                2.108,
                (35.0 + 52.0 + 99.0 + 95.0) * FRAME,
                ICDType.None,
                ICDTag.None,
                1.0);
    }

    private static void testNaturalAndRecastFlaskBoundaries() {
        Jahoda natural = deterministicJahoda(0, 0.9);
        CombatSimulator naturalSimulator = simulatorWith(natural);
        List<ActionRecord> naturalRecords =
                captureJahodaActions(naturalSimulator);
        performSkill(naturalSimulator);
        assertTrue(natural.isShadowPursuitActive(),
                "Shadow Pursuit enters at frame 30");
        assertClose(0.0,
                natural.getSkillCDRemaining(
                        naturalSimulator.getCurrentTime()),
                "Skill recast remains available in Pursuit");
        assertThrows(IllegalStateException.class,
                () -> natural.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.NORMAL),
                        naturalSimulator),
                "Non-Skill actions are blocked in Pursuit");
        advanceTo(naturalSimulator, 364.0 * FRAME);
        assertTrue(!natural.isShadowPursuitActive(),
                "Pursuit naturally ends at frame 364");
        assertEquals(0, naturalRecords.size(),
                "Natural discharge waits four frames");
        advanceTo(naturalSimulator, 368.0 * FRAME + EPSILON);
        assertAction(named(naturalRecords,
                        "Unfilled Treasure Flask").get(0),
                "Unfilled Treasure Flask",
                3.2436,
                368.0 * FRAME,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);

        Jahoda recast = deterministicJahoda(0, 0.9);
        CombatSimulator recastSimulator = simulatorWith(recast);
        List<ActionRecord> recastRecords =
                captureJahodaActions(recastSimulator);
        performSkill(recastSimulator);
        performSkill(recastSimulator);
        assertTrue(!recast.isShadowPursuitActive(),
                "Skill recast ends Pursuit");
        assertAction(named(recastRecords,
                        "Unfilled Treasure Flask").get(0),
                "Unfilled Treasure Flask",
                3.2436,
                46.0 * FRAME,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        assertTrue(recast.getSkillCDRemaining(
                recastSimulator.getCurrentTime()) > 0.0,
                "Cooldown starts at discharge");
        assertThrows(IllegalArgumentException.class,
                () -> recast.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        recastSimulator),
                "Unsupported Hold Skill is rejected");
    }

    private static void testFullFlaskMeowballsParticlesAndC6() {
        Jahoda jahoda = deterministicJahoda(6, 0.0);
        TestCharacter lunar = new TestCharacter(
                CharacterId.ILLUGA, Element.GEO, true);
        TestCharacter nonLunar = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, false);
        CombatSimulator simulator = simulatorWith(jahoda, lunar, nonLunar);
        simulator.updateMoonsign();
        simulator.getEnemy().setAura(Element.PYRO, 100.0);
        List<ActionRecord> records = captureJahodaActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        double jahodaRate = jahoda.getEffectiveStats(0.0)
                .get(StatType.CRIT_RATE);
        double lunarDamage = lunar.getEffectiveStats(0.0)
                .get(StatType.CRIT_DMG);
        double nonLunarRate = nonLunar.getEffectiveStats(0.0)
                .get(StatType.CRIT_RATE);

        performSkill(simulator);
        assertEquals(20, jahoda.getFlaskGauge(),
                "First absorption occurs at frame 37");
        advanceTo(simulator, 156.0 * FRAME);
        assertEquals(80, jahoda.getFlaskGauge(),
                "Gauge remains below full before frame 157");
        advanceTo(simulator, 161.0 * FRAME + EPSILON);
        assertEquals(100, jahoda.getFlaskGauge(),
                "Strong absorption fills to 100");
        assertEquals(Element.PYRO, jahoda.getFlaskElement(),
                "Flask keeps its first absorbed element");
        assertTrue(!jahoda.isShadowPursuitActive(),
                "Full Flask drains at frame 161");
        assertClose(jahodaRate + 0.05,
                jahoda.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.CRIT_RATE),
                "C6 grants Jahoda CRIT Rate");
        assertClose(lunarDamage + 0.40,
                lunar.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.CRIT_DMG),
                "C6 grants another Moonsign character CRIT DMG");
        assertClose(nonLunarRate,
                nonLunar.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.CRIT_RATE),
                "C6 excludes non-Moonsign characters");
        advanceTo(simulator, 263.0 * FRAME + EPSILON);
        assertAction(named(records, "Filled Treasure Flask").get(0),
                "Filled Treasure Flask",
                4.24,
                163.0 * FRAME,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        assertEquals(1, particles.size(),
                "Accepted Flask hit emits one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Jahoda emits four Anemo particles");
        assertClose(263.0 * FRAME, particles.get(0).time,
                "Particle travel uses 100 frames");
        advanceTo(simulator, 348.0 * FRAME + EPSILON);
        assertAction(named(records, "Meowball").get(0),
                "Meowball",
                2.56,
                303.0 * FRAME,
                ICDType.None,
                ICDTag.None,
                1.0);
        assertAction(named(records, "Meowball C1 Bounce").get(0),
                "Meowball C1 Bounce",
                2.56,
                348.0 * FRAME,
                ICDType.None,
                ICDTag.None,
                1.0);
        assertClose(2.0, jahoda.getTotalFlatEnergy(),
                "First accepted Meowball grants two flat Energy");
    }

    private static void testBurstRobotsA1C2C3C4AndIcd() {
        Jahoda jahoda = deterministicJahoda(4, 0.9);
        TestCharacter lunar = new TestCharacter(
                CharacterId.ILLUGA, Element.GEO, true);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, false);
        TestCharacter cryo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO, false);
        CombatSimulator simulator = simulatorWith(
                jahoda, lunar, pyro, cryo);
        simulator.updateMoonsign();
        simulator.getEnemy().setAura(Element.PYRO, 100.0);
        List<ActionRecord> records = captureJahodaActions(simulator);

        perform(simulator, CharacterActionKey.BURST);
        assertAction(named(records,
                        "Hidden Aces: Seven Tools of the Hunter").get(0),
                "Hidden Aces: Seven Tools of the Hunter",
                4.144,
                43.0 * FRAME,
                ICDType.None,
                ICDTag.None,
                1.0);
        assertClose(8.0, jahoda.getTotalFlatEnergy(),
                "C4 grants four Energy for each robot conversion");
        advanceTo(simulator, 420.0 * FRAME);
        List<ActionRecord> robots = named(
                records, "Purrsonal Coordinated Assistance Robot");
        assertTrue(robots.size() >= 6,
                "Two robots continue through the Burst window");
        assertClose(0.345328 * 1.3,
                robots.get(0).action.getDamagePercent(),
                "C3 and Pyro A1 multiply robot damage");
        assertClose(47.0 * FRAME, robots.get(0).time,
                "First robot hitmark");
        assertClose(88.0 * FRAME, robots.get(1).time,
                "Second robot hitmark");
        assertClose(126.0 * FRAME,
                robots.get(2).time - robots.get(0).time,
                "C2 selects tied Cryo second and shortens interval");
        assertClose(1.0, robots.get(0).action.getGaugeUnits(),
                "Robot special ICD applies on first hit");
        assertClose(0.0, robots.get(1).action.getGaugeUnits(),
                "Robot special ICD suppresses second hit");
        assertClose(0.0, robots.get(2).action.getGaugeUnits(),
                "Robot special ICD suppresses third hit");
        assertClose(1.0, robots.get(3).action.getGaugeUnits(),
                "Robot special ICD applies on fourth hit");

        Jahoda retry = deterministicJahoda(0, 0.9);
        CombatSimulator retrySimulator = simulatorWith(
                retry,
                new TestCharacter(
                        CharacterId.AINO, Element.HYDRO, true));
        retrySimulator.updateMoonsign();
        List<ActionRecord> retryRecords =
                captureJahodaActions(retrySimulator);
        perform(retrySimulator, CharacterActionKey.BURST);
        assertEquals(0, named(retryRecords,
                        "Purrsonal Coordinated Assistance Robot").size(),
                "Robots fail closed without absorbable aura");
        retrySimulator.getEnemy().setAura(Element.ELECTRO, 100.0);
        advanceTo(retrySimulator, 94.0 * FRAME);
        assertEquals(1, named(retryRecords,
                        "Purrsonal Coordinated Assistance Robot").size(),
                "Robot retries absorption at sourced one-third interval");
    }

    private static void testSnapshotNoEnemyAndIsolation() {
        Jahoda jahoda = deterministicJahoda(0, 0.9);
        CombatSimulator simulator = simulatorWith(jahoda);
        List<ActionRecord> records = captureJahodaActions(simulator);
        performSkill(simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 368.0 * FRAME + EPSILON);
        assertEquals(1, named(records,
                        "Unfilled Treasure Flask").size(),
                "Original timeline resolves discharge once");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 368.0 * FRAME + EPSILON);
        assertEquals(2, named(records,
                        "Unfilled Treasure Flask").size(),
                "Restored timeline reconstructs discharge once");

        Jahoda noEnemy = deterministicJahoda(6, 0.0);
        CombatSimulator empty = simulatorWithoutEnemy(noEnemy);
        List<ParticleRecord> particles = captureAnemoParticles(empty);
        performSkill(empty);
        advanceTo(empty, 8.0);
        assertEquals(0, particles.size(),
                "No enemy suppresses accepted-hit particles");
        assertClose(0.0, noEnemy.getTotalFlatEnergy(),
                "No enemy suppresses Meowball Energy");

        Jahoda invalidRandom = deterministicJahoda(1, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(
                invalidRandom,
                new TestCharacter(
                        CharacterId.ILLUGA, Element.GEO, true));
        invalidSimulator.updateMoonsign();
        invalidSimulator.getEnemy().setAura(Element.PYRO, 100.0);
        performSkill(invalidSimulator);
        assertThrows(IllegalStateException.class,
                () -> advanceTo(invalidSimulator, 304.0 * FRAME),
                "Invalid C1 random draw fails closed");

        Jahoda reused = deterministicJahoda(0, 0.9);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Jahoda rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreign =
                deterministicJahoda(0, 0.9).captureCharacterState();
        assertTrue(!jahoda.acceptsCharacterState(foreign),
                "Jahoda rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> jahoda.onAction(null, simulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> jahoda.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.PLUNGE),
                        simulator),
                "Unsupported Plunge is rejected");
    }

    private static Jahoda deterministicJahoda(
            int constellation,
            double c1Draw) {
        return new Jahoda(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> c1Draw);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        for (StatType resistance : new StatType[] {
                StatType.PHYSICAL_DMG_BONUS,
                StatType.ANEMO_DMG_BONUS,
                StatType.PYRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                StatType.ELECTRO_DMG_BONUS,
                StatType.CRYO_DMG_BONUS
        }) {
            enemy.setRes(resistance, 0.0);
        }
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static CombatSimulator simulatorWithoutEnemy(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.JAHODA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.JAHODA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureJahodaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.JAHODA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureAnemoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ANEMO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertAction(
            ActionRecord record,
            String name,
            double multiplier,
            double time,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        assertEquals(name, record.action.getName(), name + " name");
        assertClose(multiplier, record.action.getDamagePercent(),
                name + " multiplier");
        assertClose(time, record.time, name + " hitmark");
        assertEquals(icdType, record.action.getICDType(),
                name + " ICD type");
        assertEquals(icdTag, record.action.getICDTag(),
                name + " ICD tag");
        assertClose(gauge, record.action.getGaugeUnits(),
                name + " gauge");
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
            assertTrue(lines.get(index).startsWith("Jahoda,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Jahoda/Jahoda_Status.csv",
                "config/characters/Jahoda/Jahoda_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected,
                            Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Jahoda CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertTrue(
            boolean condition,
            String message) {
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
            throw new AssertionError(message + ": unexpected exception",
                    throwable);
        }
        throw new AssertionError(message + ": no exception");
    }

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

    private static final class ParticleRecord {
        private final double count;
        private final double time;

        private ParticleRecord(double count, double time) {
            this.count = count;
            this.time = time;
        }
    }

    private static final class TestCharacter extends Character {
        private final boolean lunar;

        private TestCharacter(
                CharacterId id,
                Element characterElement,
                boolean lunar) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            this.lunar = lunar;
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

        @Override
        public boolean isLunarCharacter() {
            return lunar;
        }
    }
}
