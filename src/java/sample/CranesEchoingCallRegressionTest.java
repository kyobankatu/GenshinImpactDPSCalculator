package sample;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.CranesEchoingCall;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Crane's Echoing Call hit, buff, Energy, and rollback rules. */
public final class CranesEchoingCallRegressionTest {
    private CranesEchoingCallRegressionTest() {
    }

    /** Runs metadata, refinement, trigger, boundary, restore, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testOwnerHitOpensPartyBuff();
        testOffFieldEnergyAndCooldownBoundary();
        testAbnormalEventsDoNotTrigger();
        testSnapshotRestoreAndBindingIsolation();
        System.out.println("CranesEchoingCallRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new CranesEchoingCall().getRefinement(),
                "Crane default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            CranesEchoingCall weapon = new CranesEchoingCall(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.CATALYST, weapon.getWeaponType(),
                    "Crane weapon type R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    741.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Crane base ATK R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.165, weapon.getStats().get(StatType.ATK_PERCENT),
                    "Crane ATK substat R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.15 + 0.13 * refinement,
                    weapon.getPlungingDamageBonus(),
                    "Crane Plunging bonus R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    2.25 + 0.25 * refinement,
                    weapon.getEnergyRecovery(),
                    "Crane Energy R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new CranesEchoingCall(0),
                "Crane rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new CranesEchoingCall(6),
                "Crane rejects R6");
    }

    private static void testOwnerHitOpensPartyBuff() {
        CranesEchoingCall weapon = new CranesEchoingCall(1);
        EnergyTestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        EnergyTestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);

        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(), hit("Owner Plunge", ActionType.PLUNGE));

        StatefulWeaponRegressionSupport.assertClose(
                2.5, owner.getCurrentEnergy(),
                "Crane owner Plunge restores Energy");
        StatefulWeaponRegressionSupport.assertClose(
                0.28,
                resolvedStats(ally, sim, 0.0)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Crane owner hit opens ally Plunging bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.28,
                resolvedStats(owner, sim, 19.999)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Crane buff remains active before 20 seconds");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 20.0)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Crane buff uses a half-open 20-second window");
    }

    private static void testOffFieldEnergyAndCooldownBoundary() {
        CranesEchoingCall weapon = new CranesEchoingCall(1);
        EnergyTestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        EnergyTestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        CombatSimulator sim = simulatorWith(ally, owner);
        owner.restoreCurrentEnergy(0.0);
        AttackAction allyPlunge = hit("Ally Plunge", ActionType.PLUNGE);

        sim.performActionWithoutTimeAdvance(ally.getCharacterId(), allyPlunge);
        StatefulWeaponRegressionSupport.assertClose(
                2.5, owner.getCurrentEnergy(),
                "Crane restores Energy while owner is off-field");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 0.0)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Ally Plunge does not open Crane team buff");

        sim.advanceTime(0.699);
        sim.performActionWithoutTimeAdvance(ally.getCharacterId(), allyPlunge);
        StatefulWeaponRegressionSupport.assertClose(
                2.5, owner.getCurrentEnergy(),
                "Crane rejects Energy before 0.7-second boundary");
        sim.advanceTime(0.001);
        sim.performActionWithoutTimeAdvance(ally.getCharacterId(), allyPlunge);
        StatefulWeaponRegressionSupport.assertClose(
                5.0, owner.getCurrentEnergy(),
                "Crane allows Energy at 0.7-second boundary");
    }

    private static void testAbnormalEventsDoNotTrigger() {
        CranesEchoingCall weapon = new CranesEchoingCall(5);
        EnergyTestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        EnergyTestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        EnergyTestCharacter outsider = character(
                CharacterId.RAZOR, Element.ELECTRO, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);

        weapon.onDamage(outsider, hit("Outsider", ActionType.PLUNGE), 100.0, 0.0);
        weapon.onDamage(owner, null, 100.0, 0.0);
        weapon.onDamage(owner, hit("Zero damage", ActionType.PLUNGE), 0.0, 0.0);
        weapon.onDamage(owner, hit("Wrong type", ActionType.NORMAL), 100.0, 0.0);
        AttackAction falseHit = hit("False hit", ActionType.PLUNGE);
        falseHit.setHitEffectTrigger(false);
        weapon.onDamage(owner, falseHit, 100.0, 0.0);
        weapon.onDamage(owner, zeroMotionHit(), 100.0, 0.0);

        StatefulWeaponRegressionSupport.assertClose(
                0.0, owner.getCurrentEnergy(),
                "Crane abnormal events do not restore Energy");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 0.0)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Crane abnormal events do not open buff");
    }

    private static void testSnapshotRestoreAndBindingIsolation() {
        CranesEchoingCall weapon = new CranesEchoingCall(1);
        EnergyTestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        EnergyTestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        SimulatorSnapshot beforeHit = sim.saveSnapshot();

        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(), hit("Snapshot Plunge", ActionType.PLUNGE));
        sim.restoreSnapshot(beforeHit);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, owner.getCurrentEnergy(),
                "Crane rollback restores owner Energy");
        StatefulWeaponRegressionSupport.assertClose(
                0.0,
                resolvedStats(ally, sim, 0.0)
                        .get(StatType.PLUNGING_ATTACK_DMG_BONUS),
                "Crane rollback closes team window");

        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(), hit("Post-Restore Plunge", ActionType.PLUNGE));
        StatefulWeaponRegressionSupport.assertClose(
                2.5, owner.getCurrentEnergy(),
                "Crane rollback restores Energy ICD readiness");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new CranesEchoingCall(1).restoreWeaponState(state),
                "Crane rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Crane rejects cross-simulator reuse");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new CranesEchoingCall(1)
                        .initializeForSimulator(null, new CombatSimulator()),
                "Crane rejects null owner");
        EnergyTestCharacter wrongOwner = character(
                CharacterId.RAZOR, Element.ELECTRO, null);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new CranesEchoingCall(1)
                        .initializeForSimulator(wrongOwner, new CombatSimulator()),
                "Crane rejects owner without weapon instance equipped");
        CranesEchoingCall unboundWeapon = new CranesEchoingCall(1);
        EnergyTestCharacter unboundOwner = character(
                CharacterId.SUCROSE, Element.ANEMO, unboundWeapon);
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> unboundWeapon.initializeForSimulator(
                        unboundOwner, new CombatSimulator()),
                "Crane rejects owner outside simulator party");
    }

    private static EnergyTestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new EnergyTestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulatorWith(EnergyTestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new model.entity.Enemy(90));
        for (EnergyTestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static AttackAction hit(String name, ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.ANEMO,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static AttackAction zeroMotionHit() {
        AttackAction action = new AttackAction(
                "Zero motion",
                0.0,
                Element.ANEMO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.PLUNGE);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static StatsContainer resolvedStats(
            Character character,
            CombatSimulator sim,
            double currentTime) {
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(character)) {
            buff.apply(stats, currentTime);
        }
        return stats;
    }

    /** Minimal deterministic character with a nonzero Energy cap. */
    private static final class EnergyTestCharacter extends Character {
        private EnergyTestCharacter(
                CharacterId id,
                Element characterElement,
                Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = equippedWeapon;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
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
