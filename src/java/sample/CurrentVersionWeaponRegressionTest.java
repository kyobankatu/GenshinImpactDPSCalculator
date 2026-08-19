package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.ATeaspoonOfTranscendence;
import model.weapon.DisasterAndRemorse;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused current-version weapon metadata, state, and boundary regressions. */
public final class CurrentVersionWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private CurrentVersionWeaponRegressionTest() {
    }

    /** Runs both current-version weapon contracts and their failure boundaries. */
    public static void main(String[] args) {
        testTeaspoonMetadataAndRefinement();
        testTeaspoonStacksAndUnsupportedDamageStat();
        testTeaspoonSnapshotAndGuards();
        testDisasterMetadataAndRefinement();
        testDisasterActivationExtensionAndCooldown();
        testDisasterSwitchSnapshotAndGuards();
        System.out.println("CurrentVersionWeaponRegressionTest passed");
    }

    private static void testTeaspoonMetadataAndRefinement() {
        ATeaspoonOfTranscendence defaultWeapon =
                new ATeaspoonOfTranscendence();
        assertEquals("A Teaspoon of Transcendence", defaultWeapon.getName(),
                "Teaspoon display name");
        assertEquals(WeaponType.CLAYMORE, defaultWeapon.getWeaponType(),
                "Teaspoon weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Teaspoon default refinement");
        assertClose(674.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Teaspoon base ATK");
        assertClose(0.441,
                defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Teaspoon CRIT DMG");

        for (int refinement = 1; refinement <= 5; refinement++) {
            ATeaspoonOfTranscendence weapon =
                    new ATeaspoonOfTranscendence(refinement);
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getAttackBonus(),
                    "Teaspoon ATK R" + refinement);
            assertClose(0.12 + 0.04 * refinement,
                    weapon.getStellarConductBonusPerStack(),
                    "Teaspoon Stellar-Conduct R" + refinement);
            assertTrue(!weapon.isStellarConductDamageRepresented(),
                    "Teaspoon Stellar-Conduct fails closed at R" + refinement);
        }

        assertThrows(IllegalArgumentException.class,
                () -> new ATeaspoonOfTranscendence(0),
                "Teaspoon rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new ATeaspoonOfTranscendence(6),
                "Teaspoon rejects refinement six");
    }

    private static void testTeaspoonStacksAndUnsupportedDamageStat() {
        ATeaspoonOfTranscendence weapon =
                new ATeaspoonOfTranscendence(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally = ally();
        CombatSimulator simulator = simulator(owner, ally);

        StatsContainer initial = stats(owner, simulator);
        assertClose(0.28, initial.get(StatType.ATK_PERCENT),
                "Teaspoon applies unconditional R1 ATK");
        assertClose(0.0, initial.get(StatType.DMG_BONUS_ALL),
                "Teaspoon does not leak Stellar-Conduct into generic damage");

        simulator.notifyDamage(owner, hit(ActionType.NORMAL), 1.0);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 0.0);
        AttackAction nonHit = hit(ActionType.CHARGE);
        nonHit.setHitEffectTrigger(false);
        simulator.notifyDamage(owner, nonHit, 1.0);
        simulator.notifyDamage(ally, hit(ActionType.CHARGE), 1.0);
        assertEquals(0, weapon.getStackCount(0.0),
                "Teaspoon rejects wrong, zero, non-hit, and foreign triggers");

        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(1, weapon.getStackCount(0.0),
                "Teaspoon enforces the 0.2-second trigger gate");

        simulator.advanceTime(0.2 - EPSILON);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(1, weapon.getStackCount(simulator.getCurrentTime()),
                "Teaspoon rejects a trigger immediately before 0.2 seconds");
        simulator.advanceTime(EPSILON);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(2, weapon.getStackCount(simulator.getCurrentTime()),
                "Teaspoon accepts the exact 0.2-second boundary");
        simulator.advanceTime(0.2);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        simulator.advanceTime(0.2);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(3, weapon.getStackCount(simulator.getCurrentTime()),
                "Teaspoon caps and refreshes at three stacks");
        assertEquals(3, weapon.getStackCount(5.6 - EPSILON),
                "Teaspoon remains active immediately before refreshed expiry");
        assertEquals(0, weapon.getStackCount(5.6),
                "Teaspoon expires exactly at five seconds after refresh");
    }

    private static void testTeaspoonSnapshotAndGuards() {
        ATeaspoonOfTranscendence weapon =
                new ATeaspoonOfTranscendence(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        CombatSimulator simulator = simulator(owner);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        simulator.advanceTime(0.2);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(2, weapon.getStackCount(0.2),
                "Teaspoon divergent state reaches two stacks");
        weapon.restoreWeaponState(state);
        assertEquals(1, weapon.getStackCount(0.2),
                "Teaspoon restore recovers stack and timer state");

        ATeaspoonOfTranscendence other =
                new ATeaspoonOfTranscendence(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Teaspoon rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Teaspoon rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Teaspoon rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Teaspoon rejects rebinding");
    }

    private static void testDisasterMetadataAndRefinement() {
        DisasterAndRemorse defaultWeapon = new DisasterAndRemorse();
        assertEquals("Disaster and Remorse", defaultWeapon.getName(),
                "Disaster display name");
        assertEquals(WeaponType.POLEARM, defaultWeapon.getWeaponType(),
                "Disaster weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Disaster default refinement");
        assertClose(674.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Disaster base ATK");
        assertClose(0.221,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Disaster CRIT Rate");

        for (int refinement = 1; refinement <= 5; refinement++) {
            DisasterAndRemorse weapon =
                    new DisasterAndRemorse(refinement);
            assertClose(0.30 + 0.10 * refinement,
                    weapon.getDamageBonus(),
                    "Disaster damage bonus R" + refinement);
            assertTrue(!weapon.isHexereiAmplificationActive(),
                    "Disaster Hexerei fails closed at R" + refinement);
        }

        assertThrows(IllegalArgumentException.class,
                () -> new DisasterAndRemorse(0),
                "Disaster rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new DisasterAndRemorse(6),
                "Disaster rejects refinement six");
    }

    private static void testDisasterActivationExtensionAndCooldown() {
        DisasterAndRemorse weapon = new DisasterAndRemorse(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally = ally();
        CombatSimulator simulator = simulator(owner, ally);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);

        weapon.onAction(ally, skill, simulator);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.NORMAL), simulator);
        assertTrue(!weapon.isPathActive(0.0),
                "Disaster rejects foreign and non-Skill action use");
        weapon.onAction(owner, skill, simulator);
        assertTrue(weapon.isPathActive(0.0),
                "Disaster opens Path on owner Skill use");
        StatsContainer active = stats(owner, simulator);
        assertClose(0.40, active.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Disaster applies Unforgivable to Normal damage");
        assertClose(0.40, active.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Disaster applies Unforgivable to Charged damage");
        assertClose(0.40, active.get(StatType.SKILL_DMG_BONUS),
                "Disaster applies Irreparable to Skill damage");
        assertClose(0.40, active.get(StatType.BURST_DMG_BONUS),
                "Disaster applies Irreparable to Burst damage");

        simulator.notifyDamage(owner, hit(ActionType.NORMAL), 1.0);
        simulator.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertClose(4.0, weapon.getIrreparableUntil(),
                "Disaster shares the Normal/Charged extension gate");
        simulator.notifyDamage(owner, hit(ActionType.SKILL), 1.0);
        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        assertClose(4.0, weapon.getUnforgivableUntil(),
                "Disaster shares the Skill/Burst extension gate");

        simulator.advanceTime(0.1 - EPSILON);
        simulator.notifyDamage(owner, hit(ActionType.NORMAL), 1.0);
        assertClose(4.0, weapon.getIrreparableUntil(),
                "Disaster rejects an extension immediately before 0.1 seconds");
        simulator.advanceTime(EPSILON);
        simulator.notifyDamage(owner, hit(ActionType.NORMAL), 1.0);
        assertClose(5.0, weapon.getIrreparableUntil(),
                "Disaster accepts the exact 0.1-second extension boundary");

        simulator.advanceTime(16.9);
        assertTrue(!weapon.isPathActive(17.0),
                "Disaster clears Path and both windows at 17 seconds");
        weapon.onAction(owner, skill, simulator);
        assertTrue(!weapon.isPathActive(17.0),
                "Disaster activation remains cooling down at 17 seconds");
        simulator.advanceTime(1.0);
        weapon.onAction(owner, skill, simulator);
        assertTrue(weapon.isPathActive(18.0),
                "Disaster accepts activation at the exact 18-second boundary");
    }

    private static void testDisasterSwitchSnapshotAndGuards() {
        DisasterAndRemorse weapon = new DisasterAndRemorse(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally = ally();
        CombatSimulator simulator = simulator(owner, ally);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);
        weapon.onAction(owner, skill, simulator);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();

        AttackAction zeroPercent = hit(ActionType.NORMAL, 0.0);
        simulator.notifyDamage(owner, zeroPercent, 1.0);
        simulator.notifyDamage(owner, hit(ActionType.NORMAL), 0.0);
        AttackAction nonHit = hit(ActionType.NORMAL);
        nonHit.setHitEffectTrigger(false);
        simulator.notifyDamage(owner, nonHit, 1.0);
        simulator.notifyDamage(ally, hit(ActionType.NORMAL), 1.0);
        assertClose(3.0, weapon.getIrreparableUntil(),
                "Disaster rejects zero, non-hit, and foreign extensions");

        simulator.switchCharacter(CharacterId.AMBER);
        assertTrue(!weapon.isPathActive(simulator.getCurrentTime()),
                "Disaster clears all active effects on standard switch-out");
        weapon.restoreWeaponState(state);
        assertTrue(weapon.isPathActive(0.0),
                "Disaster restore recovers Path state");
        assertTrue(weapon.isUnforgivableActive(0.0),
                "Disaster restore recovers Unforgivable state");
        assertTrue(weapon.isIrreparableActive(0.0),
                "Disaster restore recovers Irreparable state");

        DisasterAndRemorse other = new DisasterAndRemorse(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Disaster rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Disaster rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Disaster rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Disaster rejects rebinding");
    }

    private static StatefulWeaponRegressionSupport.TestCharacter owner(
            model.entity.Weapon weapon) {
        return StatefulWeaponRegressionSupport.character(
                CharacterId.KEQING, weapon);
    }

    private static StatefulWeaponRegressionSupport.TestCharacter ally() {
        return StatefulWeaponRegressionSupport.character(
                CharacterId.AMBER, null);
    }

    private static CombatSimulator simulator(
            StatefulWeaponRegressionSupport.TestCharacter... characters) {
        return StatefulWeaponRegressionSupport.simulatorWith(characters);
    }

    private static StatsContainer stats(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator simulator) {
        return StatefulWeaponRegressionSupport.stats(owner, simulator);
    }

    private static AttackAction hit(ActionType type) {
        return hit(type, 1.0);
    }

    private static AttackAction hit(ActionType type, double damagePercent) {
        AttackAction action = new AttackAction(
                "Current Version Weapon Regression Hit",
                damagePercent,
                Element.PYRO,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        StatefulWeaponRegressionSupport.assertClose(expected, actual, message);
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        StatefulWeaponRegressionSupport.assertEquals(expected, actual, message);
    }

    private static void assertTrue(boolean condition, String message) {
        StatefulWeaponRegressionSupport.assertTrue(condition, message);
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        StatefulWeaponRegressionSupport.assertThrows(expected, action, message);
    }
}
