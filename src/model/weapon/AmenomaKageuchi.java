package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

public class AmenomaKageuchi extends Weapon {
    public AmenomaKageuchi() {
        super("Amenoma Kageuchi", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.ATK_PERCENT, 0.551);
        this.weaponType = WeaponType.SWORD;
    }

    private int seeds = 0;
    private double lastSeedTime = -10.0; // Allow immediate cast

    @Override
    public void onAction(model.entity.Character user, CharacterActionRequest request, simulation.CombatSimulator sim) {
        if (request.getKey() == CharacterActionKey.SKILL) {
            if (sim.getCurrentTime() - lastSeedTime >= 5.0) {
                if (seeds < 3) {
                    seeds++;
                }
                lastSeedTime = sim.getCurrentTime();
                System.out.println("   [Amenoma] Gained Succession Seed (Total: " + seeds + ")");
            }
        } else if (request.getKey() == CharacterActionKey.BURST) {
            if (seeds > 0) {
                double refund = seeds * 12.0; // R5 Value
                System.out.println(
                        "   [Amenoma] Consumed " + seeds + " Seeds. Scheduling " + refund + " Energy restore.");
                seeds = 0;

                // Restore after 2s
                double triggerTime = sim.getCurrentTime() + 2.0;
                sim.registerEvent(new simulation.event.TimerEvent() {
                    private boolean done = false;

                    @Override
                    public double getNextTickTime() {
                        return triggerTime;
                    }

                    @Override
                    public void tick(simulation.CombatSimulator s) {
                        if (!done) {
                            user.receiveFlatEnergy(refund);
                            System.out.println("   [Amenoma] Restored " + refund + " Energy.");
                            done = true;
                        }
                    }

                    @Override
                    public boolean isFinished(double currentTime) {
                        return done;
                    }
                });
            }
        }
    }
}
