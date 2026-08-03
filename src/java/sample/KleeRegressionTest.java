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
import model.character.Klee;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Klee's classic stationary one-target slice. */
public final class KleeRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KleeRegressionTest() {
    }

    /** Runs data, timing, state, constellation, and abnormal-input checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstructors();
        testBasicAttackTimingSnapshotsAndA1();
        testSkillChargesSnapshotParticlesAndRestore();
        testC1PityAndTalentLevels();
        testBurstCadenceRandomSnapshotAndC6();
        testBurstMidHitRepeatedRestore();
        testBurstSwitchC4AndStaleSuppression();
        testC2LiveTargetDefenseReductionAndExpiry();
        testInvalidDrawsStateAndReuse();
        System.out.println("KleeRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstructors()
            throws IOException {
        Klee klee = new Klee(null, null);
        assertEquals(CharacterId.KLEE, klee.getCharacterId(),
                "Klee typed identity");
        assertEquals(Element.PYRO, klee.getElement(), "Klee element");
        assertClose(10287.0,
                klee.getBaseStats().get(StatType.BASE_HP), "Klee base HP");
        assertClose(311.0,
                klee.getBaseStats().get(StatType.BASE_ATK), "Klee base ATK");
        assertClose(615.0,
                klee.getBaseStats().get(StatType.BASE_DEF), "Klee base DEF");
        assertClose(0.288,
                klee.getBaseStats().get(StatType.PYRO_DMG_BONUS),
                "Klee ascension Pyro DMG");
        assertClose(60.0, klee.getEnergyCost(), "Klee Energy cost");
        assertClose(20.0, klee.getSkillCD(), "Klee Skill cooldown");
        assertClose(15.0, klee.getBurstCD(), "Klee Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.KLEE,
                    deterministicKlee(constellation).getCharacterId(),
                    "Klee constellation " + constellation);
        }
        assertCsvShape(Paths.get(
                "config/characters/Klee/Klee_Status.csv"), 10);
        assertCsvShape(Paths.get(
                "config/characters/Klee/Klee_Multipliers.csv"), 21);
    }

    private static void testBasicAttackTimingSnapshotsAndA1() {
        CountingSupplier a1Draws = new CountingSupplier(0.0, 1.0, 1.0);
        Klee klee = new Klee(
                null, null, 0, a1Draws, () -> 1.0, () -> 1.0);
        CombatSimulator simulator = simulatorWith(klee);
        List<ActionRecord> records = captureKleeActions(simulator);
        addBuffAt(simulator, klee, 20.0 * FRAME,
                new TestBuff(20.0, StatType.ATK_PERCENT, 1.0));
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(3, named(records, "Kaboom N").size(),
                "Klee three-hit Normal count");
        double[] expectedTimes = { 26.0, 67.0, 122.0 };
        double[] expectedMultipliers = { 1.22672, 1.0608, 1.52864 };
        for (int index = 0; index < 3; index++) {
            ActionRecord record = named(records, "Kaboom N").get(index);
            assertClose(expectedTimes[index] * FRAME, record.time,
                    "Klee N" + (index + 1) + " impact");
            assertClose(expectedMultipliers[index],
                    record.action.getDamagePercent(),
                    "Klee N" + (index + 1) + " multiplier");
            assertEquals(ActionType.NORMAL, record.action.getActionType(),
                    "Klee Normal category");
            assertTrue(!record.action.isShatterTrigger(),
                    "Klee bounded Normal excludes Shatter");
        }
        assertClose(0.0,
                records.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Klee N1 snapshots before impact-time buff");
        assertClose(1.0,
                records.get(1).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Klee N2 snapshots at release");
        assertEquals(1, a1Draws.getCount(),
                "Klee A1 four-second gate suppresses later Normal draws");

        records.clear();
        double chargeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records, "Kaboom Charged Attack").get(0);
        assertClose(chargeStart + 86.0 * FRAME, charged.time,
                "Klee Charged impact frame");
        assertClose(2.67512, charged.action.getDamagePercent(),
                "Klee Charged multiplier");
        assertTrue(!charged.action.isShatterTrigger(),
                "Klee bounded Charged Attack excludes Shatter");
        assertClose(0.50,
                charged.action.getStatSnapshot()
                        .get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Klee A1 Spark enters Charged snapshot");
        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(0.0,
                named(records, "Kaboom Charged Attack").get(0).action
                        .getStatSnapshot()
                        .get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Klee A1 Spark is consumed once");

        CountingSupplier mineA1Draws = new CountingSupplier(1.0, 0.0, 1.0);
        Klee mineA1 = new Klee(
                null, null, 0, mineA1Draws, () -> 1.0, () -> 1.0);
        CombatSimulator mineA1Sim = simulatorWith(mineA1);
        List<ActionRecord> mineA1Records = captureKleeActions(mineA1Sim);
        perform(mineA1Sim, CharacterActionKey.SKILL);
        advanceTo(mineA1Sim, 5.0);
        assertEquals(2, mineA1Draws.getCount(),
                "Klee mine can grant A1 Spark before the gate suppresses peers");
        perform(mineA1Sim, CharacterActionKey.CHARGE);
        assertClose(0.50,
                named(mineA1Records, "Kaboom Charged Attack").get(0).action
                        .getStatSnapshot()
                        .get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Klee consumes mine-generated A1 Spark");

        records.clear();
        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(0);
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Klee high Plunge category");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Klee high Plunge exact multiplier");
        assertClose(plungeStart + 1.0, simulator.getCurrentTime(),
                "Klee fixed one-second Plunge policy");
    }

    private static void testSkillChargesSnapshotParticlesAndRestore() {
        Klee klee = deterministicKlee(0);
        CombatSimulator simulator = simulatorWith(klee);
        List<ActionRecord> records = captureKleeActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        addBuffAt(simulator, klee, 50.0 * FRAME,
                new TestBuff(20.0, StatType.ATK_PERCENT, 1.0));
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(150.0 * FRAME, simulator.getCurrentTime(),
                "Klee spends two Skill charges without waiting");
        assertClose(20.0 - 117.0 * FRAME,
                klee.getSkillCDRemaining(simulator.getCurrentTime()),
                "Klee waits on the first queued charge after two casts");
        advanceTo(simulator, 6.0);
        List<ActionRecord> bounces = named(records, "Jumpy Dumpty (Bounce)");
        List<ActionRecord> mines = named(records, "Jumpy Dumpty (Mine)");
        assertEquals(2, bounces.size(), "Klee recast preserves both bounces");
        assertEquals(4, mines.size(), "Klee recast preserves all four mines");
        assertTrue(!bounces.get(0).action.isShatterTrigger(),
                "Klee bounded Skill excludes Shatter");
        assertEquals(2, particles.size(), "Klee recast particle count");
        assertClose(4.0, particles.get(0).count,
                "Klee Skill particles per cast");
        assertClose(171.0 * FRAME, particles.get(0).time,
                "Klee first particle arrival");
        assertClose(0.0,
                bounces.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Klee Skill snapshots at cast");

        Klee restoredKlee = deterministicKlee(0);
        CombatSimulator restoredSim = simulatorWith(restoredKlee);
        List<ActionRecord> restoredRecords = captureKleeActions(restoredSim);
        perform(restoredSim, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = restoredSim.saveSnapshot();
        restoredRecords.clear();
        restoredSim.restoreSnapshot(snapshot);
        restoredSim.restoreSnapshot(snapshot);
        advanceTo(restoredSim, 5.0);
        assertEquals(2,
                named(restoredRecords, "Jumpy Dumpty (Mine)").size(),
                "Klee repeated restore resolves each pending mine once");
    }

    private static void testC1PityAndTalentLevels() {
        SequenceSupplier pity = new SequenceSupplier(
                0.90, 0.90, 0.90, 0.90, 0.90, 0.50, 0.50);
        Klee klee = new Klee(
                null, null, 1, () -> 1.0, pity, () -> 1.0);
        CombatSimulator simulator = simulatorWith(klee);
        List<ActionRecord> records = captureKleeActions(simulator);
        for (int index = 0; index < 7; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> c1Hits = named(
                records, "Sparks 'n' Splash (C1)");
        assertEquals(1, c1Hits.size(), "Klee C1 pity success count");
        assertClose(1.20 * 0.72488,
                c1Hits.get(0).action.getDamagePercent(),
                "Klee C1 uses 120% of Burst multiplier");

        Klee c5 = deterministicKlee(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureKleeActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        advanceTo(c5Sim, 4.1);
        assertClose(1.904,
                named(c5Records, "Jumpy Dumpty (Bounce)").get(0).action
                        .getDamagePercent(),
                "Klee C3 Skill talent level");
        assertClose(0.656,
                named(c5Records, "Jumpy Dumpty (Mine)").get(0).action
                        .getDamagePercent(),
                "Klee C3 mine talent level");
    }

    private static void testBurstCadenceRandomSnapshotAndC6() {
        CountingSupplier burstDraws = new CountingSupplier(0.0);
        Klee klee = new Klee(
                null, null, 6, () -> 1.0, () -> 1.0, burstDraws);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO, 100.0);
        CombatSimulator simulator = simulatorWith(klee, ally);
        List<ActionRecord> records = captureKleeActions(simulator);
        SimulatorSnapshot[] beforeSnapshot = captureSnapshotAt(
                simulator, 50.0 * FRAME);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(12, burstDraws.getCount(),
                "Klee queues all Burst random outcomes at cast");
        simulator.restoreSnapshot(beforeSnapshot[0]);
        simulator.restoreSnapshot(beforeSnapshot[0]);
        assertEquals(12, burstDraws.getCount(),
                "Klee Burst restore does not redraw outcomes");
        advanceTo(simulator, 13.0);
        List<ActionRecord> burstHits = named(records, "Sparks 'n' Splash");
        assertEquals(30, burstHits.size(),
                "Klee six Burst waves include injected extras");
        assertClose(186.0 * FRAME, burstHits.get(0).time,
                "Klee first Burst wave");
        assertClose(742.0 * FRAME,
                burstHits.get(burstHits.size() - 1).time,
                "Klee final Burst hit");
        assertClose(0.8528, burstHits.get(0).action.getDamagePercent(),
                "Klee C5 Burst talent level");
        assertClose(0.388,
                burstHits.get(0).action.getStatSnapshot()
                        .get(StatType.PYRO_DMG_BONUS),
                "Klee C6 buff is active before Burst snapshot");
        assertClose(9.0, ally.getTotalFlatEnergy(),
                "Klee C6 restores three ally Energy ticks");
        assertClose(0.0, klee.getTotalFlatEnergy(),
                "Klee C6 excludes Klee from flat Energy");
        assertEquals(1, countTeamBuffs(
                simulator, BuffId.KLEE_C6_PYRO_DMG_BONUS),
                "Klee C6 team buff is typed");

        advanceTo(simulator, 15.0);
        klee.receiveEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, countTeamBuffs(
                simulator, BuffId.KLEE_C6_PYRO_DMG_BONUS),
                "Klee C6 recast refreshes without stacking");
    }

    private static void testBurstMidHitRepeatedRestore() {
        Klee klee = deterministicKlee(0);
        CombatSimulator simulator = simulatorWith(klee);
        List<ActionRecord> records = captureKleeActions(simulator);
        SimulatorSnapshot[] afterFirstHit = new SimulatorSnapshot[1];
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == klee
                    && action.getName().equals("Sparks 'n' Splash")
                    && afterFirstHit[0] == null) {
                afterFirstHit[0] = simulator.saveSnapshot();
            }
        });
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 186.0 * FRAME);
        assertTrue(afterFirstHit[0] != null,
                "Klee captures snapshot inside first Burst hit listener");
        records.clear();
        simulator.restoreSnapshot(afterFirstHit[0]);
        simulator.restoreSnapshot(afterFirstHit[0]);
        advanceTo(simulator, 13.0);
        assertEquals(17, named(records, "Sparks 'n' Splash").size(),
                "Klee mid-hit restore does not replay the resolved Burst hit");
    }

    private static void testBurstSwitchC4AndStaleSuppression() {
        Klee klee = deterministicKlee(6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO, 100.0);
        CombatSimulator simulator = simulatorWith(klee, ally);
        List<ActionRecord> records = captureKleeActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.switchCharacter(CharacterId.NOELLE);
        advanceTo(simulator, 13.0);
        assertEquals(1, named(records, "Sparkly Explosion (C4)").size(),
                "Klee C4 switch explosion count");
        AttackAction c4 = named(records, "Sparkly Explosion (C4)")
                .get(0).action;
        assertEquals(ActionType.OTHER, c4.getActionType(),
                "Klee C4 category");
        assertClose(5.55, c4.getDamagePercent(),
                "Klee C4 multiplier");
        assertClose(2.0, c4.getGaugeUnits(), "Klee C4 gauge");
        assertEquals(0, named(records, "Sparks 'n' Splash").size(),
                "Klee switch suppresses stale Burst hits");
        assertClose(0.0, ally.getTotalFlatEnergy(),
                "Klee switch suppresses stale C6 Energy");
    }

    private static void testC2LiveTargetDefenseReductionAndExpiry() {
        Klee klee = deterministicKlee(2);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.PHYSICAL, 100.0);
        CombatSimulator simulator = simulatorWith(klee, ally);
        List<Double> allyDamage = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == ally) {
                allyDamage.add(damage);
            }
        });
        List<ActionRecord> kleeRecords = captureKleeActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 4.1);
        assertClose(0.0,
                named(kleeRecords, "Jumpy Dumpty (Mine)").get(0).action
                        .getStatSnapshot()
                        .get(StatType.ENEMY_DEF_REDUCTION),
                "Klee C2 never enters Skill snapshots");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, directPhysicalProbe());
        double reducedDefenseDamage = allyDamage.get(0);
        simulator.advanceTime(10.1);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, directPhysicalProbe());
        double expiredDamage = allyDamage.get(1);
        assertTrue(reducedDefenseDamage > expiredDamage,
                "Klee C2 increases live target damage");
        assertClose(defenseMultiplier(0.233) / defenseMultiplier(0.0),
                reducedDefenseDamage / expiredDamage,
                "Klee C2 uses 23.3% target DEF reduction");
    }

    private static void testInvalidDrawsStateAndReuse() {
        assertThrows(IllegalArgumentException.class,
                () -> new Klee(null, null, -1),
                "Klee rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Klee(null, null, 7),
                "Klee rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Klee(null, null, 0, null, () -> 0.0, () -> 0.0),
                "Klee rejects null A1 source");
        assertThrows(IllegalArgumentException.class,
                () -> new Klee(null, null, 0, () -> 0.0, null, () -> 0.0),
                "Klee rejects null C1 source");
        assertThrows(IllegalArgumentException.class,
                () -> new Klee(null, null, 0, () -> 0.0, () -> 0.0, null),
                "Klee rejects null Burst source");

        Klee invalidC1 = new Klee(
                null, null, 1, () -> 1.0, () -> Double.NaN, () -> 1.0);
        CombatSimulator invalidC1Sim = simulatorWith(invalidC1);
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidC1Sim, CharacterActionKey.NORMAL),
                "Klee rejects non-finite C1 draw at consumption");

        Klee invalidBurst = new Klee(
                null, null, 0, () -> 1.0, () -> 1.0, () -> 1.1);
        CombatSimulator invalidBurstSim = simulatorWith(invalidBurst);
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidBurstSim, CharacterActionKey.BURST),
                "Klee rejects out-of-range Burst draw at consumption");

        Klee invalidA1 = new Klee(
                null, null, 0, () -> -0.1, () -> 1.0, () -> 1.0);
        CombatSimulator invalidA1Sim = simulatorWith(invalidA1);
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidA1Sim, CharacterActionKey.NORMAL),
                "Klee rejects out-of-range A1 draw at consumption");

        Klee unsupported = deterministicKlee(0);
        CombatSimulator unsupportedSim = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.DASH),
                "Klee rejects unsupported Dash");

        CountingSupplier skippedBurstDraws = new CountingSupplier(0.0);
        Klee insufficientEnergy = new Klee(
                null, null, 6, () -> 1.0, () -> 1.0,
                skippedBurstDraws);
        CombatSimulator insufficientEnergySim = simulatorWith(
                insufficientEnergy);
        insufficientEnergy.restoreCurrentEnergy(0.0);
        perform(insufficientEnergySim, CharacterActionKey.BURST);
        assertClose(60.0, insufficientEnergy.getMissedBurstCost(),
                "Klee rejects Burst with insufficient Energy");
        assertEquals(0, skippedBurstDraws.getCount(),
                "Klee skipped Burst consumes no random draws");

        Klee skillCooldown = deterministicKlee(0);
        CombatSimulator skillCooldownSim = simulatorWith(skillCooldown);
        perform(skillCooldownSim, CharacterActionKey.SKILL);
        perform(skillCooldownSim, CharacterActionKey.SKILL);
        perform(skillCooldownSim, CharacterActionKey.SKILL);
        assertClose(20.0 + 33.0 * FRAME + 75.0 * FRAME,
                skillCooldownSim.getCurrentTime(),
                "Klee serializes Skill after both charges are spent");

        Klee reusable = deterministicKlee(0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Klee rejects reuse across simulators");
        assertThrows(IllegalArgumentException.class,
                () -> reusable.restoreCharacterState(
                        new Collei(null, null, 0).captureCharacterState(),
                        new CombatSimulator()),
                "Klee rejects foreign character state");
        assertThrows(IllegalArgumentException.class,
                () -> reusable.onAction(null, new CombatSimulator()),
                "Klee rejects null action");
    }

    private static Klee deterministicKlee(int constellation) {
        return new Klee(
                null, null, constellation,
                () -> 1.0, () -> 1.0, () -> 1.0);
    }

    private static AttackAction directPhysicalProbe() {
        return new AttackAction(
                "Klee C2 Formula Probe",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
    }

    private static double defenseMultiplier(double reduction) {
        return 190.0 / (190.0 + 190.0 * (1.0 - reduction));
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
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
                CharacterId.KLEE, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureKleeActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KLEE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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
            assertTrue(lines.get(index).startsWith("Klee,"),
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
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
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

        private TestBuff(double duration, StatType stat, double amount) {
            super("Klee regression buff", duration, 0.0);
            this.stat = stat;
            this.amount = amount;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(stat, amount);
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element element,
                double baseAttack) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_ATK, baseAttack);
        }

        @Override
        public double getEnergyCost() {
            return 100.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }

    private static class CountingSupplier implements DoubleSupplier {
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

    private static final class SequenceSupplier extends CountingSupplier {
        private SequenceSupplier(double... values) {
            super(values);
        }
    }
}
