package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SturdyBone;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused metadata, window, formula, boundary, and restore checks. */
public final class SturdyBoneRegressionTest {
    private SturdyBoneRegressionTest() {
    }

    /** Runs all Sturdy Bone regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testDashWindowAndHitLimit();
        testExpiryRefreshAndIrrelevantActions();
        testSnapshotAndBindingGuards();
        System.out.println("SturdyBoneRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new SturdyBone().getRefinement(),
                "Sturdy Bone default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            SturdyBone weapon = new SturdyBone(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    "Sturdy Bone", weapon.getName(), "Sturdy Bone name");
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.SWORD, weapon.getWeaponType(),
                    "Sturdy Bone type");
            StatefulWeaponRegressionSupport.assertClose(
                    565.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Sturdy Bone base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.276, weapon.getStats().get(StatType.ATK_PERCENT),
                    "Sturdy Bone secondary ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getNormalAttackRatio(),
                    "Sturdy Bone ratio R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SturdyBone(0), "Sturdy Bone refinement zero");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SturdyBone(6), "Sturdy Bone refinement six");
    }

    private static void testDashWindowAndHitLimit() {
        SturdyBone weapon = new SturdyBone(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.DASH),
                sim);
        StatefulWeaponRegressionSupport.assertEquals(
                18, weapon.getRemainingTriggers(0.0),
                "Sturdy Bone initial trigger count");
        StatsContainer active =
                StatefulWeaponRegressionSupport.stats(owner, sim);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                active.get(StatType.NORMAL_ATTACK_ATK_FLAT_DMG_RATIO),
                "Sturdy Bone active ratio");

        AttackAction normal = StatefulWeaponRegressionSupport.hit(
                "Sturdy Normal", ActionType.NORMAL);
        double firstDamage = StatefulWeaponRegressionSupport.calculate(
                owner, normal, sim);
        for (int hit = 1; hit < 18; hit++) {
            StatefulWeaponRegressionSupport.calculate(owner, normal, sim);
        }
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone eighteenth hit consumes final trigger");
        double nineteenthDamage = StatefulWeaponRegressionSupport.calculate(
                owner, normal, sim);
        StatefulWeaponRegressionSupport.assertTrue(
                firstDamage > nineteenthDamage,
                "Sturdy Bone nineteenth hit excludes additive damage");
    }

    private static void testExpiryRefreshAndIrrelevantActions() {
        SturdyBone weapon = new SturdyBone(5);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.DASH),
                sim);
        AttackAction charged = StatefulWeaponRegressionSupport.hit(
                "Sturdy Charged", ActionType.CHARGE);
        weapon.onDamage(owner, charged, 0.0, sim);
        StatefulWeaponRegressionSupport.assertEquals(
                18, weapon.getRemainingTriggers(0.0),
                "Sturdy Bone Charged hit is irrelevant");

        sim.advanceTime(6.999);
        StatefulWeaponRegressionSupport.assertEquals(
                18, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone active before expiry");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone exact expiry");
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.DASH),
                sim);
        StatefulWeaponRegressionSupport.assertEquals(
                18, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone Dash refreshes expired window");

        AttackAction dummy = StatefulWeaponRegressionSupport.hit(
                "Sturdy Dummy", ActionType.NORMAL);
        dummy.setHitEffectTrigger(false);
        weapon.onDamage(owner, dummy, sim.getCurrentTime(), sim);
        weapon.onDamage(owner, null, sim.getCurrentTime(), sim);
        weapon.onAction(owner, null, sim);
        StatefulWeaponRegressionSupport.assertEquals(
                18, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone invalid contacts do not consume triggers");
    }

    private static void testSnapshotAndBindingGuards() {
        SturdyBone weapon = new SturdyBone(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.DASH),
                sim);
        AttackAction normal = StatefulWeaponRegressionSupport.hit(
                "Sturdy Snapshot Normal", ActionType.NORMAL);
        for (int hit = 0; hit < 5; hit++) {
            StatefulWeaponRegressionSupport.calculate(owner, normal, sim);
        }
        SnapshotAwareWeaponEffect.State saved = weapon.captureWeaponState();
        for (int hit = 0; hit < 4; hit++) {
            StatefulWeaponRegressionSupport.calculate(owner, normal, sim);
        }
        weapon.restoreWeaponState(saved);
        StatefulWeaponRegressionSupport.assertEquals(
                13, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone restore recovers trigger count");

        SturdyBone foreign = new SturdyBone(1);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign.captureWeaponState()),
                "Sturdy Bone rejects foreign state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Sturdy Bone rejects wrong state type");

        weapon.initializeForSimulator(owner, sim);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Sturdy Bone rejects another simulator");
        SturdyBone unequipped = new SturdyBone(1);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(owner, sim),
                "Sturdy Bone rejects unequipped owner");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, sim),
                "Sturdy Bone rejects null owner");

        StatefulWeaponRegressionSupport.TestCharacter stranger =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.DILUC, null);
        weapon.onDamage(stranger, normal, sim.getCurrentTime(), sim);
        StatefulWeaponRegressionSupport.assertEquals(
                13, weapon.getRemainingTriggers(sim.getCurrentTime()),
                "Sturdy Bone ignores foreign owner");
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
