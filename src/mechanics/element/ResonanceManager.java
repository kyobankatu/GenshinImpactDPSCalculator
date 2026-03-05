package mechanics.element;

import simulation.CombatSimulator;
import model.entity.Character;
import model.type.Element;
import model.type.StatType;
import mechanics.buff.SimpleBuff;

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
 *   <li><b>Shattering Ice (Cryo x2)</b> – {@code +15% Crit Rate} (applied
 *       unconditionally; ideally this is conditional on the enemy having a
 *       Cryo/Frozen aura).</li>
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
        System.out.println("\n--- Checking Elemental Resonance ---");
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
            System.out.println("   [Resonance] Protective Canopy Applied (+15% RES)");
            sim.applyTeamBuff(new SimpleBuff("Protective Canopy", 99999, 0, stats -> {
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
                System.out.println("   [Resonance] Fervent Flames (Pyro) Applied (+25% ATK)");
                sim.applyTeamBuff(new SimpleBuff("Fervent Flames", 99999, 0, stats -> {
                    stats.add(StatType.ATK_PERCENT, 0.25);
                }));
                break;

            case HYDRO:
                System.out.println("   [Resonance] Soothing Water (Hydro) Applied (+25% HP)");
                sim.applyTeamBuff(new SimpleBuff("Soothing Water", 99999, 0, stats -> {
                    stats.add(StatType.HP_PERCENT, 0.25);
                }));
                break;

            case CRYO:
                System.out.println("   [Resonance] Shattering Ice (Cryo) Applied (+15% CR vs Cryo/Frozen)");
                sim.applyTeamBuff(new SimpleBuff("Shattering Ice", 99999, 0, stats -> {
                    // Conditional CR not directly supported by SimpleBuff static application
                    // We need a conditional buff logic or assume uptime.
                    // For Simplicity: We will assume modest uptime or assume active if enemy has
                    // Cryo/Frozen?
                    // Better: The CombatSimulator/DamageCalculator handles dynamic stats.
                    // But SimpleBuff applies at `getEffectiveStats`.
                    // We can add a "Conditional" stat type? Or just add Flat CR for now
                    // and note it assumes Aura.
                    // Ideally: Check Enemy Aura?
                    // Since Buff.apply takes `currentTime`, but not `Enemy`, we can't check aura
                    // here easily
                    // without changing Buff interface or accessing Sim globally (which we can
                    // capture via closure).

                    // Let's rely on checking Sim state if possible.
                    // But SimpleBuff functional interface is (StatsContainer) -> void.
                    // We'll trust the user has Cryo aura for Cryo teams.
                    stats.add(StatType.CRIT_RATE, 0.15);
                    // Note: In a real advanced sim, this should be conditional.
                }));
                break;

            case GEO:
                System.out.println("   [Resonance] Enduring Rock (Geo) Applied (+15% DMG, -20% Geo Res)");
                sim.applyTeamBuff(new SimpleBuff("Enduring Rock", 99999, 0, stats -> {
                    stats.add(StatType.DMG_BONUS_ALL, 0.15);
                    stats.add(StatType.GEO_RES_SHRED, 0.20);
                }));
                break;

            case DENDRO:
                System.out.println("   [Resonance] Sprawling Greenery (Dendro) Applied (+50 EM)");
                sim.applyTeamBuff(new SimpleBuff("Sprawling Greenery", 99999, 0, stats -> {
                    stats.add(StatType.ELEMENTAL_MASTERY, 50.0);
                    // Note: Specific reactions give more EM (30/20). Ignoring for base
                    // implementation.
                }));
                break;

            case ANEMO:
                System.out.println("   [Resonance] Impetuous Winds (Anemo) Applied (-5% CD)");
                sim.applyTeamBuff(new SimpleBuff("Impetuous Winds", 99999, 0, stats -> {
                    stats.add(StatType.CD_REDUCTION, 0.05);
                }));
                break;

            case ELECTRO:
                System.out.println("   [Resonance] High Voltage (Electro) Applied (Particle Generation)");
                // Logic for particle generation is complex (simulation event),
                // skipping static stat buff as it provides none.
                break;

            default:
                break;
        }
    }
}
