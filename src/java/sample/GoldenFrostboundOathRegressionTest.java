package sample;

import mechanics.reaction.ReactionResult;
import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.GoldenFrostboundOath;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused checks for Golden Frostbound Oath's owner-only contract. */
public final class GoldenFrostboundOathRegressionTest {
    private static final double EPSILON = 1e-8;

    private GoldenFrostboundOathRegressionTest() {
    }

    /** Runs metadata, triggers, boundaries, rollback, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testPermanentDefenseAndSkillWindow();
        testLunarTriggerRoutesAndIsolation();
        testRefreshOffFieldAndSnapshot();
        testBindingAndInputGuards();
        System.out.println("GoldenFrostboundOathRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        GoldenFrostboundOath defaultWeapon =
                new GoldenFrostboundOath();
        StatefulWeaponRegressionSupport.assertEquals(
                "Golden Frostbound Oath", defaultWeapon.getName(),
                "Golden Frostbound display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.BOW, defaultWeapon.getWeaponType(),
                "Golden Frostbound weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(),
                "Golden Frostbound default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                542.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Golden Frostbound base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.882, defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Golden Frostbound CRIT DMG");
        for (int refinement = 1; refinement <= 5; refinement++) {
            GoldenFrostboundOath weapon =
                    new GoldenFrostboundOath(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getPermanentDefenseBonus(),
                    "Golden Frostbound DEF R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.30 + 0.10 * refinement,
                    weapon.getOwnerDamageBonus(),
                    "Golden Frostbound owner damage R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15 + 0.05 * refinement,
                    weapon.getUnavailableTeamDamageBonus(),
                    "Golden Frostbound team value R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenFrostboundOath(0),
                "Golden Frostbound rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenFrostboundOath(6),
                "Golden Frostbound rejects R6");
    }

    private static void testPermanentDefenseAndSkillWindow() {
        GoldenFrostboundOath weapon = new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                owner.getEffectiveStats(0.0).get(StatType.DEF_PERCENT),
                "Golden Frostbound permanent R1 DEF");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                owner.getEffectiveStats(0.0).get(StatType.GEO_DMG_BONUS),
                "Golden Frostbound starts without owner window");

        simulator.notifyDamage(owner, hit(ActionType.SKILL), 1.0);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(0.0),
                "Golden Frostbound opens on owner Skill hit");
        StatefulWeaponRegressionSupport.assertClose(
                0.40,
                owner.getEffectiveStats(0.0).get(StatType.GEO_DMG_BONUS),
                "Golden Frostbound owner Geo bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.40,
                owner.getEffectiveStats(0.0)
                        .get(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS),
                "Golden Frostbound owner Lunar-Crystallize bonus");
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(6.0 - EPSILON),
                "Golden Frostbound remains active before expiry");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(6.0),
                "Golden Frostbound expires exactly at six seconds");
    }

    private static void testLunarTriggerRoutesAndIsolation() {
        GoldenFrostboundOath directWeapon =
                new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter directOwner =
                owner(directWeapon);
        CombatSimulator directSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(directOwner);
        AttackAction directLunar = hit(ActionType.OTHER);
        directLunar.setLunarReactionType(
                AttackAction.LunarReactionType.CRYSTALLIZE);
        directSimulator.notifyDamage(directOwner, directLunar, 1.0);
        StatefulWeaponRegressionSupport.assertTrue(
                directWeapon.isWindowActive(0.0),
                "Golden Frostbound opens on direct Lunar-Crystallize hit");

        GoldenFrostboundOath reactionWeapon =
                new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter reactionOwner =
                owner(reactionWeapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator reactionSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(
                        reactionOwner, ally);
        ReactionResult lunar = ReactionResult.lunar(
                1.0, ReactionResult.LunarType.CRYSTALLIZE);
        reactionSimulator.notifyReaction(lunar, ally);
        StatefulWeaponRegressionSupport.assertTrue(
                !reactionWeapon.isWindowActive(0.0),
                "Golden Frostbound rejects foreign Lunar-Crystallize");
        reactionSimulator.notifyReaction(lunar, reactionOwner);
        StatefulWeaponRegressionSupport.assertTrue(
                reactionWeapon.isWindowActive(0.0),
                "Golden Frostbound opens on owner Lunar-Crystallize");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(0.0).get(StatType.GEO_DMG_BONUS),
                "Golden Frostbound does not synthesize Moondrift team Geo");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(0.0)
                        .get(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS),
                "Golden Frostbound leaves team Lunar branch inactive");
    }

    private static void testRefreshOffFieldAndSnapshot() {
        GoldenFrostboundOath weapon = new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        simulator.notifyDamage(owner, hit(ActionType.SKILL), 1.0);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(3.0);
        simulator.setActiveCharacter(CharacterId.AMBER);
        simulator.notifyDamage(owner, hit(ActionType.SKILL), 1.0);
        StatefulWeaponRegressionSupport.assertClose(
                9.0, weapon.getActiveUntil(),
                "Golden Frostbound refreshes from off-field owner damage");
        StatefulWeaponRegressionSupport.assertClose(
                0.40,
                owner.getEffectiveStats(3.0).get(StatType.GEO_DMG_BONUS),
                "Golden Frostbound owner window persists off field");
        simulator.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                6.0, weapon.getActiveUntil(),
                "Golden Frostbound snapshot restores expiration");
        StatefulWeaponRegressionSupport.assertTrue(
                !new GoldenFrostboundOath(1).isWindowActive(0.0),
                "Golden Frostbound instances are independent");
    }

    private static void testBindingAndInputGuards() {
        GoldenFrostboundOath weapon = new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter owner = owner(weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        weapon.onDamage(owner, hit(ActionType.NORMAL), 1.0, 0.0);
        weapon.onDamage(owner, hit(ActionType.SKILL), 0.0, 0.0);
        AttackAction nonHit = hit(ActionType.SKILL);
        nonHit.setHitEffectTrigger(false);
        weapon.onDamage(owner, nonHit, 1.0, 0.0);
        weapon.onDamage(ally, hit(ActionType.SKILL), 1.0, 0.0);
        weapon.onDamage(owner, null, 1.0, 0.0);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(0.0),
                "Golden Frostbound rejects wrong, zero, non-hit, foreign, null input");

        GoldenFrostboundOath unequipped =
                new GoldenFrostboundOath(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.FISCHL, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Golden Frostbound rejects unequipped binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Golden Frostbound rejects cross-simulator reuse");
        SnapshotAwareWeaponEffect.State foreign =
                new GoldenFrostboundOath(1).captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Golden Frostbound rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Golden Frostbound rejects null state");
    }

    private static StatefulWeaponRegressionSupport.TestCharacter owner(
            GoldenFrostboundOath weapon) {
        return StatefulWeaponRegressionSupport.character(
                CharacterId.FISCHL, weapon);
    }

    private static AttackAction hit(ActionType actionType) {
        AttackAction action = new AttackAction(
                "Golden Frostbound Regression Hit",
                1.0,
                Element.GEO,
                StatType.BASE_ATK,
                StatType.GEO_DMG_BONUS,
                0.0,
                false,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }
}
