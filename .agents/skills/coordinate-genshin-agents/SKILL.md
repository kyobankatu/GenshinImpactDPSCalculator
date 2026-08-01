---
name: coordinate-genshin-agents
description: Coordinate explicitly requested bounded sub-agent, Codex-Claude, independent review, comparison, or handoff work for this simulator. Use when the user asks for multiple agents, cross-client collaboration, independent audits, or parallel non-overlapping investigation.
---

# Coordinate Genshin agents

1. Read [coordination-contract.md](references/coordination-contract.md). Confirm the user and repository permit delegation; do not infer it from task size.
2. Keep the primary agent responsible for scope, design decisions, integration, publication, external actions, and user communication.
3. Delegate only independent, bounded scopes with exact files, authority, exclusions, output, and acceptance checks. Avoid concurrent edits to coupled runtime/mechanics/RL contracts.
4. For reviews, pass raw source/diffs/evidence without leaking the expected conclusion. For implementation, assign non-overlapping write sets.
5. Require durable handoff: status, facts, hypotheses, changed files, commands, results, failures, retry safety, and next action.
6. Independently inspect every result, actual diff, and material claim. Run proportional integration checks on the combined tree.
7. Stop rather than fabricate a collaborator result when the requested client or isolation surface is unavailable.
