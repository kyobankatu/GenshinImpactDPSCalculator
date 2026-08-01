# TASKS.md plan schema

`TASKS.md` is the single durable implementation plan for this repository. It is not a changelog, not a
benchmark log, and not a place for chat history.

## Document layout

```
# Accuracy Implementation Plan

## Current Status          - prose describing what is finished right now
## Scope                   - what the current pass treats as baseline plus an out-of-scope list
## Current Baseline        - implemented behavior and the commands that cover it
## Implementation Order: <Title>
### Phase 1: <Title> - Done
### Phase 2: <Title>
...
## Cross-Cutting Rules
### Testing
### Implementation Style
### Reporting
### RL Compatibility
## Deferred Systems
```

Multiple `## Implementation Order:` blocks may coexist. Keep finished blocks in place with their phases
marked done, so the plan doubles as an audit trail.

## Plan block sections

- **Status**: implemented / in progress / not started, which phases are complete, and the one-sentence
  requirement the plan exists to satisfy.
- **Scope**: bullets naming the concrete responsibilities that move, and the single source of truth each
  should end up in.
- **Out of scope for this pass**: bullets. Always include what the plan deliberately will not touch, such as
  combat formula changes, RL tensor shapes, generated `docs/` output, or new frontend tooling.
- **Definitions**: new class, interface, package, or config names with one line each, so later phases can
  reference them without re-explaining.

## Phase sections

Every phase uses these headings, in this order:

| Heading | Content |
|---|---|
| `Why first` / `Why second` / `Why` | the dependency that forces this position in the order |
| `Target files:` | real paths, including `new ...` for files to be created |
| `Tasks:` | imperative bullets, each one reviewable in isolation |
| `Acceptance criteria:` | observable end state, settleable by a command or artifact inspection |
| `Test cases to add or update:` | named regression additions, or an explicit statement that none are needed and why |
| `Verification:` | exact commands, for example `./gradlew classes`, `./gradlew ReactionRegressionTest` |

Phase sizing: one phase should be a single commit's worth of coherent change that leaves the tree building.
If a phase needs more than roughly a dozen target files or mixes unrelated subsystems, split it.

## Verification vocabulary

Use the commands the repository actually exposes:

- `./gradlew classes` for a compile-only gate in early structural phases
- `./gradlew build` for any Java or config change
- `./gradlew ReactionRegressionTest` for reaction, aura, ICD, Lunar, formula, character/weapon/artifact hooks
- `./gradlew PartyCatalogRegressionTest` for party definitions, catalog, and RL registry
- `./gradlew ReportRegressionTest` for visualization and analysis
- `./gradlew RaidenParty` / `./gradlew FlinsParty2` for sample and optimizer regressions
- `./gradlew BenchmarkRLJava`, `./gradlew ProfileCapabilities` for RL-facing contracts
- `python -m pytest src/python/rl/tests` for the Python learner
- `python scripts/agent_validate.py --path <path>` to derive the minimum set for changed paths

## Closing a phase

1. Append ` - Done` to the phase heading.
2. Update the plan block Status to name the completed phases.
3. Update `## Current Status` when the plan block finishes entirely.
4. Add any newly discovered limitation to `## Deferred Systems` or to the `README.md` accuracy notes rather
   than leaving it only in the phase text.

## Anti-patterns

- A phase whose acceptance criteria is "code is cleaner" or "tests pass" with no named command.
- Target files listed as directories or subsystem names instead of paths.
- Deleting a finished phase to shorten the document.
- Duplicating the cross-cutting testing rules inside each phase.
- Recording run-by-run measurements, job IDs, or W&B links in the plan; those belong in an experiment record.
- Recording undiscovered or rejected candidate work in the plan; that belongs in the `BACKLOG.md` ledger
  maintained by `discover-genshin-work`. The plan holds only work that is being done.
