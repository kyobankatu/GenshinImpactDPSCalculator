package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataSource;
import model.character.Ganyu;
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

/** Focused regression checks for Ganyu's stationary offensive slice. */
public final class GanyuRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private GanyuRegressionTest() {
    }

    /** Runs identity, timing, particle, field, constellation, and guard checks. */
    public static void main(String[] args) {
        testIdentityStatsAndConstellationConstruction();
        testNormalAndFrostflakeTimingA1AndC1();
        testSkillTimingParticlesC2C5AndC6();
        testBurstCadenceSnapshotA4C3AndC4();
        testCooldownEnergyIsolationAndInvalidInputs();
        System.out.println("GanyuRegressionTest passed");
    }

    private static void testIdentityStatsAndConstellationConstruction() {
        Ganyu ganyu = ganyuAtConstellation(6);
        assertEquals(CharacterId.GANYU, ganyu.getCharacterId(),
                "Ganyu typed identity");
        assertEquals(CharacterId.GANYU, CharacterId.fromName("Ganyu"),
                "Ganyu display-name identity");
        assertEquals(CharacterId.GANYU, CharacterId.fromNumericId(25),
                "Ganyu numeric identity");
        assertEquals(Element.CRYO, ganyu.getElement(), "Ganyu element");
        assertClose(9797.0,
                ganyu.getBaseStats().get(StatType.BASE_HP), EPS,
                "Ganyu base HP");
        assertClose(335.0,
                ganyu.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Ganyu base ATK");
        assertClose(630.0,
                ganyu.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Ganyu base DEF");
        assertClose(0.884,
                ganyu.getBaseStats().get(StatType.CRIT_DMG), EPS,
                "Ganyu base plus ascension CRIT DMG");
        assertClose(60.0, ganyu.getEnergyCost(), EPS,
                "Ganyu Energy cost");
        assertClose(10.0, ganyu.getSkillCD(), EPS,
                "Ganyu Skill cooldown");
        assertClose(15.0, ganyu.getBurstCD(), EPS,
                "Ganyu Burst cooldown");

        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(
                    constellation,
                    ganyuAtConstellation(constellation).getConstellation(),
                    "Ganyu explicit constellation C" + constellation);
        }
    }

    private static void testNormalAndFrostflakeTimingA1AndC1() {
        Ganyu normalGanyu = ganyuAtConstellation(0);
        CombatSimulator normalSim = simulatorWith(normalGanyu);
        List<ActionRecord> normals = captureNamedActions(
                normalSim, "Liutian Archery N");
        double[] multipliers = {
                0.58302, 0.65412, 0.83582, 0.83582, 0.88638, 1.05860
        };
        int[] impactFrames = { 23, 24, 30, 36, 31, 32 };
        int[] durations = { 19, 27, 38, 37, 28, 59 };
        double castTime = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(normalSim, CharacterActionKey.NORMAL);
            castTime += durations[step] * FRAME;
        }
        assertEquals(6, normals.size(),
                "Ganyu all Normal projectiles resolve independently");
        castTime = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            ActionRecord record = normals.get(step);
            assertClose(multipliers[step],
                    record.action.getDamagePercent(), EPS,
                    "Ganyu Normal multiplier");
            assertClose(castTime + impactFrames[step] * FRAME,
                    record.time, EPS,
                    "Ganyu Normal release plus projectile travel");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Ganyu Normal category");
            assertClose(0.0, record.action.getGaugeUnits(), EPS,
                    "Ganyu Physical Normal gauge");
            castTime += durations[step] * FRAME;
        }
        perform(normalSim, CharacterActionKey.NORMAL);
        advanceTo(normalSim, normalSim.getCurrentTime() + 1.0);
        assertTrue(normals.get(6).action.getName().endsWith("N1"),
                "Ganyu Normal chain wraps after N6");

        Ganyu c0 = ganyuAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> frostflakes = captureNamedActions(
                c0Sim, "Liutian Archery Frostflake");
        perform(c0Sim, CharacterActionKey.CHARGE);
        assertEquals(2, frostflakes.size(),
                "Ganyu Frostflake resolves arrow and Bloom");
        assertClose(113.0 * FRAME, frostflakes.get(0).time, EPS,
                "Ganyu Frostflake release plus projectile travel");
        assertClose(131.0 * FRAME, frostflakes.get(1).time, EPS,
                "Ganyu Frostflake Bloom delay after impact");
        assertClose(2.1760,
                frostflakes.get(0).action.getDamagePercent(), EPS,
                "Ganyu Frostflake Arrow multiplier");
        assertClose(3.6992,
                frostflakes.get(1).action.getDamagePercent(), EPS,
                "Ganyu Frostflake Bloom multiplier");
        for (ActionRecord record : frostflakes) {
            assertEquals(ICDType.None, record.action.getICDType(),
                    "Ganyu Frostflake has no ICD");
            assertEquals(ICDTag.ChargedAttack, record.action.getICDTag(),
                    "Ganyu Frostflake typed ICD group");
            assertClose(1.0, record.action.getGaugeUnits(), EPS,
                    "Ganyu Frostflake applies 1U");
            assertTrue(record.action.isUseSnapshot(),
                    "Ganyu Frostflake uses release snapshot");
        }
        assertClose(0.0,
                c0.getSnapshot().get(StatType.CHARGED_ATTACK_CRIT_RATE),
                EPS, "Ganyu first shot excludes A1");
        perform(c0Sim, CharacterActionKey.CHARGE);
        assertClose(0.20,
                c0.getSnapshot().get(StatType.CHARGED_ATTACK_CRIT_RATE),
                EPS, "Ganyu second shot snapshots A1");

        Ganyu c1 = ganyuAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Frostflakes = captureNamedActions(
                c1Sim, "Liutian Archery Frostflake");
        perform(c1Sim, CharacterActionKey.CHARGE);
        assertClose(2.0, c1.getTotalFlatEnergy(), EPS,
                "Ganyu C1 grants Energy once per Frostflake shot");
        assertClose(0.15,
                applicableStats(c1Sim, c1).get(StatType.CRYO_RES_SHRED),
                EPS, "Ganyu C1 applies Cryo resistance reduction");
        assertClose(frostflakes.get(0).damage,
                c1Frostflakes.get(0).damage, EPS,
                "Ganyu first Arrow does not benefit from its own C1 shred");
        assertTrue(c1Frostflakes.get(1).damage > frostflakes.get(1).damage,
                "Ganyu Bloom benefits from C1 after the Arrow hit");
        advanceTo(c1Sim, 113.0 * FRAME + 6.0);
        assertClose(0.0,
                applicableStats(c1Sim, c1).get(StatType.CRYO_RES_SHRED),
                EPS, "Ganyu C1 expires on half-open boundary");
    }

    private static void testSkillTimingParticlesC2C5AndC6() {
        Ganyu c0 = ganyuAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> hits = captureNamedActions(
                c0Sim, "Trail of the Qilin");
        perform(c0Sim, CharacterActionKey.SKILL);
        assertEquals(1, hits.size(),
                "Ganyu Skill initial hit resolves during animation");
        assertClose(13.0 * FRAME, hits.get(0).time, EPS,
                "Ganyu Skill initial hitmark");
        assertClose(2.2440,
                hits.get(0).action.getDamagePercent(), EPS,
                "Ganyu C0 Skill multiplier");
        assertEquals(ICDType.None, hits.get(0).action.getICDType(),
                "Ganyu Skill has no ICD");
        assertTrue(hits.get(0).action.isUseSnapshot(),
                "Ganyu Skill snapshots at cast");
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Ganyu initial particles remain in flight");
        advanceTo(c0Sim, 113.0 * FRAME);
        assertClose(6.0, c0.getTotalParticleEnergy(), EPS,
                "Ganyu initial hit gives two Cryo particles");
        advanceTo(c0Sim, 373.0 * FRAME);
        assertEquals(2, hits.size(),
                "Ganyu delayed Skill explosion resolves");
        assertClose(373.0 * FRAME, hits.get(1).time, EPS,
                "Ganyu delayed Skill hitmark");
        advanceTo(c0Sim, 473.0 * FRAME + 1e-9);
        assertClose(12.0, c0.getTotalParticleEnergy(), EPS,
                "Ganyu delayed hit gives two Cryo particles");

        Ganyu c2 = ganyuAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Hits = captureNamedActions(
                c2Sim, "Trail of the Qilin");
        perform(c2Sim, CharacterActionKey.SKILL);
        perform(c2Sim, CharacterActionKey.SKILL);
        assertClose(56.0 * FRAME, c2Sim.getCurrentTime(), EPS,
                "Ganyu C2 consumes two Skill charges consecutively");
        perform(c2Sim, CharacterActionKey.SKILL);
        assertClose(10.0 + 28.0 * FRAME,
                c2Sim.getCurrentTime(), EPS,
                "Ganyu C2 third Skill waits for first charge restore");
        assertEquals(5, c2Hits.size(),
                "Ganyu C2 preserves both earlier delayed explosions");

        Ganyu c5 = ganyuAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Hits = captureNamedActions(
                c5Sim, "Trail of the Qilin");
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(2.6400,
                c5Hits.get(0).action.getDamagePercent(), EPS,
                "Ganyu C5 uses talent-12 Skill multiplier");

        Ganyu c6 = ganyuAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Shots = captureNamedActions(
                c6Sim, "Liutian Archery Frostflake Arrow");
        perform(c6Sim, CharacterActionKey.SKILL);
        double firstChargeCast = c6Sim.getCurrentTime();
        perform(c6Sim, CharacterActionKey.CHARGE);
        assertClose(firstChargeCast + 30.0 * FRAME,
                c6Shots.get(0).time, EPS,
                "Ganyu C6 shortened Frostflake includes projectile travel");
        double secondChargeCast = c6Sim.getCurrentTime();
        perform(c6Sim, CharacterActionKey.CHARGE);
        assertClose(secondChargeCast + 113.0 * FRAME,
                c6Shots.get(1).time, EPS,
                "Ganyu C6 grant is consumed once");
    }

    private static void testBurstCadenceSnapshotA4C3AndC4() {
        Ganyu c0 = ganyuAtConstellation(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.CRYO);
        CombatSimulator c0Sim = simulatorWith(c0);
        c0Sim.addCharacter(ally);
        List<ActionRecord> c0Shards = captureNamedActions(
                c0Sim, "Celestial Shower Shard");
        perform(c0Sim, CharacterActionKey.BURST);
        assertTrue(!c0.isBurstActive(c0Sim.getCurrentTime()),
                "Ganyu Burst field waits for frame 122");
        advanceTo(c0Sim, 122.0 * FRAME);
        assertTrue(c0.isBurstActive(c0Sim.getCurrentTime()),
                "Ganyu Burst field starts at frame 122");
        assertClose(0.20,
                applicableStats(c0Sim, c0).get(StatType.CRYO_DMG_BONUS),
                EPS, "Ganyu A4 buffs active Ganyu");
        c0Sim.setActiveCharacter(CharacterId.NOELLE);
        assertClose(0.20,
                applicableStats(c0Sim, ally).get(StatType.CRYO_DMG_BONUS),
                EPS, "Ganyu A4 follows the active Cryo ally");
        advanceTo(c0Sim, 958.0 * FRAME);
        assertEquals(10, c0Shards.size(),
                "Ganyu Burst deterministic guaranteed shard count");
        for (int shard = 0; shard < c0Shards.size(); shard++) {
            assertClose(
                    (148.0 + 90.0 * shard) * FRAME,
                    c0Shards.get(shard).time,
                    EPS,
                    "Ganyu Burst shard cadence");
            assertClose(1.194624,
                    c0Shards.get(shard).action.getDamagePercent(), EPS,
                    "Ganyu C0 Burst multiplier");
            assertEquals(ICDType.Standard,
                    c0Shards.get(shard).action.getICDType(),
                    "Ganyu Burst standard ICD");
            assertTrue(c0Shards.get(shard).action.isUseSnapshot(),
                    "Ganyu Burst uses cast snapshot");
        }
        advanceTo(c0Sim, 1022.0 * FRAME);
        assertTrue(!c0.isBurstActive(1022.0 * FRAME),
                "Ganyu Burst field expires 15 seconds after frame 122");
        assertClose(0.0,
                applicableStats(c0Sim, ally).get(StatType.CRYO_DMG_BONUS),
                EPS, "Ganyu A4 expires with Burst field");

        Ganyu c4 = ganyuAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        perform(c4Sim, CharacterActionKey.BURST);
        advanceTo(c4Sim, 122.0 * FRAME);
        assertClose(0.05,
                applicableStats(c4Sim, c4).get(StatType.DMG_BONUS_ALL),
                EPS, "Ganyu C4 starts at five percent");
        advanceTo(c4Sim, 122.0 * FRAME + 3.0);
        assertClose(0.10,
                applicableStats(c4Sim, c4).get(StatType.DMG_BONUS_ALL),
                EPS, "Ganyu C4 ramps at three seconds");
        advanceTo(c4Sim, 122.0 * FRAME + 12.0);
        assertClose(0.25,
                applicableStats(c4Sim, c4).get(StatType.DMG_BONUS_ALL),
                EPS, "Ganyu C4 reaches five stacks");
        advanceTo(c4Sim, 1022.0 * FRAME);
        assertClose(0.25,
                applicableStats(c4Sim, c4).get(StatType.DMG_BONUS_ALL),
                EPS, "Ganyu C4 lingers after the field expires");
        advanceTo(c4Sim, 1202.0 * FRAME);
        assertClose(0.0,
                applicableStats(c4Sim, c4).get(StatType.DMG_BONUS_ALL),
                EPS, "Ganyu C4 linger expires after three seconds");

        Ganyu c3 = ganyuAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Shards = captureNamedActions(
                c3Sim, "Celestial Shower Shard");
        perform(c3Sim, CharacterActionKey.BURST);
        advanceTo(c3Sim, 148.0 * FRAME);
        assertClose(1.40544,
                c3Shards.get(0).action.getDamagePercent(), EPS,
                "Ganyu C3 uses talent-12 Burst multiplier");
    }

    private static void testCooldownEnergyIsolationAndInvalidInputs() {
        Ganyu insufficient = ganyuAtConstellation(0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        List<ActionRecord> burstHits = captureNamedActions(
                insufficientSim, "Celestial Shower");
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertEquals(0, burstHits.size(),
                "Ganyu Burst rejects insufficient Energy");
        assertClose(0.0, insufficientSim.getCurrentTime(), EPS,
                "Ganyu rejected Burst does not advance time");
        assertClose(60.0, insufficient.getMissedBurstCost(), EPS,
                "Ganyu rejected Burst records missed cost");

        Ganyu first = ganyuAtConstellation(0);
        Ganyu second = ganyuAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        List<ActionRecord> firstHits = captureNamedActions(
                firstSim, "Celestial Shower Shard");
        List<ActionRecord> secondHits = captureNamedActions(
                secondSim, "Celestial Shower Shard");
        perform(firstSim, CharacterActionKey.BURST);
        perform(secondSim, CharacterActionKey.BURST);
        advanceTo(firstSim, 148.0 * FRAME);
        assertEquals(1, firstHits.size(),
                "Ganyu first instance owns its timers");
        assertEquals(0, secondHits.size(),
                "Ganyu second instance time is independent");
        advanceTo(secondSim, 148.0 * FRAME);
        assertEquals(1, secondHits.size(),
                "Ganyu second instance resolves independently");

        assertThrows(IllegalArgumentException.class,
                () -> ganyuAtConstellation(-1),
                "Ganyu rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> ganyuAtConstellation(7),
                "Ganyu rejects constellation above six");
        List<ActionRecord> plunges = captureNamedActions(
                firstSim, "Liutian Archery High Plunge");
        perform(firstSim, CharacterActionKey.PLUNGE);
        assertEquals(1, plunges.size(), "Ganyu high Plunge hit count");
        assertClose(2.6076,
                plunges.get(0).action.getDamagePercent(), EPS,
                "Ganyu high Plunge multiplier");
        assertThrows(IllegalArgumentException.class,
                () -> perform(firstSim, CharacterActionKey.DASH),
                "Ganyu rejects unsupported Dash");

        first.initializeForSimulator(firstSim);
        CombatSimulator rejected = new CombatSimulator();
        rejected.setLoggingEnabled(false);
        rejected.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> rejected.addCharacter(first),
                "Ganyu rejects cross-simulator reuse");
    }

    private static Ganyu ganyuAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Ganyu(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(Ganyu ganyu) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(ganyu);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.GANYU,
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
            if (actor.getCharacterId() == CharacterId.GANYU
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

    /** Minimal Cryo ally used to verify active-character field targeting. */
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
