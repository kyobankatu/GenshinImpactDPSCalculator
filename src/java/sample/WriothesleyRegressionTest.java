package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.character.Wriothesley;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
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

/** Focused regression checks for Wriothesley's bounded Rebuke slice. */
public final class WriothesleyRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private WriothesleyRegressionTest() {
    }

    /** Runs data, basics, Skill, Rebuke, Burst, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testNormalChargedAndPlungeBasics();
        testSkillScalingParticleGateAndSwitch();
        testC1RebukeAndUnsupportedHpBranches();
        testRepresentedTalentAndC6Upgrades();
        testBurstAndOusia();
        testSnapshotRestoreAndIsolationGuards();
        System.out.println("WriothesleyRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Wriothesley wriothesley = new Wriothesley(null, null, 0);
        assertEquals(CharacterId.WRIOTHESLEY,
                wriothesley.getCharacterId(),
                "Wriothesley typed identity");
        assertEquals(CharacterId.WRIOTHESLEY,
                CharacterId.fromName("Wriothesley"),
                "Wriothesley display-name identity");
        assertEquals(CharacterId.WRIOTHESLEY,
                CharacterId.fromNumericId(78),
                "Wriothesley numeric identity");
        assertEquals(78, CharacterId.WRIOTHESLEY.getNumericId(),
                "Wriothesley stable numeric id");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.WRIOTHESLEY.getRegion(),
                "Wriothesley region");
        assertEquals(Element.CRYO, wriothesley.getElement(),
                "Wriothesley element");
        assertClose(13593.0,
                wriothesley.getBaseStats().get(StatType.BASE_HP),
                "Wriothesley base HP");
        assertClose(311.0,
                wriothesley.getBaseStats().get(StatType.BASE_ATK),
                "Wriothesley base ATK");
        assertClose(763.0,
                wriothesley.getBaseStats().get(StatType.BASE_DEF),
                "Wriothesley base DEF");
        assertClose(0.884,
                wriothesley.getBaseStats().get(StatType.CRIT_DMG),
                "Wriothesley base and ascension CRIT DMG");
        assertClose(60.0, wriothesley.getEnergyCost(),
                "Wriothesley Energy cost");
        assertClose(16.0, wriothesley.getSkillCD(),
                "Wriothesley Skill cooldown");
        assertClose(15.0, wriothesley.getBurstCD(),
                "Wriothesley Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Wriothesley(null, null, constellation)
                            .getConstellation(),
                    "Wriothesley C" + constellation + " construction");
        }
        assertEquals(6,
                new Wriothesley(null, null).getConstellation(),
                "Wriothesley repository default is C6");
        assertCsvShape(Path.of(
                "config/characters/Wriothesley/Wriothesley_Status.csv"),
                18);
        assertCsvShape(Path.of(
                "config/characters/Wriothesley/"
                        + "Wriothesley_Multipliers.csv"),
                24);
        assertCsvValue("N5", 1.667121);
        assertCsvValue("Enhanced Repelling Fist Scaling", 1.669515);
        assertCsvValue("Darkgold Wolfbite C5", 2.544000);
        assertCsvValue("C1 Rebuke DMG Bonus", 2.0);
    }

    private static void testNormalChargedAndPlungeBasics() {
        Wriothesley wriothesley = new Wriothesley(null, null, 0);
        CombatSimulator simulator = simulatorWith(wriothesley);
        List<ActionRecord> records = captureActions(simulator);
        int[][] hitFrames = {
            { 12 }, { 10 }, { 18 }, { 25, 35 }, { 39 }
        };
        int[] recoveryFrames = { 27, 25, 41, 56, 59 };
        double[][] multipliers = {
            { 0.980327 },
            { 0.951650 },
            { 1.235023 },
            { 0.696377, 0.696377 },
            { 1.667121 }
        };
        for (int step = 0; step < hitFrames.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> stepRecords = named(records,
                    "Repelling Fists N" + (step + 1));
            assertEquals(hitFrames[step].length, stepRecords.size(),
                    "Wriothesley N" + (step + 1) + " hit count");
            for (int variant = 0;
                    variant < hitFrames[step].length;
                    variant++) {
                ActionRecord record = stepRecords.get(variant);
                assertClose(castTime + hitFrames[step][variant] * FRAME,
                        record.time,
                        "Wriothesley N" + (step + 1) + " hitmark");
                assertClose(multipliers[step][variant],
                        record.action.getDamagePercent(),
                        "Wriothesley N" + (step + 1) + " multiplier");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Wriothesley Normal category");
                assertEquals(Element.CRYO, record.action.getElement(),
                        "Wriothesley catalyst Normal element");
                assertEquals(ICDType.Standard,
                        record.action.getICDType(),
                        "Wriothesley Normal ICD type");
                assertEquals(ICDTag.NormalAttack,
                        record.action.getICDTag(),
                        "Wriothesley Normal ICD tag");
            }
            assertClose(castTime + recoveryFrames[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Wriothesley N" + (step + 1) + " recovery");
        }

        records.clear();
        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = onlyNamed(records,
                "Forceful Fists Charged Attack");
        assertClose(chargedCast + 19.0 * FRAME, charged.time,
                "Wriothesley Charged hitmark");
        assertClose(2.600320, charged.action.getDamagePercent(),
                "Wriothesley Charged multiplier");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Wriothesley Charged category");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Wriothesley Charged has no ICD");

        records.clear();
        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = onlyNamed(records,
                "Forceful Fists of Frost High Plunge");
        assertClose(plungeCast + 47.0 * FRAME, plunge.time,
                "Wriothesley high Plunge hitmark");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Wriothesley high Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Wriothesley high Plunge category");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Wriothesley high Plunge has no ICD");
    }

    private static void testSkillScalingParticleGateAndSwitch() {
        Wriothesley wriothesley = new Wriothesley(null, null, 0);
        CombatSimulator simulator = simulatorWith(wriothesley);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);

        perform(simulator, CharacterActionKey.SKILL);
        assertClose(29.0 * FRAME, simulator.getCurrentTime(),
                "Icefang Rush recovery");
        assertClose(FRAME, wriothesley.getLastSkillTime(),
                "Icefang Rush cooldown starts at frame one");
        assertClose(601.0 * FRAME,
                wriothesley.getChillingPenaltyExpirationTime(),
                "Chilling Penalty fixed-full-HP duration");
        assertTrue(wriothesley.isChillingPenaltyActive(
                        601.0 * FRAME - EPSILON),
                "Chilling Penalty is active before expiry");
        assertTrue(!wriothesley.isChillingPenaltyActive(601.0 * FRAME),
                "Chilling Penalty expires at its half-open boundary");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord enhancedN1 = onlyNamed(records,
                "Repelling Fists N1 (Enhanced)");
        assertClose(41.0 * FRAME, enhancedN1.time,
                "Enhanced N1 hitmark after Skill");
        assertClose(0.980327 * 1.669515,
                enhancedN1.action.getDamagePercent(),
                "Skill multiplies N1 motion value");
        assertClose(161.0 * FRAME,
                wriothesley.getNextParticleAllowedTime(),
                "First enhanced hit starts two-second particle gate");

        perform(simulator, CharacterActionKey.NORMAL);
        advanceTo(simulator, 141.0 * FRAME);
        assertEquals(1, particles.size(),
                "First enhanced hit emits one particle packet");
        assertClose(1.0, particles.get(0).count,
                "Wriothesley particle count is fixed at one");
        assertClose(141.0 * FRAME, particles.get(0).time,
                "Wriothesley particle travel is one hundred frames");

        advanceTo(simulator, 143.0 * FRAME);
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose(281.0 * FRAME,
                wriothesley.getNextParticleAllowedTime(),
                "Particle gate reopens at its exact boundary");
        advanceTo(simulator, 261.0 * FRAME);
        assertEquals(2, particles.size(),
                "Exact-boundary enhanced hit emits a second packet");

        advanceTo(simulator, 601.0 * FRAME);
        records.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> unenhanced = named(records,
                "Repelling Fists N4");
        assertEquals(2, unenhanced.size(),
                "Expired Skill preserves both N4 hits");
        for (ActionRecord hit : unenhanced) {
            assertClose(0.696377, hit.action.getDamagePercent(),
                    "Expired Skill no longer scales Normal attacks");
        }

        Wriothesley switched = new Wriothesley(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSimulator = simulatorWith(switched, ally);
        perform(switchSimulator, CharacterActionKey.SKILL);
        switchSimulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switched.isChillingPenaltyActive(
                        switchSimulator.getCurrentTime()),
                "Switch-out ends Chilling Penalty");
    }

    private static void testC1RebukeAndUnsupportedHpBranches() {
        Wriothesley c0 = new Wriothesley(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        perform(c0Simulator, CharacterActionKey.SKILL);
        performNormalString(c0Simulator);
        assertTrue(!c0.hasGraciousRebuke(),
                "Unavailable low-HP A1 Rebuke route fails closed at C0");

        Wriothesley c1 = new Wriothesley(null, null, 1);
        CombatSimulator simulator = simulatorWith(c1);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        performNormalString(simulator);
        assertTrue(c1.hasGraciousRebuke(),
                "C1 in-Skill N5 prepares Gracious Rebuke");
        assertClose(601.0 * FRAME,
                c1.getChillingPenaltyExpirationTime(),
                "N5 preparation alone does not extend Skill");

        records.clear();
        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord rebuke = onlyNamed(records,
                "Rebuke: Vaulting Fist");
        double rebukeHit = chargedCast + 19.0 * FRAME;
        assertClose(rebukeHit, rebuke.time,
                "Rebuke hitmark");
        assertClose(2.600320, rebuke.action.getDamagePercent(),
                "Rebuke retains Charged Attack motion value");
        assertClose(2.0,
                rebuke.action.getStatSnapshot().get(
                        StatType.DMG_BONUS_ALL),
                "C1 Rebuke gains two hundred percent DMG Bonus");
        assertTrue(!c1.hasGraciousRebuke(),
                "Rebuke is consumed only after hitting the fixed target");
        assertClose(rebukeHit + 2.5,
                c1.getNextRebukeAllowedTime(),
                "C1 Rebuke cooldown starts on hit");
        assertClose(841.0 * FRAME,
                c1.getChillingPenaltyExpirationTime(),
                "C1 Rebuke extends Skill once by four seconds");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, named(records,
                "Forceful Fists Charged Attack").size(),
                "A second Charged Attack is not Rebuke without a new N5");

        Wriothesley c2 = new Wriothesley(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.SKILL);
        perform(c2Simulator, CharacterActionKey.BURST);
        ActionRecord c2Burst = named(c2Records,
                "Darkgold Wolfbite ").get(0);
        assertClose(0.0,
                c2Burst.action.getStatSnapshot().get(
                        StatType.DMG_BONUS_ALL),
                "C2 remains inert without unavailable A4 HP-change stacks");

        Wriothesley c4 = new Wriothesley(null, null, 4);
        assertClose(0.0,
                c4.getEffectiveStats(0.0)
                        .get(StatType.ATK_SPD),
                "Healing-derived C4 speed state is never invented");
    }

    private static void testRepresentedTalentAndC6Upgrades() {
        Wriothesley c3 = new Wriothesley(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.NORMAL);
        assertClose(1.203692,
                onlyNamed(c3Records, "Repelling Fists N1")
                        .action.getDamagePercent(),
                "C3 raises Normal talent to level twelve");
        c3Records.clear();
        perform(c3Simulator, CharacterActionKey.CHARGE);
        assertClose(3.059200,
                onlyNamed(c3Records, "Forceful Fists Charged Attack")
                        .action.getDamagePercent(),
                "C3 raises Charged talent to level twelve");

        Wriothesley c6 = new Wriothesley(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        performNormalString(c6Simulator);
        c6Records.clear();
        perform(c6Simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> rebukes = named(c6Records,
                "Rebuke: Vaulting Fist");
        assertEquals(2, rebukes.size(),
                "C6 Rebuke creates one same-frame icicle duplicate");
        assertClose(rebukes.get(0).time, rebukes.get(1).time,
                "C6 Rebuke duplicate shares the source hitmark");
        for (ActionRecord rebuke : rebukes) {
            StatsContainer snapshot = rebuke.action.getStatSnapshot();
            assertClose(3.059200, rebuke.action.getDamagePercent(),
                    "C6 inherits C3 Charged talent level");
            assertClose(0.15, snapshot.get(StatType.CRIT_RATE),
                    "C6 Rebuke gains ten percent CRIT Rate");
            assertClose(1.684, snapshot.get(StatType.CRIT_DMG),
                    "C6 Rebuke gains eighty percent CRIT DMG");
            assertClose(2.0, snapshot.get(StatType.DMG_BONUS_ALL),
                    "C6 Rebuke retains C1 DMG Bonus");
        }
    }

    private static void testBurstAndOusia() {
        Wriothesley c5 = new Wriothesley(null, null, 5);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(133.0 * FRAME, simulator.getCurrentTime(),
                "Darkgold Wolfbite recovery");
        assertClose(0.0, c5.getCurrentEnergy(),
                "Burst spends sixty Energy at frame five");
        assertClose(0.0, c5.getLastBurstTime(),
                "Burst cooldown starts at cast");
        List<ActionRecord> burstHits = named(records,
                "Darkgold Wolfbite ");
        assertEquals(5, burstHits.size(),
                "Darkgold Wolfbite has five main hits");
        int[] frames = { 99, 104, 109, 114, 119 };
        for (int index = 0; index < frames.length; index++) {
            ActionRecord hit = burstHits.get(index);
            assertClose(frames[index] * FRAME, hit.time,
                    "Darkgold Wolfbite hitmark " + (index + 1));
            assertClose(2.544000, hit.action.getDamagePercent(),
                    "C5 Burst multiplier " + (index + 1));
            assertEquals(ActionType.BURST, hit.action.getActionType(),
                    "Darkgold Wolfbite Burst category");
            assertEquals(ICDType.Standard, hit.action.getICDType(),
                    "Darkgold Wolfbite Standard ICD");
        }
        advanceTo(simulator, 160.0 * FRAME);
        ActionRecord ousia = onlyNamed(records,
                "Darkgold Wolfbite: Surging Blade");
        assertClose(160.0 * FRAME, ousia.time,
                "Surging Blade hitmark");
        assertClose(0.848000, ousia.action.getDamagePercent(),
                "C5 raises Surging Blade talent level");
        assertClose(0.0, ousia.action.getGaugeUnits(),
                "Surging Blade applies no Cryo gauge");
    }

    private static void testSnapshotRestoreAndIsolationGuards() {
        Wriothesley stateful = new Wriothesley(null, null, 1);
        CombatSimulator simulator = simulatorWith(stateful);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        performNormalString(simulator);
        SimulatorSnapshot rebukeSnapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.CHARGE);
        assertTrue(!stateful.hasGraciousRebuke(),
                "Original timeline consumes represented Rebuke");
        simulator.restoreSnapshot(rebukeSnapshot);
        assertTrue(stateful.hasGraciousRebuke(),
                "Snapshot restore recovers represented Rebuke state");
        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, named(records,
                "Rebuke: Vaulting Fist").size(),
                "Restored Rebuke resolves exactly once");

        Wriothesley pending = new Wriothesley(null, null, 5);
        CombatSimulator pendingSimulator = simulatorWith(pending);
        List<ActionRecord> pendingRecords = captureActions(pendingSimulator);
        perform(pendingSimulator, CharacterActionKey.BURST);
        assertEquals(1, pending.getPendingHitCount(),
                "Only delayed Ousia remains after Burst recovery");
        SimulatorSnapshot pendingSnapshot = pendingSimulator.saveSnapshot();
        advanceTo(pendingSimulator, 160.0 * FRAME);
        assertEquals(1, named(pendingRecords,
                "Darkgold Wolfbite: Surging Blade").size(),
                "Original delayed Ousia resolves once");
        pendingSimulator.restoreSnapshot(pendingSnapshot);
        advanceTo(pendingSimulator, 160.0 * FRAME);
        assertEquals(2, named(pendingRecords,
                "Darkgold Wolfbite: Surging Blade").size(),
                "Restored delayed Ousia resolves once more without duplicates");

        assertThrows(IllegalArgumentException.class,
                () -> new Wriothesley(null, null, -1),
                "Wriothesley rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Wriothesley(null, null, 7),
                "Wriothesley rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> stateful.onAction(null, simulator),
                "Wriothesley rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Unsupported movement action fails closed");

        Wriothesley external = new Wriothesley(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Wriothesley rejects a simulator that does not own it");
        Wriothesley reused = new Wriothesley(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Wriothesley rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!stateful.acceptsCharacterState(foreignState),
                "Wriothesley rejects another instance's snapshot state");
        assertThrows(IllegalArgumentException.class,
                () -> stateful.restoreCharacterState(
                        foreignState, simulator),
                "Wriothesley rejects foreign snapshot restore");
    }

    private static void performNormalString(CombatSimulator simulator) {
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
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
                CharacterId.WRIOTHESLEY,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.WRIOTHESLEY) {
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

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> selected = named(records, prefix);
        assertEquals(1, selected.size(), prefix + " action count");
        return selected.get(0);
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
            assertTrue(lines.get(index).startsWith("Wriothesley,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Wriothesley/Wriothesley_Status.csv",
                "config/characters/Wriothesley/"
                        + "Wriothesley_Multipliers.csv"
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
        throw new AssertionError(
                "Wriothesley CSVs missing key " + key);
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
        if (!expected.equals(actual)) {
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
