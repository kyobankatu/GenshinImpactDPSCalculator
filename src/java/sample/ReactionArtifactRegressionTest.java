package sample;

import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionResult;
import model.artifact.CrimsonWitchOfFlames;
import model.artifact.ThunderingFury;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Regression checks for Crimson Witch and Thundering Fury. */
public class ReactionArtifactRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs metadata, formula, stack, cooldown, and lifecycle checks. */
    public static void main(String[] args) {
        testMetadataAndReactionStats();
        testReactionCalculatorBonusRouting();
        testCombatResolverReactionBonusRouting();
        testCrimsonWitchSkillStacks();
        testCrimsonWitchSnapshotAndLifecycle();
        testThunderingFuryCooldownReduction();
        testThunderingFuryEligibilityAndSnapshot();
        testNullStats();
        System.out.println("ReactionArtifactRegressionTest passed");
    }

    /** Verifies canonical names and every fixed reaction bonus. */
    private static void testMetadataAndReactionStats() {
        CrimsonWitchOfFlames crimson = new CrimsonWitchOfFlames();
        assertEquals("Crimson Witch of Flames", crimson.getName(),
                "Crimson Witch name");
        assertClose(0.15, crimson.getStats().get(StatType.PYRO_DMG_BONUS),
                "Crimson Witch Pyro bonus");
        assertClose(0.40, crimson.getStats().get(StatType.OVERLOAD_DMG_BONUS),
                "Crimson Witch Overload bonus");
        assertClose(0.40, crimson.getStats().get(StatType.BURNING_DMG_BONUS),
                "Crimson Witch Burning bonus");
        assertClose(0.40, crimson.getStats().get(StatType.BURGEON_DMG_BONUS),
                "Crimson Witch Burgeon bonus");
        assertClose(0.15, crimson.getStats().get(StatType.VAPORIZE_DMG_BONUS),
                "Crimson Witch Vaporize bonus");
        assertClose(0.15, crimson.getStats().get(StatType.MELT_DMG_BONUS),
                "Crimson Witch Melt bonus");

        ThunderingFury fury = new ThunderingFury();
        assertEquals("Thundering Fury", fury.getName(),
                "Thundering Fury name");
        assertClose(0.15, fury.getStats().get(StatType.ELECTRO_DMG_BONUS),
                "Thundering Fury Electro bonus");
        assertClose(0.40, fury.getStats().get(
                StatType.ELECTRO_CHARGED_DMG_BONUS),
                "Thundering Fury Electro-Charged bonus");
        assertClose(0.40, fury.getStats().get(
                StatType.SUPERCONDUCT_DMG_BONUS),
                "Thundering Fury Superconduct bonus");
        assertClose(0.40, fury.getStats().get(StatType.HYPERBLOOM_DMG_BONUS),
                "Thundering Fury Hyperbloom bonus");
        assertClose(0.20, fury.getStats().get(StatType.AGGRAVATE_DMG_BONUS),
                "Thundering Fury Aggravate bonus");
        assertClose(0.20, fury.getStats().get(
                StatType.LUNAR_CHARGED_DMG_BONUS),
                "Thundering Fury Lunar-Charged bonus");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 50.0);
        CrimsonWitchOfFlames preserved = new CrimsonWitchOfFlames(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Crimson Witch should retain supplied stats");
        assertClose(50.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                "Reaction artifact supplied stat preservation");
    }

    /** Verifies the shared calculator consumes amp and transformative bonuses. */
    private static void testReactionCalculatorBonusRouting() {
        ReactionResult vaporize = ReactionCalculator.calculate(
                Element.HYDRO, Element.PYRO, 0.0, 90, 0.15);
        assertClose(2.30, vaporize.getAmpMultiplier(),
                "Vaporize reaction bonus");
        ReactionResult melt = ReactionCalculator.calculate(
                Element.PYRO, Element.CRYO, 0.0, 90, 0.15);
        assertClose(2.30, melt.getAmpMultiplier(),
                "Melt reaction bonus");

        assertTransformativeBonus(
                Element.PYRO, Element.DENDRO, 0.40, "Burning bonus");
        assertTransformativeBonus(
                Element.PYRO, Element.ELECTRO, 0.40, "Overload bonus");
        assertTransformativeBonus(
                Element.CRYO, Element.ELECTRO, 0.40,
                "Superconduct bonus");

        double baseBurgeon = ReactionCalculator.calculateBurgeon(
                0.0, 90, 0.0).getTransformDamage();
        double boostedBurgeon = ReactionCalculator.calculateBurgeon(
                0.0, 90, 0.40).getTransformDamage();
        assertClose(baseBurgeon * 1.40, boostedBurgeon,
                "Burgeon bonus");
    }

    /** Verifies typed resolver mapping reaches actual Overloaded damage. */
    private static void testCombatResolverReactionBonusRouting() {
        double baseline = overloadDamage(new ArtifactSet(
                "Blank", new StatsContainer()));
        double crimson = overloadDamage(new CrimsonWitchOfFlames());
        assertClose(baseline * 1.40, crimson,
                "Combat resolver Overload bonus routing");
    }

    /** Verifies Crimson Witch's cap, shared refresh, and half-open expiry. */
    private static void testCrimsonWitchSkillStacks() {
        CrimsonWitchOfFlames artifact = new CrimsonWitchOfFlames();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        CharacterActionRequest skill = CharacterActionRequest.of(
                CharacterActionKey.SKILL);

        artifact.onAction(owner, skill, sim);
        assertClose(0.225, pyroBonus(owner, sim),
                "Crimson Witch first Skill stack");
        artifact.onAction(owner, skill, sim);
        artifact.onAction(owner, skill, sim);
        artifact.onAction(owner, skill, sim);
        assertClose(0.375, pyroBonus(owner, sim),
                "Crimson Witch three-stack cap");
        sim.advanceTime(9.0);
        artifact.onAction(owner, skill, sim);
        sim.advanceTime(9.999);
        assertClose(0.375, pyroBonus(owner, sim),
                "Crimson Witch refreshed duration");
        sim.advanceTime(0.001);
        assertClose(0.15, pyroBonus(owner, sim),
                "Crimson Witch exact expiry");

        artifact.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.NORMAL), sim);
        assertClose(0.15, pyroBonus(owner, sim),
                "Non-Skill should not add Crimson Witch stacks");
    }

    /** Verifies snapshot rollback and binding guards for Crimson Witch. */
    private static void testCrimsonWitchSnapshotAndLifecycle() {
        CrimsonWitchOfFlames artifact = new CrimsonWitchOfFlames();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        CharacterActionRequest skill = CharacterActionRequest.of(
                CharacterActionKey.SKILL);
        artifact.onAction(owner, skill, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        artifact.onAction(owner, skill, sim);
        assertClose(0.30, pyroBonus(owner, sim),
                "Crimson Witch mutated snapshot state");
        sim.restoreSnapshot(snapshot);
        assertClose(0.225, pyroBonus(owner, sim),
                "Crimson Witch restored stack state");

        CombatSimulator unrelated = new CombatSimulator();
        artifact.onAction(owner, skill, unrelated);
        assertClose(0.225, pyroBonus(owner, sim),
                "Wrong-simulator Crimson Witch callback");
        boolean rejected = false;
        try {
            artifact.initializeForSimulator(owner, unrelated, true);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected, "Crimson Witch should reject cross-binding");
    }

    /** Verifies Thundering Fury's on-field 0.8-second trigger gate. */
    private static void testThunderingFuryCooldownReduction() {
        ThunderingFury artifact = new ThunderingFury();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        owner.markSkillUsed(0.0);
        ReactionResult overload = reaction(ReactionResult.Kind.OVERLOAD);

        artifact.onReaction(sim, overload, owner, owner);
        assertClose(9.0, owner.getSkillCDRemaining(0.0),
                "Thundering Fury first cooldown reduction");
        sim.advanceTime(0.799);
        artifact.onReaction(sim, overload, owner, owner);
        assertClose(8.201, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury trigger cooldown");
        sim.advanceTime(0.001);
        artifact.onReaction(sim, overload, owner, owner);
        assertClose(7.20, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury exact trigger boundary");

        sim.setActiveCharacter(ally.getCharacterId());
        sim.advanceTime(0.8);
        double before = owner.getSkillCDRemaining(sim.getCurrentTime());
        artifact.onReaction(sim, overload, owner, owner);
        assertClose(before, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Off-field Thundering Fury should not reduce cooldown");
    }

    /** Verifies every listed cooldown reaction plus snapshot and invalid callbacks. */
    private static void testThunderingFuryEligibilityAndSnapshot() {
        ThunderingFury artifact = new ThunderingFury();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter stranger = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, stranger);
        ReactionResult.Kind[] eligibleKinds = {
                ReactionResult.Kind.OVERLOAD,
                ReactionResult.Kind.OVERLOADED,
                ReactionResult.Kind.ELECTRO_CHARGED,
                ReactionResult.Kind.SUPERCONDUCT,
                ReactionResult.Kind.HYPERBLOOM,
                ReactionResult.Kind.AGGRAVATE,
                ReactionResult.Kind.QUICKEN,
                ReactionResult.Kind.LUNAR_CHARGED
        };
        for (ReactionResult.Kind kind : eligibleKinds) {
            owner.removeBuff(mechanics.buff.BuffId
                    .THUNDERING_FURY_4PC_TRIGGER_COOLDOWN);
            owner.markSkillUsed(sim.getCurrentTime());
            artifact.onReaction(sim, reaction(kind), owner, owner);
            assertClose(9.0,
                    owner.getSkillCDRemaining(sim.getCurrentTime()),
                    "Thundering Fury eligibility for " + kind);
        }

        owner.removeBuff(mechanics.buff.BuffId
                .THUNDERING_FURY_4PC_TRIGGER_COOLDOWN);
        owner.markSkillUsed(sim.getCurrentTime());
        artifact.onReaction(sim, reaction(ReactionResult.Kind.BLOOM), owner, owner);
        artifact.onReaction(sim, null, owner, owner);
        artifact.onReaction(sim, reaction(ReactionResult.Kind.OVERLOAD), stranger, owner);
        assertClose(10.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Invalid Thundering Fury callbacks should be inert");

        artifact.onReaction(
                sim, reaction(ReactionResult.Kind.OVERLOAD), owner, owner);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(0.8);
        artifact.onReaction(
                sim, reaction(ReactionResult.Kind.OVERLOAD), owner, owner);
        sim.restoreSnapshot(snapshot);
        assertClose(9.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury restored Skill cooldown");
        artifact.onReaction(
                sim, reaction(ReactionResult.Kind.OVERLOAD), owner, owner);
        assertClose(9.0, owner.getSkillCDRemaining(sim.getCurrentTime()),
                "Thundering Fury restored trigger marker");
    }

    /** Verifies both supplied-stat constructors reject null. */
    private static void testNullStats() {
        assertNullRejected(() -> new CrimsonWitchOfFlames(null),
                "Crimson Witch null stats");
        assertNullRejected(() -> new ThunderingFury(null),
                "Thundering Fury null stats");
    }

    /** Returns one zero-direct-damage Overloaded result from the full resolver. */
    private static double overloadDamage(ArtifactSet artifact) {
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        sim.getEnemy().applyAura(Element.ELECTRO, 2.0, 0.0);
        AttackAction action = new AttackAction(
                "Reaction artifact Overload",
                0.0,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        sim.performAction(owner.getCharacterId(), action);
        return sim.getTotalDamage();
    }

    /** Asserts a 40% bonus reaches one general transformative reaction. */
    private static void assertTransformativeBonus(
            Element trigger,
            Element aura,
            double bonus,
            String message) {
        double baseline = ReactionCalculator.calculate(
                trigger, aura, 0.0, 90, 0.0).getTransformDamage();
        double boosted = ReactionCalculator.calculate(
                trigger, aura, 0.0, 90, bonus).getTransformDamage();
        assertClose(baseline * (1.0 + bonus), boosted, message);
    }

    /** Creates a typed transformative result for artifact callback tests. */
    private static ReactionResult reaction(ReactionResult.Kind kind) {
        return ReactionResult.transform(1.0, kind.name(), kind);
    }

    /** Returns the owner's resolved Pyro DMG bonus. */
    private static double pyroBonus(TestCharacter owner, CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime()).get(
                StatType.PYRO_DMG_BONUS);
    }

    /** Creates one quiet simulator containing the supplied party. */
    private static CombatSimulator simulatorWith(TestCharacter... party) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : party) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Creates a deterministic character with optional artifacts. */
    private static TestCharacter character(
            CharacterId id,
            ArtifactSet... artifacts) {
        return new TestCharacter(id, artifacts);
    }

    /** Asserts a null supplied-stat constructor fails. */
    private static void assertNullRejected(Runnable constructor, String message) {
        try {
            constructor.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    /** Asserts numeric equality within tolerance. */
    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts a condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal deterministic character for artifact lifecycle checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, ArtifactSet... equippedArtifacts) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.ELECTRO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
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
