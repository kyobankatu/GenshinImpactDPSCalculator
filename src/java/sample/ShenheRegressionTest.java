package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Shenhe;
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

/** Focused regression checks for Shenhe's stationary Icy Quill support slice. */
public final class ShenheRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ShenheRegressionTest() {
    }

    /** Runs data, timing, support, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testNormalAndChargedActions();
        testSkillTimingParticlesAndWindows();
        testQuillCategoriesDynamicAttackAndQuota();
        testBurstFieldTicksAndConstellations();
        testC1C3C4C5AndC6();
        testSnapshotRestore();
        testInvalidInputsEnergyAndIsolation();
        System.out.println("ShenheRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Shenhe shenhe = new Shenhe(null, null, 6);
        assertEquals(CharacterId.SHENHE, shenhe.getCharacterId(),
                "Shenhe typed identity");
        assertEquals(CharacterId.SHENHE, CharacterId.fromName("Shenhe"),
                "Shenhe name lookup");
        assertEquals(CharacterId.SHENHE, CharacterId.fromNumericId(41),
                "Shenhe numeric lookup");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.SHENHE.getRegion(), "Shenhe region");
        assertEquals(Element.CRYO, shenhe.getElement(),
                "Shenhe element");
        assertClose(12993.0,
                shenhe.getBaseStats().get(StatType.BASE_HP),
                "Shenhe base HP");
        assertClose(304.0,
                shenhe.getBaseStats().get(StatType.BASE_ATK),
                "Shenhe base ATK");
        assertClose(830.0,
                shenhe.getBaseStats().get(StatType.BASE_DEF),
                "Shenhe base DEF");
        assertClose(0.288,
                shenhe.getBaseStats().get(StatType.ATK_PERCENT),
                "Shenhe ascension ATK");
        assertClose(80.0, shenhe.getEnergyCost(),
                "Shenhe Energy cost");
        assertClose(10.0, shenhe.getSkillCD(),
                "Shenhe Skill cooldown");
        assertClose(20.0, shenhe.getBurstCD(),
                "Shenhe Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.SHENHE,
                    new Shenhe(null, null, constellation).getCharacterId(),
                    "Shenhe explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Shenhe/Shenhe_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Shenhe/Shenhe_Multipliers.csv"), 28);
        assertCsvValue(
                "config/characters/Shenhe/Shenhe_Status.csv",
                "Base ATK", 304.0);
        assertCsvValue(
                "config/characters/Shenhe/Shenhe_Multipliers.csv",
                "Quill ATK Ratio C3", 0.91312);
        assertCsvValue(
                "config/characters/Shenhe/Shenhe_Multipliers.csv",
                "A4 Press Skill Burst DMG Bonus", 0.15);
        assertCsvValue(
                "config/characters/Shenhe/Shenhe_Multipliers.csv",
                "A4 Hold Normal Charged Plunge DMG Bonus", 0.15);
        assertCsvValue(
                "config/characters/Shenhe/Shenhe_Multipliers.csv",
                "C4 Skill DMG Bonus Per Stack", 0.05);
    }

    private static void testNormalAndChargedActions() {
        Shenhe shenhe = new Shenhe(null, null, 0);
        CombatSimulator simulator = simulatorWith(shenhe);
        List<ActionRecord> records = captureActions(simulator);
        int[][] hitmarks = {
            { 14 }, { 17 }, { 19 }, { 14, 18 }, { 26 }
        };
        int[] durations = { 29, 23, 38, 30, 59 };
        int[] hitlagFrames = { 5, 5, 5, 5, 10 };
        double[][] multipliers = {
            { 0.79474 }, { 0.73944 }, { 0.9796 },
            { 0.48348, 0.48348 }, { 1.20554 }
        };
        int recordIndex = 0;
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < hitmarks[step].length; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Shenhe Normal multiplier");
                assertClose(castTime + hitmarks[step][hit] * FRAME,
                        record.time, "Shenhe Normal hitmark");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Shenhe Normal category");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Shenhe Normal element");
            }
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Shenhe Normal animation duration");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals("Dawnstar Piercer N1",
                records.get(recordIndex).action.getName(),
                "Shenhe Normal chain wraps after N5");

        Shenhe charged = new Shenhe(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord chargedRecord = chargedRecords.get(0);
        assertEquals("Dawnstar Piercer Charged",
                chargedRecord.action.getName(),
                "Shenhe Charged name");
        assertClose(2.033302, chargedRecord.action.getDamagePercent(),
                "Shenhe Charged multiplier");
        assertClose(25.0 * FRAME, chargedRecord.time,
                "Shenhe Charged hitmark");
        assertClose(52.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Shenhe Charged duration");
    }

    private static void testSkillTimingParticlesAndWindows() {
        Shenhe press = new Shenhe(null, null, 0);
        TestCharacter pressAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator pressSimulator = simulatorWith(press, pressAlly);
        List<ActionRecord> pressRecords = captureActions(pressSimulator);
        List<ParticleRecord> pressParticles = captureCryoParticles(
                pressSimulator);
        performSkill(pressSimulator, SkillActionMode.PRESS);
        ActionRecord pressHit = named(pressRecords,
                "Spring Spirit Summoning Press").get(0);
        assertClose(4.0 * FRAME, pressHit.time,
                "Shenhe Press hitmark");
        assertClose(2.3664, pressHit.action.getDamagePercent(),
                "Shenhe Press multiplier");
        assertEquals(ICDType.None, pressHit.action.getICDType(),
                "Shenhe Press ICD");
        assertClose(1.0, pressHit.action.getGaugeUnits(),
                "Shenhe Press gauge");
        assertClose(38.0 * FRAME, pressSimulator.getCurrentTime(),
                "Shenhe Press duration");
        assertTrue(!press.canSkill(10.0 - EPSILON),
                "Shenhe Press cooldown remains closed before ten seconds");
        assertTrue(press.canSkill(10.0),
                "Shenhe Press cooldown opens at ten seconds");
        assertClose(603.0 * FRAME, press.getIcyQuillExpirationTime(),
                "Shenhe Press Quill window starts at frame three");
        assertEquals(4, press.getIcyQuillQuota(
                CharacterId.SHENHE, pressSimulator.getCurrentTime()),
                "Shenhe Press hit consumes the owner's Quill");
        assertEquals(5, press.getIcyQuillQuota(
                CharacterId.QIQI, pressSimulator.getCurrentTime()),
                "Shenhe Press grants an independent ally quota");
        assertClose(0.15, applicableStat(
                pressSimulator, pressAlly, StatType.SKILL_DMG_BONUS),
                "Shenhe Press A4 Skill bonus");
        assertClose(0.15, applicableStat(
                pressSimulator, pressAlly, StatType.BURST_DMG_BONUS),
                "Shenhe Press A4 Burst bonus");
        advanceTo(pressSimulator, 105.0 * FRAME);
        assertEquals(1, pressParticles.size(),
                "Shenhe Press emits one particle event");
        assertClose(3.0, pressParticles.get(0).count,
                "Shenhe Press particles");
        assertClose(104.0 * FRAME, pressParticles.get(0).time,
                "Shenhe Press particle travel time");
        advanceTo(pressSimulator, 603.0 * FRAME);
        assertEquals(0, press.getIcyQuillQuota(
                CharacterId.QIQI, pressSimulator.getCurrentTime()),
                "Shenhe Press Quill expires half-open");
        assertClose(0.0, applicableStat(
                pressSimulator, pressAlly, StatType.SKILL_DMG_BONUS),
                "Shenhe Press A4 expires with Quill");

        Shenhe hold = new Shenhe(null, null, 0);
        TestCharacter holdAlly = new TestCharacter(
                CharacterId.GANYU, Element.CRYO);
        CombatSimulator holdSimulator = simulatorWith(hold, holdAlly);
        List<ActionRecord> holdRecords = captureActions(holdSimulator);
        List<ParticleRecord> holdParticles = captureCryoParticles(
                holdSimulator);
        performSkill(holdSimulator, SkillActionMode.HOLD);
        ActionRecord holdHit = named(holdRecords,
                "Spring Spirit Summoning Hold").get(0);
        assertClose(33.0 * FRAME, holdHit.time,
                "Shenhe Hold hitmark");
        assertClose(3.2096, holdHit.action.getDamagePercent(),
                "Shenhe Hold multiplier");
        assertClose(2.0, holdHit.action.getGaugeUnits(),
                "Shenhe Hold gauge");
        assertClose(78.0 * FRAME, holdSimulator.getCurrentTime(),
                "Shenhe Hold duration");
        assertTrue(!hold.canSkill(15.0 - EPSILON),
                "Shenhe Hold cooldown remains closed before fifteen seconds");
        assertTrue(hold.canSkill(15.0),
                "Shenhe Hold cooldown opens at fifteen seconds");
        assertClose(932.0 * FRAME, hold.getIcyQuillExpirationTime(),
                "Shenhe Hold Quill window starts at frame 32");
        assertEquals(7, hold.getIcyQuillQuota(
                CharacterId.GANYU, holdSimulator.getCurrentTime()),
                "Shenhe Hold ally quota");
        assertClose(0.15, applicableStat(
                holdSimulator, holdAlly,
                StatType.NORMAL_ATTACK_DMG_BONUS),
                "Shenhe Hold A4 Normal bonus");
        assertClose(0.15, applicableStat(
                holdSimulator, holdAlly,
                StatType.CHARGED_ATTACK_DMG_BONUS),
                "Shenhe Hold A4 Charged bonus");
        assertClose(0.15, applicableStat(
                holdSimulator, holdAlly,
                StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Shenhe Hold A4 Plunge bonus");
        advanceTo(holdSimulator, 134.0 * FRAME);
        assertEquals(1, holdParticles.size(),
                "Shenhe Hold emits one particle event");
        assertClose(4.0, holdParticles.get(0).count,
                "Shenhe Hold particles");
        assertClose(133.0 * FRAME, holdParticles.get(0).time,
                "Shenhe Hold particle travel time");
    }

    private static void testQuillCategoriesDynamicAttackAndQuota() {
        Shenhe shenhe = new Shenhe(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.CHONGYUN, Element.CRYO);
        CombatSimulator simulator = simulatorWith(shenhe, ally);
        performSkill(simulator, SkillActionMode.PRESS);
        AttackAction skill = attackProbe(
                "Shenhe Quill Skill probe", ActionType.SKILL, Element.CRYO);
        double expected = liveAttack(simulator, shenhe) * 0.776152;
        assertClose(expected,
                targetDependentBonus(shenhe, simulator, ally, skill),
                "Shenhe Quill uses live total ATK");
        shenhe.addBuff(new SimpleBuff(
                "Shenhe dynamic ATK probe",
                10.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        double buffedExpected = liveAttack(simulator, shenhe) * 0.776152;
        assertTrue(buffedExpected > expected,
                "Shenhe Quill responds to an ATK change after cast");
        assertClose(buffedExpected,
                targetDependentBonus(shenhe, simulator, ally, skill),
                "Shenhe Quill reads buffed ATK at hit time");

        for (ActionType type : new ActionType[] {
                ActionType.NORMAL, ActionType.CHARGE, ActionType.PLUNGE,
                ActionType.SKILL, ActionType.BURST
        }) {
            assertTrue(targetDependentBonus(
                    shenhe, simulator, ally,
                    attackProbe("Shenhe " + type + " probe",
                            type, Element.CRYO)) > 0.0,
                    "Shenhe Quill accepts " + type);
        }
        AttackAction countedSkill = attackProbe(
                "Shenhe counted Skill", ActionType.OTHER, Element.CRYO);
        countedSkill.setCountsAsSkillDmg(true);
        assertTrue(targetDependentBonus(
                shenhe, simulator, ally, countedSkill) > 0.0,
                "Shenhe Quill accepts counts-as-Skill damage");
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally,
                attackProbe("Shenhe unclassified probe",
                        ActionType.OTHER, Element.CRYO)),
                "Shenhe Quill rejects unclassified OTHER damage");
        TestCharacter foreign = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, foreign, skill),
                "Shenhe Quill rejects a non-party attacker");
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally,
                attackProbe("Shenhe Pyro probe",
                        ActionType.SKILL, Element.PYRO)),
                "Shenhe Quill rejects non-Cryo damage");
        AttackAction zero = new AttackAction(
                "Shenhe zero probe",
                0.0,
                Element.CRYO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        zero.setHitEffectTrigger(true);
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally, zero),
                "Shenhe Quill rejects zero motion value");
        AttackAction lunar = attackProbe(
                "Shenhe Lunar probe", ActionType.SKILL, Element.CRYO);
        lunar.setLunarConsidered(true);
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally, lunar),
                "Shenhe Quill rejects Lunar damage");
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally, null),
                "Shenhe Quill rejects a null action");

        for (int hit = 0; hit < 5; hit++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.CHONGYUN,
                    attackProbe("Shenhe quota probe " + hit,
                            ActionType.SKILL, Element.CRYO));
        }
        assertEquals(0, shenhe.getIcyQuillQuota(
                CharacterId.CHONGYUN, simulator.getCurrentTime()),
                "Shenhe ally quota consumes after five hits");
        assertClose(0.0, targetDependentBonus(
                shenhe, simulator, ally, skill),
                "Shenhe exhausted quota adds no damage");
    }

    private static void testBurstFieldTicksAndConstellations() {
        Shenhe c0 = new Shenhe(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(98.0 * FRAME, c0Simulator.getCurrentTime(),
                "Shenhe Burst animation duration");
        assertClose(767.0 * FRAME, c0.getBurstFieldExpirationTime(),
                "Shenhe C0 Burst field duration");
        assertClose(887.0 * FRAME, c0.getBurstShredExpirationTime(),
                "Shenhe Burst shred lingers two seconds");
        ActionRecord initial = named(c0Records,
                "Divine Maiden's Deliverance Initial").get(0);
        assertClose(78.0 * FRAME, initial.time,
                "Shenhe Burst initial hitmark");
        assertClose(1.7136, initial.action.getDamagePercent(),
                "Shenhe Burst initial multiplier");
        ActionRecord firstDot = named(c0Records,
                "Divine Maiden's Deliverance DoT").get(0);
        assertClose(82.0 * FRAME, firstDot.time,
                "Shenhe Burst first DoT hitmark");
        assertClose(0.56304, firstDot.action.getDamagePercent(),
                "Shenhe Burst DoT multiplier");
        assertEquals(ICDType.Standard, firstDot.action.getICDType(),
                "Shenhe Burst DoT uses Standard ICD");
        assertEquals(ICDTag.ElementalBurst, firstDot.action.getICDTag(),
                "Shenhe Burst DoT uses the Burst ICD tag");
        assertClose(1.0, firstDot.action.getGaugeUnits(),
                "Shenhe Burst DoT applies one Cryo gauge unit");
        assertTrue(firstDot.action.hasStatSnapshot(),
                "Shenhe Burst DoT owns a cast snapshot");
        assertClose(0.0, firstDot.action.getStatSnapshot().get(
                StatType.CRYO_DMG_BONUS),
                "Shenhe Burst DoT snapshots before A1 field bonus");
        assertClose(0.15, applicableStat(
                c0Simulator, c0, StatType.CRYO_DMG_BONUS),
                "Shenhe A1 applies inside the field");
        assertClose(0.14, applicableStat(
                c0Simulator, c0, StatType.CRYO_RES_SHRED),
                "Shenhe Burst applies Cryo shred");
        assertClose(0.14, applicableStat(
                c0Simulator, c0, StatType.PHYS_RES_SHRED),
                "Shenhe Burst applies Physical shred");
        advanceTo(c0Simulator, 702.0 * FRAME);
        assertEquals(12, named(c0Records,
                "Divine Maiden's Deliverance DoT").size(),
                "Shenhe C0 Burst resolves twelve DoT hits");
        assertClose(701.0 * FRAME, named(c0Records,
                "Divine Maiden's Deliverance DoT").get(11).time,
                "Shenhe C0 Burst final DoT hitmark");
        advanceTo(c0Simulator, 767.0 * FRAME);
        assertClose(0.0, applicableStat(
                c0Simulator, c0, StatType.CRYO_DMG_BONUS),
                "Shenhe A1 expires at the field boundary");
        assertClose(0.14, applicableStat(
                c0Simulator, c0, StatType.CRYO_RES_SHRED),
                "Shenhe shred remains during linger");
        advanceTo(c0Simulator, 887.0 * FRAME);
        assertClose(0.0, applicableStat(
                c0Simulator, c0, StatType.CRYO_RES_SHRED),
                "Shenhe shred expires after linger");

        Shenhe c2 = new Shenhe(null, null, 2);
        TestCharacter c2Ally = new TestCharacter(
                CharacterId.GANYU, Element.CRYO);
        CombatSimulator c2Simulator = simulatorWith(c2, c2Ally);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.BURST);
        assertClose(1127.0 * FRAME, c2.getBurstFieldExpirationTime(),
                "Shenhe C2 extends the field to eighteen seconds");
        assertClose(0.15, applicableStat(
                c2Simulator, c2, StatType.CRYO_CRIT_DMG),
                "Shenhe C2 grants active Cryo CRIT DMG");
        c2Simulator.switchCharacter(CharacterId.GANYU);
        assertClose(0.0, applicableStat(
                c2Simulator, c2, StatType.CRYO_DMG_BONUS),
                "Shenhe A1 does not remain on the inactive field owner");
        assertClose(0.0, applicableStat(
                c2Simulator, c2, StatType.CRYO_CRIT_DMG),
                "Shenhe C2 does not remain on the inactive field owner");
        assertClose(0.15, applicableStat(
                c2Simulator, c2Ally, StatType.CRYO_DMG_BONUS),
                "Shenhe A1 follows the active field recipient");
        assertClose(0.15, applicableStat(
                c2Simulator, c2Ally, StatType.CRYO_CRIT_DMG),
                "Shenhe C2 follows the active field recipient");
        advanceTo(c2Simulator, 1053.0 * FRAME);
        assertEquals(18, named(c2Records,
                "Divine Maiden's Deliverance DoT").size(),
                "Shenhe C2 resolves eighteen DoT hits");
        assertClose(1052.0 * FRAME, named(c2Records,
                "Divine Maiden's Deliverance DoT").get(17).time,
                "Shenhe C2 Burst final DoT hitmark");
        advanceTo(c2Simulator, 1127.0 * FRAME + EPSILON);
        assertClose(0.0, applicableStat(
                c2Simulator, c2, StatType.CRYO_CRIT_DMG),
                "Shenhe C2 CRIT field expires half-open");
    }

    private static void testC1C3C4C5AndC6() {
        Shenhe c1 = new Shenhe(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        performSkill(c1Simulator, SkillActionMode.PRESS);
        performSkill(c1Simulator, SkillActionMode.PRESS);
        assertEquals(2, named(c1Records,
                "Spring Spirit Summoning Press").size(),
                "Shenhe C1 provides a second Skill charge");

        Shenhe c1HoldFirst = new Shenhe(null, null, 1);
        CombatSimulator c1HoldSimulator = simulatorWith(c1HoldFirst);
        performSkill(c1HoldSimulator, SkillActionMode.HOLD);
        performSkill(c1HoldSimulator, SkillActionMode.PRESS);
        assertClose(15.0,
                c1HoldFirst.getChargeRestoreTimes().get(0),
                "Shenhe C1 Hold-first queue captures fifteen seconds");
        assertClose(16.3,
                c1HoldFirst.getChargeRestoreTimes().get(1),
                "Shenhe C1 mixed queue retains the active Hold cooldown");

        Shenhe c1PressFirst = new Shenhe(null, null, 1);
        CombatSimulator c1PressSimulator = simulatorWith(c1PressFirst);
        performSkill(c1PressSimulator, SkillActionMode.PRESS);
        performSkill(c1PressSimulator, SkillActionMode.HOLD);
        assertClose(10.0,
                c1PressFirst.getChargeRestoreTimes().get(0),
                "Shenhe C1 Press-first queue captures ten seconds");
        assertClose(10.0 + 38.0 * FRAME,
                c1PressFirst.getChargeRestoreTimes().get(1),
                "Shenhe C1 mixed queue retains the active Press cooldown");

        Shenhe c3 = new Shenhe(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator, SkillActionMode.PRESS);
        assertClose(2.784, named(c3Records,
                "Spring Spirit Summoning Press").get(0)
                        .action.getDamagePercent(),
                "Shenhe C3 routes Skill talent level 12");

        Shenhe c4 = new Shenhe(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        performSkill(c4Simulator, SkillActionMode.PRESS);
        assertEquals(1, c4.getC4StackCount(
                c4Simulator.getCurrentTime()),
                "Shenhe C4 gains a stack after Quill damage");
        performSkill(c4Simulator, SkillActionMode.PRESS);
        ActionRecord c4Second = named(c4Records,
                "Spring Spirit Summoning Press").get(1);
        assertClose(0.05, c4Second.action.getExtraBonuses().getOrDefault(
                StatType.SKILL_DMG_BONUS, 0.0),
                "Shenhe C4 consumes stacks into Skill DMG Bonus");
        assertEquals(1, c4.getC4StackCount(
                c4Simulator.getCurrentTime()),
                "Shenhe C4 gains the new Skill hit's Quill stack post-hit");
        advanceTo(c4Simulator,
                c4Simulator.getCurrentTime() + 60.0);
        assertEquals(0, c4.getC4StackCount(
                c4Simulator.getCurrentTime()),
                "Shenhe C4 stacks expire after sixty seconds");

        Shenhe c5 = new Shenhe(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(2.016, named(c5Records,
                "Divine Maiden's Deliverance Initial").get(0)
                        .action.getDamagePercent(),
                "Shenhe C5 routes Burst initial talent level 12");
        assertClose(0.6624, named(c5Records,
                "Divine Maiden's Deliverance DoT").get(0)
                        .action.getDamagePercent(),
                "Shenhe C5 routes Burst DoT talent level 12");
        assertClose(0.15, applicableStat(
                c5Simulator, c5, StatType.CRYO_RES_SHRED),
                "Shenhe C5 routes Burst shred talent level 12");

        Shenhe c6 = new Shenhe(null, null, 6);
        TestCharacter c6Ally = new TestCharacter(
                CharacterId.GANYU, Element.CRYO);
        CombatSimulator c6Simulator = simulatorWith(c6, c6Ally);
        performSkill(c6Simulator, SkillActionMode.PRESS);
        for (int hit = 0; hit < 60; hit++) {
            ActionType type = hit % 2 == 0
                    ? ActionType.NORMAL : ActionType.CHARGE;
            c6Simulator.performActionWithoutTimeAdvance(
                    CharacterId.GANYU,
                    attackProbe("Shenhe C6 free probe " + hit,
                            type, Element.CRYO));
        }
        assertEquals(5, c6.getIcyQuillQuota(
                CharacterId.GANYU, c6Simulator.getCurrentTime()),
                "Shenhe C6 Normal and Charged hits preserve quota");
        assertEquals(50, c6.getC4StackCount(
                c6Simulator.getCurrentTime()),
                "Shenhe C4 stack generation caps at fifty");
        c6Simulator.performActionWithoutTimeAdvance(
                CharacterId.GANYU,
                attackProbe("Shenhe C6 consuming Skill probe",
                        ActionType.SKILL, Element.CRYO));
        assertEquals(4, c6.getIcyQuillQuota(
                CharacterId.GANYU, c6Simulator.getCurrentTime()),
                "Shenhe C6 still consumes quota for Skill damage");
    }

    private static void testSnapshotRestore() {
        Shenhe shenhe = new Shenhe(null, null, 1);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(shenhe, ally);
        performSkill(simulator, SkillActionMode.PRESS);
        SimulatorSnapshot quillSnapshot = simulator.saveSnapshot();
        simulator.performActionWithoutTimeAdvance(
                CharacterId.QIQI,
                attackProbe("Shenhe restore quota probe",
                        ActionType.SKILL, Element.CRYO));
        assertEquals(4, shenhe.getIcyQuillQuota(
                CharacterId.QIQI, simulator.getCurrentTime()),
                "Shenhe branch consumes one ally Quill");
        simulator.restoreSnapshot(quillSnapshot);
        simulator.restoreSnapshot(quillSnapshot);
        assertEquals(5, shenhe.getIcyQuillQuota(
                CharacterId.QIQI, simulator.getCurrentTime()),
                "Shenhe repeated restore recovers ally Quill exactly");

        Shenhe burst = new Shenhe(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> records = captureActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot pending = burstSimulator.saveSnapshot();
        records.clear();
        advanceTo(burstSimulator, 250.0 * FRAME);
        int branchTicks = named(records,
                "Divine Maiden's Deliverance DoT").size();
        assertEquals(3, branchTicks,
                "Shenhe branch resolves pending DoT events once");
        burstSimulator.restoreSnapshot(pending);
        burstSimulator.restoreSnapshot(pending);
        records.clear();
        advanceTo(burstSimulator, 250.0 * FRAME);
        assertEquals(branchTicks, named(records,
                "Divine Maiden's Deliverance DoT").size(),
                "Shenhe repeated restore reconstructs pending DoTs once");
    }

    private static void testInvalidInputsEnergyAndIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Shenhe(null, null, -1),
                "Shenhe rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Shenhe(null, null, 7),
                "Shenhe rejects constellation above six");
        Shenhe invalid = new Shenhe(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Shenhe rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Shenhe rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Shenhe rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.PLUNGE),
                "Shenhe rejects excluded Plunge timing");

        Shenhe insufficient = new Shenhe(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Shenhe insufficient Energy rejects Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Shenhe records rejected Burst Energy");
        assertClose(Double.NEGATIVE_INFINITY,
                insufficient.getBurstFieldExpirationTime(),
                "Shenhe rejected Burst starts no field");

        Shenhe noTarget = new Shenhe(null, null, 0);
        CombatSimulator noTargetSimulator = simulatorWith(noTarget);
        performSkill(noTargetSimulator, SkillActionMode.PRESS);
        StatsContainer noTargetStats = new StatsContainer();
        noTarget.applyTargetDependentTeamStats(
                noTargetStats,
                noTarget,
                null,
                attackProbe("Shenhe no-target probe",
                        ActionType.SKILL, Element.CRYO),
                noTargetSimulator.getCurrentTime());
        assertClose(0.0, noTargetStats.get(StatType.FLAT_DMG_BONUS),
                "Shenhe Quill ignores a null target");

        Shenhe reused = new Shenhe(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Shenhe rejects cross-simulator reuse");
        Shenhe owner = new Shenhe(null, null, 0);
        Shenhe foreign = new Shenhe(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Shenhe rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, new CombatSimulator()),
                "Shenhe rejects a foreign state type");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
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
                CharacterId.SHENHE, CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.SHENHE, CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(actor, action, damage, time)));
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
            if (record.actor.getCharacterId() == CharacterId.SHENHE
                    && record.action.getName().equals(name)) {
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
            Shenhe shenhe,
            CombatSimulator simulator,
            Character attacker,
            AttackAction action) {
        StatsContainer stats = new StatsContainer();
        shenhe.applyTargetDependentTeamStats(
                stats,
                attacker,
                simulator.getEnemy(),
                action,
                simulator.getCurrentTime());
        return stats.get(StatType.FLAT_DMG_BONUS);
    }

    private static double liveAttack(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.getTotalAtk();
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
            assertTrue(lines.get(index).startsWith("Shenhe,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String path,
            String key,
            double expected) throws IOException {
        for (String line : Files.readAllLines(Path.of(path))) {
            String[] columns = line.split(",", -1);
            if (columns.length == 6 && columns[2].equals(key)) {
                assertClose(expected, Double.parseDouble(columns[4]),
                        path + " value for " + key);
                return;
            }
        }
        throw new AssertionError(path + " missing key " + key);
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
        private final Character actor;
        private final AttackAction action;
        @SuppressWarnings("unused")
        private final double damage;
        private final double time;

        private ActionRecord(
                Character actor,
                AttackAction action,
                double damage,
                double time) {
            this.actor = actor;
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
