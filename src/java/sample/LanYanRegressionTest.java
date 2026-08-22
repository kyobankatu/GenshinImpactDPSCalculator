package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.LanYan;
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

/** Focused regression checks for Lan Yan's fixed-target Feathermoon slice. */
public final class LanYanRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private LanYanRegressionTest() {
    }

    /** Runs data, timing, passive, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testStationaryBasicAttacks();
        testSkillWindowRingsSnapshotsAndParticles();
        testA1ConversionAndC1();
        testBurstA4C4AndTalentConstellations();
        testC6ChargesAndParticleGenerationReset();
        testSnapshotRestoreEventUniqueness();
        testFailClosedAndReuseGuards();
        System.out.println("LanYanRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        LanYan lanYan = new LanYan(null, null, 6);
        assertEquals(CharacterId.LAN_YAN, lanYan.getCharacterId(),
                "Lan Yan typed identity");
        assertEquals(CharacterId.LAN_YAN,
                CharacterId.fromName("Lan Yan"),
                "Lan Yan name lookup");
        assertEquals(CharacterId.LAN_YAN,
                CharacterId.fromNumericId(81),
                "Lan Yan numeric lookup");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.LAN_YAN.getRegion(),
                "Lan Yan region");
        assertEquals(Element.ANEMO, lanYan.getElement(),
                "Lan Yan element");
        assertClose(9244.0,
                lanYan.getBaseStats().get(StatType.BASE_HP),
                "Lan Yan base HP");
        assertClose(251.0,
                lanYan.getBaseStats().get(StatType.BASE_ATK),
                "Lan Yan base ATK");
        assertClose(580.0,
                lanYan.getBaseStats().get(StatType.BASE_DEF),
                "Lan Yan base DEF");
        assertClose(0.24,
                lanYan.getBaseStats().get(StatType.ATK_PERCENT),
                "Lan Yan ascension ATK");
        assertClose(60.0, lanYan.getEnergyCost(),
                "Lan Yan Energy cost");
        assertClose(16.0, lanYan.getSkillCD(),
                "Lan Yan Skill cooldown");
        assertClose(15.0, lanYan.getBurstCD(),
                "Lan Yan Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.LAN_YAN,
                    new LanYan(null, null, constellation)
                            .getCharacterId(),
                    "Lan Yan explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/LanYan/LanYan_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/LanYan/LanYan_Multipliers.csv"), 20);
        assertCsvValue("Base ATK", 251.0);
        assertCsvValue("N2-2", 0.424116);
        assertCsvValue("Feathermoon Ring C3", 1.92512);
        assertCsvValue("Lustrous Moonrise C5", 4.82128);
        assertCsvValue("A4 Burst EM Flat Ratio", 7.74);
    }

    private static void testStationaryBasicAttacks() {
        LanYan normalOwner = new LanYan(null, null, 0);
        CombatSimulator normalSimulator = simulatorWith(normalOwner);
        List<ActionRecord> normals = captureActions(normalSimulator);
        int[][] hitmarks = {
            { 11 }, { 17, 37 }, { 15, 21 }, { 40 }
        };
        int[] durations = { 30, 46, 53, 63 };
        int[] hitlagFrames = { 6, 10, 2, 8 };
        double[][] multipliers = {
            { 0.70448 }, { 0.347004, 0.424116 },
            { 0.45764, 0.45764 }, { 1.09752 }
        };
        int recordIndex = 0;
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = normalSimulator.getCurrentTime();
            perform(normalSimulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < hitmarks[step].length; hit++) {
                ActionRecord record = normals.get(recordIndex++);
                assertClose(castTime + hitmarks[step][hit] * FRAME,
                        record.time,
                        "Lan Yan Normal hitmark");
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Lan Yan Normal multiplier");
                assertEquals(Element.ANEMO,
                        record.action.getElement(),
                        "Lan Yan Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Lan Yan Normal category");
                assertTrue(record.action.hasStatSnapshot(),
                        "Lan Yan Normal owns its cast snapshot");
            }
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    normalSimulator.getCurrentTime(),
                    "Lan Yan Normal recovery");
        }
        perform(normalSimulator, CharacterActionKey.NORMAL);
        assertEquals("Black Pheasant Strides on Water N1",
                normals.get(recordIndex).action.getName(),
                "Lan Yan Normal string wraps after N4");

        LanYan chargedOwner = new LanYan(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> charged = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertEquals(3, charged.size(),
                "Lan Yan Charged Attack has three hits");
        int[] chargedHitmarks = { 42, 49, 56 };
        for (int index = 0; index < charged.size(); index++) {
            assertClose(chargedHitmarks[index] * FRAME,
                    charged.get(index).time,
                    "Lan Yan Charged hitmark");
            assertClose(0.64328,
                    charged.get(index).action.getDamagePercent(),
                    "Lan Yan Charged multiplier");
            assertEquals(ICDTag.ChargedAttack,
                    charged.get(index).action.getICDTag(),
                    "Lan Yan Charged shared ICD");
        }
        assertClose(70.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Lan Yan Charged recovery");

        LanYan plungeOwner = new LanYan(null, null, 0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plunge = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        assertEquals(1, plunge.size(),
                "Lan Yan represented Plunge has one high impact");
        assertClose(46.0 * FRAME, plunge.get(0).time,
                "Lan Yan high Plunge hitmark");
        assertClose(2.607632,
                plunge.get(0).action.getDamagePercent(),
                "Lan Yan high Plunge multiplier");
        assertEquals(ICDType.None, plunge.get(0).action.getICDType(),
                "Lan Yan high Plunge has no ICD");
        assertClose(67.0 * FRAME, plungeSimulator.getCurrentTime(),
                "Lan Yan high Plunge recovery");
    }

    private static void testSkillWindowRingsSnapshotsAndParticles() {
        LanYan lanYan = new LanYan(null, null, 0);
        lanYan.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 100.0);
        CombatSimulator simulator = simulatorWith(lanYan);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);

        performSkill(simulator);
        assertClose(33.0 * FRAME, simulator.getCurrentTime(),
                "Lan Yan initial Skill attack cancel");
        assertClose(4.0 * FRAME, lanYan.getLastSkillTime(),
                "Lan Yan Skill cooldown starts at frame four");
        assertClose(16.0 + 4.0 * FRAME,
                lanYan.getSkillCooldownEndTime(),
                "Lan Yan Skill cooldown end");
        assertClose(73.0 * FRAME,
                lanYan.getFeathermoonExpirationTime(),
                "Lan Yan Feathermoon window expiration");
        assertTrue(lanYan.isFeathermoonWindowActive(
                simulator.getCurrentTime()),
                "Lan Yan Feathermoon window active at cancel");

        perform(simulator, CharacterActionKey.NORMAL);
        assertClose((74.0 + 4.0) * FRAME, simulator.getCurrentTime(),
                "Lan Yan Ring launch recovery");
        assertEquals(1, records.size(),
                "Lan Yan first Ring lands during launch recovery");
        assertTrue(!lanYan.isFeathermoonWindowActive(
                simulator.getCurrentTime()),
                "Lan Yan Ring launch consumes follow-up window");

        lanYan.addBuff(new SimpleBuff(
                "Lan Yan post-launch EM probe",
                20.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 900.0)));
        advanceTo(simulator, 119.0 * FRAME);
        assertEquals(3, records.size(),
                "Lan Yan Ring volley has three Anemo hits");
        int[] ringHitmarks = { 71, 95, 118 };
        for (int index = 0; index < records.size(); index++) {
            AttackAction ring = records.get(index).action;
            assertClose(ringHitmarks[index] * FRAME,
                    records.get(index).time,
                    "Lan Yan Ring impact");
            assertClose(1.636352, ring.getDamagePercent(),
                    "Lan Yan Ring multiplier");
            assertEquals(ICDTag.LanYan_FeathermoonRing,
                    ring.getICDTag(),
                    "Lan Yan Ring dedicated ICD");
            assertClose(100.0,
                    ring.getStatSnapshot().get(
                            StatType.ELEMENTAL_MASTERY),
                    "Lan Yan Ring retains launch EM snapshot");
            assertClose(309.0,
                    ring.getStatSnapshot().get(StatType.FLAT_DMG_BONUS),
                    "Lan Yan A4 Ring flat damage");
        }
        advanceTo(simulator, 170.0 * FRAME);
        assertEquals(0, particles.size(),
                "Lan Yan particles wait one hundred frames");
        advanceTo(simulator, 171.0 * FRAME);
        assertEquals(1, particles.size(),
                "Lan Yan particles generate once per initial Skill");
        assertClose(3.0, particles.get(0).count,
                "Lan Yan fixed particle count");
        assertClose(171.0 * FRAME, particles.get(0).time,
                "Lan Yan particle arrival");

        LanYan expired = new LanYan(null, null, 0);
        CombatSimulator expiredSimulator = simulatorWith(expired);
        List<ActionRecord> expiredRecords = captureActions(expiredSimulator);
        performSkill(expiredSimulator);
        advanceTo(expiredSimulator, 73.0 * FRAME);
        assertTrue(!expired.isFeathermoonWindowActive(
                expiredSimulator.getCurrentTime()),
                "Lan Yan follow-up window is half-open");
        perform(expiredSimulator, CharacterActionKey.NORMAL);
        assertEquals("Black Pheasant Strides on Water N1",
                expiredRecords.get(0).action.getName(),
                "Lan Yan expired window performs a normal attack");
    }

    private static void testA1ConversionAndC1() {
        LanYan c0 = new LanYan(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        c0Simulator.getEnemy().setAura(Element.HYDRO, 2.0);
        c0Simulator.getEnemy().setAura(Element.PYRO, 2.0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        performSkill(c0Simulator);
        assertEquals(Element.PYRO, c0.getAbsorbedElement(),
                "Lan Yan A1 follows typed Pyro-first priority");
        performSkill(c0Simulator);
        advanceTo(c0Simulator, 124.0 * FRAME);
        assertEquals(3, named(c0Records, "Feathermoon Ring").size(),
                "Lan Yan C0 base Ring volley");
        List<ActionRecord> converted = named(
                c0Records, "Feathermoon Ring (PYRO)");
        assertEquals(3, converted.size(),
                "Lan Yan A1 creates three converted hits");
        for (ActionRecord record : converted) {
            assertEquals(Element.PYRO, record.action.getElement(),
                    "Lan Yan A1 converted element");
            assertClose(0.818176,
                    record.action.getDamagePercent(),
                    "Lan Yan A1 converted multiplier");
            assertEquals(ICDTag.LanYan_FeathermoonRingMix,
                    record.action.getICDTag(),
                    "Lan Yan A1 converted dedicated ICD");
        }

        LanYan c1 = new LanYan(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        c1Simulator.getEnemy().setAura(Element.ELECTRO, 4.0);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        performSkill(c1Simulator);
        perform(c1Simulator, CharacterActionKey.NORMAL);
        advanceTo(c1Simulator, 124.0 * FRAME);
        assertEquals(3, named(c1Records,
                "Feathermoon Ring (C1)").size(),
                "Lan Yan C1 adds one Anemo volley");
        assertEquals(3, named(c1Records,
                "Feathermoon Ring (C1) (ELECTRO)").size(),
                "Lan Yan C1 adds one converted volley");
        assertClose(97.0 * FRAME,
                named(c1Records, "Feathermoon Ring (C1)")
                        .get(1).time,
                "Lan Yan C1 second Ring hitmark");
        assertClose(123.0 * FRAME,
                named(c1Records, "Feathermoon Ring (C1)")
                        .get(2).time,
                "Lan Yan C1 third Ring hitmark");

        LanYan failClosed = new LanYan(null, null, 1);
        CombatSimulator failClosedSimulator = simulatorWith(failClosed);
        failClosedSimulator.getEnemy().setAura(Element.DENDRO, 4.0);
        List<ActionRecord> failClosedRecords = captureActions(
                failClosedSimulator);
        performSkill(failClosedSimulator);
        assertEquals(Element.ANEMO, failClosed.getAbsorbedElement(),
                "Lan Yan A1 rejects unsupported typed Aura");
        perform(failClosedSimulator, CharacterActionKey.NORMAL);
        advanceTo(failClosedSimulator, 119.0 * FRAME);
        assertEquals(3, failClosedRecords.size(),
                "Lan Yan fail-closed A1 has no conversion or C1 volley");
    }

    private static void testBurstA4C4AndTalentConstellations() {
        LanYan c2 = new LanYan(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        c2Simulator.getEnemy().setAura(Element.PYRO, 4.0);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        performSkill(c2Simulator);
        perform(c2Simulator, CharacterActionKey.NORMAL);
        advanceTo(c2Simulator, 124.0 * FRAME);
        assertClose(1.636352,
                named(c2Records, "Feathermoon Ring").get(0)
                        .action.getDamagePercent(),
                "Lan Yan C2 leaves Ring talent level unchanged");

        LanYan c3 = new LanYan(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator);
        perform(c3Simulator, CharacterActionKey.NORMAL);
        assertClose(1.92512,
                named(c3Records, "Feathermoon Ring").get(0)
                        .action.getDamagePercent(),
                "Lan Yan C3 raises Skill talent level");

        LanYan c5 = new LanYan(null, null, 5);
        c5.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 100.0);
        TestCharacter ally = new TestCharacter(
                CharacterId.COLLEI, Element.DENDRO);
        CombatSimulator burstSimulator = simulatorWith(c5, ally);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertClose(75.0 * FRAME, burstSimulator.getCurrentTime(),
                "Lan Yan Burst recovery");
        assertEquals(3, burstRecords.size(),
                "Lan Yan Burst has three hits");
        int[] burstHitmarks = { 30, 46, 51 };
        for (int index = 0; index < burstRecords.size(); index++) {
            AttackAction burst = burstRecords.get(index).action;
            assertClose(burstHitmarks[index] * FRAME,
                    burstRecords.get(index).time,
                    "Lan Yan Burst hitmark");
            assertClose(4.82128, burst.getDamagePercent(),
                    "Lan Yan C5 Burst multiplier");
            assertClose(100.0,
                    burst.getStatSnapshot().get(
                            StatType.ELEMENTAL_MASTERY),
                    "Lan Yan Burst snapshots before C4");
            assertClose(774.0,
                    burst.getStatSnapshot().get(StatType.FLAT_DMG_BONUS),
                    "Lan Yan A4 Burst flat damage");
        }
        assertClose(0.0, c5.getCurrentEnergy(),
                "Lan Yan Burst spends Energy at frame four");
        assertClose(15.0, c5.getBurstCooldownEndTime(),
                "Lan Yan Burst cooldown starts at cast");
        assertClose(12.0, c5.getC4ExpirationTime(),
                "Lan Yan C4 exact expiration");
        assertClose(160.0,
                effectiveStat(
                        burstSimulator,
                        c5,
                        StatType.ELEMENTAL_MASTERY),
                "Lan Yan C4 buffs its owner after Burst snapshot");
        assertClose(60.0,
                effectiveStat(
                        burstSimulator,
                        ally,
                        StatType.ELEMENTAL_MASTERY),
                "Lan Yan C4 buffs party members");
        advanceTo(burstSimulator, 12.0);
        assertClose(100.0,
                effectiveStat(
                        burstSimulator,
                        c5,
                        StatType.ELEMENTAL_MASTERY),
                "Lan Yan C4 expires at the half-open boundary");
    }

    private static void testC6ChargesAndParticleGenerationReset() {
        LanYan c6 = new LanYan(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose((74.0 + 4.0) * FRAME, simulator.getCurrentTime(),
                "Lan Yan first Skill sequence ends at frame 74");

        performSkill(simulator);
        assertClose((107.0 + 8.0) * FRAME, simulator.getCurrentTime(),
                "Lan Yan C6 consumes the second Skill charge immediately");
        assertClose((78.0 + 4.0) * FRAME, c6.getLastSkillTime(),
                "Lan Yan second Skill cooldown starts four frames later");
        performSkill(simulator);
        advanceTo(simulator, 196.0 * FRAME);
        assertEquals(2, particles.size(),
                "Lan Yan new initial Skill resets particle generation");
        assertClose(171.0 * FRAME, particles.get(0).time,
                "Lan Yan first generation particle arrival");
        assertClose(195.0 * FRAME, particles.get(1).time,
                "Lan Yan reset generation particle arrival");
    }

    private static void testSnapshotRestoreEventUniqueness() {
        LanYan lanYan = new LanYan(null, null, 0);
        lanYan.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 80.0);
        CombatSimulator simulator = simulatorWith(lanYan);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        records.clear();
        particles.clear();
        advanceTo(simulator, 172.0 * FRAME);
        assertEquals(2, records.size(),
                "Lan Yan branch resolves two pending Rings");
        assertEquals(1, particles.size(),
                "Lan Yan branch resolves one pending particle event");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        advanceTo(simulator, 172.0 * FRAME);
        assertEquals(2, records.size(),
                "Lan Yan repeated restore rebuilds pending Rings once");
        assertEquals(1, particles.size(),
                "Lan Yan repeated restore rebuilds particles once");
        for (ActionRecord record : records) {
            assertClose(80.0,
                    record.action.getStatSnapshot().get(
                            StatType.ELEMENTAL_MASTERY),
                    "Lan Yan restored Ring retains owner snapshot");
        }

        LanYan windowOwner = new LanYan(null, null, 0);
        CombatSimulator windowSimulator = simulatorWith(windowOwner);
        List<ActionRecord> windowRecords = captureActions(windowSimulator);
        performSkill(windowSimulator);
        SimulatorSnapshot windowSnapshot = windowSimulator.saveSnapshot();
        advanceTo(windowSimulator, 73.0 * FRAME);
        assertTrue(!windowOwner.isFeathermoonWindowActive(
                windowSimulator.getCurrentTime()),
                "Lan Yan live branch expires Ring window");
        windowSimulator.restoreSnapshot(windowSnapshot);
        windowSimulator.restoreSnapshot(windowSnapshot);
        perform(windowSimulator, CharacterActionKey.NORMAL);
        advanceTo(windowSimulator, 119.0 * FRAME);
        assertEquals(3, windowRecords.size(),
                "Lan Yan repeated restore preserves one Ring volley");
    }

    private static void testFailClosedAndReuseGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new LanYan(null, null, -1),
                "Lan Yan rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new LanYan(null, null, 7),
                "Lan Yan rejects constellation above six");

        LanYan noTarget = new LanYan(null, null, 0);
        CombatSimulator noTargetSimulator = new CombatSimulator();
        noTargetSimulator.setLoggingEnabled(false);
        noTargetSimulator.addCharacter(noTarget);
        List<ActionRecord> noTargetRecords = captureActions(
                noTargetSimulator);
        performSkill(noTargetSimulator);
        assertTrue(!noTarget.isFeathermoonWindowActive(
                noTargetSimulator.getCurrentTime()),
                "Lan Yan target detection fails closed without an enemy");
        noTargetSimulator.advanceTime(2.0);
        assertEquals(0, noTargetRecords.size(),
                "Lan Yan no-target Skill cannot synthesize Rings");

        LanYan invalid = new LanYan(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Lan Yan rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Lan Yan rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> invalidSimulator.performAction(
                        CharacterId.LAN_YAN,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Lan Yan rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Lan Yan rejects excluded movement actions");

        LanYan insufficient = new LanYan(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator);
        insufficient.spendEnergy(insufficient.getEnergyCost());
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Lan Yan insufficient Energy rejects Burst");
        assertClose(60.0, insufficient.getMissedBurstCost(),
                "Lan Yan records rejected Burst Energy");

        LanYan reused = new LanYan(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Lan Yan rejects cross-simulator reuse");
        LanYan owner = new LanYan(null, null, 0);
        LanYan foreign = new LanYan(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Lan Yan rejects another instance's state");
        assertTrue(!owner.acceptsCharacterState(null),
                "Lan Yan rejects null snapshot payload");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
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
                CharacterId.LAN_YAN, CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.LAN_YAN,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.LAN_YAN) {
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
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
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
            assertTrue(lines.get(index).startsWith("Lan Yan,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/LanYan/LanYan_Status.csv",
                "config/characters/LanYan/LanYan_Multipliers.csv"
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
        throw new AssertionError("Lan Yan CSVs missing key " + key);
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
