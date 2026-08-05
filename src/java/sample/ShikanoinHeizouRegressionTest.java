package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.ShikanoinHeizou;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused regression checks for Heizou's fixed-target Anemo kit. */
public final class ShikanoinHeizouRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ShikanoinHeizouRegressionTest() {
    }

    /** Runs identity, action, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicAttackString();
        testA1PressSkillAndA4();
        testHoldC3C6AndParticles();
        testC1SwitchBoundary();
        testBurstIrisC4C5AndRestore();
        testGuards();
        System.out.println("ShikanoinHeizouRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        ShikanoinHeizou heizou = new ShikanoinHeizou(null, null, 6);
        assertEquals(CharacterId.SHIKANOIN_HEIZOU,
                heizou.getCharacterId(), "Heizou typed identity");
        assertEquals(CharacterId.SHIKANOIN_HEIZOU,
                CharacterId.fromName("Shikanoin Heizou"),
                "Heizou name lookup");
        assertEquals(CharacterId.SHIKANOIN_HEIZOU,
                CharacterId.fromNumericId(49), "Heizou numeric lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.SHIKANOIN_HEIZOU.getRegion(), "Heizou region");
        assertEquals(Element.ANEMO, heizou.getElement(), "Heizou element");
        assertClose(10657.0,
                heizou.getBaseStats().get(StatType.BASE_HP),
                "Heizou base HP");
        assertClose(225.0,
                heizou.getBaseStats().get(StatType.BASE_ATK),
                "Heizou base ATK");
        assertClose(684.0,
                heizou.getBaseStats().get(StatType.BASE_DEF),
                "Heizou base DEF");
        assertClose(0.24,
                heizou.getBaseStats().get(StatType.ANEMO_DMG_BONUS),
                "Heizou ascension Anemo DMG");
        assertClose(40.0, heizou.getEnergyCost(), "Heizou Energy cost");
        assertClose(10.0, heizou.getSkillCD(), "Heizou Skill cooldown");
        assertClose(12.0, heizou.getBurstCD(), "Heizou Burst cooldown");
        assertTrue(!heizou.isC2SuctionRepresented(),
                "Heizou C2 suction remains explicitly excluded");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.SHIKANOIN_HEIZOU,
                    new ShikanoinHeizou(null, null, constellation)
                            .getCharacterId(),
                    "Heizou explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/ShikanoinHeizou/"
                        + "ShikanoinHeizou_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/ShikanoinHeizou/"
                        + "ShikanoinHeizou_Multipliers.csv"), 23);
        assertCsvValue("Declension Per Stack C3", 1.137600);
        assertCsvValue("Windmuster Kick C5", 6.293760);
        assertCsvValue("Windmuster Iris C5", 0.429120);
    }

    private static void testBasicAttackString() {
        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 0, () -> 0.5);
        CombatSimulator simulator = simulatorWith(heizou);
        List<ActionRecord> records = captureActions(simulator);
        double[][] multipliers = {
            { 0.637051 }, { 0.626484 }, { 0.868020 },
            { 0.251301, 0.276434, 0.326699 }, { 1.044643 }
        };
        int[] durations = { 21, 21, 46, 38, 66 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (double multiplier : multipliers[step]) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multiplier, record.action.getDamagePercent(),
                        "Heizou Normal multiplier");
                assertEquals(Element.ANEMO, record.action.getElement(),
                        "Heizou Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Heizou Normal category");
                assertEquals(ICDType.Standard,
                        record.action.getICDType(),
                        "Heizou Normal standard ICD");
            }
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Heizou Normal recovery");
        }
        double chargedStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Fudou Style Martial Arts Charged Attack").get(0);
        assertClose(chargedStart + 24.0 * FRAME, charged.time,
                "Heizou Charged hitmark");
        assertClose(1.241000, charged.action.getDamagePercent(),
                "Heizou Charged multiplier");
        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Fudou Style Martial Arts High Plunge").get(0);
        assertClose(plungeStart + 47.0 * FRAME, plunge.time,
                "Heizou High Plunge hitmark");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Heizou High Plunge multiplier");
    }

    private static void testA1PressSkillAndA4() {
        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 0, () -> 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(heizou, ally);
        List<ActionRecord> records = captureActions(simulator);
        ReactionResult swirl = swirl(Element.PYRO);
        heizou.onReaction(swirl, heizou, 0.0, simulator);
        heizou.onReaction(swirl, heizou, 0.1 - EPSILON, simulator);
        assertEquals(1, heizou.getDeclensionStacks(),
                "Heizou A1 blocks before 0.1 seconds");
        heizou.onReaction(swirl, heizou, 0.1, simulator);
        assertEquals(2, heizou.getDeclensionStacks(),
                "Heizou A1 reopens at exact 0.1 seconds");
        assertClose(0.2, heizou.getNextA1Time(),
                "Heizou A1 gate advances from accepted Swirl");

        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        double castTime = simulator.getCurrentTime();
        performSkill(simulator, SkillActionMode.PRESS);
        ActionRecord skill = named(records, "Heartstopper Strike").get(0);
        assertClose(castTime + 20.0 * FRAME, skill.time,
                "Heizou Press Skill hitmark");
        assertClose(3.867840 + 2.0 * 0.966960,
                skill.action.getDamagePercent(),
                "Heizou Press Skill consumes live Declension");
        assertEquals(0, heizou.getDeclensionStacks(),
                "Heizou Skill callback consumes reaction-time Declension");
        assertClose(80.0,
                effectiveStats(simulator, ally).get(
                        StatType.ELEMENTAL_MASTERY),
                "Heizou A4 grants non-owner party EM");
        assertClose(0.0,
                effectiveStats(simulator, heizou).get(
                        StatType.ELEMENTAL_MASTERY),
                "Heizou A4 excludes its owner");
        assertClose(10.0 - 21.0 * FRAME,
                heizou.getSkillCDRemaining(simulator.getCurrentTime()),
                "Heizou Skill cooldown starts at release frame");
    }

    private static void testHoldC3C6AndParticles() {
        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 6, () -> 0.25);
        CombatSimulator simulator = simulatorWith(heizou);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        double castTime = simulator.getCurrentTime();
        performSkill(simulator, SkillActionMode.HOLD);
        ActionRecord skill = named(records,
                "Heartstopper Strike (Max Stacks)").get(0);
        assertClose(castTime + 200.0 * FRAME, skill.time,
                "Heizou four-stack Hold Skill hitmark");
        assertClose(4.550400 + 4.0 * 1.137600 + 2.275200,
                skill.action.getDamagePercent(),
                "Heizou C3 Hold Skill multiplier");
        assertClose(0.21,
                skill.action.getStatSnapshot().get(StatType.CRIT_RATE),
                "Heizou C6 adds four-stack CRIT Rate to Skill only");
        assertClose(0.82,
                skill.action.getStatSnapshot().get(StatType.CRIT_DMG),
                "Heizou C6 adds Conviction CRIT DMG");
        advanceTo(simulator, skill.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Heizou max-stack Skill emits one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Heizou max-stack Skill emits three particles");

        ShikanoinHeizou fullStack = new ShikanoinHeizou(
                null, null, 0, () -> 0.5);
        CombatSimulator fullStackSimulator = simulatorWith(fullStack);
        List<ActionRecord> fullStackRecords = captureActions(
                fullStackSimulator);
        ReactionResult swirl = swirl(Element.PYRO);
        for (int index = 0; index < 4; index++) {
            fullStack.onReaction(
                    swirl,
                    fullStack,
                    index * 0.1,
                    fullStackSimulator);
        }
        double fullStackCast = fullStackSimulator.getCurrentTime();
        performSkill(fullStackSimulator, SkillActionMode.HOLD);
        ActionRecord penalized = named(
                fullStackRecords,
                "Heartstopper Strike (Max Stacks)").get(0);
        assertClose(fullStackCast + 37.0 * FRAME, penalized.time,
                "Heizou full-stack Hold retains 17-frame release penalty");
        assertClose(fullStackCast + 56.0 * FRAME,
                fullStackSimulator.getCurrentTime(),
                "Heizou full-stack Hold recovery includes release penalty");
    }

    private static void testC1SwitchBoundary() {
        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 1, () -> 0.5);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(heizou, ally);
        simulator.switchCharacter(CharacterId.COLLEI);
        simulator.switchCharacter(CharacterId.SHIKANOIN_HEIZOU);
        assertEquals(1, heizou.getDeclensionStacks(),
                "Heizou C1 grants one Declension on entry");
        assertClose(0.15,
                effectiveStats(simulator, heizou).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Heizou C1 grants Normal Attack speed");
        double nextC1 = heizou.getNextC1Time();
        simulator.switchCharacter(CharacterId.COLLEI);
        simulator.switchCharacter(CharacterId.SHIKANOIN_HEIZOU);
        assertEquals(1, heizou.getDeclensionStacks(),
                "Heizou C1 blocks re-entry before ten seconds");
        advanceTo(simulator, nextC1);
        assertClose(0.0,
                effectiveStats(simulator, heizou).get(
                        StatType.NORMAL_ATTACK_SPD),
                "Heizou C1 speed expires before gate reopens");
        simulator.switchCharacter(CharacterId.COLLEI);
        simulator.switchCharacter(CharacterId.SHIKANOIN_HEIZOU);
        assertEquals(2, heizou.getDeclensionStacks(),
                "Heizou C1 reopens at exact ten-second gate");
    }

    private static void testBurstIrisC4C5AndRestore() {
        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 5, () -> 0.5);
        CombatSimulator simulator = simulatorWith(heizou);
        List<ActionRecord> records = captureActions(simulator);
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(9.0, heizou.getCurrentEnergy(),
                "Heizou C4 restores nine Energy for first valid Iris");
        assertEquals(Element.PYRO, heizou.getLastIrisElement(),
                "Heizou Burst captures live Pyro aura before Anemo impact");
        ActionRecord burst = named(records,
                "Fudou Style Vacuum Slugger").get(0);
        assertClose(castTime + 34.0 * FRAME, burst.time,
                "Heizou Burst hitmark");
        assertClose(6.293760, burst.action.getDamagePercent(),
                "Heizou C5 Burst multiplier");
        SimulatorSnapshot pendingIris = simulator.saveSnapshot();
        simulator.advanceTime(2.0 * FRAME + EPSILON);
        ActionRecord iris = named(records, "Windmuster Iris PYRO").get(0);
        assertClose(castTime + 74.0 * FRAME, iris.time,
                "Heizou Iris delay");
        assertClose(0.429120, iris.action.getDamagePercent(),
                "Heizou C5 Iris multiplier");
        simulator.restoreSnapshot(pendingIris);
        simulator.advanceTime(2.0 * FRAME + EPSILON);
        assertEquals(2, named(records, "Windmuster Iris PYRO").size(),
                "Heizou snapshot reconstructs pending Iris once");

        ShikanoinHeizou unsupported = new ShikanoinHeizou(
                null, null, 4, () -> 0.5);
        CombatSimulator unsupportedSimulator = simulatorWith(unsupported);
        unsupportedSimulator.getEnemy().setAura(Element.DENDRO, 20.0);
        perform(unsupportedSimulator, CharacterActionKey.BURST);
        assertEquals(null, unsupported.getLastIrisElement(),
                "Heizou omits Iris for unsupported aura");
        assertClose(0.0, unsupported.getCurrentEnergy(),
                "Heizou C4 grants no Energy without Iris");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShikanoinHeizou(null, null, -1),
                "Heizou rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new ShikanoinHeizou(null, null, 7),
                "Heizou rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new ShikanoinHeizou(null, null, 0, null),
                "Heizou rejects null particle draw source");

        ShikanoinHeizou heizou = new ShikanoinHeizou(
                null, null, 0, () -> 0.5);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator simulator = simulatorWith(heizou, ally);
        assertThrows(IllegalArgumentException.class,
                () -> heizou.onAction(null, simulator),
                "Heizou rejects null action");
        heizou.restoreCurrentEnergy(0.0);
        double beforeBurst = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(beforeBurst, simulator.getCurrentTime(),
                "Heizou skips Burst without Energy");
        assertClose(40.0, heizou.getMissedBurstCost(),
                "Heizou records missed Burst Energy");

        ReactionResult swirl = swirl(Element.HYDRO);
        heizou.onReaction(null, heizou, 0.0, simulator);
        heizou.onReaction(swirl, ally, 0.0, simulator);
        heizou.onReaction(swirl, heizou, 0.0, new CombatSimulator());
        simulator.switchCharacter(CharacterId.COLLEI);
        heizou.onReaction(swirl, heizou, 0.0, simulator);
        assertEquals(0, heizou.getDeclensionStacks(),
                "Heizou A1 rejects invalid and off-field reactions");

        ShikanoinHeizou external = new ShikanoinHeizou(
                null, null, 0, () -> 0.5);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Heizou rejects binding outside simulator party");
        ShikanoinHeizou reused = new ShikanoinHeizou(
                null, null, 0, () -> 0.5);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Heizou rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!heizou.acceptsCharacterState(foreignState),
                "Heizou rejects another instance snapshot payload");

        ShikanoinHeizou invalidDraw = new ShikanoinHeizou(
                null, null, 0, () -> 1.1);
        CombatSimulator invalidSimulator = simulatorWith(invalidDraw);
        ReactionResult pyroSwirl = swirl(Element.PYRO);
        invalidDraw.onReaction(pyroSwirl, invalidDraw, 0.0,
                invalidSimulator);
        invalidDraw.onReaction(pyroSwirl, invalidDraw, 0.1,
                invalidSimulator);
        assertThrows(IllegalStateException.class,
                () -> performSkill(invalidSimulator, SkillActionMode.PRESS),
                "Heizou rejects particle draws outside [0, 1]");
    }

    private static ReactionResult swirl(Element element) {
        return ReactionResult.transform(
                0.0,
                element.name() + " Swirl",
                ReactionResult.Kind.SWIRL,
                element);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
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
                CharacterId.SHIKANOIN_HEIZOU,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.SHIKANOIN_HEIZOU,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.SHIKANOIN_HEIZOU) {
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
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Shikanoin Heizou,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/ShikanoinHeizou/"
                        + "ShikanoinHeizou_Status.csv",
                "config/characters/ShikanoinHeizou/"
                        + "ShikanoinHeizou_Multipliers.csv"
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
        throw new AssertionError("Heizou CSVs missing key " + key);
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

        private ParticleRecord(double count, double time) {
            this.count = count;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public double getEnergyCost() {
            return 80.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
