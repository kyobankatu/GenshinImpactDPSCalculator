package mechanics.reaction;

public class ReactionResult {
    public enum Type {
        NONE, AMP, TRANSFORMATIVE
    }

    private Type type;
    private double ampMultiplier;
    private double transformDamage;
    private String name;

    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name) {
        this.type = type;
        this.ampMultiplier = ampMultiplier;
        this.transformDamage = transformDamage;
        this.name = name;
    }

    public static ReactionResult none() {
        return new ReactionResult(Type.NONE, 1.0, 0.0, "None");
    }

    public static ReactionResult amp(double multiplier, String name) {
        return new ReactionResult(Type.AMP, multiplier, 0.0, name);
    }

    public static ReactionResult transform(double damage, String name) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name);
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
}
