package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Clorinde;
import model.entity.Character;
import model.entity.Enemy;
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

/** Focused regression checks for Clorinde's fixed-target Night Vigil slice. */
public final class ClorindeRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ClorindeRegressionTest() {
    }

    /** Runs data, action, Bond, passive, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testSwordBasicsAndUnsupportedActions();
        testNightVigilSwiftImpaleAndParticles();
        testFullBondA4AndC6Shade();
        testA1C1C2AndPrivateIcd();
        testBurstC4AndTalentConstellations();
        testFailClosedBoundariesAndIsolation();
        testSnapshotRestore();
        System.out.println("ClorindeRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Clorinde clorinde = new Clorinde(null, null, 6);
        assertEquals(CharacterId.CLORINDE,
                clorinde.getCharacterId(),
                "Clorinde typed identity");
        assertEquals(CharacterId.CLORINDE,
                CharacterId.fromName("Clorinde"),
                "Clorinde name lookup");
        assertEquals(CharacterId.CLORINDE,
                CharacterId.fromNumericId(99),
                "Clorinde numeric lookup");
        assertEquals(99, CharacterId.CLORINDE.getNumericId(),
                "Clorinde stable numeric id");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.CLORINDE.getRegion(),
                "Clorinde region");
        assertEquals(Element.ELECTRO, clorinde.getElement(),
                "Clorinde element");
        assertClose(12956.0,
                clorinde.getBaseStats().get(StatType.BASE_HP),
                "Clorinde base HP");
        assertClose(337.0,
                clorinde.getBaseStats().get(StatType.BASE_ATK),
                "Clorinde base ATK");
        assertClose(784.0,
                clorinde.getBaseStats().get(StatType.BASE_DEF),
                "Clorinde base DEF");
        assertClose(0.242,
                clorinde.getBaseStats().get(StatType.CRIT_RATE),
                "Clorinde total base CRIT Rate");
        assertClose(60.0, clorinde.getEnergyCost(),
                "Clorinde Energy cost");
        assertClose(16.0, clorinde.getSkillCD(),
                "Clorinde Skill cooldown");
        assertClose(15.0, clorinde.getBurstCD(),
                "Clorinde Burst cooldown");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Clorinde(null, null, constellation)
                            .getConstellation(),
                    "Clorinde explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Clorinde/Clorinde_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Clorinde/Clorinde_Multipliers.csv"),
                49);
        assertCsvValue("Swift Hunt Enhanced C3", 0.874940);
        assertCsvValue("Last Lightfall C5", 2.537600);
        assertCsvValue("C2 A1 Flat Cap", 2700.0);
        assertCsvValue("C6 Shade Multiplier", 2.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Clorinde(null, null, -1),
                "Clorinde rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Clorinde(null, null, 7),
                "Clorinde rejects constellation above C6");
    }

    private static void testSwordBasicsAndUnsupportedActions() {
        Clorinde clorinde = new Clorinde(null, null, 0);
        CombatSimulator simulator = simulatorWith(clorinde);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            0.993188, 0.948521, 0.628050, 0.425020, 1.653675
        };
        int[] firstHitFrames = { 18, 12, 23, 12, 21 };
        int[] durations = { 24, 27, 42, 35, 60 };
        int[] hitlagFrames = { 6, 6, 12, 15, 6 };
        int[] hitCounts = { 1, 1, 2, 3, 1 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> hits = named(
                    records,
                    "Oath of Hunting Shadows N" + (step + 1));
            assertEquals(hitCounts[step], hits.size(),
                    "Clorinde N" + (step + 1) + " hit count");
            assertClose(castTime + firstHitFrames[step] * FRAME,
                    hits.get(0).time,
                    "Clorinde N" + (step + 1) + " first hitmark");
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Clorinde N" + (step + 1) + " recovery");
            assertClose(multipliers[step],
                    hits.get(0).action.getDamagePercent(),
                    "Clorinde N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL,
                    hits.get(0).action.getElement(),
                    "Clorinde base Normal element");
            assertEquals(ActionType.NORMAL,
                    hits.get(0).action.getActionType(),
                    "Clorinde base Normal action type");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(
                records, "Oath of Hunting Shadows N1").size(),
                "Clorinde Normal string wraps after N5");

        Clorinde basics = new Clorinde(null, null, 0);
        CombatSimulator basicsSimulator = simulatorWith(basics);
        List<ActionRecord> basicsRecords = captureActions(basicsSimulator);
        perform(basicsSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = onlyNamed(
                basicsRecords, "Oath of Hunting Shadows Charged");
        assertClose(38.0 * FRAME, charged.time,
                "Clorinde Charged hitmark");
        assertClose(44.0 * FRAME, basicsSimulator.getCurrentTime(),
                "Clorinde Charged recovery");
        assertClose(2.354200, charged.action.getDamagePercent(),
                "Clorinde Charged multiplier");
        assertEquals(ICDTag.NormalAttack, charged.action.getICDTag(),
                "Clorinde Charged shares Normal ICD tag");
        assertClose(0.0, charged.action.getGaugeUnits(),
                "Physical Charged applies no aura");

        double plungeCast = basicsSimulator.getCurrentTime();
        perform(basicsSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = onlyNamed(
                basicsRecords, "Oath of Hunting Shadows High Plunge");
        assertClose(plungeCast + 48.0 * FRAME, plunge.time,
                "Clorinde high-Plunge hitmark");
        assertClose(plungeCast + 81.0 * FRAME,
                basicsSimulator.getCurrentTime(),
                "Clorinde high-Plunge recovery");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Clorinde high-Plunge multiplier");

        assertThrows(IllegalArgumentException.class,
                () -> perform(basicsSimulator, CharacterActionKey.DASH),
                "Clorinde rejects movement actions");
        assertThrows(IllegalArgumentException.class,
                () -> basicsSimulator.performAction(
                        CharacterId.CLORINDE,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Clorinde rejects unsupported Hold Skill");
    }

    private static void testNightVigilSwiftImpaleAndParticles() {
        Clorinde clorinde = new Clorinde(null, null, 0);
        CombatSimulator simulator = simulatorWith(clorinde);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(33.0 * FRAME, simulator.getCurrentTime(),
                "Hunter's Vigil activation recovery");
        assertClose(6.0 * FRAME, clorinde.getLastSkillTime(),
                "Hunter's Vigil cooldown starts at frame six");
        assertTrue(clorinde.isNightVigilActive(
                        simulator.getCurrentTime()),
                "Night Vigil is active after activation");
        assertClose(0.0,
                clorinde.getSkillCDRemaining(simulator.getCurrentTime()),
                "Night Vigil exposes the Impale replacement");

        double swiftCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord swift = onlyNamed(
                records, "Swift Hunt (Piercing Shot) 1");
        assertClose(swiftCast + 8.0 * FRAME, swift.time,
                "Swift Hunt first hitmark");
        assertClose(swiftCast + 18.0 * FRAME,
                simulator.getCurrentTime(),
                "Swift Hunt first recovery");
        assertClose(0.712580, swift.action.getDamagePercent(),
                "C0 enhanced Swift Hunt multiplier");
        assertEquals(ActionType.NORMAL, swift.action.getActionType(),
                "Swift Hunt counts as Normal damage");
        assertEquals(Element.ELECTRO, swift.action.getElement(),
                "Swift Hunt is Electro");
        assertClose(0.35, clorinde.getBondOfLifeRatio(),
                "Enhanced Swift Hunt grants local Bond");

        double impaleCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord impale = onlyNamed(
                records, "Impale the Night (<100% Bond)");
        assertClose(impaleCast + 11.0 * FRAME, impale.time,
                "Low-Bond Impale hitmark");
        assertClose(impaleCast + (43.0 + 4.0) * FRAME,
                simulator.getCurrentTime(),
                "Impale recovery does not wait for base Skill CD");
        assertClose(0.807696, impale.action.getDamagePercent(),
                "C0 low-Bond Impale multiplier");
        assertClose(0.0, clorinde.getBondOfLifeRatio(),
                "Impale clears local Bond without fabricating healing");

        double firstParticleTime = swift.time + 100.0 * FRAME;
        advanceTo(simulator, firstParticleTime);
        assertEquals(1, particles.size(),
                "Two eligible hits inside two seconds make one particle");
        assertClose(1.0, particles.get(0).count,
                "Clorinde particle packet count");
        assertClose(firstParticleTime, particles.get(0).time,
                "Clorinde particle travel time");
        ActionRecord surging = onlyNamed(records, "Surging Blade");
        assertClose(swift.time + 42.0 * FRAME, surging.time,
                "Surging Blade delay from accepted Swift task");
        assertClose(0.0, surging.action.getGaugeUnits(),
                "Surging Blade carries no elemental gauge");
    }

    private static void testFullBondA4AndC6Shade() {
        Clorinde c0 = new Clorinde(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose(1.05, c0.getBondOfLifeRatio(),
                "Three enhanced Swift Hunts cross the 100% boundary");
        double impaleCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.SKILL);
        List<ActionRecord> fullHits = named(
                records, "Impale the Night (100%+ Bond)");
        assertEquals(3, fullHits.size(),
                "Full-Bond Impale emits three hits");
        assertClose(impaleCast + 11.0 * FRAME,
                fullHits.get(0).time,
                "Full-Bond Impale hitmark");
        assertClose(0.461360,
                fullHits.get(0).action.getDamagePercent(),
                "C0 full-Bond Impale multiplier per hit");
        assertClose(0.0, c0.getBondOfLifeRatio(),
                "Full-Bond Impale clears local Bond");
        assertEquals(1, c0.getA4StackCount(
                        simulator.getCurrentTime()),
                "Clearing 100%+ Bond grants one A4 stack");
        assertClose(0.342,
                fullHits.get(0).action.getStatSnapshot().get(
                        StatType.CRIT_RATE),
                "A4 stack applies before full-Bond Impale damage");

        Clorinde c6 = new Clorinde(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        int shadesBefore = c6.getC6ShadesRemaining();
        double c6ImpaleCast = c6Simulator.getCurrentTime();
        perform(c6Simulator, CharacterActionKey.SKILL);
        ActionRecord shade = onlyNamed(
                c6Records, "Glimbright Shade (C6)");
        assertClose(c6ImpaleCast + 37.0 * FRAME, shade.time,
                "C6 offensive Shade delay");
        assertClose(2.0, shade.action.getDamagePercent(),
                "C6 offensive Shade multiplier");
        assertEquals(shadesBefore - 1, c6.getC6ShadesRemaining(),
                "C6 full-Bond Impale spends one Shade");
        assertEquals(ICDType.ClorindeElementalArt,
                shade.action.getICDType(),
                "C6 Shade uses the private one-second ICD");
    }

    private static void testA1C1C2AndPrivateIcd() {
        Clorinde c2 = new Clorinde(null, null, 2);
        CombatSimulator simulator = simulatorWith(c2);
        List<ActionRecord> records = captureActions(simulator);
        simulator.notifyReaction(ReactionResult.transform(
                1.0, "Overload", ReactionResult.Kind.OVERLOAD), c2);
        simulator.notifyReaction(ReactionResult.transform(
                1.0, "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED), c2);
        simulator.notifyReaction(new ReactionResult(
                ReactionResult.Type.TRANSFORMATIVE,
                1.0,
                1.0,
                "Electro Swirl",
                ReactionResult.Kind.SWIRL,
                Element.ELECTRO), c2);
        assertEquals(3, c2.getA1StackCount(simulator.getCurrentTime()),
                "Three Electro reactions grant three A1 stacks");
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord swift = onlyNamed(
                records, "Swift Hunt (Piercing Shot) 1");
        assertClose(337.0 * 0.30 * 3.0,
                swift.action.getAdditiveBaseDmgBonus(),
                "C2 upgrades A1's three-stack additive damage");
        List<ActionRecord> c1Shades = named(
                records, "Nightvigil Shade (C1)");
        assertEquals(2, c1Shades.size(),
                "Accepted Electro Normal triggers two C1 Shade hits");
        assertClose(swift.time + FRAME, c1Shades.get(0).time,
                "C1 Shade fixed one-frame delay");
        assertClose(0.30,
                c1Shades.get(0).action.getDamagePercent(),
                "C1 Shade multiplier");
        assertEquals(ICDTag.Clorinde_ElementalArt,
                c1Shades.get(0).action.getICDTag(),
                "C1 Shade private ICD tag");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                        "Clorinde",
                        ICDTag.Clorinde_ElementalArt,
                        ICDType.ClorindeElementalArt,
                        0.0),
                "Clorinde private ICD admits first hit");
        assertTrue(!icd.checkApplication(
                        "Clorinde",
                        ICDTag.Clorinde_ElementalArt,
                        ICDType.ClorindeElementalArt,
                        0.5),
                "Clorinde private ICD suppresses inside one second");
        assertTrue(icd.checkApplication(
                        "Clorinde",
                        ICDTag.Clorinde_ElementalArt,
                        ICDType.ClorindeElementalArt,
                        1.0),
                "Clorinde private ICD admits exact one-second boundary");
        advanceTo(simulator, 15.0 + EPSILON);
        assertEquals(0, c2.getA1StackCount(simulator.getCurrentTime()),
                "Independent A1 stacks expire after fifteen seconds");
    }

    private static void testBurstC4AndTalentConstellations() {
        Clorinde c4 = new Clorinde(null, null, 4);
        CombatSimulator simulator = simulatorWith(c4);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        List<ActionRecord> burstHits = prefix(records, "Last Lightfall ");
        assertEquals(5, burstHits.size(),
                "Last Lightfall emits five hits");
        assertClose(burstCast + 97.0 * FRAME,
                burstHits.get(0).time,
                "Last Lightfall first hitmark");
        assertClose(burstCast + 121.0 * FRAME,
                burstHits.get(4).time,
                "Last Lightfall final hitmark");
        assertClose(burstCast + 128.0 * FRAME,
                simulator.getCurrentTime(),
                "Last Lightfall recovery");
        assertClose(2.156960,
                burstHits.get(0).action.getDamagePercent(),
                "C4 retains talent-nine Burst multiplier");
        assertClose(2.0,
                burstHits.get(0).action.getExtraBonuses().get(
                        StatType.DMG_BONUS_ALL),
                "C4 Burst bonus caps at 200%");
        assertClose(2.0, c4.getBondOfLifeRatio(),
                "Burst Bond gain respects the 200% local cap");
        assertEquals(1, c4.getA4StackCount(
                        simulator.getCurrentTime()),
                "Burst Bond change at 100%+ grants one A4 stack");

        Clorinde c5 = new Clorinde(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        ActionRecord c5First = prefix(
                c5Records, "Last Lightfall ").get(0);
        assertClose(2.537600, c5First.action.getDamagePercent(),
                "C5 uses talent-twelve Burst damage");
        assertClose(1.320000, c5.getBondOfLifeRatio(),
                "C5 uses talent-twelve Burst Bond gain");

        Clorinde c3 = new Clorinde(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        perform(c3Simulator, CharacterActionKey.NORMAL);
        assertClose(0.874940,
                onlyNamed(c3Records, "Swift Hunt (Piercing Shot) 1")
                        .action.getDamagePercent(),
                "C3 uses talent-twelve Skill multiplier");
    }

    private static void testFailClosedBoundariesAndIsolation() {
        Clorinde clorinde = new Clorinde(null, null, 6);
        assertTrue(!clorinde.isPlayerHpHealingRepresented(),
                "Player HP and actual healing are excluded");
        assertTrue(!clorinde.isExternalBondOfLifeRepresented(),
                "External Bond integrations are excluded");
        assertTrue(!clorinde.isMovementGeometryRepresented(),
                "Movement and geometry are excluded");
        assertTrue(!clorinde.isMultiTargetSelectionRepresented(),
                "Multi-target and random selection are excluded");
        assertTrue(!clorinde.isHitlagRepresented(),
                "Hitlag is excluded");
        assertTrue(!clorinde.isStaminaRepresented(),
                "Stamina is excluded");
        assertTrue(!clorinde.isLowPlungeRepresented(),
                "Low Plunge is excluded");
        assertTrue(!clorinde.isExplorationStateRepresented(),
                "Exploration state is excluded");
        assertTrue(!clorinde.isDefensiveStateRepresented(),
                "Defensive state and reactive C6 are excluded");

        Clorinde first = new Clorinde(null, null, 0);
        Clorinde independent = new Clorinde(null, null, 0);
        CombatSimulator firstSimulator = simulatorWith(first);
        simulatorWith(independent);
        perform(firstSimulator, CharacterActionKey.SKILL);
        perform(firstSimulator, CharacterActionKey.NORMAL);
        assertClose(0.35, first.getBondOfLifeRatio(),
                "First owner tracks its Bond");
        assertClose(0.0, independent.getBondOfLifeRatio(),
                "Independent owner does not share Bond");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(first),
                "One Clorinde instance cannot bind to two simulators");

        Clorinde noEnemy = new Clorinde(null, null, 0);
        CombatSimulator noEnemySimulator = simulatorWithoutEnemy(noEnemy);
        perform(noEnemySimulator, CharacterActionKey.SKILL);
        perform(noEnemySimulator, CharacterActionKey.NORMAL);
        assertClose(0.35, noEnemy.getBondOfLifeRatio(),
                "Swift Hunt local Bond does not fabricate target geometry");
    }

    private static void testSnapshotRestore() {
        Clorinde clorinde = new Clorinde(null, null, 0);
        CombatSimulator simulator = simulatorWith(clorinde);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        double snapshotTime = simulator.getCurrentTime();
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(0.0, clorinde.getBondOfLifeRatio(),
                "Divergent Impale branch clears Bond");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        assertClose(snapshotTime, simulator.getCurrentTime(),
                "Rollback restores simulation time");
        assertClose(0.35, clorinde.getBondOfLifeRatio(),
                "Rollback restores local Bond");
        advanceTo(simulator, snapshotTime + 3.0);
        assertEquals(1, named(records, "Surging Blade").size(),
                "Repeated restore reconstructs Surging Blade once");
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs particle packet once");
        advanceTo(simulator, snapshotTime + 20.0);
        assertEquals(0, clorinde.getPendingEventCount(),
                "All restored Clorinde events drain exactly once");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static CombatSimulator simulatorWithoutEnemy(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.CLORINDE,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CLORINDE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureElectroParticles(
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
        if (selected.isEmpty()) {
            throw new AssertionError("Missing action " + name);
        }
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
            assertEquals(6,
                    lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Clorinde,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) {
        double actual = TalentDataManager.getInstance().get(
                "Clorinde", key, Double.NaN);
        assertClose(expected, actual, "Clorinde CSV " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.isNaN(actual)
                || Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message
                    + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message
                    + ": expected=" + expected + ", actual=" + actual);
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
            throw new AssertionError(message
                    + ": wrong exception " + throwable, throwable);
        }
        throw new AssertionError(message + ": no exception");
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
}
