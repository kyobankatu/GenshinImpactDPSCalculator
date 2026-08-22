package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.character.Skirk;
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

/** Focused regression checks for Skirk's fixed-target Seven-Phase Flash slice. */
public final class SkirkRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private SkirkRegressionTest() {
    }

    /** Runs identity, action, resource, Burst, passive, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testSwordBasicsAndTiming();
        testSevenPhaseFlashResourceReplacementsAndParticles();
        testHavocRuinBranch();
        testRiftExtinctionAndConstellations();
        testTalentPassiveA4AndC4();
        testSnapshotAndFailClosedBoundaries();
        System.out.println("SkirkRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Skirk skirk = new Skirk(null, null, 6);
        assertEquals(CharacterId.SKIRK, skirk.getCharacterId(),
                "Skirk typed identity");
        assertEquals(CharacterId.SKIRK,
                CharacterId.fromName("Skirk"),
                "Skirk name lookup");
        assertEquals(CharacterId.SKIRK,
                CharacterId.fromNumericId(100),
                "Skirk numeric lookup");
        assertEquals(100, CharacterId.SKIRK.getNumericId(),
                "Skirk stable numeric id");
        assertEquals(CharacterRegion.UNKNOWN,
                CharacterId.SKIRK.getRegion(),
                "Skirk non-national region");
        assertEquals(Element.CRYO, skirk.getElement(),
                "Skirk element");
        assertClose(12417.0,
                skirk.getBaseStats().get(StatType.BASE_HP),
                "Skirk base HP");
        assertClose(359.0,
                skirk.getBaseStats().get(StatType.BASE_ATK),
                "Skirk base ATK");
        assertClose(806.0,
                skirk.getBaseStats().get(StatType.BASE_DEF),
                "Skirk base DEF");
        assertClose(0.884,
                skirk.getBaseStats().get(StatType.CRIT_DMG),
                "Skirk base and ascension CRIT DMG");
        assertClose(0.0, skirk.getEnergyCost(),
                "Skirk uses no conventional Energy");
        assertClose(8.0, skirk.getSkillCD(),
                "Skirk exit-based Skill cooldown");
        assertClose(15.0, skirk.getBurstCD(),
                "Skirk Burst cooldown");
        assertClose(100.0, skirk.getSerpentsSubtlety(),
                "Skirk starts at source default Subtlety");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Skirk(null, null, constellation)
                            .getConstellation(),
                    "Skirk explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Skirk/Skirk_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Skirk/Skirk_Multipliers.csv"), 72);
        assertCsvValue("Flash N1", 2.440263);
        assertCsvValue("Flash N1 C3 Passive", 3.181608);
        assertCsvValue("Ruin Point Bonus C5", 0.386467);
        assertCsvValue("C6 Burst Multiplier", 7.5);
        assertThrows(IllegalArgumentException.class,
                () -> new Skirk(null, null, -1),
                "Skirk rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Skirk(null, null, 7),
                "Skirk rejects constellation above C6");
    }

    private static void testSwordBasicsAndTiming() {
        Skirk skirk = new Skirk(null, null, 0);
        CombatSimulator simulator = simulatorWith(skirk);
        List<ActionRecord> records = captureSkirkActions(simulator);
        double[] multipliers = {
            1.001720, 0.914820, 0.595660, 1.117060, 1.523120
        };
        int[] hitFrames = { 13, 7, 8, 11, 35 };
        int[] durations = { 27, 25, 43, 23, 72 };
        int[] hitlagFrames = { 2, 2, 2, 3, 4 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> hits = prefix(
                    records, "Havoc: Sunder N" + (step + 1));
            assertEquals(step == 2 ? 2 : 1, hits.size(),
                    "Skirk N" + (step + 1) + " hit count");
            assertClose(castTime + hitFrames[step] * FRAME,
                    hits.get(0).time,
                    "Skirk N" + (step + 1) + " first hitmark");
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Skirk N" + (step + 1) + " recovery");
            assertClose(multipliers[step],
                    hits.get(0).action.getDamagePercent(),
                    "Skirk N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL,
                    hits.get(0).action.getElement(),
                    "Skirk basic Normal element");
            assertEquals(ICDTag.NormalAttack,
                    hits.get(0).action.getICDTag(),
                    "Skirk basic Normal ICD tag");
        }

        Skirk basics = new Skirk(null, null, 0);
        CombatSimulator basicsSimulator = simulatorWith(basics);
        List<ActionRecord> basicsRecords =
                captureSkirkActions(basicsSimulator);
        perform(basicsSimulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = prefix(
                basicsRecords, "Havoc: Sunder Charged Hit");
        assertEquals(2, charged.size(),
                "Skirk physical Charged has two hits");
        assertClose(27.0 * FRAME, charged.get(0).time,
                "Skirk physical Charged first hitmark");
        assertClose(34.0 * FRAME, charged.get(1).time,
                "Skirk physical Charged second hitmark");
        assertClose(53.0 * FRAME,
                basicsSimulator.getCurrentTime(),
                "Skirk physical Charged recovery");
        assertClose(1.227660,
                charged.get(0).action.getDamagePercent(),
                "Skirk physical Charged multiplier");

        double plungeCast = basicsSimulator.getCurrentTime();
        perform(basicsSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = onlyNamed(
                basicsRecords, "Havoc: Sunder High Plunge");
        assertClose(plungeCast + 48.0 * FRAME, plunge.time,
                "Skirk high-Plunge hitmark");
        assertClose(plungeCast + 74.0 * FRAME,
                basicsSimulator.getCurrentTime(),
                "Skirk high-Plunge recovery");
        assertClose(2.933586,
                plunge.action.getDamagePercent(),
                "Skirk high-Plunge multiplier");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Skirk high Plunge has no ICD");
    }

    private static void testSevenPhaseFlashResourceReplacementsAndParticles() {
        Skirk skirk = new Skirk(null, null, 0);
        CombatSimulator simulator = simulatorWith(skirk);
        List<ActionRecord> records = captureSkirkActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);
        performSkill(simulator);
        assertClose(43.0 * FRAME, simulator.getCurrentTime(),
                "Skirk Press Skill recovery");
        assertTrue(skirk.isSevenPhaseFlashActive(),
                "Skirk enters Flash at frame nineteen");
        assertClose(97.2, skirk.getSerpentsSubtlety(),
                "Skirk drains twice by Skill recovery");
        assertClose(0.0, skirk.getSkillCDRemaining(
                simulator.getCurrentTime()),
                "Skirk cooldown has not started during Flash");
        assertThrows(IllegalStateException.class,
                () -> performSkill(simulator),
                "Skirk rejects Skill while Flash is active");

        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = onlyNamed(
                records, "Seven-Phase Flash N1");
        assertClose(normalCast + 12.0 * FRAME, normal.time,
                "Flash N1 hitmark");
        assertClose(2.440263,
                normal.action.getDamagePercent(),
                "Flash N1 uses Skill level nine without passive");
        assertEquals(Element.CRYO, normal.action.getElement(),
                "Flash N1 is Cryo");
        assertEquals(ActionType.NORMAL, normal.action.getActionType(),
                "Flash N1 remains Normal damage");
        assertEquals(ICDType.Standard, normal.action.getICDType(),
                "Flash N1 standard ICD");
        advanceTo(simulator, normal.time + 100.0 * FRAME);
        assertEquals(1, particles.size(),
                "First Cryo hit creates one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Skirk particle packet has four particles");
        assertClose(normal.time + 100.0 * FRAME,
                particles.get(0).time,
                "Skirk particle travel is one hundred frames");

        Skirk chargedSkirk = new Skirk(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(chargedSkirk);
        List<ActionRecord> chargedRecords =
                captureSkirkActions(chargedSimulator);
        performSkill(chargedSimulator);
        double chargeCast = chargedSimulator.getCurrentTime();
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = prefix(
                chargedRecords, "Seven-Phase Flash Charged Hit");
        assertEquals(3, charged.size(),
                "Flash Charged has three hits");
        assertClose(chargeCast + 28.0 * FRAME,
                charged.get(0).time,
                "Flash Charged first hitmark");
        assertClose(chargeCast + 46.0 * FRAME,
                charged.get(2).time,
                "Flash Charged final hitmark");
        assertClose(0.818440,
                charged.get(0).action.getDamagePercent(),
                "Flash Charged Skill multiplier");

        Skirk depleted = new Skirk(null, null, 0);
        CombatSimulator depletedSimulator = simulatorWith(depleted);
        perform(depletedSimulator, CharacterActionKey.BURST);
        performSkill(depletedSimulator);
        double entryTime = (151.0 + 19.0) * FRAME;
        double expectedExit = entryTime + 33.0 * 12.0 * FRAME;
        advanceTo(depletedSimulator, expectedExit);
        assertTrue(!depleted.isSevenPhaseFlashActive(),
                "Flash exits on the thirty-third drain tick");
        assertClose(0.0, depleted.getSerpentsSubtlety(),
                "Flash exit clears Subtlety");
        assertClose(expectedExit, depleted.getLastSkillTime(),
                "Skill cooldown starts on Flash exit");
        assertClose(8.0, depleted.getSkillCDRemaining(expectedExit),
                "Flash exit starts the full eight-second cooldown");
    }

    private static void testHavocRuinBranch() {
        Skirk skirk = new Skirk(null, null, 0);
        CombatSimulator simulator = simulatorWith(skirk);
        List<ActionRecord> records = captureSkirkActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(151.0 * FRAME, simulator.getCurrentTime(),
                "Havoc Ruin recovery");
        assertClose(0.0, skirk.getSerpentsSubtlety(),
                "Havoc Ruin consumes all Subtlety at frame seven");
        assertClose(0.0, skirk.getLastBurstTime(),
                "Havoc Ruin starts Burst cooldown at cast");
        List<ActionRecord> slashes = prefix(
                records, "Havoc: Ruin Slash");
        assertEquals(5, slashes.size(),
                "Havoc Ruin resolves five slashes before recovery");
        int[] frames = { 109, 111, 114, 125, 135 };
        double expected = 2.086920 + 12.0 * 0.328497;
        for (int index = 0; index < frames.length; index++) {
            assertClose(frames[index] * FRAME,
                    slashes.get(index).time,
                    "Havoc Ruin slash frame " + index);
            assertClose(expected,
                    slashes.get(index).action.getDamagePercent(),
                    "Havoc Ruin capped Subtlety bonus");
            assertEquals(ICDTag.ElementalBurst,
                    slashes.get(index).action.getICDTag(),
                    "Havoc Ruin shared Burst ICD");
        }
        assertEquals(0, named(records,
                "Havoc: Ruin Final Slash").size(),
                "Havoc Ruin final lands after recovery");
        advanceTo(simulator, 158.0 * FRAME);
        assertClose(158.0 * FRAME,
                onlyNamed(records, "Havoc: Ruin Final Slash").time,
                "Havoc Ruin final hitmark");
        assertThrows(IllegalStateException.class,
                () -> perform(simulator, CharacterActionKey.BURST),
                "Havoc Ruin rejects less than fifty Subtlety");

        Skirk c2 = new Skirk(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureSkirkActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.BURST);
        double c2Expected = 2.086920 + 22.0 * 0.328497;
        assertClose(c2Expected,
                prefix(c2Records, "Havoc: Ruin Slash")
                        .get(0).action.getDamagePercent(),
                "C2 expands Ruin bonus to twenty-two points");
    }

    private static void testRiftExtinctionAndConstellations() {
        Skirk skirk = new Skirk(null, null, 6);
        CombatSimulator simulator = simulatorWith(skirk);
        List<ActionRecord> records = captureSkirkActions(simulator);
        for (int index = 0; index < 3; index++) {
            skirk.recordRepresentedVoidRift(simulator);
        }
        assertEquals(3, skirk.getRepresentedVoidRiftCount(0.0),
                "Skirk stores three source-confirmed rifts");
        skirk.recordRepresentedVoidRift(simulator);
        assertEquals(3, skirk.getRepresentedVoidRiftCount(0.0),
                "Fourth represented rift overwrites the oldest");

        performSkill(simulator);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(0,
                skirk.getRepresentedVoidRiftCount(
                        simulator.getCurrentTime()),
                "Extinction absorbs all represented rifts");
        assertEquals(3, prefix(records, "Far to Fall C1").size(),
                "Three absorbed rifts emit three C1 hits");
        assertEquals(3,
                skirk.getC6StackCount(simulator.getCurrentTime()),
                "Three absorbed rifts create three C6 stacks");
        assertClose(burstCast + 41.0 * FRAME,
                simulator.getCurrentTime(),
                "Extinction branch recovery");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord n1 = onlyNamed(records, "Seven-Phase Flash N1");
        assertClose(2.996272, n1.action.getDamagePercent(),
                "C3 raises Flash N1 to talent twelve");
        assertClose(0.220,
                n1.action.getExtraBonuses().getOrDefault(
                        StatType.DMG_BONUS_ALL, 0.0),
                "C5 raises three-rift Extinction damage bonus");
        assertClose(0.7,
                n1.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                "C2 applies seventy percent ATK after Extinction");

        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> c6Normals = prefix(
                records, "Havoc: Sever Normal");
        assertEquals(3, c6Normals.size(),
                "C6 N3 consumes one stack for three follow-ups");
        assertEquals(2,
                skirk.getC6StackCount(simulator.getCurrentTime()),
                "C6 N3 consumes exactly one stack");
        for (ActionRecord c6Normal : c6Normals) {
            assertClose(1.8,
                    c6Normal.action.getDamagePercent(),
                    "C6 Normal follow-up multiplier");
        }

        Skirk burstC6 = new Skirk(null, null, 6);
        CombatSimulator burstSimulator = simulatorWith(burstC6);
        List<ActionRecord> burstRecords =
                captureSkirkActions(burstSimulator);
        for (int index = 0; index < 2; index++) {
            burstC6.recordRepresentedVoidRift(burstSimulator);
        }
        assertEquals(2,
                burstC6.absorbRepresentedVoidRifts(burstSimulator),
                "Explicit fixed-target ingress absorbs two rifts");
        perform(burstSimulator, CharacterActionKey.BURST);
        List<ActionRecord> c6Bursts = prefix(
                burstRecords, "Havoc: Sever Burst");
        assertEquals(2, c6Bursts.size(),
                "C6 Ruin consumes all stored stacks");
        assertClose(12.0 * FRAME, c6Bursts.get(0).time,
                "C6 Ruin follow-up timing");
        assertClose(7.5,
                c6Bursts.get(0).action.getDamagePercent(),
                "C6 Ruin follow-up multiplier");
    }

    private static void testTalentPassiveA4AndC4() {
        Skirk skirk = new Skirk(null, null, 6);
        TestCharacter hydroOne = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        TestCharacter cryo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        TestCharacter hydroTwo = new TestCharacter(
                CharacterId.SANGONOMIYA_KOKOMI, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(
                skirk, hydroOne, cryo, hydroTwo);
        List<ActionRecord> records = captureSkirkActions(simulator);
        performAllyElementalHit(simulator, hydroOne);
        performAllyElementalHit(simulator, cryo);
        performAllyElementalHit(simulator, hydroTwo);
        assertEquals(3, skirk.getDeathsCrossingStacks(0.0),
                "Three unique Cryo/Hydro allies create three A4 stacks");
        performSkill(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord n1 = onlyNamed(records, "Seven-Phase Flash N1");
        assertClose(3.181608 * 1.7,
                n1.action.getDamagePercent(),
                "Talent passive, C3, and three-stack A4 multiply Flash N1");
        assertClose(0.4,
                n1.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                "C4 grants forty percent ATK at three A4 stacks");
        advanceTo(simulator, 20.0 + EPSILON);
        assertEquals(0,
                skirk.getDeathsCrossingStacks(
                        simulator.getCurrentTime()),
                "A4 contributors expire after twenty seconds");

        Skirk noPassive = new Skirk(null, null, 3);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator mixedSimulator = simulatorWith(noPassive, pyro);
        List<ActionRecord> mixedRecords =
                captureSkirkActions(mixedSimulator);
        performSkill(mixedSimulator);
        perform(mixedSimulator, CharacterActionKey.NORMAL);
        assertClose(2.996272,
                onlyNamed(mixedRecords,
                        "Seven-Phase Flash N1")
                        .action.getDamagePercent(),
                "Non-Cryo/Hydro party disables the Skill-level passive");
    }

    private static void testSnapshotAndFailClosedBoundaries() {
        Skirk skirk = new Skirk(null, null, 0);
        CombatSimulator simulator = simulatorWith(skirk);
        List<ActionRecord> records = captureSkirkActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 158.0 * FRAME);
        assertEquals(1, named(records,
                "Havoc: Ruin Final Slash").size(),
                "Live branch resolves one delayed final slash");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 158.0 * FRAME);
        assertEquals(1, named(records,
                "Havoc: Ruin Final Slash").size(),
                "Repeated restore reconstructs one final slash");

        assertThrows(IllegalArgumentException.class,
                () -> skirk.onAction(
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD),
                        simulator),
                "Skirk rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> skirk.onAction(null, simulator),
                "Skirk rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Skirk rejects movement actions");
        assertTrue(!skirk.isVoidRiftGeometryRepresented(),
                "Void Rift geometry fails closed");
        assertTrue(!skirk.isAutomaticRiftTeamPlumbingRepresented(),
                "Automatic reaction team plumbing fails closed");
        assertTrue(!skirk.isMultiTargetRandomCollectionRepresented(),
                "Multi-target and randomness fail closed");
        assertTrue(!skirk.isPlayerHpHealingDefenseRepresented(),
                "HP, healing, and defense fail closed");
        assertTrue(!skirk.isMovementHitlagStaminaRepresented(),
                "Movement, hitlag, and stamina fail closed");
        assertTrue(!skirk.isLowPlungeExplorationRepresented(),
                "Low Plunge and exploration fail closed");

        Skirk reused = new Skirk(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Skirk rejects cross-simulator reuse");
        Skirk foreign = new Skirk(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!skirk.acceptsCharacterState(foreignState),
                "Skirk rejects another owner's snapshot payload");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.SKIRK,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.SKIRK,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static void performAllyElementalHit(
            CombatSimulator simulator,
            Character ally) {
        AttackAction action = new AttackAction(
                ally.getName() + " source hit",
                1.0,
                ally.getElement(),
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.ElementalSkill, 1.0);
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(), action);
    }

    private static List<ActionRecord> captureSkirkActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.SKIRK) {
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
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static List<ActionRecord> prefix(
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
            String name) {
        List<ActionRecord> selected = named(records, name);
        assertEquals(1, selected.size(), name + " record count");
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
            assertTrue(lines.get(index).startsWith("Skirk,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Skirk/Skirk_Status.csv",
                "config/characters/Skirk/Skirk_Multipliers.csv"
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
        throw new AssertionError("Skirk CSVs missing key " + key);
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
