package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.character.Collei;
import model.character.Eula;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Eula's physical Lightfall vertical slice. */
public final class EulaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private EulaRegressionTest() {
    }

    /** Runs data, timing, boundary, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstructors();
        testNormalStringAndHighPlunge();
        testSkillModesParticlesAndGrimheart();
        testHoldSkillIcewhirlA1ShredAndExpiry();
        testA4AndBurstTiming();
        testLightfallGateCapDynamicStatsAndExpiry();
        testSwitchExplosionAndConstellations();
        testRepeatedRestoreAndStaleGenerations();
        testInvalidInputsCooldownAndEnergyRejection();
        System.out.println("EulaRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstructors()
            throws IOException {
        Eula eula = deterministicEula(6);
        assertEquals(CharacterId.EULA, eula.getCharacterId(),
                "Eula typed identity");
        assertEquals(Element.CRYO, eula.getElement(), "Eula element");
        assertClose(13226.0,
                eula.getBaseStats().get(StatType.BASE_HP), "Eula base HP");
        assertClose(342.0,
                eula.getBaseStats().get(StatType.BASE_ATK), "Eula base ATK");
        assertClose(751.0,
                eula.getBaseStats().get(StatType.BASE_DEF), "Eula base DEF");
        assertClose(0.884,
                eula.getBaseStats().get(StatType.CRIT_DMG),
                "Eula base plus ascension CRIT DMG");
        assertClose(80.0, eula.getEnergyCost(), "Eula Energy cost");
        assertClose(4.0, eula.getSkillCD(), "Eula initial Skill cooldown");
        assertClose(20.0, eula.getBurstCD(), "Eula Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.EULA,
                    deterministicEula(constellation).getCharacterId(),
                    "Eula constellation " + constellation);
        }
        assertCsvShape(Paths.get(
                "config/characters/Eula/Eula_Status.csv"), 10);
        assertCsvShape(Paths.get(
                "config/characters/Eula/Eula_Multipliers.csv"), 24);
        assertTrue(Files.exists(Paths.get(
                "config/characters/Eula/face.png")),
                "Eula face asset remains present");
    }

    private static void testNormalStringAndHighPlunge() {
        Eula eula = deterministicEula(0);
        CombatSimulator simulator = simulatorWith(eula);
        List<ActionRecord> records = captureEulaActions(simulator);
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        double[] multipliers = {
                1.648572, 1.718724, 1.043511, 1.043511,
                2.069484, 1.319735, 1.319735
        };
        double[] frames = { 30, 53, 95, 112, 143, 199, 226 };
        assertEquals(7, records.size(), "Eula N1-N5 damage instances");
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Eula Normal multiplier " + index);
            assertClose(frames[index] * FRAME, record.time,
                    "Eula Normal impact " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Eula Normal category");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(), "Eula Normal ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(), "Eula Normal ICD tag");
            assertClose(0.0, record.action.getGaugeUnits(),
                    "Eula Physical Normal gauge");
            assertTrue(record.action.isShatterTrigger(),
                    "Eula claymore Normal is blunt");
        }
        assertClose(275.0 * FRAME, simulator.getCurrentTime(),
                "Eula N1-N5 animation durations");

        Eula plungeEula = deterministicEula(0);
        CombatSimulator plungeSim = simulatorWith(plungeEula);
        List<ActionRecord> plungeRecords = captureEulaActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = plungeRecords.get(0);
        assertClose(41.0 * FRAME, plunge.time,
                "Eula High Plunge hitmark");
        assertClose(84.0 * FRAME, plungeSim.getCurrentTime(),
                "Eula High Plunge fixed duration");
        assertClose(3.422517, plunge.action.getDamagePercent(),
                "Eula High Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Eula High Plunge category");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Eula High Plunge ICD");
        assertEquals(ICDTag.None, plunge.action.getICDTag(),
                "Eula High Plunge has no ICD tag");
        assertTrue(plunge.action.isShatterTrigger(),
                "Eula High Plunge is blunt");
    }

    private static void testSkillModesParticlesAndGrimheart() {
        CountingSupplier particleDraws = new CountingSupplier(0.0, 0.5);
        Eula eula = new Eula(
                null, null, 0, particleDraws, () -> 1.0);
        CombatSimulator simulator = simulatorWith(eula);
        List<ActionRecord> records = captureEulaActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);
        simulator.performAction(
                CharacterId.EULA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(1, named(records, "Icetide Vortex (Press)").size(),
                "legacy Skill mode is Press");
        ActionRecord press = records.get(0);
        assertClose(20.0 * FRAME, press.time, "Eula Press hitmark");
        assertClose(2.4888, press.action.getDamagePercent(),
                "Eula Press multiplier");
        assertEquals(ICDType.None, press.action.getICDType(),
                "Eula Press no ICD");
        assertEquals(ICDTag.None, press.action.getICDTag(),
                "Eula Press has no ICD tag");
        assertClose(1.0, press.action.getGaugeUnits(),
                "Eula Press 1U gauge");
        assertTrue(press.action.isShatterTrigger(),
                "Eula Press is blunt");
        assertEquals(1, eula.getGrimheartStacks(simulator.getCurrentTime()),
                "Eula Press grants one Grimheart");
        advanceTo(simulator, 120.0 * FRAME);
        assertEquals(1, particles.size(), "Eula Press particle event");
        assertClose(2.0, particles.get(0).count,
                "Eula Press low draw grants two particles");
        assertClose(120.0 * FRAME, particles.get(0).time,
                "Eula Press particle arrival");

        Eula holdEula = new Eula(
                null, null, 0, () -> 0.5, () -> 1.0);
        CombatSimulator holdSim = simulatorWith(holdEula);
        List<ActionRecord> holdRecords = captureEulaActions(holdSim);
        List<ParticleRecord> holdParticles = captureCryoParticles(holdSim);
        performHold(holdSim);
        ActionRecord hold = named(
                holdRecords, "Icetide Vortex (Hold)").get(0);
        assertClose(49.0 * FRAME, hold.time, "Eula Hold hitmark");
        assertClose(4.1752, hold.action.getDamagePercent(),
                "Eula Hold multiplier");
        assertEquals(ICDType.None, hold.action.getICDType(),
                "Eula Hold no ICD");
        assertEquals(ICDTag.None, hold.action.getICDTag(),
                "Eula Hold has no ICD tag");
        assertTrue(hold.action.isShatterTrigger(), "Eula Hold is blunt");
        advanceTo(holdSim, 149.0 * FRAME);
        assertClose(2.0, holdParticles.get(0).count,
                "Eula Hold threshold draw grants two particles");
        assertClose(149.0 * FRAME, holdParticles.get(0).time,
                "Eula Hold particle arrival");
        assertEquals(1, particleDraws.getCount(),
                "unused particle draw is not consumed");

        Eula expiryEula = deterministicEula(0);
        CombatSimulator expirySim = simulatorWith(expiryEula);
        perform(expirySim, CharacterActionKey.SKILL);
        assertEquals(1, expiryEula.getGrimheartStacks(
                20.0 * FRAME + 18.0 - EPSILON),
                "Grimheart survives immediately before 18 seconds");
        assertEquals(0, expiryEula.getGrimheartStacks(
                20.0 * FRAME + 18.0),
                "Grimheart expires exactly at 18 seconds");
    }

    private static void testHoldSkillIcewhirlA1ShredAndExpiry() {
        Eula eula = deterministicEula(1);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(eula, ally);
        List<ActionRecord> records = captureEulaActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(2, eula.getGrimheartStacks(simulator.getCurrentTime()),
                "Eula stores two Grimheart stacks");
        double holdStart = simulator.getCurrentTime()
                + eula.getSkillCDRemaining(simulator.getCurrentTime());
        performHold(simulator);
        advanceTo(simulator, holdStart + 109.0 * FRAME);
        List<ActionRecord> icewhirls = named(
                records, "Icetide Vortex (Icewhirl)");
        assertEquals(2, icewhirls.size(),
                "two consumed Grimheart creates two Icewhirls");
        assertClose(holdStart + 79.0 * FRAME, icewhirls.get(0).time,
                "first Icewhirl hitmark");
        assertClose(holdStart + 92.0 * FRAME, icewhirls.get(1).time,
                "second Icewhirl hitmark");
        for (ActionRecord icewhirl : icewhirls) {
            assertClose(1.632, icewhirl.action.getDamagePercent(),
                    "Eula Icewhirl multiplier");
            assertEquals(ICDType.Standard,
                    icewhirl.action.getICDType(), "Icewhirl standard ICD");
            assertEquals(ICDTag.ElementalSkill,
                    icewhirl.action.getICDTag(), "Icewhirl shared Skill ICD");
            assertTrue(!icewhirl.action.isShatterTrigger(),
                    "Icewhirl is not blunt");
        }
        ActionRecord a1 = named(
                records, "Roiling Rime Shattered").get(0);
        assertClose(holdStart + 108.0 * FRAME, a1.time,
                "Eula A1 hitmark");
        assertClose(6.74344 * 0.50, a1.action.getDamagePercent(),
                "Eula A1 uses half Lightfall base");
        assertEquals(ActionType.BURST, a1.action.getActionType(),
                "Eula A1 is Burst damage");
        assertEquals(ICDTag.None, a1.action.getICDTag(),
                "Eula A1 has no ICD tag");
        assertTrue(a1.action.isShatterTrigger(), "Eula A1 is blunt");
        assertEquals(0, eula.getGrimheartStacks(simulator.getCurrentTime()),
                "Eula Hold consumes Grimheart");

        double shredRefresh = holdStart + 92.0 * FRAME;
        assertClose(0.24,
                applicableStat(simulator, ally,
                        StatType.PHYS_RES_SHRED,
                        shredRefresh + 14.0 - EPSILON),
                "two-stack Physical shred lasts 14 seconds");
        assertClose(0.24,
                applicableStat(simulator, ally,
                        StatType.CRYO_RES_SHRED,
                        shredRefresh + 14.0 - EPSILON),
                "two-stack Cryo shred lasts 14 seconds");
        assertClose(0.0,
                applicableStat(simulator, ally,
                        StatType.PHYS_RES_SHRED,
                        shredRefresh + 14.0),
                "Icewhirl shred expires exactly");
        assertEquals(1,
                countTeamBuffs(simulator,
                        BuffId.EULA_ICEWHIRL_RES_SHRED),
                "Icewhirl refresh remains non-stacking");
        assertClose(0.30,
                eula.getEffectiveStats(holdStart + 18.0 - EPSILON)
                        .get(StatType.PHYSICAL_DMG_BONUS),
                "two-stack C1 lasts 18 seconds");
        assertClose(0.0,
                eula.getEffectiveStats(holdStart + 18.0)
                        .get(StatType.PHYSICAL_DMG_BONUS),
                "C1 expires exactly");
    }

    private static void testA4AndBurstTiming() {
        Eula eula = deterministicEula(0);
        CombatSimulator simulator = simulatorWith(eula);
        List<ActionRecord> records = captureEulaActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(eula.getSkillCDRemaining(
                simulator.getCurrentTime()) > 0.0,
                "Eula Skill enters cooldown");
        double burstStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0,
                eula.getSkillCDRemaining(simulator.getCurrentTime()),
                "Eula A4 resets Skill cooldown");
        assertEquals(2, eula.getGrimheartStacks(simulator.getCurrentTime()),
                "Eula A4 grants and refreshes Grimheart");
        assertEquals(2, eula.getGrimheartStacks(
                burstStart + 18.0 - EPSILON),
                "Eula A4 Grimheart survives immediately before 18 seconds");
        assertEquals(0, eula.getGrimheartStacks(burstStart + 18.0),
                "Eula A4 Grimheart expires exactly at 18 seconds");
        ActionRecord initial = named(
                records, "Glacial Illumination (Initial)").get(0);
        assertClose(burstStart + 100.0 * FRAME, initial.time,
                "Eula Burst initial hitmark");
        assertClose(4.1752, initial.action.getDamagePercent(),
                "Eula C0 Burst initial multiplier");
        assertClose(2.0, initial.action.getGaugeUnits(),
                "Eula Burst initial 2U gauge");
        assertEquals(ICDType.None, initial.action.getICDType(),
                "Eula Burst initial no ICD");
        assertEquals(ICDTag.None, initial.action.getICDTag(),
                "Eula Burst initial has no ICD tag");
        assertTrue(initial.action.isShatterTrigger(),
                "Eula Burst initial is blunt");
        assertClose(burstStart + 123.0 * FRAME,
                simulator.getCurrentTime(), "Eula Burst duration");
        assertClose(0.0, eula.getCurrentEnergy(),
                "Eula Burst Energy consumed at frame 107");
        assertClose(20.0 - 26.0 * FRAME,
                eula.getBurstCDRemaining(simulator.getCurrentTime()),
                "Eula Burst cooldown starts at frame 97");
        assertEquals(0, eula.getLightfallStacks(),
                "Burst initial hit grants no Lightfall stack");
        assertTrue(eula.isLightfallActive(simulator.getCurrentTime()),
                "Lightfall activates at frame 117");
    }

    private static void testLightfallGateCapDynamicStatsAndExpiry() {
        Eula gateEula = deterministicEula(0);
        CombatSimulator gateSim = simulatorWith(gateEula);
        List<ActionRecord> gateRecords = captureEulaActions(gateSim);
        perform(gateSim, CharacterActionKey.BURST);
        perform(gateSim, CharacterActionKey.PLUNGE);
        assertEquals(0, gateEula.getLightfallStacks(),
                "High Plunge does not grant Lightfall stacks");
        gateSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        gateSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.SKILL));
        assertEquals(1, gateEula.getLightfallStacks(),
                "Lightfall 0.1-second gate blocks simultaneous damage");
        gateSim.advanceTime(6.0 * FRAME);
        gateSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.BURST));
        assertEquals(2, gateEula.getLightfallStacks(),
                "Lightfall gate accepts exact 0.1-second boundary");
        advanceTo(gateSim, 565.0 * FRAME);
        gateSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        assertEquals(2, gateEula.getLightfallStacks(),
                "Lightfall rejects stacks at exact lock frame");
        advanceTo(gateSim, 600.0 * FRAME);
        ActionRecord lightfall = named(
                gateRecords, "Glacial Illumination (Lightfall").get(0);
        assertClose(600.0 * FRAME, lightfall.time,
                "natural Lightfall impacts at frame 600");
        assertClose(6.74344 + 2.0 * 1.37776,
                lightfall.action.getDamagePercent(),
                "Lightfall uses locked stack multiplier");
        assertTrue(!lightfall.action.hasStatSnapshot(),
                "Lightfall does not snapshot stats");
        assertEquals(ICDTag.None, lightfall.action.getICDTag(),
                "Lightfall has no ICD tag");
        assertEquals(0, gateEula.getLightfallStacks(),
                "Lightfall impact cannot grant itself a stack");

        CountingSupplier c6Draws = new CountingSupplier(0.0);
        Eula c6 = new Eula(null, null, 6, () -> 1.0, c6Draws);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.BURST);
        assertEquals(5, c6.getLightfallStacks(),
                "Eula C6 starts with five Lightfall stacks");
        for (int hit = 0; hit < 20; hit++) {
            c6Sim.performActionWithoutTimeAdvance(
                    CharacterId.EULA, stackProbe(ActionType.NORMAL));
            c6Sim.advanceTime(6.0 * FRAME);
        }
        assertEquals(30, c6.getLightfallStacks(),
                "Lightfall stack count caps at 30");
        assertEquals(20, c6Draws.getCount(),
                "C6 consumes one injected draw per qualifying hit");

        double plainDamage = naturalLightfallDamage(false);
        double buffedDamage = naturalLightfallDamage(true);
        assertClose(2.0, buffedDamage / plainDamage,
                "Lightfall reads live impact-time ATK stats");
    }

    private static void testSwitchExplosionAndConstellations() {
        Eula switchEula = deterministicEula(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSim = simulatorWith(switchEula, ally);
        List<ActionRecord> switchRecords = captureEulaActions(switchSim);
        perform(switchSim, CharacterActionKey.BURST);
        double switchTime = switchSim.getCurrentTime();
        switchSim.switchCharacter(CharacterId.NOELLE);
        advanceTo(switchSim, switchTime + 35.0 * FRAME);
        List<ActionRecord> switchedLightfall = named(
                switchRecords, "Glacial Illumination (Lightfall");
        assertEquals(1, switchedLightfall.size(),
                "switch queues one Lightfall explosion");
        assertClose(switchTime + 35.0 * FRAME,
                switchedLightfall.get(0).time,
                "switch Lightfall delay is 35 frames");
        advanceTo(switchSim, 11.0);
        assertEquals(1, switchedLightfall.size(),
                "natural stale event cannot repeat switched Lightfall");

        Eula c1 = deterministicEula(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        perform(c1Sim, CharacterActionKey.SKILL);
        double oneStackHold = c1Sim.getCurrentTime()
                + c1.getSkillCDRemaining(c1Sim.getCurrentTime());
        performHold(c1Sim);
        assertClose(0.30,
                c1.getEffectiveStats(oneStackHold + 12.0 - EPSILON)
                        .get(StatType.PHYSICAL_DMG_BONUS),
                "one-stack C1 lasts 12 seconds");
        assertClose(0.0,
                c1.getEffectiveStats(oneStackHold + 12.0)
                        .get(StatType.PHYSICAL_DMG_BONUS),
                "one-stack C1 exact expiry");

        Eula c2 = deterministicEula(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        performHold(c2Sim);
        assertClose(4.0, c2.getSkillCD(),
                "Eula C2 Hold cooldown equals Press");
        Eula c0 = deterministicEula(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        performHold(c0Sim);
        assertClose(10.0, c0.getSkillCD(),
                "Eula C0 Hold cooldown is ten seconds");

        Eula c3 = deterministicEula(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureEulaActions(c3Sim);
        perform(c3Sim, CharacterActionKey.BURST);
        c3Sim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        advanceTo(c3Sim, 600.0 * FRAME);
        assertClose(4.912,
                named(c3Records, "Glacial Illumination (Initial)")
                        .get(0).action.getDamagePercent(),
                "Eula C3 uses Burst talent 12 initial");
        assertClose(8.532586 + 1.743302,
                named(c3Records, "Glacial Illumination (Lightfall")
                        .get(0).action.getDamagePercent(),
                "Eula C3 uses Burst talent 12 Lightfall values");

        Eula c5 = deterministicEula(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureEulaActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(2.928,
                named(c5Records, "Icetide Vortex (Press)")
                        .get(0).action.getDamagePercent(),
                "Eula C5 uses Skill talent 12 Press");
        performHold(c5Sim);
        assertClose(4.912,
                named(c5Records, "Icetide Vortex (Hold)")
                        .get(0).action.getDamagePercent(),
                "Eula C5 uses Skill talent 12 Hold");
        ActionRecord c5Icewhirl = named(
                c5Records, "Icetide Vortex (Icewhirl)").get(0);
        assertClose(1.92, c5Icewhirl.action.getDamagePercent(),
                "Eula C5 uses Skill talent 12 Icewhirl");
        assertClose(0.25,
                applicableStat(c5Sim, c5,
                        StatType.PHYS_RES_SHRED,
                        c5Icewhirl.time + EPSILON),
                "Eula C5 uses 25 percent resistance shred");

        CountingSupplier thresholdDraws = new CountingSupplier(0.5, 0.499);
        Eula c6 = new Eula(
                null, null, 6, () -> 1.0, thresholdDraws);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.BURST);
        c6Sim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        c6Sim.advanceTime(6.0 * FRAME);
        c6Sim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        assertEquals(8, c6.getLightfallStacks(),
                "C6 threshold excludes 0.5 and includes below 0.5");
    }

    private static void testRepeatedRestoreAndStaleGenerations() {
        Eula particleEula = deterministicEula(0);
        CombatSimulator particleSim = simulatorWith(particleEula);
        List<ParticleRecord> particles = captureCryoParticles(particleSim);
        perform(particleSim, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSim.saveSnapshot();
        particleSim.restoreSnapshot(particleSnapshot);
        particleSim.restoreSnapshot(particleSnapshot);
        advanceTo(particleSim, 3.0);
        assertEquals(1, particles.size(),
                "repeated pre-particle restore resolves once");

        Eula listenerEula = deterministicEula(0);
        CombatSimulator listenerSim = simulatorWith(listenerEula);
        List<ActionRecord> listenerRecords = captureEulaActions(listenerSim);
        List<ParticleRecord> listenerParticles = captureCryoParticles(
                listenerSim);
        SimulatorSnapshot[] listenerSnapshot = new SimulatorSnapshot[1];
        listenerSim.addDamageListener((actor, action, damage, time) -> {
            if (listenerSnapshot[0] == null
                    && actor == listenerEula
                    && action.getActionType() == ActionType.SKILL) {
                listenerSnapshot[0] = listenerSim.saveSnapshot();
            }
        });
        perform(listenerSim, CharacterActionKey.SKILL);
        listenerRecords.clear();
        listenerParticles.clear();
        listenerSim.restoreSnapshot(listenerSnapshot[0]);
        advanceTo(listenerSim, 3.0);
        assertEquals(0, listenerRecords.size(),
                "listener snapshot does not restore completed Skill hit");
        assertEquals(1, listenerParticles.size(),
                "listener snapshot preserves pre-sampled particle command");

        Eula burstEula = deterministicEula(0);
        CombatSimulator burstSim = simulatorWith(burstEula);
        List<ActionRecord> burstRecords = captureEulaActions(burstSim);
        perform(burstSim, CharacterActionKey.BURST);
        burstSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        SimulatorSnapshot burstSnapshot = burstSim.saveSnapshot();
        burstSim.advanceTime(6.0 * FRAME);
        burstSim.performActionWithoutTimeAdvance(
                CharacterId.EULA, stackProbe(ActionType.NORMAL));
        assertEquals(2, burstEula.getLightfallStacks(),
                "post-snapshot branch mutates Lightfall");
        burstSim.restoreSnapshot(burstSnapshot);
        burstSim.restoreSnapshot(burstSnapshot);
        assertEquals(1, burstEula.getLightfallStacks(),
                "Lightfall snapshot is deep copied");
        advanceTo(burstSim, 600.0 * FRAME);
        List<ActionRecord> restoredLightfall = named(
                burstRecords, "Glacial Illumination (Lightfall");
        assertEquals(1, restoredLightfall.size(),
                "repeated mid-Burst restore explodes once");
        assertClose(6.74344 + 1.37776,
                restoredLightfall.get(0).action.getDamagePercent(),
                "restored Lightfall preserves saved stack count");

        Eula staleSkill = deterministicEula(0);
        CombatSimulator staleSkillSim = simulatorWith(staleSkill);
        List<ActionRecord> staleSkillRecords = captureEulaActions(
                staleSkillSim);
        List<ParticleRecord> staleParticles = captureCryoParticles(
                staleSkillSim);
        SimulatorSnapshot[] preSkillHit = captureSnapshotAt(
                staleSkillSim, 10.0 * FRAME);
        perform(staleSkillSim, CharacterActionKey.SKILL);
        staleSkillRecords.clear();
        staleParticles.clear();
        staleSkillSim.restoreSnapshot(preSkillHit[0]);
        perform(staleSkillSim, CharacterActionKey.SKILL);
        advanceTo(staleSkillSim, 4.0);
        assertEquals(1,
                named(staleSkillRecords, "Icetide Vortex (Press)").size(),
                "stale Skill generation suppresses damage");
        assertEquals(1, staleParticles.size(),
                "stale Skill generation suppresses particles");

        Eula staleBurst = deterministicEula(0);
        CombatSimulator staleBurstSim = simulatorWith(staleBurst);
        List<ActionRecord> staleBurstRecords = captureEulaActions(
                staleBurstSim);
        SimulatorSnapshot[] preBurstEvents = captureSnapshotAt(
                staleBurstSim, 50.0 * FRAME);
        perform(staleBurstSim, CharacterActionKey.BURST);
        staleBurstRecords.clear();
        staleBurstSim.restoreSnapshot(preBurstEvents[0]);
        perform(staleBurstSim, CharacterActionKey.BURST);
        advanceTo(staleBurstSim, 12.0);
        assertEquals(1,
                named(staleBurstRecords,
                        "Glacial Illumination (Initial)").size(),
                "stale Burst generation suppresses initial damage");
        assertEquals(1,
                named(staleBurstRecords,
                        "Glacial Illumination (Lightfall").size(),
                "stale Burst generation suppresses explosion");
        assertEquals(1, staleBurst.getBurstEnergyMarkers().size(),
                "stale Burst generation suppresses Energy command");
    }

    private static void testInvalidInputsCooldownAndEnergyRejection() {
        assertThrows(IllegalArgumentException.class,
                () -> new Eula(null, null, -1),
                "Eula rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Eula(null, null, 7),
                "Eula rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Eula(null, null, 0, null, () -> 0.0),
                "Eula rejects null particle source");
        assertThrows(IllegalArgumentException.class,
                () -> new Eula(null, null, 0, () -> 0.0, null),
                "Eula rejects null C6 source");

        Eula invalidParticle = new Eula(
                null, null, 0, () -> Double.NaN, () -> 1.0);
        CombatSimulator invalidParticleSim = simulatorWith(invalidParticle);
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidParticleSim, CharacterActionKey.SKILL),
                "Eula rejects non-finite particle draw");
        Eula invalidC6 = new Eula(
                null, null, 6, () -> 1.0, () -> 1.1);
        CombatSimulator invalidC6Sim = simulatorWith(invalidC6);
        perform(invalidC6Sim, CharacterActionKey.BURST);
        assertThrows(IllegalArgumentException.class,
                () -> invalidC6Sim.performActionWithoutTimeAdvance(
                        CharacterId.EULA, stackProbe(ActionType.NORMAL)),
                "Eula rejects out-of-range C6 draw");

        Eula unsupported = deterministicEula(0);
        CombatSimulator unsupportedSim = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.CHARGE),
                "Eula rejects unsupported Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.DASH),
                "Eula rejects unsupported Dash");
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSim),
                "Eula rejects null action");

        CountingSupplier skippedC6 = new CountingSupplier(0.0);
        Eula insufficient = new Eula(
                null, null, 6, () -> 0.0, skippedC6);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Eula rejects Burst with insufficient Energy");
        assertEquals(0, skippedC6.getCount(),
                "rejected Burst consumes no C6 draw");

        Eula cooldown = deterministicEula(0);
        CombatSimulator cooldownSim = simulatorWith(cooldown);
        perform(cooldownSim, CharacterActionKey.SKILL);
        perform(cooldownSim, CharacterActionKey.SKILL);
        assertClose(304.0 * FRAME, cooldownSim.getCurrentTime(),
                "Eula serializes Skill at cooldown boundary");

        Eula reusable = deterministicEula(0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Eula rejects cross-simulator reuse");
        Eula stateOwner = deterministicEula(0);
        Eula foreignEula = deterministicEula(0);
        assertThrows(IllegalArgumentException.class,
                () -> stateOwner.restoreCharacterState(
                        foreignEula.captureCharacterState(),
                        new CombatSimulator()),
                "Eula rejects another Eula's state");
        assertThrows(IllegalArgumentException.class,
                () -> stateOwner.restoreCharacterState(
                        new Collei(null, null, 0).captureCharacterState(),
                        new CombatSimulator()),
                "Eula rejects foreign character state");
    }

    private static double naturalLightfallDamage(boolean addImpactBuff) {
        Eula eula = deterministicEula(0);
        CombatSimulator simulator = simulatorWith(eula);
        List<ActionRecord> records = captureEulaActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        if (addImpactBuff) {
            addBuffAt(
                    simulator,
                    eula,
                    590.0 * FRAME,
                    new TestBuff(
                            "Eula dynamic Lightfall probe",
                            590.0 * FRAME,
                            2.0,
                            StatType.ATK_PERCENT,
                            1.0));
        }
        advanceTo(simulator, 600.0 * FRAME);
        return named(records, "Glacial Illumination (Lightfall")
                .get(0).damage;
    }

    private static AttackAction stackProbe(ActionType actionType) {
        AttackAction action = new AttackAction(
                "Eula Lightfall Stack Probe",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static Eula deterministicEula(int constellation) {
        return new Eula(
                null, null, constellation, () -> 1.0, () -> 1.0);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
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
                CharacterId.EULA, CharacterActionRequest.of(key));
    }

    private static void performHold(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.EULA,
                CharacterActionRequest.skill(SkillActionMode.HOLD));
    }

    private static List<ActionRecord> captureEulaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.EULA) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
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

    private static int countTeamBuffs(
            CombatSimulator simulator,
            BuffId id) {
        int count = 0;
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                count++;
            }
        }
        return count;
    }

    private static void addBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            Buff buff) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                character.addBuff(buff);
            }
        });
    }

    private static SimulatorSnapshot[] captureSnapshotAt(
            CombatSimulator simulator,
            double time) {
        SimulatorSnapshot[] snapshot = new SimulatorSnapshot[1];
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                snapshot[0] = activeSim.saveSnapshot();
            }
        });
        return snapshot;
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
            assertTrue(lines.get(index).startsWith("Eula,"),
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
        }

        @Override
        public double getEnergyCost() {
            return 100.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }

    private static final class CountingSupplier implements DoubleSupplier {
        private final double[] values;
        private int count;

        private CountingSupplier(double... values) {
            this.values = values;
        }

        @Override
        public double getAsDouble() {
            double value = values[Math.min(count, values.length - 1)];
            count++;
            return value;
        }

        private int getCount() {
            return count;
        }
    }
}
