package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.reaction.ReactionResult;
import model.character.Illuga;
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

/** Focused regression checks for Illuga's fixed-target Song support slice. */
public final class IllugaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private IllugaRegressionTest() {
    }

    /** Runs data, timing, Song, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testNormalsChargedAndSkillTiming();
        testBurstSongA1A4AndConstellations();
        testNoEnemySnapshotAndIsolation();
        System.out.println("IllugaRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Illuga illuga = new Illuga(null, null, 6);
        assertEquals(CharacterId.ILLUGA, illuga.getCharacterId(),
                "Illuga typed identity");
        assertEquals(CharacterId.ILLUGA, CharacterId.fromName("Illuga"),
                "Illuga name lookup");
        assertEquals(CharacterId.ILLUGA, CharacterId.fromNumericId(113),
                "Illuga numeric lookup");
        assertEquals(CharacterRegion.NOD_KRAI,
                CharacterId.ILLUGA.getRegion(),
                "Illuga region");
        assertEquals(Element.GEO, illuga.getElement(),
                "Illuga element");
        assertClose(11962.0,
                illuga.getBaseStats().get(StatType.BASE_HP),
                "Illuga base HP");
        assertClose(191.0,
                illuga.getBaseStats().get(StatType.BASE_ATK),
                "Illuga base ATK");
        assertClose(813.0,
                illuga.getBaseStats().get(StatType.BASE_DEF),
                "Illuga base DEF");
        assertClose(96.0,
                illuga.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Illuga ascension EM");
        assertClose(60.0, illuga.getEnergyCost(),
                "Illuga Energy cost");
        assertClose(15.0, illuga.getSkillCD(),
                "Illuga Skill cooldown");
        assertClose(15.0, illuga.getBurstCD(),
                "Illuga Burst cooldown");
        assertTrue(illuga.isLunarCharacter(),
                "Illuga contributes typed Moonsign membership");
        assertTrue(!illuga.isGeoConstructTrackingRepresented(),
                "Geo construct geometry fails closed");
        assertTrue(!illuga.isIndirectLunarSupportRepresented(),
                "Indirect Harmony support fails closed");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.ILLUGA,
                    new Illuga(null, null, constellation)
                            .getCharacterId(),
                    "Illuga explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Illuga/Illuga_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Illuga/Illuga_Multipliers.csv"), 47);
        assertCsvValue("Hold EM Ratio C5", 12.064);
        assertCsvValue("Lunar Crystallize Flat Bonus Ratio C3", 4.5184);
        assertCsvValue("Particle Expected Count", 4.5);

        assertThrows(IllegalArgumentException.class,
                () -> new Illuga(null, null, -1),
                "Illuga rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Illuga(null, null, 7),
                "Illuga rejects constellation above six");
        CombatSimulator simulator = simulatorWith(
                new Illuga(null, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> simulator.getCharacter(CharacterId.ILLUGA)
                        .onAction(null, simulator),
                "Illuga rejects null action");
        assertThrows(IllegalStateException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Illuga rejects Charged Attack without Normal");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Illuga rejects unsourced Plunge");
    }

    private static void testNormalsChargedAndSkillTiming() {
        Illuga illuga = new Illuga(null, null, 0);
        CombatSimulator simulator = simulatorWith(illuga);
        List<ActionRecord> records = captureIllugaActions(simulator);
        double[][] multipliers = {
            { 0.870217 }, { 0.891515 },
            { 0.577490, 0.577490 }, { 1.401397 }
        };
        int[][] hitFrames = { { 13 }, { 16 }, { 20, 32 }, { 43 } };
        int[] durationFrames = { 30, 33, 57, 80 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> stage = normalStage(records, step);
            assertEquals(multipliers[step].length, stage.size(),
                    "Illuga N" + (step + 1) + " hit count");
            for (int hit = 0; hit < stage.size(); hit++) {
                assertClose(castTime + hitFrames[step][hit] * FRAME,
                        stage.get(hit).time,
                        "Illuga N" + (step + 1) + " hitmark " + hit);
                assertClose(multipliers[step][hit],
                        stage.get(hit).action.getDamagePercent(),
                        "Illuga N" + (step + 1) + " multiplier " + hit);
                assertEquals(Element.PHYSICAL,
                        stage.get(hit).action.getElement(),
                        "Illuga Normal remains Physical");
            }
            assertClose(castTime + durationFrames[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Illuga N" + (step + 1) + " recovery");
        }

        Illuga chargedIlluga = new Illuga(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(chargedIlluga);
        List<ActionRecord> chargedRecords = captureIllugaActions(
                chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.NORMAL);
        double chargedCast = chargedSimulator.getCurrentTime();
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                chargedRecords,
                "Oathkeeper's Spear Charged Attack").get(0);
        assertClose(chargedCast + 23.0 * FRAME, charged.time,
                "Illuga Charged hitmark");
        assertClose(2.03978, charged.action.getDamagePercent(),
                "Illuga Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Illuga Charged has no ICD");
        assertClose(chargedCast + 66.0 * FRAME,
                chargedSimulator.getCurrentTime(),
                "Illuga Charged recovery");

        Illuga press = new Illuga(null, null, 0);
        CombatSimulator pressSimulator = simulatorWith(press);
        List<ActionRecord> pressRecords = captureIllugaActions(
                pressSimulator);
        List<ParticleRecord> particles = captureGeoParticles(
                pressSimulator);
        performSkill(pressSimulator, SkillActionMode.PRESS);
        ActionRecord pressHit = named(
                pressRecords, "Dawnbearing Songbird Press").get(0);
        assertClose(27.0 * FRAME, pressHit.time,
                "Illuga Press hitmark");
        assertClose(47.0 * FRAME, pressSimulator.getCurrentTime(),
                "Illuga Press recovery");
        assertClose(96.0 * 8.20352 + 813.0 * 4.10176,
                pressHit.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Illuga Press EM plus DEF formula");
        assertEquals(ICDType.None, pressHit.action.getICDType(),
                "Illuga Press has no ICD");
        assertTrue(pressHit.action.isShatterTrigger(),
                "Illuga Press is blunt");
        assertClose(15.0 - 23.0 * FRAME,
                press.getSkillCDRemaining(
                        pressSimulator.getCurrentTime()),
                "Illuga Press cooldown starts at frame 24");
        assertEquals(0, particles.size(),
                "Illuga particles remain in flight after recovery");
        advanceTo(pressSimulator, 127.0 * FRAME);
        assertEquals(1, particles.size(),
                "Illuga emits one expected-value particle packet");
        assertClose(4.5, particles.get(0).count,
                "Illuga expected particle count");
        assertClose(127.0 * FRAME, particles.get(0).time,
                "Illuga particle travel time");

        Illuga hold = new Illuga(null, null, 5);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureIllugaActions(
                holdSimulator);
        performSkill(holdSimulator, SkillActionMode.HOLD);
        ActionRecord holdHit = named(
                holdRecords, "Dawnbearing Songbird Hold").get(0);
        assertClose(36.0 * FRAME, holdHit.time,
                "Illuga Hold hitmark");
        assertClose(58.0 * FRAME, holdSimulator.getCurrentTime(),
                "Illuga Hold recovery");
        assertClose(96.0 * 12.064 + 813.0 * 6.032,
                holdHit.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Illuga C5 Hold EM plus DEF formula");
        assertClose(15.0 - 25.0 * FRAME,
                hold.getSkillCDRemaining(holdSimulator.getCurrentTime()),
                "Illuga Hold cooldown starts at frame 33");
    }

    private static void testBurstSongA1A4AndConstellations() {
        Illuga illuga = new Illuga(null, null, 6);
        TestCharacter geo = new TestCharacter(
                CharacterId.NOELLE, Element.GEO, false);
        TestCharacter hydro = new TestCharacter(
                CharacterId.COLUMBINA, Element.HYDRO, true);
        CombatSimulator simulator = simulatorWith(illuga, geo, hydro);
        simulator.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        List<ActionRecord> records = captureIllugaActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord burst = named(
                records, "Shadowless Reflection").get(0);
        assertClose(48.0 * FRAME, burst.time,
                "Illuga Burst hitmark");
        assertClose(65.0 * FRAME, simulator.getCurrentTime(),
                "Illuga Burst recovery");
        assertClose(96.0 * 16.544 + 813.0 * 8.272,
                burst.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Illuga C3 Burst EM plus DEF formula");
        assertEquals(20, illuga.getNightingalesSongStacks(),
                "Illuga Burst hit consumes one of 21 Song stacks");
        assertClose(0.0, illuga.getCurrentEnergy(),
                "Illuga Burst spends Energy at frame six");
        assertClose(6.0 * FRAME,
                illuga.getBurstEnergyMarkers().get(0)[0],
                "Illuga Burst Energy marker");
        assertClose(200.0,
                illuga.getC4DefenseBonus(simulator.getCurrentTime()),
                "Illuga C4 is live during Burst status");

        simulator.setActiveCharacter(CharacterId.NOELLE);
        AttackAction geoProbe = probe(
                "Illuga Song Geo probe", Element.GEO, false);
        StatsContainer perHit = geo.getEffectiveStats(
                simulator.getCurrentTime());
        illuga.applyTargetDependentTeamStats(
                perHit, geo, simulator.getEnemy(), geoProbe,
                simulator.getCurrentTime());
        assertClose(80.0,
                perHit.get(StatType.ELEMENTAL_MASTERY),
                "Illuga C6 Ascendant A1 grants 80 EM");
        assertClose(0.10, perHit.get(StatType.CRIT_RATE) - 0.05,
                "Illuga C6 A1 Geo CRIT Rate");
        assertClose(0.30, perHit.get(StatType.CRIT_DMG) - 0.50,
                "Illuga C6 A1 Geo CRIT DMG");
        assertClose(200.0, perHit.get(StatType.DEF_FLAT),
                "Illuga C4 active ally DEF");
        assertClose(96.0 * (0.672 + 0.24),
                perHit.get(StatType.FLAT_DMG_BONUS),
                "Illuga C3 and A4 three-member Geo Song bonus");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, geoProbe);
        assertEquals(19, illuga.getNightingalesSongStacks(),
                "Accepted Geo hit consumes one Song stack");

        AttackAction lunarProbe = probe(
                "Illuga Song Lunar probe", Element.GEO, true);
        StatsContainer lunarStats = geo.getEffectiveStats(
                simulator.getCurrentTime());
        illuga.applyTargetDependentTeamStats(
                lunarStats, geo, simulator.getEnemy(), lunarProbe,
                simulator.getCurrentTime());
        assertClose(96.0 * (4.5184 + 1.60),
                lunarStats.get(StatType.FLAT_DMG_BONUS),
                "Illuga direct Lunar-Crystallize Song bonus");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, lunarProbe);
        assertEquals(18, illuga.getNightingalesSongStacks(),
                "Direct Lunar-Crystallize consumes one Song stack");

        for (int index = 0; index < 5; index++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.NOELLE,
                    probe("Illuga Song C2 probe " + index,
                            Element.GEO, false));
        }
        assertEquals(13, illuga.getNightingalesSongStacks(),
                "Seven post-Burst Song consumptions reach C2 threshold");
        advanceTo(simulator,
                65.0 * FRAME + 50.0 * FRAME + EPSILON);
        ActionRecord c2 = named(records, "Aedon C2 Hit").get(0);
        assertClose(96.0 * 4.0 + 813.0 * 2.0,
                c2.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Illuga C2 EM plus DEF formula");
        assertEquals(ICDType.None, c2.action.getICDType(),
                "Illuga C2 has no ICD");

        simulator.setActiveCharacter(CharacterId.ILLUGA);
        illuga.restoreCurrentEnergy(0.0);
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE),
                illuga);
        assertClose(12.0, illuga.getTotalFlatEnergy(),
                "Illuga C1 grants flat Energy on-field");
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE),
                illuga);
        assertClose(12.0, illuga.getTotalFlatEnergy(),
                "Illuga C1 blocks before 15 seconds");
        double firstC1Time = simulator.getCurrentTime();
        advanceTo(simulator, firstC1Time + 15.0 - EPSILON);
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE),
                illuga);
        assertClose(12.0, illuga.getTotalFlatEnergy(),
                "Illuga C1 remains closed before boundary");
        advanceTo(simulator, firstC1Time + 15.0);
        simulator.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE),
                illuga);
        assertClose(24.0, illuga.getTotalFlatEnergy(),
                "Illuga C1 opens exactly at 15 seconds");

        advanceTo(simulator, 20.0);
        assertTrue(!illuga.isA1Active(20.0),
                "Illuga A1 uses a half-open 20-second window");
        assertClose(0.0,
                illuga.getC4DefenseBonus(20.0),
                "Illuga C4 expires with Burst status");
    }

    private static void testNoEnemySnapshotAndIsolation() {
        Illuga noEnemyIlluga = new Illuga(null, null, 0);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyIlluga);
        List<ParticleRecord> particles = captureGeoParticles(noEnemy);
        performSkill(noEnemy, SkillActionMode.PRESS);
        noEnemy.advanceTime(3.0);
        assertEquals(0, particles.size(),
                "No target suppresses Illuga particles");
        assertTrue(noEnemyIlluga.getSkillCDRemaining(
                        noEnemy.getCurrentTime()) > 0.0,
                "No target still starts Illuga Skill cooldown");

        Illuga illuga = new Illuga(null, null, 2);
        TestCharacter geo = new TestCharacter(
                CharacterId.NOELLE, Element.GEO, false);
        CombatSimulator simulator = simulatorWith(illuga, geo);
        List<ActionRecord> records = captureIllugaActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        for (int index = 0; index < 6; index++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.NOELLE,
                    probe("Illuga rollback probe " + index,
                            Element.GEO, false));
        }
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(1.0);
        int firstBranch = named(records, "Aedon C2 Hit").size();
        assertEquals(1, firstBranch,
                "Illuga original branch resolves one C2 hit");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(1.0);
        assertEquals(1, named(records, "Aedon C2 Hit").size(),
                "Illuga repeated restore resolves one C2 hit");

        Illuga other = new Illuga(null, null, 2);
        SnapshotAwareCharacterEffect.State state =
                illuga.captureCharacterState();
        assertTrue(!other.acceptsCharacterState(state),
                "Illuga rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreCharacterState(state,
                        simulatorWith(other)),
                "Illuga restore rejects another instance's state");

        Illuga reused = new Illuga(null, null, 0);
        CombatSimulator first = simulatorWith(reused);
        reused.initializeForSimulator(first);
        CombatSimulator second = new CombatSimulator();
        second.setLoggingEnabled(false);
        second.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> second.addCharacter(reused),
                "Illuga rejects cross-simulator reuse");
    }

    private static AttackAction probe(
            String name,
            Element element,
            boolean lunarCrystallize) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                StatType.GEO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        if (lunarCrystallize) {
            action.setLunarReactionType(
                    AttackAction.LunarReactionType.CRYSTALLIZE);
        }
        return action;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
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
                CharacterId.ILLUGA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.ILLUGA,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureIllugaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ILLUGA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureGeoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> normalStage(
            List<ActionRecord> records,
            int step) {
        String prefix = "Oathkeeper's Spear N" + (step + 1);
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
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
            assertTrue(lines.get(index).startsWith("Illuga,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Illuga/Illuga_Status.csv",
                "config/characters/Illuga/Illuga_Multipliers.csv"
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
        throw new AssertionError("Illuga CSVs missing key " + key);
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
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertTrue(
            boolean condition,
            String message) {
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
        private final boolean lunar;

        private TestCharacter(
                CharacterId id,
                Element characterElement,
                boolean lunar) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            this.lunar = lunar;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public boolean isLunarCharacter() {
            return lunar;
        }
    }
}
