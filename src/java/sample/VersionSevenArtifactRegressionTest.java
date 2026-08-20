package sample;

import java.util.List;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.artifact.HeartOfTheFurnace;
import model.artifact.ScarletProof;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;

/** Regression checks for Version 7.0 artifact sets. */
public final class VersionSevenArtifactRegressionTest {
    private static final double EPS = 1e-6;

    private VersionSevenArtifactRegressionTest() {
    }

    /** Runs all Version 7.0 artifact regression cases. */
    public static void main(String[] args) {
        testTwoPieceBonuses();
        testScarletProofOwnerTriggerAndExpiry();
        testScarletProofRejectsNonOwnerAndConduct();
        testHeartOwnerAndTeamWindows();
        testHeartTeamWindowDoesNotStack();
        testHeartSnapshotRestore();
        System.out.println("VersionSevenArtifactRegressionTest passed");
    }

    private static void testTwoPieceBonuses() {
        assertEquals(0.18, new ScarletProof().getStats().get(StatType.ATK_PERCENT),
                EPS, "Scarlet Proof two-piece ATK");
        assertEquals(0.18, new HeartOfTheFurnace().getStats().get(StatType.ATK_PERCENT),
                EPS, "Heart of the Furnace two-piece ATK");
    }

    private static void testScarletProofOwnerTriggerAndExpiry() {
        TestCharacter owner = character(CharacterId.ODETTE, new ScarletProof());
        CombatSimulator sim = simulator(owner);
        sim.notifyDerivedReaction(stellarSwirl(), owner);

        StatsContainer active = effectiveStats(sim, owner);
        assertEquals(0.16, active.get(StatType.CRIT_RATE) - 0.05, EPS,
                "Scarlet Proof grants CRIT Rate");
        assertEquals(0.40, active.get(StatType.STELLAR_SWIRL_DMG_BONUS), EPS,
                "Scarlet Proof grants Stellar-Swirl damage");

        sim.advanceTime(9.0);
        sim.notifyDerivedReaction(stellarSwirl(), owner);
        sim.advanceTime(9.9);
        assertEquals(0.40,
                effectiveStats(sim, owner).get(StatType.STELLAR_SWIRL_DMG_BONUS),
                EPS, "Scarlet Proof refreshes its window");
        sim.advanceTime(0.1);
        assertEquals(0.0,
                effectiveStats(sim, owner).get(StatType.STELLAR_SWIRL_DMG_BONUS),
                EPS, "Scarlet Proof uses a half-open ten-second window");
    }

    private static void testScarletProofRejectsNonOwnerAndConduct() {
        TestCharacter owner = character(CharacterId.ODETTE, new ScarletProof());
        TestCharacter ally = character(CharacterId.ALYOSHA, null);
        CombatSimulator sim = simulator(owner, ally);
        sim.notifyDerivedReaction(stellarSwirl(), ally);
        sim.notifyDerivedReaction(stellarConduct(), owner);

        assertEquals(0.0,
                effectiveStats(sim, owner).get(StatType.STELLAR_SWIRL_DMG_BONUS),
                EPS, "Scarlet Proof rejects non-owner and Conduct events");
    }

    private static void testHeartOwnerAndTeamWindows() {
        TestCharacter owner = character(CharacterId.ALYOSHA, new HeartOfTheFurnace());
        TestCharacter ally = character(CharacterId.ODETTE, null);
        CombatSimulator sim = simulator(ally, owner);
        sim.notifyDerivedReaction(stellarConduct(), owner);

        StatsContainer ownerStats = effectiveStats(sim, owner);
        StatsContainer allyStats = effectiveStats(sim, ally);
        assertEquals(0.30, ownerStats.get(StatType.ATK_PERCENT), EPS,
                "Heart combines two-piece and owner four-piece ATK");
        assertEquals(0.50, allyStats.get(StatType.STELLAR_CONDUCT_DMG_BONUS), EPS,
                "Heart grants team Stellar-Conduct damage");
        assertEquals(0.50, allyStats.get(StatType.STELLAR_SWIRL_DMG_BONUS), EPS,
                "Heart grants team Stellar-Swirl damage");

        sim.advanceTime(12.0);
        assertEquals(0.18, effectiveStats(sim, owner).get(StatType.ATK_PERCENT), EPS,
                "Heart owner window expires at twelve seconds");
        assertEquals(0.0,
                effectiveStats(sim, ally).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                EPS, "Heart team window expires at twelve seconds");
    }

    private static void testHeartTeamWindowDoesNotStack() {
        TestCharacter first = character(CharacterId.ALYOSHA, new HeartOfTheFurnace());
        TestCharacter second = character(CharacterId.ODETTE, new HeartOfTheFurnace());
        TestCharacter ally = character(CharacterId.SUCROSE, null);
        CombatSimulator sim = simulator(first, second, ally);
        sim.notifyDerivedReaction(stellarConduct(), first);
        sim.notifyDerivedReaction(stellarSwirl(), second);

        assertEquals(0.50,
                effectiveStats(sim, ally).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                EPS, "multiple Heart team effects replace instead of stacking");
    }

    private static void testHeartSnapshotRestore() {
        TestCharacter owner = character(CharacterId.ALYOSHA, new HeartOfTheFurnace());
        TestCharacter ally = character(CharacterId.ODETTE, null);
        CombatSimulator sim = simulator(owner, ally);
        sim.notifyDerivedReaction(stellarConduct(), owner);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        sim.advanceTime(12.0);
        assertEquals(0.0,
                effectiveStats(sim, ally).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                EPS, "Heart expires before snapshot restore");
        sim.restoreSnapshot(snapshot);
        assertEquals(0.50,
                effectiveStats(sim, ally).get(StatType.STELLAR_CONDUCT_DMG_BONUS),
                EPS, "snapshot restores Heart team window");
    }

    private static ReactionResult stellarConduct() {
        return ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_CONDUCT,
                Element.CRYO,
                Element.CRYO,
                true);
    }

    private static ReactionResult stellarSwirl() {
        return ReactionResult.stellar(
                0.0,
                ReactionResult.Kind.STELLAR_SWIRL,
                Element.CRYO,
                Element.ANEMO,
                false);
    }

    private static TestCharacter character(CharacterId id, ArtifactSet artifact) {
        TestCharacter character = new TestCharacter(id);
        if (artifact != null) {
            character.setArtifacts(artifact);
        }
        return character;
    }

    private static CombatSimulator simulator(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static StatsContainer effectiveStats(
            CombatSimulator sim, Character character) {
        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        List<Buff> buffs = sim.getApplicableBuffs(character);
        for (Buff buff : buffs) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }
        return stats;
    }

    private static void assertEquals(
            double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Minimal artifact owner fixture. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id) {
            characterId = id;
            name = id.getDisplayName();
            element = Element.CRYO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = new ArtifactSet[0];
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}
