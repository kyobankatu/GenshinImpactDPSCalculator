package simulation;

import model.entity.Character;
import model.entity.Enemy;
import simulation.action.AttackAction;
import simulation.runtime.BuffManager;
import simulation.runtime.CombatActionResolver;
import simulation.runtime.DamageReport;
import simulation.runtime.MoonsignManager;
import simulation.runtime.SimulationEventDispatcher;
import simulation.runtime.VisualLoggerSink;
import mechanics.buff.Buff;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * God-class that drives the entire time-based combat simulation.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li><b>Time progression</b> — {@link #advanceTime} ticks the event queue forward,
 *       firing registered {@link simulation.event.TimerEvent}s in chronological order.</li>
 *   <li><b>Action execution</b> — {@link #performAction(String, AttackAction)} resolves
 *       damage, applies elemental gauges, handles ICD, triggers reactions, and advances
 *       simulation time by the action's animation duration.</li>
 *   <li><b>Buff lifecycle</b> — team-wide and field-only {@link mechanics.buff.Buff}s are
 *       collected and merged in {@link #getApplicableBuffs} before each damage calculation.</li>
 *   <li><b>Reaction hooks</b> — elemental reactions are computed per hit; amplifying
 *       reactions modify the damage multiplier in-line while transformative reactions
 *       (Overload, Superconduct, Electro-Charged) produce independent damage instances.</li>
 *   <li><b>Moonsign system</b> — custom Lunar mechanic tracked via the {@link Moonsign}
 *       enum; when {@link Moonsign#ASCENDANT_GLEAM} is active, non-Lunar characters trigger
 *       {@link #applyAscendantBlessing} on Skill / Burst use.</li>
 * </ul>
 *
 * <p>Entry points create a {@code CombatSimulator}, call {@link #addCharacter} and
 * {@link #setEnemy}, then drive a rotation by calling {@link #performAction} and
 * {@link #switchCharacter} in sequence. After the rotation, {@link #getDPS} and
 * {@link #printReport} summarise results.
 */
public class CombatSimulator {
    private double currentTime = 0.0;
    private Party party;
    private Enemy enemy;
    private final DamageTracker damageReport;
    private final SimulationEventBus eventDispatcher;
    private final CombatLogSink combatLogSink;
    private final CombatActionResolver actionResolver;
    private final MoonsignManager moonsignManager;
    private final BuffManager buffManager;
    private boolean isECTimerRunning = false;
    private double thundercloudEndTime = -1.0;
    private boolean enableLogging = true;
    private double lastSwapTime = -999.0; // Tracks when the last swap was initiated
    private static final double SWAP_COOLDOWN = 1.0;

    /**
     * Enables or disables per-action console logging ({@code [T=x.x]} lines).
     * Disable before optimisation runs to reduce output noise.
     *
     * @param enable {@code true} to enable logging
     */
    public void setLoggingEnabled(boolean enable) {
        this.enableLogging = enable;
    }

    /**
     * Returns whether console logging is currently enabled.
     *
     * @return {@code true} if logging is on
     */
    public boolean isLoggingEnabled() {
        return enableLogging;
    }

    /**
     * Sets the EC timer running flag directly. Used by the EC tick event to signal
     * that the Electro-Charged DoT sequence has ended.
     *
     * @param running {@code true} if an EC tick timer is active
     */
    public void setECTimerRunning(boolean running) {
        this.isECTimerRunning = running;
    }

    /**
     * Returns whether the Thundercloud state (Lunar-Charged extension) is currently active.
     * The Thundercloud persists for 6 seconds after the last Lunar-Charged trigger.
     *
     * @return {@code true} if the current time is within the Thundercloud window
     */
    public boolean isThundercloudActive() {
        return currentTime < thundercloudEndTime;
    }

    /**
     * Constructs a new simulator with an empty party and zeroed time.
     */
    public CombatSimulator() {
        this(new DamageReport(), new SimulationEventDispatcher(), new VisualLoggerSink());
    }

    /**
     * Constructs a new simulator with injected collaborators for damage tracking,
     * event dispatch, and combat logging.
     *
     * <p>This constructor exists to reduce hard dependencies on concrete helper
     * implementations and allow alternate backends in tests or tooling.
     *
     * @param damageTracker damage recorder and report formatter
     * @param eventBus      action, particle, and reaction event dispatcher
     * @param combatLogSink sink used for timeline-style simulation logs
     */
    public CombatSimulator(DamageTracker damageTracker, SimulationEventBus eventBus, CombatLogSink combatLogSink) {
        this.party = new Party();
        this.damageReport = Objects.requireNonNull(damageTracker, "damageTracker");
        this.eventDispatcher = Objects.requireNonNull(eventBus, "eventBus");
        this.combatLogSink = Objects.requireNonNull(combatLogSink, "combatLogSink");
        this.buffManager = new BuffManager(this);
        this.actionResolver = new CombatActionResolver(this);
        this.moonsignManager = new MoonsignManager(this);
    }

    /**
     * Sets the enemy that characters will attack during the simulation.
     *
     * @param enemy the {@link Enemy} instance
     */
    public void setEnemy(Enemy enemy) {
        this.enemy = enemy;
    }

    /**
     * Returns the enemy currently set on the simulator.
     *
     * @return the {@link Enemy}, or {@code null} if not yet assigned
     */
    public Enemy getEnemy() {
        return enemy;
    }

    /**
     * Adds a character to the party and initialises their energy to full.
     * The first character added automatically becomes the active character.
     *
     * @param character the {@link Character} to add
     */
    public void addCharacter(Character character) {
        character.resetEnergyStats(); // Initialize with full energy
        party.addMember(character);
    }

    /**
     * Returns the party member with the given name, or {@code null} if not found.
     *
     * @param name the character's registered name
     * @return the matching {@link Character}
     */
    public Character getCharacter(String name) {
        return party.getMember(name);
    }

    /**
     * Returns all characters in the party.
     *
     * @return collection of all {@link Character} objects
     */
    public java.util.Collection<Character> getPartyMembers() {
        return party.getMembers();
    }

    /**
     * Returns the character currently on-field.
     *
     * @return the active {@link Character}
     */
    public Character getActiveCharacter() {
        return party.getActiveCharacter();
    }

    /**
     * Switches the active character to the named party member, enforcing a
     * {@value #SWAP_COOLDOWN}-second swap cooldown between consecutive swaps.
     *
     * <p>Sequence of events:
     * <ol>
     *   <li>If the swap cooldown has not elapsed, {@link #advanceTime} is called to reach it.</li>
     *   <li>{@code onSwitchOut} and artifact {@code onSwitchOut} callbacks fire for the outgoing character.</li>
     *   <li>The new character becomes active and artifact {@code onSwitchIn} callbacks fire.</li>
     *   <li>A 0.1-second swap delay is applied via {@link #advanceTime}.</li>
     *   <li>The swap is logged to the {@code VisualLogger} timeline.</li>
     * </ol>
     *
     * @param name the name of the character to switch to
     */
    public void switchCharacter(String name) {
        // Enforce 0.5s swap cooldown between consecutive swaps
        double cooldownEnd = lastSwapTime + SWAP_COOLDOWN;
        if (currentTime < cooldownEnd) {
            advanceTime(cooldownEnd - currentTime);
        }
        lastSwapTime = currentTime;

        Character oldChar = party.getActiveCharacter();
        String oldName = (oldChar != null) ? oldChar.getName() : "?";
        if (oldChar != null) {
            oldChar.onSwitchOut(this);
            // Notify Weapon of Switch Out
            if (oldChar.getWeapon() != null) {
                oldChar.getWeapon().onSwitchOut(oldChar, this);
            }
            // Notify Artifacts of Switch Out
            if (oldChar.getArtifacts() != null) {
                for (model.entity.ArtifactSet a : oldChar.getArtifacts()) {
                    if (a != null)
                        a.onSwitchOut(this, oldChar);
                }
            }
        }
        party.switchCharacter(name);

        // Log the swap to the combat timeline
        java.util.Map<model.type.Element, Double> auraSnap =
                (enemy != null) ? enemy.getAuraMap() : new java.util.HashMap<>();
        combatLogSink.log(
                currentTime, oldName, "Swap \u2192 " + name, 0.0, "None", 0.0, auraSnap);

        Character newChar = party.getActiveCharacter();
        if (newChar != null) {
            // Notify Artifacts of Switch In
            if (newChar.getArtifacts() != null) {
                for (model.entity.ArtifactSet a : newChar.getArtifacts()) {
                    if (a != null)
                        a.onSwitchIn(this, newChar);
                }
            }
        }

        advanceTime(0.1); // Swap delay
    }

    /**
     * Immediately sets the active character without triggering swap callbacks,
     * cooldowns, or time advancement. Intended for RL optimisation initialisation only.
     *
     * @param name the name of the character to set as active
     */
    // RL Optimization: Zero-cost swap for initialization
    public void setActiveCharacter(String name) {
        party.switchCharacter(name);
    }

    /**
     * Returns the current simulation time in seconds.
     *
     * @return current time
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * Adds a buff to the team buff list. The buff is considered for all party members
     * during {@link #getApplicableBuffs}.
     *
     * @param buff the {@link Buff} to add
     */
    public void applyTeamBuff(Buff buff) {
        buffManager.applyTeamBuff(buff);
    }

    /**
     * Adds a buff to the team buff list, first removing any existing buff with the same name.
     * Prevents stacking of effects that should not accumulate (e.g. refreshable buffs).
     *
     * @param buff the {@link Buff} to apply without stacking
     */
    public void applyTeamBuffNoStack(Buff buff) {
        buffManager.applyTeamBuffNoStack(buff);
    }

    /**
     * Adds a buff to the field buff list. Field buffs are only applied to the currently
     * active (on-field) character during {@link #getApplicableBuffs}.
     *
     * @param buff the {@link Buff} to restrict to the on-field character
     */
    public void applyFieldBuff(Buff buff) {
        buffManager.applyFieldBuff(buff);
    }

    /**
     * Collects all buffs that apply to the given character at the current simulation time.
     *
     * <p>Sources aggregated (in order):
     * <ol>
     *   <li>Team buffs whose {@code appliesToCharacter} predicate matches.</li>
     *   <li>Weapon team buffs from every party member's weapon.</li>
     *   <li>Character passive team buffs from every party member.</li>
     *   <li>Field buffs, only if {@code c} is the active character.</li>
     * </ol>
     *
     * @param c the character to collect buffs for
     * @return list of applicable {@link Buff} objects (may include expired buffs;
     *         callers should filter with {@code Buff#isExpired})
     */
    public List<Buff> getApplicableBuffs(Character c) {
        return buffManager.getApplicableBuffs(c);
    }

    /**
     * Represents the global Moonsign state of the party, which governs Ascendant Blessing
     * eligibility — a custom Lunar mechanic not present in the official game.
     *
     * <ul>
     *   <li>{@link #NONE} — no Lunar characters in the party; no Moonsign effects active.</li>
     *   <li>{@link #NASCENT_GLEAM} — exactly one Lunar character; Moonsign is active but
     *       Ascendant Blessing does not trigger.</li>
     *   <li>{@link #ASCENDANT_GLEAM} — two or more Lunar characters; non-Lunar characters
     *       trigger {@link #applyAscendantBlessing} when they use a Skill or Burst.</li>
     * </ul>
     *
     * Updated automatically by {@link #updateMoonsign()} based on party composition.
     */
    // Moonsign State (Global Party State)
    public enum Moonsign {
        NONE, NASCENT_GLEAM, ASCENDANT_GLEAM
    }

    private Moonsign currentMoonsign = Moonsign.NASCENT_GLEAM; // Default to Nascent for testing? Or NONE? Text implies
                                                               // it exists.

    /**
     * Directly sets the Moonsign state. Prefer {@link #updateMoonsign()} to derive the
     * correct state from the current party composition.
     *
     * @param sign the new {@link Moonsign} state
     */
    public void setMoonsign(Moonsign sign) {
        this.currentMoonsign = sign;
    }

    /**
     * Returns the current Moonsign state.
     *
     * @return the active {@link Moonsign}
     */
    public Moonsign getMoonsign() {
        return currentMoonsign;
    }

    /**
     * Convenience method returning all buffs applicable to the currently active character.
     * Delegates to {@link #getApplicableBuffs(Character)}.
     *
     * @return list of buffs for the active character
     */
    public List<Buff> getTeamBuffs() {
        return buffManager.getActiveCharacterBuffs();
    }

    private double rotationTime = 0.0;

    /**
     * Triggers a named ability action (e.g. {@code "skill"} or {@code "burst"}) for a character,
     * waiting out any remaining cooldown before executing.
     *
     * <p>For {@code "skill"}/{@code "E"} actions the skill cooldown is enforced; for
     * {@code "burst"}/{@code "Q"} actions the burst cooldown is enforced and an energy
     * warning is printed if the character lacks sufficient energy.
     * After cooldown resolution, the character's {@code onAction} callback and weapon
     * {@code onAction} passive are invoked.
     *
     * @param charName  the name of the character performing the action
     * @param actionKey the action key string (e.g. {@code "skill"}, {@code "burst"},
     *                  {@code "E"}, {@code "Q"})
     * @throws RuntimeException if no character with {@code charName} exists in the party
     */
    public void performAction(String charName, String actionKey) {
        Character c = party.getMember(charName);
        if (c == null)
            throw new RuntimeException("Character not found: " + charName);

        // Enforce Skill CD
        if (actionKey.equals("skill") || actionKey.equals("E")) {
            double wait = c.getSkillCDRemaining(currentTime);
            if (wait > 1e-9) {
                if (enableLogging)
                    System.out.println(String.format("[T=%.1f] %s Skill CD: waiting %.2fs", currentTime, charName, wait));
                advanceTime(wait);
            }
        }

        // Enforce Burst CD
        if (actionKey.equals("burst") || actionKey.equals("Q")) {
            double wait = c.getBurstCDRemaining(currentTime);
            if (wait > 1e-9) {
                if (enableLogging)
                    System.out.println(String.format("[T=%.1f] %s Burst CD: waiting %.2fs", currentTime, charName, wait));
                advanceTime(wait);
            }
            if (c.getCurrentEnergy() < c.getEnergyCost()) {
                System.out.println(String.format("[T=%.1f] WARNING: %s burst fired with insufficient energy (%.0f/%.0f)",
                        currentTime, charName, c.getCurrentEnergy(), c.getEnergyCost()));
            }
        }

        if (enableLogging)
            System.out.println(String.format("[T=%.1f] %s triggers action: %s", currentTime, charName, actionKey));

        // Trigger Weapon Passive
        if (c.getWeapon() != null) {
            c.getWeapon().onAction(c, actionKey, this);
        }

        c.onAction(actionKey, this);
        this.rotationTime = currentTime;
    }

    /**
     * Accumulates damage into the per-character damage report and overall total.
     * Called internally after each damage calculation and reaction damage instance.
     *
     * @param charName the name to attribute the damage to
     * @param dmg      the damage amount to record
     */
    public void recordDamage(String charName, double dmg) {
        damageReport.recordDamage(charName, dmg);
    }

    /**
     * Returns the simulation time at the end of the last recorded action, used as the
     * rotation duration denominator when computing DPS.
     *
     * @return rotation end time in seconds
     */
    public double getRotationTime() {
        return rotationTime;
    }

    /**
     * Prints a formatted DPS breakdown table to standard output, showing each character's
     * total damage, share percentage, and individual DPS over the rotation time.
     */
    public void printReport() {
        damageReport.printReport(rotationTime);
    }

    /**
     * Returns total DPS over the rotation ({@code totalDamage / rotationTime}).
     * Returns {@code 0.0} if no time has elapsed.
     *
     * @return DPS value
     */
    public double getDPS() {
        return damageReport.getDps(rotationTime);
    }

    /**
     * Returns the cumulative damage dealt across all characters and reactions.
     *
     * @return total damage
     */
    public double getTotalDamage() {
        return damageReport.getTotalDamage();
    }

    private java.util.PriorityQueue<simulation.event.TimerEvent> events = new java.util.PriorityQueue<>(
            java.util.Comparator.comparingDouble(simulation.event.TimerEvent::getNextTickTime));

    /**
     * Registers a {@link simulation.event.TimerEvent} in the priority queue.
     * The event will fire when {@link #advanceTime} advances past its
     * {@link simulation.event.TimerEvent#getNextTickTime()}.
     *
     * @param e the event to register
     */
    public void registerEvent(simulation.event.TimerEvent e) {
        events.add(e);
    }

    /**
     * Advances simulation time by {@code duration} seconds, firing any registered
     * {@link simulation.event.TimerEvent}s whose tick time falls within the interval.
     *
     * <p>Events are processed in chronological order; after each tick the event is
     * re-queued unless {@link simulation.event.TimerEvent#isFinished} returns {@code true}.
     * After all events in the window have fired, {@link #currentTime} is set to
     * {@code currentTime + duration} and {@link #rotationTime} is updated.
     *
     * @param duration the number of seconds to advance
     */
    public void advanceTime(double duration) {
        double targetTime = currentTime + duration;

        while (!events.isEmpty() && events.peek().getNextTickTime() <= targetTime) {
            simulation.event.TimerEvent e = events.poll();

            double dt = e.getNextTickTime() - currentTime;
            if (dt > 0) {
                currentTime += dt;
            }

            e.tick(this);

            if (!e.isFinished(currentTime)) {
                events.add(e);
            }
        }

        if (currentTime < targetTime) {
            currentTime = targetTime;
        }
        this.rotationTime = currentTime;
    }

    /**
     * Registers an {@link ActionListener} to be notified after each
     * {@link #performAction(String, AttackAction)} call.
     *
     * @param l the listener to add
     */
    public void addListener(ActionListener l) {
        eventDispatcher.addActionListener(l);
    }

    /**
     * Registers a {@link ParticleListener} to be notified when particles are generated.
     *
     * @param l the listener to add
     */
    public void addParticleListener(ParticleListener l) {
        eventDispatcher.addParticleListener(l);
    }

    /**
     * Notifies all registered {@link ParticleListener}s that particles have been generated.
     * Called by character skill/burst implementations after emitting particles.
     *
     * @param e     the element of the particles
     * @param count the number of particles generated
     */
    public void notifyParticle(model.type.Element e, double count) {
        eventDispatcher.notifyParticle(e, count, currentTime);
    }

    /**
     * Observer interface for elemental reaction events.
     * Implementations are registered via {@link CombatSimulator#addReactionListener} and
     * are called whenever an element application triggers a reaction, including synthetic
     * Lunar reaction events.
     */
    public interface ReactionListener {
        /**
         * Invoked when an elemental reaction occurs.
         *
         * @param result the {@link mechanics.reaction.ReactionResult} describing the reaction type
         *               and any associated multipliers or damage
         * @param source the {@link model.entity.Character} whose attack triggered the reaction
         * @param time   the simulation time in seconds at which the reaction occurred
         * @param sim    the {@link CombatSimulator} managing the current simulation
         */
        void onReaction(mechanics.reaction.ReactionResult result, model.entity.Character source, double time,
                CombatSimulator sim);
    }

    /**
     * Registers a {@link ReactionListener} to be notified when any elemental reaction fires.
     *
     * @param l the listener to add
     */
    public void addReactionListener(ReactionListener l) {
        eventDispatcher.addReactionListener(l);
    }

    /**
     * Notifies all registered {@link ReactionListener}s and all artifact {@code onReaction}
     * callbacks for every party member when a reaction has been triggered.
     *
     * @param result  the reaction result
     * @param trigger the character whose element triggered the reaction
     */
    public void notifyReaction(mechanics.reaction.ReactionResult result, model.entity.Character trigger) {
        eventDispatcher.notifyReaction(result, trigger, currentTime, this, party.getMembers());

        if (trigger != null && trigger.getWeapon() != null) {
            // Potential future hook for weapon reaction events
        }
    }

    private mechanics.element.ICDManager icdManager = new mechanics.element.ICDManager();

    /**
     * Recalculates and reapplies the Gleaming Moon Synergy buff based on the current
     * presence of "Gleaming Moon: Intent" and "Gleaming Moon: Devotion" buffs on any
     * party member. Each unique effect present contributes +10% to Lunar Charged,
     * Bloom, and Crystallize DMG Bonus. The resulting synergy buff is applied to all
     * party members for 8 seconds.
     *
     * <p>This method should be called after a damage or reaction event that might have
     * changed the count of active Gleaming Moon effects.
     */
    public void updateGleamingMoonSynergy() {
        moonsignManager.updateGleamingMoonSynergy();
    }

    /**
     * Recomputes the {@link Moonsign} state from the current party composition and
     * updates it accordingly.
     *
     * <ul>
     *   <li>0 Lunar characters → {@link Moonsign#NONE}</li>
     *   <li>1 Lunar character  → {@link Moonsign#NASCENT_GLEAM}</li>
     *   <li>2+ Lunar characters → {@link Moonsign#ASCENDANT_GLEAM}</li>
     * </ul>
     *
     * Should be called after the full party is assembled via {@link #addCharacter}.
     */
    public void updateMoonsign() {
        moonsignManager.updateMoonsign();
    }

    /**
     * Executes an {@link AttackAction} for the named character, then advances simulation
     * time by the action's (ATK-SPD-adjusted) animation duration.
     *
     * <p>In addition to the steps in {@link #performActionWithoutTimeAdvance}:
     * <ul>
     *   <li>If {@link Moonsign#ASCENDANT_GLEAM} is active and the acting character is
     *       non-Lunar and uses a SKILL or BURST, {@link #applyAscendantBlessing} fires.</li>
     *   <li>All registered {@link ActionListener}s are notified.</li>
     *   <li>For NORMAL and CHARGE actions, ATK SPD from buffs shortens the animation.</li>
     * </ul>
     *
     * @param charName the name of the character performing the action
     * @param action   the {@link AttackAction} to execute
     * @throws RuntimeException if no character with {@code charName} exists in the party
     */
    public void performAction(String charName, AttackAction action) {
        Character c = party.getMember(charName);
        performActionWithoutTimeAdvance(charName, action);

        // Moonsign Ascendant Blessing Trigger
        // Condition: Ascendant Gleam, Non-Lunar Char, Skill or Burst
        if (currentMoonsign == Moonsign.ASCENDANT_GLEAM
                && !c.isLunarCharacter()
                && (action.getActionType() == model.type.ActionType.SKILL
                        || action.getActionType() == model.type.ActionType.BURST)) {
            moonsignManager.applyAscendantBlessing(c);
        }

        // Notify Listeners (Atomic Actions)
        eventDispatcher.notifyAction(charName, action, currentTime);

        // Calculate Time Advance with ATK SPD
        double duration = action.getAnimationDuration();
        if (action.getActionType() == model.type.ActionType.NORMAL
                || action.getActionType() == model.type.ActionType.CHARGE) {
            model.stats.StatsContainer stats = c.getEffectiveStats(currentTime);
            List<Buff> buffs = getApplicableBuffs(c);
            for (Buff b : buffs) {
                if (!b.isExpired(currentTime))
                    b.apply(stats, currentTime);
            }
            double spd = stats.get(model.type.StatType.ATK_SPD);
            if (spd > 0) {
                duration /= (1.0 + spd);
                if (enableLogging)
                    System.out.println(String.format("   [Speed] Duration %.2fs -> %.2fs (SPD +%.0f%%)",
                            action.getAnimationDuration(), duration, spd * 100));
            }
        }
        advanceTime(duration);
    }

    /**
     * Resolves all damage and elemental effects of an {@link AttackAction} without
     * advancing simulation time. Used for periodic events (e.g. DoT ticks via
     * {@link simulation.event.PeriodicDamageEvent}) where time is already managed
     * by the event queue.
     *
     * <p>Steps executed:
     * <ol>
     *   <li>ICD check via {@code ICDManager}; element application proceeds only if ICD permits.</li>
     *   <li>If {@link AttackAction#isLunarConsidered()}, a synthetic Lunar reaction event is fired.</li>
     *   <li>Elemental gauge is checked against active auras; reactions are computed and
     *       transformative damage instances (Overload, EC, Superconduct, Swirl) are dealt.</li>
     *   <li>Amplifying reactions set a multiplier applied in {@code DamageCalculator}.</li>
     *   <li>Standard action damage is calculated via {@code DamageCalculator.calculateDamage}.</li>
     *   <li>Weapon {@code onDamage} and artifact {@code onDamage} hooks are invoked.</li>
     *   <li>The hit is logged to {@code VisualLogger}.</li>
     * </ol>
     *
     * @param charName the name of the character performing the action
     * @param action   the {@link AttackAction} to resolve
     * @throws RuntimeException if no character with {@code charName} exists in the party
     */
    public void performActionWithoutTimeAdvance(String charName, AttackAction action) {
        actionResolver.resolveWithoutTimeAdvance(charName, action);
    }

    /**
     * Returns the simulator's combat log sink for package-local helper collaborators.
     *
     * @return combat log sink
     */
    public CombatLogSink getCombatLogSink() {
        return combatLogSink;
    }

    /**
     * Returns the simulator's ICD manager for package-local action-resolution helpers.
     *
     * @return ICD manager
     */
    public mechanics.element.ICDManager getIcdManager() {
        return icdManager;
    }

    /**
     * Returns the live team-buff list for package-local helper collaborators that manage
     * simulator-owned buff state.
     *
     * @return mutable team-buff list
     */
    public List<Buff> getTeamBuffList() {
        return buffManager.getTeamBuffList();
    }

    /**
     * Removes all team buffs with the given display name.
     *
     * @param buffName buff name to remove
     */
    public void removeTeamBuffsByName(String buffName) {
        buffManager.removeTeamBuffsByName(buffName);
    }

    /**
     * Returns whether an Electro-Charged or Lunar-Charged timer is currently active.
     *
     * @return {@code true} if an EC-related timer is active
     */
    public boolean isECTimerRunning() {
        return isECTimerRunning;
    }

    /**
     * Returns the absolute simulation time at which the Thundercloud state expires.
     *
     * @return Thundercloud expiry time in seconds
     */
    public double getThundercloudEndTime() {
        return thundercloudEndTime;
    }

    /**
     * Sets the absolute simulation time at which the Thundercloud state expires.
     *
     * @param endTime Thundercloud expiry time in seconds
     */
    public void setThundercloudEndTime(double endTime) {
        this.thundercloudEndTime = endTime;
    }
}
