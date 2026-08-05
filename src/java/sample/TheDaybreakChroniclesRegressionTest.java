package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.TheDaybreakChronicles;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused regression checks for The Daybreak Chronicles' category stacks. */
public final class TheDaybreakChroniclesRegressionTest {
    private static final double EPSILON = 1e-8;

    private TheDaybreakChroniclesRegressionTest() {
    }

    /** Runs metadata, decay, trigger, rollback, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testInitialIndependentDecay();
        testMatchingHitGainAndDecayRestart();
        testTriggerRoutingAndOffFieldBehavior();
        testSnapshotAndBindingGuards();
        System.out.println("TheDaybreakChroniclesRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        TheDaybreakChronicles defaultWeapon =
                new TheDaybreakChronicles();
        StatefulWeaponRegressionSupport.assertEquals(
                "The Daybreak Chronicles", defaultWeapon.getName(),
                "Daybreak display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.BOW, defaultWeapon.getWeaponType(),
                "Daybreak weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(),
                "Daybreak default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                674.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Daybreak base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.441, defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Daybreak CRIT DMG");
        for (int refinement = 1; refinement <= 5; refinement++) {
            TheDaybreakChronicles weapon =
                    new TheDaybreakChronicles(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.075 + 0.025 * refinement,
                    weapon.getDamageBonusPerStack(),
                    "Daybreak stack value R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new TheDaybreakChronicles(0),
                "Daybreak rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new TheDaybreakChronicles(6),
                "Daybreak rejects R6");
    }

    private static void testInitialIndependentDecay() {
        TheDaybreakChronicles weapon = new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        assertCategoryBonuses(owner, simulator, 0.60, 0.60, 0.60,
                "Daybreak initial six stacks");
        simulator.advanceTime(1.0 - EPSILON);
        assertStacks(weapon, simulator, 6, 6, 6,
                "Daybreak stacks before first decay");
        simulator.advanceTime(EPSILON);
        assertStacks(weapon, simulator, 5, 5, 5,
                "Daybreak decays at exactly one second");
        simulator.advanceTime(5.0);
        assertStacks(weapon, simulator, 0, 0, 0,
                "Daybreak reaches zero after six seconds");
        assertCategoryBonuses(owner, simulator, 0.0, 0.0, 0.0,
                "Daybreak zero-stack bonuses");
    }

    private static void testMatchingHitGainAndDecayRestart() {
        TheDaybreakChronicles weapon = new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        simulator.advanceTime(6.0);
        resolve(simulator, owner, hit(ActionType.NORMAL, true, 1.0));
        assertStacks(weapon, simulator, 1, 0, 0,
                "Daybreak Normal hit adds one isolated stack");
        simulator.advanceTime(0.5);
        resolve(simulator, owner, hit(ActionType.NORMAL, true, 1.0));
        assertStacks(weapon, simulator, 2, 0, 0,
                "Daybreak second Normal hit adds one non-Hexerei stack");
        simulator.advanceTime(1.0 - EPSILON);
        assertStacks(weapon, simulator, 2, 0, 0,
                "Daybreak hit restarts decay before boundary");
        simulator.advanceTime(EPSILON);
        assertStacks(weapon, simulator, 1, 0, 0,
                "Daybreak restarted decay fires at exact boundary");
        assertCategoryBonuses(owner, simulator, 0.10, 0.0, 0.0,
                "Daybreak Normal bonus remains isolated");
    }

    private static void testTriggerRoutingAndOffFieldBehavior() {
        TheDaybreakChronicles weapon = new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        simulator.advanceTime(6.0);

        resolve(simulator, owner, hit(ActionType.SKILL, true, 1.0));
        resolve(simulator, owner, hit(ActionType.BURST, true, 1.0));
        assertStacks(weapon, simulator, 0, 1, 1,
                "Daybreak Skill and Burst stacks are independent");
        resolve(simulator, owner, hit(ActionType.CHARGE, true, 1.0));
        resolve(simulator, owner, hit(ActionType.PLUNGE, true, 1.0));
        resolve(simulator, owner, hit(ActionType.OTHER, true, 1.0));
        assertStacks(weapon, simulator, 0, 1, 1,
                "Daybreak rejects unsupported action categories");

        AttackAction noHit = hit(ActionType.NORMAL, false, 1.0);
        resolve(simulator, owner, noHit);
        resolve(simulator, owner, hit(ActionType.NORMAL, true, 0.0));
        resolve(simulator, ally, hit(ActionType.NORMAL, true, 1.0));
        assertStacks(weapon, simulator, 0, 1, 1,
                "Daybreak rejects non-hit, zero-motion, and foreign actor");

        simulator.setActiveCharacter(CharacterId.AMBER);
        weapon.onDamage(
                owner,
                hit(ActionType.NORMAL, true, 1.0),
                1.0,
                simulator.getCurrentTime());
        assertStacks(weapon, simulator, 1, 1, 1,
                "Daybreak accepts an owner hit while off field");
    }

    private static void testSnapshotAndBindingGuards() {
        TheDaybreakChronicles weapon = new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        simulator.advanceTime(5.5);
        weapon.onDamage(
                owner,
                hit(ActionType.SKILL, true, 1.0),
                1.0,
                simulator.getCurrentTime());
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(2.0);
        weapon.onDamage(
                owner,
                hit(ActionType.BURST, true, 1.0),
                1.0,
                simulator.getCurrentTime());
        simulator.restoreSnapshot(snapshot);
        assertStacks(weapon, simulator, 1, 2, 1,
                "Daybreak snapshot restores independent stack state");
        simulator.advanceTime(0.999);
        assertStacks(weapon, simulator, 0, 2, 0,
                "Daybreak restored Skill timer remains active");
        simulator.advanceTime(0.001);
        assertStacks(weapon, simulator, 0, 1, 0,
                "Daybreak restored Skill timer decays exactly");

        TheDaybreakChronicles independent =
                new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.assertEquals(
                6, independent.getNormalStacks(1_000.0),
                "Unbound Daybreak instance preserves independent initial state");
        SnapshotAwareWeaponEffect.State foreign =
                independent.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Daybreak rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Daybreak rejects null state");

        TheDaybreakChronicles unequipped =
                new TheDaybreakChronicles(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.FISCHL, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Daybreak rejects unequipped binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Daybreak rejects cross-simulator reuse");
    }

    private static StatefulWeaponRegressionSupport.TestCharacter owner(
            TheDaybreakChronicles weapon) {
        return StatefulWeaponRegressionSupport.character(
                CharacterId.FISCHL, weapon);
    }

    private static void resolve(
            CombatSimulator simulator,
            StatefulWeaponRegressionSupport.TestCharacter actor,
            AttackAction action) {
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
    }

    private static AttackAction hit(
            ActionType actionType,
            boolean hitEffect,
            double motionValue) {
        AttackAction action = new AttackAction(
                "Daybreak Regression Hit",
                motionValue,
                model.type.Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                false,
                actionType);
        action.setHitEffectTrigger(hitEffect);
        return action;
    }

    private static void assertStacks(
            TheDaybreakChronicles weapon,
            CombatSimulator simulator,
            int normal,
            int skill,
            int burst,
            String message) {
        double time = simulator.getCurrentTime();
        StatefulWeaponRegressionSupport.assertEquals(
                normal, weapon.getNormalStacks(time), message + " Normal");
        StatefulWeaponRegressionSupport.assertEquals(
                skill, weapon.getSkillStacks(time), message + " Skill");
        StatefulWeaponRegressionSupport.assertEquals(
                burst, weapon.getBurstStacks(time), message + " Burst");
    }

    private static void assertCategoryBonuses(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator simulator,
            double normal,
            double skill,
            double burst,
            String message) {
        model.stats.StatsContainer stats =
                owner.getEffectiveStats(simulator.getCurrentTime());
        StatefulWeaponRegressionSupport.assertClose(
                normal, stats.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                message + " Normal");
        StatefulWeaponRegressionSupport.assertClose(
                skill, stats.get(StatType.SKILL_DMG_BONUS),
                message + " Skill");
        StatefulWeaponRegressionSupport.assertClose(
                burst, stats.get(StatType.BURST_DMG_BONUS),
                message + " Burst");
    }
}
