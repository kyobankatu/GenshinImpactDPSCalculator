package visualization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.type.Element;
import simulation.CombatSimulator;

/**
 * Chooses chart colors for report actors.
 */
final class ElementColorPalette {
    private ElementColorPalette() {
    }

    /**
     * Returns chart colors for the given actor names, picking a variant per
     * element so that multiple characters of the same element are distinguishable.
     *
     * @param names actor display names to color
     * @param sim   combat simulator used to resolve each actor's element; may be
     *              {@code null}
     * @return JS-literal color strings (e.g. {@code "'#FF2222'"}) aligned with
     *         {@code names}
     */
    static String[] colorsFor(List<String> names, CombatSimulator sim) {
        Map<String, String> colorMap = new HashMap<>();
        Map<Element, Integer> elementCounts = new HashMap<>();

        if (sim != null) {
            for (String name : names) {
                model.entity.Character character = sim.getCharacter(name);
                if (character != null && character.getElement() != null) {
                    Element element = character.getElement();
                    int count = elementCounts.getOrDefault(element, 0);
                    colorMap.put(name, colorFor(element, count));
                    elementCounts.put(element, count + 1);
                } else {
                    colorMap.put(name, "'#AAAAAA'");
                }
            }
        }

        return names.stream()
                .map(name -> colorMap.getOrDefault(name, "'#AAAAAA'"))
                .toArray(String[]::new);
    }

    /**
     * Picks a JS-literal color string for the given element and variant index.
     *
     * @param element element to color
     * @param variant 0-based variant index used to disambiguate same-element actors
     * @return JS-literal hex color string
     */
    private static String colorFor(Element element, int variant) {
        int v = variant % 4;
        switch (element) {
            case PYRO:
                return v == 0 ? "'#FF2222'" : v == 1 ? "'#FFAAAA'" : v == 2 ? "'#990000'" : "'#FF5500'";
            case HYDRO:
                return v == 0 ? "'#3388FF'" : v == 1 ? "'#00CCFF'" : v == 2 ? "'#0055FF'" : "'#66B2FF'";
            case ELECTRO:
                return v == 0 ? "'#A066D3'" : v == 1 ? "'#D480FF'" : v == 2 ? "'#8800CC'" : "'#CC99FF'";
            case CRYO:
                return v == 0 ? "'#99FFFF'" : v == 1 ? "'#00FFFF'" : v == 2 ? "'#66CCCC'" : "'#CCFFFF'";
            case ANEMO:
                return v == 0 ? "'#33FF99'" : v == 1 ? "'#00FFCC'" : v == 2 ? "'#66FF66'" : "'#00CC99'";
            case GEO:
                return v == 0 ? "'#FFE699'" : v == 1 ? "'#FFD700'" : v == 2 ? "'#FFAA00'" : "'#E6C200'";
            case DENDRO:
                return v == 0 ? "'#A5C882'" : v == 1 ? "'#77FF00'" : v == 2 ? "'#33CC33'" : "'#66AA44'";
            case PHYSICAL:
            default:
                return "'#AAAAAA'";
        }
    }
}
