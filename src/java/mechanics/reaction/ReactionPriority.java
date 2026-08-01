package mechanics.reaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.type.Element;

/**
 * Defines deterministic ordinary-Aura reaction attempt order by trigger.
 *
 * <p>This policy orders only elemental Auras stored by the target. Persistent
 * synthetic states such as Frozen, Burning, and Quicken retain their dedicated
 * resolver paths.
 */
public final class ReactionPriority {
    private static final Map<Element, List<Element>> PRIORITIES = buildPriorities();

    private ReactionPriority() {
    }

    /**
     * Returns active Auras in deterministic reaction-attempt order.
     *
     * <p>Known reactive Auras follow the trigger-specific policy. Any remaining
     * same-element or unsupported Aura follows {@link Element} declaration order
     * so nonreactive extension behavior is stable as well.
     *
     * @param trigger newly applied element
     * @param activeAuras current ordinary target Auras
     * @return immutable ordered Aura list
     */
    public static List<Element> orderAuras(
            Element trigger, Set<Element> activeAuras) {
        if (activeAuras == null || activeAuras.isEmpty()) {
            return Collections.emptyList();
        }
        EnumSet<Element> remaining = EnumSet.copyOf(activeAuras);
        List<Element> ordered = new ArrayList<>(remaining.size());
        List<Element> priority = PRIORITIES.get(trigger);
        if (priority != null) {
            for (Element aura : priority) {
                if (remaining.remove(aura)) {
                    ordered.add(aura);
                }
            }
        }
        for (Element aura : Element.values()) {
            if (remaining.remove(aura)) {
                ordered.add(aura);
            }
        }
        return Collections.unmodifiableList(ordered);
    }

    private static Map<Element, List<Element>> buildPriorities() {
        Map<Element, List<Element>> priorities = new EnumMap<>(Element.class);
        priorities.put(Element.ELECTRO, list(
                Element.PYRO, Element.HYDRO, Element.CRYO, Element.DENDRO));
        priorities.put(Element.PYRO, list(
                Element.ELECTRO, Element.HYDRO, Element.CRYO, Element.DENDRO));
        priorities.put(Element.CRYO, list(
                Element.ELECTRO, Element.PYRO, Element.HYDRO));
        priorities.put(Element.HYDRO, list(
                Element.PYRO, Element.CRYO, Element.DENDRO, Element.ELECTRO));
        priorities.put(Element.ANEMO, list(
                Element.ELECTRO, Element.PYRO, Element.HYDRO, Element.CRYO));
        priorities.put(Element.GEO, list(
                Element.ELECTRO, Element.HYDRO, Element.CRYO, Element.PYRO));
        priorities.put(Element.DENDRO, list(
                Element.ELECTRO, Element.PYRO, Element.HYDRO));
        return Collections.unmodifiableMap(priorities);
    }

    private static List<Element> list(Element... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
}
