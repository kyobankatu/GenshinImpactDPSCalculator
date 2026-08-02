package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataSource;
import model.character.Venti;
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

/** Focused regression checks for Venti's old-base-kit offensive slice. */
public final class VentiRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private VentiRegressionTest() {
    }

    /** Runs data, action, timing, absorption, constellation, and isolation checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsCsvAndExplicitConstellations();
        testNormalChargedAndPlungeActions();
        testPressSkillHitmarkCooldownParticlesAndC2();
        testBurstCadenceAbsorptionPrioritySwitchAndA4();
        testLateAndRejectedAbsorptionBoundaries();
        testConstellationTalentC4AndC6Windows();
        testBurstCooldownGenerationAndIndependentInstances();
        testInvalidInputsAndCrossSimulatorBinding();
        System.out.println("VentiRegressionTest passed");
    }

    private static void testIdentityStatsCsvAndExplicitConstellations()
            throws IOException {
        Venti venti = ventiAtConstellation(6);
        assertEquals(CharacterId.VENTI, venti.getCharacterId(),
                "Venti typed id");
        assertEquals(CharacterId.VENTI, CharacterId.fromName("Venti"),
                "Venti name lookup");
        assertEquals(CharacterId.VENTI, CharacterId.fromNumericId(18),
                "Venti numeric lookup");
        assertEquals(Element.ANEMO, venti.getElement(), "Venti element");
        assertClose(10531.0,
                venti.getBaseStats().get(StatType.BASE_HP), EPS,
                "Venti base HP");
        assertClose(263.0,
                venti.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Venti base ATK");
        assertClose(669.0,
                venti.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Venti base DEF");
        assertClose(1.32,
                venti.getBaseStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Venti base plus ascension ER");
        assertClose(60.0, venti.getEnergyCost(), EPS,
                "Venti Energy cost");
        assertClose(6.0, venti.getSkillCD(), EPS, "Venti Skill CD");
        assertClose(15.0, venti.getBurstCD(), EPS, "Venti Burst CD");

        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.VENTI,
                    ventiAtConstellation(constellation).getCharacterId(),
                    "Venti explicit C" + constellation + " construction");
        }

        assertCsvShape(
                Paths.get("config/characters/Venti/Venti_Status.csv"), 10);
        assertCsvShape(
                Paths.get(
                        "config/characters/Venti/Venti_Multipliers.csv"),
                15);

        Venti configuredSkill = new Venti(null, null);
        CombatSimulator configuredSkillSim = simulatorWith(configuredSkill);
        List<ActionRecord> configuredSkillHits = captureNamedActions(
                configuredSkillSim, "Skyward Sonnet");
        perform(configuredSkillSim, CharacterActionKey.SKILL);
        assertClose(5.52,
                configuredSkillHits.get(0).action.getDamagePercent(), EPS,
                "Venti default C6 loads Skill level 12 CSV value");

        Venti configuredBurst = new Venti(null, null);
        CombatSimulator configuredBurstSim = simulatorWith(configuredBurst);
        List<ActionRecord> configuredBurstHits = captureExactActions(
                configuredBurstSim, "Wind's Grand Ode");
        perform(configuredBurstSim, CharacterActionKey.BURST);
        advanceTo(configuredBurstSim, 106.0 * FRAME);
        assertClose(0.752,
                configuredBurstHits.get(0).action.getDamagePercent(), EPS,
                "Venti default C6 loads Burst level 12 CSV value");
    }

    private static void testNormalChargedAndPlungeActions() {
        Venti venti = ventiAtConstellation(0);
        CombatSimulator sim = simulatorWith(venti);
        List<ActionRecord> records = captureVentiActions(sim);
        double[] multipliers = {
                0.3745, 0.8153, 0.9622, 0.4787, 0.9306, 1.3035
        };
        int[] hitCounts = { 2, 1, 1, 2, 1, 1 };
        double[] durations = {
                30.0 * FRAME,
                38.0 * FRAME,
                33.0 * FRAME,
                31.0 * FRAME,
                22.0 * FRAME,
                98.0 * FRAME
        };
        int[][] releaseFrames = {
                { 17, 27 }, { 19 }, { 28 }, { 15, 28 }, { 17 }, { 49 }
        };
        double[] castTimes = new double[multipliers.length];
        for (int step = 0; step < multipliers.length; step++) {
            castTimes[step] = sim.getCurrentTime();
            perform(sim, CharacterActionKey.NORMAL);
        }
        int recordIndex = 0;
        double expectedTime = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            for (int hit = 0; hit < hitCounts[step]; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertEquals("Divine Marksmanship N" + (step + 1)
                        + " Hit " + (hit + 1), record.action.getName(),
                        "Venti Normal hit name");
                assertClose(multipliers[step],
                        record.action.getDamagePercent(), EPS,
                        "Venti Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Venti Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Venti Normal category");
                assertClose(0.0, record.action.getGaugeUnits(), EPS,
                        "Venti Physical Normal gauge");
                assertClose(
                        castTimes[step]
                                + (releaseFrames[step][hit] + 10.0) * FRAME,
                        record.time,
                        EPS,
                        "Venti projectile Normal timestamp");
            }
            expectedTime += durations[step];
        }
        assertClose(expectedTime, sim.getCurrentTime(), EPS,
                "Venti full Normal sequence duration");
        perform(sim, CharacterActionKey.NORMAL);
        assertTrue(records.get(recordIndex).action.getName().contains("N1"),
                "Venti Normal chain wraps after N6");

        Venti chargedVenti = ventiAtConstellation(1);
        CombatSimulator chargedSim = simulatorWith(chargedVenti);
        List<ActionRecord> chargedRecords = captureVentiActions(chargedSim);
        perform(chargedSim, CharacterActionKey.CHARGE);
        assertEquals(1, chargedRecords.size(),
                "Venti C1 geometry remains excluded");
        AttackAction charged = chargedRecords.get(0).action;
        assertClose(2.1080, charged.getDamagePercent(), EPS,
                "Venti charged multiplier");
        assertEquals(Element.ANEMO, charged.getElement(),
                "Venti charged element");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Venti charged category");
        assertEquals(ICDType.Standard, charged.getICDType(),
                "Venti charged standard ICD");
        assertEquals(ICDTag.ChargedAttack, charged.getICDTag(),
                "Venti charged ICD group");
        assertClose(1.0, charged.getGaugeUnits(), EPS,
                "Venti charged gauge");
        assertClose(94.0 * FRAME, chargedSim.getCurrentTime(), EPS,
                "Venti charged duration");

        chargedRecords.clear();
        perform(chargedSim, CharacterActionKey.PLUNGE);
        AttackAction plunge = chargedRecords.get(0).action;
        assertClose(2.6076, plunge.getDamagePercent(), EPS,
                "Venti high Plunge multiplier");
        assertEquals(Element.PHYSICAL, plunge.getElement(),
                "Venti high Plunge element");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Venti high Plunge category");
    }

    private static void testPressSkillHitmarkCooldownParticlesAndC2() {
        Venti c0 = ventiAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Skills = captureNamedActions(
                c0Sim, "Skyward Sonnet");
        perform(c0Sim, CharacterActionKey.SKILL);
        assertEquals(1, c0Skills.size(), "Venti Skill resolves once");
        ActionRecord c0Skill = c0Skills.get(0);
        assertClose(51.0 * FRAME, c0Skill.time, EPS,
                "Venti press Skill hitmark");
        assertClose(4.692, c0Skill.action.getDamagePercent(), EPS,
                "Venti C0 press Skill multiplier");
        assertEquals(ICDType.None, c0Skill.action.getICDType(),
                "Venti press Skill has no ICD");
        assertEquals(ICDTag.ElementalSkill, c0Skill.action.getICDTag(),
                "Venti press Skill typed tag");
        assertClose(2.0, c0Skill.action.getGaugeUnits(), EPS,
                "Venti press Skill 2U");
        assertClose(98.0 * FRAME, c0Sim.getCurrentTime(), EPS,
                "Venti press Skill swap duration");
        assertClose(21.0 * FRAME, c0.getLastSkillTime(), EPS,
                "Venti Skill cooldown start frame");
        assertClose(283.0 * FRAME,
                c0.getSkillCDRemaining(c0Sim.getCurrentTime()), EPS,
                "Venti Skill remaining cooldown after animation");
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Venti particles remain in flight after Skill animation");
        advanceTo(c0Sim, 151.0 * FRAME);
        assertClose(9.0, c0.getTotalParticleEnergy(), EPS,
                "Venti receives three particles after travel");
        assertClose(0.0,
                applicableStats(c0Sim, c0).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C0 has no C2 shred");

        Venti c2 = ventiAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        perform(c2Sim, CharacterActionKey.SKILL);
        assertClose(0.12,
                applicableStats(c2Sim, c2).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C2 ground Anemo shred");
        assertClose(0.12,
                applicableStats(c2Sim, c2).get(StatType.PHYS_RES_SHRED),
                EPS, "Venti C2 ground Physical shred");
        advanceTo(c2Sim, 51.0 * FRAME + 10.0 - 0.001);
        assertClose(0.12,
                applicableStats(c2Sim, c2).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C2 active before expiry");
        advanceTo(c2Sim, 51.0 * FRAME + 10.0);
        assertClose(0.0,
                applicableStats(c2Sim, c2).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C2 half-open expiry");
        assertClose(0.0,
                applicableStats(c2Sim, c2).get(StatType.PHYS_RES_SHRED),
                EPS, "Venti airborne C2 branch is not fabricated");
    }

    private static void testBurstCadenceAbsorptionPrioritySwitchAndA4() {
        Venti venti = ventiAtConstellation(0);
        CombatSimulator sim = simulatorWith(venti);
        TestCharacter pyroAlly = new TestCharacter(
                CharacterId.NOELLE, Element.PYRO);
        TestCharacter hydroAlly = new TestCharacter(
                CharacterId.ALBEDO, Element.HYDRO);
        sim.addCharacter(pyroAlly);
        sim.addCharacter(hydroAlly);
        pyroAlly.restoreCurrentEnergy(0.0);
        hydroAlly.restoreCurrentEnergy(0.0);
        sim.getEnemy().setAura(Element.CRYO, 8.0);
        sim.getEnemy().setAura(Element.ELECTRO, 8.0);
        sim.getEnemy().setAura(Element.HYDRO, 8.0);
        sim.getEnemy().setAura(Element.PYRO, 8.0);
        int[] swirlCount = { 0 };
        sim.addReactionListener((result, source, time, activeSim) -> {
            if (source == venti && result.isSwirl()) {
                swirlCount[0]++;
            }
        });
        List<ActionRecord> anemo = captureExactActions(
                sim, "Wind's Grand Ode");
        List<ActionRecord> absorbed = captureNamedActions(
                sim, "Wind's Grand Ode (Absorbed");

        double castTime = sim.getCurrentTime();
        perform(sim, CharacterActionKey.BURST);
        assertClose(castTime + 95.0 * FRAME, sim.getCurrentTime(), EPS,
                "Venti Burst action duration");
        assertClose(castTime + 81.0 * FRAME,
                venti.getLastBurstTime(), EPS,
                "Venti Burst cooldown and Energy frame");
        assertClose(0.0, venti.getCurrentEnergy(), EPS,
                "Venti Burst consumes 60 Energy");
        assertTrue(venti.isBurstActive(sim.getCurrentTime()),
                "Venti Stormeye active after cast");

        sim.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(sim, castTime + 178.0 * FRAME - 0.001);
        assertEquals(3, anemo.size(),
                "Venti has three Anemo ticks before frame 178");
        assertEquals(0, absorbed.size(),
                "Venti cannot absorb before fourth tick");
        advanceTo(sim, castTime + 178.0 * FRAME);
        assertEquals(4, anemo.size(), "Venti fourth Anemo tick");
        assertEquals(1, absorbed.size(),
                "Venti starts absorbed damage with fourth tick");
        assertEquals(Element.PYRO, venti.getAbsorbedElement(),
                "Venti absorption priority starts with Pyro");
        assertEquals(Element.PYRO, absorbed.get(0).action.getElement(),
                "Venti absorbed tick element");
        assertEquals(ICDTag.None, absorbed.get(0).action.getICDTag(),
                "Venti absorbed ICD is independent from Anemo Burst ICD");
        assertTrue(absorbed.get(0).action.isUseSnapshot(),
                "Venti absorbed tick uses cast snapshot");
        assertTrue(swirlCount[0] > 0,
                "Venti Burst resolves actual Swirl reactions");

        advanceTo(sim, castTime + 562.0 * FRAME);
        assertEquals(20, anemo.size(),
                "Venti fixed target receives twenty Anemo ticks");
        assertEquals(15, absorbed.size(),
                "Venti fixed target receives fifteen absorbed ticks");
        for (int i = 1; i < anemo.size(); i++) {
            assertClose(24.0 * FRAME,
                    anemo.get(i).time - anemo.get(i - 1).time, EPS,
                    "Venti Anemo tick cadence");
        }
        for (int i = 0; i < anemo.size(); i++) {
            assertEquals(ICDType.None, anemo.get(i).action.getICDType(),
                    "Venti local Anemo ICD encoding");
            assertClose(i % 3 == 0 ? 1.0 : 0.0,
                    anemo.get(i).action.getGaugeUnits(), EPS,
                    "Venti Anemo 1/0/0 gauge sequence");
        }
        for (int i = 0; i < absorbed.size(); i++) {
            assertEquals(ICDType.None, absorbed.get(i).action.getICDType(),
                    "Venti local absorbed ICD encoding");
            assertClose(i % 3 == 0 ? 1.0 : 0.0,
                    absorbed.get(i).action.getGaugeUnits(), EPS,
                    "Venti absorbed 1/0/0 gauge sequence");
        }
        assertTrue(venti.isBurstActive(castTime + 574.0 * FRAME - 0.001),
                "Venti Stormeye active before effect end");
        advanceTo(sim, castTime + 574.0 * FRAME);
        assertTrue(!venti.isBurstActive(sim.getCurrentTime()),
                "Venti Stormeye expires at frame 574");
        assertClose(15.0, venti.getCurrentEnergy(), EPS,
                "Venti A4 restores owner Energy");
        assertClose(15.0, pyroAlly.getCurrentEnergy(), EPS,
                "Venti A4 restores matching absorbed-element ally");
        assertClose(0.0, hydroAlly.getCurrentEnergy(), EPS,
                "Venti A4 excludes nonmatching ally");
    }

    private static void testLateAndRejectedAbsorptionBoundaries() {
        Venti late = ventiAtConstellation(0);
        CombatSimulator lateSim = simulatorWith(late);
        List<ActionRecord> lateAbsorbed = captureNamedActions(
                lateSim, "Wind's Grand Ode (Absorbed");
        perform(lateSim, CharacterActionKey.BURST);
        advanceTo(lateSim, 196.0 * FRAME - 0.001);
        assertEquals(null, late.getAbsorbedElement(),
                "Venti has no absorption before first late check");
        lateSim.getEnemy().setAura(Element.HYDRO, 2.0);
        advanceTo(lateSim, 196.0 * FRAME);
        assertEquals(Element.HYDRO, late.getAbsorbedElement(),
                "Venti captures first eligible late aura");
        assertEquals(1, lateAbsorbed.size(),
                "Venti late absorption starts independent tick train");
        lateSim.getEnemy().setAura(Element.PYRO, 2.0);
        advanceTo(lateSim, 220.0 * FRAME);
        assertEquals(Element.HYDRO, late.getAbsorbedElement(),
                "Venti absorption occurs only once per Burst");

        Venti unsupported = ventiAtConstellation(0);
        CombatSimulator unsupportedSim = simulatorWith(unsupported);
        List<ActionRecord> unsupportedAbsorbed = captureNamedActions(
                unsupportedSim, "Wind's Grand Ode (Absorbed");
        unsupportedSim.getEnemy().setAura(Element.DENDRO, 8.0);
        perform(unsupportedSim, CharacterActionKey.BURST);
        advanceTo(unsupportedSim, 874.0 * FRAME);
        assertEquals(null, unsupported.getAbsorbedElement(),
                "Venti rejects Dendro absorption");
        assertEquals(0, unsupportedAbsorbed.size(),
                "Venti physical or unsupported aura creates no extra ticks");

        Venti noAura = ventiAtConstellation(0);
        CombatSimulator noAuraSim = simulatorWith(noAura);
        TestCharacter pyroAlly = new TestCharacter(
                CharacterId.NOELLE, Element.PYRO);
        noAuraSim.addCharacter(pyroAlly);
        pyroAlly.restoreCurrentEnergy(0.0);
        perform(noAuraSim, CharacterActionKey.BURST);
        advanceTo(noAuraSim, 574.0 * FRAME);
        assertClose(15.0, noAura.getCurrentEnergy(), EPS,
                "Venti A4 owner Energy does not require absorption");
        assertClose(0.0, pyroAlly.getCurrentEnergy(), EPS,
                "Venti A4 team Energy requires absorption");
    }

    private static void testConstellationTalentC4AndC6Windows() {
        Venti c3 = ventiAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        c3Sim.getEnemy().setAura(Element.PYRO, 8.0);
        List<ActionRecord> c3Anemo = captureExactActions(
                c3Sim, "Wind's Grand Ode");
        List<ActionRecord> c3Absorbed = captureNamedActions(
                c3Sim, "Wind's Grand Ode (Absorbed");
        perform(c3Sim, CharacterActionKey.BURST);
        advanceTo(c3Sim, 178.0 * FRAME);
        assertClose(0.7520, c3Anemo.get(0).action.getDamagePercent(), EPS,
                "Venti C3 Burst DoT talent level");
        assertClose(0.3760,
                c3Absorbed.get(0).action.getDamagePercent(), EPS,
                "Venti C3 absorbed talent level");

        Venti c5 = ventiAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Skill = captureNamedActions(
                c5Sim, "Skyward Sonnet");
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(5.52, c5Skill.get(0).action.getDamagePercent(), EPS,
                "Venti C5 Skill talent level");

        Venti c4 = ventiAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        perform(c4Sim, CharacterActionKey.SKILL);
        assertClose(0.0,
                c4.getEffectiveStats(c4Sim.getCurrentTime()).get(
                        StatType.ANEMO_DMG_BONUS),
                EPS, "Venti C4 waits for particle travel");
        advanceTo(c4Sim, 151.0 * FRAME);
        assertClose(0.25,
                c4.getEffectiveStats(c4Sim.getCurrentTime()).get(
                        StatType.ANEMO_DMG_BONUS),
                EPS, "Venti C4 triggers on an active-field particle pickup");
        advanceTo(c4Sim, 151.0 * FRAME + 10.0);
        assertClose(0.0,
                c4.getEffectiveStats(c4Sim.getCurrentTime()).get(
                        StatType.ANEMO_DMG_BONUS),
                EPS, "Venti C4 half-open expiry");

        Venti offFieldC4 = ventiAtConstellation(4);
        CombatSimulator offFieldC4Sim = simulatorWith(offFieldC4);
        offFieldC4Sim.addCharacter(new TestCharacter(
                CharacterId.NOELLE, Element.GEO));
        offFieldC4Sim.setActiveCharacter(CharacterId.NOELLE);
        offFieldC4Sim.notifyParticle(Element.ANEMO, 1.0);
        assertClose(0.0,
                offFieldC4.getEffectiveStats(0.0).get(
                        StatType.ANEMO_DMG_BONUS),
                EPS, "Venti C4 rejects off-field particle notifications");
        offFieldC4Sim.setActiveCharacter(CharacterId.VENTI);
        offFieldC4Sim.notifyParticle(Element.ANEMO, 0.0);
        assertClose(0.0,
                offFieldC4.getEffectiveStats(0.0).get(
                        StatType.ANEMO_DMG_BONUS),
                EPS, "Venti C4 rejects nonpositive particle counts");

        Venti c5LiveResistance = ventiAtConstellation(5);
        Venti c6LiveResistance = ventiAtConstellation(6);
        CombatSimulator c5LiveSim = simulatorWith(c5LiveResistance);
        CombatSimulator c6LiveSim = simulatorWith(c6LiveResistance);
        List<ActionRecord> c5LiveTicks = captureExactActions(
                c5LiveSim, "Wind's Grand Ode");
        List<ActionRecord> c6LiveTicks = captureExactActions(
                c6LiveSim, "Wind's Grand Ode");
        perform(c5LiveSim, CharacterActionKey.BURST);
        perform(c6LiveSim, CharacterActionKey.BURST);
        advanceTo(c5LiveSim, 130.0 * FRAME);
        advanceTo(c6LiveSim, 130.0 * FRAME);
        assertClose(c5LiveTicks.get(0).damage,
                c6LiveTicks.get(0).damage, EPS,
                "Venti C6 first Anemo hit precedes its own shred");
        assertClose(c5LiveTicks.get(1).damage * 1.05 / 0.90,
                c6LiveTicks.get(1).damage, EPS,
                "Venti C6 live RES shred affects later snapshot ticks");

        Venti c6 = ventiAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        c6Sim.getEnemy().setAura(Element.PYRO, 8.0);
        perform(c6Sim, CharacterActionKey.BURST);
        advanceTo(c6Sim, 178.0 * FRAME);
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C6 Anemo shred");
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(StatType.PYRO_RES_SHRED),
                EPS, "Venti C6 absorbed-element shred");
        advanceTo(c6Sim, 1114.0 * FRAME - 0.001);
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(StatType.PYRO_RES_SHRED),
                EPS, "Venti C6 absorbed shred active before own expiry");
        advanceTo(c6Sim, 1114.0 * FRAME);
        assertClose(0.0,
                applicableStats(c6Sim, c6).get(StatType.PYRO_RES_SHRED),
                EPS, "Venti C6 absorbed shred expires independently");
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C6 later Anemo refresh remains active");
        advanceTo(c6Sim, 1162.0 * FRAME);
        assertClose(0.0,
                applicableStats(c6Sim, c6).get(StatType.ANEMO_RES_SHRED),
                EPS, "Venti C6 Anemo shred exact expiry");
    }

    private static void testBurstCooldownGenerationAndIndependentInstances() {
        Venti first = ventiAtConstellation(0);
        Venti second = ventiAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        List<ActionRecord> firstTicks = captureExactActions(
                firstSim, "Wind's Grand Ode");
        List<ActionRecord> secondTicks = captureExactActions(
                secondSim, "Wind's Grand Ode");
        perform(firstSim, CharacterActionKey.BURST);
        perform(secondSim, CharacterActionKey.BURST);
        advanceTo(firstSim, 106.0 * FRAME);
        assertEquals(1, firstTicks.size(),
                "Venti first instance owns its Burst timer");
        assertEquals(0, secondTicks.size(),
                "Venti second instance has independent time");
        advanceTo(secondSim, 106.0 * FRAME);
        assertEquals(1, secondTicks.size(),
                "Venti second instance resolves independently");

        advanceTo(firstSim, 981.0 * FRAME - 0.001);
        assertTrue(first.getBurstCDRemaining(firstSim.getCurrentTime()) > 0.0,
                "Venti Burst cooldown active before frame 981");
        advanceTo(firstSim, 981.0 * FRAME);
        assertClose(0.0,
                first.getBurstCDRemaining(firstSim.getCurrentTime()), EPS,
                "Venti Burst cooldown ready at frame 981");
        first.restoreCurrentEnergy(60.0);
        perform(firstSim, CharacterActionKey.BURST);
        int completedFirstGeneration = firstTicks.size();
        advanceTo(firstSim, 981.0 * FRAME + 106.0 * FRAME);
        assertEquals(completedFirstGeneration + 1, firstTicks.size(),
                "Venti recast has one new generation first tick");
    }

    private static void testInvalidInputsAndCrossSimulatorBinding() {
        assertThrows(IllegalArgumentException.class,
                () -> ventiAtConstellation(-1),
                "Venti rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> ventiAtConstellation(7),
                "Venti rejects constellation above six");

        Venti venti = ventiAtConstellation(0);
        CombatSimulator sim = simulatorWith(venti);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Venti rejects unsupported Dash action");

        CombatSimulator rejected = new CombatSimulator();
        rejected.setLoggingEnabled(false);
        rejected.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> rejected.addCharacter(venti),
                "Venti rejects cross-simulator reuse");
    }

    private static Venti ventiAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Venti(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(Venti venti) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(venti);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.VENTI,
                CharacterActionRequest.of(key));
    }

    private static void advanceTo(CombatSimulator sim, double targetTime) {
        double delta = targetTime - sim.getCurrentTime();
        if (delta > 0.0) {
            sim.advanceTime(delta);
        }
    }

    private static List<ActionRecord> captureVentiActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.VENTI) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.VENTI
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureExactActions(
            CombatSimulator sim,
            String name) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.VENTI
                    && action.getName().equals(name)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static StatsContainer applicableStats(
            CombatSimulator sim,
            Character target) {
        StatsContainer stats = target.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(target)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats;
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Venti", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
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

        private ActionRecord(AttackAction action, double damage, double time) {
            this.action = action;
            this.damage = damage;
            this.time = time;
        }
    }

    /** Minimal party member for switching and element-scoped A4 checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            this.name = id.getDisplayName();
            this.characterId = id;
            this.element = element;
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
