package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataSource;
import model.character.Yanfei;
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

/**
 * Focused regression checks for Yanfei's single-target offensive slice.
 */
public final class YanfeiRegressionTest {
    private static final double EPS = 1e-8;

    private YanfeiRegressionTest() {
    }

    /** Runs data, action, Seal, Brilliance, and constellation checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsvShape();
        testNormalSequenceSealGenerationAndExpiry();
        testChargedSealScalingConsumptionAndProviso();
        testSkillHitmarkParticlesCooldownAndSnapshot();
        testBurstBrillianceCadenceAndExpiry();
        testRepresentableConstellations();
        testSwitchStaleTimerAndOffFieldNormal();
        testIndependentInstancesAndSimulatorBinding();
        testUnsupportedActionAndInvalidConstellation();
        System.out.println("YanfeiRegressionTest passed");
    }

    private static void testIdentityStatsAndCsvShape() throws IOException {
        Yanfei yanfei = new Yanfei(null, null);
        assertEquals(6, yanfei.getConstellation(),
                "Yanfei default constructor uses C6");
        assertEquals(CharacterId.YANFEI, yanfei.getCharacterId(),
                "Yanfei typed id");
        assertEquals(CharacterId.YANFEI, CharacterId.fromName("Yanfei"),
                "Yanfei name lookup");
        assertEquals(CharacterId.YANFEI, CharacterId.fromNumericId(20),
                "Yanfei numeric lookup");
        assertEquals(Element.PYRO, yanfei.getElement(),
                "Yanfei element");
        assertClose(9352.0,
                yanfei.getBaseStats().get(StatType.BASE_HP), EPS,
                "Yanfei base HP");
        assertClose(240.0,
                yanfei.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Yanfei base ATK");
        assertClose(587.0,
                yanfei.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Yanfei base DEF");
        assertClose(0.24,
                yanfei.getBaseStats().get(StatType.PYRO_DMG_BONUS), EPS,
                "Yanfei ascension Pyro DMG");
        assertClose(80.0, yanfei.getEnergyCost(), EPS,
                "Yanfei Energy cost");
        assertEquals(4, yanfei.getScarletSealLimit(),
                "Yanfei C6 Seal limit");

        TalentDataSource customData = (character, key, defaultValue) ->
                "Base ATK".equals(key) ? 321.0 : defaultValue;
        Yanfei injected = new Yanfei(null, null, customData, 2);
        assertEquals(2, injected.getConstellation(),
                "Yanfei injectable constellation");
        assertClose(321.0,
                injected.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Yanfei injectable talent data");

        assertCsvShape(
                Paths.get("config/characters/Yanfei/Yanfei_Status.csv"),
                10);
        assertCsvShape(
                Paths.get(
                        "config/characters/Yanfei/Yanfei_Multipliers.csv"),
                21);
    }

    private static void testNormalSequenceSealGenerationAndExpiry() {
        Yanfei yanfei = yanfeiAtConstellation(0);
        CombatSimulator sim = simulatorWith(yanfei);
        List<ActionRecord> records = captureYanfeiActions(sim);
        double[] multipliers = { 0.991807, 0.886135, 1.292218 };
        double[] hitTimes = {
                22.0 / 60.0, 52.0 / 60.0, 101.0 / 60.0
        };
        double[] durations = {
                26.0 / 60.0, 28.0 / 60.0, 73.0 / 60.0
        };

        for (int i = 0; i < multipliers.length; i++) {
            double before = sim.getCurrentTime();
            perform(sim, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(i);
            assertEquals("Seal of Approval N" + (i + 1),
                    record.action.getName(), "Yanfei Normal name");
            assertClose(multipliers[i],
                    record.action.getDamagePercent(), EPS,
                    "Yanfei Normal multiplier");
            assertClose(hitTimes[i], record.time, EPS,
                    "Yanfei Normal hitmark plus travel");
            assertClose(before + durations[i], sim.getCurrentTime(), EPS,
                    "Yanfei Normal action duration");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Yanfei Normal action type");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Yanfei Normal standard ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Yanfei Normal ICD tag");
            assertClose(1.0, record.action.getGaugeUnits(), EPS,
                    "Yanfei Normal gauge");
            assertEquals(i + 1,
                    yanfei.getScarletSealCount(sim.getCurrentTime()),
                    "Yanfei Normal grants one Seal");
        }

        double expiry = 101.0 / 60.0 + 10.0;
        assertClose(expiry, yanfei.getScarletSealsExpireAt(), EPS,
                "Yanfei Normal refreshes shared Seal expiry");
        sim.advanceTime(expiry - sim.getCurrentTime() - 0.001);
        assertEquals(3, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Seal pre-expiry boundary");
        sim.advanceTime(0.001);
        assertEquals(0, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Seal exact expiry boundary");

        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Seal of Approval N1", records.get(0).action.getName(),
                "Yanfei Normal chain wraps after N3");
    }

    private static void testChargedSealScalingConsumptionAndProviso() {
        double[] expected = {
                1.523438, 1.792280, 2.061122, 2.329964
        };
        for (int seals = 0; seals <= 3; seals++) {
            Yanfei yanfei = yanfeiAtConstellation(0);
            CombatSimulator sim = simulatorWith(yanfei);
            for (int i = 0; i < seals; i++) {
                perform(sim, CharacterActionKey.NORMAL);
            }
            List<ActionRecord> charged = captureNamedActions(
                    sim, "Seal of Approval Charged (" + seals + " Seals)");
            List<Double> pyroBonusAtHit = new ArrayList<>();
            sim.addDamageListener((actor, action, damage, time) -> {
                if (actor == yanfei
                        && action.getActionType() == ActionType.CHARGE) {
                    pyroBonusAtHit.add(yanfei.getEffectiveStats(time).get(
                            StatType.PYRO_DMG_BONUS));
                }
            });

            double castTime = sim.getCurrentTime();
            perform(sim, CharacterActionKey.CHARGE);
            assertEquals(1, charged.size(),
                    "Yanfei one Charged hit");
            assertClose(expected[seals],
                    charged.get(0).action.getDamagePercent(), EPS,
                    "Yanfei seal-scaled Charged multiplier");
            assertClose(castTime + 63.0 / 60.0,
                    charged.get(0).time, EPS,
                    "Yanfei Charged hitmark");
            assertClose(castTime + 79.0 / 60.0,
                    sim.getCurrentTime(), EPS,
                    "Yanfei Charged duration");
            assertEquals(ICDType.None,
                    charged.get(0).action.getICDType(),
                    "Yanfei Charged no ICD");
            assertTrue(charged.get(0).action.isShatterTrigger(),
                    "Yanfei Charged is blunt");
            assertEquals(0,
                    yanfei.getScarletSealCount(sim.getCurrentTime()),
                    "Yanfei Charged consumes every Seal");
            assertClose(0.24 + seals * 0.05,
                    pyroBonusAtHit.get(0), EPS,
                    "Yanfei A1 applies before Charged damage");
        }

        Yanfei replacement = yanfeiAtConstellation(0);
        CombatSimulator replacementSim = simulatorWith(replacement);
        perform(replacementSim, CharacterActionKey.NORMAL);
        perform(replacementSim, CharacterActionKey.CHARGE);
        assertClose(0.29, replacement.getEffectiveStats(
                replacementSim.getCurrentTime()).get(
                        StatType.PYRO_DMG_BONUS), EPS,
                "Yanfei A1 active after consuming one Seal");
        perform(replacementSim, CharacterActionKey.CHARGE);
        assertClose(0.24, replacement.getEffectiveStats(
                replacementSim.getCurrentTime()).get(
                        StatType.PYRO_DMG_BONUS), EPS,
                "Yanfei zero-Seal Charged dispels prior A1");
    }

    private static void testSkillHitmarkParticlesCooldownAndSnapshot() {
        Yanfei yanfei = yanfeiAtConstellation(0);
        CombatSimulator sim = simulatorWith(yanfei);
        List<ActionRecord> skill = captureNamedActions(sim, "Signed Edict");

        perform(sim, CharacterActionKey.SKILL);
        assertEquals(1, skill.size(), "Yanfei Skill hit count");
        assertClose(32.0 / 60.0, skill.get(0).time, EPS,
                "Yanfei Skill hitmark");
        assertClose(46.0 / 60.0, sim.getCurrentTime(), EPS,
                "Yanfei Skill duration");
        assertClose(2.8832,
                skill.get(0).action.getDamagePercent(), EPS,
                "Yanfei C0 Skill multiplier");
        assertTrue(skill.get(0).action.isUseSnapshot(),
                "Yanfei Skill uses cast snapshot");
        assertEquals(ICDType.None, skill.get(0).action.getICDType(),
                "Yanfei Skill no ICD");
        assertClose(1.0, skill.get(0).action.getGaugeUnits(), EPS,
                "Yanfei Skill gauge");
        assertTrue(skill.get(0).action.isShatterTrigger(),
                "Yanfei Skill is blunt");
        assertEquals(3, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Skill grants maximum Seals");
        assertClose(0.0, yanfei.getTotalParticleEnergy(), EPS,
                "Yanfei Skill particles remain in flight");
        assertClose(28.0 / 60.0, yanfei.getLastSkillTime(), EPS,
                "Yanfei Skill cooldown starts at sourced frame");
        assertClose(8.7,
                yanfei.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Yanfei Skill remaining cooldown after action");
        sim.advanceTime(132.0 / 60.0 - sim.getCurrentTime());
        assertClose(9.0, yanfei.getTotalParticleEnergy(), EPS,
                "Yanfei receives three particles after 100 frames");
    }

    private static void testBurstBrillianceCadenceAndExpiry() {
        Yanfei yanfei = yanfeiAtConstellation(0);
        CombatSimulator sim = simulatorWith(yanfei);
        List<ActionRecord> burst = captureNamedActions(sim, "Done Deal");

        perform(sim, CharacterActionKey.BURST);
        assertEquals(1, burst.size(), "Yanfei Burst hit count");
        assertClose(24.0 / 60.0, burst.get(0).time, EPS,
                "Yanfei Burst hitmark");
        assertClose(3.1008,
                burst.get(0).action.getDamagePercent(), EPS,
                "Yanfei C0 Burst multiplier");
        assertTrue(burst.get(0).action.isUseSnapshot(),
                "Yanfei Burst uses cast snapshot");
        assertEquals(ICDType.Standard,
                burst.get(0).action.getICDType(),
                "Yanfei Burst standard ICD");
        assertClose(2.0, burst.get(0).action.getGaugeUnits(), EPS,
                "Yanfei Burst gauge");
        assertClose(0.0, yanfei.getCurrentEnergy(), EPS,
                "Yanfei Burst spends 80 Energy");
        assertEquals(3, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Burst grants maximum Seals on hit");
        assertTrue(yanfei.isBrillianceActive(sim.getCurrentTime()),
                "Yanfei Brilliance active after Burst");
        assertClose(15.0, yanfei.getBrillianceExpiresAt(), EPS,
                "Yanfei Brilliance duration");
        assertClose(0.518, yanfei.getEffectiveStats(
                sim.getCurrentTime()).get(
                        StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "Yanfei Brilliance Charged bonus");

        perform(sim, CharacterActionKey.CHARGE);
        assertEquals(2, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Brilliance grants at one-second cadence");
        sim.advanceTime(3.0 - sim.getCurrentTime());
        assertEquals(3, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei Brilliance respects Seal cap");

        sim.advanceTime(15.0 - sim.getCurrentTime() - 0.001);
        assertTrue(yanfei.isBrillianceActive(sim.getCurrentTime()),
                "Yanfei Brilliance pre-expiry boundary");
        sim.advanceTime(0.001);
        assertTrue(!yanfei.isBrillianceActive(sim.getCurrentTime()),
                "Yanfei Brilliance exact expiry boundary");
        assertClose(0.0, yanfei.getEffectiveStats(
                sim.getCurrentTime()).get(
                        StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "Yanfei Brilliance bonus expires exactly");
    }

    private static void testRepresentableConstellations() {
        for (int constellation = 0; constellation <= 6; constellation++) {
            Yanfei explicit = new Yanfei(null, null, constellation);
            assertEquals(constellation, explicit.getConstellation(),
                    "Yanfei explicit C" + constellation + " construction");
        }

        Yanfei c0 = yanfeiAtConstellation(0);
        Yanfei c1 = yanfeiAtConstellation(1);
        assertEquals(3, c0.getScarletSealLimit(),
                "Yanfei C0 Seal limit");
        assertEquals(3, c1.getScarletSealLimit(),
                "Yanfei C1 has no invented offensive Seal branch");

        Yanfei c3 = yanfeiAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Skill = captureNamedActions(c3Sim, "Signed Edict");
        perform(c3Sim, CharacterActionKey.SKILL);
        assertClose(3.3920,
                c3Skill.get(0).action.getDamagePercent(), EPS,
                "Yanfei C3 Skill talent level");

        Yanfei c5 = yanfeiAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Burst = captureNamedActions(c5Sim, "Done Deal");
        perform(c5Sim, CharacterActionKey.BURST);
        assertClose(3.6480,
                c5Burst.get(0).action.getDamagePercent(), EPS,
                "Yanfei C5 Burst talent level");
        assertClose(0.596, c5.getEffectiveStats(
                c5Sim.getCurrentTime()).get(
                        StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "Yanfei C5 Brilliance talent level");

        Yanfei c6 = yanfeiAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        for (int i = 0; i < 4; i++) {
            perform(c6Sim, CharacterActionKey.NORMAL);
        }
        assertEquals(4, c6.getScarletSealCount(c6Sim.getCurrentTime()),
                "Yanfei C6 fourth Seal");
        List<ActionRecord> c6Charged = captureNamedActions(
                c6Sim, "Seal of Approval Charged (4 Seals)");
        perform(c6Sim, CharacterActionKey.CHARGE);
        assertClose(2.598806,
                c6Charged.get(0).action.getDamagePercent(), EPS,
                "Yanfei C6 four-Seal Charged multiplier");
    }

    private static void testSwitchStaleTimerAndOffFieldNormal() {
        Yanfei yanfei = yanfeiAtConstellation(0);
        TestCharacter teammate = new TestCharacter();
        CombatSimulator sim = simulatorWith(yanfei);
        sim.addCharacter(teammate);
        perform(sim, CharacterActionKey.BURST);
        sim.switchCharacter(CharacterId.NOELLE);
        assertEquals(0, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei switch clears Seals");
        assertTrue(!yanfei.isBrillianceActive(sim.getCurrentTime()),
                "Yanfei switch ends Brilliance");
        sim.advanceTime(2.0);
        assertEquals(0, yanfei.getScarletSealCount(sim.getCurrentTime()),
                "Yanfei stale Brilliance timer cannot grant Seals");

        Yanfei offField = yanfeiAtConstellation(0);
        CombatSimulator offFieldSim = new CombatSimulator();
        offFieldSim.setLoggingEnabled(false);
        offFieldSim.setEnemy(new Enemy(90));
        offFieldSim.addCharacter(new TestCharacter());
        offFieldSim.addCharacter(offField);
        perform(offFieldSim, CharacterActionKey.NORMAL);
        assertEquals(0,
                offField.getScarletSealCount(offFieldSim.getCurrentTime()),
                "Yanfei off-field Normal does not grant a Seal");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Yanfei first = yanfeiAtConstellation(6);
        Yanfei second = yanfeiAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        perform(firstSim, CharacterActionKey.NORMAL);
        assertEquals(1, first.getScarletSealCount(firstSim.getCurrentTime()),
                "First Yanfei owns its Seal state");
        assertEquals(0, second.getScarletSealCount(secondSim.getCurrentTime()),
                "Second Yanfei state remains independent");

        Yanfei reused = yanfeiAtConstellation(0);
        simulatorWith(reused);
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(reused),
                "Yanfei rejects cross-simulator reuse");
    }

    private static void testUnsupportedActionAndInvalidConstellation() {
        Yanfei yanfei = yanfeiAtConstellation(0);
        CombatSimulator sim = simulatorWith(yanfei);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Yanfei unsupported Dash action");
        assertThrows(IllegalArgumentException.class,
                () -> new Yanfei(null, null, -1),
                "Yanfei negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Yanfei(null, null, 7),
                "Yanfei constellation above six");
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Yanfei", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static Yanfei yanfeiAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                defaultValue;
        return new Yanfei(null, null, talentData, constellation);
    }

    private static CombatSimulator simulatorWith(Yanfei yanfei) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(yanfei);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.YANFEI,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureYanfeiActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YANFEI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String actionName) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YANFEI
                    && actionName.equals(action.getName())) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
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

    /** Minimal party member used for switch and active-field checks. */
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
