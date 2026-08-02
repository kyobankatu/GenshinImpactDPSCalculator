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
import mechanics.reaction.ReactionEffectScheduler;
import mechanics.reaction.ReactionPriority;
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
import simulation.runtime.ReactionState;

/**
 * Lightweight regression checks for elemental reaction behavior.
 */
public class ReactionRegressionTest {
    private static final double EPS = 1e-6;

    public static void main(String[] args) {
        testPhase1ReactionMetadataAndMultipliers();
        testAccuracyPhaseG_ReactionPriorityContract();
        testPhase2Superconduct();
        testPhase3FreezeAndShatter();
        testAccuracyPhaseG_FreezeResolverContract();
        testAccuracyPhaseG_PyroFrozenMeltContract();
        testPhase4Crystallize();
        testPhase5Burning();
        testAccuracyPhaseG_BurningRefreshAndGenerationContract();
        testAccuracyPhaseG_QuickenBurningFuelContract();
        testPhase6BloomCores();
        testPhase7HyperbloomAndBurgeon();
        testPhase8QuickenAggravateSpread();
        testAccuracyPhaseG_QuickenBloomConsumptionContract();
        testPhase9LunarReactionConversion();
        testPhase10LunarChargedThundercloud();
        testPhase11LunarBloom();
        testPhase12LunarCrystallize();
        testAccuracyPhaseA_AuraDecayOneUnit();
        testAccuracyPhaseA_AuraDecayTwoUnitLongerThanOneUnit();
        testAccuracyPhaseA_VaporizeConsumesExpectedAura();
        testAccuracyPhaseA_ElectroChargedCoexistence();
        testAccuracyPhaseA_ElectroChargedPrematureExpiry();
        testAccuracyPhaseA_ElectroChargedRefreshOwnership();
        testAccuracyPhaseA_ElectroChargedDamageCooldown();
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
        testAccuracyPhaseC_CoreDamageCapSnapshotContract();
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
        testAccuracyPhaseF_ArtifactOptimizerStableResultOrder();
        testAccuracyPhaseF_ColumbinaGravityAndDewRegression();
        testAccuracyPhaseF_ColumbinaStandInBoundaries();
        testAccuracyPhaseF_ArtifactTeamBuffProviderRouting();
        testAccuracyPhaseF_AubadeInitializationContract();
        testAccuracyPhaseF_SilkenMoonsSerenadeDynamicBonus();
        testAccuracyPhaseF_AscendantBlessingExpiryReplacement();
        testAccuracyPhaseF_ArtifactLunarReactionBuffRegression();
        testAccuracyPhaseF_ViridescentVenererRefreshContract();
        testAccuracyPhaseF_NoblesseObligeRefreshContract();
        testAccuracyPhaseF_WeaponReactionBonusRegression();
        testAccuracyPhaseF_DragonsBaneTargetAuraContract();
        testAccuracyPhaseF_TargetAuraWeaponMetadata();
        testAccuracyPhaseF_StaticActionBonusWeaponMetadata();
        testAccuracyPhaseF_LegacyWeaponRefinements();
        testAccuracyPhaseF_SkillUseEventWeapons();
        testAccuracyPhaseF_WatatsumiWavewalkerWeapons();
        testAccuracyPhaseF_ReciprocalHitWeapons();
        testAccuracyPhaseF_ReactionWindowWeapons();
        testAccuracyPhaseF_HitStackWeapons();
        testAccuracyPhaseF_ActionUseWindowWeapons();
        testAccuracyPhaseF_KaeyaCharacterContract();
        testAccuracyPhaseF_AmberCharacterContract();
        testAccuracyPhaseF_DendroResonanceReactionEmContract();
        testAccuracyPhaseF_CryoResonanceConditionalCritContract();
        testAccuracyPhaseF_ElectroResonanceTypedTriggerContract();
        testAccuracyPhaseF_SucroseNoIcdApplicationContract();
        testAccuracyPhaseF_RaidenCastAndMusouIcdContract();
        testAccuracyPhaseF_RaidenEyeBuffRefreshContract();
        testAccuracyPhaseF_LiveResistanceSnapshotContract();
        testAccuracyPhaseF_ImmediateReactionLiveResistanceContract();
        testAccuracyPhaseF_DelayedReactionLiveResistanceContract();
        testAccuracyPhaseG_SourceAuraTaxAndDecayContract();
        testAccuracyPhaseG_SameElementAuraExtensionContract();
        testAccuracyPhaseG_AuraDecaySnapshotContract();
        testAccuracyPhaseG_AuraExpiryContract();
        testAccuracyPhaseG_FreezeStateContract();
        testAccuracyPhaseG_BurningStateSnapshotContract();
        testAccuracyPhaseG_QuickenStateSnapshotContract();
        testAccuracyPhaseG_InvalidSourceAuraContract();
        testAccuracyPhaseG_RuntimeAuraApplicationContract();
        testAccuracyPhaseG_AnemoGeoAuraConsumptionContract();
        testAccuracyPhaseG_BloomDirectionalAuraConsumptionContract();
        testAccuracyPhaseG_TransformativeResidualAuraContract();
        testAccuracyPhaseG_OverloadDamageSequenceContract();
        testAccuracyPhaseG_SwirlDamageSequenceContract();
        testAccuracyPhaseG_SuperconductDamageSequenceContract();
        testAccuracyPhaseG_ShatterDamageSequenceContract();
        testAccuracyPhaseG_StandardCrystallizeCooldownContract();
        testAccuracyPhaseF_PeriodicCancellationAndRaidenEyeDamageTrigger();
        testAccuracyPhaseF_BennettSkillAndBurstApplicationContract();
        testAccuracyPhaseF_XianglingGuobaNoIcdApplicationContract();
        testAccuracyPhaseF_XianglingChiliPickupOptIn();
        testAccuracyPhaseF_XingqiuSkillNoIcdApplicationContract();
        testAccuracyPhaseF_XingqiuOrbitalApplicationCadence();
        testAccuracyPhaseF_DamageHooksDispatchOnce();
        testAccuracyPhaseF_SkywardSpineInjectedProcBoundaries();
        testAccuracyPhaseF_FavoniusInjectedProcBoundaries();
        testAccuracyPhaseF_FavoniusFamilyMetadata();
        testAccuracyPhaseF_SacrificialSwordProcBoundaries();
        testAccuracyPhaseF_SacrificialFamilyMetadata();
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

    private static void testAccuracyPhaseG_ReactionPriorityContract() {
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.ELECTRO,
                        reverseSet(
                                Element.PYRO, Element.HYDRO, Element.CRYO,
                                Element.DENDRO, Element.ELECTRO)),
                Element.PYRO, Element.HYDRO, Element.CRYO,
                Element.DENDRO, Element.ELECTRO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.PYRO,
                        reverseSet(
                                Element.ELECTRO, Element.HYDRO, Element.CRYO,
                                Element.DENDRO, Element.PYRO)),
                Element.ELECTRO, Element.HYDRO, Element.CRYO,
                Element.DENDRO, Element.PYRO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.CRYO,
                        reverseSet(
                                Element.ELECTRO, Element.PYRO,
                                Element.HYDRO, Element.CRYO)),
                Element.ELECTRO, Element.PYRO, Element.HYDRO, Element.CRYO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.HYDRO,
                        reverseSet(
                                Element.PYRO, Element.CRYO, Element.DENDRO,
                                Element.ELECTRO, Element.HYDRO)),
                Element.PYRO, Element.CRYO, Element.DENDRO,
                Element.ELECTRO, Element.HYDRO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.ANEMO,
                        reverseSet(
                                Element.ELECTRO, Element.PYRO, Element.HYDRO,
                                Element.CRYO, Element.ANEMO)),
                Element.ELECTRO, Element.PYRO, Element.HYDRO,
                Element.CRYO, Element.ANEMO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.GEO,
                        reverseSet(
                                Element.ELECTRO, Element.HYDRO, Element.CRYO,
                                Element.PYRO, Element.GEO)),
                Element.ELECTRO, Element.HYDRO, Element.CRYO,
                Element.PYRO, Element.GEO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.DENDRO,
                        reverseSet(
                                Element.ELECTRO, Element.PYRO,
                                Element.HYDRO, Element.DENDRO)),
                Element.ELECTRO, Element.PYRO, Element.HYDRO, Element.DENDRO);
        assertElementOrder(
                ReactionPriority.orderAuras(
                        Element.PHYSICAL,
                        reverseSet(Element.ELECTRO, Element.HYDRO)),
                Element.HYDRO, Element.ELECTRO);

        CombatSimulator pyro = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> pyroKinds = captureReactionKinds(pyro);
        pyro.getEnemy().setAura(Element.HYDRO, 1.0);
        pyro.getEnemy().setAura(Element.ELECTRO, 1.0);
        pyro.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Pyro simultaneous priority fixture", Element.PYRO));
        assertEquals(ReactionResult.Kind.OVERLOAD, pyroKinds.get(0),
                "Pyro should notify Overloaded before Vaporize");
        assertEquals(ReactionResult.Kind.VAPORIZE, pyroKinds.get(1),
                "Pyro should notify Vaporize after Overloaded");

        CombatSimulator anemo = simulatorWith(testCharacter(Element.ANEMO));
        List<Element> swirlElements = new ArrayList<>();
        anemo.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.SWIRL) {
                swirlElements.add(result.getRelatedElement());
            }
        });
        anemo.getEnemy().setAura(Element.HYDRO, 1.0);
        anemo.getEnemy().setAura(Element.ELECTRO, 1.0);
        anemo.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Anemo simultaneous priority fixture", Element.ANEMO));
        assertElementOrder(swirlElements, Element.ELECTRO, Element.HYDRO);
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
        sim.getEnemy().applyAura(Element.HYDRO, 1.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Cryo freeze trigger", Element.CRYO));
        assertTrue(sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Cryo on Hydro should create Freeze Aura");
        assertClose(1.6, sim.getEnemy().getFreezeAuraUnits(sim.getCurrentTime()), EPS,
                "Equal 1U Cryo and Hydro sources should create 1.6U Frozen gauge");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Freeze should not deal immediate damage");

        AttackAction shatterHit = reactionHit("Blunt shatter trigger", Element.PHYSICAL);
        shatterHit.setShatterTrigger(true);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, shatterHit);
        assertTrue(!sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Shatter should clear Freeze Aura");
        assertClose(expectedTransformative(3.0, Element.PHYSICAL, 0.0), sim.getTotalDamage(), 0.5,
                "Shatter reaction damage after RES");
    }

    private static void testAccuracyPhaseG_FreezeResolverContract() {
        CombatSimulator hydroTrigger = simulatorWith(testCharacter(Element.HYDRO));
        hydroTrigger.getEnemy().applyAura(
                Element.CRYO, 1.0, hydroTrigger.getCurrentTime());
        hydroTrigger.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Hydro-on-Cryo finite Freeze fixture", Element.HYDRO));
        assertClose(1.6, hydroTrigger.getEnemy().getFreezeAuraUnits(
                hydroTrigger.getCurrentTime()), EPS,
                "Hydro on equal Cryo source should create 1.6U Frozen gauge");

        CombatSimulator extension = simulatorWith(testCharacter(Element.CRYO));
        extension.getEnemy().applyAura(Element.HYDRO, 2.0, extension.getCurrentTime());
        extension.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial extensible Freeze fixture", Element.CRYO));
        extension.advanceTime(0.5);
        Enemy.FreezeAuraState beforeExtension = extension.getEnemy().captureFreezeAuraState();
        double remainingBeforeExtension = beforeExtension.remainingUnitsAt(
                extension.getCurrentTime());
        double hydroBeforeExtension = extension.getEnemy().getAuraUnits(
                Element.HYDRO, extension.getCurrentTime());
        extension.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Active refreeze fixture", Element.CRYO));
        Enemy.FreezeAuraState afterExtension = extension.getEnemy().captureFreezeAuraState();
        assertClose(
                remainingBeforeExtension + 2.0 * Math.min(1.0, hydroBeforeExtension),
                afterExtension.units,
                EPS,
                "Matching application should add exact gauge to active Freeze");
        assertClose(beforeExtension.decayRateAt(extension.getCurrentTime()),
                afterExtension.decayRate, EPS,
                "Resolver refreeze should preserve instantaneous decay rate");
        assertTrue(afterExtension.getEndTime() > beforeExtension.getEndTime(),
                "Resolver refreeze should extend exact Frozen expiry");

        CombatSimulator expired = simulatorWith(testCharacter(Element.CRYO));
        List<ReactionResult.Kind> expiredKinds = captureReactionKinds(expired);
        expired.getEnemy().applyAura(Element.HYDRO, 1.0, expired.getCurrentTime());
        expired.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Expiring Freeze fixture", Element.CRYO));
        double freezeEnd = expired.getEnemy().captureFreezeAuraState().getEndTime();
        expired.advanceTime(freezeEnd - expired.getCurrentTime());
        AttackAction expiredShatter = reactionHit(
                "Exact-expiry Shatter fixture", Element.PHYSICAL);
        expiredShatter.setShatterTrigger(true);
        expired.performActionWithoutTimeAdvance(CharacterId.SUCROSE, expiredShatter);
        assertEquals(0, countReactions(expiredKinds, ReactionResult.Kind.SHATTER),
                "A blunt hit at exact Freeze expiry should not notify Shatter");
        assertClose(0.0, expired.getTotalDamage(), EPS,
                "A blunt hit at exact Freeze expiry should not deal Shatter damage");
        assertTrue(!expired.getEnemy().isFrozen(expired.getCurrentTime()),
                "Freeze should remain inactive after an exact-expiry blunt hit");
    }

    private static void testAccuracyPhaseG_PyroFrozenMeltContract() {
        CombatSimulator hiddenHydro = simulatorWith(testCharacter(Element.PYRO));
        hiddenHydro.getEnemy().applyAura(
                Element.HYDRO, 2.0, hiddenHydro.getCurrentTime());
        hiddenHydro.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Hidden Hydro Freeze fixture", Element.CRYO));
        List<ReactionResult.Kind> hiddenHydroKinds = captureReactionKinds(hiddenHydro);
        hiddenHydro.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                catalyzeDamageHit("Pyro-on-Frozen Melt fixture", Element.PYRO));
        assertEquals(1, hiddenHydroKinds.size(),
                "Non-blunt Pyro on Frozen should notify exactly one reaction");
        assertEquals(ReactionResult.Kind.MELT, hiddenHydroKinds.get(0),
                "Non-blunt Pyro on Frozen should Melt");
        assertClose(0.6, hiddenHydro.getEnemy().getAuraUnits(
                Element.HYDRO, hiddenHydro.getCurrentTime()), EPS,
                "Frozen Melt should preserve hidden Hydro aura");
        assertTrue(!hiddenHydro.getEnemy().isFrozen(hiddenHydro.getCurrentTime()),
                "A 1U Pyro hit should consume a 2U Frozen gauge");
        assertClose(expectedStandardDamage(0.0, 0.05, 0.50) * 2.0,
                hiddenHydro.getTotalDamage(), 0.5,
                "Pyro on Frozen should use the forward Melt multiplier");

        CombatSimulator hiddenCryo = simulatorWith(testCharacter(Element.PYRO));
        hiddenCryo.getEnemy().applyAura(
                Element.CRYO, 2.0, hiddenCryo.getCurrentTime());
        hiddenCryo.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Hidden Cryo Freeze fixture", Element.HYDRO));
        List<ReactionResult.Kind> hiddenCryoKinds = captureReactionKinds(hiddenCryo);
        AttackAction weakPyro = reactionHit(
                "Weak Pyro-on-Frozen Melt fixture", Element.PYRO);
        weakPyro.setICD(ICDType.None, ICDTag.None, 0.25);
        hiddenCryo.performActionWithoutTimeAdvance(CharacterId.SUCROSE, weakPyro);
        assertEquals(1, countReactions(hiddenCryoKinds, ReactionResult.Kind.MELT),
                "Weak Pyro on Frozen should notify one Melt");
        assertClose(1.5, hiddenCryo.getEnemy().getFreezeAuraUnits(
                hiddenCryo.getCurrentTime()), EPS,
                "Pyro Melt should consume Frozen gauge at twice trigger strength");
        assertClose(0.1, hiddenCryo.getEnemy().getAuraUnits(
                Element.CRYO, hiddenCryo.getCurrentTime()), EPS,
                "Pyro Melt should consume coexisting hidden Cryo at twice trigger strength");

        CombatSimulator strongFrozen = simulatorWith(testCharacter(Element.PYRO));
        strongFrozen.getEnemy().applyFreezeAura(
                3.0, strongFrozen.getCurrentTime());
        List<ReactionResult.Kind> strongFrozenKinds = captureReactionKinds(strongFrozen);
        AttackAction halfUnitPyro = reactionHit(
                "Strong Frozen partial-consumption fixture", Element.PYRO);
        halfUnitPyro.setICD(ICDType.None, ICDTag.None, 0.5);
        strongFrozen.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, halfUnitPyro);
        assertEquals(1, countReactions(strongFrozenKinds, ReactionResult.Kind.MELT),
                "Partial Frozen consumption should still notify one Melt");
        assertClose(2.0, strongFrozen.getEnemy().getFreezeAuraUnits(
                strongFrozen.getCurrentTime()), EPS,
                "A 0.5U Pyro hit should consume exactly 1U Frozen gauge");

        CombatSimulator blunt = simulatorWith(testCharacter(Element.PYRO));
        blunt.getEnemy().applyAura(Element.HYDRO, 2.0, blunt.getCurrentTime());
        blunt.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Blunt hidden Hydro Freeze fixture", Element.CRYO));
        List<ReactionResult.Kind> bluntKinds = captureReactionKinds(blunt);
        AttackAction bluntPyro = reactionHit(
                "Blunt Pyro-on-Frozen fixture", Element.PYRO);
        bluntPyro.setShatterTrigger(true);
        blunt.performActionWithoutTimeAdvance(CharacterId.SUCROSE, bluntPyro);
        assertEquals(2, bluntKinds.size(),
                "Blunt Pyro on Frozen should notify Shatter and exposed-aura reaction");
        assertEquals(ReactionResult.Kind.SHATTER, bluntKinds.get(0),
                "Blunt Pyro should Shatter before gauge resolution");
        assertEquals(ReactionResult.Kind.VAPORIZE, bluntKinds.get(1),
                "Blunt Pyro should Vaporize exposed hidden Hydro after Shatter");
        assertClose(0.1, blunt.getEnemy().getAuraUnits(
                Element.HYDRO, blunt.getCurrentTime()), EPS,
                "Reverse Vaporize should consume half of a 1U Pyro trigger");

        CombatSimulator expired = simulatorWith(testCharacter(Element.PYRO));
        expired.getEnemy().setAura(Element.HYDRO, 1.0, expired.getCurrentTime());
        expired.getEnemy().applyFreezeAura(1.6, expired.getCurrentTime());
        double freezeEnd = expired.getEnemy().captureFreezeAuraState().getEndTime();
        expired.advanceTime(freezeEnd - expired.getCurrentTime());
        List<ReactionResult.Kind> expiredKinds = captureReactionKinds(expired);
        expired.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Exact-expiry Pyro fixture", Element.PYRO));
        assertEquals(0, countReactions(expiredKinds, ReactionResult.Kind.MELT),
                "Pyro at exact Frozen expiry should not Melt");
        assertEquals(1, countReactions(expiredKinds, ReactionResult.Kind.VAPORIZE),
                "Pyro at exact Frozen expiry should react with exposed Hydro");
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
        assertClose(0.5, sim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "1U Crystallize should consume 0.5U from explicit Hydro fixture state");
    }

    private static void testPhase5Burning() {
        ReactionResult burning = ReactionCalculator.calculate(Element.PYRO, Element.DENDRO, 0.0, 90);
        assertEquals(ReactionResult.Kind.BURNING, burning.getKind(), "Burning kind");
        assertTrue(burning.isStateful(), "Burning should create persistent state");
        assertEquals(Element.PYRO, burning.getDamageElement(), "Burning damage element");
        assertClose(1446.85 * 0.25, burning.getTransformDamage(), 0.01, "Burning base tick damage");

        TestCharacter character = testCharacter(Element.PYRO);
        CombatSimulator sim = simulatorWith(character);
        sim.getEnemy().applyAura(Element.DENDRO, 1.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Pyro burning trigger", Element.PYRO));
        assertTrue(sim.isBurningActive(), "Burning should be active after Pyro on Dendro");
        assertClose(0.0, sim.getTotalDamage(), EPS, "Burning should not deal immediate damage");
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.DENDRO, sim.getCurrentTime()), EPS,
                "Pyro-on-Dendro should preserve the taxed Dendro fuel at trigger time");

        sim.advanceTime(0.24);
        assertClose(0.0, sim.getTotalDamage(), EPS,
                "Burning should remain silent before its first 0.25-second tick");
        sim.advanceTime(0.01);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0), sim.getTotalDamage(), 0.5,
                "First Burning tick after RES");

        sim.advanceTime(1.74);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 7.0,
                sim.getTotalDamage(), 1.0,
                "A 1U Dendro source should deal seven ticks before exact two-second depletion");
        sim.advanceTime(0.01);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                sim.getTotalDamage(), 1.0,
                "A 1U Dendro source should deal its eighth tick at exact depletion");
        assertTrue(!sim.isBurningActive(), "Burning should clear at exact Dendro fuel depletion");
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.DENDRO, sim.getCurrentTime()), EPS,
                "Exact Burning depletion should clear underlying Dendro fuel");
        sim.advanceTime(0.25);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                sim.getTotalDamage(), 1.0,
                "Burning should not deal a ninth tick after fuel depletion");

        TestCharacter dendroCharacter = testCharacter(Element.DENDRO);
        CombatSimulator reverse = simulatorWith(dendroCharacter);
        reverse.getEnemy().applyAura(Element.PYRO, 1.0, reverse.getCurrentTime());
        reverse.performActionWithoutTimeAdvance(CharacterId.SUCROSE, reactionHit("Dendro burning trigger", Element.DENDRO));
        assertTrue(reverse.isBurningActive(), "Burning should also trigger from Dendro on Pyro");
        assertClose(0.8, reverse.getEnemy().getAuraUnits(Element.PYRO, reverse.getCurrentTime()), EPS,
                "Dendro-on-Pyro should preserve the underlying taxed Pyro Aura");
        assertClose(0.8, reverse.getEnemy().getAuraUnits(Element.DENDRO, reverse.getCurrentTime()), EPS,
                "Dendro-on-Pyro should establish taxed Dendro fuel");

        CombatSimulator strong = simulatorWith(testCharacter(Element.PYRO));
        strong.getEnemy().applyAura(Element.DENDRO, 2.0, strong.getCurrentTime());
        strong.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("2U Pyro burning trigger", Element.PYRO));
        strong.advanceTime(4.0);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 16.0,
                strong.getTotalDamage(), 2.0,
                "A 2U Dendro source should supply sixteen Burning ticks over four seconds");
        assertTrue(!strong.isBurningActive(),
                "A 2U Dendro source should deplete after exactly four seconds");
    }

    private static void testAccuracyPhaseG_BurningRefreshAndGenerationContract() {
        CombatSimulator refresh = simulatorWith(testCharacter(Element.PYRO));
        ReactionEffectScheduler scheduler = new ReactionEffectScheduler(refresh);
        refresh.getEnemy().applyAura(Element.DENDRO, 2.0, refresh.getCurrentTime());
        scheduler.scheduleBurning(CharacterId.SUCROSE, 1000.0);
        refresh.advanceTime(0.25);
        assertClose(900.0, refresh.getDamageByCharacter(CharacterId.SUCROSE), EPS,
                "The initial Burning owner should receive the first live-RES tick");

        double endBeforePyroRefresh = refresh.getBurningState().getEndTime();
        scheduler.scheduleBurning(CharacterId.XIANGLING, 2000.0, false);
        assertClose(endBeforePyroRefresh, refresh.getBurningState().getEndTime(), EPS,
                "A Pyro refresh should not replace or extend Dendro fuel");
        refresh.advanceTime(0.25);
        assertClose(1800.0, refresh.getDamageByCharacter(CharacterId.XIANGLING), EPS,
                "The latest Pyro applier should own the next Burning tick");

        refresh.getEnemy().replaceAuraFromSource(
                Element.DENDRO, 0.5, refresh.getCurrentTime());
        scheduler.scheduleBurning(CharacterId.SUCROSE, 1500.0, true);
        ReactionState.BurningState overwritten = refresh.getBurningState();
        assertClose(0.4, overwritten.fuelUnits, EPS,
                "A weaker Dendro refresh should overwrite stronger remaining fuel");
        assertClose(1.5, overwritten.getEndTime(), EPS,
                "Overwritten 0.4U fuel should last one second at the minimum decay rate");
        assertEquals(CharacterId.SUCROSE, overwritten.ownerId,
                "The latest Dendro applier should own refreshed Burning damage");
        refresh.advanceTime(1.0);
        assertTrue(!refresh.isBurningActive(),
                "Overwritten weaker Dendro fuel should clear at its exact end");

        CombatSimulator stale = simulatorWith(testCharacter(Element.PYRO));
        ReactionEffectScheduler staleScheduler = new ReactionEffectScheduler(stale);
        stale.getEnemy().applyAura(Element.DENDRO, 1.0, stale.getCurrentTime());
        staleScheduler.scheduleBurning(CharacterId.SUCROSE, 1000.0);
        stale.clearBurning();
        staleScheduler.scheduleBurning(CharacterId.SUCROSE, 1000.0);
        stale.advanceTime(0.25);
        assertClose(900.0, stale.getTotalDamage(), EPS,
                "A superseded Burning event should terminate without duplicating damage");
    }

    private static void testAccuracyPhaseG_QuickenBurningFuelContract() {
        CombatSimulator quickenOnly = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> quickenOnlyKinds = captureReactionKinds(quickenOnly);
        quickenOnly.applyQuicken(0.8);
        quickenOnly.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Quicken-only Burning fixture", Element.PYRO));
        assertEquals(1, countReactions(quickenOnlyKinds, ReactionResult.Kind.BURNING),
                "Pyro on Quicken alone should emit one Burning reaction");
        assertClose(0.0, quickenOnly.getTotalDamage(), EPS,
                "Quicken-only Burning should not deal immediate damage");
        quickenOnly.advanceTime(0.24);
        assertClose(0.0, quickenOnly.getTotalDamage(), EPS,
                "Quicken-only Burning should remain silent before 0.25 seconds");
        quickenOnly.advanceTime(0.01);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0),
                quickenOnly.getTotalDamage(), 0.5,
                "Quicken-only Burning should deal its first tick at 0.25 seconds");
        quickenOnly.advanceTime(1.75);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                quickenOnly.getTotalDamage(), 1.0,
                "0.8U Quicken should supply eight Burning ticks over two seconds");
        assertTrue(quickenOnly.getQuickenState() == null,
                "Quicken-only Burning should consume typed Quicken exactly");
        assertTrue(!quickenOnly.isBurningActive(),
                "Quicken-only Burning should clear at exact shared-fuel depletion");
        quickenOnly.advanceTime(0.25);
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                quickenOnly.getTotalDamage(), 1.0,
                "Quicken-only Burning should not deal a ninth tick");

        CombatSimulator equal = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> equalKinds = captureReactionKinds(equal);
        equal.applyQuicken(0.8);
        equal.getEnemy().applyAura(Element.DENDRO, 1.0, equal.getCurrentTime());
        equal.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Equal Dendro-Quicken Burning fixture", Element.PYRO));
        equal.advanceTime(2.0);
        assertEquals(1, countReactions(equalKinds, ReactionResult.Kind.BURNING),
                "Coexisting equal fuels should emit one Burning reaction");
        assertClose(0.0, equal.getEnemy().getAuraUnits(
                Element.DENDRO, equal.getCurrentTime()), EPS,
                "Equal coexisting Dendro should deplete with shared fuel");
        assertTrue(equal.getQuickenState() == null,
                "Equal coexisting Quicken should deplete with shared fuel");
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                equal.getTotalDamage(), 1.0,
                "Equal coexisting fuels should retain one eight-tick stream");

        CombatSimulator unequal = simulatorWith(testCharacter(Element.PYRO));
        unequal.applyQuicken(0.8);
        unequal.getEnemy().applyAura(Element.DENDRO, 0.5, unequal.getCurrentTime());
        unequal.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Unequal Dendro-Quicken Burning fixture", Element.PYRO));
        unequal.advanceTime(1.0);
        assertClose(0.0, unequal.getEnemy().getAuraUnits(
                Element.DENDRO, unequal.getCurrentTime()), EPS,
                "The smaller coexisting Dendro fuel should deplete after one second");
        assertTrue(unequal.isQuickenActive(),
                "The larger Quicken fuel should sustain Burning after Dendro depletes");
        assertTrue(unequal.isBurningActive(),
                "Shared Burning should remain active while the larger fuel remains");
        unequal.advanceTime(1.0);
        assertTrue(unequal.getQuickenState() == null,
                "The larger Quicken fuel should deplete at the shared exact end");
        assertClose(expectedTransformative(0.25, Element.PYRO, 0.0) * 8.0,
                unequal.getTotalDamage(), 1.0,
                "Unequal fuels should retain the larger gauge's tick count");

        CombatSimulator refresh = simulatorWith(testCharacter(Element.PYRO));
        ReactionEffectScheduler refreshScheduler = new ReactionEffectScheduler(refresh);
        refresh.applyQuicken(0.8);
        refresh.getEnemy().applyAura(Element.DENDRO, 1.0, refresh.getCurrentTime());
        refreshScheduler.scheduleBurning(CharacterId.SUCROSE, 1000.0);
        refresh.advanceTime(0.5);
        refresh.getEnemy().replaceAuraFromSource(
                Element.DENDRO, 0.5, refresh.getCurrentTime());
        refreshScheduler.scheduleBurning(CharacterId.XIANGLING, 1200.0, true);
        assertClose(1.5, refresh.getBurningState().getEndTime(), EPS,
                "Dendro refresh should overwrite the shared Burning fuel end");
        refresh.advanceTime(1.0);
        assertTrue(!refresh.isBurningActive(),
                "Overwritten shared Burning fuel should clear at its exact end");
        assertClose(0.2, refresh.getQuickenState().units, EPS,
                "Dendro overwrite should still apply special decay to coexisting Quicken");

        CombatSimulator expired = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> expiredKinds = captureReactionKinds(expired);
        expired.applyQuicken(0.8);
        expired.advanceTime(10.0);
        expired.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Expired Quicken Burning fixture", Element.PYRO));
        assertEquals(0, countReactions(expiredKinds, ReactionResult.Kind.BURNING),
                "Pyro at exact Quicken expiry should not emit Burning");
        assertTrue(!expired.isBurningActive(),
                "Expired Quicken should not create Burning state");
        assertClose(0.8, expired.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Pyro should apply normally after exact Quicken expiry");
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
        aggravateSim.getEnemy().applyAura(
                Element.DENDRO, 1.0, aggravateSim.getCurrentTime());
        aggravateSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Electro quicken trigger", Element.ELECTRO));
        assertTrue(aggravateSim.isQuickenActive(), "Electro on Dendro should create Quicken");
        assertClose(0.8, aggravateSim.getQuickenState().units, EPS,
                "Electro on a taxed 1U Dendro Aura should create 0.8U Quicken");
        assertClose(10.0, aggravateSim.getQuickenState().getEndTime(), EPS,
                "A real 0.8U Quicken Aura should last ten seconds");
        assertClose(0.0, aggravateSim.getTotalDamage(), EPS, "Quicken should not deal immediate damage");

        double aggravateGauge = aggravateSim.getQuickenState().units;
        double aggravateEnd = aggravateSim.getQuickenState().getEndTime();
        aggravateSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Electro aggravated hit", Element.ELECTRO));
        assertClose(expectedStandardCatalyzeDamage(1.15, 0.50, 1.0, 1.0), aggravateSim.getTotalDamage(), 0.5,
                "Aggravate should add base damage before DMG Bonus/Crit/DEF/RES");
        assertClose(aggravateGauge, aggravateSim.getQuickenState().units, EPS,
                "Aggravate should not consume Quicken gauge");
        assertClose(aggravateEnd, aggravateSim.getQuickenState().getEndTime(), EPS,
                "Aggravate should not refresh Quicken expiry");

        ReactionResult spread = ReactionCalculator.calculateSpread(0.0, 90, 0.0);
        assertEquals(ReactionResult.Kind.SPREAD, spread.getKind(), "Spread kind");
        assertTrue(spread.canCrit(), "Spread additive damage should be able to crit through the hit");
        assertClose(1446.85 * 1.25, spread.getTransformDamage(), 0.01, "Spread additive base damage");

        TestCharacter dendroCharacter = testCharacter(Element.DENDRO)
                .withStat(StatType.CRIT_RATE, 1.0)
                .withStat(StatType.CRIT_DMG, 1.0)
                .withStat(StatType.DENDRO_DMG_BONUS, 0.50);
        CombatSimulator spreadSim = simulatorWith(dendroCharacter);
        spreadSim.getEnemy().applyAura(
                Element.ELECTRO, 1.0, spreadSim.getCurrentTime());
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Dendro quicken trigger", Element.DENDRO));
        assertTrue(spreadSim.isQuickenActive(), "Dendro on Electro should create Quicken");
        assertClose(0.8, spreadSim.getQuickenState().units, EPS,
                "Dendro on a taxed 1U Electro Aura should create 0.8U Quicken");
        double spreadGauge = spreadSim.getQuickenState().units;
        double spreadEnd = spreadSim.getQuickenState().getEndTime();
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Dendro spread hit", Element.DENDRO));
        assertClose(expectedStandardCatalyzeDamage(1.25, 0.50, 1.0, 1.0), spreadSim.getTotalDamage(), 0.5,
                "Spread should add base damage before DMG Bonus/Crit/DEF/RES");
        assertClose(spreadGauge, spreadSim.getQuickenState().units, EPS,
                "Spread should not consume Quicken gauge");
        assertClose(spreadEnd, spreadSim.getQuickenState().getEndTime(), EPS,
                "Spread should not refresh Quicken expiry");

        spreadSim.advanceTime(10.1);
        double beforeExpiredHit = spreadSim.getTotalDamage();
        spreadSim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                catalyzeDamageHit("Expired quicken Dendro hit", Element.DENDRO));
        double expiredHit = spreadSim.getTotalDamage() - beforeExpiredHit;
        assertClose(expectedStandardDamage(0.50, 1.0, 1.0), expiredHit, 0.5,
                "Expired Quicken should not trigger Spread");
    }

    private static void testAccuracyPhaseG_QuickenBloomConsumptionContract() {
        CombatSimulator quickenOnly = simulatorWith(testCharacter(Element.HYDRO));
        List<ReactionResult.Kind> quickenOnlyKinds = captureReactionKinds(quickenOnly);
        quickenOnly.applyQuicken(1.0);
        quickenOnly.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Quicken-only Bloom fixture", Element.HYDRO));
        assertEquals(1, countReactions(quickenOnlyKinds, ReactionResult.Kind.BLOOM),
                "Hydro on Quicken alone should emit one Bloom reaction");
        assertEquals(1, quickenOnly.getDendroCores().size(),
                "Hydro on Quicken alone should create one Dendro Core");
        assertEquals(CharacterId.SUCROSE, quickenOnly.getDendroCores().get(0).ownerId,
                "Quicken-only Bloom should retain the Hydro trigger owner");
        assertClose(0.5, quickenOnly.getQuickenState().units, EPS,
                "1U Hydro Bloom should consume 0.5U Quicken");
        assertClose(0.0, quickenOnly.getTotalDamage(), EPS,
                "Quicken-only Bloom should not deal immediate damage");

        CombatSimulator coexist = simulatorWith(testCharacter(Element.HYDRO));
        List<ReactionResult.Kind> coexistKinds = captureReactionKinds(coexist);
        coexist.applyQuicken(1.0);
        coexist.getEnemy().setAura(Element.DENDRO, 2.0);
        coexist.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Dendro-Quicken Bloom fixture", Element.HYDRO));
        assertEquals(1, countReactions(coexistKinds, ReactionResult.Kind.BLOOM),
                "Hydro on coexisting Dendro and Quicken should emit one Bloom");
        assertEquals(1, coexist.getDendroCores().size(),
                "Coexisting Dendro and Quicken should create only one core");
        assertClose(1.5, coexist.getEnemy().getAuraUnits(Element.DENDRO), EPS,
                "Coexisting Bloom should consume 0.5U underlying Dendro");
        assertClose(0.5, coexist.getQuickenState().units, EPS,
                "Coexisting Bloom should consume 0.5U Quicken simultaneously");

        CombatSimulator lunar = simulatorWith(testCharacter(Element.HYDRO).asLunar());
        List<ReactionResult.Kind> lunarKinds = captureReactionKinds(lunar);
        lunar.applyQuicken(1.0);
        lunar.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Quicken-only Lunar-Bloom fixture", Element.HYDRO));
        assertEquals(1, countReactions(lunarKinds, ReactionResult.Kind.LUNAR_BLOOM),
                "Lunar Hydro on Quicken alone should emit one Lunar-Bloom");
        assertEquals(1, lunar.getDendroCores().size(),
                "Quicken-only Lunar-Bloom should create one core");
        assertEquals(1, lunar.getVerdantDewCount(),
                "Quicken-only Lunar-Bloom should increment Verdant Dew once");
        assertEquals(1, lunar.getMoonridgeDewCount(),
                "Quicken-only Lunar-Bloom should increment Moonridge Dew once");

        CombatSimulator expired = simulatorWith(testCharacter(Element.HYDRO));
        List<ReactionResult.Kind> expiredKinds = captureReactionKinds(expired);
        expired.applyQuicken(0.8);
        expired.advanceTime(10.0);
        expired.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Expired Quicken Bloom fixture", Element.HYDRO));
        assertEquals(0, countReactions(expiredKinds, ReactionResult.Kind.BLOOM),
                "Hydro at exact Quicken expiry should not emit Bloom");
        assertEquals(0, expired.getDendroCores().size(),
                "Hydro at exact Quicken expiry should not create a core");
        assertClose(0.8, expired.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Hydro should apply normally after exact Quicken expiry");

        CombatSimulator refresh = simulatorWith(testCharacter(Element.ELECTRO));
        refresh.getEnemy().applyAura(Element.DENDRO, 1.0, refresh.getCurrentTime());
        refresh.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Initial real Quicken fixture", Element.ELECTRO));
        refresh.advanceTime(1.0);
        double originalEnd = refresh.getQuickenState().getEndTime();
        refresh.getEnemy().setAura(Element.DENDRO, 0.5);
        AttackAction weakElectro = reactionHit("Weaker real Quicken fixture", Element.ELECTRO);
        weakElectro.setICD(ICDType.None, ICDTag.None, 0.5);
        refresh.performActionWithoutTimeAdvance(CharacterId.SUCROSE, weakElectro);
        assertClose(originalEnd, refresh.getQuickenState().getEndTime(), EPS,
                "A weaker real Quicken retrigger should not change expiry");
        refresh.getEnemy().setAura(Element.DENDRO, 2.0);
        AttackAction strongElectro = reactionHit("Stronger real Quicken fixture", Element.ELECTRO);
        strongElectro.setICD(ICDType.None, ICDTag.None, 2.0);
        refresh.performActionWithoutTimeAdvance(CharacterId.SUCROSE, strongElectro);
        assertClose(2.0, refresh.getQuickenState().units, EPS,
                "A stronger real Quicken retrigger should replace gauge");
        assertClose(17.0, refresh.getQuickenState().getEndTime(), EPS,
                "A 2U real Quicken retrigger should refresh to sixteen seconds");
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
        // Hydro is an infinite fixture aura and only loses tick consumption.
        // Electro is the taxed 0.8U side introduced by the 1U source trigger.
        assertClose(0.6, sim.getEnemy().getAuraUnits(Element.HYDRO), 0.01,
                "Thundercloud tick should consume 0.4 GU Hydro");
        assertClose(0.4 - 2.0 * 0.8 / 9.5, sim.getEnemy().getAuraUnits(Element.ELECTRO), 0.01,
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

    private static void testAccuracyPhaseG_SourceAuraTaxAndDecayContract() {
        double[][] fixtures = {
            { 1.0, 0.8, 9.5 },
            { 1.5, 1.2, 10.75 },
            { 2.0, 1.6, 12.0 },
            { 4.0, 3.2, 17.0 }
        };
        for (double[] fixture : fixtures) {
            Enemy enemy = new Enemy(90);
            double sourceGauge = fixture[0];
            double taxedGauge = fixture[1];
            double duration = fixture[2];
            enemy.applyAura(Element.ELECTRO, sourceGauge, 0.0);
            assertClose(taxedGauge, enemy.getAuraUnits(Element.ELECTRO, 0.0), EPS,
                    "Fresh source should apply the exact taxed aura gauge");
            assertClose(taxedGauge / 2.0,
                    enemy.getAuraUnits(Element.ELECTRO, duration / 2.0), EPS,
                    "Source aura should decay linearly to half at its sourced midpoint");
            assertClose(0.0, enemy.getAuraUnits(Element.ELECTRO, duration), EPS,
                    "Source aura should reach zero at its sourced expiry");
        }
    }

    private static void testAccuracyPhaseG_SameElementAuraExtensionContract() {
        Enemy electro = new Enemy(90);
        electro.applyAura(Element.ELECTRO, 1.0, 0.0);
        electro.applyAura(Element.ELECTRO, 2.0, 1.0);
        assertClose(1.6, electro.getAuraUnits(Element.ELECTRO, 1.0), EPS,
                "A stronger Electro source should replace the decayed current gauge");
        assertClose(0.8, electro.getAuraUnits(Element.ELECTRO, 10.5), EPS,
                "Electro extension should retain the first 1U decay rate");
        electro.applyAura(Element.ELECTRO, 1.0, 10.5);
        assertClose(0.8, electro.getAuraUnits(Element.ELECTRO, 10.5), EPS,
                "An equal weaker source should not change amount or rate");
        assertClose(0.0, electro.getAuraUnits(Element.ELECTRO, 20.0), EPS,
                "Extended Electro should expire on the retained D(1) rate");

        Enemy pyro = new Enemy(90);
        pyro.applyAura(Element.PYRO, 1.0, 0.0);
        pyro.applyAura(Element.PYRO, 2.0, 1.0);
        assertClose(0.8, pyro.getAuraUnits(Element.PYRO, 7.0), EPS,
                "Amount-changing Pyro should adopt the stronger source decay rate");
        pyro.applyAura(Element.PYRO, 1.0, 7.0);
        assertClose(0.8, pyro.getAuraUnits(Element.PYRO, 7.0), EPS,
                "Non-changing Pyro application should retain its current rate");
        pyro.applyAura(Element.PYRO, 1.0, 7.1);
        assertClose(0.8, pyro.getAuraUnits(Element.PYRO, 7.1), EPS,
                "Amount-changing Pyro should restore the newly taxed amount");
        assertClose(0.4, pyro.getAuraUnits(Element.PYRO, 11.85), EPS,
                "Amount-changing weaker Pyro should adopt the new D(1) rate");
    }

    private static void testAccuracyPhaseG_AuraDecaySnapshotContract() {
        Enemy enemy = new Enemy(90);
        enemy.applyAura(Element.HYDRO, 1.0, 0.0);
        enemy.reduceAura(Element.HYDRO, 0.2, 2.0);
        Map<Element, double[]> snapshot = enemy.captureAuraState();
        double capturedUnits = enemy.getAuraUnits(Element.HYDRO, 2.0);
        enemy.applyAura(Element.HYDRO, 4.0, 3.0);
        enemy.restoreAuraState(snapshot);
        assertClose(capturedUnits, enemy.getAuraUnits(Element.HYDRO, 2.0), EPS,
                "Aura restore should recover consumed current units");
        assertClose(capturedUnits - 0.8 / 9.5,
                enemy.getAuraUnits(Element.HYDRO, 3.0), EPS,
                "Aura restore should preserve the selected source decay rate");
    }

    private static void testAccuracyPhaseG_AuraExpiryContract() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        sim.getEnemy().applyAura(Element.HYDRO, 1.0, sim.getCurrentTime());
        assertClose(9.5, sim.getEnemy().getAuraExpiryTime(Element.HYDRO, sim.getCurrentTime()), EPS,
                "Fresh 1U source should expose its exact 9.5-second Aura expiry");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(1.0);
        sim.getEnemy().reduceAura(Element.HYDRO, 0.4, sim.getCurrentTime());
        assertClose(4.75, sim.getEnemy().getAuraExpiryTime(Element.HYDRO, sim.getCurrentTime()), EPS,
                "Discrete consumption should rebase expiry at the original decay rate");

        sim.restoreSnapshot(snapshot);
        assertClose(9.5, sim.getEnemy().getAuraExpiryTime(Element.HYDRO, sim.getCurrentTime()), EPS,
                "Snapshot restore should recover the original Aura expiry");
        assertTrue(Double.isInfinite(
                        sim.getEnemy().getAuraExpiryTime(Element.HYDRO, 9.5)),
                "An Aura queried at exact natural expiry should have no future deadline");

        sim.getEnemy().setAura(Element.ELECTRO, 1.0);
        assertTrue(Double.isInfinite(
                        sim.getEnemy().getAuraExpiryTime(Element.ELECTRO, sim.getCurrentTime())),
                "A non-decaying compatibility Aura should expose infinite expiry");
        sim.getEnemy().setAura(Element.ELECTRO, 0.0);
        assertTrue(Double.isInfinite(
                        sim.getEnemy().getAuraExpiryTime(Element.ELECTRO, sim.getCurrentTime())),
                "An absent Aura should expose no finite future expiry");
    }

    private static void testAccuracyPhaseG_FreezeStateContract() {
        Enemy enemy = new Enemy(90);
        Enemy.FreezeAuraState initial = enemy.applyFreezeAura(1.6, 0.0);
        double expectedDuration = 2.0 * Math.sqrt(12.0) - 4.0;
        assertClose(1.6, initial.units, EPS,
                "Equal 1U sources should create 1.6U Frozen gauge");
        assertClose(0.4, initial.decayRate, EPS,
                "A rested Frozen Aura should start at 0.4U/s");
        assertClose(expectedDuration, initial.getEndTime(), EPS,
                "1.6U Frozen gauge should derive the sourced exact duration");

        double midpoint = expectedDuration / 2.0;
        double expectedMidpoint = 1.6 - 0.4 * midpoint - 0.05 * midpoint * midpoint;
        assertClose(expectedMidpoint, initial.remainingUnitsAt(midpoint), EPS,
                "Frozen gauge should follow accelerating nonlinear decay");
        assertClose(0.4 + 0.1 * midpoint, initial.decayRateAt(midpoint), EPS,
                "Frozen decay rate should accelerate by 0.1U/s^2 while active");
        assertClose(0.0, initial.remainingUnitsAt(expectedDuration), EPS,
                "Frozen gauge should reach zero at exact expiry");
        assertTrue(!enemy.isFrozen(expectedDuration),
                "Frozen state should be inactive at exact expiry");

        Enemy extensionEnemy = new Enemy(90);
        Enemy.FreezeAuraState extensionInitial = extensionEnemy.applyFreezeAura(1.6, 0.0);
        double remainingBeforeExtension = extensionInitial.remainingUnitsAt(1.0);
        Enemy.FreezeAuraState extension = extensionEnemy.applyFreezeAura(0.8, 1.0);
        assertClose(remainingBeforeExtension + 0.8, extension.units, EPS,
                "Refreeze should add gauge to the current remaining amount");
        assertClose(0.5, extension.decayRate, EPS,
                "Refreeze should retain the instantaneous active decay rate");
        assertTrue(extension.getEndTime() > extensionInitial.getEndTime(),
                "Refreeze should extend the exact Frozen expiry");

        Enemy recoveryEnemy = new Enemy(90);
        recoveryEnemy.applyFreezeAura(1.6, 0.0);
        recoveryEnemy.clearFreezeAura(1.0);
        Enemy.FreezeAuraState cleared = recoveryEnemy.captureFreezeAuraState();
        assertClose(0.5, cleared.decayRate, EPS,
                "Clearing Freeze should retain its instantaneous rate");
        assertClose(0.45, cleared.decayRateAt(1.25), EPS,
                "Inactive Freeze rate should recover by 0.2U/s^2");
        assertClose(0.4, cleared.decayRateAt(1.5), EPS,
                "Inactive Freeze rate should recover only to the 0.4U/s floor");
        Enemy.FreezeAuraState reapplied = recoveryEnemy.applyFreezeAura(0.8, 1.25);
        assertClose(0.45, reapplied.decayRate, EPS,
                "Freeze reapplied during recovery should use the recovered rate");

        Enemy consumptionEnemy = new Enemy(90);
        consumptionEnemy.applyFreezeAura(1.6, 0.0);
        Enemy.FreezeAuraState partial = consumptionEnemy.reduceFreezeAura(0.4, 0.5);
        assertClose(0.9875, partial.units, EPS,
                "Partial Freeze consumption should rebase the decayed remainder");
        assertClose(0.45, partial.decayRate, EPS,
                "Partial Freeze consumption should preserve current decay rate");
        consumptionEnemy.reduceFreezeAura(1.0, 0.5);
        assertTrue(!consumptionEnemy.isFrozen(0.5),
                "Exact or excess Freeze consumption should clear active gauge");
        Enemy.FreezeAuraState beforeInvalid = consumptionEnemy.captureFreezeAuraState();
        consumptionEnemy.applyFreezeAura(Double.NaN, 1.0);
        assertClose(beforeInvalid.units, consumptionEnemy.captureFreezeAuraState().units, EPS,
                "Invalid Freeze application should not mutate gauge");

        CombatSimulator sim = simulatorWith(testCharacter(Element.CRYO));
        sim.getEnemy().applyFreezeAura(1.6, sim.getCurrentTime());
        sim.advanceTime(0.75);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        double capturedRemaining = sim.getEnemy().getFreezeAuraUnits(sim.getCurrentTime());
        double capturedEnd = sim.getEnemy().captureFreezeAuraState().getEndTime();
        sim.getEnemy().clearFreezeAura(sim.getCurrentTime());
        sim.advanceTime(1.0);
        sim.restoreSnapshot(snapshot);
        Enemy.FreezeAuraState restored = sim.getEnemy().captureFreezeAuraState();
        assertClose(1.6, restored.units, EPS,
                "Snapshot restore should recover stored Frozen gauge");
        assertClose(0.4, restored.decayRate, EPS,
                "Snapshot restore should recover Frozen decay rate");
        assertClose(0.0, restored.lastUpdateTime, EPS,
                "Snapshot restore should recover Frozen update time");
        assertClose(capturedRemaining,
                restored.remainingUnitsAt(sim.getCurrentTime()), EPS,
                "Snapshot restore should recover current Frozen remainder");
        assertClose(capturedEnd, restored.getEndTime(), EPS,
                "Snapshot restore should recover exact Frozen expiry");
    }

    private static void testAccuracyPhaseG_BurningStateSnapshotContract() {
        Enemy enemy = new Enemy(90);
        enemy.applyAura(Element.DENDRO, 1.0, 0.0);
        assertClose(0.8 / 9.5, enemy.getAuraDecayRate(Element.DENDRO, 0.0), EPS,
                "A finite Dendro source should expose its natural decay rate");
        assertClose(0.0, enemy.getAuraDecayRate(Element.DENDRO, 9.5), EPS,
                "An Aura at exact expiry should expose no natural decay rate");
        enemy.setAura(Element.DENDRO, 1.0);
        assertClose(0.0, enemy.getAuraDecayRate(Element.DENDRO, 0.0), EPS,
                "A non-decaying compatibility Aura should expose a zero rate");
        enemy.setAura(Element.DENDRO, 0.0);
        assertClose(0.0, enemy.getAuraDecayRate(Element.DENDRO, 0.0), EPS,
                "An absent Aura should expose a zero decay rate");

        CombatSimulator sim = simulatorWith(testCharacter(Element.PYRO));
        ReactionState.BurningState initial = sim.startBurning(
                CharacterId.SUCROSE, 1000.0, 0.8, 0.4);
        assertTrue(initial != null, "A valid Burning payload should start typed state");
        assertClose(2.0, initial.getEndTime(), EPS,
                "0.8U Burning fuel at 0.4U/s should last exactly two seconds");
        assertEquals(CharacterId.SUCROSE, initial.ownerId,
                "Typed Burning state should retain the damage owner");
        int generation = initial.generation;

        sim.advanceTime(0.5);
        ReactionState.BurningState ownerRefresh = sim.refreshBurningDamage(
                CharacterId.XIANGLING, 1200.0);
        assertTrue(ownerRefresh != null, "An active Burning owner refresh should retain state");
        assertClose(0.6, ownerRefresh.fuelUnits, EPS,
                "Owner refresh should preserve continuously decayed fuel");
        assertClose(2.0, ownerRefresh.getEndTime(), EPS,
                "Owner refresh should not extend Burning fuel duration");
        assertEquals(CharacterId.XIANGLING, ownerRefresh.ownerId,
                "Owner refresh should replace the latest damage owner");
        assertClose(1200.0, ownerRefresh.preResistanceDamage, EPS,
                "Owner refresh should replace pre-resistance damage");
        assertEquals(generation, ownerRefresh.generation,
                "Owner refresh should retain the active timer generation");

        ReactionState.BurningState fuelRefresh = sim.replaceBurningFuel(0.4, 0.4);
        assertTrue(fuelRefresh != null, "A valid Dendro refresh should replace Burning fuel");
        assertClose(1.5, fuelRefresh.getEndTime(), EPS,
                "Replacement fuel should derive a new exact depletion time");
        assertEquals(generation, fuelRefresh.generation,
                "Fuel replacement should retain the active timer generation");

        sim.setBurningTimerRunning(true);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.clearBurning();
        assertTrue(sim.getBurningState() == null, "Clearing Burning should remove typed state");
        sim.restoreSnapshot(snapshot);
        ReactionState.BurningState restored = sim.getBurningState();
        assertTrue(restored != null, "Snapshot restore should recover typed Burning state");
        assertEquals(CharacterId.XIANGLING, restored.ownerId,
                "Snapshot restore should recover Burning ownership");
        assertClose(1200.0, restored.preResistanceDamage, EPS,
                "Snapshot restore should recover Burning damage");
        assertClose(0.4, restored.fuelUnits, EPS,
                "Snapshot restore should recover Burning fuel");
        assertClose(0.4, restored.fuelDecayRate, EPS,
                "Snapshot restore should recover Burning decay rate");
        assertClose(0.5, restored.lastUpdateTime, EPS,
                "Snapshot restore should recover Burning update time");
        assertEquals(generation, restored.generation,
                "Snapshot restore should recover Burning generation");
        assertTrue(sim.isBurningTimerRunning(),
                "Snapshot restore should recover the Burning timer flag");

        ReactionState.BurningState invalid = sim.startBurning(
                null, Double.NaN, -1.0, 0.0);
        assertTrue(invalid == null, "An invalid Burning payload should be rejected");
        assertTrue(sim.getBurningState() == null,
                "Rejecting an invalid Burning payload should clear stale state");
        assertTrue(!sim.isBurningTimerRunning(),
                "Rejecting an invalid Burning payload should clear the timer flag");
    }

    private static void testAccuracyPhaseG_QuickenStateSnapshotContract() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        ReactionState.QuickenState initial = sim.applyQuicken(0.8);
        assertTrue(initial != null, "A valid Quicken gauge should create typed state");
        assertClose(10.0, initial.getEndTime(), EPS,
                "0.8U Quicken should derive an exact ten-second duration");
        assertClose(0.08, initial.decayRate, EPS,
                "0.8U Quicken should derive its gauge-over-duration decay rate");

        sim.advanceTime(2.5);
        assertClose(0.6, sim.getQuickenState().remainingUnitsAt(sim.getCurrentTime()), EPS,
                "Typed Quicken should decay continuously between applications");
        ReactionState.QuickenState weaker = sim.applyQuicken(0.5);
        assertClose(10.0, weaker.getEndTime(), EPS,
                "A weaker Quicken application should leave the existing end unchanged");
        assertClose(0.8, weaker.units, EPS,
                "A weaker Quicken application should leave stored gauge unchanged");

        ReactionState.QuickenState equal = sim.applyQuicken(0.6);
        assertClose(11.5, equal.getEndTime(), EPS,
                "An equal Quicken application should refresh from the current time");
        ReactionState.QuickenState stronger = sim.applyQuicken(0.8);
        assertClose(12.5, stronger.getEndTime(), EPS,
                "A stronger Quicken application should replace gauge and duration");

        ReactionState.QuickenState consumed = sim.consumeQuicken(0.3);
        assertTrue(consumed != null, "Partial Quicken consumption should retain state");
        assertClose(0.5, consumed.units, EPS,
                "Partial Quicken consumption should rebase remaining gauge");
        assertClose(8.75, consumed.getEndTime(), EPS,
                "Partial consumption should preserve the selected decay rate");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertTrue(sim.applyQuicken(Double.NaN) == null,
                "An invalid Quicken application should be rejected");
        assertClose(0.5, sim.getQuickenState().units, EPS,
                "An invalid Quicken application should not mutate active state");
        sim.consumeQuicken(1.0);
        assertTrue(sim.getQuickenState() == null,
                "Over-consumption should clear typed Quicken state");
        assertTrue(!sim.isQuickenActive(),
                "Cleared typed Quicken should be inactive");

        sim.restoreSnapshot(snapshot);
        ReactionState.QuickenState restored = sim.getQuickenState();
        assertTrue(restored != null, "Snapshot restore should recover typed Quicken state");
        assertClose(0.5, restored.units, EPS,
                "Snapshot restore should recover Quicken units");
        assertClose(0.08, restored.decayRate, EPS,
                "Snapshot restore should recover Quicken decay rate");
        assertClose(2.5, restored.lastUpdateTime, EPS,
                "Snapshot restore should recover Quicken update time");
        assertClose(8.75, restored.getEndTime(), EPS,
                "Snapshot restore should recover Quicken expiry");

        CombatSimulator compatibility = simulatorWith(testCharacter(Element.DENDRO));
        compatibility.setQuickenEndTime(5.0);
        assertTrue(compatibility.isQuickenActive(),
                "Explicit Quicken end should retain compatibility behavior");
        compatibility.advanceTime(5.0);
        assertTrue(!compatibility.isQuickenActive(),
                "Explicit Quicken end should remain exclusive at exact expiry");
    }

    private static void testAccuracyPhaseG_InvalidSourceAuraContract() {
        Enemy enemy = new Enemy(90);
        enemy.applyAura(Element.PHYSICAL, 1.0, 0.0);
        enemy.applyAura(Element.ANEMO, 1.0, 0.0);
        enemy.applyAura(Element.GEO, 1.0, 0.0);
        enemy.applyAura(Element.PYRO, 0.0, 0.0);
        enemy.applyAura(Element.HYDRO, -1.0, 0.0);
        enemy.applyAura(Element.CRYO, Double.NaN, 0.0);
        enemy.applyAura(Element.ELECTRO, Double.POSITIVE_INFINITY, 0.0);
        enemy.applyAura(Element.DENDRO, 1.0, Double.NaN);
        assertTrue(enemy.getActiveAuras(0.0).isEmpty(),
                "Invalid or non-persistent source applications should create no aura");
    }

    private static void testAccuracyPhaseG_RuntimeAuraApplicationContract() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        AttackAction oneUnit = reactionHit("Runtime 1U Electro aura fixture", Element.ELECTRO);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, oneUnit);
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "A real 1U action should establish 0.8U after Aura Tax");

        sim.advanceTime(1.0);
        AttackAction twoUnit = reactionHit("Runtime 2U Electro aura fixture", Element.ELECTRO);
        twoUnit.setICD(ICDType.None, ICDTag.None, 2.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, twoUnit);
        assertClose(1.6, sim.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "A real stronger same-element action should extend to its taxed amount");
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE, oneUnit);
        assertClose(1.6, sim.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "A weaker real action should not shorten the stronger current aura");

        CombatSimulator invalid = simulatorWith(testCharacter(Element.ANEMO));
        invalid.setLoggingEnabled(true);
        String invalidLog = captureStandardOutput(() -> {
            invalid.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Runtime Anemo aura fixture", Element.ANEMO));
            invalid.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Runtime Geo aura fixture", Element.GEO));
            invalid.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Runtime Physical aura fixture", Element.PHYSICAL));
            invalid.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, damageHit("Runtime zero-gauge fixture", Element.PYRO, 0.0));
        });
        assertTrue(invalid.getEnemy().getActiveAuras(invalid.getCurrentTime()).isEmpty(),
                "Non-persistent and zero-gauge runtime actions should establish no aura");
        assertTrue(!invalidLog.contains("[Aura] Applied"),
                "Rejected runtime source elements should not emit false aura logs");
    }

    private static void testAccuracyPhaseG_AnemoGeoAuraConsumptionContract() {
        CombatSimulator swirl = simulatorWith(testCharacter(Element.ANEMO));
        swirl.getEnemy().applyAura(Element.PYRO, 1.0, swirl.getCurrentTime());
        swirl.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("1U Swirl consumption fixture", Element.ANEMO));
        assertClose(0.3, swirl.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "1U Swirl should leave 0.3U from a fresh taxed 1U source aura");

        CombatSimulator strongSwirl = simulatorWith(testCharacter(Element.ANEMO));
        strongSwirl.getEnemy().applyAura(Element.HYDRO, 2.0, strongSwirl.getCurrentTime());
        AttackAction twoUnitSwirl = reactionHit("2U Swirl consumption fixture", Element.ANEMO);
        twoUnitSwirl.setICD(ICDType.None, ICDTag.None, 2.0);
        strongSwirl.performActionWithoutTimeAdvance(CharacterId.SUCROSE, twoUnitSwirl);
        assertClose(0.6, strongSwirl.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "2U Swirl should consume 1U from a fresh taxed 2U source aura");

        CombatSimulator crystallize = simulatorWith(testCharacter(Element.GEO));
        crystallize.getEnemy().applyAura(Element.ELECTRO, 1.0, crystallize.getCurrentTime());
        crystallize.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("1U Crystallize consumption fixture", Element.GEO));
        assertClose(0.3, crystallize.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "1U Crystallize should leave 0.3U from a fresh taxed 1U source aura");

        CombatSimulator lunar = simulatorWith(testCharacter(Element.GEO).asLunar());
        lunar.getEnemy().applyAura(Element.HYDRO, 1.0, lunar.getCurrentTime());
        lunar.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Lunar-Crystallize consumption fixture", Element.GEO));
        assertClose(0.3, lunar.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Lunar-Crystallize should use the same 0.5 Geo modifier");
        assertEquals(3, lunar.getMoondriftCount(),
                "Scaled Lunar-Crystallize consumption should preserve Moondrift creation");

        for (double boundary : new double[] { 0.5, 0.4 }) {
            CombatSimulator depleted = simulatorWith(testCharacter(Element.ANEMO));
            depleted.getEnemy().setAura(Element.CRYO, boundary);
            depleted.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Swirl depletion fixture", Element.ANEMO));
            assertClose(0.0, depleted.getEnemy().getAuraUnits(Element.CRYO), EPS,
                    "Swirl should fully remove aura at or below scaled consumption");
        }

        CombatSimulator overload = simulatorWith(testCharacter(Element.PYRO));
        overload.getEnemy().setAura(Element.ELECTRO, 1.0);
        overload.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Overload consumption fixture", Element.PYRO));
        assertClose(0.0, overload.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "Non-Anemo/Geo transformative reactions should retain full-gauge consumption");
    }

    private static void testAccuracyPhaseG_TransformativeResidualAuraContract() {
        CombatSimulator pyroOverload = simulatorWith(testCharacter(Element.PYRO));
        List<ReactionResult.Kind> pyroKinds = captureReactionKinds(pyroOverload);
        pyroOverload.getEnemy().applyAura(Element.ELECTRO, 2.0, pyroOverload.getCurrentTime());
        pyroOverload.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Pyro Overload residual fixture", Element.PYRO));
        assertClose(0.6, pyroOverload.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "1U Pyro Overload should leave 0.6U from taxed 2U Electro");
        assertEquals(1, countReactions(pyroKinds, ReactionResult.Kind.OVERLOAD),
                "Residual Overload should emit one typed reaction");

        CombatSimulator electroOverload = simulatorWith(testCharacter(Element.ELECTRO));
        electroOverload.getEnemy().applyAura(Element.PYRO, 2.0, electroOverload.getCurrentTime());
        electroOverload.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Electro Overload residual fixture", Element.ELECTRO));
        assertClose(0.6, electroOverload.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "1U Electro Overload should leave 0.6U from taxed 2U Pyro");

        CombatSimulator cryoSuperconduct = simulatorWith(testCharacter(Element.CRYO));
        cryoSuperconduct.getEnemy().applyAura(
                Element.ELECTRO, 2.0, cryoSuperconduct.getCurrentTime());
        cryoSuperconduct.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Cryo Superconduct residual fixture", Element.CRYO));
        assertClose(0.6, cryoSuperconduct.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "1U Cryo Superconduct should leave 0.6U from taxed 2U Electro");

        CombatSimulator electroSuperconduct = simulatorWith(testCharacter(Element.ELECTRO));
        List<ReactionResult.Kind> superconductKinds = captureReactionKinds(electroSuperconduct);
        electroSuperconduct.getEnemy().applyAura(
                Element.CRYO, 2.0, electroSuperconduct.getCurrentTime());
        electroSuperconduct.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Electro Superconduct residual fixture", Element.ELECTRO));
        assertClose(0.6, electroSuperconduct.getEnemy().getAuraUnits(Element.CRYO), EPS,
                "1U Electro Superconduct should leave 0.6U from taxed 2U Cryo");
        assertEquals(1, countReactions(superconductKinds, ReactionResult.Kind.SUPERCONDUCT),
                "Residual Superconduct should emit one typed reaction");

        pyroOverload.advanceTime(4.49);
        assertTrue(pyroOverload.getEnemy().getAuraUnits(
                Element.ELECTRO, pyroOverload.getCurrentTime()) > 0.0,
                "A 0.6U D(2) residual should remain just before 4.5 seconds");
        pyroOverload.advanceTime(0.02);
        assertClose(0.0, pyroOverload.getEnemy().getAuraUnits(
                Element.ELECTRO, pyroOverload.getCurrentTime()), EPS,
                "A 0.6U D(2) residual should expire after 4.5 seconds");

        for (double sourceGauge : new double[] { 1.0, 0.5 }) {
            CombatSimulator depleted = simulatorWith(testCharacter(Element.PYRO));
            depleted.getEnemy().applyAura(
                    Element.ELECTRO, sourceGauge, depleted.getCurrentTime());
            depleted.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Overload depletion fixture", Element.PYRO));
            assertClose(0.0, depleted.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                    "Overload should fully remove aura at or below 1U consumption");
        }
    }

    private static void testAccuracyPhaseG_OverloadDamageSequenceContract() {
        CombatSimulator sim = simulatorWith(testCharacter(
                Element.PYRO, CharacterId.SUCROSE));
        sim.addCharacter(testCharacter(Element.PYRO, CharacterId.XIANGLING));
        sim.getEnemy().setAura(Element.ELECTRO, 8.0);
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        double overloadDamage = expectedTransformative(2.75, Element.PYRO, 0.0);

        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial Overload damage-sequence fixture", Element.PYRO));
        assertClose(overloadDamage, sim.getTotalDamage(), 0.5,
                "The first Overload should deal reaction damage");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertClose(0.1, snapshot.overloadTargetDamageCooldownEndTime, EPS,
                "First Overload should start the target-wide damage GCD");
        assertClose(0.5, snapshot.overloadOwnerDamageCooldownEndTimes.get(
                CharacterId.SUCROSE), EPS,
                "First Overload should start the owner's damage cooldown");

        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Pre-target-boundary Overload fixture", Element.PYRO));
        assertClose(overloadDamage, sim.getTotalDamage(), 0.5,
                "A different owner should be damage-blocked before 0.1 seconds");

        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Exact-target-boundary Overload fixture", Element.PYRO));
        assertClose(overloadDamage * 2.0, sim.getTotalDamage(), 0.5,
                "A different owner should deal damage at exactly 0.1 seconds");

        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Post-target owner-blocked Overload fixture", Element.PYRO));
        sim.advanceTime(0.39);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Pre-owner-boundary Overload fixture", Element.PYRO));
        assertClose(overloadDamage * 2.0, sim.getTotalDamage(), 0.5,
                "The original owner should remain damage-blocked before 0.5 seconds");

        sim.advanceTime(0.01);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Exact-owner-boundary Overload fixture", Element.PYRO));
        assertClose(overloadDamage * 3.0, sim.getTotalDamage(), 0.5,
                "The original owner should deal damage at exactly 0.5 seconds");
        assertEquals(6, countReactions(kinds, ReactionResult.Kind.OVERLOAD),
                "Damage-blocked hits should still notify every Overload reaction");
        assertClose(2.0, sim.getEnemy().getAuraUnits(
                Element.ELECTRO, sim.getCurrentTime()), EPS,
                "Every accepted and blocked Overload should consume exactly 1U Aura");

        sim.restoreSnapshot(snapshot);
        assertClose(0.0, sim.getCurrentTime(), EPS,
                "Snapshot restore should rewind the Overload cooldown clock");
        assertClose(overloadDamage, sim.getTotalDamage(), 0.5,
                "Snapshot restore should rewind damage with cooldown state");
        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Restored target-boundary Overload fixture", Element.PYRO));
        sim.advanceTime(0.4);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Restored owner-boundary Overload fixture", Element.PYRO));
        assertClose(overloadDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Restored target and owner cooldowns should preserve exact boundaries");

        CombatSimulator superconduct = simulatorWith(testCharacter(Element.ELECTRO));
        superconduct.getEnemy().setAura(Element.CRYO, 3.0);
        superconduct.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("First non-Overload damage-sequence fixture", Element.ELECTRO));
        superconduct.advanceTime(0.1);
        superconduct.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Second non-Overload damage-sequence fixture", Element.ELECTRO));
        assertClose(expectedTransformative(1.5, Element.CRYO, 0.0) * 2.0,
                superconduct.getTotalDamage(), 0.5,
                "Overload limits should not suppress boundary Superconduct damage");
    }

    private static void testAccuracyPhaseG_SwirlDamageSequenceContract() {
        CombatSimulator sim = simulatorWith(testCharacter(
                Element.ANEMO, CharacterId.SUCROSE));
        sim.addCharacter(testCharacter(Element.ANEMO, CharacterId.XIANGLING));
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        double swirlDamage = expectedTransformative(
                0.6, Element.PYRO, 0.0);

        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial Pyro Swirl sequence fixture", Element.ANEMO));
        assertClose(swirlDamage, sim.getTotalDamage(), 0.5,
                "The first Pyro Swirl should deal reaction damage");

        sim.advanceTime(0.05);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Pre-target Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage, sim.getTotalDamage(), 0.5,
                "Another owner should be target-blocked before 0.1 seconds");
        assertClose(1.5, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Target-blocked Swirl should still consume half source gauge");

        sim.getEnemy().setAura(Element.HYDRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Independent Hydro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 2.0, sim.getTotalDamage(), 0.5,
                "Hydro Swirl should have independent per-element state");
        sim.getEnemy().setAura(Element.HYDRO, 0.0);

        sim.advanceTime(0.05);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Second Pyro Swirl sequence fixture", Element.ANEMO));
        assertClose(swirlDamage * 3.0, sim.getTotalDamage(), 0.5,
                "The owner should deal its second Pyro Swirl at the target boundary");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertClose(0.2, snapshot.swirlTargetDamageCooldownEndTimes.get(
                Element.PYRO), EPS,
                "The second Pyro Swirl should advance its target GCD");
        ReactionState.FixedDamageSequenceState ownerState =
                snapshot.swirlOwnerDamageSequenceStates.get(
                        Element.PYRO).get(CharacterId.SUCROSE);
        assertClose(0.5, ownerState.windowEndTime, EPS,
                "The Pyro Swirl owner should retain the first fixed boundary");
        assertEquals(2, ownerState.attemptCount,
                "The Pyro Swirl owner should record two accepted attempts");
        assertTrue(!snapshot.swirlOwnerDamageSequenceStates.get(
                        Element.PYRO).containsKey(CharacterId.XIANGLING),
                "A target-blocked Pyro Swirl should not start owner state");
        assertTrue(snapshot.swirlOwnerDamageSequenceStates.get(
                        Element.HYDRO).containsKey(CharacterId.XIANGLING),
                "Hydro Swirl should preserve independent owner state");

        sim.advanceTime(0.1);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Third Pyro Swirl sequence fixture", Element.ANEMO));
        assertClose(swirlDamage * 3.0, sim.getTotalDamage(), 0.5,
                "The owner's third Pyro Swirl should deal no damage");
        assertClose(1.5, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Owner-blocked Swirl should still consume half source gauge");

        sim.advanceTime(0.05);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Post-owner target-blocked Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 3.0, sim.getTotalDamage(), 0.5,
                "An owner-blocked attempt should still start the target GCD");

        sim.advanceTime(0.05);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Independent owner Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 4.0, sim.getTotalDamage(), 0.5,
                "Another owner should have an independent Pyro Swirl sequence");

        sim.advanceTime(0.2);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Exact owner-reset Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 5.0, sim.getTotalDamage(), 0.5,
                "The original owner should deal damage at exactly 0.5 seconds");
        assertEquals(9, countReactions(kinds, ReactionResult.Kind.SWIRL),
                "Every damage-blocked Swirl should retain reaction notification");

        sim.restoreSnapshot(snapshot);
        assertClose(swirlDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should rewind Swirl damage and state");
        sim.advanceTime(0.1);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Restored third Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should recover the Pyro owner damage limit");
        sim.advanceTime(0.3);
        sim.getEnemy().setAura(Element.PYRO, 2.0);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Restored reset Pyro Swirl fixture", Element.ANEMO));
        assertClose(swirlDamage * 4.0, sim.getTotalDamage(), 0.5,
                "Restored Pyro owner state should reset at its exact boundary");
    }

    private static void testAccuracyPhaseG_StandardCrystallizeCooldownContract() {
        CombatSimulator dualAura = simulatorWith(testCharacter(Element.GEO));
        List<ReactionResult> dualAuraResults = new ArrayList<>();
        dualAura.addReactionListener((result, source, time, simulator) ->
                dualAuraResults.add(result));
        dualAura.getEnemy().setAura(Element.ELECTRO, 2.0);
        dualAura.getEnemy().setAura(Element.HYDRO, 2.0);
        dualAura.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Dual-Aura standard Crystallize fixture", Element.GEO));
        assertEquals(1, dualAuraResults.size(),
                "One Geo hit should notify at most one standard Crystallize");
        assertEquals(Element.ELECTRO, dualAuraResults.get(0).getRelatedElement(),
                "Standard Crystallize should retain B-062 Aura priority");
        assertClose(1.5, dualAura.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "Accepted standard Crystallize should consume the priority Aura");
        assertClose(2.0, dualAura.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Same-hit blocked Crystallize should not consume a second Aura");

        CombatSimulator timed = simulatorWith(testCharacter(
                Element.GEO, CharacterId.SUCROSE));
        timed.addCharacter(testCharacter(Element.GEO, CharacterId.XIANGLING));
        List<ReactionResult.Kind> timedKinds = captureReactionKinds(timed);
        timed.getEnemy().setAura(Element.ELECTRO, 0.5);
        timed.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial standard Crystallize fixture", Element.GEO));
        SimulatorSnapshot snapshot = timed.saveSnapshot();
        assertClose(1.0, snapshot.standardCrystallizeCooldownEndTime, EPS,
                "First standard Crystallize should start a one-second cooldown");

        timed.getEnemy().setAura(Element.HYDRO, 2.0);
        timed.advanceTime(0.999);
        timed.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Shared pre-boundary Crystallize fixture", Element.GEO));
        assertEquals(1, countReactions(timedKinds, ReactionResult.Kind.CRYSTALLIZE),
                "Cooldown should be shared across owners and Aura elements");
        assertClose(2.0, timed.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Pre-boundary standard Crystallize should not consume Aura");

        timed.advanceTime(0.001);
        timed.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Exact-boundary Crystallize fixture", Element.GEO));
        assertEquals(2, countReactions(timedKinds, ReactionResult.Kind.CRYSTALLIZE),
                "Standard Crystallize should trigger at exactly one second");
        assertClose(1.5, timed.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Exact-boundary standard Crystallize should consume Aura");

        timed.restoreSnapshot(snapshot);
        timed.getEnemy().setAura(Element.CRYO, 2.0);
        int restoredCount = countReactions(
                timedKinds, ReactionResult.Kind.CRYSTALLIZE);
        timed.advanceTime(0.999);
        timed.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Restored pre-boundary Crystallize fixture", Element.GEO));
        assertEquals(restoredCount, countReactions(
                timedKinds, ReactionResult.Kind.CRYSTALLIZE),
                "Snapshot restore should recover the active standard cooldown");
        timed.advanceTime(0.001);
        timed.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Restored exact-boundary Crystallize fixture", Element.GEO));
        assertEquals(restoredCount + 1, countReactions(
                timedKinds, ReactionResult.Kind.CRYSTALLIZE),
                "A blocked retry should not refresh the restored boundary");

        CombatSimulator lunar = simulatorWith(testCharacter(Element.GEO).asLunar());
        List<ReactionResult.Kind> lunarKinds = captureReactionKinds(lunar);
        lunar.getEnemy().setAura(Element.HYDRO, 3.0);
        for (int i = 0; i < 3; i++) {
            lunar.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE,
                    reactionHit("Rapid Lunar-Crystallize fixture " + i, Element.GEO));
        }
        assertEquals(4, countReactions(
                lunarKinds, ReactionResult.Kind.LUNAR_CRYSTALLIZE),
                "Three rapid Lunar-Crystallize triggers should retain the Harmony notification");
        assertEquals(3, lunar.getLunarCrystallizeTriggerCount(),
                "Lunar-Crystallize triggers should bypass the standard global cooldown");
    }

    private static void testAccuracyPhaseG_SuperconductDamageSequenceContract() {
        CombatSimulator sim = simulatorWith(testCharacter(
                Element.ELECTRO, CharacterId.SUCROSE));
        sim.addCharacter(testCharacter(Element.ELECTRO, CharacterId.XIANGLING));
        sim.getEnemy().setAura(Element.CRYO, 8.0);
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        double superconductDamage = expectedTransformative(
                1.5, Element.CRYO, 0.0);

        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial Superconduct sequence fixture", Element.ELECTRO));
        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Pre-target-boundary Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage, sim.getTotalDamage(), 0.5,
                "A different owner should be damage-blocked before 0.1 seconds");

        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Second owner Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 2.0, sim.getTotalDamage(), 0.5,
                "The owner should deal its second hit at the target boundary");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertClose(0.2, snapshot.superconductTargetDamageCooldownEndTime, EPS,
                "Second Superconduct should advance the target damage GCD");
        ReactionState.FixedDamageSequenceState ownerState =
                snapshot.superconductOwnerDamageSequenceStates.get(
                        CharacterId.SUCROSE);
        assertClose(0.5, ownerState.windowEndTime, EPS,
                "The owner sequence should retain its first-hit fixed boundary");
        assertEquals(2, ownerState.attemptCount,
                "The owner sequence should record two target-accepted attempts");
        assertTrue(!snapshot.superconductOwnerDamageSequenceStates.containsKey(
                CharacterId.XIANGLING),
                "A target-blocked attempt should not start an owner sequence");

        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Third owner Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 2.0, sim.getTotalDamage(), 0.5,
                "The owner's third target-accepted hit should deal no damage");
        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Post-owner-block target GCD fixture", Element.ELECTRO));
        assertClose(superconductDamage * 2.0, sim.getTotalDamage(), 0.5,
                "An owner-blocked attempt should still start the target GCD");
        sim.advanceTime(0.05);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Independent owner Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Another owner should have an independent damage sequence");
        sim.advanceTime(0.2);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Exact owner-reset Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 4.0, sim.getTotalDamage(), 0.5,
                "The original owner should deal damage at exactly 0.5 seconds");
        assertEquals(7, countReactions(kinds, ReactionResult.Kind.SUPERCONDUCT),
                "Damage-blocked attempts should still notify every Superconduct");
        assertClose(1.0, sim.getEnemy().getAuraUnits(Element.CRYO), EPS,
                "Every accepted and blocked Superconduct should consume Aura");

        sim.restoreSnapshot(snapshot);
        assertClose(superconductDamage * 2.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should rewind Superconduct damage");
        sim.advanceTime(0.1);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Restored third Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 2.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should recover the owner damage limit");
        sim.advanceTime(0.3);
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Restored owner-reset Superconduct fixture", Element.ELECTRO));
        assertClose(superconductDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Restored owner sequence should reset at its exact boundary");

        CombatSimulator baseline = simulatorWith(testCharacter(Element.PHYSICAL));
        baseline.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                damageHit("Late physical baseline fixture", Element.PHYSICAL, 1.0));
        double baselinePhysicalDamage = baseline.getTotalDamage();

        CombatSimulator shred = simulatorWith(testCharacter(Element.ELECTRO));
        List<ReactionResult.Kind> shredKinds = captureReactionKinds(shred);
        shred.getEnemy().setAura(Element.CRYO, 4.0);
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                shred.advanceTime(0.1);
            }
            shred.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE,
                    reactionHit("Superconduct shred refresh fixture " + i, Element.ELECTRO));
        }
        assertClose(superconductDamage * 2.0, shred.getTotalDamage(), 0.5,
                "The third owner hit should refresh shred without dealing damage");
        assertEquals(3, countReactions(
                shredKinds, ReactionResult.Kind.SUPERCONDUCT),
                "Owner-blocked Superconduct should retain reaction notification");
        shred.advanceTime(11.95);
        double damageBeforePhysical = shred.getTotalDamage();
        shred.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                damageHit("Physical after blocked Superconduct", Element.PHYSICAL, 1.0));
        double refreshedPhysicalDamage = shred.getTotalDamage() - damageBeforePhysical;
        assertTrue(refreshedPhysicalDamage > baselinePhysicalDamage * 1.20,
                "Damage-blocked Superconduct should refresh physical RES shred");
    }

    private static void testAccuracyPhaseG_ShatterDamageSequenceContract() {
        CombatSimulator sim = simulatorWith(testCharacter(
                Element.PHYSICAL, CharacterId.SUCROSE));
        sim.addCharacter(testCharacter(Element.PHYSICAL, CharacterId.XIANGLING));
        List<ReactionResult.Kind> kinds = captureReactionKinds(sim);
        double shatterDamage = expectedTransformative(
                3.0, Element.PHYSICAL, 0.0);

        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                shatterHit("Initial Shatter sequence fixture"));
        assertClose(shatterDamage, sim.getTotalDamage(), 0.5,
                "The first Shatter should deal reaction damage");

        sim.advanceTime(0.1);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                shatterHit("Pre-target-boundary Shatter fixture"));
        assertClose(shatterDamage, sim.getTotalDamage(), 0.5,
                "A different owner should be damage-blocked before 0.2 seconds");
        assertTrue(!sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Target-blocked Shatter should still clear Freeze");

        sim.advanceTime(0.1);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                shatterHit("Second owner Shatter fixture"));
        assertClose(shatterDamage * 2.0, sim.getTotalDamage(), 0.5,
                "The owner should deal its second hit at the target boundary");
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertClose(0.4, snapshot.shatterTargetDamageCooldownEndTime, EPS,
                "Second Shatter should advance the target damage GCD");
        ReactionState.FixedDamageSequenceState ownerState =
                snapshot.shatterOwnerDamageSequenceStates.get(
                        CharacterId.SUCROSE);
        assertClose(0.5, ownerState.windowEndTime, EPS,
                "The Shatter owner sequence should retain its fixed boundary");
        assertEquals(2, ownerState.attemptCount,
                "The Shatter owner sequence should record two accepted attempts");
        assertTrue(!snapshot.shatterOwnerDamageSequenceStates.containsKey(
                CharacterId.XIANGLING),
                "A target-blocked Shatter should not start an owner sequence");

        sim.advanceTime(0.2);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                shatterHit("Third owner Shatter fixture"));
        assertClose(shatterDamage * 2.0, sim.getTotalDamage(), 0.5,
                "The owner's third target-accepted Shatter should deal no damage");
        assertTrue(!sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Owner-blocked Shatter should still clear Freeze");

        sim.advanceTime(0.1);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                shatterHit("Post-owner-block target GCD Shatter fixture"));
        assertClose(shatterDamage * 2.0, sim.getTotalDamage(), 0.5,
                "An owner-blocked Shatter should still start the target GCD");
        assertTrue(!sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Repeated target-blocked Shatter should still clear Freeze");

        sim.advanceTime(0.1);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                shatterHit("Independent owner Shatter fixture"));
        assertClose(shatterDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Another owner should have an independent Shatter sequence");
        assertEquals(6, countReactions(kinds, ReactionResult.Kind.SHATTER),
                "Damage-blocked attempts should still notify every Shatter");

        sim.restoreSnapshot(snapshot);
        assertClose(shatterDamage * 2.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should rewind Shatter damage");
        sim.advanceTime(0.1);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                shatterHit("Restored target-blocked Shatter fixture"));
        assertClose(shatterDamage * 2.0, sim.getTotalDamage(), 0.5,
                "Snapshot restore should recover the active Shatter target GCD");
        assertTrue(!sim.getEnemy().isFrozen(sim.getCurrentTime()),
                "Restored target-blocked Shatter should clear Freeze");

        sim.advanceTime(0.2);
        sim.getEnemy().applyFreezeAura(2.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                shatterHit("Restored exact owner-reset Shatter fixture"));
        assertClose(shatterDamage * 3.0, sim.getTotalDamage(), 0.5,
                "Restored Shatter owner sequence should reset at exactly 0.5 seconds");
    }

    private static void testAccuracyPhaseG_BloomDirectionalAuraConsumptionContract() {
        CombatSimulator weak = simulatorWith(testCharacter(Element.HYDRO));
        List<ReactionResult.Kind> weakKinds = captureReactionKinds(weak);
        weak.getEnemy().applyAura(Element.DENDRO, 2.0, weak.getCurrentTime());
        weak.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("1U weak Bloom consumption fixture", Element.HYDRO));
        assertClose(1.1, weak.getEnemy().getAuraUnits(Element.DENDRO), EPS,
                "1U Hydro Bloom should consume 0.5U from taxed 2U Dendro");
        assertEquals(1, countReactions(weakKinds, ReactionResult.Kind.BLOOM),
                "Directional Bloom should emit one typed reaction");
        assertEquals(1, weak.getDendroCores().size(),
                "Directional Bloom should create one Dendro Core");
        assertEquals(CharacterId.SUCROSE, weak.getDendroCores().get(0).ownerId,
                "Directional Bloom should retain the triggering character as core owner");
        assertClose(0.0, weak.getTotalDamage(), EPS,
                "Directional consumption should not make Bloom deal immediate damage");

        CombatSimulator strongWeak = simulatorWith(testCharacter(Element.HYDRO));
        strongWeak.getEnemy().applyAura(Element.DENDRO, 2.0, strongWeak.getCurrentTime());
        AttackAction twoUnitHydro = reactionHit("2U weak Bloom consumption fixture", Element.HYDRO);
        twoUnitHydro.setICD(ICDType.None, ICDTag.None, 2.0);
        strongWeak.performActionWithoutTimeAdvance(CharacterId.SUCROSE, twoUnitHydro);
        assertClose(0.6, strongWeak.getEnemy().getAuraUnits(Element.DENDRO), EPS,
                "2U Hydro Bloom should consume 1U from taxed 2U Dendro");

        CombatSimulator strong = simulatorWith(testCharacter(Element.DENDRO));
        strong.getEnemy().applyAura(Element.HYDRO, 4.0, strong.getCurrentTime());
        strong.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("1U strong Bloom consumption fixture", Element.DENDRO));
        assertClose(1.2, strong.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "1U Dendro Bloom should consume 2U from taxed 4U Hydro");

        for (double hydroSourceGauge : new double[] { 2.0, 1.0 }) {
            CombatSimulator depleted = simulatorWith(testCharacter(Element.DENDRO));
            depleted.getEnemy().applyAura(
                    Element.HYDRO, hydroSourceGauge, depleted.getCurrentTime());
            depleted.performActionWithoutTimeAdvance(
                    CharacterId.SUCROSE, reactionHit("Strong Bloom depletion fixture", Element.DENDRO));
            assertClose(0.0, depleted.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                    "Dendro Bloom should fully remove Hydro at or below 2U consumption");
        }

        CombatSimulator lunarWeak = simulatorWith(testCharacter(Element.HYDRO).asLunar());
        List<ReactionResult.Kind> lunarWeakKinds = captureReactionKinds(lunarWeak);
        lunarWeak.getEnemy().applyAura(
                Element.DENDRO, 2.0, lunarWeak.getCurrentTime());
        lunarWeak.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Weak Lunar-Bloom fixture", Element.HYDRO));
        assertClose(1.1, lunarWeak.getEnemy().getAuraUnits(Element.DENDRO), EPS,
                "Lunar-Bloom should preserve weak Hydro consumption");
        assertEquals(1, countReactions(lunarWeakKinds, ReactionResult.Kind.LUNAR_BLOOM),
                "Directional Lunar-Bloom should emit one typed reaction");
        assertEquals(1, lunarWeak.getDendroCores().size(),
                "Directional Lunar-Bloom should create one Dendro Core");
        assertEquals(CharacterId.SUCROSE, lunarWeak.getDendroCores().get(0).ownerId,
                "Directional Lunar-Bloom should retain core ownership");
        assertEquals(1, lunarWeak.getVerdantDewCount(),
                "Directional Lunar-Bloom should increment Verdant Dew once");
        assertEquals(1, lunarWeak.getMoonridgeDewCount(),
                "Directional Lunar-Bloom should increment Moonridge Dew once");

        CombatSimulator lunarStrong = simulatorWith(testCharacter(Element.DENDRO).asLunar());
        lunarStrong.getEnemy().applyAura(Element.HYDRO, 4.0, lunarStrong.getCurrentTime());
        lunarStrong.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Strong Lunar-Bloom fixture", Element.DENDRO));
        assertClose(1.2, lunarStrong.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Lunar-Bloom should preserve strong Dendro consumption");
        assertEquals(1, lunarStrong.getDendroCores().size(),
                "Strong Lunar-Bloom should retain core creation");
        assertEquals(1, lunarStrong.getVerdantDewCount(),
                "Strong Lunar-Bloom should increment Verdant Dew once");
        assertEquals(1, lunarStrong.getMoonridgeDewCount(),
                "Strong Lunar-Bloom should increment Moonridge Dew once");
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
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.ELECTRO), EPS,
                "Electro-Charged should tax the new Electro source aura once");

        sim.advanceTime(1.01);
        // Hydro is explicit fixture state; Electro is a taxed 1U source aura.
        assertClose(0.6 - 1.0 / 11.0, sim.getEnemy().getAuraUnits(Element.HYDRO), 0.01,
                "Standard Electro-Charged tick should consume 0.4U Hydro on top of natural decay");
        assertClose(0.4 - 0.8 / 9.5, sim.getEnemy().getAuraUnits(Element.ELECTRO), 0.01,
                "Standard Electro-Charged tick should consume 0.4U Electro on top of natural decay");
    }

    private static void testAccuracyPhaseA_ElectroChargedPrematureExpiry() {
        CombatSimulator premature = simulatorWith(testCharacter(Element.ELECTRO));
        ReactionEffectScheduler prematureScheduler = new ReactionEffectScheduler(premature);
        premature.getEnemy().setAura(Element.HYDRO, 0.5, premature.getCurrentTime());
        prematureScheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        premature.advanceTime(1.0);
        assertClose(900.0, premature.getTotalDamage(), EPS,
                "Standard EC should retain its first nominal one-second tick");
        double prematureExpiry = premature.getEnemy().getAuraExpiryTime(
                Element.HYDRO, premature.getCurrentTime());
        assertClose(1.7, prematureExpiry, EPS,
                "Nominal consumption should leave a Hydro Aura expiring 0.7 seconds later");
        premature.advanceTime(prematureExpiry - premature.getCurrentTime() - 0.001);
        assertClose(900.0, premature.getTotalDamage(), EPS,
                "Premature EC damage should not occur before exact Aura expiry");
        premature.advanceTime(0.001);
        assertClose(1800.0, premature.getTotalDamage(), EPS,
                "Aura expiry more than 0.5 seconds later should deal one premature EC tick");
        assertClose(2.08, premature.getEnemy().getAuraUnits(
                        Element.ELECTRO, premature.getCurrentTime()), EPS,
                "Premature damage tick should consume another 0.4U from the remaining Aura");
        assertTrue(!premature.isECTimerRunning(),
                "Premature terminal EC damage should finish its timer");

        CombatSimulator suppressed = simulatorWith(testCharacter(Element.ELECTRO));
        ReactionEffectScheduler suppressedScheduler = new ReactionEffectScheduler(suppressed);
        suppressed.getEnemy().setAura(Element.HYDRO, 0.48, suppressed.getCurrentTime());
        suppressedScheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        suppressed.advanceTime(1.0);
        double suppressedExpiry = suppressed.getEnemy().getAuraExpiryTime(
                Element.HYDRO, suppressed.getCurrentTime());
        assertClose(1.4, suppressedExpiry, EPS,
                "Suppression fixture should leave only 0.4 seconds of Hydro Aura");
        suppressed.advanceTime(suppressedExpiry - suppressed.getCurrentTime());
        assertClose(900.0, suppressed.getTotalDamage(), EPS,
                "Aura expiry within 0.5 seconds should not deal terminal EC damage");
        assertTrue(!suppressed.isECTimerRunning(),
                "Suppressed terminal expiry should finish its EC timer");
        assertClose(2.536470588, suppressed.getEnemy().getAuraUnits(
                        Element.ELECTRO, suppressed.getCurrentTime()), 1e-6,
                "Suppressed expiry should not consume another 0.4U Electro");

        CombatSimulator extended = simulatorWith(testCharacter(Element.ELECTRO));
        ReactionEffectScheduler extendedScheduler = new ReactionEffectScheduler(extended);
        extended.getEnemy().setAura(Element.HYDRO, 0.5, extended.getCurrentTime());
        extendedScheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        extended.advanceTime(1.2);
        extended.getEnemy().applyAura(Element.HYDRO, 2.0, extended.getCurrentTime());
        extended.advanceTime(0.5);
        assertClose(900.0, extended.getTotalDamage(), EPS,
                "Aura extension should cancel damage at the obsolete premature expiry");
        extended.advanceTime(0.3);
        assertClose(1800.0, extended.getTotalDamage(), EPS,
                "Extended EC should retain its original nominal second tick");

        CombatSimulator lunar = simulatorWith(testCharacter(Element.ELECTRO).asLunar());
        ReactionEffectScheduler lunarScheduler = new ReactionEffectScheduler(lunar);
        lunarScheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 1.0, 1000.0, true);
        lunar.advanceTime(1.999);
        assertClose(0.0, lunar.getTotalDamage(), EPS,
                "Lunar-Charged should not inherit the standard premature wake policy");
        lunar.advanceTime(0.001);
        assertTrue(lunar.getTotalDamage() > 0.0,
                "Lunar-Charged should retain its fixed two-second first tick");
    }

    private static void testAccuracyPhaseA_ElectroChargedRefreshOwnership() {
        double initialEm = 0.0;
        double refreshEm = 1000.0;
        TestCharacter initialOwner = testCharacter(
                Element.ELECTRO, CharacterId.SUCROSE)
                .withStat(StatType.ELEMENTAL_MASTERY, initialEm);
        TestCharacter refreshOwner = testCharacter(
                Element.HYDRO, CharacterId.XIANGLING)
                .withStat(StatType.ELEMENTAL_MASTERY, refreshEm);
        CombatSimulator sim = simulatorWith(initialOwner);
        sim.addCharacter(refreshOwner);
        List<CharacterId> reactionOwners = new ArrayList<>();
        sim.addReactionListener((result, source, time, simulator) -> {
            if (result.getKind() == ReactionResult.Kind.ELECTRO_CHARGED) {
                reactionOwners.add(source.getCharacterId());
            }
        });

        sim.getEnemy().setAura(Element.HYDRO, 4.0, sim.getCurrentTime());
        sim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE,
                reactionHit("Initial standard EC owner", Element.ELECTRO));
        double initialDamage = expectedTransformative(
                2.0, Element.ELECTRO, initialEm);
        double refreshDamage = expectedTransformative(
                2.0, Element.ELECTRO, refreshEm);
        assertClose(initialDamage, sim.getTotalDamage(), 0.5,
                "A new standard EC sequence should retain its immediate damage");

        sim.advanceTime(0.2);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("First active standard EC refresh", Element.HYDRO));
        assertClose(initialDamage, sim.getTotalDamage(), 0.5,
                "An active EC refresh should not deal another immediate hit");
        ReactionState.StandardElectroChargedState refreshedState =
                sim.getStandardElectroChargedState();
        assertEquals(CharacterId.XIANGLING, refreshedState.ownerId,
                "An active EC refresh should replace the next tick owner");
        assertClose(refreshDamage / 0.9, refreshedState.preResistanceDamage, 0.5,
                "An active EC refresh should replace the next tick EM snapshot");

        sim.advanceTime(0.4);
        sim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Second active standard EC refresh", Element.HYDRO));
        assertClose(initialDamage, sim.getTotalDamage(), 0.5,
                "An active EC refresh after 0.5 seconds should still defer damage");
        assertEquals(3, reactionOwners.size(),
                "Every active EC refresh should continue reaction notification");
        assertTrue(sim.getEnemy().getAuraUnits(
                        Element.HYDRO, sim.getCurrentTime()) > 0.0,
                "An active EC refresh should continue applying its source Aura");

        sim.advanceTime(0.4);
        assertClose(initialDamage + refreshDamage, sim.getTotalDamage(), 1.0,
                "The next standard EC tick should use the latest owner's EM snapshot");
        assertClose(initialDamage,
                sim.getDamageByCharacter(CharacterId.SUCROSE), 0.5,
                "The initial owner should retain only the new-sequence immediate hit");
        assertClose(refreshDamage,
                sim.getDamageByCharacter(CharacterId.XIANGLING), 0.5,
                "The refreshed owner should receive the next periodic EC tick");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.updateStandardElectroChargedState(CharacterId.SUCROSE, 1.0);
        sim.restoreSnapshot(snapshot);
        ReactionState.StandardElectroChargedState restoredState =
                sim.getStandardElectroChargedState();
        assertEquals(CharacterId.XIANGLING, restoredState.ownerId,
                "Snapshot restore should recover the latest standard EC owner");
        assertClose(refreshDamage / 0.9, restoredState.preResistanceDamage, 0.5,
                "Snapshot restore should recover the standard EC damage payload");
    }

    private static void testAccuracyPhaseA_ElectroChargedDamageCooldown() {
        CombatSimulator blocked = expiredElectroChargedSequenceFixture();
        List<ReactionResult.Kind> blockedReactions = new ArrayList<>();
        blocked.addReactionListener((result, source, time, simulator) ->
                blockedReactions.add(result.getKind()));
        blocked.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Blocked standard EC sequence restart", Element.HYDRO));
        assertClose(900.0, blocked.getTotalDamage(), EPS,
                "A sequence restart before target cooldown expiry should deal no damage");
        assertEquals(1, blockedReactions.size(),
                "A cooldown-blocked sequence restart should still notify");
        assertEquals(ReactionResult.Kind.ELECTRO_CHARGED,
                blockedReactions.get(0),
                "A cooldown-blocked restart should retain the EC reaction kind");
        assertTrue(blocked.isECTimerRunning(),
                "A cooldown-blocked restart should still start its periodic timer");
        assertEquals(CharacterId.XIANGLING,
                blocked.getStandardElectroChargedState().ownerId,
                "A cooldown-blocked restart should still update tick ownership");
        assertTrue(blocked.getEnemy().getAuraUnits(
                        Element.HYDRO, blocked.getCurrentTime()) > 0.0,
                "A cooldown-blocked restart should still apply Hydro Aura");
        assertClose(1.5,
                blocked.getStandardElectroChargedDamageCooldownEndTime(), EPS,
                "Blocked damage should not refresh the prior target cooldown");
        blocked.advanceTime(0.6);
        assertClose(900.0, blocked.getTotalDamage(), EPS,
                "A blocked restart should schedule its next tick from restart time");

        CombatSimulator exact = expiredElectroChargedSequenceFixture();
        exact.advanceTime(0.1);
        exact.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING,
                reactionHit("Exact standard EC sequence restart", Element.HYDRO));
        assertClose(900.0 + expectedTransformative(
                        2.0, Element.ELECTRO, 0.0),
                exact.getTotalDamage(), 0.5,
                "A sequence restart at exactly 0.5 seconds should deal damage");

        CombatSimulator noConsumption = simulatorWith(
                testCharacter(Element.ELECTRO));
        ReactionEffectScheduler scheduler =
                new ReactionEffectScheduler(noConsumption);
        noConsumption.getEnemy().setAura(
                Element.HYDRO, 4.0, noConsumption.getCurrentTime());
        scheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        noConsumption.advanceTime(0.75);
        assertTrue(noConsumption.tryStartStandardElectroChargedDamageCooldown(),
                "Cooldown fixture should accept its synthetic damage at 0.75 seconds");
        double hydroBefore = noConsumption.getEnemy().getAuraUnits(
                Element.HYDRO, noConsumption.getCurrentTime());
        double electroBefore = noConsumption.getEnemy().getAuraUnits(
                Element.ELECTRO, noConsumption.getCurrentTime());
        double hydroDecayRate = noConsumption.getEnemy().getAuraDecayRate(
                Element.HYDRO, noConsumption.getCurrentTime());
        double electroDecayRate = noConsumption.getEnemy().getAuraDecayRate(
                Element.ELECTRO, noConsumption.getCurrentTime());
        noConsumption.advanceTime(0.25);
        assertClose(0.0, noConsumption.getTotalDamage(), EPS,
                "A nominal EC tick inside the target cooldown should deal no damage");
        assertClose(hydroBefore - hydroDecayRate * 0.25,
                noConsumption.getEnemy().getAuraUnits(
                        Element.HYDRO, noConsumption.getCurrentTime()), EPS,
                "A blocked EC tick should not consume Hydro beyond natural decay");
        assertClose(electroBefore - electroDecayRate * 0.25,
                noConsumption.getEnemy().getAuraUnits(
                        Element.ELECTRO, noConsumption.getCurrentTime()), EPS,
                "A blocked EC tick should not consume Electro beyond natural decay");

        CombatSimulator snapshotSim = simulatorWith(
                testCharacter(Element.ELECTRO));
        assertTrue(snapshotSim.tryStartStandardElectroChargedDamageCooldown(),
                "Snapshot fixture should accept initial standard EC damage");
        SimulatorSnapshot snapshot = snapshotSim.saveSnapshot();
        snapshotSim.advanceTime(0.499);
        assertTrue(!snapshotSim.tryStartStandardElectroChargedDamageCooldown(),
                "Snapshot fixture should block immediately before reset");
        snapshotSim.restoreSnapshot(snapshot);
        assertClose(0.0, snapshotSim.getStandardElectroChargedLastDamageTime(), EPS,
                "Snapshot restore should recover the last successful EC damage time");
        snapshotSim.advanceTime(0.5);
        assertTrue(snapshotSim.tryStartStandardElectroChargedDamageCooldown(),
                "Snapshot-restored EC cooldown should accept the exact boundary");
    }

    private static CombatSimulator expiredElectroChargedSequenceFixture() {
        CombatSimulator sim = simulatorWith(testCharacter(
                Element.ELECTRO, CharacterId.SUCROSE));
        sim.addCharacter(testCharacter(Element.HYDRO, CharacterId.XIANGLING));
        ReactionEffectScheduler scheduler = new ReactionEffectScheduler(sim);
        sim.getEnemy().setAura(Element.HYDRO, 0.48, sim.getCurrentTime());
        scheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        sim.advanceTime(1.4);
        assertClose(900.0, sim.getTotalDamage(), EPS,
                "Expired-sequence fixture should contain one nominal EC tick");
        assertTrue(!sim.isECTimerRunning(),
                "Expired-sequence fixture should finish before restart");
        assertClose(1.0, sim.getStandardElectroChargedLastDamageTime(), EPS,
                "Expired-sequence fixture should retain its last successful tick time");
        return sim;
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
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "First standard ICD hit should apply aura");

        sim.getEnemy().setAura(Element.PYRO, 0.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD hit 2", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        assertClose(0.0, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
                "Second quick same-group hit should be ICD-blocked");

        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                standardIcdHit("Standard ICD hit 3", Element.PYRO, ICDTag.ElementalSkill, 0.0));
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
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
        assertClose(0.8, sim.getEnemy().getAuraUnits(Element.PYRO), EPS,
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

    private static void testAccuracyPhaseC_CoreDamageCapSnapshotContract() {
        CombatSimulator sim = simulatorWith(testCharacter(Element.ELECTRO));
        ReactionEffectScheduler scheduler = new ReactionEffectScheduler(sim);
        for (int i = 0; i < 4; i++) {
            sim.addDendroCore(CharacterId.SUCROSE, 1000.0);
        }

        scheduler.consumeDendroCores(
                CharacterId.SUCROSE, 1000.0, "Saved Hyperbloom", 1);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        assertEquals(1, snapshot.recentDendroCoreDamageTimes.size(),
                "Snapshot should preserve one accepted Dendro Core hit");
        assertEquals(3, snapshot.dendroCores.size(),
                "Snapshot should retain the three unconsumed cores");

        scheduler.consumeDendroCores(
                CharacterId.SUCROSE, 1000.0, "Discarded Burgeon branch", 2);
        assertClose(1800.0, sim.getTotalDamage(), EPS,
                "The future branch should accept only its second target hit");
        assertEquals(1, sim.getDendroCores().size(),
                "Damage-capped future hits should still consume cores");

        sim.restoreSnapshot(snapshot);
        scheduler.consumeDendroCores(
                CharacterId.SUCROSE, 1000.0, "Replayed Burgeon branch", 2);
        assertClose(1800.0, sim.getTotalDamage(), EPS,
                "Restore should accept replayed hit two and cap only hit three");
        assertEquals(1, sim.getDendroCores().size(),
                "Replayed capped hits should retain core-consumption behavior");
        assertEquals(2, sim.saveSnapshot().recentDendroCoreDamageTimes.size(),
                "Replay should rebuild exactly two active damage timestamps");

        sim.advanceTime(0.5);
        sim.addDendroCore(CharacterId.SUCROSE, 1000.0);
        sim.addDendroCore(CharacterId.SUCROSE, 1000.0);
        scheduler.consumeDendroCores(
                CharacterId.SUCROSE, 1000.0, "Boundary Hyperbloom", 2);
        assertClose(3600.0, sim.getTotalDamage(), EPS,
                "Exactly 0.5 seconds should accept two fresh core damage hits");
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

    private static void testAccuracyPhaseF_ArtifactOptimizerStableResultOrder() {
        List<StatType> requestedOrder = List.of(
                StatType.CRIT_RATE,
                StatType.CRIT_DMG,
                StatType.ATK_PERCENT);
        List<Map<StatType, Integer>> results = new ArrayList<>();

        String output = captureStandardOutput(() -> results.add(
                mechanics.optimization.IterativeSimulator.optimizeSubstatsNDim(
                        rolls -> simulatorWith(testCharacter(Element.PYRO, CharacterId.XIANGLING)),
                        sim -> sim.advanceTime(1.0),
                        CharacterId.XIANGLING,
                        requestedOrder,
                        6)));

        Map<StatType, Integer> result = results.get(0);
        assertEquals(requestedOrder, new ArrayList<>(result.keySet()),
                "Joint optimizer should return rolls in requested stat order");
        assertEquals(Integer.valueOf(2), result.get(StatType.CRIT_RATE),
                "Equal-DPS fixture should retain balanced CRIT Rate rolls");
        assertEquals(Integer.valueOf(2), result.get(StatType.CRIT_DMG),
                "Equal-DPS fixture should retain balanced CRIT DMG rolls");
        assertEquals(Integer.valueOf(2), result.get(StatType.ATK_PERCENT),
                "Equal-DPS fixture should retain balanced ATK rolls");
        assertTrue(output.contains(
                        "Result: {CRIT_RATE=2, CRIT_DMG=2, ATK_PERCENT=2} => DPS: 0"),
                "Joint optimizer should render result keys in requested stat order");
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
        assertClose(0.8, gaugeSim.getEnemy().getAuraUnits(Element.HYDRO), EPS,
                "Xingqiu orbital 1U source should establish 0.8U Hydro aura");

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

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.FavoniusCodex(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Favonius should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.FavoniusCodex(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Favonius should reject refinement six");

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

        double[] r1Draws = { 0.6, 0.599999, 0.0 };
        int[] r1DrawIndex = { 0 };
        model.weapon.FavoniusCodex r1Weapon = new model.weapon.FavoniusCodex(
                1, () -> r1Draws[r1DrawIndex[0]++]);
        TestCharacter r1Owner = testCharacter(Element.HYDRO).withStat(StatType.CRIT_RATE, 1.0);
        r1Owner.setWeapon(r1Weapon);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1Owner.restoreCurrentEnergy(0.0);

        r1Weapon.onDamage(r1Owner, hit, 0.0, r1Sim);
        assertEquals(1, r1DrawIndex[0], "An R1 draw equal to 60% should not trigger Windfall");
        assertClose(0.0, r1Owner.getCurrentEnergy(), EPS,
                "A failed R1 Windfall draw should not generate particles");
        captureStandardOutput(() -> r1Weapon.onDamage(r1Owner, hit, 0.0, r1Sim));
        double r1Energy = r1Owner.getCurrentEnergy();
        assertTrue(r1Energy > 0.0, "An R1 draw below 60% should generate neutral particles");
        r1Weapon.onDamage(r1Owner, hit, 11.999, r1Sim);
        assertEquals(2, r1DrawIndex[0], "R1 Windfall should not draw before twelve seconds");
        captureStandardOutput(() -> r1Weapon.onDamage(r1Owner, hit, 12.0, r1Sim));
        assertEquals(3, r1DrawIndex[0], "R1 Windfall should draw at exactly twelve seconds");
        assertTrue(r1Owner.getCurrentEnergy() > r1Energy,
                "R1 Windfall should generate particles again at its exact cooldown boundary");
    }

    private static void testAccuracyPhaseF_FavoniusFamilyMetadata() {
        model.weapon.FavoniusSword sword = new model.weapon.FavoniusSword(5, () -> 0.0);
        assertWeaponMetadata(
                sword, "Favonius Sword", 454.0, 0.613, model.type.WeaponType.SWORD);
        assertFavoniusWindfallGeneratesEnergy(sword, "Favonius Sword");

        model.weapon.FavoniusGreatsword greatsword =
                new model.weapon.FavoniusGreatsword(5, () -> 0.0);
        assertWeaponMetadata(greatsword, "Favonius Greatsword", 454.0, 0.613,
                model.type.WeaponType.CLAYMORE);
        assertFavoniusWindfallGeneratesEnergy(greatsword, "Favonius Greatsword");

        model.weapon.FavoniusLance lance = new model.weapon.FavoniusLance(5, () -> 0.0);
        assertWeaponMetadata(
                lance, "Favonius Lance", 565.0, 0.306, model.type.WeaponType.POLEARM);
        assertFavoniusWindfallGeneratesEnergy(lance, "Favonius Lance");

        model.weapon.FavoniusWarbow warbow = new model.weapon.FavoniusWarbow(5, () -> 0.0);
        assertWeaponMetadata(
                warbow, "Favonius Warbow", 454.0, 0.613, model.type.WeaponType.BOW);
        assertFavoniusWindfallGeneratesEnergy(warbow, "Favonius Warbow");
    }

    private static void testAccuracyPhaseF_SacrificialSwordProcBoundaries() {
        boolean nullRejected = false;
        try {
            new model.weapon.SacrificialSword(null);
        } catch (NullPointerException expected) {
            nullRejected = true;
        }
        assertTrue(nullRejected, "Sacrificial Sword should reject a null proc draw source");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.SacrificialSword(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Sacrificial should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.SacrificialSword(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Sacrificial should reject refinement six");

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

        double[] r1Draws = { 0.4, 0.399999, 0.0 };
        int[] r1DrawIndex = { 0 };
        model.weapon.SacrificialSword r1Weapon = new model.weapon.SacrificialSword(
                1, () -> r1Draws[r1DrawIndex[0]++]);
        TestCharacter r1Owner = testCharacter(Element.HYDRO);
        r1Owner.setSkillCD(60.0);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1Owner.markSkillUsed(0.0);
        r1Weapon.onDamage(r1Owner, skillHit, 0.0, r1Sim);
        assertEquals(1, r1DrawIndex[0], "An R1 draw equal to 40% should fail Composed");
        assertTrue(!r1Owner.canSkill(0.0), "A failed R1 draw should preserve Skill cooldown");
        r1Weapon.onDamage(r1Owner, skillHit, 0.0, r1Sim);
        assertEquals(2, r1DrawIndex[0], "A failed R1 draw should permit an immediate retry");
        assertTrue(r1Owner.canSkill(0.0), "An R1 draw below 40% should reset Skill cooldown");
        r1Owner.markSkillUsed(1.0);
        r1Weapon.onDamage(r1Owner, skillHit, 29.999, r1Sim);
        assertEquals(2, r1DrawIndex[0], "R1 Composed should not draw before thirty seconds");
        assertTrue(!r1Owner.canSkill(29.999),
                "A pre-boundary R1 hit should preserve Skill cooldown");
        r1Weapon.onDamage(r1Owner, skillHit, 30.0, r1Sim);
        assertEquals(3, r1DrawIndex[0], "R1 Composed should draw at exactly thirty seconds");
        assertTrue(r1Owner.canSkill(30.0),
                "An exact-boundary R1 success should reset Skill cooldown");
    }

    private static void testAccuracyPhaseF_SacrificialFamilyMetadata() {
        model.weapon.SacrificialGreatsword greatsword =
                new model.weapon.SacrificialGreatsword(5, () -> 0.0);
        assertWeaponMetadata(greatsword, "Sacrificial Greatsword", 565.0, 0.306,
                model.type.WeaponType.CLAYMORE);
        assertSacrificialResetsSkill(greatsword, "Sacrificial Greatsword");

        model.weapon.SacrificialBow bow = new model.weapon.SacrificialBow(5, () -> 0.0);
        assertWeaponMetadata(
                bow, "Sacrificial Bow", 565.0, 0.306, model.type.WeaponType.BOW);
        assertSacrificialResetsSkill(bow, "Sacrificial Bow");

        model.weapon.SacrificialFragments fragments =
                new model.weapon.SacrificialFragments(5, () -> 0.0);
        assertEquals("Sacrificial Fragments", fragments.getName(),
                "Sacrificial Fragments display name");
        assertClose(454.0, fragments.getBaseAtk(), EPS,
                "Sacrificial Fragments base ATK");
        assertClose(221.0, fragments.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Sacrificial Fragments Elemental Mastery");
        assertClose(0.0, fragments.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Sacrificial Fragments should not add Energy Recharge");
        assertEquals(model.type.WeaponType.CATALYST, fragments.getWeaponType(),
                "Sacrificial Fragments weapon type");
        assertSacrificialResetsSkill(fragments, "Sacrificial Fragments");
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
        TestCharacter ally = testCharacter(Element.ANEMO, CharacterId.XIANGLING);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(ally);
        double beforeCrit = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.CRIT_RATE);
        assertClose(0.0, resolvedStat(sim, ally, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Night should grant no Lunar bonus before Intent is active");
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(CharacterId.SUCROSE,
                reactionHit("Artifact Lunar-Charged trigger", Element.ELECTRO));
        double afterCrit = owner.getEffectiveStats(sim.getCurrentTime()).get(StatType.CRIT_RATE);
        assertClose(beforeCrit + 0.15, afterCrit, EPS,
                "Night of the Sky's Unveiling should grant on-field Lunar reaction CRIT Rate");
        assertClose(0.10, resolvedStat(sim, owner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Night Intent should grant its wearer 10% Lunar-Charged DMG");
        assertClose(0.10, resolvedStat(sim, ally, StatType.LUNAR_BLOOM_DMG_BONUS), EPS,
                "Night Intent should grant allies 10% Lunar-Bloom DMG");
        assertClose(0.10, resolvedStat(sim, ally, StatType.LUNAR_CRYSTALLIZE_DMG_BONUS), EPS,
                "Night Intent should grant allies 10% Lunar-Crystallize DMG");

        Buff intent = owner.getActiveBuffs().stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_INTENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Night Intent should be active"));
        Buff synergy = sim.getApplicableBuffs(ally).stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_SYNERGY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Night synergy provider should be routed"));
        assertEquals(owner.getCharacterId(), synergy.getSourceCharacterId(),
                "Night-only synergy should be sourced by its canonical owner");
        StatsContainer atIntentExpiry = new StatsContainer();
        synergy.apply(atIntentExpiry, intent.getExpirationTime());
        assertClose(0.0, atIntentExpiry.get(StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Exact Intent expiry should remove the Night-only synergy");

        TestCharacter firstOwner = testCharacter(Element.ELECTRO, CharacterId.FLINS).asLunar();
        firstOwner.setArtifacts(new model.artifact.NightOfTheSkysUnveiling());
        TestCharacter secondOwner = testCharacter(Element.HYDRO, CharacterId.COLUMBINA).asLunar();
        secondOwner.setArtifacts(new model.artifact.NightOfTheSkysUnveiling());
        CombatSimulator duplicateSim = simulatorWith(firstOwner);
        duplicateSim.addCharacter(secondOwner);
        duplicateSim.updateMoonsign();
        duplicateSim.setActiveCharacter(CharacterId.FLINS);
        duplicateSim.notifyReaction(ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED), firstOwner);
        assertClose(0.10, resolvedStat(duplicateSim, secondOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Duplicate Night sets should retain one distinct Intent bonus");
        List<Buff> duplicateSynergies = duplicateSim.getApplicableBuffs(secondOwner).stream()
                .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_SYNERGY)
                .collect(java.util.stream.Collectors.toList());
        assertEquals(1, duplicateSynergies.size(),
                "Duplicate Night sets should expose one canonical synergy provider");
        assertEquals(CharacterId.FLINS, duplicateSynergies.get(0).getSourceCharacterId(),
                "The first Night wearer should source a Night-only duplicate party");
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
        assertEquals(1L, sim.getApplicableBuffs(intentOwner).stream()
                        .filter(buff -> buff.getId() == BuffId.GLEAMING_MOON_SYNERGY)
                        .count(),
                "Mixed Night and Silken sets should expose one canonical synergy provider");
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

    private static void testAccuracyPhaseF_AubadeInitializationContract() {
        model.artifact.AubadeOfMorningstarAndMoon empty =
                new model.artifact.AubadeOfMorningstarAndMoon();
        assertClose(80.0, empty.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Aubade should grant 80 Elemental Mastery as its two-piece bonus");
        assertClose(0.0, empty.getStats().get(StatType.ATK_PERCENT), EPS,
                "Aubade should not retain the obsolete 18% ATK bonus");

        StatsContainer suppliedStats = new StatsContainer();
        suppliedStats.add(StatType.ELEMENTAL_MASTERY, 25.0);
        suppliedStats.add(StatType.CRIT_RATE, 0.10);
        model.artifact.AubadeOfMorningstarAndMoon supplied =
                new model.artifact.AubadeOfMorningstarAndMoon(suppliedStats);
        assertClose(105.0, supplied.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Aubade should add its 80 EM once to supplied artifact stats");
        assertClose(0.10, supplied.getStats().get(StatType.CRIT_RATE), EPS,
                "Aubade should preserve supplied main and substats");
        assertClose(0.0, supplied.getStats().get(StatType.ATK_PERCENT), EPS,
                "Supplied Aubade stats should not acquire the obsolete ATK bonus");

        TestCharacter activeAlly = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        TestCharacter offFieldOwner = testCharacter(Element.ELECTRO, CharacterId.INEFFA).asLunar();
        offFieldOwner.setArtifacts(new model.artifact.AubadeOfMorningstarAndMoon());
        CombatSimulator sim = simulatorWith(activeAlly);
        sim.addCharacter(offFieldOwner);
        sim.setMoonsign(CombatSimulator.Moonsign.NASCENT_GLEAM);

        assertClose(0.20, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "An initially off-field Aubade owner should receive 20% Lunar damage");
        assertClose(0.0, resolvedStat(sim, activeAlly, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Aubade should not grant its owner-only bonus to allies");
        sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "An Ascendant off-field owner should receive the full 60% bonus");
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_BLOOM_DMG_BONUS), EPS,
                "Aubade should apply its dynamic bonus to Lunar-Bloom");
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CRYSTALLIZE_DMG_BONUS), EPS,
                "Aubade should apply its dynamic bonus to Lunar-Crystallize");

        sim.switchCharacter(CharacterId.INEFFA);
        sim.advanceTime(1.0);
        SimulatorSnapshot lingeringSnapshot = sim.saveSnapshot();
        sim.advanceTime(1.899);
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Aubade should remain active just before three on-field seconds");
        sim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Aubade should expire at exactly three on-field seconds");

        sim.restoreSnapshot(lingeringSnapshot);
        assertEquals(CharacterId.INEFFA, sim.getActiveCharacter().getCharacterId(),
                "Aubade snapshot restore should recover the active owner");
        sim.advanceTime(1.899);
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Aubade snapshot restore should preserve remaining linger time");
        sim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Restored Aubade linger should retain exact expiry");
        sim.switchCharacter(CharacterId.SUCROSE);
        assertClose(0.60, resolvedStat(sim, offFieldOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "Switching out should reactivate Aubade immediately");
        assertEquals(1L, offFieldOwner.getActiveBuffs().stream()
                        .filter(buff -> buff.getId() == BuffId.AUBADE_BONUS)
                        .count(),
                "Repeated Aubade transitions should retain one typed owner buff");

        TestCharacter activeOwner = testCharacter(Element.ELECTRO, CharacterId.INEFFA).asLunar();
        activeOwner.setArtifacts(new model.artifact.AubadeOfMorningstarAndMoon());
        CombatSimulator activeSim = simulatorWith(activeOwner);
        activeSim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        assertClose(0.0, resolvedStat(activeSim, activeOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "An initially active Aubade owner should not start with the off-field bonus");
        TestCharacter secondAlly = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        activeSim.addCharacter(secondAlly);
        activeSim.switchCharacter(CharacterId.SUCROSE);
        assertClose(0.60, resolvedStat(activeSim, activeOwner, StatType.LUNAR_CHARGED_DMG_BONUS), EPS,
                "An initially active owner should activate Aubade after first switching out");
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
        boolean lowRefinementRejected = false;
        try {
            new model.weapon.DragonsBane(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Dragon's Bane should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.DragonsBane(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Dragon's Bane should reject refinement six");

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

        TestCharacter r1Owner = testCharacter(Element.PYRO);
        model.weapon.DragonsBane r1Weapon = new model.weapon.DragonsBane(1);
        r1Owner.setWeapon(r1Weapon);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        double r1BaseDamage = calculateDirectDamage(r1Sim, r1Owner, directHit, 0.0, 1.0);
        r1Sim.getEnemy().setAura(Element.HYDRO, 1.0);
        assertClose(r1BaseDamage * 1.20,
                calculateDirectDamage(r1Sim, r1Owner, directHit, 0.0, 1.0), EPS,
                "R1 Dragon's Bane should grant 20% damage against Hydro aura");
        assertEquals(1, r1Weapon.getRefinement(), "Dragon's Bane should expose refinement rank");
    }

    private static void testAccuracyPhaseF_TargetAuraWeaponMetadata() {
        model.weapon.LionsRoar lionsRoar = new model.weapon.LionsRoar();
        assertEquals("Lion's Roar", lionsRoar.getName(), "Lion's Roar display name");
        assertClose(510.0, lionsRoar.getBaseAtk(), EPS, "Lion's Roar base ATK");
        assertClose(0.413, lionsRoar.getStats().get(StatType.ATK_PERCENT), EPS,
                "Lion's Roar ATK substat");
        assertEquals(model.type.WeaponType.SWORD, lionsRoar.getWeaponType(),
                "Lion's Roar weapon type");
        assertTargetAuraWeaponDamage(
                lionsRoar, Element.PYRO, Element.HYDRO, 0.36, "Lion's Roar");

        model.weapon.LionsRoar r1LionsRoar = new model.weapon.LionsRoar(1);
        assertTargetAuraWeaponDamage(
                r1LionsRoar, Element.ELECTRO, Element.HYDRO, 0.20, "R1 Lion's Roar");

        model.weapon.Rainslasher rainslasher = new model.weapon.Rainslasher();
        assertEquals("Rainslasher", rainslasher.getName(), "Rainslasher display name");
        assertClose(510.0, rainslasher.getBaseAtk(), EPS, "Rainslasher base ATK");
        assertClose(165.0, rainslasher.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Rainslasher Elemental Mastery");
        assertEquals(model.type.WeaponType.CLAYMORE, rainslasher.getWeaponType(),
                "Rainslasher weapon type");
        assertTargetAuraWeaponDamage(
                rainslasher, Element.HYDRO, Element.PYRO, 0.36, "Rainslasher");

        model.weapon.MagicGuide magicGuide = new model.weapon.MagicGuide();
        assertEquals("Magic Guide", magicGuide.getName(), "Magic Guide display name");
        assertClose(354.0, magicGuide.getBaseAtk(), EPS, "Magic Guide base ATK");
        assertClose(187.0, magicGuide.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Magic Guide Elemental Mastery");
        assertEquals(model.type.WeaponType.CATALYST, magicGuide.getWeaponType(),
                "Magic Guide weapon type");
        assertTargetAuraWeaponDamage(
                magicGuide, Element.ELECTRO, Element.PYRO, 0.24, "Magic Guide");

        model.weapon.MagicGuide r1MagicGuide = new model.weapon.MagicGuide(1);
        assertTargetAuraWeaponDamage(
                r1MagicGuide, Element.HYDRO, Element.PYRO, 0.12, "R1 Magic Guide");

        model.weapon.CoolSteel coolSteel = new model.weapon.CoolSteel();
        assertEquals("Cool Steel", coolSteel.getName(), "Cool Steel display name");
        assertClose(401.0, coolSteel.getBaseAtk(), EPS, "Cool Steel base ATK");
        assertClose(0.352, coolSteel.getStats().get(StatType.ATK_PERCENT), EPS,
                "Cool Steel ATK substat");
        assertEquals(model.type.WeaponType.SWORD, coolSteel.getWeaponType(),
                "Cool Steel weapon type");
        assertTargetAuraWeaponDamage(
                coolSteel, Element.HYDRO, Element.ELECTRO, 0.24, "Cool Steel");

        model.weapon.CoolSteel r1CoolSteel = new model.weapon.CoolSteel(1);
        assertTargetAuraWeaponDamage(
                r1CoolSteel, Element.CRYO, Element.PYRO, 0.12, "R1 Cool Steel");

        model.weapon.BloodtaintedGreatsword bloodtaintedGreatsword =
                new model.weapon.BloodtaintedGreatsword();
        assertEquals("Bloodtainted Greatsword", bloodtaintedGreatsword.getName(),
                "Bloodtainted Greatsword display name");
        assertClose(354.0, bloodtaintedGreatsword.getBaseAtk(), EPS,
                "Bloodtainted Greatsword base ATK");
        assertClose(187.0,
                bloodtaintedGreatsword.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Bloodtainted Greatsword Elemental Mastery");
        assertEquals(model.type.WeaponType.CLAYMORE, bloodtaintedGreatsword.getWeaponType(),
                "Bloodtainted Greatsword weapon type");
        assertTargetAuraWeaponDamage(
                bloodtaintedGreatsword, Element.PYRO, Element.HYDRO, 0.24,
                "Bloodtainted Greatsword");

        model.weapon.BloodtaintedGreatsword r1BloodtaintedGreatsword =
                new model.weapon.BloodtaintedGreatsword(1);
        assertTargetAuraWeaponDamage(
                r1BloodtaintedGreatsword, Element.ELECTRO, Element.HYDRO, 0.12,
                "R1 Bloodtainted Greatsword");

        model.weapon.RavenBow ravenBow = new model.weapon.RavenBow();
        assertEquals("Raven Bow", ravenBow.getName(), "Raven Bow display name");
        assertClose(448.0, ravenBow.getBaseAtk(), EPS, "Raven Bow base ATK");
        assertClose(94.0, ravenBow.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Raven Bow Elemental Mastery");
        assertEquals(model.type.WeaponType.BOW, ravenBow.getWeaponType(),
                "Raven Bow weapon type");
        assertTargetAuraWeaponDamage(
                ravenBow, Element.HYDRO, Element.ELECTRO, 0.24, "Raven Bow");

        model.weapon.RavenBow r1RavenBow = new model.weapon.RavenBow(1);
        assertTargetAuraWeaponDamage(
                r1RavenBow, Element.PYRO, Element.ELECTRO, 0.12, "R1 Raven Bow");
    }

    private static void testAccuracyPhaseF_KaeyaCharacterContract() {
        assertEquals(CharacterId.KAEYA, CharacterId.fromName("Kaeya"),
                "Kaeya display name should resolve to a typed id");
        assertEquals(CharacterId.KAEYA, CharacterId.fromNumericId(9),
                "Kaeya numeric id should round trip");

        model.character.Kaeya configured = new model.character.Kaeya(
                new TestWeapon(), blankArtifact());
        assertClose(11636.0, configured.getBaseStats().get(StatType.BASE_HP), EPS,
                "Kaeya Lv90 base HP");
        assertClose(223.0, configured.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Kaeya Lv90 base ATK");
        assertClose(792.0, configured.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Kaeya Lv90 base DEF");
        assertClose(1.267, configured.getBaseStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Kaeya Lv90 Energy Recharge");

        RecordingDamageWeapon baselineSkillWeapon = new RecordingDamageWeapon("Frostgnaw");
        model.character.Kaeya baselineSkillKaeya = new model.character.Kaeya(
                baselineSkillWeapon, blankArtifact(), kaeyaTalentData(0));
        CombatSimulator baselineSkillSim = simulatorWithExistingCharacter(baselineSkillKaeya);
        baselineSkillKaeya.restoreCurrentEnergy(0.0);
        baselineSkillSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(1, baselineSkillWeapon.actions.size(),
                "Frostgnaw should deal one Skill hit");
        AttackAction frostgnaw = baselineSkillWeapon.actions.get(0);
        assertEquals(ActionType.SKILL, frostgnaw.getActionType(),
                "Frostgnaw should retain Skill typing");
        assertEquals(ICDType.None, frostgnaw.getICDType(),
                "Frostgnaw should have no elemental ICD");
        assertClose(2.0, frostgnaw.getGaugeUnits(), EPS,
                "Frostgnaw should apply 2U Cryo");
        assertClose(3.2504, frostgnaw.getDamagePercent(), EPS,
                "C0 Frostgnaw should use talent-9 damage");
        assertClose(2.67 * 3.0, baselineSkillKaeya.getTotalParticleEnergy(), EPS,
                "Single-target Frostgnaw should generate 2.67 Cryo particles");

        model.character.Kaeya frozenSkillKaeya = new model.character.Kaeya(
                new TestWeapon(), blankArtifact(), kaeyaTalentData(0));
        CombatSimulator frozenSkillSim = simulatorWithExistingCharacter(frozenSkillKaeya);
        frozenSkillKaeya.restoreCurrentEnergy(0.0);
        frozenSkillSim.getEnemy().setAura(Element.HYDRO, 1.0);
        frozenSkillSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertTrue(frozenSkillSim.getEnemy().isFrozen(frozenSkillSim.getCurrentTime()),
                "Frostgnaw should Freeze a Hydro-affected target");
        assertClose(3.67 * 3.0, frozenSkillKaeya.getTotalParticleEnergy(), EPS,
                "Frozen single-target Frostgnaw should add one A4 particle");

        RecordingDamageWeapon c0NormalWeapon = new RecordingDamageWeapon("Kaeya N1");
        model.character.Kaeya c0NormalKaeya = new model.character.Kaeya(
                c0NormalWeapon, blankArtifact(), kaeyaTalentData(0));
        CombatSimulator c0NormalSim = simulatorWithExistingCharacter(c0NormalKaeya);
        c0NormalSim.getEnemy().setAura(Element.CRYO, 1.0);
        c0NormalSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        assertClose(0.0,
                c0NormalWeapon.actions.get(0).getExtraBonuses()
                        .getOrDefault(StatType.CRIT_RATE, 0.0), EPS,
                "C0 Kaeya should not receive Excellent Blood CRIT Rate");

        RecordingDamageWeapon c1NormalWeapon = new RecordingDamageWeapon("Kaeya N");
        model.character.Kaeya c1NormalKaeya = new model.character.Kaeya(
                c1NormalWeapon, blankArtifact(), kaeyaTalentData(1));
        CombatSimulator c1NormalSim = simulatorWithExistingCharacter(c1NormalKaeya);
        c1NormalSim.getEnemy().setAura(Element.CRYO, 1.0);
        c1NormalSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        assertClose(0.15,
                c1NormalWeapon.actions.get(0).getExtraBonuses()
                        .getOrDefault(StatType.CRIT_RATE, 0.0), EPS,
                "C1 Kaeya should gain 15% Normal CRIT Rate against Cryo aura");
        c1NormalSim.getEnemy().setAura(Element.CRYO, 0.0);
        c1NormalSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.NORMAL));
        assertClose(0.0,
                c1NormalWeapon.actions.get(1).getExtraBonuses()
                        .getOrDefault(StatType.CRIT_RATE, 0.0), EPS,
                "C1 Kaeya should not gain CRIT Rate without Cryo or Frozen state");

        RecordingDamageWeapon c0BurstWeapon = new RecordingDamageWeapon("Glacial Waltz Icicle");
        model.character.Kaeya c0BurstKaeya = new model.character.Kaeya(
                c0BurstWeapon, blankArtifact(), kaeyaTalentData(0));
        CombatSimulator c0BurstSim = simulatorWithExistingCharacter(c0BurstKaeya);
        c0BurstKaeya.restoreCurrentEnergy(60.0);
        c0BurstSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.0, c0BurstKaeya.getCurrentEnergy(), EPS,
                "C0 Glacial Waltz should consume 60 Energy");
        c0BurstSim.advanceTime(8.0);
        assertEquals(13, c0BurstWeapon.actions.size(),
                "C0 stationary Glacial Waltz should deal thirteen hits");
        AttackAction c0Icicle = c0BurstWeapon.actions.get(0);
        assertTrue(c0Icicle.isUseSnapshot(), "Glacial Waltz should use its cast snapshot");
        assertEquals(ICDType.Standard, c0Icicle.getICDType(),
                "Glacial Waltz should use standard ICD");
        assertClose(1.0, c0Icicle.getGaugeUnits(), EPS,
                "Glacial Waltz should apply 1U Cryo on eligible hits");
        assertClose(1.3192, c0Icicle.getDamagePercent(), EPS,
                "C0 Glacial Waltz should use talent-9 damage");

        RecordingDamageWeapon c6BurstWeapon = new RecordingDamageWeapon("Glacial Waltz Icicle");
        model.character.Kaeya c6BurstKaeya = new model.character.Kaeya(
                c6BurstWeapon, blankArtifact(), kaeyaTalentData(6));
        CombatSimulator c6BurstSim = simulatorWithExistingCharacter(c6BurstKaeya);
        TestCharacter c6BurstAlly = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        c6BurstSim.addCharacter(c6BurstAlly);
        c6BurstKaeya.restoreCurrentEnergy(60.0);
        c6BurstAlly.restoreCurrentEnergy(0.0);
        c6BurstSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(15.0, c6BurstKaeya.getCurrentEnergy(), EPS,
                "C6 Glacial Waltz should refund 15 flat Energy only to Kaeya");
        assertClose(0.0, c6BurstAlly.getCurrentEnergy(), EPS,
                "C6 Glacial Waltz should not refund flat Energy to allies");
        c6BurstSim.advanceTime(8.0);
        assertEquals(17, c6BurstWeapon.actions.size(),
                "C6 stationary Glacial Waltz should include the additional icicle");
        assertClose(1.55, c6BurstWeapon.actions.get(0).getDamagePercent(), EPS,
                "C5 should raise Glacial Waltz to its level-12 multiplier");

        RecordingDamageWeapon c6SkillWeapon = new RecordingDamageWeapon("Frostgnaw");
        model.character.Kaeya c6SkillKaeya = new model.character.Kaeya(
                c6SkillWeapon, blankArtifact(), kaeyaTalentData(6));
        CombatSimulator c6SkillSim = simulatorWithExistingCharacter(c6SkillKaeya);
        c6SkillSim.performAction(
                CharacterId.KAEYA, CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertClose(3.82, c6SkillWeapon.actions.get(0).getDamagePercent(), EPS,
                "C3 should raise Frostgnaw to its level-12 multiplier");
    }

    private static void testAccuracyPhaseF_StaticActionBonusWeaponMetadata() {
        model.weapon.TheStringless stringless = new model.weapon.TheStringless();
        assertEquals("The Stringless", stringless.getName(),
                "The Stringless display name");
        assertClose(510.0, stringless.getBaseAtk(), EPS,
                "The Stringless base ATK");
        assertClose(165.0, stringless.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "The Stringless Elemental Mastery");
        assertEquals(model.type.WeaponType.BOW, stringless.getWeaponType(),
                "The Stringless weapon type");
        assertEquals(5, stringless.getRefinement(),
                "The Stringless default refinement");

        StatsContainer r5Stats = new StatsContainer();
        stringless.applyPassive(r5Stats, 0.0);
        assertClose(0.48, r5Stats.get(StatType.SKILL_DMG_BONUS), EPS,
                "R5 The Stringless Skill damage bonus");
        assertClose(0.48, r5Stats.get(StatType.BURST_DMG_BONUS), EPS,
                "R5 The Stringless Burst damage bonus");
        assertClose(0.0, r5Stats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "The Stringless should not modify Normal damage");
        assertClose(0.0, r5Stats.get(StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "The Stringless should not modify Charged damage");

        model.weapon.TheStringless r1Stringless = new model.weapon.TheStringless(1);
        StatsContainer r1Stats = new StatsContainer();
        r1Stringless.applyPassive(r1Stats, 0.0);
        assertClose(0.24, r1Stats.get(StatType.SKILL_DMG_BONUS), EPS,
                "R1 The Stringless Skill damage bonus");
        assertClose(0.24, r1Stats.get(StatType.BURST_DMG_BONUS), EPS,
                "R1 The Stringless Burst damage bonus");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.TheStringless(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected,
                "The Stringless should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.TheStringless(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected,
                "The Stringless should reject refinement six");

        model.weapon.Rust rust = new model.weapon.Rust();
        assertEquals("Rust", rust.getName(), "Rust display name");
        assertClose(510.0, rust.getBaseAtk(), EPS, "Rust base ATK");
        assertClose(0.413, rust.getStats().get(StatType.ATK_PERCENT), EPS,
                "Rust ATK substat");
        assertEquals(model.type.WeaponType.BOW, rust.getWeaponType(),
                "Rust weapon type");
        assertEquals(5, rust.getRefinement(), "Rust default refinement");

        StatsContainer r5RustStats = new StatsContainer();
        rust.applyPassive(r5RustStats, 0.0);
        assertClose(0.80, r5RustStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R5 Rust Normal damage bonus");
        assertClose(-0.10, r5RustStats.get(StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "R5 Rust Charged damage penalty");
        assertClose(0.0, r5RustStats.get(StatType.SKILL_DMG_BONUS), EPS,
                "Rust should not modify Skill damage");
        assertClose(0.0, r5RustStats.get(StatType.BURST_DMG_BONUS), EPS,
                "Rust should not modify Burst damage");

        model.weapon.Rust r1Rust = new model.weapon.Rust(1);
        StatsContainer r1RustStats = new StatsContainer();
        r1Rust.applyPassive(r1RustStats, 0.0);
        assertClose(0.40, r1RustStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R1 Rust Normal damage bonus");
        assertClose(-0.10, r1RustStats.get(StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "R1 Rust Charged damage penalty should not scale with refinement");

        boolean lowRustRefinementRejected = false;
        try {
            new model.weapon.Rust(0);
        } catch (IllegalArgumentException expected) {
            lowRustRefinementRejected = true;
        }
        assertTrue(lowRustRefinementRejected, "Rust should reject refinement zero");

        boolean highRustRefinementRejected = false;
        try {
            new model.weapon.Rust(6);
        } catch (IllegalArgumentException expected) {
            highRustRefinementRejected = true;
        }
        assertTrue(highRustRefinementRejected, "Rust should reject refinement six");

        model.weapon.WhiteTassel whiteTassel = new model.weapon.WhiteTassel();
        assertEquals("White Tassel", whiteTassel.getName(),
                "White Tassel display name");
        assertClose(401.0, whiteTassel.getBaseAtk(), EPS,
                "White Tassel base ATK");
        assertClose(0.234, whiteTassel.getStats().get(StatType.CRIT_RATE), EPS,
                "White Tassel CRIT Rate");
        assertEquals(model.type.WeaponType.POLEARM, whiteTassel.getWeaponType(),
                "White Tassel weapon type");
        assertEquals(5, whiteTassel.getRefinement(),
                "White Tassel default refinement");

        StatsContainer r5WhiteTasselStats = new StatsContainer();
        whiteTassel.applyPassive(r5WhiteTasselStats, 0.0);
        assertClose(0.48,
                r5WhiteTasselStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R5 White Tassel Normal damage bonus");
        assertClose(0.0,
                r5WhiteTasselStats.get(StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "White Tassel should not modify Charged damage");
        assertClose(0.0, r5WhiteTasselStats.get(StatType.SKILL_DMG_BONUS), EPS,
                "White Tassel should not modify Skill damage");
        assertClose(0.0, r5WhiteTasselStats.get(StatType.BURST_DMG_BONUS), EPS,
                "White Tassel should not modify Burst damage");

        model.weapon.WhiteTassel r1WhiteTassel = new model.weapon.WhiteTassel(1);
        StatsContainer r1WhiteTasselStats = new StatsContainer();
        r1WhiteTassel.applyPassive(r1WhiteTasselStats, 0.0);
        assertClose(0.24,
                r1WhiteTasselStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R1 White Tassel Normal damage bonus");

        boolean lowWhiteTasselRefinementRejected = false;
        try {
            new model.weapon.WhiteTassel(0);
        } catch (IllegalArgumentException expected) {
            lowWhiteTasselRefinementRejected = true;
        }
        assertTrue(lowWhiteTasselRefinementRejected,
                "White Tassel should reject refinement zero");

        boolean highWhiteTasselRefinementRejected = false;
        try {
            new model.weapon.WhiteTassel(6);
        } catch (IllegalArgumentException expected) {
            highWhiteTasselRefinementRejected = true;
        }
        assertTrue(highWhiteTasselRefinementRejected,
                "White Tassel should reject refinement six");
    }

    private static void testAccuracyPhaseF_AmberCharacterContract() {
        assertEquals(CharacterId.AMBER, CharacterId.fromName("Amber"),
                "Amber display name should resolve to a typed id");
        assertEquals(CharacterId.AMBER, CharacterId.fromNumericId(10),
                "Amber numeric id should round trip");

        model.character.Amber configured = new model.character.Amber(
                new TestWeapon(), blankArtifact());
        assertClose(9461.0, configured.getBaseStats().get(StatType.BASE_HP), EPS,
                "Amber Lv90 base HP");
        assertClose(223.0, configured.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Amber Lv90 base ATK");
        assertClose(601.0, configured.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Amber Lv90 base DEF");
        assertClose(0.24, configured.getBaseStats().get(StatType.ATK_PERCENT), EPS,
                "Amber Lv90 ascension ATK");

        RecordingDamageWeapon c0ChargedWeapon = new RecordingDamageWeapon("Amber Charged");
        model.character.Amber c0ChargedAmber = new model.character.Amber(
                c0ChargedWeapon, blankArtifact(), amberTalentData(0));
        CombatSimulator c0ChargedSim = simulatorWithExistingCharacter(c0ChargedAmber);
        c0ChargedSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertEquals(1, c0ChargedWeapon.actions.size(),
                "C0 Amber should fire one fully Charged arrow");
        AttackAction c0Charged = c0ChargedWeapon.actions.get(0);
        assertEquals(ActionType.CHARGE, c0Charged.getActionType(),
                "Amber aimed shot should retain Charged typing");
        assertEquals(ICDType.Standard, c0Charged.getICDType(),
                "Amber aimed shot should use shared Charged ICD");
        assertClose(2.0, c0Charged.getGaugeUnits(), EPS,
                "Amber fully Charged aimed shot should apply 2U Pyro");

        RecordingDamageWeapon c1ChargedWeapon = new RecordingDamageWeapon("Amber Charged");
        model.character.Amber c1ChargedAmber = new model.character.Amber(
                c1ChargedWeapon, blankArtifact(), amberTalentData(1));
        CombatSimulator c1ChargedSim = simulatorWithExistingCharacter(c1ChargedAmber);
        c1ChargedSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertEquals(2, c1ChargedWeapon.actions.size(),
                "C1 Amber should fire a second fully Charged arrow");
        assertClose(c1ChargedWeapon.actions.get(0).getDamagePercent() * 0.20,
                c1ChargedWeapon.actions.get(1).getDamagePercent(), EPS,
                "C1 second arrow should deal 20% of the primary multiplier");
        assertEquals(ICDTag.ChargedAttack, c1ChargedWeapon.actions.get(1).getICDTag(),
                "C1 second arrow should share Charged ICD");

        RecordingDamageWeapon c0SkillWeapon = new RecordingDamageWeapon("Baron Bunny Explosion");
        model.character.Amber c0SkillAmber = new model.character.Amber(
                c0SkillWeapon, blankArtifact(), amberTalentData(0));
        CombatSimulator c0SkillSim = simulatorWithExistingCharacter(c0SkillAmber);
        c0SkillAmber.restoreCurrentEnergy(0.0);
        c0SkillSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(0, c0SkillWeapon.actions.size(),
                "Baron Bunny should not explode during its cast animation");
        assertClose(0.0, c0SkillAmber.getTotalParticleEnergy(), EPS,
                "Baron Bunny should not generate particles before exploding");
        c0SkillSim.advanceTime(8.0 - c0SkillSim.getCurrentTime() - 0.001);
        assertEquals(0, c0SkillWeapon.actions.size(),
                "Baron Bunny should remain pending immediately before eight seconds");
        c0SkillSim.advanceTime(0.001);
        assertEquals(1, c0SkillWeapon.actions.size(),
                "Baron Bunny should explode exactly once at eight seconds");
        AttackAction c0Explosion = c0SkillWeapon.actions.get(0);
        assertTrue(c0Explosion.isUseSnapshot(), "Baron Bunny should use its cast snapshot");
        assertEquals(ICDType.None, c0Explosion.getICDType(),
                "Baron Bunny should have no elemental ICD");
        assertClose(2.0, c0Explosion.getGaugeUnits(), EPS,
                "Baron Bunny should apply 2U Pyro");
        assertClose(2.0944, c0Explosion.getDamagePercent(), EPS,
                "C0 Baron Bunny should use talent-9 damage");
        assertClose(12.0, c0SkillAmber.getTotalParticleEnergy(), EPS,
                "Baron Bunny should generate four on-field Pyro particles");

        model.character.Amber c0CooldownAmber = new model.character.Amber(
                new TestWeapon(), blankArtifact(), amberTalentData(0));
        c0CooldownAmber.markSkillUsed(0.0);
        assertTrue(!c0CooldownAmber.canSkill(14.999),
                "C0 Baron Bunny should remain unavailable before fifteen seconds");
        assertTrue(c0CooldownAmber.canSkill(15.0),
                "C0 Baron Bunny should return at fifteen seconds");

        model.character.Amber c4CooldownAmber = new model.character.Amber(
                new TestWeapon(), blankArtifact(), amberTalentData(4));
        c4CooldownAmber.markSkillUsed(0.0);
        assertTrue(c4CooldownAmber.canSkill(0.0),
                "C4 Amber should retain a second Baron Bunny charge");
        c4CooldownAmber.markSkillUsed(0.0);
        assertTrue(!c4CooldownAmber.canSkill(11.999),
                "C4 Baron Bunny charges should remain empty before twelve seconds");
        assertTrue(c4CooldownAmber.canSkill(12.0),
                "C4 Baron Bunny should restore a charge at twelve seconds");

        RecordingDamageWeapon c5SkillWeapon = new RecordingDamageWeapon("Baron Bunny Explosion");
        model.character.Amber c5SkillAmber = new model.character.Amber(
                c5SkillWeapon, blankArtifact(), amberTalentData(5));
        CombatSimulator c5SkillSim = simulatorWithExistingCharacter(c5SkillAmber);
        c5SkillSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.SKILL));
        c5SkillSim.advanceTime(8.0);
        assertClose(2.4640, c5SkillWeapon.actions.get(0).getDamagePercent(), EPS,
                "C5 should raise Baron Bunny to its level-12 multiplier");

        RecordingDamageWeapon c0BurstWeapon = new RecordingDamageWeapon("Fiery Rain Wave");
        model.character.Amber c0BurstAmber = new model.character.Amber(
                c0BurstWeapon, blankArtifact(), amberTalentData(0));
        CombatSimulator c0BurstSim = simulatorWithExistingCharacter(c0BurstAmber);
        c0BurstAmber.restoreCurrentEnergy(40.0);
        c0BurstSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.0, c0BurstAmber.getCurrentEnergy(), EPS,
                "Fiery Rain should consume 40 Energy");
        c0BurstSim.advanceTime(2.0);
        assertEquals(18, c0BurstWeapon.actions.size(),
                "A centered enemy should receive all eighteen Fiery Rain waves");
        AttackAction c0Wave = c0BurstWeapon.actions.get(0);
        assertTrue(c0Wave.isUseSnapshot(), "Fiery Rain should use its cast snapshot");
        assertEquals(ICDType.Standard, c0Wave.getICDType(),
                "Fiery Rain should use standard ICD");
        assertClose(1.0, c0Wave.getGaugeUnits(), EPS,
                "Fiery Rain should apply 1U on eligible waves");
        assertClose(0.10,
                c0Wave.getExtraBonuses().getOrDefault(StatType.BURST_CRIT_RATE, 0.0), EPS,
                "Fiery Rain should receive A1 CRIT Rate");
        assertClose(0.4774, c0Wave.getDamagePercent(), EPS,
                "C0 Fiery Rain should use talent-9 damage");

        RecordingDamageWeapon c6BurstWeapon = new RecordingDamageWeapon("Fiery Rain Wave");
        model.character.Amber c6BurstAmber = new model.character.Amber(
                c6BurstWeapon, blankArtifact(), amberTalentData(6));
        TestCharacter c6BurstAlly = testCharacter(Element.CRYO, CharacterId.KAEYA);
        CombatSimulator c6BurstSim = simulatorWithExistingCharacter(c6BurstAmber);
        c6BurstSim.addCharacter(c6BurstAlly);
        c6BurstAmber.restoreCurrentEnergy(40.0);
        double c6BurstStart = c6BurstSim.getCurrentTime();
        c6BurstSim.performAction(
                CharacterId.AMBER, CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.39, resolvedStat(c6BurstSim, c6BurstAmber, StatType.ATK_PERCENT), EPS,
                "C6 Wildfire should add 15% ATK to Amber");
        assertClose(0.15, resolvedStat(c6BurstSim, c6BurstAlly, StatType.ATK_PERCENT), EPS,
                "C6 Wildfire should add 15% ATK to allies");
        assertClose(0.5617, c6BurstWeapon.actions.get(0).getDamagePercent(), EPS,
                "C3 should raise Fiery Rain to its level-12 multiplier");
        c6BurstSim.advanceTime(c6BurstStart + 9.999 - c6BurstSim.getCurrentTime());
        assertClose(0.15, resolvedStat(c6BurstSim, c6BurstAlly, StatType.ATK_PERCENT), EPS,
                "C6 Wildfire should remain active immediately before ten seconds");
        c6BurstSim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(c6BurstSim, c6BurstAlly, StatType.ATK_PERCENT), EPS,
                "C6 Wildfire should expire at exactly ten seconds");
    }

    private static void testAccuracyPhaseF_LegacyWeaponRefinements() {
        model.weapon.AlleyFlash alleyFlash = new model.weapon.AlleyFlash();
        assertEquals("The Alley Flash", alleyFlash.getName(),
                "The Alley Flash display name");
        assertClose(620.0, alleyFlash.getBaseAtk(), EPS,
                "The Alley Flash base ATK");
        assertClose(55.0, alleyFlash.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "The Alley Flash Elemental Mastery");
        assertClose(0.12, alleyFlash.getStats().get(StatType.DMG_BONUS_ALL), EPS,
                "The Alley Flash default should preserve R1 damage");
        assertEquals(model.type.WeaponType.SWORD, alleyFlash.getWeaponType(),
                "The Alley Flash weapon type");
        assertEquals(1, alleyFlash.getRefinement(),
                "The Alley Flash default refinement");

        model.weapon.AlleyFlash r5AlleyFlash = new model.weapon.AlleyFlash(5);
        assertClose(0.24, r5AlleyFlash.getStats().get(StatType.DMG_BONUS_ALL), EPS,
                "R5 The Alley Flash damage bonus");

        boolean lowAlleyFlashRefinementRejected = false;
        try {
            new model.weapon.AlleyFlash(0);
        } catch (IllegalArgumentException expected) {
            lowAlleyFlashRefinementRejected = true;
        }
        assertTrue(lowAlleyFlashRefinementRejected,
                "The Alley Flash should reject refinement zero");

        boolean highAlleyFlashRefinementRejected = false;
        try {
            new model.weapon.AlleyFlash(6);
        } catch (IllegalArgumentException expected) {
            highAlleyFlashRefinementRejected = true;
        }
        assertTrue(highAlleyFlashRefinementRejected,
                "The Alley Flash should reject refinement six");

        model.weapon.Deathmatch deathmatch = new model.weapon.Deathmatch();
        assertEquals("Deathmatch", deathmatch.getName(), "Deathmatch display name");
        assertClose(454.0, deathmatch.getBaseAtk(), EPS, "Deathmatch base ATK");
        assertClose(0.368, deathmatch.getStats().get(StatType.CRIT_RATE), EPS,
                "Deathmatch CRIT Rate");
        assertEquals(model.type.WeaponType.POLEARM, deathmatch.getWeaponType(),
                "Deathmatch weapon type");
        assertEquals(1, deathmatch.getRefinement(), "Deathmatch default refinement");

        StatsContainer r1SingleStats = new StatsContainer();
        deathmatch.applyPassive(r1SingleStats, 0.0);
        assertClose(0.24, r1SingleStats.get(StatType.ATK_PERCENT), EPS,
                "R1 Deathmatch single-target ATK");
        assertClose(0.0, r1SingleStats.get(StatType.DEF_PERCENT), EPS,
                "Deathmatch single-target branch should not add DEF");
        deathmatch.setSingleTarget(false);
        StatsContainer r1MultiStats = new StatsContainer();
        deathmatch.applyPassive(r1MultiStats, 0.0);
        assertClose(0.16, r1MultiStats.get(StatType.ATK_PERCENT), EPS,
                "R1 Deathmatch multi-target ATK");
        assertClose(0.16, r1MultiStats.get(StatType.DEF_PERCENT), EPS,
                "R1 Deathmatch multi-target DEF");

        model.weapon.Deathmatch r5Deathmatch = new model.weapon.Deathmatch(5);
        StatsContainer r5SingleStats = new StatsContainer();
        r5Deathmatch.applyPassive(r5SingleStats, 0.0);
        assertClose(0.48, r5SingleStats.get(StatType.ATK_PERCENT), EPS,
                "R5 Deathmatch single-target ATK");
        r5Deathmatch.setSingleTarget(false);
        StatsContainer r5MultiStats = new StatsContainer();
        r5Deathmatch.applyPassive(r5MultiStats, 0.0);
        assertClose(0.32, r5MultiStats.get(StatType.ATK_PERCENT), EPS,
                "R5 Deathmatch multi-target ATK");
        assertClose(0.32, r5MultiStats.get(StatType.DEF_PERCENT), EPS,
                "R5 Deathmatch multi-target DEF");

        boolean lowDeathmatchRefinementRejected = false;
        try {
            new model.weapon.Deathmatch(0);
        } catch (IllegalArgumentException expected) {
            lowDeathmatchRefinementRejected = true;
        }
        assertTrue(lowDeathmatchRefinementRejected,
                "Deathmatch should reject refinement zero");

        boolean highDeathmatchRefinementRejected = false;
        try {
            new model.weapon.Deathmatch(6);
        } catch (IllegalArgumentException expected) {
            highDeathmatchRefinementRejected = true;
        }
        assertTrue(highDeathmatchRefinementRejected,
                "Deathmatch should reject refinement six");

        model.weapon.TheCatch theCatch = new model.weapon.TheCatch();
        assertEquals("The Catch", theCatch.getName(), "The Catch display name");
        assertClose(510.0, theCatch.getBaseAtk(), EPS, "The Catch base ATK");
        assertClose(0.459, theCatch.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "The Catch Energy Recharge");
        assertEquals(model.type.WeaponType.POLEARM, theCatch.getWeaponType(),
                "The Catch weapon type");
        assertEquals(5, theCatch.getRefinement(), "The Catch default refinement");

        StatsContainer r5CatchStats = new StatsContainer();
        theCatch.applyPassive(r5CatchStats, 0.0);
        assertClose(0.32, r5CatchStats.get(StatType.BURST_DMG_BONUS), EPS,
                "R5 The Catch Burst damage bonus");
        assertClose(0.12, r5CatchStats.get(StatType.BURST_CRIT_RATE), EPS,
                "R5 The Catch Burst CRIT Rate");
        assertClose(0.0, r5CatchStats.get(StatType.SKILL_DMG_BONUS), EPS,
                "The Catch should not modify Skill damage");
        assertClose(0.0, r5CatchStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "The Catch should not modify Normal damage");

        model.weapon.TheCatch r1Catch = new model.weapon.TheCatch(1);
        StatsContainer r1CatchStats = new StatsContainer();
        r1Catch.applyPassive(r1CatchStats, 0.0);
        assertClose(0.16, r1CatchStats.get(StatType.BURST_DMG_BONUS), EPS,
                "R1 The Catch Burst damage bonus");
        assertClose(0.06, r1CatchStats.get(StatType.BURST_CRIT_RATE), EPS,
                "R1 The Catch Burst CRIT Rate");

        boolean lowCatchRefinementRejected = false;
        try {
            new model.weapon.TheCatch(0);
        } catch (IllegalArgumentException expected) {
            lowCatchRefinementRejected = true;
        }
        assertTrue(lowCatchRefinementRejected,
                "The Catch should reject refinement zero");

        boolean highCatchRefinementRejected = false;
        try {
            new model.weapon.TheCatch(6);
        } catch (IllegalArgumentException expected) {
            highCatchRefinementRejected = true;
        }
        assertTrue(highCatchRefinementRejected,
                "The Catch should reject refinement six");
    }

    private static void testAccuracyPhaseF_SkillUseEventWeapons() {
        model.weapon.OathswornEye oathswornEye = new model.weapon.OathswornEye();
        assertEquals("Oathsworn Eye", oathswornEye.getName(),
                "Oathsworn Eye display name");
        assertClose(565.0, oathswornEye.getBaseAtk(), EPS,
                "Oathsworn Eye base ATK");
        assertClose(0.276, oathswornEye.getStats().get(StatType.ATK_PERCENT), EPS,
                "Oathsworn Eye ATK substat");
        assertEquals(model.type.WeaponType.CATALYST, oathswornEye.getWeaponType(),
                "Oathsworn Eye weapon type");
        assertEquals(5, oathswornEye.getRefinement(),
                "Oathsworn Eye default refinement");

        TestCharacter oathOwner = testCharacter(Element.HYDRO);
        oathOwner.setWeapon(oathswornEye);
        CombatSimulator oathSim = simulatorWith(oathOwner);
        assertClose(1.0, resolvedStat(oathSim, oathOwner, StatType.ENERGY_RECHARGE), EPS,
                "Oathsworn Eye should be inactive before Skill use");
        captureStandardOutput(() -> oathSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.NORMAL)));
        assertClose(1.0, resolvedStat(oathSim, oathOwner, StatType.ENERGY_RECHARGE), EPS,
                "Non-Skill actions should not activate Oathsworn Eye");
        captureStandardOutput(() -> oathSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(1.48, resolvedStat(oathSim, oathOwner, StatType.ENERGY_RECHARGE), EPS,
                "R5 Oathsworn Eye should activate immediately on Skill use");
        oathSim.advanceTime(5.0);
        captureStandardOutput(() -> oathSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        oathSim.advanceTime(9.999);
        assertClose(1.48, resolvedStat(oathSim, oathOwner, StatType.ENERGY_RECHARGE), EPS,
                "Refreshed Oathsworn Eye should remain active before expiry");
        oathSim.advanceTime(0.001);
        assertClose(1.0, resolvedStat(oathSim, oathOwner, StatType.ENERGY_RECHARGE), EPS,
                "Oathsworn Eye should be inactive at exact expiry");

        model.weapon.OathswornEye r1OathswornEye = new model.weapon.OathswornEye(1);
        TestCharacter r1OathOwner = testCharacter(Element.HYDRO);
        r1OathOwner.setWeapon(r1OathswornEye);
        CombatSimulator r1OathSim = simulatorWith(r1OathOwner);
        captureStandardOutput(() -> r1OathSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(1.24,
                resolvedStat(r1OathSim, r1OathOwner, StatType.ENERGY_RECHARGE), EPS,
                "R1 Oathsworn Eye Energy Recharge");

        boolean lowOathswornRefinementRejected = false;
        try {
            new model.weapon.OathswornEye(0);
        } catch (IllegalArgumentException expected) {
            lowOathswornRefinementRejected = true;
        }
        assertTrue(lowOathswornRefinementRejected,
                "Oathsworn Eye should reject refinement zero");

        boolean highOathswornRefinementRejected = false;
        try {
            new model.weapon.OathswornEye(6);
        } catch (IllegalArgumentException expected) {
            highOathswornRefinementRejected = true;
        }
        assertTrue(highOathswornRefinementRejected,
                "Oathsworn Eye should reject refinement six");

        model.weapon.WindblumeOde windblumeOde = new model.weapon.WindblumeOde();
        assertEquals("Windblume Ode", windblumeOde.getName(),
                "Windblume Ode display name");
        assertClose(510.0, windblumeOde.getBaseAtk(), EPS,
                "Windblume Ode base ATK");
        assertClose(165.0, windblumeOde.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Windblume Ode Elemental Mastery");
        assertEquals(model.type.WeaponType.BOW, windblumeOde.getWeaponType(),
                "Windblume Ode weapon type");
        assertEquals(5, windblumeOde.getRefinement(),
                "Windblume Ode default refinement");

        TestCharacter windblumeOwner = testCharacter(Element.ANEMO);
        windblumeOwner.setWeapon(windblumeOde);
        CombatSimulator windblumeSim = simulatorWith(windblumeOwner);
        assertClose(0.0, resolvedStat(windblumeSim, windblumeOwner, StatType.ATK_PERCENT), EPS,
                "Windblume Ode should be inactive before Skill use");
        captureStandardOutput(() -> windblumeSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(0.32,
                resolvedStat(windblumeSim, windblumeOwner, StatType.ATK_PERCENT), EPS,
                "R5 Windblume Ode should activate immediately on Skill use");
        windblumeSim.advanceTime(3.0);
        captureStandardOutput(() -> windblumeSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        windblumeSim.advanceTime(5.999);
        assertClose(0.32,
                resolvedStat(windblumeSim, windblumeOwner, StatType.ATK_PERCENT), EPS,
                "Refreshed Windblume Ode should remain active before expiry");
        windblumeSim.advanceTime(0.001 + 1e-9);
        assertClose(0.0,
                resolvedStat(windblumeSim, windblumeOwner, StatType.ATK_PERCENT), EPS,
                "Windblume Ode should be inactive at exact expiry");

        model.weapon.WindblumeOde r1WindblumeOde = new model.weapon.WindblumeOde(1);
        TestCharacter r1WindblumeOwner = testCharacter(Element.ANEMO);
        r1WindblumeOwner.setWeapon(r1WindblumeOde);
        CombatSimulator r1WindblumeSim = simulatorWith(r1WindblumeOwner);
        captureStandardOutput(() -> r1WindblumeSim.performAction(
                CharacterId.SUCROSE, CharacterActionRequest.of(CharacterActionKey.SKILL)));
        assertClose(0.16,
                resolvedStat(r1WindblumeSim, r1WindblumeOwner, StatType.ATK_PERCENT), EPS,
                "R1 Windblume Ode ATK bonus");

        boolean lowWindblumeRefinementRejected = false;
        try {
            new model.weapon.WindblumeOde(0);
        } catch (IllegalArgumentException expected) {
            lowWindblumeRefinementRejected = true;
        }
        assertTrue(lowWindblumeRefinementRejected,
                "Windblume Ode should reject refinement zero");

        boolean highWindblumeRefinementRejected = false;
        try {
            new model.weapon.WindblumeOde(6);
        } catch (IllegalArgumentException expected) {
            highWindblumeRefinementRejected = true;
        }
        assertTrue(highWindblumeRefinementRejected,
                "Windblume Ode should reject refinement six");

        model.weapon.FesteringDesire festeringDesire = new model.weapon.FesteringDesire();
        assertEquals("Festering Desire", festeringDesire.getName(),
                "Festering Desire display name");
        assertClose(510.0, festeringDesire.getBaseAtk(), EPS,
                "Festering Desire base ATK");
        assertClose(0.459,
                festeringDesire.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Festering Desire Energy Recharge");
        assertEquals(model.type.WeaponType.SWORD, festeringDesire.getWeaponType(),
                "Festering Desire weapon type");
        assertEquals(5, festeringDesire.getRefinement(),
                "Festering Desire default refinement");

        StatsContainer r5FesteringStats = new StatsContainer();
        festeringDesire.applyPassive(r5FesteringStats, 0.0);
        assertClose(0.32, r5FesteringStats.get(StatType.SKILL_DMG_BONUS), EPS,
                "R5 Festering Desire Skill damage bonus");
        assertClose(0.12, r5FesteringStats.get(StatType.SKILL_CRIT_RATE), EPS,
                "R5 Festering Desire Skill CRIT Rate");
        assertClose(0.0, r5FesteringStats.get(StatType.BURST_DMG_BONUS), EPS,
                "Festering Desire should not modify Burst damage");
        assertClose(0.0, r5FesteringStats.get(StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "Festering Desire should not modify Normal damage");

        model.weapon.FesteringDesire r1FesteringDesire =
                new model.weapon.FesteringDesire(1);
        StatsContainer r1FesteringStats = new StatsContainer();
        r1FesteringDesire.applyPassive(r1FesteringStats, 0.0);
        assertClose(0.16, r1FesteringStats.get(StatType.SKILL_DMG_BONUS), EPS,
                "R1 Festering Desire Skill damage bonus");
        assertClose(0.06, r1FesteringStats.get(StatType.SKILL_CRIT_RATE), EPS,
                "R1 Festering Desire Skill CRIT Rate");

        boolean lowFesteringRefinementRejected = false;
        try {
            new model.weapon.FesteringDesire(0);
        } catch (IllegalArgumentException expected) {
            lowFesteringRefinementRejected = true;
        }
        assertTrue(lowFesteringRefinementRejected,
                "Festering Desire should reject refinement zero");

        boolean highFesteringRefinementRejected = false;
        try {
            new model.weapon.FesteringDesire(6);
        } catch (IllegalArgumentException expected) {
            highFesteringRefinementRejected = true;
        }
        assertTrue(highFesteringRefinementRejected,
                "Festering Desire should reject refinement six");
    }

    private static void testAccuracyPhaseF_WatatsumiWavewalkerWeapons() {
        model.weapon.Akuoumaru akuoumaru = new model.weapon.Akuoumaru();
        assertEquals("Akuoumaru", akuoumaru.getName(), "Akuoumaru display name");
        assertClose(510.0, akuoumaru.getBaseAtk(), EPS, "Akuoumaru base ATK");
        assertClose(0.413, akuoumaru.getStats().get(StatType.ATK_PERCENT), EPS,
                "Akuoumaru ATK substat");
        assertEquals(model.type.WeaponType.CLAYMORE, akuoumaru.getWeaponType(),
                "Akuoumaru weapon type");
        assertEquals(5, akuoumaru.getRefinement(), "Akuoumaru default refinement");

        StatsContainer uninitializedStats = new StatsContainer();
        akuoumaru.applyPassive(uninitializedStats, 0.0);
        assertClose(0.0, uninitializedStats.get(StatType.BURST_DMG_BONUS), EPS,
                "Watatsumi Wavewalker should be inactive before simulator initialization");

        TestCharacter owner = testCharacter(Element.HYDRO, CharacterId.SUCROSE);
        owner.setWeapon(akuoumaru);
        CombatSimulator sim = simulatorWith(owner);
        sim.addCharacter(testCharacter(Element.PYRO, CharacterId.XIANGLING));
        sim.addCharacter(testCharacter(Element.HYDRO, CharacterId.XINGQIU));
        sim.addCharacter(testCharacter(Element.PYRO, CharacterId.BENNETT));
        assertClose(0.576, resolvedStat(sim, owner, StatType.BURST_DMG_BONUS), EPS,
                "R5 Akuoumaru should use all four party members' maximum Energy");
        assertClose(0.576, resolvedStat(sim, owner, StatType.BURST_DMG_BONUS), EPS,
                "Repeated Watatsumi Wavewalker evaluation should not accumulate");
        assertClose(0.0, resolvedStat(sim, owner, StatType.SKILL_DMG_BONUS), EPS,
                "Watatsumi Wavewalker should not change Skill damage");
        assertClose(0.0, resolvedStat(sim, owner, StatType.DMG_BONUS_ALL), EPS,
                "Watatsumi Wavewalker should not change all damage");

        model.weapon.Akuoumaru r1Akuoumaru = new model.weapon.Akuoumaru(1);
        TestCharacter r1Owner = testCharacter(Element.HYDRO);
        r1Owner.setWeapon(r1Akuoumaru);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        assertClose(0.072, resolvedStat(r1Sim, r1Owner, StatType.BURST_DMG_BONUS), EPS,
                "R1 Akuoumaru should grant 0.12% per owner maximum Energy point");
        assertEquals(1, r1Akuoumaru.getRefinement(), "Akuoumaru R1 refinement");

        model.weapon.Akuoumaru cappedAkuoumaru = new model.weapon.Akuoumaru(1);
        TestBurstCharacter cappedOwner = new TestBurstCharacter(400.0);
        cappedOwner.setWeapon(cappedAkuoumaru);
        CombatSimulator cappedSim = simulatorWithExistingCharacter(cappedOwner);
        assertClose(0.40,
                resolvedStat(cappedSim, cappedOwner, StatType.BURST_DMG_BONUS), EPS,
                "R1 Akuoumaru should cap Burst damage at 40%");

        model.weapon.MouunsMoon mouunsMoon = new model.weapon.MouunsMoon();
        assertEquals("Mouun's Moon", mouunsMoon.getName(), "Mouun's Moon display name");
        assertClose(565.0, mouunsMoon.getBaseAtk(), EPS, "Mouun's Moon base ATK");
        assertClose(0.276, mouunsMoon.getStats().get(StatType.ATK_PERCENT), EPS,
                "Mouun's Moon ATK substat");
        assertEquals(model.type.WeaponType.BOW, mouunsMoon.getWeaponType(),
                "Mouun's Moon weapon type");
        assertEquals(5, mouunsMoon.getRefinement(), "Mouun's Moon default refinement");

        TestCharacter mouunOwner = testCharacter(Element.HYDRO);
        mouunOwner.setWeapon(mouunsMoon);
        CombatSimulator mouunSim = simulatorWith(mouunOwner);
        assertClose(0.144,
                resolvedStat(mouunSim, mouunOwner, StatType.BURST_DMG_BONUS), EPS,
                "R5 Mouun's Moon should inherit Watatsumi Wavewalker");

        boolean lowMouunRefinementRejected = false;
        try {
            new model.weapon.MouunsMoon(0);
        } catch (IllegalArgumentException expected) {
            lowMouunRefinementRejected = true;
        }
        assertTrue(lowMouunRefinementRejected,
                "Mouun's Moon should reject refinement zero");

        boolean highMouunRefinementRejected = false;
        try {
            new model.weapon.MouunsMoon(6);
        } catch (IllegalArgumentException expected) {
            highMouunRefinementRejected = true;
        }
        assertTrue(highMouunRefinementRejected,
                "Mouun's Moon should reject refinement six");

        model.weapon.WavebreakersFin wavebreakersFin =
                new model.weapon.WavebreakersFin();
        assertEquals("Wavebreaker's Fin", wavebreakersFin.getName(),
                "Wavebreaker's Fin display name");
        assertClose(620.0, wavebreakersFin.getBaseAtk(), EPS,
                "Wavebreaker's Fin base ATK");
        assertClose(0.138, wavebreakersFin.getStats().get(StatType.ATK_PERCENT), EPS,
                "Wavebreaker's Fin ATK substat");
        assertEquals(model.type.WeaponType.POLEARM, wavebreakersFin.getWeaponType(),
                "Wavebreaker's Fin weapon type");
        assertEquals(5, wavebreakersFin.getRefinement(),
                "Wavebreaker's Fin default refinement");

        TestCharacter finOwner = testCharacter(Element.HYDRO);
        finOwner.setWeapon(wavebreakersFin);
        CombatSimulator finSim = simulatorWith(finOwner);
        assertClose(0.144,
                resolvedStat(finSim, finOwner, StatType.BURST_DMG_BONUS), EPS,
                "R5 Wavebreaker's Fin should inherit Watatsumi Wavewalker");

        boolean lowFinRefinementRejected = false;
        try {
            new model.weapon.WavebreakersFin(0);
        } catch (IllegalArgumentException expected) {
            lowFinRefinementRejected = true;
        }
        assertTrue(lowFinRefinementRejected,
                "Wavebreaker's Fin should reject refinement zero");

        boolean highFinRefinementRejected = false;
        try {
            new model.weapon.WavebreakersFin(6);
        } catch (IllegalArgumentException expected) {
            highFinRefinementRejected = true;
        }
        assertTrue(highFinRefinementRejected,
                "Wavebreaker's Fin should reject refinement six");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.Akuoumaru(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Akuoumaru should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.Akuoumaru(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Akuoumaru should reject refinement six");
    }

    private static void testAccuracyPhaseF_ReciprocalHitWeapons() {
        model.weapon.SolarPearl solarPearl = new model.weapon.SolarPearl();
        assertEquals("Solar Pearl", solarPearl.getName(), "Solar Pearl display name");
        assertClose(510.0, solarPearl.getBaseAtk(), EPS, "Solar Pearl base ATK");
        assertClose(0.276, solarPearl.getStats().get(StatType.CRIT_RATE), EPS,
                "Solar Pearl CRIT Rate");
        assertEquals(model.type.WeaponType.CATALYST, solarPearl.getWeaponType(),
                "Solar Pearl weapon type");
        assertEquals(5, solarPearl.getRefinement(), "Solar Pearl default refinement");

        TestCharacter owner = testCharacter(Element.HYDRO);
        owner.setWeapon(solarPearl);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction normalHit = typedDamageHit(
                "Solar Pearl Normal", ActionType.NORMAL, 1.0);
        AttackAction skillHit = typedDamageHit(
                "Solar Pearl Skill", ActionType.SKILL, 1.0);
        AttackAction chargedHit = typedDamageHit(
                "Solar Pearl Charged", ActionType.CHARGE, 1.0);
        AttackAction zeroNormalHit = typedDamageHit(
                "Solar Pearl zero Normal", ActionType.NORMAL, 0.0);

        assertClose(0.0, resolvedStat(sim, owner, StatType.SKILL_DMG_BONUS), EPS,
                "Solar Pearl should be inactive before a direct hit");
        solarPearl.onDamage(owner, normalHit, 0.0, sim);
        assertClose(0.40, resolvedStat(sim, owner, StatType.SKILL_DMG_BONUS), EPS,
                "R5 Solar Pearl Normal hit should enable Skill damage");
        assertClose(0.40, resolvedStat(sim, owner, StatType.BURST_DMG_BONUS), EPS,
                "R5 Solar Pearl Normal hit should enable Burst damage");
        assertClose(0.0,
                resolvedStat(sim, owner, StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "Normal hit should not enable its own Solar Pearl bonus");

        solarPearl.onDamage(owner, skillHit, 1.0, sim);
        assertClose(0.40,
                resolvedStat(sim, owner, StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "Solar Pearl Skill hit should enable Normal damage");
        solarPearl.onDamage(owner, normalHit, 5.0, sim);
        assertClose(0.40, effectiveStatAt(owner, StatType.SKILL_DMG_BONUS, 6.999), EPS,
                "Refreshed Skill window should remain active after six seconds");
        assertClose(0.40,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 6.999), EPS,
                "Independent Normal window should remain active before expiry");
        assertClose(0.0,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 7.0), EPS,
                "Independent Normal window should expire exactly six seconds later");
        assertClose(0.40, effectiveStatAt(owner, StatType.SKILL_DMG_BONUS, 10.999), EPS,
                "Refreshed Skill window should remain active before expiry");
        assertClose(0.0, effectiveStatAt(owner, StatType.SKILL_DMG_BONUS, 11.0), EPS,
                "Refreshed Skill window should expire exactly");

        solarPearl.onDamage(owner, chargedHit, 12.0, sim);
        solarPearl.onDamage(owner, zeroNormalHit, 12.0, sim);
        assertClose(0.0, effectiveStatAt(owner, StatType.SKILL_DMG_BONUS, 12.0), EPS,
                "Charged and zero-damage hits should not activate Solar Pearl");

        model.weapon.SolarPearl r1SolarPearl = new model.weapon.SolarPearl(1);
        assertEquals(1, r1SolarPearl.getRefinement(), "Solar Pearl R1 refinement");
        TestCharacter r1Owner = testCharacter(Element.HYDRO);
        r1Owner.setWeapon(r1SolarPearl);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1SolarPearl.onDamage(r1Owner, normalHit, 0.0, r1Sim);
        r1SolarPearl.onDamage(r1Owner, skillHit, 0.0, r1Sim);
        assertClose(0.20, resolvedStat(r1Sim, r1Owner, StatType.SKILL_DMG_BONUS), EPS,
                "R1 Solar Pearl Skill damage bonus");
        assertClose(0.20,
                resolvedStat(r1Sim, r1Owner, StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R1 Solar Pearl Normal damage bonus");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.SolarPearl(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Solar Pearl should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.SolarPearl(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Solar Pearl should reject refinement six");

        model.weapon.MitternachtsWaltz mitternachtsWaltz =
                new model.weapon.MitternachtsWaltz();
        assertEquals("Mitternachts Waltz", mitternachtsWaltz.getName(),
                "Mitternachts Waltz display name");
        assertClose(510.0, mitternachtsWaltz.getBaseAtk(), EPS,
                "Mitternachts Waltz base ATK");
        assertClose(0.517,
                mitternachtsWaltz.getStats().get(StatType.PHYSICAL_DMG_BONUS), EPS,
                "Mitternachts Waltz Physical DMG Bonus");
        assertEquals(model.type.WeaponType.BOW, mitternachtsWaltz.getWeaponType(),
                "Mitternachts Waltz weapon type");
        assertEquals(5, mitternachtsWaltz.getRefinement(),
                "Mitternachts Waltz default refinement");

        TestCharacter waltzOwner = testCharacter(Element.ELECTRO);
        waltzOwner.setWeapon(mitternachtsWaltz);
        CombatSimulator waltzSim = simulatorWith(waltzOwner);
        mitternachtsWaltz.onDamage(waltzOwner, normalHit, 0.0, waltzSim);
        assertClose(0.40,
                resolvedStat(waltzSim, waltzOwner, StatType.SKILL_DMG_BONUS), EPS,
                "R5 Mitternachts Waltz Normal hit should enable Skill damage");
        assertClose(0.0,
                resolvedStat(waltzSim, waltzOwner, StatType.BURST_DMG_BONUS), EPS,
                "Mitternachts Waltz Normal hit should not enable Burst damage");
        mitternachtsWaltz.onDamage(waltzOwner, skillHit, 0.0, waltzSim);
        assertClose(0.40,
                resolvedStat(waltzSim, waltzOwner, StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R5 Mitternachts Waltz Skill hit should enable Normal damage");
        assertClose(0.40,
                effectiveStatAt(waltzOwner, StatType.SKILL_DMG_BONUS, 4.999), EPS,
                "Mitternachts Waltz should remain active before five seconds");
        assertClose(0.0,
                effectiveStatAt(waltzOwner, StatType.SKILL_DMG_BONUS, 5.0), EPS,
                "Mitternachts Waltz should expire at exactly five seconds");

        AttackAction burstHit = typedDamageHit(
                "Mitternachts Waltz Burst", ActionType.BURST, 1.0);
        mitternachtsWaltz.onDamage(waltzOwner, burstHit, 6.0, waltzSim);
        mitternachtsWaltz.onDamage(waltzOwner, chargedHit, 6.0, waltzSim);
        assertClose(0.0,
                effectiveStatAt(waltzOwner, StatType.NORMAL_ATTACK_DMG_BONUS, 6.0), EPS,
                "Burst and Charged hits should not activate Mitternachts Waltz");

        model.weapon.MitternachtsWaltz r1Waltz =
                new model.weapon.MitternachtsWaltz(1);
        TestCharacter r1WaltzOwner = testCharacter(Element.ELECTRO);
        r1WaltzOwner.setWeapon(r1Waltz);
        CombatSimulator r1WaltzSim = simulatorWith(r1WaltzOwner);
        r1Waltz.onDamage(r1WaltzOwner, normalHit, 0.0, r1WaltzSim);
        assertClose(0.20,
                resolvedStat(r1WaltzSim, r1WaltzOwner, StatType.SKILL_DMG_BONUS), EPS,
                "R1 Mitternachts Waltz Skill damage bonus");

        boolean lowWaltzRefinementRejected = false;
        try {
            new model.weapon.MitternachtsWaltz(0);
        } catch (IllegalArgumentException expected) {
            lowWaltzRefinementRejected = true;
        }
        assertTrue(lowWaltzRefinementRejected,
                "Mitternachts Waltz should reject refinement zero");

        boolean highWaltzRefinementRejected = false;
        try {
            new model.weapon.MitternachtsWaltz(6);
        } catch (IllegalArgumentException expected) {
            highWaltzRefinementRejected = true;
        }
        assertTrue(highWaltzRefinementRejected,
                "Mitternachts Waltz should reject refinement six");

        model.weapon.DodocoTales dodocoTales = new model.weapon.DodocoTales();
        assertEquals("Dodoco Tales", dodocoTales.getName(),
                "Dodoco Tales display name");
        assertClose(454.0, dodocoTales.getBaseAtk(), EPS, "Dodoco Tales base ATK");
        assertClose(0.551, dodocoTales.getStats().get(StatType.ATK_PERCENT), EPS,
                "Dodoco Tales ATK substat");
        assertEquals(model.type.WeaponType.CATALYST, dodocoTales.getWeaponType(),
                "Dodoco Tales weapon type");
        assertEquals(5, dodocoTales.getRefinement(),
                "Dodoco Tales default refinement");

        TestCharacter dodocoOwner = testCharacter(Element.PYRO);
        dodocoOwner.setWeapon(dodocoTales);
        CombatSimulator dodocoSim = simulatorWith(dodocoOwner);
        dodocoTales.onDamage(dodocoOwner, normalHit, 0.0, dodocoSim);
        assertClose(0.32,
                resolvedStat(dodocoSim, dodocoOwner, StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "R5 Dodoco Tales Normal hit should enable Charged damage");
        assertClose(0.551,
                resolvedStat(dodocoSim, dodocoOwner, StatType.ATK_PERCENT), EPS,
                "Normal hit should not enable Dodoco Tales ATK");
        dodocoTales.onDamage(dodocoOwner, chargedHit, 0.0, dodocoSim);
        assertClose(0.711,
                resolvedStat(dodocoSim, dodocoOwner, StatType.ATK_PERCENT), EPS,
                "R5 Dodoco Tales Charged hit should add 16% ATK");
        assertClose(0.32,
                effectiveStatAt(dodocoOwner, StatType.CHARGED_ATTACK_DMG_BONUS, 5.999), EPS,
                "Dodoco Tales Charged damage should remain active before expiry");
        assertClose(0.0,
                effectiveStatAt(dodocoOwner, StatType.CHARGED_ATTACK_DMG_BONUS, 6.0), EPS,
                "Dodoco Tales Charged damage should expire exactly");
        assertClose(0.551, effectiveStatAt(dodocoOwner, StatType.ATK_PERCENT, 6.0), EPS,
                "Dodoco Tales ATK window should expire exactly");

        dodocoTales.onDamage(dodocoOwner, skillHit, 7.0, dodocoSim);
        assertClose(0.0,
                effectiveStatAt(dodocoOwner, StatType.CHARGED_ATTACK_DMG_BONUS, 7.0), EPS,
                "Skill hits should not activate Dodoco Tales");

        model.weapon.DodocoTales r1DodocoTales = new model.weapon.DodocoTales(1);
        TestCharacter r1DodocoOwner = testCharacter(Element.PYRO);
        r1DodocoOwner.setWeapon(r1DodocoTales);
        CombatSimulator r1DodocoSim = simulatorWith(r1DodocoOwner);
        r1DodocoTales.onDamage(r1DodocoOwner, normalHit, 0.0, r1DodocoSim);
        r1DodocoTales.onDamage(r1DodocoOwner, chargedHit, 0.0, r1DodocoSim);
        assertClose(0.16,
                resolvedStat(
                        r1DodocoSim,
                        r1DodocoOwner,
                        StatType.CHARGED_ATTACK_DMG_BONUS),
                EPS,
                "R1 Dodoco Tales Charged damage bonus");
        assertClose(0.631,
                resolvedStat(r1DodocoSim, r1DodocoOwner, StatType.ATK_PERCENT), EPS,
                "R1 Dodoco Tales ATK bonus");

        boolean lowDodocoRefinementRejected = false;
        try {
            new model.weapon.DodocoTales(0);
        } catch (IllegalArgumentException expected) {
            lowDodocoRefinementRejected = true;
        }
        assertTrue(lowDodocoRefinementRejected,
                "Dodoco Tales should reject refinement zero");

        boolean highDodocoRefinementRejected = false;
        try {
            new model.weapon.DodocoTales(6);
        } catch (IllegalArgumentException expected) {
            highDodocoRefinementRejected = true;
        }
        assertTrue(highDodocoRefinementRejected,
                "Dodoco Tales should reject refinement six");
    }

    private static void testAccuracyPhaseF_ReactionWindowWeapons() {
        model.weapon.MappaMare mappaMare = new model.weapon.MappaMare();
        assertEquals("Mappa Mare", mappaMare.getName(), "Mappa Mare display name");
        assertClose(565.0, mappaMare.getBaseAtk(), EPS, "Mappa Mare base ATK");
        assertClose(110.0, mappaMare.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Mappa Mare Elemental Mastery");
        assertEquals(model.type.WeaponType.CATALYST, mappaMare.getWeaponType(),
                "Mappa Mare weapon type");
        assertEquals(5, mappaMare.getRefinement(), "Mappa Mare default refinement");

        TestCharacter owner = testCharacter(Element.ANEMO);
        owner.setWeapon(mappaMare);
        CombatSimulator sim = simulatorWith(owner);
        ReactionResult overload = ReactionResult.transform(
                0.0, "Overloaded", ReactionResult.Kind.OVERLOAD);
        sim.notifyReaction(ReactionResult.none(), owner);
        assertClose(0.0, resolvedStat(sim, owner, StatType.ANEMO_DMG_BONUS), EPS,
                "Mappa Mare should ignore a no-reaction event");

        sim.notifyReaction(overload, owner);
        for (Element element : Element.values()) {
            double expected = element == Element.PHYSICAL ? 0.0 : 0.16;
            assertClose(expected,
                    resolvedStat(sim, owner, element.getBonusStatType()), EPS,
                    "Mappa Mare first stack for " + element);
        }
        sim.notifyReaction(overload, owner);
        sim.notifyReaction(overload, owner);
        assertClose(0.32, resolvedStat(sim, owner, StatType.ANEMO_DMG_BONUS), EPS,
                "Mappa Mare should cap at two R5 stacks");

        sim.advanceTime(9.0);
        sim.notifyReaction(overload, owner);
        sim.advanceTime(9.999);
        assertClose(0.32, resolvedStat(sim, owner, StatType.ANEMO_DMG_BONUS), EPS,
                "Mappa Mare should refresh both stacks before shared expiry");
        sim.advanceTime(0.001 + 1e-9);
        assertClose(0.0, resolvedStat(sim, owner, StatType.ANEMO_DMG_BONUS), EPS,
                "Mappa Mare should expire both stacks at exactly ten seconds");

        model.weapon.MappaMare r1MappaMare = new model.weapon.MappaMare(1);
        TestCharacter r1Owner = testCharacter(Element.ANEMO);
        r1Owner.setWeapon(r1MappaMare);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1Sim.notifyReaction(overload, r1Owner);
        assertClose(0.08, resolvedStat(r1Sim, r1Owner, StatType.ANEMO_DMG_BONUS), EPS,
                "R1 Mappa Mare first stack");

        TestCharacter allySource = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        r1Sim.addCharacter(allySource);
        r1Sim.notifyReaction(overload, allySource);
        assertClose(0.08, resolvedStat(r1Sim, r1Owner, StatType.ANEMO_DMG_BONUS), EPS,
                "An ally reaction should not add a Mappa Mare stack");

        model.weapon.MappaMare offFieldMappaMare = new model.weapon.MappaMare();
        TestCharacter activeAlly = testCharacter(Element.PYRO, CharacterId.SUCROSE);
        CombatSimulator offFieldSim = simulatorWith(activeAlly);
        TestCharacter offFieldOwner = testCharacter(Element.ANEMO, CharacterId.XIANGLING);
        offFieldOwner.setWeapon(offFieldMappaMare);
        offFieldSim.addCharacter(offFieldOwner);
        offFieldSim.notifyReaction(overload, offFieldOwner);
        assertClose(0.0,
                resolvedStat(offFieldSim, offFieldOwner, StatType.ANEMO_DMG_BONUS), EPS,
                "An off-field owner reaction should not activate Mappa Mare");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.MappaMare(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Mappa Mare should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.MappaMare(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Mappa Mare should reject refinement six");

        model.weapon.EmeraldOrb emeraldOrb = new model.weapon.EmeraldOrb();
        assertEquals("Emerald Orb", emeraldOrb.getName(), "Emerald Orb display name");
        assertClose(448.0, emeraldOrb.getBaseAtk(), EPS, "Emerald Orb base ATK");
        assertClose(94.0, emeraldOrb.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Emerald Orb Elemental Mastery");
        assertEquals(model.type.WeaponType.CATALYST, emeraldOrb.getWeaponType(),
                "Emerald Orb weapon type");
        assertEquals(5, emeraldOrb.getRefinement(), "Emerald Orb default refinement");

        ReactionResult[] rapidsReactions = {
                ReactionResult.amp(2.0, "Vaporize", ReactionResult.Kind.VAPORIZE),
                ReactionResult.transform(
                        0.0, "Electro-Charged", ReactionResult.Kind.ELECTRO_CHARGED),
                ReactionResult.state("Frozen", ReactionResult.Kind.FROZEN, Element.CRYO),
                ReactionResult.transform(0.0, "Bloom", ReactionResult.Kind.BLOOM),
                ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED),
                ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM),
                ReactionResult.transform(
                        0.0, "Hydro Swirl", ReactionResult.Kind.SWIRL, Element.HYDRO)
        };
        for (ReactionResult reaction : rapidsReactions) {
            assertReactionWindowBonus(
                    new model.weapon.EmeraldOrb(), reaction,
                    StatType.ATK_PERCENT, 0.40,
                    "Emerald Orb " + reaction.getKind());
        }
        assertReactionWindowBonus(
                new model.weapon.EmeraldOrb(),
                ReactionResult.amp(1.5, "Melt", ReactionResult.Kind.MELT),
                StatType.ATK_PERCENT, 0.0,
                "Emerald Orb Melt exclusion");
        assertReactionWindowBonus(
                new model.weapon.EmeraldOrb(),
                ReactionResult.transform(
                        0.0, "Pyro Swirl", ReactionResult.Kind.SWIRL, Element.PYRO),
                StatType.ATK_PERCENT, 0.0,
                "Emerald Orb Pyro Swirl exclusion");

        model.weapon.EmeraldOrb expiringOrb = new model.weapon.EmeraldOrb();
        TestCharacter orbOwner = testCharacter(Element.HYDRO);
        orbOwner.setWeapon(expiringOrb);
        CombatSimulator orbSim = simulatorWith(orbOwner);
        orbSim.notifyReaction(rapidsReactions[0], orbOwner);
        orbSim.advanceTime(11.999);
        assertClose(0.40, resolvedStat(orbSim, orbOwner, StatType.ATK_PERCENT), EPS,
                "Emerald Orb should remain active before twelve seconds");
        orbSim.advanceTime(0.001 + 1e-9);
        assertClose(0.0, resolvedStat(orbSim, orbOwner, StatType.ATK_PERCENT), EPS,
                "Emerald Orb should expire at exactly twelve seconds");

        model.weapon.EmeraldOrb r1EmeraldOrb = new model.weapon.EmeraldOrb(1);
        assertReactionWindowBonus(
                r1EmeraldOrb, rapidsReactions[0],
                StatType.ATK_PERCENT, 0.20, "R1 Emerald Orb");

        boolean lowEmeraldRefinementRejected = false;
        try {
            new model.weapon.EmeraldOrb(0);
        } catch (IllegalArgumentException expected) {
            lowEmeraldRefinementRejected = true;
        }
        assertTrue(lowEmeraldRefinementRejected,
                "Emerald Orb should reject refinement zero");

        boolean highEmeraldRefinementRejected = false;
        try {
            new model.weapon.EmeraldOrb(6);
        } catch (IllegalArgumentException expected) {
            highEmeraldRefinementRejected = true;
        }
        assertTrue(highEmeraldRefinementRejected,
                "Emerald Orb should reject refinement six");

        model.weapon.DarkIronSword darkIronSword = new model.weapon.DarkIronSword();
        assertEquals("Dark Iron Sword", darkIronSword.getName(),
                "Dark Iron Sword display name");
        assertClose(401.0, darkIronSword.getBaseAtk(), EPS,
                "Dark Iron Sword base ATK");
        assertClose(141.0,
                darkIronSword.getStats().get(StatType.ELEMENTAL_MASTERY), EPS,
                "Dark Iron Sword Elemental Mastery");
        assertEquals(model.type.WeaponType.SWORD, darkIronSword.getWeaponType(),
                "Dark Iron Sword weapon type");
        assertEquals(1, darkIronSword.getRefinement(),
                "Dark Iron Sword fixed refinement");

        ReactionResult[] overloadedReactions = {
                ReactionResult.transform(0.0, "Overload", ReactionResult.Kind.OVERLOAD),
                ReactionResult.transform(0.0, "Overloaded", ReactionResult.Kind.OVERLOADED),
                ReactionResult.transform(
                        0.0, "Superconduct", ReactionResult.Kind.SUPERCONDUCT),
                ReactionResult.transform(
                        0.0, "Electro-Charged", ReactionResult.Kind.ELECTRO_CHARGED),
                ReactionResult.state("Quicken", ReactionResult.Kind.QUICKEN, Element.DENDRO),
                ReactionResult.additive(
                        0.0, "Aggravate", ReactionResult.Kind.AGGRAVATE, Element.ELECTRO),
                ReactionResult.transform(
                        0.0, "Hyperbloom", ReactionResult.Kind.HYPERBLOOM),
                ReactionResult.lunar(0.0, ReactionResult.LunarType.CHARGED),
                ReactionResult.transform(
                        0.0, "Electro Swirl", ReactionResult.Kind.SWIRL, Element.ELECTRO)
        };
        for (ReactionResult reaction : overloadedReactions) {
            assertReactionWindowBonus(
                    new model.weapon.DarkIronSword(), reaction,
                    StatType.ATK_PERCENT, 0.20,
                    "Dark Iron Sword " + reaction.getKind());
        }
        assertReactionWindowBonus(
                new model.weapon.DarkIronSword(),
                ReactionResult.transform(0.0, "Bloom", ReactionResult.Kind.BLOOM),
                StatType.ATK_PERCENT, 0.0,
                "Dark Iron Sword Bloom exclusion");
        assertReactionWindowBonus(
                new model.weapon.DarkIronSword(),
                ReactionResult.transform(
                        0.0, "Hydro Swirl", ReactionResult.Kind.SWIRL, Element.HYDRO),
                StatType.ATK_PERCENT, 0.0,
                "Dark Iron Sword Hydro Swirl exclusion");

        model.weapon.DarkIronSword expiringSword = new model.weapon.DarkIronSword();
        TestCharacter swordOwner = testCharacter(Element.ELECTRO);
        swordOwner.setWeapon(expiringSword);
        CombatSimulator swordSim = simulatorWith(swordOwner);
        swordSim.notifyReaction(overloadedReactions[0], swordOwner);
        swordSim.advanceTime(11.999);
        assertClose(0.20, resolvedStat(swordSim, swordOwner, StatType.ATK_PERCENT), EPS,
                "Dark Iron Sword should remain active before twelve seconds");
        swordSim.advanceTime(0.001 + 1e-9);
        assertClose(0.0, resolvedStat(swordSim, swordOwner, StatType.ATK_PERCENT), EPS,
                "Dark Iron Sword should expire at exactly twelve seconds");
    }

    private static void testAccuracyPhaseF_HitStackWeapons() {
        model.weapon.BalladOfTheBoundlessBlue ballad =
                new model.weapon.BalladOfTheBoundlessBlue();
        assertEquals("Ballad of the Boundless Blue", ballad.getName(),
                "Ballad display name");
        assertClose(565.0, ballad.getBaseAtk(), EPS, "Ballad base ATK");
        assertClose(0.306, ballad.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Ballad Energy Recharge");
        assertEquals(model.type.WeaponType.CATALYST, ballad.getWeaponType(),
                "Ballad weapon type");
        assertEquals(5, ballad.getRefinement(), "Ballad default refinement");

        TestCharacter owner = testCharacter(Element.HYDRO);
        owner.setWeapon(ballad);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction normalHit = typedDamageHit("Ballad Normal", ActionType.NORMAL, 1.0);
        AttackAction skillHit = typedDamageHit("Ballad Skill", ActionType.SKILL, 1.0);
        AttackAction zeroChargeHit = typedDamageHit("Ballad zero Charge", ActionType.CHARGE, 0.0);

        ballad.onDamage(owner, normalHit, 0.0, sim);
        assertClose(0.16,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 0.0), EPS,
                "Ballad first R5 Normal stack");
        assertClose(0.12,
                effectiveStatAt(owner, StatType.CHARGED_ATTACK_DMG_BONUS, 0.0), EPS,
                "Ballad first R5 Charged stack");
        ballad.onDamage(owner, normalHit, 0.299, sim);
        assertClose(0.16,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 0.299), EPS,
                "Ballad should reject a hit immediately before CT");
        ballad.onDamage(owner, normalHit, 0.3, sim);
        ballad.onDamage(owner, normalHit, 0.6, sim);
        assertClose(0.48,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 0.6), EPS,
                "Ballad should allow a stack at exact CT and cap at three");
        assertClose(0.36,
                effectiveStatAt(owner, StatType.CHARGED_ATTACK_DMG_BONUS, 0.6), EPS,
                "Ballad should scale unequal Charged bonuses across three stacks");

        ballad.onDamage(owner, normalHit, 0.9, sim);
        ballad.onDamage(owner, skillHit, 1.2, sim);
        ballad.onDamage(owner, zeroChargeHit, 1.2, sim);
        assertClose(0.48,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 6.899), EPS,
                "Ballad cap refresh should preserve all stacks before expiry");
        assertClose(0.0,
                effectiveStatAt(owner, StatType.NORMAL_ATTACK_DMG_BONUS, 6.9), EPS,
                "Ballad should expire all stacks exactly");

        model.weapon.BalladOfTheBoundlessBlue persistentBallad =
                new model.weapon.BalladOfTheBoundlessBlue();
        TestCharacter persistentOwner = testCharacter(Element.HYDRO, CharacterId.SUCROSE);
        persistentOwner.setWeapon(persistentBallad);
        CombatSimulator persistentSim = simulatorWith(persistentOwner);
        persistentBallad.onDamage(persistentOwner, normalHit, 0.0, persistentSim);
        persistentSim.addCharacter(testCharacter(Element.PYRO, CharacterId.XIANGLING));
        persistentSim.setActiveCharacter(CharacterId.XIANGLING);
        assertClose(0.16,
                effectiveStatAt(
                        persistentOwner, StatType.NORMAL_ATTACK_DMG_BONUS, 1.0), EPS,
                "Ballad stacks should persist while the owner is off-field");

        model.weapon.BalladOfTheBoundlessBlue r1Ballad =
                new model.weapon.BalladOfTheBoundlessBlue(1);
        TestCharacter r1Owner = testCharacter(Element.HYDRO);
        r1Owner.setWeapon(r1Ballad);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1Ballad.onDamage(r1Owner, normalHit, 0.0, r1Sim);
        assertClose(0.08,
                resolvedStat(r1Sim, r1Owner, StatType.NORMAL_ATTACK_DMG_BONUS), EPS,
                "R1 Ballad Normal bonus");
        assertClose(0.06,
                resolvedStat(r1Sim, r1Owner, StatType.CHARGED_ATTACK_DMG_BONUS), EPS,
                "R1 Ballad Charged bonus");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.BalladOfTheBoundlessBlue(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected, "Ballad should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.BalladOfTheBoundlessBlue(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected, "Ballad should reject refinement six");

        model.weapon.CompoundBow compoundBow = new model.weapon.CompoundBow();
        assertEquals("Compound Bow", compoundBow.getName(),
                "Compound Bow display name");
        assertClose(454.0, compoundBow.getBaseAtk(), EPS, "Compound Bow base ATK");
        assertClose(0.690,
                compoundBow.getStats().get(StatType.PHYSICAL_DMG_BONUS), EPS,
                "Compound Bow Physical DMG Bonus");
        assertEquals(model.type.WeaponType.BOW, compoundBow.getWeaponType(),
                "Compound Bow weapon type");
        assertEquals(5, compoundBow.getRefinement(),
                "Compound Bow default refinement");

        TestCharacter compoundOwner = testCharacter(Element.PHYSICAL);
        compoundOwner.setWeapon(compoundBow);
        CombatSimulator compoundSim = simulatorWith(compoundOwner);
        AttackAction chargeHit = typedDamageHit(
                "Compound Bow Charge", ActionType.CHARGE, 1.0);
        compoundBow.onDamage(compoundOwner, normalHit, 0.0, compoundSim);
        compoundBow.onDamage(compoundOwner, chargeHit, 0.3, compoundSim);
        compoundBow.onDamage(compoundOwner, normalHit, 0.6, compoundSim);
        compoundBow.onDamage(compoundOwner, chargeHit, 0.9, compoundSim);
        assertClose(0.32,
                effectiveStatAt(compoundOwner, StatType.ATK_PERCENT, 0.9), EPS,
                "R5 Compound Bow should cap at four ATK stacks");
        assertClose(0.096,
                effectiveStatAt(compoundOwner, StatType.ATK_SPD, 0.9), EPS,
                "R5 Compound Bow should cap at four Normal SPD stacks");
        compoundBow.onDamage(compoundOwner, skillHit, 1.2, compoundSim);
        assertClose(0.32,
                effectiveStatAt(compoundOwner, StatType.ATK_PERCENT, 6.899), EPS,
                "Skill hits should not refresh Compound Bow");
        assertClose(0.0,
                effectiveStatAt(compoundOwner, StatType.ATK_PERCENT, 6.9), EPS,
                "Compound Bow should expire all stacks exactly");

        model.weapon.CompoundBow r1CompoundBow = new model.weapon.CompoundBow(1);
        TestCharacter r1CompoundOwner = testCharacter(Element.PHYSICAL);
        r1CompoundOwner.setWeapon(r1CompoundBow);
        CombatSimulator r1CompoundSim = simulatorWith(r1CompoundOwner);
        r1CompoundBow.onDamage(r1CompoundOwner, normalHit, 0.0, r1CompoundSim);
        assertClose(0.04,
                resolvedStat(r1CompoundSim, r1CompoundOwner, StatType.ATK_PERCENT), EPS,
                "R1 Compound Bow ATK stack");
        assertClose(0.012,
                resolvedStat(r1CompoundSim, r1CompoundOwner, StatType.ATK_SPD), EPS,
                "R1 Compound Bow Normal SPD stack");

        boolean lowCompoundRefinementRejected = false;
        try {
            new model.weapon.CompoundBow(0);
        } catch (IllegalArgumentException expected) {
            lowCompoundRefinementRejected = true;
        }
        assertTrue(lowCompoundRefinementRejected,
                "Compound Bow should reject refinement zero");

        boolean highCompoundRefinementRejected = false;
        try {
            new model.weapon.CompoundBow(6);
        } catch (IllegalArgumentException expected) {
            highCompoundRefinementRejected = true;
        }
        assertTrue(highCompoundRefinementRejected,
                "Compound Bow should reject refinement six");

        model.weapon.IbisPiercer ibisPiercer = new model.weapon.IbisPiercer();
        assertEquals("Ibis Piercer", ibisPiercer.getName(),
                "Ibis Piercer display name");
        assertClose(565.0, ibisPiercer.getBaseAtk(), EPS, "Ibis Piercer base ATK");
        assertClose(0.276, ibisPiercer.getStats().get(StatType.ATK_PERCENT), EPS,
                "Ibis Piercer ATK substat");
        assertEquals(model.type.WeaponType.BOW, ibisPiercer.getWeaponType(),
                "Ibis Piercer weapon type");
        assertEquals(5, ibisPiercer.getRefinement(),
                "Ibis Piercer default refinement");

        TestCharacter ibisOwner = testCharacter(Element.CRYO);
        ibisOwner.setWeapon(ibisPiercer);
        CombatSimulator ibisSim = simulatorWith(ibisOwner);
        ibisPiercer.onDamage(ibisOwner, chargeHit, 0.0, ibisSim);
        assertClose(80.0,
                effectiveStatAt(ibisOwner, StatType.ELEMENTAL_MASTERY, 0.0), EPS,
                "R5 Ibis Piercer first stack");
        ibisPiercer.onDamage(ibisOwner, chargeHit, 0.499, ibisSim);
        assertClose(80.0,
                effectiveStatAt(ibisOwner, StatType.ELEMENTAL_MASTERY, 0.499), EPS,
                "Ibis Piercer should reject a hit immediately before CT");
        ibisPiercer.onDamage(ibisOwner, chargeHit, 0.5, ibisSim);
        assertClose(160.0,
                effectiveStatAt(ibisOwner, StatType.ELEMENTAL_MASTERY, 0.5), EPS,
                "Ibis Piercer should allow its second stack at exact CT");
        ibisPiercer.onDamage(ibisOwner, chargeHit, 1.0, ibisSim);
        ibisPiercer.onDamage(ibisOwner, normalHit, 1.5, ibisSim);
        assertClose(160.0,
                effectiveStatAt(ibisOwner, StatType.ELEMENTAL_MASTERY, 6.999), EPS,
                "Ibis Piercer cap refresh should remain active before expiry");
        assertClose(0.0,
                effectiveStatAt(ibisOwner, StatType.ELEMENTAL_MASTERY, 7.0), EPS,
                "Ibis Piercer should expire both stacks exactly");

        model.weapon.IbisPiercer r1IbisPiercer = new model.weapon.IbisPiercer(1);
        TestCharacter r1IbisOwner = testCharacter(Element.CRYO);
        r1IbisOwner.setWeapon(r1IbisPiercer);
        CombatSimulator r1IbisSim = simulatorWith(r1IbisOwner);
        r1IbisPiercer.onDamage(r1IbisOwner, chargeHit, 0.0, r1IbisSim);
        assertClose(40.0,
                resolvedStat(r1IbisSim, r1IbisOwner, StatType.ELEMENTAL_MASTERY), EPS,
                "R1 Ibis Piercer first stack");

        boolean lowIbisRefinementRejected = false;
        try {
            new model.weapon.IbisPiercer(0);
        } catch (IllegalArgumentException expected) {
            lowIbisRefinementRejected = true;
        }
        assertTrue(lowIbisRefinementRejected,
                "Ibis Piercer should reject refinement zero");

        boolean highIbisRefinementRejected = false;
        try {
            new model.weapon.IbisPiercer(6);
        } catch (IllegalArgumentException expected) {
            highIbisRefinementRejected = true;
        }
        assertTrue(highIbisRefinementRejected,
                "Ibis Piercer should reject refinement six");
    }

    private static void testAccuracyPhaseF_ActionUseWindowWeapons() {
        model.weapon.EtherlightSpindlelute etherlight =
                new model.weapon.EtherlightSpindlelute();
        assertEquals("Etherlight Spindlelute", etherlight.getName(),
                "Etherlight Spindlelute display name");
        assertClose(510.0, etherlight.getBaseAtk(), EPS,
                "Etherlight Spindlelute base ATK");
        assertClose(0.459,
                etherlight.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                "Etherlight Spindlelute Energy Recharge");
        assertEquals(model.type.WeaponType.CATALYST, etherlight.getWeaponType(),
                "Etherlight Spindlelute weapon type");
        assertEquals(5, etherlight.getRefinement(),
                "Etherlight Spindlelute default refinement");

        TestCharacter owner = testCharacter(Element.ANEMO);
        owner.setWeapon(etherlight);
        CombatSimulator sim = simulatorWith(owner);
        etherlight.onAction(
                owner, CharacterActionRequest.of(CharacterActionKey.NORMAL), sim);
        assertClose(0.0, resolvedStat(sim, owner, StatType.ELEMENTAL_MASTERY), EPS,
                "Normal use should not activate Etherlight Spindlelute");
        etherlight.onAction(
                owner, CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        assertClose(200.0, resolvedStat(sim, owner, StatType.ELEMENTAL_MASTERY), EPS,
                "R5 Etherlight Spindlelute Skill-use EM");
        sim.advanceTime(10.0);
        etherlight.onAction(
                owner, CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        sim.advanceTime(19.999);
        assertClose(200.0, resolvedStat(sim, owner, StatType.ELEMENTAL_MASTERY), EPS,
                "Refreshed Etherlight Spindlelute should remain active before expiry");
        sim.advanceTime(0.001 + 1e-9);
        assertClose(0.0, resolvedStat(sim, owner, StatType.ELEMENTAL_MASTERY), EPS,
                "Etherlight Spindlelute should expire at exactly twenty seconds");

        model.weapon.EtherlightSpindlelute r1Etherlight =
                new model.weapon.EtherlightSpindlelute(1);
        TestCharacter r1Owner = testCharacter(Element.ANEMO);
        r1Owner.setWeapon(r1Etherlight);
        CombatSimulator r1Sim = simulatorWith(r1Owner);
        r1Etherlight.onAction(
                r1Owner, CharacterActionRequest.of(CharacterActionKey.SKILL), r1Sim);
        assertClose(100.0,
                resolvedStat(r1Sim, r1Owner, StatType.ELEMENTAL_MASTERY), EPS,
                "R1 Etherlight Spindlelute Skill-use EM");

        boolean lowRefinementRejected = false;
        try {
            new model.weapon.EtherlightSpindlelute(0);
        } catch (IllegalArgumentException expected) {
            lowRefinementRejected = true;
        }
        assertTrue(lowRefinementRejected,
                "Etherlight Spindlelute should reject refinement zero");

        boolean highRefinementRejected = false;
        try {
            new model.weapon.EtherlightSpindlelute(6);
        } catch (IllegalArgumentException expected) {
            highRefinementRejected = true;
        }
        assertTrue(highRefinementRejected,
                "Etherlight Spindlelute should reject refinement six");
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
        sim.getEnemy().setFreezeAura(1.0, sim.getCurrentTime());
        assertClose(baseCrit + 0.15, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should grant 15% CRIT Rate against Frozen state");
        double freezeEnd = sim.getEnemy().captureFreezeAuraState().getEndTime();
        sim.advanceTime(freezeEnd - sim.getCurrentTime());
        assertClose(baseCrit, resolvedStat(sim, owner, StatType.CRIT_RATE), EPS,
                "Cryo resonance should be inactive at exact Freeze expiry");

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

    private static double effectiveStatAt(
            Character character,
            StatType statType,
            double currentTime) {
        return character.getEffectiveStats(currentTime).get(statType);
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

    private static void testAccuracyPhaseF_LiveResistanceSnapshotContract() {
        TestCharacter owner = testCharacter(Element.PYRO, CharacterId.XIANGLING)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction snapshotHit = new AttackAction(
                "Snapshot Pyro RES fixture", 1.0, Element.PYRO, StatType.BASE_ATK,
                StatType.PYRO_DMG_BONUS, 0.0, true, ActionType.SKILL);
        snapshotHit.setICD(ICDType.None, ICDTag.None, 0.0);
        owner.captureSnapshot(sim.getCurrentTime(), sim.getTeamBuffs());
        double baseline = calculateDirectDamage(sim, owner, snapshotHit, sim.getCurrentTime(), 1.0);

        sim.applyTeamBuff(new SimpleBuff(
                "Live Pyro RES fixture", BuffId.XIANGLING_GUOBA_C1_SHRED,
                10.0, sim.getCurrentTime(), stats -> stats.add(StatType.PYRO_RES_SHRED, 0.15)));
        double reducedSnapshot = calculateDirectDamage(
                sim, owner, snapshotHit, sim.getCurrentTime(), 1.0);
        assertClose(baseline * (1.025 / 0.90), reducedSnapshot, EPS,
                "A snapshotted Pyro hit should use reduction activated after its snapshot");

        AttackAction liveHit = new AttackAction(
                "Live Pyro RES fixture", 1.0, Element.PYRO, StatType.BASE_ATK,
                StatType.PYRO_DMG_BONUS, 0.0, false, ActionType.SKILL);
        liveHit.setICD(ICDType.None, ICDTag.None, 0.0);
        double reducedLive = calculateDirectDamage(sim, owner, liveHit, sim.getCurrentTime(), 1.0);
        assertClose(reducedLive, reducedSnapshot, EPS,
                "Live and snapshotted hits should share one impact-time RES multiplier");

        sim.applyTeamBuff(new SimpleBuff(
                "Generic RES fixture", 10.0, sim.getCurrentTime(),
                stats -> stats.add(StatType.RES_SHRED, 0.10)));
        sim.applyTeamBuff(new SimpleBuff(
                "Unrelated Hydro RES fixture", 10.0, sim.getCurrentTime(),
                stats -> stats.add(StatType.HYDRO_RES_SHRED, 0.40)));
        double combined = calculateDirectDamage(sim, owner, snapshotHit, sim.getCurrentTime(), 1.0);
        assertClose(baseline * (1.075 / 0.90), combined, EPS,
                "Generic and matching elemental reduction should add once without unrelated elements");

        TestCharacter staleOwner = testCharacter(Element.PYRO, CharacterId.XIANGLING)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0);
        CombatSimulator staleSim = simulatorWith(staleOwner);
        staleSim.applyTeamBuff(new SimpleBuff(
                "Expiring Pyro RES fixture", BuffId.XIANGLING_GUOBA_C1_SHRED,
                10.0, staleSim.getCurrentTime(),
                stats -> stats.add(StatType.PYRO_RES_SHRED, 0.15)));
        staleOwner.captureSnapshot(staleSim.getCurrentTime(), staleSim.getTeamBuffs());
        staleSim.advanceTime(10.0);
        double afterExpiry = calculateDirectDamage(
                staleSim, staleOwner, snapshotHit, staleSim.getCurrentTime(), 1.0);
        assertClose(baseline, afterExpiry, EPS,
                "A snapshotted hit should not retain reduction at its exact expiry");

        TestCharacter lunarOwner = testCharacter(Element.ELECTRO, CharacterId.FLINS)
                .withStat(StatType.CRIT_RATE, 0.0)
                .withStat(StatType.CRIT_DMG, 0.0)
                .asLunar();
        CombatSimulator lunarSim = simulatorWith(lunarOwner);
        AttackAction lunarHit = new AttackAction(
                "Snapshot Lunar RES fixture", 1.0, Element.ELECTRO, StatType.BASE_ATK,
                StatType.ELECTRO_DMG_BONUS, 0.0, true, ActionType.SKILL);
        lunarHit.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);
        lunarHit.setICD(ICDType.None, ICDTag.None, 0.0);
        lunarOwner.captureSnapshot(lunarSim.getCurrentTime(), lunarSim.getTeamBuffs());
        double lunarBaseline = calculateDirectDamage(
                lunarSim, lunarOwner, lunarHit, lunarSim.getCurrentTime(), 1.0);
        lunarSim.applyTeamBuff(new SimpleBuff(
                "Live Electro RES fixture", 10.0, lunarSim.getCurrentTime(),
                stats -> stats.add(StatType.ELECTRO_RES_SHRED, 0.15)));
        double lunarReduced = calculateDirectDamage(
                lunarSim, lunarOwner, lunarHit, lunarSim.getCurrentTime(), 1.0);
        assertClose(lunarBaseline * (1.025 / 0.90), lunarReduced, EPS,
                "A snapshotted Lunar hit should use live matching RES reduction");
    }

    private static void testAccuracyPhaseF_ImmediateReactionLiveResistanceContract() {
        TestCharacter snapshotOwner = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator snapshotSim = simulatorWith(snapshotOwner);
        AttackAction snapshotTrigger = new AttackAction(
                "Snapshot Overload RES fixture", 0.0, Element.PYRO, StatType.BASE_ATK,
                StatType.PYRO_DMG_BONUS, 0.0, true, ActionType.SKILL);
        snapshotTrigger.setICD(ICDType.None, ICDTag.None, 1.0);
        snapshotOwner.captureSnapshot(snapshotSim.getCurrentTime(), snapshotSim.getTeamBuffs());
        snapshotSim.applyTeamBuff(new SimpleBuff(
                "Live reaction Pyro RES fixture", 10.0, snapshotSim.getCurrentTime(),
                stats -> stats.add(StatType.PYRO_RES_SHRED, 0.15)));
        snapshotSim.getEnemy().setAura(Element.ELECTRO, 4.0, snapshotSim.getCurrentTime());
        snapshotSim.performActionWithoutTimeAdvance(CharacterId.XIANGLING, snapshotTrigger);
        assertClose(expectedTransformative(2.75, Element.PYRO, 0.0) * (1.025 / 0.90),
                snapshotSim.getTotalDamage(), 0.5,
                "A snapshotted Overload trigger should use reduction activated after snapshot");

        TestCharacter staleOwner = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator staleSim = simulatorWith(staleOwner);
        staleSim.applyTeamBuff(new SimpleBuff(
                "Stale reaction Pyro RES fixture", 10.0, staleSim.getCurrentTime(),
                stats -> stats.add(StatType.PYRO_RES_SHRED, 0.15)));
        staleOwner.captureSnapshot(staleSim.getCurrentTime(), staleSim.getTeamBuffs());
        staleSim.advanceTime(10.0);
        staleSim.getEnemy().setAura(Element.ELECTRO, 4.0, staleSim.getCurrentTime());
        staleSim.performActionWithoutTimeAdvance(CharacterId.XIANGLING, snapshotTrigger);
        assertClose(expectedTransformative(2.75, Element.PYRO, 0.0),
                staleSim.getTotalDamage(), 0.5,
                "A snapshotted Overload trigger should ignore reduction at exact expiry");

        TestCharacter vvOwner = testCharacter(Element.ANEMO, CharacterId.SUCROSE);
        vvOwner.setArtifacts(new model.artifact.ViridescentVenerer());
        TestCharacter pyroAlly = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator vvSim = simulatorWith(vvOwner);
        vvSim.addCharacter(pyroAlly);
        vvSim.getEnemy().setAura(Element.PYRO, 4.0, vvSim.getCurrentTime());
        vvSim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("First VV Swirl RES fixture", Element.ANEMO));
        double firstSwirlDamage = vvSim.getTotalDamage();
        assertClose(expectedTransformative(0.6, Element.PYRO, 0.0) * 1.60, firstSwirlDamage, 0.5,
                "The first Swirl should not use the VV reduction emitted by that reaction");

        vvSim.advanceTime(0.1);
        vvSim.getEnemy().setAura(Element.PYRO, 4.0, vvSim.getCurrentTime());
        vvSim.performActionWithoutTimeAdvance(
                CharacterId.SUCROSE, reactionHit("Second VV Swirl RES fixture", Element.ANEMO));
        double secondSwirlDamage = vvSim.getTotalDamage() - firstSwirlDamage;
        assertClose(expectedTransformative(0.6, Element.PYRO, 0.0) * 1.60 * (1.15 / 0.90),
                secondSwirlDamage, 0.5,
                "A later Swirl should use the already-active cross-element VV reduction");

        vvSim.getEnemy().setAura(Element.ELECTRO, 4.0, vvSim.getCurrentTime());
        vvSim.performActionWithoutTimeAdvance(
                CharacterId.XIANGLING, reactionHit("Post-VV Overload RES fixture", Element.PYRO));
        double postVvOverload = vvSim.getTotalDamage() - firstSwirlDamage - secondSwirlDamage;
        assertClose(expectedTransformative(2.75, Element.PYRO, 0.0) * (1.15 / 0.90),
                postVvOverload, 0.5,
                "A later Pyro reaction should use the already-active VV reduction");
    }

    private static void testAccuracyPhaseF_DelayedReactionLiveResistanceContract() {
        TestCharacter electroOwner = testCharacter(Element.ELECTRO, CharacterId.SUCROSE);
        CombatSimulator electroSim = simulatorWith(electroOwner);
        ReactionEffectScheduler electroScheduler = new ReactionEffectScheduler(electroSim);
        electroSim.getEnemy().setAura(Element.HYDRO, 4.0, electroSim.getCurrentTime());
        electroScheduler.scheduleElectroCharged(
                CharacterId.SUCROSE, Element.ELECTRO, 4.0, 1000.0, false);
        electroSim.advanceTime(1.0);
        assertClose(900.0, electroSim.getTotalDamage(), EPS,
                "Electro-Charged first tick should use baseline Electro RES");
        electroSim.applyTeamBuff(new SimpleBuff(
                "Timed Electro RES fixture", 1.5, electroSim.getCurrentTime(),
                stats -> stats.add(StatType.ELECTRO_RES_SHRED, 0.15)));
        electroSim.advanceTime(1.0);
        assertClose(900.0 + 1025.0, electroSim.getTotalDamage(), EPS,
                "Electro-Charged second tick should use newly active Electro reduction");
        electroSim.advanceTime(1.0);
        assertClose(900.0 + 1025.0 + 900.0, electroSim.getTotalDamage(), EPS,
                "Electro-Charged tick should drop reduction after exact expiry");

        TestCharacter burningOwner = testCharacter(Element.PYRO, CharacterId.XIANGLING);
        CombatSimulator burningSim = simulatorWith(burningOwner);
        ReactionEffectScheduler burningScheduler = new ReactionEffectScheduler(burningSim);
        burningSim.getEnemy().applyAura(
                Element.DENDRO, 1.0, burningSim.getCurrentTime());
        burningScheduler.scheduleBurning(CharacterId.XIANGLING, 1000.0);
        burningSim.advanceTime(0.25);
        assertClose(900.0, burningSim.getTotalDamage(), EPS,
                "Burning first tick should use baseline Pyro RES");
        burningSim.applyTeamBuff(new SimpleBuff(
                "Timed Burning Pyro RES fixture", 0.5, burningSim.getCurrentTime(),
                stats -> stats.add(StatType.PYRO_RES_SHRED, 0.15)));
        burningSim.advanceTime(0.25);
        assertClose(900.0 + 1025.0, burningSim.getTotalDamage(), EPS,
                "Burning second tick should use newly active Pyro reduction");
        burningSim.advanceTime(0.25);
        assertClose(900.0 + 1025.0 + 900.0, burningSim.getTotalDamage(), EPS,
                "Burning tick should drop reduction at exact expiry");

        TestCharacter bloomOwner = testCharacter(Element.HYDRO, CharacterId.SUCROSE);
        CombatSimulator bloomSim = simulatorWith(bloomOwner);
        ReactionEffectScheduler bloomScheduler = new ReactionEffectScheduler(bloomSim);
        bloomScheduler.createDendroCore(CharacterId.SUCROSE, 1000.0);
        bloomSim.applyTeamBuff(new SimpleBuff(
                "Post-creation Dendro RES fixture", 10.0, bloomSim.getCurrentTime(),
                stats -> stats.add(StatType.DENDRO_RES_SHRED, 0.15)));
        bloomSim.advanceTime(6.0);
        assertClose(1025.0, bloomSim.getTotalDamage(), EPS,
                "Bloom explosion should use Dendro reduction activated after core creation");

        TestCharacter staleBloomOwner = testCharacter(Element.HYDRO, CharacterId.SUCROSE);
        CombatSimulator staleBloomSim = simulatorWith(staleBloomOwner);
        ReactionEffectScheduler staleBloomScheduler = new ReactionEffectScheduler(staleBloomSim);
        staleBloomSim.applyTeamBuff(new SimpleBuff(
                "Expired core Dendro RES fixture", 1.0, staleBloomSim.getCurrentTime(),
                stats -> stats.add(StatType.DENDRO_RES_SHRED, 0.15)));
        staleBloomScheduler.createDendroCore(CharacterId.SUCROSE, 1000.0);
        staleBloomSim.advanceTime(6.0);
        assertClose(900.0, staleBloomSim.getTotalDamage(), EPS,
                "Bloom explosion should not retain creation-time Dendro reduction");

        TestCharacter coreOwner = testCharacter(Element.ELECTRO, CharacterId.SUCROSE);
        CombatSimulator coreSim = simulatorWith(coreOwner);
        ReactionEffectScheduler coreScheduler = new ReactionEffectScheduler(coreSim);
        coreSim.applyTeamBuff(new SimpleBuff(
                "Core consumption Dendro RES fixture", 10.0, coreSim.getCurrentTime(),
                stats -> stats.add(StatType.DENDRO_RES_SHRED, 0.15)));
        coreSim.addDendroCore(CharacterId.SUCROSE, 1000.0);
        coreScheduler.consumeDendroCores(CharacterId.SUCROSE, 1000.0, "Hyperbloom", 1);
        assertClose(1025.0, coreSim.getTotalDamage(), EPS,
                "Hyperbloom consumption should use current Dendro reduction");
        coreSim.addDendroCore(CharacterId.SUCROSE, 1000.0);
        coreScheduler.consumeDendroCores(CharacterId.SUCROSE, 1000.0, "Burgeon", 1);
        assertClose(2050.0, coreSim.getTotalDamage(), EPS,
                "Burgeon consumption should share current Dendro reduction");

        TestCharacter lunarOwner = testCharacter(Element.ELECTRO, CharacterId.FLINS).asLunar();
        CombatSimulator lunarSim = simulatorWith(lunarOwner);
        ReactionEffectScheduler lunarScheduler = new ReactionEffectScheduler(lunarSim);
        double lunarBaseline = lunarScheduler.computeInitialLunarChargedDamage();
        lunarSim.applyTeamBuff(new SimpleBuff(
                "Weighted Lunar Electro RES fixture", 10.0, lunarSim.getCurrentTime(),
                stats -> stats.add(StatType.ELECTRO_RES_SHRED, 0.15)));
        double lunarReduced = lunarScheduler.computeInitialLunarChargedDamage();
        assertClose(lunarBaseline * (1.025 / 0.90), lunarReduced, EPS,
                "Weighted Lunar damage should use current matching reduction once");
        lunarSim.applyTeamBuff(new SimpleBuff(
                "Weighted Lunar unrelated RES fixture", 10.0, lunarSim.getCurrentTime(),
                stats -> stats.add(StatType.HYDRO_RES_SHRED, 0.40)));
        assertClose(lunarReduced, lunarScheduler.computeInitialLunarChargedDamage(), EPS,
                "Weighted Lunar damage should ignore unrelated elemental reduction");
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

    private static AttackAction shatterHit(String name) {
        AttackAction action = reactionHit(name, Element.PHYSICAL);
        action.setShatterTrigger(true);
        return action;
    }

    private static AttackAction damageHit(String name, Element element, double multiplier) {
        AttackAction action = new AttackAction(name, multiplier, element, StatType.BASE_ATK);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static AttackAction typedDamageHit(
            String name,
            ActionType actionType,
            double multiplier) {
        return new AttackAction(
                name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.DMG_BONUS_ALL,
                0.0,
                actionType);
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

    private static java.util.Set<Element> reverseSet(Element... elements) {
        java.util.Set<Element> result = new java.util.LinkedHashSet<>();
        for (int i = elements.length - 1; i >= 0; i--) {
            result.add(elements[i]);
        }
        return result;
    }

    private static void assertElementOrder(
            List<Element> actual, Element... expected) {
        assertEquals(expected.length, actual.size(),
                "Element order should contain the expected number of entries");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual.get(i),
                    "Element order mismatch at index " + i);
        }
    }

    private static void assertWeaponMetadata(
            Weapon weapon,
            String expectedName,
            double expectedBaseAtk,
            double expectedEnergyRecharge,
            model.type.WeaponType expectedType) {
        assertEquals(expectedName, weapon.getName(), expectedName + " display name");
        assertClose(expectedBaseAtk, weapon.getBaseAtk(), EPS, expectedName + " base ATK");
        assertClose(expectedEnergyRecharge,
                weapon.getStats().get(StatType.ENERGY_RECHARGE), EPS,
                expectedName + " Energy Recharge");
        assertEquals(expectedType, weapon.getWeaponType(), expectedName + " weapon type");
    }

    private static void assertFavoniusWindfallGeneratesEnergy(
            model.weapon.FavoniusWeapon weapon,
            String weaponName) {
        TestCharacter owner = testCharacter(Element.HYDRO).withStat(StatType.CRIT_RATE, 1.0);
        owner.setWeapon(weapon);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        AttackAction hit = damageHit(weaponName + " Windfall fixture", Element.HYDRO, 1.0);
        captureStandardOutput(() -> weapon.onDamage(owner, hit, 0.0, sim));
        assertTrue(owner.getCurrentEnergy() > 0.0,
                weaponName + " should inherit neutral-particle Windfall");
    }

    private static void assertSacrificialResetsSkill(
            model.weapon.SacrificialWeapon weapon,
            String weaponName) {
        TestCharacter owner = testCharacter(Element.HYDRO);
        owner.setWeapon(weapon);
        owner.setSkillCD(10.0);
        owner.markSkillUsed(0.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skillHit = new AttackAction(
                weaponName + " Composed fixture",
                1.0,
                Element.HYDRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        captureStandardOutput(() -> weapon.onDamage(owner, skillHit, 0.0, sim));
        assertTrue(owner.canSkill(0.0), weaponName + " should inherit Composed Skill reset");
    }

    private static void assertTargetAuraWeaponDamage(
            model.weapon.TargetAuraDamageWeapon weapon,
            Element eligibleElement,
            Element ineligibleElement,
            double expectedBonus,
            String weaponName) {
        TestCharacter owner = testCharacter(Element.PHYSICAL);
        owner.setWeapon(weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction hit = damageHit(weaponName + " target fixture", Element.PHYSICAL, 1.0);
        double baseDamage = calculateDirectDamage(sim, owner, hit, 0.0, 1.0);
        assertClose(0.0, owner.getEffectiveStats(0.0).get(StatType.DMG_BONUS_ALL), EPS,
                weaponName + " target bonus should not enter effective stats");

        sim.getEnemy().setAura(eligibleElement, 1.0);
        assertClose(baseDamage * (1.0 + expectedBonus),
                calculateDirectDamage(sim, owner, hit, 0.0, 1.0), EPS,
                weaponName + " eligible target damage");
        sim.getEnemy().setAura(eligibleElement, 0.0);
        sim.getEnemy().setAura(ineligibleElement, 1.0);
        assertClose(baseDamage, calculateDirectDamage(sim, owner, hit, 0.0, 1.0), EPS,
                weaponName + " ineligible target damage");
    }

    private static void assertReactionWindowBonus(
            model.weapon.ReactionWindowWeapon weapon,
            ReactionResult reaction,
            StatType statType,
            double expectedBonus,
            String scenario) {
        TestCharacter owner = testCharacter(Element.HYDRO);
        owner.setWeapon(weapon);
        CombatSimulator sim = simulatorWith(owner);
        sim.notifyReaction(reaction, owner);
        assertClose(expectedBonus, resolvedStat(sim, owner, statType), EPS, scenario);
    }

    private static void assertClose(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static mechanics.data.TalentDataSource kaeyaTalentData(int constellation) {
        return (characterName, key, defaultValue) -> {
            if ("Kaeya".equals(characterName) && "Constellation".equals(key)) {
                return constellation;
            }
            return defaultValue;
        };
    }

    private static mechanics.data.TalentDataSource amberTalentData(int constellation) {
        return (characterName, key, defaultValue) -> {
            if ("Amber".equals(characterName) && "Constellation".equals(key)) {
                return constellation;
            }
            return defaultValue;
        };
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
