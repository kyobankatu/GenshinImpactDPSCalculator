package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Yaoyao;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
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

/** Focused regression checks for Yaoyao's bounded radish slice. */
public final class YaoyaoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private YaoyaoRegressionTest() {
    }

    /** Runs identity, action, summon, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testConstellationValidation();
        testNormalChargedAndPlungeActions();
        testSkillCadenceIcdParticlesAndReplacement();
        testBurstCadenceSwapAndExpiry();
        testBurstEmpowersThrowingYuegui();
        testC2EnergyC4MasteryAndTalentLevels();
        testSnapshotRestore();
        testBindingAndInputGuards();
        System.out.println("YaoyaoRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.YAOYAO, CharacterId.fromNumericId(59),
                "Yaoyao numeric identity");
        assertEquals(CharacterId.YAOYAO, CharacterId.fromName("Yaoyao"),
                "Yaoyao exact-name identity");
        assertEquals(CharacterRegion.LIYUE, CharacterId.YAOYAO.getRegion(),
                "Yaoyao region");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("yaoyao"),
                "Yaoyao lookup remains case-sensitive");

        Yaoyao yaoyao = yaoyao(0);
        assertEquals(CharacterId.YAOYAO, yaoyao.getCharacterId(),
                "Yaoyao runtime identity");
        assertEquals(Element.DENDRO, yaoyao.getElement(),
                "Yaoyao element");
        assertClose(12289.0,
                yaoyao.getBaseStats().get(StatType.BASE_HP),
                "Yaoyao base HP");
        assertClose(212.0,
                yaoyao.getBaseStats().get(StatType.BASE_ATK),
                "Yaoyao base ATK");
        assertClose(751.0,
                yaoyao.getBaseStats().get(StatType.BASE_DEF),
                "Yaoyao base DEF");
        assertClose(0.24,
                yaoyao.getBaseStats().get(StatType.HP_PERCENT),
                "Yaoyao ascension HP percent");
        assertClose(80.0, yaoyao.getEnergyCost(),
                "Yaoyao Burst cost");
        assertClose(15.0, yaoyao.getSkillCD(),
                "Yaoyao Skill cooldown");
        assertClose(20.0, yaoyao.getBurstCD(),
                "Yaoyao Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Yaoyao/Yaoyao_Status.csv"));
        assertCsvShape(Path.of(
                "config/characters/Yaoyao/Yaoyao_Multipliers.csv"));
        assertCsvValue("N3 Hit 2", 0.605282);
        assertCsvValue("Adeptal Legacy Radish C5", 1.443200);
    }

    private static void testConstellationValidation() {
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation,
                    yaoyao(constellation).getConstellation(),
                    "Yaoyao accepts C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> yaoyao(-1),
                "Yaoyao rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> yaoyao(7),
                "Yaoyao rejects constellation above C6");
    }

    private static void testNormalChargedAndPlungeActions() {
        Yaoyao yaoyao = yaoyao(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yaoyao, ally);
        List<ActionRecord> records = captureActions(simulator);
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(
                records, "Toss 'N' Turn Spear N");
        assertEquals(5, normals.size(),
                "Yaoyao four-step Normal string has five hits");
        double[] multipliers = {
            0.937003, 0.871623, 0.576463, 0.605282, 1.431764
        };
        double[] frames = {
            13.0, 28.0 + 16.0, 59.0 + 12.0,
            59.0 + 31.0, 110.0 + 21.0
        };
        for (int index = 0; index < normals.size(); index++) {
            ActionRecord record = normals.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Yaoyao Normal multiplier " + index);
            assertClose(frames[index] * FRAME, record.time,
                    "Yaoyao Normal hitmark " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Yaoyao Normal action type " + index);
        }
        assertClose(169.0 * FRAME, simulator.getCurrentTime(),
                "Yaoyao Normal string duration");
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.YAOYAO);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2,
                named(records, "Toss 'N' Turn Spear N1").size(),
                "Yaoyao switch-out resets Normal string");

        Yaoyao chargedOwner = yaoyao(0);
        CombatSimulator chargedSim = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(chargedSim);
        perform(chargedSim, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                chargedRecords, "Toss 'N' Turn Spear Charged").get(0);
        assertClose(2.069800, charged.action.getDamagePercent(),
                "Yaoyao Charged multiplier");
        assertClose(24.0 * FRAME, charged.time,
                "Yaoyao Charged hitmark");
        assertClose(55.0 * FRAME, chargedSim.getCurrentTime(),
                "Yaoyao Charged duration");

        Yaoyao plungeOwner = yaoyao(0);
        CombatSimulator plungeSim = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(
                plungeRecords, "Toss 'N' Turn Spear High Plunge").get(0);
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Yaoyao High Plunge multiplier");
        assertClose(46.0 * FRAME, plunge.time,
                "Yaoyao High Plunge hitmark");
        assertClose(77.0 * FRAME, plungeSim.getCurrentTime(),
                "Yaoyao High Plunge duration");
    }

    private static void testSkillCadenceIcdParticlesAndReplacement() {
        Yaoyao owner = yaoyao(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(15.0 * FRAME, owner.getLastSkillTime(),
                "Yaoyao Skill cooldown starts at frame 15");
        assertClose(15.0 * FRAME + 15.0,
                owner.getSkillCooldownEndTime(),
                "Yaoyao Skill cooldown duration");
        assertClose(52.0 * FRAME, simulator.getCurrentTime(),
                "Yaoyao Skill action duration");
        advanceTo(simulator, 12.0);
        List<ActionRecord> radishes = named(
                records, "Yuegui White Jade Radish");
        assertEquals(10, radishes.size(),
                "Yaoyao Skill summon throws ten radishes");
        for (int index = 0; index < radishes.size(); index++) {
            ActionRecord radish = radishes.get(index);
            assertClose((105.0 + 60.0 * index) * FRAME,
                    radish.time,
                    "Yaoyao Skill radish cadence " + index);
            assertClose(0.508640,
                    radish.action.getDamagePercent(),
                    "Yaoyao C0 Skill radish multiplier " + index);
            assertTrue(radish.action.hasStatSnapshot(),
                    "Yaoyao Skill radish retains spawn snapshot " + index);
            double expectedGauge = index % 3 == 0 ? 1.0 : 0.0;
            assertClose(expectedGauge,
                    radish.action.getGaugeUnits(),
                    "Yaoyao Skill radish 2.5-second ICD " + index);
        }
        assertEquals(5, particles.size(),
                "Yaoyao Skill particle ICD admits five packets");
        for (int index = 0; index < particles.size(); index++) {
            assertClose((205.0 + 120.0 * index) * FRAME,
                    particles.get(index).time,
                    "Yaoyao particle travel and ICD " + index);
            assertClose(1.0, particles.get(index).count,
                    "Yaoyao particle packet size " + index);
        }

        Yaoyao replacementOwner = yaoyao(0);
        CombatSimulator replacementSim = simulatorWith(replacementOwner);
        List<ActionRecord> replacementRecords =
                captureActions(replacementSim);
        replacementOwner.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                replacementSim);
        advanceTo(replacementSim, 1.2);
        replacementOwner.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                replacementSim);
        advanceTo(replacementSim, 13.0);
        List<ActionRecord> replacementRadishes = named(
                replacementRecords, "Yuegui White Jade Radish");
        assertEquals(10, replacementRadishes.size(),
                "Yaoyao Skill recast replaces prior Yuegui work");
        assertTrue(replacementRadishes.get(0).time > 2.0,
                "Only replacement Yuegui reaches its first radish");
    }

    private static void testBurstCadenceSwapAndExpiry() {
        Yaoyao owner = yaoyao(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(63.0 * FRAME, simulator.getCurrentTime(),
                "Yaoyao Burst action duration");
        assertClose(20.0, owner.getBurstCooldownEndTime(),
                "Yaoyao Burst cooldown starts at cast");
        assertClose(0.0, owner.getCurrentEnergy(),
                "Yaoyao Burst spends Energy at frame 7");
        advanceTo(simulator, 5.99);
        ActionRecord initial = named(records, "Moonjade Descent").get(0);
        assertClose(16.0 * FRAME, initial.time,
                "Yaoyao Burst initial hitmark");
        assertClose(1.947520, initial.action.getDamagePercent(),
                "Yaoyao C0 Burst initial multiplier");
        List<ActionRecord> radishes = named(
                records, "Adeptal Legacy White Jade Radish");
        assertEquals(9, radishes.size(),
                "Yaoyao three jumping Yuegui resolve nine radishes");
        double[] expectedFrames = {
            146, 204, 206, 263, 264, 266, 323, 324, 326
        };
        for (int index = 0; index < radishes.size(); index++) {
            assertClose(expectedFrames[index] * FRAME,
                    radishes.get(index).time,
                    "Yaoyao Burst radish cadence " + index);
        }
        assertTrue(owner.isBurstActive(5.99),
                "Yaoyao Burst remains active before six seconds");
        advanceTo(simulator, 6.0);
        assertTrue(!owner.isBurstActive(6.0),
                "Yaoyao Burst expires at exact six seconds");

        Yaoyao swapOwner = yaoyao(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator swapSim = simulatorWith(swapOwner, ally);
        List<ActionRecord> swapRecords = captureActions(swapSim);
        perform(swapSim, CharacterActionKey.BURST);
        swapSim.switchCharacter(CharacterId.NOELLE);
        advanceTo(swapSim, 6.0);
        assertEquals(0, named(
                swapRecords,
                "Adeptal Legacy White Jade Radish").size(),
                "Yaoyao swap removes jumping Yuegui before first hit");
        assertTrue(!swapOwner.isBurstActive(swapSim.getCurrentTime()),
                "Yaoyao swap ends Adeptal Legacy immediately");
    }

    private static void testC2EnergyC4MasteryAndTalentLevels() {
        Yaoyao c2 = yaoyao(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        perform(c2Sim, CharacterActionKey.BURST);
        advanceTo(c2Sim, 5.99);
        assertClose(12.0, c2.getCurrentEnergy(),
                "Yaoyao C2 restores four 3-Energy packets");
        assertClose(12.0, c2.getTotalFlatEnergy(),
                "Yaoyao C2 Energy bypasses Energy Recharge");

        Yaoyao c4 = yaoyao(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        perform(c4Sim, CharacterActionKey.SKILL);
        double expectedMastery = 12289.0 * 1.24 * 0.003;
        assertClose(expectedMastery,
                c4.getC4ElementalMastery(c4Sim.getCurrentTime()),
                "Yaoyao C4 converts cast-time Max HP to EM");
        assertClose(expectedMastery,
                c4.getEffectiveStats(c4Sim.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "Yaoyao C4 applies represented EM");
        advanceTo(c4Sim, 8.8);
        assertClose(0.0, c4.getC4ElementalMastery(8.8),
                "Yaoyao C4 expires at exact boundary");

        Yaoyao c3 = yaoyao(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Sim);
        perform(c3Sim, CharacterActionKey.SKILL);
        advanceTo(c3Sim, 2.0);
        assertClose(0.598400,
                named(c3Records, "Yuegui White Jade Radish")
                        .get(0).action.getDamagePercent(),
                "Yaoyao C3 raises Skill to Talent 12");

        Yaoyao c5 = yaoyao(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Sim);
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, 2.5);
        assertClose(2.291200,
                named(c5Records, "Moonjade Descent")
                        .get(0).action.getDamagePercent(),
                "Yaoyao C5 raises Burst initial to Talent 12");
        assertClose(1.443200,
                named(c5Records, "Adeptal Legacy White Jade Radish")
                        .get(0).action.getDamagePercent(),
                "Yaoyao C5 raises Burst radish to Talent 12");

        Yaoyao c6 = yaoyao(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Sim);
        perform(c6Sim, CharacterActionKey.SKILL);
        advanceTo(c6Sim, 12.0);
        assertEquals(10, named(
                c6Records, "Yuegui White Jade Radish").size(),
                "Yaoyao C6 does not synthesize Mega Radish geometry");
        assertTrue(c6.getActiveBuffs().isEmpty(),
                "Yaoyao healing and C1 branches remain absent");
    }

    private static void testBurstEmpowersThrowingYuegui() {
        Yaoyao owner = yaoyao(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 2.0);
        List<ActionRecord> empowered = named(
                records, "Adeptal Legacy White Jade Radish");
        assertEquals(1, empowered.size(),
                "Yaoyao Burst empowers the existing throwing Yuegui");
        assertClose(105.0 * FRAME, empowered.get(0).time,
                "Empowered throwing Yuegui keeps its Skill cadence");
        assertClose(1.226720,
                empowered.get(0).action.getDamagePercent(),
                "Empowered throwing Yuegui uses Burst radish scaling");
        assertEquals(ActionType.BURST,
                empowered.get(0).action.getActionType(),
                "Empowered throwing Yuegui counts as Burst damage");
    }

    private static void testSnapshotRestore() {
        Yaoyao owner = yaoyao(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 4.0);
        List<ActionRecord> baseline = named(
                records, "Yuegui White Jade Radish");
        assertEquals(3, baseline.size(),
                "Yaoyao live Skill resolves three early radishes");
        double baselineDamage = baseline.get(0).damage;
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 4.0);
        List<ActionRecord> restored = named(
                records, "Yuegui White Jade Radish");
        assertEquals(6, restored.size(),
                "Yaoyao restored Skill resolves future work once");
        assertClose(baselineDamage, restored.get(3).damage,
                "Yaoyao restored summon preserves spawn snapshot");
        assertEquals(2, particles.size(),
                "Yaoyao live and restored particle work each resolves once");

        Yaoyao comboOwner = yaoyao(0);
        CombatSimulator comboSim = simulatorWith(comboOwner);
        List<ActionRecord> comboRecords = captureActions(comboSim);
        perform(comboSim, CharacterActionKey.NORMAL);
        SimulatorSnapshot comboSnapshot = comboSim.saveSnapshot();
        perform(comboSim, CharacterActionKey.NORMAL);
        comboSim.restoreSnapshot(comboSnapshot);
        perform(comboSim, CharacterActionKey.NORMAL);
        assertEquals(2,
                named(comboRecords, "Toss 'N' Turn Spear N2").size(),
                "Yaoyao restore preserves Normal combo progression");
    }

    private static void testBindingAndInputGuards() {
        Yaoyao yaoyao = yaoyao(0);
        CombatSimulator simulator = simulatorWith(yaoyao);
        assertThrows(IllegalArgumentException.class,
                () -> yaoyao.onAction(null, simulator),
                "Yaoyao rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Yaoyao rejects unsupported Dash");
        Yaoyao external = yaoyao(0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Yaoyao rejects binding outside simulator party");
        Yaoyao reused = yaoyao(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Yaoyao rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!yaoyao.acceptsCharacterState(foreignState),
                "Yaoyao rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> yaoyao.restoreCharacterState(
                        foreignState, simulator),
                "Yaoyao rejects foreign restore payload");
    }

    private static Yaoyao yaoyao(int constellation) {
        return new Yaoyao(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Yaoyao yaoyao,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(yaoyao);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.YAOYAO);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.YAOYAO, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(action, damage, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
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

    private static void assertCsvShape(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertTrue(lines.size() > 2, path + " has data rows");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Yaoyao,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Yaoyao/Yaoyao_Status.csv",
                "config/characters/Yaoyao/Yaoyao_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected, Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Yaoyao CSVs missing key " + key);
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
        private final double damage;
        private final double time;

        private ActionRecord(
                AttackAction action,
                double damage,
                double time) {
            this.action = action;
            this.damage = damage;
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
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return null;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}
