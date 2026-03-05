# Genshin DPS Calculator プロジェクト仕様書

本書は、原神の戦闘シミュレーションおよび聖遺物ステータス最適化を行うツールの仕様書です。
AIエージェントがプロジェクトの全体像や独自仕様を迅速に把握できるように構成されています。
また、プログラム構造、変数の記法、およびステータス表（CSV）の厳格な記述ルールを定義することで、新規開発時にも差異が生じず一貫性が保たれるようにしています。

## 1. プロジェクト概要
*   **言語**: Java 11+
*   **用途**: パーティ単位での行動シミュレーションを実行し、DPS（秒間ダメージ）を算出。さらに、各キャラクターの聖遺物サブステータス（会心率、会心ダメージ、攻撃力、HP、熟知など）の割り振りを N次元的に探索し、パーティ全体の総DPSを最大化する。
*   **特徴**: スキルや爆発のダメージ倍率、ヒットフレーム（キャストタイムや着弾遅延）を細かくエミュレートし、元素付着（ICD）、元素反応、バフ・デバフの動的変化を正確に再現する。

## 2. ディレクトリ・アーキテクチャ構成
*   **`src/simulation/`**: シミュレーションのコアエンジン。
    *   `CombatSimulator.java`: 時間の進行（`advanceTime`）、キャラクターの行動実行（`performAction`）、バフ管理、リアクションのフック、オーラ（元素付着）の管理を担う神クラス。
    *   `Party.java`: 編成されているメンバーの管理と、アクティブキャラクターの切り替え。
*   **`src/model/`**: エンティティモデル。
    *   `character/`: 各キャラクタークラス。固有天賦や特定の行動パターンを実装。
    *   `weapon/`, `artifact/`: 武器・聖遺物の実装（効果の発動条件、バフ付与ロジック）。
    *   `type/StatType.java`: ステータスやバフダメージの内部キー（ATK_PERCENT, LUNAR_BASE_BONUS 等）を定義。
*   **`src/mechanics/`**: 計算および最適化ロジック。
    *   `formula/DamageCalculator.java`: ダメージ計算の中核。基礎値×倍率、各種ダメージバフ、会心、敵の防御力・耐性減衰（翠緑など）をすべてここで処理する。
    *   `reaction/`: 元素反応のダメージ計算、ゲージ消費。
    *   `element/ICDManager.java`: スキルごとの元素付着クールダウン（ヒット回数/時間経過）を管理。
    *   `optimization/`: 聖遺物サブステ分配・DPS最適化アルゴリズム（IterativeSimulator, ArtifactOptimizer）。
    *   `energy/EnergyManager.java`: 粒子生成とER（元素チャージ効率）に基づくエネルギー回復ロジック。
*   **`config/characters/`**: CSVファイルによる外部化データ。レベル別ステータスやスキル倍率(`_Multipliers.csv`, `_Status.csv`)を定義。

## 3. 主要なシステム・ロジック

### 3.1 聖遺物サブステータス最適化 (N-Dimensional Optimization)
*   **前提要件（MinER）**: キャラクターごとに設定された必要元素チャージ効率（MinER）を満たすまで優先的にロールを割り振る。
*   **ロジック**: `IterativeSimulator` と `ArtifactOptimizer` が連携。KQM基準（各サブステ初期2回）をベースに、残りのロール（最大＋20回など）を複数の最適化対象ステータス（CR, CD, ATK%, HP%, EM）に対して、**山登り法（Hill Climbing / Swap Refinement）**または**N次元グリッド探索**を用いて、パーティ全体のダメージが最大になる割り振りを算出する。

### 3.2 アクションキャストタイム（Animation Duration）
*   `AttackAction.animationDuration` プロパティにより、スキルや爆発を撃つ際の「モーションの硬直時間」を表現。
*   `CombatSimulator.performAction` 実行時、この硬直時間分だけ自動的にシミュレーション時間（`advanceTime`）が進み、スキル間の隙間（遅延）が再現される。

### 3.3 元素付着（Aura / ICD）と 翠緑の影 (VV Shred)
*   スタッツ上の属性（PYRO_RES_SHRED 等）により各種耐性デバフを管理。
*   拡散反応（Swirl-Electro など）が発生した際に `ViridescentVenerer.java` がフックされ、`CombatSimulator` の反応トリガー経由でパーティ全体に耐性ダウンバフを付与する。
*   この耐性ダウンは、直撃ダメージ（`DamageCalculator`）および 固定値リアクションダメージ（感電・過負荷等）の双方に適用される。

## 4. 独自実装要素（カスタムキャラクター・メカニクス）
本プロジェクトでは、公式設定にはない**オリジナルのキャラクターおよび属性メカニクス**が実装されている点に注意すること。

### 4.1 Lunar / 非Lunar 分類
*   キャラクターは属性として `isLunar` を持つ（Ineffa, Flins, Columbina 等は Lunar）。
*   **Moonsign (月のサイン)**: パーティ内のLunarキャラ数に応じて `ASCENDANT_GLEAM`（複数人）や `NASCENT_GLOW`（単体）へと状態が変化し、固有のシナジーを発生させる。

### 4.2 Ascendant Blessing (アセンダント・ブレッシング)
*   非Lunarキャラクター（Sucroseなど）がスキルや爆発を使用すると、パーティ内のLunarキャラクター全員にLunar系のダメージバフを付与する仕組み（スクロースの熟知ベースでのバフ量スケーリングなども実装済み）。

### 4.3 独自の Lunar ダメージスケーリング
*   **LUNAR_BASE_BONUS**: Ineffaの持つ能力。基礎ダメージに乗算される前の「基礎加算枠(Base Damage Bonus)」の拡張系。
*   **LUNAR_MULTIPLIER**: Columbinaの持つ能力。最終ダメージに対して完全に独立した乗算（Independent Multiplier）を行う。計算式は熟知（EM）に依存し `(EM / 2000) * 1.5` のようなスケーリングを持つ。
*   ※これらは `DamageCalculator.java` に特殊な計算式として組み込まれており、デバッグログのフォーマットにて `ColMult` として独立表示される。

## 5. デバッグと出力
*   **テキストログ (`sim_output...txt`)**: 時系列での行動（[T=1.5] ...）、反応、ダメージ、エネルギー配布状況がすべてログ化される。デバッグ・検算の一次資料となる。
*   **Formula Debug**: `DamageCalculator.java` にて、ダメージ計算の各要素（MV, BaseI, React, Gear, Burst, Crit, Res, ColMult）の値をログ出力する機能があり、ダメージ計算が合わない場合の特定に用いる。
*   **HTMLレポート**: `HtmlReportGenerator.java` によって、パイチャート（キャラごとのダメージ貢献度）、タイムライングラフ（バフやオーラの時間推移）、聖遺物の最適化ロール数分布一覧が出力される。

## 6. 命名規則・変数の記法 (Naming Conventions)
コードの一貫性を保つため、以下の命名規則を厳格に適用します。

- **クラス名・インターフェース名 (PascalCase / UpperCamelCase)**
  - 例: `DamageCalculator`, `StatsContainer`, `ActiveCharacterBuff`
- **メソッド名・変数名 (camelCase)**
  - 意味が明確に伝わる動詞や名詞を使用する。
  - 例: `calculateDamage`, `baseStats`, `getEffectiveStats`, `currentTime`
- **列挙型 (Enum) および 定数 (SCREAMING_SNAKE_CASE)**
  - 例: `CRIT_RATE`, `ELEMENTAL_MASTERY`, `LUNAR_BASE_BONUS`
- **デバッグログやコンソール出力プレフィックス**
  - フォーマットやモジュールごとに `[]` で括ったプレフィックスを付ける。
  - 例: `[DC_DEBUG]`, `[FormulaDebug]`, `[FormulaValues]`

## 7. ステータス表（CSV）の厳格な記法 (Status Table Notation)
キャラクターのスキル倍率やステータスを定義する `_Multipliers.csv` などのファイルは、プログラムのパース処理でエラーが生じないよう、以下のルールに厳密に従って記述する必要があります。

### ファイル名規則
`<CharacterName>_Multipliers.csv` (例: `Ineffa_Multipliers.csv`)

### CSVヘッダー (1列目)
必ず以下の順序と文字列で、空白文字や大文字小文字のブレなく定義すること。
`Character,AbilityType,Key,Level,Value1,Value2`

### カラム定義
1. **Character** (String)
   - キャラクター名（PascalCase）。例: `Ineffa`
2. **AbilityType** (String)
   - アビリティの種類。プログラム側の `ActionType` 区分と合致させる。基本は大文字。
   - 許可される値の例: `NORMAL`, `CHARGED`, `PLUNGE`, `SKILL`, `BURST`, `PASSIVE` など。
3. **Key** (String)
   - 該当データの一意な識別名。スキル内のどの部分の倍率なのかを明確にする。
   - 例: `N1`, `N2` (通常攻撃段数), `Skill DMG`, `Shield Ratio`, `Shield Flat`, `Burst DMG` 等。
4. **Level** (Integer)
   - 天賦レベルまたはスキルレベル。基本は数値（9, 10など）。固定パッシブの場合は `-1` などを充てることもある。
5. **Value1** (Double)
   - 基本となる乗数や固定値。
   - ダメージ倍率や比率の場合は小数で記述する（例: 64.0% の場合は `0.640`）。
   - 固定ステータス値の場合はそのまま記述する（例: シールド基礎値 `2820`）。
6. **Value2** (Double)
   - 追加の乗数や、特定の条件下での追加ダメージボーナス等。使用しない場合（大半のケース）は `0` を指定する。

### CSV記述例
```csv
Character,AbilityType,Key,Level,Value1,Value2
Ineffa,NORMAL,N1,9,0.640,0
Ineffa,NORMAL,N2,9,0.629,0
Ineffa,SKILL,Skill DMG,9,1.469,0
Ineffa,SKILL,Shield Ratio,9,3.76,0
Ineffa,SKILL,Shield Flat,9,2820,0
Ineffa,BURST,Burst DMG,9,11.506,0
```

### 注意事項
- カンマ `,` の前後に不要なスペース（空白）を絶対に入れないこと。パースエラーの原因となります。
- `Value1`, `Value2` には計算式や単位の文字列（`%`など）を含めず、必ず計算可能な数値のみを記述すること。
- 改行コードは `CRLF` または `LF` で統一し、EOF（ファイル末尾）にも空行を含めること。

---
*Future Agents*: 新しい機能を追加・修正する場合は、必ず `DamageCalculator` の計算式フローおよび、`CombatSimulator` の `advanceTime` / `performAction` の時間の流れに矛盾が生じないかを確認すること。また、新たなバフや効果を追加する際は `StatType` への定義追加を忘れずに行うこと。本仕様書に記載された命名規則・CSV記法フォーマットから逸脱してはならない。
