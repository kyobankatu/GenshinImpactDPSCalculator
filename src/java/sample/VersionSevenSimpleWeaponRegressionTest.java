package sample;

import model.entity.Character;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.ClashOfKings;
import model.weapon.CovenantOfFrostAndSnow;
import model.weapon.JadeVista;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Focused Version 7.0 simple-weapon metadata and passive regressions.
 *
 * <p>The vectors pin Genshin Optimizer commit {@code d791814a}, including all
 * refinement ranks, half-open duration and cooldown boundaries, owner and hit
 * guards, snapshot restoration, and Jade Vista's same-element priority.</p>
 */
public final class VersionSevenSimpleWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private VersionSevenSimpleWeaponRegressionTest() {
    }

    /** Runs all Version 7.0 simple-weapon regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testInvalidRefinements();
        testCovenantDurationRefreshAndGuards();
        testCovenantSnapshotAndBinding();
        testClashDurationExtensionAndCooldown();
        testClashRejectedHitsSnapshotAndBinding();
        testJadeVistaCompositionPriorityAndCap();
        testJadeVistaMissingMetadataAndBinding();
        System.out.println("VersionSevenSimpleWeaponRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new CovenantOfFrostAndSnow().getRefinement(),
                "Covenant default refinement");
        assertEquals(5, new ClashOfKings().getRefinement(),
                "Clash default refinement");
        assertEquals(5, new JadeVista().getRefinement(),
                "Jade default refinement");

        for (int refinement = 1; refinement <= 5; refinement++) {
            CovenantOfFrostAndSnow covenant =
                    new CovenantOfFrostAndSnow(refinement);
            ClashOfKings clash = new ClashOfKings(refinement);
            JadeVista jade = new JadeVista(refinement);

            assertMetadata(covenant, "Covenant of Frost and Snow",
                    WeaponType.BOW, 510.0, StatType.DEF_PERCENT, 0.517,
                    refinement);
            assertClose(90.0 + 30.0 * refinement,
                    covenant.getElementalMasteryBonus(),
                    "Covenant EM R" + refinement);

            assertMetadata(clash, "Clash of Kings", WeaponType.CATALYST,
                    510.0, StatType.CRIT_RATE, 0.276, refinement);
            assertClose(0.15 + 0.05 * refinement,
                    clash.getAttackBonus(),
                    "Clash ATK R" + refinement);
            assertClose(75.0 + 25.0 * refinement,
                    clash.getElementalMasteryBonus(),
                    "Clash EM R" + refinement);

            assertMetadata(jade, "Jade Vista", WeaponType.BOW,
                    510.0, StatType.CRIT_RATE, 0.276, refinement);
            assertClose(48.0 + 16.0 * refinement,
                    jade.getElementalMasteryPerStack(),
                    "Jade same-element EM R" + refinement);
            assertClose(0.09 + 0.03 * refinement,
                    jade.getAttackBonusPerStack(),
                    "Jade different-element ATK R" + refinement);
        }
    }

    private static void testInvalidRefinements() {
        assertThrows(IllegalArgumentException.class,
                () -> new CovenantOfFrostAndSnow(0),
                "Covenant rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new CovenantOfFrostAndSnow(6),
                "Covenant rejects refinement six");
        assertThrows(IllegalArgumentException.class,
                () -> new ClashOfKings(0),
                "Clash rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new ClashOfKings(6),
                "Clash rejects refinement six");
        assertThrows(IllegalArgumentException.class,
                () -> new JadeVista(0),
                "Jade rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new JadeVista(6),
                "Jade rejects refinement six");
    }

    private static void testCovenantDurationRefreshAndGuards() {
        CovenantOfFrostAndSnow weapon = new CovenantOfFrostAndSnow(1);
        TestCharacter owner = character(CharacterId.AMBER, Element.CRYO, weapon);
        TestCharacter ally = character(CharacterId.GANYU, Element.CRYO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);

        weapon.onAction(ally, skill, simulator);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.NORMAL), simulator);
        weapon.onAction(owner, null, simulator);
        assertTrue(!weapon.isWindowActive(0.0),
                "Covenant rejects foreign, non-Skill, and null actions");

        weapon.onAction(owner, skill, simulator);
        assertClose(12.0, weapon.getActiveUntil(),
                "Covenant opens a twelve-second window");
        assertClose(120.0, effectiveStats(owner).get(StatType.ELEMENTAL_MASTERY),
                "Covenant applies R1 EM");
        assertTrue(weapon.isWindowActive(12.0 - EPSILON),
                "Covenant remains active immediately before expiry");
        assertTrue(!weapon.isWindowActive(12.0),
                "Covenant expires at the exact boundary");

        simulator.advanceTime(6.0);
        weapon.onAction(owner, skill, simulator);
        assertClose(18.0, weapon.getActiveUntil(),
                "Covenant has no cooldown and refreshes from Skill time");
        assertTrue(weapon.isWindowActive(18.0 - EPSILON),
                "Refreshed Covenant remains active before expiry");
        assertTrue(!weapon.isWindowActive(18.0),
                "Refreshed Covenant expires exactly");
    }

    private static void testCovenantSnapshotAndBinding() {
        CovenantOfFrostAndSnow weapon = new CovenantOfFrostAndSnow(5);
        TestCharacter owner = character(CharacterId.AMBER, Element.CRYO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);
        weapon.onAction(owner, skill, simulator);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        simulator.advanceTime(4.0);
        weapon.onAction(owner, skill, simulator);
        assertClose(16.0, weapon.getActiveUntil(),
                "Covenant divergent state refreshes");
        weapon.restoreWeaponState(state);
        assertClose(12.0, weapon.getActiveUntil(),
                "Covenant restore recovers expiration");

        CovenantOfFrostAndSnow other = new CovenantOfFrostAndSnow(5);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Covenant rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Covenant rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Covenant rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Covenant rejects rebinding");
        CovenantOfFrostAndSnow unequipped =
                new CovenantOfFrostAndSnow(1);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, simulator),
                "Covenant rejects an unequipped owner");
    }

    private static void testClashDurationExtensionAndCooldown() {
        ClashOfKings weapon = new ClashOfKings(1);
        TestCharacter owner = character(CharacterId.LISA, Element.ELECTRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);
        weapon.onAction(owner, skill, simulator);

        assertClose(6.0, weapon.getActiveUntil(),
                "Clash opens a six-second window");
        assertClose(12.0, weapon.getNextActivationAt(),
                "Clash starts a twelve-second trigger cooldown");
        StatsContainer active = effectiveStats(owner);
        assertClose(0.20 + 0.276, active.get(StatType.ATK_PERCENT)
                        + active.get(StatType.CRIT_RATE),
                "Clash keeps ATK and CRIT channels separate");
        assertClose(0.20, active.get(StatType.ATK_PERCENT),
                "Clash applies R1 ATK");
        assertClose(100.0, active.get(StatType.ELEMENTAL_MASTERY),
                "Clash applies R1 EM");

        weapon.onDamage(owner, hit(ActionType.CHARGE),
                simulator.getCurrentTime(), simulator);
        assertClose(12.0, weapon.getActiveUntil(),
                "Clash Charged hit extends the existing window by six seconds");
        assertTrue(!weapon.isExtensionAvailable(),
                "Clash consumes its one extension");
        weapon.onDamage(owner, hit(ActionType.CHARGE),
                simulator.getCurrentTime(), simulator);
        assertClose(12.0, weapon.getActiveUntil(),
                "Clash cannot extend twice");

        simulator.advanceTime(11.0);
        weapon.onAction(owner, skill, simulator);
        assertClose(12.0, weapon.getActiveUntil(),
                "Clash rejects Skill before the cooldown boundary");
        assertTrue(weapon.isWindowActive(12.0 - EPSILON),
                "Extended Clash remains active before expiry");
        simulator.advanceTime(1.0);
        assertTrue(!weapon.isWindowActive(12.0),
                "Extended Clash expires at the exact boundary");
        weapon.onAction(owner, skill, simulator);
        assertClose(18.0, weapon.getActiveUntil(),
                "Clash activates at the exact cooldown boundary");
        assertTrue(weapon.isExtensionAvailable(),
                "A new Clash activation restores extension eligibility");
    }

    private static void testClashRejectedHitsSnapshotAndBinding() {
        ClashOfKings weapon = new ClashOfKings(5);
        TestCharacter owner = character(CharacterId.LISA, Element.ELECTRO, weapon);
        TestCharacter ally = character(CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        CharacterActionRequest skill =
                CharacterActionRequest.of(CharacterActionKey.SKILL);
        weapon.onAction(ally, skill, simulator);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.BURST), simulator);
        assertTrue(!weapon.isWindowActive(0.0),
                "Clash rejects foreign and non-Skill actions");
        weapon.onAction(owner, skill, simulator);

        AttackAction nonHit = hit(ActionType.CHARGE);
        nonHit.setHitEffectTrigger(false);
        weapon.onDamage(owner, nonHit, 0.0, simulator);
        weapon.onDamage(owner, hit(ActionType.NORMAL), 0.0, simulator);
        weapon.onDamage(ally, hit(ActionType.CHARGE), 0.0, simulator);
        weapon.onDamage(owner, hit(ActionType.CHARGE, 0.0), 0.0, simulator);
        assertClose(6.0, weapon.getActiveUntil(),
                "Clash rejects non-hit, wrong-type, foreign, and zero hits");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        weapon.onDamage(owner, hit(ActionType.CHARGE), 0.0, simulator);
        assertClose(12.0, weapon.getActiveUntil(),
                "Clash divergent state consumes extension");
        weapon.restoreWeaponState(state);
        assertClose(6.0, weapon.getActiveUntil(),
                "Clash restore recovers expiration");
        assertTrue(weapon.isExtensionAvailable(),
                "Clash restore recovers extension availability");

        simulator.advanceTime(6.0);
        weapon.onDamage(owner, hit(ActionType.CHARGE), 6.0, simulator);
        assertClose(6.0, weapon.getActiveUntil(),
                "Clash rejects extension at exact expiry");

        ClashOfKings other = new ClashOfKings(5);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Clash rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Clash rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Clash rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Clash rejects rebinding");
        ClashOfKings unequipped = new ClashOfKings(1);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, simulator),
                "Clash rejects an unequipped owner");
    }

    private static void testJadeVistaCompositionPriorityAndCap() {
        JadeVista weapon = new JadeVista(1);
        TestCharacter owner = character(CharacterId.AMBER, Element.PYRO, weapon);
        CombatSimulator simulator = simulatorWith(
                owner,
                character(CharacterId.BENNETT, Element.PYRO, null),
                character(CharacterId.XIANGLING, Element.PYRO, null),
                character(CharacterId.GANYU, Element.CRYO, null),
                character(CharacterId.KEQING, Element.ELECTRO, null),
                character(CharacterId.XINGQIU, Element.HYDRO, null));

        assertEquals(2, weapon.getSameElementStackCount(),
                "Jade counts same-element teammates first");
        assertEquals(1, weapon.getDifferentElementStackCount(),
                "Jade gives the remaining cap to different elements");
        StatsContainer stats = effectiveStats(owner);
        assertClose(128.0, stats.get(StatType.ELEMENTAL_MASTERY),
                "Jade applies two R1 same-element EM stacks");
        assertClose(0.12, stats.get(StatType.ATK_PERCENT),
                "Jade applies one R1 different-element ATK stack");
        assertClose(0.276, stats.get(StatType.CRIT_RATE),
                "Jade retains its CRIT Rate substat");

        simulator.addCharacter(
                character(CharacterId.KLEE, Element.PYRO, null));
        assertEquals(3, weapon.getSameElementStackCount(),
                "Jade caps same-element stacks at three");
        assertEquals(0, weapon.getDifferentElementStackCount(),
                "Jade priority leaves no different-element stack at cap");
    }

    private static void testJadeVistaMissingMetadataAndBinding() {
        JadeVista weapon = new JadeVista(5);
        TestCharacter owner = character(CharacterId.AMBER, null, weapon);
        TestCharacter ally = character(CharacterId.GANYU, Element.CRYO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        assertEquals(0, weapon.getSameElementStackCount(),
                "Jade fails closed when owner element is missing");
        assertEquals(0, weapon.getDifferentElementStackCount(),
                "Jade does not classify teammates without owner element");
        assertClose(0.0,
                effectiveStats(owner).get(StatType.ELEMENTAL_MASTERY),
                "Jade missing owner element grants no EM");
        assertClose(0.0, effectiveStats(owner).get(StatType.ATK_PERCENT),
                "Jade missing owner element grants no ATK");

        weapon.initializeForSimulator(owner, simulator);
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Jade rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Jade rejects rebinding");
        JadeVista unequipped = new JadeVista(1);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, simulator),
                "Jade rejects an unequipped owner");
    }

    private static void assertMetadata(
            Weapon weapon,
            String name,
            WeaponType type,
            double baseAttack,
            StatType secondaryStat,
            double secondaryValue,
            int refinement) {
        assertEquals(name, weapon.getName(), name + " name R" + refinement);
        assertEquals(type, weapon.getWeaponType(),
                name + " type R" + refinement);
        assertClose(baseAttack, weapon.getStats().get(StatType.BASE_ATK),
                name + " base ATK R" + refinement);
        assertClose(secondaryValue, weapon.getStats().get(secondaryStat),
                name + " secondary stat R" + refinement);
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        for (TestCharacter character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static StatsContainer effectiveStats(TestCharacter character) {
        return character.getEffectiveStats(0.0);
    }

    private static AttackAction hit(ActionType actionType) {
        return hit(actionType, 1.0);
    }

    private static AttackAction hit(
            ActionType actionType,
            double damagePercent) {
        AttackAction action = new AttackAction(
                "Version Seven Simple Weapon Regression Hit",
                damagePercent,
                Element.PYRO,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
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

    /** Minimal character with a test-selected element and optional weapon. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element selectedElement,
                Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = selectedElement;
            weapon = equippedWeapon;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 1.0);
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
