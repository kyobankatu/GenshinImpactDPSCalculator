---
name: manage-genshin-git
description: Manage branches, commits, pushes, stashes, and history hygiene in this repository, including the linear-history rule, the commit subject convention, the never-commit artifact boundary, and git authority during unattended sessions. Use for any commit, push, branch, stash, rebase, or repository history decision.
---

# Manage Genshin git history

1. Read root `AGENTS.md` and [git-contract.md](references/git-contract.md) before the first commit of a session. Establish current branch, upstream, dirty paths, and existing stashes in one inspection pass.
2. Never commit on `master`. Commit on `dev_0` or a session branch created from the current `master`. Push work branches freely; leave `master` commits, merges into `master`, force pushes, history rewrites, and remote branch or tag deletion to the user.
3. Keep history linear. This repository has no merge commits. Integrate with rebase or fast-forward only.
4. Write one subject line per commit as `<type>: <lowercase imperative summary>` using `feat`, `fix`, `change`, `refactor`, `docs`, `test`, or `debug`. Keep one mechanical change per commit and combine types in a single subject only when the change is genuinely inseparable.
5. Stage explicit paths. Never stage the whole tree: `logs/`, `output/`, `*.sh`, `wandb/`, `sweeps/`, `bin/`, `build/`, and `.claude/settings.local.json` are ignored deliberately and must stay untracked. Run `python scripts/preflight.py` before committing.
6. Treat generated `docs/` output and root report HTML as build artifacts. Commit them only when the task is explicitly about published output.
7. Stash with a descriptive message and named paths when switching branches. Never drop, clear, or pop a stash you did not create, and list surviving stash entries in the handoff.
8. Report branch, every commit subject created, push target and result, surviving stashes, and anything intentionally left uncommitted.

Never rewrite published history, force-push, or hard-reset a branch that has an upstream without an explicit instruction naming that branch.
