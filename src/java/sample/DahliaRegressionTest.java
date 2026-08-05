package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.reaction.ReactionResult;
import model.character.Dahlia;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
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

/** Focused regression checks for Dahlia's Favonian Favor support slice. */
public final class DahliaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private DahliaRegressionTest() {
    }

    /** Runs identity, action, support, boundary, rollback, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicAttackStringAndTiming();
        testPressSkillParticlesAndRollback();
        testBurstFavorAndConstellations();
        testNormalBenisonC1AndRollback();
        testZeroDamageNormalHitsCount();
        testFrozenA1GateAndCap();
        testFavorExpirationBoundary();
        testGuardsAndIsolation();
        System.out.println("DahliaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Dahlia dahlia = new Dahlia(null, null, 6);
        assertEquals(CharacterId.DAHLIA, dahlia.getCharacterId(),
                "Dahlia typed identity");
        assertEquals(CharacterId.DAHLIA,
                CharacterId.fromName("Dahlia"),
                "Dahlia name lookup");
        assertEquals(CharacterId.DAHLIA,
                CharacterId.fromNumericId(87),
                "Dahlia numeric lookup");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.DAHLIA.getRegion(),
                "Dahlia region");
        assertEquals(Element.HYDRO, dahlia.getElement(),
                "Dahlia element");
        assertClose(12506.0,
                dahlia.getBaseStats().get(StatType.BASE_HP),
                "Dahlia base HP");
        assertClose(189.0,
                dahlia.getBaseStats().get(StatType.BASE_ATK),
                "Dahlia base ATK");
        assertClose(560.0,
                dahlia.getBaseStats().get(StatType.BASE_DEF),
                "Dahlia base DEF");
        assertClose(0.24,
                dahlia.getBaseStats().get(StatType.HP_PERCENT),
                "Dahlia ascension HP");
        assertClose(60.0, dahlia.getEnergyCost(),
                "Dahlia Energy cost");
        assertClose(9.0, dahlia.getSkillCD(),
                "Dahlia Skill cooldown");
        assertClose(15.0, dahlia.getBurstCD(),
                "Dahlia Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.DAHLIA,
                    new Dahlia(null, null, constellation)
                            .getCharacterId(),
                    "Dahlia explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Dahlia/Dahlia_Status.csv"), 24);
        assertCsvShape(Path.of(
                "config/characters/Dahlia/Dahlia_Multipliers.csv"), 12);
        assertCsvValue("N3 Hit 2", 0.533092);
        assertCsvValue("Immersive Ordinance C5", 4.656000);
        assertCsvValue("Radiant Psalter C3", 8.128000);
    }

    private static void testBasicAttackStringAndTiming() {
        Dahlia dahlia = new Dahlia(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(dahlia, ally);
        List<ActionRecord> records = captureActions(simulator);

        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
        }
        double[] expectedMultipliers = {
            0.800049, 0.736722, 0.436238, 0.533092, 1.206267
        };
        double[] expectedTimes = {
            15.0 * FRAME,
            45.0 * FRAME,
            80.0 * FRAME,
            81.0 * FRAME,
            134.0 * FRAME
        };
        for (int index = 0; index < expectedMultipliers.length; index++) {
            ActionRecord record = records.get(index);
            assertClose(expectedMultipliers[index],
                    record.action.getDamagePercent(),
                    "Dahlia Normal multiplier " + index);
            assertClose(expectedTimes[index], record.time,
                    "Dahlia Normal hitmark " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Dahlia Normal action type");
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Dahlia Normal element");
        }

        double chargeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        List<ActionRecord> charged = named(records, "Charged Hit");
        assertEquals(2, charged.size(),
                "Dahlia Charged hit count");
        assertClose(chargeStart + 10.0 * FRAME,
                charged.get(0).time,
                "Dahlia Charged first hitmark");
        assertClose(charged.get(0).time, charged.get(1).time,
                "Dahlia Charged hits are simultaneous");
        assertClose(0.732614,
                charged.get(0).action.getDamagePercent(),
                "Dahlia Charged first multiplier");
        assertClose(1.011706,
                charged.get(1).action.getDamagePercent(),
                "Dahlia Charged second multiplier");

        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.PLUNGE));
        ActionRecord plunge = named(records, "High Plunge").get(0);
        assertClose(plungeStart + 39.0 * FRAME, plunge.time,
                "Dahlia high Plunge hitmark");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Dahlia high Plunge multiplier");
        assertTrue(plunge.action.isShatterTrigger(),
                "Dahlia high Plunge is blunt for Shatter");

        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.DAHLIA);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertTrue(named(records, " Ritual N1").size() >= 3,
                "Dahlia switch-out resets the Normal string");
    }

    private static void testPressSkillParticlesAndRollback() {
        Dahlia dahlia = new Dahlia(null, null, 0);
        CombatSimulator simulator = simulatorWith(dahlia);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));

        ActionRecord skill = named(records, "Immersive Ordinance").get(0);
        assertClose(27.0 * FRAME, skill.time,
                "Dahlia Press Skill hitmark");
        assertClose(3.957600, skill.action.getDamagePercent(),
                "Dahlia C0 Skill Talent 9");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Dahlia Skill has no ICD");
        assertEquals(ICDTag.None, skill.action.getICDTag(),
                "Dahlia Skill uses the no-ICD tag");
        assertClose(1.0, skill.action.getGaugeUnits(),
                "Dahlia Skill gauge");
        assertClose(8.5,
                dahlia.getSkillCDRemaining(simulator.getCurrentTime()),
                "Dahlia Skill cooldown starts at frame 23");

        SimulatorSnapshot pendingParticle = simulator.saveSnapshot();
        advanceTo(simulator, 3.0);
        assertEquals(1, particles.size(),
                "Dahlia Skill emits one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Dahlia Skill emits three Hydro particles");
        assertClose(127.0 * FRAME, particles.get(0).time,
                "Dahlia particle travel timing");
        simulator.restoreSnapshot(pendingParticle);
        advanceTo(simulator, 3.0);
        assertEquals(2, particles.size(),
                "Dahlia restores pending particles exactly once");
    }

    private static void testBurstFavorAndConstellations() {
        Dahlia c0 = new Dahlia(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        ActionRecord c0Burst = named(c0Records, "Radiant Psalter").get(0);
        assertClose(30.0 * FRAME, c0Burst.time,
                "Dahlia Burst hitmark");
        assertClose(6.908800, c0Burst.action.getDamagePercent(),
                "Dahlia C0 Burst Talent 9");
        assertClose(2.0, c0Burst.action.getGaugeUnits(),
                "Dahlia Burst gauge");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Dahlia Burst spends Energy at frame 8");
        assertClose(15.0 - 55.0 * FRAME,
                c0.getBurstCDRemaining(c0Simulator.getCurrentTime()),
                "Dahlia Burst cooldown starts on cast");
        assertTrue(c0.isFavonianFavorActive(
                c0Simulator.getCurrentTime()),
                "Dahlia Favor starts at frame 31");

        Dahlia c6 = new Dahlia(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c6Simulator = simulatorWith(c6, ally);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        perform(c6Simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        assertClose(4.656000,
                named(c6Records, "Immersive Ordinance").get(0)
                        .action.getDamagePercent(),
                "Dahlia C5 Skill Talent 12");
        assertClose(8.128000,
                named(c6Records, "Radiant Psalter").get(0)
                        .action.getDamagePercent(),
                "Dahlia C3 Burst Talent 12");
        double expectedSpeed = 12506.0 * 1.24 * 0.000005 + 0.10;
        assertClose(expectedSpeed,
                effectiveStats(c6Simulator, ally).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Dahlia A4 and C6 team Normal speed");
        assertEquals(1, countBuffs(c6Simulator, ally,
                BuffId.DAHLIA_FAVONIAN_FAVOR_ATTACK_SPEED),
                "Dahlia Favor uses one typed no-stack buff");
        advanceTo(c6Simulator,
                c6Simulator.getCurrentTime() + 1.1);
        assertEquals(1, countBuffs(c6Simulator, ally,
                BuffId.DAHLIA_FAVONIAN_FAVOR_ATTACK_SPEED),
                "Dahlia speed refresh replaces instead of stacking");
    }

    private static void testNormalBenisonC1AndRollback() {
        Dahlia dahlia = new Dahlia(null, null, 1);
        CombatSimulator simulator = simulatorWith(dahlia);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        for (int step = 0; step < 3; step++) {
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
        }
        assertEquals(0, dahlia.getBenisonStacks(),
                "Dahlia N3 double hit respects the 0.05-second gate");
        SimulatorSnapshot beforeFourthHit = simulator.saveSnapshot();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertEquals(1, dahlia.getBenisonStacks(),
                "Dahlia four eligible Normal hits grant Benison");
        assertClose(2.5, dahlia.getCurrentEnergy(),
                "Dahlia C1 restores Energy per generated stack");

        simulator.restoreSnapshot(beforeFourthHit);
        assertEquals(0, dahlia.getBenisonStacks(),
                "Dahlia rollback restores Benison");
        assertClose(0.0, dahlia.getCurrentEnergy(),
                "Dahlia rollback restores C1 Energy");
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertEquals(1, dahlia.getBenisonStacks(),
                "Dahlia restored Normal state generates once");
        assertClose(2.5, dahlia.getCurrentEnergy(),
                "Dahlia restored C1 Energy generates once");
    }

    private static void testFrozenA1GateAndCap() {
        Dahlia dahlia = new Dahlia(null, null, 1);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.CRYO);
        CombatSimulator simulator = simulatorWith(dahlia, ally);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));

        simulator.notifyReaction(ReactionResult.none(), ally);
        assertEquals(0, dahlia.getBenisonStacks(),
                "Dahlia A1 rejects NONE reactions");
        TestCharacter external = new TestCharacter(
                CharacterId.AMBER, Element.PYRO);
        simulator.notifyReaction(frozen(), external);
        assertEquals(0, dahlia.getBenisonStacks(),
                "Dahlia A1 rejects non-party triggers");

        simulator.notifyReaction(frozen(), ally);
        assertEquals(2, dahlia.getBenisonStacks(),
                "Dahlia A1 grants two Benison on Frozen");
        assertClose(5.0, dahlia.getCurrentEnergy(),
                "Dahlia C1 restores Energy for A1 stacks");
        simulator.notifyReaction(frozen(), ally);
        assertEquals(2, dahlia.getBenisonStacks(),
                "Dahlia A1 blocks Frozen inside eight seconds");

        simulator.advanceTime(8.0);
        simulator.notifyReaction(frozen(), ally);
        assertEquals(4, dahlia.getBenisonStacks(),
                "Dahlia A1 allows Frozen at the exact cooldown boundary");
        assertEquals(4, dahlia.getGeneratedBenisonStacks(),
                "Dahlia Benison generation reaches the four-stack cap");
        assertClose(10.0, dahlia.getCurrentEnergy(),
                "Dahlia C1 Energy follows the four-stack cap");

        simulator.advanceTime(8.0);
        simulator.notifyReaction(frozen(), ally);
        assertEquals(4, dahlia.getBenisonStacks(),
                "Dahlia A1 fails closed above the generation cap");
        assertClose(10.0, dahlia.getCurrentEnergy(),
                "Dahlia C1 grants no Energy above the cap");
    }

    private static void testZeroDamageNormalHitsCount() {
        Dahlia dahlia = new Dahlia(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(dahlia, ally);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        simulator.switchCharacter(CharacterId.NOELLE);
        for (int hit = 0; hit < 4; hit++) {
            AttackAction zeroDamageNormal = new AttackAction(
                    "Zero-damage Normal hit " + hit,
                    0.0,
                    Element.PHYSICAL,
                    StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    0.0,
                    ActionType.NORMAL);
            zeroDamageNormal.setICD(
                    ICDType.Standard, ICDTag.NormalAttack, 0.0);
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.NOELLE, zeroDamageNormal);
            simulator.advanceTime(0.05);
        }
        assertEquals(1, dahlia.getBenisonStacks(),
                "Dahlia counts accepted Normal hits independent of damage");
    }

    private static void testFavorExpirationBoundary() {
        Dahlia dahlia = new Dahlia(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(dahlia, ally);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        double expiration = 31.0 * FRAME + 15.0;
        advanceTo(simulator, expiration - 2.0 * EPSILON);
        assertTrue(dahlia.isFavonianFavorActive(
                simulator.getCurrentTime()),
                "Dahlia C4 Favor is active before 15-second expiry");
        assertTrue(effectiveStats(simulator, ally).get(
                StatType.NORMAL_ATTACK_SPD) > 0.0,
                "Dahlia A4 speed is active before expiry");
        advanceTo(simulator, expiration);
        assertTrue(!dahlia.isFavonianFavorActive(
                simulator.getCurrentTime()),
                "Dahlia C4 Favor expires at the exact boundary");
        assertClose(0.0,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Dahlia A4 speed expires at the exact boundary");
    }

    private static void testGuardsAndIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Dahlia(null, null, -1),
                "Dahlia rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Dahlia(null, null, 7),
                "Dahlia rejects constellation above C6");

        Dahlia dahlia = new Dahlia(null, null, 0);
        CombatSimulator simulator = simulatorWith(dahlia);
        assertThrows(IllegalArgumentException.class,
                () -> dahlia.onAction(null, simulator),
                "Dahlia rejects null actions");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.skill(
                        SkillActionMode.HOLD)),
                "Dahlia rejects excluded Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.of(
                        CharacterActionKey.DASH)),
                "Dahlia rejects unsupported movement actions");

        Dahlia external = new Dahlia(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Dahlia rejects binding outside the party");
        Dahlia reused = new Dahlia(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Dahlia rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!dahlia.acceptsCharacterState(foreignState),
                "Dahlia rejects another instance snapshot payload");

        Dahlia first = new Dahlia(null, null, 1);
        Dahlia second = new Dahlia(null, null, 1);
        CombatSimulator firstSimulator = simulatorWith(first);
        CombatSimulator secondSimulator = simulatorWith(second);
        perform(firstSimulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        firstSimulator.notifyReaction(frozen(), first);
        assertEquals(2, first.getBenisonStacks(),
                "Dahlia first instance owns its Benison");
        assertEquals(0, second.getBenisonStacks(),
                "Dahlia second instance remains independent");
        assertTrue(!second.isFavonianFavorActive(
                secondSimulator.getCurrentTime()),
                "Dahlia Favor does not leak between instances");
    }

    private static ReactionResult frozen() {
        return ReactionResult.state(
                "Frozen", ReactionResult.Kind.FROZEN, Element.CRYO);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionRequest request) {
        simulator.performAction(CharacterId.DAHLIA, request);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DAHLIA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureHydroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String fragment) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().contains(fragment)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static StatsContainer effectiveStats(
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

    private static int countBuffs(
            CombatSimulator simulator,
            Character character,
            BuffId id) {
        int count = 0;
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (buff.getId() == id
                    && !buff.isExpired(simulator.getCurrentTime())) {
                count++;
            }
        }
        return count;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(
                targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(
            Path path,
            int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Dahlia,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Dahlia/Dahlia_Status.csv",
                "config/characters/Dahlia/Dahlia_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected,
                            Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Dahlia CSVs missing key " + key);
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + error.getClass().getSimpleName(), error);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected "
                    + expected + " but got " + actual);
        }
    }

    private static void assertTrue(
            boolean condition,
            String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected "
                    + expected + " but got " + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public void onAction(
                CharacterActionRequest request,
                CombatSimulator simulator) {
            throw new UnsupportedOperationException(
                    "TestCharacter has no actions");
        }
    }
}
