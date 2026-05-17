package mechanics.reaction;

import model.type.Element;

/**
 * 反応計算の結果を表す値オブジェクト。
 *
 * <p>{@link mechanics.reaction.ReactionCalculator} の計算結果を、
 * 増幅反応 (Amp) の倍率、変化反応 (Transformative) のダメージ値、表示名、
 * 反応種別 ({@link Kind})、関連元素、Lunar 反応種別 ({@link LunarType}) としてまとめて保持する。</p>
 *
 * <p>本クラスは不変ではないがフィールドは生成時に確定し、外部からは getter 経由でのみ参照される。
 * インスタンスは {@link #none()} / {@link #amp(double, String)} / {@link #transform(double, String)}
 * 等の static ファクトリ経由で生成することを推奨する。</p>
 */
public class ReactionResult {
    /**
     * 反応の大分類を表す列挙。
     */
    public enum Type {
        /** 反応なし。 */
        NONE,
        /** 増幅反応 (Vaporize / Melt 等)。倍率としてダメージに乗算される。 */
        AMP,
        /** 変化反応 (Swirl / Overload / Bloom 等)。独立したダメージインスタンスを発生させる。 */
        TRANSFORMATIVE
    }

    /**
     * 反応の具体種別を表す列挙。
     */
    public enum Kind {
        /** 反応なし。 */
        NONE,
        /** 蒸発反応。 */
        VAPORIZE,
        /** 溶解反応。 */
        MELT,
        /** 拡散反応。 */
        SWIRL,
        /** 過負荷反応 (新表記)。 */
        OVERLOAD,
        /** 超電導反応。 */
        SUPERCONDUCT,
        /** 過負荷反応 (旧表記)。 */
        OVERLOADED,
        /** 草元素付着 (原激化) 状態。 */
        QUICKEN,
        /** 超激化反応。 */
        AGGRAVATE,
        /** 超開花反応。 */
        HYPERBLOOM,
        /** 開花反応。 */
        BLOOM,
        /** 結晶反応。 */
        CRYSTALLIZE,
        /** 感電反応。 */
        ELECTRO_CHARGED,
        /** Lunar 拡張: 感電に対応する非公式反応。 */
        LUNAR_CHARGED,
        /** Lunar 拡張: 開花に対応する非公式反応。 */
        LUNAR_BLOOM,
        /** Lunar 拡張: 結晶に対応する非公式反応。 */
        LUNAR_CRYSTALLIZE,
        /** 雷霆万鈞 (Thundercloud Strike) による特殊変化反応。 */
        THUNDERCLOUD_STRIKE,
        /** その他、未分類の反応。 */
        OTHER
    }

    /**
     * Lunar 系反応の細分種別。
     */
    public enum LunarType {
        /** Lunar 反応でない、または該当しない。 */
        NONE,
        /** Lunar-Charged (感電に対応)。 */
        CHARGED,
        /** Lunar-Bloom (開花に対応)。 */
        BLOOM,
        /** Lunar-Crystallize (結晶に対応)。 */
        CRYSTALLIZE
    }

    /** 反応の大分類。 */
    private Type type;
    /** 増幅反応倍率。{@link Type#AMP} 以外では 1.0。 */
    private double ampMultiplier;
    /** 変化反応ダメージ。{@link Type#TRANSFORMATIVE} 以外では 0.0。 */
    private double transformDamage;
    /** 表示用反応名。 */
    private String name;
    /** 反応の具体種別。 */
    private Kind kind;
    /** 関連元素 (Swirl の拡散元素、Crystallize の付着元素など)。 */
    private Element relatedElement;
    /** Lunar 反応細分。 */
    private LunarType lunarType;

    /**
     * 反応種別と倍率/ダメージ、名称から {@code ReactionResult} を構築する。
     * {@link Kind} と {@link LunarType} は名称から推定される。
     *
     * @param type            反応の大分類
     * @param ampMultiplier   増幅反応倍率 (AMP 以外では 1.0)
     * @param transformDamage 変化反応ダメージ (TRANSFORMATIVE 以外では 0.0)
     * @param name            表示用反応名
     */
    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name) {
        this(type, ampMultiplier, transformDamage, name, inferKind(name), inferLunarType(name), null);
    }

    /**
     * Kind を明示して {@code ReactionResult} を構築する。
     *
     * @param type            反応の大分類
     * @param ampMultiplier   増幅反応倍率
     * @param transformDamage 変化反応ダメージ
     * @param name            表示用反応名
     * @param kind            反応の具体種別
     */
    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name, Kind kind) {
        this(type, ampMultiplier, transformDamage, name, kind, inferLunarType(kind), null);
    }

    /**
     * Kind と関連元素を明示して {@code ReactionResult} を構築する。
     *
     * @param type            反応の大分類
     * @param ampMultiplier   増幅反応倍率
     * @param transformDamage 変化反応ダメージ
     * @param name            表示用反応名
     * @param kind            反応の具体種別
     * @param relatedElement  関連元素 (Swirl 拡散元素など)
     */
    public ReactionResult(Type type, double ampMultiplier, double transformDamage, String name, Kind kind,
            Element relatedElement) {
        this(type, ampMultiplier, transformDamage, name, kind, inferLunarType(kind), relatedElement);
    }

    /**
     * すべてのフィールドを明示して {@code ReactionResult} を構築する。
     *
     * @param type            反応の大分類
     * @param ampMultiplier   増幅反応倍率
     * @param transformDamage 変化反応ダメージ
     * @param name            表示用反応名
     * @param kind            反応の具体種別
     * @param lunarType       Lunar 反応細分
     * @param relatedElement  関連元素
     */
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

    /**
     * 反応が発生しなかったことを表す空の結果を生成する。
     *
     * @return 反応なしを表す {@code ReactionResult}
     */
    public static ReactionResult none() {
        return new ReactionResult(Type.NONE, 1.0, 0.0, "None", Kind.NONE);
    }

    /**
     * 増幅反応 (Vaporize / Melt) の結果を生成する。
     *
     * @param multiplier 増幅倍率
     * @param name       表示用反応名
     * @return 増幅反応の {@code ReactionResult}
     */
    public static ReactionResult amp(double multiplier, String name) {
        return new ReactionResult(Type.AMP, multiplier, 0.0, name);
    }

    /**
     * Kind を明示した増幅反応の結果を生成する。
     *
     * @param multiplier 増幅倍率
     * @param name       表示用反応名
     * @param kind       反応の具体種別
     * @return 増幅反応の {@code ReactionResult}
     */
    public static ReactionResult amp(double multiplier, String name, Kind kind) {
        return new ReactionResult(Type.AMP, multiplier, 0.0, name, kind);
    }

    /**
     * 変化反応の結果を生成する。
     *
     * @param damage 変化反応ダメージ
     * @param name   表示用反応名
     * @return 変化反応の {@code ReactionResult}
     */
    public static ReactionResult transform(double damage, String name) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name);
    }

    /**
     * Kind を明示した変化反応の結果を生成する。
     *
     * @param damage 変化反応ダメージ
     * @param name   表示用反応名
     * @param kind   反応の具体種別
     * @return 変化反応の {@code ReactionResult}
     */
    public static ReactionResult transform(double damage, String name, Kind kind) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name, kind);
    }

    /**
     * Kind と関連元素を明示した変化反応の結果を生成する。
     *
     * @param damage          変化反応ダメージ
     * @param name            表示用反応名
     * @param kind            反応の具体種別
     * @param relatedElement  関連元素
     * @return 変化反応の {@code ReactionResult}
     */
    public static ReactionResult transform(double damage, String name, Kind kind, Element relatedElement) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, name, kind, relatedElement);
    }

    /**
     * Lunar 反応の結果を生成する。
     *
     * @param damage    Lunar 反応ダメージ
     * @param lunarType Lunar 反応細分
     * @return Lunar 反応の {@code ReactionResult}
     */
    public static ReactionResult lunar(double damage, LunarType lunarType) {
        return new ReactionResult(Type.TRANSFORMATIVE, 1.0, damage, canonicalLunarName(lunarType),
                kindFromLunarType(lunarType), lunarType, null);
    }

    /**
     * 反応の大分類を返す。
     *
     * @return 反応の {@link Type}
     */
    public Type getType() {
        return type;
    }

    /**
     * 増幅反応倍率を返す。
     *
     * @return 増幅反応倍率 (AMP 以外では 1.0)
     */
    public double getAmpMultiplier() {
        return ampMultiplier;
    }

    /**
     * 変化反応ダメージを返す。
     *
     * @return 変化反応ダメージ (TRANSFORMATIVE 以外では 0.0)
     */
    public double getTransformDamage() {
        return transformDamage;
    }

    /**
     * 表示用反応名を返す。
     *
     * @return 反応名
     */
    public String getName() {
        return name;
    }

    /**
     * 反応の具体種別を返す。
     *
     * @return 反応の {@link Kind}
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * 関連元素を返す。
     *
     * @return Swirl の拡散元素や Crystallize の付着元素など、関連する {@link Element}。なければ {@code null}。
     */
    public Element getRelatedElement() {
        return relatedElement;
    }

    /**
     * Lunar 反応細分を返す。
     *
     * @return {@link LunarType}
     */
    public LunarType getLunarType() {
        return lunarType;
    }

    /**
     * 感電または Lunar-Charged 反応かを判定する。
     *
     * @return 感電系反応であれば {@code true}
     */
    public boolean isElectroCharged() {
        return kind == Kind.ELECTRO_CHARGED || kind == Kind.LUNAR_CHARGED;
    }

    /**
     * 拡散反応かを判定する。
     *
     * @return 拡散反応であれば {@code true}
     */
    public boolean isSwirl() {
        return kind == Kind.SWIRL;
    }

    /**
     * 雷霆万鈞反応かを判定する。
     *
     * @return 雷霆万鈞反応であれば {@code true}
     */
    public boolean isThundercloudStrike() {
        return kind == Kind.THUNDERCLOUD_STRIKE;
    }

    /**
     * Lunar 反応 (Lunar-Charged / Lunar-Bloom / Lunar-Crystallize のいずれか) かを判定する。
     *
     * @return Lunar 反応であれば {@code true}
     */
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

    /**
     * 反応名から {@link Kind} を推定する。
     * 現在の実装では具体名マッピングは行わず、{@code null} の場合に {@link Kind#NONE} を、
     * それ以外で {@link Kind#OTHER} を返す。
     *
     * @param name 表示用反応名
     * @return 推定された {@link Kind}
     */
    private static Kind inferKind(String name) {
        if (name == null) {
            return Kind.NONE;
        }
        return Kind.OTHER;
    }

    /**
     * 反応名から {@link LunarType} を推定する。
     *
     * @param name 表示用反応名
     * @return 推定された {@link LunarType}
     */
    private static LunarType inferLunarType(String name) {
        Kind kind = inferKind(name);
        return inferLunarType(kind);
    }

    /**
     * {@link Kind} から {@link LunarType} を導出する。
     *
     * @param kind 反応の具体種別
     * @return 対応する {@link LunarType} (非 Lunar 反応は {@link LunarType#NONE})
     */
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

    /**
     * {@link LunarType} から対応する {@link Kind} を導出する。
     *
     * @param lunarType Lunar 反応細分
     * @return 対応する {@link Kind}
     */
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

    /**
     * {@link LunarType} に対する正規 (canonical) 表示名を返す。
     *
     * @param lunarType Lunar 反応細分
     * @return Lunar 反応の正規表示名。非 Lunar の場合は {@code null}
     */
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
