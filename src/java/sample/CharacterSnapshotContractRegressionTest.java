package sample;

import model.entity.Character;
import model.entity.SnapshotAwareCharacterEffect;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.event.SimpleTimerEvent;

/** Focused checks for opt-in character state capture and reconstruction. */
public final class CharacterSnapshotContractRegressionTest {
    private CharacterSnapshotContractRegressionTest() {
    }

    /** Runs normal, repeated-restore, and wrong-state checks. */
    public static void main(String[] args) {
        TestCharacter character = new TestCharacter();
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.addCharacter(character);
        character.counter = 7;
        character.eventTime = 2.0;
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        character.counter = 9;
        sim.advanceTime(3.0);
        sim.restoreSnapshot(snapshot);
        assertEquals(7, character.counter, "character counter restore");
        sim.advanceTime(2.0);
        assertEquals(1, character.eventCount, "future event reconstruction");

        sim.restoreSnapshot(snapshot);
        sim.restoreSnapshot(snapshot);
        sim.advanceTime(2.0);
        assertEquals(2, character.eventCount,
                "repeated restore leaves one reconstructed event");

        SimulatorSnapshot.CharacterSnapshot saved =
                snapshot.characters.get(CharacterId.NOELLE);
        snapshot.characters.put(
                CharacterId.NOELLE,
                new SimulatorSnapshot.CharacterSnapshot(
                        saved.currentEnergy,
                        saved.lastSkillTime,
                        saved.lastBurstTime,
                        saved.skillCooldownEndTime,
                        saved.burstCooldownEndTime,
                        saved.activeChargeCooldownDuration,
                        saved.chargeRestoreTimes,
                        saved.activeBuffRefs,
                        saved.activeBuffTimes,
                        saved.weaponState,
                        new WrongState()));
        assertThrows(IllegalArgumentException.class,
                () -> sim.restoreSnapshot(snapshot),
                "wrong character state type");
        System.out.println("CharacterSnapshotContractRegressionTest passed");
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
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

    private static final class TestState
            implements SnapshotAwareCharacterEffect.State {
        private final int counter;
        private final double eventTime;

        private TestState(int counter, double eventTime) {
            this.counter = counter;
            this.eventTime = eventTime;
        }
    }

    private static final class WrongState
            implements SnapshotAwareCharacterEffect.State {
    }

    private static final class TestCharacter extends Character
            implements SnapshotAwareCharacterEffect {
        private int counter;
        private int eventCount;
        private double eventTime;

        private TestCharacter() {
            name = "Noelle";
            characterId = CharacterId.NOELLE;
            element = Element.GEO;
        }

        @Override
        public State captureCharacterState() {
            return new TestState(counter, eventTime);
        }

        @Override
        public void restoreCharacterState(
                State state,
                CombatSimulator simulator) {
            if (!(state instanceof TestState)) {
                throw new IllegalArgumentException(
                        "Unexpected test character state");
            }
            TestState restored = (TestState) state;
            counter = restored.counter;
            eventTime = restored.eventTime;
            if (eventTime <= simulator.getCurrentTime()) {
                return;
            }
            simulator.registerEvent(new SimpleTimerEvent(eventTime, 1.0) {
                @Override
                public void onTick(CombatSimulator activeSim) {
                    finish();
                    eventCount++;
                }
            });
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }
}
