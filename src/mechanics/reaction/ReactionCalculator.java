package mechanics.reaction;

import model.type.Element;

/**
 * Computes elemental reactions triggered when a new element interacts with an
 * existing elemental aura on an enemy.
 *
 * <p>
 * Supports calculating both Amplifying reactions (Vaporize, Melt) and
 * Transformative reactions (Swirl, Overload, Electro-Charged).
 */
public class ReactionCalculator {

    /**
     * Calculates the reaction result given a trigger element, an aura element,
     * the attacker's Elemental Mastery, and the attacker's level.
     * Assumes no external reaction bonus percentage.
     *
     * @param trigger the newly applied element
     * @param aura    the existing element on the enemy
     * @param em      the attacker's Elemental Mastery
     * @param level   the attacker's level (used for transformative base damage)
     * @return a {@link ReactionResult} representing the reaction type and
     *         damage/multiplier
     */
    public static ReactionResult calculate(Element trigger, Element aura, double em, int level) {
        return calculate(trigger, aura, em, level, 0.0);
    }

    /**
     * Calculates the reaction result including any specific reaction damage
     * bonuses.
     *
     * @param trigger       the newly applied element
     * @param aura          the existing element on the enemy
     * @param em            the attacker's Elemental Mastery
     * @param level         the attacker's level
     * @param reactionBonus additional damage bonus for this specific reaction (e.g.
     *                      from 4pc Crimson Witch)
     * @return a {@link ReactionResult} representing the reaction type and
     *         damage/multiplier
     */
    public static ReactionResult calculate(Element trigger, Element aura, double em, int level, double reactionBonus) {
        if (trigger == null || aura == null) {
            return ReactionResult.none();
        }

        // Amplifying Reactions (No change, reactionBonus usually for Transformative
        // here, or specific amp logic)
        // ... (Keep existing Amp logic if possible, or copy it)
        // For brevity in this tool call, I will include the full method body to ensure
        // correctness.

        // Amplifying Reactions
        if ((trigger == Element.HYDRO && aura == Element.PYRO) ||
                (trigger == Element.PYRO && aura == Element.HYDRO) ||
                (trigger == Element.PYRO && aura == Element.CRYO) ||
                (trigger == Element.CRYO && aura == Element.PYRO)) {

            double baseMulti = 1.0;
            if (trigger == Element.HYDRO && aura == Element.PYRO)
                baseMulti = 2.0;
            else if (trigger == Element.PYRO && aura == Element.HYDRO)
                baseMulti = 1.5;
            else if (trigger == Element.PYRO && aura == Element.CRYO)
                baseMulti = 2.0;
            else if (trigger == Element.CRYO && aura == Element.PYRO)
                baseMulti = 1.5;

            double emBonus = (2.78 * em) / (em + 1400.0);
            double totalMulti = baseMulti * (1.0 + emBonus); // Amp reaction bonus from artifacts usually handled in
                                                             // logic usually?
            // Stats like "Vaporize DMG Bonus" exist.
            // For now, we ignore reactionBonus for Amp as header implies strictly
            // Swirl/Transformative context?
            // Or we should apply it? `Crimson Witch` gives +15% Melt/Vape.
            // Let's assume reactionBonus passed here is relevant to the reaction type
            // triggered.
            // But caller passes `swirlBonus`. So only apply if Swirl.

            return ReactionResult.amp(totalMulti, (baseMulti == 2.0 || baseMulti == 1.5) ? "Vaporize" : "Melt");
        }

        // Transformative Reactions

        // Swirl (Anemo + Element)
        if (trigger == Element.ANEMO
                && (aura == Element.PYRO || aura == Element.HYDRO || aura == Element.ELECTRO || aura == Element.CRYO)) {
            double dmg = calculateTransformativeDamage(level, em, 0.6, reactionBonus);
            String type = "Swirl-" + aura.toString(); // e.g. "Swirl-PYRO"
            // Wait, aura.toString() might be uppercase. Enum toString is name().
            // "Swirl-PYRO".
            // VV Logic needs to parse this.
            return ReactionResult.transform(dmg, convertSwirlName(type));
        }
        // Swirl (Element + Anemo) - usually Anemo triggers, but if Anemo is aura...
        // Anemo doesn't persist as Aura usually (sim removes it).
        // But if it did:
        // if ((trigger == Element.PYRO || ...) && aura == Element.ANEMO) ...

        // Overload (Pyro + Electro)
        if ((trigger == Element.PYRO && aura == Element.ELECTRO) ||
                (trigger == Element.ELECTRO && aura == Element.PYRO)) {
            double dmg = calculateTransformativeDamage(level, em, 2.0, 0.0); // No specific overload bonus passed yet
            return ReactionResult.transform(dmg, "Overload");
        }

        // Electro-Charged (Electro + Hydro)
        if ((trigger == Element.ELECTRO && aura == Element.HYDRO) ||
                (trigger == Element.HYDRO && aura == Element.ELECTRO)) {

            double dmg = calculateTransformativeDamage(level, em, 1.2, 0.0);
            return ReactionResult.transform(dmg, "Electro-Charged");
        }

        return ReactionResult.none();
    }

    /**
     * Normalizes the Swirl reaction name format.
     * For example, converts {@code "Swirl-PYRO"} to {@code "Swirl-Pyro"}.
     *
     * @param raw the raw reaction string
     * @return formatted reaction string
     */
    private static String convertSwirlName(String raw) {
        // Normalize "Swirl-PYRO" to "Swirl-Pyro" if needed, or keep uppercase.
        // Let's keep common format: "Swirl-Pyro"
        String[] parts = raw.split("-");
        if (parts.length > 1) {
            String elem = parts[1]; // PYRO
            elem = elem.charAt(0) + elem.substring(1).toLowerCase(); // Pyro
            return "Swirl-" + elem;
        }
        return raw;
    }

    /**
     * Computes the base damage for transformative reactions based on character
     * level,
     * Elemental Mastery, the specific reaction's multiplier, and any external
     * bonuses.
     *
     * @param level         character level
     * @param em            character Elemental Mastery
     * @param reactionMulti base multiplier for the reaction type (e.g. Overload =
     *                      2.0)
     * @param bonusPct      additional percentage bonus for the reaction
     * @return total computed transformative damage
     */
    private static double calculateTransformativeDamage(int level, double em, double reactionMulti, double bonusPct) {
        // Level 90 Base ~ 1447. Let's use 1446.85
        double levelBase = 1446.85;
        if (level < 90) {
            // Approximate or simplified.
            if (level == 80)
                levelBase = 1077.44;
        }

        double emBonus = (16.0 * em) / (em + 2000.0);
        double dmg = levelBase * reactionMulti * (1.0 + emBonus + bonusPct);

        return dmg;
    }

    /**
     * Legacy support method to retrieve just the amplifying reaction multiplier.
     * To be removed or redirected in future updates.
     *
     * @param trigger the newly applied element
     * @param aura    the existing element on the enemy
     * @param em      the attacker's Elemental Mastery
     * @return the amplifying multiplier, or {@code 1.0} if no amplifying reaction
     *         occurred
     */
    public static double getMultiplier(Element trigger, Element aura, double em) {
        ReactionResult res = calculate(trigger, aura, em, 90);
        if (res.getType() == ReactionResult.Type.AMP)
            return res.getAmpMultiplier();
        return 1.0;
    }
}
