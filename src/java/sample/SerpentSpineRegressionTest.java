package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SerpentSpine;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;

/** Regression checks for Serpent Spine's fixed stack cadence. */
public final class SerpentSpineRegressionTest {
    private SerpentSpineRegressionTest() {
    }

    /** Runs metadata, cadence, field, cap, snapshot, and validation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testCadenceFieldStateAndCap();
        testSnapshotRestoresCadence();
        System.out.println("SerpentSpineRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new SerpentSpine().getRefinement(), "Spine default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            SerpentSpine weapon = new SerpentSpine(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CLAYMORE, weapon.getWeaponType(), "Spine type");
            StatefulWeaponRegressionSupport.assertClose(
                    510.0, weapon.getStats().get(StatType.BASE_ATK), "Spine base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.276, weapon.getStats().get(StatType.CRIT_RATE), "Spine CRIT Rate");
            StatefulWeaponRegressionSupport.assertClose(
                    0.05 + 0.01 * refinement,
                    weapon.getDamageBonusPerStack(),
                    "Spine stack R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class, () -> new SerpentSpine(0), "Spine R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class, () -> new SerpentSpine(6), "Spine R6");
    }

    private static void testCadenceFieldStateAndCap() {
        SerpentSpine weapon = new SerpentSpine(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        sim.advanceTime(3.999);
        StatefulWeaponRegressionSupport.assertEquals(0, weapon.getStackCount(),
                "Spine no pre-boundary stack");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertEquals(1, weapon.getStackCount(),
                "Spine exact four-second stack");
        sim.setActiveCharacter(CharacterId.AMBER);
        sim.advanceTime(4.0);
        StatefulWeaponRegressionSupport.assertEquals(1, weapon.getStackCount(),
                "Spine off-field check preserves stacks");
        sim.setActiveCharacter(CharacterId.RAZOR);
        sim.advanceTime(20.0);
        StatefulWeaponRegressionSupport.assertEquals(5, weapon.getStackCount(),
                "Spine caps at five stacks");
        StatefulWeaponRegressionSupport.assertClose(
                0.30,
                StatefulWeaponRegressionSupport.stats(owner, sim)
                        .get(StatType.DMG_BONUS_ALL),
                "Spine applies five R1 stacks");
    }

    private static void testSnapshotRestoresCadence() {
        SerpentSpine weapon = new SerpentSpine(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        sim.advanceTime(5.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(7.0);
        StatefulWeaponRegressionSupport.assertEquals(3, weapon.getStackCount(),
                "Spine advances before rollback");
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertEquals(1, weapon.getStackCount(),
                "Spine rollback restores stack count");
        sim.advanceTime(2.999);
        StatefulWeaponRegressionSupport.assertEquals(1, weapon.getStackCount(),
                "Spine restored cadence waits to exact boundary");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertEquals(2, weapon.getStackCount(),
                "Spine restored cadence fires at original boundary");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SerpentSpine(1).restoreWeaponState(state),
                "Spine rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Spine rejects cross-simulator reuse");
    }
}
