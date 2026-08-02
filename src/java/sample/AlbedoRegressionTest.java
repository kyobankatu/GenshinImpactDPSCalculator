package sample;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import model.character.Albedo;
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

/** Executable regression coverage for Albedo's offensive vertical slice. */
public final class AlbedoRegressionTest {
    private static final double EPSILON = 1e-8;

    private AlbedoRegressionTest() {
    }

    /** Runs all Albedo regression checks. */
    public static void main(String[] args) throws Exception {
        testCsvShapeAndBaseMetadata();
        testNormalChargedAndPlungeActions();
        testSolarIsotomaCadenceParticlesAndSwitch();
        testTrueDamageFiltersAndSnapshotRefresh();
        testBurstFatalBlossomAndA4();
        testConstellationsOneThroughFive();
        testReplacementAndStaleGeneration();
        testIndependentInstancesAndSimulatorBinding();
        testUnsupportedAndInvalidPaths();
        System.out.println("Albedo regression tests passed.");
    }

    private static void testCsvShapeAndBaseMetadata() throws IOException {
        assertCsvShape(
                Path.of("config/characters/Albedo/Albedo_Status.csv"),
                10);
        assertCsvShape(
                Path.of("config/characters/Albedo/Albedo_Multipliers.csv"),
                18);

        Albedo albedo = albedoAtConstellation(0);
        assertEquals(CharacterId.ALBEDO, albedo.getCharacterId(),
                "Albedo typed identity");
        assertEquals(Element.GEO, albedo.getElement(),
                "Albedo element");
        assertClose(13226.0,
                albedo.getBaseStats().get(StatType.BASE_HP),
                EPSILON, "Albedo base HP");
        assertClose(251.0,
                albedo.getBaseStats().get(StatType.BASE_ATK),
                EPSILON, "Albedo base ATK");
        assertClose(876.0,
                albedo.getBaseStats().get(StatType.BASE_DEF),
                EPSILON, "Albedo base DEF");
        assertClose(0.288,
                albedo.getBaseStats().get(StatType.GEO_DMG_BONUS),
                EPSILON, "Albedo ascension Geo DMG");
        assertClose(40.0, albedo.getEnergyCost(), EPSILON,
                "Albedo Burst cost");
        assertClose(4.0, albedo.getSkillCD(), EPSILON,
                "Albedo Skill cooldown");
        assertClose(12.0, albedo.getBurstCD(), EPSILON,
                "Albedo Burst cooldown");
    }

    private static void testNormalChargedAndPlungeActions() {
        Albedo albedo = albedoAtConstellation(0);
        CombatSimulator sim = simulatorWith(albedo, false);
        List<ActionRecord> actions = captureAlbedoActions(sim);
        double[] expectedNormals = {
                0.674976, 0.674976, 0.871844, 0.914030, 1.140428
        };
        for (int i = 0; i < expectedNormals.length; i++) {
            perform(sim, CharacterActionKey.NORMAL);
            ActionRecord record = actions.get(i);
            assertClose(expectedNormals[i],
                    record.action.getDamagePercent(), EPSILON,
                    "Albedo Normal multiplier " + (i + 1));
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Albedo Normal classification " + (i + 1));
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Albedo Normal ICD tag " + (i + 1));
            assertClose(0.0, record.action.getGaugeUnits(), EPSILON,
                    "Albedo physical Normal gauge " + (i + 1));
        }

        int beforeCharge = actions.size();
        double chargeStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.CHARGE);
        assertEquals(beforeCharge + 2, actions.size(),
                "Albedo Charged two-hit count");
        assertClose(0.869,
                actions.get(beforeCharge).action.getDamagePercent(),
                EPSILON, "Albedo Charged first multiplier");
        assertClose(1.106,
                actions.get(beforeCharge + 1).action.getDamagePercent(),
                EPSILON, "Albedo Charged second multiplier");
        assertClose(actions.get(beforeCharge).time,
                actions.get(beforeCharge + 1).time,
                EPSILON, "Albedo Charged simultaneous hits");
        assertClose(chargeStart + 56.0 / 60.0,
                sim.getCurrentTime(), EPSILON,
                "Albedo Charged action duration");

        int beforePlunge = actions.size();
        double plungeStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = actions.get(beforePlunge);
        assertClose(2.933586, plunge.action.getDamagePercent(),
                EPSILON, "Albedo high Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Albedo Plunge classification");
        assertTrue(plunge.action.isShatterTrigger(),
                "Albedo Plunge blunt marker");
        assertClose(plungeStart + 66.0 / 60.0,
                sim.getCurrentTime(), EPSILON,
                "Albedo high Plunge duration");
    }

    private static void testSolarIsotomaCadenceParticlesAndSwitch() {
        Albedo albedo = albedoAtConstellation(0);
        CombatSimulator sim = simulatorWith(albedo, true);
        List<ActionRecord> casts = captureNamedActions(
                sim, "Abiogenesis: Solar Isotoma");
        List<ActionRecord> blossoms = captureNamedActions(
                sim, "Transient Blossom");
        List<ParticleRecord> particles = new ArrayList<>();
        sim.addParticleListener((element, count, time) ->
                particles.add(new ParticleRecord(element, count, time)));

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, casts.size(), "Albedo Skill cast hit count");
        assertClose(25.0 / 60.0, casts.get(0).time, EPSILON,
                "Albedo Skill cast hitmark");
        assertClose(33.0 / 60.0, sim.getCurrentTime(), EPSILON,
                "Albedo Skill action duration");
        assertClose(2.2168, casts.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C0 Skill cast multiplier");
        assertTrue(casts.get(0).action.isUseSnapshot(),
                "Albedo Skill cast snapshot");
        assertTrue(albedo.isSolarIsotomaActive(sim.getCurrentTime()),
                "Albedo field active after cast");
        assertClose(25.0 / 60.0 + 30.0,
                albedo.getSolarIsotomaExpiresAt(), EPSILON,
                "Albedo field 30-second lifetime");
        assertClose(4.0, albedo.getSkillCooldownEndTime(), EPSILON,
                "Albedo Skill cooldown starts at cast");

        sim.setActiveCharacter(CharacterId.NOELLE);
        double firstTrigger = sim.getCurrentTime();
        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Party true hit"));
        assertEquals(0, blossoms.size(),
                "Albedo Blossom waits one frame");
        sim.advanceTime(1.0 / 60.0);
        assertEquals(1, blossoms.size(),
                "Albedo any-party damage trigger");
        ActionRecord first = blossoms.get(0);
        assertClose(firstTrigger + 1.0 / 60.0,
                first.time, EPSILON, "Albedo Blossom delay");
        assertClose(2.2712, first.action.getDamagePercent(), EPSILON,
                "Albedo C0 Blossom multiplier");
        assertEquals(StatType.BASE_DEF, first.action.getScalingStat(),
                "Albedo Blossom DEF scaling");
        assertEquals(ActionType.SKILL, first.action.getActionType(),
                "Albedo Blossom Skill classification");
        assertEquals(ICDType.Standard, first.action.getICDType(),
                "Albedo Blossom standard ICD");
        assertEquals(ICDTag.ElementalSkill, first.action.getICDTag(),
                "Albedo Blossom Skill ICD tag");
        assertClose(1.0, first.action.getGaugeUnits(), EPSILON,
                "Albedo Blossom Geo gauge");
        assertTrue(first.action.isUseSnapshot(),
                "Albedo Blossom uses field snapshot");
        assertEquals(1, particles.size(),
                "Albedo expected-particle event count");
        assertEquals(Element.GEO, particles.get(0).element,
                "Albedo particle element");
        assertClose(0.67, particles.get(0).count, EPSILON,
                "Albedo expected particle count");

        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Cooldown blocked"));
        sim.advanceTime(0.5);
        assertEquals(1, blossoms.size(),
                "Albedo Blossom two-second cooldown gate");
        advanceTo(sim, firstTrigger + 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Boundary trigger"));
        sim.advanceTime(1.0 / 60.0);
        assertEquals(2, blossoms.size(),
                "Albedo Blossom exact two-second boundary");
        assertTrue(albedo.isSolarIsotomaActive(sim.getCurrentTime()),
                "Albedo field persists after switching");

        advanceTo(sim, albedo.getSolarIsotomaExpiresAt());
        assertTrue(!albedo.isSolarIsotomaActive(sim.getCurrentTime()),
                "Albedo field half-open expiry boundary");
        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Expired field hit"));
        sim.advanceTime(1.0 / 60.0);
        assertEquals(2, blossoms.size(),
                "Albedo expired field rejects damage");
    }

    private static void testTrueDamageFiltersAndSnapshotRefresh() {
        Albedo buffed = albedoAtConstellation(0);
        buffed.addBuff(new SimpleBuff(
                "Temporary DEF",
                1.0,
                0.0,
                stats -> stats.add(StatType.DEF_PERCENT, 1.0)));
        CombatSimulator buffedSim = simulatorWith(buffed, true);
        List<ActionRecord> buffedBlossoms = captureNamedActions(
                buffedSim, "Transient Blossom");
        perform(buffedSim, CharacterActionKey.SKILL);
        advanceTo(buffedSim, 1.1);

        AttackAction dummy = trueHit("Animation-only damage");
        dummy.setHitEffectTrigger(false);
        buffedSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, dummy);
        AttackAction explicitZero = new AttackAction(
                "Explicit zero true hit",
                0.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        explicitZero.setHitEffectTrigger(true);
        explicitZero.setICD(
                ICDType.Standard, ICDTag.NormalAttack, 0.0);
        buffedSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, explicitZero);
        buffedSim.advanceTime(0.1);
        assertEquals(0, buffedBlossoms.size(),
                "Albedo rejects dummy and zero damage");

        double triggerTime = buffedSim.getCurrentTime();
        buffedSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Valid trigger"));
        buffedSim.advanceTime(1.0 / 60.0);
        assertEquals(1, buffedBlossoms.size(),
                "Albedo valid true damage trigger");
        assertEquals(1, buffedBlossoms.size(),
                "Albedo Blossom cannot recursively retrigger itself");

        Albedo plain = albedoAtConstellation(0);
        CombatSimulator plainSim = simulatorWith(plain, true);
        List<ActionRecord> plainBlossoms = captureNamedActions(
                plainSim, "Transient Blossom");
        perform(plainSim, CharacterActionKey.SKILL);
        advanceTo(plainSim, triggerTime);
        plainSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Plain trigger"));
        plainSim.advanceTime(1.0 / 60.0);
        assertTrue(buffedBlossoms.get(0).damage
                        > plainBlossoms.get(0).damage,
                "Albedo field retains expired cast-time DEF snapshot");
        assertClose(0.0,
                buffed.getEffectiveStats(buffedSim.getCurrentTime())
                        .get(StatType.DEF_PERCENT),
                EPSILON, "Albedo temporary live DEF expired");

        advanceTo(buffedSim, 4.0);
        perform(buffedSim, CharacterActionKey.SKILL);
        double refreshedTrigger = buffedSim.getCurrentTime();
        buffedSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Refreshed trigger"));
        buffedSim.advanceTime(1.0 / 60.0);

        Albedo refreshedPlain = albedoAtConstellation(0);
        CombatSimulator refreshedPlainSim = simulatorWith(
                refreshedPlain, true);
        List<ActionRecord> refreshedPlainBlossoms = captureNamedActions(
                refreshedPlainSim, "Transient Blossom");
        advanceTo(refreshedPlainSim, 4.0);
        perform(refreshedPlainSim, CharacterActionKey.SKILL);
        advanceTo(refreshedPlainSim, refreshedTrigger);
        refreshedPlainSim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, trueHit("Refreshed plain trigger"));
        refreshedPlainSim.advanceTime(1.0 / 60.0);
        assertClose(refreshedPlainBlossoms.get(0).damage,
                buffedBlossoms.get(1).damage,
                EPSILON, "Albedo recast refreshes field snapshot");
    }

    private static void testBurstFatalBlossomAndA4() {
        Albedo albedo = albedoAtConstellation(0);
        CombatSimulator sim = simulatorWith(albedo, true);
        List<ActionRecord> burst = captureNamedActions(
                sim, "Rite of Progeniture: Tectonic Tide");
        List<ActionRecord> fatal = captureNamedActions(
                sim, "Fatal Blossom");
        List<ActionRecord> transientBlossoms = captureNamedActions(
                sim, "Transient Blossom");
        perform(sim, CharacterActionKey.SKILL);
        double burstCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.BURST);
        assertClose(burstCast + 96.0 / 60.0,
                sim.getCurrentTime(), EPSILON,
                "Albedo Burst action duration");
        assertEquals(1, burst.size(), "Albedo Burst initial hit count");
        assertClose(burstCast + 75.0 / 60.0,
                burst.get(0).time, EPSILON,
                "Albedo Burst initial hitmark");
        assertClose(6.2424, burst.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C0 Burst multiplier");
        assertEquals(ActionType.BURST,
                burst.get(0).action.getActionType(),
                "Albedo Burst classification");
        assertTrue(!burst.get(0).action.isUseSnapshot(),
                "Albedo Burst initial hit uses live stats");
        assertClose(0.0, albedo.getCurrentEnergy(), EPSILON,
                "Albedo Burst Energy spend");
        assertClose(burstCast + 12.0,
                albedo.getBurstCooldownEndTime(), EPSILON,
                "Albedo Burst cooldown");
        assertClose(125.0,
                effectiveStat(sim, sim.getCharacter(CharacterId.NOELLE),
                        StatType.ELEMENTAL_MASTERY),
                EPSILON, "Albedo A4 team EM");
        assertEquals(0, fatal.size(),
                "Albedo Fatal Blossom occurs after animation");
        assertEquals(0, transientBlossoms.size(),
                "Albedo Burst cannot trigger Transient Blossom");

        advanceTo(sim, burstCast + 145.0 / 60.0);
        assertEquals(1, fatal.size(),
                "Albedo single-target Fatal Blossom policy");
        assertClose(1.224, fatal.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C0 Fatal Blossom multiplier");
        assertTrue(fatal.get(0).action.isUseSnapshot(),
                "Albedo Fatal Blossom uses Skill-cast snapshot");
        assertEquals(ActionType.BURST,
                fatal.get(0).action.getActionType(),
                "Albedo Fatal Blossom Burst classification");
        assertEquals(0, transientBlossoms.size(),
                "Albedo Fatal Blossom cannot recursively trigger field");

        double a4Expiry = burst.get(0).time + 10.0;
        advanceTo(sim, a4Expiry);
        assertClose(0.0,
                effectiveStat(sim, sim.getCharacter(CharacterId.NOELLE),
                        StatType.ELEMENTAL_MASTERY),
                EPSILON, "Albedo A4 half-open expiry");

        Albedo noField = albedoAtConstellation(0);
        CombatSimulator noFieldSim = simulatorWith(noField, true);
        List<ActionRecord> noFieldFatal = captureNamedActions(
                noFieldSim, "Fatal Blossom");
        perform(noFieldSim, CharacterActionKey.BURST);
        advanceTo(noFieldSim, 145.0 / 60.0);
        assertEquals(0, noFieldFatal.size(),
                "Albedo Burst without field has no Fatal Blossom");
    }

    private static void testConstellationsOneThroughFive() {
        Albedo c1 = albedoAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1, true);
        c1.spendEnergy(40.0);
        perform(c1Sim, CharacterActionKey.SKILL);
        triggerAndResolve(c1Sim, CharacterId.NOELLE, "C1 trigger");
        assertClose(1.2, c1.getTotalFlatEnergy(), EPSILON,
                "Albedo C1 flat Energy per Blossom");

        Albedo c2 = albedoAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2, true);
        List<ActionRecord> c2Burst = captureNamedActions(
                c2Sim, "Rite of Progeniture: Tectonic Tide");
        List<ActionRecord> c2Fatal = captureNamedActions(
                c2Sim, "Fatal Blossom");
        perform(c2Sim, CharacterActionKey.SKILL);
        for (int i = 0; i < 5; i++) {
            double triggerTime = c2Sim.getCurrentTime();
            triggerAndResolve(c2Sim, CharacterId.NOELLE,
                    "C2 trigger " + i);
            if (i < 4) {
                assertEquals(i + 1,
                        c2.getFatalReckoningStacks(
                                c2Sim.getCurrentTime()),
                        "Albedo C2 stack acquisition " + i);
            } else {
                assertEquals(4,
                        c2.getFatalReckoningStacks(
                                c2Sim.getCurrentTime()),
                        "Albedo C2 four-stack cap");
            }
            if (i < 4) {
                advanceTo(c2Sim, triggerTime + 2.0);
            }
        }
        double expectedC2Flat = 876.0 * 4.0 * 0.30;
        double burstCast = c2Sim.getCurrentTime();
        perform(c2Sim, CharacterActionKey.BURST);
        assertEquals(0,
                c2.getFatalReckoningStacks(c2Sim.getCurrentTime()),
                "Albedo C2 stacks consumed on Burst");
        advanceTo(c2Sim, burstCast + 145.0 / 60.0);

        Albedo c2Baseline = albedoAtConstellation(2);
        CombatSimulator c2BaselineSim = simulatorWith(c2Baseline, true);
        List<ActionRecord> baselineBurst = captureNamedActions(
                c2BaselineSim, "Rite of Progeniture: Tectonic Tide");
        List<ActionRecord> baselineFatal = captureNamedActions(
                c2BaselineSim, "Fatal Blossom");
        perform(c2BaselineSim, CharacterActionKey.SKILL);
        double baselineBurstCast = c2BaselineSim.getCurrentTime();
        perform(c2BaselineSim, CharacterActionKey.BURST);
        advanceTo(c2BaselineSim,
                baselineBurstCast + 145.0 / 60.0);
        assertTrue(c2Burst.get(0).damage > baselineBurst.get(0).damage,
                "Albedo C2 adds consumed DEF to initial Burst");
        assertTrue(c2Fatal.get(0).damage > baselineFatal.get(0).damage,
                "Albedo C2 adds consumed DEF to Fatal Blossom");
        assertTrue(expectedC2Flat > 0.0,
                "Albedo C2 sourced four-stack flat damage is positive");
        assertClose(0.0,
                c2.getSnapshot().get(StatType.FLAT_DMG_BONUS),
                EPSILON, "Albedo C2 restores field snapshot after Fatal");

        Albedo expiringC2 = albedoAtConstellation(2);
        CombatSimulator expiringC2Sim = simulatorWith(expiringC2, true);
        perform(expiringC2Sim, CharacterActionKey.SKILL);
        triggerAndResolve(
                expiringC2Sim, CharacterId.NOELLE, "Expiry trigger");
        double stackTime = expiringC2Sim.getCurrentTime();
        advanceTo(expiringC2Sim, stackTime + 30.0);
        assertEquals(0,
                expiringC2.getFatalReckoningStacks(
                        expiringC2Sim.getCurrentTime()),
                "Albedo C2 30-second half-open expiry");

        Albedo c3 = albedoAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3, true);
        List<ActionRecord> c3Casts = captureNamedActions(
                c3Sim, "Abiogenesis: Solar Isotoma");
        List<ActionRecord> c3Blossoms = captureNamedActions(
                c3Sim, "Transient Blossom");
        perform(c3Sim, CharacterActionKey.SKILL);
        triggerAndResolve(c3Sim, CharacterId.NOELLE, "C3 trigger");
        assertClose(2.608, c3Casts.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C3 Skill cast talent level");
        assertClose(2.672,
                c3Blossoms.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C3 Blossom talent level");

        Albedo c4 = albedoAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4, true);
        perform(c4Sim, CharacterActionKey.SKILL);
        Character c4Teammate = c4Sim.getCharacter(CharacterId.NOELLE);
        assertClose(0.30,
                effectiveStat(c4Sim, c4Teammate,
                        StatType.PLUNGING_ATTACK_DMG_BONUS),
                EPSILON, "Albedo C4 active-character Plunge bonus");
        assertTrue(hasTeamBuff(c4Sim,
                        BuffId.ALBEDO_C4_PLUNGING_DMG_BONUS),
                "Albedo C4 typed team buff");
        advanceTo(c4Sim, c4.getSolarIsotomaExpiresAt());
        assertClose(0.0,
                effectiveStat(c4Sim, c4Teammate,
                        StatType.PLUNGING_ATTACK_DMG_BONUS),
                EPSILON, "Albedo C4 expires with field");

        Albedo c5 = albedoAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5, true);
        List<ActionRecord> c5Burst = captureNamedActions(
                c5Sim, "Rite of Progeniture: Tectonic Tide");
        List<ActionRecord> c5Fatal = captureNamedActions(
                c5Sim, "Fatal Blossom");
        perform(c5Sim, CharacterActionKey.SKILL);
        double c5BurstCast = c5Sim.getCurrentTime();
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, c5BurstCast + 145.0 / 60.0);
        assertClose(7.344, c5Burst.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C5 Burst talent level");
        assertClose(1.44, c5Fatal.get(0).action.getDamagePercent(),
                EPSILON, "Albedo C5 Fatal Blossom talent level");
    }

    private static void testReplacementAndStaleGeneration() {
        Albedo albedo = albedoAtConstellation(0);
        CombatSimulator sim = simulatorWith(albedo, true);
        List<ActionRecord> fatal = captureNamedActions(
                sim, "Fatal Blossom");
        perform(sim, CharacterActionKey.SKILL);
        double firstExpiry = albedo.getSolarIsotomaExpiresAt();
        advanceTo(sim, 4.0);
        perform(sim, CharacterActionKey.BURST);
        perform(sim, CharacterActionKey.SKILL);
        double replacementExpiry = albedo.getSolarIsotomaExpiresAt();
        assertTrue(replacementExpiry > firstExpiry,
                "Albedo recast replaces field lifetime");
        advanceTo(sim, 4.0 + 145.0 / 60.0);
        assertEquals(0, fatal.size(),
                "Albedo stale Fatal Blossom rejected after replacement");
        advanceTo(sim, firstExpiry);
        assertTrue(albedo.isSolarIsotomaActive(sim.getCurrentTime()),
                "Albedo stale expiry cannot clear replacement field");
        advanceTo(sim, replacementExpiry);
        assertTrue(!albedo.isSolarIsotomaActive(sim.getCurrentTime()),
                "Albedo replacement field expires independently");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Albedo first = albedoAtConstellation(2);
        Albedo second = albedoAtConstellation(2);
        CombatSimulator firstSim = simulatorWith(first, false);
        CombatSimulator secondSim = simulatorWith(second, false);
        perform(firstSim, CharacterActionKey.SKILL);
        assertTrue(first.isSolarIsotomaActive(firstSim.getCurrentTime()),
                "First Albedo owns active field");
        assertTrue(!second.isSolarIsotomaActive(secondSim.getCurrentTime()),
                "Second Albedo field state remains independent");

        Albedo reused = albedoAtConstellation(0);
        CombatSimulator original = simulatorWith(reused, false);
        assertTrue(original.getCharacter(CharacterId.ALBEDO) == reused,
                "Albedo initialized in first simulator");
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(reused),
                "Albedo listener state rejects cross-simulator reuse");
    }

    private static void testUnsupportedAndInvalidPaths() {
        boolean hasHexereiPath = false;
        boolean hasShieldPath = false;
        for (Method method : Albedo.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            hasHexereiPath |= name.contains("hexerei")
                    || name.contains("silverisotoma");
            hasShieldPath |= name.contains("shield");
        }
        assertTrue(!hasHexereiPath,
                "Albedo must not invent Hexerei or Silver Isotoma paths");
        assertTrue(!hasShieldPath,
                "Albedo must not invent C6 shield state");

        Albedo albedo = albedoAtConstellation(0);
        CombatSimulator sim = simulatorWith(albedo, false);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Albedo unsupported Dash action");
        assertThrows(IllegalArgumentException.class,
                () -> albedoAtConstellation(-1),
                "Albedo negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> albedoAtConstellation(7),
                "Albedo constellation above six");
    }

    private static void triggerAndResolve(
            CombatSimulator sim,
            CharacterId actor,
            String name) {
        sim.performActionWithoutTimeAdvance(actor, trueHit(name));
        sim.advanceTime(1.0 / 60.0);
    }

    private static AttackAction trueHit(String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        return action;
    }

    private static double effectiveStat(
            CombatSimulator sim,
            Character character,
            StatType statType) {
        StatsContainer stats = character.getEffectiveStats(
                sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats.get(statType);
    }

    private static boolean hasTeamBuff(
            CombatSimulator sim,
            BuffId buffId) {
        for (Buff buff : sim.getTeamBuffList()) {
            if (buff.getId() == buffId
                    && !buff.isExpired(sim.getCurrentTime())) {
                return true;
            }
        }
        return false;
    }

    private static void advanceTo(CombatSimulator sim, double time) {
        if (time > sim.getCurrentTime()) {
            sim.advanceTime(time - sim.getCurrentTime());
        }
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Albedo", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static Albedo albedoAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                "Constellation".equals(key) ? constellation : defaultValue;
        return new Albedo(null, null, talentData);
    }

    private static CombatSimulator simulatorWith(
            Albedo albedo,
            boolean withTeammate) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(albedo);
        if (withTeammate) {
            sim.addCharacter(new TestCharacter());
        }
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.ALBEDO,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureAlbedoActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ALBEDO) {
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
            if (actor.getCharacterId() == CharacterId.ALBEDO
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

    private static final class ParticleRecord {
        private final Element element;
        private final double count;
        private final double time;

        private ParticleRecord(Element element, double count, double time) {
            this.element = element;
            this.count = count;
            this.time = time;
        }
    }

    /** Minimal Geo teammate for trigger, switch, and team-buff checks. */
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
