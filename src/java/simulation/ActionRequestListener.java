package simulation;

import model.entity.Character;
import simulation.action.CharacterActionRequest;

/** Observer for typed character inputs after runtime gates accept them. */
public interface ActionRequestListener {
    /**
     * Handles one accepted typed input before character action logic runs.
     *
     * @param actor character receiving the input
     * @param request accepted typed request
     * @param time input time before the action advances the simulator
     */
    void onActionRequest(
            Character actor,
            CharacterActionRequest request,
            double time);
}
