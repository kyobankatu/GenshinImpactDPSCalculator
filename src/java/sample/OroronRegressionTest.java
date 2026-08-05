package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.element.ICDManager;
import mechanics.reaction.ReactionResult;
import model.character.Ororon;
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

/** Focused regression checks for Ororon's fixed-target Hypersense slice. */
public final class OroronRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private OroronRegressionTest() {
    }

    /** Runs data, action, passive, constellation, ICD, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicAttackTimingAndMultipliers();
        testSpiritOrbParticlesCooldownAndPrivateIcd();
        testA1A4AndC1FixedTargetBranches();
        testBurstCadenceAndConstellations();
        testC6StackCapAndActiveCharacterBuff();
        testSnapshotIsolationAndFailClosedGuards();
        System.out.println("OroronRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Ororon ororon = new Ororon(null, null, 6);
        assertEquals(CharacterId.ORORON, ororon.getCharacterId(),
                "Ororon typed identity");
        assertEquals(CharacterId.ORORON, CharacterId.fromName("Ororon"),
                "Ororon name lookup");
        assertEquals(CharacterId.ORORON, CharacterId.fromNumericId(85),
                "Ororon numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.ORORON.getRegion(), "Ororon region");
        assertEquals(Element.ELECTRO, ororon.getElement(),
                "Ororon element");
        assertClose(9244.0,
                ororon.getBaseStats().get(StatType.BASE_HP),
                "Ororon base HP");
        assertClose(244.0,
                ororon.getBaseStats().get(StatType.BASE_ATK),
                "Ororon base ATK");
        assertClose(587.0,
                ororon.getBaseStats().get(StatType.BASE_DEF),
                "Ororon base DEF");
        assertClose(0.24,
                ororon.getBaseStats().get(StatType.ATK_PERCENT),
                "Ororon ascension ATK");
        assertClose(60.0, ororon.getEnergyCost(),
                "Ororon Energy cost");
        assertClose(15.0, ororon.getSkillCD(),
                "Ororon Skill cooldown");
        assertClose(15.0, ororon.getBurstCD(),
                "Ororon Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.ORORON,
                    new Ororon(null, null, constellation).getCharacterId(),
                    "Ororon explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Ororon/Ororon_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Ororon/Ororon_Multipliers.csv"), 39);
        assertCsvValue("Spirit Orb C3", 3.952);
        assertCsvValue("Soundwave Collision C5", 0.664);
    }

    private static void testBasicAttackTimingAndMultipliers() {
        Ororon ororon = new Ororon(null, null, 0);
        CombatSimulator simulator = simulatorWith(ororon);
        List<ActionRecord> records = captureOroronActions(simulator);
        double[] multipliers = { 0.930399, 0.815233, 1.282755 };
        double[] hitTimes = { 22.0, 52.0, 115.0 };
        double[] endTimes = { 32.0, 85.0, 155.0 };
        for (int index = 0; index < multipliers.length; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(index);
            assertEquals("Spiritvessel Snapshot N" + (index + 1),
                    record.action.getName(), "Ororon Normal name");
            assertClose(hitTimes[index] * FRAME, record.time,
                    "Ororon N" + (index + 1) + " impact frame");
            assertClose(multipliers[index],
                    record.action.getDamagePercent(),
                    "Ororon N" + (index + 1) + " multiplier");
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Ororon Normal element");
            assertEquals(ActionType.NORMAL,
                    record.action.getActionType(),
                    "Ororon Normal type");
            assertClose(endTimes[index] * FRAME,
                    simulator.getCurrentTime(),
                    "Ororon N" + (index + 1) + " duration");
        }

        int beforeCharge = records.size();
        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(beforeCharge, records.size(),
                "Ororon charged arrow retains travel past animation");
        advanceTo(simulator, chargedCast + 94.0 * FRAME);
        ActionRecord charged = records.get(beforeCharge);
        assertClose(chargedCast + 94.0 * FRAME, charged.time,
                "Ororon fully charged impact frame");
        assertClose(2.108, charged.action.getDamagePercent(),
                "Ororon fully charged multiplier");
        assertEquals(Element.ELECTRO, charged.action.getElement(),
                "Ororon fully charged element");
        assertEquals(ICDType.None, charged.action.getICDType(),
                "Ororon fully charged no ICD");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = records.get(records.size() - 1);
        assertClose(plungeCast, plunge.time,
                "Ororon high Plunge resolves at impact input");
        assertClose(2.6086, plunge.action.getDamagePercent(),
                "Ororon high Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                plunge.action.getActionType(), "Ororon Plunge type");
        assertClose(plungeCast + 58.0 * FRAME,
                simulator.getCurrentTime(), "Ororon Plunge duration");
    }

    private static void testSpiritOrbParticlesCooldownAndPrivateIcd() {
        Ororon c0 = new Ororon(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureOroronActions(simulator);
        List<Double> particles = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(31.0 * FRAME, simulator.getCurrentTime(),
                "Ororon Skill action duration");
        assertEquals(0, named(records, "Night's Sling").size(),
                "Ororon Spirit Orb waits for travel");
        assertClose(14.0 * FRAME + 15.0,
                c0.getSkillCooldownEndTime(),
                "Ororon Skill cooldown starts at frame 14");
        advanceTo(simulator, 41.0 * FRAME - 0.001);
        assertEquals(0, named(records, "Night's Sling").size(),
                "Ororon Spirit Orb pre-impact boundary");
        simulator.advanceTime(0.001);
        List<ActionRecord> skill = named(records, "Night's Sling");
        assertEquals(1, skill.size(),
                "Ororon one-target Spirit Orb hit count");
        assertClose(3.3592, skill.get(0).action.getDamagePercent(),
                "Ororon C0 Spirit Orb multiplier");
        assertEquals(ICDTag.ElementalSkill,
                skill.get(0).action.getICDTag(),
                "Ororon Spirit Orb ICD tag");
        assertEquals(0, particles.size(),
                "Ororon particles retain travel time");
        advanceTo(simulator, 141.0 * FRAME - 0.001);
        assertEquals(0, particles.size(),
                "Ororon particle pre-arrival boundary");
        simulator.advanceTime(0.001);
        assertEquals(1, particles.size(),
                "Ororon one particle event");
        assertClose(3.0, particles.get(0),
                "Ororon three Electro particles");

        Ororon c3 = new Ororon(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureOroronActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        advanceTo(c3Simulator, 41.0 * FRAME);
        assertClose(3.952,
                named(c3Records, "Night's Sling").get(0)
                        .action.getDamagePercent(),
                "Ororon C3 Skill talent increase");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                "Ororon", ICDTag.Ororon_Soundwave,
                ICDType.OroronSoundwave, 0.0),
                "Ororon Soundwave first application");
        assertTrue(!icd.checkApplication(
                "Ororon", ICDTag.Ororon_Soundwave,
                ICDType.OroronSoundwave, 2.999),
                "Ororon Soundwave three-second pre-boundary");
        assertTrue(icd.checkApplication(
                "Ororon", ICDTag.ElementalBurst,
                ICDType.Standard, 2.999),
                "Ororon Ritual ICD remains independent");
        assertTrue(icd.checkApplication(
                "Ororon", ICDTag.Ororon_Soundwave,
                ICDType.OroronSoundwave, 3.0),
                "Ororon Soundwave exact three-second boundary");
    }

    private static void testA1A4AndC1FixedTargetBranches() {
        Ororon ororon = new Ororon(null, null, 1);
        TestCharacter ally = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(ororon, ally);
        List<ActionRecord> records = captureOroronActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 41.0 * FRAME);
        assertTrue(ororon.isC1MarkActive(simulator.getCurrentTime()),
                "Ororon C1 mark starts on Spirit Orb impact");
        simulator.setActiveCharacter(CharacterId.XINGQIU);
        ororon.spendEnergy(60.0);
        ally.spendEnergy(40.0);

        AttackAction hydroPlunge = elementalHit(
                "Ally Hydro Plunge", Element.HYDRO, ActionType.PLUNGE);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydroPlunge);
        assertClose(5.0, ororon.getNightsoulPoints(),
                "Ororon A1 first teammate hit points");
        assertClose(3.0, ally.getTotalFlatEnergy(),
                "Ororon A4 active-character Energy");
        assertClose(3.0, ororon.getTotalFlatEnergy(),
                "Ororon A4 off-field self Energy");
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydroPlunge);
        assertClose(5.0, ororon.getNightsoulPoints(),
                "Ororon A1 0.3-second point gate");
        assertEquals(1, ororon.getA4TriggerCount(),
                "Ororon A4 one-second gate");

        simulator.advanceTime(0.3);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydroPlunge);
        assertClose(10.0, ororon.getNightsoulPoints(),
                "Ororon A1 second eligible teammate hit");
        double reactionTime = simulator.getCurrentTime();
        simulator.notifyReaction(ReactionResult.transform(
                1.0,
                "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED), ally);
        assertClose(0.0, ororon.getNightsoulPoints(),
                "Ororon Hypersense consumes ten points");
        assertEquals(0, named(records, "Hypersense").size(),
                "Ororon Hypersense waits twelve frames");
        advanceTo(simulator, reactionTime + 12.0 * FRAME);
        AttackAction hypersense = named(records, "Hypersense")
                .get(0).action;
        assertClose(1.6, hypersense.getDamagePercent(),
                "Ororon A1 Hypersense multiplier");
        assertClose(0.5,
                bonus(hypersense, StatType.DMG_BONUS_ALL),
                "Ororon C1 marked-target Hypersense bonus");
        assertEquals(ICDType.None, hypersense.getICDType(),
                "Ororon Hypersense no ICD");

        simulator.advanceTime(0.5);
        simulator.notifyReaction(ReactionResult.transform(
                1.0,
                "Lunar-Charged",
                ReactionResult.Kind.LUNAR_CHARGED), ally);
        assertEquals(1, named(records, "Hypersense").size(),
                "Ororon Hypersense 1.8-second cooldown");
        advanceTo(simulator, 41.0 * FRAME + 1.0);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydroPlunge);
        advanceTo(simulator, 41.0 * FRAME + 2.0);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydroPlunge);
        assertEquals(3, ororon.getA4TriggerCount(),
                "Ororon A4 three-trigger cap");
        assertClose(9.0, ally.getTotalFlatEnergy(),
                "Ororon A4 active Energy cap total");
        assertClose(9.0, ororon.getTotalFlatEnergy(),
                "Ororon A4 self Energy cap total");
    }

    private static void testBurstCadenceAndConstellations() {
        Ororon c0 = new Ororon(null, null, 0);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureOroronActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.BURST);
        assertClose(62.0 * FRAME, c0Simulator.getCurrentTime(),
                "Ororon Burst action duration");
        assertClose(0.0, c0.getCurrentEnergy(),
                "Ororon Burst spends Energy at frame three");
        assertEquals(1, named(c0Records, "Dark Voices Echo Ritual").size(),
                "Ororon Ritual initial hit count");
        assertClose(36.0 * FRAME,
                named(c0Records, "Dark Voices Echo Ritual").get(0).time,
                "Ororon Ritual frame 36");
        advanceTo(c0Simulator, 9.001);
        assertEquals(8, named(c0Records, "Supersonic Oculus").size(),
                "Ororon C0 Soundwaves at seconds one through eight");

        Ororon c6 = new Ororon(null, null, 6);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureOroronActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.BURST);
        assertClose(8.0, c6.getCurrentEnergy(),
                "Ororon C4 restores eight Energy at Ritual impact");
        ActionRecord c6Hypersense = named(c6Records, "Hypersense C6")
                .get(0);
        assertClose(12.0 * FRAME, c6Hypersense.time,
                "Ororon C6 Hypersense frame 12");
        assertClose(3.2, c6Hypersense.action.getDamagePercent(),
                "Ororon C6 Hypersense multiplier");
        ActionRecord ritual = named(c6Records, "Dark Voices Echo Ritual")
                .get(0);
        assertClose(3.48768, ritual.action.getDamagePercent(),
                "Ororon C5 Ritual talent increase");
        assertClose(0.08,
                bonus(ritual.action, StatType.ELECTRO_DMG_BONUS),
                "Ororon C2 initial Electro stack");
        ActionRecord firstSoundwave = named(
                c6Records, "Supersonic Oculus").get(0);
        assertClose(0.75, firstSoundwave.time,
                "Ororon C4 first Soundwave interval");
        assertClose(0.664, firstSoundwave.action.getDamagePercent(),
                "Ororon C5 Soundwave talent increase");
        assertClose(0.16,
                bonus(firstSoundwave.action,
                        StatType.ELECTRO_DMG_BONUS),
                "Ororon C2 stack after Ritual hit");
        assertEquals(ICDType.OroronSoundwave,
                firstSoundwave.action.getICDType(),
                "Ororon Soundwave private ICD type");
        assertEquals(ICDTag.Ororon_Soundwave,
                firstSoundwave.action.getICDTag(),
                "Ororon Soundwave private ICD tag");
        advanceTo(c6Simulator, 9.001);
        assertEquals(11, named(c6Records, "Supersonic Oculus").size(),
                "Ororon C4 0.75-second cadence in nine-second window");
        assertEquals(0, c6.getC2Stacks(c6Simulator.getCurrentTime()),
                "Ororon C2 exact nine-second expiry");
    }

    private static void testC6StackCapAndActiveCharacterBuff() {
        Ororon ororon = new Ororon(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.XINGQIU, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(ororon, ally);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, ororon.getC6AtkStackCount(
                simulator.getCurrentTime()),
                "Ororon C6 Burst Hypersense grants one stack");
        assertClose(0.1, simulatorAtkPercent(simulator, ororon),
                "Ororon C6 active owner ATK stack");
        simulator.setActiveCharacter(CharacterId.XINGQIU);
        assertClose(0.1, simulatorAtkPercent(simulator, ally),
                "Ororon C6 follows the active character");

        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 62.0 * FRAME + 41.0 * FRAME);
        AttackAction hydro = elementalHit(
                "Hydro trigger", Element.HYDRO, ActionType.PLUNGE);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydro);
        simulator.advanceTime(0.3);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydro);
        simulator.notifyReaction(ReactionResult.transform(
                1.0,
                "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED), ally);
        simulator.advanceTime(1.8);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydro);
        simulator.advanceTime(0.3);
        simulator.performActionWithoutTimeAdvance(
                CharacterId.XINGQIU, hydro);
        simulator.notifyReaction(ReactionResult.transform(
                1.0,
                "Lunar-Charged",
                ReactionResult.Kind.LUNAR_CHARGED), ally);
        assertEquals(3, ororon.getC6AtkStackCount(
                simulator.getCurrentTime()),
                "Ororon C6 independently expiring stack count");
        assertClose(0.3, simulatorAtkPercent(simulator, ally),
                "Ororon C6 three-stack active-character ATK cap");
    }

    private static void testSnapshotIsolationAndFailClosedGuards() {
        Ororon ororon = new Ororon(null, null, 0);
        CombatSimulator simulator = simulatorWith(ororon);
        List<ActionRecord> records = captureOroronActions(simulator);
        List<Double> particles = captureElectroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 3.0);
        assertEquals(1, named(records, "Night's Sling").size(),
                "Ororon live pending Spirit Orb resolves once");
        assertEquals(1, particles.size(),
                "Ororon live pending particles resolve once");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 3.0);
        assertEquals(2, named(records, "Night's Sling").size(),
                "Ororon restore reconstructs one Spirit Orb");
        assertEquals(2, particles.size(),
                "Ororon restore reconstructs one particle event");
        assertEquals(0, ororon.getPendingHitCount(),
                "Ororon restore leaves no stale impacts");

        assertTrue(!ororon.isNightsoulBurstTeamPlumbingRepresented(),
                "Ororon Nightsoul Burst team plumbing fails closed");
        assertTrue(!ororon.isNightsoulAlignedDamageTriggerRepresented(),
                "Ororon generic Nightsoul damage trigger fails closed");
        assertTrue(!ororon.isMovementGeometryRepresented(),
                "Ororon movement and geometry excluded");
        assertTrue(!ororon.isMultiTargetBounceRepresented(),
                "Ororon bounces and multi-target excluded");
        assertTrue(!ororon.isTauntRepresented(),
                "Ororon taunt excluded");
        assertTrue(!ororon.isHitlagRepresented(),
                "Ororon hitlag excluded");
        assertTrue(!ororon.isStaminaRepresented(),
                "Ororon stamina excluded");
        assertTrue(!ororon.isLowPlungeRepresented(),
                "Ororon low Plunge excluded");
        assertTrue(!ororon.isAimWeakspotRepresented(),
                "Ororon aim weakspots excluded");
        assertTrue(!ororon.isTargetPositionSelectionRepresented(),
                "Ororon target-position selection excluded");

        assertThrows(IllegalArgumentException.class,
                () -> new Ororon(null, null, -1),
                "Ororon rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Ororon(null, null, 7),
                "Ororon rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> ororon.onAction(null, simulator),
                "Ororon rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.ORORON,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Ororon rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Ororon rejects unsupported Dash action");
        Ororon external = new Ororon(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Ororon rejects binding outside simulator party");
        SnapshotAwareCharacterEffect.State foreign =
                external.captureCharacterState();
        assertTrue(!ororon.acceptsCharacterState(foreign),
                "Ororon rejects another instance state");
        Ororon isolated = new Ororon(null, null, 1);
        CombatSimulator isolatedSimulator = simulatorWith(isolated);
        perform(isolatedSimulator, CharacterActionKey.SKILL);
        advanceTo(isolatedSimulator, 41.0 * FRAME);
        assertTrue(isolated.isC1MarkActive(
                isolatedSimulator.getCurrentTime()),
                "Ororon first instance owns its C1 mark");
        assertTrue(!external.isC1MarkActive(
                isolatedSimulator.getCurrentTime()),
                "Ororon second instance remains isolated");
        Ororon reused = new Ororon(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Ororon rejects cross-simulator reuse");
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.ORORON, CharacterActionRequest.of(key));
    }

    private static AttackAction elementalHit(
            String name,
            Element element,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        return action;
    }

    private static List<ActionRecord> captureOroronActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ORORON) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureElectroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
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

    private static double simulatorAtkPercent(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(StatType.ATK_PERCENT)
                - character.getBaseStats().get(StatType.ATK_PERCENT);
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
            assertTrue(lines.get(index).startsWith("Ororon,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Ororon/Ororon_Status.csv",
                "config/characters/Ororon/Ororon_Multipliers.csv"
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
        throw new AssertionError("Ororon CSVs missing key " + key);
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

    /** Captured Ororon action and resolution timestamp. */
    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    /** Minimal typed teammate fixture for A1, A4, and C6 checks. */
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
