package sample;

import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.MountainBracingBolt;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused metadata, trigger, timing, snapshot, and binding checks. */
public final class MountainBracingBoltRegressionTest {
    private static final double EPSILON = 1e-8;

    private MountainBracingBoltRegressionTest() {
    }

    /** Runs all Mountain-Bracing Bolt regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinementTable();
        testPermanentAndTriggeredSkillBonus();
        testPressHoldAndFieldPersistence();
        testExactBoundaryRefreshAndCooldownWait();
        testSnapshotRestoreAndIndependentInstances();
        testIgnoredAndRejectedActions();
        testBindingAndStateGuards();
        System.out.println("MountainBracingBoltRegressionTest passed");
    }

    private static void testMetadataAndRefinementTable() {
        MountainBracingBolt defaultWeapon = new MountainBracingBolt();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Mountain-Bracing Bolt default refinement");
        assertEquals("Mountain-Bracing Bolt", defaultWeapon.getName(),
                "Mountain-Bracing Bolt name");
        assertEquals(WeaponType.POLEARM, defaultWeapon.getWeaponType(),
                "Mountain-Bracing Bolt weapon type");
        assertClose(565.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Mountain-Bracing Bolt base ATK");
        assertClose(0.306,
                defaultWeapon.getStats().get(StatType.ENERGY_RECHARGE),
                "Mountain-Bracing Bolt Energy Recharge");
        assertClose(0.15, defaultWeapon.getClimbingStaminaReduction(),
                "Mountain-Bracing Bolt climbing stamina reduction");

        double[] expected = {0.12, 0.15, 0.18, 0.21, 0.24};
        for (int refinement = 1; refinement <= 5; refinement++) {
            assertClose(expected[refinement - 1],
                    new MountainBracingBolt(refinement)
                            .getSkillDamageBonus(),
                    "Mountain-Bracing Bolt Skill bonus R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new MountainBracingBolt(0),
                "Mountain-Bracing Bolt rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new MountainBracingBolt(6),
                "Mountain-Bracing Bolt rejects R6");
    }

    private static void testPermanentAndTriggeredSkillBonus() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);

        assertClose(0.12, skillBonus(owner, -1.0),
                "Negative time applies only the permanent copy");
        assertTrue(!weapon.isTriggeredBonusActive(-1.0),
                "Negative time is outside the triggered window");
        assertClose(0.12, skillBonus(owner, 0.0),
                "Inactive weapon applies permanent Skill bonus");

        simulator.setActiveCharacter(ally.getCharacterId());
        performSkill(simulator, ally, SkillActionMode.PRESS);
        assertClose(0.24, skillBonus(owner, simulator.getCurrentTime()),
                "Active R1 window applies exactly two Skill copies");

        StatsContainer stats = new StatsContainer();
        stats.set(StatType.NORMAL_ATTACK_DMG_BONUS, 0.01);
        stats.set(StatType.CHARGED_ATTACK_DMG_BONUS, 0.02);
        stats.set(StatType.PLUNGING_ATTACK_DMG_BONUS, 0.03);
        stats.set(StatType.SKILL_DMG_BONUS, 0.04);
        stats.set(StatType.BURST_DMG_BONUS, 0.05);
        stats.set(StatType.DMG_BONUS_ALL, 0.06);
        weapon.applyPassive(stats, simulator.getCurrentTime());
        assertClose(0.01, stats.get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Mountain-Bracing Bolt does not change Normal DMG");
        assertClose(0.02, stats.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Mountain-Bracing Bolt does not change Charged DMG");
        assertClose(0.03, stats.get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Mountain-Bracing Bolt does not change Plunging DMG");
        assertClose(0.28, stats.get(StatType.SKILL_DMG_BONUS),
                "Mountain-Bracing Bolt changes only Skill DMG");
        assertClose(0.05, stats.get(StatType.BURST_DMG_BONUS),
                "Mountain-Bracing Bolt does not change Burst DMG");
        assertClose(0.06, stats.get(StatType.DMG_BONUS_ALL),
                "Mountain-Bracing Bolt does not change all-DMG bonus");
    }

    private static void testPressHoldAndFieldPersistence() {
        MountainBracingBolt weapon = new MountainBracingBolt(5);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BEIDOU, null, true, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);

        simulator.setActiveCharacter(ally.getCharacterId());
        performSkill(simulator, ally, SkillActionMode.PRESS);
        assertClose(0.48, skillBonus(owner, simulator.getCurrentTime()),
                "Another active member Press Skill triggers R5 copy");
        simulator.advanceTime(8.0);
        performSkill(simulator, ally, SkillActionMode.HOLD);
        assertClose(0.48, skillBonus(owner, simulator.getCurrentTime()),
                "Supported Hold Skill triggers R5 copy");

        assertTrue(simulator.getActiveCharacter() == ally,
                "Owner is off-field when ally triggers the passive");
        simulator.switchCharacter(owner.getCharacterId());
        assertTrue(simulator.getActiveCharacter() == owner,
                "Owner switches in after the trigger");
        assertClose(0.48, skillBonus(owner, simulator.getCurrentTime()),
                "Triggered copy persists through owner switch-in");
    }

    private static void testExactBoundaryRefreshAndCooldownWait() {
        MountainBracingBolt boundaryWeapon = new MountainBracingBolt(1);
        TestCharacter boundaryOwner = character(
                CharacterId.YUN_JIN, boundaryWeapon, false, 0.0);
        TestCharacter boundaryAlly = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator boundarySimulator = simulatorWith(
                boundaryOwner, boundaryAlly);
        boundarySimulator.setActiveCharacter(boundaryAlly.getCharacterId());

        performSkill(boundarySimulator, boundaryAlly, SkillActionMode.PRESS);
        assertTrue(boundaryWeapon.isTriggeredBonusActive(0.0),
                "Triggered copy starts at the accepted input timestamp");
        assertTrue(boundaryWeapon.isTriggeredBonusActive(7.999999),
                "Triggered copy remains active at 7.999999 seconds");
        assertTrue(!boundaryWeapon.isTriggeredBonusActive(8.0),
                "Triggered copy expires at the exact eight-second boundary");

        boundarySimulator.advanceTime(4.0);
        performSkill(boundarySimulator, boundaryAlly, SkillActionMode.PRESS);
        assertClose(0.24,
                skillBonus(boundaryOwner, boundarySimulator.getCurrentTime()),
                "Refresh never creates a third Skill bonus copy");
        assertTrue(boundaryWeapon.isTriggeredBonusActive(11.999999),
                "Refresh replaces the half-open expiry");
        assertTrue(!boundaryWeapon.isTriggeredBonusActive(12.0),
                "Refreshed copy expires exactly eight seconds later");

        MountainBracingBolt cooldownWeapon = new MountainBracingBolt(1);
        TestCharacter cooldownOwner = character(
                CharacterId.YUN_JIN, cooldownWeapon, false, 0.0);
        TestCharacter cooldownAlly = character(
                CharacterId.BENNETT, null, false, 3.0);
        CombatSimulator cooldownSimulator = simulatorWith(
                cooldownOwner, cooldownAlly);
        cooldownSimulator.setActiveCharacter(cooldownAlly.getCharacterId());
        performSkill(cooldownSimulator, cooldownAlly, SkillActionMode.PRESS);
        performSkill(cooldownSimulator, cooldownAlly, SkillActionMode.PRESS);
        assertClose(3.0, cooldownSimulator.getCurrentTime(),
                "Second Skill auto-waits for the ally cooldown");
        assertTrue(cooldownWeapon.isTriggeredBonusActive(10.999999),
                "Auto-wait refresh uses the accepted post-wait timestamp");
        assertTrue(!cooldownWeapon.isTriggeredBonusActive(11.0),
                "Auto-wait refresh expires from the post-wait timestamp");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        simulator.setActiveCharacter(ally.getCharacterId());
        SimulatorSnapshot inactive = simulator.saveSnapshot();

        performSkill(simulator, ally, SkillActionMode.PRESS);
        SimulatorSnapshot active = simulator.saveSnapshot();
        simulator.advanceTime(8.0);
        SimulatorSnapshot expired = simulator.saveSnapshot();
        assertClose(0.12, skillBonus(owner, simulator.getCurrentTime()),
                "Window is inactive before active snapshot restore");

        simulator.restoreSnapshot(active);
        assertClose(0.24, skillBonus(owner, simulator.getCurrentTime()),
                "Active simulator snapshot restores the window");
        simulator.restoreSnapshot(expired);
        assertClose(0.12, skillBonus(owner, simulator.getCurrentTime()),
                "Inactive expired snapshot restores permanent copy only");
        simulator.restoreSnapshot(inactive);
        assertClose(0.12, skillBonus(owner, simulator.getCurrentTime()),
                "Inactive pre-trigger snapshot clears the window");

        MountainBracingBolt first = new MountainBracingBolt(1);
        MountainBracingBolt second = new MountainBracingBolt(5);
        TestCharacter firstOwner = character(
                CharacterId.YUN_JIN, first, false, 0.0);
        TestCharacter firstAlly = character(
                CharacterId.BENNETT, null, false, 0.0);
        TestCharacter secondOwner = character(
                CharacterId.KUJOU_SARA, second, false, 0.0);
        TestCharacter secondAlly = character(
                CharacterId.DILUC, null, false, 0.0);
        CombatSimulator firstSimulator = simulatorWith(firstOwner, firstAlly);
        CombatSimulator secondSimulator = simulatorWith(
                secondOwner, secondAlly);
        firstSimulator.setActiveCharacter(firstAlly.getCharacterId());
        performSkill(firstSimulator, firstAlly, SkillActionMode.PRESS);
        assertClose(0.24, skillBonus(firstOwner, 0.0),
                "First weapon instance owns its active window");
        assertClose(0.24, skillBonus(secondOwner, 0.0),
                "Second weapon instance remains at permanent R5 copy");
        assertTrue(!second.isTriggeredBonusActive(0.0),
                "Independent instance does not share trigger state");
    }

    private static void testIgnoredAndRejectedActions() {
        testOwnerAndWrongTypedActions();
        testDirectSkillAttackAndOffFieldActor();
        testUnsupportedNullAndUnknownRequests();
    }

    private static void testOwnerAndWrongTypedActions() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);

        performSkill(simulator, owner, SkillActionMode.PRESS);
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Owner Skill does not trigger Mountain-Bracing Bolt");

        simulator.setActiveCharacter(ally.getCharacterId());
        CharacterActionKey[] wrongActions = {
                CharacterActionKey.NORMAL,
                CharacterActionKey.CHARGE,
                CharacterActionKey.PLUNGE,
                CharacterActionKey.BURST,
                CharacterActionKey.DASH
        };
        for (CharacterActionKey key : wrongActions) {
            simulator.performAction(
                    ally.getCharacterId(), CharacterActionRequest.of(key));
            assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                    "Wrong typed action does not trigger: " + key);
        }
    }

    private static void testDirectSkillAttackAndOffFieldActor() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        simulator.setActiveCharacter(ally.getCharacterId());
        AttackAction directSkill = new AttackAction(
                "Direct Skill AttackAction",
                1.0,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        simulator.performAction(ally.getCharacterId(), directSkill);
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Direct Skill AttackAction does not imitate typed input");

        simulator.setActiveCharacter(owner.getCharacterId());
        performSkill(simulator, ally, SkillActionMode.PRESS);
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Direct typed action from an off-field actor is ignored");
    }

    private static void testUnsupportedNullAndUnknownRequests() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        simulator.setActiveCharacter(ally.getCharacterId());

        assertThrows(IllegalArgumentException.class,
                () -> performSkill(
                        simulator, ally, SkillActionMode.HOLD),
                "Unsupported Hold mode is rejected before notification");
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Unsupported Skill mode does not trigger");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        ally.getCharacterId(),
                        (CharacterActionRequest) null),
                "Null typed request is rejected");
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Null typed request does not trigger");
        assertThrows(RuntimeException.class,
                () -> simulator.performAction(
                        CharacterId.UNKNOWN,
                        CharacterActionRequest.skill(
                                SkillActionMode.PRESS)),
                "Unknown actor is rejected");
        simulator.notifyActionRequest(null, null);
        assertPermanentOnly(weapon, owner, simulator.getCurrentTime(),
                "Null direct notification is ignored");
    }

    private static void testBindingAndStateGuards() {
        MountainBracingBolt weapon = new MountainBracingBolt(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, weapon, false, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, null, false, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        weapon.initializeForSimulator(owner, simulator);
        weapon.initializeForSimulator(owner, simulator);

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> new MountainBracingBolt(1)
                        .restoreWeaponState(state),
                "Mountain-Bracing Bolt rejects foreign instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Mountain-Bracing Bolt rejects wrong state type");

        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Mountain-Bracing Bolt rejects cross-simulator reuse");
        TestCharacter otherOwner = character(
                CharacterId.KUJOU_SARA, weapon, false, 0.0);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        otherOwner, simulator),
                "Mountain-Bracing Bolt rejects cross-owner reuse");

        MountainBracingBolt nullWeapon = new MountainBracingBolt(1);
        TestCharacter nullOwner = character(
                CharacterId.YUN_JIN, nullWeapon, false, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(null, simulator),
                "Mountain-Bracing Bolt rejects null owner");
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(nullOwner, null),
                "Mountain-Bracing Bolt rejects null simulator");

        MountainBracingBolt unequipped = new MountainBracingBolt(1);
        TestCharacter wrongOwner = character(
                CharacterId.KUJOU_SARA, null, false, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        wrongOwner, simulator),
                "Mountain-Bracing Bolt rejects unequipped owner");

        MountainBracingBolt outside = new MountainBracingBolt(1);
        TestCharacter outsideOwner = character(
                CharacterId.KUJOU_SARA, outside, false, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> outside.initializeForSimulator(
                        outsideOwner, simulator),
                "Mountain-Bracing Bolt rejects owner outside simulator party");
    }

    private static TestCharacter character(
            CharacterId id,
            Weapon weapon,
            boolean supportsHold,
            double skillCooldown) {
        return new TestCharacter(id, weapon, supportsHold, skillCooldown);
    }

    private static CombatSimulator simulatorWith(
            TestCharacter... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void performSkill(
            CombatSimulator simulator,
            TestCharacter actor,
            SkillActionMode mode) {
        simulator.performAction(
                actor.getCharacterId(),
                CharacterActionRequest.skill(mode));
    }

    private static double skillBonus(Character owner, double time) {
        return owner.getEffectiveStats(time).get(StatType.SKILL_DMG_BONUS);
    }

    private static void assertPermanentOnly(
            MountainBracingBolt weapon,
            Character owner,
            double time,
            String message) {
        assertTrue(!weapon.isTriggeredBonusActive(time), message + " window");
        assertClose(weapon.getSkillDamageBonus(), skillBonus(owner, time),
                message + " stats");
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Minimal typed-action fixture with configurable Hold and Skill cooldown. */
    private static final class TestCharacter extends Character {
        private final boolean supportsHold;

        private TestCharacter(
                CharacterId id,
                Weapon equippedWeapon,
                boolean supportsHold,
                double skillCooldown) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.PYRO;
            weapon = equippedWeapon;
            this.supportsHold = supportsHold;
            setSkillCD(skillCooldown);
            baseStats.set(StatType.BASE_ATK, 100.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        protected boolean supportsSkillActionMode(SkillActionMode mode) {
            return mode == SkillActionMode.PRESS
                    || supportsHold && mode == SkillActionMode.HOLD;
        }

        @Override
        public void onAction(
                CharacterActionRequest request,
                CombatSimulator simulator) {
            if (request.getKey() == CharacterActionKey.SKILL) {
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
            }
        }
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
