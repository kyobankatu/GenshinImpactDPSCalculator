package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Freminet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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

/** Focused regression checks for Freminet's fixed-target Pressure kit. */
public final class FreminetRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private FreminetRegressionTest() {
    }

    /** Runs identity, action, state, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalAttackStringAndC3();
        testSkillOpenLevelZeroAndSnapshot();
        testPressureBranchesA4C1C2C5();
        testBurstAccelerationLevelFourAndSwitch();
        testC4C6ReactionStacks();
        testExactWindowBoundaries();
        testGuards();
        System.out.println("FreminetRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Freminet freminet = new Freminet(null, null, 6);
        assertEquals(CharacterId.FREMINET, freminet.getCharacterId(),
                "Freminet typed identity");
        assertEquals(CharacterId.FREMINET,
                CharacterId.fromName("Freminet"),
                "Freminet name lookup");
        assertEquals(CharacterId.FREMINET,
                CharacterId.fromNumericId(50),
                "Freminet numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.FREMINET.getRegion(), "Freminet region");
        assertEquals(Element.CRYO, freminet.getElement(),
                "Freminet element");
        assertClose(12071.0,
                freminet.getBaseStats().get(StatType.BASE_HP),
                "Freminet base HP");
        assertClose(255.0,
                freminet.getBaseStats().get(StatType.BASE_ATK),
                "Freminet base ATK");
        assertClose(708.0,
                freminet.getBaseStats().get(StatType.BASE_DEF),
                "Freminet base DEF");
        assertClose(0.24,
                freminet.getBaseStats().get(StatType.ATK_PERCENT),
                "Freminet ascension ATK");
        assertClose(60.0, freminet.getEnergyCost(),
                "Freminet Energy cost");
        assertClose(10.0, freminet.getSkillCD(),
                "Freminet Skill cooldown");
        assertClose(15.0, freminet.getBurstCD(),
                "Freminet Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.FREMINET,
                    new Freminet(null, null, constellation).getCharacterId(),
                    "Freminet explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Freminet/Freminet_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Freminet/Freminet_Multipliers.csv"), 39);
        assertCsvValue("N4 C3", 2.792805);
        assertCsvValue("Pressure Physical Level 4 C5", 4.868800);
        assertCsvValue("Shadowhunter's Ambush", 5.412800);
    }

    private static void testPhysicalAttackStringAndC3() {
        Freminet c0 = new Freminet(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        double[] t9 = { 1.547626, 1.482182, 1.872189, 2.274552 };
        int[] durations = { 47, 49, 65, 86 };
        int[] hitlagFrames = { 8, 8, 8, 9 };
        for (int index = 0; index < t9.length; index++) {
            double castTime = c0Simulator.getCurrentTime();
            perform(c0Simulator, CharacterActionKey.NORMAL);
            assertClose(t9[index], c0Records.get(index).action
                            .getDamagePercent(),
                    "Freminet Talent 9 Normal multiplier");
            assertEquals(Element.PHYSICAL,
                    c0Records.get(index).action.getElement(),
                    "Freminet physical Normal element");
            assertClose(castTime
                            + (durations[index] + hitlagFrames[index]) * FRAME,
                    c0Simulator.getCurrentTime(),
                    "Freminet Normal recovery");
        }

        Freminet c3 = new Freminet(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        double[] t12 = { 1.900249, 1.819895, 2.298764, 2.792805 };
        for (int index = 0; index < t12.length; index++) {
            perform(c3Simulator, CharacterActionKey.NORMAL);
            assertClose(t12[index], c3Records.get(index).action
                            .getDamagePercent(),
                    "Freminet C3 Normal multiplier");
        }
        double plungeStart = c3Simulator.getCurrentTime();
        perform(c3Simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(c3Records,
                "Flowing Eddies High Plunge").get(0);
        assertClose(plungeStart + 47.0 * FRAME, plunge.time,
                "Freminet Plunge hitmark");
        assertClose(4.202331, plunge.action.getDamagePercent(),
                "Freminet C3 Plunge multiplier");
    }

    private static void testSkillOpenLevelZeroAndSnapshot() {
        Freminet freminet = new Freminet(null, null, 0);
        CombatSimulator simulator = simulatorWith(freminet);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(freminet.isPersTimeActive(simulator.getCurrentTime()),
                "Freminet Skill opens Pers Time");
        assertEquals(0, freminet.getPressureLevel(simulator.getCurrentTime()),
                "Freminet Skill starts at Pressure zero");
        ActionRecord thrust = named(records,
                "Pressurized Floe: Upward Thrust").get(0);
        assertClose(castTime + 29.0 * FRAME, thrust.time,
                "Freminet Upward Thrust hitmark");
        assertClose(1.411680, thrust.action.getDamagePercent(),
                "Freminet Upward Thrust Talent 9");
        assertClose(0.0,
                freminet.getSkillCDRemaining(simulator.getCurrentTime()),
                "Freminet Pers Time exposes the Pressure recast");
        SimulatorSnapshot pendingSpirit = simulator.saveSnapshot();
        advanceTo(simulator, castTime + 62.0 * FRAME + EPSILON);
        assertEquals(1, named(records,
                "Pressurized Floe: Spiritbreath Thorn").size(),
                "Freminet queues one Spiritbreath Thorn");
        simulator.restoreSnapshot(pendingSpirit);
        advanceTo(simulator, castTime + 62.0 * FRAME + EPSILON);
        assertEquals(2, named(records,
                "Pressurized Floe: Spiritbreath Thorn").size(),
                "Freminet restores pending Spiritbreath once");
        advanceTo(simulator, thrust.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Freminet Thrust emits one particle packet");
        assertClose(2.0, particles.get(0).count,
                "Freminet non-Burst Thrust emits two particles");

        double recast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord levelZero = named(records,
                "Shattering Pressure Cryo Level 0").get(0);
        assertClose(recast + 42.0 * FRAME, levelZero.time,
                "Freminet level-zero Pressure hitmark");
        assertClose(3.408160, levelZero.action.getDamagePercent(),
                "Freminet level-zero Pressure multiplier");
        assertEquals(0, named(records,
                "Shattering Pressure Physical Level 0").size(),
                "Freminet level zero has no Physical branch");
        assertTrue(!freminet.isPersTimeActive(simulator.getCurrentTime()),
                "Freminet detonation closes Pers Time");
    }

    private static void testPressureBranchesA4C1C2C5() {
        Freminet freminet = new Freminet(null, null, 5);
        CombatSimulator simulator = simulatorWith(freminet);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, freminet.getPressureLevel(simulator.getCurrentTime()),
                "Freminet Pers Time Normal gains one Pressure");
        freminet.onReaction(reaction(ReactionResult.Kind.SHATTER),
                freminet, simulator.getCurrentTime(), simulator);
        advanceTo(simulator, 129.0 * FRAME + EPSILON);
        freminet.restoreCurrentEnergy(0.0);
        double recast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord cryo = named(records,
                "Shattering Pressure Cryo Level 1").get(0);
        ActionRecord physical = named(records,
                "Shattering Pressure Physical Level 1").get(0);
        assertClose(recast + 42.0 * FRAME, cryo.time,
                "Freminet split Pressure hitmark");
        assertClose(2.004800, cryo.action.getDamagePercent(),
                "Freminet C5 Pressure Cryo multiplier");
        assertClose(0.973760, physical.action.getDamagePercent(),
                "Freminet C5 Pressure Physical multiplier");
        assertClose(0.20,
                cryo.action.getStatSnapshot().get(StatType.CRIT_RATE),
                "Freminet C1 applies Pressure-only CRIT Rate");
        assertClose(0.40,
                cryo.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Freminet A4 applies Pressure-only DMG bonus");
        assertClose(2.0, freminet.getCurrentEnergy(),
                "Freminet C2 restores two Energy below level four");
        assertClose((391.0 - 6.0) * FRAME - EPSILON,
                freminet.getSkillCDRemaining(simulator.getCurrentTime()),
                "Freminet A1 reduces the remaining cooldown below level four");
    }

    private static void testBurstAccelerationLevelFourAndSwitch() {
        Freminet freminet = new Freminet(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(freminet, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(freminet.isBurstActive(simulator.getCurrentTime()),
                "Freminet Burst opens Subnautical Hunter mode");
        assertClose(0.0,
                freminet.getSkillCDRemaining(simulator.getCurrentTime()),
                "Freminet Burst resets Skill cooldown");
        assertClose(5.412800,
                named(records, "Shadowhunter's Ambush").get(0)
                        .action.getDamagePercent(),
                "Freminet Burst remains Talent 9 at C6");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, freminet.getPressureLevel(simulator.getCurrentTime()),
                "Freminet Burst Normal gains two Pressure");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(4, freminet.getPressureLevel(simulator.getCurrentTime()),
                "Freminet Burst second Normal reaches level four");
        List<ActionRecord> frost = named(records,
                "Pressurized Floe: Pers Time Frost");
        assertEquals(2, frost.size(),
                "Freminet queues Frost for both Pers Time Normals");
        assertClose(0.286400, frost.get(0).action.getDamagePercent(),
                "Freminet Burst doubles C5 Frost damage");
        freminet.restoreCurrentEnergy(0.0);
        double detonation = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord levelFour = named(records,
                "Shattering Pressure Physical Level 4").get(0);
        assertClose(detonation + 37.0 * FRAME, levelFour.time,
                "Freminet level-four Normal replacement hitmark");
        assertClose(4.868800, levelFour.action.getDamagePercent(),
                "Freminet C5 level-four Physical multiplier");
        assertEquals(0, named(records,
                "Shattering Pressure Cryo Level 4").size(),
                "Freminet level four has no Cryo branch");
        assertClose(3.0, freminet.getCurrentEnergy(),
                "Freminet C2 restores three Energy at level four");
        advanceTo(simulator, levelFour.time + 100.0 * FRAME + EPSILON);
        assertTrue(particles.size() >= 2,
                "Freminet Thrust and level-four Pressure emit particles");
        assertClose(1.0, particles.get(particles.size() - 1).count,
                "Freminet Burst level-four Pressure emits one particle");
        int n1Count = named(records, "Flowing Eddies N1").size();
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(n1Count + 1,
                named(records, "Flowing Eddies N1").size(),
                "Freminet level-four Normal replacement resets the string");
        simulator.switchCharacter(CharacterId.COLLEI);
        assertTrue(!freminet.isBurstActive(simulator.getCurrentTime()),
                "Freminet switch-out ends Burst acceleration");
    }

    private static void testC4C6ReactionStacks() {
        Freminet freminet = new Freminet(null, null, 6);
        CombatSimulator simulator = simulatorWith(freminet);
        ReactionResult frozen = reaction(ReactionResult.Kind.FROZEN);
        freminet.onReaction(frozen, freminet, 0.0, simulator);
        advanceTo(simulator, 0.3 - EPSILON);
        freminet.onReaction(frozen, freminet, 0.3 - EPSILON, simulator);
        assertEquals(1, freminet.getC4Stacks(0.3 - EPSILON),
                "Freminet C4 blocks before 0.3 seconds");
        advanceTo(simulator, 0.3);
        freminet.onReaction(reaction(ReactionResult.Kind.SUPERCONDUCT),
                freminet, 0.3, simulator);
        assertEquals(2, freminet.getC4Stacks(0.3),
                "Freminet C4 reopens and caps at two stacks");
        assertEquals(2, freminet.getC6Stacks(0.3),
                "Freminet C6 tracks the same accepted reactions");
        advanceTo(simulator, 0.6);
        freminet.onReaction(reaction(ReactionResult.Kind.SHATTER),
                freminet, 0.6, simulator);
        assertEquals(3, freminet.getC6Stacks(0.6),
                "Freminet C6 caps at three stacks");
        assertClose(0.42,
                effectiveStats(simulator, freminet).get(
                        StatType.ATK_PERCENT),
                "Freminet C4 adds two 9% ATK stacks");
        assertClose(0.86,
                effectiveStats(simulator, freminet).get(StatType.CRIT_DMG),
                "Freminet C6 adds three 12% CRIT DMG stacks");
        assertEquals(0, freminet.getC4Stacks(6.6),
                "Freminet C4 expires at exact six seconds");
        assertEquals(0, freminet.getC6Stacks(6.6),
                "Freminet C6 expires at exact six seconds");

        Freminet wrong = new Freminet(null, null, 6);
        CombatSimulator wrongSimulator = simulatorWith(wrong);
        wrong.onReaction(null, wrong, 0.0, wrongSimulator);
        wrong.onReaction(frozen, freminet, 0.0, wrongSimulator);
        wrong.onReaction(frozen, wrong, 0.0, simulator);
        wrong.onReaction(reaction(ReactionResult.Kind.OVERLOAD),
                wrong, 0.0, wrongSimulator);
        assertEquals(0, wrong.getC4Stacks(0.0),
                "Freminet rejects invalid reaction callbacks");
    }

    private static void testExactWindowBoundaries() {
        Freminet windows = new Freminet(null, null, 0);
        CombatSimulator windowSimulator = simulatorWith(windows);
        perform(windowSimulator, CharacterActionKey.SKILL);
        perform(windowSimulator, CharacterActionKey.NORMAL);
        assertTrue(windows.isPersTimeActive(10.0 - EPSILON),
                "Freminet Pers Time remains active before ten seconds");
        assertTrue(!windows.isPersTimeActive(10.0),
                "Freminet Pers Time expires at ten seconds");
        assertEquals(0, windows.getPressureLevel(10.0),
                "Freminet Pers Time expiry clears Pressure");

        Freminet burst = new Freminet(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burst);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertTrue(burst.isBurstActive(10.0 - EPSILON),
                "Freminet Burst remains active before ten seconds");
        assertTrue(!burst.isBurstActive(10.0),
                "Freminet Burst expires at ten seconds");

        Freminet aligned = new Freminet(null, null, 0);
        CombatSimulator alignedSimulator = simulatorWith(aligned);
        List<ActionRecord> alignedRecords = captureActions(alignedSimulator);
        perform(alignedSimulator, CharacterActionKey.SKILL);
        perform(alignedSimulator, CharacterActionKey.SKILL);
        perform(alignedSimulator, CharacterActionKey.BURST);
        perform(alignedSimulator, CharacterActionKey.SKILL);
        assertEquals(1, named(alignedRecords,
                "Pressurized Floe: Spiritbreath Thorn").size(),
                "Freminet Spiritbreath gate blocks an early reset Skill");
        perform(alignedSimulator, CharacterActionKey.SKILL);
        advanceTo(alignedSimulator, 9.0);
        perform(alignedSimulator, CharacterActionKey.SKILL);
        advanceTo(alignedSimulator, 9.0 + 62.0 * FRAME + EPSILON);
        assertEquals(2, named(alignedRecords,
                "Pressurized Floe: Spiritbreath Thorn").size(),
                "Freminet Spiritbreath gate reopens at nine seconds");

        assertA4Boundary(5.0 - EPSILON, 0.40,
                "Freminet A4 remains active before five seconds");
        assertA4Boundary(5.0, 0.0,
                "Freminet A4 expires at five seconds");
    }

    private static void assertA4Boundary(
            double recastTime,
            double expectedBonus,
            String message) {
        Freminet freminet = new Freminet(null, null, 0);
        CombatSimulator simulator = simulatorWith(freminet);
        List<ActionRecord> records = captureActions(simulator);
        freminet.onReaction(reaction(ReactionResult.Kind.SHATTER),
                freminet, 0.0, simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, recastTime);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord pressure = named(records,
                "Shattering Pressure Cryo Level 0").get(0);
        assertClose(expectedBonus,
                pressure.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                message);
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Freminet(null, null, -1),
                "Freminet rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Freminet(null, null, 7),
                "Freminet rejects constellation above C6");
        Freminet freminet = new Freminet(null, null, 0);
        CombatSimulator simulator = simulatorWith(freminet);
        assertThrows(IllegalArgumentException.class,
                () -> freminet.onAction(null, simulator),
                "Freminet rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Freminet rejects unsupported Charged Attack");
        freminet.restoreCurrentEnergy(0.0);
        double beforeBurst = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(beforeBurst, simulator.getCurrentTime(),
                "Freminet skips Burst without Energy");
        assertClose(60.0, freminet.getMissedBurstCost(),
                "Freminet records missed Burst Energy");

        Freminet external = new Freminet(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Freminet rejects binding outside simulator party");
        Freminet reused = new Freminet(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Freminet rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!freminet.acceptsCharacterState(foreignState),
                "Freminet rejects another instance snapshot payload");
    }

    private static ReactionResult reaction(ReactionResult.Kind kind) {
        if (kind == ReactionResult.Kind.FROZEN) {
            return ReactionResult.state("Frozen", kind, null);
        }
        return ReactionResult.transform(0.0, kind.name(), kind, null);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
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
                CharacterId.FREMINET,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.FREMINET) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureCryoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.CRYO) {
                records.add(new ParticleRecord(count));
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
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Freminet,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Freminet/Freminet_Status.csv",
                "config/characters/Freminet/Freminet_Multipliers.csv"
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
        throw new AssertionError("Freminet CSVs missing key " + key);
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
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public double getEnergyCost() {
            return 80.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
