package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for weapons with passives that react to standard character switches.
 *
 * <p>
 * The legacy two-argument switch-out callback remains the required implementation
 * point. Target-aware implementations may override the three-argument overload,
 * while existing implementations continue to receive exactly one callback through
 * its default bridge.
 */
public interface SwitchAwareWeaponEffect {
    /**
     * Applies weapon behavior when the owner leaves the active field.
     *
     * @param user outgoing weapon owner
     * @param sim active simulator
     */
    void onSwitchOut(Character user, CombatSimulator sim);

    /**
     * Applies weapon behavior before the owner switches to a resolved party member.
     *
     * <p>
     * The default implementation delegates exactly once to the legacy
     * {@link #onSwitchOut(Character, CombatSimulator)} callback. Implementations that
     * need the incoming character may override this method without changing existing
     * switch-out implementations.
     *
     * @param user outgoing weapon owner
     * @param incoming resolved character entering the active field
     * @param sim active simulator whose party still has {@code user} active
     */
    default void onSwitchOut(Character user, Character incoming, CombatSimulator sim) {
        onSwitchOut(user, sim);
    }

    /**
     * Applies weapon behavior after the owner enters the active field.
     *
     * <p>
     * Weapons without switch-in behavior inherit this no-op implementation.
     *
     * @param user incoming weapon owner, already active in the simulator party
     * @param sim active simulator
     */
    default void onSwitchIn(Character user, CombatSimulator sim) {
    }
}
