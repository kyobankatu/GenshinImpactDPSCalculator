package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.reaction.ReactionResult;
import model.character.Nahida;
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

/** Focused regression checks for Nahida's bounded Tri-Karma slice. */
public final class NahidaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private NahidaRegressionTest() {
    }

    /** Runs identity, trigger, Shrine, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testConstellationAndInputGuards();
        testNormalChargedAndPlungeActions();
        testSkillTriggerCooldownExpiryAndParticles();
        testBurstElementBranchesAndA1();
        testAscensionAndConstellationEffects();
        testSnapshotRestore();
        System.out.println("NahidaRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.NAHIDA, CharacterId.fromNumericId(65),
                "Nahida numeric identity");
        assertEquals(CharacterId.NAHIDA, CharacterId.fromName("Nahida"),
                "Nahida exact-name identity");
        assertEquals(CharacterRegion.SUMERU, CharacterId.NAHIDA.getRegion(),
                "Nahida region");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("nahida"),
                "Nahida lookup remains case-sensitive");

        Nahida nahida = nahida(0);
        assertEquals(CharacterId.NAHIDA, nahida.getCharacterId(),
                "Nahida runtime identity");
        assertEquals(Element.DENDRO, nahida.getElement(),
                "Nahida element");
        assertClose(10360.0,
                nahida.getBaseStats().get(StatType.BASE_HP),
                "Nahida base HP");
        assertClose(299.0,
                nahida.getBaseStats().get(StatType.BASE_ATK),
                "Nahida base ATK");
        assertClose(630.0,
                nahida.getBaseStats().get(StatType.BASE_DEF),
                "Nahida base DEF");
        assertClose(115.2,
                nahida.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Nahida ascension Elemental Mastery");
        assertClose(50.0, nahida.getEnergyCost(),
                "Nahida Burst cost");
        assertClose(5.0, nahida.getSkillCD(),
                "Nahida Skill cooldown");
        assertClose(13.5, nahida.getBurstCD(),
                "Nahida Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Nahida/Nahida_Status.csv"));
        assertCsvShape(Path.of(
                "config/characters/Nahida/Nahida_Multipliers.csv"));
        assertCsvValue("Tri-Karma EM C3", 4.128);
        assertCsvValue("Hydro Extension 2 C5", 10.032);
    }

    private static void testConstellationAndInputGuards() {
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    nahida(constellation).getConstellation(),
                    "Nahida accepts C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> nahida(-1),
                "Nahida rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> nahida(7),
                "Nahida rejects constellation above C6");

        Nahida owner = nahida(0);
        CombatSimulator simulator = simulatorWith(owner);
        assertThrows(IllegalArgumentException.class,
                () -> owner.onAction(null, simulator),
                "Nahida rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.NAHIDA,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Nahida rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Nahida rejects unsupported Dash");

        Nahida external = nahida(0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Nahida rejects binding outside a party");
        Nahida reused = nahida(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Nahida rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreign =
                external.captureCharacterState();
        assertTrue(!owner.acceptsCharacterState(foreign),
                "Nahida rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(foreign, simulator),
                "Nahida rejects foreign restore payload");
    }

    private static void testNormalChargedAndPlungeActions() {
        Nahida owner = nahida(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO, 0.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        List<ActionRecord> records = captureActions(simulator);
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(records, "Akara N");
        assertEquals(4, normals.size(),
                "Nahida four-step Normal string resolves four hits");
        double[] multipliers = {
            0.685182, 0.628565, 0.779865, 0.992909
        };
        double[] hitFrames = { 23, 50, 92, 151 };
        for (int index = 0; index < normals.size(); index++) {
            assertClose(multipliers[index],
                    normals.get(index).action.getDamagePercent(),
                    "Nahida Normal multiplier " + index);
            assertClose(hitFrames[index] * FRAME,
                    normals.get(index).time,
                    "Nahida Normal hitmark " + index);
            assertEquals(Element.DENDRO,
                    normals.get(index).action.getElement(),
                    "Nahida catalyst Normal element " + index);
        }
        assertClose(182.0 * FRAME, simulator.getCurrentTime(),
                "Nahida Normal string duration");
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.NAHIDA);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Akara N1").size(),
                "Nahida switch-out resets the Normal string");

        Nahida chargedOwner = nahida(0);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                chargedRecords, "Akara Charged Attack").get(0);
        assertClose(2.244, charged.action.getDamagePercent(),
                "Nahida Charged multiplier");
        assertClose(65.0 * FRAME, charged.time,
                "Nahida Charged hitmark");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Nahida Charged has no ICD");

        Nahida plungeOwner = nahida(0);
        CombatSimulator plungeSimulator = simulatorWith(plungeOwner);
        List<ActionRecord> plungeRecords = captureActions(
                plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(
                plungeRecords, "Akara High Plunge").get(0);
        assertClose(2.607632, plunge.action.getDamagePercent(),
                "Nahida High Plunge multiplier");
        assertClose(46.0 * FRAME, plunge.time,
                "Nahida High Plunge hitmark");
        assertClose(68.0 * FRAME, plungeSimulator.getCurrentTime(),
                "Nahida High Plunge duration");
    }

    private static void testSkillTriggerCooldownExpiryAndParticles() {
        Nahida unmarked = nahida(0);
        CombatSimulator unmarkedSimulator = simulatorWith(unmarked);
        List<ActionRecord> unmarkedRecords = captureActions(
                unmarkedSimulator);
        notifyBloom(unmarkedSimulator, unmarked);
        unmarkedSimulator.advanceTime(1.0);
        assertEquals(0, named(
                unmarkedRecords, "Tri-Karma Purification").size(),
                "Reaction before Seed does not trigger Tri-Karma");

        Nahida owner = nahida(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord skill = named(
                records, "All Schemes to Know Press").get(0);
        assertClose(13.0 * FRAME, skill.time,
                "Nahida Tap Skill hitmark");
        assertClose(1.6728, skill.action.getDamagePercent(),
                "Nahida C0 Tap Skill multiplier");
        assertClose(11.0 * FRAME, owner.getLastSkillTime(),
                "Nahida Skill cooldown starts at frame 11");
        assertClose(11.0 * FRAME + 5.0,
                owner.getSkillCooldownEndTime(),
                "Nahida Skill cooldown duration");
        assertClose(13.0 * FRAME + 25.0,
                owner.getSeedExpirationTime(),
                "Nahida Seed lasts 25 seconds from hit");

        double firstTrigger = simulator.getCurrentTime();
        notifyBloom(simulator, owner);
        simulator.advanceTime(2.0 * FRAME);
        assertEquals(0, named(
                records, "Tri-Karma Purification").size(),
                "Tri-Karma waits through frame two");
        simulator.advanceTime(FRAME);
        List<ActionRecord> triKarma = named(
                records, "Tri-Karma Purification");
        assertEquals(1, triKarma.size(),
                "Tri-Karma resolves at frame three");
        ActionRecord first = triKarma.get(0);
        assertClose(firstTrigger + 3.0 * FRAME, first.time,
                "Tri-Karma exact delay");
        assertClose(1.7544, first.action.getDamagePercent(),
                "Nahida C0 Tri-Karma ATK ratio");
        assertEquals(ICDType.NahidaTriKarma,
                first.action.getICDType(),
                "Tri-Karma uses its one-second application group");
        assertEquals(ICDTag.Nahida_TriKarma,
                first.action.getICDTag(),
                "Tri-Karma uses its typed ICD tag");
        assertClose(115.2 * 3.5088,
                first.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Tri-Karma snapshots EM additive base damage");

        notifyBloom(simulator, owner);
        simulator.advanceTime(firstTrigger + 2.5
                - simulator.getCurrentTime());
        assertEquals(1, named(
                records, "Tri-Karma Purification").size(),
                "Tri-Karma global cooldown blocks early reaction");
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(2, named(
                records, "Tri-Karma Purification").size(),
                "Tri-Karma admits exact 2.5-second boundary");
        assertEquals(1, particles.size(),
                "Seven-second particle ICD admits one packet");
        assertClose(3.0, particles.get(0).count,
                "Tri-Karma particle packet size");
        assertClose(firstTrigger + 103.0 * FRAME,
                particles.get(0).time,
                "Tri-Karma particle travel time");

        advanceTo(simulator, owner.getSeedExpirationTime());
        assertTrue(!owner.isSeedActive(simulator.getCurrentTime()),
                "Seed expires at the exact boundary");
        notifyBloom(simulator, owner);
        simulator.advanceTime(1.0);
        assertEquals(2, named(
                records, "Tri-Karma Purification").size(),
                "Expired Seed cannot trigger Tri-Karma");
    }

    private static void testBurstElementBranchesAndA1() {
        Nahida owner = nahida(0);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, 800.0);
        TestCharacter electro = new TestCharacter(
                CharacterId.FISCHL, Element.ELECTRO, 0.0);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO, 0.0);
        CombatSimulator simulator = simulatorWith(
                owner, pyro, electro, hydro);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, owner.getCurrentEnergy(),
                "Nahida Burst spends 50 Energy at frame five");
        assertTrue(owner.isShrineEffectActive(
                        simulator.getCurrentTime()),
                "Shrine effects begin thirty frames after field creation");
        assertClose(
                burstCast + 66.0 * FRAME + 15.0 + 5.6848,
                owner.getShrineExpirationTime(),
                "One Hydro member extends C0 Shrine duration");

        simulator.setActiveCharacter(CharacterId.BENNETT);
        assertClose(1000.0,
                effectiveStats(pyro, simulator).get(
                        StatType.ELEMENTAL_MASTERY),
                "A1 grants 25 percent of highest structural EM");
        simulator.setActiveCharacter(CharacterId.NAHIDA);
        double firstTrigger = simulator.getCurrentTime();
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        ActionRecord first = named(
                records, "Tri-Karma Purification").get(0);
        assertClose(315.2,
                first.action.getStatSnapshot().get(
                        StatType.ELEMENTAL_MASTERY),
                "Nahida receives active-character A1 inside Shrine");
        assertClose(0.25296 + 0.1152,
                first.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Pyro Shrine bonus and A4 combine on Tri-Karma");
        assertClose(0.03456,
                first.action.getStatSnapshot().get(
                        StatType.SKILL_CRIT_RATE),
                "A4 converts trigger-time EM to Skill CRIT Rate");

        double reducedInterval = 2.5 - 0.4216;
        advanceTo(simulator, firstTrigger + reducedInterval);
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(2, named(
                records, "Tri-Karma Purification").size(),
                "One Electro member shortens the trigger interval");

        double shrineExpiry = owner.getShrineExpirationTime();
        advanceTo(simulator, shrineExpiry - 2.0 * FRAME);
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        ActionRecord boundary = named(
                records, "Tri-Karma Purification").get(2);
        assertTrue(boundary.time > shrineExpiry,
                "Boundary Tri-Karma lands after Shrine expiry");
        assertClose(0.25296 + 0.1152,
                boundary.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Tri-Karma retains trigger-time Shrine and A4 snapshot");
        advanceTo(simulator,
                shrineExpiry - 2.0 * FRAME + reducedInterval);
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        ActionRecord postShrine = named(
                records, "Tri-Karma Purification").get(3);
        assertClose(0.0,
                postShrine.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "Reaction after Shrine expiry receives no Shrine bonus");

        Nahida c1 = nahida(1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.BURST);
        assertClose(66.0 * FRAME + 15.0 + 5.6848,
                c1.getShrineExpirationTime(),
                "C1 contributes one virtual Hydro count");

        Nahida c5 = nahida(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(66.0 * FRAME + 15.0 + 6.688,
                c5.getShrineExpirationTime(),
                "C5 raises the C1 Hydro extension to Talent 12");
    }

    private static void testAscensionAndConstellationEffects() {
        Nahida c3 = nahida(3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        assertClose(1.968, named(
                c3Records, "All Schemes to Know Press")
                        .get(0).action.getDamagePercent(),
                "C3 raises Tap Skill to Talent 12");
        notifyBloom(c3Simulator, c3);
        c3Simulator.advanceTime(3.0 * FRAME);
        ActionRecord c3TriKarma = named(
                c3Records, "Tri-Karma Purification").get(0);
        assertClose(2.064, c3TriKarma.action.getDamagePercent(),
                "C3 raises Tri-Karma ATK ratio to Talent 12");
        assertClose(115.2 * 4.128,
                c3TriKarma.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "C3 raises Tri-Karma EM ratio to Talent 12");

        Nahida c4 = nahida(4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        perform(c4Simulator, CharacterActionKey.SKILL);
        assertClose(215.2,
                c4.getEffectiveStats(
                        c4Simulator.getCurrentTime()).get(
                                StatType.ELEMENTAL_MASTERY),
                "C4 grants 100 EM for one marked fixed target");
        advanceTo(c4Simulator, c4.getSeedExpirationTime());
        assertClose(115.2,
                c4.getEffectiveStats(
                        c4Simulator.getCurrentTime()).get(
                                StatType.ELEMENTAL_MASTERY),
                "C4 expires with the fixed target Seed");

        Nahida c6 = nahida(6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        perform(c6Simulator, CharacterActionKey.BURST);
        for (int index = 0; index < 7; index++) {
            perform(c6Simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> oblivion = named(
                c6Records,
                "Tri-Karma Purification: Karmic Oblivion");
        assertEquals(6, oblivion.size(),
                "C6 stops after six Karmic Oblivion triggers");
        assertEquals(6, c6.getC6TriggerCount(),
                "C6 stores its six-trigger cap");
        for (ActionRecord record : oblivion) {
            assertClose(2.0, record.action.getDamagePercent(),
                    "C6 Karmic Oblivion ATK ratio");
            assertEquals(ICDTag.Nahida_C6,
                    record.action.getICDTag(),
                    "C6 uses its independent typed ICD tag");
            assertEquals(ActionType.SKILL,
                    record.action.getActionType(),
                    "C6 counts as Elemental Skill damage");
        }
    }

    private static void testSnapshotRestore() {
        Nahida owner = nahida(0);
        CombatSimulator simulator = simulatorWith(owner);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        notifyBloom(simulator, owner);
        SimulatorSnapshot triggerSnapshot = simulator.saveSnapshot();
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(1, named(
                records, "Tri-Karma Purification").size(),
                "Live pending Tri-Karma resolves once");
        simulator.restoreSnapshot(triggerSnapshot);
        simulator.restoreSnapshot(triggerSnapshot);
        notifyBloom(simulator, owner);
        simulator.advanceTime(3.0 * FRAME);
        assertEquals(2, named(
                records, "Tri-Karma Purification").size(),
                "Restored trigger cooldown blocks duplicate scheduling");

        SimulatorSnapshot particleSnapshot = simulator.saveSnapshot();
        double arrival = simulator.getCurrentTime() + 100.0 * FRAME;
        advanceTo(simulator, arrival);
        assertEquals(1, particles.size(),
                "Live pending particle packet resolves once");
        simulator.restoreSnapshot(particleSnapshot);
        simulator.restoreSnapshot(particleSnapshot);
        advanceTo(simulator, arrival);
        assertEquals(2, particles.size(),
                "Restored particle packet resolves once");

        Nahida comboOwner = nahida(0);
        CombatSimulator comboSimulator = simulatorWith(comboOwner);
        List<ActionRecord> comboRecords = captureActions(comboSimulator);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot comboSnapshot = comboSimulator.saveSnapshot();
        perform(comboSimulator, CharacterActionKey.NORMAL);
        comboSimulator.restoreSnapshot(comboSnapshot);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(comboRecords, "Akara N2").size(),
                "Rollback preserves Nahida's Normal sequence");
    }

    private static Nahida nahida(int constellation) {
        return new Nahida(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Nahida nahida,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(nahida);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.NAHIDA);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.NAHIDA,
                CharacterActionRequest.of(key));
    }

    private static void notifyBloom(
            CombatSimulator simulator,
            Character source) {
        simulator.notifyReaction(new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE,
                1.0,
                0.0,
                "Bloom",
                ReactionResult.Kind.BLOOM), source);
    }

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator) {
        StatsContainer stats = character.getEffectiveStats(
                simulator.getCurrentTime());
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            buff.apply(stats, simulator.getCurrentTime());
        }
        return stats;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(action, damage, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
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

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertTrue(lines.size() > 2, path + " has data rows");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Nahida,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Nahida/Nahida_Status.csv",
                "config/characters/Nahida/Nahida_Multipliers.csv"
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
        throw new AssertionError("Nahida CSVs missing key " + key);
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
                Element element,
                double elementalMastery) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
            baseStats.add(
                    StatType.ELEMENTAL_MASTERY,
                    elementalMastery);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return null;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}
