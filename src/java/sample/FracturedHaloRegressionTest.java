package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.FracturedHalo;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Fractured Halo's represented ATK branch. */
public final class FracturedHaloRegressionTest {
    private static final double EPSILON = 1e-8;

    private FracturedHaloRegressionTest() {
    }

    /** Runs metadata, window, isolation, restore, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testSkillBurstWindowAndIsolation();
        testRefreshOffFieldPersistenceAndSnapshot();
        testBindingAndTriggerGuards();
        System.out.println("FracturedHaloRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        FracturedHalo defaultWeapon = new FracturedHalo();
        StatefulWeaponRegressionSupport.assertEquals(
                "Fractured Halo", defaultWeapon.getName(),
                "Fractured Halo display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.POLEARM, defaultWeapon.getWeaponType(),
                "Fractured Halo weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(),
                "Fractured Halo default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                608.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Fractured Halo base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.662, defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Fractured Halo CRIT DMG");
        for (int refinement = 1; refinement <= 5; refinement++) {
            FracturedHalo weapon = new FracturedHalo(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.18 + 0.06 * refinement,
                    weapon.getAttackBonus(),
                    "Fractured Halo ATK bonus R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.30 + 0.10 * refinement,
                    weapon.getLunarChargedTeamBonus(),
                    "Fractured Halo Lunar-Charged value R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FracturedHalo(0),
                "Fractured Halo rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new FracturedHalo(6),
                "Fractured Halo rejects R6");
    }

    private static void testSkillBurstWindowAndIsolation() {
        FracturedHalo weapon = new FracturedHalo(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.NORMAL);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(0.0),
                "Fractured Halo rejects Normal input");
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(0.0),
                "Fractured Halo opens on Skill input");
        StatsContainer active = owner.getEffectiveStats(0.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.24, active.get(StatType.ATK_PERCENT),
                "Fractured Halo applies R1 ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, active.get(StatType.DMG_BONUS_ALL),
                "Fractured Halo does not synthesize shield team damage");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, active.get(StatType.LUNAR_CHARGED_DMG_BONUS),
                "Fractured Halo leaves Lunar-Charged branch inactive");
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(20.0 - EPSILON),
                "Fractured Halo remains active before twenty seconds");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(20.0),
                "Fractured Halo expires at exactly twenty seconds");

        FracturedHalo burstWeapon = new FracturedHalo(5);
        StatefulWeaponRegressionSupport.TestCharacter burstOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAIDEN_SHOGUN, burstWeapon);
        CombatSimulator burstSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(burstOwner);
        trigger(burstWeapon, burstOwner, burstSimulator,
                CharacterActionKey.BURST);
        StatefulWeaponRegressionSupport.assertClose(
                0.48,
                burstOwner.getEffectiveStats(0.0)
                        .get(StatType.ATK_PERCENT),
                "Fractured Halo opens R5 window on Burst input");
    }

    private static void testRefreshOffFieldPersistenceAndSnapshot() {
        FracturedHalo weapon = new FracturedHalo(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(10.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        StatefulWeaponRegressionSupport.assertClose(
                30.0, weapon.getActiveUntil(),
                "Fractured Halo refreshes without an internal cooldown");
        simulator.switchCharacter(CharacterId.BENNETT);
        StatefulWeaponRegressionSupport.assertClose(
                0.24,
                owner.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "Fractured Halo ATK persists while owner is off field");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "Fractured Halo ATK remains owner-only");
        simulator.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                20.0, weapon.getActiveUntil(),
                "Fractured Halo snapshot restores expiration");
        StatefulWeaponRegressionSupport.assertTrue(
                !new FracturedHalo(1).isWindowActive(0.0),
                "Fractured Halo instances are independent");
    }

    private static void testBindingAndTriggerGuards() {
        FracturedHalo weapon = new FracturedHalo(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.BENNETT, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        trigger(weapon, ally, simulator, CharacterActionKey.SKILL);
        weapon.onAction(owner, null, simulator);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(0.0),
                "Fractured Halo rejects foreign and null action input");
        simulator.switchCharacter(CharacterId.BENNETT);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(0.0),
                "Fractured Halo rejects off-field owner input");

        FracturedHalo unequipped = new FracturedHalo(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Fractured Halo rejects unequipped binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Fractured Halo rejects cross-simulator reuse");
        SnapshotAwareWeaponEffect.State foreign =
                new FracturedHalo(1).captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Fractured Halo rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Fractured Halo rejects null state");
    }

    private static void trigger(
            FracturedHalo weapon,
            StatefulWeaponRegressionSupport.TestCharacter user,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(
                user, CharacterActionRequest.of(key), simulator);
    }
}
