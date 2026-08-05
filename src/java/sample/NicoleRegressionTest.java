package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.Nicole;
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

/** Focused regression checks for Nicole's fixed-target Arcane Projection slice. */
public final class NicoleRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private NicoleRegressionTest() {
    }

    /** Runs Nicole's data, timing, support, projection, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testCatalystBasicsAndHighPlunge();
        testSkillSnapshotParticlesAndTalentConstellation();
        testBurstActivationProjectionCadenceAndLiveActor();
        testA1A4AndConstellations();
        testSnapshotRestoreAndGenerationIsolation();
        testFailClosedAndReuseGuards();
        System.out.println("NicoleRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Nicole nicole = new Nicole(null, null, 6);
        assertEquals(CharacterId.NICOLE, nicole.getCharacterId(),
                "Nicole typed identity");
        assertEquals(CharacterId.NICOLE,
                CharacterId.fromName("Nicole"),
                "Nicole display-name lookup");
        assertEquals(CharacterId.NICOLE,
                CharacterId.fromNumericId(92),
                "Nicole numeric lookup");
        assertEquals(CharacterRegion.UNKNOWN,
                CharacterId.NICOLE.getRegion(),
                "Nicole source catalog region fails closed");
        assertEquals(Element.PYRO, nicole.getElement(),
                "Nicole element");
        assertClose(10409.0,
                nicole.getBaseStats().get(StatType.BASE_HP),
                "Nicole base HP");
        assertClose(342.0,
                nicole.getBaseStats().get(StatType.BASE_ATK),
                "Nicole base ATK");
        assertClose(563.0,
                nicole.getBaseStats().get(StatType.BASE_DEF),
                "Nicole base DEF");
        assertClose(0.288,
                nicole.getBaseStats().get(StatType.ATK_PERCENT),
                "Nicole ascension ATK");
        assertClose(60.0, nicole.getEnergyCost(),
                "Nicole Energy cost");
        assertClose(16.0, nicole.getSkillCD(),
                "Nicole Skill cooldown");
        assertClose(15.0, nicole.getBurstCD(),
                "Nicole Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.NICOLE,
                    new Nicole(null, null, constellation)
                            .getCharacterId(),
                    "Nicole explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Nicole/Nicole_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Nicole/Nicole_Multipliers.csv"), 53);
        assertCsvValue("Base ATK", 342.0);
        assertCsvValue("Uncreated Light C3", 2.768);
        assertCsvValue("Arcane Projection C5", 2.016);
        assertCsvValue("C6 DEF Ignore", 0.4);
    }

    private static void testCatalystBasicsAndHighPlunge() {
        Nicole nicole = new Nicole(null, null, 0);
        CombatSimulator simulator = simulatorWith(nicole);
        List<ActionRecord> records = captureActions(simulator);
        int[] hitmarks = { 17, 8, 38 };
        int[] recoveries = { 25, 22, 52 };
        double[] multipliers = { 0.598046, 0.503771, 0.785196 };
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterId.NICOLE,
                    CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertClose(castTime + hitmarks[step] * FRAME,
                    record.time,
                    "Nicole Normal hitmark");
            assertClose(castTime + recoveries[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Nicole Normal recovery");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Nicole Normal multiplier");
            assertEquals(Element.PYRO,
                    record.action.getElement(),
                    "Nicole Normal element");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Nicole Normal category");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Nicole Normal ICD");
            assertClose(1.0,
                    record.action.getGaugeUnits(),
                    "Nicole Normal gauge");
            assertTrue(!record.action.hasStatSnapshot(),
                    "Nicole Normal snapshots at impact through live stats");
        }
        perform(simulator, CharacterId.NICOLE,
                CharacterActionKey.NORMAL);
        assertEquals("Allegoria N1", records.get(3).action.getName(),
                "Nicole Normal wraps after N3");

        Nicole chargedOwner = new Nicole(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> charged = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterId.NICOLE,
                CharacterActionKey.CHARGE);
        assertClose(64.0 * FRAME,
                chargedSimulator.getCurrentTime(),
                "Nicole Charged recovery");
        assertEquals(0, charged.size(),
                "Nicole Charged impact follows earliest recovery");
        advanceTo(chargedSimulator, 66.0 * FRAME);
        assertEquals(1, charged.size(),
                "Nicole Charged Attack has one fixed-target hit");
        assertClose(66.0 * FRAME, charged.get(0).time,
                "Nicole Charged hitmark");
        assertClose(1.90944,
                charged.get(0).action.getDamagePercent(),
                "Nicole Charged multiplier");
        assertEquals(ICDType.None,
                charged.get(0).action.getICDType(),
                "Nicole Charged has no application ICD");
        assertEquals(ICDTag.ChargedAttack,
                charged.get(0).action.getICDTag(),
                "Nicole Charged typed ICD tag");

        Nicole plungeOwner = new Nicole(null, null, 0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plunge = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterId.NICOLE,
                CharacterActionKey.PLUNGE);
        assertEquals(1, plunge.size(),
                "Nicole represented Plunge has one high impact");
        assertClose(46.0 * FRAME, plunge.get(0).time,
                "Nicole high Plunge hitmark");
        assertClose(67.0 * FRAME,
                plungeSimulator.getCurrentTime(),
                "Nicole high Plunge recovery");
        assertClose(2.607632,
                plunge.get(0).action.getDamagePercent(),
                "Nicole high Plunge multiplier");
        assertEquals(ICDType.None,
                plunge.get(0).action.getICDType(),
                "Nicole high Plunge has no application ICD");
    }

    private static void testSkillSnapshotParticlesAndTalentConstellation() {
        Nicole c0 = new Nicole(null, null, 0);
        c0.getBaseStats().set(StatType.BASE_ATK, 1000.0);
        c0.getBaseStats().set(StatType.ATK_PERCENT, 0.0);
        TestCharacter ally = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO, 100.0);
        CombatSimulator simulator = simulatorWith(c0, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = capturePyroParticles(simulator);
        performSkill(simulator, CharacterId.NICOLE);
        assertClose(31.0 * FRAME, simulator.getCurrentTime(),
                "Nicole Skill recovery");
        assertEquals(1, records.size(),
                "Nicole Skill has one hit");
        assertClose(9.0 * FRAME, records.get(0).time,
                "Nicole Skill hitmark");
        assertClose(2.3528,
                records.get(0).action.getDamagePercent(),
                "Nicole C0 Skill multiplier");
        assertEquals(ICDType.None,
                records.get(0).action.getICDType(),
                "Nicole Skill has no application ICD");
        assertClose(0.0, c0.getLastSkillTime(),
                "Nicole Skill cooldown starts at cast");
        assertClose(16.0, c0.getSkillCooldownEndTime(),
                "Nicole Skill cooldown end");
        assertClose(142.5, c0.getGraceAttackBonus(),
                "Nicole Grace snapshots frame-eight ATK");
        assertClose(20.0 + 8.0 * FRAME,
                c0.getGraceExpirationTime(),
                "Nicole Grace starts at frame eight");
        assertClose(142.5,
                effectiveStat(simulator, ally, StatType.ATK_FLAT),
                "Nicole Grace reaches the party");
        c0.addBuff(new SimpleBuff(
                "Nicole post-Skill ATK probe",
                30.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_FLAT, 3000.0)));
        assertClose(142.5, c0.getGraceAttackBonus(),
                "Nicole Grace remains snapshotted after ATK changes");
        advanceTo(simulator, 108.0 * FRAME);
        assertEquals(0, particles.size(),
                "Nicole particles wait for frame-nine hit plus travel");
        advanceTo(simulator, 109.0 * FRAME);
        assertEquals(1, particles.size(),
                "Nicole Skill generates one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Nicole Skill fixed particle count");
        assertClose(109.0 * FRAME, particles.get(0).time,
                "Nicole particle arrival frame");

        Nicole c3 = new Nicole(null, null, 3);
        c3.getBaseStats().set(StatType.BASE_ATK, 1000.0);
        c3.getBaseStats().set(StatType.ATK_PERCENT, 0.0);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator, CharacterId.NICOLE);
        assertClose(2.768,
                c3Records.get(0).action.getDamagePercent(),
                "Nicole C3 raises Skill talent");
        assertClose(168.0, c3.getGraceAttackBonus(),
                "Nicole C3 raises Grace ratio");
    }

    private static void testBurstActivationProjectionCadenceAndLiveActor() {
        Nicole nicole = new Nicole(null, null, 0);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO, 100.0);
        CombatSimulator simulator = simulatorWith(nicole, hydro);
        nicole.receiveEnergy(60.0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterId.NICOLE,
                CharacterActionKey.BURST);
        assertClose(113.0 * FRAME, simulator.getCurrentTime(),
                "Nicole Burst recovery");
        assertEquals(1, records.size(),
                "Nicole Burst initial hit resolves before recovery");
        assertClose(108.0 * FRAME, records.get(0).time,
                "Nicole Burst initial hitmark");
        assertClose(5.3856,
                records.get(0).action.getDamagePercent(),
                "Nicole C0 Burst initial multiplier");
        assertEquals(0, nicole.getProjectionCount(),
                "Nicole initial hit precedes Burst activation");
        assertTrue(nicole.isBurstActive(109.0 * FRAME),
                "Nicole Burst activates one frame after initial hit");
        assertClose(0.0, nicole.getCurrentEnergy(),
                "Nicole Burst spends Energy at frame twelve");
        assertClose(15.0, nicole.getBurstCooldownEndTime(),
                "Nicole Burst cooldown starts at cast");

        simulator.setActiveCharacter(CharacterId.XINGQIU);
        double triggerTime = simulator.getCurrentTime();
        performProbeHit(simulator, hydro, Element.HYDRO,
                ActionType.NORMAL);
        hydro.addBuff(new SimpleBuff(
                "Projection live ATK probe",
                10.0,
                triggerTime,
                stats -> stats.add(StatType.ATK_FLAT, 900.0)));
        simulator.setActiveCharacter(CharacterId.NICOLE);
        advanceTo(simulator, triggerTime + 30.0 * FRAME);
        List<ActionRecord> projections = named(
                records, "Arcane Projection 1");
        assertEquals(1, projections.size(),
                "Nicole queues one Burst projection");
        ActionRecord projection = projections.get(0);
        assertEquals(CharacterId.XINGQIU, projection.actorId,
                "Nicole projection retains triggering actor");
        assertEquals(Element.HYDRO, projection.action.getElement(),
                "Nicole projection uses triggering actor element");
        assertEquals(ActionType.OTHER,
                projection.action.getActionType(),
                "Nicole projection is not Skill or Burst damage");
        assertClose(0.0, projection.action.getGaugeUnits(),
                "Nicole projection applies no aura");
        assertTrue(!projection.action.hasStatSnapshot(),
                "Nicole projection uses live impact stats");
        assertClose(876.375, projection.damage,
                "Nicole projection uses captured actor live impact ATK");

        Nicole gateOwner = new Nicole(null, null, 0);
        CombatSimulator gateSimulator = simulatorWith(gateOwner);
        gateOwner.receiveEnergy(60.0);
        List<ActionRecord> gateRecords = captureActions(gateSimulator);
        perform(gateSimulator, CharacterId.NICOLE,
                CharacterActionKey.BURST);
        double firstTrigger = gateSimulator.getCurrentTime();
        for (int trigger = 0; trigger < 5; trigger++) {
            if (trigger > 0) {
                advanceTo(gateSimulator,
                        firstTrigger + trigger * 3.0);
            }
            performProbeHit(gateSimulator, gateOwner,
                    Element.PYRO, ActionType.NORMAL);
        }
        advanceTo(gateSimulator, firstTrigger + 12.5);
        assertEquals(4, gateOwner.getProjectionCount(),
                "Nicole Burst projection cap is four");
        assertEquals(4, namedPrefix(
                gateRecords, "Arcane Projection ").size(),
                "Nicole resolves four Burst projections");
        assertTrue(gateOwner.isBurstActive(
                109.0 * FRAME + 20.0 - EPSILON),
                "Nicole Burst active before exact expiry");
        assertTrue(!gateOwner.isBurstActive(
                109.0 * FRAME + 20.0),
                "Nicole Burst inactive at exact expiry");

        Nicole c5 = new Nicole(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        c5.receiveEnergy(60.0);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterId.NICOLE,
                CharacterActionKey.BURST);
        assertClose(6.336,
                c5Records.get(0).action.getDamagePercent(),
                "Nicole C5 raises initial Burst talent");
        performProbeHit(c5Simulator, c5,
                Element.PYRO, ActionType.NORMAL);
        advanceTo(c5Simulator,
                c5Simulator.getCurrentTime() + 30.0 * FRAME);
        assertClose(2.016,
                namedPrefix(c5Records, "Arcane Projection ")
                        .get(0).action.getDamagePercent(),
                "Nicole C5 raises projection talent");
    }

    private static void testA1A4AndConstellations() {
        Nicole c0 = new Nicole(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO, 100.0);
        CombatSimulator c0Simulator = simulatorWith(c0, ally);
        performSkill(c0Simulator, CharacterId.NICOLE);
        c0Simulator.switchCharacter(CharacterId.KAEYA);
        double switchTime = 31.0 * FRAME;
        advanceTo(c0Simulator, switchTime + 3.0 - FRAME);
        assertTrue(!c0.isGuidanceActive(
                CharacterId.KAEYA, c0Simulator.getCurrentTime()),
                "Nicole A1 waits three seconds for non-Hexerei ally");
        advanceTo(c0Simulator, switchTime + 3.0);
        assertTrue(c0.isGuidanceActive(
                CharacterId.KAEYA, c0Simulator.getCurrentTime()),
                "Nicole A1 upgrades ally at exact three-second delay");
        assertClose(c0.getGraceAttackBonus() + 300.0,
                effectiveStat(c0Simulator, ally, StatType.ATK_FLAT),
                "Nicole A1 adds 300 ATK over Grace");

        Nicole c1 = new Nicole(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        performProbeHit(c1Simulator, c1,
                Element.PYRO, ActionType.NORMAL);
        advanceTo(c1Simulator, 30.0 * FRAME);
        assertEquals(1, named(
                c1Records, "Arcane Projection: Unity").size(),
                "Nicole C1 projection triggers without Burst");
        assertClose(6.0,
                named(c1Records, "Arcane Projection: Unity")
                        .get(0).action.getDamagePercent(),
                "Nicole C1 projection multiplier");
        advanceTo(c1Simulator, 6.0);
        performProbeHit(c1Simulator, c1,
                Element.PYRO, ActionType.NORMAL);
        advanceTo(c1Simulator, 6.0 + 30.0 * FRAME);
        assertEquals(2, named(
                c1Records, "Arcane Projection: Unity").size(),
                "Nicole C1 gate reopens at exact six seconds");

        Nicole c2 = new Nicole(null, null, 2);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, 100.0);
        CombatSimulator c2Simulator = simulatorWith(c2, pyro);
        performSkill(c2Simulator, CharacterId.NICOLE);
        assertClose(300.0 + c2.getGraceAttackBonus(),
                effectiveStat(c2Simulator, pyro, StatType.ATK_FLAT),
                "Nicole C2 adds independent team ATK");
        StatsContainer c2HitStats = new StatsContainer();
        c2Simulator.setActiveCharacter(CharacterId.NICOLE);
        AttackAction pyroNormal = probeAction(
                Element.PYRO, ActionType.NORMAL);
        c2.applyTargetDependentTeamStats(
                c2HitStats,
                c2,
                c2Simulator.getEnemy(),
                pyroNormal,
                c2Simulator.getCurrentTime());
        assertClose(0.25,
                c2HitStats.get(StatType.PYRO_RES_SHRED),
                "Nicole C2 shreds upgraded character element");

        Nicole c4 = new Nicole(null, null, 4);
        c4.getBaseStats().set(StatType.BASE_ATK, 1000.0);
        c4.getBaseStats().set(StatType.ATK_PERCENT, 0.0);
        CombatSimulator c4Simulator = simulatorWith(c4);
        performSkill(c4Simulator, CharacterId.NICOLE);
        assertEquals(7, c4.getC4StackCount(
                CharacterId.NICOLE, c4Simulator.getCurrentTime()),
                "Nicole C4 grants eight stacks and Skill consumes one");
        StatsContainer c4OtherStats = new StatsContainer();
        c4.applyTargetDependentTeamStats(
                c4OtherStats,
                c4,
                c4Simulator.getEnemy(),
                probeAction(Element.PYRO, ActionType.OTHER),
                c4Simulator.getCurrentTime());
        assertClose(0.0,
                c4OtherStats.get(StatType.FLAT_DMG_BONUS),
                "Nicole C4 excludes projection-class attacks");
        StatsContainer c4NormalStats = new StatsContainer();
        c4.applyTargetDependentTeamStats(
                c4NormalStats,
                c4,
                c4Simulator.getEnemy(),
                probeAction(Element.PYRO, ActionType.NORMAL),
                c4Simulator.getCurrentTime());
        assertTrue(c4NormalStats.get(StatType.FLAT_DMG_BONUS) > 0.0,
                "Nicole C4 adds live Nicole ATK to eligible hits");

        Nicole c6 = new Nicole(null, null, 6);
        TestCharacter c6Ally = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO, 100.0);
        CombatSimulator c6Simulator = simulatorWith(c6, c6Ally);
        performSkill(c6Simulator, CharacterId.NICOLE);
        assertTrue(c6.isGuidanceActive(
                CharacterId.NICOLE, c6Simulator.getCurrentTime()),
                "Nicole C6 extends self Guidance through Grace");
        assertTrue(c6.isGuidanceActive(
                CharacterId.XINGQIU, c6Simulator.getCurrentTime()),
                "Nicole C6 shares self Guidance with allies");
        StatsContainer c6Stats = new StatsContainer();
        c6Simulator.setActiveCharacter(CharacterId.XINGQIU);
        c6.applyTargetDependentTeamStats(
                c6Stats,
                c6Ally,
                c6Simulator.getEnemy(),
                probeAction(Element.HYDRO, ActionType.NORMAL),
                c6Simulator.getCurrentTime());
        assertClose(0.4, c6Stats.get(StatType.DEF_IGNORE),
                "Nicole C6 grants Guidance DEF ignore");
        assertClose(0.25,
                c6Stats.get(StatType.HYDRO_RES_SHRED),
                "Nicole C6-shared Guidance retains C2 shred");
        c6Simulator.switchCharacter(CharacterId.NICOLE);
        assertTrue(c6.isGuidanceActive(
                CharacterId.XINGQIU, c6Simulator.getCurrentTime()),
                "Nicole C6 Guidance survives switching");
    }

    private static void testSnapshotRestoreAndGenerationIsolation() {
        Nicole nicole = new Nicole(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO, 100.0);
        CombatSimulator simulator = simulatorWith(nicole, ally);
        nicole.receiveEnergy(60.0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterId.NICOLE,
                CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.XINGQIU);
        performProbeHit(simulator, ally,
                Element.HYDRO, ActionType.NORMAL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        records.clear();
        advanceTo(simulator,
                simulator.getCurrentTime() + 30.0 * FRAME);
        assertEquals(1, namedPrefix(
                records, "Arcane Projection ").size(),
                "Nicole live branch resolves one pending projection");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator,
                simulator.getCurrentTime() + 30.0 * FRAME);
        assertEquals(1, namedPrefix(
                records, "Arcane Projection ").size(),
                "Nicole repeated restore rebuilds projection once");
        assertEquals(CharacterId.XINGQIU,
                namedPrefix(records, "Arcane Projection ")
                        .get(0).actorId,
                "Nicole restore retains projection actor");

        Nicole skillOwner = new Nicole(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillOwner);
        List<ParticleRecord> particles = capturePyroParticles(skillSimulator);
        performSkill(skillSimulator, CharacterId.NICOLE);
        SimulatorSnapshot skillSnapshot = skillSimulator.saveSnapshot();
        particles.clear();
        advanceTo(skillSimulator, 109.0 * FRAME);
        assertEquals(1, particles.size(),
                "Nicole live branch resolves one particle packet");
        skillSimulator.restoreSnapshot(skillSnapshot);
        skillSimulator.restoreSnapshot(skillSnapshot);
        particles.clear();
        advanceTo(skillSimulator, 109.0 * FRAME);
        assertEquals(1, particles.size(),
                "Nicole repeated restore rebuilds particles once");
    }

    private static void testFailClosedAndReuseGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Nicole(null, null, -1),
                "Nicole rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Nicole(null, null, 7),
                "Nicole rejects constellation above six");

        Nicole invalid = new Nicole(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Nicole rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Nicole rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> invalidSimulator.performAction(
                        CharacterId.NICOLE,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Nicole rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterId.NICOLE,
                        CharacterActionKey.DASH),
                "Nicole rejects movement actions");

        Nicole insufficient = new Nicole(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterId.NICOLE,
                CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Nicole insufficient Energy rejects Burst");
        assertClose(60.0, insufficient.getMissedBurstCost(),
                "Nicole records rejected Burst Energy");

        Nicole noTarget = new Nicole(null, null, 0);
        CombatSimulator noTargetSimulator = new CombatSimulator();
        noTargetSimulator.setLoggingEnabled(false);
        noTargetSimulator.addCharacter(noTarget);
        List<ParticleRecord> noTargetParticles =
                capturePyroParticles(noTargetSimulator);
        performSkill(noTargetSimulator, CharacterId.NICOLE);
        noTargetSimulator.advanceTime(3.0);
        assertEquals(0, noTargetParticles.size(),
                "Nicole no-target Skill cannot synthesize particles");

        Nicole reused = new Nicole(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Nicole rejects cross-simulator reuse");
        Nicole owner = new Nicole(null, null, 0);
        Nicole foreign = new Nicole(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Nicole rejects another instance's state");
        assertTrue(!owner.acceptsCharacterState(null),
                "Nicole rejects null snapshot payload");

        assertTrue(!invalid.isShieldStateRepresented(),
                "Nicole shield state fails closed");
        assertTrue(!invalid.isHexereiRepresented(),
                "Nicole Hexerei state fails closed");
        assertTrue(!invalid.isProjectionGeometryRepresented(),
                "Nicole projection geometry fails closed");
        assertTrue(!invalid.isPlayerHpRepresented(),
                "Nicole player HP fails closed");
        assertTrue(!invalid.isMovementRepresented(),
                "Nicole movement fails closed");
        assertTrue(!invalid.isRandomTargetingRepresented(),
                "Nicole random targeting fails closed");
        assertTrue(!invalid.isStaminaRepresented(),
                "Nicole stamina fails closed");
        assertTrue(!invalid.isHitlagRepresented(),
                "Nicole hitlag fails closed");
        assertTrue(!invalid.isLowPlungeRepresented(),
                "Nicole low Plunge fails closed");
        assertTrue(!invalid.isUnsupportedDefensiveStateRepresented(),
                "Nicole unsupported defense fails closed");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterId actorId,
            CharacterActionKey key) {
        simulator.performAction(actorId, CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            CharacterId actorId) {
        simulator.performAction(actorId,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static void performProbeHit(
            CombatSimulator simulator,
            Character actor,
            Element element,
            ActionType type) {
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), probeAction(element, type));
    }

    private static AttackAction probeAction(
            Element element,
            ActionType type) {
        StatType bonus = type == ActionType.NORMAL
                ? StatType.NORMAL_ATTACK_DMG_BONUS : null;
        AttackAction action = new AttackAction(
                "Nicole regression probe",
                1.0,
                element,
                StatType.BASE_ATK,
                bonus,
                0.0,
                type);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(
                        actor.getCharacterId(), action, damage, time)));
        return records;
    }

    private static List<ParticleRecord> capturePyroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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

    private static List<ActionRecord> namedPrefix(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)
                    && !record.action.getName().equals(
                            "Arcane Projection: Unity")) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static double effectiveStat(
            CombatSimulator simulator,
            Character character,
            StatType stat) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(stat);
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(
            Path path,
            int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Nicole,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Nicole/Nicole_Status.csv",
                "config/characters/Nicole/Nicole_Multipliers.csv"
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
        throw new AssertionError("Nicole CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected)
                == Double.doubleToLongBits(actual)) {
            return;
        }
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ActionRecord {
        private final CharacterId actorId;
        private final AttackAction action;
        private final double damage;
        private final double time;

        private ActionRecord(
                CharacterId actorId,
                AttackAction action,
                double damage,
                double time) {
            this.actorId = actorId;
            this.action = action;
            this.damage = damage;
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
                Element element,
                double baseAttack) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_ATK, baseAttack);
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
