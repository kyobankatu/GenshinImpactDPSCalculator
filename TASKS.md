# CVaR-PPO + Transformer

## 動機

「Columbina素振り 70%」の局所最適から抜け出すために、目的関数とアーキテクチャを改善する。

- **CVaR-PPO**: 平均リターン最大化を捨て、上側 expectile を最適化することで「分散が大きい高峰戦略」を選好させる。Columbina素振りは平均は高いが分散が小さく、上側分位では最適ローテに負ける。
- **Transformer**: GRU の減衰記憶では捉えられない「フォーム発動 5step 前」「Sucrose 拡散 10step 前」のような長距離依存を attention で直接参照させる。

両者は直交：CVaR は「**何を最適化するか**」、Transformer は「**何を表現できるか**」を変える。

---

## Phase 1: CVaR-PPO

### 設計

価値関数の損失を MSE から **expectile loss** に変更：

```
L(R, V) = w * (R - V)^2
w = q       if R > V
w = 1 - q   otherwise
```

q=0.5 で標準MSE と等価（後方互換）。q=0.9 で「実リターン > 予測値」の残差を 9倍重く学習し、V が上側 expectile を予測するようになる。advantage = G - V は「上側 expectile を超えたシーケンスのみ正」となり、ポリシー勾配が「分散の大きい高峰行動」に向かう。

### Task 1-1: 設定パラメータ追加

**ファイル**: `src/python/rl/train_recurrent_ppo.py`

- config dict に `"value_quantile": 0.5`（デフォルト）
- CLI 引数 `--value-quantile FLOAT`

### Task 1-2: value loss を expectile loss に変更

**ファイル**: `src/python/rl/train_recurrent_ppo.py` の `train_epoch`

```python
diff = minibatch["return_targets"] - value
weight = torch.where(diff > 0, q, 1.0 - q)
value_loss = (weight * diff.pow(2) * valid_mask).sum() / valid_count
```

q=0.5 で MSE 同等。

### Task 1-3: execute.sh への env var 追加

```
VALUE_QUANTILE=${VALUE_QUANTILE:-0.5}
TRAIN_ARGS+=(--value-quantile "${VALUE_QUANTILE}")
```

通常学習は q=0.5、risk-seeking には q=0.7〜0.9 を指定。

---

## Phase 2: Transformer

### 設計の核心

**hidden state shape を保ったまま** Transformer 化する：

- `recurrent_state` の shape を `(B, hidden_size)` のまま維持（GRU と同形）
- これを「**過去の累積 summary token**」として扱う
- forward_sequence では `[summary, t_1, t_2, ..., t_T]` の sequence を causal attention で処理
- 末尾の出力を新しい summary として返す
- 1 chunk 内では full attention（rollout 64 step 全体に attend 可能）
- chunk をまたぐ際は summary 1 token のみで情報伝達（GRU 同様の bottleneck）

これによりメモリコスト・既存インフラ・チェックポイントフォーマットを大きく変えずに済む。

### Task 2-1: TransformerPolicy クラス追加

**ファイル**: `src/python/rl/recurrent_ppo.py`

`RecurrentPolicy` と同じインターフェース（forward_step, forward_sequence, act, save, load）を持つ新クラスを追加。

主要構成：
- 既存の per-character encoder + global encoder + attention pooling は維持
- 各 step を 1 token (hidden_size) に圧縮
- `nn.TransformerEncoder(num_layers=2, nhead=4, dim_feedforward=hidden_size*4)` を causal mask 付きで適用
- 位置情報なし（content と causal mask で順序を学習。簡潔さ優先）
- forward_sequence:
  ```
  tokens = [summary; t_1, ..., t_T]   # shape (B, T+1, H)
  out = transformer(tokens, causal_mask)
  per_step_logits = policy_head(out[:, 1:])   # skip summary position
  per_step_values = value_head(out[:, 1:] concat privileged_enc)
  new_summary = out[:, -1]
  ```
- forward_step:
  ```
  token = encode(obs)   # (B, H)
  seq = [summary, token]   # (B, 2, H)
  out = transformer(seq, causal_mask)
  logits = policy_head(out[:, 1])
  value = value_head(out[:, 1])
  new_summary = out[:, 1]
  ```

### Task 2-2: build_policy ファクトリ

**ファイル**: `src/python/rl/recurrent_ppo.py`

```python
def build_policy(policy_type, observation_size, hidden_size, action_size, **kwargs):
    if policy_type == "gru":
        return RecurrentPolicy(observation_size, hidden_size, action_size, **kwargs)
    if policy_type == "transformer":
        return TransformerPolicy(observation_size, hidden_size, action_size, **kwargs)
    raise ValueError(f"unknown policy_type: {policy_type}")
```

`save/load` payload に `policy_type` フィールドを追加し、不一致は明示的にエラー。

### Task 2-3: train_recurrent_ppo.py から build_policy 経由

config に `policy_type: "transformer"` を追加（デフォルト transformer）。
`RecurrentPolicy(...)` の直接呼出を `build_policy(config["policy_type"], ...)` に置換。

### Task 2-4: execute.sh への切替フラグ

```
POLICY_TYPE=${POLICY_TYPE:-transformer}
TRAIN_ARGS+=(--policy-type "${POLICY_TYPE}")
```

`--policy-type gru` で旧アーキに切替可能（比較用）。

### Task 2-5: 既存チェックポイント互換性

`policy_type` が一致しないとロード時にエラー。既存の `latest-model.pt` は GRU 用なので、Transformer に切り替える際はゼロから学習。execute.sh の `RESUME_TRAINING=false` で対応可。

---

## リスク

- **CVaR-PPO**: q=0.5 と等価が壊れていないか単体確認必要
- **Transformer 学習不安定性**: 既存学習率・clip_range の再調整が必要かもしれない
- **チェックポイント非互換**: ゼロ学習必須
- **位置エンコーディングなしの妥当性**: causal mask + summary token の content で十分順序が捕捉されるはずだが、効かなかったら learned positional embedding を追加

## 完了条件

- `./gradlew build` 通過
- `python3 -m py_compile` 通過
- `--policy-type gru --value-quantile 0.5` で旧と等価動作
- `--policy-type transformer --value-quantile 0.7` でゼロから学習開始、loss が降下
