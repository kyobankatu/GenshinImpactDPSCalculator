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
    /** 聖遺物「燃え盛る炎の魔女」の元素スキル使用スタック。 */
    CRIMSON_WITCH_4PC_PYRO_STACK,
    /** 聖遺物「雷のような怒り」のクールダウン短縮発動間隔。 */
    THUNDERING_FURY_4PC_TRIGGER_COOLDOWN,
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
    /** 聖遺物「風立ちの日」の牧歌の風の祝福。 */
    A_DAY_CARVED_FROM_RISING_WINDS_4PC,
    /** 聖遺物「残響の森で囁かれる夜話」の岩元素ダメージ強化。 */
    NIGHTTIME_WHISPERS_IN_THE_ECHOING_WOODS_4PC,
    /** 聖遺物「辰砂往生録」の潜光。 */
    VERMILLION_HEREAFTER_NASCENT_LIGHT,
    /** Disenchantment in Deep Shadow live Superconduct CRIT Rate bonus. */
    DISENCHANTMENT_SUPERCONDUCT_CRIT_RATE,
    /** Scroll of the Hero of Cinder City Pyro DMG bonus. */
    SCROLL_CINDER_CITY_PYRO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Hydro DMG bonus. */
    SCROLL_CINDER_CITY_HYDRO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Electro DMG bonus. */
    SCROLL_CINDER_CITY_ELECTRO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Cryo DMG bonus. */
    SCROLL_CINDER_CITY_CRYO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Anemo DMG bonus. */
    SCROLL_CINDER_CITY_ANEMO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Geo DMG bonus. */
    SCROLL_CINDER_CITY_GEO_DMG_BONUS,
    /** Scroll of the Hero of Cinder City Dendro DMG bonus. */
    SCROLL_CINDER_CITY_DENDRO_DMG_BONUS,
    SUPERCONDUCT_PHYS_RES_SHRED,
    /** 武器「漂泊の宵星」効果。 */
    WANDERING_EVENSTAR_WILDLING_NIGHTSTAR,
    /** 武器「マカイラの水色」効果。 */
    MAKHAIRA_AQUAMARINE_DESERT_PAVILION,
    /** 武器「サイフォスの月明かり」効果。 */
    XIPHOS_MOONLIGHT_JINNIS_WHISPER,
    /** 武器「終焉を嘆く詩」の千年の大楽章・別れの歌。 */
    ELEGY_FAREWELL_SONG,
    /** 武器「草薙の稲光」の元素爆発後元素チャージ効率。 */
    ENGULFING_LIGHTNING_ER,
    /** Key of Khaj-Nisut three-stack team Elemental Mastery bonus. */
    KEY_OF_KHAJ_NISUT_TEAM_EM,
    /** 「千年の大楽章」各武器が共有する攻撃力効果。 */
    MILLENNIAL_MOVEMENT_ATK,
    /** 武器「蒼古なる自由への誓い」の通常・重撃・落下攻撃強化。 */
    FREEDOM_SWORN_SONG_OF_RESISTANCE,
    /** 武器「松籟の響く頃」の通常攻撃速度強化。 */
    SONG_OF_BROKEN_PINES_BANNER_HYMN,
    /** Golden Majesty 武器シリーズの共有攻撃力スタック。 */
    GOLDEN_MAJESTY_ATK_STACKS,
    /** Golden Majesty 武器シリーズの 0.3 秒スタック獲得間隔。 */
    GOLDEN_MAJESTY_STACK_COOLDOWN,
    /** Yae Miko C4 team Electro DMG bonus. */
    YAE_MIKO_C4_ELECTRO_DMG_BONUS,
    /** Albedo A4 team Elemental Mastery bonus. */
    ALBEDO_A4_TEAM_EM,
    /** Albedo C4 active-character Plunging Attack DMG bonus. */
    ALBEDO_C4_PLUNGING_DMG_BONUS,
    /** Venti C2 Anemo and Physical resistance reduction. */
    VENTI_C2_RES_SHRED,
    /** Venti C6 Anemo and absorbed-element resistance reduction. */
    VENTI_C6_RES_SHRED,
    /** Yoimiya A1 Pyro DMG Bonus stacks. */
    YOIMIYA_A1_PYRO_DMG_BONUS,
    /** Yoimiya A4 team ATK bonus. */
    YOIMIYA_A4_TEAM_ATK,
    /** Yanfei A1 Pyro DMG Bonus after Scarlet Seal consumption. */
    YANFEI_A1_PYRO_DMG_BONUS,
    /** Yanfei Brilliance Charged Attack DMG bonus. */
    YANFEI_BRILLIANCE_CHARGED_DMG_BONUS,
    /** Rosaria A4 team CRIT Rate bonus. */
    ROSARIA_A4_TEAM_CRIT_RATE,
    /** Rosaria C6 Physical resistance reduction. */
    ROSARIA_C6_PHYSICAL_RES_SHRED,
    /** Diluc A4 Pyro DMG bonus during Searing Onslaught infusion. */
    DILUC_A4_PYRO_DMG_BONUS,
    /** Diluc C4 Searing Onslaught DMG bonus. */
    DILUC_C4_SKILL_DMG_BONUS,
    /** Keqing A4 CRIT Rate and Energy Recharge bonus. */
    KEQING_A4_CRIT_RATE_AND_ER,
    /** Keqing C4 ATK bonus after an Electro reaction. */
    KEQING_C4_ATK_BONUS,
    /** Keqing C6 Electro DMG bonus stacks. */
    KEQING_C6_ELECTRO_DMG_BONUS,
    /** Ganyu A1 Frostflake CRIT Rate bonus. */
    GANYU_A1_FROSTFLAKE_CRIT_RATE,
    /** Ganyu A4 active-character Cryo DMG bonus. */
    GANYU_A4_CRYO_DMG_BONUS,
    /** Ganyu C1 Cryo resistance reduction. */
    GANYU_C1_CRYO_RES_SHRED,
    /** Ganyu C4 Celestial Shower DMG bonus. */
    GANYU_C4_CELESTIAL_SHOWER_DMG_BONUS,
    /** Beidou C6 Electro resistance reduction. */
    BEIDOU_C6_ELECTRO_RES_SHRED,
    /** Klee C6 non-stacking team Pyro DMG bonus. */
    KLEE_C6_PYRO_DMG_BONUS,
    /** Eula Icewhirl Cryo and Physical resistance reduction. */
    EULA_ICEWHIRL_RES_SHRED,
    /** Eula C1 owner-only Physical DMG bonus. */
    EULA_C1_PHYSICAL_DMG_BONUS,
    /** Gorou General's War Banner active-character field bonuses. */
    GOROU_GENERAL_WAR_BANNER,
    /** Gorou A1 team DEF bonus after Burst activation. */
    GOROU_A1_DEF_BONUS,
    /** Gorou C6 Geo-only CRIT DMG bonus. */
    GOROU_C6_GEO_CRIT_DMG,
    /** Yelan A4 active-character ramping all-damage bonus. */
    YELAN_ADAPT_WITH_EASE,
    /** Yelan C4 team Max HP bonus from Lifeline marks. */
    YELAN_C4_MAX_HP,
    /** Kujou Sara's Crowfeather Cover charged-shot state. */
    KUJOU_SARA_CROWFEATHER_COVER,
    /** Kujou Sara's active-recipient Tengu Juurai ATK and C6 bonus. */
    KUJOU_SARA_TENGU_JUURAI,
    /** Yun Jin's per-recipient Flying Cloud Flag Formation quota. */
    YUN_JIN_FLYING_CLOUD_FORMATION,
    /** Yun Jin C2 team Normal Attack damage bonus. */
    YUN_JIN_C2_NORMAL_DMG,
    /** Yun Jin C4 owner DEF bonus after Crystallize. */
    YUN_JIN_C4_DEF,
    /** Yun Jin C6 quota-aware team Normal Attack speed bonus. */
    YUN_JIN_C6_NORMAL_SPEED,
    /** Faruzan's team Anemo DMG and C6 CRIT DMG support window. */
    FARUZAN_PRAYERFUL_WIND,
    /** Faruzan's Anemo resistance reduction window. */
    FARUZAN_PERFIDIOUS_WIND,
    /** Shenhe A4 Press Skill and Burst damage bonus. */
    SHENHE_A4_SKILL_BURST_DMG,
    /** Shenhe A4 Hold Normal, Charged, and Plunging damage bonus. */
    SHENHE_A4_NORMAL_CHARGED_PLUNGE_DMG,
    /** Shenhe Burst Cryo and Physical resistance reduction. */
    SHENHE_BURST_RES_SHRED,
    /** Shenhe Burst active-character A1 and C2 Cryo bonuses. */
    SHENHE_BURST_ACTIVE_BONUS,
    /** Tighnari A1 owner Elemental Mastery bonus. */
    TIGHNARI_A1_ELEMENTAL_MASTERY,
    /** Tighnari C2 owner Dendro damage bonus. */
    TIGHNARI_C2_DENDRO_DMG_BONUS,
    /** Tighnari C4 party Elemental Mastery bonus. */
    TIGHNARI_C4_PARTY_ELEMENTAL_MASTERY,
    /** Alhaitham C4 non-owner party Elemental Mastery bonus. */
    ALHAITHAM_C4_PARTY_ELEMENTAL_MASTERY,
    /** Alhaitham C4 owner Dendro damage bonus. */
    ALHAITHAM_C4_DENDRO_DMG_BONUS,
    /** Ayato Burst party Normal Attack damage bonus. */
    AYATO_BURST_NORMAL_DMG,
    /** Kaveh's Painted Dome party Bloom damage bonus. */
    KAVEH_PAINTED_DOME_BLOOM_DMG,
    /** Chevreuse A1 Pyro and Electro resistance reduction. */
    CHEVREUSE_A1_COORDINATED_TACTICS,
    /** Chevreuse A4 Pyro/Electro party ATK bonus. */
    CHEVREUSE_A4_VERTICAL_FORCE_COORDINATION,
    /** Xinyan C4 Physical resistance reduction after Skill damage. */
    XINYAN_C4_PHYSICAL_RES_SHRED,
    /** Ayato C4 party Normal Attack speed bonus. */
    AYATO_C4_NORMAL_SPEED,
    /** Heizou A4 non-owner party Elemental Mastery bonus. */
    HEIZOU_A4_PARTY_ELEMENTAL_MASTERY,
    /** Heizou C1 owner Normal Attack speed bonus. */
    HEIZOU_C1_NORMAL_ATTACK_SPEED,
    /** Freminet C4 stacking owner ATK bonus. */
    FREMINET_C4_ATK,
    /** Freminet C6 stacking owner CRIT DMG bonus. */
    FREMINET_C6_CRIT_DMG,
    /** Candace C2 owner Max HP bonus after Skill damage. */
    CANDACE_C2_MAX_HP,
    /** Candace Crimson Crown elemental Normal Attack support. */
    CANDACE_CRIMSON_CROWN_NORMAL_DMG,
    /** Lynette A1 party ATK bonus after Burst use. */
    LYNETTE_A1_PARTY_ATK,
    /** Lynette C6 owner Anemo damage window. */
    LYNETTE_C6_ANEMO_DMG,
    /** Mika's 12-second party Soulwind attack-speed window. */
    MIKA_SOULWIND_ATTACK_SPEED,
    /** Charlotte C2 owner ATK window after Skill damage. */
    CHARLOTTE_C2_ATK,
    /** Kazuha A4 team Pyro damage bonus. */
    KAZUHA_A4_PYRO_DMG_BONUS,
    /** Kazuha A4 team Hydro damage bonus. */
    KAZUHA_A4_HYDRO_DMG_BONUS,
    /** Kazuha A4 team Electro damage bonus. */
    KAZUHA_A4_ELECTRO_DMG_BONUS,
    /** Kazuha A4 team Cryo damage bonus. */
    KAZUHA_A4_CRYO_DMG_BONUS,
    /** Kazuha C2 owner Elemental Mastery field bonus. */
    KAZUHA_C2_OWNER_ELEMENTAL_MASTERY,
    /** Kazuha C2 active-character Elemental Mastery field bonus. */
    KAZUHA_C2_ACTIVE_ELEMENTAL_MASTERY,
    /** Kazuha C6 owner Anemo infusion window. */
    KAZUHA_C6_INFUSION,
    /** Aloy A1 owner ATK window. */
    ALOY_A1_OWNER_ATK,
    /** Aloy A1 non-owner party ATK window. */
    ALOY_A1_TEAM_ATK,
    /** Aloy A4 stacking Cryo damage window. */
    ALOY_A4_CRYO_DMG_BONUS,
    /** Nightweaver's Looking Glass simultaneous-window team reaction bonus. */
    NIGHTWEAVERS_LOOKING_GLASS_TEAM_REACTION_DMG,
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
