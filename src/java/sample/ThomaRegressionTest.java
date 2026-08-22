package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Thoma;
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

/** Focused regression checks for Thoma's represented fixed-target slice. */
public final class ThomaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ThomaRegressionTest() {
    }

    /** Runs identity, action, trigger, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testConstellationValidation();
        testNormalChargedAndPlungeActions();
        testSkillTimingParticlesAndC3();
        testBurstInitialC4AndC5();
        testFieryCollapseTriggerAndIcd();
        testFieryCollapseGuardsAndExpiry();
        testC2DurationAndExcludedBranches();
        testSnapshotRestore();
        testBindingAndInputGuards();
        System.out.println("ThomaRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.THOMA, CharacterId.fromNumericId(58),
                "Thoma numeric identity");
        assertEquals(CharacterId.THOMA, CharacterId.fromName("Thoma"),
                "Thoma exact-name identity");
        assertEquals(CharacterRegion.INAZUMA, CharacterId.THOMA.getRegion(),
                "Thoma region");
        assertEquals(CharacterId.YAOYAO, CharacterId.fromNumericId(59),
                "Thoma next numeric identity");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("thoma"),
                "Thoma lookup remains case-sensitive");

        Thoma thoma = thoma(0, 0.75);
        assertEquals(CharacterId.THOMA, thoma.getCharacterId(),
                "Thoma runtime identity");
        assertEquals(Element.PYRO, thoma.getElement(),
                "Thoma element");
        assertClose(10331.0,
                thoma.getBaseStats().get(StatType.BASE_HP),
                "Thoma base HP");
        assertClose(202.0,
                thoma.getBaseStats().get(StatType.BASE_ATK),
                "Thoma base ATK");
        assertClose(751.0,
                thoma.getBaseStats().get(StatType.BASE_DEF),
                "Thoma base DEF");
        assertClose(0.24,
                thoma.getBaseStats().get(StatType.ATK_PERCENT),
                "Thoma ascension ATK percent");
        assertClose(0.0,
                thoma.getBaseStats().get(StatType.HP_PERCENT),
                "Thoma does not synthesize HP ascension");
        assertClose(80.0, thoma.getEnergyCost(),
                "Thoma Burst cost");
        assertClose(15.0, thoma.getSkillCD(),
                "Thoma Skill cooldown");
        assertClose(20.0, thoma.getBurstCD(),
                "Thoma Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Thoma/Thoma_Status.csv"), 20);
        assertCsvShape(Path.of(
                "config/characters/Thoma/Thoma_Multipliers.csv"), 13);
        assertCsvValue("N3 Hit 2", 0.492170);
        assertCsvValue("Fiery Collapse C5", 1.160000);
    }

    private static void testConstellationValidation() {
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation,
                    thoma(constellation, 0.75).getConstellation(),
                    "Thoma accepts C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> thoma(-1, 0.75),
                "Thoma rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> thoma(7, 0.75),
                "Thoma rejects constellation above C6");
    }

    private static void testNormalChargedAndPlungeActions() {
        Thoma thoma = thoma(0, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(thoma, ally);
        List<ActionRecord> records = captureActions(simulator);
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(records, "Swiftshatter Spear N");
        assertEquals(5, normals.size(),
                "Thoma four-step Normal string has five hits");
        double[] multipliers = {
            0.815596, 0.801534, 0.492170, 0.492170, 1.237456
        };
        double[] times = {
            13.0, 29.0 + 18.0, 65.0 + 10.0,
            65.0 + 23.0, 97.0 + 20.0
        };
        for (int index = 0; index < normals.size(); index++) {
            ActionRecord record = normals.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Thoma Normal multiplier " + index);
            assertClose(times[index] * FRAME, record.time,
                    "Thoma Normal hitmark " + index);
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Thoma Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Thoma Normal action type " + index);
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Thoma Normal ICD tag " + index);
        }
        assertClose(155.0 * FRAME, simulator.getCurrentTime(),
                "Thoma Normal string duration");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.THOMA);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Swiftshatter Spear N1").size(),
                "Thoma switch-out resets Normal string");

        Thoma chargedOwner = thoma(0, 0.75);
        CombatSimulator chargedSim = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(chargedSim);
        perform(chargedSim, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                chargedRecords, "Swiftshatter Spear Charged").get(0);
        assertClose(2.071380, charged.action.getDamagePercent(),
                "Thoma Charged multiplier");
        assertClose(27.0 * FRAME, charged.time,
                "Thoma Charged hitmark");
        assertClose(64.0 * FRAME, chargedSim.getCurrentTime(),
                "Thoma Charged duration");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Thoma Charged action type");
        assertEquals(ICDTag.ChargedAttack, charged.action.getICDTag(),
                "Thoma Charged ICD tag");

        Thoma plungeOwner = thoma(0, 0.75);
        CombatSimulator plungeSim = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(
                plungeRecords, "Swiftshatter Spear High Plunge").get(0);
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Thoma High Plunge multiplier");
        assertClose(46.0 * FRAME, plunge.time,
                "Thoma High Plunge hitmark");
        assertClose(77.0 * FRAME, plungeSim.getCurrentTime(),
                "Thoma High Plunge duration");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Thoma High Plunge has no elemental ICD");
    }

    private static void testSkillTimingParticlesAndC3() {
        Thoma c0 = thoma(0, 0.0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(records, "Blazing Blessing").get(0);
        assertClose(11.0 * FRAME, skill.time,
                "Thoma Skill hitmark");
        assertClose(2.488800, skill.action.getDamagePercent(),
                "Thoma C0 Skill multiplier");
        assertEquals(ICDType.None, skill.action.getICDType(),
                "Thoma Skill has no ICD");
        assertEquals(ICDTag.ElementalSkill, skill.action.getICDTag(),
                "Thoma Skill ICD tag");
        assertClose(1.0, skill.action.getGaugeUnits(),
                "Thoma Skill Pyro gauge");
        assertClose(9.0 * FRAME, c0.getLastSkillTime(),
                "Thoma Skill cooldown starts at frame 9");
        assertClose(9.0 * FRAME + 15.0,
                c0.getSkillCooldownEndTime(),
                "Thoma Skill cooldown duration");
        assertClose(54.0 * FRAME, simulator.getCurrentTime(),
                "Thoma Skill action duration");
        advanceTo(simulator, 112.0 * FRAME);
        assertEquals(1, particles.size(),
                "Thoma Skill emits one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Thoma low draw emits four Pyro particles");
        assertClose(111.0 * FRAME, particles.get(0).time,
                "Thoma particle travel time");

        Thoma threeParticles = thoma(0, 0.75);
        CombatSimulator threeSim = simulatorWith(threeParticles);
        List<ParticleRecord> threeRecords = captureParticles(threeSim);
        perform(threeSim, CharacterActionKey.SKILL);
        advanceTo(threeSim, 112.0 * FRAME);
        assertClose(3.0, threeRecords.get(0).count,
                "Thoma high draw emits three Pyro particles");

        Thoma c3 = thoma(3, 0.75);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Sim);
        perform(c3Sim, CharacterActionKey.SKILL);
        assertClose(2.928000,
                named(c3Records, "Blazing Blessing")
                        .get(0).action.getDamagePercent(),
                "Thoma C3 raises Skill to Talent 12");
    }

    private static void testBurstInitialC4AndC5() {
        Thoma c0 = thoma(0, 0.75);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord initial = named(
                records, "Crimson Ooyoroi Initial").get(0);
        assertClose(40.0 * FRAME, initial.time,
                "Thoma Burst initial hitmark");
        assertClose(1.496000, initial.action.getDamagePercent(),
                "Thoma C0 Burst initial multiplier");
        assertEquals(ICDType.None, initial.action.getICDType(),
                "Thoma Burst initial has no ICD");
        assertClose(2.0, initial.action.getGaugeUnits(),
                "Thoma Burst initial Pyro gauge");
        assertEquals(ActionType.BURST, initial.action.getActionType(),
                "Thoma Burst initial action type");
        assertTrue(initial.action.hasStatSnapshot(),
                "Thoma Burst initial keeps cast-time stats");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Thoma C0 spends 80 Energy at frame 7");
        assertClose(20.0, c0.getBurstCooldownEndTime(),
                "Thoma Burst cooldown starts at cast");
        assertClose(58.0 * FRAME, simulator.getCurrentTime(),
                "Thoma Burst action duration");

        Thoma c4 = thoma(4, 0.75);
        CombatSimulator c4Sim = simulatorWith(c4);
        perform(c4Sim, CharacterActionKey.BURST);
        assertClose(15.0, c4.getCurrentEnergy(),
                "Thoma C4 restores 15 Energy after spending Burst cost");
        assertClose(15.0, c4.getTotalFlatEnergy(),
                "Thoma C4 records flat Energy restoration");

        Thoma c5 = thoma(5, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c5Sim = simulatorWith(c5, ally);
        List<ActionRecord> c5Records = captureActions(c5Sim);
        perform(c5Sim, CharacterActionKey.BURST);
        assertClose(1.760000,
                named(c5Records, "Crimson Ooyoroi Initial")
                        .get(0).action.getDamagePercent(),
                "Thoma C5 raises Burst initial to Talent 12");
        c5Sim.setActiveCharacter(CharacterId.NOELLE);
        directHit(c5Sim, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "C5 trigger");
        c5Sim.advanceTime(11.0 * FRAME);
        assertClose(1.160000,
                named(c5Records, "Crimson Ooyoroi Fiery Collapse")
                        .get(0).action.getDamagePercent(),
                "Thoma C5 raises Fiery Collapse to Talent 12");
    }

    private static void testFieryCollapseTriggerAndIcd() {
        Thoma thoma = thoma(0, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(thoma, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        double firstTrigger = simulator.getCurrentTime();
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Active ally Normal");
        assertEquals(0,
                named(records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Fiery Collapse waits eleven frames");
        simulator.advanceTime(11.0 * FRAME);
        List<ActionRecord> collapses = named(
                records, "Crimson Ooyoroi Fiery Collapse");
        assertEquals(1, collapses.size(),
                "Active ally Normal triggers Fiery Collapse");
        ActionRecord collapse = collapses.get(0);
        assertClose(firstTrigger + 11.0 * FRAME, collapse.time,
                "Fiery Collapse source-backed delay");
        assertEquals(CharacterId.THOMA, collapse.actor,
                "Fiery Collapse remains Thoma-attributed");
        assertClose(0.986000, collapse.action.getDamagePercent(),
                "Fiery Collapse C0 multiplier");
        assertEquals(ActionType.BURST, collapse.action.getActionType(),
                "Fiery Collapse counts as Burst damage");
        assertEquals(ICDType.Standard, collapse.action.getICDType(),
                "Fiery Collapse standard ICD");
        assertEquals(ICDTag.ElementalBurst, collapse.action.getICDTag(),
                "Fiery Collapse shared Burst ICD tag");
        assertClose(1.0, collapse.action.getGaugeUnits(),
                "Fiery Collapse Pyro gauge");
        assertTrue(collapse.action.hasStatSnapshot(),
                "Fiery Collapse keeps trigger-time stats");
        assertClose(10331.0 * 0.022,
                collapse.action.getAdditiveBaseDmgBonus(),
                "Thoma A4 adds final Max HP flat damage");

        advanceTo(simulator, firstTrigger + 1.0 - EPSILON * 10.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Early Normal");
        assertEquals(1, named(
                records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Fiery Collapse rejects trigger before one second");
        advanceTo(simulator, firstTrigger + 1.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Exact Normal");
        simulator.advanceTime(11.0 * FRAME);
        assertEquals(2, named(
                records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Fiery Collapse accepts exact one-second boundary");
    }

    private static void testFieryCollapseGuardsAndExpiry() {
        Thoma thoma = thoma(0, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(thoma, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        double gateTime = simulator.getCurrentTime();

        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.SKILL, 1.0, true, "Wrong action");
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 0.0, true, "Zero Normal");
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, false, "Non-hit Normal");
        simulator.setActiveCharacter(CharacterId.THOMA);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Off-field ally Normal");
        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.THOMA,
                ActionType.NORMAL, 1.0, true, "Off-field owner Normal");
        simulator.advanceTime(11.0 * FRAME);
        assertEquals(0, named(
                records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Wrong, zero, non-hit, and off-field attacks fail closed");

        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Accepted after guards");
        simulator.advanceTime(11.0 * FRAME);
        assertEquals(1, named(
                records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Rejected hits do not consume Fiery Collapse ICD");
        assertClose(gateTime + 11.0 * FRAME * 2.0,
                named(records, "Crimson Ooyoroi Fiery Collapse")
                        .get(0).time,
                "Accepted guard-case Collapse timing");

        Thoma expiryOwner = thoma(0, 0.75);
        TestCharacter expiryAlly = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator expirySim = simulatorWith(
                expiryOwner, expiryAlly);
        List<ActionRecord> expiryRecords = captureActions(expirySim);
        perform(expirySim, CharacterActionKey.BURST);
        expirySim.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(expirySim, 15.0);
        assertTrue(!expiryOwner.isCrimsonOoyoroiActive(15.0),
                "Thoma C0 Burst expires at exact 15 seconds");
        directHit(expirySim, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Expiry Normal");
        expirySim.advanceTime(11.0 * FRAME);
        assertEquals(0, named(
                expiryRecords, "Crimson Ooyoroi Fiery Collapse").size(),
                "Exact-expiry Normal does not trigger Fiery Collapse");
    }

    private static void testC2DurationAndExcludedBranches() {
        Thoma c2 = thoma(2, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c2, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(simulator, 17.9);
        assertTrue(c2.isCrimsonOoyoroiActive(17.9),
                "Thoma C2 Burst remains active before 18 seconds");
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "C2 late Normal");
        simulator.advanceTime(11.0 * FRAME);
        assertEquals(1, named(
                records, "Crimson Ooyoroi Fiery Collapse").size(),
                "Thoma C2 permits a late Fiery Collapse");
        assertTrue(!c2.isCrimsonOoyoroiActive(18.0),
                "Thoma C2 Burst expires at exact 18 seconds");

        Thoma c1 = thoma(1, 0.75);
        CombatSimulator skillSim = simulatorWith(c1);
        perform(skillSim, CharacterActionKey.SKILL);
        assertClose(9.0 * FRAME + 15.0,
                c1.getSkillCooldownEndTime(),
                "Thoma C1 does not shorten Skill without shield hit");
        Thoma c1Burst = thoma(1, 0.75);
        CombatSimulator burstSim = simulatorWith(c1Burst);
        perform(burstSim, CharacterActionKey.BURST);
        assertClose(20.0, c1Burst.getBurstCooldownEndTime(),
                "Thoma C1 does not shorten Burst without shield hit");

        Thoma c6 = thoma(6, 0.75);
        TestCharacter c6Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c6Sim = simulatorWith(c6, c6Ally);
        perform(c6Sim, CharacterActionKey.BURST);
        c6Sim.setActiveCharacter(CharacterId.NOELLE);
        directHit(c6Sim, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "C6 Normal");
        c6Sim.advanceTime(11.0 * FRAME);
        assertTrue(c6Sim.getTeamBuffList().isEmpty(),
                "Thoma C6 does not synthesize shield-gated damage buffs");
        assertTrue(c6.getActiveBuffs().isEmpty(),
                "Thoma shield and A1 branches remain absent");
    }

    private static void testSnapshotRestore() {
        Thoma comboOwner = thoma(0, 0.75);
        CombatSimulator comboSim = simulatorWith(comboOwner);
        List<ActionRecord> comboRecords = captureActions(comboSim);
        perform(comboSim, CharacterActionKey.NORMAL);
        SimulatorSnapshot comboSnapshot = comboSim.saveSnapshot();
        perform(comboSim, CharacterActionKey.NORMAL);
        comboSim.restoreSnapshot(comboSnapshot);
        perform(comboSim, CharacterActionKey.NORMAL);
        assertEquals(2,
                named(comboRecords, "Swiftshatter Spear N2").size(),
                "Thoma restore preserves Normal combo progression");

        Thoma particleOwner = thoma(0, 0.0);
        CombatSimulator particleSim = simulatorWith(particleOwner);
        List<ParticleRecord> particles = captureParticles(particleSim);
        perform(particleSim, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSim.saveSnapshot();
        advanceTo(particleSim, 112.0 * FRAME);
        assertEquals(1, particles.size(),
                "Thoma live pending particle arrives once");
        particleSim.restoreSnapshot(particleSnapshot);
        particleSim.restoreSnapshot(particleSnapshot);
        advanceTo(particleSim, 112.0 * FRAME);
        assertEquals(2, particles.size(),
                "Thoma restored pending particle arrives once");

        Thoma burstOwner = thoma(0, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator burstSim = simulatorWith(burstOwner, ally);
        List<ActionRecord> burstRecords = captureActions(burstSim);
        perform(burstSim, CharacterActionKey.BURST);
        burstSim.setActiveCharacter(CharacterId.NOELLE);
        directHit(burstSim, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, true, "Snapshot Normal");
        SimulatorSnapshot burstSnapshot = burstSim.saveSnapshot();
        double nextAllowed = burstOwner.getNextFieryCollapseAllowedTime();
        burstSim.advanceTime(11.0 * FRAME);
        List<ActionRecord> collapses = named(
                burstRecords, "Crimson Ooyoroi Fiery Collapse");
        assertEquals(1, collapses.size(),
                "Thoma live pending Fiery Collapse arrives once");
        double baselineDamage = collapses.get(0).damage;
        burstSim.restoreSnapshot(burstSnapshot);
        burstSim.restoreSnapshot(burstSnapshot);
        burstSim.advanceTime(11.0 * FRAME);
        collapses = named(
                burstRecords, "Crimson Ooyoroi Fiery Collapse");
        assertEquals(2, collapses.size(),
                "Thoma restored pending Fiery Collapse arrives once");
        assertClose(baselineDamage, collapses.get(1).damage,
                "Thoma restored Collapse preserves trigger snapshot");
        assertClose(nextAllowed,
                burstOwner.getNextFieryCollapseAllowedTime(),
                "Thoma restore preserves trigger ICD boundary");
        assertTrue(burstOwner.isCrimsonOoyoroiActive(
                        burstSim.getCurrentTime()),
                "Thoma restore preserves Burst window");
        assertEquals(0, burstOwner.getPendingHitCount(),
                "Thoma restored Collapse drains pending-hit state");
    }

    private static void testBindingAndInputGuards() {
        Thoma thoma = thoma(0, 0.75);
        CombatSimulator simulator = simulatorWith(thoma);
        assertThrows(IllegalArgumentException.class,
                () -> thoma.onAction(null, simulator),
                "Thoma rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Thoma rejects unsupported Dash");
        Thoma external = thoma(0, 0.75);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Thoma rejects binding outside simulator party");
        Thoma reused = thoma(0, 0.75);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Thoma rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!thoma.acceptsCharacterState(foreignState),
                "Thoma rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> thoma.restoreCharacterState(
                        foreignState, simulator),
                "Thoma rejects foreign restore payload");

        Thoma invalidRandom = new Thoma(
                null,
                null,
                TalentDataManager.getInstance(),
                0,
                () -> 1.0);
        CombatSimulator invalidSim = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSim, CharacterActionKey.SKILL),
                "Thoma rejects out-of-range particle draws");
        assertThrows(IllegalArgumentException.class,
                () -> new Thoma(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Thoma rejects null particle random source");
    }

    private static Thoma thoma(int constellation, double particleDraw) {
        return new Thoma(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw);
    }

    private static CombatSimulator simulatorWith(
            Thoma thoma,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(thoma);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.THOMA);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.THOMA, CharacterActionRequest.of(key));
    }

    private static void directHit(
            CombatSimulator simulator,
            CharacterId actor,
            ActionType actionType,
            double multiplier,
            boolean hitEffect,
            String name) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(hitEffect);
        simulator.performActionWithoutTimeAdvance(actor, action);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(
                        actor.getCharacterId(), action, damage, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
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
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
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
            assertTrue(lines.get(index).startsWith("Thoma,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Thoma/Thoma_Status.csv",
                "config/characters/Thoma/Thoma_Multipliers.csv"
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
        throw new AssertionError("Thoma CSVs missing key " + key);
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
        if (!java.util.Objects.equals(expected, actual)) {
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
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ActionRecord {
        private final CharacterId actor;
        private final AttackAction action;
        private final double damage;
        private final double time;

        private ActionRecord(
                CharacterId actor,
                AttackAction action,
                double damage,
                double time) {
            this.actor = actor;
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
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return null;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}
