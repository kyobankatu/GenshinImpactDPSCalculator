package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import mechanics.element.ICDManager;
import model.character.Aino;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
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

/** Focused regression checks for Aino's fixed-target Ducky slice. */
public final class AinoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private AinoRegressionTest() {
    }

    /** Runs data, timing, particle, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndPrivateIcd();
        testPhysicalNormalsAndMusecatcher();
        testParticlesC4AndNoEnemyBoundary();
        testDuckyCadenceA1A4AndC3();
        testC1C2AndC6Support();
        testFailClosedSnapshotAndIsolation();
        System.out.println("AinoRegressionTest passed");
    }

    private static void testIdentityDataAndPrivateIcd()
            throws IOException {
        Aino aino = new Aino(null, null, 6);
        assertEquals(CharacterId.AINO, aino.getCharacterId(),
                "Aino typed identity");
        assertEquals(CharacterId.AINO, CharacterId.fromName("Aino"),
                "Aino name lookup");
        assertEquals(CharacterId.AINO, CharacterId.fromNumericId(86),
                "Aino numeric lookup");
        assertEquals(CharacterRegion.NOD_KRAI,
                CharacterId.AINO.getRegion(),
                "Aino region");
        assertEquals(Element.HYDRO, aino.getElement(),
                "Aino element");
        assertClose(11201.0,
                aino.getBaseStats().get(StatType.BASE_HP),
                "Aino base HP");
        assertClose(242.0,
                aino.getBaseStats().get(StatType.BASE_ATK),
                "Aino base ATK");
        assertClose(607.0,
                aino.getBaseStats().get(StatType.BASE_DEF),
                "Aino base DEF");
        assertClose(96.0,
                aino.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Aino ascension EM");
        assertClose(50.0, aino.getEnergyCost(),
                "Aino Energy cost");
        assertClose(10.0, aino.getSkillCD(),
                "Aino Skill cooldown");
        assertClose(13.5, aino.getBurstCD(),
                "Aino Burst cooldown");
        assertTrue(aino.isLunarCharacter(),
                "Aino contributes typed Moonsign membership");
        CombatSimulator moonsignSimulator = simulatorWith(
                new Aino(null, null, 0),
                new TestCharacter(
                        CharacterId.COLUMBINA,
                        Element.HYDRO,
                        true));
        moonsignSimulator.updateMoonsign();
        assertEquals(CombatSimulator.Moonsign.ASCENDANT_GLEAM,
                moonsignSimulator.getMoonsign(),
                "Aino participates in typed Moonsign composition");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.AINO,
                    new Aino(null, null, constellation).getCharacterId(),
                    "Aino explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Aino/Aino_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Aino/Aino_Multipliers.csv"), 33);
        assertCsvValue("Musecatcher Stage 2 C5", 3.776);
        assertCsvValue("Ducky Water Ball C3", 0.40224);
        assertCsvValue("A1 Ducky Interval", 0.7);

        ICDManager icdManager = new ICDManager();
        assertTrue(icdManager.checkApplication(
                "AINO",
                ICDTag.Aino_Ducky,
                ICDType.AinoDucky,
                0.0),
                "First enhanced Ducky application passes");
        assertTrue(!icdManager.checkApplication(
                "AINO",
                ICDTag.Aino_Ducky,
                ICDType.AinoDucky,
                1.799999),
                "Enhanced Ducky ICD blocks before 1.8 seconds");
        assertTrue(icdManager.checkApplication(
                "AINO",
                ICDTag.Aino_Ducky,
                ICDType.AinoDucky,
                1.8),
                "Enhanced Ducky ICD opens at 1.8 seconds");
    }

    private static void testPhysicalNormalsAndMusecatcher() {
        Aino aino = new Aino(null, null, 0);
        CombatSimulator simulator = simulatorWith(aino);
        List<ActionRecord> records = captureAinoActions(simulator);
        double[][] normalValues = {
            { 1.221719 }, { 1.216079 }, { 0.904210, 0.904210 }
        };
        int[][] hitFrames = { { 23 }, { 20 }, { 35, 43 } };
        int[] durations = { 48, 75, 93 };
        for (int step = 0; step < normalValues.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> stage = normalStage(records, step);
            assertEquals(normalValues[step].length, stage.size(),
                    "Aino N" + (step + 1) + " hit count");
            for (int hit = 0; hit < stage.size(); hit++) {
                assertClose(castTime + hitFrames[step][hit] * FRAME,
                        stage.get(hit).time,
                        "Aino N" + (step + 1) + " hitmark " + hit);
                assertClose(normalValues[step][hit],
                        stage.get(hit).action.getDamagePercent(),
                        "Aino N" + (step + 1) + " multiplier " + hit);
                assertEquals(Element.PHYSICAL,
                        stage.get(hit).action.getElement(),
                        "Aino Normal remains Physical");
                assertTrue(stage.get(hit).action.isShatterTrigger(),
                        "Aino claymore Normal is blunt");
            }
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Aino N" + (step + 1) + " recovery");
        }

        Aino skillAino = new Aino(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillAino);
        List<ActionRecord> skillRecords = captureAinoActions(
                skillSimulator);
        List<ParticleRecord> particles = captureHydroParticles(
                skillSimulator);
        performSkill(skillSimulator);
        List<ActionRecord> stageOne = named(
                skillRecords, "Musecatcher Stage 1");
        List<ActionRecord> stageTwo = named(
                skillRecords, "Musecatcher Stage 2");
        assertEquals(1, stageOne.size(),
                "Musecatcher stage one hit count");
        assertEquals(1, stageTwo.size(),
                "Musecatcher stage two hit count");
        assertClose(15.0 * FRAME, stageOne.get(0).time,
                "Musecatcher stage one hitmark");
        assertClose(33.0 * FRAME, stageTwo.get(0).time,
                "Musecatcher stage two hitmark");
        assertClose(1.1152,
                stageOne.get(0).action.getDamagePercent(),
                "Musecatcher C0 stage one multiplier");
        assertClose(3.2096,
                stageTwo.get(0).action.getDamagePercent(),
                "Musecatcher C0 stage two multiplier");
        assertTrue(!stageOne.get(0).action.isShatterTrigger(),
                "Musecatcher stage one is not blunt");
        assertTrue(stageTwo.get(0).action.isShatterTrigger(),
                "Musecatcher stage two is blunt");
        assertClose(52.0 * FRAME, skillSimulator.getCurrentTime(),
                "Musecatcher recovery");
        assertClose(10.0 - 39.0 * FRAME,
                skillAino.getSkillCDRemaining(
                        skillSimulator.getCurrentTime()),
                "Skill cooldown starts at frame 13");
        assertEquals(0, particles.size(),
                "Particles remain in flight after Skill recovery");
        advanceTo(skillSimulator, 15.0 * FRAME + 100.0 * FRAME);
        assertEquals(1, particles.size(),
                "Two Skill hits share one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Musecatcher particle count");
        assertClose(15.0 * FRAME + 100.0 * FRAME,
                particles.get(0).time,
                "Musecatcher particle travel time");

        Aino c5 = new Aino(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureAinoActions(c5Simulator);
        performSkill(c5Simulator);
        assertClose(1.312,
                named(c5Records, "Musecatcher Stage 1")
                        .get(0).action.getDamagePercent(),
                "C5 raises Musecatcher stage one");
        assertClose(3.776,
                named(c5Records, "Musecatcher Stage 2")
                        .get(0).action.getDamagePercent(),
                "C5 raises Musecatcher stage two");
    }

    private static void testParticlesC4AndNoEnemyBoundary() {
        Aino c4 = new Aino(null, null, 4);
        CombatSimulator simulator = simulatorWith(c4);
        c4.restoreCurrentEnergy(0.0);
        performSkill(simulator);
        assertClose(10.0, c4.getTotalFlatEnergy(),
                "C4 grants one flat-Energy packet per Skill");
        assertClose(10.0, c4.getCurrentEnergy(),
                "C4 Energy is immediate on the accepted first hit");
        advanceTo(simulator, 10.3);
        performSkill(simulator);
        assertClose(20.0, c4.getTotalFlatEnergy(),
                "C4 opens at the exact 10-second gate");

        Aino noEnemyAino = new Aino(null, null, 4);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyAino);
        List<ParticleRecord> particles = captureHydroParticles(noEnemy);
        noEnemyAino.restoreCurrentEnergy(0.0);
        performSkill(noEnemy);
        noEnemy.advanceTime(3.0);
        assertEquals(0, particles.size(),
                "No target suppresses Musecatcher particles");
        assertClose(0.0, noEnemyAino.getTotalFlatEnergy(),
                "No target suppresses C4 Energy");
    }

    private static void testDuckyCadenceA1A4AndC3() {
        Aino c0 = new Aino(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureAinoActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Burst spends 50 Energy at frame five");
        assertClose(5.0 * FRAME,
                c0.getBurstEnergyMarkers().get(0)[0],
                "Burst Energy marker uses frame five");
        assertClose(13.5 - 1.0,
                c0.getBurstCDRemaining(simulator.getCurrentTime()),
                "Burst cooldown starts at cast time");
        double firstHit = 123.0 * FRAME + 10.0 * FRAME;
        advanceTo(simulator, firstHit + EPSILON);
        ActionRecord first = named(records,
                "Cool Your Jets Ducky Water Ball").get(0);
        assertClose(firstHit, first.time,
                "Ducky first deterministic impact");
        assertClose(0.341904, first.action.getDamagePercent(),
                "C0 Ducky multiplier");
        assertEquals(ICDType.Standard, first.action.getICDType(),
                "Nascent Ducky uses standard ICD");
        assertEquals(ICDTag.ElementalBurst, first.action.getICDTag(),
                "Nascent Ducky uses Burst ICD tag");
        assertClose(48.0,
                first.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "A4 adds half of emission-time EM");
        assertTrue(c0.isDuckyActive(123.0 * FRAME),
                "Ducky C2 window starts at frame 123");
        advanceTo(simulator,
                123.0 * FRAME + 13.5 + 10.0 * FRAME + EPSILON);
        assertEquals(10, named(records,
                "Cool Your Jets Ducky Water Ball").size(),
                "Nascent Ducky emits ten deterministic balls");
        assertTrue(!c0.isDuckyActive(
                123.0 * FRAME + 14.0),
                "Ducky support window closes at 14 seconds");

        Aino c3 = new Aino(null, null, 3);
        CombatSimulator enhancedSimulator = simulatorWith(c3);
        enhancedSimulator.setMoonsign(
                CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        List<ActionRecord> enhancedRecords = captureAinoActions(
                enhancedSimulator);
        perform(enhancedSimulator, CharacterActionKey.BURST);
        advanceTo(enhancedSimulator,
                123.0 * FRAME + 14.0 + 10.0 * FRAME + EPSILON);
        List<ActionRecord> enhanced = named(
                enhancedRecords, "Cool Your Jets Ducky Water Ball");
        assertEquals(21, enhanced.size(),
                "A1 Ducky emits at the deterministic 0.7-second cadence");
        assertClose(0.7, enhanced.get(1).time - enhanced.get(0).time,
                "A1 Ducky impact interval");
        assertClose(0.40224,
                enhanced.get(0).action.getDamagePercent(),
                "C3 raises Ducky multiplier");
        assertEquals(ICDType.AinoDucky,
                enhanced.get(0).action.getICDType(),
                "A1 Ducky uses private 1.8-second ICD");
        assertEquals(ICDTag.Aino_Ducky,
                enhanced.get(0).action.getICDTag(),
                "A1 Ducky uses private typed tag");
    }

    private static void testC1C2AndC6Support() {
        Aino aino = new Aino(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, false);
        CombatSimulator simulator = simulatorWith(aino, ally);
        List<ActionRecord> records = captureAinoActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(176.0,
                aino.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C1 buffs Aino even while she is active");
        assertClose(0.0,
                ally.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C1 does not buff an inactive ally");
        simulator.setActiveCharacter(CharacterId.BENNETT);
        assertClose(80.0,
                ally.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C1 follows the active non-Aino character");
        StatsContainer nascentC6 = ally.getEffectiveStats(
                simulator.getCurrentTime());
        assertClose(0.15,
                nascentC6.get(StatType.ELECTRO_CHARGED_DMG_BONUS),
                "C6 grants standard Electro-Charged bonus");
        assertClose(0.15,
                nascentC6.get(StatType.BLOOM_DMG_BONUS),
                "C6 grants Bloom and Bountiful-Core bonus");
        assertClose(0.15,
                nascentC6.get(StatType.LUNAR_REACTION_DMG_BONUS_ALL),
                "C6 grants typed Lunar reaction bonus");
        simulator.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        assertClose(0.35,
                ally.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELECTRO_CHARGED_DMG_BONUS),
                "C6 Ascendant bonus is dynamic");

        double firstTrigger = 123.0 * FRAME + 0.02;
        advanceTo(simulator, firstTrigger);
        simulator.setActiveCharacter(CharacterId.AINO);
        performAllyHit(simulator, ally);
        simulator.setActiveCharacter(CharacterId.BENNETT);
        performAllyHit(simulator, ally);
        performAllyHit(simulator, ally);
        advanceTo(simulator, firstTrigger + 10.0 * FRAME + EPSILON);
        List<ActionRecord> c2Hits = named(
                records, "Cool Your Jets Ducky C2");
        assertEquals(1, c2Hits.size(),
                "C2 uses one five-second trigger gate");
        assertClose(0.25,
                c2Hits.get(0).action.getDamagePercent(),
                "C2 ATK multiplier");
        assertClose(264.0,
                c2Hits.get(0).action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "C2 captures 100 percent EM plus A4");
        assertEquals(ICDType.None,
                c2Hits.get(0).action.getICDType(),
                "C2 has no elemental application ICD");
        advanceTo(simulator, firstTrigger + 5.0);
        performAllyHit(simulator, ally);
        advanceTo(simulator,
                firstTrigger + 5.0 + 10.0 * FRAME + EPSILON);
        assertEquals(2, named(records,
                "Cool Your Jets Ducky C2").size(),
                "C2 reopens at exactly five seconds");

        advanceTo(simulator, 15.0);
        assertClose(0.0,
                ally.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C1 expires at 15 seconds");
        assertClose(0.0,
                ally.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELECTRO_CHARGED_DMG_BONUS),
                "C6 expires at 15 seconds");
    }

    private static void testFailClosedSnapshotAndIsolation() {
        Aino aino = new Aino(null, null, 0);
        CombatSimulator simulator = simulatorWith(aino);
        List<ActionRecord> records = captureAinoActions(simulator);
        aino.addBuff(new SimpleBuff(
                "Aino Snapshot ATK",
                5.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 2.1);
        assertEquals(1, aino.getPendingHitCount(),
                "First emitted Ducky ball remains in flight");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(0.2);
        ActionRecord original = named(records,
                "Cool Your Jets Ducky Water Ball").get(0);
        assertClose(1.0,
                original.action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                "Ducky impact preserves emission-time stats");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(0.2);
        List<ActionRecord> restored = named(records,
                "Cool Your Jets Ducky Water Ball");
        assertEquals(1, restored.size(),
                "Repeated rollback reconstructs one Ducky impact");
        assertClose(1.0,
                restored.get(0).action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                "Restored Ducky impact keeps its snapshot");

        assertThrows(IllegalArgumentException.class,
                () -> aino.onAction(
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD),
                        simulator),
                "Aino rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> aino.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.CHARGE),
                        simulator),
                "Aino rejects unsourced Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> aino.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.PLUNGE),
                        simulator),
                "Aino rejects unsourced Plunging Attack");
        assertThrows(IllegalArgumentException.class,
                () -> aino.onAction(null, simulator),
                "Aino rejects null action");
        assertTrue(!aino.isRandomPlacementRepresented(),
                "No random or multi-target placement is synthesized");
        assertTrue(!aino.isDefensiveStateRepresented(),
                "No unsupported defensive state is synthesized");
        assertThrows(IllegalArgumentException.class,
                () -> new Aino(null, null, -1),
                "Aino rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Aino(null, null, 7),
                "Aino rejects constellation above C6");

        Aino reused = new Aino(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Aino rejects cross-simulator reuse");
        Aino foreign = new Aino(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!aino.acceptsCharacterState(foreignState),
                "Aino rejects another instance's snapshot payload");
    }

    private static void performAllyHit(
            CombatSimulator simulator,
            TestCharacter ally) {
        AttackAction action = new AttackAction(
                "Aino C2 Test Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(), action);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
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
                CharacterId.AINO,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.AINO,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureAinoActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.AINO) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureHydroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> normalStage(
            List<ActionRecord> records,
            int step) {
        String prefix = "Bish-Bash-Bosh Repair N" + (step + 1);
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
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
            assertTrue(lines.get(index).startsWith("Aino,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Aino/Aino_Status.csv",
                "config/characters/Aino/Aino_Multipliers.csv"
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
        throw new AssertionError("Aino CSVs missing key " + key);
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
