package sample;

import java.util.Collections;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.formula.DamageCalculator;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.StaffOfTheScarletSands;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Staff of the Scarlet Sands' dynamic and snapshotted conversions. */
public final class StaffOfTheScarletSandsRegressionTest {
    private static final double EPS = 1e-9;
    private static final double OWNER_BASE_ATTACK = 100.0;
    private static final double WEAPON_BASE_ATTACK = 542.0;

    private StaffOfTheScarletSandsRegressionTest() {
    }

    /** Runs metadata, ordering, stack lifecycle, rejection, and rollback checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testDynamicLateElementalMastery();
        testPostHitOrderingAndStackSnapshots();
        testCapSharedRefreshAndExactExpiry();
        testTriggerRejectionsAndZeroDamageHit();
        testSnapshotRollbackAndStrictRestore();
        testIndependentInstancesAndBindingValidation();
        System.out.println("StaffOfTheScarletSandsRegressionTest passed");
    }

    /** Verifies Lv. 90 metadata plus every refinement coefficient. */
    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new StaffOfTheScarletSands().getRefinement(),
                "Scarlet Sands default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            StaffOfTheScarletSands weapon =
                    new StaffOfTheScarletSands(refinement);
            assertEquals("Staff of the Scarlet Sands", weapon.getName(),
                    "Scarlet Sands name");
            assertEquals(WeaponType.POLEARM, weapon.getWeaponType(),
                    "Scarlet Sands weapon type");
            assertEquals(refinement, weapon.getRefinement(),
                    "Scarlet Sands selected refinement");
            assertClose(542.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Scarlet Sands base ATK");
            assertClose(0.441, weapon.getStats().get(StatType.CRIT_RATE),
                    "Scarlet Sands CRIT Rate");
            assertClose(0.39 + 0.13 * refinement,
                    weapon.getElementalMasteryConversionRatio(),
                    "Scarlet Sands unconditional ratio R" + refinement);
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getStackConversionRatio(),
                    "Scarlet Sands stack ratio R" + refinement);
            assertClose(weapon.getElementalMasteryConversionRatio(),
                    weapon.getStats().get(
                            StatType.ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO),
                    "Scarlet Sands typed conversion R" + refinement);
        }
        assertThrowsIllegalArgument(
                () -> new StaffOfTheScarletSands(0),
                "Scarlet Sands refinement zero");
        assertThrowsIllegalArgument(
                () -> new StaffOfTheScarletSands(6),
                "Scarlet Sands refinement six");
    }

    /** Verifies that unconditional conversion sees EM added after weapon assembly. */
    private static void testDynamicLateElementalMastery() {
        StaffOfTheScarletSands weapon = new StaffOfTheScarletSands(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon, 100.0);
        CombatSimulator sim = simulatorWith(owner);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Scarlet Sands initial dynamic EM conversion");

        owner.addBuff(new SimpleBuff(
                "Late EM",
                BuffId.CUSTOM,
                5.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 150.0)));
        assertClose(baseAttack() + 250.0 * 0.52,
                totalAttack(owner, sim),
                "Scarlet Sands late active-buff EM conversion");
        sim.advanceTime(5.0);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Scarlet Sands dynamic conversion after EM expiry");
    }

    /** Verifies post-hit activation and per-stack EM snapshots. */
    private static void testPostHitOrderingAndStackSnapshots() {
        StaffOfTheScarletSands weapon = new StaffOfTheScarletSands(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon, 100.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skill = hit("Scarlet Sands Skill", 1.0, ActionType.SKILL);

        double firstDamage = calculate(owner, skill, sim);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(owner, sim),
                "Trigger hit should add its first stack after resolving");
        double secondDamage = calculate(owner, skill, sim);
        assertTrue(secondDamage > firstDamage,
                "Following Skill hit should receive the prior stack");

        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 200.0);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 0.0);
        assertClose(baseAttack()
                        + 100.0 * 0.28
                        + 100.0 * 0.28
                        + 200.0 * 0.28,
                totalAttack(owner, sim),
                "Each stack should retain acquisition-time EM");
    }

    /** Verifies no local gate, cap behavior, shared refresh, and half-open expiry. */
    private static void testCapSharedRefreshAndExactExpiry() {
        StaffOfTheScarletSands weapon = new StaffOfTheScarletSands(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon, 100.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skill = hit("Shared Stack Skill", 1.0, ActionType.SKILL);

        weapon.onDamage(owner, skill, 0.0, sim);
        weapon.onDamage(owner, skill, 0.0, sim);
        weapon.onDamage(owner, skill, 0.0, sim);
        assertClose(baseAttack() + 100.0 * 0.52 + 3.0 * 100.0 * 0.28,
                totalAttack(owner, sim),
                "Three same-time single-target hits should reach cap");

        sim.advanceTime(9.999);
        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 1000.0);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertClose(baseAttack() + 1000.0 * 0.52 + 3.0 * 100.0 * 0.28,
                totalAttack(owner, sim),
                "Cap refresh should preserve all snapshotted values");

        sim.advanceTime(9.999);
        assertClose(baseAttack() + 1000.0 * 0.52 + 3.0 * 100.0 * 0.28,
                totalAttack(owner, sim),
                "All shared-duration stacks should survive before refreshed expiry");
        sim.advanceTime(0.001);
        assertClose(baseAttack() + 1000.0 * 0.52,
                totalAttack(owner, sim),
                "All shared-duration stacks should expire together at ten seconds");
    }

    /** Verifies field, source, simulator, category, and hit-effect boundaries. */
    private static void testTriggerRejectionsAndZeroDamageHit() {
        StaffOfTheScarletSands weapon = new StaffOfTheScarletSands(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon, 100.0);
        TestCharacter ally = character(
                CharacterId.AMBER, new StaffOfTheScarletSands(1), 100.0);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction skill = hit("Eligible Skill", 1.0, ActionType.SKILL);

        weapon.onDamage(ally, skill, 0.0, sim);
        weapon.onDamage(owner, hit("Normal", 1.0, ActionType.NORMAL), 0.0, sim);
        weapon.onDamage(owner, hit("Burst", 1.0, ActionType.BURST), 0.0, sim);
        weapon.onDamage(owner, null, 0.0, sim);
        weapon.onDamage(owner, skill, 0.0, new CombatSimulator());
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Wrong source, type, simulator, and null action should be rejected");

        AttackAction dummySkill = new AttackAction(
                "Animation-Only Skill Cast",
                0.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.SKILL);
        weapon.onDamage(owner, dummySkill, 0.0, sim);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Animation-only Skill cast should be rejected");

        sim.setActiveCharacter(ally.getCharacterId());
        weapon.onDamage(owner, skill, 0.0, sim);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Off-field Skill hit should be rejected");
        sim.setActiveCharacter(owner.getCharacterId());

        AttackAction zeroDamageHit = hit(
                "True Zero-Damage Skill Hit", 0.0, ActionType.SKILL);
        weapon.onDamage(owner, zeroDamageHit, 0.0, sim);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(owner, sim),
                "Explicit zero-damage Skill hit should gain a stack");

        sim.setActiveCharacter(ally.getCharacterId());
        sim.advanceTime(9.0);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(owner, sim),
                "Acquired stack should persist while owner is off-field");
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        sim.advanceTime(1.0);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Off-field Skill hit should not refresh the shared duration");
        sim.setActiveCharacter(owner.getCharacterId());

        AttackAction classifiedSkill = hit(
                "Skill-Classified Follow-Up", 1.0, ActionType.OTHER);
        classifiedSkill.setCountsAsSkillDmg(true);
        weapon.onDamage(
                owner, classifiedSkill, sim.getCurrentTime(), sim);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(owner, sim),
                "Skill-classified hit should gain a stack");
    }

    /** Verifies simulator rollback and strict immutable state ownership. */
    private static void testSnapshotRollbackAndStrictRestore() {
        StaffOfTheScarletSands weapon = new StaffOfTheScarletSands(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon, 100.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skill = hit("Rollback Skill", 1.0, ActionType.SKILL);
        weapon.onDamage(owner, skill, 0.0, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(5.0);
        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 200.0);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertClose(baseAttack() + 200.0 * 0.52 + 100.0 * 0.28 + 200.0 * 0.28,
                totalAttack(owner, sim),
                "Second stack should exist before rollback");
        sim.restoreSnapshot(snapshot);
        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 100.0);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(owner, sim),
                "Rollback should restore stack values and shared expiry");
        sim.advanceTime(10.0);
        assertClose(baseAttack() + 100.0 * 0.52,
                totalAttack(owner, sim),
                "Rollback should restore exact original expiry");

        assertThrowsIllegalArgument(
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() {
                        }),
                "Scarlet Sands unrelated snapshot type");
        StaffOfTheScarletSands other = new StaffOfTheScarletSands(1);
        assertThrowsIllegalArgument(
                () -> other.restoreWeaponState(weapon.captureWeaponState()),
                "Scarlet Sands cross-instance snapshot");
    }

    /** Verifies independent state plus null, equipment, and cross-binding rejection. */
    private static void testIndependentInstancesAndBindingValidation() {
        StaffOfTheScarletSands first = new StaffOfTheScarletSands(1);
        StaffOfTheScarletSands second = new StaffOfTheScarletSands(5);
        TestCharacter firstOwner = character(CharacterId.SUCROSE, first, 100.0);
        TestCharacter secondOwner = character(CharacterId.AMBER, second, 100.0);
        CombatSimulator firstSim = simulatorWith(firstOwner);
        CombatSimulator secondSim = simulatorWith(secondOwner);
        first.onDamage(
                firstOwner,
                hit("Independent Skill", 1.0, ActionType.SKILL),
                0.0,
                firstSim);
        assertClose(baseAttack() + 100.0 * 0.52 + 100.0 * 0.28,
                totalAttack(firstOwner, firstSim),
                "First Scarlet Sands instance state");
        assertClose(baseAttack() + 100.0 * 1.04,
                totalAttack(secondOwner, secondSim),
                "Second Scarlet Sands instance should remain unstacked");

        first.initializeForSimulator(firstOwner, firstSim);
        assertThrowsIllegalArgument(
                () -> new StaffOfTheScarletSands(1)
                        .initializeForSimulator(null, firstSim),
                "Scarlet Sands null owner binding");
        assertThrowsIllegalArgument(
                () -> new StaffOfTheScarletSands(1)
                        .initializeForSimulator(firstOwner, null),
                "Scarlet Sands null simulator binding");

        StaffOfTheScarletSands unequipped = new StaffOfTheScarletSands(1);
        assertThrowsIllegalArgument(
                () -> unequipped.initializeForSimulator(firstOwner, firstSim),
                "Scarlet Sands unequipped owner binding");
        assertThrowsIllegalState(
                () -> first.initializeForSimulator(firstOwner, new CombatSimulator()),
                "Scarlet Sands cross-simulator binding");
        assertThrowsIllegalState(
                () -> first.initializeForSimulator(secondOwner, firstSim),
                "Scarlet Sands cross-owner binding");
    }

    private static TestCharacter character(
            CharacterId id,
            Weapon weapon,
            double elementalMastery) {
        TestCharacter character = new TestCharacter(id);
        character.getBaseStats().set(StatType.BASE_ATK, OWNER_BASE_ATTACK);
        character.getBaseStats().set(
                StatType.ELEMENTAL_MASTERY, elementalMastery);
        character.setWeapon(weapon);
        return character;
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static AttackAction hit(
            String name,
            double damagePercent,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                damagePercent,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static double calculate(
            TestCharacter owner,
            AttackAction action,
            CombatSimulator sim) {
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                Collections.emptyList(),
                sim.getCurrentTime(),
                1.0,
                sim);
    }

    private static double totalAttack(
            TestCharacter character,
            CombatSimulator sim) {
        return character.getEffectiveStats(sim.getCurrentTime()).getTotalAtk();
    }

    private static double baseAttack() {
        return OWNER_BASE_ATTACK + WEAPON_BASE_ATTACK;
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPS) {
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

    private static void assertThrowsIllegalArgument(
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message + ": expected IllegalArgumentException");
    }

    private static void assertThrowsIllegalState(
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message + ": expected IllegalStateException");
    }

    /** Minimal combatant exposing mutable base EM for acquisition snapshot checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id) {
            name = "Scarlet Sands Tester " + id;
            characterId = id;
            element = Element.PHYSICAL;
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
