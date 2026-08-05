package sample;

import java.util.List;

import mechanics.formula.DamageCalculator;
import model.entity.ArtifactSet;
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
import model.weapon.BeaconOfTheReedSea;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused regression checks for Beacon of the Reed Sea's supported branches. */
public final class BeaconOfTheReedSeaRegressionTest {
    private static final double EPSILON = 1e-8;

    private BeaconOfTheReedSeaRegressionTest() {
    }

    /** Runs metadata, trigger, timing, snapshot, isolation, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testHpAndAttackIsolation();
        testSkillHitRoutingAndRefresh();
        testOffFieldOwnerAndBindingGuards();
        testSnapshotRollbackAndIndependentInstances();
        System.out.println("BeaconOfTheReedSeaRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        BeaconOfTheReedSea defaultWeapon = new BeaconOfTheReedSea();
        assertEquals("Beacon of the Reed Sea", defaultWeapon.getName(),
                "Beacon display name");
        assertEquals(WeaponType.CLAYMORE, defaultWeapon.getWeaponType(),
                "Beacon weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Beacon default refinement");
        assertClose(608.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Beacon base ATK");
        assertClose(0.331,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Beacon CRIT Rate");

        for (int refinement = 1; refinement <= 5; refinement++) {
            BeaconOfTheReedSea weapon =
                    new BeaconOfTheReedSea(refinement);
            assertClose(0.15 + 0.05 * refinement,
                    weapon.getSkillHitAttackBonus(),
                    "Beacon Skill-hit ATK R" + refinement);
            assertClose(0.24 + 0.08 * refinement,
                    weapon.getNoShieldHpBonus(),
                    "Beacon no-shield HP R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconOfTheReedSea(0),
                "Beacon rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new BeaconOfTheReedSea(6),
                "Beacon rejects R6");
    }

    private static void testHpAndAttackIsolation() {
        BeaconOfTheReedSea weapon = new BeaconOfTheReedSea(1);
        TestCharacter owner = character(
                CharacterId.RAZOR, Element.ELECTRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);

        StatsContainer inactive = seededStats();
        weapon.applyPassive(inactive, 0.0);
        assertClose(0.42, inactive.get(StatType.HP_PERCENT),
                "Beacon applies supported no-shield HP branch");
        assertClose(0.20, inactive.get(StatType.ATK_PERCENT),
                "Beacon excludes Skill ATK before a hit");
        assertClose(0.30, inactive.get(StatType.DMG_BONUS_ALL),
                "Beacon does not alter generic damage");

        weapon.onDamage(owner, skillHit(), 0.0, simulator);
        StatsContainer active = seededStats();
        weapon.applyPassive(active, 0.0);
        assertClose(0.42, active.get(StatType.HP_PERCENT),
                "Beacon keeps no-shield HP during Skill window");
        assertClose(0.40, active.get(StatType.ATK_PERCENT),
                "Beacon applies only one Skill-hit ATK stack");
        assertClose(0.30, active.get(StatType.DMG_BONUS_ALL),
                "Beacon Skill window preserves unrelated stats");
    }

    private static void testSkillHitRoutingAndRefresh() {
        BeaconOfTheReedSea weapon = new BeaconOfTheReedSea(1);
        TestCharacter owner = character(
                CharacterId.RAZOR, Element.ELECTRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);

        calculate(owner, skillHit(), simulator);
        assertTrue(weapon.isSkillWindowActive(0.0),
                "Beacon accepts an owner Skill hit through damage hooks");
        assertTrue(weapon.isSkillWindowActive(8.0 - EPSILON),
                "Beacon window remains active before eight seconds");
        assertTrue(!weapon.isSkillWindowActive(8.0),
                "Beacon window expires at exactly eight seconds");

        weapon.onDamage(owner, skillHit(), 3.0, simulator);
        assertClose(11.0, weapon.getSkillWindowUntil(),
                "Beacon refreshes its window on a later Skill hit");
        assertTrue(weapon.isSkillWindowActive(11.0 - EPSILON),
                "Beacon refreshed window remains half-open");
        assertTrue(!weapon.isSkillWindowActive(11.0),
                "Beacon refreshed window expires at its exact boundary");

        AttackAction skillClassifiedHit = hit(ActionType.NORMAL, 1.0, true);
        skillClassifiedHit.setCountsAsSkillDmg(true);
        weapon.onDamage(owner, skillClassifiedHit, 11.0, simulator);
        assertClose(19.0, weapon.getSkillWindowUntil(),
                "Beacon accepts damage classified as Skill damage");

        double unchangedUntil = weapon.getSkillWindowUntil();
        weapon.onDamage(owner, hit(ActionType.BURST, 1.0, true),
                12.0, simulator);
        weapon.onDamage(owner, hit(ActionType.SKILL, 0.0, true),
                12.0, simulator);
        weapon.onDamage(owner, hit(ActionType.SKILL, 1.0, false),
                12.0, simulator);
        weapon.onDamage(owner, null, 12.0, simulator);
        assertClose(unchangedUntil, weapon.getSkillWindowUntil(),
                "Beacon rejects non-Skill, zero-damage, dummy, and null hits");
    }

    private static void testOffFieldOwnerAndBindingGuards() {
        BeaconOfTheReedSea weapon = new BeaconOfTheReedSea(1);
        TestCharacter owner = character(
                CharacterId.RAZOR, Element.ELECTRO, weapon);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        simulator.switchCharacter(CharacterId.AMBER);
        double offFieldHitTime = simulator.getCurrentTime();
        calculate(owner, skillHit(), simulator);
        assertClose(offFieldHitTime + 8.0, weapon.getSkillWindowUntil(),
                "Beacon accepts delayed off-field owner Skill damage");

        weapon.onDamage(ally, skillHit(), 2.0, simulator);
        weapon.onDamage(owner, skillHit(), 2.0, new CombatSimulator());
        assertClose(offFieldHitTime + 8.0, weapon.getSkillWindowUntil(),
                "Beacon rejects wrong owner and simulator callbacks");

        BeaconOfTheReedSea unequipped = new BeaconOfTheReedSea(1);
        TestCharacter bare = character(
                CharacterId.SUCROSE, Element.ANEMO, null);
        CombatSimulator bareSimulator = simulatorWith(bare);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Beacon rejects an unequipped owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Beacon rejects cross-simulator rebinding");
    }

    private static void testSnapshotRollbackAndIndependentInstances() {
        BeaconOfTheReedSea weapon = new BeaconOfTheReedSea(1);
        TestCharacter owner = character(
                CharacterId.RAZOR, Element.ELECTRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        weapon.onDamage(owner, skillHit(), 0.0, simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        weapon.onDamage(owner, skillHit(), 4.0, simulator);
        assertClose(12.0, weapon.getSkillWindowUntil(),
                "Beacon state changes before rollback");
        simulator.restoreSnapshot(snapshot);
        assertClose(8.0, weapon.getSkillWindowUntil(),
                "Beacon rollback restores the original window");

        BeaconOfTheReedSea independent = new BeaconOfTheReedSea(1);
        assertTrue(!independent.isSkillWindowActive(0.0),
                "Beacon instances keep independent windows");
        SnapshotAwareWeaponEffect.State foreign =
                independent.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Beacon rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Beacon rejects a foreign state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Beacon rejects null state");
    }

    private static StatsContainer seededStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.HP_PERCENT, 0.10);
        stats.set(StatType.ATK_PERCENT, 0.20);
        stats.set(StatType.DMG_BONUS_ALL, 0.30);
        return stats;
    }

    private static double calculate(
            TestCharacter owner,
            AttackAction action,
            CombatSimulator simulator) {
        return DamageCalculator.calculateDamage(
                owner,
                simulator.getEnemy(),
                action,
                List.of(),
                simulator.getCurrentTime(),
                1.0,
                simulator);
    }

    private static AttackAction skillHit() {
        return hit(ActionType.SKILL, 1.0, true);
    }

    private static AttackAction hit(
            ActionType actionType,
            double damagePercent,
            boolean hitEffectTrigger) {
        AttackAction action = new AttackAction(
                "Beacon Test Hit",
                damagePercent,
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(hitEffectTrigger);
        return action;
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
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
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }

    /** Minimal deterministic owner used by the focused weapon checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element element,
                Weapon weapon) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            this.weapon = weapon;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return weapon;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}
