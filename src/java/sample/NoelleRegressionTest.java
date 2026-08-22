package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.character.Noelle;
import model.entity.Enemy;
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

/**
 * Focused regression executable for Noelle's offensive vertical slice.
 */
public final class NoelleRegressionTest {
    private static final double EPS = 1e-9;

    private NoelleRegressionTest() {
    }

    /**
     * Runs Noelle data, action, timing, state, and constellation checks.
     *
     * @param args ignored command-line arguments
     * @throws IOException if the configured CSV files cannot be read
     */
    public static void main(String[] args) throws IOException {
        testIdentityStatsAndCsvAlignment();
        testNormalChargedAndPlungeActions();
        testBreastplateAndA4CooldownReduction();
        testBurstEnergyCooldownAndCastHits();
        testInfusionConversionExpiryAndSnapshotRestore();
        testConstellationBoundariesAndUnsupportedEffects();
        testIndependentInstancesAndSimulatorBinding();
        testInvalidConstellation();
        System.out.println("Noelle regression checks passed.");
    }

    private static void testIdentityStatsAndCsvAlignment() throws IOException {
        Noelle noelle = new Noelle(null, null);
        assertEquals(CharacterId.NOELLE, noelle.getCharacterId(),
                "Noelle typed identity");
        assertEquals(CharacterId.NOELLE, CharacterId.fromName("Noelle"),
                "Noelle display-name lookup");
        assertEquals(CharacterId.NOELLE, CharacterId.fromNumericId(13),
                "Noelle numeric-id lookup");
        assertClose(12071.0, noelle.getBaseStats().get(StatType.BASE_HP), EPS,
                "Noelle level-90 base HP");
        assertClose(191.0, noelle.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Noelle level-90 base ATK");
        assertClose(799.0, noelle.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Noelle level-90 base DEF");
        assertClose(0.30, noelle.getBaseStats().get(StatType.DEF_PERCENT), EPS,
                "Noelle ascension DEF");
        assertClose(60.0, noelle.getEnergyCost(), EPS,
                "Noelle Burst Energy cost");
        assertClose(24.0, noelle.getSkillCD(), EPS,
                "Noelle Skill cooldown");
        assertClose(15.0, noelle.getBurstCD(), EPS,
                "Noelle Burst cooldown");

        assertCsvShape(Path.of(
                "config/characters/Noelle/Noelle_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Noelle/Noelle_Multipliers.csv"), 16);

        TalentDataManager talentData = TalentDataManager.getInstance();
        assertClose(1.4536, talentData.get("Noelle", "N1", -1.0), EPS,
                "Noelle configured N1 multiplier");
        assertClose(0.9322,
                talentData.get("Noelle", "Charged Spin", -1.0), EPS,
                "Noelle configured Charged multiplier");
        assertClose(3.422517,
                talentData.get("Noelle", "Plunge High", -1.0), EPS,
                "Noelle configured high Plunge multiplier");
        assertClose(2.40,
                talentData.get("Noelle", "Breastplate", -1.0), EPS,
                "Noelle configured C3 Skill multiplier");
        assertClose(1.344,
                talentData.get("Noelle", "Sweeping Time Burst Hit", -1.0), EPS,
                "Noelle configured C5 Burst hit multiplier");
        assertClose(1.856,
                talentData.get("Noelle", "Sweeping Time Skill Hit", -1.0), EPS,
                "Noelle configured C5 Burst skill-hit multiplier");
        assertClose(0.80,
                talentData.get("Noelle", "Burst DEF Conversion", -1.0), EPS,
                "Noelle configured C5 DEF conversion");
    }

    private static void testNormalChargedAndPlungeActions() {
        Noelle noelle = noelleAtConstellation(0);
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> records = captureNoelleActions(sim);

        for (int i = 0; i < 4; i++) {
            perform(sim, CharacterActionKey.NORMAL);
        }

        double[] normalMultipliers = {
                1.4536, 1.34774, 1.58474, 2.08402
        };
        double[] normalDurations = {
                48.0 / 60.0,
                56.0 / 60.0,
                41.0 / 60.0,
                120.0 / 60.0
        };
        int[] normalHitlagFrames = { 0, 0, 9, 0 };
        double expectedTime = 0.0;
        assertEquals(4, records.size(), "Noelle four-hit Normal action count");
        for (int i = 0; i < 4; i++) {
            AttackAction action = records.get(i).action;
            assertClose(expectedTime, records.get(i).time, EPS,
                    "Noelle N" + (i + 1) + " resolution time");
            assertClose(normalMultipliers[i], action.getDamagePercent(), EPS,
                    "Noelle N" + (i + 1) + " multiplier");
            assertClose(normalDurations[i], action.getAnimationDuration(), EPS,
                    "Noelle N" + (i + 1) + " action interval");
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Noelle Normal action type");
            assertEquals(Element.PHYSICAL, action.getElement(),
                    "Noelle uninfused Normal element");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Noelle uninfused Normal ICD type");
            assertEquals(ICDTag.NormalAttack, action.getICDTag(),
                    "Noelle Normal ICD tag");
            assertClose(0.0, action.getGaugeUnits(), EPS,
                    "Noelle uninfused Normal gauge");
            assertTrue(action.isShatterTrigger(),
                    "Noelle Normal should be blunt");
            expectedTime += normalDurations[i];
            expectedTime += normalHitlagFrames[i] / 60.0;
        }

        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertTrue(records.get(0).action.getName().endsWith("N1"),
                "Noelle Normal chain should wrap after N4");

        records.clear();
        perform(sim, CharacterActionKey.CHARGE);
        AttackAction charged = records.get(0).action;
        assertClose(0.9322, charged.getDamagePercent(), EPS,
                "Noelle Charged spin multiplier");
        assertClose(23.0 / 60.0, charged.getAnimationDuration(), EPS,
                "Noelle steady-state Charged spin interval");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Noelle Charged action type");
        assertEquals(Element.PHYSICAL, charged.getElement(),
                "Noelle uninfused Charged element");
        assertEquals(ICDTag.ChargedAttack, charged.getICDTag(),
                "Noelle Charged ICD tag");
        assertClose(0.0, charged.getGaugeUnits(), EPS,
                "Noelle uninfused Charged gauge");

        records.clear();
        perform(sim, CharacterActionKey.PLUNGE);
        AttackAction plunge = records.get(0).action;
        assertClose(3.422517, plunge.getDamagePercent(), EPS,
                "Noelle high Plunge multiplier");
        assertClose(82.0 / 60.0, plunge.getAnimationDuration(), EPS,
                "Noelle high Plunge interval");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Noelle Plunge action type");
        assertEquals(ICDType.None, plunge.getICDType(),
                "Noelle Plunge ICD type");
        assertEquals(ICDTag.PlungeAttack, plunge.getICDTag(),
                "Noelle Plunge ICD tag");
        assertClose(0.0, plunge.getGaugeUnits(), EPS,
                "Noelle uninfused Plunge gauge");
    }

    private static void testBreastplateAndA4CooldownReduction() {
        Noelle noelle = noelleAtConstellation(0);
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> skillHits = captureNamedActions(sim, "Breastplate");
        double energyBefore = noelle.getCurrentEnergy();

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, skillHits.size(),
                "Noelle Breastplate should deal one cast hit");
        AttackAction hit = skillHits.get(0).action;
        assertClose(15.0 / 60.0, skillHits.get(0).time, EPS,
                "Noelle Breastplate hitmark");
        assertClose(2.04, hit.getDamagePercent(), EPS,
                "Noelle C0 Breastplate talent-9 multiplier");
        assertEquals(StatType.BASE_DEF, hit.getScalingStat(),
                "Noelle Breastplate DEF scaling");
        assertEquals(ActionType.SKILL, hit.getActionType(),
                "Noelle Breastplate action type");
        assertEquals(Element.GEO, hit.getElement(),
                "Noelle Breastplate element");
        assertEquals(ICDType.Standard, hit.getICDType(),
                "Noelle Breastplate ICD type");
        assertEquals(ICDTag.ElementalSkill, hit.getICDTag(),
                "Noelle Breastplate ICD tag");
        assertClose(2.0, hit.getGaugeUnits(), EPS,
                "Noelle Breastplate gauge");
        assertClose((43.0 + 4.0) / 60.0, sim.getCurrentTime(), EPS,
                "Noelle Breastplate action interval");
        assertClose(energyBefore, noelle.getCurrentEnergy(), EPS,
                "Noelle Breastplate should not change Energy");
        assertClose(0.0, noelle.getTotalParticleEnergy(), EPS,
                "Noelle Breastplate should generate no particles");
        assertClose(24.0, noelle.getSkillCooldownEndTime(), EPS,
                "Noelle Breastplate cooldown start boundary");

        for (int i = 0; i < 3; i++) {
            perform(sim, CharacterActionKey.NORMAL);
        }
        assertClose(24.0, noelle.getSkillCooldownEndTime(), EPS,
                "Noelle A4 should not reduce cooldown before four hits");
        perform(sim, CharacterActionKey.NORMAL);
        assertClose(23.0, noelle.getSkillCooldownEndTime(), EPS,
                "Noelle A4 fourth-hit cooldown reduction");

        Noelle gated = noelleAtConstellation(0);
        CombatSimulator gatedSim = simulatorWith(gated);
        List<ActionRecord> gatedHits = captureNamedActions(
                gatedSim,
                "Breastplate");
        perform(gatedSim, CharacterActionKey.SKILL);
        perform(gatedSim, CharacterActionKey.SKILL);
        assertEquals(2, gatedHits.size(),
                "Noelle Skill cooldown should permit exactly two requested casts");
        assertClose(24.0 + 15.0 / 60.0, gatedHits.get(1).time, EPS,
                "Noelle second Breastplate should wait for cooldown");

        Noelle persistent = noelleAtConstellation(0);
        CombatSimulator persistentSim = simulatorWith(persistent);
        perform(persistentSim, CharacterActionKey.SKILL);
        for (int i = 0; i < 3; i++) {
            perform(persistentSim, CharacterActionKey.NORMAL);
        }
        perform(persistentSim, CharacterActionKey.SKILL);
        double recastCooldownEnd = persistent.getSkillCooldownEndTime();
        perform(persistentSim, CharacterActionKey.NORMAL);
        assertClose(recastCooldownEnd - 1.0,
                persistent.getSkillCooldownEndTime(), EPS,
                "Noelle A4 partial count should persist across Breastplate casts");
    }

    private static void testBurstEnergyCooldownAndCastHits() {
        Noelle noelle = noelleAtConstellation(0);
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> burstHits = captureSweepingTimeDamage(sim);

        noelle.spendEnergy(60.0);
        perform(sim, CharacterActionKey.BURST);
        assertEquals(0, burstHits.size(),
                "Noelle Burst should skip without Energy");
        assertClose(0.0, sim.getCurrentTime(), EPS,
                "Skipped Noelle Burst should not advance time");
        assertClose(60.0, noelle.getMissedBurstCost(), EPS,
                "Skipped Noelle Burst should record missed cost");

        noelle.receiveFlatEnergy(60.0);
        perform(sim, CharacterActionKey.BURST);
        assertEquals(2, burstHits.size(),
                "Noelle Burst should resolve both cast hits");
        assertClose(24.0 / 60.0, burstHits.get(0).time, EPS,
                "Noelle Burst first hitmark");
        assertClose(64.0 / 60.0, burstHits.get(1).time, EPS,
                "Noelle Burst second hitmark");
        assertClose(1.1424, burstHits.get(0).action.getDamagePercent(), EPS,
                "Noelle C0 first Burst multiplier");
        assertClose(1.5776, burstHits.get(1).action.getDamagePercent(), EPS,
                "Noelle C0 second Burst multiplier");
        for (ActionRecord record : burstHits) {
            AttackAction action = record.action;
            assertEquals(ActionType.BURST, action.getActionType(),
                    "Noelle cast hit Burst classification");
            assertEquals(Element.GEO, action.getElement(),
                    "Noelle cast hit element");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Noelle cast hit ICD type");
            assertEquals(ICDTag.ElementalBurst, action.getICDTag(),
                    "Noelle cast hit shared ICD tag");
            assertClose(1.0, action.getGaugeUnits(), EPS,
                    "Noelle cast hit gauge");
        }
        assertClose(0.0, noelle.getCurrentEnergy(), EPS,
                "Noelle Burst should spend 60 Energy");
        assertClose((89.0 + 26.0) / 60.0, sim.getCurrentTime(), EPS,
                "Noelle Burst action interval");
        assertClose(15.0, noelle.getBurstCooldownEndTime(), EPS,
                "Noelle Burst cooldown boundary");
        double[] icdState = sim.getIcdManager().saveStates()
                .get("NOELLE_ElementalBurst");
        assertTrue(icdState != null,
                "Noelle Burst should create a typed ICD state");
        assertClose(24.0 / 60.0, icdState[0], EPS,
                "Noelle first Burst application time");
        assertClose(1.0, icdState[1], EPS,
                "Noelle second Burst hit should increment the ICD counter once");

        perform(sim, CharacterActionKey.BURST);
        assertClose(15.0, sim.getCurrentTime(), EPS,
                "Noelle second Burst request should wait for cooldown");
        assertEquals(2, burstHits.size(),
                "Noelle second Burst should then skip without Energy");
        assertClose(120.0, noelle.getMissedBurstCost(), EPS,
                "Noelle repeated missed Burst accounting");
    }

    private static void testInfusionConversionExpiryAndSnapshotRestore() {
        Noelle noelle = noelleAtConstellation(0);
        noelle.addBuff(new SimpleBuff(
                "Temporary DEF",
                1.0,
                0.0,
                stats -> stats.add(StatType.DEF_FLAT, 100.0)));
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> normals = captureNamedActions(
                sim,
                "Favonius Bladework - Maid N1");

        perform(sim, CharacterActionKey.BURST);
        double capturedDef = 799.0 * 1.30 + 100.0;
        double expectedConvertedAtk = 191.0 + capturedDef * 0.68;
        assertClose(expectedConvertedAtk,
                noelle.getEffectiveStats(sim.getCurrentTime()).getTotalAtk(),
                EPS,
                "Noelle Burst should snapshot temporary DEF into ATK");
        assertClose(799.0 * 1.30,
                noelle.getEffectiveStats(sim.getCurrentTime()).getTotalDef(),
                EPS,
                "Temporary DEF should expire without changing conversion");

        perform(sim, CharacterActionKey.NORMAL);
        AttackAction infused = normals.get(0).action;
        assertEquals(Element.GEO, infused.getElement(),
                "Noelle Sweeping Time Normal infusion");
        assertEquals(ICDType.None, infused.getICDType(),
                "Noelle infused Normal should have no ICD");
        assertClose(1.0, infused.getGaugeUnits(), EPS,
                "Noelle infused Normal gauge");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(15.0 - sim.getCurrentTime());
        assertTrue(!noelle.isFormActive(sim.getCurrentTime()),
                "Noelle Sweeping Time should expire at exactly 15 seconds");
        assertClose(191.0,
                noelle.getEffectiveStats(sim.getCurrentTime()).getTotalAtk(),
                EPS,
                "Noelle DEF conversion exact expiry");

        sim.restoreSnapshot(snapshot);
        sim.advanceTime(15.0 - sim.getCurrentTime() - 0.001);
        assertTrue(noelle.isFormActive(sim.getCurrentTime()),
                "Restored Noelle Sweeping Time should remain active before expiry");
        assertClose(expectedConvertedAtk,
                noelle.getEffectiveStats(sim.getCurrentTime()).getTotalAtk(),
                EPS,
                "Restored Noelle conversion should retain captured DEF");
        sim.advanceTime(0.001);
        assertTrue(!noelle.isFormActive(sim.getCurrentTime()),
                "Restored Noelle Sweeping Time exact expiry");

        List<ActionRecord> expiredActions = captureNoelleActions(sim);
        perform(sim, CharacterActionKey.NORMAL);
        AttackAction expiredNormal = expiredActions.get(0).action;
        assertEquals(Element.PHYSICAL, expiredNormal.getElement(),
                "Noelle Normal should return to Physical at expiry");
        assertClose(0.0, expiredNormal.getGaugeUnits(), EPS,
                "Expired Noelle infusion should apply no gauge");
    }

    private static void testConstellationBoundariesAndUnsupportedEffects() {
        Noelle c1 = noelleAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Charged = captureNamedActions(
                c1Sim,
                "Favonius Bladework - Maid Charged Spin");
        perform(c1Sim, CharacterActionKey.CHARGE);
        assertTrue(!c1Charged.get(0).action.getExtraBonuses()
                        .containsKey(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Noelle C1 should not grant the C2 Charged bonus");

        Noelle c2 = noelleAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Charged = captureNamedActions(
                c2Sim,
                "Favonius Bladework - Maid Charged Spin");
        perform(c2Sim, CharacterActionKey.CHARGE);
        assertClose(0.15,
                c2Charged.get(0).action.getExtraBonuses()
                        .get(StatType.CHARGED_ATTACK_DMG_BONUS),
                EPS,
                "Noelle C2 additive Charged DMG bonus");

        assertClose(2.04, firstBreastplateMultiplier(2), EPS,
                "Noelle C2 talent-9 Breastplate multiplier");
        assertClose(2.40, firstBreastplateMultiplier(3), EPS,
                "Noelle C3 talent-12 Breastplate multiplier");

        Noelle c3 = noelleAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Explosion = captureNamedActions(
                c3Sim,
                "Breastplate Natural Expiry (C4)");
        perform(c3Sim, CharacterActionKey.SKILL);
        c3Sim.advanceTime(12.0 - c3Sim.getCurrentTime());
        assertEquals(0, c3Explosion.size(),
                "Noelle C3 should not emit a C4 expiry hit");

        Noelle c4 = noelleAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        List<ActionRecord> c4Explosion = captureNamedActions(
                c4Sim,
                "Breastplate Natural Expiry (C4)");
        perform(c4Sim, CharacterActionKey.SKILL);
        c4Sim.advanceTime(12.0 - c4Sim.getCurrentTime() - 0.001);
        assertEquals(0, c4Explosion.size(),
                "Noelle C4 should not trigger before natural expiry");
        c4Sim.advanceTime(0.001);
        assertEquals(1, c4Explosion.size(),
                "Noelle C4 should trigger at natural expiry");
        AttackAction c4Hit = c4Explosion.get(0).action;
        assertClose(4.0, c4Hit.getDamagePercent(), EPS,
                "Noelle C4 multiplier");
        assertEquals(StatType.BASE_ATK, c4Hit.getScalingStat(),
                "Noelle C4 current ATK scaling");
        assertEquals(ActionType.SKILL, c4Hit.getActionType(),
                "Noelle C4 Skill damage classification");
        assertEquals(ICDType.Standard, c4Hit.getICDType(),
                "Noelle C4 shared Skill ICD type");
        assertEquals(ICDTag.ElementalSkill, c4Hit.getICDTag(),
                "Noelle C4 shared Skill ICD tag");
        assertClose(0.0, c4Hit.getGaugeUnits(), EPS,
                "Noelle C4 should apply no Geo gauge");

        double unbuffedC4Damage = c4Explosion.get(0).damage;
        Noelle burstC4 = noelleAtConstellation(4);
        CombatSimulator burstC4Sim = simulatorWith(burstC4);
        List<ActionRecord> burstC4Explosion = captureNamedActions(
                burstC4Sim,
                "Breastplate Natural Expiry (C4)");
        perform(burstC4Sim, CharacterActionKey.SKILL);
        burstC4Sim.advanceTime(10.0 - burstC4Sim.getCurrentTime());
        perform(burstC4Sim, CharacterActionKey.BURST);
        burstC4Sim.advanceTime(12.0 - burstC4Sim.getCurrentTime());
        assertTrue(burstC4Explosion.get(0).damage > unbuffedC4Damage,
                "Noelle C4 should use current ATK at natural expiry");

        List<ActionRecord> c4BurstHits = firstBurstHits(4);
        List<ActionRecord> c5BurstHits = firstBurstHits(5);
        assertClose(1.1424, c4BurstHits.get(0).action.getDamagePercent(), EPS,
                "Noelle C4 talent-9 Burst multiplier");
        assertClose(1.5776, c4BurstHits.get(1).action.getDamagePercent(), EPS,
                "Noelle C4 talent-9 Burst skill-hit multiplier");
        assertClose(1.344, c5BurstHits.get(0).action.getDamagePercent(), EPS,
                "Noelle C5 talent-12 Burst multiplier");
        assertClose(1.856, c5BurstHits.get(1).action.getDamagePercent(), EPS,
                "Noelle C5 talent-12 Burst skill-hit multiplier");

        double baseDef = 799.0 * 1.30;
        assertClose(191.0 + baseDef * 0.80,
                burstAtkAtConstellation(5), EPS,
                "Noelle C5 DEF conversion");
        assertClose(191.0 + baseDef * 1.30,
                burstAtkAtConstellation(6), EPS,
                "Noelle C6 additive DEF conversion");

        Noelle c6 = noelleAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.BURST);
        c6Sim.advanceTime(15.0 - c6Sim.getCurrentTime());
        assertTrue(!c6.isFormActive(c6Sim.getCurrentTime()),
                "Noelle C6 enemy-defeat extension is intentionally excluded");

        double beforeSkillAtk = c6.getEffectiveStats(c6Sim.getCurrentTime())
                .getTotalAtk();
        perform(c6Sim, CharacterActionKey.SKILL);
        assertClose(beforeSkillAtk,
                c6.getEffectiveStats(c6Sim.getCurrentTime()).getTotalAtk(),
                EPS,
                "Noelle Breastplate defensive state should add no offensive stats");
        assertThrows(IllegalArgumentException.class,
                () -> perform(c6Sim, CharacterActionKey.DASH),
                "Noelle unsupported non-offensive Dash action");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Noelle first = noelleAtConstellation(0);
        Noelle second = noelleAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);

        perform(firstSim, CharacterActionKey.SKILL);
        for (int i = 0; i < 4; i++) {
            perform(firstSim, CharacterActionKey.NORMAL);
        }
        assertClose(23.0, first.getSkillCooldownEndTime(), EPS,
                "First Noelle A4 state");
        assertTrue(second.getSkillCooldownEndTime() < 0.0,
                "Second Noelle cooldown state should remain independent");

        perform(firstSim, CharacterActionKey.BURST);
        assertTrue(first.isFormActive(firstSim.getCurrentTime()),
                "First Noelle Burst state");
        assertTrue(!second.isFormActive(secondSim.getCurrentTime()),
                "Second Noelle Burst state should remain independent");
        assertClose(191.0,
                second.getEffectiveStats(secondSim.getCurrentTime()).getTotalAtk(),
                EPS,
                "Second Noelle conversion state should remain independent");

        Noelle reused = noelleAtConstellation(0);
        CombatSimulator original = simulatorWith(reused);
        assertTrue(original.getCharacter(CharacterId.NOELLE) == reused,
                "Noelle should initialize in the first simulator");
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(reused),
                "Noelle listener state should reject cross-simulator reuse");
    }

    private static void testInvalidConstellation() {
        assertThrows(IllegalArgumentException.class,
                () -> noelleAtConstellation(-1),
                "Noelle negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> noelleAtConstellation(7),
                "Noelle constellation above six");
    }

    private static double firstBreastplateMultiplier(int constellation) {
        Noelle noelle = noelleAtConstellation(constellation);
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> hits = captureNamedActions(sim, "Breastplate");
        perform(sim, CharacterActionKey.SKILL);
        return hits.get(0).action.getDamagePercent();
    }

    private static List<ActionRecord> firstBurstHits(int constellation) {
        Noelle noelle = noelleAtConstellation(constellation);
        CombatSimulator sim = simulatorWith(noelle);
        List<ActionRecord> hits = captureSweepingTimeDamage(sim);
        perform(sim, CharacterActionKey.BURST);
        return hits;
    }

    private static double burstAtkAtConstellation(int constellation) {
        Noelle noelle = noelleAtConstellation(constellation);
        CombatSimulator sim = simulatorWith(noelle);
        perform(sim, CharacterActionKey.BURST);
        return noelle.getEffectiveStats(sim.getCurrentTime()).getTotalAtk();
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Noelle", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static Noelle noelleAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                "Constellation".equals(key) ? constellation : defaultValue;
        return new Noelle(null, null, talentData);
    }

    private static CombatSimulator simulatorWith(Noelle noelle) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(noelle);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.NOELLE,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureNoelleActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NOELLE) {
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
            if (actor.getCharacterId() == CharacterId.NOELLE
                    && actionName.equals(action.getName())) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureSweepingTimeDamage(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NOELLE
                    && action.getName().startsWith("Sweeping Time ")
                    && !"Sweeping Time Cast".equals(action.getName())) {
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
}
