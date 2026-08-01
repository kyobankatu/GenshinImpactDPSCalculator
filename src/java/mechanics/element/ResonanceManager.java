package mechanics.element;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import simulation.CombatSimulator;
import model.entity.Character;
import model.type.Element;
import model.type.StatType;
import mechanics.buff.SimpleBuff;
import mechanics.energy.EnergyManager;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects the party's elemental composition and applies the appropriate
 * elemental resonance team buff to the simulator.
 *
 * <p>Resonance rules mirrored from the official game:
 * <ul>
 *   <li><b>Protective Canopy</b> – all 4 elements are unique; applied as a
 *       placeholder buff (defensive stats are not fully modelled).</li>
 *   <li><b>Fervent Flames (Pyro x2)</b> – {@code +25% ATK}.</li>
 *   <li><b>Soothing Water (Hydro x2)</b> – {@code +25% HP}.</li>
 *   <li><b>Shattering Ice (Cryo x2)</b> – {@code +15% Crit Rate} while the
 *       enemy is affected by Cryo or Frozen.</li>
 *   <li><b>Enduring Rock (Geo x2)</b> – {@code +15% DMG Bonus}, {@code -20%
 *       Geo RES} on enemy.</li>
 *   <li><b>Sprawling Greenery (Dendro x2)</b> – {@code +50 EM}.</li>
 *   <li><b>Impetuous Winds (Anemo x2)</b> – {@code -5% Skill/Burst CD}.</li>
 *   <li><b>High Voltage (Electro x2)</b> – particle generation event; no
 *       static stat buff is applied.</li>
 * </ul>
 */
public class ResonanceManager {

    /**
     * Inspects the party composition of {@code sim} and registers the correct
     * elemental resonance team buff.
     *
     * <p>If all 4 party slots are occupied by distinct elements, Protective
     * Canopy is applied and the method returns immediately (no 2-element
     * resonance can exist when each element appears only once).
     *
     * @param sim the combat simulator whose party is examined; the buff is
     *            registered on this same simulator
     */
    public static void applyResonances(CombatSimulator sim) {
        if (sim.isLoggingEnabled()) {
            System.out.println("\n--- Checking Elemental Resonance ---");
        }
        Collection<Character> party = sim.getPartyMembers();
        Map<Element, Integer> elementCounts = new HashMap<>();
        int uniqueElements = 0;

        for (Character c : party) {
            Element e = c.getElement();
            if (e == null)
                continue;

            if (!elementCounts.containsKey(e)) {
                uniqueElements++;
            }
            elementCounts.put(e, elementCounts.getOrDefault(e, 0) + 1);
        }

        // 1. Protective Canopy (4 Unique Elements)
        if (uniqueElements >= 4) {
            if (sim.isLoggingEnabled()) {
                System.out.println("   [Resonance] Protective Canopy Applied (+15% RES)");
            }
            sim.applyTeamBuff(new SimpleBuff("Protective Canopy", BuffId.PROTECTIVE_CANOPY, 99999, 0, stats -> {
                // Assuming RES usually means Elemental/Physical RES against incoming DMG.
                // In this DPS sim, "RES" stats usually refer to Enemy RES Shred or similar.
                // Standard StatType doesn't seem to have "Incoming RES".
                // We will skip actual implementation if defensive stats are not used,
                // but log it for completeness.
            }));
            return; // Protective Canopy overrides others?
            // Actually in-game, Canopy exists if no 2-element resonance exists.
            // Since we have 4 slots, if unique=4, then no element count >= 2.
            // So return is safe/correct.
        }

        // 2. 2-Element Resonances
        for (Element e : elementCounts.keySet()) {
            if (elementCounts.get(e) >= 2) {
                applySpecificResonance(sim, e);
            }
        }
    }

    /**
     * Registers the team buff for the given element's 2-element resonance.
     *
     * @param sim the simulator to receive the buff
     * @param e   the element whose resonance should be applied
     */
    private static void applySpecificResonance(CombatSimulator sim, Element e) {
        switch (e) {
            case PYRO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Fervent Flames (Pyro) Applied (+25% ATK)");
                }
                sim.applyTeamBuff(new SimpleBuff("Fervent Flames", BuffId.FERVENT_FLAMES, 99999, 0, stats -> {
                    stats.add(StatType.ATK_PERCENT, 0.25);
                }));
                break;

            case HYDRO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Soothing Water (Hydro) Applied (+25% HP)");
                }
                sim.applyTeamBuff(new SimpleBuff("Soothing Water", BuffId.SOOTHING_WATER, 99999, 0, stats -> {
                    stats.add(StatType.HP_PERCENT, 0.25);
                }));
                break;

            case CRYO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Shattering Ice (Cryo) Applied (+15% CR vs Cryo/Frozen)");
                }
                sim.applyTeamBuff(new Buff("Shattering Ice", BuffId.SHATTERING_ICE, 99999, 0) {
                    @Override
                    protected void applyStats(model.stats.StatsContainer stats, double currentTime) {
                        boolean affectedByCryo = sim.getEnemy() != null
                                && sim.getEnemy().getAuraUnits(Element.CRYO, currentTime) > 0.0;
                        boolean frozen = sim.getEnemy() != null && sim.getEnemy().isFrozen();
                        if (affectedByCryo || frozen) {
                            stats.add(StatType.CRIT_RATE, 0.15);
                        }
                    }
                });
                break;

            case GEO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Enduring Rock (Geo) Applied (+15% DMG, -20% Geo Res)");
                }
                sim.applyTeamBuff(new SimpleBuff("Enduring Rock", BuffId.ENDURING_ROCK, 99999, 0, stats -> {
                    stats.add(StatType.DMG_BONUS_ALL, 0.15);
                    stats.add(StatType.GEO_RES_SHRED, 0.20);
                }));
                break;

            case DENDRO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Sprawling Greenery (Dendro) Applied (+50 EM, reaction buffs)");
                }
                sim.applyTeamBuff(new SimpleBuff("Sprawling Greenery", BuffId.SPRAWLING_GREENERY, 99999, 0, stats -> {
                    stats.add(StatType.ELEMENTAL_MASTERY, 50.0);
                }));
                registerSprawlingGreeneryReactionBuffs(sim);
                break;

            case ANEMO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] Impetuous Winds (Anemo) Applied (-5% CD)");
                }
                sim.applyTeamBuff(new SimpleBuff("Impetuous Winds", BuffId.IMPETUOUS_WINDS, 99999, 0, stats -> {
                    stats.add(StatType.CD_REDUCTION, 0.05);
                }));
                break;

            case ELECTRO:
                if (sim.isLoggingEnabled()) {
                    System.out.println("   [Resonance] High Voltage (Electro) Applied (Particle Generation)");
                }
                final double[] lastHVParticleTime = { Double.NEGATIVE_INFINITY };
                sim.addReactionListener(new CombatSimulator.ReactionListener() {
                    @Override
                    public void onReaction(ReactionResult result, Character source, double time,
                            CombatSimulator s) {
                        if (!result.triggersElectroResonance()) {
                            return;
                        }
                        if (time - lastHVParticleTime[0] < 5.0) {
                            return;
                        }
                        lastHVParticleTime[0] = time;
                        s.getEnergyDistributor().distributeParticles(Element.ELECTRO, 1.0, ParticleType.PARTICLE);
                    }
                });
                break;

            default:
                break;
        }
    }

    /**
     * Registers the two independently refreshed Sprawling Greenery reaction buffs.
     *
     * @param sim simulator whose reaction bus and team buffs receive the resonance effects
     */
    private static void registerSprawlingGreeneryReactionBuffs(CombatSimulator sim) {
        sim.addReactionListener((result, source, time, activeSim) -> {
            if (isSprawlingGreeneryPrimaryReaction(result.getKind())) {
                activeSim.applyTeamBuffNoStack(new SimpleBuff(
                        "Sprawling Greenery: Primary Reaction",
                        BuffId.SPRAWLING_GREENERY_PRIMARY_REACTION,
                        6.0,
                        time,
                        stats -> stats.add(StatType.ELEMENTAL_MASTERY, 30.0)));
            } else if (isSprawlingGreenerySecondaryReaction(result.getKind())) {
                activeSim.applyTeamBuffNoStack(new SimpleBuff(
                        "Sprawling Greenery: Secondary Reaction",
                        BuffId.SPRAWLING_GREENERY_SECONDARY_REACTION,
                        6.0,
                        time,
                        stats -> stats.add(StatType.ELEMENTAL_MASTERY, 20.0)));
            }
        });
    }

    /**
     * Returns whether a reaction grants the six-second 30 EM resonance buff.
     *
     * @param kind typed reaction kind
     * @return {@code true} for Burning, Quicken, Bloom, or Lunar-Bloom
     */
    private static boolean isSprawlingGreeneryPrimaryReaction(ReactionResult.Kind kind) {
        return kind == ReactionResult.Kind.BURNING
                || kind == ReactionResult.Kind.QUICKEN
                || kind == ReactionResult.Kind.BLOOM
                || kind == ReactionResult.Kind.LUNAR_BLOOM;
    }

    /**
     * Returns whether a reaction grants the six-second 20 EM resonance buff.
     *
     * @param kind typed reaction kind
     * @return {@code true} for Aggravate, Spread, Hyperbloom, or Burgeon
     */
    private static boolean isSprawlingGreenerySecondaryReaction(ReactionResult.Kind kind) {
        return kind == ReactionResult.Kind.AGGRAVATE
                || kind == ReactionResult.Kind.SPREAD
                || kind == ReactionResult.Kind.HYPERBLOOM
                || kind == ReactionResult.Kind.BURGEON;
    }
}
