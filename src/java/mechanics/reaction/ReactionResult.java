package mechanics.reaction;

import model.type.Element;

public class ReactionResult {
    public enum Type {
        NONE, AMP, TRANSFORMATIVE
    }

    public enum Kind {
        NONE,
        VAPORIZE,
        MELT,
        SWIRL,
        OVERLOAD,
        SUPERCONDUCT,
        OVERLOADED,
        QUICKEN,
        AGGRAVATE,
        HYPERBLOOM,
        BLOOM,
        CRYSTALLIZE,
        ELECTRO_CHARGED,
        LUNAR_CHARGED,
        LUNAR_BLOOM,
        LUNAR_CRYSTALLIZE,
        THUNDERCLOUD_STRIKE,
        OTHER
    }

    public enum LunarType {
        NONE,
        CHARGED,
        BLOOM,
        CRYSTALLIZE
    }

    private Type type;
    private double ampMultiplier;
    private double transformDamage;
    private String name;
    private Kind kind;
    private Element relatedElement;
    private LunarType lunarType;

    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name) {
        this(type, ampMultiplier, transformDamage, name, inferKind(name), inferLunarType(name), null);
    }

    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name, Kind kind) {
        this(type, ampMultiplier, transformDamage, name, kind, inferLunarType(kind), null);
    }

    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name, Kind kind,
            Element relatedElement) {
        this(type, ampMultiplier, transformDamage, name, kind, inferLunarType(kind), relatedElement);
    }

    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name, Kind kind,
            LunarType lunarType, Element relatedElement) {
        this.type = type;
        this.ampMultiplier = ampMultiplier;
        this.transformDamage = transformDamage;
        this.name = name;
        this.kind = kind;
        this.lunarType = lunarType;
        this.relatedElement = relatedElement;
    }

    public static ReactionResult none() {
        return new ReactionResult(Type.NONE, 1.0, 0.0, "None", Kind.NONE);
    }

    public static ReactionResult amp(double multiplier, String name) {
        return new ReactionResult(Type.AMP, multiplier, 0.0, name);
    }

    public static ReactionResult amp(double multiplier, String name, Kind kind) {
        return new ReactionResult(Type.AMP, multiplier, 0.0, name, kind);
    }

    public static ReactionResult transform(double damage, String name) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name);
    }

    public static ReactionResult transform(double damage, String name, Kind kind) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name, kind);
    }

    public static ReactionResult transform(double damage, String name, Kind kind, Element relatedElement) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name, kind, relatedElement);
    }

    public static ReactionResult lunar(double damage, LunarType lunarType) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, canonicalLunarName(lunarType),
                kindFromLunarType(lunarType), lunarType, null);
    }

    public Type getType() {
        return type;
    }

    public double getAmpMultiplier() {
        return ampMultiplier;
    }

    public double getTransformDamage() {
        return transformDamage;
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public Element getRelatedElement() {
        return relatedElement;
    }

    public LunarType getLunarType() {
        return lunarType;
    }

    public boolean isElectroCharged() {
        return kind == Kind.ELECTRO_CHARGED || kind == Kind.LUNAR_CHARGED;
    }

    public boolean isSwirl() {
        return kind == Kind.SWIRL;
    }

    public boolean isThundercloudStrike() {
        return kind == Kind.THUNDERCLOUD_STRIKE;
    }

    public boolean isLunarReaction() {
        return kind == Kind.LUNAR_CHARGED
                || kind == Kind.LUNAR_BLOOM
                || kind == Kind.LUNAR_CRYSTALLIZE;
    }

    /**
     * Returns the swirled element when this result represents a Swirl reaction.
     *
     * @return infused Swirl element, or {@code null} if unavailable
     */
    public Element getSwirlElement() {
        if (!isSwirl()) {
            return null;
        }
        if (relatedElement != null) {
            return relatedElement;
        }
        return null;
    }

    /**
     * Returns the corresponding Lunar reaction label for reaction listeners that
     * still need the legacy Lunar naming bridge.
     *
     * @return canonical Lunar reaction name, or {@code null} if this result does not
     *         map to a Lunar reaction
     */
    public String getCanonicalLunarReactionName() {
        if (lunarType != LunarType.NONE) {
            return canonicalLunarName(lunarType);
        }
        if (kind == Kind.ELECTRO_CHARGED) {
            return canonicalLunarName(LunarType.CHARGED);
        }
        if (kind == Kind.BLOOM) {
            return canonicalLunarName(LunarType.BLOOM);
        }
        if (kind == Kind.CRYSTALLIZE && relatedElement == Element.HYDRO) {
            return canonicalLunarName(LunarType.CRYSTALLIZE);
        }
        return null;
    }

    /**
     * Returns whether this reaction should count for Electro resonance particle
     * generation.
     *
     * @return {@code true} if High Voltage should trigger on this reaction
     */
    public boolean triggersElectroResonance() {
        if (isElectroCharged() || isLunarReaction()) {
            return true;
        }
        return kind == Kind.SUPERCONDUCT
                || kind == Kind.OVERLOADED
                || kind == Kind.OVERLOAD
                || kind == Kind.QUICKEN
                || kind == Kind.AGGRAVATE
                || kind == Kind.HYPERBLOOM;
    }

    private static Kind inferKind(String name) {
        if (name == null) {
            return Kind.NONE;
        }
        return Kind.OTHER;
    }

    private static LunarType inferLunarType(String name) {
        Kind kind = inferKind(name);
        return inferLunarType(kind);
    }

    private static LunarType inferLunarType(Kind kind) {
        switch (kind) {
            case LUNAR_CHARGED:
                return LunarType.CHARGED;
            case LUNAR_BLOOM:
                return LunarType.BLOOM;
            case LUNAR_CRYSTALLIZE:
                return LunarType.CRYSTALLIZE;
            default:
                return LunarType.NONE;
        }
    }

    private static Kind kindFromLunarType(LunarType lunarType) {
        switch (lunarType) {
            case CHARGED:
                return Kind.LUNAR_CHARGED;
            case BLOOM:
                return Kind.LUNAR_BLOOM;
            case CRYSTALLIZE:
                return Kind.LUNAR_CRYSTALLIZE;
            default:
                return Kind.NONE;
        }
    }

    private static String canonicalLunarName(LunarType lunarType) {
        switch (lunarType) {
            case CHARGED:
                return "Lunar-Charged";
            case BLOOM:
                return "Lunar-Bloom";
            case CRYSTALLIZE:
                return "Lunar-Crystallize";
            default:
                return null;
        }
    }
}
