---
name: coordinate-genshin-agents
description: Coordinate explicitly requested bounded sub-agent, branch-isolated implementation, Codex-Claude, independent review, comparison, or handoff work for this simulator. Use when the user asks for multiple agents, parallel branches, cross-client collaboration, independent audits, or non-blocking investigation.
---

# Coordinate Genshin agents

1. Read [coordination-contract.md](references/coordination-contract.md). Confirm the user and repository permit delegation; do not infer it from task size.
2. Keep the primary agent responsible for scope, design decisions, integration, publication, external actions, and user communication.
3. Delegate only independent, bounded scopes with exact files, authority, exclusions, output, and acceptance checks. Give implementation delegates an isolated branch or worktree from a named baseline revision; never let two agents share a writable checkout. Avoid concurrent edits to coupled runtime/mechanics/RL contracts.
4. For reviews, pass raw source/diffs/evidence without leaking the expected conclusion. For implementation, assign non-overlapping write sets.
5. Keep commits and publication explicit. A delegate may commit only on its assigned branch when authorized; it never pushes, rebases the primary branch, submits jobs, or edits `TASKS.md`/`BACKLOG.md` unless the assignment says so.
6. Require durable handoff: branch and baseline, status, facts, hypotheses, changed files, commits, commands, results, failures, retry safety, and next action.
7. Independently inspect every result, actual diff, and material claim. Integrate one branch at a time and run proportional checks on the combined primary tree before updating plan or ledger status.
8. Stop rather than fabricate a collaborator result when the requested client or isolation surface is unavailable.
