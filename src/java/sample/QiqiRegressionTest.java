package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import model.character.Qiqi;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
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

/** Focused regression checks for Qiqi's classic offensive vertical slice. */
public final class QiqiRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private QiqiRegressionTest() {
    }

    /** Runs Qiqi's data, timing, constellation, and snapshot cases. */
    public static void main(String[] args) throws IOException {
        testIdentityStatsAndCsvData();
        testNormalChargedAndPlungeTimings();
        testNormalChainSnapshotRestore();
        testClassicSkillSnapshotAndTalentScaling();
        testSkillSnapshotRestoreAndRecast();
        testBurstTalismanEnergyAndTalentScaling();
        testC1AndC2();
        testBoundaryAndAbnormalCases();
        System.out.println("QiqiRegressionTest passed");
    }

    private static void testIdentityStatsAndCsvData() throws IOException {
        Qiqi qiqi = new Qiqi(null, null);
        assertEquals(CharacterId.QIQI, qiqi.getCharacterId(),
                "Qiqi typed identity");
        assertEquals(CharacterId.QIQI, CharacterId.fromName("Qiqi"),
                "Qiqi display identity");
        assertEquals(CharacterId.QIQI, CharacterId.fromNumericId(29),
                "Qiqi numeric identity");
        assertEquals(Element.CRYO, qiqi.getElement(), "Qiqi element");
        assertClose(12368.0,
                qiqi.getBaseStats().get(StatType.BASE_HP),
                "Qiqi base HP");
        assertClose(287.0,
                qiqi.getBaseStats().get(StatType.BASE_ATK),
                "Qiqi base ATK");
        assertClose(922.0,
                qiqi.getBaseStats().get(StatType.BASE_DEF),
                "Qiqi base DEF");
        assertClose(0.2215,
                qiqi.getBaseStats().get(StatType.HEALING_BONUS),
                "Qiqi ascension Healing Bonus");
        assertClose(30.0, qiqi.getSkillCD(),
                "Qiqi classic Skill cooldown");
        assertClose(20.0, qiqi.getBurstCD(), "Qiqi Burst cooldown");
        assertClose(80.0, qiqi.getEnergyCost(), "Qiqi Energy cost");
        assertCsvShape(
                Path.of("config/characters/Qiqi/Qiqi_Status.csv"), 10);
        assertCsvShape(
                Path.of("config/characters/Qiqi/Qiqi_Multipliers.csv"), 13);
    }

    private static void testNormalChargedAndPlungeTimings() {
        Qiqi qiqi = new Qiqi(null, null, 0);
        CombatSimulator simulator = simulatorWith(qiqi);
        List<ActionRecord> normals = captureNamedActions(
                simulator, "Ancient Sword Art N");
        int[][] hitmarks = {
                { 11 }, { 10 }, { 9, 20 }, { 8, 18 }, { 16 }
        };
        int[] durations = { 21, 22, 33, 28, 53 };
        double[] multipliers = {
                0.69362, 0.71416, 0.44398, 0.45346, 1.15814
        };
        int recordIndex = 0;
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < hitmarks[step].length; hit++) {
                ActionRecord record = normals.get(recordIndex++);
                assertClose(castTime + hitmarks[step][hit] * FRAME,
                        record.time,
                        "Qiqi N" + (step + 1) + " hitmark");
                assertClose(multipliers[step],
                        record.action.getDamagePercent(),
                        "Qiqi N" + (step + 1) + " multiplier");
            }
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Qiqi N" + (step + 1) + " duration");
        }
        assertEquals(7, normals.size(),
                "Qiqi N3 and N4 are distinct double hits");
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(normals.get(7).action.getName().contains("N1"),
                "Qiqi Normal chain wraps after N5");

        Qiqi charged = new Qiqi(null, null, 0);
        CombatSimulator chargedSim = simulatorWith(charged);
        List<ActionRecord> chargedHits = captureNamedActions(
                chargedSim, "Ancient Sword Art Charged");
        perform(chargedSim, CharacterActionKey.CHARGE);
        assertEquals(2, chargedHits.size(), "Qiqi Charged hit count");
        assertClose(15.0 * FRAME, chargedHits.get(0).time,
                "Qiqi Charged first hitmark");
        assertClose(29.0 * FRAME, chargedHits.get(1).time,
                "Qiqi Charged second hitmark");
        assertClose(76.0 * FRAME, chargedSim.getCurrentTime(),
                "Qiqi Charged duration");
        assertClose(1.18184,
                chargedHits.get(0).action.getDamagePercent(),
                "Qiqi Charged multiplier");

        Qiqi plunging = new Qiqi(null, null, 0);
        CombatSimulator plungeSim = simulatorWith(plunging);
        List<ActionRecord> plunges = captureNamedActions(
                plungeSim, "Ancient Sword Art High Plunge");
        perform(plungeSim, CharacterActionKey.PLUNGE);
        assertClose(46.0 * FRAME, plunges.get(0).time,
                "Qiqi high Plunge hitmark");
        assertClose(77.0 * FRAME, plungeSim.getCurrentTime(),
                "Qiqi high Plunge duration");
        assertClose(2.933586,
                plunges.get(0).action.getDamagePercent(),
                "Qiqi high Plunge multiplier");
    }

    private static void testClassicSkillSnapshotAndTalentScaling() {
        Qiqi qiqi = new Qiqi(null, null, 0);
        qiqi.addBuff(new SimpleBuff(
                "Qiqi pre-hit Skill ATK",
                0.25,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(qiqi);
        List<ActionRecord> initial = captureNamedActions(
                simulator, "Adeptus Art: Herald of Frost Initial");
        List<ActionRecord> swipes = captureNamedActions(
                simulator, "Herald of Frost Swipe");
        List<Double> particles = new ArrayList<>();
        simulator.addParticleListener((element, count, time) ->
                particles.add(count));
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, initial.size(), "Qiqi Skill initial hit count");
        assertClose(32.0 * FRAME, initial.get(0).time,
                "Qiqi Skill initial hitmark");
        assertClose(1.632, initial.get(0).action.getDamagePercent(),
                "Qiqi level-9 Skill initial multiplier");
        assertClose(0.0,
                initial.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Qiqi Skill initial snapshots frame-32 stats");
        assertClose(57.0 * FRAME, simulator.getCurrentTime(),
                "Qiqi Skill duration");
        assertClose(30.0 + 3.0 * FRAME,
                qiqi.getSkillCooldownEndTime(),
                "Qiqi classic 30-second Skill cooldown");
        assertTrue(qiqi.isHeraldActive(15.0 - EPSILON),
                "Qiqi classic Herald lasts 15 seconds");
        assertTrue(!qiqi.isHeraldActive(15.0),
                "Qiqi Herald uses half-open expiry");
        simulator.advanceTime(15.0 - simulator.getCurrentTime());
        assertEquals(9, swipes.size(), "Qiqi maintained swipe count");
        double[] offsets = {
                1.5, 3.75, 4.75, 7.0, 8.0,
                10.25, 11.25, 13.5, 14.5
        };
        for (int index = 0; index < swipes.size(); index++) {
            assertClose(offsets[index], swipes.get(index).time,
                    "Qiqi Herald swipe timing " + (index + 1));
            assertClose(0.612,
                    swipes.get(index).action.getDamagePercent(),
                    "Qiqi level-9 Herald swipe multiplier");
            assertClose(0.0,
                    swipes.get(index).action.getStatSnapshot()
                            .get(StatType.ATK_PERCENT),
                    "Qiqi Herald swipe retains frame-32 snapshot");
        }
        assertEquals(0, particles.size(),
                "Qiqi classic Skill generates no particles");

        Qiqi c5 = new Qiqi(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Initial = captureNamedActions(
                c5Sim, "Adeptus Art: Herald of Frost Initial");
        List<ActionRecord> c5Swipes = captureNamedActions(
                c5Sim, "Herald of Frost Swipe");
        perform(c5Sim, CharacterActionKey.SKILL);
        advanceTo(c5Sim, 1.5);
        assertClose(1.920, c5Initial.get(0).action.getDamagePercent(),
                "Qiqi C5 Skill initial level-12 multiplier");
        assertClose(0.720, c5Swipes.get(0).action.getDamagePercent(),
                "Qiqi C5 swipe level-12 multiplier");
    }

    private static void testNormalChainSnapshotRestore() {
        Qiqi qiqi = new Qiqi(null, null, 0);
        CombatSimulator simulator = simulatorWith(qiqi);
        List<ActionRecord> normals = captureNamedActions(
                simulator, "Ancient Sword Art N");
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> expectedN3 = new ArrayList<>(
                normals.subList(2, 4));

        simulator.restoreSnapshot(snapshot);
        normals.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, normals.size(),
                "Qiqi restored Normal chain resumes with double-hit N3");
        for (int hit = 0; hit < 2; hit++) {
            assertEquals(expectedN3.get(hit).action.getName(),
                    normals.get(hit).action.getName(),
                    "Qiqi restored N3 hit identity");
            assertClose(expectedN3.get(hit).time, normals.get(hit).time,
                    "Qiqi restored N3 hit timing");
            assertClose(expectedN3.get(hit).damage, normals.get(hit).damage,
                    "Qiqi restored N3 hit damage");
        }
    }

    private static void testSkillSnapshotRestoreAndRecast() {
        Qiqi qiqi = new Qiqi(null, null, 0);
        CombatSimulator simulator = simulatorWith(qiqi);
        List<ActionRecord> swipes = captureNamedActions(
                simulator, "Herald of Frost Swipe");
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(15.0);
        List<ActionRecord> expected = new ArrayList<>(swipes);
        assertEquals(9, expected.size(),
                "Qiqi original branch resolves nine swipes");

        simulator.restoreSnapshot(snapshot);
        swipes.clear();
        simulator.advanceTime(15.0);
        assertSwipeReplay(expected, swipes,
                "Qiqi restored Herald sequence");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        swipes.clear();
        simulator.advanceTime(15.0);
        assertSwipeReplay(expected, swipes,
                "Qiqi repeated restore Herald sequence");

        Qiqi recast = new Qiqi(null, null, 0);
        CombatSimulator recastSim = simulatorWith(recast);
        List<ActionRecord> recastSwipes = captureNamedActions(
                recastSim, "Herald of Frost Swipe");
        perform(recastSim, CharacterActionKey.SKILL);
        recast.resetSkillCooldown(recastSim.getCurrentTime());
        double replacementCast = recastSim.getCurrentTime();
        perform(recastSim, CharacterActionKey.SKILL);
        recastSim.advanceTime(15.0);
        assertEquals(9, recastSwipes.size(),
                "Qiqi recast cancels the old swipe stream");
        assertClose(replacementCast + 1.5, recastSwipes.get(0).time,
                "Qiqi replacement stream owns first swipe");
    }

    private static void testBurstTalismanEnergyAndTalentScaling() {
        Qiqi c0 = new Qiqi(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> bursts = captureNamedActions(
                simulator, "Adeptus Art: Preserver of Fortune");
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, bursts.size(), "Qiqi Burst hit count");
        assertClose(82.0 * FRAME, bursts.get(0).time,
                "Qiqi Burst hitmark");
        assertClose(4.8416, bursts.get(0).action.getDamagePercent(),
                "Qiqi level-9 Burst multiplier");
        assertEquals(ICDType.None, bursts.get(0).action.getICDType(),
                "Qiqi Burst uses KQM baseline no ICD");
        assertEquals(ICDTag.ElementalBurst,
                bursts.get(0).action.getICDTag(),
                "Qiqi Burst typed ICD tag");
        assertClose(2.0, bursts.get(0).action.getGaugeUnits(),
                "Qiqi Burst applies 2U Cryo");
        assertClose(115.0 * FRAME, simulator.getCurrentTime(),
                "Qiqi Burst duration");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Qiqi Burst spends 80 Energy");
        assertClose(20.0, c0.getBurstCooldownEndTime(),
                "Qiqi Burst starts 20-second cooldown at cast");
        double talismanExpiry = 40.0 * FRAME + 15.0;
        assertTrue(c0.isTalismanActive(talismanExpiry - EPSILON),
                "Qiqi talisman lasts 15 seconds from application");
        assertTrue(!c0.isTalismanActive(talismanExpiry),
                "Qiqi talisman uses half-open expiry");
        SimulatorSnapshot talismanSnapshot = simulator.saveSnapshot();
        advanceTo(simulator, talismanExpiry);
        assertTrue(!c0.isTalismanActive(simulator.getCurrentTime()),
                "Qiqi talisman expires on the original branch");
        simulator.restoreSnapshot(talismanSnapshot);
        assertTrue(c0.isTalismanActive(simulator.getCurrentTime()),
                "Qiqi snapshot restores talisman activity");
        advanceTo(simulator, talismanExpiry);
        assertTrue(!c0.isTalismanActive(simulator.getCurrentTime()),
                "Qiqi restored talisman keeps its exact expiry");

        Qiqi c3 = new Qiqi(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Bursts = captureNamedActions(
                c3Sim, "Adeptus Art: Preserver of Fortune");
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(5.696, c3Bursts.get(0).action.getDamagePercent(),
                "Qiqi C3 Burst level-12 multiplier");
    }

    private static void testC1AndC2() {
        Qiqi c1 = new Qiqi(null, null, 1);
        CombatSimulator c1Sim = simulatorWith(c1);
        perform(c1Sim, CharacterActionKey.SKILL);
        perform(c1Sim, CharacterActionKey.BURST);
        advanceTo(c1Sim, 15.0);
        assertClose(16.0, c1.getCurrentEnergy(),
                "Qiqi C1 gives two Energy for eight talisman swipes");
        assertClose(16.0, c1.getTotalFlatEnergy(),
                "Qiqi C1 Energy is flat");

        Qiqi c2 = new Qiqi(null, null, 2);
        CombatSimulator cryoSim = simulatorWith(c2);
        cryoSim.getEnemy().setAura(Element.CRYO, 1.0);
        List<ActionRecord> cryoNormals = captureNamedActions(
                cryoSim, "Ancient Sword Art N1");
        perform(cryoSim, CharacterActionKey.NORMAL);
        assertClose(0.15,
                cryoNormals.get(0).action.getExtraBonuses()
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Qiqi C2 activates against Cryo at impact");

        Qiqi frozenC2 = new Qiqi(null, null, 2);
        CombatSimulator frozenSim = simulatorWith(frozenC2);
        frozenSim.getEnemy().applyFreezeAura(2.0, 0.0);
        List<ActionRecord> frozenCharges = captureNamedActions(
                frozenSim, "Ancient Sword Art Charged");
        perform(frozenSim, CharacterActionKey.CHARGE);
        for (ActionRecord hit : frozenCharges) {
            assertClose(0.15,
                    hit.action.getExtraBonuses()
                            .get(StatType.CHARGED_ATTACK_DMG_BONUS),
                    "Qiqi C2 activates against Frozen at impact");
        }

        Qiqi noAuraC2 = new Qiqi(null, null, 2);
        CombatSimulator noAuraSim = simulatorWith(noAuraC2);
        List<ActionRecord> noAuraNormals = captureNamedActions(
                noAuraSim, "Ancient Sword Art N1");
        perform(noAuraSim, CharacterActionKey.NORMAL);
        assertTrue(noAuraNormals.get(0).action.getExtraBonuses().isEmpty(),
                "Qiqi C2 does not activate without Cryo or Frozen");
    }

    private static void testBoundaryAndAbnormalCases() {
        Qiqi exactInitial = new Qiqi(null, null, 0);
        CombatSimulator exactInitialSim = simulatorWith(exactInitial);
        List<ActionRecord> exactInitialHits = captureNamedActions(
                exactInitialSim, "Adeptus Art: Herald of Frost Initial");
        SimulatorSnapshot[] exactInitialSnapshot = captureSnapshotAt(
                exactInitialSim, 31.0 * FRAME);
        perform(exactInitialSim, CharacterActionKey.SKILL);
        assertEquals(1, exactInitialHits.size(),
                "Qiqi original pending frame-32 initial resolves");
        exactInitialSim.restoreSnapshot(exactInitialSnapshot[0]);
        exactInitialHits.clear();
        exactInitialSim.advanceTime(FRAME + EPSILON);
        assertEquals(1, exactInitialHits.size(),
                "Qiqi restored pending frame-32 initial resolves once");
        exactInitialSim.restoreSnapshot(exactInitialSnapshot[0]);
        exactInitialSim.restoreSnapshot(exactInitialSnapshot[0]);
        exactInitialHits.clear();
        exactInitialSim.advanceTime(FRAME + EPSILON);
        assertEquals(1, exactInitialHits.size(),
                "Qiqi repeated pending-initial restore keeps one hit");

        Qiqi exact = new Qiqi(null, null, 0);
        CombatSimulator exactSim = simulatorWith(exact);
        List<ActionRecord> exactSwipes = captureNamedActions(
                exactSim, "Herald of Frost Swipe");
        perform(exactSim, CharacterActionKey.SKILL);
        SnapshotAwareCharacterEffect.State pendingSwipe =
                exact.captureCharacterState();
        advanceTo(exactSim, 1.5);
        assertEquals(1, exactSwipes.size(),
                "Qiqi original exact-deadline swipe resolves");
        SimulatorSnapshot[] exactSnapshot = captureSnapshotAt(
                exactSim, exactSim.getCurrentTime());
        exact.restoreCharacterState(pendingSwipe, exactSim);
        exactSwipes.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactSwipes.size(),
                "Qiqi same-time setup resolves one swipe");
        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactSwipes.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactSwipes.size(),
                "Qiqi restored exact-deadline swipe resolves once");
        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactSwipes.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactSwipes.size(),
                "Qiqi repeated exact-deadline restore keeps one swipe");

        Qiqi insufficient = new Qiqi(null, null, 0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertClose(0.0, insufficientSim.getCurrentTime(),
                "Qiqi insufficient Energy skips Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Qiqi skipped Burst records required Energy");

        assertThrows(IllegalArgumentException.class,
                () -> new Qiqi(null, null, -1),
                "Qiqi rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Qiqi(null, null, 7),
                "Qiqi rejects constellation above six");
        assertThrows(IllegalArgumentException.class,
                () -> exact.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, exactSim),
                "Qiqi rejects foreign snapshot state");
        assertThrows(IllegalStateException.class,
                () -> exact.initializeForSimulator(new CombatSimulator()),
                "Qiqi rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> exact.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH),
                        exactSim),
                "Qiqi rejects unsupported Dash");
    }

    private static CombatSimulator simulatorWith(Qiqi qiqi) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        simulator.addCharacter(qiqi);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.QIQI,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator simulator,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.QIQI
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
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

    private static void assertSwipeReplay(
            List<ActionRecord> expected,
            List<ActionRecord> actual,
            String message) {
        assertEquals(expected.size(), actual.size(), message + " count");
        for (int index = 0; index < expected.size(); index++) {
            assertClose(expected.get(index).time, actual.get(index).time,
                    message + " timing");
            assertClose(expected.get(index).damage, actual.get(index).damage,
                    message + " damage");
        }
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " column count line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Qiqi,"),
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
}
