package model.character;

import model.entity.Character;
import model.entity.Weapon;
import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.ICDType;
import model.type.ICDTag;
import model.type.ActionType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.event.PeriodicDamageEvent;
import mechanics.buff.SimpleBuff;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;

/**
 * Custom "Lunar" Electro polearm character implementation.
 *
 * <p><b>Mechanics overview:</b>
 * <ul>
 *   <li><b>Manifest Flame form (Skill — out of form)</b> — enters a 10 s form that
 *       infuses Normal Attacks with Electro and grants 1 Electro particle per NA
 *       (2 s CD).</li>
 *   <li><b>Northland Spearstorm (Skill — in form)</b> — Electro Skill hit with a 6 s CD;
 *       activates the 6 s Thunderous Symphony state.</li>
 *   <li><b>Ancient Ritual: Cometh the Night (Burst, cost 80)</b> — initial + 2–4 middle
 *       + final Electro hits; extra middle hits when Thundercloud is active.</li>
 *   <li><b>Thunderous Symphony (Burst, cost 30)</b> — fired while Thunderous Symphony
 *       is active; deals main + optional Additional hit when Thundercloud is active.</li>
 *   <li><b>Whispering Flame passive</b> — grants EM equal to 8% of ATK (max 160) and
 *       {@code LUNAR_UNIQUE_BONUS +20%}.</li>
 *   <li><b>Lunar Base Bonus team buff</b> — provides {@code LUNAR_BASE_BONUS} scaling
 *       with ATK: {@code min(0.14, ATK / 100 * 0.007)}.</li>
 * </ul>
 *
 * <p>Flins is a Lunar character ({@link #isLunarCharacter()} returns {@code true}).
 * Burst energy cost switches between 80 (standard) and 30 (Thunderous Symphony active).
 */
public class Flins extends Character {

    private int normalAttackStep = 0;

    // States
    private double manifestFlameEndTime = -1;
    private double thunderousSymphonyEndTime = -1;
    private double northlandSpearstormNextTime = 0;
    private double normalAttackParticleNextTime = 0; // 2s CD on Manifest Flame NA particle

    /**
     * Constructs Flins with the given weapon and artifact set.
     * Initialises level-90 base stats (loaded from CSV), Electro element,
     * and cooldowns.
     *
     * @param weapon    equipped weapon
     * @param artifacts equipped artifact set
     */
    public Flins(Weapon weapon, ArtifactSet artifacts) {
        super();
        this.name = "Flins";

        // Stats (Lv 90)
        baseStats.set(StatType.BASE_HP,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base HP", 12491));
        baseStats.set(StatType.BASE_ATK,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base ATK", 352));
        baseStats.set(StatType.BASE_DEF,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base DEF", 809));
        baseStats.set(StatType.CRIT_DMG,
                mechanics.data.TalentDataManager.getInstance().get(this.name, "Base CD", 0.384)); // 38.4%

        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.element = Element.ELECTRO;

        // Defaults
        this.constellation = 0;

        this.skillCD = 16.0;
        this.burstCD = 20.0;
    }

    /**
     * Returns the burst energy cost: 30 when Thunderous Symphony is active,
     * otherwise 80.
     */
    @Override
    public double getEnergyCost() {
        return thunderousSymphonyActive ? 30 : 80;
    }

    private boolean thunderousSymphonyActive = false;

    /**
     * Returns {@code true} if Manifest Flame form is currently active.
     *
     * @param currentTime current simulation time in seconds
     */
    private boolean isManifestFlameActive(double currentTime) {
        return currentTime < manifestFlameEndTime;
    }

    /**
     * Returns whether Thunderous Symphony state is currently active and
     * updates the cached {@code thunderousSymphonyActive} flag.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} if Thunderous Symphony has not yet expired
     */
    // Overload for checks with time
    public boolean isThunderousSymphonyActive(double currentTime) {
        boolean active = currentTime < thunderousSymphonyEndTime;
        this.thunderousSymphonyActive = active;
        return active;
    }

    /**
     * Returns the remaining skill cooldown.  While in Manifest Flame form the
     * cooldown is gated by Northland Spearstorm's 6 s CD; otherwise the
     * standard form-entry CD applies.
     *
     * @param currentTime current simulation time in seconds
     * @return remaining cooldown in seconds
     */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isManifestFlameActive(currentTime)) {
            // In form: gated by Northland Spearstorm's 6s CD
            return Math.max(0, northlandSpearstormNextTime - currentTime);
        }
        // Out of form: gated by form entry CD
        return super.getSkillCDRemaining(currentTime);
    }

    /**
     * Returns the remaining burst cooldown.  When Thunderous Symphony is active
     * the burst can be cast immediately (returns 0).
     *
     * @param currentTime current simulation time in seconds
     * @return remaining cooldown in seconds
     */
    @Override
    public double getBurstCDRemaining(double currentTime) {
        if (isThunderousSymphonyActive(currentTime)) {
            // Symphony burst has no separate CD; usable whenever Symphony is active
            return 0.0;
        }
        return super.getBurstCDRemaining(currentTime);
    }

    /**
     * Returns {@code false}; Flins has no persistent burst state tracked by
     * the base class.
     */
    @Override
    public boolean isBurstActive(double currentTime) {
        return false;
    }

    /**
     * Returns {@code true}; Flins is a Lunar character and benefits from
     * Lunar synergy buffs.
     */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /**
     * Applies the Whispering Flame passive: grants EM equal to 8% of total ATK
     * (capped at 160) and {@code LUNAR_UNIQUE_BONUS +20%}.
     *
     * @param stats the stats container to modify
     */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Whispering Flame: EM increased by 8% of ATK (Max 160)
        double atk = stats.getTotalAtk();
        double bonusEm = Math.min(160.0, atk * 0.08);
        stats.add(StatType.ELEMENTAL_MASTERY, bonusEm);

        // Unique Bonus (Self)
        stats.add(StatType.LUNAR_UNIQUE_BONUS, 0.20);
    }

    /**
     * Handles action keys dispatched by the combat simulator.
     *
     * <p>Supported keys:
     * <ul>
     *   <li>{@code "skill"} / {@code "E"} — enters Manifest Flame or casts Northland Spearstorm.</li>
     *   <li>{@code "burst"} / {@code "Q"} — casts the standard burst or Thunderous Symphony.</li>
     *   <li>{@code "attack"} — advances the normal attack combo.</li>
     *   <li>{@code "dash"} — resets the normal attack step and advances time by 0.4 s.</li>
     * </ul>
     *
     * @param key action identifier string
     * @param sim the combat simulator context
     */
    @Override
    public void onAction(String key, CombatSimulator sim) {
        switch (key) {
            case "skill":
            case "E":
                if (!isManifestFlameActive(sim.getCurrentTime())) {
                    markSkillUsed(sim.getCurrentTime());
                    skill_enterForm(sim);
                } else {
                    skill_spearstorm(sim); // CD enforced by CombatSimulator
                }
                break;
            case "burst":
            case "Q":
                markBurstUsed(sim.getCurrentTime());
                if (isThunderousSymphonyActive(sim.getCurrentTime())) {
                    burst_symphony(sim);
                } else {
                    burst_standard(sim);
                }
                break;
            case "attack":
                normalAttack(sim);
                break;
            case "dash":
                normalAttackStep = 0;
                sim.advanceTime(0.4);
                break;
        }
    }

    /**
     * Executes the current normal attack step.  In Manifest Flame form attacks
     * deal Electro damage; outside form they deal Physical damage.  N4 fires
     * two hits at the same motion value.
     *
     * @param sim the combat simulator context
     */
    private void normalAttack(CombatSimulator sim) {
        boolean inForm = isManifestFlameActive(sim.getCurrentTime());
        String key = "N" + (normalAttackStep + 1);
        String name = "Flins " + key + (inForm ? " (Manifest)" : "");
        String lookupKey = inForm ? "Form " + key : key;

        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, lookupKey, 0.8); // Default fallback
        double dur = 0.3;

        switch (normalAttackStep) {
            case 0:
                dur = 0.2;
                break;
            case 1:
                dur = 0.2;
                break;
            case 2:
                dur = 0.2;
                break;
            case 3:
                dur = 0.6;
                break;
            case 4:
                dur = 0.6;
                break;
        }

        // Note: N4 is 2 hits.
        boolean isMultiHit = (normalAttackStep == 3); // 0,1,2,3(N4)

        // Element: In Form -> Electro (Cannot be overridden), Out Form -> Physical
        Element dmgElement = inForm ? Element.ELECTRO : Element.PHYSICAL;

        AttackAction hit = new AttackAction(name, mv, dmgElement, StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS, 0.0, ActionType.NORMAL);
        hit.setICD(ICDType.Standard, ICDTag.NormalAttack, 1.0);
        hit.setAnimationDuration(dur);

        double attackTime = sim.getCurrentTime();
        if (isMultiHit) {
            // First Hit
            sim.performAction(this.name, hit);
            // Second Hit (Same MV)
            sim.performAction(this.name, hit);
        } else {
            sim.performAction(this.name, hit);
        }

        // Manifest Flame NA: generate 1 Electro particle with 2s CD
        if (inForm && attackTime >= normalAttackParticleNextTime) {
            EnergyManager.distributeParticles(Element.ELECTRO, 1.0, ParticleType.PARTICLE, sim);
            normalAttackParticleNextTime = attackTime + 2.0;
        }

        normalAttackStep++;
        if (normalAttackStep >= 5)
            normalAttackStep = 0;

        // sim.advanceTime(0.3); // Handled by hit.setAnimationDuration
    }

    /**
     * Enters Manifest Flame form for 10 s without dealing damage.
     *
     * @param sim the combat simulator context
     */
    private void skill_enterForm(CombatSimulator sim) {
        // Just enters form.
        this.manifestFlameEndTime = sim.getCurrentTime() + 10.0;
        if (sim.isLoggingEnabled())
            System.out.println("Flins entering Manifest Flame form.");

        sim.performAction(this.name, new AttackAction("Enter Form", 0, Element.PHYSICAL, StatType.BASE_ATK, null, 0.3));

    }

    /**
     * Casts Northland Spearstorm: a single Electro Skill hit that activates
     * the 6 s Thunderous Symphony state and sets the next allowed cast time.
     *
     * @param sim the combat simulator context
     */
    private void skill_spearstorm(CombatSimulator sim) {
        // Northland Spearstorm
        double mv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Spearstorm DMG", 3.0328);

        AttackAction hit = new AttackAction("Northland Spearstorm", mv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS, 0.0, false, ActionType.SKILL);
        hit.setICD(ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        hit.setAnimationDuration(0.6);

        // CD and Symphony state start from cast time, not after animation ends
        double castTime = sim.getCurrentTime();
        this.northlandSpearstormNextTime = castTime + 6.0;
        this.thunderousSymphonyEndTime = castTime + 6.0;
        this.thunderousSymphonyActive = true;

        sim.performAction(this.name, hit);
        if (sim.isLoggingEnabled())
            System.out.println("Flins enters Thunderous Symphony state.");
    }

    /**
     * Calculates the Lunar Base Damage bonus from Flins' ATK:
     * {@code min(0.14, ATK / 100 * 0.007)}.
     *
     * @param sim the combat simulator context
     * @return the base bonus multiplier
     */
    private double getLunarBaseBonus(CombatSimulator sim) {
        // Passive 3: Increase Base DMG by 0.7% per 100 ATK (Max 14%)
        // Uses Flins' ATK
        double atk = this.getEffectiveStats(sim.getCurrentTime()).getTotalAtk();
        return Math.min(0.14, (atk / 100.0) * 0.007);
    }

    /**
     * Casts the standard 80-cost burst "Ancient Ritual: Cometh the Night".
     * Fires an initial hit, then schedules 2–4 middle hits and a final hit
     * with staggered delays.  Extra middle hits are added when Thundercloud is active.
     *
     * @param sim the combat simulator context
     */
    private void burst_standard(CombatSimulator sim) {
        // Ancient Ritual: Cometh the Night (Cost 80)
        double initialMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst Initial", 4.417);
        double middleMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst Middle", 0.276);
        double finalMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Burst Final", 1.988);

        // Initial
        AttackAction hit = new AttackAction("Cometh the Night (Initial)", initialMv, Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        hit.setLunarConsidered(true);
        hit.setAnimationDuration(1.5);
        sim.performAction(this.name, hit);

        // Delayed Hits
        int middleCount = 2 + (isThundercloudActive(sim) ? 2 : 0);

        double midMv = middleMv;
        double finMv = finalMv;

        for (int i = 0; i < middleCount; i++) {
            AttackAction mid = new AttackAction("Cometh the Night (Middle)", midMv, Element.ELECTRO, StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
            mid.setLunarConsidered(true);

            final int idx = i;
            sim.registerEvent(new simulation.event.TimerEvent() {
                boolean done = false;

                @Override
                public double getNextTickTime() {
                    return done ? Double.MAX_VALUE : sim.getCurrentTime() + 1.0 + (idx * 0.3);
                }

                @Override
                public void tick(CombatSimulator s) {
                    s.performAction(name, mid);
                    done = true;
                }

                @Override
                public boolean isFinished(double t) {
                    return done;
                }
            });
        }

        // Final Hit
        AttackAction fin = new AttackAction("Cometh the Night (Final)", finMv, Element.ELECTRO, StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        fin.setLunarConsidered(true);
        sim.registerEvent(new simulation.event.TimerEvent() {
            boolean done = false;

            @Override
            public double getNextTickTime() {
                return done ? Double.MAX_VALUE : sim.getCurrentTime() + 1.0 + (middleCount * 0.3) + 0.5;
            }

            @Override
            public void tick(CombatSimulator s) {
                s.performAction(name, fin);
                done = true;
            }

            @Override
            public boolean isFinished(double t) {
                return done;
            }
        });
    }

    /**
     * Casts the 30-cost Thunderous Symphony burst.  Fires the main hit and,
     * when Thundercloud is active, schedules an Additional hit 0.1 s later.
     *
     * @param sim the combat simulator context
     */
    private void burst_symphony(CombatSimulator sim) {
        // Thunderous Symphony (Cost 30)
        double mainMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Symphony DMG", 1.215);
        double addMv = mechanics.data.TalentDataManager.getInstance().get(this.name, "Symphony Additional", 1.767);

        AttackAction hit = new AttackAction("Thunderous Symphony", mainMv, Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
        hit.setLunarConsidered(true);
        hit.setAnimationDuration(1.0);

        // Symphony Additional fires 0.1s after Symphony DMG when Thundercloud is active
        if (isThundercloudActive(sim)) {
            AttackAction extra = new AttackAction("Thunderous Symphony (Additional)", addMv,
                    Element.ELECTRO, StatType.BASE_ATK,
                    StatType.BURST_DMG_BONUS, 0.0, false, ActionType.BURST);
            extra.setLunarConsidered(true);
            final double additionalTime = sim.getCurrentTime() + 0.1;
            final String charName = this.name;
            sim.registerEvent(new simulation.event.TimerEvent() {
                boolean done = false;

                @Override
                public double getNextTickTime() {
                    return done ? Double.MAX_VALUE : additionalTime;
                }

                @Override
                public void tick(CombatSimulator s) {
                    s.performAction(charName, extra);
                    done = true;
                }

                @Override
                public boolean isFinished(double t) {
                    return done;
                }
            });
        }

        sim.performAction(this.name, hit);
    }

    /**
     * Queries the simulator for the Thundercloud state.
     * Thundercloud is a 6 s timed state set by a Lunar-Charged trigger.
     *
     * @param sim the combat simulator context
     * @return {@code true} if Thundercloud is currently active
     */
    private boolean isThundercloudActive(CombatSimulator sim) {
        // Thundercloud is a 6s timed state from last Lunar-Charged trigger, tracked by sim
        return sim.isThundercloudActive();
    }

    /**
     * Returns the permanent team buffs provided by Flins.
     *
     * <p>Includes a passive {@code LUNAR_BASE_BONUS} buff scaling with ATK:
     * {@code min(0.14, ATK / 100 * 0.007)}.
     *
     * @return list containing the Lunar Base Bonus buff
     */
    @Override
    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        java.util.List<mechanics.buff.Buff> buffs = new java.util.ArrayList<>();
        buffs.add(new mechanics.buff.Buff("Flins: Lunar Base Bonus", Double.MAX_VALUE, 0) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                double atk = Flins.this.getStructuralStats(currentTime).getTotalAtk();
                double bonus = Math.min(0.14, (atk / 100.0) * 0.007);
                stats.add(StatType.LUNAR_BASE_BONUS, bonus);
            }
        });
        return buffs;
    }

}
