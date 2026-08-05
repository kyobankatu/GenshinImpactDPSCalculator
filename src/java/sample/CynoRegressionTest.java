package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Cyno;
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

/** Focused regression checks for Cyno's fixed-target Pactsworn kit. */
public final class CynoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private CynoRegressionTest() {
    }

    /** Runs data, timing, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalActionsAndSkill();
        testBurstFormNormalAndConstellations();
        testEndseerJudicationAndFormExtension();
        testC4EnergyAndReactionGuards();
        testSnapshotRestoreAndSwitch();
        testInvalidInputsAndIsolation();
        System.out.println("CynoRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Cyno cyno = new Cyno(null, null, 6);
        assertEquals(CharacterId.CYNO, cyno.getCharacterId(),
                "Cyno typed identity");
        assertEquals(CharacterId.CYNO, CharacterId.fromName("Cyno"),
                "Cyno name lookup");
        assertEquals(CharacterId.CYNO, CharacterId.fromNumericId(46),
                "Cyno numeric lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.CYNO.getRegion(),
                "Cyno region");
        assertEquals(Element.ELECTRO, cyno.getElement(), "Cyno element");
        assertClose(12491.0,
                cyno.getBaseStats().get(StatType.BASE_HP),
                "Cyno base HP");
        assertClose(318.0,
                cyno.getBaseStats().get(StatType.BASE_ATK),
                "Cyno base ATK");
        assertClose(859.0,
                cyno.getBaseStats().get(StatType.BASE_DEF),
                "Cyno base DEF");
        assertClose(0.884,
                cyno.getBaseStats().get(StatType.CRIT_DMG),
                "Cyno default plus ascension CRIT DMG");
        assertClose(80.0, cyno.getEnergyCost(), "Cyno Energy cost");
        assertClose(7.5, cyno.getSkillCD(), "Cyno Skill cooldown");
        assertClose(20.0, cyno.getBurstCD(), "Cyno Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.CYNO,
                    new Cyno(null, null, constellation).getCharacterId(),
                    "Cyno explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Cyno/Cyno_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Cyno/Cyno_Multipliers.csv"), 52);
        assertCsvValue("Pactsworn N5 C3", 2.951613);
        assertCsvValue("Mortuary Rite C5", 3.136);
        assertCsvValue("A4 Duststalker Bolt EM Ratio", 2.5);
    }

    private static void testPhysicalActionsAndSkill() {
        Cyno cyno = new Cyno(null, null, 0, () -> 0.5);
        CombatSimulator simulator = simulatorWith(cyno);
        List<ActionRecord> records = captureActions(simulator);
        double[][] multipliers = {
            { 0.904961 }, { 0.880408 },
            { 0.538417, 0.538417 }, { 1.394271 }
        };
        int[] durations = { 15, 22, 27, 58 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (double multiplier : multipliers[step]) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multiplier, record.action.getDamagePercent(),
                        "Cyno physical Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Cyno physical Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Cyno physical Normal category");
                assertEquals(ICDTag.NormalAttack,
                        record.action.getICDTag(),
                        "Cyno physical Normal ICD tag");
            }
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Cyno physical Normal recovery");
        }
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Invoker's Spear Charged Attack").get(0);
        assertClose(2.24834, charged.action.getDamagePercent(),
                "Cyno physical Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Cyno physical Charged ICD");
        performSkill(simulator);
        ActionRecord skill = named(records,
                "Secret Rite: Chasmic Soulfarer").get(0);
        assertClose(2.2168, skill.action.getDamagePercent(),
                "Cyno Skill multiplier");
        assertEquals(ActionType.SKILL, skill.action.getActionType(),
                "Cyno Skill category");
        assertClose(7.5, cyno.getSkillCD(),
                "Cyno outside-form Skill cooldown");
        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Invoker's Spear High Plunge").get(0);
        assertClose(plungeStart + 46.0 * FRAME, plunge.time,
                "Cyno physical High Plunge hitmark");
        assertEquals(Element.PHYSICAL, plunge.action.getElement(),
                "Cyno physical High Plunge element");
    }

    private static void testBurstFormNormalAndConstellations() {
        Cyno cyno = new Cyno(null, null, 6, () -> 0.5);
        CombatSimulator simulator = simulatorWith(cyno);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(cyno.isFormActive(simulator.getCurrentTime()),
                "Cyno form activates before Burst recovery ends");
        assertClose(0.0, cyno.getCurrentEnergy(),
                "Cyno Burst spends Energy");
        assertClose(100.0, effectiveStat(
                simulator, cyno, StatType.ELEMENTAL_MASTERY),
                "Cyno form grants Elemental Mastery");
        assertClose(0.20, effectiveStat(
                simulator, cyno, StatType.ATK_SPD),
                "Cyno C1 activates with form");
        assertEquals(4, cyno.getC6Stacks(simulator.getCurrentTime()),
                "Cyno C6 grants four activation stacks");

        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = named(records,
                "Pactsworn Pathclearer Pactsworn N1").get(0);
        assertClose(castTime + 10.0 * FRAME, normal.time,
                "Cyno C1 accelerates Pactsworn N1 hitmark");
        assertClose(1.765924, normal.action.getDamagePercent(),
                "Cyno C3 Pactsworn N1 multiplier");
        assertEquals(Element.ELECTRO, normal.action.getElement(),
                "Cyno Pactsworn Normal infusion");
        assertClose(150.0,
                normal.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Cyno A4 adds 1.5 times form EM to Normals");
        assertEquals(1, cyno.getC2Stacks(simulator.getCurrentTime()),
                "Cyno C2 gains one stack from accepted Normal damage");
        assertClose(0.10, effectiveStat(
                simulator, cyno, StatType.ELECTRO_DMG_BONUS),
                "Cyno C2 stack grants Electro bonus");
        assertEquals(3, cyno.getC6Stacks(simulator.getCurrentTime()),
                "Cyno C6 consumes one stack per eligible Normal");
        ActionRecord c6Bolt = named(records, "C6 Duststalker Bolt").get(0);
        assertEquals(ICDTag.Cyno_C6_DuststalkerBolt,
                c6Bolt.action.getICDTag(),
                "Cyno C6 uses a separate Bolt ICD tag");
        assertClose(250.0,
                c6Bolt.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Cyno Bolt A4 reads live EM after C2 trigger");

        Cyno c0 = new Cyno(null, null, 0, () -> 0.5);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        perform(c0Simulator, CharacterActionKey.NORMAL);
        assertClose(1.438227, named(c0Records,
                "Pactsworn Pathclearer Pactsworn N1").get(0)
                .action.getDamagePercent(),
                "Cyno C0 Pactsworn N1 multiplier");
        assertTrue(named(c0Records, "C6 Duststalker Bolt").isEmpty(),
                "Cyno C0 does not emit C6 Bolts");

        double formExpiration = c0.getFormExpirationTime();
        advanceTo(c0Simulator, formExpiration - 0.10);
        perform(c0Simulator, CharacterActionKey.NORMAL);
        ActionRecord lateNormal = named(c0Records,
                "Pactsworn Pathclearer Pactsworn N2").get(0);
        assertEquals(1, named(c0Records,
                "Pactsworn Pathclearer Pactsworn N2").size(),
                "Cyno form attack started before expiry still lands");
        assertClose(150.0, lateNormal.action.getStatSnapshot().get(
                StatType.FLAT_DMG_BONUS),
                "Cyno PP slide preserves form EM through late hit");

        Cyno c1Charge = new Cyno(null, null, 1, () -> 0.5);
        CombatSimulator chargeSimulator = simulatorWith(c1Charge);
        perform(chargeSimulator, CharacterActionKey.BURST);
        double chargeStart = chargeSimulator.getCurrentTime();
        perform(chargeSimulator, CharacterActionKey.CHARGE);
        assertClose(chargeStart + 65.0 * FRAME,
                chargeSimulator.getCurrentTime(),
                "Cyno C1 does not accelerate Charged Attack recovery");

        Cyno c3Plunge = new Cyno(null, null, 3, () -> 0.5);
        CombatSimulator plungeSimulator = simulatorWith(c3Plunge);
        List<ActionRecord> plungeRecords = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.BURST);
        double formPlungeStart = plungeSimulator.getCurrentTime();
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord formPlunge = named(plungeRecords,
                "Pactsworn Pathclearer High Plunge").get(0);
        assertClose(formPlungeStart + 48.0 * FRAME, formPlunge.time,
                "Cyno Pactsworn High Plunge hitmark");
        assertClose(3.601998, formPlunge.action.getDamagePercent(),
                "Cyno C3 Pactsworn High Plunge multiplier");
        assertEquals(Element.ELECTRO, formPlunge.action.getElement(),
                "Cyno Pactsworn High Plunge infusion");
    }

    private static void testEndseerJudicationAndFormExtension() {
        Cyno cyno = new Cyno(null, null, 6, () -> 0.2);
        CombatSimulator simulator = simulatorWith(cyno);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particleCounts = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.BURST);
        double formStart = 84.0 * FRAME;
        double originalExpiration = formStart + 10.0;
        double endseerStart = 328.0 * FRAME;
        advanceTo(simulator, endseerStart);
        assertTrue(cyno.isEndseerActive(simulator.getCurrentTime()),
                "Cyno first Endseer opens after 328 frames");
        performSkill(simulator);
        ActionRecord rite = named(records, "Mortuary Rite").get(0);
        assertClose(3.136, rite.action.getDamagePercent(),
                "Cyno C5 Mortuary Rite multiplier");
        assertClose(0.35, rite.action.getExtraBonuses().getOrDefault(
                StatType.SKILL_DMG_BONUS, 0.0),
                "Cyno Judication grants Skill damage bonus");
        assertEquals(3, named(records, "Duststalker Bolt").size(),
                "Cyno Judication emits three Duststalker Bolts");
        for (ActionRecord bolt : named(records, "Duststalker Bolt")) {
            assertEquals(ICDTag.Cyno_DuststalkerBolt,
                    bolt.action.getICDTag(),
                    "Cyno A1 Bolts share their dedicated ICD tag");
        }
        assertClose(originalExpiration + 4.0,
                cyno.getFormExpirationTime(),
                "Cyno Mortuary Rite extends form by four seconds");
        assertEquals(1, particleCounts.size(),
                "Cyno Mortuary Rite generates one particle event");
        assertClose(2.0, particleCounts.get(0),
                "Cyno injectable particle draw selects two particles");
        assertEquals(8, cyno.getC6Stacks(simulator.getCurrentTime()),
                "Cyno Judication refresh caps C6 stacks at eight");

        double secondEndseer = endseerStart + 234.0 * FRAME;
        advanceTo(simulator, secondEndseer);
        assertTrue(cyno.isEndseerActive(secondEndseer),
                "Cyno schedules the next Endseer interval");

        Cyno lateExtension = new Cyno(null, null, 6, () -> 0.5);
        CombatSimulator lateSimulator = simulatorWith(lateExtension);
        perform(lateSimulator, CharacterActionKey.BURST);
        double lateSecondEndseer = 328.0 * FRAME + 234.0 * FRAME;
        advanceTo(lateSimulator, lateSecondEndseer);
        performSkill(lateSimulator);
        double thirdEndseer = lateSecondEndseer + 234.0 * FRAME;
        advanceTo(lateSimulator, thirdEndseer);
        assertTrue(lateExtension.isEndseerActive(thirdEndseer),
                "Cyno late extension preserves the third Endseer schedule");
    }

    private static void testC4EnergyAndReactionGuards() {
        Cyno cyno = new Cyno(null, null, 4, () -> 0.5);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(cyno, ally);
        ally.restoreCurrentEnergy(0.0);
        perform(simulator, CharacterActionKey.BURST);
        ReactionResult aggravate = new ReactionResult(
                ReactionResult.Type.AMP,
                1.0,
                0.0,
                "Aggravate",
                ReactionResult.Kind.AGGRAVATE);
        for (int index = 0; index < 6; index++) {
            cyno.onReaction(
                    aggravate,
                    cyno,
                    simulator.getCurrentTime(),
                    simulator);
        }
        assertEquals(5, cyno.getC4TriggerCount(),
                "Cyno C4 caps at five reactions per form");
        assertClose(15.0, ally.getCurrentEnergy(),
                "Cyno C4 grants three team Energy per trigger");
        cyno.onReaction(
                aggravate,
                ally,
                simulator.getCurrentTime(),
                simulator);
        assertClose(15.0, ally.getCurrentEnergy(),
                "Cyno C4 rejects foreign reaction owners");

        Cyno c3 = new Cyno(null, null, 3, () -> 0.5);
        TestCharacter c3Ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator c3Simulator = simulatorWith(c3, c3Ally);
        c3Ally.restoreCurrentEnergy(0.0);
        perform(c3Simulator, CharacterActionKey.BURST);
        c3.onReaction(
                aggravate,
                c3,
                c3Simulator.getCurrentTime(),
                c3Simulator);
        assertClose(0.0, c3Ally.getCurrentEnergy(),
                "Cyno C3 has no C4 Energy effect");

        Cyno c2OutsideForm = new Cyno(null, null, 2, () -> 0.5);
        CombatSimulator c2Simulator = simulatorWith(c2OutsideForm);
        perform(c2Simulator, CharacterActionKey.NORMAL);
        assertEquals(1, c2OutsideForm.getC2Stacks(
                c2Simulator.getCurrentTime()),
                "Cyno C2 accepts Physical Normal hits outside form");
        assertClose(0.10, effectiveStat(
                c2Simulator,
                c2OutsideForm,
                StatType.ELECTRO_DMG_BONUS),
                "Cyno outside-form Normal grants one C2 Electro stack");
    }

    private static void testSnapshotRestoreAndSwitch() {
        Cyno cyno = new Cyno(null, null, 6, () -> 0.5);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(cyno, ally);
        perform(simulator, CharacterActionKey.BURST);
        double endseerTime = 328.0 * FRAME;
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, endseerTime);
        assertTrue(cyno.isEndseerActive(endseerTime),
                "Cyno pending Endseer opens before restore");
        simulator.restoreSnapshot(snapshot);
        assertTrue(!cyno.isEndseerActive(simulator.getCurrentTime()),
                "Cyno restore rewinds Endseer state");
        advanceTo(simulator, endseerTime);
        assertTrue(cyno.isEndseerActive(endseerTime),
                "Cyno restore reconstructs pending Endseer once");

        simulator.switchCharacter(CharacterId.COLLEI);
        assertTrue(!cyno.isFormActive(simulator.getCurrentTime()),
                "Cyno switch-out ends Pactsworn form");
        simulator.switchCharacter(CharacterId.CYNO);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals("Invoker's Spear N2",
                records.get(0).action.getName(),
                "Cyno physical Normal string continues after first post-switch N1");

        Cyno foreign = new Cyno(null, null, 6, () -> 0.5);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!cyno.acceptsCharacterState(foreignState),
                "Cyno rejects another instance's payload");

        int[] drawCalls = { 0 };
        double[] draws = { 0.2, 0.8 };
        Cyno taped = new Cyno(null, null, 0,
                () -> draws[drawCalls[0]++]);
        CombatSimulator tapedSimulator = simulatorWith(taped);
        List<Double> particles = captureElectroParticles(tapedSimulator);
        perform(tapedSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot beforeSkill = tapedSimulator.saveSnapshot();
        performSkill(tapedSimulator);
        tapedSimulator.restoreSnapshot(beforeSkill);
        performSkill(tapedSimulator);
        assertEquals(2, particles.size(),
                "Cyno replay resolves both particle branches");
        assertClose(2.0, particles.get(0),
                "Cyno first branch particle draw");
        assertClose(2.0, particles.get(1),
                "Cyno restored branch reuses particle draw tape");
        assertEquals(1, drawCalls[0],
                "Cyno restored branch does not consume a second draw");
    }

    private static void testInvalidInputsAndIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cyno(null, null, -1),
                "Cyno rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Cyno(null, null, 7),
                "Cyno rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Cyno(null, null, 0, null),
                "Cyno rejects null particle draw");

        Cyno invalidDraw = new Cyno(
                null, null, 0, () -> Double.NaN);
        CombatSimulator invalidSimulator = simulatorWith(invalidDraw);
        perform(invalidSimulator, CharacterActionKey.BURST);
        assertThrows(IllegalStateException.class,
                () -> performSkill(invalidSimulator),
                "Cyno validates particle draws in form Skill");

        Cyno isolated = new Cyno(null, null, 0, () -> 0.5);
        CombatSimulator first = simulatorWith(isolated);
        isolated.initializeForSimulator(first);
        CombatSimulator second = new CombatSimulator();
        assertThrows(IllegalStateException.class,
                () -> second.addCharacter(isolated),
                "Cyno rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> isolated.onAction(null, first),
                "Cyno rejects null actions");
        assertThrows(IllegalArgumentException.class,
                () -> isolated.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        first),
                "Cyno rejects Hold Skill");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.CYNO, CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.CYNO,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CYNO) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureElectroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
                records.add(count);
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
            assertTrue(lines.get(index).startsWith("Cyno,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Cyno/Cyno_Status.csv",
                "config/characters/Cyno/Cyno_Multipliers.csv"
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
        throw new AssertionError("Cyno CSVs missing key " + key);
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
