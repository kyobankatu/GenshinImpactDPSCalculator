package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.character.KaedeharaKazuha;
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
import simulation.event.SimpleTimerEvent;

/** Focused regression checks for Kazuha's stationary Swirl support slice. */
public final class KaedeharaKazuhaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KaedeharaKazuhaRegressionTest() {
    }

    /** Runs data, timing, support, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicAttackTimingsAndMultipliers();
        testSkillTimelinesCooldownsParticlesAndC4();
        testBurstHitsSnapshotsAndConstellations();
        testA4CaptureAndElementalCoexistence();
        testC6InfusionLiveMasterySharedIcdAndSwitchReset();
        testSnapshotRestoreWithoutDuplicateDelayedHits();
        testInvalidInputsAndSimulatorIsolation();
        System.out.println("KaedeharaKazuhaRegressionTest passed");
    }

    private static void testBasicAttackTimingsAndMultipliers() {
        KaedeharaKazuha kazuha = new KaedeharaKazuha(null, null, 0);
        CombatSimulator simulator = simulatorWith(kazuha);
        List<ActionRecord> records = captureActions(simulator);
        int[][] hitmarks = {
            { 13 }, { 11 }, { 16, 26 }, { 16 }, { 15, 19, 28 }
        };
        int[] durations = { 22, 26, 41, 46, 80 };
        int[] hitlagFrames = { 6, 6, 8, 8, 8 };
        double[][] multipliers = {
            { 0.82634 }, { 0.83108 }, { 0.474, 0.5688 },
            { 1.11548 }, { 0.4661, 0.4661, 0.4661 }
        };
        int recordIndex = 0;
        for (int step = 0; step < hitmarks.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            for (int hit = 0; hit < hitmarks[step].length; hit++) {
                ActionRecord record = records.get(recordIndex++);
                assertClose(castTime + hitmarks[step][hit] * FRAME,
                        record.time, "Kazuha Normal hitmark");
                assertClose(multipliers[step][hit],
                        record.action.getDamagePercent(),
                        "Kazuha Normal multiplier");
                assertEquals(Element.PHYSICAL, record.action.getElement(),
                        "Kazuha uninfused Normal element");
                assertEquals(ActionType.NORMAL,
                        record.action.getActionType(),
                        "Kazuha Normal category");
            }
            assertClose(castTime
                            + (durations[step] + hitlagFrames[step]) * FRAME,
                    simulator.getCurrentTime(),
                    "Kazuha Normal recovery");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals("Garyuu Bladework N1",
                records.get(recordIndex).action.getName(),
                "Kazuha Normal string wraps after N5");

        KaedeharaKazuha charged = new KaedeharaKazuha(null, null, 0);
        CombatSimulator chargedSimulator = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertClose(55.0 * FRAME, chargedSimulator.getCurrentTime(),
                "Kazuha Charged recovery");
        assertEquals(2, chargedRecords.size(),
                "Kazuha Charged resolves two components");
        assertClose(20.0 * FRAME, chargedRecords.get(0).time,
                "Kazuha Charged first hitmark");
        assertClose(20.0 * FRAME, chargedRecords.get(1).time,
                "Kazuha Charged second hitmark");
        assertClose(0.79, chargedRecords.get(0).action.getDamagePercent(),
                "Kazuha Charged first multiplier");
        assertClose(1.37144,
                chargedRecords.get(1).action.getDamagePercent(),
                "Kazuha Charged second multiplier");

        KaedeharaKazuha plunge = new KaedeharaKazuha(null, null, 0);
        CombatSimulator plungeSimulator = simulatorWith(plunge);
        List<ActionRecord> plungeRecords = captureActions(plungeSimulator);
        perform(plungeSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plungeHit = onlyNamed(
                plungeRecords, "Garyuu Bladework High Plunge");
        assertClose(40.0 * FRAME, plungeHit.time,
                "Kazuha external High Plunge hitmark");
        assertClose(41.0 * FRAME, plungeSimulator.getCurrentTime(),
                "Kazuha external High Plunge recovery");
        assertClose(3.75499, plungeHit.action.getDamagePercent(),
                "Kazuha High Plunge multiplier");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        KaedeharaKazuha kazuha = new KaedeharaKazuha(null, null, 6);
        assertEquals(CharacterId.KAEDEHARA_KAZUHA,
                kazuha.getCharacterId(), "Kazuha typed identity");
        assertEquals(CharacterId.KAEDEHARA_KAZUHA,
                CharacterId.fromName("Kaedehara Kazuha"),
                "Kazuha name lookup");
        assertEquals(CharacterId.KAEDEHARA_KAZUHA,
                CharacterId.fromNumericId(43),
                "Kazuha numeric lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KAEDEHARA_KAZUHA.getRegion(),
                "Kazuha region");
        assertEquals(Element.ANEMO, kazuha.getElement(),
                "Kazuha element");
        assertClose(13348.0,
                kazuha.getBaseStats().get(StatType.BASE_HP),
                "Kazuha base HP");
        assertClose(297.0,
                kazuha.getBaseStats().get(StatType.BASE_ATK),
                "Kazuha base ATK");
        assertClose(807.0,
                kazuha.getBaseStats().get(StatType.BASE_DEF),
                "Kazuha base DEF");
        assertClose(115.0,
                kazuha.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Kazuha ascension Elemental Mastery");
        assertClose(60.0, kazuha.getEnergyCost(),
                "Kazuha Energy cost");
        assertClose(6.0, kazuha.getSkillCD(),
                "Kazuha default Skill cooldown");
        assertClose(15.0, kazuha.getBurstCD(),
                "Kazuha Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.KAEDEHARA_KAZUHA,
                    new KaedeharaKazuha(
                            null, null, constellation).getCharacterId(),
                    "Kazuha explicit C" + constellation + " construction");
        }

        assertCsvShape(Path.of(
                "config/characters/KaedeharaKazuha/"
                        + "KaedeharaKazuha_Status.csv"), 10);
        assertCsvShape(Path.of(
                "config/characters/KaedeharaKazuha/"
                        + "KaedeharaKazuha_Multipliers.csv"), 88);
        assertCsvValue("Base ATK", 297.0);
        assertCsvValue("Press", 3.264);
        assertCsvValue("Hold C3", 5.216);
        assertCsvValue("Burst DoT C5", 2.4);
        assertCsvValue("A4 Elemental DMG Bonus Per EM", 0.0004);
        assertCsvValue("C6 Normal Charged Plunge DMG Bonus Per EM", 0.002);
    }

    private static void testSkillTimelinesCooldownsParticlesAndC4() {
        KaedeharaKazuha press = new KaedeharaKazuha(null, null, 4);
        CombatSimulator pressSimulator = simulatorWith(press);
        List<ActionRecord> pressRecords = captureActions(pressSimulator);
        List<ParticleRecord> pressParticles = captureAnemoParticles(
                pressSimulator);
        press.spendEnergy(20.0);
        pressSimulator.getEnemy().setAura(Element.CRYO, 20.0);
        pressSimulator.getEnemy().setAura(Element.ELECTRO, 20.0);
        pressSimulator.getEnemy().setAura(Element.HYDRO, 20.0);
        pressSimulator.getEnemy().setAura(Element.PYRO, 20.0);
        addStatBuffAt(pressSimulator, press, 19.0 * FRAME,
                "Kazuha Midare snapshot EM",
                StatType.ELEMENTAL_MASTERY, 100.0);
        performSkill(pressSimulator, SkillActionMode.PRESS);

        ActionRecord pressHit = onlyNamed(
                pressRecords, "Chihayaburu Press");
        ActionRecord pressA1 = onlyNamed(
                pressRecords, "Chihayaburu Soumon Swordsmanship");
        ActionRecord pressMidare = onlyNamed(
                pressRecords, "Midare Ranzan High Plunge");
        assertClose(10.0 * FRAME, pressHit.time,
                "Kazuha Press hitmark");
        assertClose(60.0 * FRAME, pressA1.time,
                "Kazuha Press A1 hitmark");
        assertClose(61.0 * FRAME, pressMidare.time,
                "Kazuha Press Midare hitmark");
        assertClose(61.0 * FRAME, pressSimulator.getCurrentTime(),
                "Kazuha Press combined action duration");
        assertClose(3.84, pressHit.action.getDamagePercent(),
                "Kazuha C3 raises the Press Skill talent");
        assertEquals(Element.PYRO, press.getSkillAbsorption(),
                "Kazuha Press absorption priority starts at Pyro");
        assertEquals(Element.PYRO, pressA1.action.getElement(),
                "Kazuha Press A1 uses selected element");
        assertEquals(Element.ANEMO, pressMidare.action.getElement(),
                "Kazuha Press Midare remains Anemo");
        assertClose(115.0, pressHit.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Kazuha Skill snapshots at cast");
        assertClose(215.0, pressA1.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Kazuha A1 snapshots at plunge input");
        assertClose(215.0, pressMidare.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Kazuha Midare snapshots at plunge input");
        assertClose(43.0, press.getCurrentEnergy(),
                "Kazuha C4 Press grants three flat Energy below 45");
        double pressCooldownEnd = 8.0 * FRAME + 5.4;
        assertTrue(!press.canSkill(pressCooldownEnd - EPSILON),
                "Kazuha C1 Press cooldown stays closed before boundary");
        assertTrue(press.canSkill(pressCooldownEnd),
                "Kazuha C1 Press cooldown opens at boundary");
        advanceTo(pressSimulator, 111.0 * FRAME);
        assertEquals(1, pressParticles.size(),
                "Kazuha Press emits one particle event");
        assertClose(3.0, pressParticles.get(0).count,
                "Kazuha Press particle count");
        assertClose(110.0 * FRAME, pressParticles.get(0).time,
                "Kazuha Press particle travel time");

        KaedeharaKazuha hold = new KaedeharaKazuha(null, null, 4);
        CombatSimulator holdSimulator = simulatorWith(hold);
        List<ActionRecord> holdRecords = captureActions(holdSimulator);
        List<ParticleRecord> holdParticles = captureAnemoParticles(
                holdSimulator);
        hold.spendEnergy(20.0);
        holdSimulator.getEnemy().setAura(Element.CRYO, 20.0);
        holdSimulator.getEnemy().setAura(Element.ELECTRO, 20.0);
        holdSimulator.getEnemy().setAura(Element.HYDRO, 20.0);
        performSkill(holdSimulator, SkillActionMode.HOLD);

        ActionRecord holdHit = onlyNamed(
                holdRecords, "Chihayaburu Hold");
        ActionRecord holdA1 = onlyNamed(
                holdRecords, "Chihayaburu Soumon Swordsmanship");
        ActionRecord holdMidare = onlyNamed(
                holdRecords, "Midare Ranzan High Plunge");
        assertClose(33.0 * FRAME, holdHit.time,
                "Kazuha Hold hitmark");
        assertClose(98.0 * FRAME, holdA1.time,
                "Kazuha Hold A1 hitmark");
        assertClose(99.0 * FRAME, holdMidare.time,
                "Kazuha Hold Midare hitmark");
        assertClose(99.0 * FRAME, holdSimulator.getCurrentTime(),
                "Kazuha Hold combined action duration");
        assertClose(5.216, holdHit.action.getDamagePercent(),
                "Kazuha C3 raises the Hold Skill talent");
        assertEquals(Element.HYDRO, hold.getSkillAbsorption(),
                "Kazuha Hold absorption skips absent Pyro");
        assertEquals(Element.HYDRO, holdA1.action.getElement(),
                "Kazuha Hold A1 uses selected element");
        assertClose(44.0, hold.getCurrentEnergy(),
                "Kazuha C4 Hold grants four flat Energy below 45");
        double holdCooldownEnd = 31.0 * FRAME + 8.1;
        assertTrue(!hold.canSkill(holdCooldownEnd - EPSILON),
                "Kazuha C1 Hold cooldown stays closed before boundary");
        assertTrue(hold.canSkill(holdCooldownEnd),
                "Kazuha C1 Hold cooldown opens at boundary");
        advanceTo(holdSimulator, 134.0 * FRAME);
        assertEquals(1, holdParticles.size(),
                "Kazuha Hold emits one particle event");
        assertClose(4.0, holdParticles.get(0).count,
                "Kazuha Hold particle count");
        assertClose(133.0 * FRAME, holdParticles.get(0).time,
                "Kazuha Hold particle travel time");

        KaedeharaKazuha threshold = new KaedeharaKazuha(null, null, 4);
        CombatSimulator thresholdSimulator = simulatorWith(threshold);
        threshold.spendEnergy(15.0);
        performSkill(thresholdSimulator, SkillActionMode.PRESS);
        assertClose(45.0, threshold.getCurrentEnergy(),
                "Kazuha C4 excludes the exact 45-Energy boundary");

        KaedeharaKazuha delayedHold = new KaedeharaKazuha(null, null, 0);
        CombatSimulator delayedHoldSimulator = simulatorWith(delayedHold);
        addAuraAt(delayedHoldSimulator, 4.0 * FRAME, Element.PYRO);
        performSkill(delayedHoldSimulator, SkillActionMode.HOLD);
        assertEquals(Element.PYRO, delayedHold.getSkillAbsorption(),
                "Kazuha Hold retries absorption during the cast");

        KaedeharaKazuha delayedPress = new KaedeharaKazuha(null, null, 0);
        CombatSimulator delayedPressSimulator = simulatorWith(delayedPress);
        List<ActionRecord> delayedPressRecords = captureActions(
                delayedPressSimulator);
        addAuraAt(delayedPressSimulator, 4.0 * FRAME, Element.PYRO);
        performSkill(delayedPressSimulator, SkillActionMode.PRESS);
        assertEquals(null, delayedPress.getSkillAbsorption(),
                "Kazuha Press samples absorption only at cast frame one");
        assertEquals(0, named(delayedPressRecords,
                "Chihayaburu Soumon Swordsmanship").size(),
                "Kazuha Press without cast-time absorption omits A1");
    }

    private static void testBurstHitsSnapshotsAndConstellations() {
        KaedeharaKazuha c2 = new KaedeharaKazuha(null, null, 2);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(c2, ally);
        List<ActionRecord> records = captureActions(simulator);
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        perform(simulator, CharacterActionKey.BURST);

        ActionRecord initial = onlyNamed(records, "Kazuha Slash Initial");
        assertClose(82.0 * FRAME, initial.time,
                "Kazuha Burst initial hitmark");
        assertClose(4.4608, initial.action.getDamagePercent(),
                "Kazuha Burst initial multiplier before C5");
        assertClose(115.0, initial.action.getStatSnapshot().get(
                StatType.ELEMENTAL_MASTERY),
                "Kazuha Burst initial snapshots at cast");
        assertEquals(Element.PYRO, c2.getBurstAbsorption(),
                "Kazuha Burst selects enemy Pyro aura");
        assertClose(609.0 * FRAME,
                c2.getC2ExpirationTime(),
                "Kazuha C2 remains active through the final field tick");
        assertClose(315.0, effectiveStat(
                simulator, c2, StatType.ELEMENTAL_MASTERY),
                "Kazuha active C2 receives exactly 200 EM");
        simulator.switchCharacter(CharacterId.QIQI);
        assertClose(315.0, effectiveStat(
                simulator, c2, StatType.ELEMENTAL_MASTERY),
                "Kazuha off-field C2 owner buff replaces field buff");
        assertClose(200.0, effectiveStat(
                simulator, ally, StatType.ELEMENTAL_MASTERY),
                "Kazuha C2 buffs the active ally");

        advanceTo(simulator, 608.0 * FRAME);
        assertClose(200.0, effectiveStat(
                simulator, ally, StatType.ELEMENTAL_MASTERY),
                "Kazuha C2 remains active on the final Burst tick");
        advanceTo(simulator, 609.0 * FRAME);
        List<ActionRecord> dots = named(records, "Kazuha Slash DoT");
        List<ActionRecord> absorbed = named(
                records, "Kazuha Slash Absorbed DoT");
        assertEquals(5, dots.size(),
                "Kazuha Burst emits five Anemo DoTs");
        assertEquals(5, absorbed.size(),
                "Kazuha Burst emits five absorbed DoTs");
        int[] dotFrames = { 140, 257, 374, 491, 608 };
        for (int index = 0; index < dotFrames.length; index++) {
            assertClose(dotFrames[index] * FRAME, dots.get(index).time,
                    "Kazuha Burst Anemo DoT timing");
            assertClose(dotFrames[index] * FRAME,
                    absorbed.get(index).time,
                    "Kazuha Burst absorbed DoT timing");
            assertClose(2.04,
                    dots.get(index).action.getDamagePercent(),
                    "Kazuha Burst Anemo DoT multiplier");
            assertClose(0.612,
                    absorbed.get(index).action.getDamagePercent(),
                    "Kazuha Burst absorbed DoT multiplier");
            assertEquals(Element.PYRO,
                    absorbed.get(index).action.getElement(),
                    "Kazuha Burst absorbed DoT element");
            assertClose(115.0,
                    dots.get(index).action.getStatSnapshot().get(
                            StatType.ELEMENTAL_MASTERY),
                    "Kazuha Burst DoT snapshots before C2");
            assertClose(115.0,
                    absorbed.get(index).action.getStatSnapshot().get(
                            StatType.ELEMENTAL_MASTERY),
                    "Kazuha absorbed DoT snapshots before C2");
        }
        assertClose(115.0, effectiveStat(
                simulator, c2, StatType.ELEMENTAL_MASTERY),
                "Kazuha C2 expires half-open after the field endpoint");
        assertClose(0.0, effectiveStat(
                simulator, ally, StatType.ELEMENTAL_MASTERY),
                "Kazuha C2 active-character buff expires half-open");

        KaedeharaKazuha c1 = new KaedeharaKazuha(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        performSkill(c1Simulator, SkillActionMode.PRESS);
        assertTrue(!c1.canSkill(c1Simulator.getCurrentTime()),
                "Kazuha C1 Skill remains cooling before Burst");
        perform(c1Simulator, CharacterActionKey.BURST);
        assertTrue(c1.canSkill(c1Simulator.getCurrentTime()),
                "Kazuha C1 Burst resets the Skill cooldown at cast");

        KaedeharaKazuha c5 = new KaedeharaKazuha(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        c5Simulator.getEnemy().setAura(Element.HYDRO, 20.0);
        perform(c5Simulator, CharacterActionKey.BURST);
        advanceTo(c5Simulator, 141.0 * FRAME);
        assertClose(5.248,
                onlyNamed(c5Records, "Kazuha Slash Initial")
                        .action.getDamagePercent(),
                "Kazuha C5 raises the Burst initial talent");
        assertClose(2.4,
                onlyNamed(c5Records, "Kazuha Slash DoT")
                        .action.getDamagePercent(),
                "Kazuha C5 raises the Burst DoT talent");
        assertClose(0.72,
                onlyNamed(c5Records, "Kazuha Slash Absorbed DoT")
                        .action.getDamagePercent(),
                "Kazuha C5 raises the absorbed DoT talent");
    }

    private static void testA4CaptureAndElementalCoexistence() {
        KaedeharaKazuha kazuha = new KaedeharaKazuha(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(kazuha, ally);
        kazuha.addBuff(statBuff(
                "Kazuha A4 first EM", simulator.getCurrentTime(),
                StatType.ELEMENTAL_MASTERY, 100.0));
        notifySwirl(simulator, kazuha, Element.PYRO);
        assertClose(0.086, effectiveStat(
                simulator, ally, StatType.PYRO_DMG_BONUS),
                "Kazuha A4 captures 215 EM for Pyro");

        kazuha.addBuff(statBuff(
                "Kazuha A4 second EM", simulator.getCurrentTime(),
                StatType.ELEMENTAL_MASTERY, 50.0));
        notifySwirl(simulator, kazuha, Element.HYDRO);
        assertClose(0.086, effectiveStat(
                simulator, ally, StatType.PYRO_DMG_BONUS),
                "Kazuha A4 Pyro snapshot ignores later EM changes");
        assertClose(0.106, effectiveStat(
                simulator, ally, StatType.HYDRO_DMG_BONUS),
                "Kazuha A4 independently captures Hydro EM");

        kazuha.addBuff(statBuff(
                "Kazuha A4 refresh EM", simulator.getCurrentTime(),
                StatType.ELEMENTAL_MASTERY, 500.0));
        notifySwirl(simulator, kazuha, Element.PYRO);
        assertClose(0.306, effectiveStat(
                simulator, ally, StatType.PYRO_DMG_BONUS),
                "Kazuha A4 same-element refresh replaces captured value");
        assertClose(0.106, effectiveStat(
                simulator, ally, StatType.HYDRO_DMG_BONUS),
                "Kazuha A4 different elements coexist");

        notifySwirl(simulator, ally, Element.ELECTRO);
        assertClose(0.0, effectiveStat(
                simulator, ally, StatType.ELECTRO_DMG_BONUS),
                "Kazuha A4 ignores another character's Swirl");
        advanceTo(simulator, 8.0);
        assertClose(0.0, effectiveStat(
                simulator, ally, StatType.PYRO_DMG_BONUS),
                "Kazuha A4 expires half-open at eight seconds");
        assertClose(0.0, effectiveStat(
                simulator, ally, StatType.HYDRO_DMG_BONUS),
                "Kazuha A4 elemental instances share duration semantics");
    }

    private static void testC6InfusionLiveMasterySharedIcdAndSwitchReset() {
        KaedeharaKazuha skillPlunge = new KaedeharaKazuha(null, null, 6);
        skillPlunge.addBuff(statBuff(
                "Kazuha C6 Skill Plunge EM", 0.0,
                StatType.ELEMENTAL_MASTERY, 100.0));
        CombatSimulator skillPlungeSimulator = simulatorWith(skillPlunge);
        List<ActionRecord> skillPlungeRecords = captureActions(
                skillPlungeSimulator);
        skillPlungeSimulator.getEnemy().setAura(Element.PYRO, 20.0);
        performSkill(skillPlungeSimulator, SkillActionMode.PRESS);
        assertClose(0.43, onlyNamed(
                skillPlungeRecords,
                "Chihayaburu Soumon Swordsmanship")
                        .action.getExtraBonuses().getOrDefault(
                                StatType.PLUNGING_ATTACK_DMG_BONUS, 0.0),
                "Kazuha C6 A1 reads live EM");
        assertClose(0.43, onlyNamed(
                skillPlungeRecords, "Midare Ranzan High Plunge")
                        .action.getExtraBonuses().getOrDefault(
                                StatType.PLUNGING_ATTACK_DMG_BONUS, 0.0),
                "Kazuha C6 Midare reads live EM");

        KaedeharaKazuha kazuha = new KaedeharaKazuha(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.QIQI, Element.CRYO);
        CombatSimulator simulator = simulatorWith(kazuha, ally);
        List<ActionRecord> records = captureActions(simulator);
        int[] pyroSwirls = { 0 };
        simulator.addReactionListener((result, source, time, active) -> {
            if (source == kazuha
                    && result.isSwirl()
                    && result.getSwirlElement() == Element.PYRO) {
                pyroSwirls[0]++;
            }
        });

        performSkill(simulator, SkillActionMode.PRESS);
        assertClose(10.0 * FRAME + 5.0,
                kazuha.getC6ExpirationTime(),
                "Kazuha C6 Skill infusion starts on the Skill hit");
        kazuha.addBuff(statBuff(
                "Kazuha C6 first live EM", simulator.getCurrentTime(),
                StatType.ELEMENTAL_MASTERY, 100.0));
        simulator.getEnemy().setAura(Element.PYRO, 20.0);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord firstNormal = onlyNamed(
                records, "Garyuu Bladework N1");
        assertEquals(Element.ANEMO, firstNormal.action.getElement(),
                "Kazuha C6 infuses Normal attacks with Anemo");
        assertEquals(ICDType.Standard, firstNormal.action.getICDType(),
                "Kazuha C6 Normal uses Standard ICD");
        assertEquals(ICDTag.Kazuha_C6_Infusion,
                firstNormal.action.getICDTag(),
                "Kazuha C6 Normal uses the shared basic-attack tag");
        assertClose(0.43, firstNormal.action.getExtraBonuses().getOrDefault(
                StatType.NORMAL_ATTACK_DMG_BONUS, 0.0),
                "Kazuha C6 Normal reads live 215 EM");

        kazuha.addBuff(statBuff(
                "Kazuha C6 second live EM", simulator.getCurrentTime(),
                StatType.ELEMENTAL_MASTERY, 50.0));
        perform(simulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = named(records,
                "Garyuu Bladework Charged-1");
        assertEquals(1, charged.size(),
                "Kazuha C6 resolves the first Charged component once");
        assertEquals(ICDTag.Kazuha_C6_Infusion,
                charged.get(0).action.getICDTag(),
                "Kazuha C6 Charged shares the Normal ICD tag");
        assertClose(0.53, charged.get(0).action.getExtraBonuses()
                        .getOrDefault(
                                StatType.CHARGED_ATTACK_DMG_BONUS, 0.0),
                "Kazuha C6 Charged reads later live 265 EM");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, pyroSwirls[0],
                "Kazuha C6 Normal and Charged share first-and-fourth-hit ICD");
        assertClose(0.0, effectiveStat(
                simulator, ally, StatType.PYRO_DMG_BONUS),
                "Kazuha C6 weapon Swirls do not trigger A4");
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord infusedPlunge = onlyNamed(
                records, "Garyuu Bladework High Plunge");
        assertEquals(Element.ANEMO, infusedPlunge.action.getElement(),
                "Kazuha C6 infuses external Plunging Attacks");
        assertEquals(ICDType.None, infusedPlunge.action.getICDType(),
                "Kazuha C6 external Plunge has no ICD");
        assertClose(0.53, infusedPlunge.action.getExtraBonuses().getOrDefault(
                StatType.PLUNGING_ATTACK_DMG_BONUS, 0.0),
                "Kazuha C6 external Plunge reads live EM");

        double expiration = kazuha.getC6ExpirationTime();
        advanceTo(simulator, expiration);
        simulator.switchCharacter(CharacterId.QIQI);
        simulator.switchCharacter(CharacterId.KAEDEHARA_KAZUHA);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> normals = named(
                records, "Garyuu Bladework N1");
        ActionRecord postExpiration = normals.get(normals.size() - 1);
        assertEquals(Element.PHYSICAL,
                postExpiration.action.getElement(),
                "Kazuha C6 infusion expires half-open");
        assertEquals(ICDType.None,
                postExpiration.action.getICDType(),
                "Kazuha physical Normal has no elemental ICD");
        assertEquals("Garyuu Bladework N1",
                postExpiration.action.getName(),
                "Kazuha switch resets the Normal string");
    }

    private static void testSnapshotRestoreWithoutDuplicateDelayedHits() {
        KaedeharaKazuha skill = new KaedeharaKazuha(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skill);
        List<ActionRecord> skillRecords = captureActions(skillSimulator);
        SimulatorSnapshot[] skillSnapshot = { null };
        skillSimulator.getEnemy().setAura(Element.PYRO, 20.0);
        skillSimulator.registerEvent(new SimpleTimerEvent(
                20.0 * FRAME, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                skillSnapshot[0] = activeSimulator.saveSnapshot();
            }
        });
        performSkill(skillSimulator, SkillActionMode.PRESS);
        assertTrue(skillSnapshot[0] != null,
                "Kazuha Skill snapshot captured during ascent");
        int branchSkillHits = named(skillRecords,
                "Chihayaburu Soumon Swordsmanship").size()
                + named(skillRecords, "Midare Ranzan High Plunge").size();
        assertEquals(2, branchSkillHits,
                "Kazuha Skill branch resolves A1 and Midare once");
        skillSimulator.restoreSnapshot(skillSnapshot[0]);
        skillSimulator.restoreSnapshot(skillSnapshot[0]);
        skillRecords.clear();
        advanceTo(skillSimulator, 61.0 * FRAME);
        assertEquals(branchSkillHits,
                named(skillRecords,
                        "Chihayaburu Soumon Swordsmanship").size()
                        + named(skillRecords,
                                "Midare Ranzan High Plunge").size(),
                "Kazuha repeated Skill restore schedules delayed hits once");

        KaedeharaKazuha burst = new KaedeharaKazuha(null, null, 2);
        CombatSimulator burstSimulator = simulatorWith(burst);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        burstSimulator.getEnemy().setAura(Element.HYDRO, 20.0);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot pendingBurst = burstSimulator.saveSnapshot();
        burstRecords.clear();
        advanceTo(burstSimulator, 609.0 * FRAME);
        int branchBurstHits = named(
                burstRecords, "Kazuha Slash DoT").size()
                + named(burstRecords,
                        "Kazuha Slash Absorbed DoT").size();
        assertEquals(10, branchBurstHits,
                "Kazuha Burst branch resolves ten delayed field hits");
        burstSimulator.restoreSnapshot(pendingBurst);
        burstSimulator.restoreSnapshot(pendingBurst);
        burstRecords.clear();
        advanceTo(burstSimulator, 609.0 * FRAME);
        assertEquals(branchBurstHits,
                named(burstRecords, "Kazuha Slash DoT").size()
                        + named(burstRecords,
                                "Kazuha Slash Absorbed DoT").size(),
                "Kazuha repeated Burst restore schedules delayed hits once");
        for (ActionRecord record : burstRecords) {
            assertClose(115.0, record.action.getStatSnapshot().get(
                    StatType.ELEMENTAL_MASTERY),
                    "Kazuha restored Burst hit retains pre-C2 snapshot");
        }
    }

    private static void testInvalidInputsAndSimulatorIsolation() {
        assertThrows(IllegalArgumentException.class,
                () -> new KaedeharaKazuha(null, null, -1),
                "Kazuha rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new KaedeharaKazuha(null, null, 7),
                "Kazuha rejects constellation above six");
        assertThrows(NullPointerException.class,
                () -> CharacterActionRequest.skill(null),
                "Kazuha action request rejects null Skill mode");

        KaedeharaKazuha invalid = new KaedeharaKazuha(null, null, 0);
        CombatSimulator invalidSimulator = simulatorWith(invalid);
        assertThrows(IllegalArgumentException.class,
                () -> invalid.onAction(null, invalidSimulator),
                "Kazuha rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> invalid.initializeForSimulator(null),
                "Kazuha rejects null simulator");
        assertThrows(IllegalArgumentException.class,
                () -> perform(invalidSimulator, CharacterActionKey.DASH),
                "Kazuha rejects Dash");

        KaedeharaKazuha reused = new KaedeharaKazuha(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Kazuha rejects cross-simulator reuse");

        KaedeharaKazuha absent = new KaedeharaKazuha(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> absent.initializeForSimulator(new CombatSimulator()),
                "Kazuha rejects a simulator that does not own it");

        KaedeharaKazuha owner = new KaedeharaKazuha(null, null, 0);
        KaedeharaKazuha foreign = new KaedeharaKazuha(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Kazuha rejects another instance's state");
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        new SnapshotAwareCharacterEffect.State() {
                        }, new CombatSimulator()),
                "Kazuha rejects a foreign state type");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
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
                CharacterId.KAEDEHARA_KAZUHA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.KAEDEHARA_KAZUHA,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId()
                    == CharacterId.KAEDEHARA_KAZUHA) {
                records.add(new ActionRecord(action, damage, time));
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

    private static ActionRecord onlyNamed(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> selected = named(records, name);
        assertEquals(1, selected.size(), name + " action count");
        return selected.get(0);
    }

    private static double effectiveStat(
            CombatSimulator simulator,
            Character character,
            StatType stat) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.get(stat);
    }

    private static SimpleBuff statBuff(
            String name,
            double startTime,
            StatType stat,
            double amount) {
        return new SimpleBuff(
                name,
                100.0,
                startTime,
                stats -> stats.add(stat, amount));
    }

    private static void addStatBuffAt(
            CombatSimulator simulator,
            Character character,
            double time,
            String name,
            StatType stat,
            double amount) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                character.addBuff(statBuff(name, time, stat, amount));
            }
        });
    }

    private static void addAuraAt(
            CombatSimulator simulator,
            double time,
            Element element) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                activeSimulator.getEnemy().setAura(element, 20.0);
            }
        });
    }

    private static void notifySwirl(
            CombatSimulator simulator,
            Character source,
            Element element) {
        simulator.notifyReaction(ReactionResult.transform(
                0.0,
                "Kazuha regression Swirl",
                ReactionResult.Kind.SWIRL,
                element), source);
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
            assertTrue(lines.get(index).startsWith("Kaedehara Kazuha,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/KaedeharaKazuha/"
                        + "KaedeharaKazuha_Status.csv",
                "config/characters/KaedeharaKazuha/"
                        + "KaedeharaKazuha_Multipliers.csv"
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
        throw new AssertionError("Kazuha CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected)
                == Double.doubleToLongBits(actual)) {
            return;
        }
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(
                    message + ": unexpected " + throwable, throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        @SuppressWarnings("unused")
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
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }
    }
}
