package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import model.character.Rosaria;
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

/** Focused regression checks for Rosaria's stationary offensive slice. */
public final class RosariaRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private RosariaRegressionTest() {
    }

    /** Runs identity, timing, particle, buff, constellation, and guard checks. */
    public static void main(String[] args) {
        testIdentityStatsAndConstellationConstruction();
        testNormalChargedAndPlungeTiming();
        testSkillTimingParticlesA1AndC3();
        testBurstCadenceSnapshotA4AndC2();
        testC5C6AndActualCritExclusions();
        testCooldownEnergyIsolationAndInvalidInputs();
        System.out.println("RosariaRegressionTest passed");
    }

    private static void testIdentityStatsAndConstellationConstruction() {
        Rosaria rosaria = rosariaAtConstellation(6);
        assertEquals(CharacterId.ROSARIA, rosaria.getCharacterId(),
                "Rosaria typed identity");
        assertEquals(CharacterId.ROSARIA, CharacterId.fromName("Rosaria"),
                "Rosaria display-name identity");
        assertEquals(CharacterId.ROSARIA, CharacterId.fromNumericId(21),
                "Rosaria numeric identity");
        assertEquals(Element.CRYO, rosaria.getElement(),
                "Rosaria element");
        assertClose(12289.0,
                rosaria.getBaseStats().get(StatType.BASE_HP), EPS,
                "Rosaria base HP");
        assertClose(240.0,
                rosaria.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Rosaria base ATK");
        assertClose(710.0,
                rosaria.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Rosaria base DEF");
        assertClose(0.24,
                rosaria.getBaseStats().get(StatType.ATK_PERCENT), EPS,
                "Rosaria ascension ATK");
        assertClose(60.0, rosaria.getEnergyCost(), EPS,
                "Rosaria Energy cost");
        assertClose(6.0, rosaria.getSkillCD(), EPS,
                "Rosaria Skill cooldown");
        assertClose(15.0, rosaria.getBurstCD(), EPS,
                "Rosaria Burst cooldown");

        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(
                    constellation,
                    rosariaAtConstellation(constellation).getConstellation(),
                    "Rosaria explicit constellation C" + constellation);
        }
    }

    private static void testNormalChargedAndPlungeTiming() {
        Rosaria rosaria = rosariaAtConstellation(0);
        CombatSimulator sim = simulatorWith(rosaria);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Spear of the Church N");
        double[][] multipliers = {
                { 0.9638 },
                { 0.9480 },
                { 0.5846, 0.5846 },
                { 1.2798 },
                { 0.76472, 0.7900 }
        };
        int[][] hitmarks = {
                { 9 }, { 13 }, { 19, 28 }, { 32 }, { 26, 40 }
        };
        int[] durations = { 24, 27, 34, 52, 66 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = sim.getCurrentTime();
            perform(sim, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < multipliers[step].length; hit++) {
                ActionRecord record = normals.get(recordIndex++);
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(), EPS,
                        "Rosaria Normal multiplier");
                assertClose(castTime + hitmarks[step][hit] * FRAME,
                        record.time, EPS,
                        "Rosaria Normal hitmark");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Rosaria Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Rosaria Normal category");
                assertClose(0.0, record.action.getGaugeUnits(), EPS,
                        "Rosaria Normal has no aura");
            }
            assertClose(castTime + durations[step] * FRAME,
                    sim.getCurrentTime(), EPS,
                    "Rosaria Normal animation length");
        }
        perform(sim, CharacterActionKey.NORMAL);
        assertTrue(normals.get(recordIndex).action.getName().contains("N1"),
                "Rosaria Normal chain wraps after N5");

        Rosaria chargedRosaria = rosariaAtConstellation(0);
        CombatSimulator chargedSim = simulatorWith(chargedRosaria);
        List<ActionRecord> charged = captureNamedActions(
                chargedSim, "Spear of the Church Charged");
        perform(chargedSim, CharacterActionKey.CHARGE);
        assertEquals(1, charged.size(), "Rosaria Charged hit count");
        assertClose(2.5122,
                charged.get(0).action.getDamagePercent(), EPS,
                "Rosaria Charged multiplier");
        assertClose(22.0 * FRAME, charged.get(0).time, EPS,
                "Rosaria Charged hitmark");
        assertClose(69.0 * FRAME, chargedSim.getCurrentTime(), EPS,
                "Rosaria Charged animation length");
        assertEquals(ICDTag.ChargedAttack,
                charged.get(0).action.getICDTag(),
                "Rosaria Charged typed ICD tag");

        Rosaria plungeRosaria = rosariaAtConstellation(0);
        CombatSimulator plungeSim = simulatorWith(plungeRosaria);
        List<ActionRecord> plunges = captureNamedActions(
                plungeSim, "Spear of the Church High Plunge");
        perform(plungeSim, CharacterActionKey.PLUNGE);
        assertClose(2.933586,
                plunges.get(0).action.getDamagePercent(), EPS,
                "Rosaria high Plunge multiplier");
        assertClose(43.0 * FRAME, plunges.get(0).time, EPS,
                "Rosaria high Plunge hitmark");
        assertClose(80.0 * FRAME, plungeSim.getCurrentTime(), EPS,
                "Rosaria high Plunge animation length");
        assertTrue(plunges.get(0).action.isShatterTrigger(),
                "Rosaria high Plunge is blunt");
    }

    private static void testSkillTimingParticlesA1AndC3() {
        Rosaria c0 = rosariaAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Hits = captureNamedActions(
                c0Sim, "Ravaging Confession");
        perform(c0Sim, CharacterActionKey.SKILL);
        assertEquals(2, c0Hits.size(), "Rosaria Skill hit count");
        assertClose(24.0 * FRAME, c0Hits.get(0).time, EPS,
                "Rosaria Skill first hitmark");
        assertClose(38.0 * FRAME, c0Hits.get(1).time, EPS,
                "Rosaria Skill second hitmark");
        assertClose(0.9928,
                c0Hits.get(0).action.getDamagePercent(), EPS,
                "Rosaria Skill first multiplier");
        assertClose(2.3120,
                c0Hits.get(1).action.getDamagePercent(), EPS,
                "Rosaria Skill second multiplier");
        for (ActionRecord record : c0Hits) {
            assertEquals(ICDType.None, record.action.getICDType(),
                    "Rosaria Skill has no ICD");
            assertClose(1.0, record.action.getGaugeUnits(), EPS,
                    "Rosaria Skill applies 1U");
        }
        assertClose(51.0 * FRAME, c0Sim.getCurrentTime(), EPS,
                "Rosaria Skill animation length");
        assertClose(23.0 * FRAME, c0.getLastSkillTime(), EPS,
                "Rosaria Skill cooldown start");
        assertClose(332.0 * FRAME,
                c0.getSkillCDRemaining(c0Sim.getCurrentTime()), EPS,
                "Rosaria Skill remaining cooldown");
        assertClose(0.17,
                c0.getEffectiveStats(c0Sim.getCurrentTime()).get(
                        StatType.CRIT_RATE),
                EPS, "Rosaria A1 applies before first hit");
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Rosaria Skill particles remain in flight");
        advanceTo(c0Sim, 138.0 * FRAME - 0.001);
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Rosaria particles wait for 100-frame travel");
        advanceTo(c0Sim, 138.0 * FRAME);
        assertClose(9.0, c0.getTotalParticleEnergy(), EPS,
                "Rosaria receives three same-element particles");
        advanceTo(c0Sim, 324.0 * FRAME - 0.001);
        assertClose(0.17,
                c0.getEffectiveStats(c0Sim.getCurrentTime()).get(
                        StatType.CRIT_RATE),
                EPS, "Rosaria A1 remains active before expiry");
        advanceTo(c0Sim, 324.0 * FRAME);
        assertClose(0.05,
                c0.getEffectiveStats(c0Sim.getCurrentTime()).get(
                        StatType.CRIT_RATE),
                EPS, "Rosaria A1 exact half-open expiry");

        Rosaria c3 = rosariaAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Hits = captureNamedActions(
                c3Sim, "Ravaging Confession");
        perform(c3Sim, CharacterActionKey.SKILL);
        assertClose(1.1680,
                c3Hits.get(0).action.getDamagePercent(), EPS,
                "Rosaria C3 first Skill multiplier");
        assertClose(2.7200,
                c3Hits.get(1).action.getDamagePercent(), EPS,
                "Rosaria C3 second Skill multiplier");
    }

    private static void testBurstCadenceSnapshotA4AndC2() {
        Rosaria c0 = rosariaAtConstellation(0);
        c0.addBuff(new SimpleBuff(
                "Rosaria test CRIT",
                30.0,
                0.0,
                stats -> stats.add(StatType.CRIT_RATE, 0.95)));
        c0.addBuff(new SimpleBuff(
                "Rosaria frame-15 ATK",
                30.0 * FRAME,
                10.0 * FRAME,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator c0Sim = simulatorWith(c0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        c0Sim.addCharacter(ally);
        List<ActionRecord> c0Hits = captureNamedActions(
                c0Sim, "Rites of Termination");
        perform(c0Sim, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(), EPS,
                "Rosaria Burst spends 60 Energy");
        assertClose(0.0, c0.getLastBurstTime(), EPS,
                "Rosaria Burst cooldown starts at cast");
        assertEquals(2, c0Hits.size(),
                "Rosaria Burst initial hit count after animation");
        assertClose(15.0 * FRAME, c0Hits.get(0).time, EPS,
                "Rosaria Burst first hitmark");
        assertClose(56.0 * FRAME, c0Hits.get(1).time, EPS,
                "Rosaria Burst second hitmark");
        assertClose(70.0 * FRAME, c0Sim.getCurrentTime(), EPS,
                "Rosaria Burst animation length");
        assertTrue(c0Hits.get(0).action.isUseSnapshot(),
                "Rosaria Burst initial hit is snapshotted");
        assertClose(1.24,
                c0Hits.get(0).action.getStatSnapshot().get(
                        StatType.ATK_PERCENT), EPS,
                "Rosaria first Burst hit snapshots at frame 15");
        assertClose(0.24,
                c0Hits.get(1).action.getStatSnapshot().get(
                        StatType.ATK_PERCENT), EPS,
                "Rosaria lance snapshots independently at frame 56");
        assertClose(0.15,
                applicableStats(c0Sim, ally).get(StatType.CRIT_RATE) - 0.05,
                EPS, "Rosaria A4 share caps at 15 percent");
        assertClose(1.0,
                applicableStats(c0Sim, c0).get(StatType.CRIT_RATE),
                EPS, "Rosaria A4 excludes its source");

        double[] tickTimes = { 176, 296, 416, 536 };
        for (int i = 0; i < tickTimes.length; i++) {
            advanceTo(c0Sim, tickTimes[i] * FRAME);
            assertEquals(i + 3, c0Hits.size(),
                    "Rosaria C0 Burst pulse count");
            assertClose(tickTimes[i] * FRAME,
                    c0Hits.get(i + 2).time, EPS,
                    "Rosaria Burst pulse timestamp");
            assertClose(2.2440,
                    c0Hits.get(i + 2).action.getDamagePercent(), EPS,
                    "Rosaria Burst pulse multiplier");
        }
        assertTrue(c0.isBurstActive(566.0 * FRAME - 0.001),
                "Rosaria C0 field active before expiry");
        assertFalse(c0.isBurstActive(566.0 * FRAME),
                "Rosaria C0 field exact expiry");
        advanceTo(c0Sim, 10.0);
        assertClose(0.0,
                applicableStats(c0Sim, ally).get(StatType.CRIT_RATE) - 0.05,
                EPS, "Rosaria A4 exact half-open expiry");

        Rosaria c2 = rosariaAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Hits = captureNamedActions(
                c2Sim, "Rites of Termination");
        perform(c2Sim, CharacterActionKey.BURST);
        advanceTo(c2Sim, 776.0 * FRAME);
        assertEquals(8, c2Hits.size(),
                "Rosaria C2 adds two Burst pulses");
        assertClose(656.0 * FRAME, c2Hits.get(6).time, EPS,
                "Rosaria C2 fifth pulse timestamp");
        assertClose(776.0 * FRAME, c2Hits.get(7).time, EPS,
                "Rosaria C2 sixth pulse timestamp");
        assertTrue(c2.isBurstActive(806.0 * FRAME - 0.001),
                "Rosaria C2 field active before expiry");
        assertFalse(c2.isBurstActive(806.0 * FRAME),
                "Rosaria C2 field exact expiry");
    }

    private static void testC5C6AndActualCritExclusions() {
        Rosaria c5 = rosariaAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Hits = captureNamedActions(
                c5Sim, "Rites of Termination");
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, 176.0 * FRAME);
        assertClose(2.0800,
                c5Hits.get(0).action.getDamagePercent(), EPS,
                "Rosaria C5 first Burst multiplier");
        assertClose(3.0400,
                c5Hits.get(1).action.getDamagePercent(), EPS,
                "Rosaria C5 second Burst multiplier");
        assertClose(2.6400,
                c5Hits.get(2).action.getDamagePercent(), EPS,
                "Rosaria C5 DoT multiplier");

        Rosaria c6 = rosariaAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.BURST);
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(
                        StatType.PHYS_RES_SHRED),
                EPS, "Rosaria C6 applies Physical shred");
        advanceTo(c6Sim, 776.0 * FRAME);
        Buff c6Buff = findApplicableBuff(
                c6Sim, c6, BuffId.ROSARIA_C6_PHYSICAL_RES_SHRED);
        assertClose(776.0 * FRAME + 10.0,
                c6Buff.getExpirationTime(), EPS,
                "Rosaria C6 DoT refreshes Physical shred");
        advanceTo(c6Sim, 776.0 * FRAME + 10.0 - 0.001);
        assertClose(0.20,
                applicableStats(c6Sim, c6).get(
                        StatType.PHYS_RES_SHRED),
                EPS, "Rosaria C6 active before final expiry");
        advanceTo(c6Sim, 776.0 * FRAME + 10.0);
        assertClose(0.0,
                applicableStats(c6Sim, c6).get(
                        StatType.PHYS_RES_SHRED),
                EPS, "Rosaria C6 exact half-open expiry");

        Rosaria c1 = rosariaAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        perform(c1Sim, CharacterActionKey.NORMAL);
        assertClose(0.0,
                c1.getEffectiveStats(c1Sim.getCurrentTime()).get(
                        StatType.NORMAL_ATTACK_DMG_BONUS),
                EPS, "Rosaria C1 actual-crit effect remains excluded");
        assertClose(0.0,
                c1.getEffectiveStats(c1Sim.getCurrentTime()).get(
                        StatType.ATK_SPD),
                EPS, "Rosaria C1 attack speed remains excluded");

        Rosaria c4 = rosariaAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        c4.restoreCurrentEnergy(0.0);
        perform(c4Sim, CharacterActionKey.SKILL);
        advanceTo(c4Sim, 138.0 * FRAME);
        assertClose(9.0, c4.getCurrentEnergy(), EPS,
                "Rosaria C4 does not synthesize actual-crit Energy");
        assertClose(0.0, c4.getTotalFlatEnergy(), EPS,
                "Rosaria C4 actual-crit flat Energy remains excluded");
    }

    private static void testCooldownEnergyIsolationAndInvalidInputs() {
        Rosaria cooldown = rosariaAtConstellation(0);
        CombatSimulator cooldownSim = simulatorWith(cooldown);
        List<ActionRecord> skillHits = captureNamedActions(
                cooldownSim, "Ravaging Confession");
        perform(cooldownSim, CharacterActionKey.SKILL);
        perform(cooldownSim, CharacterActionKey.SKILL);
        assertClose(434.0 * FRAME, cooldownSim.getCurrentTime(), EPS,
                "Rosaria repeated Skill waits for cooldown");
        assertClose(407.0 * FRAME, skillHits.get(2).time, EPS,
                "Rosaria repeated Skill first hit timing");
        assertClose(421.0 * FRAME, skillHits.get(3).time, EPS,
                "Rosaria repeated Skill second hit timing");

        Rosaria insufficient = rosariaAtConstellation(0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        List<ActionRecord> burstHits = captureNamedActions(
                insufficientSim, "Rites of Termination");
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertEquals(0, burstHits.size(),
                "Rosaria Burst rejects insufficient Energy");
        assertClose(0.0, insufficientSim.getCurrentTime(), EPS,
                "Rosaria rejected Burst does not advance time");
        assertClose(60.0, insufficient.getMissedBurstCost(), EPS,
                "Rosaria rejected Burst records missed cost");

        Rosaria first = rosariaAtConstellation(0);
        Rosaria second = rosariaAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        List<ActionRecord> firstHits = captureNamedActions(
                firstSim, "Rites of Termination");
        List<ActionRecord> secondHits = captureNamedActions(
                secondSim, "Rites of Termination");
        perform(firstSim, CharacterActionKey.BURST);
        perform(secondSim, CharacterActionKey.BURST);
        advanceTo(firstSim, 176.0 * FRAME);
        assertEquals(3, firstHits.size(),
                "Rosaria first instance owns its timers");
        assertEquals(2, secondHits.size(),
                "Rosaria second instance time is independent");
        advanceTo(secondSim, 176.0 * FRAME);
        assertEquals(3, secondHits.size(),
                "Rosaria second instance resolves independently");

        assertThrows(IllegalArgumentException.class,
                () -> rosariaAtConstellation(-1),
                "Rosaria rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> rosariaAtConstellation(7),
                "Rosaria rejects constellation above six");
        assertThrows(IllegalArgumentException.class,
                () -> perform(firstSim, CharacterActionKey.DASH),
                "Rosaria rejects unsupported Dash");

        first.initializeForSimulator(firstSim);
        CombatSimulator rejected = new CombatSimulator();
        rejected.setLoggingEnabled(false);
        rejected.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> rejected.addCharacter(first),
                "Rosaria rejects cross-simulator reuse");
    }

    private static Rosaria rosariaAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Rosaria(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(Rosaria rosaria) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(rosaria);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.ROSARIA,
                CharacterActionRequest.of(key));
    }

    private static void advanceTo(CombatSimulator sim, double targetTime) {
        double delta = targetTime - sim.getCurrentTime();
        if (delta > 0.0) {
            sim.advanceTime(delta);
        }
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ROSARIA
                    && action.getName().startsWith(namePrefix)) {
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

    private static Buff findApplicableBuff(
            CombatSimulator sim,
            Character target,
            BuffId id) {
        for (Buff buff : sim.getApplicableBuffs(target)) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        throw new AssertionError("Missing applicable buff: " + id);
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

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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

    /** Minimal ally used to verify Rosaria's team-only A4 targeting. */
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
