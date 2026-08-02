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
    /** 行秋 元素爆発「古華剣・裁雨留虹」の継続状態。 */
    RAINCUTTER,
    /** 行秋 命ノ星座 2 による水元素耐性低下。 */
    XINGQIU_C2_HYDRO_SHRED,
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
    /** スクロース命ノ星座 4 の通常/重撃カウント。 */
    SUCROSE_C4_ALCHEMANIA_HIT,
    /** スクロース命ノ星座 4 の 0.1 秒カウントクールダウン。 */
    SUCROSE_C4_ALCHEMANIA_COUNT_COOLDOWN,
    /** Lunar 拡張: フリンスの Lunar 加算ダメージボーナス。 */
    FLINS_LUNAR_BASE_BONUS,
    /** フリンス命ノ星座 2 による雷元素耐性低下。 */
    FLINS_C2_ELECTRO_RES_SHRED,
    /** フリンス命ノ星座 6 による昇格の光のチーム Lunar-Charged 乗算。 */
    FLINS_C6_LUNAR_CHARGED_ELEVATION,
    /** Lunar 拡張: コロンビーナの Lunar 加算ダメージボーナス。 */
    COLUMBINA_LUNAR_BASE_BONUS,
    /** Lunar 拡張: コロンビーナ C1 による Lunar 反応ボーナス。 */
    COLUMBINA_C1_LUNAR_REACTION_BONUS,
    /** Lunar 拡張: コロンビーナ C2 による Lunar 反応ボーナス。 */
    COLUMBINA_C2_LUNAR_REACTION_BONUS,
    /** Lunar 拡張: イネファの Lunar 加算ダメージボーナス。 */
    INEFFA_LUNAR_BASE_BONUS,
    /** イネファ命ノ星座 1 の Carrier Flow Composite。 */
    INEFFA_C1_CARRIER_FLOW_COMPOSITE,
    /** イネファ命ノ星座 4 の元素エネルギー回復クールダウン。 */
    INEFFA_C4_ENERGY_COOLDOWN,
    /** イネファ命ノ星座 6 の追撃クールダウン。 */
    INEFFA_C6_FOLLOW_UP_COOLDOWN,
    /** 雷電将軍「諸願百日の儀」: 諸願スタックによる元素爆発 DMG ボーナス。 */
    RAIDEN_EYE_OF_STORMY_JUDGMENT,
    /** 雷電将軍 命ノ星座 4「常道への誓い」による味方攻撃力ボーナス。 */
    RAIDEN_C4_PLEDGE_OF_PROPRIETY,
    /** 雷電将軍 命ノ星座 6「願いの代行人」の発動回数。 */
    RAIDEN_C6_WISHBEARER_TRIGGER,
    /** 雷電将軍 命ノ星座 6「願いの代行人」の 1 秒クールダウン。 */
    RAIDEN_C6_WISHBEARER_COOLDOWN,
    /** リサ「誘雷」スタックの独立した有効期限マーカー。 */
    LISA_CONDUCTIVE_STACK,
    /** リサ命ノ星座 6「パルスの魔女」の戦闘中クールダウン。 */
    LISA_C6_PULSATING_WITCH_COOLDOWN,
    /** 香菱 グゥオパァー C1: 防御力減少効果。 */
    XIANGLING_GUOBA_C1_SHRED,
    /** 香菱 元素爆発「ピリ辛」状態 (Chili)。 */
    XIANGLING_CHILI,
    /** 香菱 命ノ星座 6 効果。 */
    XIANGLING_C6,
    /** 聖遺物 4 セット「旧貴族のしつけ」効果。 */
    NOBLESSE_OBLIGE_4PC,
    /** 聖遺物 4 セット「教官」によるチーム元素熟知バフ。 */
    INSTRUCTOR_4PC_TEAM_EM,
    /** 聖遺物 4 セット「亡命者」の非重複エネルギー回復シーケンス。 */
    THE_EXILE_4PC_SEQUENCE,
    /** 聖遺物 4 セット「深林の記憶」による草元素耐性ダウン。 */
    DEEPWOOD_MEMORIES_4PC_SHRED,
    /** 聖遺物「砂上の楼閣の史話」の重撃後攻撃強化。 */
    DESERT_PAVILION_CHRONICLE_4PC,
    /** 聖遺物「追憶のしめ縄」の通常・重撃・落下攻撃強化。 */
    SHIMENAWAS_REMINISCENCE_4PC,
    /** 聖遺物「楽園の絶花」の開花系ダメージスタック。 */
    FLOWER_OF_PARADISE_LOST_STACK,
    /** 聖遺物「楽園の絶花」のスタック獲得クールダウン。 */
    FLOWER_OF_PARADISE_LOST_TRIGGER_COOLDOWN,
    /** 聖遺物「長き夜の誓い」の光輝スタック。 */
    LONG_NIGHTS_OATH_RADIANCE_STACK,
    /** 聖遺物「長き夜の誓い」の落下攻撃クールダウン。 */
    LONG_NIGHTS_OATH_PLUNGE_COOLDOWN,
    /** 聖遺物「長き夜の誓い」の重撃クールダウン。 */
    LONG_NIGHTS_OATH_CHARGED_COOLDOWN,
    /** 聖遺物「長き夜の誓い」の元素スキルクールダウン。 */
    LONG_NIGHTS_OATH_SKILL_COOLDOWN,
    /** 聖遺物「千岩牢固」のチーム攻撃力強化。 */
    TENACITY_OF_THE_MILLELITH_TEAM_ATK,
    /** 聖遺物「千岩牢固」の元素スキル命中クールダウン。 */
    TENACITY_OF_THE_MILLELITH_TRIGGER_COOLDOWN,
    SUPERCONDUCT_PHYS_RES_SHRED,
    /** 武器「漂泊の宵星」効果。 */
    WANDERING_EVENSTAR_WILDLING_NIGHTSTAR,
    /** 武器「マカイラの水色」効果。 */
    MAKHAIRA_AQUAMARINE_DESERT_PAVILION,
    /** 武器「サイフォスの月明かり」効果。 */
    XIPHOS_MOONLIGHT_JINNIS_WHISPER,
    /** 武器「終焉を嘆く詩」の千年の大楽章・別れの歌。 */
    ELEGY_FAREWELL_SONG,
    /** 「千年の大楽章」各武器が共有する攻撃力効果。 */
    MILLENNIAL_MOVEMENT_ATK,
    /** 武器「蒼古なる自由への誓い」の通常・重撃・落下攻撃強化。 */
    FREEDOM_SWORN_SONG_OF_RESISTANCE,
    /** 武器「松籟の響く頃」の通常攻撃速度強化。 */
    SONG_OF_BROKEN_PINES_BANNER_HYMN,
    /** 武器「千夜に浮かぶ夢」の味方元素熟知共有。 */
    A_THOUSAND_FLOATING_DREAMS_TEAM_EM,
    /** 武器「龍殺しの英傑譚」の交代先への攻撃力バフ。 */
    THRILLING_TALES_LEGACY,
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
    /** 繁茂の草: 燃焼・原激化・開花系による 6 秒間の元素熟知 +30。 */
    SPRAWLING_GREENERY_PRIMARY_REACTION,
    /** 繁茂の草: 超激化・草激化・超開花・烈開花による 6 秒間の元素熟知 +20。 */
    SPRAWLING_GREENERY_SECONDARY_REACTION,
    /** 元素共鳴「迅速の風」(風風): 移動速度+, スタミナ消費 -15%, クールタイム短縮。 */
    IMPETUOUS_WINDS
}
