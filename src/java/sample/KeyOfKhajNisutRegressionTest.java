package sample;

import java.util.List;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.formula.DamageCalculator;
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
import model.weapon.KeyOfKhajNisut;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Key of Khaj-Nisut's Grand Hymn state. */
public final class KeyOfKhajNisutRegressionTest {
    private static final double EPS = 1e-8;
    private static final double OWNER_BASE_HP = 10000.0;

    private KeyOfKhajNisutRegressionTest() {
    }

    /** Runs metadata, trigger, lifecycle, snapshot, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPostHitOrderingAndThreeStackTeamBuff();
        testCooldownSharedRefreshAndCurrentMaxHp();
        testTriggerRejectionsAndOffFieldRetention();
        testSnapshotRollbackAndStrictRestore();
        testIndependentInstancesAndBindingValidation();
        System.out.println("KeyOfKhajNisutRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new KeyOfKhajNisut().getRefinement(),
                "Key default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            KeyOfKhajNisut weapon = new KeyOfKhajNisut(refinement);
            assertEquals("Key of Khaj-Nisut", weapon.getName(), "Key name");
            assertEquals(WeaponType.SWORD, weapon.getWeaponType(), "Key type");
            assertEquals(refinement, weapon.getRefinement(), "Key refinement");
            assertClose(542.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Key base ATK");
            assertClose(0.15 + 0.05 * refinement, weapon.getHpBonus(),
                    "Key passive HP R" + refinement);
            assertClose(0.662 + weapon.getHpBonus(),
                    weapon.getStats().get(StatType.HP_PERCENT),
                    "Key total weapon HP R" + refinement);
            assertClose(0.0009 + 0.0003 * refinement,
                    weapon.getOwnerEmRatio(),
                    "Key owner EM ratio R" + refinement);
            assertClose(0.0015 + 0.0005 * refinement,
                    weapon.getTeamEmRatio(),
                    "Key team EM ratio R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new KeyOfKhajNisut(0), "Key refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new KeyOfKhajNisut(6), "Key refinement six");
    }

    private static void testPostHitOrderingAndThreeStackTeamBuff() {
        KeyOfKhajNisut weapon = new KeyOfKhajNisut(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction skill = skillHit("Grand Hymn Skill", 1.0);

        double firstDamage = calculate(owner, skill, sim);
        assertClose(288.9, firstDamage,
                "Key triggering hit resolves before first EM stack");
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Key first post-hit stack");
        sim.advanceTime(0.3);
        double secondDamage = calculate(owner, skill, sim);
        assertTrue(secondDamage > firstDamage,
                "Key following Skill hit sees the prior EM stack");
        sim.advanceTime(0.3);
        calculate(owner, skill, sim);

        double maxHp = maxHp(owner, sim);
        double ownerOnly = maxHp * weapon.getOwnerEmRatio() * 3.0;
        double team = maxHp * weapon.getTeamEmRatio();
        assertClose(ownerOnly + team, elementalMastery(owner, sim),
                "Key owner receives owner and team EM portions");
        assertClose(team, elementalMastery(ally, sim),
                "Key ally receives team EM portion");
        assertEquals(1L, sim.getApplicableBuffs(owner).stream()
                        .filter(buff -> buff.getId()
                                == BuffId.KEY_OF_KHAJ_NISUT_TEAM_EM)
                        .count(),
                "Key typed team buff appears once");
    }

    private static void testCooldownSharedRefreshAndCurrentMaxHp() {
        KeyOfKhajNisut weapon = new KeyOfKhajNisut(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction skill = skillHit("Boundary Skill", 1.0);

        weapon.onDamage(owner, skill, 0.0, sim);
        sim.advanceTime(0.299);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Key pre-0.3 cooldown rejection");
        sim.advanceTime(0.001);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertEquals(2, weapon.getStackCount(sim.getCurrentTime()),
                "Key exact 0.3 cooldown acceptance");

        owner.addBuff(new SimpleBuff(
                "Late Max HP",
                100.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.HP_PERCENT, 1.0)));
        sim.advanceTime(0.3);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        double maxHp = maxHp(owner, sim);
        double ownerOnly = maxHp * weapon.getOwnerEmRatio() * 3.0;
        double team = maxHp * weapon.getTeamEmRatio();
        assertClose(ownerOnly + team, elementalMastery(owner, sim),
                "Key third stack recalculates all owner EM from current Max HP");

        sim.advanceTime(0.3);
        owner.getBaseStats().set(StatType.HP_FLAT, 1000.0);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        double refreshedMaxHp = maxHp(owner, sim);
        assertClose(refreshedMaxHp * weapon.getOwnerEmRatio() * 3.0
                        + refreshedMaxHp * weapon.getTeamEmRatio(),
                elementalMastery(owner, sim),
                "Key cap refresh recalculates owner and team values");

        sim.advanceTime(19.999);
        assertEquals(3, weapon.getStackCount(sim.getCurrentTime()),
                "Key shared stacks survive before refreshed expiry");
        assertTrue(elementalMastery(ally, sim) > 0.0,
                "Key team buff survives before refreshed expiry");
        sim.advanceTime(0.001);
        assertEquals(0, weapon.getStackCount(sim.getCurrentTime()),
                "Key shared stacks expire at exact boundary");
        assertClose(0.0, elementalMastery(ally, sim),
                "Key team buff expires at exact boundary");
    }

    private static void testTriggerRejectionsAndOffFieldRetention() {
        KeyOfKhajNisut weapon = new KeyOfKhajNisut(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction skill = skillHit("Eligible Skill", 1.0);

        weapon.onDamage(ally, skill, 0.0, sim);
        weapon.onDamage(owner, hit("Normal", 1.0, ActionType.NORMAL), 0.0, sim);
        weapon.onDamage(owner, null, 0.0, sim);
        weapon.onDamage(owner, skill, 0.0, new CombatSimulator());
        AttackAction dummy = new AttackAction(
                "Dummy Skill",
                0.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.SKILL);
        weapon.onDamage(owner, dummy, 0.0, sim);
        assertEquals(0, weapon.getStackCount(0.0),
                "Key rejects wrong source/type/simulator and dummy casts");

        weapon.onDamage(owner, skill, 0.0, sim);
        sim.setActiveCharacter(CharacterId.AMBER);
        sim.advanceTime(0.3);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertEquals(1, weapon.getStackCount(sim.getCurrentTime()),
                "Key cannot gain stacks off-field");
        assertTrue(elementalMastery(owner, sim) > 0.0,
                "Key owner stack persists off-field");

        sim.setActiveCharacter(CharacterId.SUCROSE);
        AttackAction classified = hit(
                "Skill-Classified Follow-Up", 1.0, ActionType.OTHER);
        classified.setCountsAsSkillDmg(true);
        weapon.onDamage(owner, classified, sim.getCurrentTime(), sim);
        assertEquals(2, weapon.getStackCount(sim.getCurrentTime()),
                "Key accepts explicit Skill-damage classification");
    }

    private static void testSnapshotRollbackAndStrictRestore() {
        KeyOfKhajNisut weapon = new KeyOfKhajNisut(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction skill = skillHit("Snapshot Skill", 1.0);
        acquireThreeStacks(weapon, owner, skill, sim);
        double ownerEm = elementalMastery(owner, sim);
        double allyEm = elementalMastery(ally, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(5.0);
        owner.getBaseStats().set(StatType.HP_FLAT, 5000.0);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        assertTrue(elementalMastery(owner, sim) > ownerEm,
                "Key state changes before rollback");
        sim.restoreSnapshot(snapshot);
        owner.getBaseStats().set(StatType.HP_FLAT, 0.0);
        assertClose(ownerEm, elementalMastery(owner, sim),
                "Key rollback restores owner EM state");
        assertClose(allyEm, elementalMastery(ally, sim),
                "Key rollback restores typed team buff");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        KeyOfKhajNisut other = new KeyOfKhajNisut(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Key rejects another weapon's state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Key rejects foreign state type");
    }

    private static void testIndependentInstancesAndBindingValidation() {
        KeyOfKhajNisut first = new KeyOfKhajNisut(1);
        KeyOfKhajNisut second = new KeyOfKhajNisut(5);
        TestCharacter firstOwner = character(CharacterId.SUCROSE, first);
        TestCharacter secondOwner = character(CharacterId.AMBER, second);
        CombatSimulator firstSim = simulatorWith(firstOwner);
        CombatSimulator secondSim = simulatorWith(secondOwner);
        first.onDamage(firstOwner, skillHit("First Skill", 1.0), 0.0, firstSim);
        assertEquals(1, first.getStackCount(0.0), "Key first instance stack");
        assertEquals(0, second.getStackCount(0.0),
                "Key second instance remains independent");

        assertThrows(IllegalStateException.class,
                () -> first.initializeForSimulator(firstOwner, secondSim),
                "Key rejects cross-simulator binding");
        assertThrows(IllegalArgumentException.class,
                () -> new KeyOfKhajNisut(1)
                        .initializeForSimulator(firstOwner, firstSim),
                "Key rejects owner without this weapon equipped");
        assertThrows(IllegalArgumentException.class,
                () -> new KeyOfKhajNisut(1)
                        .initializeForSimulator(null, firstSim),
                "Key rejects null owner");
    }

    private static void acquireThreeStacks(
            KeyOfKhajNisut weapon,
            TestCharacter owner,
            AttackAction skill,
            CombatSimulator sim) {
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        sim.advanceTime(0.3);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
        sim.advanceTime(0.3);
        weapon.onDamage(owner, skill, sim.getCurrentTime(), sim);
    }

    private static double calculate(
            TestCharacter owner,
            AttackAction action,
            CombatSimulator sim) {
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                sim.getApplicableBuffs(owner),
                sim.getCurrentTime(),
                1.0,
                sim);
    }

    private static double maxHp(
            TestCharacter character,
            CombatSimulator sim) {
        return resolvedStats(character, sim).getTotalHp();
    }

    private static double elementalMastery(
            TestCharacter character,
            CombatSimulator sim) {
        return resolvedStats(character, sim).get(StatType.ELEMENTAL_MASTERY);
    }

    private static StatsContainer resolvedStats(
            TestCharacter character,
            CombatSimulator sim) {
        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        sim.getApplicableBuffs(character).stream()
                .filter(buff -> !buff.isExpired(sim.getCurrentTime()))
                .forEach(buff -> buff.apply(stats, sim.getCurrentTime()));
        return stats;
    }

    private static AttackAction skillHit(String name, double damagePercent) {
        return hit(name, damagePercent, ActionType.SKILL);
    }

    private static AttackAction hit(
            String name,
            double damagePercent,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                damagePercent,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static TestCharacter character(
            CharacterId characterId,
            Weapon weapon) {
        return new TestCharacter(characterId, weapon);
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPS) {
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
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
    }

    /** Minimal owner/ally with a Skill EM conversion for ordering checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId characterId, Weapon weapon) {
            name = characterId.getDisplayName();
            this.characterId = characterId;
            element = characterId == CharacterId.AMBER
                    ? Element.PYRO : Element.ANEMO;
            this.weapon = weapon;
            baseStats.set(StatType.BASE_HP, OWNER_BASE_HP);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 0.0);
            baseStats.set(
                    StatType.ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO,
                    1.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
