package mechanics.buff;

import model.stats.StatsContainer;
import model.entity.Character;
import model.type.CharacterId;
import model.type.Element;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class for all stat-modifying buffs in the simulation.
 *
 * <p>A buff is active for the time window {@code [startTime, expirationTime)}.
 * Subclasses implement {@link #applyStats} to write their concrete bonuses into
 * a {@link StatsContainer}.  Optional targeting filters allow a buff to be
 * restricted to certain characters ({@link #exclude}) or elements
 * ({@link #forElement}).
 */
public abstract class Buff {
    protected String name;
    protected BuffId id;
    protected double expirationTime; // Sim time when this buff expires
    protected double startTime; // Sim time when this buff starts

    // Targeting: null means "no restriction"
    protected Set<CharacterId> excludeChars = null;
    protected Element targetElement = null;

    /**
     * Creates a time-limited buff active from {@code currentTime} for {@code duration} seconds.
     *
     * @param name        display name used for logging and de-duplication
     * @param duration    how long the buff lasts in simulation seconds
     * @param currentTime simulation time at which the buff starts
     */
    public Buff(String name, double duration, double currentTime) {
        this(name, BuffId.CUSTOM, duration, currentTime);
    }

    public Buff(String name, BuffId id, double duration, double currentTime) {
        this.name = name;
        this.id = id;
        this.startTime = currentTime;
        this.expirationTime = currentTime + duration;
    }

    /**
     * Creates a permanent buff that never expires (expiration set to
     * {@link Double#MAX_VALUE}).
     *
     * @param name display name of the buff
     */
    public Buff(String name) {
        this(name, BuffId.CUSTOM);
    }

    public Buff(String name, BuffId id) {
        this.name = name;
        this.id = id;
        this.startTime = 0.0;
        this.expirationTime = Double.MAX_VALUE;
    }

    /**
     * Returns the display name of this buff.
     *
     * @return buff name string
     */
    public String getName() {
        return name;
    }

    public BuffId getId() {
        return id;
    }

    /** Exclude specific characters from receiving this buff. Returns this for chaining. */
    public Buff exclude(String... names) {
        this.excludeChars = new HashSet<>();
        for (String name : names) {
            this.excludeChars.add(CharacterId.fromName(name));
        }
        return this;
    }

    /** Exclude specific characters from receiving this buff. Returns this for chaining. */
    public Buff exclude(CharacterId... ids) {
        this.excludeChars = new HashSet<>(Arrays.asList(ids));
        return this;
    }

    /** Restrict this buff to characters with the given element. Returns this for chaining. */
    public Buff forElement(Element element) {
        this.targetElement = element;
        return this;
    }

    /** Returns true if this buff applies to a character with the given name and element. */
    public boolean appliesToCharacter(Character character) {
        if (excludeChars != null && excludeChars.contains(character.getCharacterId())) return false;
        if (targetElement != null && targetElement != character.getElement()) return false;
        return true;
    }

    /**
     * Applies this buff's stat bonuses to {@code stats} if {@code currentTime}
     * falls within the active window {@code [startTime, expirationTime)}.
     * Logs an informational message for Ineffa/Columbina when the buff is inactive.
     *
     * @param stats       the stats container to modify
     * @param currentTime current simulation time in seconds
     */
    // Applies stats for a calculation at 'currentTime'
    public void apply(StatsContainer stats, double currentTime) {
        // Enforce active window: [startTime, expirationTime)
        if (currentTime >= startTime && currentTime < expirationTime) {
            applyStats(stats, currentTime);
        }
    }

    /**
     * Returns {@code true} if this buff has expired at the given simulation time.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} when the buff window has closed
     */
    public boolean isExpired(double currentTime) {
        return currentTime >= expirationTime;
    }

    /**
     * Applies this buff's concrete stat modifications.  Called only when the
     * buff is within its active time window.
     *
     * @param stats       the stats container to modify
     * @param currentTime current simulation time in seconds
     */
    protected abstract void applyStats(StatsContainer stats, double currentTime);
}
