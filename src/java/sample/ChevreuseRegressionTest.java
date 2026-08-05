package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.character.Chevreuse;
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

/** Focused regression checks for Chevreuse's represented fixed-target slice. */
public final class ChevreuseRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ChevreuseRegressionTest() {
    }

    /** Runs identity, actions, support, constellations, restore, and guards. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testNormalStringAndReset();
        testPressHoldArkheAndParticles();
        testActualOverloadA1AndC1();
        testOverchargedA4AndC2();
        testBurstSnapshotAndC4C5();
        testSnapshotRestore();
        testGuards();
        System.out.println("ChevreuseRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.CHEVREUSE,
                CharacterId.fromNumericId(57),
                "Chevreuse numeric identity");
        assertEquals(CharacterId.CHEVREUSE,
                CharacterId.fromName("Chevreuse"),
                "Chevreuse exact-name identity");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.CHEVREUSE.getRegion(),
                "Chevreuse region");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromNumericId(58),
                "Chevreuse next identity remains unassigned");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromName("chevreuse"),
                "Chevreuse lookup remains case-sensitive");

        Chevreuse chevreuse = new Chevreuse(null, null, 0);
        assertEquals(CharacterId.CHEVREUSE,
                chevreuse.getCharacterId(),
                "Chevreuse runtime identity");
        assertEquals(Element.PYRO, chevreuse.getElement(),
                "Chevreuse element");
        assertClose(11962.0,
                chevreuse.getBaseStats().get(StatType.BASE_HP),
                "Chevreuse base HP");
        assertClose(193.0,
                chevreuse.getBaseStats().get(StatType.BASE_ATK),
                "Chevreuse base ATK");
        assertClose(605.0,
                chevreuse.getBaseStats().get(StatType.BASE_DEF),
                "Chevreuse base DEF");
        assertClose(0.24,
                chevreuse.getBaseStats().get(StatType.HP_PERCENT),
                "Chevreuse ascension HP");
        assertClose(60.0, chevreuse.getEnergyCost(),
                "Chevreuse Burst cost");
        assertCsvShape(Path.of(
                "config/characters/Chevreuse/Chevreuse_Status.csv"), 24);
        assertCsvShape(Path.of(
                "config/characters/Chevreuse/Chevreuse_Multipliers.csv"),
                16);
        assertCsvValue("N3 Hit 2", 0.596225);
        assertCsvValue("Explosive Grenade C3", 7.363200);
    }

    private static void testNormalStringAndReset() {
        Chevreuse chevreuse = new Chevreuse(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.ELECTRO);
        CombatSimulator simulator = simulatorWith(chevreuse, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        List<ActionRecord> normals = named(records,
                "Line Bayonet Thrust EX");
        assertEquals(5, normals.size(),
                "Chevreuse four-Normal string contains five hits");
        double[] expected = {
            0.976108, 0.905940, 0.507895, 0.596225, 1.419456
        };
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    normals.get(index).action.getDamagePercent(),
                    "Chevreuse Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Chevreuse Normal element " + index);
        }
        assertClose(11.0 * FRAME, normals.get(0).time,
                "Chevreuse N1 impact frame");
        assertClose((33.0 + 12.0) * FRAME, normals.get(1).time,
                "Chevreuse N2 impact frame");
        assertClose((66.0 + 15.0) * FRAME, normals.get(2).time,
                "Chevreuse N3 first impact frame");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.CHEVREUSE);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertEquals(2, named(records,
                "Line Bayonet Thrust EX N1").size(),
                "Chevreuse switch-out resets Normal string");
    }

    private static void testPressHoldArkheAndParticles() {
        Chevreuse press = new Chevreuse(null, null, 0);
        CombatSimulator pressSimulator = simulatorWith(press);
        List<ActionRecord> pressRecords = captureActions(pressSimulator);
        List<Double> particles = capturePyroParticles(pressSimulator);
        perform(pressSimulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        advanceTo(pressSimulator, 2.3);
        assertClose(1.958400,
                named(pressRecords,
                        "Short-Range Rapid Interdiction Fire")
                        .get(0).action.getDamagePercent(),
                "Chevreuse Press Skill Talent 9");
        List<ActionRecord> arkhe = named(pressRecords, "Surging Blade");
        assertEquals(1, arkhe.size(),
                "Chevreuse Press schedules one Surging Blade");
        assertClose(59.0 * FRAME, arkhe.get(0).time,
                "Chevreuse Press Arkhe frame");
        assertClose(0.489600,
                arkhe.get(0).action.getDamagePercent(),
                "Chevreuse Arkhe Talent 9");
        assertEquals(1, particles.size(),
                "Chevreuse Skill emits one particle packet");
        assertClose(4.0, particles.get(0),
                "Chevreuse Skill emits four Pyro particles");

        Chevreuse hold = new Chevreuse(null, null, 0);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureActions(holdSimulator);
        perform(holdSimulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertClose(2.937600,
                named(holdRecords,
                        "Short-Range Rapid Interdiction Fire [Hold]")
                        .get(0).action.getDamagePercent(),
                "Chevreuse minimum Hold Talent 9");
        advanceTo(holdSimulator, 1.0);
        assertClose(55.0 * FRAME,
                named(holdRecords, "Surging Blade").get(0).time,
                "Chevreuse minimum Hold Arkhe frame");
    }

    private static void testActualOverloadA1AndC1() {
        Chevreuse chevreuse = new Chevreuse(null, null, 1);
        TestCharacter electro = new TestCharacter(
                CharacterId.NOELLE, Element.ELECTRO);
        CombatSimulator simulator = simulatorWith(chevreuse, electro);
        simulator.switchCharacter(CharacterId.NOELLE);
        electro.spendEnergy(20.0);
        triggerActualOverload(
                simulator, electro, Element.PYRO, Element.ELECTRO);
        assertTrue(chevreuse.hasOverchargedBall(),
                "Actual Overload grants an Overcharged Ball");
        assertClose(6.0, electro.getTotalFlatEnergy(),
                "Chevreuse C1 restores active non-owner Energy");
        StatsContainer shred = effectiveStats(simulator, electro);
        assertClose(0.40, shred.get(StatType.PYRO_RES_SHRED),
                "Chevreuse A1 applies Pyro RES reduction");
        assertClose(0.40, shred.get(StatType.ELECTRO_RES_SHRED),
                "Chevreuse A1 applies Electro RES reduction");

        triggerActualOverload(
                simulator, electro, Element.PYRO, Element.ELECTRO);
        assertClose(6.0, electro.getTotalFlatEnergy(),
                "Chevreuse C1 respects its ten-second ICD");
        simulator.advanceTime(10.0);
        triggerActualOverload(
                simulator, electro, Element.PYRO, Element.ELECTRO);
        assertClose(12.0, electro.getTotalFlatEnergy(),
                "Chevreuse C1 refreshes at the exact ICD boundary");

        Chevreuse mixed = new Chevreuse(null, null, 1);
        TestCharacter mixedElectro = new TestCharacter(
                CharacterId.NOELLE, Element.ELECTRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator mixedSimulator = simulatorWith(
                mixed, mixedElectro, hydro);
        mixedSimulator.switchCharacter(CharacterId.NOELLE);
        mixedElectro.spendEnergy(20.0);
        triggerActualOverload(mixedSimulator, mixedElectro,
                Element.PYRO, Element.ELECTRO);
        assertTrue(mixed.hasOverchargedBall(),
                "Mixed party still receives an Overcharged Ball");
        assertClose(0.0, mixedElectro.getTotalFlatEnergy(),
                "Mixed party disables Chevreuse C1");
        assertTrue(!hasTeamBuff(mixedSimulator,
                        BuffId.CHEVREUSE_A1_COORDINATED_TACTICS),
                "Mixed party disables Chevreuse A1");
    }

    private static void testOverchargedA4AndC2() {
        Chevreuse chevreuse = new Chevreuse(
                null, null, mechanics.data.TalentDataManager.getInstance(),
                2, () -> 0.0);
        chevreuse.addBuff(new SimpleBuff(
                "Chevreuse test HP",
                20.0,
                0.0,
                stats -> stats.add(StatType.HP_FLAT, 30000.0)));
        TestCharacter electro = new TestCharacter(
                CharacterId.NOELLE, Element.ELECTRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(
                chevreuse, electro, hydro);
        List<ActionRecord> records = captureActions(simulator);
        triggerActualOverload(
                simulator, chevreuse, Element.ELECTRO, Element.PYRO);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertTrue(!chevreuse.hasOverchargedBall(),
                "Overcharged Hold consumes the stored Ball");
        assertClose(4.800800,
                named(records,
                        "Short-Range Rapid Interdiction Fire [Overcharged]")
                        .get(0).action.getDamagePercent(),
                "Chevreuse Overcharged Ball Talent 9");
        assertClose(0.40,
                effectiveStats(simulator, electro)
                        .get(StatType.ATK_PERCENT),
                "Chevreuse A4 caps the snapshotted ATK bonus");
        assertClose(0.0,
                effectiveStats(simulator, hydro)
                        .get(StatType.ATK_PERCENT),
                "Chevreuse A4 excludes non-Pyro/Electro members");
        chevreuse.removeBuff(BuffId.CUSTOM);
        assertClose(0.40,
                effectiveStats(simulator, electro)
                        .get(StatType.ATK_PERCENT),
                "Chevreuse A4 preserves its Max HP snapshot");

        advanceTo(simulator, 1.1);
        List<ActionRecord> explosions = named(records,
                "Sniper Induced Explosion");
        assertEquals(2, explosions.size(),
                "Chevreuse C2 creates two fixed-target explosions");
        assertClose(1.20,
                explosions.get(0).action.getDamagePercent(),
                "Chevreuse C2 explosion multiplier");
        assertClose(19.0 * FRAME + 0.6,
                explosions.get(0).time,
                "Chevreuse C2 minimum random delay");
    }

    private static void testBurstSnapshotAndC4C5() {
        Chevreuse chevreuse = new Chevreuse(
                null, null, mechanics.data.TalentDataManager.getInstance(),
                5, () -> 0.0);
        chevreuse.addBuff(new SimpleBuff(
                "Chevreuse Burst snapshot test",
                0.8,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 0.50)));
        CombatSimulator simulator = simulatorWith(chevreuse);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        ActionRecord burst = named(records, "Explosive Grenade").get(0);
        assertClose(7.363200, burst.action.getDamagePercent(),
                "Chevreuse C3 Burst uses Talent 12");
        assertClose(59.0 * FRAME, burst.time,
                "Chevreuse Burst impact frame");
        assertClose(0.50,
                burst.action.getStatSnapshot().get(StatType.ATK_PERCENT),
                "Chevreuse Burst snapshots at frame 43 before buff expiry");
        assertEquals(2,
                chevreuse.getC4ShotsRemaining(simulator.getCurrentTime()),
                "Chevreuse C4 starts with two free Hold shots");

        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertEquals(0,
                chevreuse.getC4ShotsRemaining(simulator.getCurrentTime()),
                "Chevreuse C4 consumes two Hold shots");
        assertClose(3.456000,
                named(records,
                        "Short-Range Rapid Interdiction Fire [Hold]")
                        .get(0).action.getDamagePercent(),
                "Chevreuse C5 Hold Skill uses Talent 12");
        assertClose(0.0,
                chevreuse.getSkillCDRemaining(simulator.getCurrentTime()),
                "Chevreuse C4 free Hold shots do not start cooldown");

        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        assertTrue(chevreuse.getSkillCDRemaining(
                        simulator.getCurrentTime()) > 14.0,
                "Chevreuse third Hold starts ordinary cooldown");

        Chevreuse expiry = new Chevreuse(null, null, 4);
        CombatSimulator expirySimulator = simulatorWith(expiry);
        perform(expirySimulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        advanceTo(expirySimulator, 6.0);
        assertEquals(0, expiry.getC4ShotsRemaining(6.0),
                "Chevreuse C4 expires at the exact six-second boundary");
    }

    private static void testSnapshotRestore() {
        Chevreuse chevreuse = new Chevreuse(null, null, 0);
        CombatSimulator simulator = simulatorWith(chevreuse);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        int resolvedAtSnapshot = records.size();
        advanceTo(simulator, 2.3);
        assertEquals(2, records.size(),
                "Chevreuse live branch resolves Skill and Arkhe once");
        assertEquals(1, particles.size(),
                "Chevreuse live branch resolves one particle packet");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 2.3);
        assertEquals(4 - resolvedAtSnapshot, records.size(),
                "Chevreuse restore replays only pending Arkhe once");
        assertEquals(2, particles.size(),
                "Chevreuse restore replays only pending particles once");
        assertEquals(0, chevreuse.getPendingHitCount(),
                "Chevreuse has no pending hits after restored work ends");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Chevreuse(null, null, -1),
                "Chevreuse rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Chevreuse(null, null, 7),
                "Chevreuse rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Chevreuse(null, null,
                        mechanics.data.TalentDataManager.getInstance(),
                        0, null),
                "Chevreuse rejects a null C2 random source");
        Chevreuse chevreuse = new Chevreuse(null, null, 0);
        CombatSimulator simulator = simulatorWith(chevreuse);
        assertThrows(IllegalArgumentException.class,
                () -> chevreuse.onAction(null, simulator),
                "Chevreuse rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.of(
                        CharacterActionKey.CHARGE)),
                "Chevreuse rejects deferred Charged attacks");
        Chevreuse invalidRandom = new Chevreuse(
                null, null, mechanics.data.TalentDataManager.getInstance(),
                2, () -> 1.0);
        CombatSimulator invalidRandomSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidRandomSimulator,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Chevreuse rejects out-of-range C2 random draws");
        Chevreuse external = new Chevreuse(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Chevreuse rejects binding outside simulator party");
        Chevreuse reused = new Chevreuse(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Chevreuse rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!chevreuse.acceptsCharacterState(foreignState),
                "Chevreuse rejects another instance snapshot payload");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        for (Element element : Element.values()) {
            enemy.setRes(element.getBonusStatType(), 0.0);
        }
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionRequest request) {
        simulator.performAction(CharacterId.CHEVREUSE, request);
    }

    private static void triggerActualOverload(
            CombatSimulator simulator,
            Character trigger,
            Element aura,
            Element attackElement) {
        simulator.getEnemy().setAura(aura, 1.0);
        AttackAction action = new AttackAction(
                "Chevreuse regression Overload trigger",
                1.0,
                attackElement,
                StatType.BASE_ATK,
                attackElement.getBonusStatType(),
                0.0,
                ActionType.OTHER);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        simulator.performActionWithoutTimeAdvance(
                trigger.getCharacterId(), action);
    }

    private static StatsContainer effectiveStats(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static boolean hasTeamBuff(
            CombatSimulator simulator,
            BuffId id) {
        for (Buff buff : simulator.getTeamBuffList()) {
            if (buff.getId() == id
                    && !buff.isExpired(simulator.getCurrentTime())) {
                return true;
            }
        }
        return false;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CHEVREUSE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> capturePyroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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
            assertTrue(lines.get(index).startsWith("Chevreuse,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Chevreuse/Chevreuse_Status.csv",
                "config/characters/Chevreuse/Chevreuse_Multipliers.csv"
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
        throw new AssertionError("Chevreuse CSVs missing key " + key);
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

    /** Captured Chevreuse action and its resolved simulation time. */
    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    /** Minimal typed party member used for composition and C1 checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
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
