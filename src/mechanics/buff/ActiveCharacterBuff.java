package mechanics.buff;

import model.stats.StatsContainer;
import simulation.CombatSimulator;
import model.entity.Character;

/**
 * A {@link Buff} that only applies its stat bonuses when a specific character
 * is the currently active (on-field) character.
 *
 * <p>This is used for buffs whose description reads "while on the field", such
 * as Ineffa's Reconstruction Protocol passive which gives EM to each party
 * member only while they are active.
 */
public class ActiveCharacterBuff extends Buff {
    private CombatSimulator sim;
    private Character owner;
    private java.util.function.Consumer<StatsContainer> effect;

    /**
     * Creates a time-limited buff that is conditional on the owner being active.
     *
     * @param name        display name used for logging and de-duplication
     * @param duration    how long the buff lasts in simulation seconds
     * @param currentTime simulation time at which the buff starts
     * @param sim         the combat simulator used to query the active character
     * @param owner       the character who must be on-field for the buff to apply
     * @param effect      lambda that writes stat bonuses into the provided container
     */
    public ActiveCharacterBuff(String name, double duration, double currentTime,
            CombatSimulator sim, Character owner,
            java.util.function.Consumer<StatsContainer> effect) {
        super(name, duration, currentTime);
        this.sim = sim;
        this.owner = owner;
        this.effect = effect;
    }

    public ActiveCharacterBuff(String name, BuffId id, double duration, double currentTime,
            CombatSimulator sim, Character owner,
            java.util.function.Consumer<StatsContainer> effect) {
        super(name, id, duration, currentTime);
        this.sim = sim;
        this.owner = owner;
        this.effect = effect;
    }

    /**
     * Applies the effect lambda only if {@code owner} is the currently active
     * character in the simulation.
     *
     * @param stats       the stats container to modify
     * @param currentTime current simulation time in seconds (unused by this implementation)
     */
    @Override
    protected void applyStats(StatsContainer stats, double currentTime) {
        // Only apply if the owner of this buff is the currently active character
        if (sim.getActiveCharacter() == owner) {
            effect.accept(stats);
        }
    }
}
