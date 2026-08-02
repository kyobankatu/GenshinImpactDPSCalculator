package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import model.character.Yoimiya;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression executable for Yoimiya's offensive vertical slice. */
public final class YoimiyaRegressionTest {
    private static final double EPS = 1e-9;

    private YoimiyaRegressionTest() {
    }

    /**
     * Runs Yoimiya data, action, timing, mark, passive, and boundary checks.
     *
     * @param args ignored command-line arguments
     * @throws IOException if configured CSV files cannot be read
     */
    public static void main(String[] args) throws IOException {
        testIdentityStatsCsvAndConstellations();
        testNormalSequenceChargedAndPlunge();
        testNiwabiInfusionParticlesA1AndSwitch();
        testAurousBlazeA4AndCooldownBoundary();
        testConstellationBranchesAndExplicitExclusions();
        testInvalidTriggersGenerationAndOwnership();
        testInvalidActionAndSimulatorBinding();
        System.out.println("Yoimiya regression checks passed.");
    }

    private static void testIdentityStatsCsvAndConstellations()
            throws IOException {
        Yoimiya yoimiya = new Yoimiya(null, null);
        assertEquals(CharacterId.YOIMIYA, yoimiya.getCharacterId(),
                "Yoimiya typed identity");
        assertEquals(CharacterId.YOIMIYA, CharacterId.fromName("Yoimiya"),
                "Yoimiya display-name lookup");
        assertEquals(CharacterId.YOIMIYA, CharacterId.fromNumericId(19),
                "Yoimiya numeric-id lookup");
        assertClose(10164.0,
                yoimiya.getBaseStats().get(StatType.BASE_HP), EPS,
                "Yoimiya level-90 base HP");
        assertClose(323.0,
                yoimiya.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Yoimiya level-90 base ATK");
        assertClose(615.0,
                yoimiya.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Yoimiya level-90 base DEF");
        assertClose(0.242,
                yoimiya.getBaseStats().get(StatType.CRIT_RATE), EPS,
                "Yoimiya ascension CRIT Rate plus base CRIT Rate");
        assertClose(60.0, yoimiya.getEnergyCost(), EPS,
                "Yoimiya Burst Energy cost");
        assertClose(18.0, yoimiya.getSkillCD(), EPS,
                "Yoimiya Skill cooldown");
        assertClose(15.0, yoimiya.getBurstCD(), EPS,
                "Yoimiya Burst cooldown");

        for (int constellation = 0; constellation <= 6; constellation++) {
            Yoimiya explicit = new Yoimiya(
                    null, null, constellation);
            assertEquals(constellation, explicit.getConstellation(),
                    "Yoimiya explicit C" + constellation);
        }

        assertCsvShape(Path.of(
                "config/characters/Yoimiya/Yoimiya_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/Yoimiya/Yoimiya_Multipliers.csv"), 16);
        TalentDataManager data = TalentDataManager.getInstance();
        assertClose(0.5994,
                data.get("Yoimiya", "N1 Hit 1", -1.0), EPS,
                "Yoimiya configured N1 first arrow");
        assertClose(1.58793,
                data.get(
                        "Yoimiya", "Blazing Arrow Multiplier", -1.0),
                EPS,
                "Yoimiya configured talent-9 Niwabi multiplier");
        assertClose(1.67646,
                data.get(
                        "Yoimiya", "Blazing Arrow Multiplier C3", -1.0),
                EPS,
                "Yoimiya configured talent-12 Niwabi multiplier");
        assertClose(2.44,
                data.get(
                        "Yoimiya", "Aurous Blaze Explosion C5", -1.0),
                EPS,
                "Yoimiya configured talent-12 mark multiplier");
    }

    private static void testNormalSequenceChargedAndPlunge() {
        Yoimiya yoimiya = new Yoimiya(null, null, 0);
        CombatSimulator sim = simulatorWith(yoimiya, false);
        List<ActionRecord> records = captureYoimiyaActions(sim);

        for (int i = 0; i < 5; i++) {
            perform(sim, CharacterActionKey.NORMAL);
        }
        assertEquals(7, records.size(),
                "Yoimiya five-step Normal arrow count");
        double[] multipliers = {
                0.5994, 0.5994, 1.14996, 1.494948,
                0.7807, 0.7807, 1.78044
        };
        String[] names = {
                "Firework Flare-Up N1 Hit 1",
                "Firework Flare-Up N1 Hit 2",
                "Firework Flare-Up N2",
                "Firework Flare-Up N3",
                "Firework Flare-Up N4 Hit 1",
                "Firework Flare-Up N4 Hit 2",
                "Firework Flare-Up N5"
        };
        for (int i = 0; i < records.size(); i++) {
            AttackAction action = records.get(i).action;
            assertEquals(names[i], action.getName(),
                    "Yoimiya Normal arrow name " + i);
            assertClose(multipliers[i], action.getDamagePercent(), EPS,
                    "Yoimiya Normal arrow multiplier " + i);
            assertEquals(Element.PHYSICAL, action.getElement(),
                    "Yoimiya uninfused Normal element " + i);
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Yoimiya Normal action type " + i);
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Yoimiya Normal ICD type " + i);
            assertEquals(ICDTag.NormalAttack, action.getICDTag(),
                    "Yoimiya Normal ICD tag " + i);
            assertClose(0.0, action.getGaugeUnits(), EPS,
                    "Yoimiya Physical Normal gauge " + i);
        }
        assertClose(25.0 / 60.0, records.get(0).time, EPS,
                "Yoimiya N1 first impact includes projectile travel");
        assertClose(34.0 / 60.0, records.get(1).time, EPS,
                "Yoimiya N1 second impact includes projectile travel");
        assertClose(3.2666666666666666, sim.getCurrentTime(), EPS,
                "Yoimiya five-step Normal action timeline");

        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Firework Flare-Up N1 Hit 1",
                records.get(0).action.getName(),
                "Yoimiya Normal chain wraps to N1");

        records.clear();
        perform(sim, CharacterActionKey.CHARGE);
        AttackAction charged = records.get(0).action;
        assertClose(2.108, charged.getDamagePercent(), EPS,
                "Yoimiya fully charged aimed multiplier");
        assertEquals(Element.PYRO, charged.getElement(),
                "Yoimiya fully charged aimed element");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Yoimiya fully charged aimed action type");
        assertEquals(ICDType.None, charged.getICDType(),
                "Yoimiya fully charged aimed ICD");
        assertClose(1.0, charged.getGaugeUnits(), EPS,
                "Yoimiya fully charged aimed gauge");

        records.clear();
        perform(sim, CharacterActionKey.PLUNGE);
        AttackAction plunge = records.get(0).action;
        assertClose(2.6076, plunge.getDamagePercent(), EPS,
                "Yoimiya high Plunge multiplier");
        assertEquals(Element.PHYSICAL, plunge.getElement(),
                "Yoimiya high Plunge element");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Yoimiya high Plunge action type");
    }

    private static void testNiwabiInfusionParticlesA1AndSwitch() {
        Yoimiya yoimiya = new Yoimiya(null, null, 0);
        CombatSimulator sim = simulatorWith(yoimiya, true);
        List<ActionRecord> records = captureYoimiyaActions(sim);

        double castTime = sim.getCurrentTime();
        perform(sim, CharacterActionKey.SKILL);
        assertClose(castTime + 34.0 / 60.0,
                sim.getCurrentTime(), EPS,
                "Yoimiya Skill action interval");
        assertTrue(yoimiya.isNiwabiActive(sim.getCurrentTime()),
                "Niwabi active after Skill action");
        assertClose(castTime + 11.0 / 60.0 + 10.0,
                yoimiya.getNiwabiExpiresAt(), EPS,
                "Niwabi starts at sourced Skill frame");
        assertClose(castTime + 11.0 / 60.0 + 18.0,
                yoimiya.getSkillCooldownEndTime(), EPS,
                "Yoimiya Skill cooldown starts at frame 11");

        perform(sim, CharacterActionKey.NORMAL);
        assertEquals(2, records.size(),
                "Infused N1 resolves two arrows");
        for (ActionRecord record : records) {
            assertEquals(Element.PYRO, record.action.getElement(),
                    "Niwabi converts Normal arrow to Pyro");
            assertClose(0.5994 * 1.58793,
                    record.action.getDamagePercent(), EPS,
                    "Niwabi multiplicatively scales released arrow");
            assertClose(1.0, record.action.getGaugeUnits(), EPS,
                    "Niwabi Normal arrow gauge");
        }
        assertEquals(2,
                yoimiya.getA1StackCount(sim.getCurrentTime()),
                "N1 two arrows grant two A1 stacks");
        assertClose(0.04,
                yoimiya.getEffectiveStats(sim.getCurrentTime())
                        .get(StatType.PYRO_DMG_BONUS),
                EPS,
                "A1 current Pyro DMG bonus");
        assertClose(3.0, yoimiya.getTotalParticleEnergy(), EPS,
                "Niwabi first arrow emits one same-element particle");

        double a1Expiry = records.get(1).time + 3.0;
        sim.advanceTime(a1Expiry - sim.getCurrentTime() - 0.001);
        assertEquals(2, yoimiya.getA1StackCount(sim.getCurrentTime()),
                "A1 half-open pre-expiry boundary");
        sim.advanceTime(0.001);
        assertEquals(0, yoimiya.getA1StackCount(sim.getCurrentTime()),
                "A1 exact expiry boundary");

        double niwabiExpiry = yoimiya.getNiwabiExpiresAt();
        sim.advanceTime(niwabiExpiry - sim.getCurrentTime() - 0.20);
        records.clear();
        perform(sim, CharacterActionKey.NORMAL);
        sim.advanceTime(2.0 / 60.0);
        assertEquals(Element.PYRO, records.get(0).action.getElement(),
                "Pre-expiry arrow retains released Niwabi element");
        assertTrue(!yoimiya.isNiwabiActive(sim.getCurrentTime()),
                "Niwabi expires while the released N1 is in flight");
        assertEquals(0, yoimiya.getA1StackCount(sim.getCurrentTime()),
                "Post-expiry impact does not grant A1");
        double particlesBeforeSynthetic = yoimiya.getTotalParticleEnergy();
        sim.notifyDamage(
                yoimiya,
                elementalAction(
                        "Synthetic Pyro Normal",
                        ActionType.NORMAL,
                        Element.PYRO,
                        1.0,
                        true),
                1.0);
        assertClose(particlesBeforeSynthetic,
                yoimiya.getTotalParticleEnergy(), EPS,
                "Unowned synthetic Pyro Normal cannot generate particles");
        assertEquals(0, yoimiya.getA1StackCount(sim.getCurrentTime()),
                "Unowned synthetic Pyro Normal cannot generate A1 stacks");

        Yoimiya switched = new Yoimiya(null, null, 0);
        CombatSimulator switchSim = simulatorWith(switched, true);
        perform(switchSim, CharacterActionKey.SKILL);
        switchSim.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switched.isNiwabiActive(switchSim.getCurrentTime()),
                "Niwabi ends immediately on switch-out");
    }

    private static void testAurousBlazeA4AndCooldownBoundary() {
        Yoimiya yoimiya = new Yoimiya(null, null, 0);
        CombatSimulator sim = simulatorWith(yoimiya, true);
        Character ally = sim.getCharacter(CharacterId.NOELLE);
        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.NORMAL);
        int stacksAtBurst = yoimiya.getA1StackCount(sim.getCurrentTime());
        List<ActionRecord> records = captureYoimiyaActions(sim);
        double burstCast = sim.getCurrentTime();

        perform(sim, CharacterActionKey.BURST);
        assertClose(0.0, yoimiya.getCurrentEnergy(), EPS,
                "Yoimiya Burst spends 60 Energy");
        assertEquals(1, records.size(),
                "Yoimiya Burst initial hit count");
        ActionRecord initial = records.get(0);
        assertClose(burstCast + 75.0 / 60.0, initial.time, EPS,
                "Yoimiya Burst initial hitmark");
        assertClose(2.1624, initial.action.getDamagePercent(), EPS,
                "Yoimiya C0 Burst initial multiplier");
        assertEquals(ICDType.Standard, initial.action.getICDType(),
                "Yoimiya Burst initial standard ICD");
        assertClose(2.0, initial.action.getGaugeUnits(), EPS,
                "Yoimiya Burst initial 2U gauge");
        assertTrue(yoimiya.isAurousBlazeActive(sim.getCurrentTime()),
                "Aurous Blaze active after Burst impact");
        assertClose(initial.time + 10.0,
                yoimiya.getAurousBlazeExpiresAt(), EPS,
                "C0 Aurous Blaze duration");
        assertClose(0.0,
                effectiveStats(sim, yoimiya)
                        .get(StatType.ATK_PERCENT),
                EPS,
                "A4 excludes Yoimiya");
        assertClose(0.10 + stacksAtBurst * 0.01,
                effectiveStats(sim, ally)
                        .get(StatType.ATK_PERCENT),
                EPS,
                "A4 snapshots A1 stacks for ally ATK");

        records.clear();
        partyHit(sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        sim.advanceTime(1.0 / 60.0);
        assertEquals(1, records.size(),
                "First eligible party hit detonates Aurous Blaze");
        assertAurousExplosion(records.get(0).action, 2.074);

        partyHit(sim, CharacterId.NOELLE, ActionType.SKILL, 1.0, true);
        sim.advanceTime(1.0 / 60.0);
        assertEquals(1, records.size(),
                "Aurous Blaze rejects pre-two-second trigger");
        double exactReady = records.get(0).time - 1.0 / 60.0 + 2.0;
        sim.advanceTime(exactReady - sim.getCurrentTime());
        partyHit(sim, CharacterId.NOELLE, ActionType.BURST, 1.0, true);
        sim.advanceTime(1.0 / 60.0);
        assertEquals(2, records.size(),
                "Aurous Blaze accepts exact two-second boundary");

        double markExpiry = yoimiya.getAurousBlazeExpiresAt();
        sim.advanceTime(markExpiry - sim.getCurrentTime() - 0.001);
        assertTrue(yoimiya.isAurousBlazeActive(sim.getCurrentTime()),
                "Aurous Blaze pre-expiry boundary");
        sim.advanceTime(0.001);
        assertTrue(!yoimiya.isAurousBlazeActive(sim.getCurrentTime()),
                "Aurous Blaze exact expiry boundary");
        partyHit(sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        sim.advanceTime(1.0 / 60.0);
        assertEquals(2, records.size(),
                "Expired Aurous Blaze cannot detonate");

        Yoimiya c4 = new Yoimiya(null, null, 4);
        CombatSimulator c4Sim = simulatorWith(c4, true);
        perform(c4Sim, CharacterActionKey.SKILL);
        double cooldownBefore = c4.getSkillCooldownEndTime();
        perform(c4Sim, CharacterActionKey.BURST);
        partyHit(c4Sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        assertClose(cooldownBefore - 1.2,
                c4.getSkillCooldownEndTime(), EPS,
                "C4 mark trigger reduces pending Skill cooldown");
    }

    private static void testConstellationBranchesAndExplicitExclusions() {
        Yoimiya c1 = new Yoimiya(null, null, 1);
        CombatSimulator c1Sim = simulatorWith(c1, true);
        List<ActionRecord> c1Records = captureYoimiyaActions(c1Sim);
        perform(c1Sim, CharacterActionKey.BURST);
        assertClose(c1Records.get(0).time + 14.0,
                c1.getAurousBlazeExpiresAt(), EPS,
                "C1 extends Aurous Blaze by four seconds");

        Yoimiya c2 = new Yoimiya(null, null, 2);
        CombatSimulator c2Sim = simulatorWith(c2, false);
        List<ActionRecord> c2Records = captureYoimiyaActions(c2Sim);
        perform(c2Sim, CharacterActionKey.SKILL);
        perform(c2Sim, CharacterActionKey.NORMAL);
        assertClose(0.5994 * 1.58793,
                c2Records.get(0).action.getDamagePercent(), EPS,
                "C2 actual-CRIT branch intentionally adds no deterministic bonus");

        Yoimiya c3 = new Yoimiya(null, null, 3);
        CombatSimulator c3Sim = simulatorWith(c3, false);
        List<ActionRecord> c3Records = captureYoimiyaActions(c3Sim);
        perform(c3Sim, CharacterActionKey.SKILL);
        perform(c3Sim, CharacterActionKey.NORMAL);
        assertClose(0.5994 * 1.67646,
                c3Records.get(0).action.getDamagePercent(), EPS,
                "C3 selects talent-12 Niwabi multiplier");

        Yoimiya c5 = new Yoimiya(null, null, 5);
        CombatSimulator c5Sim = simulatorWith(c5, true);
        List<ActionRecord> c5Records = captureYoimiyaActions(c5Sim);
        perform(c5Sim, CharacterActionKey.BURST);
        assertClose(2.544,
                c5Records.get(0).action.getDamagePercent(), EPS,
                "C5 selects talent-12 Burst initial multiplier");
        c5Records.clear();
        partyHit(c5Sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        c5Sim.advanceTime(1.0 / 60.0);
        assertClose(2.44,
                c5Records.get(0).action.getDamagePercent(), EPS,
                "C5 selects talent-12 mark multiplier");

        Yoimiya c6 = new Yoimiya(null, null, 6);
        CombatSimulator c6Sim = simulatorWith(c6, false);
        List<ActionRecord> c6Records = captureYoimiyaActions(c6Sim);
        perform(c6Sim, CharacterActionKey.SKILL);
        perform(c6Sim, CharacterActionKey.NORMAL);
        assertEquals(2, c6Records.size(),
                "C6 random extra arrow is intentionally excluded");
    }

    private static void testInvalidTriggersGenerationAndOwnership() {
        Yoimiya yoimiya = new Yoimiya(null, null, 0);
        CombatSimulator sim = simulatorWith(yoimiya, true);
        List<ActionRecord> records = captureYoimiyaActions(sim);
        perform(sim, CharacterActionKey.BURST);
        records.clear();

        partyHit(sim, CharacterId.YOIMIYA, ActionType.NORMAL, 1.0, true);
        records.clear();
        partyHit(sim, CharacterId.NOELLE, ActionType.OTHER, 1.0, true);
        partyHit(sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, false);
        partyHit(sim, CharacterId.NOELLE, ActionType.NORMAL, 0.0, true);
        Character outsider = new TestCharacter(CharacterId.RAZOR, Element.ELECTRO);
        sim.notifyDamage(
                outsider,
                action("Outsider", ActionType.SKILL, 1.0, true),
                1.0);
        sim.advanceTime(1.0 / 60.0);
        assertEquals(0, records.size(),
                "Self/dummy/zero/wrong-action/non-party hits are rejected");

        partyHit(sim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        yoimiya.onAction(
                CharacterActionRequest.of(CharacterActionKey.BURST), sim);
        assertEquals(1, records.size(),
                "Recast invalidates queued old-generation detonation");
        assertEquals("Ryuukin Saxifrage", records.get(0).action.getName(),
                "Only replacement Burst resolves after stale trigger");

        Yoimiya first = new Yoimiya(null, null, 0);
        Yoimiya second = new Yoimiya(null, null, 0);
        CombatSimulator firstSim = simulatorWith(first, true);
        CombatSimulator secondSim = simulatorWith(second, true);
        List<ActionRecord> firstRecords = captureYoimiyaActions(firstSim);
        List<ActionRecord> secondRecords = captureYoimiyaActions(secondSim);
        perform(firstSim, CharacterActionKey.BURST);
        firstRecords.clear();
        partyHit(firstSim, CharacterId.NOELLE, ActionType.NORMAL, 1.0, true);
        firstSim.advanceTime(1.0 / 60.0);
        secondSim.advanceTime(1.0 / 60.0);
        assertEquals(1, firstRecords.size(),
                "First Yoimiya instance owns its mark listener");
        assertEquals(0, secondRecords.size(),
                "Independent Yoimiya instance remains untouched");
    }

    private static void testInvalidActionAndSimulatorBinding() {
        Yoimiya yoimiya = new Yoimiya(null, null, 0);
        CombatSimulator sim = simulatorWith(yoimiya, false);
        assertThrows(IllegalArgumentException.class,
                () -> yoimiya.onAction(
                        CharacterActionRequest.of(CharacterActionKey.DASH),
                        sim),
                "Yoimiya rejects unsupported typed action");
        assertThrows(IllegalArgumentException.class,
                () -> new Yoimiya(null, null, -1),
                "Yoimiya rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Yoimiya(null, null, 7),
                "Yoimiya rejects constellation above six");

        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(IllegalStateException.class,
                () -> other.addCharacter(yoimiya),
                "Yoimiya rejects cross-simulator reuse");
    }

    private static void assertAurousExplosion(
            AttackAction action,
            double multiplier) {
        assertEquals("Aurous Blaze Explosion", action.getName(),
                "Aurous Blaze explosion name");
        assertClose(multiplier, action.getDamagePercent(), EPS,
                "Aurous Blaze explosion multiplier");
        assertEquals(ActionType.BURST, action.getActionType(),
                "Aurous Blaze Burst damage classification");
        assertEquals(Element.PYRO, action.getElement(),
                "Aurous Blaze element");
        assertEquals(ICDType.Standard, action.getICDType(),
                "Aurous Blaze standard ICD");
        assertEquals(ICDTag.ElementalBurst, action.getICDTag(),
                "Aurous Blaze Burst ICD tag");
        assertClose(1.0, action.getGaugeUnits(), EPS,
                "Aurous Blaze 1U gauge");
    }

    private static CombatSimulator simulatorWith(
            Yoimiya yoimiya,
            boolean includeAlly) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(yoimiya);
        if (includeAlly) {
            sim.addCharacter(new TestCharacter(
                    CharacterId.NOELLE, Element.GEO));
        }
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.YOIMIYA,
                CharacterActionRequest.of(key));
    }

    private static void partyHit(
            CombatSimulator sim,
            CharacterId actorId,
            ActionType type,
            double multiplier,
            boolean trueHit) {
        sim.performActionWithoutTimeAdvance(
                actorId,
                action("Party Trigger", type, multiplier, trueHit));
    }

    private static AttackAction action(
            String name,
            ActionType type,
            double multiplier,
            boolean trueHit) {
        return elementalAction(
                name,
                type,
                Element.PHYSICAL,
                multiplier,
                trueHit);
    }

    private static AttackAction elementalAction(
            String name,
            ActionType type,
            Element element,
            double multiplier,
            boolean trueHit) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                type);
        action.setHitEffectTrigger(trueHit);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static StatsContainer effectiveStats(
            CombatSimulator sim,
            Character character) {
        StatsContainer stats = character.getEffectiveStats(
                sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats;
    }

    private static List<ActionRecord> captureYoimiyaActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YOIMIYA) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static void assertCsvShape(Path path, int dataRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(dataRows + 1, lines.size(),
                path + " row count");
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0),
                path + " header");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at row " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
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

    /** Minimal party member used to drive Aurous Blaze triggers. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            this.name = id.getDisplayName();
            this.characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 1000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
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
