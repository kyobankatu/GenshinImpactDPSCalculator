package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import model.character.Diluc;
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
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Diluc's stationary offensive slice. */
public final class DilucRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private DilucRegressionTest() {
    }

    /** Runs identity, action, chain, Burst, constellation, and boundary checks. */
    public static void main(String[] args) {
        testIdentityStatsAndConstruction();
        testNormalSequenceTimingAndPlunge();
        testSkillChainCooldownParticlesAndExpiry();
        testC3AndC4SkillWindows();
        testBurstCadenceGaugeInfusionAndC5();
        testC6NormalEnhancementAndComboPreservation();
        testUnsupportedInsufficientEnergyAndSwitch();
        testIndependentInstancesAndSimulatorBinding();
        System.out.println("DilucRegressionTest passed");
    }

    private static void testIdentityStatsAndConstruction() {
        Diluc diluc = new Diluc(null, null);
        assertEquals(6, diluc.getConstellation(),
                "Diluc default constructor uses C6");
        assertEquals(CharacterId.DILUC, diluc.getCharacterId(),
                "Diluc typed identity");
        assertEquals(CharacterId.DILUC, CharacterId.fromName("Diluc"),
                "Diluc display lookup");
        assertEquals(CharacterId.DILUC, CharacterId.fromNumericId(22),
                "Diluc numeric lookup");
        assertEquals(Element.PYRO, diluc.getElement(),
                "Diluc element");
        assertClose(12981.0,
                diluc.getBaseStats().get(StatType.BASE_HP), EPS,
                "Diluc base HP");
        assertClose(335.0,
                diluc.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Diluc base ATK");
        assertClose(784.0,
                diluc.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Diluc base DEF");
        assertClose(0.242,
                diluc.getBaseStats().get(StatType.CRIT_RATE), EPS,
                "Diluc total base CRIT Rate");
        assertClose(40.0, diluc.getEnergyCost(), EPS,
                "Diluc Energy cost");

        TalentDataSource customData = (character, key, defaultValue) ->
                "Base ATK".equals(key) ? 401.0 : defaultValue;
        Diluc injected = new Diluc(null, null, customData, 2);
        assertEquals(2, injected.getConstellation(),
                "Diluc injectable constellation");
        assertClose(401.0,
                injected.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Diluc injectable talent data");

        assertThrows(IllegalArgumentException.class,
                () -> dilucAtConstellation(-1),
                "Diluc negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> dilucAtConstellation(7),
                "Diluc constellation above six");
    }

    private static void testNormalSequenceTimingAndPlunge() {
        Diluc diluc = dilucAtConstellation(0);
        CombatSimulator sim = simulatorWith(diluc);
        List<ActionRecord> records = captureDilucActions(sim);
        double[] multipliers = {
                1.64794, 1.61002, 1.81542, 2.46164
        };
        int[] hitFrames = { 24, 39, 26, 49 };
        int[] actionFrames = { 42, 56, 44, 111 };
        int[] hitlagFrames = { 10, 9, 9, 11 };

        double castTime = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(sim, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertEquals("Tempered Sword N" + (step + 1),
                    record.action.getName(),
                    "Diluc Normal name");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(), EPS,
                    "Diluc Normal multiplier");
            assertClose(castTime + hitFrames[step] * FRAME,
                    record.time, EPS,
                    "Diluc Normal hitmark");
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Diluc Normal physical element");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Diluc Normal category");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Diluc Normal standard ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Diluc Normal typed ICD tag");
            assertClose(0.0, record.action.getGaugeUnits(), EPS,
                    "Diluc physical Normal has no gauge");
            assertTrue(record.action.isShatterTrigger(),
                    "Diluc claymore Normal is blunt");
            castTime += (actionFrames[step] + hitlagFrames[step]) * FRAME;
            assertClose(castTime, sim.getCurrentTime(), EPS,
                    "Diluc Normal action duration");
        }

        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Tempered Sword N1", records.get(4).action.getName(),
                "Diluc Normal sequence wraps after N4");

        records.clear();
        double plungeCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Diluc high Plunge count");
        assertClose(plungeCast + 58.0 * FRAME,
                records.get(0).time, EPS,
                "Diluc high Plunge hitmark");
        assertClose(4.10702,
                records.get(0).action.getDamagePercent(), EPS,
                "Diluc high Plunge multiplier");
        assertEquals(Element.PHYSICAL,
                records.get(0).action.getElement(),
                "Diluc physical high Plunge");
    }

    private static void testSkillChainCooldownParticlesAndExpiry() {
        Diluc diluc = dilucAtConstellation(0);
        CombatSimulator sim = simulatorWith(diluc);
        List<ActionRecord> skills = captureNamedActions(
                sim, "Searing Onslaught");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, skills.size(), "Diluc first Skill hit");
        assertClose(24.0 * FRAME, skills.get(0).time, EPS,
                "Diluc first Skill hitmark");
        assertClose((43.0 + 11.0) * FRAME,
                sim.getCurrentTime(), EPS,
                "Diluc first Skill duration");
        assertEquals(1, diluc.getSkillStage(sim.getCurrentTime()),
                "Diluc exposes second Skill stage");
        assertClose(0.0,
                diluc.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Diluc chain bypasses gateway cooldown");
        assertClose(4.0, diluc.getSkillChainDeadline(), EPS,
                "Diluc first continuation deadline");
        assertSkill(skills.get(0).action, 1.6048,
                "Diluc first Skill");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(2, skills.size(), "Diluc second Skill hit");
        assertClose((43.0 + 11.0 + 28.0) * FRAME,
                skills.get(1).time, EPS,
                "Diluc second Skill hitmark");
        assertSkill(skills.get(1).action, 1.6592,
                "Diluc second Skill");
        assertEquals(2, diluc.getSkillStage(sim.getCurrentTime()),
                "Diluc exposes third Skill stage");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(3, skills.size(), "Diluc third Skill hit");
        assertClose((43.0 + 11.0 + 49.0 + 11.0 + 46.0) * FRAME,
                skills.get(2).time, EPS,
                "Diluc third Skill hitmark");
        assertSkill(skills.get(2).action, 2.1896,
                "Diluc third Skill");
        assertEquals(0, diluc.getSkillStage(sim.getCurrentTime()),
                "Diluc third Skill closes chain");
        assertClose((163.0 + 36.0) * FRAME,
                sim.getCurrentTime(), EPS,
                "Diluc three-stage action duration");
        assertClose(10.0 - (163.0 + 36.0) * FRAME,
                diluc.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Diluc cooldown remains anchored to first cast");
        assertClose(7.5, diluc.getTotalParticleEnergy(), EPS,
                "Diluc first two deterministic particle packets arrive");

        advanceTo(sim, 260.0 * FRAME + EPS);
        assertClose(11.25, diluc.getTotalParticleEnergy(), EPS,
                "Diluc all Skill particles arrive after 100 frames");

        double beforeGateway = sim.getCurrentTime();
        perform(sim, CharacterActionKey.SKILL);
        assertTrue(beforeGateway < 10.0,
                "Diluc follow-up request starts before cooldown end");
        assertClose(10.0 + (43.0 + 11.0) * FRAME,
                sim.getCurrentTime(), EPS,
                "Diluc gateway waits to first-cast cooldown end");
        assertClose(10.0 + 24.0 * FRAME,
                skills.get(3).time, EPS,
                "Diluc fresh chain begins after cooldown wait");

        Diluc expired = dilucAtConstellation(0);
        CombatSimulator expiredSim = simulatorWith(expired);
        List<ActionRecord> expiredSkills = captureNamedActions(
                expiredSim, "Searing Onslaught");
        perform(expiredSim, CharacterActionKey.SKILL);
        advanceTo(expiredSim, 4.0);
        perform(expiredSim, CharacterActionKey.SKILL);
        assertEquals("Searing Onslaught 1",
                expiredSkills.get(1).action.getName(),
                "Diluc exact four-second boundary starts new chain");
        assertClose(10.0 + 24.0 * FRAME,
                expiredSkills.get(1).time, EPS,
                "Diluc expired chain observes original cooldown");
    }

    private static void testC3AndC4SkillWindows() {
        Diluc c3 = dilucAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Skills = captureNamedActions(
                c3Sim, "Searing Onslaught");
        perform(c3Sim, CharacterActionKey.SKILL);
        perform(c3Sim, CharacterActionKey.SKILL);
        perform(c3Sim, CharacterActionKey.SKILL);
        double[] expected = { 1.888, 1.952, 2.576 };
        for (int i = 0; i < expected.length; i++) {
            assertClose(expected[i],
                    c3Skills.get(i).action.getDamagePercent(), EPS,
                    "Diluc C3 Skill talent level");
        }
        double unbuffedSecondSkillDamage = c3Skills.get(1).damage;

        Diluc c4 = dilucAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        List<ActionRecord> c4Skills = captureNamedActions(
                c4Sim, "Searing Onslaught");
        perform(c4Sim, CharacterActionKey.SKILL);
        advanceTo(c4Sim, 2.0);
        assertTrue(hasActiveBuff(c4, BuffId.DILUC_C4_SKILL_DMG_BONUS, 2.0),
                "Diluc C4 lower boundary exposes typed buff marker");
        perform(c4Sim, CharacterActionKey.SKILL);
        assertClose(0.0,
                c4Skills.get(1).action.getExtraBonuses().getOrDefault(
                        StatType.SKILL_DMG_BONUS, 0.0),
                EPS, "Diluc C4 has no duplicate action-local bonus");
        assertClose(1.40,
                c4Skills.get(1).damage / unbuffedSecondSkillDamage,
                EPS, "Diluc C4 applies exactly 40 percent more damage");

        Diluc early = dilucAtConstellation(4);
        CombatSimulator earlySim = simulatorWith(early);
        List<ActionRecord> earlySkills = captureNamedActions(
                earlySim, "Searing Onslaught");
        perform(earlySim, CharacterActionKey.SKILL);
        advanceTo(earlySim, 2.0 - 0.001);
        assertTrue(!hasActiveBuff(
                        early,
                        BuffId.DILUC_C4_SKILL_DMG_BONUS,
                        earlySim.getCurrentTime()),
                "Diluc C4 typed marker is absent before two seconds");
        perform(earlySim, CharacterActionKey.SKILL);
        assertClose(0.0,
                earlySkills.get(1).action.getExtraBonuses().getOrDefault(
                        StatType.SKILL_DMG_BONUS, 0.0),
                EPS, "Diluc C4 rejects cast before two seconds");

        Diluc upper = dilucAtConstellation(4);
        CombatSimulator upperSim = simulatorWith(upper);
        List<ActionRecord> upperSkills = captureNamedActions(
                upperSim, "Searing Onslaught");
        perform(upperSim, CharacterActionKey.SKILL);
        advanceTo(upperSim, 4.0);
        assertTrue(!hasActiveBuff(
                        upper,
                        BuffId.DILUC_C4_SKILL_DMG_BONUS,
                        upperSim.getCurrentTime()),
                "Diluc C4 typed marker expires at four seconds");
        perform(upperSim, CharacterActionKey.SKILL);
        assertEquals("Searing Onslaught 1",
                upperSkills.get(1).action.getName(),
                "Diluc C4 upper boundary is a fresh chain");
        assertClose(0.0,
                upperSkills.get(1).action.getExtraBonuses().getOrDefault(
                        StatType.SKILL_DMG_BONUS, 0.0),
                EPS, "Diluc C4 excludes exact four-second boundary");
    }

    private static void testBurstCadenceGaugeInfusionAndC5() {
        Diluc c0 = dilucAtConstellation(0);
        c0.addBuff(new SimpleBuff(
                "Frame-100 ATK",
                20.0 * FRAME,
                90.0 * FRAME,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator sim = simulatorWith(c0);
        c0.restoreCurrentEnergy(40.0);
        List<ActionRecord> burst = captureNamedActions(sim, "Dawn");

        perform(sim, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(), EPS,
                "Diluc Dawn consumes 40 Energy");
        assertClose((140.0 + 9.0) * FRAME,
                sim.getCurrentTime(), EPS,
                "Diluc Dawn action duration");
        assertEquals(5, burst.size(),
                "Diluc first five Dawn hits resolve during extended animation");
        assertTrue(c0.isPyroInfusionActive(sim.getCurrentTime()),
                "Diluc Dawn infusion active after cast");
        assertClose(12.0, c0.getInfusionExpiresAt(), EPS,
                "Diluc A4 extends infusion to twelve seconds");
        assertClose(0.20,
                effectiveStats(sim, c0).get(StatType.PYRO_DMG_BONUS), EPS,
                "Diluc A4 Pyro bonus");
        assertClose(12.0 - (140.0 + 9.0) * FRAME,
                c0.getBurstCDRemaining(sim.getCurrentTime()), EPS,
                "Diluc Burst cooldown starts on cast");

        advanceTo(sim, 202.0 * FRAME);
        assertEquals(10, burst.size(), "Diluc Dawn total hit count");
        int[] hitFrames = {
                100, 112, 124, 136, 148, 160, 172, 184, 196, 202
        };
        for (int i = 0; i < burst.size(); i++) {
            ActionRecord record = burst.get(i);
            assertClose(hitFrames[i] * FRAME, record.time, EPS,
                    "Diluc Dawn hit cadence");
            assertEquals(ActionType.BURST,
                    record.action.getActionType(),
                    "Diluc Dawn Burst category");
            assertEquals(ICDType.None,
                    record.action.getICDType(),
                    "Diluc Dawn local application encoding");
            assertEquals(ICDTag.ElementalBurst,
                    record.action.getICDTag(),
                    "Diluc Dawn typed Burst tag");
            assertClose(i == 0 || i == 5 ? 2.0 : 0.0,
                    record.action.getGaugeUnits(), EPS,
                    "Diluc Dawn five-hit application sequence");
        }
        assertClose(3.468,
                burst.get(0).action.getDamagePercent(), EPS,
                "Diluc Dawn initial multiplier");
        for (int i = 1; i <= 8; i++) {
            assertClose(1.02,
                    burst.get(i).action.getDamagePercent(), EPS,
                    "Diluc Dawn DoT multiplier");
        }
        assertClose(3.468,
                burst.get(9).action.getDamagePercent(), EPS,
                "Diluc Dawn explosion multiplier");
        assertTrue(!burst.get(0).action.hasStatSnapshot(),
                "Diluc Dawn initial strike resolves dynamically");
        assertClose(1.0,
                burst.get(1).action.getStatSnapshot().get(
                        StatType.ATK_PERCENT), EPS,
                "Diluc Dawn follow-ups keep the frame-100 snapshot");

        List<ActionRecord> normals = captureNamedActions(
                sim, "Tempered Sword");
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(Element.PYRO,
                normals.get(0).action.getElement(),
                "Diluc Dawn infuses Normal Attack");
        assertClose(1.0,
                normals.get(0).action.getGaugeUnits(), EPS,
                "Diluc infused Normal applies 1U");
        advanceTo(sim, 12.0);
        assertTrue(!c0.isPyroInfusionActive(sim.getCurrentTime()),
                "Diluc infusion expires at exact A4 boundary");
        assertClose(0.0,
                effectiveStats(sim, c0).get(StatType.PYRO_DMG_BONUS), EPS,
                "Diluc A4 bonus expires with infusion");
        normals.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(Element.PHYSICAL,
                normals.get(0).action.getElement(),
                "Diluc Normal returns to Physical after infusion");

        Diluc c5 = dilucAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        c5.restoreCurrentEnergy(40.0);
        List<ActionRecord> c5Burst = captureNamedActions(c5Sim, "Dawn");
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, 202.0 * FRAME);
        assertClose(4.08,
                c5Burst.get(0).action.getDamagePercent(), EPS,
                "Diluc C5 initial multiplier");
        assertClose(1.20,
                c5Burst.get(1).action.getDamagePercent(), EPS,
                "Diluc C5 DoT multiplier");
        assertClose(4.08,
                c5Burst.get(9).action.getDamagePercent(), EPS,
                "Diluc C5 explosion multiplier");
    }

    private static void testC6NormalEnhancementAndComboPreservation() {
        Diluc c6 = dilucAtConstellation(6);
        CombatSimulator sim = simulatorWith(c6);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Tempered Sword");

        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Tempered Sword N1", normals.get(0).action.getName(),
                "Diluc C6 setup Normal");
        double skillCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.SKILL);
        assertEquals(2,
                c6.getC6NormalUsesRemaining(sim.getCurrentTime()),
                "Diluc C6 grants two Normal uses");

        double firstCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord first = normals.get(1);
        assertEquals("Tempered Sword N2", first.action.getName(),
                "Diluc C6 Skill preserves Normal combo");
        assertClose(firstCast + 39.0 * FRAME / 1.30,
                first.time, EPS,
                "Diluc C6 accelerates first Normal hitmark");
        assertClose(0.30,
                first.action.getExtraBonuses().getOrDefault(
                        StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                EPS, "Diluc C6 first Normal damage bonus");
        assertEquals(1,
                c6.getC6NormalUsesRemaining(sim.getCurrentTime()),
                "Diluc C6 consumes first Normal use");

        double secondCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord second = normals.get(2);
        assertEquals("Tempered Sword N3", second.action.getName(),
                "Diluc C6 continues Normal combo");
        assertClose(secondCast + 26.0 * FRAME / 1.30,
                second.time, EPS,
                "Diluc C6 accelerates second Normal hitmark");
        assertClose(0.30,
                second.action.getExtraBonuses().getOrDefault(
                        StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                EPS, "Diluc C6 second Normal damage bonus");
        assertEquals(0,
                c6.getC6NormalUsesRemaining(sim.getCurrentTime()),
                "Diluc C6 consumes second Normal use");

        double thirdCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord third = normals.get(3);
        assertEquals("Tempered Sword N4", third.action.getName(),
                "Diluc C6 third Normal remains in combo");
        assertClose(thirdCast + 49.0 * FRAME,
                third.time, EPS,
                "Diluc C6 third Normal has base hitmark");
        assertClose(0.0,
                third.action.getExtraBonuses().getOrDefault(
                        StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                EPS, "Diluc C6 only buffs two Normals");

        assertTrue(sim.getCurrentTime() < skillCast + 6.0,
                "Diluc C6 uses were consumed before expiry");
    }

    private static void testUnsupportedInsufficientEnergyAndSwitch() {
        Diluc diluc = dilucAtConstellation(0);
        CombatSimulator sim = simulatorWith(diluc);
        List<ActionRecord> records = captureDilucActions(sim);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.CHARGE),
                "Diluc Charged Attack timing boundary");
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Diluc unsupported Dash action");

        diluc.restoreCurrentEnergy(0.0);
        perform(sim, CharacterActionKey.BURST);
        assertEquals(0, records.size(),
                "Diluc insufficient-Energy Burst produces no hits");
        assertClose(0.0, sim.getCurrentTime(), EPS,
                "Diluc skipped Burst consumes no time");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, diluc.getSkillStage(sim.getCurrentTime()),
                "Diluc Skill chain active before switch");
        diluc.onSwitchOut(sim);
        assertEquals(1, diluc.getSkillStage(sim.getCurrentTime()),
                "Diluc switch preserves Skill chain");
        assertClose(0.0,
                diluc.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Diluc switch keeps the next Skill stage available");
        perform(sim, CharacterActionKey.SKILL);
        assertEquals("Searing Onslaught 2",
                records.get(records.size() - 1).action.getName(),
                "Diluc resumes the second Skill stage after switching");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Diluc first = dilucAtConstellation(6);
        Diluc second = dilucAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        perform(firstSim, CharacterActionKey.SKILL);
        assertEquals(2,
                first.getC6NormalUsesRemaining(firstSim.getCurrentTime()),
                "First Diluc owns C6 state");
        assertEquals(0,
                second.getC6NormalUsesRemaining(secondSim.getCurrentTime()),
                "Second Diluc remains independent");
        assertEquals(1, first.getSkillStage(firstSim.getCurrentTime()),
                "First Diluc owns Skill stage");
        assertEquals(0, second.getSkillStage(secondSim.getCurrentTime()),
                "Second Diluc Skill stage remains clean");

        first.initializeForSimulator(firstSim);
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(first),
                "Diluc rejects cross-simulator reuse");
    }

    private static void assertSkill(
            AttackAction action,
            double multiplier,
            String message) {
        assertClose(multiplier, action.getDamagePercent(), EPS,
                message + " multiplier");
        assertEquals(Element.PYRO, action.getElement(),
                message + " element");
        assertEquals(ActionType.SKILL, action.getActionType(),
                message + " category");
        assertEquals(ICDType.None, action.getICDType(),
                message + " ICD");
        assertEquals(ICDTag.ElementalSkill, action.getICDTag(),
                message + " ICD tag");
        assertClose(1.0, action.getGaugeUnits(), EPS,
                message + " gauge");
        assertTrue(action.isShatterTrigger(),
                message + " blunt classification");
    }

    private static Diluc dilucAtConstellation(int constellation) {
        return new Diluc(null, null, constellation);
    }

    private static CombatSimulator simulatorWith(Diluc diluc) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(diluc);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.DILUC,
                CharacterActionRequest.of(key));
    }

    private static void advanceTo(CombatSimulator sim, double time) {
        double delta = time - sim.getCurrentTime();
        if (delta < -EPS) {
            throw new AssertionError(
                    "Cannot move simulation backwards from "
                            + sim.getCurrentTime() + " to " + time);
        }
        if (delta > 0.0) {
            sim.advanceTime(delta);
        }
    }

    private static StatsContainer effectiveStats(
            CombatSimulator sim,
            Character character) {
        StatsContainer stats = character.getEffectiveStats(
                sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
    }

    private static boolean hasActiveBuff(
            Character character,
            BuffId id,
            double currentTime) {
        for (Buff buff : character.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    private static List<ActionRecord> captureDilucActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DILUC) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String prefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DILUC
                    && action.getName().startsWith(prefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
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
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
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
}
