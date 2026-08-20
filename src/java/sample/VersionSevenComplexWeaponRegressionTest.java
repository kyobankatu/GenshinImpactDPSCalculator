package sample;

import java.util.List;
import java.util.Objects;

import mechanics.formula.DamageCalculator;
import mechanics.reaction.ReactionResult;
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
import model.weapon.ForgedByTheGoldenMelody;
import model.weapon.WhitelakeFrostfeather;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused Version 7.0 regression for the two timer-heavy Stellar weapons. */
public final class VersionSevenComplexWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private VersionSevenComplexWeaponRegressionTest() {
    }

    /** Runs metadata, trigger-boundary, off-field, and snapshot regressions. */
    public static void main(String[] args) {
        testWhitelakeMetadataAndRefinement();
        testWhitelakeIndependentStacksAndBoundaries();
        testWhitelakeStellarEnergyAndSnapshot();
        testForgedMetadataAndFailClosedInitialPhase();
        testForgedCycleCopyAndOffFieldBehavior();
        testForgedSnapshotAndGuards();
        System.out.println("VersionSevenComplexWeaponRegressionTest passed");
    }

    private static void testWhitelakeMetadataAndRefinement() {
        WhitelakeFrostfeather defaultWeapon = new WhitelakeFrostfeather();
        assertEquals("Whitelake Frostfeather", defaultWeapon.getName(),
                "Whitelake display name");
        assertEquals(WeaponType.SWORD, defaultWeapon.getWeaponType(),
                "Whitelake weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Whitelake default refinement");
        assertClose(674.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Whitelake base ATK");
        assertClose(0.221, defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Whitelake CRIT Rate");

        double[] attacks = { 0.0, 0.08, 0.10, 0.12, 0.14, 0.16 };
        double[] critDamage = { 0.0, 0.50, 0.65, 0.80, 0.95, 1.10 };
        double[] energy = { 0.0, 4.0, 4.5, 5.0, 5.5, 6.0 };
        for (int refinement = 1; refinement <= 5; refinement++) {
            WhitelakeFrostfeather weapon =
                    new WhitelakeFrostfeather(refinement);
            assertClose(attacks[refinement], weapon.getAttackBonusPerStack(),
                    "Whitelake ATK R" + refinement);
            assertClose(critDamage[refinement], weapon.getStellarCritDamage(),
                    "Whitelake Stellar CRIT DMG R" + refinement);
            assertClose(energy[refinement], weapon.getEnergyRecovery(),
                    "Whitelake Energy R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new WhitelakeFrostfeather(0),
                "Whitelake rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new WhitelakeFrostfeather(6),
                "Whitelake rejects refinement six");
    }

    private static void testWhitelakeIndependentStacksAndBoundaries() {
        WhitelakeFrostfeather weapon = new WhitelakeFrostfeather(1);
        TestCharacter owner = character(CharacterId.KEQING, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);

        calculate(owner, skillHit(), sim);
        calculate(owner, skillHit(), sim);
        calculate(ally, skillHit(), sim);
        calculate(owner, normalHit(), sim);
        assertEquals(1, weapon.getStackCount(0.0),
                "Whitelake rejects same-time, foreign, and non-Skill hits");

        sim.advanceTime(0.1 - EPSILON);
        calculate(owner, skillHit(), sim);
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Whitelake rejects a hit immediately before 0.1 seconds");
        sim.advanceTime(EPSILON);
        calculate(owner, skillHit(), sim);
        sim.advanceTime(0.1);
        calculate(owner, skillHit(), sim);
        assertEquals(3, weapon.getStackCount(sim.getCurrentTime()),
                "Whitelake accepts the cooldown boundary and reaches three stacks");

        StatsContainer fullStacks = stats(owner, sim);
        assertClose(0.24, fullStacks.get(StatType.ATK_PERCENT),
                "Whitelake applies three independent ATK stacks");
        assertClose(0.50,
                fullStacks.get(StatType.STELLAR_CONDUCT_CRIT_DMG),
                "Whitelake applies Stellar-Conduct CRIT DMG at three stacks");
        assertClose(0.50,
                fullStacks.get(StatType.STELLAR_SWIRL_CRIT_DMG),
                "Whitelake applies Stellar-Swirl CRIT DMG at three stacks");

        sim.advanceTime(0.1);
        calculate(owner, skillHit(), sim);
        assertEquals(3, weapon.getStackCount(0.3),
                "Whitelake refreshes the earliest stack at the cap");
        assertEquals(3, weapon.getStackCount(8.1 - EPSILON),
                "Whitelake keeps all refreshed stacks before the first expiry");
        assertEquals(2, weapon.getStackCount(8.1),
                "Whitelake expires each stack at its independent boundary");
        StatsContainer partialStacks = owner.getEffectiveStats(8.1);
        assertClose(0.16, partialStacks.get(StatType.ATK_PERCENT),
                "Whitelake retains only two ATK stacks after expiry");
        assertClose(0.0,
                partialStacks.get(StatType.STELLAR_CONDUCT_CRIT_DMG),
                "Whitelake removes Stellar CRIT DMG below three stacks");
    }

    private static void testWhitelakeStellarEnergyAndSnapshot() {
        WhitelakeFrostfeather weapon = new WhitelakeFrostfeather(1);
        TestCharacter owner = character(CharacterId.KEQING, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);
        owner.spendEnergy(100.0);

        calculate(owner, skillHit(), sim);
        sim.advanceTime(0.1);
        calculate(owner, skillHit(), sim);
        sim.advanceTime(0.1);
        calculate(owner, skillHit(), sim);
        sim.switchCharacter(CharacterId.AMBER);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), owner);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Whitelake restores Energy for an off-field Stellar reaction");

        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_SWIRL), owner);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), ally);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Whitelake rejects cooldown and foreign Stellar events");
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(3.5 - EPSILON);
        calculate(owner, stellarHit(), sim);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Whitelake rejects direct Stellar damage before 3.5 seconds");
        sim.advanceTime(EPSILON);
        calculate(owner, stellarHit(), sim);
        assertClose(8.0, owner.getCurrentEnergy(),
                "Whitelake accepts direct Stellar damage at the cooldown boundary");

        sim.restoreSnapshot(snapshot);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Whitelake snapshot restores Energy state");
        assertEquals(3, weapon.getStackCount(sim.getCurrentTime()),
                "Whitelake snapshot restores independent stack expirations");
        calculate(owner, stellarHit(), sim);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Whitelake snapshot restores the Energy cooldown");
        sim.advanceTime(3.5);
        calculate(owner, stellarHit(), sim);
        assertClose(8.0, owner.getCurrentEnergy(),
                "Whitelake restored cooldown reopens at the exact boundary");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        WhitelakeFrostfeather other = new WhitelakeFrostfeather(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Whitelake rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Whitelake rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, sim),
                "Whitelake rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Whitelake rejects rebinding");
    }

    private static void testForgedMetadataAndFailClosedInitialPhase() {
        ForgedByTheGoldenMelody defaultWeapon =
                new ForgedByTheGoldenMelody();
        assertEquals("Forged by the Golden Melody", defaultWeapon.getName(),
                "Forged display name");
        assertEquals(WeaponType.CLAYMORE, defaultWeapon.getWeaponType(),
                "Forged weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Forged default refinement");
        assertClose(510.0, defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Forged base ATK");
        assertClose(0.276, defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Forged CRIT Rate");

        double[] attacks = { 0.0, 0.18, 0.225, 0.27, 0.315, 0.36 };
        double[] mastery = { 0.0, 120.0, 150.0, 180.0, 210.0, 240.0 };
        double[] stellar = { 0.0, 0.28, 0.35, 0.42, 0.49, 0.56 };
        for (int refinement = 1; refinement <= 5; refinement++) {
            ForgedByTheGoldenMelody weapon =
                    new ForgedByTheGoldenMelody(refinement);
            assertClose(attacks[refinement], weapon.getAttackBonus(),
                    "Forged ATK R" + refinement);
            assertClose(mastery[refinement], weapon.getElementalMasteryBonus(),
                    "Forged EM R" + refinement);
            assertClose(stellar[refinement], weapon.getStellarDamageBonus(),
                    "Forged Stellar DMG R" + refinement);
            assertTrue(!weapon.isImmediateInitialMovementAssumed(),
                    "Forged fails closed before the first ten-second tick at R"
                            + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ForgedByTheGoldenMelody(0),
                "Forged rejects refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new ForgedByTheGoldenMelody(6),
                "Forged rejects refinement six");
    }

    private static void testForgedCycleCopyAndOffFieldBehavior() {
        ForgedByTheGoldenMelody weapon = new ForgedByTheGoldenMelody(1);
        TestCharacter owner = character(CharacterId.KEQING, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);
        sim.switchCharacter(CharacterId.AMBER);

        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), owner);
        assertEquals(ForgedByTheGoldenMelody.MovementType.NONE,
                weapon.getCopiedMovementType(0.0),
                "Forged does not copy an unsupported immediate movement");
        assertClose(0.0, stats(owner, sim).get(StatType.ATK_PERCENT),
                "Forged has no movement before the first timer tick");

        sim.advanceTime(10.0 - sim.getCurrentTime() - EPSILON);
        assertEquals(ForgedByTheGoldenMelody.MovementType.NONE,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged remains inactive immediately before ten seconds");
        sim.advanceTime(EPSILON);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ATTACK,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged starts with ATK at the ten-second boundary off-field");

        sim.notifyReaction(ReactionResult.transform(
                0.0,
                "Superconduct",
                ReactionResult.Kind.SUPERCONDUCT,
                Element.ELECTRO,
                Element.CRYO), owner);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_SWIRL), ally);
        assertEquals(ForgedByTheGoldenMelody.MovementType.NONE,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged rejects non-Stellar and foreign reaction events");
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_SWIRL), owner);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ATTACK,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged copies the off-field owner's active ATK movement");
        assertClose(0.36, stats(owner, sim).get(StatType.ATK_PERCENT),
                "Forged stacks original and copied ATK movements");

        sim.advanceTime(10.0);
        StatsContainer mixed = stats(owner, sim);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ELEMENTAL_MASTERY,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged advances from ATK to EM");
        assertClose(0.18, mixed.get(StatType.ATK_PERCENT),
                "Forged retains copied ATK across an original phase change");
        assertClose(120.0, mixed.get(StatType.ELEMENTAL_MASTERY),
                "Forged applies original EM after twenty seconds");

        sim.advanceTime(2.0 - EPSILON);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), owner);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ATTACK,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged rejects copy refresh before twelve seconds");
        sim.advanceTime(EPSILON);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), owner);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ELEMENTAL_MASTERY,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged copies EM at the exact cooldown boundary");
        assertClose(240.0, stats(owner, sim).get(StatType.ELEMENTAL_MASTERY),
                "Forged stacks original and copied EM movements");

        sim.advanceTime(8.0);
        StatsContainer stellarPhase = stats(owner, sim);
        assertEquals(ForgedByTheGoldenMelody.MovementType.STELLAR_DAMAGE,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged advances from EM to Stellar damage");
        assertClose(0.28,
                stellarPhase.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Forged applies Stellar-Conduct damage bonus");
        assertClose(0.28,
                stellarPhase.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Forged applies Stellar-Swirl damage bonus");

        sim.advanceTime(4.0);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_SWIRL), owner);
        StatsContainer doubledStellar = stats(owner, sim);
        assertClose(0.56,
                doubledStellar.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Forged copies Stellar damage at the exact cooldown boundary");
        assertClose(0.56,
                doubledStellar.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Forged copied Stellar movement covers both reaction families");
    }

    private static void testForgedSnapshotAndGuards() {
        ForgedByTheGoldenMelody weapon = new ForgedByTheGoldenMelody(1);
        TestCharacter owner = character(CharacterId.KEQING, weapon);
        CombatSimulator sim = simulator(owner);
        sim.advanceTime(30.0);
        sim.notifyReaction(stellar(ReactionResult.Kind.STELLAR_CONDUCT), owner);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(10.0);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ATTACK,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged divergent timeline returns to ATK");
        sim.restoreSnapshot(snapshot);
        assertEquals(ForgedByTheGoldenMelody.MovementType.STELLAR_DAMAGE,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged snapshot restores the original phase");
        assertEquals(ForgedByTheGoldenMelody.MovementType.STELLAR_DAMAGE,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged snapshot restores the copied movement");
        sim.advanceTime(10.0);
        assertEquals(ForgedByTheGoldenMelody.MovementType.ATTACK,
                weapon.getMovementType(sim.getCurrentTime()),
                "Forged snapshot reconstructs the next TimerEvent");
        assertEquals(ForgedByTheGoldenMelody.MovementType.STELLAR_DAMAGE,
                weapon.getCopiedMovementType(sim.getCurrentTime()),
                "Forged copied movement keeps its independent twelve-second expiry");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        ForgedByTheGoldenMelody other = new ForgedByTheGoldenMelody(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Forged rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "Forged rejects another state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, sim),
                "Forged rejects null owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "Forged rejects rebinding");
    }

    private static TestCharacter character(CharacterId id, Weapon weapon) {
        return new TestCharacter(id, weapon);
    }

    private static CombatSimulator simulator(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static StatsContainer stats(TestCharacter owner, CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime());
    }

    private static double calculate(
            TestCharacter owner,
            AttackAction action,
            CombatSimulator sim) {
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                List.of(),
                sim.getCurrentTime(),
                1.0,
                sim);
    }

    private static AttackAction skillHit() {
        return hit("Version Seven Skill Hit", ActionType.SKILL);
    }

    private static AttackAction normalHit() {
        return hit("Version Seven Normal Hit", ActionType.NORMAL);
    }

    private static AttackAction stellarHit() {
        AttackAction action = hit("Version Seven Stellar Hit", ActionType.SKILL);
        action.setStellarReactionType(AttackAction.StellarReactionType.CONDUCT);
        return action;
    }

    private static AttackAction hit(String name, ActionType type) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.CRYO,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static ReactionResult stellar(ReactionResult.Kind kind) {
        return ReactionResult.stellar(
                0.0,
                kind,
                Element.CRYO,
                kind == ReactionResult.Kind.STELLAR_CONDUCT
                        ? Element.ELECTRO
                        : Element.ANEMO,
                false);
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
        if (!Objects.equals(expected, actual)) {
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

    /** Minimal deterministic owner with a nonzero Energy bar. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.CRYO;
            weapon = equippedWeapon;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 1.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 100.0;
        }
    }
}
