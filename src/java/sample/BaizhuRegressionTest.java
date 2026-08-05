package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Baizhu;
import model.entity.Character;
import model.entity.Enemy;
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

/** Focused checks for Baizhu's represented offensive and reaction slice. */
public final class BaizhuRegressionTest {
    private static final double EPSILON = 1e-8;

    private BaizhuRegressionTest() {
    }

    /** Runs identity, action, Sprite, Spiritvein, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalsChargedAndSkillChain();
        testC1ChargesC2AndCooldown();
        testBurstA4C4AndC6();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("BaizhuRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        Baizhu baizhu = baizhu(0, 0.75);
        assertEquals(CharacterId.BAIZHU, baizhu.getCharacterId(),
                "Baizhu typed identity");
        assertEquals(CharacterId.BAIZHU,
                CharacterId.fromNumericId(73), "Baizhu numeric identity");
        assertEquals(CharacterId.BAIZHU,
                CharacterId.fromName("Baizhu"),
                "Baizhu display-name identity");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.BAIZHU.getRegion(), "Baizhu region");
        assertEquals(Element.DENDRO, baizhu.getElement(),
                "Baizhu element");
        assertClose(13348.0,
                baizhu.getBaseStats().get(StatType.BASE_HP),
                "Baizhu base HP");
        assertClose(193.0,
                baizhu.getBaseStats().get(StatType.BASE_ATK),
                "Baizhu base ATK");
        assertClose(500.0,
                baizhu.getBaseStats().get(StatType.BASE_DEF),
                "Baizhu base DEF");
        assertClose(0.288,
                baizhu.getBaseStats().get(StatType.HP_PERCENT),
                "Baizhu ascension HP");
        assertClose(80.0, baizhu.getEnergyCost(), "Baizhu Energy cost");
        assertClose(10.0, baizhu.getSkillCD(),
                "Baizhu Skill cooldown");
        assertClose(20.0, baizhu.getBurstCD(),
                "Baizhu Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    baizhu(constellation, 0.75).getConstellation(),
                    "Baizhu constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Baizhu/Baizhu_Status.csv"), 27);
        assertCsvShape(Path.of(
                "config/characters/Baizhu/Baizhu_Multipliers.csv"), 10);
        assertThrows(IllegalArgumentException.class,
                () -> baizhu(-1, 0.75),
                "Baizhu rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> baizhu(7, 0.75),
                "Baizhu rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Baizhu(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Baizhu rejects null particle source");
        CombatSimulator simulator = simulatorWith(baizhu);
        assertThrows(IllegalArgumentException.class,
                () -> baizhu.onAction(null, simulator),
                "Baizhu rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Baizhu rejects unpinned Plunge data");
    }

    private static void testNormalsChargedAndSkillChain() {
        Baizhu baizhu = baizhu(0, 0.25);
        CombatSimulator simulator = simulatorWith(baizhu);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureDendroParticles(simulator);
        double[] expected = {
            0.635297, 0.619222, 0.383207, 0.383207, 0.920339
        };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(5, records.size(), "Baizhu Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Baizhu Normal multiplier " + index);
            assertEquals(Element.DENDRO,
                    records.get(index).action.getElement(),
                    "Baizhu Normal element " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, records.size(), "Baizhu Charged hit count");
        assertClose(2.057680,
                records.get(0).action.getDamagePercent(),
                "Baizhu Charged multiplier");
        assertEquals(ActionType.CHARGE,
                records.get(0).action.getActionType(),
                "Baizhu Charged category");

        records.clear();
        perform(simulator, CharacterActionKey.SKILL);
        simulator.advanceTime(3.0);
        List<ActionRecord> skillHits = namedPrefix(
                records, "Universal Diagnosis Hit");
        assertEquals(3, skillHits.size(),
                "Universal Diagnosis chains three fixed-target hits");
        for (ActionRecord record : skillHits) {
            assertClose(1.346400,
                    record.action.getDamagePercent(),
                    "C0 Universal Diagnosis multiplier");
        }
        assertEquals(1, particles.size(),
                "Universal Diagnosis creates one particle packet");
        assertClose(4.0, particles.get(0),
                "Low particle draw selects four Dendro particles");
    }

    private static void testC1ChargesC2AndCooldown() {
        Baizhu c1 = baizhu(1, 0.75);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertTrue(c1.canSkill(c1Simulator.getCurrentTime()),
                "C1 retains a second Skill charge");
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertTrue(!c1.canSkill(c1Simulator.getCurrentTime()),
                "C1 consumes both stored Skill charges");

        Baizhu c2 = baizhu(2, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c2Simulator = simulatorWith(c2, ally);
        List<ActionRecord> records = captureActions(c2Simulator);
        c2Simulator.switchCharacter(CharacterId.NOELLE);
        performAllyHit(c2Simulator);
        assertEquals(1,
                named(records, "Gossamer Sprite: Splice (C2)").size(),
                "Active ally damage triggers C2 once");
        assertClose(2.50,
                named(records, "Gossamer Sprite: Splice (C2)")
                        .get(0).action.getDamagePercent(),
                "C2 uses the sourced 250 percent multiplier");
        performAllyHit(c2Simulator);
        assertEquals(1,
                named(records, "Gossamer Sprite: Splice (C2)").size(),
                "C2 rejects damage inside five-second cooldown");
        c2Simulator.advanceTime(5.0);
        performAllyHit(c2Simulator);
        assertEquals(2,
                named(records, "Gossamer Sprite: Splice (C2)").size(),
                "C2 retriggers at the exact five-second boundary");
    }

    private static void testBurstA4C4AndC6() {
        Baizhu c4 = baizhu(4, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c4, ally);
        List<ActionRecord> records = captureActions(simulator);
        c4.restoreCurrentEnergy(80.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(80.0,
                ally.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ELEMENTAL_MASTERY),
                "C4 grants party Elemental Mastery at Burst cast");
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.advanceTime(15.0);
        List<ActionRecord> spiritveins = named(
                records, "Holistic Revivification Spiritvein");
        assertEquals(6, spiritveins.size(),
                "Natural Burst lifecycle creates six Spiritveins");
        for (ActionRecord spiritvein : spiritveins) {
            assertClose(1.941280,
                    spiritvein.action.getDamagePercent(),
                    "C3 raises Spiritvein multiplier");
            assertClose(0.0,
                    spiritvein.action.getAdditiveBaseDmgBonus(),
                    "C4 Spiritvein has no C6 HP addition");
        }
        double baizhuHpThousands = c4.getEffectiveStats(0.0)
                .getTotalHp() / 1000.0;
        StatsContainer verdant = ally.getEffectiveStats(16.0);
        assertClose(baizhuHpThousands * 0.02,
                verdant.get(StatType.BLOOM_DMG_BONUS),
                "A4 grants active recipient Bloom bonus");
        assertClose(baizhuHpThousands * 0.008,
                verdant.get(StatType.SPREAD_DMG_BONUS),
                "A4 grants active recipient Spread bonus");
        assertClose(baizhuHpThousands * 0.007,
                verdant.get(StatType.LUNAR_BLOOM_DMG_BONUS),
                "A4 grants active recipient Lunar Bloom bonus");
        assertClose(0.0,
                ally.getEffectiveStats(17.0)
                        .get(StatType.ELEMENTAL_MASTERY),
                "C4 expires after fifteen seconds");

        Baizhu c6 = baizhu(6, 0.75);
        TestCharacter c6Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c6Simulator = simulatorWith(c6, c6Ally);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        c6Simulator.switchCharacter(CharacterId.NOELLE);
        c6.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                c6Simulator);
        c6Simulator.advanceTime(3.0);
        List<ActionRecord> c6Spiritveins = named(
                c6Records, "Holistic Revivification Spiritvein");
        assertEquals(1, c6Spiritveins.size(),
                "C6 first Skill hit creates one natural-expiry Spiritvein");
        assertClose(c6.getEffectiveStats(0.0).getTotalHp() * 0.08,
                c6Spiritveins.get(0).action.getAdditiveBaseDmgBonus(),
                "C6 adds eight percent Max HP to Spiritvein");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Baizhu baizhu = baizhu(0, 0.75);
        CombatSimulator simulator = simulatorWith(baizhu);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(2.0);
        assertEquals(3,
                namedPrefix(records, "Universal Diagnosis Hit").size(),
                "First timeline resolves all Skill hits");
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(2.0);
        assertEquals(2,
                namedPrefix(records, "Universal Diagnosis Hit").size(),
                "Restored timeline resolves each remaining Skill hit once");

        Baizhu foreign = baizhu(0, 0.75);
        assertTrue(!baizhu.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Baizhu rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> baizhu.restoreCharacterState(null, simulator),
                "Baizhu rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(baizhu),
                "Baizhu rejects cross-simulator reuse");

        Baizhu invalidRandom = baizhu(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.SKILL),
                "Baizhu rejects out-of-range particle draw");
    }

    private static Baizhu baizhu(int constellation, double particleDraw) {
        return new Baizhu(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
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
                CharacterId.BAIZHU, CharacterActionRequest.of(key));
    }

    private static void performAllyHit(CombatSimulator simulator) {
        AttackAction hit = new AttackAction(
                "Ally Test Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        simulator.performActionWithoutTimeAdvance(CharacterId.NOELLE, hit);
        simulator.advanceTime(14.0 / 60.0);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.BAIZHU) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureDendroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(count);
            }
        });
        return records;
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

    private static List<ActionRecord> namedPrefix(
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

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Baizhu,"),
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
