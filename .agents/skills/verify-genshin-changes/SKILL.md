---
name: verify-genshin-changes
description: Select and run the smallest sufficient verification for a change set before commit or handoff, covering path-to-check routing, Gradle regression entry points, sample party baselines, Python RL tests, agent-asset validation, and untracked-artifact leakage. Use before declaring any change complete or committing.
---

# Verify Genshin changes

1. Read [verification-gate.md](references/verification-gate.md). Enumerate the actual change set from the working tree rather than from memory of what you edited.
2. Derive the check set with `python scripts/agent_validate.py --path <path>` for each changed path, or `--base origin/master` for a committed range. Do not invent checks the router does not select, and do not skip ones it does.
3. Run the gate with `python scripts/preflight.py --run`. It applies the same routing and additionally fails when an ignored artifact such as `*.sh`, `logs/`, `output/`, or `.claude/settings.local.json` has been staged.
4. Add the phase-specific commands that `TASKS.md` names in its Verification section, plus `./gradlew build` for any Java or config change.
5. Compare numeric baselines, not just exit codes. `RaidenParty` and `FlinsParty2` totals in `README.md` are the reference; a changed total is a finding to report, not noise to absorb.
6. Separate deterministic expectations from known random behavior. Skyward Spine Vacuum Blade procs and other random effects make some sample totals vary; state which differences are expected before attributing them to your change.
7. Run live RL integration only when the changed contract requires it, and keep it bounded: `BenchmarkRLJava`, then a short `ServeRLJava` plus `--preset debug` smoke, stopped afterwards.
8. Report every command run with its observed result, every check the router selected but you skipped and why, and whether existing checkpoints, reports, or committed baselines became incompatible.

A clean compile is not verification. Never report a check as passing unless its actual output was observed.
