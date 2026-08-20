package sample;

import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.artifact.DisenchantmentInDeepShadow;
import model.artifact.ThunderingFury;
import model.artifact.ViridescentVenerer;
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
import model.weapon.ATeaspoonOfTranscendence;
import model.weapon.KagurasVerity;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for legacy content updated for Stellar reactions. */
public final class LegacyStellarContentRegressionTest {
    private static final double EPSILON = 1e-9;

    private LegacyStellarContentRegressionTest() {
    }

    /** Runs refinement, trigger, boundary, target-state, and snapshot checks. */
    public static void main(String[] args) {
        testTeaspoonR1R5AndBoundaries();
        testTeaspoonSnapshot();
        testKaguraR1R5AndBoundaries();
        testKaguraSnapshotAndStateGuards();
        testArtifactTypedBonuses();
        testDisenchantmentTargetStatesAndSnapshot();
        testThunderingFuryStellarEligibilityAndSnapshot();
        System.out.println("LegacyStellarContentRegressionTest passed");
    }

    /** Verifies Teaspoon's paired typed bonuses, trigger gate, cap, and expiry. */
    private static void testTeaspoonR1R5AndBoundaries() {
        ATeaspoonOfTranscendence r1 = new ATeaspoonOfTranscendence(1);
        TestCharacter owner = character(CharacterId.KEQING, r1);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);

        sim.notifyDamage(owner, hit(ActionType.NORMAL), 1.0);
        sim.notifyDamage(ally, hit(ActionType.CHARGE), 1.0);
        assertEquals(0, r1.getStackCount(0.0),
                "Teaspoon rejects non-Charged and foreign hits");

        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertStellarBonuses(owner, sim, 0.16, 0.16,
                "Teaspoon R1 one stack");
        sim.advanceTime(0.2);
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        sim.advanceTime(0.2);
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        sim.advanceTime(0.2);
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(3, r1.getStackCount(sim.getCurrentTime()),
                "Teaspoon caps at three stacks");
        assertStellarBonuses(owner, sim, 0.48, 0.48,
                "Teaspoon R1 three stacks");
        sim.advanceTime(5.0);
        assertStellarBonuses(owner, sim, 0.0, 0.0,
                "Teaspoon exact shared expiry");

        ATeaspoonOfTranscendence r5 = new ATeaspoonOfTranscendence(5);
        TestCharacter r5Owner = character(CharacterId.RAZOR, r5);
        CombatSimulator r5Sim = simulator(r5Owner);
        r5Sim.notifyDamage(r5Owner, hit(ActionType.CHARGE), 1.0);
        assertStellarBonuses(r5Owner, r5Sim, 0.32, 0.32,
                "Teaspoon R5 one stack");
    }

    /** Verifies simulator snapshots restore Teaspoon's stack and trigger gate. */
    private static void testTeaspoonSnapshot() {
        ATeaspoonOfTranscendence weapon = new ATeaspoonOfTranscendence(1);
        TestCharacter owner = character(CharacterId.KEQING, weapon);
        CombatSimulator sim = simulator(owner);
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(0.2);
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(2, weapon.getStackCount(sim.getCurrentTime()),
                "Teaspoon divergent snapshot state");
        sim.restoreSnapshot(snapshot);
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Teaspoon snapshot restores one stack");
        sim.notifyDamage(owner, hit(ActionType.CHARGE), 1.0);
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Teaspoon snapshot restores the 0.2-second gate");
    }

    /** Verifies Kagura's typed stack parity, field guard, cap, and expiry. */
    private static void testKaguraR1R5AndBoundaries() {
        KagurasVerity r1 = new KagurasVerity(1);
        TestCharacter owner = character(CharacterId.SUCROSE, r1);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);
        CharacterActionRequest skill = CharacterActionRequest.of(
                CharacterActionKey.SKILL);

        r1.onAction(ally, skill, sim);
        r1.onAction(owner, CharacterActionRequest.of(
                CharacterActionKey.NORMAL), sim);
        r1.onAction(owner, null, sim);
        assertEquals(0, r1.getStackCount(),
                "Kagura rejects foreign, non-Skill, and null actions");

        for (int stack = 1; stack <= 3; stack++) {
            r1.onAction(owner, skill, sim);
            assertClose(0.12 * stack,
                    stats(owner, sim).get(StatType.SKILL_DMG_BONUS),
                    "Kagura R1 Skill stack " + stack);
            assertClose(0.12 * stack,
                    stats(owner, sim).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                    "Kagura R1 Conduct stack " + stack);
        }
        r1.onAction(owner, skill, sim);
        assertEquals(3, r1.getStackCount(), "Kagura caps at three stacks");
        sim.advanceTime(24.0);
        assertClose(0.0,
                stats(owner, sim).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Kagura exact shared expiry");

        KagurasVerity r5 = new KagurasVerity(5);
        TestCharacter r5Owner = character(CharacterId.YAE_MIKO, r5);
        CombatSimulator r5Sim = simulator(r5Owner);
        r5.onAction(r5Owner, skill, r5Sim);
        assertClose(0.24,
                stats(r5Owner, r5Sim).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Kagura R5 one Conduct stack");

        CombatSimulator offFieldSim = simulator(
                character(CharacterId.AMBER, null),
                character(CharacterId.SUCROSE, new KagurasVerity(1)));
        TestCharacter offFieldOwner = (TestCharacter) offFieldSim.getCharacter(
                CharacterId.SUCROSE);
        KagurasVerity offField = (KagurasVerity) offFieldOwner.getWeapon();
        offField.onAction(offFieldOwner, skill, offFieldSim);
        assertEquals(0, offField.getStackCount(),
                "Kagura rejects off-field Skill callbacks");
    }

    /** Verifies Kagura snapshot rollback and instance-bound state validation. */
    private static void testKaguraSnapshotAndStateGuards() {
        KagurasVerity weapon = new KagurasVerity(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulator(owner);
        CharacterActionRequest skill = CharacterActionRequest.of(
                CharacterActionKey.SKILL);
        weapon.onAction(owner, skill, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        weapon.onAction(owner, skill, sim);
        weapon.onAction(owner, skill, sim);
        assertEquals(3, weapon.getStackCount(),
                "Kagura divergent snapshot state");
        sim.restoreSnapshot(snapshot);
        assertEquals(1, weapon.getStackCount(),
                "Kagura snapshot restores one stack");

        assertThrows(IllegalArgumentException.class,
                () -> new KagurasVerity(1).restoreWeaponState(state),
                "Kagura rejects foreign instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Kagura rejects null state");
    }

    /** Verifies every source-backed static Stellar artifact bonus. */
    private static void testArtifactTypedBonuses() {
        DisenchantmentInDeepShadow disenchantment =
                new DisenchantmentInDeepShadow();
        assertClose(0.40, disenchantment.getStats().get(
                StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Disenchantment Conduct bonus");
        ViridescentVenerer venerer = new ViridescentVenerer();
        assertClose(0.20, venerer.getStats().get(
                StatType.STELLAR_SWIRL_DMG_BONUS),
                "Viridescent Venerer Stellar-Swirl bonus");
        ThunderingFury fury = new ThunderingFury();
        assertClose(0.20, fury.getStats().get(
                StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Thundering Fury Conduct bonus");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 25.0);
        ViridescentVenerer preserved = new ViridescentVenerer(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Viridescent Venerer preserves supplied stats");
        assertClose(0.20, supplied.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Viridescent Venerer supplied Stellar-Swirl bonus");
    }

    /** Verifies both target states, half-open expiry, and manager snapshot restore. */
    private static void testDisenchantmentTargetStatesAndSnapshot() {
        DisenchantmentInDeepShadow artifact =
                new DisenchantmentInDeepShadow();
        TestCharacter owner = character(
                CharacterId.KEQING, null, artifact);
        CombatSimulator sim = simulator(owner);
        assertClose(0.0, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment has no CRIT bonus without target state");

        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Superconduct Physical RES Shred",
                BuffId.SUPERCONDUCT_PHYS_RES_SHRED,
                12.0,
                sim.getCurrentTime(),
                current -> current.add(StatType.PHYS_RES_SHRED, 0.40)));
        assertClose(0.16, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment recognizes Superconduct target state");
        sim.advanceTime(12.0);
        assertClose(0.0, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment Superconduct state expires exactly");

        sim.getStellarReactionManager().triggerStellarConduct(
                sim.getCurrentTime());
        assertClose(0.16, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment recognizes Stellar-Conduct target state");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(6.0);
        assertClose(0.0, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment Stellar-Conduct state expires exactly");
        sim.restoreSnapshot(snapshot);
        assertClose(0.16, stats(owner, sim).get(StatType.CRIT_RATE),
                "Disenchantment target state survives snapshot restore");
    }

    /** Verifies TF accepts Conduct, rejects Swirl/off-field, and restores its gate. */
    private static void testThunderingFuryStellarEligibilityAndSnapshot() {
        ThunderingFury artifact = new ThunderingFury();
        TestCharacter owner = character(
                CharacterId.KEQING, null, artifact);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulator(owner, ally);
        owner.markSkillUsed(0.0);
        artifact.onReaction(sim, stellarConduct(), owner, owner);
        assertClose(9.0, owner.getSkillCDRemaining(0.0),
                "Thundering Fury accepts Stellar-Conduct");
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(0.8);
        artifact.onReaction(sim, stellarConduct(), owner, owner);
        assertClose(7.2, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury accepts exact 0.8-second boundary");
        sim.restoreSnapshot(snapshot);
        artifact.onReaction(sim, stellarConduct(), owner, owner);
        assertClose(9.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury snapshot restores trigger gate");

        owner.removeBuff(BuffId.THUNDERING_FURY_4PC_TRIGGER_COOLDOWN);
        owner.markSkillUsed(sim.getCurrentTime());
        artifact.onReaction(sim, stellarSwirl(), owner, owner);
        assertClose(10.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury rejects Stellar-Swirl");
        sim.setActiveCharacter(CharacterId.AMBER);
        artifact.onReaction(sim, stellarConduct(), owner, owner);
        assertClose(10.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury rejects off-field Stellar-Conduct");
    }

    private static ReactionResult stellarConduct() {
        return ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_CONDUCT,
                Element.CRYO,
                Element.CRYO,
                true);
    }

    private static ReactionResult stellarSwirl() {
        return ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_SWIRL,
                Element.CRYO,
                Element.ANEMO,
                false);
    }

    private static AttackAction hit(ActionType actionType) {
        AttackAction action = new AttackAction(
                "Legacy Stellar Content Hit",
                1.0,
                Element.CRYO,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static TestCharacter character(
            CharacterId id,
            Weapon weapon,
            ArtifactSet... artifacts) {
        return new TestCharacter(id, weapon, artifacts);
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

    private static StatsContainer stats(
            TestCharacter character,
            CombatSimulator sim) {
        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        List<Buff> buffs = sim.getApplicableBuffs(character);
        for (Buff buff : buffs) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
    }

    private static void assertStellarBonuses(
            TestCharacter owner,
            CombatSimulator sim,
            double expectedConduct,
            double expectedSwirl,
            String message) {
        StatsContainer stats = stats(owner, sim);
        assertClose(expectedConduct,
                stats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                message + " Conduct");
        assertClose(expectedSwirl,
                stats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                message + " Swirl");
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
            int expected,
            int actual,
            String message) {
        if (expected != actual) {
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

    /** Minimal deterministic fixture for legacy equipment checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Weapon equippedWeapon,
                ArtifactSet... equippedArtifacts) {
            characterId = id;
            name = id.getDisplayName();
            element = Element.ELECTRO;
            weapon = equippedWeapon;
            artifacts = equippedArtifacts;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 0.0);
            setSkillCD(10.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
