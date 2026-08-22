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
import mechanics.formula.DamageCalculator;
import model.character.Escoffier;
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
import simulation.event.SimpleTimerEvent;

/**
 * Source-backed regression coverage for Escoffier's bounded offensive slice.
 *
 * <p>The checks pin loader data, fixed-target frames, Skill generation and
 * snapshots, delayed cooldown/Energy operations, typed support, constellation
 * quotas, rollback reconstruction, and fail-closed unsupported branches.</p>
 */
public final class EscoffierRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private EscoffierRegressionTest() {
    }

    /** Runs every Escoffier regression assertion. */
    public static void main(String[] args) throws Exception {
        testIdentityAndLoaderData();
        testFixedTargetBasics();
        testSkillCadenceParticlesAndIcd();
        testSkillCooldownAndGenerationInvalidation();
        testBurstTimingEnergyAndHitTimeStats();
        testA4AndC1CompositionSupport();
        testC2ColdDishSupport();
        testC3C5AndC6Offense();
        testSnapshotRestoreAndIsolation();
        testUnsupportedBoundaries();
        System.out.println("Escoffier regression tests passed.");
    }

    private static void testIdentityAndLoaderData() throws IOException {
        assertEquals(88, CharacterId.ESCOFFIER.getNumericId(),
                "Escoffier numeric id");
        assertEquals(CharacterId.ESCOFFIER,
                CharacterId.fromNumericId(88),
                "Escoffier numeric lookup");
        assertEquals(CharacterId.ESCOFFIER,
                CharacterId.fromName("Escoffier"),
                "Escoffier display lookup");

        Escoffier escoffier = new Escoffier(null, null, 0);
        assertClose(13348.0,
                escoffier.getBaseStats().get(StatType.BASE_HP),
                "Level-90 Base HP");
        assertClose(347.0,
                escoffier.getBaseStats().get(StatType.BASE_ATK),
                "Level-90 Base ATK");
        assertClose(732.0,
                escoffier.getBaseStats().get(StatType.BASE_DEF),
                "Level-90 Base DEF");
        assertClose(0.242,
                escoffier.getBaseStats().get(StatType.CRIT_RATE),
                "Default plus ascension CRIT Rate");
        assertClose(60.0, escoffier.getEnergyCost(),
                "Burst Energy cost");
        assertClose(15.0, escoffier.getSkillCD(),
                "Skill cooldown");
        assertClose(15.0, escoffier.getBurstCD(),
                "Burst cooldown");

        assertCsvShape(Path.of(
                "config/characters/Escoffier/Escoffier_Status.csv"),
                13);
        assertCsvShape(Path.of(
                "config/characters/Escoffier/Escoffier_Multipliers.csv"),
                34);
        assertCsvValue("Low-Temperature Cooking C3", 1.008);
        assertCsvValue("Frosty Parfait C3", 2.4);
        assertCsvValue("Scoring Cuts C5", 11.856);
        assertCsvValue("A4 Four Member Shred", 0.55);
    }

    private static void testFixedTargetBasics() {
        Escoffier escoffier = new Escoffier(null, null, 0);
        CombatSimulator simulator = simulatorWith(escoffier);
        List<ActionRecord> records = captureActions(simulator);

        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose((108.0 + 32.0) * FRAME,
                simulator.getCurrentTime(),
                "Three-step Normal string duration");
        assertAction(records, "Kitchen Skills N1", 0.947099,
                7.0 * FRAME, Element.PHYSICAL);
        assertAction(records, "Kitchen Skills N2", 0.874388,
                (33.0 + 8.0) * FRAME, Element.PHYSICAL);
        assertAction(records, "Kitchen Skills N3-1", 0.606270,
                (73.0 + 16.0) * FRAME, Element.PHYSICAL);
        assertAction(records, "Kitchen Skills N3-2", 0.740996,
                (86.0 + 16.0) * FRAME, Element.PHYSICAL);
        assertHitlagProfile(named(records, "Kitchen Skills N3-1")
                        .get(0).action,
                0.06, 0.01, true, false, false,
                "Kitchen Skills N3-1");
        assertHitlagProfile(named(records, "Kitchen Skills N3-2")
                        .get(0).action,
                0.06, 0.01, true, false, false,
                "Kitchen Skills N3-2");

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertAction(records, "Kitchen Skills Charged Attack", 2.120360,
                chargedCast + 20.0 * FRAME, Element.PHYSICAL);
        assertClose(chargedCast + 65.0 * FRAME,
                simulator.getCurrentTime(),
                "Charged Attack duration");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertAction(records, "Kitchen Skills High Plunge", 2.933586,
                plungeCast + 43.0 * FRAME, Element.PHYSICAL);
        assertClose(plungeCast + 77.0 * FRAME,
                simulator.getCurrentTime(),
                "High Plunge duration");
    }

    private static void testSkillCadenceParticlesAndIcd() {
        Escoffier escoffier = new Escoffier(null, null, 0);
        CombatSimulator simulator = simulatorWith(escoffier);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureCryoParticles(simulator);

        performSkill(simulator);
        assertClose(35.0 * FRAME, simulator.getCurrentTime(),
                "Press Skill action duration");
        ActionRecord initial = named(records,
                "Low-Temperature Cooking").get(0);
        assertClose(23.0 * FRAME, initial.time,
                "Press Skill initial hitmark");
        assertClose(0.8568, initial.action.getDamagePercent(),
                "Talent-9 initial multiplier");
        assertEquals(ICDType.ESCOFFIER_SKILL,
                initial.action.getICDType(),
                "Initial Skill private ICD type");
        assertEquals(ICDTag.ESCOFFIER_SKILL,
                initial.action.getICDTag(),
                "Initial Skill private ICD tag");
        assertClose(1.0, initial.action.getGaugeUnits(),
                "Initial Skill gauge");

        advanceTo(simulator, 83.0 * FRAME + EPSILON);
        ActionRecord surging = named(records, "Surging Blade").get(0);
        assertClose(83.0 * FRAME, surging.time,
                "Surging Blade hitmark");
        assertClose(0.5712, surging.action.getDamagePercent(),
                "Surging Blade multiplier");
        assertClose(0.0, surging.action.getGaugeUnits(),
                "Surging Blade does not invent Arkhe gauge");

        advanceTo(simulator, 153.0 * FRAME + EPSILON);
        ActionRecord firstTick = named(records, "Frosty Parfait").get(0);
        assertClose(153.0 * FRAME, firstTick.time,
                "First Cooking Mek impact includes travel");
        assertClose(2.04, firstTick.action.getDamagePercent(),
                "Talent-9 periodic multiplier");
        assertTrue(firstTick.action.hasStatSnapshot(),
                "Traveling periodic attack owns emission snapshot");
        advanceTo(simulator, 212.0 * FRAME + EPSILON);
        assertClose(212.0 * FRAME,
                named(records, "Frosty Parfait").get(1).time,
                "58.5-frame cadence rounds each emission upward");

        advanceTo(simulator, 1323.0 * FRAME + EPSILON);
        assertEquals(21, named(records, "Frosty Parfait").size(),
                "Cooking Mek emits exactly 21 periodic hits");
        assertEquals(1, particles.size(),
                "Initial hit emits one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Particle packet count");
        assertClose(123.0 * FRAME, particles.get(0).time,
                "Particle travel from frame-23 hit");

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "Escoffier", ICDTag.ESCOFFIER_SKILL,
                ICDType.ESCOFFIER_SKILL, 0.0),
                "Private ICD admits first application");
        assertTrue(!manager.checkApplication(
                "Escoffier", ICDTag.ESCOFFIER_SKILL,
                ICDType.ESCOFFIER_SKILL, 1.499),
                "Private ICD blocks before 1.5 seconds");
        assertTrue(manager.checkApplication(
                "Escoffier", ICDTag.ESCOFFIER_SKILL,
                ICDType.ESCOFFIER_SKILL, 1.5),
                "Private ICD admits exact 1.5-second boundary");
    }

    private static void testSkillCooldownAndGenerationInvalidation() {
        Escoffier escoffier = new Escoffier(null, null, 0);
        CombatSimulator simulator = simulatorWith(escoffier);
        List<ActionRecord> records = captureActions(simulator);

        performSkill(simulator);
        assertClose(15.0 - 13.0 * FRAME,
                escoffier.getSkillCDRemaining(simulator.getCurrentTime()),
                "Skill cooldown starts at frame 22");
        performSkill(simulator);
        double secondCast = 922.0 * FRAME;
        assertClose(secondCast + 35.0 * FRAME,
                simulator.getCurrentTime(),
                "Immediate recast waits for delayed cooldown boundary");
        assertClose(secondCast + 22.0 * FRAME,
                escoffier.getLastSkillTime(),
                "Second delayed cooldown start timestamp");

        advanceTo(simulator, secondCast + 152.0 * FRAME);
        for (ActionRecord record : named(records, "Frosty Parfait")) {
            assertTrue(record.time < secondCast,
                    "Recast invalidates old-generation future ticks");
        }
        advanceTo(simulator, secondCast + 153.0 * FRAME + EPSILON);
        ActionRecord firstNew = named(records, "Frosty Parfait")
                .get(named(records, "Frosty Parfait").size() - 1);
        assertClose(secondCast + 153.0 * FRAME, firstNew.time,
                "Replacement generation starts its own cadence");
    }

    private static void testBurstTimingEnergyAndHitTimeStats() {
        Escoffier baseline = new Escoffier(null, null, 0);
        CombatSimulator baselineSimulator = simulatorWith(baseline);
        List<ActionRecord> baselineRecords = captureActions(
                baselineSimulator);
        perform(baselineSimulator, CharacterActionKey.BURST);
        ActionRecord baselineBurst = named(
                baselineRecords, "Scoring Cuts").get(0);
        assertClose(92.0 * FRAME, baselineBurst.time,
                "Burst offensive hitmark");
        assertClose(10.0776, baselineBurst.action.getDamagePercent(),
                "Talent-9 Burst multiplier");
        assertClose(0.0, baseline.getCurrentEnergy(),
                "Burst spends 60 Energy at frame 5");
        assertClose(15.0 - 110.0 * FRAME,
                baseline.getBurstCDRemaining(
                        baselineSimulator.getCurrentTime()),
                "Burst cooldown starts at cast time");

        Escoffier live = new Escoffier(null, null, 0);
        CombatSimulator liveSimulator = simulatorWith(live);
        List<ActionRecord> liveRecords = captureActions(liveSimulator);
        liveSimulator.registerEvent(new SimpleTimerEvent(60.0 * FRAME, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                live.addBuff(new SimpleBuff(
                        "Late Burst ATK",
                        2.0,
                        activeSimulator.getCurrentTime(),
                        stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
            }
        });
        perform(liveSimulator, CharacterActionKey.BURST);
        ActionRecord liveBurst = named(liveRecords, "Scoring Cuts").get(0);
        assertClose(2.0, liveBurst.damage / baselineBurst.damage,
                "Burst snapshots live stats at frame-92 hitmark");
        assertTrue(!liveBurst.action.hasStatSnapshot(),
                "Burst does not capture cast-time stats");
    }

    private static void testA4AndC1CompositionSupport() {
        Escoffier c1 = new Escoffier(null, null, 1);
        TestCharacter hydroOne = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        TestCharacter cryoTwo = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        TestCharacter hydroThree = new TestCharacter(
                CharacterId.BARBARA, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(
                c1, hydroOne, cryoTwo, hydroThree);
        performSkill(simulator);

        Buff a4 = typedBuff(simulator,
                BuffId.ESCOFFIER_A4_CRYO_HYDRO_RES_SHRED);
        StatsContainer a4Stats = new StatsContainer();
        a4.apply(a4Stats, simulator.getCurrentTime());
        assertClose(0.55, a4Stats.get(StatType.CRYO_RES_SHRED),
                "A4 four-member Cryo shred");
        assertClose(0.55, a4Stats.get(StatType.HYDRO_RES_SHRED),
                "A4 four-member Hydro shred");
        assertClose(23.0 * FRAME, a4.getStartTime(),
                "A4 begins after the triggering initial hit");
        assertClose(23.0 * FRAME + 12.0, a4.getExpirationTime(),
                "A4 exact 12-second window");

        Buff c1Buff = typedBuff(simulator,
                BuffId.ESCOFFIER_C1_CRYO_CRIT_DMG);
        StatsContainer c1Stats = new StatsContainer();
        c1Buff.apply(c1Stats, simulator.getCurrentTime());
        assertClose(0.60, c1Stats.get(StatType.CRYO_CRIT_DMG),
                "C1 grants typed Cryo CRIT DMG");
        assertClose(15.0, c1Buff.getExpirationTime(),
                "C1 starts on cast for exactly 15 seconds");

        Escoffier mixed = new Escoffier(null, null, 1);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator mixedSimulator = simulatorWith(mixed, pyro);
        performSkill(mixedSimulator);
        assertTrue(findTypedBuff(
                mixedSimulator,
                BuffId.ESCOFFIER_C1_CRYO_CRIT_DMG) == null,
                "C1 fails closed for a mixed-element party");
        Buff mixedA4 = typedBuff(mixedSimulator,
                BuffId.ESCOFFIER_A4_CRYO_HYDRO_RES_SHRED);
        StatsContainer mixedStats = new StatsContainer();
        mixedA4.apply(mixedStats, mixedSimulator.getCurrentTime());
        assertClose(0.05, mixedStats.get(StatType.CRYO_RES_SHRED),
                "A4 counts only Escoffier in mixed party");
    }

    private static void testC2ColdDishSupport() {
        Escoffier c2 = new Escoffier(null, null, 2);
        TestCharacter cryoAlly = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator simulator = simulatorWith(c2, cryoAlly);
        performSkill(simulator);
        simulator.setActiveCharacter(CharacterId.KAEYA);

        AttackAction eligible = testAttack(
                "Eligible Cryo Normal", Element.CRYO, ActionType.NORMAL);
        StatsContainer resolved = DamageCalculator.resolveTargetStats(
                cryoAlly,
                simulator.getEnemy(),
                eligible,
                simulator.getApplicableBuffs(cryoAlly),
                simulator.getCurrentTime(),
                simulator);
        assertClose(347.0 * 2.4,
                resolved.get(StatType.FLAT_DMG_BONUS),
                "C2 uses Escoffier's live ATK before ally damage");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.KAEYA, eligible);
        assertEquals(4, c2.getC2Count(simulator.getCurrentTime()),
                "Eligible ally Cryo hit consumes one Cold Dish");

        simulator.setActiveCharacter(CharacterId.ESCOFFIER);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.KAEYA,
                testAttack("Off-field Cryo", Element.CRYO,
                        ActionType.NORMAL));
        assertEquals(4, c2.getC2Count(simulator.getCurrentTime()),
                "Off-field ally cannot consume C2");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.ESCOFFIER,
                testAttack("Owner Cryo", Element.CRYO,
                        ActionType.NORMAL));
        assertEquals(4, c2.getC2Count(simulator.getCurrentTime()),
                "Escoffier cannot consume her own C2");

        simulator.setActiveCharacter(CharacterId.KAEYA);
        for (int hit = 0; hit < 4; hit++) {
            simulator.performActionWithoutTimeAdvance(
                    CharacterId.KAEYA,
                    testAttack("Cold Dish " + hit,
                            Element.CRYO, ActionType.SKILL));
        }
        assertEquals(0, c2.getC2Count(simulator.getCurrentTime()),
                "C2 stops after five total eligible hits");
        StatsContainer exhausted = DamageCalculator.resolveTargetStats(
                cryoAlly,
                simulator.getEnemy(),
                eligible,
                simulator.getApplicableBuffs(cryoAlly),
                simulator.getCurrentTime(),
                simulator);
        assertClose(0.0, exhausted.get(StatType.FLAT_DMG_BONUS),
                "Exhausted C2 adds no flat damage");
    }

    private static void testC3C5AndC6Offense() {
        Escoffier c3 = new Escoffier(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator);
        advanceTo(c3Simulator, 153.0 * FRAME + EPSILON);
        assertClose(1.008,
                named(c3Records, "Low-Temperature Cooking")
                        .get(0).action.getDamagePercent(),
                "C3 raises initial Skill multiplier");
        assertClose(0.672,
                named(c3Records, "Surging Blade")
                        .get(0).action.getDamagePercent(),
                "C3 raises Surging Blade multiplier");
        assertClose(2.4,
                named(c3Records, "Frosty Parfait")
                        .get(0).action.getDamagePercent(),
                "C3 raises Cooking Mek multiplier");

        Escoffier c5 = new Escoffier(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(11.856,
                named(c5Records, "Scoring Cuts")
                        .get(0).action.getDamagePercent(),
                "C5 raises Burst multiplier");

        Escoffier c6 = new Escoffier(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO);
        CombatSimulator c6Simulator = simulatorWith(c6, ally);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        performSkill(c6Simulator);
        c6Simulator.setActiveCharacter(CharacterId.KAEYA);
        c6Simulator.performActionWithoutTimeAdvance(
                CharacterId.KAEYA,
                testAttack("C6 Trigger 1", Element.PHYSICAL,
                        ActionType.NORMAL));
        c6Simulator.performActionWithoutTimeAdvance(
                CharacterId.KAEYA,
                testAttack("C6 Gate Block", Element.PHYSICAL,
                        ActionType.NORMAL));
        assertEquals(5, c6.getC6Count(),
                "C6 same-time trigger is blocked by 0.5-second gate");
        for (int trigger = 2; trigger <= 6; trigger++) {
            c6Simulator.advanceTime(0.5);
            c6Simulator.performActionWithoutTimeAdvance(
                    CharacterId.KAEYA,
                    testAttack("C6 Trigger " + trigger,
                            Element.PHYSICAL, ActionType.NORMAL));
        }
        assertEquals(0, c6.getC6Count(),
                "C6 stops after six follow-ups");
        c6Simulator.advanceTime(6.0 * FRAME);
        List<ActionRecord> followUps = named(
                c6Records, "Special-Grade Frozen Parfait");
        assertEquals(6, followUps.size(),
                "C6 emits six represented follow-up attacks");
        for (ActionRecord followUp : followUps) {
            assertClose(5.0, followUp.action.getDamagePercent(),
                    "C6 500 percent ATK multiplier");
            assertTrue(followUp.action.hasStatSnapshot(),
                    "C6 travel hit owns trigger-time stats");
            assertEquals(ICDTag.ESCOFFIER_SKILL,
                    followUp.action.getICDTag(),
                    "C6 shares Skill ICD group");
        }
    }

    private static void testSnapshotRestoreAndIsolation() {
        Escoffier escoffier = new Escoffier(null, null, 0);
        CombatSimulator simulator = simulatorWith(escoffier);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator);
        escoffier.addBuff(new SimpleBuff(
                "Emission Snapshot ATK",
                115.0 * FRAME,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        advanceTo(simulator, 150.0 * FRAME);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(4.0 * FRAME);
        ActionRecord original = named(records, "Frosty Parfait").get(0);
        assertClose(1.0,
                original.action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Periodic hit preserves frame-148 emission stats");
        assertClose(0.0,
                original.action.getStatSnapshot()
                        .get(StatType.CRYO_RES_SHRED),
                "Enemy-facing A4 is not frozen into emission stats");
        StatsContainer impactStats = DamageCalculator.resolveTargetStats(
                escoffier,
                simulator.getEnemy(),
                original.action,
                simulator.getApplicableBuffs(escoffier),
                simulator.getCurrentTime(),
                simulator);
        assertClose(0.05,
                impactStats.get(StatType.CRYO_RES_SHRED),
                "Snapshotted travel hit reads A4 live at impact");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(4.0 * FRAME);
        assertEquals(1, named(records, "Frosty Parfait").size(),
                "Repeated restore reconstructs pending impact once");
        assertClose(1.0,
                named(records, "Frosty Parfait").get(0)
                        .action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Restored impact preserves emission snapshot");

        Escoffier foreign = new Escoffier(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!escoffier.acceptsCharacterState(foreignState),
                "Escoffier rejects another instance's state payload");
    }

    private static void testUnsupportedBoundaries() {
        assertThrows(IllegalArgumentException.class,
                () -> new Escoffier(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Escoffier(null, null, 7),
                "Constellation above C6 is rejected");

        Escoffier escoffier = new Escoffier(null, null, 6);
        CombatSimulator simulator = simulatorWith(escoffier);
        assertThrows(IllegalArgumentException.class,
                () -> escoffier.onAction(null, simulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> escoffier.onAction(
                        CharacterActionRequest.skill(SkillActionMode.HOLD),
                        simulator),
                "Hold Skill is rejected");
        assertTrue(escoffier.isSurgingBladeRepresented(),
                "Source-backed Surging Blade offense is represented");
        assertTrue(!escoffier.isLowPlungeRepresented(),
                "Low Plunge remains excluded");
        assertTrue(!escoffier.isC4HealingEnergyRepresented(),
                "Healing-driven C4 remains excluded");

        Escoffier reused = new Escoffier(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Character instance cannot cross simulator owners");
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
                actionType == ActionType.NORMAL
                        ? StatType.NORMAL_ATTACK_DMG_BONUS
                        : StatType.SKILL_DMG_BONUS,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        return action;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
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
                CharacterId.ESCOFFIER,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.ESCOFFIER,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ESCOFFIER) {
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

    private static Buff typedBuff(
            CombatSimulator simulator,
            BuffId id) {
        Buff buff = findTypedBuff(simulator, id);
        if (buff == null) {
            throw new AssertionError("Missing typed buff " + id);
        }
        return buff;
    }

    private static Buff findTypedBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        return null;
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

    private static void assertAction(
            List<ActionRecord> records,
            String name,
            double multiplier,
            double time,
            Element element) {
        ActionRecord record = named(records, name).get(0);
        assertClose(multiplier, record.action.getDamagePercent(),
                name + " multiplier");
        assertClose(time, record.time, name + " hitmark");
        assertEquals(element, record.action.getElement(),
                name + " element");
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
            assertTrue(lines.get(index).startsWith("Escoffier,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Escoffier/Escoffier_Status.csv",
                "config/characters/Escoffier/Escoffier_Multipliers.csv"
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
        throw new AssertionError("Escoffier CSVs missing key " + key);
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

    private static void assertHitlagProfile(
            AttackAction action,
            double haltTime,
            double factor,
            boolean defenseHalt,
            boolean deployable,
            boolean headshotOnly,
            String message) {
        assertClose(haltTime,
                action.getHitlagProfile().getHaltTimeSeconds(),
                message + " hitlag halt time");
        assertClose(factor, action.getHitlagProfile().getFactor(),
                message + " hitlag factor");
        assertEquals(defenseHalt,
                action.getHitlagProfile().canDefenseHalt(),
                message + " hitlag Defense Halt");
        assertEquals(deployable,
                action.getHitlagProfile().isDeployable(),
                message + " hitlag deployable");
        assertEquals(headshotOnly,
                action.getHitlagProfile().isHeadshotOnly(),
                message + " hitlag headshot-only");
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
