# Git contract

## Branch layout

| Branch | Role |
|---|---|
| `master` | Integration branch and default PR target. Agents do not commit here. |
| `dev_0` | Standing development branch. Default target for autonomous and interactive work. |
| `dev_<n>` / session branch | Created from current `master` when work should stay isolated. |

`origin` is `git@github.com:kyobankatu/GenshinImpactDPSCalculator.git`. `origin/HEAD` points at `master`.

## Linear history rule

The repository contains zero merge commits. Preserve that property:

- integrate with `git rebase` or fast-forward;
- never create an explicit merge commit;
- if a rebase conflicts in generated or ignored output, prefer the branch version of source and regenerate the artifact rather than hand-merging report HTML.

## Commit subject convention

Format: `<type>: <lowercase imperative summary>`.

| Type | Use for |
|---|---|
| `feat` | new mechanic, character, chart, RL capability |
| `fix` | incorrect behavior, formula, protocol, or report defect |
| `change` | intentional behavior/config/doc adjustment that is neither a feature nor a defect fix |
| `refactor` | structure change with no intended behavior change |
| `docs` | `README.md`, `AGENTS.md`, `TASKS.md`, Javadoc, skill catalog |
| `test` | regression entry points and script tests |
| `debug` | temporary diagnostic logging that is expected to be removed |

Observed examples from this history:

- `feat: add continuous enemy aura decay model`
- `fix: fix burst energy timeline markers`
- `refactor: centralize party definitions and launchers`
- `change: update doc and html report`
- `docs: add dual-client agent skill catalog and validation tooling`

Multiple types may appear in one subject when the change is inseparable, matching existing entries such as
`feat: delete SIL feat: implement VinePPO`. Prefer separate commits when the parts can be validated separately.

Root `AGENTS.md` also describes a `subsystem: summary` style. The type-prefixed form above is the convention
actually present in every commit and is authoritative for new commits.

## Never-commit boundary

These are ignored on purpose. Staging any of them is a defect, not a decision:

- `*.sh` including the whole `execute*.sh` / `evaluate.sh` job family, and `execute.sh` by name
- `logs/`, `output/`, `wandb/`, `sweeps/`
- `bin/`, `build/`, `.gradle/`
- `*.class`, `*.jar`, `*.jfr`, `*.log`, `*.iml`
- `rl_report.html`, `learning_curve.png`, `stats_dump.txt`
- `.claude/settings.local.json`
- `__pycache__/`, `.venv/`
- `articles/`, `tools/`

Tracked on purpose, despite looking generated:

- `docs/` published report and Javadoc assets (commit only when the task is about published output)
- `gradle/wrapper/gradle-wrapper.jar`
- `.claude/skills/` shims and `.agents/skills/` canonical skills

## Unattended session authority

Pre-cleared without asking:

- create a work branch from current `master`;
- commit on `dev_0` or a session branch;
- push a work branch, including the first push that sets upstream;
- stash and restore paths the session itself modified.

Deferred to the handoff instead of attempted:

- any commit or push to `master`;
- force push, history rewrite, hard reset of a branch with an upstream;
- deleting remote branches, tags, or stash entries created outside the session;
- opening or merging a pull request.

Do not pause an unattended session to request one of the deferred actions. Complete the pre-cleared work,
record the deferred action with its rationale, and continue with the next queue item.

## Handoff record

State branch, base revision, ordered commit subjects, push target and result, surviving stash entries,
intentionally uncommitted paths, and any deferred git action awaiting the user.
