package sample;

import mechanics.reaction.ReactionResult;
import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.FangOfTheMountainKing;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;

/** Regression checks for Fang of the Mountain King's independent stacks. */
public final class FangOfTheMountainKingRegressionTest {
    private FangOfTheMountainKingRegressionTest() {
    }

    /** Runs metadata, Skill/reaction stacks, expiry, snapshot, and validation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testSkillAndPartyReactionStacks();
        testIndependentExpiryCapAndSnapshot();
        System.out.println("FangOfTheMountainKingRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new FangOfTheMountainKing().getRefinement(),
                "Fang default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            FangOfTheMountainKing weapon = new FangOfTheMountainKing(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CLAYMORE, weapon.getWeaponType(), "Fang type");
            StatefulWeaponRegressionSupport.assertClose(
                    741.0, weapon.getStats().get(StatType.BASE_ATK), "Fang base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.110, weapon.getStats().get(StatType.CRIT_RATE), "Fang CRIT Rate");
            StatefulWeaponRegressionSupport.assertClose(
                    0.10 + 0.025 * (refinement - 1),
                    weapon.getDamageBonusPerStack(),
                    "Fang stack R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FangOfTheMountainKing(0),
                "Fang R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FangOfTheMountainKing(6),
                "Fang R6");
    }

    private static void testSkillAndPartyReactionStacks() {
        FangOfTheMountainKing weapon = new FangOfTheMountainKing(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        weapon.onDamage(
                owner,
                StatefulWeaponRegressionSupport.hit("Skill", ActionType.SKILL),
                0.0,
                sim);
        StatefulWeaponRegressionSupport.assertEquals(1, weapon.getStackCount(0.0),
                "Fang owner Skill stack");
        sim.notifyReaction(ReactionResult.state(
                "Burning", ReactionResult.Kind.BURNING, null), ally);
        StatefulWeaponRegressionSupport.assertEquals(4, weapon.getStackCount(0.0),
                "Fang party Burning grants three stacks");
        sim.notifyReaction(ReactionResult.transform(
                1.0, "Burgeon", ReactionResult.Kind.BURGEON), ally);
        StatefulWeaponRegressionSupport.assertEquals(4, weapon.getStackCount(0.0),
                "Fang shared reaction cooldown rejects immediate Burgeon");
        StatefulWeaponRegressionSupport.assertClose(
                0.4,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.SKILL_DMG_BONUS),
                "Fang applies Skill stack bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.4,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.BURST_DMG_BONUS),
                "Fang applies Burst stack bonus");
    }

    private static void testIndependentExpiryCapAndSnapshot() {
        FangOfTheMountainKing weapon = new FangOfTheMountainKing(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        weapon.onDamage(owner,
                StatefulWeaponRegressionSupport.hit("Skill", ActionType.SKILL),
                0.0,
                sim);
        sim.advanceTime(2.0);
        sim.notifyReaction(ReactionResult.state(
                "Burning", ReactionResult.Kind.BURNING, null), ally);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(4.0);
        StatefulWeaponRegressionSupport.assertEquals(3,
                weapon.getStackCount(sim.getCurrentTime()),
                "Fang first independent stack expires at six seconds");
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertEquals(4, weapon.getStackCount(2.0),
                "Fang rollback restores all expirations");
        sim.advanceTime(2.0);
        sim.notifyReaction(ReactionResult.transform(
                1.0, "Burgeon", ReactionResult.Kind.BURGEON), ally);
        StatefulWeaponRegressionSupport.assertEquals(6, weapon.getStackCount(4.0),
                "Fang caps at six stacks");

        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, null);
        weapon.onElementalReaction(
                ReactionResult.state("Burning", ReactionResult.Kind.BURNING, null),
                outsider,
                6.0,
                sim);
        StatefulWeaponRegressionSupport.assertEquals(6, weapon.getStackCount(4.0),
                "Fang rejects non-party reaction source");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FangOfTheMountainKing(1).restoreWeaponState(state),
                "Fang rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Fang rejects cross-simulator reuse");
    }
}
