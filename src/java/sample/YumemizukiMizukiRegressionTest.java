package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.YumemizukiMizuki;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/**
 * Source-backed regression checks for Mizuki's Dreamdrifter slice.
 *
 * <p>The executable pins aligned CSV data, fixed-target attacks, Skill launch
 * and impact timing, particles, private ICD, A1/A4, representable
 * constellations, Burst delays, rollback, switching, and fail-closed omitted
 * systems.</p>
 */
public final class YumemizukiMizukiRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private YumemizukiMizukiRegressionTest() {
    }

    /** Runs every focused Mizuki regression assertion. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndPrivateIcd();
        testCatalystBasics();
        testDreamdrifterCadenceParticlesAndCancel();
        testA1A4AndConstellationSupport();
        testStellarSwirlRoutingWindowsAndSnapshot();
        testBurstTalentLevelsAndNoEnemyBoundary();
        testRollbackSwitchAndUnsupportedBoundaries();
        System.out.println("YumemizukiMizukiRegressionTest passed");
    }

    private static void testIdentityDataAndPrivateIcd()
            throws IOException {
        YumemizukiMizuki mizuki = new YumemizukiMizuki(
                null, null, 6);
        assertEquals(CharacterId.YUMEMIZUKI_MIZUKI,
                mizuki.getCharacterId(),
                "Mizuki typed identity");
        assertEquals(CharacterId.YUMEMIZUKI_MIZUKI,
                CharacterId.fromName("Yumemizuki Mizuki"),
                "Mizuki display-name lookup");
        assertEquals(CharacterId.YUMEMIZUKI_MIZUKI,
                CharacterId.fromNumericId(97),
                "Mizuki numeric lookup");
        assertEquals(97,
                CharacterId.YUMEMIZUKI_MIZUKI.getNumericId(),
                "Mizuki stable numeric id");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.YUMEMIZUKI_MIZUKI.getRegion(),
                "Mizuki region");
        assertEquals(Element.ANEMO, mizuki.getElement(),
                "Mizuki element");
        assertClose(12736.0,
                mizuki.getBaseStats().get(StatType.BASE_HP),
                "Mizuki Base HP");
        assertClose(215.0,
                mizuki.getBaseStats().get(StatType.BASE_ATK),
                "Mizuki Base ATK");
        assertClose(757.0,
                mizuki.getBaseStats().get(StatType.BASE_DEF),
                "Mizuki Base DEF");
        assertClose(115.2,
                mizuki.getBaseStats().get(
                        StatType.ELEMENTAL_MASTERY),
                "Mizuki ascension EM");
        assertClose(60.0, mizuki.getEnergyCost(),
                "Mizuki Burst cost");
        assertClose(15.0, mizuki.getSkillCD(),
                "Mizuki Skill cooldown");
        assertClose(15.0, mizuki.getBurstCD(),
                "Mizuki Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.YUMEMIZUKI_MIZUKI,
                    new YumemizukiMizuki(
                            null, null, constellation).getCharacterId(),
                    "Mizuki explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/YumemizukiMizuki/"
                        + "YumemizukiMizuki_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/YumemizukiMizuki/"
                        + "YumemizukiMizuki_Multipliers.csv"), 53);
        assertCsvValue("Dreamdrifter Continuous Damage C3", 0.898240);
        assertCsvValue("Burst Activation Damage C5", 1.881600);
        assertCsvValue("Cloud Launch Interval Frames", 45.0);
        assertCsvValue("Dreamdrifter Stellar-Swirl Bonus", 0.000420);
        assertCsvValue("Dreamdrifter Stellar-Swirl Bonus C3", 0.000510);
        assertCsvValue("C1 Stellar-Swirl EM Flat Multiplier", 5.5);
        assertCsvValue("C6 Stellar-Swirl CRIT Rate", 0.10);
        assertCsvValue("C6 Stellar-Swirl CRIT DMG", 0.20);

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "YUMEMIZUKI_MIZUKI",
                ICDTag.YumemizukiMizuki_Dreamdrifter,
                ICDType.YumemizukiMizukiDreamdrifter,
                0.0),
                "First Dreamdrifter application passes");
        assertTrue(!manager.checkApplication(
                "YUMEMIZUKI_MIZUKI",
                ICDTag.YumemizukiMizuki_Dreamdrifter,
                ICDType.YumemizukiMizukiDreamdrifter,
                1.2 - EPSILON),
                "Dreamdrifter ICD blocks before 1.2 seconds");
        assertTrue(manager.checkApplication(
                "YUMEMIZUKI_MIZUKI",
                ICDTag.YumemizukiMizuki_Dreamdrifter,
                ICDType.YumemizukiMizukiDreamdrifter,
                1.2),
                "Dreamdrifter ICD opens at 1.2 seconds");
    }

    private static void testCatalystBasics() {
        YumemizukiMizuki mizuki = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator simulator = simulatorWith(mizuki);
        List<ActionRecord> records = captureMizukiActions(simulator);
        int[] impactFrames = { 17, 29, 47 };
        int[] durationFrames = { 34, 38, 98 };
        double[] multipliers = { 0.888706, 0.797545, 1.213270 };
        for (int step = 0; step < 3; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = named(
                    records,
                    "Pure Heart, Pure Dreams N" + (step + 1)).get(0);
            assertClose(castTime + impactFrames[step] * FRAME,
                    record.time,
                    "Mizuki N" + (step + 1) + " impact");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Mizuki N" + (step + 1) + " multiplier");
            assertEquals(Element.ANEMO,
                    record.action.getElement(),
                    "Mizuki catalyst Normal element");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Mizuki Normal ICD tag");
            assertClose(castTime + durationFrames[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Mizuki N" + (step + 1) + " duration");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(records,
                "Pure Heart, Pure Dreams Charged Attack").get(0);
        assertClose(chargedCast + 39.0 * FRAME,
                charged.time,
                "Mizuki Charged impact");
        assertClose(2.21, charged.action.getDamagePercent(),
                "Mizuki Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Mizuki Charged has no ICD");
        assertClose(chargedCast + 81.0 * FRAME,
                simulator.getCurrentTime(),
                "Mizuki Charged duration");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records,
                "Pure Heart, Pure Dreams High Plunge").get(0);
        assertClose(plungeCast, plunge.time,
                "Repository fixed high-Plunge impact");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Mizuki high-Plunge multiplier");
        assertClose(plungeCast + 60.0 * FRAME,
                simulator.getCurrentTime(),
                "Repository fixed high-Plunge duration");
    }

    private static void testDreamdrifterCadenceParticlesAndCancel() {
        YumemizukiMizuki mizuki = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator simulator = simulatorWith(mizuki);
        List<ActionRecord> records = captureMizukiActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        assertClose(50.0 * FRAME, simulator.getCurrentTime(),
                "Dreamdrifter Skill duration");
        assertTrue(mizuki.isDreamDrifterActive(
                simulator.getCurrentTime()),
                "Dreamdrifter remains active after recovery");
        ActionRecord activation = named(
                records, "Aisa Utamakura Pilgrimage").get(0);
        assertClose(2.0 * FRAME, activation.time,
                "Dreamdrifter activation impact");
        assertClose(0.981648,
                activation.action.getDamagePercent(),
                "Dreamdrifter activation Talent-9 multiplier");
        assertEquals(ICDType.None, activation.action.getICDType(),
                "Dreamdrifter activation has no ICD");
        ActionRecord firstCloud = named(
                records, "Dreamdrifter Continuous Attack").get(0);
        assertClose(48.0 * FRAME, firstCloud.time,
                "First cloud includes launch and travel");
        assertClose(0.763504,
                firstCloud.action.getDamagePercent(),
                "Cloud Talent-9 multiplier");
        assertEquals(ICDType.YumemizukiMizukiDreamdrifter,
                firstCloud.action.getICDType(),
                "Cloud private ICD type");
        assertEquals(ICDTag.YumemizukiMizuki_Dreamdrifter,
                firstCloud.action.getICDTag(),
                "Cloud private ICD tag");
        assertTrue(firstCloud.action.hasStatSnapshot(),
                "Cloud owns cast-time stats");
        assertClose(0.0,
                mizuki.getSkillCDRemaining(
                        simulator.getCurrentTime()),
                "Active Dreamdrifter exposes its cancel input");
        assertEquals(0, particles.size(),
                "Particle packets remain in flight at recovery");

        advanceTo(simulator, 238.0 * FRAME + EPSILON);
        List<ActionRecord> clouds = named(
                records, "Dreamdrifter Continuous Attack");
        assertEquals(5, clouds.size(),
                "Five clouds impact through frame 228");
        int[] cloudImpactFrames = { 48, 93, 138, 183, 228 };
        for (int index = 0; index < cloudImpactFrames.length; index++) {
            assertClose(cloudImpactFrames[index] * FRAME,
                    clouds.get(index).time,
                    "Cloud impact " + index);
        }
        assertEquals(4, particles.size(),
                "Skill emits exactly four particle packets");
        int[] particleFrames = { 102, 148, 193, 238 };
        for (int index = 0; index < particleFrames.length; index++) {
            assertClose(1.0, particles.get(index).count,
                    "Mizuki particle packet size " + index);
            assertClose(particleFrames[index] * FRAME,
                    particles.get(index).time,
                    "Mizuki particle arrival " + index);
        }
        assertEquals(0, mizuki.getParticleGenerationsRemaining(),
                "Particle generation cap is exhausted");

        YumemizukiMizuki cancel = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator cancelSimulator = simulatorWith(cancel);
        performSkill(cancelSimulator);
        assertThrows(IllegalStateException.class,
                () -> perform(
                        cancelSimulator, CharacterActionKey.NORMAL),
                "Dreamdrifter blocks Normal input");
        performSkill(cancelSimulator);
        assertTrue(!cancel.isDreamDrifterActive(
                cancelSimulator.getCurrentTime()),
                "Second Skill input cancels Dreamdrifter");
        assertClose(15.0 - 77.0 * FRAME,
                cancel.getSkillCDRemaining(
                        cancelSimulator.getCurrentTime()),
                "Cancel reveals cooldown started at frame 23");
    }

    private static void testA1A4AndConstellationSupport() {
        ReactionResult swirl = ReactionResult.transform(
                1000.0,
                "Swirl (Pyro)",
                ReactionResult.Kind.SWIRL,
                Element.PYRO,
                Element.PYRO);
        YumemizukiMizuki a1 = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator a1Simulator = simulatorWith(a1);
        performSkill(a1Simulator);
        double firstReactionTime = a1Simulator.getCurrentTime();
        a1.onReaction(swirl, a1, firstReactionTime, a1Simulator);
        assertClose(7.5, a1.getDreamDrifterExpirationTime(),
                "A1 first Swirl extends by 2.5 seconds");
        a1.onReaction(swirl, a1,
                firstReactionTime + 0.3 - EPSILON, a1Simulator);
        assertClose(7.5, a1.getDreamDrifterExpirationTime(),
                "A1 extension ICD blocks early Swirl");
        a1.onReaction(swirl, a1,
                firstReactionTime + 0.3, a1Simulator);
        assertClose(10.0, a1.getDreamDrifterExpirationTime(),
                "A1 second extension opens at 0.3 seconds");
        assertEquals(0, a1.getDreamDrifterExtensionsRemaining(),
                "A1 extension count is capped at two");
        a1.onReaction(swirl, a1,
                firstReactionTime + 0.6, a1Simulator);
        assertClose(10.0, a1.getDreamDrifterExpirationTime(),
                "A1 third Swirl cannot extend the state");

        YumemizukiMizuki a4 = new YumemizukiMizuki(
                null, null, 0);
        TestCharacter pyroAlly = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator a4Simulator = simulatorWith(a4, pyroAlly);
        performSkill(a4Simulator);
        performAllyHit(a4Simulator, pyroAlly, Element.PYRO);
        assertClose(215.2,
                a4.getEffectiveStats(
                        a4Simulator.getCurrentTime()).get(
                                StatType.ELEMENTAL_MASTERY),
                "A4 grants 100 EM after an ally PHEC hit");
        performAllyHit(a4Simulator, pyroAlly, Element.PHYSICAL);
        assertClose(215.2,
                a4.getEffectiveStats(
                        a4Simulator.getCurrentTime()).get(
                                StatType.ELEMENTAL_MASTERY),
                "A4 ignores Physical ally hits");

        AttackAction anemoHit = testAction(Element.ANEMO);
        YumemizukiMizuki c1 = new YumemizukiMizuki(
                null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(
                c1,
                new TestCharacter(CharacterId.SUCROSE, Element.ANEMO));
        performSkill(c1Simulator);
        Character c1Ally = c1Simulator.getCharacter(CharacterId.SUCROSE);
        StatsContainer c1Stats = new StatsContainer();
        c1Stats.set(StatType.STELLAR_SWIRL_BASE_DMG_BONUS, 0.25);
        c1.applyTargetDependentTeamStats(
                c1Stats,
                c1Ally,
                c1Simulator.getEnemy(),
                anemoHit,
                c1Simulator.getCurrentTime());
        double baseSwirlBonus = 0.0042 * 115.2;
        double c1Equivalent = 11.0 * 115.2 / (1446.85 * 0.6);
        double baseStellarSwirlBonus = 0.00042 * 115.2;
        double c1StellarEquivalent = 5.5 * 115.2
                / (1446.85 * 0.75 * 1.25);
        assertClose(baseSwirlBonus + c1Equivalent,
                c1Stats.get(StatType.SWIRL_DMG_BONUS),
                "C1 flat Swirl addition uses its exact equivalent bonus");
        assertClose(baseStellarSwirlBonus + c1StellarEquivalent,
                c1Stats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "C1 flat Stellar-Swirl addition accounts for its base section");
        c1.onReaction(swirl, c1Ally,
                c1Simulator.getCurrentTime(), c1Simulator);
        StatsContainer consumedStats = new StatsContainer();
        c1.applyTargetDependentTeamStats(
                consumedStats,
                c1Ally,
                c1Simulator.getEnemy(),
                anemoHit,
                c1Simulator.getCurrentTime());
        assertClose(baseSwirlBonus,
                consumedStats.get(StatType.SWIRL_DMG_BONUS),
                "C1 awaiting mark is consumed by one Swirl");
        assertClose(baseStellarSwirlBonus,
                consumedStats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Ordinary Swirl consumption removes Stellar C1 independently");

        YumemizukiMizuki c2 = new YumemizukiMizuki(
                null, null, 2);
        TestCharacter hydroAlly = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator c2Simulator = simulatorWith(c2, hydroAlly);
        performSkill(c2Simulator);
        c2.onReaction(swirl, hydroAlly,
                c2Simulator.getCurrentTime(), c2Simulator);
        StatsContainer c2Stats = new StatsContainer();
        c2.applyTargetDependentTeamStats(
                c2Stats,
                hydroAlly,
                c2Simulator.getEnemy(),
                testAction(Element.HYDRO),
                c2Simulator.getCurrentTime());
        assertClose(0.0004 * 115.2,
                c2.getC2ElementalDamageBonus(),
                "C2 snapshots cast-time Mizuki EM");
        assertClose(0.0004 * 115.2,
                c2Stats.get(StatType.HYDRO_DMG_BONUS),
                "C2 grants Hydro damage to another party member");
        StatsContainer c2OwnerStats = new StatsContainer();
        c2.applyTargetDependentTeamStats(
                c2OwnerStats,
                c2,
                c2Simulator.getEnemy(),
                testAction(Element.ANEMO),
                c2Simulator.getCurrentTime());
        assertClose(0.0,
                c2OwnerStats.get(StatType.HYDRO_DMG_BONUS),
                "C2 excludes Mizuki herself");

        YumemizukiMizuki c6 = new YumemizukiMizuki(
                null, null, 6);
        TestCharacter cryoAlly = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator c6Simulator = simulatorWith(c6, cryoAlly);
        performSkill(c6Simulator);
        c6.onReaction(swirl, cryoAlly,
                c6Simulator.getCurrentTime(), c6Simulator);
        StatsContainer c6Stats = new StatsContainer();
        c6.applyTargetDependentTeamStats(
                c6Stats,
                cryoAlly,
                c6Simulator.getEnemy(),
                anemoHit,
                c6Simulator.getCurrentTime());
        assertClose(0.0051 * 115.2 + 0.30,
                c6Stats.get(StatType.SWIRL_DMG_BONUS),
                "C3 Skill and deterministic C6 expected Swirl bonus");
        assertClose(0.00051 * 115.2,
                c6Stats.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "C3 Skill routes only its Stellar-Swirl bonus");
        assertClose(0.10,
                c6Stats.get(StatType.STELLAR_SWIRL_CRIT_RATE),
                "C6 routes Stellar-Swirl CRIT Rate independently");
        assertClose(0.20,
                c6Stats.get(StatType.STELLAR_SWIRL_CRIT_DMG),
                "C6 routes Stellar-Swirl CRIT DMG independently");
        assertClose(0.0,
                c6Stats.get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                "Mizuki Swirl support does not leak into Conduct damage");
        assertClose(0.0,
                c6Stats.get(StatType.STELLAR_CONDUCT_CRIT_RATE),
                "Mizuki C6 does not leak into Conduct CRIT Rate");
        assertClose(0.0,
                c6Stats.get(StatType.STELLAR_CONDUCT_CRIT_DMG),
                "Mizuki C6 does not leak into Conduct CRIT DMG");
    }

    private static void testStellarSwirlRoutingWindowsAndSnapshot() {
        ReactionResult stellarSwirl = stellar(
                ReactionResult.Kind.STELLAR_SWIRL, 1000.0);
        ReactionResult stellarConduct = stellar(
                ReactionResult.Kind.STELLAR_CONDUCT, 1000.0);
        ReactionResult overload = ReactionResult.transform(
                1000.0,
                "Overload",
                ReactionResult.Kind.OVERLOAD,
                Element.PYRO,
                Element.PYRO);

        YumemizukiMizuki a1 = new YumemizukiMizuki(
                null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.SUCROSE, Element.ANEMO);
        CombatSimulator a1Simulator = simulatorWith(a1, ally);
        performSkill(a1Simulator);
        double reactionTime = a1Simulator.getCurrentTime();
        double initialExpiration = a1.getDreamDrifterExpirationTime();
        a1Simulator.notifyReaction(stellarConduct, a1);
        a1Simulator.notifyReaction(overload, a1);
        assertClose(initialExpiration,
                a1.getDreamDrifterExpirationTime(),
                "Conduct and non-Swirl do not extend A1");
        a1.onReaction(stellarSwirl,
                new TestCharacter(CharacterId.VENTI, Element.ANEMO),
                reactionTime,
                a1Simulator);
        assertClose(initialExpiration,
                a1.getDreamDrifterExpirationTime(),
                "Foreign Stellar-Swirl source does not extend A1");
        SimulatorSnapshot a1Snapshot = a1Simulator.saveSnapshot();
        a1Simulator.notifyReaction(stellarSwirl, a1);
        assertClose(initialExpiration + 2.5,
                a1.getDreamDrifterExpirationTime(),
                "Typed Stellar-Swirl extends A1");
        a1Simulator.restoreSnapshot(a1Snapshot);
        assertClose(initialExpiration,
                a1.getDreamDrifterExpirationTime(),
                "Snapshot restore recovers the pre-Stellar A1 window");
        a1Simulator.notifyReaction(stellarSwirl, a1);
        assertClose(initialExpiration + 2.5,
                a1.getDreamDrifterExpirationTime(),
                "Restored A1 window extends once on replay");
        a1.onReaction(stellarSwirl, a1,
                reactionTime + 0.3 - EPSILON, a1Simulator);
        assertClose(initialExpiration + 2.5,
                a1.getDreamDrifterExpirationTime(),
                "Stellar-Swirl A1 remains closed before the ICD boundary");
        a1.onReaction(stellarSwirl, a1,
                reactionTime + 0.3, a1Simulator);
        assertClose(initialExpiration + 5.0,
                a1.getDreamDrifterExpirationTime(),
                "Stellar-Swirl A1 opens at the exact ICD boundary");
        a1Simulator.switchCharacter(CharacterId.SUCROSE);
        assertTrue(!a1.isDreamDrifterActive(a1Simulator.getCurrentTime()),
                "Switching makes Dreamdrifter inactive");
        a1.onReaction(stellarSwirl, a1,
                a1Simulator.getCurrentTime(), a1Simulator);
        assertEquals(0, a1.getDreamDrifterExtensionsRemaining(),
                "Inactive Dreamdrifter cannot extend from Stellar-Swirl");

        YumemizukiMizuki window = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator windowSimulator = simulatorWith(window);
        performSkill(windowSimulator);
        advanceTo(windowSimulator, 5.0 - EPSILON);
        StatsContainer beforeExpiry = new StatsContainer();
        window.applyTargetDependentTeamStats(
                beforeExpiry,
                window,
                windowSimulator.getEnemy(),
                testAction(Element.ANEMO),
                windowSimulator.getCurrentTime());
        assertClose(0.00042 * 115.2,
                beforeExpiry.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Dreamdrifter Stellar bonus remains before expiry");
        advanceTo(windowSimulator, 5.0);
        StatsContainer atExpiry = new StatsContainer();
        window.applyTargetDependentTeamStats(
                atExpiry,
                window,
                windowSimulator.getEnemy(),
                testAction(Element.ANEMO),
                windowSimulator.getCurrentTime());
        assertClose(0.0,
                atExpiry.get(StatType.STELLAR_SWIRL_DMG_BONUS),
                "Dreamdrifter Stellar bonus expires at the exact boundary");

        YumemizukiMizuki c1 = new YumemizukiMizuki(
                null, null, 1);
        TestCharacter c1Ally = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator c1Simulator = simulatorWith(c1, c1Ally);
        performSkill(c1Simulator);
        assertTrue(c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "C1 awaiting mark is active before Stellar events");
        c1Simulator.notifyReaction(stellarConduct, c1Ally);
        c1Simulator.notifyReaction(overload, c1Ally);
        assertTrue(c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Conduct and non-Swirl do not consume C1");
        c1.onReaction(stellarSwirl,
                new TestCharacter(CharacterId.VENTI, Element.ANEMO),
                c1Simulator.getCurrentTime(), c1Simulator);
        assertTrue(c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Foreign Stellar-Swirl source does not consume C1");
        c1Simulator.notifyReaction(stellar(
                ReactionResult.Kind.STELLAR_SWIRL, 0.0), c1Ally);
        assertTrue(c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Zero-damage Stellar-Swirl does not consume C1");
        SimulatorSnapshot snapshot = c1Simulator.saveSnapshot();
        c1Simulator.notifyReaction(stellarSwirl, c1Ally);
        assertTrue(!c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Party Stellar-Swirl consumes C1 exactly once");
        c1Simulator.notifyReaction(stellarSwirl, c1Ally);
        assertTrue(!c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Consumed C1 remains unavailable");
        c1Simulator.restoreSnapshot(snapshot);
        assertTrue(c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Snapshot restore recovers the unconsumed C1 mark");
        c1Simulator.notifyReaction(stellarSwirl, c1Ally);
        assertTrue(!c1.isC1AwaitingActive(c1Simulator.getCurrentTime()),
                "Restored C1 mark consumes once on the replayed event");

        YumemizukiMizuki expiry = new YumemizukiMizuki(
                null, null, 1);
        CombatSimulator expirySimulator = simulatorWith(expiry);
        performSkill(expirySimulator);
        advanceTo(expirySimulator, 4.0 * FRAME + 3.0);
        assertTrue(!expiry.isC1AwaitingActive(
                        expirySimulator.getCurrentTime()),
                "C1 awaiting mark expires at the exact half-open boundary");
        expirySimulator.notifyReaction(stellarSwirl, expiry);
        assertTrue(!expiry.isC1AwaitingActive(
                        expirySimulator.getCurrentTime()),
                "Expired C1 ignores Stellar-Swirl");
    }

    private static void testBurstTalentLevelsAndNoEnemyBoundary() {
        YumemizukiMizuki c0 = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureMizukiActions(simulator);
        c0.restoreCurrentEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord burst = named(
                records, "Anraku Secret Spring Therapy").get(0);
        assertClose(93.0 * FRAME, burst.time,
                "Burst activation hitmark");
        assertClose(1.599360, burst.action.getDamagePercent(),
                "Burst Talent-9 multiplier");
        assertClose(94.0 * FRAME, simulator.getCurrentTime(),
                "Burst duration");
        assertClose(4.0 * FRAME,
                c0.getBurstEnergyMarkers().get(0)[0],
                "Burst spends Energy at frame four");
        assertClose(15.0 - 93.0 * FRAME,
                c0.getBurstCDRemaining(simulator.getCurrentTime()),
                "Burst cooldown starts at frame one");

        YumemizukiMizuki c6 = new YumemizukiMizuki(
                null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureMizukiActions(c6Simulator);
        performSkill(c6Simulator);
        assertClose(1.154880,
                named(c6Records, "Aisa Utamakura Pilgrimage")
                        .get(0).action.getDamagePercent(),
                "C3 raises Skill activation talent");
        assertClose(0.898240,
                named(c6Records, "Dreamdrifter Continuous Attack")
                        .get(0).action.getDamagePercent(),
                "C3 raises continuous attack talent");
        c6.restoreCurrentEnergy(60.0);
        perform(c6Simulator, CharacterActionKey.BURST);
        assertClose(1.881600,
                named(c6Records, "Anraku Secret Spring Therapy")
                        .get(0).action.getDamagePercent(),
                "C5 raises Burst activation talent");

        YumemizukiMizuki noEnemyMizuki =
                new YumemizukiMizuki(null, null, 0);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyMizuki);
        List<ParticleRecord> noEnemyParticles =
                captureAnemoParticles(noEnemy);
        performSkill(noEnemy);
        noEnemy.advanceTime(5.0);
        assertEquals(0, noEnemyParticles.size(),
                "No target suppresses all Skill particles");
        assertEquals(4,
                noEnemyMizuki.getParticleGenerationsRemaining(),
                "No target does not consume particle generations");
    }

    private static void testRollbackSwitchAndUnsupportedBoundaries() {
        YumemizukiMizuki rollback = new YumemizukiMizuki(
                null, null, 0);
        CombatSimulator rollbackSimulator = simulatorWith(rollback);
        List<ActionRecord> rollbackRecords = captureMizukiActions(
                rollbackSimulator);
        List<ParticleRecord> rollbackParticles = captureAnemoParticles(
                rollbackSimulator);
        performSkill(rollbackSimulator);
        SimulatorSnapshot snapshot = rollbackSimulator.saveSnapshot();
        rollbackSimulator.advanceTime(110.0 * FRAME);
        rollbackSimulator.restoreSnapshot(snapshot);
        rollbackSimulator.restoreSnapshot(snapshot);
        rollbackRecords.clear();
        rollbackParticles.clear();
        advanceTo(rollbackSimulator, 160.0 * FRAME);
        assertEquals(2,
                named(rollbackRecords,
                        "Dreamdrifter Continuous Attack").size(),
                "Repeated restore resolves each pending cloud once");
        assertEquals(2, rollbackParticles.size(),
                "Repeated restore resolves each pending particle once");

        YumemizukiMizuki switching = new YumemizukiMizuki(
                null, null, 1);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSimulator = simulatorWith(
                switching, ally);
        List<ActionRecord> switchRecords = captureMizukiActions(
                switchSimulator);
        performSkill(switchSimulator);
        int cloudsAtSwitch = named(
                switchRecords, "Dreamdrifter Continuous Attack").size();
        switchSimulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switching.isDreamDrifterActive(
                switchSimulator.getCurrentTime()),
                "Switch-out terminates Dreamdrifter");
        switchSimulator.advanceTime(5.0);
        assertEquals(cloudsAtSwitch,
                named(switchRecords,
                        "Dreamdrifter Continuous Attack").size(),
                "Switch invalidates future cloud launches");

        assertThrows(IllegalArgumentException.class,
                () -> new YumemizukiMizuki(null, null, -1),
                "Mizuki rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new YumemizukiMizuki(null, null, 7),
                "Mizuki rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> switching.onAction(null, switchSimulator),
                "Mizuki rejects null actions");
        assertThrows(IllegalArgumentException.class,
                () -> switchSimulator.performAction(
                        CharacterId.YUMEMIZUKI_MIZUKI,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Mizuki rejects Hold Skill");
        assertTrue(!switching.isHealingAndCurrentHpRepresented(),
                "Healing and current HP fail closed");
        assertTrue(!switching.isSnackSystemRepresented(),
                "Snack and C4 systems fail closed");
        assertTrue(!switching.isMultiTargetGeometryRepresented(),
                "Multi-target geometry fails closed");
        assertTrue(!switching.isMovementAndExplorationRepresented(),
                "Movement and exploration fail closed");
        assertTrue(!switching.isRandomTargetingRepresented(),
                "Random targeting fails closed");
        assertTrue(!switching.isHitlagRepresented(),
                "Hitlag fails closed");
        assertTrue(!switching.isStaminaAndLowPlungeRepresented(),
                "Stamina and low Plunge fail closed");

        SnapshotAwareCharacterEffect.State state =
                switching.captureCharacterState();
        YumemizukiMizuki foreign = new YumemizukiMizuki(
                null, null, 0);
        assertTrue(!foreign.acceptsCharacterState(state),
                "Mizuki rejects another instance's snapshot state");
    }

    private static AttackAction testAction(Element element) {
        AttackAction action = new AttackAction(
                "Mizuki Support Test",
                1.0,
                element,
                StatType.BASE_ATK,
                element.getBonusStatType(),
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static ReactionResult stellar(
            ReactionResult.Kind kind,
            double damage) {
        Element damageElement = kind == ReactionResult.Kind.STELLAR_SWIRL
                ? Element.ANEMO : Element.ELECTRO;
        return ReactionResult.stellar(
                damage,
                kind,
                Element.CRYO,
                damageElement,
                false);
    }

    private static void performAllyHit(
            CombatSimulator simulator,
            TestCharacter ally,
            Element element) {
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(), testAction(element));
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static CombatSimulator simulatorWithoutEnemy(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.YUMEMIZUKI_MIZUKI,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.YUMEMIZUKI_MIZUKI,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureMizukiActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId()
                    == CharacterId.YUMEMIZUKI_MIZUKI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureAnemoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ANEMO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith(
                            "Yumemizuki Mizuki,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/YumemizukiMizuki/"
                        + "YumemizukiMizuki_Status.csv",
                "config/characters/YumemizukiMizuki/"
                        + "YumemizukiMizuki_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected,
                            Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Mizuki CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void assertTrue(
            boolean condition,
            String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": unexpected exception",
                    throwable);
        }
        throw new AssertionError(message + ": no exception");
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    private static final class ParticleRecord {
        private final double count;
        private final double time;

        private ParticleRecord(double count, double time) {
            this.count = count;
            this.time = time;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
