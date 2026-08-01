---
name: plan-genshin-implementation
description: Author, extend, and close out `TASKS.md` implementation plans using this repository's phase schema, covering status text, scope and out-of-scope lists, per-phase target files, tasks, acceptance criteria, test cases, verification commands, and cross-cutting rules. Use before implementing any multi-step feature, refactor, accuracy pass, or RL change.
---

# Plan a Genshin implementation

1. Read `TASKS.md` in full, then [plan-schema.md](references/plan-schema.md). Extend the existing document; never create a parallel plan file and never turn the plan into a run log or a training diary.
2. Verify the `## Current Status` text still describes reality. Correct stale status prose in the same pass that adds new work.
3. Open a plan as `## Implementation Order: <Title>` followed by Status, Scope, an explicit out-of-scope list, and Definitions for every new class, interface, or type name the plan introduces.
4. Decompose into phases where each phase compiles, validates, and can be committed on its own. Order phases so every later phase depends only on earlier ones, and state the dependency in that phase's Why section.
5. Give each phase `### Phase N: <Title>` with Why, Target files, Tasks, Acceptance criteria, Test cases to add or update, and Verification. Name real repository paths and real Gradle or Python commands, not categories.
6. Write acceptance criteria that an executable command or an inspectable artifact can settle. Decide before implementation whether a phase needs new regression coverage, and record the reason when it intentionally adds none.
7. Keep shared constraints in `## Cross-Cutting Rules` (testing, implementation style, reporting, RL compatibility) and untouched systems in `## Deferred Systems` rather than repeating them per phase.
8. Close a phase by appending ` - Done` to its heading and updating the plan Status and `## Current Status`. Preserve the completed phase's record; never delete or rewrite finished phases.
9. Report which phases were added, which are now done, and the exact verification command each remaining phase requires.

Do not write a phase whose completion cannot be proven, and do not restate combat mechanics that `README.md` or the code already defines.
