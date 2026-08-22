package sample;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.character.Razor;
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

/**
 * Focused regression executable for Razor's offensive vertical slice.
 */
public final class RazorRegressionTest {
    private static final double EPS = 1e-9;
    private static final double FRAME = 1.0 / 60.0;

    private RazorRegressionTest() {
    }

    /**
     * Runs Razor data, action, timing, state, passive, and constellation checks.
     *
     * @param args ignored command-line arguments
     * @throws IOException if configured CSV files cannot be read
     */
    public static void main(String[] args) throws IOException {
        testIdentityStatsAndCsvAlignment();
        testNormalChargedAndPlungeActions();
        testPressSkillTimingParticlesAndSigils();
        testBurstEnergyFormEchoAndSwitchExpiry();
        testA1ResetAndDynamicA4();
        testC1ActiveFieldParticleWindow();
        testConstellationTalentLevelsAndC6();
        testTypedActionAndUnsupportedBoundaries();
        testIndependentInstancesAndSimulatorBinding();
        testInvalidConstellation();
        System.out.println("Razor regression checks passed.");
    }

    private static void testIdentityStatsAndCsvAlignment()
            throws IOException {
        Razor razor = new Razor(null, null);
        assertEquals(CharacterId.RAZOR, razor.getCharacterId(),
                "Razor typed identity");
        assertEquals(CharacterId.RAZOR, CharacterId.fromName("Razor"),
                "Razor display-name lookup");
        assertEquals(CharacterId.RAZOR, CharacterId.fromNumericId(14),
                "Razor numeric-id lookup");
        assertClose(11962.0,
                razor.getBaseStats().get(StatType.BASE_HP), EPS,
                "Razor level-90 base HP");
        assertClose(234.0,
                razor.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Razor level-90 base ATK");
        assertClose(751.0,
                razor.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Razor level-90 base DEF");
        assertClose(0.30,
                razor.getBaseStats().get(StatType.PHYSICAL_DMG_BONUS),
                EPS,
                "Razor ascension Physical DMG");
        assertClose(80.0, razor.getEnergyCost(), EPS,
                "Razor Burst Energy cost");
        assertClose(4.92, razor.getSkillCD(), EPS,
                "Razor A1 Press Skill cooldown");
        assertClose(20.0, razor.getBurstCD(), EPS,
                "Razor Burst cooldown");

        assertCsvShape(Path.of(
                "config/characters/Razor/Razor_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Razor/Razor_Multipliers.csv"), 20);

        TalentDataManager talentData = TalentDataManager.getInstance();
        assertClose(1.6132,
                talentData.get("Razor", "N1", -1.0), EPS,
                "Razor configured N1 multiplier");
        assertClose(3.9840,
                talentData.get(
                        "Razor", "Claw and Thunder Press", -1.0),
                EPS,
                "Razor configured C5 Press multiplier");
        assertClose(5.9040,
                talentData.get(
                        "Razor", "Claw and Thunder Hold", -1.0),
                EPS,
                "Razor retained Hold static multiplier");
        assertClose(3.20,
                talentData.get("Razor", "Lightning Fang", -1.0), EPS,
                "Razor configured C3 Burst multiplier");
        assertClose(0.48,
                talentData.get(
                        "Razor", "Lightning Fang Echo", -1.0),
                EPS,
                "Razor configured C3 Echo coefficient");
    }

    private static void testNormalChargedAndPlungeActions() {
        Razor razor = razorAtConstellation(0);
        CombatSimulator sim = simulatorWith(razor);
        List<ActionRecord> records = captureRazorActions(sim);

        for (int i = 0; i < 4; i++) {
            perform(sim, CharacterActionKey.NORMAL);
        }

        double[] multipliers = {
                1.6132, 1.38972, 1.73752, 2.28808
        };
        double[] durations = {
                54.0 / 60.0,
                43.0 / 60.0,
                57.0 / 60.0,
                129.0 / 60.0
        };
        int[] hitlagFrames = { 10, 10, 10, 13 };
        double[] haltTimes = { 0.10, 0.10, 0.10, 0.15 };
        double[] factors = { 0.01, 0.01, 0.05, 0.01 };
        double expectedTime = 0.0;
        assertEquals(4, records.size(),
                "Razor four-hit Normal action count");
        for (int i = 0; i < records.size(); i++) {
            ActionRecord record = records.get(i);
            AttackAction action = record.action;
            assertClose(expectedTime, record.time, EPS,
                    "Razor N" + (i + 1) + " resolution time");
            assertClose(multipliers[i], action.getDamagePercent(), EPS,
                    "Razor N" + (i + 1) + " multiplier");
            assertClose(durations[i], action.getAnimationDuration(), EPS,
                    "Razor N" + (i + 1) + " action interval");
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Razor Normal action type");
            assertEquals(Element.PHYSICAL, action.getElement(),
                    "Razor Normal element");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Razor Normal ICD type");
            assertEquals(ICDTag.NormalAttack, action.getICDTag(),
                    "Razor Normal ICD tag");
            assertClose(0.0, action.getGaugeUnits(), EPS,
                    "Razor Physical Normal gauge");
            assertTrue(action.isShatterTrigger(),
                    "Razor Normal should be blunt");
            assertClose(haltTimes[i],
                    action.getHitlagProfile().getHaltTimeSeconds(), EPS,
                    "Razor N" + (i + 1) + " hitlag halt time");
            assertClose(factors[i],
                    action.getHitlagProfile().getFactor(), EPS,
                    "Razor N" + (i + 1) + " hitlag factor");
            assertTrue(action.getHitlagProfile().canDefenseHalt(),
                    "Razor Normal permits Defense Halt");
            expectedTime += durations[i] + hitlagFrames[i] * FRAME;
        }

        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Steel Fang N1", records.get(0).action.getName(),
                "Razor Normal chain wrap");

        records.clear();
        perform(sim, CharacterActionKey.CHARGE);
        AttackAction charged = records.get(0).action;
        assertClose(1.1490, charged.getDamagePercent(), EPS,
                "Razor Charged cyclic multiplier");
        assertClose(23.0 / 60.0,
                charged.getAnimationDuration(), EPS,
                "Razor steady-state Charged interval");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Razor Charged action type");
        assertEquals(Element.PHYSICAL, charged.getElement(),
                "Razor Charged element");
        assertTrue(charged.isShatterTrigger(),
                "Razor Charged should be blunt");

        records.clear();
        perform(sim, CharacterActionKey.PLUNGE);
        AttackAction plunge = records.get(0).action;
        assertClose(3.764769, plunge.getDamagePercent(), EPS,
                "Razor high Plunge multiplier");
        assertClose(58.0 / 60.0,
                plunge.getAnimationDuration(), EPS,
                "Razor high Plunge action interval");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Razor Plunge action type");
        assertEquals(ICDType.None, plunge.getICDType(),
                "Razor Physical Plunge ICD type");
        assertEquals(ICDTag.None, plunge.getICDTag(),
                "Razor Physical Plunge ICD tag");
        assertClose(0.0, plunge.getGaugeUnits(), EPS,
                "Razor Physical Plunge gauge");
    }

    private static void testPressSkillTimingParticlesAndSigils() {
        Razor razor = razorAtConstellation(0);
        CombatSimulator sim = simulatorWith(razor);
        razor.spendEnergy(80.0);
        List<ActionRecord> pressHits = captureNamedActions(
                sim, "Claw and Thunder Press");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, pressHits.size(),
                "Razor Press should emit one hit");
        AttackAction press = pressHits.get(0).action;
        assertClose(32.0 / 60.0, pressHits.get(0).time, EPS,
                "Razor Press hitmark outside Burst");
        assertClose(3.3864, press.getDamagePercent(), EPS,
                "Razor C0 Press talent-9 multiplier");
        assertEquals(ActionType.SKILL, press.getActionType(),
                "Razor Press action type");
        assertEquals(Element.ELECTRO, press.getElement(),
                "Razor Press element");
        assertEquals(ICDType.None, press.getICDType(),
                "Razor Press no ICD");
        assertEquals(ICDTag.ElementalSkill, press.getICDTag(),
                "Razor Press ICD tag");
        assertClose(2.0, press.getGaugeUnits(), EPS,
                "Razor Press gauge");
        assertClose((80.0 + 10.0) * FRAME,
                sim.getCurrentTime(), EPS,
                "Razor Press action interval outside Burst");
        assertClose(30.0 / 60.0 + 4.92,
                razor.getSkillCooldownEndTime(), EPS,
                "Razor Press delayed cooldown start");
        assertEquals(1, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Press first Sigil");
        assertClose(9.0, razor.getTotalParticleEnergy(), EPS,
                "Razor Press three same-element particles before ER");
        assertClose(13.5, razor.getTotalScaledParticleEnergy(), EPS,
                "Razor Press particles include Sigil and low-Energy ER");
        assertClose(1.50,
                razor.getEffectiveStats(sim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                EPS,
                "Razor Sigil plus A4 Energy Recharge");

        double secondCast = razor.getSkillCooldownEndTime();
        perform(sim, CharacterActionKey.SKILL);
        assertClose(secondCast + 32.0 / 60.0,
                pressHits.get(1).time, EPS,
                "Razor second Press waits for cooldown");
        assertEquals(2, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Press second Sigil");

        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.SKILL);
        assertEquals(3, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Sigil cap");
        double expiration = pressHits.get(3).time + 18.0;
        sim.advanceTime(expiration - sim.getCurrentTime() - 0.001);
        assertEquals(3, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Sigil half-open pre-expiry boundary");
        sim.advanceTime(0.002);
        assertEquals(0, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Sigil expiry boundary");

        Razor burstRazor = razorAtConstellation(0);
        CombatSimulator burstSim = simulatorWith(burstRazor);
        perform(burstSim, CharacterActionKey.BURST);
        double particleBase = burstRazor.getTotalParticleEnergy();
        double burstSkillStart = burstSim.getCurrentTime();
        List<ActionRecord> burstPressHits = captureNamedActions(
                burstSim, "Claw and Thunder Press");
        perform(burstSim, CharacterActionKey.SKILL);
        assertClose(burstSkillStart + 33.0 / 60.0,
                burstPressHits.get(0).time, EPS,
                "Razor Press Burst-form hitmark");
        assertClose(burstSkillStart + (85.0 + 10.0) * FRAME,
                burstSim.getCurrentTime(), EPS,
                "Razor Press Burst-form action interval");
        assertClose(particleBase,
                burstRazor.getTotalParticleEnergy(), EPS,
                "Razor Press particles disabled during Lightning Fang");
        assertEquals(1,
                burstRazor.getElectroSigilCount(
                        burstSim.getCurrentTime()),
                "Razor Press still grants a Sigil during Lightning Fang");
    }

    private static void testBurstEnergyFormEchoAndSwitchExpiry() {
        Razor razor = razorAtConstellation(0);
        CombatSimulator sim = simulatorWith(razor);
        for (int i = 0; i < 3; i++) {
            perform(sim, CharacterActionKey.SKILL);
        }
        double castTime = sim.getCurrentTime();
        List<ActionRecord> burstHits = captureNamedActions(
                sim, "Lightning Fang");

        perform(sim, CharacterActionKey.BURST);
        assertEquals(1, burstHits.size(),
                "Razor Burst cast hit count");
        AttackAction burst = burstHits.get(0).action;
        assertClose(castTime + 32.0 / 60.0,
                burstHits.get(0).time, EPS,
                "Razor Burst hitmark");
        assertClose(2.72, burst.getDamagePercent(), EPS,
                "Razor C0 Burst multiplier");
        assertEquals(ActionType.BURST, burst.getActionType(),
                "Razor Burst action type");
        assertEquals(ICDType.None, burst.getICDType(),
                "Razor Burst no ICD");
        assertEquals(ICDTag.ElementalBurst, burst.getICDTag(),
                "Razor Burst ICD tag");
        assertClose(2.0, burst.getGaugeUnits(), EPS,
                "Razor Burst gauge");
        assertTrue(burst.isShatterTrigger(),
                "Razor Burst cast should be blunt");
        assertClose(castTime + 73.0 / 60.0,
                sim.getCurrentTime(), EPS,
                "Razor Burst action interval");
        assertClose(15.0, razor.getCurrentEnergy(), EPS,
                "Razor Burst spends 80 then converts three Sigils");
        assertClose(15.0, razor.getTotalFlatEnergy(), EPS,
                "Razor Burst Sigil conversion is flat Energy");
        assertEquals(0, razor.getElectroSigilCount(sim.getCurrentTime()),
                "Razor Burst clears Sigils");
        assertClose(castTime + 2.0 / 60.0 + 20.0,
                razor.getBurstCooldownEndTime(), EPS,
                "Razor Burst cooldown frame");
        assertTrue(razor.isFormActive(sim.getCurrentTime()),
                "Razor Lightning Fang active after cast");
        assertClose(0.39,
                razor.getEffectiveStats(sim.getCurrentTime())
                        .get(StatType.ATK_SPD),
                EPS,
                "Razor C0 Lightning Fang attack speed");

        List<ActionRecord> normalAndEcho = captureRazorActions(sim);
        double normalStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(2, normalAndEcho.size(),
                "Razor Burst Normal and one echo");
        AttackAction normal = normalAndEcho.get(0).action;
        AttackAction echo = normalAndEcho.get(1).action;
        assertEquals("Steel Fang N1", normal.getName(),
                "Razor Burst Normal resolves before echo");
        assertEquals("The Wolf Within Echo", echo.getName(),
                "Razor Wolf echo name");
        assertClose(normalStart, normalAndEcho.get(0).time, EPS,
                "Razor Burst Normal resolution time");
        assertClose(normalStart, normalAndEcho.get(1).time, EPS,
                "Razor echo resolves alongside Normal");
        assertClose(1.6132 * 0.408,
                echo.getDamagePercent(), EPS,
                "Razor echo scales from N1 motion value");
        assertEquals(ActionType.BURST, echo.getActionType(),
                "Razor echo Burst damage type");
        assertEquals(StatType.BURST_DMG_BONUS, echo.getBonusStat(),
                "Razor echo Burst DMG bonus routing");
        assertEquals(ICDType.Standard, echo.getICDType(),
                "Razor echo standard ICD");
        assertEquals(ICDTag.ElementalBurst, echo.getICDTag(),
                "Razor echo ICD tag");
        assertClose(1.0, echo.getGaugeUnits(), EPS,
                "Razor echo gauge");
        assertClose(normalStart + (54.0 * FRAME) / 1.39
                        + 10.0 * FRAME,
                sim.getCurrentTime(), EPS,
                "Razor Lightning Fang attack-speed timeline");

        double formExpiry = castTime + 32.0 / 60.0 + 15.0;
        sim.advanceTime(formExpiry - sim.getCurrentTime() - 0.001);
        assertTrue(razor.isFormActive(sim.getCurrentTime()),
                "Razor form pre-expiry boundary");
        sim.advanceTime(0.001);
        assertTrue(!razor.isFormActive(sim.getCurrentTime()),
                "Razor form expiry boundary");

        Razor switched = razorAtConstellation(0);
        CombatSimulator switchSim = simulatorWith(switched);
        switchSim.addCharacter(new TestCharacter());
        perform(switchSim, CharacterActionKey.BURST);
        assertTrue(switched.isFormActive(switchSim.getCurrentTime()),
                "Razor form before switch");
        switchSim.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switched.isFormActive(switchSim.getCurrentTime()),
                "Razor form ends on switch");
    }

    private static void testA1ResetAndDynamicA4() {
        Razor razor = razorAtConstellation(0);
        CombatSimulator sim = simulatorWith(razor);
        perform(sim, CharacterActionKey.SKILL);
        assertTrue(razor.getSkillCDRemaining(sim.getCurrentTime()) > 0.0,
                "Razor Skill cooldown active before Burst");
        perform(sim, CharacterActionKey.BURST);
        assertClose(0.0,
                razor.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Razor A1 Burst reset");

        Razor hunger = razorAtConstellation(0);
        CombatSimulator hungerSim = simulatorWith(hunger);
        assertClose(1.0,
                hunger.getEffectiveStats(hungerSim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                EPS,
                "Razor A4 inactive at full Energy");
        hunger.spendEnergy(40.0);
        assertClose(1.0,
                hunger.getEffectiveStats(hungerSim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                EPS,
                "Razor A4 inactive at exactly half Energy");
        hunger.spendEnergy(0.001);
        assertClose(1.30,
                hunger.getEffectiveStats(hungerSim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                EPS,
                "Razor A4 active below half Energy");
        hunger.receiveFlatEnergy(0.001);
        assertClose(1.0,
                hunger.getEffectiveStats(hungerSim.getCurrentTime())
                        .getTotalEnergyRecharge(),
                EPS,
                "Razor A4 deactivates at half Energy");
    }

    private static void testC1ActiveFieldParticleWindow() {
        Razor c0 = razorAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        c0Sim.getEnergyDistributor().distributeParticles(
                Element.ELECTRO, 1.0, ParticleType.PARTICLE);
        assertClose(0.0,
                c0.getEffectiveStats(c0Sim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                EPS,
                "Razor C0 should not gain C1 bonus");

        Razor c1 = razorAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        c1Sim.getEnergyDistributor().distributeParticles(
                Element.ELECTRO, 1.0, ParticleType.PARTICLE);
        assertClose(0.10,
                c1.getEffectiveStats(c1Sim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                EPS,
                "Razor C1 active-field particle bonus");
        c1Sim.advanceTime(8.0 - 0.001);
        assertClose(0.10,
                c1.getEffectiveStats(c1Sim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                EPS,
                "Razor C1 pre-expiry boundary");
        c1Sim.advanceTime(0.002);
        assertClose(0.0,
                c1.getEffectiveStats(c1Sim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                EPS,
                "Razor C1 expiry boundary");

        Razor offField = razorAtConstellation(1);
        CombatSimulator offFieldSim = simulatorWith(offField);
        offFieldSim.addCharacter(new TestCharacter());
        offFieldSim.setActiveCharacter(CharacterId.NOELLE);
        offFieldSim.getEnergyDistributor().distributeParticles(
                Element.ELECTRO, 1.0, ParticleType.PARTICLE);
        assertClose(0.0,
                offField.getEffectiveStats(offFieldSim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                EPS,
                "Razor C1 should require active-field pickup");
    }

    private static void testConstellationTalentLevelsAndC6() {
        Razor c3 = razorAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Burst = captureNamedActions(
                c3Sim, "Lightning Fang");
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(3.20,
                c3Burst.get(0).action.getDamagePercent(), EPS,
                "Razor C3 Burst talent level");
        assertClose(0.40,
                c3.getEffectiveStats(c3Sim.getCurrentTime())
                        .get(StatType.ATK_SPD),
                EPS,
                "Razor C3 Burst attack speed talent level");
        List<ActionRecord> c3Echo = captureNamedActions(
                c3Sim, "The Wolf Within Echo");
        perform(c3Sim, CharacterActionKey.NORMAL);
        assertClose(1.6132 * 0.48,
                c3Echo.get(0).action.getDamagePercent(), EPS,
                "Razor C3 echo talent level");

        Razor c5 = razorAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Press = captureNamedActions(
                c5Sim, "Claw and Thunder Press");
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(3.9840,
                c5Press.get(0).action.getDamagePercent(), EPS,
                "Razor C5 Skill talent level");

        Razor c6 = razorAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> lightning = captureNamedActions(
                c6Sim, "Lupus Fulguris");
        perform(c6Sim, CharacterActionKey.NORMAL);
        assertEquals(1, lightning.size(),
                "Razor C6 ready initially");
        AttackAction first = lightning.get(0).action;
        assertClose(0.0, lightning.get(0).time, EPS,
                "Razor C6 initial trigger time");
        assertClose(1.0, first.getDamagePercent(), EPS,
                "Razor C6 multiplier");
        assertEquals(ActionType.OTHER, first.getActionType(),
                "Razor C6 has no ability type");
        assertEquals(null, first.getBonusStat(),
                "Razor C6 has no ability bonus stat");
        assertEquals(ICDType.None, first.getICDType(),
                "Razor C6 no ICD");
        assertEquals(ICDTag.None, first.getICDTag(),
                "Razor C6 independent ICD tag");
        assertClose(1.0, first.getGaugeUnits(), EPS,
                "Razor C6 gauge");
        assertEquals(1, c6.getElectroSigilCount(c6Sim.getCurrentTime()),
                "Razor C6 Sigil outside Burst");

        perform(c6Sim, CharacterActionKey.NORMAL);
        assertEquals(1, lightning.size(),
                "Razor C6 blocked before ten seconds");
        c6Sim.advanceTime(10.0 - c6Sim.getCurrentTime());
        perform(c6Sim, CharacterActionKey.NORMAL);
        assertEquals(2, lightning.size(),
                "Razor C6 half-open ten-second boundary");
        assertClose(10.0, lightning.get(1).time, EPS,
                "Razor C6 retrigger time");

        Razor burstC6 = razorAtConstellation(6);
        CombatSimulator burstC6Sim = simulatorWith(burstC6);
        perform(burstC6Sim, CharacterActionKey.BURST);
        perform(burstC6Sim, CharacterActionKey.NORMAL);
        assertEquals(0,
                burstC6.getElectroSigilCount(
                        burstC6Sim.getCurrentTime()),
                "Razor C6 should not grant Sigil during Lightning Fang");
    }

    private static void testTypedActionAndUnsupportedBoundaries() {
        boolean hasHoldControlPath = false;
        for (Method method : Razor.class.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("holdskill")) {
                hasHoldControlPath = true;
            }
        }
        assertTrue(!hasHoldControlPath,
                "Razor must not invent an untyped Hold Skill control path");

        Razor razor = razorAtConstellation(0);
        CombatSimulator sim = simulatorWith(razor);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Razor unsupported Dash action");
        perform(sim, CharacterActionKey.BURST);
        assertThrows(IllegalStateException.class,
                () -> perform(sim, CharacterActionKey.CHARGE),
                "Razor Charged Attack disabled during Lightning Fang");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Razor first = razorAtConstellation(6);
        Razor second = razorAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        List<ActionRecord> firstC6 = captureNamedActions(
                firstSim, "Lupus Fulguris");
        List<ActionRecord> secondC6 = captureNamedActions(
                secondSim, "Lupus Fulguris");

        perform(firstSim, CharacterActionKey.NORMAL);
        assertEquals(1, firstC6.size(),
                "First Razor C6 state");
        assertEquals(0, secondC6.size(),
                "Second Razor C6 state should remain independent");
        assertEquals(1,
                first.getElectroSigilCount(firstSim.getCurrentTime()),
                "First Razor Sigil state");
        assertEquals(0,
                second.getElectroSigilCount(secondSim.getCurrentTime()),
                "Second Razor Sigil state should remain independent");

        perform(firstSim, CharacterActionKey.BURST);
        assertTrue(first.isFormActive(firstSim.getCurrentTime()),
                "First Razor form state");
        assertTrue(!second.isFormActive(secondSim.getCurrentTime()),
                "Second Razor form state should remain independent");

        Razor reused = razorAtConstellation(1);
        CombatSimulator original = simulatorWith(reused);
        assertTrue(original.getCharacter(CharacterId.RAZOR) == reused,
                "Razor should initialize in first simulator");
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(reused),
                "Razor listener state should reject cross-simulator reuse");
    }

    private static void testInvalidConstellation() {
        assertThrows(IllegalArgumentException.class,
                () -> razorAtConstellation(-1),
                "Razor negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> razorAtConstellation(7),
                "Razor constellation above six");
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Razor", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static Razor razorAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                "Constellation".equals(key) ? constellation : defaultValue;
        return new Razor(null, null, talentData);
    }

    private static CombatSimulator simulatorWith(Razor razor) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(razor);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.RAZOR,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureRazorActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.RAZOR) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String actionName) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.RAZOR
                    && actionName.equals(action.getName())) {
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
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
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

    /** Minimal party member used only to exercise standard switch callbacks. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            this.name = "Noelle";
            this.characterId = CharacterId.NOELLE;
            this.element = Element.GEO;
            baseStats.set(StatType.BASE_HP, 1.0);
            baseStats.set(StatType.BASE_ATK, 1.0);
            baseStats.set(StatType.BASE_DEF, 1.0);
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
