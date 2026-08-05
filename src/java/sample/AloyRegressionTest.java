package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.reaction.ReactionResult;
import model.character.Aloy;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Aloy's fixed-target Coil kit. */
public final class AloyRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private AloyRegressionTest() {
    }

    /** Runs Aloy's data, timing, buff, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndC0Construction();
        testNormalAndChargedTimingsAndMultipliers();
        testSkillTimelineSnapshotIcdParticlesCoilsAndA1();
        testSecondSkillRushingIceInfusionAndA4();
        testSwitchClearAndRefresh();
        testBurstSnapshotTimingEnergyAndCooldown();
        testSnapshotRestoreWithoutDuplicateDelayedWork();
        testInvalidInputsAndIsolation();
        System.out.println("AloyRegressionTest passed");
    }

    private static void testIdentityDataAndC0Construction()
            throws IOException {
        Aloy aloy = new Aloy(null, null);
        assertEquals(CharacterId.ALOY, aloy.getCharacterId(),
                "Aloy typed identity");
        assertEquals(CharacterId.ALOY, CharacterId.fromName("Aloy"),
                "Aloy name lookup");
        assertEquals(CharacterId.ALOY, CharacterId.fromNumericId(44),
                "Aloy numeric lookup");
        assertEquals(CharacterRegion.UNKNOWN,
                CharacterId.ALOY.getRegion(), "Aloy crossover region");
        assertEquals(Element.CRYO, aloy.getElement(), "Aloy element");
        assertClose(10899.0,
                aloy.getBaseStats().get(StatType.BASE_HP),
                "Aloy base HP");
        assertClose(234.0,
                aloy.getBaseStats().get(StatType.BASE_ATK),
                "Aloy base ATK");
        assertClose(676.0,
                aloy.getBaseStats().get(StatType.BASE_DEF),
                "Aloy base DEF");
        assertClose(0.288,
                aloy.getBaseStats().get(StatType.CRYO_DMG_BONUS),
                "Aloy ascension Cryo bonus");
        assertClose(40.0, aloy.getEnergyCost(), "Aloy Energy cost");
        assertClose(20.0, aloy.getSkillCD(), "Aloy Skill cooldown");
        assertClose(12.0, aloy.getBurstCD(), "Aloy Burst cooldown");
        assertThrows(IllegalArgumentException.class,
                () -> new Aloy(
                        null, null,
                        TalentDataManager.getInstance(), -1),
                "Aloy rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Aloy(
                        null, null,
                        TalentDataManager.getInstance(), 1),
                "Aloy rejects constellations above C0");

        assertCsvShape(Path.of(
                "config/characters/Aloy/Aloy_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Aloy/Aloy_Multipliers.csv"), 64);
        assertCsvValue("Base ATK", 234.0);
        assertCsvValue("N1-1", 0.3552);
        assertCsvValue("Fully Charged Aimed Shot", 2.108);
        assertCsvValue("Freeze Bomb", 3.0192);
        assertCsvValue("Chillwater Bomblet", 0.68);
        assertCsvValue("Prophecies of Dawn", 6.1064);
        assertCsvValue("Rushing Ice Normal Attack DMG Bonus", 0.45325);
    }

    private static void testNormalAndChargedTimingsAndMultipliers() {
        Aloy aloy = new Aloy(null, null);
        CombatSimulator simulator = simulatorWith(aloy);
        List<ActionRecord> records = captureActions(simulator);
        int[][] releaseFrames = { { 11, 24 }, { 16 }, { 23 }, { 30 } };
        int[] recoveryFrames = { 31, 28, 38, 61 };
        String[][] names = {
            { "Rapid Fire N1-1", "Rapid Fire N1-2" },
            { "Rapid Fire N2" },
            { "Rapid Fire N3" },
            { "Rapid Fire N4" }
        };
        double[][] multipliers = {
            { 0.3552, 0.3996 }, { 0.7252 }, { 0.888 }, { 1.10408 }
        };
        double[] castTimes = new double[4];
        for (int step = 0; step < recoveryFrames.length; step++) {
            castTimes[step] = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            assertClose(castTimes[step] + recoveryFrames[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Aloy Normal recovery " + (step + 1));
        }
        for (int step = 0; step < names.length; step++) {
            for (int hit = 0; hit < names[step].length; hit++) {
                ActionRecord record = onlyNamed(records, names[step][hit]);
                assertClose(castTimes[step]
                                + (releaseFrames[step][hit] + 10.0) * FRAME,
                        record.time, "Aloy Normal projectile impact");
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Aloy Normal multiplier");
                assertEquals(Element.PHYSICAL, record.action.getElement(),
                        "Aloy uninfused Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Aloy Normal category");
                assertEquals(ICDType.None, record.action.getICDType(),
                        "Aloy physical Normal has no ICD");
            }
        }
        perform(simulator, CharacterActionKey.NORMAL);
        advanceTo(simulator, castTimes[3] + 192.0 * FRAME);
        assertEquals(2, named(records, "Rapid Fire N1-1").size(),
                "Aloy Normal string wraps after N4");

        Aloy charged = new Aloy(null, null);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertClose(96.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Aloy fully Charged recovery");
        ActionRecord aimed = onlyNamed(chargedRecords,
                "Rapid Fire Fully Charged Aimed Shot");
        assertClose(96.0 * FRAME, aimed.time,
                "Aloy fully Charged projectile impact");
        assertClose(2.108, aimed.action.getDamagePercent(),
                "Aloy fully Charged multiplier");
        assertEquals(Element.CRYO, aimed.action.getElement(),
                "Aloy fully Charged element");
        assertEquals(ActionType.CHARGE, aimed.action.getActionType(),
                "Aloy fully Charged category");
        assertEquals(ICDType.None, aimed.action.getICDType(),
                "Aloy fully Charged has no ICD");
        assertClose(1.0, aimed.action.getGaugeUnits(),
                "Aloy fully Charged gauge");
    }

    private static void testSkillTimelineSnapshotIcdParticlesCoilsAndA1() {
        Aloy aloy = new Aloy(null, null);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(aloy, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);
        addStatBuffAt(simulator, aloy, FRAME,
                "Aloy post-cast ATK", StatType.ATK_PERCENT, 1.0);
        performSkill(simulator);

        assertClose(70.0 * FRAME, simulator.getCurrentTime(),
                "Aloy Skill recovery");
        ActionRecord freeze = onlyNamed(records,
                "Frozen Wilds Freeze Bomb");
        List<ActionRecord> bomblets = named(records,
                "Frozen Wilds Chillwater Bomblet");
        assertClose(24.0 * FRAME, freeze.time,
                "Aloy Freeze Bomb impact");
        assertClose(3.0192, freeze.action.getDamagePercent(),
                "Aloy Freeze Bomb multiplier");
        assertEquals(ICDType.None, freeze.action.getICDType(),
                "Aloy Freeze Bomb has no ICD");
        assertEquals(ICDTag.None, freeze.action.getICDTag(),
                "Aloy Freeze Bomb ICD tag");
        assertClose(1.0, freeze.action.getGaugeUnits(),
                "Aloy Freeze Bomb gauge");
        assertEquals(2, bomblets.size(),
                "Aloy fixed target resolves two Bomblets");
        for (int index = 0; index < bomblets.size(); index++) {
            ActionRecord bomblet = bomblets.get(index);
            assertClose((30.0 + index * 6.0) * FRAME, bomblet.time,
                    "Aloy Bomblet impact");
            assertClose(0.68, bomblet.action.getDamagePercent(),
                    "Aloy Bomblet multiplier");
            assertEquals(ICDType.Standard,
                    bomblet.action.getICDType(),
                    "Aloy Bomblet Standard ICD");
            assertEquals(ICDTag.ElementalSkill,
                    bomblet.action.getICDTag(),
                    "Aloy Bomblet shared Skill ICD tag");
            assertClose(1.0, bomblet.action.getGaugeUnits(),
                    "Aloy Bomblet gauge");
        }
        for (ActionRecord record : records) {
            assertClose(0.0, record.action.getStatSnapshot().get(
                    StatType.ATK_PERCENT),
                    "Aloy Skill components preserve cast snapshot");
        }
        assertEquals(2, aloy.getCoilCount(),
                "Aloy two Bomblets grant two Coils");
        assertClose(1.16, effectiveStat(
                simulator, aloy, StatType.ATK_PERCENT),
                "Aloy A1 grants owner ATK and refreshes");
        assertClose(0.08, effectiveStat(
                simulator, ally, StatType.ATK_PERCENT),
                "Aloy A1 grants team ATK excluding owner");

        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord coilNormal = named(records,
                "Rapid Fire N1-1").get(0);
        assertClose(normalCast + 21.0 * FRAME, coilNormal.time,
                "Aloy Coil Normal impact");
        assertClose(0.1813,
                coilNormal.action.getExtraBonuses().getOrDefault(
                        StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                "Aloy two Coils grant the stack-two Normal bonus");

        advanceTo(simulator, 125.0 * FRAME);
        assertEquals(1, particles.size(),
                "Aloy Skill emits one particle event");
        assertClose(5.0, particles.get(0).count,
                "Aloy Skill emits five particles");
        assertClose(124.0 * FRAME, particles.get(0).time,
                "Aloy Skill particle travel time");

        Aloy icd = new Aloy(null, null);
        CombatSimulator icdSimulator = simulatorWith(icd);
        int[] melts = { 0 };
        icdSimulator.addReactionListener((result, source, time, active) -> {
            if (result.getKind() == ReactionResult.Kind.MELT) {
                melts[0]++;
            }
        });
        icdSimulator.getEnemy().setAura(Element.PYRO, 20.0);
        performSkill(icdSimulator);
        assertEquals(2, melts[0],
                "Aloy Freeze Bomb and first Bomblet apply Cryo");
    }

    private static void testSecondSkillRushingIceInfusionAndA4() {
        Aloy aloy = new Aloy(null, null);
        CombatSimulator simulator = simulatorWith(aloy);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        double cooldownEnd = 19.0 * FRAME + 20.0;
        assertTrue(!aloy.canSkill(cooldownEnd - EPSILON),
                "Aloy Skill cooldown stays closed before boundary");
        assertTrue(aloy.canSkill(cooldownEnd),
                "Aloy Skill cooldown opens at boundary");
        advanceTo(simulator, cooldownEnd);
        double secondCast = simulator.getCurrentTime();
        performSkill(simulator);
        double rushingStart = secondCast + 36.0 * FRAME;
        double rushingEnd = rushingStart + 10.0;
        assertEquals(0, aloy.getCoilCount(),
                "Aloy fourth Coil is consumed by Rushing Ice");
        assertTrue(aloy.isRushingIceActive(simulator.getCurrentTime()),
                "Aloy second Skill activates Rushing Ice");
        assertClose(rushingEnd, aloy.getRushingIceExpirationTime(),
                "Aloy Rushing Ice expiration timestamp");
        assertEquals(1, aloy.getA4StackCount(simulator.getCurrentTime()),
                "Aloy A4 starts at one stack");
        assertClose(0.323, effectiveStat(
                simulator, aloy, StatType.CRYO_DMG_BONUS),
                "Aloy A4 starts with 3.5 percent Cryo bonus");

        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord infused = named(records, "Rapid Fire N1-1").get(0);
        assertClose(normalCast + 21.0 * FRAME, infused.time,
                "Aloy Rushing Ice Normal impact");
        assertEquals(Element.CRYO, infused.action.getElement(),
                "Aloy Rushing Ice infuses Normal attacks");
        assertEquals(ICDType.Standard, infused.action.getICDType(),
                "Aloy infused Normal uses Standard ICD");
        assertEquals(ICDTag.NormalAttack, infused.action.getICDTag(),
                "Aloy infused Normal uses Normal ICD tag");
        assertClose(1.0, infused.action.getGaugeUnits(),
                "Aloy infused Normal gauge");
        assertClose(0.45325,
                infused.action.getExtraBonuses().getOrDefault(
                        StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                "Aloy Rushing Ice Normal bonus");

        advanceTo(simulator, rushingStart + 9.0);
        assertEquals(10, aloy.getA4StackCount(simulator.getCurrentTime()),
                "Aloy A4 reaches ten stacks after nine seconds");
        assertClose(0.638, effectiveStat(
                simulator, aloy, StatType.CRYO_DMG_BONUS),
                "Aloy A4 reaches 35 percent Cryo bonus");
        advanceTo(simulator, rushingEnd);
        assertTrue(!aloy.isRushingIceActive(simulator.getCurrentTime()),
                "Aloy Rushing Ice expires at the exact boundary");
        assertEquals(0, aloy.getA4StackCount(simulator.getCurrentTime()),
                "Aloy A4 has no stack at expiration");
        assertClose(0.288, effectiveStat(
                simulator, aloy, StatType.CRYO_DMG_BONUS),
                "Aloy A4 expires half-open with Rushing Ice");

        Aloy projectile = new Aloy(null, null);
        CombatSimulator projectileSimulator = simulatorWith(projectile);
        List<ActionRecord> projectileRecords = captureActions(
                projectileSimulator);
        performSkill(projectileSimulator);
        advanceTo(projectileSimulator, 19.0 * FRAME + 20.0);
        double projectileSecondCast = projectileSimulator.getCurrentTime();
        performSkill(projectileSimulator);
        double projectileRushingEnd = projectileSecondCast
                + 36.0 * FRAME + 10.0;
        advanceTo(projectileSimulator,
                projectileRushingEnd - 12.0 * FRAME);
        perform(projectileSimulator, CharacterActionKey.NORMAL);
        advanceTo(projectileSimulator,
                projectileRushingEnd + 23.0 * FRAME);
        List<ActionRecord> boundaryArrows = named(
                projectileRecords, "Rapid Fire N1-1");
        boundaryArrows.addAll(named(
                projectileRecords, "Rapid Fire N1-2"));
        assertEquals(2, boundaryArrows.size(),
                "Aloy Rushing boundary projectile count");
        assertEquals(Element.CRYO,
                boundaryArrows.get(0).action.getElement(),
                "Aloy arrow keeps release-time infusion in flight");
        assertClose(0.638,
                boundaryArrows.get(0).action.getStatSnapshot().get(
                        StatType.CRYO_DMG_BONUS),
                "Aloy arrow keeps release-time A4 stats in flight");
        assertEquals(Element.PHYSICAL,
                boundaryArrows.get(1).action.getElement(),
                "Aloy arrow released after expiry stays Physical");
        assertClose(0.288,
                boundaryArrows.get(1).action.getStatSnapshot().get(
                        StatType.CRYO_DMG_BONUS),
                "Aloy post-expiry arrow excludes A4 stats");
    }

    private static void testSwitchClearAndRefresh() {
        Aloy clear = new Aloy(null, null);
        TestCharacter clearAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator clearSimulator = simulatorWith(clear, clearAlly);
        performSkill(clearSimulator);
        double clearStart = clearSimulator.getCurrentTime();
        clearSimulator.switchCharacter(CharacterId.QIQI);
        advanceTo(clearSimulator, clearStart + 30.0 - EPSILON);
        assertEquals(2, clear.getCoilCount(),
                "Aloy off-field Coils remain before 30 seconds");
        advanceTo(clearSimulator, clearStart + 30.0);
        assertEquals(0, clear.getCoilCount(),
                "Aloy off-field Coils clear at 30 seconds");

        Aloy returning = new Aloy(null, null);
        TestCharacter returningAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator returningSimulator = simulatorWith(
                returning, returningAlly);
        performSkill(returningSimulator);
        double returningStart = returningSimulator.getCurrentTime();
        returningSimulator.switchCharacter(CharacterId.QIQI);
        advanceTo(returningSimulator, returningStart + 1.0);
        returningSimulator.switchCharacter(CharacterId.ALOY);
        advanceTo(returningSimulator, returningStart + 30.0);
        assertEquals(0, returning.getCoilCount(),
                "Aloy return does not cancel the last-exit Coil clear");

        Aloy refresh = new Aloy(null, null);
        TestCharacter refreshAlly = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator refreshSimulator = simulatorWith(
                refresh, refreshAlly);
        performSkill(refreshSimulator);
        double firstExit = refreshSimulator.getCurrentTime();
        refreshSimulator.switchCharacter(CharacterId.QIQI);
        advanceTo(refreshSimulator, firstExit + 1.0);
        refreshSimulator.switchCharacter(CharacterId.ALOY);
        advanceTo(refreshSimulator, firstExit + 2.0);
        refreshSimulator.switchCharacter(CharacterId.QIQI);
        advanceTo(refreshSimulator, firstExit + 30.0);
        assertEquals(2, refresh.getCoilCount(),
                "Aloy later exit replaces the earlier Coil clear");
        advanceTo(refreshSimulator, firstExit + 32.0);
        assertEquals(0, refresh.getCoilCount(),
                "Aloy refreshed Coil clear fires after 30 seconds");
    }

    private static void testBurstSnapshotTimingEnergyAndCooldown() {
        Aloy aloy = new Aloy(null, null);
        CombatSimulator simulator = simulatorWith(aloy);
        List<ActionRecord> records = captureActions(simulator);
        double[] energy = { -1.0, -1.0 };
        observeEnergy(simulator, aloy, FRAME, energy, 0);
        observeEnergy(simulator, aloy, 2.0 * FRAME + EPSILON,
                energy, 1);
        addStatBuffAt(simulator, aloy, FRAME,
                "Aloy post-Burst ATK", StatType.ATK_PERCENT, 1.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(117.0 * FRAME, simulator.getCurrentTime(),
                "Aloy Burst recovery");
        ActionRecord burst = onlyNamed(records, "Prophecies of Dawn");
        assertClose(100.0 * FRAME, burst.time,
                "Aloy Burst hit frame");
        assertClose(6.1064, burst.action.getDamagePercent(),
                "Aloy Burst multiplier");
        assertEquals(Element.CRYO, burst.action.getElement(),
                "Aloy Burst element");
        assertEquals(ActionType.BURST, burst.action.getActionType(),
                "Aloy Burst category");
        assertEquals(ICDType.Standard, burst.action.getICDType(),
                "Aloy Burst Standard ICD");
        assertEquals(ICDTag.ElementalBurst, burst.action.getICDTag(),
                "Aloy Burst ICD tag");
        assertClose(2.0, burst.action.getGaugeUnits(),
                "Aloy Burst gauge");
        assertClose(0.0, burst.action.getStatSnapshot().get(
                StatType.ATK_PERCENT),
                "Aloy Burst preserves cast snapshot");
        assertClose(40.0, energy[0],
                "Aloy Burst Energy remains before frame two");
        assertClose(0.0, energy[1],
                "Aloy Burst spends 40 Energy at frame two");

        aloy.receiveEnergy(40.0);
        advanceTo(simulator, 12.0 - EPSILON);
        assertTrue(!aloy.canBurst(simulator.getCurrentTime()),
                "Aloy Burst cooldown stays closed before boundary");
        advanceTo(simulator, 12.0);
        assertTrue(aloy.canBurst(simulator.getCurrentTime()),
                "Aloy Burst cooldown opens at 12 seconds");
    }

    private static void testSnapshotRestoreWithoutDuplicateDelayedWork() {
        Aloy skill = new Aloy(null, null);
        CombatSimulator skillSimulator = simulatorWith(skill);
        List<ActionRecord> skillRecords = captureActions(skillSimulator);
        List<ParticleRecord> skillParticles = captureCryoParticles(
                skillSimulator);
        SimulatorSnapshot[] skillSnapshot = { null };
        captureSnapshotAt(skillSimulator, 10.0 * FRAME, skillSnapshot);
        performSkill(skillSimulator);
        advanceTo(skillSimulator, 125.0 * FRAME);
        assertEquals(3, skillRecords.size(),
                "Aloy Skill branch resolves three hits");
        assertEquals(1, skillParticles.size(),
                "Aloy Skill branch resolves one particle event");
        skillSimulator.restoreSnapshot(skillSnapshot[0]);
        skillSimulator.restoreSnapshot(skillSnapshot[0]);
        skillRecords.clear();
        skillParticles.clear();
        advanceTo(skillSimulator, 125.0 * FRAME);
        assertEquals(3, skillRecords.size(),
                "Aloy repeated Skill restore schedules hits once");
        assertEquals(1, skillParticles.size(),
                "Aloy repeated Skill restore schedules particles once");
        assertEquals(2, skill.getCoilCount(),
                "Aloy restored Skill grants Coils once");

        Aloy burst = new Aloy(null, null);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        SimulatorSnapshot[] burstSnapshot = { null };
        captureSnapshotAt(burstSimulator, 50.0 * FRAME, burstSnapshot);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertEquals(1, burstRecords.size(),
                "Aloy Burst branch resolves one hit");
        burstSimulator.restoreSnapshot(burstSnapshot[0]);
        burstSimulator.restoreSnapshot(burstSnapshot[0]);
        burstRecords.clear();
        advanceTo(burstSimulator, 101.0 * FRAME);
        assertEquals(1, burstRecords.size(),
                "Aloy repeated Burst restore schedules hit once");

        Aloy projectile = new Aloy(null, null);
        CombatSimulator projectileSimulator = simulatorWith(projectile);
        List<ActionRecord> projectileRecords = captureActions(
                projectileSimulator);
        perform(projectileSimulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot projectileSnapshot =
                projectileSimulator.saveSnapshot();
        projectileRecords.clear();
        advanceTo(projectileSimulator, 35.0 * FRAME);
        assertEquals(1, projectileRecords.size(),
                "Aloy branch resolves pending N1 projectile once");
        projectileSimulator.restoreSnapshot(projectileSnapshot);
        projectileSimulator.restoreSnapshot(projectileSnapshot);
        projectileRecords.clear();
        advanceTo(projectileSimulator, 35.0 * FRAME);
        assertEquals(1, projectileRecords.size(),
                "Aloy repeated restore schedules pending projectile once");
        assertEquals("Rapid Fire N1-2",
                projectileRecords.get(0).action.getName(),
                "Aloy restored projectile preserves hit identity");
    }

    private static void testInvalidInputsAndIsolation() {
        assertThrows(NullPointerException.class,
                () -> CharacterActionRequest.skill(null),
                "Aloy action request rejects null Skill mode");
        Aloy invalid = new Aloy(null, null);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Aloy rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Aloy rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> invalidSimulator.performAction(
                        CharacterId.ALOY,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Aloy rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Aloy rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.PLUNGE),
                "Aloy rejects excluded Plunge");

        Aloy absent = new Aloy(null, null);
        assertThrows(IllegalArgumentException.class,
                () -> absent.initializeForSimulator(new CombatSimulator()),
                "Aloy rejects a simulator that does not own it");
        Aloy reused = new Aloy(null, null);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Aloy rejects cross-simulator reuse");

        Aloy owner = new Aloy(null, null);
        Aloy foreign = new Aloy(null, null);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Aloy rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, new CombatSimulator()),
                "Aloy rejects a foreign state type");

        Aloy noEnemy = new Aloy(null, null);
        CombatSimulator noEnemySimulator = new CombatSimulator();
        noEnemySimulator.setLoggingEnabled(false);
        noEnemySimulator.addCharacter(noEnemy);
        assertThrows(NullPointerException.class,
                () -> performSkill(noEnemySimulator),
                "Aloy requires an enemy target for attack resolution");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
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
                CharacterId.ALOY, CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.ALOY,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ALOY) {
                records.add(new ActionRecord(action, damage, time));
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

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = named(records, name);
        assertEquals(1, selected.size(), name + " action count");
        return selected.get(0);
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

    private static void addStatBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            String name,
            StatType stat,
            double amount) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                character.addBuff(new SimpleBuff(
                        name,
                        100.0,
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
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Aloy,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Aloy/Aloy_Status.csv",
                "config/characters/Aloy/Aloy_Multipliers.csv"
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
        throw new AssertionError("Aloy CSVs missing key " + key);
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
