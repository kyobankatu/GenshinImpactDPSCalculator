package mechanics.rl;

import java.util.List;

import mechanics.optimization.ProfileAction;
import model.type.CharacterId;

/**
 * Represents one phase of a teacher rotation.
 */
public class RotationPhase {
    public final CharacterId characterId;
    public final List<ProfileAction> actions;

    public RotationPhase(CharacterId characterId, List<ProfileAction> actions) {
        this.characterId = characterId;
        this.actions = actions;
    }

    public RotationPhase(String charName, List<ProfileAction> actions) {
        this(CharacterId.fromName(charName), actions);
    }

    @Override
    public String toString() {
        return characterId.getDisplayName() + actions;
    }
}
