package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataSource;
import mechanics.reaction.ReactionResult;
import model.character.Keqing;
import model.entity.Enemy;
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

/** Focused regression checks for Keqing's legacy offensive slice. */
public final class KeqingRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private KeqingRegressionTest() {
    }

    /** Runs identity, action, timing, state, and constellation checks. */
    public static void main(String[] args) {
        testIdentityStatsAndConstruction();
        testNormalChainAndPhysicalActions();
        testStilettoRecastInfusionParticlesAndCooldown();
        testChargedDetonationAndStilettoExpiry();
        testBurstCadenceEnergyA4AndC3();
        testC1C4C5AndC6();
        testInsufficientEnergyAndCrossSimulatorBinding();
        System.out.println("KeqingRegressionTest passed");
    }

    private static void testIdentityStatsAndConstruction() {
        Keqing keqing = keqingAtConstellation(6);
        assertEquals(CharacterId.KEQING, keqing.getCharacterId(),
                "Keqing typed id");
        assertEquals(Element.ELECTRO, keqing.getElement(),
                "Keqing element");
        assertClose(13103.0,
                keqing.getBaseStats().get(StatType.BASE_HP), EPS,
                "Keqing base HP");
        assertClose(323.0,
                keqing.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Keqing base ATK");
        assertClose(799.0,
                keqing.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Keqing base DEF");
        assertClose(0.884,
                keqing.getBaseStats().get(StatType.CRIT_DMG), EPS,
                "Keqing base plus ascension CRIT DMG");
        assertClose(40.0, keqing.getEnergyCost(), EPS,
                "Keqing Energy cost");
        assertClose(7.5, keqing.getSkillCD(), EPS,
                "Keqing Skill cooldown");
        assertClose(12.0, keqing.getBurstCD(), EPS,
                "Keqing Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation,
                    keqingAtConstellation(constellation).getConstellation(),
                    "Keqing explicit C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Keqing(null, null, -1),
                "Keqing rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Keqing(null, null, 7),
                "Keqing rejects high constellation");
    }

    private static void testNormalChainAndPhysicalActions() {
        Keqing keqing = keqingAtConstellation(0);
        CombatSimulator sim = simulatorWith(keqing);
        List<ActionRecord> records = captureKeqingActions(sim);
        double[][] multipliers = {
                { 0.75366 }, { 0.75366 }, { 1.00014 },
                { 0.57828, 0.63200 }, { 1.23082 }
        };
        int[][] frames = { { 10 }, { 10 }, { 14 }, { 11, 21 }, { 22 } };
        int[] durations = { 19, 24, 36, 58, 66 };
        double castTime = 0.0;
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(sim, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < multipliers[step].length; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(), EPS,
                        "Keqing N" + (step + 1) + " multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Keqing physical Normal");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Keqing Normal category");
                assertEquals(ICDType.Standard,
                        record.action.getICDType(),
                        "Keqing Normal standard ICD");
                assertClose(castTime + frames[step][hit] * FRAME,
                        record.time, EPS,
                        "Keqing Normal hitmark");
            }
            castTime += durations[step] * FRAME;
        }
        assertClose(castTime, sim.getCurrentTime(), EPS,
                "Keqing full Normal duration");

        records.clear();
        perform(sim, CharacterActionKey.CHARGE);
        assertEquals(2, records.size(), "Keqing Charged hit count");
        assertClose(1.41094, records.get(0).action.getDamagePercent(), EPS,
                "Keqing Charged first multiplier");
        assertClose(1.58000, records.get(1).action.getDamagePercent(), EPS,
                "Keqing Charged second multiplier");
        perform(sim, CharacterActionKey.PLUNGE);
        assertClose(2.933586,
                records.get(2).action.getDamagePercent(), EPS,
                "Keqing high Plunge multiplier");
    }

    private static void testStilettoRecastInfusionParticlesAndCooldown() {
        Keqing keqing = keqingAtConstellation(0);
        CombatSimulator sim = simulatorWith(keqing);
        List<ActionRecord> records = captureKeqingActions(sim);
        perform(sim, CharacterActionKey.SKILL);
        assertTrue(keqing.isStilettoActive(sim.getCurrentTime()),
                "Keqing Stiletto remains after first cast");
        assertClose(0.8568,
                find(records, "Lightning Stiletto").action
                        .getDamagePercent(), EPS,
                "Keqing Stiletto multiplier");
        assertClose(0.0, keqing.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Keqing recast bypasses first-cast cooldown gate");

        double recastTime = sim.getCurrentTime();
        perform(sim, CharacterActionKey.SKILL);
        ActionRecord slash = find(records, "Slashing");
        assertClose(recastTime + 16.0 * FRAME, slash.time, EPS,
                "Keqing recast hitmark");
        assertClose(2.856, slash.action.getDamagePercent(), EPS,
                "Keqing recast multiplier");
        assertEquals(ICDType.Standard, slash.action.getICDType(),
                "Keqing recast standard ICD");
        assertEquals(ICDTag.ElementalSkill, slash.action.getICDTag(),
                "Keqing recast Skill tag");
        assertClose(2.0, slash.action.getGaugeUnits(), EPS,
                "Keqing recast gauge");
        assertTrue(keqing.isElectroInfusionActive(sim.getCurrentTime()),
                "Keqing A1 infusion starts on recast");
        assertClose(7.5 - sim.getCurrentTime(),
                keqing.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Keqing cooldown preserves first-cast deadline");

        perform(sim, CharacterActionKey.NORMAL);
        ActionRecord infused = records.get(records.size() - 1);
        assertEquals(Element.ELECTRO, infused.action.getElement(),
                "Keqing A1 infuses Normal Attack");
        sim.advanceTime(100.0 * FRAME);
        assertClose(7.5, keqing.getTotalParticleEnergy(), EPS,
                "Keqing expected 2.5 Skill particles reach active owner");
        sim.advanceTime(5.0);
        assertTrue(!keqing.isElectroInfusionActive(sim.getCurrentTime()),
                "Keqing A1 infusion expires");
    }

    private static void testChargedDetonationAndStilettoExpiry() {
        Keqing keqing = keqingAtConstellation(0);
        CombatSimulator sim = simulatorWith(keqing);
        List<ActionRecord> records = captureKeqingActions(sim);
        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.CHARGE);
        assertEquals(2, count(records, "Thunderclap Slash"),
                "Keqing Charged detonation hit count");
        assertTrue(!keqing.isStilettoActive(sim.getCurrentTime()),
                "Keqing Charged Attack consumes Stiletto");
        assertTrue(!keqing.isElectroInfusionActive(sim.getCurrentTime()),
                "Keqing Charged detonation does not grant infusion");

        Keqing expiryKeqing = keqingAtConstellation(0);
        CombatSimulator expirySim = simulatorWith(expiryKeqing);
        perform(expirySim, CharacterActionKey.SKILL);
        expirySim.advanceTime(5.0);
        assertTrue(!expiryKeqing.isStilettoActive(expirySim.getCurrentTime()),
                "Keqing Stiletto exact expiry");
        assertClose(7.5 - expirySim.getCurrentTime(),
                expiryKeqing.getSkillCDRemaining(expirySim.getCurrentTime()),
                EPS,
                "Keqing expiry restores original cooldown deadline");
    }

    private static void testBurstCadenceEnergyA4AndC3() {
        Keqing c0 = keqingAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Records = captureKeqingActions(c0Sim);
        perform(c0Sim, CharacterActionKey.BURST);
        c0Sim.advanceTime(100.0 * FRAME);
        assertEquals(10, count(c0Records, "Starward Sword"),
                "Keqing Burst total hit count");
        assertClose(1.496,
                find(c0Records, "Initial").action.getDamagePercent(), EPS,
                "Keqing Burst initial multiplier");
        assertClose(0.408,
                find(c0Records, "Consecutive Slash 1").action
                        .getDamagePercent(), EPS,
                "Keqing Burst slash multiplier");
        assertClose(3.2096,
                find(c0Records, "Last Attack").action.getDamagePercent(), EPS,
                "Keqing Burst final multiplier");
        assertClose(0.0, c0.getCurrentEnergy(), EPS,
                "Keqing Burst spends 40 Energy");
        assertClose(0.20,
                c0.getEffectiveStats(c0Sim.getCurrentTime())
                        .get(StatType.CRIT_RATE), EPS,
                "Keqing A4 CRIT Rate");
        assertClose(1.15,
                c0.getEffectiveStats(c0Sim.getCurrentTime())
                        .get(StatType.ENERGY_RECHARGE), EPS,
                "Keqing A4 Energy Recharge");

        Keqing c3 = keqingAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureKeqingActions(c3Sim);
        perform(c3Sim, CharacterActionKey.BURST);
        c3Sim.advanceTime(100.0 * FRAME);
        assertClose(1.760,
                find(c3Records, "Initial").action.getDamagePercent(), EPS,
                "Keqing C3 Burst talent level");
        assertClose(3.776,
                find(c3Records, "Last Attack").action.getDamagePercent(), EPS,
                "Keqing C3 final talent level");
    }

    private static void testC1C4C5AndC6() {
        Keqing c1 = keqingAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Records = captureKeqingActions(c1Sim);
        perform(c1Sim, CharacterActionKey.SKILL);
        perform(c1Sim, CharacterActionKey.SKILL);
        assertEquals(1, count(c1Records, "Stellar Restoration C1"),
                "Keqing C1 guaranteed terminus hit");

        Keqing c5 = keqingAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureKeqingActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(1.008,
                find(c5Records, "Lightning Stiletto").action
                        .getDamagePercent(), EPS,
                "Keqing C5 Stiletto talent level");
        assertClose(3.360,
                find(c5Records, "Slashing").action.getDamagePercent(), EPS,
                "Keqing C5 recast talent level");

        Keqing c4 = keqingAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        c4Sim.notifyReaction(
                ReactionResult.transform(
                        1.0, "Aggravate", ReactionResult.Kind.AGGRAVATE),
                c4);
        assertClose(0.25,
                c4.getEffectiveStats(c4Sim.getCurrentTime())
                        .get(StatType.ATK_PERCENT), EPS,
                "Keqing C4 reaction ATK bonus");
        c4Sim.advanceTime(10.0);
        assertClose(0.0,
                c4.getEffectiveStats(c4Sim.getCurrentTime())
                        .get(StatType.ATK_PERCENT), EPS,
                "Keqing C4 exact expiry");

        Keqing c6 = keqingAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.NORMAL);
        perform(c6Sim, CharacterActionKey.CHARGE);
        perform(c6Sim, CharacterActionKey.SKILL);
        perform(c6Sim, CharacterActionKey.SKILL);
        perform(c6Sim, CharacterActionKey.BURST);
        assertClose(0.24,
                c6.getEffectiveStats(c6Sim.getCurrentTime())
                        .get(StatType.ELECTRO_DMG_BONUS), EPS,
                "Keqing C6 four independent action sources");
        c6Sim.advanceTime(8.0);
        assertClose(0.0,
                c6.getEffectiveStats(c6Sim.getCurrentTime())
                        .get(StatType.ELECTRO_DMG_BONUS), EPS,
                "Keqing C6 stack expiry");
    }

    private static void testInsufficientEnergyAndCrossSimulatorBinding() {
        Keqing noEnergy = keqingAtConstellation(0);
        CombatSimulator noEnergySim = simulatorWith(noEnergy);
        noEnergy.spendEnergy(noEnergy.getCurrentEnergy());
        List<ActionRecord> records = captureKeqingActions(noEnergySim);
        perform(noEnergySim, CharacterActionKey.BURST);
        assertEquals(0, records.size(),
                "Keqing insufficient Energy rejects Burst");

        Keqing reused = keqingAtConstellation(4);
        simulatorWith(reused);
        CombatSimulator second = new CombatSimulator();
        second.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> second.addCharacter(reused),
                "Keqing rejects cross-simulator reuse");

        Keqing first = keqingAtConstellation(6);
        Keqing independent = keqingAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator independentSim = simulatorWith(independent);
        perform(firstSim, CharacterActionKey.SKILL);
        assertTrue(first.isStilettoActive(firstSim.getCurrentTime()),
                "Keqing first instance owns Stiletto");
        assertTrue(!independent.isStilettoActive(
                independentSim.getCurrentTime()),
                "Keqing independent instance has no leaked Stiletto");
    }

    private static Keqing keqingAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Keqing(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(Keqing keqing) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(keqing);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.KEQING,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureKeqingActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KEQING) {
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
                        "Missing Keqing action containing: " + namePart));
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
}
