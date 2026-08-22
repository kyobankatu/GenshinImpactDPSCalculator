package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.KamisatoAyaka;
import model.entity.Character;
import model.entity.Enemy;
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

/** Focused regression checks for Ayaka's fixed-target Frostflake slice. */
public final class KamisatoAyakaRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private KamisatoAyakaRegressionTest() {
    }

    /** Runs identity, timing, cadence, boundary, guard, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndInputGuards();
        testNormalChargedPlungeAndInfusion();
        testSkillParticlesPassivesC1AndCooldown();
        testBurstCadenceSnapshotAndConstellations();
        testC6RollbackAndIsolation();
        System.out.println("KamisatoAyakaRegressionTest passed");
    }

    private static void testIdentityDataAndInputGuards()
            throws IOException {
        KamisatoAyaka ayaka = ayakaAtConstellation(0, 0.25, 0.25);
        assertEquals(CharacterId.KAMISATO_AYAKA, ayaka.getCharacterId(),
                "Ayaka typed identity");
        assertEquals(CharacterId.KAMISATO_AYAKA,
                CharacterId.fromName("Kamisato Ayaka"),
                "Ayaka display identity");
        assertEquals(CharacterId.KAMISATO_AYAKA,
                CharacterId.fromNumericId(69),
                "Ayaka numeric identity");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KAMISATO_AYAKA.getRegion(),
                "Ayaka region");
        assertEquals(Element.CRYO, ayaka.getElement(), "Ayaka element");
        assertClose(12858.0,
                ayaka.getBaseStats().get(StatType.BASE_HP),
                "Ayaka base HP");
        assertClose(342.0,
                ayaka.getBaseStats().get(StatType.BASE_ATK),
                "Ayaka base ATK");
        assertClose(784.0,
                ayaka.getBaseStats().get(StatType.BASE_DEF),
                "Ayaka base DEF");
        assertClose(0.884,
                ayaka.getBaseStats().get(StatType.CRIT_DMG),
                "Ayaka base plus ascension CRIT DMG");
        assertClose(80.0, ayaka.getEnergyCost(), "Ayaka Energy cost");
        assertClose(10.0, ayaka.getSkillCD(), "Ayaka Skill cooldown");
        assertClose(20.0, ayaka.getBurstCD(), "Ayaka Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/KamisatoAyaka/"
                        + "KamisatoAyaka_Status.csv"), 28);
        assertCsvShape(Path.of(
                "config/characters/KamisatoAyaka/"
                        + "KamisatoAyaka_Multipliers.csv"), 15);

        assertThrows(IllegalArgumentException.class,
                () -> ayakaAtConstellation(-1, 0.25, 0.25),
                "Ayaka rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> ayakaAtConstellation(7, 0.25, 0.25),
                "Ayaka rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new KamisatoAyaka(
                        null, null, 0, null, () -> 0.25),
                "Ayaka rejects null particle source");
        assertThrows(IllegalArgumentException.class,
                () -> new KamisatoAyaka(
                        null, null, 0, () -> 0.25, null),
                "Ayaka rejects null C1 source");

        CombatSimulator simulator = simulatorWith(ayaka);
        assertThrows(IllegalArgumentException.class,
                () -> ayaka.onAction(null, simulator),
                "Ayaka rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.KAMISATO_AYAKA,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Ayaka rejects Hold Skill");
        assertThrows(RuntimeException.class,
                () -> simulator.performAction(
                        CharacterId.KAMISATO_AYATO,
                        CharacterActionRequest.of(CharacterActionKey.NORMAL)),
                "Ayaka simulator rejects wrong typed identity");

        KamisatoAyaka invalidParticle = ayakaAtConstellation(0, 1.1, 0.25);
        CombatSimulator particleSimulator = simulatorWith(invalidParticle);
        assertThrows(IllegalStateException.class,
                () -> perform(particleSimulator, CharacterActionKey.SKILL),
                "Ayaka rejects out-of-range particle draw");
        KamisatoAyaka invalidC1 = ayakaAtConstellation(1, 0.25, -0.1);
        CombatSimulator c1Simulator = simulatorWith(invalidC1);
        perform(c1Simulator, CharacterActionKey.DASH);
        assertThrows(IllegalStateException.class,
                () -> perform(c1Simulator, CharacterActionKey.NORMAL),
                "Ayaka rejects out-of-range C1 draw");
    }

    private static void testNormalChargedPlungeAndInfusion() {
        KamisatoAyaka ayaka = ayakaAtConstellation(0, 0.25, 0.25);
        CombatSimulator simulator = simulatorWith(ayaka);
        List<ActionRecord> records = captureActions(simulator);
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(7, records.size(), "Ayaka Normal hit count");
        double[] multipliers = {
            0.840070, 0.894438, 1.150493,
            0.416061, 0.416061, 0.416061, 1.436362
        };
        double[] times = {
            8.0 * FRAME,
            (32.0 + 6.0) * FRAME,
            (58.0 + 12.0) * FRAME,
            (82.0 + 20.0) * FRAME,
            (89.0 + 20.0) * FRAME,
            (96.0 + 20.0) * FRAME,
            (124.0 + 26.0) * FRAME
        };
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Ayaka Normal multiplier " + index);
            assertClose(times[index], record.time,
                    "Ayaka Normal hit time " + index);
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Ayaka uninfused Normal element " + index);
            assertEquals(ActionType.NORMAL, record.action.getActionType(),
                    "Ayaka Normal category " + index);
        }
        assertEquals(ICDTag.Ayaka_NormalFive,
                records.get(6).action.getICDTag(),
                "Ayaka N5 owns separate ICD");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(3, records.size(), "Ayaka Charged hit count");
        for (int index = 0; index < records.size(); index++) {
            assertClose(1.012780,
                    records.get(index).action.getDamagePercent(),
                    "Ayaka Charged multiplier " + index);
            assertEquals(ICDTag.Ayaka_Charged,
                    records.get(index).action.getICDTag(),
                    "Ayaka Charged typed ICD " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Ayaka high Plunge hit count");
        assertClose(2.933586, records.get(0).action.getDamagePercent(),
                "Ayaka high Plunge multiplier");
        assertEquals(ICDType.None, records.get(0).action.getICDType(),
                "Ayaka high Plunge has no ICD");

        KamisatoAyaka infused = ayakaAtConstellation(0, 0.25, 0.25);
        CombatSimulator infusedSimulator = simulatorWith(infused);
        List<ActionRecord> infusedRecords = captureActions(infusedSimulator);
        perform(infusedSimulator, CharacterActionKey.DASH);
        double dashHitTime = 20.0 * FRAME;
        assertTrue(infused.isCryoInfusionActive(dashHitTime),
                "Ayaka infusion starts at Senho exit hit");
        assertTrue(infused.isA4Active(dashHitTime),
                "Ayaka A4 starts on Senho exit hit");
        infusedRecords.clear();
        perform(infusedSimulator, CharacterActionKey.NORMAL);
        assertEquals(Element.CRYO,
                infusedRecords.get(0).action.getElement(),
                "Ayaka Normal resolves Cryo during infusion");
        advanceTo(infusedSimulator, dashHitTime + 5.0);
        assertTrue(!infused.isCryoInfusionActive(
                infusedSimulator.getCurrentTime()),
                "Ayaka infusion expires at exact boundary");
        infusedRecords.clear();
        perform(infusedSimulator, CharacterActionKey.NORMAL);
        assertEquals(Element.PHYSICAL,
                infusedRecords.get(0).action.getElement(),
                "Ayaka Normal returns to Physical after infusion");
        advanceTo(infusedSimulator, dashHitTime + 10.0);
        assertTrue(!infused.isA4Active(infusedSimulator.getCurrentTime()),
                "Ayaka A4 expires at exact boundary");
    }

    private static void testSkillParticlesPassivesC1AndCooldown() {
        KamisatoAyaka c5 = ayakaAtConstellation(5, 0.25, 0.75);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, records.size(), "Ayaka Skill hit count");
        assertClose(33.0 * FRAME, records.get(0).time,
                "Ayaka Skill hitmark");
        assertClose(4.784000, records.get(0).action.getDamagePercent(),
                "Ayaka C5 Skill multiplier");
        assertClose(0.0, c5.getLastSkillTime(),
                "Ayaka Skill cooldown starts at cast");
        assertTrue(c5.isA1Active(simulator.getCurrentTime()),
                "Ayaka A1 starts at Skill cast");
        StatsContainer a1Stats = effectiveStats(c5, simulator);
        assertClose(0.30,
                a1Stats.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Ayaka A1 Normal bonus");
        assertClose(0.30,
                a1Stats.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Ayaka A1 Charged bonus");
        advanceTo(simulator, 133.0 * FRAME);
        assertEquals(1, particles.size(), "Ayaka particle packet count");
        assertClose(4.0, particles.get(0), "Ayaka low particle outcome");
        records.clear();
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(10.0 + 33.0 * FRAME, records.get(0).time,
                "Ayaka second Skill waits for cooldown");

        KamisatoAyaka c1 = ayakaAtConstellation(1, 0.75, 0.75);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.DASH);
        perform(c1Simulator, CharacterActionKey.NORMAL);
        assertClose(9.7 - c1Simulator.getCurrentTime(),
                c1.getSkillCDRemaining(c1Simulator.getCurrentTime()),
                "Ayaka C1 reduces active Skill cooldown by 0.3 seconds");
        assertClose(0.18,
                effectiveStats(c1, c1Simulator)
                        .get(StatType.CRYO_DMG_BONUS),
                "Ayaka A4 Cryo bonus");

        KamisatoAyaka failedC1 = ayakaAtConstellation(1, 0.25, 0.25);
        CombatSimulator failedSimulator = simulatorWith(failedC1);
        perform(failedSimulator, CharacterActionKey.SKILL);
        perform(failedSimulator, CharacterActionKey.DASH);
        perform(failedSimulator, CharacterActionKey.NORMAL);
        assertClose(10.0 - failedSimulator.getCurrentTime(),
                failedC1.getSkillCDRemaining(failedSimulator.getCurrentTime()),
                "Ayaka failed C1 draw does not reduce cooldown");

        KamisatoAyaka noEnergy = ayakaAtConstellation(0, 0.25, 0.25);
        CombatSimulator noEnergySimulator = simulatorWith(noEnergy);
        noEnergy.spendEnergy(noEnergy.getCurrentEnergy());
        List<ActionRecord> noEnergyRecords = captureActions(noEnergySimulator);
        perform(noEnergySimulator, CharacterActionKey.BURST);
        assertEquals(0, noEnergyRecords.size(),
                "Ayaka insufficient Energy rejects Burst");
        assertEquals(0, noEnergy.getPendingHitCount(),
                "Ayaka rejected Burst queues no work");
    }

    private static void testBurstCadenceSnapshotAndConstellations() {
        KamisatoAyaka c0 = ayakaAtConstellation(0, 0.25, 0.25);
        CombatSimulator c0Simulator = simulatorWith(c0);
        c0.addBuff(new SimpleBuff(
                "Ayaka cast ATK",
                1.0,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 0.50)));
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Ayaka Burst spends Energy at frame eight");
        assertClose(20.0 - 125.0 * FRAME,
                c0.getBurstCDRemaining(c0Simulator.getCurrentTime()),
                "Ayaka Burst cooldown starts at cast");
        advanceTo(c0Simulator, burstEndTime() + EPSILON);
        List<ActionRecord> c0Burst = named(c0Records, "Soumetsu");
        assertEquals(20, c0Burst.size(),
                "Ayaka C0 Burst has nineteen cuts and one Bloom");
        for (int cut = 0; cut < 19; cut++) {
            ActionRecord record = c0Burst.get(cut);
            assertClose((104.0 + 15.0 * cut) * FRAME,
                    record.time,
                    "Ayaka Burst cut cadence " + cut);
            assertClose(1.909100, record.action.getDamagePercent(),
                    "Ayaka C0 Burst cut multiplier " + cut);
            assertClose(0.50,
                    record.action.getStatSnapshot()
                            .get(StatType.ATK_PERCENT),
                    "Ayaka Burst keeps cast snapshot " + cut);
        }
        ActionRecord bloom = c0Burst.get(19);
        assertClose(burstEndTime(), bloom.time,
                "Ayaka Burst Bloom timing");
        assertClose(2.863650, bloom.action.getDamagePercent(),
                "Ayaka C0 Burst Bloom multiplier");

        KamisatoAyaka c4 = ayakaAtConstellation(4, 0.25, 0.25);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        perform(c4Simulator, CharacterActionKey.BURST);
        advanceTo(c4Simulator, burstEndTime() + EPSILON);
        List<ActionRecord> c4Burst = named(c4Records, "Soumetsu");
        assertEquals(60, c4Burst.size(),
                "Ayaka C2 adds two complete mini storms");
        assertClose(2.246000,
                find(c4Burst, "Cutting 1").action.getDamagePercent(),
                "Ayaka C3 main cut multiplier");
        assertClose(0.449200,
                find(c4Burst, "Mini 1 Cutting 1")
                        .action.getDamagePercent(),
                "Ayaka C2 mini cut factor");
        assertClose(0.673800,
                find(c4Burst, "Mini 1 Bloom")
                        .action.getDamagePercent(),
                "Ayaka C2 mini Bloom factor");
        ActionRecord firstMain = find(c4Burst, "Cutting 1");
        ActionRecord secondMain = find(c4Burst, "Cutting 2");
        assertTrue(secondMain.damage > firstMain.damage,
                "Ayaka later Burst cuts benefit from C4 DEF reduction");
        assertTrue(c4.isC4Active(burstEndTime()),
                "Ayaka C4 refreshes on final Bloom");
        advanceTo(c4Simulator, burstEndTime() + 6.0);
        assertTrue(!c4.isC4Active(c4Simulator.getCurrentTime()),
                "Ayaka C4 expires six seconds after final hit");
    }

    private static void testC6RollbackAndIsolation() {
        KamisatoAyaka c6 = ayakaAtConstellation(6, 0.75, 0.75);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        assertTrue(c6.isC6Ready(), "Ayaka C6 starts ready");
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertEquals(3, c6Records.size(), "Ayaka C6 Charged hit count");
        for (ActionRecord record : c6Records) {
            assertClose(2.98,
                    record.action.getExtraBonuses().getOrDefault(
                            StatType.CHARGED_ATTACK_DMG_BONUS, 0.0),
                    "Ayaka C6 empowers all three Charged hits");
        }
        assertTrue(!c6.isC6Ready(), "Ayaka C6 is consumed on first hit");
        double readyTime = 27.0 * FRAME + 10.5;
        advanceTo(c6Simulator, readyTime - EPSILON);
        assertTrue(!c6.isC6Ready(),
                "Ayaka C6 remains unavailable before exact recovery");
        advanceTo(c6Simulator, readyTime);
        assertTrue(c6.isC6Ready(),
                "Ayaka C6 recovers at exact source-backed delay");

        KamisatoAyaka rollback = ayakaAtConstellation(4, 0.25, 0.25);
        CombatSimulator rollbackSimulator = simulatorWith(rollback);
        List<ActionRecord> rollbackRecords = captureActions(rollbackSimulator);
        perform(rollbackSimulator, CharacterActionKey.DASH);
        perform(rollbackSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = rollbackSimulator.saveSnapshot();
        int resolvedAtSnapshot = rollbackRecords.size();
        double snapshotTime = rollbackSimulator.getCurrentTime();
        rollbackSimulator.advanceTime(8.0);
        int futureHits = rollbackRecords.size() - resolvedAtSnapshot;
        assertTrue(futureHits > 0,
                "Ayaka snapshot retains future Burst hits");
        rollbackSimulator.restoreSnapshot(snapshot);
        rollbackRecords.clear();
        assertTrue(rollback.isCryoInfusionActive(
                rollbackSimulator.getCurrentTime()),
                "Ayaka rollback restores Senho infusion window");
        advanceTo(rollbackSimulator, snapshotTime + 8.0);
        assertEquals(futureHits, rollbackRecords.size(),
                "Ayaka restored Burst sequence resolves once");

        KamisatoAyaka foreign = ayakaAtConstellation(0, 0.25, 0.25);
        assertTrue(!rollback.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Ayaka rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> rollback.restoreCharacterState(null, rollbackSimulator),
                "Ayaka rejects null rollback state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(rollback),
                "Ayaka rejects cross-simulator reuse");
    }

    private static double burstEndTime() {
        return 404.0 * FRAME;
    }

    private static KamisatoAyaka ayakaAtConstellation(
            int constellation,
            double particleDraw,
            double c1Draw) {
        return new KamisatoAyaka(
                null,
                null,
                constellation,
                () -> particleDraw,
                () -> c1Draw);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
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
                CharacterId.KAMISATO_AYAKA,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KAMISATO_AYAKA) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<Double> captureCryoParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.CRYO) {
                records.add(count);
            }
        });
        return records;
    }

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator) {
        double time = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String fragment) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().contains(fragment)) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static ActionRecord find(
            List<ActionRecord> records,
            String suffix) {
        for (ActionRecord record : records) {
            if (record.action.getName().endsWith(suffix)) {
                return record;
            }
        }
        throw new AssertionError("Missing Ayaka action ending with " + suffix);
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        double delta = targetTime - simulator.getCurrentTime();
        if (delta > 0.0) {
            simulator.advanceTime(delta);
        }
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
            assertTrue(lines.get(index).startsWith("Kamisato Ayaka,"),
                    path + " identity at line " + (index + 1));
        }
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
}
