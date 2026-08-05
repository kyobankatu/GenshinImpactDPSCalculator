package sample;

import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SwordOfNarzissenkreuz;
import model.weapon.SwordOfNarzissenkreuz.EnergyBlastType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused metadata, trigger, timing, rollback, isolation, and guard checks. */
public final class SwordOfNarzissenkreuzRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double BLAST_DELAY = 0.1;

    private SwordOfNarzissenkreuzRegressionTest() {
    }

    /** Runs Hero's Blade metadata, behavior, boundary, and state regressions. */
    public static void main(String[] args) {
        testMetadataRefinementAndBlastSelection();
        testEligibleActionTypesAndDelayedPhysicalDamage();
        testHitDamageOwnerAndFieldEligibility();
        testKnownArkheCharactersFailClosed();
        testExactCooldownBoundaryAndPendingPersistence();
        testSnapshotRollbackAndIndependentInstances();
        testBindingStateAndInputGuards();
        System.out.println("SwordOfNarzissenkreuzRegressionTest passed");
    }

    private static void testMetadataRefinementAndBlastSelection() {
        SwordOfNarzissenkreuz defaultWeapon =
                new SwordOfNarzissenkreuz();
        assertEquals(
                "Sword of Narzissenkreuz",
                defaultWeapon.getName(),
                "Narzissenkreuz display name");
        assertEquals(
                WeaponType.SWORD,
                defaultWeapon.getWeaponType(),
                "Narzissenkreuz weapon type");
        assertEquals(
                5,
                defaultWeapon.getRefinement(),
                "Narzissenkreuz default refinement");
        assertEquals(
                EnergyBlastType.OUSIA,
                defaultWeapon.getEnergyBlastType(),
                "Narzissenkreuz default blast selection");
        assertClose(
                510.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Narzissenkreuz base ATK");
        assertClose(
                0.413,
                defaultWeapon.getStats().get(StatType.ATK_PERCENT),
                "Narzissenkreuz ATK percent");

        for (int refinement = 1; refinement <= 5; refinement++) {
            SwordOfNarzissenkreuz weapon =
                    new SwordOfNarzissenkreuz(
                            refinement,
                            EnergyBlastType.PNEUMA);
            assertClose(
                    1.2 + 0.4 * refinement,
                    weapon.getBlastMotionValue(),
                    "Narzissenkreuz blast multiplier R" + refinement);
            assertEquals(
                    EnergyBlastType.PNEUMA,
                    weapon.getEnergyBlastType(),
                    "Narzissenkreuz retains typed blast selection");
        }
    }

    private static void testEligibleActionTypesAndDelayedPhysicalDamage() {
        ActionType[] eligible = {
            ActionType.NORMAL,
            ActionType.CHARGE,
            ActionType.PLUNGE
        };
        for (ActionType actionType : eligible) {
            SwordOfNarzissenkreuz weapon =
                    new SwordOfNarzissenkreuz(1);
            StatefulWeaponRegressionSupport.TestCharacter owner =
                    StatefulWeaponRegressionSupport.character(
                            CharacterId.KEQING,
                            weapon);
            CombatSimulator simulator =
                    StatefulWeaponRegressionSupport.simulatorWith(owner);
            resolve(simulator, owner, positiveHit(actionType));
            double damageAfterTrigger = simulator.getTotalDamage();
            assertEquals(
                    1,
                    weapon.getPendingBlastCount(),
                    "Narzissenkreuz queues " + actionType + " blast");

            simulator.advanceTime(BLAST_DELAY - EPSILON);
            assertClose(
                    damageAfterTrigger,
                    simulator.getTotalDamage(),
                    "Narzissenkreuz waits 0.1 seconds for " + actionType);
            simulator.advanceTime(EPSILON);
            assertTrue(
                    simulator.getTotalDamage() > damageAfterTrigger,
                    "Narzissenkreuz resolves Physical damage for " + actionType);
            assertEquals(
                    0,
                    weapon.getPendingBlastCount(),
                    "Narzissenkreuz consumes pending " + actionType + " blast");
        }

        double r1Blast = resolveBlastDamage(1);
        double r5Blast = resolveBlastDamage(5);
        assertClose(
                2.0,
                r5Blast / r1Blast,
                "Narzissenkreuz R5 blast is exactly twice R1");
    }

    private static void testHitDamageOwnerAndFieldEligibility() {
        SwordOfNarzissenkreuz weapon = new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER,
                        null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        AttackAction noHitEffect = positiveHit(ActionType.NORMAL);
        noHitEffect.setHitEffectTrigger(false);
        resolve(simulator, owner, noHitEffect);
        resolve(simulator, owner, zeroDamageHit());
        resolve(simulator, owner, positiveHit(ActionType.SKILL));
        resolve(simulator, owner, positiveHit(ActionType.BURST));
        resolve(simulator, owner, positiveHit(ActionType.OTHER));
        resolve(simulator, ally, positiveHit(ActionType.NORMAL));
        assertEquals(
                0,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz rejects non-hit, zero, wrong type, and foreign actor");

        simulator.setActiveCharacter(CharacterId.AMBER);
        resolve(simulator, owner, positiveHit(ActionType.NORMAL));
        assertEquals(
                0,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz rejects an off-field owner hit");

        simulator.setActiveCharacter(CharacterId.KEQING);
        resolve(simulator, owner, positiveHit(ActionType.NORMAL));
        assertEquals(
                1,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz accepts the active equipped owner");
    }

    private static void testKnownArkheCharactersFailClosed() {
        CharacterId[] knownArkheCharacters = {
            CharacterId.FREMINET,
            CharacterId.LYNETTE,
            CharacterId.CHARLOTTE
        };
        for (CharacterId characterId : knownArkheCharacters) {
            assertTrue(
                    SwordOfNarzissenkreuz.isKnownArkheCharacter(characterId),
                    "Narzissenkreuz recognizes native Arkhe for " + characterId);
            SwordOfNarzissenkreuz weapon =
                    new SwordOfNarzissenkreuz(1);
            StatefulWeaponRegressionSupport.TestCharacter owner =
                    StatefulWeaponRegressionSupport.character(
                            characterId,
                            weapon);
            CombatSimulator simulator =
                    StatefulWeaponRegressionSupport.simulatorWith(owner);
            resolve(simulator, owner, positiveHit(ActionType.NORMAL));
            assertEquals(
                    0,
                    weapon.getPendingBlastCount(),
                    "Narzissenkreuz fails closed for " + characterId);
        }
        assertTrue(
                !SwordOfNarzissenkreuz.isKnownArkheCharacter(CharacterId.KEQING),
                "Narzissenkreuz leaves non-Arkhe characters eligible");
        assertTrue(
                !SwordOfNarzissenkreuz.isKnownArkheCharacter(null),
                "Narzissenkreuz handles a null Arkhe identity safely");
    }

    private static void testExactCooldownBoundaryAndPendingPersistence() {
        SwordOfNarzissenkreuz weapon = new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER,
                        null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);

        resolve(simulator, owner, positiveHit(ActionType.NORMAL));
        assertClose(
                12.0,
                weapon.getNextProcTime(),
                "Narzissenkreuz cooldown starts at trigger time");
        resolve(simulator, owner, positiveHit(ActionType.CHARGE));
        assertEquals(
                1,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz cooldown prevents duplicate pending blasts");
        simulator.setActiveCharacter(CharacterId.AMBER);
        double damageBeforeBlast = simulator.getTotalDamage();
        simulator.advanceTime(BLAST_DELAY);
        assertTrue(
                simulator.getTotalDamage() > damageBeforeBlast,
                "Narzissenkreuz pending blast persists after switch-out");

        simulator.setActiveCharacter(CharacterId.KEQING);
        simulator.advanceTime(12.0 - BLAST_DELAY - EPSILON);
        resolve(simulator, owner, positiveHit(ActionType.PLUNGE));
        assertEquals(
                0,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz remains gated immediately before 12 seconds");
        simulator.advanceTime(EPSILON);
        resolve(simulator, owner, positiveHit(ActionType.PLUNGE));
        assertEquals(
                1,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz reopens at exactly 12 seconds");
        assertClose(
                24.0,
                weapon.getNextProcTime(),
                "Narzissenkreuz advances exact cooldown boundary");
    }

    private static void testSnapshotRollbackAndIndependentInstances() {
        SwordOfNarzissenkreuz weapon = new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        resolve(simulator, owner, positiveHit(ActionType.NORMAL));
        double triggerDamage = simulator.getTotalDamage();
        simulator.advanceTime(0.05);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(0.05);
        assertTrue(
                simulator.getTotalDamage() > triggerDamage,
                "Narzissenkreuz live branch resolves pending blast");

        simulator.restoreSnapshot(snapshot);
        assertClose(
                triggerDamage,
                simulator.getTotalDamage(),
                "Narzissenkreuz rollback restores damage report");
        assertEquals(
                1,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz rollback restores pending blast");
        assertClose(
                12.0,
                weapon.getNextProcTime(),
                "Narzissenkreuz rollback restores cooldown");
        simulator.advanceTime(0.05);
        assertTrue(
                simulator.getTotalDamage() > triggerDamage,
                "Narzissenkreuz restored blast resolves once");
        assertEquals(
                0,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz restored blast is consumed");

        SwordOfNarzissenkreuz independent =
                new SwordOfNarzissenkreuz(1);
        assertEquals(
                0,
                independent.getPendingBlastCount(),
                "Narzissenkreuz instances isolate pending blasts");
        assertTrue(
                Double.isInfinite(independent.getNextProcTime())
                        && independent.getNextProcTime() < 0.0,
                "Narzissenkreuz instances isolate cooldowns");
    }

    private static void testBindingStateAndInputGuards() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SwordOfNarzissenkreuz(0),
                "Narzissenkreuz rejects R0");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SwordOfNarzissenkreuz(6),
                "Narzissenkreuz rejects R6");
        assertThrows(
                NullPointerException.class,
                () -> new SwordOfNarzissenkreuz(1, null),
                "Narzissenkreuz rejects a null blast selection");

        SwordOfNarzissenkreuz weapon = new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        weapon.initializeForSimulator(owner, simulator);
        weapon.onDamage(owner, null, 1.0, 0.0);
        assertEquals(
                0,
                weapon.getPendingBlastCount(),
                "Narzissenkreuz rejects a null damage action");

        SwordOfNarzissenkreuz unequipped =
                new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(bare, bareSimulator),
                "Narzissenkreuz rejects an unequipped owner");
        assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner,
                        new CombatSimulator()),
                "Narzissenkreuz rejects cross-simulator rebinding");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SwordOfNarzissenkreuz(1)
                        .initializeForSimulator(null, simulator),
                "Narzissenkreuz rejects a null owner");

        SwordOfNarzissenkreuz absent =
                new SwordOfNarzissenkreuz(1);
        StatefulWeaponRegressionSupport.TestCharacter absentOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        absent);
        assertThrows(
                IllegalArgumentException.class,
                () -> absent.initializeForSimulator(
                        absentOwner,
                        new CombatSimulator()),
                "Narzissenkreuz rejects an owner outside the party");

        SwordOfNarzissenkreuz foreign =
                new SwordOfNarzissenkreuz(1);
        SnapshotAwareWeaponEffect.State foreignState =
                foreign.captureWeaponState();
        assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreignState),
                "Narzissenkreuz rejects another instance state");
        assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Narzissenkreuz rejects a foreign state type");
        assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Narzissenkreuz rejects null state");
    }

    private static double resolveBlastDamage(int refinement) {
        SwordOfNarzissenkreuz weapon =
                new SwordOfNarzissenkreuz(refinement);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.KEQING,
                        weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        resolve(simulator, owner, positiveHit(ActionType.NORMAL));
        double triggerDamage = simulator.getTotalDamage();
        simulator.advanceTime(BLAST_DELAY);
        return simulator.getTotalDamage() - triggerDamage;
    }

    private static void resolve(
            CombatSimulator simulator,
            StatefulWeaponRegressionSupport.TestCharacter actor,
            AttackAction action) {
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(),
                action);
    }

    private static AttackAction positiveHit(ActionType actionType) {
        AttackAction action = new AttackAction(
                "Narzissenkreuz Regression Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                false,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static AttackAction zeroDamageHit() {
        AttackAction action = new AttackAction(
                "Narzissenkreuz Zero Damage Hit",
                0.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                false,
                ActionType.NORMAL);
        action.setHitEffectTrigger(true);
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
        if (!expected.equals(actual)) {
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

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
