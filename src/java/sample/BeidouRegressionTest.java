package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.character.Beidou;
import model.character.Noelle;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.action.SkillActionMode;

/** Focused regression checks for Beidou's offensive single-target slice. */
public final class BeidouRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private BeidouRegressionTest() {
    }

    /** Runs Beidou's data, timing, trigger, constellation, and rollback cases. */
    public static void main(String[] args) throws IOException {
        testIdentityStatsAndCsvData();
        testNormalString();
        testTidecallerTimingParticlesAndConstellation();
        testStormbreakerInitialAndConstellation();
        testDischargeTriggerGatesAndBoundaries();
        testExtraAttackTrigger();
        testC6ResistanceWindow();
        testSnapshotRestore();
        testAbnormalCases();
        System.out.println("BeidouRegressionTest passed");
    }

    private static void testIdentityStatsAndCsvData() throws IOException {
        Beidou beidou = new Beidou(null, null);
        assertEquals(CharacterId.BEIDOU, beidou.getCharacterId(),
                "Beidou typed identity");
        assertEquals(Element.ELECTRO, beidou.getElement(),
                "Beidou element");
        assertClose(13050.0,
                beidou.getBaseStats().get(StatType.BASE_HP),
                "Beidou base HP");
        assertClose(225.0,
                beidou.getBaseStats().get(StatType.BASE_ATK),
                "Beidou base ATK");
        assertClose(648.0,
                beidou.getBaseStats().get(StatType.BASE_DEF),
                "Beidou base DEF");
        assertClose(0.24,
                beidou.getBaseStats().get(StatType.ELECTRO_DMG_BONUS),
                "Beidou ascension Electro bonus");
        assertClose(7.5, beidou.getSkillCD(),
                "Beidou Skill cooldown");
        assertClose(20.0, beidou.getBurstCD(),
                "Beidou Burst cooldown");
        assertClose(80.0, beidou.getEnergyCost(),
                "Beidou Energy cost");
        assertCsvShape(
                Path.of("config/characters/Beidou/Beidou_Status.csv"),
                10);
        assertCsvShape(
                Path.of("config/characters/Beidou/Beidou_Multipliers.csv"),
                12);
    }

    private static void testNormalString() {
        Beidou beidou = new Beidou(null, null, 0);
        CombatSimulator simulator = simulatorWith(beidou);
        List<ActionRecord> records = captureNamedActions(
                simulator, "Oceanborne N");
        int[] hitmarks = { 23, 22, 45, 25, 43 };
        int[] durations = { 31, 36, 54, 36, 96 };
        double[] multipliers = {
                1.30666, 1.30192, 1.62266, 1.58948, 2.06032
        };
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertClose(castTime + hitmarks[step] * FRAME,
                    record.time,
                    "Beidou N" + (step + 1) + " hitmark");
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Beidou N" + (step + 1) + " multiplier");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Beidou N" + (step + 1) + " duration");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Beidou Normal action type");
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Beidou Normal element");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(records.get(5).action.getName().endsWith("N1"),
                "Beidou Normal chain wraps after N5");

        Beidou reset = new Beidou(null, null, 0);
        CombatSimulator resetSim = simulatorWith(reset);
        List<ActionRecord> resetRecords = captureNamedActions(
                resetSim, "Oceanborne N");
        perform(resetSim, CharacterActionKey.NORMAL);
        perform(resetSim, CharacterActionKey.SKILL);
        perform(resetSim, CharacterActionKey.NORMAL);
        assertTrue(resetRecords.get(1).action.getName().endsWith("N1"),
                "Beidou non-Normal action resets chain");

        Beidou switched = new Beidou(null, null, 0);
        Noelle ally = new Noelle(null, null);
        CombatSimulator switchSim = simulatorWith(switched, ally);
        List<ActionRecord> switchRecords = captureNamedActions(
                switchSim, "Oceanborne N");
        perform(switchSim, CharacterActionKey.NORMAL);
        switchSim.switchCharacter(CharacterId.NOELLE);
        switchSim.switchCharacter(CharacterId.BEIDOU);
        perform(switchSim, CharacterActionKey.NORMAL);
        assertTrue(switchRecords.get(1).action.getName().endsWith("N1"),
                "Beidou switch-out resets Normal chain");
    }

    private static void testTidecallerTimingParticlesAndConstellation() {
        Beidou c0 = new Beidou(null, null, 0);
        c0.addBuff(new SimpleBuff(
                "Expired before Tidecaller hit",
                0.25,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> skills = captureNamedActions(
                simulator, "Tidecaller Press");
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, skills.size(), "Tidecaller hit count");
        AttackAction skill = skills.get(0).action;
        assertClose(23.0 * FRAME, skills.get(0).time,
                "Tidecaller hitmark");
        assertClose(2.0672, skill.getDamagePercent(),
                "Tidecaller C0 multiplier");
        assertEquals(ICDType.None, skill.getICDType(),
                "Tidecaller has no ICD");
        assertClose(2.0, skill.getGaugeUnits(),
                "Tidecaller Electro gauge");
        assertTrue(!skill.hasStatSnapshot(),
                "Tidecaller evaluates stats dynamically");
        assertClose(45.0 * FRAME, simulator.getCurrentTime(),
                "Tidecaller action duration");
        assertClose(4.0 * FRAME + 7.5,
                c0.getSkillCooldownEndTime(),
                "Tidecaller cooldown starts at frame 4");
        assertEquals(0, particles.size(),
                "Tidecaller particles have travel delay");
        advanceTo(simulator, 123.0 * FRAME);
        assertEquals(1, particles.size(),
                "Tidecaller emits one particle packet");
        assertClose(2.0, particles.get(0).count,
                "Tidecaller zero-counter particle count");
        assertClose(123.0 * FRAME, particles.get(0).time,
                "Tidecaller particle arrival");

        Beidou c3 = new Beidou(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Skills = captureNamedActions(
                c3Sim, "Tidecaller Press");
        perform(c3Sim, CharacterActionKey.SKILL);
        assertClose(2.432, c3Skills.get(0).action.getDamagePercent(),
                "Beidou C3 raises Tidecaller to level 12");

    }

    private static void testStormbreakerInitialAndConstellation() {
        Beidou c0 = new Beidou(null, null, 0);
        c0.addBuff(new SimpleBuff(
                "Expired before Burst initial",
                0.25,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> initial = captureNamedActions(
                simulator, "Stormbreaker Initial");
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, initial.size(),
                "Stormbreaker initial hit count");
        assertClose(28.0 * FRAME, initial.get(0).time,
                "Stormbreaker initial hitmark");
        assertClose(2.0672,
                initial.get(0).action.getDamagePercent(),
                "Stormbreaker C0 initial multiplier");
        assertEquals(ICDType.None,
                initial.get(0).action.getICDType(),
                "Stormbreaker initial has no ICD");
        assertClose(4.0,
                initial.get(0).action.getGaugeUnits(),
                "Stormbreaker initial Electro gauge");
        assertTrue(!initial.get(0).action.hasStatSnapshot(),
                "Stormbreaker initial evaluates stats dynamically");
        assertClose(58.0 * FRAME, simulator.getCurrentTime(),
                "Stormbreaker action duration");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Stormbreaker spends 80 Energy at frame 6");
        assertClose(20.0, c0.getBurstCooldownEndTime(),
                "Stormbreaker starts 20-second cooldown at cast");
        assertTrue(c0.isStormbreakerActive(15.0 - EPSILON),
                "Stormbreaker remains active before 15 seconds");
        assertTrue(!c0.isStormbreakerActive(15.0),
                "Stormbreaker expires at exactly 15 seconds");

        Beidou c5 = new Beidou(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Hits = captureNamedActions(
                c5Sim, "Stormbreaker Initial");
        perform(c5Sim, CharacterActionKey.BURST);
        assertClose(2.432,
                c5Hits.get(0).action.getDamagePercent(),
                "Beidou C5 raises Burst initial to level 12");
    }

    private static void testDischargeTriggerGatesAndBoundaries() {
        Beidou c0 = new Beidou(null, null, 0);
        Noelle noelle = new Noelle(null, null);
        CombatSimulator simulator = simulatorWith(c0, noelle);
        List<ActionRecord> discharges = captureNamedActions(
                simulator, "Stormbreaker Lightning Discharge");
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        double firstTrigger = simulator.getCurrentTime();
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, "Active Normal");
        assertEquals(0, discharges.size(),
                "Discharge lands one frame after trigger");
        simulator.advanceTime(FRAME);
        assertEquals(1, discharges.size(),
                "Active-character Normal triggers Discharge");
        AttackAction discharge = discharges.get(0).action;
        assertClose(firstTrigger + FRAME, discharges.get(0).time,
                "Discharge one-frame delay");
        assertClose(1.632, discharge.getDamagePercent(),
                "C0 Discharge multiplier");
        assertEquals(ActionType.BURST, discharge.getActionType(),
                "Discharge damage type");
        assertEquals(ICDType.Standard, discharge.getICDType(),
                "Discharge standard ICD");
        assertEquals(ICDTag.ElementalBurst, discharge.getICDTag(),
                "Discharge shared Burst ICD tag");
        assertClose(1.0, discharge.getGaugeUnits(),
                "Discharge Electro gauge");
        assertTrue(discharge.hasStatSnapshot(),
                "Discharge owns Burst-cast stats");

        advanceTo(simulator, firstTrigger + 1.0 - EPSILON * 10.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.CHARGE, 1.0, "Early Charged");
        assertEquals(1, discharges.size(),
                "Discharge rejects trigger before one second");
        advanceTo(simulator, firstTrigger + 1.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.CHARGE, 1.0, "Exact Charged");
        simulator.advanceTime(FRAME);
        assertEquals(2, discharges.size(),
                "Discharge accepts exact one-second boundary");

        advanceTo(simulator, firstTrigger + 2.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.SKILL, 1.0, "Skill gate");
        directHit(simulator, CharacterId.NOELLE,
                ActionType.BURST, 1.0, "Burst gate");
        directHit(simulator, CharacterId.NOELLE,
                ActionType.PLUNGE, 1.0, "Plunge gate");
        simulator.advanceTime(FRAME);
        assertEquals(2, discharges.size(),
                "Skill, Burst, and Plunge do not trigger Discharge");

        Beidou zeroDamage = new Beidou(null, null, 0);
        Noelle zeroDamageAlly = new Noelle(null, null);
        CombatSimulator zeroDamageSim = simulatorWith(
                zeroDamage, zeroDamageAlly);
        List<ActionRecord> zeroDamageDischarges = captureNamedActions(
                zeroDamageSim, "Stormbreaker Lightning Discharge");
        perform(zeroDamageSim, CharacterActionKey.BURST);
        zeroDamageSim.setActiveCharacter(CharacterId.NOELLE);
        directHit(zeroDamageSim, CharacterId.NOELLE,
                ActionType.NORMAL, 0.0, "Shielded zero-damage Normal");
        zeroDamageSim.advanceTime(FRAME);
        assertEquals(1, zeroDamageDischarges.size(),
                "Zero-damage Normal hit triggers Discharge");

        advanceTo(simulator, firstTrigger + 3.0);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.BEIDOU,
                ActionType.NORMAL, 1.0, "Off-field owner");
        simulator.advanceTime(FRAME);
        assertEquals(2, discharges.size(),
                "Off-field direct damage does not trigger");

        advanceTo(simulator, 15.0);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.NORMAL, 1.0, "Expiry hit");
        simulator.advanceTime(FRAME);
        assertEquals(2, discharges.size(),
                "Stormbreaker rejects trigger at exact expiry");

        Beidou c2 = new Beidou(null, null, 2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Discharges = captureNamedActions(
                c2Sim, "Stormbreaker Lightning Discharge");
        perform(c2Sim, CharacterActionKey.BURST);
        directHit(c2Sim, CharacterId.BEIDOU,
                ActionType.NORMAL, 1.0, "C2 single target");
        c2Sim.advanceTime(FRAME);
        assertEquals(1, c2Discharges.size(),
                "C2 has no additional single-target bounce");

        Beidou c5 = new Beidou(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Discharges = captureNamedActions(
                c5Sim, "Stormbreaker Lightning Discharge");
        perform(c5Sim, CharacterActionKey.BURST);
        directHit(c5Sim, CharacterId.BEIDOU,
                ActionType.NORMAL, 1.0, "C5 trigger");
        c5Sim.advanceTime(FRAME);
        assertClose(1.92,
                c5Discharges.get(0).action.getDamagePercent(),
                "Beidou C5 raises Discharge to level 12");
    }

    private static void testExtraAttackTrigger() {
        Beidou beidou = new Beidou(null, null, 0);
        Noelle noelle = new Noelle(null, null);
        CombatSimulator simulator = simulatorWith(beidou, noelle);
        List<ActionRecord> discharges = captureNamedActions(
                simulator, "Stormbreaker Lightning Discharge");
        perform(simulator, CharacterActionKey.BURST);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        directHit(simulator, CharacterId.NOELLE,
                ActionType.EXTRA, 1.0, "Extra Attack trigger");
        simulator.advanceTime(FRAME);
        assertEquals(1, discharges.size(),
                "Extra Attack triggers Stormbreaker Discharge");
    }

    private static void testC6ResistanceWindow() {
        Beidou c5 = new Beidou(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Initial = captureNamedActions(
                c5Sim, "Stormbreaker Initial");
        perform(c5Sim, CharacterActionKey.BURST);

        Beidou c6 = new Beidou(null, null, 6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Initial = captureNamedActions(
                c6Sim, "Stormbreaker Initial");
        perform(c6Sim, CharacterActionKey.BURST);
        assertClose(c5Initial.get(0).damage, c6Initial.get(0).damage,
                "C6 starts after Stormbreaker initial hit");
        Buff shred = findTeamBuff(
                c6Sim, BuffId.BEIDOU_C6_ELECTRO_RES_SHRED);
        assertTrue(shred != null, "C6 creates typed resistance shred");
        assertClose(30.0 * FRAME, shred.getStartTime(),
                "C6 starts at frame 30");
        assertClose(990.0 * FRAME, shred.getExpirationTime(),
                "C6 expires at frame 990");
        StatsContainer before = new StatsContainer();
        shred.apply(before, 30.0 * FRAME - EPSILON);
        assertClose(0.0, before.get(StatType.ELECTRO_RES_SHRED),
                "C6 inactive before frame 30");
        StatsContainer atStart = new StatsContainer();
        shred.apply(atStart, 30.0 * FRAME);
        assertClose(0.15,
                atStart.get(StatType.ELECTRO_RES_SHRED),
                "C6 active at frame 30");
        StatsContainer atEnd = new StatsContainer();
        shred.apply(atEnd, 990.0 * FRAME);
        assertClose(0.0, atEnd.get(StatType.ELECTRO_RES_SHRED),
                "C6 uses half-open frame-990 expiry");
    }

    private static void testSnapshotRestore() {
        Beidou skillOwner = new Beidou(null, null, 0);
        CombatSimulator skillSim = simulatorWith(skillOwner);
        List<ParticleRecord> particles = captureParticles(skillSim);
        perform(skillSim, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = skillSim.saveSnapshot();
        advanceTo(skillSim, 123.0 * FRAME);
        assertEquals(1, particles.size(),
                "Baseline pending particle arrives");
        skillSim.restoreSnapshot(particleSnapshot);
        skillSim.restoreSnapshot(particleSnapshot);
        advanceTo(skillSim, 123.0 * FRAME);
        assertEquals(2, particles.size(),
                "Repeated restore reconstructs one particle packet");

        Beidou burstOwner = new Beidou(null, null, 0);
        CombatSimulator burstSim = simulatorWith(burstOwner);
        List<ActionRecord> discharges = captureNamedActions(
                burstSim, "Stormbreaker Lightning Discharge");
        perform(burstSim, CharacterActionKey.BURST);
        directHit(burstSim, CharacterId.BEIDOU,
                ActionType.NORMAL, 1.0, "Snapshot trigger");
        SimulatorSnapshot dischargeSnapshot = burstSim.saveSnapshot();
        burstSim.advanceTime(FRAME);
        assertEquals(1, discharges.size(),
                "Baseline pending Discharge arrives");
        double baselineDamage = discharges.get(0).damage;
        burstSim.restoreSnapshot(dischargeSnapshot);
        burstSim.restoreSnapshot(dischargeSnapshot);
        burstSim.advanceTime(FRAME);
        assertEquals(2, discharges.size(),
                "Repeated restore reconstructs one Discharge");
        assertClose(baselineDamage, discharges.get(1).damage,
                "Restored Discharge preserves Burst snapshot");
        assertTrue(burstOwner.isStormbreakerActive(
                        burstSim.getCurrentTime()),
                "Restore preserves Stormbreaker state");
        assertClose(dischargeSnapshot.currentTime + 1.0,
                burstOwner.getNextDischargeAllowedTime(),
                "Restore preserves next trigger boundary");
    }

    private static void testAbnormalCases() {
        assertThrows(IllegalArgumentException.class,
                () -> new Beidou(null, null, -1),
                "Beidou rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Beidou(null, null, 7),
                "Beidou rejects constellation above six");
        Beidou beidou = new Beidou(null, null, 0);
        CombatSimulator simulator = simulatorWith(beidou);
        assertThrows(IllegalArgumentException.class,
                () -> beidou.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, simulator),
                "Beidou rejects foreign snapshot state");
        assertThrows(IllegalStateException.class,
                () -> beidou.initializeForSimulator(
                        new CombatSimulator()),
                "Beidou rejects cross-simulator reuse");
        assertThrows(IllegalArgumentException.class,
                () -> beidou.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.CHARGE),
                        simulator),
                "Beidou explicitly excludes Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> beidou.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.PLUNGE),
                        simulator),
                "Beidou explicitly excludes Plunging Attack");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.BEIDOU,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Beidou explicitly excludes Hold Skill");

        Beidou insufficient = new Beidou(null, null, 0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        insufficient.spendEnergy(80.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertClose(-999.0, insufficient.getLastBurstTime(),
                "Skipped Burst does not start cooldown");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Skipped Burst records missing Energy");
    }

    private static CombatSimulator simulatorWith(
            Beidou beidou,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        simulator.addCharacter(beidou);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.BEIDOU);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.BEIDOU,
                CharacterActionRequest.of(key));
    }

    private static void directHit(
            CombatSimulator simulator,
            CharacterId actor,
            ActionType actionType,
            double multiplier,
            String name) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        simulator.performActionWithoutTimeAdvance(actor, action);
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator simulator,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.BEIDOU
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static Buff findTeamBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        return null;
    }

    private static void assertCsvShape(
            Path path,
            int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6,
                    lines.get(index).split(",", -1).length,
                    path + " column count line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Beidou,"),
                    path + " identity line " + (index + 1));
        }
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
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
        } catch (Throwable failure) {
            if (expected.isInstance(failure)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got " + failure,
                    failure);
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
}
