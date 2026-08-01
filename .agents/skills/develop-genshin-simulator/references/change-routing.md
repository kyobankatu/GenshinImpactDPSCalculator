# Simulator change routing

Read the root and nearest package `AGENTS.md` files before using a row.

| Changed area | Inspect together | Minimum focused validation |
|---|---|---|
| `simulation/runtime`, `action`, `event` | swaps, timer ordering, buffs, reactions, logging, `SimulatorSnapshot` | `ReactionRegressionTest`, affected party, build |
| reaction, aura, ICD, formula, Lunar | `mechanics/reaction`, `element`, `formula`, `Enemy`, action resolver, affected content | `ReactionRegressionTest`, Raiden or FlinsParty2, build |
| energy or particle flow | energy manager, character energy fields, `EnergyAnalyzer`, optimizer, report timeline | affected party, build |
| buff lifecycle or stats | buff owner, damage formula, snapshots, `StatsRecorder`, report | reaction regression when triggers interact, affected party, build |
| character, weapon, artifact | closest model package, config data, hooks, formula/reaction/report consumers | targeted regression, affected party, build |
| party definition/catalog | party setup, generic runner, RL registry/spec, Gradle dynamic dispatch | `PartyCatalogRegressionTest`, affected party, build |
| optimizer or rotation search | determinism, ER analysis, simulator factories, party rotation | affected party and reproducible benchmark, build |
| snapshot or rollback state | simulator, party, enemy, reaction state, characters, events, RL branch state | snapshot regression, reaction regression, RL checks, build |
| visualization or log record | logger writes, record shape, data builder, view adapter, renderer, file writer | `ReportRegressionTest`, affected report-generating party, build |
| Java/Python RL contract | use `$develop-genshin-rl` | its full routed validation |

Use `./gradlew <Name>` on Unix-like systems and `gradlew.bat <Name>` on Windows.
