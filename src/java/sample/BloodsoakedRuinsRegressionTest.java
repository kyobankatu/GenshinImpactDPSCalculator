package sample;

import java.util.List;

import mechanics.formula.DamageCalculator;
import mechanics.reaction.ReactionEffectScheduler;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.BloodsoakedRuins;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused metadata, Lunar damage, timing, Energy, snapshot, and binding checks. */
public final class BloodsoakedRuinsRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double FRAME = 1.0 / 60.0;

    private BloodsoakedRuinsRegressionTest() {
    }

    /** Runs all Bloodsoaked Ruins regression cases. */
    public static void main(String[] args) {
        testMetadataAndRefinementTable();
        testBurstWindowAndCooldownAutoWait();
        testBurstRejectionsAndSwitchPersistence();
        testDirectLunarDamageRouting();
        testWeightedAndPeriodicOwnerContribution();
        testActualReactionTriggerAndOrdering();
        testReactionSourceAndDerivedExclusions();
        testCritRefreshAndExactExpiry();
        testEnergyCooldownBoundaries();
        testSnapshotRestoreAndIndependentInstances();
        testBindingAndStateGuards();
        System.out.println("BloodsoakedRuinsRegressionTest passed");
    }

    private static void testMetadataAndRefinementTable() {
        BloodsoakedRuins defaultWeapon = new BloodsoakedRuins();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Bloodsoaked Ruins default refinement");
        assertEquals("Bloodsoaked Ruins", defaultWeapon.getName(),
                "Bloodsoaked Ruins name");
        assertEquals(WeaponType.POLEARM, defaultWeapon.getWeaponType(),
                "Bloodsoaked Ruins weapon type");
        assertClose(674.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Bloodsoaked Ruins base ATK");
        assertClose(0.221,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Bloodsoaked Ruins CRIT Rate");

        for (int refinement = 1; refinement <= 5; refinement++) {
            BloodsoakedRuins weapon = new BloodsoakedRuins(refinement);
            assertClose(0.24 + 0.12 * refinement,
                    weapon.getLunarChargedBonus(),
                    "Bloodsoaked Ruins Lunar-Charged bonus R" + refinement);
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getCritDamageBonus(),
                    "Bloodsoaked Ruins CRIT DMG bonus R" + refinement);
            assertClose(11.0 + refinement,
                    weapon.getEnergyRecovery(),
                    "Bloodsoaked Ruins Energy recovery R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BloodsoakedRuins(0),
                "Bloodsoaked Ruins rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new BloodsoakedRuins(6),
                "Bloodsoaked Ruins rejects R6");
    }

    private static void testBurstWindowAndCooldownAutoWait() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 2.0);
        CombatSimulator sim = simulatorWith(owner);

        performBurst(sim, owner);
        assertTrue(weapon.isBurstWindowActive(0.0),
                "Accepted owner Burst opens the window at cast time");
        assertTrue(weapon.isBurstWindowActive(210.0 * FRAME - 1e-9),
                "Burst window remains active immediately before 210 frames");
        assertTrue(!weapon.isBurstWindowActive(210.0 * FRAME),
                "Burst window expires at the exact 210-frame boundary");

        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        performBurst(sim, owner);
        assertClose(2.0, sim.getCurrentTime(),
                "Second Burst auto-waits for the owner cooldown");
        assertTrue(weapon.isBurstWindowActive(2.0),
                "Post-wait Burst refreshes from its accepted timestamp");
        assertClose(0.36,
                owner.getEffectiveStats(2.0)
                        .get(StatType.LUNAR_CHARGED_DMG_BONUS),
                "Burst refresh replaces rather than stacks the R1 bonus");
        assertTrue(weapon.isBurstWindowActive(5.499999),
                "Refreshed Burst window remains active before its expiry");
        assertTrue(!weapon.isBurstWindowActive(5.5),
                "Refreshed Burst window keeps a half-open expiry");
    }

    private static void testBurstRejectionsAndSwitchPersistence() {
        BloodsoakedRuins persistentWeapon = new BloodsoakedRuins(1);
        TestCharacter persistentOwner = character(
                CharacterId.YUN_JIN,
                Element.ELECTRO,
                persistentWeapon,
                true,
                0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null, false, 0.0);
        CombatSimulator persistentSim = simulatorWith(persistentOwner, ally);
        performBurst(persistentSim, persistentOwner);
        persistentSim.switchCharacter(ally.getCharacterId());
        assertTrue(persistentSim.getActiveCharacter() == ally,
                "Owner switches off field after an accepted Burst");
        assertTrue(persistentWeapon.isBurstWindowActive(
                        persistentSim.getCurrentTime()),
                "Accepted Burst window persists through a standard switch");

        BloodsoakedRuins insufficientWeapon = new BloodsoakedRuins(1);
        TestCharacter insufficientOwner = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                insufficientWeapon,
                true,
                0.0);
        CombatSimulator insufficientSim = simulatorWith(insufficientOwner);
        insufficientOwner.restoreCurrentEnergy(59.999);
        performBurst(insufficientSim, insufficientOwner);
        assertTrue(!insufficientWeapon.isBurstWindowActive(0.0),
                "Insufficient-Energy Burst request cannot open the window");

        BloodsoakedRuins offFieldWeapon = new BloodsoakedRuins(1);
        TestCharacter offFieldOwner = character(
                CharacterId.YUN_JIN,
                Element.ELECTRO,
                offFieldWeapon,
                true,
                0.0);
        TestCharacter activeAlly = character(
                CharacterId.AMBER, Element.PYRO, null, false, 0.0);
        CombatSimulator offFieldSim = simulatorWith(offFieldOwner, activeAlly);
        offFieldSim.setActiveCharacter(activeAlly.getCharacterId());
        performBurst(offFieldSim, offFieldOwner);
        assertTrue(!offFieldWeapon.isBurstWindowActive(0.0),
                "Off-field owner Burst cannot open the weapon window");
    }

    private static void testDirectLunarDamageRouting() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction charged = lunarHit(
                "Typed Lunar-Charged",
                AttackAction.LunarReactionType.CHARGED);
        AttackAction legacy = legacyLunarHit("Legacy Lunar-Charged");
        AttackAction bloom = lunarHit(
                "Lunar-Bloom isolation",
                AttackAction.LunarReactionType.BLOOM);
        AttackAction crystallize = lunarHit(
                "Lunar-Crystallize isolation",
                AttackAction.LunarReactionType.CRYSTALLIZE);
        AttackAction standard = standardHit("Standard isolation");

        double chargedBaseline = calculate(owner, charged, sim);
        double legacyBaseline = calculate(owner, legacy, sim);
        double bloomBaseline = calculate(owner, bloom, sim);
        double crystallizeBaseline = calculate(owner, crystallize, sim);
        double standardBaseline = calculate(owner, standard, sim);

        performBurst(sim, owner);
        assertClose(chargedBaseline * 1.36,
                calculate(owner, charged, sim),
                "Burst bonus applies to typed direct Lunar-Charged damage");
        assertClose(legacyBaseline * 1.36,
                calculate(owner, legacy, sim),
                "Burst bonus applies to legacy null-subtype Lunar damage");
        assertClose(bloomBaseline, calculate(owner, bloom, sim),
                "Burst bonus excludes Lunar-Bloom direct damage");
        assertClose(crystallizeBaseline, calculate(owner, crystallize, sim),
                "Burst bonus excludes Lunar-Crystallize direct damage");
        assertClose(standardBaseline, calculate(owner, standard, sim),
                "Burst bonus excludes standard damage");
    }

    private static void testWeightedAndPeriodicOwnerContribution() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null, false, 0.0);
        CombatSimulator sim = simulatorWith(owner, ally);
        ReactionEffectScheduler scheduler = new ReactionEffectScheduler(sim);
        double inactiveWeighted = scheduler.computeInitialLunarChargedDamage();

        BloodsoakedRuins ownerOnlyWeapon = new BloodsoakedRuins(1);
        TestCharacter ownerOnly = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                ownerOnlyWeapon,
                true,
                0.0);
        CombatSimulator ownerOnlySim = simulatorWith(ownerOnly);
        double inactiveOwnerContribution = new ReactionEffectScheduler(
                ownerOnlySim).computeInitialLunarChargedDamage();

        performBurst(sim, owner);
        double activeWeighted = scheduler.computeInitialLunarChargedDamage();
        assertClose(inactiveOwnerContribution * 0.36,
                activeWeighted - inactiveWeighted,
                "Weighted Lunar-Charged damage includes the owner's Burst bonus");

        owner.restoreCurrentEnergy(0.0);
        double beforeReaction = sim.getTotalDamage();
        triggerActualLunarCharged(sim, owner);
        double expectedInitial = scheduler.computeInitialLunarChargedDamage();
        assertClose(expectedInitial,
                sim.getTotalDamage() - beforeReaction,
                "Triggering Lunar-Charged uses the newly opened CRIT window");

        double expectedPeriodic = scheduler.computeInitialLunarChargedDamage();
        double beforePeriodic = sim.getTotalDamage();
        sim.advanceTime(2.0);
        assertClose(expectedPeriodic,
                sim.getTotalDamage() - beforePeriodic,
                "Periodic Lunar-Charged tick includes the owner's live bonus");
    }

    private static void testActualReactionTriggerAndOrdering() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        double standardBaseline = calculate(
                owner, standardHit("Pre-trigger standard"), sim);
        double lunarBaseline = calculate(
                owner,
                lunarHit(
                        "Pre-trigger Lunar",
                        AttackAction.LunarReactionType.CHARGED),
                sim);

        boolean[] critActiveInObserver = {false};
        double[] energyInObserver = {-1.0};
        double[] critDamageInObserver = {-1.0};
        sim.addReactionListener((result, source, time, activeSim) -> {
            if (result.getKind() == ReactionResult.Kind.LUNAR_CHARGED) {
                critActiveInObserver[0] = weapon.isCritWindowActive(time);
                energyInObserver[0] = owner.getCurrentEnergy();
                critDamageInObserver[0] = owner.getEffectiveStats(time)
                        .get(StatType.CRIT_DMG);
            }
        });

        triggerActualLunarCharged(sim, owner);
        assertTrue(critActiveInObserver[0],
                "Weapon callback opens CRIT before reaction observers run");
        assertClose(12.0, energyInObserver[0],
                "Weapon restores Energy before reaction observers run");
        assertClose(1.28, critDamageInObserver[0],
                "Reaction observer sees the generic R1 CRIT DMG bonus");
        assertClose(12.0, owner.getCurrentEnergy(),
                "Actual on-field owner Lunar-Charged restores flat Energy");

        double expectedCritRatio = (1.0 + 0.221 * 1.28)
                / (1.0 + 0.221);
        assertClose(standardBaseline * expectedCritRatio,
                calculate(owner, standardHit("Post-trigger standard"), sim),
                "Generic CRIT DMG increases standard direct damage");
        assertClose(lunarBaseline * expectedCritRatio,
                calculate(
                        owner,
                        lunarHit(
                                "Post-trigger Lunar",
                                AttackAction.LunarReactionType.CHARGED),
                        sim),
                "Generic CRIT DMG increases direct Lunar damage");
    }

    private static void testReactionSourceAndDerivedExclusions() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null, true, 0.0);
        CombatSimulator sim = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        ReactionResult lunarCharged = ReactionResult.lunar(
                0.0, ReactionResult.LunarType.CHARGED);

        sim.notifyReaction(lunarCharged, ally);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Ally Lunar-Charged cannot trigger Bloodsoaked Ruins");

        sim.notifyReaction(ReactionResult.transform(
                0.0,
                "Electro-Charged",
                ReactionResult.Kind.ELECTRO_CHARGED), owner);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Standard Electro-Charged cannot trigger Bloodsoaked Ruins");

        sim.notifyReaction(ReactionResult.lunar(
                0.0, ReactionResult.LunarType.BLOOM), owner);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Lunar-Bloom cannot trigger Bloodsoaked Ruins");

        sim.notifyReaction(ReactionResult.lunar(
                0.0, ReactionResult.LunarType.CRYSTALLIZE), owner);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Lunar-Crystallize cannot trigger Bloodsoaked Ruins");

        sim.setActiveCharacter(ally.getCharacterId());
        sim.notifyReaction(lunarCharged, owner);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Off-field owner Lunar-Charged cannot trigger Bloodsoaked Ruins");

        sim.setActiveCharacter(owner.getCharacterId());
        sim.notifyDerivedReaction(lunarCharged, owner);
        sim.performActionWithoutTimeAdvance(
                owner.getCharacterId(),
                derivedLunarHit("Derived Lunar direct hit"));
        assertReactionPassiveInactive(weapon, owner, sim,
                "Derived Lunar notifications cannot grant CRIT or Energy");
    }

    private static void testCritRefreshAndExactExpiry() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);

        notifyActualLunarCharged(sim, owner);
        assertTrue(weapon.isCritWindowActive(360.0 * FRAME - 1e-9),
                "CRIT window remains active immediately before 360 frames");
        assertTrue(!weapon.isCritWindowActive(360.0 * FRAME),
                "CRIT window expires at the exact 360-frame boundary");

        sim.advanceTime(5.0);
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "CRIT refresh inside Energy ICD does not restore Energy");
        assertTrue(weapon.isCritWindowActive(10.999999),
                "CRIT refresh replaces the previous expiry");
        assertTrue(!weapon.isCritWindowActive(11.0),
                "Refreshed CRIT window keeps a half-open expiry");
    }

    private static void testEnergyCooldownBoundaries() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);

        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "First accepted Lunar-Charged restores R1 Energy");
        sim.advanceTime(840.0 * FRAME - 0.001);
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Energy remains blocked immediately before 840 frames");
        sim.advanceTime(0.001);
        notifyActualLunarCharged(sim, owner);
        assertClose(24.0, owner.getCurrentEnergy(),
                "Energy reopens at the exact 840-frame boundary");

        sim.advanceTime(840.0 * FRAME);
        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        notifyActualLunarCharged(sim, owner);
        assertClose(owner.getMaxEnergy(), owner.getCurrentEnergy(),
                "Full Energy clamps the accepted recovery");
        owner.spendEnergy(owner.getMaxEnergy());
        notifyActualLunarCharged(sim, owner);
        assertClose(0.0, owner.getCurrentEnergy(),
                "A full-Energy trigger still consumes the Energy gate");
        sim.advanceTime(840.0 * FRAME - 0.001);
        notifyActualLunarCharged(sim, owner);
        assertClose(0.0, owner.getCurrentEnergy(),
                "Consumed full-Energy gate remains closed before its boundary");
        sim.advanceTime(0.001);
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Consumed full-Energy gate reopens at its exact boundary");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        SimulatorSnapshot preTrigger = sim.saveSnapshot();

        owner.restoreCurrentEnergy(owner.getMaxEnergy());
        performBurst(sim, owner);
        SimulatorSnapshot burstOnly = sim.saveSnapshot();
        notifyActualLunarCharged(sim, owner);
        SimulatorSnapshot bothActive = sim.saveSnapshot();
        sim.advanceTime(4.0);
        SimulatorSnapshot critOnly = sim.saveSnapshot();

        sim.restoreSnapshot(burstOnly);
        assertTrue(weapon.isBurstWindowActive(sim.getCurrentTime()),
                "Burst-only snapshot restores the Burst window");
        assertTrue(!weapon.isCritWindowActive(sim.getCurrentTime()),
                "Burst-only snapshot clears the later CRIT window");
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Burst-only snapshot independently restores Energy ICD readiness");

        sim.restoreSnapshot(bothActive);
        assertTrue(weapon.isBurstWindowActive(sim.getCurrentTime()),
                "Combined snapshot restores the Burst window");
        assertTrue(weapon.isCritWindowActive(sim.getCurrentTime()),
                "Combined snapshot restores the CRIT window");
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Combined snapshot restores the closed Energy ICD");

        sim.restoreSnapshot(critOnly);
        assertTrue(!weapon.isBurstWindowActive(sim.getCurrentTime()),
                "CRIT-only snapshot keeps the Burst window expired");
        assertTrue(weapon.isCritWindowActive(sim.getCurrentTime()),
                "CRIT-only snapshot restores the independent CRIT window");

        sim.restoreSnapshot(preTrigger);
        assertReactionPassiveInactive(weapon, owner, sim,
                "Pre-trigger snapshot clears both weapon windows");
        notifyActualLunarCharged(sim, owner);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Pre-trigger snapshot clears the Energy ICD");
        assertTrue(!weapon.isBurstWindowActive(sim.getCurrentTime()),
                "Reaction after pre-trigger restore does not invent a Burst window");

        BloodsoakedRuins first = new BloodsoakedRuins(1);
        BloodsoakedRuins second = new BloodsoakedRuins(5);
        TestCharacter firstOwner = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                first,
                true,
                0.0);
        TestCharacter secondOwner = character(
                CharacterId.BENNETT,
                Element.PYRO,
                second,
                true,
                0.0);
        CombatSimulator firstSim = simulatorWith(firstOwner);
        CombatSimulator secondSim = simulatorWith(secondOwner);
        firstOwner.restoreCurrentEnergy(0.0);
        secondOwner.restoreCurrentEnergy(0.0);
        notifyActualLunarCharged(firstSim, firstOwner);
        assertTrue(first.isCritWindowActive(0.0),
                "First weapon instance owns its CRIT window");
        assertTrue(!second.isCritWindowActive(0.0),
                "Second weapon instance does not share CRIT state");
        assertClose(0.0, secondOwner.getCurrentEnergy(),
                "Second weapon instance does not share Energy recovery");
    }

    private static void testBindingAndStateGuards() {
        BloodsoakedRuins weapon = new BloodsoakedRuins(1);
        TestCharacter owner = character(
                CharacterId.YUN_JIN, Element.ELECTRO, weapon, true, 0.0);
        CombatSimulator sim = simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        weapon.initializeForSimulator(owner, sim);

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> new BloodsoakedRuins(1).restoreWeaponState(state),
                "Bloodsoaked Ruins rejects foreign instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Bloodsoaked Ruins rejects the wrong state type");

        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Bloodsoaked Ruins rejects cross-simulator reuse");
        TestCharacter otherOwner = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                weapon,
                true,
                0.0);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(otherOwner, sim),
                "Bloodsoaked Ruins rejects cross-owner reuse");

        BloodsoakedRuins nullWeapon = new BloodsoakedRuins(1);
        TestCharacter nullOwner = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                nullWeapon,
                true,
                0.0);
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(null, sim),
                "Bloodsoaked Ruins rejects null owner");
        assertThrows(IllegalArgumentException.class,
                () -> nullWeapon.initializeForSimulator(nullOwner, null),
                "Bloodsoaked Ruins rejects null simulator");

        BloodsoakedRuins unequipped = new BloodsoakedRuins(1);
        TestCharacter wrongOwner = character(
                CharacterId.BENNETT, Element.PYRO, null, false, 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(wrongOwner, sim),
                "Bloodsoaked Ruins rejects an unequipped owner");

        BloodsoakedRuins outside = new BloodsoakedRuins(1);
        TestCharacter outsideOwner = character(
                CharacterId.KUJOU_SARA,
                Element.ELECTRO,
                outside,
                true,
                0.0);
        assertThrows(IllegalArgumentException.class,
                () -> outside.initializeForSimulator(outsideOwner, sim),
                "Bloodsoaked Ruins rejects an owner outside the simulator party");
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon,
            boolean lunar,
            double burstCooldown) {
        return new TestCharacter(id, element, weapon, lunar, burstCooldown);
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static void performBurst(
            CombatSimulator sim,
            TestCharacter owner) {
        sim.performAction(
                owner.getCharacterId(),
                CharacterActionRequest.of(CharacterActionKey.BURST));
    }

    private static void triggerActualLunarCharged(
            CombatSimulator sim,
            TestCharacter source) {
        sim.getEnemy().setAura(Element.HYDRO, 1.0);
        sim.performActionWithoutTimeAdvance(
                source.getCharacterId(),
                reactionHit("Actual Lunar-Charged trigger"));
    }

    private static void notifyActualLunarCharged(
            CombatSimulator sim,
            TestCharacter source) {
        sim.notifyReaction(
                ReactionResult.lunar(
                        0.0, ReactionResult.LunarType.CHARGED),
                source);
    }

    private static AttackAction reactionHit(String name) {
        AttackAction action = new AttackAction(
                name,
                0.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
        action.setICD(ICDType.None, ICDTag.None, 1.0);
        return action;
    }

    private static AttackAction lunarHit(
            String name,
            AttackAction.LunarReactionType type) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
        action.setLunarReactionType(type);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static AttackAction legacyLunarHit(String name) {
        AttackAction action = lunarHit(
                name, AttackAction.LunarReactionType.CHARGED);
        action.setLunarReactionType(null);
        action.setLunarConsidered(true);
        return action;
    }

    private static AttackAction derivedLunarHit(String name) {
        return lunarHit(name, AttackAction.LunarReactionType.CHARGED);
    }

    private static AttackAction standardHit(String name) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.OTHER);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        return action;
    }

    private static double calculate(
            Character owner,
            AttackAction action,
            CombatSimulator sim) {
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                List.of(),
                sim.getCurrentTime(),
                1.0,
                sim);
    }

    private static void assertReactionPassiveInactive(
            BloodsoakedRuins weapon,
            TestCharacter owner,
            CombatSimulator sim,
            String message) {
        assertTrue(!weapon.isCritWindowActive(sim.getCurrentTime()),
                message + " (CRIT window)");
        assertClose(0.0, owner.getCurrentEnergy(),
                message + " (Energy)");
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
        if (expected == null ? actual != null : !expected.equals(actual)) {
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
        } catch (Throwable thrown) {
            if (expected.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    /** Minimal Burst-capable character with optional Lunar conversion. */
    private static final class TestCharacter extends Character {
        private final boolean lunar;

        private TestCharacter(
                CharacterId id,
                Element characterElement,
                Weapon equippedWeapon,
                boolean lunar,
                double burstCooldown) {
            name = id.getDisplayName();
            characterId = id;
            element = characterElement;
            weapon = equippedWeapon;
            this.lunar = lunar;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
            baseStats.set(StatType.CRIT_DMG, 1.0);
            setBurstCD(burstCooldown);
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

        @Override
        public void onAction(
                CharacterActionRequest request,
                CombatSimulator sim) {
            if (request.getKey() == CharacterActionKey.BURST) {
                markBurstUsed(sim.getCurrentTime());
            }
        }
    }

    /** Foreign marker used to verify state-type rejection. */
    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}
