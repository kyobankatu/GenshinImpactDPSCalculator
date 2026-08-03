package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Mona;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Mona's classic offensive vertical slice. */
public final class MonaRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private MonaRegressionTest() {
    }

    /** Runs Mona's data, timing, constellation, boundary, and snapshot cases. */
    public static void main(String[] args) throws IOException {
        testIdentityStatsA4AndCsvData();
        testNormalChargedAndPlungeMetadata();
        testNormalChainSnapshotRestore();
        testPressSkillSnapshotParticlesAndRecast();
        testPressSkillSnapshotRestore();
        testBubbleOmenTriggerAndForcedPop();
        testBurstConstellationsAndSnapshotRestore();
        testC2ProbabilityCooldownAndSnapshot();
        testBoundaryAndAbnormalCases();
        System.out.println("MonaRegressionTest passed");
    }

    private static void testIdentityStatsA4AndCsvData()
            throws IOException {
        Mona mona = new Mona(null, null, 0);
        assertEquals(CharacterId.MONA, mona.getCharacterId(),
                "Mona typed identity");
        assertEquals(CharacterId.MONA, CharacterId.fromName("Mona"),
                "Mona display identity");
        assertEquals(CharacterId.MONA, CharacterId.fromNumericId(30),
                "Mona numeric identity");
        assertEquals(Element.HYDRO, mona.getElement(), "Mona element");
        assertClose(10409.0,
                mona.getBaseStats().get(StatType.BASE_HP),
                "Mona base HP");
        assertClose(287.0,
                mona.getBaseStats().get(StatType.BASE_ATK),
                "Mona base ATK");
        assertClose(653.0,
                mona.getBaseStats().get(StatType.BASE_DEF),
                "Mona base DEF");
        assertClose(1.32,
                mona.getBaseStats().get(StatType.ENERGY_RECHARGE),
                "Mona ascension Energy Recharge");
        assertClose(0.264,
                mona.getEffectiveStats(0.0).get(
                        StatType.HYDRO_DMG_BONUS),
                "Mona A4 converts effective Energy Recharge");
        mona.addBuff(new SimpleBuff(
                "Mona ER probe", 10.0, 0.0,
                stats -> stats.add(StatType.ENERGY_RECHARGE, 0.50)));
        assertClose(0.364,
                mona.getEffectiveStats(0.0).get(
                        StatType.HYDRO_DMG_BONUS),
                "Mona A4 updates dynamically");
        assertClose(12.0, mona.getSkillCD(), "Mona Skill cooldown");
        assertClose(15.0, mona.getBurstCD(), "Mona Burst cooldown");
        assertClose(60.0, mona.getEnergyCost(), "Mona Energy cost");
        assertCsvShape(
                Path.of("config/characters/Mona/Mona_Status.csv"), 10);
        assertCsvShape(
                Path.of("config/characters/Mona/Mona_Multipliers.csv"), 17);
    }

    private static void testNormalChargedAndPlungeMetadata() {
        Mona mona = new Mona(null, null, 0);
        CombatSimulator simulator = simulatorWith(mona);
        List<ActionRecord> normals = captureNamedActions(
                simulator, "Ripple of Fate N");
        int[] hitmarks = { 11, 14, 25, 27 };
        int[] durations = { 18, 23, 39, 67 };
        double[] multipliers = {
                0.6392, 0.6120, 0.7616, 0.95472
        };
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = normals.get(step);
            assertClose(castTime + hitmarks[step] * FRAME,
                    record.time, "Mona N" + (step + 1) + " hitmark");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Mona N" + (step + 1) + " duration");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Mona N" + (step + 1) + " multiplier");
            assertEquals(ICDType.Standard, record.action.getICDType(),
                    "Mona Normal standard ICD");
            assertClose(1.0, record.action.getGaugeUnits(),
                    "Mona Normal 1U Hydro");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(4).action.getName().endsWith("N1"),
                "Mona Normal chain wraps after N4");

        Mona charged = new Mona(null, null, 0);
        CombatSimulator chargedSim = simulatorWith(charged);
        List<ActionRecord> chargedHits = captureNamedActions(
                chargedSim, "Ripple of Fate Charged Attack");
        perform(chargedSim, CharacterActionKey.CHARGE);
        assertClose(66.0 * FRAME, chargedHits.get(0).time,
                "Mona Charged hitmark");
        assertClose(113.0 * FRAME, chargedSim.getCurrentTime(),
                "Mona Charged duration");
        assertClose(2.54524,
                chargedHits.get(0).action.getDamagePercent(),
                "Mona Charged multiplier");
        assertEquals(ICDType.None,
                chargedHits.get(0).action.getICDType(),
                "Mona Charged isolated no-ICD policy");

        Mona plunging = new Mona(null, null, 0);
        CombatSimulator plungeSim = simulatorWith(plunging);
        List<ActionRecord> plunges = captureNamedActions(
                plungeSim, "Ripple of Fate High Plunge");
        perform(plungeSim, CharacterActionKey.PLUNGE);
        assertClose(0.0, plunges.get(0).time,
                "Mona catalyst Plunge resolves at action start");
        assertClose(75.0 * FRAME, plungeSim.getCurrentTime(),
                "Mona catalyst Plunge duration adaptation");
        assertClose(2.6076,
                plunges.get(0).action.getDamagePercent(),
                "Mona high Plunge multiplier");
    }

    private static void testNormalChainSnapshotRestore() {
        Mona mona = new Mona(null, null, 0);
        CombatSimulator simulator = simulatorWith(mona);
        List<ActionRecord> normals = captureNamedActions(
                simulator, "Ripple of Fate N");
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(2).action.getName().endsWith("N3"),
                "Mona chain reaches N3 before restore");
        simulator.restoreSnapshot(snapshot);
        normals.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(0).action.getName().endsWith("N3"),
                "Mona snapshot restores Normal progression");
        perform(simulator, CharacterActionKey.SKILL);
        normals.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(0).action.getName().endsWith("N1"),
                "Mona non-Normal action resets the chain");
    }

    private static void testPressSkillSnapshotParticlesAndRecast() {
        Mona mona = new Mona(null, null, 0);
        mona.addBuff(new SimpleBuff(
                "Mona cast ATK", 0.25, 0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(mona);
        List<ActionRecord> skillHits = captureNamedActions(
                simulator, "Mirror Reflection of Doom");
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 329.0 * FRAME + 100.0 * FRAME);
        assertEquals(5, skillHits.size(),
                "Mona Press Skill has four ticks and one explosion");
        int[] frames = { 86, 145, 204, 263, 329 };
        for (int index = 0; index < frames.length; index++) {
            ActionRecord record = skillHits.get(index);
            assertClose(frames[index] * FRAME, record.time,
                    "Mona Skill event timing " + index);
            assertClose(1.0,
                    record.action.getStatSnapshot().get(
                            StatType.ATK_PERCENT),
                    "Mona Skill retains cast snapshot");
            assertEquals(index == 4 ? ICDType.None : ICDType.Standard,
                    record.action.getICDType(),
                    "Mona Skill ICD split " + index);
        }
        assertClose(0.544,
                skillHits.get(0).action.getDamagePercent(),
                "Mona level-9 Skill DoT multiplier");
        assertClose(2.2576,
                skillHits.get(4).action.getDamagePercent(),
                "Mona level-9 Skill explosion multiplier");
        assertEquals(1, particles.size(),
                "Mona Skill emits one expected particle packet");
        assertClose(10.0 / 3.0, particles.get(0).count,
                "Mona expected Skill particle count");
        assertClose((329.0 + 100.0) * FRAME,
                particles.get(0).time,
                "Mona particle travel delay");
        assertClose(12.0 + 24.0 * FRAME,
                mona.getSkillCooldownEndTime(),
                "Mona Skill cooldown starts at frame 24");

        Mona c5 = new Mona(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Hits = captureNamedActions(
                c5Sim, "Mirror Reflection of Doom");
        perform(c5Sim, CharacterActionKey.SKILL);
        advanceTo(c5Sim, 329.0 * FRAME);
        assertClose(0.640, c5Hits.get(0).action.getDamagePercent(),
                "Mona C5 Skill DoT talent level");
        assertClose(2.656, c5Hits.get(4).action.getDamagePercent(),
                "Mona C5 Skill explosion talent level");

        Mona recast = new Mona(null, null, 0);
        CombatSimulator recastSim = simulatorWith(recast);
        List<ActionRecord> recastHits = captureNamedActions(
                recastSim, "Mirror Reflection of Doom");
        perform(recastSim, CharacterActionKey.SKILL);
        recast.resetSkillCooldown(recastSim.getCurrentTime());
        perform(recastSim, CharacterActionKey.SKILL);
        advanceTo(recastSim, 2.0 + 329.0 * FRAME);
        assertEquals(5, recastHits.size(),
                "Mona recast cancels the superseded Phantom stream");
    }

    private static void testPressSkillSnapshotRestore() {
        Mona mona = new Mona(null, null, 0);
        CombatSimulator simulator = simulatorWith(mona);
        List<ActionRecord> hits = captureNamedActions(
                simulator, "Mirror Reflection of Doom");
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot beforeFirstTick = simulator.saveSnapshot();
        advanceTo(simulator, (329.0 + 100.0) * FRAME + EPSILON);
        assertEquals(5, hits.size(), "Mona initial Skill path hit count");
        assertEquals(1, particles.size(),
                "Mona initial Skill path particle count");

        simulator.restoreSnapshot(beforeFirstTick);
        simulator.restoreSnapshot(beforeFirstTick);
        hits.clear();
        particles.clear();
        advanceTo(simulator, (329.0 + 100.0) * FRAME + EPSILON);
        assertEquals(5, hits.size(),
                "Mona repeated pre-tick restore does not duplicate hits");
        assertEquals(1, particles.size(),
                "Mona repeated pre-tick restore does not duplicate particles");

        Mona mid = new Mona(null, null, 0);
        CombatSimulator midSim = simulatorWith(mid);
        List<ActionRecord> midHits = captureNamedActions(
                midSim, "Mirror Reflection of Doom");
        perform(midSim, CharacterActionKey.SKILL);
        advanceTo(midSim, 150.0 * FRAME);
        SimulatorSnapshot midSnapshot = midSim.saveSnapshot();
        midHits.clear();
        advanceTo(midSim, 330.0 * FRAME);
        int expectedRemaining = midHits.size();
        midSim.restoreSnapshot(midSnapshot);
        midHits.clear();
        advanceTo(midSim, 330.0 * FRAME);
        assertEquals(expectedRemaining, midHits.size(),
                "Mona mid-stream restore resumes remaining Skill events");
    }

    private static void testBubbleOmenTriggerAndForcedPop() {
        Mona baseline = new Mona(null, null, 0);
        CombatSimulator baselineSim = simulatorWith(baseline);
        List<ActionRecord> baselineNormals = captureNamedActions(
                baselineSim, "Ripple of Fate N1");
        perform(baselineSim, CharacterActionKey.NORMAL);
        double baselineDamage = baselineNormals.get(0).damage;

        Mona mona = new Mona(null, null, 0);
        CombatSimulator simulator = simulatorWith(mona);
        List<ActionRecord> normals = captureNamedActions(
                simulator, "Ripple of Fate N1");
        List<ActionRecord> bursts = captureNamedActions(
                simulator, "Stellaris Phantasm");
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(mona.isBubbleActive(simulator.getCurrentTime()),
                "Mona Bubble is active after frame-107 application");
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(0).damage > baselineDamage,
                "Bubble-triggering hit receives Omen bonus");
        assertTrue(!mona.isBubbleActive(simulator.getCurrentTime()),
                "First positive direct hit pops Bubble");
        assertTrue(mona.isOmenAmplified(simulator.getCurrentTime()),
                "Omen remains after Bubble pop");
        assertEquals(2, bursts.size(),
                "Mona Burst records application and explosion");
        assertClose(107.0 * FRAME, bursts.get(0).time,
                "Mona Bubble application timing");
        assertClose(normals.get(0).time + FRAME, bursts.get(1).time,
                "Mona Bubble explosion occurs one frame after trigger");
        assertClose(7.5208,
                bursts.get(1).action.getDamagePercent(),
                "Mona level-9 Bubble multiplier");
        assertClose(2.0, bursts.get(1).action.getGaugeUnits(),
                "Mona Bubble explosion applies 2U Hydro");

        Mona forced = new Mona(null, null, 0);
        CombatSimulator forcedSim = simulatorWith(forced);
        List<ActionRecord> forcedBursts = captureNamedActions(
                forcedSim, "Stellaris Phantasm");
        perform(forcedSim, CharacterActionKey.BURST);
        double forcedPopTime = (107.0 + 480.0) * FRAME;
        advanceTo(forcedSim, forcedPopTime);
        assertTrue(!forced.isBubbleActive(forcedSim.getCurrentTime()),
                "Mona Bubble force-pops at eight seconds");
        advanceTo(forcedSim, forcedPopTime + FRAME);
        assertEquals(2, forcedBursts.size(),
                "Mona forced Bubble pop deals one explosion");
        assertTrue(forced.isOmenAmplified(
                forcedPopTime + 5.0 - EPSILON),
                "Mona Omen active before five-second expiry");
        assertTrue(!forced.isOmenAmplified(forcedPopTime + 5.0),
                "Mona Omen uses half-open expiry");
    }

    private static void testBurstConstellationsAndSnapshotRestore() {
        Mona c3 = new Mona(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Bursts = captureNamedActions(
                c3Sim, "Stellaris Phantasm Illusory Bubble Explosion");
        perform(c3Sim, CharacterActionKey.BURST);
        perform(c3Sim, CharacterActionKey.NORMAL);
        assertClose(8.848,
                c3Bursts.get(0).action.getDamagePercent(),
                "Mona C3 Bubble talent level");
        assertClose(0.60,
                effectiveStatsWithTeam(c3Sim, c3).get(
                        StatType.DMG_BONUS_ALL),
                "Mona C3 Omen bonus");

        Mona c4 = new Mona(null, null, 4);
        CombatSimulator c4Sim = simulatorWith(c4);
        perform(c4Sim, CharacterActionKey.BURST);
        StatsContainer bubbleStats = effectiveStatsWithTeam(c4Sim, c4);
        assertClose(0.60,
                bubbleStats.get(StatType.DMG_BONUS_ALL),
                "Mona Bubble projects C3 Omen value");
        assertClose(0.20, bubbleStats.get(StatType.CRIT_RATE),
                "Mona C4 adds 15 percent target-bound CRIT Rate");

        Mona pending = new Mona(null, null, 0);
        CombatSimulator pendingSim = simulatorWith(pending);
        SimulatorSnapshot[] beforeApplication = captureSnapshotAt(
                pendingSim, 100.0 * FRAME);
        perform(pendingSim, CharacterActionKey.BURST);
        assertTrue(beforeApplication[0] != null,
                "Mona captures Burst before Bubble application");
        pendingSim.restoreSnapshot(beforeApplication[0]);
        pendingSim.restoreSnapshot(beforeApplication[0]);
        advanceTo(pendingSim, 107.0 * FRAME);
        assertTrue(pending.isBubbleActive(pendingSim.getCurrentTime()),
                "Mona repeated pre-application restore applies one Bubble");

        SimulatorSnapshot bubbleSnapshot = pendingSim.saveSnapshot();
        pendingSim.notifyDamage(pending, directNormalProbe(), 100.0);
        SimulatorSnapshot pendingExplosion = pendingSim.saveSnapshot();
        advanceTo(pendingSim, pendingSim.getCurrentTime() + FRAME);
        double onceDamage = pendingSim.getTotalDamage();
        pendingSim.restoreSnapshot(pendingExplosion);
        pendingSim.restoreSnapshot(pendingExplosion);
        advanceTo(pendingSim, pendingSim.getCurrentTime() + FRAME);
        assertClose(onceDamage, pendingSim.getTotalDamage(),
                "Mona repeated pending-explosion restore is idempotent");
        pendingSim.restoreSnapshot(bubbleSnapshot);
        assertTrue(pending.isBubbleActive(pendingSim.getCurrentTime()),
                "Mona restores Bubble before its trigger");
    }

    private static void testC2ProbabilityCooldownAndSnapshot() {
        AtomicInteger draws = new AtomicInteger();
        Mona c2 = new Mona(null, null, 2, () -> {
            draws.incrementAndGet();
            return 0.0;
        });
        CombatSimulator simulator = simulatorWith(c2);
        List<ActionRecord> followUps = captureNamedActions(
                simulator, "Come 'n' Get Me, Hag!");
        AttackAction normal = directNormalProbe();
        simulator.notifyDamage(c2, normal, 100.0);
        SimulatorSnapshot pendingSnapshot = simulator.saveSnapshot();
        advanceTo(simulator, 53.0 * FRAME);
        assertEquals(1, followUps.size(),
                "Mona C2 success schedules one Charged Attack");
        assertEquals(1, draws.get(), "Mona C2 draws on eligible hit");

        simulator.notifyDamage(c2, normal, 100.0);
        assertEquals(1, draws.get(),
                "Mona C2 does not draw inside cooldown");
        advanceTo(simulator, 5.0);
        simulator.notifyDamage(c2, normal, 100.0);
        assertEquals(2, draws.get(),
                "Mona C2 accepts the exact five-second boundary");

        simulator.restoreSnapshot(pendingSnapshot);
        simulator.restoreSnapshot(pendingSnapshot);
        followUps.clear();
        advanceTo(simulator, 53.0 * FRAME);
        assertEquals(1, followUps.size(),
                "Mona repeated pending-C2 restore does not duplicate impact");

        Mona failed = new Mona(null, null, 2, () -> 0.75);
        CombatSimulator failedSim = simulatorWith(failed);
        List<ActionRecord> failedHits = captureNamedActions(
                failedSim, "Come 'n' Get Me, Hag!");
        failedSim.notifyDamage(failed, directNormalProbe(), 100.0);
        failedSim.advanceTime(1.0);
        assertEquals(0, failedHits.size(),
                "Mona C2 failed draw schedules no Charged Attack");

        Mona c1 = new Mona(null, null, 1, () -> 0.0);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Hits = captureNamedActions(
                c1Sim, "Come 'n' Get Me, Hag!");
        c1Sim.notifyDamage(c1, directNormalProbe(), 100.0);
        c1Sim.advanceTime(1.0);
        assertEquals(0, c1Hits.size(),
                "Mona C2 does not leak into C1");
    }

    private static void testBoundaryAndAbnormalCases() {
        Mona insufficient = new Mona(null, null, 0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertClose(0.0, insufficientSim.getCurrentTime(),
                "Mona insufficient Energy skips Burst");
        assertClose(60.0, insufficient.getMissedBurstCost(),
                "Mona skipped Burst records required Energy");

        Mona rejected = new Mona(null, null, 2, () -> Double.NaN);
        CombatSimulator rejectedSim = simulatorWith(rejected);
        assertThrows(IllegalStateException.class,
                () -> rejectedSim.notifyDamage(
                        rejected, directNormalProbe(), 100.0),
                "Mona rejects non-finite C2 draw");
        assertThrows(IllegalArgumentException.class,
                () -> new Mona(null, null, -1),
                "Mona rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Mona(null, null, 7),
                "Mona rejects constellation above six");
        assertThrows(IllegalArgumentException.class,
                () -> new Mona(null, null, 2, null),
                "Mona rejects null C2 draw");
        assertThrows(IllegalArgumentException.class,
                () -> rejected.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, rejectedSim),
                "Mona rejects foreign snapshot state");
        assertThrows(IllegalStateException.class,
                () -> rejected.initializeForSimulator(
                        new CombatSimulator()),
                "Mona rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> rejected.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH),
                        rejectedSim),
                "Mona rejects unsupported alternate sprint");

        Mona bubble = new Mona(null, null, 0);
        CombatSimulator bubbleSim = simulatorWith(bubble);
        perform(bubbleSim, CharacterActionKey.BURST);
        bubbleSim.notifyDamage(bubble, directNormalProbe(), 0.0);
        assertTrue(bubble.isBubbleActive(bubbleSim.getCurrentTime()),
                "Mona Bubble ignores zero direct damage");
        AttackAction indirect = new AttackAction(
                "Indirect probe", 1.0, Element.PHYSICAL,
                StatType.BASE_ATK, null, 0.0, ActionType.OTHER);
        bubbleSim.notifyDamage(bubble, indirect, 100.0);
        assertTrue(bubble.isBubbleActive(bubbleSim.getCurrentTime()),
                "Mona Bubble ignores indirect OTHER damage");
    }

    private static StatsContainer effectiveStatsWithTeam(
            CombatSimulator simulator,
            Mona mona) {
        double time = simulator.getCurrentTime();
        StatsContainer stats = mona.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(mona)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats;
    }

    private static AttackAction directNormalProbe() {
        AttackAction action = new AttackAction(
                "Mona direct Normal probe",
                1.0,
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        return action;
    }

    private static CombatSimulator simulatorWith(Mona mona) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        simulator.addCharacter(mona);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.MONA,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator simulator,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.MONA
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
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

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " column count line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Mona,"),
                    path + " character identity line " + (index + 1));
        }
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
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
}
