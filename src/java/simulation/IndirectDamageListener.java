package simulation;

import model.entity.Character;

/** Observer for reaction or environmental enemy damage without an attack action. */
@FunctionalInterface
public interface IndirectDamageListener {
    /**
     * Handles one resolved indirect-damage result.
     *
     * @param owner attributed character, or {@code null} for ownerless damage
     * @param damage final indirect damage dealt to the enemy
     * @param time simulation time in seconds
     */
    void onIndirectDamage(Character owner, double damage, double time);
}
