package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Bennett;
import model.character.Layla;
import model.entity.Character;
import model.entity.Enemy;
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

/** Focused checks for Layla's fixed-target offensive Night Star slice. */
public final class LaylaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private LaylaRegressionTest() {
    }

    /** Runs identity, cadence, constellation, Burst, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testNormalChargedAndPlungeActions();
        testSkillCadenceParticlesExpiryAndCooldown();
        testPartySkillNightStarTrigger();
        testAscensionAndConstellationEffects();
        testBurstCadenceAndEnergy();
        testSnapshotRestoreAndWrongStateBehavior();
        System.out.println("LaylaRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Layla layla = layla(0, 0.5);
        assertEquals(CharacterId.LAYLA, layla.getCharacterId(),
                "Layla typed identity");
        assertEquals(CharacterId.LAYLA, CharacterId.fromNumericId(67),
                "Layla numeric identity");
        assertEquals(CharacterId.LAYLA, CharacterId.fromName("Layla"),
                "Layla display-name identity");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.LAYLA.getRegion(), "Layla region");
        assertEquals(Element.CRYO, layla.getElement(), "Layla element");
        assertClose(11092.0,
                layla.getBaseStats().get(StatType.BASE_HP),
                "Layla base HP");
        assertClose(217.0,
                layla.getBaseStats().get(StatType.BASE_ATK),
                "Layla base ATK");
        assertClose(655.0,
                layla.getBaseStats().get(StatType.BASE_DEF),
                "Layla base DEF");
        assertClose(0.24,
                layla.getBaseStats().get(StatType.HP_PERCENT),
                "Layla ascension HP");
        assertClose(40.0, layla.getEnergyCost(), "Layla Energy cost");
        assertClose(12.0, layla.getSkillCD(), "Layla Skill cooldown");
        assertClose(12.0, layla.getBurstCD(), "Layla Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    layla(constellation, 0.5).getConstellation(),
                    "Layla constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Layla/Layla_Status.csv"), 23);
        assertCsvShape(Path.of(
                "config/characters/Layla/Layla_Multipliers.csv"), 12);
        assertThrows(IllegalArgumentException.class,
                () -> layla(-1, 0.5),
                "Layla rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> layla(7, 0.5),
                "Layla rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Layla(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Layla rejects null particle randomness");

        CombatSimulator simulator = simulatorWith(layla);
        assertThrows(IllegalArgumentException.class,
                () -> layla.onAction(null, simulator),
                "Layla rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.LAYLA,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Layla rejects Hold Skill");
    }

    private static void testNormalChargedAndPlungeActions() {
        Layla layla = layla(0, 0.5);
        CombatSimulator simulator = simulatorWith(layla);
        List<ActionRecord> records = captureActions(simulator);
        double[] expectedNormals = {
            0.940969, 0.890741, 1.340677
        };
        for (int step = 0; step < expectedNormals.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertEquals("Sword of the Radiant Path N" + (step + 1),
                    record.action.getName(),
                    "Layla Normal name " + step);
            assertClose(expectedNormals[step],
                    record.action.getDamagePercent(),
                    "Layla Normal multiplier " + step);
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Layla Normal element " + step);
        }

        records.clear();
        double chargeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(2, records.size(), "Layla Charged hit count");
        assertClose(chargeStart + 16.0 * FRAME,
                records.get(0).time, "Layla Charged first hit timing");
        assertClose(0.876900,
                records.get(0).action.getDamagePercent(),
                "Layla Charged first multiplier");
        assertClose(0.965380,
                records.get(1).action.getDamagePercent(),
                "Layla Charged second multiplier");

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Layla high Plunge hit count");
        assertClose(2.933586,
                records.get(0).action.getDamagePercent(),
                "Layla high Plunge multiplier");
    }

    private static void testSkillCadenceParticlesExpiryAndCooldown() {
        Layla layla = layla(0, 0.5);
        CombatSimulator simulator = simulatorWith(layla);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, named(records, "Nights of Formal Focus").size(),
                "Layla Skill initial hit count");
        assertTrue(layla.isCurtainActive(simulator.getCurrentTime()),
                "Layla offensive Curtain activates at frame 19");
        assertClose(739.0 * FRAME,
                layla.getCurtainExpirationTime(),
                "Layla Curtain expires twelve seconds after frame 19");
        assertClose(739.0 * FRAME,
                layla.getSkillCooldownEndTime(),
                "Layla Skill cooldown starts at frame 19");

        advanceTo(simulator, 450.0 * FRAME);
        List<ActionRecord> shooting = startingWith(records,
                "Shooting Star");
        assertEquals(1, shooting.size(),
                "Layla first cadence volley hits at frame 450");
        assertClose(450.0 * FRAME, shooting.get(0).time,
                "Layla first Shooting Star timing");
        assertClose(0.250240,
                shooting.get(0).action.getDamagePercent(),
                "Layla C0 Shooting Star multiplier");
        assertClose(11092.0 * 1.24 * 0.015,
                shooting.get(0).action.getAdditiveBaseDmgBonus(),
                "Layla A4 uses fire-time Max HP");

        advanceTo(simulator, 550.0 * FRAME);
        shooting = startingWith(records, "Shooting Star");
        assertEquals(4, shooting.size(),
                "Layla cadence volley contains four projectiles");
        assertEquals(1, particles.size(),
                "Layla volley creates one particle packet");
        assertClose(1.0, particles.get(0),
                "Layla high particle draw creates one particle");

        int hitCount = shooting.size();
        advanceTo(simulator, 740.0 * FRAME);
        assertTrue(!layla.isCurtainActive(simulator.getCurrentTime()),
                "Layla Curtain expires at its exact boundary");
        assertEquals(0, layla.getNightStarCount(
                simulator.getCurrentTime()),
                "Layla clears non-volley Night Stars on expiry");
        assertEquals(hitCount,
                startingWith(records, "Shooting Star").size(),
                "Layla expired Curtain stops future cadence damage");
    }

    private static void testAscensionAndConstellationEffects() {
        Layla c2 = layla(2, 0.5);
        CombatSimulator c2Simulator = simulatorWith(c2);
        c2.restoreCurrentEnergy(0.0);
        perform(c2Simulator, CharacterActionKey.SKILL);
        advanceTo(c2Simulator, 450.0 * FRAME);
        assertClose(1.0, c2.getCurrentEnergy(),
                "Layla C2 restores one Energy on first star hit");

        Layla c3 = layla(3, 0.5);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        assertClose(0.256000,
                named(c3Records, "Nights of Formal Focus")
                        .get(0).action.getDamagePercent(),
                "Layla C3 raises Skill cast multiplier");
        advanceTo(c3Simulator, 450.0 * FRAME);
        assertClose(0.294400,
                startingWith(c3Records, "Shooting Star")
                        .get(0).action.getDamagePercent(),
                "Layla C3 raises Shooting Star multiplier");

        Layla c6 = layla(6, 0.5);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        advanceTo(c6Simulator, 378.0 * FRAME);
        ActionRecord firstC6Star = startingWith(
                c6Records, "Shooting Star").get(0);
        assertClose(378.0 * FRAME, firstC6Star.time,
                "Layla C6 shortens Night Star cadence by twenty percent");
        assertClose(0.40,
                firstC6Star.action.getExtraBonuses()
                        .get(StatType.DMG_BONUS_ALL),
                "Layla C6 adds Shooting Star damage bonus");

        Layla invalidRandom = layla(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> advanceTo(invalidSimulator, 450.0 * FRAME),
                "Layla rejects out-of-range particle random draw");
    }

    private static void testPartySkillNightStarTrigger() {
        Layla layla = layla(0, 0.5);
        Bennett bennett = new Bennett(null, null);
        CombatSimulator simulator = simulatorWith(layla, bennett);
        perform(simulator, CharacterActionKey.SKILL);
        simulator.performAction(
                CharacterId.BENNETT,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(2, layla.getNightStarCount(
                simulator.getCurrentTime()),
                "Accepted party Skill adds two Night Stars");
    }

    private static void testBurstCadenceAndEnergy() {
        Layla c0 = layla(0, 0.5);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        c0.restoreCurrentEnergy(40.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Layla Burst spends Energy at frame six");
        assertClose(12.0, c0.getBurstCooldownEndTime(),
                "Layla Burst starts twelve-second cooldown on cast");
        advanceTo(simulator, 744.0 * FRAME);
        List<ActionRecord> slugs = named(records, "Starlight Slug");
        assertEquals(8, slugs.size(),
                "Layla Burst emits eight fixed-target slugs");
        assertClose(114.0 * FRAME, slugs.get(0).time,
                "Layla first Starlight Slug timing");
        assertClose(90.0 * FRAME,
                slugs.get(1).time - slugs.get(0).time,
                "Layla Starlight Slug interval");
        assertClose(0.079030,
                slugs.get(0).action.getDamagePercent(),
                "Layla C0 Starlight Slug multiplier");

        Layla c6 = layla(6, 0.5);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        c6.restoreCurrentEnergy(40.0);
        perform(c6Simulator, CharacterActionKey.BURST);
        advanceTo(c6Simulator, 114.0 * FRAME);
        AttackAction c6Slug = named(c6Records, "Starlight Slug")
                .get(0).action;
        assertClose(0.092976, c6Slug.getDamagePercent(),
                "Layla C5 raises Burst talent multiplier");
        assertClose(0.40,
                c6Slug.getExtraBonuses().get(StatType.DMG_BONUS_ALL),
                "Layla C6 adds Starlight Slug damage bonus");
    }

    private static void testSnapshotRestoreAndWrongStateBehavior() {
        Layla layla = layla(2, 0.5);
        CombatSimulator simulator = simulatorWith(layla);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        double snapshotCooldown = layla.getSkillCDRemaining(
                simulator.getCurrentTime());
        advanceTo(simulator, 525.0 * FRAME);
        List<ActionRecord> expectedStars = startingWith(
                records, "Shooting Star");
        assertEquals(4, expectedStars.size(),
                "Layla original branch resolves one volley");
        double expectedDamage = simulator.getTotalDamage();

        simulator.restoreSnapshot(snapshot);
        records.clear();
        assertClose(snapshotCooldown, layla.getSkillCDRemaining(
                simulator.getCurrentTime()),
                "Layla rollback restores Skill cooldown");
        advanceTo(simulator, 525.0 * FRAME);
        assertEquals(4, startingWith(records, "Shooting Star").size(),
                "Layla restored branch resolves one volley");
        assertClose(expectedDamage, simulator.getTotalDamage(),
                "Layla restored branch preserves total damage");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 525.0 * FRAME);
        assertEquals(4, startingWith(records, "Shooting Star").size(),
                "Layla repeated restore keeps one future volley");

        Layla foreign = layla(0, 0.5);
        assertTrue(!layla.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Layla rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> layla.restoreCharacterState(null, simulator),
                "Layla rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(layla),
                "Layla rejects cross-simulator reuse");
    }

    private static Layla layla(int constellation, double particleDraw) {
        return new Layla(
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
                CharacterId.LAYLA, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.LAYLA) {
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

    private static List<ActionRecord> startingWith(
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
            assertTrue(lines.get(index).startsWith("Layla,"),
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
