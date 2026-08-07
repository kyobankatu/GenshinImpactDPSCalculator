package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Bennett;
import model.character.Durin;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
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

/** Focused regression checks for Durin's bounded dual-form slice. */
public final class DurinRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private DurinRegressionTest() {
    }

    /** Runs data, action, form, passive, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testSwordBasicsAndPrivateIcd();
        testDualFormSkillAndParticles();
        testWhiteAndBlackBursts();
        testSupportConstellationsSnapshotAndBoundaries();
        System.out.println("DurinRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Durin durin = new Durin(null, null, 6);
        assertEquals(CharacterId.DURIN, durin.getCharacterId(),
                "Durin typed identity");
        assertEquals(CharacterId.DURIN, CharacterId.fromName("Durin"),
                "Durin name lookup");
        assertEquals(CharacterId.DURIN, CharacterId.fromNumericId(102),
                "Durin numeric lookup");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.DURIN.getRegion(), "Durin region");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromNumericId(Integer.MAX_VALUE),
                "Out-of-range numeric ID stays unknown");
        assertEquals(Element.PYRO, durin.getElement(),
                "Durin element");
        assertClose(12429.0,
                durin.getBaseStats().get(StatType.BASE_HP),
                "Durin base HP");
        assertClose(347.0,
                durin.getBaseStats().get(StatType.BASE_ATK),
                "Durin base ATK");
        assertClose(822.0,
                durin.getBaseStats().get(StatType.BASE_DEF),
                "Durin base DEF");
        assertClose(0.884,
                durin.getBaseStats().get(StatType.CRIT_DMG),
                "Durin total base CRIT DMG");
        assertClose(70.0, durin.getEnergyCost(),
                "Durin Burst cost");
        assertClose(12.0, durin.getSkillCD(),
                "Durin Skill cooldown");
        assertClose(18.0, durin.getBurstCD(),
                "Durin Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.DURIN,
                    new Durin(null, null, constellation).getCharacterId(),
                    "Durin explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Durin/Durin_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Durin/Durin_Multipliers.csv"), 47);
        assertCsvValue("Dragon of Dark Decay C3", 2.5968);
        assertCsvValue("C6 Black DEF Ignore", 0.70);
    }

    private static void testSwordBasicsAndPrivateIcd() {
        Durin durin = new Durin(null, null, 0);
        CombatSimulator simulator = simulatorWith(durin);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            0.838696, 0.753344, 0.535762, 0.535762, 1.307213
        };
        double[] frames = { 11.0, 38.0, 73.0, 96.0, 152.0 };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterId.DURIN,
                    CharacterActionKey.NORMAL);
        }
        assertEquals(5, records.size(),
                "Durin four-step Normal string has five hits");
        for (int index = 0; index < records.size(); index++) {
            assertClose(multipliers[index],
                    records.get(index).action.getDamagePercent(),
                    "Durin Normal multiplier " + index);
            assertClose(frames[index] * FRAME,
                    records.get(index).time,
                    "Durin Normal hit frame " + index);
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Durin sword Normal element");
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterId.DURIN,
                CharacterActionKey.CHARGE);
        ActionRecord charged = named(records, "Radiant Wingslash Charged")
                .get(0);
        assertClose(chargedCast + 17.0 * FRAME, charged.time,
                "Durin Charged hit frame");
        assertClose(2.084020, charged.action.getDamagePercent(),
                "Durin Charged multiplier");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterId.DURIN,
                CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(records, "Radiant Wingslash High")
                .get(0);
        assertClose(plungeCast + 48.0 * FRAME, plunge.time,
                "Durin high Plunge frame");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Durin high Plunge multiplier");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Durin high Plunge has no ICD");

        ICDManager manager = new ICDManager();
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_BlackSkill,
                ICDType.DurinBlackSkill, 0.0),
                "Durin black Skill admits first hit");
        assertTrue(!manager.checkApplication(
                "Durin", ICDTag.Durin_BlackSkill,
                ICDType.DurinBlackSkill, 0.1),
                "Durin black Skill suppresses inside 0.3 seconds");
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_BlackSkill,
                ICDType.DurinBlackSkill, 0.3),
                "Durin black Skill admits exact boundary");
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_WhiteBurst,
                ICDType.DurinWhiteBurst, 0.0),
                "Durin white Burst ICD stays independent");
        assertTrue(!manager.checkApplication(
                "Durin", ICDTag.Durin_WhiteBurst,
                ICDType.DurinWhiteBurst, 1.49),
                "Durin white Burst suppresses before 1.5 seconds");
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_WhiteBurst,
                ICDType.DurinWhiteBurst, 1.5),
                "Durin white Burst admits exact boundary");
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_BlackBurst,
                ICDType.DurinBlackBurst, 0.0),
                "Durin black Burst admits first hit");
        assertTrue(!manager.checkApplication(
                "Durin", ICDTag.Durin_BlackBurst,
                ICDType.DurinBlackBurst, 1.99),
                "Durin black Burst suppresses before two seconds");
        assertTrue(manager.checkApplication(
                "Durin", ICDTag.Durin_BlackBurst,
                ICDType.DurinBlackBurst, 2.0),
                "Durin black Burst admits exact boundary");
    }

    private static void testDualFormSkillAndParticles() {
        Durin white = new Durin(null, null, 0);
        CombatSimulator whiteSimulator = simulatorWith(white);
        List<ActionRecord> whiteRecords = captureActions(whiteSimulator);
        List<ParticleRecord> whiteParticles =
                captureParticles(whiteSimulator);
        white.spendEnergy(70.0);
        perform(whiteSimulator, CharacterId.DURIN,
                CharacterActionKey.SKILL);
        assertTrue(white.isSkillSelectionActive(),
                "Initial Skill opens the choice window");
        assertEquals(0, whiteRecords.size(),
                "Initial Skill choice deals no damage");
        perform(whiteSimulator, CharacterId.DURIN,
                CharacterActionKey.SKILL);
        assertEquals("WHITE", white.getSelectedForm(),
                "Skill recast selects white form");
        ActionRecord whiteHit = named(
                whiteRecords, "Transmutation: Confirmation").get(0);
        assertClose(84.0 * FRAME, whiteHit.time,
                "White Skill recast hit frame from initial cast");
        assertClose(1.7952, whiteHit.action.getDamagePercent(),
                "White Skill multiplier");
        assertClose(30.0, white.getCurrentEnergy(),
                "C0 Skill restores 30 Energy");
        assertClose(12.0 - whiteSimulator.getCurrentTime(),
                white.getSkillCDRemaining(
                        whiteSimulator.getCurrentTime()),
                "Skill cooldown remains anchored to initial cast");
        advanceTo(whiteSimulator,
                whiteHit.time + 100.0 * FRAME + EPSILON);
        assertEquals(1, whiteParticles.size(),
                "White Skill emits one particle packet");
        assertClose(4.0, whiteParticles.get(0).count,
                "White Skill emits four Pyro particles");

        Durin black = new Durin(null, null, 5);
        CombatSimulator blackSimulator = simulatorWith(black);
        List<ActionRecord> blackRecords = captureActions(blackSimulator);
        List<ParticleRecord> blackParticles =
                captureParticles(blackSimulator);
        black.spendEnergy(70.0);
        perform(blackSimulator, CharacterId.DURIN,
                CharacterActionKey.SKILL);
        perform(blackSimulator, CharacterId.DURIN,
                CharacterActionKey.NORMAL);
        assertEquals("BLACK", black.getSelectedForm(),
                "Normal recast selects black form");
        List<ActionRecord> denial = named(
                blackRecords, "Transmutation: Denial");
        assertEquals(3, denial.size(),
                "Black Skill recast deals three hits");
        assertClose(1.4448, denial.get(0).action.getDamagePercent(),
                "C5 raises black Skill hit one");
        assertClose(1.0640, denial.get(1).action.getDamagePercent(),
                "C5 raises black Skill hit two");
        assertClose(1.2928, denial.get(2).action.getDamagePercent(),
                "C5 raises black Skill hit three");
        assertClose(39.0, black.getCurrentEnergy(),
                "C5 Skill restores 39 Energy");
        advanceTo(blackSimulator,
                denial.get(0).time + 100.0 * FRAME + EPSILON);
        assertEquals(1, blackParticles.size(),
                "Black Skill's particle ICD emits one packet");
        assertEquals(ICDType.DurinBlackSkill,
                denial.get(0).action.getICDType(),
                "Black Skill uses its 0.3-second ICD");
    }

    private static void testWhiteAndBlackBursts() {
        Durin white = new Durin(null, null, 0);
        CombatSimulator whiteSimulator = simulatorWith(white);
        List<ActionRecord> whiteRecords = captureActions(whiteSimulator);
        perform(whiteSimulator, CharacterId.DURIN,
                CharacterActionKey.BURST);
        assertEquals("WHITE", white.getBurstForm(),
                "No black selection defaults Burst to white");
        assertClose(0.0, white.getCurrentEnergy(),
                "Burst spends 70 Energy at frame ten");
        assertEquals(1, named(whiteRecords,
                "Principle of Purity").size(),
                "First white opening hit resolves by frame 104");
        assertEquals(10, white.getA4Stacks(),
                "White Burst initializes ten A4 stacks");
        advanceTo(whiteSimulator, 156.0 * FRAME + EPSILON);
        ActionRecord firstDragon = named(
                whiteRecords, "Dragon of White Flame").get(0);
        assertClose(1.60888 * (1.0 + 347.0 * 0.0003),
                firstDragon.action.getDamagePercent(),
                "A4 multiplies the first white periodic hit");
        assertEquals(9, white.getA4Stacks(),
                "First periodic hit consumes one A4 stack");
        advanceTo(whiteSimulator, 23.0);
        assertEquals(20, named(
                whiteRecords, "Dragon of White Flame").size(),
                "White dragon resolves twenty fixed-target ticks");

        Durin black = new Durin(null, null, 6);
        CombatSimulator blackSimulator = simulatorWith(black);
        List<ActionRecord> blackRecords = captureActions(blackSimulator);
        perform(blackSimulator, CharacterId.DURIN,
                CharacterActionKey.SKILL);
        perform(blackSimulator, CharacterId.DURIN,
                CharacterActionKey.NORMAL);
        perform(blackSimulator, CharacterId.DURIN,
                CharacterActionKey.BURST);
        assertEquals("BLACK", black.getBurstForm(),
                "Black Skill selection drives black Burst");
        ActionRecord blackOpening = named(
                blackRecords, "Principle of Darkness").get(0);
        assertClose(2.5088,
                blackOpening.action.getDamagePercent(),
                "C3 raises black opening hit one");
        assertClose(0.70, blackOpening.action.getDefenseIgnore(),
                "C6 black Burst ignores 70 percent DEF");
        assertClose(0.40,
                bonus(blackOpening.action, StatType.DMG_BONUS_ALL),
                "C4 gives black Burst 40 percent DMG bonus");
        assertClose(347.0 * 1.5,
                bonus(blackOpening.action, StatType.FLAT_DMG_BONUS),
                "C1 black opening receives 150 percent Durin ATK");
        assertEquals(18, black.getBlackC1Stacks(),
                "C1 black opening consumes two stacks");
        StatsContainer blackStats = effectiveWithSimulator(
                black, blackSimulator);
        assertClose(0.40,
                blackStats.get(StatType.VAPORIZE_DMG_BONUS),
                "Black A1 gives 40 percent Vaporize bonus");
        assertClose(0.40,
                blackStats.get(StatType.MELT_DMG_BONUS),
                "Black A1 gives 40 percent Melt bonus");
    }

    private static void testSupportConstellationsSnapshotAndBoundaries() {
        Bennett bennett = new Bennett(null, null);
        Durin support = new Durin(null, null, 6);
        CombatSimulator supportSimulator = simulatorWith(bennett, support);
        perform(supportSimulator, CharacterId.DURIN,
                CharacterActionKey.BURST);
        assertTrue(bennett.hasBuff(BuffId.DURIN_C1_WHITE_FLAT_DMG),
                "White C1 grants an active-character flat-damage buff");
        ReactionResult overload = new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE,
                1.0,
                0.0,
                "Overloaded",
                ReactionResult.Kind.OVERLOAD);
        supportSimulator.notifyReaction(overload, bennett);
        StatsContainer supportedStats = effectiveWithSimulator(
                bennett, supportSimulator);
        assertClose(0.20,
                supportedStats.get(StatType.PYRO_RES_SHRED),
                "White A1 shreds Pyro RES after Overload");
        assertClose(0.20,
                supportedStats.get(StatType.ELECTRO_RES_SHRED),
                "White A1 shreds Electro RES after Overload");
        assertClose(0.50,
                supportedStats.get(StatType.PYRO_DMG_BONUS),
                "C2 grants Pyro DMG after Overload");
        assertClose(0.50,
                supportedStats.get(StatType.ELECTRO_DMG_BONUS),
                "C2 grants Electro DMG after Overload");
        assertClose(0.30,
                supportedStats.get(StatType.ENEMY_DEF_REDUCTION),
                "C6 white hit applies 30 percent DEF reduction");
        supportSimulator.advanceTime(6.0 + EPSILON);
        StatsContainer expiredStats = effectiveWithSimulator(
                bennett, supportSimulator);
        assertClose(0.0,
                expiredStats.get(StatType.PYRO_RES_SHRED),
                "White A1 shred expires after six seconds");
        assertClose(0.0,
                expiredStats.get(StatType.PYRO_DMG_BONUS),
                "C2 bonus expires after six seconds");

        Durin restored = new Durin(null, null, 0);
        CombatSimulator restoredSimulator = simulatorWith(restored);
        List<ActionRecord> restoredRecords =
                captureActions(restoredSimulator);
        perform(restoredSimulator, CharacterId.DURIN,
                CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = restoredSimulator.saveSnapshot();
        double targetTime = 5.0;
        advanceTo(restoredSimulator, targetTime);
        int firstPass = restoredRecords.size();
        assertTrue(firstPass > 3,
                "Burst has delayed work after snapshot");
        restoredSimulator.restoreSnapshot(snapshot);
        restoredSimulator.restoreSnapshot(snapshot);
        restoredRecords.clear();
        advanceTo(restoredSimulator, targetTime);
        assertEquals(firstPass - 1, restoredRecords.size(),
                "Repeated restore reconstructs each post-snapshot hit once");

        assertTrue(!restored.isPlayerHpHealingDefenseRepresented(),
                "Player HP, healing, and defense fail closed");
        assertTrue(!restored.isMovementGeometryRepresented(),
                "Movement and geometry fail closed");
        assertTrue(!restored.isMultiTargetRandomSelectionRepresented(),
                "Multi-target and random selection fail closed");
        assertTrue(!restored.isHitlagStaminaRepresented(),
                "Hitlag and stamina fail closed");
        assertTrue(!restored.isLowPlungeExplorationRepresented(),
                "Low Plunge and exploration fail closed");
        assertTrue(!restored.isUnsupportedTeamStateRepresented(),
                "Unsupported Hexerei and Burning team state fails closed");
        assertThrows(IllegalArgumentException.class,
                () -> new Durin(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Durin(null, null, 7),
                "Constellation above C6 is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> restored.onAction(null, restoredSimulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> restoredSimulator.performAction(
                        CharacterId.DURIN,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Hold Skill is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> perform(restoredSimulator,
                        CharacterId.DURIN,
                        CharacterActionKey.DASH),
                "Movement action is rejected");
        Durin foreign = new Durin(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!restored.acceptsCharacterState(foreignState),
                "Durin rejects another instance state");
        Durin reused = new Durin(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Durin rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterId characterId,
            CharacterActionKey key) {
        simulator.performAction(
                characterId, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DURIN) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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

    private static StatsContainer effectiveWithSimulator(
            Character character,
            CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
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
        if (targetTime > simulator.getCurrentTime()) {
            simulator.advanceTime(targetTime - simulator.getCurrentTime());
        }
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
            assertTrue(lines.get(index).startsWith("Durin,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Durin/Durin_Status.csv",
                "config/characters/Durin/Durin_Multipliers.csv"
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
        throw new AssertionError("Durin CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
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
                    + expected.getSimpleName() + " but caught "
                    + thrown.getClass().getSimpleName(), thrown);
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
