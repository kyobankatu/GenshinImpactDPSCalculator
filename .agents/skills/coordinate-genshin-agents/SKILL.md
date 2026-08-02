---
name: coordinate-genshin-agents
description: Coordinate explicitly authorized bounded sub-agent, branch-isolated implementation, Codex-Claude, independent review, comparison, or handoff work for this simulator. Use when the user asks for multiple agents, parallel branches, cross-client collaboration, independent audits, non-blocking investigation, or grants session-level authority to delegate when useful.
---

# Coordinate Genshin agents

1. Read [coordination-contract.md](references/coordination-contract.md). Confirm the user and repository permit delegation; do not infer it from task size. A user's explicit blanket authorization for one autonomous session remains valid throughout that session and does not require per-agent confirmation.
2. Before spawning, identify the primary critical-path unit and independent sidecars. Keep the immediate blocker local, then delegate only work that can run while the primary continues meaningful non-overlapping work.
3. Keep the primary agent responsible for scope, design decisions, integration, publication, external actions, and user communication.
4. Delegate only independent, bounded scopes with exact files, authority, exclusions, output, and acceptance checks. Give implementation delegates an isolated branch or worktree from a named baseline revision; never let two agents share a writable checkout. Avoid concurrent edits to coupled runtime/mechanics/RL contracts.
5. Start with at most two concurrent delegates. Prefer concrete code units with disjoint source and test paths; otherwise use research, inventory, or review sidecars. Do not spawn work whose integration cost exceeds its likely time saving.
6. For reviews, pass raw source/diffs/evidence without leaking the expected conclusion. For implementation, assign non-overlapping write sets.
7. Keep commits and publication explicit. A delegate may commit only on its assigned branch when authorized; it never pushes, rebases the primary branch, submits jobs, or edits `TASKS.md`/`BACKLOG.md` unless the assignment says so.
8. Require durable handoff: branch and baseline, status, facts, hypotheses, changed files, commits, commands, results, failures, retry safety, and next action.
9. Independently inspect every result, actual diff, and material claim. Integrate one branch at a time and run proportional checks on the combined primary tree before updating plan or ledger status.
10. Close completed or unneeded agents promptly. At wind-down, leave unverified work unintegrated and record its exact branch or handoff state.
11. Stop rather than fabricate a collaborator result when the requested client or isolation surface is unavailable.
