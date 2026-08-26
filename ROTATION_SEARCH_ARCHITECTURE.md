# Rotation Search And Neural Guidance Architecture

## 1. Purpose

本書は、ローテーション探索器を改善し、その後にニューラルネットワーク
（NN）を探索支援として統合するための設計方針を定める。

実装順序、対象ファイル、受け入れ条件、検証コマンドの正本は
`TASKS.md` の B-212 Phase 16 以降とする。本書は第二の進捗台帳ではなく、
各Phaseを通して維持する責務分離、比較条件、品質ゲートを説明する。

目標は、任意の登録済みパーティとloadoutに対して、合法かつ周期的に
継続可能な高品質ローテーションを生成できるモデルを作ることである。
非凸な探索空間に対する大域最適性は主張せず、同一予算内のbest-found
rotationとして評価する。

## 2. Current State

現在の探索器自体はNNではない。

- `EvolutionaryRotationSearcher` は決定論的な進化的探索である。
- `MctsRotationSearcher` はMCTSであり、評価値はJava simulatorのrollout
  objectiveを使用する。
- `ExpertPolicyPrior` は一様priorまたは記録済みpriorを供給できるが、
  現在のJava探索中に学習済みNNを推論しているわけではない。
- Python側にはrecurrent policy/value modelが存在するが、現行のteacher
  searchとは独立している。
- `EvolutionaryRotationSearcher` は初期action seedを受け取れる一方、
  `MctsRotationSearcher` は現状そのseedを利用しない。

したがって、最初に必要なのはモデルの大型化ではなく、simulatorを教師と
する探索器が人間baselineを正しく再生し、十分な完全候補を評価し、実行可能
な候補だけを教師データとして採用できる状態にすることである。

## 3. Architecture Decision

採用する構造は次の通りとする。

```text
Human rotation seeds ----+
Uniform/random proposal --+--> Search strategy --> Rotation environment
NN policy/value guidance -+                          |
                                                     v
                                           Java combat simulator
                                                     |
                                                     v
                                      Feasibility-first rank/archive
```

責務は以下のように固定する。

1. Java combat simulatorをdamage、energy、aura、ICD、time、eventの採点権威
   とする。
2. Search strategyは候補生成と探索順序を担当し、ゲームルールを複製しない。
3. NNはpolicy priorとvalue estimateによって探索順序を改善する任意の支援器
   とし、合法性、action mask、終了条件、最終scoreを決定しない。
4. NN checkpointが欠落、不整合、NaN、無効logitを返した場合はfail closed
   または一様priorへ明示的にfallbackする。
5. 人間rotationは初期seedとbaselineであり、最終教師ラベルとは限らない。
6. 全比較は同一scenario、同一loadout、同一simulator-callまたはstep予算、
   同一seed集合で行う。
7. NN guided searchがunguided searchと人間baselineを品質ゲートで上回るまで、
   分散学習や大規模sweepを開始しない。

この分離により、NNの構造を変更してもsimulatorの正しさを失わず、NNなしの
決定論的fallbackを常に比較対象として残せる。

## 4. Stage A: Deterministic Teacher Repair

`TASKS.md` B-212 Phase 16で、まず教師探索の正しさを確立する。

### 4.1 Strict human-seed replay

- `SourcedRotationSeed` を対象scenarioのaction列へ変換し、順序と回数を変えず
  に評価する。
- source seedのactionが非合法なら、別actionへ黙って置換せず、そのseedを
  理由付きでrejectする。
- random proposalやmutationのrepairを許す場合は、strict replayとは別mode
  として記録する。
- 現行の固定`maxActions`より長いsource seedを途中で切らない。cycle duration
  とseed長からscenario-aware budgetを決定する。

現在のsource catalogには長いrotationと連続する短いWaitが多いため、固定64
actionや128-step程度の予算では、人間baselineの再生や完全なpopulation /
generation評価を保証できない。

### 4.2 Feasibility-first ranking

候補比較は次の優先順にする。

1. actionがすべて合法である。
2. trajectoryが要求された終了条件まで完了している。
3. 次cycleへ移行できるenergy、cooldown、party stateを満たす。
4. 上記が同順位の場合にのみdamage、DPS、objective scoreを比較する。

未完了の瞬間火力候補が、完了した持続可能rotationより上位になるscore-only
archiveは禁止する。実行可能候補が存在しない場合は、通常archiveとは分離した
diagnostic fallbackとして保持し、behavior-cloning labelには使用しない。

### 4.3 Search accounting

単一のstep counterだけでなく、少なくとも以下を別々に記録する。

- simulator callsとenvironment steps
- completed trajectories
- completed populations
- completed generations
- legal、repaired、rejected、incomplete、cyclic-infeasible candidate数
- human seed、random、unguided、guidedそれぞれのbest/median score

進化的探索のproduction runは、初期populationだけでなく少なくとも一つの
mutation generationを完了しなければ成功扱いにしない。

### 4.4 Snapshot safety

探索の高速化にsnapshot restoreを用いる前に、owner-scoped delayed eventを
持つ全RL対象characterを監査する。

- pending event、owner state、energy、cooldown、aura、ICD、RNGを復元する。
- `SnapshotAwareCharacterEffect` のcapture/restoreが不完全なscenarioは、
  branch-safe search対象としてfail closedする。
- reset + action-history replayとdirect snapshot restoreで、同一event trace、
  action legality、energy、scoreになることを回帰テストする。

## 5. Stage B: Search Throughput

正しさのゲートを通過した後に、候補評価数を増やす。

### 5.1 Direct snapshot restore

現行のrestoreがresetとaction-history replayに依存する場合、初期状態または
探索分岐点の完全snapshotから直接復元する。reset、step、trajectory、complete
generationごとのwall-clockを測り、同じtrajectoryを返すことを確認してから
採用する。

### 5.2 Wait macro

短いWaitの連続列は、探索内部ではrun-length geneまたはWait macroとして扱う。
ただし、simulator実行、dataset保存、既存protocolの境界では安定した
`PolicyAction` 列へ展開する。

これにより探索次元を減らしつつ、action vocabularyやcheckpoint contractを
不用意に変更しない。macroの圧縮・展開は可逆でなければならない。

### 5.3 Performance acceptance

速度改善は、同一候補、同一score、同一snapshot traceを保った上でのみ採用
する。単純なsteps/sだけでなく、以下を主要指標とする。

- completed feasible trajectories/s
- completed generations/hour
- feasible improvement per simulator call
- reset/restore latency
- duplicate candidate ratio

## 6. Stage C: Expert Dataset Quality Gate

`TASKS.md` B-212 Phase 17で、モデル比較前に教師データを凍結する。

- rank-qualified trajectoryだけをpublishする。
- source seed、親trajectory、search algorithm、config、simulator build、loadout、
  random seedをprovenanceとして保存する。
- exact replayで合法性、score、cycle feasibilityを再検証する。
- human baseline、deterministic random、unguided searchに対する改善量を保存する。
- duplicateまたはほぼ同一trajectoryを計測し、partyごとの過剰代表を防ぐ。
- train、validation、holdout間のcharacter、party、archetype leakageを数値化する。

現行catalogは完全なcharacter-disjoint 3-way splitを作れない構造を含むため、
Phase 17開始時にsplit policyを明示的に決める。少なくともparty/loadout-disjoint
を維持し、shared support characterのleakageを報告する。holdoutが空のdataset
は最終品質ゲートを通過させない。

既存datasetは、simulator fingerprint、party fingerprint、action layout、または
quality policyが一致しない場合に再利用しない。

## 7. Stage D: Neural Guidance

教師探索とdatasetが品質ゲートを通過した後、NNを探索支援として統合する。

### 7.1 Model outputs

NNは各stateに対して次を出力する。

- legal action上のpolicy prior
- そのstateから得られる期待teacher objectiveのvalue estimate
- 必要ならcycle completionまたはfeasibilityの補助予測

action maskはJava環境から与え、mask外actionの確率は必ず0にする。最終trajectory
scoreはNN valueではなくsimulator rolloutで確定する。

### 7.2 Search integration

- MCTSではpolicy priorをPUCTの展開順序に、valueを未展開leafの評価に使う。
- 進化的探索ではpolicy priorを初期candidate生成とmutation proposalに使い、
  simulator objectiveとfeasibility rankでselectionする。
- human seedを両探索器のbaseline/archiveへ同じ条件で投入する。
- NNなし、一様prior、記録prior、学習priorを同じinterfaceで切り替えられる
  ようにする。

### 7.3 Model acceptance

少なくとも五つの固定search seedを用い、同一call予算で以下を比較する。

- human seed only
- deterministic random search
- unguided evolutionary search / MCTS
- NN-guided evolutionary search / MCTS

NN-guided searchはpublish対象scenarioのmedianでhuman baselineとunguided search
の両方以上であり、未見holdoutでも改善を示す必要がある。平均値だけで失敗
scenarioを隠してはならない。

## 8. Stage E: Expert Iteration

NN統合後は、以下を反復する。

1. 現行policy/valueで探索をguideする。
2. simulatorでrank-qualifiedな改善trajectoryだけを採用する。
3. lineage付きでdatasetへ追加し、重複とsplit leakageを検査する。
4. policyはteacher action、valueはsimulator returnを教師として再学習する。
5. 固定benchmark suiteで旧model、unguided search、人間baselineと比較する。

新modelが品質ゲートを満たした場合だけchampionを更新する。改善が停止した
場合は、modelを大型化する前にdataset coverage、teacher diversity、value
calibration、search budgetのどこが律速かを切り分ける。

## 9. Model-Structure Research

datasetと評価条件を固定した後に、モデル構造を比較する。候補には小型MLP、
GRU/LSTM、Transformer系sequence modelを含められるが、全候補で次を揃える。

- 同一train/validation/holdout split
- 同一teacher labelsとobservation/action contract
- 同一training tokenまたはwall-clock budget
- 同一search call budget
- 同一checkpoint selection rule

比較指標はofflineのaction accuracyだけにしない。policy top-k accuracy、value
rank correlation/calibration、最終的なfeasible improvement per simulator call、
未見partyのteacher advantageを併記する。

## 10. Required Tests

基本的な正常系と異常系は以下とする。

### Normal

- source seedが同一action列としてstrict replayされる。
- 64または128 actionを超える長いseedが途中で切られない。
- feasible human seedが、それ未満のcandidateによってarchiveから消えない。
- complete and cyclic trajectoryがincomplete damage spikeより上位になる。
- snapshot restoreとuninterrupted executionのtrace、energy、scoreが一致する。
- Wait macroの圧縮・展開がround-tripする。
- 固定seed、config、buildでsearch結果とcounterが再現する。
- NN guidanceの有無にかかわらずaction maskとsimulator scoreが一致する。

### Abnormal

- illegal source actionを別actionに置換せずrejectする。
- generationを完了できない予算をproduction successにしない。
- snapshot未対応のpending owner eventを持つscenarioをsearch対象にしない。
- incomplete、cyclic-infeasible、duplicate trajectoryをexpert labelにしない。
- 空holdoutまたはsplit leakage上限超過でdataset gateを失敗させる。
- checkpoint fingerprint/action layout不一致を受け入れない。
- NNのNaN、全mask外logit、timeout時に明示的fallbackまたはrejectする。
- NN guidanceがmask外actionやsimulator未検証scoreをarchiveへ注入できない。

## 11. Exit Criteria

次のすべてを満たした時点で、「教師探索基盤が完成しNN研究へ進める」と判断
する。

- 全publish trajectoryがlegal、complete、cyclically feasible、exactly replayable
  である。
- 各publish scenarioのteacher medianがhuman baselineとdeterministic random
  の両方以上である。
- 少なくとも一つの完全populationとmutation generationが計測されている。
- snapshot safety auditとWait macro round-trip testが通る。
- datasetに非空のtrain、validation、holdoutがあり、leakageが報告されている。
- benchmarkが同一simulator-call予算と固定seedで再現できる。

その後、NN-guided searchが同じゲートでunguided teacher以上となり、未見holdout
でも改善を維持した場合にのみ、expert iterationを主経路へ昇格する。
