package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.character.Noelle;
import model.character.Sayu;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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
import simulation.event.SimpleTimerEvent;

/** Focused data, timing, snapshot, constellation, and guard checks for Sayu. */
public final class SayuRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private SayuRegressionTest() {
    }

    /** Runs Sayu's complete bounded offensive-slice regression cases. */
    public static void main(String[] args) throws Exception {
        testDataAndMetadata();
        testNormalChainAndHighPlunge();
        testPressSkillTimingSnapshotParticlesAndConstellations();
        testBurstCadenceEnergySnapshotAndConstellations();
        testC6EndToEndDamageAndRestore();
        testC4ActiveSwirlEnergyGate();
        testSnapshotRestoreAndStaleGenerations();
        testInvalidInputsCooldownAndBindingGuards();
        System.out.println("SayuRegressionTest passed");
    }

    private static void testDataAndMetadata() throws IOException {
        Sayu sayu = new Sayu(null, null);
        assertEquals(CharacterId.SAYU, sayu.getCharacterId(),
                "Sayu identity");
        assertClose(11854.0,
                sayu.getBaseStats().get(StatType.BASE_HP),
                "Sayu base HP");
        assertClose(244.0,
                sayu.getBaseStats().get(StatType.BASE_ATK),
                "Sayu base ATK");
        assertClose(745.0,
                sayu.getBaseStats().get(StatType.BASE_DEF),
                "Sayu base DEF");
        assertClose(96.0,
                sayu.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Sayu ascension EM");
        assertClose(80.0, sayu.getEnergyCost(), "Sayu Energy cost");
        assertClose(6.0, sayu.getSkillCD(), "Sayu Press cooldown");
        assertClose(20.0, sayu.getBurstCD(), "Sayu Burst cooldown");
        assertCsvShape(
                Paths.get("config/characters/Sayu/Sayu_Status.csv"), 10);
        assertCsvShape(
                Paths.get("config/characters/Sayu/Sayu_Multipliers.csv"),
                17);
    }

    private static void testNormalChainAndHighPlunge() {
        Sayu sayu = new Sayu(null, null, 0);
        CombatSimulator simulator = simulatorWith(sayu);
        List<ActionRecord> records = captureSayuActions(simulator);
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(
                records, "Shuumatsuban Ninja Blade N");
        assertEquals(5, normals.size(), "Sayu Normal hit count");
        assertClose(23.0 * FRAME, normals.get(0).time,
                "Sayu N1 hitmark");
        assertClose(65.0 * FRAME, normals.get(1).time,
                "Sayu N2 hitmark");
        assertClose(98.0 * FRAME, normals.get(2).time,
                "Sayu N3 first hitmark");
        assertClose(110.0 * FRAME, normals.get(3).time,
                "Sayu N3 second hitmark");
        assertClose(171.0 * FRAME, normals.get(4).time,
                "Sayu N4 hitmark");
        assertClose(1.3272, normals.get(0).action.getDamagePercent(),
                "Sayu N1 Talent 9 multiplier");
        assertClose(0.7979, normals.get(2).action.getDamagePercent(),
                "Sayu N3 per-hit multiplier");

        double plungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        List<ActionRecord> plunges = named(
                records, "Shuumatsuban Ninja Blade High Plunge");
        assertEquals(1, plunges.size(), "Sayu High Plunge count");
        assertClose(3.422517, plunges.get(0).action.getDamagePercent(),
                "Sayu High Plunge multiplier");
        assertClose(plungeStart + 1.0, simulator.getCurrentTime(),
                "Sayu High Plunge bounded duration");
    }

    private static void testPressSkillTimingSnapshotParticlesAndConstellations() {
        Sayu c0 = new Sayu(null, null, 0);
        c0.addBuff(new SimpleBuff(
                "Sayu cast-only ATK",
                3.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureSayuActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        List<ActionRecord> wheel = named(records, "Fuufuu Windwheel Press");
        List<ActionRecord> kick = named(
                records, "Fuufuu Whirlwind Kick Press");
        assertEquals(1, wheel.size(), "Sayu Press Windwheel count");
        assertEquals(1, kick.size(), "Sayu Press kick count");
        assertClose(7.0 * FRAME, wheel.get(0).time,
                "Sayu Windwheel hitmark");
        assertClose(25.0 * FRAME, kick.get(0).time,
                "Sayu kick hitmark");
        assertClose(0.612, wheel.get(0).action.getDamagePercent(),
                "Sayu Talent 9 Windwheel multiplier");
        assertClose(2.6928, kick.get(0).action.getDamagePercent(),
                "Sayu Talent 9 kick multiplier");
        assertEquals(ICDType.Standard, wheel.get(0).action.getICDType(),
                "Sayu Windwheel Standard ICD");
        assertEquals(ICDTag.ElementalSkill, wheel.get(0).action.getICDTag(),
                "Sayu Windwheel Skill group");
        assertEquals(ICDType.None, kick.get(0).action.getICDType(),
                "Sayu kick applies without ICD");
        assertClose(1.0, wheel.get(0).action.getGaugeUnits(),
                "Sayu Windwheel gauge");
        assertClose(1.0, kick.get(0).action.getGaugeUnits(),
                "Sayu kick gauge");
        assertClose(1.0,
                wheel.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sayu Windwheel preserves cast ATK");
        assertClose(1.0,
                kick.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sayu kick preserves independent cast ATK");
        assertClose(14.0 * FRAME + 6.0,
                c0.getSkillCooldownEndTime(),
                "Sayu cooldown starts at frame 14");
        advanceTo(simulator, 3.0);
        assertEquals(1, particles.size(), "Sayu particle packet count");
        assertClose(2.0, particles.get(0).count,
                "Sayu kick particle count");
        assertClose(125.0 * FRAME, particles.get(0).time,
                "Sayu particle arrival includes 100-frame travel");

        Sayu c2 = new Sayu(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureSayuActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.SKILL);
        AttackAction c2Kick = named(
                c2Records, "Fuufuu Whirlwind Kick Press").get(0).action;
        assertClose(0.033,
                c2Kick.getExtraBonuses().get(StatType.SKILL_DMG_BONUS),
                "Sayu C2 Press kick bonus");

        Sayu c5 = new Sayu(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureSayuActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        assertClose(0.720,
                named(c5Records, "Fuufuu Windwheel Press")
                        .get(0).action.getDamagePercent(),
                "Sayu C5 Windwheel Talent 12");
        assertClose(3.168,
                named(c5Records, "Fuufuu Whirlwind Kick Press")
                        .get(0).action.getDamagePercent(),
                "Sayu C5 kick Talent 12");
    }

    private static void testBurstCadenceEnergySnapshotAndConstellations() {
        Sayu c0 = new Sayu(null, null, 0);
        c0.addBuff(new SimpleBuff(
                "Sayu Burst cast-only ATK",
                3.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureSayuActions(simulator);
        double[] energyAt = new double[2];
        observeEnergy(simulator, c0, 6.0 * FRAME, energyAt, 0);
        observeEnergy(simulator, c0, 7.0 * FRAME + EPSILON, energyAt, 1);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 12.0);
        List<ActionRecord> initial = named(
                records, "Yoohoo Art: Mujina Flurry");
        List<ActionRecord> daruma = named(records, "Muji-Muji Daruma");
        assertEquals(1, initial.size(), "Sayu Burst initial count");
        assertEquals(7, daruma.size(), "Sayu Daruma tick count");
        assertClose(12.0 * FRAME, initial.get(0).time,
                "Sayu Burst initial hitmark");
        assertClose(145.0 * FRAME, daruma.get(0).time,
                "Sayu first Daruma frame");
        assertClose(685.0 * FRAME, daruma.get(6).time,
                "Sayu seventh Daruma frame");
        for (int tick = 1; tick < daruma.size(); tick++) {
            assertClose(90.0 * FRAME,
                    daruma.get(tick).time - daruma.get(tick - 1).time,
                    "Sayu Daruma cadence " + tick);
        }
        assertClose(1.9856, initial.get(0).action.getDamagePercent(),
                "Sayu Burst initial Talent 9");
        assertClose(0.884, daruma.get(0).action.getDamagePercent(),
                "Sayu Daruma Talent 9");
        assertEquals(ICDType.None, initial.get(0).action.getICDType(),
                "Sayu Burst initial no ICD");
        assertEquals(ICDType.Standard,
                daruma.get(0).action.getICDType(),
                "Sayu Daruma Standard ICD");
        assertEquals(ICDTag.ElementalBurst,
                daruma.get(0).action.getICDTag(),
                "Sayu Daruma shared Burst group");
        assertClose(1.0,
                initial.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sayu initial preserves Burst cast ATK");
        assertClose(1.0,
                daruma.get(6).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sayu final Daruma preserves Burst cast ATK");
        assertClose(80.0, energyAt[0],
                "Sayu Energy remains before frame 7");
        assertClose(0.0, energyAt[1],
                "Sayu Energy is consumed at frame 7");
        assertClose(20.0, c0.getBurstCooldownEndTime(),
                "Sayu Burst cooldown begins on cast");

        Sayu c3 = new Sayu(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureSayuActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        advanceTo(c3Simulator, 3.0);
        assertClose(2.336,
                named(c3Records, "Yoohoo Art: Mujina Flurry")
                        .get(0).action.getDamagePercent(),
                "Sayu C3 initial Talent 12");
        assertClose(1.04,
                named(c3Records, "Muji-Muji Daruma")
                        .get(0).action.getDamagePercent(),
                "Sayu C3 Daruma Talent 12");

        Sayu c6 = new Sayu(null, null, 6);
        c6.addBuff(new SimpleBuff(
                "Sayu C6 EM cap probe",
                20.0,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 3000.0)));
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureSayuActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.BURST);
        advanceTo(c6Simulator, 3.0);
        ActionRecord c6Daruma = named(
                c6Records, "Muji-Muji Daruma").get(0);
        assertClose(976.0,
                c6Daruma.action.getStatSnapshot()
                        .get(StatType.FLAT_DMG_BONUS),
                "Sayu C6 caps flat damage at 400 percent snapshot ATK");
    }

    private static void testC4ActiveSwirlEnergyGate() {
        ReactionResult swirl = new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE,
                1.0,
                100.0,
                "Swirl",
                ReactionResult.Kind.SWIRL);
        Sayu c4 = new Sayu(null, null, 4);
        Noelle ally = new Noelle(null, null);
        CombatSimulator simulator = simulatorWith(c4, ally);
        c4.spendEnergy(80.0);
        simulator.getEnemy().setAura(Element.PYRO, 2.0);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(1.2, c4.getCurrentEnergy(),
                "Sayu C4 receives a real Skill Swirl notification");
        double c4Boundary = 7.0 * FRAME + 2.0;
        c4.onReaction(
                swirl, c4, c4Boundary - 2.0 * EPSILON, simulator);
        assertClose(1.2, c4.getCurrentEnergy(),
                "Sayu C4 rejects before two-second boundary");
        c4.onReaction(swirl, c4, c4Boundary, simulator);
        assertClose(2.4, c4.getCurrentEnergy(),
                "Sayu C4 accepts exact two-second boundary");
        simulator.setActiveCharacter(CharacterId.NOELLE);
        c4.onReaction(swirl, c4, c4Boundary + 2.0, simulator);
        assertClose(2.4, c4.getCurrentEnergy(),
                "Sayu C4 rejects off-field Swirl");
        simulator.setActiveCharacter(CharacterId.SAYU);
        c4.onReaction(swirl, ally, c4Boundary + 2.0, simulator);
        assertClose(2.4, c4.getCurrentEnergy(),
                "Sayu C4 rejects ally-triggered Swirl");

        Sayu c3 = new Sayu(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        c3.spendEnergy(80.0);
        c3.onReaction(swirl, c3, 0.0, c3Simulator);
        assertClose(0.0, c3.getCurrentEnergy(),
                "Sayu C4 Energy does not leak into C3");
    }

    private static void testC6EndToEndDamageAndRestore() {
        Sayu c5 = new Sayu(null, null, 5);
        c5.addBuff(elementalMasteryBuff("Sayu C5 EM probe"));
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureSayuActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        advanceTo(c5Simulator, 12.0);
        List<ActionRecord> c5Daruma = named(
                c5Records, "Muji-Muji Daruma");

        Sayu c6 = new Sayu(null, null, 6);
        c6.addBuff(elementalMasteryBuff("Sayu C6 EM probe"));
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureSayuActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = c6Simulator.saveSnapshot();
        advanceTo(c6Simulator, 12.0);
        List<ActionRecord> firstBranch = named(
                c6Records, "Muji-Muji Daruma");
        assertEquals(7, firstBranch.size(),
                "Sayu C6 first branch tick count");
        assertClose(244.0 * 196.0 * 0.002,
                firstBranch.get(0).action.getStatSnapshot()
                        .get(StatType.FLAT_DMG_BONUS),
                "Sayu C6 uncapped EM flat damage");
        double c6DamageGain = firstBranch.get(0).damage
                - c5Daruma.get(0).damage;
        assertTrue(c6DamageGain > 0.0,
                "Sayu C6 increases resolved Daruma damage");
        for (int tick = 1; tick < firstBranch.size(); tick++) {
            assertClose(c6DamageGain,
                    firstBranch.get(tick).damage - c5Daruma.get(tick).damage,
                    "Sayu C6 constant additive damage tick " + tick);
        }

        c6Simulator.restoreSnapshot(snapshot);
        advanceTo(c6Simulator, 12.0);
        List<ActionRecord> bothBranches = named(
                c6Records, "Muji-Muji Daruma");
        assertEquals(14, bothBranches.size(),
                "Sayu C6 restored branch tick count");
        for (int tick = 0; tick < 7; tick++) {
            assertClose(firstBranch.get(tick).damage,
                    bothBranches.get(tick + 7).damage,
                    "Sayu C6 restored damage tick " + tick);
        }
    }

    private static SimpleBuff elementalMasteryBuff(String name) {
        return new SimpleBuff(
                name,
                20.0,
                0.0,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 100.0));
    }

    private static void testSnapshotRestoreAndStaleGenerations() {
        Sayu skillSayu = new Sayu(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillSayu);
        List<ParticleRecord> particles = captureAnemoParticles(skillSimulator);
        perform(skillSimulator, CharacterActionKey.SKILL);
        SimulatorSnapshot skillSnapshot = skillSimulator.saveSnapshot();
        advanceTo(skillSimulator, 3.0);
        assertEquals(1, particles.size(),
                "Sayu original Skill branch resolves particle packet");
        skillSimulator.restoreSnapshot(skillSnapshot);
        advanceTo(skillSimulator, 3.0);
        assertEquals(2, particles.size(),
                "Sayu restored Skill branch resolves once");
        skillSimulator.restoreSnapshot(skillSnapshot);
        skillSimulator.restoreSnapshot(skillSnapshot);
        advanceTo(skillSimulator, 3.0);
        assertEquals(3, particles.size(),
                "Sayu repeated restore keeps one Skill packet");

        Sayu burstSayu = new Sayu(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burstSayu);
        List<ActionRecord> burstRecords = captureSayuActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSimulator.saveSnapshot();
        advanceTo(burstSimulator, 12.0);
        assertEquals(7, named(burstRecords, "Muji-Muji Daruma").size(),
                "Sayu original Burst branch resolves seven ticks");
        burstSimulator.restoreSnapshot(burstSnapshot);
        advanceTo(burstSimulator, 12.0);
        assertEquals(14, named(burstRecords, "Muji-Muji Daruma").size(),
                "Sayu restored Burst branch resolves once");

        Sayu staleSkill = new Sayu(null, null, 0);
        CombatSimulator staleSkillSimulator = simulatorWith(staleSkill);
        List<ParticleRecord> staleParticles = captureAnemoParticles(
                staleSkillSimulator);
        staleSkill.onAction(
                CharacterActionRequest.skill(SkillActionMode.PRESS),
                staleSkillSimulator);
        staleSkill.onAction(
                CharacterActionRequest.skill(SkillActionMode.PRESS),
                staleSkillSimulator);
        advanceTo(staleSkillSimulator, 4.0);
        assertEquals(1, staleParticles.size(),
                "Sayu stale Skill particle is suppressed");

        Sayu staleBurst = new Sayu(null, null, 0);
        CombatSimulator staleBurstSimulator = simulatorWith(staleBurst);
        List<ActionRecord> staleBurstRecords = captureSayuActions(
                staleBurstSimulator);
        staleBurst.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                staleBurstSimulator);
        staleBurst.restoreCurrentEnergy(80.0);
        staleBurst.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST),
                staleBurstSimulator);
        advanceTo(staleBurstSimulator, 14.0);
        assertEquals(7,
                named(staleBurstRecords, "Muji-Muji Daruma").size(),
                "Sayu stale Daruma generation is suppressed");
    }

    private static void testInvalidInputsCooldownAndBindingGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Sayu(null, null, -1),
                "Sayu rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Sayu(null, null, 7),
                "Sayu rejects constellation above six");

        Sayu unsupported = new Sayu(null, null, 0);
        CombatSimulator unsupportedSimulator = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSimulator),
                "Sayu rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> unsupportedSimulator.performAction(
                        CharacterId.SAYU,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Sayu rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(
                        unsupportedSimulator, CharacterActionKey.CHARGE),
                "Sayu rejects Charged Attack");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSimulator, CharacterActionKey.DASH),
                "Sayu rejects Dash");

        Sayu insufficient = new Sayu(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureSayuActions(
                insufficientSimulator);
        insufficient.spendEnergy(80.0);
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Sayu insufficient Energy rejects Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Sayu records rejected Burst cost");

        Sayu cooldown = new Sayu(null, null, 0);
        CombatSimulator cooldownSimulator = simulatorWith(cooldown);
        perform(cooldownSimulator, CharacterActionKey.SKILL);
        perform(cooldownSimulator, CharacterActionKey.SKILL);
        assertClose(418.0 * FRAME, cooldownSimulator.getCurrentTime(),
                "Sayu serializes Press Skill at cooldown boundary");

        Sayu reusable = new Sayu(null, null, 0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Sayu rejects cross-simulator reuse");
        Sayu stateOwner = new Sayu(null, null, 0);
        Sayu foreign = new Sayu(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> stateOwner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Sayu rejects another Sayu's state");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
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
                CharacterId.SAYU, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureSayuActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.SAYU) {
                records.add(new ActionRecord(action, damage, time));
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
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static void observeEnergy(
            CombatSimulator simulator,
            Sayu sayu,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = sayu.getCurrentEnergy();
            }
        });
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
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Sayu,"),
                    path + " identity at line " + (index + 1));
        }
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": unexpected " + throwable,
                    throwable);
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
