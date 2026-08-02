package sample;

import java.util.Collections;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.formula.DamageCalculator;
import model.artifact.ADayCarvedFromRisingWinds;
import model.artifact.NighttimeWhispersInTheEchoingWoods;
import model.artifact.VermillionHereafter;
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
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Focused regression checks for hit, Skill-use, and Burst-use artifact windows.
 */
public final class ActionWindowArtifactRegressionTest {
    private static final double EPS = 1e-9;

    private ActionWindowArtifactRegressionTest() {
    }

    /**
     * Runs fixed-stat, timing, ordering, lifecycle, and snapshot checks.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        testMetadataAndSuppliedStats();
        testADayTriggerCategoriesAndOrdering();
        testADayTimingRefreshAndOffFieldTrigger();
        testNighttimeAcceptedSkillWindow();
        testVermillionBurstGateRefreshAndSwitchDispel();
        testWrongAndNullCallbacks();
        testBindingAndInstanceIsolation();
        testOwnerBuffSnapshotRestore();
        System.out.println("ActionWindowArtifactRegressionTest passed");
    }

    /** Verifies exact fixed stats, supplied-container retention, and inactive upgrades. */
    private static void testMetadataAndSuppliedStats() {
        ADayCarvedFromRisingWinds risingWinds =
                new ADayCarvedFromRisingWinds();
        NighttimeWhispersInTheEchoingWoods nighttime =
                new NighttimeWhispersInTheEchoingWoods();
        VermillionHereafter vermillion = new VermillionHereafter();

        assertEquals("A Day Carved From Rising Winds", risingWinds.getName(),
                "Rising Winds name");
        assertEquals("Nighttime Whispers in the Echoing Woods",
                nighttime.getName(), "Nighttime Whispers name");
        assertEquals("Vermillion Hereafter", vermillion.getName(),
                "Vermillion name");
        assertClose(0.18, risingWinds.getStats().get(StatType.ATK_PERCENT),
                "Rising Winds fixed ATK");
        assertClose(0.18, nighttime.getStats().get(StatType.ATK_PERCENT),
                "Nighttime Whispers fixed ATK");
        assertClose(0.18, vermillion.getStats().get(StatType.ATK_PERCENT),
                "Vermillion fixed ATK");
        assertClose(0.0, risingWinds.getStats().get(StatType.CRIT_RATE),
                "Witch's Homework CRIT upgrade should remain inactive");
        assertClose(0.0, nighttime.getStats().get(StatType.GEO_DMG_BONUS),
                "Nighttime Geo window should not be a fixed stat");

        assertSuppliedStatsPreserved(
                new ADayCarvedFromRisingWinds(suppliedStats()),
                "Rising Winds");
        assertSuppliedStatsPreserved(
                new NighttimeWhispersInTheEchoingWoods(suppliedStats()),
                "Nighttime Whispers");
        assertSuppliedStatsPreserved(
                new VermillionHereafter(suppliedStats()),
                "Vermillion");

        assertNullStatsRejected(
                () -> new ADayCarvedFromRisingWinds(null), "Rising Winds");
        assertNullStatsRejected(
                () -> new NighttimeWhispersInTheEchoingWoods(null),
                "Nighttime Whispers");
        assertNullStatsRejected(
                () -> new VermillionHereafter(null), "Vermillion");
    }

    /**
     * Verifies Rising Winds accepts sourced hit categories, including zero
     * damage, but does not apply to the hit that starts its window.
     */
    private static void testADayTriggerCategoriesAndOrdering() {
        ADayCarvedFromRisingWinds artifact =
                new ADayCarvedFromRisingWinds();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        Enemy enemy = new Enemy(90);

        AttackAction normal = attack("Rising Winds Normal", 1.0,
                ActionType.NORMAL);
        double triggerDamage = DamageCalculator.calculateDamage(
                owner, enemy, normal, Collections.emptyList(),
                sim.getCurrentTime(), 1.0, sim);
        assertClose(531.0, triggerDamage,
                "Rising Winds trigger hit should use only fixed ATK");
        double followingDamage = DamageCalculator.calculateDamage(
                owner, enemy, normal, Collections.emptyList(),
                sim.getCurrentTime(), 1.0, sim);
        assertClose(643.5, followingDamage,
                "Rising Winds following hit should use the active window");
        assertClose(0.43, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds exact fixed plus dynamic ATK");
        assertClose(0.0, effectiveStat(owner, sim, StatType.CRIT_RATE),
                "Rising Winds unsupported CRIT upgrade");

        ActionType[] eligibleTypes = {
                ActionType.NORMAL,
                ActionType.CHARGE,
                ActionType.SKILL,
                ActionType.BURST
        };
        for (ActionType actionType : eligibleTypes) {
            owner.removeBuff(BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC);
            artifact.onDamage(sim, attack("Eligible", 0.0, actionType),
                    0.0, owner);
            assertActiveBuffCount(owner,
                    BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                    sim.getCurrentTime(), 1,
                    "Rising Winds eligible zero-damage " + actionType);
        }

        owner.removeBuff(BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC);
        AttackAction skillClassified = attack(
                "Skill-classified follow-up", 0.0, ActionType.OTHER);
        skillClassified.setCountsAsSkillDmg(true);
        artifact.onDamage(sim, skillClassified, 0.0, owner);
        assertActiveBuffCount(owner,
                BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                sim.getCurrentTime(), 1,
                "Rising Winds Skill-damage classification");

        owner.removeBuff(BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC);
        artifact.onDamage(sim, attack("Plunge", 1.0, ActionType.PLUNGE),
                100.0, owner);
        artifact.onDamage(sim, attack("Other", 1.0, ActionType.OTHER),
                100.0, owner);
        assertActiveBuffCount(owner,
                BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                sim.getCurrentTime(), 0,
                "Rising Winds unsupported hit categories");

        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertActiveBuffCount(owner,
                BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                sim.getCurrentTime(), 0,
                "Rising Winds should not trigger on non-hitting action use");
    }

    /** Verifies Rising Winds half-open timing, exact refresh, and off-field hits. */
    private static void testADayTimingRefreshAndOffFieldTrigger() {
        ADayCarvedFromRisingWinds artifact =
                new ADayCarvedFromRisingWinds();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction normal = attack("Off-field Normal", 0.0,
                ActionType.NORMAL);

        sim.switchCharacter(ally.getCharacterId());
        DamageCalculator.calculateDamage(
                owner, new Enemy(90), normal, Collections.emptyList(),
                sim.getCurrentTime(), 1.0, sim);
        assertClose(0.43, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds off-field owner hit");
        double firstStart = sim.getCurrentTime();
        sim.advanceTime(5.0);
        artifact.onDamage(sim, normal, 0.0, owner);
        assertActiveBuffCount(owner,
                BuffId.A_DAY_CARVED_FROM_RISING_WINDS_4PC,
                sim.getCurrentTime(), 1,
                "Rising Winds refresh should replace instead of stack");
        sim.advanceTime(5.999);
        assertClose(0.43, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds pre-expiry boundary");
        sim.advanceTime(0.001);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds at-expiry boundary");
        assertTrue(sim.getCurrentTime() >= firstStart + 6.0,
                "Rising Winds refresh should outlive the original window");
    }

    /** Verifies accepted Skill dispatch, exact Geo value, refresh, and boundaries. */
    private static void testNighttimeAcceptedSkillWindow() {
        NighttimeWhispersInTheEchoingWoods artifact =
                new NighttimeWhispersInTheEchoingWoods();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);

        artifact.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.NORMAL), sim);
        assertClose(0.0, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime non-Skill callback");
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertClose(0.20, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime exact Geo window");
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Nighttime exact fixed ATK");
        assertClose(0.20, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime unsupported 150% enhancement should be zero");

        sim.advanceTime(5.0);
        artifact.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        assertActiveBuffCount(owner,
                BuffId.NIGHTTIME_WHISPERS_IN_THE_ECHOING_WOODS_4PC,
                sim.getCurrentTime(), 1,
                "Nighttime refresh should replace instead of stack");
        sim.advanceTime(9.999);
        assertClose(0.20, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime pre-expiry boundary");
        sim.advanceTime(0.001);
        assertClose(0.0, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime at-expiry boundary");
    }

    /** Verifies Burst gating, exact Nascent Light, replacement, expiry, and dispel. */
    private static void testVermillionBurstGateRefreshAndSwitchDispel() {
        VermillionHereafter artifact = new VermillionHereafter();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter ally = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, ally);

        owner.restoreCurrentEnergy(0.0);
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rejected Burst should not start Nascent Light");

        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertClose(0.26, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion exact fixed plus Nascent Light ATK");
        assertClose(0.26, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion unsupported HP-loss stacks should be zero");

        sim.advanceTime(5.0);
        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertActiveBuffCount(owner,
                BuffId.VERMILLION_HEREAFTER_NASCENT_LIGHT,
                sim.getCurrentTime(), 1,
                "Vermillion recast should replace instead of stack");
        sim.advanceTime(15.999);
        assertClose(0.26, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion pre-expiry boundary");
        sim.advanceTime(0.001);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion at-expiry boundary");

        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        sim.performAction(owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
        sim.switchCharacter(ally.getCharacterId());
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion owner switch-out should dispel Nascent Light");
        artifact.onSwitchIn(sim, owner);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion switch-in should not restore Nascent Light");
    }

    /** Verifies unbound, wrong-owner, wrong-simulator, and null callbacks are inert. */
    private static void testWrongAndNullCallbacks() {
        CombatSimulator unrelated = new CombatSimulator();
        unrelated.setLoggingEnabled(false);
        AttackAction normal = attack("Guard Normal", 0.0, ActionType.NORMAL);

        ADayCarvedFromRisingWinds unboundDay =
                new ADayCarvedFromRisingWinds();
        unboundDay.onDamage(unrelated, normal, 0.0, null);
        NighttimeWhispersInTheEchoingWoods unboundNight =
                new NighttimeWhispersInTheEchoingWoods();
        unboundNight.onAction(null, null, null);
        VermillionHereafter unboundVermillion = new VermillionHereafter();
        unboundVermillion.onBurst(null);
        unboundVermillion.onSwitchOut(null, null);

        ADayCarvedFromRisingWinds day = new ADayCarvedFromRisingWinds();
        NighttimeWhispersInTheEchoingWoods night =
                new NighttimeWhispersInTheEchoingWoods();
        VermillionHereafter vermillion = new VermillionHereafter();
        TestCharacter owner = character(
                CharacterId.SUCROSE, day, night, vermillion);
        TestCharacter wrongOwner = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, wrongOwner);

        day.onDamage(sim, null, 0.0, owner);
        day.onDamage(sim, normal, 0.0, wrongOwner);
        day.onDamage(unrelated, normal, 0.0, owner);
        day.onDamage(null, normal, 0.0, owner);
        night.onAction(owner, null, sim);
        night.onAction(wrongOwner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        night.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), unrelated);
        night.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), null);
        vermillion.onBurst(unrelated);
        vermillion.onBurst(null);
        vermillion.onSwitchOut(sim, wrongOwner);
        vermillion.onSwitchOut(unrelated, owner);
        vermillion.onSwitchOut(null, owner);
        vermillion.onSwitchOut(sim, null);

        assertClose(0.54, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Wrong callbacks should not create ATK windows");
        assertClose(0.0, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Wrong callbacks should not create Geo windows");
    }

    /** Verifies idempotent binding, cross-binding rejection, and independent copies. */
    private static void testBindingAndInstanceIsolation() {
        assertBindingGuards(new ADayCarvedFromRisingWinds(), "Rising Winds");
        assertBindingGuards(
                new NighttimeWhispersInTheEchoingWoods(),
                "Nighttime Whispers");
        assertBindingGuards(new VermillionHereafter(), "Vermillion");

        ADayCarvedFromRisingWinds firstDay =
                new ADayCarvedFromRisingWinds();
        ADayCarvedFromRisingWinds secondDay =
                new ADayCarvedFromRisingWinds();
        TestCharacter firstDayOwner = character(CharacterId.SUCROSE, firstDay);
        TestCharacter secondDayOwner = character(CharacterId.AMBER, secondDay);
        CombatSimulator firstDaySim = simulatorWith(firstDayOwner);
        CombatSimulator secondDaySim = simulatorWith(secondDayOwner);
        firstDay.onDamage(firstDaySim,
                attack("Independent", 0.0, ActionType.NORMAL),
                0.0, firstDayOwner);
        assertClose(0.43, effectiveStat(
                firstDayOwner, firstDaySim, StatType.ATK_PERCENT),
                "First Rising Winds instance");
        assertClose(0.18, effectiveStat(
                secondDayOwner, secondDaySim, StatType.ATK_PERCENT),
                "Second Rising Winds instance should remain independent");

        NighttimeWhispersInTheEchoingWoods firstNight =
                new NighttimeWhispersInTheEchoingWoods();
        NighttimeWhispersInTheEchoingWoods secondNight =
                new NighttimeWhispersInTheEchoingWoods();
        TestCharacter firstNightOwner = character(CharacterId.SUCROSE, firstNight);
        TestCharacter secondNightOwner = character(CharacterId.AMBER, secondNight);
        CombatSimulator firstNightSim = simulatorWith(firstNightOwner);
        CombatSimulator secondNightSim = simulatorWith(secondNightOwner);
        firstNight.onAction(firstNightOwner,
                CharacterActionRequest.of(CharacterActionKey.SKILL),
                firstNightSim);
        assertClose(0.20, effectiveStat(
                firstNightOwner, firstNightSim, StatType.GEO_DMG_BONUS),
                "First Nighttime instance");
        assertClose(0.0, effectiveStat(
                secondNightOwner, secondNightSim, StatType.GEO_DMG_BONUS),
                "Second Nighttime instance should remain independent");

        VermillionHereafter firstVermillion = new VermillionHereafter();
        VermillionHereafter secondVermillion = new VermillionHereafter();
        TestCharacter firstVermillionOwner = character(
                CharacterId.SUCROSE, firstVermillion);
        TestCharacter secondVermillionOwner = character(
                CharacterId.AMBER, secondVermillion);
        CombatSimulator firstVermillionSim = simulatorWith(firstVermillionOwner);
        CombatSimulator secondVermillionSim = simulatorWith(secondVermillionOwner);
        firstVermillion.onBurst(firstVermillionSim);
        assertClose(0.26, effectiveStat(
                firstVermillionOwner, firstVermillionSim,
                StatType.ATK_PERCENT), "First Vermillion instance");
        assertClose(0.18, effectiveStat(
                secondVermillionOwner, secondVermillionSim,
                StatType.ATK_PERCENT),
                "Second Vermillion instance should remain independent");
    }

    /** Verifies all three owner-buff windows survive simulator snapshot restore. */
    private static void testOwnerBuffSnapshotRestore() {
        testADaySnapshotRestore();
        testNighttimeSnapshotRestore();
        testVermillionSnapshotRestore();
    }

    /** Verifies Rising Winds owner-buff timing is restored exactly. */
    private static void testADaySnapshotRestore() {
        ADayCarvedFromRisingWinds artifact =
                new ADayCarvedFromRisingWinds();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        artifact.onDamage(sim, attack("Snapshot", 0.0, ActionType.NORMAL),
                0.0, owner);
        sim.advanceTime(1.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(6.0);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds should expire before restore");
        sim.restoreSnapshot(snapshot);
        assertClose(0.43, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds snapshot restore");
        sim.advanceTime(4.999);
        assertClose(0.43, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds restored pre-expiry");
        sim.advanceTime(0.001);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Rising Winds restored expiry");
    }

    /** Verifies Nighttime owner-buff timing is restored exactly. */
    private static void testNighttimeSnapshotRestore() {
        NighttimeWhispersInTheEchoingWoods artifact =
                new NighttimeWhispersInTheEchoingWoods();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        artifact.onAction(owner,
                CharacterActionRequest.of(CharacterActionKey.SKILL), sim);
        sim.advanceTime(1.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(10.0);
        assertClose(0.0, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime should expire before restore");
        sim.restoreSnapshot(snapshot);
        assertClose(0.20, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime snapshot restore");
        sim.advanceTime(8.999);
        assertClose(0.20, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime restored pre-expiry");
        sim.advanceTime(0.001);
        assertClose(0.0, effectiveStat(owner, sim, StatType.GEO_DMG_BONUS),
                "Nighttime restored expiry");
    }

    /** Verifies Vermillion owner-buff timing is restored exactly. */
    private static void testVermillionSnapshotRestore() {
        VermillionHereafter artifact = new VermillionHereafter();
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        CombatSimulator sim = simulatorWith(owner);
        artifact.onBurst(sim);
        sim.advanceTime(1.0);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(16.0);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion should expire before restore");
        sim.restoreSnapshot(snapshot);
        assertClose(0.26, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion snapshot restore");
        sim.advanceTime(14.999);
        assertClose(0.26, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion restored pre-expiry");
        sim.advanceTime(0.001);
        assertClose(0.18, effectiveStat(owner, sim, StatType.ATK_PERCENT),
                "Vermillion restored expiry");
    }

    /** Verifies supplied containers retain caller stats and receive fixed ATK. */
    private static void assertSuppliedStatsPreserved(
            ArtifactSet artifact,
            String label) {
        assertClose(17.0, artifact.getStats().get(StatType.ELEMENTAL_MASTERY),
                label + " supplied EM");
        assertClose(0.23, artifact.getStats().get(StatType.ATK_PERCENT),
                label + " supplied plus fixed ATK");
    }

    /** Creates one supplied-stat fixture. */
    private static StatsContainer suppliedStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ELEMENTAL_MASTERY, 17.0);
        stats.set(StatType.ATK_PERCENT, 0.05);
        return stats;
    }

    /** Verifies one constructor rejects null supplied stats. */
    private static void assertNullStatsRejected(
            Runnable constructor,
            String label) {
        boolean rejected = false;
        try {
            constructor.run();
        } catch (NullPointerException expected) {
            rejected = true;
        }
        assertTrue(rejected, label + " should reject null supplied stats");
    }

    /** Verifies one artifact's idempotent and exclusive binding contract. */
    private static void assertBindingGuards(
            ArtifactSet artifact,
            String label) {
        TestCharacter owner = character(CharacterId.SUCROSE, artifact);
        TestCharacter wrongOwner = character(CharacterId.AMBER);
        CombatSimulator sim = simulatorWith(owner, wrongOwner);
        CombatSimulator wrongSimulator = new CombatSimulator();
        wrongSimulator.setLoggingEnabled(false);

        initialize(artifact, owner, sim);
        initialize(artifact, owner, sim);

        boolean ownerRejected = false;
        try {
            initialize(artifact, wrongOwner, sim);
        } catch (IllegalStateException expected) {
            ownerRejected = true;
        }
        assertTrue(ownerRejected, label + " should reject cross-owner reuse");

        boolean simulatorRejected = false;
        try {
            initialize(artifact, owner, wrongSimulator);
        } catch (IllegalStateException expected) {
            simulatorRejected = true;
        }
        assertTrue(simulatorRejected,
                label + " should reject cross-simulator reuse");

        ArtifactSet fresh = freshCopy(artifact);
        boolean nullOwnerRejected = false;
        try {
            initialize(fresh, null, sim);
        } catch (IllegalArgumentException expected) {
            nullOwnerRejected = true;
        }
        assertTrue(nullOwnerRejected, label + " should reject a null owner");

        boolean nullSimulatorRejected = false;
        try {
            initialize(fresh, owner, null);
        } catch (IllegalArgumentException expected) {
            nullSimulatorRejected = true;
        }
        assertTrue(nullSimulatorRejected,
                label + " should reject a null simulator");
    }

    /** Initializes one of the three artifact types through its focused capability. */
    private static void initialize(
            ArtifactSet artifact,
            Character owner,
            CombatSimulator sim) {
        if (artifact instanceof ADayCarvedFromRisingWinds) {
            ((ADayCarvedFromRisingWinds) artifact)
                    .initializeForSimulator(owner, sim, true);
            return;
        }
        if (artifact instanceof NighttimeWhispersInTheEchoingWoods) {
            ((NighttimeWhispersInTheEchoingWoods) artifact)
                    .initializeForSimulator(owner, sim, true);
            return;
        }
        if (artifact instanceof VermillionHereafter) {
            ((VermillionHereafter) artifact)
                    .initializeForSimulator(owner, sim, true);
            return;
        }
        throw new IllegalArgumentException("Unsupported artifact fixture");
    }

    /** Creates a fresh artifact of the same supported fixture type. */
    private static ArtifactSet freshCopy(ArtifactSet artifact) {
        if (artifact instanceof ADayCarvedFromRisingWinds) {
            return new ADayCarvedFromRisingWinds();
        }
        if (artifact instanceof NighttimeWhispersInTheEchoingWoods) {
            return new NighttimeWhispersInTheEchoingWoods();
        }
        if (artifact instanceof VermillionHereafter) {
            return new VermillionHereafter();
        }
        throw new IllegalArgumentException("Unsupported artifact fixture");
    }

    /** Creates an attack fixture with explicit sourced hit category. */
    private static AttackAction attack(
            String name,
            double multiplier,
            ActionType actionType) {
        return new AttackAction(
                name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
    }

    /** Creates a quiet simulator containing the supplied party in order. */
    private static CombatSimulator simulatorWith(TestCharacter... party) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        for (TestCharacter character : party) {
            sim.addCharacter(character);
        }
        return sim;
    }

    /** Creates a deterministic character with optional artifacts. */
    private static TestCharacter character(
            CharacterId characterId,
            ArtifactSet... artifacts) {
        return new TestCharacter(characterId, artifacts);
    }

    /** Returns one live effective stat at the simulator's current time. */
    private static double effectiveStat(
            Character character,
            CombatSimulator sim,
            StatType statType) {
        return character.getEffectiveStats(sim.getCurrentTime()).get(statType);
    }

    /** Verifies the active count of one typed owner buff. */
    private static void assertActiveBuffCount(
            Character owner,
            BuffId buffId,
            double currentTime,
            int expected,
            String message) {
        int count = 0;
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == buffId && !buff.isExpired(currentTime)) {
                count++;
            }
        }
        if (count != expected) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + count);
        }
    }

    /** Asserts two floating-point values are equal within test tolerance. */
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

    /** Asserts a boolean condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal deterministic character for artifact callback and action-gate checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                ArtifactSet... equippedArtifacts) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.GEO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = equippedArtifacts;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 1.0);
            setSkillCD(0.0);
            setBurstCD(0.0);
        }

        /** Applies no fixture-specific passive stats. */
        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        /** Returns the deterministic Burst cost used by the gate check. */
        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        /** Records accepted Skill and Burst use without emitting a damage hit. */
        @Override
        public void onAction(
                CharacterActionRequest request,
                CombatSimulator sim) {
            if (request.getKey() == CharacterActionKey.SKILL) {
                markSkillUsed(sim.getCurrentTime());
            } else if (request.getKey() == CharacterActionKey.BURST) {
                markBurstUsed(sim.getCurrentTime());
            }
        }
    }
}
