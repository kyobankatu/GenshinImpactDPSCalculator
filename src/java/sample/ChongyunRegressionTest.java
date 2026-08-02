package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import model.character.Chongyun;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.weapon.ApprenticesNotes;
import model.weapon.SilverSword;
import model.weapon.WasterGreatsword;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Chongyun's stationary offensive slice. */
public final class ChongyunRegressionTest {
    private static final double EPS = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private ChongyunRegressionTest() {
    }

    /** Runs data, timing, field, snapshot, and constellation checks. */
    public static void main(String[] args) throws IOException {
        testIdentityStatsAndCsvData();
        testNormalChargedPlungeAndC1();
        testSkillFieldInfusionParticlesAndC5();
        testTeamFieldC2AndSnapshotRestore();
        testA4SnapshotResistanceAndBurstConstellations();
        testRecastEarlyA4AndNullWeaponInfusion();
        testC4EnergyAndSimulatorGuard();
        System.out.println("ChongyunRegressionTest passed");
    }

    private static void testIdentityStatsAndCsvData() throws IOException {
        Chongyun chongyun = chongyunAtConstellation(6);
        assertEquals(CharacterId.CHONGYUN, chongyun.getCharacterId(),
                "Chongyun typed identity");
        assertEquals(CharacterId.CHONGYUN,
                CharacterId.fromName("Chongyun"),
                "Chongyun display-name identity");
        assertEquals(CharacterId.CHONGYUN,
                CharacterId.fromNumericId(27),
                "Chongyun numeric identity");
        assertEquals(Element.CRYO, chongyun.getElement(),
                "Chongyun element");
        assertClose(10984.0,
                chongyun.getBaseStats().get(StatType.BASE_HP), EPS,
                "Chongyun base HP");
        assertClose(223.0,
                chongyun.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Chongyun base ATK");
        assertClose(648.0,
                chongyun.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Chongyun base DEF");
        assertClose(0.24,
                chongyun.getBaseStats().get(StatType.ATK_PERCENT), EPS,
                "Chongyun ascension ATK");
        assertClose(40.0, chongyun.getEnergyCost(), EPS,
                "Chongyun Energy cost");
        assertClose(15.0, chongyun.getSkillCD(), EPS,
                "Chongyun Skill cooldown");
        assertClose(12.0, chongyun.getBurstCD(), EPS,
                "Chongyun Burst cooldown");

        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(
                    constellation,
                    chongyunAtConstellation(constellation)
                            .getConstellation(),
                    "Chongyun explicit constellation C"
                            + constellation);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> chongyunAtConstellation(-1),
                "Chongyun rejects negative constellation");
        assertThrows(
                IllegalArgumentException.class,
                () -> chongyunAtConstellation(7),
                "Chongyun rejects constellation above six");

        assertCsvShape(
                Paths.get("config/characters/Chongyun/"
                        + "Chongyun_Status.csv"),
                10);
        assertCsvShape(
                Paths.get("config/characters/Chongyun/"
                        + "Chongyun_Multipliers.csv"),
                21);
    }

    private static void testNormalChargedPlungeAndC1() {
        Chongyun c0 = chongyunAtConstellation(0);
        CombatSimulator sim = simulatorWith(c0);
        List<ActionRecord> normals = captureNamedActions(sim, "Demonbane N");
        double[] multipliers = { 1.2861, 1.1597, 1.4757, 1.8597 };
        int[] hitmarks = { 26, 24, 41, 53 };
        int[] durations = { 30, 36, 57, 101 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = sim.getCurrentTime();
            perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
            ActionRecord record = normals.get(step);
            assertClose(multipliers[step],
                    record.action.getDamagePercent(), EPS,
                    "Chongyun N" + (step + 1) + " multiplier");
            assertClose(castTime + hitmarks[step] * FRAME,
                    record.time, EPS,
                    "Chongyun N" + (step + 1) + " hitmark");
            assertClose(castTime + durations[step] * FRAME,
                    sim.getCurrentTime(), EPS,
                    "Chongyun N" + (step + 1) + " duration");
            assertAttack(
                    record.action,
                    Element.PHYSICAL,
                    ActionType.NORMAL,
                    ICDType.Standard,
                    ICDTag.NormalAttack,
                    0.0,
                    true,
                    "Chongyun Normal");
        }
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        assertTrue(normals.get(4).action.getName().endsWith("N1"),
                "Chongyun Normal chain wraps after N4");

        Chongyun charged = chongyunAtConstellation(0);
        CombatSimulator chargedSim = simulatorWith(charged);
        List<ActionRecord> chargedRecords = captureNamedActions(
                chargedSim, "Demonbane Charged");
        perform(
                chargedSim,
                CharacterId.CHONGYUN,
                CharacterActionKey.CHARGE);
        ActionRecord charge = chargedRecords.get(0);
        assertClose(1.0341, charge.action.getDamagePercent(), EPS,
                "Chongyun steady Charged multiplier");
        assertClose(23.0 * FRAME, chargedSim.getCurrentTime(), EPS,
                "Chongyun steady Charged duration");
        assertAttack(
                charge.action,
                Element.PHYSICAL,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                true,
                "Chongyun Charged");

        Chongyun plunging = chongyunAtConstellation(0);
        CombatSimulator plungeSim = simulatorWith(plunging);
        List<ActionRecord> plunges = captureNamedActions(
                plungeSim, "Demonbane High Plunge");
        perform(
                plungeSim,
                CharacterId.CHONGYUN,
                CharacterActionKey.PLUNGE);
        assertClose(47.0 * FRAME, plunges.get(0).time, EPS,
                "Chongyun high Plunge hitmark");
        assertClose(87.0 * FRAME, plungeSim.getCurrentTime(), EPS,
                "Chongyun high Plunge duration");
        assertClose(3.422517,
                plunges.get(0).action.getDamagePercent(), EPS,
                "Chongyun high Plunge multiplier");
        assertAttack(
                plunges.get(0).action,
                Element.PHYSICAL,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                true,
                "Chongyun Plunge");

        Chongyun c1 = chongyunAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        List<ActionRecord> c1Blades = captureNamedActions(
                c1Sim, "Ice Unleashed Blade");
        for (int step = 0; step < 4; step++) {
            perform(c1Sim, CharacterId.CHONGYUN,
                    CharacterActionKey.NORMAL);
        }
        assertEquals(3, c1Blades.size(),
                "Chongyun C1 emits three blades after N4");
        for (int blade = 0; blade < c1Blades.size(); blade++) {
            ActionRecord record = c1Blades.get(blade);
            assertClose(0.50, record.action.getDamagePercent(), EPS,
                    "Chongyun C1 blade multiplier");
            if (blade > 0) {
                assertClose(5.0 * FRAME,
                        record.time - c1Blades.get(blade - 1).time,
                        EPS,
                        "Chongyun C1 blade spacing");
            }
            assertAttack(
                    record.action,
                    Element.CRYO,
                    ActionType.OTHER,
                    ICDType.None,
                    ICDTag.None,
                    1.0,
                    false,
                    "Chongyun C1 blade");
        }
    }

    private static void testSkillFieldInfusionParticlesAndC5() {
        Chongyun c0 = chongyunAtConstellation(0);
        CombatSimulator sim = simulatorWith(c0);
        List<ActionRecord> skillRecords = captureNamedActions(
                sim, "Spirit Blade: Chonghua");
        List<ParticleRecord> particles = new ArrayList<>();
        sim.addParticleListener((element, count, time) ->
                particles.add(new ParticleRecord(element, count, time)));
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.SKILL);
        ActionRecord skill = skillRecords.get(0);
        assertClose(36.0 * FRAME, skill.time, EPS,
                "Layered Frost hitmark");
        assertClose(52.0 * FRAME, sim.getCurrentTime(), EPS,
                "Layered Frost duration");
        assertClose(2.92468, skill.action.getDamagePercent(), EPS,
                "Layered Frost talent-9 multiplier");
        assertFalse(skill.action.hasStatSnapshot(),
                "Layered Frost resolves dynamically at impact");
        assertAttack(
                skill.action,
                Element.CRYO,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                2.0,
                true,
                "Layered Frost");
        assertTrue(c0.isFormActive(sim.getCurrentTime()),
                "Spirit Blade field active after Skill animation");

        double normalCast = sim.getCurrentTime();
        List<ActionRecord> normals = captureNamedActions(sim, "Demonbane N");
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        assertClose(normalCast + 26.0 * FRAME / 1.08,
                normals.get(0).time, EPS,
                "A1 scales Chongyun N1 hitmark");
        assertClose(normalCast + 30.0 * FRAME / 1.08,
                sim.getCurrentTime(), EPS,
                "A1 scales Chongyun N1 duration");
        assertAttack(
                normals.get(0).action,
                Element.CRYO,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0,
                true,
                "Layered Frost infused Normal");

        advanceTo(sim, 136.0 * FRAME);
        assertEquals(1, particles.size(),
                "Layered Frost emits one particle packet");
        assertEquals(Element.CRYO, particles.get(0).element,
                "Layered Frost particle element");
        assertClose(4.0, particles.get(0).count, EPS,
                "Layered Frost deterministic particle count");
        assertClose(136.0 * FRAME, particles.get(0).time, EPS,
                "Layered Frost particle arrival");

        Chongyun c5 = chongyunAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Skills = captureNamedActions(
                c5Sim, "Spirit Blade: Chonghua");
        perform(c5Sim, CharacterId.CHONGYUN, CharacterActionKey.SKILL);
        assertClose(3.4408,
                c5Skills.get(0).action.getDamagePercent(), EPS,
                "Chongyun C5 Skill talent-12 multiplier");
        advanceTo(c5Sim, 10.60);
        assertTrue(c5.isFormActive(10.599999),
                "Spirit Blade field remains active before ten seconds");
        assertFalse(c5.isFormActive(10.60),
                "Spirit Blade field uses half-open expiry");
        assertClose(0.08,
                c5.getEffectiveStats(13.59)
                        .get(StatType.NORMAL_ATTACK_SPD),
                EPS,
                "C5 field final refresh lingers three seconds");
        assertClose(0.0,
                c5.getEffectiveStats(13.60)
                        .get(StatType.NORMAL_ATTACK_SPD),
                EPS,
                "C5 infusion and speed expire after three seconds");
    }

    private static void testTeamFieldC2AndSnapshotRestore() {
        Chongyun c2 = chongyunAtConstellation(2);
        TestCharacter sword = new TestCharacter(
                CharacterId.KAEYA, Element.CRYO, new SilverSword());
        TestCharacter catalyst = new TestCharacter(
                CharacterId.LISA, Element.ELECTRO,
                new ApprenticesNotes());
        CombatSimulator sim = simulatorWith(c2, sword, catalyst);
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.SKILL);

        sim.setActiveCharacter(CharacterId.KAEYA);
        advanceTo(sim, 1.60);
        StatsContainer swordStats = applicableStats(sim, sword);
        assertClose(0.08,
                swordStats.get(StatType.NORMAL_ATTACK_SPD), EPS,
                "A1 buffs eligible sword teammate");
        assertClose(0.15,
                swordStats.get(StatType.CD_REDUCTION), EPS,
                "C2 buffs sword teammate");
        sword.markSkillUsed(
                sim.getCurrentTime(), sim.getApplicableBuffs(sword));
        assertClose(8.50,
                sword.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "C2 snapshots 15 percent Skill cooldown reduction");

        sim.setActiveCharacter(CharacterId.LISA);
        advanceTo(sim, 2.60);
        StatsContainer catalystStats = applicableStats(sim, catalyst);
        assertClose(0.0,
                catalystStats.get(StatType.NORMAL_ATTACK_SPD), EPS,
                "A1 excludes catalyst teammate");
        assertClose(0.15,
                catalystStats.get(StatType.CD_REDUCTION), EPS,
                "C2 remains weapon-independent");
        catalyst.markBurstUsed(
                sim.getCurrentTime(), sim.getApplicableBuffs(catalyst));
        assertClose(17.0,
                catalyst.getBurstCDRemaining(sim.getCurrentTime()), EPS,
                "C2 snapshots 15 percent Burst cooldown reduction");

        SimulatorSnapshot fieldSnapshot = sim.saveSnapshot();
        advanceTo(sim, 10.70);
        assertFalse(c2.isFormActive(sim.getCurrentTime()),
                "field state marker expires after ten seconds");
        assertClose(0.15,
                applicableStats(sim, catalyst)
                        .get(StatType.CD_REDUCTION),
                EPS,
                "final field refresh remains active during linger");
        sim.restoreSnapshot(fieldSnapshot);
        assertTrue(c2.isFormActive(sim.getCurrentTime()),
                "snapshot restore recovers active field marker");
        assertClose(0.15,
                applicableStats(sim, catalyst)
                        .get(StatType.CD_REDUCTION),
                EPS,
                "snapshot restore recovers field recipient timing");
        sim.advanceTime(8.1);
        assertClose(0.0,
                applicableStats(sim, catalyst)
                        .get(StatType.CD_REDUCTION),
                EPS,
                "restored field recipient buff expires normally");
        assertFalse(c2.isFormActive(sim.getCurrentTime()),
                "restored field marker expires normally");
        sim.restoreSnapshot(fieldSnapshot);
        assertClose(0.15,
                applicableStats(sim, catalyst)
                        .get(StatType.CD_REDUCTION),
                EPS,
                "field marker snapshot supports repeated restore");
    }

    private static void testA4SnapshotResistanceAndBurstConstellations() {
        Chongyun c0 = chongyunAtConstellation(0);
        c0.addBuff(new SimpleBuff(
                "Short cast ATK",
                1.0,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator sim = simulatorWith(c0);
        List<ActionRecord> skill = captureNamedActions(
                sim, "Spirit Blade: Chonghua");
        List<ActionRecord> a4 = captureNamedActions(
                sim, "Rimechaser Blade");
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.SKILL);
        advanceTo(sim, 655.0 * FRAME);
        assertEquals(1, a4.size(), "A4 emits one Rimechaser Blade");
        assertClose(655.0 * FRAME, a4.get(0).time, EPS,
                "A4 Rimechaser hitmark");
        assertClose(2.92468,
                a4.get(0).action.getDamagePercent(), EPS,
                "A4 reuses Layered Frost multiplier");
        assertTrue(a4.get(0).action.hasStatSnapshot(),
                "A4 owns the Skill cast-time snapshot");
        assertClose(skill.get(0).damage, a4.get(0).damage, EPS,
                "A4 preserves expired cast-time ATK buff");
        assertAttack(
                a4.get(0).action,
                Element.CRYO,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                true,
                "A4 Rimechaser");
        assertClose(0.10,
                applicableStats(sim, c0)
                        .get(StatType.CRYO_RES_SHRED),
                EPS,
                "A4 applies ten percent Cryo RES shred after impact");
        sim.advanceTime(8.0);
        assertClose(0.0,
                applicableStats(sim, c0)
                        .get(StatType.CRYO_RES_SHRED),
                EPS,
                "A4 Cryo RES shred expires after eight seconds");

        assertBurst(0, 3, 2.4208);
        assertBurst(3, 3, 2.8480);
        assertBurst(6, 4, 2.8480);
    }

    private static void testRecastEarlyA4AndNullWeaponInfusion() {
        Chongyun recasting = chongyunAtConstellation(0);
        recasting.addBuff(new SimpleBuff(
                "First cast only ATK",
                0.70,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator recastSim = simulatorWith(recasting);
        List<ActionRecord> firstSkills = captureNamedActions(
                recastSim, "Spirit Blade: Chonghua");
        List<ActionRecord> a4 = captureNamedActions(
                recastSim, "Rimechaser Blade");
        perform(recastSim, CharacterId.CHONGYUN,
                CharacterActionKey.SKILL);
        double recastTime = recastSim.getCurrentTime();
        recasting.resetSkillCooldown(recastTime);
        perform(recastSim, CharacterId.CHONGYUN,
                CharacterActionKey.SKILL);
        advanceTo(recastSim, recastTime + 81.0 * FRAME);
        assertEquals(1, a4.size(),
                "Skill recast emits old field A4 early");
        assertClose(recastTime + 81.0 * FRAME, a4.get(0).time, EPS,
                "recast A4 resolves 81 frames after cast");
        assertClose(firstSkills.get(0).damage, a4.get(0).damage, EPS,
                "recast A4 retains the old field snapshot");
        advanceTo(recastSim, 655.0 * FRAME);
        assertEquals(1, a4.size(),
                "recast invalidates old regular-expiry A4");
        advanceTo(recastSim, recastTime + 655.0 * FRAME);
        assertEquals(2, a4.size(),
                "new field retains its own regular A4");

        TalentDataSource data = (character, key, defaultValue) ->
                defaultValue;
        Chongyun weaponless = new Chongyun(null, null, data, 0);
        CombatSimulator nullWeaponSim = simulatorWith(weaponless);
        perform(nullWeaponSim, CharacterId.CHONGYUN,
                CharacterActionKey.SKILL);
        List<ActionRecord> normals = captureNamedActions(
                nullWeaponSim, "Demonbane N");
        double castTime = nullWeaponSim.getCurrentTime();
        perform(nullWeaponSim, CharacterId.CHONGYUN,
                CharacterActionKey.NORMAL);
        assertEquals(Element.CRYO, normals.get(0).action.getElement(),
                "weaponless Chongyun retains intrinsic claymore infusion");
        assertClose(castTime + 30.0 * FRAME / 1.08,
                nullWeaponSim.getCurrentTime(), EPS,
                "weaponless Chongyun remains eligible for A1 speed");
    }

    private static void assertBurst(
            int constellation,
            int bladeCount,
            double multiplier) {
        Chongyun chongyun = chongyunAtConstellation(constellation);
        CombatSimulator sim = simulatorWith(chongyun);
        List<ActionRecord> blades = captureNamedActions(
                sim, "Cloud-Parting Star Blade");
        double castTime = sim.getCurrentTime();
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.BURST);
        int[] expectedHitmarks = bladeCount == 4
                ? new int[] { 50, 59, 67, 77 }
                : new int[] { 50, 59, 67 };
        assertEquals(bladeCount, blades.size(),
                "Cloud-Parting Star blade count C" + constellation);
        for (int blade = 0; blade < blades.size(); blade++) {
            ActionRecord record = blades.get(blade);
            assertClose(castTime + expectedHitmarks[blade] * FRAME,
                    record.time, EPS,
                    "Cloud-Parting Star blade hitmark");
            assertClose(multiplier,
                    record.action.getDamagePercent(), EPS,
                    "Cloud-Parting Star multiplier C"
                            + constellation);
            assertFalse(record.action.hasStatSnapshot(),
                    "Cloud-Parting Star remains dynamic");
            assertAttack(
                    record.action,
                    Element.CRYO,
                    ActionType.BURST,
                    ICDType.None,
                    ICDTag.ElementalBurst,
                    1.0,
                    true,
                    "Cloud-Parting Star");
        }
        assertClose(castTime + 79.0 * FRAME,
                sim.getCurrentTime(), EPS,
                "Cloud-Parting Star animation duration");
        assertClose(12.0 - 73.0 * FRAME,
                chongyun.getBurstCDRemaining(sim.getCurrentTime()), EPS,
                "Cloud-Parting Star cooldown starts at frame six");
    }

    private static void testC4EnergyAndSimulatorGuard() {
        Chongyun c4 = chongyunAtConstellation(4);
        CombatSimulator sim = simulatorWith(c4);
        sim.getEnemy().setAura(Element.CRYO, 2.0, sim.getCurrentTime());
        double before = c4.getTotalFlatEnergy();
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        double first = c4.getTotalFlatEnergy() - before;
        SimulatorSnapshot cooldownSnapshot = sim.saveSnapshot();
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        double second = c4.getTotalFlatEnergy() - before - first;
        assertClose(2.0, first - second, EPS,
                "C4 grants two Energy on first eligible hit");
        sim.advanceTime(2.0);
        double priorThird = c4.getTotalFlatEnergy();
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        double third = c4.getTotalFlatEnergy() - priorThird;
        assertClose(first, third, EPS,
                "C4 refreshes after its two-second cooldown");
        sim.restoreSnapshot(cooldownSnapshot);
        double beforeRestoredHit = c4.getTotalFlatEnergy();
        perform(sim, CharacterId.CHONGYUN, CharacterActionKey.NORMAL);
        assertClose(1.0 / 4.66,
                c4.getTotalFlatEnergy() - beforeRestoredHit,
                EPS,
                "snapshot restore recovers C4 internal cooldown marker");

        CombatSimulator other = new CombatSimulator();
        other.setLoggingEnabled(false);
        other.setEnemy(new Enemy(90));
        assertThrows(
                IllegalStateException.class,
                () -> other.addCharacter(c4),
                "Chongyun rejects cross-simulator reuse");
    }

    private static Chongyun chongyunAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new Chongyun(
                new WasterGreatsword(),
                null,
                data,
                constellation);
    }

    private static CombatSimulator simulatorWith(
            Chongyun chongyun,
            Character... teammates) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(chongyun);
        for (Character teammate : teammates) {
            sim.addCharacter(teammate);
        }
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterId characterId,
            CharacterActionKey key) {
        sim.performAction(
                characterId,
                CharacterActionRequest.of(key));
    }

    private static void advanceTo(
            CombatSimulator sim,
            double targetTime) {
        double delta = targetTime - sim.getCurrentTime();
        if (delta > 0.0) {
            sim.advanceTime(delta);
        }
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String prefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CHONGYUN
                    && action.getName().startsWith(prefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static StatsContainer applicableStats(
            CombatSimulator sim,
            Character character) {
        StatsContainer stats = character.getEffectiveStats(
                sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
    }

    private static void assertAttack(
            AttackAction action,
            Element element,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean shatter,
            String label) {
        assertEquals(element, action.getElement(), label + " element");
        assertEquals(actionType, action.getActionType(),
                label + " category");
        assertEquals(icdType, action.getICDType(), label + " ICD type");
        assertEquals(icdTag, action.getICDTag(), label + " ICD tag");
        assertClose(gauge, action.getGaugeUnits(), EPS,
                label + " gauge");
        assertEquals(shatter, action.isShatterTrigger(),
                label + " Shatter flag");
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(),
                path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            String[] columns = lines.get(index).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (index + 1));
            assertEquals("Chongyun", columns[0],
                    path + " character at line " + (index + 1));
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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
            throw new AssertionError(
                    message + ": unexpected exception " + thrown,
                    thrown);
        }
        throw new AssertionError(message + ": no exception thrown");
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
        private final Element element;
        private final double count;
        private final double time;

        private ParticleRecord(
                Element element,
                double count,
                double time) {
            this.element = element;
            this.count = count;
            this.time = time;
        }
    }

    /** Minimal teammate used to validate field targeting and cooldowns. */
    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element characterElement,
                Weapon equippedWeapon) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = equippedWeapon;
            artifacts = new ArtifactSet[0];
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 200.0);
            baseStats.set(StatType.BASE_DEF, 600.0);
            setSkillCD(10.0);
            setBurstCD(20.0);
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
            // Test teammate has no passive.
        }
    }
}
