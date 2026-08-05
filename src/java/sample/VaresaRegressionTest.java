package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import mechanics.element.ICDManager;
import model.character.Varesa;
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

/** Focused regression checks for Varesa's bounded Fiery Passion slice. */
public final class VaresaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private VaresaRegressionTest() {
    }

    /** Runs data, action, state, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testCatalystBasicsHighPlungeAndIcd();
        testSkillChargesParticlesAndFieryFlow();
        testBurstApexAndConstellations();
        testPassivesSnapshotAndFailClosedBoundaries();
        System.out.println("VaresaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Varesa varesa = new Varesa(null, null, 6);
        assertEquals(CharacterId.VARESA, varesa.getCharacterId(),
                "Varesa typed identity");
        assertEquals(CharacterId.VARESA, CharacterId.fromName("Varesa"),
                "Varesa name lookup");
        assertEquals(CharacterId.VARESA, CharacterId.fromNumericId(98),
                "Varesa numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.VARESA.getRegion(), "Varesa region");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(97),
                "Unassigned adjacent numeric ID");
        assertEquals(Element.ELECTRO, varesa.getElement(),
                "Varesa element");
        assertClose(12699.0,
                varesa.getBaseStats().get(StatType.BASE_HP),
                "Varesa base HP");
        assertClose(356.0,
                varesa.getBaseStats().get(StatType.BASE_ATK),
                "Varesa base ATK");
        assertClose(782.0,
                varesa.getBaseStats().get(StatType.BASE_DEF),
                "Varesa base DEF");
        assertClose(0.242,
                varesa.getBaseStats().get(StatType.CRIT_RATE),
                "Varesa total base CRIT Rate");
        assertClose(70.0, varesa.getEnergyCost(),
                "Varesa standard Burst cost");
        assertClose(70.0, varesa.getMaxEnergy(),
                "Varesa Energy maximum");
        assertClose(9.0, varesa.getSkillCD(),
                "Varesa Skill charge cooldown");
        assertClose(18.0, varesa.getBurstCD(),
                "Varesa standard Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.VARESA,
                    new Varesa(null, null, constellation).getCharacterId(),
                    "Varesa explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Varesa/Varesa_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Varesa/Varesa_Multipliers.csv"), 50);
        assertCsvValue("Fiery Passion High Plunge C5", 6.303497);
        assertCsvValue("Volcano Kablam C3", 8.0528);
    }

    private static void testCatalystBasicsHighPlungeAndIcd() {
        Varesa varesa = new Varesa(null, null, 0);
        CombatSimulator simulator = simulatorWith(varesa);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = { 0.795233, 0.680476, 0.957318 };
        double[] hitFrames = { 17.0, 50.0, 106.0 };
        double[] endFrames = { 43.0, 73.0, 132.0 };
        for (int index = 0; index < multipliers.length; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(index);
            assertClose(hitFrames[index] * FRAME, record.time,
                    "Varesa N" + (index + 1) + " impact frame");
            assertClose(endFrames[index] * FRAME,
                    simulator.getCurrentTime(),
                    "Varesa N" + (index + 1) + " duration");
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Varesa N" + (index + 1) + " multiplier");
            assertEquals(Element.ELECTRO, record.action.getElement(),
                    "Varesa catalyst Normal element");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Varesa Normal ICD tag");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = records.get(3);
        assertClose(chargedCast + 69.0 * FRAME, charged.time,
                "Varesa Charged impact frame");
        assertClose(chargedCast + 77.0 * FRAME,
                simulator.getCurrentTime(),
                "Varesa deterministic Plunge transition");
        assertClose(1.51776, charged.action.getDamagePercent(),
                "Varesa Charged multiplier");
        assertEquals(ICDTag.Varesa_CombatCycle,
                charged.action.getICDTag(),
                "Varesa Charged private ICD");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(4);
        assertClose(plungeCast + 37.0 * FRAME, plunge.time,
                "Varesa high Plunge impact frame");
        assertClose(3.422517, plunge.action.getDamagePercent(),
                "Varesa high Plunge multiplier");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Varesa high Plunge has no ICD");
        assertClose(25.0, varesa.getNightsoulPoints(),
                "High Plunge generates 25 local points");

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "Varesa", ICDTag.NormalAttack,
                ICDType.Standard, 0.0),
                "Varesa Normal admits first application");
        assertTrue(manager.checkApplication(
                "Varesa", ICDTag.Varesa_CombatCycle,
                ICDType.Standard, 0.0),
                "Varesa combat-cycle group remains independent");
    }

    private static void testSkillChargesParticlesAndFieryFlow() {
        Varesa varesa = new Varesa(
                null, null, TalentDataManager.getInstance(),
                0, () -> 0.25);
        CombatSimulator simulator = simulatorWith(varesa);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(records, "Rush").get(0);
        assertClose(5.0 * FRAME, skill.time,
                "Varesa Skill impact frame");
        assertClose(43.0 * FRAME, simulator.getCurrentTime(),
                "Varesa Skill duration");
        assertClose(20.0, varesa.getNightsoulPoints(),
                "Varesa Skill generates 20 Nightsoul points");
        assertClose(1.26616, skill.action.getDamagePercent(),
                "Varesa Skill multiplier");
        assertEquals(ICDTag.Varesa_CombatCycle,
                skill.action.getICDTag(), "Varesa Skill private ICD");

        perform(simulator, CharacterActionKey.PLUNGE);
        assertTrue(varesa.isNightsoulActive(),
                "Skill plus high Plunge enters Fiery Passion");
        assertClose(40.0, varesa.getNightsoulPoints(),
                "Fiery Passion enters at the 40-point cap");
        assertTrue(varesa.isFreeSkillAvailable(),
                "Fiery Passion grants one free Skill");
        double cooldownBeforeFree = varesa.getSkillCDRemaining(
                simulator.getCurrentTime());
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(!varesa.isFreeSkillAvailable(),
                "Free Fiery Skill is consumed");
        assertClose(cooldownBeforeFree, varesa.getSkillCDRemaining(
                simulator.getCurrentTime()),
                "Free Skill does not consume another charge");
        ActionRecord fierySkill = named(
                records, "Fiery Passion Rush").get(0);
        assertClose(1.8088, fierySkill.action.getDamagePercent(),
                "Fiery Passion Skill multiplier");

        double fieryPlungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord fieryPlunge = named(
                records, "Fiery Passion High Plunge").get(0);
        assertClose(fieryPlungeCast + 41.0 * FRAME,
                fieryPlunge.time,
                "Fiery Passion high Plunge impact frame");
        assertClose(5.133776,
                fieryPlunge.action.getDamagePercent(),
                "Fiery Passion high Plunge multiplier");
        assertTrue(!varesa.isNightsoulActive(),
                "Fiery high Plunge consumes and exits Nightsoul");
        assertTrue(varesa.isApexDriveActive(),
                "Fiery high Plunge activates Apex Drive");
        assertClose(30.0, varesa.getEnergyCost(),
                "Apex Drive enables the 30-Energy Burst");

        advanceTo(simulator, skill.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Paid Skill emits one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Low random draw emits three particles");
    }

    private static void testBurstApexAndConstellations() {
        Varesa c0 = new Varesa(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        ActionRecord kick = named(c0Records, "Flying Kick").get(0);
        assertClose(88.0 * FRAME, kick.time,
                "Flying Kick impact frame");
        assertClose(5.86704, kick.action.getDamagePercent(),
                "Flying Kick multiplier");
        assertTrue(c0.isNightsoulActive(),
                "Flying Kick enters Fiery Passion at frame three");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Flying Kick spends 70 Energy at frame nine");

        Varesa c2 = new Varesa(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        c2.spendEnergy(70.0);
        perform(c2Simulator, CharacterActionKey.PLUNGE);
        assertTrue(c2.isApexDriveActive(),
                "C2 regular high Plunge activates Apex Drive");
        assertClose(11.5, c2.getCurrentEnergy(),
                "C2 high Plunge restores 11.5 Energy");

        Varesa c3 = new Varesa(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        assertClose(6.9024,
                named(c3Records, "Flying Kick").get(0)
                        .action.getDamagePercent(),
                "C3 raises Flying Kick talent level");

        Varesa c4 = new Varesa(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        perform(c4Simulator, CharacterActionKey.BURST);
        c4.receiveFlatEnergy(70.0);
        c4.reduceBurstCooldown(c4Simulator.getCurrentTime(), 18.0);
        assertTrue(c4.isFreeSkillAvailable(),
                "Initial Flying Kick grants the free Skill");
        perform(c4Simulator, CharacterActionKey.SKILL);
        c4.receiveFlatEnergy(70.0);
        perform(c4Simulator, CharacterActionKey.BURST);
        assertTrue(!c4.isFreeSkillAvailable(),
                "Fiery Flying Kick does not re-grant the free Skill");
        List<ActionRecord> fieryKicks = named(
                c4Records, "Fiery Passion Flying Kick");
        ActionRecord fieryKick = fieryKicks.get(fieryKicks.size() - 1);
        assertClose(1.0,
                bonus(fieryKick.action, StatType.DMG_BONUS_ALL),
                "C4 doubles Burst damage while Fiery Passion is active");

        Varesa c4Plunge = new Varesa(null, null, 4);
        CombatSimulator c4PlungeSimulator = simulatorWith(c4Plunge);
        List<ActionRecord> c4PlungeRecords =
                captureActions(c4PlungeSimulator);
        perform(c4PlungeSimulator, CharacterActionKey.BURST);
        perform(c4PlungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord diligentPlunge = named(
                c4PlungeRecords, "Fiery Passion High Plunge").get(0);
        assertClose(1780.0,
                bonus(diligentPlunge.action, StatType.FLAT_DMG_BONUS),
                "C4 stores 500 percent ATK for the next high Plunge");

        Varesa c6 = new Varesa(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        c6.spendEnergy(70.0);
        perform(c6Simulator, CharacterActionKey.PLUNGE);
        assertClose(41.5, c6.getCurrentEnergy(),
                "C6 Apex and C2 hit restore 41.5 Energy");
        perform(c6Simulator, CharacterActionKey.BURST);
        ActionRecord kablam = named(c6Records, "Volcano Kablam").get(0);
        assertClose(8.0528, kablam.action.getDamagePercent(),
                "C3 raises Volcano Kablam talent level");
        assertClose(0.10,
                bonus(kablam.action, StatType.CRIT_RATE),
                "C6 gives Volcano Kablam CRIT Rate");
        assertClose(1.0,
                bonus(kablam.action, StatType.CRIT_DMG),
                "C6 gives Volcano Kablam CRIT DMG");
        assertClose(640.8,
                bonus(kablam.action, StatType.FLAT_DMG_BONUS),
                "Kablam receives enhanced A1 but not C4 Plunge flat damage");
        assertClose(11.5, c6.getCurrentEnergy(),
                "Volcano Kablam spends 30 Energy");
    }

    private static void testPassivesSnapshotAndFailClosedBoundaries() {
        Varesa c0 = new Varesa(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.SKILL);
        perform(c0Simulator, CharacterActionKey.PLUNGE);
        ActionRecord c0Plunge = named(c0Records, "High Plunge").get(0);
        assertClose(178.0,
                bonus(c0Plunge.action, StatType.FLAT_DMG_BONUS),
                "A1 adds 50 percent live ATK at C0");

        Varesa a4 = new Varesa(null, null, 0);
        CombatSimulator a4Simulator = simulatorWith(a4);
        a4.notifyNightsoulBurst(a4Simulator);
        a4.notifyNightsoulBurst(a4Simulator);
        a4.notifyNightsoulBurst(a4Simulator);
        assertEquals(2, a4.getA4StackCount(),
                "A4 caps at two explicit stacks");
        StatsContainer a4Stats = a4.getEffectiveStats(
                a4Simulator.getCurrentTime());
        assertClose(0.70, a4Stats.get(StatType.ATK_PERCENT),
                "A4 gives 35 percent ATK per stack");
        a4Simulator.advanceTime(12.0);
        assertEquals(0, a4.getA4StackCount(),
                "A4 stacks expire after 12 seconds");

        Varesa restored = new Varesa(
                null, null, TalentDataManager.getInstance(),
                0, () -> 0.75);
        CombatSimulator restoredSimulator = simulatorWith(restored);
        List<ParticleRecord> particles = captureParticles(restoredSimulator);
        perform(restoredSimulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = restoredSimulator.saveSnapshot();
        double particleTime = 105.0 * FRAME;
        advanceTo(restoredSimulator, particleTime + EPSILON);
        assertEquals(1, particles.size(),
                "Pending particle resolves once before restore");
        restoredSimulator.restoreSnapshot(snapshot);
        restoredSimulator.restoreSnapshot(snapshot);
        particles.clear();
        advanceTo(restoredSimulator, particleTime + EPSILON);
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs pending particle once");
        assertClose(2.0, particles.get(0).count,
                "High random draw emits two particles");

        assertTrue(!restored.isMovementTerrainGeometryRepresented(),
                "Movement, terrain, and height geometry fail closed");
        assertTrue(!restored.isNightsoulBurstTeamPlumbingRepresented(),
                "Team Nightsoul Burst plumbing fails closed");
        assertTrue(!restored.isRandomMultiTargetSelectionRepresented(),
                "Random and multi-target selection fail closed");
        assertTrue(!restored.isPlayerHpHealingRepresented(),
                "Player HP and healing fail closed");
        assertTrue(!restored.isHitlagStaminaRepresented(),
                "Hitlag and stamina fail closed");
        assertTrue(!restored.isLowPlungeRepresented(),
                "Low Plunge fails closed");
        assertTrue(!restored.isExplorationDefensiveStateRepresented(),
                "Exploration and defensive state fail closed");
        assertThrows(IllegalArgumentException.class,
                () -> new Varesa(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Varesa(null, null, 7),
                "Constellation above C6 is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> restored.onAction(null, restoredSimulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> restoredSimulator.performAction(
                        CharacterId.VARESA,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Hold Skill is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> perform(restoredSimulator, CharacterActionKey.DASH),
                "Movement action is rejected");

        Varesa foreign = new Varesa(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!restored.acceptsCharacterState(foreignState),
                "Varesa rejects another instance state");
        Varesa reused = new Varesa(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Varesa rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.VARESA,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.VARESA) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
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
            StatType statType) {
        Double value = action.getExtraBonuses().get(statType);
        return value == null ? 0.0 : value;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        if (targetTime > simulator.getCurrentTime()) {
            simulator.advanceTime(targetTime - simulator.getCurrentTime());
        }
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
            assertTrue(lines.get(index).startsWith("Varesa,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Varesa/Varesa_Status.csv",
                "config/characters/Varesa/Varesa_Multipliers.csv"
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
        throw new AssertionError("Varesa CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
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
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but caught "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
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

    private static final class ParticleRecord {
        private final double count;
        private final double time;

        private ParticleRecord(double count, double time) {
            this.count = count;
            this.time = time;
        }
    }
}
