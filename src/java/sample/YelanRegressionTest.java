package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Yelan;
import model.entity.Character;
import model.entity.Enemy;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Yelan's Exquisite Throw vertical slice. */
public final class YelanRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private YelanRegressionTest() {
    }

    /** Runs data, timing, constellation, abnormal, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndConstructors();
        testNormalStringAndHighPlunge();
        testSkillTimingParticlesAndCharges();
        testBurstInitialA4AndWaveGate();
        testSkillCoordinationAndProjectileMicrosnapshots();
        testA1C3C4C5AndC6();
        testRepeatedRestoreAndStaleWork();
        testInvalidInputsCooldownEnergyAndOwnership();
        System.out.println("YelanRegressionTest passed");
    }

    private static void testIdentityStatsDataAndConstructors()
            throws IOException {
        Yelan yelan = new Yelan(null, null, 6);
        assertEquals(CharacterId.YELAN, yelan.getCharacterId(),
                "Yelan typed identity");
        assertEquals(Element.HYDRO, yelan.getElement(), "Yelan element");
        assertClose(14450.0,
                yelan.getBaseStats().get(StatType.BASE_HP), "Yelan base HP");
        assertClose(244.0,
                yelan.getBaseStats().get(StatType.BASE_ATK), "Yelan base ATK");
        assertClose(548.0,
                yelan.getBaseStats().get(StatType.BASE_DEF), "Yelan base DEF");
        assertClose(0.242,
                yelan.getBaseStats().get(StatType.CRIT_RATE),
                "Yelan base plus ascension CRIT Rate");
        assertClose(70.0, yelan.getEnergyCost(), "Yelan Energy cost");
        assertClose(10.0, yelan.getSkillCD(), "Yelan Skill cooldown");
        assertClose(18.0, yelan.getBurstCD(), "Yelan Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.YELAN,
                    new Yelan(null, null, constellation).getCharacterId(),
                    "Yelan constellation " + constellation);
        }
        assertCsvShape(Paths.get(
                "config/characters/Yelan/Yelan_Status.csv"), 10);
        assertCsvShape(Paths.get(
                "config/characters/Yelan/Yelan_Multipliers.csv"), 23);
        assertTrue(Files.exists(Paths.get(
                "config/characters/Yelan/face.png")),
                "Yelan face asset remains present");
    }

    private static void testNormalStringAndHighPlunge() {
        Yelan yelan = new Yelan(null, null, 0);
        CombatSimulator simulator = simulatorWith(yelan);
        List<ActionRecord> records = captureYelanActions(simulator);
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        double[] multipliers = {
                0.74734, 0.71732, 0.948, 0.59724, 0.59724
        };
        double[] frames = { 23, 38, 64, 99, 113 };
        assertEquals(5, records.size(), "Yelan N1-N4 damage instances");
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Yelan Normal multiplier " + index);
            assertClose(frames[index] * FRAME, record.time,
                    "Yelan Normal impact " + index);
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(), "Yelan Normal category");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(), "Yelan Normal ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(), "Yelan Normal ICD tag");
            assertClose(0.0, record.action.getGaugeUnits(),
                    "Yelan Physical Normal gauge");
        }
        assertClose(141.0 * FRAME, simulator.getCurrentTime(),
                "Yelan N1-N4 animation durations");

        Yelan plungeYelan = new Yelan(null, null, 0);
        CombatSimulator plungeSim = simulatorWith(plungeYelan);
        List<ActionRecord> plungeRecords = captureYelanActions(plungeSim);
        perform(plungeSim, CharacterActionKey.PLUNGE);
        ActionRecord plunge = plungeRecords.get(0);
        assertClose(41.0 * FRAME, plunge.time, "Yelan Plunge hitmark");
        assertClose(84.0 * FRAME, plungeSim.getCurrentTime(),
                "Yelan Plunge duration");
        assertClose(2.6076, plunge.action.getDamagePercent(),
                "Yelan High Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Yelan Plunge category");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Yelan Plunge no ICD");
        assertEquals(ICDTag.None, plunge.action.getICDTag(),
                "Yelan Plunge no ICD tag");
        assertTrue(plunge.action.isShatterTrigger(),
                "Yelan High Plunge is blunt");
    }

    private static void testSkillTimingParticlesAndCharges() {
        Yelan yelan = new Yelan(null, null, 0);
        CombatSimulator simulator = simulatorWith(yelan);
        List<ActionRecord> records = captureYelanActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord lifeline = named(records, "Lingering Lifeline").get(0);
        assertClose(36.0 * FRAME, lifeline.time, "Lifeline impact");
        assertClose(42.0 * FRAME, simulator.getCurrentTime(),
                "Lifeline action duration");
        assertClose(0.384431, lifeline.action.getDamagePercent(),
                "Lifeline Talent 9 multiplier");
        assertEquals(StatType.BASE_HP, lifeline.action.getScalingStat(),
                "Lifeline Max HP scaling");
        assertEquals(ActionType.SKILL, lifeline.action.getActionType(),
                "Lifeline Skill category");
        assertEquals(ICDType.Standard, lifeline.action.getICDType(),
                "Lifeline standard ICD");
        assertEquals(ICDTag.ElementalSkill, lifeline.action.getICDTag(),
                "Lifeline independent Skill tag");
        assertClose(1.0, lifeline.action.getGaugeUnits(), "Lifeline 1U");
        assertClose(33.0 * FRAME + 10.0,
                yelan.getSkillCooldownEndTime(),
                "Lifeline cooldown starts at frame 33");
        advanceTo(simulator, 136.0 * FRAME);
        assertEquals(1, particles.size(), "Lifeline particle event");
        assertClose(4.0, particles.get(0).count,
                "Lifeline creates four particles");
        assertClose(136.0 * FRAME, particles.get(0).time,
                "Lifeline particle arrival");

        Yelan c1 = new Yelan(null, null, 1);
        CombatSimulator c1Sim = simulatorWith(c1);
        perform(c1Sim, CharacterActionKey.SKILL);
        double secondCast = c1Sim.getCurrentTime();
        perform(c1Sim, CharacterActionKey.SKILL);
        assertClose(secondCast + 42.0 * FRAME, c1Sim.getCurrentTime(),
                "C1 second charge casts without waiting");
        double firstRestore = 33.0 * FRAME + 10.0;
        assertClose(firstRestore,
                c1.getSkillCDRemaining(c1Sim.getCurrentTime())
                        + c1Sim.getCurrentTime(),
                "C1 first charge restore boundary");
        advanceTo(c1Sim, firstRestore);
        perform(c1Sim, CharacterActionKey.SKILL);
        assertTrue(!c1.getChargeRestoreTimes().isEmpty()
                        && c1.getChargeRestoreTimes().stream()
                                .anyMatch(time -> time > firstRestore),
                "C1 keeps the remaining sequential restore queued");
    }

    private static void testBurstInitialA4AndWaveGate() {
        Yelan yelan = new Yelan(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(yelan, ally);
        List<ActionRecord> records = captureYelanActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord initial = named(
                records, "Depth-Clarion Dice (Initial)").get(0);
        assertClose(76.0 * FRAME, initial.time, "Burst initial hitmark");
        assertClose(0.124236, initial.action.getDamagePercent(),
                "Burst initial Talent 9 multiplier");
        assertEquals(StatType.BASE_HP, initial.action.getScalingStat(),
                "Burst initial Max HP scaling");
        assertEquals(ICDType.None, initial.action.getICDType(),
                "Burst initial no ICD");
        assertEquals(ICDTag.None, initial.action.getICDTag(),
                "Burst initial independent ICD tag");
        assertClose(2.0, initial.action.getGaugeUnits(), "Burst initial 2U");
        assertClose(93.0 * FRAME, simulator.getCurrentTime(),
                "Burst action duration");
        assertClose(0.0, yelan.getCurrentEnergy(),
                "Burst spends Energy at frame 6");
        assertTrue(yelan.isExquisiteThrowActive(73.0 * FRAME),
                "Burst starts exactly at frame 73");
        assertTrue(!yelan.isExquisiteThrowActive(
                73.0 * FRAME + 15.0),
                "Burst expires at exact 15-second boundary");
        assertClose(0.01,
                applicableStat(simulator, yelan,
                        StatType.DMG_BONUS_ALL, 73.0 * FRAME),
                "A4 starts at one percent");
        assertClose(0.045,
                applicableStat(simulator, yelan,
                        StatType.DMG_BONUS_ALL, 73.0 * FRAME + 1.0),
                "A4 increments exactly after one second");
        assertClose(0.50,
                applicableStat(simulator, yelan,
                        StatType.DMG_BONUS_ALL, 73.0 * FRAME + 14.0),
                "A4 reaches its cap");

        simulator.setActiveCharacter(CharacterId.NOELLE);
        double firstTrigger = simulator.getCurrentTime();
        resolveNormalProbe(simulator, CharacterId.NOELLE, "first");
        resolveNormalProbe(simulator, CharacterId.NOELLE, "same-instance");
        advanceTo(simulator, firstTrigger + 1.0 - FRAME);
        resolveNormalProbe(simulator, CharacterId.NOELLE, "before-boundary");
        advanceTo(simulator, firstTrigger + 1.0);
        resolveNormalProbe(simulator, CharacterId.NOELLE, "exact-boundary");
        advanceTo(simulator, firstTrigger + 1.0 + 40.0 * FRAME);
        assertEquals(6, named(records, "Exquisite Throw Projectile").size(),
                "same-time and before-gate hits do not duplicate waves");
        assertEquals(1, named(records, "Exquisite Throw (C2)").size(),
                "C2 has an independent 1.8-second gate");
        assertClose(firstTrigger + 17.0 * FRAME,
                named(records, "Exquisite Throw (C2)").get(0).time,
                "C2 hitmark");
        for (ActionRecord projectile
                : named(records, "Exquisite Throw Projectile")) {
            assertEquals(ActionType.BURST,
                    projectile.action.getActionType(),
                    "Throw counts as Burst damage");
            assertEquals(ICDType.Standard,
                    projectile.action.getICDType(),
                    "Throw standard ICD");
            assertEquals(ICDTag.ElementalBurst,
                    projectile.action.getICDTag(),
                    "Throw independent Burst ICD group");
            assertTrue(projectile.action.hasStatSnapshot(),
                    "Throw projectile carries a microsnapshot");
        }
    }

    private static void testSkillCoordinationAndProjectileMicrosnapshots() {
        Yelan yelan = new Yelan(null, null, 0);
        CombatSimulator simulator = simulatorWith(yelan);
        List<ActionRecord> records = captureYelanActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        double skillStart = simulator.getCurrentTime();
        double trigger = skillStart + 36.0 * FRAME;
        addBuffAt(
                simulator,
                yelan,
                trigger + 5.0 * FRAME,
                new TestBuff(
                        "Yelan projectile microsnapshot probe",
                        trigger + 5.0 * FRAME,
                        5.0,
                        StatType.HP_PERCENT,
                        1.0));
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, trigger + 40.0 * FRAME);
        List<ActionRecord> waves = named(
                records, "Exquisite Throw Projectile");
        assertEquals(3, waves.size(),
                "Lifeline independently coordinates one wave");
        assertClose(trigger + 20.0 * FRAME, waves.get(0).time,
                "Throw first projectile travel");
        assertClose(trigger + 26.0 * FRAME, waves.get(1).time,
                "Throw second projectile travel");
        assertClose(trigger + 32.0 * FRAME, waves.get(2).time,
                "Throw third projectile travel");
        assertClose(0.06,
                waves.get(0).action.getStatSnapshot()
                        .get(StatType.HP_PERCENT),
                "first projectile snapshots before probe buff");
        assertClose(1.06,
                waves.get(1).action.getStatSnapshot()
                        .get(StatType.HP_PERCENT),
                "second projectile snapshots after probe buff");
        assertClose(1.06,
                waves.get(2).action.getStatSnapshot()
                        .get(StatType.HP_PERCENT),
                "third projectile snapshots independently");
    }

    private static void testA1C3C4C5AndC6() {
        Element[] elements = {
                Element.HYDRO, Element.GEO, Element.PYRO, Element.CRYO
        };
        double[] expectedA1 = { 0.06, 0.12, 0.18, 0.30 };
        for (int count = 1; count <= 4; count++) {
            Yelan yelan = new Yelan(null, null, 0);
            Character[] party = new Character[count];
            party[0] = yelan;
            for (int index = 1; index < count; index++) {
                party[index] = new TestCharacter(
                        CharacterId.values()[index], elements[index]);
            }
            CombatSimulator simulator = simulatorWith(party);
            assertClose(expectedA1[count - 1],
                    yelan.getEffectiveStats(0.0).get(StatType.HP_PERCENT),
                    "A1 distinct element count " + count);
            assertTrue(simulator.getCharacter(CharacterId.YELAN) == yelan,
                    "A1 fixture keeps Yelan in party");
        }

        Yelan c3 = new Yelan(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Records = captureYelanActions(c3Sim);
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(0.14616,
                named(c3Records, "Depth-Clarion").get(0)
                        .action.getDamagePercent(),
                "C3 raises Burst initial to Talent 12");

        Yelan c5 = new Yelan(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Records = captureYelanActions(c5Sim);
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(0.452272,
                named(c5Records, "Lingering Lifeline").get(0)
                        .action.getDamagePercent(),
                "C5 raises Lifeline to Talent 12");

        Yelan c4 = new Yelan(null, null, 4);
        TestCharacter c4Ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator c4Sim = simulatorWith(c4, c4Ally);
        for (int cast = 0; cast < 4; cast++) {
            perform(c4Sim, CharacterActionKey.SKILL);
        }
        double fourthHit = c4Sim.getCurrentTime() - 6.0 * FRAME;
        assertEquals(4, c4.getC4Stacks(fourthHit), "C4 caps at four stacks");
        assertClose(0.40,
                applicableStat(c4Sim, c4Ally,
                        StatType.HP_PERCENT, fourthHit),
                "C4 grants party Max HP");
        assertEquals(4, c4.getC4Stacks(fourthHit + 25.0 - EPSILON),
                "C4 survives immediately before expiry");
        assertEquals(0, c4.getC4Stacks(fourthHit + 25.0),
                "C4 expires exactly at 25 seconds");

        Yelan c6 = new Yelan(null, null, 6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Records = captureYelanActions(c6Sim);
        perform(c6Sim, CharacterActionKey.BURST);
        assertEquals(5, c6.getC6ArrowsRemaining(c6Sim.getCurrentTime()),
                "C6 starts with five arrows");
        for (int step = 0; step < 4; step++) {
            perform(c6Sim, CharacterActionKey.NORMAL);
        }
        advanceTo(c6Sim, c6Sim.getCurrentTime() + 2.0);
        List<ActionRecord> barbs = named(
                c6Records, "Winner Takes All Breakthrough Barb");
        assertEquals(5, barbs.size(), "C6 replaces exactly five arrows");
        assertEquals(0, c6.getC6ArrowsRemaining(c6Sim.getCurrentTime()),
                "C6 consumes its five-arrow count");
        for (ActionRecord barb : barbs) {
            assertClose(0.196792 * 1.56,
                    barb.action.getDamagePercent(), "C6 Barb multiplier");
            assertEquals(StatType.BASE_HP, barb.action.getScalingStat(),
                    "C6 Barb Max HP scaling");
            assertEquals(StatType.CHARGED_ATTACK_DMG_BONUS,
                    barb.action.getBonusStat(),
                    "C6 Barb receives Charged DMG Bonus");
            assertEquals(ActionType.NORMAL, barb.action.getActionType(),
                    "C6 input remains a Normal trigger");
        }
    }

    private static void testRepeatedRestoreAndStaleWork() {
        Yelan yelan = new Yelan(null, null, 0);
        CombatSimulator simulator = simulatorWith(yelan);
        List<ActionRecord> records = captureYelanActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        resolveNormalProbe(simulator, CharacterId.YELAN, "restore-wave");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        double finish = simulator.getCurrentTime() + 40.0 * FRAME;
        advanceTo(simulator, finish);
        assertEquals(3, named(records, "Exquisite Throw Projectile").size(),
                "original branch resolves one wave");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, finish);
        assertEquals(6, named(records, "Exquisite Throw Projectile").size(),
                "first restore reconstructs one wave");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, finish);
        assertEquals(9, named(records, "Exquisite Throw Projectile").size(),
                "repeated restore does not duplicate work");

        Yelan particleYelan = new Yelan(null, null, 1);
        CombatSimulator particleSim = simulatorWith(particleYelan);
        List<ParticleRecord> particles = captureHydroParticles(particleSim);
        perform(particleSim, CharacterActionKey.SKILL);
        perform(particleSim, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSim.saveSnapshot();
        double particleFinish = particleSim.getCurrentTime() + 3.0;
        advanceTo(particleSim, particleFinish);
        assertEquals(2, particles.size(),
                "both C1 casts retain independent particle work");
        particleSim.restoreSnapshot(particleSnapshot);
        advanceTo(particleSim, particleFinish);
        assertEquals(4, particles.size(),
                "particle work reconstructs once after restore");

        Yelan stale = new Yelan(null, null, 0);
        CombatSimulator staleSim = simulatorWith(stale);
        List<ActionRecord> staleRecords = captureYelanActions(staleSim);
        perform(staleSim, CharacterActionKey.BURST);
        resolveNormalProbe(staleSim, CharacterId.YELAN, "stale-wave");
        stale.restoreCooldowns(
                stale.getLastSkillTime(),
                stale.getLastBurstTime(),
                stale.getSkillCooldownEndTime(),
                staleSim.getCurrentTime(),
                stale.getActiveChargeCooldownDuration(),
                stale.getChargeRestoreTimes());
        stale.restoreCurrentEnergy(70.0);
        perform(staleSim, CharacterActionKey.BURST);
        assertEquals(0,
                named(staleRecords, "Exquisite Throw Projectile").size(),
                "new Burst generation suppresses stale queued wave");
    }

    private static void testInvalidInputsCooldownEnergyAndOwnership() {
        assertThrows(IllegalArgumentException.class,
                () -> new Yelan(null, null, -1),
                "negative constellation rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Yelan(null, null, 7),
                "constellation seven rejected");
        Yelan unsupported = new Yelan(null, null, 0);
        CombatSimulator unsupportedSim = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.CHARGE),
                "unsupported Charged action rejected");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSim, CharacterActionKey.DASH),
                "unsupported Dash rejected");
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSim),
                "null action rejected");

        Yelan insufficient = new Yelan(null, null, 0);
        CombatSimulator insufficientSim = simulatorWith(insufficient);
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSim, CharacterActionKey.BURST);
        assertTrue(!insufficient.isExquisiteThrowActive(73.0 * FRAME),
                "insufficient Energy rejects Burst");

        Yelan cooldown = new Yelan(null, null, 0);
        CombatSimulator cooldownSim = simulatorWith(cooldown);
        perform(cooldownSim, CharacterActionKey.SKILL);
        perform(cooldownSim, CharacterActionKey.SKILL);
        assertClose(33.0 * FRAME + 10.0 + 42.0 * FRAME,
                cooldownSim.getCurrentTime(),
                "C0 second Skill waits for exact cooldown then acts");

        Yelan offField = new Yelan(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator offFieldSim = simulatorWith(offField, ally);
        List<ActionRecord> offFieldRecords = captureYelanActions(offFieldSim);
        perform(offFieldSim, CharacterActionKey.BURST);
        offFieldSim.setActiveCharacter(CharacterId.NOELLE);
        AttackAction zero = normalProbe("zero", 0.0);
        offFieldSim.performActionWithoutTimeAdvance(CharacterId.NOELLE, zero);
        AttackAction wrong = normalProbe("wrong", 1.0);
        wrong.setAnimationDuration(0.0);
        offFieldSim.setActiveCharacter(CharacterId.YELAN);
        offFieldSim.performActionWithoutTimeAdvance(CharacterId.NOELLE, wrong);
        advanceTo(offFieldSim, offFieldSim.getCurrentTime() + 1.0);
        assertEquals(0,
                named(offFieldRecords, "Exquisite Throw Projectile").size(),
                "zero and off-field Normal hits do not trigger waves");

        Yelan reusable = new Yelan(null, null, 0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "cross-simulator Yelan reuse rejected");
        Yelan other = new Yelan(null, null, 0);
        assertTrue(!reusable.acceptsCharacterState(
                other.captureCharacterState()),
                "foreign Yelan snapshot payload rejected");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
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
                CharacterId.YELAN, CharacterActionRequest.of(key));
    }

    private static void resolveNormalProbe(
            CombatSimulator simulator,
            CharacterId actor,
            String name) {
        simulator.performActionWithoutTimeAdvance(
                actor, normalProbe(name, 1.0));
    }

    private static AttackAction normalProbe(String name, double multiplier) {
        AttackAction action = new AttackAction(
                "Yelan wave probe " + name,
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static List<ActionRecord> captureYelanActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YELAN) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureHydroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
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

    private static double applicableStat(
            CombatSimulator simulator,
            Character character,
            StatType stat,
            double time) {
        StatsContainer stats = character.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats.get(stat);
    }

    private static void addBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            Buff buff) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                character.addBuff(buff);
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
            assertTrue(lines.get(index).startsWith("Yelan,"),
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

    private static final class TestBuff extends Buff {
        private final StatType stat;
        private final double amount;

        private TestBuff(
                String name,
                double startTime,
                double duration,
                StatType stat,
                double amount) {
            super(name, duration, startTime);
            this.stat = stat;
            this.amount = amount;
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(stat, amount);
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_HP, 10000.0);
        }

        @Override
        public double getEnergyCost() {
            return 100.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
