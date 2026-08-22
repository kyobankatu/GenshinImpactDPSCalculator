package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Xiao;
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

/** Focused regression checks for Xiao's represented offensive slice. */
public final class XiaoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private XiaoRegressionTest() {
    }

    /** Runs normal, boundary, rollback, and abnormal Xiao checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testConstellationAndCharges();
        testNormalChargedAndPlunge();
        testSkillParticlesA4AndC3();
        testBurstInfusionA1C2AndC5();
        testBurstParticlesExpiryAndSwap();
        testSnapshotRestore();
        testBindingAndInputGuards();
        System.out.println("XiaoRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.XIAO, CharacterId.fromNumericId(60),
                "Xiao numeric identity");
        assertEquals(CharacterId.XIAO, CharacterId.fromName("Xiao"),
                "Xiao exact-name identity");
        assertEquals(CharacterRegion.LIYUE, CharacterId.XIAO.getRegion(),
                "Xiao region");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("xiao"),
                "Xiao lookup remains case-sensitive");

        Xiao xiao = xiao(0);
        assertEquals(CharacterId.XIAO, xiao.getCharacterId(),
                "Xiao runtime identity");
        assertEquals(Element.ANEMO, xiao.getElement(),
                "Xiao element");
        assertClose(12736.0,
                xiao.getBaseStats().get(StatType.BASE_HP),
                "Xiao base HP");
        assertClose(349.0,
                xiao.getBaseStats().get(StatType.BASE_ATK),
                "Xiao base ATK");
        assertClose(799.0,
                xiao.getBaseStats().get(StatType.BASE_DEF),
                "Xiao base DEF");
        assertClose(0.242,
                xiao.getBaseStats().get(StatType.CRIT_RATE),
                "Xiao total base CRIT Rate");
        assertClose(70.0, xiao.getEnergyCost(),
                "Xiao Burst cost");
        assertClose(10.0, xiao.getSkillCD(),
                "Xiao Skill cooldown");
        assertClose(18.0, xiao.getBurstCD(),
                "Xiao Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Xiao/Xiao_Status.csv"), 23);
        assertCsvShape(Path.of(
                "config/characters/Xiao/Xiao_Multipliers.csv"), 14);
        assertCsvValue("N6", 1.611720);
        assertCsvValue("Bane of All Evil Bonus C5", 1.043000);
    }

    private static void testConstellationAndCharges() {
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation, xiao(constellation).getConstellation(),
                    "Xiao accepts C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> xiao(-1),
                "Xiao rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> xiao(7),
                "Xiao rejects constellation above C6");

        Xiao c0 = xiao(0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        perform(c0Simulator, CharacterActionKey.SKILL);
        perform(c0Simulator, CharacterActionKey.SKILL);
        double thirdRequest = c0Simulator.getCurrentTime();
        perform(c0Simulator, CharacterActionKey.SKILL);
        assertClose(10.0,
                c0Simulator.getCurrentTime() - 37.0 * FRAME,
                "C0 third Skill waits for the first restored charge");
        assertTrue(c0Simulator.getCurrentTime() > thirdRequest + 9.0,
                "C0 charge exhaustion causes a bounded wait");

        Xiao c1 = xiao(1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertEquals(3, c1.getChargeRestoreTimes().size(),
                "C1 exposes three Skill charges");
        assertClose(10.0, c1.getChargeRestoreTimes().get(0),
                "C1 first charge restore");
        assertClose(10.0 + 37.0 * FRAME,
                c1.getChargeRestoreTimes().get(1),
                "C1 second charge restore");
        assertClose(10.0 + 74.0 * FRAME,
                c1.getChargeRestoreTimes().get(2),
                "C1 third charge restore");
    }

    private static void testNormalChargedAndPlunge() {
        Xiao xiao = xiao(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(xiao, ally);
        List<ActionRecord> records = captureActions(simulator);
        for (int step = 0; step < 6; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(records, "Whirlwind Thrust N");
        assertEquals(8, normals.size(),
                "Xiao six-step Normal string has eight hits");
        double[] multipliers = {
            0.463240, 0.463240, 0.957560, 1.152920,
            0.633440, 0.633440, 1.203240, 1.611720
        };
        double[] hitFrames = {
            4.0, 17.0,
            26.0 + 15.0 + 5.0,
            53.0 + 15.0 + 10.0,
            91.0 + 14.0 + 15.0, 91.0 + 31.0 + 15.0,
            133.0 + 16.0 + 22.0,
            163.0 + 39.0 + 27.0
        };
        for (int index = 0; index < normals.size(); index++) {
            assertClose(multipliers[index],
                    normals.get(index).action.getDamagePercent(),
                    "Xiao Normal multiplier " + index);
            assertClose(hitFrames[index] * FRAME,
                    normals.get(index).time,
                    "Xiao Normal hitmark " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Xiao Normal element " + index);
            assertEquals(ICDTag.NormalAttack,
                    normals.get(index).action.getICDTag(),
                    "Xiao Normal ICD tag " + index);
        }
        assertClose((242.0 + 33.0) * FRAME, simulator.getCurrentTime(),
                "Xiao Normal string duration");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.XIAO);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(4, named(records, "Whirlwind Thrust N1").size(),
                "Xiao switch-out resets Normal progression");

        Xiao chargedOwner = xiao(0);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                chargedRecords, "Whirlwind Thrust Charged").get(0);
        assertClose(2.036480, charged.action.getDamagePercent(),
                "Xiao Charged multiplier");
        assertClose(16.0 * FRAME, charged.time,
                "Xiao Charged hitmark");
        assertClose((45.0 + 5.0) * FRAME,
                chargedSimulator.getCurrentTime(),
                "Xiao Charged duration");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Xiao Charged category");

        Xiao plungeOwner = xiao(0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(
                plungeRecords, "Whirlwind Thrust High Plunge").get(0);
        assertClose(3.754990, plunge.action.getDamagePercent(),
                "Xiao High Plunge multiplier");
        assertClose(46.0 * FRAME, plunge.time,
                "Xiao High Plunge hitmark");
        assertClose(66.0 * FRAME, plungeSimulator.getCurrentTime(),
                "Xiao High Plunge duration");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Xiao High Plunge has no ICD");
    }

    private static void testSkillParticlesA4AndC3() {
        Xiao c1 = xiao(1);
        CombatSimulator simulator = simulatorWith(c1);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.SKILL);
        List<ActionRecord> skills = named(
                records, "Lemniscatic Wind Cycling");
        assertEquals(3, skills.size(),
                "Xiao C1 casts three consecutive Skills");
        for (int index = 0; index < skills.size(); index++) {
            assertClose(4.297600, skills.get(index).action.getDamagePercent(),
                    "Xiao Skill multiplier " + index);
            assertEquals(ICDType.None, skills.get(index).action.getICDType(),
                    "Xiao Skill applies through 0.1-second sourced ICD "
                            + index);
            assertEquals(ICDTag.ElementalSkill,
                    skills.get(index).action.getICDTag(),
                    "Xiao Skill typed ICD tag " + index);
        }
        assertClose(0.0, skills.get(0).action.getStatSnapshot().get(
                StatType.SKILL_DMG_BONUS),
                "First Skill does not consume its own delayed A4 stack");
        assertClose(0.15, skills.get(1).action.getStatSnapshot().get(
                StatType.SKILL_DMG_BONUS),
                "Second Skill receives one A4 stack");
        assertClose(0.30, skills.get(2).action.getStatSnapshot().get(
                StatType.SKILL_DMG_BONUS),
                "Third Skill receives two A4 stacks");
        assertEquals(3, c1.getA4Stacks(simulator.getCurrentTime()),
                "Third delayed A4 activation reaches cap");
        advanceTo(simulator, 89.0 * FRAME + 7.0);
        assertEquals(0, c1.getA4Stacks(simulator.getCurrentTime()),
                "A4 expires at its exact half-open boundary");
        assertEquals(3, particles.size(),
                "Three connected Skills emit three particle packets");
        assertClose(3.0, particles.get(0).count,
                "Xiao Skill emits three Anemo particles");
        assertClose(104.0 * FRAME, particles.get(0).time,
                "Xiao first particle travel timing");

        Xiao c3 = xiao(3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        assertClose(5.056000, named(
                c3Records, "Lemniscatic Wind Cycling").get(0)
                        .action.getDamagePercent(),
                "Xiao C3 routes Skill to Talent 12");
    }

    private static void testBurstInfusionA1C2AndC5() {
        Xiao c0 = xiao(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c0, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(c0.isBurstActive(simulator.getCurrentTime()),
                "Xiao Burst is active after cast animation");
        assertClose(29.0 * FRAME + 18.0,
                c0.getBurstCooldownEndTime(),
                "Xiao Burst cooldown starts at frame 29");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Xiao Burst spends Energy at frame 36");
        assertClose(36.0 * FRAME, c0.getBurstEnergyMarkers().get(0)[0],
                "Xiao Burst Energy marker timing");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord first = named(records, "Whirlwind Thrust N1").get(0);
        assertEquals(Element.ANEMO, first.action.getElement(),
                "Burst converts Normal damage to Anemo");
        assertClose(1.0, first.action.getGaugeUnits(),
                "Burst Normal applies 1U Anemo");
        assertClose(0.906500, first.action.getStatSnapshot().get(
                StatType.NORMAL_ATTACK_DMG_BONUS),
                "C0 Burst grants Talent-9 Normal bonus");
        assertClose(0.05, first.action.getStatSnapshot().get(
                StatType.DMG_BONUS_ALL),
                "A1 starts at five percent");

        advanceTo(simulator, 3.0);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord ramped = named(records, "Whirlwind Thrust N2").get(0);
        assertClose(0.10, ramped.action.getStatSnapshot().get(
                StatType.DMG_BONUS_ALL),
                "A1 reaches ten percent at exact three seconds");

        Xiao c5 = xiao(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        perform(c5Simulator, CharacterActionKey.CHARGE);
        ActionRecord c5Charge = named(
                c5Records, "Whirlwind Thrust Charged").get(0);
        assertEquals(Element.ANEMO, c5Charge.action.getElement(),
                "C5 Burst converts Charged damage");
        assertEquals(ICDTag.NormalAttack, c5Charge.action.getICDTag(),
                "Burst Charged shares Normal ICD");
        assertClose(1.043000, c5Charge.action.getStatSnapshot().get(
                StatType.CHARGED_ATTACK_DMG_BONUS),
                "C5 routes Burst bonus to Talent 12");

        Xiao c2 = xiao(2);
        TestCharacter c2Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c2Simulator = simulatorWith(c2, c2Ally);
        assertClose(1.0, c2.getEffectiveStats(0.0).get(
                StatType.ENERGY_RECHARGE),
                "C2 does not apply on field");
        c2Simulator.setActiveCharacter(CharacterId.NOELLE);
        assertClose(1.25, c2.getEffectiveStats(0.0).get(
                StatType.ENERGY_RECHARGE),
                "C2 adds twenty-five percent ER off field");
        Xiao c1 = xiao(1);
        TestCharacter c1Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c1Simulator = simulatorWith(c1, c1Ally);
        c1Simulator.setActiveCharacter(CharacterId.NOELLE);
        assertClose(1.0, c1.getEffectiveStats(0.0).get(
                StatType.ENERGY_RECHARGE),
                "C1 does not receive C2 ER early");
    }

    private static void testBurstParticlesExpiryAndSwap() {
        Xiao suppressed = xiao(0);
        CombatSimulator suppressedSimulator = simulatorWith(suppressed);
        List<ParticleRecord> suppressedParticles = captureParticles(
                suppressedSimulator);
        perform(suppressedSimulator, CharacterActionKey.BURST);
        perform(suppressedSimulator, CharacterActionKey.SKILL);
        advanceTo(suppressedSimulator, 4.0);
        assertEquals(0, suppressedParticles.size(),
                "Skill cast during Burst suppresses particles");

        Xiao expiryOwner = xiao(0);
        CombatSimulator expirySimulator = simulatorWith(expiryOwner);
        List<ActionRecord> expiryRecords = captureActions(expirySimulator);
        perform(expirySimulator, CharacterActionKey.BURST);
        advanceTo(expirySimulator, 15.95);
        assertTrue(!expiryOwner.isBurstActive(15.95),
                "Burst expires at the exact sourced boundary");
        perform(expirySimulator, CharacterActionKey.PLUNGE);
        ActionRecord expiredPlunge = named(
                expiryRecords, "Whirlwind Thrust High Plunge").get(0);
        assertEquals(Element.PHYSICAL, expiredPlunge.action.getElement(),
                "Exact-expiry Plunge remains Physical");
        assertClose(0.0, expiredPlunge.action.getStatSnapshot().get(
                StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Exact-expiry Plunge receives no Burst bonus");

        Xiao swapped = xiao(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator swapSimulator = simulatorWith(swapped, ally);
        List<ActionRecord> swapRecords = captureActions(swapSimulator);
        perform(swapSimulator, CharacterActionKey.BURST);
        swapSimulator.switchCharacter(CharacterId.NOELLE);
        swapSimulator.switchCharacter(CharacterId.XIAO);
        assertTrue(!swapped.isBurstActive(swapSimulator.getCurrentTime()),
                "Switch-out cancels Xiao Burst");
        perform(swapSimulator, CharacterActionKey.NORMAL);
        assertEquals(Element.PHYSICAL, named(
                swapRecords, "Whirlwind Thrust N1").get(0)
                        .action.getElement(),
                "Post-swap Normal stays Physical");
        assertTrue(swapped.getActiveBuffs().isEmpty(),
                "Excluded HP, C4, and C6 branches create no buffs");
    }

    private static void testSnapshotRestore() {
        Xiao comboOwner = xiao(0);
        CombatSimulator comboSimulator = simulatorWith(comboOwner);
        List<ActionRecord> comboRecords = captureActions(comboSimulator);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot comboSnapshot = comboSimulator.saveSnapshot();
        perform(comboSimulator, CharacterActionKey.NORMAL);
        comboSimulator.restoreSnapshot(comboSnapshot);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(comboRecords, "Whirlwind Thrust N2").size(),
                "Restore preserves Xiao Normal progression");

        Xiao particleOwner = xiao(0);
        CombatSimulator particleSimulator = simulatorWith(particleOwner);
        List<ParticleRecord> particles = captureParticles(particleSimulator);
        perform(particleSimulator, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSimulator.saveSnapshot();
        advanceTo(particleSimulator, 104.0 * FRAME);
        assertEquals(1, particles.size(),
                "Live pending Xiao particles arrive once");
        particleSimulator.restoreSnapshot(particleSnapshot);
        particleSimulator.restoreSnapshot(particleSnapshot);
        advanceTo(particleSimulator, 104.0 * FRAME);
        assertEquals(2, particles.size(),
                "Restored pending Xiao particles arrive once");
        assertEquals(0, particleOwner.getPendingHitCount(),
                "Restored Xiao hit queue drains cleanly");

        Xiao burstOwner = xiao(0);
        CombatSimulator burstSimulator = simulatorWith(burstOwner);
        perform(burstSimulator, CharacterActionKey.BURST);
        advanceTo(burstSimulator, 3.0);
        SimulatorSnapshot burstSnapshot = burstSimulator.saveSnapshot();
        advanceTo(burstSimulator, 6.0);
        assertClose(0.15, burstOwner.getA1DamageBonus(6.0),
                "Live A1 reaches fifteen percent at six seconds");
        burstSimulator.restoreSnapshot(burstSnapshot);
        assertTrue(burstOwner.isBurstActive(3.0),
                "Restore preserves Xiao Burst window");
        assertClose(0.10, burstOwner.getA1DamageBonus(3.0),
                "Restore preserves A1 origin time");
    }

    private static void testBindingAndInputGuards() {
        Xiao xiao = xiao(0);
        CombatSimulator simulator = simulatorWith(xiao);
        assertThrows(IllegalArgumentException.class,
                () -> xiao.onAction(null, simulator),
                "Xiao rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Xiao rejects unsupported Dash");
        Xiao external = xiao(0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Xiao rejects binding outside simulator party");
        Xiao reused = xiao(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Xiao rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!xiao.acceptsCharacterState(foreignState),
                "Xiao rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> xiao.restoreCharacterState(foreignState, simulator),
                "Xiao rejects foreign restore payload");
    }

    private static Xiao xiao(int constellation) {
        return new Xiao(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Xiao xiao,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(xiao);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.XIAO);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.XIAO, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(action, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
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
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
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
            assertTrue(lines.get(index).startsWith("Xiao,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Xiao/Xiao_Status.csv",
                "config/characters/Xiao/Xiao_Multipliers.csv"
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
        throw new AssertionError("Xiao CSVs missing key " + key);
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
