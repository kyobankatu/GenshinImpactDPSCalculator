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
