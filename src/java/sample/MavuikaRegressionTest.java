package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.formula.DamageCalculator;
import model.character.Mavuika;
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

/** Focused regression checks for Mavuika's fixed-target Fighting Spirit slice. */
public final class MavuikaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private MavuikaRegressionTest() {
    }

    /** Runs data, action, resource, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testClaymoreBasicsAndHighPlunge();
        testSkillModesDrainParticlesAndRecast();
        testFightingSpiritBoundaryAndBurstScaling();
        testPassivesAndConstellations();
        testCooldownEnergyAndC6Offense();
        testSnapshotRestoreAndGenerationIsolation();
        testInvalidInputsAndExplicitExclusions();
        System.out.println("MavuikaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Mavuika mavuika = new Mavuika(null, null, 6);
        assertEquals(CharacterId.MAVUIKA, mavuika.getCharacterId(),
                "Mavuika typed identity");
        assertEquals(CharacterId.MAVUIKA,
                CharacterId.fromName("Mavuika"),
                "Mavuika name lookup");
        assertEquals(CharacterId.MAVUIKA,
                CharacterId.fromNumericId(104),
                "Mavuika numeric lookup");
        assertEquals(CharacterRegion.NATLAN,
                CharacterId.MAVUIKA.getRegion(),
                "Mavuika region");
        assertEquals(Element.PYRO, mavuika.getElement(),
                "Mavuika element");
        assertClose(12552.0,
                mavuika.getBaseStats().get(StatType.BASE_HP),
                "Mavuika base HP");
        assertClose(359.0,
                mavuika.getBaseStats().get(StatType.BASE_ATK),
                "Mavuika base ATK");
        assertClose(792.0,
                mavuika.getBaseStats().get(StatType.BASE_DEF),
                "Mavuika base DEF");
        assertClose(0.884,
                mavuika.getBaseStats().get(StatType.CRIT_DMG),
                "Mavuika total base CRIT DMG");
        assertClose(0.0, mavuika.getEnergyCost(),
                "Mavuika Burst Energy cost");
        assertClose(15.0, mavuika.getSkillCD(),
                "Mavuika Skill cooldown");
        assertClose(18.0, mavuika.getBurstCD(),
                "Mavuika Burst cooldown");
        assertClose(200.0, mavuika.getFightingSpirit(),
                "Mavuika starts with maximum Fighting Spirit");
        assertClose(100.0,
                mavuika.getBurstFightingSpiritThreshold(),
                "Mavuika Burst Fighting Spirit threshold");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.MAVUIKA,
                    new Mavuika(null, null, constellation)
                            .getCharacterId(),
                    "Mavuika explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Mavuika/Mavuika_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Mavuika/Mavuika_Multipliers.csv"), 74);
        assertCsvValue("Sunfell Slice C3", 8.896);
        assertCsvValue("The Named Moment C5", 1.488);
        assertCsvValue("C6 Flamestrider Ring", 4.0);
    }

    private static void testClaymoreBasicsAndHighPlunge() {
        Mavuika mavuika = new Mavuika(null, null, 0);
        CombatSimulator simulator = simulatorWith(mavuika);
        List<ActionRecord> records = captureMavuikaActions(simulator);
        double[] expectedMultipliers = {
            1.470411,
            0.670212, 0.670212,
            0.610380, 0.610380, 0.610380,
            2.134706
        };
        double[] expectedFrames = {
            21.0,
            45.0 + 9.0, 57.0 + 9.0,
            101.0 + 23.0, 106.0 + 23.0, 112.0 + 23.0,
            149.0 + 23.0
        };
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(expectedMultipliers.length, records.size(),
                "Mavuika claymore string hit count");
        for (int index = 0; index < records.size(); index++) {
            ActionRecord record = records.get(index);
            assertClose(expectedMultipliers[index],
                    record.action.getDamagePercent(),
                    "Mavuika claymore multiplier " + index);
            assertClose(expectedFrames[index] * FRAME,
                    record.time,
                    "Mavuika claymore impact frame " + index);
            assertEquals(Element.PHYSICAL,
                    record.action.getElement(),
                    "Mavuika claymore element " + index);
            assertEquals(ICDType.Standard,
                    record.action.getICDType(),
                    "Mavuika claymore standard ICD " + index);
        }

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.CHARGE);
        ActionRecord charged = named(
                records, "Flames Weave Life Charged").get(0);
        assertClose(chargedCast + 40.0 * FRAME,
                charged.time,
                "Mavuika claymore Charged hit frame");
        assertClose(3.56132,
                charged.action.getDamagePercent(),
                "Mavuika claymore Charged multiplier");

        double plungeCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = named(
                records, "Flames Weave Life High Plunge").get(0);
        assertClose(plungeCast + 41.0 * FRAME,
                plunge.time,
                "Mavuika fixed high Plunge frame");
        assertClose(3.422517,
                plunge.action.getDamagePercent(),
                "Mavuika fixed high Plunge multiplier");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Mavuika fixed high Plunge has no ICD");
    }

    private static void testSkillModesDrainParticlesAndRecast() {
        Mavuika ring = new Mavuika(null, null, 0);
        CombatSimulator ringSimulator = simulatorWith(ring);
        List<ActionRecord> ringRecords =
                captureMavuikaActions(ringSimulator);
        List<ParticleRecord> particles =
                capturePyroParticles(ringSimulator);
        performSkill(ringSimulator, SkillActionMode.PRESS);
        assertEquals("RING", ring.getArmamentMode(),
                "Press Skill enters Ring mode");
        assertTrue(ring.isNightsoulBlessingActive(),
                "Press Skill enters local Nightsoul");
        assertClose(1.2648,
                named(ringRecords, "The Named Moment").get(0)
                        .action.getDamagePercent(),
                "Press Skill initial multiplier");
        assertClose(15.3, ring.getSkillCooldownEndTime(),
                "Skill cooldown starts at frame 18");
        assertTrue(ring.getNightsoulPoints() < 80.0,
                "Ring Nightsoul drains every 0.1 seconds");
        advanceTo(ringSimulator, 116.0 * FRAME + EPSILON);
        assertEquals(1, particles.size(),
                "Skill emits one particle packet");
        assertClose(5.0, particles.get(0).count,
                "Skill emits five Pyro particles");
        advanceTo(ringSimulator, 2.0 + EPSILON);
        ActionRecord ringHit = named(
                ringRecords, "Rings of Searing Radiance").get(0);
        assertClose(2.0, ringHit.time,
                "Ring first attacks after two seconds");
        assertClose(2.176, ringHit.action.getDamagePercent(),
                "Ring Talent-9 multiplier");
        double pointsAfterRing = ring.getNightsoulPoints();
        performSkill(ringSimulator, SkillActionMode.PRESS);
        assertEquals("FLAMESTRIDER", ring.getArmamentMode(),
                "Press recast toggles Ring to Flamestrider");
        advanceTo(ringSimulator, 4.1);
        assertEquals(1, named(
                ringRecords, "Rings of Searing Radiance").size(),
                "Recast generation suppresses stale Ring ticks");
        assertTrue(ring.getNightsoulPoints() < pointsAfterRing,
                "Flamestrider uses the larger local drain");

        Mavuika bike = new Mavuika(null, null, 0);
        CombatSimulator bikeSimulator = simulatorWith(bike);
        List<ActionRecord> bikeRecords =
                captureMavuikaActions(bikeSimulator);
        performSkill(bikeSimulator, SkillActionMode.HOLD);
        assertEquals("FLAMESTRIDER", bike.getArmamentMode(),
                "Hold Skill enters Flamestrider mode");
        assertClose(41.0 * FRAME,
                bike.getSkillCDRemaining(bikeSimulator.getCurrentTime()),
                "Hold Skill retains source recast lock remainder");
        double normalCast = bikeSimulator.getCurrentTime();
        perform(bikeSimulator, CharacterActionKey.NORMAL);
        ActionRecord bikeNormal = named(
                bikeRecords, "Flamestrider Normal").get(0);
        assertClose(normalCast + 19.0 * FRAME,
                bikeNormal.time,
                "Flamestrider N1 hit frame");
        assertClose(1.052075,
                bikeNormal.action.getDamagePercent(),
                "Flamestrider N1 Talent-9 multiplier");
        assertEquals(ICDTag.Mavuika_Flamestrider,
                bikeNormal.action.getICDTag(),
                "Flamestrider uses its private ICD tag");
        double chargeCast = bikeSimulator.getCurrentTime();
        perform(bikeSimulator, CharacterActionKey.CHARGE);
        List<ActionRecord> charged = named(
                bikeRecords, "Flamestrider Charged Attack");
        assertEquals(2, charged.size(),
                "Fixed-target Flamestrider Charge has cyclic and final hits");
        assertClose(chargeCast + 35.0 * FRAME,
                charged.get(0).time,
                "Flamestrider cyclic fixed-target frame");
        assertClose(chargeCast + 95.0 * FRAME,
                charged.get(1).time,
                "Flamestrider earliest final frame");

        bike.resetSkillCooldown(bikeSimulator.getCurrentTime());
        double refreshCast = bikeSimulator.getCurrentTime();
        performSkill(bikeSimulator, SkillActionMode.PRESS);
        assertClose(refreshCast + 27.0 * FRAME,
                bikeSimulator.getCurrentTime(),
                "Available Flamestrider Skill refresh uses 27 frames");
        assertEquals("FLAMESTRIDER", bike.getArmamentMode(),
                "Available Skill refresh preserves Flamestrider mode");
    }

    private static void testFightingSpiritBoundaryAndBurstScaling() {
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        Mavuika mavuika = new Mavuika(null, null, 0);
        CombatSimulator simulator = simulatorWith(mavuika, ally);
        List<ActionRecord> records = captureMavuikaActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        ActionRecord sunfell = named(records, "Sunfell Slice").get(0);
        assertClose(7.5616, sunfell.action.getDamagePercent(),
                "C0 Sunfell Slice Talent-9 multiplier");
        assertClose(200.0, mavuika.getBurstFightingSpirit(),
                "Burst snapshots all consumed Fighting Spirit");
        assertClose(200.0 * 0.0272 * 359.0,
                bonus(sunfell.action, StatType.FLAT_DMG_BONUS),
                "Sunfell local Fighting Spirit flat damage");
        assertTrue(mavuika.isCrucibleActive(
                simulator.getCurrentTime()),
                "Crucible is active by the Burst cancel frame");
        double cruciblePoints = mavuika.getNightsoulPoints();
        simulator.advanceTime(1.0);
        assertClose(cruciblePoints, mavuika.getNightsoulPoints(),
                "Crucible pauses local Nightsoul consumption");
        assertClose(0.38,
                mavuika.getA4DamageBonus(simulator.getCurrentTime()),
                "C0 A4 decays one twentieth after one second");

        assertTrue(mavuika.notifyExternallyConfirmedAllyNormalHit(
                CharacterId.BENNETT, simulator),
                "First externally confirmed ally Normal passes");
        assertTrue(!mavuika.notifyExternallyConfirmedAllyNormalHit(
                CharacterId.BENNETT, simulator),
                "Ally Normal global 0.1-second gate rejects duplicate");
        simulator.advanceTime(0.1);
        assertTrue(mavuika.notifyExternallyConfirmedAllyNormalHit(
                CharacterId.BENNETT, simulator),
                "Ally Normal exact 0.1-second boundary passes");
        assertTrue(mavuika.notifyExternallyConfirmedNightsoulConsumption(
                CharacterId.BENNETT, 100.0, simulator),
                "Typed ally Nightsoul consumption enters Fighting Spirit");
        advanceTo(simulator, 18.1);
        assertTrue(mavuika.canBurst(simulator.getCurrentTime()),
                "Burst becomes ready at cooldown with at least 100 Spirit");

        Mavuika threshold = new Mavuika(null, null, 0);
        CombatSimulator thresholdSimulator = simulatorWith(threshold);
        perform(thresholdSimulator, CharacterActionKey.BURST);
        advanceTo(thresholdSimulator, 18.1);
        assertTrue(!threshold.canBurst(
                thresholdSimulator.getCurrentTime()),
                "Burst fails closed below 100 Fighting Spirit");
        assertThrows(IllegalStateException.class,
                () -> perform(thresholdSimulator,
                        CharacterActionKey.BURST),
                "Burst input below threshold is rejected");
    }

    private static void testPassivesAndConstellations() {
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        Mavuika a1 = new Mavuika(null, null, 0);
        CombatSimulator a1Simulator = simulatorWith(a1, ally);
        assertTrue(a1.notifyExternallyConfirmedNightsoulBurst(
                CharacterId.BENNETT, a1Simulator),
                "Externally confirmed Nightsoul Burst activates A1");
        assertClose(0.30,
                a1.getEffectiveStats(a1Simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "A1 grants 30 percent ATK");
        a1Simulator.advanceTime(10.0);
        assertClose(0.0,
                a1.getEffectiveStats(a1Simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "A1 uses a half-open ten-second window");

        Mavuika c1 = new Mavuika(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1, ally);
        perform(c1Simulator, CharacterActionKey.BURST);
        double beforeIngress = c1.getFightingSpirit();
        c1.notifyExternallyConfirmedNightsoulConsumption(
                CharacterId.BENNETT, 8.0, c1Simulator);
        assertClose(beforeIngress + 10.0, c1.getFightingSpirit(),
                "C1 multiplies Fighting Spirit generation by 1.25");
        assertClose(0.40,
                c1.getEffectiveStats(c1Simulator.getCurrentTime())
                        .get(StatType.ATK_PERCENT),
                "C1 ingress grants 40 percent ATK");
        Mavuika c1Points = new Mavuika(null, null, 1);
        CombatSimulator c1PointSimulator = simulatorWith(c1Points);
        performSkill(c1PointSimulator, SkillActionMode.PRESS);
        assertTrue(c1Points.getNightsoulPoints() > 110.0,
                "C1 raises maximum Nightsoul points to 120");

        Mavuika c2 = new Mavuika(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2, ally);
        performSkill(c2Simulator, SkillActionMode.PRESS);
        StatsContainer c2Stats = targetStats(
                c2Simulator,
                ally,
                probe("C2 Ring probe", ActionType.NORMAL));
        assertClose(0.20,
                c2Stats.get(StatType.ENEMY_DEF_REDUCTION),
                "C2 Ring reduces fixed-target DEF by 20 percent");
        assertClose(559.0,
                c2.getEffectiveStats(c2Simulator.getCurrentTime())
                        .get(StatType.BASE_ATK),
                "C2 adds 200 Base ATK during Nightsoul");

        Mavuika c3 = new Mavuika(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records =
                captureMavuikaActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        ActionRecord c3Sunfell = named(
                c3Records, "Sunfell Slice").get(0);
        assertClose(8.896,
                c3Sunfell.action.getDamagePercent(),
                "C3 raises Sunfell Slice to level 12");
        assertTrue(bonus(c3Sunfell.action, StatType.FLAT_DMG_BONUS)
                        > bonusValueAtC0(),
                "C2 and C3 increase Sunfell flat damage");

        Mavuika c4 = new Mavuika(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        perform(c4Simulator, CharacterActionKey.BURST);
        assertClose(0.50,
                c4.getA4DamageBonus(c4Simulator.getCurrentTime()),
                "C4 adds ten percent to full A4");
        c4Simulator.advanceTime(5.0);
        assertClose(0.50,
                c4.getA4DamageBonus(c4Simulator.getCurrentTime()),
                "C4 prevents A4 decay");

        Mavuika c5 = new Mavuika(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records =
                captureMavuikaActions(c5Simulator);
        performSkill(c5Simulator, SkillActionMode.HOLD);
        assertClose(1.488,
                named(c5Records, "The Named Moment").get(0)
                        .action.getDamagePercent(),
                "C5 raises initial Skill to level 12");
        perform(c5Simulator, CharacterActionKey.NORMAL);
        assertClose(1.291788,
                named(c5Records, "Flamestrider Normal").get(0)
                        .action.getDamagePercent(),
                "C5 raises Flamestrider N1 to level 12");
    }

    private static void testCooldownEnergyAndC6Offense() {
        Mavuika cooldown = new Mavuika(null, null, 0);
        CombatSimulator cooldownSimulator = simulatorWith(cooldown);
        performSkill(cooldownSimulator, SkillActionMode.PRESS);
        assertClose(15.3, cooldown.getSkillCooldownEndTime(),
                "Skill cooldown endpoint includes frame-18 delay");
        assertClose(0.0, cooldown.getCurrentEnergy(),
                "Mavuika has no Energy bar cost");

        Mavuika burstCooldown = new Mavuika(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burstCooldown);
        perform(burstSimulator, CharacterActionKey.BURST);
        assertClose(18.0,
                burstCooldown.getBurstCooldownEndTime(),
                "Burst cooldown starts at cast");

        Mavuika c6Ring = new Mavuika(null, null, 6);
        CombatSimulator c6RingSimulator = simulatorWith(c6Ring);
        List<ActionRecord> ringRecords =
                captureMavuikaActions(c6RingSimulator);
        performSkill(c6RingSimulator, SkillActionMode.PRESS);
        advanceTo(c6RingSimulator, 2.1);
        assertEquals(1, named(
                ringRecords, "Flamestrider (C6)").size(),
                "C6 Ring hit triggers one 200 percent follow-up");
        assertClose(2.0,
                named(ringRecords, "Flamestrider (C6)").get(0)
                        .action.getDamagePercent(),
                "C6 Ring follow-up multiplier");

        Mavuika c6Bike = new Mavuika(null, null, 6);
        CombatSimulator c6BikeSimulator = simulatorWith(c6Bike);
        List<ActionRecord> bikeRecords =
                captureMavuikaActions(c6BikeSimulator);
        performSkill(c6BikeSimulator, SkillActionMode.HOLD);
        advanceTo(c6BikeSimulator, 3.1);
        ActionRecord c6BikeRing = named(
                bikeRecords, "Rings of Searing Radiance (C6)").get(0);
        assertClose(3.0, c6BikeRing.time,
                "C6 Flamestrider Ring first tick timing");
        assertClose(4.0,
                c6BikeRing.action.getDamagePercent(),
                "C6 Flamestrider Ring multiplier");
    }

    private static void testSnapshotRestoreAndGenerationIsolation() {
        Mavuika restored = new Mavuika(null, null, 0);
        CombatSimulator simulator = simulatorWith(restored);
        List<ActionRecord> records = captureMavuikaActions(simulator);
        performSkill(simulator, SkillActionMode.PRESS);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 2.1);
        assertEquals(1, named(
                records, "Rings of Searing Radiance").size(),
                "Ring delayed hit resolves before restore");
        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 2.1);
        assertEquals(1, named(
                records, "Rings of Searing Radiance").size(),
                "Repeated restore reconstructs Ring hit once");

        Mavuika isolated = new Mavuika(null, null, 0);
        CombatSimulator isolatedSimulator = simulatorWith(isolated);
        List<ActionRecord> isolatedRecords =
                captureMavuikaActions(isolatedSimulator);
        performSkill(isolatedSimulator, SkillActionMode.PRESS);
        performSkill(isolatedSimulator, SkillActionMode.PRESS);
        advanceTo(isolatedSimulator, 2.2);
        assertEquals(0, named(
                isolatedRecords, "Rings of Searing Radiance").size(),
                "Armament generation rejects stale Ring event");

        Mavuika owner = new Mavuika(null, null, 0);
        Mavuika foreign = new Mavuika(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!owner.acceptsCharacterState(foreignState),
                "Mavuika rejects another instance state");
    }

    private static void testInvalidInputsAndExplicitExclusions() {
        Mavuika mavuika = new Mavuika(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.BENNETT, Element.PYRO);
        CombatSimulator simulator = simulatorWith(mavuika, ally);
        assertThrows(IllegalArgumentException.class,
                () -> new Mavuika(null, null, -1),
                "Negative constellation is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new Mavuika(null, null, 7),
                "Constellation above C6 is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> mavuika.onAction(null, simulator),
                "Null action is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Movement action is rejected");
        performSkill(simulator, SkillActionMode.PRESS);
        assertThrows(IllegalArgumentException.class,
                () -> performSkill(simulator, SkillActionMode.HOLD),
                "Hold Skill is rejected for recast");
        assertThrows(IllegalArgumentException.class,
                () -> mavuika.notifyExternallyConfirmedNightsoulConsumption(
                        CharacterId.BENNETT, Double.NaN, simulator),
                "Non-finite Nightsoul ingress is rejected");
        assertTrue(!mavuika.notifyExternallyConfirmedAllyNormalHit(
                CharacterId.UNKNOWN, simulator),
                "Unknown ally Normal source fails closed");
        assertTrue(!mavuika.notifyExternallyConfirmedAllyNormalHit(
                CharacterId.MAVUIKA, simulator),
                "External local Normal source fails closed");
        assertTrue(!mavuika.notifyExternallyConfirmedNightsoulBurst(
                CharacterId.XINGQIU, simulator),
                "Non-party Nightsoul Burst source fails closed");
        assertTrue(!mavuika.isAutomaticTeamNightsoulPlumbingRepresented(),
                "Automatic team Nightsoul plumbing fails closed");
        assertTrue(!mavuika.isPlayerHpHealingDamageIntakeRepresented(),
                "Player HP, healing, and damage intake fail closed");
        assertTrue(!mavuika.isShieldDefenseRepresented(),
                "Shield and defense state fail closed");
        assertTrue(!mavuika.isMovementTerrainGeometryRepresented(),
                "Movement, terrain, and geometry fail closed");
        assertTrue(!mavuika.isRandomMultiTargetRepresented(),
                "Random and multi-target behavior fail closed");
        assertTrue(!mavuika.isHitlagStaminaRepresented(),
                "Hitlag and stamina fail closed");
        assertTrue(!mavuika.isLowPlungeExplorationRepresented(),
                "Low Plunge and exploration fail closed");

        Mavuika reused = new Mavuika(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Mavuika rejects cross-simulator reuse");
    }

    private static double bonusValueAtC0() {
        return 200.0 * 0.0272 * 359.0;
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.MAVUIKA,
                CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.MAVUIKA,
                CharacterActionRequest.skill(mode));
    }

    private static AttackAction probe(
            String name,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PYRO,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static StatsContainer targetStats(
            CombatSimulator simulator,
            Character attacker,
            AttackAction action) {
        return DamageCalculator.resolveTargetStats(
                attacker,
                simulator.getEnemy(),
                action,
                simulator.getApplicableBuffs(attacker),
                simulator.getCurrentTime(),
                simulator);
    }

    private static List<ActionRecord> captureMavuikaActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.MAVUIKA) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ParticleRecord> capturePyroParticles(
            CombatSimulator simulator) {
        List<ParticleRecord> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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
            assertTrue(lines.get(index).startsWith("Mavuika,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) throws IOException {
        for (String path : new String[] {
                "config/characters/Mavuika/Mavuika_Status.csv",
                "config/characters/Mavuika/Mavuika_Multipliers.csv"
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
        throw new AssertionError("Mavuika CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but was " + actual);
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
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but caught "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
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
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
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
