package sample;

import java.util.List;
import java.util.Objects;

import mechanics.formula.DamageCalculator;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Shared deterministic fixtures for the stateful weapon regression batch. */
final class StatefulWeaponRegressionSupport {
    private static final double EPSILON = 1e-8;

    private StatefulWeaponRegressionSupport() {
    }

    /** Creates a minimal deterministic character with an optional weapon. */
    static TestCharacter character(CharacterId id, Weapon weapon) {
        return new TestCharacter(id, weapon);
    }

    /** Creates a simulator containing the supplied party in order. */
    static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Creates one deterministic positive damage hit. */
    static AttackAction hit(String name, ActionType type) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PYRO,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(true);
        return action;
    }

    /** Resolves one hit through the standard damage and weapon callback path. */
    static double calculate(
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

    /** Returns the owner's current effective stats. */
    static StatsContainer stats(TestCharacter owner, CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime());
    }

    /** Asserts two floating-point values are equal within test tolerance. */
    static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Asserts two values are equal. */
    static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Asserts a condition. */
    static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Asserts the action throws the selected type. */
    static void assertThrows(
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
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    /** Minimal owner with deterministic offensive stats. */
    static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.PYRO;
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
            return 0.0;
        }
    }
}
