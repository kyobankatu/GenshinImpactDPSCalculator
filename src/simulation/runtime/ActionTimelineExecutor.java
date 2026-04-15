package simulation.runtime;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulationEventBus;
import simulation.action.AttackAction;

/**
 * Owns post-resolution action sequencing such as follow-up buffs, action event
 * dispatch, animation-duration scaling, and timeline advancement.
 */
public class ActionTimelineExecutor {
    private final CombatSimulator sim;
    private final SimulationEventBus eventBus;

    /**
     * Creates a timeline executor bound to the given simulator.
     *
     * @param sim active simulator
     * @param eventBus action event dispatcher used after action resolution
     */
    public ActionTimelineExecutor(CombatSimulator sim, SimulationEventBus eventBus) {
        this.sim = sim;
        this.eventBus = eventBus;
    }

    /**
     * Executes an already-authorized action and advances the simulation timeline.
     *
     * @param characterId acting character
     * @param action action to execute
     */
    public void execute(CharacterId characterId, AttackAction action) {
        Character character = requireCharacter(characterId);
        sim.performActionWithoutTimeAdvance(characterId, action);

        applyAscendantBlessingIfNeeded(character, action);
        eventBus.notifyAction(character, action, sim.getCurrentTime());

        sim.advanceTime(resolveAnimationDuration(character, action));
    }

    private Character requireCharacter(CharacterId characterId) {
        Character character = sim.getCharacter(characterId);
        if (character == null) {
            throw new RuntimeException("Character not found: " + characterId);
        }
        return character;
    }

    private void applyAscendantBlessingIfNeeded(Character character, AttackAction action) {
        if (sim.getMoonsign() != CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            return;
        }
        if (character.isLunarCharacter()) {
            return;
        }
        ActionType actionType = action.getActionType();
        if (actionType == ActionType.SKILL || actionType == ActionType.BURST) {
            sim.applyAscendantBlessing(character);
        }
    }

    private double resolveAnimationDuration(Character character, AttackAction action) {
        double duration = action.getAnimationDuration();
        ActionType actionType = action.getActionType();
        if (actionType != ActionType.NORMAL && actionType != ActionType.CHARGE) {
            return duration;
        }

        StatsContainer stats = character.getEffectiveStats(sim.getCurrentTime());
        List<Buff> buffs = sim.getApplicableBuffs(character);
        for (Buff buff : buffs) {
            if (!buff.isExpired(sim.getCurrentTime())) {
                buff.apply(stats, sim.getCurrentTime());
            }
        }

        double speed = stats.get(StatType.ATK_SPD);
        if (speed <= 0) {
            return duration;
        }

        double scaledDuration = duration / (1.0 + speed);
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Speed] Duration %.2fs -> %.2fs (SPD +%.0f%%)",
                    action.getAnimationDuration(), scaledDuration, speed * 100));
        }
        return scaledDuration;
    }
}
