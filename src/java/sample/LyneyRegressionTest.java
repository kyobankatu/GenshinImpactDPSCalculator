package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Lyney;
import model.character.Xiangling;
import model.entity.Character;
import model.entity.Enemy;
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

/** Focused checks for Lyney's Prop Arrow and Grin-Malkin slice. */
public final class LyneyRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private LyneyRegressionTest() {
    }

    /** Runs identity, Hat, Skill, Burst, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testNormalAndPropArrowFlow();
        testC1SkillC3C4AndC6();
        testBurstC2C5AndA4();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("LyneyRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Lyney lyney = lyney(0);
        assertEquals(CharacterId.LYNEY, lyney.getCharacterId(),
                "Lyney typed identity");
        assertEquals(CharacterId.LYNEY, CharacterId.fromNumericId(82),
                "Lyney numeric identity");
        assertEquals(CharacterId.LYNEY, CharacterId.fromName("Lyney"),
                "Lyney name identity");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.LYNEY.getRegion(), "Lyney region");
        assertEquals(Element.PYRO, lyney.getElement(), "Lyney element");
        assertClose(11021.0,
                lyney.getBaseStats().get(StatType.BASE_HP),
                "Lyney base HP");
        assertClose(318.0,
                lyney.getBaseStats().get(StatType.BASE_ATK),
                "Lyney base ATK");
        assertClose(538.0,
                lyney.getBaseStats().get(StatType.BASE_DEF),
                "Lyney base DEF");
        assertClose(0.242,
                lyney.getBaseStats().get(StatType.CRIT_RATE),
                "Lyney total base CRIT Rate");
        assertClose(60.0, lyney.getEnergyCost(), "Lyney Energy cost");
        assertClose(15.0, lyney.getSkillCD(), "Lyney Skill cooldown");
        assertClose(15.0, lyney.getBurstCD(), "Lyney Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation, lyney(constellation).getConstellation(),
                    "Lyney constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Lyney/Lyney_Status.csv"), 20);
        assertCsvShape(Path.of(
                "config/characters/Lyney/Lyney_Multipliers.csv"), 22);
        assertThrows(IllegalArgumentException.class,
                () -> lyney(-1), "Lyney rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> lyney(7), "Lyney rejects constellation above C6");

        CombatSimulator simulator = simulatorWith(lyney);
        assertThrows(IllegalArgumentException.class,
                () -> lyney.onAction(null, simulator),
                "Lyney rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.LYNEY,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Lyney rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Lyney rejects unsupported Plunge");
    }

    private static void testNormalAndPropArrowFlow() {
        Lyney lyney = lyney(0);
        CombatSimulator simulator = simulatorWith(lyney);
        List<ActionRecord> records = captureActions(simulator);
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(5,
                startingWith(records, "Card Force Translocation N").size(),
                "Lyney Normal string hit count");
        assertClose(0.500860,
                startingWith(records, "Card Force Translocation N3")
                        .get(1).action.getDamagePercent(),
                "Lyney N3 second multiplier");

        records.clear();
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        advanceTo(simulator, castTime + 2.7);
        ActionRecord prop = named(
                records, "Card Force Translocation Prop Arrow").get(0);
        assertClose(castTime + 113.0 * FRAME, prop.time,
                "Lyney Prop Arrow impact timing");
        assertClose(2.937600, prop.action.getDamagePercent(),
                "Lyney Prop Arrow multiplier");
        assertClose(0.12,
                prop.action.getHitlagProfile().getHaltTimeSeconds(),
                "Lyney Prop Arrow headshot hitlag halt time");
        assertClose(0.01,
                prop.action.getHitlagProfile().getFactor(),
                "Lyney Prop Arrow headshot hitlag factor");
        assertTrue(!prop.action.getHitlagProfile().canDefenseHalt(),
                "Lyney Prop Arrow headshot omits Defense Halt");
        assertTrue(prop.action.getHitlagProfile().isDeployable(),
                "Lyney Prop Arrow headshot hitlag is deployable");
        assertTrue(prop.action.getHitlagProfile().isHeadshotOnly(),
                "Lyney Prop Arrow metadata is headshot-only");
        assertEquals(1, lyney.getActiveHatCount(),
                "Lyney C0 Prop Arrow creates one Hat");
        assertEquals(0, lyney.getPropSurplusStacks(),
                "Lyney HP-dependent stack fails closed");
        assertEquals(1, named(records, "Spiritbreath Thorn").size(),
                "Lyney Prop Arrow schedules one Arkhe-labelled damage hit");
        advanceTo(simulator, castTime + 7.0);
        assertEquals(1, named(records, "Pyrotechnic Strike").size(),
                "Lyney Hat expires into one Pyrotechnic Strike");
        assertEquals(0, lyney.getActiveHatCount(),
                "Lyney removes naturally expired Hat");
    }

    private static void testC1SkillC3C4AndC6() {
        Lyney c1 = lyney(1);
        CombatSimulator simulator = simulatorWith(c1);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        advanceTo(simulator, 2.0);
        assertEquals(2, c1.getActiveHatCount(),
                "Lyney C1 first Prop Arrow creates two Hats");
        assertEquals(1, c1.getPropSurplusStacks(),
                "Lyney C1 grants one non-HP stack");
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(records, "Bewildering Lights").get(0);
        assertClose(2.842400 + 0.904400,
                skill.action.getDamagePercent(),
                "Lyney Skill consumes one Prop Surplus stack");
        assertEquals(0, c1.getPropSurplusStacks(),
                "Lyney Skill clears Prop Surplus");
        assertEquals(0, c1.getActiveHatCount(),
                "Lyney Skill detonates both Hats");
        assertEquals(2, named(
                records, "Pyrotechnic Strike (Skill)").size(),
                "Lyney Skill resolves both Hat strikes");
        assertEquals(1, particles.size(),
                "Lyney Skill emits one particle packet");
        assertClose(5.0, particles.get(0),
                "Lyney Skill emits five Pyro particles");

        Lyney c3 = lyney(3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.CHARGE);
        advanceTo(c3Simulator, 2.0);
        assertClose(3.456000,
                named(c3Records, "Card Force Translocation Prop Arrow")
                        .get(0).action.getDamagePercent(),
                "Lyney C3 raises Prop Arrow talent");

        Lyney c4 = lyney(4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        perform(c4Simulator, CharacterActionKey.CHARGE);
        advanceTo(c4Simulator, 2.0);
        assertClose(0.2,
                c4.getEffectiveStats(c4Simulator.getCurrentTime())
                        .get(StatType.PYRO_RES_SHRED),
                "Lyney C4 applies six-second Pyro RES shred");

        Lyney c6 = lyney(6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.CHARGE);
        advanceTo(c6Simulator, 2.0);
        assertClose(4.240000 * 0.8,
                named(c6Records, "Pyrotechnic Strike: Reprised (C6)")
                        .get(0).action.getDamagePercent(),
                "Lyney C6 Reprised multiplier");
    }

    private static void testBurstC2C5AndA4() {
        Lyney c2 = lyney(2);
        Xiangling xiangling = new Xiangling(null, null);
        CombatSimulator simulator = simulatorWith(c2, xiangling);
        simulator.getEnemy().setAura(Element.PYRO, 10.0);
        simulator.advanceTime(6.0);
        assertEquals(3, c2.getCrispFocusStacks(),
                "Lyney C2 reaches three stacks after six seconds on field");
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction normal = startingWith(
                records, "Card Force Translocation N1").get(0).action;
        assertClose(0.6,
                normal.getExtraBonuses().get(StatType.CRIT_DMG),
                "Lyney C2 grants sixty percent CRIT DMG");
        assertClose(0.8,
                normal.getExtraBonuses().get(StatType.DMG_BONUS_ALL),
                "Lyney A4 counts one other Pyro party member");

        c2.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(c2.isBurstActive(simulator.getCurrentTime()),
                "Lyney Burst form begins at frame one hundred");
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(!c2.isBurstActive(simulator.getCurrentTime()),
                "Lyney Skill ends Burst form early");
        assertEquals(1, c2.getPropSurplusStacks(),
                "Lyney Burst finish grants one Prop Surplus");
        assertEquals(1, c2.getActiveHatCount(),
                "Lyney Burst finish creates one Hat");
        assertEquals(1, named(
                records, "Wondrous Trick: Explosive Firework").size(),
                "Lyney early Burst finish emits Firework");

        Lyney c5 = lyney(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        c5.restoreCurrentEnergy(60.0);
        perform(c5Simulator, CharacterActionKey.BURST);
        advanceTo(c5Simulator, 2.0);
        assertClose(3.080000,
                named(c5Records, "Wondrous Trick: Miracle Parade")
                        .get(0).action.getDamagePercent(),
                "Lyney C5 raises Burst collision talent");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Lyney lyney = lyney(1);
        CombatSimulator simulator = simulatorWith(lyney);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.CHARGE);
        advanceTo(simulator, 2.0);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 7.0);
        int expectedHits = named(records, "Pyrotechnic Strike").size();
        double expectedDamage = simulator.getTotalDamage();
        assertEquals(2, expectedHits,
                "Lyney original C1 branch resolves both Hat expiries");

        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 7.0);
        assertEquals(expectedHits,
                named(records, "Pyrotechnic Strike").size(),
                "Lyney restore resolves each future Hat strike once");
        assertClose(expectedDamage, simulator.getTotalDamage(),
                "Lyney restore preserves total damage");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 7.0);
        assertEquals(expectedHits,
                named(records, "Pyrotechnic Strike").size(),
                "Lyney repeated restore keeps one Hat sequence");

        Lyney foreign = lyney(0);
        assertTrue(!lyney.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Lyney rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> lyney.restoreCharacterState(null, simulator),
                "Lyney rejects null state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(lyney),
                "Lyney rejects cross-simulator reuse");
    }

    private static Lyney lyney(int constellation) {
        return new Lyney(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.LYNEY, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.LYNEY) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> capturePyroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
                records.add(count);
            }
        });
        return records;
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

    private static List<ActionRecord> startingWith(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                matches.add(record);
            }
        }
        return matches;
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
            assertTrue(lines.get(index).startsWith("Lyney,"),
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

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
