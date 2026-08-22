package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.character.Varka;
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

/** Focused regression checks for Varka's fixed-target Sturm und Drang slice. */
public final class VarkaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private VarkaRegressionTest() {
    }

    /** Runs identity, action, stance, passive, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testClaymoreBasicsAndTiming();
        testSturmConversionCompositionAndParticles();
        testFourWindsAzureAndConstellations();
        testBurstA4C4AndC6();
        testSnapshotAndFailClosedBoundaries();
        System.out.println("VarkaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Varka varka = new Varka(null, null, 6);
        assertEquals(CharacterId.VARKA, varka.getCharacterId(),
                "Varka typed identity");
        assertEquals(CharacterId.VARKA,
                CharacterId.fromName("Varka"),
                "Varka name lookup");
        assertEquals(CharacterId.VARKA,
                CharacterId.fromNumericId(105),
                "Varka numeric lookup");
        assertEquals(105, CharacterId.VARKA.getNumericId(),
                "Varka stable numeric id");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.VARKA.getRegion(),
                "Varka region");
        assertEquals(Element.ANEMO, varka.getElement(),
                "Varka element");
        assertClose(12613.0,
                varka.getBaseStats().get(StatType.BASE_HP),
                "Varka base HP");
        assertClose(353.0,
                varka.getBaseStats().get(StatType.BASE_ATK),
                "Varka base ATK");
        assertClose(795.0,
                varka.getBaseStats().get(StatType.BASE_DEF),
                "Varka base DEF");
        assertClose(0.884,
                varka.getBaseStats().get(StatType.CRIT_DMG),
                "Varka base and ascension CRIT DMG");
        assertClose(60.0, varka.getEnergyCost(),
                "Varka Burst Energy cost");
        assertClose(16.0, varka.getSkillCD(),
                "Varka Skill cooldown");
        assertClose(15.0, varka.getBurstCD(),
                "Varka Burst cooldown");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Varka(null, null, constellation)
                            .getConstellation(),
                    "Varka explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Varka/Varka_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Varka/Varka_Multipliers.csv"), 68);
        assertCsvValue("Sturm N1 C3", 1.845813);
        assertCsvValue("Azure Devour Element Hit C3", 1.872000);
        assertCsvValue("Northwind Avatar Hit 1 C5", 6.739200);
        assertCsvValue("C2 Northwind Multiplier", 8.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Varka(null, null, -1),
                "Varka rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Varka(null, null, 7),
                "Varka rejects constellation above C6");
    }

    private static void testClaymoreBasicsAndTiming() {
        Varka varka = new Varka(null, null, 0);
        CombatSimulator simulator = simulatorWith(varka);
        List<ActionRecord> records = captureVarkaActions(simulator);
        double castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord n1 = onlyNamed(records, "Favonius Bladework N1");
        assertClose(castTime + 19.0 * FRAME, n1.time,
                "Varka N1 hitmark");
        assertClose(castTime + (46.0 + 6.0) * FRAME,
                simulator.getCurrentTime(),
                "Varka N1 recovery");
        assertClose(1.202633, n1.action.getDamagePercent(),
                "Varka N1 multiplier");
        assertEquals(Element.PHYSICAL, n1.action.getElement(),
                "Varka N1 element");
        assertEquals(ICDTag.NormalAttack, n1.action.getICDTag(),
                "Varka N1 ICD group");

        records.clear();
        castTime = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> n2 = prefix(records,
                "Favonius Bladework N2-");
        assertEquals(2, n2.size(), "Varka N2 hit count");
        assertClose(castTime + 18.0 * FRAME, n2.get(0).time,
                "Varka N2 first hitmark");
        assertClose(castTime + 28.0 * FRAME, n2.get(1).time,
                "Varka N2 second hitmark");
        assertClose(0.818478,
                n2.get(1).action.getDamagePercent(),
                "Varka N2 second multiplier");

        Varka basics = new Varka(null, null, 0);
        CombatSimulator basicsSimulator = simulatorWith(basics);
        List<ActionRecord> basicsRecords =
                captureVarkaActions(basicsSimulator);
        perform(basicsSimulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = prefix(
                basicsRecords, "Favonius Bladework Charged Hit");
        assertEquals(2, charged.size(),
                "Varka physical Charged hit count");
        assertClose(41.0 * FRAME, charged.get(0).time,
                "Varka physical Charged hitmark");
        assertClose((67.0 + 9.0) * FRAME,
                basicsSimulator.getCurrentTime(),
                "Varka physical Charged recovery");
        assertClose(1.573364,
                charged.get(0).action.getDamagePercent(),
                "Varka physical Charged multiplier");

        double plungeCast = basicsSimulator.getCurrentTime();
        perform(basicsSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = onlyNamed(
                basicsRecords, "Favonius Bladework High Plunge");
        assertClose(plungeCast + 48.0 * FRAME, plunge.time,
                "Varka high-Plunge hitmark");
        assertClose(plungeCast + 74.0 * FRAME,
                basicsSimulator.getCurrentTime(),
                "Varka high-Plunge recovery");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Varka high Plunge has no ICD");
    }

    private static void testSturmConversionCompositionAndParticles() {
        Varka varka = new Varka(null, null, 0);
        TestCharacter anemo = new TestCharacter(
                CharacterId.SUCROSE, Element.ANEMO);
        TestCharacter pyroOne = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter pyroTwo = new TestCharacter(
                CharacterId.DILUC, Element.PYRO);
        CombatSimulator simulator = simulatorWith(
                varka, anemo, pyroOne, pyroTwo);
        List<ActionRecord> records = captureVarkaActions(simulator);
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        assertClose((55.0 + 9.0) * FRAME,
                simulator.getCurrentTime(),
                "Varka initial Skill recovery");
        ActionRecord skill = onlyNamed(records, "Windbound Execution");
        assertClose(40.0 * FRAME, skill.time,
                "Varka initial Skill hitmark");
        assertClose(4.732800, skill.action.getDamagePercent(),
                "Varka initial Skill multiplier");
        assertTrue(varka.isSturmUndDrangActive(),
                "Varka enters Sturm und Drang on the Skill hit");
        assertEquals(Element.PYRO, varka.getConversionElement(),
                "Varka uses source PHEC priority");
        assertTrue(varka.getSkillCDRemaining(
                simulator.getCurrentTime()) < 11.0,
                "Stance exposes its first Four Winds recharge");

        records.clear();
        double normalCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord sturmN1 = onlyNamed(
                records, "Sturm und Drang N1");
        assertEquals(Element.PYRO, sturmN1.action.getElement(),
                "Sturm N1 uses converted element");
        assertEquals(ICDTag.Varka_NormalElement,
                sturmN1.action.getICDTag(),
                "Sturm converted Normal private ICD");
        assertClose(1.503291 * 2.2,
                sturmN1.action.getDamagePercent(),
                "A1 composition multiplier combines both conditions");
        assertClose(0.0353,
                sturmN1.action.getExtraBonuses().getOrDefault(
                        StatType.DMG_BONUS_ALL, 0.0),
                "A1 converts live ATK into elemental damage bonus");
        assertClose(normalCast + 19.0 * FRAME, sturmN1.time,
                "Sturm N1 hitmark");

        records.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> sturmN2 = prefix(
                records, "Sturm und Drang N2-");
        assertEquals(2, sturmN2.size(), "Sturm N2 hit count");
        assertEquals(Element.ANEMO,
                sturmN2.get(0).action.getElement(),
                "Sturm N2 first hit alternates to Anemo");
        assertEquals(Element.PYRO,
                sturmN2.get(1).action.getElement(),
                "Sturm N2 second hit alternates to PHEC");
        assertEquals(ICDTag.Varka_NormalWind,
                sturmN2.get(0).action.getICDTag(),
                "Sturm Anemo Normal private ICD");

        advanceTo(simulator, 141.0 * FRAME);
        assertEquals(1, particles.size(),
                "Varka initial Skill emits one particle packet");
        assertClose(6.0, particles.get(0).count,
                "Varka Skill particle count");
        assertClose(140.0 * FRAME, particles.get(0).time,
                "Varka Skill particle travel timing");
    }

    private static void testFourWindsAzureAndConstellations() {
        Varka skillVarka = new Varka(null, null, 2);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator skillSimulator = simulatorWith(
                skillVarka, pyro);
        List<ActionRecord> skillRecords =
                captureVarkaActions(skillSimulator);
        performSkill(skillSimulator);
        assertEquals(1, skillVarka.getFourWindsCharges(),
                "C1 grants one immediate Four Winds charge");
        skillRecords.clear();
        performSkill(skillSimulator);
        List<ActionRecord> ascension = prefix(
                skillRecords, "Four Winds' Ascension Hit");
        assertEquals(2, ascension.size(),
                "Four Winds has two fixed-target hits");
        assertEquals(Element.PYRO,
                ascension.get(0).action.getElement(),
                "Four Winds first hit uses converted element");
        assertEquals(Element.ANEMO,
                ascension.get(1).action.getElement(),
                "Four Winds second hit uses Anemo");
        assertEquals(ICDType.None,
                ascension.get(0).action.getICDType(),
                "Four Winds has no ICD");
        assertClose(2.987920 * 2.0,
                ascension.get(0).action.getDamagePercent(),
                "C1 doubles the first special action");
        assertEquals(1, named(
                skillRecords, "Varka C2 Northwind").size(),
                "C2 adds one 800-percent Anemo hit");
        assertClose(8.0, onlyNamed(
                skillRecords, "Varka C2 Northwind")
                        .action.getDamagePercent(),
                "C2 multiplier");
        assertEquals(0, skillVarka.getFourWindsCharges(),
                "Four Winds consumes its non-free charge");

        Varka chargeVarka = new Varka(null, null, 3);
        CombatSimulator chargeSimulator = simulatorWith(
                chargeVarka,
                new TestCharacter(CharacterId.BENNETT, Element.PYRO));
        List<ActionRecord> chargeRecords =
                captureVarkaActions(chargeSimulator);
        performSkill(chargeSimulator);
        chargeRecords.clear();
        perform(chargeSimulator, CharacterActionKey.CHARGE);
        List<ActionRecord> azure = prefix(
                chargeRecords, "Azure Devour Hit");
        assertEquals(4, azure.size(),
                "Azure Devour has four fixed-target hits");
        assertClose(1.872000 * 2.0,
                azure.get(0).action.getDamagePercent(),
                "C3 and C1 affect Azure Devour");
        assertEquals(ICDTag.Varka_ExtraElement,
                azure.get(0).action.getICDTag(),
                "Azure converted hit private ICD");
        assertEquals(ICDTag.Varka_ExtraWind,
                azure.get(1).action.getICDTag(),
                "Azure Anemo hit private ICD");

        Varka c6 = new Varka(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(
                c6,
                new TestCharacter(CharacterId.BENNETT, Element.PYRO));
        List<ActionRecord> c6Records =
                captureVarkaActions(c6Simulator);
        performSkill(c6Simulator);
        performSkill(c6Simulator);
        c6Records.clear();
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertEquals(4, prefix(
                c6Records, "Azure Devour Hit").size(),
                "C6 special Skill grants a free Azure window");
        assertClose(0.0,
                c6.getSkillCDRemaining(c6Simulator.getCurrentTime()),
                "C6 Azure grants a free Skill window");
    }

    private static void testBurstA4C4AndC6() {
        Varka varka = new Varka(null, null, 6);
        TestCharacter pyro = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        TestCharacter hydro = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        TestCharacter electro = new TestCharacter(
                CharacterId.FISCHL, Element.ELECTRO);
        CombatSimulator simulator = simulatorWith(
                varka, pyro, hydro, electro);
        List<ActionRecord> records = captureVarkaActions(simulator);
        varka.receiveFlatEnergy(60.0);
        perform(simulator, CharacterActionKey.BURST);
        List<ActionRecord> burst = prefix(
                records, "Northwind Avatar Hit");
        assertEquals(2, burst.size(),
                "Northwind Avatar has two hits");
        assertClose(112.0 * FRAME, burst.get(0).time,
                "Northwind Avatar first hitmark");
        assertClose(131.0 * FRAME, burst.get(1).time,
                "Northwind Avatar second hitmark");
        assertClose(6.739200,
                burst.get(0).action.getDamagePercent(),
                "C5 Burst first multiplier");
        assertEquals(Element.PYRO,
                burst.get(0).action.getElement(),
                "Burst first hit uses conversion when available");
        assertEquals(Element.ANEMO,
                burst.get(1).action.getElement(),
                "Burst second hit uses Anemo");
        assertClose(0.0, varka.getCurrentEnergy(),
                "Burst spends Energy at frame four");
        assertClose(152.0 * FRAME,
                simulator.getCurrentTime(),
                "Northwind Avatar recovery");

        notifySwirl(simulator, varka, Element.PYRO);
        notifySwirl(simulator, pyro, Element.HYDRO);
        notifySwirl(simulator, hydro, Element.ELECTRO);
        notifySwirl(simulator, electro, Element.CRYO);
        assertEquals(4, varka.getA4Stacks(
                simulator.getCurrentTime()),
                "A4 accepts one stack per party source");

        records.clear();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = onlyNamed(
                records, "Favonius Bladework N1");
        assertClose(0.30,
                normal.action.getExtraBonuses().getOrDefault(
                        StatType.DMG_BONUS_ALL, 0.0),
                "A4 grants four damage stacks to eligible attacks");
        assertClose(0.80,
                normal.action.getExtraBonuses().getOrDefault(
                        StatType.CRIT_DMG, 0.0),
                "C6 converts four A4 stacks into CRIT DMG");

        StatsContainer allyStats = new StatsContainer();
        for (Buff buff : simulator.getApplicableBuffs(pyro)) {
            if (!buff.isExpired(simulator.getCurrentTime())) {
                buff.apply(allyStats, simulator.getCurrentTime());
            }
        }
        assertClose(0.20,
                allyStats.get(StatType.ANEMO_DMG_BONUS),
                "C4 grants team Anemo damage after Varka Swirl");
        assertClose(0.20,
                allyStats.get(StatType.PYRO_DMG_BONUS),
                "C4 grants the swirled team damage type");

        simulator.advanceTime(8.01);
        assertEquals(0, varka.getA4Stacks(
                simulator.getCurrentTime()),
                "A4 stacks share an eight-second expiry");
    }

    private static void testSnapshotAndFailClosedBoundaries() {
        Varka varka = new Varka(null, null, 0);
        CombatSimulator simulator = simulatorWith(
                varka,
                new TestCharacter(CharacterId.BENNETT, Element.PYRO));
        List<ParticleRecord> particles = captureAnemoParticles(simulator);
        performSkill(simulator);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 141.0 * FRAME);
        assertEquals(1, particles.size(),
                "Live branch resolves one delayed particle packet");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        particles.clear();
        advanceTo(simulator, 141.0 * FRAME);
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs one particle packet");
        assertTrue(varka.isSturmUndDrangActive(),
                "Snapshot restores active stance state");

        varka.onSwitchOut(simulator);
        assertTrue(!varka.isSturmUndDrangActive(),
                "Switching ends local stance state");
        assertThrows(IllegalArgumentException.class,
                () -> varka.onAction(
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD),
                        simulator),
                "Varka rejects unsupported Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> varka.onAction(null, simulator),
                "Varka rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Varka rejects movement actions");

        Varka noPhec = new Varka(null, null, 0);
        CombatSimulator noPhecSimulator = simulatorWith(noPhec);
        List<ActionRecord> noPhecRecords =
                captureVarkaActions(noPhecSimulator);
        performSkill(noPhecSimulator);
        assertEquals(Element.PHYSICAL,
                noPhec.getConversionElement(),
                "Missing PHEC composition fails closed to Physical");
        noPhecRecords.clear();
        perform(noPhecSimulator, CharacterActionKey.NORMAL);
        assertEquals(Element.PHYSICAL, onlyNamed(
                noPhecRecords, "Sturm und Drang N1")
                        .action.getElement(),
                "No-PHEC stance does not invent an infusion");

        Varka reused = new Varka(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Varka rejects cross-simulator reuse");
        Varka foreign = new Varka(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!varka.acceptsCharacterState(foreignState),
                "Varka rejects another owner's snapshot payload");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ANEMO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.VARKA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.VARKA,
                CharacterActionRequest.skill(SkillActionMode.PRESS));
    }

    private static void notifySwirl(
            CombatSimulator simulator,
            Character source,
            Element element) {
        simulator.notifyReaction(ReactionResult.transform(
                1.0,
                "Swirl",
                ReactionResult.Kind.SWIRL,
                element), source);
    }

    private static List<ActionRecord> captureVarkaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.VARKA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureAnemoParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ANEMO) {
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

    private static List<ActionRecord> prefix(
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

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = named(records, name);
        assertEquals(1, selected.size(), name + " record count");
        return selected.get(0);
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
            assertTrue(lines.get(index).startsWith("Varka,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Varka/Varka_Status.csv",
                "config/characters/Varka/Varka_Multipliers.csv"
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
        throw new AssertionError("Varka CSVs missing key " + key);
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
