package simulation.runtime;

import model.entity.Character;
import model.entity.ActionTriggeredWeaponEffect;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Owns cooldown gating and dispatch for typed character actions.
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
     * Executes a typed character action after applying cooldown and energy gates.
     *
     * @param charName acting character name
     * @param request typed action request
     */
    public void performAction(String charName, CharacterActionRequest request) {
        Character character = sim.getCharacter(charName);
        if (character == null) {
            throw new RuntimeException("Character not found: " + charName);
        }

        if (request.getKey() == CharacterActionKey.SKILL) {
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

        if (request.getKey() == CharacterActionKey.BURST) {
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
                    sim.getCurrentTime(), charName, request.getLogLabel()));
        }

        if (character.getWeapon() instanceof ActionTriggeredWeaponEffect) {
            ((ActionTriggeredWeaponEffect) character.getWeapon()).onAction(character, request, sim);
        }

        character.onAction(request, sim);
        sim.setRotationTime(sim.getCurrentTime());
    }
}
