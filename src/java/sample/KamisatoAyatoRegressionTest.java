package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import mechanics.buff.Buff;
import model.character.KamisatoAyato;
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

/** Focused regression checks for Ayato's fixed-target Namisen kit. */
public final class KamisatoAyatoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KamisatoAyatoRegressionTest() {
    }

    /** Runs data, timing, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalActions();
        testSkillNamisenParticlesAndC2();
        testBurstC4AndC5();
        testC6AndSkillBoundaries();
        testA4SnapshotAndGuards();
        System.out.println("KamisatoAyatoRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        KamisatoAyato ayato = new KamisatoAyato(null, null, 6);
        assertEquals(CharacterId.KAMISATO_AYATO, ayato.getCharacterId(),
                "Ayato typed identity");
        assertEquals(CharacterId.KAMISATO_AYATO,
                CharacterId.fromName("Kamisato Ayato"),
                "Ayato name lookup");
        assertEquals(CharacterId.KAMISATO_AYATO,
                CharacterId.fromNumericId(48),
                "Ayato numeric lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KAMISATO_AYATO.getRegion(),
                "Ayato region");
        assertEquals(Element.HYDRO, ayato.getElement(), "Ayato element");
        assertClose(13715.0,
                ayato.getBaseStats().get(StatType.BASE_HP),
                "Ayato base HP");
        assertClose(299.0,
                ayato.getBaseStats().get(StatType.BASE_ATK),
                "Ayato base ATK");
        assertClose(769.0,
                ayato.getBaseStats().get(StatType.BASE_DEF),
                "Ayato base DEF");
        assertClose(0.884,
                ayato.getBaseStats().get(StatType.CRIT_DMG),
                "Ayato default plus ascension CRIT DMG");
        assertClose(80.0, ayato.getEnergyCost(), "Ayato Energy cost");
        assertClose(12.0, ayato.getSkillCD(), "Ayato Skill cooldown");
        assertClose(20.0, ayato.getBurstCD(), "Ayato Burst cooldown");
        assertTrue(!ayato.isC1ConditionRepresented(),
                "Ayato C1 is explicitly inactive without target HP");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.KAMISATO_AYATO,
                    new KamisatoAyato(null, null, constellation)
                            .getCharacterId(),
                    "Ayato explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/KamisatoAyato/"
                        + "KamisatoAyato_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/KamisatoAyato/"
                        + "KamisatoAyato_Multipliers.csv"), 42);
        assertCsvValue("Namisen Per Stack C3", 0.012657);
        assertCsvValue("Bloomwater Blade C5", 1.32912);
        assertCsvValue("C6 Multiplier", 4.5);
    }

    private static void testPhysicalActions() {
        KamisatoAyato ayato = new KamisatoAyato(null, null, 0, () -> 0.5);
        CombatSimulator simulator = simulatorWith(ayato);
        List<ActionRecord> records = captureActions(simulator);
        double[][] multipliers = {
            { 0.826040 }, { 0.866377 }, { 1.076833 },
            { 0.541031, 0.541031 }, { 1.389010 }
        };
        int[] durations = { 15, 27, 30, 27, 63 };
        int[] hitlagFrames = { 6, 6, 8, 0, 9 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (double multiplier : multipliers[step]) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multiplier, record.action.getDamagePercent(),
                        "Ayato physical Normal multiplier");
                assertEquals(Element.PHYSICAL, record.action.getElement(),
                        "Ayato physical Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Ayato physical Normal category");
            }
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Ayato physical Normal recovery");
        }
        double chargeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Kamisato Art: Marobashi Charged Attack").get(0);
        assertClose(chargeStart + 24.0 * FRAME, charged.time,
                "Ayato Charged hitmark");
        assertClose(2.379731, charged.action.getDamagePercent(),
                "Ayato Charged multiplier");
        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Kamisato Art: Marobashi High Plunge").get(0);
        assertClose(plungeStart + 47.0 * FRAME, plunge.time,
                "Ayato High Plunge hitmark");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Ayato High Plunge multiplier");
    }

    private static void testSkillNamisenParticlesAndC2() {
        KamisatoAyato ayato = new KamisatoAyato(null, null, 2, () -> 0.25);
        CombatSimulator simulator = simulatorWith(ayato);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        performSkill(simulator);
        assertTrue(ayato.isSkillActive(simulator.getCurrentTime()),
                "Ayato Skill state starts on cast");
        assertEquals(2, ayato.getNamisenStacks(),
                "Ayato A1 starts at two Namisen stacks");
        double shunStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord first = named(records,
                "Kamisato Art: Kyouka Shunsuiken N1").get(0);
        assertClose(shunStart + 5.0 * FRAME, first.time,
                "Ayato Shunsuiken hitmark");
        assertEquals(Element.HYDRO, first.action.getElement(),
                "Ayato Shunsuiken Hydro conversion");
        assertEquals(ICDType.Standard, first.action.getICDType(),
                "Ayato Shunsuiken standard Normal ICD");
        assertClose(13715.0 * 0.010308 * 2.0,
                first.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Ayato first Namisen uses pre-hit stacks and Max HP");
        assertEquals(5, ayato.getNamisenStacks(),
                "Ayato Illusion reaches C2 maximum during recovery");
        assertClose(13715.0 * 1.5,
                effectiveStats(simulator, ayato).getTotalHp(),
                "Ayato C2 grants Max HP at three stacks");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord second = named(records,
                "Kamisato Art: Kyouka Shunsuiken N2").get(0);
        assertClose(13715.0 * 1.5 * 0.010308 * 5.0,
                second.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Ayato later Namisen uses C2 Max HP and maximum stacks");
        advanceTo(simulator, 35.0 * FRAME + EPSILON);
        assertEquals(5, ayato.getNamisenStacks(),
                "Ayato A1 explosion sets C2 maximum stacks");
        ActionRecord illusion = named(records,
                "Kamisato Art: Kyouka Water Illusion").get(0);
        assertClose(1.8644, illusion.action.getDamagePercent(),
                "Ayato C2 Water Illusion uses Talent 9");

        advanceTo(simulator, first.time + 80.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Ayato first accepted Shunsuiken emits particles");
        assertClose(2.0, particles.get(0).count,
                "Ayato low draw emits two particles");
        for (int index = 0; index < 3; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        simulator.advanceTime(80.0 * FRAME + EPSILON);
        assertEquals(2, particles.size(),
                "Ayato particle ICD reopens after 1.9 seconds");
    }

    private static void testBurstC4AndC5() {
        KamisatoAyato ayato = new KamisatoAyato(null, null, 5, () -> 0.5);
        TestCharacter ally = new TestCharacter(CharacterId.COLLEI,
                Element.DENDRO);
        CombatSimulator simulator = simulatorWith(ayato, ally);
        List<ActionRecord> records = captureActions(simulator);
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, ayato.getCurrentEnergy(),
                "Ayato Burst spends Energy at frame 5");
        assertClose(0.15,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Ayato C4 grants party Normal speed at cast");
        assertClose(0.20,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_DMG_BONUS),
                "Ayato C5 Burst grants Talent 12 Normal bonus");
        double normalStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose(normalStart + 15.0 * FRAME / 1.15 + 6.0 * FRAME,
                simulator.getCurrentTime(),
                "Ayato C4 accelerates owner Normal recovery");
        advanceTo(simulator, castTime + 139.0 * FRAME + EPSILON);
        List<ActionRecord> blades = namedPrefix(records,
                "Kamisato Art: Suiyuu Bloomwater Blade");
        assertEquals(1, blades.size(), "Ayato first Burst blade count");
        assertClose(1.32912, blades.get(0).action.getDamagePercent(),
                "Ayato C5 Burst multiplier");
        advanceTo(simulator,
                castTime + (139.0 + 11.0 * 90.0) * FRAME + EPSILON);
        blades = namedPrefix(records,
                "Kamisato Art: Suiyuu Bloomwater Blade");
        assertEquals(12, blades.size(),
                "Ayato fixed target receives twelve Burst blades");
        for (int index = 1; index < blades.size(); index++) {
            assertClose(1.5,
                    blades.get(index).time - blades.get(index - 1).time,
                    "Ayato Burst blade interval");
            assertClose(blades.get(0).action.getStatSnapshot().get(
                            StatType.ATK_PERCENT),
                    blades.get(index).action.getStatSnapshot().get(
                            StatType.ATK_PERCENT),
                    "Ayato Burst reuses one stat snapshot");
        }
        advanceTo(simulator, 15.0 + EPSILON);
        assertClose(0.0,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Ayato C4 expires at fifteen seconds");
        advanceTo(simulator, 101.0 * FRAME + 18.0 + EPSILON);
        assertClose(0.0,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_DMG_BONUS),
                "Ayato Burst Normal bonus expires with the field");
    }

    private static void testC6AndSkillBoundaries() {
        KamisatoAyato ayato = new KamisatoAyato(null, null, 6, () -> 0.5);
        CombatSimulator simulator = simulatorWith(ayato);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        double shunStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord shun = named(records,
                "Kamisato Art: Kyouka Shunsuiken N1 C3").get(0);
        assertClose(1.1931, shun.action.getDamagePercent(),
                "Ayato C3 raises Shunsuiken talent");
        advanceTo(simulator, shun.time + 22.0 * FRAME + EPSILON);
        List<ActionRecord> c6 = namedPrefix(records,
                "Boundless Origin (C6)");
        assertEquals(2, c6.size(), "Ayato C6 creates two extra strikes");
        assertClose(shun.time + 20.0 * FRAME, c6.get(0).time,
                "Ayato C6 first delay");
        assertClose(shun.time + 22.0 * FRAME, c6.get(1).time,
                "Ayato C6 second delay");
        assertClose(0.0, c6.get(0).action.getStatSnapshot().get(
                StatType.FLAT_DMG_BONUS),
                "Ayato C6 strikes exclude Namisen");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, namedPrefix(records,
                "Boundless Origin (C6)").size(),
                "Ayato C6 triggers only on the first accepted Shunsuiken");

        assertThrows(IllegalStateException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Ayato rejects Charged Attack during Skill state");
        double expiration = ayato.getSkillExpirationTime();
        advanceTo(simulator, expiration - 0.05);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord lateShun = named(records,
                "Kamisato Art: Kyouka Shunsuiken N3 C3").get(0);
        assertEquals(Element.HYDRO, lateShun.action.getElement(),
                "Ayato Shunsuiken preserves cast-time infusion");
        assertClose(13715.0 * 1.5 * 0.012657 * 5.0,
                lateShun.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Ayato late Shunsuiken preserves cast-time Namisen");
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord physical = named(records,
                "Kamisato Art: Marobashi N1").get(0);
        assertEquals(Element.PHYSICAL, physical.action.getElement(),
                "Ayato returns to physical string at exact expiry");
    }

    private static void testA4SnapshotAndGuards() {
        AtomicInteger draws = new AtomicInteger();
        KamisatoAyato ayato = new KamisatoAyato(
                null, null, 0,
                () -> draws.getAndIncrement() == 0 ? 0.25 : 0.75);
        TestCharacter ally = new TestCharacter(CharacterId.COLLEI,
                Element.DENDRO);
        CombatSimulator simulator = simulatorWith(ayato, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        ayato.restoreCurrentEnergy(0.0);
        simulator.switchCharacter(CharacterId.COLLEI);
        advanceTo(simulator, 1.0 + EPSILON);
        assertClose(2.0, ayato.getCurrentEnergy(),
                "Ayato A4 restores two off-field Energy per second");
        advanceTo(simulator, 20.0 + EPSILON);
        assertClose(40.0, ayato.getCurrentEnergy(),
                "Ayato A4 stops at forty Energy");

        simulator.switchCharacter(CharacterId.KAMISATO_AYATO);
        performSkill(simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.NORMAL);
        double firstShunTime = named(records,
                "Kamisato Art: Kyouka Shunsuiken N1").get(0).time;
        advanceTo(simulator, firstShunTime + 80.0 * FRAME + EPSILON);
        assertClose(2.0, particles.get(0).count,
                "Ayato original particle draw result");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        firstShunTime = named(records,
                "Kamisato Art: Kyouka Shunsuiken N1").get(0).time;
        advanceTo(simulator, firstShunTime + 80.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Ayato repeated restore reconstructs particle once");
        assertClose(2.0, particles.get(0).count,
                "Ayato rollback replays particle draw tape");
        assertEquals(1, draws.get(),
                "Ayato rollback does not consume another random draw");

        assertThrows(IllegalArgumentException.class,
                () -> new KamisatoAyato(null, null, -1),
                "Ayato rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new KamisatoAyato(null, null, 7),
                "Ayato rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new KamisatoAyato(null, null, 0, null),
                "Ayato rejects null particle draw source");
        assertThrows(IllegalArgumentException.class,
                () -> ayato.onAction(null, simulator),
                "Ayato rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> ayato.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Ayato rejects Hold Skill");

        KamisatoAyato invalidDraw = new KamisatoAyato(
                null, null, 0, () -> 1.1);
        CombatSimulator invalidSimulator = simulatorWith(invalidDraw);
        performSkill(invalidSimulator);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.NORMAL),
                "Ayato rejects particle draws outside [0, 1]");

        KamisatoAyato reused = new KamisatoAyato(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Ayato rejects cross-simulator reuse");
        KamisatoAyato foreign = new KamisatoAyato(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!ayato.acceptsCharacterState(foreignState),
                "Ayato rejects another instance's snapshot payload");
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
                CharacterId.KAMISATO_AYATO,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.KAMISATO_AYATO,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KAMISATO_AYATO) {
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
            assertTrue(lines.get(index).startsWith("Kamisato Ayato,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/KamisatoAyato/"
                        + "KamisatoAyato_Status.csv",
                "config/characters/KamisatoAyato/"
                        + "KamisatoAyato_Multipliers.csv"
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
        throw new AssertionError("Ayato CSVs missing key " + key);
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
