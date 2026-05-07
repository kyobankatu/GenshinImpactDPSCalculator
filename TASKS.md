# Fix: Flins on-field form state invisible to RL policy

## 問題

Flins が Manifest Flame 形態中（Skill使用後10秒間）であっても、
RL ポリシーの Observation にそのシグナルが届かない。

- `BurstStateProvider.isBurstActive()` は全キャラ共通の「形態中フラグ」として機能しているが、
  `Flins.isBurstActive()` は常に `false` を返す（未実装）。
- ポリシーからすると「Flinsはスキル使用後に何もできないキャラ」に見えるため、
  すぐに Swap out してしまう。

## 方針

`BurstStateProvider` を `FormStateProvider` に改名し、メソッド名も
`isBurstActive` → `isFormActive` に統一する。

全キャラに共通した「時限的な形態・バースト効果が発動中」という概念を
名称で明示し、Flins の実装漏れを修正する。

Observation サイズは変化しないため、既存チェックポイントから継続学習可能。

---

## Task 1: インターフェース改名

**ファイル**: `src/java/model/entity/BurstStateProvider.java`

- ファイルを `FormStateProvider.java` に改名
- メソッド `isBurstActive(double currentTime)` → `isFormActive(double currentTime)` に改名

---

## Task 2: 各キャラのインターフェース実装を更新（機械的改名）

以下のファイルで `implements BurstStateProvider` → `implements FormStateProvider`、
メソッド名 `isBurstActive` → `isFormActive` に変更する。
ロジックは変更しない。

- `src/java/model/character/RaidenShogun.java`
- `src/java/model/character/Bennett.java`
- `src/java/model/character/Sucrose.java`
- `src/java/model/character/Ineffa.java`
- `src/java/model/character/Xingqiu.java`
- `src/java/model/character/Xiangling.java`

---

## Task 3: Flins の実装を修正（本質的な変更）

**ファイル**: `src/java/model/character/Flins.java`

- `implements BurstStateProvider` → `implements FormStateProvider` に変更
- `isFormActive(double currentTime)` を以下の通り実装:

```java
@Override
public boolean isFormActive(double currentTime) {
    return isManifestFlameActive(currentTime);
}
```

**理由**: Manifest Flame 形態中（Electro-infused 通常攻撃が有効な10秒間）が
Flins にとっての「表でアクションすべき形態」であるため。

Thunderous Symphony（Spearstorm後の特殊バースト状態）は
`getBurstCDRemaining()` が 0 を返すことで `burst readiness = 1.0` となり、
既存の Observation で表現されているため対応不要。

---

## Task 4: 呼び出し側を更新

以下のファイルで `BurstStateProvider` / `isBurstActive` の参照を
`FormStateProvider` / `isFormActive` に変更する。

- `src/java/mechanics/rl/ObservationEncoder.java`
- `src/java/mechanics/rl/PrivilegedStateEncoder.java`
- `src/java/mechanics/optimization/RotationSearcher.java`

---

## Task 5: ビルド確認

```bash
./gradlew build
```

エラーがないことを確認する。

---

## 完了条件

- `./gradlew build` が通る
- `Flins.isFormActive(t)` が Manifest Flame 形態中に `true` を返すことを
  シミュレーションログで確認できる
- Observation サイズ（`ObservationEncoder.OBSERVATION_SIZE`）が変化していない
