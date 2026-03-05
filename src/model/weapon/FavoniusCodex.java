package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.action.AttackAction;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;

public class FavoniusCodex extends Weapon {
    public FavoniusCodex() {
        super("Favonius Codex", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.ENERGY_RECHARGE, 0.459); // 45.9%
    }

    private double lastProcTime = -10.0; // Ready immediately

    @Override
    public void onDamage(Character user, AttackAction action, double currentTime, simulation.CombatSimulator sim) {
        // Windfall Passive: R5
        // CRIT Hits have 100% chance to gen particle. CD 6s.

        if (currentTime - lastProcTime < 6.0) {
            return;
        }

        // Check Crit
        // Since simulation is average-based, we approximate "CRIT Hit" as event
        // occurring with P = CritRate.
        // For accurate energy simulation over time, we roll for it.
        double cr = user.getEffectiveStats(currentTime).get(StatType.CRIT_RATE);

        // Clamp CR
        if (cr > 1.0)
            cr = 1.0;
        if (cr < 0.0)
            cr = 0.0;

        if (Math.random() < cr) {
            // Trigger
            System.out.println(
                    String.format("   [Favonius] Windfall Triggered by %s (CR: %.1f%%)", user.getName(), cr * 100));
            // Generate 3 Neutral Particles (Standard Favonius)
            // Text says "6 Energy for the character".
            // If we use standard particles: 3 Neutral * 2 Energy = 6 Energy (Base).
            // But Particles are affected by ER.
            // Description "regenerate 6 Energy for the character" implies flat energy?
            // "Generate a small amount of Elemental Particles, which will regenerate 6
            // Energy..."
            // Usually this means it generates particles. And particles regenerate energy.
            // In-game: It generates 3 Clear Orbs/Particles.
            // We use standard particle distribution.
            EnergyManager.distributeParticles(model.type.Element.PHYSICAL, 3.0, ParticleType.PARTICLE, sim);

            lastProcTime = currentTime;
        }
    }
}
