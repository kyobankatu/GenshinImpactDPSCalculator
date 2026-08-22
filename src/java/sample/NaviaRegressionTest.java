package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.reaction.ReactionResult;
import model.character.Navia;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused checks for Navia's represented Crystal Shrapnel slice. */
public final class NaviaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private NaviaRegressionTest() {
    }

    /** Runs identity, acquisition, action, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalsPlungeAndA4();
        testShrapnelAcquisitionAndPressSkill();
        testConstellationsParticlesAndInfusion();
        testBurstCadenceAndC4();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("NaviaRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        Navia navia = navia(0, 0.75);
        assertEquals(CharacterId.NAVIA, navia.getCharacterId(),
                "Navia typed identity");
        assertEquals(CharacterId.NAVIA,
                CharacterId.fromNumericId(77),
                "Navia numeric identity");
        assertEquals(CharacterId.NAVIA,
                CharacterId.fromName("Navia"),
                "Navia display-name identity");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.NAVIA.getRegion(), "Navia region");
        assertEquals(Element.GEO, navia.getElement(), "Navia element");
        assertClose(12650.0,
                navia.getBaseStats().get(StatType.BASE_HP),
                "Navia base HP");
        assertClose(352.0,
                navia.getBaseStats().get(StatType.BASE_ATK),
                "Navia base ATK");
        assertClose(793.0,
                navia.getBaseStats().get(StatType.BASE_DEF),
                "Navia base DEF");
        assertClose(0.884,
                navia.getBaseStats().get(StatType.CRIT_DMG),
                "Navia ascension CRIT DMG");
        assertClose(60.0, navia.getEnergyCost(), "Navia Energy cost");
        assertClose(9.0, navia.getSkillCD(), "Navia Skill cooldown");
        assertClose(15.0, navia.getBurstCD(), "Navia Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    navia(constellation, 0.75).getConstellation(),
                    "Navia constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Navia/Navia_Status.csv"), 31);
        assertCsvShape(Path.of(
                "config/characters/Navia/Navia_Multipliers.csv"), 15);
        assertThrows(IllegalArgumentException.class,
                () -> navia(-1, 0.75),
                "Navia rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> navia(7, 0.75),
                "Navia rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Navia(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Navia rejects null particle source");
        CombatSimulator simulator = simulatorWith(navia);
        assertThrows(IllegalArgumentException.class,
                () -> navia.onAction(null, simulator),
                "Navia rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(
                        simulator,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Navia rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Navia rejects unpinned Charged Attack");
    }

    private static void testNormalsPlungeAndA4() {
        Navia navia = navia(0, 0.75);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        TestCharacter cryo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator simulator = simulatorWith(
                navia, pyro, hydro, cryo);
        List<ActionRecord> records = captureActions(simulator);
        assertClose(0.40,
                navia.getEffectiveStats(0.0)
                        .get(StatType.ATK_PERCENT),
                "Navia A4 caps at two eligible members");
        double[] expected = {
            1.718139,
            1.589306,
            0.640927,
            0.640927,
            0.640927,
            2.451417
        };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(expected.length, records.size(),
                "Navia Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Navia Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Navia uninfused Normal element " + index);
        }
        for (int index = 2; index <= 4; index++) {
            AttackAction n3 = records.get(index).action;
            assertClose(0.01,
                    n3.getHitlagProfile().getHaltTimeSeconds(),
                    "Navia N3 multi-hit halt time " + index);
            assertClose(0.01,
                    n3.getHitlagProfile().getFactor(),
                    "Navia N3 multi-hit factor " + index);
            assertTrue(!n3.getHitlagProfile().canDefenseHalt(),
                    "Navia N3 multi-hit omits Defense Halt " + index);
            assertTrue(n3.getHitlagProfile().isDeployable(),
                    "Navia N3 multi-hit is deployable " + index);
            assertTrue(!n3.getHitlagProfile().isHeadshotOnly(),
                    "Navia N3 multi-hit is not headshot-only " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Navia High Plunge hit count");
        assertClose(3.422517,
                records.get(0).action.getDamagePercent(),
                "Navia High Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                records.get(0).action.getActionType(),
                "Navia High Plunge category");
    }

    private static void testShrapnelAcquisitionAndPressSkill() {
        Navia navia = navia(0, 0.75);
        CombatSimulator simulator = simulatorWith(navia);
        simulator.notifyReaction(
                ReactionResult.transform(
                        0.0,
                        "Crystallize",
                        ReactionResult.Kind.CRYSTALLIZE),
                navia);
        assertEquals(0, navia.getCrystalShrapnelCount(),
                "Plain Crystallize creation does not imply pickup");
        assertTrue(navia.notifyCrystallizeShardObtained(simulator),
                "Explicit standard shard pickup grants one stack");
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0,
                        ReactionResult.LunarType.CRYSTALLIZE),
                navia);
        assertEquals(2, navia.getCrystalShrapnelCount(),
                "Lunar-Crystallize directly grants one stack");
        navia.notifyCrystallizeShardObtained(simulator);

        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        AttackAction shardshot = named(
                records,
                "Ceremonial Crystalshot: Rosula Shardshot")
                .get(0).action;
        assertClose(6.711600 * 2.0,
                shardshot.getDamagePercent(),
                "Three stacks emit eleven fixed-target pellets");
        assertEquals(0, navia.getCrystalShrapnelCount(),
                "C0 Skill consumes all Shrapnel");
        assertClose(11.0 * FRAME,
                navia.getLastSkillTime(),
                "Navia Skill cooldown starts at firing frame");
        assertTrue(navia.isA1InfusionActive(simulator.getCurrentTime()),
                "Skill firing activates A1");
    }

    private static void testConstellationsParticlesAndInfusion() {
        Navia navia = navia(6, 0.25);
        CombatSimulator simulator = simulatorWith(navia);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureGeoParticles(simulator);
        for (int stack = 0; stack < 6; stack++) {
            navia.notifyCrystallizeShardObtained(simulator);
        }
        perform(simulator, CharacterActionKey.SKILL);
        AttackAction shardshot = named(
                records,
                "Ceremonial Crystalshot: Rosula Shardshot")
                .get(0).action;
        assertClose(7.896000 * 2.0,
                shardshot.getDamagePercent(),
                "C3 raises six-stack Shardshot base multiplier");
        assertClose(0.45,
                shardshot.getExtraBonuses()
                        .get(StatType.DMG_BONUS_ALL),
                "Excess stacks grant Skill DMG bonus");
        assertClose(0.36,
                shardshot.getExtraBonuses().get(StatType.CRIT_RATE),
                "C2 grants capped Shardshot CRIT Rate");
        assertClose(1.35,
                shardshot.getExtraBonuses().get(StatType.CRIT_DMG),
                "C6 grants CRIT DMG for excess stacks");
        assertEquals(3, navia.getCrystalShrapnelCount(),
                "C6 retains stacks above the first three");

        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction normal = named(records, "Blunt Refusal N1")
                .get(0).action;
        assertEquals(Element.GEO, normal.getElement(),
                "A1 infuses Navia Normal with Geo");
        assertClose(0.40,
                normal.getExtraBonuses().get(StatType.DMG_BONUS_ALL),
                "A1 adds forty percent basic attack damage");
        simulator.advanceTime(3.0);
        assertEquals(1, particles.size(),
                "Navia Skill emits one particle packet");
        assertClose(4.0, particles.get(0),
                "Low particle draw selects four Geo particles");
        assertEquals(1,
                named(records, "Cannon Fire Support (C2)").size(),
                "C2 emits one support shot per Skill");
        assertEquals(1,
                named(records,
                        "Ceremonial Crystalshot: Surging Blade").size(),
                "Skill emits one delayed Surging Blade");

        Navia c1 = navia(1, 0.75);
        CombatSimulator c1Simulator = simulatorWith(c1);
        c1.restoreCurrentEnergy(60.0);
        perform(c1Simulator, CharacterActionKey.BURST);
        for (int stack = 0; stack < 3; stack++) {
            c1.notifyCrystallizeShardObtained(c1Simulator);
        }
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertClose(9.0, c1.getCurrentEnergy(),
                "C1 restores three Energy per consumed stack");
        assertClose(12.0, c1.getBurstCooldownEndTime(),
                "C1 reduces Burst cooldown by three seconds");
    }

    private static void testBurstCadenceAndC4() {
        Navia navia = navia(5, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(navia, ally);
        List<ActionRecord> records = captureActions(simulator);
        navia.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, navia.getCurrentEnergy(),
                "Navia Burst spends Energy at frame twelve");
        assertClose(15.0, navia.getBurstCooldownEndTime(),
                "Navia Burst cooldown starts on cast");
        assertClose(1.504000,
                named(records,
                        "As the Sunlit Sky's Singing Salute")
                        .get(0).action.getDamagePercent(),
                "C5 raises Burst initial multiplier");
        advanceTo(simulator, 15.0);
        List<ActionRecord> support = named(
                records, "Cannon Fire Support");
        assertEquals(17, support.size(),
                "Navia Burst emits seventeen fixed support shots");
        assertClose(0.863000,
                support.get(0).action.getDamagePercent(),
                "C5 raises support-fire multiplier");
        assertClose(42.0 * FRAME,
                support.get(1).time - support.get(0).time,
                "First support-fire interval is forty-two frames");
        assertClose(48.0 * FRAME,
                support.get(2).time - support.get(1).time,
                "Second support-fire interval is forty-eight frames");
        assertEquals(5, navia.getCrystalShrapnelCount(),
                "Burst gains Shrapnel only once per 2.4 seconds");
        StatsContainer allyStats = withApplicableBuffs(
                simulator, ally, simulator.getCurrentTime());
        assertClose(0.20,
                allyStats.get(StatType.GEO_RES_SHRED),
                "C4 refreshes team-visible Geo resistance shred");
        advanceTo(simulator, 23.0);
        allyStats = withApplicableBuffs(
                simulator, ally, simulator.getCurrentTime());
        assertClose(0.0,
                allyStats.get(StatType.GEO_RES_SHRED),
                "C4 expires eight seconds after the last support hit");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Navia navia = navia(0, 0.75);
        CombatSimulator simulator = simulatorWith(navia);
        List<ActionRecord> records = captureActions(simulator);
        navia.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 15.0);
        int expectedSupport = named(
                records, "Cannon Fire Support").size();
        double expectedDamage = simulator.getTotalDamage();
        assertEquals(17, expectedSupport,
                "Original branch resolves all support shots");

        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 15.0);
        assertEquals(17,
                named(records, "Cannon Fire Support").size(),
                "Restored branch resolves each support shot once");
        assertClose(expectedDamage, simulator.getTotalDamage(),
                "Restored Burst branch preserves total damage");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 15.0);
        assertEquals(17,
                named(records, "Cannon Fire Support").size(),
                "Repeated restore keeps one support-fire sequence");

        Navia foreign = navia(0, 0.75);
        assertTrue(!navia.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Navia rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> navia.restoreCharacterState(null, simulator),
                "Navia rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(navia),
                "Navia rejects cross-simulator reuse");

        Navia invalidRandom = navia(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(
                        invalidSimulator,
                        CharacterActionKey.SKILL),
                "Navia rejects out-of-range particle draw");
    }

    private static Navia navia(int constellation, double particleDraw) {
        return new Navia(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        perform(simulator, CharacterActionRequest.of(key));
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionRequest request) {
        simulator.performAction(CharacterId.NAVIA, request);
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NAVIA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureGeoParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
                records.add(count);
            }
        });
        return records;
    }

    private static StatsContainer withApplicableBuffs(
            CombatSimulator simulator,
            Character character,
            double time) {
        StatsContainer stats = character.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (name.equals(record.action.getName())) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Navia,"),
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
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
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

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
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
