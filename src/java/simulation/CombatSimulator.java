package simulation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.energy.EnergyDistributor;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import simulation.action.AttackAction;
import simulation.action.CharacterActionRequest;
import simulation.event.TimerEvent;
import simulation.runtime.ActionGateway;
import simulation.runtime.ActionTimelineExecutor;
import simulation.runtime.BuffManager;
import simulation.runtime.CombatActionResolver;
import simulation.runtime.DamageReport;
import simulation.runtime.MoonsignManager;
import simulation.runtime.ReactionState;
import simulation.runtime.ReactionStateController;
import simulation.runtime.SimulationClock;
import simulation.runtime.SimulationEventDispatcher;
import simulation.runtime.SwitchManager;
import simulation.runtime.VisualLoggerSink;

/**
 * Coordinates the time-based combat simulation and delegates focused runtime work
 * to helper collaborators.
 */
public class CombatSimulator {
    private final Party party;
    private Enemy enemy;
    private final DamageTracker damageReport;
    private final SimulationEventBus eventDispatcher;
    private final CombatLogSink combatLogSink;
    private final CombatActionResolver actionResolver;
    private final MoonsignManager moonsignManager;
    private final BuffManager buffManager;
    private final SimulationClock simulationClock;
    private final SwitchManager switchManager;
    private final ActionGateway actionGateway;
    private final ActionTimelineExecutor actionTimelineExecutor;
    private final ReactionState reactionState;
    private final ReactionStateController reactionStateController;
    private final EnergyDistributor energyDistributor;
    private final mechanics.element.ICDManager icdManager;
    private boolean enableLogging = true;

    /**
     * Represents the global Moonsign state of the party.
     */
    public enum Moonsign {
        NONE, NASCENT_GLEAM, ASCENDANT_GLEAM
    }

    private Moonsign currentMoonsign = Moonsign.NASCENT_GLEAM;

    /**
     * Constructs a simulator with default runtime collaborators.
     */
    public CombatSimulator() {
        this(new DamageReport(), new SimulationEventDispatcher(), new VisualLoggerSink());
    }

    /**
     * Constructs a simulator with injected reporting and event dependencies.
     *
     * @param damageTracker damage recorder and report formatter
     * @param eventBus      action, particle, and reaction dispatcher
     * @param combatLogSink combat timeline sink
     */
    public CombatSimulator(DamageTracker damageTracker, SimulationEventBus eventBus, CombatLogSink combatLogSink) {
        this.party = new Party();
        this.damageReport = Objects.requireNonNull(damageTracker, "damageTracker");
        this.eventDispatcher = Objects.requireNonNull(eventBus, "eventBus");
        this.combatLogSink = Objects.requireNonNull(combatLogSink, "combatLogSink");
        this.reactionState = new ReactionState();
        this.simulationClock = new SimulationClock(this);
        this.buffManager = new BuffManager(this);
        this.actionResolver = new CombatActionResolver(this);
        this.moonsignManager = new MoonsignManager(this);
        this.switchManager = new SwitchManager(this, party, combatLogSink);
        this.actionGateway = new ActionGateway(this);
        this.actionTimelineExecutor = new ActionTimelineExecutor(this, eventBus);
        this.reactionStateController = new ReactionStateController(this, reactionState);
        this.energyDistributor = new EnergyDistributor(this);
        this.icdManager = new mechanics.element.ICDManager();
    }

    /**
     * Enables or disables per-action console logging.
     *
     * @param enable {@code true} to enable logging
     */
    public void setLoggingEnabled(boolean enable) {
        this.enableLogging = enable;
    }

    /**
     * Returns whether console logging is enabled.
     *
     * @return {@code true} if enabled
     */
    public boolean isLoggingEnabled() {
        return enableLogging;
    }

    /**
     * Sets the enemy used in the current simulation.
     *
     * @param enemy target enemy
     */
    public void setEnemy(Enemy enemy) {
        this.enemy = enemy;
    }

    /**
     * Returns the current enemy.
     *
     * @return configured enemy, or {@code null}
     */
    public Enemy getEnemy() {
        return enemy;
    }

    /**
     * Adds a character to the party and initializes rotation energy state.
     *
     * @param character character to add
     */
    public void addCharacter(Character character) {
        character.resetEnergyStats();
        party.addMember(character);
    }

    /**
     * Returns a party member by name.
     *
     * @param name character name
     * @return matching character, or {@code null}
     */
    public Character getCharacter(String name) {
        return party.getMember(name);
    }

    public Character getCharacter(CharacterId id) {
        return party.getMember(id);
    }

    /**
     * Returns all party members.
     *
     * @return party members
     */
    public Collection<Character> getPartyMembers() {
        return party.getMembers();
    }

    /**
     * Returns the active character.
     *
     * @return active character, or {@code null}
     */
    public Character getActiveCharacter() {
        return party.getActiveCharacter();
    }

    /**
     * Executes a standard character swap with cooldown and callbacks.
     *
     * @param name target character name
     */
    public void switchCharacter(String name) {
        CharacterId id = party.resolveCharacterId(name);
        switchCharacter(id);
    }

    public void switchCharacter(CharacterId id) {
        switchManager.switchCharacter(id);
    }

    /**
     * Directly sets the active character without swap side effects.
     *
     * @param name target character name
     */
    public void setActiveCharacter(String name) {
        CharacterId id = party.resolveCharacterId(name);
        setActiveCharacter(id);
    }

    public void setActiveCharacter(CharacterId id) {
        switchManager.setActiveCharacter(id);
    }

    /**
     * Returns the current simulation time.
     *
     * @return current time in seconds
     */
    public double getCurrentTime() {
        return simulationClock.getCurrentTime();
    }

    /**
     * Adds a team buff.
     *
     * @param buff buff to add
     */
    public void applyTeamBuff(Buff buff) {
        buffManager.applyTeamBuff(buff);
    }

    /**
     * Adds a non-stacking team buff.
     *
     * @param buff buff to add
     */
    public void applyTeamBuffNoStack(Buff buff) {
        buffManager.applyTeamBuffNoStack(buff);
    }

    /**
     * Adds an active-character-only field buff.
     *
     * @param buff buff to add
     */
    public void applyFieldBuff(Buff buff) {
        buffManager.applyFieldBuff(buff);
    }

    /**
     * Returns buffs applicable to the given character.
     *
     * @param character character being evaluated
     * @return applicable buff list
     */
    public List<Buff> getApplicableBuffs(Character character) {
        return buffManager.getApplicableBuffs(character);
    }

    /**
     * Directly sets the Moonsign state.
     *
     * @param sign new moonsign state
     */
    public void setMoonsign(Moonsign sign) {
        this.currentMoonsign = sign;
    }

    /**
     * Returns the current Moonsign state.
     *
     * @return current moonsign
     */
    public Moonsign getMoonsign() {
        return currentMoonsign;
    }

    /**
     * Returns buffs currently applicable to the active character.
     *
     * @return active-character buff list
     */
    public List<Buff> getTeamBuffs() {
        return buffManager.getActiveCharacterBuffs();
    }

    public EnergyDistributor getEnergyDistributor() {
        return energyDistributor;
    }

    /**
     * Executes a typed action after applying cooldown and energy gates.
     *
     * @param charName acting character name
     * @param request typed action request
     */
    public void performAction(String charName, CharacterActionRequest request) {
        CharacterId id = party.resolveCharacterId(charName);
        performAction(id, request);
    }

    public void performAction(CharacterId characterId, CharacterActionRequest request) {
        actionGateway.performAction(characterId, request);
    }

    /**
     * Records dealt damage under the given source.
     *
     * @param charName source name
     * @param dmg damage amount
     */
    public void recordDamage(String charName, double dmg) {
        damageReport.recordDamage(charName, dmg);
    }

    public void recordDamage(CharacterId characterId, double dmg) {
        damageReport.recordDamage(characterId.getDisplayName(), dmg);
    }

    /**
     * Returns the tracked rotation duration.
     *
     * @return rotation time in seconds
     */
    public double getRotationTime() {
        return simulationClock.getRotationTime();
    }

    /**
     * Explicitly updates the tracked rotation time.
     *
     * @param rotationTime new rotation time in seconds
     */
    public void setRotationTime(double rotationTime) {
        simulationClock.setRotationTime(rotationTime);
    }

    /**
     * Prints a DPS summary report.
     */
    public void printReport() {
        damageReport.printReport(getRotationTime());
    }

    /**
     * Returns total DPS over the recorded rotation time.
     *
     * @return DPS value
     */
    public double getDPS() {
        return damageReport.getDps(getRotationTime());
    }

    /**
     * Returns total accumulated damage.
     *
     * @return total damage
     */
    public double getTotalDamage() {
        return damageReport.getTotalDamage();
    }

    /**
     * Returns the accumulated damage attributed to a specific character.
     *
     * @param id character whose damage to query
     * @return damage total for that character, or {@code 0.0} if not found
     */
    public double getDamageByCharacter(CharacterId id) {
        return damageReport.getDamageBySource(id.getDisplayName());
    }

    /**
     * Registers a timer event.
     *
     * @param event event to register
     */
    public void registerEvent(TimerEvent event) {
        simulationClock.registerEvent(event);
    }

    /**
     * Advances simulation time and ticks due events.
     *
     * @param duration duration to advance in seconds
     */
    public void advanceTime(double duration) {
        simulationClock.advanceTime(duration);
    }

    /**
     * Adds an action listener.
     *
     * @param listener listener to register
     */
    public void addListener(ActionListener listener) {
        eventDispatcher.addActionListener(listener);
    }

    /**
     * Adds a particle listener.
     *
     * @param listener listener to register
     */
    public void addParticleListener(ParticleListener listener) {
        eventDispatcher.addParticleListener(listener);
    }

    /**
     * Dispatches a particle event.
     *
     * @param element particle element
     * @param count particle count
     */
    public void notifyParticle(model.type.Element element, double count) {
        eventDispatcher.notifyParticle(element, count, getCurrentTime());
    }

    /**
     * Observer interface for elemental reaction events.
     */
    public interface ReactionListener {
        /**
         * Called whenever a reaction occurs.
         *
         * @param result reaction result
         * @param source triggering character
         * @param time reaction time
         * @param sim active simulator
         */
        void onReaction(mechanics.reaction.ReactionResult result, model.entity.Character source, double time,
                CombatSimulator sim);
    }

    /**
     * Adds a reaction listener.
     *
     * @param listener listener to register
     */
    public void addReactionListener(ReactionListener listener) {
        eventDispatcher.addReactionListener(listener);
    }

    /**
     * Dispatches a reaction event and artifact reaction hooks.
     *
     * @param result reaction result
     * @param trigger triggering character
     */
    public void notifyReaction(mechanics.reaction.ReactionResult result, model.entity.Character trigger) {
        eventDispatcher.notifyReaction(result, trigger, getCurrentTime(), this, party.getMembers());
    }

    /**
     * Recomputes party-wide Gleaming Moon synergy.
     */
    public void updateGleamingMoonSynergy() {
        moonsignManager.updateGleamingMoonSynergy();
    }

    /**
     * Recomputes Moonsign from current party composition.
     */
    public void updateMoonsign() {
        moonsignManager.updateMoonsign();
    }

    /**
     * Executes an attack action and advances time by its animation duration.
     *
     * @param charName acting character name
     * @param action action to execute
     */
    public void performAction(String charName, AttackAction action) {
        CharacterId characterId = party.resolveCharacterId(charName);
        performAction(characterId, action);
    }

    public void performAction(CharacterId characterId, AttackAction action) {
        actionTimelineExecutor.execute(characterId, action);
    }

    /**
     * Resolves an attack action without time advancement.
     *
     * @param charName acting character name
     * @param action action to resolve
     */
    public void performActionWithoutTimeAdvance(String charName, AttackAction action) {
        CharacterId characterId = party.resolveCharacterId(charName);
        performActionWithoutTimeAdvance(characterId, action);
    }

    public void performActionWithoutTimeAdvance(CharacterId characterId, AttackAction action) {
        actionResolver.resolveWithoutTimeAdvance(characterId, action);
    }

    /**
     * Returns the configured combat log sink.
     *
     * @return combat log sink
     */
    public CombatLogSink getCombatLogSink() {
        return combatLogSink;
    }

    /**
     * Returns the simulator ICD manager.
     *
     * @return ICD manager
     */
    public mechanics.element.ICDManager getIcdManager() {
        return icdManager;
    }

    /**
     * Collaborator-facing helper for Moonsign-specific follow-up policy.
     *
     * @param buffer the character whose stats determine the granted Lunar bonus
     */
    public void applyAscendantBlessing(Character buffer) {
        moonsignManager.applyAscendantBlessing(buffer);
    }

    /**
     * Returns the live simulator-owned team buff list.
     *
     * @return mutable team buff list
     */
    public List<Buff> getTeamBuffList() {
        return buffManager.getTeamBuffList();
    }

    public void removeTeamBuffsById(mechanics.buff.BuffId buffId) {
        buffManager.removeTeamBuffsById(buffId);
    }

    /**
     * Compatibility wrapper retained for existing reaction helpers.
     *
     * Sets the EC timer running flag.
     *
     * @param running new EC timer state
     */
    public void setECTimerRunning(boolean running) {
        reactionStateController.setEcTimerRunning(running);
    }

    /**
     * Compatibility wrapper retained for existing reaction helpers.
     *
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isECTimerRunning() {
        return reactionStateController.isEcTimerRunning();
    }

    /**
     * Compatibility wrapper retained for existing reaction helpers.
     *
     * Returns whether Thundercloud is active at the current time.
     *
     * @return {@code true} if active
     */
    public boolean isThundercloudActive() {
        return reactionStateController.isThundercloudActive();
    }

    /**
     * Compatibility wrapper retained for existing reaction helpers.
     *
     * Returns the Thundercloud expiry time.
     *
     * @return expiry time in seconds
     */
    public double getThundercloudEndTime() {
        return reactionStateController.getThundercloudEndTime();
    }

    /**
     * Compatibility wrapper retained for existing reaction helpers.
     *
     * Sets the Thundercloud expiry time.
     *
     * @param endTime expiry time in seconds
     */
    public void setThundercloudEndTime(double endTime) {
        reactionStateController.setThundercloudEndTime(endTime);
    }
}
