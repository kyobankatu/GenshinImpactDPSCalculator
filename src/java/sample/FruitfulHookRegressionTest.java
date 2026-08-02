package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.FruitfulHook;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Fruitful Hook's Plunging passive. */
public final class FruitfulHookRegressionTest {
    private FruitfulHookRegressionTest() {
    }

    /** Runs metadata, ordering, timing, snapshot, and validation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPostHitWindowAndExactExpiry();
        testTriggerRejectionsAndSnapshot();
        System.out.println("FruitfulHookRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new FruitfulHook().getRefinement(), "Hook default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            FruitfulHook weapon = new FruitfulHook(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CLAYMORE, weapon.getWeaponType(), "Hook type");
            StatefulWeaponRegressionSupport.assertClose(
                    565.0, weapon.getStats().get(StatType.BASE_ATK), "Hook base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.276, weapon.getStats().get(StatType.ATK_PERCENT), "Hook ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getActionBonus(),
                    "Hook coefficient R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class, () -> new FruitfulHook(0), "Hook R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class, () -> new FruitfulHook(6), "Hook R6");
    }

    private static void testPostHitWindowAndExactExpiry() {
        FruitfulHook weapon = new FruitfulHook(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        StatsContainer initial = StatefulWeaponRegressionSupport.stats(owner, sim);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                initial.get(StatType.PLUNGING_ATTACK_CRIT_RATE),
                "Hook unconditional Plunge CRIT Rate");

        AttackAction plunge = StatefulWeaponRegressionSupport.hit(
                "Hook Plunge", ActionType.PLUNGE);
        StatefulWeaponRegressionSupport.calculate(owner, plunge, sim);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Hook following Normal sees the post-hit window");
        sim.advanceTime(9.999);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Hook window survives before expiry");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Hook expires at ten seconds");
    }

    private static void testTriggerRejectionsAndSnapshot() {
        FruitfulHook weapon = new FruitfulHook(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        AttackAction dummy = StatefulWeaponRegressionSupport.hit(
                "Dummy Plunge", ActionType.PLUNGE);
        dummy.setHitEffectTrigger(false);
        weapon.onDamage(owner, dummy, 0.0, sim);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Hook rejects dummy Plunge");

        weapon.onDamage(owner,
                StatefulWeaponRegressionSupport.hit("Live Plunge", ActionType.PLUNGE),
                0.0,
                sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(10.0);
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Hook rollback restores window");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FruitfulHook(1).restoreWeaponState(state),
                "Hook rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Hook rejects cross-simulator reuse");
    }
}
