package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SilvershowerHeartstrings;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused metadata, Remedy-window, isolation, and rollback checks. */
public final class SilvershowerHeartstringsRegressionTest {
    private static final double EPSILON = 1e-8;

    private SilvershowerHeartstringsRegressionTest() {
    }

    /** Runs refinement, Skill Remedy, expiry, isolation, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testSkillRemedyAndUnsupportedBranches();
        testExactExpiryRefreshAndOffFieldPersistence();
        testTriggerAndBindingGuards();
        testSnapshotRestoreAndInstanceIsolation();
        System.out.println("SilvershowerHeartstringsRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        SilvershowerHeartstrings defaultWeapon =
                new SilvershowerHeartstrings();
        StatefulWeaponRegressionSupport.assertEquals(
                "Silvershower Heartstrings",
                defaultWeapon.getName(),
                "Silvershower Heartstrings display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.BOW,
                defaultWeapon.getWeaponType(),
                "Silvershower Heartstrings weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5,
                defaultWeapon.getRefinement(),
                "Silvershower Heartstrings default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                542.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Silvershower Heartstrings base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.662,
                defaultWeapon.getStats().get(StatType.HP_PERCENT),
                "Silvershower Heartstrings HP substat");

        for (int refinement = 1; refinement <= 5; refinement++) {
            SilvershowerHeartstrings weapon =
                    new SilvershowerHeartstrings(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.09 + 0.03 * refinement,
                    weapon.getRemedyHpBonusPerStack(),
                    "Silvershower Heartstrings Remedy HP R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.03 + 0.01 * refinement,
                    weapon.getThreeStackAdditionalHpBonus(),
                    "Silvershower Heartstrings three-stack HP R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.21 + 0.07 * refinement,
                    weapon.getBurstCriticalRateBonus(),
                    "Silvershower Heartstrings Burst CRIT Rate R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    25.0,
                    weapon.getSkillRemedyDuration(),
                    "Silvershower Heartstrings Skill duration R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    25.0,
                    weapon.getBondOfLifeRemedyDuration(),
                    "Silvershower Heartstrings Bond duration R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    20.0,
                    weapon.getHealingRemedyDuration(),
                    "Silvershower Heartstrings healing duration R" + refinement);
        }

        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SilvershowerHeartstrings(0),
                "Silvershower Heartstrings rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new SilvershowerHeartstrings(6),
                "Silvershower Heartstrings rejects R6");
    }

    private static void testSkillRemedyAndUnsupportedBranches() {
        SilvershowerHeartstrings weapon =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);

        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                passiveStats(weapon, 0.0).get(StatType.HP_PERCENT),
                "Silvershower Heartstrings has no pre-window passive HP");
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isSkillRemedyActive(0.0),
                "Silvershower Heartstrings rejects Burst as a Remedy trigger");
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatsContainer active = passiveStats(weapon, 0.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.12,
                active.get(StatType.HP_PERCENT),
                "Silvershower Heartstrings applies one R1 Skill stack");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                active.get(StatType.CRIT_RATE),
                "Silvershower Heartstrings does not leak Burst CRIT Rate");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isBondOfLifeRemedyActive(),
                "Silvershower Heartstrings leaves Bond Remedy inactive");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isHealingRemedyActive(),
                "Silvershower Heartstrings leaves healing Remedy inactive");

        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertClose(
                0.12,
                passiveStats(weapon, 0.0).get(StatType.HP_PERCENT),
                "Silvershower Heartstrings refreshes instead of stacking Skill Remedy");
    }

    private static void testExactExpiryRefreshAndOffFieldPersistence() {
        SilvershowerHeartstrings weapon =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.FISCHL, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isSkillRemedyActive(25.0 - EPSILON),
                "Silvershower Heartstrings remains active before 25 seconds");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isSkillRemedyActive(25.0),
                "Silvershower Heartstrings expires exactly at 25 seconds");

        simulator.advanceTime(10.0);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertClose(
                35.0,
                weapon.getSkillStackUntil(),
                "Silvershower Heartstrings refreshes the exact expiry");
        simulator.switchCharacter(CharacterId.FISCHL);
        StatefulWeaponRegressionSupport.assertClose(
                0.782,
                owner.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.HP_PERCENT),
                "Silvershower Heartstrings owner HP persists off field");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                ally.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.HP_PERCENT),
                "Silvershower Heartstrings excludes allies");
        simulator.advanceTime(25.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                passiveStats(weapon, simulator.getCurrentTime())
                        .get(StatType.HP_PERCENT),
                "Silvershower Heartstrings closes at the refreshed boundary");
    }

    private static void testTriggerAndBindingGuards() {
        SilvershowerHeartstrings weapon =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.FISCHL, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        CombatSimulator foreignSimulator = new CombatSimulator();

        weapon.onAction(owner, null, simulator);
        trigger(weapon, ally, simulator, CharacterActionKey.SKILL);
        trigger(weapon, owner, foreignSimulator, CharacterActionKey.SKILL);
        trigger(weapon, owner, simulator, CharacterActionKey.NORMAL);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isSkillRemedyActive(0.0),
                "Silvershower Heartstrings rejects null, foreign, and wrong input");
        simulator.switchCharacter(CharacterId.FISCHL);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isSkillRemedyActive(0.0),
                "Silvershower Heartstrings rejects off-field owner Skill use");

        SilvershowerHeartstrings unequipped =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(bare, bareSimulator),
                "Silvershower Heartstrings rejects unequipped binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, bareSimulator),
                "Silvershower Heartstrings rejects null owner binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(bare, null),
                "Silvershower Heartstrings rejects null simulator binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, foreignSimulator),
                "Silvershower Heartstrings rejects cross-simulator reuse");
    }

    private static void testSnapshotRestoreAndInstanceIsolation() {
        SilvershowerHeartstrings weapon =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(10.0);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        StatefulWeaponRegressionSupport.assertClose(
                35.0,
                weapon.getSkillStackUntil(),
                "Silvershower Heartstrings mutates after snapshot");
        simulator.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                25.0,
                weapon.getSkillStackUntil(),
                "Silvershower Heartstrings restores snapshot expiry");

        SilvershowerHeartstrings independent =
                new SilvershowerHeartstrings(1);
        StatefulWeaponRegressionSupport.assertTrue(
                !independent.isSkillRemedyActive(0.0),
                "Silvershower Heartstrings instances are independent");
        SnapshotAwareWeaponEffect.State foreign =
                independent.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Silvershower Heartstrings rejects foreign state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Silvershower Heartstrings rejects null state");
    }

    private static StatsContainer passiveStats(
            SilvershowerHeartstrings weapon,
            double currentTime) {
        StatsContainer stats = new StatsContainer();
        weapon.applyPassive(stats, currentTime);
        return stats;
    }

    private static void trigger(
            SilvershowerHeartstrings weapon,
            StatefulWeaponRegressionSupport.TestCharacter user,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(
                user, CharacterActionRequest.of(key), simulator);
    }
}
