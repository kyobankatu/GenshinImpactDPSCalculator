package model.artifact;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareArtifact;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Scroll of the Hero of Cinder City's supported non-Nightsoul team bonus.
 *
 * <p>An owner-attributed elemental reaction grants 12% team Elemental DMG for
 * each typed element involved for 15 seconds, including while the owner is off
 * field. The owner's own element must be in that set. Each element uses a
 * distinct typed replacement id, matching same-set non-stacking while keeping
 * independently expiring elements. Nightsoul Energy and the additional 28%
 * Nightsoul branch remain inactive.</p>
 */
public class ScrollOfTheHeroOfCinderCity extends ArtifactSet
        implements ReactionAwareArtifact {
    private static final double EFFECT_DURATION = 15.0;
    private static final double ELEMENTAL_DAMAGE_BONUS = 0.12;

    /** Constructs the set with a fresh stat container. */
    public ScrollOfTheHeroOfCinderCity() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ScrollOfTheHeroOfCinderCity(StatsContainer stats) {
        super("Scroll of the Hero of Cinder City",
                Objects.requireNonNull(stats, "stats"));
    }

    /**
     * Applies typed per-element team buffs for one owner-attributed reaction.
     *
     * <p>The simulator dispatches this callback after the current attack stats
     * have been captured. Consequently, these buffs start at reaction time but
     * affect subsequent damage calculations rather than the triggering hit.</p>
     *
     * @param sim simulator dispatching the reaction
     * @param result typed reaction result
     * @param triggerCharacter character attributed as the reaction trigger
     * @param owner character carrying this set
     */
    @Override
    public void onReaction(
            CombatSimulator sim,
            ReactionResult result,
            Character triggerCharacter,
            Character owner) {
        if (sim == null
                || result == null
                || triggerCharacter == null
                || owner == null
                || triggerCharacter != owner
                || result.getType() == null
                || result.getType() == ReactionResult.Type.NONE) {
            return;
        }

        Set<Element> elements = getReactionElements(result);
        Element ownerElement = owner.getElement();
        if (ownerElement == null || !elements.contains(ownerElement)) {
            return;
        }

        for (Element element : elements) {
            StatType statType = getDamageBonusStat(element);
            BuffId buffId = getBuffId(element);
            sim.applyTeamBuffNoStack(new SimpleBuff(
                    "Scroll of Cinder City: " + element + " DMG Bonus",
                    buffId,
                    EFFECT_DURATION,
                    sim.getCurrentTime(),
                    stats -> stats.add(statType, ELEMENTAL_DAMAGE_BONUS))
                    .sourcedBy(owner.getCharacterId()));
        }
    }

    /** Returns the canonical typed element set for a reaction result. */
    private Set<Element> getReactionElements(ReactionResult result) {
        EnumSet<Element> elements = EnumSet.noneOf(Element.class);
        if (result.getKind() == null) {
            return elements;
        }
        switch (result.getKind()) {
            case VAPORIZE:
                return elements(Element.PYRO, Element.HYDRO);
            case MELT:
                return elements(Element.PYRO, Element.CRYO);
            case OVERLOAD:
            case OVERLOADED:
                return elements(Element.PYRO, Element.ELECTRO);
            case SUPERCONDUCT:
                return elements(Element.CRYO, Element.ELECTRO);
            case ELECTRO_CHARGED:
                return elements(Element.ELECTRO, Element.HYDRO);
            case FROZEN:
                return elements(Element.CRYO, Element.HYDRO);
            case SWIRL:
                return elementsWithRelated(Element.ANEMO,
                        result.getRelatedElement());
            case CRYSTALLIZE:
                return elementsWithRelated(Element.GEO,
                        result.getRelatedElement());
            case QUICKEN:
            case AGGRAVATE:
                return elements(Element.DENDRO, Element.ELECTRO);
            case SPREAD:
                return elements(Element.DENDRO);
            case BLOOM:
                return elements(Element.DENDRO, Element.HYDRO);
            case HYPERBLOOM:
                return elements(Element.DENDRO, Element.ELECTRO);
            case BURGEON:
            case BURNING:
                return elements(Element.DENDRO, Element.PYRO);
            case LUNAR_CHARGED:
                if (result.getDamageElement() != null) {
                    return elements(Element.ELECTRO, Element.HYDRO);
                }
                return elements;
            case LUNAR_BLOOM:
                if (result.getDamageElement() != null) {
                    return elements(Element.DENDRO, Element.HYDRO);
                }
                return elements;
            case LUNAR_CRYSTALLIZE:
                if (result.getDamageElement() != null
                        && (result.getRelatedElement() == null
                                || result.getRelatedElement() == Element.HYDRO)) {
                    return elements(Element.GEO, Element.HYDRO);
                }
                return elements;
            case NONE:
            case SHATTER:
            case THUNDERCLOUD_STRIKE:
            case OTHER:
            default:
                return elements;
        }
    }

    /** Creates one mutable typed element set from the supplied elements. */
    private EnumSet<Element> elements(Element first, Element... remaining) {
        EnumSet<Element> elements = EnumSet.of(first);
        for (Element element : remaining) {
            elements.add(element);
        }
        return elements;
    }

    /** Creates a Swirl or Crystallize element set with validated metadata. */
    private EnumSet<Element> elementsWithRelated(
            Element baseElement,
            Element relatedElement) {
        EnumSet<Element> elements = EnumSet.noneOf(Element.class);
        if (!isReactiveAuraElement(relatedElement)) {
            return elements;
        }
        elements.add(baseElement);
        elements.add(relatedElement);
        return elements;
    }

    /** Tests whether typed reaction metadata names a supported Aura element. */
    private boolean isReactiveAuraElement(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO;
    }

    /** Maps one elemental bonus to its structural stat type. */
    private StatType getDamageBonusStat(Element element) {
        switch (element) {
            case PYRO:
                return StatType.PYRO_DMG_BONUS;
            case HYDRO:
                return StatType.HYDRO_DMG_BONUS;
            case ELECTRO:
                return StatType.ELECTRO_DMG_BONUS;
            case CRYO:
                return StatType.CRYO_DMG_BONUS;
            case ANEMO:
                return StatType.ANEMO_DMG_BONUS;
            case GEO:
                return StatType.GEO_DMG_BONUS;
            case DENDRO:
                return StatType.DENDRO_DMG_BONUS;
            case PHYSICAL:
            default:
                throw new IllegalArgumentException(
                        "Unsupported Scroll element: " + element);
        }
    }

    /** Maps one elemental bonus to its same-set replacement id. */
    private BuffId getBuffId(Element element) {
        switch (element) {
            case PYRO:
                return BuffId.SCROLL_CINDER_CITY_PYRO_DMG_BONUS;
            case HYDRO:
                return BuffId.SCROLL_CINDER_CITY_HYDRO_DMG_BONUS;
            case ELECTRO:
                return BuffId.SCROLL_CINDER_CITY_ELECTRO_DMG_BONUS;
            case CRYO:
                return BuffId.SCROLL_CINDER_CITY_CRYO_DMG_BONUS;
            case ANEMO:
                return BuffId.SCROLL_CINDER_CITY_ANEMO_DMG_BONUS;
            case GEO:
                return BuffId.SCROLL_CINDER_CITY_GEO_DMG_BONUS;
            case DENDRO:
                return BuffId.SCROLL_CINDER_CITY_DENDRO_DMG_BONUS;
            case PHYSICAL:
            default:
                throw new IllegalArgumentException(
                        "Unsupported Scroll element: " + element);
        }
    }
}
