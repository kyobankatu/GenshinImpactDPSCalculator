package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Diona;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused metadata, action, field, constellation, and guard checks. */
public final class DionaRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private DionaRegressionTest() {
    }

    /** Runs Diona's complete offensive-slice regression cases. */
    public static void main(String[] args) {
        testMetadataAndConstructorGuards();
        testNormalChargeAndPlungeActions();
        testNormalChainSnapshotRestore();
        testHoldSkillAndC2();
        testDelayedProjectileAndParticleSnapshotRestore();
        testBurstTicksC1C4AndC6();
        testBurstSnapshotRestore();
        testEnergyCooldownAndBindingGuards();
        System.out.println("DionaRegressionTest passed");
    }

    private static void testMetadataAndConstructorGuards() {
        Diona diona = new Diona(null, null);
        assertEquals(CharacterId.DIONA, diona.getCharacterId(),
                "Diona identity");
        assertClose(9570.0, diona.getBaseStats().get(StatType.BASE_HP),
                "Diona base HP");
        assertClose(212.0, diona.getBaseStats().get(StatType.BASE_ATK),
                "Diona base ATK");
        assertClose(601.0, diona.getBaseStats().get(StatType.BASE_DEF),
                "Diona base DEF");
        assertClose(0.24,
                diona.getBaseStats().get(StatType.CRYO_DMG_BONUS),
                "Diona ascension Cryo DMG");
        assertClose(80.0, diona.getEnergyCost(), "Diona Energy cost");
        assertClose(15.0, diona.getSkillCD(), "Diona Hold Skill cooldown");
        assertClose(20.0, diona.getBurstCD(), "Diona Burst cooldown");
        assertThrows(IllegalArgumentException.class,
                () -> new Diona(null, null, -1),
                "Diona rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Diona(null, null, 7),
                "Diona rejects constellation seven");
    }

    private static void testNormalChargeAndPlungeActions() {
        Diona diona = new Diona(null, null, 0);
        CombatSimulator sim = simulatorWith(diona);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Katzlein Style N");
        for (int step = 0; step < 5; step++) {
            perform(sim, CharacterActionKey.NORMAL);
        }
        assertEquals(5, normals.size(), "Diona Normal hit count");
        assertClose(29.0 * FRAME, normals.get(0).time,
                "Diona N1 hitmark plus projectile travel");
        double afterNormals = sim.getTotalDamage();
        assertTrue(afterNormals > 0.0, "Diona Normal chain deals damage");

        List<ActionRecord> charged = captureNamedActions(
                sim, "Katzlein Style Fully Charged");
        double chargeStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.CHARGE);
        assertEquals(0, charged.size(),
                "Diona charged projectile remains in flight at frame 94");
        sim.advanceTime(2.0 * FRAME + 0.001);
        assertEquals(1, charged.size(), "Diona charged projectile hit count");
        assertClose(chargeStart + 96.0 * FRAME, charged.get(0).time,
                "Diona charged snapshot and travel timing");
        perform(sim, CharacterActionKey.PLUNGE);
        assertTrue(sim.getTotalDamage() > afterNormals,
                "Diona Charged and Plunging attacks deal damage");
    }

    private static void testHoldSkillAndC2() {
        Diona c0 = new Diona(null, null, 0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Paws = captureNamedActions(c0Sim, "Icy Paw");
        c0.restoreCurrentEnergy(0.0);
        perform(c0Sim, CharacterActionKey.SKILL);
        assertEquals(5, c0Paws.size(), "Diona Hold Skill paw count");
        for (ActionRecord paw : c0Paws) {
            assertClose(0.71264, paw.action.getDamagePercent(),
                    "Diona C0 Skill level-9 multiplier");
            assertTrue(paw.action.hasStatSnapshot(),
                    "Diona paw owns cast-time snapshot");
        }
        c0Sim.advanceTime(2.5);
        double c0Damage = c0Sim.getTotalDamage();
        assertTrue(c0Damage > 0.0, "Diona Hold Skill resolves five paws");
        assertClose(15.0 + 29.0 * FRAME,
                c0.getSkillCooldownEndTime(),
                "Diona Hold Skill starts cooldown at frame 29");
        assertClose(12.0, c0.getCurrentEnergy(),
                "Diona Hold Skill resolves four expected Cryo particles");

        Diona c2 = new Diona(null, null, 2);
        CombatSimulator c2Sim = simulatorWith(c2);
        perform(c2Sim, CharacterActionKey.SKILL);
        c2Sim.advanceTime(2.5);
        assertTrue(c2Sim.getTotalDamage() > c0Damage,
                "Diona C2 increases Icy Paw damage");

        Diona snapshotDiona = new Diona(null, null, 0);
        snapshotDiona.addBuff(new SimpleBuff(
                "Diona cast-only Skill ATK",
                20.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator snapshotSim = simulatorWith(snapshotDiona);
        List<ActionRecord> snapshotPaws = captureNamedActions(
                snapshotSim, "Icy Paw");
        perform(snapshotSim, CharacterActionKey.SKILL);
        assertClose(1.0,
                snapshotPaws.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Diona paw retains cast-only ATK");

        Diona c5 = new Diona(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Paws = captureNamedActions(c5Sim, "Icy Paw");
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(0.8384, c5Paws.get(0).action.getDamagePercent(),
                "Diona C5 raises Skill to level 12");
    }

    private static void testNormalChainSnapshotRestore() {
        Diona diona = new Diona(null, null, 0);
        CombatSimulator sim = simulatorWith(diona);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Katzlein Style N");
        perform(sim, CharacterActionKey.NORMAL);
        perform(sim, CharacterActionKey.NORMAL);
        SimulatorSnapshot branchSnapshot = sim.saveSnapshot();
        double branchDamage = sim.getTotalDamage();

        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord expectedN3 = normals.get(normals.size() - 1);
        double expectedDamage = sim.getTotalDamage();

        sim.restoreSnapshot(branchSnapshot);
        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord restoredN3 = normals.get(normals.size() - 1);
        assertEquals("Katzlein Style N3", restoredN3.action.getName(),
                "Diona restored Normal chain resumes at N3");
        assertClose(expectedN3.time, restoredN3.time,
                "Diona restored N3 keeps branch timing");
        assertClose(expectedN3.damage, restoredN3.damage,
                "Diona restored N3 keeps branch damage");
        assertClose(expectedDamage, sim.getTotalDamage(),
                "Diona restored Normal branch keeps total damage");
        assertTrue(sim.getTotalDamage() > branchDamage,
                "Diona restored Normal branch resolves its hit");
    }

    private static void testDelayedProjectileAndParticleSnapshotRestore() {
        Diona chargedDiona = new Diona(null, null, 0);
        CombatSimulator chargedSim = simulatorWith(chargedDiona);
        List<ActionRecord> charged = captureNamedActions(
                chargedSim, "Katzlein Style Fully Charged");
        perform(chargedSim, CharacterActionKey.CHARGE);
        SimulatorSnapshot chargedSnapshot = chargedSim.saveSnapshot();
        assertEquals(0, charged.size(),
                "Diona snapshot captures charged projectile in flight");
        chargedSim.advanceTime(2.0 * FRAME + 0.001);
        assertEquals(1, charged.size(),
                "Diona original charged projectile resolves");
        double impactTime = charged.get(0).time;

        chargedSim.restoreSnapshot(chargedSnapshot);
        charged.clear();
        chargedSim.advanceTime(2.0 * FRAME + 0.001);
        assertEquals(1, charged.size(),
                "Diona restored charged projectile resolves once");
        assertClose(impactTime, charged.get(0).time,
                "Diona restored charged projectile keeps impact time");

        chargedSim.restoreSnapshot(chargedSnapshot);
        chargedSim.restoreSnapshot(chargedSnapshot);
        charged.clear();
        chargedSim.advanceTime(2.0 * FRAME + 0.001);
        assertEquals(1, charged.size(),
                "Diona repeated restore keeps one charged impact");

        Diona particleDiona = new Diona(null, null, 0);
        CombatSimulator particleSim = simulatorWith(particleDiona);
        List<Double> particleTimes = new ArrayList<>();
        particleSim.addParticleListener((element, count, time) ->
                particleTimes.add(time));
        perform(particleSim, CharacterActionKey.SKILL);
        particleDiona.resetSkillCooldown(particleSim.getCurrentTime());
        perform(particleSim, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSim.saveSnapshot();
        particleSim.advanceTime(3.0);
        assertEquals(2, particleTimes.size(),
                "Diona original branch resolves two particle packets");

        particleSim.restoreSnapshot(particleSnapshot);
        particleTimes.clear();
        particleSim.advanceTime(3.0);
        assertEquals(2, particleTimes.size(),
                "Diona restore replays two particle packets");

        particleSim.restoreSnapshot(particleSnapshot);
        particleSim.restoreSnapshot(particleSnapshot);
        particleTimes.clear();
        particleSim.advanceTime(3.0);
        assertEquals(2, particleTimes.size(),
                "Diona repeated restore keeps two particle packets");
    }

    private static void testBurstTicksC1C4AndC6() {
        Diona c0 = new Diona(null, null, 0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Initial = captureNamedActions(
                c0Sim, "Signature Mix Initial");
        perform(c0Sim, CharacterActionKey.BURST);
        assertClose(1.3600,
                c0Initial.get(0).action.getDamagePercent(),
                "Diona C0 Burst level-9 multiplier");
        assertTrue(c0Initial.get(0).action.hasStatSnapshot(),
                "Diona Burst initial owns cast-time snapshot");
        assertClose(41.0 * FRAME, c0.getLastBurstTime(),
                "Diona Burst cooldown starts at frame 41");
        assertClose(20.0 + 41.0 * FRAME, c0.getBurstCooldownEndTime(),
                "Diona Burst cooldown keeps sourced delay");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Diona Burst spends Energy at frame 43");

        Diona c3 = new Diona(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Initial = captureNamedActions(
                c3Sim, "Signature Mix Initial");
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(1.6000,
                c3Initial.get(0).action.getDamagePercent(),
                "Diona C3 raises Burst to level 12");

        Diona diona = new Diona(null, null, 6);
        CombatSimulator sim = simulatorWith(diona);
        perform(sim, CharacterActionKey.BURST);
        assertTrue(diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona Burst field starts at impact");
        StatsContainer fieldStats = resolvedStats(diona, sim);
        assertClose(200.0, fieldStats.get(StatType.ELEMENTAL_MASTERY),
                "Diona C6 grants field EM at full HP boundary");
        SimulatorSnapshot fieldSnapshot = sim.saveSnapshot();

        double chargeStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.CHARGE);
        assertClose(58.0 / 60.0, sim.getCurrentTime() - chargeStart,
                "Diona C4 shortens field Charged action");
        sim.advanceTime(11.5);
        assertTrue(sim.getTotalDamage() > 0.0,
                "Diona Burst initial and periodic ticks resolve");
        sim.advanceTime(2.0);
        assertTrue(!diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona Burst field expires after 12.5 seconds");
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona C1 restores flat Energy after field ends");
        assertClose(0.0,
                resolvedStats(diona, sim).get(StatType.ELEMENTAL_MASTERY),
                "Diona C6 EM expires with field");

        sim.restoreSnapshot(fieldSnapshot);
        assertTrue(diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona restored Burst field remains active");
        assertClose(200.0,
                resolvedStats(diona, sim).get(StatType.ELEMENTAL_MASTERY),
                "Diona restored C6 field retains EM");
        sim.advanceTime(12.4);
        assertTrue(!diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona restored Burst field keeps exact expiry");
    }

    private static void testEnergyCooldownAndBindingGuards() {
        Diona diona = new Diona(null, null, 0);
        CombatSimulator sim = simulatorWith(diona);
        diona.restoreCurrentEnergy(0.0);
        double before = sim.getCurrentTime();
        perform(sim, CharacterActionKey.BURST);
        assertClose(before, sim.getCurrentTime(),
                "Diona insufficient Energy skips Burst");
        assertClose(80.0, diona.getMissedBurstCost(),
                "Diona records missed Burst cost");
        diona.initializeForSimulator(sim);
        assertThrows(IllegalStateException.class,
                () -> diona.initializeForSimulator(new CombatSimulator()),
                "Diona rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> diona.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH), sim),
                "Diona rejects unsupported Dash");
    }

    private static void testBurstSnapshotRestore() {
        Diona diona = new Diona(null, null, 3);
        diona.addBuff(new SimpleBuff(
                "Diona cast-only Burst ATK",
                90.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator sim = simulatorWith(diona);
        List<ActionRecord> ticks = captureNamedActions(
                sim, "Signature Mix Tick");
        perform(sim, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = sim.saveSnapshot();
        double savedDamage = sim.getTotalDamage();
        double refundTime = 58.0 * FRAME + 12.5;

        advanceTo(sim, refundTime - 0.001);
        assertEquals(6, ticks.size(),
                "Diona original branch resolves six future Burst ticks");
        assertClose(0.0, diona.getCurrentEnergy(),
                "Diona C1 refund waits for field end");
        advanceTo(sim, refundTime);
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona original branch resolves one C1 refund");
        List<ActionRecord> expectedTicks = new ArrayList<>(ticks);
        double expectedDamage = sim.getTotalDamage();
        assertTrue(expectedDamage > savedDamage,
                "Diona future Burst ticks add damage");
        assertBurstTickSequence(expectedTicks);

        sim.restoreSnapshot(burstSnapshot);
        ticks.clear();
        assertClose(0.0, diona.getCurrentEnergy(),
                "Diona Burst restore rewinds C1 Energy");
        advanceTo(sim, refundTime - 0.001);
        assertClose(0.0, diona.getCurrentEnergy(),
                "Diona restored C1 refund keeps exact deadline");
        advanceTo(sim, refundTime);
        assertBurstReplay(expectedTicks, ticks);
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona restored branch resolves one C1 refund");
        assertClose(expectedDamage, sim.getTotalDamage(),
                "Diona restored Burst branch keeps exact future damage");

        sim.restoreSnapshot(burstSnapshot);
        ticks.clear();
        advanceTo(sim, 58.0 * FRAME + 4.0);
        assertEquals(2, ticks.size(),
                "Diona progress branch consumes two Burst ticks");
        SimulatorSnapshot progressSnapshot = sim.saveSnapshot();
        ticks.clear();
        advanceTo(sim, refundTime);
        List<ActionRecord> expectedRemaining = new ArrayList<>(
                expectedTicks.subList(2, expectedTicks.size()));
        assertBurstReplay(expectedRemaining, ticks);
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona progress branch resolves one C1 refund");

        sim.restoreSnapshot(progressSnapshot);
        ticks.clear();
        advanceTo(sim, refundTime);
        assertBurstReplay(expectedRemaining, ticks);
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona progress restore resolves one C1 refund");
        assertClose(expectedDamage, sim.getTotalDamage(),
                "Diona progress restore replays only remaining damage");

        sim.restoreSnapshot(burstSnapshot);
        sim.restoreSnapshot(burstSnapshot);
        ticks.clear();
        advanceTo(sim, refundTime);
        assertBurstReplay(expectedTicks, ticks);
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona repeated restore leaves one C1 refund");
        assertClose(expectedDamage, sim.getTotalDamage(),
                "Diona repeated restore leaves one future damage sequence");

        SimulatorSnapshot completedSnapshot = sim.saveSnapshot();
        ticks.clear();
        sim.restoreSnapshot(completedSnapshot);
        sim.advanceTime(20.0);
        assertEquals(0, ticks.size(),
                "Diona completed Burst restore schedules no expired ticks");
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona completed Burst restore schedules no extra refund");
    }

    private static void assertBurstTickSequence(List<ActionRecord> ticks) {
        assertEquals(6, ticks.size(), "Diona Burst future tick count");
        for (int index = 0; index < ticks.size(); index++) {
            ActionRecord tick = ticks.get(index);
            assertEquals("Signature Mix Tick " + (index + 1),
                    tick.action.getName(),
                    "Diona Burst tick order");
            assertClose(1.0528, tick.action.getDamagePercent(),
                    "Diona restored Burst retains C3 multiplier");
            assertClose(1.0,
                    tick.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                    "Diona Burst tick retains cast-only snapshot");
            assertClose(58.0 * FRAME + (index + 1) * 2.0,
                    tick.time,
                    "Diona Burst tick absolute timing");
        }
    }

    private static void assertBurstReplay(
            List<ActionRecord> expected,
            List<ActionRecord> actual) {
        assertEquals(expected.size(), actual.size(),
                "Diona restored Burst tick count");
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).action.getName(),
                    actual.get(index).action.getName(),
                    "Diona restored Burst tick identity");
            assertClose(expected.get(index).action.getDamagePercent(),
                    actual.get(index).action.getDamagePercent(),
                    "Diona restored Burst tick multiplier");
            assertClose(expected.get(index).action.getStatSnapshot()
                            .get(StatType.ATK_PERCENT),
                    actual.get(index).action.getStatSnapshot()
                            .get(StatType.ATK_PERCENT),
                    "Diona restored Burst cast snapshot");
            assertClose(expected.get(index).time, actual.get(index).time,
                    "Diona restored Burst tick timing");
            assertClose(expected.get(index).damage, actual.get(index).damage,
                    "Diona restored Burst tick damage");
        }
    }

    private static void advanceTo(CombatSimulator sim, double targetTime) {
        sim.advanceTime(targetTime - sim.getCurrentTime());
    }

    private static CombatSimulator simulatorWith(Diona diona) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(diona);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.DIONA,
                CharacterActionRequest.of(key));
    }

    private static StatsContainer resolvedStats(
            Diona diona,
            CombatSimulator sim) {
        StatsContainer stats = diona.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(diona)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DIONA
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
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
