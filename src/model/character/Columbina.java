package model.character;

import mechanics.formula.DamageCalculator;
import model.entity.Character;
import model.type.Element;
import model.type.ICDType;
import model.type.ICDTag;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
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
 * <p>Columbina is a Lunar character ({@link #isLunarCharacter()} returns {@code true}) and
 * registers herself as a {@link CombatSimulator.ReactionListener} on first action.
 */
public class Columbina extends Character implements CombatSimulator.ReactionListener {
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

    // Gravity Accumulation Logic
    private Map<String, Integer> gravityByType = new HashMap<>(); // "Charged", "Bloom", "Crystallize" -> Count
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

        this.skillCD = 17.0;
        this.burstCD = 15.0;
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
     * Handles action keys dispatched by the combat simulator.
     * Registers the reaction listener on first use.
     *
     * <p>Supported keys:
     * <ul>
     *   <li>{@code "attack_charged"} — performs a Moondew Cleanse if Dew is available,
     *       otherwise a standard Charged Attack.</li>
     *   <li>{@code "skill"} — casts Eternal Tides, starts the Gravity Ripple periodic
     *       event, and generates 4 Hydro particles.</li>
     *   <li>{@code "burst"} — casts Moonlit Melancholy and applies the 20 s Lunar Domain
     *       reaction bonus team buff.</li>
     *   <li>{@code "attack_normal_*"} — fires a normal attack hit.</li>
     * </ul>
     *
     * @param actionKey the action identifier string
     * @param sim       the combat simulator context
     */
    @Override
    public void onAction(String actionKey, CombatSimulator sim) {
        if (!registeredListener) {
            sim.addReactionListener(this);
            registeredListener = true;
        }

        double currentTime = sim.getCurrentTime();

        switch (actionKey) {
            case "attack_charged":
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
            case "skill":
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
                break;
            case "burst":
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
                        "Columbina: Lunar Burst Bonus", 20.0, sim.getCurrentTime(),
                        st -> st.add(model.type.StatType.LUNAR_REACTION_DMG_BONUS_ALL, getMultiplier("Lunar Domain Bonus"))));
                break;
            default:
                if (actionKey.startsWith("attack_normal_")) {
                    AttackAction na = new AttackAction(
                            actionKey,
                            getMultiplier(mapNormalKey(actionKey)),
                            Element.HYDRO,
                            model.type.StatType.BASE_ATK,
                            null);
                    na.setAnimationDuration(0.5);
                    sim.performAction("Columbina", na);
                }
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
            special.setLunarReactionType("Bloom");
            special.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
            special.setAnimationDuration(0.5 + (i * 0.1));
            sim.performAction("Columbina", special);
        }
    }

    /**
     * Maps a normal-attack action key to the talent data lookup key.
     *
     * @param key action key such as {@code "attack_normal_1"}
     * @return talent data key (currently simplified to {@code "Normal 1"})
     */
    private String mapNormalKey(String key) {
        return "Normal 1"; // Simplified
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
        String type = result.getName();

        // 4th Passive: Thundercloud-Strike — add expected extra 33% within Lunar Domain
        if (type.equals("Thundercloud-Strike")) {
            if (time <= domainEndTime) {
                double extra = result.getTransformDamage() * 0.33;
                sim.recordDamage("Thundercloud", extra);
                visualization.VisualLogger.getInstance().log(time, "Thundercloud",
                        "Lunar-Charged Reaction (Extra)", extra,
                        "Lunar-Charged Reaction (Extra)", extra, sim.getEnemy().getAuraMap());
            }
            return;
        }

        if (type.equals("Electro-Charged"))
            type = "Lunar-Charged";
        else if (type.equals("Bloom"))
            type = "Lunar-Bloom";
        else if (type.startsWith("Crystallize") && type.contains("HYDRO"))
            type = "Lunar-Crystallize";

        if (!type.startsWith("Lunar"))
            return;

        boolean nearRipple = (time <= rippleEndTime);

        if (nearRipple) {
            if (time - lastGravityTime >= 2.0) {
                accumulateGravity(type, sim);
                lastGravityTime = time;
            }

            boolean domainActive = (time <= domainEndTime);
            if (domainActive && type.contains("Bloom")) {
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

            if (type.contains("Bloom")) {
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
    private void accumulateGravity(String type, CombatSimulator sim) {
        gravity += 20;
        gravityByType.put(type, gravityByType.getOrDefault(type, 0) + 20);

        if (gravity >= MAX_GRAVITY) {
            String dominant = type;
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
     * Fires an Interference attack corresponding to the dominant reaction type.
     * Each Lunar reaction type produces a distinct element and hit count.
     *
     * @param dominant the dominant Lunar reaction type that triggered the cap
     * @param sim      the combat simulator context
     */
    private void triggerInterference(String dominant, CombatSimulator sim) {
        switch (dominant) {
            case "Lunar-Charged":
                AttackAction ie = new AttackAction(
                        "Interference (Charged)",
                        getMultiplier("Interference Charged"),
                        Element.ELECTRO,
                        model.type.StatType.BASE_HP,
                        null);
                ie.setLunarReactionType("Charged");
                ie.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
                ie.setAnimationDuration(0);
                sim.performAction("Columbina", ie);
                break;
            case "Lunar-Bloom":
                for (int i = 0; i < 5; i++) {
                    AttackAction ib = new AttackAction(
                            "Interference (Bloom)",
                            getMultiplier("Interference Bloom"),
                            Element.DENDRO,
                            model.type.StatType.BASE_HP,
                            null);
                    ib.setLunarReactionType("Bloom");
                    ib.setICD(ICDType.None, ICDTag.None, 0.0); // Lunar Reaction DMG: no aura application
                    ib.setAnimationDuration(0);
                    sim.performAction("Columbina", ib);
                }
                break;
            case "Lunar-Crystallize":
                AttackAction ic = new AttackAction(
                        "Interference (Crystallize)",
                        getMultiplier("Interference Crystallize"),
                        Element.GEO,
                        model.type.StatType.BASE_HP,
                        null);
                ic.setLunarReactionType("Crystallize");
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
                    icExtra.setLunarReactionType("Crystallize");
                    icExtra.setICD(ICDType.None, ICDTag.None, 0.0);
                    icExtra.setAnimationDuration(0);
                    sim.performAction("Columbina", icExtra);
                }
                break;
        }
    }

    /**
     * Returns the permanent team buffs provided by Columbina.
     *
     * <p>Includes a passive {@code LUNAR_MULTIPLIER} buff whose value scales with
     * Columbina's total HP: {@code min(0.07, (HP / 1000) * 0.002)}.
     *
     * @return list containing the Lunar Multiplier buff
     */
    @Override
    public java.util.List<mechanics.buff.Buff> getTeamBuffs() {
        java.util.List<mechanics.buff.Buff> buffs = new java.util.ArrayList<>();

        // Passive: HP -> Multiplier
        buffs.add(new mechanics.buff.Buff("Columbina: Lunar Multiplier", Double.MAX_VALUE, 0) {
            @Override
            protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                double hp = Columbina.this.getStructuralStats(currentTime).getTotalHp();
                double bonus = Math.min(0.07, (hp / 1000.0) * 0.002);
                // System.out.println("[COLUMBINA_DEBUG] HP:" + hp + " Bonus:" + bonus);
                stats.add(model.type.StatType.LUNAR_MULTIPLIER, bonus);
            }
        });

        // Burst Bonus: active only while Lunar Domain is up (applied via applyTeamBuff on burst cast)

        return buffs;
    }
}
