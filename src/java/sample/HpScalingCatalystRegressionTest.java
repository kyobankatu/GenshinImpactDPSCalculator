package sample;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.EverlastingMoonglow;
import model.weapon.SurfsUp;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for the HP-scaling catalyst campaign. */
public final class HpScalingCatalystRegressionTest {
    private static final double EPSILON = 1e-8;

    private HpScalingCatalystRegressionTest() {
    }

    /** Runs metadata, trigger, boundary, snapshot, and guard checks. */
    public static void main(String[] args) {
        testMoonglowMetadataAndStats();
        testMoonglowEnergyWindow();
        testMoonglowSnapshotAndGuards();
        testSurfsUpMetadataAndActivation();
        testSurfsUpStackOrderAndBoundaries();
        testSurfsUpSnapshotAndGuards();
        System.out.println("HpScalingCatalystRegressionTest passed");
    }

    private static void testMoonglowMetadataAndStats() {
        EverlastingMoonglow defaultWeapon = new EverlastingMoonglow();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Moonglow default refinement");
        assertEquals("Everlasting Moonglow", defaultWeapon.getName(),
                "Moonglow name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Moonglow weapon type");
        assertClose(608.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Moonglow base ATK");
        assertClose(0.496,
                defaultWeapon.getStats().get(StatType.HP_PERCENT),
                "Moonglow HP substat");
        for (int refinement = 1; refinement <= 5; refinement++) {
            EverlastingMoonglow weapon =
                    new EverlastingMoonglow(refinement);
            assertClose(0.075 + 0.025 * refinement,
                    weapon.getHealingBonus(),
                    "Moonglow Healing Bonus R" + refinement);
            assertClose(0.005 + 0.005 * refinement,
                    weapon.getMaxHpNormalRatio(),
                    "Moonglow Max HP Normal ratio R" + refinement);
            StatsContainer stats = new StatsContainer();
            weapon.applyPassive(stats, 0.0);
            assertClose(weapon.getHealingBonus(),
                    stats.get(StatType.HEALING_BONUS),
                    "Moonglow supplied Healing Bonus R" + refinement);
            assertClose(weapon.getMaxHpNormalRatio(),
                    stats.get(StatType.MAX_HP_TO_NORMAL_FLAT_DMG_RATIO),
                    "Moonglow supplied Normal ratio R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new EverlastingMoonglow(0),
                "Moonglow rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new EverlastingMoonglow(6),
                "Moonglow rejects R6");
    }

    private static void testMoonglowEnergyWindow() {
        EverlastingMoonglow weapon = new EverlastingMoonglow(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.BURST),
                simulator);
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        assertClose(0.6, owner.getCurrentEnergy(),
                "Moonglow first Normal restores Energy");
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        assertClose(0.6, owner.getCurrentEnergy(),
                "Moonglow blocks same-time duplicate Energy");
        simulator.advanceTime(0.1 - EPSILON);
        weapon.onDamage(owner, normalHit(), simulator.getCurrentTime(), simulator);
        assertClose(0.6, owner.getCurrentEnergy(),
                "Moonglow blocks immediately before 0.1 seconds");
        simulator.advanceTime(EPSILON);
        weapon.onDamage(owner, normalHit(), simulator.getCurrentTime(), simulator);
        assertClose(1.2, owner.getCurrentEnergy(),
                "Moonglow reopens at exact 0.1 seconds");

        weapon.onDamage(owner, hit(ActionType.CHARGE, 1.0, true),
                simulator.getCurrentTime() + 1.0, simulator);
        weapon.onDamage(owner, hit(ActionType.NORMAL, 0.0, true),
                simulator.getCurrentTime() + 1.0, simulator);
        weapon.onDamage(owner, hit(ActionType.NORMAL, 1.0, false),
                simulator.getCurrentTime() + 1.0, simulator);
        weapon.onDamage(ally, normalHit(),
                simulator.getCurrentTime() + 1.0, simulator);
        weapon.onDamage(owner, normalHit(),
                simulator.getCurrentTime() + 1.0, new CombatSimulator());
        assertClose(1.2, owner.getCurrentEnergy(),
                "Moonglow rejects wrong damage callbacks");

        simulator.advanceTime(12.0 - simulator.getCurrentTime());
        weapon.onDamage(owner, normalHit(), simulator.getCurrentTime(), simulator);
        assertClose(1.2, owner.getCurrentEnergy(),
                "Moonglow window is half-open at twelve seconds");

        simulator.switchCharacter(CharacterId.AMBER);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.BURST),
                simulator);
        simulator.switchCharacter(CharacterId.SUCROSE);
        weapon.onDamage(owner, normalHit(), simulator.getCurrentTime(), simulator);
        assertClose(1.2, owner.getCurrentEnergy(),
                "Moonglow off-field Burst does not open a window");
    }

    private static void testMoonglowSnapshotAndGuards() {
        EverlastingMoonglow weapon = new EverlastingMoonglow(2);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.BURST),
                simulator);
        SimulatorSnapshot active = simulator.saveSnapshot();
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        SimulatorSnapshot gated = simulator.saveSnapshot();

        simulator.restoreSnapshot(active);
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        assertClose(0.6, owner.getCurrentEnergy(),
                "Moonglow restores pre-hit snapshot window");
        simulator.restoreSnapshot(gated);
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        assertClose(0.6, owner.getCurrentEnergy(),
                "Moonglow restores post-hit Energy gate");

        weapon.initializeForSimulator(owner, simulator);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> new EverlastingMoonglow(2).restoreWeaponState(state),
                "Moonglow rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Moonglow rejects foreign state type");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Moonglow rejects cross-simulator reuse");

        EverlastingMoonglow unequipped = new EverlastingMoonglow(1);
        TestCharacter wrongOwner = character(
                CharacterId.AMBER, Element.PYRO, null);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        wrongOwner, simulator),
                "Moonglow rejects unequipped owner");
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, simulator),
                "Moonglow rejects null owner");
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(wrongOwner, null),
                "Moonglow rejects null simulator");

        EverlastingMoonglow externalWeapon = new EverlastingMoonglow(1);
        TestCharacter externalOwner = character(
                CharacterId.LISA, Element.ELECTRO, externalWeapon);
        assertThrows(IllegalArgumentException.class,
                () -> externalWeapon.initializeForSimulator(
                        externalOwner, simulator),
                "Moonglow rejects owner outside simulator party");
    }

    private static void testSurfsUpMetadataAndActivation() {
        SurfsUp defaultWeapon = new SurfsUp();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Surf's Up default refinement");
        assertEquals("Surf's Up", defaultWeapon.getName(),
                "Surf's Up name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Surf's Up weapon type");
        assertClose(542.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Surf's Up base ATK");
        assertClose(0.882,
                defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Surf's Up CRIT DMG substat");
        for (int refinement = 1; refinement <= 5; refinement++) {
            SurfsUp weapon = new SurfsUp(refinement);
            assertClose(0.15 + 0.05 * refinement,
                    weapon.getHpBonus(),
                    "Surf's Up HP bonus R" + refinement);
            assertClose(0.09 + 0.03 * refinement,
                    weapon.getNormalDamageBonusPerStack(),
                    "Surf's Up stack bonus R" + refinement);
        }

        SurfsUp weapon = new SurfsUp(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        weapon.onAction(
                owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                simulator);
        assertEquals(4, weapon.getStackCount(0.0),
                "Surf's Up Skill starts at four stacks");
        assertClose(0.20,
                resolvedStats(owner, simulator).get(StatType.HP_PERCENT),
                "Surf's Up applies unconditional R1 HP");
        assertClose(0.48,
                resolvedStats(owner, simulator).get(
                        StatType.NORMAL_ATTACK_DMG_BONUS),
                "Surf's Up applies four R1 Normal stacks");
        assertThrows(IllegalArgumentException.class,
                () -> new SurfsUp(0), "Surf's Up rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new SurfsUp(6), "Surf's Up rejects R6");
    }

    private static void testSurfsUpStackOrderAndBoundaries() {
        SurfsUp weapon = new SurfsUp(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), simulator);
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        assertEquals(3, weapon.getStackCount(0.0),
                "Surf's Up Normal consumes one stack");
        weapon.onDamage(owner, normalHit(), 1.5 - EPSILON, simulator);
        assertEquals(3, weapon.getStackCount(1.5 - EPSILON),
                "Surf's Up blocks loss before 1.5 seconds");
        weapon.onDamage(owner, normalHit(), 1.5, simulator);
        assertEquals(2, weapon.getStackCount(1.5),
                "Surf's Up reopens loss at exact 1.5 seconds");

        ReactionResult vaporize = ReactionResult.amp(
                2.0, "Vaporize", ReactionResult.Kind.VAPORIZE);
        weapon.onElementalReaction(vaporize, owner, 1.5, simulator);
        assertEquals(3, weapon.getStackCount(1.5),
                "Surf's Up Vaporize restores one stack");
        weapon.onElementalReaction(vaporize, owner, 3.0 - EPSILON, simulator);
        assertEquals(3, weapon.getStackCount(3.0 - EPSILON),
                "Surf's Up blocks gain before 1.5 seconds");
        weapon.onElementalReaction(vaporize, owner, 3.0, simulator);
        assertEquals(4, weapon.getStackCount(3.0),
                "Surf's Up reopens gain at exact 1.5 seconds");

        simulator.advanceTime(15.0);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), simulator);
        weapon.onElementalReaction(vaporize, owner, 15.0, simulator);
        assertEquals(5, weapon.getStackCount(15.0),
                "Surf's Up retains a temporary fifth same-hit stack");
        weapon.onDamage(owner, normalHit(), 15.0, simulator);
        assertEquals(4, weapon.getStackCount(15.0),
                "Surf's Up same-hit gain precedes Normal loss");

        SurfsUp normalizeWeapon = new SurfsUp(1);
        TestCharacter normalizeOwner = character(
                CharacterId.AMBER, Element.PYRO, normalizeWeapon);
        CombatSimulator normalizeSimulator = simulatorWith(normalizeOwner);
        normalizeWeapon.onAction(normalizeOwner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                normalizeSimulator);
        normalizeWeapon.onElementalReaction(
                vaporize, normalizeOwner, 0.0, normalizeSimulator);
        assertEquals(5, normalizeWeapon.getStackCount(0.5 - EPSILON),
                "Surf's Up fifth stack survives before 0.5 seconds");
        assertEquals(4, normalizeWeapon.getStackCount(0.5),
                "Surf's Up fifth stack normalizes at exact 0.5 seconds");
        assertEquals(0, normalizeWeapon.getStackCount(14.0),
                "Surf's Up stack window expires at exact fourteen seconds");

        SurfsUp persistentGateWeapon = new SurfsUp(1);
        TestCharacter persistentGateOwner = character(
                CharacterId.LISA, Element.ELECTRO, persistentGateWeapon);
        CombatSimulator persistentGateSimulator = simulatorWith(
                persistentGateOwner);
        persistentGateWeapon.onAction(
                persistentGateOwner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                persistentGateSimulator);
        persistentGateSimulator.advanceTime(13.9);
        persistentGateWeapon.onElementalReaction(
                vaporize,
                persistentGateOwner,
                persistentGateSimulator.getCurrentTime(),
                persistentGateSimulator);
        persistentGateWeapon.onDamage(
                persistentGateOwner,
                normalHit(),
                persistentGateSimulator.getCurrentTime(),
                persistentGateSimulator);
        persistentGateSimulator.advanceTime(1.1);
        persistentGateWeapon.onAction(
                persistentGateOwner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                persistentGateSimulator);
        persistentGateWeapon.onElementalReaction(
                vaporize,
                persistentGateOwner,
                persistentGateSimulator.getCurrentTime(),
                persistentGateSimulator);
        assertEquals(4, persistentGateWeapon.getStackCount(15.0),
                "Surf's Up Skill preserves the Vaporize gain gate");
        persistentGateWeapon.onDamage(
                persistentGateOwner,
                normalHit(),
                persistentGateSimulator.getCurrentTime(),
                persistentGateSimulator);
        assertEquals(4, persistentGateWeapon.getStackCount(15.0),
                "Surf's Up Skill preserves the Normal loss gate");
    }

    private static void testSurfsUpSnapshotAndGuards() {
        SurfsUp weapon = new SurfsUp(2);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), simulator);
        SimulatorSnapshot fourStacks = simulator.saveSnapshot();
        weapon.onDamage(owner, normalHit(), 0.0, simulator);
        SimulatorSnapshot threeStacks = simulator.saveSnapshot();
        simulator.restoreSnapshot(fourStacks);
        assertEquals(4, weapon.getStackCount(0.0),
                "Surf's Up restores four-stack snapshot");
        simulator.restoreSnapshot(threeStacks);
        assertEquals(3, weapon.getStackCount(0.0),
                "Surf's Up restores loss-gated snapshot");

        ReactionResult vaporize = ReactionResult.amp(
                2.0, "Vaporize", ReactionResult.Kind.VAPORIZE);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.BURST), simulator);
        weapon.onElementalReaction(
                ReactionResult.amp(2.0, "Melt", ReactionResult.Kind.MELT),
                owner, 2.0, simulator);
        weapon.onElementalReaction(vaporize, ally, 2.0, simulator);
        weapon.onElementalReaction(vaporize, owner, 2.0,
                new CombatSimulator());
        weapon.onDamage(owner, hit(ActionType.CHARGE, 1.0, true),
                2.0, simulator);
        weapon.onDamage(owner, hit(ActionType.NORMAL, 0.0, true),
                2.0, simulator);
        weapon.onDamage(ally, normalHit(), 2.0, simulator);
        assertEquals(3, weapon.getStackCount(2.0),
                "Surf's Up rejects wrong callbacks");

        simulator.switchCharacter(CharacterId.AMBER);
        weapon.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), simulator);
        weapon.onElementalReaction(vaporize, owner, 3.0, simulator);
        weapon.onDamage(owner, normalHit(), 3.0, simulator);
        assertEquals(3, weapon.getStackCount(3.0),
                "Surf's Up rejects off-field triggers");

        weapon.initializeForSimulator(owner, simulator);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> new SurfsUp(2).restoreWeaponState(state),
                "Surf's Up rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Surf's Up rejects foreign state type");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Surf's Up rejects cross-simulator reuse");

        SurfsUp unequipped = new SurfsUp(1);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(ally, simulator),
                "Surf's Up rejects unequipped owner");
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, simulator),
                "Surf's Up rejects null owner");
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(ally, null),
                "Surf's Up rejects null simulator");

        SurfsUp externalWeapon = new SurfsUp(1);
        TestCharacter externalOwner = character(
                CharacterId.LISA, Element.ELECTRO, externalWeapon);
        assertThrows(IllegalArgumentException.class,
                () -> externalWeapon.initializeForSimulator(
                        externalOwner, simulator),
                "Surf's Up rejects owner outside simulator party");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static AttackAction normalHit() {
        return hit(ActionType.NORMAL, 1.0, true);
    }

    private static StatsContainer resolvedStats(
            Character character,
            CombatSimulator simulator) {
        return character.getEffectiveStats(simulator.getCurrentTime());
    }

    private static AttackAction hit(
            ActionType type,
            double damagePercent,
            boolean hitEffectTrigger) {
        AttackAction action = new AttackAction(
                "HP Catalyst Test",
                damagePercent,
                Element.HYDRO,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(hitEffectTrigger);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
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

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element element,
                Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            weapon = equippedWeapon;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public double getEnergyCost() {
            return 80.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
