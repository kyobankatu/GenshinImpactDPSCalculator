# Verification gate

## Tools

| Command | Role |
|---|---|
| `python scripts/agent_validate.py --path <path>` | print the checks implied by one or more changed paths |
| `python scripts/agent_validate.py --base origin/master` | derive paths from a committed range plus working tree |
| `python scripts/agent_validate.py --run` | run the printed checks |
| `python scripts/preflight.py` | routed checks plus staged-artifact leak detection |
| `python scripts/preflight.py --run` | the same, executing the checks |
| `python scripts/validate_agent_assets.py` | validate the dual-client skill catalog |

`preflight.py` is the default pre-commit gate because it adds the leak check. `agent_validate.py` remains
useful when you want the plan for a hypothetical path without touching the working tree.

## Routing table

Derived from `scripts/agent_validate.py`. Treat the script as authoritative if it diverges.

| Changed path prefix | Selected check | Command |
|---|---|---|
| `AGENTS.md`, `README.md`, `.agents/`, `.claude/`, `scripts/` | `agent-assets`, `agent-tools-tests` | `validate_agent_assets.py`, `unittest` over `scripts/tests` |
| `build.gradle`, `src/java/`, `config/characters/`, `config/capability_profiles/` | `java-build` | `./gradlew build` |
| `src/java/mechanics/{reaction,element,formula,buff,energy}/`, `src/java/model/{character,weapon,artifact}/`, `src/java/simulation/{runtime,action,event}/` | `reaction-regression` | `./gradlew ReactionRegressionTest` |
| `src/java/simulation/party/`, `src/java/mechanics/rl/`, `src/java/sample/PartyCatalogRegressionTest` | `party-catalog-regression` | `./gradlew PartyCatalogRegressionTest` |
| `src/java/visualization/`, `src/java/mechanics/analysis/` | `report-regression` | `./gradlew ReportRegressionTest` |
| `src/java/mechanics/optimization/` | `raiden-party`, `flins-party` | `./gradlew RaidenParty`, `./gradlew FlinsParty2` |
| `src/java/mechanics/rl/` | `java-rollout-benchmark` | `./gradlew BenchmarkRLJava` |
| `src/python/rl/`, `requirements.txt` | `python-rl-tests` | `python -m pytest src/python/rl/tests` |

Checks the router does not select but that a change may still require:

- `./gradlew ProfileCapabilities` when capability semantics changed and regeneration is intended.
- `./gradlew javadoc` when public API documentation changed.
- A live `ServeRLJava` plus bounded training or evaluation smoke when a protocol or checkpoint contract changed.

## Numeric baselines

`README.md` records the current audited totals:

- `./gradlew RaidenParty`: 1,362,938 total damage / 64,902 DPS
- `./gradlew FlinsParty2`: 17,044,468 total damage / 246,664 DPS

A change to either number is a reportable result. If the change is intended, update `README.md` in the same
commit and say so; if it is unintended, treat it as a regression. Known nondeterminism: `RaidenParty` can vary
because Skyward Spine Vacuum Blade procs are random, and the optimizer consumes that randomness.

## Leak check

`preflight.py` fails when a staged path matches the never-commit boundary: `*.sh`, `*.class`, `*.jar`,
`*.jfr`, `*.log`, `logs/`, `output/`, `wandb/`, `sweeps/`, `bin/`, `build/`, `.gradle/`, `articles/`,
`tools/`, `__pycache__/`, `.venv/`, `rl_report.html`, `learning_curve.png`, `stats_dump.txt`, and
`.claude/settings.local.json`. `docs/` is tracked on purpose and is not a leak.

## Reporting

State, in order: the change set, the checks the router selected, each command with its observed outcome, any
selected check that was skipped and the reason, baseline numbers before and after when they moved, and
whether checkpoints or committed reports are now behaviorally incompatible.
