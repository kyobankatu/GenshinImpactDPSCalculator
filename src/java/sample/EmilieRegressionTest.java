package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Emilie;
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

/** Focused regression checks for Emilie's fixed-target Lumidouce slice. */
public final class EmilieRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private EmilieRegressionTest() {
    }

    /** Runs data, Case, scent, Burst, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalBasicsAndSkillCadence();
        testBurningScentsA1A4AndC2();
        testBurstReplacementAndConstellations();
        testC6AndFailClosedScope();
        testSnapshotAndIsolationGuards();
        System.out.println("EmilieRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Emilie emilie = new Emilie(null, null, 6);
        assertEquals(CharacterId.EMILIE, emilie.getCharacterId(),
                "Emilie typed identity");
        assertEquals(CharacterId.EMILIE, CharacterId.fromName("Emilie"),
                "Emilie name lookup");
        assertEquals(CharacterId.EMILIE,
                CharacterId.fromNumericId(79),
                "Emilie numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.EMILIE.getRegion(),
                "Emilie region");
        assertEquals(Element.DENDRO, emilie.getElement(),
                "Emilie element");
        assertClose(13568.0,
                emilie.getBaseStats().get(StatType.BASE_HP),
                "Emilie base HP");
        assertClose(335.0,
                emilie.getBaseStats().get(StatType.BASE_ATK),
                "Emilie base ATK");
        assertClose(730.0,
                emilie.getBaseStats().get(StatType.BASE_DEF),
                "Emilie base DEF");
        assertClose(0.884,
                emilie.getBaseStats().get(StatType.CRIT_DMG),
                "Emilie base plus ascension CRIT DMG");
        assertClose(50.0, emilie.getEnergyCost(),
                "Emilie Energy cost");
        assertClose(14.0, emilie.getSkillCD(),
                "Emilie Skill cooldown");
        assertClose(13.5, emilie.getBurstCD(),
                "Emilie Burst cooldown");
        assertTrue(emilie.isA4BurningBonusRepresented(),
                "A4 has typed Burning state");
        assertTrue(!emilie.isArkheRepresented(),
                "Arkhe is explicitly excluded");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.EMILIE,
                    new Emilie(null, null, constellation).getCharacterId(),
                    "Emilie explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Emilie/Emilie_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Emilie/Emilie_Multipliers.csv"), 35);
        assertCsvValue("Lumidouce Case Level 2 C3", 1.68);
        assertCsvValue("Lumidouce Case Level 3 C5", 4.344);
        assertCsvValue("C4 Burst Target Gate", 0.4);
        ICDManager icdManager = new ICDManager();
        assertTrue(icdManager.checkApplication(
                "EMILIE",
                ICDTag.Emilie_Lumidouce,
                ICDType.EmilieLumidouce,
                0.0),
                "First Lumidouce application passes");
        assertTrue(!icdManager.checkApplication(
                "EMILIE",
                ICDTag.Emilie_Lumidouce,
                ICDType.EmilieLumidouce,
                1.5),
                "Lumidouce private ICD blocks before two seconds");
        assertTrue(icdManager.checkApplication(
                "EMILIE",
                ICDTag.Emilie_Lumidouce,
                ICDType.EmilieLumidouce,
                2.0),
                "Lumidouce private ICD opens at two seconds");
    }

    private static void testPhysicalBasicsAndSkillCadence() {
        Emilie emilie = new Emilie(null, null, 0);
        CombatSimulator simulator = simulatorWith(emilie);
        List<ActionRecord> records = captureActions(simulator);
        double[] normalValues = {
            0.892163, 0.824823, 1.089473, 1.379798
        };
        int[] hitFrames = { 11, 16, 33, 34 };
        int[] durations = { 20, 19, 40, 70 };
        int[] hitlagFrames = { 8, 8, 8, 9 };
        for (int step = 0; step < normalValues.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord normal = named(records,
                    "Shadow-Hunting Spear N" + (step + 1)).get(0);
            assertClose(castTime + hitFrames[step] * FRAME,
                    normal.time,
                    "Emilie N" + (step + 1) + " hitmark");
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Emilie N" + (step + 1) + " recovery");
            assertClose(normalValues[step],
                    normal.action.getDamagePercent(),
                    "Emilie N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL, normal.action.getElement(),
                    "Emilie Normal is Physical outside C6");
        }

        records.clear();
        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Shadow-Hunting Spear Charged Attack").get(0);
        assertClose(chargedCast + 26.0 * FRAME, charged.time,
                "Emilie Charged hitmark");
        assertClose(1.67796, charged.action.getDamagePercent(),
                "Emilie Charged multiplier");
        assertEquals(Element.PHYSICAL, charged.action.getElement(),
                "Emilie Charged is Physical outside C6");

        records.clear();
        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Shadow-Hunting Spear High Plunge").get(0);
        assertClose(plungeCast + 49.0 * FRAME, plunge.time,
                "Emilie high Plunge hitmark");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Emilie high Plunge multiplier");

        Emilie skillEmilie = new Emilie(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillEmilie);
        List<ActionRecord> skillRecords = captureActions(skillSimulator);
        List<ParticleRecord> particles = captureDendroParticles(
                skillSimulator);
        performSkill(skillSimulator);
        assertEquals(1, skillEmilie.getCaseLevel(
                skillSimulator.getCurrentTime()),
                "Skill spawns level-1 Case at frame 16");
        assertClose(16.0 * FRAME + 22.0,
                skillEmilie.getCaseExpirationTime(),
                "Case has exact 22-second duration");
        ActionRecord summon = named(skillRecords,
                "Fragrance Extraction: Lumidouce Case Summon").get(0);
        assertClose(37.0 * FRAME, summon.time,
                "Skill summon hitmark");
        assertClose(0.80036, summon.action.getDamagePercent(),
                "Skill summon multiplier");
        advanceTo(skillSimulator, 2.0);
        ActionRecord caseHit = named(skillRecords,
                "Lumidouce Case Level 1").get(0);
        assertClose(16.0 * FRAME + 1.5 + 5.0 * FRAME,
                caseHit.time,
                "Case attack emission and travel cadence");
        assertEquals(ICDType.EmilieLumidouce,
                caseHit.action.getICDType(),
                "Case uses private two-second ICD");
        assertEquals(ICDTag.Emilie_Lumidouce,
                caseHit.action.getICDTag(),
                "Case uses private ICD tag");
        advanceTo(skillSimulator, caseHit.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Accepted Case hit emits one particle packet");
        assertClose(1.0, particles.get(0).count,
                "Case particle count");
        advanceTo(skillSimulator, caseHit.time + 2.4);
        assertEquals(1, particles.size(),
                "Particle gate suppresses sub-2.5-second Case hit");
        advanceTo(skillSimulator, caseHit.time + 5.0);
        assertEquals(2, particles.size(),
                "Particle gate reopens after 2.5 seconds");
        advanceTo(skillSimulator,
                skillEmilie.getCaseExpirationTime() + EPSILON);
        assertEquals(0, skillEmilie.getCaseLevel(
                skillSimulator.getCurrentTime()),
                "Case expires at the 22-second boundary");
    }

    private static void testBurningScentsA1A4AndC2() {
        Emilie emilie = new Emilie(null, null, 0);
        CombatSimulator simulator = simulatorWith(emilie);
        List<ActionRecord> records = captureActions(simulator);
        startLongBurning(simulator, CharacterId.EMILIE);
        performSkill(simulator);
        advanceTo(simulator, 2.8);
        assertEquals(2, emilie.getCaseLevel(simulator.getCurrentTime()),
                "Two Burning scents promote Case to level 2");
        advanceTo(simulator, 4.7);
        startLongBurning(simulator, CharacterId.EMILIE);
        advanceTo(simulator, 6.7);
        startLongBurning(simulator, CharacterId.EMILIE);
        advanceTo(simulator, 7.2);
        assertEquals(1, named(records, "Cleardew Cologne").size(),
                "Two more scents under refreshed Burning trigger one Cleardew proc");
        ActionRecord cleardew = named(records, "Cleardew Cologne").get(0);
        assertClose(6.0, cleardew.action.getDamagePercent(),
                "Cleardew has 600 percent ATK multiplier");
        assertClose(0.05025,
                cleardew.action.getExtraBonuses().get(
                        StatType.DMG_BONUS_ALL),
                "A4 adds 15 percent per 1000 ATK against Burning target");

        Emilie c1 = new Emilie(null, null, 1);
        TestCharacter dendroAlly = new TestCharacter(
                CharacterId.NAHIDA, Element.DENDRO);
        CombatSimulator c1Simulator = simulatorWith(c1, dendroAlly);
        startLongBurning(c1Simulator, CharacterId.EMILIE);
        performTestDendroHit(c1Simulator, dendroAlly);
        performTestDendroHit(c1Simulator, dendroAlly);
        assertEquals(1, c1.getScentCount(),
                "C1 Dendro-damage scent obeys 2.9-second gate");
        c1Simulator.advanceTime(3.0);
        c1Simulator.notifyReaction(
                ReactionResult.state(
                        "Burning",
                        ReactionResult.Kind.BURNING,
                        Element.PYRO),
                dendroAlly);
        assertEquals(2, c1.getScentCount(),
                "C1 Burning reaction uses the shared scent gate");

        Emilie c2 = new Emilie(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        performSkill(c2Simulator);
        ActionRecord c2Summon = named(c2Records,
                "Fragrance Extraction: Lumidouce Case Summon").get(0);
        assertClose(0.2,
                c2Summon.action.getExtraBonuses().get(
                        StatType.DMG_BONUS_ALL),
                "C1 grants 20 percent Skill damage");
        Buff shred = typedBuff(c2Simulator,
                BuffId.EMILIE_C2_DENDRO_RES_SHRED);
        StatsContainer shredStats = new StatsContainer();
        shred.apply(shredStats, c2Simulator.getCurrentTime());
        assertClose(0.3,
                shredStats.get(StatType.DENDRO_RES_SHRED),
                "C2 applies typed 30 percent Dendro resistance shred");
    }

    private static void testBurstReplacementAndConstellations() {
        Emilie c0 = new Emilie(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        startLongBurning(c0Simulator, CharacterId.EMILIE);
        performSkill(c0Simulator);
        advanceTo(c0Simulator, 2.8);
        c0Simulator.clearBurning();
        double burstCast = c0Simulator.getCurrentTime();
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(3.0, c0.getCurrentEnergy(),
                "Burst spends 50 Energy before one in-flight particle lands");
        assertClose(100.0,
                c0.getBurstEnergyMarkers().get(0)[1],
                "Burst spends from a full 50-Energy bar");
        assertEquals(3, c0.getCaseLevel(c0Simulator.getCurrentTime()),
                "Burst replaces Case with level 3 at frame 96");
        assertClose(13.5 - 111.0 * FRAME,
                c0.getBurstCDRemaining(c0Simulator.getCurrentTime()),
                "Burst cooldown starts at cast time");
        double burstSpawn = burstCast + 96.0 * FRAME;
        advanceTo(c0Simulator, burstSpawn + 2.8 + EPSILON);
        assertEquals(2, c0.getCaseLevel(c0Simulator.getCurrentTime()),
                "Burst restores the prior level-2 Case after 2.8 seconds");
        assertEquals(3, named(c0Records,
                "Aromatic Explication: Lumidouce Case Level 3").size(),
                "C0 fixed target passes the 0.7-second gate three times");

        Emilie c4 = new Emilie(null, null, 5);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        perform(c4Simulator, CharacterActionKey.BURST);
        double c4Spawn = 96.0 * FRAME;
        advanceTo(c4Simulator, c4Spawn + 4.8 + EPSILON);
        List<ActionRecord> c4Hits = named(c4Records,
                "Aromatic Explication: Lumidouce Case Level 3");
        assertEquals(8, c4Hits.size(),
                "C4 extends Burst and adapts the fixed-target gate");
        assertClose(4.344,
                c4Hits.get(0).action.getDamagePercent(),
                "C5 raises level-3 Case multiplier");

        Emilie c3 = new Emilie(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator);
        advanceTo(c3Simulator, 2.0);
        assertClose(0.9416,
                named(c3Records,
                        "Fragrance Extraction: Lumidouce Case Summon")
                        .get(0).action.getDamagePercent(),
                "C3 raises Skill summon multiplier");
        assertClose(0.792,
                named(c3Records, "Lumidouce Case Level 1")
                        .get(0).action.getDamagePercent(),
                "C3 raises Case multiplier");
    }

    private static void testC6AndFailClosedScope() {
        Emilie c6 = new Emilie(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        for (int index = 0; index < 5; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        for (int index = 0; index < 4; index++) {
            ActionRecord enhanced = named(records,
                    "Shadow-Hunting Spear N" + (index + 1)).get(0);
            assertEquals(Element.DENDRO, enhanced.action.getElement(),
                    "C6 infuses represented Normal " + (index + 1));
            assertClose(1005.0,
                    enhanced.action.getStatSnapshot().get(
                            StatType.FLAT_DMG_BONUS),
                    "C6 adds 300 percent of captured ATK");
        }
        assertEquals(4, c6.getC6ScentCount(),
                "C6 stops after four generated scents");
        ActionRecord fifth = named(records,
                "Shadow-Hunting Spear N1").get(1);
        assertEquals(Element.PHYSICAL, fifth.action.getElement(),
                "C6 four-scent cap ends infusion before fifth hit");
        assertClose(0.0, fifth.action.getStatSnapshot().get(
                StatType.FLAT_DMG_BONUS),
                "Fifth Normal has no C6 flat damage");

        Emilie chargedC6 = new Emilie(null, null, 6);
        CombatSimulator chargedSimulator = simulatorWith(chargedC6);
        List<ActionRecord> chargedRecords = captureActions(chargedSimulator);
        performSkill(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(chargedRecords,
                "Shadow-Hunting Spear Charged Attack").get(0);
        assertEquals(Element.DENDRO, charged.action.getElement(),
                "C6 infuses represented Charged Attack");
        assertClose(1005.0,
                charged.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "C6 Charged Attack receives 300 percent ATK flat damage");

        assertThrows(IllegalArgumentException.class,
                () -> c6.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Emilie rejects unsupported Hold Skill");
        assertTrue(!c6.isArkheRepresented(),
                "No Arkhe hit is synthesized");
    }

    private static void testSnapshotAndIsolationGuards() {
        Emilie emilie = new Emilie(null, null, 0);
        CombatSimulator simulator = simulatorWith(emilie);
        List<ActionRecord> records = captureActions(simulator);
        emilie.addBuff(new SimpleBuff(
                "Snapshot ATK",
                2.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        performSkill(simulator);
        advanceTo(simulator, 16.0 * FRAME + 1.5 + EPSILON);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(0.2);
        ActionRecord original = named(records,
                "Lumidouce Case Level 1").get(0);
        assertClose(1.0,
                original.action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Pending Case hit owns emission-time stats");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(0.2);
        assertEquals(1, named(records,
                "Lumidouce Case Level 1").size(),
                "Repeated restore reconstructs pending Case hit once");
        assertClose(1.0,
                named(records, "Lumidouce Case Level 1").get(0)
                        .action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Restored pending hit preserves its snapshot");

        assertThrows(IllegalArgumentException.class,
                () -> new Emilie(null, null, -1),
                "Emilie rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Emilie(null, null, 7),
                "Emilie rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> emilie.onAction(null, simulator),
                "Emilie rejects null action");
        Emilie reused = new Emilie(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Emilie rejects cross-simulator reuse");
        Emilie foreign = new Emilie(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!emilie.acceptsCharacterState(foreignState),
                "Emilie rejects another instance's snapshot payload");
    }

    private static void performTestDendroHit(
            CombatSimulator simulator,
            TestCharacter actor) {
        AttackAction action = new AttackAction(
                "Test Dendro Hit",
                1.0,
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.DENDRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
    }

    private static void startLongBurning(
            CombatSimulator simulator,
            CharacterId ownerId) {
        simulator.startBurning(ownerId, 100.0, 100.0, 1.0);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
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
                CharacterId.EMILIE,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.EMILIE,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.EMILIE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureDendroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static Buff typedBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        throw new AssertionError("Missing typed buff " + id);
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
            assertTrue(lines.get(index).startsWith("Emilie,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Emilie/Emilie_Status.csv",
                "config/characters/Emilie/Emilie_Multipliers.csv"
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
        throw new AssertionError("Emilie CSVs missing key " + key);
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
        private TestCharacter(
                CharacterId id,
                Element characterElement) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
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
