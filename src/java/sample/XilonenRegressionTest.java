package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.element.ICDManager;
import mechanics.formula.DamageCalculator;
import model.character.Xilonen;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
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

/** Focused regression checks for Xilonen's bounded Source Sampler slice. */
public final class XilonenRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private XilonenRegressionTest() {
    }

    /** Runs data, action, sampler, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testSwordBasicsAndHighPlunge();
        testSkillBladeRollerParticlesAndCooldown();
        testSamplerCompositionA1A4AndC2();
        testBurstBranchesEnergyAndLiveHits();
        testC4AndC6Offense();
        testSnapshotRestoreIsolationAndExclusions();
        System.out.println("XilonenRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Xilonen xilonen = new Xilonen(null, null, 6);
        assertEquals(CharacterId.XILONEN, xilonen.getCharacterId(),
                "Xilonen typed identity");
        assertEquals(CharacterId.XILONEN, CharacterId.fromName("Xilonen"),
                "Xilonen exact-name lookup");
        assertEquals(CharacterId.XILONEN,
                CharacterId.fromNumericId(94),
                "Xilonen numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.XILONEN.getRegion(),
                "Xilonen region");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromNumericId(Integer.MAX_VALUE),
                "Out-of-range numeric ID remains unassigned");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromName("xilonen"),
                "Name lookup remains case-sensitive");
        assertEquals(Element.GEO, xilonen.getElement(),
                "Xilonen element");
        assertClose(12405.0,
                xilonen.getBaseStats().get(StatType.BASE_HP),
                "Xilonen base HP");
        assertClose(275.0,
                xilonen.getBaseStats().get(StatType.BASE_ATK),
                "Xilonen base ATK");
        assertClose(930.0,
                xilonen.getBaseStats().get(StatType.BASE_DEF),
                "Xilonen base DEF");
        assertClose(0.36,
                xilonen.getBaseStats().get(StatType.DEF_PERCENT),
                "Xilonen ascension DEF");
        assertClose(60.0, xilonen.getEnergyCost(),
                "Xilonen Energy cost");
        assertClose(7.0, xilonen.getSkillCD(),
                "Xilonen post-Blessing Skill cooldown");
        assertClose(15.0, xilonen.getBurstCD(),
                "Xilonen Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.XILONEN,
                    new Xilonen(null, null, constellation)
                            .getCharacterId(),
                    "Xilonen explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Xilonen/Xilonen_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Xilonen/Xilonen_Multipliers.csv"), 47);
        assertCsvValue("Source Sampler RES Shred C3", 0.42);
        assertCsvValue("Ocelotlicue Point C5", 5.6256);
    }

    private static void testSwordBasicsAndHighPlunge() {
        Xilonen xilonen = new Xilonen(null, null, 0);
        CombatSimulator simulator = simulatorWith(xilonen);
        List<ActionRecord> records = captureXilonenActions(simulator);
        double[][] multipliers = {
            { 0.951523 }, { 0.502914, 0.502914 }, { 1.340235 }
        };
        double[][] hitFrames = { { 18 }, { 50, 66 }, { 113 } };
        double[] endFrames = { 34, 91, 161 };
        double[] priorHitlagFrames = { 0, 6, 18 };
        double[] cumulativeHitlagFrames = { 6, 18, 26 };
        int recordIndex = 0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < multipliers[step].length; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertClose((hitFrames[step][hit]
                                + priorHitlagFrames[step]) * FRAME,
                        record.time,
                        "Xilonen Normal impact frame");
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Xilonen Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        record.action.getElement(),
                        "Xilonen Normal element");
                assertEquals(StatType.BASE_ATK,
                        record.action.getScalingStat(),
                        "Xilonen Normal ATK scaling");
            }
            assertClose((endFrames[step]
                            + cumulativeHitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Xilonen Normal action duration");
        }

        double chargeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charge = records.get(recordIndex++);
        assertClose(chargeCast + 23.0 * FRAME, charge.time,
                "Xilonen Charged hit frame");
        assertClose(1.67796, charge.action.getDamagePercent(),
                "Xilonen Charged multiplier");
        assertClose(chargeCast + 42.0 * FRAME,
                simulator.getCurrentTime(),
                "Xilonen Charged duration");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(recordIndex);
        assertClose(plungeCast + 50.0 * FRAME, plunge.time,
                "Xilonen high Plunge hit frame");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Xilonen high Plunge multiplier");
        assertEquals(StatType.BASE_DEF,
                plunge.action.getScalingStat(),
                "Xilonen high Plunge DEF scaling");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Xilonen high Plunge no ICD");
        assertClose(plungeCast + 75.0 * FRAME,
                simulator.getCurrentTime(),
                "Xilonen high Plunge duration");
    }

    private static void testSkillBladeRollerParticlesAndCooldown() {
        Xilonen xilonen = new Xilonen(null, null, 1);
        CombatSimulator simulator = simulatorWith(xilonen);
        List<ActionRecord> records = captureXilonenActions(simulator);
        List<ParticleRecord> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(20.0 * FRAME, simulator.getCurrentTime(),
                "Xilonen Skill duration");
        ActionRecord skill = named(records, "Yohual's Scratch").get(0);
        assertClose(6.0 * FRAME, skill.time,
                "Xilonen Skill hit frame");
        assertClose(3.0464, skill.action.getDamagePercent(),
                "Xilonen C1 Skill multiplier");
        assertEquals(StatType.BASE_DEF,
                skill.action.getScalingStat(),
                "Xilonen Skill DEF scaling");
        assertTrue(xilonen.isNightsoulBlessingActive(),
                "Skill enters local Blessing");
        assertClose(43.95, xilonen.getNightsoulPoints(),
                "C1 reduces three represented drain ticks");

        double rollerCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord roller = named(records, "Blade Roller").get(0);
        assertClose(rollerCast + 17.0 * FRAME, roller.time,
                "Blade Roller N1 hit frame");
        assertClose(1.029244, roller.action.getDamagePercent(),
                "Blade Roller N1 multiplier");
        assertEquals(ICDTag.Xilonen_BladeRoller,
                roller.action.getICDTag(),
                "Blade Roller private ICD tag");
        assertClose(0.30,
                bonus(roller.action, StatType.DMG_BONUS_ALL),
                "A1 low-conversion Nightsoul damage bonus");
        assertEquals(Element.GEO, roller.action.getElement(),
                "Blade Roller Geo element");
        assertClose(0.03,
                roller.action.getHitlagProfile().getHaltTimeSeconds(),
                "Blade Roller N1 halt time");
        assertTrue(roller.action.getHitlagProfile().canDefenseHalt(),
                "Blade Roller N1 permits Defense Halt");

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "Xilonen", ICDTag.NormalAttack,
                ICDType.Standard, 0.0),
                "Physical Normal ICD admits first hit");
        assertTrue(manager.checkApplication(
                "Xilonen", ICDTag.Xilonen_BladeRoller,
                ICDType.Standard, 0.0),
                "Blade Roller ICD is independent");

        advanceTo(simulator, 106.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Skill emits one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Skill particle count");
        assertClose(106.0 * FRAME, particles.get(0).time,
                "Skill hit plus 100-frame particle travel");

        Xilonen recast = new Xilonen(null, null, 0);
        CombatSimulator recastSimulator = simulatorWith(recast);
        List<ParticleRecord> recastParticles =
                captureGeoParticles(recastSimulator);
        perform(recastSimulator, CharacterActionKey.SKILL);
        perform(recastSimulator, CharacterActionKey.SKILL);
        assertClose(61.0 * FRAME, recastSimulator.getCurrentTime(),
                "Early Skill waits through one-second recast lock");
        assertTrue(!recast.isNightsoulBlessingActive(),
                "Skill recast exits Blessing");
        assertClose(8.0, recast.getSkillCooldownEndTime(),
                "Seven-second cooldown starts on recast exit");
        advanceTo(recastSimulator, 106.0 * FRAME + EPSILON);
        assertEquals(1, recastParticles.size(),
                "Exit invalidates drain generation but preserves particles");
    }

    private static void testSamplerCompositionA1A4AndC2() {
        Xilonen xilonen = new Xilonen(null, null, 3);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(xilonen, pyro, hydro);
        assertEquals(2, xilonen.getConvertedSamplerCount(),
                "Two PHEC teammates convert two Samplers");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(xilonen.getNightsoulPoints() > 70.0,
                "First A1 hit adds 35 points after drain");
        perform(simulator, CharacterActionKey.NORMAL);
        double activationTime = (81.0 + 6.0) * FRAME;
        assertTrue(!xilonen.isNightsoulBlessingActive(),
                "Maximum points exit Blessing after Roller action");
        assertTrue(xilonen.isSamplerActive(Element.PYRO,
                simulator.getCurrentTime()),
                "Converted Pyro Sampler activates");
        assertTrue(xilonen.isSamplerActive(Element.HYDRO,
                simulator.getCurrentTime()),
                "Converted Hydro Sampler activates");
        assertTrue(xilonen.isSamplerActive(Element.GEO,
                simulator.getCurrentTime()),
                "Short party retains Geo Sampler");
        assertTrue(!xilonen.isSamplerActive(Element.ELECTRO,
                simulator.getCurrentTime()),
                "Absent Electro Sampler fails closed");
        AttackAction samplerProbe = elementalHit(
                "Pyro Sampler Probe", Element.PYRO, ActionType.SKILL);
        samplerProbe.setStatSnapshot(new StatsContainer());
        StatsContainer samplerStats = DamageCalculator.resolveTargetStats(
                pyro,
                simulator.getEnemy(),
                samplerProbe,
                simulator.getApplicableBuffs(pyro),
                simulator.getCurrentTime(),
                simulator);
        assertClose(0.42,
                samplerStats.get(StatType.PYRO_RES_SHRED),
                "C3 Source Sampler live Pyro shred");
        assertClose(0.0,
                samplerStats.get(StatType.ELECTRO_RES_SHRED),
                "Source Sampler adds no absent-element shred");
        assertClose(0.56,
                xilonen.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.DEF_PERCENT),
                "A4 grants 20 percent DEF after maximum points");
        advanceTo(simulator, activationTime + 15.0);
        assertTrue(!xilonen.isSamplerActive(
                Element.PYRO, simulator.getCurrentTime()),
                "Sampler window is half-open at exact expiry");
        StatsContainer expiredSamplerStats =
                DamageCalculator.resolveTargetStats(
                        pyro,
                        simulator.getEnemy(),
                        samplerProbe,
                        simulator.getApplicableBuffs(pyro),
                        simulator.getCurrentTime(),
                        simulator);
        assertClose(0.0,
                expiredSamplerStats.get(StatType.PYRO_RES_SHRED),
                "Snapshotted hit does not retain expired Sampler shred");

        Xilonen c2 = new Xilonen(null, null, 2);
        TestCharacter c2Pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter electro = new TestCharacter(
                CharacterId.RAIDEN_SHOGUN, Element.ELECTRO);
        CombatSimulator c2Simulator = simulatorWith(
                c2, c2Pyro, electro);
        electro.spendEnergy(60.0);
        electro.markBurstCooldownUsed(0.0, null);
        assertClose(0.50,
                appliedStats(c2Simulator, c2)
                        .get(StatType.GEO_DMG_BONUS),
                "C2 Geo Sampler remains active below three conversions");
        perform(c2Simulator, CharacterActionKey.SKILL);
        perform(c2Simulator, CharacterActionKey.NORMAL);
        perform(c2Simulator, CharacterActionKey.NORMAL);
        assertClose(25.0, electro.getTotalFlatEnergy(),
                "C2 Electro Sampler grants 25 flat Energy");
        assertClose(6.0,
                15.0 - electro.getBurstCooldownEndTime(),
                "C2 Electro Sampler reduces Burst cooldown by six seconds");
        assertClose(0.45,
                appliedStats(c2Simulator, c2Pyro)
                        .get(StatType.ATK_PERCENT),
                "C2 Pyro Sampler grants ATK during active window");
        assertTrue(!c2.isC2HydroHpRepresented(),
                "C2 Hydro player-HP support remains excluded");
    }

    private static void testBurstBranchesEnergyAndLiveHits() {
        Xilonen offensive = new Xilonen(null, null, 5);
        CombatSimulator offensiveSimulator = simulatorWith(offensive);
        List<ActionRecord> offensiveRecords =
                captureXilonenActions(offensiveSimulator);
        perform(offensiveSimulator, CharacterActionKey.BURST);
        assertClose(101.0 * FRAME,
                offensiveSimulator.getCurrentTime(),
                "Xilonen Burst action duration");
        assertClose(0.0, offensive.getCurrentEnergy(),
                "Xilonen Burst spends Energy at frame 16");
        assertClose(15.0,
                offensive.getBurstCooldownEndTime(),
                "Xilonen Burst cooldown starts at cast");
        assertEquals(1, named(offensiveRecords,
                "Ocelotlicue Point").size(),
                "Offensive Burst initial hit resolves by action end");
        offensive.addBuff(new mechanics.buff.SimpleBuff(
                "Late Burst DEF",
                2.0,
                offensiveSimulator.getCurrentTime(),
                stats -> stats.add(StatType.DEF_PERCENT, 1.0)));
        advanceTo(offensiveSimulator, 164.0 * FRAME + EPSILON);
        assertEquals(2, named(offensiveRecords,
                "Follow-Up Beat").size(),
                "Low-conversion Burst emits two follow-up hits");
        for (ActionRecord record : offensiveRecords) {
            assertClose(5.6256, record.action.getDamagePercent(),
                    "C5 Burst talent multiplier");
            assertTrue(!record.action.hasStatSnapshot(),
                    "Burst hits use live impact-time stats");
            assertEquals(ICDTag.ElementalBurst,
                    record.action.getICDTag(),
                    "Burst hits share standard Burst ICD");
        }
        assertTrue(named(offensiveRecords,
                "Follow-Up Beat").get(0).damage
                        > named(offensiveRecords,
                                "Ocelotlicue Point").get(0).damage,
                "Late DEF buff affects unsnapshotted follow-up hit");

        Xilonen support = new Xilonen(null, null, 0);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator supportSimulator = simulatorWith(
                support, pyro, hydro);
        List<ActionRecord> supportRecords =
                captureXilonenActions(supportSimulator);
        perform(supportSimulator, CharacterActionKey.BURST);
        advanceTo(supportSimulator, 3.0);
        assertEquals(1, supportRecords.size(),
                "Two-conversion Burst keeps only initial offense");
        assertTrue(!support.isHealingRepresented(),
                "Support Burst healing remains excluded");
    }

    private static void testC4AndC6Offense() {
        Xilonen c4 = new Xilonen(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator c4Simulator = simulatorWith(c4, ally);
        perform(c4Simulator, CharacterActionKey.SKILL);
        AttackAction allyNormal = elementalHit(
                "C4 Ally Normal", Element.PHYSICAL, ActionType.NORMAL);
        StatsContainer c4Stats = DamageCalculator.resolveTargetStats(
                ally,
                c4Simulator.getEnemy(),
                allyNormal,
                c4Simulator.getApplicableBuffs(ally),
                c4Simulator.getCurrentTime(),
                c4Simulator);
        assertClose(930.0 * 1.36 * 0.65,
                c4Stats.get(StatType.FLAT_DMG_BONUS),
                "C4 reads Xilonen's live DEF");
        c4Simulator.performActionWithoutTimeAdvance(
                CharacterId.BENNETT, allyNormal);
        assertEquals(5, c4.getC4Stacks(
                CharacterId.BENNETT,
                c4Simulator.getCurrentTime()),
                "C4 consumes one of six ally quotas after damage");
        c4Simulator.performActionWithoutTimeAdvance(
                CharacterId.BENNETT,
                elementalHit("C4 Ineligible Skill",
                        Element.PYRO, ActionType.SKILL));
        assertEquals(5, c4.getC4Stacks(
                CharacterId.BENNETT,
                c4Simulator.getCurrentTime()),
                "C4 rejects Skill damage");

        Xilonen c5 = new Xilonen(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureXilonenActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        perform(c5Simulator, CharacterActionKey.NORMAL);
        double c5Damage = named(c5Records, "Blade Roller").get(0).damage;

        Xilonen c6 = new Xilonen(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureXilonenActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        double pointsBefore = c6.getNightsoulPoints();
        perform(c6Simulator, CharacterActionKey.NORMAL);
        ActionRecord c6Roller = named(c6Records, "Blade Roller").get(0);
        assertTrue(c6Roller.damage > c5Damage * 2.0,
                "C6 adds 300 percent live DEF to Blade Roller");
        assertClose(pointsBefore, c6.getNightsoulPoints(),
                "C6 pauses Nightsoul point drain during its window");
        assertEquals(5, c6.getC4Stacks(
                CharacterId.XILONEN,
                c6Simulator.getCurrentTime()),
                "C6 Roller also consumes one C4 owner quota");
    }

    private static void testSnapshotRestoreIsolationAndExclusions() {
        Xilonen xilonen = new Xilonen(null, null, 0);
        CombatSimulator simulator = simulatorWith(xilonen);
        List<ActionRecord> records = captureXilonenActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 3.0);
        assertEquals(2, named(records, "Follow-Up Beat").size(),
                "Live Burst follow-ups resolve once");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 3.0);
        assertEquals(2, named(records, "Follow-Up Beat").size(),
                "Repeated restore reconstructs each follow-up once");

        assertTrue(!xilonen.isMovementGeometryRepresented(),
                "Movement, climbing, and geometry fail closed");
        assertTrue(!xilonen.isMultiTargetSelectionRepresented(),
                "Multi-target and random selection fail closed");
        assertTrue(!xilonen.isNightsoulBurstTeamPlumbingRepresented(),
                "Nightsoul Burst team plumbing fails closed");
        assertTrue(!xilonen.isHitlagStaminaRepresented(),
                "Hitlag and stamina fail closed");
        assertTrue(!xilonen.isLowPlungeRepresented(),
                "Low Plunge fails closed");
        assertTrue(!xilonen.isExplorationDefensiveStateRepresented(),
                "Exploration and defensive state fail closed");
        assertThrows(IllegalArgumentException.class,
                () -> new Xilonen(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Xilonen(null, null, 7),
                "Constellation above C6 is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> xilonen.onAction(null, simulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.XILONEN,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Hold Skill is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Movement action is rejected");

        Xilonen blessed = new Xilonen(null, null, 0);
        CombatSimulator blessedSimulator = simulatorWith(blessed);
        perform(blessedSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalArgumentException.class,
                () -> perform(blessedSimulator,
                        CharacterActionKey.CHARGE),
                "Charged Attack during Blessing is rejected");

        Xilonen foreign = new Xilonen(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!xilonen.acceptsCharacterState(foreignState),
                "Xilonen rejects another instance state");
        Xilonen reused = new Xilonen(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Xilonen rejects cross-simulator reuse");
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
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
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
                CharacterId.XILONEN,
                CharacterActionRequest.of(key));
    }

    private static AttackAction elementalHit(
            String name,
            Element element,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                actionType == ActionType.NORMAL
                        ? StatType.NORMAL_ATTACK_DMG_BONUS
                        : StatType.SKILL_DMG_BONUS,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        return action;
    }

    private static List<ActionRecord> captureXilonenActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.XILONEN) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureGeoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static StatsContainer appliedStats(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static double bonus(
            AttackAction action,
            StatType statType) {
        Double value = action.getExtraBonuses().get(statType);
        return value == null ? 0.0 : value;
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
            assertTrue(lines.get(index).startsWith("Xilonen,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Xilonen/Xilonen_Status.csv",
                "config/characters/Xilonen/Xilonen_Multipliers.csv"
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
        throw new AssertionError("Xilonen CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
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
                    + expected.getSimpleName() + " but caught "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double damage;
        private final double time;

        private ActionRecord(
                AttackAction action,
                double damage,
                double time) {
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
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
            setBurstCD(15.0);
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
