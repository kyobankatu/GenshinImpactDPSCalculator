package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Collei;
import model.entity.Character;
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

/** Focused regression checks for Collei's reaction character slice. */
public final class ColleiRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ColleiRegressionTest() {
    }

    /** Runs data, timing, reaction, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstellations();
        testBasicAttackCategoriesAndSwitchReset();
        testSkillTimingSnapshotParticlesAndC6();
        testBurstCadenceGaugeEnergyAndTalentLevels();
        testSproutAndFinalTickExtensions();
        testC1AndC4Windows();
        testRepeatedMidEventRestore();
        testInvalidInputsAndReuse();
        System.out.println("ColleiRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstellations()
            throws IOException {
        Collei collei = new Collei(null, null);
        assertEquals(CharacterId.COLLEI, collei.getCharacterId(),
                "Collei typed identity");
        assertEquals(Element.DENDRO, collei.getElement(), "Collei element");
        assertClose(9787.0,
                collei.getBaseStats().get(StatType.BASE_HP),
                "Collei base HP");
        assertClose(200.0,
                collei.getBaseStats().get(StatType.BASE_ATK),
                "Collei base ATK");
        assertClose(601.0,
                collei.getBaseStats().get(StatType.BASE_DEF),
                "Collei base DEF");
        assertClose(0.24,
                collei.getBaseStats().get(StatType.ATK_PERCENT),
                "Collei ascension ATK");
        assertClose(60.0, collei.getEnergyCost(), "Collei Energy cost");
        assertClose(12.0, collei.getSkillCD(), "Collei Skill cooldown");
        assertClose(15.0, collei.getBurstCD(), "Collei Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.COLLEI,
                    new Collei(null, null, constellation).getCharacterId(),
                    "Collei explicit constellation " + constellation);
        }
        assertCsvShape(Paths.get(
                "config/characters/Collei/Collei_Status.csv"), 10);
        assertCsvShape(Paths.get(
                "config/characters/Collei/Collei_Multipliers.csv"), 16);
    }

    private static void testBasicAttackCategoriesAndSwitchReset() {
        Collei collei = new Collei(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(collei, ally);
        List<ActionRecord> records = captureColleiActions(simulator);
        double[] multipliers = { 0.80106, 0.78368, 0.99382, 1.24978 };
        double[] times = { 26.0, 48.0, 81.0, 134.0 };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(4, records.size(), "Collei four-shot hit count");
        for (int step = 0; step < 4; step++) {
            assertClose(multipliers[step],
                    records.get(step).action.getDamagePercent(),
                    "Collei N" + (step + 1) + " multiplier");
            assertClose(times[step] * FRAME, records.get(step).time,
                    "Collei N" + (step + 1) + " impact time");
            assertEquals(ActionType.NORMAL,
                    records.get(step).action.getActionType(),
                    "Collei Normal category");
        }

        records.clear();
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.COLLEI);
        perform(simulator, CharacterActionKey.NORMAL);
        simulator.advanceTime(3.0 * FRAME);
        assertTrue(records.get(0).action.getName().endsWith("N1"),
                "Collei switch-out resets Normal chain");

        Collei charged = new Collei(null, null, 0);
        CombatSimulator chargedSim = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureColleiActions(chargedSim);
        perform(chargedSim, CharacterActionKey.CHARGE);
        AttackAction aimed = chargedRecords.get(0).action;
        assertClose(2.108, aimed.getDamagePercent(),
                "Collei fully charged multiplier");
        assertEquals(Element.DENDRO, aimed.getElement(),
                "Collei fully charged element");
        assertEquals(ActionType.CHARGE, aimed.getActionType(),
                "Collei fully charged category");
        assertClose(1.0, aimed.getGaugeUnits(),
                "Collei fully charged gauge");
        chargedRecords.clear();
        perform(chargedSim, CharacterActionKey.PLUNGE);
        assertEquals(ActionType.PLUNGE,
                chargedRecords.get(0).action.getActionType(),
                "Collei high Plunge category");
        assertEquals(Element.PHYSICAL,
                chargedRecords.get(0).action.getElement(),
                "Collei high Plunge element");
    }

    private static void testSkillTimingSnapshotParticlesAndC6() {
        Collei c6 = new Collei(null, null, 6);
        c6.addBuff(new TestBuff(
                20.0 * FRAME, StatType.ATK_PERCENT, 1.0));
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureColleiActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 3.0);
        List<ActionRecord> skill = named(records, "Floral Brush");
        assertEquals(2, skill.size(), "Collei Skill two passes");
        assertClose(34.0 * FRAME, skill.get(0).time,
                "Collei Skill outbound hitmark");
        assertClose(138.0 * FRAME, skill.get(1).time,
                "Collei Skill return hitmark");
        for (ActionRecord record : skill) {
            assertClose(3.024, record.action.getDamagePercent(),
                    "Collei C3 Skill multiplier");
            assertEquals(ICDType.None, record.action.getICDType(),
                    "Collei Skill has no ICD");
            assertClose(1.24,
                    record.action.getStatSnapshot().get(
                            StatType.ATK_PERCENT),
                    "Collei Skill retains cast snapshot");
        }
        assertEquals(1, named(records, "Forest of Falling Arrows").size(),
                "Collei C6 triggers once per Skill cast");
        assertEquals(ActionType.OTHER,
                named(records, "Forest of Falling Arrows")
                        .get(0).action.getActionType(),
                "Collei C6 is OTHER damage");
        assertEquals(1, particles.size(), "Collei one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Collei deterministic particle count");
        assertClose(134.0 * FRAME, particles.get(0).time,
                "Collei particle arrival");
        assertClose(20.0 * FRAME, c6.getLastSkillTime(),
                "Collei Skill cooldown starts at frame 20");

        Collei c5 = new Collei(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureColleiActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        advanceTo(c5Sim, 3.0);
        assertEquals(0,
                named(c5Records, "Forest of Falling Arrows").size(),
                "Collei C6 does not leak into C5");
    }

    private static void testBurstCadenceGaugeEnergyAndTalentLevels() {
        Collei c5 = new Collei(null, null, 5);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureColleiActions(simulator);
        double[] energyAt = new double[2];
        observeEnergy(simulator, c5, 6.0 * FRAME, energyAt, 0);
        observeEnergy(simulator, c5, 7.0 * FRAME + EPSILON, energyAt, 1);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 7.0);
        List<ActionRecord> explosions = named(
                records, "Trump-Card Kitty Explosion");
        List<ActionRecord> leaps = named(records, "Trump-Card Kitty Leap");
        assertEquals(1, explosions.size(), "Collei Burst explosion count");
        assertEquals(12, leaps.size(), "Collei base Burst leap count");
        assertClose(25.0 * FRAME, explosions.get(0).time,
                "Collei Burst explosion frame");
        assertClose(4.03648,
                explosions.get(0).action.getDamagePercent(),
                "Collei C5 explosion multiplier");
        assertClose(0.86496, leaps.get(0).action.getDamagePercent(),
                "Collei C5 leap multiplier");
        assertClose(68.0 * FRAME, leaps.get(0).time,
                "Collei first leap frame");
        assertClose(398.0 * FRAME, leaps.get(11).time,
                "Collei final base leap frame");
        assertClose(1.0, explosions.get(0).action.getGaugeUnits(),
                "Collei explosion applies Dendro");
        for (int leap = 0; leap < leaps.size(); leap++) {
            double expectedGauge = leap == 5 || leap == 11 ? 1.0 : 0.0;
            assertClose(expectedGauge,
                    leaps.get(leap).action.getGaugeUnits(),
                    "Collei shared Burst application leap " + leap);
        }
        assertClose(218.0 * FRAME, leaps.get(5).time,
                "Collei second Burst Dendro application");
        assertClose(60.0, energyAt[0],
                "Collei retains Energy through frame 6");
        assertClose(0.0, energyAt[1],
                "Collei spends Energy at frame 7");
        assertClose(0.0, c5.getLastBurstTime(),
                "Collei Burst cooldown starts at cast");
    }

    private static void testSproutAndFinalTickExtensions() {
        Collei c0 = new Collei(null, null, 0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Records = captureColleiActions(c0Sim);
        perform(c0Sim, CharacterActionKey.SKILL);
        c0.onReaction(dendroReaction(), c0,
                c0Sim.getCurrentTime(), c0Sim);
        advanceTo(c0Sim, 6.0);
        List<ActionRecord> c0Sprout = named(
                c0Records, "Floral Sidewinder Sprout");
        assertEquals(2, c0Sprout.size(), "Collei A1 two Sprout ticks");
        assertClose(1.0, c0Sprout.get(0).action.getGaugeUnits(),
                "Collei first Sprout applies Dendro");
        assertClose(0.0, c0Sprout.get(1).action.getGaugeUnits(),
                "Collei second Sprout shares application cadence");

        Collei c2 = new Collei(null, null, 2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Records = captureColleiActions(c2Sim);
        c2Sim.addDamageListener((actor, action, damage, time) -> {
            if (action.getName().equals("Floral Sidewinder Sprout")
                    && named(c2Records,
                            "Floral Sidewinder Sprout").size() == 2) {
                c2.onReaction(dendroReaction(), c2, time, c2Sim);
            }
        });
        perform(c2Sim, CharacterActionKey.SKILL);
        advanceTo(c2Sim, 9.0);
        assertEquals(4,
                named(c2Records, "Floral Sidewinder Sprout").size(),
                "Collei C2 final base tick can extend Sprout");

        Collei a4 = new Collei(null, null, 0);
        CombatSimulator a4Sim = simulatorWith(a4);
        List<ActionRecord> a4Records = captureColleiActions(a4Sim);
        a4Sim.addDamageListener((actor, action, damage, time) -> {
            if (action.getName().equals("Trump-Card Kitty Leap")
                    && Math.abs(time - 398.0 * FRAME) < EPSILON) {
                a4.onReaction(dendroReaction(), a4, time, a4Sim);
                a4.onReaction(dendroReaction(), a4, time, a4Sim);
                a4.onReaction(dendroReaction(), a4, time, a4Sim);
                a4.onReaction(dendroReaction(), a4, time, a4Sim);
            }
        });
        perform(a4Sim, CharacterActionKey.BURST);
        advanceTo(a4Sim, 10.0);
        assertEquals(18,
                named(a4Records, "Trump-Card Kitty Leap").size(),
                "Collei final base leap can reach three-second A4 cap");
    }

    private static void testC1AndC4Windows() {
        Collei collei = new Collei(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(collei, ally);
        assertClose(1.0,
                collei.getEffectiveStats(0.0)
                        .get(StatType.ENERGY_RECHARGE),
                "Collei C1 inactive on-field");
        simulator.switchCharacter(CharacterId.NOELLE);
        assertClose(1.20,
                collei.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ENERGY_RECHARGE),
                "Collei C1 active off-field");
        simulator.switchCharacter(CharacterId.COLLEI);
        double burstCastTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(60.0,
                applicableStats(simulator, ally)
                        .get(StatType.ELEMENTAL_MASTERY),
                "Collei C4 grants ally EM");
        assertClose(0.0,
                applicableStats(simulator, collei)
                        .get(StatType.ELEMENTAL_MASTERY),
                "Collei C4 excludes Collei");
        advanceTo(simulator, burstCastTime + 12.0);
        assertClose(0.0,
                applicableStats(simulator, ally)
                        .get(StatType.ELEMENTAL_MASTERY),
                "Collei C4 expires at twelve seconds");
    }

    private static void testRepeatedMidEventRestore() {
        Collei skillCollei = new Collei(null, null, 2);
        CombatSimulator skillSim = simulatorWith(skillCollei);
        List<ActionRecord> skillRecords = captureColleiActions(skillSim);
        List<ParticleRecord> particles = captureParticles(skillSim);
        perform(skillSim, CharacterActionKey.SKILL);
        SimulatorSnapshot skillSnapshot = skillSim.saveSnapshot();
        advanceTo(skillSim, 6.0);
        int expectedHits = skillRecords.size();
        int expectedParticles = particles.size();
        skillSim.restoreSnapshot(skillSnapshot);
        skillSim.restoreSnapshot(skillSnapshot);
        skillRecords.clear();
        particles.clear();
        advanceTo(skillSim, 6.0);
        assertEquals(expectedHits - 1, skillRecords.size(),
                "Collei repeated Skill restore replays only future hits");
        assertEquals(expectedParticles, particles.size(),
                "Collei repeated Skill restore replays one particle packet");

        Collei burstCollei = new Collei(null, null, 0);
        CombatSimulator burstSim = simulatorWith(burstCollei);
        List<ActionRecord> burstRecords = captureColleiActions(burstSim);
        perform(burstSim, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSim.saveSnapshot();
        advanceTo(burstSim, 7.0);
        int expectedLeaps = named(
                burstRecords, "Trump-Card Kitty Leap").size();
        burstSim.restoreSnapshot(burstSnapshot);
        burstSim.restoreSnapshot(burstSnapshot);
        burstRecords.clear();
        advanceTo(burstSim, 7.0);
        assertEquals(expectedLeaps,
                named(burstRecords, "Trump-Card Kitty Leap").size(),
                "Collei repeated Burst restore keeps each leap once");
    }

    private static void testInvalidInputsAndReuse() {
        assertThrows(IllegalArgumentException.class,
                () -> new Collei(null, null, -1),
                "Collei rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Collei(null, null, 7),
                "Collei rejects constellation above six");
        Collei collei = new Collei(null, null, 0);
        CombatSimulator simulator = simulatorWith(collei);
        assertThrows(IllegalArgumentException.class,
                () -> collei.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, simulator),
                "Collei rejects foreign snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> collei.initializeForSimulator(new CombatSimulator()),
                "Collei rejects simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> collei.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH),
                        simulator),
                "Collei rejects unsupported Dash");
        collei.restoreCurrentEnergy(0.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(60.0, collei.getMissedBurstCost(),
                "Collei rejects Burst with insufficient Energy");
        double before = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(before + 20.0 * FRAME + 12.0 + 68.0 * FRAME,
                simulator.getCurrentTime(),
                "Collei serializes Skill recast after cooldown");
        collei.onReaction(ReactionResult.none(), collei,
                simulator.getCurrentTime(), simulator);
    }

    private static ReactionResult dendroReaction() {
        return ReactionResult.state(
                "Quicken", ReactionResult.Kind.QUICKEN, Element.DENDRO);
    }

    private static StatsContainer applicableStats(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.COLLEI, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureColleiActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.COLLEI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static void observeEnergy(
            CombatSimulator simulator,
            Collei collei,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                values[index] = collei.getCurrentEnergy();
            }
        });
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
            assertTrue(lines.get(index).startsWith("Collei,"),
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
            super("Collei regression cast buff", duration, 0.0);
            this.stat = stat;
            this.amount = amount;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(stat, amount);
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
