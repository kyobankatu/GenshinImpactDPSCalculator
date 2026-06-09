package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.formula.ResistanceCalculator;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

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
        assertClose(0.6, sim.getEnemy().getAuraUnits(Element.HYDRO), 0.01,
                "Thundercloud tick should consume 0.4 GU Hydro");
        assertClose(0.6, sim.getEnemy().getAuraUnits(Element.ELECTRO), 0.01,
                "Thundercloud tick should consume 0.4 GU Electro");
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

    private static List<ReactionResult.Kind> captureReactionKinds(CombatSimulator sim) {
        List<ReactionResult.Kind> kinds = new ArrayList<>();
        sim.addReactionListener((result, source, time, simulator) -> kinds.add(result.getKind()));
        return kinds;
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
        double additive = ReactionCalculator.calculateAdditiveReactionDamage(90, 0.0, additiveMultiplier, 0.0);
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

    private static final class TestWeapon extends Weapon {
        private TestWeapon() {
            super("Test Weapon", new StatsContainer());
        }
    }
}
