package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.element.ICDManager;
import model.character.Chasca;
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

/** Focused regression checks for Chasca's fixed-target Shadowhunt slice. */
public final class ChascaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ChascaRegressionTest() {
    }

    /** Runs data, timing, Nightsoul, shell, Burst, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBowBasicsAndTimings();
        testNightsoulDrainNormalAndCooldown();
        testDeterministicVolleyParticlesAndConstellations();
        testBurstA4AndIcd();
        testSnapshotAndFailClosedGuards();
        System.out.println("ChascaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Chasca chasca = new Chasca(null, null, 6);
        assertEquals(CharacterId.CHASCA, chasca.getCharacterId(),
                "Chasca typed identity");
        assertEquals(CharacterId.CHASCA, CharacterId.fromName("Chasca"),
                "Chasca name lookup");
        assertEquals(CharacterId.CHASCA, CharacterId.fromNumericId(101),
                "Chasca numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.CHASCA.getRegion(), "Chasca region");
        assertEquals(Element.ANEMO, chasca.getElement(),
                "Chasca element");
        assertClose(9797.0,
                chasca.getBaseStats().get(StatType.BASE_HP),
                "Chasca base HP");
        assertClose(347.0,
                chasca.getBaseStats().get(StatType.BASE_ATK),
                "Chasca base ATK");
        assertClose(615.0,
                chasca.getBaseStats().get(StatType.BASE_DEF),
                "Chasca base DEF");
        assertClose(0.242,
                chasca.getBaseStats().get(StatType.CRIT_RATE),
                "Chasca total base CRIT Rate");
        assertClose(60.0, chasca.getEnergyCost(),
                "Chasca Energy cost");
        assertClose(6.5, chasca.getSkillCD(),
                "Chasca exit-based Skill cooldown");
        assertClose(15.0, chasca.getBurstCD(),
                "Chasca Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.CHASCA,
                    new Chasca(null, null, constellation)
                            .getCharacterId(),
                    "Chasca explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Chasca/Chasca_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Chasca/Chasca_Multipliers.csv"), 74);
        assertCsvValue("Shining Shadowhunt Shell C3", 3.33144);
        assertCsvValue("Radiant Soulseeker Shell C5", 4.136);
    }

    private static void testBowBasicsAndTimings() {
        Chasca chasca = new Chasca(null, null, 0);
        CombatSimulator simulator = simulatorWith(chasca);
        List<ActionRecord> records = captureChascaActions(simulator);
        double[] multipliers = { 0.882003, 0.819183, 0.545606, 0.467885 };
        int[] hitCounts = { 1, 1, 2, 1 };
        int[] durations = { 32, 29, 53, 62 };
        int cursor = 0;
        double elapsed = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < hitCounts[step]; hit++) {
                ActionRecord record = records.get(cursor++);
                assertClose(multipliers[step],
                        record.action.getDamagePercent(),
                        "Chasca N" + (step + 1) + " multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Chasca bow Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Chasca bow Normal action type");
            }
            elapsed += durations[step] * FRAME;
            assertClose(elapsed, simulator.getCurrentTime(),
                    "Chasca N" + (step + 1) + " duration");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = records.get(cursor++);
        assertClose(chargedCast + 86.0 * FRAME, charged.time,
                "Chasca fully Charged impact");
        assertClose(2.108, charged.action.getDamagePercent(),
                "Chasca fully Charged multiplier");
        assertEquals(Element.ANEMO, charged.action.getElement(),
                "Chasca fully Charged element");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Chasca fully Charged action type");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(cursor);
        assertClose(plungeCast + 45.0 * FRAME, plunge.time,
                "Chasca high Plunge impact");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Chasca high Plunge multiplier");
        assertEquals(Element.PHYSICAL, plunge.action.getElement(),
                "Chasca high Plunge element");
    }

    private static void testNightsoulDrainNormalAndCooldown() {
        Chasca chasca = new Chasca(null, null, 0);
        CombatSimulator simulator = simulatorWith(chasca);
        List<ActionRecord> records = captureChascaActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(chasca.isNightsoulActive(),
                "Chasca enters Nightsoul immediately");
        assertClose(76.8, chasca.getNightsoulPoints(),
                "Chasca drains four ticks during Skill animation");
        assertEquals(1, named(records,
                "Spirit Reins, Shadow Hunt").size(),
                "Chasca Skill initial hit count");
        assertClose(0.0,
                chasca.getSkillCDRemaining(simulator.getCurrentTime()),
                "Chasca Skill cooldown waits for Nightsoul exit");

        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord tap = named(records, "Multitarget Fire").get(0);
        assertClose(normalCast + 11.0 * FRAME, tap.time,
                "Chasca Nightsoul Normal impact");
        assertClose(0.612, tap.action.getDamagePercent(),
                "Chasca Nightsoul Normal multiplier");
        assertEquals(ICDType.ChascaAlternating,
                tap.action.getICDType(),
                "Chasca Nightsoul Normal ICD type");
        assertEquals(ICDTag.Chasca_Tap,
                tap.action.getICDTag(),
                "Chasca Nightsoul Normal ICD tag");

        double exitTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(!chasca.isNightsoulActive(),
                "Chasca second Skill exits Nightsoul");
        assertClose(0.0, chasca.getNightsoulPoints(),
                "Chasca exit clears Nightsoul points");
        assertClose(6.5 - 40.0 * FRAME,
                chasca.getSkillCDRemaining(simulator.getCurrentTime()),
                "Chasca exit starts Skill cooldown before cancel recovery");
        assertTrue(exitTime < simulator.getCurrentTime(),
                "Chasca cancel advances recovery frames");
    }

    private static void testDeterministicVolleyParticlesAndConstellations() {
        Chasca c2 = new Chasca(null, null, 2);
        CombatSimulator simulator = simulatorWith(
                c2,
                new TestCharacter(CharacterId.XIANGLING, Element.PYRO),
                new TestCharacter(CharacterId.FURINA, Element.HYDRO),
                new TestCharacter(CharacterId.GANYU, Element.CRYO));
        List<ActionRecord> records = captureChascaActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        double volleyCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> shining = exactNamed(records,
                "Shining Shadowhunt Shell");
        List<ActionRecord> shadow = named(records, "Shadowhunt Shell");
        assertEquals(5, shining.size(),
                "Chasca three-PHEC C1 volley converted shell count");
        assertEquals(1, shadow.size(),
                "Chasca three-PHEC volley Anemo shell count");
        assertEquals(1, named(records,
                "Shining Shadowhunt Shell C2").size(),
                "Chasca C2 fixed-target extra occurs once per volley");
        assertClose(volleyCast + (11.0 + 108.0 + 4.0) * FRAME,
                shining.get(0).time,
                "Chasca first reverse-order shell impact");
        assertClose(0.65,
                bonus(shining.get(0).action, StatType.DMG_BONUS_ALL),
                "Chasca C2 A1 reaches capped converted-shell bonus");
        simulator.advanceTime(2.0);
        assertEquals(1, particles.size(),
                "Chasca volley generates one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Chasca volley particle count");

        Chasca c6 = new Chasca(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(
                c6,
                new TestCharacter(CharacterId.XIANGLING, Element.PYRO),
                new TestCharacter(CharacterId.FURINA, Element.HYDRO),
                new TestCharacter(CharacterId.GANYU, Element.CRYO));
        List<ActionRecord> c6Records = captureChascaActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertTrue(c6.isC6VolleyReady(c6Simulator.getCurrentTime()),
                "Chasca converted volley primes C6");
        double secondCast = c6Simulator.getCurrentTime();
        perform(c6Simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> c6Shining = exactNamed(c6Records,
                "Shining Shadowhunt Shell");
        ActionRecord secondVolley = c6Shining.get(5);
        assertClose(secondCast + 8.0 * FRAME, secondVolley.time,
                "Chasca C6 second volley charges in four frames");
        assertClose(1.7,
                secondVolley.action.getStatSnapshot()
                        .get(StatType.CRIT_DMG),
                "Chasca C6 volley adds 120% CRIT DMG");
    }

    private static void testBurstA4AndIcd() {
        Chasca chasca = new Chasca(null, null, 4);
        CombatSimulator simulator = simulatorWith(
                chasca,
                new TestCharacter(CharacterId.XIANGLING, Element.PYRO),
                new TestCharacter(CharacterId.FURINA, Element.HYDRO));
        List<ActionRecord> records = captureChascaActions(simulator);
        chasca.receiveFlatEnergy(60.0);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, burstCast + 161.0 * FRAME);
        assertEquals(1, named(records,
                "Galesplitting Soulseeker Shell").size(),
                "Chasca Burst initial shell count");
        assertEquals(4, exactNamed(records,
                "Radiant Soulseeker Shell").size(),
                "Chasca deterministic Burst radiant count");
        assertEquals(2, named(records,
                "Soulseeker Shell").size(),
                "Chasca deterministic Burst Anemo count");
        assertEquals(1, named(records,
                "Radiant Soulseeker Shell C4").size(),
                "Chasca C4 fixed-target extra occurs once");
        assertClose(6.0, chasca.getCurrentEnergy(),
                "Chasca C4 restores 1.5 Energy per radiant shell");

        assertTrue(chasca.notifyExternallyConfirmedNightsoulBurst(simulator),
                "Chasca explicit A4 ingress queues one shell");
        simulator.advanceTime(1.0);
        assertEquals(1, named(records,
                "Burning Shining Shadowhunt Shell").size(),
                "Chasca explicit A4 shell uses stable first PHEC");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                "Chasca", ICDTag.Chasca_Shining,
                ICDType.ChascaAlternating, 0.0),
                "Chasca alternating first hit applies");
        assertTrue(!icd.checkApplication(
                "Chasca", ICDTag.Chasca_Shining,
                ICDType.ChascaAlternating, 0.1),
                "Chasca alternating second hit is suppressed");
        assertTrue(icd.checkApplication(
                "Chasca", ICDTag.Chasca_Shining,
                ICDType.ChascaAlternating, 0.2),
                "Chasca alternating third hit applies");
        assertTrue(icd.checkApplication(
                "Chasca", ICDTag.Chasca_Burst,
                ICDType.ChascaAlternating, 0.2),
                "Chasca Burst ICD group remains independent");
    }

    private static void testSnapshotAndFailClosedGuards() {
        Chasca chasca = new Chasca(null, null, 0);
        CombatSimulator simulator = simulatorWith(chasca);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        double target = simulator.getCurrentTime() + 12.0 * FRAME;
        advanceTo(simulator, target);
        double expected = chasca.getNightsoulPoints();
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, target);
        assertClose(expected, chasca.getNightsoulPoints(),
                "Chasca repeated restore reconstructs one drain stream");

        assertTrue(!chasca.isNightsoulBurstTeamPlumbingRepresented(),
                "Chasca team Nightsoul plumbing fails closed");
        assertTrue(!chasca.isRandomPhecSelectionRepresented(),
                "Chasca random PHEC selection fails closed");
        assertTrue(!chasca.isMovementAndFlightRepresented(),
                "Chasca movement and flight fail closed");
        assertTrue(!chasca.isAimGeometryRepresented(),
                "Chasca aim geometry fails closed");
        assertTrue(!chasca.isMultiTargetDistributionRepresented(),
                "Chasca multi-target distribution fails closed");
        assertTrue(!chasca.isHitlagAndStaminaRepresented(),
                "Chasca hitlag and stamina fail closed");

        assertThrows(IllegalArgumentException.class,
                () -> new Chasca(null, null, -1),
                "Chasca rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Chasca(null, null, 7),
                "Chasca rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> chasca.onAction(null, simulator),
                "Chasca rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.CHASCA,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Chasca rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Chasca rejects unsupported Dash");

        Chasca foreign = new Chasca(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!chasca.acceptsCharacterState(foreignState),
                "Chasca rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> chasca.restoreCharacterState(null, simulator),
                "Chasca rejects null snapshot payload");
        Chasca external = new Chasca(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Chasca rejects binding outside simulator party");
        Chasca reused = new Chasca(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Chasca rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
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
                CharacterId.CHASCA,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureChascaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CHASCA) {
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
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static List<ActionRecord> exactNamed(
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

    private static double bonus(
            AttackAction action,
            StatType statType) {
        Double value = action.getExtraBonuses().get(statType);
        return value == null ? 0.0 : value;
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
            assertTrue(lines.get(index).startsWith("Chasca,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Chasca/Chasca_Status.csv",
                "config/characters/Chasca/Chasca_Multipliers.csv"
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
        throw new AssertionError("Chasca CSVs missing key " + key);
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

    private static final class ParticleRecord {
        private final double count;
        private final double time;

        private ParticleRecord(double count, double time) {
            this.count = count;
            this.time = time;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement) {
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
