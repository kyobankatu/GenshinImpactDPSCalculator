package sample;

import java.util.List;
import java.util.stream.Collectors;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.NightweaversLookingGlass;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Focused converted-Bloom, dual-window, multi-copy, and snapshot checks. */
public final class NightweaversLookingGlassRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;
    private static final StatType[] TEAM_REACTION_STATS = {
        StatType.BLOOM_DMG_BONUS,
        StatType.HYPERBLOOM_DMG_BONUS,
        StatType.BURGEON_DMG_BONUS,
        StatType.LUNAR_BLOOM_DMG_BONUS
    };

    private NightweaversLookingGlassRegressionTest() {
    }

    /** Runs the complete B-180 focused regression contract. */
    public static void main(String[] args) {
        testMetadataAndRefinementTable();
        testNormalAuraConvertedBloomBonusRouting();
        testQuickenOnlyConvertedBloomBonusRouting();
        testOrdinaryAndLunarBloomBonusIsolation();
        testConvertedBloomNotificationOrder();
        testOffFieldSkillHitAndReactionWindows();
        testOwnerEmCopiesAndTeamReactionStats();
        testHalfOpenBoundariesAndRefresh();
        testMultiCopyProviderSelection();
        testNoCrossCopyWindowCombination();
        testSnapshotRestoreAndIndependentInstances();
        testTriggerRejections();
        testBindingAndStateGuards();
        System.out.println("NightweaversLookingGlassRegressionTest passed");
    }

    private static void testMetadataAndRefinementTable() {
        NightweaversLookingGlass defaultWeapon =
                new NightweaversLookingGlass();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Nightweaver default refinement");
        assertEquals("Nightweaver's Looking Glass", defaultWeapon.getName(),
                "Nightweaver name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Nightweaver weapon type");
        assertClose(542.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Nightweaver base ATK");
        assertClose(265.0,
                defaultWeapon.getStats().get(StatType.ELEMENTAL_MASTERY),
                "Nightweaver EM substat");

        for (int refinement = 1; refinement <= 5; refinement++) {
            NightweaversLookingGlass weapon =
                    new NightweaversLookingGlass(refinement);
            assertClose(45.0 + 15.0 * refinement,
                    weapon.getElementalMasteryPerWindow(),
                    "Nightweaver owner EM R" + refinement);
            assertClose(0.90 + 0.30 * refinement,
                    weapon.getBloomDamageBonus(),
                    "Nightweaver Bloom bonus R" + refinement);
            assertClose(0.60 + 0.20 * refinement,
                    weapon.getHyperbloomAndBurgeonDamageBonus(),
                    "Nightweaver Hyperbloom/Burgeon bonus R" + refinement);
            assertClose(0.30 + 0.10 * refinement,
                    weapon.getLunarBloomDamageBonus(),
                    "Nightweaver Lunar-Bloom bonus R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new NightweaversLookingGlass(0),
                "Nightweaver rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new NightweaversLookingGlass(6),
                "Nightweaver rejects R6");
    }

    private static void testNormalAuraConvertedBloomBonusRouting() {
        double hydroBaseline = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, true, 0.0, 0.0, false);
        double hydroLunarBonus = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, true, 0.0, 0.40, false);
        assertClose(hydroBaseline * 1.40, hydroLunarBonus,
                "Converted Hydro-on-Dendro uses Lunar-Bloom bonus");

        double dendroBaseline = bloomCoreDamage(
                Element.DENDRO, Element.HYDRO, true, 0.0, 0.0, false);
        double dendroLunarBonus = bloomCoreDamage(
                Element.DENDRO, Element.HYDRO, true, 0.0, 0.40, false);
        assertClose(dendroBaseline * 1.40, dendroLunarBonus,
                "Converted Dendro-on-Hydro uses Lunar-Bloom bonus");
    }

    private static void testQuickenOnlyConvertedBloomBonusRouting() {
        double baseline = bloomCoreDamage(
                Element.HYDRO, null, true, 0.0, 0.0, true);
        double lunarBonus = bloomCoreDamage(
                Element.HYDRO, null, true, 0.0, 0.55, true);
        assertClose(baseline * 1.55, lunarBonus,
                "Quicken-only converted Bloom uses Lunar-Bloom bonus");
    }

    private static void testOrdinaryAndLunarBloomBonusIsolation() {
        double ordinaryBaseline = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, false, 0.0, 0.0, false);
        double ordinaryBloomBonus = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, false, 0.60, 0.0, false);
        double ordinaryLunarBonus = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, false, 0.0, 0.60, false);
        assertClose(ordinaryBaseline * 1.60, ordinaryBloomBonus,
                "Ordinary Bloom uses ordinary Bloom bonus");
        assertClose(ordinaryBaseline, ordinaryLunarBonus,
                "Ordinary Bloom ignores Lunar-Bloom bonus");

        double lunarBaseline = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, true, 0.0, 0.0, false);
        double lunarOrdinaryBonus = bloomCoreDamage(
                Element.HYDRO, Element.DENDRO, true, 0.60, 0.0, false);
        assertClose(lunarBaseline, lunarOrdinaryBonus,
                "Converted Bloom ignores ordinary Bloom bonus");
    }

    private static void testConvertedBloomNotificationOrder() {
        assertConvertedBloomNotifiedBeforeCoreCreation(false,
                "Normal-aura converted Bloom notification order");
        assertConvertedBloomNotifiedBeforeCoreCreation(true,
                "Quicken-only converted Bloom notification order");
    }

    private static void testOffFieldSkillHitAndReactionWindows() {
        NightweaversLookingGlass weapon = new NightweaversLookingGlass(1);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null, false);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon, false);
        CombatSimulator sim = simulatorWith(ally, owner);
        assertEquals(ally, sim.getActiveCharacter(),
                "Nightweaver fixture starts with owner off-field");

        resolveDirectHit(sim, owner, skillHit("Off-field Hydro Skill", Element.HYDRO));
        assertTrue(weapon.isPrayerActive(0.0),
                "Off-field owner Hydro Skill damage opens Prayer");
        assertTrue(!weapon.isNewMoonActive(0.0),
                "Skill damage does not open New Moon");

        notifyActualLunarBloom(sim, ally);
        assertTrue(weapon.isNewMoonActive(0.0),
                "Actual party Lunar-Bloom opens New Moon");

        sim.advanceTime(1.0);
        AttackAction classified = hit(
                "Off-field Skill-classified follow-up",
                Element.DENDRO,
                ActionType.OTHER,
                1.0,
                true);
        classified.setCountsAsSkillDmg(true);
        resolveDirectHit(sim, owner, classified);
        assertTrue(weapon.isPrayerActive(1.0 + 270.0 * FRAME - 1e-9),
                "Explicit Skill-damage classification refreshes Prayer");
    }

    private static void testOwnerEmCopiesAndTeamReactionStats() {
        NightweaversLookingGlass weapon = new NightweaversLookingGlass(2);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.DENDRO, weapon, false);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.HYDRO, null, false);
        CombatSimulator sim = simulatorWith(owner, ally);
        double baseEm = 265.0;
        double oneCopy = weapon.getElementalMasteryPerWindow();

        assertClose(baseEm, resolvedStats(owner, sim).get(
                StatType.ELEMENTAL_MASTERY),
                "Nightweaver starts with only its EM substat");
        resolveDirectHit(sim, owner, skillHit("Dendro Skill", Element.DENDRO));
        assertClose(baseEm + oneCopy, resolvedStats(owner, sim).get(
                StatType.ELEMENTAL_MASTERY),
                "Prayer grants one owner EM copy");
        assertNoTeamReactionStats(owner, sim,
                "Prayer alone grants no team reaction stats");

        notifyActualLunarBloom(sim, ally);
        assertClose(baseEm + 2.0 * oneCopy, resolvedStats(owner, sim).get(
                StatType.ELEMENTAL_MASTERY),
                "Prayer and New Moon grant two owner EM copies");
        assertTeamReactionStats(
                resolvedStats(owner, sim), weapon,
                "Owner receives simultaneous-window team stats");
        assertTeamReactionStats(
                resolvedStats(ally, sim), weapon,
                "Ally receives simultaneous-window team stats");

        List<Buff> typedBuffs = sim.getApplicableBuffs(ally).stream()
                .filter(buff -> buff.getId()
                        == BuffId.NIGHTWEAVERS_LOOKING_GLASS_TEAM_REACTION_DMG)
                .collect(Collectors.toList());
        assertEquals(1, typedBuffs.size(),
                "Nightweaver exposes one typed team buff");
        assertEquals(owner.getCharacterId(),
                typedBuffs.get(0).getSourceCharacterId(),
                "Nightweaver team buff keeps owner attribution");
    }

    private static void testHalfOpenBoundariesAndRefresh() {
        NightweaversLookingGlass prayerWeapon =
                new NightweaversLookingGlass(1);
        TestCharacter prayerOwner = character(
                CharacterId.SUCROSE, Element.HYDRO, prayerWeapon, false);
        CombatSimulator prayerSim = simulatorWith(prayerOwner);
        resolveDirectHit(
                prayerSim,
                prayerOwner,
                skillHit("Prayer boundary Skill", Element.HYDRO));
        assertTrue(prayerWeapon.isPrayerActive(270.0 * FRAME - 1e-9),
                "Prayer is active immediately before frame 270");
        assertTrue(!prayerWeapon.isPrayerActive(270.0 * FRAME),
                "Prayer expires at exact frame 270");
        prayerSim.advanceTime(240.0 * FRAME);
        resolveDirectHit(
                prayerSim,
                prayerOwner,
                skillHit("Prayer refresh Skill", Element.DENDRO));
        assertTrue(prayerWeapon.isPrayerActive(510.0 * FRAME - 1e-9),
                "Prayer refresh replaces expiry from its hit time");
        assertTrue(!prayerWeapon.isPrayerActive(510.0 * FRAME),
                "Refreshed Prayer keeps a half-open frame-270 duration");

        NightweaversLookingGlass moonWeapon =
                new NightweaversLookingGlass(1);
        TestCharacter moonOwner = character(
                CharacterId.SUCROSE, Element.DENDRO, moonWeapon, false);
        TestCharacter moonAlly = character(
                CharacterId.AMBER, Element.HYDRO, null, false);
        CombatSimulator moonSim = simulatorWith(moonOwner, moonAlly);
        notifyActualLunarBloom(moonSim, moonAlly);
        assertTrue(moonWeapon.isNewMoonActive(600.0 * FRAME - 1e-9),
                "New Moon is active immediately before frame 600");
        assertTrue(!moonWeapon.isNewMoonActive(600.0 * FRAME),
                "New Moon expires at exact frame 600");
        moonSim.advanceTime(360.0 * FRAME);
        notifyActualLunarBloom(moonSim, moonAlly);
        assertTrue(moonWeapon.isNewMoonActive(960.0 * FRAME - 1e-9),
                "New Moon refresh replaces expiry from reaction time");
        assertTrue(!moonWeapon.isNewMoonActive(960.0 * FRAME),
                "Refreshed New Moon remains half-open");
    }

    private static void testMultiCopyProviderSelection() {
        NightweaversLookingGlass low = new NightweaversLookingGlass(1);
        NightweaversLookingGlass high = new NightweaversLookingGlass(5);
        TestCharacter lowOwner = character(
                CharacterId.SUCROSE, Element.HYDRO, low, false);
        TestCharacter highOwner = character(
                CharacterId.AMBER, Element.DENDRO, high, false);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null, false);
        CombatSimulator sim = simulatorWith(lowOwner, highOwner, ally);
        activateBothWindows(low, lowOwner, sim, ally);
        activateBothWindows(high, highOwner, sim, ally);
        assertTeamReactionStats(resolvedStats(ally, sim), high,
                "Highest active refinement provides the nonstacking team bonus");
        assertEquals(1L, countNightweaverTeamBuffs(sim, ally),
                "Multiple active copies expose one team buff");

        NightweaversLookingGlass fallbackLow =
                new NightweaversLookingGlass(1);
        NightweaversLookingGlass inactiveHigh =
                new NightweaversLookingGlass(5);
        TestCharacter fallbackLowOwner = character(
                CharacterId.SUCROSE, Element.HYDRO, fallbackLow, false);
        TestCharacter inactiveHighOwner = character(
                CharacterId.AMBER, Element.DENDRO, inactiveHigh, false);
        TestCharacter fallbackAlly = character(
                CharacterId.BENNETT, Element.PYRO, null, false);
        CombatSimulator fallbackSim = simulatorWith(
                fallbackLowOwner, inactiveHighOwner, fallbackAlly);
        activateBothWindows(
                fallbackLow,
                fallbackLowOwner,
                fallbackSim,
                fallbackAlly);
        resolveDirectHit(fallbackSim, inactiveHighOwner,
                skillHit("Inactive-high Prayer", Element.DENDRO));
        assertTeamReactionStats(
                resolvedStats(fallbackAlly, fallbackSim),
                fallbackLow,
                "Lower refinement provides when the higher copy lacks one window");
        assertEquals(1L, countNightweaverTeamBuffs(
                        fallbackSim, fallbackAlly),
                "Canonical selection falls back to one fully active copy");

        NightweaversLookingGlass first = new NightweaversLookingGlass(3);
        NightweaversLookingGlass second = new NightweaversLookingGlass(3);
        TestCharacter firstOwner = character(
                CharacterId.SUCROSE, Element.HYDRO, first, false);
        TestCharacter secondOwner = character(
                CharacterId.AMBER, Element.DENDRO, second, false);
        TestCharacter tieAlly = character(
                CharacterId.BENNETT, Element.PYRO, null, false);
        CombatSimulator tieSim = simulatorWith(
                firstOwner, secondOwner, tieAlly);
        activateBothWindows(first, firstOwner, tieSim, tieAlly);
        activateBothWindows(second, secondOwner, tieSim, tieAlly);
        List<Buff> active = nightweaverTeamBuffs(tieSim, tieAlly);
        assertEquals(1, active.size(),
                "Equal-refinement copies expose one team buff");
        assertEquals(firstOwner.getCharacterId(),
                active.get(0).getSourceCharacterId(),
                "Equal refinement uses first party-order provider");
    }

    private static void testNoCrossCopyWindowCombination() {
        NightweaversLookingGlass first = new NightweaversLookingGlass(1);
        NightweaversLookingGlass second = new NightweaversLookingGlass(5);
        TestCharacter firstOwner = character(
                CharacterId.SUCROSE, Element.HYDRO, first, false);
        TestCharacter secondOwner = character(
                CharacterId.AMBER, Element.DENDRO, second, false);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null, false);
        CombatSimulator sim = simulatorWith(firstOwner, secondOwner, ally);

        resolveDirectHit(sim, firstOwner,
                skillHit("First-copy Prayer", Element.HYDRO));
        second.onElementalReaction(
                ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM),
                ally,
                sim.getCurrentTime(),
                sim);
        assertTrue(first.isPrayerActive(0.0) && !first.isNewMoonActive(0.0),
                "First copy owns only Prayer");
        assertTrue(!second.isPrayerActive(0.0) && second.isNewMoonActive(0.0),
                "Second copy owns only New Moon");
        assertNoTeamReactionStats(ally, sim,
                "Windows from different copies never combine");
        assertEquals(0L, countNightweaverTeamBuffs(sim, ally),
                "Cross-copy windows expose no team buff");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        NightweaversLookingGlass weapon = new NightweaversLookingGlass(2);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon, false);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.DENDRO, null, false);
        CombatSimulator sim = simulatorWith(owner, ally);
        SimulatorSnapshot inactive = sim.saveSnapshot();

        resolveDirectHit(sim, owner,
                skillHit("Snapshot Prayer", Element.HYDRO));
        SimulatorSnapshot prayerOnly = sim.saveSnapshot();
        notifyActualLunarBloom(sim, ally);
        SimulatorSnapshot bothActive = sim.saveSnapshot();
        sim.advanceTime(5.0);
        SimulatorSnapshot moonOnly = sim.saveSnapshot();

        sim.restoreSnapshot(prayerOnly);
        assertTrue(weapon.isPrayerActive(sim.getCurrentTime()),
                "Prayer-only snapshot restores Prayer");
        assertTrue(!weapon.isNewMoonActive(sim.getCurrentTime()),
                "Prayer-only snapshot clears New Moon");
        sim.restoreSnapshot(bothActive);
        assertTrue(weapon.isPrayerActive(sim.getCurrentTime()),
                "Combined snapshot restores Prayer");
        assertTrue(weapon.isNewMoonActive(sim.getCurrentTime()),
                "Combined snapshot restores New Moon");
        assertTeamReactionStats(resolvedStats(ally, sim), weapon,
                "Combined snapshot restores team bonus");
        sim.restoreSnapshot(moonOnly);
        assertTrue(!weapon.isPrayerActive(sim.getCurrentTime()),
                "Moon-only snapshot keeps Prayer expired");
        assertTrue(weapon.isNewMoonActive(sim.getCurrentTime()),
                "Moon-only snapshot restores New Moon");
        sim.restoreSnapshot(inactive);
        assertTrue(!weapon.isPrayerActive(sim.getCurrentTime())
                        && !weapon.isNewMoonActive(sim.getCurrentTime()),
                "Inactive snapshot clears both windows");

        NightweaversLookingGlass independent =
                new NightweaversLookingGlass(4);
        TestCharacter independentOwner = character(
                CharacterId.BENNETT, Element.DENDRO, independent, false);
        CombatSimulator independentSim = simulatorWith(independentOwner);
        assertTrue(!independent.isPrayerActive(0.0)
                        && !independent.isNewMoonActive(0.0),
                "Separate instance does not inherit restored state");
        resolveDirectHit(independentSim, independentOwner,
                skillHit("Independent Skill", Element.DENDRO));
        assertTrue(independent.isPrayerActive(0.0),
                "Separate instance owns its own Prayer state");
    }

    private static void testTriggerRejections() {
        NightweaversLookingGlass weapon = new NightweaversLookingGlass(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon, false);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.DENDRO, null, false);
        CombatSimulator sim = simulatorWith(owner, ally);

        resolveDirectHit(sim, owner, hit(
                "Hydro Normal", Element.HYDRO, ActionType.NORMAL, 1.0, true));
        resolveDirectHit(sim, owner, hit(
                "Pyro Skill", Element.PYRO, ActionType.SKILL, 1.0, true));
        resolveDirectHit(sim, owner, hit(
                "Zero Skill", Element.HYDRO, ActionType.SKILL, 0.0, true));
        resolveDirectHit(sim, owner, hit(
                "Non-hit Skill", Element.HYDRO, ActionType.SKILL, 1.0, false));
        resolveDirectHit(sim, ally,
                skillHit("Wrong-source Dendro Skill", Element.DENDRO));
        weapon.onDamage(owner,
                skillHit("Wrong-simulator Skill", Element.HYDRO),
                0.0,
                new CombatSimulator());
        weapon.onDamage(owner, null, 0.0, sim);
        assertTrue(!weapon.isPrayerActive(0.0),
                "Wrong actions, elements, sources, and simulator reject Prayer");

        sim.notifyReaction(
                reaction(ReactionResult.Kind.BLOOM), ally);
        sim.notifyReaction(
                reaction(ReactionResult.Kind.HYPERBLOOM), ally);
        sim.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CHARGED), ally);
        sim.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CRYSTALLIZE), ally);
        sim.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.BLOOM),
                character(CharacterId.BENNETT, Element.PYRO, null, false));
        sim.notifyDerivedReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.BLOOM), ally);
        weapon.onElementalReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.BLOOM),
                ally,
                0.0,
                new CombatSimulator());
        weapon.onElementalReaction(null, ally, 0.0, sim);
        weapon.onElementalReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.BLOOM),
                null,
                0.0,
                sim);
        assertTrue(!weapon.isNewMoonActive(0.0),
                "Non-Lunar-Bloom, derived, foreign-source, and invalid callbacks reject New Moon");
        assertNoTeamReactionStats(ally, sim,
                "Rejected triggers expose no reaction stats");
    }

    private static void testBindingAndStateGuards() {
        NightweaversLookingGlass weapon = new NightweaversLookingGlass(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.HYDRO, weapon, false);
        CombatSimulator sim = simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        weapon.initializeForSimulator(owner, sim);

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> new NightweaversLookingGlass(1)
                        .restoreWeaponState(state),
                "Nightweaver rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Nightweaver rejects foreign state type");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Nightweaver rejects cross-simulator reuse");

        TestCharacter otherOwner = character(
                CharacterId.AMBER, Element.DENDRO, weapon, false);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(otherOwner, sim),
                "Nightweaver rejects cross-owner reuse");

        NightweaversLookingGlass nullWeapon =
                new NightweaversLookingGlass(1);
        TestCharacter nullOwner = character(
                CharacterId.AMBER, Element.DENDRO, nullWeapon, false);
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(null, sim),
                "Nightweaver rejects null owner");
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(nullOwner, null),
                "Nightweaver rejects null simulator");

        NightweaversLookingGlass unequipped =
                new NightweaversLookingGlass(1);
        TestCharacter wrongOwner = character(
                CharacterId.BENNETT, Element.PYRO, null, false);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(wrongOwner, sim),
                "Nightweaver rejects unequipped owner");

        NightweaversLookingGlass outside =
                new NightweaversLookingGlass(1);
        TestCharacter outsideOwner = character(
                CharacterId.AMBER, Element.DENDRO, outside, false);
        assertThrows(IllegalArgumentException.class,
                () -> outside.initializeForSimulator(outsideOwner, sim),
                "Nightweaver rejects owner outside simulator party");

        NightweaversLookingGlass removed =
                new NightweaversLookingGlass(1);
        TestCharacter removedOwner = character(
                CharacterId.AMBER, Element.HYDRO, removed, false);
        TestCharacter removedAlly = character(
                CharacterId.BENNETT, Element.DENDRO, null, false);
        CombatSimulator removedSim = simulatorWith(
                removedOwner, removedAlly);
        activateBothWindows(
                removed, removedOwner, removedSim, removedAlly);
        removedOwner.setWeapon(null);
        assertNoTeamReactionStats(removedAlly, removedSim,
                "Unequipped Nightweaver exposes no stale team state");
        removed.onDamage(
                removedOwner,
                skillHit("Unequipped Skill", Element.HYDRO),
                removedSim.getCurrentTime(),
                removedSim);
        removed.onElementalReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.BLOOM),
                removedAlly,
                removedSim.getCurrentTime(),
                removedSim);
        assertEquals(0L, countNightweaverTeamBuffs(
                removedSim, removedAlly),
                "Unequipped callbacks cannot re-expose team state");
    }

    private static void assertConvertedBloomNotifiedBeforeCoreCreation(
            boolean quickenOnly,
            String message) {
        TestCharacter source = character(
                CharacterId.SUCROSE, Element.HYDRO, null, true);
        CombatSimulator sim = simulatorWith(source);
        int[] notificationCount = { 0 };
        sim.addReactionListener((result, trigger, time, activeSimulator) -> {
            if (result.getKind() != ReactionResult.Kind.LUNAR_BLOOM) {
                return;
            }
            notificationCount[0]++;
            assertEquals(0, activeSimulator.getDendroCores().size(),
                    message + " observes no core before the callback");
        });
        if (quickenOnly) {
            sim.applyQuicken(1.0);
        } else {
            sim.getEnemy().setAura(Element.DENDRO, 2.0);
        }
        sim.performActionWithoutTimeAdvance(
                source.getCharacterId(),
                reactionHit("Notification ordering", Element.HYDRO));
        assertEquals(1, notificationCount[0],
                message + " emits one actual reaction callback");
        assertEquals(1, sim.getDendroCores().size(),
                message + " creates the core after notification");
    }

    private static double bloomCoreDamage(
            Element trigger,
            Element aura,
            boolean lunar,
            double bloomBonus,
            double lunarBloomBonus,
            boolean quickenOnly) {
        TestCharacter source = character(
                CharacterId.SUCROSE, trigger, null, lunar);
        source.getBaseStats().set(StatType.BLOOM_DMG_BONUS, bloomBonus);
        source.getBaseStats().set(
                StatType.LUNAR_BLOOM_DMG_BONUS, lunarBloomBonus);
        CombatSimulator sim = simulatorWith(source);
        if (quickenOnly) {
            sim.applyQuicken(1.0);
        } else {
            sim.getEnemy().setAura(aura, 2.0);
        }
        sim.performActionWithoutTimeAdvance(
                source.getCharacterId(), reactionHit("Bloom routing", trigger));
        assertEquals(1, sim.getDendroCores().size(),
                "Bloom routing fixture creates one core");
        return sim.getDendroCores().get(0).preResistanceDamage;
    }

    private static void activateBothWindows(
            NightweaversLookingGlass weapon,
            TestCharacter owner,
            CombatSimulator sim,
            TestCharacter reactionSource) {
        resolveDirectHit(sim, owner,
                skillHit("Activate Prayer", owner.getElement()));
        weapon.onElementalReaction(
                ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM),
                reactionSource,
                sim.getCurrentTime(),
                sim);
    }

    private static void notifyActualLunarBloom(
            CombatSimulator sim,
            Character source) {
        sim.notifyReaction(
                ReactionResult.lunar(0.0, ReactionResult.LunarType.BLOOM),
                source);
    }

    private static ReactionResult reaction(ReactionResult.Kind kind) {
        return new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE,
                1.0,
                0.0,
                kind.name(),
                kind);
    }

    private static AttackAction skillHit(String name, Element element) {
        return hit(name, element, ActionType.SKILL, 1.0, true);
    }

    private static AttackAction hit(
            String name,
            Element element,
            ActionType type,
            double damagePercent,
            boolean hitEffectTrigger) {
        AttackAction action = new AttackAction(
                name,
                damagePercent,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(hitEffectTrigger);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static AttackAction reactionHit(String name, Element element) {
        AttackAction action = hit(
                name, element, ActionType.OTHER, 0.0, false);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        return action;
    }

    private static void resolveDirectHit(
            CombatSimulator sim,
            Character source,
            AttackAction action) {
        sim.performActionWithoutTimeAdvance(source.getCharacterId(), action);
    }

    private static StatsContainer resolvedStats(
            Character character,
            CombatSimulator sim) {
        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
    }

    private static void assertTeamReactionStats(
            StatsContainer stats,
            NightweaversLookingGlass provider,
            String message) {
        assertClose(provider.getBloomDamageBonus(),
                stats.get(StatType.BLOOM_DMG_BONUS),
                message + " (Bloom)");
        assertClose(provider.getHyperbloomAndBurgeonDamageBonus(),
                stats.get(StatType.HYPERBLOOM_DMG_BONUS),
                message + " (Hyperbloom)");
        assertClose(provider.getHyperbloomAndBurgeonDamageBonus(),
                stats.get(StatType.BURGEON_DMG_BONUS),
                message + " (Burgeon)");
        assertClose(provider.getLunarBloomDamageBonus(),
                stats.get(StatType.LUNAR_BLOOM_DMG_BONUS),
                message + " (Lunar-Bloom)");
    }

    private static void assertNoTeamReactionStats(
            Character character,
            CombatSimulator sim,
            String message) {
        StatsContainer stats = resolvedStats(character, sim);
        for (StatType stat : TEAM_REACTION_STATS) {
            assertClose(0.0, stats.get(stat), message + " (" + stat + ")");
        }
    }

    private static long countNightweaverTeamBuffs(
            CombatSimulator sim,
            Character character) {
        return nightweaverTeamBuffs(sim, character).size();
    }

    private static List<Buff> nightweaverTeamBuffs(
            CombatSimulator sim,
            Character character) {
        return sim.getApplicableBuffs(character).stream()
                .filter(buff -> buff.getId()
                        == BuffId.NIGHTWEAVERS_LOOKING_GLASS_TEAM_REACTION_DMG)
                .collect(Collectors.toList());
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon,
            boolean lunar) {
        return new TestCharacter(id, element, weapon, lunar);
    }

    private static CombatSimulator simulatorWith(
            TestCharacter... characters) {
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
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
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
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Minimal deterministic owner with optional Lunar conversion capability. */
    private static final class TestCharacter extends Character {
        private final boolean lunar;

        private TestCharacter(
                CharacterId id,
                Element characterElement,
                Weapon equippedWeapon,
                boolean lunar) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = equippedWeapon;
            this.lunar = lunar;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 0.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public boolean isLunarCharacter() {
            return lunar;
        }
    }

    /** Foreign marker used to verify strict snapshot-state typing. */
    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
