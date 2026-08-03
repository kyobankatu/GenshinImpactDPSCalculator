package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Gorou;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Gorou's stationary field-support slice. */
public final class GorouRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private GorouRegressionTest() {
    }

    /** Runs data, timing, field, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstructors();
        testNormalStringAndHighPlunge();
        testSkillDamageParticlesAndFieldLinger();
        testGeoCountAndC3C6Tiers();
        testC6DoesNotSnapshot();
        testBurstA1CollapseAndDynamicStats();
        testC1AndC2Boundaries();
        testSwitchPersistenceReplacementAndLunarC2();
        testRepeatedRestoreAndStaleGenerations();
        testInvalidInputsCooldownAndEnergyRejection();
        System.out.println("GorouRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstructors()
            throws IOException {
        Gorou gorou = new Gorou(null, null);
        assertEquals(CharacterId.GOROU, gorou.getCharacterId(),
                "Gorou typed identity");
        assertEquals(Element.GEO, gorou.getElement(), "Gorou element");
        assertClose(9570.0,
                gorou.getBaseStats().get(StatType.BASE_HP),
                "Gorou base HP");
        assertClose(183.0,
                gorou.getBaseStats().get(StatType.BASE_ATK),
                "Gorou base ATK");
        assertClose(648.0,
                gorou.getBaseStats().get(StatType.BASE_DEF),
                "Gorou base DEF");
        assertClose(0.24,
                gorou.getBaseStats().get(StatType.GEO_DMG_BONUS),
                "Gorou ascension Geo DMG");
        assertClose(80.0, gorou.getEnergyCost(), "Gorou Energy cost");
        assertClose(10.0, gorou.getSkillCD(), "Gorou Skill cooldown");
        assertClose(20.0, gorou.getBurstCD(), "Gorou Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Gorou(null, null, constellation).getConstellation(),
                    "Gorou constellation " + constellation);
        }
        assertCsvShape(Paths.get(
                "config/characters/Gorou/Gorou_Status.csv"), 10);
        assertCsvShape(Paths.get(
                "config/characters/Gorou/Gorou_Multipliers.csv"), 19);
        assertTrue(Files.exists(Paths.get(
                "config/characters/Gorou/face.png")),
                "Gorou face asset remains present");
    }

    private static void testNormalStringAndHighPlunge() {
        Gorou gorou = new Gorou(null, null, 0);
        CombatSimulator simulator = simulatorWith(gorou);
        List<ActionRecord> records = captureGorouActions(simulator);
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        double[] multipliers = {
                0.69362, 0.68256, 0.9085, 1.08388
        };
        double[] impactFrames = { 27, 43, 84, 131 };
        assertEquals(4, records.size(), "Gorou N1-N4 damage instances");
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Gorou Normal multiplier " + index);
            assertClose(impactFrames[index] * FRAME,
                    record.time, "Gorou Normal impact " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Gorou Normal category");
            assertEquals(ICDType.None,
                    record.action.getICDType(), "Gorou Normal ICD");
            assertEquals(ICDTag.None,
                    record.action.getICDTag(), "Gorou Normal ICD tag");
            assertClose(0.0, record.action.getGaugeUnits(),
                    "Gorou Physical Normal gauge");
            assertTrue(record.action.hasStatSnapshot(),
                    "Gorou arrow snapshots at release");
            assertTrue(!record.action.isShatterTrigger(),
                    "Gorou bow Normal is not blunt");
        }
        assertClose(145.0 * FRAME, simulator.getCurrentTime(),
                "Gorou N1-N4 animation durations");

        Gorou plungeGorou = new Gorou(null, null, 0);
        CombatSimulator plungeSim = simulatorWith(plungeGorou);
        List<ActionRecord> plungeRecords = captureGorouActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = plungeRecords.get(0);
        assertClose(0.0, plunge.time,
                "Gorou High Plunge uses repository bow impact policy");
        assertClose(1.0, plungeSim.getCurrentTime(),
                "Gorou High Plunge duration");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Gorou High Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                plunge.action.getActionType(),
                "Gorou High Plunge category");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Gorou High Plunge ICD");
        assertEquals(ICDTag.None, plunge.action.getICDTag(),
                "Gorou High Plunge ICD tag");
        assertTrue(plunge.action.isShatterTrigger(),
                "Gorou High Plunge is blunt");
    }

    private static void testSkillDamageParticlesAndFieldLinger() {
        Gorou gorou = new Gorou(null, null, 0);
        CombatSimulator simulator = simulatorWith(gorou);
        List<ActionRecord> records = captureGorouActions(simulator);
        List<ParticleRecord> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(
                records, "Inuzaka All-Round Defense").get(0);
        assertClose(34.0 * FRAME, skill.time, "Gorou Skill hitmark");
        assertClose(1.8224, skill.action.getDamagePercent(),
                "Gorou T9 Skill multiplier");
        assertClose(648.0 * 1.56,
                skill.action.getAdditiveBaseDmgBonus(),
                "Gorou A4 Skill live DEF addition");
        assertEquals(ActionType.SKILL, skill.action.getActionType(),
                "Gorou Skill category");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Gorou Skill applies every hit");
        assertEquals(ICDTag.None, skill.action.getICDTag(),
                "Gorou Skill independent ICD metadata");
        assertClose(1.0, skill.action.getGaugeUnits(),
                "Gorou Skill 1U gauge");
        assertTrue(!skill.action.hasStatSnapshot(),
                "Gorou Skill reads live impact stats");
        assertClose(0.0,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        51.0 * FRAME - EPSILON),
                "Gorou field waits 17 frames after Skill hit");
        advanceTo(simulator, 51.0 * FRAME);
        assertClose(350.472,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        simulator.getCurrentTime()),
                "Gorou field first Skill tick");
        assertClose(634.0 * FRAME, gorou.getFieldEndTime(),
                "Gorou Skill field exact end");
        advanceTo(simulator, 134.0 * FRAME);
        assertEquals(1, particles.size(), "Gorou Skill particle event");
        assertClose(2.0, particles.get(0).count,
                "Gorou Skill produces two particles");
        assertClose(134.0 * FRAME, particles.get(0).time,
                "Gorou particle travel timing");
        advanceTo(simulator, 747.0 * FRAME - EPSILON);
        assertClose(350.472,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        simulator.getCurrentTime()),
                "Gorou final Skill update lingers two seconds");
        advanceTo(simulator, 747.0 * FRAME + EPSILON);
        assertClose(0.0,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        simulator.getCurrentTime()),
                "Gorou Skill linger expires exactly");
        assertTrue(!gorou.isFieldActive(634.0 * FRAME),
                "Gorou Skill field is half-open");
    }

    private static void testGeoCountAndC3C6Tiers() {
        assertFieldTier(1, 412.32, 0.0, 0.10);
        assertFieldTier(2, 412.32, 0.0, 0.20);
        assertFieldTier(3, 412.32, 0.15, 0.40);

        Gorou c3 = new Gorou(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureGorouActions(c3Sim);
        perform(c3Sim, CharacterActionKey.SKILL);
        advanceTo(c3Sim, 51.0 * FRAME);
        assertClose(2.144, c3Records.get(0).action.getDamagePercent(),
                "Gorou C3 Skill multiplier");
        assertClose(412.32,
                applicableStat(c3Sim, c3, StatType.DEF_FLAT,
                        c3Sim.getCurrentTime()),
                "Gorou C3 field DEF");
    }

    private static void assertFieldTier(
            int geoCount,
            double expectedDef,
            double expectedGeo,
            double expectedC6) {
        Gorou gorou = new Gorou(null, null, 6);
        List<Character> party = new ArrayList<>();
        party.add(gorou);
        if (geoCount >= 2) {
            party.add(new TestCharacter(CharacterId.NOELLE, Element.GEO));
        }
        if (geoCount >= 3) {
            party.add(new TestCharacter(CharacterId.ALBEDO, Element.GEO));
        }
        CombatSimulator simulator = simulatorWith(
                party.toArray(new Character[0]));
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 51.0 * FRAME);
        assertClose(expectedDef,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        simulator.getCurrentTime()),
                "Gorou field DEF at Geo count " + geoCount);
        assertClose(expectedGeo,
                applicableStat(simulator, gorou, StatType.GEO_DMG_BONUS,
                        simulator.getCurrentTime()) - 0.24,
                "Gorou field Geo bonus at Geo count " + geoCount);
        assertClose(expectedC6,
                gorou.getC6GeoCritDmg(34.0 * FRAME),
                "Gorou C6 tier at Geo count " + geoCount);
        assertClose(expectedC6,
                gorou.getC6GeoCritDmg(754.0 * FRAME - EPSILON),
                "Gorou C6 survives before expiry " + geoCount);
        assertClose(0.0,
                gorou.getC6GeoCritDmg(754.0 * FRAME),
                "Gorou C6 expires exactly " + geoCount);
    }

    private static void testC6DoesNotSnapshot() {
        Gorou gorou = new Gorou(null, null, 6);
        CombatSimulator simulator = simulatorWith(gorou);
        List<ActionRecord> records = captureGorouActions(simulator);
        StatsContainer beforeC6 = gorou.getEffectiveStats(0.0);
        perform(simulator, CharacterActionKey.SKILL);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.GOROU,
                snapshotProbe(
                        "Gorou C6 snapshot probe active",
                        Element.GEO,
                        beforeC6));
        advanceTo(simulator, 754.0 * FRAME + EPSILON);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.GOROU,
                snapshotProbe(
                        "Gorou C6 snapshot probe expired",
                        Element.GEO,
                        beforeC6));
        List<ActionRecord> probes = named(
                records, "Gorou C6 snapshot probe");
        double expectedRatio = (1.0 + 0.05 * 0.60)
                / (1.0 + 0.05 * 0.50);
        assertClose(expectedRatio,
                probes.get(0).damage / probes.get(1).damage,
                "Gorou C6 resolves after stored action snapshots");
    }

    private static void testBurstA1CollapseAndDynamicStats() {
        Gorou gorou = new Gorou(null, null, 0);
        CombatSimulator simulator = simulatorWith(gorou);
        List<ActionRecord> records = captureGorouActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord initial = named(
                records, "Juuga: Forward Unto Victory").get(0);
        assertClose(31.0 * FRAME, initial.time,
                "Gorou Burst initial hitmark");
        assertClose(1.669672, initial.action.getDamagePercent(),
                "Gorou T9 Burst multiplier");
        assertClose(648.0 * 1.25 * 0.156,
                initial.action.getAdditiveBaseDmgBonus(),
                "Gorou Burst initial A4 benefits from A1");
        assertEquals(ICDType.None, initial.action.getICDType(),
                "Gorou Burst initial no ICD");
        assertEquals(ICDTag.None, initial.action.getICDTag(),
                "Gorou Burst initial separate metadata");
        assertClose(1.0, initial.action.getGaugeUnits(),
                "Gorou Burst initial 1U");
        assertClose(0.0, gorou.getCurrentEnergy(),
                "Gorou Burst Energy spent at frame 7");
        assertClose(56.0 * FRAME, simulator.getCurrentTime(),
                "Gorou Burst animation duration");
        assertClose(20.0 - 56.0 * FRAME,
                gorou.getBurstCDRemaining(simulator.getCurrentTime()),
                "Gorou Burst cooldown starts at cast");
        assertClose(0.25,
                applicableStat(simulator, gorou, StatType.DEF_PERCENT,
                        31.0 * FRAME),
                "Gorou A1 starts on hitmark");
        assertClose(0.0,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        48.0 * FRAME - EPSILON),
                "Gorou Burst field waits 17 frames");
        assertClose(350.472,
                applicableStat(simulator, gorou, StatType.DEF_FLAT,
                        48.0 * FRAME),
                "Gorou Burst first field tick");

        advanceTo(simulator, 121.0 * FRAME);
        List<ActionRecord> collapses = named(records, "Crystal Collapse");
        assertEquals(1, collapses.size(),
                "Gorou first Crystal Collapse");
        ActionRecord first = collapses.get(0);
        assertClose(121.0 * FRAME, first.time,
                "Gorou first Collapse cadence");
        assertClose(1.0421, first.action.getDamagePercent(),
                "Gorou T9 Collapse multiplier");
        assertEquals(ICDType.Standard, first.action.getICDType(),
                "Gorou Collapse standard ICD");
        assertEquals(ICDTag.ElementalBurst, first.action.getICDTag(),
                "Gorou Collapse shared Burst ICD");
        assertTrue(!first.action.hasStatSnapshot(),
                "Gorou Collapse is dynamic");
        gorou.addBuff(new TestBuff(
                "Gorou dynamic DEF probe",
                simulator.getCurrentTime(),
                10.0,
                StatType.DEF_PERCENT,
                1.0));
        advanceTo(simulator, 211.0 * FRAME);
        collapses = named(records, "Crystal Collapse");
        assertTrue(collapses.get(1).action.getAdditiveBaseDmgBonus()
                        > first.action.getAdditiveBaseDmgBonus(),
                "Gorou later Collapse reads live DEF");
        advanceTo(simulator, 571.0 * FRAME);
        collapses = named(records, "Crystal Collapse");
        assertEquals(5, collapses.size(),
                "Gorou unextended Burst creates five Collapses");
        double[] frames = { 121, 211, 301, 391, 481 };
        for (int index = 0; index < frames.length; index++) {
            assertClose(frames[index] * FRAME,
                    collapses.get(index).time,
                    "Gorou Collapse cadence " + index);
        }
        assertTrue(!gorou.isFieldActive(571.0 * FRAME),
                "Gorou Burst field closes exactly");
        assertClose(0.25,
                applicableStat(simulator, gorou, StatType.DEF_PERCENT,
                        751.0 * FRAME - EPSILON),
                "Gorou A1 survives immediately before 12 seconds");
        assertClose(0.0,
                applicableStat(simulator, gorou, StatType.DEF_PERCENT,
                        751.0 * FRAME),
                "Gorou A1 expires exactly");

        Gorou c5 = new Gorou(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureGorouActions(c5Sim);
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, 121.0 * FRAME);
        assertClose(1.96432,
                named(c5Records, "Juuga").get(0)
                        .action.getDamagePercent(),
                "Gorou C5 Burst initial multiplier");
        assertClose(1.226,
                named(c5Records, "Crystal Collapse").get(0)
                        .action.getDamagePercent(),
                "Gorou C5 Collapse multiplier");
    }

    private static void testC1AndC2Boundaries() {
        Gorou gorou = new Gorou(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(gorou, ally);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 accepts first shard pickup");
        assertTrue(!gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 rejects before 0.1 seconds");
        double firstExtensionEnd = 631.0 * FRAME;
        assertClose(firstExtensionEnd, gorou.getFieldEndTime(),
                "Gorou C2 adds one second");
        simulator.advanceTime(6.0 * FRAME - EPSILON);
        assertTrue(!gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 rejects immediately before gate");
        simulator.advanceTime(EPSILON);
        assertTrue(gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 accepts exact 0.1-second gate");
        simulator.advanceTime(6.0 * FRAME);
        assertTrue(gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 accepts third extension");
        simulator.advanceTime(6.0 * FRAME);
        assertTrue(!gorou.notifyCrystallizeShardObtained(simulator),
                "Gorou C2 caps at three seconds");
        assertEquals(3, gorou.getC2ExtensionCount(),
                "Gorou C2 extension count cap");
        assertClose(751.0 * FRAME, gorou.getFieldEndTime(),
                "Gorou C2 maximum field end");

        Gorou c1 = new Gorou(null, null, 2);
        TestCharacter c1Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c1Sim = simulatorWith(c1, c1Ally);
        perform(c1Sim, CharacterActionKey.SKILL);
        perform(c1Sim, CharacterActionKey.BURST);
        c1.notifyCrystallizeShardObtained(c1Sim);
        c1Sim.advanceTime(6.0 * FRAME);
        c1.notifyCrystallizeShardObtained(c1Sim);
        c1Sim.advanceTime(6.0 * FRAME);
        c1.notifyCrystallizeShardObtained(c1Sim);
        double firstC1Time = c1Sim.getCurrentTime();
        c1Sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, geoProbe("Gorou C1 first"));
        advanceTo(c1Sim, firstC1Time + 500.0 * FRAME);
        perform(c1Sim, CharacterActionKey.SKILL);
        double refreshedCooldownEnd = c1.getSkillCooldownEndTime();
        advanceTo(c1Sim, firstC1Time + 600.0 * FRAME - EPSILON);
        c1Sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, geoProbe("Gorou C1 early"));
        assertClose(refreshedCooldownEnd, c1.getSkillCooldownEndTime(),
                "Gorou C1 rejects before ten seconds");
        advanceTo(c1Sim, firstC1Time + 600.0 * FRAME);
        c1Sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, geoProbe("Gorou C1 exact"));
        assertClose(refreshedCooldownEnd - 2.0,
                c1.getSkillCooldownEndTime(),
                "Gorou C1 accepts exact ten-second gate");
    }

    private static void testSwitchPersistenceReplacementAndLunarC2() {
        Gorou gorou = new Gorou(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(gorou, ally);
        List<ActionRecord> records = captureGorouActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        long skillFieldEndFrames = Math.round(
                gorou.getFieldEndTime() / FRAME);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(gorou.getFieldEndTime()
                        < skillFieldEndFrames * FRAME,
                "Gorou Burst replaces the existing Skill field");
        assertClose((648.0 * 1.25 + 350.472) * 0.156,
                named(records, "Juuga").get(0)
                        .action.getAdditiveBaseDmgBonus(),
                "replaced Skill field buff lingers through Burst hitmark");
        simulator.switchCharacter(CharacterId.NOELLE);
        advanceTo(simulator, 113.0 * FRAME);
        assertClose(350.472,
                applicableStat(simulator, ally, StatType.DEF_FLAT,
                        simulator.getCurrentTime()),
                "Gorou Burst field follows switched active character");

        int before = gorou.getC2ExtensionCount();
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE),
                ally);
        assertEquals(before + 1, gorou.getC2ExtensionCount(),
                "active Lunar-Crystallize extends Gorou C2");
        simulator.notifyReaction(ReactionResult.none(), ally);
        assertEquals(before + 1, gorou.getC2ExtensionCount(),
                "non-Crystallize reaction cannot extend Gorou C2");
    }

    private static void testRepeatedRestoreAndStaleGenerations() {
        Gorou gorou = new Gorou(null, null, 2);
        CombatSimulator simulator = simulatorWith(gorou);
        List<ActionRecord> records = captureGorouActions(simulator);
        List<ParticleRecord> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 3.0);
        int firstParticles = particles.size();
        int firstActions = records.size();
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 3.0);
        assertEquals(firstParticles * 2, particles.size(),
                "Gorou restore reconstructs one particle branch");
        assertEquals(firstActions, records.size(),
                "Gorou restore does not replay resolved Skill hit");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 3.0);
        assertEquals(firstParticles * 3, particles.size(),
                "Gorou repeated restore stays exact-once");

        Gorou burstGorou = new Gorou(null, null, 2);
        CombatSimulator burstSim = simulatorWith(burstGorou);
        List<ActionRecord> burstRecords = captureGorouActions(burstSim);
        perform(burstSim, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSim.saveSnapshot();
        advanceTo(burstSim, 4.0);
        int branchCollapses = named(
                burstRecords, "Crystal Collapse").size();
        burstSim.restoreSnapshot(burstSnapshot);
        advanceTo(burstSim, 4.0);
        assertEquals(branchCollapses * 2,
                named(burstRecords, "Crystal Collapse").size(),
                "Gorou restore reconstructs Collapse branch once");

        Gorou staleSkill = new Gorou(null, null, 0);
        CombatSimulator staleSkillSim = simulatorWith(staleSkill);
        List<ParticleRecord> staleParticles = captureGeoParticles(
                staleSkillSim);
        staleSkill.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                staleSkillSim);
        staleSkill.onAction(
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                staleSkillSim);
        advanceTo(staleSkillSim, 4.0);
        assertEquals(1, staleParticles.size(),
                "Gorou stale Skill particle generation is suppressed");

        Gorou staleBurst = new Gorou(null, null, 0);
        CombatSimulator staleBurstSim = simulatorWith(staleBurst);
        List<ActionRecord> staleBurstRecords = captureGorouActions(
                staleBurstSim);
        staleBurst.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                staleBurstSim);
        staleBurst.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                staleBurstSim);
        advanceTo(staleBurstSim, 4.0);
        List<ActionRecord> staleCollapses = named(
                staleBurstRecords, "Crystal Collapse");
        assertEquals(1, staleCollapses.size(),
                "Gorou stale Burst Collapse generation is suppressed");
        assertClose((56.0 + 31.0 + 90.0) * FRAME,
                staleCollapses.get(0).time,
                "Gorou surviving Collapse belongs to latest Burst");
    }

    private static void testInvalidInputsCooldownAndEnergyRejection() {
        assertThrows(IllegalArgumentException.class,
                () -> new Gorou(null, null, -1),
                "Gorou rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Gorou(null, null, 7),
                "Gorou rejects constellation above six");

        Gorou unsupported = new Gorou(null, null, 0);
        CombatSimulator unsupportedSim = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSim),
                "Gorou rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.CHARGE),
                "Gorou rejects unsupported Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.DASH),
                "Gorou rejects unsupported Dash");

        Gorou insufficient = new Gorou(null, null, 0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureGorouActions(
                insufficientSim);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSim, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Gorou insufficient Energy rejects Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Gorou records rejected Burst cost");

        Gorou cooldown = new Gorou(null, null, 0);
        CombatSimulator cooldownSim = simulatorWith(cooldown);
        perform(cooldownSim, CharacterActionKey.SKILL);
        perform(cooldownSim, CharacterActionKey.SKILL);
        assertClose(679.0 * FRAME, cooldownSim.getCurrentTime(),
                "Gorou serializes Skill at cooldown boundary");

        Gorou reusable = new Gorou(null, null, 0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Gorou rejects cross-simulator reuse");
        Gorou stateOwner = new Gorou(null, null, 0);
        Gorou foreign = new Gorou(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> stateOwner.restoreCharacterState(
                        foreign.captureCharacterState(),
                        new CombatSimulator()),
                "Gorou rejects another Gorou's state");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
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
                CharacterId.GOROU, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureGorouActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.GOROU) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureGeoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
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

    private static double applicableStat(
            CombatSimulator simulator,
            Character character,
            StatType stat,
            double time) {
        StatsContainer stats = character.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats.get(stat);
    }

    private static AttackAction geoProbe(String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.GEO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static AttackAction snapshotProbe(
            String name,
            Element element,
            StatsContainer snapshot) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setStatSnapshot(snapshot);
        return action;
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
            assertTrue(lines.get(index).startsWith("Gorou,"),
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": unexpected " + throwable,
                    throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
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

    private static final class TestBuff extends Buff {
        private final StatType stat;
        private final double amount;

        private TestBuff(
                String name,
                double startTime,
                double duration,
                StatType stat,
                double amount) {
            super(name, duration, startTime);
            this.stat = stat;
            this.amount = amount;
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(stat, amount);
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
        }

        @Override
        public double getEnergyCost() {
            return 100.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
