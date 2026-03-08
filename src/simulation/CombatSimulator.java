package simulation;

import model.entity.Character;
import model.entity.Enemy;
import simulation.action.AttackAction;
import mechanics.buff.Buff;
import java.util.ArrayList;
import java.util.List;

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
    private List<Buff> teamBuffs = new ArrayList<>();
    private List<Buff> fieldBuffs = new ArrayList<>();
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
        this.party = new Party();
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
        visualization.VisualLogger.getInstance().log(
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
        teamBuffs.add(buff);
    }

    /**
     * Adds a buff to the team buff list, first removing any existing buff with the same name.
     * Prevents stacking of effects that should not accumulate (e.g. refreshable buffs).
     *
     * @param buff the {@link Buff} to apply without stacking
     */
    public void applyTeamBuffNoStack(Buff buff) {
        teamBuffs.removeIf(b -> b.getName().equals(buff.getName()));
        teamBuffs.add(buff);
    }

    /**
     * Adds a buff to the field buff list. Field buffs are only applied to the currently
     * active (on-field) character during {@link #getApplicableBuffs}.
     *
     * @param buff the {@link Buff} to restrict to the on-field character
     */
    public void applyFieldBuff(Buff buff) {
        fieldBuffs.add(buff);
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
        List<Buff> buffs = new ArrayList<>();
        for (Buff b : teamBuffs) {
            if (b.appliesToCharacter(c.getName(), c.getElement()))
                buffs.add(b);
        }

        // Collect Weapon Team Buffs and Character Team Buffs
        for (Character member : party.getMembers()) {
            if (member.getWeapon() != null) {
                buffs.addAll(member.getWeapon().getTeamBuffs(member));
            }
            buffs.addAll(member.getTeamBuffs());
        }

        if (c == party.getActiveCharacter()) {
            buffs.addAll(fieldBuffs);
        }
        // System.out.println("[DEBUG_CS] getApplicableBuffs for " + c.getName() + " ->
        // " + buffs.size());
        // System.out.print(" Buffs: ");
        // for (Buff b : buffs)
        // System.out.print(b.getName() + ", ");
        // System.out.println();
        return buffs;
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
        return getApplicableBuffs(party.getActiveCharacter());
    }

    private java.util.Map<String, Double> damageReport = new java.util.HashMap<>();
    private double totalDamage = 0.0;
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
        totalDamage += dmg;
        damageReport.put(charName, damageReport.getOrDefault(charName, 0.0) + dmg);
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
        System.out.println("----------------------------------------------");
        System.out.println("DPS Breakdown:");
        for (String name : damageReport.keySet()) {
            double d = damageReport.get(name);
            System.out.println(
                    String.format("%-10s : %,8.0f (%.1f%%) - DPS: %,.0f", name, d, d / totalDamage * 100, d / rotationTime));
        }
        System.out.println("----------------------------------------------");
        System.out.println("Total Rotation Damage: " + String.format("%,.0f", totalDamage));
        System.out.println(String.format("DPS (%.1fs): %,.0f", rotationTime, totalDamage / rotationTime));
    }

    /**
     * Returns total DPS over the rotation ({@code totalDamage / rotationTime}).
     * Returns {@code 0.0} if no time has elapsed.
     *
     * @return DPS value
     */
    public double getDPS() {
        return rotationTime > 0 ? totalDamage / rotationTime : 0.0;
    }

    /**
     * Returns the cumulative damage dealt across all characters and reactions.
     *
     * @return total damage
     */
    public double getTotalDamage() {
        return totalDamage;
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

    private java.util.List<ActionListener> listeners = new ArrayList<>();

    /**
     * Registers an {@link ActionListener} to be notified after each
     * {@link #performAction(String, AttackAction)} call.
     *
     * @param l the listener to add
     */
    public void addListener(ActionListener l) {
        listeners.add(l);
    }

    private java.util.List<ParticleListener> particleListeners = new ArrayList<>();

    /**
     * Registers a {@link ParticleListener} to be notified when particles are generated.
     *
     * @param l the listener to add
     */
    public void addParticleListener(ParticleListener l) {
        particleListeners.add(l);
    }

    /**
     * Notifies all registered {@link ParticleListener}s that particles have been generated.
     * Called by character skill/burst implementations after emitting particles.
     *
     * @param e     the element of the particles
     * @param count the number of particles generated
     */
    public void notifyParticle(model.type.Element e, double count) {
        for (ParticleListener l : particleListeners) {
            l.onParticle(e, count, currentTime);
        }
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

    private java.util.List<ReactionListener> reactionListeners = new ArrayList<>();

    /**
     * Registers a {@link ReactionListener} to be notified when any elemental reaction fires.
     *
     * @param l the listener to add
     */
    public void addReactionListener(ReactionListener l) {
        reactionListeners.add(l);
    }

    /**
     * Notifies all registered {@link ReactionListener}s and all artifact {@code onReaction}
     * callbacks for every party member when a reaction has been triggered.
     *
     * @param result  the reaction result
     * @param trigger the character whose element triggered the reaction
     */
    public void notifyReaction(mechanics.reaction.ReactionResult result, model.entity.Character trigger) {
        for (ReactionListener l : reactionListeners) {
            l.onReaction(result, trigger, currentTime, this);
        }

        // Notify Artifacts of ALL party members (Global Listeners)
        for (model.entity.Character member : party.getMembers()) {
            if (member.getArtifacts() != null) {
                for (model.entity.ArtifactSet a : member.getArtifacts()) {
                    if (a != null) {
                        a.onReaction(this, result, trigger, member);
                    }
                }
            }
        }

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
        // Count Unique Effects
        boolean hasIntent = false;
        boolean hasDevotion = false;

        for (Character m : party.getMembers()) {
            for (mechanics.buff.Buff b : getApplicableBuffs(m)) { // Check active buffs
                if (b.getName().equals("Gleaming Moon: Intent"))
                    hasIntent = true;
                if (b.getName().equals("Gleaming Moon: Devotion"))
                    hasDevotion = true;
            }
        }

        int count = (hasIntent ? 1 : 0) + (hasDevotion ? 1 : 0);

        if (count > 0) {
            final double bonus = 0.10 * count;

            // Create Synergy Buff
            // Duration? Text says "increased ... for each different ... effect THAT party
            // members HAVE".
            // So it's a dynamic state, not a fixed duration buff triggered by event.
            // But we implement it as a maintained Buff for simplicity.
            // When buffs expire, we need to update this.
            // BUT we only call this on TRIGGER (Damage or Reaction).
            // Optimization: Just ensure this buff exists and has correct value.
            // For now, let's refresh it with a short duration (e.g. 0.5s) or matching the
            // source duration?
            // "Devotion" lasts 8s. "Intent" lasts 4s.
            // Safe bet: Refresh it for 8s (max possible). If Intent drops, we won't know
            // until next check.
            // Ideally: The Synergy Buff itself should be dynamic check in `applyStats`.

            mechanics.buff.Buff synergyBuff = new mechanics.buff.Buff("Gleaming Moon: Synergy", 8.0, currentTime) {
                @Override
                protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                    // 10% * Count
                    // Check dynamic count again? Or allow Snapshot?
                    // Artifact text implies constant checking.
                    // But simpler to snapshot current count.
                    stats.add(model.type.StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
                    stats.add(model.type.StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
                    stats.add(model.type.StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
                }
            };

            // Apply to everyone
            for (Character m : party.getMembers()) {
                m.addBuff(synergyBuff);
            }
        }
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
        int lunarCount = 0;
        for (Character c : party.getMembers()) {
            if (c.isLunarCharacter()) {
                lunarCount++;
            }
        }

        if (lunarCount >= 2) {
            setMoonsign(Moonsign.ASCENDANT_GLEAM);
            if (enableLogging)
                System.out.println("[System] Moonsign updated to ASCENDANT_GLEAM (Lunar Chars: " + lunarCount + ")");
        } else if (lunarCount == 1) {
            setMoonsign(Moonsign.NASCENT_GLEAM);
            if (enableLogging)
                System.out.println("[System] Moonsign updated to NASCENT_GLEAM (Lunar Chars: " + lunarCount + ")");
        } else {
            setMoonsign(Moonsign.NONE);
        }
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

            applyAscendantBlessing(c);
        }

        // Notify Listeners (Atomic Actions)
        for (ActionListener l : new ArrayList<>(listeners)) { // Copy to avoid concurrent mod
            l.onAction(charName, action, currentTime);
        }

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

    private void applyAscendantBlessing(Character buffer) {
        // Calculate Bonus
        // Max 36%
        double bonus = 0.0;
        model.stats.StatsContainer s = buffer.getEffectiveStats(currentTime);
        model.type.Element e = buffer.getElement();

        if (e == model.type.Element.PYRO || e == model.type.Element.ELECTRO || e == model.type.Element.CRYO) {
            // 0.9% per 100 ATK
            double atk = s.getTotalAtk();
            bonus = (atk / 100.0) * 0.009;
        } else if (e == model.type.Element.HYDRO) {
            // 0.6% per 1000 HP
            double hp = s.getTotalHp();
            bonus = (hp / 1000.0) * 0.006;
        } else if (e == model.type.Element.GEO) {
            // 1% per 100 DEF
            double def = s.getTotalDef();
            bonus = (def / 100.0) * 0.01;
        } else if (e == model.type.Element.ANEMO || e == model.type.Element.DENDRO) {
            // 2.25% per 100 EM
            double em = s.get(model.type.StatType.ELEMENTAL_MASTERY);
            bonus = (em / 100.0) * 0.0225;
        }

        // Cap at 36% (0.36)
        if (bonus > 0.36)
            bonus = 0.36;

        final double finalBonus = bonus;

        // Apply Team Buff
        // "Moonsign: Ascendant Blessing"
        // Check if existing buff is stronger?
        // Logic: "Effects do not stack". Usually implies overwrite or keep strongest.
        // Let's overwite for now as usually latest snapshot applies, OR we check value.
        // Given spec: "If multiple Non-Lunar chars exist, use the most high one."
        // We should check existing buff value.

        // But Buffs are stored in a List. We can look for it.
        boolean applied = false;
        Buff betterBuff = null;

        // We need a custom Buff class or identify by name to check value
        // SimpleBuff doesn't store the "source value" easily accessible unless we parse
        // name or extend.
        // Let's extend Buff for this.

        // Search current Team Buffs
        for (Buff b : teamBuffs) {
            if (b.getName().equals("Moonsign: Ascendant Blessing")) {
                // How to check value?
                // We'll trust the logic: if new bonus is higher, replace.
                // We will implement a specialized MoonsignBuff class inside here or just use a
                // field.
                if (b instanceof MoonsignBuff) {
                    if (((MoonsignBuff) b).getValue() > finalBonus) {
                        // Existing is stronger, do not overwrite. but REFRESH duration?
                        // "Effect will not stack".
                        // Usually implies we keep the strongest.
                        // If same, maybe refresh?
                        // Let's assume we convert valid duration if new is same/stronger.
                        // But if new is weaker, we ignore?
                        return; // Keep existing stronger buff
                    }
                }
            }
        }

        // Remove old if exists (since we are replacing with stronger/new)
        teamBuffs.removeIf(b -> b.getName().equals("Moonsign: Ascendant Blessing"));

        if (enableLogging)
            System.out.println(
                    String.format("   [Buff] Ascendant Blessing Triggered by %s (+%.1f%% Lunar DMG) - Duration 20s",
                            buffer.getName(), finalBonus * 100));

        applyTeamBuff(new MoonsignBuff(finalBonus, currentTime));
    }

    private static class MoonsignBuff extends Buff {
        private double value;

        public MoonsignBuff(double value, double startTime) {
            super("Moonsign: Ascendant Blessing", 20.0, startTime);
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        @Override
        protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
            stats.add(model.type.StatType.LUNAR_MOONSIGN_BONUS, value);
        }
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
        Character c = party.getMember(charName);
        if (c == null)
            throw new RuntimeException("Character not found: " + charName);

        if (action.getICDTag() == null) {
            action.setICD(action.getICDType(), model.type.ICDTag.None, action.getGaugeUnits());
        }
        if (action.getICDType() == null) {
            action.setICD(model.type.ICDType.Standard, action.getICDTag(), action.getGaugeUnits());
        }

        // ICD Check
        boolean applied = icdManager.checkApplication(charName, action.getICDTag(), action.getICDType(), currentTime);

        // Notify Lunar Action (Inflicting Lunar DMG)
        if (action.isLunarConsidered()) {
            // Notify specific Lunar Reaction if noted
            if (action.getLunarReactionType() != null) {
                String rType = "Lunar-" + action.getLunarReactionType();
                // Create dummy result
                mechanics.reaction.ReactionResult dummy = mechanics.reaction.ReactionResult.transform(0.0, rType);
                notifyReaction(dummy, c);
            }
        }

        double reactionMulti = 1.0;

        if (applied && action.getGaugeUnits() > 0) {
            model.type.Element trigger = action.getElement();
            java.util.Set<model.type.Element> currentAuras = enemy.getActiveAuras();
            boolean reactionTriggered = false;

            // Stats for EM
            model.stats.StatsContainer stats;
            if (action.isUseSnapshot()) {
                stats = c.getSnapshot();
            } else {
                stats = c.getEffectiveStats(currentTime);
                List<Buff> buffs = getApplicableBuffs(c);
                for (Buff b : buffs) {
                    if (!b.isExpired(currentTime))
                        b.apply(stats, currentTime);
                }
            }
            double em = stats.get(model.type.StatType.ELEMENTAL_MASTERY);
            double swirlBonus = stats.get(model.type.StatType.SWIRL_DMG_BONUS);

            for (model.type.Element aura : currentAuras) {
                mechanics.reaction.ReactionResult result = mechanics.reaction.ReactionCalculator.calculate(trigger,
                        aura, em, 90, swirlBonus);

                if (result.getType() != mechanics.reaction.ReactionResult.Type.NONE) {
                    reactionTriggered = true;

                    // Notify Listeners (Reaction Triggered)
                    String reactName = result.getName();
                    if (reactName.equals("Crystallize")) {
                        reactName += " (" + aura.toString() + ")";
                    }
                    notifyReaction(result, c);

                    if (result.getType() == mechanics.reaction.ReactionResult.Type.AMP) {
                        reactionMulti = result.getAmpMultiplier();
                        if (enableLogging)
                            System.out.println(String.format("   [Reaction] %s on %s -> %s Multi %.2f", trigger, aura,
                                    result.getName(), reactionMulti));

                        double consumption = action.getGaugeUnits(); // Base Trigger Units

                        // Simplified check based on element pairs
                        boolean isReverse = (trigger == model.type.Element.PYRO && aura == model.type.Element.HYDRO) ||
                                (trigger == model.type.Element.CRYO && aura == model.type.Element.PYRO);

                        double modifier = isReverse ? 0.5 : 2.0;
                        double reduction = consumption * modifier;

                        this.getEnemy().reduceAura(aura, reduction);

                    } else if (result.getType() == mechanics.reaction.ReactionResult.Type.TRANSFORMATIVE) {
                        model.type.Element reactionElement = model.type.Element.PYRO; // Default Overload
                        if (result.getName().equals("Electro-Charged"))
                            reactionElement = model.type.Element.ELECTRO;

                        // Overload / Superconduct / Swirl consume gauge
                        // Standard Transformative: 1x consumption of Aura?
                        // Actually Overload consumes 1U of Electro and Pyro.
                        // We need to reduce the Aura (aura) by 1.0 * TriggerGU? Or purely consumption
                        // based on reaction?
                        // Wiki: "Overload consumes both the Pyro and Electro gauges."
                        // Consumption amount: 1U of Pyro consumes 1U of Electro?
                        // Simplified: Reduce aura by trigger gauge units.
                        if (!result.getName().equals("Electro-Charged")) {
                            this.getEnemy().reduceAura(aura, action.getGaugeUnits());
                            // If Overload, we also conceptually consume the Trigger, but since Trigger acts
                            // as source for multiple, we let it persist for the loop (Simultaneous).
                        }

                        double res = enemy.getRes(reactionElement.getBonusStatType());
                        double resShred = stats.get(model.type.StatType.RES_SHRED);
                        switch (reactionElement) {
                            case PYRO:
                                resShred += stats.get(model.type.StatType.PYRO_RES_SHRED);
                                break;
                            case HYDRO:
                                resShred += stats.get(model.type.StatType.HYDRO_RES_SHRED);
                                break;
                            case CRYO:
                                resShred += stats.get(model.type.StatType.CRYO_RES_SHRED);
                                break;
                            case ELECTRO:
                                resShred += stats.get(model.type.StatType.ELECTRO_RES_SHRED);
                                break;
                            case ANEMO:
                                resShred += stats.get(model.type.StatType.ANEMO_RES_SHRED);
                                break;
                            case GEO:
                                resShred += stats.get(model.type.StatType.GEO_RES_SHRED);
                                break;
                            case DENDRO:
                                resShred += stats.get(model.type.StatType.DENDRO_RES_SHRED);
                                break;
                            case PHYSICAL:
                                resShred += stats.get(model.type.StatType.PHYS_RES_SHRED);
                                break;
                        }
                        double resFactor = mechanics.formula.DamageCalculator.calculateResMulti(res, resShred);

                        double reactBonus = 0.0;
                        if (result.getName().equals("Electro-Charged")) {
                            reactBonus = stats.get(model.type.StatType.ELECTRO_CHARGED_DMG_BONUS);
                        }
                        // Add other reactions here as needed

                        double transDmg = result.getTransformDamage() * (1.0 + reactBonus) * resFactor;

                        // When Lunar mode converts EC, use Lunar-Charged formula for initial trigger
                        boolean isLunar = (result.getName().equals("Electro-Charged") && currentMoonsign != Moonsign.NONE);
                        String reactionLabel = isLunar ? "Lunar-Charged" : result.getName();

                        double triggerDmg;
                        if (isLunar) {
                            java.util.List<Double> potDmgs = new java.util.ArrayList<>();
                            for (model.entity.Character mem : getPartyMembers()) {
                                model.stats.StatsContainer ms = mem.getEffectiveStats(currentTime);
                                double baseBonus = ms.get(model.type.StatType.LUNAR_BASE_BONUS);
                                double uBonus = ms.get(model.type.StatType.LUNAR_UNIQUE_BONUS)
                                        + ms.get(model.type.StatType.LUNAR_CHARGED_DMG_BONUS)
                                        + ms.get(model.type.StatType.ELECTRO_CHARGED_DMG_BONUS)
                                        + ms.get(model.type.StatType.LUNAR_REACTION_DMG_BONUS_ALL);
                                double colMult = 1.0 + ms.get(model.type.StatType.LUNAR_MULTIPLIER);
                                double memEm = ms.get(model.type.StatType.ELEMENTAL_MASTERY);
                                double emBonus = (2.78 * memEm) / (memEm + 1400.0);
                                double cr = ms.get(model.type.StatType.CRIT_RATE);
                                double cd = ms.get(model.type.StatType.CRIT_DMG);
                                double critMult = 1.0 + (Math.min(cr, 1.0) * cd);
                                double resVal = enemy.getRes(model.type.StatType.ELECTRO_DMG_BONUS);
                                double rMult;
                                if (resVal < 0) rMult = 1.0 - (resVal / 2.0);
                                else if (resVal < 0.75) rMult = 1.0 - resVal;
                                else rMult = 1.0 / (1.0 + 4.0 * resVal);
                                potDmgs.add(1.8 * 1446.85 * (1.0 + baseBonus) * (1.0 + uBonus) * (1.0 + emBonus) * critMult * rMult * colMult);
                            }
                            potDmgs.sort(java.util.Collections.reverseOrder());
                            double[] weights = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };
                            triggerDmg = 0.0;
                            for (int i = 0; i < potDmgs.size() && i < 4; i++) {
                                triggerDmg += potDmgs.get(i) * weights[i];
                            }
                        } else {
                            triggerDmg = transDmg;
                        }

                        if (enableLogging)
                            System.out
                                    .println(String.format("   [Reaction] %s on %s -> %s Damage: %,.0f", trigger, aura,
                                            reactionLabel, triggerDmg));
                        recordDamage(charName, triggerDmg);

                        // The instruction implies this notifyReaction should be here, but the original
                        // code had it earlier.
                        // Keeping it here as per the instruction's provided snippet.
                        // notifyReaction(result, c); // This line was moved from above.

                        visualization.VisualLogger.getInstance().log(currentTime, charName, reactionLabel, triggerDmg,
                                reactionLabel, triggerDmg, enemy.getAuraMap());

                        // Aura Management
                        if (result.getName().equals("Electro-Charged")) {
                            if (isLunar) {
                                // Refresh Thundercloud state: 6s from last Lunar-Charged trigger
                                thundercloudEndTime = currentTime + 6.0;
                            }

                            if (!isECTimerRunning) {
                                isECTimerRunning = true;
                                registerEvent(new simulation.event.TimerEvent() {
                                    private double nextTick = currentTime + (isLunar ? 2.0 : 1.0); // Lunar: 2s, EC: 1s

                                    @Override
                                    public void tick(CombatSimulator sim) {
                                        // Continuation check: Lunar uses Thundercloud timer, standard EC uses aura state
                                        boolean shouldTick = isLunar
                                                ? (currentTime <= thundercloudEndTime)
                                                : (sim.getEnemy().getAuraUnits(model.type.Element.HYDRO) > 0 &&
                                                        sim.getEnemy().getAuraUnits(model.type.Element.ELECTRO) > 0);
                                        if (shouldTick) {

                                            // Deal EC / Lunar Damage
                                            String label = "Electro-Charged Tick";
                                            double finalDmg = 0;

                                            if (isLunar) {
                                                label = "Lunar-Charged Reaction";

                                                // 1. Base Bonus (From Team Buffs)
                                                // Already applied to 's' via 'getEffectiveStats' because we registered
                                                // them as Team Buffs.
                                                // BUT we need the base bonus for *calculation* here.
                                                // Since we iterate party members 'c', 'c' has the Team Buff.
                                                // So 'baseBonus' is c.getEffectiveStats().get(LUNAR_BASE_BONUS).
                                                // We don't need to calculate it manually!

                                                // Logic simplified:
                                                // Check for each member individually.

                                                // 2. Calculate Damage for EACH Party Member
                                                java.util.List<Double> potentialDamages = new java.util.ArrayList<>();

                                                for (model.entity.Character c : getPartyMembers()) {
                                                    model.stats.StatsContainer s = c.getEffectiveStats(currentTime);

                                                    // Formula: 1.8 * LvMult * (1+Base) * (1+Unique) * (1+EM) * Crit *
                                                    // Res
                                                    double lvMult = 1446.85; // Lv 90 Fixed

                                                    // Base Bonus from Stats
                                                    double baseBonus = s.get(model.type.StatType.LUNAR_BASE_BONUS);

                                                    // Unique Bonus: Flins +20% (Self Buff refactored)
                                                    double uniqueBonus = s.get(model.type.StatType.LUNAR_UNIQUE_BONUS);

                                                    // Reaction Bonuses (Weapon/Artifacts)
                                                    uniqueBonus += s.get(model.type.StatType.LUNAR_CHARGED_DMG_BONUS);
                                                    uniqueBonus += s.get(model.type.StatType.ELECTRO_CHARGED_DMG_BONUS);
                                                    uniqueBonus += s
                                                            .get(model.type.StatType.LUNAR_REACTION_DMG_BONUS_ALL); // Added
                                                                                                                    // Columbina
                                                                                                                    // Burst

                                                    // Columbina Passive Multiplier
                                                    double columbinaMult = 1.0
                                                            + s.get(model.type.StatType.LUNAR_MULTIPLIER);

                                                    // EM Bonus (Reaction)
                                                    double em = s.get(model.type.StatType.ELEMENTAL_MASTERY);
                                                    double emBonus = (2.78 * em) / (em + 1400.0);

                                                    // Crit
                                                    double cr = s.get(model.type.StatType.CRIT_RATE);
                                                    double cd = s.get(model.type.StatType.CRIT_DMG);
                                                    double critMult = 1.0 + (Math.min(cr, 1.0) * cd);

                                                    // Res (Enemy vs Electro? Lunar is Electro?)
                                                    // "Lunar-Charged consumes Hydro/Electro".
                                                    // Usually EC deals Electro DMG.
                                                    // Using Electro Res.
                                                    double resMult = 0.9;
                                                    double resVal = sim.getEnemy()
                                                            .getRes(model.type.StatType.ELECTRO_DMG_BONUS);
                                                    if (resVal < 0)
                                                        resMult = 1.0 - (resVal / 2.0);
                                                    if (resVal < 0)
                                                        resMult = 1.0 - (resVal / 2.0);
                                                    else if (resVal < 0.75)
                                                        resMult = 1.0 - resVal;
                                                    else
                                                        resMult = 1.0 / (1.0 + 4.0 * resVal);

                                                    double dmg = 1.8 * lvMult * (1.0 + baseBonus) * (1.0 + uniqueBonus)
                                                            * (1.0 + emBonus) * critMult * resMult * columbinaMult;
                                                    potentialDamages.add(dmg);
                                                }

                                                // 3. Sort Descending
                                                potentialDamages.sort(java.util.Collections.reverseOrder());

                                                // 4. Weighted Sum
                                                // Weights: 1, 0.5, 1/12, 1/12
                                                double[] weights = { 1.0, 0.5, 1.0 / 12.0, 1.0 / 12.0 };
                                                for (int i = 0; i < potentialDamages.size() && i < 4; i++) {
                                                    finalDmg += potentialDamages.get(i) * weights[i];
                                                }

                                            } else {
                                                // Standard EC
                                                finalDmg = transDmg;
                                            }

                                            if (enableLogging)
                                                System.out.println(
                                                        String.format("   [DoT] %s Damage: %,.0f", label, finalDmg));

                                            sim.recordDamage("Thundercloud", finalDmg);
                                            visualization.VisualLogger.getInstance().log(currentTime, "Thundercloud", label,
                                                    finalDmg, label, finalDmg, sim.getEnemy().getAuraMap());

                                            // Notify listeners with the strike damage so passives can react
                                            if (isLunar) {
                                                sim.notifyReaction(
                                                    mechanics.reaction.ReactionResult.transform(finalDmg, "Thundercloud-Strike"),
                                                    sim.getActiveCharacter());
                                            }

                                            sim.getEnemy().reduceAura(model.type.Element.HYDRO, 0.4);
                                            sim.getEnemy().reduceAura(model.type.Element.ELECTRO, 0.4);

                                            nextTick += (isLunar ? 2.0 : 1.0);
                                        } else {
                                            isECTimerRunning = false;
                                            nextTick = Double.MAX_VALUE;
                                        }
                                    }

                                    @Override
                                    public double getNextTickTime() {
                                        return nextTick;
                                    }

                                    @Override
                                    public boolean isFinished(double time) {
                                        return nextTick == Double.MAX_VALUE || time > 1000;
                                    }
                                });
                            }
                            // Apply Trigger Gauge (Supplement)
                            enemy.setAura(trigger, action.getGaugeUnits());
                        } else {
                            enemy.setAura(aura, 0);
                        }
                    }
                }
            }

            if (!reactionTriggered) {
                // Prevent Physical, Anemo, Geo from persisting as Aura
                // (Simplified: Only Pyro, Hydro, Cryo, Electro, Dendro stick)
                if (trigger != model.type.Element.PHYSICAL &&
                        trigger != model.type.Element.ANEMO &&
                        trigger != model.type.Element.GEO) {
                    enemy.setAura(trigger, action.getGaugeUnits());
                    if (enableLogging)
                        System.out.println(
                                String.format("   [Aura] Applied %s (%.1f U)", trigger, action.getGaugeUnits()));
                }
            }

        } else {
            if (enableLogging)
                System.out.println(String.format("   [ICD] Applied blocked (%s)", action.getICDTag()));
        }

        if (enableLogging)
            System.out.println(String.format("[T=%.1f] %s uses %s", currentTime, charName, action.getName()));

        List<Buff> activeBuffs = getApplicableBuffs(c);

        // System.out.println("[CS_PRE_CALC] Calling calculate for " + c.getName() + "
        // with "
        // + (activeBuffs == null ? "null" : activeBuffs.size()) + " buffs.");

        // Calculate Damage
        double dmg = mechanics.formula.DamageCalculator.calculateDamage(c, enemy, action, activeBuffs, currentTime,
                reactionMulti, this);
        // Trigger Weapon onDamage (e.g. Wolf-Fang, Favonius)
        if (c.getWeapon() != null) {
            c.getWeapon().onDamage(c, action, currentTime, this);
        }

        // NA energy generation: Normal/Charged Attacks have a weapon-type-dependent
        // probability of generating 1 flat Energy (not affected by Energy Recharge).
        // Expected value is used instead of random rolls.
        if (action.getActionType() == model.type.ActionType.NORMAL
                || action.getActionType() == model.type.ActionType.CHARGE) {
            if (c.getWeapon() != null) {
                double naEnergy = c.getWeapon().getExpectedNAEnergyPerHit();
                if (naEnergy > 0) {
                    c.receiveFlatEnergy(naEnergy);
                }
            }
        }

        if (enableLogging)
            System.out.println(String.format("   -> Damage: %,.0f", dmg));
        recordDamage(charName, dmg);

        // Notify Artifacts (onDamage)
        if (c.getArtifacts() != null) {
            for (model.entity.ArtifactSet a : c.getArtifacts()) {
                if (a != null) {
                    a.onDamage(this, action, dmg, c);
                }
            }
        }

        visualization.VisualLogger.getInstance().log(currentTime, charName, action.getName(), dmg,
                (reactionMulti > 1.0 ? "Amp x" + String.format("%.2f", reactionMulti) : "None"), 0.0,
                enemy.getAuraMap(), action.getDebugFormula());
    }
}
