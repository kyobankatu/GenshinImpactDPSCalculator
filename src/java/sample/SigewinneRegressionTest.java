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
import model.character.Sigewinne;
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

/** Focused regression checks for Sigewinne's fixed-target offensive slice. */
public final class SigewinneRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private SigewinneRegressionTest() {
    }

    /** Runs data, action, passive, constellation, exclusion, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalNormalsAndUnsupportedBasics();
        testPressSkillCadenceParticlesAndIcd();
        testHoldSkillAndSkillConstellations();
        testAppropriateRestAndC2();
        testBurstPulsesAndConstellations();
        testFailClosedScopeAndIsolation();
        testSnapshotRestore();
        System.out.println("SigewinneRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Sigewinne sigewinne = new Sigewinne(null, null, 6);
        assertEquals(CharacterId.SIGEWINNE,
                sigewinne.getCharacterId(),
                "Sigewinne typed identity");
        assertEquals(CharacterId.SIGEWINNE,
                CharacterId.fromName("Sigewinne"),
                "Sigewinne name lookup");
        assertEquals(CharacterId.SIGEWINNE,
                CharacterId.fromNumericId(84),
                "Sigewinne numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.SIGEWINNE.getRegion(),
                "Sigewinne region");
        assertEquals(Element.HYDRO, sigewinne.getElement(),
                "Sigewinne element");
        assertClose(13348.0,
                sigewinne.getBaseStats().get(StatType.BASE_HP),
                "Sigewinne base HP");
        assertClose(193.0,
                sigewinne.getBaseStats().get(StatType.BASE_ATK),
                "Sigewinne base ATK");
        assertClose(500.0,
                sigewinne.getBaseStats().get(StatType.BASE_DEF),
                "Sigewinne base DEF");
        assertClose(0.288,
                sigewinne.getBaseStats().get(StatType.HP_PERCENT),
                "Sigewinne ascension HP");
        assertClose(70.0, sigewinne.getEnergyCost(),
                "Sigewinne Energy cost");
        assertClose(18.0, sigewinne.getSkillCD(),
                "Sigewinne Skill cooldown");
        assertClose(18.0, sigewinne.getBurstCD(),
                "Sigewinne Burst cooldown");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.SIGEWINNE,
                    new Sigewinne(null, null, constellation)
                            .getCharacterId(),
                    "Sigewinne explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Sigewinne/Sigewinne_Status.csv"),
                12);
        assertCsvShape(Path.of(
                "config/characters/Sigewinne/Sigewinne_Multipliers.csv"),
                24);
        assertCsvValue("Bubblebalm C3", 0.0456);
        assertCsvValue("Super Saturated Syringing C5", 0.235416);
        assertCsvValue("C2 Hydro RES Shred", 0.35);
    }

    private static void testPhysicalNormalsAndUnsupportedBasics() {
        Sigewinne sigewinne = new Sigewinne(null, null, 0);
        CombatSimulator simulator = simulatorWith(sigewinne);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = { 0.966628, 0.938283, 1.438369 };
        int[] hitFrames = { 12, 14, 38 };
        int[] durations = { 20, 36, 82 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord normal = named(records,
                    "Targeted Treatment N" + (step + 1)).get(0);
            assertClose(castTime + hitFrames[step] * FRAME,
                    normal.time,
                    "Sigewinne N" + (step + 1) + " hitmark");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Sigewinne N" + (step + 1) + " recovery");
            assertClose(multipliers[step],
                    normal.action.getDamagePercent(),
                    "Sigewinne N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL,
                    normal.action.getElement(),
                    "Sigewinne Normal element");
            assertEquals(ActionType.NORMAL,
                    normal.action.getActionType(),
                    "Sigewinne Normal action type");
            assertClose(0.0, normal.action.getGaugeUnits(),
                    "Sigewinne Physical Normal gauge");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Targeted Treatment N1").size(),
                "Sigewinne Normal string wraps after N3");

        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Sigewinne rejects aimed/Charged attacks");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Sigewinne rejects Plunge attacks");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Sigewinne rejects movement actions");
    }

    private static void testPressSkillCadenceParticlesAndIcd() {
        Sigewinne sigewinne = new Sigewinne(null, null, 0);
        CombatSimulator simulator = simulatorWith(sigewinne);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        performSkill(simulator, SkillActionMode.PRESS);
        assertClose(41.0 * FRAME, simulator.getCurrentTime(),
                "Sigewinne Press recovery");
        assertClose(18.0 - 25.0 * FRAME,
                sigewinne.getSkillCDRemaining(
                        simulator.getCurrentTime()),
                "Press cooldown starts at frame 16");
        advanceTo(simulator, (35.0 + 4.0 * 107.0) * FRAME
                + EPSILON);

        List<ActionRecord> bubbles = prefixed(records,
                "Rebound Hydrotherapy Bubble ");
        assertEquals(5, bubbles.size(),
                "C0 Press produces five fixed-target bounces");
        for (int index = 0; index < bubbles.size(); index++) {
            ActionRecord bubble = bubbles.get(index);
            assertClose((35.0 + index * 107.0) * FRAME,
                    bubble.time,
                    "Press bubble " + (index + 1) + " cadence");
            assertClose(0.03876,
                    bubble.action.getDamagePercent(),
                    "C0 Bubblebalm multiplier");
            assertEquals(StatType.BASE_HP,
                    bubble.action.getScalingStat(),
                    "Bubblebalm scales with Max HP");
            assertEquals(ICDType.SigewinneBubblebalm,
                    bubble.action.getICDType(),
                    "Bubblebalm private two-second ICD");
            assertEquals(ICDTag.Sigewinne_Bubblebalm,
                    bubble.action.getICDTag(),
                    "Bubblebalm private ICD tag");
            assertClose(0.0,
                    bubble.action.getStatSnapshot().get(
                            StatType.HYDRO_DMG_BONUS),
                    "Bubble snapshot precedes A1 activation");
        }

        List<ActionRecord> blades = named(records,
                "Rebound Hydrotherapy: Surging Blade");
        assertEquals(1, blades.size(),
                "C0 sequence calls one Surging Blade");
        assertClose(75.0 * FRAME, blades.get(0).time,
                "Surging Blade lands 40 frames after first bounce");
        assertClose(0.011628,
                blades.get(0).action.getDamagePercent(),
                "C0 Surging Blade multiplier");
        assertClose(0.0, blades.get(0).action.getGaugeUnits(),
                "Surging Blade applies zero gauge");
        assertClose(0.08,
                blades.get(0).action.getStatSnapshot().get(
                        StatType.HYDRO_DMG_BONUS),
                "Surging Blade uses live A1 Hydro bonus");
        assertEquals(1, particles.size(),
                "First accepted bubble emits one particle packet");
        assertClose(4.0, particles.get(0).count,
                "Bubble packet contains four Hydro particles");
        assertClose(135.0 * FRAME, particles.get(0).time,
                "Particles use 100-frame travel from first bounce");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Bubblebalm,
                        ICDType.SigewinneBubblebalm,
                        0.0),
                "Bubble ICD admits first hit");
        assertTrue(!icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Bubblebalm,
                        ICDType.SigewinneBubblebalm,
                        2.0 - 2.0 * EPSILON),
                "Bubble ICD blocks before two seconds");
        assertTrue(icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Bubblebalm,
                        ICDType.SigewinneBubblebalm,
                        2.0),
                "Bubble ICD opens at two seconds");
    }

    private static void testHoldSkillAndSkillConstellations() {
        Sigewinne c1 = new Sigewinne(null, null, 1);
        CombatSimulator simulator = simulatorWith(c1);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator, SkillActionMode.HOLD);
        assertClose(89.0 * FRAME, simulator.getCurrentTime(),
                "Sigewinne Hold recovery");
        assertClose(18.0 - 23.0 * FRAME,
                c1.getSkillCDRemaining(simulator.getCurrentTime()),
                "Hold cooldown starts at frame 66");
        advanceTo(simulator, (90.0 + 7.0 * 107.0) * FRAME
                + EPSILON);
        List<ActionRecord> bubbles = prefixed(records,
                "Rebound Hydrotherapy Bubble ");
        assertEquals(8, bubbles.size(),
                "C1 adds three fixed-target bounces");
        double[] tierBonuses = {
            0.10, 0.10, 0.10, 0.10, 0.05, 0.0, 0.0, 0.0
        };
        for (int index = 0; index < bubbles.size(); index++) {
            assertClose(tierBonuses[index],
                    bubbles.get(index).action.getExtraBonuses()
                            .getOrDefault(StatType.DMG_BONUS_ALL, 0.0),
                    "C1 Hold tier bonus at bounce " + (index + 1));
        }
        assertEquals(18,
                c1.getConvalescenceStacks(
                        simulator.getCurrentTime()),
                "C1 adds one Convalescence stack per bounce");
        assertEquals(2, named(records,
                "Rebound Hydrotherapy: Surging Blade").size(),
                "C1 duration crosses the ten-second Surging gate");

        Sigewinne c3 = new Sigewinne(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        performSkill(c3Simulator, SkillActionMode.PRESS);
        assertClose(0.0456,
                named(c3Records, "Rebound Hydrotherapy Bubble 1")
                        .get(0).action.getDamagePercent(),
                "C3 raises Bubblebalm to Talent 12");
        advanceTo(c3Simulator, 75.0 * FRAME + EPSILON);
        assertClose(0.01368,
                named(c3Records,
                        "Rebound Hydrotherapy: Surging Blade")
                        .get(0).action.getDamagePercent(),
                "C3 raises Surging Blade to Talent 12");

        Sigewinne replacement = new Sigewinne(null, null, 0);
        replacement.setSkillCD(1.0);
        CombatSimulator replacementSimulator = simulatorWith(replacement);
        List<ActionRecord> replacementRecords = captureActions(
                replacementSimulator);
        performSkill(replacementSimulator, SkillActionMode.PRESS);
        performSkill(replacementSimulator, SkillActionMode.PRESS);
        advanceTo(replacementSimulator, 10.0);
        assertEquals(6, prefixed(replacementRecords,
                "Rebound Hydrotherapy Bubble ").size(),
                "Replacement Skill cancels four future old bounces");
        assertEquals(1, named(replacementRecords,
                "Rebound Hydrotherapy: Surging Blade").size(),
                "Canceled bounces reserve no future Surging Blade");
    }

    private static void testAppropriateRestAndC2() {
        Sigewinne c0 = new Sigewinne(null, null, 0);
        addMaxHp(c0, 40000.0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c0, ally);
        performSkill(simulator, SkillActionMode.PRESS);
        assertEquals(10,
                c0.getConvalescenceStacks(
                        simulator.getCurrentTime()),
                "A1 starts with ten Convalescence stacks");
        assertClose(18.0, c0.getConvalescenceExpirationTime(),
                "A1 has an exact 18-second duration");
        StatsContainer bonus = new StatsContainer();
        AttackAction skill = testSkillAction();
        c0.applyTargetDependentTeamStats(
                bonus,
                ally,
                simulator.getEnemy(),
                skill,
                simulator.getCurrentTime());
        assertClose(800.0,
                bonus.get(StatType.FLAT_DMG_BONUS),
                "A1 adds 80 damage per 1000 HP above 30000");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, skill);
        assertEquals(9,
                c0.getConvalescenceStacks(
                        simulator.getCurrentTime()),
                "Positive off-field ally Skill consumes one stack");

        AttackAction normal = new AttackAction(
                "Convalescence Normal rejection",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
        normal.setICD(ICDType.None, ICDTag.NormalAttack, 0.0);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, normal);
        assertEquals(9,
                c0.getConvalescenceStacks(
                        simulator.getCurrentTime()),
                "Off-field non-Skill damage consumes no stack");
        simulator.switchCharacter(CharacterId.NOELLE);
        StatsContainer activeBonus = new StatsContainer();
        c0.applyTargetDependentTeamStats(
                activeBonus,
                ally,
                simulator.getEnemy(),
                skill,
                simulator.getCurrentTime());
        assertClose(0.0,
                activeBonus.get(StatType.FLAT_DMG_BONUS),
                "A1 excludes the active ally");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.NOELLE, testSkillAction());
        assertEquals(9,
                c0.getConvalescenceStacks(
                        simulator.getCurrentTime()),
                "Active ally Skill consumes no stack");
        advanceTo(simulator, 18.0);
        assertEquals(0, c0.getConvalescenceStacks(18.0),
                "A1 expires at the exact 18-second boundary");

        Sigewinne c1 = new Sigewinne(null, null, 1);
        addMaxHp(c1, 100000.0);
        CombatSimulator c1Simulator = simulatorWith(
                c1,
                new TestCharacter(CharacterId.NOELLE, Element.GEO));
        performSkill(c1Simulator, SkillActionMode.PRESS);
        StatsContainer capped = new StatsContainer();
        c1.applyTargetDependentTeamStats(
                capped,
                c1Simulator.getCharacter(CharacterId.NOELLE),
                c1Simulator.getEnemy(),
                testSkillAction(),
                c1Simulator.getCurrentTime());
        assertClose(3500.0,
                capped.get(StatType.FLAT_DMG_BONUS),
                "C1 uses the 3500 flat-damage cap");

        Sigewinne c2 = new Sigewinne(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        performSkill(c2Simulator, SkillActionMode.PRESS);
        Buff shred = typedBuff(
                c2Simulator,
                BuffId.SIGEWINNE_C2_HYDRO_RES_SHRED);
        StatsContainer shredStats = new StatsContainer();
        shred.apply(shredStats, c2Simulator.getCurrentTime());
        assertClose(0.35,
                shredStats.get(StatType.HYDRO_RES_SHRED),
                "C2 applies typed 35 percent Hydro resistance shred");
        assertClose(35.0 * FRAME + 8.0,
                shred.getExpirationTime(),
                "C2 shred starts after the first accepted bubble hit");
    }

    private static void testBurstPulsesAndConstellations() {
        Sigewinne c0 = new Sigewinne(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(241.0 * FRAME, simulator.getCurrentTime(),
                "C0 Burst full animation duration");
        List<ActionRecord> pulses = prefixed(records,
                "Super Saturated Syringing Pulse ");
        assertEquals(6, pulses.size(),
                "C0 Burst emits six fixed-target pulses");
        for (int index = 0; index < pulses.size(); index++) {
            ActionRecord pulse = pulses.get(index);
            assertClose((99.0 + index * 25.0) * FRAME,
                    pulse.time,
                    "C0 Burst pulse " + (index + 1) + " timing");
            assertClose(0.200104,
                    pulse.action.getDamagePercent(),
                    "C0 Burst Talent 9 multiplier");
            assertEquals(StatType.BASE_HP,
                    pulse.action.getScalingStat(),
                    "Burst scales with Max HP");
            assertEquals(ICDType.SigewinneBurst,
                    pulse.action.getICDType(),
                    "Burst uses private 1.9-second ICD");
            assertEquals(ICDTag.Sigewinne_Burst,
                    pulse.action.getICDTag(),
                    "Burst uses private ICD tag");
        }
        assertClose(0.0, c0.getCurrentEnergy(),
                "Burst spends 70 Energy at frame 5");
        assertClose(14.0,
                c0.getBurstCDRemaining(simulator.getCurrentTime()),
                "Burst cooldown starts at frame 1");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Burst,
                        ICDType.SigewinneBurst,
                        0.0),
                "Burst ICD admits first pulse");
        assertTrue(!icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Burst,
                        ICDType.SigewinneBurst,
                        1.9 - 2.0 * EPSILON),
                "Burst ICD blocks before 1.9 seconds");
        assertTrue(icd.checkApplication(
                        "Sigewinne",
                        ICDTag.Sigewinne_Burst,
                        ICDType.SigewinneBurst,
                        1.9),
                "Burst ICD opens at 1.9 seconds");

        Sigewinne c5 = new Sigewinne(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(425.0 * FRAME,
                c5Simulator.getCurrentTime(),
                "C4 extends Burst animation by three seconds");
        List<ActionRecord> c5Pulses = prefixed(c5Records,
                "Super Saturated Syringing Pulse ");
        assertEquals(14, c5Pulses.size(),
                "C4 extended Burst emits fourteen pulses");
        assertClose(0.235416,
                c5Pulses.get(0).action.getDamagePercent(),
                "C5 raises Burst to Talent 12");
    }

    private static void testFailClosedScopeAndIsolation() {
        Sigewinne sigewinne = new Sigewinne(null, null, 6);
        CombatSimulator simulator = simulatorWith(sigewinne);
        assertTrue(!sigewinne.isHealingRepresented(),
                "Healing is explicitly excluded");
        assertTrue(!sigewinne.isPlayerHpRepresented(),
                "Player HP is explicitly excluded");
        assertTrue(!sigewinne.isBondOfLifeRepresented(),
                "Bond of Life is explicitly excluded");
        assertTrue(!sigewinne.isMovementGeometryRepresented(),
                "Movement and geometry are explicitly excluded");
        assertTrue(!sigewinne.isRandomBounceTargetingRepresented(),
                "Random/multi-target bounce selection is excluded");
        assertTrue(!sigewinne.isUnderwaterRepresented(),
                "Underwater behavior is explicitly excluded");
        assertTrue(!sigewinne.isHitlagStaminaRepresented(),
                "Hitlag and stamina are explicitly excluded");
        assertTrue(!sigewinne.isC6CritRepresented(),
                "Healing-triggered C6 is explicitly excluded");
        assertThrows(IllegalArgumentException.class,
                () -> new Sigewinne(null, null, -1),
                "Sigewinne rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Sigewinne(null, null, 7),
                "Sigewinne rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> sigewinne.onAction(null, simulator),
                "Sigewinne rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> sigewinne.initializeForSimulator(null),
                "Sigewinne rejects null simulator");

        Sigewinne reused = new Sigewinne(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Sigewinne rejects cross-simulator reuse");
        Sigewinne foreign = new Sigewinne(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!sigewinne.acceptsCharacterState(foreignState),
                "Sigewinne rejects another instance's snapshot payload");
    }

    private static void testSnapshotRestore() {
        Sigewinne sigewinne = new Sigewinne(null, null, 0);
        CombatSimulator simulator = simulatorWith(sigewinne);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureHydroParticles(simulator);
        performSkill(simulator, SkillActionMode.PRESS);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        sigewinne.addBuff(new SimpleBuff(
                "Post-cast HP mutation",
                10.0,
                simulator.getCurrentTime(),
                stats -> stats.add(StatType.HP_PERCENT, 1.0)));
        records.clear();
        particles.clear();
        advanceTo(simulator, 143.0 * FRAME);
        ActionRecord original = named(records,
                "Rebound Hydrotherapy Bubble 2").get(0);
        assertClose(0.288,
                original.action.getStatSnapshot().get(
                        StatType.HP_PERCENT),
                "Pending bubble preserves cast-time HP snapshot");
        assertEquals(1, particles.size(),
                "Original branch resolves one particle packet");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        advanceTo(simulator, 143.0 * FRAME);
        assertEquals(1, named(records,
                "Rebound Hydrotherapy Bubble 2").size(),
                "Repeated restore reconstructs pending bubble once");
        assertClose(0.288,
                named(records, "Rebound Hydrotherapy Bubble 2")
                        .get(0).action.getStatSnapshot().get(
                                StatType.HP_PERCENT),
                "Restored bubble preserves cast-time HP snapshot");
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs particle packet once");
        assertEquals(3, sigewinne.getPendingEventCount(),
                "Restore retains only unresolved later bubbles");
    }

    private static void addMaxHp(
            Sigewinne sigewinne,
            double targetMaxHp) {
        double baseMaxHp = 13348.0 * 1.288;
        sigewinne.addBuff(new SimpleBuff(
                "Sigewinne test Max HP",
                100.0,
                0.0,
                stats -> stats.add(
                        StatType.HP_FLAT,
                        targetMaxHp - baseMaxHp)));
    }

    private static AttackAction testSkillAction() {
        AttackAction action = new AttackAction(
                "Convalescence Skill probe",
                1.0,
                Element.GEO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                0.0,
                ActionType.SKILL);
        action.setICD(ICDType.None, ICDTag.ElementalSkill, 0.0);
        action.setCountsAsSkillDmg(true);
        return action;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
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
                CharacterId.SIGEWINNE,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.SIGEWINNE,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.SIGEWINNE) {
                records.add(new ActionRecord(action, time));
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
            String name) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().equals(name)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static List<ActionRecord> prefixed(
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

    private static Buff typedBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        throw new AssertionError("Missing typed buff " + id);
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
            assertEquals(6,
                    lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Sigewinne,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Sigewinne/Sigewinne_Status.csv",
                "config/characters/Sigewinne/Sigewinne_Multipliers.csv"
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
        throw new AssertionError("Sigewinne CSVs missing key " + key);
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
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
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
