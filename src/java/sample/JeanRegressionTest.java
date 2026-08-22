package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.character.Jean;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Jean's stationary offensive slice. */
public final class JeanRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private JeanRegressionTest() {
    }

    /** Runs action, particle, snapshot, constellation, and guard checks. */
    public static void main(String[] args) {
        testIdentityDataAndConstellationConstruction();
        testNormalChargedAndPlungeTiming();
        testSkillSnapshotTimingParticlesAndC5();
        testSkillParticleSnapshotReplay();
        testBurstSnapshotTimingA4AndC3();
        testNormalAttackStepSnapshotReplay();
        testBurstExitSnapshotReplay();
        testC2AttackSpeedAndSnapshotReplay();
        testC4RefreshBoundaryAndSnapshotReplay();
        testCooldownEnergyIsolationAndExcludedInputs();
        System.out.println("JeanRegressionTest passed");
    }

    private static void testIdentityDataAndConstellationConstruction() {
        Jean jean = jeanAtConstellation(6);
        assertEquals(CharacterId.JEAN, jean.getCharacterId(),
                "Jean typed identity");
        assertEquals(CharacterId.JEAN, CharacterId.fromName("Jean"),
                "Jean display-name identity");
        assertEquals(CharacterId.JEAN, CharacterId.fromNumericId(26),
                "Jean numeric identity");
        assertEquals(Element.ANEMO, jean.getElement(), "Jean element");
        assertClose(14695.0,
                jean.getBaseStats().get(StatType.BASE_HP), EPS,
                "Jean base HP");
        assertClose(239.0,
                jean.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Jean base ATK");
        assertClose(769.0,
                jean.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Jean base DEF");
        assertClose(0.2215,
                jean.getBaseStats().get(StatType.HEALING_BONUS), EPS,
                "Jean ascension Healing Bonus metadata");
        assertClose(80.0, jean.getEnergyCost(), EPS,
                "Jean Energy cost");
        assertClose(6.0, jean.getSkillCD(), EPS,
                "Jean Skill cooldown");
        assertClose(20.0, jean.getBurstCD(), EPS,
                "Jean Burst cooldown");

        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(
                    constellation,
                    jeanAtConstellation(constellation).getConstellation(),
                    "Jean explicit constellation C" + constellation);
        }

        TalentDataManager data = TalentDataManager.getInstance();
        assertClose(5.8400,
                data.get("Jean", "Gale Blade C5", -1.0), EPS,
                "Jean C5 multiplier CSV loading");
        assertClose(1.5680,
                data.get("Jean", "Field Border C3", -1.0), EPS,
                "Jean C3 border multiplier CSV loading");
    }

    private static void testNormalChargedAndPlungeTiming() {
        Jean jean = jeanAtConstellation(0);
        CombatSimulator sim = simulatorWith(jean);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Favonius Bladework N");
        double[] multipliers = {
                0.88796, 0.83740, 1.10758, 1.21028, 1.45518
        };
        int[] hitmarks = { 13, 6, 17, 37, 25 };
        int[] durations = { 22, 14, 28, 44, 68 };
        int[] hitlagFrames = { 6, 6, 8, 8, 10 };
        double castTime = 0.0;
        for (int step = 0; step < multipliers.length; step++) {
            perform(sim, CharacterActionKey.NORMAL);
            ActionRecord record = normals.get(step);
            assertClose(multipliers[step],
                    record.action.getDamagePercent(), EPS,
                    "Jean Normal multiplier");
            assertClose(castTime + hitmarks[step] * FRAME,
                    record.time, EPS,
                    "Jean Normal hitmark");
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Jean Normal element");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Jean Normal category");
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Jean Normal standard ICD metadata");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Jean Normal ICD group");
            assertClose(0.0, record.action.getGaugeUnits(), EPS,
                    "Jean Physical Normal gauge");
            castTime += (durations[step] + hitlagFrames[step]) * FRAME;
            assertClose(castTime, sim.getCurrentTime(), EPS,
                    "Jean Normal animation length");
        }
        perform(sim, CharacterActionKey.NORMAL);
        assertTrue(normals.get(5).action.getName().endsWith("N1"),
                "Jean Normal chain wraps after N5");

        Jean chargedJean = jeanAtConstellation(0);
        CombatSimulator chargedSim = simulatorWith(chargedJean);
        List<ActionRecord> charged = captureNamedActions(
                chargedSim, "Favonius Bladework Charged Attack");
        perform(chargedSim, CharacterActionKey.CHARGE);
        assertEquals(1, charged.size(), "Jean Charged hit count");
        assertClose(2.97672,
                charged.get(0).action.getDamagePercent(), EPS,
                "Jean Charged multiplier");
        assertClose(36.0 * FRAME, charged.get(0).time, EPS,
                "Jean Charged hitmark");
        assertClose(57.0 * FRAME,
                chargedSim.getCurrentTime(), EPS,
                "Jean Charged animation length");
        assertEquals(ICDType.Standard,
                charged.get(0).action.getICDType(),
                "Jean Charged standard ICD metadata");
        assertEquals(ICDTag.NormalAttack,
                charged.get(0).action.getICDTag(),
                "Jean Charged shares Normal ICD group");

        Jean plungeJean = jeanAtConstellation(0);
        CombatSimulator plungeSim = simulatorWith(plungeJean);
        List<ActionRecord> plunges = captureNamedActions(
                plungeSim, "Favonius Bladework High Plunge");
        perform(plungeSim, CharacterActionKey.PLUNGE);
        assertClose(2.933586,
                plunges.get(0).action.getDamagePercent(), EPS,
                "Jean high Plunge multiplier");
        assertClose(43.0 * FRAME, plunges.get(0).time, EPS,
                "Jean high Plunge hitmark");
        assertClose(80.0 * FRAME,
                plungeSim.getCurrentTime(), EPS,
                "Jean high Plunge animation length");
        assertEquals(ICDType.None,
                plunges.get(0).action.getICDType(),
                "Jean Plunge no-ICD metadata");
        assertTrue(plunges.get(0).action.isShatterTrigger(),
                "Jean high Plunge is blunt");
    }

    private static void testSkillSnapshotTimingParticlesAndC5() {
        Jean c0 = jeanAtConstellation(0);
        c0.addBuff(new SimpleBuff(
                "Jean cast-only Skill ATK",
                20.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Hits = captureNamedActions(
                c0Sim, "Gale Blade");
        perform(c0Sim, CharacterActionKey.SKILL);
        assertEquals(1, c0Hits.size(), "Jean Skill hit count");
        AttackAction skill = c0Hits.get(0).action;
        assertClose(21.0 * FRAME, c0Hits.get(0).time, EPS,
                "Jean Skill hitmark");
        assertClose(46.0 * FRAME, c0Sim.getCurrentTime(), EPS,
                "Jean Skill animation length");
        assertClose(19.0 * FRAME, c0.getLastSkillTime(), EPS,
                "Jean Skill cooldown start");
        assertClose(4.9640, skill.getDamagePercent(), EPS,
                "Jean C0 Skill multiplier");
        assertEquals(Element.ANEMO, skill.getElement(),
                "Jean Skill element");
        assertEquals(ActionType.SKILL, skill.getActionType(),
                "Jean Skill category");
        assertEquals(ICDType.None, skill.getICDType(),
                "Jean Skill has no ICD");
        assertEquals(ICDTag.ElementalSkill, skill.getICDTag(),
                "Jean Skill ICD group");
        assertClose(2.0, skill.getGaugeUnits(), EPS,
                "Jean Skill applies 2U");
        assertTrue(skill.hasStatSnapshot(),
                "Jean Skill owns a cast-time snapshot");
        assertClose(1.0,
                skill.getStatSnapshot().get(StatType.ATK_PERCENT), EPS,
                "Jean Skill retains the pre-impact ATK buff");
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Jean Skill particles remain in flight");
        advanceTo(c0Sim, 121.0 * FRAME - 0.001);
        assertClose(0.0, c0.getTotalParticleEnergy(), EPS,
                "Jean particles wait for 100-frame travel");
        advanceTo(c0Sim, 121.0 * FRAME);
        assertClose(8.01, c0.getTotalParticleEnergy(), EPS,
                "Jean receives expected 2.67 same-element particles");

        Jean c5 = jeanAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Hits = captureNamedActions(
                c5Sim, "Gale Blade");
        perform(c5Sim, CharacterActionKey.SKILL);
        assertClose(5.8400,
                c5Hits.get(0).action.getDamagePercent(), EPS,
                "Jean C5 Skill multiplier");

        Jean noTarget = jeanAtConstellation(0);
        CombatSimulator noTargetSim = simulatorWithoutEnemy(noTarget);
        List<ActionRecord> noTargetHits = captureNamedActions(
                noTargetSim, "Gale Blade");
        perform(noTargetSim, CharacterActionKey.SKILL);
        advanceTo(noTargetSim, 200.0 * FRAME);
        assertEquals(0, noTargetHits.size(),
                "Jean targetless Skill has no enemy hit");
        assertClose(0.0, noTarget.getTotalParticleEnergy(), EPS,
                "Jean targetless Skill grants no particles");
    }

    private static void testBurstSnapshotTimingA4AndC3() {
        Jean c0 = jeanAtConstellation(0);
        c0.addBuff(new SimpleBuff(
                "Jean cast-only Burst ATK",
                30.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator c0Sim = simulatorWith(c0);
        List<ActionRecord> c0Hits = captureNamedActions(
                c0Sim, "Dandelion Breeze");
        perform(c0Sim, CharacterActionKey.BURST);
        assertEquals(2, c0Hits.size(),
                "Jean Burst entry and initial hits resolve during cast");
        assertClose(40.0 * FRAME, c0Hits.get(0).time, EPS,
                "Jean Burst first stationary border hit");
        assertClose(55.0 * FRAME, c0Hits.get(1).time, EPS,
                "Jean Burst initial hitmark");
        assertClose(1.3328,
                c0Hits.get(0).action.getDamagePercent(), EPS,
                "Jean C0 border multiplier");
        assertClose(7.2216,
                c0Hits.get(1).action.getDamagePercent(), EPS,
                "Jean C0 initial multiplier");
        for (ActionRecord record : c0Hits) {
            assertBurstHit(record.action, 1.0);
        }
        assertClose(90.0 * FRAME, c0Sim.getCurrentTime(), EPS,
                "Jean Burst animation length");
        assertClose(38.0 * FRAME, c0.getLastBurstTime(), EPS,
                "Jean Burst cooldown start");
        assertClose(16.0, c0.getCurrentEnergy(), EPS,
                "Jean A4 refunds 20 percent Energy");
        assertClose(16.0, c0.getTotalFlatEnergy(), EPS,
                "Jean A4 refund is flat Energy");
        advanceTo(c0Sim, 640.0 * FRAME);
        assertEquals(3, c0Hits.size(),
                "Jean stationary Burst resolves the exit border hit");
        assertClose(640.0 * FRAME, c0Hits.get(2).time, EPS,
                "Jean Burst final stationary border hit");
        assertClose(1.3328,
                c0Hits.get(2).action.getDamagePercent(), EPS,
                "Jean final border multiplier");
        assertBurstHit(c0Hits.get(2).action, 1.0);

        Jean c3 = jeanAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Hits = captureNamedActions(
                c3Sim, "Dandelion Breeze");
        perform(c3Sim, CharacterActionKey.BURST);
        assertClose(1.5680,
                c3Hits.get(0).action.getDamagePercent(), EPS,
                "Jean C3 border multiplier");
        assertClose(8.4960,
                c3Hits.get(1).action.getDamagePercent(), EPS,
                "Jean C3 initial multiplier");
    }

    private static void testSkillParticleSnapshotReplay() {
        Jean jean = jeanAtConstellation(0);
        CombatSimulator sim = simulatorWith(jean);
        List<Double> particles = new ArrayList<>();
        sim.addParticleListener((element, count, time) -> particles.add(time));
        perform(sim, CharacterActionKey.SKILL);
        jean.resetSkillCooldown(sim.getCurrentTime());
        perform(sim, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        advanceTo(sim, 167.0 * FRAME);
        assertEquals(2, particles.size(),
                "Jean original branch resolves two particle packets");

        sim.restoreSnapshot(snapshot);
        particles.clear();
        advanceTo(sim, 167.0 * FRAME);
        assertEquals(2, particles.size(),
                "Jean restore replays two particle packets");

        sim.restoreSnapshot(snapshot);
        sim.restoreSnapshot(snapshot);
        particles.clear();
        advanceTo(sim, 167.0 * FRAME);
        assertEquals(2, particles.size(),
                "Jean repeated restore keeps two particle packets");

        Jean exactJean = jeanAtConstellation(0);
        CombatSimulator exactSim = simulatorWith(exactJean);
        List<Double> exactParticles = new ArrayList<>();
        exactSim.addParticleListener((element, count, time) ->
                exactParticles.add(time));
        perform(exactSim, CharacterActionKey.SKILL);
        SnapshotAwareCharacterEffect.State pendingState =
                exactJean.captureCharacterState();
        advanceTo(exactSim, 121.0 * FRAME);
        assertEquals(1, exactParticles.size(),
                "Jean exact-deadline original particle resolves");

        SimulatorSnapshot[] exactSnapshot = captureSnapshotAt(
                exactSim, exactSim.getCurrentTime());
        exactJean.restoreCharacterState(pendingState, exactSim);
        exactParticles.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactParticles.size(),
                "Jean same-time setup resolves one particle");

        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactParticles.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactParticles.size(),
                "Jean exact-deadline restore replays particle");

        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactSim.restoreSnapshot(exactSnapshot[0]);
        exactParticles.clear();
        exactSim.advanceTime(0.0);
        assertEquals(1, exactParticles.size(),
                "Jean repeated exact-deadline restore keeps one particle");
    }

    private static void testNormalAttackStepSnapshotReplay() {
        Jean jean = jeanAtConstellation(0);
        CombatSimulator sim = simulatorWith(jean);
        List<ActionRecord> normals = captureNamedActions(
                sim, "Favonius Bladework N");
        perform(sim, CharacterActionKey.NORMAL);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Favonius Bladework N2",
                normals.get(1).action.getName(),
                "Jean branched Normal continues with N2");

        sim.restoreSnapshot(snapshot);
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Favonius Bladework N2",
                normals.get(2).action.getName(),
                "Jean restored Normal continues with N2");
        assertClose(normals.get(1).time, normals.get(2).time, EPS,
                "Jean restored N2 retains its hit time");
    }

    private static void testBurstExitSnapshotReplay() {
        Jean jean = jeanAtConstellation(0);
        jean.addBuff(new SimpleBuff(
                "Jean replay-only Burst ATK",
                30.0 * FRAME,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 0.75)));
        CombatSimulator sim = simulatorWith(jean);
        List<ActionRecord> exits = captureNamedActions(
                sim, "Dandelion Breeze Field Exit");
        perform(sim, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        SnapshotAwareCharacterEffect.State pendingState =
                jean.captureCharacterState();

        advanceTo(sim, 640.0 * FRAME);
        assertEquals(1, exits.size(),
                "Jean original Burst exit resolves once");
        ActionRecord original = exits.get(0);
        assertClose(640.0 * FRAME, original.time, EPS,
                "Jean original Burst exit time");
        assertClose(1.3328, original.action.getDamagePercent(), EPS,
                "Jean original Burst exit multiplier");
        assertBurstHit(original.action, 0.75);

        sim.restoreSnapshot(snapshot);
        advanceTo(sim, 640.0 * FRAME);
        assertEquals(2, exits.size(),
                "Jean restored Burst exit resolves once");
        assertSameExit(original, exits.get(1),
                "Jean restored Burst exit");

        sim.restoreSnapshot(snapshot);
        sim.restoreSnapshot(snapshot);
        advanceTo(sim, 640.0 * FRAME);
        assertEquals(3, exits.size(),
                "Jean repeated restore leaves one Burst exit");
        assertSameExit(original, exits.get(2),
                "Jean repeatedly restored Burst exit");

        SimulatorSnapshot[] exactSnapshot = captureSnapshotAt(
                sim, sim.getCurrentTime());
        jean.restoreCharacterState(pendingState, sim);
        exits.clear();
        sim.advanceTime(0.0);
        assertEquals(1, exits.size(),
                "Jean same-time setup resolves one Burst exit");

        sim.restoreSnapshot(exactSnapshot[0]);
        exits.clear();
        sim.advanceTime(0.0);
        assertEquals(1, exits.size(),
                "Jean exact-deadline restore replays Burst exit");

        sim.restoreSnapshot(exactSnapshot[0]);
        sim.restoreSnapshot(exactSnapshot[0]);
        exits.clear();
        sim.advanceTime(0.0);
        assertEquals(1, exits.size(),
                "Jean repeated exact-deadline restore keeps one Burst exit");
    }

    private static void testC2AttackSpeedAndSnapshotReplay() {
        Jean c2 = jeanAtConstellation(2);
        TestCharacter ally = new TestCharacter();
        CombatSimulator sim = simulatorWith(c2);
        sim.addCharacter(ally);
        perform(sim, CharacterActionKey.SKILL);
        advanceTo(sim, 121.0 * FRAME);
        assertClose(0.15,
                applicableStats(sim, c2).get(StatType.ATK_SPD), EPS,
                "Jean C2 buffs Jean after active particle pickup");
        assertClose(0.15,
                applicableStats(sim, ally).get(StatType.ATK_SPD), EPS,
                "Jean C2 buffs the party");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        double castTime = sim.getCurrentTime();
        List<ActionRecord> normals = captureNamedActions(
                sim, "Favonius Bladework N1");
        perform(sim, CharacterActionKey.NORMAL);
        assertClose(castTime + 13.0 * FRAME / 1.15,
                normals.get(0).time, EPS,
                "Jean C2 scales Normal hitmark");
        assertClose(castTime + 22.0 * FRAME / 1.15 + 6.0 * FRAME,
                sim.getCurrentTime(), EPS,
                "Jean C2 scales Normal animation");
        advanceTo(sim, 121.0 * FRAME + 15.0);
        assertClose(0.0,
                applicableStats(sim, c2).get(StatType.ATK_SPD), EPS,
                "Jean C2 expires on its half-open boundary");
        sim.restoreSnapshot(snapshot);
        assertClose(121.0 * FRAME, sim.getCurrentTime(), EPS,
                "Jean C2 snapshot restores clock");
        assertClose(0.15,
                applicableStats(sim, c2).get(StatType.ATK_SPD), EPS,
                "Jean C2 marker survives snapshot replay");

        Jean offFieldC2 = jeanAtConstellation(2);
        TestCharacter offFieldAlly = new TestCharacter();
        CombatSimulator offFieldSim = simulatorWith(offFieldC2);
        offFieldSim.addCharacter(offFieldAlly);
        perform(offFieldSim, CharacterActionKey.SKILL);
        offFieldSim.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(offFieldSim, 121.0 * FRAME);
        assertClose(0.0,
                applicableStats(offFieldSim, offFieldAlly).get(
                        StatType.ATK_SPD),
                EPS, "Jean C2 requires Jean to receive particles on-field");
    }

    private static void testC4RefreshBoundaryAndSnapshotReplay() {
        Jean c4 = jeanAtConstellation(4);
        TestCharacter ally = new TestCharacter();
        CombatSimulator sim = simulatorWith(c4);
        sim.addCharacter(ally);
        perform(sim, CharacterActionKey.BURST);
        assertClose(0.40,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 shreds Anemo RES in the stationary field");
        assertClose(0.40,
                applicableStats(sim, ally).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 is party-visible enemy resistance state");

        SimulatorSnapshot snapshot = sim.saveSnapshot();
        advanceTo(sim, 712.0 * FRAME - 0.001);
        assertClose(0.40,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 final frame-640 refresh remains active");
        advanceTo(sim, 712.0 * FRAME);
        assertClose(0.0,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 expires at the exact half-open frame 712");

        sim.restoreSnapshot(snapshot);
        assertClose(90.0 * FRAME, sim.getCurrentTime(), EPS,
                "Jean C4 snapshot restores clock");
        assertClose(0.40,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 marker survives snapshot replay");
        advanceTo(sim, 712.0 * FRAME - 0.001);
        assertClose(0.40,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 replay preserves the final refresh window");
        advanceTo(sim, 712.0 * FRAME);
        assertClose(0.0,
                applicableStats(sim, c4).get(StatType.ANEMO_RES_SHRED),
                EPS, "Jean C4 replay preserves the expiration boundary");
    }

    private static void testCooldownEnergyIsolationAndExcludedInputs() {
        Jean empty = jeanAtConstellation(0);
        CombatSimulator emptySim = simulatorWith(empty);
        empty.restoreCurrentEnergy(0.0);
        List<ActionRecord> emptyBurst = captureNamedActions(
                emptySim, "Dandelion Breeze");
        perform(emptySim, CharacterActionKey.BURST);
        assertEquals(0, emptyBurst.size(),
                "Jean cannot Burst without Energy");
        assertClose(0.0, emptySim.getCurrentTime(), EPS,
                "Rejected Jean Burst consumes no time");
        assertClose(80.0, empty.getMissedBurstCost(), EPS,
                "Rejected Jean Burst records missing Energy");

        Jean cooldownJean = jeanAtConstellation(0);
        CombatSimulator cooldownSim = simulatorWith(cooldownJean);
        List<ActionRecord> skills = captureNamedActions(
                cooldownSim, "Gale Blade");
        perform(cooldownSim, CharacterActionKey.SKILL);
        perform(cooldownSim, CharacterActionKey.SKILL);
        assertEquals(2, skills.size(),
                "Jean repeated Skill resolves after cooldown wait");
        assertClose(400.0 * FRAME, skills.get(1).time, EPS,
                "Jean second Skill hit follows frame-19 cooldown start");
        assertClose(425.0 * FRAME,
                cooldownSim.getCurrentTime(), EPS,
                "Jean second Skill animation completes after cooldown");

        Jean first = jeanAtConstellation(0);
        Jean second = jeanAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        perform(firstSim, CharacterActionKey.SKILL);
        assertClose(19.0 * FRAME, first.getLastSkillTime(), EPS,
                "Jean first instance records cooldown");
        assertTrue(second.canSkill(secondSim.getCurrentTime()),
                "Jean simulator instances isolate cooldown readiness");
        assertClose(0.0, secondSim.getCurrentTime(), EPS,
                "Jean second simulator clock remains isolated");

        Jean c1 = jeanAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Skill = captureNamedActions(
                c1Sim, "Gale Blade");
        perform(c1Sim, CharacterActionKey.SKILL);
        assertClose(4.9640,
                c1Skill.get(0).action.getDamagePercent(), EPS,
                "Jean C1 held pull is excluded from tap Skill");

        assertThrows(IllegalArgumentException.class,
                () -> jeanAtConstellation(-1),
                "Jean rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> jeanAtConstellation(7),
                "Jean rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> perform(c1Sim, CharacterActionKey.DASH),
                "Jean rejects unrepresented stamina action");

        Jean reused = jeanAtConstellation(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Jean rejects cross-simulator reuse");
    }

    private static void assertBurstHit(
            AttackAction action,
            double expectedAttackPercent) {
        assertEquals(Element.ANEMO, action.getElement(),
                "Jean Burst element");
        assertEquals(ActionType.BURST, action.getActionType(),
                "Jean Burst category");
        assertEquals(ICDType.None, action.getICDType(),
                "Jean Burst has no ICD");
        assertEquals(ICDTag.ElementalBurst, action.getICDTag(),
                "Jean Burst ICD group");
        assertClose(2.0, action.getGaugeUnits(), EPS,
                "Jean Burst applies 2U");
        assertTrue(action.hasStatSnapshot(),
                "Jean Burst hit owns a cast-time snapshot");
        assertClose(expectedAttackPercent,
                action.getStatSnapshot().get(StatType.ATK_PERCENT), EPS,
                "Jean Burst hit retains its cast-time ATK buff");
    }

    private static void assertSameExit(
            ActionRecord expected,
            ActionRecord actual,
            String message) {
        assertClose(expected.time, actual.time, EPS,
                message + " time");
        assertClose(expected.damage, actual.damage, EPS,
                message + " damage");
        assertClose(expected.action.getDamagePercent(),
                actual.action.getDamagePercent(), EPS,
                message + " multiplier");
        assertClose(expected.action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                actual.action.getStatSnapshot().get(
                        StatType.ATK_PERCENT),
                EPS, message + " cast snapshot");
    }

    private static Jean jeanAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                defaultValue;
        return new Jean(null, null, talentData, constellation);
    }

    private static CombatSimulator simulatorWith(Jean jean) {
        CombatSimulator sim = simulatorWithoutEnemy(jean);
        sim.setEnemy(new Enemy(90));
        return sim;
    }

    private static CombatSimulator simulatorWithoutEnemy(Jean jean) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.addCharacter(jean);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey actionKey) {
        sim.performAction(
                CharacterId.JEAN,
                CharacterActionRequest.of(actionKey));
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String actionNamePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.JEAN
                    && action.getName().startsWith(actionNamePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static SimulatorSnapshot[] captureSnapshotAt(
            CombatSimulator sim,
            double time) {
        SimulatorSnapshot[] snapshot = new SimulatorSnapshot[1];
        sim.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                snapshot[0] = activeSim.saveSnapshot();
            }
        });
        return snapshot;
    }

    private static StatsContainer applicableStats(
            CombatSimulator sim,
            Character character) {
        double currentTime = sim.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : sim.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static void advanceTo(CombatSimulator sim, double targetTime) {
        sim.advanceTime(targetTime - sim.getCurrentTime());
    }

    private static void assertClose(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
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
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
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

    /** Minimal party member used for team-buff and field-state checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            this.name = "Noelle";
            this.characterId = CharacterId.NOELLE;
            this.element = Element.GEO;
            baseStats.set(StatType.BASE_HP, 1.0);
            baseStats.set(StatType.BASE_ATK, 1.0);
            baseStats.set(StatType.BASE_DEF, 1.0);
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
