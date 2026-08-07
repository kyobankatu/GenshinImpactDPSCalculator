package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionCalculator;
import mechanics.reaction.ReactionResult;
import model.character.Lauma;
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

/** Focused regression checks for Lauma's fixed-target Lunar-Bloom slice. */
public final class LaumaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private LaumaRegressionTest() {
    }

    /** Runs data, action, Dew, reaction, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndBasics();
        testPressHoldAndAtomicDewBoundaries();
        testSanctuaryCadenceParticlesAndShred();
        testBurstPaleHymnAndReactionIsolation();
        testPassivesAndConstellations();
        testCooldownEnergyAndMoonsong();
        testSnapshotRestoreAndGenerationIsolation();
        testInvalidInputsAndExplicitExclusions();
        System.out.println("LaumaRegressionTest passed");
    }

    private static void testIdentityDataAndBasics() throws IOException {
        Lauma lauma = new Lauma(null, null, 0);
        assertEquals(CharacterId.LAUMA, lauma.getCharacterId(),
                "Lauma typed identity");
        assertEquals(CharacterId.LAUMA, CharacterId.fromName("Lauma"),
                "Lauma name lookup");
        assertEquals(CharacterId.LAUMA, CharacterId.fromNumericId(103),
                "Lauma numeric lookup");
        assertEquals(CharacterRegion.NOD_KRAI,
                CharacterId.LAUMA.getRegion(),
                "Lauma region");
        assertEquals(Element.DENDRO, lauma.getElement(),
                "Lauma element");
        assertClose(10654.0,
                lauma.getBaseStats().get(StatType.BASE_HP),
                "Lauma base HP");
        assertClose(255.0,
                lauma.getBaseStats().get(StatType.BASE_ATK),
                "Lauma base ATK");
        assertClose(669.0,
                lauma.getBaseStats().get(StatType.BASE_DEF),
                "Lauma base DEF");
        assertClose(315.2,
                lauma.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Lauma base plus ascension EM");
        assertClose(60.0, lauma.getEnergyCost(),
                "Lauma Burst cost");
        assertTrue(lauma.isLunarCharacter(),
                "Lauma contributes typed Moonsign membership");
        assertCsvShape(Path.of(
                "config/characters/Lauma/Lauma_Status.csv"), 13);
        assertCsvShape(Path.of(
                "config/characters/Lauma/Lauma_Multipliers.csv"), 70);
        assertCsvValue("N1", 0.572941);
        assertCsvValue("Hold Lunar-Bloom Per Dew C5", 3.04);
        assertCsvValue("Bloom Family EM Ratio C3", 5.5552);
        assertCsvValue("Pale Hymn Gain Frames", 96.0);
        assertCsvValue("Lunar-Bloom Base Bonus Per EM", 0.000175);

        CombatSimulator normals = simulatorWith(lauma);
        List<ActionRecord> normalRecords = captureLaumaActions(normals);
        perform(normals, CharacterActionKey.NORMAL);
        perform(normals, CharacterActionKey.NORMAL);
        perform(normals, CharacterActionKey.NORMAL);
        List<ActionRecord> normalHits = prefixed(
                normalRecords, "Peregrination of Linnunrata N");
        assertEquals(3, normalHits.size(),
                "Lauma has three catalyst Normal stages");
        assertClose(14.0 * FRAME, normalHits.get(0).time,
                "N1 impact frame");
        assertClose((29.0 + 11.0) * FRAME,
                normalHits.get(1).time,
                "N2 impact frame");
        assertClose((29.0 + 33.0 + 16.0) * FRAME,
                normalHits.get(2).time,
                "N3 impact frame");
        assertClose(0.756446,
                normalHits.get(2).action.getDamagePercent(),
                "N3 talent-9 multiplier");
        for (ActionRecord record : normalHits) {
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Catalyst Normals use standard ICD");
            assertEquals(ICDTag.NormalAttack,
                    record.action.getICDTag(),
                    "Catalyst Normals share Normal ICD");
            assertEquals(Element.DENDRO,
                    record.action.getElement(),
                    "Catalyst Normals deal Dendro damage");
        }

        Lauma chargedLauma = new Lauma(null, null, 0);
        CombatSimulator charged = simulatorWith(chargedLauma);
        List<ActionRecord> chargedRecords = captureLaumaActions(charged);
        perform(charged, CharacterActionKey.CHARGE);
        assertEquals(0, named(chargedRecords,
                "Peregrination Spiritcall Prayer").size(),
                "Charged hit remains queued after frame 68");
        charged.advanceTime(5.0 * FRAME + EPSILON);
        ActionRecord chargedHit = named(chargedRecords,
                "Peregrination Spiritcall Prayer").get(0);
        assertClose(73.0 * FRAME, chargedHit.time,
                "offensive Charged hit frame");
        assertClose(2.19368,
                chargedHit.action.getDamagePercent(),
                "Charged talent-9 multiplier");
        assertEquals(ICDType.None, chargedHit.action.getICDType(),
                "Charged hit has no application ICD");

        Lauma plungeLauma = new Lauma(null, null, 0);
        CombatSimulator plunge = simulatorWith(plungeLauma);
        List<ActionRecord> plungeRecords = captureLaumaActions(plunge);
        perform(plunge, CharacterActionKey.PLUNGE);
        ActionRecord highPlunge = named(plungeRecords,
                "Peregrination High Plunge").get(0);
        assertClose(0.0, highPlunge.time,
                "fixed High Plunge resolves at action ingress");
        assertClose(2.607632,
                highPlunge.action.getDamagePercent(),
                "High Plunge talent-9 multiplier");
        assertEquals(ActionType.PLUNGE,
                highPlunge.action.getActionType(),
                "High Plunge uses typed Plunge category");
    }

    private static void testPressHoldAndAtomicDewBoundaries() {
        Lauma pressLauma = new Lauma(null, null, 0);
        CombatSimulator press = simulatorWith(pressLauma);
        List<ActionRecord> pressRecords = captureLaumaActions(press);
        performSkill(press, SkillActionMode.PRESS);
        ActionRecord pressHit = named(
                pressRecords, "Hymn of Hunting Press").get(0);
        assertClose(16.0 * FRAME, pressHit.time,
                "Press Skill impact frame");
        assertClose(2.0672, pressHit.action.getDamagePercent(),
                "Press Skill talent-9 multiplier");
        assertClose(0.32,
                pressHit.action.getStatSnapshot().get(
                        StatType.SKILL_DMG_BONUS),
                "A4 caps Skill damage bonus at 32 percent");
        assertClose(0.6, press.getCurrentTime(),
                "Press uses sourced earliest-cancel duration");

        Lauma holdLauma = new Lauma(null, null, 0);
        CombatSimulator hold = simulatorWith(holdLauma);
        List<ActionRecord> holdRecords = captureLaumaActions(hold);
        for (int index = 0; index < 4; index++) {
            hold.incrementVerdantDewCount();
        }
        int[] dewAtBoundary = { -1, -1 };
        registerProbe(hold, 28.0 * FRAME,
                () -> dewAtBoundary[0] = hold.getVerdantDewCount());
        registerProbe(hold, 29.0 * FRAME + EPSILON,
                () -> dewAtBoundary[1] = hold.getVerdantDewCount());
        performSkill(hold, SkillActionMode.HOLD);
        assertEquals(4, dewAtBoundary[0],
                "Hold leaves Dew intact before frame 29");
        assertEquals(1, dewAtBoundary[1],
                "Hold atomically consumes three Dew at frame 29");
        assertEquals(1, hold.getVerdantDewCount(),
                "Hold preserves Dew above the three-stack cap");
        ActionRecord holdInitial = named(
                holdRecords, "Hymn of Eternal Rest Hold").get(0);
        ActionRecord holdLunar = named(
                holdRecords,
                "Hymn of Eternal Rest Lunar-Bloom").get(0);
        assertClose(45.0 * FRAME, holdInitial.time,
                "Hold initial impact frame");
        assertClose(holdInitial.time, holdLunar.time,
                "Hold packets share frame 45");
        assertClose(3.0 * 2.584,
                holdLunar.action.getDamagePercent(),
                "Hold multiplier uses actual atomic consumption");
        assertEquals(3, holdLauma.getStoredMoonsong(
                hold.getCurrentTime()),
                "Moonsong uses actual atomic consumption");

        Lauma oneDewLauma = new Lauma(null, null, 0);
        CombatSimulator oneDew = simulatorWith(oneDewLauma);
        List<ActionRecord> oneDewRecords = captureLaumaActions(oneDew);
        oneDew.incrementVerdantDewCount();
        performSkill(oneDew, SkillActionMode.HOLD);
        assertClose(2.584,
                named(oneDewRecords,
                        "Hymn of Eternal Rest Lunar-Bloom")
                        .get(0).action.getDamagePercent(),
                "Hold accepts exactly one Dew");
        assertEquals(0, oneDew.getVerdantDewCount(),
                "one-Dew Hold consumes exactly one");

        Lauma emptyLauma = new Lauma(null, null, 0);
        CombatSimulator empty = simulatorWith(emptyLauma);
        assertThrows(IllegalStateException.class,
                () -> performSkill(empty, SkillActionMode.HOLD),
                "Hold fails before action start without Dew");

        Lauma depletedLauma = new Lauma(null, null, 0);
        CombatSimulator depleted = simulatorWith(depletedLauma);
        depleted.incrementVerdantDewCount();
        registerProbe(depleted, 20.0 * FRAME,
                () -> depleted.consumeVerdantDewCount(1));
        assertThrows(IllegalStateException.class,
                () -> performSkill(depleted, SkillActionMode.HOLD),
                "Hold fails closed when atomic frame-29 consumption is zero");
    }

    private static void testSanctuaryCadenceParticlesAndShred() {
        Lauma lauma = new Lauma(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, false);
        CombatSimulator simulator = simulatorWith(lauma, ally);
        List<ActionRecord> actions = captureLaumaActions(simulator);
        List<ParticleRecord> particles = captureDendroParticles(simulator);
        lauma.restoreCurrentEnergy(0.0);
        performSkill(simulator, SkillActionMode.PRESS);
        advanceTo(simulator,
                (62.0 + 117.0 * 7.0) * FRAME + EPSILON);
        List<ActionRecord> sanctuary = named(
                actions, "Frostgrove Sanctuary");
        assertEquals(8, sanctuary.size(),
                "Sanctuary emits exactly eight ticks");
        assertClose(62.0 * FRAME, sanctuary.get(0).time,
                "Sanctuary first tick frame");
        for (int index = 1; index < sanctuary.size(); index++) {
            assertClose(117.0 * FRAME,
                    sanctuary.get(index).time
                            - sanctuary.get(index - 1).time,
                    "Sanctuary tick interval " + index);
        }
        assertClose(1.632,
                sanctuary.get(0).action.getDamagePercent(),
                "Sanctuary ATK multiplier");
        assertClose(315.2 * 3.264,
                sanctuary.get(0).action.getStatSnapshot().get(
                        StatType.FLAT_DMG_BONUS),
                "Sanctuary adds sourced EM scaling");
        assertClose(15.0, lauma.getTotalFlatEnergy(),
                "C4 grants three five-Energy packets");
        assertClose(30.6, lauma.getCurrentEnergy(),
                "C4 plus four same-element particle packets reach Energy");
        assertClose(0.225,
                resolvedStats(simulator, ally).get(
                        StatType.DENDRO_RES_SHRED),
                "Skill support shreds Dendro RES");
        assertClose(0.225,
                resolvedStats(simulator, ally).get(
                        StatType.HYDRO_RES_SHRED),
                "Skill support shreds Hydro RES");
        assertEquals(4, particles.size(),
                "3.3-second gate accepts four deterministic particle packets");
        for (ParticleRecord particle : particles) {
            assertClose(1.3, particle.count,
                    "particle packet uses pinned expected count");
        }
        double shredEnd = lauma.getSkillShredUntil();
        advanceTo(simulator, shredEnd);
        assertClose(0.0,
                resolvedStats(simulator, ally).get(
                        StatType.DENDRO_RES_SHRED),
                "Skill shred closes at the exact 10-second boundary");

        Lauma noEnemyLauma = new Lauma(null, null, 4);
        CombatSimulator noEnemy = simulatorWithoutEnemy(noEnemyLauma);
        List<ParticleRecord> noEnemyParticles =
                captureDendroParticles(noEnemy);
        noEnemyLauma.restoreCurrentEnergy(0.0);
        performSkill(noEnemy, SkillActionMode.PRESS);
        noEnemy.advanceTime(16.0);
        assertEquals(0, noEnemyParticles.size(),
                "no target suppresses Sanctuary particles");
        assertClose(0.0, noEnemyLauma.getTotalFlatEnergy(),
                "no target suppresses C4 Energy");
        assertClose(Double.NEGATIVE_INFINITY,
                noEnemyLauma.getSkillShredUntil(),
                "no target suppresses resistance shred");
    }

    private static void testBurstPaleHymnAndReactionIsolation() {
        Lauma lauma = new Lauma(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.HYDRO, false);
        CombatSimulator simulator = simulatorWith(lauma, ally);
        simulator.setMoonsign(CombatSimulator.Moonsign.NONE);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(18, lauma.getPaleHymnStackCount(
                simulator.getCurrentTime()),
                "Burst grants 18 Pale Hymn stacks at frame 96");
        StatsContainer supported = resolvedStats(simulator, ally);
        double em = supported.get(StatType.ELEMENTAL_MASTERY);
        double laumaEm = 315.2;
        double flatSupport = laumaEm * 4.72192;
        ReactionResult bloom = ReactionCalculator.calculate(
                Element.HYDRO,
                Element.DENDRO,
                em,
                90,
                supported.get(StatType.BLOOM_DMG_BONUS));
        ReactionResult baseBloom = ReactionCalculator.calculate(
                Element.HYDRO, Element.DENDRO, em, 90, 0.0);
        assertClose(flatSupport,
                bloom.getTransformDamage()
                        - baseBloom.getTransformDamage(),
                "Pale Hymn adds exact Lauma-EM Bloom flat damage");
        ReactionResult hyper = ReactionCalculator.calculateHyperbloom(
                em, 90,
                supported.get(StatType.HYPERBLOOM_DMG_BONUS));
        ReactionResult baseHyper = ReactionCalculator.calculateHyperbloom(
                em, 90, 0.0);
        assertClose(flatSupport,
                hyper.getTransformDamage()
                        - baseHyper.getTransformDamage(),
                "Pale Hymn adds exact Hyperbloom flat damage");
        ReactionResult burgeon = ReactionCalculator.calculateBurgeon(
                em, 90,
                supported.get(StatType.BURGEON_DMG_BONUS));
        ReactionResult baseBurgeon = ReactionCalculator.calculateBurgeon(
                em, 90, 0.0);
        assertClose(flatSupport,
                burgeon.getTransformDamage()
                        - baseBurgeon.getTransformDamage(),
                "Pale Hymn adds exact Burgeon flat damage");

        simulator.setMoonsign(CombatSimulator.Moonsign.NASCENT_GLEAM);
        StatsContainer lunarSupported = resolvedStats(simulator, ally);
        ReactionResult lunarBloom = ReactionCalculator.calculate(
                Element.HYDRO,
                Element.DENDRO,
                em,
                90,
                lunarSupported.get(StatType.LUNAR_BLOOM_DMG_BONUS));
        assertClose(laumaEm * 3.77808,
                lunarBloom.getTransformDamage()
                        - baseBloom.getTransformDamage(),
                "Pale Hymn adds exact Lunar-Bloom core flat damage");

        simulator.notifyReaction(ReactionResult.transform(
                100.0, "Overloaded", ReactionResult.Kind.OVERLOAD), ally);
        assertEquals(18, lauma.getPaleHymnStackCount(
                simulator.getCurrentTime()),
                "unrelated reactions do not consume Pale Hymn");
        for (ReactionResult.Kind kind : new ReactionResult.Kind[] {
                ReactionResult.Kind.BLOOM,
                ReactionResult.Kind.HYPERBLOOM,
                ReactionResult.Kind.BURGEON,
                ReactionResult.Kind.LUNAR_BLOOM
        }) {
            int before = lauma.getPaleHymnStackCount(
                    simulator.getCurrentTime());
            simulator.notifyReaction(ReactionResult.transform(
                    100.0, kind.name(), kind), ally);
            assertEquals(before - 1,
                    lauma.getPaleHymnStackCount(
                            simulator.getCurrentTime()),
                    kind + " consumes exactly one Pale Hymn stack");
        }

        AttackAction directLunar = lunarBloomAction(
                "External Lunar-Bloom", 2.0);
        lauma.prepareLunarBloomAction(
                ally, directLunar, simulator.getCurrentTime());
        double lunarBaseBonus = laumaEm * 0.000175;
        assertClose(lunarBaseBonus,
                directLunar.getStatSnapshot().get(
                        StatType.LUNAR_BASE_BONUS),
                "typed ingress applies Lauma's Lunar-Bloom base bonus");
        double directBase = 3.0 * em * 2.0
                * (1.0 + lunarBaseBonus);
        assertClose(laumaEm * 3.77808 / directBase,
                directLunar.getStatSnapshot().get(
                        StatType.LUNAR_BLOOM_DMG_BONUS),
                "typed ingress applies exact direct Lunar-Bloom support");
        int beforeDirect = lauma.getPaleHymnStackCount(
                simulator.getCurrentTime());
        simulator.performActionWithoutTimeAdvance(
                ally.getCharacterId(), directLunar);
        assertEquals(beforeDirect - 1,
                lauma.getPaleHymnStackCount(
                        simulator.getCurrentTime()),
                "resolved direct Lunar-Bloom consumes one stack");
    }

    private static void testPassivesAndConstellations() {
        Lauma nascent = new Lauma(null, null, 0);
        TestCharacter nascentAlly = new TestCharacter(
                CharacterId.BENNETT, Element.HYDRO, false);
        CombatSimulator nascentSim = simulatorWith(nascent, nascentAlly);
        nascentSim.setMoonsign(CombatSimulator.Moonsign.NASCENT_GLEAM);
        performSkill(nascentSim, SkillActionMode.PRESS);
        assertClose(0.15,
                resolvedStats(nascentSim, nascentAlly).get(
                        StatType.BLOOM_DMG_BONUS),
                "A1 Nascent uses expected 15-percent reaction crit value");
        advanceTo(nascentSim, 20.0);
        assertClose(0.0,
                resolvedStats(nascentSim, nascentAlly).get(
                        StatType.BLOOM_DMG_BONUS),
                "A1 closes at 20 seconds");

        Lauma c2 = new Lauma(null, null, 2);
        TestCharacter lunarAlly = new TestCharacter(
                CharacterId.BENNETT, Element.HYDRO, true);
        CombatSimulator c2Sim = simulatorWith(c2, lunarAlly);
        c2Sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        assertClose(0.40,
                resolvedStats(c2Sim, lunarAlly).get(
                        StatType.LUNAR_BLOOM_DMG_BONUS),
                "C2 grants typed Ascendant Lunar-Bloom bonus");
        AttackAction a1Lunar = lunarBloomAction(
                "A1 Ascendant Probe", 2.0);
        c2.prepareLunarBloomAction(lunarAlly, a1Lunar,
                c2Sim.getCurrentTime());
        assertClose(0.15,
                a1Lunar.getStatSnapshot().get(StatType.CRIT_RATE),
                "A1 Ascendant adds 10-percent Lunar-Bloom CR");
        assertClose(0.20,
                a1Lunar.getStatSnapshot().get(
                        StatType.LUNAR_REACTION_CRIT_DMG),
                "A1 Ascendant is permanent and adds 20-percent CD");

        Lauma c5 = new Lauma(null, null, 5);
        TestCharacter c5Ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO, false);
        CombatSimulator c5Sim = simulatorWith(c5, c5Ally);
        List<ActionRecord> c5Actions = captureLaumaActions(c5Sim);
        performSkill(c5Sim, SkillActionMode.PRESS);
        advanceTo(c5Sim, 62.0 * FRAME + EPSILON);
        assertClose(2.432,
                named(c5Actions, "Hymn of Hunting Press")
                        .get(0).action.getDamagePercent(),
                "C5 raises Press Skill to talent 12");
        assertClose(1.92,
                named(c5Actions, "Frostgrove Sanctuary")
                        .get(0).action.getDamagePercent(),
                "C5 raises Sanctuary ATK ratio to talent 12");
        assertClose(0.31,
                resolvedStats(c5Sim, c5Ally).get(
                        StatType.DENDRO_RES_SHRED),
                "C5 raises resistance shred to talent 12");

        Lauma c6 = new Lauma(null, null, 6);
        TestCharacter c6Ally = new TestCharacter(
                CharacterId.BENNETT, Element.HYDRO, true);
        CombatSimulator c6Sim = simulatorWith(c6, c6Ally);
        c6Sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
        List<ActionRecord> c6Actions = captureLaumaActions(c6Sim);
        performSkill(c6Sim, SkillActionMode.PRESS);
        advanceTo(c6Sim,
                (62.0 + 117.0 * 7.0 + 16.0) * FRAME + EPSILON);
        assertEquals(8, named(c6Actions,
                "Frostgrove Sanctuary C6").size(),
                "C6 emits eight delayed Lunar-Bloom packets");
        ActionRecord c6Sanctuary = named(
                c6Actions, "Frostgrove Sanctuary C6").get(0);
        assertClose(315.2 * 0.000175,
                c6Sanctuary.action.getStatSnapshot().get(
                        StatType.LUNAR_BASE_BONUS),
                "C6 Sanctuary uses Lauma's typed Lunar base bonus");
        assertClose(0.25,
                c6Sanctuary.action.getStatSnapshot().get(
                        StatType.LUNAR_MULTIPLIER),
                "C6 Sanctuary receives its non-Pale elevation support");
        assertEquals(16, c6.getPaleHymnStackCount(
                c6Sim.getCurrentTime()),
                "C6 packets grant two non-self-consuming stacks each");
        AttackAction c6Probe = lunarBloomAction(
                "C6 Elevation Probe", 2.0);
        c6.prepareLunarBloomAction(
                c6Ally, c6Probe, c6Sim.getCurrentTime());
        assertClose(0.25,
                c6Probe.getStatSnapshot().get(
                        StatType.LUNAR_MULTIPLIER),
                "C6 elevation uses explicit typed Lunar-Bloom ingress");
        perform(c6Sim, CharacterActionKey.NORMAL);
        ActionRecord converted = named(c6Actions,
                "Peregrination C6 Pale Hymn").get(0);
        assertClose(1.5, converted.action.getDamagePercent(),
                "C6 converts a Normal using one Pale Hymn stack");
        assertTrue(c6.getPaleHymnStackCount(
                c6Sim.getCurrentTime()) <= 14,
                "converted C6 Normal consumes conversion and support stacks");
        performSkill(c6Sim, SkillActionMode.PRESS);
        assertEquals(0, c6.getPaleHymnStackCount(
                c6Sim.getCurrentTime()),
                "new Skill clears the C6 Pale Hymn pool");
    }

    private static void testCooldownEnergyAndMoonsong() {
        Lauma lauma = new Lauma(null, null, 0);
        CombatSimulator simulator = simulatorWith(lauma);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, lauma.getCurrentEnergy(),
                "Burst spends 60 Energy at frame eight");
        assertClose(8.0 * FRAME,
                lauma.getBurstEnergyMarkers().get(0)[0],
                "Burst Energy marker uses frame eight");
        assertClose(15.0 - 109.0 * FRAME,
                lauma.getBurstCDRemaining(simulator.getCurrentTime()),
                "Burst cooldown starts at cast time");
        assertEquals(18, lauma.getPaleHymnStackCount(
                simulator.getCurrentTime()),
                "Pale Hymn is active after frame 96");
        double paleExpiry = 96.0 * FRAME + 15.0;
        advanceTo(simulator, paleExpiry);
        assertEquals(0, lauma.getPaleHymnStackCount(
                simulator.getCurrentTime()),
                "Burst Pale Hymn expires at 15 seconds");

        Lauma skillCdLauma = new Lauma(null, null, 0);
        CombatSimulator skillCd = simulatorWith(skillCdLauma);
        performSkill(skillCd, SkillActionMode.PRESS);
        assertClose(12.0 - (36.0 - 13.0) * FRAME,
                skillCdLauma.getSkillCDRemaining(
                        skillCd.getCurrentTime()),
                "Skill cooldown starts at frame 13");

        Lauma storedLauma = new Lauma(null, null, 0);
        CombatSimulator stored = simulatorWith(storedLauma);
        stored.incrementVerdantDewCount();
        stored.incrementVerdantDewCount();
        performSkill(stored, SkillActionMode.HOLD);
        assertEquals(2, storedLauma.getStoredMoonsong(
                stored.getCurrentTime()),
                "Hold stores two Moonsong Dew before Burst");
        perform(stored, CharacterActionKey.BURST);
        assertEquals(30, storedLauma.getPaleHymnStackCount(
                stored.getCurrentTime()),
                "Burst converts stored Moonsong into 12 stacks");
        assertEquals(0, storedLauma.getStoredMoonsong(
                stored.getCurrentTime()),
                "Burst clears converted Moonsong");

        Lauma activeLauma = new Lauma(null, null, 0);
        CombatSimulator active = simulatorWith(activeLauma);
        perform(active, CharacterActionKey.BURST);
        for (int index = 0; index < 3; index++) {
            active.incrementVerdantDewCount();
        }
        performSkill(active, SkillActionMode.HOLD);
        assertEquals(35, activeLauma.getPaleHymnStackCount(
                active.getCurrentTime()),
                "Hold converts three Dew into 18 stacks, then consumes one");
        assertEquals(0, activeLauma.getStoredMoonsong(
                active.getCurrentTime()),
                "active-Burst Moonsong is not left stored");
    }

    private static void testSnapshotRestoreAndGenerationIsolation() {
        Lauma lauma = new Lauma(null, null, 0);
        CombatSimulator simulator = simulatorWith(lauma);
        List<ActionRecord> actions = captureLaumaActions(simulator);
        simulator.incrementVerdantDewCount();
        SimulatorSnapshot[] captured = new SimulatorSnapshot[1];
        registerProbe(simulator, 28.0 * FRAME,
                () -> captured[0] = simulator.saveSnapshot());
        performSkill(simulator, SkillActionMode.HOLD);
        assertEquals(0, simulator.getVerdantDewCount(),
                "original branch consumes Dew");
        assertTrue(captured[0] != null,
                "frame-28 Hold snapshot captured");
        simulator.restoreSnapshot(captured[0]);
        simulator.restoreSnapshot(captured[0]);
        actions.clear();
        simulator.advanceTime(28.0 * FRAME + EPSILON);
        assertEquals(0, simulator.getVerdantDewCount(),
                "restored Hold consumes Dew exactly once");
        assertEquals(1, named(actions,
                "Hymn of Eternal Rest Lunar-Bloom").size(),
                "repeated rollback reconstructs one Hold Lunar packet");

        Lauma generationLauma = new Lauma(null, null, 0);
        CombatSimulator generation = simulatorWith(generationLauma);
        List<ActionRecord> generationActions =
                captureLaumaActions(generation);
        performSkill(generation, SkillActionMode.PRESS);
        advanceTo(generation, 12.0 + 13.0 * FRAME);
        performSkill(generation, SkillActionMode.PRESS);
        generation.advanceTime(16.0);
        assertEquals(14, named(generationActions,
                "Frostgrove Sanctuary").size(),
                "new Skill generation suppresses two old late ticks");

        Lauma foreign = new Lauma(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!lauma.acceptsCharacterState(foreignState),
                "Lauma rejects another instance's snapshot payload");
    }

    private static void testInvalidInputsAndExplicitExclusions() {
        Lauma lauma = new Lauma(null, null, 0);
        CombatSimulator simulator = simulatorWith(lauma);
        assertThrows(IllegalArgumentException.class,
                () -> lauma.onAction(null, simulator),
                "Lauma rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> lauma.onAction(
                        CharacterActionRequest.of(
                                CharacterActionKey.DASH),
                        simulator),
                "Lauma rejects unsupported Dash state");
        assertThrows(IllegalArgumentException.class,
                () -> new Lauma(null, null, -1),
                "Lauma rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Lauma(null, null, 7),
                "Lauma rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> lauma.prepareLunarBloomAction(
                        lauma,
                        new AttackAction(
                                "Invalid Ingress",
                                1.0,
                                Element.DENDRO,
                                StatType.BASE_ATK),
                        simulator.getCurrentTime()),
                "Lauma rejects non-Lunar ingress");
        assertTrue(!lauma.isHealingRepresented(),
                "C1 healing and player HP are explicitly excluded");
        assertTrue(!lauma.isDeerMovementRepresented(),
                "deer movement, stamina, and terrain are excluded");
        assertTrue(!lauma.isRandomMultiTargetRepresented(),
                "random and multi-target behavior are excluded");
        assertTrue(!lauma.isLowPlungeRepresented(),
                "Low Plunge is excluded");

        Lauma reused = new Lauma(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Lauma rejects cross-simulator reuse");
        Lauma detached = new Lauma(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> detached.initializeForSimulator(
                        new CombatSimulator()),
                "Lauma rejects a simulator that does not own her");
    }

    private static AttackAction lunarBloomAction(
            String name,
            double multiplier) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                Element.DENDRO,
                StatType.ELEMENTAL_MASTERY,
                null,
                0.0,
                ActionType.OTHER);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setLunarReactionType(
                AttackAction.LunarReactionType.BLOOM);
        return action;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
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
                CharacterId.LAUMA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.LAUMA,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureLaumaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.LAUMA) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> captureDendroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(new ParticleRecord(count, time));
            }
        });
        return records;
    }

    private static void registerProbe(
            CombatSimulator simulator,
            double time,
            Runnable probe) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                probe.run();
            }
        });
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

    private static List<ActionRecord> prefixed(
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

    private static StatsContainer resolvedStats(
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
            assertTrue(lines.get(index).startsWith("Lauma,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Lauma/Lauma_Status.csv",
                "config/characters/Lauma/Lauma_Multipliers.csv"
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
        throw new AssertionError("Lauma CSVs missing key " + key);
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

    private static void assertTrue(
            boolean condition,
            String message) {
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
        private final boolean lunar;

        private TestCharacter(
                CharacterId id,
                Element characterElement,
                boolean lunar) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            this.lunar = lunar;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
            baseStats.set(StatType.ELEMENTAL_MASTERY, 100.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public boolean isLunarCharacter() {
            return lunar;
        }
    }
}
