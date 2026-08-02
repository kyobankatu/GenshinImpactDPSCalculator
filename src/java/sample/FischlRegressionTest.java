package sample;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import mechanics.reaction.ReactionResult;
import model.character.Fischl;
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
 * Focused regression checks for Fischl's single-target offensive slice.
 */
public final class FischlRegressionTest {
    private static final double EPS = 1e-8;

    private FischlRegressionTest() {
    }

    /** Runs data, action, summon, reaction, and constellation checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndCsvShape();
        testNormalChargedAndPlungeActions();
        testSkillSummonTimingLifetimeParticlesAndCooldown();
        testSkillRecastAndSnapshotRefresh();
        testBurstDamageOzResetAndSwitchPersistence();
        testA4ReactionEligibilityAndCooldown();
        testConstellationsOneThroughSix();
        testC6OncePerNormalAndSharedOzIcd();
        testTypedAndUnsupportedBoundaries();
        testIndependentInstancesAndSimulatorBinding();
        testInvalidConstellation();
        System.out.println("FischlRegressionTest passed");
    }

    private static void testIdentityDataAndCsvShape() throws IOException {
        Fischl fischl = fischlAtConstellation(6);
        assertEquals(CharacterId.FISCHL, fischl.getCharacterId(),
                "Fischl typed id");
        assertEquals("Fischl", fischl.getName(), "Fischl display name");
        assertEquals(Element.ELECTRO, fischl.getElement(),
                "Fischl element");
        assertEquals(CharacterId.FISCHL, CharacterId.fromName("Fischl"),
                "Fischl name lookup");
        assertEquals(CharacterId.FISCHL, CharacterId.fromNumericId(15),
                "Fischl numeric lookup");
        assertClose(9189.0,
                fischl.getBaseStats().get(StatType.BASE_HP), EPS,
                "Fischl base HP");
        assertClose(244.0,
                fischl.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Fischl base ATK");
        assertClose(594.0,
                fischl.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Fischl base DEF");
        assertClose(0.24,
                fischl.getBaseStats().get(StatType.ATK_PERCENT), EPS,
                "Fischl ascension ATK");
        assertClose(60.0, fischl.getEnergyCost(), EPS,
                "Fischl Energy cost");

        assertCsvShape(
                Paths.get("config/characters/Fischl/Fischl_Status.csv"),
                10);
        assertCsvShape(
                Paths.get(
                        "config/characters/Fischl/Fischl_Multipliers.csv"),
                20);
    }

    private static void testNormalChargedAndPlungeActions() {
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        List<ActionRecord> records = captureFischlActions(sim);
        double[] multipliers = {
                0.81054, 0.85952, 1.06808, 1.06018, 1.32404
        };
        double[] durations = {
                25.0 / 60.0,
                22.0 / 60.0,
                38.0 / 60.0,
                32.0 / 60.0,
                67.0 / 60.0
        };
        double expectedTime = 0.0;
        for (int i = 0; i < multipliers.length; i++) {
            perform(sim, CharacterActionKey.NORMAL);
            AttackAction action = records.get(i).action;
            assertEquals("Bolts of Downfall N" + (i + 1),
                    action.getName(), "Fischl Normal name");
            assertClose(expectedTime, records.get(i).time, EPS,
                    "Fischl Normal resolution time");
            assertClose(multipliers[i], action.getDamagePercent(), EPS,
                    "Fischl Normal multiplier");
            assertClose(durations[i], action.getAnimationDuration(), EPS,
                    "Fischl Normal duration");
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Fischl Normal type");
            assertEquals(Element.PHYSICAL, action.getElement(),
                    "Fischl Normal element");
            assertClose(0.0, action.getGaugeUnits(), EPS,
                    "Fischl Normal gauge");
            expectedTime += durations[i];
        }
        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Bolts of Downfall N1", records.get(0).action.getName(),
                "Fischl Normal chain wrap");

        records.clear();
        perform(sim, CharacterActionKey.CHARGE);
        AttackAction charged = records.get(0).action;
        assertClose(2.108, charged.getDamagePercent(), EPS,
                "Fischl fully charged multiplier");
        assertEquals(Element.ELECTRO, charged.getElement(),
                "Fischl fully charged element");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Fischl charged type");
        assertEquals(ICDType.None, charged.getICDType(),
                "Fischl fully charged no ICD");
        assertClose(1.0, charged.getGaugeUnits(), EPS,
                "Fischl fully charged gauge");
        assertClose(96.0 / 60.0, charged.getAnimationDuration(), EPS,
                "Fischl fully charged duration");

        records.clear();
        perform(sim, CharacterActionKey.PLUNGE);
        AttackAction plunge = records.get(0).action;
        assertClose(2.6086, plunge.getDamagePercent(), EPS,
                "Fischl high Plunge multiplier");
        assertEquals(Element.PHYSICAL, plunge.getElement(),
                "Fischl high Plunge element");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Fischl high Plunge type");
        assertClose(0.0, plunge.getGaugeUnits(), EPS,
                "Fischl high Plunge gauge");
    }

    private static void testSkillSummonTimingLifetimeParticlesAndCooldown() {
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        List<ActionRecord> summon = captureNamedActions(sim, "Oz Summon");
        List<ActionRecord> ticks = captureNamedActions(sim, "Oz Attack");

        perform(sim, CharacterActionKey.SKILL);
        assertClose(43.0 / 60.0, sim.getCurrentTime(), EPS,
                "Fischl Skill action duration");
        assertEquals(1, summon.size(), "Fischl summon hit count");
        assertClose(38.0 / 60.0, summon.get(0).time, EPS,
                "Fischl summon hitmark");
        assertClose(1.96248,
                summon.get(0).action.getDamagePercent(), EPS,
                "Fischl C0 summon multiplier");
        assertEquals(ICDType.None, summon.get(0).action.getICDType(),
                "Fischl summon no ICD");
        assertClose(1.0, summon.get(0).action.getGaugeUnits(), EPS,
                "Fischl summon gauge");
        assertTrue(fischl.isOzActive(sim.getCurrentTime()),
                "Fischl Oz active after Skill");
        assertClose(18.0 / 60.0 + 10.0,
                fischl.getOzActiveUntil(), EPS,
                "Fischl C0 Oz lifetime");
        assertClose(0.0, fischl.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Fischl Skill exposes recast while Oz active");
        assertEquals(0, ticks.size(), "Fischl Oz waits for first tick");

        sim.advanceTime(93.0 / 60.0 - sim.getCurrentTime() - 0.001);
        assertEquals(0, ticks.size(), "Fischl Oz pre-first-tick boundary");
        sim.advanceTime(0.002);
        assertEquals(1, ticks.size(), "Fischl Oz first tick");
        assertClose(93.0 / 60.0, ticks.get(0).time, EPS,
                "Fischl Oz first damage frame");
        assertClose(1.5096, ticks.get(0).action.getDamagePercent(), EPS,
                "Fischl C0 Oz multiplier");
        assertTrue(ticks.get(0).action.isUseSnapshot(),
                "Fischl Oz uses cast snapshot");
        assertClose(2.01, fischl.getTotalParticleEnergy(), EPS,
                "Fischl expected 0.67 same-element particle Energy");

        double secondTick = 93.0 / 60.0 + 59.0 / 60.0;
        sim.advanceTime(secondTick - sim.getCurrentTime() - 0.001);
        assertEquals(1, ticks.size(), "Fischl Oz pre-interval boundary");
        sim.advanceTime(0.002);
        assertEquals(2, ticks.size(), "Fischl Oz 59-frame interval");

        sim.advanceTime(fischl.getOzActiveUntil()
                - sim.getCurrentTime() - 0.001);
        assertTrue(fischl.isOzActive(sim.getCurrentTime()),
                "Fischl Oz pre-expiry boundary");
        sim.advanceTime(0.002);
        assertTrue(!fischl.isOzActive(sim.getCurrentTime()),
                "Fischl Oz expiry boundary");
        assertClose(18.0 / 60.0 + 25.0 - sim.getCurrentTime(),
                fischl.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Fischl underlying Skill cooldown after Oz expires");
    }

    private static void testSkillRecastAndSnapshotRefresh() {
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        fischl.addBuff(new SimpleBuff(
                "Short ATK",
                0.74,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        List<ActionRecord> summon = captureNamedActions(sim, "Oz Summon");
        List<ActionRecord> ticks = captureNamedActions(sim, "Oz Attack");
        perform(sim, CharacterActionKey.SKILL);
        double originalExpiry = fischl.getOzActiveUntil();
        double recastStart = sim.getCurrentTime();
        perform(sim, CharacterActionKey.SKILL);

        assertEquals(1, summon.size(),
                "Fischl recast should not repeat summon damage");
        assertClose(originalExpiry, fischl.getOzActiveUntil(), EPS,
                "Fischl recast should not extend Oz lifetime");
        assertClose(recastStart + 37.0 / 60.0,
                sim.getCurrentTime(), EPS,
                "Fischl recast duration");
        assertTrue(fischl.getSkillCDRemaining(sim.getCurrentTime()) > 0.0,
                "Fischl recast cooldown active");
        assertClose(92.0 / 60.0 - 37.0 / 60.0,
                fischl.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Fischl recast cooldown starts on input");

        double expectedTick = recastStart + 2.0 / 60.0 + 70.0 / 60.0;
        sim.advanceTime(expectedTick - sim.getCurrentTime() - 0.001);
        assertEquals(0, ticks.size(),
                "Fischl recast resets pending Oz timer");
        sim.advanceTime(0.002);
        assertEquals(1, ticks.size(), "Fischl recast first tick");
        assertClose(expectedTick, ticks.get(0).time, EPS,
                "Fischl recast first tick time");

        Fischl buffed = fischlAtConstellation(0);
        CombatSimulator buffedSim = simulatorWith(buffed);
        buffed.addBuff(new SimpleBuff(
                "Snapshot ATK",
                0.5,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        List<ActionRecord> buffedTicks = captureNamedActions(
                buffedSim, "Oz Attack");
        perform(buffedSim, CharacterActionKey.SKILL);
        buffedSim.advanceTime(93.0 / 60.0 - buffedSim.getCurrentTime());

        Fischl plain = fischlAtConstellation(0);
        CombatSimulator plainSim = simulatorWith(plain);
        List<ActionRecord> plainTicks = captureNamedActions(
                plainSim, "Oz Attack");
        perform(plainSim, CharacterActionKey.SKILL);
        plainSim.advanceTime(93.0 / 60.0 - plainSim.getCurrentTime());
        assertClose(plainTicks.get(0).damage, ticks.get(0).damage, EPS,
                "Fischl recast refreshes to post-buff stats");
        assertTrue(buffedTicks.get(0).damage > plainTicks.get(0).damage,
                "Fischl Oz retains expired cast-time ATK snapshot");
        assertClose(0.0,
                buffed.getEffectiveStats(buffedSim.getCurrentTime())
                        .get(StatType.ATK_PERCENT)
                        - buffed.getBaseStats().get(StatType.ATK_PERCENT),
                EPS,
                "Fischl snapshot buff expired from live stats");
    }

    private static void testBurstDamageOzResetAndSwitchPersistence() {
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        sim.addCharacter(new TestCharacter());
        List<ActionRecord> burst = captureNamedActions(
                sim, "Midnight Phantasmagoria");
        List<ActionRecord> ticks = captureNamedActions(sim, "Oz Attack");

        perform(sim, CharacterActionKey.BURST);
        assertClose(148.0 / 60.0, sim.getCurrentTime(), EPS,
                "Fischl Burst full action duration");
        assertEquals(1, burst.size(), "Fischl Burst hit count");
        assertClose(18.0 / 60.0, burst.get(0).time, EPS,
                "Fischl Burst hitmark");
        assertClose(3.536, burst.get(0).action.getDamagePercent(), EPS,
                "Fischl C0 Burst multiplier");
        assertClose(0.0, fischl.getCurrentEnergy(), EPS,
                "Fischl Burst Energy spend");
        assertClose(15.0, fischl.getBurstCooldownEndTime(), EPS,
                "Fischl Burst cooldown");
        assertTrue(!fischl.isFormActive(sim.getCurrentTime()),
                "Fischl full Burst form ended before request returns");
        assertTrue(fischl.isOzActive(sim.getCurrentTime()),
                "Fischl Burst deploys Oz");
        assertClose(113.0 / 60.0 + 10.0,
                fischl.getOzActiveUntil(), EPS,
                "Fischl Burst resets Oz duration at deployment");

        sim.setActiveCharacter(CharacterId.NOELLE);
        assertTrue(fischl.isOzActive(sim.getCurrentTime()),
                "Fischl Oz persists after switch");
        sim.advanceTime(192.0 / 60.0 - sim.getCurrentTime());
        assertEquals(1, ticks.size(), "Fischl Burst Oz first tick");
        assertClose(192.0 / 60.0, ticks.get(0).time, EPS,
                "Fischl Burst Oz first damage frame");

        Fischl reset = fischlAtConstellation(0);
        CombatSimulator resetSim = simulatorWith(reset);
        perform(resetSim, CharacterActionKey.SKILL);
        double skillExpiry = reset.getOzActiveUntil();
        perform(resetSim, CharacterActionKey.BURST);
        assertTrue(reset.getOzActiveUntil() > skillExpiry,
                "Fischl Burst replaces and resets an existing Oz");
    }

    private static void testA4ReactionEligibilityAndCooldown() {
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        TestCharacter teammate = new TestCharacter();
        sim.addCharacter(teammate);
        List<ActionRecord> a4 = captureNamedActions(
                sim, "Thundering Retribution");

        sim.notifyReaction(ReactionResult.transform(
                1.0, "Overloaded", ReactionResult.Kind.OVERLOAD), fischl);
        assertEquals(0, a4.size(), "Fischl A4 requires Oz");
        perform(sim, CharacterActionKey.SKILL);
        double firstReactionTime = sim.getCurrentTime();
        sim.notifyReaction(ReactionResult.transform(
                1.0, "Overloaded", ReactionResult.Kind.OVERLOAD), fischl);
        assertEquals(0, a4.size(), "Fischl A4 waits four frames");
        sim.advanceTime(4.0 / 60.0);
        assertEquals(1, a4.size(), "Fischl A4 active reaction trigger");
        assertClose(firstReactionTime + 4.0 / 60.0,
                a4.get(0).time, EPS, "Fischl A4 hit delay");
        AttackAction first = a4.get(0).action;
        assertClose(0.80, first.getDamagePercent(), EPS,
                "Fischl A4 multiplier");
        assertEquals(ActionType.SKILL, first.getActionType(),
                "Fischl A4 Skill classification");
        assertEquals(ICDType.None, first.getICDType(),
                "Fischl A4 no ICD");
        assertClose(1.0, first.getGaugeUnits(), EPS,
                "Fischl A4 gauge");
        assertTrue(first.isUseSnapshot(), "Fischl A4 uses Oz snapshot");

        sim.notifyReaction(ReactionResult.transform(
                1.0, "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED), fischl);
        assertEquals(1, a4.size(), "Fischl A4 cooldown gate");
        sim.advanceTime(firstReactionTime + 0.5 - sim.getCurrentTime());
        sim.notifyReaction(ReactionResult.state(
                "Quicken", ReactionResult.Kind.QUICKEN, null), fischl);
        sim.advanceTime(4.0 / 60.0);
        assertEquals(2, a4.size(), "Fischl A4 half-open cooldown boundary");

        sim.advanceTime(0.5);
        sim.setActiveCharacter(CharacterId.NOELLE);
        sim.notifyReaction(ReactionResult.additive(
                1.0, "Spread", ReactionResult.Kind.SPREAD,
                Element.DENDRO), teammate);
        assertEquals(2, a4.size(), "Fischl A4 rejects Spread");
        sim.notifyReaction(ReactionResult.transform(
                1.0, "Overloaded", ReactionResult.Kind.OVERLOAD), fischl);
        assertEquals(2, a4.size(),
                "Fischl A4 requires reaction source to be active");
        sim.notifyReaction(ReactionResult.transform(
                1.0, "Superconduct", ReactionResult.Kind.SUPERCONDUCT),
                teammate);
        assertEquals(2, a4.size(),
                "Fischl teammate A4 waits four frames");
        sim.advanceTime(4.0 / 60.0);
        assertEquals(3, a4.size(),
                "Fischl A4 supports an active teammate trigger");
    }

    private static void testConstellationsOneThroughSix() {
        Fischl c1 = fischlAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Hits = captureNamedActions(
                c1Sim, "Gaze of the Deep");
        perform(c1Sim, CharacterActionKey.NORMAL);
        assertEquals(1, c1Hits.size(), "Fischl C1 coordinated hit");
        assertClose(0.22, c1Hits.get(0).action.getDamagePercent(), EPS,
                "Fischl C1 multiplier");
        assertEquals(Element.PHYSICAL, c1Hits.get(0).action.getElement(),
                "Fischl C1 element");
        assertEquals(ActionType.NORMAL, c1Hits.get(0).action.getActionType(),
                "Fischl C1 Normal classification");

        Fischl c2 = fischlAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Summon = captureNamedActions(c2Sim, "Oz Summon");
        perform(c2Sim, CharacterActionKey.SKILL);
        assertClose(1.96248 + 2.0,
                c2Summon.get(0).action.getDamagePercent(), EPS,
                "Fischl C2 additive summon multiplier");

        Fischl c3 = fischlAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Summon = captureNamedActions(c3Sim, "Oz Summon");
        List<ActionRecord> c3Ticks = captureNamedActions(c3Sim, "Oz Attack");
        perform(c3Sim, CharacterActionKey.SKILL);
        c3Sim.advanceTime(93.0 / 60.0 - c3Sim.getCurrentTime());
        assertClose(2.3088 + 2.0,
                c3Summon.get(0).action.getDamagePercent(), EPS,
                "Fischl C3 Skill talent level");
        assertClose(1.776, c3Ticks.get(0).action.getDamagePercent(), EPS,
                "Fischl C3 Oz talent level");

        Fischl c4 = fischlAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        List<ActionRecord> c4Hits = captureFischlActions(c4Sim);
        perform(c4Sim, CharacterActionKey.BURST);
        assertEquals("Her Pilgrimage of Bleak",
                c4Hits.get(0).action.getName(),
                "Fischl C4 resolves before Burst");
        assertClose(8.0 / 60.0, c4Hits.get(0).time, EPS,
                "Fischl C4 hitmark");
        assertClose(2.22, c4Hits.get(0).action.getDamagePercent(), EPS,
                "Fischl C4 multiplier");
        assertEquals(ICDTag.ElementalBurst,
                c4Hits.get(0).action.getICDTag(),
                "Fischl C4 shares Burst ICD tag");
        assertClose(2.0, c4Hits.get(0).action.getGaugeUnits(), EPS,
                "Fischl C4 gauge");

        Fischl c5 = fischlAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Burst = captureNamedActions(
                c5Sim, "Midnight Phantasmagoria");
        perform(c5Sim, CharacterActionKey.BURST);
        assertClose(4.16, c5Burst.get(0).action.getDamagePercent(), EPS,
                "Fischl C5 Burst talent level");

        Fischl c6 = fischlAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        perform(c6Sim, CharacterActionKey.SKILL);
        assertClose(18.0 / 60.0 + 12.0,
                c6.getOzActiveUntil(), EPS,
                "Fischl C6 Oz duration extension");
    }

    private static void testC6OncePerNormalAndSharedOzIcd() {
        Fischl fischl = fischlAtConstellation(6);
        CombatSimulator sim = simulatorWith(fischl);
        TestCharacter teammate = new TestCharacter();
        sim.addCharacter(teammate);
        List<ActionRecord> c6 = captureNamedActions(sim, "Evernight Raven");
        List<ActionRecord> oz = captureNamedActions(sim, "Oz Attack");
        perform(sim, CharacterActionKey.SKILL);
        sim.setActiveCharacter(CharacterId.NOELLE);

        AttackAction firstHit = normalFixture("Multi Normal 1");
        AttackAction secondHit = normalFixture("Multi Normal 2");
        sim.performActionWithoutTimeAdvance(CharacterId.NOELLE, firstHit);
        sim.performActionWithoutTimeAdvance(CharacterId.NOELLE, secondHit);
        assertEquals(0, c6.size(), "Fischl C6 waits for Oz travel");
        sim.advanceTime(10.0 / 60.0);
        assertEquals(1, c6.size(),
                "Fischl C6 triggers once for same-time multi-hit Normal");
        assertClose(43.0 / 60.0 + 10.0 / 60.0,
                c6.get(0).time, EPS, "Fischl C6 travel delay");
        assertClose(0.30, c6.get(0).action.getDamagePercent(), EPS,
                "Fischl C6 multiplier");
        assertEquals(ActionType.SKILL, c6.get(0).action.getActionType(),
                "Fischl C6 Skill classification");
        assertTrue(c6.get(0).action.isUseSnapshot(),
                "Fischl C6 uses Oz snapshot");
        assertClose(1.0, c6.get(0).action.getGaugeUnits(), EPS,
                "Fischl C6 first shared-ICD application");

        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, normalFixture("Normal 2"));
        sim.advanceTime(10.0 / 60.0);
        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, normalFixture("Normal 3"));
        sim.advanceTime(10.0 / 60.0);
        assertClose(0.0, c6.get(1).action.getGaugeUnits(), EPS,
                "Fischl C6 second shared-ICD hit blocked");
        assertClose(0.0, c6.get(2).action.getGaugeUnits(), EPS,
                "Fischl C6 third shared-ICD hit blocked");

        sim.advanceTime(93.0 / 60.0 - sim.getCurrentTime());
        assertEquals(1, oz.size(), "Fischl Oz shared-ICD fourth hit");
        assertClose(1.0, oz.get(0).action.getGaugeUnits(), EPS,
                "Fischl Oz shares and completes C6 four-hit ICD");

        sim.performActionWithoutTimeAdvance(
                CharacterId.FISCHL, normalFixture("Off-field Fischl Normal"));
        assertEquals(3, c6.size(),
                "Fischl C6 rejects Normal damage from an off-field actor");
    }

    private static void testTypedAndUnsupportedBoundaries() {
        boolean hasWeakPointControlPath = false;
        for (Method method : Fischl.class.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("weakpoint")) {
                hasWeakPointControlPath = true;
            }
        }
        assertTrue(!hasWeakPointControlPath,
                "Fischl must not invent a weak-point A1 control path");
        Fischl fischl = fischlAtConstellation(0);
        CombatSimulator sim = simulatorWith(fischl);
        assertThrows(IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Fischl unsupported Dash action");
    }

    private static void testIndependentInstancesAndSimulatorBinding() {
        Fischl first = fischlAtConstellation(6);
        Fischl second = fischlAtConstellation(6);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        perform(firstSim, CharacterActionKey.SKILL);
        assertTrue(first.isOzActive(firstSim.getCurrentTime()),
                "First Fischl Oz state");
        assertTrue(!second.isOzActive(secondSim.getCurrentTime()),
                "Second Fischl Oz state remains independent");

        Fischl reused = fischlAtConstellation(0);
        CombatSimulator original = simulatorWith(reused);
        assertTrue(original.getCharacter(CharacterId.FISCHL) == reused,
                "Fischl initialized in first simulator");
        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(reused),
                "Fischl listener state rejects cross-simulator reuse");
    }

    private static void testInvalidConstellation() {
        assertThrows(IllegalArgumentException.class,
                () -> fischlAtConstellation(-1),
                "Fischl negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> fischlAtConstellation(7),
                "Fischl constellation above six");
    }

    private static AttackAction normalFixture(String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.Standard, ICDTag.NormalAttack, 0.0);
        return action;
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Fischl", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static Fischl fischlAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                "Constellation".equals(key) ? constellation : defaultValue;
        return new Fischl(null, null, talentData);
    }

    private static CombatSimulator simulatorWith(Fischl fischl) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(fischl);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.FISCHL,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureFischlActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.FISCHL) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String actionName) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.FISCHL
                    && actionName.equals(action.getName())) {
                records.add(new ActionRecord(action, damage, time));
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

    /** Minimal party member used for active-source and switch checks. */
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
