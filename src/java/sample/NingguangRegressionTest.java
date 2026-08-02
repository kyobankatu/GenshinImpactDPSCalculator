package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataSource;
import model.character.Ningguang;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Ningguang's legacy offensive slice. */
public final class NingguangRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private NingguangRegressionTest() {
    }

    /** Runs identity, action, Jade, Screen, Burst, and isolation checks. */
    public static void main(String[] args) {
        testIdentityStatsAndConstruction();
        testNormalVariantsJadeCapAndChargedConsumption();
        testSwitchClearsJadesAndPlunge();
        testJadeScreenTimingParticlesAndExpiry();
        testBurstScreenC2AndEnergy();
        testConstellationTalentAndC6Jades();
        testInsufficientEnergyAndCrossSimulatorBinding();
        System.out.println("NingguangRegressionTest passed");
    }

    private static void testIdentityStatsAndConstruction() {
        Ningguang ningguang = ningguangAtConstellation(6);
        assertEquals(CharacterId.NINGGUANG, ningguang.getCharacterId(),
                "Ningguang typed id");
        assertEquals(Element.GEO, ningguang.getElement(),
                "Ningguang element");
        assertClose(9787.0,
                ningguang.getBaseStats().get(StatType.BASE_HP), EPS,
                "Ningguang base HP");
        assertClose(212.0,
                ningguang.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Ningguang base ATK");
        assertClose(573.0,
                ningguang.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Ningguang base DEF");
        assertClose(0.24,
                ningguang.getBaseStats().get(StatType.GEO_DMG_BONUS), EPS,
                "Ningguang ascension Geo DMG");
        assertClose(40.0, ningguang.getEnergyCost(), EPS,
                "Ningguang Energy cost");
        assertClose(12.0, ningguang.getSkillCD(), EPS,
                "Ningguang Skill cooldown");
        assertClose(12.0, ningguang.getBurstCD(), EPS,
                "Ningguang Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation,
                    ningguangAtConstellation(constellation).getConstellation(),
                    "Ningguang explicit C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Ningguang(null, null, -1),
                "Ningguang rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Ningguang(null, null, 7),
                "Ningguang rejects high constellation");
    }

    private static void testNormalVariantsJadeCapAndChargedConsumption() {
        Ningguang ningguang = ningguangAtConstellation(0);
        CombatSimulator sim = simulatorWith(ningguang);
        List<ActionRecord> records = captureNingguangActions(sim);
        int[] releaseFrames = { 29, 19, 27 };
        int[] durationFrames = { 61, 56, 66 };
        String[] variants = { "Left", "Right", "Twirl" };
        double castTime = 0.0;
        for (int variant = 0; variant < variants.length; variant++) {
            perform(sim, CharacterActionKey.NORMAL);
            ActionRecord first = records.get(variant * 2);
            ActionRecord second = records.get(variant * 2 + 1);
            assertTrue(first.action.getName().endsWith(variants[variant]),
                    "Ningguang deterministic Normal variant");
            assertClose(0.476, first.action.getDamagePercent(), EPS,
                    "Ningguang Normal multiplier");
            assertEquals(ActionType.NORMAL, first.action.getActionType(),
                    "Ningguang Normal category");
            assertEquals(ICDType.Standard, first.action.getICDType(),
                    "Ningguang Normal ICD");
            assertClose(castTime + (releaseFrames[variant] + 10.0) * FRAME,
                    first.time, EPS,
                    "Ningguang Normal projectile timing");
            assertClose(first.time, second.time, EPS,
                    "Ningguang paired Normal projectiles");
            assertEquals(variant + 1, ningguang.getStarJadeCount(),
                    "Ningguang gains one Jade per Normal action");
            castTime += durationFrames[variant] * FRAME;
        }
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(3, ningguang.getStarJadeCount(),
                "Ningguang Star Jade cap");

        records.clear();
        double chargedCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.CHARGE);
        sim.advanceTime(3.0 * FRAME);
        assertEquals(4, records.size(),
                "Ningguang Charged plus three Jade hits");
        assertClose(2.95936, records.get(0).action.getDamagePercent(), EPS,
                "Ningguang Charged multiplier");
        for (int i = 1; i < records.size(); i++) {
            assertClose(0.8432, records.get(i).action.getDamagePercent(), EPS,
                    "Ningguang Star Jade multiplier");
            assertEquals(ICDTag.ChargedAttack,
                    records.get(i).action.getICDTag(),
                    "Ningguang Star Jade shared Charged ICD tag");
        }
        assertClose(chargedCast + 45.0 * FRAME,
                records.get(0).time, EPS,
                "Ningguang Left Charged travel timing");
        assertClose(chargedCast + 55.0 * FRAME,
                records.get(1).time, EPS,
                "Ningguang Left Jade travel timing");
        assertEquals(0, ningguang.getStarJadeCount(),
                "Ningguang Charged consumes all Jades");
    }

    private static void testSwitchClearsJadesAndPlunge() {
        Ningguang ningguang = ningguangAtConstellation(0);
        CombatSimulator sim = simulatorWith(ningguang, new TestCharacter());
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(1, ningguang.getStarJadeCount(),
                "Ningguang owns a Jade before switch");
        sim.switchCharacter(CharacterId.NOELLE);
        assertEquals(0, ningguang.getStarJadeCount(),
                "Ningguang switch clears Jades");

        Ningguang plungeNingguang = ningguangAtConstellation(0);
        CombatSimulator plungeSim = simulatorWith(plungeNingguang);
        List<ActionRecord> records = captureNingguangActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        assertClose(2.6076,
                records.get(0).action.getDamagePercent(), EPS,
                "Ningguang high Plunge multiplier");
        assertEquals(Element.GEO, records.get(0).action.getElement(),
                "Ningguang catalyst Plunge element");
        assertClose(75.0 * FRAME, plungeSim.getCurrentTime(), EPS,
                "Ningguang high Plunge duration");
    }

    private static void testJadeScreenTimingParticlesAndExpiry() {
        Ningguang ningguang = ningguangAtConstellation(0);
        CombatSimulator sim = simulatorWith(ningguang);
        List<ActionRecord> records = captureNingguangActions(sim);
        perform(sim, CharacterActionKey.SKILL);
        ActionRecord screen = find(records, "Jade Screen");
        assertClose(17.0 * FRAME, screen.time, EPS,
                "Ningguang Jade Screen hitmark");
        assertClose(3.9168, screen.action.getDamagePercent(), EPS,
                "Ningguang Jade Screen multiplier");
        assertEquals(ICDType.None, screen.action.getICDType(),
                "Ningguang Jade Screen no ICD");
        assertClose(1.0, screen.action.getGaugeUnits(), EPS,
                "Ningguang Jade Screen gauge");
        assertTrue(ningguang.isJadeScreenActive(sim.getCurrentTime()),
                "Ningguang Jade Screen active");
        sim.advanceTime(100.0 * FRAME);
        assertClose(10.2, ningguang.getTotalParticleEnergy(), EPS,
                "Ningguang expected 3.4 Geo particles");
        sim.advanceTime(30.0);
        assertTrue(!ningguang.isJadeScreenActive(sim.getCurrentTime()),
                "Ningguang Jade Screen expiry");
    }

    private static void testBurstScreenC2AndEnergy() {
        Ningguang c0 = ningguangAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Records = captureNingguangActions(c0Sim);
        perform(c0Sim, CharacterActionKey.BURST);
        assertEquals(6, count(c0Records, "Starshatter Gem"),
                "Ningguang base Burst gem count");
        assertClose(0.0, c0.getCurrentEnergy(), EPS,
                "Ningguang Burst spends 40 Energy");

        Ningguang c2 = ningguangAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Records = captureNingguangActions(c2Sim);
        perform(c2Sim, CharacterActionKey.SKILL);
        assertTrue(c2.getSkillCDRemaining(c2Sim.getCurrentTime()) > 0.0,
                "Ningguang Skill cooldown active before C2 reset");
        perform(c2Sim, CharacterActionKey.BURST);
        c2Sim.advanceTime(30.0 * FRAME);
        assertEquals(6,
                count(c2Records, "Starshatter Jade Screen Gem"),
                "Ningguang Screen contributes six Burst gems");
        assertTrue(!c2.isJadeScreenActive(c2Sim.getCurrentTime()),
                "Ningguang Burst consumes Screen");
        assertClose(0.0,
                c2.getSkillCDRemaining(c2Sim.getCurrentTime()), EPS,
                "Ningguang C2 resets Skill on Screen destruction");
    }

    private static void testConstellationTalentAndC6Jades() {
        Ningguang c3 = ningguangAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureNingguangActions(c3Sim);
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(1.7392,
                find(c3Records, "Starshatter Gem").action
                        .getDamagePercent(), EPS,
                "Ningguang C3 Burst talent level");

        Ningguang c5 = ningguangAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureNingguangActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(4.608,
                find(c5Records, "Jade Screen").action
                        .getDamagePercent(), EPS,
                "Ningguang C5 Skill talent level");

        Ningguang c6 = ningguangAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Records = captureNingguangActions(c6Sim);
        perform(c6Sim, CharacterActionKey.BURST);
        assertEquals(7, c6.getStarJadeCount(),
                "Ningguang C6 grants seven Jades");
        c6Records.clear();
        perform(c6Sim, CharacterActionKey.CHARGE);
        assertEquals(8, c6Records.size(),
                "Ningguang C6 Charged plus seven Jades");
        assertEquals(0, c6.getStarJadeCount(),
                "Ningguang C6 Jades consumed");
    }

    private static void testInsufficientEnergyAndCrossSimulatorBinding() {
        Ningguang noEnergy = ningguangAtConstellation(0);
        CombatSimulator noEnergySim = simulatorWith(noEnergy);
        noEnergy.spendEnergy(noEnergy.getCurrentEnergy());
        List<ActionRecord> records = captureNingguangActions(noEnergySim);
        perform(noEnergySim, CharacterActionKey.BURST);
        assertEquals(0, records.size(),
                "Ningguang insufficient Energy rejects Burst");

        Ningguang reused = ningguangAtConstellation(0);
        simulatorWith(reused);
        CombatSimulator second = new CombatSimulator();
        second.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> second.addCharacter(reused),
                "Ningguang rejects cross-simulator reuse");

        Ningguang first = ningguangAtConstellation(6);
        Ningguang independent = ningguangAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator independentSim = simulatorWith(independent);
        perform(firstSim, CharacterActionKey.NORMAL);
        assertEquals(1, first.getStarJadeCount(),
                "Ningguang first instance owns Jade");
        assertEquals(0, independent.getStarJadeCount(),
                "Ningguang independent instance has no leaked Jade");
        assertClose(0.0, independentSim.getCurrentTime(), EPS,
                "Ningguang independent simulator remains untouched");
    }

    private static Ningguang ningguangAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Ningguang(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(Character... party) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (Character character : party) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.NINGGUANG,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureNingguangActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.NINGGUANG) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static ActionRecord find(
            List<ActionRecord> records,
            String namePart) {
        return records.stream()
                .filter(record -> record.action.getName().contains(namePart))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing Ningguang action containing: " + namePart));
    }

    private static int count(
            List<ActionRecord> records,
            String namePart) {
        return (int) records.stream()
                .filter(record -> record.action.getName().contains(namePart))
                .count();
    }

    private static void assertClose(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
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
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    /** Minimal switch target used to verify Star Jade field ownership. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            this.name = "Noelle";
            this.characterId = CharacterId.NOELLE;
            this.element = Element.GEO;
            baseStats.set(StatType.BASE_HP, 1.0);
            baseStats.set(StatType.BASE_ATK, 1.0);
            baseStats.set(StatType.BASE_DEF, 1.0);
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
