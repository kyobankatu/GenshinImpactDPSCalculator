package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Citlali;
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

/** Focused regression checks for Citlali's fixed-target Itzpapa slice. */
public final class CitlaliRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private CitlaliRegressionTest() {
    }

    /** Runs data, timing, support, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndIcd();
        testBasicsSkillParticlesAndBurst();
        testA1OpalFireAndA4();
        testC1C2C3AndC5();
        testC4AndC6();
        testSnapshotRefreshAndFailClosedScope();
        System.out.println("CitlaliRegressionTest passed");
    }

    private static void testIdentityDataAndIcd() throws IOException {
        Citlali citlali = new Citlali(null, null, 6);
        assertEquals(CharacterId.CITLALI, citlali.getCharacterId(),
                "Citlali typed identity");
        assertEquals(CharacterId.CITLALI, CharacterId.fromName("Citlali"),
                "Citlali display-name lookup");
        assertEquals(CharacterId.CITLALI, CharacterId.fromNumericId(89),
                "Citlali numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.CITLALI.getRegion(),
                "Citlali region");
        assertEquals(Element.CRYO, citlali.getElement(),
                "Citlali element");
        assertClose(11634.0,
                citlali.getBaseStats().get(StatType.BASE_HP),
                "Citlali base HP");
        assertClose(127.0,
                citlali.getBaseStats().get(StatType.BASE_ATK),
                "Citlali base ATK");
        assertClose(763.0,
                citlali.getBaseStats().get(StatType.BASE_DEF),
                "Citlali base DEF");
        assertClose(115.2,
                citlali.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Citlali ascension Elemental Mastery");
        assertClose(60.0, citlali.getEnergyCost(),
                "Citlali Energy cost");
        assertClose(16.0, citlali.getSkillCD(),
                "Citlali Skill cooldown");
        assertClose(15.0, citlali.getBurstCD(),
                "Citlali Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.CITLALI,
                    new Citlali(null, null, constellation).getCharacterId(),
                    "Citlali explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Citlali/Citlali_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Citlali/Citlali_Multipliers.csv"), 45);
        assertCsvValue("Frostfall Storm C3", 0.34048);
        assertCsvValue("Ice Storm C5", 10.752);
        assertCsvValue("C6 Maximum Points", 40.0);

        ICDManager icdManager = new ICDManager();
        assertTrue(icdManager.checkApplication(
                "CITLALI",
                ICDTag.Citlali_FrostfallStorm,
                ICDType.CitlaliFrostfallStorm,
                0.0),
                "First Frostfall application passes");
        assertTrue(!icdManager.checkApplication(
                "CITLALI",
                ICDTag.Citlali_FrostfallStorm,
                ICDType.CitlaliFrostfallStorm,
                1.49),
                "Frostfall private ICD blocks before 1.5 seconds");
        assertTrue(icdManager.checkApplication(
                "CITLALI",
                ICDTag.Citlali_FrostfallStorm,
                ICDType.CitlaliFrostfallStorm,
                1.5),
                "Frostfall private ICD opens at 1.5 seconds");
    }

    private static void testBasicsSkillParticlesAndBurst() {
        Citlali basics = new Citlali(null, null, 0);
        CombatSimulator basicSimulator = simulatorWith(basics);
        List<ActionRecord> basicRecords = captureActions(basicSimulator);
        double[] normalValues = { 0.737922, 0.659831, 0.914110 };
        int[] impactFrames = { 26, 26, 46 };
        int[] durationFrames = { 38, 39, 52 };
        for (int step = 0; step < normalValues.length; step++) {
            double castTime = basicSimulator.getCurrentTime();
            perform(basicSimulator, CharacterActionKey.NORMAL);
            ActionRecord normal = named(basicRecords,
                    "Shadow-Stealing Spirit Vessel N" + (step + 1)).get(0);
            assertClose(castTime + impactFrames[step] * FRAME,
                    normal.time,
                    "Citlali N" + (step + 1) + " impact");
            assertClose(castTime + durationFrames[step] * FRAME,
                    basicSimulator.getCurrentTime(),
                    "Citlali N" + (step + 1) + " recovery");
            assertClose(normalValues[step],
                    normal.action.getDamagePercent(),
                    "Citlali N" + (step + 1) + " multiplier");
            assertEquals(Element.CRYO, normal.action.getElement(),
                    "Citlali catalyst Normal element");
        }

        double chargedCast = basicSimulator.getCurrentTime();
        perform(basicSimulator, CharacterActionKey.CHARGE);
        assertEquals(0, named(basicRecords,
                "Shadow-Stealing Spirit Vessel Charged Attack").size(),
                "Charged projectile remains in flight after recovery");
        basicSimulator.advanceTime(4.0 * FRAME + EPSILON);
        ActionRecord charged = named(basicRecords,
                "Shadow-Stealing Spirit Vessel Charged Attack").get(0);
        assertClose(chargedCast + 60.0 * FRAME, charged.time,
                "Citlali Charged impact includes travel");
        assertClose(1.6864, charged.action.getDamagePercent(),
                "Citlali Charged multiplier");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Citlali Charged has no ICD");

        double plungeCast = basicSimulator.getCurrentTime();
        perform(basicSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(basicRecords,
                "Shadow-Stealing Spirit Vessel High Plunge").get(0);
        assertClose(plungeCast, plunge.time,
                "High Plunge uses fixed stationary impact");
        assertClose(plungeCast + 1.0, basicSimulator.getCurrentTime(),
                "High Plunge uses repository catalyst duration");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Citlali High Plunge multiplier");

        Citlali skill = new Citlali(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skill);
        List<ActionRecord> skillRecords = captureActions(skillSimulator);
        List<ParticleRecord> particles = captureCryoParticles(skillSimulator);
        performSkill(skillSimulator);
        assertTrue(skill.isNightsoulActive(skillSimulator.getCurrentTime()),
                "Skill enters local Nightsoul at frame 18");
        assertClose(24.0, skill.getNightsoulPoints(),
                "Skill grants 24 local Nightsoul points");
        assertTrue(!skill.isOpalFireActive(),
                "C0 Skill alone does not reach Opal Fire threshold");
        assertClose(18.0 * FRAME + 20.0,
                skill.getNightsoulExpirationTime(),
                "Skill creates exact 20-second Blessing window");
        ActionRecord initial = named(skillRecords,
                "Dawnfrost Darkstar: Obsidian Tzitzimitl").get(0);
        assertClose(20.0 * FRAME, initial.time,
                "Skill initial impact frame");
        assertClose(1.24032, initial.action.getDamagePercent(),
                "Skill initial multiplier");
        assertClose(16.0 - 32.0 * FRAME,
                skill.getSkillCDRemaining(skillSimulator.getCurrentTime()),
                "Skill cooldown starts at frame 18");
        assertEquals(0, particles.size(),
                "Skill particles retain 100-frame travel");
        advanceTo(skillSimulator, 120.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Skill emits one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Skill particle count");

        Citlali burst = new Citlali(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertClose(0.0, burst.getCurrentEnergy(),
                "Burst spends 60 Energy at frame 8");
        assertClose(15.0 - 113.0 * FRAME,
                burst.getBurstCDRemaining(burstSimulator.getCurrentTime()),
                "Burst cooldown starts at cast time");
        advanceTo(burstSimulator, 118.0 * FRAME + EPSILON);
        ActionRecord iceStorm = named(burstRecords,
                "Edict of Entwined Splendor: Ice Storm").get(0);
        assertClose(118.0 * FRAME, iceStorm.time,
                "Burst initial impact frame");
        assertClose(9.1392, iceStorm.action.getDamagePercent(),
                "Burst initial multiplier");
        assertClose(12.0 * 115.2,
                iceStorm.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "A4 Ice Storm captures 1200 percent of cast EM");
        assertClose(24.0, burst.getNightsoulPoints(),
                "Burst grants 24 points at frame 115");
        advanceTo(burstSimulator, 210.0 * FRAME + EPSILON);
        ActionRecord skull = named(burstRecords,
                "Spiritvessel Skull").get(0);
        assertClose(210.0 * FRAME, skull.time,
                "Fixed-target Spiritvessel Skull frame");
        assertClose(2.2848, skull.action.getDamagePercent(),
                "Spiritvessel Skull multiplier");
        assertEquals(ICDTag.Citlali_SpiritVessel,
                skull.action.getICDTag(),
                "Spiritvessel Skull typed ICD tag");
        assertClose(27.0, burst.getNightsoulPoints(),
                "Fixed target contributes one Skull's three points");
    }

    private static void testA1OpalFireAndA4() {
        Citlali citlali = new Citlali(null, null, 0);
        citlali.addBuff(new SimpleBuff(
                "A4 EM fixture",
                30.0,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 100.0)));
        CombatSimulator simulator = simulatorWith(citlali);
        List<ActionRecord> records = captureActions(simulator);
        simulator.notifyReaction(reaction(ReactionResult.Kind.MELT), citlali);
        assertClose(0.0, citlali.getNightsoulPoints(),
                "A1 fails closed before local Blessing");
        assertTrue(!hasTypedBuff(simulator,
                BuffId.CITLALI_A1_PYRO_HYDRO_RES_SHRED),
                "A1 does not fabricate support before local Blessing");
        performSkill(simulator);
        simulator.notifyReaction(reaction(ReactionResult.Kind.MELT), citlali);
        assertClose(40.0, citlali.getNightsoulPoints(),
                "First A1 event grants 16 points");
        assertTrue(!citlali.isOpalFireActive(),
                "Forty points remain below Opal Fire threshold");
        simulator.notifyReaction(reaction(ReactionResult.Kind.FROZEN), citlali);
        assertClose(40.0, citlali.getNightsoulPoints(),
                "A1 point gate blocks a same-time Frozen event");
        Buff shred = typedBuff(simulator,
                BuffId.CITLALI_A1_PYRO_HYDRO_RES_SHRED);
        StatsContainer shredStats = new StatsContainer();
        shred.apply(shredStats, simulator.getCurrentTime());
        assertClose(0.20, shredStats.get(StatType.PYRO_RES_SHRED),
                "A1 Pyro resistance support");
        assertClose(0.20, shredStats.get(StatType.HYDRO_RES_SHRED),
                "A1 Hydro resistance support");

        simulator.advanceTime(8.0);
        double activationTime = simulator.getCurrentTime();
        simulator.notifyReaction(reaction(ReactionResult.Kind.FROZEN), citlali);
        assertClose(56.0, citlali.getNightsoulPoints(),
                "A1 point gate reopens at eight seconds");
        assertTrue(citlali.isOpalFireActive(),
                "Fifty-six points activate Opal Fire");
        advanceTo(simulator, activationTime + 59.0 * FRAME + EPSILON);
        ActionRecord frostfall = named(records, "Frostfall Storm").get(0);
        assertClose(activationTime + 59.0 * FRAME, frostfall.time,
                "Frostfall first cadence");
        assertClose(0.289408, frostfall.action.getDamagePercent(),
                "Frostfall multiplier");
        assertClose(0.9 * 215.2,
                frostfall.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "A4 Frostfall captures 90 percent of release EM");
        assertEquals(ICDType.CitlaliFrostfallStorm,
                frostfall.action.getICDType(),
                "Frostfall private ICD type");
        advanceTo(simulator, activationTime + 2.0 * 59.0 * FRAME + EPSILON);
        assertEquals(2, named(records, "Frostfall Storm").size(),
                "Frostfall repeats every 59 frames");
        advanceTo(simulator, activationTime + 7.1);
        assertTrue(!citlali.isOpalFireActive(),
                "C0 Opal Fire stops after point depletion");
        assertTrue(!citlali.isTeamNightsoulBurstRepresented(),
                "A4 team Nightsoul Burst gain remains excluded");
    }

    private static void testC1C2C3AndC5() {
        Citlali c2 = new Citlali(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator simulator = simulatorWith(c2, ally);
        performSkill(simulator);
        assertClose(240.2,
                c2.getEffectiveStats(simulator.getCurrentTime()).get(
                        StatType.ELEMENTAL_MASTERY),
                "C2 grants Citlali 125 Elemental Mastery");
        assertEquals(10, c2.getStellarBladeCount(),
                "C1 Skill creates ten Stellar Blades");
        AttackAction offFieldNormal = testAttack(
                "C1 Off-Field Ally Normal", Element.PYRO, ActionType.NORMAL);
        StatsContainer offFieldStats = new StatsContainer();
        c2.applyTargetDependentTeamStats(
                offFieldStats,
                ally,
                simulator.getEnemy(),
                offFieldNormal,
                simulator.getCurrentTime());
        assertClose(0.0,
                offFieldStats.get(StatType.ELEMENTAL_MASTERY),
                "C2 does not buff an off-field ally");
        assertClose(0.0,
                offFieldStats.get(StatType.FLAT_DMG_BONUS),
                "C1 does not buff an off-field ally");
        simulator.setActiveCharacter(CharacterId.BENNETT);
        AttackAction allyNormal = testAttack(
                "C1 Ally Normal", Element.PYRO, ActionType.NORMAL);
        StatsContainer supportStats = new StatsContainer();
        c2.applyTargetDependentTeamStats(
                supportStats,
                ally,
                simulator.getEnemy(),
                allyNormal,
                simulator.getCurrentTime());
        assertClose(250.0,
                supportStats.get(StatType.ELEMENTAL_MASTERY),
                "C2 grants active ally 250 Elemental Mastery");
        assertClose(480.4,
                supportStats.get(StatType.FLAT_DMG_BONUS),
                "C1 uses Citlali's live C2 Elemental Mastery");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.BENNETT, allyNormal);
        assertEquals(9, c2.getStellarBladeCount(),
                "One accepted active-ally hit consumes one Stellar Blade");

        AttackAction unsupported = testAttack(
                "C1 Other", Element.PYRO, ActionType.OTHER);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.BENNETT, unsupported);
        assertEquals(9, c2.getStellarBladeCount(),
                "Unsupported action category does not consume C1");
        simulator.notifyReaction(reaction(ReactionResult.Kind.MELT), ally);
        assertEquals(12, c2.getStellarBladeCount(),
                "A1 adds three C1 Stellar Blades");
        StatsContainer c2ShredStats = new StatsContainer();
        typedBuff(simulator,
                BuffId.CITLALI_A1_PYRO_HYDRO_RES_SHRED).apply(
                        c2ShredStats, simulator.getCurrentTime());
        assertClose(0.40, c2ShredStats.get(StatType.PYRO_RES_SHRED),
                "C2 doubles A1 resistance support");

        Citlali c5 = new Citlali(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        performSkill(c5Simulator);
        assertClose(1.4592,
                named(c5Records,
                        "Dawnfrost Darkstar: Obsidian Tzitzimitl")
                        .get(0).action.getDamagePercent(),
                "C3 raises Skill initial multiplier");
        perform(c5Simulator, CharacterActionKey.BURST);
        advanceTo(c5Simulator,
                50.0 * FRAME + 118.0 * FRAME + EPSILON);
        assertClose(10.752,
                named(c5Records,
                        "Edict of Entwined Splendor: Ice Storm")
                        .get(0).action.getDamagePercent(),
                "C5 raises Burst initial multiplier");
        advanceTo(c5Simulator,
                50.0 * FRAME + 210.0 * FRAME + EPSILON);
        assertClose(2.688,
                named(c5Records, "Spiritvessel Skull")
                        .get(0).action.getDamagePercent(),
                "C5 raises Spiritvessel Skull multiplier");
    }

    private static void testC4AndC6() {
        Citlali c4 = new Citlali(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(c4Simulator);
        performSkill(c4Simulator);
        perform(c4Simulator, CharacterActionKey.BURST);
        double burstCast = 50.0 * FRAME;
        double opalActivation = burstCast + 210.0 * FRAME;
        double firstFrostfall = opalActivation + 59.0 * FRAME;
        advanceTo(c4Simulator, firstFrostfall - EPSILON);
        double energyBefore = c4.getCurrentEnergy();
        double pointsBefore = c4.getNightsoulPoints();
        advanceTo(c4Simulator, firstFrostfall + EPSILON);
        assertClose(energyBefore + 8.0, c4.getCurrentEnergy(),
                "C4 first Frostfall restores eight Energy");
        assertTrue(c4.getNightsoulPoints() > pointsBefore + 15.0,
                "C4 first Frostfall grants sixteen Nightsoul points");
        assertClose(0.34048,
                named(c4Records, "Frostfall Storm")
                        .get(0).action.getDamagePercent(),
                "C3 raises C4 Frostfall multiplier");
        double c4SkullTime = firstFrostfall + 92.0 * FRAME;
        advanceTo(c4Simulator, c4SkullTime + EPSILON);
        ActionRecord c4Skull = named(c4Records,
                "Spiritvessel Skull C4").get(0);
        assertClose(c4SkullTime, c4Skull.time,
                "C4 Skull fixed-target delay");
        assertClose(18.0 * 240.2,
                c4Skull.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "C4 Skull captures 1800 percent of live EM");
        advanceTo(c4Simulator, firstFrostfall + 8.0 - EPSILON);
        assertEquals(1, named(c4Records,
                "Spiritvessel Skull C4").size(),
                "C4 eight-second gate suppresses intervening Frostfalls");

        Citlali c6 = new Citlali(null, null, 6);
        TestCharacter hydroAlly = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator c6Simulator = simulatorWith(c6, hydroAlly);
        performSkill(c6Simulator);
        assertTrue(c6.isOpalFireActive(),
                "C6 enters Opal Fire below fifty points");
        perform(c6Simulator, CharacterActionKey.BURST);
        c6Simulator.advanceTime(7.0);
        assertClose(40.0, c6.getC6PointCount(),
                "C6 Cifra points cap at forty");
        StatsContainer selfStats = new StatsContainer();
        c6.applyTargetDependentTeamStats(
                selfStats,
                c6,
                c6Simulator.getEnemy(),
                testAttack("C6 Self", Element.CRYO, ActionType.OTHER),
                c6Simulator.getCurrentTime());
        assertClose(1.0, selfStats.get(StatType.DMG_BONUS_ALL),
                "C6 grants Citlali 2.5 percent damage per point");
        StatsContainer allyStats = new StatsContainer();
        c6.applyTargetDependentTeamStats(
                allyStats,
                hydroAlly,
                c6Simulator.getEnemy(),
                testAttack("C6 Ally", Element.HYDRO, ActionType.OTHER),
                c6Simulator.getCurrentTime());
        assertClose(0.60, allyStats.get(StatType.PYRO_DMG_BONUS),
                "C6 grants party Pyro damage per point");
        assertClose(0.60, allyStats.get(StatType.HYDRO_DMG_BONUS),
                "C6 grants party Hydro damage per point");
        advanceTo(c6Simulator, 18.0 * FRAME + 20.0 + EPSILON);
        assertClose(0.0, c6.getC6PointCount(),
                "Blessing expiry clears C6 points");
        assertTrue(!c6.isOpalFireActive(),
                "Blessing expiry invalidates Opal Fire generation");
    }

    private static void testSnapshotRefreshAndFailClosedScope() {
        Citlali snapshotCitlali = new Citlali(null, null, 0);
        CombatSimulator snapshotSimulator = simulatorWith(snapshotCitlali);
        List<ActionRecord> snapshotRecords = captureActions(snapshotSimulator);
        snapshotCitlali.addBuff(new SimpleBuff(
                "Charged release ATK",
                0.5,
                snapshotSimulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        perform(snapshotSimulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot snapshot = snapshotSimulator.saveSnapshot();
        snapshotSimulator.advanceTime(0.1);
        ActionRecord original = named(snapshotRecords,
                "Shadow-Stealing Spirit Vessel Charged Attack").get(0);
        assertClose(1.0,
                original.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                "In-flight Charged hit owns release-stage stats");
        snapshotSimulator.restoreSnapshot(snapshot);
        snapshotSimulator.restoreSnapshot(snapshot);
        snapshotRecords.clear();
        snapshotSimulator.advanceTime(0.1);
        assertEquals(1, named(snapshotRecords,
                "Shadow-Stealing Spirit Vessel Charged Attack").size(),
                "Repeated restore reconstructs pending hit once");
        assertClose(1.0,
                named(snapshotRecords,
                        "Shadow-Stealing Spirit Vessel Charged Attack")
                        .get(0).action.getStatSnapshot().get(
                                StatType.ATK_PERCENT),
                "Restored pending hit preserves release snapshot");

        Citlali generationCitlali = new Citlali(null, null, 6);
        CombatSimulator generationSimulator = simulatorWith(
                generationCitlali);
        List<ActionRecord> generationRecords = captureActions(
                generationSimulator);
        performSkill(generationSimulator);
        SimulatorSnapshot opalSnapshot = generationSimulator.saveSnapshot();
        generationSimulator.advanceTime(0.6);
        assertEquals(1, named(generationRecords,
                "Frostfall Storm").size(),
                "Original generation emits one first Frostfall");
        generationSimulator.restoreSnapshot(opalSnapshot);
        generationSimulator.restoreSnapshot(opalSnapshot);
        generationRecords.clear();
        generationSimulator.advanceTime(0.6);
        assertEquals(1, named(generationRecords,
                "Frostfall Storm").size(),
                "Repeated restore reconstructs Opal cadence once");

        Citlali refresh = new Citlali(null, null, 0);
        CombatSimulator refreshSimulator = simulatorWith(refresh);
        performSkill(refreshSimulator);
        advanceTo(refreshSimulator, 18.0 * FRAME + 16.0 + EPSILON);
        performSkill(refreshSimulator);
        double refreshedExpiry = refresh.getNightsoulExpirationTime();
        advanceTo(refreshSimulator, 18.0 * FRAME + 20.0 + EPSILON);
        assertTrue(refresh.isNightsoulActive(refreshSimulator.getCurrentTime()),
                "Stale first expiry cannot invalidate refreshed Blessing");
        assertClose(18.0 * FRAME + 16.0 + EPSILON
                        + 18.0 * FRAME + 20.0,
                refreshedExpiry,
                "Skill refresh owns its new expiration");

        assertTrue(!refresh.isShieldStateRepresented(),
                "Shield durability and absorption fail closed");
        assertTrue(!refresh.isGeometryRepresented(),
                "Geometry and multi-target behavior fail closed");
        assertThrows(IllegalArgumentException.class,
                () -> refresh.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        refreshSimulator),
                "Citlali rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> refresh.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH),
                        refreshSimulator),
                "Citlali rejects movement actions");
        assertThrows(IllegalArgumentException.class,
                () -> refresh.onAction(null, refreshSimulator),
                "Citlali rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> new Citlali(null, null, -1),
                "Citlali rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Citlali(null, null, 7),
                "Citlali rejects constellation above C6");
        Citlali reused = new Citlali(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Citlali rejects cross-simulator reuse");
        Citlali foreign = new Citlali(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!refresh.acceptsCharacterState(foreignState),
                "Citlali rejects another instance's snapshot payload");
    }

    private static ReactionResult reaction(ReactionResult.Kind kind) {
        return ReactionResult.state(kind.name(), kind, Element.CRYO);
    }

    private static AttackAction testAttack(
            String name,
            Element element,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                element.getBonusStatType(),
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.CITLALI,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.CITLALI,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CITLALI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureCryoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.CRYO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static Buff typedBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        throw new AssertionError("Missing typed buff " + id);
    }

    private static boolean hasTypedBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return true;
            }
        }
        return false;
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
            assertTrue(lines.get(index).startsWith("Citlali,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Citlali/Citlali_Status.csv",
                "config/characters/Citlali/Citlali_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected, Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Citlali CSVs missing key " + key);
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
