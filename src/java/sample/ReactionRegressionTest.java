package sample;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mechanics.analysis.EnergyAnalyzer;
import mechanics.buff.BuffId;
import mechanics.formula.ResistanceCalculator;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Lightweight regression checks for elemental reaction behavior.
 */
public class ReactionRegressionTest {
    private static final double EPS = 1e-6;

    public static void main(String[] args) {
        testPhase1ReactionMetadataAndMultipliers();
        testPhase2Superconduct();
        testPhase3FreezeAndShatter();
        testPhase4Crystallize();
        testPhase5Burning();
        testPhase6BloomCores();
        testPhase7HyperbloomAndBurgeon();
        testPhase8QuickenAggravateSpread();
        testPhase9LunarReactionConversion();
        testPhase10LunarChargedThundercloud();
        testPhase11LunarBloom();
        testPhase12LunarCrystallize();
        testAccuracyPhaseA_AuraDecayOneUnit();
        testAccuracyPhaseA_AuraDecayTwoUnitLongerThanOneUnit();
        testAccuracyPhaseA_VaporizeConsumesExpectedAura();
        testAccuracyPhaseA_ElectroChargedCoexistence();
        testAccuracyPhaseA_QuickenCoexistsWithDendroFollowup();
        testAccuracyPhaseB_StandardIcdThreeHitRule();
        testAccuracyPhaseB_StandardIcdTimeRule();
        testAccuracyPhaseB_NoIcdAppliesEveryHit();
        testAccuracyPhaseB_SharedIcdBlocksRelatedHits();
        testAccuracyPhaseB_DamageStillOccursWhenApplicationBlocked();
        testAccuracyPhaseB_IcdLoggingMatchesApplicationDecision();
        testAccuracyPhaseC_CoreOverflowExplodesOldest();
        testAccuracyPhaseC_HyperbloomConsumesOneCoreForSingleProjectile();
        testAccuracyPhaseC_BurgeonConsumesAoECores();
        testAccuracyPhaseC_CoreExplosionHitCap();
        testAccuracyPhaseC_LunarBloomUsesSameCorePolicy();
        testAccuracyPhaseD_QuickenRefreshesDuration();
        testAccuracyPhaseD_AggravateUsesTriggerEm();
        testAccuracyPhaseD_SpreadUsesTriggerReactionBonus();
        testAccuracyPhaseD_AdditivePassesThroughCritAndDmgBonus();
        testAccuracyPhaseD_NoCatalyzeAfterQuickenExpiry();
        testAccuracyPhaseE_LunarConversionRequiresBenedictionSource();
        testAccuracyPhaseE_LunarChargedTickOwnershipAndCrit();
        testAccuracyPhaseE_LunarBloomDewState();
        testAccuracyPhaseE_LunarCrystallizeHarmonyCadence();
        testAccuracyPhaseF_RaidenResolveAndEnergyRegression();
        testAccuracyPhaseF_FlinsThundercloudConditionalHits();
        testAccuracyPhaseF_BurstEnergyGateAndFlinsSpecialCost();
        testAccuracyPhaseF_ColumbinaGravityAndDewRegression();
        testAccuracyPhaseF_ColumbinaStandInBoundaries();
        testAccuracyPhaseF_ArtifactLunarReactionBuffRegression();
        testAccuracyPhaseF_WeaponReactionBonusRegression();
        testAccuracyPhaseF_SucroseNoIcdApplicationContract();
        testAccuracyPhaseF_XianglingChiliPickupOptIn();
        testAccuracyPhaseF_XingqiuOrbitalApplicationCadence();
        testAccuracyPhaseF_DamageHooksDispatchOnce();
        testAccuracyPhaseF_SkywardSpineInjectedProcBoundaries();
        testAccuracyPhase2_TimeAwareLinearDecay();
        testAccuracyPhase2_QueryBeforeAndAtApplication();
        testAccuracyPhase2_ExpiryBoundaries();
        testAccuracyPhase2_ReduceAuraAfterPartialDecay();
        testAccuracyPhase2_ZeroOrNegativeNeverNegative();
        testAccuracyPhase2_MapAndActiveAurasDecayAware();
        testAccuracyPhase4_SnapshotPreservesDecay();
        System.out.println("ReactionRegressionTest passed");
    }

    private static void testPhase1ReactionMetadataAndMultipliers() {
        ReactionResult vaporize = ReactionCalculator.calculate(Element.HYDRO, Element.PYRO, 0.0, 90);
        assertEquals(ReactionResult.Type.AMP, vaporize.getType(), "Hydro on Pyro should Vaporize");
        assertEquals(ReactionResult.Kind.VAPORIZE, vaporize.getKind(), "Hydro on Pyro kind");
        assertClose(2.0, vaporize.getAmpMultiplier(), EPS, "Hydro Vaporize multiplier");

        ReactionResult melt = ReactionCalculator.calculate(Element.CRYO, Element.PYRO, 0.0, 90);
        assertEquals(ReactionResult.Type.AMP, melt.getType(), "Cryo on Pyro should Melt");
        assertClose(1.5, melt.getAmpMultiplier(), EPS, "Reverse Melt multiplier");

        ReactionResult overload = ReactionCalculator.calculate(Element.PYRO, Element.ELECTRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.OVERLOAD, overload.getKind(), "Overloaded kind");
        assertEquals(Element.PYRO, overload.getDamageElement(), "Overloaded damage element");
        assertClose(1446.85 * 2.75, overload.getTransformDamage(), 0.01, "Overloaded base damage");

        ReactionResult electroCharged = ReactionCalculator.calculate(Element.ELECTRO, Element.HYDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.ELECTRO_CHARGED, electroCharged.getKind(), "Electro-Charged kind");
        assertEquals(Element.ELECTRO, electroCharged.getDamageElement(), "Electro-Charged damage element");
        assertClose(1446.85 * 2.0, electroCharged.getTransformDamage(), 0.01, "Electro-Charged base damage");

        ReactionResult swirl = ReactionCalculator.calculate(Element.ANEMO, Element.PYRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.SWIRL, swirl.getKind(), "Swirl kind");
        assertEquals(Element.PYRO, swirl.getRelatedElement(), "Swirl related element");
        assertEquals(Element.PYRO, swirl.getDamageElement(), "Swirl damage element");
        assertClose(1446.85 * 0.6, swirl.getTransformDamage(), 0.01, "Swirl base damage");
    }

    private static void testPhase2Superconduct() {
        ReactionResult superconduct = ReactionCalculator.calculate(Element.CRYO, Element.ELECTRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.SUPERCONDUCT, superconduct.getKind(), "Superconduct kind");
        assertEquals(Element.CRYO, superconduct.getDamageElement(), "Superconduct damage element");
        assertClose(1446.85 * 1.5, superconduct.getTransformDamage(), 0.01, "Superconduct base damage");

        TestCharacter baselineCharacter = testCharacter(Element.CRYO);
        CombatSimulator baseline = simulatorWith(baselineCharacter);
        baseline.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                damageHit("Physical baseline", Element.PHYSICAL, 1.0));
        double baselinePhysicalDamage = baseline.getTotalDamage();

        TestCharacter reactionCharacter = testCharacter(Element.CRYO);
        CombatSimulator sim = simulatorWith(reactionCharacter);
        sim.getEnemy().setAura(Element.ELECTRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Cryo trigger", Element.CRYO));
        double reactionDamage = sim.getTotalDamage();
        assertClose(expectedTransformative(1.5, Element.CRYO, 0.0), reactionDamage, 0.5,
                "Superconduct reaction damage after RES");

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                damageHit("Physical after Superconduct", Element.PHYSICAL, 1.0));
        double physicalAfterShred = sim.getTotalDamage() - reactionDamage;
        assertTrue(physicalAfterShred > baselinePhysicalDamage * 1.20,
                "Superconduct should increase subsequent Physical damage through RES shred");
    }

    private static void testPhase3FreezeAndShatter() {
        ReactionResult frozen = ReactionCalculator.calculate(Element.CRYO, Element.HYDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.FROZEN, frozen.getKind(), "Frozen kind");
        assertTrue(frozen.isStateful(), "Frozen should be stateful");
        assertClose(0.0, frozen.getTransformDamage(), EPS, "Frozen should not deal damage");

        TestCharacter character = testCharacter(Element.CRYO);
        CombatSimulator sim = simulatorWith(character);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Cryo freeze trigger", Element.CRYO));
        assertTrue(sim.getEnemy().isFrozen(), "Cryo on Hydro should create Freeze Aura");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Freeze should not deal immediate damage");

        AttackAction shatterHit = reactionHit("Blunt shatter trigger", Element.PHYSICAL);
        shatterHit.setShatterTrigger(true);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, shatterHit);
        assertTrue(!sim.getEnemy().isFrozen(), "Shatter should clear Freeze Aura");
        assertClose(expectedTransformative(3.0, Element.PHYSICAL, 0.0), sim.getTotalDamage(), 0.5,
                "Shatter reaction damage after RES");
    }

    private static void testPhase4Crystallize() {
        ReactionResult crystallize = ReactionCalculator.calculate(Element.GEO, Element.HYDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.CRYSTALLIZE, crystallize.getKind(), "Crystallize kind");
        assertEquals(Element.HYDRO, crystallize.getRelatedElement(), "Crystallize related element");
        assertTrue(crystallize.isStateful(), "Crystallize should be represented as a state/event reaction");
        assertClose(0.0, crystallize.getTransformDamage(), EPS, "Crystallize should not deal damage");

        TestCharacter character = testCharacter(Element.GEO);
        CombatSimulator sim = simulatorWith(character);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Geo crystallize trigger", Element.GEO));
        assertClose(0.0, sim.getTotalDamage(), EPS, "Crystallize should not deal offensive damage");
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.HYDRO), EPS, "Crystallize should consume Hydro aura");
    }

    private static void testPhase5Burning() {
        ReactionResult burning = ReactionCalculator.calculate(Element.PYRO, Element.DENDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.BURNING, burning.getKind(), "Burning kind");
        assertTrue(burning.isStateful(), "Burning should create persistent state");
        assertEquals(Element.PYRO, burning.getDamageElement(), "Burning damage element");
        assertClose(1446.85 * 0.25, burning.getTransformDamage(), 0.01, "Burning base tick damage");

        TestCharacter character = testCharacter(Element.PYRO);
        CombatSimulator sim = simulatorWith(character);
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Pyro burning trigger", Element.PYRO));
        assertTrue(sim.isBurningActive(), "Burning should be active after Pyro on Dendro");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Burning should not deal immediate damage");

        sim.advanceTime(0.26);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0), sim.getTotalDamage(), 0.5,
                "First Burning tick after RES");
        assertTrue(sim.getEnemy().getAuraUnits(Element.PYRO) >= 1.0, "Burning should maintain Pyro aura");

        sim.advanceTime(2.25);
        assertTrue(!sim.isBurningActive(), "Simplified Burning should expire after its fixed window");

        TestCharacter dendroCharacter = testCharacter(Element.DENDRO);
        CombatSimulator reverse = simulatorWith(dendroCharacter);
        reverse.getEnemy().setAura(Element.PYRO, 1.0);
        reverse.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Dendro burning trigger", Element.DENDRO));
        assertTrue(reverse.isBurningActive(), "Burning should also trigger from Dendro on Pyro");
        assertTrue(reverse.getEnemy().getAuraUnits(Element.PYRO) >= 1.0,
                "Burning should maintain Pyro aura for Dendro-on-Pyro trigger order");
    }

    private static void testPhase6BloomCores() {
        ReactionResult bloom = ReactionCalculator.calculate(Element.HYDRO, Element.DENDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.BLOOM, bloom.getKind(), "Bloom kind");
        assertTrue(bloom.isStateful(), "Bloom should create a Dendro Core state");
        assertEquals(Element.DENDRO, bloom.getDamageElement(), "Bloom damage element");
        assertClose(1446.85 * 2.0, bloom.getTransformDamage(), 0.01, "Bloom base damage");
        ReactionResult boostedBloom = ReactionCalculator.calculate(Element.HYDRO, Element.DENDRO, 0.0, 90, 0.50);
        assertClose(1446.85 * 2.0 * 1.50, boostedBloom.getTransformDamage(), 0.01,
                "Bloom reaction bonus should increase core damage");

        TestCharacter character = testCharacter(Element.HYDRO);
        CombatSimulator sim = simulatorWith(character);
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Hydro bloom trigger", Element.HYDRO));
        assertEquals(1, sim.getDendroCores().size(), "Bloom should create one Dendro Core");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Bloom should not deal immediate damage");

        sim.advanceTime(6.01);
        assertEquals(0, sim.getDendroCores().size(), "Dendro Core should be removed after expiry");
        assertClose(expectedTransformative(2.0, Element.DENDRO, 0.0), sim.getTotalDamage(), 0.5,
                "Bloom core expiry damage after RES");

        TestCharacter capCharacter = testCharacter(Element.HYDRO);
        CombatSimulator capped = simulatorWith(capCharacter);
        for (int i = 0; i < 6; i++) {
            capped.getEnemy().setAura(Element.DENDRO, 1.0);
            capped.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    reactionHit("Hydro bloom trigger " + i, Element.HYDRO));
        }
        assertEquals(5, capped.getDendroCores().size(), "Only five Dendro Cores should remain");
        assertClose(expectedTransformative(2.0, Element.DENDRO, 0.0), capped.getTotalDamage(), 0.5,
                "Creating a sixth Dendro Core should explode the oldest one");
    }

    private static void testPhase7HyperbloomAndBurgeon() {
        ReactionResult hyperbloom = ReactionCalculator.calculateHyperbloom(0.0, 90, 0.0);
        assertEquals(ReactionResult.Kind.HYPERBLOOM, hyperbloom.getKind(), "Hyperbloom kind");
        assertEquals(Element.DENDRO, hyperbloom.getDamageElement(), "Hyperbloom damage element");
        assertClose(1446.85 * 3.0, hyperbloom.getTransformDamage(), 0.01, "Hyperbloom base damage");

        TestCharacter electroCharacter = testCharacter(Element.ELECTRO)
                .withStat(StatType.HYPERBLOOM_DMG_BONUS, 0.50);
        CombatSimulator hyper = simulatorWith(electroCharacter);
        hyper.addDendroCore(CharacterId.SUCROSE, expectedTransformative(2.0, Element.DENDRO, 0.0));
        hyper.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro core trigger", Element.ELECTRO));
        assertEquals(0, hyper.getDendroCores().size(), "Hyperbloom should consume Dendro Cores");
        assertClose(expectedTransformative(3.0, Element.DENDRO, 0.0, 0.50), hyper.getTotalDamage(), 0.5,
                "Hyperbloom damage after RES");

        ReactionResult burgeon = ReactionCalculator.calculateBurgeon(0.0, 90, 0.0);
        assertEquals(ReactionResult.Kind.BURGEON, burgeon.getKind(), "Burgeon kind");
        assertEquals(Element.DENDRO, burgeon.getDamageElement(), "Burgeon damage element");
        assertClose(1446.85 * 3.0, burgeon.getTransformDamage(), 0.01, "Burgeon base damage");

        TestCharacter pyroCharacter = testCharacter(Element.PYRO)
                .withStat(StatType.BURGEON_DMG_BONUS, 0.50);
        CombatSimulator burgeonSim = simulatorWith(pyroCharacter);
        burgeonSim.addDendroCore(CharacterId.SUCROSE, expectedTransformative(2.0, Element.DENDRO, 0.0));
        burgeonSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Pyro core trigger", Element.PYRO));
        assertEquals(0, burgeonSim.getDendroCores().size(), "Burgeon should consume Dendro Cores");
        assertClose(expectedTransformative(3.0, Element.DENDRO, 0.0, 0.50), burgeonSim.getTotalDamage(), 0.5,
                "Burgeon damage after RES");
    }

    private static void testPhase8QuickenAggravateSpread() {
        ReactionResult quicken = ReactionCalculator.calculate(Element.ELECTRO, Element.DENDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.QUICKEN, quicken.getKind(), "Quicken kind");
        assertTrue(quicken.isStateful(), "Quicken should create persistent target state");
        assertClose(0.0, quicken.getTransformDamage(), EPS, "Quicken itself should not deal damage");

        ReactionResult aggravate = ReactionCalculator.calculateAggravate(0.0, 90, 0.0);
        assertEquals(ReactionResult.Kind.AGGRAVATE, aggravate.getKind(), "Aggravate kind");
        assertTrue(aggravate.canCrit(), "Aggravate additive damage should be able to crit through the hit");
        assertClose(1446.85 * 1.15, aggravate.getTransformDamage(), 0.01, "Aggravate additive base damage");

        TestCharacter electroCharacter = testCharacter(Element.ELECTRO)
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0)
                .withStat(StatType.ELECTRO_DMG_BONUS, 0.50);
        CombatSimulator aggravateSim = simulatorWith(electroCharacter);
        aggravateSim.getEnemy().setAura(Element.DENDRO, 1.0);
        aggravateSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro quicken trigger", Element.ELECTRO));
        assertTrue(aggravateSim.isQuickenActive(), "Electro on Dendro should create Quicken");
        assertClose(0.0, aggravateSim.getTotalDamage(), EPS, "Quicken should not deal immediate damage");

        aggravateSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Electro aggravated hit", Element.ELECTRO));
        assertClose(expectedStandardCatalyzeDamage(1.15, 0.50, 1.0, 1.0), aggravateSim.getTotalDamage(), 0.5,
                "Aggravate should add base damage before DMG Bonus/Crit/DEF/RES");

        ReactionResult spread = ReactionCalculator.calculateSpread(0.0, 90, 0.0);
        assertEquals(ReactionResult.Kind.SPREAD, spread.getKind(), "Spread kind");
        assertTrue(spread.canCrit(), "Spread additive damage should be able to crit through the hit");
        assertClose(1446.85 * 1.25, spread.getTransformDamage(), 0.01, "Spread additive base damage");

        TestCharacter dendroCharacter = testCharacter(Element.DENDRO)
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0)
                .withStat(StatType.DENDRO_DMG_BONUS, 0.50);
        CombatSimulator spreadSim = simulatorWith(dendroCharacter);
        spreadSim.getEnemy().setAura(Element.ELECTRO, 1.0);
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Dendro quicken trigger", Element.DENDRO));
        assertTrue(spreadSim.isQuickenActive(), "Dendro on Electro should create Quicken");
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Dendro spread hit", Element.DENDRO));
        assertClose(expectedStandardCatalyzeDamage(1.25, 0.50, 1.0, 1.0), spreadSim.getTotalDamage(), 0.5,
                "Spread should add base damage before DMG Bonus/Crit/DEF/RES");

        spreadSim.advanceTime(11.1);
        double beforeExpiredHit = spreadSim.getTotalDamage();
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Expired quicken Dendro hit", Element.DENDRO));
        double expiredHit = spreadSim.getTotalDamage() - beforeExpiredHit;
        assertClose(expectedStandardDamage(0.50, 1.0, 1.0), expiredHit, 0.5,
                "Expired Quicken should not trigger Spread");
    }

    private static void testPhase9LunarReactionConversion() {
        TestCharacter electro = testCharacter(Element.ELECTRO).asLunar();
        CombatSimulator charged = simulatorWith(electro);
        List<ReactionResult.Kind> chargedKinds = captureReactionKinds(charged);
        charged.getEnemy().setAura(Element.HYDRO, 1.0);
        charged.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro lunar charged trigger", Element.ELECTRO));
        assertTrue(chargedKinds.contains(ReactionResult.Kind.LUNAR_CHARGED),
                "Electro-Charged should convert to Lunar-Charged with Lunar conversion active");

        TestCharacter hydro = testCharacter(Element.HYDRO).asLunar();
        CombatSimulator bloom = simulatorWith(hydro);
        List<ReactionResult.Kind> bloomKinds = captureReactionKinds(bloom);
        bloom.getEnemy().setAura(Element.DENDRO, 1.0);
        bloom.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Hydro lunar bloom trigger", Element.HYDRO));
        assertTrue(bloomKinds.contains(ReactionResult.Kind.LUNAR_BLOOM),
                "Bloom should convert to Lunar-Bloom with Lunar conversion active");
        assertEquals(1, bloom.getDendroCores().size(), "Lunar-Bloom should preserve Dendro Core creation");

        TestCharacter geo = testCharacter(Element.GEO).asLunar();
        CombatSimulator crystallize = simulatorWith(geo);
        List<ReactionResult.Kind> crystallizeKinds = captureReactionKinds(crystallize);
        crystallize.getEnemy().setAura(Element.HYDRO, 1.0);
        crystallize.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Geo lunar crystallize trigger", Element.GEO));
        assertTrue(crystallizeKinds.contains(ReactionResult.Kind.LUNAR_CRYSTALLIZE),
                "Hydro Crystallize should convert to Lunar-Crystallize with Lunar conversion active");
        assertEquals(3, crystallize.getMoondriftCount(), "First Lunar-Crystallize should create Moondrifts");
    }

    private static void testPhase10LunarChargedThundercloud() {
        TestCharacter electro = testCharacter(Element.ELECTRO).asLunar();
        CombatSimulator sim = simulatorWith(electro);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro lunar charged trigger", Element.ELECTRO));
        double immediate = sim.getTotalDamage();
        assertTrue(immediate > 0.0, "Lunar-Charged should deal immediate Thundercloud setup damage");
        assertTrue(sim.isThundercloudActive(), "Lunar-Charged should create one active Thundercloud window");

        sim.advanceTime(1.9);
        assertClose(immediate, sim.getTotalDamage(), 0.5, "Thundercloud should not tick before 2 seconds");
        sim.advanceTime(0.2);
        assertTrue(sim.getTotalDamage() > immediate, "Thundercloud should tick at the 2 second cadence");
        // Hydro was applied with the legacy infinite-expiry aura, so it only loses
        // the 0.4 GU tick consumption. Electro was applied with a finite duration
        // by the Electro-Charged scheduler, so it also reflects natural decay over
        // the 2 second Thundercloud cadence (1U over the 11s simplified duration).
        assertClose(0.6, sim.getEnemy().getAuraUnits(Element.HYDRO), 0.01,
                "Thundercloud tick should consume 0.4 GU Hydro");
        assertClose(0.6 - 2.0 / 11.0, sim.getEnemy().getAuraUnits(Element.ELECTRO), 0.01,
                "Thundercloud tick should consume 0.4 GU Electro on top of natural decay");
    }

    private static void testPhase11LunarBloom() {
        TestCharacter hydro = testCharacter(Element.HYDRO).asLunar();
        CombatSimulator sim = simulatorWith(hydro);
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Hydro lunar bloom trigger", Element.HYDRO));
        assertTrue(kinds.contains(ReactionResult.Kind.LUNAR_BLOOM), "Lunar-Bloom should trigger as an event");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Lunar-Bloom itself should not deal immediate damage");
        assertEquals(1, sim.getDendroCores().size(), "Lunar-Bloom should preserve Dendro Core behavior");
        sim.advanceTime(6.01);
        assertTrue(sim.getTotalDamage() > 0.0, "Lunar-Bloom Core should still explode through Bloom core handling");
    }

    private static void testPhase12LunarCrystallize() {
        TestCharacter standardGeo = testCharacter(Element.GEO);
        CombatSimulator standard = simulatorWith(standardGeo);
        standard.getEnemy().setAura(Element.HYDRO, 1.0);
        standard.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Standard crystallize trigger", Element.GEO));
        assertEquals(0, standard.getMoondriftCount(), "Standard Crystallize should not create Moondrifts");

        TestCharacter lunarGeo = testCharacter(Element.GEO).asLunar();
        CombatSimulator lunar = simulatorWith(lunarGeo);
        for (int i = 0; i < 3; i++) {
            lunar.getEnemy().setAura(Element.HYDRO, 1.0);
            lunar.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    reactionHit("Lunar crystallize trigger " + i, Element.GEO));
        }
        assertEquals(3, lunar.getMoondriftCount(), "Lunar-Crystallize should track Moondrift state");
        assertEquals(3, lunar.getLunarCrystallizeTriggerCount(),
                "Lunar-Crystallize trigger count should accumulate");
        assertTrue(lunar.getTotalDamage() > 0.0,
                "Every third Lunar-Crystallize should trigger Moondrift Harmony damage");
    }

    private static void testAccuracyPhaseA_AuraDecayOneUnit() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        sim.getEnemy().setAura(Element.PYRO, 1.0, sim.getCurrentTime());
        sim.advanceTime(10.99);
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "1U aura should remain just before simplified expiry");
        sim.advanceTime(0.02);
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "1U aura should expire naturally after its duration");
    }

    private static void testAccuracyPhaseA_AuraDecayTwoUnitLongerThanOneUnit() {
        CombatSimulator oneUnit = simulatorWith(testCharacter(Element.PYRO));
        CombatSimulator twoUnit = simulatorWith(testCharacter(Element.PYRO));
        oneUnit.getEnemy().setAura(Element.PYRO, 1.0, oneUnit.getCurrentTime());
        twoUnit.getEnemy().setAura(Element.PYRO, 2.0, twoUnit.getCurrentTime());

        oneUnit.advanceTime(11.1);
        twoUnit.advanceTime(11.1);
        assertClose(0.0, oneUnit.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "1U aura should be expired after 11.1s");
        assertClose(2.0, twoUnit.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "2U aura should survive longer than 1U");
    }

    private static void testAccuracyPhase2_TimeAwareLinearDecay() {
        // 1U aura uses simplified duration 6 + 1*5 = 11s, so decay rate is 1/11 units/s.
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.PYRO, 1.0, 0.0);
        assertClose(1.0, enemy.getAuraUnits(Element.PYRO, 0.0), EPS,
                "Time-aware read at application time should equal applied units");
        assertClose(0.5, enemy.getAuraUnits(Element.PYRO, 5.5), EPS,
                "1U aura should linearly decay to half at the duration midpoint");
        assertClose(0.0, enemy.getAuraUnits(Element.PYRO, 11.0), EPS,
                "1U aura should reach zero at its configured expiry");
        assertClose(0.0, enemy.getAuraUnits(Element.PYRO, 20.0), EPS,
                "Decayed aura should never read below zero after expiry");
    }

    private static void testAccuracyPhase2_QueryBeforeAndAtApplication() {
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.PYRO, 1.0, 5.0);
        assertClose(1.0, enemy.getAuraUnits(Element.PYRO, 3.0), EPS,
                "Query before application time should return full units (no decay yet)");
        assertClose(1.0, enemy.getAuraUnits(Element.PYRO, 5.0), EPS,
                "Query exactly at application time should return full units");
    }

    private static void testAccuracyPhase2_ExpiryBoundaries() {
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.ELECTRO, 1.0, 0.0);
        assertClose(0.0, enemy.getAuraUnits(Element.ELECTRO, 11.0), EPS,
                "Decayed value should be zero exactly at expiry");
        assertClose(0.0, enemy.getAuraUnits(Element.ELECTRO, 11.01), EPS,
                "Decayed value should remain zero just after expiry");
        enemy.updateAuras(11.0);
        assertTrue(enemy.getActiveAuras(11.0).isEmpty(),
                "updateAuras at expiry should remove the naturally decayed aura");
        assertClose(0.0, enemy.getAuraUnits(Element.ELECTRO), EPS,
                "Removed aura should read zero through the legacy accessor as well");
    }

    private static void testAccuracyPhase2_ReduceAuraAfterPartialDecay() {
        // 2U aura uses duration 6 + 2*5 = 16s, so decay rate is 2/16 = 0.125 units/s.
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.HYDRO, 2.0, 0.0);
        assertClose(1.5, enemy.getAuraUnits(Element.HYDRO, 4.0), EPS,
                "2U aura should decay to 1.5U after 4s");
        enemy.reduceAura(Element.HYDRO, 0.5, 4.0);
        assertClose(1.0, enemy.getAuraUnits(Element.HYDRO, 4.0), EPS,
                "Discrete consumption should use the decayed current value");
        assertClose(0.5, enemy.getAuraUnits(Element.HYDRO, 8.0), EPS,
                "Decay should continue from the remaining value at the original rate");
        assertClose(0.0, enemy.getAuraUnits(Element.HYDRO, 12.0), EPS,
                "Re-based aura should expire 8s after consumption (1.0U / 0.125)");
    }

    private static void testAccuracyPhase2_ZeroOrNegativeNeverNegative() {
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.PYRO, 1.0, 0.0);
        enemy.setAura(Element.PYRO, 0.0, 0.0);
        assertClose(0.0, enemy.getAuraUnits(Element.PYRO, 0.0), EPS,
                "Applying zero units should remove the aura");

        enemy.setAura(Element.PYRO, 1.0, 0.0);
        enemy.reduceAura(Element.PYRO, 5.0, 0.0);
        assertClose(0.0, enemy.getAuraUnits(Element.PYRO, 0.0), EPS,
                "Over-consumption should remove the aura and never report negative units");
    }

    private static void testAccuracyPhase2_MapAndActiveAurasDecayAware() {
        Enemy enemy = new Enemy(90);
        enemy.setAura(Element.ELECTRO, 1.0, 0.0);
        assertEquals(Element.ELECTRO, enemy.getPrimaryAura(5.5),
                "Primary aura should still be reported while partially decayed");
        assertClose(0.5, enemy.getAuraMap(5.5).getOrDefault(Element.ELECTRO, 0.0), EPS,
                "Time-aware aura map should report the decayed current value");
        assertTrue(enemy.getActiveAuras(5.5).contains(Element.ELECTRO),
                "Active auras should include a partially decayed aura");

        assertTrue(enemy.getAuraMap(11.0).isEmpty(),
                "Time-aware aura map should omit fully decayed auras at expiry");
        assertTrue(enemy.getPrimaryAura(11.0) == null,
                "Primary aura should be null once the aura has decayed to zero");
    }

    private static void testAccuracyPhase4_SnapshotPreservesDecay() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        // 1U Pyro applied at t=0 with finite duration (11s), decay rate 1/11 units/s.
        sim.getEnemy().setAura(Element.PYRO, 1.0, sim.getCurrentTime());
        SimulatorSnapshot snap = sim.saveSnapshot();

        // Advance and mutate the live state past the snapshot point.
        sim.advanceTime(8.0);
        assertClose(1.0 - 8.0 / 11.0, sim.getEnemy().getAuraUnits(Element.PYRO, sim.getCurrentTime()), EPS,
                "Live aura should have decayed before restore");

        sim.restoreSnapshot(snap);
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.PYRO, sim.getCurrentTime()), EPS,
                "Restore should bring the aura back to its saved units");

        // Future natural decay must resume from the restored state, not be flattened.
        sim.advanceTime(5.5);
        assertClose(1.0 - 5.5 / 11.0, sim.getEnemy().getAuraUnits(Element.PYRO, sim.getCurrentTime()), EPS,
                "Restored aura should continue to decay with the original rate");
    }

    private static void testAccuracyPhaseA_VaporizeConsumesExpectedAura() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        sim.getEnemy().setAura(Element.HYDRO, 2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Pyro reverse vaporize trigger", Element.PYRO));
        assertClose(1.5, sim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Reverse Vaporize should consume 0.5U Hydro for a 1U Pyro trigger");
    }

    private static void testAccuracyPhaseA_ElectroChargedCoexistence() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        sim.setMoonsign(CombatSimulator.Moonsign.NONE);
        sim.getEnemy().setAura(Element.HYDRO, 1.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro charged coexistence trigger", Element.ELECTRO));
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Electro-Charged should keep Hydro aura before tick consumption");
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "Electro-Charged should keep Electro aura before tick consumption");

        sim.advanceTime(1.01);
        // Both auras were applied with a finite duration, so each tick consumes
        // 0.4U on top of natural decay (1U over the 11s simplified duration) at the
        // 1 second standard Electro-Charged cadence.
        assertClose(0.6 - 1.0 / 11.0, sim.getEnemy().getAuraUnits(Element.HYDRO), 0.01,
                "Standard Electro-Charged tick should consume 0.4U Hydro on top of natural decay");
        assertClose(0.6 - 1.0 / 11.0, sim.getEnemy().getAuraUnits(Element.ELECTRO), 0.01,
                "Standard Electro-Charged tick should consume 0.4U Electro on top of natural decay");
    }

    private static void testAccuracyPhaseA_QuickenCoexistsWithDendroFollowup() {
        TestCharacter dendroCharacter = testCharacter(Element.DENDRO)
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0)
                .withStat(StatType.DENDRO_DMG_BONUS, 0.50);
        CombatSimulator sim = simulatorWith(dendroCharacter);
        sim.getEnemy().setAura(Element.ELECTRO, 1.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Dendro quicken coexistence trigger", Element.DENDRO));
        assertTrue(sim.isQuickenActive(), "Quicken should be active after Dendro on Electro");

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Dendro spread coexistence hit", Element.DENDRO));
        assertTrue(sim.isQuickenActive(), "Spread follow-up should not immediately delete Quicken");
        assertClose(expectedStandardCatalyzeDamage(1.25, 0.50, 1.0, 1.0), sim.getTotalDamage(), 0.5,
                "Dendro follow-up during Quicken should trigger Spread");
    }

    private static void testAccuracyPhaseB_StandardIcdThreeHitRule() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD hit 1", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "First standard ICD hit should apply aura");

        sim.getEnemy().setAura(Element.PYRO, 0.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD hit 2", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Second quick same-group hit should be ICD-blocked");

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD hit 3", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Third quick same-group hit should apply by the 3-hit ICD rule");
    }

    private static void testAccuracyPhaseB_StandardIcdTimeRule() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD time hit 1", Element.PYRO, ICDTag.ElementalBurst, 0.0));
        sim.getEnemy().setAura(Element.PYRO, 0.0);
        sim.advanceTime(2.51);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD time hit 2", Element.PYRO, ICDTag.ElementalBurst, 0.0));
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Same-group hit after 2.5s should apply aura");
    }

    private static void testAccuracyPhaseB_NoIcdAppliesEveryHit() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("No ICD vaporize 1", Element.PYRO));
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("No ICD vaporize 2", Element.PYRO));

        int vaporizeCount = 0;
        for (ReactionResult.Kind kind : kinds) {
            if (kind == ReactionResult.Kind.VAPORIZE) {
                vaporizeCount++;
            }
        }
        assertEquals(2, vaporizeCount, "No-ICD hits should apply and react every hit");
    }

    private static void testAccuracyPhaseB_SharedIcdBlocksRelatedHits() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Shared ICD skill hit A", Element.PYRO, ICDTag.ElementalSkill, 1.0));
        sim.getEnemy().setAura(Element.PYRO, 0.0);
        double afterFirst = sim.getTotalDamage();

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Shared ICD skill hit B", Element.PYRO, ICDTag.ElementalSkill, 1.0));
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Different action names in the same ICD group should share the block");
        assertTrue(sim.getTotalDamage() > afterFirst,
                "ICD-blocked related action should still deal direct damage");
    }

    private static void testAccuracyPhaseB_DamageStillOccursWhenApplicationBlocked() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        AttackAction first = standardIcdHit("Damage ICD hit 1", Element.PYRO, ICDTag.NormalAttack, 1.0);
        AttackAction second = standardIcdHit("Damage ICD hit 2", Element.PYRO, ICDTag.NormalAttack, 1.0);

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, first);
        sim.getEnemy().setAura(Element.PYRO, 0.0);
        double firstDamage = sim.getTotalDamage();
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, second);

        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Second same-group hit should not apply aura while ICD-blocked");
        assertClose(firstDamage * 2.0, sim.getTotalDamage(), 0.5,
                "ICD-blocked elemental application should not suppress direct damage");
    }

    private static void testAccuracyPhaseB_IcdLoggingMatchesApplicationDecision() {
        CombatSimulator zeroGaugeSim = simulatorWith(testCharacter(Element.PYRO));
        zeroGaugeSim.setLoggingEnabled(true);
        String zeroGaugeLog = captureStandardOutput(() -> zeroGaugeSim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                damageHit("Zero-gauge logging hit", Element.PYRO, 1.0)));
        assertTrue(!zeroGaugeLog.contains("[ICD] Applied blocked"),
                "Zero-gauge attacks should not be reported as ICD-blocked");

        CombatSimulator blockedSim = simulatorWith(testCharacter(Element.PYRO));
        blockedSim.setLoggingEnabled(true);
        String blockedLog = captureStandardOutput(() -> {
            blockedSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    standardIcdHit("ICD logging hit 1", Element.PYRO, ICDTag.ElementalSkill, 0.0));
            blockedSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    standardIcdHit("ICD logging hit 2", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        });
        assertTrue(blockedLog.contains("[ICD] Applied blocked (ElementalSkill)"),
                "A positive-gauge application rejected by ICD should retain the blocked diagnostic");
    }

    private static void testAccuracyPhaseC_CoreOverflowExplodesOldest() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.HYDRO));
        for (int i = 0; i < 6; i++) {
            sim.getEnemy().setAura(Element.DENDRO, 1.0);
            sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    reactionHit("Accuracy Bloom overflow " + i, Element.HYDRO));
        }
        assertEquals(5, sim.getDendroCores().size(), "Core overflow should leave five active cores");
        assertClose(expectedTransformative(2.0, Element.DENDRO, 0.0), sim.getTotalDamage(), 0.5,
                "Core overflow should explode the oldest core deterministically");
    }

    private static void testAccuracyPhaseC_HyperbloomConsumesOneCoreForSingleProjectile() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        addDendroCores(sim, 3);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Single projectile Hyperbloom", Element.ELECTRO));
        assertEquals(2, sim.getDendroCores().size(),
                "Default Electro Hyperbloom policy should consume one core");
        assertClose(expectedTransformative(3.0, Element.DENDRO, 0.0), sim.getTotalDamage(), 0.5,
                "Single Hyperbloom projectile should deal one core of damage");
    }

    private static void testAccuracyPhaseC_BurgeonConsumesAoECores() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        addDendroCores(sim, 4);
        AttackAction aoeBurgeon = reactionHit("Limited AoE Burgeon", Element.PYRO);
        aoeBurgeon.setDendroCoreConsumptionLimit(2);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, aoeBurgeon);
        assertEquals(2, sim.getDendroCores().size(),
                "Configured AoE Burgeon should consume the requested core count");
        assertClose(expectedTransformative(3.0, Element.DENDRO, 0.0) * 2.0, sim.getTotalDamage(), 1.0,
                "Two consumed Burgeon cores should both deal damage inside the hit cap");
    }

    private static void testAccuracyPhaseC_CoreExplosionHitCap() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        addDendroCores(sim, 3);
        AttackAction cappedBurgeon = reactionHit("Capped AoE Burgeon", Element.PYRO);
        cappedBurgeon.setDendroCoreConsumptionLimit(3);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, cappedBurgeon);
        assertEquals(0, sim.getDendroCores().size(), "All configured cores should be consumed");
        assertClose(expectedTransformative(3.0, Element.DENDRO, 0.0) * 2.0, sim.getTotalDamage(), 1.0,
                "Only two core explosions should damage the same target inside the 0.5s cap window");
    }

    private static void testAccuracyPhaseC_LunarBloomUsesSameCorePolicy() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.HYDRO).asLunar());
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Accuracy Lunar-Bloom core", Element.HYDRO));
        addDendroCores(sim, 2);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Lunar-Bloom Hyperbloom policy", Element.ELECTRO));
        assertEquals(2, sim.getDendroCores().size(),
                "Lunar-Bloom-created cores should share the one-core Hyperbloom policy");
        assertTrue(sim.getTotalDamage() > 0.0,
                "Lunar-Bloom core consumed by Hyperbloom should use normal core damage handling");
    }

    private static void testAccuracyPhaseD_QuickenRefreshesDuration() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Quicken refresh trigger 1", Element.ELECTRO));
        double firstEnd = sim.getQuickenEndTime();

        sim.advanceTime(5.0);
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Quicken refresh trigger 2", Element.ELECTRO));
        assertTrue(sim.getQuickenEndTime() > firstEnd,
                "Re-triggering Quicken before expiry should extend the expiry time");
    }

    private static void testAccuracyPhaseD_AggravateUsesTriggerEm() {
        double em = 300.0;
        TestCharacter electroCharacter = testCharacter(Element.ELECTRO)
                .withStat(StatType.ELEMENTAL_MASTERY, em)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0);
        CombatSimulator sim = simulatorWith(electroCharacter);
        sim.setQuickenEndTime(20.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("EM Aggravate hit", Element.ELECTRO));
        assertClose(expectedStandardCatalyzeDamageWithEm(1.15, 0.0, 0.0, 0.0, em, 0.0),
                sim.getTotalDamage(), 0.5,
                "Aggravate additive value should use the trigger character EM");
    }

    private static void testAccuracyPhaseD_SpreadUsesTriggerReactionBonus() {
        double spreadBonus = 0.50;
        TestCharacter dendroCharacter = testCharacter(Element.DENDRO)
                .withStat(StatType.SPREAD_DMG_BONUS, spreadBonus)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0);
        CombatSimulator sim = simulatorWith(dendroCharacter);
        sim.setQuickenEndTime(20.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Bonus Spread hit", Element.DENDRO));
        assertClose(expectedStandardCatalyzeDamageWithEm(1.25, 0.0, 0.0, 0.0, 0.0, spreadBonus),
                sim.getTotalDamage(), 0.5,
                "Spread additive value should include trigger reaction bonus");
    }

    private static void testAccuracyPhaseD_AdditivePassesThroughCritAndDmgBonus() {
        TestCharacter electroCharacter = testCharacter(Element.ELECTRO)
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0)
                .withStat(StatType.ELECTRO_DMG_BONUS, 0.50);
        CombatSimulator sim = simulatorWith(electroCharacter);
        sim.setQuickenEndTime(20.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Crit Aggravate hit", Element.ELECTRO));
        assertClose(expectedStandardCatalyzeDamage(1.15, 0.50, 1.0, 1.0), sim.getTotalDamage(), 0.5,
                "Aggravate additive base should pass through DMG Bonus, Crit, DEF, and RES");
    }

    private static void testAccuracyPhaseD_NoCatalyzeAfterQuickenExpiry() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0));
        sim.setQuickenEndTime(1.0);
        sim.advanceTime(1.01);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Expired Aggravate hit", Element.ELECTRO));
        assertClose(expectedStandardDamage(0.0, 0.0, 0.0), sim.getTotalDamage(), 0.5,
                "Expired Quicken should not allow Aggravate");
    }

    private static void testAccuracyPhaseE_LunarConversionRequiresBenedictionSource() {
        CombatSimulator noConverter = simulatorWith(testCharacter(Element.ELECTRO));
        List<ReactionResult.Kind> standardKinds = captureReactionKinds(noConverter);
        noConverter.getEnemy().setAura(Element.HYDRO, 1.0);
        noConverter.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("No converter EC", Element.ELECTRO));
        assertTrue(!standardKinds.contains(ReactionResult.Kind.LUNAR_CHARGED),
                "Moonsign without a Lunar converter should not convert Electro-Charged");
        assertTrue(standardKinds.contains(ReactionResult.Kind.ELECTRO_CHARGED),
                "No-converter Electro-Charged should remain standard");

        CombatSimulator withConverter = simulatorWith(testCharacter(Element.ELECTRO).asLunar());
        List<ReactionResult.Kind> lunarKinds = captureReactionKinds(withConverter);
        withConverter.getEnemy().setAura(Element.HYDRO, 1.0);
        withConverter.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Converter EC", Element.ELECTRO));
        assertTrue(lunarKinds.contains(ReactionResult.Kind.LUNAR_CHARGED),
                "Lunar converter plus Moonsign should convert Electro-Charged");
    }

    private static void testAccuracyPhaseE_LunarChargedTickOwnershipAndCrit() {
        CombatSimulator noCrit = simulatorWith(testCharacter(Element.ELECTRO).asLunar()
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0));
        noCrit.getEnemy().setAura(Element.HYDRO, 1.0);
        noCrit.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("No crit Lunar-Charged", Element.ELECTRO));
        noCrit.advanceTime(2.1);
        double noCritDamage = noCrit.getTotalDamage();

        CombatSimulator crit = simulatorWith(testCharacter(Element.ELECTRO).asLunar()
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0));
        crit.getEnemy().setAura(Element.HYDRO, 1.0);
        crit.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Crit Lunar-Charged", Element.ELECTRO));
        crit.advanceTime(2.1);
        assertTrue(crit.getTotalDamage() > noCritDamage * 1.9,
                "Lunar-Charged immediate/tick damage should use Lunar crit scaling");
    }

    private static void testAccuracyPhaseE_LunarBloomDewState() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.HYDRO).asLunar());
        sim.getEnemy().setAura(Element.DENDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Dew Lunar-Bloom", Element.HYDRO));
        assertEquals(1, sim.getVerdantDewCount(), "Lunar-Bloom should increment Verdant Dew state");
        assertEquals(1, sim.getMoonridgeDewCount(), "Lunar-Bloom should increment Moonridge Dew state");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Second Dew Lunar-Bloom", Element.HYDRO));
        sim.restoreSnapshot(snapshot);
        assertEquals(1, sim.getVerdantDewCount(), "Verdant Dew state should be snapshot-safe");
        assertEquals(1, sim.getMoonridgeDewCount(), "Moonridge Dew state should be snapshot-safe");
    }

    private static void testAccuracyPhaseE_LunarCrystallizeHarmonyCadence() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.GEO).asLunar());
        double before = sim.getTotalDamage();
        for (int i = 0; i < 3; i++) {
            sim.getEnemy().setAura(Element.HYDRO, 1.0);
            sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                    reactionHit("Accuracy Lunar-Crystallize cadence " + i, Element.GEO));
        }
        assertEquals(3, sim.getLunarCrystallizeTriggerCount(),
                "Lunar-Crystallize should count three triggers");
        assertTrue(sim.getTotalDamage() > before,
                "Third Lunar-Crystallize trigger should fire Harmony damage");
    }

    private static void testAccuracyPhaseF_RaidenResolveAndEnergyRegression() {
        model.character.RaidenShogun baselineRaiden = new model.character.RaidenShogun(
                new model.weapon.TheCatch(), blankArtifact());
        CombatSimulator baseline = simulatorWithExistingCharacter(baselineRaiden);
        baseline.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        baseline.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        double baselineBurst = baseline.getLastActionDirectDamageCapture();

        model.character.RaidenShogun stackedRaiden = new model.character.RaidenShogun(
                new model.weapon.TheCatch(), blankArtifact());
        CombatSimulator stacked = simulatorWithExistingCharacter(stackedRaiden);
        TestCharacter burstSource = testCharacter(Element.HYDRO);
        stacked.addCharacter(burstSource);
        stacked.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        stacked.performAction(CharacterId.SUCROSE,
                new AttackAction("Resolve source burst", 0.0, Element.HYDRO, StatType.BASE_ATK,
                        null, 0.0, false, ActionType.BURST));
        stacked.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        double stackedBurst = stacked.getLastActionDirectDamageCapture();

        assertTrue(stackedBurst > baselineBurst,
                "Raiden burst should gain damage from Resolve generated by another burst action");

        model.character.RaidenShogun raiden = new model.character.RaidenShogun(
                new model.weapon.TheCatch(), blankArtifact());
        model.character.Xiangling xiangling = new model.character.Xiangling(
                new model.weapon.TheCatch(), blankArtifact());
        CombatSimulator multiHitBurst = simulatorWithExistingCharacter(raiden);
        multiHitBurst.addCharacter(xiangling);
        multiHitBurst.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        multiHitBurst.performAction(CharacterId.XIANGLING,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(18.0, raiden.getResolveStacks(), EPS,
                "Raiden Resolve should count Xiangling's multi-hit burst cast once"
                        + " plus one Wishes Unnumbered particle trigger");
    }

    private static void testAccuracyPhaseF_FlinsThundercloudConditionalHits() {
        model.character.Flins noCloudFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator noCloud = simulatorWithExistingCharacter(noCloudFlins);
        int[] noCloudMiddleHits = { 0 };
        List<Double> noCloudDelayedHitTimes = new ArrayList<>();
        noCloud.addListener((actor, action, time) -> {
            if ("Cometh the Night (Middle)".equals(action.getName())) {
                noCloudMiddleHits[0]++;
                noCloudDelayedHitTimes.add(time);
            } else if ("Cometh the Night (Final)".equals(action.getName())) {
                noCloudDelayedHitTimes.add(time);
            }
        });
        noCloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), noCloud);
        noCloud.advanceTime(10.0);
        assertEquals(3, noCloudDelayedHitTimes.size(),
                "Flins standard burst without Thundercloud should schedule two middle hits and one final hit");
        assertClose(2.5, noCloudDelayedHitTimes.get(0), EPS,
                "First Flins delayed middle hit should use a fixed scheduled time");
        assertClose(2.8, noCloudDelayedHitTimes.get(1), EPS,
                "Second Flins delayed middle hit should use a fixed scheduled time");
        assertClose(3.6, noCloudDelayedHitTimes.get(2), EPS,
                "Flins delayed final hit should use a fixed scheduled time");

        model.character.Flins cloudFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator cloud = simulatorWithExistingCharacter(cloudFlins);
        int[] cloudMiddleHits = { 0 };
        cloud.addListener((actor, action, time) -> {
            if ("Cometh the Night (Middle)".equals(action.getName())) {
                cloudMiddleHits[0]++;
            }
        });
        cloud.setThundercloudEndTime(cloud.getCurrentTime() + 30.0);
        assertTrue(cloud.isThundercloudActive(), "Test setup should activate simulator Thundercloud state");
        cloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), cloud);
        cloud.advanceTime(10.0);
        assertTrue(cloudMiddleHits[0] == noCloudMiddleHits[0] + 2,
                "Flins standard burst should add conditional middle hits while Thundercloud is active"
                        + " (noCloud=" + noCloudMiddleHits[0] + ", cloud=" + cloudMiddleHits[0] + ")");
    }

    private static void testAccuracyPhaseF_BurstEnergyGateAndFlinsSpecialCost() {
        TestBurstCharacter gated = new TestBurstCharacter(60.0);
        CombatSimulator gateSim = simulatorWithExistingCharacter(gated);
        gated.restoreCurrentEnergy(59.0);

        gateSim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.BURST));

        assertEquals(0, gated.burstCasts, "Burst should not execute below current energy cost");
        assertClose(59.0, gated.getCurrentEnergy(), EPS, "Skipped burst should not consume energy");
        assertClose(60.0, gated.getMissedBurstCost(), EPS, "Skipped burst cost should feed ER calibration");
        Map<CharacterId, Double> missedBurstER = EnergyAnalyzer.calculateERRequirements(gateSim);
        assertTrue(missedBurstER.get(CharacterId.SUCROSE) > 1.0,
                "Missed burst should raise required ER above base when particle energy is absent");

        gated.restoreCurrentEnergy(60.0);
        gateSim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.BURST));

        assertEquals(1, gated.burstCasts, "Burst should execute at current energy cost");
        assertClose(0.0, gated.getCurrentEnergy(), EPS, "Standard burst should spend its full cost");

        model.character.Flins flins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator flinsSim = simulatorWithExistingCharacter(flins);
        flins.restoreCurrentEnergy(80.0);

        flinsSim.performAction(CharacterId.FLINS, CharacterActionRequest.of(CharacterActionKey.SKILL));
        flinsSim.performAction(CharacterId.FLINS, CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertClose(80.0, flins.getMaxEnergy(), EPS, "Flins energy bar should remain 80 during special burst state");
        assertClose(30.0, flins.getEnergyCost(), EPS, "Flins Thunderous Symphony burst should cost 30");

        flins.restoreCurrentEnergy(29.0);
        flinsSim.performAction(CharacterId.FLINS, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(29.0, flins.getCurrentEnergy(), EPS,
                "Flins special burst should skip below its 30-energy cost");
        assertClose(30.0, flins.getMissedBurstCost(), EPS,
                "Skipped Flins special burst should record the active 30-energy cost");

        flins.restoreCurrentEnergy(30.0);
        flinsSim.performAction(CharacterId.FLINS, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.0, flins.getCurrentEnergy(), EPS,
                "Flins special burst should succeed at exactly 30 energy");
        flins.receiveFlatEnergy(100.0);
        assertClose(80.0, flins.getCurrentEnergy(), EPS,
                "Flins energy gain should still cap at the 80-energy bar");
    }

    private static void testAccuracyPhaseF_ColumbinaGravityAndDewRegression() {
        model.character.Columbina columbina = new model.character.Columbina(new TestWeapon(), blankArtifact());
        CombatSimulator sim = simulatorWithExistingCharacter(columbina);
        int[] cleanseHits = { 0 };
        sim.addListener((actor, action, time) -> {
            if (action.getName().startsWith("Moondew Cleanse Hit")) {
                cleanseHits[0]++;
            }
        });

        columbina.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        sim.notifyReaction(
                ReactionResult.lunar(
                        0.0,
                        ReactionResult.LunarType.BLOOM,
                        Element.DENDRO,
                        Element.DENDRO,
                        true,
                        false),
                columbina);
        columbina.onAction(CharacterActionRequest.of(CharacterActionKey.CHARGE), sim);

        assertEquals(3, cleanseHits[0],
                "Columbina should gain Verdant Dew from Lunar-Bloom near Ripple and consume it for Moondew Cleanse");
    }

    private static void testAccuracyPhaseF_ColumbinaStandInBoundaries() {
        model.character.Columbina columbina = new model.character.Columbina(new TestWeapon(), blankArtifact());
        CombatSimulator sim = simulatorWithExistingCharacter(columbina);
        ReactionResult thundercloudStrike = ReactionResult.transform(
                100.0,
                "Thundercloud Strike",
                ReactionResult.Kind.THUNDERCLOUD_STRIKE);

        columbina.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), sim);
        double beforeExpectedExtra = sim.getTotalDamage();
        columbina.onReaction(thundercloudStrike, columbina, sim.getCurrentTime(), sim);
        assertClose(33.0, sim.getTotalDamage() - beforeExpectedExtra, EPS,
                "Columbina should model the 33% Thundercloud proc as expected extra damage");

        sim.advanceTime(20.0 - sim.getCurrentTime());
        double beforeDomainBoundary = sim.getTotalDamage();
        columbina.onReaction(thundercloudStrike, columbina, sim.getCurrentTime(), sim);
        assertClose(33.0, sim.getTotalDamage() - beforeDomainBoundary, EPS,
                "Columbina expected Thundercloud extra should apply at the domain expiry boundary");

        sim.advanceTime(0.01);
        double afterDomainExpiry = sim.getTotalDamage();
        columbina.onReaction(thundercloudStrike, columbina, sim.getCurrentTime(), sim);
        assertClose(afterDomainExpiry, sim.getTotalDamage(), EPS,
                "Columbina expected Thundercloud extra should not apply after the domain expires");

        model.character.Columbina rippleColumbina = new model.character.Columbina(new TestWeapon(), blankArtifact());
        CombatSimulator rippleSim = simulatorWithExistingCharacter(rippleColumbina);
        int[] cleanseHits = { 0 };
        rippleSim.addListener((actor, action, time) -> {
            if (action.getName().startsWith("Moondew Cleanse Hit")) {
                cleanseHits[0]++;
            }
        });
        ReactionResult lunarBloom = ReactionResult.lunar(
                0.0,
                ReactionResult.LunarType.BLOOM,
                Element.DENDRO,
                Element.DENDRO,
                true,
                false);

        rippleColumbina.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), rippleSim);
        rippleSim.advanceTime(25.0 - rippleSim.getCurrentTime());
        for (Element element : Element.values()) {
            rippleSim.getEnemy().setAura(element, 0.0);
        }
        rippleColumbina.onReaction(lunarBloom, rippleColumbina, rippleSim.getCurrentTime(), rippleSim);
        rippleColumbina.onAction(CharacterActionRequest.of(CharacterActionKey.CHARGE), rippleSim);
        assertEquals(3, cleanseHits[0],
                "Columbina should assume reactions are near Ripple at its expiry boundary");

        model.character.Columbina expiredRippleColumbina =
                new model.character.Columbina(new TestWeapon(), blankArtifact());
        CombatSimulator expiredRippleSim = simulatorWithExistingCharacter(expiredRippleColumbina);
        int[] expiredCleanseHits = { 0 };
        expiredRippleSim.addListener((actor, action, time) -> {
            if (action.getName().startsWith("Moondew Cleanse Hit")) {
                expiredCleanseHits[0]++;
            }
        });
        expiredRippleColumbina.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), expiredRippleSim);
        expiredRippleSim.advanceTime(25.01 - expiredRippleSim.getCurrentTime());
        for (Element element : Element.values()) {
            expiredRippleSim.getEnemy().setAura(element, 0.0);
        }
        expiredRippleColumbina.onReaction(
                lunarBloom,
                expiredRippleColumbina,
                expiredRippleSim.getCurrentTime(),
                expiredRippleSim);
        expiredRippleColumbina.onAction(CharacterActionRequest.of(CharacterActionKey.CHARGE), expiredRippleSim);
        assertEquals(0, expiredCleanseHits[0],
                "Columbina should not assume reactions are near Ripple after it expires");
    }

    private static void testAccuracyPhaseF_XingqiuOrbitalApplicationCadence() {
        model.character.Xingqiu inactiveXingqiu =
                new model.character.Xingqiu(new TestWeapon(), blankArtifact());
        CombatSimulator inactiveSim = simulatorWithExistingCharacter(inactiveXingqiu);
        inactiveSim.advanceTime(20.0);
        assertClose(0.0, inactiveSim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Xingqiu orbital Hydro should not apply before Raincutter is cast");

        model.character.Xingqiu xingqiu = new model.character.Xingqiu(new TestWeapon(), blankArtifact());
        CombatSimulator sim = simulatorWithExistingCharacter(xingqiu);
        List<Double> orbitalTimes = new ArrayList<>();
        sim.addReactionListener((result, source, time, activeSim) -> {
            if (result.getKind() == ReactionResult.Kind.VAPORIZE) {
                orbitalTimes.add(time);
            }
        });

        xingqiu.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), sim);
        double firstOrbitalTime = sim.getCurrentTime();
        sim.getEnemy().setAura(Element.PYRO, 1.0);
        sim.registerEvent(new simulation.event.SimpleTimerEvent(firstOrbitalTime + 2.249, 2.25) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                activeSim.getEnemy().setAura(Element.PYRO, 1.0);
                if (activeSim.getCurrentTime() >= firstOrbitalTime + 17.999) {
                    finish();
                }
            }
        });
        sim.advanceTime(18.01);

        assertEquals(9, orbitalTimes.size(),
                "Every Xingqiu orbital pulse should apply Hydro through the inclusive 18-second boundary");
        for (int i = 0; i < orbitalTimes.size(); i++) {
            assertClose(firstOrbitalTime + i * 2.25, orbitalTimes.get(i), EPS,
                    "Xingqiu orbital pulses should use the sourced 2.25-second cadence");
        }
        assertClose(0.0, sim.getTotalDamage(), EPS,
                "Xingqiu orbital contact pulses should deal no direct damage");

        int countAtExpiry = orbitalTimes.size();
        sim.advanceTime(10.0);
        assertEquals(countAtExpiry, orbitalTimes.size(),
                "Xingqiu orbital pulses should stop after the Raincutter event expires");

        model.character.Xingqiu gaugeXingqiu =
                new model.character.Xingqiu(new TestWeapon(), blankArtifact());
        CombatSimulator gaugeSim = simulatorWithExistingCharacter(gaugeXingqiu);
        gaugeXingqiu.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), gaugeSim);
        for (Element element : Element.values()) {
            gaugeSim.getEnemy().setAura(element, 0.0);
        }
        gaugeSim.advanceTime(0.0);
        assertClose(1.0, gaugeSim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Xingqiu orbital contact pulses should apply 1U Hydro");

        List<AttackAction> raincutterWave = xingqiu.getRaincutterAttack(0);
        assertEquals(2, raincutterWave.size(), "Xingqiu C6 Raincutter should start with a two-sword wave");
        AttackAction raincutterSword = raincutterWave.get(0);
        assertClose(1.09, raincutterSword.getDamagePercent(), EPS,
                "Xingqiu Raincutter swords should retain the configured level-12 multiplier");
        assertEquals(ActionType.BURST, raincutterSword.getActionType(),
                "Xingqiu Raincutter swords should remain Burst damage");
        assertEquals(ICDType.Standard, raincutterSword.getICDType(),
                "Xingqiu Raincutter swords should retain standard ICD");
        assertEquals(ICDTag.Xingqiu_Raincutter, raincutterSword.getICDTag(),
                "Xingqiu Raincutter swords should retain their separate ICD group");
    }

    private static void testAccuracyPhaseF_DamageHooksDispatchOnce() {
        CountingDamageWeapon weapon = new CountingDamageWeapon();
        CountingDamageArtifact artifact = new CountingDamageArtifact();
        TestCharacter character = testCharacter(Element.ELECTRO);
        character.setWeapon(weapon);
        character.setArtifacts(artifact);
        CombatSimulator sim = simulatorWith(character);

        AttackAction standard = new AttackAction(
                "Counting Standard Hit",
                1.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.ELECTRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        standard.setICD(ICDType.None, ICDTag.None, 0.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, standard);
        assertEquals(1, weapon.damageHookCount,
                "One standard hit should dispatch its weapon damage hook once");
        assertEquals(1, artifact.damageHookCount,
                "One standard hit should dispatch its artifact damage hook once");

        AttackAction lunar = new AttackAction(
                "Counting Lunar Hit",
                1.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.ELECTRO_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        lunar.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);
        lunar.setICD(ICDType.None, ICDTag.None, 0.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, lunar);
        assertEquals(2, weapon.damageHookCount,
                "One Lunar hit should dispatch its weapon damage hook once");
        assertEquals(2, artifact.damageHookCount,
                "One Lunar hit should dispatch its artifact damage hook once");

        AttackAction direct = new AttackAction(
                "Counting Direct Formula Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        mechanics.formula.DamageCalculator.calculateDamage(
                character,
                sim.getEnemy(),
                direct,
                sim.getApplicableBuffs(character),
                sim.getCurrentTime(),
                1.0,
                sim);
        assertEquals(3, weapon.damageHookCount,
                "A direct DamageCalculator caller should dispatch its weapon hook once");
        assertEquals(3, artifact.damageHookCount,
                "A direct DamageCalculator caller should dispatch its artifact hook once");
    }

    private static void testAccuracyPhaseF_SkywardSpineInjectedProcBoundaries() {
        boolean nullRejected = false;
        try {
            new model.weapon.SkywardSpine(null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "Skyward Spine should reject a null proc draw source");

        double[] draws = { 0.5, 0.499999, 0.25 };
        int[] drawIndex = { 0 };
        model.weapon.SkywardSpine weapon = new model.weapon.SkywardSpine(
                () -> draws[drawIndex[0]++]);
        TestCharacter character = testCharacter(Element.ELECTRO);
        character.setWeapon(weapon);
        CombatSimulator sim = simulatorWith(character);
        AttackAction normal = new AttackAction(
                "Skyward Spine Proc Test",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        AttackAction other = new AttackAction(
                "Skyward Spine Other Test",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);

        weapon.onDamage(character, normal, 0.0, sim);
        assertEquals(1, drawIndex[0], "A 0.5 draw should be consumed and fail the strict 50% chance gate");
        assertClose(0.0, sim.getTotalDamage(), EPS, "A 0.5 Skyward Spine draw should not proc");

        weapon.onDamage(character, normal, 0.1, sim);
        assertEquals(2, drawIndex[0], "A failed Skyward Spine draw should not start cooldown");
        double afterFirstProc = sim.getTotalDamage();
        assertTrue(afterFirstProc > 0.0, "A 0.499999 Skyward Spine draw should proc");

        weapon.onDamage(character, normal, 2.099, sim);
        assertEquals(2, drawIndex[0], "Skyward Spine should not draw at 1.999 seconds of cooldown");
        assertClose(afterFirstProc, sim.getTotalDamage(), EPS,
                "Skyward Spine should not deal another Vacuum Blade hit before cooldown");

        weapon.onDamage(character, other, 2.1, sim);
        assertEquals(2, drawIndex[0], "OTHER follow-ups should not consume Skyward Spine proc draws");

        weapon.onDamage(character, normal, 2.1, sim);
        assertEquals(3, drawIndex[0], "Skyward Spine should draw at exactly 2.0 seconds after a proc");
        assertTrue(sim.getTotalDamage() > afterFirstProc,
                "An eligible successful draw should deal a second Vacuum Blade hit");

        double firstSequenceDamage = sim.getTotalDamage();
        int[] repeatedDrawIndex = { 0 };
        model.weapon.SkywardSpine repeatedWeapon = new model.weapon.SkywardSpine(
                () -> draws[repeatedDrawIndex[0]++]);
        TestCharacter repeatedCharacter = testCharacter(Element.ELECTRO);
        repeatedCharacter.setWeapon(repeatedWeapon);
        CombatSimulator repeatedSim = simulatorWith(repeatedCharacter);
        repeatedWeapon.onDamage(repeatedCharacter, normal, 0.0, repeatedSim);
        repeatedWeapon.onDamage(repeatedCharacter, normal, 0.1, repeatedSim);
        repeatedWeapon.onDamage(repeatedCharacter, normal, 2.099, repeatedSim);
        repeatedWeapon.onDamage(repeatedCharacter, other, 2.1, repeatedSim);
        repeatedWeapon.onDamage(repeatedCharacter, normal, 2.1, repeatedSim);
        assertEquals(drawIndex[0], repeatedDrawIndex[0],
                "Identical Skyward Spine sequences should consume the same number of draws");
        assertClose(firstSequenceDamage, repeatedSim.getTotalDamage(), EPS,
                "Identical Skyward Spine sequences should produce identical proc damage");
    }

    private static void testAccuracyPhaseF_ArtifactLunarReactionBuffRegression() {
        TestCharacter owner = testCharacter(Element.ELECTRO).asLunar();
        owner.setArtifacts(new model.artifact.NightOfTheSkysUnveiling());
        CombatSimulator sim = simulatorWith(owner);
        double beforeCrit = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.CRIT_RATE);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Artifact Lunar-Charged trigger", Element.ELECTRO));
        double afterCrit = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.CRIT_RATE);
        assertClose(beforeCrit + 0.15, afterCrit, EPS,
                "Night of the Sky's Unveiling should grant on-field Lunar reaction CRIT Rate");
    }

    private static void testAccuracyPhaseF_WeaponReactionBonusRegression() {
        TestCharacter owner = testCharacter(Element.ANEMO);
        owner.setWeapon(new model.weapon.SunnyMorningSleepIn());
        CombatSimulator sim = simulatorWith(owner);
        sim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL));
        double beforeEm = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.ELEMENTAL_MASTERY);
        sim.getEnemy().setAura(Element.PYRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Sunny swirl trigger", Element.ANEMO));
        double afterEm = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.ELEMENTAL_MASTERY);
        assertClose(beforeEm + 120.0, afterEm, EPS,
                "Sunny Morning Sleep-In should grant EM only after owner-triggered Swirl");
    }

    private static void testAccuracyPhaseF_SucroseNoIcdApplicationContract() {
        model.character.Sucrose skillSucrose = new model.character.Sucrose(new TestWeapon(), blankArtifact());
        CombatSimulator skillSim = simulatorWithExistingCharacter(skillSucrose);
        List<AttackAction> skillActions = new ArrayList<>();
        skillSim.addListener((actor, action, time) -> skillActions.add(action));
        skillSim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL));

        AttackAction skill = findAction(skillActions, "Astable Anemohypostasis Creation - 6308");
        assertEquals(ICDType.None, skill.getICDType(), "Sucrose Skill should have no ICD");
        assertEquals(ICDTag.ElementalSkill, skill.getICDTag(), "Sucrose Skill should retain a typed Skill tag");
        assertClose(1.0, skill.getGaugeUnits(), EPS, "Sucrose Skill should apply 1U Anemo");

        model.character.Sucrose burstSucrose = new model.character.Sucrose(new TestWeapon(), blankArtifact());
        CombatSimulator burstSim = simulatorWithExistingCharacter(burstSucrose);
        List<AttackAction> burstActions = new ArrayList<>();
        List<ReactionResult.Kind> burstReactions = captureReactionKinds(burstSim);
        burstSim.addListener((actor, action, time) -> burstActions.add(action));
        burstSim.getEnemy().setAura(Element.PYRO, 4.0, burstSim.getCurrentTime());
        burstSim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.BURST));

        AttackAction cast = findAction(burstActions, "Forbidden Creation - Isomer 75 (Cast)");
        assertEquals(ICDType.None, cast.getICDType(), "Sucrose Burst cast should not enter an ICD group");
        assertClose(0.0, cast.getGaugeUnits(), EPS, "Sucrose Burst cast should not apply Anemo");
        assertEquals(0, burstReactions.size(), "Sucrose Burst cast should not trigger a reaction");

        burstSim.getEnemy().setAura(Element.PYRO, 4.0, burstSim.getCurrentTime());
        burstSim.advanceTime(2.01);
        assertEquals(1, countReactions(burstReactions, ReactionResult.Kind.SWIRL),
                "First Sucrose Burst tick should apply Anemo without ICD");
        burstSim.getEnemy().setAura(Element.PYRO, 4.0, burstSim.getCurrentTime());
        burstSim.advanceTime(2.0);
        assertEquals(2, countReactions(burstReactions, ReactionResult.Kind.SWIRL),
                "Consecutive Sucrose Burst ticks should each apply Anemo without ICD");

        model.character.Sucrose absorbSucrose = new model.character.Sucrose(new TestWeapon(), blankArtifact());
        CombatSimulator absorbSim = simulatorWithExistingCharacter(absorbSucrose);
        List<AttackAction> absorbActions = new ArrayList<>();
        absorbSim.addListener((actor, action, time) -> absorbActions.add(action));
        absorbSim.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.SWIRL) {
                simulator.getEnemy().setAura(Element.HYDRO, 4.0, time);
            }
        });
        absorbSim.getEnemy().setAura(Element.PYRO, 4.0, absorbSim.getCurrentTime());
        absorbSim.performAction(CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.BURST));
        absorbSim.advanceTime(2.01);

        AttackAction absorbed = findAction(absorbActions, "Forbidden Creation - Isomer 75 (Absorb)");
        assertEquals(ICDType.None, absorbed.getICDType(), "Sucrose absorbed Burst damage should have no ICD");
        assertEquals(ICDTag.ElementalBurst, absorbed.getICDTag(),
                "Sucrose absorbed Burst damage should retain a typed Burst tag");
        assertClose(1.0, absorbed.getGaugeUnits(), EPS, "Sucrose absorbed Burst damage should apply 1U");
    }

    private static void testAccuracyPhaseF_XianglingChiliPickupOptIn() {
        model.character.Xiangling noPickup = new model.character.Xiangling(
                new model.weapon.TheCatch(), blankArtifact());
        CombatSimulator noPickupSim = simulatorWithExistingCharacter(noPickup);
        noPickupSim.performAction(CharacterId.XIANGLING,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertTrue(noPickupSim.getTeamBuffs().stream()
                        .noneMatch(buff -> buff.getId() == BuffId.XIANGLING_CHILI),
                "Xiangling should not assume a chili pickup by default");

        model.character.Xiangling assumedPickup = new model.character.Xiangling(
                new model.weapon.TheCatch(), blankArtifact());
        assumedPickup.setChiliPickupAssumed(true);
        CombatSimulator assumedPickupSim = simulatorWithExistingCharacter(assumedPickup);
        assumedPickupSim.performAction(CharacterId.XIANGLING,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        mechanics.buff.Buff chili = assumedPickupSim.getTeamBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.XIANGLING_CHILI)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Opted-in Xiangling should register a chili buff"));

        StatsContainer beforePickup = new StatsContainer();
        chili.apply(beforePickup, 6.99);
        assertClose(0.0, beforePickup.get(StatType.ATK_PERCENT), EPS,
                "Assumed chili pickup should not apply before Guoba disappears");
        StatsContainer duringPickup = new StatsContainer();
        chili.apply(duringPickup, 7.0);
        assertClose(0.10, duringPickup.get(StatType.ATK_PERCENT), EPS,
                "Assumed chili pickup should apply after Guoba disappears");
        StatsContainer afterPickup = new StatsContainer();
        chili.apply(afterPickup, 17.0);
        assertClose(0.0, afterPickup.get(StatType.ATK_PERCENT), EPS,
                "Assumed chili pickup should expire after ten seconds");
    }

    private static List<ReactionResult.Kind> captureReactionKinds(CombatSimulator sim) {
        List<ReactionResult.Kind> kinds = new ArrayList<>();
        sim.addReactionListener((result, source, time, simulator) -> kinds.add(result.getKind()));
        return kinds;
    }

    private static int countReactions(List<ReactionResult.Kind> kinds, ReactionResult.Kind expected) {
        int count = 0;
        for (ReactionResult.Kind kind : kinds) {
            if (kind == expected) {
                count++;
            }
        }
        return count;
    }

    private static AttackAction findAction(List<AttackAction> actions, String name) {
        for (AttackAction action : actions) {
            if (name.equals(action.getName())) {
                return action;
            }
        }
        throw new AssertionError("Expected action not observed: " + name);
    }

    private static String captureStandardOutput(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static AttackAction reactionHit(String name, Element element) {
        AttackAction action = new AttackAction(name, 0.0, element, StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        return action;
    }

    private static AttackAction damageHit(String name, Element element, double multiplier) {
        AttackAction action = new AttackAction(name, multiplier, element, StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static AttackAction catalyzeDamageHit(String name, Element element) {
        AttackAction action = new AttackAction(name, 1.0, element, StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        return action;
    }

    private static AttackAction standardIcdHit(String name, Element element, ICDTag tag, double multiplier) {
        AttackAction action = new AttackAction(name, multiplier, element, StatType.BASE_ATK);
        action.setICD(ICDType.Standard, tag, 1.0);
        return action;
    }

    private static void addDendroCores(CombatSimulator sim, int count) {
        for (int i = 0; i < count; i++) {
            sim.addDendroCore(CharacterId.SUCROSE, expectedTransformative(2.0, Element.DENDRO, 0.0));
        }
    }

    private static TestCharacter testCharacter(Element element) {
        return new TestCharacter(element);
    }

    private static CombatSimulator simulatorWith(TestCharacter character) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(character);
        return sim;
    }

    private static CombatSimulator simulatorWithExistingCharacter(model.entity.Character character) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(character);
        return sim;
    }

    private static ArtifactSet blankArtifact() {
        return new ArtifactSet("Blank", new StatsContainer());
    }

    private static double expectedTransformative(double reactionMultiplier, Element damageElement, double em) {
        return expectedTransformative(reactionMultiplier, damageElement, em, 0.0);
    }

    private static double expectedTransformative(
            double reactionMultiplier, Element damageElement, double em, double reactionBonus) {
        double base = ReactionCalculator.calculateTransformativeDamage(90, em, reactionMultiplier, reactionBonus);
        double res = ResistanceCalculator.calculateResMulti(0.10, 0.0);
        return base * res;
    }

    private static double expectedStandardCatalyzeDamage(
            double additiveMultiplier, double dmgBonus, double critRate, double critDmg) {
        return expectedStandardCatalyzeDamageWithEm(additiveMultiplier, dmgBonus, critRate, critDmg, 0.0, 0.0);
    }

    private static double expectedStandardCatalyzeDamageWithEm(
            double additiveMultiplier, double dmgBonus, double critRate, double critDmg, double em, double reactionBonus) {
        double additive = ReactionCalculator.calculateAdditiveReactionDamage(90, em, additiveMultiplier, reactionBonus);
        double base = 1000.0 + additive;
        return base * (1.0 + dmgBonus) * (1.0 + Math.min(critRate, 1.0) * critDmg) * 0.5 * 0.9;
    }

    private static double expectedStandardDamage(double dmgBonus, double critRate, double critDmg) {
        return 1000.0 * (1.0 + dmgBonus) * (1.0 + Math.min(critRate, 1.0) * critDmg) * 0.5 * 0.9;
    }

    private static void assertClose(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(Element element) {
            this.name = "Reaction Tester";
            this.characterId = CharacterId.SUCROSE;
            this.element = element;
            this.weapon = new TestWeapon();
            this.artifacts = new ArtifactSet[0];
            this.baseStats.set(StatType.BASE_HP, 10000.0);
            this.baseStats.set(StatType.BASE_ATK, 1000.0);
            this.baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        private TestCharacter withStat(StatType statType, double value) {
            this.baseStats.set(statType, value);
            return this;
        }

        private TestCharacter asLunar() {
            this.lunar = true;
            return this;
        }

        private boolean lunar;

        @Override
        public boolean isLunarCharacter() {
            return lunar;
        }
    }

    private static final class TestBurstCharacter extends Character {
        private int burstCasts = 0;
        private final double energyCost;

        private TestBurstCharacter(double energyCost) {
            this.name = "Burst Gate Tester";
            this.characterId = CharacterId.SUCROSE;
            this.element = Element.ANEMO;
            this.weapon = new TestWeapon();
            this.artifacts = new ArtifactSet[0];
            this.energyCost = energyCost;
            this.baseStats.set(StatType.BASE_HP, 10000.0);
            this.baseStats.set(StatType.BASE_ATK, 1000.0);
            this.baseStats.set(StatType.BASE_DEF, 700.0);
            setBurstCD(0.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return energyCost;
        }

        @Override
        public void onAction(CharacterActionRequest request, CombatSimulator sim) {
            if (request.getKey() == CharacterActionKey.BURST) {
                burstCasts++;
                markBurstUsed(sim.getCurrentTime());
            }
        }
    }

    private static final class TestWeapon extends Weapon {
        private TestWeapon() {
            super("Test Weapon", new StatsContainer());
        }
    }

    /** Counts weapon damage-hook dispatches without adding combat behavior. */
    private static final class CountingDamageWeapon extends Weapon
            implements model.entity.DamageTriggeredWeaponEffect {
        private int damageHookCount;

        private CountingDamageWeapon() {
            super("Counting Damage Weapon", new StatsContainer());
        }

        @Override
        public void onDamage(
                Character user,
                AttackAction action,
                double currentTime,
                CombatSimulator sim) {
            damageHookCount++;
        }
    }

    /** Counts artifact damage-hook dispatches without adding combat behavior. */
    private static final class CountingDamageArtifact extends ArtifactSet
            implements model.entity.DamageTriggeredArtifactEffect {
        private int damageHookCount;

        private CountingDamageArtifact() {
            super("Counting Damage Artifact", new StatsContainer());
        }

        @Override
        public void onDamage(
                CombatSimulator sim,
                AttackAction action,
                double damage,
                Character owner) {
            damageHookCount++;
        }
    }
}
