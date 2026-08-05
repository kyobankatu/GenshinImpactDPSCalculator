package sample;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.AthameArtis;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused Athame Artis metadata, trigger, routing, and rollback checks. */
public final class AthameArtisRegressionTest {
    private static final double EPSILON = 1e-8;

    private AthameArtisRegressionTest() {
    }

    /** Runs Athame Artis refinement, window, routing, and guard regressions. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPositiveBurstHitAndTriggerGuards();
        testOwnerAndActiveAllyRouting();
        testExactExpiryRefreshAndOffFieldTrigger();
        testSnapshotRestoreAndIndependentInstances();
        testBindingAndStateGuards();
        System.out.println("AthameArtisRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        AthameArtis defaultWeapon = new AthameArtis();
        StatefulWeaponRegressionSupport.assertEquals(
                "Athame Artis",
                defaultWeapon.getName(),
                "Athame Artis display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.SWORD,
                defaultWeapon.getWeaponType(),
                "Athame Artis weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5,
                defaultWeapon.getRefinement(),
                "Athame Artis default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                608.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Athame Artis base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.331,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Athame Artis CRIT Rate");

        for (int refinement = 1; refinement <= 5; refinement++) {
            AthameArtis weapon = new AthameArtis(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getBurstCriticalDamageBonus(),
                    "Athame Artis Burst CRIT DMG R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15 + 0.05 * refinement,
                    weapon.getOwnerAttackBonus(),
                    "Athame Artis owner ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.12 + 0.04 * refinement,
                    weapon.getActiveAllyAttackBonus(),
                    "Athame Artis active ally ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertTrue(
                    !weapon.isHexereiAmplificationActive(),
                    "Athame Artis leaves Hexerei inactive at R" + refinement);
        }

        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new AthameArtis(0),
                "Athame Artis rejects refinement zero");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new AthameArtis(6),
                "Athame Artis rejects refinement six");
    }

    private static void testPositiveBurstHitAndTriggerGuards() {
        AthameArtis weapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        weapon.onDamage(owner, hit(ActionType.NORMAL), 1.0, 0.0);
        weapon.onDamage(owner, hit(ActionType.SKILL), 1.0, 0.0);
        weapon.onDamage(owner, hit(ActionType.BURST), 0.0, 0.0);
        AttackAction nonHit = hit(ActionType.BURST);
        nonHit.setHitEffectTrigger(false);
        weapon.onDamage(owner, nonHit, 1.0, 0.0);
        weapon.onDamage(ally, hit(ActionType.BURST), 1.0, 0.0);
        weapon.onDamage(owner, null, 1.0, 0.0);
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(0.0),
                "Athame Artis rejects wrong, zero, non-hit, foreign, and null input");

        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(0.0),
                "Athame Artis accepts positive owner Burst hit");

        AthameArtis classifiedWeapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter classifiedOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XINGQIU, classifiedWeapon);
        CombatSimulator classifiedSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(classifiedOwner);
        AttackAction classifiedBurst = hit(ActionType.OTHER);
        classifiedBurst.setCountsAsBurstDmg(true);
        classifiedSimulator.notifyDamage(
                classifiedOwner, classifiedBurst, 1.0);
        StatefulWeaponRegressionSupport.assertTrue(
                classifiedWeapon.isWindowActive(0.0),
                "Athame Artis accepts typed Burst-damage classification");
        StatefulWeaponRegressionSupport.assertTrue(
                classifiedSimulator.getPartyMembers().contains(classifiedOwner),
                "Athame Artis classified fixture stays simulator-bound");
    }

    private static void testOwnerAndActiveAllyRouting() {
        AthameArtis weapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter firstAlly =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.TestCharacter secondAlly =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.XIANGLING, null);
        CombatSimulator simulator = StatefulWeaponRegressionSupport.simulatorWith(
                owner, firstAlly, secondAlly);
        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);

        StatefulWeaponRegressionSupport.assertClose(
                0.20,
                resolvedStats(owner, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis grants owner R1 ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(firstAlly, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis does not buff off-field ally while owner is active");

        simulator.setActiveCharacter(CharacterId.AMBER);
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                resolvedStats(firstAlly, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis buffs the active non-owner ally");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(secondAlly, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis excludes the off-field non-owner ally");
        StatefulWeaponRegressionSupport.assertClose(
                0.20,
                resolvedStats(owner, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis owner ATK persists off field");

        simulator.setActiveCharacter(CharacterId.XIANGLING);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(firstAlly, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis removes support from switched-out ally");
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                resolvedStats(secondAlly, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis follows the active ally on switch");
    }

    private static void testExactExpiryRefreshAndOffFieldTrigger() {
        AthameArtis weapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        simulator.setActiveCharacter(CharacterId.AMBER);

        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(0.0),
                "Athame Artis permits off-field owner Burst damage");
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isWindowActive(3.0 - EPSILON),
                "Athame Artis remains active immediately before expiry");
        StatefulWeaponRegressionSupport.assertTrue(
                !weapon.isWindowActive(3.0),
                "Athame Artis expires exactly at three seconds");

        simulator.advanceTime(2.0);
        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        StatefulWeaponRegressionSupport.assertClose(
                5.0,
                weapon.getActiveUntil(),
                "Athame Artis refreshes its exact expiration");
        StatefulWeaponRegressionSupport.assertClose(
                0.16,
                resolvedStats(ally, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis refreshed support remains active");
        simulator.advanceTime(3.0);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, simulator).get(StatType.ATK_PERCENT),
                "Athame Artis support closes at the half-open boundary");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        AthameArtis weapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();

        simulator.advanceTime(1.0);
        simulator.notifyDamage(owner, hit(ActionType.BURST), 1.0);
        StatefulWeaponRegressionSupport.assertClose(
                4.0,
                weapon.getActiveUntil(),
                "Athame Artis live branch refreshes after snapshot");
        simulator.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertClose(
                3.0,
                weapon.getActiveUntil(),
                "Athame Artis rollback restores exact expiration");

        AthameArtis independent = new AthameArtis(1);
        StatefulWeaponRegressionSupport.assertTrue(
                !independent.isWindowActive(0.0),
                "Athame Artis instances isolate windows");
        SnapshotAwareWeaponEffect.State foreign =
                independent.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Athame Artis rejects foreign instance state");
    }

    private static void testBindingAndStateGuards() {
        AthameArtis unequipped = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Athame Artis rejects unequipped binding");

        AthameArtis weapon = new AthameArtis(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.initializeForSimulator(owner, simulator);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Athame Artis rejects cross-simulator reuse");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "Athame Artis rejects null owner binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(owner, null),
                "Athame Artis rejects null simulator binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Athame Artis rejects null state");

        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, new AthameArtis(1));
        AthameArtis outsiderWeapon =
                (AthameArtis) outsider.getWeapon();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> outsiderWeapon.initializeForSimulator(
                        outsider, simulator),
                "Athame Artis rejects owner outside target party");
    }

    private static AttackAction hit(ActionType actionType) {
        AttackAction action = new AttackAction(
                "Athame test hit",
                1.0,
                Element.PYRO,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static StatsContainer resolvedStats(
            StatefulWeaponRegressionSupport.TestCharacter character,
            CombatSimulator simulator) {
        StatsContainer stats = character.getEffectiveStats(
                simulator.getCurrentTime());
        List<Buff> buffs = simulator.getApplicableBuffs(character);
        for (Buff buff : buffs) {
            if (!buff.isExpired(simulator.getCurrentTime())) {
                buff.apply(stats, simulator.getCurrentTime());
            }
        }
        return stats;
    }
}
