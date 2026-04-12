package model.entity;

import model.entity.state.ArtifactRollProfile;
import model.entity.state.CooldownState;
import model.entity.state.EnergyState;
import model.entity.state.SnapshotState;
import model.stats.StatsContainer;
import model.type.StatType;

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
    protected final java.util.List<mechanics.buff.Buff> activeBuffs = new java.util.ArrayList<>();

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
        this.baseStats = new StatsContainer();
        this.baseStats.set(StatType.CRIT_RATE, 0.05);
        this.baseStats.set(StatType.CRIT_DMG, 0.50);
        this.baseStats.set(StatType.ENERGY_RECHARGE, 1.0);
    }

    public int getConstellation() {
        return constellation;
    }

    public String getName() {
        return name;
    }

    public StatsContainer getBaseStats() {
        return baseStats;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }

    public void setArtifacts(ArtifactSet artifact) {
        this.artifacts = new ArtifactSet[] { artifact };
    }

    public ArtifactSet[] getArtifacts() {
        return artifacts;
    }

    public abstract void applyPassive(StatsContainer currentStats);

    public void addBuff(mechanics.buff.Buff buff) {
        activeBuffs.add(buff);
    }

    public void removeBuff(String name) {
        activeBuffs.removeIf(buff -> buff.getName().equals(name));
    }

    public boolean hasBuff(String name) {
        for (mechanics.buff.Buff buff : activeBuffs) {
            if (buff.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public java.util.List<mechanics.buff.Buff> getActiveBuffs() {
        return activeBuffs;
    }

    public void clearBuffs() {
        activeBuffs.clear();
    }

    public StatsContainer getEffectiveStats(double currentTime) {
        return statAssembler.assembleEffectiveStats(this, currentTime);
    }

    public StatsContainer getStructuralStats(double currentTime) {
        return statAssembler.assembleStructuralStats(this, currentTime);
    }

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

    public StatsContainer getSnapshot() {
        return snapshotState.getSnapshot();
    }

    public void onAction(String key, simulation.CombatSimulator sim) {
        if (weapon != null) {
            weapon.onAction(this, key, sim);
        }
        System.out.println(name + " does nothing specific for " + key);
    }

    public void onSwitchOut(simulation.CombatSimulator sim) {
        // Default: do nothing
    }

    public model.type.Element getElement() {
        return element;
    }

    public void receiveEnergy(double amount) {
        energyState.receiveEnergy(amount, getEnergyCost());
    }

    public double getCurrentEnergy() {
        return energyState.getCurrentEnergy();
    }

    public void receiveParticleEnergy(double baseAmount, double er) {
        energyState.receiveParticleEnergy(baseAmount, er, getEnergyCost());
    }

    public void receiveFlatEnergy(double amount) {
        energyState.receiveFlatEnergy(amount, getEnergyCost());
    }

    public double getTotalParticleEnergy() {
        return energyState.getTotalParticleEnergy();
    }

    public double getTotalFlatEnergy() {
        return energyState.getTotalFlatEnergy();
    }

    public double getTotalEnergyGained() {
        return energyState.getTotalEnergyGained();
    }

    public void resetEnergyStats() {
        energyState.reset(getEnergyCost());
        cooldownState.resetChargeState();
    }

    public void setArtifactRolls(java.util.Map<StatType, Integer> rolls) {
        artifactRollProfile.setArtifactRolls(rolls);
    }

    public java.util.Map<StatType, Integer> getArtifactRolls() {
        return artifactRollProfile.getArtifactRolls();
    }

    public void setSkillCD(double cd) {
        cooldownState.setSkillCD(cd);
    }

    public void setBurstCD(double cd) {
        cooldownState.setBurstCD(cd);
    }

    public void setSkillMaxCharges(int maxCharges) {
        cooldownState.setSkillMaxCharges(maxCharges);
    }

    public boolean canSkill(double currentTime) {
        return cooldownState.canSkill(currentTime);
    }

    public boolean canBurst(double currentTime) {
        return cooldownState.canBurst(currentTime, getCurrentEnergy(), getEnergyCost());
    }

    public boolean isBurstActive(double currentTime) {
        return false;
    }

    public double getSkillCDRemaining(double currentTime) {
        return cooldownState.getSkillCDRemaining(currentTime);
    }

    public double getBurstCDRemaining(double currentTime) {
        return cooldownState.getBurstCDRemaining(currentTime);
    }

    public void markSkillUsed(double currentTime) {
        cooldownState.markSkillUsed(currentTime);
    }

    public void markBurstUsed(double currentTime) {
        energyState.markBurstUsed(getEnergyCost());
        cooldownState.markBurstUsed(currentTime);
    }

    public java.util.List<double[]> getBurstEnergyWindows() {
        return energyState.getBurstEnergyWindows();
    }

    public abstract double getEnergyCost();

    public double getSkillCD() {
        return cooldownState.getSkillCD();
    }

    public double getBurstCD() {
        return cooldownState.getBurstCD();
    }

    public double getLastSkillTime() {
        return cooldownState.getLastSkillTime();
    }

    public double getLastBurstTime() {
        return cooldownState.getLastBurstTime();
    }

    public boolean isLunarCharacter() {
        return false;
    }

    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        return new java.util.ArrayList<>();
    }
}
