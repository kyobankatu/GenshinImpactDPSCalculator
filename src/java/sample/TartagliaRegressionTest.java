package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.character.Tartaglia;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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

/** Focused regression checks for Tartaglia's fixed-target melee stance slice. */
public final class TartagliaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private TartagliaRegressionTest() {
    }

    /** Runs data, stance, Riptide, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testRangedBasicsAndRiptideFlash();
        testMeleeStanceSlashParticlesAndCooldown();
        testBurstsConstellationsAndC4();
        testSnapshotSwitchAndReuseGuards();
        System.out.println("TartagliaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Tartaglia tartaglia = new Tartaglia(null, null, 6);
        assertEquals(CharacterId.TARTAGLIA, tartaglia.getCharacterId(),
                "Tartaglia typed identity");
        assertEquals(CharacterId.TARTAGLIA,
                CharacterId.fromName("Tartaglia"),
                "Tartaglia name lookup");
        assertEquals(CharacterId.TARTAGLIA,
                CharacterId.fromNumericId(75),
                "Tartaglia numeric lookup");
        assertEquals(CharacterRegion.SNEZHNAYA,
                CharacterId.TARTAGLIA.getRegion(),
                "Tartaglia region");
        assertEquals(Element.HYDRO, tartaglia.getElement(),
                "Tartaglia element");
        assertClose(13103.0,
                tartaglia.getBaseStats().get(StatType.BASE_HP),
                "Tartaglia base HP");
        assertClose(301.0,
                tartaglia.getBaseStats().get(StatType.BASE_ATK),
                "Tartaglia base ATK");
        assertClose(815.0,
                tartaglia.getBaseStats().get(StatType.BASE_DEF),
                "Tartaglia base DEF");
        assertClose(0.288,
                tartaglia.getBaseStats().get(StatType.HYDRO_DMG_BONUS),
                "Tartaglia ascension Hydro bonus");
        assertClose(60.0, tartaglia.getEnergyCost(),
                "Tartaglia Energy cost");
        assertClose(15.0, tartaglia.getBurstCD(),
                "Tartaglia Burst cooldown");
        assertTrue(!tartaglia.isA4CritApplicationRepresented(),
                "Tartaglia A4 crit callback is explicitly excluded");
        assertTrue(!tartaglia.isPartyTalentPassiveRepresented(),
                "Tartaglia party talent passive is explicitly excluded");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.TARTAGLIA,
                    new Tartaglia(null, null, constellation)
                            .getCharacterId(),
                    "Tartaglia explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Tartaglia/Tartaglia_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Tartaglia/Tartaglia_Multipliers.csv"), 43);
        assertCsvValue("Melee N6-2 C3", 0.849720);
        assertCsvValue("Melee Burst C5", 9.280000);
        assertCsvValue("C4 Interval", 3.9);
    }

    private static void testRangedBasicsAndRiptideFlash() {
        Tartaglia tartaglia = new Tartaglia(null, null, 0);
        CombatSimulator simulator = simulatorWith(tartaglia);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            0.758400, 0.850040, 1.017520,
            1.047540, 1.118640, 1.336680
        };
        int[] hitFrames = { 27, 18, 25, 29, 21, 24 };
        int[] durations = { 26, 27, 33, 32, 33, 66 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Tartaglia ranged Normal recovery");
            if (hitFrames[step] > durations[step]) {
                simulator.advanceTime(
                        (hitFrames[step] - durations[step]) * FRAME
                                + EPSILON);
            }
            ActionRecord record = records.get(step);
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Tartaglia ranged Normal multiplier");
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Tartaglia ranged Normal element");
            assertEquals(ActionType.NORMAL, record.action.getActionType(),
                    "Tartaglia ranged Normal category");
        }

        records.clear();
        double firstAim = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        simulator.advanceTime(4.0 * FRAME);
        ActionRecord charged = named(records,
                "Cutting Torrent Fully-Charged Aimed Shot").get(0);
        assertClose(firstAim + 96.0 * FRAME, charged.time,
                "Tartaglia full aim hitmark includes default travel");
        assertClose(2.108000, charged.action.getDamagePercent(),
                "Tartaglia full aim multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Tartaglia full aim has no application ICD");
        assertTrue(tartaglia.isRiptideActive(simulator.getCurrentTime()),
                "First full aim applies Riptide");
        assertClose(charged.time + 18.0,
                tartaglia.getRiptideExpirationTime(),
                "A1 extends Riptide from ten to eighteen seconds");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        simulator.advanceTime(4.0 * FRAME);
        assertEquals(3, namedPrefix(records, "Riptide Flash").size(),
                "Marked full aim triggers three Riptide Flash hits");
        for (ActionRecord flash : namedPrefix(records, "Riptide Flash")) {
            assertClose(0.210800, flash.action.getDamagePercent(),
                    "Riptide Flash multiplier");
            assertClose(0.0, flash.action.getGaugeUnits(),
                    "Private Flash aura ICD is fail-closed");
        }
    }

    private static void testMeleeStanceSlashParticlesAndCooldown() {
        Tartaglia tartaglia = new Tartaglia(null, null, 3);
        CombatSimulator simulator = simulatorWith(tartaglia);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        applyRiptideWithAim(simulator);
        records.clear();

        double skillCast = simulator.getCurrentTime();
        performSkill(simulator);
        assertTrue(tartaglia.isMeleeStanceActive(simulator.getCurrentTime()),
                "Tartaglia Skill enters melee stance");
        assertClose(skillCast + 30.0,
                tartaglia.getStanceExpirationTime(),
                "Tartaglia melee stance has exact thirty-second maximum");
        ActionRecord entry = named(records,
                "Foul Legacy: Raging Tide Stance Change").get(0);
        assertClose(1.440000, entry.action.getDamagePercent(),
                "C3 raises stance-change multiplier");

        records.clear();
        double meleeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord melee = named(records, "Foul Legacy Melee N1").get(0);
        assertClose(meleeStart + 8.0 * FRAME, melee.time,
                "Tartaglia melee N1 hitmark");
        assertClose(0.876880, melee.action.getDamagePercent(),
                "C3 raises melee N1 multiplier");
        assertEquals(Element.HYDRO, melee.action.getElement(),
                "Tartaglia melee Normal is Hydro");
        assertEquals(1, named(records, "Riptide Slash").size(),
                "Marked melee hit triggers Riptide Slash");
        assertClose(1.358000,
                named(records, "Riptide Slash").get(0)
                        .action.getDamagePercent(),
                "C3 raises Riptide Slash multiplier");
        simulator.advanceTime(80.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Accepted Riptide damage emits one particle packet");
        assertClose(1.0, particles.get(0).count,
                "Riptide particle count");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(2, namedPrefix(records,
                "Foul Legacy Melee Charged").size(),
                "Melee Charged Attack has two hits");
        assertClose(1.358000,
                namedPrefix(records, "Foul Legacy Melee Charged")
                        .get(0).action.getDamagePercent(),
                "C3 melee Charged first multiplier");
        assertClose(1.623780,
                namedPrefix(records, "Foul Legacy Melee Charged")
                        .get(1).action.getDamagePercent(),
                "C3 melee Charged second multiplier");

        Tartaglia c0 = new Tartaglia(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        performSkill(c0Simulator);
        advanceTo(c0Simulator, 3.0);
        performSkill(c0Simulator);
        assertClose(8.0 - 7.0 * FRAME,
                c0.getSkillCDRemaining(c0Simulator.getCurrentTime()),
                "Three-second stance maps to eight-second cooldown");

        Tartaglia c1 = new Tartaglia(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        performSkill(c1Simulator);
        advanceTo(c1Simulator, 3.0);
        performSkill(c1Simulator);
        assertClose(6.4 - 7.0 * FRAME,
                c1.getSkillCDRemaining(c1Simulator.getCurrentTime()),
                "C1 reduces the mapped stance cooldown by twenty percent");

        Tartaglia maximum = new Tartaglia(null, null, 0);
        CombatSimulator maximumSimulator = simulatorWith(maximum);
        performSkill(maximumSimulator);
        advanceTo(maximumSimulator, 30.0 + EPSILON);
        assertTrue(!maximum.isMeleeStanceActive(
                maximumSimulator.getCurrentTime()),
                "Thirty-second boundary ends melee stance automatically");
        assertClose(45.0 - EPSILON,
                maximum.getSkillCDRemaining(
                        maximumSimulator.getCurrentTime()),
                "Maximum stance duration maps to forty-five-second cooldown");
    }

    private static void testBurstsConstellationsAndC4() {
        Tartaglia ranged = new Tartaglia(null, null, 5);
        CombatSimulator rangedSimulator = simulatorWith(ranged);
        List<ActionRecord> rangedRecords = captureActions(rangedSimulator);
        perform(rangedSimulator, CharacterActionKey.BURST);
        assertClose(20.0, ranged.getCurrentEnergy(),
                "Ranged Burst spends sixty then refunds twenty Energy");
        rangedSimulator.advanceTime(16.0 * FRAME);
        ActionRecord rangedBurst = named(rangedRecords,
                "Havoc: Obliteration Ranged Stance").get(0);
        assertClose(7.568000, rangedBurst.action.getDamagePercent(),
                "C5 raises ranged Burst multiplier");
        assertTrue(ranged.isRiptideActive(rangedSimulator.getCurrentTime()),
                "Ranged Burst applies Riptide");

        Tartaglia melee = new Tartaglia(null, null, 6);
        CombatSimulator meleeSimulator = simulatorWith(melee);
        List<ActionRecord> meleeRecords = captureActions(meleeSimulator);
        applyRiptideWithAim(meleeSimulator);
        performSkill(meleeSimulator);
        perform(meleeSimulator, CharacterActionKey.BURST);
        assertClose(0.0, melee.getCurrentEnergy(),
                "Melee Burst spends sixty Energy at its delayed frame");
        assertClose(9.280000,
                named(meleeRecords,
                        "Havoc: Obliteration Melee Stance")
                        .get(0).action.getDamagePercent(),
                "C5 raises melee Burst multiplier");
        assertClose(2.400000,
                named(meleeRecords, "Riptide Blast")
                        .get(0).action.getDamagePercent(),
                "C5 raises Riptide Blast multiplier");
        assertTrue(!melee.isRiptideActive(meleeSimulator.getCurrentTime()),
                "Riptide Blast clears fixed-target Riptide");
        performSkill(meleeSimulator);
        assertClose(0.0,
                melee.getSkillCDRemaining(meleeSimulator.getCurrentTime()),
                "C6 resets Skill cooldown when melee stance ends");

        Tartaglia c4 = new Tartaglia(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        perform(c4Simulator, CharacterActionKey.BURST);
        c4Simulator.advanceTime(16.0 * FRAME);
        double applicationTime = named(c4Records,
                "Havoc: Obliteration Ranged Stance").get(0).time;
        advanceTo(c4Simulator, applicationTime + 3.9 + 2.0 * FRAME);
        assertEquals(3, namedPrefix(c4Records, "Riptide Flash").size(),
                "C4 fixed-target tick triggers ranged Riptide Flash");
    }

    private static void testSnapshotSwitchAndReuseGuards() {
        Tartaglia tartaglia = new Tartaglia(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(tartaglia, ally);
        List<ActionRecord> records = captureActions(simulator);
        applyRiptideWithAim(simulator);
        performSkill(simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, named(records, "Riptide Slash").size(),
                "Original future melee hit triggers Slash once");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, named(records, "Riptide Slash").size(),
                "Repeated restore reconstructs melee and Slash once");

        simulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!tartaglia.isMeleeStanceActive(
                simulator.getCurrentTime()),
                "Switch-out ends Tartaglia melee stance");
        assertTrue(tartaglia.getSkillCDRemaining(
                        simulator.getCurrentTime()) > 0.0,
                "Switch-out starts mapped stance cooldown");

        assertThrows(IllegalArgumentException.class,
                () -> new Tartaglia(null, null, -1),
                "Tartaglia rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Tartaglia(null, null, 7),
                "Tartaglia rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> tartaglia.onAction(null, simulator),
                "Tartaglia rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> tartaglia.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Tartaglia rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> tartaglia.onAction(
                        CharacterActionRequest.of(CharacterActionKey.PLUNGE),
                        simulator),
                "Tartaglia rejects unsupported Plunge");

        Tartaglia reused = new Tartaglia(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Tartaglia rejects cross-simulator reuse");
        Tartaglia foreign = new Tartaglia(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!tartaglia.acceptsCharacterState(foreignState),
                "Tartaglia rejects another instance's snapshot payload");
    }

    private static void applyRiptideWithAim(CombatSimulator simulator) {
        perform(simulator, CharacterActionKey.CHARGE);
        simulator.advanceTime(4.0 * FRAME);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
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

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.TARTAGLIA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.TARTAGLIA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.TARTAGLIA) {
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

    private static List<ActionRecord> namedPrefix(
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
            assertTrue(lines.get(index).startsWith("Tartaglia,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Tartaglia/Tartaglia_Status.csv",
                "config/characters/Tartaglia/Tartaglia_Multipliers.csv"
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
        throw new AssertionError("Tartaglia CSVs missing key " + key);
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
        private TestCharacter(CharacterId id, Element characterElement) {
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
