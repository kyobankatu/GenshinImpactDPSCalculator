package simulation.runtime;

import java.util.HashMap;
import java.util.Map;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SwitchAwareCharacter;
import model.entity.SwitchAwareWeaponEffect;
import model.type.Element;
import simulation.CombatLogSink;
import simulation.CombatSimulator;
import simulation.Party;

/**
 * Owns character switching policy, callbacks, and timeline logging.
 */
public class SwitchManager {
    private static final double SWAP_COOLDOWN = 1.0;
    private static final double SWAP_DELAY = 0.1;

    private final CombatSimulator sim;
    private final Party party;
    private final CombatLogSink combatLogSink;
    private double lastSwapTime = -999.0;

    /**
     * Creates a switch manager bound to the current simulator state.
     *
     * @param sim active simulator
     * @param party active party container
     * @param combatLogSink sink used for swap timeline logs
     */
    public SwitchManager(CombatSimulator sim, Party party, CombatLogSink combatLogSink) {
        this.sim = sim;
        this.party = party;
        this.combatLogSink = combatLogSink;
    }

    /**
     * Performs a standard character swap with cooldown, callbacks, and delay.
     *
     * @param name target character name
     */
    public void switchCharacter(String name) {
        double currentTime = sim.getCurrentTime();
        double cooldownEnd = lastSwapTime + SWAP_COOLDOWN;
        if (currentTime < cooldownEnd) {
            sim.advanceTime(cooldownEnd - currentTime);
        }
        lastSwapTime = sim.getCurrentTime();

        Character oldChar = party.getActiveCharacter();
        String oldName = oldChar != null ? oldChar.getName() : "?";
        if (oldChar != null) {
            if (oldChar instanceof SwitchAwareCharacter) {
                ((SwitchAwareCharacter) oldChar).onSwitchOut(sim);
            }
            if (oldChar.getWeapon() instanceof SwitchAwareWeaponEffect) {
                ((SwitchAwareWeaponEffect) oldChar.getWeapon()).onSwitchOut(oldChar, sim);
            }
            notifyArtifactsSwitchOut(oldChar);
        }

        party.switchCharacter(name);

        Map<Element, Double> auraSnap = sim.getEnemy() != null ? sim.getEnemy().getAuraMap() : new HashMap<>();
        combatLogSink.log(sim.getCurrentTime(), oldName, "Swap -> " + name, 0.0, "None", 0.0, auraSnap);

        Character newChar = party.getActiveCharacter();
        if (newChar != null) {
            notifyArtifactsSwitchIn(newChar);
        }

        sim.advanceTime(SWAP_DELAY);
    }

    /**
     * Directly swaps the active character without callbacks or timeline cost.
     *
     * @param name target character name
     */
    public void setActiveCharacter(String name) {
        party.switchCharacter(name);
    }

    private void notifyArtifactsSwitchOut(Character character) {
        if (character.getArtifacts() == null) {
            return;
        }
        for (ArtifactSet artifact : character.getArtifacts()) {
            if (artifact != null) {
                artifact.onSwitchOut(sim, character);
            }
        }
    }

    private void notifyArtifactsSwitchIn(Character character) {
        if (character.getArtifacts() == null) {
            return;
        }
        for (ArtifactSet artifact : character.getArtifacts()) {
            if (artifact != null) {
                artifact.onSwitchIn(sim, character);
            }
        }
    }
}
