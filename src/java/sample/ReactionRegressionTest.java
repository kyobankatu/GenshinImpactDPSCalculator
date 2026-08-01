package sample;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import mechanics.analysis.EnergyAnalyzer;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.element.ResonanceManager;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;
import mechanics.formula.DamageCalculator;
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
import simulation.event.PeriodicDamageEvent;

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
        testAccuracyPhaseF_FlinsSkillApplicationContract();
        testAccuracyPhaseF_FlinsThundercloudConditionalHits();
        testAccuracyPhaseF_FlinsSymphonyZeroGaugeContract();
        testAccuracyPhaseF_IneffaOverclockZeroGaugeContract();
        testAccuracyPhaseF_IneffaSkillNoIcdApplicationContract();
        testAccuracyPhaseF_IneffaBirgittaSummonLifecycle();
        testAccuracyPhaseF_BurstEnergyGateAndFlinsSpecialCost();
        testAccuracyPhaseF_PartySizeParticleEnergyMultipliers();
        testAccuracyPhaseF_ImpetuousWindsCooldownSnapshot();
        testAccuracyPhaseF_TimingAwareEnergyAnalysis();
        testAccuracyPhaseF_ArtifactOptimizerRejectsUnreachableEr();
        testAccuracyPhaseF_ColumbinaGravityAndDewRegression();
        testAccuracyPhaseF_ColumbinaStandInBoundaries();
        testAccuracyPhaseF_ArtifactTeamBuffProviderRouting();
        testAccuracyPhaseF_SilkenMoonsSerenadeDynamicBonus();
        testAccuracyPhaseF_AscendantBlessingExpiryReplacement();
        testAccuracyPhaseF_ArtifactLunarReactionBuffRegression();
        testAccuracyPhaseF_ViridescentVenererRefreshContract();
        testAccuracyPhaseF_NoblesseObligeRefreshContract();
        testAccuracyPhaseF_WeaponReactionBonusRegression();
        testAccuracyPhaseF_DragonsBaneTargetAuraContract();
        testAccuracyPhaseF_DendroResonanceReactionEmContract();
        testAccuracyPhaseF_CryoResonanceConditionalCritContract();
        testAccuracyPhaseF_ElectroResonanceTypedTriggerContract();
        testAccuracyPhaseF_SucroseNoIcdApplicationContract();
        testAccuracyPhaseF_RaidenCastAndMusouIcdContract();
        testAccuracyPhaseF_RaidenEyeBuffRefreshContract();
        testAccuracyPhaseF_PeriodicCancellationAndRaidenEyeDamageTrigger();
        testAccuracyPhaseF_BennettSkillAndBurstApplicationContract();
        testAccuracyPhaseF_XianglingGuobaNoIcdApplicationContract();
        testAccuracyPhaseF_XianglingChiliPickupOptIn();
        testAccuracyPhaseF_XingqiuSkillNoIcdApplicationContract();
        testAccuracyPhaseF_XingqiuOrbitalApplicationCadence();
        testAccuracyPhaseF_DamageHooksDispatchOnce();
        testAccuracyPhaseF_SkywardSpineInjectedProcBoundaries();
        testAccuracyPhaseF_FavoniusInjectedProcBoundaries();
        testAccuracyPhaseF_SacrificialSwordProcBoundaries();
        testAccuracyPhaseF_WanderingEvenstarTimedSnapshot();
        testAccuracyPhaseF_ColumbinaMoondriftInjectedDrawBoundaries();
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
        List<AttackAction> noCloudActions = new ArrayList<>();
        List<Double> noCloudDelayedHitTimes = new ArrayList<>();
        noCloud.addListener((actor, action, time) -> {
            noCloudActions.add(action);
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

        AttackAction initial = findAction(noCloudActions, "Cometh the Night (Initial)");
        AttackAction middle = findAction(noCloudActions, "Cometh the Night (Middle)");
        AttackAction fin = findAction(noCloudActions, "Cometh the Night (Final)");
        assertEquals(ICDType.None, initial.getICDType(), "Flins standard Burst initial hit should have no ICD");
        assertEquals(ICDTag.ElementalBurst, initial.getICDTag(),
                "Flins standard Burst initial hit should retain Burst tag");
        assertClose(1.0, initial.getGaugeUnits(), EPS, "Flins standard Burst initial hit should apply 1U");
        assertEquals(ICDType.None, middle.getICDType(), "Flins standard Burst middle hits should have no ICD");
        assertEquals(ICDTag.ElementalBurst, middle.getICDTag(),
                "Flins standard Burst middle hits should retain Burst tag");
        assertClose(0.0, middle.getGaugeUnits(), EPS, "Flins standard Burst middle hits should apply 0U");
        assertEquals(ICDType.None, fin.getICDType(), "Flins standard Burst final hit should have no ICD");
        assertEquals(ICDTag.ElementalBurst, fin.getICDTag(),
                "Flins standard Burst final hit should retain Burst tag");
        assertClose(0.0, fin.getGaugeUnits(), EPS, "Flins standard Burst final hit should apply 0U");
        assertTrue(middle.getDamagePercent() > 0.0 && fin.getDamagePercent() > 0.0,
                "Flins standard Burst delayed hits should retain positive direct damage");

        model.character.Flins cloudFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator cloud = simulatorWithExistingCharacter(cloudFlins);
        int[] cloudMiddleHits = { 0 };
        List<AttackAction> cloudActions = new ArrayList<>();
        cloud.addListener((actor, action, time) -> {
            if ("Cometh the Night (Middle)".equals(action.getName())) {
                cloudMiddleHits[0]++;
                cloudActions.add(action);
            }
        });
        cloud.setThundercloudEndTime(cloud.getCurrentTime() + 30.0);
        assertTrue(cloud.isThundercloudActive(), "Test setup should activate simulator Thundercloud state");
        cloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), cloud);
        cloud.advanceTime(10.0);
        assertTrue(cloudMiddleHits[0] == noCloudMiddleHits[0] + 2,
                "Flins standard burst should add conditional middle hits while Thundercloud is active"
                        + " (noCloud=" + noCloudMiddleHits[0] + ", cloud=" + cloudMiddleHits[0] + ")");
        assertTrue(cloudActions.stream().allMatch(action -> action.getICDType() == ICDType.None
                && action.getICDTag() == ICDTag.ElementalBurst && action.getGaugeUnits() == 0.0),
                "All Thundercloud-conditional middle hits should retain 0U/no-ICD metadata");

        model.character.Flins reactingFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator reactingSim = simulatorWithExistingCharacter(reactingFlins);
        int[] elementalReactions = { 0 };
        reactingSim.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.LUNAR_CHARGED && result.getTransformDamage() > 0.0) {
                elementalReactions[0]++;
            }
        });
        reactingSim.getEnemy().setAura(Element.HYDRO, 4.0, reactingSim.getCurrentTime());
        reactingFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), reactingSim);
        reactingSim.advanceTime(10.0);
        assertEquals(1, elementalReactions[0],
                "Only the 1U initial standard-Burst hit should trigger an elemental reaction");
    }

    private static void testAccuracyPhaseF_FlinsSkillApplicationContract() {
        model.character.Flins flins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator sim = simulatorWithExistingCharacter(flins);
        List<AttackAction> actions = new ArrayList<>();
        int[] elementalReactions = { 0 };
        sim.addListener((actor, action, time) -> actions.add(action));
        sim.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.LUNAR_CHARGED && result.getTransformDamage() > 0.0) {
                elementalReactions[0]++;
            }
        });
        sim.getEnemy().setAura(Element.HYDRO, 4.0, sim.getCurrentTime());
        double activationStart = sim.getCurrentTime();
        double damageBeforeActivation = sim.getTotalDamage();

        flins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), sim);

        AttackAction activation = findAction(actions, "Enter Form");
        assertEquals(Element.ELECTRO, activation.getElement(), "Flins form activation should be an Electro hit");
        assertEquals(ActionType.SKILL, activation.getActionType(), "Flins form activation should retain Skill typing");
        assertEquals(ICDType.None, activation.getICDType(), "Flins form activation should have no ICD");
        assertEquals(ICDTag.ElementalSkill, activation.getICDTag(),
                "Flins form activation should retain Skill tag");
        assertClose(0.0, activation.getGaugeUnits(), EPS, "Flins form activation should apply 0U Electro");
        assertClose(0.3, sim.getCurrentTime() - activationStart, EPS,
                "Flins form activation should retain its 0.3-second duration");
        assertClose(damageBeforeActivation, sim.getTotalDamage(), EPS,
                "Flins form activation should remain damageless");
        assertEquals(0, elementalReactions[0], "Flins 0U form activation should not trigger a reaction");
        assertTrue(sim.getEnemy().getAuraUnits(Element.HYDRO, sim.getCurrentTime()) > 0.0,
                "Flins 0U form activation should preserve Hydro aura");
        assertTrue(flins.isFormActive(sim.getCurrentTime()), "Flins form activation should enter Manifest Flame");

        elementalReactions[0] = 0;
        sim.getEnemy().setAura(Element.HYDRO, 4.0, sim.getCurrentTime());
        double spearstormStart = sim.getCurrentTime();
        double damageBeforeSpearstorm = sim.getTotalDamage();

        flins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), sim);

        AttackAction spearstorm = findAction(actions, "Northland Spearstorm");
        assertEquals(Element.ELECTRO, spearstorm.getElement(), "Flins Spearstorm should remain Electro");
        assertEquals(ActionType.SKILL, spearstorm.getActionType(), "Flins Spearstorm should retain Skill typing");
        assertEquals(ICDType.None, spearstorm.getICDType(), "Flins Spearstorm should have no ICD");
        assertEquals(ICDTag.ElementalSkill, spearstorm.getICDTag(), "Flins Spearstorm should retain Skill tag");
        assertClose(1.0, spearstorm.getGaugeUnits(), EPS, "Flins Spearstorm should apply 1U Electro");
        assertClose(0.3, sim.getCurrentTime() - spearstormStart, EPS,
                "Flins Spearstorm should retain its 0.3-second duration");
        assertEquals(1, elementalReactions[0], "Flins Spearstorm should trigger one elemental reaction on Hydro");
        assertTrue(sim.getTotalDamage() > damageBeforeSpearstorm,
                "Flins Spearstorm should retain positive direct damage");
        assertTrue(flins.isThunderousSymphonyActive(sim.getCurrentTime()),
                "Flins Spearstorm should activate Thunderous Symphony state");
    }

    private static void testAccuracyPhaseF_FlinsSymphonyZeroGaugeContract() {
        model.character.Flins cloudFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator cloudSim = simulatorWithExistingCharacter(cloudFlins);
        List<AttackAction> cloudActions = new ArrayList<>();
        List<Double> symphonyTimes = new ArrayList<>();
        List<ReactionResult.Kind> cloudReactions = captureReactionKinds(cloudSim);
        cloudSim.addListener((actor, action, time) -> {
            cloudActions.add(action);
            if (action.getName().startsWith("Thunderous Symphony")) {
                symphonyTimes.add(time);
            }
        });
        cloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), cloudSim);
        cloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), cloudSim);
        cloudSim.setThundercloudEndTime(cloudSim.getCurrentTime() + 30.0);
        cloudSim.getEnemy().setAura(Element.HYDRO, 4.0, cloudSim.getCurrentTime());
        cloudReactions.clear();
        double damageBeforeBurst = cloudSim.getTotalDamage();

        cloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), cloudSim);

        AttackAction main = findAction(cloudActions, "Thunderous Symphony");
        AttackAction additional = findAction(cloudActions, "Thunderous Symphony (Additional)");
        assertEquals(ICDType.None, main.getICDType(), "Flins Symphony main hit should have no ICD");
        assertEquals(ICDType.None, additional.getICDType(), "Flins Symphony Additional hit should have no ICD");
        assertEquals(ICDTag.ElementalBurst, main.getICDTag(), "Flins Symphony main hit should retain Burst tag");
        assertEquals(ICDTag.ElementalBurst, additional.getICDTag(),
                "Flins Symphony Additional hit should retain Burst tag");
        assertClose(0.0, main.getGaugeUnits(), EPS, "Flins Symphony main hit should apply 0U Electro");
        assertClose(0.0, additional.getGaugeUnits(), EPS,
                "Flins Symphony Additional hit should apply 0U Electro");
        assertEquals(ActionType.BURST, main.getActionType(), "Flins Symphony main hit should retain Burst typing");
        assertEquals(ActionType.BURST, additional.getActionType(),
                "Flins Symphony Additional hit should retain Burst typing");
        assertTrue(main.isLunarConsidered() && additional.isLunarConsidered(),
                "Flins Symphony hits should retain direct Lunar-Charged consideration");
        assertEquals(2, symphonyTimes.size(), "Active Thundercloud should produce main and Additional hits");
        assertClose(0.1, symphonyTimes.get(1) - symphonyTimes.get(0), EPS,
                "Flins Symphony Additional hit should retain its 0.1-second delay");
        assertEquals(0, cloudReactions.size(), "Zero-gauge Symphony hits should not trigger elemental reactions");
        assertTrue(cloudSim.getEnemy().getAuraUnits(Element.HYDRO, cloudSim.getCurrentTime()) > 0.0,
                "Zero-gauge Symphony hits should preserve the existing Hydro aura");
        assertTrue(cloudSim.getTotalDamage() > damageBeforeBurst,
                "Zero-gauge Symphony hits should retain positive direct damage");

        model.character.Flins noCloudFlins = new model.character.Flins(new TestWeapon(), blankArtifact());
        CombatSimulator noCloudSim = simulatorWithExistingCharacter(noCloudFlins);
        List<AttackAction> noCloudActions = new ArrayList<>();
        noCloudSim.addListener((actor, action, time) -> noCloudActions.add(action));
        noCloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), noCloudSim);
        noCloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), noCloudSim);

        noCloudFlins.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), noCloudSim);

        assertTrue(noCloudActions.stream().anyMatch(action -> "Thunderous Symphony".equals(action.getName())),
                "Symphony state should produce the main hit without Thundercloud");
        assertTrue(noCloudActions.stream()
                .noneMatch(action -> "Thunderous Symphony (Additional)".equals(action.getName())),
                "Symphony without Thundercloud should not produce the Additional hit");
    }

    private static void testAccuracyPhaseF_IneffaOverclockZeroGaugeContract() {
        model.character.Ineffa inactiveIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator inactiveSim = simulatorWithExistingCharacter(inactiveIneffa);
        List<AttackAction> inactiveActions = new ArrayList<>();
        inactiveSim.addListener((actor, action, time) -> inactiveActions.add(action));
        inactiveIneffa.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), inactiveSim);
        inactiveSim.advanceTime(2.01);
        assertTrue(inactiveActions.stream().noneMatch(action -> "Overclock (Lunar)".equals(action.getName())),
                "Ineffa should not emit Overclock while Thundercloud is inactive");

        model.character.Ineffa activeIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator activeSim = simulatorWithExistingCharacter(activeIneffa);
        List<AttackAction> activeActions = new ArrayList<>();
        activeSim.addListener((actor, action, time) -> activeActions.add(action));
        activeSim.setThundercloudEndTime(activeSim.getCurrentTime() + 30.0);
        activeIneffa.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), activeSim);
        double damageBeforeTick = activeSim.getTotalDamage();
        activeSim.advanceTime(2.01);

        AttackAction overclock = findAction(activeActions, "Overclock (Lunar)");
        assertEquals(ICDType.None, overclock.getICDType(), "Ineffa Overclock should not enter an ICD group");
        assertEquals(ICDTag.None, overclock.getICDTag(), "Ineffa Overclock should use the neutral ICD tag");
        assertClose(0.0, overclock.getGaugeUnits(), EPS, "Ineffa Overclock should apply 0U Electro");
        assertEquals(AttackAction.LunarReactionType.CHARGED, overclock.getLunarReactionType(),
                "Ineffa Overclock should retain direct Lunar-Charged classification");
        assertTrue(overclock.getDamagePercent() > 0.0 && activeSim.getTotalDamage() > damageBeforeTick,
                "Ineffa Overclock should retain positive direct damage");

        model.character.Ineffa auraIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator auraSim = simulatorWithExistingCharacter(auraIneffa);
        auraSim.getEnemy().setAura(Element.HYDRO, 1.0, auraSim.getCurrentTime());
        auraSim.performActionWithoutTimeAdvance(CharacterId.INEFFA, overclock);
        assertClose(1.0, auraSim.getEnemy().getAuraUnits(Element.HYDRO, auraSim.getCurrentTime()), EPS,
                "Zero-gauge Overclock should not consume Hydro or trigger elemental application");

        model.character.Ineffa expiredIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator expiredSim = simulatorWithExistingCharacter(expiredIneffa);
        List<AttackAction> expiredActions = new ArrayList<>();
        expiredSim.addListener((actor, action, time) -> expiredActions.add(action));
        expiredIneffa.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), expiredSim);
        expiredSim.setThundercloudEndTime(expiredSim.getCurrentTime() + 2.0);
        expiredSim.advanceTime(2.0);
        assertTrue(expiredActions.stream().noneMatch(action -> "Overclock (Lunar)".equals(action.getName())),
                "Ineffa should not emit Overclock at the Thundercloud expiry boundary");
    }

    private static void testAccuracyPhaseF_IneffaSkillNoIcdApplicationContract() {
        model.character.Ineffa reactingIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator reactingSim = simulatorWithExistingCharacter(reactingIneffa);
        List<AttackAction> actions = new ArrayList<>();
        int[] elementalReactions = { 0 };
        reactingSim.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.LUNAR_CHARGED && result.getTransformDamage() > 0.0) {
                elementalReactions[0]++;
            }
        });
        reactingSim.addListener((actor, action, time) -> actions.add(action));
        reactingSim.getEnemy().setAura(Element.HYDRO, 1.0, reactingSim.getCurrentTime());
        reactingIneffa.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), reactingSim);

        AttackAction skill = findAction(actions, "Enhanced Cleaning Module");
        assertEquals(ICDType.None, skill.getICDType(), "Ineffa Skill should have no ICD");
        assertEquals(ICDTag.ElementalSkill, skill.getICDTag(), "Ineffa Skill should retain a typed Skill tag");
        assertClose(1.0, skill.getGaugeUnits(), EPS, "Ineffa Skill should apply 1U Electro");

        elementalReactions[0] = 0;
        reactingSim.setThundercloudEndTime(reactingSim.getCurrentTime());
        reactingSim.getEnemy().setAura(Element.HYDRO, 1.0, reactingSim.getCurrentTime());
        reactingSim.advanceTime(2.01);
        assertEquals(1, elementalReactions[0], "First Birgitta tick should apply Electro without ICD");

        elementalReactions[0] = 0;
        reactingSim.setThundercloudEndTime(reactingSim.getCurrentTime());
        reactingSim.getEnemy().setAura(Element.HYDRO, 1.0, reactingSim.getCurrentTime());
        reactingSim.advanceTime(2.0);
        assertEquals(1, elementalReactions[0],
                "Second Birgitta tick at the 2-second cadence should not be ICD-suppressed");

        model.character.Ineffa noAuraIneffa = new model.character.Ineffa(new TestWeapon(), blankArtifact());
        CombatSimulator noAuraSim = simulatorWithExistingCharacter(noAuraIneffa);
        List<ReactionResult.Kind> noAuraReactions = captureReactionKinds(noAuraSim);
        noAuraIneffa.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), noAuraSim);
        double damageBeforeTick = noAuraSim.getTotalDamage();
        noAuraSim.setThundercloudEndTime(noAuraSim.getCurrentTime());
        noAuraSim.advanceTime(2.01);
        assertEquals(0, noAuraReactions.size(), "Birgitta against no reactive aura should not create a reaction");
        assertTrue(noAuraSim.getTotalDamage() > damageBeforeTick,
                "Birgitta against no reactive aura should retain direct damage");
    }

    private static void testAccuracyPhaseF_IneffaBirgittaSummonLifecycle() {
        RecordingDamageWeapon lifetimeWeapon = new RecordingDamageWeapon("Birgitta Discharge");
        model.character.Ineffa lifetimeIneffa = new model.character.Ineffa(
                lifetimeWeapon, blankArtifact());
        CombatSimulator lifetimeSim = simulatorWithExistingCharacter(lifetimeIneffa);
        lifetimeSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        double skillEndTime = lifetimeSim.getCurrentTime();
        lifetimeSim.advanceTime(22.01);

        assertEquals(10, lifetimeWeapon.actions.size(),
                "One Birgitta summon should deal ten Discharge attacks");
        for (int i = 0; i < lifetimeWeapon.times.size(); i++) {
            assertClose(skillEndTime + (i + 1) * 2.0, lifetimeWeapon.times.get(i), EPS,
                    "Birgitta should attack every two seconds through +20 seconds");
        }

        RecordingDamageWeapon burstWeapon = new RecordingDamageWeapon("Birgitta Discharge");
        model.character.Ineffa burstIneffa = new model.character.Ineffa(burstWeapon, blankArtifact());
        CombatSimulator burstSim = simulatorWithExistingCharacter(burstIneffa);
        captureStandardOutput(() -> burstSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.BURST)));
        double burstEndTime = burstSim.getCurrentTime();
        burstSim.advanceTime(2.01);
        assertEquals(1, burstWeapon.actions.size(), "Ineffa Burst should summon Birgitta");
        assertClose(burstEndTime + 2.0, burstWeapon.times.get(0), EPS,
                "Burst-summoned Birgitta should use the two-second cadence");

        RecordingDamageWeapon skillRefreshWeapon = new RecordingDamageWeapon("Birgitta Discharge");
        model.character.Ineffa skillRefreshIneffa = new model.character.Ineffa(
                skillRefreshWeapon, blankArtifact());
        CombatSimulator skillRefreshSim = simulatorWithExistingCharacter(skillRefreshIneffa);
        skillRefreshSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        skillRefreshSim.advanceTime(15.4);
        skillRefreshSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        double skillRefreshTime = skillRefreshSim.getCurrentTime();
        skillRefreshSim.advanceTime(4.01);
        assertEquals(2, countTimesAfter(skillRefreshWeapon.times, skillRefreshTime),
                "Skill refresh should leave only one Birgitta stream");

        RecordingDamageWeapon burstRefreshWeapon = new RecordingDamageWeapon("Birgitta Discharge");
        model.character.Ineffa burstRefreshIneffa = new model.character.Ineffa(
                burstRefreshWeapon, blankArtifact());
        CombatSimulator burstRefreshSim = simulatorWithExistingCharacter(burstRefreshIneffa);
        burstRefreshSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        captureStandardOutput(() -> burstRefreshSim.performAction(CharacterId.INEFFA,
                CharacterActionRequest.of(CharacterActionKey.BURST)));
        double burstRefreshTime = burstRefreshSim.getCurrentTime();
        burstRefreshSim.advanceTime(4.01);
        assertEquals(2, countTimesAfter(burstRefreshWeapon.times, burstRefreshTime),
                "Burst refresh should leave only one Birgitta stream");
    }

    private static void testAccuracyPhaseF_BurstEnergyGateAndFlinsSpecialCost() {
        TestBurstCharacter gated = new TestBurstCharacter(60.0);
        CombatSimulator gateSim = simulatorWithExistingCharacter(gated);
        gated.restoreCurrentEnergy(59.8);

        gateSim.setLoggingEnabled(true);
        String skippedLog = captureStandardOutput(() -> gateSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.BURST)));
        gateSim.setLoggingEnabled(false);

        assertEquals(0, gated.burstCasts, "Burst should not execute below current energy cost");
        assertClose(59.8, gated.getCurrentEnergy(), EPS, "Skipped burst should not consume energy");
        assertClose(60.0, gated.getMissedBurstCost(), EPS, "Skipped burst cost should feed ER calibration");
        assertTrue(skippedLog.contains("(59.8/60.0)"),
                "Insufficient-energy diagnostics should not round a deficit to 60/60");
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

    private static void testAccuracyPhaseF_PartySizeParticleEnergyMultipliers() {
        CharacterId[] characterIds = {
            CharacterId.SUCROSE,
            CharacterId.XIANGLING,
            CharacterId.XINGQIU,
            CharacterId.BENNETT,
            CharacterId.COLUMBINA
        };
        double[] expectedOffFieldEnergy = { 1.6, 1.4, 1.2, 1.2 };

        for (int partySize = 2; partySize <= 5; partySize++) {
            CombatSimulator sim = new CombatSimulator();
            sim.setLoggingEnabled(false);
            List<TestCharacter> members = new ArrayList<>();
            for (int index = 0; index < partySize; index++) {
                TestCharacter member = testCharacter(Element.ANEMO, characterIds[index]);
                members.add(member);
                sim.addCharacter(member);
            }

            EnergyManager.distributeParticles(
                    Element.PHYSICAL, 1.0, ParticleType.PARTICLE, sim);

            assertClose(2.0, members.get(0).getTotalParticleEnergy(), EPS,
                    "Active neutral particle energy should not depend on party size");
            for (int index = 1; index < members.size(); index++) {
                assertClose(
                        expectedOffFieldEnergy[partySize - 2],
                        members.get(index).getTotalParticleEnergy(),
                        EPS,
                        "Off-field neutral particle energy should follow party size");
            }
        }

        CombatSimulator elementalSim = new CombatSimulator();
        elementalSim.setLoggingEnabled(false);
        TestCharacter elementalActive = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        TestCharacter elementalOffField = testCharacter(Element.ANEMO, CharacterId.XIANGLING);
        TestCharacter elementalThird = testCharacter(Element.ANEMO, CharacterId.XINGQIU);
        elementalSim.addCharacter(elementalActive);
        elementalSim.addCharacter(elementalOffField);
        elementalSim.addCharacter(elementalThird);
        EnergyManager.distributeParticles(Element.ANEMO, 1.0, ParticleType.PARTICLE, elementalSim);
        assertClose(3.0, elementalActive.getTotalParticleEnergy(), EPS,
                "Active same-element particle should retain its base value");
        assertClose(2.1, elementalOffField.getTotalParticleEnergy(), EPS,
                "Three-member off-field multiplier should compose with same-element value");

        CombatSimulator orbSim = new CombatSimulator();
        orbSim.setLoggingEnabled(false);
        TestCharacter orbActive = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        TestCharacter orbOffField = testCharacter(Element.ANEMO, CharacterId.XIANGLING);
        orbSim.addCharacter(orbActive);
        orbSim.addCharacter(orbOffField);
        EnergyManager.distributeParticles(Element.PHYSICAL, 1.0, ParticleType.ORB, orbSim);
        assertClose(6.0, orbActive.getTotalParticleEnergy(), EPS,
                "Neutral orb should retain the active base value");
        assertClose(4.8, orbOffField.getTotalParticleEnergy(), EPS,
                "Two-member off-field multiplier should apply to neutral orbs");

        EnergyManager.distributeFlatEnergy(3.0, elementalSim);
        for (Character member : elementalSim.getPartyMembers()) {
            assertClose(3.0, member.getTotalFlatEnergy(), EPS,
                    "Flat energy should bypass party-size multipliers");
        }

        CombatSimulator emptySim = new CombatSimulator();
        emptySim.setLoggingEnabled(false);
        int[] emptyNotifications = { 0 };
        emptySim.addParticleListener((element, count, time) -> emptyNotifications[0]++);
        EnergyManager.distributeParticles(
                Element.PHYSICAL, 1.0, ParticleType.PARTICLE, emptySim);
        assertEquals(0, emptyNotifications[0],
                "Particle distribution without an active character should be a no-op");
    }

    private static void testAccuracyPhaseF_ImpetuousWindsCooldownSnapshot() {
        TestCharacter resonanceOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        resonanceOwner.setSkillCD(10.0);
        resonanceOwner.setBurstCD(20.0);
        TestCharacter resonanceAlly = testCharacter(Element.ANEMO, CharacterId.XIANGLING);
        CombatSimulator resonanceSim = new CombatSimulator();
        resonanceSim.setLoggingEnabled(false);
        resonanceSim.addCharacter(resonanceOwner);
        resonanceSim.addCharacter(resonanceAlly);
        ResonanceManager.applyResonances(resonanceSim);

        StatsContainer resonanceStats = new StatsContainer();
        for (Buff buff : resonanceSim.getApplicableBuffs(resonanceOwner)) {
            buff.apply(resonanceStats, 0.0);
        }
        assertClose(0.05, resonanceStats.get(StatType.CD_REDUCTION), EPS,
                "Two Anemo characters should activate Impetuous Winds");
        resonanceOwner.markSkillUsed(0.0, resonanceSim.getApplicableBuffs(resonanceOwner));
        resonanceOwner.markBurstUsed(0.0, resonanceSim.getApplicableBuffs(resonanceOwner));
        resonanceOwner.restoreCurrentEnergy(resonanceOwner.getMaxEnergy());
        assertTrue(!resonanceOwner.canSkill(9.499),
                "Reduced Skill should remain unavailable before 9.5 seconds");
        assertTrue(resonanceOwner.canSkill(9.5),
                "Reduced Skill should be ready at exactly 9.5 seconds");
        assertTrue(!resonanceOwner.canBurst(18.999),
                "Reduced Burst should remain unavailable before 19 seconds");
        assertTrue(resonanceOwner.canBurst(19.0),
                "Reduced Burst should be ready at exactly 19 seconds");
        assertClose(10.0, resonanceOwner.getSkillCD(), EPS,
                "Cooldown reduction should not rewrite base Skill metadata");
        assertClose(20.0, resonanceOwner.getBurstCD(), EPS,
                "Cooldown reduction should not rewrite base Burst metadata");

        SimulatorSnapshot resonanceSnapshot = resonanceSim.saveSnapshot();
        resonanceSim.advanceTime(20.0);
        resonanceSim.restoreSnapshot(resonanceSnapshot);
        assertClose(9.5, resonanceOwner.getSkillCDRemaining(resonanceSim.getCurrentTime()), EPS,
                "Snapshot restore should recover reduced Skill cooldown end time");
        assertClose(19.0, resonanceOwner.getBurstCDRemaining(resonanceSim.getCurrentTime()), EPS,
                "Snapshot restore should recover reduced Burst cooldown end time");

        TestCharacter noResonanceOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        noResonanceOwner.setSkillCD(10.0);
        noResonanceOwner.setBurstCD(20.0);
        simulatorWith(noResonanceOwner);
        noResonanceOwner.markSkillUsed(0.0);
        noResonanceOwner.markBurstUsed(0.0);
        noResonanceOwner.restoreCurrentEnergy(noResonanceOwner.getMaxEnergy());
        assertTrue(!noResonanceOwner.canSkill(9.5) && noResonanceOwner.canSkill(10.0),
                "One Anemo character should retain the base Skill cooldown");
        assertTrue(!noResonanceOwner.canBurst(19.0) && noResonanceOwner.canBurst(20.0),
                "One Anemo character should retain the base Burst cooldown");

        TestCharacter chargeOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        chargeOwner.setSkillCD(10.0);
        chargeOwner.setSkillMaxCharges(2);
        CombatSimulator chargeSim = simulatorWith(chargeOwner);
        chargeSim.applyTeamBuff(new SimpleBuff(
                "Temporary Cooldown Reduction",
                BuffId.CUSTOM,
                0.5,
                0.0,
                stats -> stats.add(StatType.CD_REDUCTION, 0.05)));
        chargeOwner.markSkillUsed(0.0, chargeSim.getApplicableBuffs(chargeOwner));
        chargeSim.advanceTime(1.0);
        chargeOwner.markSkillUsed(
                chargeSim.getCurrentTime(), chargeSim.getApplicableBuffs(chargeOwner));
        List<Double> expectedChargeRestores = java.util.List.of(9.5, 10.5);
        assertEquals(expectedChargeRestores, chargeOwner.getChargeRestoreTimes(),
                "Active multi-charge queue should retain its first cooldown snapshot");
        SimulatorSnapshot chargeSnapshot = chargeSim.saveSnapshot();
        assertTrue(!chargeOwner.canSkill(9.499),
                "Both reduced charges should remain pending before the first boundary");
        assertTrue(chargeOwner.canSkill(9.5),
                "One reduced charge should restore at the first exact boundary");
        chargeSim.restoreSnapshot(chargeSnapshot);
        assertEquals(expectedChargeRestores, chargeOwner.getChargeRestoreTimes(),
                "Snapshot restore should recover pending reduced charge times");
        assertClose(9.5, chargeOwner.getActiveChargeCooldownDuration(), EPS,
                "Snapshot restore should recover the active charge duration");

        TestCharacter cappedOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE)
                .withStat(StatType.CD_REDUCTION, 1.5);
        cappedOwner.setSkillCD(10.0);
        cappedOwner.markSkillUsed(0.0);
        assertTrue(cappedOwner.canSkill(0.0),
                "Cooldown reduction above 100% should clamp to a non-negative duration");
    }

    private static void testAccuracyPhaseF_TimingAwareEnergyAnalysis() {
        TestBurstCharacter intervalCharacter = new TestBurstCharacter(60.0);
        CombatSimulator intervalSim = simulatorWithExistingCharacter(intervalCharacter);

        intervalSim.performAction(CharacterId.SUCROSE,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        intervalCharacter.receiveParticleEnergy(30.0, 1.0);
        intervalSim.performAction(CharacterId.SUCROSE,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        intervalCharacter.receiveParticleEnergy(90.0, 1.0);
        intervalSim.performAction(CharacterId.SUCROSE,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        intervalCharacter.receiveParticleEnergy(60.0, 1.0);

        assertEquals(3, intervalCharacter.getBurstEnergyWindows().size(),
                "Successful and skipped Burst requests should both close analysis windows");
        Map<CharacterId, Double> intervalER = EnergyAnalyzer.calculateERRequirements(intervalSim);
        assertClose(2.0, intervalER.get(CharacterId.SUCROSE), EPS,
                "Later excess particles should not hide a deficient 30-energy interval");

        TestBurstCharacter cyclicCharacter = new TestBurstCharacter(60.0);
        CombatSimulator cyclicSim = simulatorWithExistingCharacter(cyclicCharacter);
        cyclicCharacter.receiveParticleEnergy(10.0, 1.0);
        cyclicSim.performAction(CharacterId.SUCROSE,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        cyclicCharacter.receiveParticleEnergy(20.0, 1.0);
        Map<CharacterId, Double> cyclicER = EnergyAnalyzer.calculateERRequirements(cyclicSim);
        assertClose(2.0, cyclicER.get(CharacterId.SUCROSE), EPS,
                "Final tail and pre-first particles should form one cyclic interval");

        TestBurstCharacter unfundedCharacter = new TestBurstCharacter(60.0);
        CombatSimulator unfundedSim = simulatorWithExistingCharacter(unfundedCharacter);
        unfundedSim.performAction(CharacterId.SUCROSE,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        Map<CharacterId, Double> unfundedER = EnergyAnalyzer.calculateERRequirements(unfundedSim);
        assertClose(9.99, unfundedER.get(CharacterId.SUCROSE), EPS,
                "A Burst interval without enough flat or particle energy should use the sentinel");
    }

    private static void testAccuracyPhaseF_ArtifactOptimizerRejectsUnreachableEr() {
        double maximumEr = 1.0
                + (model.standards.KQMSConstants.FIXED_ROLLS + 10)
                        * model.standards.KQMSConstants.ENERGY_RECHARGE;
        StatsContainer baseStats = new StatsContainer();
        baseStats.set(StatType.ENERGY_RECHARGE, 1.0);
        StatsContainer emptyStats = new StatsContainer();

        mechanics.optimization.ArtifactOptimizer.OptimizationConfig exactConfig =
                energyOnlyArtifactConfig(maximumEr);
        mechanics.optimization.ArtifactOptimizer.OptimizationResult[] exactResult = { null };
        captureStandardOutput(() -> exactResult[0] = mechanics.optimization.ArtifactOptimizer.generate(
                exactConfig, baseStats, emptyStats, emptyStats));
        assertClose(
                maximumEr - 1.0,
                exactResult[0].stats.get(StatType.ENERGY_RECHARGE),
                EPS,
                "Artifact optimizer should accept an exactly reachable ER target");

        mechanics.optimization.ArtifactOptimizer.OptimizationConfig impossibleConfig =
                energyOnlyArtifactConfig(maximumEr + 0.000001);
        String impossibleMessage = captureArtifactErFailure(
                impossibleConfig, baseStats, emptyStats);
        assertTrue(impossibleMessage.contains("ENERGY_RECHARGE target unreachable"),
                "Unreachable ER failure should identify the constrained stat");
        assertTrue(impossibleMessage.contains("requested") && impossibleMessage.contains("achieved"),
                "Unreachable ER failure should report requested and achieved values");

        mechanics.optimization.ArtifactOptimizer.OptimizationConfig manualConfig =
                energyOnlyArtifactConfig(1.20);
        manualConfig.manualRolls = java.util.Map.of(StatType.ENERGY_RECHARGE, 0);
        String manualMessage = captureArtifactErFailure(manualConfig, baseStats, emptyStats);
        assertTrue(manualMessage.contains("ENERGY_RECHARGE target unreachable"),
                "Insufficient manual ER rolls should use the same feasibility guard");
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

    private static void testAccuracyPhaseF_FavoniusInjectedProcBoundaries() {
        boolean nullRejected = false;
        try {
            new model.weapon.FavoniusCodex(null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "Favonius Codex should reject a null proc draw source");

        double[] draws = { 0.5, 0.499999, 0.0 };
        int[] drawIndex = { 0 };
        model.weapon.FavoniusCodex weapon = new model.weapon.FavoniusCodex(
                () -> draws[drawIndex[0]++]);
        TestCharacter owner = testCharacter(Element.HYDRO).withStat(StatType.CRIT_RATE, 0.5);
        owner.setWeapon(weapon);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        AttackAction hit = damageHit("Favonius draw boundary", Element.HYDRO, 1.0);

        weapon.onDamage(owner, hit, 0.0, sim);
        assertEquals(1, drawIndex[0], "A draw equal to CRIT Rate should not trigger Windfall");
        assertClose(0.0, owner.getCurrentEnergy(), EPS,
                "A failed Favonius draw should not generate particles");

        captureStandardOutput(() -> weapon.onDamage(owner, hit, 0.0, sim));
        assertEquals(2, drawIndex[0], "A failed Favonius draw should permit an immediate retry");
        double energyAfterFirstProc = owner.getCurrentEnergy();
        assertTrue(energyAfterFirstProc > 0.0,
                "A Favonius draw below CRIT Rate should generate neutral particles");

        weapon.onDamage(owner, hit, 5.999, sim);
        assertEquals(2, drawIndex[0], "Favonius should not draw before its six-second cooldown");
        captureStandardOutput(() -> weapon.onDamage(owner, hit, 6.0, sim));
        assertEquals(3, drawIndex[0], "Favonius should draw at exactly six seconds after a proc");

        int[] replayDrawIndex = { 0 };
        model.weapon.FavoniusCodex replayWeapon = new model.weapon.FavoniusCodex(
                () -> draws[replayDrawIndex[0]++]);
        TestCharacter replayOwner = testCharacter(Element.HYDRO).withStat(StatType.CRIT_RATE, 0.5);
        replayOwner.setWeapon(replayWeapon);
        CombatSimulator replaySim = simulatorWith(replayOwner);
        replayOwner.restoreCurrentEnergy(0.0);
        replayWeapon.onDamage(replayOwner, hit, 0.0, replaySim);
        captureStandardOutput(() -> replayWeapon.onDamage(replayOwner, hit, 0.0, replaySim));
        assertEquals(2, replayDrawIndex[0], "Identical Favonius sequences should consume equal draws");
        assertClose(energyAfterFirstProc, replayOwner.getCurrentEnergy(), EPS,
                "Identical Favonius sequences should generate equal energy");
    }

    private static void testAccuracyPhaseF_SacrificialSwordProcBoundaries() {
        boolean nullRejected = false;
        try {
            new model.weapon.SacrificialSword(null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "Sacrificial Sword should reject a null proc draw source");

        AttackAction skillHit = new AttackAction(
                "Sacrificial Skill Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        skillHit.setICD(ICDType.None, ICDTag.ElementalSkill, 0.0);
        AttackAction zeroSkillHit = new AttackAction(
                "Sacrificial Zero Skill Hit",
                0.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        AttackAction normalHit = new AttackAction(
                "Sacrificial Normal Hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.NORMAL);

        int[] dispatchDraws = { 0 };
        model.weapon.SacrificialSword dispatchWeapon = new model.weapon.SacrificialSword(() -> {
            dispatchDraws[0]++;
            return 0.799999;
        });
        TestCharacter dispatchOwner = testCharacter(Element.HYDRO);
        dispatchOwner.setWeapon(dispatchWeapon);
        dispatchOwner.setSkillCD(10.0);
        CombatSimulator dispatchSim = simulatorWith(dispatchOwner);
        dispatchOwner.markSkillUsed(0.0);
        dispatchSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, skillHit);
        assertEquals(1, dispatchDraws[0],
                "A resolved Skill hit should dispatch one Sacrificial draw");
        assertTrue(dispatchOwner.canSkill(0.0),
                "A draw just below 0.8 should reset the pending Skill cooldown");
        assertClose(454.0, dispatchWeapon.getStats().get(StatType.BASE_ATK), EPS,
                "Sacrificial Sword should retain its Lv90 base ATK");
        assertClose(0.613, dispatchWeapon.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Sacrificial Sword should retain its Lv90 Energy Recharge");

        int[] failedDraws = { 0 };
        model.weapon.SacrificialSword failedWeapon = new model.weapon.SacrificialSword(() -> {
            failedDraws[0]++;
            return 0.8;
        });
        TestCharacter failedOwner = testCharacter(Element.HYDRO);
        failedOwner.setSkillCD(10.0);
        failedOwner.markSkillUsed(0.0);
        failedWeapon.onDamage(failedOwner, skillHit, 0.0, simulatorWith(failedOwner));
        assertEquals(1, failedDraws[0], "A draw equal to 0.8 should be consumed and fail");
        assertTrue(!failedOwner.canSkill(9.999),
                "A failed boundary draw should preserve the Skill cooldown");

        double[] retrySequence = { 0.8, 0.1, 0.1 };
        int[] retryIndex = { 0 };
        model.weapon.SacrificialSword retryWeapon = new model.weapon.SacrificialSword(
                () -> retrySequence[retryIndex[0]++]);
        TestCharacter retryOwner = testCharacter(Element.HYDRO);
        retryOwner.setWeapon(retryWeapon);
        retryOwner.setSkillCD(30.0);
        CombatSimulator retrySim = simulatorWith(retryOwner);
        retryOwner.markSkillUsed(0.0);
        retryWeapon.onDamage(retryOwner, skillHit, 0.0, retrySim);
        assertEquals(1, retryIndex[0], "A failed Skill hit should permit a later-hit retry");
        retryWeapon.onDamage(retryOwner, skillHit, 0.0, retrySim);
        assertEquals(2, retryIndex[0], "A later Skill hit should consume the successful retry draw");
        assertTrue(retryOwner.canSkill(0.0), "The successful retry should reset Skill cooldown");
        retryWeapon.onDamage(retryOwner, skillHit, 0.0, retrySim);
        assertEquals(2, retryIndex[0], "A successful retry should suppress further same-time draws");

        retryOwner.markSkillUsed(1.0);
        retryWeapon.onDamage(retryOwner, skillHit, 15.999, retrySim);
        assertEquals(2, retryIndex[0], "Composed should not draw before sixteen seconds");
        assertTrue(!retryOwner.canSkill(15.999),
                "A suppressed pre-boundary hit should not reset Skill cooldown");
        retryWeapon.onDamage(retryOwner, skillHit, 16.0, retrySim);
        assertEquals(3, retryIndex[0], "Composed should draw at exactly sixteen seconds");
        assertTrue(retryOwner.canSkill(16.0),
                "An exact-boundary success should reset Skill cooldown");

        int[] ineligibleDraws = { 0 };
        model.weapon.SacrificialSword ineligibleWeapon = new model.weapon.SacrificialSword(() -> {
            ineligibleDraws[0]++;
            return 0.0;
        });
        TestCharacter ineligibleOwner = testCharacter(Element.HYDRO);
        CombatSimulator ineligibleSim = simulatorWith(ineligibleOwner);
        ineligibleWeapon.onDamage(ineligibleOwner, normalHit, 0.0, ineligibleSim);
        ineligibleWeapon.onDamage(ineligibleOwner, zeroSkillHit, 0.0, ineligibleSim);
        assertEquals(0, ineligibleDraws[0],
                "Non-Skill and zero-damage Skill actions should not consume draws");

        int[] readyDraws = { 0 };
        model.weapon.SacrificialSword readyWeapon = new model.weapon.SacrificialSword(() -> {
            readyDraws[0]++;
            return 0.0;
        });
        TestCharacter readyOwner = testCharacter(Element.HYDRO);
        CombatSimulator readySim = simulatorWith(readyOwner);
        readyWeapon.onDamage(readyOwner, skillHit, 0.0, readySim);
        readyOwner.setSkillCD(30.0);
        readyOwner.markSkillUsed(1.0);
        readyWeapon.onDamage(readyOwner, skillHit, 1.0, readySim);
        assertEquals(1, readyDraws[0],
                "A ready-Skill proc should consume the weapon cooldown");
        assertTrue(!readyOwner.canSkill(1.0),
                "A hit during weapon cooldown should not reset a newly pending Skill");

        model.weapon.SacrificialSword chargeWeapon = new model.weapon.SacrificialSword(() -> 0.0);
        TestCharacter chargeOwner = testCharacter(Element.HYDRO);
        chargeOwner.setSkillCD(10.0);
        chargeOwner.setSkillMaxCharges(2);
        CombatSimulator chargeSim = simulatorWith(chargeOwner);
        chargeOwner.markSkillUsed(0.0);
        chargeOwner.markSkillUsed(1.0);
        chargeWeapon.onDamage(chargeOwner, skillHit, 2.0, chargeSim);
        assertEquals(java.util.List.of(11.0), chargeOwner.getChargeRestoreTimes(),
                "Composed should remove only the earliest pending charge restore");
        assertClose(10.0, chargeOwner.getActiveChargeCooldownDuration(), EPS,
                "A remaining charge should retain the captured restore duration");
    }

    private static void testAccuracyPhaseF_WanderingEvenstarTimedSnapshot() {
        double firstTriggerTime = 64.0 / 60.0;
        TestCharacter ally = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        TestCharacter owner = testCharacter(Element.ANEMO, CharacterId.SUCROSE)
                .withStat(StatType.ELEMENTAL_MASTERY, 500.0);
        StatsContainer artifactStats = new StatsContainer();
        artifactStats.set(StatType.ELEMENTAL_MASTERY, 200.0);
        owner.setArtifacts(new ArtifactSet("Evenstar EM Fixture", artifactStats));
        owner.setWeapon(new model.weapon.WanderingEvenstar());

        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(ally);
        sim.addCharacter(owner);

        assertClose(0.0, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar owner buff should be absent before 64 frames");
        assertClose(0.0, resolvedStat(sim, ally, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar ally buff should be absent before 64 frames");
        sim.advanceTime(firstTriggerTime - 0.000001);
        assertClose(0.0, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar should remain inactive immediately before 64 frames");
        sim.advanceTime(0.000001);

        double firstSnapshotEM = 500.0 + 200.0 + 165.0;
        assertClose(firstSnapshotEM * 0.48, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar owner buff should use base, weapon, and artifact EM");
        assertClose(firstSnapshotEM * 0.48 * 0.30, resolvedStat(sim, ally, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar ally share should use the owner's captured EM");

        owner.addBuff(new Buff("Evenstar Snapshot EM", 20.0, sim.getCurrentTime()) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                stats.add(StatType.ELEMENTAL_MASTERY, 100.0);
            }
        });
        sim.advanceTime(9.999);
        assertClose(firstSnapshotEM * 0.48, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar owner buff should not change inside a snapshot interval");
        assertClose(firstSnapshotEM * 0.48 * 0.30, resolvedStat(sim, ally, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar ally share should not change inside a snapshot interval");
        sim.advanceTime(0.001);

        double secondSnapshotEM = firstSnapshotEM + 100.0;
        assertClose(secondSnapshotEM * 0.48, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar owner buff should update at the ten-second resnapshot");
        assertClose(secondSnapshotEM * 0.48 * 0.30, resolvedStat(sim, ally, StatType.ATK_FLAT), EPS,
                "Wandering Evenstar ally share should update from the same resnapshot");

        TestCharacter stackTarget = testCharacter(Element.HYDRO, CharacterId.XINGQIU);
        TestCharacter firstOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE)
                .withStat(StatType.ELEMENTAL_MASTERY, 200.0);
        TestCharacter secondOwner = testCharacter(Element.HYDRO, CharacterId.COLUMBINA)
                .withStat(StatType.ELEMENTAL_MASTERY, 100.0);
        firstOwner.setWeapon(new model.weapon.WanderingEvenstar());
        secondOwner.setWeapon(new model.weapon.WanderingEvenstar());
        CombatSimulator stackSim = new CombatSimulator();
        stackSim.setLoggingEnabled(false);
        stackSim.setEnemy(new Enemy(90));
        stackSim.addCharacter(stackTarget);
        stackSim.addCharacter(firstOwner);
        stackSim.addCharacter(secondOwner);
        stackSim.advanceTime(firstTriggerTime);
        double expectedStackedShare = (200.0 + 165.0) * 0.48 * 0.30
                + (100.0 + 165.0) * 0.48 * 0.30;
        assertClose(expectedStackedShare, resolvedStat(stackSim, stackTarget, StatType.ATK_FLAT), EPS,
                "Independent Wandering Evenstar ally shares should stack once each");
    }

    private static void testAccuracyPhaseF_ColumbinaMoondriftInjectedDrawBoundaries() {
        boolean nullRejected = false;
        try {
            new model.character.Columbina(new TestWeapon(), blankArtifact(), (DoubleSupplier) null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "Columbina should reject a null Moondrift draw source");

        double[] draws = { 0.329999, 0.33 };
        int[] drawIndex = { 0 };
        List<String> actions = runMoondriftDrawSequence(() -> draws[drawIndex[0]++]);
        assertEquals(2, drawIndex[0], "Two Crystallize Interferences should consume two draws");
        assertEquals(3, actions.size(), "One of two Moondrift draws should add an extra attack");
        assertEquals("Interference (Crystallize)", actions.get(0), "First Moondrift main attack");
        assertEquals("Interference (Crystallize) Extra", actions.get(1),
                "A draw below 0.33 should add the Moondrift extra attack");
        assertEquals("Interference (Crystallize)", actions.get(2),
                "A draw equal to 0.33 should not add an extra attack");

        int[] replayDrawIndex = { 0 };
        List<String> replayActions = runMoondriftDrawSequence(() -> draws[replayDrawIndex[0]++]);
        assertEquals(drawIndex[0], replayDrawIndex[0],
                "Identical Moondrift sequences should consume equal draws");
        assertEquals(actions, replayActions,
                "Identical Moondrift sequences should produce equal action outcomes");
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

    private static void testAccuracyPhaseF_ArtifactTeamBuffProviderRouting() {
        TestCharacter owner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        owner.setArtifacts(new FixtureArtifactTeamBuffProvider());
        TestCharacter ally = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(ally);
        sim.applyTeamBuff(new SimpleBuff("Independent provider fixture", 20.0, 0.0,
                stats -> stats.add(StatType.ATK_FLAT, 10.0)));

        assertClose(50.0, resolvedStat(sim, owner, StatType.ATK_FLAT), EPS,
                "Artifact and simulator team buffs should coexist for the owner");
        assertClose(50.0, resolvedStat(sim, ally, StatType.ATK_FLAT), EPS,
                "Artifact and simulator team buffs should coexist for an ally");
        assertClose(0.0, resolvedStat(sim, owner, StatType.DEF_FLAT), EPS,
                "Artifact team-buff targeting should exclude the wrong element");
        assertClose(25.0, resolvedStat(sim, ally, StatType.DEF_FLAT), EPS,
                "Artifact team-buff targeting should include the configured element");

        List<Buff> ownerBuffs = sim.getApplicableBuffs(owner);
        Buff fallbackSource = ownerBuffs.stream()
                .filter(buff -> buff.getDisplayName().equals("Artifact provider fallback source"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Artifact fallback-source buff should be routed"));
        assertEquals(CharacterId.SUCROSE, fallbackSource.getSourceCharacterId(),
                "Artifact provider should attribute an unknown source to its owner");
        Buff explicitSource = sim.getApplicableBuffs(ally).stream()
                .filter(buff -> buff.getDisplayName().equals("Artifact provider explicit source"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Artifact explicit-source buff should be routed"));
        assertEquals(CharacterId.COLUMBINA, explicitSource.getSourceCharacterId(),
                "Artifact provider should preserve an explicit source");
    }

    private static void testAccuracyPhaseF_SilkenMoonsSerenadeDynamicBonus() {
        model.artifact.SilkenMoonsSerenade silken = new model.artifact.SilkenMoonsSerenade();
        assertClose(0.20, silken.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Silken should retain its 20% two-piece Energy Recharge");

        TestCharacter silkenOwner = testCharacter(Element.ELECTRO, CharacterId.INEFFA).asLunar();
        silkenOwner.setArtifacts(silken);
        TestCharacter intentOwner = testCharacter(Element.ELECTRO, CharacterId.FLINS).asLunar();
        intentOwner.setArtifacts(new model.artifact.NightOfTheSkysUnveiling());
        CombatSimulator sim = simulatorWith(silkenOwner);
        sim.addCharacter(intentOwner);
        sim.updateMoonsign();
        sim.setActiveCharacter(CharacterId.FLINS);

        assertClose(0.0, resolvedStat(sim, silkenOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Silken should grant no Lunar bonus without a Gleaming Moon effect");
        sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED), intentOwner);
        assertClose(0.10, resolvedStat(sim, silkenOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Intent alone should grant one dynamic Silken Lunar bonus");

        sim.performActionWithoutTimeAdvance(CharacterId.INEFFA,
                damageHit("Off-field Silken trigger", Element.ELECTRO, 1.0));
        assertClose(120.0, resolvedStat(sim, silkenOwner, StatType.ELEMENTAL_MASTERY), EPS,
                "Off-field Ascendant Silken damage should grant 120 team EM");
        assertClose(0.20, resolvedStat(sim, silkenOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Intent and Devotion should grant 20% Lunar-Charged DMG");
        assertClose(0.20, resolvedStat(sim, intentOwner, StatType.LUNAR_BLOOM_DMG_BONUS), EPS,
                "Intent and Devotion should grant 20% Lunar-Bloom DMG to allies");
        assertClose(0.20, resolvedStat(sim, intentOwner, StatType.LUNAR_CRYSTALLIZE_DMG_BONUS), EPS,
                "Intent and Devotion should grant 20% Lunar-Crystallize DMG to allies");

        sim.performActionWithoutTimeAdvance(CharacterId.INEFFA,
                damageHit("Repeated Silken trigger", Element.ELECTRO, 1.0));
        intentOwner.addBuff(new Buff("Duplicate Intent fixture", BuffId.GLEAMING_MOON_INTENT, 4.0,
                sim.getCurrentTime()) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
            }
        });
        assertClose(200.0, resolvedStat(sim, intentOwner, StatType.ELEMENTAL_MASTERY), EPS,
                "Repeated Devotion should refresh rather than stack team EM");
        assertClose(0.20, resolvedStat(sim, intentOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Duplicate Gleaming Moon IDs should count once each");

        Buff intent = intentOwner.getActiveBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_INTENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Intent fixture should be active"));
        Buff devotion = silkenOwner.getActiveBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_DEVOTION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Devotion fixture should be active"));
        Buff synergy = sim.getApplicableBuffs(silkenOwner).stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_SYNERGY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Silken synergy provider should be routed"));
        assertEquals(CharacterId.INEFFA, synergy.getSourceCharacterId(),
                "Silken synergy should be sourced by its canonical owner");

        StatsContainer atIntentExpiry = new StatsContainer();
        synergy.apply(atIntentExpiry, intent.getExpirationTime());
        assertClose(0.10, atIntentExpiry.get(StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Exact Intent expiry should leave only Devotion's dynamic bonus");
        StatsContainer atDevotionExpiry = new StatsContainer();
        synergy.apply(atDevotionExpiry, devotion.getExpirationTime());
        assertClose(0.0, atDevotionExpiry.get(StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Exact Devotion expiry should remove the remaining dynamic bonus");
        StatsContainer devotionAtExpiry = new StatsContainer();
        devotion.apply(devotionAtExpiry, devotion.getExpirationTime());
        assertClose(0.0, devotionAtExpiry.get(StatType.ELEMENTAL_MASTERY), EPS,
                "Devotion should exclude its exact eight-second expiry");

        TestCharacter firstOwner = testCharacter(Element.ELECTRO, CharacterId.INEFFA).asLunar();
        firstOwner.setArtifacts(new model.artifact.SilkenMoonsSerenade());
        TestCharacter secondOwner = testCharacter(Element.HYDRO, CharacterId.COLUMBINA).asLunar();
        secondOwner.setArtifacts(new model.artifact.SilkenMoonsSerenade());
        CombatSimulator multiSim = simulatorWith(firstOwner);
        multiSim.addCharacter(secondOwner);
        multiSim.updateMoonsign();
        multiSim.setActiveCharacter(CharacterId.COLUMBINA);
        multiSim.performActionWithoutTimeAdvance(CharacterId.INEFFA,
                damageHit("First Silken owner trigger", Element.ELECTRO, 1.0));
        multiSim.performActionWithoutTimeAdvance(CharacterId.COLUMBINA,
                damageHit("Second Silken owner trigger", Element.HYDRO, 1.0));
        assertClose(120.0, resolvedStat(multiSim, firstOwner, StatType.ELEMENTAL_MASTERY), EPS,
                "Two Silken wearers should retain one Devotion EM value");
        assertClose(0.10, resolvedStat(multiSim, secondOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Two Silken wearers should retain one distinct Devotion bonus");
        assertEquals(1L, multiSim.getApplicableBuffs(firstOwner).stream()
                        .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_SYNERGY)
                        .count(),
                "Two Silken wearers should expose one canonical synergy provider");
    }

    private static void testAccuracyPhaseF_AscendantBlessingExpiryReplacement() {
        TestCharacter strongSource = testCharacter(Element.PYRO, CharacterId.BENNETT)
                .withStat(StatType.BASE_ATK, 4000.0);
        TestCharacter weakSource = testCharacter(Element.ANEMO, CharacterId.SUCROSE)
                .withStat(StatType.ELEMENTAL_MASTERY, 400.0);
        TestCharacter lunarSource = testCharacter(Element.ELECTRO, CharacterId.FLINS).asLunar();
        CombatSimulator sim = simulatorWith(strongSource);
        sim.addCharacter(weakSource);
        sim.addCharacter(lunarSource);
        sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);

        AttackAction strongSkill = blessingFixtureAction("Strong Blessing Skill", ActionType.SKILL);
        AttackAction weakBurst = blessingFixtureAction("Weak Blessing Burst", ActionType.BURST);
        sim.performAction(CharacterId.BENNETT, strongSkill);
        simulation.runtime.MoonsignManager.MoonsignBuff initial = requireAscendantBlessing(sim);
        assertClose(0.36, initial.getValue(), EPS,
                "Ascendant Blessing should cap a 4000 ATK Pyro source at 36%");
        assertClose(0.0, initial.getStartTime(), EPS,
                "Initial Ascendant Blessing should begin at the triggering action time");
        assertClose(20.0, initial.getExpirationTime(), EPS,
                "Initial Ascendant Blessing should retain its sourced 20-second window");

        sim.advanceTime(5.0);
        sim.performAction(CharacterId.SUCROSE, weakBurst);
        simulation.runtime.MoonsignManager.MoonsignBuff retained = requireAscendantBlessing(sim);
        assertClose(0.36, retained.getValue(), EPS,
                "An active stronger Ascendant Blessing should reject a weaker value");
        assertClose(20.0, retained.getExpirationTime(), EPS,
                "A rejected weaker value should not extend the active stronger window");

        sim.performAction(CharacterId.BENNETT, strongSkill);
        simulation.runtime.MoonsignManager.MoonsignBuff refreshed = requireAscendantBlessing(sim);
        assertClose(0.36, refreshed.getValue(), EPS,
                "An equal Ascendant Blessing should retain its calculated value");
        assertClose(5.0, refreshed.getStartTime(), EPS,
                "An equal Ascendant Blessing should refresh at the new trigger time");
        assertClose(25.0, refreshed.getExpirationTime(), EPS,
                "An equal Ascendant Blessing should refresh one 20-second window");

        sim.advanceTime(20.0);
        assertTrue(refreshed.isExpired(sim.getCurrentTime()),
                "The stronger Ascendant Blessing should be expired at its exact boundary");
        sim.performAction(CharacterId.SUCROSE, weakBurst);
        simulation.runtime.MoonsignManager.MoonsignBuff replacement = requireAscendantBlessing(sim);
        assertClose(0.09, replacement.getValue(), EPS,
                "An expired stronger Blessing should not block a weaker Anemo replacement");
        assertClose(25.0, replacement.getStartTime(), EPS,
                "The weaker replacement should begin at the exact expiry boundary");
        assertClose(45.0, replacement.getExpirationTime(), EPS,
                "The weaker replacement should establish a fresh 20-second window");

        TestCharacter gatedStrongSource = testCharacter(Element.PYRO, CharacterId.BENNETT)
                .withStat(StatType.BASE_ATK, 4000.0);
        TestCharacter gatedLunarSource = testCharacter(Element.ELECTRO, CharacterId.FLINS).asLunar();
        CombatSimulator gatedSim = simulatorWith(gatedStrongSource);
        gatedSim.addCharacter(gatedLunarSource);
        gatedSim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        gatedSim.performAction(CharacterId.BENNETT,
                blessingFixtureAction("Non-triggering Normal", ActionType.NORMAL));
        gatedSim.performAction(CharacterId.FLINS,
                blessingFixtureAction("Lunar Skill", ActionType.SKILL));
        assertTrue(gatedSim.getTeamBuffList().stream()
                        .noneMatch(buff -> buff.getId() == BuffId.MOONSIGN_ASCENDANT_BLESSING),
                "Normal actions and Lunar-character Skills should not grant Ascendant Blessing");
    }

    private static AttackAction blessingFixtureAction(String name, ActionType actionType) {
        AttackAction action = new AttackAction(
                name, 0.0, Element.PHYSICAL, StatType.BASE_ATK, null, 0.0, actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static simulation.runtime.MoonsignManager.MoonsignBuff requireAscendantBlessing(
            CombatSimulator sim) {
        long count = sim.getTeamBuffList().stream()
                .filter(buff -> buff.getId() == BuffId.MOONSIGN_ASCENDANT_BLESSING)
                .count();
        assertEquals(1L, count, "Ascendant Blessing should retain one typed team buff");
        Buff blessing = sim.getTeamBuffList().stream()
                .filter(buff -> buff.getId() == BuffId.MOONSIGN_ASCENDANT_BLESSING)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ascendant Blessing should exist"));
        assertTrue(blessing instanceof simulation.runtime.MoonsignManager.MoonsignBuff,
                "Ascendant Blessing should retain its value-bearing implementation");
        return (simulation.runtime.MoonsignManager.MoonsignBuff) blessing;
    }

    private static void testAccuracyPhaseF_ViridescentVenererRefreshContract() {
        ReactionResult pyroSwirl = ReactionCalculator.calculate(Element.ANEMO, Element.PYRO, 0.0, 90);
        ReactionResult hydroSwirl = ReactionCalculator.calculate(Element.ANEMO, Element.HYDRO, 0.0, 90);

        TestCharacter owner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        owner.setArtifacts(new model.artifact.ViridescentVenerer());
        TestCharacter ally = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator refreshSim = simulatorWith(owner);
        refreshSim.addCharacter(ally);

        refreshSim.notifyReaction(pyroSwirl, owner);
        assertClose(0.40, resolvedStat(refreshSim, owner, StatType.PYRO_RES_SHRED), EPS,
                "VV should apply one Pyro resistance shred to the team");
        refreshSim.advanceTime(5.0);
        refreshSim.notifyReaction(pyroSwirl, owner);
        assertClose(0.40, resolvedStat(refreshSim, ally, StatType.PYRO_RES_SHRED), EPS,
                "Repeated Pyro Swirls should refresh rather than stack VV shred");
        assertEquals(1L, refreshSim.getTeamBuffs().stream()
                        .filter(buff -> buff.getId() == BuffId.VV_SHRED_PYRO)
                        .count(),
                "VV should retain one simulator-owned buff per element");

        CombatSimulator baselineDamageSim = simulatorWith(testCharacter(Element.PYRO, CharacterId.XIANGLING));
        baselineDamageSim.performActionWithoutTimeAdvance(CharacterId.XIANGLING,
                damageHit("VV baseline Pyro hit", Element.PYRO, 1.0));
        double baselineDamage = baselineDamageSim.getTotalDamage();
        double beforeVvDamage = refreshSim.getTotalDamage();
        refreshSim.performActionWithoutTimeAdvance(CharacterId.XIANGLING,
                damageHit("VV-shredded Pyro hit", Element.PYRO, 1.0));
        double vvDamage = refreshSim.getTotalDamage() - beforeVvDamage;
        assertClose(baselineDamage * (1.15 / 0.90), vvDamage, EPS,
                "A subsequent Pyro hit should observe the refreshed 40% VV shred");

        refreshSim.advanceTime(9.999);
        assertClose(0.40, resolvedStat(refreshSim, ally, StatType.PYRO_RES_SHRED), EPS,
                "Refreshed VV should remain active immediately before ten seconds");
        refreshSim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(refreshSim, ally, StatType.PYRO_RES_SHRED), EPS,
                "Refreshed VV should expire at exactly ten seconds");

        TestCharacter multiOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        multiOwner.setArtifacts(new model.artifact.ViridescentVenerer());
        CombatSimulator multiSim = simulatorWith(multiOwner);
        multiSim.notifyReaction(pyroSwirl, multiOwner);
        multiSim.advanceTime(2.0);
        multiSim.notifyReaction(hydroSwirl, multiOwner);
        assertClose(0.40, resolvedStat(multiSim, multiOwner, StatType.PYRO_RES_SHRED), EPS,
                "Pyro VV should coexist with a later Hydro VV application");
        assertClose(0.40, resolvedStat(multiSim, multiOwner, StatType.HYDRO_RES_SHRED), EPS,
                "Hydro VV should retain its independent typed duration");
        multiSim.advanceTime(8.0);
        assertClose(0.0, resolvedStat(multiSim, multiOwner, StatType.PYRO_RES_SHRED), EPS,
                "Earlier Pyro VV should expire independently");
        assertClose(0.40, resolvedStat(multiSim, multiOwner, StatType.HYDRO_RES_SHRED), EPS,
                "Later Hydro VV should remain active after Pyro expires");

        TestCharacter offFieldOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        offFieldOwner.setArtifacts(new model.artifact.ViridescentVenerer());
        TestCharacter activeAlly = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator offFieldSim = simulatorWith(offFieldOwner);
        offFieldSim.addCharacter(activeAlly);
        offFieldSim.setActiveCharacter(CharacterId.XIANGLING);
        offFieldSim.notifyReaction(pyroSwirl, offFieldOwner);
        assertClose(0.0, resolvedStat(offFieldSim, activeAlly, StatType.PYRO_RES_SHRED), EPS,
                "An off-field VV owner should not apply resistance shred");

        TestCharacter wrongTriggerOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        wrongTriggerOwner.setArtifacts(new model.artifact.ViridescentVenerer());
        TestCharacter wrongTrigger = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator wrongTriggerSim = simulatorWith(wrongTriggerOwner);
        wrongTriggerSim.addCharacter(wrongTrigger);
        wrongTriggerSim.notifyReaction(pyroSwirl, wrongTrigger);
        assertClose(0.0, resolvedStat(wrongTriggerSim, wrongTriggerOwner, StatType.PYRO_RES_SHRED), EPS,
                "An active VV owner should not apply shred for another character's Swirl");

        ReactionResult unsupportedSwirl = new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE, 1.0, 0.0, "Unsupported Swirl",
                ReactionResult.Kind.SWIRL, Element.DENDRO);
        wrongTriggerSim.notifyReaction(unsupportedSwirl, wrongTriggerOwner);
        wrongTriggerSim.notifyReaction(ReactionResult.none(), wrongTriggerOwner);
        assertTrue(wrongTriggerSim.getTeamBuffs().stream()
                        .noneMatch(buff -> buff.getId() == BuffId.VV_SHRED_PYRO
                                || buff.getId() == BuffId.VV_SHRED_HYDRO
                                || buff.getId() == BuffId.VV_SHRED_CRYO
                                || buff.getId() == BuffId.VV_SHRED_ELECTRO),
                "Unsupported or non-Swirl reactions should not add a VV buff");
    }

    private static void testAccuracyPhaseF_NoblesseObligeRefreshContract() {
        model.artifact.NoblesseOblige equippedSet =
                new model.artifact.NoblesseOblige(new StatsContainer());
        assertClose(0.20, equippedSet.getStats().get(StatType.BURST_DMG_BONUS), EPS,
                "Noblesse should retain its 20% two-piece Burst DMG bonus");

        model.character.Bennett bennett = new model.character.Bennett(new TestWeapon(), equippedSet);
        TestCharacter ally = testCharacter(Element.HYDRO, CharacterId.XINGQIU);
        CombatSimulator actualBurstSim = simulatorWithExistingCharacter(bennett);
        actualBurstSim.addCharacter(ally);
        captureStandardOutput(() -> actualBurstSim.performAction(
                CharacterId.BENNETT, CharacterActionRequest.of(CharacterActionKey.BURST)));
        assertClose(0.20, resolvedStat(actualBurstSim, bennett, StatType.ATK_PERCENT), EPS,
                "An actual Bennett Burst should apply one Noblesse buff to its owner");
        assertClose(0.20, resolvedStat(actualBurstSim, ally, StatType.ATK_PERCENT), EPS,
                "An actual Bennett Burst should apply one Noblesse buff to its ally");

        TestCharacter refreshOwner = testCharacter(Element.PYRO, CharacterId.BENNETT);
        CombatSimulator refreshSim = simulatorWith(refreshOwner);
        model.artifact.NoblesseOblige refreshSet =
                new model.artifact.NoblesseOblige(new StatsContainer());
        refreshSim.applyTeamBuff(new SimpleBuff("Independent ATK fixture", 20.0, 0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 0.10)));
        captureStandardOutput(() -> refreshSet.onBurst(refreshSim));
        assertClose(0.30, resolvedStat(refreshSim, refreshOwner, StatType.ATK_PERCENT), EPS,
                "Noblesse should combine with an unrelated ATK buff");
        refreshSim.advanceTime(5.0);
        captureStandardOutput(() -> refreshSet.onBurst(refreshSim));
        assertClose(0.30, resolvedStat(refreshSim, refreshOwner, StatType.ATK_PERCENT), EPS,
                "Repeated Noblesse applications should refresh rather than stack");
        assertEquals(1L, refreshSim.getTeamBuffs().stream()
                        .filter(buff -> buff.getId() == BuffId.NOBLESSE_OBLIGE_4PC)
                        .count(),
                "Noblesse refresh should retain one typed team buff");
        refreshSim.advanceTime(11.999);
        assertClose(0.30, resolvedStat(refreshSim, refreshOwner, StatType.ATK_PERCENT), EPS,
                "Refreshed Noblesse should remain active immediately before twelve seconds");
        refreshSim.advanceTime(0.001);
        assertClose(0.10, resolvedStat(refreshSim, refreshOwner, StatType.ATK_PERCENT), EPS,
                "Refreshed Noblesse should expire at exactly twelve seconds");

        TestCharacter multiOwner = testCharacter(Element.PYRO, CharacterId.BENNETT);
        CombatSimulator multiSim = simulatorWith(multiOwner);
        model.artifact.NoblesseOblige firstSet =
                new model.artifact.NoblesseOblige(new StatsContainer());
        model.artifact.NoblesseOblige secondSet =
                new model.artifact.NoblesseOblige(new StatsContainer());
        captureStandardOutput(() -> firstSet.onBurst(multiSim));
        multiSim.advanceTime(1.0);
        captureStandardOutput(() -> secondSet.onBurst(multiSim));
        assertClose(0.20, resolvedStat(multiSim, multiOwner, StatType.ATK_PERCENT), EPS,
                "Separate Noblesse instances should refresh one shared team effect");
        assertEquals(1L, multiSim.getTeamBuffs().stream()
                        .filter(buff -> buff.getId() == BuffId.NOBLESSE_OBLIGE_4PC)
                        .count(),
                "Separate Noblesse instances should leave one typed team buff");
    }

    private static void testAccuracyPhaseF_WeaponReactionBonusRegression() {
        TestCharacter owner = testCharacter(Element.ANEMO);
        owner.setWeapon(new model.weapon.SunnyMorningSleepIn());
        CombatSimulator sim = simulatorWith(owner);
        captureStandardOutput(() -> sim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        double beforeEm = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.ELEMENTAL_MASTERY);
        sim.getEnemy().setAura(Element.PYRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Sunny swirl trigger", Element.ANEMO));
        double afterEm = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.ELEMENTAL_MASTERY);
        assertClose(beforeEm + 120.0, afterEm, EPS,
                "Sunny Morning Sleep-In should grant EM only after owner-triggered Swirl");
    }

    private static void testAccuracyPhaseF_DragonsBaneTargetAuraContract() {
        TestCharacter owner = testCharacter(Element.PYRO);
        owner.setWeapon(new model.weapon.DragonsBane());
        CombatSimulator sim = simulatorWith(owner);
        AttackAction directHit = damageHit("Dragon's Bane direct hit", Element.PHYSICAL, 1.0);
        double baseDamage = calculateDirectDamage(sim, owner, directHit, 0.0, 1.0);
        double affectedDamage = baseDamage * 1.36;

        assertClose(0.0, owner.getEffectiveStats(0.0).get(StatType.DMG_BONUS_ALL), EPS,
                "Dragon's Bane target bonus should not enter effective character stats");
        assertClose(baseDamage, calculateDirectDamage(sim, owner, directHit, 0.0, 1.0), EPS,
                "Dragon's Bane should not grant damage without an eligible aura");

        sim.getEnemy().setAura(Element.ELECTRO, 1.0);
        assertClose(baseDamage, calculateDirectDamage(sim, owner, directHit, 0.0, 1.0), EPS,
                "Dragon's Bane should not grant damage against Electro aura");
        sim.getEnemy().setAura(Element.ELECTRO, 0.0);

        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        assertClose(affectedDamage, calculateDirectDamage(sim, owner, directHit, 0.0, 1.0), EPS,
                "Dragon's Bane should grant 36% damage against Hydro aura");
        sim.getEnemy().setAura(Element.HYDRO, 0.0);
        sim.getEnemy().setAura(Element.PYRO, 1.0);
        assertClose(affectedDamage, calculateDirectDamage(sim, owner, directHit, 0.0, 1.0), EPS,
                "Dragon's Bane should grant 36% damage against Pyro aura");

        sim.getEnemy().setAura(Element.PYRO, 0.0);
        sim.getEnemy().setAura(Element.HYDRO, 1.0, 0.0);
        assertClose(affectedDamage, calculateDirectDamage(sim, owner, directHit, 10.999, 1.0), EPS,
                "Dragon's Bane should remain active immediately before aura expiry");
        assertClose(baseDamage, calculateDirectDamage(sim, owner, directHit, 11.0, 1.0), EPS,
                "Dragon's Bane should be inactive at exact aura expiry");

        sim.getEnemy().setAura(Element.HYDRO, 0.5);
        AttackAction vaporizeHit = new AttackAction(
                "Dragon's Bane Vaporize hit",
                1.0,
                Element.PYRO,
                StatType.BASE_ATK);
        vaporizeHit.setICD(ICDType.None, ICDTag.None, 1.0);
        double vaporizeMultiplier = ReactionCalculator.calculate(
                Element.PYRO,
                Element.HYDRO,
                owner.getEffectiveStats(0.0).get(StatType.ELEMENTAL_MASTERY),
                90).getAmpMultiplier();
        double beforeVaporize = sim.getTotalDamage();
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, vaporizeHit);
        assertClose(affectedDamage * vaporizeMultiplier, sim.getTotalDamage() - beforeVaporize, EPS,
                "Dragon's Bane should use pre-hit Hydro aura for a consuming Vaporize hit");
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Vaporize fixture should consume the Hydro aura after eligibility is captured");

        owner.captureSnapshot(0.0, null);
        AttackAction snapshotHit = new AttackAction(
                "Dragon's Bane snapshot hit",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                true);
        snapshotHit.setICD(ICDType.None, ICDTag.None, 0.0);
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        assertClose(affectedDamage, performAndMeasureDamage(sim, snapshotHit), EPS,
                "Snapshot hit should evaluate a newly applied Hydro aura");
        assertClose(affectedDamage, performAndMeasureDamage(sim, snapshotHit), EPS,
                "Repeated snapshot hits should not accumulate target bonus mutations");
        sim.getEnemy().setAura(Element.HYDRO, 0.0);
        assertClose(baseDamage, performAndMeasureDamage(sim, snapshotHit), EPS,
                "Snapshot hit should lose the bonus when the target aura is removed");
        assertClose(0.0, owner.getSnapshot().get(StatType.DMG_BONUS_ALL), EPS,
                "Dragon's Bane target bonus should never be stored in the snapshot");
    }

    private static void testAccuracyPhaseF_DendroResonanceReactionEmContract() {
        ReactionResult.Kind[] primaryKinds = {
                ReactionResult.Kind.BURNING,
                ReactionResult.Kind.QUICKEN,
                ReactionResult.Kind.BLOOM,
                ReactionResult.Kind.LUNAR_BLOOM
        };
        for (ReactionResult.Kind kind : primaryKinds) {
            assertSprawlingGreeneryTrigger(kind, 80.0);
        }

        ReactionResult.Kind[] secondaryKinds = {
                ReactionResult.Kind.AGGRAVATE,
                ReactionResult.Kind.SPREAD,
                ReactionResult.Kind.HYPERBLOOM,
                ReactionResult.Kind.BURGEON
        };
        for (ReactionResult.Kind kind : secondaryKinds) {
            assertSprawlingGreeneryTrigger(kind, 70.0);
        }
        assertSprawlingGreeneryTrigger(ReactionResult.Kind.VAPORIZE, 50.0);

        TestCharacter owner = testCharacter(Element.DENDRO, CharacterId.SUCROSE);
        CombatSimulator independentSim = simulatorWith(owner);
        independentSim.addCharacter(testCharacter(Element.DENDRO, CharacterId.XIANGLING));
        ResonanceManager.applyResonances(independentSim);
        assertClose(50.0, resolvedElementalMastery(independentSim, owner), EPS,
                "Dendro resonance should retain its permanent 50 EM base");

        independentSim.notifyReaction(
                ReactionResult.state("Quicken", ReactionResult.Kind.QUICKEN, null), owner);
        assertClose(80.0, resolvedElementalMastery(independentSim, owner), EPS,
                "Dendro primary reaction should add 30 EM");
        independentSim.advanceTime(3.0);
        independentSim.notifyReaction(
                ReactionResult.additive(0.0, "Aggravate", ReactionResult.Kind.AGGRAVATE, Element.ELECTRO), owner);
        assertClose(100.0, resolvedElementalMastery(independentSim, owner), EPS,
                "Dendro primary and secondary reaction buffs should stack independently");
        independentSim.advanceTime(3.0);
        assertClose(70.0, resolvedElementalMastery(independentSim, owner), EPS,
                "Dendro primary buff should expire exactly six seconds after its trigger");
        independentSim.advanceTime(3.0);
        assertClose(50.0, resolvedElementalMastery(independentSim, owner), EPS,
                "Dendro secondary buff should expire on its independent boundary");

        TestCharacter refreshOwner = testCharacter(Element.DENDRO, CharacterId.SUCROSE);
        CombatSimulator refreshSim = simulatorWith(refreshOwner);
        refreshSim.addCharacter(testCharacter(Element.DENDRO, CharacterId.XIANGLING));
        ResonanceManager.applyResonances(refreshSim);
        refreshSim.notifyReaction(ReactionResult.transform(0.0, "Bloom", ReactionResult.Kind.BLOOM), refreshOwner);
        refreshSim.advanceTime(5.0);
        refreshSim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM), refreshOwner);
        assertClose(80.0, resolvedElementalMastery(refreshSim, refreshOwner), EPS,
                "Repeated primary triggers should refresh at 30 EM without stacking");
        refreshSim.advanceTime(1.0);
        assertClose(80.0, resolvedElementalMastery(refreshSim, refreshOwner), EPS,
                "Lunar-Bloom should refresh the primary buff beyond its original expiry");
        refreshSim.advanceTime(5.0);
        assertClose(50.0, resolvedElementalMastery(refreshSim, refreshOwner), EPS,
                "Refreshed primary buff should expire at its replacement boundary");
    }

    private static void testAccuracyPhaseF_CryoResonanceConditionalCritContract() {
        TestCharacter owner = testCharacter(Element.CRYO, CharacterId.SUCROSE);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(testCharacter(Element.CRYO, CharacterId.XIANGLING));
        ResonanceManager.applyResonances(sim);
        double baseCrit = resolvedStat(sim, owner, StatType.CRIT_RATE);
        assertClose(0.05, baseCrit, EPS,
                "Cryo resonance should not grant CRIT Rate against an unaffected enemy");

        sim.getEnemy().setAura(Element.PYRO, 1.0);
        assertClose(baseCrit, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should not grant CRIT Rate against an unrelated aura");

        sim.getEnemy().setAura(Element.PYRO, 0.0);
        sim.getEnemy().setAura(Element.CRYO, 1.0);
        assertClose(baseCrit + 0.15, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should grant 15% CRIT Rate against Cryo aura");

        sim.getEnemy().setAura(Element.CRYO, 0.0);
        sim.getEnemy().setFreezeAura(1.0);
        assertClose(baseCrit + 0.15, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should grant 15% CRIT Rate against Frozen state");

        sim.getEnemy().clearFreezeAura();
        sim.getEnemy().setAura(Element.CRYO, 1.0, sim.getCurrentTime());
        sim.advanceTime(10.999);
        assertClose(baseCrit + 0.15, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should remain active immediately before aura expiry");
        sim.advanceTime(0.001);
        assertClose(baseCrit, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should be inactive exactly at aura expiry");
    }

    private static void testAccuracyPhaseF_ElectroResonanceTypedTriggerContract() {
        ReactionResult.Kind[] eligibleKinds = {
                ReactionResult.Kind.SUPERCONDUCT,
                ReactionResult.Kind.OVERLOADED,
                ReactionResult.Kind.OVERLOAD,
                ReactionResult.Kind.ELECTRO_CHARGED,
                ReactionResult.Kind.LUNAR_CHARGED,
                ReactionResult.Kind.QUICKEN,
                ReactionResult.Kind.AGGRAVATE,
                ReactionResult.Kind.HYPERBLOOM
        };
        for (ReactionResult.Kind kind : eligibleKinds) {
            assertTrue(ReactionResult.transform(0.0, kind.name(), kind).triggersElectroResonance(),
                    "Electro resonance should accept " + kind);
        }

        ReactionResult.Kind[] ineligibleKinds = {
                ReactionResult.Kind.LUNAR_BLOOM,
                ReactionResult.Kind.LUNAR_CRYSTALLIZE,
                ReactionResult.Kind.SPREAD,
                ReactionResult.Kind.BURGEON,
                ReactionResult.Kind.VAPORIZE
        };
        for (ReactionResult.Kind kind : ineligibleKinds) {
            assertTrue(!ReactionResult.transform(0.0, kind.name(), kind).triggersElectroResonance(),
                    "Electro resonance should reject " + kind);
        }

        TestCharacter owner = testCharacter(Element.ELECTRO, CharacterId.SUCROSE);
        TestCharacter ally = testCharacter(Element.ELECTRO, CharacterId.XIANGLING);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(ally);
        owner.restoreCurrentEnergy(0.0);
        ally.restoreCurrentEnergy(0.0);
        ResonanceManager.applyResonances(sim);
        double emptyEnergy = partyEnergy(owner, ally);

        sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM), owner);
        assertClose(emptyEnergy, partyEnergy(owner, ally), EPS,
                "Lunar-Bloom should not generate an Electro resonance particle");

        sim.notifyReaction(ReactionResult.transform(
                0.0, "Superconduct", ReactionResult.Kind.SUPERCONDUCT), owner);
        double afterFirstParticle = partyEnergy(owner, ally);
        assertTrue(afterFirstParticle > emptyEnergy,
                "First eligible reaction should generate an Electro resonance particle");

        sim.notifyReaction(ReactionResult.transform(
                0.0, "Overloaded", ReactionResult.Kind.OVERLOADED), owner);
        assertClose(afterFirstParticle, partyEnergy(owner, ally), EPS,
                "Electro resonance should share one cooldown across eligible reaction kinds");
        sim.advanceTime(4.999);
        sim.notifyReaction(ReactionResult.state("Quicken", ReactionResult.Kind.QUICKEN, null), owner);
        assertClose(afterFirstParticle, partyEnergy(owner, ally), EPS,
                "Electro resonance should remain on cooldown before five seconds");
        sim.advanceTime(0.001);
        sim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED), owner);
        assertTrue(partyEnergy(owner, ally) > afterFirstParticle,
                "Electro resonance should generate another particle at exactly five seconds");
    }

    private static double partyEnergy(Character... characters) {
        double total = 0.0;
        for (Character character : characters) {
            total += character.getCurrentEnergy();
        }
        return total;
    }

    private static void assertSprawlingGreeneryTrigger(ReactionResult.Kind kind, double expectedEm) {
        TestCharacter owner = testCharacter(Element.DENDRO, CharacterId.SUCROSE);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(testCharacter(Element.DENDRO, CharacterId.XIANGLING));
        ResonanceManager.applyResonances(sim);
        sim.notifyReaction(ReactionResult.transform(0.0, kind.name(), kind), owner);
        assertClose(expectedEm, resolvedElementalMastery(sim, owner), EPS,
                "Unexpected Sprawling Greenery EM for " + kind);
    }

    private static double resolvedElementalMastery(CombatSimulator sim, Character character) {
        return resolvedStat(sim, character, StatType.ELEMENTAL_MASTERY);
    }

    private static double resolvedStat(CombatSimulator sim, Character character, StatType statType) {
        AttackAction probe = damageHit("Resonance stat probe", Element.PHYSICAL, 0.0);
        return DamageCalculator.resolveStats(
                character, probe, sim.getApplicableBuffs(character), sim.getCurrentTime())
                .get(statType);
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

    private static void testAccuracyPhaseF_XianglingGuobaNoIcdApplicationContract() {
        RecordingDamageWeapon reactingWeapon = new RecordingDamageWeapon("Guoba Attack");
        model.character.Xiangling reactingXiangling = new model.character.Xiangling(
                reactingWeapon, blankArtifact());
        CombatSimulator reactingSim = simulatorWithExistingCharacter(reactingXiangling);
        TestCharacter reactingAlly = testCharacter(Element.PYRO, CharacterId.BENNETT);
        reactingSim.addCharacter(reactingAlly);
        reactingSim.setActiveCharacter(CharacterId.BENNETT);
        List<ReactionResult.Kind> reactingKinds = captureReactionKinds(reactingSim);
        reactingSim.getEnemy().setAura(Element.HYDRO, 4.0, reactingSim.getCurrentTime());
        reactingSim.performAction(CharacterId.XIANGLING,
                CharacterActionRequest.of(CharacterActionKey.SKILL));

        reactingSim.advanceTime(1.999);
        assertClose(0.0, resolvedStat(reactingSim, reactingXiangling, StatType.PYRO_RES_SHRED), EPS,
                "Guoba C1 shred should be absent before the first flame hit");
        assertTrue(reactingSim.getTeamBuffList().stream()
                        .noneMatch(buff -> buff.getId() == BuffId.XIANGLING_GUOBA_C1_SHRED),
                "Guoba C1 should not create a typed status before its first hit");

        double[] expectedTimes = {2.0, 3.5, 5.0, 6.5};
        for (int i = 0; i < expectedTimes.length; i++) {
            reactingSim.advanceTime(i == 0 ? 0.001 : 1.5);
            assertEquals(i + 1, reactingWeapon.actions.size(),
                    "Each Guoba timestamp should execute exactly one new flame hit");
            mechanics.buff.Buff shred = requireGuobaC1Shred(reactingSim);
            assertClose(expectedTimes[i], shred.getStartTime(), EPS,
                    "Each Guoba hit should refresh the C1 start time");
            assertClose(expectedTimes[i] + 6.0, shred.getExpirationTime(), EPS,
                    "Each Guoba hit should refresh one six-second C1 window");
            assertClose(0.15, resolvedStat(reactingSim, reactingXiangling, StatType.PYRO_RES_SHRED), EPS,
                    "Off-field Xiangling should observe the enemy's refreshed C1 shred");
            assertClose(0.15, resolvedStat(reactingSim, reactingAlly, StatType.PYRO_RES_SHRED), EPS,
                    "The active ally should observe the same refreshed C1 shred");
        }

        assertEquals(4, reactingWeapon.actions.size(), "Guoba should deal four periodic hits");
        for (int i = 0; i < expectedTimes.length; i++) {
            assertClose(expectedTimes[i], reactingWeapon.times.get(i), EPS,
                    "Guoba should use its sourced four-hit cadence");
        }
        assertEquals(4, countReactions(reactingKinds, ReactionResult.Kind.VAPORIZE),
                "Every Guoba hit should apply Pyro and Vaporize without ICD");
        assertTrue(reactingWeapon.actions.stream().allMatch(action -> action.getElement() == Element.PYRO
                        && action.getActionType() == ActionType.SKILL
                        && action.getICDType() == ICDType.None
                        && action.getICDTag() == ICDTag.ElementalSkill
                        && action.getGaugeUnits() == 1.0),
                "Every Guoba hit should retain its sourced Pyro Skill application contract");

        reactingSim.advanceTime(5.999);
        assertClose(0.15, resolvedStat(reactingSim, reactingXiangling, StatType.PYRO_RES_SHRED), EPS,
                "Final Guoba C1 shred should remain active immediately before six seconds");
        Buff finalShred = requireGuobaC1Shred(reactingSim);
        StatsContainer atExactExpiry = new StatsContainer();
        finalShred.apply(atExactExpiry, finalShred.getExpirationTime());
        assertClose(0.0, atExactExpiry.get(StatType.PYRO_RES_SHRED), EPS,
                "Final Guoba C1 shred should exclude its exact expiry");
        reactingSim.advanceTime(0.001001);
        assertClose(0.0, resolvedStat(reactingSim, reactingXiangling, StatType.PYRO_RES_SHRED), EPS,
                "Final Guoba C1 shred should be absent after 12.5 seconds");

        RecordingDamageWeapon noAuraWeapon = new RecordingDamageWeapon("Guoba Attack");
        model.character.Xiangling noAuraXiangling = new model.character.Xiangling(
                noAuraWeapon, blankArtifact());
        CombatSimulator noAuraSim = simulatorWithExistingCharacter(noAuraXiangling);
        List<ReactionResult.Kind> noAuraKinds = captureReactionKinds(noAuraSim);
        noAuraSim.performAction(CharacterId.XIANGLING,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        noAuraSim.advanceTime(8.01);

        assertEquals(4, noAuraWeapon.actions.size(),
                "Guoba should not produce a fifth hit after its field duration");
        assertEquals(0, noAuraKinds.size(), "Guoba should not fabricate reactions without an aura");
    }

    private static Buff requireGuobaC1Shred(CombatSimulator sim) {
        long count = sim.getTeamBuffList().stream()
                .filter(buff -> buff.getId() == BuffId.XIANGLING_GUOBA_C1_SHRED)
                .count();
        assertEquals(1L, count, "Guoba C1 refresh should retain one typed team buff");
        Buff shred = sim.getTeamBuffList().stream()
                .filter(buff -> buff.getId() == BuffId.XIANGLING_GUOBA_C1_SHRED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Guoba C1 shred should exist"));
        assertEquals(CharacterId.XIANGLING, shred.getSourceCharacterId(),
                "Guoba C1 shred should be sourced by Xiangling");
        return shred;
    }

    private static void testAccuracyPhaseF_XingqiuSkillNoIcdApplicationContract() {
        RecordingDamageWeapon reactingWeapon = new RecordingDamageWeapon("Fatal Rainscreen");
        model.character.Xingqiu reactingXingqiu = new model.character.Xingqiu(
                reactingWeapon, blankArtifact());
        CombatSimulator reactingSim = simulatorWithExistingCharacter(reactingXingqiu);
        List<ReactionResult.Kind> reactingKinds = captureReactionKinds(reactingSim);
        reactingSim.getEnemy().setAura(Element.PYRO, 4.0, reactingSim.getCurrentTime());
        reactingSim.performAction(CharacterId.XINGQIU,
                CharacterActionRequest.of(CharacterActionKey.SKILL));

        assertEquals(2, reactingWeapon.actions.size(), "Fatal Rainscreen should deal two Skill hits");
        assertEquals(2, countReactions(reactingKinds, ReactionResult.Kind.VAPORIZE),
                "Both Fatal Rainscreen hits should apply Hydro and Vaporize without ICD");
        assertTrue(reactingWeapon.actions.stream().allMatch(action -> action.getElement() == Element.HYDRO
                        && action.getActionType() == ActionType.SKILL
                        && action.getICDType() == ICDType.None
                        && action.getICDTag() == ICDTag.ElementalSkill
                        && action.getGaugeUnits() == 1.0),
                "Both Fatal Rainscreen hits should retain their sourced Hydro Skill contract");

        RecordingDamageWeapon noAuraWeapon = new RecordingDamageWeapon("Fatal Rainscreen");
        model.character.Xingqiu noAuraXingqiu = new model.character.Xingqiu(
                noAuraWeapon, blankArtifact());
        CombatSimulator noAuraSim = simulatorWithExistingCharacter(noAuraXingqiu);
        List<ReactionResult.Kind> noAuraKinds = captureReactionKinds(noAuraSim);
        noAuraSim.performAction(CharacterId.XINGQIU,
                CharacterActionRequest.of(CharacterActionKey.SKILL));

        assertEquals(2, noAuraWeapon.actions.size(),
                "Fatal Rainscreen should still deal two hits without an aura");
        assertEquals(0, noAuraKinds.size(),
                "Fatal Rainscreen should not fabricate reactions without an aura");
    }

    private static void testAccuracyPhaseF_BennettSkillAndBurstApplicationContract() {
        RecordingDamageWeapon skillWeapon = new RecordingDamageWeapon("Passion Overload");
        model.character.Bennett skillBennett = new model.character.Bennett(skillWeapon, blankArtifact());
        CombatSimulator skillSim = simulatorWithExistingCharacter(skillBennett);
        List<ReactionResult.Kind> skillKinds = captureReactionKinds(skillSim);
        skillSim.getEnemy().setAura(Element.ELECTRO, 2.0, skillSim.getCurrentTime());
        skillSim.performAction(CharacterId.BENNETT,
                CharacterActionRequest.of(CharacterActionKey.SKILL));

        assertEquals(1, skillWeapon.actions.size(), "Bennett Press should deal one Skill hit");
        AttackAction skill = skillWeapon.actions.get(0);
        assertEquals(Element.PYRO, skill.getElement(), "Bennett Press should deal Pyro damage");
        assertEquals(ActionType.SKILL, skill.getActionType(), "Bennett Press should retain Skill typing");
        assertEquals(ICDType.None, skill.getICDType(), "Bennett Press should have no ICD");
        assertEquals(ICDTag.ElementalSkill, skill.getICDTag(), "Bennett Press should retain its Skill tag");
        assertClose(2.0, skill.getGaugeUnits(), EPS, "Bennett Press should apply 2U Pyro");
        assertEquals(1, countReactions(skillKinds, ReactionResult.Kind.OVERLOAD),
                "Bennett Press should trigger Overloaded against Electro");

        RecordingDamageWeapon burstWeapon = new RecordingDamageWeapon("Fantastic Voyage Hit");
        model.character.Bennett burstBennett = new model.character.Bennett(burstWeapon, blankArtifact());
        CombatSimulator burstSim = simulatorWithExistingCharacter(burstBennett);
        List<ReactionResult.Kind> burstKinds = captureReactionKinds(burstSim);
        burstSim.getEnemy().setAura(Element.HYDRO, 2.0, burstSim.getCurrentTime());
        captureStandardOutput(() -> burstSim.performAction(CharacterId.BENNETT,
                CharacterActionRequest.of(CharacterActionKey.BURST)));

        assertEquals(1, burstWeapon.actions.size(), "Fantastic Voyage should deal one Burst hit");
        AttackAction burst = burstWeapon.actions.get(0);
        assertEquals(Element.PYRO, burst.getElement(), "Fantastic Voyage should deal Pyro damage");
        assertEquals(ActionType.BURST, burst.getActionType(), "Fantastic Voyage should retain Burst typing");
        assertEquals(ICDType.None, burst.getICDType(), "Fantastic Voyage should have no ICD");
        assertEquals(ICDTag.ElementalBurst, burst.getICDTag(),
                "Fantastic Voyage should retain its Burst tag");
        assertClose(2.0, burst.getGaugeUnits(), EPS, "Fantastic Voyage should apply 2U Pyro");
        assertEquals(1, countReactions(burstKinds, ReactionResult.Kind.VAPORIZE),
                "Fantastic Voyage should trigger Vaporize against Hydro");

        RecordingDamageWeapon noAuraSkillWeapon = new RecordingDamageWeapon("Passion Overload");
        model.character.Bennett noAuraSkillBennett = new model.character.Bennett(
                noAuraSkillWeapon, blankArtifact());
        CombatSimulator noAuraSkillSim = simulatorWithExistingCharacter(noAuraSkillBennett);
        List<ReactionResult.Kind> noAuraSkillKinds = captureReactionKinds(noAuraSkillSim);
        noAuraSkillSim.performAction(CharacterId.BENNETT,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(1, noAuraSkillWeapon.actions.size(),
                "Bennett Press should still deal damage without an aura");
        assertEquals(0, noAuraSkillKinds.size(),
                "Bennett Press should not fabricate reactions without an aura");

        RecordingDamageWeapon noAuraBurstWeapon = new RecordingDamageWeapon("Fantastic Voyage Hit");
        model.character.Bennett noAuraBurstBennett = new model.character.Bennett(
                noAuraBurstWeapon, blankArtifact());
        CombatSimulator noAuraBurstSim = simulatorWithExistingCharacter(noAuraBurstBennett);
        List<ReactionResult.Kind> noAuraBurstKinds = captureReactionKinds(noAuraBurstSim);
        captureStandardOutput(() -> noAuraBurstSim.performAction(CharacterId.BENNETT,
                CharacterActionRequest.of(CharacterActionKey.BURST)));
        assertEquals(1, noAuraBurstWeapon.actions.size(),
                "Fantastic Voyage should still deal damage without an aura");
        assertEquals(0, noAuraBurstKinds.size(),
                "Fantastic Voyage should not fabricate reactions without an aura");
    }

    private static void testAccuracyPhaseF_RaidenCastAndMusouIcdContract() {
        RecordingDamageWeapon skillWeapon = new RecordingDamageWeapon("");
        model.character.RaidenShogun skillRaiden = new model.character.RaidenShogun(
                skillWeapon, blankArtifact());
        CombatSimulator skillSim = simulatorWithExistingCharacter(skillRaiden);
        List<ReactionResult.Kind> skillKinds = captureReactionKinds(skillSim);
        skillSim.getEnemy().setAura(Element.CRYO, 4.0, skillSim.getCurrentTime());
        captureStandardOutput(() -> skillSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL)));
        AttackAction eyeTrigger = damageHit("Raiden Eye Trigger", Element.PHYSICAL, 1.0);
        eyeTrigger.setAnimationDuration(0.0);
        skillSim.getEnemy().setAura(Element.CRYO, 4.0, skillSim.getCurrentTime());
        captureStandardOutput(() -> skillSim.performAction(CharacterId.RAIDEN_SHOGUN, eyeTrigger));
        skillSim.advanceTime(0.9);
        skillSim.getEnemy().setAura(Element.CRYO, 4.0, skillSim.getCurrentTime());
        captureStandardOutput(() -> skillSim.performAction(CharacterId.RAIDEN_SHOGUN, eyeTrigger));

        AttackAction skillCast = findAction(skillWeapon.actions, "Raiden E Cast");
        AttackAction firstEye = findAction(skillWeapon.actions, "Eye of Stormy Judgment");
        assertEquals(ICDType.None, skillCast.getICDType(), "Raiden Skill cast should have no ICD");
        assertEquals(ICDTag.ElementalSkill, skillCast.getICDTag(),
                "Raiden Skill cast should retain its Skill tag");
        assertClose(1.0, skillCast.getGaugeUnits(), EPS, "Raiden Skill cast should apply 1U Electro");
        assertEquals(ICDType.Standard, firstEye.getICDType(), "Raiden Eye should use standard ICD");
        assertEquals(ICDTag.ElementalSkill, firstEye.getICDTag(), "Raiden Eye should retain its Skill tag");
        assertClose(1.0, firstEye.getGaugeUnits(), EPS, "Raiden Eye should apply 1U Electro");
        assertEquals(2, countReactions(skillKinds, ReactionResult.Kind.SUPERCONDUCT),
                "Skill cast and first Eye should both apply before Eye ICD blocks its next hit");

        RecordingDamageWeapon burstWeapon = new RecordingDamageWeapon("");
        model.character.RaidenShogun burstRaiden = new model.character.RaidenShogun(
                burstWeapon, blankArtifact());
        CombatSimulator burstSim = simulatorWithExistingCharacter(burstRaiden);
        List<ReactionResult.Kind> burstKinds = captureReactionKinds(burstSim);
        captureStandardOutput(() -> burstSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.BURST)));

        AttackAction burstCast = findAction(burstWeapon.actions, "Musou Shinsetsu");
        assertEquals(ICDType.None, burstCast.getICDType(), "Raiden Burst initial hit should have no ICD");
        assertEquals(ICDTag.ElementalBurst, burstCast.getICDTag(),
                "Raiden Burst initial hit should retain its Burst tag");
        assertClose(2.0, burstCast.getGaugeUnits(), EPS, "Raiden Burst initial hit should apply 2U Electro");

        burstSim.setEnemy(new Enemy(90));
        burstSim.getEnemy().setAura(Element.PYRO, 4.0, burstSim.getCurrentTime());
        captureStandardOutput(() -> burstSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.NORMAL)));
        burstSim.getEnemy().setAura(Element.PYRO, 4.0, burstSim.getCurrentTime());
        captureStandardOutput(() -> burstSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.CHARGE)));

        AttackAction burstNormal = findAction(burstWeapon.actions, "Raiden Burst N1");
        AttackAction burstCharge = findAction(burstWeapon.actions, "Raiden Burst CA");
        assertEquals(ICDTag.Raiden_MusouIsshin, burstNormal.getICDTag(),
                "Raiden Burst Normal should use the shared Musou tag");
        assertEquals(ICDTag.Raiden_MusouIsshin, burstCharge.getICDTag(),
                "Raiden Burst Charged should use the shared Musou tag");
        assertEquals(ICDType.Standard, burstNormal.getICDType(),
                "Raiden Burst Normal should use standard ICD");
        assertEquals(ICDType.Standard, burstCharge.getICDType(),
                "Raiden Burst Charged should use standard ICD");
        assertEquals(1, countReactions(burstKinds, ReactionResult.Kind.OVERLOAD),
                "Immediate Burst Normal and Charged attacks should share one ICD group");

        RecordingDamageWeapon physicalWeapon = new RecordingDamageWeapon("Raiden ");
        model.character.RaidenShogun physicalRaiden = new model.character.RaidenShogun(
                physicalWeapon, blankArtifact());
        CombatSimulator physicalSim = simulatorWithExistingCharacter(physicalRaiden);
        physicalSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.NORMAL));
        physicalSim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertEquals(ICDTag.NormalAttack, findAction(physicalWeapon.actions, "Raiden N1").getICDTag(),
                "Physical Raiden Normal should retain the generic Normal tag");
        assertEquals(ICDTag.ChargedAttack, findAction(physicalWeapon.actions, "Raiden CA").getICDTag(),
                "Physical Raiden Charged should retain the generic Charged tag");
    }

    private static void testAccuracyPhaseF_RaidenEyeBuffRefreshContract() {
        model.character.RaidenShogun raiden = new model.character.RaidenShogun(
                new TestWeapon(), blankArtifact());
        TestCharacter ally = testCharacter(Element.HYDRO, CharacterId.XINGQIU);
        CombatSimulator sim = simulatorWithExistingCharacter(raiden);
        sim.addCharacter(ally);

        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(0.27, resolvedStat(sim, raiden, StatType.BURST_DMG_BONUS), EPS,
                "Raiden Eye should scale from Raiden's 90-Energy Burst cost");
        assertClose(0.18, resolvedStat(sim, ally, StatType.BURST_DMG_BONUS), EPS,
                "Raiden Eye should scale independently from an ally's 60-Energy Burst cost");
        assertRaidenEyeBuff(raiden, "Initial Raiden Eye buff");
        assertRaidenEyeBuff(ally, "Initial ally Eye buff");

        sim.advanceTime(9.5);
        assertClose(10.0, sim.getCurrentTime(), EPS,
                "Raiden Eye fixture should reach the exact Skill cooldown boundary");
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(10.5, sim.getCurrentTime(), EPS,
                "Raiden Eye refresh should retain the existing Skill action time");
        assertClose(0.27, resolvedStat(sim, raiden, StatType.BURST_DMG_BONUS), EPS,
                "Raiden Eye recast should refresh rather than double Raiden's bonus");
        assertClose(0.18, resolvedStat(sim, ally, StatType.BURST_DMG_BONUS), EPS,
                "Raiden Eye recast should refresh rather than double the ally's bonus");
        Buff refreshedRaidenEye = assertRaidenEyeBuff(raiden, "Refreshed Raiden Eye buff");
        Buff refreshedAllyEye = assertRaidenEyeBuff(ally, "Refreshed ally Eye buff");
        assertClose(35.5, refreshedRaidenEye.getExpirationTime(), EPS,
                "Refreshed Raiden Eye should retain a 25-second window");
        assertClose(35.5, refreshedAllyEye.getExpirationTime(), EPS,
                "Refreshed ally Eye should retain a 25-second window");

        sim.advanceTime(24.999);
        assertClose(0.27, resolvedStat(sim, raiden, StatType.BURST_DMG_BONUS), EPS,
                "Refreshed Raiden Eye should remain active immediately before 25 seconds");
        assertClose(0.18, resolvedStat(sim, ally, StatType.BURST_DMG_BONUS), EPS,
                "Refreshed ally Eye should remain active immediately before 25 seconds");
        StatsContainer raidenAtExpiry = new StatsContainer();
        refreshedRaidenEye.apply(raidenAtExpiry, refreshedRaidenEye.getExpirationTime());
        assertClose(0.0, raidenAtExpiry.get(StatType.BURST_DMG_BONUS), EPS,
                "Raiden Eye's half-open window should exclude its exact expiry");
        StatsContainer allyAtExpiry = new StatsContainer();
        refreshedAllyEye.apply(allyAtExpiry, refreshedAllyEye.getExpirationTime());
        assertClose(0.0, allyAtExpiry.get(StatType.BURST_DMG_BONUS), EPS,
                "Ally Eye's half-open window should exclude its exact expiry");
        sim.advanceTime(0.001001);
        assertClose(0.0, resolvedStat(sim, raiden, StatType.BURST_DMG_BONUS), EPS,
                "Refreshed Raiden Eye should be absent after 25 seconds");
        assertClose(0.0, resolvedStat(sim, ally, StatType.BURST_DMG_BONUS), EPS,
                "Refreshed ally Eye should be absent after 25 seconds");
    }

    private static Buff assertRaidenEyeBuff(Character character, String message) {
        long count = character.getActiveBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT)
                .count();
        assertEquals(1L, count, message + " count");
        Buff eyeBuff = character.getActiveBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.RAIDEN_EYE_OF_STORMY_JUDGMENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError(message + " should exist"));
        assertEquals(CharacterId.RAIDEN_SHOGUN, eyeBuff.getSourceCharacterId(),
                message + " source");
        return eyeBuff;
    }

    private static void testAccuracyPhaseF_PeriodicCancellationAndRaidenEyeDamageTrigger() {
        int[] cancelledCallbacks = {0};
        CombatSimulator cancelledSim = simulatorWith(testCharacter(Element.PHYSICAL));
        PeriodicDamageEvent cancelledEvent = new PeriodicDamageEvent(
                "Reaction Tester", null, 1.0, 1.0, 3.0,
                sim -> cancelledCallbacks[0]++);
        cancelledSim.registerEvent(cancelledEvent);
        cancelledEvent.cancel();
        cancelledEvent.cancel();
        cancelledSim.advanceTime(2.0);
        assertEquals(0, cancelledCallbacks[0],
                "Cancelled periodic events should not execute callbacks");

        int[] activeCallbacks = {0};
        CombatSimulator activeSim = simulatorWith(testCharacter(Element.PHYSICAL));
        activeSim.registerEvent(new PeriodicDamageEvent(
                "Reaction Tester", null, 1.0, 1.0, 3.0,
                sim -> activeCallbacks[0]++));
        activeSim.advanceTime(1.0);
        assertEquals(1, activeCallbacks[0],
                "Non-cancelled periodic events should retain their cadence");

        RecordingDamageWeapon eyeWeapon = new RecordingDamageWeapon("Eye of Stormy Judgment");
        model.character.RaidenShogun raiden = new model.character.RaidenShogun(
                eyeWeapon, blankArtifact());
        CombatSimulator sim = simulatorWithExistingCharacter(raiden);
        double[] eyeParticles = {0.0};
        sim.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
                eyeParticles[0] += count;
            }
        });
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL)));
        double eyeStartTime = sim.getCurrentTime();
        captureStandardOutput(() -> sim.advanceTime(3.0));
        assertEquals(0, eyeWeapon.actions.size(),
                "Raiden Eye should not attack while the party deals no damage");

        AttackAction trigger = damageHit("Eye Trigger", Element.PHYSICAL, 1.0);
        trigger.setAnimationDuration(0.0);
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        assertEquals(1, eyeWeapon.actions.size(),
                "Positive timeline damage should trigger one Raiden Eye attack");
        assertClose(0.5, eyeParticles[0], EPS,
                "Each coordinated attack should emit 0.5 expected Electro particles");

        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        sim.advanceTime(0.899);
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        assertEquals(1, eyeWeapon.actions.size(),
                "Damage inside the Eye cooldown should not trigger another attack");

        sim.advanceTime(0.001);
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        assertEquals(2, eyeWeapon.actions.size(),
                "Damage at the exact Eye cooldown boundary should trigger");

        sim.advanceTime(0.9);
        AttackAction zeroDamage = damageHit("Zero Damage Eye Trigger", Element.PHYSICAL, 0.0);
        zeroDamage.setAnimationDuration(0.0);
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, zeroDamage));
        assertEquals(2, eyeWeapon.actions.size(),
                "Zero direct damage should not trigger Raiden Eye");

        captureStandardOutput(() -> sim.performActionWithoutTimeAdvance(CharacterId.RAIDEN_SHOGUN, trigger));
        captureStandardOutput(() -> sim.advanceTime(0.0));
        assertEquals(3, eyeWeapon.actions.size(),
                "No-time-advance damage should trigger through resolved-damage dispatch");

        sim.advanceTime(eyeStartTime + 25.0 - sim.getCurrentTime());
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        assertEquals(3, eyeWeapon.actions.size(),
                "Expired Eye state should reject otherwise eligible damage");

        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN,
                CharacterActionRequest.of(CharacterActionKey.SKILL)));
        captureStandardOutput(() -> sim.performAction(CharacterId.RAIDEN_SHOGUN, trigger));
        assertEquals(4, eyeWeapon.actions.size(),
                "Skill refresh should retain one listener-owned Eye trigger");
        assertClose(2.0, eyeParticles[0], EPS,
                "Four coordinated attacks should emit four expected particle procs");
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

    private static int countTimesAfter(List<Double> times, double boundary) {
        int count = 0;
        for (double time : times) {
            if (time > boundary) {
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

    private static double calculateDirectDamage(
            CombatSimulator sim,
            Character attacker,
            AttackAction action,
            double currentTime,
            double reactionMultiplier) {
        return DamageCalculator.calculateDamage(
                attacker,
                sim.getEnemy(),
                action,
                sim.getApplicableBuffs(attacker),
                currentTime,
                reactionMultiplier,
                sim);
    }

    private static double performAndMeasureDamage(CombatSimulator sim, AttackAction action) {
        double beforeDamage = sim.getTotalDamage();
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, action);
        return sim.getTotalDamage() - beforeDamage;
    }

    private static mechanics.optimization.ArtifactOptimizer.OptimizationConfig energyOnlyArtifactConfig(
            double minER) {
        mechanics.optimization.ArtifactOptimizer.OptimizationConfig config =
                new mechanics.optimization.ArtifactOptimizer.OptimizationConfig();
        config.mainStatSands = StatType.HP_PERCENT;
        config.mainStatGoblet = StatType.ATK_PERCENT;
        config.mainStatCirclet = StatType.DEF_PERCENT;
        config.subStatPriority = java.util.List.of(StatType.ENERGY_RECHARGE);
        config.minER = minER;
        config.useCritRatio = false;
        return config;
    }

    private static String captureArtifactErFailure(
            mechanics.optimization.ArtifactOptimizer.OptimizationConfig config,
            StatsContainer baseStats,
            StatsContainer emptyStats) {
        String[] message = { null };
        captureStandardOutput(() -> {
            try {
                mechanics.optimization.ArtifactOptimizer.generate(
                        config, baseStats, emptyStats, emptyStats);
            } catch (IllegalStateException expected) {
                message[0] = expected.getMessage();
            }
        });
        assertTrue(message[0] != null, "Artifact optimizer should reject an unreachable ER target");
        return message[0];
    }

    private static List<String> runMoondriftDrawSequence(DoubleSupplier drawSource) {
        model.character.Columbina columbina = new model.character.Columbina(
                new TestWeapon(), blankArtifact(), drawSource);
        CombatSimulator sim = simulatorWithExistingCharacter(columbina);
        List<String> actions = new ArrayList<>();
        sim.addListener((actor, action, time) -> {
            if (action.getName().startsWith("Interference (Crystallize)")) {
                actions.add(action.getName());
            }
        });
        columbina.onAction(CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        columbina.onAction(CharacterActionRequest.of(CharacterActionKey.BURST), sim);
        ReactionResult crystallize = ReactionResult.lunar(
                0.0, ReactionResult.LunarType.CRYSTALLIZE);
        for (int i = 0; i < 6; i++) {
            sim.advanceTime(2.0);
            columbina.onReaction(crystallize, columbina, sim.getCurrentTime(), sim);
        }
        return actions;
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

    private static TestCharacter testCharacter(Element element, CharacterId characterId) {
        return new TestCharacter(element, characterId);
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
            this(element, CharacterId.SUCROSE);
        }

        private TestCharacter(Element element, CharacterId characterId) {
            this.name = "Reaction Tester";
            this.characterId = characterId;
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

    /** Captures matching damage actions from the production damage-hook path. */
    private static final class RecordingDamageWeapon extends Weapon
            implements model.entity.DamageTriggeredWeaponEffect {
        private final String actionNamePrefix;
        private final List<AttackAction> actions = new ArrayList<>();
        private final List<Double> times = new ArrayList<>();

        private RecordingDamageWeapon(String actionNamePrefix) {
            super("Recording Damage Weapon", new StatsContainer());
            this.actionNamePrefix = actionNamePrefix;
        }

        @Override
        public void onDamage(
                Character user,
                AttackAction action,
                double currentTime,
                CombatSimulator sim) {
            if (action.getName().startsWith(actionNamePrefix)) {
                actions.add(action);
                times.add(currentTime);
            }
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

    /** Supplies fixture team buffs through the artifact provider capability. */
    private static final class FixtureArtifactTeamBuffProvider extends ArtifactSet
            implements model.entity.ArtifactTeamBuffProvider {
        private FixtureArtifactTeamBuffProvider() {
            super("Artifact Team Provider Fixture", new StatsContainer());
        }

        @Override
        public List<Buff> getArtifactTeamBuffs(Character owner, CombatSimulator sim) {
            Buff fallbackSource = new SimpleBuff("Artifact provider fallback source",
                    Double.MAX_VALUE, 0.0, stats -> stats.add(StatType.ATK_FLAT, 40.0));
            Buff explicitSource = new SimpleBuff("Artifact provider explicit source",
                    Double.MAX_VALUE, 0.0, stats -> stats.add(StatType.DEF_FLAT, 25.0))
                    .forElement(Element.PYRO)
                    .sourcedBy(CharacterId.COLUMBINA);
            return java.util.List.of(fallbackSource, explicitSource);
        }
    }
}
