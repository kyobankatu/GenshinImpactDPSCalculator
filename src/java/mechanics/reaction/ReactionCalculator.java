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
    private static final double LEVEL_80_MULTIPLIER = 1077.44;
    private static final double LEVEL_90_MULTIPLIER = 1446.85;
    private static final double BURNING_MULTIPLIER = 0.25;
    private static final double SWIRL_MULTIPLIER = 0.6;
    private static final double SUPERCONDUCT_MULTIPLIER = 1.5;
    private static final double ELECTRO_CHARGED_MULTIPLIER = 2.0;
    private static final double BLOOM_MULTIPLIER = 2.0;
    private static final double OVERLOADED_MULTIPLIER = 2.75;
    private static final double HYPERBLOOM_MULTIPLIER = 3.0;
    private static final double BURGEON_MULTIPLIER = 3.0;
    private static final double SHATTER_MULTIPLIER = 3.0;
    private static final double AGGRAVATE_MULTIPLIER = 1.15;
    private static final double SPREAD_MULTIPLIER = 1.25;

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

            boolean vaporize = (trigger == Element.HYDRO && aura == Element.PYRO)
                    || (trigger == Element.PYRO && aura == Element.HYDRO);
            String reactionName = vaporize ? "Vaporize" : "Melt";
            ReactionResult.Kind reactionKind = vaporize
                    ? ReactionResult.Kind.VAPORIZE
                    : ReactionResult.Kind.MELT;
            return ReactionResult.amp(totalMulti, reactionName, reactionKind);
        }

        // Transformative Reactions

        // Quicken (Dendro + Electro). This creates a persistent aura; Aggravate
        // and Spread are handled by the simulator while the aura is active.
        if ((trigger == Element.DENDRO && aura == Element.ELECTRO) ||
                (trigger == Element.ELECTRO && aura == Element.DENDRO)) {
            return ReactionResult.state("Quicken", ReactionResult.Kind.QUICKEN, null);
        }

        // Frozen (Hydro + Cryo). The simulator treats immobilization as non-offensive
        // state; Shatter consumes this state later.
        if ((trigger == Element.HYDRO && aura == Element.CRYO) ||
                (trigger == Element.CRYO && aura == Element.HYDRO)) {
            return ReactionResult.state("Frozen", ReactionResult.Kind.FROZEN, null);
        }

        // Burning (Pyro + Dendro). This creates a persistent Burning state whose
        // Pyro ticks are scheduled by the simulator.
        if ((trigger == Element.PYRO && aura == Element.DENDRO) ||
                (trigger == Element.DENDRO && aura == Element.PYRO)) {
            double dmg = calculateTransformativeDamage(level, em, BURNING_MULTIPLIER, 0.0);
            return ReactionResult.stateDamage(dmg, "Burning", ReactionResult.Kind.BURNING, null, Element.PYRO);
        }

        // Bloom (Hydro + Dendro). The reaction creates a Dendro Core; the damage
        // value here is stored for the delayed core explosion.
        if ((trigger == Element.HYDRO && aura == Element.DENDRO) ||
                (trigger == Element.DENDRO && aura == Element.HYDRO)) {
            double dmg = calculateTransformativeDamage(level, em, BLOOM_MULTIPLIER, reactionBonus);
            return ReactionResult.stateDamage(dmg, "Bloom", ReactionResult.Kind.BLOOM, null, Element.DENDRO);
        }

        // Crystallize (Geo + Pyro/Hydro/Electro/Cryo). Shield pickup is defensive
        // and omitted by this single-target maximum-DPS simulator.
        if (trigger == Element.GEO
                && (aura == Element.PYRO || aura == Element.HYDRO || aura == Element.ELECTRO || aura == Element.CRYO)) {
            return ReactionResult.state("Crystallize-" + formatElement(aura), ReactionResult.Kind.CRYSTALLIZE, aura);
        }

        // Swirl (Anemo + Element)
        if (trigger == Element.ANEMO
                && (aura == Element.PYRO || aura == Element.HYDRO || aura == Element.ELECTRO || aura == Element.CRYO)) {
            double dmg = calculateTransformativeDamage(level, em, SWIRL_MULTIPLIER, reactionBonus);
            return ReactionResult.transform(dmg, convertSwirlName(aura), ReactionResult.Kind.SWIRL, aura, aura);
        }
        // Swirl (Element + Anemo) - usually Anemo triggers, but if Anemo is aura...
        // Anemo doesn't persist as Aura usually (sim removes it).
        // But if it did:
        // if ((trigger == Element.PYRO || ...) && aura == Element.ANEMO) ...

        // Overload (Pyro + Electro)
        if ((trigger == Element.PYRO && aura == Element.ELECTRO) ||
                (trigger == Element.ELECTRO && aura == Element.PYRO)) {
            double dmg = calculateTransformativeDamage(level, em, OVERLOADED_MULTIPLIER, 0.0);
            return ReactionResult.transform(dmg, "Overloaded", ReactionResult.Kind.OVERLOAD, null, Element.PYRO);
        }

        // Superconduct (Cryo + Electro)
        if ((trigger == Element.CRYO && aura == Element.ELECTRO) ||
                (trigger == Element.ELECTRO && aura == Element.CRYO)) {
            double dmg = calculateTransformativeDamage(level, em, SUPERCONDUCT_MULTIPLIER, 0.0);
            return ReactionResult.transform(dmg, "Superconduct", ReactionResult.Kind.SUPERCONDUCT,
                    null, Element.CRYO);
        }

        // Electro-Charged (Electro + Hydro)
        if ((trigger == Element.ELECTRO && aura == Element.HYDRO) ||
                (trigger == Element.HYDRO && aura == Element.ELECTRO)) {

            double dmg = calculateTransformativeDamage(level, em, ELECTRO_CHARGED_MULTIPLIER, 0.0);
            return ReactionResult.transform(dmg, "Electro-Charged", ReactionResult.Kind.ELECTRO_CHARGED,
                    null, Element.ELECTRO);
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
    private static String convertSwirlName(Element aura) {
        return "Swirl-" + formatElement(aura);
    }

    private static String formatElement(Element element) {
        String elem = element.name();
        return elem.charAt(0) + elem.substring(1).toLowerCase();
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
    public static ReactionResult calculateShatter(double em, int level, double reactionBonus) {
        double dmg = calculateTransformativeDamage(level, em, SHATTER_MULTIPLIER, reactionBonus);
        return ReactionResult.transform(dmg, "Shatter", ReactionResult.Kind.SHATTER, null, Element.PHYSICAL);
    }

    public static ReactionResult calculateHyperbloom(double em, int level, double reactionBonus) {
        double dmg = calculateTransformativeDamage(level, em, HYPERBLOOM_MULTIPLIER, reactionBonus);
        return ReactionResult.transform(dmg, "Hyperbloom", ReactionResult.Kind.HYPERBLOOM, null, Element.DENDRO);
    }

    public static ReactionResult calculateBurgeon(double em, int level, double reactionBonus) {
        double dmg = calculateTransformativeDamage(level, em, BURGEON_MULTIPLIER, reactionBonus);
        return ReactionResult.transform(dmg, "Burgeon", ReactionResult.Kind.BURGEON, null, Element.DENDRO);
    }

    public static ReactionResult calculateAggravate(double em, int level, double reactionBonus) {
        double dmg = calculateAdditiveReactionDamage(level, em, AGGRAVATE_MULTIPLIER, reactionBonus);
        return ReactionResult.additive(dmg, "Aggravate", ReactionResult.Kind.AGGRAVATE, Element.ELECTRO);
    }

    public static ReactionResult calculateSpread(double em, int level, double reactionBonus) {
        double dmg = calculateAdditiveReactionDamage(level, em, SPREAD_MULTIPLIER, reactionBonus);
        return ReactionResult.additive(dmg, "Spread", ReactionResult.Kind.SPREAD, Element.DENDRO);
    }

    public static double calculateTransformativeDamage(int level, double em, double reactionMulti, double bonusPct) {
        double levelBase = levelMultiplier(level);

        double emBonus = (16.0 * em) / (em + 2000.0);
        double dmg = levelBase * reactionMulti * (1.0 + emBonus + bonusPct);

        return dmg;
    }

    public static double calculateAdditiveReactionDamage(int level, double em, double reactionMulti, double bonusPct) {
        double levelBase = levelMultiplier(level);
        double emBonus = (5.0 * em) / (em + 1200.0);
        return levelBase * reactionMulti * (1.0 + emBonus + bonusPct);
    }

    private static double levelMultiplier(int level) {
        if (level >= 90) {
            return LEVEL_90_MULTIPLIER;
        }
        if (level >= 80) {
            return LEVEL_80_MULTIPLIER;
        }
        return LEVEL_90_MULTIPLIER;
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
