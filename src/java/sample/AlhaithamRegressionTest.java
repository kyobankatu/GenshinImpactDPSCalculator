package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Alhaitham;
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

/** Focused regression checks for Alhaitham's fixed-target Mirror kit. */
public final class AlhaithamRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private AlhaithamRegressionTest() {
    }

    /** Runs data, action, Mirror, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalNormalChargedAndPlunge();
        testSkillInfusionProjectionDecayAndC1();
        testA1C2AndCustomChargedIcd();
        testInfusionSnapshotAttackSpeedAndA4Cap();
        testBurstCountsSnapshotsAndMirrorConversion();
        testC4AndC6();
        testSnapshotRestoreSwitchAndGuards();
        System.out.println("AlhaithamRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Alhaitham alhaitham = new Alhaitham(null, null, 6);
        assertEquals(CharacterId.ALHAITHAM, alhaitham.getCharacterId(),
                "Alhaitham typed identity");
        assertEquals(CharacterId.ALHAITHAM,
                CharacterId.fromName("Alhaitham"),
                "Alhaitham name lookup");
        assertEquals(CharacterId.ALHAITHAM,
                CharacterId.fromNumericId(47),
                "Alhaitham numeric lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.ALHAITHAM.getRegion(),
                "Alhaitham region");
        assertEquals(Element.DENDRO, alhaitham.getElement(),
                "Alhaitham element");
        assertClose(13348.0,
                alhaitham.getBaseStats().get(StatType.BASE_HP),
                "Alhaitham base HP");
        assertClose(313.0,
                alhaitham.getBaseStats().get(StatType.BASE_ATK),
                "Alhaitham base ATK");
        assertClose(782.0,
                alhaitham.getBaseStats().get(StatType.BASE_DEF),
                "Alhaitham base DEF");
        assertClose(0.288,
                alhaitham.getBaseStats().get(StatType.DENDRO_DMG_BONUS),
                "Alhaitham ascension Dendro bonus");
        assertClose(70.0, alhaitham.getEnergyCost(),
                "Alhaitham Energy cost");
        assertClose(18.0, alhaitham.getSkillCD(),
                "Alhaitham Skill cooldown");
        assertClose(18.0, alhaitham.getBurstCD(),
                "Alhaitham Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.ALHAITHAM,
                    new Alhaitham(null, null, constellation)
                            .getCharacterId(),
                    "Alhaitham explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Alhaitham/Alhaitham_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Alhaitham/Alhaitham_Multipliers.csv"), 48);
        assertCsvValue("Rush EM C3", 3.0976);
        assertCsvValue("Projection ATK", 1.1424);
        assertCsvValue("Burst ATK C5", 2.432);
        assertCsvValue("C6 CRIT DMG", 0.70);
    }

    private static void testPhysicalNormalChargedAndPlunge() {
        Alhaitham alhaitham = new Alhaitham(null, null, 0);
        CombatSimulator simulator = simulatorWith(alhaitham);
        List<ActionRecord> records = captureActions(simulator);
        double[][] multipliers = {
            { 0.90989 }, { 0.932374 }, { 0.627931, 0.627931 },
            { 1.226665 }, { 1.540516 }
        };
        int[] durations = { 15, 22, 44, 30, 67 };
        int[] hitlagFrames = { 5, 5, 5, 0, 5 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (double multiplier : multipliers[step]) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multiplier, record.action.getDamagePercent(),
                        "Alhaitham Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Alhaitham physical Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Alhaitham Normal category");
            }
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Alhaitham Normal recovery");
        }
        double chargeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = namedPrefix(records,
                "Abductive Reasoning Charged Attack");
        assertEquals(2, charged.size(),
                "Alhaitham Charged Attack has two hits");
        assertClose(chargeStart + 19.0 * FRAME, charged.get(0).time,
                "Alhaitham first Charged hitmark");
        assertClose(chargeStart + 27.0 * FRAME, charged.get(1).time,
                "Alhaitham second Charged hitmark");
        assertEquals(Element.PHYSICAL, charged.get(0).action.getElement(),
                "Alhaitham zero-Mirror Charged is Physical");
        Alhaitham plungeOwner = new Alhaitham(null, null, 0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(plungeRecords,
                "Abductive Reasoning High Plunge").get(0);
        assertEquals(Element.PHYSICAL, plunge.action.getElement(),
                "Alhaitham zero-Mirror Plunge is Physical");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Alhaitham High Plunge multiplier");
    }

    private static void testSkillInfusionProjectionDecayAndC1() {
        Alhaitham alhaitham = new Alhaitham(null, null, 1);
        CombatSimulator simulator = simulatorWith(alhaitham);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureDendroParticles(simulator);
        performSkill(simulator);
        assertEquals(2, alhaitham.getMirrorCount(),
                "Alhaitham zero-Mirror Skill creates two Mirrors");
        ActionRecord skill = named(records,
                "Universality: An Elaboration on Form").get(0);
        assertClose(19.0 * FRAME, skill.time,
                "Alhaitham Skill hitmark");
        assertClose(3.2912, skill.action.getDamagePercent(),
                "Alhaitham Skill ATK ratio");
        assertClose(18.0 - (12.0 + 6.0) * FRAME,
                alhaitham.getSkillCDRemaining(simulator.getCurrentTime()),
                "Alhaitham Skill cooldown starts at mirror gain frame");

        double normalStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = named(records,
                "Abductive Reasoning N1").get(0);
        assertEquals(Element.DENDRO, normal.action.getElement(),
                "Alhaitham Mirrors infuse Normal Attacks");
        assertClose(normalStart + 11.0 * FRAME, normal.time,
                "Alhaitham infused N1 hitmark");
        double beforeProjectionCd = alhaitham.getSkillCDRemaining(
                simulator.getCurrentTime());
        advanceTo(simulator, normal.time + 37.0 * FRAME);
        List<ActionRecord> projections = named(records,
                "Chisel-Light Mirror: Projection Attack 2");
        assertEquals(2, projections.size(),
                "Alhaitham two Mirrors produce two Projection hits");
        assertClose(normal.time + 28.0 * FRAME, projections.get(0).time,
                "Alhaitham two-Mirror first Projection hitmark");
        assertClose(normal.time + 37.0 * FRAME, projections.get(1).time,
                "Alhaitham two-Mirror second Projection hitmark");
        assertEquals(ICDType.AlhaithamProjection,
                projections.get(0).action.getICDType(),
                "Alhaitham Projection dedicated ICD type");
        assertEquals(ICDTag.Alhaitham_Projection,
                projections.get(0).action.getICDTag(),
                "Alhaitham Projection dedicated ICD tag");
        assertTrue(alhaitham.getSkillCDRemaining(
                simulator.getCurrentTime())
                < beforeProjectionCd - 1.0,
                "Alhaitham C1 reduces Skill cooldown on Projection hit");
        advanceTo(simulator,
                projections.get(0).time + 100.0 * FRAME);
        assertEquals(1, particles.size(),
                "Alhaitham Projection particle ICD emits one particle");

        double firstLoss = 15.0 * FRAME + 233.0 * FRAME;
        advanceTo(simulator, firstLoss);
        assertEquals(1, alhaitham.getMirrorCount(),
                "Alhaitham loses the first Mirror after 233 frames");
        advanceTo(simulator, firstLoss + 233.0 * FRAME);
        assertEquals(0, alhaitham.getMirrorCount(),
                "Alhaitham loses remaining Mirrors sequentially");
    }

    private static void testA1C2AndCustomChargedIcd() {
        Alhaitham alhaitham = new Alhaitham(null, null, 2);
        CombatSimulator simulator = simulatorWith(alhaitham);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        assertEquals(2, alhaitham.getC2Stacks(simulator.getCurrentTime()),
                "Alhaitham Skill grants two independent C2 stacks");
        ActionRecord rush = named(records,
                "Universality: An Elaboration on Form").get(0);
        assertClose(0.0, rush.action.getStatSnapshot().get(
                StatType.FLAT_DMG_BONUS),
                "Alhaitham Rush EM ratio snapshots before Skill Mirror C2");
        assertClose(100.0, effectiveStat(
                simulator, alhaitham, StatType.ELEMENTAL_MASTERY),
                "Alhaitham two C2 stacks grant 100 EM");
        advanceTo(simulator, 1.0);
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(3, alhaitham.getMirrorCount(),
                "Alhaitham A1 adds one Mirror before Projection count");
        assertEquals(3, alhaitham.getC2Stacks(simulator.getCurrentTime()),
                "Alhaitham A1 Mirror adds a third C2 stack");
        List<ActionRecord> charged = namedPrefix(records,
                "Abductive Reasoning Charged Attack");
        assertEquals(Element.DENDRO, charged.get(0).action.getElement(),
                "Alhaitham Mirror-infused Charged is Dendro");
        assertEquals(ICDType.AlhaithamCharged,
                charged.get(0).action.getICDType(),
                "Alhaitham infused Charged uses time-only ICD");
        assertEquals(ICDTag.Alhaitham_Charged,
                charged.get(0).action.getICDTag(),
                "Alhaitham infused Charged uses dedicated tag");
        advanceTo(simulator, 2.20);
        ActionRecord projection = named(records,
                "Chisel-Light Mirror: Projection Attack 3").get(0);
        assertClose(342.72, projection.action.getStatSnapshot().get(
                StatType.FLAT_DMG_BONUS),
                "Alhaitham Projection EM ratio snapshots at trigger");
        assertClose(0.15,
                projection.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Alhaitham A4 reads Projection snapshot EM");
        advanceTo(simulator, 8.30);
        assertEquals(1, alhaitham.getC2Stacks(simulator.getCurrentTime()),
                "Alhaitham independently expires older C2 stacks");
    }

    private static void testBurstCountsSnapshotsAndMirrorConversion() {
        for (int mirrors = 0; mirrors <= 3; mirrors++) {
            Alhaitham alhaitham = new Alhaitham(null, null, 0);
            CombatSimulator simulator = simulatorWith(alhaitham);
            List<ActionRecord> records = captureActions(simulator);
            createMirrors(simulator, mirrors);
            double burstStart = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.BURST);
            int expectedHits = 4 + 2 * mirrors;
            double finalHit = burstStart
                    + (94.0 + (expectedHits - 1) * 21.0) * FRAME;
            advanceTo(simulator, finalHit + EPSILON);
            List<ActionRecord> burstHits = namedPrefix(records,
                    "Particular Field: Fetters of Phenomena");
            assertEquals(expectedHits, burstHits.size(),
                    "Alhaitham Burst hit count for " + mirrors
                            + " consumed Mirrors");
            assertClose(burstStart + 94.0 * FRAME,
                    burstHits.get(0).time,
                    "Alhaitham Burst first hitmark");
            assertClose(2.0672,
                    burstHits.get(0).action.getDamagePercent(),
                    "Alhaitham C0 Burst ATK ratio");
            assertEquals(ICDType.Standard,
                    burstHits.get(0).action.getICDType(),
                    "Alhaitham Burst standard ICD");
            assertClose(70.0,
                    alhaitham.getBurstEnergyWindows().get(0)[2],
                    "Alhaitham Burst records a 70-Energy spend");
            advanceTo(simulator,
                    burstStart + 184.0 * FRAME + EPSILON);
            assertEquals(3 - mirrors, alhaitham.getMirrorCount(),
                    "Alhaitham Burst delayed Mirror conversion");
        }
    }

    private static void testInfusionSnapshotAttackSpeedAndA4Cap() {
        Alhaitham expiring = new Alhaitham(null, null, 0);
        CombatSimulator expiringSimulator = simulatorWith(expiring);
        List<ActionRecord> expiringRecords = captureActions(
                expiringSimulator);
        performSkill(expiringSimulator);
        double firstLoss = (15.0 + 233.0) * FRAME;
        double secondLoss = firstLoss + 233.0 * FRAME;
        advanceTo(expiringSimulator, firstLoss + EPSILON);
        assertEquals(1, expiring.getMirrorCount(),
                "Alhaitham boundary starts with one remaining Mirror");
        advanceTo(expiringSimulator, secondLoss - 20.0 * FRAME);
        perform(expiringSimulator, CharacterActionKey.PLUNGE);
        ActionRecord expiredPlunge = named(expiringRecords,
                "Abductive Reasoning High Plunge").get(0);
        assertEquals(Element.PHYSICAL,
                expiredPlunge.action.getElement(),
                "Alhaitham hit snapshots Physical after final Mirror expires");

        Alhaitham generated = new Alhaitham(null, null, 0);
        CombatSimulator generatedSimulator = simulatorWith(generated);
        List<ActionRecord> generatedRecords = captureActions(
                generatedSimulator);
        perform(generatedSimulator, CharacterActionKey.BURST);
        advanceTo(generatedSimulator, 179.0 * FRAME);
        perform(generatedSimulator, CharacterActionKey.NORMAL);
        ActionRecord generatedNormal = named(generatedRecords,
                "Abductive Reasoning N1").get(0);
        assertEquals(Element.DENDRO,
                generatedNormal.action.getElement(),
                "Alhaitham hit snapshots infusion after delayed Mirror gain");

        Alhaitham accelerated = new Alhaitham(null, null, 0);
        accelerated.addBuff(new SimpleBuff(
                "Alhaitham test Normal speed",
                10.0,
                0.0,
                stats -> stats.add(StatType.NORMAL_ATTACK_SPD, 0.20)));
        CombatSimulator speedSimulator = simulatorWith(accelerated);
        List<ActionRecord> speedRecords = captureActions(speedSimulator);
        perform(speedSimulator, CharacterActionKey.NORMAL);
        assertClose(11.0 * FRAME / 1.20,
                named(speedRecords, "Abductive Reasoning N1").get(0).time,
                "Alhaitham Normal speed adjusts hitmark");
        assertClose(15.0 * FRAME / 1.20 + 5.0 * FRAME,
                speedSimulator.getCurrentTime(),
                "Alhaitham Normal speed adjusts recovery");

        Alhaitham capped = new Alhaitham(null, null, 0);
        capped.addBuff(new SimpleBuff(
                "Alhaitham test EM cap",
                10.0,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 2000.0)));
        CombatSimulator cappedSimulator = simulatorWith(capped);
        List<ActionRecord> cappedRecords = captureActions(cappedSimulator);
        performSkill(cappedSimulator);
        perform(cappedSimulator, CharacterActionKey.NORMAL);
        advanceTo(cappedSimulator, 2.0);
        ActionRecord cappedProjection = named(cappedRecords,
                "Chisel-Light Mirror: Projection Attack 2").get(0);
        assertClose(4569.6,
                cappedProjection.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Alhaitham Projection applies trigger-time EM scaling");
        assertClose(1.0,
                cappedProjection.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Alhaitham A4 caps Projection damage bonus at 100 percent");
    }

    private static void testC4AndC6() {
        Alhaitham c4 = new Alhaitham(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator c4Simulator = simulatorWith(c4, ally);
        perform(c4Simulator, CharacterActionKey.CHARGE);
        assertEquals(1, c4.getMirrorCount(),
                "Alhaitham A1 prepares one consumed C4 Mirror");
        double burstStart = c4Simulator.getCurrentTime();
        perform(c4Simulator, CharacterActionKey.BURST);
        assertClose(30.0, effectiveStat(
                c4Simulator, ally, StatType.ELEMENTAL_MASTERY),
                "Alhaitham C4 grants ally EM per consumed Mirror");
        advanceTo(c4Simulator,
                burstStart + 184.0 * FRAME + EPSILON);
        assertClose(0.20, effectiveStat(
                c4Simulator, c4, StatType.DENDRO_DMG_BONUS) - 0.288,
                "Alhaitham C4 grants owner Dendro bonus per generated Mirror");

        Alhaitham c6 = new Alhaitham(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        performSkill(c6Simulator);
        double c6BurstStart = c6Simulator.getCurrentTime();
        perform(c6Simulator, CharacterActionKey.BURST);
        perform(c6Simulator, CharacterActionKey.CHARGE);
        advanceTo(c6Simulator,
                c6BurstStart + 184.0 * FRAME + EPSILON);
        assertEquals(3, c6.getMirrorCount(),
                "Alhaitham C6 Burst generates three Mirrors");
        assertClose(0.15, effectiveStat(
                c6Simulator, c6, StatType.CRIT_RATE),
                "Alhaitham C6 overflow grants CRIT Rate");
        assertClose(1.20, effectiveStat(
                c6Simulator, c6, StatType.CRIT_DMG),
                "Alhaitham C6 overflow grants CRIT DMG");
        assertClose(c6BurstStart + 184.0 * FRAME + 6.0,
                c6.getC6ExpirationTime(),
                "Alhaitham C6 initial overflow duration");
    }

    private static void testSnapshotRestoreSwitchAndGuards() {
        Alhaitham alhaitham = new Alhaitham(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(alhaitham, ally);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        double burstStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        records.clear();
        double finalHit = burstStart + (94.0 + 7.0 * 21.0) * FRAME;
        advanceTo(simulator, finalHit + EPSILON);
        assertEquals(8, namedPrefix(records,
                "Particular Field: Fetters of Phenomena").size(),
                "Alhaitham snapshot branch resolves eight Burst hits");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, finalHit + EPSILON);
        assertEquals(8, namedPrefix(records,
                "Particular Field: Fetters of Phenomena").size(),
                "Alhaitham repeated restore reconstructs Burst hits once");

        advanceTo(simulator,
                burstStart + 184.0 * FRAME + EPSILON);
        assertEquals(1, alhaitham.getMirrorCount(),
                "Alhaitham restored delayed conversion generates one Mirror");
        simulator.switchCharacter(CharacterId.COLLEI);
        assertEquals(0, alhaitham.getMirrorCount(),
                "Alhaitham switch-out removes Mirrors");

        assertThrows(IllegalArgumentException.class,
                () -> new Alhaitham(null, null, -1),
                "Alhaitham rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Alhaitham(null, null, 7),
                "Alhaitham rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> alhaitham.onAction(null, simulator),
                "Alhaitham rejects null actions");
        assertThrows(IllegalArgumentException.class,
                () -> alhaitham.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Alhaitham rejects Hold Skill in fixed-target slice");

        Alhaitham reused = new Alhaitham(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Alhaitham rejects cross-simulator reuse");
        Alhaitham foreign = new Alhaitham(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!alhaitham.acceptsCharacterState(foreignState),
                "Alhaitham rejects another instance's snapshot payload");
    }

    private static void createMirrors(
            CombatSimulator simulator,
            int mirrors) {
        if (mirrors == 0) {
            return;
        }
        if (mirrors == 1) {
            perform(simulator, CharacterActionKey.CHARGE);
            return;
        }
        performSkill(simulator);
        if (mirrors == 3) {
            perform(simulator, CharacterActionKey.CHARGE);
        }
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
                CharacterId.ALHAITHAM, CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.ALHAITHAM,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ALHAITHAM) {
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

    private static double effectiveStat(
            CombatSimulator simulator,
            Character character,
            StatType stat) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(stat);
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
            assertTrue(lines.get(index).startsWith("Alhaitham,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Alhaitham/Alhaitham_Status.csv",
                "config/characters/Alhaitham/Alhaitham_Multipliers.csv"
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
        throw new AssertionError("Alhaitham CSVs missing key " + key);
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
        @SuppressWarnings("unused")
        private final double count;
        @SuppressWarnings("unused")
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
