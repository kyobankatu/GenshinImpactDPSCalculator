package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.character.Prune;
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

/** Focused regression checks for Prune's fixed-target Bell support slice. */
public final class PruneRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private PruneRegressionTest() {
    }

    /** Runs data, timing, recast, support, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testNormalsChargedAndInitialSkill();
        testConvertedSkillParticlesAndTollingRally();
        testBurstCadenceA1AndConstellations();
        testC6ActiveSupport();
        testSnapshotNoEnemyAndIsolation();
        System.out.println("PruneRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Prune prune = new Prune(null, null, 6);
        assertEquals(CharacterId.PRUNE, prune.getCharacterId(),
                "Prune typed identity");
        assertEquals(CharacterId.PRUNE, CharacterId.fromName("Prune"),
                "Prune name lookup");
        assertEquals(CharacterId.PRUNE, CharacterId.fromNumericId(106),
                "Prune numeric lookup");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.PRUNE.getRegion(), "Prune region");
        assertEquals(Element.ANEMO, prune.getElement(), "Prune element");
        assertClose(9679.0,
                prune.getBaseStats().get(StatType.BASE_HP),
                "Prune base HP");
        assertClose(221.0,
                prune.getBaseStats().get(StatType.BASE_ATK),
                "Prune base ATK");
        assertClose(580.0,
                prune.getBaseStats().get(StatType.BASE_DEF),
                "Prune base DEF");
        assertClose(0.24,
                prune.getBaseStats().get(StatType.ATK_PERCENT),
                "Prune ascension ATK");
        assertClose(70.0, prune.getEnergyCost(), "Prune Energy cost");
        assertClose(15.0, prune.getSkillCD(), "Prune Skill cooldown");
        assertClose(18.0, prune.getBurstCD(), "Prune Burst cooldown");
        assertTrue(!prune.isHexereiHomeworkRepresented(),
                "Hexerei homework fails closed");
        assertTrue(!prune.isRandomMultiTargetRepresented(),
                "Random multi-target behavior fails closed");
        assertCsvShape(Path.of(
                "config/characters/Prune/Prune_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Prune/Prune_Multipliers.csv"), 32);
        assertCsvValue("Witch-tribution C5", 4.0912);
        assertCsvValue("C6 Flat ATK", 350.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Prune(null, null, -1),
                "Prune rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Prune(null, null, 7),
                "Prune rejects C7");
        CombatSimulator simulator = simulatorWith(prune);
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.PRUNE,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Prune rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> prune.onAction(null, simulator),
                "Prune rejects null action");
    }

    private static void testNormalsChargedAndInitialSkill() {
        Prune prune = new Prune(null, null, 0);
        CombatSimulator simulator = simulatorWith(prune);
        List<ActionRecord> records = capturePruneActions(simulator);
        int[] hitFrames = { 19, 24, 42 };
        int[] durations = { 23, 49, 63 };
        double[] multipliers = { 0.826554, 0.820774, 1.155606 };
        for (int index = 0; index < 3; index++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = named(records,
                    "Badaboom Hexbuster Hammer N" + (index + 1)).get(0);
            assertClose(castTime + hitFrames[index] * FRAME,
                    record.time, "Prune N" + (index + 1) + " hitmark");
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Prune N" + (index + 1) + " multiplier");
            assertEquals(Element.ANEMO, record.action.getElement(),
                    "Prune catalyst Normal element");
            assertClose(castTime + durations[index] * FRAME,
                    simulator.getCurrentTime(),
                    "Prune N" + (index + 1) + " recovery");
        }

        double chargeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Badaboom Hexbuster Hammer Charged Attack").get(0);
        assertClose(chargeCast + 46.0 * FRAME, charged.time,
                "Prune Charged hitmark");
        assertClose(2.26984, charged.action.getDamagePercent(),
                "Prune Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Prune Charged has no ICD");
        assertClose(chargeCast + 95.0 * FRAME,
                simulator.getCurrentTime(), "Prune Charged recovery");

        Prune skillPrune = new Prune(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillPrune);
        List<ActionRecord> skillRecords = capturePruneActions(skillSimulator);
        double skillCast = skillSimulator.getCurrentTime();
        performSkill(skillSimulator);
        ActionRecord skill = named(skillRecords,
                "Ring-A-Ding-Ding Hexhunter Chime").get(0);
        assertClose(skillCast + 26.0 * FRAME, skill.time,
                "Prune initial Skill hitmark");
        assertClose(2.84648, skill.action.getDamagePercent(),
                "Prune initial Skill multiplier");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Prune initial Skill has no ICD");
        assertClose(skillCast + 27.0 * FRAME,
                skillSimulator.getCurrentTime(),
                "Prune initial Skill recovery");
        assertClose(15.0 - 2.0 * FRAME,
                skillPrune.getSkillCDRemaining(
                        skillSimulator.getCurrentTime()),
                "Prune Skill cooldown starts at frame 25");
    }

    private static void testConvertedSkillParticlesAndTollingRally() {
        Prune prune = new Prune(null, null, 1);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator simulator = simulatorWith(prune, ally);
        List<ActionRecord> records = capturePruneActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        prune.restoreCurrentEnergy(0.0);
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        performSkill(simulator);
        assertEquals(Element.PYRO, prune.getConvertedElement(),
                "Initial Skill Swirl stores Pyro conversion");
        assertTrue(prune.getSkillRecastUntil()
                        > simulator.getCurrentTime(),
                "Initial Skill opens converted recast");
        assertClose(0.0,
                prune.getSkillCDRemaining(simulator.getCurrentTime()),
                "Converted recast bypasses original cooldown");
        double recastTime = simulator.getCurrentTime();
        performSkill(simulator);
        ActionRecord converted = named(records,
                "Clang Clang Witch-tribution Comes").get(0);
        assertClose(recastTime + 30.0 * FRAME, converted.time,
                "Converted Skill hitmark");
        assertEquals(Element.PYRO, converted.action.getElement(),
                "Converted Skill uses stored element");
        assertClose(3.47752, converted.action.getDamagePercent(),
                "Converted Skill multiplier");
        assertClose(2.0, prune.getCurrentEnergy(),
                "C1 converted hammer restores flat Energy");
        assertTrue(hasActiveBuff(
                ally, BuffId.PRUNE_TOLLING_RALLY,
                simulator.getCurrentTime()),
                "Converted hammer grants ally Tolling Rally");
        assertTrue(!hasActiveBuff(
                prune, BuffId.PRUNE_TOLLING_RALLY,
                simulator.getCurrentTime()),
                "Tolling Rally excludes Prune");
        assertClose(Double.NEGATIVE_INFINITY,
                prune.getSkillRecastUntil(),
                "Converted Skill consumes recast window");

        advanceTo(simulator, 26.0 * FRAME + 100.0 * FRAME);
        assertEquals(1, particles.size(),
                "Initial Skill emits one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Initial Skill particle count");
        assertClose(26.0 * FRAME + 100.0 * FRAME,
                particles.get(0).time,
                "Initial Skill particle delay");
    }

    private static void testBurstCadenceA1AndConstellations() {
        Prune c0 = new Prune(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = capturePruneActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(0.0, c0.getCurrentEnergy(),
                "Prune Burst spends 70 Energy at frame 16");
        assertClose(16.0 * FRAME,
                c0.getBurstEnergyMarkers().get(0)[0],
                "Prune Burst Energy marker timing");
        assertClose(18.0 - 71.0 * FRAME,
                c0.getBurstCDRemaining(c0Simulator.getCurrentTime()),
                "Prune Burst cooldown starts at cast time");
        advanceTo(c0Simulator, 14.0);
        assertEquals(1, named(c0Records,
                "The Bell Tolls The Hunt Is On").size(),
                "Prune Burst cast hit count");
        List<ActionRecord> c0Ticks = named(c0Records, "Witchlure Bell");
        assertEquals(6, c0Ticks.size(), "C0 Burst tick count");
        assertClose(137.0 * FRAME, c0Ticks.get(0).time,
                "Prune first Burst tick");
        assertClose(117.0 * FRAME,
                c0Ticks.get(1).time - c0Ticks.get(0).time,
                "Prune Burst tick interval");

        Prune c6 = new Prune(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = capturePruneActions(c6Simulator);
        c6Simulator.getEnemy().setAura(Element.PYRO, 100.0);
        perform(c6Simulator, CharacterActionKey.BURST);
        advanceTo(c6Simulator, 18.0);
        assertEquals(8, named(c6Records, "Witchlure Bell").size(),
                "C6 extends Burst to eight ticks");
        assertTrue(named(c6Records,
                "Verdict and Punishment").size() > 0,
                "Burst-tick Swirl queues A1 hammer");
        assertTrue(named(c6Records,
                "Banehunter Oathhammer Ricochet").size() > 0,
                "A1 hammer queues fixed-target C4 ricochet");
        assertTrue(c6.getC2AttackPercent(4.0) >= 0.15,
                "Hammer hits grow C2 while Burst is active");
        assertTrue(c6.getC2AttackPercent(4.0) <= 0.40 + EPSILON,
                "C2 ATK remains capped");
    }

    private static void testC6ActiveSupport() {
        Prune prune = new Prune(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator simulator = simulatorWith(prune, ally);
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        performSkill(simulator);
        performSkill(simulator);
        simulator.setActiveCharacter(CharacterId.BENNETT);
        double pruneAtkBefore = resolvedStats(simulator, prune).getTotalAtk();
        double allyAtkBefore = resolvedStats(simulator, ally).getTotalAtk();
        simulator.getEnemy().setAura(Element.HYDRO, 20.0);
        AttackAction reaction = new AttackAction(
                "Prune C6 Regression Reaction",
                1.0,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.PYRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        reaction.setICD(ICDType.None, ICDTag.None, 1.0);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.BENNETT, reaction);
        assertClose(pruneAtkBefore + 350.0,
                resolvedStats(simulator, prune).getTotalAtk(),
                "C6 grants Prune flat ATK");
        assertClose(allyAtkBefore + 350.0,
                resolvedStats(simulator, ally).getTotalAtk(),
                "C6 grants active Tolling Rally ally flat ATK");
        simulator.advanceTime(5.0);
        assertClose(pruneAtkBefore,
                resolvedStats(simulator, prune).getTotalAtk(),
                "C6 Prune ATK expires at five seconds");
    }

    private static void testSnapshotNoEnemyAndIsolation() {
        Prune prune = new Prune(null, null, 0);
        CombatSimulator simulator = simulatorWith(prune);
        perform(simulator, CharacterActionKey.BURST);
        int pending = prune.getPendingHitCount();
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(4.0);
        assertTrue(prune.getPendingHitCount() < pending,
                "Burst advances pending work");
        simulator.restoreSnapshot(snapshot);
        assertEquals(pending, prune.getPendingHitCount(),
                "Snapshot restores pending Prune hits");
        simulator.advanceTime(14.0);
        assertEquals(0, prune.getPendingHitCount(),
                "Restored Burst work resolves once");

        Prune noEnemyPrune = new Prune(null, null, 0);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyPrune);
        List<ParticleRecord> particles = captureAnemoParticles(noEnemy);
        performSkill(noEnemy);
        noEnemy.advanceTime(3.0);
        assertEquals(0, particles.size(),
                "No target suppresses Prune particles");

        Prune independent = new Prune(null, null, 0);
        CombatSimulator independentSimulator = simulatorWith(independent);
        independent.initializeForSimulator(independentSimulator);
        SnapshotAwareCharacterEffect.State foreign =
                independent.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> prune.restoreCharacterState(foreign, simulator),
                "Prune rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> prune.restoreCharacterState(null, simulator),
                "Prune rejects null state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(prune),
                "Prune rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        for (StatType resistance : new StatType[] {
                StatType.ANEMO_DMG_BONUS,
                StatType.PYRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                StatType.ELECTRO_DMG_BONUS,
                StatType.CRYO_DMG_BONUS
        }) {
            enemy.setRes(resistance, 0.0);
        }
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static CombatSimulator simulatorWithoutEnemy(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.PRUNE,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.PRUNE,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> capturePruneActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.PRUNE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureAnemoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ANEMO) {
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

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        if (targetTime > simulator.getCurrentTime()) {
            simulator.advanceTime(targetTime - simulator.getCurrentTime());
        }
    }

    private static StatsContainer resolvedStats(
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

    private static boolean hasActiveBuff(
            Character character,
            BuffId id,
            double currentTime) {
        for (Buff buff : character.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }

    private static void assertCsvShape(
            Path path,
            int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Prune,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Prune/Prune_Status.csv",
                "config/characters/Prune/Prune_Multipliers.csv"
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
        throw new AssertionError("Prune CSVs missing key " + key);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.compare(expected, actual) == 0) {
            return;
        }
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(
                    message + ": expected=" + expected
                            + " actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected
                            + " actual=" + actual);
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
            throw new AssertionError(
                    message + ": unexpected=" + thrown, thrown);
        }
        throw new AssertionError(message + ": no exception");
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
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }
}
