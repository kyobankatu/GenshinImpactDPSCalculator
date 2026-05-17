package model.entity;

import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import model.entity.state.ArtifactRollProfile;
import model.entity.state.CooldownState;
import model.entity.state.EnergyState;
import model.entity.state.SnapshotState;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.StatType;
import simulation.action.CharacterActionRequest;

/**
 * Abstract base class for all playable characters in the simulation.
 *
 * <p>
 * Each concrete subclass represents a specific character and must implement
 * {@link #applyPassive(StatsContainer)} to register its talent-derived stat
 * modifications, and {@link #getEnergyCost()} to declare the burst energy
 * requirement.
 *
 * <p>
 * <b>Stat computation overview:</b>
 * <ul>
 * <li>{@link #getEffectiveStats(double)} - full stats including active buffs;
 * use for damage calculations.</li>
 * <li>{@link #getStructuralStats(double)} - base + weapon + artifacts +
 * self-passive only; excludes active and team buffs to prevent recursive
 * cycles when team-buff providers query their own stats.</li>
 * </ul>
 *
 * <p>
 * <b>Snapshot pattern:</b> call
 * {@link #captureSnapshot(double, java.util.List)}
 * at cast time and {@link #getSnapshot()} during impact resolution to replicate
 * Genshin's snapshot behaviour for skills whose damage is calculated after the
 * initial buff window has elapsed.
 *
 * <p>
 * Initialised with KQM standard base stats: 5 % crit rate, 50 % crit DMG,
 * 100 % energy recharge.
 */
public abstract class Character {
    protected String name;
    /** Level-90 base stats for this character. */
    protected StatsContainer baseStats;
    protected Weapon weapon;
    protected ArtifactSet[] artifacts;
    protected int constellation = 0;
    protected model.type.Element element;
    protected CharacterId characterId = CharacterId.UNKNOWN;
    protected final java.util.List<mechanics.buff.Buff> activeBuffs = new java.util.ArrayList<>();
    protected final TalentDataSource talentData;

    private final StatAssembler statAssembler = new StatAssembler();
    private final SnapshotState snapshotState = new SnapshotState();
    private final EnergyState energyState = new EnergyState();
    private final CooldownState cooldownState = new CooldownState();
    private final ArtifactRollProfile artifactRollProfile = new ArtifactRollProfile();

    /**
     * Initialises base stats with KQM standard defaults:
     * 5 % crit rate, 50 % crit DMG, 100 % energy recharge.
     */
    public Character() {
        this(TalentDataManager.getInstance());
    }

    /**
     * Internal constructor that allows injecting a {@link TalentDataSource}
     * (useful for tests). Initialises base stats with the KQM defaults.
     *
     * @param talentData talent value lookup source
     */
    protected Character(TalentDataSource talentData) {
        this.talentData = talentData;
        this.baseStats = new StatsContainer();
        this.baseStats.set(StatType.CRIT_RATE, 0.05);
        this.baseStats.set(StatType.CRIT_DMG, 0.50);
        this.baseStats.set(StatType.ENERGY_RECHARGE, 1.0);
    }

    /** @return constellation level (0-6) */
    public int getConstellation() {
        return constellation;
    }

    /** @return character name string used for CSV / log identification */
    public String getName() {
        return name;
    }

    /** @return {@link CharacterId} of this character */
    public CharacterId getCharacterId() {
        return characterId;
    }

    /** @return container of base (level-90 + KQM default) stats */
    public StatsContainer getBaseStats() {
        return baseStats;
    }

    /** @return equipped weapon, or {@code null} if none */
    public Weapon getWeapon() {
        return weapon;
    }

    /**
     * Equips the supplied weapon on this character.
     *
     * @param weapon weapon to equip
     */
    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }

    /**
     * Equips a single 4-piece artifact set.
     *
     * @param artifact artifact set to equip
     */
    public void setArtifacts(ArtifactSet artifact) {
        this.artifacts = new ArtifactSet[] { artifact };
    }

    /** @return currently equipped artifact sets */
    public ArtifactSet[] getArtifacts() {
        return artifacts;
    }

    /**
     * Applies this character's talent-driven passive stat modifications.
     * <p>Called by {@link StatAssembler} after base + weapon + artifact stats
     * have been merged. Subclasses mutate {@code currentStats} in place.
     *
     * @param currentStats accumulated stats container to mutate
     */
    public abstract void applyPassive(StatsContainer currentStats);

    /**
     * Adds a buff to this character. If the buff has no source character set,
     * the source is filled in with this character's id (so team-buff lookups
     * can resolve correctly).
     *
     * @param buff buff to add
     */
    public void addBuff(mechanics.buff.Buff buff) {
        if (buff.getSourceCharacterId() == model.type.CharacterId.UNKNOWN) {
            buff.sourcedBy(this.characterId);
        }
        activeBuffs.add(buff);
    }

    /**
     * Removes every active buff matching the given identifier.
     *
     * @param id buff id to remove
     */
    public void removeBuff(mechanics.buff.BuffId id) {
        activeBuffs.removeIf(buff -> buff.getId() == id);
    }

    /**
     * Tests whether a buff with the given id is currently active.
     *
     * @param id buff id to query
     * @return {@code true} if at least one matching buff is active
     */
    public boolean hasBuff(mechanics.buff.BuffId id) {
        for (mechanics.buff.Buff buff : activeBuffs) {
            if (buff.getId() == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the live list of currently active buffs (not a copy).
     * Callers must not modify it concurrently with simulation advancement.
     *
     * @return live list of active buffs
     */
    public java.util.List<mechanics.buff.Buff> getActiveBuffs() {
        return activeBuffs;
    }

    /** Removes every active buff from this character. */
    public void clearBuffs() {
        activeBuffs.clear();
    }

    /**
     * Returns the full effective stats including base, weapon, artifacts,
     * passives, and all currently active buffs (self and team).
     * Used for damage calculations.
     *
     * @param currentTime current simulation time
     * @return stats container with all bonuses merged in
     */
    public StatsContainer getEffectiveStats(double currentTime) {
        return statAssembler.assembleEffectiveStats(this, currentTime);
    }

    /**
     * Returns structural stats only: base + weapon + artifacts + self-passive.
     * Excludes active buffs and team buffs so that team-buff providers can
     * query their own stats without producing recursive evaluation cycles.
     *
     * @param currentTime current simulation time
     * @return stats container without active/team buffs
     */
    public StatsContainer getStructuralStats(double currentTime) {
        return statAssembler.assembleStructuralStats(this, currentTime);
    }

    /**
     * Captures and stores a stats snapshot for delayed damage resolution.
     * <p>The captured value is the effective stats at {@code currentTime},
     * merged with any not-yet-expired {@code extraBuffs} (typically the
     * cast-time team buffs the off-field hit should inherit).
     *
     * @param currentTime current simulation time
     * @param extraBuffs  additional buffs to merge into the snapshot;
     *                    may be {@code null}
     */
    public void captureSnapshot(double currentTime, java.util.List<mechanics.buff.Buff> extraBuffs) {
        StatsContainer total = getEffectiveStats(currentTime);
        if (extraBuffs != null) {
            for (mechanics.buff.Buff buff : extraBuffs) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(total, currentTime);
                }
            }
        }
        snapshotState.setSnapshot(total);
    }

    /** @return last captured stats snapshot (empty container if none) */
    public StatsContainer getSnapshot() {
        return snapshotState.getSnapshot();
    }

    /**
     * Reacts to an action request issued against this character.
     * Default implementation merely logs the unhandled request; subclasses
     * override to implement character-specific responses (e.g. Xingqiu
     * spawning Rain Sword orbits on switch-in).
     *
     * @param request the incoming action request
     * @param sim     active combat simulator
     */
    public void onAction(CharacterActionRequest request, simulation.CombatSimulator sim) {
        System.out.println(name + " does nothing specific for " + request.getKey());
    }

    /** @return primary elemental type of this character */
    public model.type.Element getElement() {
        return element;
    }

    /**
     * Looks up a talent-table value by key, returning a default when no row
     * is configured for this character.
     *
     * @param key          talent key (e.g. {@code "SKILL_DMG_RATIO"})
     * @param defaultValue value to return when missing
     * @return looked-up value or {@code defaultValue}
     */
    protected double getTalentValue(String key, double defaultValue) {
        return talentData.get(this.name, key, defaultValue);
    }

    /**
     * Adds a generic energy amount (already ER-scaled if applicable).
     *
     * @param amount energy to add
     */
    public void receiveEnergy(double amount) {
        energyState.receiveEnergy(amount, getEnergyCost());
    }

    /** @return current energy on this character */
    public double getCurrentEnergy() {
        return energyState.getCurrentEnergy();
    }

    /**
     * Adds particle-based energy, applying the supplied ER multiplier.
     *
     * @param baseAmount unscaled particle value
     * @param er         Energy Recharge multiplier
     */
    public void receiveParticleEnergy(double baseAmount, double er) {
        energyState.receiveParticleEnergy(baseAmount, er, getEnergyCost());
    }

    /**
     * Adds flat energy (bypasses ER scaling).
     *
     * @param amount flat energy amount
     */
    public void receiveFlatEnergy(double amount) {
        energyState.receiveFlatEnergy(amount, getEnergyCost());
    }

    /** @return cumulative particle energy received before ER scaling */
    public double getTotalParticleEnergy() {
        return energyState.getTotalParticleEnergy();
    }

    /**
     * Returns total particle energy received after ER scaling.
     * Used by the capability profiler to measure a character's energy generation score.
     *
     * @return ER-scaled particle energy total
     */
    public double getTotalScaledParticleEnergy() {
        return energyState.getTotalScaledParticleEnergy();
    }

    /** @return cumulative flat energy received */
    public double getTotalFlatEnergy() {
        return energyState.getTotalFlatEnergy();
    }

    /** @return cumulative energy actually added to the energy bar */
    public double getTotalEnergyGained() {
        return energyState.getTotalEnergyGained();
    }

    /**
     * Resets all energy accumulators and clears any pending skill charge
     * restore timestamps. Called between simulation episodes.
     */
    public void resetEnergyStats() {
        energyState.reset(getEnergyCost());
        cooldownState.resetChargeState();
    }

    /**
     * Sets the optimizer-produced artifact substat roll allocation.
     *
     * @param rolls substat -&gt; roll count map
     */
    public void setArtifactRolls(java.util.Map<StatType, Integer> rolls) {
        artifactRollProfile.setArtifactRolls(rolls);
    }

    /** @return substat-to-roll-count map for this character */
    public java.util.Map<StatType, Integer> getArtifactRolls() {
        return artifactRollProfile.getArtifactRolls();
    }

    /**
     * Sets the skill cooldown length.
     *
     * @param cd cooldown in seconds
     */
    public void setSkillCD(double cd) {
        cooldownState.setSkillCD(cd);
    }

    /**
     * Sets the burst cooldown length.
     *
     * @param cd cooldown in seconds
     */
    public void setBurstCD(double cd) {
        cooldownState.setBurstCD(cd);
    }

    /**
     * Sets the maximum number of simultaneous skill charges.
     *
     * @param maxCharges maximum stored skill charges
     */
    public void setSkillMaxCharges(int maxCharges) {
        cooldownState.setSkillMaxCharges(maxCharges);
    }

    /**
     * @param currentTime current simulation time
     * @return {@code true} if a skill charge is available
     */
    public boolean canSkill(double currentTime) {
        return cooldownState.canSkill(currentTime);
    }

    /**
     * @param currentTime current simulation time
     * @return {@code true} if burst CD has elapsed and energy is sufficient
     */
    public boolean canBurst(double currentTime) {
        return cooldownState.canBurst(currentTime, getCurrentEnergy(), getEnergyCost());
    }

    /**
     * @param currentTime current simulation time
     * @return remaining skill cooldown in seconds (0 if ready)
     */
    public double getSkillCDRemaining(double currentTime) {
        return cooldownState.getSkillCDRemaining(currentTime);
    }

    /**
     * @param currentTime current simulation time
     * @return remaining burst cooldown in seconds (0 if ready)
     */
    public double getBurstCDRemaining(double currentTime) {
        return cooldownState.getBurstCDRemaining(currentTime);
    }

    /**
     * Records that the skill was just used at the given time.
     *
     * @param currentTime current simulation time
     */
    public void markSkillUsed(double currentTime) {
        cooldownState.markSkillUsed(currentTime);
    }

    /**
     * Records that the burst was just used: zeroes current energy, snapshots
     * the per-window energy subtotals, and starts the burst cooldown.
     *
     * @param currentTime current simulation time
     */
    public void markBurstUsed(double currentTime) {
        energyState.markBurstUsed(getEnergyCost());
        cooldownState.markBurstUsed(currentTime);
    }

    /**
     * @return list of per-burst-window energy subtotals
     *         ({@code [particle, flat, burstCost]} per entry)
     */
    public java.util.List<double[]> getBurstEnergyWindows() {
        return energyState.getBurstEnergyWindows();
    }

    /** @return burst energy cost (the value the bar fills toward) */
    public abstract double getEnergyCost();

    /** @return configured skill cooldown length */
    public double getSkillCD() {
        return cooldownState.getSkillCD();
    }

    /** @return configured burst cooldown length */
    public double getBurstCD() {
        return cooldownState.getBurstCD();
    }

    /** @return last skill use timestamp (large negative if never used) */
    public double getLastSkillTime() {
        return cooldownState.getLastSkillTime();
    }

    /** @return last burst use timestamp (large negative if never used) */
    public double getLastBurstTime() {
        return cooldownState.getLastBurstTime();
    }

    /**
     * Marks whether this character belongs to the custom Lunar archetype
     * (Ineffa / Flins / Columbina). Lunar characters receive synergy buffs
     * from non-Lunar teammates and are subject to the special-case branches
     * in {@code DamageCalculator}.
     *
     * @return {@code true} if this character is a Lunar character;
     *         default implementation returns {@code false}
     */
    public boolean isLunarCharacter() {
        return false;
    }

    /**
     * Returns a copy of the charge restore schedule for snapshot purposes.
     *
     * @return copy of charge restore times
     */
    public java.util.List<Double> getChargeRestoreTimes() {
        return cooldownState.getChargeRestoreTimes();
    }

    /**
     * Restores cooldown state from snapshot values.
     *
     * @param lastSkillTime      last skill use time
     * @param lastBurstTime      last burst use time
     * @param chargeRestoreTimes charge restore schedule
     */
    public void restoreCooldowns(double lastSkillTime, double lastBurstTime, java.util.List<Double> chargeRestoreTimes) {
        cooldownState.restore(lastSkillTime, lastBurstTime, chargeRestoreTimes);
    }

    /**
     * Directly sets current energy without updating totals. Used only for snapshot restore.
     *
     * @param energy energy value to set
     */
    public void restoreCurrentEnergy(double energy) {
        energyState.setCurrentEnergy(energy);
    }
}
