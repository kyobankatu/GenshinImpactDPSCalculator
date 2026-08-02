package model.weapon;

import java.util.Objects;
import java.util.function.DoubleSupplier;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * The Widsith catalyst with an injectable Debut theme selection.
 *
 * <p>
 * Entering the field selects Recitative, Aria, or Interlude for ten seconds.
 * The selection has a shared thirty-second cooldown, and an active theme
 * persists when the wielder leaves the field.
 */
public class TheWidsith extends Weapon
        implements SimulatorInitializedWeaponEffect, SwitchAwareWeaponEffect {
    private static final double EFFECT_DURATION = 10.0;
    private static final double ACTIVATION_COOLDOWN = 30.0;

    private enum Theme {
        RECITATIVE,
        ARIA,
        INTERLUDE
    }

    private final int refinement;
    private final DoubleSupplier themeDraw;
    private final double attackBonus;
    private final double elementalDamageBonus;
    private final double elementalMasteryBonus;
    private Character owner;
    private CombatSimulator simulator;
    private Theme activeTheme;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs an R5 The Widsith with stochastic Debut draws. */
    public TheWidsith() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 The Widsith with an explicit Debut draw source.
     *
     * @param themeDraw source returning finite values in {@code [0, 1)}
     * @throws NullPointerException if {@code themeDraw} is null
     */
    public TheWidsith(DoubleSupplier themeDraw) {
        this(5, themeDraw);
    }

    /**
     * Constructs The Widsith at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheWidsith(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs The Widsith with a selected refinement and Debut draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param themeDraw source returning finite values in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code refinement} is outside 1-5
     * @throws NullPointerException if {@code themeDraw} is null
     */
    public TheWidsith(int refinement, DoubleSupplier themeDraw) {
        super("The Widsith", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.themeDraw = Objects.requireNonNull(themeDraw, "Debut draw source is required");
        this.attackBonus = 0.45 + 0.15 * refinement;
        this.elementalDamageBonus = 0.36 + 0.12 * refinement;
        this.elementalMasteryBonus = 180.0 + 60.0 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_DMG, 0.551);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Binds Debut to its owner and activates it when the owner starts on-field.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        Objects.requireNonNull(equippedOwner, "Weapon owner is required");
        Objects.requireNonNull(sim, "Weapon simulator is required");
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "The Widsith is already bound to another simulator");
            }
            return;
        }

        Theme initialTheme = null;
        if (sim.getActiveCharacter() == equippedOwner) {
            initialTheme = drawTheme();
        }
        owner = equippedOwner;
        simulator = sim;
        if (initialTheme != null) {
            activate(initialTheme, sim.getCurrentTime());
        }
    }

    /**
     * Leaves an active Debut theme unchanged when the owner exits the field.
     *
     * @param user outgoing weapon owner
     * @param sim active simulator
     */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
        // Debut explicitly survives switching the wielder off-field.
    }

    /**
     * Attempts a fresh Debut selection when the bound owner enters the field.
     *
     * @param user incoming weapon owner
     * @param sim active simulator
     */
    @Override
    public void onSwitchIn(Character user, CombatSimulator sim) {
        if (simulator == null || user != owner || sim != simulator) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        if (currentTime < nextActivationTime) {
            return;
        }
        Theme selectedTheme = drawTheme();
        activate(selectedTheme, currentTime);
    }

    /**
     * Applies the selected Debut theme during its half-open ten-second window.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (activeTheme == null || currentTime >= activeUntil) {
            return;
        }
        switch (activeTheme) {
            case RECITATIVE:
                stats.add(StatType.ATK_PERCENT, attackBonus);
                break;
            case ARIA:
                for (Element element : Element.values()) {
                    if (element != Element.PHYSICAL) {
                        stats.add(element.getBonusStatType(), elementalDamageBonus);
                    }
                }
                break;
            case INTERLUDE:
                stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
                break;
            default:
                throw new IllegalStateException("Unsupported Debut theme: " + activeTheme);
        }
    }

    /** Selects one of the three equally sized Debut draw intervals. */
    private Theme drawTheme() {
        double draw = themeDraw.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalArgumentException(
                    "Debut draw must be finite and in [0, 1)");
        }
        if (draw < 1.0 / 3.0) {
            return Theme.RECITATIVE;
        }
        if (draw < 2.0 / 3.0) {
            return Theme.ARIA;
        }
        return Theme.INTERLUDE;
    }

    /** Commits a validated theme selection and its shared cooldown. */
    private void activate(Theme selectedTheme, double currentTime) {
        activeTheme = selectedTheme;
        activeUntil = currentTime + EFFECT_DURATION;
        nextActivationTime = currentTime + ACTIVATION_COOLDOWN;
    }
}
