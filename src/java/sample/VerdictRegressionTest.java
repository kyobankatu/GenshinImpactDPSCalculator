package sample;

import mechanics.reaction.ReactionResult;
import model.entity.SnapshotAwareWeaponEffect;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.Verdict;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused metadata, Seal timing, isolation, snapshot, and guard checks. */
public final class VerdictRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final ReactionResult LUNAR_CRYSTALLIZE =
            ReactionResult.lunar(
                    0.0,
                    ReactionResult.LunarType.CRYSTALLIZE);
    private static final ReactionResult ORDINARY_CRYSTALLIZE =
            new ReactionResult(
                    ReactionResult.Type.TRANSFORMATIVE,
                    1.0,
                    0.0,
                    "Crystallize",
                    ReactionResult.Kind.CRYSTALLIZE);

    private VerdictRegressionTest() {
    }

    /** Runs metadata, passive, Seal, rollback, and validation regressions. */
    public static void main(String[] args) {
        testMetadataAndRefinementTable();
        testPermanentAttackIsolation();
        testPartySealGainCapCooldownAndExpiry();
        testSkillConsumptionWindow();
        testSnapshotRollbackAndIndependentInstances();
        testOwnerSimulatorAndStateGuards();
        System.out.println("VerdictRegressionTest passed");
    }

    private static void testMetadataAndRefinementTable() {
        Verdict defaultWeapon = new Verdict();
        StatefulWeaponRegressionSupport.assertEquals(
                "Verdict", defaultWeapon.getName(), "Verdict display name");
        StatefulWeaponRegressionSupport.assertEquals(
                WeaponType.CLAYMORE,
                defaultWeapon.getWeaponType(),
                "Verdict weapon type");
        StatefulWeaponRegressionSupport.assertEquals(
                5, defaultWeapon.getRefinement(), "Verdict default refinement");
        StatefulWeaponRegressionSupport.assertClose(
                674.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Verdict base ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.221,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Verdict CRIT Rate");

        for (int refinement = 1; refinement <= 5; refinement++) {
            Verdict weapon = new Verdict(refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15 + 0.05 * refinement,
                    weapon.getAttackBonus(),
                    "Verdict permanent ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.135 + 0.045 * refinement,
                    weapon.getSkillDamageBonusPerSeal(),
                    "Verdict Seal Skill bonus R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new Verdict(0),
                "Verdict rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new Verdict(6),
                "Verdict rejects R6");
    }

    private static void testPermanentAttackIsolation() {
        Verdict weapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);

        StatefulWeaponRegressionSupport.assertClose(
                0.20,
                StatefulWeaponRegressionSupport.stats(owner, simulator)
                        .get(StatType.ATK_PERCENT),
                "Verdict applies permanent ATK");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                StatefulWeaponRegressionSupport.stats(owner, simulator)
                        .get(StatType.SKILL_DMG_BONUS),
                "Verdict has no Skill bonus without Seals");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                StatefulWeaponRegressionSupport.stats(owner, simulator)
                        .get(StatType.BURST_DMG_BONUS),
                "Verdict permanent passive excludes Burst DMG");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                StatefulWeaponRegressionSupport.stats(owner, simulator)
                        .get(StatType.DMG_BONUS_ALL),
                "Verdict permanent passive excludes generic DMG");
    }

    private static void testPartySealGainCapCooldownAndExpiry() {
        Verdict weapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        simulator.setActiveCharacter(CharacterId.AMBER);

        simulator.notifyReaction(ORDINARY_CRYSTALLIZE, ally);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(0.0),
                "Ordinary Crystallize without shard pickup grants no Seal");
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, ally);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(0.0),
                "Off-field Verdict gains a Seal from a party Lunar-Crystallize");
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(0.0),
                "Verdict enforces the shared one-second gain cooldown");

        simulator.advanceTime(1.0 - EPSILON);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, ally);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Verdict rejects Seal gain immediately before one second");
        simulator.advanceTime(EPSILON);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);
        StatefulWeaponRegressionSupport.assertEquals(
                2,
                weapon.getSealCount(1.0),
                "Verdict gains a second Seal at exactly one second");
        StatefulWeaponRegressionSupport.assertClose(
                16.0,
                weapon.getSealsExpireAt(),
                "Second Seal refreshes the shared expiration");

        simulator.advanceTime(1.0);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, ally);
        StatefulWeaponRegressionSupport.assertEquals(
                2,
                weapon.getSealCount(2.0),
                "Verdict caps at two Seals");
        StatefulWeaponRegressionSupport.assertClose(
                17.0,
                weapon.getSealsExpireAt(),
                "Successful capped gain refreshes shared expiration");
        StatefulWeaponRegressionSupport.assertEquals(
                2,
                weapon.getSealCount(17.0 - EPSILON),
                "Verdict Seals remain before the half-open expiry");
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(17.0),
                "Verdict Seals expire at exactly fifteen seconds after refresh");
    }

    private static void testSkillConsumptionWindow() {
        Verdict baselineWeapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter baselineOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, baselineWeapon);
        CombatSimulator baselineSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(baselineOwner);
        AttackAction skill = skillHit("Verdict Skill");
        double baselineDamage = StatefulWeaponRegressionSupport.calculate(
                baselineOwner, skill, baselineSimulator);

        Verdict weapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);
        simulator.advanceTime(1.0);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);

        double firstSkillDamage = StatefulWeaponRegressionSupport.calculate(
                owner, skill, simulator);
        StatefulWeaponRegressionSupport.assertClose(
                baselineDamage * 1.36,
                firstSkillDamage,
                "Two R1 Seals affect the Skill hit that starts consumption");
        simulator.advanceTime(0.2 - EPSILON);
        double windowSkillDamage = StatefulWeaponRegressionSupport.calculate(
                owner, skill, simulator);
        StatefulWeaponRegressionSupport.assertClose(
                baselineDamage * 1.36,
                windowSkillDamage,
                "Skill hits inside the 0.2-second window retain both Seals");
        simulator.advanceTime(EPSILON);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Verdict consumes every Seal at exactly 0.2 seconds");
        StatefulWeaponRegressionSupport.assertClose(
                baselineDamage,
                StatefulWeaponRegressionSupport.calculate(
                        owner, skill, simulator),
                "Post-consumption Skill damage returns to baseline");

        simulator.advanceTime(0.8);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);
        AttackAction burst = StatefulWeaponRegressionSupport.hit(
                "Verdict Burst", ActionType.BURST);
        StatefulWeaponRegressionSupport.calculate(owner, burst, simulator);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Burst damage neither receives nor consumes Verdict Seals");
    }

    private static void testSnapshotRollbackAndIndependentInstances() {
        Verdict weapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, weapon);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner);
        simulator.notifyReaction(LUNAR_CRYSTALLIZE, owner);
        AttackAction skill = skillHit("Snapshot Skill");
        StatefulWeaponRegressionSupport.calculate(owner, skill, simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();

        simulator.advanceTime(0.2);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Verdict live state consumes before rollback");
        simulator.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Verdict rollback restores the Seal");
        simulator.advanceTime(0.2 - EPSILON);
        StatefulWeaponRegressionSupport.assertEquals(
                1,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Verdict rollback restores pending consumption timing");
        simulator.advanceTime(EPSILON);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(simulator.getCurrentTime()),
                "Restored consumption fires at its exact boundary");

        Verdict independent = new Verdict(1);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                independent.getSealCount(0.0),
                "Verdict instances keep independent Seal state");
    }

    private static void testOwnerSimulatorAndStateGuards() {
        Verdict weapon = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, weapon);
        StatefulWeaponRegressionSupport.TestCharacter ally =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.AMBER, null);
        StatefulWeaponRegressionSupport.TestCharacter outsider =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.SUCROSE, null);
        CombatSimulator simulator =
                StatefulWeaponRegressionSupport.simulatorWith(owner, ally);
        CombatSimulator wrongSimulator = new CombatSimulator();

        weapon.onElementalReaction(
                LUNAR_CRYSTALLIZE, outsider, 0.0, simulator);
        weapon.onElementalReaction(
                LUNAR_CRYSTALLIZE, ally, 0.0, wrongSimulator);
        weapon.onElementalReaction(null, ally, 0.0, simulator);
        weapon.onElementalReaction(
                LUNAR_CRYSTALLIZE, null, 0.0, simulator);
        weapon.onDamage(
                ally,
                StatefulWeaponRegressionSupport.hit(
                        "Foreign Skill", ActionType.SKILL),
                0.0,
                simulator);
        weapon.onDamage(
                owner,
                StatefulWeaponRegressionSupport.hit(
                        "Wrong Simulator Skill", ActionType.SKILL),
                0.0,
                wrongSimulator);
        weapon.onDamage(owner, null, 0.0, simulator);
        StatefulWeaponRegressionSupport.assertEquals(
                0,
                weapon.getSealCount(0.0),
                "Verdict rejects foreign and null callbacks");

        Verdict unequipped = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter bare =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, null);
        CombatSimulator bareSimulator =
                StatefulWeaponRegressionSupport.simulatorWith(bare);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Verdict rejects unequipped binding");

        Verdict absent = new Verdict(1);
        StatefulWeaponRegressionSupport.TestCharacter absentOwner =
                StatefulWeaponRegressionSupport.character(
                        CharacterId.RAZOR, absent);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> absent.initializeForSimulator(
                        absentOwner, new CombatSimulator()),
                "Verdict rejects an owner outside the simulator party");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> absent.initializeForSimulator(null, simulator),
                "Verdict rejects null owner binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> absent.initializeForSimulator(absentOwner, null),
                "Verdict rejects null simulator binding");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, wrongSimulator),
                "Verdict rejects cross-simulator reuse");

        SnapshotAwareWeaponEffect.State foreignState =
                new Verdict(1).captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreignState),
                "Verdict rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Verdict rejects foreign state type");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Verdict rejects null state");
    }

    private static AttackAction skillHit(String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                model.type.Element.PYRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
