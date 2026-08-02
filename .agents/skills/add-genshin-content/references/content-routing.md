# Content routing

| Content | Primary owners | Required adjacent checks |
|---|---|---|
| character | `model/character`, config data, buffs/formulas/reactions | selected hooks, energy, snapshot, report, affected party |
| weapon | `model/weapon`, stat/passive hooks | refinement, cooldown/randomness, optimizer stability, report |
| artifact | `model/artifact`, stats/buffs | set thresholds, stacking keys, optimizer rolls, report |
| party | `simulation/party` definition and catalog | fixed setup parity, rotation, generic runner, RL registry |
| reaction or custom mechanic | narrow mechanics package plus affected content | aura/ICD, attribution, snapshot, RL observation, report |
| static character data | existing `config/characters` convention and loader | file naming, required fields, runtime loading, Java assumptions |
| RL capability metadata | party definition, registry, profiler, profile JSON | regenerate only after semantic change and review the diff |

For a new party, mechanical setup and scripted rotation have one source of truth in its party definition. Generic simulation and RL launchers must consume that definition.

## Campaign batching

For explicit broad content expansion, keep one compact inventory with columns for content ID, type, source
readiness, shared prerequisite, focused verification, implementation commit, and status. Prefer batches of four
vertical slices or 60 minutes of implementation. Run focused checks per unit and shared expensive checks once at
the batch boundary. Reconcile tracked docs at that boundary and at wind-down; do not add repetitive prose blocks
for every content file.
