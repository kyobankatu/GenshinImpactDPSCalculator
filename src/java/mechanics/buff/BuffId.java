package mechanics.buff;

/**
 * シミュレータロジックから存在判定や参照に用いる、型付きバフ識別子。
 *
 * <p>表示名は別途 {@link Buff#getName()} 等で管理されるため、ログやレポートで
 * 人間可読な名称を保ちつつ、コード上は本 enum でバフを厳密に同定できる。</p>
 *
 * <p>各定数の分類:</p>
 * <ul>
 *   <li>キャラクター固有バフ (例: {@link #RAIDEN_EYE_OF_STORMY_JUDGMENT}, {@link #XIANGLING_CHILI})</li>
 *   <li>武器固有バフ (例: {@link #CALAMITY_QUELLER}, {@link #AUBADE_BONUS})</li>
 *   <li>聖遺物セット効果 (例: {@link #NOBLESSE_OBLIGE_4PC}, {@link #VV_SHRED_PYRO})</li>
 *   <li>元素共鳴 (例: {@link #FERVENT_FLAMES}, {@link #SOOTHING_WATER})</li>
 *   <li>非公式 Lunar 機構 (例: {@link #FLINS_LUNAR_BASE_BONUS}, {@link #COLUMBINA_LUNAR_BRILLIANCE})</li>
 * </ul>
 */
public enum BuffId {
    /** 未指定 / バフなしを表すプレースホルダ。 */
    NONE,
    /** 上記カテゴリに属さない、その場限りの汎用バフ。 */
    CUSTOM,
    /** 雷電将軍 命ノ星座 4 凸「神威・天日の眼」など、特定範囲の万能ダメージ強化。 */
    FANTASTIC_VOYAGE,
    /** 雷電将軍 元素爆発「奥義・夢想真説」状態の万能効果。 */
    RAINCUTTER,
    /** Lunar 拡張: コロンビーナの「皓月の意志」。 */
    GLEAMING_MOON_INTENT,
    /** Lunar 拡張: コロンビーナの「皓月の献身」。 */
    GLEAMING_MOON_DEVOTION,
    /** Lunar 拡張: 月相シナジー (Lunar / 非 Lunar の相互強化)。 */
    GLEAMING_MOON_SYNERGY,
    /** Lunar 拡張: 非 Lunar キャラから Lunar キャラに付与される「月相・昇格祝福」。 */
    MOONSIGN_ASCENDANT_BLESSING,
    /** リコンストラクション・プロトコル 2 段目バフ。 */
    RECONSTRUCTION_PROTOCOL_P2,
    /** 武器「降臨之剣」(Calamity Queller) のスタック効果。 */
    CALAMITY_QUELLER,
    /** 武器「裁断」(Eagle Spear of Justice) 系のダメージバフ。 */
    EAGLE_SPEAR_OF_JUSTICE,
    /** 武器「晴れた日の麦わら帽子」スキル系の元素熟知バフ。 */
    SUNNY_MORNING_SKILL_EM,
    /** 武器「晴れた日の麦わら帽子」爆発系の元素熟知バフ。 */
    SUNNY_MORNING_BURST_EM,
    /** 武器「晴れた日の麦わら帽子」拡散系の元素熟知バフ。 */
    SUNNY_MORNING_SWIRL_EM,
    /** 武器「豊穣の海・聖酒」(Bountiful Sea, Sacred Wine) 効果。 */
    BOUNTIFUL_SEA_SACRED_WINE,
    /** Lunar 拡張: コロンビーナ「月華の輝き」(Lunar Brilliance)。 */
    COLUMBINA_LUNAR_BRILLIANCE,
    /** Lunar 拡張: 雨海による中断耐性 (Rainsea Interruption Resistance)。 */
    RAINSEA_INTERRUPTION_RESISTANCE,
    /** Lunar 拡張: 雨海によるシールド (Rainsea Shield)。 */
    RAINSEA_SHIELD,
    /** Lunar 拡張: 月相 C2 攻撃力ボーナス。 */
    C2_MOONSIGN_ATK_BONUS,
    /** Lunar 拡張: 月相 C2 元素熟知ボーナス。 */
    C2_MOONSIGN_EM_BONUS,
    /** Lunar 拡張: 月相 C2 防御力ボーナス。 */
    C2_MOONSIGN_DEF_BONUS,
    /** Lunar 拡張: コロンビーナ元素爆発ボーナス。 */
    COLUMBINA_LUNAR_BURST_BONUS,
    /** スクロース固有素質 4 (Mollis Favonius A4) による元素熟知共有。 */
    SUCROSE_MOLLIS_FAVONIUS_A4,
    /** スクロース固有素質 1: 炎元素 DMG 増加 (拡散反応経由)。 */
    SUCROSE_CATALYST_CONVERSION_A1_PYRO,
    /** スクロース固有素質 1: 水元素 DMG 増加。 */
    SUCROSE_CATALYST_CONVERSION_A1_HYDRO,
    /** スクロース固有素質 1: 雷元素 DMG 増加。 */
    SUCROSE_CATALYST_CONVERSION_A1_ELECTRO,
    /** スクロース固有素質 1: 氷元素 DMG 増加。 */
    SUCROSE_CATALYST_CONVERSION_A1_CRYO,
    /** スクロース命ノ星座 6 のボーナス。 */
    SUCROSE_C6_BONUS,
    /** Lunar 拡張: フリンスの Lunar 加算ダメージボーナス。 */
    FLINS_LUNAR_BASE_BONUS,
    /** Lunar 拡張: コロンビーナの Lunar 加算ダメージボーナス。 */
    COLUMBINA_LUNAR_BASE_BONUS,
    /** Lunar 拡張: コロンビーナ C1 による Lunar 反応ボーナス。 */
    COLUMBINA_C1_LUNAR_REACTION_BONUS,
    /** Lunar 拡張: コロンビーナ C2 による Lunar 反応ボーナス。 */
    COLUMBINA_C2_LUNAR_REACTION_BONUS,
    /** Lunar 拡張: イネファの Lunar 加算ダメージボーナス。 */
    INEFFA_LUNAR_BASE_BONUS,
    /** 雷電将軍「諸願百日の儀」: 諸願スタックによる元素爆発 DMG ボーナス。 */
    RAIDEN_EYE_OF_STORMY_JUDGMENT,
    /** 香菱 グゥオパァー C1: 防御力減少効果。 */
    XIANGLING_GUOBA_C1_SHRED,
    /** 香菱 元素爆発「ピリ辛」状態 (Chili)。 */
    XIANGLING_CHILI,
    /** 香菱 命ノ星座 6 効果。 */
    XIANGLING_C6,
    /** 聖遺物 4 セット「旧貴族のしつけ」効果。 */
    NOBLESSE_OBLIGE_4PC,
    /** 武器「漂泊の宵星」効果。 */
    WANDERING_EVENSTAR_WILDLING_NIGHTSTAR,
    /** Aubade 系武器ボーナス。 */
    AUBADE_BONUS,
    /** 聖遺物 4 セット「翠緑の影」: 拡散後の炎元素耐性ダウン。 */
    VV_SHRED_PYRO,
    /** 翠緑の影: 拡散後の水元素耐性ダウン。 */
    VV_SHRED_HYDRO,
    /** 翠緑の影: 拡散後の雷元素耐性ダウン。 */
    VV_SHRED_ELECTRO,
    /** 翠緑の影: 拡散後の氷元素耐性ダウン。 */
    VV_SHRED_CRYO,
    /** Lunar 拡張: 庇護の天蓋 (Protective Canopy)。 */
    PROTECTIVE_CANOPY,
    /** 元素共鳴「熱誠の炎」(炎炎): 攻撃力 +25%。 */
    FERVENT_FLAMES,
    /** 元素共鳴「容彩の水」(水水): HP +25%。 */
    SOOTHING_WATER,
    /** 元素共鳴「破砕の氷」(氷氷): 凍結時間+, 会心率 +15%。 */
    SHATTERING_ICE,
    /** 元素共鳴「不動の岩」(岩岩): シールド強化、岩ダメージ +15%。 */
    ENDURING_ROCK,
    /** 元素共鳴「繁茂の草」(草草): 元素熟知 +50 ほか。 */
    SPRAWLING_GREENERY,
    /** 元素共鳴「迅速の風」(風風): 移動速度+, スタミナ消費 -15%, クールタイム短縮。 */
    IMPETUOUS_WINDS
}
