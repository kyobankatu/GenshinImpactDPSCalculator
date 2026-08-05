package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.element.ICDManager;
import model.character.Mualani;
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

/** Focused regression checks for Mualani's fixed-target Surging Bite slice. */
public final class MualaniRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private MualaniRegressionTest() {
    }

    /** Runs data, timing, Nightsoul, passive, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testCatalystBasicsAndTimings();
        testNightsoulContactsSurgingBiteAndParticles();
        testSkillExitCooldownAndNaturalDrain();
        testA1AndConstellationBranches();
        testBurstProjectileSnapshotAndA4();
        testSnapshotGenerationIsolationAndFailClosedGuards();
        System.out.println("MualaniRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Mualani mualani = new Mualani(null, null, 6);
        assertEquals(CharacterId.MUALANI, mualani.getCharacterId(),
                "Mualani typed identity");
        assertEquals(CharacterId.MUALANI, CharacterId.fromName("Mualani"),
                "Mualani name lookup");
        assertEquals(CharacterId.MUALANI, CharacterId.fromNumericId(91),
                "Mualani numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.MUALANI.getRegion(), "Mualani region");
        assertEquals(Element.HYDRO, mualani.getElement(),
                "Mualani element");
        assertClose(15185.0,
                mualani.getBaseStats().get(StatType.BASE_HP),
                "Mualani base HP");
        assertClose(182.0,
                mualani.getBaseStats().get(StatType.BASE_ATK),
                "Mualani base ATK");
        assertClose(570.0,
                mualani.getBaseStats().get(StatType.BASE_DEF),
                "Mualani base DEF");
        assertClose(0.242,
                mualani.getBaseStats().get(StatType.CRIT_RATE),
                "Mualani total base CRIT Rate");
        assertClose(60.0, mualani.getEnergyCost(),
                "Mualani Energy cost");
        assertClose(6.0, mualani.getSkillCD(),
                "Mualani exit-based Skill cooldown");
        assertClose(15.0, mualani.getBurstCD(),
                "Mualani Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.MUALANI,
                    new Mualani(null, null, constellation)
                            .getCharacterId(),
                    "Mualani explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Mualani/Mualani_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Mualani/Mualani_Multipliers.csv"), 52);
        assertCsvValue("Surging Bite Additional C3", 0.434);
        assertCsvValue("Boomsharka-laka C5", 1.168784);
    }

    private static void testCatalystBasicsAndTimings() {
        Mualani mualani = new Mualani(null, null, 0);
        CombatSimulator simulator = simulatorWith(mualani);
        List<ActionRecord> records = captureMualaniActions(simulator);
        double[] multipliers = { 0.873732, 0.758635, 1.190585 };
        double[] hitFrames = { 11.0, 42.0, 96.0 };
        double[] endFrames = { 33.0, 65.0, 132.0 };
        for (int index = 0; index < multipliers.length; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(index);
            assertClose(hitFrames[index] * FRAME, record.time,
                    "Mualani N" + (index + 1) + " impact frame");
            assertClose(endFrames[index] * FRAME,
                    simulator.getCurrentTime(),
                    "Mualani N" + (index + 1) + " duration");
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Mualani N" + (index + 1) + " multiplier");
            assertEquals(Element.HYDRO, record.action.getElement(),
                    "Mualani catalyst Normal element");
            assertEquals(ICDType.Standard, record.action.getICDType(),
                    "Mualani Normal standard ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Mualani Normal ICD tag");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = records.get(3);
        assertClose(chargedCast + 71.0 * FRAME, charged.time,
                "Mualani Charged impact frame");
        assertClose(chargedCast + 100.0 * FRAME,
                simulator.getCurrentTime(),
                "Mualani Charged duration");
        assertClose(2.42896, charged.action.getDamagePercent(),
                "Mualani Charged multiplier");
        assertEquals(ActionType.CHARGE, charged.action.getActionType(),
                "Mualani Charged action type");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Mualani Charged no ICD");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(4);
        assertClose(plungeCast + 45.0 * FRAME, plunge.time,
                "Mualani high Plunge impact frame");
        assertClose(plungeCast + 68.0 * FRAME,
                simulator.getCurrentTime(),
                "Mualani high Plunge duration");
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Mualani high Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.action.getActionType(),
                "Mualani high Plunge action type");
    }

    private static void testNightsoulContactsSurgingBiteAndParticles() {
        Mualani contactBoundary = new Mualani(null, null, 0);
        CombatSimulator contactSimulator = simulatorWith(contactBoundary);
        perform(contactSimulator, CharacterActionKey.SKILL);
        assertTrue(contactBoundary.notifyFixedTargetSurfingContact(
                contactSimulator),
                "Mualani first fixed-target contact");
        contactSimulator.advanceTime(0.699);
        assertTrue(!contactBoundary.notifyFixedTargetSurfingContact(
                contactSimulator),
                "Mualani contact rejects the 0.7-second pre-boundary");
        contactSimulator.advanceTime(0.001);
        assertTrue(contactBoundary.notifyFixedTargetSurfingContact(
                contactSimulator),
                "Mualani contact accepts the exact 0.7-second boundary");

        Mualani mualani = new Mualani(
                null, null, TalentDataFixture.INSTANCE, 2, () -> 0.25);
        CombatSimulator simulator = simulatorWith(mualani);
        List<ActionRecord> records = captureMualaniActions(simulator);
        List<Double> particles = captureHydroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(69.0 * FRAME, simulator.getCurrentTime(),
                "Mualani Skill duration");
        assertTrue(mualani.isNightsoulActive(),
                "Mualani enters Nightsoul at frame two");
        assertClose(49.0, mualani.getNightsoulPoints(),
                "Mualani drains eleven points during Skill animation");
        assertEquals(2, mualani.getWaveMomentumStacks(),
                "Mualani C2 starts with two momentum stacks");
        assertTrue(mualani.notifyFixedTargetSurfingContact(simulator),
                "Mualani fixed-target contact adds third stack");
        assertTrue(!mualani.notifyFixedTargetSurfingContact(simulator),
                "Mualani contact obeys 0.7-second gate");

        double biteCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> bites = named(records, "Sharky's Surging Bite");
        assertEquals(1, bites.size(),
                "Mualani single-target Surging Bite count");
        ActionRecord bite = bites.get(0);
        assertClose(biteCast + 42.0 * FRAME, bite.time,
                "Mualani Surging Bite frame 42");
        assertClose(biteCast + 258.0 * FRAME,
                simulator.getCurrentTime(),
                "Mualani Surging Bite duration");
        assertClose(1.3978, bite.action.getDamagePercent(),
                "Mualani C2 Surging Bite stacks and C1 multiplier");
        assertEquals(StatType.BASE_HP,
                bite.action.getScalingStat(),
                "Mualani Bite scales with Max HP");
        assertEquals(StatType.NORMAL_ATTACK_DMG_BONUS,
                bite.action.getBonusStat(),
                "Mualani Bite uses Normal Attack bonus");
        assertEquals(ICDType.None, bite.action.getICDType(),
                "Mualani Bite has no ICD");
        assertEquals(ICDTag.None, bite.action.getICDTag(),
                "Mualani Bite has no shared application tag");
        assertClose(bite.time + 1.8,
                mualani.getNextBiteAllowedTime(),
                "Mualani Bite cooldown starts on impact");
        assertEquals(1, mualani.getWaveMomentumStacks(),
                "Mualani Bite consumes stacks then C2 A1 refills one");
        assertEquals(1, mualani.getA1PufferCount(),
                "Mualani first Bite consumes one A1 puffer");
        assertEquals(1, particles.size(),
                "Mualani first Bite creates one particle packet");
        assertClose(5.0, particles.get(0),
                "Mualani low random draw creates five particles");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                "Mualani", ICDTag.NormalAttack,
                ICDType.Standard, 0.0),
                "Mualani Normal first application");
        assertTrue(icd.checkApplication(
                "Mualani", ICDTag.None,
                ICDType.None, 0.1),
                "Mualani Bite no-ICD application remains independent");
    }

    private static void testSkillExitCooldownAndNaturalDrain() {
        Mualani cancel = new Mualani(null, null, 0);
        CombatSimulator cancelSimulator = simulatorWith(cancel);
        perform(cancelSimulator, CharacterActionKey.SKILL);
        double cancelTime = cancelSimulator.getCurrentTime();
        perform(cancelSimulator, CharacterActionKey.SKILL);
        assertTrue(!cancel.isNightsoulActive(),
                "Mualani second Skill exits Nightsoul");
        assertClose(0.0, cancel.getNightsoulPoints(),
                "Mualani exit clears Nightsoul points");
        assertClose(cancelTime + 6.0,
                cancel.getSkillCooldownEndTime(),
                "Mualani Skill cooldown starts on manual exit");
        assertClose(cancelTime + 17.0 * FRAME,
                cancelSimulator.getCurrentTime(),
                "Mualani Skill cancel duration");

        Mualani natural = new Mualani(null, null, 0);
        CombatSimulator naturalSimulator = simulatorWith(natural);
        perform(naturalSimulator, CharacterActionKey.SKILL);
        advanceTo(naturalSimulator, 362.0 * FRAME - 0.001);
        assertTrue(natural.isNightsoulActive(),
                "Mualani remains active before final drain tick");
        naturalSimulator.advanceTime(0.001);
        assertTrue(!natural.isNightsoulActive(),
                "Mualani exits at exact final drain tick");
        assertClose(362.0 * FRAME + 6.0,
                natural.getSkillCooldownEndTime(),
                "Mualani natural exit starts six-second cooldown");
    }

    private static void testA1AndConstellationBranches() {
        Mualani c1 = new Mualani(
                null, null, TalentDataFixture.INSTANCE, 1, () -> 0.75);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureMualaniActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.NORMAL);
        perform(c1Simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> c1Bites = named(c1Records, "Sharky's Bite");
        assertEquals(2, c1Bites.size(),
                "Mualani C1 two Bite sequence");
        assertClose(0.80756,
                c1Bites.get(0).action.getDamagePercent(),
                "Mualani C1 first Bite Max-HP bonus");
        assertClose(0.14756,
                c1Bites.get(1).action.getDamagePercent(),
                "Mualani C1 bonus is once per blessing");

        Mualani c3 = new Mualani(
                null, null, TalentDataFixture.INSTANCE, 3, () -> 0.75);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureMualaniActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        assertTrue(c3.notifyFixedTargetSurfingContact(c3Simulator),
                "Mualani C3 reaches Surging Bite stacks");
        perform(c3Simulator, CharacterActionKey.NORMAL);
        assertClose(1.528,
                named(c3Records, "Sharky's Surging Bite").get(0)
                        .action.getDamagePercent(),
                "Mualani C3 Skill talent and C1 bonus");

        Mualani c6 = new Mualani(
                null, null, TalentDataFixture.INSTANCE, 6, () -> 0.75);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureMualaniActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        c6.spendEnergy(60.0);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> c6Bites = named(c6Records, "Sharky's Bite");
        assertEquals(2, c6Bites.size(),
                "Mualani C6 two non-Surging Bites");
        assertClose(1.0072,
                c6Bites.get(0).action.getDamagePercent(),
                "Mualani C6 first C2 two-stack Bite");
        assertClose(0.9204,
                c6Bites.get(1).action.getDamagePercent(),
                "Mualani C6 repeats C1 after A1 momentum refill");
        assertEquals(2, c6.getA1PufferCount(),
                "Mualani A1 puffer two-trigger cap");
        assertClose(16.0, c6.getTotalFlatEnergy(),
                "Mualani C4 grants eight Energy per puffer");
        assertTrue(c6.getNightsoulPoints() > 0.0,
                "Mualani C2 delayed points preserve Nightsoul state");
    }

    private static void testBurstProjectileSnapshotAndA4() {
        Mualani c0 = new Mualani(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureMualaniActions(simulator);
        c0.addBuff(new SimpleBuff(
                "Mualani temporary HP",
                BuffId.CUSTOM,
                2.0,
                0.0,
                stats -> stats.add(StatType.HP_PERCENT, 0.5)));
        assertTrue(c0.notifyExternallyConfirmedNightsoulBurst(simulator),
                "Mualani A4 first external trigger");
        assertTrue(c0.notifyExternallyConfirmedNightsoulBurst(simulator),
                "Mualani A4 second external trigger");
        assertTrue(c0.notifyExternallyConfirmedNightsoulBurst(simulator),
                "Mualani A4 third external trigger");
        assertTrue(!c0.notifyExternallyConfirmedNightsoulBurst(simulator),
                "Mualani A4 three-stack cap");
        perform(simulator, CharacterActionKey.BURST);
        assertClose(180.0 * FRAME, simulator.getCurrentTime(),
                "Mualani Burst duration");
        assertClose(15.0, c0.getBurstCooldownEndTime(),
                "Mualani Burst cooldown starts at cast");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Mualani Burst spends Energy at frame eleven");
        assertEquals(0, c0.getA4Stacks(),
                "Mualani Burst consumes A4 stacks at cast");
        ActionRecord burst = named(records, "Boomsharka-laka").get(0);
        assertClose(178.0 * FRAME, burst.time,
                "Mualani Burst impact follows frame-108 creation and travel");
        assertEquals(ICDType.None, burst.action.getICDType(),
                "Mualani Burst has no ICD");
        assertClose(15185.0 * (0.993466 + 0.45),
                burst.action.getAdditiveBaseDmgBonus(),
                "Mualani Burst uses impact-time Max HP and three A4 stacks");
        assertClose(15185.0 * 1.5,
                burst.action.getStatSnapshot().getTotalHp(),
                "Mualani Burst retains frame-108 stat snapshot");

        Mualani c6 = new Mualani(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureMualaniActions(c6Simulator);
        c6.notifyExternallyConfirmedNightsoulBurst(c6Simulator);
        c6.notifyExternallyConfirmedNightsoulBurst(c6Simulator);
        c6.notifyExternallyConfirmedNightsoulBurst(c6Simulator);
        perform(c6Simulator, CharacterActionKey.BURST);
        AttackAction c6Burst = named(c6Records, "Boomsharka-laka")
                .get(0).action;
        assertClose(15185.0 * (1.168784 + 0.45),
                c6Burst.getAdditiveBaseDmgBonus(),
                "Mualani C5 Burst talent and A4 addition");
        assertClose(0.75,
                bonus(c6Burst, StatType.DMG_BONUS_ALL),
                "Mualani C4 Burst damage bonus");
    }

    private static void testSnapshotGenerationIsolationAndFailClosedGuards() {
        Mualani mualani = new Mualani(null, null, 2);
        CombatSimulator simulator = simulatorWith(mualani);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 75.0 * FRAME);
        assertClose(48.0, mualani.getNightsoulPoints(),
                "Mualani live drain advances once");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 75.0 * FRAME);
        assertClose(48.0, mualani.getNightsoulPoints(),
                "Mualani repeated restore reconstructs one drain event");
        double cancelTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, cancelTime + 2.0);
        assertTrue(!mualani.isNightsoulActive(),
                "Mualani canceled generation remains invalidated");
        assertClose(0.0, mualani.getNightsoulPoints(),
                "Mualani stale drain cannot revive canceled state");

        assertTrue(!mualani.isPlayerHpChangeRepresented(),
                "Mualani player HP change fails closed");
        assertTrue(!mualani.isHealingRepresented(),
                "Mualani healing fails closed");
        assertTrue(!mualani.isMovementSurfingTerrainRepresented(),
                "Mualani movement, surfing, and terrain fail closed");
        assertTrue(!mualani.isEnemyMarkGeometryRepresented(),
                "Mualani enemy mark geometry fails closed");
        assertTrue(!mualani.isMultiTargetMissileRepresented(),
                "Mualani multi-target missiles fail closed");
        assertTrue(!mualani.isNightsoulBurstTeamPlumbingRepresented(),
                "Mualani automatic Nightsoul Burst plumbing fails closed");
        assertTrue(!mualani.isRandomTargetRepresented(),
                "Mualani random targeting fails closed");
        assertTrue(!mualani.isStaminaRepresented(),
                "Mualani stamina fails closed");
        assertTrue(!mualani.isHitlagRepresented(),
                "Mualani hitlag fails closed");
        assertTrue(!mualani.isLowPlungeRepresented(),
                "Mualani low Plunge fails closed");
        assertTrue(!mualani.isExplorationStateRepresented(),
                "Mualani exploration state fails closed");

        assertThrows(IllegalArgumentException.class,
                () -> new Mualani(null, null, -1),
                "Mualani rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Mualani(null, null, 7),
                "Mualani rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Mualani(
                        null, null, TalentDataFixture.INSTANCE, 0, null),
                "Mualani rejects null particle randomness");
        assertThrows(IllegalArgumentException.class,
                () -> mualani.onAction(null, simulator),
                "Mualani rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.MUALANI,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Mualani rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Mualani rejects unsupported Dash");

        Mualani invalidRandom = new Mualani(
                null, null, TalentDataFixture.INSTANCE, 0, () -> 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> perform(
                        invalidSimulator, CharacterActionKey.NORMAL),
                "Mualani rejects invalid particle random draw");

        Mualani foreign = new Mualani(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!mualani.acceptsCharacterState(foreignState),
                "Mualani rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> mualani.restoreCharacterState(null, simulator),
                "Mualani rejects null snapshot payload");
        Mualani external = new Mualani(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Mualani rejects binding outside simulator party");
        Mualani reused = new Mualani(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Mualani rejects cross-simulator reuse");

        Mualani isolated = new Mualani(null, null, 2);
        CombatSimulator isolatedSimulator = simulatorWith(isolated);
        perform(isolatedSimulator, CharacterActionKey.SKILL);
        assertEquals(2, isolated.getWaveMomentumStacks(),
                "Mualani first instance owns C2 stacks");
        assertEquals(0, foreign.getWaveMomentumStacks(),
                "Mualani second instance remains isolated");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
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
                CharacterId.MUALANI,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureMualaniActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.MUALANI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureHydroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
                records.add(count);
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
            assertTrue(lines.get(index).startsWith("Mualani,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Mualani/Mualani_Status.csv",
                "config/characters/Mualani/Mualani_Multipliers.csv"
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
        throw new AssertionError("Mualani CSVs missing key " + key);
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
        if (!expected.equals(actual)) {
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
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Captured Mualani action and resolution timestamp. */
    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    /** Uses the production CSV loader while keeping constructor injection explicit. */
    private enum TalentDataFixture implements mechanics.data.TalentDataSource {
        INSTANCE;

        @Override
        public double get(
                String character,
                String key,
                double defaultValue) {
            return mechanics.data.TalentDataManager.getInstance()
                    .get(character, key, defaultValue);
        }
    }
}
