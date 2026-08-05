package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.character.YunJin;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Yun Jin's Flying Cloud support slice. */
public final class YunJinRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private YunJinRegressionTest() {
    }

    /** Runs data, action, Formation, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstructors();
        testNormalChargedAndPlungeTiming();
        testPressAndHoldSkillTimingParticlesAndDynamicDef();
        testBurstTimingEnergyAndFormationStart();
        testMultiHitQuotaAndThirtyHitExhaustion();
        testRecipientQuotasAndC6();
        testC6NormalTimelineScaling();
        testLiveDefA4AndConstellations();
        testHalfOpenExpiry();
        testPendingParticleAndQuotaSnapshotRestore();
        testInvalidNullCrossSimulatorAndEnergyBoundaries();
        System.out.println("YunJinRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstructors()
            throws IOException {
        YunJin yunJin = new YunJin(null, null, 0);
        assertEquals(CharacterId.YUN_JIN, yunJin.getCharacterId(),
                "Yun Jin typed identity");
        assertEquals("Yun Jin", yunJin.getName(),
                "Yun Jin display identity");
        assertEquals(Element.GEO, yunJin.getElement(),
                "Yun Jin element");
        assertClose(10657.0,
                yunJin.getBaseStats().get(StatType.BASE_HP),
                "Yun Jin base HP");
        assertClose(191.0,
                yunJin.getBaseStats().get(StatType.BASE_ATK),
                "Yun Jin base ATK");
        assertClose(734.0,
                yunJin.getBaseStats().get(StatType.BASE_DEF),
                "Yun Jin base DEF");
        assertClose(1.2667,
                yunJin.getBaseStats().get(StatType.ENERGY_RECHARGE),
                "Yun Jin ascension Energy Recharge");
        assertClose(60.0, yunJin.getEnergyCost(),
                "Yun Jin Energy cost");
        assertClose(9.0, yunJin.getSkillCD(),
                "Yun Jin C0 Skill cooldown");
        assertClose(15.0, yunJin.getBurstCD(),
                "Yun Jin Burst cooldown");
        assertClose(443.0 * FRAME,
                new YunJin(null, null, 1).getSkillCD(),
                "Yun Jin C1 exact Skill cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new YunJin(null, null, constellation)
                            .getConstellation(),
                    "Yun Jin constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/YunJin/YunJin_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/YunJin/YunJin_Multipliers.csv"), 25);
    }

    private static void testNormalChargedAndPlungeTiming() {
        YunJin yunJin = new YunJin(null, null, 0);
        CombatSimulator simulator = simulatorWith(yunJin);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.YUN_JIN);
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        double[] multipliers = {
            0.74418, 0.73944, 0.42186, 0.5056,
            0.44082, 0.5293, 1.23714
        };
        double[] hitFrames = { 15, 33, 50, 65, 84, 96, 133 };
        assertEquals(7, records.size(),
                "Yun Jin N1-N5 damage instances");
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Yun Jin Normal multiplier " + index);
            assertClose(hitFrames[index] * FRAME, record.time,
                    "Yun Jin Normal hitmark " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Yun Jin Normal action type");
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Yun Jin Normal element");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Yun Jin Normal ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Yun Jin Normal ICD tag");
            assertTrue(!record.action.hasStatSnapshot(),
                    "Yun Jin Normal resolves live stats");
        }
        assertClose(185.0 * FRAME, simulator.getCurrentTime(),
                "Yun Jin N1-N5 duration");

        YunJin chargedYunJin = new YunJin(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(chargedYunJin);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator, CharacterId.YUN_JIN);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertEquals(1, chargedRecords.size(),
                "Yun Jin Charged damage instances");
        ActionRecord charged = chargedRecords.get(0);
        assertClose(25.0 * FRAME, charged.time,
                "Yun Jin Charged hitmark");
        assertClose(59.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Yun Jin Charged duration");
        assertClose(2.2357, charged.action.getDamagePercent(),
                "Yun Jin Charged multiplier");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Yun Jin Charged action type");
        assertTrue(!charged.action.hasStatSnapshot(),
                "Yun Jin Charged resolves live stats");

        YunJin plungeYunJin = new YunJin(null, null, 0);
        CombatSimulator plungeSimulator = simulatorWith(plungeYunJin);
        List<ActionRecord> plungeRecords = captureActions(
                plungeSimulator, CharacterId.YUN_JIN);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        assertEquals(1, plungeRecords.size(),
                "Yun Jin High Plunge damage instances");
        ActionRecord plunge = plungeRecords.get(0);
        assertClose(43.0 * FRAME, plunge.time,
                "Yun Jin High Plunge hitmark");
        assertClose(80.0 * FRAME, plungeSimulator.getCurrentTime(),
                "Yun Jin High Plunge duration");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Yun Jin High Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Yun Jin High Plunge action type");
        assertTrue(!plunge.action.hasStatSnapshot(),
                "Yun Jin High Plunge resolves live stats");
        assertTrue(plunge.action.isShatterTrigger(),
                "Yun Jin High Plunge is blunt");
    }

    private static void testPressAndHoldSkillTimingParticlesAndDynamicDef() {
        YunJin press = new YunJin(null, null, 0);
        CombatSimulator pressSimulator = simulatorWith(press);
        List<ActionRecord> pressRecords = captureActions(
                pressSimulator, CharacterId.YUN_JIN);
        List<ParticleRecord> pressParticles = captureGeoParticles(
                pressSimulator);
        performSkill(pressSimulator, SkillActionMode.PRESS);
        assertEquals(1, pressRecords.size(),
                "Yun Jin Press damage instances");
        ActionRecord pressHit = pressRecords.get(0);
        assertClose(13.0 * FRAME, pressHit.time,
                "Yun Jin Press hitmark");
        assertClose(62.0 * FRAME, pressSimulator.getCurrentTime(),
                "Yun Jin Press duration");
        assertClose(2.53504, pressHit.action.getDamagePercent(),
                "Yun Jin Press multiplier");
        assertEquals(ActionType.SKILL, pressHit.action.getActionType(),
                "Yun Jin Press action type");
        assertEquals(Element.GEO, pressHit.action.getElement(),
                "Yun Jin Press element");
        assertEquals(StatType.BASE_DEF,
                pressHit.action.getScalingStat(),
                "Yun Jin Press DEF scaling");
        assertEquals(ICDType.None, pressHit.action.getICDType(),
                "Yun Jin Press no ICD");
        assertClose(2.0, pressHit.action.getGaugeUnits(),
                "Yun Jin Press gauge");
        assertTrue(!pressHit.action.hasStatSnapshot(),
                "Yun Jin Press reads live DEF");
        assertClose(11.0 * FRAME + 9.0,
                press.getSkillCooldownEndTime(),
                "Yun Jin Press cooldown starts at frame 11");
        advanceTo(pressSimulator, 113.0 * FRAME - EPSILON);
        assertEquals(0, pressParticles.size(),
                "Yun Jin Press particles wait for travel");
        advanceTo(pressSimulator, 113.0 * FRAME);
        assertEquals(1, pressParticles.size(),
                "Yun Jin Press particle packet count");
        assertClose(2.0, pressParticles.get(0).count,
                "Yun Jin Press particle amount");
        assertClose(113.0 * FRAME, pressParticles.get(0).time,
                "Yun Jin Press particle arrival");

        YunJin dynamic = new YunJin(null, null, 0);
        CombatSimulator dynamicSimulator = simulatorWith(dynamic);
        List<ActionRecord> dynamicRecords = captureActions(
                dynamicSimulator, CharacterId.YUN_JIN);
        dynamicSimulator.registerEvent(new SimpleTimerEvent(
                12.0 * FRAME, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                dynamic.addBuff(new SimpleBuff(
                        "Yun Jin dynamic DEF probe",
                        5.0,
                        activeSimulator.getCurrentTime(),
                        stats -> stats.add(StatType.DEF_PERCENT, 1.0)));
            }
        });
        performSkill(dynamicSimulator, SkillActionMode.PRESS);
        assertClose(2.0,
                dynamicRecords.get(0).damage / pressHit.damage,
                "Yun Jin Press reads DEF gained after cast");

        YunJin hold = new YunJin(null, null, 0);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureActions(
                holdSimulator, CharacterId.YUN_JIN);
        List<ParticleRecord> holdParticles = captureGeoParticles(
                holdSimulator);
        performSkill(holdSimulator, SkillActionMode.HOLD);
        assertEquals(1, holdRecords.size(),
                "Yun Jin Hold damage instances");
        ActionRecord holdHit = holdRecords.get(0);
        assertClose(93.0 * FRAME, holdHit.time,
                "Yun Jin Hold Level 2 hitmark");
        assertClose(141.0 * FRAME, holdSimulator.getCurrentTime(),
                "Yun Jin Hold Level 2 duration");
        assertClose(6.3376, holdHit.action.getDamagePercent(),
                "Yun Jin Hold Level 2 multiplier");
        assertClose(4.0, holdHit.action.getGaugeUnits(),
                "Yun Jin Hold Level 2 gauge");
        assertClose(90.0 * FRAME + 9.0,
                hold.getSkillCooldownEndTime(),
                "Yun Jin Hold cooldown starts at frame 90");
        advanceTo(holdSimulator, 193.0 * FRAME);
        assertEquals(1, holdParticles.size(),
                "Yun Jin Hold particle packet count");
        assertClose(3.0, holdParticles.get(0).count,
                "Yun Jin Hold particle amount");
        assertClose(193.0 * FRAME, holdParticles.get(0).time,
                "Yun Jin Hold particle arrival");
    }

    private static void testBurstTimingEnergyAndFormationStart() {
        YunJin yunJin = new YunJin(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yunJin, ally);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.YUN_JIN);
        double[] observedEnergy = { -1.0, -1.0 };
        observeEnergy(simulator, yunJin, 3.0 * FRAME,
                observedEnergy, 0);
        observeEnergy(simulator, yunJin, 5.0 * FRAME,
                observedEnergy, 1);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(60.0, observedEnergy[0],
                "Yun Jin Burst Energy before frame 4");
        assertClose(0.0, observedEnergy[1],
                "Yun Jin Burst Energy after frame 4");
        assertClose(0.0, yunJin.getCurrentEnergy(),
                "Yun Jin Burst spends 60 Energy");
        assertClose(57.0 * FRAME, simulator.getCurrentTime(),
                "Yun Jin Burst duration");
        assertEquals(1, records.size(),
                "Yun Jin Burst initial damage instances");
        ActionRecord burst = records.get(0);
        assertClose(35.0 * FRAME, burst.time,
                "Yun Jin Burst hitmark");
        assertClose(4.148, burst.action.getDamagePercent(),
                "Yun Jin Burst multiplier");
        assertEquals(ActionType.BURST, burst.action.getActionType(),
                "Yun Jin Burst action type");
        assertClose(2.0, burst.action.getGaugeUnits(),
                "Yun Jin Burst gauge");
        assertEquals(ICDType.None, burst.action.getICDType(),
                "Yun Jin Burst no ICD");
        assertEquals(30, yunJin.getFormationQuota(
                CharacterId.YUN_JIN, simulator.getCurrentTime()),
                "Yun Jin receives Formation quota");
        assertEquals(30, yunJin.getFormationQuota(
                CharacterId.NOELLE, simulator.getCurrentTime()),
                "ally receives Formation quota");
        assertClose(35.0 * FRAME + 12.0,
                yunJin.getFormationExpirationTime(),
                "Yun Jin Formation starts after Burst damage");
        assertClose(15.0 - 57.0 * FRAME,
                yunJin.getBurstCDRemaining(simulator.getCurrentTime()),
                "Yun Jin Burst cooldown starts at cast");
    }

    private static void testMultiHitQuotaAndThirtyHitExhaustion() {
        YunJin multiHit = new YunJin(null, null, 0);
        CombatSimulator multiHitSimulator = simulatorWith(multiHit);
        perform(multiHitSimulator, CharacterActionKey.BURST);
        perform(multiHitSimulator, CharacterActionKey.NORMAL);
        perform(multiHitSimulator, CharacterActionKey.NORMAL);
        perform(multiHitSimulator, CharacterActionKey.NORMAL);
        assertEquals(26, multiHit.getFormationQuota(
                CharacterId.YUN_JIN,
                multiHitSimulator.getCurrentTime()),
                "Yun Jin N3 consumes both Normal hit quotas");

        YunJin yunJin = new YunJin(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yunJin, ally);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.NOELLE);
        perform(simulator, CharacterActionKey.BURST);
        AttackAction probe = normalProbe("Yun Jin quota probe");
        for (int hit = 0; hit < 31; hit++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.NOELLE, probe);
        }
        assertEquals(0, yunJin.getFormationQuota(
                CharacterId.NOELLE, simulator.getCurrentTime()),
                "Yun Jin Formation exhausts at 30 successful hits");
        assertEquals(31, records.size(),
                "Yun Jin quota probe damage instances");
        assertTrue(records.get(29).damage > records.get(30).damage,
                "Yun Jin 30th hit is buffed and 31st is not");
        assertClose(records.get(30).damage, records.get(0).damage
                        - (records.get(29).damage
                                - records.get(30).damage),
                1e-6,
                "Yun Jin fixed Formation damage is stable through quota");

        int beforeCharge = yunJin.getFormationQuota(
                CharacterId.YUN_JIN, simulator.getCurrentTime());
        simulator.performActionWithoutTimeAdvance(
                CharacterId.YUN_JIN,
                attackProbe("Yun Jin Charged quota exclusion",
                        ActionType.CHARGE));
        assertEquals(beforeCharge, yunJin.getFormationQuota(
                CharacterId.YUN_JIN, simulator.getCurrentTime()),
                "Yun Jin Formation excludes Charged damage");
    }

    private static void testRecipientQuotasAndC6() {
        YunJin yunJin = new YunJin(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yunJin, ally);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.12, applicableStat(
                simulator, yunJin, StatType.NORMAL_ATTACK_SPD,
                simulator.getCurrentTime()),
                "Yun Jin C6 applies to Yun Jin recipient");
        assertClose(0.12, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_SPD,
                simulator.getCurrentTime()),
                "Yun Jin C6 applies to ally recipient");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.YUN_JIN,
                normalProbe("Yun Jin recipient probe"));
        assertEquals(29, yunJin.getFormationQuota(
                CharacterId.YUN_JIN, simulator.getCurrentTime()),
                "Yun Jin quota decrements independently");
        assertEquals(30, yunJin.getFormationQuota(
                CharacterId.NOELLE, simulator.getCurrentTime()),
                "ally quota remains independent");
        for (int hit = 0; hit < 30; hit++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.NOELLE,
                    normalProbe("Yun Jin ally exhaustion probe"));
        }
        assertClose(0.0, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_SPD,
                simulator.getCurrentTime()),
                "Yun Jin C6 ends for exhausted ally");
        assertClose(0.12, applicableStat(
                simulator, yunJin, StatType.NORMAL_ATTACK_SPD,
                simulator.getCurrentTime()),
                "Yun Jin C6 remains for another recipient");
    }

    private static void testC6NormalTimelineScaling() {
        YunJin c5 = new YunJin(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        perform(c5Simulator, CharacterActionKey.BURST);
        List<ActionRecord> c5Records = captureActions(
                c5Simulator, CharacterId.YUN_JIN);
        double c5CastTime = c5Simulator.getCurrentTime();
        perform(c5Simulator, CharacterActionKey.NORMAL);
        assertClose(c5CastTime + 15.0 * FRAME,
                c5Records.get(0).time,
                "Yun Jin C5 N1 baseline hitmark");
        assertClose(c5CastTime + 20.0 * FRAME,
                c5Simulator.getCurrentTime(),
                "Yun Jin C5 N1 baseline duration");

        YunJin activeC6 = new YunJin(null, null, 6);
        CombatSimulator activeC6Simulator = simulatorWith(activeC6);
        perform(activeC6Simulator, CharacterActionKey.BURST);
        List<ActionRecord> activeC6Records = captureActions(
                activeC6Simulator, CharacterId.YUN_JIN);
        double activeC6CastTime = activeC6Simulator.getCurrentTime();
        perform(activeC6Simulator, CharacterActionKey.NORMAL);
        assertClose(activeC6CastTime + 15.0 * FRAME / 1.12,
                activeC6Records.get(0).time,
                "Yun Jin C6 scales N1 hitmark by 12 percent");
        assertClose(activeC6CastTime + 20.0 * FRAME / 1.12,
                activeC6Simulator.getCurrentTime(),
                "Yun Jin C6 scales N1 duration by 12 percent");

        YunJin exhaustedC6 = new YunJin(null, null, 6);
        CombatSimulator exhaustedC6Simulator = simulatorWith(exhaustedC6);
        perform(exhaustedC6Simulator, CharacterActionKey.BURST);
        for (int hit = 0; hit < 30; hit++) {
            exhaustedC6Simulator.performActionWithoutTimeAdvance(
                    CharacterId.YUN_JIN,
                    normalProbe("Yun Jin C6 exhaustion setup"));
        }
        assertEquals(0, exhaustedC6.getFormationQuota(
                CharacterId.YUN_JIN,
                exhaustedC6Simulator.getCurrentTime()),
                "Yun Jin C6 timeline fixture exhausts recipient quota");
        List<ActionRecord> exhaustedC6Records = captureActions(
                exhaustedC6Simulator, CharacterId.YUN_JIN);
        double exhaustedC6CastTime =
                exhaustedC6Simulator.getCurrentTime();
        perform(exhaustedC6Simulator, CharacterActionKey.NORMAL);
        assertClose(exhaustedC6CastTime + 15.0 * FRAME,
                exhaustedC6Records.get(0).time,
                "Yun Jin exhausted C6 restores N1 hitmark baseline");
        assertClose(exhaustedC6CastTime + 20.0 * FRAME,
                exhaustedC6Simulator.getCurrentTime(),
                "Yun Jin exhausted C6 restores N1 duration baseline");
    }

    private static void testLiveDefA4AndConstellations() {
        double oneElement = formationBonus(
                new YunJin(null, null, 0));
        assertClose(734.0 * (0.54672 + 0.025), oneElement,
                "Yun Jin one-element A4 ratio");

        YunJin twoElementYunJin = new YunJin(null, null, 0);
        TestCharacter pyroOnly = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator twoElementSimulator = simulatorWith(
                twoElementYunJin, pyroOnly);
        perform(twoElementSimulator, CharacterActionKey.BURST);
        assertClose(734.0 * (0.54672 + 0.05),
                targetDependentBonus(
                        twoElementYunJin,
                        twoElementSimulator,
                        pyroOnly,
                        normalProbe("Yun Jin two-element probe")),
                "Yun Jin two-element A4 ratio");

        YunJin threeElementYunJin = new YunJin(null, null, 0);
        TestCharacter threeElementPyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter threeElementCryo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator threeElementSimulator = simulatorWith(
                threeElementYunJin,
                threeElementPyro,
                threeElementCryo);
        perform(threeElementSimulator, CharacterActionKey.BURST);
        assertClose(734.0 * (0.54672 + 0.075),
                targetDependentBonus(
                        threeElementYunJin,
                        threeElementSimulator,
                        threeElementPyro,
                        normalProbe("Yun Jin three-element probe")),
                "Yun Jin three-element A4 ratio");

        YunJin fourElementYunJin = new YunJin(null, null, 0);
        TestCharacter geo = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter cryo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        TestCharacter electro = new TestCharacter(
                CharacterId.LISA, Element.ELECTRO);
        CombatSimulator fourElementSimulator = simulatorWith(
                fourElementYunJin, geo, pyro, cryo, electro);
        perform(fourElementSimulator, CharacterActionKey.BURST);
        assertClose(734.0 * (0.54672 + 0.115),
                targetDependentBonus(
                        fourElementYunJin,
                        fourElementSimulator,
                        geo,
                        normalProbe("Yun Jin four-element probe")),
                "Yun Jin four-element A4 ratio");
        fourElementYunJin.addBuff(new SimpleBuff(
                "Yun Jin live DEF probe",
                5.0,
                fourElementSimulator.getCurrentTime(),
                stats -> stats.add(StatType.DEF_PERCENT, 0.50)));
        assertClose(734.0 * 1.50 * (0.54672 + 0.115),
                targetDependentBonus(
                        fourElementYunJin,
                        fourElementSimulator,
                        geo,
                        normalProbe("Yun Jin live DEF Formation probe")),
                "Yun Jin Formation reads live DEF");

        YunJin c2 = new YunJin(null, null, 2);
        TestCharacter c2Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c2Simulator = simulatorWith(c2, c2Ally);
        perform(c2Simulator, CharacterActionKey.BURST);
        assertClose(0.15, applicableStat(
                c2Simulator, c2Ally,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                c2Simulator.getCurrentTime()),
                "Yun Jin C2 Normal DMG Bonus");

        YunJin c3 = new YunJin(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(
                c3Simulator, CharacterId.YUN_JIN);
        perform(c3Simulator, CharacterActionKey.BURST);
        assertClose(4.88, c3Records.get(0).action.getDamagePercent(),
                "Yun Jin C3 Burst multiplier");
        assertClose(734.0 * (0.6432 + 0.025),
                targetDependentBonus(
                        c3,
                        c3Simulator,
                        c3,
                        normalProbe("Yun Jin C3 Formation probe")),
                "Yun Jin C3 Formation ratio");

        YunJin c4 = new YunJin(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        c4Simulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Crystallize",
                        ReactionResult.Kind.CRYSTALLIZE),
                c4);
        assertClose(0.20, c4.getEffectiveStats(
                c4Simulator.getCurrentTime()).get(StatType.DEF_PERCENT),
                "Yun Jin C4 reacts to Crystallize");
        c4Simulator.advanceTime(12.0 - EPSILON);
        assertClose(0.20, c4.getEffectiveStats(
                c4Simulator.getCurrentTime()).get(StatType.DEF_PERCENT),
                "Yun Jin C4 remains before expiry");
        c4Simulator.advanceTime(EPSILON);
        assertClose(0.0, c4.getEffectiveStats(
                c4Simulator.getCurrentTime()).get(StatType.DEF_PERCENT),
                "Yun Jin C4 expires at exact boundary");

        YunJin c5 = new YunJin(null, null, 5);
        CombatSimulator c5PressSimulator = simulatorWith(c5);
        List<ActionRecord> c5PressRecords = captureActions(
                c5PressSimulator, CharacterId.YUN_JIN);
        performSkill(c5PressSimulator, SkillActionMode.PRESS);
        assertClose(2.9824,
                c5PressRecords.get(0).action.getDamagePercent(),
                "Yun Jin C5 Press multiplier");
        YunJin c5Hold = new YunJin(null, null, 5);
        CombatSimulator c5HoldSimulator = simulatorWith(c5Hold);
        List<ActionRecord> c5HoldRecords = captureActions(
                c5HoldSimulator, CharacterId.YUN_JIN);
        performSkill(c5HoldSimulator, SkillActionMode.HOLD);
        assertClose(7.456,
                c5HoldRecords.get(0).action.getDamagePercent(),
                "Yun Jin C5 Hold multiplier");
    }

    private static void testHalfOpenExpiry() {
        YunJin yunJin = new YunJin(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yunJin, ally);
        perform(simulator, CharacterActionKey.BURST);
        double expiration = yunJin.getFormationExpirationTime();
        assertEquals(30, yunJin.getFormationQuota(
                CharacterId.NOELLE, expiration - EPSILON),
                "Yun Jin Formation remains before expiry");
        assertClose(0.15, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_DMG_BONUS,
                expiration - EPSILON),
                "Yun Jin C2 remains before expiry");
        assertClose(0.12, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_SPD,
                expiration - EPSILON),
                "Yun Jin C6 remains before expiry");
        assertEquals(0, yunJin.getFormationQuota(
                CharacterId.NOELLE, expiration),
                "Yun Jin Formation expires exactly");
        assertClose(0.0, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_DMG_BONUS,
                expiration),
                "Yun Jin C2 expires exactly");
        assertClose(0.0, applicableStat(
                simulator, ally, StatType.NORMAL_ATTACK_SPD,
                expiration),
                "Yun Jin C6 expires exactly");
    }

    private static void testPendingParticleAndQuotaSnapshotRestore() {
        YunJin particleYunJin = new YunJin(null, null, 0);
        CombatSimulator particleSimulator = simulatorWith(particleYunJin);
        List<ParticleRecord> particles = captureGeoParticles(
                particleSimulator);
        performSkill(particleSimulator, SkillActionMode.PRESS);
        SimulatorSnapshot particleSnapshot = particleSimulator.saveSnapshot();
        advanceTo(particleSimulator, 2.0);
        assertEquals(1, particles.size(),
                "Yun Jin first particle branch");
        particleSimulator.restoreSnapshot(particleSnapshot);
        advanceTo(particleSimulator, 2.0);
        assertEquals(2, particles.size(),
                "Yun Jin restored particle branch is exact-once");
        particleSimulator.restoreSnapshot(particleSnapshot);
        particleSimulator.restoreSnapshot(particleSnapshot);
        advanceTo(particleSimulator, 2.0);
        assertEquals(3, particles.size(),
                "Yun Jin repeated restore avoids duplicate particles");

        YunJin quotaYunJin = new YunJin(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator quotaSimulator = simulatorWith(
                quotaYunJin, ally);
        perform(quotaSimulator, CharacterActionKey.BURST);
        quotaSimulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE,
                normalProbe("Yun Jin pre-snapshot quota probe"));
        assertEquals(29, quotaYunJin.getFormationQuota(
                CharacterId.NOELLE, quotaSimulator.getCurrentTime()),
                "Yun Jin pre-snapshot quota");
        SimulatorSnapshot quotaSnapshot = quotaSimulator.saveSnapshot();
        quotaSimulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE,
                normalProbe("Yun Jin branch quota probe"));
        assertEquals(28, quotaYunJin.getFormationQuota(
                CharacterId.NOELLE, quotaSimulator.getCurrentTime()),
                "Yun Jin branch quota consumption");
        quotaSimulator.restoreSnapshot(quotaSnapshot);
        assertEquals(29, quotaYunJin.getFormationQuota(
                CharacterId.NOELLE, quotaSimulator.getCurrentTime()),
                "Yun Jin restores active recipient quota");
        assertClose(0.12, applicableStat(
                quotaSimulator, ally, StatType.NORMAL_ATTACK_SPD,
                quotaSimulator.getCurrentTime()),
                "Yun Jin restores active C6 recipient state");
        quotaSimulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE,
                normalProbe("Yun Jin restored quota probe"));
        assertEquals(28, quotaYunJin.getFormationQuota(
                CharacterId.NOELLE, quotaSimulator.getCurrentTime()),
                "Yun Jin restored quota consumes once");
    }

    private static void testInvalidNullCrossSimulatorAndEnergyBoundaries() {
        assertThrows(IllegalArgumentException.class,
                () -> new YunJin(null, null, -1),
                "Yun Jin rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new YunJin(null, null, 7),
                "Yun Jin rejects constellation above six");

        YunJin unsupported = new YunJin(null, null, 0);
        CombatSimulator unsupportedSimulator = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSimulator),
                "Yun Jin rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.initializeForSimulator(null),
                "Yun Jin rejects null simulator");
        assertThrows(NullPointerException.class,
                () -> unsupportedSimulator.performAction(
                        CharacterId.YUN_JIN,
                        CharacterActionRequest.skill(null)),
                "Yun Jin rejects null Skill mode");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSimulator,
                        CharacterActionKey.DASH),
                "Yun Jin rejects Dash");

        YunJin insufficient = new YunJin(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator, CharacterId.YUN_JIN);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Yun Jin insufficient Energy rejects Burst");
        assertEquals(0, insufficient.getFormationQuota(
                CharacterId.YUN_JIN,
                insufficientSimulator.getCurrentTime()),
                "Yun Jin rejected Burst starts no Formation");
        assertClose(60.0, insufficient.getMissedBurstCost(),
                "Yun Jin records rejected Burst cost");

        YunJin nullTarget = new YunJin(null, null, 0);
        CombatSimulator nullTargetSimulator = simulatorWith(nullTarget);
        perform(nullTargetSimulator, CharacterActionKey.BURST);
        StatsContainer nullTargetStats = new StatsContainer();
        nullTarget.applyTargetDependentTeamStats(
                nullTargetStats,
                nullTarget,
                null,
                normalProbe("Yun Jin null-target probe"),
                nullTargetSimulator.getCurrentTime());
        assertClose(0.0,
                nullTargetStats.get(StatType.FLAT_DMG_BONUS),
                "Yun Jin Formation ignores null target");

        YunJin reusable = new YunJin(null, null, 0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Yun Jin rejects cross-simulator reuse");
        YunJin owner = new YunJin(null, null, 0);
        YunJin foreign = new YunJin(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Yun Jin rejects another instance's state");
    }

    private static double formationBonus(YunJin yunJin) {
        CombatSimulator simulator = simulatorWith(yunJin);
        perform(simulator, CharacterActionKey.BURST);
        return targetDependentBonus(
                yunJin,
                simulator,
                yunJin,
                normalProbe("Yun Jin Formation bonus probe"));
    }

    private static double targetDependentBonus(
            YunJin yunJin,
            CombatSimulator simulator,
            Character attacker,
            AttackAction action) {
        StatsContainer stats = new StatsContainer();
        yunJin.applyTargetDependentTeamStats(
                stats,
                attacker,
                simulator.getEnemy(),
                action,
                simulator.getCurrentTime());
        return stats.get(StatType.FLAT_DMG_BONUS);
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
                CharacterId.YUN_JIN,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.YUN_JIN,
                CharacterActionRequest.skill(mode));
    }

    private static AttackAction normalProbe(String name) {
        return attackProbe(name, ActionType.NORMAL);
    }

    private static AttackAction attackProbe(
            String name,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                actionType == ActionType.NORMAL
                        ? StatType.NORMAL_ATTACK_DMG_BONUS : null,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator,
            CharacterId characterId) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == characterId) {
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

    private static void observeEnergy(
            CombatSimulator simulator,
            YunJin yunJin,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = yunJin.getCurrentEnergy();
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
            assertTrue(lines.get(index).startsWith("Yun Jin,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        assertClose(expected, actual, EPSILON, message);
    }

    private static void assertClose(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
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

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
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
