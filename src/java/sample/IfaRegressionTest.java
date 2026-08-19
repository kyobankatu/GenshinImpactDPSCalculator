package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Ifa;
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

/** Focused regression checks for Ifa's fixed-target offensive slice. */
public final class IfaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private IfaRegressionTest() {
    }

    /** Runs data, action, particle, constellation, boundary, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityAndData();
        testGroundedActionsAndTiming();
        testBlessingTonicParticlesAndC1();
        testC3C6AndLocalIcdSequence();
        testBurstSedationMarkAndC4();
        testNaturalAndEarlyBlessingExit();
        testSnapshotFailClosedAndIsolation();
        System.out.println("IfaRegressionTest passed");
    }

    private static void testIdentityAndData() throws IOException {
        Ifa ifa = new Ifa(null, null, 6);
        assertEquals(CharacterId.IFA, ifa.getCharacterId(),
                "Ifa typed identity");
        assertEquals(CharacterId.IFA, CharacterId.fromName("Ifa"),
                "Ifa name lookup");
        assertEquals(CharacterId.IFA, CharacterId.fromNumericId(108),
                "Ifa numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.IFA.getRegion(),
                "Ifa region");
        assertEquals(Element.ANEMO, ifa.getElement(),
                "Ifa element");
        assertClose(10081.0,
                ifa.getBaseStats().get(StatType.BASE_HP),
                "Ifa base HP");
        assertClose(178.0,
                ifa.getBaseStats().get(StatType.BASE_ATK),
                "Ifa base ATK");
        assertClose(605.0,
                ifa.getBaseStats().get(StatType.BASE_DEF),
                "Ifa base DEF");
        assertClose(96.0,
                ifa.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Ifa ascension EM");
        assertClose(60.0, ifa.getEnergyCost(),
                "Ifa Energy cost");
        assertClose(7.5, ifa.getSkillCD(),
                "Ifa Skill cooldown");
        assertClose(15.0, ifa.getBurstCD(),
                "Ifa Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.IFA,
                    new Ifa(null, null, constellation).getCharacterId(),
                    "Ifa explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Ifa/Ifa_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Ifa/Ifa_Multipliers.csv"), 34);
        assertCsvValue("Tonic Shot C3", 2.6672);
        assertCsvValue("Compound Sedation Field C5", 10.1696);
        assertCsvValue("C6 Extra Shot", 1.2);
    }

    private static void testGroundedActionsAndTiming() {
        Ifa ifa = deterministicIfa(0, 0.9, 0.9);
        CombatSimulator simulator = simulatorWith(ifa);
        List<ActionRecord> records = captureIfaActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.CHARGE);

        assertEquals(4, records.size(),
                "Three Normals and one Charged impact");
        assertAction(records.get(0),
                "Rite of Dispelling Winds N1",
                0.911322,
                10.0 * FRAME,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        assertAction(records.get(1),
                "Rite of Dispelling Winds N2",
                0.806942,
                40.0 * FRAME,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        assertAction(records.get(2),
                "Rite of Dispelling Winds N3",
                1.270893,
                100.0 * FRAME,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
        assertAction(records.get(3),
                "Rite of Dispelling Winds Charged Attack",
                2.499680,
                193.0 * FRAME,
                ICDType.None,
                ICDTag.None,
                1.0);
        assertClose((27.0 + 31.0 + 90.0 + 86.0) * FRAME,
                simulator.getCurrentTime(),
                "Grounded action durations");
    }

    private static void testBlessingTonicParticlesAndC1() {
        Ifa ifa = deterministicIfa(1, 0.9, 0.9);
        CombatSimulator simulator = simulatorWith(ifa);
        List<ActionRecord> records = captureIfaActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        ifa.restoreCurrentEnergy(0.0);

        performSkill(simulator);
        assertTrue(ifa.isNightsoulBlessingActive(
                simulator.getCurrentTime()),
                "Press Skill enters owner Blessing");
        assertClose(76.0,
                ifa.getNightsoulPoints(simulator.getCurrentTime()),
                "Five drain ticks resolve by frame 31");
        assertClose(598.0 * FRAME,
                ifa.getNightsoulExpirationTime(),
                "Blessing natural end frame");
        assertClose(0.0,
                ifa.getSkillCDRemaining(simulator.getCurrentTime()),
                "Skill cooldown is deferred until exit");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord first = named(records, "Tonic Shot").get(0);
        assertClose(32.0 * FRAME, first.time,
                "Hold Tonic Shot release frame");
        assertClose(2.267120, first.action.getDamagePercent(),
                "C1 uses talent-nine Tonic multiplier");
        assertClose(6.0, ifa.getTotalFlatEnergy(),
                "C1 grants flat Energy on accepted Tonic hit");
        assertEquals(0, particles.size(),
                "Particle packet remains in flight");
        advanceTo(simulator, first.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "First Tonic hit emits one packet");
        assertClose(4.0, particles.get(0).count,
                "High particle draw emits four particles");
        assertClose(first.time + 100.0 * FRAME,
                particles.get(0).time,
                "Particle travel uses one hundred frames");

        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(6.0, ifa.getTotalFlatEnergy(),
                "C1 is closed before eight seconds");
        advanceTo(simulator,
                first.time + 8.0 - 3.0 * FRAME);
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(12.0, ifa.getTotalFlatEnergy(),
                "C1 opens at the exact eight-second boundary");
        assertEquals(1, particles.size(),
                "One Blessing emits at most one particle packet");

        Ifa noEnemyIfa = deterministicIfa(1, 0.0, 0.9);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyIfa);
        List<ParticleRecord> noEnemyParticles =
                captureAnemoParticles(noEnemy);
        noEnemyIfa.restoreCurrentEnergy(0.0);
        performSkill(noEnemy);
        perform(noEnemy, CharacterActionKey.NORMAL);
        noEnemy.advanceTime(3.0);
        assertClose(0.0, noEnemyIfa.getTotalFlatEnergy(),
                "No target suppresses C1 Energy");
        assertEquals(0, noEnemyParticles.size(),
                "No target suppresses particles");
    }

    private static void testC3C6AndLocalIcdSequence() {
        Ifa ifa = deterministicIfa(6, 0.0, 0.0);
        CombatSimulator simulator = simulatorWith(ifa);
        List<ActionRecord> records = captureIfaActions(simulator);
        performSkill(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.CHARGE);

        List<ActionRecord> tonic = named(records, "Tonic Shot");
        List<ActionRecord> extra = named(records, "Tonic Shot C6");
        assertEquals(2, tonic.size(),
                "Normal and Charged inputs emit primary Tonic shots");
        assertEquals(1, extra.size(),
                "Successful C6 draw emits one extra shot");
        assertClose(2.667200,
                tonic.get(0).action.getDamagePercent(),
                "C3 raises Tonic Shot");
        assertClose(1.2,
                extra.get(0).action.getDamagePercent(),
                "C6 extra shot scales from total ATK");
        assertClose(FRAME,
                extra.get(0).time - tonic.get(0).time,
                "C6 shot follows one frame later");
        assertEquals(ICDType.None,
                tonic.get(0).action.getICDType(),
                "Private sequence is locally encoded without shared ICD");
        assertEquals(ICDTag.ElementalSkill,
                tonic.get(0).action.getICDTag(),
                "Tonic actions retain typed Skill grouping metadata");
        assertClose(1.0, tonic.get(0).action.getGaugeUnits(),
                "First Tonic shot applies Anemo");
        assertClose(0.0, extra.get(0).action.getGaugeUnits(),
                "Second Tonic shot is suppressed");
        assertClose(1.0, tonic.get(1).action.getGaugeUnits(),
                "Third Tonic shot applies Anemo");
    }

    private static void testBurstSedationMarkAndC4() {
        Ifa ifa = deterministicIfa(6, 0.9, 0.9);
        CombatSimulator simulator = simulatorWith(ifa);
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        List<ActionRecord> records = captureIfaActions(simulator);
        perform(simulator, CharacterActionKey.BURST);

        ActionRecord field = named(
                records, "Compound Sedation Field").get(0);
        ActionRecord mark = named(records, "Sedation Mark").get(0);
        assertClose(41.0 * FRAME, field.time,
                "Burst initial hit frame");
        assertClose(10.169600, field.action.getDamagePercent(),
                "C5 raises Burst initial hit");
        assertClose(79.0 * FRAME, mark.time,
                "Sedation Mark resolves 38 frames after detection");
        assertClose(2.179200, mark.action.getDamagePercent(),
                "C5 raises Sedation Mark");
        assertEquals(Element.PYRO, mark.action.getElement(),
                "Sedation Mark captures prioritized target Aura");
        assertClose(96.0,
                field.action.getStatSnapshot().get(
                        StatType.ELEMENTAL_MASTERY),
                "Burst cast snapshot precedes C4");
        assertClose(196.0,
                mark.action.getStatSnapshot().get(
                        StatType.ELEMENTAL_MASTERY),
                "Sedation detection snapshot includes C4");
        assertClose(0.0, ifa.getCurrentEnergy(),
                "Burst spends sixty Energy at frame four");
        assertClose(4.0 * FRAME,
                ifa.getBurstEnergyMarkers().get(0)[0],
                "Burst Energy marker uses frame four");
        assertTrue(ifa.isC4Active(simulator.getCurrentTime()),
                "C4 is active after Burst");
        assertClose(196.0,
                ifa.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C4 grants one hundred owner EM");
        advanceTo(simulator, 15.0);
        assertTrue(!ifa.isC4Active(15.0),
                "C4 closes at the half-open boundary");
        assertClose(96.0,
                ifa.getEffectiveStats(15.0).get(
                        StatType.ELEMENTAL_MASTERY),
                "C4 EM expires at fifteen seconds");

        Ifa noAuraIfa = deterministicIfa(0, 0.9, 0.9);
        CombatSimulator noAura = simulatorWith(noAuraIfa);
        List<ActionRecord> noAuraRecords = captureIfaActions(noAura);
        perform(noAura, CharacterActionKey.BURST);
        assertEquals(1, noAuraRecords.size(),
                "No supported Aura suppresses Sedation Mark");
    }

    private static void testNaturalAndEarlyBlessingExit() {
        Ifa natural = deterministicIfa(0, 0.9, 0.9);
        CombatSimulator naturalSimulator = simulatorWith(natural);
        performSkill(naturalSimulator);
        advanceTo(naturalSimulator, 598.0 * FRAME + EPSILON);
        assertTrue(!natural.isNightsoulBlessingActive(
                naturalSimulator.getCurrentTime()),
                "Hundredth drain tick ends Blessing");
        assertClose(0.0,
                natural.getNightsoulPoints(
                        naturalSimulator.getCurrentTime()),
                "Natural exit clears Nightsoul points");
        assertTrue(natural.getSkillCDRemaining(
                naturalSimulator.getCurrentTime()) > 7.49,
                "Natural exit starts the 7.5-second cooldown");

        Ifa early = deterministicIfa(0, 0.9, 0.9);
        CombatSimulator earlySimulator = simulatorWith(early);
        performSkill(earlySimulator);
        double recastTime = earlySimulator.getCurrentTime();
        performSkill(earlySimulator);
        assertTrue(!early.isNightsoulBlessingActive(
                earlySimulator.getCurrentTime()),
                "Skill recast exits Blessing");
        assertClose(7.5 - 44.0 * FRAME,
                early.getSkillCDRemaining(
                        earlySimulator.getCurrentTime()),
                "Early exit starts cooldown at recast time");
        assertTrue(recastTime < earlySimulator.getCurrentTime(),
                "Early exit consumes its 44-frame action");
    }

    private static void testSnapshotFailClosedAndIsolation() {
        Ifa ifa = deterministicIfa(4, 0.9, 0.9);
        CombatSimulator simulator = simulatorWith(ifa);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(ifa.getPendingCommandCount() >= 2,
                "Blessing end and particle packet remain pending");
        SimulatorSnapshot pending = simulator.saveSnapshot();
        simulator.advanceTime(1.0);
        assertEquals(1, particles.size(),
                "Original future particle packet resolves once");
        simulator.restoreSnapshot(pending);
        simulator.restoreSnapshot(pending);
        particles.clear();
        simulator.advanceTime(1.0);
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs one particle packet");

        Ifa c4 = deterministicIfa(4, 0.9, 0.9);
        CombatSimulator c4Simulator = simulatorWith(c4);
        perform(c4Simulator, CharacterActionKey.BURST);
        SimulatorSnapshot c4Snapshot = c4Simulator.saveSnapshot();
        advanceTo(c4Simulator, 15.1);
        assertTrue(!c4.isC4Active(c4Simulator.getCurrentTime()),
                "C4 expires before rollback");
        c4Simulator.restoreSnapshot(c4Snapshot);
        assertTrue(c4.isC4Active(c4Simulator.getCurrentTime()),
                "Snapshot restores C4 owner state");

        assertThrows(IllegalArgumentException.class,
                () -> ifa.onAction(
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD),
                        simulator),
                "Ifa rejects movement-dependent Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> ifa.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.PLUNGE),
                        simulator),
                "Ifa rejects plunge geometry");
        assertThrows(IllegalArgumentException.class,
                () -> ifa.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.DASH),
                        simulator),
                "Ifa rejects movement-only Dash");
        assertThrows(IllegalArgumentException.class,
                () -> ifa.onAction(null, simulator),
                "Ifa rejects null action");
        assertTrue(!ifa.isHealingRepresented(),
                "Healing is fail-closed");
        assertTrue(!ifa.isMovementStateRepresented(),
                "Movement and falling are fail-closed");
        assertTrue(!ifa.isMultiTargetGeometryRepresented(),
                "Geometry and multiple targets are fail-closed");
        assertTrue(!ifa.isTeamNightsoulPlumbingRepresented(),
                "Team Nightsoul plumbing is fail-closed");
        assertThrows(IllegalArgumentException.class,
                () -> new Ifa(null, null, -1),
                "Ifa rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Ifa(null, null, 7),
                "Ifa rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Ifa(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null,
                        () -> 0.0),
                "Ifa rejects a null random source");

        Ifa invalidC6 = deterministicIfa(6, 0.9, 1.0);
        CombatSimulator invalidC6Simulator = simulatorWith(invalidC6);
        performSkill(invalidC6Simulator);
        assertThrows(IllegalStateException.class,
                () -> perform(
                        invalidC6Simulator,
                        CharacterActionKey.NORMAL),
                "Ifa validates C6 random draws");

        Ifa reused = deterministicIfa(0, 0.9, 0.9);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Ifa rejects cross-simulator reuse");
        Ifa foreign = deterministicIfa(0, 0.9, 0.9);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!ifa.acceptsCharacterState(foreignState),
                "Ifa rejects another instance's snapshot payload");
    }

    private static Ifa deterministicIfa(
            int constellation,
            double particleDraw,
            double c6Draw) {
        return new Ifa(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw,
                () -> c6Draw);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.IFA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.IFA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureIfaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.IFA) {
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
            assertTrue(lines.get(index).startsWith("Ifa,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Ifa/Ifa_Status.csv",
                "config/characters/Ifa/Ifa_Multipliers.csv"
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
        throw new AssertionError("Ifa CSVs missing key " + key);
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
}
