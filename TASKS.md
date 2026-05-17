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

---

# RL方策崩壊の修正計画

## 背景

`output/rl_report_flinsparty2.html` と W&B run `iquvpl24` を確認した結果、  
現行 RL は `FlinsParty2` の deterministic 評価で `Columbina` 張り付きと `ATTACK` 連打に崩れている。

観測された症状:

- deterministic 評価がほぼ `ATTACK` 偏重
- `FlinsParty2` で `Flins` の expected on-field share に対して realized share が極端に低い
- `Columbina` が長時間 on-field を占有してローテーションが壊れる
- stochastic 評価は deterministic より良く、argmax 化で方策品質が悪化している
- 手書き `FlinsParty2` ローテーションに対して RL deterministic の総ダメージが大幅に低い

## 原因整理

- 現行報酬は `damageDelta` 主体で、弱い通常攻撃連打でも正報酬になりやすい
- `idleTimePenaltyPerSecond` が小さく、低効率 on-field 継続を十分に罰していない
- `roleAlignmentBonusWeight` はデフォルトで無効のため、役割逸脱が学習目的に入っていない
- 評価 HTML は deterministic 側のみ可視化しており、stochastic との乖離が見えにくい
- multi-party 学習で 1 つの方策に複数ローテーションを同居させ、初期学習を難化させている

## 修正方針

- deterministic でも壊れにくい報酬へ変更する
- `FlinsParty2` 単独で方策を安定化させてから multi-party に戻す
- 評価出力を deterministic / stochastic の両方で確認できるようにする
- role prior は補助に使うが、それだけに依存しない

## 実装タスク

### Task 1: 評価レポートを deterministic 専用から拡張

対象:

- `src/python/rl/evaluate_policy.py`
- 必要なら `src/python/rl/evaluation.py`

内容:

- deterministic だけでなく stochastic でも HTML レポートを生成できるようにする
- 出力ファイル名に `det` / `stoch` を含めて混同を防ぐ
- summary に deterministic と stochastic の両方を必ず保存する

目的:

- 「学習できていない」のか「argmax で潰れている」のかを即座に判別できるようにする

### Task 2: 報酬へ role alignment を常用導入

対象:

- `src/java/sample/ServeRLJava.java`
- 必要なら RL 起動スクリプト類

内容:

- `roleAlignmentBonusWeight` を 0 のままにしない
- まずは小さめの重みで有効化し、`Flins` の on-field share が回復するかを観察する
- 学習ジョブ設定にもこの値を明示的に残す

目的:

- `FlinsParty2` で `Flins` が前に出るべきという事前構造を学習目的に反映する

### Task 3: 低効率通常連打への罰を追加

対象:

- `src/java/mechanics/rl/RewardFunction.java`
- 必要なら `src/java/mechanics/rl/EpisodeConfig.java`

内容:

- 直近数ステップの `damage per second` が低い通常攻撃継続に追加ペナルティを入れる
- `ATTACK` のみを連続選択し、かつ burst / skill / swap による改善余地がある場合を重点的に罰する
- 一律に通常攻撃を罰するのではなく、carry の正当な通常コンボは壊さない条件にする

候補:

- 非 carry キャラの長時間 on-field 継続ペナルティ
- 低ダメージ行動の連続ペナルティ
- action efficiency が閾値未満のときの減点

目的:

- `Columbina Normal` を延々振る局所解を潰す

### Task 4: 有効 swap / skill / burst の中間報酬を追加

対象:

- `src/java/mechanics/rl/RewardFunction.java`
- 必要なら `src/java/mechanics/rl/BattleEnvironment.java`

内容:

- 役割に沿った swap、ready 状態での skill / burst 実行に小さな正報酬を付ける
- ただし damage 本体より大きくしない
- burst ready を長時間放置した場合の機会損失も検討する

目的:

- 「安全な通常攻撃より、意味のあるセットアップや爆発を選ぶ」方向に探索を寄せる

### Task 5: `FlinsParty2` 単独学習プリセットを作る

対象:

- `src/java/sample/ServeRLJava.java`
- `src/python/rl/train_recurrent_ppo.py`
- 必要なら起動メモやスクリプト

内容:

- 学習対象を `FlinsParty2` 単独に固定した設定を用意する
- `all` や `default` multi-party 学習は、単独安定後の第2段階に回す
- deterministic eval を一定間隔で必ず取る

目的:

- multi-party 干渉を外して、まず 1 パーティで報酬設計の良し悪しを検証する

### Task 6: deterministic 崩壊の早期検知メトリクス追加

対象:

- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/evaluation.py`

内容:

- `action_fraction_0`
- per-party `realized_on_field_share`
- deterministic / stochastic damage ratio
- deterministic / stochastic role alignment ratio

を継続記録し、閾値を超えたら run を失敗候補として判定しやすくする

目的:

- 学習後に HTML を見て初めて崩壊に気づく状態をやめる

### Task 7: deterministic 選択そのものの鋭さを見直す

対象:

- `src/python/rl/recurrent_ppo.py`
- 必要なら評価コード

内容:

- top probability が高いのに deterministic 品質が悪いケースを調べる
- 必要なら評価時に `argmax` 以外の policy extraction も比較する
- ただし根本は報酬設計なので、ここは後順位とする

目的:

- stochastic では回るのに deterministic で壊れる原因を、方策分布の偏りとして切り分ける

## 実施順

1. Task 1 で deterministic / stochastic の両方を可視化する
2. Task 2 と Task 5 を入れて `FlinsParty2` 単独で再学習する
3. Task 3 と Task 4 で報酬を修正する
4. Task 6 で崩壊検知を常設する
5. それでも deterministic だけ悪い場合に Task 7 を調べる

## 完了条件

- `FlinsParty2` deterministic 評価で `Flins` の realized on-field share が expected share に近づく
- `ATTACK` 偏重が緩和され、swap / skill / burst の比率が回復する
- `output/rl_report_flinsparty2.html` 相当の deterministic レポートで `Columbina` 張り付きが解消する
- deterministic damage が手書きローテーション基準に対して大幅改善する
- stochastic と deterministic の乖離が小さくなる

## 検証

- `./gradlew build`
- 役割ボーナス有効で `ServeRLJava` を起動
- `FlinsParty2` 単独設定で PPO を再学習
- `python3 src/python/rl/evaluate_policy.py --mode both --summary`
- deterministic / stochastic の両方で `FlinsParty2` レポートを比較
- W&B で per-party `action_fraction`, `realized_on_field_share`, `eval_det_damage`, `eval_stochastic_damage` を確認
