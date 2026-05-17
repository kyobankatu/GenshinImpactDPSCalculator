package model.entity.state;

import java.util.HashMap;
import java.util.Map;

import model.type.StatType;

/**
 * Holds optimizer-produced artifact roll allocations for a character.
 *
 * <p>The map associates a substat (e.g. {@link StatType#CRIT_RATE}) with the
 * integer number of rolls allocated to it. Used by
 * {@code ArtifactOptimizer} during hill-climbing and read back by
 * {@code StatAssembler} when assembling the final stat sheet.
 */
public class ArtifactRollProfile {
    private Map<StatType, Integer> artifactRolls;

    /**
     * Sets the substat-to-roll-count map for this character.
     *
     * @param artifactRolls map from substat key to integer roll count
     */
    public void setArtifactRolls(Map<StatType, Integer> artifactRolls) {
        this.artifactRolls = artifactRolls;
    }

    /**
     * Returns the substat roll allocations.
     *
     * @return roll map, or an empty map if none have been set
     */
    public Map<StatType, Integer> getArtifactRolls() {
        return artifactRolls != null ? artifactRolls : new HashMap<>();
    }
}
