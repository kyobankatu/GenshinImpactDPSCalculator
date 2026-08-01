---
name: clean-genshin-artifacts
description: Classify and safely remove generated Gradle, simulation-report, RL checkpoint, W&B, profiler, cache, log, or temporary artifacts while preserving source, committed documentation, active jobs, and recoverable experiment state. Use before recursive, wildcard, multi-path, or ambiguous cleanup.
---

# Clean Genshin artifacts

1. Read root instructions and [cleanup-boundaries.md](references/cleanup-boundaries.md). Confirm the cleanup is authorized and distinguish tracked source, committed docs, ignored outputs, active artifacts, and unknown paths.
2. Resolve exact canonical targets under one explicitly retained project or experiment boundary. Never target a drive root, home, repository root, unresolved variable, or broad glob.
3. Reconcile live Gradle, Java rollout, Python learner, profiler, scheduler, and checkpoint users before classifying artifacts as stale.
4. Use exact non-recursive unlink for one known file. For recursive, wildcard, synchronized, or multi-path deletion, use an available guarded manifest workflow that inventories identity, entry count, and bytes before apply.
5. Preserve failed-run evidence until its retry safety and diagnostic value are recorded.
6. Verify target absence and protected source/config/docs survival after cleanup. Report what was removed and whether it is recoverable.

Never implement cleanup as an unreviewed `rm -rf`, `Remove-Item -Recurse`, deletion loop, or scheduler-wide cancellation.
