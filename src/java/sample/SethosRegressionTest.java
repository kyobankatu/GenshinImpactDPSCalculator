package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Sethos;
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

/** Focused regressions for Sethos's fixed-target offensive slice. */
public final class SethosRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private SethosRegressionTest() {
    }

    /** Runs identity, normal, boundary, rollback, and abnormal checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testNormalStringAndC3();
        testChargedAndShadowpiercingEnergy();
        testA4C1C2AndC6Boundaries();
        testSkillReactionRefundParticlesAndCooldown();
        testBurstDuskBoltExpiryAndSwap();
        testSnapshotRestore();
        testBindingAndInputGuards();
        System.out.println("SethosRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.SETHOS, CharacterId.fromNumericId(64),
                "Sethos numeric identity");
        assertEquals(CharacterId.SETHOS, CharacterId.fromName("Sethos"),
                "Sethos exact-name identity");
        assertEquals(CharacterRegion.SUMERU, CharacterId.SETHOS.getRegion(),
                "Sethos region");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("sethos"),
                "Sethos lookup remains case-sensitive");

        Sethos sethos = sethos(0);
        assertEquals(CharacterId.SETHOS, sethos.getCharacterId(),
                "Sethos runtime identity");
        assertEquals(Element.ELECTRO, sethos.getElement(),
                "Sethos element");
        assertClose(9787.0, sethos.getBaseStats().get(StatType.BASE_HP),
                "Sethos base HP");
        assertClose(227.0, sethos.getBaseStats().get(StatType.BASE_ATK),
                "Sethos base ATK");
        assertClose(560.0, sethos.getBaseStats().get(StatType.BASE_DEF),
                "Sethos base DEF");
        assertClose(96.0, sethos.getBaseStats().get(
                StatType.ELEMENTAL_MASTERY),
                "Sethos ascension Elemental Mastery");
        assertClose(60.0, sethos.getEnergyCost(),
                "Sethos Burst cost");
        assertClose(8.0, sethos.getSkillCD(),
                "Sethos Skill cooldown");
        assertClose(15.0, sethos.getBurstCD(),
                "Sethos Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Sethos/Sethos_Status.csv"), 29);
        assertCsvShape(Path.of(
                "config/characters/Sethos/Sethos_Multipliers.csv"), 17);
        assertCsvValue("Shadowpiercing ATK", 2.380000);
        assertCsvValue("Dusk Bolt Elemental Mastery C5", 3.923200);
    }

    private static void testNormalStringAndC3() {
        Sethos c0 = sethos(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c0, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> normals = named(
                records, "Royal Reed Archery N");
        assertEquals(4, normals.size(),
                "Sethos three-step Normal string has four hits");
        double[] multipliers = {
            0.966628, 0.437186, 0.488852, 1.359290
        };
        double[] hitFrames = { 20.0, 41.0, 44.0, 103.0 };
        for (int index = 0; index < normals.size(); index++) {
            assertClose(multipliers[index],
                    normals.get(index).action.getDamagePercent(),
                    "Sethos Normal multiplier " + index);
            assertClose(hitFrames[index] * FRAME,
                    normals.get(index).time,
                    "Sethos Normal timing " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Sethos Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    normals.get(index).action.getActionType(),
                    "Sethos Normal category " + index);
        }
        assertClose(123.0 * FRAME, simulator.getCurrentTime(),
                "Sethos Normal string recovery");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.SETHOS);
        perform(simulator, CharacterActionKey.NORMAL);
        advanceBy(simulator, 2.0 * FRAME);
        assertEquals(2, named(records, "Royal Reed Archery N1").size(),
                "Switch-out resets Sethos Normal progression");

        Sethos c3 = sethos(3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.NORMAL);
        advanceBy(c3Simulator, FRAME);
        assertClose(1.186873,
                named(c3Records, "Royal Reed Archery N1").get(0)
                        .action.getDamagePercent(),
                "C3 routes Normal to Talent 12");
    }

    private static void testChargedAndShadowpiercingEnergy() {
        Sethos charged = sethos(0);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        charged.restoreCurrentEnergy(20.0);
        List<ActionRecord> chargedRecords = captureActions(
                chargedSimulator);
        charged.performFullyChargedAimedShot(chargedSimulator);
        assertClose(25.0 * FRAME, chargedSimulator.getCurrentTime(),
                "A1 caps fully charged recovery reduction at frame sixteen");
        assertClose(10.0, charged.getCurrentEnergy(),
                "Fully charged shot consumes half considered Energy");
        advanceBy(chargedSimulator, FRAME);
        ActionRecord chargedHit = named(
                chargedRecords, "Royal Reed Archery Fully Charged").get(0);
        assertClose(26.0 * FRAME, chargedHit.time,
                "Fully charged projectile impact timing");
        assertClose(2.108000, chargedHit.action.getDamagePercent(),
                "Fully charged multiplier");
        assertEquals(ICDType.None, chargedHit.action.getICDType(),
                "Fully charged shot has no ICD");
        assertClose(1.0, chargedHit.action.getGaugeUnits(),
                "Fully charged shot applies 1U Electro");

        Sethos unaccelerated = sethos(0);
        CombatSimulator unacceleratedSimulator = simulatorWith(unaccelerated);
        unaccelerated.restoreCurrentEnergy(0.0);
        List<ActionRecord> unacceleratedRecords = captureActions(
                unacceleratedSimulator);
        unaccelerated.performFullyChargedAimedShot(
                unacceleratedSimulator);
        assertClose(83.0 * FRAME,
                unacceleratedSimulator.getCurrentTime(),
                "Zero-Energy fully charged recovery");
        advanceBy(unacceleratedSimulator, 2.0 * FRAME);
        assertClose(84.0 * FRAME,
                named(unacceleratedRecords,
                        "Royal Reed Archery Fully Charged").get(0).time,
                "Zero-Energy fully charged impact");

        Sethos shadow = sethos(0);
        CombatSimulator shadowSimulator = simulatorWith(shadow);
        shadow.restoreCurrentEnergy(20.0);
        List<ActionRecord> shadowRecords = captureActions(shadowSimulator);
        perform(shadowSimulator, CharacterActionKey.CHARGE);
        ActionRecord shadowHit = named(
                shadowRecords, "Royal Reed Archery Shadowpiercing").get(0);
        assertClose(37.0 * FRAME, shadowSimulator.getCurrentTime(),
                "A1 accelerated Shadowpiercing recovery");
        assertClose(36.0 * FRAME, shadowHit.time,
                "A1 accelerated Shadowpiercing impact");
        assertClose(0.0, shadow.getCurrentEnergy(),
                "Shadowpiercing consumes all considered Energy");
        assertClose(2.380000, shadowHit.action.getDamagePercent(),
                "Shadowpiercing ATK multiplier");
        assertClose((2.287520 + 7.0) * 96.0,
                shadowHit.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Shadowpiercing combines talent and first A4 EM additions");
        assertClose(2.0, shadowHit.action.getGaugeUnits(),
                "Shadowpiercing applies 2U Electro");
    }

    private static void testA4C1C2AndC6Boundaries() {
        Sethos c1 = sethos(1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        c1.restoreCurrentEnergy(20.0);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.CHARGE);
        assertClose(0.15, named(
                c1Records, "Royal Reed Archery Shadowpiercing").get(0)
                        .action.getExtraBonuses().get(
                                StatType.CRIT_RATE),
                "C1 grants Shadowpiercing CRIT Rate");

        Sethos c2 = sethos(2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        c2.restoreCurrentEnergy(20.0);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.CHARGE);
        ActionRecord c2Hit = named(
                c2Records, "Royal Reed Archery Shadowpiercing").get(0);
        assertEquals(1, c2.getC2Stacks(c2Simulator.getCurrentTime()),
                "A1 Energy consumption adds one C2 stack");
        assertClose(0.15, c2Hit.action.getStatSnapshot().get(
                StatType.ELECTRO_DMG_BONUS),
                "Consuming stack applies to the released Shadowpiercing shot");
        advanceTo(c2Simulator, 26.0 * FRAME + 10.0);
        assertEquals(0, c2.getC2Stacks(c2Simulator.getCurrentTime()),
                "C2 stack expires at its exact half-open boundary");

        Sethos c6 = sethos(6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        c6.restoreCurrentEnergy(20.0);
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertClose(20.0, c6.getCurrentEnergy(),
                "C6 restores the first Shadowpiercing Energy spend");
        assertClose(36.0 * FRAME + 15.0,
                c6.getC6NextAllowedTime(),
                "C6 cooldown starts on the connected impact");
        perform(c6Simulator, CharacterActionKey.CHARGE);
        assertClose(0.0, c6.getCurrentEnergy(),
                "C6 does not restore a second shot during cooldown");

        Sethos a4 = sethos(0);
        CombatSimulator a4Simulator = simulatorWith(a4);
        List<ActionRecord> a4Records = captureActions(a4Simulator);
        for (int index = 0; index < 5; index++) {
            a4.restoreCurrentEnergy(20.0);
            perform(a4Simulator, CharacterActionKey.CHARGE);
        }
        List<ActionRecord> shots = named(
                a4Records, "Royal Reed Archery Shadowpiercing");
        assertEquals(4, a4.getA4HitCount(),
                "A4 consumes exactly four enhanced hits");
        for (int index = 0; index < 4; index++) {
            assertClose((2.287520 + 7.0) * 96.0,
                    shots.get(index).action.getStatSnapshot().get(
                            StatType.FLAT_DMG_BONUS),
                    "A4 enhanced shot " + index);
        }
        assertClose(2.287520 * 96.0,
                shots.get(4).action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Fifth shot receives no A4 addition");
        advanceTo(a4Simulator, 36.0 * FRAME + 15.0);
        a4.restoreCurrentEnergy(20.0);
        perform(a4Simulator, CharacterActionKey.CHARGE);
        advanceBy(a4Simulator, FRAME);
        assertClose((2.287520 + 7.0) * 96.0,
                named(a4Records,
                        "Royal Reed Archery Shadowpiercing").get(5)
                        .action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "A4 refreshes at the exact fifteen-second boundary");
    }

    private static void testSkillReactionRefundParticlesAndCooldown() {
        Sethos reacting = sethos(2);
        CombatSimulator simulator = simulatorWith(reacting);
        reacting.restoreCurrentEnergy(0.0);
        simulator.getEnemy().setAura(Element.HYDRO, 2.0);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord first = named(
                records, "Ancient Rite: Thunderous Roar of Sand").get(0);
        assertClose(13.0 * FRAME, first.time,
                "Skill impacts at the sourced frame thirteen");
        assertClose(1.965200, first.action.getDamagePercent(),
                "Skill multiplier");
        assertEquals(ICDType.None, first.action.getICDType(),
                "Skill has no ICD");
        assertClose(12.0, reacting.getCurrentEnergy(),
                "Eligible Skill reaction restores twelve flat Energy");
        assertEquals(1, reacting.getC2Stacks(simulator.getCurrentTime()),
                "Skill Energy restoration adds the C2 regaining stack");

        perform(simulator, CharacterActionKey.SKILL);
        List<ActionRecord> skills = named(
                records, "Ancient Rite: Thunderous Roar of Sand");
        assertClose(490.0 * FRAME,
                skills.get(1).time - 13.0 * FRAME,
                "Skill cooldown starts at frame ten");
        advanceTo(simulator, 604.0 * FRAME);
        assertEquals(2, particles.size(),
                "Two connected Skills emit two particle packets");
        assertClose(2.0, particles.get(0).count,
                "Skill emits two Electro particles");
        assertClose(113.0 * FRAME, particles.get(0).time,
                "Skill particles arrive one hundred frames after impact");

        Sethos noReaction = sethos(2);
        CombatSimulator noReactionSimulator = simulatorWith(noReaction);
        noReaction.restoreCurrentEnergy(0.0);
        perform(noReactionSimulator, CharacterActionKey.SKILL);
        assertClose(0.0, noReaction.getCurrentEnergy(),
                "Skill without an eligible reaction grants no flat Energy");
        assertEquals(0, noReaction.getC2Stacks(
                noReactionSimulator.getCurrentTime()),
                "No reaction creates no C2 regaining stack");
    }

    private static void testBurstDuskBoltExpiryAndSwap() {
        Sethos c0 = sethos(0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(50.0 * FRAME, simulator.getCurrentTime(),
                "Burst recovery");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Burst spends sixty Energy at frame seven");
        assertTrue(c0.isBurstActive(simulator.getCurrentTime()),
                "Burst is active after recovery");
        assertClose(15.0, c0.getBurstCooldownEndTime(),
                "Burst cooldown starts at cast time");
        perform(simulator, CharacterActionKey.NORMAL);
        advanceBy(simulator, 2.0 * FRAME);
        ActionRecord dusk = named(records, "Dusk Bolt N1").get(0);
        assertClose(70.0 * FRAME, dusk.time,
                "Dusk Bolt impact timing");
        assertEquals(Element.ELECTRO, dusk.action.getElement(),
                "Burst converts Normal hit to Electro");
        assertEquals(ICDTag.ElementalBurst, dusk.action.getICDTag(),
                "Dusk Bolt uses Burst ICD tag");
        assertClose(3.334720 * 96.0,
                dusk.action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Dusk Bolt adds Talent-9 EM damage");

        Sethos c5 = sethos(5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        perform(c5Simulator, CharacterActionKey.NORMAL);
        advanceBy(c5Simulator, 2.0 * FRAME);
        assertClose(3.923200 * 96.0,
                named(c5Records, "Dusk Bolt N1").get(0)
                        .action.getStatSnapshot().get(
                                StatType.FLAT_DMG_BONUS),
                "C5 routes Dusk Bolt to Talent 12");
        assertEquals(1, c5.getC2Stacks(c5Simulator.getCurrentTime()),
                "Burst creates one C2 stack");

        Sethos expiry = sethos(0);
        CombatSimulator expirySimulator = simulatorWith(expiry);
        List<ActionRecord> expiryRecords = captureActions(expirySimulator);
        perform(expirySimulator, CharacterActionKey.BURST);
        advanceTo(expirySimulator, 8.0);
        assertTrue(!expiry.isBurstActive(8.0),
                "Burst expires at the exact half-open boundary");
        perform(expirySimulator, CharacterActionKey.NORMAL);
        advanceBy(expirySimulator, 2.0 * FRAME);
        assertEquals(Element.PHYSICAL, named(
                expiryRecords, "Royal Reed Archery N1").get(0)
                        .action.getElement(),
                "Normal released after expiry remains Physical");

        Sethos switched = sethos(0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSimulator = simulatorWith(switched, ally);
        perform(switchSimulator, CharacterActionKey.BURST);
        switchSimulator.switchCharacter(CharacterId.NOELLE);
        switchSimulator.switchCharacter(CharacterId.SETHOS);
        assertTrue(!switched.isBurstActive(switchSimulator.getCurrentTime()),
                "Switch-out cancels Sethos Burst");
        switched.restoreCurrentEnergy(20.0);
        perform(switchSimulator, CharacterActionKey.CHARGE);

        Sethos insufficient = sethos(0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        insufficient.restoreCurrentEnergy(0.0);
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertClose(0.0, insufficientSimulator.getCurrentTime(),
                "Insufficient-Energy Burst is skipped");
        assertClose(60.0, insufficient.getMissedBurstCost(),
                "Skipped Burst records its Energy demand");
    }

    private static void testSnapshotRestore() {
        Sethos combo = sethos(0);
        CombatSimulator comboSimulator = simulatorWith(combo);
        List<ActionRecord> comboRecords = captureActions(comboSimulator);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        SimulatorSnapshot comboSnapshot = comboSimulator.saveSnapshot();
        perform(comboSimulator, CharacterActionKey.NORMAL);
        comboSimulator.restoreSnapshot(comboSnapshot);
        perform(comboSimulator, CharacterActionKey.NORMAL);
        assertEquals(4, named(comboRecords,
                "Royal Reed Archery N2").size(),
                "Restore preserves the two-hit Normal progression");

        Sethos aimed = sethos(2);
        CombatSimulator aimedSimulator = simulatorWith(aimed);
        aimed.restoreCurrentEnergy(0.0);
        List<ActionRecord> aimedRecords = captureActions(aimedSimulator);
        aimed.performFullyChargedAimedShot(aimedSimulator);
        SimulatorSnapshot aimedSnapshot = aimedSimulator.saveSnapshot();
        advanceBy(aimedSimulator, 2.0 * FRAME);
        assertEquals(1, named(aimedRecords,
                "Royal Reed Archery Fully Charged").size(),
                "Live pending aimed hit resolves once");
        aimedSimulator.restoreSnapshot(aimedSnapshot);
        aimedSimulator.restoreSnapshot(aimedSnapshot);
        advanceBy(aimedSimulator, 2.0 * FRAME);
        assertEquals(2, named(aimedRecords,
                "Royal Reed Archery Fully Charged").size(),
                "Restored pending aimed hit resolves once");
        assertEquals(0, aimed.getPendingHitCount(),
                "Restored aimed queue drains cleanly");
        assertEquals(1, aimed.getC2Stacks(aimedSimulator.getCurrentTime()),
                "Restore preserves the consuming C2 stack");

        Sethos particleOwner = sethos(0);
        CombatSimulator particleSimulator = simulatorWith(particleOwner);
        List<ParticleRecord> particles = captureParticles(particleSimulator);
        perform(particleSimulator, CharacterActionKey.SKILL);
        SimulatorSnapshot particleSnapshot = particleSimulator.saveSnapshot();
        advanceTo(particleSimulator, 113.0 * FRAME);
        assertEquals(1, particles.size(),
                "Live pending particle packet arrives once");
        particleSimulator.restoreSnapshot(particleSnapshot);
        particleSimulator.restoreSnapshot(particleSnapshot);
        advanceTo(particleSimulator, 113.0 * FRAME);
        assertEquals(2, particles.size(),
                "Restored pending particle packet arrives once");
    }

    private static void testBindingAndInputGuards() {
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(constellation, sethos(constellation).getConstellation(),
                    "Sethos accepts C" + constellation);
        }
        assertThrows(IllegalArgumentException.class,
                () -> sethos(-1),
                "Sethos rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> sethos(7),
                "Sethos rejects constellation above C6");

        Sethos sethos = sethos(0);
        CombatSimulator simulator = simulatorWith(sethos);
        assertThrows(IllegalArgumentException.class,
                () -> sethos.onAction(null, simulator),
                "Sethos rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Sethos rejects unsupported Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Sethos rejects unsupported Plunge");

        Sethos burst = sethos(0);
        CombatSimulator burstSimulator = simulatorWith(burst);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertThrows(IllegalStateException.class,
                () -> perform(burstSimulator, CharacterActionKey.CHARGE),
                "Sethos rejects Shadowpiercing during Burst");
        assertThrows(IllegalStateException.class,
                () -> burst.performFullyChargedAimedShot(burstSimulator),
                "Sethos rejects fully charged aiming during Burst");

        Sethos external = sethos(0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Sethos rejects binding outside simulator party");
        Sethos reused = sethos(0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Sethos rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!sethos.acceptsCharacterState(foreignState),
                "Sethos rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> sethos.restoreCharacterState(foreignState, simulator),
                "Sethos rejects foreign restore payload");
    }

    private static Sethos sethos(int constellation) {
        return new Sethos(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Sethos sethos,
            Character... allies) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        simulator.addCharacter(sethos);
        for (Character ally : allies) {
            simulator.addCharacter(ally);
        }
        simulator.setActiveCharacter(CharacterId.SETHOS);
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.SETHOS, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) ->
                records.add(new ActionRecord(action, time)));
        return records;
    }

    private static List<ParticleRecord> captureParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
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

    private static void advanceBy(
            CombatSimulator simulator,
            double duration) {
        simulator.advanceTime(duration);
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
            assertTrue(lines.get(index).startsWith("Sethos,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Sethos/Sethos_Status.csv",
                "config/characters/Sethos/Sethos_Multipliers.csv"
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
        throw new AssertionError("Sethos CSVs missing key " + key);
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
