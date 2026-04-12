package simulation.runtime;

import model.entity.Character;
import simulation.CombatSimulator;

/**
 * Owns cooldown gating and dispatch for named character actions.
 */
public class ActionGateway {
    private final CombatSimulator sim;

    /**
     * Creates an action gateway bound to the given simulator.
     *
     * @param sim active simulator
     */
    public ActionGateway(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Executes a named character action after applying cooldown and energy gates.
     *
     * @param charName acting character name
     * @param actionKey action key such as {@code "skill"} or {@code "burst"}
     */
    public void performNamedAction(String charName, String actionKey) {
        Character character = sim.getCharacter(charName);
        if (character == null) {
            throw new RuntimeException("Character not found: " + charName);
        }

        if (isSkillAction(actionKey)) {
            double wait = character.getSkillCDRemaining(sim.getCurrentTime());
            if (wait > 1e-9) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "[T=%.1f] %s Skill CD: waiting %.2fs",
                            sim.getCurrentTime(), charName, wait));
                }
                sim.advanceTime(wait);
            }
        }

        if (isBurstAction(actionKey)) {
            double wait = character.getBurstCDRemaining(sim.getCurrentTime());
            if (wait > 1e-9) {
                if (sim.isLoggingEnabled()) {
                    System.out.println(String.format(
                            "[T=%.1f] %s Burst CD: waiting %.2fs",
                            sim.getCurrentTime(), charName, wait));
                }
                sim.advanceTime(wait);
            }
            if (character.getCurrentEnergy() < character.getEnergyCost()) {
                System.out.println(String.format(
                        "[T=%.1f] WARNING: %s burst fired with insufficient energy (%.0f/%.0f)",
                        sim.getCurrentTime(), charName, character.getCurrentEnergy(), character.getEnergyCost()));
            }
        }

        if (sim.isLoggingEnabled()) {
            System.out.println(String.format("[T=%.1f] %s triggers action: %s",
                    sim.getCurrentTime(), charName, actionKey));
        }

        if (character.getWeapon() != null) {
            character.getWeapon().onAction(character, actionKey, sim);
        }

        character.onAction(actionKey, sim);
        sim.setRotationTime(sim.getCurrentTime());
    }

    private boolean isSkillAction(String actionKey) {
        return "skill".equals(actionKey) || "E".equals(actionKey);
    }

    private boolean isBurstAction(String actionKey) {
        return "burst".equals(actionKey) || "Q".equals(actionKey);
    }
}
