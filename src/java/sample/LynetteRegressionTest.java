package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Lynette;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
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

/** Focused regression checks for Lynette's fixed-target Bogglecat kit. */
public final class LynetteRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private LynetteRegressionTest() {
    }

    /** Runs identity, action, Skill, Burst, constellation, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalBasics();
        testPressHoldArkheParticlesC4C5C6AndRestore();
        testArkheGateStartsAtSkillHitmark();
        testBurstTicksA1AndC3();
        testGuards();
        System.out.println("LynetteRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction() throws IOException {
        Lynette lynette = new Lynette(null, null, 6);
        assertEquals(CharacterId.LYNETTE, lynette.getCharacterId(),
                "Lynette typed identity");
        assertEquals(CharacterId.LYNETTE, CharacterId.fromName("Lynette"),
                "Lynette name lookup");
        assertEquals(CharacterId.LYNETTE, CharacterId.fromNumericId(52),
                "Lynette numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.LYNETTE.getRegion(), "Lynette region");
        assertEquals(Element.ANEMO, lynette.getElement(), "Lynette element");
        assertClose(12397.0,
                lynette.getBaseStats().get(StatType.BASE_HP),
                "Lynette base HP");
        assertClose(232.0,
                lynette.getBaseStats().get(StatType.BASE_ATK),
                "Lynette base ATK");
        assertClose(712.0,
                lynette.getBaseStats().get(StatType.BASE_DEF),
                "Lynette base DEF");
        assertClose(0.24,
                lynette.getBaseStats().get(StatType.ANEMO_DMG_BONUS),
                "Lynette ascension Anemo bonus");
        assertClose(70.0, lynette.getEnergyCost(), "Lynette Energy cost");
        assertClose(12.0, lynette.getSkillCD(), "Lynette Skill CD");
        assertClose(18.0, lynette.getBurstCD(), "Lynette Burst CD");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.LYNETTE,
                    new Lynette(null, null, constellation).getCharacterId(),
                    "Lynette explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Lynette/Lynette_Status.csv"), 13);
        assertCsvShape(Path.of(
                "config/characters/Lynette/Lynette_Multipliers.csv"), 16);
        assertCsvValue("N3 Hit 2", 0.396706);
        assertCsvValue("Enigma Thrust C5", 5.360000);
        assertCsvValue("Bogglecat Box C3", 1.024000);
    }

    private static void testPhysicalBasics() {
        Lynette lynette = new Lynette(null, null, 0);
        CombatSimulator simulator = simulatorWith(lynette);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            0.791501, 0.691013, 0.511920, 0.396706, 1.160273
        };
        int record = 0;
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
            int hits = step == 2 ? 2 : 1;
            for (int hit = 0; hit < hits; hit++) {
                assertClose(expected[record],
                        records.get(record).action.getDamagePercent(),
                        "Lynette Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        records.get(record).action.getElement(),
                        "Lynette physical Normal element");
                record++;
            }
        }
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        assertEquals(2, named(records, "Rapid Rites Charged").size(),
                "Lynette Charged Attack has two hits");
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.PLUNGE));
        assertClose(2.933586, named(records,
                "Rapid Rites High Plunge").get(0)
                        .action.getDamagePercent(),
                "Lynette high Plunge multiplier");
    }

    private static void testPressHoldArkheParticlesC4C5C6AndRestore() {
        Lynette lynette = new Lynette(null, null, 6);
        CombatSimulator simulator = simulatorWith(lynette);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        double firstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        ActionRecord thrust = named(records, "Enigmatic Feint").get(0);
        assertClose(firstCast + 28.0 * FRAME, thrust.time,
                "Lynette Press hitmark");
        assertClose(5.360000, thrust.action.getDamagePercent(),
                "Lynette C5 Skill Talent 12");
        ActionRecord arkhe = named(records,
                "Surging Blade (Lynette)").get(0);
        assertClose(firstCast + 58.0 * FRAME, arkhe.time,
                "Lynette Press Arkhe hitmark");
        assertClose(0.624000, arkhe.action.getDamagePercent(),
                "Lynette C5 Arkhe Talent 12");
        assertClose(0.0,
                lynette.getSkillCDRemaining(simulator.getCurrentTime()),
                "Lynette C4 leaves the second Skill charge ready");
        assertTrue(lynette.isC6InfusionActive(simulator.getCurrentTime()),
                "Lynette C6 infusion starts during Skill");
        assertClose(0.44,
                effectiveStats(simulator, lynette).get(
                        StatType.ANEMO_DMG_BONUS),
                "Lynette C6 adds 20% Anemo bonus");
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertEquals(Element.ANEMO,
                named(records, "Rapid Rites N1").get(0).action.getElement(),
                "Lynette C6 infuses Normal Attack");

        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        assertTrue(lynette.getSkillCDRemaining(
                simulator.getCurrentTime()) > 0.0,
                "Lynette C4 exhausts after the second charge");
        assertEquals(1, named(records,
                "Surging Blade (Lynette)").size(),
                "Lynette Arkhe gate blocks the second Skill");
        SimulatorSnapshot pendingParticles = simulator.saveSnapshot();
        advanceTo(simulator, 4.0);
        assertEquals(2, particles.size(),
                "Lynette two Skills emit two particle packets");
        assertClose(4.0, particles.get(0).count,
                "Lynette Skill emits four particles");
        simulator.restoreSnapshot(pendingParticles);
        advanceTo(simulator, 4.0);
        assertEquals(3, particles.size(),
                "Lynette replays only the pending particle packet");

        Lynette hold = new Lynette(null, null, 0);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureActions(holdSimulator);
        perform(holdSimulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertClose(51.0 * FRAME,
                named(holdRecords, "Enigmatic Feint").get(0).time,
                "Lynette minimum-Hold hitmark");
        advanceTo(holdSimulator, 80.0 * FRAME);
        assertClose(79.0 * FRAME,
                named(holdRecords, "Surging Blade (Lynette)").get(0).time,
                "Lynette minimum-Hold Arkhe hitmark");
    }

    private static void testBurstTicksA1AndC3() {
        Lynette lynette = new Lynette(null, null, 3);
        TestCharacter hydro = new TestCharacter(
                CharacterId.YELAN, Element.HYDRO);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator simulator = simulatorWith(lynette, hydro, pyro);
        List<ActionRecord> records = captureActions(simulator);
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        ActionRecord initial = named(records,
                "Magic Trick: Astonishing Shift").get(0);
        assertClose(castTime + 18.0 * FRAME, initial.time,
                "Lynette Burst initial hitmark");
        assertClose(1.664000, initial.action.getDamagePercent(),
                "Lynette C3 Burst Talent 12");
        assertClose(0.0, lynette.getCurrentEnergy(),
                "Lynette Burst consumes Energy at frame six");
        assertClose(0.16,
                effectiveStats(simulator, hydro).get(StatType.ATK_PERCENT),
                "Lynette A1 counts three unique party elements");
        advanceTo(simulator, castTime + 727.0 * FRAME);
        List<ActionRecord> ticks = named(records, "Bogglecat Box Tick");
        assertEquals(11, ticks.size(), "Lynette Burst queues eleven ticks");
        assertClose(castTime + 136.0 * FRAME, ticks.get(0).time,
                "Lynette first Bogglecat tick");
        assertClose(59.0 * FRAME,
                ticks.get(1).time - ticks.get(0).time,
                "Lynette Bogglecat tick interval");
        assertClose(1.024000, ticks.get(0).action.getDamagePercent(),
                "Lynette C3 Bogglecat Talent 12");
        assertClose(0.0,
                effectiveStats(simulator, hydro).get(StatType.ATK_PERCENT),
                "Lynette A1 expires at ten seconds");
    }

    private static void testArkheGateStartsAtSkillHitmark() {
        Lynette beforeBoundary = new Lynette(null, null, 4);
        CombatSimulator beforeSimulator = simulatorWith(beforeBoundary);
        List<ActionRecord> beforeRecords = captureActions(beforeSimulator);
        perform(beforeSimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        advanceTo(beforeSimulator, 10.0 - 2.0 * EPSILON);
        perform(beforeSimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        assertEquals(1, named(beforeRecords,
                "Surging Blade (Lynette)").size(),
                "Lynette Arkhe remains gated immediately before ten seconds");

        Lynette atBoundary = new Lynette(null, null, 4);
        CombatSimulator boundarySimulator = simulatorWith(atBoundary);
        List<ActionRecord> boundaryRecords = captureActions(boundarySimulator);
        perform(boundarySimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        advanceTo(boundarySimulator, 10.0);
        perform(boundarySimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        assertEquals(2, named(boundaryRecords,
                "Surging Blade (Lynette)").size(),
                "Lynette Arkhe reopens ten seconds after the Skill hitmark");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Lynette(null, null, -1),
                "Lynette rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Lynette(null, null, 7),
                "Lynette rejects constellation above C6");
        Lynette lynette = new Lynette(null, null, 0);
        CombatSimulator simulator = simulatorWith(lynette);
        assertThrows(IllegalArgumentException.class,
                () -> lynette.onAction(null, simulator),
                "Lynette rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.of(
                        CharacterActionKey.DASH)),
                "Lynette rejects unsupported action");
        lynette.restoreCurrentEnergy(0.0);
        double beforeBurst = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        assertClose(beforeBurst, simulator.getCurrentTime(),
                "Lynette skips Burst without Energy");
        assertClose(70.0, lynette.getMissedBurstCost(),
                "Lynette records missed Burst Energy");

        Lynette external = new Lynette(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Lynette rejects binding outside simulator party");
        Lynette reused = new Lynette(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Lynette rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!lynette.acceptsCharacterState(foreignState),
                "Lynette rejects another instance snapshot payload");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionRequest request) {
        simulator.performAction(CharacterId.LYNETTE, request);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.LYNETTE) {
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
                records.add(new ParticleRecord(count));
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
            assertTrue(lines.get(index).startsWith("Lynette,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Lynette/Lynette_Status.csv",
                "config/characters/Lynette/Lynette_Multipliers.csv"
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
        throw new AssertionError("Lynette CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected)
                == Double.doubleToLongBits(actual)) {
            return;
        }
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
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

    private static final class ParticleRecord {
        private final double count;

        private ParticleRecord(double count) {
            this.count = count;
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
