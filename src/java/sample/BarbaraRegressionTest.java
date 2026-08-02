package sample;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.character.Barbara;
import model.character.Xingqiu;
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
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Focused regression executable for Barbara's offensive vertical slice.
 */
public final class BarbaraRegressionTest {
    private static final double EPS = 1e-9;

    private BarbaraRegressionTest() {
    }

    /**
     * Runs Barbara identity, action, skill, burst, passive, and constellation checks.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        testIdentityAndConfiguredStats();
        testNormalChargedAndPlungeActions();
        testSkillDropletsAndSharedIcd();
        testBurstHasNoOffensiveDamage();
        testC1EnergyBoundary();
        testC2AndEncoreFieldBonus();
        testC4ChargedEnergyAndC5SkillLevel();
        testMelodyLoopSnapshotRestore();
        System.out.println("Barbara regression checks passed.");
    }

    private static void testIdentityAndConfiguredStats() {
        Barbara barbara = new Barbara(null, null);
        assertEquals(CharacterId.BARBARA, barbara.getCharacterId(),
                "Barbara typed identity");
        assertEquals(CharacterId.BARBARA, CharacterId.fromName("Barbara"),
                "Barbara display-name lookup");
        assertEquals(CharacterId.BARBARA, CharacterId.fromNumericId(12),
                "Barbara numeric-id lookup");
        assertClose(9787.0, barbara.getBaseStats().get(StatType.BASE_HP), EPS,
                "Barbara level-90 base HP");
        assertClose(159.0, barbara.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Barbara level-90 base ATK");
        assertClose(669.0, barbara.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Barbara level-90 base DEF");
        assertClose(0.24, barbara.getBaseStats().get(StatType.HP_PERCENT), EPS,
                "Barbara ascension HP");
        assertClose(80.0, barbara.getEnergyCost(), EPS,
                "Barbara Burst Energy cost");
        assertClose(27.2, barbara.getSkillCD(), EPS,
                "Barbara C2 Skill cooldown");
        assertClose(20.0, barbara.getBurstCD(), EPS,
                "Barbara Burst cooldown");

        TalentDataManager talentData = TalentDataManager.getInstance();
        assertClose(0.64328, talentData.get("Barbara", "N1", -1.0), EPS,
                "Barbara configured N1 multiplier");
        assertClose(2.82608,
                talentData.get("Barbara", "Charged Attack", -1.0), EPS,
                "Barbara configured Charged multiplier");
        assertClose(1.044064,
                talentData.get("Barbara", "Plunge Drop", -1.0), EPS,
                "Barbara configured Plunge drop multiplier");
        assertClose(2.087686,
                talentData.get("Barbara", "Plunge Low", -1.0), EPS,
                "Barbara configured low Plunge multiplier");
        assertClose(2.607632,
                talentData.get("Barbara", "Plunge High", -1.0), EPS,
                "Barbara configured high Plunge multiplier");
        assertClose(1.1680,
                talentData.get("Barbara", "Skill Droplet", -1.0), EPS,
                "Barbara configured C5 Skill multiplier");
    }

    private static void testNormalChargedAndPlungeActions() {
        Barbara barbara = barbaraAtConstellation(0);
        CombatSimulator sim = simulatorWith(barbara);
        List<AttackAction> actions = captureBarbaraDamageActions(sim);

        for (int i = 0; i < 4; i++) {
            sim.performAction(
                    CharacterId.BARBARA,
                    CharacterActionRequest.of(CharacterActionKey.NORMAL));
        }

        double[] normalMultipliers = { 0.64328, 0.60384, 0.69768, 0.93840 };
        double[] normalDurations = {
                15.0 / 60.0,
                21.0 / 60.0,
                22.0 / 60.0,
                60.0 / 60.0
        };
        assertEquals(4, actions.size(), "Barbara four-hit Normal action count");
        for (int i = 0; i < 4; i++) {
            AttackAction action = actions.get(i);
            assertClose(normalMultipliers[i], action.getDamagePercent(), EPS,
                    "Barbara N" + (i + 1) + " multiplier");
            assertClose(normalDurations[i], action.getAnimationDuration(), EPS,
                    "Barbara N" + (i + 1) + " action interval");
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Barbara Normal action type");
            assertEquals(Element.HYDRO, action.getElement(),
                    "Barbara Normal element");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Barbara Normal ICD type");
            assertEquals(ICDTag.NormalAttack, action.getICDTag(),
                    "Barbara Normal ICD tag");
            assertClose(1.0, action.getGaugeUnits(), EPS,
                    "Barbara Normal gauge");
        }

        actions.clear();
        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.CHARGE));
        AttackAction charged = actions.get(0);
        assertClose(2.82608, charged.getDamagePercent(), EPS,
                "Barbara Charged multiplier");
        assertClose(89.0 / 60.0, charged.getAnimationDuration(), EPS,
                "Barbara Charged interval");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Barbara Charged action type");
        assertEquals(ICDType.None, charged.getICDType(),
                "Barbara sourced 0.5-second Charged ICD representation");
        assertEquals(ICDTag.ChargedAttack, charged.getICDTag(),
                "Barbara Charged ICD tag");

        actions.clear();
        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.PLUNGE));
        AttackAction plunge = actions.get(0);
        assertClose(2.607632, plunge.getDamagePercent(), EPS,
                "Barbara high Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Barbara Plunge action type");
        assertEquals(ICDTag.PlungeAttack, plunge.getICDTag(),
                "Barbara Plunge ICD tag");
    }

    private static void testSkillDropletsAndSharedIcd() {
        Barbara barbara = barbaraAtConstellation(0);
        CombatSimulator sim = simulatorWith(barbara);
        barbara.spendEnergy(80.0);
        List<AttackAction> actions = captureNamedDamageActions(
                sim,
                "Let the Show Begin Droplet");

        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(1, actions.size(),
                "First Barbara Skill droplet should resolve during cast");
        sim.advanceTime(0.1);
        assertEquals(2, actions.size(),
                "Barbara Skill should resolve exactly two droplets");
        for (AttackAction action : actions) {
            assertClose(0.9928, action.getDamagePercent(), EPS,
                    "C0 Barbara Skill droplet multiplier");
            assertEquals(ActionType.SKILL, action.getActionType(),
                    "Barbara Skill action type");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Barbara Skill ICD type");
            assertEquals(ICDTag.ElementalSkill, action.getICDTag(),
                    "Barbara Skill ICD tag");
            assertClose(1.0, action.getGaugeUnits(), EPS,
                    "Barbara Skill gauge");
        }
        double[] icdState = sim.getIcdManager().saveStates()
                .get("BARBARA_ElementalSkill");
        assertTrue(icdState != null,
                "Barbara Skill should create a typed ICD state");
        assertClose(0.5, icdState[0], EPS,
                "Barbara first Skill droplet application time");
        assertClose(2.0, icdState[1], EPS,
                "Barbara second Skill droplet should be ICD-blocked");
        assertClose(0.0, barbara.getCurrentEnergy(), EPS,
                "Barbara Skill should generate no particles");
    }

    private static void testBurstHasNoOffensiveDamage() {
        Barbara barbara = barbaraAtConstellation(0);
        CombatSimulator sim = simulatorWith(barbara);
        List<AttackAction> actions = captureNamedDamageActions(
                sim,
                "Shining Miracle Cast");

        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.BURST));
        assertEquals(1, actions.size(),
                "Barbara Burst should emit one cast action");
        assertClose(0.0, actions.get(0).getDamagePercent(), EPS,
                "Barbara Burst offensive multiplier");
        assertClose(0.0, sim.getTotalDamage(), EPS,
                "Barbara Burst should deal no damage");
        assertClose(0.0, barbara.getCurrentEnergy(), EPS,
                "Barbara Burst should spend 80 Energy");
        assertClose(141.0 / 60.0, sim.getCurrentTime(), EPS,
                "Barbara Burst action interval");
    }

    private static void testC1EnergyBoundary() {
        Barbara c0 = barbaraAtConstellation(0);
        CombatSimulator c0Sim = simulatorWith(c0);
        c0.spendEnergy(80.0);
        c0Sim.advanceTime(10.0);
        assertClose(0.0, c0.getCurrentEnergy(), EPS,
                "C0 Barbara should not regenerate periodic Energy");

        Barbara c1 = barbaraAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        c1.spendEnergy(80.0);
        c1Sim.advanceTime(9.999);
        assertClose(0.0, c1.getCurrentEnergy(), EPS,
                "Barbara C1 should not trigger before ten seconds");
        c1Sim.advanceTime(0.001);
        assertClose(1.0, c1.getCurrentEnergy(), EPS,
                "Barbara C1 should trigger at ten seconds");
        c1Sim.advanceTime(10.0);
        assertClose(2.0, c1.getCurrentEnergy(), EPS,
                "Barbara C1 should repeat every ten seconds");
    }

    private static void testC2AndEncoreFieldBonus() {
        Barbara c1 = barbaraAtConstellation(1);
        CombatSimulator c1Sim = simulatorWith(c1);
        c1Sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertClose(32.0, c1.getSkillCD(), EPS,
                "Barbara C1 should retain the base Skill cooldown");
        assertClose(0.0, resolvedStat(
                c1Sim,
                c1,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara C1 should not grant the C2 Hydro bonus");

        Barbara barbara = barbaraAtConstellation(2);
        Xingqiu xingqiu = new Xingqiu(null, null);
        CombatSimulator sim = simulatorWith(barbara);
        sim.addCharacter(xingqiu);
        sim.setActiveCharacter(CharacterId.XINGQIU);
        double xingqiuBaseline = resolvedStat(
                sim,
                xingqiu,
                StatType.HYDRO_DMG_BONUS);
        sim.setActiveCharacter(CharacterId.BARBARA);

        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertTrue(barbara.isFormActive(sim.getCurrentTime()),
                "Barbara Melody Loop should be active after Skill");
        assertClose(0.15, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara C2 active-character Hydro bonus");
        sim.setActiveCharacter(CharacterId.XINGQIU);
        assertClose(xingqiuBaseline + 0.15, resolvedStat(
                sim,
                xingqiu,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara C2 should follow the active character");
        assertClose(0.0, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara C2 should not buff off-field Barbara");

        sim.getEnergyDistributor().distributeParticles(
                Element.PYRO,
                10.0,
                ParticleType.PARTICLE);
        sim.setActiveCharacter(CharacterId.BARBARA);
        sim.advanceTime(20.0 - sim.getCurrentTime() - 0.001);
        assertClose(0.15, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara Encore should cap Melody Loop at twenty seconds");
        sim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Barbara C2 should expire at the exact extended boundary");
        assertTrue(!barbara.isFormActive(sim.getCurrentTime()),
                "Barbara Melody Loop should end at the extended boundary");
    }

    private static void testC4ChargedEnergyAndC5SkillLevel() {
        Barbara c3 = barbaraAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        c3.spendEnergy(80.0);
        c3Sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertClose(0.0, c3.getCurrentEnergy(), EPS,
                "Barbara C3 should not grant Charged Attack Energy");

        Barbara c4 = barbaraAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        c4.spendEnergy(80.0);
        c4Sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.CHARGE));
        assertClose(1.0, c4.getCurrentEnergy(), EPS,
                "Barbara C4 single-target Charged Energy");

        Barbara c5 = new Barbara(null, null);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<AttackAction> c5Droplets = captureNamedDamageActions(
                c5Sim,
                "Let the Show Begin Droplet");
        c5Sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        assertEquals(1, c5Droplets.size(),
                "Barbara C5 first droplet capture");
        assertClose(1.1680, c5Droplets.get(0).getDamagePercent(), EPS,
                "Barbara C5 talent-12 Skill multiplier");
    }

    private static void testMelodyLoopSnapshotRestore() {
        Barbara barbara = barbaraAtConstellation(2);
        CombatSimulator sim = simulatorWith(barbara);
        sim.performAction(
                CharacterId.BARBARA,
                CharacterActionRequest.of(CharacterActionKey.SKILL));
        sim.getEnergyDistributor().distributeParticles(
                Element.PHYSICAL,
                2.0,
                ParticleType.PARTICLE);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.getEnergyDistributor().distributeParticles(
                Element.PHYSICAL,
                3.0,
                ParticleType.PARTICLE);
        sim.advanceTime(17.5 - sim.getCurrentTime());
        assertClose(0.15, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Mutated Barbara Melody Loop should last twenty seconds");

        sim.restoreSnapshot(snapshot);
        sim.advanceTime(17.0 - sim.getCurrentTime() - 0.001);
        assertClose(0.15, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Restored Barbara Melody Loop should retain two-second extension");
        sim.advanceTime(0.001);
        assertClose(0.0, resolvedStat(
                sim,
                barbara,
                StatType.HYDRO_DMG_BONUS), EPS,
                "Restored Barbara Melody Loop exact expiry");
    }

    private static Barbara barbaraAtConstellation(int constellation) {
        TalentDataSource talentData = (character, key, defaultValue) ->
                "Constellation".equals(key) ? constellation : defaultValue;
        return new Barbara(null, null, talentData);
    }

    private static CombatSimulator simulatorWith(Barbara barbara) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(barbara);
        return sim;
    }

    private static List<AttackAction> captureBarbaraDamageActions(
            CombatSimulator sim) {
        List<AttackAction> actions = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.BARBARA) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static List<AttackAction> captureNamedDamageActions(
            CombatSimulator sim,
            String actionName) {
        List<AttackAction> actions = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.BARBARA
                    && actionName.equals(action.getName())) {
                actions.add(action);
            }
        });
        return actions;
    }

    private static double resolvedStat(
            CombatSimulator sim,
            Character character,
            StatType statType) {
        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(character)) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats.get(statType);
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
}
