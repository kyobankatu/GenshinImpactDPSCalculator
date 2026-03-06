package model.entity;

import model.type.StatType;
import model.stats.StatsContainer;

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
 * <li>{@link #getEffectiveStats(double)} – full stats including active buffs;
 * use for damage calculations.</li>
 * <li>{@link #getStructuralStats(double)} – base + weapon + artifacts +
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
    protected StatsContainer baseStats; // Lv90時点の基礎ステータス
    protected Weapon weapon;
    protected ArtifactSet[] artifacts;

    protected int constellation = 0;

    /**
     * Initialises base stats with KQM standard defaults:
     * 5 % crit rate, 50 % crit DMG, 100 % energy recharge.
     */
    public Character() {
        this.baseStats = new StatsContainer();
        // default KQM Base
        this.baseStats.set(StatType.CRIT_RATE, 0.05);
        this.baseStats.set(StatType.CRIT_DMG, 0.50);
        this.baseStats.set(StatType.ENERGY_RECHARGE, 1.0); // Base 100%
    }

    /**
     * Returns the character's constellation level (0–6).
     *
     * @return constellation level
     */
    public int getConstellation() {
        return constellation;
    }

    /**
     * Returns the character's display name.
     *
     * @return character name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the raw level-90 base stats container before any equipment or buffs
     * are applied.
     *
     * @return base stats container
     */
    public StatsContainer getBaseStats() {
        return baseStats;
    }

    /**
     * Returns the currently equipped weapon.
     *
     * @return weapon, or {@code null} if none is equipped
     */
    public Weapon getWeapon() {
        return weapon;
    }

    /**
     * Equips the given weapon on this character.
     *
     * @param weapon weapon to equip
     */
    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }

    /**
     * Equips a single artifact set, replacing any previously equipped artifacts.
     *
     * @param artifact artifact set to equip
     */
    public void setArtifacts(ArtifactSet artifact) {
        // Support single set or array logic
        // Current logic uses array: protected ArtifactSet[] artifacts;
        this.artifacts = new ArtifactSet[] { artifact };
    }

    /**
     * Returns the array of equipped artifact sets.
     *
     * @return artifact set array, or {@code null} if none are equipped
     */
    public ArtifactSet[] getArtifacts() {
        return artifacts;
    }

    /**
     * Hook called during stat compilation to apply this character's passive
     * talents (e.g., ascension passives, innate scaling conversions).
     * Implementations should call {@code currentStats.add(...)} to modify the
     * provided container in-place.
     *
     * @param currentStats the aggregated stats container to mutate
     */
    public abstract void applyPassive(StatsContainer currentStats);

    protected java.util.List<mechanics.buff.Buff> activeBuffs = new java.util.ArrayList<>();

    /**
     * Adds a buff to this character's active buff list.
     *
     * @param buff buff to add
     */
    public void addBuff(mechanics.buff.Buff buff) {
        activeBuffs.add(buff);
    }

    /**
     * Removes all buffs with the given name from the active buff list.
     *
     * @param name name of the buff to remove
     */
    public void removeBuff(String name) {
        activeBuffs.removeIf(b -> b.getName().equals(name));
    }

    /**
     * Returns {@code true} if at least one active buff with the given name exists.
     *
     * @param name buff name to query
     * @return {@code true} if the buff is present
     */
    public boolean hasBuff(String name) {
        for (mechanics.buff.Buff b : activeBuffs) {
            if (b.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the live list of active buffs.
     * Callers should not modify this list directly; use {@link #addBuff} and
     * {@link #removeBuff} instead.
     *
     * @return mutable list of active buffs
     */
    public java.util.List<mechanics.buff.Buff> getActiveBuffs() {
        return activeBuffs;
    }

    /**
     * Removes all active buffs from this character.
     */
    public void clearBuffs() {
        activeBuffs.clear();
    }

    /**
     * Computes and returns the character's fully resolved stats at the given
     * simulation time, including base stats, weapon, artifacts, weapon passive,
     * all non-expired active buffs, artifact passives, and the character's own
     * passive talent.
     *
     * <p>
     * This is the primary stat source for damage calculations. Do <em>not</em>
     * call this from inside a team-buff provider's own stat lookup; use
     * {@link #getStructuralStats(double)} there to avoid recursion.
     *
     * @param currentTime simulation time in seconds
     * @return fully resolved stats container
     */
    public StatsContainer getEffectiveStats(double currentTime) {
        StatsContainer total = new StatsContainer();
        total = total.merge(baseStats);
        total = total.merge(weapon.getStats());
        // 聖遺物計算...
        if (artifacts != null) {
            for (ArtifactSet artifact : artifacts) {
                if (artifact != null) {
                    total = total.merge(artifact.getStats());
                }
            }
        }

        // Apply Weapon Passive
        if (weapon != null) {
            weapon.applyPassive(total, currentTime);
        }

        // Apply Buffs
        for (mechanics.buff.Buff buff : activeBuffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(total, currentTime);
            }
        }

        // Apply Artifact Dynamic/Passives (e.g. Emblem 4pc)
        if (artifacts != null) {
            for (ArtifactSet artifact : artifacts) {
                if (artifact != null) {
                    artifact.applyPassive(total);
                }
            }
        }

        applyPassive(total); // 固有天賦の適用
        return total;
    }

    /**
     * Computes a recursion-safe stat view that includes only base stats, weapon
     * stats, weapon passive, artifact stats, artifact passives, and the
     * character's own passive talent.
     *
     * <p>
     * This method intentionally ignores "Active Buffs" and "Team Buffs"
     * to prevent infinite recursion when a team buff needs to scale off the
     * provider's own stats (e.g., Kazuha's EM sharing).
     *
     * @param currentTime simulation time in seconds
     * @return structural stats container
     */
    public StatsContainer getStructuralStats(double currentTime) {
        StatsContainer total = new StatsContainer();
        total = total.merge(baseStats);
        if (weapon != null) {
            total = total.merge(weapon.getStats());
            weapon.applyPassive(total, currentTime);
        }
        if (artifacts != null) {
            for (ArtifactSet artifact : artifacts) {
                if (artifact != null) {
                    total = total.merge(artifact.getStats());
                    artifact.applyPassive(total);
                }
            }
        }
        applyPassive(total);
        return total;
    }

    /** Stored snapshot stats captured at skill/burst cast time. */
    protected StatsContainer snapshotStats;

    /**
     * Captures and stores a stat snapshot at the given simulation time.
     * {@link #getEffectiveStats(double)} is used as the base, then any
     * {@code extraBuffs} that have not yet expired are applied on top.
     * The resulting container is stored and retrievable via {@link #getSnapshot()}.
     *
     * <p>
     * Call this at the moment a skill or burst is cast to replicate Genshin's
     * snapshotting behaviour, where later damage instances inherit the caster's
     * stats at cast time rather than impact time.
     *
     * @param currentTime simulation time in seconds at which the snapshot is taken
     * @param extraBuffs  additional buffs to layer on top of the effective stats;
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

        // Artifact Passives already applied in getEffectiveStats

        this.snapshotStats = total;
    }

    /**
     * Returns the most recently captured stat snapshot.
     * If no snapshot has been captured yet, returns an empty
     * {@link StatsContainer}.
     *
     * @return snapshot stats, never {@code null}
     */
    public StatsContainer getSnapshot() {
        return snapshotStats != null ? snapshotStats : new StatsContainer();
    }

    /**
     * Called by the simulator when a named action (e.g. {@code "Q"} for burst,
     * {@code "E"} for skill) is executed. Delegates to the weapon's action hook
     * by default.
     *
     * @param key action key string
     * @param sim the active combat simulator
     */
    // Defines behavior for named actions (e.g. "Q", "E")
    public void onAction(String key, simulation.CombatSimulator sim) {
        if (weapon != null) {
            weapon.onAction(this, key, sim);
        }
        System.out.println(name + " does nothing specific for " + key);
    }

    /**
     * Called by the simulator when this character is swapped off-field.
     * Default implementation does nothing; override to handle off-field cleanup.
     *
     * @param sim the active combat simulator
     */
    // Called when the character is swapped out
    public void onSwitchOut(simulation.CombatSimulator sim) {
        // Default: Do nothing
    }

    protected double currentEnergy = 0;
    protected double totalEnergyGained = 0;

    // Analysis fields
    protected double totalFlatEnergyGained = 0;
    /** Cumulative particle energy received pre-ER scaling, post-field-penalty. */
    protected double totalParticleEnergyGained = 0; // Pre-ER, Post-FieldPenalty

    // Per-burst-window tracking for accurate ER requirement analysis
    private double particleEnergyThisWindow = 0;
    private double flatEnergyThisWindow = 0;
    private java.util.List<double[]> burstEnergyWindows = new java.util.ArrayList<>();

    protected model.type.Element element;

    /**
     * Returns the character's elemental vision (e.g. PYRO, CRYO).
     *
     * @return element type
     */
    public model.type.Element getElement() {
        return element;
    }

    /**
     * Adds the given final energy amount to this character's energy gauge,
     * capped at {@link #getEnergyCost()}, and accumulates it in the total
     * energy-gained counter.
     *
     * <p>
     * Prefer {@link #receiveParticleEnergy(double, double)} or
     * {@link #receiveFlatEnergy(double)} so that per-source accounting is
     * maintained correctly.
     *
     * @param amount final energy amount to add (after all multipliers)
     */
    public void receiveEnergy(double amount) {
        // Logic moved to manage distinct types?
        // Actually EnergyManager calls this with Final Amount.
        // We need overloaded method: receiveEnergy(amount, isFlat, isParticleBase)
        totalEnergyGained += amount;
        currentEnergy = Math.min(getEnergyCost(), currentEnergy + amount);
    }

    /**
     * Returns the character's current energy.
     *
     * @return current energy value
     */
    public double getCurrentEnergy() {
        return currentEnergy;
    }

    /**
     * Receives energy from elemental particles or orbs.
     * Records {@code baseAmount} (count × value × off-field factor) in the
     * pre-ER particle energy total, then applies the character's energy recharge
     * multiplier ({@code er}) before crediting the gauge via
     * {@link #receiveEnergy(double)}.
     *
     * @param baseAmount raw energy before ER scaling
     *                   (particle count × particle value × field penalty)
     * @param er         the character's energy recharge ratio (e.g. 1.32 for 132 %
     *                   ER)
     */
    public void receiveParticleEnergy(double baseAmount, double er) {
        // baseAmount = Count * Value * OffFieldFactor
        totalParticleEnergyGained += baseAmount;
        particleEnergyThisWindow += baseAmount;
        double amount = baseAmount * er;
        receiveEnergy(amount);
    }

    /**
     * Receives a flat energy amount that bypasses ER scaling (e.g. from certain
     * talent or passive mechanics). Tracks the amount separately for analysis
     * and forwards it to {@link #receiveEnergy(double)}.
     *
     * @param amount flat energy to add directly to the gauge
     */
    public void receiveFlatEnergy(double amount) {
        totalFlatEnergyGained += amount;
        flatEnergyThisWindow += amount;
        receiveEnergy(amount);
    }

    /**
     * Returns the cumulative pre-ER particle energy received across all windows.
     *
     * @return total particle energy (pre-ER)
     */
    public double getTotalParticleEnergy() {
        return totalParticleEnergyGained;
    }

    /**
     * Returns the cumulative flat energy received across all windows.
     *
     * @return total flat energy
     */
    public double getTotalFlatEnergy() {
        return totalFlatEnergyGained;
    }

    /**
     * Returns the cumulative total energy credited to the gauge across all
     * windows (post-ER for particle energy, direct for flat energy).
     *
     * @return total energy gained
     */
    public double getTotalEnergyGained() {
        return totalEnergyGained;
    }

    /**
     * Resets all energy counters and windows to their initial state.
     * Sets {@link #currentEnergy} to {@link #getEnergyCost()} so that the
     * first burst of a rotation can be used immediately.
     */
    public void resetEnergyStats() {
        totalEnergyGained = 0;
        currentEnergy = getEnergyCost(); // Start each rotation with full energy pre-loaded
        totalFlatEnergyGained = 0;
        totalParticleEnergyGained = 0;
        particleEnergyThisWindow = 0;
        flatEnergyThisWindow = 0;
        burstEnergyWindows.clear();
        chargeRestoreTimes.clear();
    }

    // Artifact Analysis
    protected java.util.Map<StatType, Integer> artifactRolls;

    /**
     * Stores the artifact substat roll distribution used by the optimizer.
     *
     * @param rolls map from {@link StatType} to number of rolls allocated
     */
    public void setArtifactRolls(java.util.Map<StatType, Integer> rolls) {
        this.artifactRolls = rolls;
    }

    /**
     * Returns the stored artifact substat roll distribution, or an empty map if
     * none has been set.
     *
     * @return roll distribution map, never {@code null}
     */
    public java.util.Map<StatType, Integer> getArtifactRolls() {
        return artifactRolls != null ? artifactRolls : new java.util.HashMap<>();
    }

    // --- Cooldown Tracking for RL ---
    protected double lastSkillTime = -999;
    protected double lastBurstTime = -999;
    protected double skillCD = 6.0; // Default
    protected double burstCD = 15.0; // Default

    // Charge-based skill support (e.g. Sucrose C1)
    protected int skillMaxCharges = 1;
    private java.util.List<Double> chargeRestoreTimes = new java.util.ArrayList<>();

    /**
     * Sets the elemental skill cooldown in seconds.
     *
     * @param cd cooldown duration
     */
    public void setSkillCD(double cd) {
        this.skillCD = cd;
    }

    /**
     * Sets the elemental burst cooldown in seconds.
     *
     * @param cd cooldown duration
     */
    public void setBurstCD(double cd) {
        this.burstCD = cd;
    }

    /**
     * Returns {@code true} if the elemental skill is available at the given
     * simulation time (i.e. no remaining cooldown / a charge is available).
     *
     * @param currentTime simulation time in seconds
     * @return {@code true} if the skill can be used
     */
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= 0;
    }

    /**
     * Returns {@code true} if the elemental burst can be used at the given
     * simulation time, requiring both the burst cooldown to have elapsed and
     * sufficient energy.
     *
     * @param currentTime simulation time in seconds
     * @return {@code true} if the burst can be used
     */
    public boolean canBurst(double currentTime) {
        return (currentTime - lastBurstTime) >= burstCD && currentEnergy >= getEnergyCost();
    }

    /**
     * Returns {@code true} if the character's burst is currently in an active
     * state (providing on-field buffs or dealing DoT damage). Default
     * implementation always returns {@code false}; override in subclasses that
     * have persistent burst effects.
     *
     * @param currentTime simulation time in seconds
     * @return {@code true} if the burst is active
     */
    // Check if the character's burst is currently active (providing buffs or dps)
    public boolean isBurstActive(double currentTime) {
        return false; // Default
    }

    /**
     * Returns the remaining elemental skill cooldown in seconds, or {@code 0}
     * if the skill (or a charge) is available. For charge-based skills, returns
     * the time until the earliest charge is restored.
     *
     * @param currentTime simulation time in seconds
     * @return seconds remaining on cooldown, {@code 0} if ready
     */
    public double getSkillCDRemaining(double currentTime) {
        if (skillMaxCharges > 1) {
            chargeRestoreTimes.removeIf(t -> t <= currentTime);
            int available = skillMaxCharges - chargeRestoreTimes.size();
            if (available > 0)
                return 0;
            return chargeRestoreTimes.get(0) - currentTime;
        }
        return Math.max(0, lastSkillTime + skillCD - currentTime);
    }

    /**
     * Returns the remaining elemental burst cooldown in seconds, or {@code 0}
     * if the burst cooldown has elapsed (energy requirement is checked
     * separately by {@link #canBurst(double)}).
     *
     * @param currentTime simulation time in seconds
     * @return seconds remaining on cooldown, {@code 0} if the timer has expired
     */
    public double getBurstCDRemaining(double currentTime) {
        return Math.max(0, lastBurstTime + burstCD - currentTime);
    }

    /**
     * Records that the elemental skill was used at {@code currentTime}.
     * For charge-based skills, a new charge-restore timestamp is appended and
     * the list is kept sorted. Always updates {@link #lastSkillTime}.
     *
     * @param currentTime simulation time in seconds
     */
    public void markSkillUsed(double currentTime) {
        if (skillMaxCharges > 1) {
            chargeRestoreTimes.removeIf(t -> t <= currentTime);
            chargeRestoreTimes.add(currentTime + skillCD);
            java.util.Collections.sort(chargeRestoreTimes);
        }
        this.lastSkillTime = currentTime;
    }

    /**
     * Records that the elemental burst was used at {@code currentTime}.
     * Snapshots the current window's particle and flat energy into
     * {@link #getBurstEnergyWindows()} for ER requirement analysis, resets the
     * per-window counters, drains the energy gauge to zero, and updates
     * {@link #lastBurstTime}.
     *
     * @param currentTime simulation time in seconds
     */
    public void markBurstUsed(double currentTime) {
        // Snapshot the energy window for this burst: [particleBase, flat, cost]
        burstEnergyWindows.add(new double[] { particleEnergyThisWindow, flatEnergyThisWindow, getEnergyCost() });
        // Reset window counters for the next burst window
        particleEnergyThisWindow = 0;
        flatEnergyThisWindow = 0;
        currentEnergy = 0;
        this.lastBurstTime = currentTime;
    }

    /**
     * Returns the list of per-burst-window energy records.
     * Each element is a {@code double[3]} array: {@code [particleBaseEnergy,
     * flatEnergy, burstCost]}.
     *
     * @return list of energy window records
     */
    public java.util.List<double[]> getBurstEnergyWindows() {
        return burstEnergyWindows;
    }

    /**
     * Returns the elemental burst energy cost for this character.
     *
     * @return burst energy cost
     */
    public abstract double getEnergyCost();

    // --- Getters for RL State ---

    /**
     * Returns the elemental skill cooldown duration in seconds.
     *
     * @return skill cooldown
     */
    public double getSkillCD() {
        return skillCD;
    }

    /**
     * Returns the elemental burst cooldown duration in seconds.
     *
     * @return burst cooldown
     */
    public double getBurstCD() {
        return burstCD;
    }

    /**
     * Returns the simulation time at which the elemental skill was last used.
     *
     * @return last skill use time in seconds
     */
    public double getLastSkillTime() {
        return lastSkillTime;
    }

    /**
     * Returns the simulation time at which the elemental burst was last used.
     *
     * @return last burst use time in seconds
     */
    public double getLastBurstTime() {
        return lastBurstTime;
    }

    /**
     * Returns {@code true} if this character is a Lunar character.
     * Lunar characters receive special synergy buffs from non-Lunar teammates
     * (Moonsign / Ascendant Blessing mechanics) and may interact with
     * {@code LUNAR_*} stat types in {@link model.type.StatType}.
     * Default implementation returns {@code false}; override in Lunar
     * character subclasses.
     *
     * @return {@code true} if this is a Lunar character
     */
    public boolean isLunarCharacter() {
        return false;
    }


    /**
     * Returns the list of team-wide buffs that this character provides to the
     * party. These buffs are applied to teammates during stat compilation and
     * must use {@link #getStructuralStats(double)} internally to avoid
     * recursive stat lookups.
     * Default implementation returns an empty list.
     *
     * @return list of team buffs granted by this character
     */
    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        return new java.util.ArrayList<>();
    }
}
