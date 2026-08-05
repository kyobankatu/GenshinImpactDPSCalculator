package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.element.ICDManager;
import model.character.Kinich;
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
import simulation.action.SkillActionMode;

/** Focused regression checks for Kinich's fixed-target Scalespiker slice. */
public final class KinichRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KinichRegressionTest() {
    }

    /** Runs loader, timing, resource, ICD, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testClaymoreBasicsAndSnapshots();
        testNightsoulLoopCannonParticlesAndCooldown();
        testPrivateIcdAndC2Window();
        testBurstTimingEnergyExtensionAndConstellations();
        testSnapshotGenerationSwitchAndFailClosedGuards();
        System.out.println("KinichRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Kinich kinich = new Kinich(null, null, 6);
        assertEquals(CharacterId.KINICH, kinich.getCharacterId(),
                "Kinich typed identity");
        assertEquals(CharacterId.KINICH, CharacterId.fromName("Kinich"),
                "Kinich name lookup");
        assertEquals(CharacterId.KINICH, CharacterId.fromNumericId(93),
                "Kinich numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.KINICH.getRegion(), "Kinich region");
        assertEquals(Element.DENDRO, kinich.getElement(),
                "Kinich element");
        assertClose(10875.0,
                kinich.getBaseStats().get(StatType.BASE_HP),
                "Kinich base HP");
        assertClose(332.0,
                kinich.getBaseStats().get(StatType.BASE_ATK),
                "Kinich base ATK");
        assertClose(692.0,
                kinich.getBaseStats().get(StatType.BASE_DEF),
                "Kinich base DEF");
        assertClose(0.884,
                kinich.getBaseStats().get(StatType.CRIT_DMG),
                "Kinich base plus ascension CRIT DMG");
        assertClose(70.0, kinich.getEnergyCost(),
                "Kinich Energy cost");
        assertClose(18.0, kinich.getSkillCD(),
                "Kinich Skill cooldown");
        assertClose(18.0, kinich.getBurstCD(),
                "Kinich Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.KINICH,
                    new Kinich(null, null, constellation)
                            .getCharacterId(),
                    "Kinich explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Kinich/Kinich_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Kinich/Kinich_Multipliers.csv"), 34);
        assertCsvValue("Scalespiker Cannon C3", 13.7488);
        assertCsvValue("Ajaw Dragon Breath C5", 2.41472);
    }

    private static void testClaymoreBasicsAndSnapshots() {
        Kinich kinich = new Kinich(null, null, 0);
        CombatSimulator simulator = simulatorWith(kinich);
        List<ActionRecord> records = captureKinichActions(simulator);
        double[] multipliers = { 1.81858, 1.52312, 2.26888 };
        double[] hitFrames = { 21.0, 69.0, 139.0 };
        double[] endFrames = { 47.0, 95.0, 174.0 };
        for (int index = 0; index < multipliers.length; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(index);
            assertEquals("Nightsun Style N" + (index + 1),
                    record.action.getName(), "Kinich Normal name");
            assertClose(hitFrames[index] * FRAME, record.time,
                    "Kinich N" + (index + 1) + " impact frame");
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Kinich N" + (index + 1) + " multiplier");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Kinich Normal type");
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Kinich Normal element");
            assertTrue(record.action.isShatterTrigger(),
                    "Kinich claymore Normal is blunt");
            assertTrue(record.action.hasStatSnapshot(),
                    "Kinich Normal owns its cast snapshot");
            assertClose(endFrames[index] * FRAME,
                    simulator.getCurrentTime(),
                    "Kinich N" + (index + 1) + " duration");
        }

        double chargedCast = simulator.getCurrentTime();
        int chargedStart = records.size();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(chargedStart + 1, records.size(),
                "Kinich first Charged hit lands during animation");
        advanceTo(simulator, chargedCast + 119.0 * FRAME);
        for (int index = 0; index < 3; index++) {
            ActionRecord charged = records.get(chargedStart + index);
            assertClose(chargedCast
                            + new double[] { 71.0, 95.0, 119.0 }[index]
                                    * FRAME,
                    charged.time,
                    "Kinich Charged hit timing " + index);
            assertClose(0.88954,
                    charged.action.getDamagePercent(),
                    "Kinich Charged hit multiplier");
            assertEquals(ActionType.CHARGE,
                    charged.action.getActionType(),
                    "Kinich Charged type");
            assertTrue(charged.action.isShatterTrigger(),
                    "Kinich Charged hit is blunt");
        }

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(records.size() - 1);
        assertClose(plungeCast, plunge.time,
                "Kinich high Plunge resolves at impact input");
        assertClose(3.422517,
                plunge.action.getDamagePercent(),
                "Kinich high Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                plunge.action.getActionType(),
                "Kinich high Plunge type");
        assertClose(plungeCast + 58.0 * FRAME,
                simulator.getCurrentTime(),
                "Kinich high Plunge duration");
    }

    private static void testNightsoulLoopCannonParticlesAndCooldown() {
        Kinich kinich = new Kinich(null, null, 6);
        CombatSimulator simulator = simulatorWith(kinich);
        List<ActionRecord> records = captureKinichActions(simulator);
        List<ParticleRecord> particles = captureDendroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(kinich.isNightsoulActive(),
                "Kinich enters local Nightsoul after frame 9");
        assertClose(1.0, kinich.getNightsoulPoints(),
                "Kinich first periodic point at frame 39");
        assertClose(9.0 * FRAME, kinich.getLastSkillTime(),
                "Kinich Skill cooldown starts on entry");
        assertClose(18.0 + 9.0 * FRAME,
                kinich.getSkillCooldownEndTime(),
                "Kinich exact Skill cooldown end");

        assertThrows(IllegalStateException.class,
                () -> perform(simulator, CharacterActionKey.SKILL),
                "Kinich rejects Cannon below maximum points");
        for (int loop = 0; loop < 4; loop++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertClose(20.0, kinich.getNightsoulPoints(),
                "Kinich Loop and time points cap at 20");
        List<ActionRecord> loops = named(records,
                "Canopy Hunter Loop Shot");
        assertEquals(8, loops.size(),
                "Kinich four Loop Shots resolve two hits each");
        assertClose(0.5, loops.get(0).time - 42.0 * FRAME,
                "Kinich Loop Shot first impact delay");
        assertClose(38.0 * FRAME,
                loops.get(1).time - 42.0 * FRAME,
                "Kinich Loop Shot second impact delay");
        for (ActionRecord loop : loops) {
            assertEquals(ActionType.SKILL,
                    loop.action.getActionType(),
                    "Kinich Loop Shot Skill type");
            assertEquals(ICDType.KinichLoopShot,
                    loop.action.getICDType(),
                    "Kinich Loop Shot private ICD type");
            assertEquals(ICDTag.Kinich_LoopShot,
                    loop.action.getICDTag(),
                    "Kinich Loop Shot private ICD tag");
            assertClose(1.1456,
                    loop.action.getDamagePercent(),
                    "Kinich C3 Loop Shot multiplier");
        }

        kinich.restoreCurrentEnergy(0.0);
        kinich.recordRepresentedNightsoulBurst(simulator);
        kinich.recordRepresentedNightsoulBurst(simulator);
        double cannonCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        List<ActionRecord> cannons = named(records,
                "Scalespiker Cannon");
        assertEquals(2, cannons.size(),
                "Kinich C6 adds one deterministic Cannon rebound");
        ActionRecord cannon = cannons.get(0);
        ActionRecord rebound = cannons.get(1);
        assertClose(cannonCast + 48.0 * FRAME, cannon.time,
                "Kinich Cannon release plus travel timing");
        assertClose(cannonCast + 98.0 * FRAME, rebound.time,
                "Kinich C6 rebound travel timing");
        assertClose(20.1488, cannon.action.getDamagePercent(),
                "Kinich Cannon consumes two A4 stacks");
        assertClose(13.4, rebound.action.getDamagePercent(),
                "Kinich C6 rebound retains A4 additive multiplier");
        assertClose(1.0, bonus(cannon.action, StatType.CRIT_DMG),
                "Kinich C1 Cannon CRIT DMG");
        assertClose(1.0,
                bonus(cannon.action, StatType.DMG_BONUS_ALL),
                "Kinich C2 first Cannon damage bonus");
        assertClose(0.0,
                kinich.getHuntersExperienceStacks(
                        simulator.getCurrentTime()),
                "Kinich Cannon consumes A4 stacks");
        assertClose(3.0, kinich.getNightsoulPoints(),
                "Kinich Cannon drains at release then resumes timed points");
        assertClose(cannonCast + 135.0 * FRAME,
                simulator.getCurrentTime(),
                "Kinich Cannon animation duration");
        assertEquals(0, particles.size(),
                "Kinich particles retain travel after Cannon animation");
        advanceTo(simulator, cannon.time + 100.0 * FRAME);
        assertEquals(1, particles.size(),
                "Kinich one particle event per Skill generation");
        assertClose(5.0, particles.get(0).count,
                "Kinich Skill particle count");
        assertClose(10.0, kinich.getTotalFlatEnergy(),
                "Kinich C4 respects its 2.8-second Energy gate");
    }

    private static void testPrivateIcdAndC2Window() {
        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_LoopShot,
                ICDType.KinichLoopShot,
                0.0), "Kinich Loop first hit applies");
        for (int hit = 0; hit < 3; hit++) {
            assertTrue(!manager.checkApplication(
                    "Kinich",
                    ICDTag.Kinich_LoopShot,
                    ICDType.KinichLoopShot,
                    0.1 * (hit + 1)),
                    "Kinich Loop suppressed hit " + hit);
        }
        assertTrue(manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_LoopShot,
                ICDType.KinichLoopShot,
                0.4), "Kinich Loop fourth-hit bypass");
        assertTrue(manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_ScalespikerCannon,
                ICDType.KinichScalespikerCannon,
                0.4), "Kinich Cannon ICD is independent");
        assertTrue(!manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_ScalespikerCannon,
                ICDType.KinichScalespikerCannon,
                1.59), "Kinich Cannon blocked before 1.2 seconds");
        assertTrue(manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_ScalespikerCannon,
                ICDType.KinichScalespikerCannon,
                1.60), "Kinich Cannon exact 1.2-second reset");
        assertTrue(manager.checkApplication(
                "Kinich",
                ICDTag.Kinich_LoopShot,
                ICDType.KinichLoopShot,
                2.4), "Kinich Loop exact two-second reset");

        Kinich kinich = new Kinich(null, null, 2);
        CombatSimulator simulator = simulatorWith(kinich);
        perform(simulator, CharacterActionKey.SKILL);
        double loopCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        Buff c2 = findTeamBuff(simulator,
                mechanics.buff.BuffId.KINICH_C2_DENDRO_RES_SHRED);
        assertTrue(c2 != null,
                "Kinich first Loop hit creates typed C2 shred");
        assertClose(loopCast + 30.0 * FRAME,
                c2.getStartTime(), "Kinich C2 starts after first damage");
        assertClose(loopCast + 30.0 * FRAME + 6.0,
                c2.getExpirationTime(), "Kinich C2 six-second duration");
        assertClose(0.3,
                teamDendroShred(simulator, kinich),
                "Kinich C2 Dendro resistance shred value");
        advanceTo(simulator, c2.getExpirationTime());
        assertClose(0.0,
                teamDendroShred(simulator, kinich),
                "Kinich C2 expires at the exact boundary");
    }

    private static void testBurstTimingEnergyExtensionAndConstellations() {
        Kinich kinich = new Kinich(null, null, 6);
        CombatSimulator simulator = simulatorWith(kinich);
        List<ActionRecord> records = captureKinichActions(simulator);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(burstCast + 126.0 * FRAME,
                simulator.getCurrentTime(),
                "Kinich Burst animation duration");
        assertClose(burstCast + 1.0 * FRAME,
                kinich.getLastBurstTime(),
                "Kinich Burst cooldown start delay");
        assertClose(0.0, kinich.getCurrentEnergy(),
                "Kinich Burst Energy consumed at frame 5");
        assertEquals(0, named(records,
                "Hail to the Almighty Dragonlord").size(),
                "Kinich Burst opener lands after animation");
        advanceTo(simulator, burstCast + 161.0 * FRAME);
        ActionRecord initial = named(records,
                "Hail to the Almighty Dragonlord").get(0);
        assertClose(burstCast + 161.0 * FRAME, initial.time,
                "Kinich Burst opener frame");
        assertClose(2.68, initial.action.getDamagePercent(),
                "Kinich C5 Burst opener multiplier");
        assertClose(0.7,
                bonus(initial.action, StatType.DMG_BONUS_ALL),
                "Kinich C4 Burst damage bonus");
        assertTrue(initial.action.hasStatSnapshot(),
                "Kinich Burst opener owns cast snapshot");
        advanceTo(simulator, burstCast + 988.0 * FRAME);
        List<ActionRecord> breaths = named(records,
                "Ajaw Dragon Breath");
        assertEquals(6, breaths.size(),
                "Kinich Ajaw resolves six deterministic breaths");
        int[] frames = { 253, 398, 548, 693, 843, 988 };
        for (int index = 0; index < frames.length; index++) {
            assertClose(burstCast + frames[index] * FRAME,
                    breaths.get(index).time,
                    "Kinich Ajaw deterministic frame " + index);
            assertClose(2.41472,
                    breaths.get(index).action.getDamagePercent(),
                    "Kinich C5 Ajaw multiplier");
            assertClose(0.7,
                    bonus(breaths.get(index).action,
                            StatType.DMG_BONUS_ALL),
                    "Kinich C4 Ajaw damage bonus");
        }
        Kinich extensionKinich = new Kinich(null, null, 0);
        CombatSimulator extensionSimulator = simulatorWith(extensionKinich);
        perform(extensionSimulator, CharacterActionKey.SKILL);
        double originalExpiration = 9.0 * FRAME + 610.0 * FRAME;
        perform(extensionSimulator, CharacterActionKey.BURST);
        advanceTo(extensionSimulator, originalExpiration + 1.69);
        assertTrue(extensionKinich.isNightsoulActive(),
                "Kinich Burst extends Nightsoul before 1.7 seconds");
        advanceTo(extensionSimulator, originalExpiration + 1.7);
        assertTrue(!extensionKinich.isNightsoulActive(),
                "Kinich Burst extension expires exactly");

        Kinich c0 = new Kinich(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureKinichActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        advanceTo(c0Simulator, 253.0 * FRAME);
        assertClose(2.278,
                named(c0Records,
                        "Hail to the Almighty Dragonlord")
                        .get(0).action.getDamagePercent(),
                "Kinich C0 Burst multiplier");
        assertClose(0.0,
                bonus(named(c0Records,
                        "Hail to the Almighty Dragonlord")
                        .get(0).action, StatType.DMG_BONUS_ALL),
                "Kinich C0 has no C4 Burst bonus");
    }

    private static void testSnapshotGenerationSwitchAndFailClosedGuards() {
        Kinich kinich = new Kinich(null, null, 0);
        TestCharacter ally = new TestCharacter(
                "Kinich Ally", CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(kinich, ally);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot pointSnapshot = simulator.saveSnapshot();
        advanceTo(simulator, 2.2);
        assertClose(4.0, kinich.getNightsoulPoints(),
                "Kinich live periodic points advance");
        simulator.restoreSnapshot(pointSnapshot);
        simulator.restoreSnapshot(pointSnapshot);
        advanceTo(simulator, 2.2);
        assertClose(4.0, kinich.getNightsoulPoints(),
                "Kinich restore reconstructs periodic points once");

        simulator.switchCharacter(CharacterId.XINGQIU);
        assertTrue(!kinich.isNightsoulActive(),
                "Kinich switch-out cancels Nightsoul");
        assertClose(0.0, kinich.getNightsoulPoints(),
                "Kinich switch-out clears points");
        advanceTo(simulator, 12.0);
        assertClose(0.0, kinich.getNightsoulPoints(),
                "Kinich stale point generation stays invalidated");

        Kinich burstKinich = new Kinich(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burstKinich);
        List<ActionRecord> records = captureKinichActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSimulator.saveSnapshot();
        advanceTo(burstSimulator, 17.0);
        assertEquals(7, records.size(),
                "Kinich live Burst resolves opener and six breaths");
        burstSimulator.restoreSnapshot(burstSnapshot);
        burstSimulator.restoreSnapshot(burstSnapshot);
        advanceTo(burstSimulator, 17.0);
        assertEquals(14, records.size(),
                "Kinich restore reconstructs one Burst sequence");
        assertEquals(0, burstKinich.getPendingHitCount(),
                "Kinich restore leaves no stale impacts");

        Kinich guard = new Kinich(null, null, 0);
        CombatSimulator guardSimulator = simulatorWith(guard);
        perform(guardSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> perform(guardSimulator, CharacterActionKey.CHARGE),
                "Kinich rejects Charged Attack in Nightsoul");
        assertThrows(IllegalStateException.class,
                () -> perform(guardSimulator, CharacterActionKey.PLUNGE),
                "Kinich rejects high Plunge in Nightsoul");
        assertThrows(IllegalArgumentException.class,
                () -> guardSimulator.performAction(
                        CharacterId.KINICH,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Kinich rejects grappling Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(guardSimulator, CharacterActionKey.DASH),
                "Kinich rejects unsupported Dash");
        assertTrue(!guard.isA1BurningBurgeonRepresented(),
                "Kinich A1 Burning/Burgeon branch fails closed");
        assertTrue(!guard.isNightsoulBurstTeamPlumbingRepresented(),
                "Kinich Nightsoul Burst team plumbing fails closed");
        assertTrue(!guard.isGrapplingMovementBlindSpotRepresented(),
                "Kinich grappling and Blind Spot fail closed");
        assertTrue(!guard.isRandomMultiTargetSelectionRepresented(),
                "Kinich random and multi-target selection fail closed");
        assertTrue(!guard.isHitlagStaminaRepresented(),
                "Kinich hitlag and stamina fail closed");
        assertTrue(!guard.isLowPlungeRepresented(),
                "Kinich low Plunge fails closed");
        assertTrue(!guard.isExplorationStateRepresented(),
                "Kinich exploration state fails closed");
        assertTrue(!guard.isC1MovementSpeedRepresented(),
                "Kinich C1 movement speed fails closed");
        assertTrue(!guard.isC2AreaIncreaseRepresented(),
                "Kinich C2 area increase fails closed");

        assertThrows(IllegalArgumentException.class,
                () -> new Kinich(null, null, -1),
                "Kinich rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Kinich(null, null, 7),
                "Kinich rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> guard.onAction(null, guardSimulator),
                "Kinich rejects null action");
        Kinich external = new Kinich(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Kinich rejects binding outside simulator party");
        SnapshotAwareCharacterEffect.State foreign =
                external.captureCharacterState();
        assertTrue(!guard.acceptsCharacterState(foreign),
                "Kinich rejects another instance state");
        assertTrue(!guard.acceptsCharacterState(null),
                "Kinich rejects null state");
        Kinich reused = new Kinich(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Kinich rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
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
                CharacterId.KINICH, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureKinichActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KINICH) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureDendroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
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

    private static Buff findTeamBuff(
            CombatSimulator simulator,
            mechanics.buff.BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id) {
                return buff;
            }
        }
        return null;
    }

    private static double teamDendroShred(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = new StatsContainer();
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(StatType.DENDRO_RES_SHRED);
    }

    private static double bonus(
            AttackAction action,
            StatType statType) {
        Double value = action.getExtraBonuses().get(statType);
        return value == null ? 0.0 : value;
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
            assertTrue(lines.get(index).startsWith("Kinich,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Kinich/Kinich_Status.csv",
                "config/characters/Kinich/Kinich_Multipliers.csv"
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
        throw new AssertionError("Kinich CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected "
                    + expected + ", got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected "
                    + expected + ", got " + actual);
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
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + ", got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Captured Kinich action and resolution timestamp. */
    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    /** Captured particle count and arrival timestamp. */
    private static final class ParticleRecord {
        private final double count;
        private final double time;

        private ParticleRecord(double count, double time) {
            this.count = count;
            this.time = time;
        }
    }

    /** Minimal typed teammate fixture for switch invalidation coverage. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                String displayName,
                CharacterId id,
                Element element) {
            name = displayName;
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }
    }
}
