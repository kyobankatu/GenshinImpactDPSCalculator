package mechanics.energy;

/**
 * Represents the two sizes of elemental energy pickup in Genshin Impact.
 *
 * <p>Each constant stores two base energy values used by
 * {@link EnergyManager#distributeParticles}:
 * <ul>
 *   <li><b>diffElementValue</b> – energy given to a character whose element
 *       does not match the particle's element.</li>
 *   <li><b>sameElementValue</b> – energy given to a character whose element
 *       matches the particle's element.</li>
 * </ul>
 *
 * <p>Standard base values (before ER scaling and active/off-field multipliers):
 * <table>
 *   <caption>Energy values by particle type</caption>
 *   <tr><th>Type</th><th>Different Element</th><th>Same Element</th></tr>
 *   <tr><td>PARTICLE</td><td>1.0</td><td>3.0</td></tr>
 *   <tr><td>ORB</td><td>3.0</td><td>9.0</td></tr>
 * </table>
 */
public enum ParticleType {
    /** A standard elemental particle drop (e.g. from skill hits). */
    PARTICLE(1.0, 3.0),

    /** A larger elemental orb drop (e.g. from boss kills). */
    ORB(3.0, 9.0);

    private final double diffElementValue;
    private final double sameElementValue;

    ParticleType(double diff, double same) {
        this.diffElementValue = diff;
        this.sameElementValue = same;
    }

    /**
     * Returns the base energy value for a character receiving this particle.
     *
     * @param sameElement {@code true} if the receiving character's element matches
     *                    the particle's element
     * @return the base energy value before ER scaling and range multipliers
     */
    public double getValue(boolean sameElement) {
        return sameElement ? sameElementValue : diffElementValue;
    }
}
