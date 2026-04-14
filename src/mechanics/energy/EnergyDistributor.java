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
    private static final double OFF_FIELD_PENALTY = 0.6;

    private final CombatSimulator sim;

    public EnergyDistributor(CombatSimulator sim) {
        this.sim = sim;
    }

    public void distributeParticles(Element particleElement, double count, ParticleType type) {
        try {
            System.out.println("   [Energy] Distributing " + count + " " + particleElement + " particles...");
            Character activeChar = sim.getActiveCharacter();
            if (activeChar == null) {
                System.out.println("   [Energy] No active character found!");
                return;
            }

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

                double rangeMultiplier = isActive ? 1.0 : OFF_FIELD_PENALTY;
                double er = c.getEffectiveStats(sim.getCurrentTime()).get(StatType.ENERGY_RECHARGE);
                double particleBase = count * baseValue * rangeMultiplier;
                c.receiveParticleEnergy(particleBase, er);
            }

            sim.notifyParticle(particleElement, count);
        } catch (Exception e) {
            System.out.println("[ERROR] Crash in EnergyDistributor:");
            e.printStackTrace();
        }
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
