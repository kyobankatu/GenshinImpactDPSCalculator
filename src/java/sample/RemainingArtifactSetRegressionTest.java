package sample;

import java.util.function.Function;
import java.util.function.Supplier;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.artifact.DisenchantmentInDeepShadow;
import model.artifact.ObsidianCodex;
import model.artifact.RetracingBolide;
import model.artifact.ScrollOfTheHeroOfCinderCity;
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

/** Regression checks for the remaining derived and boundary artifact sets. */
public final class RemainingArtifactSetRegressionTest {
    private static final double EPS = 1e-9;

    private RemainingArtifactSetRegressionTest() {
    }

    /** Runs metadata, live-state, replacement, ordering, and boundary checks. */
    public static void main(String[] args) {
        testCanonicalMetadataAndStats();
        testDisenchantmentReactionBonus();
        testDisenchantmentLiveStatusAndBinding();
        testScrollOwnerElementAndOffFieldTrigger();
        testScrollTypedReactionMappings();
        testScrollReplacementExpiryAndIndependence();
        testScrollSameSetReplacement();
        testScrollPostSnapshotOrdering();
        testNullStats();
        System.out.println("RemainingArtifactSetRegressionTest passed");
    }

    /** Verifies canonical names, supported stats, and inert boundary sets. */
    private static void testCanonicalMetadataAndStats() {
        DisenchantmentInDeepShadow disenchantment =
                new DisenchantmentInDeepShadow();
        assertEquals("Disenchantment in Deep Shadow",
                disenchantment.getName(), "Disenchantment name");
        assertClose(0.18,
                disenchantment.getStats().get(StatType.ATK_PERCENT),
                "Disenchantment ATK bonus");
        assertClose(0.80,
                disenchantment.getStats().get(
                        StatType.SUPERCONDUCT_DMG_BONUS),
                "Disenchantment Superconduct bonus");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 37.0);
        DisenchantmentInDeepShadow preserved =
                new DisenchantmentInDeepShadow(supplied);
        assertTrue(preserved.getStats() == supplied,
                "Disenchantment should retain supplied stats");
        assertClose(37.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                "Disenchantment supplied marker");

        testInertBoundarySet("Obsidian Codex", ObsidianCodex::new,
                ObsidianCodex::new);
        testInertBoundarySet("Retracing Bolide", RetracingBolide::new,
                RetracingBolide::new);
        testInertBoundarySet("Scroll of the Hero of Cinder City",
                ScrollOfTheHeroOfCinderCity::new,
                ScrollOfTheHeroOfCinderCity::new);
    }

    /** Verifies the 80% fixed bonus reaches full Superconduct resolution. */
    private static void testDisenchantmentReactionBonus() {
        double baseline = superconductDamage(new ArtifactSet(
                "Blank", new StatsContainer()));
        double boosted = superconductDamage(
                new DisenchantmentInDeepShadow());
        assertClose(baseline * 1.80, boosted,
                "Disenchantment Superconduct resolver bonus");
    }

    /** Verifies live Superconduct status, exact expiry, and binding guards. */
    private static void testDisenchantmentLiveStatusAndBinding() {
        DisenchantmentInDeepShadow artifact =
                new DisenchantmentInDeepShadow();
        TestCharacter owner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, artifact);
        CombatSimulator sim = simulatorWith(owner);

        assertClose(0.0, critRate(owner, sim),
                "Disenchantment inactive CRIT Rate");
        applySuperconductStatus(sim);
        assertClose(0.16, critRate(owner, sim),
                "Disenchantment active CRIT Rate");

        sim.advanceTime(5.0);
        applySuperconductStatus(sim);
        assertEquals(1, countTeamBuffs(
                sim, BuffId.SUPERCONDUCT_PHYS_RES_SHRED),
                "Superconduct status replacement count");
        sim.advanceTime(11.999);
        assertClose(0.16, critRate(owner, sim),
                "Disenchantment refreshed status before expiry");
        sim.advanceTime(0.001);
        assertClose(0.0, critRate(owner, sim),
                "Disenchantment exact status expiry");

        artifact.initializeForSimulator(owner, sim, true);
        assertEquals(1, countCharacterBuffs(owner,
                BuffId.DISENCHANTMENT_SUPERCONDUCT_CRIT_RATE),
                "Disenchantment idempotent binding");

        assertIllegalArgument(
                () -> new DisenchantmentInDeepShadow()
                        .initializeForSimulator(null, sim, true),
                "Disenchantment null owner binding");
        assertIllegalArgument(
                () -> new DisenchantmentInDeepShadow()
                        .initializeForSimulator(owner, null, true),
                "Disenchantment null simulator binding");
        assertIllegalState(
                () -> artifact.initializeForSimulator(
                        owner, new CombatSimulator(), true),
                "Disenchantment cross-simulator binding");
    }

    /** Verifies owner attribution, owner element inclusion, and off-field use. */
    private static void testScrollOwnerElementAndOffFieldTrigger() {
        TestCharacter active = character(
                CharacterId.XINGQIU, Element.HYDRO);
        ScrollOfTheHeroOfCinderCity scroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter owner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, scroll);
        CombatSimulator sim = simulatorWith(active, owner);
        assertTrue(sim.getActiveCharacter() != owner,
                "Scroll owner should start off field");

        scroll.onReaction(sim, superconduct(), owner, owner);
        assertClose(0.12, elementalBonus(
                active, sim, StatType.ELECTRO_DMG_BONUS),
                "Off-field Scroll Electro team bonus");
        assertClose(0.12, elementalBonus(
                active, sim, StatType.CRYO_DMG_BONUS),
                "Off-field Scroll Cryo team bonus");
        assertClose(0.12, elementalBonus(
                owner, sim, StatType.ELECTRO_DMG_BONUS),
                "Scroll owner receives team bonus");

        ScrollOfTheHeroOfCinderCity mismatchScroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter pyroOwner = character(
                CharacterId.BENNETT, Element.PYRO, mismatchScroll);
        CombatSimulator mismatchSim = simulatorWith(pyroOwner);
        mismatchScroll.onReaction(
                mismatchSim, superconduct(), pyroOwner, pyroOwner);
        assertClose(0.0, elementalBonus(
                pyroOwner, mismatchSim, StatType.ELECTRO_DMG_BONUS),
                "Scroll owner element must participate in reaction");

        ScrollOfTheHeroOfCinderCity wrongSourceScroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter wrongSourceOwner = character(
                CharacterId.LISA, Element.ELECTRO, wrongSourceScroll);
        TestCharacter stranger = character(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator wrongSourceSim = simulatorWith(
                wrongSourceOwner, stranger);
        wrongSourceScroll.onReaction(
                wrongSourceSim, superconduct(), stranger, wrongSourceOwner);
        assertClose(0.0, elementalBonus(
                wrongSourceOwner,
                wrongSourceSim,
                StatType.ELECTRO_DMG_BONUS),
                "Scroll requires owner-attributed reaction");

        wrongSourceScroll.onReaction(null, superconduct(),
                wrongSourceOwner, wrongSourceOwner);
        wrongSourceScroll.onReaction(wrongSourceSim, null,
                wrongSourceOwner, wrongSourceOwner);
        wrongSourceScroll.onReaction(wrongSourceSim, ReactionResult.none(),
                wrongSourceOwner, wrongSourceOwner);
    }

    /** Verifies typed element mapping for representative reaction families. */
    private static void testScrollTypedReactionMappings() {
        assertScrollMapping(
                Element.PYRO,
                ReactionResult.amp(
                        2.0, "Vaporize", ReactionResult.Kind.VAPORIZE),
                StatType.PYRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                null,
                "Scroll Vaporize mapping");
        assertScrollMapping(
                Element.ANEMO,
                ReactionResult.transform(
                        1.0,
                        "Swirl",
                        ReactionResult.Kind.SWIRL,
                        Element.PYRO,
                        Element.PYRO),
                StatType.ANEMO_DMG_BONUS,
                StatType.PYRO_DMG_BONUS,
                null,
                "Scroll Swirl mapping");
        assertScrollMapping(
                Element.GEO,
                ReactionResult.transform(
                        0.0,
                        "Crystallize",
                        ReactionResult.Kind.CRYSTALLIZE,
                        Element.HYDRO,
                        Element.GEO),
                StatType.GEO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                null,
                "Scroll Crystallize mapping");
        assertScrollMapping(
                Element.ELECTRO,
                ReactionResult.transform(
                        1.0,
                        "Hyperbloom",
                        ReactionResult.Kind.HYPERBLOOM,
                        null,
                        Element.DENDRO),
                StatType.ELECTRO_DMG_BONUS,
                StatType.DENDRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                "Scroll Hyperbloom excludes Hydro");
        assertScrollMapping(
                Element.DENDRO,
                ReactionResult.additive(
                        1.0,
                        "Spread",
                        ReactionResult.Kind.SPREAD,
                        Element.DENDRO),
                StatType.DENDRO_DMG_BONUS,
                null,
                StatType.ELECTRO_DMG_BONUS,
                "Scroll Spread maps only Dendro");

        ReactionResult lunarCharged = ReactionResult.lunar(
                1.0,
                ReactionResult.LunarType.CHARGED,
                null,
                Element.ELECTRO,
                false,
                false);
        assertScrollMapping(
                Element.ELECTRO,
                lunarCharged,
                StatType.ELECTRO_DMG_BONUS,
                StatType.HYDRO_DMG_BONUS,
                null,
                "Scroll Lunar-Charged mapping");

        ScrollOfTheHeroOfCinderCity derivedScroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter derivedOwner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, derivedScroll);
        CombatSimulator derivedSim = simulatorWith(derivedOwner);
        derivedScroll.onReaction(
                derivedSim,
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CHARGED),
                derivedOwner,
                derivedOwner);
        assertClose(0.0, elementalBonus(
                derivedOwner,
                derivedSim,
                StatType.ELECTRO_DMG_BONUS),
                "Synthetic Lunar notification should not trigger Scroll");
    }

    /** Verifies per-element replacement, independent expiry, and half-open time. */
    private static void testScrollReplacementExpiryAndIndependence() {
        ScrollOfTheHeroOfCinderCity scroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter owner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, scroll);
        CombatSimulator sim = simulatorWith(owner);
        scroll.onReaction(sim, superconduct(), owner, owner);

        sim.advanceTime(5.0);
        ReactionResult electroCharged = ReactionResult.transform(
                1.0,
                "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED,
                null,
                Element.ELECTRO);
        scroll.onReaction(sim, electroCharged, owner, owner);

        assertEquals(1, countTeamBuffs(
                sim, BuffId.SCROLL_CINDER_CITY_ELECTRO_DMG_BONUS),
                "Scroll Electro replacement count");
        assertEquals(1, countTeamBuffs(
                sim, BuffId.SCROLL_CINDER_CITY_CRYO_DMG_BONUS),
                "Scroll Cryo independent count");
        assertEquals(1, countTeamBuffs(
                sim, BuffId.SCROLL_CINDER_CITY_HYDRO_DMG_BONUS),
                "Scroll Hydro independent count");

        sim.advanceTime(9.999);
        assertClose(0.12, elementalBonus(
                owner, sim, StatType.CRYO_DMG_BONUS),
                "Scroll original element before expiry");
        assertClose(0.12, elementalBonus(
                owner, sim, StatType.ELECTRO_DMG_BONUS),
                "Scroll refreshed element before expiry");
        sim.advanceTime(0.001);
        assertClose(0.0, elementalBonus(
                owner, sim, StatType.CRYO_DMG_BONUS),
                "Scroll original element exact expiry");
        assertClose(0.12, elementalBonus(
                owner, sim, StatType.ELECTRO_DMG_BONUS),
                "Scroll refreshed element remains active");
        assertClose(0.12, elementalBonus(
                owner, sim, StatType.HYDRO_DMG_BONUS),
                "Scroll new element remains active");

        sim.advanceTime(5.0);
        assertClose(0.0, elementalBonus(
                owner, sim, StatType.ELECTRO_DMG_BONUS),
                "Scroll refreshed element exact expiry");
        assertClose(0.0, elementalBonus(
                owner, sim, StatType.HYDRO_DMG_BONUS),
                "Scroll new element exact expiry");
    }

    /** Verifies a second same-name set replaces rather than stacks per element. */
    private static void testScrollSameSetReplacement() {
        ScrollOfTheHeroOfCinderCity firstScroll =
                new ScrollOfTheHeroOfCinderCity();
        ScrollOfTheHeroOfCinderCity secondScroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter firstOwner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, firstScroll);
        TestCharacter secondOwner = character(
                CharacterId.LISA, Element.ELECTRO, secondScroll);
        CombatSimulator sim = simulatorWith(firstOwner, secondOwner);
        firstScroll.onReaction(sim, superconduct(), firstOwner, firstOwner);
        sim.advanceTime(5.0);
        secondScroll.onReaction(sim, superconduct(), secondOwner, secondOwner);

        assertEquals(1, countTeamBuffs(
                sim, BuffId.SCROLL_CINDER_CITY_ELECTRO_DMG_BONUS),
                "Same-set Scroll Electro non-stack");
        Buff electroBuff = findTeamBuff(
                sim, BuffId.SCROLL_CINDER_CITY_ELECTRO_DMG_BONUS);
        assertEquals(secondOwner.getCharacterId(),
                electroBuff.getSourceCharacterId(),
                "Same-set Scroll replacement source");
        assertClose(0.12, elementalBonus(
                firstOwner, sim, StatType.ELECTRO_DMG_BONUS),
                "Same-set Scroll replacement value");
    }

    /** Verifies the trigger hit uses pre-buff stats and the next hit uses 12%. */
    private static void testScrollPostSnapshotOrdering() {
        double[] baseline = vaporizeThenPyroDamage(
                new ArtifactSet("Blank", new StatsContainer()));
        double[] scroll = vaporizeThenPyroDamage(
                new ScrollOfTheHeroOfCinderCity());
        assertClose(baseline[0], scroll[0],
                "Scroll should not buff its triggering hit");
        assertClose(baseline[1] * 1.12, scroll[1],
                "Scroll should buff the subsequent hit");
    }

    /** Verifies every supplied-stat constructor rejects null explicitly. */
    private static void testNullStats() {
        assertNullRejected(() -> new DisenchantmentInDeepShadow(null),
                "Disenchantment null stats");
        assertNullRejected(() -> new ScrollOfTheHeroOfCinderCity(null),
                "Scroll null stats");
        assertNullRejected(() -> new ObsidianCodex(null),
                "Obsidian Codex null stats");
        assertNullRejected(() -> new RetracingBolide(null),
                "Retracing Bolide null stats");
    }

    /** Verifies one unsupported set preserves stats and stays inert. */
    private static void testInertBoundarySet(
            String expectedName,
            Supplier<ArtifactSet> freshFactory,
            Function<StatsContainer, ArtifactSet> suppliedFactory) {
        ArtifactSet fresh = freshFactory.get();
        assertEquals(expectedName, fresh.getName(), expectedName + " name");
        assertAllZero(fresh.getStats(), expectedName + " fresh stats");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 37.0);
        ArtifactSet preserved = suppliedFactory.apply(supplied);
        assertTrue(preserved.getStats() == supplied,
                expectedName + " should retain supplied stats");
        assertOnlyStat(supplied, StatType.ELEMENTAL_MASTERY, 37.0,
                expectedName + " supplied stats");

        StatsContainer passiveTarget = new StatsContainer();
        passiveTarget.set(StatType.ELEMENTAL_MASTERY, 11.0);
        preserved.applyPassive(passiveTarget);
        assertOnlyStat(passiveTarget, StatType.ELEMENTAL_MASTERY, 11.0,
                expectedName + " inactive effects");

        ArtifactSet independent = freshFactory.get();
        fresh.getStats().set(StatType.ATK_PERCENT, 0.75);
        assertAllZero(independent.getStats(),
                expectedName + " independent instances");
    }

    /** Verifies one typed reaction maps to expected and excluded stats. */
    private static void assertScrollMapping(
            Element ownerElement,
            ReactionResult result,
            StatType firstExpected,
            StatType secondExpected,
            StatType excluded,
            String message) {
        ScrollOfTheHeroOfCinderCity scroll =
                new ScrollOfTheHeroOfCinderCity();
        TestCharacter owner = character(
                CharacterId.SUCROSE, ownerElement, scroll);
        CombatSimulator sim = simulatorWith(owner);
        scroll.onReaction(sim, result, owner, owner);
        assertClose(0.12, elementalBonus(owner, sim, firstExpected),
                message + " first element");
        if (secondExpected != null) {
            assertClose(0.12, elementalBonus(owner, sim, secondExpected),
                    message + " second element");
        }
        if (excluded != null) {
            assertClose(0.0, elementalBonus(owner, sim, excluded),
                    message + " excluded element");
        }
    }

    /** Returns one full Superconduct result with typed damage metadata. */
    private static ReactionResult superconduct() {
        return ReactionResult.transform(
                1.0,
                "Superconduct",
                ReactionResult.Kind.SUPERCONDUCT,
                null,
                Element.CRYO);
    }

    /** Returns Superconduct damage from one deterministic zero-direct hit. */
    private static double superconductDamage(ArtifactSet artifact) {
        TestCharacter owner = character(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO, artifact);
        CombatSimulator sim = simulatorWith(owner);
        sim.getEnemy().applyAura(Element.CRYO, 2.0, 0.0);
        sim.performAction(owner.getCharacterId(), attack(
                "Disenchantment Superconduct",
                0.0,
                Element.ELECTRO,
                1.0));
        return sim.getTotalDamage();
    }

    /** Returns trigger-hit and subsequent-hit damage for Scroll ordering. */
    private static double[] vaporizeThenPyroDamage(ArtifactSet artifact) {
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, artifact);
        CombatSimulator sim = simulatorWith(owner);
        sim.getEnemy().applyAura(Element.HYDRO, 2.0, 0.0);
        sim.performAction(owner.getCharacterId(), attack(
                "Scroll trigger Vaporize",
                1.0,
                Element.PYRO,
                1.0));
        double triggerDamage = sim.getTotalDamage();
        sim.performAction(owner.getCharacterId(), attack(
                "Scroll subsequent Pyro",
                1.0,
                Element.PYRO,
                0.0));
        double subsequentDamage = sim.getTotalDamage() - triggerDamage;
        return new double[] { triggerDamage, subsequentDamage };
    }

    /** Creates one deterministic direct-damage action. */
    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            double gaugeUnits) {
        return new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                gaugeUnits,
                ActionType.SKILL);
    }

    /** Applies the canonical twelve-second Superconduct status marker. */
    private static void applySuperconductStatus(CombatSimulator sim) {
        sim.applyTeamBuffNoStack(new SimpleBuff(
                "Superconduct Physical RES Shred",
                BuffId.SUPERCONDUCT_PHYS_RES_SHRED,
                12.0,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.PHYS_RES_SHRED, 0.40)));
    }

    /** Returns current owner CRIT Rate. */
    private static double critRate(
            TestCharacter owner,
            CombatSimulator sim) {
        return owner.getEffectiveStats(sim.getCurrentTime()).get(
                StatType.CRIT_RATE);
    }

    /** Returns one current elemental damage bonus. */
    private static double elementalBonus(
            TestCharacter character,
            CombatSimulator sim,
            StatType statType) {
        StatsContainer stats = character.getEffectiveStats(
                sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats.get(statType);
    }

    /** Counts simulator team buffs with one typed identity. */
    private static int countTeamBuffs(CombatSimulator sim, BuffId id) {
        int count = 0;
        for (Buff buff : sim.getTeamBuffList()) {
            if (buff.getId() == id) {
                count++;
            }
        }
        return count;
    }

    /** Finds one simulator team buff by typed identity. */
    private static Buff findTeamBuff(CombatSimulator sim, BuffId id) {
        for (Buff buff : sim.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        throw new AssertionError("Missing team buff: " + id);
    }

    /** Counts owner buffs with one typed identity. */
    private static int countCharacterBuffs(TestCharacter owner, BuffId id) {
        int count = 0;
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == id) {
                count++;
            }
        }
        return count;
    }

    /** Creates one quiet simulator containing the supplied party. */
    private static CombatSimulator simulatorWith(TestCharacter... party) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : party) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Creates one deterministic character with optional artifact sets. */
    private static TestCharacter character(
            CharacterId id,
            Element element,
            ArtifactSet... artifacts) {
        return new TestCharacter(id, element, artifacts);
    }

    /** Asserts every stat is zero. */
    private static void assertAllZero(StatsContainer stats, String message) {
        for (StatType type : StatType.values()) {
            assertClose(0.0, stats.get(type), message + " " + type);
        }
    }

    /** Asserts one expected stat is the container's only non-zero stat. */
    private static void assertOnlyStat(
            StatsContainer stats,
            StatType expectedType,
            double expectedValue,
            String message) {
        for (StatType type : StatType.values()) {
            double expected = type == expectedType ? expectedValue : 0.0;
            assertClose(expected, stats.get(type), message + " " + type);
        }
    }

    /** Asserts a null supplied-stat constructor fails. */
    private static void assertNullRejected(Runnable constructor, String message) {
        try {
            constructor.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    /** Asserts an invalid binding argument is rejected. */
    private static void assertIllegalArgument(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(
                    message + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    /** Asserts cross-binding is rejected. */
    private static void assertIllegalState(Runnable action, String message) {
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

    /** Minimal deterministic character for artifact checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement,
                ArtifactSet... equippedArtifacts) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = equippedArtifacts;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 0.0);
        }

        /** Applies no character-specific passives. */
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
