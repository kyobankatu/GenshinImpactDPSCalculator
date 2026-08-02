package sample;

import java.util.Collections;

import mechanics.formula.DamageCalculator;
import model.artifact.Adventurer;
import model.artifact.Berserker;
import model.artifact.BloodstainedChivalry;
import model.artifact.BraveHeart;
import model.artifact.Gambler;
import model.artifact.LuckyDog;
import model.artifact.MarechausseeHunter;
import model.artifact.ResolutionOfSojourner;
import model.artifact.VourukashasGlow;
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
import simulation.action.AttackAction;

/** Regression checks for combat-static low-rarity artifact sets. */
public class StaticArtifactRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs metadata, isolation, and action-specific CRIT checks. */
    public static void main(String[] args) {
        testAdventurer();
        testLuckyDog();
        testGambler();
        testResolutionOfSojourner();
        testStaticCombatBoundarySets();
        testActionCategorySets();
        testNullStats();
        System.out.println("StaticArtifactRegressionTest passed");
    }

    /** Verifies Adventurer's combat-representable flat HP bonus. */
    private static void testAdventurer() {
        Adventurer fresh = new Adventurer();
        assertEquals("Adventurer", fresh.getName(), "Adventurer name");
        assertClose(1000.0, fresh.getStats().get(StatType.HP_FLAT),
                "Adventurer flat HP");
        assertClose(0.0, fresh.getStats().get(StatType.HEALING_BONUS),
                "Adventurer should not fabricate chest healing");

        StatsContainer supplied = suppliedStats();
        Adventurer preserved = new Adventurer(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Adventurer should retain the supplied container");
        assertClose(7.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                "Adventurer supplied stat preservation");
        assertClose(1000.0, supplied.get(StatType.HP_FLAT),
                "Adventurer supplied flat HP");
    }

    /** Verifies Lucky Dog's combat-representable flat DEF bonus. */
    private static void testLuckyDog() {
        LuckyDog fresh = new LuckyDog();
        assertEquals("Lucky Dog", fresh.getName(), "Lucky Dog name");
        assertClose(100.0, fresh.getStats().get(StatType.DEF_FLAT),
                "Lucky Dog flat DEF");
        assertClose(0.0, fresh.getStats().get(StatType.HEALING_BONUS),
                "Lucky Dog should not fabricate Mora healing");

        StatsContainer supplied = suppliedStats();
        LuckyDog preserved = new LuckyDog(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Lucky Dog should retain the supplied container");
        assertClose(100.0, supplied.get(StatType.DEF_FLAT),
                "Lucky Dog supplied flat DEF");
    }

    /** Verifies Gambler's Skill bonus and inactive defeat reset boundary. */
    private static void testGambler() {
        Gambler fresh = new Gambler();
        assertEquals("Gambler", fresh.getName(), "Gambler name");
        assertClose(0.20, fresh.getStats().get(StatType.SKILL_DMG_BONUS),
                "Gambler Skill DMG");
        assertClose(0.0, fresh.getStats().get(StatType.DMG_BONUS_ALL),
                "Gambler should not add unrelated damage");

        StatsContainer supplied = suppliedStats();
        Gambler preserved = new Gambler(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Gambler should retain the supplied container");
        assertClose(0.20, supplied.get(StatType.SKILL_DMG_BONUS),
                "Gambler supplied Skill DMG");
    }

    /** Verifies Resolution's Charged-only CRIT routing. */
    private static void testResolutionOfSojourner() {
        ResolutionOfSojourner fresh = new ResolutionOfSojourner();
        assertEquals("Resolution of Sojourner", fresh.getName(),
                "Resolution name");
        assertClose(0.18, fresh.getStats().get(StatType.ATK_PERCENT),
                "Resolution ATK");
        assertClose(0.30,
                fresh.getStats().get(StatType.CHARGED_ATTACK_CRIT_RATE),
                "Resolution Charged CRIT Rate");

        CombatSimulator sim = simulatorWith(fresh);
        Character owner = sim.getCharacter(CharacterId.SUCROSE);
        double normalDamage = directDamage(
                sim, owner, ActionType.NORMAL, StatType.NORMAL_ATTACK_DMG_BONUS);
        double chargedDamage = directDamage(
                sim, owner, ActionType.CHARGE, StatType.CHARGED_ATTACK_DMG_BONUS);
        double skillDamage = directDamage(
                sim, owner, ActionType.SKILL, StatType.SKILL_DMG_BONUS);
        double burstDamage = directDamage(
                sim, owner, ActionType.BURST, StatType.BURST_DMG_BONUS);
        assertClose(normalDamage * 1.30, chargedDamage,
                "Resolution Charged-only average CRIT multiplier");
        assertClose(normalDamage, skillDamage,
                "Resolution should not affect Skill CRIT Rate");
        assertClose(normalDamage, burstDamage,
                "Resolution should not affect Burst CRIT Rate");

        assertClose(0.30,
                owner.getEffectiveStats(-10.0).get(
                        StatType.CHARGED_ATTACK_CRIT_RATE),
                "Resolution negative-time stability");
        assertClose(0.30,
                owner.getEffectiveStats(1000.0).get(
                        StatType.CHARGED_ATTACK_CRIT_RATE),
                "Resolution positive-time stability");

        StatsContainer supplied = suppliedStats();
        ResolutionOfSojourner preserved =
                new ResolutionOfSojourner(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Resolution should retain the supplied container");
        assertClose(0.18, supplied.get(StatType.ATK_PERCENT),
                "Resolution supplied ATK");
    }

    /** Verifies exact static values for inactive conditional sets. */
    private static void testStaticCombatBoundarySets() {
        Berserker berserker = new Berserker();
        assertEquals("Berserker", berserker.getName(), "Berserker name");
        assertClose(0.12, berserker.getStats().get(StatType.CRIT_RATE),
                "Berserker CRIT Rate");
        assertClose(0.0,
                berserker.getStats().get(StatType.CHARGED_ATTACK_CRIT_RATE),
                "Berserker should not fabricate low-HP CRIT Rate");

        BraveHeart braveHeart = new BraveHeart();
        assertEquals("Brave Heart", braveHeart.getName(), "Brave Heart name");
        assertClose(0.18, braveHeart.getStats().get(StatType.ATK_PERCENT),
                "Brave Heart ATK");
        assertClose(0.0, braveHeart.getStats().get(StatType.DMG_BONUS_ALL),
                "Brave Heart should not fabricate enemy-HP damage");

        BloodstainedChivalry bloodstained = new BloodstainedChivalry();
        assertEquals("Bloodstained Chivalry", bloodstained.getName(),
                "Bloodstained name");
        assertClose(0.25,
                bloodstained.getStats().get(StatType.PHYSICAL_DMG_BONUS),
                "Bloodstained Physical DMG");
        assertClose(0.0,
                bloodstained.getStats().get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Bloodstained should not fabricate defeat-window damage");

        StatsContainer supplied = suppliedStats();
        Berserker preserved = new Berserker(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Berserker should retain the supplied container");
        assertClose(7.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                "Static set supplied stat preservation");
        assertClose(0.12, supplied.get(StatType.CRIT_RATE),
                "Berserker supplied CRIT Rate");
    }

    /** Verifies Marechaussee and Vourukasha action-category isolation. */
    private static void testActionCategorySets() {
        MarechausseeHunter marechaussee = new MarechausseeHunter();
        assertEquals("Marechaussee Hunter", marechaussee.getName(),
                "Marechaussee name");
        assertClose(0.15,
                marechaussee.getStats().get(StatType.NORMAL_ATTACK_DMG_BONUS),
                "Marechaussee Normal DMG");
        assertClose(0.15,
                marechaussee.getStats().get(StatType.CHARGED_ATTACK_DMG_BONUS),
                "Marechaussee Charged DMG");
        assertClose(0.0, marechaussee.getStats().get(StatType.CRIT_RATE),
                "Marechaussee should not fabricate HP-change CRIT stacks");

        CombatSimulator blankSim = simulatorWith(
                new ArtifactSet("Blank", new StatsContainer()));
        Character blankOwner = blankSim.getCharacter(CharacterId.SUCROSE);
        double baseNormal = directDamage(blankSim, blankOwner,
                ActionType.NORMAL, StatType.NORMAL_ATTACK_DMG_BONUS);
        double baseCharged = directDamage(blankSim, blankOwner,
                ActionType.CHARGE, StatType.CHARGED_ATTACK_DMG_BONUS);
        double baseSkill = directDamage(blankSim, blankOwner,
                ActionType.SKILL, StatType.SKILL_DMG_BONUS);
        double baseBurst = directDamage(blankSim, blankOwner,
                ActionType.BURST, StatType.BURST_DMG_BONUS);

        CombatSimulator marechausseeSim = simulatorWith(marechaussee);
        Character marechausseeOwner = marechausseeSim.getCharacter(
                CharacterId.SUCROSE);
        assertClose(baseNormal * 1.15,
                directDamage(marechausseeSim, marechausseeOwner,
                        ActionType.NORMAL, StatType.NORMAL_ATTACK_DMG_BONUS),
                "Marechaussee Normal damage routing");
        assertClose(baseCharged * 1.15,
                directDamage(marechausseeSim, marechausseeOwner,
                        ActionType.CHARGE, StatType.CHARGED_ATTACK_DMG_BONUS),
                "Marechaussee Charged damage routing");
        assertClose(baseSkill,
                directDamage(marechausseeSim, marechausseeOwner,
                        ActionType.SKILL, StatType.SKILL_DMG_BONUS),
                "Marechaussee Skill isolation");

        VourukashasGlow vourukasha = new VourukashasGlow();
        assertEquals("Vourukasha's Glow", vourukasha.getName(),
                "Vourukasha name");
        assertClose(0.20, vourukasha.getStats().get(StatType.HP_PERCENT),
                "Vourukasha HP");
        assertClose(0.10,
                vourukasha.getStats().get(StatType.SKILL_DMG_BONUS),
                "Vourukasha Skill DMG");
        assertClose(0.10,
                vourukasha.getStats().get(StatType.BURST_DMG_BONUS),
                "Vourukasha Burst DMG");
        CombatSimulator vourukashaSim = simulatorWith(vourukasha);
        Character vourukashaOwner = vourukashaSim.getCharacter(
                CharacterId.SUCROSE);
        assertClose(baseSkill * 1.10,
                directDamage(vourukashaSim, vourukashaOwner,
                        ActionType.SKILL, StatType.SKILL_DMG_BONUS),
                "Vourukasha Skill damage routing");
        assertClose(baseBurst * 1.10,
                directDamage(vourukashaSim, vourukashaOwner,
                        ActionType.BURST, StatType.BURST_DMG_BONUS),
                "Vourukasha Burst damage routing");
        assertClose(baseNormal,
                directDamage(vourukashaSim, vourukashaOwner,
                        ActionType.NORMAL, StatType.NORMAL_ATTACK_DMG_BONUS),
                "Vourukasha Normal isolation");
        assertClose(0.10,
                vourukashaOwner.getEffectiveStats(-1.0).get(
                        StatType.SKILL_DMG_BONUS),
                "Vourukasha negative-time stability");
        assertClose(0.10,
                vourukashaOwner.getEffectiveStats(1000.0).get(
                        StatType.BURST_DMG_BONUS),
                "Vourukasha positive-time stability");

        StatsContainer supplied = suppliedStats();
        VourukashasGlow preserved = new VourukashasGlow(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Vourukasha should retain the supplied container");
    }

    /** Verifies all supplied-stat constructors reject null explicitly. */
    private static void testNullStats() {
        assertNullRejected(() -> new Adventurer(null), "Adventurer null stats");
        assertNullRejected(() -> new LuckyDog(null), "Lucky Dog null stats");
        assertNullRejected(() -> new Gambler(null), "Gambler null stats");
        assertNullRejected(
                () -> new ResolutionOfSojourner(null),
                "Resolution null stats");
        assertNullRejected(() -> new Berserker(null), "Berserker null stats");
        assertNullRejected(() -> new BraveHeart(null), "Brave Heart null stats");
        assertNullRejected(
                () -> new BloodstainedChivalry(null),
                "Bloodstained null stats");
        assertNullRejected(
                () -> new MarechausseeHunter(null),
                "Marechaussee null stats");
        assertNullRejected(
                () -> new VourukashasGlow(null),
                "Vourukasha null stats");
    }

    /** Creates a simulator containing one deterministic artifact wearer. */
    private static CombatSimulator simulatorWith(ArtifactSet artifact) {
        TestCharacter character = new TestCharacter();
        character.setArtifacts(artifact);
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(character);
        return sim;
    }

    /** Calculates one otherwise-identical direct hit for category comparison. */
    private static double directDamage(
            CombatSimulator sim,
            Character owner,
            ActionType actionType,
            StatType bonusStat) {
        AttackAction action = new AttackAction(
                "Artifact " + actionType,
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                Collections.emptyList(),
                0.0,
                1.0,
                sim);
    }

    /** Creates a marker stat container for supplied-state checks. */
    private static StatsContainer suppliedStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ELEMENTAL_MASTERY, 7.0);
        return stats;
    }

    /** Asserts that a constructor rejects null. */
    private static void assertNullRejected(Runnable constructor, String message) {
        try {
            constructor.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    /** Asserts two doubles are equal within the test tolerance. */
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

    /** Asserts a boolean condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal deterministic character used by direct formula checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Static Artifact Tester";
            characterId = CharacterId.SUCROSE;
            element = Element.ANEMO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = new ArtifactSet[0];
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 1.0);
        }

        /** No character-specific passive. */
        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        /** Returns an unused Burst cost for the fixture. */
        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
