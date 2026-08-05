package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataManager;
import model.character.Nilou;
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

/** Focused checks for Nilou's represented fixed-target dance slice. */
public final class NilouRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private NilouRegressionTest() {
    }

    /** Runs identity, cadence, constellation, guard, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalAndChargedAttacks();
        testSwordDanceParticlesC1C4AndBurst();
        testWhirlingAuraCompositionC2AndC5();
        testC6AndUnsupportedBloomBoundary();
        testSnapshotRestoreAndWrongState();
        System.out.println("NilouRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        Nilou nilou = nilou(0, 0.25);
        assertEquals(CharacterId.NILOU, nilou.getCharacterId(),
                "Nilou typed identity");
        assertEquals(CharacterId.NILOU, CharacterId.fromNumericId(71),
                "Nilou numeric identity");
        assertEquals(CharacterId.NILOU, CharacterId.fromName("Nilou"),
                "Nilou display-name identity");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.NILOU.getRegion(), "Nilou region");
        assertEquals(Element.HYDRO, nilou.getElement(), "Nilou element");
        assertClose(15185.0,
                nilou.getBaseStats().get(StatType.BASE_HP),
                "Nilou base HP");
        assertClose(230.0,
                nilou.getBaseStats().get(StatType.BASE_ATK),
                "Nilou base ATK");
        assertClose(729.0,
                nilou.getBaseStats().get(StatType.BASE_DEF),
                "Nilou base DEF");
        assertClose(0.288,
                nilou.getBaseStats().get(StatType.HP_PERCENT),
                "Nilou ascension HP");
        assertClose(70.0, nilou.getEnergyCost(), "Nilou Energy cost");
        assertClose(18.0, nilou.getSkillCD(), "Nilou Skill cooldown");
        assertClose(18.0, nilou.getBurstCD(), "Nilou Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    nilou(constellation, 0.25).getConstellation(),
                    "Nilou constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Nilou/Nilou_Status.csv"), 27);
        assertCsvShape(Path.of(
                "config/characters/Nilou/Nilou_Multipliers.csv"), 23);
        assertCsvValue("Luminous Illusion C5", 0.143376);
        assertCsvValue("Lingering Aeon C3", 0.450560);

        assertThrows(IllegalArgumentException.class,
                () -> nilou(-1, 0.25),
                "Nilou rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> nilou(7, 0.25),
                "Nilou rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Nilou(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Nilou rejects null particle randomness");

        CombatSimulator simulator = simulatorWith(nilou);
        assertThrows(IllegalArgumentException.class,
                () -> nilou.onAction(null, simulator),
                "Nilou rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.NILOU,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Nilou rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Nilou rejects unrepresented Plunge");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Nilou rejects movement action");
    }

    private static void testNormalAndChargedAttacks() {
        Nilou nilou = nilou(0, 0.75);
        CombatSimulator simulator = simulatorWith(nilou);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = { 0.924253, 0.834809, 1.292551 };
        for (int step = 0; step < 3; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = prefixed(records, "Dance of Samser N");
        assertEquals(3, normals.size(), "Nilou Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    normals.get(index).action.getDamagePercent(),
                    "Nilou Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Nilou Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    normals.get(index).action.getActionType(),
                    "Nilou Normal category " + index);
        }
        assertClose(12.0 * FRAME, normals.get(0).time,
                "Nilou N1 hit frame");
        assertClose((24.0 + 9.0) * FRAME, normals.get(1).time,
                "Nilou N2 hit frame");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = prefixed(
                records, "Dance of Samser Charged");
        assertEquals(2, charged.size(), "Nilou Charged hit count");
        assertClose(0.922720,
                charged.get(0).action.getDamagePercent(),
                "Nilou Charged first multiplier");
        assertClose(1.000140,
                charged.get(1).action.getDamagePercent(),
                "Nilou Charged second multiplier");
        assertClose(FRAME,
                charged.get(1).time - charged.get(0).time,
                "Nilou Charged consecutive hit timing");
    }

    private static void testSwordDanceParticlesC1C4AndBurst() {
        Nilou nilou = nilou(4, 0.25);
        nilou.spendEnergy(20.0);
        CombatSimulator simulator = simulatorWith(
                nilou,
                new TestCharacter(CharacterId.NAHIDA, Element.DENDRO));
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureHydroParticles(simulator);

        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(nilou.isPirouetteActive(simulator.getCurrentTime()),
                "Nilou Skill enters Pirouette");
        assertClose(0.0,
                nilou.getSkillCDRemaining(simulator.getCurrentTime()),
                "Pirouette continuation bypasses Skill cooldown");
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(!nilou.isPirouetteActive(simulator.getCurrentTime()),
                "Third Sword Dance step ends Pirouette");
        assertTrue(nilou.isLunarPrayerActive(
                simulator.getCurrentTime()),
                "Sword route starts Lunar Prayer");
        assertTrue(nilou.isGoldenChaliceActive(
                simulator.getCurrentTime()),
                "valid composition starts Golden Chalice");
        assertEquals(0, nilou.getDanceStep(),
                "completed Sword route resets dance step");

        List<ActionRecord> illusion = named(
                records, "Luminous Illusion");
        assertEquals(1, illusion.size(),
                "Sword route creates one Luminous Illusion");
        assertClose(0.121870,
                illusion.get(0).action.getDamagePercent(),
                "C4 uses Talent 9 Skill multiplier");
        assertClose(0.65,
                illusion.get(0).action.getExtraBonuses()
                        .getOrDefault(StatType.SKILL_DMG_BONUS, 0.0),
                "C1 enhances Luminous Illusion by 65 percent");
        assertClose(15.0, nilou.getTotalFlatEnergy(),
                "C4 final step restores fifteen flat Energy");

        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, simulator.getCurrentTime() + 2.0);
        AttackAction initial = named(records, "Dance of Abzendegi")
                .get(0).action;
        AttackAction aeon = named(records, "Lingering Aeon")
                .get(0).action;
        assertClose(0.368640, initial.getDamagePercent(),
                "C3 raises initial Burst multiplier");
        assertClose(0.450560, aeon.getDamagePercent(),
                "C3 raises Lingering Aeon multiplier");
        assertEquals(StatType.BASE_HP, initial.getScalingStat(),
                "Nilou Burst scales from Max HP");
        assertClose(0.50,
                initial.getExtraBonuses().getOrDefault(
                        StatType.BURST_DMG_BONUS, 0.0),
                "C4 enhances initial Burst damage");
        assertClose(0.50,
                aeon.getExtraBonuses().getOrDefault(
                        StatType.BURST_DMG_BONUS, 0.0),
                "C4 enhances delayed Burst damage");
        assertClose(9.0, nilou.getCurrentEnergy(),
                "Nilou Burst spends Energy before late particles arrive");
        assertEquals(4, particles.size(),
                "initial and three Pirouette hits create four packets");
        assertClose(2.0, particles.get(0),
                "low draw creates two initial particles");
        for (int index = 1; index < particles.size(); index++) {
            assertClose(1.0, particles.get(index),
                    "Pirouette packet count " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, named(records, "Sword Dance 1").size(),
                "Lunar Prayer converts Normal to Sword Dance");
        assertEquals(1, named(records, "Sword Dance 2").size(),
                "Lunar Prayer preserves second Sword Dance step");
        assertEquals(1, named(records, "Luminous Illusion").size(),
                "Lunar Prayer preserves third Sword Dance step");
        assertEquals(0, prefixed(records, "Dance of Samser N").size(),
                "Lunar Prayer suppresses physical Normal");
    }

    private static void testWhirlingAuraCompositionC2AndC5() {
        Nilou nilou = nilou(5, 0.75);
        TestCharacter dendro = new TestCharacter(
                CharacterId.NAHIDA, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(nilou, dendro);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);

        assertTrue(nilou.isTranquilityAuraActive(
                simulator.getCurrentTime()),
                "Whirling route starts Tranquility Aura");
        assertTrue(!nilou.isLunarPrayerActive(
                simulator.getCurrentTime()),
                "Whirling route does not start Lunar Prayer");
        assertClose(0.101232,
                named(records, "Water Wheel").get(0)
                        .action.getDamagePercent(),
                "C5 raises Water Wheel multiplier");
        List<ActionRecord> aura = named(records, "Tranquility Aura");
        assertTrue(!aura.isEmpty(),
                "Tranquility Aura begins applying Hydro");
        assertEquals(ICDType.NilouTranquility,
                aura.get(0).action.getICDType(),
                "Tranquility Aura uses Nilou ICD type");
        assertEquals(ICDTag.Nilou_TranquilityAura,
                aura.get(0).action.getICDTag(),
                "Tranquility Aura uses an independent tag");
        assertTrue(!aura.get(0).action.isHitEffectTrigger(),
                "zero-damage Aura does not trigger hit effects");
        assertClose(18.0,
                nilou.getTranquilityExpirationTime()
                        - (simulator.getCurrentTime() - 23.0 * FRAME),
                "C1 extends Tranquility Aura to eighteen seconds");

        StatsContainer dendroStats = effectiveStats(dendro, simulator);
        assertClose(0.35,
                dendroStats.get(StatType.HYDRO_RES_SHRED),
                "C2 Hydro damage applies team Hydro RES shred");
        assertTrue(hasApplicableBuff(
                        simulator,
                        dendro,
                        BuffId.NILOU_C2_HYDRO_RES_SHRED),
                "C2 uses typed non-stacking buff");
        double expiration = nilou.getTranquilityExpirationTime();
        assertTrue(nilou.isTranquilityAuraActive(expiration - FRAME),
                "Tranquility Aura remains active before boundary");
        assertTrue(!nilou.isTranquilityAuraActive(expiration),
                "Tranquility Aura expires at half-open boundary");

        Nilou invalid = nilou(2, 0.75);
        TestCharacter geo = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator invalidSimulator = simulatorWith(invalid, geo);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        assertTrue(!invalid.isGoldenChaliceActive(
                invalidSimulator.getCurrentTime()),
                "non-Hydro-Dendro composition blocks Golden Chalice");
        assertTrue(!hasApplicableBuff(
                        invalidSimulator,
                        geo,
                        BuffId.NILOU_C2_HYDRO_RES_SHRED),
                "wrong composition blocks C2 shred");
    }

    private static void testC6AndUnsupportedBloomBoundary() {
        Nilou c6 = nilou(6, 0.75);
        StatsContainer stats = c6.getEffectiveStats(0.0);
        double hp = 15185.0 * 1.288;
        assertClose(0.05 + hp / 1000.0 * 0.006,
                stats.get(StatType.CRIT_RATE),
                "C6 derives CRIT Rate from Max HP");
        assertClose(0.50 + hp / 1000.0 * 0.012,
                stats.get(StatType.CRIT_DMG),
                "C6 derives CRIT DMG from Max HP");
        assertTrue(!c6.isBountifulCoreReplacementRepresented(),
                "unsupported Core replacement fails closed");

        Nilou capped = new Nilou(
                null,
                null,
                (character, key, defaultValue) -> {
                    if ("Base HP".equals(key)) {
                        return 100000.0;
                    }
                    if ("Ascension HP Percent".equals(key)) {
                        return 0.0;
                    }
                    return defaultValue;
                },
                6,
                () -> 0.75);
        StatsContainer cappedStats = capped.getEffectiveStats(0.0);
        assertClose(0.35, cappedStats.get(StatType.CRIT_RATE),
                "C6 CRIT Rate respects cap");
        assertClose(1.10, cappedStats.get(StatType.CRIT_DMG),
                "C6 CRIT DMG respects cap");
    }

    private static void testSnapshotRestoreAndWrongState() {
        Nilou nilou = nilou(1, 0.75);
        CombatSimulator simulator = simulatorWith(
                nilou,
                new TestCharacter(CharacterId.NAHIDA, Element.DENDRO));
        perform(simulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Pirouette rejects Charged input");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(1, nilou.getDanceStep(),
                "first step advances dance state");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();

        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(nilou.isLunarPrayerActive(
                simulator.getCurrentTime()),
                "mixed branch uses final Normal route");

        simulator.restoreSnapshot(snapshot);
        assertEquals(1, nilou.getDanceStep(),
                "rollback restores dance step");
        assertTrue(nilou.isPirouetteActive(simulator.getCurrentTime()),
                "rollback restores Pirouette window");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(nilou.isTranquilityAuraActive(
                simulator.getCurrentTime()),
                "restored branch can choose final Skill route");
        assertTrue(!nilou.isLunarPrayerActive(
                simulator.getCurrentTime()),
                "restored Skill route excludes Lunar Prayer");

        Nilou foreign = nilou(0, 0.75);
        assertTrue(!nilou.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Nilou rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> nilou.restoreCharacterState(null, simulator),
                "Nilou rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(nilou),
                "Nilou rejects cross-simulator reuse");

        Nilou invalidRandom = nilou(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(
                        invalidSimulator,
                        CharacterActionKey.SKILL),
                "Nilou rejects out-of-range particle draw");
    }

    private static Nilou nilou(int constellation, double particleDraw) {
        return new Nilou(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
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
                CharacterId.NILOU,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NILOU) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureHydroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
                records.add(count);
            }
        });
        return records;
    }

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator) {
        StatsContainer stats = character.getEffectiveStats(
                simulator.getCurrentTime());
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(simulator.getCurrentTime())) {
                buff.apply(stats, simulator.getCurrentTime());
            }
        }
        return stats;
    }

    private static boolean hasApplicableBuff(
            CombatSimulator simulator,
            Character character,
            BuffId id) {
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (buff.getId() == id
                    && !buff.isExpired(simulator.getCurrentTime())) {
                return true;
            }
        }
        return false;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (name.equals(record.action.getName())) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static List<ActionRecord> prefixed(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                matches.add(record);
            }
        }
        return matches;
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
            assertTrue(lines.get(index).startsWith("Nilou,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
            "config/characters/Nilou/Nilou_Status.csv",
            "config/characters/Nilou/Nilou_Multipliers.csv"
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
        throw new AssertionError("Nilou CSVs missing key " + key);
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
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
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
    }
}
