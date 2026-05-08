# Multi-Action Counterfactual VinePPO

## 問題

現状の vine 計算は「選ばれた行動 vs ランダム行動」の比較しか行わない：

```
vine_adv = Q_MC(s, a_chosen) - Q_MC(s, a_random)
```

PPO は実際に選ばれた行動の確率しか直接更新できないため、ポリシーが「ATTACKを選んだが、もしSKILLを選んでいたら更に良かった」という反実仮想（counterfactual）の信号を得られない。

結果として、「即時の高ダメージ行動（例：高DPSキャラへのswap）」が過大評価され、「セットアップ→ペイオフの多段ローテーション（例：フォーム発動→フォーム中攻撃継続）」が学習されにくい。これはどのパーティ・どのキャラでも起きる構造的問題。

## 方針

vine snapshot ごとに**全有効行動の Q_MC を評価**し、それらの平均をベースラインとして counterfactual advantage を計算する：

```
vine_adv = Q_MC(s, a_chosen) - mean_{a ∈ valid} Q_MC(s, a)
```

これにより：
- a_chosen が有効行動の中で最良に近ければ正のadvantage → 強化
- a_chosen が劣るなら負のadvantage → 抑制（エントロピー再分配により他の良い行動が伸びる）
- 行動選択がパーティ非依存：行動IDのみで動作するので汎用性を保つ

実装上は単一TCPラウンドトリップで全行動分のQ_MCを返すように Java/Python 双方を更新する。

期待値的には mean baseline ≈ random baseline だが、
- 各行動について K 分岐で Q を推定するため、平均化により分散が下がる
- 全行動を実際に評価するため、ベースラインがより正確

---

## Task 1: プロトコルバージョン更新

**ファイル**:
- `src/java/mechanics/rl/bridge/BatchProtocol.java`
- `src/python/rl/binary_protocol.py`

`VERSION` 定数を 7 → 8 に更新。互換性のない変更があるため、新旧クライアントが接続するとHELLOで失敗する。

---

## Task 2: Java 側の `branchRollout` を multi-action 化

### Task 2a: `BattleEnvironment.branchRolloutMultiAction` を追加

**ファイル**: `src/java/mechanics/rl/BattleEnvironment.java`

新メソッドを追加（既存 `branchRolloutMean` は内部実装として残す）：

```java
public double[] branchRolloutMultiAction(SimulatorSnapshot snap, double snapLastSwapTime,
        int K, int H, double gamma) {
    ensureReset();
    int actionCount = mechanics.rl.RLAction.SIZE;
    double[] qValues = new double[actionCount];

    // スナップショット状態での有効行動マスクを一度だけ取得
    simulator.restoreSnapshot(snap);
    lastSwapTime = snapLastSwapTime;
    stepCount = 0;
    previousActionId = -1;
    java.util.Arrays.fill(slotLastActiveTime, -config.maxEpisodeTime);
    java.util.Arrays.fill(slotOnFieldTime, 0.0);
    fillActionMaskBuffer();
    boolean[] valid = new boolean[actionCount];
    for (int i = 0; i < actionCount; i++) {
        valid[i] = actionMaskBuffer[i] > 0.5;
    }

    for (int a = 0; a < actionCount; a++) {
        if (!valid[a]) {
            qValues[a] = Double.NaN;
            continue;
        }
        qValues[a] = branchRolloutMean(snap, snapLastSwapTime, a, K, H, gamma);
    }
    return qValues;
}
```

### Task 2b: `VectorizedEnvironment.branchRollout` を multi-action 版に置き換え

**ファイル**: `src/java/mechanics/rl/bridge/VectorizedEnvironment.java`

現行の `branchRollout(snapshotId, firstAction, baselineAction, K, H, gamma): double` を削除し、`branchRolloutMulti(snapshotId, K, H, gamma): double[]` を追加：

```java
public double[] branchRolloutMulti(int snapshotId, int K, int H, double gamma) {
    SnapshotEntry entry = snapshotStore.remove(snapshotId);
    if (entry == null) {
        throw new IllegalArgumentException("Unknown snapshot id: " + snapshotId);
    }
    return branchEnv.branchRolloutMultiAction(entry.snapshot, entry.lastSwapTime, K, H, gamma);
}
```

---

## Task 3: `RolloutService` ハンドラ更新

**ファイル**: `src/java/mechanics/rl/bridge/RolloutService.java`

`CMD_BRANCH_ROLLOUT` ハンドラのワイヤフォーマットを以下に変更：

- in: `runner_id (int)`, `snapshot_id (int)`, `K (int)`, `H (int)`, `gamma (double)`
- out: `actionCount (int)`, `qValues[actionCount] (doubles)`

例外時は actionCount=`RLAction.SIZE` で全要素 NaN を返す（既存の例外ハンドリングパターンに従う）。

---

## Task 4: Python 側クライアント更新

### Task 4a: `RolloutServiceClient.branch_rollout` を `branch_rollout_multi` に置き換え

**ファイル**: `src/python/rl/rollout_service_client.py`

返り値を `List[float]`（各要素は Q_MC、無効な行動は NaN）に変更。

```python
def branch_rollout_multi(self, runner_id: int, snapshot_id: int,
                         K: int, H: int, gamma: float) -> list:
    send_int(self.sock, CMD_BRANCH_ROLLOUT)
    send_int(self.sock, runner_id)
    send_int(self.sock, snapshot_id)
    send_int(self.sock, K)
    send_int(self.sock, H)
    send_double(self.sock, gamma)
    action_count = recv_int(self.sock)
    return recv_doubles(self.sock, action_count)
```

### Task 4b: `MultiRolloutServiceClient.branch_rollout` も同様に更新

同ファイルの `MultiRolloutServiceClient.branch_rollout` を `branch_rollout_multi` に置き換える。

---

## Task 5: `apply_vine_ppo_advantages` 更新

**ファイル**: `src/python/rl/train_recurrent_ppo.py`

```python
for seg_idx, step_idx, snap_id, action in vine_candidates:
    try:
        q_values = client.branch_rollout_multi(runner_id, snap_id, K, H, gamma)
    except Exception as e:
        print(f"[VinePPO] branch_rollout_multi failed: snap_id={snap_id} error={e}", flush=True)
        continue
    valid_q = [q for q in q_values if not (q != q)]  # filter NaN
    if not valid_q or action >= len(q_values) or q_values[action] != q_values[action]:
        continue
    chosen_q = q_values[action]
    baseline = sum(valid_q) / len(valid_q)
    vine_adv = chosen_q - baseline
    step = segments[seg_idx]["steps"][step_idx]
    gae_adv = step["advantage"]
    step["advantage"] = vine_adv
    adv_list.append(vine_adv)
    bias_list.append(abs(vine_adv - gae_adv))
```

---

## Task 6: ビルド確認

```bash
./gradlew build
```

エラーがないことを確認する。

---

## リスク

- **計算コスト**: vine 1ポイントあたりの simulator step 数が約3.5倍に増える（2行動 → 平均6行動）。`vine_max_points_per_update=64` でキャップされるため許容範囲。
- **advantage のスケール変化**: 平均ベースラインは期待値的にはランダムベースラインと同等だが、分散が低下するため `vine/setup_adv_mean` の値分布が変わる。学習自体には問題ないが、過去ランとの直接比較は困難。
- **プロトコル破壊的変更**: VERSION bump で旧 Java/Python の組合せは HELLO で失敗する。新しいセットで両方ビルド/起動する必要あり。
- **既存チェックポイント**: NN の構造には影響しないため、`latest-model.pt` から resume 可能。

---

## 完了条件

- `./gradlew build` が通る
- `--use-vine-ppo` で起動して `vine/points` が更新されつつ、`vine/setup_adv_mean` が正負両方の値を取る（counterfactual の正常動作シグナル）
- Java RolloutService ログに `[RolloutService] branch_rollout error` が頻発しない
