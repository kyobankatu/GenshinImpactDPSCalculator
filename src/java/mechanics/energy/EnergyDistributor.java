package mechanics.energy;

import model.entity.Character;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Instance-based orchestrator for particle and flat-energy distribution within a
 * single {@link CombatSimulator}.
 */
public class EnergyDistributor {
    private static final double TWO_MEMBER_OFF_FIELD_MULTIPLIER = 0.8;
    private static final double THREE_MEMBER_OFF_FIELD_MULTIPLIER = 0.7;
    private static final double FOUR_MEMBER_OFF_FIELD_MULTIPLIER = 0.6;

    private final CombatSimulator sim;

    public EnergyDistributor(CombatSimulator sim) {
        this.sim = sim;
    }

    public void distributeParticles(Element particleElement, double count, ParticleType type) {
        try {
            if (sim.isLoggingEnabled()) {
                System.out.println("   [Energy] Distributing " + count + " " + particleElement + " particles...");
            }
            Character activeChar = sim.getActiveCharacter();
            if (activeChar == null) {
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Energy] No active character found!");
                }
                return;
            }
            double offFieldMultiplier = getOffFieldMultiplier(sim.getPartyMembers().size());

            for (Character c : sim.getPartyMembers()) {
                boolean isActive = c == activeChar;
                boolean isSameElement = c.getElement() == particleElement;

                double baseValue;
                if (particleElement == null || particleElement == Element.PHYSICAL) {
                    double neutralBase = 2.0;
                    double sizeMult = (type == ParticleType.ORB) ? 3.0 : 1.0;
                    baseValue = neutralBase * sizeMult;
                } else {
                    baseValue = type.getValue(isSameElement);
                }

                double rangeMultiplier = isActive ? 1.0 : offFieldMultiplier;
                double er = c.getEffectiveStats(sim.getCurrentTime()).get(StatType.ENERGY_RECHARGE);
                double particleBase = count * baseValue * rangeMultiplier;
                c.receiveParticleEnergy(particleBase, er);
            }

            sim.notifyParticle(particleElement, count);
        } catch (Exception e) {
            if (sim.isLoggingEnabled()) {
                System.out.println("[ERROR] Crash in EnergyDistributor:");
                e.printStackTrace();
            }
            throw e;
        }
    }

    /**
     * Resolves the inactive-character collection factor for the current party.
     *
     * <p>A one-character party has no off-field recipient. Four or more
     * registered characters retain the standard full-party minimum.
     *
     * @param partySize number of registered party members
     * @return multiplier applied before Energy Recharge
     */
    private static double getOffFieldMultiplier(int partySize) {
        if (partySize <= 1) {
            return 1.0;
        }
        if (partySize == 2) {
            return TWO_MEMBER_OFF_FIELD_MULTIPLIER;
        }
        if (partySize == 3) {
            return THREE_MEMBER_OFF_FIELD_MULTIPLIER;
        }
        return FOUR_MEMBER_OFF_FIELD_MULTIPLIER;
    }

    public void distributeFlatEnergy(double amount) {
        for (Character c : sim.getPartyMembers()) {
            c.receiveFlatEnergy(amount);
        }
    }

    public void scheduleKQMSEnemyParticles() {
        sim.registerEvent(new simulation.event.TimerEvent() {
            double[] dropTimes = { 10.0 };
            int idx = 0;

            @Override
            public void tick(CombatSimulator s) {
                distributeParticles(model.type.Element.PHYSICAL, 2.0, ParticleType.PARTICLE);
                idx++;
            }

            @Override
            public boolean isFinished(double t) {
                return idx >= dropTimes.length;
            }

            @Override
            public double getNextTickTime() {
                return idx < dropTimes.length ? dropTimes[idx] : -1;
            }
        });
    }
}
