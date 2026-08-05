package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Xianyun;
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
import simulation.action.SkillActionMode;

/** Focused regressions for Xianyun's fixed-target Starwicker slice. */
public final class XianyunRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private XianyunRegressionTest() {
    }

    /** Runs data, action, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testCatalystBasics();
        testDriftcloudWaveParticlesAndCooldown();
        testBurstStarwickerAndTriggerBoundaries();
        testA1A4AndConstellations();
        testC6FreeSkillsAndParticleBoundary();
        testSnapshotRestore();
        testFailClosedAndIsolationGuards();
        System.out.println("XianyunRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Xianyun xianyun = xianyun(6);
        assertEquals(CharacterId.XIANYUN, xianyun.getCharacterId(),
                "Xianyun typed identity");
        assertEquals(CharacterId.XIANYUN,
                CharacterId.fromName("Xianyun"),
                "Xianyun name lookup");
        assertEquals(CharacterId.XIANYUN,
                CharacterId.fromNumericId(83),
                "Xianyun numeric lookup");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.XIANYUN.getRegion(),
                "Xianyun region");
        assertEquals(Element.ANEMO, xianyun.getElement(),
                "Xianyun element");
        assertClose(10409.0,
                xianyun.getBaseStats().get(StatType.BASE_HP),
                "Xianyun base HP");
        assertClose(335.0,
                xianyun.getBaseStats().get(StatType.BASE_ATK),
                "Xianyun base ATK");
        assertClose(573.0,
                xianyun.getBaseStats().get(StatType.BASE_DEF),
                "Xianyun base DEF");
        assertClose(0.288,
                xianyun.getBaseStats().get(StatType.ATK_PERCENT),
                "Xianyun ascension ATK");
        assertClose(70.0, xianyun.getEnergyCost(),
                "Xianyun Energy cost");
        assertClose(12.0, xianyun.getSkillCD(),
                "Xianyun Skill cooldown");
        assertClose(18.0, xianyun.getBurstCD(),
                "Xianyun Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    xianyun(constellation).getConstellation(),
                    "Xianyun accepts C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Xianyun/Xianyun_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Xianyun/Xianyun_Multipliers.csv"), 42);
        assertCsvValue("Driftcloud Wave 3 C5", 6.752);
        assertCsvValue("Starwicker C3", 0.784);
        assertCsvValue("C2 A4 Flat DMG Cap", 18000.0);
    }

    private static void testCatalystBasics() {
        Xianyun xianyun = xianyun(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(xianyun, ally);
        List<ActionRecord> records = captureActions(simulator);
        double[] normalValues = {
            0.685141, 0.660538, 0.830919, 1.103586
        };
        int[] hitFrames = { 12, 14, 34, 38 };
        int[] durations = { 34, 38, 65, 93 };
        double elapsedFrames = 0.0;
        for (int step = 0; step < normalValues.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord normal = named(records,
                    "Word of Wind and Flower N" + (step + 1)).get(0);
            assertClose(normalValues[step],
                    normal.action.getDamagePercent(),
                    "Xianyun N" + (step + 1) + " multiplier");
            assertClose((elapsedFrames + hitFrames[step]) * FRAME,
                    normal.time,
                    "Xianyun N" + (step + 1) + " hitmark");
            assertEquals(Element.ANEMO, normal.action.getElement(),
                    "Xianyun catalyst Normal element");
            assertEquals(ICDTag.NormalAttack,
                    normal.action.getICDTag(),
                    "Xianyun Normal ICD tag");
            elapsedFrames += durations[step];
        }
        assertClose(230.0 * FRAME, simulator.getCurrentTime(),
                "Xianyun Normal string duration");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.XIANYUN);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records,
                "Word of Wind and Flower N1").size(),
                "Xianyun switch resets Normal progression");

        Xianyun chargedOwner = xianyun(0);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(chargedRecords,
                "Word of Wind and Flower Charged").get(0);
        assertClose(2.09304, charged.action.getDamagePercent(),
                "Xianyun Charged multiplier");
        assertClose(56.0 * FRAME, charged.time,
                "Xianyun Charged hitmark");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Xianyun Charged uses no ICD");

        Xianyun plungeOwner = xianyun(0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(
                plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(plungeRecords,
                "Word of Wind and Flower High Plunge").get(0);
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Xianyun fixed high-Plunge multiplier");
        assertClose(46.0 * FRAME, plunge.time,
                "Xianyun fixed high-Plunge hitmark");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Xianyun high Plunge category");
    }

    private static void testDriftcloudWaveParticlesAndCooldown() {
        Xianyun c0 = xianyun(0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, c0.getSkillLeapCount(simulator.getCurrentTime()),
                "First Skyladder enters one-leap state");
        assertEquals(0, records.size(),
                "Skyladder collision damage fails closed");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(3, c0.getSkillLeapCount(simulator.getCurrentTime()),
                "Third Skyladder reaches the represented cap");
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord wave = named(records,
                "Driftcloud Wave (3 Leaps)").get(0);
        assertClose(93.0 * FRAME, wave.time,
                "Three-leap Driftcloud hitmark");
        assertClose(5.7392, wave.action.getDamagePercent(),
                "C0 three-leap Driftcloud multiplier");
        assertEquals(ActionType.PLUNGE, wave.action.getActionType(),
                "Driftcloud is Plunge damage");
        assertEquals(0, c0.getSkillLeapCount(simulator.getCurrentTime()),
                "Driftcloud consumes the Skill state");
        assertEquals(0, particles.size(),
                "Particles remain in flight after Driftcloud recovery");
        advanceTo(simulator, 193.0 * FRAME);
        assertEquals(1, particles.size(),
                "One accepted Driftcloud queues one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Driftcloud particle count");
        assertClose(193.0 * FRAME, particles.get(0).time,
                "Driftcloud particle arrival");

        Xianyun c5 = xianyun(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        perform(c5Simulator, CharacterActionKey.SKILL);
        perform(c5Simulator, CharacterActionKey.SKILL);
        perform(c5Simulator, CharacterActionKey.PLUNGE);
        assertClose(6.752,
                named(c5Records, "Driftcloud Wave").get(0)
                        .action.getDamagePercent(),
                "C5 raises Driftcloud talent level");

        Xianyun unused = xianyun(0);
        CombatSimulator unusedSimulator = simulatorWith(unused);
        perform(unusedSimulator, CharacterActionKey.SKILL);
        advanceTo(unusedSimulator, 220.0 * FRAME);
        assertEquals(0, unused.getSkillLeapCount(
                unusedSimulator.getCurrentTime()),
                "Unused one-leap state expires exactly");
        assertClose(9.0 - 220.0 * FRAME,
                unused.getSkillCDRemaining(
                        unusedSimulator.getCurrentTime()),
                "Unused Driftcloud state reduces cooldown by three seconds");

        Xianyun fourth = xianyun(0);
        CombatSimulator fourthSimulator = simulatorWith(fourth);
        perform(fourthSimulator, CharacterActionKey.SKILL);
        perform(fourthSimulator, CharacterActionKey.SKILL);
        perform(fourthSimulator, CharacterActionKey.SKILL);
        perform(fourthSimulator, CharacterActionKey.SKILL);
        assertClose(9.0 + 14.0 * FRAME,
                fourthSimulator.getCurrentTime(),
                "Fourth Skill waits for the exact reduced base cooldown");
        assertEquals(1, fourth.getSkillLeapCount(
                fourthSimulator.getCurrentTime()),
                "Fourth Skill starts a fresh sequence after readiness");

        Xianyun c1 = xianyun(1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.PLUNGE);
        double secondStart = c1Simulator.getCurrentTime();
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertClose(secondStart + 14.0 * FRAME,
                c1Simulator.getCurrentTime(),
                "C1 second charge starts without waiting");
    }

    private static void testBurstStarwickerAndTriggerBoundaries() {
        Xianyun xianyun = xianyun(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(xianyun, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord initial = named(records,
                "Stars Gather at Dusk (Initial)").get(0);
        assertClose(75.0 * FRAME, initial.time,
                "Xianyun Burst initial hitmark");
        assertClose(1.836, initial.action.getDamagePercent(),
                "C0 Burst initial multiplier");
        assertClose(0.0, xianyun.getCurrentEnergy(),
                "Burst spends seventy Energy at frame eighteen");
        assertEquals(8, xianyun.getAdeptalAssistanceStacks(
                simulator.getCurrentTime()),
                "Burst creates eight Adeptal Assistance stacks");

        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, "Non-Plunge probe");
        assertEquals(8, xianyun.getAdeptalAssistanceStacks(
                simulator.getCurrentTime()),
                "Normal damage does not consume Starwicker");
        double plungeTime = simulator.getCurrentTime();
        directHit(simulator, CharacterId.NOELLE,
                ActionType.PLUNGE, "Active Plunge probe");
        assertEquals(7, xianyun.getAdeptalAssistanceStacks(
                simulator.getCurrentTime()),
                "Active Plunge consumes one stack");
        simulator.advanceTime(5.0 * FRAME);
        ActionRecord starwicker = named(records, "Starwicker").get(0);
        assertClose(plungeTime + 5.0 * FRAME, starwicker.time,
                "Starwicker follows after five frames");
        assertClose(0.6664, starwicker.action.getDamagePercent(),
                "C0 Starwicker multiplier");
        assertEquals(ActionType.BURST,
                starwicker.action.getActionType(),
                "Starwicker is Burst damage");

        simulator.setActiveCharacter(CharacterId.XIANYUN);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.PLUNGE, "Off-field Plunge probe");
        assertEquals(7, xianyun.getAdeptalAssistanceStacks(
                simulator.getCurrentTime()),
                "Off-field actor cannot consume Starwicker");
        advanceTo(simulator, 75.0 * FRAME + 16.0);
        assertEquals(0, xianyun.getAdeptalAssistanceStacks(
                simulator.getCurrentTime()),
                "Adeptal Assistance expires with the Burst window");

        Xianyun c3 = xianyun(3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        assertClose(2.16,
                named(c3Records, "Stars Gather at Dusk").get(0)
                        .action.getDamagePercent(),
                "C3 raises initial Burst multiplier");
        perform(c3Simulator, CharacterActionKey.PLUNGE);
        assertClose(0.784,
                named(c3Records, "Starwicker").get(0)
                        .action.getDamagePercent(),
                "C3 raises Starwicker multiplier");
    }

    private static void testA1A4AndConstellations() {
        Xianyun c0 = xianyun(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c0, ally);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, c0.getA1StackCount(simulator.getCurrentTime()),
                "One fixed Driftcloud target grants one A1 stack");
        StatsContainer a1Stats = new StatsContainer();
        c0.applyTargetDependentTeamStats(
                a1Stats,
                ally,
                simulator.getEnemy(),
                probe(ActionType.PLUNGE, "A1 probe"),
                simulator.getCurrentTime());
        assertClose(0.04,
                a1Stats.get(StatType.PLUNGING_ATTACK_CRIT_RATE),
                "One A1 stack grants four percent Plunge CRIT Rate");
        double expiry = simulator.getCurrentTime() + 20.0;
        advanceTo(simulator, expiry);
        assertEquals(0, c0.getA1StackCount(simulator.getCurrentTime()),
                "A1 stack expires at twenty seconds");

        Xianyun a4 = xianyun(0);
        TestCharacter a4Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator a4Simulator = simulatorWith(a4, a4Ally);
        perform(a4Simulator, CharacterActionKey.BURST);
        a4Simulator.setActiveCharacter(CharacterId.NOELLE);
        StatsContainer a4Stats = new StatsContainer();
        a4.applyTargetDependentTeamStats(
                a4Stats,
                a4Ally,
                a4Simulator.getEnemy(),
                probe(ActionType.PLUNGE, "A4 probe"),
                a4Simulator.getCurrentTime());
        assertClose(2.0 * 335.0 * 1.288,
                a4Stats.get(StatType.FLAT_DMG_BONUS),
                "A4 adds two hundred percent of live Xianyun ATK");

        Xianyun c2 = xianyun(2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        perform(c2Simulator, CharacterActionKey.SKILL);
        assertClose(0.488,
                c2.getEffectiveStats(c2Simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "C2 Skill grants twenty percent ATK");
        advanceTo(c2Simulator, 15.0 + EPSILON);
        assertClose(0.288,
                c2.getEffectiveStats(c2Simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "C2 ATK expires after fifteen seconds");

        Xianyun c2A4 = xianyun(2);
        TestCharacter c2Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c2A4Simulator = simulatorWith(c2A4, c2Ally);
        perform(c2A4Simulator, CharacterActionKey.BURST);
        c2A4Simulator.setActiveCharacter(CharacterId.NOELLE);
        StatsContainer c2A4Stats = new StatsContainer();
        c2A4.applyTargetDependentTeamStats(
                c2A4Stats,
                c2Ally,
                c2A4Simulator.getEnemy(),
                probe(ActionType.PLUNGE, "C2 A4 probe"),
                c2A4Simulator.getCurrentTime());
        assertClose(4.0 * 335.0 * 1.288,
                c2A4Stats.get(StatType.FLAT_DMG_BONUS),
                "C2 doubles the A4 live-ATK ratio");
    }

    private static void testC6FreeSkillsAndParticleBoundary() {
        Xianyun c6 = xianyun(6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(8, c6.getC6FreeSkillUses(simulator.getCurrentTime()),
                "C6 Burst grants eight free Skill starts");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord wave = named(records,
                "Driftcloud Wave (3 Leaps)").get(0);
        assertClose(0.70,
                wave.action.getStatSnapshot().get(
                        StatType.PLUNGING_ATTACK_CRIT_DMG),
                "C6 three-leap Wave gains seventy percent CRIT DMG");
        assertEquals(7, c6.getC6FreeSkillUses(
                simulator.getCurrentTime()),
                "One C6 sequence consumes one free use");
        for (int index = 0; index < 3; index++) {
            perform(simulator, CharacterActionKey.SKILL);
            perform(simulator, CharacterActionKey.PLUNGE);
        }
        assertEquals(4, c6.getA1StackCount(simulator.getCurrentTime()),
                "Four fixed Driftcloud hits reach the A1 cap");
        StatsContainer cappedA1 = new StatsContainer();
        c6.applyTargetDependentTeamStats(
                cappedA1,
                c6,
                simulator.getEnemy(),
                probe(ActionType.PLUNGE, "C6 A1 cap probe"),
                simulator.getCurrentTime());
        assertClose(0.10,
                cappedA1.get(StatType.PLUNGING_ATTACK_CRIT_RATE),
                "Four A1 stacks grant ten percent Plunge CRIT Rate");
        simulator.advanceTime(100.0 * FRAME);
        assertEquals(0, particles.size(),
                "C6 free Driftcloud sequence produces no particles");
    }

    private static void testSnapshotRestore() {
        Xianyun xianyun = xianyun(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(xianyun, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.PLUNGE, "Snapshot Plunge");
        assertEquals(1, xianyun.getPendingHitCount(),
                "Starwicker is pending before its five-frame delay");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(5.0 * FRAME);
        ActionRecord original = named(records, "Starwicker").get(0);
        double originalDamage = original.damage;
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(5.0 * FRAME);
        List<ActionRecord> restored = named(records, "Starwicker");
        assertEquals(1, restored.size(),
                "Repeated restore reconstructs Starwicker once");
        assertClose(originalDamage, restored.get(0).damage,
                "Restored Starwicker preserves trigger-time stats");
        assertEquals(0, xianyun.getPendingHitCount(),
                "Restored Starwicker queue drains cleanly");
    }

    private static void testFailClosedAndIsolationGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> xianyun(-1),
                "Xianyun rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> xianyun(7),
                "Xianyun rejects constellation above C6");
        Xianyun xianyun = xianyun(0);
        CombatSimulator simulator = simulatorWith(xianyun);
        assertTrue(!xianyun.isSkyladderCollisionDamageRepresented(),
                "Skyladder collision remains geometry-gated");
        assertTrue(!xianyun.isHealingRepresented(),
                "Healing and player HP remain excluded");
        assertTrue(!xianyun.isPlungeHeightStateRepresented(),
                "Plunge height state remains excluded");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.XIANYUN,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Xianyun rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Xianyun rejects unsupported Dash");
        perform(simulator, CharacterActionKey.SKILL);
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.NORMAL),
                "Cloud state rejects Normal attacks");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Cloud state rejects Charged attacks");
        assertThrows(IllegalArgumentException.class,
                () -> xianyun.onAction(null, simulator),
                "Xianyun rejects null action");

        Xianyun external = xianyun(0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Xianyun rejects binding outside a simulator party");
        Xianyun reused = xianyun(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Xianyun rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!xianyun.acceptsCharacterState(foreignState),
                "Xianyun rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> xianyun.restoreCharacterState(
                        foreignState, simulator),
                "Xianyun rejects foreign restore payload");

        Xianyun noTarget = xianyun(0);
        CombatSimulator empty = new CombatSimulator();
        empty.setLoggingEnabled(false);
        empty.addCharacter(noTarget);
        perform(empty, CharacterActionKey.SKILL);
        perform(empty, CharacterActionKey.PLUNGE);
        assertEquals(0, noTarget.getA1StackCount(empty.getCurrentTime()),
                "No target grants no A1 stack");
    }

    private static Xianyun xianyun(int constellation) {
        return new Xianyun(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Xianyun xianyun,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(xianyun);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.XIANYUN);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.XIANYUN,
                CharacterActionRequest.of(key));
    }

    private static void directHit(
            CombatSimulator simulator,
            CharacterId actor,
            ActionType type,
            String name) {
        simulator.performActionWithoutTimeAdvance(actor, probe(type, name));
    }

    private static AttackAction probe(ActionType type, String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.GEO,
                StatType.BASE_ATK,
                type == ActionType.PLUNGE
                        ? StatType.PLUNGING_ATTACK_DMG_BONUS : null,
                0.0,
                type);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(action, damage, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
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
            assertTrue(lines.get(index).startsWith("Xianyun,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Xianyun/Xianyun_Status.csv",
                "config/characters/Xianyun/Xianyun_Multipliers.csv"
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
        throw new AssertionError("Xianyun CSVs missing key " + key);
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
