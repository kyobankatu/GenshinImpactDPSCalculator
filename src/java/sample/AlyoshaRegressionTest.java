package sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Alyosha;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.StellarReactionProvider;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/**
 * Focused Version 7.0 Alyosha fixed-target regression suite.
 *
 * <p>Tests pin Genshin Optimizer commit {@code d791814a}: Lv. 90 stats,
 * base Lv. 9 and C3/C5 Lv. 12 multipliers, zero-frame and zero-gauge
 * boundaries, Hunter's Mark and Precision, A4, C1-C3, C5-C6 represented
 * effects, manual Burst ticks, and complete snapshot restoration.</p>
 */
public final class AlyoshaRegressionTest {
    private static final double EPSILON = 1e-8;

    private AlyoshaRegressionTest() {
    }

    /** Runs every Alyosha regression case. */
    public static void main(String[] args) {
        testMetadataAndA4();
        testNormalAndChargedMultipliers();
        testSkillModesCooldownAndMarkBoundary();
        testC2DurationAndTugarinMark();
        testC3AndC5TalentValues();
        testC6PrecisionAndSnapshot();
        testBurstManualTicksAndBoundaries();
        testC1ElectroReactionGateAndSnapshot();
        testInvalidInputsAndStateOwnership();
        System.out.println("AlyoshaRegressionTest passed");
    }

    private static void testMetadataAndA4() {
        Alyosha alyosha = new Alyosha(null, null);
        assertEquals(CharacterId.ALYOSHA, alyosha.getCharacterId(),
                "Alyosha typed identity");
        assertEquals(Element.ELECTRO, alyosha.getElement(),
                "Alyosha element");
        assertEquals(0, alyosha.getConstellation(),
                "Alyosha default constellation");
        assertClose(11962.4065,
                alyosha.getBaseStats().get(StatType.BASE_HP),
                "Alyosha Lv. 90 HP");
        assertClose(265.4965,
                alyosha.getBaseStats().get(StatType.BASE_ATK),
                "Alyosha Lv. 90 ATK");
        assertClose(702.9972,
                alyosha.getBaseStats().get(StatType.BASE_DEF),
                "Alyosha Lv. 90 DEF");
        assertClose(1.2667,
                alyosha.getBaseStats().get(StatType.ENERGY_RECHARGE),
                "Alyosha Lv. 90 ER");
        assertClose(15.0, alyosha.getSkillCD(), "Alyosha Skill cooldown");
        assertClose(18.0, alyosha.getBurstCD(), "Alyosha Burst cooldown");
        assertClose(70.0, alyosha.getEnergyCost(), "Alyosha Energy cost");

        StatsContainer baseA4 = alyosha.getEffectiveStats(0.0);
        assertClose(1.2667 * 0.35,
                baseA4.get(StatType.SKILL_DMG_BONUS),
                "Alyosha A4 base-ER Skill bonus");
        assertClose(1.2667 * 0.35,
                baseA4.get(StatType.BURST_DMG_BONUS),
                "Alyosha A4 base-ER Burst bonus");

        StatsContainer nonConvertingStats = new StatsContainer();
        nonConvertingStats.set(
                StatType.NON_CONVERTING_ENERGY_RECHARGE,
                0.20);
        Alyosha nonConverting = new Alyosha(
                new Weapon("Alyosha non-converting ER fixture",
                        nonConvertingStats),
                null);
        StatsContainer totalErA4 = nonConverting.getEffectiveStats(0.0);
        assertClose(1.4667 * 0.35,
                totalErA4.get(StatType.SKILL_DMG_BONUS),
                "Alyosha A4 includes non-converting ER in total ER");
        assertClose(1.4667 * 0.35,
                totalErA4.get(StatType.BURST_DMG_BONUS),
                "Alyosha A4 total ER applies equally to Burst");

        StatsContainer weaponStats = new StatsContainer();
        weaponStats.set(StatType.ENERGY_RECHARGE, 1.0);
        Alyosha capped = new Alyosha(
                new Weapon("Alyosha ER Fixture", weaponStats), null);
        StatsContainer cappedA4 = capped.getEffectiveStats(0.0);
        assertClose(0.70,
                cappedA4.get(StatType.SKILL_DMG_BONUS),
                "Alyosha A4 Skill cap");
        assertClose(0.70,
                cappedA4.get(StatType.BURST_DMG_BONUS),
                "Alyosha A4 Burst cap");
    }

    private static void testNormalAndChargedMultipliers() {
        Alyosha alyosha = new Alyosha(null, null);
        CombatSimulator simulator = simulatorWith(alyosha);
        List<AttackAction> actions = captureActions(simulator);

        completeNormalString(simulator);
        assertEquals(5, actions.size(),
                "Alyosha four-input Normal string has five hits");
        assertAction(actions.get(0), 0.87848, ActionType.NORMAL,
                StatType.NORMAL_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha N1");
        assertAction(actions.get(1), 0.8848, ActionType.NORMAL,
                StatType.NORMAL_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha N2");
        assertAction(actions.get(2), 0.62884, ActionType.NORMAL,
                StatType.NORMAL_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha N3-1");
        assertAction(actions.get(3), 0.5846, ActionType.NORMAL,
                StatType.NORMAL_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha N3-2");
        assertAction(actions.get(4), 1.39356, ActionType.NORMAL,
                StatType.NORMAL_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha N4");
        assertTrue(alyosha.isHunterMarkActive(0.0),
                "Alyosha N4 applies Hunter's Mark");

        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertAction(actions.get(5), 2.03978, ActionType.CHARGE,
                StatType.CHARGED_ATTACK_DMG_BONUS, Element.PHYSICAL,
                "Alyosha Charged Attack");
        assertClose(0.0, simulator.getCurrentTime(),
                "Alyosha sourced actions do not infer animation time");
    }

    private static void testSkillModesCooldownAndMarkBoundary() {
        Alyosha alyosha = new Alyosha(null, null);
        CombatSimulator simulator = simulatorWith(alyosha);
        List<AttackAction> actions = captureActions(simulator);

        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
        assertAction(actions.get(0), 4.87424, ActionType.SKILL,
                StatType.SKILL_DMG_BONUS, Element.ELECTRO,
                "Alyosha Press Skill");
        assertTrue(alyosha.isHunterMarkActive(15.0 - EPSILON),
                "Alyosha Skill mark remains before fifteen seconds");
        assertTrue(!alyosha.isHunterMarkActive(15.0),
                "Alyosha Skill mark expires exactly at fifteen seconds");

        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.skill(SkillActionMode.HOLD));
        assertClose(15.0, simulator.getCurrentTime(),
                "Alyosha Hold waits to the exact Skill cooldown boundary");
        assertAction(actions.get(1), 6.0928, ActionType.SKILL,
                StatType.SKILL_DMG_BONUS, Element.ELECTRO,
                "Alyosha Hold Skill");
    }

    private static void testC2DurationAndTugarinMark() {
        Alyosha alyosha = new Alyosha(null, null, 2);
        CombatSimulator simulator = simulatorWith(alyosha);

        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertTrue(alyosha.isBurstFieldActive(20.0 - EPSILON),
                "Alyosha C2 Burst remains active before twenty seconds");

        alyosha.triggerTugarinTick(simulator);
        assertTrue(alyosha.isHunterMarkActive(0.0),
                "Alyosha C2 Tugarin directly applies an absent mark");
        assertEquals(0, alyosha.getPrecisionStackCount(0.0),
                "Alyosha C2 direct mark application does not activate it");

        alyosha.triggerTugarinTick(simulator);
        assertEquals(1, alyosha.getPrecisionStackCount(0.0),
                "Alyosha Tugarin base effect activates the existing mark");
        assertTrue(alyosha.isHunterMarkActive(0.0),
                "Alyosha C2 reapplies the mark without reactivating it");

        simulator.advanceTime(20.0);
        assertTrue(!alyosha.isBurstFieldActive(20.0),
                "Alyosha C2 Burst expires exactly at twenty seconds");
    }

    private static void testC3AndC5TalentValues() {
        Alyosha c3 = new Alyosha(null, null, 3);
        CombatSimulator skillSimulator = simulatorWith(c3);
        List<AttackAction> skillActions = captureActions(skillSimulator);

        skillSimulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
        assertAction(skillActions.get(0), 5.7344, ActionType.SKILL,
                StatType.SKILL_DMG_BONUS, Element.ELECTRO,
                "Alyosha C3 Lv. 12 Press Skill");
        skillSimulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.skill(SkillActionMode.HOLD));
        assertAction(skillActions.get(1), 7.168, ActionType.SKILL,
                StatType.SKILL_DMG_BONUS, Element.ELECTRO,
                "Alyosha C3 Lv. 12 Hold Skill");
        completeNormalString(skillSimulator);
        assertClose(0.23744,
                resolvedStats(c3, skillSimulator).get(StatType.ATK_PERCENT),
                "Alyosha C3 Lv. 12 Precision ATK bonus");

        Alyosha c5 = new Alyosha(null, null, 5);
        CombatSimulator burstSimulator = simulatorWith(c5);
        List<AttackAction> burstActions = captureActions(burstSimulator);
        burstSimulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        c5.triggerFulguriteFieldTick(burstSimulator);
        c5.triggerTugarinTick(burstSimulator);
        assertAction(burstActions.get(0), 1.4992, ActionType.BURST,
                StatType.BURST_DMG_BONUS, Element.ELECTRO,
                "Alyosha C5 Lv. 12 Burst field");
        assertAction(burstActions.get(1), 1.004464, ActionType.BURST,
                StatType.BURST_DMG_BONUS, Element.ELECTRO,
                "Alyosha C5 Lv. 12 Tugarin");
    }

    private static void testC6PrecisionAndSnapshot() {
        Alyosha alyosha = new Alyosha(null, null, 6);
        CombatSimulator simulator = simulatorWith(alyosha);

        completeNormalString(simulator);
        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
        assertEquals(1, alyosha.getPrecisionStackCount(0.0),
                "Alyosha first mark activation grants one Precision stack");

        completeNormalString(simulator);
        completeNormalString(simulator);
        assertEquals(2, alyosha.getPrecisionStackCount(0.0),
                "Alyosha C6 Precision caps at two stacks");
        StatsContainer twoStackStats = resolvedStats(alyosha, simulator);
        assertClose(0.47488,
                twoStackStats.get(StatType.ATK_PERCENT),
                "Alyosha C6 two Precision ATK stacks");
        assertClose(100.0,
                twoStackStats.get(StatType.ELEMENTAL_MASTERY),
                "Alyosha C6 two-stack EM");
        assertClose(0.0,
                twoStackStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Alyosha Precision Stellar bonus requires Conduct Radiance");

        simulator.getStellarReactionManager().triggerStellarConduct(0.0);
        StatsContainer radianceStats = resolvedStats(alyosha, simulator);
        assertClose(0.40,
                radianceStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Alyosha C6 two Precision Stellar-Conduct stacks");

        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(15.0);
        assertEquals(0, alyosha.getPrecisionStackCount(15.0),
                "Alyosha Precision expires at the exact boundary");
        simulator.restoreSnapshot(snapshot);
        assertEquals(2, alyosha.getPrecisionStackCount(0.0),
                "Alyosha snapshot restores Precision stacks");
        StatsContainer restoredStats = resolvedStats(alyosha, simulator);
        assertClose(0.47488,
                restoredStats.get(StatType.ATK_PERCENT),
                "Alyosha snapshot restores Precision field buffs");
        assertClose(100.0,
                restoredStats.get(StatType.ELEMENTAL_MASTERY),
                "Alyosha snapshot restores C6 EM field buff");
    }

    private static void testBurstManualTicksAndBoundaries() {
        Alyosha alyosha = new Alyosha(null, null);
        CombatSimulator simulator = simulatorWith(alyosha);
        List<AttackAction> actions = captureActions(simulator);

        simulator.performAction(
                CharacterId.ALYOSHA,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertTrue(alyosha.isBurstFieldActive(0.0),
                "Alyosha Burst opens Hunter's Advance");
        assertEquals(0, actions.size(),
                "Alyosha Burst infers no initial field or Tugarin hit");
        assertClose(0.0, alyosha.getCurrentEnergy(),
                "Alyosha Burst spends seventy Energy");

        alyosha.triggerFulguriteFieldTick(simulator);
        assertAction(actions.get(0), 1.27432, ActionType.BURST,
                StatType.BURST_DMG_BONUS, Element.ELECTRO,
                "Alyosha manual field tick");

        completeNormalString(simulator);
        assertTrue(alyosha.isHunterMarkActive(0.0),
                "Alyosha marks the fixed target before Tugarin");
        alyosha.triggerTugarinTick(simulator);
        assertAction(actions.get(actions.size() - 1), 0.853794,
                ActionType.BURST, StatType.BURST_DMG_BONUS,
                Element.ELECTRO, "Alyosha manual Tugarin tick");
        assertTrue(!alyosha.isHunterMarkActive(0.0),
                "Alyosha Tugarin activates and removes Hunter's Mark");
        assertEquals(1, alyosha.getPrecisionStackCount(0.0),
                "Alyosha Tugarin activation grants Precision");

        assertTrue(!alyosha.isAutomaticBurstCadenceRepresented(),
                "Alyosha automatic cadence fails closed");
        assertTrue(!alyosha.hasSourceBackedAnimationGaugeIcdAndParticles(),
                "Alyosha unsourced combat metadata fails closed");
        assertTrue(!alyosha.isHealingRepresented(),
                "Alyosha unsourced healing runtime fails closed");
        assertTrue(alyosha.isBurstFieldActive(14.0 - EPSILON),
                "Alyosha Burst remains active before fourteen seconds");
        simulator.advanceTime(14.0);
        assertTrue(!alyosha.isBurstFieldActive(14.0),
                "Alyosha Burst expires exactly at fourteen seconds");
        assertThrows(IllegalStateException.class,
                () -> alyosha.triggerTugarinTick(simulator),
                "Alyosha rejects manual Tugarin outside Burst");
    }

    private static void testC1ElectroReactionGateAndSnapshot() {
        Alyosha alyosha = new Alyosha(null, null, 1);
        TestProvider provider = new TestProvider(CharacterId.GANYU);
        CombatSimulator simulator = simulatorWith(alyosha, provider);
        alyosha.spendEnergy(70.0);

        ReactionResult directNotification = ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_CONDUCT,
                Element.CRYO,
                Element.CRYO,
                false);
        simulator.notifyReaction(directNotification, provider);
        assertClose(0.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 rejects direct Stellar damage notification");

        ReactionResult bloom = ReactionResult.transform(
                0.0, "Bloom", ReactionResult.Kind.BLOOM);
        simulator.notifyReaction(bloom, provider);
        assertClose(0.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 rejects an unrelated reaction");

        ReactionResult overload = ReactionResult.transform(
                0.0, "Overload", ReactionResult.Kind.OVERLOAD);
        simulator.notifyReaction(overload, provider);
        assertClose(15.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 accepts a normal Electro-related reaction");
        assertClose(18.0, alyosha.getNextC1EnergyAt(),
                "Alyosha C1 opens its eighteen-second gate");
        simulator.notifyReaction(overload, provider);
        assertClose(15.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 rejects another reaction during gate");

        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(18.0 - EPSILON);
        simulator.notifyReaction(overload, provider);
        assertClose(15.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 rejects immediately before gate boundary");
        simulator.advanceTime(EPSILON);
        ReactionResult electroSwirl = ReactionResult.transform(
                0.0,
                "Electro Swirl",
                ReactionResult.Kind.SWIRL,
                Element.ELECTRO);
        simulator.notifyReaction(electroSwirl, provider);
        assertClose(30.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 accepts typed Electro Swirl at gate boundary");

        simulator.restoreSnapshot(snapshot);
        assertClose(15.0, alyosha.getCurrentEnergy(),
                "Alyosha snapshot restores C1 Energy state");
        assertClose(18.0, alyosha.getNextC1EnergyAt(),
                "Alyosha snapshot restores C1 gate");

        TestProvider outsider = new TestProvider(CharacterId.KAEYA);
        simulator.advanceTime(18.0);
        simulator.notifyReaction(overload, outsider);
        assertClose(15.0, alyosha.getCurrentEnergy(),
                "Alyosha C1 rejects non-party reaction source");
    }

    private static void testInvalidInputsAndStateOwnership() {
        assertThrows(IllegalArgumentException.class,
                () -> new Alyosha(null, null, -1),
                "Alyosha rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Alyosha(null, null, 7),
                "Alyosha rejects constellation seven");

        Alyosha alyosha = new Alyosha(null, null);
        CombatSimulator simulator = simulatorWith(alyosha);
        assertThrows(IllegalArgumentException.class,
                () -> alyosha.onAction(null, simulator),
                "Alyosha rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.ALYOSHA,
                        CharacterActionRequest.of(CharacterActionKey.PLUNGE)),
                "Alyosha rejects unsupported Plunge");
        assertThrows(IllegalArgumentException.class,
                () -> alyosha.triggerFulguriteFieldTick(
                        new CombatSimulator()),
                "Alyosha rejects manual tick on foreign simulator");

        SnapshotAwareCharacterEffect.State state =
                alyosha.captureCharacterState();
        Alyosha other = new Alyosha(null, null);
        assertTrue(!other.acceptsCharacterState(state),
                "Alyosha rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> alyosha.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() { },
                        simulator),
                "Alyosha rejects another state type");
        assertThrows(IllegalStateException.class,
                () -> alyosha.initializeForSimulator(
                        simulatorWith(new TestProvider(CharacterId.GANYU))),
                "Alyosha rejects simulator rebinding");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new model.entity.Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static List<AttackAction> captureActions(
            CombatSimulator simulator) {
        List<AttackAction> actions = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ALYOSHA) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static void completeNormalString(CombatSimulator simulator) {
        for (int i = 0; i < 4; i++) {
            simulator.performAction(
                    CharacterId.ALYOSHA,
                    CharacterActionRequest.of(CharacterActionKey.NORMAL));
        }
    }

    private static StatsContainer resolvedStats(
            Alyosha alyosha,
            CombatSimulator simulator) {
        StatsContainer stats = alyosha.getEffectiveStats(
                simulator.getCurrentTime());
        for (Buff buff : simulator.getApplicableBuffs(alyosha)) {
            buff.apply(stats, simulator.getCurrentTime());
        }
        return stats;
    }

    private static void assertAction(
            AttackAction action,
            double multiplier,
            ActionType actionType,
            StatType bonusStat,
            Element element,
            String message) {
        assertClose(multiplier, action.getDamagePercent(),
                message + " multiplier");
        assertEquals(actionType, action.getActionType(),
                message + " action type");
        assertEquals(bonusStat, action.getBonusStat(),
                message + " bonus stat");
        assertEquals(element, action.getElement(),
                message + " element");
        assertClose(0.0, action.getAnimationDuration(),
                message + " animation");
        assertClose(0.0, action.getGaugeUnits(),
                message + " gauge");
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
        if (!Objects.equals(expected, actual)) {
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
        throw new AssertionError(
                message + ": expected " + expected.getSimpleName());
    }

    /** Minimal party member that can enable Stellar-Conduct conversion. */
    private static final class TestProvider extends Character
            implements StellarReactionProvider {
        private TestProvider(CharacterId id) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.CRYO;
        }

        @Override
        public boolean enablesStellarConduct() {
            return true;
        }

        @Override
        public boolean enablesStellarSwirl() {
            return false;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }
}
