package mechanics.rl;

import java.util.List;

import mechanics.optimization.ProfileAction;

/**
 * Represents one phase of a teacher rotation: a character name and the ordered
 * sequence of actions (SKILL, BURST, ATTACK, ATTACK_UNTIL_END) that character
 * must execute before the phase is considered complete.
 */
public class RotationPhase {
    public final String charName;
    public final List<ProfileAction> actions;

    public RotationPhase(String charName, List<ProfileAction> actions) {
        this.charName = charName;
        this.actions = actions;
    }

    @Override
    public String toString() {
        return charName + actions;
    }
}
