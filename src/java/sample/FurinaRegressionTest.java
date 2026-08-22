package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.element.ICDManager;
import model.character.Furina;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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

/** Focused regression checks for Furina's fixed-HP Ousia Salon slice. */
public final class FurinaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private FurinaRegressionTest() {
    }

    /** Runs data, timing, particles, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndPrivateIcd();
        testSwordBasics();
        testSalonCadenceParticlesA4AndC4();
        testBurstC1C3C5AndC6();
        testSnapshotAndFailClosedBoundaries();
        System.out.println("FurinaRegressionTest passed");
    }

    private static void testIdentityDataAndPrivateIcd()
            throws IOException {
        Furina furina = new Furina(null, null, 6);
        assertEquals(CharacterId.FURINA, furina.getCharacterId(),
                "Furina typed identity");
        assertEquals(CharacterId.FURINA, CharacterId.fromName("Furina"),
                "Furina name lookup");
        assertEquals(CharacterId.FURINA, CharacterId.fromNumericId(96),
                "Furina numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.FURINA.getRegion(), "Furina region");
        assertEquals(Element.HYDRO, furina.getElement(),
                "Furina element");
        assertClose(15307.0,
                furina.getBaseStats().get(StatType.BASE_HP),
                "Furina base HP");
        assertClose(244.0,
                furina.getBaseStats().get(StatType.BASE_ATK),
                "Furina base ATK");
        assertClose(696.0,
                furina.getBaseStats().get(StatType.BASE_DEF),
                "Furina base DEF");
        assertClose(0.242,
                furina.getBaseStats().get(StatType.CRIT_RATE),
                "Furina base plus ascension CRIT Rate");
        assertClose(60.0, furina.getEnergyCost(),
                "Furina Energy cost");
        assertClose(20.0, furina.getSkillCD(),
                "Furina Skill cooldown");
        assertClose(15.0, furina.getBurstCD(),
                "Furina Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.FURINA,
                    new Furina(null, null, constellation).getCharacterId(),
                    "Furina explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Furina/Furina_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Furina/Furina_Multipliers.csv"), 41);
        assertCsvValue("Mademoiselle Crabaletta C5", 0.16576);
        assertCsvValue("Fanfare DMG Ratio C3", 0.0029);

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "FURINA",
                ICDTag.Furina_Chevalmarin,
                ICDType.FurinaSalonSolitaire,
                0.0), "Chevalmarin first application passes");
        assertTrue(!manager.checkApplication(
                "FURINA",
                ICDTag.Furina_Chevalmarin,
                ICDType.FurinaSalonSolitaire,
                1.0), "Chevalmarin sequence blocks second hit");
        assertTrue(manager.checkApplication(
                "FURINA",
                ICDTag.Furina_Chevalmarin,
                ICDType.FurinaSalonSolitaire,
                2.0), "Chevalmarin applies again on its third hit");
        assertTrue(manager.checkApplication(
                "FURINA",
                ICDTag.Furina_Usher,
                ICDType.FurinaSalonSolitaire,
                1.0), "Usher owns an independent source tag");
    }

    private static void testSwordBasics() {
        Furina furina = new Furina(null, null, 0);
        CombatSimulator simulator = simulatorWith(furina);
        List<ActionRecord> records = captureFurinaActions(simulator);
        double[] multipliers = {
            0.888955, 0.803398, 1.012669, 1.346634
        };
        int[] hitFrames = { 15, 12, 21, 27 };
        int[] durations = { 34, 28, 48, 58 };
        int[] hitlagFrames = { 5, 5, 5, 5 };
        for (int index = 0; index < multipliers.length; index++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord normal = named(records,
                    "Soloist's Solicitation N" + (index + 1)).get(0);
            assertClose(castTime + hitFrames[index] * FRAME,
                    normal.time, "Furina Normal hitmark " + index);
            assertClose(castTime
                            + (durations[index] + hitlagFrames[index]) * FRAME,
                    simulator.getCurrentTime(),
                    "Furina Normal duration " + index);
            assertClose(multipliers[index],
                    normal.action.getDamagePercent(),
                    "Furina Normal multiplier " + index);
            assertEquals(Element.PHYSICAL, normal.action.getElement(),
                    "Furina C0 Normal is Physical");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Soloist's Solicitation Charged").get(0);
        assertClose(chargedCast + 33.0 * FRAME, charged.time,
                "Furina Charged hitmark");
        assertClose(1.36354, charged.action.getDamagePercent(),
                "Furina Charged multiplier");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Soloist's Solicitation High Plunge").get(0);
        assertClose(plungeCast + 47.0 * FRAME, plunge.time,
                "Furina high Plunge hitmark");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Furina high Plunge multiplier");
    }

    private static void testSalonCadenceParticlesA4AndC4() {
        Furina furina = new Furina(null, null, 4);
        TestCharacter allyOne = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter allyTwo = new TestCharacter(
                CharacterId.XIANGLING, Element.PYRO);
        TestCharacter allyThree = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(
                furina, allyOne, allyTwo, allyThree);
        List<ActionRecord> records = captureFurinaActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        furina.restoreCurrentEnergy(0.0);
        performSkill(simulator);
        assertClose(1754.0 * FRAME,
                furina.getSalonActiveUntil(),
                "Salon exact source duration");
        ActionRecord bubble = named(records,
                "Salon Solitaire: Ousia Bubble").get(0);
        assertClose(18.0 * FRAME, bubble.time,
                "Ousia bubble hitmark");
        assertClose(0.133688, bubble.action.getDamagePercent(),
                "Ousia bubble C4 uses T9 value");

        advanceTo(simulator, 2.3);
        ActionRecord chevalmarin = named(records,
                "Salon Member: Surintendante Chevalmarin").get(0);
        ActionRecord crabaletta = named(records,
                "Salon Member: Mademoiselle Crabaletta").get(0);
        ActionRecord usher = named(records,
                "Salon Member: Gentilhomme Usher").get(0);
        assertClose(110.0 * FRAME, chevalmarin.time,
                "Chevalmarin deterministic first impact");
        assertClose(131.0 * FRAME, crabaletta.time,
                "Crabaletta deterministic first impact");
        assertClose(133.0 * FRAME, usher.time,
                "Usher deterministic first impact");
        assertClose(0.054944 * 1.4,
                chevalmarin.action.getDamagePercent(),
                "Four fixed-full-HP party members scale Salon damage");
        assertClose(0.107149,
                bonus(chevalmarin.action, StatType.DMG_BONUS_ALL),
                "A4 uses Furina's 15307 Max HP");
        assertEquals(ICDTag.Furina_Chevalmarin,
                chevalmarin.action.getICDTag(),
                "Chevalmarin private ICD tag");
        assertEquals(ICDType.FurinaSalonSolitaire,
                chevalmarin.action.getICDType(),
                "Chevalmarin private 30-second sequence");
        assertEquals(ICDType.None, crabaletta.action.getICDType(),
                "Crabaletta has no ICD");
        assertClose(4.0, furina.getTotalFlatEnergy(),
                "First Salon hit grants C4 flat Energy");

        advanceTo(simulator, 3.51);
        assertEquals(1, particles.size(),
                "First Salon hit emits one particle packet");
        assertClose(1.0, particles.get(0).count,
                "Furina particle packet size");
        advanceTo(simulator, 6.77);
        assertEquals(2, particles.size(),
                "Particle gate reopens on later Chevalmarin hit");
        advanceTo(simulator, 7.41);
        assertClose(8.0, furina.getTotalFlatEnergy(),
                "C4 five-second Energy gate reopens");
    }

    private static void testBurstC1C3C5AndC6() {
        Furina c3 = new Furina(null, null, 3);
        CombatSimulator burstSimulator = simulatorWith(c3);
        List<ActionRecord> burstRecords = captureFurinaActions(
                burstSimulator);
        c3.restoreCurrentEnergy(60.0);
        perform(burstSimulator, CharacterActionKey.BURST);
        ActionRecord burst = named(burstRecords,
                "Let the People Rejoice").get(0);
        assertClose(98.0 * FRAME, burst.time,
                "Furina Burst hitmark");
        assertClose(0.228128, burst.action.getDamagePercent(),
                "C3 Burst talent value");
        assertClose(0.435,
                burst.action.getStatSnapshot().get(
                        StatType.DMG_BONUS_ALL),
                "C1 initial Fanfare buffs Burst at frame 98");
        assertClose(0.0, c3.getCurrentEnergy(),
                "Burst spends 60 Energy at frame 7");

        Furina c5 = new Furina(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureFurinaActions(c5Simulator);
        performSkill(c5Simulator);
        assertClose(0.15728,
                named(c5Records,
                        "Salon Solitaire: Ousia Bubble").get(0)
                                .action.getDamagePercent(),
                "C5 raises Ousia bubble talent");

        Furina c6 = new Furina(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureFurinaActions(c6Simulator);
        performSkill(c6Simulator);
        for (int index = 0; index < 6; index++) {
            perform(c6Simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = namePrefix(
                c6Records, "Soloist's Solicitation N");
        assertEquals(6, normals.size(),
                "Six C6 sword hits resolve");
        for (ActionRecord normal : normals) {
            assertEquals(Element.HYDRO, normal.action.getElement(),
                    "C6 converts represented sword hit to Hydro");
            assertClose(15307.0 * 0.18,
                    normal.action.getStatSnapshot().get(
                            StatType.FLAT_DMG_BONUS),
                    "C6 adds 18 percent Max HP damage");
        }
        assertEquals(6, c6.getC6HitCount(),
                "C6 ends after six accepted hits");
        perform(c6Simulator, CharacterActionKey.NORMAL);
        normals = namePrefix(c6Records, "Soloist's Solicitation N");
        assertEquals(Element.PHYSICAL,
                normals.get(normals.size() - 1).action.getElement(),
                "Seventh sword hit fails closed to Physical");
    }

    private static void testSnapshotAndFailClosedBoundaries() {
        Furina furina = new Furina(null, null, 0);
        CombatSimulator simulator = simulatorWith(furina);
        List<ActionRecord> records = captureFurinaActions(simulator);
        performSkill(simulator);
        advanceTo(simulator, 1.6);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 1.9);
        assertEquals(1, named(records,
                "Salon Member: Surintendante Chevalmarin").size(),
                "Original Salon projectile resolves once");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 1.9);
        assertEquals(1, named(records,
                "Salon Member: Surintendante Chevalmarin").size(),
                "Repeated rollback reconstructs one Salon projectile");

        assertThrows(IllegalArgumentException.class,
                () -> furina.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Furina rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> furina.onAction(null, simulator),
                "Furina rejects null action");
        assertTrue(!furina.isPlayerHpRepresented(),
                "Player HP and healing fail closed");
        assertTrue(!furina.isRandomTargetingRepresented(),
                "Random and multi-target behavior fail closed");
        assertThrows(IllegalArgumentException.class,
                () -> new Furina(null, null, -1),
                "Furina rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Furina(null, null, 7),
                "Furina rejects constellation above C6");

        Furina reused = new Furina(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Furina rejects cross-simulator reuse");
        Furina foreign = new Furina(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!furina.acceptsCharacterState(foreignState),
                "Furina rejects another instance's snapshot payload");
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
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.FURINA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.FURINA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureFurinaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.FURINA) {
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
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static List<ActionRecord> namePrefix(
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

    private static double bonus(
            AttackAction action,
            StatType type) {
        return action.getExtraBonuses().getOrDefault(type, 0.0);
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
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Furina,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Furina/Furina_Status.csv",
                "config/characters/Furina/Furina_Multipliers.csv"
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
        throw new AssertionError("Furina CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
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
            throw new AssertionError(message + ": unexpected exception",
                    throwable);
        }
        throw new AssertionError(message + ": no exception");
    }

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
        private TestCharacter(
                CharacterId id,
                Element characterElement) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
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
