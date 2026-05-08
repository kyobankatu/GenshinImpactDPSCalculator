# VinePPO 学習安定化（再正規化 + 水平延長）

## 問題

multi-action counterfactual VinePPO 実装後の学習で：
1. `vine/setup_adv_mean` が ±1000スケール、通常 GAE advantage は正規化済みで ±2 スケール → vine 候補の勾配寄与が500倍となり vine 64点が更新を支配
2. `value_loss` が 23k〜300k と不安定
3. ダメージが 7.8M で頭打ち（前回 9M より低下気味）
4. H=16 ではフォーム持続時間（〜10秒）の恩恵を MC ロールアウトで捉えきれない

## Task A: vine 上書き後の advantage 再正規化

**ファイル**: `src/python/rl/train_recurrent_ppo.py`

`apply_vine_ppo_advantages` の末尾で、vine 上書きが発生した場合に**全 segments の全 step の advantage を再度 mean=0, std=1 に正規化**する。

vine 候補は `compute_advantages` で正規化された GAE advantage を raw な vine_adv (大スケール) で上書きするため、最終的に学習に渡る advantage 分布が崩れる。再正規化で全体スケールを揃える。

擬似コード:
```python
if adv_list:
    all_advs = [step["advantage"] for seg in segments for step in seg["steps"]]
    arr = np.asarray(all_advs, dtype=np.float32)
    mean, std = float(arr.mean()), float(arr.std() + 1e-8)
    for seg in segments:
        for step in seg["steps"]:
            step["advantage"] = (step["advantage"] - mean) / std
```

リスク：
- 通常 GAE advantage 分布が vine 由来の少数の極端値に引きずられて狭くなる可能性 → 64/65536 の比率なら std への影響は限定的
- return_target は影響を受けない（value loss は別計算なのでOK）

## Task B: vine_horizon を 16 → 32 に拡大

**ファイル**: `execute.sh`

```
VINE_HORIZON=${VINE_HORIZON:-16}  # → 32
```

Manifest Flame フォーム持続時間（10秒、〜20-25 step）を MC ロールアウトで完全カバー。

リスク：
- vine 計算コストが2倍 (ただし `vine_max_points=64` でキャップ済みなのでスループットへの影響は限定)

## 完了条件

- `./gradlew build` が通る
- 再学習で `vine/setup_adv_mean` が±2程度のスケール（GAEと同等）に収まる
- `value_loss` が安定（〜10k以下に低下）
