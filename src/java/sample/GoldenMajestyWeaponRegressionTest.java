package sample;

import java.util.Collections;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.formula.DamageCalculator;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.GoldenMajestyWeapon;
import model.weapon.MemoryOfDust;
import model.weapon.SummitShaper;
import model.weapon.TheUnforged;
import model.weapon.VortexVanquisher;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionRequest;

/** Regression checks for the four Golden Majesty five-star weapons. */
public final class GoldenMajestyWeaponRegressionTest {
    private static final double EPS = 1e-9;
    private static final double FIXED_ATTACK_PERCENT = 0.496;

    private GoldenMajestyWeaponRegressionTest() {
    }

    /** Runs metadata, trigger, timing, rollback, and lifecycle checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementTables();
        testPostDamageOrderingAndZeroDamageHit();
        testEligibleCategoriesAndFieldGate();
        testCooldownCapRefreshAndExpiry();
        testSnapshotRollback();
        testBindingValidationAndIndependentInstances();
        System.out.println("GoldenMajestyWeaponRegressionTest passed");
    }

    /** Verifies exact shared Lv. 90 metadata and every refinement coefficient. */
    private static void testMetadataAndRefinementTables() {
        assertEquals(5, new SummitShaper().getRefinement(),
                "Summit Shaper default refinement");
        assertEquals(5, new TheUnforged().getRefinement(),
                "The Unforged default refinement");
        assertEquals(5, new MemoryOfDust().getRefinement(),
                "Memory of Dust default refinement");
        assertEquals(5, new VortexVanquisher().getRefinement(),
                "Vortex Vanquisher default refinement");

        String[] names = {
                "Summit Shaper",
                "The Unforged",
                "Memory of Dust",
                "Vortex Vanquisher"
        };
        WeaponType[] types = {
                WeaponType.SWORD,
                WeaponType.CLAYMORE,
                WeaponType.CATALYST,
                WeaponType.POLEARM
        };
        for (int family = 0; family < names.length; family++) {
            for (int refinement = 1; refinement <= 5; refinement++) {
                GoldenMajestyWeapon weapon = weapon(family, refinement);
                assertEquals(names[family], weapon.getName(),
                        "Golden Majesty canonical name");
                assertEquals(types[family], weapon.getWeaponType(),
                        "Golden Majesty weapon type");
                assertEquals(refinement, weapon.getRefinement(),
                        "Golden Majesty refinement");
                assertClose(608.0,
                        weapon.getStats().get(StatType.BASE_ATK),
                        "Golden Majesty base ATK");
                assertClose(FIXED_ATTACK_PERCENT,
                        weapon.getStats().get(StatType.ATK_PERCENT),
                        "Golden Majesty ATK substat");
                assertClose(0.03 + 0.01 * refinement,
                        weapon.getAttackBonusPerStack(),
                        "Golden Majesty stack coefficient R" + refinement);
            }
        }

        assertThrows(() -> new SummitShaper(0), "Summit refinement zero");
        assertThrows(() -> new TheUnforged(6), "Unforged refinement six");
        assertThrows(() -> new MemoryOfDust(0), "Memory refinement zero");
        assertThrows(() -> new VortexVanquisher(6), "Vortex refinement six");
    }

    /** Verifies post-hit ordering and the sourced zero-damage attack behavior. */
    private static void testPostDamageOrderingAndZeroDamageHit() {
        SummitShaper weapon = new SummitShaper(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction normal = attack("Golden Majesty Normal", 1.0,
                ActionType.NORMAL);

        double firstDamage = DamageCalculator.calculateDamage(
                owner, new Enemy(90), normal, Collections.emptyList(),
                sim.getCurrentTime(), 1.0, sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.04,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "Trigger hit should open one stack only after resolving");
        double secondDamage = DamageCalculator.calculateDamage(
                owner, new Enemy(90), normal, Collections.emptyList(),
                sim.getCurrentTime(), 1.0, sim);
        assertTrue(secondDamage > firstDamage,
                "Following hit should receive the first Golden Majesty stack");
        assertClose(FIXED_ATTACK_PERCENT + 0.04,
                effectiveAttackPercent(owner, sim),
                "Immediate second hit should remain behind stack cooldown");

        SummitShaper zeroWeapon = new SummitShaper(1);
        TestCharacter zeroOwner = character(CharacterId.AMBER, zeroWeapon);
        CombatSimulator zeroSim = simulatorWith(zeroOwner);
        DamageCalculator.calculateDamage(
                zeroOwner, new Enemy(90),
                attack("Zero-Damage Attack", 0.0, ActionType.NORMAL),
                Collections.emptyList(), zeroSim.getCurrentTime(), 1.0,
                zeroSim);
        assertClose(FIXED_ATTACK_PERCENT + 0.04,
                effectiveAttackPercent(zeroOwner, zeroSim),
                "Resolved zero-damage attack should gain one stack");
    }

    /** Verifies eligible action categories, classification, and on-field scope. */
    private static void testEligibleCategoriesAndFieldGate() {
        TheUnforged weapon = new TheUnforged(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(
                CharacterId.AMBER, new SummitShaper(1));
        CombatSimulator sim = simulatorWith(owner, ally);

        ActionType[] eligible = {
                ActionType.NORMAL,
                ActionType.CHARGE,
                ActionType.SKILL,
                ActionType.BURST
        };
        for (int i = 0; i < eligible.length; i++) {
            if (i > 0) {
                sim.advanceTime(0.3);
            }
            weapon.onDamage(owner,
                    attack("Eligible", 0.0, eligible[i]),
                    sim.getCurrentTime(), sim);
        }
        assertClose(FIXED_ATTACK_PERCENT + 4.0 * 0.04,
                effectiveAttackPercent(owner, sim),
                "Every sourced direct hit category should gain a stack");

        sim.advanceTime(0.3);
        AttackAction classified = attack(
                "Skill-Classified Follow-Up", 0.0, ActionType.OTHER);
        classified.setCountsAsSkillDmg(true);
        weapon.onDamage(owner, classified, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 5.0 * 0.04,
                effectiveAttackPercent(owner, sim),
                "Skill-classified follow-up should reach stack cap");

        sim.advanceTime(0.3);
        weapon.onDamage(owner,
                attack("Plunge", 0.0, ActionType.PLUNGE),
                sim.getCurrentTime(), sim);
        weapon.onDamage(owner,
                attack("Other", 0.0, ActionType.OTHER),
                sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 5.0 * 0.04,
                effectiveAttackPercent(owner, sim),
                "Unsupported categories should not mutate stack state");

        sim.switchCharacter(ally.getCharacterId());
        sim.advanceTime(0.3);
        double expirationBefore = activeBuffExpiration(
                owner, BuffId.GOLDEN_MAJESTY_ATK_STACKS,
                sim.getCurrentTime());
        weapon.onDamage(owner,
                attack("Off-Field Normal", 0.0, ActionType.NORMAL),
                sim.getCurrentTime(), sim);
        assertClose(expirationBefore, activeBuffExpiration(
                owner, BuffId.GOLDEN_MAJESTY_ATK_STACKS,
                sim.getCurrentTime()),
                "Off-field owner hit should not refresh Golden Majesty");
    }

    /** Verifies half-open cooldown, cap, shared refresh, and exact expiry. */
    private static void testCooldownCapRefreshAndExpiry() {
        MemoryOfDust weapon = new MemoryOfDust(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction normal = attack("Stack Hit", 0.0, ActionType.NORMAL);

        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        sim.advanceTime(0.299);
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.04,
                effectiveAttackPercent(owner, sim),
                "Stack cooldown should remain closed at 0.299 seconds");
        sim.advanceTime(0.001);
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.08,
                effectiveAttackPercent(owner, sim),
                "Stack cooldown should open at exactly 0.300 seconds");

        for (int i = 0; i < 5; i++) {
            sim.advanceTime(0.3);
            weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        }
        assertClose(FIXED_ATTACK_PERCENT + 5.0 * 0.04,
                effectiveAttackPercent(owner, sim),
                "Golden Majesty should cap at five stacks");
        double expiration = activeBuffExpiration(
                owner, BuffId.GOLDEN_MAJESTY_ATK_STACKS,
                sim.getCurrentTime());
        sim.advanceTime(expiration - sim.getCurrentTime() - 0.001);
        assertClose(FIXED_ATTACK_PERCENT + 5.0 * 0.04,
                effectiveAttackPercent(owner, sim),
                "Golden Majesty should remain active before eight seconds");
        sim.advanceTime(expiration - sim.getCurrentTime());
        assertClose(FIXED_ATTACK_PERCENT,
                effectiveAttackPercent(owner, sim),
                "Golden Majesty should expire at exactly eight seconds");
        assertClose(expiration, sim.getCurrentTime(),
                "Golden Majesty expiry timestamp");

        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.04,
                effectiveAttackPercent(owner, sim),
                "Expired stack sequence should restart from one");
    }

    /** Verifies stack and cooldown markers restore with simulator snapshots. */
    private static void testSnapshotRollback() {
        VortexVanquisher weapon = new VortexVanquisher(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction normal = attack("Snapshot Hit", 0.0, ActionType.NORMAL);

        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        sim.advanceTime(0.3);
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(0.3);
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.12,
                effectiveAttackPercent(owner, sim),
                "Divergent Golden Majesty third stack");

        sim.restoreSnapshot(snapshot);
        assertClose(FIXED_ATTACK_PERCENT + 0.08,
                effectiveAttackPercent(owner, sim),
                "Snapshot should restore two Golden Majesty stacks");
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.08,
                effectiveAttackPercent(owner, sim),
                "Snapshot should restore the active stack cooldown");
        sim.advanceTime(0.3);
        weapon.onDamage(owner, normal, sim.getCurrentTime(), sim);
        assertClose(FIXED_ATTACK_PERCENT + 0.12,
                effectiveAttackPercent(owner, sim),
                "Restored cooldown should open at exact boundary");
    }

    /** Verifies lifecycle guards, wrong callbacks, and independent state. */
    private static void testBindingValidationAndIndependentInstances() {
        SummitShaper first = new SummitShaper(1);
        TestCharacter firstOwner = character(CharacterId.SUCROSE, first);
        CombatSimulator firstSim = simulatorWith(firstOwner);
        CombatSimulator wrongSim = new CombatSimulator();
        first.onDamage(firstOwner,
                attack("Wrong Simulator", 0.0, ActionType.NORMAL),
                firstSim.getCurrentTime(), wrongSim);
        assertClose(FIXED_ATTACK_PERCENT,
                effectiveAttackPercent(firstOwner, firstSim),
                "Wrong simulator callback should remain inert");
        assertThrowsState(
                () -> first.initializeForSimulator(firstOwner, wrongSim),
                "Golden Majesty cross-binding");

        SummitShaper second = new SummitShaper(5);
        TestCharacter secondOwner = character(CharacterId.AMBER, second);
        CombatSimulator secondSim = simulatorWith(secondOwner);
        first.onDamage(firstOwner,
                attack("First Instance", 0.0, ActionType.NORMAL),
                firstSim.getCurrentTime(), firstSim);
        assertClose(FIXED_ATTACK_PERCENT,
                effectiveAttackPercent(secondOwner, secondSim),
                "Independent weapon should retain no foreign stacks");
        assertClose(0.08, second.getAttackBonusPerStack(),
                "Independent refinement coefficient");
    }

    /** Creates one family member by compact table index. */
    private static GoldenMajestyWeapon weapon(int family, int refinement) {
        switch (family) {
            case 0:
                return new SummitShaper(refinement);
            case 1:
                return new TheUnforged(refinement);
            case 2:
                return new MemoryOfDust(refinement);
            case 3:
                return new VortexVanquisher(refinement);
            default:
                throw new IllegalArgumentException("Unknown weapon family index");
        }
    }

    /** Creates a direct attack fixture with the selected action category. */
    private static AttackAction attack(
            String name,
            double multiplier,
            ActionType actionType) {
        return new AttackAction(
                name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                actionType);
    }

    /** Creates a minimal character carrying the selected weapon. */
    private static TestCharacter character(CharacterId id, Weapon weapon) {
        return new TestCharacter(id, weapon);
    }

    /** Creates a quiet simulator and preserves character insertion order. */
    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Returns the owner's effective ATK% at the simulator's current time. */
    private static double effectiveAttackPercent(
            Character owner,
            CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime())
                .get(StatType.ATK_PERCENT);
    }

    /** Returns one active typed buff's expiry, or negative infinity. */
    private static double activeBuffExpiration(
            Character owner,
            BuffId id,
            double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return buff.getExpirationTime();
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** Asserts that invalid refinement input is rejected. */
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(
                    message + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    /** Asserts that cross-simulator reuse is rejected. */
    private static void assertThrowsState(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(
                    message + ": expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    /** Asserts numeric equality within tolerance. */
    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
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

    /** Minimal action-inert owner used to exercise weapon callbacks directly. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.PHYSICAL;
            weapon = equippedWeapon;
            artifacts = new ArtifactSet[] {
                    new ArtifactSet("Blank", new StatsContainer())
            };
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
            // Weapon-only fixture.
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public void onAction(
                CharacterActionRequest request,
                CombatSimulator sim) {
            // Weapon hooks are exercised through resolved damage fixtures.
        }
    }
}
