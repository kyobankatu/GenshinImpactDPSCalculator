package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Tighnari;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Tighnari's fixed-target Wreath Arrow kit. */
public final class TighnariRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private TighnariRegressionTest() {
    }

    /** Runs data, timing, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testNormalString();
        testWreathClusterSnapshotsAndPassives();
        testSkillSuffusionParticlesAndC2();
        testBurstC3C4AndOrdering();
        testC5C6AndA4Cap();
        testSnapshotRestorePendingProjectiles();
        testInvalidInputsEnergyAndIsolation();
        System.out.println("TighnariRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Tighnari tighnari = new Tighnari(null, null, 6);
        assertEquals(CharacterId.TIGHNARI, tighnari.getCharacterId(),
                "Tighnari typed identity");
        assertEquals(CharacterId.TIGHNARI,
                CharacterId.fromName("Tighnari"),
                "Tighnari name lookup");
        assertEquals(CharacterId.TIGHNARI,
                CharacterId.fromNumericId(42),
                "Tighnari numeric lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.TIGHNARI.getRegion(),
                "Tighnari region");
        assertEquals(Element.DENDRO, tighnari.getElement(),
                "Tighnari element");
        assertClose(10850.0,
                tighnari.getBaseStats().get(StatType.BASE_HP),
                "Tighnari base HP");
        assertClose(268.0,
                tighnari.getBaseStats().get(StatType.BASE_ATK),
                "Tighnari base ATK");
        assertClose(630.0,
                tighnari.getBaseStats().get(StatType.BASE_DEF),
                "Tighnari base DEF");
        assertClose(0.288,
                tighnari.getBaseStats().get(StatType.DENDRO_DMG_BONUS),
                "Tighnari ascension Dendro bonus");
        assertClose(40.0, tighnari.getEnergyCost(),
                "Tighnari Energy cost");
        assertClose(12.0, tighnari.getSkillCD(),
                "Tighnari Skill cooldown");
        assertClose(12.0, tighnari.getBurstCD(),
                "Tighnari Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.TIGHNARI,
                    new Tighnari(null, null, constellation)
                            .getCharacterId(),
                    "Tighnari explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Tighnari/Tighnari_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Tighnari/Tighnari_Multipliers.csv"), 34);
        assertCsvValue("Base ATK", 268.0);
        assertCsvValue("Wreath Arrow", 1.4824);
        assertCsvValue("Clusterbloom Arrow", 0.6562);
        assertCsvValue("Tanglevine Shaft C3", 1.1124);
        assertCsvValue("C4 Reaction Additional Elemental Mastery", 60.0);
    }

    private static void testNormalString() {
        Tighnari tighnari = new Tighnari(null, null, 0);
        CombatSimulator simulator = simulatorWith(tighnari);
        List<ActionRecord> records = captureActions(simulator);
        int[][] releases = { { 14 }, { 12 }, { 13, 25 }, { 28 } };
        int[] durations = { 26, 23, 37, 68 };
        double[][] multipliers = {
            { 0.82002 }, { 0.77104 },
            { 0.48585, 0.48585 }, { 1.26084 }
        };
        int recordIndex = 0;
        for (int step = 0; step < releases.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < releases[step].length; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(castTime
                                + (releases[step][hit] + 10.0) * FRAME,
                        record.time, "Tighnari Normal impact");
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Tighnari Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Tighnari Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Tighnari Normal category");
            }
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Tighnari Normal recovery");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals("Khanda Barrier-Buster N1",
                records.get(recordIndex).action.getName(),
                "Tighnari Normal string wraps after N4");

        Tighnari reset = new Tighnari(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator resetSimulator = simulatorWith(reset, ally);
        List<ActionRecord> resetRecords = captureActions(resetSimulator);
        perform(resetSimulator, CharacterActionKey.NORMAL);
        resetSimulator.switchCharacter(CharacterId.COLLEI);
        resetSimulator.switchCharacter(CharacterId.TIGHNARI);
        perform(resetSimulator, CharacterActionKey.NORMAL);
        assertEquals("Khanda Barrier-Buster N1",
                resetRecords.get(1).action.getName(),
                "Tighnari switch resets Normal string");
    }

    private static void testWreathClusterSnapshotsAndPassives() {
        Tighnari c0 = new Tighnari(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        double[] a1Observations = { -1.0, -1.0 };
        observeStat(simulator, c0, 175.0 * FRAME,
                StatType.ELEMENTAL_MASTERY, a1Observations, 0);
        observeStat(simulator, c0, 176.0 * FRAME + EPSILON,
                StatType.ELEMENTAL_MASTERY, a1Observations, 1);
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(183.0 * FRAME, simulator.getCurrentTime(),
                "Tighnari normal Wreath recovery");
        assertEquals(0, records.size(),
                "Tighnari Wreath remains in flight after recovery");
        advanceTo(simulator, 186.0 * FRAME);
        ActionRecord wreath = named(records,
                "Khanda Barrier-Buster Wreath Arrow").get(0);
        assertClose(185.0 * FRAME, wreath.time,
                "Tighnari Wreath impact");
        assertClose(1.4824, wreath.action.getDamagePercent(),
                "Tighnari Wreath multiplier");
        assertEquals(ICDType.None, wreath.action.getICDType(),
                "Tighnari Wreath has no ICD");
        assertClose(1.0, wreath.action.getGaugeUnits(),
                "Tighnari Wreath gauge");
        assertClose(0.0, wreath.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Tighnari first Wreath snapshots before A1");
        assertClose(50.0, effectiveStat(
                simulator, c0, StatType.ELEMENTAL_MASTERY),
                "Tighnari A1 starts when Wreath is fired");
        assertClose(0.0, a1Observations[0],
                "Tighnari A1 is inactive on the release frame");
        assertClose(50.0, a1Observations[1],
                "Tighnari A1 activates one frame after release");
        advanceTo(simulator, 221.0 * FRAME);
        List<ActionRecord> clusters = named(records,
                "Khanda Barrier-Buster Clusterbloom Arrow");
        assertEquals(4, clusters.size(),
                "Tighnari Wreath creates four Clusterblooms");
        for (ActionRecord cluster : clusters) {
            assertClose(220.0 * FRAME, cluster.time,
                    "Tighnari Clusterbloom impact");
            assertClose(0.6562, cluster.action.getDamagePercent(),
                    "Tighnari Clusterbloom multiplier");
            assertEquals(ICDType.TighnariClusterbloom,
                    cluster.action.getICDType(),
                    "Tighnari Clusterbloom dedicated ICD");
            assertEquals(ICDTag.Tighnari_Clusterbloom,
                    cluster.action.getICDTag(),
                    "Tighnari Clusterbloom shared ICD tag");
            assertClose(50.0, cluster.action.getStatSnapshot().get(
                    StatType.ELEMENTAL_MASTERY),
                    "Tighnari Clusterbloom snapshots A1 at creation");
            assertClose(0.03, cluster.action.getExtraBonuses().getOrDefault(
                    StatType.CHARGED_ATTACK_DMG_BONUS, 0.0),
                    "Tighnari A4 reads Clusterbloom creation EM");
        }
        advanceTo(simulator, 416.0 * FRAME + EPSILON);
        assertClose(0.0, effectiveStat(
                simulator, c0, StatType.ELEMENTAL_MASTERY),
                "Tighnari A1 expires four seconds after release");

        Tighnari c1 = new Tighnari(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.CHARGE);
        advanceTo(c1Simulator, 221.0 * FRAME);
        for (ActionRecord record : c1Records) {
            assertClose(0.15, record.action.getExtraBonuses().getOrDefault(
                    StatType.CRIT_RATE, 0.0),
                    "Tighnari C1 applies to every Charged component");
        }

        Tighnari icd = new Tighnari(null, null, 0);
        CombatSimulator icdSimulator = simulatorWith(icd);
        int[] clusterSpreads = { 0 };
        icdSimulator.addReactionListener((result, source, time, active) -> {
            if (result.getKind()
                    == mechanics.reaction.ReactionResult.Kind.SPREAD
                    && Math.abs(time - 220.0 * FRAME) < EPSILON) {
                clusterSpreads[0]++;
            }
        });
        icdSimulator.getEnemy().setAura(Element.ELECTRO, 2.0);
        perform(icdSimulator, CharacterActionKey.CHARGE);
        advanceTo(icdSimulator, 221.0 * FRAME);
        assertEquals(1, clusterSpreads[0],
                "Tighnari four Clusterblooms apply Dendro only on hit one");
    }

    private static void testSkillSuffusionParticlesAndC2() {
        int[] drawCalls = { 0 };
        Tighnari lowDraw = new Tighnari(
                null, null,
                mechanics.data.TalentDataManager.getInstance(),
                2, () -> {
                    drawCalls[0]++;
                    return drawCalls[0] == 1 ? 0.0 : 0.999;
                });
        CombatSimulator simulator = simulatorWith(lowDraw);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureDendroParticles(simulator);
        addStatBuffAt(simulator, lowDraw, 14.0 * FRAME,
                "Tighnari pre-snapshot EM", StatType.ELEMENTAL_MASTERY,
                100.0);
        addStatBuffAt(simulator, lowDraw, 16.0 * FRAME,
                "Tighnari post-snapshot EM", StatType.ELEMENTAL_MASTERY,
                1000.0);
        performSkill(simulator);
        assertClose(30.0 * FRAME, simulator.getCurrentTime(),
                "Tighnari Skill recovery");
        ActionRecord skill = named(records, "Vijnana-Phala Mine").get(0);
        assertClose(20.0 * FRAME, skill.time,
                "Tighnari Skill impact");
        assertClose(2.5432, skill.action.getDamagePercent(),
                "Tighnari Skill multiplier");
        assertEquals(3, lowDraw.getSuffusionCount(
                simulator.getCurrentTime()),
                "Tighnari Skill grants three accelerated Wreaths");
        assertClose(733.0 * FRAME,
                lowDraw.getSuffusionExpirationTime(),
                "Tighnari Suffusion starts at frame thirteen");
        assertClose(500.0 * FRAME,
                lowDraw.getSkillFieldExpirationTime(),
                "Tighnari Skill field starts on frame twenty");
        assertClose(860.0 * FRAME,
                lowDraw.getC2ExpirationTime(),
                "Tighnari C2 lingers six seconds after the field");
        assertClose(0.288, skill.action.getStatSnapshot().get(
                StatType.DENDRO_DMG_BONUS),
                "Tighnari Skill frame-fifteen snapshot excludes C2");
        assertClose(100.0, skill.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Tighnari Skill snapshots on frame fifteen");
        SimulatorSnapshot particleSnapshot = simulator.saveSnapshot();
        assertClose(0.20, effectiveStat(
                simulator, lowDraw, StatType.DENDRO_DMG_BONUS) - 0.288,
                "Tighnari C2 fixed target Dendro bonus");
        advanceTo(simulator, 121.0 * FRAME);
        assertEquals(1, particles.size(),
                "Tighnari Skill emits one particle event");
        assertClose(4.0, particles.get(0).count,
                "Tighnari low draw emits four particles");
        assertClose(120.0 * FRAME, particles.get(0).time,
                "Tighnari Skill particle travel time");
        assertEquals(1, drawCalls[0],
                "Tighnari Skill selects particle count once");
        simulator.restoreSnapshot(particleSnapshot);
        simulator.restoreSnapshot(particleSnapshot);
        particles.clear();
        advanceTo(simulator, 121.0 * FRAME);
        assertClose(4.0, particles.get(0).count,
                "Tighnari restored particle event keeps selected count");
        assertEquals(1, drawCalls[0],
                "Tighnari restore does not redraw particles");

        double firstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(firstCast + 41.0 * FRAME,
                simulator.getCurrentTime(),
                "Tighnari Suffusion accelerates Wreath recovery");
        assertEquals(2, lowDraw.getSuffusionCount(
                simulator.getCurrentTime()),
                "Tighnari accelerated cast consumes one quota at cast");
        perform(simulator, CharacterActionKey.CHARGE);
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(0, lowDraw.getSuffusionCount(
                simulator.getCurrentTime()),
                "Tighnari third accelerated cast exhausts quota");
        double fourthCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(fourthCast + 183.0 * FRAME,
                simulator.getCurrentTime(),
                "Tighnari fourth Wreath uses normal charge time");
        advanceTo(simulator, 860.0 * FRAME + EPSILON);
        assertClose(0.0, effectiveStat(
                simulator, lowDraw, StatType.DENDRO_DMG_BONUS) - 0.288,
                "Tighnari C2 expires after field linger");

        Tighnari highDraw = new Tighnari(
                null, null,
                mechanics.data.TalentDataManager.getInstance(),
                0, () -> 0.999);
        CombatSimulator highSimulator = simulatorWith(highDraw);
        List<ParticleRecord> highParticles = captureDendroParticles(
                highSimulator);
        performSkill(highSimulator);
        advanceTo(highSimulator, 121.0 * FRAME);
        assertClose(3.0, highParticles.get(0).count,
                "Tighnari high draw emits three particles");
    }

    private static void testBurstC3C4AndOrdering() {
        Tighnari c0 = new Tighnari(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(118.0 * FRAME, simulator.getCurrentTime(),
                "Tighnari Burst recovery");
        List<ActionRecord> primaries = named(records,
                "Fashioner's Tanglevine Shaft Primary");
        assertEquals(2, primaries.size(),
                "Tighnari Burst resolves first two primaries by recovery");
        assertClose(112.0 * FRAME, primaries.get(0).time,
                "Tighnari Burst first primary");
        assertClose(0.94554, primaries.get(0).action.getDamagePercent(),
                "Tighnari Burst primary multiplier");
        assertEquals(ICDType.Standard,
                primaries.get(0).action.getICDType(),
                "Tighnari Burst shared Standard ICD");
        assertEquals(ICDTag.ElementalBurst,
                primaries.get(0).action.getICDTag(),
                "Tighnari Burst ICD tag");
        advanceTo(simulator, 176.0 * FRAME);
        primaries = named(records,
                "Fashioner's Tanglevine Shaft Primary");
        List<ActionRecord> secondaries = named(records,
                "Fashioner's Tanglevine Shaft Secondary");
        assertEquals(6, primaries.size(),
                "Tighnari Burst resolves six primaries");
        assertEquals(6, secondaries.size(),
                "Tighnari Burst resolves six secondaries");
        assertClose(128.0 * FRAME, primaries.get(5).time,
                "Tighnari Burst final primary");
        assertClose(175.0 * FRAME, secondaries.get(5).time,
                "Tighnari Burst final secondary");
        assertClose(1.15566,
                secondaries.get(0).action.getDamagePercent(),
                "Tighnari Burst secondary multiplier");

        Tighnari burstIcd = new Tighnari(null, null, 0);
        CombatSimulator burstIcdSimulator = simulatorWith(burstIcd);
        int[] burstReactions = { 0 };
        burstIcdSimulator.addReactionListener(
                (result, source, time, active) -> {
                    if (result.getKind()
                                    == mechanics.reaction.ReactionResult.Kind.QUICKEN
                            || result.getKind()
                                    == mechanics.reaction.ReactionResult.Kind.SPREAD) {
                        burstReactions[0]++;
                    }
                });
        burstIcdSimulator.getEnemy().setAura(Element.ELECTRO, 2.0);
        perform(burstIcdSimulator, CharacterActionKey.BURST);
        advanceTo(burstIcdSimulator, 176.0 * FRAME);
        assertEquals(5, burstReactions[0],
                "Tighnari shared Burst ICD fixes Quicken/Spread notifications");

        Tighnari dynamic = new Tighnari(null, null, 0);
        CombatSimulator dynamicSimulator = simulatorWith(dynamic);
        List<ActionRecord> dynamicRecords = captureActions(dynamicSimulator);
        addStatBuffAt(dynamicSimulator, dynamic, 114.0 * FRAME,
                "Tighnari mid-Burst EM", StatType.ELEMENTAL_MASTERY,
                100.0);
        perform(dynamicSimulator, CharacterActionKey.BURST);
        advanceTo(dynamicSimulator, 176.0 * FRAME);
        List<ActionRecord> dynamicSecondaries = named(dynamicRecords,
                "Fashioner's Tanglevine Shaft Secondary");
        assertClose(0.0, dynamicSecondaries.get(0)
                        .action.getStatSnapshot().get(
                                StatType.ELEMENTAL_MASTERY),
                "Tighnari first secondary snapshots at first primary");
        assertClose(100.0, dynamicSecondaries.get(1)
                        .action.getStatSnapshot().get(
                                StatType.ELEMENTAL_MASTERY),
                "Tighnari later secondary snapshots its own primary time");

        Tighnari c3 = new Tighnari(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        advanceTo(c3Simulator, 176.0 * FRAME);
        assertClose(1.1124, named(c3Records,
                "Fashioner's Tanglevine Shaft Primary").get(0)
                        .action.getDamagePercent(),
                "Tighnari C3 primary multiplier");
        assertClose(1.3596, named(c3Records,
                "Fashioner's Tanglevine Shaft Secondary").get(0)
                        .action.getDamagePercent(),
                "Tighnari C3 secondary multiplier");

        Tighnari c4 = new Tighnari(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator c4Simulator = simulatorWith(c4, ally);
        c4Simulator.getEnemy().setAura(Element.ELECTRO, 2.0);
        perform(c4Simulator, CharacterActionKey.BURST);
        assertClose(120.0, effectiveStat(
                c4Simulator, ally, StatType.ELEMENTAL_MASTERY),
                "Tighnari C4 Burst Quicken upgrades party EM");
        assertTrue(c4.getC4ExpirationTime()
                        >= 112.0 * FRAME + 8.0 - EPSILON,
                "Tighnari C4 reaction refreshes the eight-second window");

        Tighnari c4Base = new Tighnari(null, null, 4);
        TestCharacter baseAlly = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator c4BaseSimulator = simulatorWith(c4Base, baseAlly);
        perform(c4BaseSimulator, CharacterActionKey.BURST);
        assertClose(8.0, c4Base.getC4ExpirationTime(),
                "Tighnari C4 base window starts at Burst input");
        assertClose(60.0, effectiveStat(
                c4BaseSimulator, baseAlly, StatType.ELEMENTAL_MASTERY),
                "Tighnari C4 base Burst grants sixty party EM");
    }

    private static void testC5C6AndA4Cap() {
        Tighnari c5 = new Tighnari(
                null, null,
                mechanics.data.TalentDataManager.getInstance(),
                5, () -> 0.0);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        performSkill(c5Simulator);
        assertClose(2.992, named(c5Records, "Vijnana-Phala Mine").get(0)
                        .action.getDamagePercent(),
                "Tighnari C5 Skill multiplier");

        Tighnari c6 = new Tighnari(
                null, null,
                mechanics.data.TalentDataManager.getInstance(),
                6, () -> 0.0);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        performSkill(c6Simulator);
        double castTime = c6Simulator.getCurrentTime();
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertClose(castTime + 8.0 * FRAME,
                c6Simulator.getCurrentTime(),
                "Tighnari C6 Skill-enhanced Wreath recovery");
        advanceTo(c6Simulator, castTime + 76.0 * FRAME);
        List<ActionRecord> c6Arrows = named(c6Records,
                "Khanda Barrier-Buster C6 Clusterbloom Arrow");
        assertEquals(1, c6Arrows.size(),
                "Tighnari C6 creates one additional arrow");
        assertClose(castTime + 45.0 * FRAME, c6Arrows.get(0).time,
                "Tighnari C6 additional arrow impact");
        assertClose(1.5, c6Arrows.get(0).action.getDamagePercent(),
                "Tighnari C6 additional arrow multiplier");
        assertEquals(ICDType.None, c6Arrows.get(0).action.getICDType(),
                "Tighnari C6 additional arrow has no ICD");

        Tighnari capped = new Tighnari(null, null, 0);
        capped.addBuff(new SimpleBuff(
                "Tighnari A4 cap probe",
                100.0,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 2000.0)));
        CombatSimulator cappedSimulator = simulatorWith(capped);
        List<ActionRecord> cappedRecords = captureActions(cappedSimulator);
        perform(cappedSimulator, CharacterActionKey.BURST);
        assertClose(0.60, named(cappedRecords,
                "Fashioner's Tanglevine Shaft Primary").get(0)
                        .action.getExtraBonuses().getOrDefault(
                                StatType.BURST_DMG_BONUS, 0.0),
                "Tighnari A4 caps at sixty percent");
    }

    private static void testSnapshotRestorePendingProjectiles() {
        Tighnari tighnari = new Tighnari(null, null, 0);
        CombatSimulator simulator = simulatorWith(tighnari);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot pending = simulator.saveSnapshot();
        records.clear();
        advanceTo(simulator, 221.0 * FRAME);
        int branchActions = records.size();
        assertEquals(5, branchActions,
                "Tighnari branch resolves Wreath and four Clusterblooms");
        simulator.restoreSnapshot(pending);
        simulator.restoreSnapshot(pending);
        records.clear();
        advanceTo(simulator, 221.0 * FRAME);
        assertEquals(branchActions, records.size(),
                "Tighnari repeated restore reconstructs projectiles once");
        assertClose(50.0, named(records,
                "Khanda Barrier-Buster Clusterbloom Arrow").get(0)
                        .action.getStatSnapshot().get(
                                StatType.ELEMENTAL_MASTERY),
                "Tighnari restored Clusterbloom retains creation snapshot");

        Tighnari burst = new Tighnari(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSimulator.saveSnapshot();
        burstRecords.clear();
        advanceTo(burstSimulator, 176.0 * FRAME);
        int branchBurstHits = burstRecords.size();
        assertEquals(10, branchBurstHits,
                "Tighnari branch resolves ten post-recovery Burst hits");
        burstSimulator.restoreSnapshot(burstSnapshot);
        burstSimulator.restoreSnapshot(burstSnapshot);
        burstRecords.clear();
        advanceTo(burstSimulator, 176.0 * FRAME);
        assertEquals(branchBurstHits, burstRecords.size(),
                "Tighnari repeated restore reconstructs pending Burst hits once");
    }

    private static void testInvalidInputsEnergyAndIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Tighnari(null, null, -1),
                "Tighnari rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Tighnari(null, null, 7),
                "Tighnari rejects constellation above six");
        assertThrows(IllegalArgumentException.class,
                () -> new Tighnari(
                        null, null,
                        mechanics.data.TalentDataManager.getInstance(),
                        0, null),
                "Tighnari rejects a null particle draw source");

        Tighnari invalid = new Tighnari(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Tighnari rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Tighnari rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> invalidSimulator.performAction(
                        CharacterId.TIGHNARI,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Tighnari rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Tighnari rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.PLUNGE),
                "Tighnari rejects excluded Plunge timing");

        Tighnari insufficient = new Tighnari(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Tighnari insufficient Energy rejects Burst");
        assertClose(40.0, insufficient.getMissedBurstCost(),
                "Tighnari records rejected Burst Energy");

        Tighnari badDraw = new Tighnari(
                null, null,
                mechanics.data.TalentDataManager.getInstance(),
                0, () -> 1.0);
        CombatSimulator badDrawSimulator = simulatorWith(badDraw);
        assertThrows(IllegalStateException.class,
                () -> performSkill(badDrawSimulator),
                "Tighnari rejects a particle draw outside [0, 1)");

        Tighnari reused = new Tighnari(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Tighnari rejects cross-simulator reuse");
        Tighnari owner = new Tighnari(null, null, 0);
        Tighnari foreign = new Tighnari(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Tighnari rejects another instance's state");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
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
                CharacterId.TIGHNARI, CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.TIGHNARI,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.TIGHNARI) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureDendroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
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

    private static void addStatBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            String name,
            StatType stat,
            double amount) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                character.addBuff(new SimpleBuff(
                        name,
                        100.0,
                        time,
                        stats -> stats.add(stat, amount)));
            }
        });
    }

    private static void observeStat(
            CombatSimulator simulator,
            Character character,
            double time,
            StatType stat,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = effectiveStat(
                        activeSimulator, character, stat);
            }
        });
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
            assertTrue(lines.get(index).startsWith("Tighnari,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Tighnari/Tighnari_Status.csv",
                "config/characters/Tighnari/Tighnari_Multipliers.csv"
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
        throw new AssertionError("Tighnari CSVs missing key " + key);
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
        @SuppressWarnings("unused")
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
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
