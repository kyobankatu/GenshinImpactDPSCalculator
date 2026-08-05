package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Neuvillette;
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

/** Focused regression checks for Neuvillette's fixed-target Judgment slice. */
public final class NeuvilletteRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private NeuvilletteRegressionTest() {
    }

    /** Runs data, timing, passive, constellation, exclusion, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testCatalystNormalsAndUnsupportedActions();
        testEquitableJudgmentCadenceAndScaling();
        testTypedA1StacksC1AndC2();
        testSkillParticlesThornAndCooldown();
        testBurstTimingEnergyC5AndLiveSnapshots();
        testC6CurrentsAndIndependentIcd();
        testFailClosedScopeAndIsolation();
        testSnapshotRestore();
        System.out.println("NeuvilletteRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Neuvillette neuvillette = new Neuvillette(null, null, 6);
        assertEquals(CharacterId.NEUVILLETTE,
                neuvillette.getCharacterId(),
                "Neuvillette typed identity");
        assertEquals(CharacterId.NEUVILLETTE,
                CharacterId.fromName("Neuvillette"),
                "Neuvillette name lookup");
        assertEquals(CharacterId.NEUVILLETTE,
                CharacterId.fromNumericId(90),
                "Neuvillette numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.NEUVILLETTE.getRegion(),
                "Neuvillette region");
        assertEquals(Element.HYDRO, neuvillette.getElement(),
                "Neuvillette element");
        assertClose(14695.0,
                neuvillette.getBaseStats().get(StatType.BASE_HP),
                "Neuvillette base HP");
        assertClose(208.0,
                neuvillette.getBaseStats().get(StatType.BASE_ATK),
                "Neuvillette base ATK");
        assertClose(576.0,
                neuvillette.getBaseStats().get(StatType.BASE_DEF),
                "Neuvillette base DEF");
        assertClose(0.884,
                neuvillette.getBaseStats().get(StatType.CRIT_DMG),
                "Neuvillette base and ascension CRIT DMG");
        assertClose(70.0, neuvillette.getEnergyCost(),
                "Neuvillette Energy cost");
        assertClose(12.0, neuvillette.getSkillCD(),
                "Neuvillette Skill cooldown");
        assertClose(18.0, neuvillette.getBurstCD(),
                "Neuvillette Burst cooldown");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Neuvillette(null, null, constellation)
                            .getConstellation(),
                    "Neuvillette explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Neuvillette/"
                        + "Neuvillette_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Neuvillette/"
                        + "Neuvillette_Multipliers.csv"), 27);
        assertCsvValue("Equitable Judgment C3", 0.165094);
        assertCsvValue("O Tides I Have Returned C5", 0.445157);
        assertCsvValue("C6 Max HP Ratio", 0.10);
        assertThrows(IllegalArgumentException.class,
                () -> new Neuvillette(null, null, -1),
                "Neuvillette rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Neuvillette(null, null, 7),
                "Neuvillette rejects constellation above C6");
    }

    private static void testCatalystNormalsAndUnsupportedActions() {
        Neuvillette neuvillette = new Neuvillette(null, null, 0);
        CombatSimulator simulator = simulatorWith(neuvillette);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = { 0.927806, 0.786175, 1.229739 };
        int[] hitFrames = { 19, 16, 32 };
        int[] durations = { 36, 33, 62 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord normal = onlyNamed(
                    records, "Normal " + (step + 1));
            assertClose(castTime + hitFrames[step] * FRAME,
                    normal.time,
                    "Neuvillette N" + (step + 1) + " hitmark");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Neuvillette N" + (step + 1) + " recovery");
            assertClose(multipliers[step],
                    normal.action.getDamagePercent(),
                    "Neuvillette N" + (step + 1) + " multiplier");
            assertEquals(Element.HYDRO, normal.action.getElement(),
                    "Neuvillette catalyst Normal element");
            assertEquals(ActionType.NORMAL,
                    normal.action.getActionType(),
                    "Neuvillette Normal action type");
            assertEquals(ICDType.Standard,
                    normal.action.getICDType(),
                    "Neuvillette Normal ICD type");
            assertEquals(ICDTag.NormalAttack,
                    normal.action.getICDTag(),
                    "Neuvillette Normal ICD tag");
            assertClose(1.0, normal.action.getGaugeUnits(),
                    "Neuvillette Normal gauge");
            assertClose(0.30,
                    normal.action.getStatSnapshot().get(
                            StatType.HYDRO_DMG_BONUS),
                    "Full-HP A4 is present on Normal snapshot");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Normal 1").size(),
                "Neuvillette Normal string wraps after N3");

        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Neuvillette rejects Plunge");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Neuvillette rejects movement actions");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.NEUVILLETTE,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Neuvillette rejects unsupported Hold Skill");
    }

    private static void testEquitableJudgmentCadenceAndScaling() {
        Neuvillette c0 = new Neuvillette(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> beam = named(
                records, "Charged Attack: Equitable Judgment");
        int[] hitFrames = { 232, 254, 279, 304, 329, 354, 379, 399 };
        assertEquals(8, beam.size(),
                "Full Equitable Judgment emits eight hits");
        for (int index = 0; index < beam.size(); index++) {
            ActionRecord hit = beam.get(index);
            assertClose(hitFrames[index] * FRAME, hit.time,
                    "Judgment hitmark " + (index + 1));
            assertClose(0.134458,
                    hit.action.getDamagePercent(),
                    "Judgment C0 multiplier " + (index + 1));
            assertEquals(StatType.BASE_HP,
                    hit.action.getScalingStat(),
                    "Judgment Max-HP scaling");
            assertEquals(StatType.CHARGED_ATTACK_DMG_BONUS,
                    hit.action.getBonusStat(),
                    "Judgment Charged DMG bonus category");
            assertEquals(ActionType.CHARGE,
                    hit.action.getActionType(),
                    "Judgment Charged action category");
            assertEquals(ICDTag.Neuvillette_Judgment,
                    hit.action.getICDTag(),
                    "Judgment private ICD tag");
            assertClose(0.30,
                    hit.action.getStatSnapshot().get(
                            StatType.HYDRO_DMG_BONUS),
                    "Judgment fixed-full-HP A4");
        }
        assertClose(450.0 * FRAME, simulator.getCurrentTime(),
                "No-droplet full Judgment fixed recovery");

        Neuvillette c3 = new Neuvillette(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.CHARGE);
        assertClose(0.165094 * 1.1,
                onlyNamed(c3Records,
                        "Charged Attack: Equitable Judgment")
                        .action.getDamagePercent(),
                "C3 talent and C1 base stack scale Judgment");
    }

    private static void testTypedA1StacksC1AndC2() {
        Neuvillette c2 = new Neuvillette(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c2, ally);
        simulator.notifyReaction(
                ReactionResult.amp(
                        1.5,
                        "Vaporize",
                        ReactionResult.Kind.VAPORIZE),
                ally);
        assertEquals(2,
                c2.getPastDraconicGloriesStackCount(0.0),
                "C1 base plus Vaporize stack");
        simulator.notifyReaction(
                ReactionResult.amp(
                        1.5,
                        "Vaporize",
                        ReactionResult.Kind.VAPORIZE),
                ally);
        assertEquals(2,
                c2.getPastDraconicGloriesStackCount(0.0),
                "Repeated Vaporize refreshes its typed family");
        simulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Electro-Charged",
                        ReactionResult.Kind.ELECTRO_CHARGED),
                ally);
        assertEquals(3,
                c2.getPastDraconicGloriesStackCount(0.0),
                "Distinct Hydro reaction reaches A1 cap");
        simulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Cryo Swirl",
                        ReactionResult.Kind.SWIRL,
                        Element.CRYO),
                ally);
        assertEquals(3,
                c2.getPastDraconicGloriesStackCount(0.0),
                "Non-Hydro Swirl does not alter A1 state");

        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord hit = onlyNamed(
                records, "Charged Attack: Equitable Judgment");
        assertClose(0.134458 * 1.6,
                hit.action.getDamagePercent(),
                "Three A1 stacks multiply Judgment by 1.6");
        assertClose(0.884 + 3.0 * 0.14,
                hit.action.getStatSnapshot().get(StatType.CRIT_DMG),
                "C2 adds fourteen percent CRIT DMG per A1 stack");

        Neuvillette expiry = new Neuvillette(null, null, 0);
        TestCharacter expiryAlly = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator expirySimulator = simulatorWith(
                expiry, expiryAlly);
        expirySimulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Hydro Swirl",
                        ReactionResult.Kind.SWIRL,
                        Element.HYDRO),
                expiryAlly);
        assertEquals(1,
                expiry.getPastDraconicGloriesStackCount(
                        30.0 - EPSILON),
                "A1 stack is active before thirty seconds");
        assertEquals(0,
                expiry.getPastDraconicGloriesStackCount(30.0),
                "A1 stack expires at exact thirty seconds");
        TestCharacter foreign = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        expiry.onReaction(
                ReactionResult.amp(
                        1.5,
                        "Vaporize",
                        ReactionResult.Kind.VAPORIZE),
                foreign,
                30.0,
                expirySimulator);
        assertEquals(0,
                expiry.getPastDraconicGloriesStackCount(30.0),
                "Foreign source cannot create A1 state");
    }

    private static void testSkillParticlesThornAndCooldown() {
        Neuvillette neuvillette = new Neuvillette(null, null, 0);
        CombatSimulator simulator = simulatorWith(neuvillette);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(42.0 * FRAME, simulator.getCurrentTime(),
                "Skill recovery");
        assertClose(20.0 * FRAME, neuvillette.getLastSkillTime(),
                "Skill cooldown starts at frame twenty");
        assertClose(698.0 * FRAME,
                neuvillette.getSkillCDRemaining(
                        simulator.getCurrentTime()),
                "Skill retains source twelve-second cooldown");
        ActionRecord skill = onlyNamed(records,
                "O Tears, I Shall Repay");
        assertClose(23.0 * FRAME, skill.time,
                "Skill initial hitmark");
        assertClose(0.218688, skill.action.getDamagePercent(),
                "Skill Max-HP multiplier");
        assertEquals(StatType.BASE_HP,
                skill.action.getScalingStat(),
                "Skill scales from Max HP");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Skill initial hit has no ICD");

        advanceTo(simulator, 123.0 * FRAME);
        ActionRecord thorn = onlyNamed(
                records, "Spiritbreath Thorn (Neuvillette)");
        assertClose(60.0 * FRAME, thorn.time,
                "Spiritbreath Thorn hitmark");
        assertClose(0.3536, thorn.action.getDamagePercent(),
                "Spiritbreath Thorn ATK multiplier");
        assertEquals(StatType.BASE_ATK,
                thorn.action.getScalingStat(),
                "Spiritbreath Thorn scales from ATK");
        assertClose(0.0, thorn.action.getGaugeUnits(),
                "Spiritbreath Thorn applies no aura");
        assertEquals(1, particles.size(),
                "Skill creates one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Skill packet contains four Hydro particles");
        assertClose(123.0 * FRAME, particles.get(0).time,
                "Skill particles use one-hundred-frame travel");
        assertClose(23.0 * FRAME + 0.3,
                neuvillette.getNextParticleAllowedTime(),
                "Skill starts exact particle gate on accepted damage");
    }

    private static void testBurstTimingEnergyC5AndLiveSnapshots() {
        Neuvillette c0 = new Neuvillette(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(135.0 * FRAME, simulator.getCurrentTime(),
                "Burst fixed recovery");
        assertClose(0.0, c0.getLastBurstTime(),
                "Burst cooldown starts at cast");
        assertClose(18.0 - 135.0 * FRAME,
                c0.getBurstCDRemaining(simulator.getCurrentTime()),
                "Burst retains source eighteen-second cooldown");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Burst spends seventy Energy at frame four");
        ActionRecord initial = onlyNamed(
                records, "O Tides, I Have Returned: Skill DMG");
        assertClose(95.0 * FRAME, initial.time,
                "Burst initial hitmark");
        assertClose(0.378383, initial.action.getDamagePercent(),
                "Burst initial Max-HP multiplier");
        assertEquals(StatType.BASE_HP,
                initial.action.getScalingStat(),
                "Burst initial scales from Max HP");
        List<ActionRecord> waterfalls = named(
                records, "O Tides, I Have Returned: Waterfall DMG");
        assertEquals(1, waterfalls.size(),
                "First Waterfall resolves at frame 135");
        assertClose(135.0 * FRAME, waterfalls.get(0).time,
                "First Waterfall hitmark");
        assertClose(0.154793,
                waterfalls.get(0).action.getDamagePercent(),
                "C0 Waterfall multiplier");

        c0.addBuff(new SimpleBuff(
                "Post-cast HP mutation",
                10.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.HP_PERCENT, 1.0)));
        advanceTo(simulator, 154.0 * FRAME);
        waterfalls = named(
                records, "O Tides, I Have Returned: Waterfall DMG");
        assertEquals(2, waterfalls.size(),
                "Burst resolves both Waterfall hits");
        assertClose(0.0,
                waterfalls.get(0).action.getStatSnapshot().get(
                        StatType.HP_PERCENT),
                "First Waterfall snapshots before HP mutation");
        assertClose(1.0,
                waterfalls.get(1).action.getStatSnapshot().get(
                        StatType.HP_PERCENT),
                "Second Waterfall uses source-defined hit-time snapshot");

        Neuvillette c5 = new Neuvillette(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        advanceTo(c5Simulator, 154.0 * FRAME);
        assertClose(0.445157,
                onlyNamed(c5Records,
                        "O Tides, I Have Returned: Skill DMG")
                        .action.getDamagePercent(),
                "C5 raises Burst initial talent");
        assertClose(0.182109,
                named(c5Records,
                        "O Tides, I Have Returned: Waterfall DMG")
                        .get(0).action.getDamagePercent(),
                "C5 raises Waterfall talent");
    }

    private static void testC6CurrentsAndIndependentIcd() {
        Neuvillette c6 = new Neuvillette(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> beam = named(
                records, "Charged Attack: Equitable Judgment");
        assertClose(0.165094 * 1.1,
                beam.get(0).action.getDamagePercent(),
                "C6 includes C3 talent and C1 base A1 stack");
        assertClose(0.884 + 0.14,
                beam.get(0).action.getStatSnapshot().get(
                        StatType.CRIT_DMG),
                "C6 includes C2 CRIT DMG for base stack");
        List<ActionRecord> currents = named(
                records, "Charged Attack: Equitable Judgment (C6)");
        assertEquals(4, currents.size(),
                "Full beam calls two C6 current pairs");
        double[] times = {
            261.0 * FRAME,
            261.0 * FRAME,
            383.0 * FRAME,
            383.0 * FRAME
        };
        for (int index = 0; index < currents.size(); index++) {
            ActionRecord current = currents.get(index);
            assertClose(times[index], current.time,
                    "C6 current impact " + (index + 1));
            assertClose(0.11,
                    current.action.getDamagePercent(),
                    "C6 Max-HP ratio includes one A1 stack");
            assertEquals(StatType.BASE_HP,
                    current.action.getScalingStat(),
                    "C6 current scales from Max HP");
            assertEquals(ICDTag.Neuvillette_C6,
                    current.action.getICDTag(),
                    "C6 private ICD tag");
        }
        assertClose(474.0 * FRAME, c6.getNextC6AllowedTime(),
                "Second C6 trigger starts an independent two-second gate");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                        "Neuvillette",
                        ICDTag.Neuvillette_Judgment,
                        ICDType.Standard,
                        0.0),
                "Judgment ICD admits its first hit");
        assertTrue(icd.checkApplication(
                        "Neuvillette",
                        ICDTag.Neuvillette_C6,
                        ICDType.Standard,
                        0.0),
                "C6 ICD remains independent at the same timestamp");
    }

    private static void testFailClosedScopeAndIsolation() {
        Neuvillette neuvillette = new Neuvillette(null, null, 0);
        assertTrue(!neuvillette.isPlayerHpStateRepresented(),
                "Player HP change, drain, and healing are excluded");
        assertTrue(!neuvillette.isSourcewaterDropletRepresented(),
                "Sourcewater Droplets and geometry are excluded");
        assertTrue(!neuvillette.isMovementGeometryRepresented(),
                "Hover and movement are excluded");
        assertTrue(!neuvillette.isMultiTargetRandomnessRepresented(),
                "Multi-target and random targeting are excluded");
        assertTrue(!neuvillette.isStaminaHitlagRepresented(),
                "Stamina and hitlag are excluded");
        assertTrue(!neuvillette.isLowPlungeRepresented(),
                "Low Plunge is excluded");
        assertTrue(!neuvillette.isDynamicA4Represented(),
                "Dynamic HP-ratio A4 is excluded");
        assertTrue(!neuvillette.isC6DurationExtensionRepresented(),
                "Droplet-driven C6 duration extension is excluded");

        CombatSimulator simulator = simulatorWith(neuvillette);
        assertThrows(IllegalArgumentException.class,
                () -> neuvillette.onAction(null, simulator),
                "Neuvillette rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> neuvillette.initializeForSimulator(null),
                "Neuvillette rejects null simulator");

        Neuvillette reused = new Neuvillette(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Neuvillette rejects cross-simulator reuse");
        Neuvillette foreign = new Neuvillette(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!neuvillette.acceptsCharacterState(foreignState),
                "Neuvillette rejects another owner's snapshot payload");
    }

    private static void testSnapshotRestore() {
        Neuvillette neuvillette = new Neuvillette(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(neuvillette, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        simulator.notifyReaction(
                ReactionResult.amp(
                        1.5,
                        "Vaporize",
                        ReactionResult.Kind.VAPORIZE),
                ally);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Electro-Charged",
                        ReactionResult.Kind.ELECTRO_CHARGED),
                ally);
        assertEquals(2,
                neuvillette.getPastDraconicGloriesStackCount(
                        simulator.getCurrentTime()),
                "Divergent branch mutates typed A1 state");
        records.clear();
        particles.clear();
        advanceTo(simulator, 123.0 * FRAME);
        assertEquals(1, named(records,
                "Spiritbreath Thorn (Neuvillette)").size(),
                "Original branch resolves one Thorn");
        assertEquals(1, particles.size(),
                "Original branch resolves one particle packet");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        assertEquals(1,
                neuvillette.getPastDraconicGloriesStackCount(
                        simulator.getCurrentTime()),
                "Rollback restores typed A1 state");
        advanceTo(simulator, 123.0 * FRAME);
        assertEquals(1, named(records,
                "Spiritbreath Thorn (Neuvillette)").size(),
                "Repeated restore reconstructs Thorn once");
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs particle packet once");
        assertEquals(0, neuvillette.getPendingEventCount(),
                "All reconstructed Skill events resolve exactly once");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
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
                CharacterId.NEUVILLETTE,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NEUVILLETTE) {
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

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = named(records, name);
        if (selected.isEmpty()) {
            throw new AssertionError("Missing action " + name);
        }
        return selected.get(0);
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(
            Path path,
            int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6,
                    lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Neuvillette,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Neuvillette/"
                        + "Neuvillette_Status.csv",
                "config/characters/Neuvillette/"
                        + "Neuvillette_Multipliers.csv"
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
        throw new AssertionError("Neuvillette CSVs missing key " + key);
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

    private static void assertTrue(
            boolean condition,
            String message) {
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
