package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.TulaytullahsRemembrance;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for Tulaytullah's Remembrance stack state. */
public final class TulaytullahsRemembranceRegressionTest {
    private TulaytullahsRemembranceRegressionTest() {
    }

    /** Runs metadata, passive/hit stacks, switch, snapshot, and validation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPassiveAndHitStackBoundaries();
        testSwitchExpiryAndSnapshot();
        System.out.println("TulaytullahsRemembranceRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new TulaytullahsRemembrance().getRefinement(),
                "Tulaytullah default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            TulaytullahsRemembrance weapon =
                    new TulaytullahsRemembrance(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CATALYST, weapon.getWeaponType(), "Tulaytullah type");
            StatefulWeaponRegressionSupport.assertClose(
                    674.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Tulaytullah base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.441, weapon.getStats().get(StatType.CRIT_DMG),
                    "Tulaytullah CRIT DMG");
            StatefulWeaponRegressionSupport.assertClose(
                    0.075 + 0.025 * refinement,
                    weapon.getNormalSpeedBonus(),
                    "Tulaytullah speed R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.036 + 0.012 * refinement,
                    weapon.getNormalDamageBonusPerStack(),
                    "Tulaytullah stack R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new TulaytullahsRemembrance(0),
                "Tulaytullah R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new TulaytullahsRemembrance(6),
                "Tulaytullah R6");
    }

    private static void testPassiveAndHitStackBoundaries() {
        TulaytullahsRemembrance weapon = new TulaytullahsRemembrance(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        StatefulWeaponRegressionSupport.assertClose(
                0.10,
                weapon.getStats().get(StatType.NORMAL_ATTACK_SPD),
                "Tulaytullah static Normal speed");
        weapon.onAction(
                owner, CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        StatefulWeaponRegressionSupport.assertEquals(0, weapon.getStackCount(0.0),
                "Tulaytullah starts at zero stacks");
        weapon.onDamage(
                owner,
                StatefulWeaponRegressionSupport.hit("Normal", ActionType.NORMAL),
                0.0,
                sim);
        StatefulWeaponRegressionSupport.assertEquals(2, weapon.getStackCount(0.0),
                "Tulaytullah Normal hit grants two stacks");
        sim.advanceTime(0.299);
        weapon.onDamage(
                owner,
                StatefulWeaponRegressionSupport.hit("Early Normal", ActionType.NORMAL),
                sim.getCurrentTime(),
                sim);
        StatefulWeaponRegressionSupport.assertEquals(2, weapon.getStackCount(0.299),
                "Tulaytullah pre-0.3 hit rejected");
        sim.advanceTime(0.701);
        StatefulWeaponRegressionSupport.assertEquals(3, weapon.getStackCount(1.0),
                "Tulaytullah one-second passive stack");
        weapon.onDamage(
                owner,
                StatefulWeaponRegressionSupport.hit("Ready Normal", ActionType.NORMAL),
                1.0,
                sim);
        StatefulWeaponRegressionSupport.assertEquals(5, weapon.getStackCount(1.0),
                "Tulaytullah exact ready hit adds two");
    }

    private static void testSwitchExpiryAndSnapshot() {
        TulaytullahsRemembrance weapon = new TulaytullahsRemembrance(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        weapon.onAction(owner, CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        sim.advanceTime(2.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.switchCharacter(CharacterId.AMBER);
        StatefulWeaponRegressionSupport.assertEquals(0,
                weapon.getStackCount(sim.getCurrentTime()),
                "Tulaytullah switch cancels stacks");
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertEquals(2, weapon.getStackCount(2.0),
                "Tulaytullah rollback restores passive stacks");
        sim.advanceTime(11.999);
        StatefulWeaponRegressionSupport.assertEquals(10,
                weapon.getStackCount(sim.getCurrentTime()),
                "Tulaytullah caps before expiry");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertEquals(0,
                weapon.getStackCount(sim.getCurrentTime()),
                "Tulaytullah expires at fourteen seconds");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new TulaytullahsRemembrance(1).restoreWeaponState(state),
                "Tulaytullah rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Tulaytullah rejects cross-simulator reuse");
    }
}
