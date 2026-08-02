---
name: plan-genshin-implementation
description: Author, extend, and close out compact `TASKS.md` implementation plans using this repository's phase schema, including implementation-first campaign batches, target files, acceptance criteria, tests, and verification. Use before multi-step features, refactors, accuracy passes, RL changes, or bulk content additions without producing per-item documentation churn.
---

# Plan a Genshin implementation

1. Read [plan-schema.md](references/plan-schema.md), then inspect `TASKS.md` with `rg` and bounded ranges. Read Current Status, shared rules, Deferred Systems, and active plan blocks; do not reload completed plan history in full. Extend the existing document and never turn it into a run log or training diary.
2. Verify the `## Current Status` text still describes reality. Correct stale status prose in the same pass that adds new work.
3. Open a plan as `## Implementation Order: <Title>` followed by Status, Scope, an explicit out-of-scope list, and Definitions for every new class, interface, or type name the plan introduces.
4. Decompose into phases where each phase compiles and validates. For homogeneous content campaigns, make a phase a batch of related implementation units or one shared prerequisite; do not create separate planning and acceptance phases for each character, weapon, or artifact.
5. Give each phase `### Phase N: <Title>` with Why, Target files, Tasks, Acceptance criteria, Test cases to add or update, and Verification. Name real repository paths and real Gradle or Python commands, not categories.
6. Write acceptance criteria that an executable command or an inspectable artifact can settle. Decide before implementation whether a phase needs new regression coverage, and record the reason when it intentionally adds none.
7. Keep shared constraints in `## Cross-Cutting Rules` (testing, implementation style, reporting, RL compatibility) and untouched systems in `## Deferred Systems` rather than repeating them per phase.
8. Close phases at tracked documentation checkpoints. Append ` - Done`, update plan Status and Current Status, and summarize the batch in at most three high-signal completion bullets. Individual verified code commits may temporarily lead tracked status within the bounded documentation cadence defined by `run-genshin-autonomous-session`.
9. Report which phases were added, which are now done, and the exact verification command each remaining phase requires.

Do not write a phase whose completion cannot be proven, restate mechanics already defined by code/README, or expand `TASKS.md` with commit-by-commit logs and repeated baseline payloads.
