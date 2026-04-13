package mechanics.buff;

import model.stats.StatsContainer;
import java.util.function.Consumer;

/**
 * A concrete {@link Buff} implementation that delegates stat application to a
 * {@link Consumer}&lt;{@link StatsContainer}&gt; lambda.
 *
 * <p>Use {@code SimpleBuff} when the buff effect can be expressed as a single
 * lambda, for example:
 * <pre>
 *   new SimpleBuff("Bennett Q ATK", 12.0, sim.getCurrentTime(),
 *       s -> s.add(StatType.ATK_FLAT, bonus));
 * </pre>
 */
public class SimpleBuff extends Buff {
    private Consumer<StatsContainer> effect;

    /**
     * Creates a time-limited buff whose effect is provided as a lambda.
     *
     * @param name        display name used for logging and de-duplication
     * @param duration    how long the buff lasts in simulation seconds
     * @param currentTime simulation time at which the buff starts
     * @param effect      lambda that writes stat bonuses into the provided container
     */
    public SimpleBuff(String name, double duration, double currentTime, Consumer<StatsContainer> effect) {
        super(name, duration, currentTime);
        this.effect = effect;
    }

    public SimpleBuff(String name, BuffId id, double duration, double currentTime, Consumer<StatsContainer> effect) {
        super(name, id, duration, currentTime);
        this.effect = effect;
    }

    /**
     * Invokes the stored effect lambda to apply stat bonuses, if the effect is non-null.
     *
     * @param stats       the stats container to modify
     * @param currentTime current simulation time in seconds (unused by this implementation)
     */
    @Override
    protected void applyStats(StatsContainer stats, double currentTime) {
        if (effect != null) {
            effect.accept(stats);
        }
    }
}
