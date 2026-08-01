# Java/Python RL contract matrix

| Contract | Java owner | Python owner | Validate |
|---|---|---|---|
| action IDs and masks | `RLAction`, `ActionSpace`, `BattleEnvironment` | policy action layout, evaluation | Java regression, Python tests, bounded live step |
| observation layout | `ObservationEncoder` | `recurrent_ppo.py`, checkpoint validation | shape/feature tests and checkpoint compatibility |
| privileged observation | `PrivilegedStateEncoder` | learner loss/input handling | paired shape and semantic checks |
| party IDs/order/metadata | `RLPartyRegistry`, `RLPartySpec`, service metadata | client, training logs, evaluation aggregation | `PartyCatalogRegressionTest`, permutation tests |
| binary framing | `mechanics/rl/bridge/BatchProtocol`, `RolloutService` | `binary_protocol.py`, `rollout_service_client.py` | byte-level round trip and live service smoke |
| reset/step result | `BattleEnvironment`, `ActionResult`, vectorized runner | rollout client and trainer | normal, invalid-action, terminal episode paths |
| Vine snapshots | simulator snapshot, `BattleEnvironment`, service commands | client/learner branch logic | restore equality, release, stale/invalid ID paths |
| reward | `RewardFunction`, `EpisodeConfig` | training interpretation and evaluation summaries | component tests and matched evaluation |
| checkpoints | service metadata and capability profiles | `recurrent_ppo.py`, train/evaluate loaders | explicit compatibility accept/reject tests |

Common checks:

```text
./gradlew PartyCatalogRegressionTest
./gradlew BenchmarkRLJava
python -m pytest src/python/rl/tests
./gradlew build
```
