package sample;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.LumidouceElegy;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Lumidouce Elegy's Burning stack state. */
public final class LumidouceElegyRegressionTest {
    private static final ReactionResult BURNING = ReactionResult.state(
            "Burning", ReactionResult.Kind.BURNING, Element.DENDRO);

    private LumidouceElegyRegressionTest() {
    }

    /** Runs metadata, trigger, boundary, rollback, and binding checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testActualBurningAndSameHitDeduplication();
        testDendroBurningPathAndEnergyBoundaries();
        testTriggerRejectionsAndOffFieldBehavior();
        testSnapshotAndBindingValidation();
        System.out.println("LumidouceElegyRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        StatefulWeaponRegressionSupport.assertEquals(
                5, new LumidouceElegy().getRefinement(),
                "Lumidouce default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            LumidouceElegy weapon = new LumidouceElegy(refinement);
            StatefulWeaponRegressionSupport.assertEquals(
                    WeaponType.POLEARM, weapon.getWeaponType(),
                    "Lumidouce weapon type");
            StatefulWeaponRegressionSupport.assertClose(
                    608.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Lumidouce base ATK");
            StatefulWeaponRegressionSupport.assertClose(
                    0.331, weapon.getStats().get(StatType.CRIT_RATE),
                    "Lumidouce CRIT Rate");
            StatefulWeaponRegressionSupport.assertClose(
                    0.11 + 0.04 * refinement,
                    weapon.getAttackBonus(),
                    "Lumidouce ATK coefficient R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    0.13 + 0.05 * refinement,
                    weapon.getDamageBonusPerStack(),
                    "Lumidouce damage coefficient R" + refinement);
            StatefulWeaponRegressionSupport.assertClose(
                    11.0 + refinement,
                    weapon.getEnergyRecovery(),
                    "Lumidouce Energy coefficient R" + refinement);
        }
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LumidouceElegy(0),
                "Lumidouce rejects R0");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LumidouceElegy(6),
                "Lumidouce rejects R6");
    }

    private static void testActualBurningAndSameHitDeduplication() {
        LumidouceElegy weapon = new LumidouceElegy(1);
        EnergyCharacter owner = new EnergyCharacter(
                CharacterId.XIANGLING, Element.DENDRO, weapon);
        CombatSimulator sim = simulatorWith(owner);
        owner.spendEnergy(owner.getMaxEnergy());
        sim.getEnemy().setAura(Element.PYRO, 2.0, sim.getCurrentTime());

        AttackAction dendro = hit("Lumidouce Burning hit", Element.DENDRO);
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), dendro);
        StatefulWeaponRegressionSupport.assertTrue(
                sim.isBurningActive(),
                "Lumidouce fixture starts Burning");
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce reaction and direct callback deduplicate one hit");
        StatefulWeaponRegressionSupport.assertClose(
                0.18,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .get(StatType.DMG_BONUS_ALL),
                "Lumidouce one-stack damage bonus");
        StatefulWeaponRegressionSupport.assertClose(
                0.15,
                owner.getEffectiveStats(sim.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "Lumidouce permanent ATK bonus");

        owner.getBaseStats().set(StatType.FLAT_DMG_BONUS, 100.0);
        AttackAction zeroMotion = new AttackAction(
                "Lumidouce same-time zero-motion hit",
                0.0,
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.DENDRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        zeroMotion.setHitEffectTrigger(true);
        sim.performActionWithoutTimeAdvance(owner.getCharacterId(), zeroMotion);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce counts a distinct same-time additive-damage hit");
        StatefulWeaponRegressionSupport.assertClose(
                12.0, owner.getCurrentEnergy(),
                "Lumidouce zero-motion additive hit reaches Energy threshold");

        sim.notifyDerivedReaction(BURNING, owner);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce ignores derived Burning notifications");
    }

    private static void testDendroBurningPathAndEnergyBoundaries() {
        LumidouceElegy weapon = new LumidouceElegy(1);
        EnergyCharacter owner = new EnergyCharacter(
                CharacterId.XIANGLING, Element.DENDRO, weapon);
        CombatSimulator sim = simulatorWith(owner);
        owner.spendEnergy(owner.getMaxEnergy());
        sim.startBurning(owner.getCharacterId(), 1000.0, 20.0, 0.1);
        AttackAction dendro = hit("Lumidouce Dendro", Element.DENDRO);

        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        sim.advanceTime(1.0);
        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce Dendro hits reach stack cap");
        StatefulWeaponRegressionSupport.assertClose(
                12.0, owner.getCurrentEnergy(),
                "Lumidouce restores Energy at two stacks");

        owner.spendEnergy(owner.getCurrentEnergy());
        sim.advanceTime(1.0);
        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertClose(
                0.0, owner.getCurrentEnergy(),
                "Lumidouce Energy ICD blocks a two-stack refresh");

        sim.advanceTime(7.999);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce shared stack duration is half-open before expiry");
        sim.advanceTime(0.001);
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(10.0),
                "Lumidouce stacks expire at exactly eight seconds");

        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        sim.advanceTime(3.0);
        weapon.onDamage(owner, dendro, 100.0, 13.0);
        StatefulWeaponRegressionSupport.assertClose(
                12.0, owner.getCurrentEnergy(),
                "Lumidouce Energy ICD reopens at exactly twelve seconds");
    }

    private static void testTriggerRejectionsAndOffFieldBehavior() {
        LumidouceElegy weapon = new LumidouceElegy(1);
        EnergyCharacter active = new EnergyCharacter(
                CharacterId.SUCROSE, Element.ANEMO, null);
        EnergyCharacter owner = new EnergyCharacter(
                CharacterId.XIANGLING, Element.DENDRO, weapon);
        CombatSimulator sim = simulatorWith(active, owner);
        sim.startBurning(active.getCharacterId(), 1000.0, 20.0, 0.1);

        AttackAction dendro = hit("Off-field Dendro", Element.DENDRO);
        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce can trigger while owner is off field");

        sim.advanceTime(1.0);
        weapon.onDamage(active, dendro, 100.0, sim.getCurrentTime());
        weapon.onDamage(owner, hit("Wrong element", Element.PYRO),
                100.0, sim.getCurrentTime());
        AttackAction dummy = hit("Dummy Dendro", Element.DENDRO);
        dummy.setHitEffectTrigger(false);
        weapon.onDamage(owner, dummy, 100.0, sim.getCurrentTime());
        AttackAction zero = new AttackAction(
                "Zero Dendro",
                0.0,
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.DENDRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        zero.setHitEffectTrigger(true);
        weapon.onDamage(owner, zero, 0.0, sim.getCurrentTime());
        weapon.onElementalReaction(
                ReactionResult.none(), owner, sim.getCurrentTime(), sim);
        weapon.onElementalReaction(BURNING, active, sim.getCurrentTime(), sim);
        StatefulWeaponRegressionSupport.assertEquals(
                1, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce rejects unrelated and foreign callbacks");
    }

    private static void testSnapshotAndBindingValidation() {
        LumidouceElegy weapon = new LumidouceElegy(1);
        EnergyCharacter owner = new EnergyCharacter(
                CharacterId.XIANGLING, Element.DENDRO, weapon);
        CombatSimulator sim = simulatorWith(owner);
        owner.spendEnergy(owner.getMaxEnergy());
        sim.startBurning(owner.getCharacterId(), 1000.0, 20.0, 0.1);
        sim.notifyReaction(BURNING, owner);
        sim.advanceTime(1.0);
        sim.notifyReaction(BURNING, owner);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        owner.spendEnergy(owner.getCurrentEnergy());
        sim.advanceTime(8.0);
        StatefulWeaponRegressionSupport.assertEquals(
                0, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce state changes before rollback");
        sim.restoreSnapshot(snapshot);
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce rollback restores stack state");
        StatefulWeaponRegressionSupport.assertClose(
                12.0, owner.getCurrentEnergy(),
                "Lumidouce rollback restores Energy");

        owner.spendEnergy(owner.getCurrentEnergy());
        AttackAction dendro = hit("Restored pending Dendro", Element.DENDRO);
        weapon.onDamage(owner, dendro, 100.0, sim.getCurrentTime());
        StatefulWeaponRegressionSupport.assertEquals(
                2, weapon.getStackCount(sim.getCurrentTime()),
                "Lumidouce rollback restores same-hit reservation");
        StatefulWeaponRegressionSupport.assertClose(
                0.0, owner.getCurrentEnergy(),
                "Lumidouce restored same-hit reservation suppresses duplicate Energy");
        sim.advanceTime(11.999);
        weapon.onDamage(owner, dendro, 100.0, 12.999);
        StatefulWeaponRegressionSupport.assertClose(
                0.0, owner.getCurrentEnergy(),
                "Lumidouce rollback restores Energy ICD before boundary");
        weapon.onDamage(owner, dendro, 100.0, 13.0);
        StatefulWeaponRegressionSupport.assertClose(
                12.0, owner.getCurrentEnergy(),
                "Lumidouce restored Energy ICD opens at exact boundary");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LumidouceElegy(1).restoreWeaponState(state),
                "Lumidouce rejects another instance state");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Lumidouce rejects foreign state type");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Lumidouce rejects cross-simulator reuse");
        StatefulWeaponRegressionSupport.assertThrows(
                IllegalArgumentException.class,
                () -> new LumidouceElegy(1).initializeForSimulator(owner, sim),
                "Lumidouce rejects an unequipped binding");
    }

    private static CombatSimulator simulatorWith(EnergyCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new model.entity.Enemy(90));
        for (EnergyCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static AttackAction hit(String name, Element element) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                element.getBonusStatType(),
                0.0,
                ActionType.SKILL);
        action.setHitEffectTrigger(true);
        return action;
    }

    /** Minimal Energy-bearing character for weapon tests. */
    private static final class EnergyCharacter extends Character {
        private EnergyCharacter(
                CharacterId id,
                Element characterElement,
                LumidouceElegy equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
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
            return 80.0;
        }
    }

    /** Foreign marker used to verify state type validation. */
    private static final class ForeignState implements SnapshotAwareWeaponEffect.State {
    }
}
