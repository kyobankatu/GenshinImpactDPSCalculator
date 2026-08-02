package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for characters with explicit standard-switch behavior.
 */
public interface SwitchAwareCharacter {
    /**
     * Applies character behavior before this character leaves the active field.
     *
     * @param sim active simulator whose party still has this character active
     */
    void onSwitchOut(CombatSimulator sim);

    /**
     * Applies character behavior after this character enters the active field.
     *
     * <p>Characters that only react to leaving the field inherit this no-op
     * bridge, preserving the existing switch-out capability contract.
     *
     * @param sim active simulator whose party already has this character active
     */
    default void onSwitchIn(CombatSimulator sim) {
    }
}
