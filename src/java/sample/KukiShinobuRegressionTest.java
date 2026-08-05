package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.reaction.ReactionResult;
import model.character.KukiShinobu;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Kuki Shinobu's fixed-full-HP kit. */
public final class KukiShinobuRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KukiShinobuRegressionTest() {
    }

    /** Runs Kuki's data, timing, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstellations();
        testNormalAndChargedAttacks();
        testC0SkillTimelineIcdA4AndParticles();
        testC2AndC3SkillUpgrades();
        testBurstSnapshotEnergyCooldownAndC5();
        testC4TriggersScalingCooldownAndParticles();
        testSwitchPersistence();
        testSnapshotRestoreWithoutDuplicateWork();
        testInvalidInputsAndIsolation();
        System.out.println("KukiShinobuRegressionTest passed");
    }

    private static void testIdentityDataAndConstellations()
            throws IOException {
        KukiShinobu kuki = kuki(0, () -> 0.99);
        assertEquals(CharacterId.KUKI_SHINOBU, kuki.getCharacterId(),
                "Kuki typed identity");
        assertEquals(CharacterId.KUKI_SHINOBU,
                CharacterId.fromName("Kuki Shinobu"),
                "Kuki name lookup");
        assertEquals(CharacterId.KUKI_SHINOBU,
                CharacterId.fromNumericId(45),
                "Kuki numeric lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KUKI_SHINOBU.getRegion(),
                "Kuki region");
        assertEquals(Element.ELECTRO, kuki.getElement(), "Kuki element");
        assertClose(12289.0,
                kuki.getBaseStats().get(StatType.BASE_HP),
                "Kuki base HP");
        assertClose(212.0,
                kuki.getBaseStats().get(StatType.BASE_ATK),
                "Kuki base ATK");
        assertClose(751.0,
                kuki.getBaseStats().get(StatType.BASE_DEF),
                "Kuki base DEF");
        assertClose(0.24,
                kuki.getBaseStats().get(StatType.HP_PERCENT),
                "Kuki ascension HP");
        assertClose(60.0, kuki.getEnergyCost(), "Kuki Energy cost");
        assertClose(15.0, kuki.getSkillCD(), "Kuki Skill cooldown");
        assertClose(15.0, kuki.getBurstCD(), "Kuki Burst cooldown");

        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation,
                    kuki(constellation, () -> 0.99).getConstellation(),
                    "Kuki constructs C" + constellation);
        }
        assertEquals(6, new KukiShinobu(null, null).getConstellation(),
                "Kuki repository default is C6");
        assertThrows(IllegalArgumentException.class,
                () -> kuki(-1, () -> 0.99),
                "Kuki rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> kuki(7, () -> 0.99),
                "Kuki rejects constellation above C6");

        assertCsvShape(Path.of(
                "config/characters/KukiShinobu/KukiShinobu_Status.csv"),
                10);
        assertCsvShape(Path.of(
                "config/characters/KukiShinobu/KukiShinobu_Multipliers.csv"),
                69);
        assertCsvValue("Base HP", 12289.0);
        assertCsvValue("Ascension HP Percent", 0.24);
        assertCsvValue("N4", 1.3983);
        assertCsvValue("Skill Initial", 1.287104);
        assertCsvValue("Grass Ring", 0.42908);
        assertCsvValue("Single Instance Max HP", 0.061282);
        assertCsvValue("C4 Thundergrass Mark Max HP", 0.097);
    }

    private static void testNormalAndChargedAttacks() {
        KukiShinobu kuki = kuki(0, () -> 0.99);
        CombatSimulator simulator = simulatorWith(kuki);
        List<ActionRecord> records = captureKukiActions(simulator);
        int[] hitFrames = { 12, 13, 13, 23 };
        int[] recoveryFrames = { 19, 17, 42, 57 };
        int[] absoluteHitFrames = { 12, 32, 49, 101 };
        double[] multipliers = { 0.89586, 0.81844, 1.0902, 1.3983 };
        double[] castTimes = new double[4];
        for (int step = 0; step < 4; step++) {
            castTimes[step] = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            assertClose(castTimes[step] + recoveryFrames[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Kuki Normal recovery " + (step + 1));
            ActionRecord record = onlyNamed(records,
                    "Shinobu's Shadowsword N" + (step + 1));
            assertClose(castTimes[step] + hitFrames[step] * FRAME,
                    record.time, "Kuki Normal hit frame " + (step + 1));
            assertClose(absoluteHitFrames[step] * FRAME, record.time,
                    "Kuki chained Normal absolute hit frame " + (step + 1));
            assertClose(multipliers[step],
                    record.action.getDamagePercent(),
                    "Kuki Normal multiplier " + (step + 1));
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Kuki Normal element");
            assertEquals(ActionType.NORMAL, record.action.getActionType(),
                    "Kuki Normal category");
            assertEquals(ICDType.None, record.action.getICDType(),
                    "Kuki physical Normal has no ICD");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2,
                named(records, "Shinobu's Shadowsword N1").size(),
                "Kuki Normal string wraps after N4");

        KukiShinobu charged = kuki(0, () -> 0.99);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureKukiActions(
                chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertClose(35.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Kuki Charged recovery");
        double[] chargedMultipliers = { 1.022102, 1.226617 };
        int[] chargedFrames = { 14, 25 };
        for (int index = 0; index < 2; index++) {
            ActionRecord record = onlyNamed(chargedRecords,
                    "Shinobu's Shadowsword Charged " + (index + 1));
            assertClose(chargedFrames[index] * FRAME, record.time,
                    "Kuki Charged hit frame " + (index + 1));
            assertClose(chargedMultipliers[index],
                    record.action.getDamagePercent(),
                    "Kuki Charged multiplier " + (index + 1));
            assertEquals(ActionType.CHARGE, record.action.getActionType(),
                    "Kuki Charged category");
            assertEquals(ICDType.None, record.action.getICDType(),
                    "Kuki physical Charged has no ICD");
        }
    }

    private static void testC0SkillTimelineIcdA4AndParticles() {
        SequenceDraw draws = new SequenceDraw(
                0.0, 0.9, 0.0, 0.9, 0.0, 0.9, 0.0, 0.9);
        KukiShinobu kuki = kuki(0, draws);
        kuki.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 100.0);
        CombatSimulator simulator = simulatorWith(kuki);
        List<ActionRecord> records = captureKukiActions(simulator);
        List<ParticleRecord> particles = captureElectroParticles(simulator);
        int[] overloads = { 0 };
        simulator.getEnemy().setAura(Element.PYRO, 100.0);
        simulator.addReactionListener((result, source, time, active) -> {
            if (result.getKind() == ReactionResult.Kind.OVERLOAD) {
                overloads[0]++;
            }
        });
        addStatBuffAt(simulator, kuki, 5.0 * FRAME, 30.0 * FRAME,
                "Kuki post-cast initial EM",
                StatType.ELEMENTAL_MASTERY, 100.0);
        addStatBuffAt(simulator, kuki, 112.0 * FRAME, 2.0 * FRAME,
                "Kuki ring-bell-only EM",
                StatType.ELEMENTAL_MASTERY, 300.0);
        addStatBuffAt(simulator, kuki, 114.0 * FRAME, 100.0,
                "Kuki ring-hit-only EM",
                StatType.ELEMENTAL_MASTERY, 200.0);

        perform(simulator, CharacterActionKey.SKILL);
        assertClose(52.0 * FRAME, simulator.getCurrentTime(),
                "Kuki Skill recovery");
        assertClose(7.0 * FRAME, kuki.getLastSkillTime(),
                "Kuki Skill cooldown starts at frame seven");
        assertClose(15.0 + 7.0 * FRAME,
                kuki.getSkillCooldownEndTime(),
                "Kuki Skill cooldown end");
        assertTrue(kuki.isRingActive(simulator.getCurrentTime()),
                "Kuki ring activates at frame 23");
        assertClose(23.0 * FRAME + 12.0,
                kuki.getRingExpirationTime(),
                "Kuki C0 ring duration");
        assertEquals(0, draws.getCount(),
                "Kuki does not draw particles before a ring hit passes gate");

        ActionRecord initial = onlyNamed(records, "Sanctifying Ring");
        assertClose(11.0 * FRAME, initial.time,
                "Kuki Skill initial hit frame");
        assertClose(1.287104, initial.action.getDamagePercent(),
                "Kuki C0 Skill initial multiplier");
        assertSkillMetadata(initial.action, "Kuki Skill initial");
        assertClose(200.0,
                initial.action.getStatSnapshot().get(
                        StatType.ELEMENTAL_MASTERY),
                "Kuki Skill initial uses live hit-time stats");
        assertClose(25.0, initial.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Kuki A4 fixes cast-time EM for the initial hit");

        advanceTo(simulator, 756.0 * FRAME);
        List<ActionRecord> rings = named(records,
                "Grass Ring of Sanctification");
        assertEquals(8, rings.size(), "Kuki C0 ring tick count");
        for (int index = 0; index < rings.size(); index++) {
            ActionRecord ring = rings.get(index);
            assertClose((115.0 + index * 90.0) * FRAME, ring.time,
                    "Kuki C0 ring tick frame " + (index + 1));
            assertClose(0.42908, ring.action.getDamagePercent(),
                    "Kuki C0 ring multiplier");
            assertSkillMetadata(ring.action, "Kuki ring");
        }
        assertClose(300.0,
                rings.get(0).action.getStatSnapshot().get(
                        StatType.ELEMENTAL_MASTERY),
                "Kuki ring uses live hit-time stats");
        assertClose(100.0,
                rings.get(0).action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Kuki A4 fixes EM at the bell two frames before hit");
        assertTrue(!kuki.isRingActive(743.0 * FRAME),
                "Kuki C0 ring expires at exact boundary");
        assertTrue(rings.get(7).time > kuki.getRingExpirationTime(),
                "Kuki final C0 tick lands after ring expiration");
        assertEquals(5, overloads[0],
                "Kuki initial and ring share Standard Skill ICD");

        assertEquals(4, particles.size(),
                "Kuki deterministic alternating draws emit four particles");
        assertEquals(8, draws.getCount(),
                "Kuki draws once per ring hit after particle gate");
        for (int index = 0; index < particles.size(); index++) {
            assertClose(1.0, particles.get(index).count,
                    "Kuki ring particle count");
            assertClose((215.0 + index * 180.0) * FRAME,
                    particles.get(index).time,
                    "Kuki selected ring particle travel time");
        }
    }

    private static void testC2AndC3SkillUpgrades() {
        KukiShinobu c2 = kuki(2, () -> 0.99);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureKukiActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.SKILL);
        assertClose(23.0 * FRAME + 15.0,
                c2.getRingExpirationTime(),
                "Kuki C2 extends ring to 15 seconds");
        assertTrue(c2.isRingActive(923.0 * FRAME - EPSILON),
                "Kuki C2 ring remains before expiration");
        assertTrue(!c2.isRingActive(923.0 * FRAME),
                "Kuki C2 ring expires half-open");
        advanceTo(c2Simulator, 926.0 * FRAME);
        List<ActionRecord> c2Rings = named(c2Records,
                "Grass Ring of Sanctification");
        assertEquals(10, c2Rings.size(), "Kuki C2 ring tick count");
        assertClose(925.0 * FRAME, c2Rings.get(9).time,
                "Kuki C2 final ring tick follows expiration");

        KukiShinobu c3 = kuki(3, () -> 0.99);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureKukiActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        advanceTo(c3Simulator, 116.0 * FRAME);
        assertClose(1.51424,
                onlyNamed(c3Records, "Sanctifying Ring")
                        .action.getDamagePercent(),
                "Kuki C3 initial Skill multiplier");
        assertClose(0.5048,
                onlyNamed(c3Records, "Grass Ring of Sanctification")
                        .action.getDamagePercent(),
                "Kuki C3 ring multiplier");
    }

    private static void testBurstSnapshotEnergyCooldownAndC5() {
        KukiShinobu c0 = kuki(0, () -> 0.99);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureKukiActions(simulator);
        double[] energy = { -1.0, -1.0 };
        observeEnergy(simulator, c0, 3.0 * FRAME, energy, 0);
        observeEnergy(simulator, c0, 4.0 * FRAME + EPSILON, energy, 1);
        addStatBuffAt(simulator, c0, 10.0 * FRAME,
                "Kuki post-Burst HP", StatType.HP_PERCENT, 1.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(63.0 * FRAME, simulator.getCurrentTime(),
                "Kuki Burst recovery");
        assertClose(0.0, c0.getLastBurstTime(),
                "Kuki Burst cooldown starts at cast");
        assertClose(15.0, c0.getBurstCooldownEndTime(),
                "Kuki Burst cooldown end");
        assertClose(60.0, energy[0],
                "Kuki Burst Energy remains before frame four");
        assertClose(0.0, energy[1],
                "Kuki Burst spends Energy at frame four");
        advanceTo(simulator, 153.0 * FRAME);
        List<ActionRecord> burst = named(records,
                "Gyoei Narukami Kariyama Rite 1");
        List<ActionRecord> allBurst = namedPrefix(records,
                "Gyoei Narukami Kariyama Rite ");
        assertEquals(1, burst.size(), "Kuki first Burst hit count");
        assertEquals(7, allBurst.size(),
                "Kuki fixed high-HP Burst resolves seven hits");
        int[] hitFrames = { 50, 67, 84, 101, 118, 135, 152 };
        for (int index = 0; index < allBurst.size(); index++) {
            ActionRecord hit = allBurst.get(index);
            assertClose(hitFrames[index] * FRAME, hit.time,
                    "Kuki Burst hit frame " + (index + 1));
            assertClose(0.061282, hit.action.getDamagePercent(),
                    "Kuki C0 Burst multiplier");
            assertEquals(StatType.BASE_HP, hit.action.getScalingStat(),
                    "Kuki Burst scales with Max HP");
            assertEquals(ActionType.BURST, hit.action.getActionType(),
                    "Kuki Burst category");
            assertEquals(ICDType.Standard, hit.action.getICDType(),
                    "Kuki Burst Standard ICD");
            assertEquals(ICDTag.ElementalBurst, hit.action.getICDTag(),
                    "Kuki Burst ICD tag");
            assertClose(1.0, hit.action.getGaugeUnits(),
                    "Kuki Burst gauge");
            assertClose(0.24,
                    hit.action.getStatSnapshot().get(StatType.HP_PERCENT),
                    "Kuki Burst preserves cast-time HP snapshot");
        }
        c0.receiveEnergy(60.0);
        advanceTo(simulator, 15.0 - EPSILON);
        assertTrue(!c0.canBurst(simulator.getCurrentTime()),
                "Kuki Burst cooldown remains closed before boundary");
        advanceTo(simulator, 15.0);
        assertTrue(c0.canBurst(simulator.getCurrentTime()),
                "Kuki Burst cooldown opens at exact boundary");

        KukiShinobu c5 = kuki(5, () -> 0.99);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureKukiActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(0.072096,
                onlyNamed(c5Records,
                        "Gyoei Narukami Kariyama Rite 1")
                        .action.getDamagePercent(),
                "Kuki C5 Burst multiplier");
    }

    private static void testC4TriggersScalingCooldownAndParticles() {
        for (ActionType type : new ActionType[] {
                ActionType.NORMAL, ActionType.CHARGE, ActionType.PLUNGE
        }) {
            KukiShinobu kuki = kuki(4, () -> 0.99);
            TestCharacter ally = new TestCharacter(
                    CharacterId.QIQI, Element.CRYO);
            CombatSimulator simulator = simulatorWith(kuki, ally);
            List<ActionRecord> records = captureKukiActions(simulator);
            perform(simulator, CharacterActionKey.SKILL);
            simulator.setActiveCharacter(CharacterId.QIQI);
            resolveFixtureAttack(simulator, ally, type, "Eligible " + type);
            advanceTo(simulator, 58.0 * FRAME);
            assertEquals(1, named(records, "Thundergrass Mark").size(),
                    "Kuki C4 accepts active-party " + type);
        }

        KukiShinobu filters = kuki(4, () -> 0.99);
        TestCharacter filterAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator filterSimulator = simulatorWith(filters, filterAlly);
        List<ActionRecord> filterRecords = captureKukiActions(filterSimulator);
        perform(filterSimulator, CharacterActionKey.SKILL);
        filterSimulator.setActiveCharacter(CharacterId.QIQI);
        for (ActionType type : new ActionType[] {
                ActionType.SKILL, ActionType.BURST, ActionType.OTHER
        }) {
            resolveFixtureAttack(filterSimulator, filterAlly, type,
                    "Rejected " + type);
        }
        resolveFixtureAttack(filterSimulator, filters, ActionType.NORMAL,
                "Off-field Kuki Normal");
        advanceTo(filterSimulator, 58.0 * FRAME);
        assertEquals(0, named(filterRecords, "Thundergrass Mark").size(),
                "Kuki C4 excludes non-NCP and non-active actors");

        KukiShinobu scaling = kuki(4, () -> 0.0);
        TestCharacter scalingAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator scalingSimulator = simulatorWith(
                scaling, scalingAlly);
        List<ActionRecord> scalingRecords = captureKukiActions(
                scalingSimulator);
        List<ParticleRecord> scalingParticles = captureElectroParticles(
                scalingSimulator);
        perform(scalingSimulator, CharacterActionKey.SKILL);
        scalingSimulator.setActiveCharacter(CharacterId.QIQI);
        addStatBuffAt(scalingSimulator, scaling, 55.0 * FRAME,
                "Kuki pre-C4 HP", StatType.HP_PERCENT, 0.5);
        addStatBuffAt(scalingSimulator, scaling, 55.0 * FRAME,
                "Kuki pre-C4 Skill bonus",
                StatType.SKILL_DMG_BONUS, 0.5);
        resolveFixtureAttack(scalingSimulator, scalingAlly,
                ActionType.NORMAL, "First C4 trigger");
        advanceTo(scalingSimulator, 158.0 * FRAME);
        ActionRecord mark = onlyNamed(scalingRecords, "Thundergrass Mark");
        assertClose(57.0 * FRAME, mark.time,
                "Kuki C4 resolves five frames after trigger");
        assertClose(0.0, mark.action.getDamagePercent(),
                "Kuki C4 represents trigger-time HP as fixed base damage");
        assertClose(12289.0 * 1.24 * 0.097,
                mark.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Kuki C4 fixes Max HP base at trigger time");
        assertClose(0.74,
                mark.action.getStatSnapshot().get(StatType.HP_PERCENT),
                "Kuki C4 captures later hit-time HP stats separately");
        assertClose(0.5,
                mark.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Kuki C4 applies live hit-time Skill bonus");
        assertEquals(ICDType.None, mark.action.getICDType(),
                "Kuki C4 has no ICD");
        assertEquals(ICDTag.None, mark.action.getICDTag(),
                "Kuki C4 has no ICD tag");
        assertClose(1.0, mark.action.getGaugeUnits(), "Kuki C4 gauge");
        assertEquals(1, scalingParticles.size(),
                "Kuki selected C4 emits one particle event");
        assertClose(157.0 * FRAME, scalingParticles.get(0).time,
                "Kuki C4 particle travel time");

        KukiShinobu gate = kuki(4, () -> 0.99);
        TestCharacter gateAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator gateSimulator = simulatorWith(gate, gateAlly);
        List<ActionRecord> gateRecords = captureKukiActions(gateSimulator);
        perform(gateSimulator, CharacterActionKey.SKILL);
        gateSimulator.setActiveCharacter(CharacterId.QIQI);
        double firstTrigger = gateSimulator.getCurrentTime();
        resolveFixtureAttack(gateSimulator, gateAlly,
                ActionType.NORMAL, "C4 gate first");
        advanceTo(gateSimulator, firstTrigger + 5.0 - EPSILON);
        resolveFixtureAttack(gateSimulator, gateAlly,
                ActionType.NORMAL, "C4 gate early");
        advanceTo(gateSimulator, firstTrigger + 5.0);
        resolveFixtureAttack(gateSimulator, gateAlly,
                ActionType.NORMAL, "C4 gate exact");
        advanceTo(gateSimulator, firstTrigger + 5.0 + 6.0 * FRAME);
        assertEquals(2, named(gateRecords, "Thundergrass Mark").size(),
                "Kuki C4 accepts exact five-second boundary only");

        assertParticleGate(false, 121.0 * FRAME, 1, 2,
                "Kuki particle gate rejects before 0.2 seconds");
        assertParticleGate(true, 122.0 * FRAME, 2, 3,
                "Kuki particle gate accepts exact 0.2 seconds");
    }

    private static void assertParticleGate(
            boolean exact,
            double triggerTime,
            int expectedParticles,
            int expectedDraws,
            String message) {
        SequenceDraw draws = new SequenceDraw(0.0, 0.0, 0.0);
        KukiShinobu kuki = kuki(4, draws);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(kuki, ally);
        List<ParticleRecord> particles = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        simulator.setActiveCharacter(CharacterId.QIQI);
        advanceTo(simulator, triggerTime);
        resolveFixtureAttack(simulator, ally, ActionType.NORMAL,
                exact ? "Exact particle gate" : "Early particle gate");
        advanceTo(simulator, 228.0 * FRAME);
        assertEquals(expectedParticles, particles.size(), message);
        assertEquals(expectedDraws, draws.getCount(),
                message + " draw count");
    }

    private static void testSwitchPersistence() {
        KukiShinobu kuki = kuki(4, () -> 0.99);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(kuki, ally);
        List<ActionRecord> records = captureKukiActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        double expiration = kuki.getRingExpirationTime();
        simulator.switchCharacter(CharacterId.QIQI);
        assertTrue(kuki.isRingActive(simulator.getCurrentTime()),
                "Kuki ring persists through switch-out");
        assertClose(expiration, kuki.getRingExpirationTime(),
                "Kuki switch-out preserves ring expiration");
        resolveFixtureAttack(simulator, ally, ActionType.NORMAL,
                "Off-field ring C4 trigger");
        advanceTo(simulator, 116.0 * FRAME);
        assertEquals(1, named(records, "Thundergrass Mark").size(),
                "Kuki off-field ring still enables active ally C4");
        assertEquals(1,
                named(records, "Grass Ring of Sanctification").size(),
                "Kuki ring continues ticking off field");
    }

    private static void testSnapshotRestoreWithoutDuplicateWork() {
        KukiShinobu activation = kuki(0, () -> 0.0);
        CombatSimulator activationSimulator = simulatorWith(activation);
        List<ActionRecord> activationRecords = captureKukiActions(
                activationSimulator);
        SimulatorSnapshot[] activationSnapshot = { null };
        captureSnapshotAt(activationSimulator, 22.0 * FRAME,
                activationSnapshot);
        perform(activationSimulator, CharacterActionKey.SKILL);
        activationSimulator.restoreSnapshot(activationSnapshot[0]);
        activationSimulator.restoreSnapshot(activationSnapshot[0]);
        activationRecords.clear();
        advanceTo(activationSimulator, 116.0 * FRAME);
        assertEquals(1,
                named(activationRecords,
                        "Grass Ring of Sanctification").size(),
                "Kuki repeated activation restore queues one ring tick");

        KukiShinobu ring = kuki(0, () -> 0.0);
        CombatSimulator ringSimulator = simulatorWith(ring);
        List<ActionRecord> ringRecords = captureKukiActions(ringSimulator);
        List<ParticleRecord> ringParticles = captureElectroParticles(
                ringSimulator);
        perform(ringSimulator, CharacterActionKey.SKILL);
        SimulatorSnapshot ringSnapshot = ringSimulator.saveSnapshot();
        advanceTo(ringSimulator, 216.0 * FRAME);
        ringSimulator.restoreSnapshot(ringSnapshot);
        ringSimulator.restoreSnapshot(ringSnapshot);
        ringRecords.clear();
        ringParticles.clear();
        advanceTo(ringSimulator, 216.0 * FRAME);
        assertEquals(2,
                named(ringRecords,
                        "Grass Ring of Sanctification").size(),
                "Kuki repeated ring restore queues each due tick once");
        assertEquals(1, ringParticles.size(),
                "Kuki repeated ring restore queues pending particle once");

        KukiShinobu burst = kuki(0, () -> 0.99);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> burstRecords = captureKukiActions(burstSimulator);
        SimulatorSnapshot[] burstSnapshot = { null };
        captureSnapshotAt(burstSimulator, 40.0 * FRAME, burstSnapshot);
        perform(burstSimulator, CharacterActionKey.BURST);
        burstSimulator.restoreSnapshot(burstSnapshot[0]);
        burstSimulator.restoreSnapshot(burstSnapshot[0]);
        burstRecords.clear();
        advanceTo(burstSimulator, 153.0 * FRAME);
        assertEquals(7,
                namedPrefix(burstRecords,
                        "Gyoei Narukami Kariyama Rite ").size(),
                "Kuki repeated Burst restore queues seven hits once");

        SequenceDraw c4Draws = new SequenceDraw(0.0, 0.9);
        KukiShinobu c4 = kuki(4, c4Draws);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator c4Simulator = simulatorWith(c4, ally);
        List<ActionRecord> c4Records = captureKukiActions(c4Simulator);
        List<ParticleRecord> c4Particles = captureElectroParticles(
                c4Simulator);
        perform(c4Simulator, CharacterActionKey.SKILL);
        c4Simulator.setActiveCharacter(CharacterId.QIQI);
        resolveFixtureAttack(c4Simulator, ally, ActionType.NORMAL,
                "Pending C4 snapshot trigger");
        SimulatorSnapshot c4Snapshot = c4Simulator.saveSnapshot();
        advanceTo(c4Simulator, 158.0 * FRAME);
        assertEquals(2, c4Draws.getCount(),
                "Kuki original branch consumes C4 and ring draws");
        c4Simulator.restoreSnapshot(c4Snapshot);
        c4Simulator.restoreSnapshot(c4Snapshot);
        c4Records.clear();
        c4Particles.clear();
        advanceTo(c4Simulator, 158.0 * FRAME);
        assertEquals(1, named(c4Records, "Thundergrass Mark").size(),
                "Kuki repeated C4 restore queues one mark");
        assertEquals(1, c4Particles.size(),
                "Kuki repeated C4 restore queues one particle");
        assertEquals(2, c4Draws.getCount(),
                "Kuki C4 rollback replays future draws from tape");
    }

    private static void testInvalidInputsAndIsolation() {
        KukiShinobu invalid = kuki(0, () -> 0.99);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Kuki rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Kuki rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Kuki rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.PLUNGE),
                "Kuki rejects direct Plunge action");
        assertThrows(IllegalArgumentException.class,
                () -> new KukiShinobu(
                        null, null, TalentDataManager.getInstance(), 0, null),
                "Kuki rejects null particle supplier");

        KukiShinobu badDraw = kuki(0, () -> 1.0);
        CombatSimulator badDrawSimulator = simulatorWith(badDraw);
        assertThrows(IllegalStateException.class,
                () -> {
                    perform(badDrawSimulator, CharacterActionKey.SKILL);
                    advanceTo(badDrawSimulator, 116.0 * FRAME);
                },
                "Kuki rejects particle draw at one");
        KukiShinobu nanDraw = kuki(0, () -> Double.NaN);
        CombatSimulator nanDrawSimulator = simulatorWith(nanDraw);
        assertThrows(IllegalStateException.class,
                () -> {
                    perform(nanDrawSimulator, CharacterActionKey.SKILL);
                    advanceTo(nanDrawSimulator, 116.0 * FRAME);
                },
                "Kuki rejects non-finite particle draw");

        KukiShinobu absent = kuki(0, () -> 0.99);
        assertThrows(IllegalArgumentException.class,
                () -> absent.initializeForSimulator(new CombatSimulator()),
                "Kuki rejects simulator that does not own her");
        KukiShinobu reused = kuki(0, () -> 0.99);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Kuki rejects cross-simulator reuse");

        KukiShinobu owner = kuki(0, () -> 0.99);
        KukiShinobu foreign = kuki(0, () -> 0.99);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Kuki rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, new CombatSimulator()),
                "Kuki rejects foreign state type");

        KukiShinobu noEnemy = kuki(0, () -> 0.99);
        CombatSimulator noEnemySimulator = new CombatSimulator();
        noEnemySimulator.setLoggingEnabled(false);
        noEnemySimulator.addCharacter(noEnemy);
        assertThrows(NullPointerException.class,
                () -> perform(noEnemySimulator, CharacterActionKey.SKILL),
                "Kuki requires enemy for attack resolution");
    }

    private static KukiShinobu kuki(
            int constellation,
            DoubleSupplier particleDrawSource) {
        return new KukiShinobu(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                particleDrawSource);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.KUKI_SHINOBU,
                CharacterActionRequest.of(key));
    }

    private static void resolveFixtureAttack(
            CombatSimulator simulator,
            Character actor,
            ActionType actionType,
            String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                false,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
    }

    private static List<ActionRecord> captureKukiActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor != null
                    && actor.getCharacterId()
                            == CharacterId.KUKI_SHINOBU) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureElectroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
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
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = named(records, name);
        assertEquals(1, selected.size(), name + " action count");
        return selected.get(0);
    }

    private static void assertSkillMetadata(
            AttackAction action,
            String message) {
        assertEquals(Element.ELECTRO, action.getElement(),
                message + " element");
        assertEquals(ActionType.SKILL, action.getActionType(),
                message + " category");
        assertEquals(ICDType.Standard, action.getICDType(),
                message + " Standard ICD");
        assertEquals(ICDTag.ElementalSkill, action.getICDTag(),
                message + " shared Skill ICD tag");
        assertClose(1.0, action.getGaugeUnits(), message + " gauge");
    }

    private static void addStatBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            String name,
            StatType stat,
            double amount) {
        addStatBuffAt(
                simulator, character, time, 100.0, name, stat, amount);
    }

    private static void addStatBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            double duration,
            String name,
            StatType stat,
            double amount) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                character.addBuff(new SimpleBuff(
                        name,
                        duration,
                        time,
                        stats -> stats.add(stat, amount)));
            }
        });
    }

    private static void observeEnergy(
            CombatSimulator simulator,
            Character character,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = character.getCurrentEnergy();
            }
        });
    }

    private static void captureSnapshotAt(
            CombatSimulator simulator,
            double time,
            SimulatorSnapshot[] target) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                target[0] = activeSimulator.saveSnapshot();
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
            assertTrue(lines.get(index).startsWith("Kuki Shinobu,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/KukiShinobu/KukiShinobu_Status.csv",
                "config/characters/KukiShinobu/KukiShinobu_Multipliers.csv"
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
        throw new AssertionError("Kuki CSVs missing key " + key);
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
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        @SuppressWarnings("unused")
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

    private static final class SequenceDraw implements DoubleSupplier {
        private final double[] values;
        private int count;

        private SequenceDraw(double... values) {
            this.values = values.clone();
        }

        @Override
        public double getAsDouble() {
            if (count >= values.length) {
                throw new AssertionError(
                        "Kuki particle supplier exhausted at draw " + count);
            }
            return values[count++];
        }

        private int getCount() {
            return count;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
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
