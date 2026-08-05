package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.character.KujouSara;
import model.character.Noelle;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
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

/** Regression checks for Kujou Sara's bounded Crowfeather support slice. */
public final class KujouSaraRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;

    private KujouSaraRegressionTest() {
    }

    /** Runs data, action, buff, constellation, and restore checks. */
    public static void main(String[] args) throws Exception {
        testDataAndNormalString();
        testChargedCoverAmbushAndEnergy();
        testC2C5AndCooldownGates();
        testBurstTimingSnapshotAndGeometryPolicy();
        testC6LiveRecipientContract();
        testSnapshotRestoreAndStaleEvents();
        testInvalidInputsAndBindingGuards();
        System.out.println("KujouSaraRegressionTest passed");
    }

    private static void testDataAndNormalString() throws IOException {
        assertCsvShape(
                Path.of("config/characters/KujouSara/KujouSara_Status.csv"),
                10);
        assertCsvShape(
                Path.of("config/characters/KujouSara/"
                        + "KujouSara_Multipliers.csv"),
                18);
        KujouSara sara = new KujouSara(null, null, 0);
        assertEquals(CharacterId.KUJOU_SARA, sara.getCharacterId(),
                "Sara typed identity");
        assertEquals(Element.ELECTRO, sara.getElement(),
                "Sara element");
        assertClose(9570.0, sara.getBaseStats().get(StatType.BASE_HP),
                "Sara base HP");
        assertClose(195.0, sara.getBaseStats().get(StatType.BASE_ATK),
                "Sara base ATK");
        assertClose(628.0, sara.getBaseStats().get(StatType.BASE_DEF),
                "Sara base DEF");
        assertClose(0.24, sara.getBaseStats().get(StatType.ATK_PERCENT),
                "Sara ascension ATK");
        assertClose(80.0, sara.getEnergyCost(), "Sara Energy cost");
        assertClose(10.0, sara.getSkillCD(), "Sara Skill cooldown");
        assertClose(20.0, sara.getBurstCD(), "Sara Burst cooldown");

        CombatSimulator simulator = simulatorWith(sara);
        List<ActionRecord> records = captureActions(simulator, CharacterId.KUJOU_SARA);
        double[] expectedMultipliers = {
            0.67782, 0.711, 0.89112, 0.92588, 1.0665
        };
        int[] releases = { 14, 13, 19, 19, 32 };
        int[] durations = { 25, 28, 37, 38, 45 };
        double castTime = 0.0;
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
            ActionRecord record = records.get(step);
            assertClose(expectedMultipliers[step],
                    record.action.getDamagePercent(),
                    "Sara N" + (step + 1) + " multiplier");
            assertClose(castTime + (releases[step] + 10.0) * FRAME,
                    record.time,
                    "Sara N" + (step + 1) + " projectile hitmark");
            assertEquals(ActionType.NORMAL, record.action.getActionType(),
                    "Sara Normal action type");
            assertEquals(Element.PHYSICAL, record.action.getElement(),
                    "Sara Normal element");
            castTime += durations[step] * FRAME;
        }
        assertClose(castTime, simulator.getCurrentTime(),
                "Sara Normal string duration");
    }

    private static void testChargedCoverAmbushAndEnergy() {
        KujouSara uncovered = new KujouSara(null, null, 0);
        CombatSimulator uncoveredSimulator = simulatorWith(uncovered);
        List<ActionRecord> uncoveredRecords = captureActions(
                uncoveredSimulator, CharacterId.KUJOU_SARA);
        perform(uncoveredSimulator, CharacterActionKey.CHARGE);
        assertClose(96.0 * FRAME, uncoveredSimulator.getCurrentTime(),
                "Sara uncovered charged duration");
        advanceTo(uncoveredSimulator, 4.0);
        assertEquals(1, uncoveredRecords.size(),
                "Sara charged shot without Cover creates no Ambush");

        StatsContainer weaponStats = new StatsContainer();
        weaponStats.set(StatType.BASE_ATK, 500.0);
        KujouSara sara = new KujouSara(
                new Weapon("Sara Base ATK probe", weaponStats), null, 0);
        Noelle recipient = new Noelle(null, null);
        CombatSimulator simulator = simulatorWith(sara, recipient);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.KUJOU_SARA);
        List<ParticleRecord> particles = captureElectroParticles(simulator);
        sara.spendEnergy(80.0);
        recipient.spendEnergy(100.0);
        sara.addBuff(new SimpleBuff(
                "Sara Skill snapshot probe",
                1.0,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(sara.hasCrowfeatherCover(simulator.getCurrentTime()),
                "Sara Skill grants Cover");
        assertClose(7.0 * FRAME + 10.0,
                sara.getSkillCooldownEndTime(),
                "Sara Skill cooldown starts at frame 7");
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(112.0 * FRAME, simulator.getCurrentTime(),
                "Sara A1 charged timing after Skill animation");
        assertFalse(sara.hasCrowfeatherCover(simulator.getCurrentTime()),
                "Sara charged shot consumes Cover");
        simulator.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(simulator, 202.0 * FRAME);

        List<ActionRecord> charged = named(records, "Tengu Bowmanship Fully Charged");
        List<ActionRecord> ambush = named(records, "Tengu Juurai: Ambush");
        assertEquals(1, charged.size(), "Sara covered charged count");
        assertEquals(1, ambush.size(), "Sara Ambush count");
        assertClose(2.108, charged.get(0).action.getDamagePercent(),
                "Sara fully charged multiplier");
        assertClose(2.13792, ambush.get(0).action.getDamagePercent(),
                "Sara Ambush multiplier");
        assertClose(1.24,
                ambush.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sara Ambush preserves Skill cast snapshot");
        assertEquals(ICDType.None, ambush.get(0).action.getICDType(),
                "Sara Ambush has no ICD");
        assertClose(1.0, ambush.get(0).action.getGaugeUnits(),
                "Sara Ambush applies 1U");
        assertClose(1.2, sara.getCurrentEnergy(),
                "Sara A4 restores flat Energy to self");
        assertClose(1.2, recipient.getCurrentEnergy(),
                "Sara A4 restores flat Energy to ally");

        StatsContainer recipientStats = statsWithTeamBuffs(
                simulator, recipient, simulator.getCurrentTime());
        assertClose((195.0 + 500.0) * 0.73032,
                recipientStats.get(StatType.ATK_FLAT),
                "Sara ATK buff uses character plus weapon Base ATK");
        StatsContainer saraStats = statsWithTeamBuffs(
                simulator, sara, simulator.getCurrentTime());
        assertClose(0.0, saraStats.get(StatType.ATK_FLAT),
                "Sara buff remains recipient-fixed after switching");
        advanceTo(simulator, 302.0 * FRAME);
        assertEquals(1, particles.size(),
                "Sara ordinary Ambush creates one particle packet");
        assertClose(3.0, particles.get(0).count,
                "Sara ordinary Ambush creates three particles");

        KujouSara expiry = new KujouSara(null, null, 0);
        CombatSimulator expirySimulator = simulatorWith(expiry);
        expiry.onAction(
                CharacterActionRequest.skill(SkillActionMode.PRESS),
                expirySimulator);
        advanceTo(expirySimulator, 18.0);
        assertFalse(expiry.hasCrowfeatherCover(18.0),
                "Sara Cover expires at exact 18-second boundary");
        expiry.onAction(
                CharacterActionRequest.of(CharacterActionKey.CHARGE),
                expirySimulator);
        assertClose(18.0 + 96.0 * FRAME,
                expirySimulator.getCurrentTime(),
                "Sara expired Cover does not grant A1 timing");
    }

    private static void testC2C5AndCooldownGates() {
        KujouSara c2 = new KujouSara(null, null, 2);
        Noelle ally = new Noelle(null, null);
        CombatSimulator c2Simulator = simulatorWith(c2, ally);
        List<ActionRecord> c2Records = captureActions(
                c2Simulator, CharacterId.KUJOU_SARA);
        List<ParticleRecord> c2Particles = captureElectroParticles(c2Simulator);
        c2.spendEnergy(80.0);
        ally.spendEnergy(100.0);
        c2Simulator.setActiveCharacter(CharacterId.NOELLE);
        perform(c2Simulator, CharacterActionKey.SKILL);
        advanceTo(c2Simulator, 103.0 * FRAME);
        List<ActionRecord> weak = named(c2Records, "Tengu Juurai: Ambush C2");
        assertEquals(1, weak.size(), "Sara C2 weak feather count");
        assertClose(2.13792 * 0.30,
                weak.get(0).action.getDamagePercent(),
                "Sara C2 weak feather multiplier");
        assertClose(1.2, c2.getCurrentEnergy(),
                "Sara C2 triggers A4");
        assertClose(1.2, ally.getCurrentEnergy(),
                "Sara C2 A4 reaches ally");
        assertClose(9.0 - 96.0 * FRAME,
                c2.getSkillCDRemaining(103.0 * FRAME),
                "Sara C1 reduces Skill cooldown once");
        advanceTo(c2Simulator, 5.0);
        assertEquals(0, c2Particles.size(),
                "Sara C2 weak feather creates no particles");

        KujouSara c5 = new KujouSara(null, null, 5);
        Noelle c5Ally = new Noelle(null, null);
        CombatSimulator c5Simulator = simulatorWith(c5, c5Ally);
        List<ActionRecord> c5Records = captureActions(
                c5Simulator, CharacterId.KUJOU_SARA);
        c5Simulator.setActiveCharacter(CharacterId.NOELLE);
        perform(c5Simulator, CharacterActionKey.SKILL);
        advanceTo(c5Simulator, 103.0 * FRAME);
        assertClose(2.5152 * 0.30,
                named(c5Records, "Tengu Juurai: Ambush C2")
                        .get(0).action.getDamagePercent(),
                "Sara C5 upgrades C2 Ambush talent");
        assertClose(195.0 * 0.8592,
                statsWithTeamBuffs(
                        c5Simulator, c5Ally, c5Simulator.getCurrentTime())
                        .get(StatType.ATK_FLAT),
                "Sara C5 upgrades ATK ratio");

        KujouSara boundary = new KujouSara(null, null, 2);
        Noelle lateRecipient = new Noelle(null, null);
        CombatSimulator boundarySimulator = simulatorWith(
                boundary, lateRecipient);
        perform(boundarySimulator, CharacterActionKey.SKILL);
        advanceTo(boundarySimulator, 102.0 * FRAME);
        boundarySimulator.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(boundarySimulator, 103.0 * FRAME);
        assertClose(195.0 * 0.73032,
                statsWithTeamBuffs(
                        boundarySimulator,
                        boundary,
                        103.0 * FRAME).get(StatType.ATK_FLAT),
                "Sara C2 selects recipient one frame before explosion");
        assertClose(0.0,
                statsWithTeamBuffs(
                        boundarySimulator,
                        lateRecipient,
                        103.0 * FRAME).get(StatType.ATK_FLAT),
                "Sara C2 ignores a switch after recipient capture");
    }

    private static void testBurstTimingSnapshotAndGeometryPolicy() {
        KujouSara c0 = new KujouSara(null, null, 0);
        c0.addBuff(new SimpleBuff(
                "Sara Burst frame-47 snapshot probe",
                0.80,
                0.0,
                stats -> stats.add(StatType.ATK_PERCENT, 1.0)));
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.KUJOU_SARA);
        double[] energy = { Double.NaN, Double.NaN };
        observeEnergy(simulator, c0, 49.0 * FRAME, energy, 0);
        observeEnergy(simulator, c0, 51.0 * FRAME, energy, 1);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(80.0 * FRAME, simulator.getCurrentTime(),
                "Sara Burst animation duration");
        assertClose(80.0, energy[0],
                "Sara Burst Energy remains through frame 49");
        assertClose(0.0, energy[1],
                "Sara Burst Energy is consumed at frame 50");
        assertClose(47.0 * FRAME + 20.0,
                c0.getBurstCooldownEndTime(),
                "Sara Burst cooldown starts at frame 47");
        List<ActionRecord> initial = named(records, "Subjugation: Koukou Sendou Titanbreaker");
        assertEquals(1, initial.size(), "Sara Titanbreaker count");
        assertClose(51.0 * FRAME, initial.get(0).time,
                "Sara Titanbreaker hitmark");
        assertClose(6.9632, initial.get(0).action.getDamagePercent(),
                "Sara Titanbreaker Talent 9");
        assertClose(1.24,
                initial.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_PERCENT),
                "Sara Burst snapshots live frame-47 stats");
        assertClose(0.0,
                initial.get(0).action.getStatSnapshot()
                        .get(StatType.ATK_FLAT),
                "Sara Burst does not snapshot its own Tengu buff");
        advanceTo(simulator, 2.0);
        List<ActionRecord> clusters = named(
                records, "Subjugation: Koukou Sendou Stormcluster");
        assertEquals(1, clusters.size(),
                "Sara stationary policy resolves one Stormcluster");
        assertClose(100.0 * FRAME, clusters.get(0).time,
                "Sara representative Stormcluster hitmark");
        assertClose(0.58004, clusters.get(0).action.getDamagePercent(),
                "Sara Stormcluster Talent 9");
        assertEquals(ICDType.Standard,
                clusters.get(0).action.getICDType(),
                "Sara Stormcluster uses shared standard ICD");
        assertEquals(ICDTag.ElementalBurst,
                clusters.get(0).action.getICDTag(),
                "Sara Stormcluster uses separate Burst ICD group");

        KujouSara c3 = new KujouSara(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(
                c3Simulator, CharacterId.KUJOU_SARA);
        perform(c3Simulator, CharacterActionKey.BURST);
        advanceTo(c3Simulator, 2.0);
        assertClose(8.192,
                named(c3Records, "Subjugation: Koukou Sendou Titanbreaker")
                        .get(0).action.getDamagePercent(),
                "Sara C3 Titanbreaker Talent 12");
        assertClose(0.6824,
                named(c3Records, "Subjugation: Koukou Sendou Stormcluster")
                        .get(0).action.getDamagePercent(),
                "Sara C3 Stormcluster Talent 12");

        KujouSara c4 = new KujouSara(null, null, 4);
        CombatSimulator c4Simulator = simulatorWith(c4);
        List<ActionRecord> c4Records = captureActions(
                c4Simulator, CharacterId.KUJOU_SARA);
        perform(c4Simulator, CharacterActionKey.BURST);
        advanceTo(c4Simulator, 2.0);
        assertEquals(1,
                named(c4Records, "Subjugation: Koukou Sendou Stormcluster")
                        .size(),
                "Sara C4 does not multiply geometry-dependent hits");
    }

    private static void testC6LiveRecipientContract() {
        double c5Electro = recipientProbeDamage(
                5, Element.ELECTRO, false, null);
        double c6Electro = recipientProbeDamage(
                6, Element.ELECTRO, false, null);
        assertClose(30.0, c6Electro - c5Electro,
                "Sara C6 adds live Electro-only CRIT DMG");
        double c5Physical = recipientProbeDamage(
                5, Element.PHYSICAL, false, null);
        double c6Physical = recipientProbeDamage(
                6, Element.PHYSICAL, false, null);
        assertClose(c5Physical, c6Physical,
                "Sara C6 does not affect non-Electro direct damage");
        assertClose(
                recipientProbeDamage(5, Element.ELECTRO, true, null),
                recipientProbeDamage(6, Element.ELECTRO, true, null),
                "Sara C6 expires at the exact six-second boundary");
        assertTrue(
                recipientProbeDamage(
                        6,
                        Element.ELECTRO,
                        false,
                        AttackAction.LunarReactionType.CHARGED)
                        > recipientProbeDamage(
                                5,
                                Element.ELECTRO,
                                false,
                                AttackAction.LunarReactionType.CHARGED),
                "Sara C6 applies to Lunar-Charged");
        assertClose(
                recipientProbeDamage(
                        5,
                        Element.ELECTRO,
                        false,
                        AttackAction.LunarReactionType.BLOOM),
                recipientProbeDamage(
                        6,
                        Element.ELECTRO,
                        false,
                        AttackAction.LunarReactionType.BLOOM),
                "Sara C6 does not leak into Lunar-Bloom");
        assertClose(
                recipientProbeDamage(
                        5,
                        Element.ELECTRO,
                        false,
                        AttackAction.LunarReactionType.CRYSTALLIZE),
                recipientProbeDamage(
                        6,
                        Element.ELECTRO,
                        false,
                        AttackAction.LunarReactionType.CRYSTALLIZE),
                "Sara C6 does not leak into Lunar-Crystallize");

        double[] c5Burst = burstSelfC6Damage(5);
        double[] c6Burst = burstSelfC6Damage(6);
        assertClose(c5Burst[0], c6Burst[0],
                "Sara Titanbreaker does not receive its own C6");
        assertTrue(c6Burst[1] > c5Burst[1],
                "Sara later Stormcluster receives live C6");
    }

    private static double[] burstSelfC6Damage(int constellation) {
        KujouSara sara = new KujouSara(null, null, constellation);
        CombatSimulator simulator = simulatorWith(sara);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.KUJOU_SARA);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 2.0);
        return new double[] {
            named(records, "Subjugation: Koukou Sendou Titanbreaker")
                    .get(0).damage,
            named(records, "Subjugation: Koukou Sendou Stormcluster")
                    .get(0).damage
        };
    }

    private static double recipientProbeDamage(
            int constellation,
            Element element,
            boolean expired,
            AttackAction.LunarReactionType lunarType) {
        KujouSara sara = new KujouSara(null, null, constellation);
        Noelle recipient = new Noelle(null, null);
        recipient.getBaseStats().set(StatType.BASE_ATK, 100.0);
        recipient.getBaseStats().set(StatType.ATK_PERCENT, 0.0);
        recipient.getBaseStats().set(StatType.ATK_FLAT, 0.0);
        recipient.getBaseStats().set(StatType.CRIT_RATE, 1.0);
        recipient.getBaseStats().set(StatType.CRIT_DMG, 0.50);
        StatsContainer preBuffSnapshot = recipient.getEffectiveStats(0.0);
        CombatSimulator simulator = simulatorWith(sara, recipient);
        simulator.setActiveCharacter(CharacterId.NOELLE);
        List<ActionRecord> records = captureActions(
                simulator, CharacterId.NOELLE);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 103.0 * FRAME);
        assertClose(195.0 * 0.8592,
                statsWithTeamBuffs(simulator, recipient, simulator.getCurrentTime())
                        .get(StatType.ATK_FLAT),
                "Sara recipient receives one C5+ ATK buff");
        if (expired) {
            advanceTo(simulator, 103.0 * FRAME + 6.0);
        }
        AttackAction probe = new AttackAction(
                "Sara C6 live probe",
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.NORMAL);
        probe.setICD(ICDType.None, ICDTag.None, 0.0);
        if (lunarType != null) {
            probe.setLunarReactionType(lunarType);
        }
        probe.setStatSnapshot(preBuffSnapshot);
        simulator.performActionWithoutTimeAdvance(CharacterId.NOELLE, probe);
        return records.get(0).damage;
    }

    private static void testSnapshotRestoreAndStaleEvents() {
        KujouSara skillSara = new KujouSara(null, null, 0);
        CombatSimulator skillSimulator = simulatorWith(skillSara);
        List<ActionRecord> skillRecords = captureActions(
                skillSimulator, CharacterId.KUJOU_SARA);
        List<ParticleRecord> particles = captureElectroParticles(skillSimulator);
        perform(skillSimulator, CharacterActionKey.SKILL);
        perform(skillSimulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot skillSnapshot = skillSimulator.saveSnapshot();
        advanceTo(skillSimulator, 6.0);
        assertEquals(1, named(skillRecords, "Tengu Juurai: Ambush").size(),
                "Sara original Ambush branch resolves once");
        assertEquals(1, particles.size(),
                "Sara original particle branch resolves once");
        skillSimulator.restoreSnapshot(skillSnapshot);
        advanceTo(skillSimulator, 6.0);
        assertEquals(2, named(skillRecords, "Tengu Juurai: Ambush").size(),
                "Sara restored Ambush branch resolves once");
        assertEquals(2, particles.size(),
                "Sara restored particle branch resolves once");
        skillSimulator.restoreSnapshot(skillSnapshot);
        skillSimulator.restoreSnapshot(skillSnapshot);
        advanceTo(skillSimulator, 6.0);
        assertEquals(3, named(skillRecords, "Tengu Juurai: Ambush").size(),
                "Sara repeated restore suppresses stale Ambush event");
        assertEquals(3, particles.size(),
                "Sara repeated restore suppresses stale particle event");

        KujouSara burstSara = new KujouSara(null, null, 0);
        CombatSimulator burstSimulator = simulatorWith(burstSara);
        List<ActionRecord> burstRecords = captureActions(
                burstSimulator, CharacterId.KUJOU_SARA);
        perform(burstSimulator, CharacterActionKey.BURST);
        SimulatorSnapshot burstSnapshot = burstSimulator.saveSnapshot();
        advanceTo(burstSimulator, 2.0);
        assertEquals(1,
                named(burstRecords, "Subjugation: Koukou Sendou Stormcluster")
                        .size(),
                "Sara original Stormcluster resolves once");
        burstSimulator.restoreSnapshot(burstSnapshot);
        advanceTo(burstSimulator, 2.0);
        assertEquals(2,
                named(burstRecords, "Subjugation: Koukou Sendou Stormcluster")
                        .size(),
                "Sara restored Stormcluster resolves once");
        burstSimulator.restoreSnapshot(burstSnapshot);
        burstSimulator.restoreSnapshot(burstSnapshot);
        advanceTo(burstSimulator, 2.0);
        assertEquals(3,
                named(burstRecords, "Subjugation: Koukou Sendou Stormcluster")
                        .size(),
                "Sara repeated restore suppresses stale Burst event");
    }

    private static void testInvalidInputsAndBindingGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new KujouSara(null, null, -1),
                "Sara rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new KujouSara(null, null, 7),
                "Sara rejects constellation above six");

        KujouSara unsupported = new KujouSara(null, null, 0);
        CombatSimulator unsupportedSimulator = simulatorWith(unsupported);
        assertThrows(IllegalArgumentException.class,
                () -> unsupported.onAction(null, unsupportedSimulator),
                "Sara rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> unsupportedSimulator.performAction(
                        CharacterId.KUJOU_SARA,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Sara rejects Hold Skill");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSimulator, CharacterActionKey.DASH),
                "Sara rejects Dash");
        assertThrows(IllegalArgumentException.class,
                () -> perform(unsupportedSimulator, CharacterActionKey.PLUNGE),
                "Sara rejects Plunge");

        KujouSara insufficient = new KujouSara(null, null, 0);
        CombatSimulator insufficientSimulator = simulatorWith(insufficient);
        List<ActionRecord> insufficientRecords = captureActions(
                insufficientSimulator, CharacterId.KUJOU_SARA);
        insufficient.spendEnergy(80.0);
        perform(insufficientSimulator, CharacterActionKey.BURST);
        assertEquals(0, insufficientRecords.size(),
                "Sara insufficient Energy rejects Burst");
        assertClose(80.0, insufficient.getMissedBurstCost(),
                "Sara records rejected Burst cost");

        KujouSara reusable = new KujouSara(null, null, 0);
        simulatorWith(reusable);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reusable),
                "Sara rejects cross-simulator reuse");
        KujouSara owner = new KujouSara(null, null, 0);
        KujouSara foreign = new KujouSara(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertThrows(IllegalArgumentException.class,
                () -> owner.restoreCharacterState(
                        foreignState, new CombatSimulator()),
                "Sara rejects another instance's state");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
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

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.KUJOU_SARA,
                CharacterActionRequest.of(key));
    }

    private static StatsContainer statsWithTeamBuffs(
            CombatSimulator simulator,
            Character character,
            double currentTime) {
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator,
            CharacterId characterId) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == characterId) {
                records.add(new ActionRecord(action, damage, time));
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
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
    }

    private static void observeEnergy(
            CombatSimulator simulator,
            KujouSara sara,
            double time,
            double[] values,
            int index) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                values[index] = sara.getCurrentEnergy();
            }
        });
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
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Kujou Sara,"),
                    path + " identity at line " + (index + 1));
        }
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

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
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
}
