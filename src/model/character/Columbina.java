package model.character;

import mechanics.buff.BuffId;
import mechanics.formula.DamageCalculator;
import model.entity.CharacterTeamBuffProvider;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDType;
import model.type.ICDTag;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.event.TimerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom "Lunar" Hydro character implementation.
 *
 * <p><b>Mechanics overview:</b>
 * <ul>
 *   <li><b>Gravity / Gravity Ripple (Skill)</b> — summons a periodic Hydro field that
 *       ticks every 2 s for 25 s and generates particles.</li>
 *   <li><b>Moonlit Melancholy (Burst)</b> — HP-scaling Hydro nuke; activates a 20 s
 *       Lunar Domain that adds {@code LUNAR_REACTION_DMG_BONUS_ALL}.</li>
 *   <li><b>Gravity accumulation</b> — Lunar reactions (Charged, Bloom, Crystallize) near
 *       an active ripple accumulate Gravity in increments of 20 (max 60); at cap the
 *       dominant reaction type triggers an Interference attack.</li>
 *   <li><b>Verdant / Moonridge Dew</b> — Bloom reactions grant Verdant Dew stacks;
 *       Bloom inside an active Lunar Domain grants Moonridge Dew stacks (up to 3 per 18 s window).
 *       Consuming a Dew on a Charged Attack fires a special Moondew Cleanse hit sequence.</li>
 *   <li><b>Lunar Multiplier passive</b> — grants {@code LUNAR_MULTIPLIER} to the whole
 *       party as a function of Columbina's total HP (capped at +7%).</li>
 *   <li><b>4th passive — Lunar Domain synergies</b>:
 *       Lunar-Charged: Thundercloud strikes have a 33% chance for an extra strike;
 *       Lunar-Bloom: Moonridge Dew gain rate increased to up to 3 per 18 s;
 *       Lunar-Crystallize (Moondrift Harmony): each Moondrift has a 33% chance for
 *       an extra attack.</li>
 * </ul>
 *
 * <p><b>Constellation 1 — Radiance Over Blossoms and Peaks:</b>
 * <ul>
 *   <li>Skill (Eternal Tides) immediately triggers Gravity Interference (15 s CD).</li>
 *   <li>Moonsign Ascendant Gleam: on each Interference trigger, applies a bonus based on
 *       the dominant Gravity type — Lunar-Charged: active character +6 Energy;
 *       Lunar-Bloom: 8 s interruption resistance; Lunar-Crystallize: 8 s Rainsea Shield
 *       (12% Columbina Max HP, Hydro ×250%).</li>
 *   <li>All party members' Lunar Reaction DMG permanently +1.5%.</li>
 * </ul>
 *
 * <p><b>Constellation 2 — Tides of Lunar Gravity:</b>
 * <ul>
 *   <li>Rate of accumulating Gravity increases by 34% (cooldown reduced from 2 s to ~1.49 s).</li>
 *   <li>On Gravity Interference, Columbina gains Lunar Brilliance (+40% Max HP) for 8 s.</li>
 *   <li>Moonsign Ascendant Gleam: while Lunar Brilliance is already active at the next
 *       Interference trigger, the dominant reaction type grants the active character a stat
 *       bonus for 8 s scaled from Columbina's Max HP —
 *       Lunar-Charged: ATK +1% HP; Lunar-Bloom: EM +0.35% HP;
 *       Lunar-Crystallize: DEF +1% HP.</li>
 *   <li>All nearby party members' Lunar Reaction DMG permanently +7%.</li>
 * </ul>
 *
 * <p>Columbina is a Lunar character ({@link #isLunarCharacter()} returns {@code true}) and
 * registers herself as a {@link CombatSimulator.ReactionListener} on first action.
 */
public class Columbina extends Character implements CharacterTeamBuffProvider, ReactionAwareCharacter {
    // Resources
    private int gravity = 0;
    private static final int MAX_GRAVITY = 60;

    // Dews
    private int verdantDew = 0;
    private int moonridgeDew = 0;
    private static final int MAX_DEW = 3;

    // States
    private double rippleEndTime = 0;
    private double domainEndTime = 0;

    // Constellation
    private int constellation = 0;
    private double c1SkillCDNextTime = 0; // 15s CD for C1 on-skill interference trigger
    private double lunarBrillianceEndTime = -1; // C2: Lunar Brilliance expiry time

    // Gravity Accumulation Logic
    private Map<AttackAction.LunarReactionType, Integer> gravityByType = new HashMap<>();
    private double lastGravityTime = 0;
    // 4th Passive: Moonridge Dew window (up to 3 per 18s)
    private double dewWindowStart = -18.0;
    private int dewsInWindow = 0;

    /**
     * Constructs Columbina with the given weapon and artifact set.
     * Initialises level-90 base stats, Hydro element, and cooldowns.
     *
     * @param weapon    equipped weapon
     * @param artifacts equipped artifact set
     */
    public Columbina(model.entity.Weapon weapon, model.entity.ArtifactSet artifacts) {
        super();
        this.name = "Columbina";
        this.characterId = CharacterId.COLUMBINA;
        this.weapon = weapon;
        this.artifacts = new model.entity.ArtifactSet[] { artifacts };
        this.element = Element.HYDRO;

        // Stats (Lv 90)
        baseStats.set(model.type.StatType.BASE_HP, 14695);
        baseStats.set(model.type.StatType.BASE_ATK, 96);
        baseStats.set(model.type.StatType.BASE_DEF, 515);
        baseStats.set(model.type.StatType.CRIT_RATE, 0.242);
        baseStats.set(model.type.StatType.CRIT_DMG, 0.50);
        baseStats.set(model.type.StatType.ENERGY_RECHARGE, 1.0);

        setSkillCD(17.0);
        setBurstCD(15.0);

        // Constellation (read from CSV; defaults to 0)
        this.constellation = (int) mechanics.data.TalentDataManager.getInstance()
                .get(this.name, "Constellation", 0);
    }

    /**
     * Returns {@code true}; Columbina is a Lunar character and benefits from
     * Lunar synergy buffs and the {@code LUNAR_MULTIPLIER} final multiplier.
     */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    // Lazy Registration flag
    private boolean registeredListener = false;

    /**
     * Returns the burst energy cost (60).
     */
    @Override
    public double getEnergyCost() {
        return 60;
    }

    /**
     * No static passive stat modifications for Columbina (handled dynamically).
     */
    @Override
    public void applyPassive(model.stats.StatsContainer stats) {
        // Passive logic
    }

    /**
     * Retrieves a talent multiplier value from the CSV data manager.
     *
     * @param key talent key string (e.g. "Skill DMG")
     * @return the multiplier value, or {@code 0.0} if not found
     */
    private double getMultiplier(String key) {
        return mechanics.data.TalentDataManager.getInstance().get(this.name, key, 0.0);
    }

    /**
     * Handles typed action requests dispatched by the combat simulator.
     * Registers the reaction listener on first use.
     *
     * <p>Supported actions:
     * <ul>
     *   <li>{@link CharacterActionKey#CHARGE} — performs a Moondew Cleanse if Dew is available,
     *       otherwise a standard Charged Attack.</li>
     *   <li>{@link CharacterActionKey#SKILL} — casts Eternal Tides, starts the Gravity Ripple periodic
     *       event, and generates 4 Hydro particles.</li>
     *   <li>{@link CharacterActionKey#BURST} — casts Moonlit Melancholy and applies the 20 s Lunar Domain
     *       reaction bonus team buff.</li>
     *   <li>{@link CharacterActionKey#NORMAL} — fires Columbina's current normal attack.</li>
     * </ul>
     *
     * @param request typed action request
     * @param sim       the combat simulator context
     */
    @Override
    public void onAction(CharacterActionRequest request, CombatSimulator sim) {
        if (!registeredListener) {
            sim.addReactionListener(this);
            registeredListener = true;
        }

        double currentTime = sim.getCurrentTime();

        switch (request.getKey()) {
            case CHARGE:
                if (moonridgeDew > 0) {
                    performSpecialCA(sim, true);
                    moonridgeDew--;
                } else if (verdantDew > 0) {
                    performSpecialCA(sim, false);
                    verdantDew--;
                } else {
                    // Standard CA
                    AttackAction ca = new AttackAction(
                            "Columbina Charged",
                            getMultiplier("Charged Attack"),
                            Element.HYDRO,
                            model.type.StatType.BASE_ATK,
                            null);
                    ca.setAnimationDuration(1.5); // Estimate
                    sim.performAction("Columbina", ca);
                }
                break;
            case SKILL:
                markSkillUsed(currentTime);
                // Summon Gravity Ripple
                AttackAction skill = new AttackAction(
                        "Eternal Tides",
                        getMultiplier("Skill DMG"),
                        Element.HYDRO,
                        model.type.StatType.BASE_HP,
                        null);
                skill.setICD(ICDType.Standard, ICDTag.Columbina_Cast, 1.0);
                skill.setAnimationDuration(0.8);
                sim.performAction("Columbina", skill);

                // Set Ripple Duration (25s)
                final double thisRippleEndTime = currentTime + 25.0;
                rippleEndTime = thisRippleEndTime;

                sim.registerEvent(new simulation.event.SimpleTimerEvent(currentTime + 1.0, 2.0) {
                    @Override
                    public void onTick(CombatSimulator s) {
                        if (s.getCurrentTime() > thisRippleEndTime) {
                            finish();
                            return;
                        }
                        AttackAction ripple = new AttackAction(
                                "Gravity Ripple",
                                getMultiplier("Gravity Ripple"),
                                Element.HYDRO,
                                model.type.StatType.BASE_HP,
                                model.type.StatType.SKILL_DMG_BONUS,
                                0.0,
                                model.type.ActionType.SKILL);
                        ripple.setICD(ICDType.Standard, ICDTag.Columbina_Moonreel, 1.0);
                        s.performAction("Columbina", ripple);
                    }
                });

                // Generate Particles
                mechanics.energy.EnergyManager.distributeParticles(model.type.Element.HYDRO, 4.0,
                        mechanics.energy.ParticleType.PARTICLE, sim);

                // C1: Immediately trigger Gravity Interference (15s CD)
                if (constellation >= 1 && currentTime >= c1SkillCDNextTime) {
                    c1SkillCDNextTime = currentTime + 15.0;
                    triggerInterference(getDominantReactionType(), sim);
                }
                break;
            case BURST:
                markBurstUsed(currentTime);
                // Lunar Domain
                AttackAction burst = new AttackAction(
                        "Moonlit Melancholy",
                        getMultiplier("Burst Skill"),
                        Element.HYDRO,
                        model.type.StatType.BASE_HP, // Scales with HP
                        null);
                burst.setICD(ICDType.None, ICDTag.ElementalBurst, 2.0);
                burst.setAnimationDuration(2.0);
                sim.performAction("Columbina", burst);

                domainEndTime = currentTime + 20.0;

                // Lunar Domain burst bonus: active for the domain duration (20s)
                sim.applyTeamBuffNoStack(new mechanics.buff.SimpleBuff(
                        "Columbina: Lunar Burst Bonus", BuffId.COLUMBINA_LUNAR_BURST_BONUS, 20.0, sim.getCurrentTime(),
                        st -> st.add(model.type.StatType.LUNAR_REACTION_DMG_BONUS_ALL, getMultiplier("Lunar Domain Bonus"))));
                break;
            case NORMAL:
                AttackAction na = new AttackAction(
                        "Columbina Normal",
                        getMultiplier("Normal 1"),
                        Element.HYDRO,
                        model.type.StatType.BASE_ATK,
                        null);
                na.setAnimationDuration(0.5);
                sim.performAction("Columbina", na);
                break;
            default:
                break;
        }
    }

    /**
     * Fires three Moondew Cleanse hits (Dendro, HP-scaling) as part of an
     * enhanced Charged Attack when a Dew stack is available.
     *
     * @param sim       the combat simulator context
     * @param moonridge {@code true} if consuming a Moonridge Dew stack (unused branch
     *                  distinction; both variants use the same attack pattern)
     */
    private void performSpecialCA(CombatSimulator sim, boolean moonridge) {
        double mv = getMultiplier("Moondew Cleanse");

        for (int i = 0; i < 3; i++) {
            AttackAction special = new AttackAction(
                    "Moondew Cleanse Hit " + (i + 1),
                    mv,
                    Element.DENDRO,
                    model.type.StatType.BASE_HP,
                    null);
            special.setLunarReactionType(AttackAction.LunarReactionType.BLOOM);
            special.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
            special.setAnimationDuration(0.5 + (i * 0.1));
            sim.performAction("Columbina", special);
        }
    }

    /**
     * Handles reaction events to accumulate Gravity and Dew stacks.
     *
     * <p>Only Lunar-type reactions (Lunar-Charged, Lunar-Bloom, Lunar-Crystallize) are
     * processed. Gravity accumulates when a Ripple is active and the 2 s inter-gravity
     * cooldown has elapsed. Moonridge Dew is granted for Bloom inside the active Lunar
     * Domain (18 s CD). Verdant Dew is granted for any Bloom near the Ripple.
     *
     * @param result the reaction result carrying the reaction type name
     * @param source the character that triggered the reaction
     * @param time   the simulation time at which the reaction occurred
     * @param sim    the combat simulator context
     */
    @Override
    public void onReaction(mechanics.reaction.ReactionResult result, model.entity.Character source, double time,
            CombatSimulator sim) {
        // 4th Passive: Thundercloud-Strike — add expected extra 33% within Lunar Domain
        if (result.isThundercloudStrike()) {
            if (time <= domainEndTime) {
                double extra = result.getTransformDamage() * 0.33;
                sim.recordDamage("Thundercloud", extra);
                visualization.VisualLogger.getInstance().log(time, "Thundercloud",
                        "Lunar-Charged Reaction (Extra)", extra,
                        "Lunar-Charged Reaction (Extra)", extra, sim.getEnemy().getAuraMap());
            }
            return;
        }

        AttackAction.LunarReactionType type = toLunarReactionType(result);
        if (type == null)
            return;

        boolean nearRipple = (time <= rippleEndTime);

        if (nearRipple) {
            double gravityCooldown = (constellation >= 2) ? (2.0 / 1.34) : 2.0;
            if (time - lastGravityTime >= gravityCooldown) {
                accumulateGravity(type, sim);
                lastGravityTime = time;
            }

            boolean domainActive = (time <= domainEndTime);
            if (domainActive && type == AttackAction.LunarReactionType.BLOOM) {
                // 4th Passive: up to 3 Moonridge Dews per 18s window
                if (time - dewWindowStart >= 18.0) {
                    dewWindowStart = time;
                    dewsInWindow = 0;
                }
                if (dewsInWindow < 3 && moonridgeDew < MAX_DEW) {
                    moonridgeDew++;
                    dewsInWindow++;
                }
            }

            if (type == AttackAction.LunarReactionType.BLOOM) {
                if (verdantDew < MAX_DEW)
                    verdantDew++;
            }
        }
    }

    /**
     * Increments the Gravity counter by 20 for the given reaction type and
     * triggers an Interference attack when the counter reaches {@link #MAX_GRAVITY}.
     *
     * @param type the Lunar reaction type string (e.g. {@code "Lunar-Bloom"})
     * @param sim  the combat simulator context
     */
    private void accumulateGravity(AttackAction.LunarReactionType type, CombatSimulator sim) {
        gravity += 20;
        gravityByType.put(type, gravityByType.getOrDefault(type, 0) + 20);

        if (gravity >= MAX_GRAVITY) {
            AttackAction.LunarReactionType dominant = type;
            int max = -1;
            for (var entry : gravityByType.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    dominant = entry.getKey();
                }
            }

            gravity = 0;
            gravityByType.clear();
            triggerInterference(dominant, sim);
        }
    }

    /**
     * Returns the Lunar reaction type that has accumulated the most Gravity,
     * or {@code "Lunar-Charged"} as the default when no Gravity has accumulated.
     *
     * @return dominant reaction type string
     */
    private AttackAction.LunarReactionType getDominantReactionType() {
        AttackAction.LunarReactionType dominant = AttackAction.LunarReactionType.CHARGED;
        int max = -1;
        for (Map.Entry<AttackAction.LunarReactionType, Integer> entry : gravityByType.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                dominant = entry.getKey();
            }
        }
        return dominant;
    }

    /**
     * Fires an Interference attack corresponding to the dominant reaction type.
     * Each Lunar reaction type produces a distinct element and hit count.
     * When C1 is active and Moonsign is Ascendant Gleam, also applies a
     * dominant-type-specific bonus effect.
     *
     * @param dominant the dominant Lunar reaction type that triggered the cap
     * @param sim      the combat simulator context
     */
    private void triggerInterference(AttackAction.LunarReactionType dominant, CombatSimulator sim) {
        switch (dominant) {
            case CHARGED:
                AttackAction ie = new AttackAction(
                        "Interference (Charged)",
                        getMultiplier("Interference Charged"),
                        Element.ELECTRO,
                        model.type.StatType.BASE_HP,
                        null);
                ie.setLunarReactionType(AttackAction.LunarReactionType.CHARGED);
                ie.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
                ie.setAnimationDuration(0);
                sim.performAction("Columbina", ie);
                break;
            case BLOOM:
                for (int i = 0; i < 5; i++) {
                    AttackAction ib = new AttackAction(
                            "Interference (Bloom)",
                            getMultiplier("Interference Bloom"),
                            Element.DENDRO,
                            model.type.StatType.BASE_HP,
                            null);
                    ib.setLunarReactionType(AttackAction.LunarReactionType.BLOOM);
                    ib.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
                    ib.setAnimationDuration(0);
                    sim.performAction("Columbina", ib);
                }
                break;
            case CRYSTALLIZE:
                AttackAction ic = new AttackAction(
                        "Interference (Crystallize)",
                        getMultiplier("Interference Crystallize"),
                        Element.GEO,
                        model.type.StatType.BASE_HP,
                        null);
                ic.setLunarReactionType(AttackAction.LunarReactionType.CRYSTALLIZE);
                ic.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
                ic.setAnimationDuration(0);
                sim.performAction("Columbina", ic);
                // 4th Passive: 33% extra Moondrift (Moondrift Harmony) when Lunar Domain active
                if (sim.getCurrentTime() <= domainEndTime && Math.random() < 0.33) {
                    AttackAction icExtra = new AttackAction(
                            "Interference (Crystallize) Extra",
                            getMultiplier("Interference Crystallize"),
                            Element.GEO,
                            model.type.StatType.BASE_HP,
                            null);
                    icExtra.setLunarReactionType(AttackAction.LunarReactionType.CRYSTALLIZE);
                    icExtra.setICD(ICDType.None, ICDTag.None, 0.0);
                    icExtra.setAnimationDuration(0);
                    sim.performAction("Columbina", icExtra);
                }
                break;
        }

        // C2: Lunar Brilliance (+40% HP, 8s) and optional C2 Moonsign bonus
        if (constellation >= 2) {
            double t = sim.getCurrentTime();
            boolean brillianceWasActive = (t < lunarBrillianceEndTime);
            lunarBrillianceEndTime = t + 8.0;
            this.removeBuff(BuffId.COLUMBINA_LUNAR_BRILLIANCE);
            this.addBuff(new mechanics.buff.Buff("Columbina: Lunar Brilliance", BuffId.COLUMBINA_LUNAR_BRILLIANCE,
                    8.0, t) {
                @Override
                protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                    stats.add(model.type.StatType.HP_PERCENT, 0.40);
                }
            });
            if (brillianceWasActive && sim.getMoonsign() == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
                applyC2MoonsignEffect(dominant, sim);
            }
        }

        // C1: Moonsign Ascendant Gleam — bonus effect based on dominant type
        if (constellation >= 1 && sim.getMoonsign() == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            applyC1MoonsignEffect(dominant, sim);
        }
    }

    /**
     * Applies the C1 Moonsign: Ascendant Gleam bonus triggered on Gravity Interference.
     *
     * <ul>
     *   <li><b>Lunar-Charged</b>: active character recovers 6 Energy.</li>
     *   <li><b>Lunar-Bloom</b>: active character gains interruption resistance for 8 s.</li>
     *   <li><b>Lunar-Crystallize</b>: active character gains Rainsea Shield
     *       (12% Columbina Max HP, Hydro 250% effectiveness) for 8 s.</li>
     * </ul>
     *
     * @param dominant the dominant reaction type determining which effect fires
     * @param sim      the combat simulator context
     */
    private void applyC1MoonsignEffect(AttackAction.LunarReactionType dominant, CombatSimulator sim) {
        double t = sim.getCurrentTime();
        Character active = sim.getActiveCharacter();
        switch (dominant) {
            case CHARGED:
                // Active character recovers 6 Energy
                if (active != null) {
                    active.receiveFlatEnergy(6);
                }
                break;
            case BLOOM:
                // Active character gains interruption resistance for 8s (defensive, no DPS stat)
                if (active != null) {
                    active.removeBuff(BuffId.RAINSEA_INTERRUPTION_RESISTANCE);
                    active.addBuff(new mechanics.buff.Buff("Rainsea: Interruption Resistance",
                            BuffId.RAINSEA_INTERRUPTION_RESISTANCE, 8.0, t) {
                        @Override
                        protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                            // Interruption resistance has no direct DPS impact
                        }
                    });
                }
                break;
            case CRYSTALLIZE:
                // Rainsea Shield: 12% Columbina Max HP, Hydro 250% effectiveness, 8s
                if (active != null) {
                    double shieldHp = getStructuralStats(t).getTotalHp() * 0.12;
                    String shieldName = String.format("Rainsea Shield (%.0f HP)", shieldHp);
                    active.removeBuff(BuffId.RAINSEA_SHIELD);
                    active.addBuff(new mechanics.buff.Buff(shieldName, BuffId.RAINSEA_SHIELD, 8.0, t) {
                        @Override
                        protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                            // Shield absorption is defensive, no DPS stat impact
                        }
                    });
                }
                break;
        }
    }

    /**
     * Applies the C2 Moonsign: Ascendant Gleam bonus triggered when Lunar Brilliance
     * is already active at the time of a subsequent Gravity Interference.
     *
     * <p>Each effect lasts 8 s and scales from Columbina's Max HP at the time of trigger:
     * <ul>
     *   <li><b>Lunar-Charged</b>: active character ATK +1% Columbina Max HP.</li>
     *   <li><b>Lunar-Bloom</b>: active character EM +0.35% Columbina Max HP.</li>
     *   <li><b>Lunar-Crystallize</b>: active character DEF +1% Columbina Max HP.</li>
     * </ul>
     *
     * @param dominant the dominant reaction type determining which effect fires
     * @param sim      the combat simulator context
     */
    private void applyC2MoonsignEffect(AttackAction.LunarReactionType dominant, CombatSimulator sim) {
        double t = sim.getCurrentTime();
        Character active = sim.getActiveCharacter();
        if (active == null) {
            return;
        }
        double colHp = getStructuralStats(t).getTotalHp();
        switch (dominant) {
            case CHARGED:
                double atkBonus = colHp * 0.01;
                active.removeBuff(BuffId.C2_MOONSIGN_ATK_BONUS);
                active.addBuff(new mechanics.buff.Buff("C2 Moonsign: ATK Bonus", BuffId.C2_MOONSIGN_ATK_BONUS,
                        8.0, t) {
                    @Override
                    protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                        stats.add(model.type.StatType.ATK_FLAT, atkBonus);
                    }
                });
                break;
            case BLOOM:
                double emBonus = colHp * 0.0035;
                active.removeBuff(BuffId.C2_MOONSIGN_EM_BONUS);
                active.addBuff(new mechanics.buff.Buff("C2 Moonsign: EM Bonus", BuffId.C2_MOONSIGN_EM_BONUS,
                        8.0, t) {
                    @Override
                    protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                        stats.add(model.type.StatType.ELEMENTAL_MASTERY, emBonus);
                    }
                });
                break;
            case CRYSTALLIZE:
                double defBonus = colHp * 0.01;
                active.removeBuff(BuffId.C2_MOONSIGN_DEF_BONUS);
                active.addBuff(new mechanics.buff.Buff("C2 Moonsign: DEF Bonus", BuffId.C2_MOONSIGN_DEF_BONUS,
                        8.0, t) {
                    @Override
                    protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                        stats.add(model.type.StatType.DEF_FLAT, defBonus);
                    }
                });
                break;
        }
    }

    private AttackAction.LunarReactionType toLunarReactionType(mechanics.reaction.ReactionResult result) {
        switch (result.getLunarType()) {
            case CHARGED:
                return AttackAction.LunarReactionType.CHARGED;
            case BLOOM:
                return AttackAction.LunarReactionType.BLOOM;
            case CRYSTALLIZE:
                return AttackAction.LunarReactionType.CRYSTALLIZE;
            default:
                if (result.isElectroCharged()) {
                    return AttackAction.LunarReactionType.CHARGED;
                }
                return null;
        }
    }

    /**
     * Returns the permanent team buffs provided by Columbina.
     *
     * <p>Includes a passive {@code LUNAR_BASE_BONUS} buff whose value scales with
     * Columbina's total HP: {@code min(0.07, (HP / 1000) * 0.002)}.
     *
     * @return list containing the Lunar Base Bonus buff
     */
    @Override
    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        java.util.List<mechanics.buff.Buff> buffs = new java.util.ArrayList<>();

        // Passive: HP -> Multiplier
        buffs.add(new mechanics.buff.Buff("Columbina: Lunar Base Bonus", BuffId.COLUMBINA_LUNAR_BASE_BONUS,
                Double.MAX_VALUE, 0) {
            @Override
            protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                double hp = Columbina.this.getStructuralStats(currentTime).getTotalHp();
                double bonus = Math.min(0.07, (hp / 1000.0) * 0.002);
                // System.out.println("[COLUMBINA_DEBUG] HP:" + hp + " Bonus:" + bonus);
                stats.add(model.type.StatType.LUNAR_BASE_BONUS, bonus);
            }
        });

        // Burst Bonus: active only while Lunar Domain is up (applied via applyTeamBuff on burst cast)

        // C1: All party members' Lunar Reaction DMG +1.5% (permanent, independent multiplier)
        if (constellation >= 1) {
            buffs.add(new mechanics.buff.Buff("Columbina C1: Lunar Reaction DMG +1.5%",
                    BuffId.COLUMBINA_C1_LUNAR_REACTION_BONUS, Double.MAX_VALUE, 0) {
                @Override
                protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                    stats.add(model.type.StatType.LUNAR_MULTIPLIER, 0.015);
                }
            });
        }

        // C2: All party members' Lunar Reaction DMG +7% (permanent, independent multiplier)
        if (constellation >= 2) {
            buffs.add(new mechanics.buff.Buff("Columbina C2: Lunar Reaction DMG +7%",
                    BuffId.COLUMBINA_C2_LUNAR_REACTION_BONUS, Double.MAX_VALUE, 0) {
                @Override
                protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                    stats.add(model.type.StatType.LUNAR_MULTIPLIER, 0.07);
                }
            });
        }

        return buffs;
    }
}
