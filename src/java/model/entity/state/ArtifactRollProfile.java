package model.entity.state;

import java.util.HashMap;
import java.util.Map;

import model.type.StatType;

/**
 * Holds optimizer-produced artifact roll allocations for a character.
 */
public class ArtifactRollProfile {
    private Map<StatType, Integer> artifactRolls;

    public void setArtifactRolls(Map<StatType, Integer> artifactRolls) {
        this.artifactRolls = artifactRolls;
    }

    public Map<StatType, Integer> getArtifactRolls() {
        return artifactRolls != null ? artifactRolls : new HashMap<>();
    }
}
