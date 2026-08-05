package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Faruzan;
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

/** Focused regression checks for Faruzan's fixed-target Anemo support slice. */
public final class FaruzanRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private FaruzanRegressionTest() {
    }

    /** Runs data, timing, support, gate, restore, and invalid-input checks. */
    public static void main(String[] args) throws Exception {
        testIdentityBaseDataAndConstellations();
        testNormalReleaseImpactDurationAndMultipliers();
        testFullyChargedAndManifestGale();
        testSkillCollapseParticlesAndC4();
        testBurstOrderingPulseWindowsAndStaleRecast();
        testA4CategoriesRatioGateAndExclusions();
        testConstellationTalentAndC6Gates();
        testSnapshotRestorePendingAndSupportState();
        testInvalidInputsEnergyTargetAndIsolation();
        System.out.println("FaruzanRegressionTest passed");
    }

    private static void testIdentityBaseDataAndConstellations()
            throws IOException {
        Faruzan faruzan = new Faruzan(null, null, 6);
        assertEquals(CharacterId.FARUZAN, faruzan.getCharacterId(),
                "Faruzan typed identity");
        assertEquals(CharacterId.FARUZAN, CharacterId.fromName("Faruzan"),
                "Faruzan name lookup");
        assertEquals(CharacterId.FARUZAN, CharacterId.fromNumericId(40),
                "Faruzan numeric lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.FARUZAN.getRegion(), "Faruzan region");
        assertEquals(Element.ANEMO, faruzan.getElement(),
                "Faruzan element");
        assertClose(9570.0,
                faruzan.getBaseStats().get(StatType.BASE_HP),
                "Faruzan base HP");
        assertClose(196.0,
                faruzan.getBaseStats().get(StatType.BASE_ATK),
                "Faruzan base ATK");
        assertClose(628.0,
                faruzan.getBaseStats().get(StatType.BASE_DEF),
                "Faruzan base DEF");
        assertClose(0.24,
                faruzan.getBaseStats().get(StatType.ATK_PERCENT),
                "Faruzan ascension ATK");
        assertClose(80.0, faruzan.getEnergyCost(),
                "Faruzan Energy cost");
        assertClose(6.0, faruzan.getSkillCD(),
                "Faruzan Skill cooldown");
        assertClose(20.0, faruzan.getBurstCD(),
                "Faruzan Burst cooldown");

        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.FARUZAN,
                    new Faruzan(null, null, constellation).getCharacterId(),
                    "Faruzan explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Faruzan/Faruzan_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Faruzan/Faruzan_Multipliers.csv"), 16);
    }

    private static void testNormalReleaseImpactDurationAndMultipliers() {
        Faruzan faruzan = new Faruzan(null, null, 0);
        CombatSimulator simulator = simulatorWith(faruzan);
        List<ActionRecord> records = captureActions(simulator);
        int[] releases = { 14, 10, 24, 29 };
        int[] durations = { 26, 21, 39, 86 };
        double[] multipliers = {
            0.821774, 0.775053, 0.976724, 1.297449
        };

        for (int step = 0; step < releases.length; step++) {
            double castTime = simulator.getCurrentTime();
            addStatBuffAt(
                    simulator,
                    faruzan,
                    castTime + (releases[step] - 1.0) * FRAME,
                    "Faruzan N" + (step + 1) + " pre-release",
                    StatType.ATK_PERCENT,
                    1.0);
            addStatBuffAt(
                    simulator,
                    faruzan,
                    castTime + (releases[step] + 1.0) * FRAME,
                    "Faruzan N" + (step + 1) + " post-release",
                    StatType.ATK_PERCENT,
                    10.0);
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertEquals("Parthian Shot N" + (step + 1),
                    record.action.getName(), "Faruzan Normal name");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Faruzan N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Faruzan Normal element");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Faruzan Normal category");
            assertClose(castTime + (releases[step] + 10.0) * FRAME,
                    record.time,
                    "Faruzan N" + (step + 1) + " impact");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Faruzan N" + (step + 1) + " duration");
            assertClose(0.24 + step * 11.0 + 1.0,
                    record.action.getStatSnapshot().get(
                            StatType.ATK_PERCENT),
                    "Faruzan N" + (step + 1)
                            + " snapshots at release");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals("Parthian Shot N1", records.get(4).action.getName(),
                "Faruzan Normal chain wraps after N4");

        TestCharacter ally = new TestCharacter(
                CharacterId.SAYU, Element.ANEMO);
        CombatSimulator resetSimulator = simulatorWith(
                new Faruzan(null, null, 0), ally);
        List<ActionRecord> resetRecords = captureActions(resetSimulator);
        perform(resetSimulator, CharacterActionKey.NORMAL);
        resetSimulator.switchCharacter(CharacterId.SAYU);
        resetSimulator.switchCharacter(CharacterId.FARUZAN);
        perform(resetSimulator, CharacterActionKey.NORMAL);
        assertEquals("Parthian Shot N1",
                resetRecords.get(1).action.getName(),
                "Faruzan switch-out resets Normal chain");
    }

    private static void testFullyChargedAndManifestGale() {
        Faruzan charged = new Faruzan(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator);
        addStatBuffAt(
                chargedSimulator,
                charged,
                85.0 * FRAME,
                "Faruzan charged pre-release",
                StatType.ATK_PERCENT,
                1.0);
        addStatBuffAt(
                chargedSimulator,
                charged,
                87.0 * FRAME,
                "Faruzan charged post-release",
                StatType.ATK_PERCENT,
                10.0);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord fullyCharged = chargedRecords.get(0);
        assertEquals("Parthian Shot Fully Charged",
                fullyCharged.action.getName(),
                "Faruzan fully charged name");
        assertClose(2.108, fullyCharged.action.getDamagePercent(),
                "Faruzan fully charged multiplier");
        assertClose(96.0 * FRAME, fullyCharged.time,
                "Faruzan fully charged impact");
        assertClose(96.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Faruzan fully charged duration");
        assertClose(1.24,
                fullyCharged.action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                "Faruzan fully charged release at frame 86");

        Faruzan c0 = new Faruzan(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.SKILL);
        assertClose(12.0 * FRAME, c0.getLastSkillTime(),
                "Faruzan Manifest and Skill cooldown start at frame 12");
        assertEquals(1, c0.getManifestCharges(
                c0Simulator.getCurrentTime()),
                "Faruzan C0 gains one Manifest charge");
        assertClose(1092.0 * FRAME, c0.getManifestExpirationTime(),
                "Faruzan Manifest lasts 1080 frames from frame 12");
        double hurricaneCast = c0Simulator.getCurrentTime();
        addStatBuffAt(
                c0Simulator,
                c0,
                hurricaneCast + 48.0 * FRAME,
                "Faruzan Hurricane pre-release",
                StatType.ATK_PERCENT,
                1.0);
        addStatBuffAt(
                c0Simulator,
                c0,
                hurricaneCast + 50.0 * FRAME,
                "Faruzan Hurricane post-release",
                StatType.ATK_PERCENT,
                10.0);
        perform(c0Simulator, CharacterActionKey.CHARGE);
        ActionRecord hurricane = named(c0Records, "Hurricane Arrow").get(0);
        assertClose(hurricaneCast + 59.0 * FRAME, hurricane.time,
                "Faruzan A1 Hurricane impact");
        assertClose(hurricaneCast + 60.0 * FRAME,
                c0Simulator.getCurrentTime(),
                "Faruzan A1 Hurricane duration");
        assertClose(1.24,
                hurricane.action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                "Faruzan A1 Hurricane release at frame 49");
        assertEquals(0, c0.getManifestCharges(
                c0Simulator.getCurrentTime()),
                "Faruzan Hurricane consumes C0 charge");

        Faruzan c1 = new Faruzan(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertEquals(2, c1.getManifestCharges(
                c1Simulator.getCurrentTime()),
                "Faruzan C1 gains two Manifest charges");
        perform(c1Simulator, CharacterActionKey.CHARGE);
        perform(c1Simulator, CharacterActionKey.CHARGE);
        assertEquals(2, named(c1Records, "Hurricane Arrow").size(),
                "Faruzan C1 permits two Hurricane Arrows");
        advanceTo(c1Simulator, c1.getManifestExpirationTime());
        assertEquals(0, c1.getManifestCharges(
                c1Simulator.getCurrentTime()),
                "Faruzan Manifest uses a half-open expiry");
        perform(c1Simulator, CharacterActionKey.CHARGE);
        assertEquals(1,
                named(c1Records, "Parthian Shot Fully Charged").size(),
                "Faruzan expired Manifest no longer creates Hurricane");
    }

    private static void testSkillCollapseParticlesAndC4() {
        Faruzan c4 = new Faruzan(null, null, 4);
        CombatSimulator simulator = simulatorWith(c4);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        double[] beforeState = { Double.NaN, Double.NaN };
        observeSkillState(simulator, c4, 11.0 * FRAME,
                beforeState, 0);
        observeSkillState(simulator, c4, 13.0 * FRAME,
                beforeState, 1);
        c4.spendEnergy(10.0);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(0, (int) beforeState[0],
                "Faruzan has no Manifest before frame 12");
        assertEquals(2, (int) beforeState[1],
                "Faruzan C1+ Manifest is active after frame 12");
        ActionRecord skill = named(
                records, "Wind Realm of Nasamjnin").get(0);
        assertClose(14.0 * FRAME, skill.time,
                "Faruzan Skill damage frame");
        assertClose(2.976, skill.action.getDamagePercent(),
                "Faruzan C3 Skill multiplier");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Faruzan Skill has no ICD");
        assertEquals(ICDTag.None, skill.action.getICDTag(),
                "Faruzan Skill uses no ICD tag");
        assertClose(1.0, skill.action.getGaugeUnits(),
                "Faruzan Skill applies 1U Anemo");
        assertClose(12.0 * FRAME, c4.getLastSkillTime(),
                "Faruzan Skill cooldown starts at frame 12");
        assertClose(372.0 * FRAME, c4.getSkillCooldownEndTime(),
                "Faruzan Skill six-second cooldown boundary");

        perform(simulator, CharacterActionKey.CHARGE);
        perform(simulator, CharacterActionKey.CHARGE);
        advanceTo(simulator, 187.0 * FRAME + EPSILON);
        List<ActionRecord> collapses = named(
                records, "Pressurized Collapse");
        assertEquals(2, collapses.size(),
                "Faruzan C1 arrows each create Collapse");
        assertClose(127.0 * FRAME, collapses.get(0).time,
                "Faruzan first Collapse is 33 frames after impact");
        assertClose(187.0 * FRAME, collapses.get(1).time,
                "Faruzan second Collapse is 33 frames after impact");
        for (ActionRecord collapse : collapses) {
            assertClose(2.16, collapse.action.getDamagePercent(),
                    "Faruzan C3 Collapse multiplier");
            assertEquals(ICDType.None, collapse.action.getICDType(),
                    "Faruzan Collapse has no ICD");
            assertEquals(ICDTag.None, collapse.action.getICDTag(),
                    "Faruzan Collapse uses no ICD tag");
            assertClose(1.0, collapse.action.getGaugeUnits(),
                    "Faruzan Collapse applies 1U Anemo");
        }
        assertClose(4.0, c4.getTotalFlatEnergy(),
                "Faruzan C4 grants Energy for both gated Collapses");
        assertClose(74.0, c4.getCurrentEnergy(),
                "Faruzan C4 Energy is independent from particles");
        advanceTo(simulator, 227.0 * FRAME);
        assertEquals(1, particles.size(),
                "Faruzan 330-frame particle gate blocks second Collapse");
        assertClose(2.0, particles.get(0).count,
                "Faruzan Collapse emits two particles");
        assertClose(227.0 * FRAME, particles.get(0).time,
                "Faruzan particles arrive 100 frames after Collapse");
    }

    private static void testBurstOrderingPulseWindowsAndStaleRecast() {
        Faruzan c0 = new Faruzan(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        double[] energy = { Double.NaN, Double.NaN };
        observeEnergy(simulator, c0, 2.0 * FRAME, energy, 0);
        observeEnergy(simulator, c0, 4.0 * FRAME, energy, 1);
        double[] prayer = { Double.NaN, Double.NaN };
        observeApplicableStat(simulator, c0, 42.0 * FRAME,
                StatType.ANEMO_DMG_BONUS, prayer, 0);
        observeApplicableStat(simulator, c0, 44.0 * FRAME,
                StatType.ANEMO_DMG_BONUS, prayer, 1);
        double[] shredAtHit = { Double.NaN };
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (action.getName().equals("The Wind's Secret Ways")) {
                shredAtHit[0] = c0.getPerfidiousExpirationTime();
            }
        });
        perform(simulator, CharacterActionKey.BURST);
        assertClose(80.0, energy[0],
                "Faruzan Burst Energy remains before frame 3");
        assertClose(0.0, energy[1],
                "Faruzan Burst spends Energy at frame 3");
        assertClose(0.0, prayer[0],
                "Faruzan Prayer is absent before frame 43");
        assertClose(0.306, prayer[1],
                "Faruzan Prayer starts at frame 43");
        ActionRecord burst = named(
                records, "The Wind's Secret Ways").get(0);
        assertClose(54.0 * FRAME, burst.time,
                "Faruzan Burst hit frame");
        assertClose(6.4192, burst.action.getDamagePercent(),
                "Faruzan C0 Burst multiplier");
        assertEquals(ICDType.None, burst.action.getICDType(),
                "Faruzan Burst has no ICD");
        assertClose(Double.NEGATIVE_INFINITY, shredAtHit[0],
                "Faruzan Burst damage resolves before first shred");
        assertClose(294.0 * FRAME, c0.getPerfidiousExpirationTime(),
                "Faruzan first shred starts after frame-54 hit");
        assertClose(60.0 * FRAME, simulator.getCurrentTime(),
                "Faruzan Burst public action uses frame-60 swap cancel");

        advanceTo(simulator, 1020.0 * FRAME - EPSILON);
        assertClose(0.306, applicableStat(
                simulator, c0, StatType.ANEMO_DMG_BONUS),
                "Faruzan C0 Prayer active before final shred expiry");
        assertClose(0.30, applicableStat(
                simulator, c0, StatType.ANEMO_RES_SHRED),
                "Faruzan C0 shred active before frame 1020");
        advanceTo(simulator, 1020.0 * FRAME);
        assertClose(0.0, applicableStat(
                simulator, c0, StatType.ANEMO_RES_SHRED),
                "Faruzan C0 shred expires at frame 1020");
        advanceTo(simulator, 1028.0 * FRAME);
        assertClose(0.0, applicableStat(
                simulator, c0, StatType.ANEMO_DMG_BONUS),
                "Faruzan C0 Prayer expires at frame 1028");

        Faruzan c2 = new Faruzan(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        perform(c2Simulator, CharacterActionKey.BURST);
        advanceTo(c2Simulator, 1380.0 * FRAME - EPSILON);
        assertClose(0.30, applicableStat(
                c2Simulator, c2, StatType.ANEMO_RES_SHRED),
                "Faruzan C2 shred active before frame 1380");
        advanceTo(c2Simulator, 1380.0 * FRAME);
        assertClose(0.0, applicableStat(
                c2Simulator, c2, StatType.ANEMO_RES_SHRED),
                "Faruzan C2 shred expires at frame 1380");
        advanceTo(c2Simulator, 1388.0 * FRAME - EPSILON);
        assertClose(0.306, applicableStat(
                c2Simulator, c2, StatType.ANEMO_DMG_BONUS),
                "Faruzan C2 Prayer active before frame 1388");
        advanceTo(c2Simulator, 1388.0 * FRAME);
        assertClose(0.0, applicableStat(
                c2Simulator, c2, StatType.ANEMO_DMG_BONUS),
                "Faruzan C2 Prayer expires at frame 1388");

        Faruzan recast = new Faruzan(null, null, 0);
        CombatSimulator recastSimulator = simulatorWith(recast);
        perform(recastSimulator, CharacterActionKey.BURST);
        advanceTo(recastSimulator, 100.0 * FRAME);
        double[] preservedPrayer = { Double.NaN };
        double[] preservedShred = { Double.NaN };
        observeApplicableStat(
                recastSimulator,
                recast,
                101.0 * FRAME,
                StatType.ANEMO_DMG_BONUS,
                preservedPrayer,
                0);
        observeApplicableStat(
                recastSimulator,
                recast,
                101.0 * FRAME,
                StatType.ANEMO_RES_SHRED,
                preservedShred,
                0);
        recast.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                recastSimulator);
        assertClose(0.306, preservedPrayer[0],
                "Faruzan recast preserves active Prayer before new pulse");
        assertClose(0.30, preservedShred[0],
                "Faruzan recast preserves active shred before new hit");
        advanceTo(recastSimulator, 180.0 * FRAME);
        assertClose(394.0 * FRAME,
                recast.getPerfidiousExpirationTime(),
                "Faruzan recast suppresses stale shred pulse");
        advanceTo(recastSimulator, 282.0 * FRAME);
        assertClose(383.0 * FRAME,
                recast.getPrayerExpirationTime(),
                "Faruzan recast suppresses stale Prayer pulse");
    }

    private static void testA4CategoriesRatioGateAndExclusions() {
        Faruzan faruzan = new Faruzan(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.SAYU, Element.ANEMO);
        CombatSimulator simulator = simulatorWith(faruzan, ally);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 102.0 * FRAME + EPSILON);
        ActionType[] eligible = {
            ActionType.NORMAL,
            ActionType.CHARGE,
            ActionType.PLUNGE,
            ActionType.SKILL,
            ActionType.BURST
        };
        for (ActionType type : eligible) {
            assertClose(196.0 * 0.32, targetDependentBonus(
                    faruzan,
                    simulator,
                    ally,
                    attackProbe("Faruzan A4 " + type,
                            type, Element.ANEMO)),
                    "Faruzan A4 covers " + type);
        }
        assertClose(0.0, targetDependentBonus(
                faruzan,
                simulator,
                ally,
                attackProbe("Faruzan A4 Physical",
                        ActionType.NORMAL, Element.PHYSICAL)),
                "Faruzan A4 excludes non-Anemo damage");
        assertClose(0.0, targetDependentBonus(
                faruzan,
                simulator,
                ally,
                attackProbe("Faruzan A4 other",
                        ActionType.OTHER, Element.ANEMO)),
                "Faruzan A4 excludes OTHER damage");
        TestCharacter outsider = new TestCharacter(
                CharacterId.NOELLE, Element.ANEMO);
        assertClose(0.0, targetDependentBonus(
                faruzan,
                simulator,
                outsider,
                attackProbe("Faruzan A4 outsider",
                        ActionType.NORMAL, Element.ANEMO)),
                "Faruzan A4 excludes non-party attackers");
        StatsContainer nullTargetStats = new StatsContainer();
        faruzan.applyTargetDependentTeamStats(
                nullTargetStats,
                ally,
                null,
                attackProbe("Faruzan A4 null target",
                        ActionType.NORMAL, Element.ANEMO),
                simulator.getCurrentTime());
        assertClose(0.0,
                nullTargetStats.get(StatType.FLAT_DMG_BONUS),
                "Faruzan A4 ignores a null target");

        Faruzan gated = new Faruzan(null, null, 0);
        CombatSimulator gatedSimulator = simulatorWith(gated);
        perform(gatedSimulator, CharacterActionKey.BURST);
        advanceTo(gatedSimulator, 102.0 * FRAME - EPSILON);
        assertClose(0.0, targetDependentBonus(
                gated,
                gatedSimulator,
                gated,
                attackProbe("Faruzan A4 before gate",
                        ActionType.SKILL, Element.ANEMO)),
                "Faruzan A4 shared gate remains closed before frame 102");
        advanceTo(gatedSimulator, 102.0 * FRAME + EPSILON);
        assertClose(196.0 * 0.32, targetDependentBonus(
                gated,
                gatedSimulator,
                gated,
                attackProbe("Faruzan A4 at gate",
                        ActionType.SKILL, Element.ANEMO)),
                "Faruzan A4 shared gate opens immediately after frame 102");
    }

    private static void testConstellationTalentAndC6Gates() {
        Faruzan c5 = new Faruzan(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(7.552,
                named(c5Records, "The Wind's Secret Ways")
                        .get(0).action.getDamagePercent(),
                "Faruzan C5 Burst multiplier");
        assertClose(0.36, applicableStat(
                c5Simulator, c5, StatType.ANEMO_DMG_BONUS),
                "Faruzan C5 Anemo bonus");
        assertClose(0.0, applicableStat(
                c5Simulator, c5, StatType.ANEMO_CRIT_DMG),
                "Faruzan C5 has no C6 Anemo CRIT DMG");

        Faruzan c6 = new Faruzan(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.40, applicableStat(
                simulator, c6, StatType.ANEMO_CRIT_DMG),
                "Faruzan C6 grants Anemo CRIT DMG");
        advanceTo(simulator, 87.0 * FRAME + EPSILON);
        assertEquals(1, named(records, "Pressurized Collapse").size(),
                "Faruzan C6 Burst hit schedules one Collapse");
        advanceTo(simulator, 187.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Faruzan first C6 Collapse generates particles");

        advanceTo(simulator, 384.0 * FRAME);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.FARUZAN,
                attackProbe("Faruzan C6 Pyro trigger",
                        ActionType.OTHER, Element.PYRO));
        advanceTo(simulator, 417.0 * FRAME + EPSILON);
        assertEquals(2, named(records, "Pressurized Collapse").size(),
                "Faruzan C6 accepts active-attacker non-Anemo damage");
        advanceTo(simulator, 517.0 * FRAME + EPSILON);
        assertEquals(2, particles.size(),
                "Faruzan particle gate opens exactly after 330 frames");

        Faruzan indirect = new Faruzan(null, null, 6);
        CombatSimulator indirectSimulator = simulatorWith(indirect);
        List<ActionRecord> indirectRecords = captureActions(
                indirectSimulator);
        perform(indirectSimulator, CharacterActionKey.BURST);
        advanceTo(indirectSimulator, 234.0 * FRAME + EPSILON);
        indirectSimulator.notifyIndirectDamage(indirect, 100.0);
        advanceTo(indirectSimulator, 267.0 * FRAME + EPSILON);
        assertEquals(2,
                named(indirectRecords, "Pressurized Collapse").size(),
                "Faruzan C6 accepts active-owner indirect damage");
        indirectSimulator.notifyIndirectDamage(indirect, 100.0);
        advanceTo(indirectSimulator, 414.0 * FRAME - EPSILON);
        assertEquals(2,
                named(indirectRecords, "Pressurized Collapse").size(),
                "Faruzan C6 indirect damage shares the 180-frame gate");
        advanceTo(indirectSimulator, 414.0 * FRAME + 2.0 * EPSILON);
        indirectSimulator.notifyIndirectDamage(indirect, 100.0);
        advanceTo(indirectSimulator, 447.0 * FRAME + 4.0 * EPSILON);
        assertEquals(3,
                named(indirectRecords, "Pressurized Collapse").size(),
                "Faruzan C6 indirect gate opens immediately after 180 frames");

        Faruzan offField = new Faruzan(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.SAYU, Element.ANEMO);
        CombatSimulator offFieldSimulator = simulatorWith(offField, ally);
        List<ActionRecord> offFieldRecords = captureActions(
                offFieldSimulator);
        perform(offFieldSimulator, CharacterActionKey.BURST);
        advanceTo(offFieldSimulator, 234.0 * FRAME);
        offFieldSimulator.switchCharacter(CharacterId.SAYU);
        offFieldSimulator.performActionWithoutTimeAdvance(
                CharacterId.FARUZAN,
                attackProbe("Faruzan off-field C6 probe",
                        ActionType.OTHER, Element.PYRO));
        advanceTo(offFieldSimulator, 267.0 * FRAME);
        assertEquals(1,
                named(offFieldRecords, "Pressurized Collapse").size(),
                "Faruzan C6 ignores damage from an off-field attacker");

        Faruzan nonReset = new Faruzan(null, null, 6);
        CombatSimulator nonResetSimulator = simulatorWith(nonReset);
        List<ActionRecord> nonResetRecords = captureActions(
                nonResetSimulator);
        perform(nonResetSimulator, CharacterActionKey.BURST);
        advanceTo(nonResetSimulator, 100.0 * FRAME);
        nonReset.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                nonResetSimulator);
        advanceTo(nonResetSimulator, 187.0 * FRAME + EPSILON);
        assertEquals(1,
                named(nonResetRecords, "Pressurized Collapse").size(),
                "Faruzan Burst recast does not reset the 180-frame C6 gate");
        advanceTo(nonResetSimulator, 282.0 * FRAME);
        assertClose(383.0 * FRAME,
                nonReset.getPrayerExpirationTime(),
                "Faruzan recast invalidates old C2 Prayer pulses");
    }

    private static void testSnapshotRestorePendingAndSupportState() {
        Faruzan pending = new Faruzan(null, null, 1);
        CombatSimulator pendingSimulator = simulatorWith(pending);
        List<ActionRecord> pendingRecords = captureActions(
                pendingSimulator);
        List<ParticleRecord> pendingParticles = captureAnemoParticles(
                pendingSimulator);
        perform(pendingSimulator, CharacterActionKey.SKILL);
        perform(pendingSimulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot beforeCollapse = pendingSimulator.saveSnapshot();
        advanceTo(pendingSimulator, 230.0 * FRAME);
        assertEquals(1,
                named(pendingRecords, "Pressurized Collapse").size(),
                "Faruzan branch resolves pending Collapse once");
        assertEquals(1, pendingParticles.size(),
                "Faruzan branch resolves pending particle once");
        pendingSimulator.restoreSnapshot(beforeCollapse);
        pendingSimulator.restoreSnapshot(beforeCollapse);
        pendingRecords.clear();
        pendingParticles.clear();
        advanceTo(pendingSimulator, 230.0 * FRAME);
        assertEquals(1,
                named(pendingRecords, "Pressurized Collapse").size(),
                "Faruzan repeated restore reconstructs one Collapse");
        assertEquals(1, pendingParticles.size(),
                "Faruzan repeated restore reconstructs one particle event");

        Faruzan particleOwner = new Faruzan(null, null, 1);
        CombatSimulator particleSimulator = simulatorWith(particleOwner);
        List<ParticleRecord> particleRecords = captureAnemoParticles(
                particleSimulator);
        perform(particleSimulator, CharacterActionKey.SKILL);
        perform(particleSimulator, CharacterActionKey.CHARGE);
        advanceTo(particleSimulator, 127.0 * FRAME + EPSILON);
        SimulatorSnapshot pendingParticle = particleSimulator.saveSnapshot();
        advanceTo(particleSimulator, 227.0 * FRAME);
        assertEquals(1, particleRecords.size(),
                "Faruzan pending particle branch resolves once");
        particleSimulator.restoreSnapshot(pendingParticle);
        particleSimulator.restoreSnapshot(pendingParticle);
        particleRecords.clear();
        advanceTo(particleSimulator, 227.0 * FRAME);
        assertEquals(1, particleRecords.size(),
                "Faruzan repeated restore keeps pending particle once");

        Faruzan support = new Faruzan(null, null, 2);
        CombatSimulator supportSimulator = simulatorWith(support);
        perform(supportSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot supportSnapshot = supportSimulator.saveSnapshot();
        advanceTo(supportSimulator, 1388.0 * FRAME);
        assertClose(0.0, applicableStat(
                supportSimulator, support, StatType.ANEMO_DMG_BONUS),
                "Faruzan support branch reaches expiry");
        supportSimulator.restoreSnapshot(supportSnapshot);
        supportSimulator.restoreSnapshot(supportSnapshot);
        assertClose(0.306, applicableStat(
                supportSimulator, support, StatType.ANEMO_DMG_BONUS),
                "Faruzan restore recovers Prayer support");
        assertClose(0.30, applicableStat(
                supportSimulator, support, StatType.ANEMO_RES_SHRED),
                "Faruzan restore recovers Perfidious support");
        advanceTo(supportSimulator, 1388.0 * FRAME);
        assertClose(0.0, applicableStat(
                supportSimulator, support, StatType.ANEMO_DMG_BONUS),
                "Faruzan restored pulses preserve final expiry");
    }

    private static void testInvalidInputsEnergyTargetAndIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Faruzan(null, null, -1),
                "Faruzan rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Faruzan(null, null, 7),
                "Faruzan rejects constellation above six");

        Faruzan invalid = new Faruzan(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Faruzan rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Faruzan rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> invalidSimulator.performAction(
                        CharacterId.FARUZAN,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Faruzan rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Faruzan rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.PLUNGE),
                "Faruzan rejects excluded Plunge timing");

        Faruzan insufficient = new Faruzan(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Faruzan insufficient Energy rejects Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Faruzan records rejected Burst Energy");
        assertClose(Double.NEGATIVE_INFINITY,
                insufficient.getPrayerExpirationTime(),
                "Faruzan rejected Burst starts no support");

        Faruzan noTarget = new Faruzan(null, null, 0);
        CombatSimulator noTargetSimulator = simulatorWith(noTarget);
        StatsContainer noTargetStats = new StatsContainer();
        noTarget.applyTargetDependentTeamStats(
                noTargetStats,
                noTarget,
                null,
                attackProbe("Faruzan no-target A4",
                        ActionType.SKILL, Element.ANEMO),
                noTargetSimulator.getCurrentTime());
        assertClose(0.0,
                noTargetStats.get(StatType.FLAT_DMG_BONUS),
                "Faruzan target-dependent support ignores no target");

        Faruzan reused = new Faruzan(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Faruzan rejects cross-simulator reuse");
        Faruzan owner = new Faruzan(null, null, 0);
        Faruzan foreign = new Faruzan(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Faruzan rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, new CombatSimulator()),
                "Faruzan rejects foreign state type");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
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
                CharacterId.FARUZAN, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.FARUZAN) {
                records.add(new ActionRecord(action, damage, time));
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

    private static AttackAction attackProbe(
            String name,
            ActionType type,
            Element element) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                damageBonusFor(type),
                0.0,
                type);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static StatType damageBonusFor(ActionType type) {
        switch (type) {
            case NORMAL:
                return StatType.NORMAL_ATTACK_DMG_BONUS;
            case CHARGE:
                return StatType.CHARGED_ATTACK_DMG_BONUS;
            case PLUNGE:
                return StatType.PLUNGING_ATTACK_DMG_BONUS;
            case SKILL:
                return StatType.SKILL_DMG_BONUS;
            case BURST:
                return StatType.BURST_DMG_BONUS;
            default:
                return null;
        }
    }

    private static double targetDependentBonus(
            Faruzan faruzan,
            CombatSimulator simulator,
            Character attacker,
            AttackAction action) {
        StatsContainer stats = new StatsContainer();
        faruzan.applyTargetDependentTeamStats(
                stats,
                attacker,
                simulator.getEnemy(),
                action,
                simulator.getCurrentTime());
        return stats.get(StatType.FLAT_DMG_BONUS);
    }

    private static double applicableStat(
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

    private static void observeSkillState(
            CombatSimulator simulator,
            Faruzan faruzan,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = faruzan.getManifestCharges(time);
            }
        });
    }

    private static void observeEnergy(
            CombatSimulator simulator,
            Faruzan faruzan,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = faruzan.getCurrentEnergy();
            }
        });
    }

    private static void observeApplicableStat(
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
                values[index] = applicableStat(
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
            assertTrue(lines.get(index).startsWith("Faruzan,"),
                    path + " identity at line " + (index + 1));
        }
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
