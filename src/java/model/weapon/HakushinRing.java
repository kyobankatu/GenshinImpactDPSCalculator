package model.weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Hakushin Ring catalyst with independent elemental team-bonus windows.
 *
 * <p>Eligible Electro-related reactions triggered by the on-field owner grant
 * the party members of each involved element that element's DMG Bonus for six
 * seconds. Each element owns one {@code [start, start + 6)} window; another
 * reaction cannot refresh that element until its current window expires.</p>
 */
public class HakushinRing extends Weapon
        implements SimulatorInitializedWeaponEffect,
        CombatSimulator.ReactionListener,
        WeaponTeamBuffProvider {
    private static final double BONUS_DURATION = 6.0;

    private final int refinement;
    private final double elementalDamageBonus;
    private final Map<Element, Buff> elementalBuffs = new EnumMap<>(Element.class);
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Hakushin Ring at refinement rank five. */
    public HakushinRing() {
        this(5);
    }

    /**
     * Constructs Hakushin Ring at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public HakushinRing(int refinement) {
        super("Hakushin Ring", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalDamageBonus = 0.075 + 0.025 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.306);
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
     * Binds the equipped owner and registers exactly one reaction listener.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Hakushin Ring is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /**
     * Starts missing elemental windows for one eligible owner-attributed reaction.
     *
     * <p>Hyperbloom intentionally grants Electro and Dendro bonuses without
     * Hydro. Swirl and Crystallize qualify only when their typed related element
     * is Electro.</p>
     *
     * @param result resolved reaction with typed kind and related element
     * @param source character attributed as the reaction trigger
     * @param time reaction time in simulation seconds
     * @param sim simulator dispatching the reaction
     */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || source != owner
                || sim.getActiveCharacter() != owner
                || result == null) {
            return;
        }
        for (Element element : getInvolvedElements(result)) {
            Buff currentBuff = elementalBuffs.get(element);
            if (currentBuff != null && !currentBuff.isExpired(time)) {
                continue;
            }
            Buff newBuff = new SimpleBuff(
                    "Hakushin Ring (" + element + ")",
                    BONUS_DURATION,
                    time,
                    stats -> stats.add(element.getBonusStatType(), elementalDamageBonus));
            newBuff.forElement(element).sourcedBy(owner.getCharacterId());
            elementalBuffs.put(element, newBuff);
        }
    }

    /**
     * Returns elemental team buffs only for the bound owner.
     *
     * <p>Expired entries remain harmless historical windows until that element
     * is triggered again; {@link Buff#apply} enforces the half-open interval.</p>
     *
     * @param equippedOwner owner whose equipped weapon is being queried
     * @return current per-element windows, or an empty list for an unbound or wrong owner
     */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (owner == null || equippedOwner != owner) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(elementalBuffs.values()));
    }

    /**
     * Maps an eligible reaction to the elements whose party members are buffed.
     *
     * @param result resolved typed reaction
     * @return involved playable elements, or an empty set when ineligible
     */
    private Set<Element> getInvolvedElements(ReactionResult result) {
        switch (result.getKind()) {
            case OVERLOAD:
            case OVERLOADED:
                return EnumSet.of(Element.ELECTRO, Element.PYRO);
            case SUPERCONDUCT:
                return EnumSet.of(Element.ELECTRO, Element.CRYO);
            case ELECTRO_CHARGED:
            case LUNAR_CHARGED:
                return EnumSet.of(Element.ELECTRO, Element.HYDRO);
            case QUICKEN:
            case AGGRAVATE:
            case SPREAD:
            case HYPERBLOOM:
                return EnumSet.of(Element.ELECTRO, Element.DENDRO);
            case SWIRL:
                if (result.getRelatedElement() == Element.ELECTRO) {
                    return EnumSet.of(Element.ELECTRO, Element.ANEMO);
                }
                return Collections.emptySet();
            case CRYSTALLIZE:
                if (result.getRelatedElement() == Element.ELECTRO) {
                    return EnumSet.of(Element.ELECTRO, Element.GEO);
                }
                return Collections.emptySet();
            default:
                return Collections.emptySet();
        }
    }
}
