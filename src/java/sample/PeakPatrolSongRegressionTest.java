package sample;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.PeakPatrolSong;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Peak Patrol Song stack, snapshot, and isolation rules. */
public final class PeakPatrolSongRegressionTest {
    private static final StatType[] ELEMENTAL_BONUS_STATS = {
        StatType.PYRO_DMG_BONUS,
        StatType.HYDRO_DMG_BONUS,
        StatType.ANEMO_DMG_BONUS,
        StatType.ELECTRO_DMG_BONUS,
        StatType.DENDRO_DMG_BONUS,
        StatType.CRYO_DMG_BONUS,
        StatType.GEO_DMG_BONUS
    };

    private PeakPatrolSongRegressionTest() {
    }

    /** Runs refinement, stack, boundary, abnormal, snapshot, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testSelfStacksAndTeamDefenseSnapshot();
        testCooldownRefreshAndHalfOpenBoundaries();
        testCapAndSnapshotRefresh();
        testAbnormalEventsDoNotTrigger();
        testSnapshotRestoreAndBindingIsolation();
        System.out.println("PeakPatrolSongRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new PeakPatrolSong().getRefinement(),
                "Peak default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            PeakPatrolSong weapon = new PeakPatrolSong(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.SWORD, weapon.getWeaponType(),
                    "Peak weapon type R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    542.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Peak base ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.827, weapon.getStats().get(StatType.DEF_PERCENT),
                    "Peak DEF substat R" + refinement);

            StatefulWeaponRegressionSupport.TestCharacter owner =
                    StatefulWeaponRegressionSupport.character(
                            CharacterId.RAZOR, weapon);
            StatefulWeaponRegressionSupport.TestCharacter ally =
                    StatefulWeaponRegressionSupport.character(
                            CharacterId.AMBER, null);
            owner.getBaseStats().set(StatType.BASE_DEF, 1000.0);
            CombatSimulator sim =
                    StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
            weapon.onDamage(owner, hit("First", ActionType.NORMAL), 100.0, 0.0);
            weapon.onDamage(owner, hit("Second", ActionType.PLUNGE), 100.0, 0.1);

            double selfDefPerStack = 0.06 + 0.02 * refinement;
            double selfElementPerStack = 0.075 + 0.025 * refinement;
            StatsContainer ownerStats = owner.getEffectiveStats(0.1);
            StatefulWeaponRegressionSupport.assertClose(
                    0.827 + 2.0 * selfDefPerStack,
                    ownerStats.get(StatType.DEF_PERCENT),
                    "Peak self DEF R" + refinement);
            assertSevenElementalBonuses(
                    ownerStats,
                    2.0 * selfElementPerStack,
                    "Peak self elements R" + refinement);

            double finalDefense = 1000.0
                    * (1.0 + 0.827 + 2.0 * selfDefPerStack);
            double expectedTeamBonus = Math.min(
                    0.192 + 0.064 * refinement,
                    (0.06 + 0.02 * refinement) * finalDefense / 1000.0);
            StatefulWeaponRegressionSupport.assertClose(
                    expectedTeamBonus,
                    weapon.getSnapshottedTeamBonus(),
                    "Peak captured team value R" + refinement);
            assertSevenElementalBonuses(
                    resolvedStats(ally, sim, 0.1),
                    expectedTeamBonus,
                    "Peak team elements R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new PeakPatrolSong(0),
                "Peak rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new PeakPatrolSong(6),
                "Peak rejects R6");
    }

    private static void testSelfStacksAndTeamDefenseSnapshot() {
        PeakPatrolSong weapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        owner.getBaseStats().set(StatType.BASE_DEF, 1000.0);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(), hit("Integrated Normal", ActionType.NORMAL));
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Peak integrated Normal gains one stack");
        assertSevenElementalBonuses(
                owner.getEffectiveStats(0.0), 0.10,
                "Peak one-stack self elements");
        assertSevenElementalBonuses(
                resolvedStats(ally, sim, 0.0), 0.0,
                "Peak one stack does not grant team bonus");

        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(), hit("Integrated Plunge", ActionType.PLUNGE));
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.1),
                "Peak integrated Plunge reaches two stacks");
        StatefulWeaponRegressionSupport.assertClose(
                0.15896,
                weapon.getSnapshottedTeamBonus(),
                "Peak snapshots final DEF after second self stack");
        assertSevenElementalBonuses(
                resolvedStats(ally, sim, 0.1), 0.15896,
                "Peak grants snapshotted team elements");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                owner.getEffectiveStats(0.1).get(StatType.PHYSICAL_DMG_BONUS),
                "Peak self passive excludes Physical");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                owner.getEffectiveStats(0.1).get(StatType.DMG_BONUS_ALL),
                "Peak self passive excludes all-DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 0.1).get(StatType.PHYSICAL_DMG_BONUS),
                "Peak team passive excludes Physical");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 0.1).get(StatType.DMG_BONUS_ALL),
                "Peak team passive excludes all-DMG");
    }

    private static void testCooldownRefreshAndHalfOpenBoundaries() {
        PeakPatrolSong weapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        owner.getBaseStats().set(StatType.BASE_DEF, 1000.0);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        AttackAction normal = hit("Normal", ActionType.NORMAL);

        weapon.onDamage(owner, normal, 100.0, 0.0);
        weapon.onDamage(owner, normal, 100.0, 0.099);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.099),
                "Peak rejects trigger before 0.1 seconds");
        weapon.onDamage(owner, normal, 100.0, 0.1);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.1),
                "Peak allows trigger at 0.1-second boundary");
        weapon.onDamage(owner, normal, 100.0, 0.2);

        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(6.199),
                "Peak two-stack refresh remains active before six seconds");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(6.2),
                "Peak shared stack window expires at six seconds");
        assertSevenElementalBonuses(
                resolvedStats(ally, sim, 15.199), 0.15896,
                "Peak refreshed team window remains active before 15 seconds");
        assertSevenElementalBonuses(
                resolvedStats(ally, sim, 15.2), 0.0,
                "Peak team window expires at 15-second boundary");
    }

    private static void testCapAndSnapshotRefresh() {
        PeakPatrolSong cappedWeapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter cappedOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, cappedWeapon);
        StatefulWeaponRegressionSupport.TestCharacter cappedAlly =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        cappedOwner.getBaseStats().set(StatType.BASE_DEF, 4000.0);
        CombatSimulator cappedSim = StatefulWeaponRegressionSupport.simulatorWith(
                cappedOwner, cappedAlly);
        cappedWeapon.onDamage(
                cappedOwner, hit("First", ActionType.NORMAL), 100.0, 0.0);
        cappedWeapon.onDamage(
                cappedOwner, hit("Second", ActionType.NORMAL), 100.0, 0.1);
        StatefulWeaponRegressionSupport.assertClose(
                0.256,
                cappedWeapon.getSnapshottedTeamBonus(),
                "Peak R1 team bonus respects cap");

        PeakPatrolSong refreshWeapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter refreshOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, refreshWeapon);
        StatefulWeaponRegressionSupport.TestCharacter refreshAlly =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        refreshOwner.getBaseStats().set(StatType.BASE_DEF, 1000.0);
        CombatSimulator refreshSim = StatefulWeaponRegressionSupport.simulatorWith(
                refreshOwner, refreshAlly);
        refreshWeapon.onDamage(
                refreshOwner, hit("First", ActionType.NORMAL), 100.0, 0.0);
        refreshWeapon.onDamage(
                refreshOwner, hit("Second", ActionType.NORMAL), 100.0, 0.1);
        double firstSnapshot = refreshWeapon.getSnapshottedTeamBonus();
        refreshOwner.getBaseStats().set(StatType.BASE_DEF, 1500.0);
        assertSevenElementalBonuses(
                resolvedStats(refreshAlly, refreshSim, 0.15), firstSnapshot,
                "Peak team value remains snapshotted after DEF changes");
        refreshWeapon.onDamage(
                refreshOwner, hit("Refresh", ActionType.NORMAL), 100.0, 0.2);
        StatefulWeaponRegressionSupport.assertClose(
                0.23844,
                refreshWeapon.getSnapshottedTeamBonus(),
                "Peak two-stack refresh captures updated final DEF");
    }

    private static void testAbnormalEventsDoNotTrigger() {
        PeakPatrolSong weapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(CharacterId.SUCROSE, null);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        weapon.onDamage(ally, hit("Ally", ActionType.NORMAL), 100.0, 0.0);
        weapon.onDamage(outsider, hit("Outsider", ActionType.NORMAL), 100.0, 0.0);
        weapon.onDamage(owner, null, 100.0, 0.0);
        weapon.onDamage(owner, hit("Zero damage", ActionType.NORMAL), 0.0, 0.0);
        weapon.onDamage(owner, hit("Skill", ActionType.SKILL), 100.0, 0.0);
        AttackAction falseHit = hit("False hit", ActionType.PLUNGE);
        falseHit.setHitEffectTrigger(false);
        weapon.onDamage(owner, falseHit, 100.0, 0.0);
        weapon.onDamage(owner, zeroMotionHit(), 100.0, 0.0);

        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(0.0),
                "Peak abnormal events do not gain stacks");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, weapon.getSnapshottedTeamBonus(),
                "Peak abnormal events do not capture team bonus");
    }

    private static void testSnapshotRestoreAndBindingIsolation() {
        PeakPatrolSong weapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(CharacterId.AMBER, null);
        owner.getBaseStats().set(StatType.BASE_DEF, 1000.0);
        CombatSimulator sim = StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        weapon.onDamage(owner, hit("First", ActionType.NORMAL), 100.0, 0.0);
        SimulatorSnapshot oneStack = sim.saveSnapshot();
        sim.advanceTime(0.1);
        weapon.onDamage(owner, hit("Second", ActionType.NORMAL), 100.0, 0.1);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.1),
                "Peak reaches two stacks before rollback");

        sim.restoreSnapshot(oneStack);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Peak rollback restores one self stack");
        assertSevenElementalBonuses(
                resolvedStats(ally, sim, 0.0), 0.0,
                "Peak rollback removes future team snapshot");
        weapon.onDamage(owner, hit("Too early", ActionType.NORMAL), 100.0, 0.099);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.099),
                "Peak rollback restores trigger ICD");
        weapon.onDamage(owner, hit("Boundary", ActionType.NORMAL), 100.0, 0.1);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.1),
                "Peak post-rollback trigger is ready at boundary");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new PeakPatrolSong(1).restoreWeaponState(state),
                "Peak rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Peak rejects cross-simulator reuse");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new PeakPatrolSong(1)
                        .initializeForSimulator(null, new CombatSimulator()),
                "Peak rejects null owner");
        StatefulWeaponRegressionSupport.TestCharacter wrongOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.SUCROSE, null);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new PeakPatrolSong(1)
                        .initializeForSimulator(wrongOwner, new CombatSimulator()),
                "Peak rejects owner without weapon instance equipped");
        PeakPatrolSong unboundWeapon = new PeakPatrolSong(1);
        StatefulWeaponRegressionSupport.TestCharacter unboundOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, unboundWeapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unboundWeapon.initializeForSimulator(
                        unboundOwner, new CombatSimulator()),
                "Peak rejects owner outside simulator party");
    }

    private static AttackAction hit(String name, ActionType actionType) {
        return StatefulWeaponRegressionSupport.hit(name, actionType);
    }

    private static AttackAction zeroMotionHit() {
        AttackAction action = new AttackAction(
                "Zero motion",
                0.0,
                model.type.Element.PYRO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.NORMAL);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static StatsContainer resolvedStats(
            Character character,
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(character)) {
            buff.apply(stats, currentTime);
        }
        return stats;
    }

    private static void assertSevenElementalBonuses(
            StatsContainer stats,
            double expected,
            String message) {
        for (StatType stat : ELEMENTAL_BONUS_STATS) {
            StatefulWeaponRegressionSupport.assertClose(
                    expected, stats.get(stat), message + " " + stat);
        }
    }
}
