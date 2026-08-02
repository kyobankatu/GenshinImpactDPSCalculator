package sample;

import mechanics.buff.Buff;
import model.character.Diona;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused metadata, action, field, constellation, and guard checks. */
public final class DionaRegressionTest {
    private static final double EPSILON = 1e-8;

    private DionaRegressionTest() {
    }

    /** Runs Diona's complete offensive-slice regression cases. */
    public static void main(String[] args) {
        testMetadataAndConstructorGuards();
        testNormalChargeAndPlungeActions();
        testHoldSkillAndC2();
        testBurstTicksC1C4AndC6();
        testEnergyCooldownAndBindingGuards();
        System.out.println("DionaRegressionTest passed");
    }

    private static void testMetadataAndConstructorGuards() {
        Diona diona = new Diona(null, null);
        assertEquals(CharacterId.DIONA, diona.getCharacterId(),
                "Diona identity");
        assertClose(9570.0, diona.getBaseStats().get(StatType.BASE_HP),
                "Diona base HP");
        assertClose(212.0, diona.getBaseStats().get(StatType.BASE_ATK),
                "Diona base ATK");
        assertClose(601.0, diona.getBaseStats().get(StatType.BASE_DEF),
                "Diona base DEF");
        assertClose(0.24,
                diona.getBaseStats().get(StatType.CRYO_DMG_BONUS),
                "Diona ascension Cryo DMG");
        assertClose(80.0, diona.getEnergyCost(), "Diona Energy cost");
        assertClose(15.0, diona.getSkillCD(), "Diona Hold Skill cooldown");
        assertClose(20.0, diona.getBurstCD(), "Diona Burst cooldown");
        assertThrows(IllegalArgumentException.class,
                () -> new Diona(null, null, -1),
                "Diona rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Diona(null, null, 7),
                "Diona rejects constellation seven");
    }

    private static void testNormalChargeAndPlungeActions() {
        Diona diona = new Diona(null, null, 0);
        CombatSimulator sim = simulatorWith(diona);
        for (int step = 0; step < 5; step++) {
            perform(sim, CharacterActionKey.NORMAL);
        }
        double afterNormals = sim.getTotalDamage();
        assertTrue(afterNormals > 0.0, "Diona Normal chain deals damage");
        perform(sim, CharacterActionKey.CHARGE);
        perform(sim, CharacterActionKey.PLUNGE);
        assertTrue(sim.getTotalDamage() > afterNormals,
                "Diona Charged and Plunging attacks deal damage");
    }

    private static void testHoldSkillAndC2() {
        Diona c0 = new Diona(null, null, 0);
        CombatSimulator c0Sim = simulatorWith(c0);
        perform(c0Sim, CharacterActionKey.SKILL);
        c0Sim.advanceTime(2.5);
        double c0Damage = c0Sim.getTotalDamage();
        assertTrue(c0Damage > 0.0, "Diona Hold Skill resolves five paws");
        assertClose(15.0, c0.getSkillCooldownEndTime(),
                "Diona Hold Skill starts 15-second cooldown");

        Diona c2 = new Diona(null, null, 2);
        CombatSimulator c2Sim = simulatorWith(c2);
        perform(c2Sim, CharacterActionKey.SKILL);
        c2Sim.advanceTime(2.5);
        assertTrue(c2Sim.getTotalDamage() > c0Damage,
                "Diona C2 increases Icy Paw damage");
    }

    private static void testBurstTicksC1C4AndC6() {
        Diona diona = new Diona(null, null, 6);
        CombatSimulator sim = simulatorWith(diona);
        perform(sim, CharacterActionKey.BURST);
        assertTrue(diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona Burst field starts at impact");
        StatsContainer fieldStats = resolvedStats(diona, sim);
        assertClose(200.0, fieldStats.get(StatType.ELEMENTAL_MASTERY),
                "Diona C6 grants field EM at full HP boundary");

        double chargeStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.CHARGE);
        assertClose(58.0 / 60.0, sim.getCurrentTime() - chargeStart,
                "Diona C4 shortens field Charged action");
        sim.advanceTime(11.5);
        assertTrue(sim.getTotalDamage() > 0.0,
                "Diona Burst initial and periodic ticks resolve");
        sim.advanceTime(2.0);
        assertTrue(!diona.isBurstFieldActive(sim.getCurrentTime()),
                "Diona Burst field expires after 12.5 seconds");
        assertClose(15.0, diona.getCurrentEnergy(),
                "Diona C1 restores flat Energy after field ends");
        assertClose(0.0,
                resolvedStats(diona, sim).get(StatType.ELEMENTAL_MASTERY),
                "Diona C6 EM expires with field");
    }

    private static void testEnergyCooldownAndBindingGuards() {
        Diona diona = new Diona(null, null, 0);
        CombatSimulator sim = simulatorWith(diona);
        diona.restoreCurrentEnergy(0.0);
        double before = sim.getCurrentTime();
        perform(sim, CharacterActionKey.BURST);
        assertClose(before, sim.getCurrentTime(),
                "Diona insufficient Energy skips Burst");
        assertClose(80.0, diona.getMissedBurstCost(),
                "Diona records missed Burst cost");
        diona.initializeForSimulator(sim);
        assertThrows(IllegalStateException.class,
                () -> diona.initializeForSimulator(new CombatSimulator()),
                "Diona rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> diona.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH), sim),
                "Diona rejects unsupported Dash");
    }

    private static CombatSimulator simulatorWith(Diona diona) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(diona);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.DIONA,
                CharacterActionRequest.of(key));
    }

    private static StatsContainer resolvedStats(
            Diona diona,
            CombatSimulator sim) {
        StatsContainer stats = diona.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(diona)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats;
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }
}
