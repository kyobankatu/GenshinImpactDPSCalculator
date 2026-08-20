package simulation.runtime;

import java.util.EnumMap;
import java.util.Map;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;

/**
 * Owns Polestar Field, its four-second application windows, and Stellar Radiance.
 *
 * <p>The simulator is fixed-target and has no position model, so every party member
 * and the configured enemy are considered inside an active Polestar Field.</p>
 */
public class StellarReactionManager {
    private static final double FIELD_DURATION = 6.0;
    private static final double WINDOW_DURATION = 4.0;
    private static final double APPLICATION_RECORD_ICD = 0.1;
    private static final double STELLAR_SWIRL_RADIANCE_DURATION = 8.0;
    private static final int MAX_RECORDED_APPLICATIONS = 12;
    private static final double[] ELEMENT_DAMAGE_BONUS = {
        0.20, 0.29, 0.30, 0.31, 0.32, 0.33, 0.34,
        0.35, 0.36, 0.37, 0.38, 0.39, 0.40
    };
    private static final double[] CONDUCT_MULTIPLIER = {
        1.00, 1.45, 1.50, 1.55, 1.60, 1.65, 1.70,
        1.75, 1.80, 1.85, 1.90, 1.95, 2.00
    };

    /** Immutable payload used by simulator snapshots. */
    public static final class State {
        private final double fieldEndTime;
        private final double nextWindowTime;
        private final int currentApplications;
        private final int recordedApplications;
        private final double stellarSwirlRadianceEndTime;
        private final Map<CharacterId, Double> nextRecordTimes;

        private State(
                double fieldEndTime,
                double nextWindowTime,
                int currentApplications,
                int recordedApplications,
                double stellarSwirlRadianceEndTime,
                Map<CharacterId, Double> nextRecordTimes) {
            this.fieldEndTime = fieldEndTime;
            this.nextWindowTime = nextWindowTime;
            this.currentApplications = currentApplications;
            this.recordedApplications = recordedApplications;
            this.stellarSwirlRadianceEndTime = stellarSwirlRadianceEndTime;
            this.nextRecordTimes = new EnumMap<>(CharacterId.class);
            this.nextRecordTimes.putAll(nextRecordTimes);
        }
    }

    private double fieldEndTime;
    private double nextWindowTime;
    private int currentApplications;
    private int recordedApplications;
    private double stellarSwirlRadianceEndTime;
    private final Map<CharacterId, Double> nextRecordTimes = new EnumMap<>(CharacterId.class);

    /** Creates or refreshes the six-second Polestar Field. */
    public void triggerStellarConduct(double currentTime) {
        advanceTo(currentTime);
        if (!isFieldActive(currentTime)) {
            currentApplications = 0;
            recordedApplications = 0;
            nextRecordTimes.clear();
            nextWindowTime = currentTime + WINDOW_DURATION;
        }
        fieldEndTime = currentTime + FIELD_DURATION;
    }

    /** Refreshes the eight-second Radiance: Stellar-Swirl state. */
    public void triggerStellarSwirl(double currentTime) {
        stellarSwirlRadianceEndTime = currentTime + STELLAR_SWIRL_RADIANCE_DURATION;
    }

    /** Records one successful Cryo or Electro application inside the field. */
    public void recordElementApplication(
            CharacterId source, Element element, double currentTime) {
        advanceTo(currentTime);
        if (!isFieldActive(currentTime)
                || (element != Element.CRYO && element != Element.ELECTRO)
                || recordedApplications >= MAX_RECORDED_APPLICATIONS) {
            return;
        }
        double nextRecordTime = nextRecordTimes.getOrDefault(source, 0.0);
        if (currentTime + 1e-9 < nextRecordTime) {
            return;
        }
        recordedApplications++;
        nextRecordTimes.put(source, currentTime + APPLICATION_RECORD_ICD);
    }

    /** Returns whether the Polestar Field is active at the supplied time. */
    public boolean isFieldActive(double currentTime) {
        return fieldEndTime > currentTime + 1e-9;
    }

    /** Returns whether Radiance: Stellar-Conduct is currently present. */
    public boolean hasStellarConductRadiance(double currentTime) {
        return isFieldActive(currentTime);
    }

    /** Returns whether Radiance: Stellar-Swirl is currently present. */
    public boolean hasStellarSwirlRadiance(double currentTime) {
        return stellarSwirlRadianceEndTime > currentTime + 1e-9;
    }

    /** Returns the released application count for the current four-second window. */
    public int getCurrentApplications(double currentTime) {
        advanceTo(currentTime);
        return isFieldActive(currentTime) ? currentApplications : 0;
    }

    /** Returns the applications still being recorded for the next release. */
    public int getRecordedApplications(double currentTime) {
        advanceTo(currentTime);
        return isFieldActive(currentTime) ? recordedApplications : 0;
    }

    /** Builds the dynamic all-inside field buff for the current time. */
    public Buff createFieldBuff(double currentTime) {
        advanceTo(currentTime);
        if (!isFieldActive(currentTime)) {
            return null;
        }
        int applications = currentApplications;
        double elementalBonus = ELEMENT_DAMAGE_BONUS[applications];
        double conductMultiplier = CONDUCT_MULTIPLIER[applications] - 1.0;
        return new SimpleBuff(
                "Polestar Field",
                Math.max(0.0, fieldEndTime - currentTime),
                currentTime,
                stats -> {
                    stats.add(StatType.CRYO_DMG_BONUS, elementalBonus);
                    stats.add(StatType.ELECTRO_DMG_BONUS, elementalBonus);
                    stats.add(StatType.PHYS_RES_SHRED, 0.40);
                    stats.add(StatType.STELLAR_CONDUCT_MULTIPLIER, conductMultiplier);
                });
    }

    /** Captures every mutable field and Radiance value. */
    public State captureState() {
        return new State(
                fieldEndTime,
                nextWindowTime,
                currentApplications,
                recordedApplications,
                stellarSwirlRadianceEndTime,
                nextRecordTimes);
    }

    /** Restores a previously captured state. */
    public void restoreState(State state) {
        fieldEndTime = state.fieldEndTime;
        nextWindowTime = state.nextWindowTime;
        currentApplications = state.currentApplications;
        recordedApplications = state.recordedApplications;
        stellarSwirlRadianceEndTime = state.stellarSwirlRadianceEndTime;
        nextRecordTimes.clear();
        nextRecordTimes.putAll(state.nextRecordTimes);
    }

    private void advanceTo(double currentTime) {
        if (nextWindowTime <= 0.0) {
            return;
        }
        while (currentTime + 1e-9 >= nextWindowTime
                && nextWindowTime <= fieldEndTime + 1e-9) {
            currentApplications = recordedApplications;
            recordedApplications = 0;
            nextRecordTimes.clear();
            nextWindowTime += WINDOW_DURATION;
        }
        if (!isFieldActive(currentTime)) {
            currentApplications = 0;
            recordedApplications = 0;
            nextRecordTimes.clear();
        }
    }
}
