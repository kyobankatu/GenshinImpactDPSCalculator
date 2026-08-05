package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import mechanics.element.ICDManager;
import model.character.Arlecchino;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
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

/** Focused regression checks for Arlecchino's fixed-target Red Death slice. */
public final class ArlecchinoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ArlecchinoRegressionTest() {
    }

    /** Runs data, action, Directive, constellation, exclusion, and restore checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPolearmBasicsAndUnsupportedActions();
        testSkillDirectiveUpgradeParticlesAndIcd();
        testChargedCollectionMasqueAndConsumption();
        testBurstResetAndConstellations();
        testFailClosedScopeAndIsolation();
        testGenerationInvalidation();
        testSnapshotRestore();
        System.out.println("ArlecchinoRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Arlecchino arlecchino = new Arlecchino(null, null, 6);
        assertEquals(CharacterId.ARLECCHINO,
                arlecchino.getCharacterId(),
                "Arlecchino typed identity");
        assertEquals(CharacterId.ARLECCHINO,
                CharacterId.fromName("Arlecchino"),
                "Arlecchino name lookup");
        assertEquals(CharacterId.ARLECCHINO,
                CharacterId.fromNumericId(95),
                "Arlecchino numeric lookup");
        assertEquals(95, CharacterId.ARLECCHINO.getNumericId(),
                "Arlecchino stable numeric id");
        assertEquals(CharacterRegion.SNEZHNAYA,
                CharacterId.ARLECCHINO.getRegion(),
                "Arlecchino region");
        assertEquals(Element.PYRO, arlecchino.getElement(),
                "Arlecchino element");
        assertClose(13103.0,
                arlecchino.getBaseStats().get(StatType.BASE_HP),
                "Arlecchino base HP");
        assertClose(342.0,
                arlecchino.getBaseStats().get(StatType.BASE_ATK),
                "Arlecchino base ATK");
        assertClose(765.0,
                arlecchino.getBaseStats().get(StatType.BASE_DEF),
                "Arlecchino base DEF");
        assertClose(0.884,
                arlecchino.getBaseStats().get(StatType.CRIT_DMG),
                "Arlecchino base and ascension CRIT DMG");
        assertClose(60.0, arlecchino.getEnergyCost(),
                "Arlecchino Energy cost");
        assertClose(30.0, arlecchino.getSkillCD(),
                "Arlecchino Skill cooldown");
        assertClose(15.0, arlecchino.getBurstCD(),
                "Arlecchino Burst cooldown");
        assertClose(0.40,
                arlecchino.getEffectiveStats(0.0).get(
                        StatType.PYRO_DMG_BONUS),
                "Arlecchino permanent combat Pyro bonus");
        for (int constellation = 0;
                constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    new Arlecchino(null, null, constellation)
                            .getConstellation(),
                    "Arlecchino explicit C" + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Arlecchino/"
                        + "Arlecchino_Status.csv"), 12);
        assertCsvShape(Path.of(
                "config/characters/Arlecchino/"
                        + "Arlecchino_Multipliers.csv"), 50);
        assertCsvValue("Masque Increase C3", 2.716);
        assertCsvValue("Directive Due Bond", 1.30);
        assertCsvValue("Balemoon Rising C5", 7.408);
        assertCsvValue("C6 Burst ATK Bond Ratio", 7.0);
        assertThrows(IllegalArgumentException.class,
                () -> new Arlecchino(null, null, -1),
                "Arlecchino rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Arlecchino(null, null, 7),
                "Arlecchino rejects constellation above C6");
    }

    private static void testPolearmBasicsAndUnsupportedActions() {
        Arlecchino arlecchino = new Arlecchino(null, null, 0);
        CombatSimulator simulator = simulatorWith(arlecchino);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            0.872681,
            0.957290,
            1.201274,
            0.682434,
            1.285709,
            1.568577
        };
        int[] firstHitFrames = { 11, 16, 17, 24, 21, 44 };
        int[] durations = { 24, 31, 39, 55, 43, 59 };
        for (int step = 0; step < multipliers.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionKey.NORMAL);
            List<ActionRecord> hits = named(
                    records,
                    "Invitation to a Beheading N" + (step + 1));
            assertEquals(step == 3 ? 2 : 1, hits.size(),
                    "Arlecchino N" + (step + 1) + " hit count");
            assertClose(castTime + firstHitFrames[step] * FRAME,
                    hits.get(0).time,
                    "Arlecchino N" + (step + 1) + " hitmark");
            assertClose(castTime + durations[step] * FRAME,
                    simulator.getCurrentTime(),
                    "Arlecchino N" + (step + 1) + " recovery");
            assertClose(multipliers[step],
                    hits.get(0).action.getDamagePercent(),
                    "Arlecchino N" + (step + 1) + " multiplier");
            assertEquals(Element.PHYSICAL,
                    hits.get(0).action.getElement(),
                    "Arlecchino uninfused Normal element");
            assertEquals(ActionType.NORMAL,
                    hits.get(0).action.getActionType(),
                    "Arlecchino Normal action type");
            assertEquals(ICDTag.NormalAttack,
                    hits.get(0).action.getICDTag(),
                    "Arlecchino Normal ICD tag");
        }
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(
                records, "Invitation to a Beheading N1").size(),
                "Arlecchino Normal string wraps after N6");

        Arlecchino basics = new Arlecchino(null, null, 0);
        CombatSimulator basicsSimulator = simulatorWith(basics);
        List<ActionRecord> basicsRecords = captureActions(basicsSimulator);
        perform(basicsSimulator, CharacterActionKey.CHARGE);
        ActionRecord charged = onlyNamed(
                basicsRecords,
                "Invitation to a Beheading Charged");
        assertClose(37.0 * FRAME, charged.time,
                "Arlecchino Charged hitmark");
        assertClose(60.0 * FRAME, basicsSimulator.getCurrentTime(),
                "Arlecchino Charged recovery");
        assertClose(1.668480, charged.action.getDamagePercent(),
                "Arlecchino Charged multiplier");
        assertEquals(ICDType.ArlecchinoCharged,
                charged.action.getICDType(),
                "Arlecchino Charged private ICD type");
        assertClose(0.0, charged.action.getGaugeUnits(),
                "Physical Charged applies no aura");

        double plungeCast = basicsSimulator.getCurrentTime();
        perform(basicsSimulator, CharacterActionKey.PLUNGE);
        ActionRecord plunge = onlyNamed(
                basicsRecords,
                "Invitation to a Beheading High Plunge");
        assertClose(plungeCast + 48.0 * FRAME, plunge.time,
                "Arlecchino high-Plunge hitmark");
        assertClose(plungeCast + 81.0 * FRAME,
                basicsSimulator.getCurrentTime(),
                "Arlecchino high-Plunge recovery");
        assertClose(2.933586, plunge.action.getDamagePercent(),
                "Arlecchino high-Plunge multiplier");
        assertEquals(ICDType.None, plunge.action.getICDType(),
                "Arlecchino high-Plunge has no ICD");

        assertThrows(IllegalArgumentException.class,
                () -> perform(basicsSimulator, CharacterActionKey.DASH),
                "Arlecchino rejects movement actions");
        assertThrows(IllegalArgumentException.class,
                () -> basicsSimulator.performAction(
                        CharacterId.ARLECCHINO,
                        CharacterActionRequest.skill(
                                SkillActionMode.HOLD)),
                "Arlecchino rejects unsupported Hold Skill");
    }

    private static void testSkillDirectiveUpgradeParticlesAndIcd() {
        Arlecchino arlecchino = new Arlecchino(null, null, 0);
        CombatSimulator simulator = simulatorWith(arlecchino);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = capturePyroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(77.0 * FRAME, simulator.getCurrentTime(),
                "All Is Ash fixed recovery");
        assertClose(16.0 * FRAME, arlecchino.getLastSkillTime(),
                "All Is Ash cooldown starts at frame sixteen");
        assertClose(30.0 - 61.0 * FRAME,
                arlecchino.getSkillCDRemaining(
                        simulator.getCurrentTime()),
                "All Is Ash retains thirty-second cooldown");
        ActionRecord spike = onlyNamed(records, "All Is Ash (Spike)");
        ActionRecord cleave = onlyNamed(records, "All Is Ash (Cleave)");
        assertClose(17.0 * FRAME, spike.time,
                "All Is Ash Spike hitmark");
        assertClose(38.0 * FRAME, cleave.time,
                "All Is Ash Cleave hitmark");
        assertClose(0.252280, spike.action.getDamagePercent(),
                "All Is Ash Spike multiplier");
        assertClose(2.270520, cleave.action.getDamagePercent(),
                "All Is Ash Cleave multiplier");
        assertEquals(ICDType.ArlecchinoElementalArt,
                spike.action.getICDType(),
                "Spike uses Arlecchino private ICD");
        assertEquals(ICDType.None, cleave.action.getICDType(),
                "Cleave has no ICD");
        assertTrue(arlecchino.hasActiveDirective(
                        simulator.getCurrentTime()),
                "Accepted Cleave applies one Directive");
        assertEquals(1, arlecchino.getDirectiveLevel(
                        simulator.getCurrentTime()),
                "C0 Directive starts Ordinal");

        double directiveApplication = 38.0 * FRAME;
        advanceTo(simulator, directiveApplication + 5.0 - EPSILON);
        assertEquals(0, named(records, "Blood-Debt Directive").size(),
                "Directive does not tick before five seconds");
        assertEquals(1, arlecchino.getDirectiveLevel(
                        simulator.getCurrentTime()),
                "Directive remains Ordinal before A1 boundary");
        advanceTo(simulator, directiveApplication + 5.0);
        assertEquals(1, named(records, "Blood-Debt Directive").size(),
                "Directive first tick resolves at five seconds");
        assertEquals(2, arlecchino.getDirectiveLevel(
                        simulator.getCurrentTime()),
                "A1 upgrades Directive at exact five seconds");
        advanceTo(simulator, directiveApplication + 10.0);
        assertEquals(2, named(records, "Blood-Debt Directive").size(),
                "Directive emits two five-second ticks");
        assertClose(0.540600,
                named(records, "Blood-Debt Directive")
                        .get(0).action.getDamagePercent(),
                "Directive tick multiplier");

        assertEquals(1, particles.size(),
                "Accepted Cleave creates one particle packet");
        assertClose(5.0, particles.get(0).count,
                "All Is Ash creates five Pyro particles");
        assertClose(138.0 * FRAME, particles.get(0).time,
                "All Is Ash particle travel time");

        ICDManager icd = new ICDManager();
        assertTrue(icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_ElementalArt,
                        ICDType.ArlecchinoElementalArt,
                        0.0),
                "Elemental Art ICD admits first hit");
        assertTrue(!icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_ElementalArt,
                        ICDType.ArlecchinoElementalArt,
                        1.0),
                "Elemental Art ICD suppresses second hit");
        assertTrue(icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_Charged,
                        ICDType.ArlecchinoCharged,
                        1.0),
                "Charged ICD is independent at the same time");
        assertTrue(icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_ElementalArt,
                        ICDType.ArlecchinoElementalArt,
                        2.0),
                "Elemental Art ICD applies on every third hit");
        assertTrue(!icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_ElementalArt,
                        ICDType.ArlecchinoElementalArt,
                        3.0),
                "Elemental Art ICD restarts after third-hit application");
        assertTrue(icd.checkApplication(
                        "Arlecchino",
                        ICDTag.Arlecchino_ElementalArt,
                        ICDType.ArlecchinoElementalArt,
                        12.0),
                "Elemental Art ICD applies at exact ten seconds");

        advanceTo(simulator, directiveApplication + 30.0);
        assertTrue(!arlecchino.hasActiveDirective(
                        simulator.getCurrentTime()),
                "Directive expires at exact thirty seconds");
    }

    private static void testChargedCollectionMasqueAndConsumption() {
        Arlecchino c0 = new Arlecchino(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(0.65, c0.getBondOfLifeRatio(),
                "Pre-upgrade Charged collection grants 65% Bond");
        ActionRecord collectionCharged = onlyNamed(
                records,
                "Invitation to a Beheading Charged");
        assertEquals(Element.PYRO, collectionCharged.action.getElement(),
                "Charged hit observes its earlier Directive collection");
        assertClose(0.0,
                collectionCharged.action.getAdditiveBaseDmgBonus(),
                "Masque does not add damage to Charged Attacks");

        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord normal = onlyNamed(
                records,
                "Invitation to a Beheading N1");
        assertEquals(Element.PYRO, normal.action.getElement(),
                "Masque infuses Normal Attacks");
        assertClose(2.212 * 0.65 * 342.0,
                normal.action.getAdditiveBaseDmgBonus(),
                "Masque snapshots live Bond and ATK into Normal addition");
        assertClose(0.65 * 0.925, c0.getBondOfLifeRatio(),
                "Accepted Normal consumes 7.5% of current Bond");
        assertClose(normal.time + 2.0 * FRAME,
                c0.getNextBondConsumeAllowedTime(),
                "Normal Bond consumption uses the two-frame gate");
        assertClose(16.0 * FRAME + 30.0 - 0.8,
                c0.getSkillCooldownEndTime(),
                "Accepted Masque Normal reduces Skill cooldown by 0.8s");

        for (int index = 0; index < 10; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        ActionRecord firstBelowThreshold = named(
                records,
                "Invitation to a Beheading N5").get(1);
        assertEquals(Element.PHYSICAL,
                firstBelowThreshold.action.getElement(),
                "Normal after Bond falls below 30% is Physical");

        Arlecchino upgraded = new Arlecchino(null, null, 0);
        CombatSimulator upgradedSimulator = simulatorWith(upgraded);
        perform(upgradedSimulator, CharacterActionKey.SKILL);
        advanceTo(upgradedSimulator, 38.0 * FRAME + 5.0);
        perform(upgradedSimulator, CharacterActionKey.CHARGE);
        assertClose(1.30, upgraded.getBondOfLifeRatio(),
                "A1 Due collection grants 130% Bond");

        Arlecchino c1 = new Arlecchino(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.SKILL);
        perform(c1Simulator, CharacterActionKey.CHARGE);
        perform(c1Simulator, CharacterActionKey.NORMAL);
        assertClose((2.212 + 1.0) * 0.65 * 342.0,
                onlyNamed(c1Records,
                        "Invitation to a Beheading N1")
                        .action.getAdditiveBaseDmgBonus(),
                "C1 adds 100% to the Masque talent ratio");

        Arlecchino c3 = new Arlecchino(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        perform(c3Simulator, CharacterActionKey.CHARGE);
        perform(c3Simulator, CharacterActionKey.NORMAL);
        ActionRecord c3Normal = onlyNamed(
                c3Records,
                "Invitation to a Beheading N1");
        assertClose(1.071520, c3Normal.action.getDamagePercent(),
                "C3 raises Normal talent to level twelve");
        assertClose((2.716 + 1.0) * 1.30 * 342.0,
                c3Normal.action.getAdditiveBaseDmgBonus(),
                "C3 raises the Masque talent ratio");
    }

    private static void testBurstResetAndConstellations() {
        Arlecchino c6 = new Arlecchino(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        double burstCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.BURST);
        assertClose(burstCast, c6.getLastBurstTime(),
                "Balemoon Rising cooldown starts at cast");
        assertClose(15.0, c6.getTotalFlatEnergy(),
                "C4 restores fifteen flat Energy after Burst spends sixty");
        assertClose(15.0, c6.getTotalScaledParticleEnergy(),
                "Skill particles arrive during the Burst animation");
        assertClose(30.0, c6.getCurrentEnergy(),
                "C4 and Skill particles refill thirty Energy in order");
        assertClose(burstCast + 13.0,
                c6.getBurstCooldownEndTime(),
                "C4 reduces active Burst cooldown by two seconds");
        assertClose(burstCast + 22.0 * FRAME + 10.0,
                c6.getNextC2AllowedTime(),
                "C2 starts its independent ten-second gate");
        assertClose(burstCast + 22.0 * FRAME + 10.0,
                c6.getNextC4AllowedTime(),
                "C4 starts its independent ten-second gate");
        ActionRecord c2 = onlyNamed(records, "Balemoon Bloodfire (C2)");
        assertClose(burstCast + 72.0 * FRAME, c2.time,
                "C2 Bloodfire resolves fifty frames after collection");
        assertClose(9.0, c2.action.getDamagePercent(),
                "C2 Bloodfire multiplier");
        assertEquals(ICDType.None, c2.action.getICDType(),
                "C2 Bloodfire has no ICD");

        ActionRecord burst = onlyNamed(records, "Balemoon Rising");
        assertClose(burstCast + 110.0 * FRAME, burst.time,
                "Balemoon Rising damage hitmark");
        assertClose(7.408, burst.action.getDamagePercent(),
                "C5 raises Burst talent to level twelve");
        assertClose(7.0 * 1.30 * 342.0,
                burst.action.getAdditiveBaseDmgBonus(),
                "C6 Burst addition uses post-collection live Bond");
        assertClose(0.15,
                burst.action.getStatSnapshot().get(StatType.CRIT_RATE),
                "C6 adds ten percent Burst CRIT Rate");
        assertClose(1.584,
                burst.action.getStatSnapshot().get(StatType.CRIT_DMG),
                "C6 adds seventy percent Burst CRIT DMG");
        assertClose(0.0, c6.getBondOfLifeRatio(),
                "Burst resets local Bond after damage");
        assertClose(0.0,
                c6.getSkillCDRemaining(simulator.getCurrentTime()),
                "Burst resets the active Skill cooldown");
        assertClose(38.0 * FRAME + 20.0,
                c6.getC6ExpirationTime(),
                "C6 CRIT window starts on Skill frame thirty-eight");
        double initialC6Expiration = c6.getC6ExpirationTime();
        perform(simulator, CharacterActionKey.SKILL);
        assertClose(initialC6Expiration, c6.getC6ExpirationTime(),
                "C6 does not refresh inside its fifteen-second gate");
        advanceTo(simulator, initialC6Expiration);
        perform(simulator, CharacterActionKey.NORMAL);
        ActionRecord expiredC6Normal = onlyNamed(
                records,
                "Invitation to a Beheading N1");
        assertClose(0.05,
                expiredC6Normal.action.getStatSnapshot().get(
                        StatType.CRIT_RATE),
                "C6 CRIT Rate expires at exact twenty seconds");
        assertClose(0.884,
                expiredC6Normal.action.getStatSnapshot().get(
                        StatType.CRIT_DMG),
                "C6 CRIT DMG expires at exact twenty seconds");

        Arlecchino c5 = new Arlecchino(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        ActionRecord c5Burst = onlyNamed(c5Records, "Balemoon Rising");
        assertClose(7.408, c5Burst.action.getDamagePercent(),
                "C5 Burst multiplier without C6");
        assertClose(0.0, c5Burst.action.getAdditiveBaseDmgBonus(),
                "C5 has no C6 Burst addition");
        assertClose(0.05,
                c5Burst.action.getStatSnapshot().get(StatType.CRIT_RATE),
                "C5 has no C6 CRIT Rate");
    }

    private static void testFailClosedScopeAndIsolation() {
        Arlecchino arlecchino = new Arlecchino(null, null, 0);
        assertTrue(!arlecchino.isPlayerHpHealingRepresented(),
                "Player HP, healing, and damage intake are excluded");
        assertTrue(!arlecchino.isDefensiveEffectsRepresented(),
                "A4 and constellation defenses are excluded");
        assertTrue(!arlecchino.isMovementGeometryRepresented(),
                "Movement and geometry are excluded");
        assertTrue(!arlecchino.isMultiTargetRandomnessRepresented(),
                "Multi-target and random selection are excluded");
        assertTrue(!arlecchino.isExternalBondOfLifeRepresented(),
                "External Bond integrations are excluded");
        assertTrue(!arlecchino.isStaminaHitlagRepresented(),
                "Stamina and hitlag are excluded");
        assertTrue(!arlecchino.isLowPlungeRepresented(),
                "Low Plunge and collision selection are excluded");
        assertTrue(!arlecchino.isTargetDeathCollectionRepresented(),
                "Unsupported target-death collection fails closed");

        CombatSimulator simulator = simulatorWith(arlecchino);
        assertThrows(IllegalArgumentException.class,
                () -> arlecchino.onAction(null, simulator),
                "Arlecchino rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> arlecchino.initializeForSimulator(null),
                "Arlecchino rejects null simulator");

        Arlecchino noTarget = new Arlecchino(null, null, 0);
        CombatSimulator noTargetSimulator = simulatorWithoutEnemy(noTarget);
        List<ParticleRecord> noTargetParticles =
                capturePyroParticles(noTargetSimulator);
        perform(noTargetSimulator, CharacterActionKey.SKILL);
        advanceTo(noTargetSimulator, 138.0 * FRAME);
        assertTrue(!noTarget.hasActiveDirective(
                        noTargetSimulator.getCurrentTime()),
                "No target cannot create a Directive");
        assertEquals(0, noTargetParticles.size(),
                "No accepted Cleave cannot create particles");

        Arlecchino reused = new Arlecchino(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Arlecchino rejects cross-simulator reuse");
        Arlecchino foreign = new Arlecchino(null, null, 0);
        SnapshotAwareCharacterEffect.State foreignState =
                foreign.captureCharacterState();
        assertTrue(!arlecchino.acceptsCharacterState(foreignState),
                "Arlecchino rejects another owner's snapshot payload");

        Arlecchino independent = new Arlecchino(null, null, 0);
        CombatSimulator independentSimulator = simulatorWith(independent);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(0.65, arlecchino.getBondOfLifeRatio(),
                "First owner tracks its Bond");
        assertClose(0.0, independent.getBondOfLifeRatio(),
                "Independent owner does not share Bond state");
        assertTrue(!independent.hasActiveDirective(
                        independentSimulator.getCurrentTime()),
                "Independent owner does not share Directive state");
    }

    private static void testGenerationInvalidation() {
        Arlecchino arlecchino = new Arlecchino(null, null, 0);
        CombatSimulator simulator = simulatorWith(arlecchino);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.BURST);
        assertTrue(!arlecchino.hasActiveDirective(
                        simulator.getCurrentTime()),
                "Burst collection invalidates the first Directive");
        perform(simulator, CharacterActionKey.SKILL);
        double secondApplication = 38.0 * FRAME
                + 77.0 * FRAME + 146.0 * FRAME;
        advanceTo(simulator, secondApplication + 5.0 - EPSILON);
        assertEquals(0, named(records, "Blood-Debt Directive").size(),
                "Collected generation cannot emit stale Directive ticks");
        advanceTo(simulator, secondApplication + 5.0);
        assertEquals(1, named(records, "Blood-Debt Directive").size(),
                "Replacement generation emits one current Directive tick");
    }

    private static void testSnapshotRestore() {
        Arlecchino arlecchino = new Arlecchino(null, null, 0);
        CombatSimulator simulator = simulatorWith(arlecchino);
        List<ActionRecord> records = captureActions(simulator);
        List<ParticleRecord> particles = capturePyroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(0.65, arlecchino.getBondOfLifeRatio(),
                "Divergent branch collects Ordinal Directive");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        particles.clear();
        assertClose(0.0, arlecchino.getBondOfLifeRatio(),
                "Rollback restores local Bond");
        assertEquals(1, arlecchino.getDirectiveLevel(
                        simulator.getCurrentTime()),
                "Rollback restores Ordinal Directive");
        advanceTo(simulator, 38.0 * FRAME + 5.0);
        assertEquals(1, named(records, "Blood-Debt Directive").size(),
                "Repeated restore reconstructs first Directive tick once");
        assertEquals(1, particles.size(),
                "Repeated restore reconstructs particle packet once");
        assertEquals(2, arlecchino.getDirectiveLevel(
                        simulator.getCurrentTime()),
                "Rollback-restored Directive upgrades at source boundary");
        perform(simulator, CharacterActionKey.CHARGE);
        assertClose(1.30, arlecchino.getBondOfLifeRatio(),
                "Restored Due Directive collects 130% Bond");
        advanceTo(simulator, 38.0 * FRAME + 30.0);
        assertEquals(1, named(records, "Blood-Debt Directive").size(),
                "Collected restored Directive emits no stale second tick");
        assertEquals(0, arlecchino.getPendingEventCount(),
                "All reconstructed and invalidated events drain exactly once");
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
                CharacterId.ARLECCHINO,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ARLECCHINO) {
                records.add(new ActionRecord(action, time));
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
            assertTrue(lines.get(index).startsWith("Arlecchino,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(
            String key,
            double expected) {
        double actual = TalentDataManager.getInstance().get(
                "Arlecchino",
                key,
                Double.NaN);
        assertClose(expected, actual, "Arlecchino CSV " + key);
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
