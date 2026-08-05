package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.reaction.ReactionEffectScheduler;
import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.Predator;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused metadata, Cryo-stack, affinity, snapshot, and binding checks. */
public final class PredatorRegressionTest {
    private PredatorRegressionTest() {
    }

    /** Runs all fixed-R1 Predator regression cases. */
    public static void main(String[] args) {
        testMetadataAndPlatformBoundary();
        testValidTriggerAndTwoStackCap();
        testExactExpiryAndRefresh();
        testRejectedAndAcceptedDamageEvents();
        testElementalIndirectDamageEvents();
        testIndirectListenerSegregation();
        testDirectAndIndirectSharedWindow();
        testCryoSwirlIntegration();
        testSuperconductIntegration();
        testScheduledElementalDispatch();
        testAloyOnlyFlatAttack();
        testSnapshotRestore();
        testElementalIndirectSnapshotRestore();
        testIndependentInstances();
        testBindingAndStateGuards();
        System.out.println("PredatorRegressionTest passed");
    }

    private static void testMetadataAndPlatformBoundary() {
        Predator weapon = new Predator();

        StatefulWeaponRegressionSupport.assertEquals(
                "Predator", weapon.getName(),
                "Predator name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.BOW, weapon.getWeaponType(),
                "Predator weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getRefinement(),
                "Predator is fixed at refinement rank one");
        StatefulWeaponRegressionSupport.assertClose(
                510.0, weapon.getStats().get(StatType.BASE_ATK),
                "Predator fixed-R1 base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.413, weapon.getStats().get(StatType.ATK_PERCENT),
                "Predator fixed-R1 ATK substat");
        StatefulWeaponRegressionSupport.assertTrue(
                weapon.isPlatformPassiveEnabled(),
                "Predator models the PlayStation passive as enabled");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(0.0),
                "Predator starts without Cryo-hit stacks");
    }

    private static void testValidTriggerAndTwoStackCap() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        assertAttackBonuses(owner, 0.0, 0.0,
                "Predator inactive stack bonuses");
        sim.notifyDamage(
                owner,
                cryoHit("First Cryo Skill", ActionType.SKILL),
                100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "First positive active-owner Cryo hit gains one stack");
        assertAttackBonuses(owner, 0.0, 0.10,
                "Predator one-stack bonuses");

        sim.notifyDamage(
                owner,
                cryoHit("Second Cryo Burst", ActionType.BURST),
                100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.0),
                "Second positive active-owner Cryo hit gains two stacks");
        assertAttackBonuses(owner, 0.0, 0.20,
                "Predator two-stack bonuses");

        sim.notifyDamage(
                owner,
                cryoHit("Capped Cryo Plunge", ActionType.PLUNGE),
                100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.0),
                "Further Cryo hits cannot exceed two stacks");
        assertUnrelatedBonusesUnchanged(owner, 0.0);
    }

    private static void testExactExpiryAndRefresh() {
        Predator expiryWeapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter expiryOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, expiryWeapon);
        CombatSimulator expirySim =
                StatefulWeaponRegressionSupport.simulatorWith(expiryOwner);

        expirySim.notifyDamage(expiryOwner, cryoHit("Expiry"), 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, expiryWeapon.getStackCount(5.999999),
                "Predator stack remains active before six seconds");
        StatefulWeaponRegressionSupport.assertEquals(
                0, expiryWeapon.getStackCount(6.0),
                "Predator stack expires at the exact six-second boundary");
        assertAttackBonuses(expiryOwner, 6.0, 0.0,
                "Predator expired stack bonuses");

        Predator refreshWeapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter refreshOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, refreshWeapon);
        CombatSimulator refreshSim =
                StatefulWeaponRegressionSupport.simulatorWith(refreshOwner);
        refreshSim.notifyDamage(refreshOwner, cryoHit("First"), 100.0);
        refreshSim.advanceTime(1.0);
        refreshSim.notifyDamage(refreshOwner, cryoHit("Second"), 100.0);
        refreshSim.advanceTime(1.0);
        refreshSim.notifyDamage(refreshOwner, cryoHit("Capped refresh"), 100.0);

        StatefulWeaponRegressionSupport.assertEquals(
                2, refreshWeapon.getStackCount(7.999999),
                "Capped hit refreshes the shared stack window");
        StatefulWeaponRegressionSupport.assertEquals(
                0, refreshWeapon.getStackCount(8.0),
                "Refreshed shared window remains half-open");
    }

    private static void testRejectedAndAcceptedDamageEvents() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.SUCROSE, null);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        sim.setActiveCharacter(ally.getCharacterId());
        sim.notifyDamage(owner, cryoHit("Inactive owner"), 100.0);
        assertNoStacks(weapon, 0.0,
                "Inactive owner Cryo damage is rejected");

        sim.setActiveCharacter(owner.getCharacterId());
        sim.notifyDamage(owner, hit("Non-Cryo", Element.PYRO, true), 100.0);
        sim.notifyDamage(owner, cryoHit("Zero damage"), 0.0);
        sim.notifyDamage(owner, cryoHit("Negative damage"), -1.0);
        sim.notifyDamage(ally, cryoHit("Other party actor"), 100.0);
        sim.notifyDamage(outsider, cryoHit("Foreign actor"), 100.0);
        sim.notifyDamage(owner, null, 100.0);
        sim.notifyIndirectDamage(owner, 100.0);
        assertNoStacks(weapon, 0.0,
                "Rejected and indirect events do not gain stacks");

        AttackAction disabledHitEffect = hit(
                "Direct Cryo without hit-effect callback",
                Element.CRYO,
                ActionType.BURST,
                false);
        sim.notifyDamage(owner, disabledHitEffect, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Positive direct Cryo damage ignores hit-effect flag");
    }

    private static void testElementalIndirectDamageEvents() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.SUCROSE, null);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        sim.notifyElementalIndirectDamage(owner, Element.ELECTRO, 100.0);
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 0.0);
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, -1.0);
        sim.notifyElementalIndirectDamage(null, Element.CRYO, 100.0);
        sim.notifyElementalIndirectDamage(ally, Element.CRYO, 100.0);
        sim.notifyElementalIndirectDamage(outsider, Element.CRYO, 100.0);
        assertNoStacks(weapon, 0.0,
                "Rejected typed indirect damage does not gain stacks");

        sim.setActiveCharacter(ally.getCharacterId());
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        assertNoStacks(weapon, 0.0,
                "Off-field owner typed Cryo damage is rejected");

        sim.setActiveCharacter(owner.getCharacterId());
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Active owner typed Cryo damage gains one stack");
    }

    private static void testDirectAndIndirectSharedWindow() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);

        sim.notifyDamage(owner, cryoHit("Direct first stack"), 100.0);
        sim.advanceTime(1.0);
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(1.0),
                "Direct and indirect Cryo damage share the two-stack cap");

        sim.advanceTime(1.0);
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(7.999999),
                "Capped indirect Cryo damage refreshes the shared window");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(8.0),
                "Direct and indirect Cryo damage share one half-open expiry");
    }

    private static void testIndirectListenerSegregation() {
        CombatSimulator sim = new CombatSimulator();
        int[] legacyCount = {0};
        int[] elementalCount = {0};
        sim.addIndirectDamageListener(
                (owner, damage, time) -> legacyCount[0]++);
        sim.addElementalIndirectDamageListener(
                (owner, element, damage, time) -> elementalCount[0]++);

        sim.notifyIndirectDamage(null, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, legacyCount[0],
                "Legacy indirect dispatch reaches its existing listener once");
        StatefulWeaponRegressionSupport.assertEquals(
                0, elementalCount[0],
                "Legacy indirect dispatch does not fabricate an element");

        sim.notifyElementalIndirectDamage(null, Element.CRYO, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, legacyCount[0],
                "Typed elemental dispatch does not duplicate legacy fan-out");
        StatefulWeaponRegressionSupport.assertEquals(
                1, elementalCount[0],
                "Typed elemental dispatch reaches its narrow listener once");
    }

    private static void testSuperconductIntegration() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        AttackAction electroHit = hit(
                "Predator Superconduct fixture",
                Element.ELECTRO,
                ActionType.SKILL,
                true);
        electroHit.setICD(ICDType.None, ICDTag.None, 1.0);

        sim.notifyDamage(owner, electroHit, 100.0);
        assertNoStacks(weapon, 0.0,
                "Direct Electro damage cannot trigger Predator");

        sim.getEnemy().setAura(Element.CRYO, 8.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), electroHit);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Owner Superconduct emits accepted typed Cryo damage");

        sim.advanceTime(0.1);
        sim.getEnemy().setAura(Element.CRYO, 8.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), electroHit);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(0.1),
                "Second accepted Superconduct reaches the shared cap");
        SimulatorSnapshot acceptedPair = sim.saveSnapshot();

        sim.advanceTime(0.1);
        sim.getEnemy().setAura(Element.CRYO, 8.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), electroHit);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(6.099999),
                "Blocked third Superconduct attempt retains prior expiry");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(6.1),
                "Blocked third Superconduct attempt cannot refresh Predator");

        sim.restoreSnapshot(acceptedPair);
        sim.advanceTime(0.4);
        sim.getEnemy().setAura(Element.CRYO, 8.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), electroHit);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(6.499999),
                "Superconduct at the exact sequence boundary refreshes Predator");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(6.5),
                "Boundary Superconduct refresh keeps a half-open expiry");
    }

    private static void testCryoSwirlIntegration() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        AttackAction anemoHit = hit(
                "Predator Cryo Swirl fixture",
                Element.ANEMO,
                ActionType.SKILL,
                true);
        anemoHit.setICD(ICDType.None, ICDTag.None, 1.0);

        sim.getEnemy().setAura(Element.CRYO, 4.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), anemoHit);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Accepted Cryo Swirl emits typed Cryo damage");
    }

    private static void testScheduledElementalDispatch() {
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, null);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        int[] legacyCount = {0};
        List<Element> typedElements = new ArrayList<>();
        List<model.entity.Character> typedOwners = new ArrayList<>();
        sim.addIndirectDamageListener(
                (attributedOwner, damage, time) -> legacyCount[0]++);
        sim.addElementalIndirectDamageListener(
                (attributedOwner, element, damage, time) -> {
                    typedOwners.add(attributedOwner);
                    typedElements.add(element);
                });

        sim.getEnemy().setAura(Element.HYDRO, 2.0, sim.getCurrentTime());
        new ReactionEffectScheduler(sim).scheduleElectroCharged(
                owner.getCharacterId(), Element.ELECTRO, 4.0, 1000.0, false);
        sim.advanceTime(1.0);

        StatefulWeaponRegressionSupport.assertEquals(
                1, legacyCount[0],
                "Scheduled Electro-Charged retains one legacy notification");
        StatefulWeaponRegressionSupport.assertEquals(
                List.of(Element.ELECTRO), typedElements,
                "Scheduled Electro-Charged emits one canonical typed element");
        StatefulWeaponRegressionSupport.assertEquals(
                List.of(owner), typedOwners,
                "Scheduled Electro-Charged preserves attributed owner");
    }

    private static void testAloyOnlyFlatAttack() {
        Predator aloyWeapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter aloy =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, aloyWeapon);
        StatefulWeaponRegressionSupport.simulatorWith(aloy);
        StatefulWeaponRegressionSupport.assertClose(
                66.0,
                aloy.getEffectiveStats(0.0).get(StatType.ATK_FLAT),
                "Predator grants Aloy 66 flat ATK");

        Predator amberWeapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter amber =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, amberWeapon);
        StatefulWeaponRegressionSupport.simulatorWith(amber);
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                amber.getEffectiveStats(0.0).get(StatType.ATK_FLAT),
                "Predator does not grant flat ATK to another owner");
    }

    private static void testSnapshotRestore() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        SimulatorSnapshot inactive = sim.saveSnapshot();

        sim.notifyDamage(owner, cryoHit("First"), 100.0);
        SimulatorSnapshot oneStack = sim.saveSnapshot();
        sim.advanceTime(2.0);
        sim.notifyDamage(owner, cryoHit("Second"), 100.0);
        SimulatorSnapshot twoStacks = sim.saveSnapshot();
        sim.advanceTime(6.0);
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(8.0),
                "Predator expires before snapshot rollback");

        sim.restoreSnapshot(twoStacks);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(2.0),
                "Predator rollback restores two stacks");
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(7.999999),
                "Predator rollback restores refreshed expiry");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(8.0),
                "Predator restored expiry remains half-open");

        sim.restoreSnapshot(oneStack);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Predator rollback restores one stack");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(6.0),
                "Predator one-stack rollback restores original expiry");

        sim.restoreSnapshot(inactive);
        assertNoStacks(weapon, 0.0,
                "Predator rollback restores inactive state");
    }

    private static void testElementalIndirectSnapshotRestore() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        SimulatorSnapshot inactive = sim.saveSnapshot();

        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        SimulatorSnapshot oneStack = sim.saveSnapshot();
        sim.advanceTime(2.0);
        sim.notifyElementalIndirectDamage(owner, Element.CRYO, 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(2.0),
                "Typed indirect damage reaches two stacks before rollback");

        sim.restoreSnapshot(oneStack);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(0.0),
                "Rollback restores one typed indirect stack");
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(6.0),
                "Rollback restores typed indirect stack expiry");

        sim.restoreSnapshot(inactive);
        assertNoStacks(weapon, 0.0,
                "Rollback removes typed indirect stacks");
    }

    private static void testIndependentInstances() {
        Predator first = new Predator();
        Predator second = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter firstOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, first);
        StatefulWeaponRegressionSupport.TestCharacter secondOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, second);
        CombatSimulator firstSim =
                StatefulWeaponRegressionSupport.simulatorWith(firstOwner);
        StatefulWeaponRegressionSupport.simulatorWith(secondOwner);

        firstSim.notifyDamage(firstOwner, cryoHit("First instance"), 100.0);
        StatefulWeaponRegressionSupport.assertEquals(
                1, first.getStackCount(0.0),
                "First Predator owns its stack state");
        StatefulWeaponRegressionSupport.assertEquals(
                0, second.getStackCount(0.0),
                "Second Predator does not share stack state");
        assertAttackBonuses(firstOwner, 0.0, 0.10,
                "First Predator independent bonus");
        assertAttackBonuses(secondOwner, 0.0, 0.0,
                "Second Predator independent bonus");
    }

    private static void testBindingAndStateGuards() {
        Predator weapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator sim =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        weapon.initializeForSimulator(owner, sim);
        weapon.initializeForSimulator(owner, sim);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new Predator().restoreWeaponState(state),
                "Predator rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Predator rejects an incompatible state type");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Predator rejects cross-simulator reuse");

        StatefulWeaponRegressionSupport.TestCharacter otherOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, weapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(otherOwner, sim),
                "Predator rejects cross-owner reuse");

        Predator nullWeapon = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter nullOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, nullWeapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(null, sim),
                "Predator rejects a null owner");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(nullOwner, null),
                "Predator rejects a null simulator");

        Predator unequipped = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter wrongOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(wrongOwner, sim),
                "Predator rejects an unequipped owner");

        Predator outside = new Predator();
        StatefulWeaponRegressionSupport.TestCharacter outsideOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.ALOY, outside);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> outside.initializeForSimulator(outsideOwner, sim),
                "Predator rejects an owner outside the simulator party");
    }

    private static AttackAction cryoHit(String name) {
        return cryoHit(name, ActionType.NORMAL);
    }

    private static AttackAction cryoHit(
            String name,
            ActionType actionType) {
        return hit(name, Element.CRYO, actionType, true);
    }

    private static AttackAction hit(
            String name,
            Element element,
            boolean hitEffectTrigger) {
        return hit(name, element, ActionType.NORMAL, hitEffectTrigger);
    }

    private static AttackAction hit(
            String name,
            Element element,
            ActionType actionType,
            boolean hitEffectTrigger) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(hitEffectTrigger);
        return action;
    }

    private static void assertAttackBonuses(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            double currentTime,
            double expected,
            String message) {
        StatsContainer stats = owner.getEffectiveStats(currentTime);
        StatefulWeaponRegressionSupport.assertClose(
                expected,
                stats.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                message + " Normal");
        StatefulWeaponRegressionSupport.assertClose(
                expected,
                stats.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                message + " Charged");
    }

    private static void assertUnrelatedBonusesUnchanged(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            double currentTime) {
        StatsContainer stats = owner.getEffectiveStats(currentTime);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, stats.get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Predator does not grant Plunging DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, stats.get(StatType.SKILL_DMG_BONUS),
                "Predator does not grant Skill DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, stats.get(StatType.BURST_DMG_BONUS),
                "Predator does not grant Burst DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, stats.get(StatType.DMG_BONUS_ALL),
                "Predator does not grant generic DMG");
    }

    private static void assertNoStacks(
            Predator weapon,
            double currentTime,
            String message) {
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(currentTime), message);
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
