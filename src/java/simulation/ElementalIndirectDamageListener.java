package simulation;

import model.entity.Character;
import model.type.Element;

/** Observer for resolved indirect enemy damage with a known typed element. */
@FunctionalInterface
public interface ElementalIndirectDamageListener {
    /**
     * Handles one accepted elemental indirect-damage result.
     *
     * @param owner attributed character, or {@code null} for ownerless damage
     * @param element final damage element
     * @param damage final damage dealt to the enemy
     * @param time impact time in simulation seconds
     */
    void onElementalIndirectDamage(
            Character owner,
            Element element,
            double damage,
            double time);
}
