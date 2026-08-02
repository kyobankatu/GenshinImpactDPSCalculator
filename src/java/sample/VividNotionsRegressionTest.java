package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.VividNotions;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for Vivid Notions' independent Plunging CRIT DMG windows. */
public final class VividNotionsRegressionTest {
    private VividNotionsRegressionTest() {
    }

    /** Runs metadata, window, cancellation, rollback, and validation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testIndependentWindowsAndExactExpiry();
        testCancellationBoundaryAndGenerationSafety();
        testTriggerRejectionsAndIsolation();
        testSnapshotRestoresPendingCancellation();
        testBindingAndStateValidation();
        System.out.println("VividNotionsRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new VividNotions().getRefinement(),
                "Vivid default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            VividNotions weapon = new VividNotions(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CATALYST, weapon.getWeaponType(),
                    "Vivid weapon type");
            StatefulWeaponRegressionSupport.assertClose(
                    674.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Vivid base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.441, weapon.getStats().get(StatType.CRIT_DMG),
                    "Vivid CRIT DMG");
            StatefulWeaponRegressionSupport.assertClose(
                    0.21 + 0.07 * refinement,
                    weapon.getAttackBonus(),
                    "Vivid ATK coefficient R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.21 + 0.07 * refinement,
                    weapon.getPlungeWindowCritDamage(),
                    "Vivid Plunge coefficient R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.30 + 0.10 * refinement,
                    weapon.getSkillBurstWindowCritDamage(),
                    "Vivid Skill/Burst coefficient R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new VividNotions(0),
                "Vivid rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new VividNotions(6),
                "Vivid rejects R6");
    }

    private static void testIndependentWindowsAndExactExpiry() {
        VividNotions weapon = new VividNotions(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);

        StatefulWeaponRegressionSupport.assertClose(
                0.28,
                stats(owner, sim).get(StatType.ATK_PERCENT),
                "Vivid permanent ATK bonus");
        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        StatefulWeaponRegressionSupport.assertClose(
                0.28, plungeCritDamage(owner, sim),
                "Vivid Plunge use opens first window");
        sim.advanceTime(5.0);
        use(weapon, owner, sim, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid windows add together");

        sim.advanceTime(9.999);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid Plunge window remains active before boundary");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertClose(
                0.40, plungeCritDamage(owner, sim),
                "Vivid Plunge window expires at exactly fifteen seconds");
        sim.advanceTime(5.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid Skill window expires at its independent boundary");

        use(weapon, owner, sim, CharacterActionKey.BURST);
        StatefulWeaponRegressionSupport.assertClose(
                0.40, plungeCritDamage(owner, sim),
                "Vivid Burst opens the shared second window");
    }

    private static void testCancellationBoundaryAndGenerationSafety() {
        VividNotions weapon = new VividNotions(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        AttackAction plunge = StatefulWeaponRegressionSupport.hit(
                "Vivid Plunge", ActionType.PLUNGE);

        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.SKILL);
        weapon.onDamage(owner, plunge, 100.0, sim.getCurrentTime());
        sim.advanceTime(0.099);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid cancellation waits through 0.099 seconds");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid cancellation fires at exactly 0.1 seconds");

        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.SKILL);
        weapon.onDamage(owner, plunge, 100.0, sim.getCurrentTime());
        sim.advanceTime(0.05);
        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.BURST);
        sim.advanceTime(0.05);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid stale cancellation preserves refreshed generations");
        weapon.onDamage(owner, plunge, 100.0, sim.getCurrentTime());
        sim.advanceTime(0.1);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid current-generation cancellation clears both windows");
    }

    private static void testTriggerRejectionsAndIsolation() {
        VividNotions weapon = new VividNotions(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        StatefulWeaponRegressionSupport.TestCharacter other =
                StatefulWeaponRegressionSupport.character(CharacterId.YAE_MIKO, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, other);
        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.NORMAL);
        weapon.onAction(
                other,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                sim);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                new CombatSimulator());
        StatefulWeaponRegressionSupport.assertClose(
                0.28, plungeCritDamage(owner, sim),
                "Vivid ignores irrelevant action use");
        StatefulWeaponRegressionSupport.assertClose(
                1.441, stats(owner, sim).get(StatType.CRIT_DMG),
                "Vivid action bonus does not alter generic CRIT DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(other, sim),
                "Vivid window does not affect another character");

        AttackAction dummy = StatefulWeaponRegressionSupport.hit(
                "Vivid dummy", ActionType.PLUNGE);
        dummy.setHitEffectTrigger(false);
        weapon.onDamage(owner, dummy, 100.0, sim.getCurrentTime());
        AttackAction zero = new AttackAction(
                "Vivid zero",
                0.0,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                0.0,
                ActionType.PLUNGE);
        zero.setHitEffectTrigger(true);
        weapon.onDamage(owner, zero, 0.0, sim.getCurrentTime());
        weapon.onDamage(owner,
                StatefulWeaponRegressionSupport.hit("Vivid Normal", ActionType.NORMAL),
                100.0, sim.getCurrentTime());
        weapon.onDamage(other,
                StatefulWeaponRegressionSupport.hit("Other Plunge", ActionType.PLUNGE),
                100.0, sim.getCurrentTime());
        sim.advanceTime(0.1);
        StatefulWeaponRegressionSupport.assertClose(
                0.28, plungeCritDamage(owner, sim),
                "Vivid rejects dummy, zero-damage, and wrong-owner hits");

        owner.getBaseStats().set(StatType.FLAT_DMG_BONUS, 100.0);
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), zero);
        sim.advanceTime(0.1);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid accepts positive zero-motion additive damage");
    }

    private static void testSnapshotRestoresPendingCancellation() {
        VividNotions weapon = new VividNotions(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        AttackAction plunge = StatefulWeaponRegressionSupport.hit(
                "Vivid snapshot Plunge", ActionType.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.PLUNGE);
        use(weapon, owner, sim, CharacterActionKey.SKILL);
        weapon.onDamage(owner, plunge, 100.0, sim.getCurrentTime());
        sim.advanceTime(0.05);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(0.05);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid cancellation changes state before rollback");
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid rollback restores both windows");
        sim.advanceTime(0.049);
        StatefulWeaponRegressionSupport.assertClose(
                0.68, plungeCritDamage(owner, sim),
                "Vivid restored cancellation preserves remaining delay");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, plungeCritDamage(owner, sim),
                "Vivid restored cancellation fires at original boundary");
    }

    private static void testBindingAndStateValidation() {
        VividNotions weapon = new VividNotions(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new VividNotions(1).restoreWeaponState(state),
                "Vivid rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Vivid rejects foreign state type");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Vivid rejects cross-simulator reuse");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new VividNotions(1).initializeForSimulator(owner, sim),
                "Vivid rejects an unequipped binding");
    }

    private static void use(
            VividNotions weapon,
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator sim,
            CharacterActionKey key) {
        weapon.onAction(owner, CharacterActionRequest.of(key), sim);
    }

    private static StatsContainer stats(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime());
    }

    private static double plungeCritDamage(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator sim) {
        return stats(owner, sim).get(StatType.PLUNGING_ATTACK_CRIT_DMG);
    }

    /** Foreign marker used to verify state type validation. */
    private static final class ForeignState implements SnapshotAwareWeaponEffect.State {
    }
}
