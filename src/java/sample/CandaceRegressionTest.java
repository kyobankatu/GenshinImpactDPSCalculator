package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Candace;
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
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Candace's fixed-target Crimson Crown kit. */
public final class CandaceRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private CandaceRegressionTest() {
    }

    /** Runs identity, action, support, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalBasics();
        testPressHoldParticlesC2C4C5AndRestore();
        testBurstSupportSwitchWaveAndC1C3();
        testC6ElementalNormalGate();
        testGuards();
        System.out.println("CandaceRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction() throws IOException {
        Candace candace = new Candace(null, null, 6);
        assertEquals(CharacterId.CANDACE, candace.getCharacterId(),
                "Candace typed identity");
        assertEquals(CharacterId.CANDACE, CharacterId.fromName("Candace"),
                "Candace name lookup");
        assertEquals(CharacterId.CANDACE, CharacterId.fromNumericId(51),
                "Candace numeric lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.CANDACE.getRegion(), "Candace region");
        assertEquals(Element.HYDRO, candace.getElement(), "Candace element");
        assertClose(10875.0,
                candace.getBaseStats().get(StatType.BASE_HP),
                "Candace base HP");
        assertClose(212.0,
                candace.getBaseStats().get(StatType.BASE_ATK),
                "Candace base ATK");
        assertClose(682.0,
                candace.getBaseStats().get(StatType.BASE_DEF),
                "Candace base DEF");
        assertClose(0.24,
                candace.getBaseStats().get(StatType.HP_PERCENT),
                "Candace ascension HP");
        assertClose(60.0, candace.getEnergyCost(), "Candace Energy cost");
        assertClose(6.0, candace.getSkillCD(), "Candace base Skill CD");
        assertClose(15.0, candace.getBurstCD(), "Candace Burst CD");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.CANDACE,
                    new Candace(null, null, constellation).getCharacterId(),
                    "Candace explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Candace/Candace_Status.csv"), 16);
        assertCsvShape(Path.of(
                "config/characters/Candace/Candace_Multipliers.csv"), 15);
        assertCsvValue("N3 Hit 2", 0.796873);
        assertCsvValue("Heron's Sanctum Hold C5", 0.380800);
        assertCsvValue("Wagtail Initial C3", 0.132208);
    }

    private static void testPhysicalBasics() {
        Candace candace = new Candace(null, null, 0);
        CombatSimulator simulator = simulatorWith(candace);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            1.117060, 1.123380, 0.651987, 0.796873, 1.744320
        };
        int record = 0;
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
            int hits = step == 2 ? 2 : 1;
            for (int hit = 0; hit < hits; hit++) {
                assertClose(multipliers[record],
                        records.get(record).action.getDamagePercent(),
                        "Candace Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        records.get(record).action.getElement(),
                        "Candace physical Normal element");
                record++;
            }
        }
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        assertClose(2.281520, named(records,
                "Gleaming Spear Charged Attack").get(0)
                        .action.getDamagePercent(),
                "Candace Charged multiplier");
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.PLUNGE));
        assertClose(2.933586, named(records,
                "Gleaming Spear High Plunge").get(0)
                        .action.getDamagePercent(),
                "Candace high Plunge multiplier");
    }

    private static void testPressHoldParticlesC2C4C5AndRestore() {
        Candace press = new Candace(null, null, 0);
        CombatSimulator pressSimulator = simulatorWith(press);
        List<ActionRecord> pressRecords = captureActions(pressSimulator);
        List<ParticleRecord> pressParticles = captureHydroParticles(
                pressSimulator);
        double castTime = pressSimulator.getCurrentTime();
        perform(pressSimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        ActionRecord pressHit = named(pressRecords,
                "Sacred Rite: Heron's Sanctum").get(0);
        assertClose(castTime + 16.0 * FRAME, pressHit.time,
                "Candace Press hitmark");
        assertClose(0.204000, pressHit.action.getDamagePercent(),
                "Candace Press Talent 9");
        assertClose(5.8,
                press.getSkillCDRemaining(pressSimulator.getCurrentTime()),
                "Candace Press CD starts at frame 14");
        SimulatorSnapshot pendingParticle = pressSimulator.saveSnapshot();
        advanceTo(pressSimulator, castTime + 116.0 * FRAME + EPSILON);
        assertClose(2.0, pressParticles.get(0).count,
                "Candace Press emits two particles");
        pressSimulator.restoreSnapshot(pendingParticle);
        advanceTo(pressSimulator, castTime + 116.0 * FRAME + EPSILON);
        assertEquals(2, pressParticles.size(),
                "Candace restores one pending particle packet");

        Candace hold = new Candace(null, null, 5);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureActions(holdSimulator);
        List<ParticleRecord> holdParticles = captureHydroParticles(
                holdSimulator);
        perform(holdSimulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        ActionRecord holdHit = named(holdRecords,
                "Sacred Rite: Heron's Sanctum (Charged)").get(0);
        assertClose(0.380800, holdHit.action.getDamagePercent(),
                "Candace C5 Hold Talent 12");
        assertClose(5.6,
                hold.getSkillCDRemaining(holdSimulator.getCurrentTime()),
                "Candace C4 reduces Hold CD to six seconds");
        assertClose(0.44,
                effectiveStats(holdSimulator, hold).get(
                        StatType.HP_PERCENT),
                "Candace C2 adds 20% Max HP");
        advanceTo(holdSimulator, 191.0 * FRAME + EPSILON);
        assertClose(3.0, holdParticles.get(0).count,
                "Candace Hold emits three particles");

        Candace c0Hold = new Candace(null, null, 0);
        CombatSimulator c0HoldSimulator = simulatorWith(c0Hold);
        perform(c0HoldSimulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertClose(8.6,
                c0Hold.getSkillCDRemaining(
                        c0HoldSimulator.getCurrentTime()),
                "Candace C0 Hold keeps nine-second CD");
    }

    private static void testBurstSupportSwitchWaveAndC1C3() {
        Candace candace = new Candace(null, null, 3);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(candace, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> allyDamage = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == ally) {
                allyDamage.add(damage);
            }
        });
        simulator.registerEvent(new SimpleTimerEvent(10.0 * FRAME, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                candace.addBuff(new SimpleBuff(
                        "Candace snapshot probe",
                        20.0,
                        activeSimulator.getCurrentTime(),
                        stats -> stats.add(StatType.HP_PERCENT, 0.50)));
            }
        });
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        ActionRecord initial = named(records,
                "Sacred Rite: Wagtail's Tide (Initial)").get(0);
        assertClose(castTime + 33.0 * FRAME, initial.time,
                "Candace Burst hitmark");
        assertClose(0.132208, initial.action.getDamagePercent(),
                "Candace C3 Burst Talent 12");
        assertClose(13485.0,
                initial.action.getStatSnapshot().getTotalHp(),
                "Candace Burst damage retains cast-time Max HP");
        assertClose(0.0, candace.getCurrentEnergy(),
                "Candace Burst consumes Energy at frame four");
        assertTrue(candace.isCrimsonCrownActive(
                initial.time + 12.0 - EPSILON),
                "Candace C1 Crown remains active before twelve seconds");
        assertTrue(!candace.isCrimsonCrownActive(initial.time + 12.0),
                "Candace C1 Crown expires at twelve seconds");
        assertClose(0.2946125,
                effectiveStats(simulator, ally).get(
                        StatType.ELEMENTAL_NORMAL_ATTACK_DMG_BONUS),
                "Candace base and A4 Normal support");
        assertClose(0.0,
                effectiveStats(simulator, ally).get(
                        StatType.NORMAL_ATTACK_DMG_BONUS),
                "Candace Crown does not use unconditional Normal bonus");
        double switchTime = simulator.getCurrentTime();
        simulator.switchCharacter(CharacterId.COLLEI);
        advanceTo(simulator, switchTime + FRAME + EPSILON);
        assertEquals(1, named(records,
                "Sacred Rite: Wagtail's Tide (Wave)").size(),
                "Candace switch-out queues one represented wave");
        performTestNormal(simulator, ally, Element.PHYSICAL);
        performTestNormal(simulator, ally, Element.HYDRO);
        assertTrue(allyDamage.get(1) > allyDamage.get(0),
                "Candace Crown buffs elemental but not Physical Normals");
    }

    private static void testC6ElementalNormalGate() {
        Candace candace = new Candace(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(candace, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        simulator.switchCharacter(CharacterId.COLLEI);
        double firstTriggerTime = simulator.getCurrentTime();
        performTestNormal(simulator, ally, Element.HYDRO);
        advanceTo(simulator, simulator.getCurrentTime() + FRAME + EPSILON);
        assertEquals(1, named(records, "The Overflow (C6)").size(),
                "Candace C6 accepts ally elemental Normal");
        performTestNormal(simulator, ally, Element.HYDRO);
        advanceTo(simulator, simulator.getCurrentTime() + FRAME + EPSILON);
        assertEquals(1, named(records, "The Overflow (C6)").size(),
                "Candace C6 blocks before 2.3 seconds");
        advanceTo(simulator, firstTriggerTime + 2.3);
        performTestNormal(simulator, ally, Element.PHYSICAL);
        assertEquals(1, named(records, "The Overflow (C6)").size(),
                "Candace C6 rejects Physical Normal");
        performTestNormal(simulator, ally, Element.HYDRO);
        advanceTo(simulator, simulator.getCurrentTime() + FRAME + EPSILON);
        assertEquals(2, named(records, "The Overflow (C6)").size(),
                "Candace C6 reopens after 2.3 seconds");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Candace(null, null, -1),
                "Candace rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Candace(null, null, 7),
                "Candace rejects constellation above C6");
        Candace candace = new Candace(null, null, 0);
        CombatSimulator simulator = simulatorWith(candace);
        assertThrows(IllegalArgumentException.class,
                () -> candace.onAction(null, simulator),
                "Candace rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.of(
                        CharacterActionKey.DASH)),
                "Candace rejects unsupported action");
        candace.restoreCurrentEnergy(0.0);
        double beforeBurst = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        assertClose(beforeBurst, simulator.getCurrentTime(),
                "Candace skips Burst without Energy");
        assertClose(60.0, candace.getMissedBurstCost(),
                "Candace records missed Burst Energy");

        Candace external = new Candace(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Candace rejects binding outside simulator party");
        Candace reused = new Candace(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Candace rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!candace.acceptsCharacterState(foreignState),
                "Candace rejects another instance snapshot payload");
    }

    private static void performTestNormal(
            CombatSimulator simulator,
            Character actor,
            Element element) {
        AttackAction action = new AttackAction(
                "Candace C6 Test Normal",
                1.0,
                element,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
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
        simulator.performAction(CharacterId.CANDACE, request);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CANDACE) {
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
                records.add(new ParticleRecord(count));
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
            assertTrue(lines.get(index).startsWith("Candace,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Candace/Candace_Status.csv",
                "config/characters/Candace/Candace_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected, Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Candace CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected)
                == Double.doubleToLongBits(actual)) {
            return;
        }
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
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
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

        private ParticleRecord(double count) {
            this.count = count;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return null;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}
