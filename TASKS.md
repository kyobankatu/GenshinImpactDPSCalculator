# 順列ベースCapabilityProfile実装計画

## 目的

`team_buff_score` を `partyOrder[0]` 固定の主力相性スコアから切り離し、  
4人パーティの全順列 `4! = 24` を使った汎用的な支援寄与スコアへ置き換える。

最終出力はこれまで通り 1 次元の `team_buff_score` のまま維持する。

## 問題

- 現行 profiler は `partyOrder[0]` を固定 primary として扱う
- `FlinsParty2` では `Flins` への相性がそのまま `team_buff_score` になってしまう
- `Sucrose` のような短時間支援役より、`Columbina` のような特定 carry への強い増幅役が過大評価されやすい
- profile が「汎用 support 性」ではなく「固定ローテ相性」を学習器へ渡してしまう

## 方針

- `team_buff_score` は 1 つのまま残す
- ただし内部算出は party 全順列ベースへ変更する
- 各順列で support 対象キャラの寄与を、対照 run との差分で測る
- subject 自身の direct damage は寄与から除外し、他メンバーへの支援価値を優先して測る
- 計算量は増えてよい。学習全体より profiling の正しさを優先する

## 実装タスク

### Task 1: 全順列列挙

対象:

- `src/java/mechanics/rl/CapabilityProfiler.java`

内容:

- `config.partyOrder` の全順列を生成する helper を追加する
- profiling 中に 24 通りを走査できるようにする

### Task 2: 順列ローテーションscript導入

対象:

- `src/java/mechanics/rl/CapabilityProfiler.java`

内容:

- 順列内の先頭3人は support entry script を実行する
- 最後の1人は carry window script を実行する
- support entry は `burst -> skill -> normal fallback` 優先
- carry window は `best action` ベースで一定アクション数回す

### Task 3: subject抑制対照run導入

対象:

- `src/java/mechanics/rl/CapabilityProfiler.java`

内容:

- 各順列について `subject enabled` と `subject suppressed` の 2 run を実行する
- suppressed では subject の支援行動を neutral 化する
- 差分は `subject` 以外のメンバーが出したダメージで比較する

目的:

- subject 自身の direct damage を `team_buff_score` へ混ぜない

### Task 4: team_buff_score集約

対象:

- `src/java/mechanics/rl/CapabilityProfiler.java`

内容:

- 24 順列すべての寄与差分を平均して `team_buff_score` を作る
- 将来の調整用にログへ permutation support の平均値を出す

### Task 5: 既存profile契約維持

対象:

- `src/java/mechanics/rl/CapabilityProfile.java`
- `src/java/mechanics/rl/ObservationEncoder.java`

内容:

- profile JSON キーは変えない
- 観測次元は増やさない
- 学習器側の入力契約は維持する

## 完了条件

- `team_buff_score` が `partyOrder[0]` に依存しない
- `FlinsParty2` で `Columbina` と `Sucrose` の support 値比較が固定 carry 相性から切り離される
- `profiles.json` 形式は互換のまま
- `./gradlew build` が通る

## 検証

- `./gradlew build`
- `./gradlew ProfileCapabilities`
- 生成された `config/capability_profiles/profiles.json` を確認
- `FlinsParty2` で `team_buff_score` の大小関係が以前より汎用 support 性に近づいているかを確認
