package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.element.ICDManager;
import model.character.Wanderer;
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

/** Focused regression checks for Wanderer's fixed-target Windfavored slice. */
public final class WandererRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private WandererRegressionTest() {
    }

    /** Runs data, action, form, constellation, ICD, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicActionsAndReleaseSnapshots();
        testWindfavoredParticlesAndTermination();
        testBurstAndTalentConstellations();
        testC6AndDedicatedIcd();
        testRollbackSwitchAndIsolation();
        System.out.println("WandererRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Wanderer wanderer = new Wanderer(null, null, 6);
        assertEquals(CharacterId.WANDERER, wanderer.getCharacterId(),
                "Wanderer typed identity");
        assertEquals(CharacterId.WANDERER,
                CharacterId.fromName("Wanderer"),
                "Wanderer name lookup");
        assertEquals(CharacterId.WANDERER,
                CharacterId.fromNumericId(76),
                "Wanderer numeric lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.WANDERER.getRegion(),
                "Wanderer region");
        assertEquals(Element.ANEMO, wanderer.getElement(),
                "Wanderer element");
        assertClose(10164.0,
                wanderer.getBaseStats().get(StatType.BASE_HP),
                "Wanderer base HP");
        assertClose(328.0,
                wanderer.getBaseStats().get(StatType.BASE_ATK),
                "Wanderer base ATK");
        assertClose(607.0,
                wanderer.getBaseStats().get(StatType.BASE_DEF),
                "Wanderer base DEF");
        assertClose(0.242,
                wanderer.getBaseStats().get(StatType.CRIT_RATE),
                "Wanderer base plus ascension CRIT Rate");
        assertClose(60.0, wanderer.getEnergyCost(),
                "Wanderer Energy cost");
        assertClose(15.0, wanderer.getBurstCD(),
                "Wanderer Burst cooldown");
        assertTrue(!wanderer.isA1AbsorptionRepresented(),
                "Wanderer A1 absorption is explicitly excluded");
        assertTrue(!wanderer.isA4Represented(),
                "Wanderer A4 is explicitly excluded");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.WANDERER,
                    new Wanderer(null, null, constellation)
                            .getCharacterId(),
                    "Wanderer explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Wanderer/Wanderer_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Wanderer/Wanderer_Multipliers.csv"), 28);
        assertCsvValue("N3-2", 0.875320);
        assertCsvValue("Windfavored Normal Modifier C5", 1.588550);
        assertCsvValue("Burst Hit C3", 2.944000);
        assertCsvValue("C6 Restore Cooldown", 0.2);
    }

    private static void testBasicActionsAndReleaseSnapshots() {
        Wanderer wanderer = new Wanderer(null, null, 0);
        CombatSimulator simulator = simulatorWith(wanderer);
        List<ActionRecord> records = captureActions(simulator);
        wanderer.addBuff(new SimpleBuff(
                "Wanderer release snapshot probe",
                BuffId.CUSTOM,
                0.2,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));

        double n1Cast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord n1 = named(records, "Yuuban Meigen N1").get(0);
        assertClose(n1Cast + 16.0 * FRAME, n1.time,
                "Wanderer N1 release plus travel hitmark");
        assertClose(1.262420, n1.action.getDamagePercent(),
                "Wanderer N1 multiplier");
        assertClose(1.0,
                n1.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                "N1 retains its release-stage expiring buff");
        assertClose(n1Cast + 35.0 * FRAME,
                simulator.getCurrentTime(),
                "Wanderer N1 full recovery");

        double n2Cast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord n2 = named(records, "Yuuban Meigen N2").get(0);
        assertClose(n2Cast + 11.0 * FRAME, n2.time,
                "Wanderer N2 release plus travel hitmark");
        assertClose(1.194480, n2.action.getDamagePercent(),
                "Wanderer N2 multiplier");

        double n3Cast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> n3 = namedPrefix(records, "Yuuban Meigen N3");
        assertEquals(2, n3.size(), "Wanderer N3 has two hits");
        assertClose(n3Cast + 37.0 * FRAME, n3.get(0).time,
                "Wanderer N3 first hitmark");
        assertClose(n3Cast + 46.0 * FRAME, n3.get(1).time,
                "Wanderer N3 second hitmark");

        records.clear();
        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Yuuban Meigen Charged").get(0);
        assertClose(chargedCast + 34.0 * FRAME, charged.time,
                "Wanderer Charged hitmark");
        assertClose(2.245360, charged.action.getDamagePercent(),
                "Wanderer Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Wanderer Charged has no application ICD");

        records.clear();
        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Yuuban Meigen High Plunge").get(0);
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Wanderer High Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Wanderer High Plunge category");
        assertClose(plungeCast + 1.0, simulator.getCurrentTime(),
                "Wanderer repository catalyst plunge duration");
    }

    private static void testWindfavoredParticlesAndTermination() {
        Wanderer wanderer = new Wanderer(null, null, 0);
        CombatSimulator simulator = simulatorWith(wanderer);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        CharacterActionRequest plunge = CharacterActionRequest.of(
                CharacterActionKey.PLUNGE);
        assertTrue(wanderer.canPerformAction(
                plunge, simulator.getCurrentTime()),
                "High Plunge is legal before Windfavored");
        performSkill(simulator);
        assertTrue(wanderer.isWindfavoredActive(
                simulator.getCurrentTime()),
                "Skill enters Windfavored immediately");
        assertEquals(96, wanderer.getSkydwellerPoints(),
                "Four depletion ticks occur during Skill recovery");
        assertTrue(!wanderer.canPerformAction(
                plunge, simulator.getCurrentTime()),
                "High Plunge mask closes during Windfavored");
        ActionRecord skill = named(records,
                "Hanega: Song of the Wind").get(0);
        assertClose(2.0 * FRAME, skill.time,
                "Wanderer Skill initial hitmark");
        assertClose(1.618400, skill.action.getDamagePercent(),
                "C0 Skill multiplier");

        records.clear();
        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = named(records,
                "Yuuban Meigen N1 (Windfavored)").get(0);
        assertClose(normalCast + 20.0 * FRAME, normal.time,
                "Windfavored N1 release plus travel");
        assertClose(1.262420 * 1.511525,
                normal.action.getDamagePercent(),
                "Windfavored N1 multiplicative talent scaling");

        double firstParticleHit = normal.time;
        double exactGateCast = firstParticleHit + 2.0
                - 36.0 * FRAME;
        advanceTo(simulator, exactGateCast);
        perform(simulator, CharacterActionKey.CHARGE);
        simulator.advanceTime(100.0 * FRAME + EPSILON);
        assertEquals(2, particles.size(),
                "Particle gate accepts its exact two-second boundary");
        assertClose(firstParticleHit + 100.0 * FRAME,
                particles.get(0).time,
                "First Wanderer particle travel timing");
        assertClose(firstParticleHit + 2.0 + 100.0 * FRAME,
                particles.get(1).time,
                "Boundary Wanderer particle travel timing");

        Wanderer natural = new Wanderer(null, null, 0);
        CombatSimulator naturalSimulator = simulatorWith(natural);
        performSkill(naturalSimulator);
        advanceTo(naturalSimulator, 10.0 - 1e-6);
        assertTrue(natural.isWindfavoredActive(
                naturalSimulator.getCurrentTime()),
                "Windfavored remains active immediately before ten seconds");
        assertEquals(1, natural.getSkydwellerPoints(),
                "One point remains immediately before natural expiry");
        advanceTo(naturalSimulator, 10.0);
        assertTrue(!natural.isWindfavoredActive(
                naturalSimulator.getCurrentTime()),
                "The hundredth tick ends Windfavored at exactly ten seconds");
        assertClose(6.0,
                natural.getSkillCDRemaining(
                        naturalSimulator.getCurrentTime()),
                "Natural termination starts the exact six-second cooldown");

        Wanderer manual = new Wanderer(null, null, 0);
        CombatSimulator manualSimulator = simulatorWith(manual);
        performSkill(manualSimulator);
        double manualExit = manualSimulator.getCurrentTime();
        performSkill(manualSimulator);
        assertTrue(!manual.isWindfavoredActive(
                manualSimulator.getCurrentTime()),
                "Second Skill manually ends Windfavored");
        assertClose(6.0 - 26.0 * FRAME,
                manual.getSkillCDRemaining(
                        manualSimulator.getCurrentTime()),
                "Manual exit cooldown starts before fall recovery");
        assertClose(manualExit + 26.0 * FRAME,
                manualSimulator.getCurrentTime(),
                "Manual exit recovery timing");

        Wanderer c1 = new Wanderer(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        performSkill(c1Simulator);
        double c1Start = c1Simulator.getCurrentTime();
        perform(c1Simulator, CharacterActionKey.NORMAL);
        assertClose(c1Start + 43.0 * FRAME / 1.1,
                c1Simulator.getCurrentTime(),
                "C1 applies ten-percent Windfavored attack speed");
    }

    private static void testBurstAndTalentConstellations() {
        Wanderer c0 = new Wanderer(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Wanderer Burst spends sixty Energy");
        assertEquals(2, namedPrefix(c0Records,
                "Kyougen: Five Ceremonial Plays").size(),
                "Two Burst hits land before normal recovery ends");
        c0Simulator.advanceTime(15.0 * FRAME);
        List<ActionRecord> c0Burst = namedPrefix(c0Records,
                "Kyougen: Five Ceremonial Plays");
        assertEquals(5, c0Burst.size(),
                "Wanderer Burst resolves five hits");
        for (int index = 0; index < c0Burst.size(); index++) {
            assertClose(2.502400,
                    c0Burst.get(index).action.getDamagePercent(),
                    "C0 Burst hit multiplier");
            if (index > 0) {
                assertClose(6.0 * FRAME,
                        c0Burst.get(index).time
                                - c0Burst.get(index - 1).time,
                        "Wanderer Burst hit spacing");
            }
        }

        Wanderer c3 = new Wanderer(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        assertClose(2.944000,
                namedPrefix(c3Records,
                        "Kyougen: Five Ceremonial Plays")
                        .get(0).action.getDamagePercent(),
                "C3 uses Burst talent level twelve");

        Wanderer c5 = new Wanderer(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        performSkill(c5Simulator);
        assertClose(1.904000,
                named(c5Records, "Hanega: Song of the Wind")
                        .get(0).action.getDamagePercent(),
                "C5 uses Skill talent level twelve");

        Wanderer c2 = new Wanderer(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        performSkill(c2Simulator);
        advanceTo(c2Simulator, 2.5);
        assertEquals(75, c2.getSkydwellerPoints(),
                "Twenty-five points are spent by 2.5 seconds");
        perform(c2Simulator, CharacterActionKey.BURST);
        ActionRecord c2Burst = namedPrefix(c2Records,
                "Kyougen: Five Ceremonial Plays").get(0);
        assertClose(1.0,
                c2Burst.action.getStatSnapshot().get(
                        StatType.BURST_DMG_BONUS),
                "C2 snapshots four percent per spent point");
        assertTrue(!c2.isWindfavoredActive(
                c2Simulator.getCurrentTime()),
                "Burst terminates Windfavored");
        assertClose(6.0,
                c2.getSkillCDRemaining(c2Simulator.getCurrentTime()),
                "Burst termination starts cooldown after form Burst recovery");
    }

    private static void testC6AndDedicatedIcd() {
        Wanderer c6 = new Wanderer(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        advanceTo(simulator, 6.2);
        assertTrue(c6.getSkydwellerPoints() < 40,
                "C6 fixture reaches the below-forty branch");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord original = named(records,
                "Yuuban Meigen N1 (Windfavored)").get(0);
        ActionRecord extra = named(records,
                "Shugen: The Curtains' Melancholic Sway").get(0);
        assertClose(original.time + 8.0 * FRAME, extra.time,
                "C6 extra Normal delay");
        assertClose(original.action.getDamagePercent() * 0.40,
                extra.action.getDamagePercent(),
                "C6 extra Normal multiplier");
        assertEquals(ActionType.NORMAL, extra.action.getActionType(),
                "C6 follow-up remains Normal Attack damage");
        assertEquals(ICDTag.Wanderer_C6, extra.action.getICDTag(),
                "C6 follow-up uses its dedicated ICD tag");
        assertEquals(1, c6.getC6RestoreCount(),
                "First eligible C6 hit restores points");

        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(3, c6.getC6RestoreCount(),
                "N3's second hit is blocked by the 0.2-second restore gate");
        assertEquals(4, named(records,
                "Shugen: The Curtains' Melancholic Sway").size(),
                "C6 still creates one extra hit per Windfavored Normal hit");
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(5, c6.getC6RestoreCount(),
                "C6 restoration is capped at five activations");

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "WANDERER",
                ICDTag.Wanderer_C6,
                ICDType.WandererC6,
                0.0),
                "First C6 elemental application is accepted");
        assertTrue(!manager.checkApplication(
                "WANDERER",
                ICDTag.Wanderer_C6,
                ICDType.WandererC6,
                2.0 - 1e-6),
                "C6 application is blocked immediately before two seconds");
        assertTrue(manager.checkApplication(
                "WANDERER",
                ICDTag.Wanderer_C6,
                ICDType.WandererC6,
                2.0),
                "C6 application accepts the exact two-second boundary");
    }

    private static void testRollbackSwitchAndIsolation() {
        Wanderer rollback = new Wanderer(null, null, 0);
        CombatSimulator rollbackSimulator = simulatorWith(rollback);
        List<ActionRecord> rollbackRecords = captureActions(
                rollbackSimulator);
        perform(rollbackSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = rollbackSimulator.saveSnapshot();
        int resolvedBeforeSnapshot = namedPrefix(rollbackRecords,
                "Kyougen: Five Ceremonial Plays").size();
        assertEquals(2, resolvedBeforeSnapshot,
                "Rollback fixture captures three pending Burst hits");
        rollbackSimulator.restoreSnapshot(snapshot);
        rollbackSimulator.restoreSnapshot(snapshot);
        rollbackRecords.clear();
        rollbackSimulator.advanceTime(15.0 * FRAME);
        assertEquals(3, namedPrefix(rollbackRecords,
                "Kyougen: Five Ceremonial Plays").size(),
                "Repeated restore replays each pending Burst hit once");

        Wanderer depletion = new Wanderer(null, null, 0);
        CombatSimulator depletionSimulator = simulatorWith(depletion);
        performSkill(depletionSimulator);
        SimulatorSnapshot depletionSnapshot =
                depletionSimulator.saveSnapshot();
        advanceTo(depletionSimulator, 3.0);
        int originalPoints = depletion.getSkydwellerPoints();
        depletionSimulator.restoreSnapshot(depletionSnapshot);
        depletionSimulator.restoreSnapshot(depletionSnapshot);
        advanceTo(depletionSimulator, 3.0);
        assertEquals(originalPoints, depletion.getSkydwellerPoints(),
                "Repeated restore reconstructs point depletion once");

        Wanderer switching = new Wanderer(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSimulator = simulatorWith(switching, ally);
        performSkill(switchSimulator);
        switchSimulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switching.isWindfavoredActive(
                switchSimulator.getCurrentTime()),
                "Switch-out terminates Windfavored");
        assertClose(5.9,
                switching.getSkillCDRemaining(
                        switchSimulator.getCurrentTime()),
                "Switch recovery consumes 0.1 seconds of the form cooldown");

        assertThrows(IllegalArgumentException.class,
                () -> new Wanderer(null, null, -1),
                "Wanderer rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Wanderer(null, null, 7),
                "Wanderer rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> switching.onAction(null, switchSimulator),
                "Wanderer rejects null actions");
        assertThrows(IllegalArgumentException.class,
                () -> switching.onAction(
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD),
                        switchSimulator),
                "Wanderer rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> switching.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.DASH),
                        switchSimulator),
                "Wanderer rejects excluded dash actions");

        Wanderer reused = new Wanderer(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Wanderer rejects cross-simulator reuse");
        Wanderer foreign = new Wanderer(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!switching.acceptsCharacterState(foreignState),
                "Wanderer rejects another instance's snapshot payload");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
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
                CharacterId.WANDERER,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.WANDERER,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.WANDERER) {
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
            assertTrue(lines.get(index).startsWith("Wanderer,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
            "config/characters/Wanderer/Wanderer_Status.csv",
            "config/characters/Wanderer/Wanderer_Multipliers.csv"
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
        throw new AssertionError("Wanderer CSVs missing key " + key);
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
