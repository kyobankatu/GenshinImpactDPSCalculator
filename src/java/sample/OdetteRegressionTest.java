package sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import mechanics.buff.Buff;
import model.character.Odette;
import model.entity.Character;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
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

/**
 * Focused Version 7.0 Odette fixed-target regression suite.
 *
 * <p>Tests pin Genshin Optimizer revision {@code d791814a}: level 90 stats,
 * talent level 9 and constellation-adjusted level 12 multipliers, direct
 * Stellar type selection, half-open windows, Splendor channels, A4 and base
 * conversion caps, effective C2 resistance reduction, C4 accepted-damage
 * gating, C6 stack limits, and complete mutable-state restoration.</p>
 */
public final class OdetteRegressionTest {
    private static final double EPSILON = 1e-8;

    private OdetteRegressionTest() {
    }

    /** Runs every Odette fixed-target regression case. */
    public static void main(String[] args) {
        testMetadataAndTalentLevelNineNormals();
        testSkillCodaRadianceAndLevelTwelve();
        testSplendorChannelsA4AndBaseConversion();
        testBurstAndHalfOpenWindows();
        testC2ResistanceBuffChangesDamage();
        testC4CoordinatedGateTypesAndSnapshot();
        testC6TransferCapAndFullStateSnapshot();
        testInvalidInputsAndStateOwnership();
        System.out.println("OdetteRegressionTest passed");
    }

    private static void testMetadataAndTalentLevelNineNormals() {
        Odette odette = new Odette(null, null);
        assertEquals(CharacterId.ODETTE, odette.getCharacterId(),
                "Odette typed identity");
        assertEquals(Element.CRYO, odette.getElement(), "Odette element");
        assertEquals(0, odette.getConstellation(),
                "Odette default constellation");
        assertClose(12980.6656,
                odette.getBaseStats().get(StatType.BASE_HP),
                "Odette level 90 HP");
        assertClose(334.8497,
                odette.getBaseStats().get(StatType.BASE_ATK),
                "Odette level 90 ATK");
        assertClose(786.9997,
                odette.getBaseStats().get(StatType.BASE_DEF),
                "Odette level 90 DEF");
        assertClose(0.884,
                odette.getBaseStats().get(StatType.CRIT_DMG),
                "Odette ascension CRIT DMG plus base");
        assertClose(15.0, odette.getSkillCD(), "Odette Skill cooldown");
        assertClose(15.0, odette.getBurstCD(), "Odette Burst cooldown");
        assertClose(60.0, odette.getEnergyCost(), "Odette Burst cost");
        assertTrue(odette.enablesStellarConduct(),
                "Odette enables Stellar-Conduct");
        assertTrue(odette.enablesStellarSwirl(),
                "Odette enables Stellar-Swirl");

        CombatSimulator simulator = simulatorWith(odette);
        List<AttackAction> actions = captureOdetteActions(simulator);
        for (int input = 0; input < 5; input++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        double[] multipliers = {
            0.952724, 0.946357, 0.599136,
            0.703258, 1.370065, 1.657388
        };
        assertEquals(multipliers.length, actions.size(),
                "Odette five-input Normal string hit count");
        for (int index = 0; index < multipliers.length; index++) {
            assertAction(
                    actions.get(index),
                    multipliers[index],
                    ActionType.NORMAL,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    Element.PHYSICAL,
                    "Odette TL9 Normal hit " + index);
        }
        perform(simulator, CharacterActionKey.CHARGE);
        assertAction(
                actions.get(6),
                1.97342,
                ActionType.CHARGE,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                Element.PHYSICAL,
                "Odette TL9 Charged Attack");
        assertClose(0.0, simulator.getCurrentTime(),
                "Odette does not infer action frames");
    }

    private static void testSkillCodaRadianceAndLevelTwelve() {
        Odette c0 = new Odette(null, null);
        CombatSimulator noRadiance = simulatorWith(c0);
        List<AttackAction> noRadianceActions = captureOdetteActions(noRadiance);
        perform(noRadiance, CharacterActionKey.SKILL);
        assertAction(noRadianceActions.get(0), 1.83736,
                ActionType.SKILL, StatType.SKILL_DMG_BONUS, Element.CRYO,
                "Odette TL9 Skill cast");
        c0.performCoda(noRadiance);
        assertAction(noRadianceActions.get(1), 1.62928,
                ActionType.SKILL, StatType.SKILL_DMG_BONUS, Element.CRYO,
                "Odette TL9 Coda damage over time");
        assertStellarAction(noRadianceActions.get(2), 5.19792,
                AttackAction.StellarReactionType.CONDUCT,
                "Odette no-Radiance Coda defaults to Conduct");

        noRadiance.getStellarReactionManager().triggerStellarSwirl(0.0);
        c0.performPlume(noRadiance);
        assertAction(noRadianceActions.get(3), 0.73168,
                ActionType.SKILL, StatType.SKILL_DMG_BONUS, Element.CRYO,
                "Odette TL9 Plume regular hit");
        assertStellarAction(noRadianceActions.get(4), 0.688976,
                AttackAction.StellarReactionType.SWIRL,
                "Odette TL9 Plume Swirl follow-up");

        Odette priority = new Odette(null, null);
        CombatSimulator both = simulatorWith(priority);
        List<AttackAction> bothActions = captureOdetteActions(both);
        perform(both, CharacterActionKey.SKILL);
        both.getStellarReactionManager().triggerStellarSwirl(0.0);
        both.getStellarReactionManager().triggerStellarConduct(0.0);
        priority.performCoda(both);
        assertStellarAction(bothActions.get(2), 5.19792,
                AttackAction.StellarReactionType.CONDUCT,
                "Odette Conduct Radiance has priority");
        priority.performWing(both);
        assertStellarAction(bothActions.get(4), 0.549304,
                AttackAction.StellarReactionType.CONDUCT,
                "Odette TL9 Wing Conduct follow-up");

        Odette c3 = new Odette(null, null, 3);
        CombatSimulator levelTwelve = simulatorWith(c3);
        List<AttackAction> c3Actions = captureOdetteActions(levelTwelve);
        perform(levelTwelve, CharacterActionKey.SKILL);
        c3.performCoda(levelTwelve);
        c3.performPlume(levelTwelve);
        assertClose(2.1616, c3Actions.get(0).getDamagePercent(),
                "Odette C3 TL12 Skill cast");
        assertClose(1.9168, c3Actions.get(1).getDamagePercent(),
                "Odette C3 TL12 Coda regular hit");
        assertClose(6.1152, c3Actions.get(2).getDamagePercent(),
                "Odette C3 TL12 Coda Conduct hit");
        assertClose(3.0, c3Actions.get(3).getDamagePercent(),
                "Odette C3 retains C1 Coda follow-up");
        assertClose(0.8608, c3Actions.get(4).getDamagePercent(),
                "Odette C3 TL12 Plume regular hit");
    }

    private static void testSplendorChannelsA4AndBaseConversion() {
        Odette base = new Odette(null, null);
        CombatSimulator baseSimulator = simulatorWith(base);
        List<AttackAction> baseActions = captureOdetteActions(baseSimulator);
        perform(baseSimulator, CharacterActionKey.SKILL);
        StatsContainer owner = base.getEffectiveStats(0.0);
        assertClose(0.60,
                owner.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "A1 owner Splendor uses ordinary Conduct DMG channel");
        assertClose(0.60,
                owner.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "A1 owner Splendor uses ordinary Swirl DMG channel");
        assertClose(0.0,
                owner.get(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "A1 owner Splendor does not use special channel");
        base.performCoda(baseSimulator);
        StatsContainer baseSnapshot = baseActions.get(2).getStatSnapshot();
        assertClose(334.8497 * 0.00007,
                baseSnapshot.get(StatType.STELLAR_CONDUCT_BASE_DMG_BONUS),
                "Odette uncapped Conduct base conversion");
        assertClose(334.8497 * 0.00007,
                baseSnapshot.get(StatType.STELLAR_SWIRL_BASE_DMG_BONUS),
                "Odette uncapped Swirl base conversion");
        assertClose(0.0,
                baseSnapshot.get(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "A4 remains zero below 1000 ATK");

        StatsContainer weaponStats = new StatsContainer();
        weaponStats.set(StatType.ATK_FLAT, 3000.0);
        Odette capped = new Odette(
                new Weapon("Odette ATK Fixture", weaponStats), null);
        CombatSimulator cappedSimulator = simulatorWith(capped);
        List<AttackAction> cappedActions = captureOdetteActions(cappedSimulator);
        perform(cappedSimulator, CharacterActionKey.SKILL);
        capped.performCoda(cappedSimulator);
        StatsContainer cappedSnapshot = cappedActions.get(2).getStatSnapshot();
        assertClose(0.14,
                cappedSnapshot.get(StatType.STELLAR_CONDUCT_BASE_DMG_BONUS),
                "Odette Conduct base conversion cap");
        assertClose(0.14,
                cappedSnapshot.get(StatType.STELLAR_SWIRL_BASE_DMG_BONUS),
                "Odette Swirl base conversion cap");
        assertClose(0.30,
                cappedSnapshot.get(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "Odette A4 original-damage special cap");

        TestCharacter ally = new TestCharacter(CharacterId.GANYU, Element.CRYO);
        base.onSwitchOut(baseSimulator);
        baseSimulator.advanceTime(1.0);
        CombatSimulator teamSimulator = simulatorWith(
                new Odette(null, null), ally);
        Odette teamOdette = (Odette) teamSimulator.getCharacter(
                CharacterId.ODETTE);
        perform(teamSimulator, CharacterActionKey.SKILL);
        teamOdette.onSwitchOut(teamSimulator);
        teamSimulator.advanceTime(1.0);
        StatsContainer teamStats = resolvedStats(ally, teamSimulator);
        assertClose(0.15,
                teamStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "shared Splendor uses ordinary Conduct DMG channel");
        assertClose(0.0,
                teamStats.get(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "shared Splendor does not use special channel");
    }

    private static void testBurstAndHalfOpenWindows() {
        Odette c0 = new Odette(null, null);
        CombatSimulator simulator = simulatorWith(c0);
        List<AttackAction> actions = captureOdetteActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(4, actions.size(), "Odette Burst has four sourced hits");
        for (int hit = 0; hit < 3; hit++) {
            assertClose(1.872992, actions.get(hit).getDamagePercent(),
                    "Odette TL9 Burst slash " + hit);
        }
        assertClose(2.894624, actions.get(3).getDamagePercent(),
                "Odette TL9 Burst final");
        assertClose(1.06,
                c0.getEffectiveStats(0.0).get(
                        StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Odette Burst bonus combines with four Splendor stacks");
        assertTrue(c0.isBurstBonusActive(20.0 - 1e-6),
                "Odette Burst bonus active before twenty seconds");
        assertTrue(!c0.isBurstBonusActive(20.0),
                "Odette Burst bonus expires at twenty seconds");

        Odette c5 = new Odette(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<AttackAction> c5Actions = captureOdetteActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(2.20352, c5Actions.get(0).getDamagePercent(),
                "Odette C5 TL12 Burst slash");
        assertClose(3.40544, c5Actions.get(3).getDamagePercent(),
                "Odette C5 TL12 Burst final");
        assertClose(1.48,
                c5.getEffectiveStats(0.0).get(
                        StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Odette C5 Burst .58 plus six Splendor stacks");

        Odette boundary = new Odette(null, null);
        CombatSimulator boundarySimulator = simulatorWith(boundary);
        perform(boundarySimulator, CharacterActionKey.SKILL);
        boundarySimulator.advanceTime(6.0);
        assertTrue(!boundary.isCodaAvailable(6.0),
                "Odette Coda uses a six-second half-open window");
        assertThrows(IllegalStateException.class,
                () -> boundary.performCoda(boundarySimulator),
                "Odette rejects Coda at exact expiry");
    }

    private static void testC2ResistanceBuffChangesDamage() {
        double c0Damage = cryoDamageInsideConduct(new Odette(null, null));
        double c2Damage = cryoDamageInsideConduct(new Odette(null, null, 2));
        assertTrue(c2Damage > c0Damage,
                "Odette C2 RES Buff changes resolved Cryo damage");

        Odette c2 = new Odette(null, null, 2);
        TestCharacter ally = new TestCharacter(CharacterId.GANYU, Element.CRYO);
        CombatSimulator simulator = simulatorWith(c2, ally);
        perform(simulator, CharacterActionKey.SKILL);
        simulator.getStellarReactionManager().triggerStellarConduct(0.0);
        StatsContainer conduct = resolvedStats(ally, simulator);
        assertClose(0.20, conduct.get(StatType.CRYO_RES_SHRED),
                "Odette C2 Conduct Cryo RES reduction");
        assertClose(0.20, conduct.get(StatType.ELECTRO_RES_SHRED),
                "Odette C2 Conduct Electro RES reduction");
        simulator.getStellarReactionManager().triggerStellarSwirl(0.0);
        simulator.advanceTime(6.0);
        StatsContainer swirl = resolvedStats(ally, simulator);
        assertClose(0.20, swirl.get(StatType.CRYO_RES_SHRED),
                "Odette C2 Swirl Cryo RES reduction");
        assertClose(0.20, swirl.get(StatType.ANEMO_RES_SHRED),
                "Odette C2 Swirl Anemo RES reduction");
    }

    private static void testC4CoordinatedGateTypesAndSnapshot() {
        Odette c4 = new Odette(null, null, 4);
        TestCharacter ally = new TestCharacter(CharacterId.GANYU, Element.CRYO);
        CombatSimulator simulator = simulatorWith(c4, ally);
        List<AttackAction> actions = captureOdetteActions(simulator);

        triggerPartyStellar(simulator, ally, AttackAction.StellarReactionType.CONDUCT);
        assertEquals(1, actions.size(),
                "Odette C4 triggers from accepted party Stellar damage");
        assertStellarAction(actions.get(0), 0.66,
                AttackAction.StellarReactionType.CONDUCT,
                "Odette C4 no-Radiance Conduct follow-up");
        SimulatorSnapshot gated = simulator.saveSnapshot();

        simulator.advanceTime(3.5 - 1e-6);
        triggerPartyStellar(simulator, ally, AttackAction.StellarReactionType.CONDUCT);
        assertEquals(1, actions.size(),
                "Odette C4 blocks before 3.5-second boundary");
        simulator.advanceTime(1e-6);
        simulator.getStellarReactionManager().triggerStellarSwirl(
                simulator.getCurrentTime());
        triggerPartyStellar(simulator, ally, AttackAction.StellarReactionType.CONDUCT);
        assertEquals(2, actions.size(),
                "Odette C4 accepts at 3.5-second boundary");
        assertStellarAction(actions.get(1), 0.99,
                AttackAction.StellarReactionType.SWIRL,
                "Odette C4 Swirl follow-up");

        simulator.restoreSnapshot(gated);
        triggerPartyStellar(simulator, ally, AttackAction.StellarReactionType.CONDUCT);
        assertEquals(2, actions.size(),
                "Odette C4 snapshot restores next trigger gate");
        simulator.advanceTime(3.5);
        simulator.getStellarReactionManager().triggerStellarSwirl(3.5);
        simulator.getStellarReactionManager().triggerStellarConduct(3.5);
        triggerPartyStellar(simulator, ally, AttackAction.StellarReactionType.SWIRL);
        assertEquals(3, actions.size(),
                "Odette C4 retriggers after restored gate");
        assertEquals(AttackAction.StellarReactionType.CONDUCT,
                actions.get(2).getStellarReactionType(),
                "Odette C4 gives Conduct priority when both Radiances exist");
    }

    private static void testC6TransferCapAndFullStateSnapshot() {
        Odette c6 = new Odette(null, null, 6);
        TestCharacter ally = new TestCharacter(CharacterId.GANYU, Element.CRYO);
        CombatSimulator simulator = simulatorWith(c6, ally);
        List<AttackAction> actions = captureOdetteActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        c6.performCoda(simulator);
        c6.onSwitchOut(simulator);
        simulator.advanceTime(10.0);
        assertEquals(6, c6.getSelfSplendorStacks(10.0),
                "Odette C6 preserves owner Splendor");
        assertEquals(6, c6.getTeamSplendorStacks(10.0),
                "Odette C6 shared Splendor caps at six");
        StatsContainer teamStats = resolvedStats(ally, simulator);
        assertClose(1.19,
                teamStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Odette C6 team combines six Splendor stacks and C4 Burst echo");
        assertClose(0.25,
                teamStats.get(StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "Odette C6 team elevation uses special channel");
        assertClose(0.20,
                c6.getEffectiveStats(10.0).get(
                        StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS),
                "Odette C6 owner elevation uses special channel");

        c6.onSwitchIn(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(10.0);
        assertTrue(!c6.isDanceDoubleActive(20.0),
                "Odette state mutates after snapshot");
        simulator.restoreSnapshot(snapshot);
        assertClose(10.0, simulator.getCurrentTime(),
                "Odette snapshot restores simulator time");
        assertTrue(c6.isDanceDoubleActive(10.0),
                "Odette snapshot restores Dance Double");
        assertTrue(c6.isBurstBonusActive(10.0),
                "Odette snapshot restores Burst bonus");
        assertTrue(!c6.isCodaAvailable(10.0),
                "Odette snapshot restores consumed Coda window");
        assertEquals(6, c6.getSelfSplendorStacks(10.0),
                "Odette snapshot restores owner Splendor");
        assertEquals(6, c6.getTeamSplendorStacks(10.0),
                "Odette snapshot restores team Splendor");

        simulator.getStellarReactionManager().triggerStellarSwirl(10.0);
        int beforePlume = actions.size();
        c6.performPlume(simulator);
        assertTrue(actions.size() >= beforePlume + 2,
                "Odette snapshot restores Coda-empowered dance state");
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction lastNormal = lastActionOfType(actions, ActionType.NORMAL);
        assertClose(0.946357, lastNormal.getDamagePercent(),
                "Odette snapshot restores Normal string step");
    }

    private static void testInvalidInputsAndStateOwnership() {
        assertThrows(IllegalArgumentException.class,
                () -> new Odette(null, null, -1),
                "Odette rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Odette(null, null, 7),
                "Odette rejects constellation above six");
        Odette odette = new Odette(null, null);
        CombatSimulator simulator = simulatorWith(odette);
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.ODETTE,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Odette rejects Hold Skill");
        assertThrows(IllegalStateException.class,
                () -> odette.performPlume(simulator),
                "Odette rejects Plume without Dance Double");
        SnapshotAwareCharacterEffect.State state =
                odette.captureCharacterState();
        assertTrue(!new Odette(null, null).acceptsCharacterState(state),
                "Odette rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> odette.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() { },
                        simulator),
                "Odette rejects another state type");
    }

    private static double cryoDamageInsideConduct(Odette odette) {
        TestCharacter ally = new TestCharacter(CharacterId.GANYU, Element.CRYO);
        CombatSimulator simulator = simulatorWith(odette, ally);
        perform(simulator, CharacterActionKey.SKILL);
        simulator.getStellarReactionManager().triggerStellarConduct(0.0);
        double before = simulator.getTotalDamage();
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(),
                regularAction("C2 Cryo fixture", 1.0, Element.CRYO));
        return simulator.getTotalDamage() - before;
    }

    private static void triggerPartyStellar(
            CombatSimulator simulator,
            Character actor,
            AttackAction.StellarReactionType type) {
        AttackAction action = regularAction(
                "Party Stellar fixture", 1.0, Element.CRYO);
        action.setStellarReactionType(type);
        simulator.performActionWithoutTimeAdvance(actor.getCharacterId(), action);
    }

    private static AttackAction regularAction(
            String name,
            double multiplier,
            Element element) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
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

    private static List<AttackAction> captureOdetteActions(
            CombatSimulator simulator) {
        List<AttackAction> actions = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ODETTE) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static StatsContainer resolvedStats(
            Character character,
            CombatSimulator simulator) {
        StatsContainer stats = character.getEffectiveStats(
                simulator.getCurrentTime());
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            buff.apply(stats, simulator.getCurrentTime());
        }
        return stats;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.ODETTE,
                CharacterActionRequest.of(key));
    }

    private static AttackAction lastActionOfType(
            List<AttackAction> actions,
            ActionType type) {
        for (int index = actions.size() - 1; index >= 0; index--) {
            if (actions.get(index).getActionType() == type) {
                return actions.get(index);
            }
        }
        throw new AssertionError("No action of type " + type);
    }

    private static void assertStellarAction(
            AttackAction action,
            double multiplier,
            AttackAction.StellarReactionType type,
            String message) {
        assertAction(action, multiplier, ActionType.SKILL,
                StatType.SKILL_DMG_BONUS, Element.CRYO, message);
        assertEquals(type, action.getStellarReactionType(),
                message + " Stellar type");
        assertTrue(action.hasStatSnapshot(), message + " stat snapshot");
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
        assertEquals(element, action.getElement(), message + " element");
        assertClose(0.0, action.getAnimationDuration(),
                message + " animation");
        assertEquals(ICDType.None, action.getICDType(),
                message + " ICD type");
        assertEquals(ICDTag.None, action.getICDTag(),
                message + " ICD tag");
        assertClose(0.0, action.getGaugeUnits(), message + " gauge");
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

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element characterElement) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
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
