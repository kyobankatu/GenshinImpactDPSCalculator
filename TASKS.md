# RL修正計画

## 目的

現状の RL プログラムには、学習結果を歪める不整合が複数ある。
特に優先度が高いのは以下。

- VinePPO の branch rollout が multi-party 前提で正しい party 文脈を保持していない
- VinePPO の MC rollout が snapshot 時点の RL 補助状態を復元していない
- actor の advantage と critic の return target が VinePPO 有効時に別定義になっている
- Java/Python 間の観測仕様定数が分裂している
- 評価・チェックポイント・profile 読込まわりに壊れやすい実装が残っている

この計画書は、上記を全て修正し、RL の学習・評価・再開の一貫性を回復するための実装順序を定義する。

## 方針

- まず学習を壊す不整合を止血する
- 次に Java/Python 間の契約を一本化する
- そのあと評価系と運用系の不整合を解消する
- 最後に検証手順を整備して、再発を防ぐ

## Phase 1: VinePPOの正当性を回復

### Task 1-1: snapshotにparty文脈を保持

対象:

- `src/java/mechanics/rl/bridge/VectorizedEnvironment.java`

内容:

- `SnapshotEntry` に `partyId` を追加する
- 必要なら `partyName` も保持してログとデバッグを容易にする
- branch rollout 実行時に snapshot 元の party を必ず復元対象として扱う

目的:

- multi-party 学習時に、別パーティの `EpisodeConfig.partyOrder` や action mask を誤用しないようにする

### Task 1-2: snapshotにRL補助状態を保持

対象:

- `src/java/mechanics/rl/BattleEnvironment.java`
- `src/java/mechanics/rl/bridge/VectorizedEnvironment.java`

内容:

- `lastSwapTime`
- `previousActionId`
- `stepCount`
- `slotLastActiveTime`
- `slotOnFieldTime`

上記を simulator snapshot とは別に保存・復元できる構造を導入する。
必要なら `BattleEnvironment` 用の lightweight な snapshot class を追加する。

目的:

- branch rollout が snapshot 時点の真の continuation になるようにする
- repeated swap penalty と履歴依存観測量を MC rollout 中でも整合させる

### Task 1-3: branch rollout用環境のparty整合を保証

対象:

- `src/java/mechanics/rl/bridge/VectorizedEnvironment.java`
- `src/java/mechanics/rl/BattleEnvironment.java`

内容:

- 共有 `branchEnv` をそのまま使い回す設計を見直す
- snapshot 元 party に応じて `branchEnv.reset(false, partyId)` 相当の復元を行う
- 復元後に RL 補助状態 snapshot を適用し、その後に MC rollout を走らせる

目的:

- VinePPO 有効時に `Q_MC(s, a)` が別パーティ設定で計算される破綻を排除する

### Task 1-4: Vine advantage と critic target の定義を統一

対象:

- `src/python/rl/train_recurrent_ppo.py`

内容:

- VinePPO を使うときの学習目標を明文化する
- 次のどちらかに統一する
  1. actor だけ Vine advantage、critic は従来 return を維持する設計だと明示してメトリクスも分離する
  2. actor/critic とも Vine に整合する target に寄せる

現状は中途半端なので、どちらかに決めてコードとコメントを一致させる。

目的:

- actor/critic が別問題を解く状態を解消する

### Task 1-5: VinePPOコメントと実装の乖離を解消

対象:

- `src/python/rl/train_recurrent_ppo.py`

内容:

- `apply_vine_ppo_advantages` の docstring とコメントを実装に合わせて書き直す
- `Q - V` を採るのか `Q - mean(valid_q)` を採るのかを明記する
- counterfactual baseline の根拠を短く残す

目的:

- 将来の誤修正を防ぐ

## Phase 2: 観測・状態の契約を一本化

### Task 2-1: 観測仕様の単一ソース化

対象:

- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/rollout_service_client.py`

内容:

- Python 側の `CHAR_FEATURE_SIZE`, `GLOBAL_FEATURE_SIZE`, `NUM_CHARS`, `PRIVILEGED_OBSERVATION_SIZE` の固定値依存を最小化する
- モデル構築と checkpoint 検証は rollout service の handshake 値を正とする
- Python 側定数は削除または「古い fallback」であることをやめ、明示的検証ロジックに置き換える

目的:

- Java/Python の観測次元ずれを静かに通さない

### Task 2-2: checkpoint metadataの厳格化

対象:

- `src/python/rl/recurrent_ppo.py`
- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/evaluate_policy.py`

内容:

- checkpoint 保存時に以下を必須化
  - `policy_type`
  - `observation_size`
  - `char_feature_size`
  - `global_feature_size`
  - `num_chars`
  - `privileged_observation_size`
- 読込時に metadata 欠落なら GRU 扱いへ倒さず、明示的にエラーにする
- architecture mismatch は fail fast に統一する

目的:

- 古い checkpoint を誤って別モデルとして読む事故を防ぐ

### Task 2-3: privileged state仕様の確認と明文化

対象:

- `src/java/mechanics/rl/PrivilegedStateEncoder.java`
- `src/python/rl/recurrent_ppo.py`

内容:

- privileged state 23 次元の意味をコードコメントに揃える
- actor 観測と critic privileged state の関係を整理する
- 将来変更時に Java/Python 両方を触る必要があることを明記する

目的:

- critic 系の入力変更が暗黙に入るのを防ぐ

## Phase 3: 評価系の信頼性を上げる

### Task 3-1: 評価ロジックの共通化

対象:

- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/evaluate_policy.py`
- 必要なら新規共通モジュール

内容:

- single episode evaluation
- multi-party aggregate
- action fraction 集計
- attention 集計

上記の重複実装を共通化する。

目的:

- train 時評価と standalone 評価のドリフトを防ぐ

### Task 3-2: multi-party aggregateの定義を明確化

対象:

- `src/python/rl/train_recurrent_ppo.py`
- `src/python/rl/evaluate_policy.py`

内容:

- 現状の単純平均を `macro_average` として明示する
- 必要なら以下を追加する
  - `macro_reward`
  - `macro_damage`
  - `weighted_reward`
  - `weighted_damage`
- 少なくともログ・表示上は何を平均した値か分かるようにする

目的:

- 評価数値の解釈ミスを防ぐ

### Task 3-3: 学習中メトリクスの意味を明確化

対象:

- `src/python/rl/train_recurrent_ppo.py`

内容:

- `mean_reward`, `mean_damage` が「更新中に完了した episode 平均」であることを明示
- 必要なら `completed_episodes == 0` のときのログ値を `0.0` 固定ではなく `NaN` や別指標にする

目的:

- 学習監視時の誤読を減らす

## Phase 4: capability profile読込の健全化

### Task 4-1: regexベースJSON読込を廃止

対象:

- `src/java/mechanics/rl/ObservationEncoder.java`

内容:

- 正規表現抽出をやめ、JSON を正しく parse する方式へ置き換える
- 依存追加が不要なら既存標準機能で対応、必要なら最小限の JSON ライブラリ導入を検討

目的:

- フォーマット変更時に silently zero profile へ落ちる事故を防ぐ

### Task 4-2: profile欠損時の扱いを強化

対象:

- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/mechanics/rl/CapabilityProfiler.java`
- `src/java/sample/ProfileCharacterCapabilities.java`

内容:

- 欠損を warning のみで流すか、学習時は fail fast にするか方針を決める
- 少なくとも profile 再生成手順を README/TASKS に合わせて明確化する

目的:

- capability profile 不整合を早期検知する

## Phase 5: 運用と後方互換の整理

### Task 5-1: 古いcheckpoint世代の扱いを明文化

対象:

- `README.md`
- `src/python/rl/evaluate_policy.py`
- `src/python/rl/train_recurrent_ppo.py`

内容:

- 現行フォーマットと旧フォーマットの互換性方針を決める
- 非互換なら明示的に落とす
- 互換を残すなら converter を別スクリプトに分離する

目的:

- 暗黙互換で誤動作するより、意図的に変換させる運用にする

### Task 5-2: rollout service handshakeの検証強化

対象:

- `src/python/rl/rollout_service_client.py`

内容:

- version だけでなく observation/action/privileged metadata も、学習器・checkpoint と照合する
- mismatch 時は分かりやすいエラーを出す

目的:

- Java server と Python learner の組み合わせミスを即座に検知する

## 検証計画

### Java側

- `./gradlew build`
- `./gradlew ServeRLJava`
- 必要なら `./gradlew BenchmarkRLJava`
- VinePPO 有効時に single-party / multi-party の両方で branch rollout が落ちないことを確認

### Python側

- `python3 -m py_compile src/python/rl/*.py`
- `python3 src/python/rl/evaluate_policy.py --mode deterministic`
- `python3 src/python/rl/train_recurrent_ppo.py --preset debug --updates 2 --envs 2 --rollout-length 8`

### 重点確認項目

- VinePPO 無効時に従来学習が壊れていない
- VinePPO 有効時に multi-party でも snapshot/party 不整合が出ない
- checkpoint 再開時に metadata mismatch を正しく弾く
- Java/Python の observation size が一致しない場合に即エラーになる
- evaluation の per-party 表示と aggregate 表示が意味的に整合する

## 完了条件

- VinePPO の branch rollout が snapshot 元 party と RL 補助状態を正しく復元する
- actor/critic 学習目標の整合性がコードとコメントの両方で明確になっている
- Java/Python 間の観測・privileged state 契約が一本化されている
- checkpoint metadata が厳格化され、曖昧な読込が消えている
- 評価系の重複実装が整理され、multi-party aggregate の意味が明示されている
- capability profile 読込が正規の parse 方式になっている
- `./gradlew build` と Python 側の最低限の動作確認が通る
