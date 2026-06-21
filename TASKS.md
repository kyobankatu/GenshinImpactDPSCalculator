# Accuracy Implementation Plan

## Current Status

The simulator accuracy audit and high-impact fixes for `RaidenParty`,
`FlinsParty2`, and RL-facing metadata are complete.

The previous HTML report detail and UI upgrade is complete. Character, weapon,
and artifact local image assets are now used in the generated report where
available.

The HTML report's reaction damage presentation has been unified. The report now
uses one `Elemental Reaction Damage` view backed by separately recorded elemental
reaction damage, while reaction-labeled direct hits remain in Timeline and
Action Damage only.

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

This pass makes reaction damage reporting precise and consistent.
`Reaction Damage` and `Reaction-labeled Direct Damage` currently describe
related concepts with separate UI treatments. The report should instead expose a
single, clearly defined reaction damage view limited to elemental reaction damage
as defined by Genshin Impact mechanics.

Out of scope for this pass:

- simulator mechanics, damage formulas, optimizer behavior, and RL behavior
- new report asset downloads or changes to the existing `face.png` files
- introducing a frontend framework, build step, or server-side report viewer
- editing generated `docs/` or committed report output unless explicitly
  requested

## Current Baseline

Implemented and covered by `sample.ReactionRegressionTest`:

- reaction metadata and transformative multipliers
- Superconduct, Freeze, Shatter, Crystallize, and Burning
- Bloom, Hyperbloom, and Burgeon core behavior
- Quicken, Aggravate, and Spread additive damage
- Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize behavior
- aura decay and basic gauge consumption
- ICD hit/time rules, no-ICD behavior, and shared ICD blocking
- selected character, weapon, and artifact trigger regressions

Primary validation command:

- `./gradlew ReactionRegressionTest`

Broader sample validation:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

## Implementation Order: Unified Elemental Reaction Damage Reporting

Status:

- Done.
- The split `Reaction Damage` and `Reaction-labeled Direct Damage` presentation has been replaced with one consistent report view for actual elemental reaction damage.

Scope:

- Improve generated HTML report semantics and readability under `output/`.
- Unify the chart/table presentation for reaction damage.
- Restrict reaction-damage aggregation to Genshin-defined elemental reaction damage.
- Keep `Timeline`, `Action Damage`, and total actor damage behavior intact unless a phase explicitly touches only the labels needed to avoid confusion.

Out of scope for this pass:

- Changing simulator damage formulas or reaction mechanics.
- Changing total damage, DPS, cumulative damage, rolling DPS, or action damage definitions.
- Treating reaction-labeled direct hits as reaction damage.
- Adding a frontend framework, build step, or server-side report viewer.
- Persisting report data as a separate JSON artifact unless explicitly requested.
- Editing generated `docs/` or committed report output unless explicitly requested.

Definitions:

- Elemental reaction damage:
  Damage produced by a Genshin-defined elemental reaction itself. Examples include Vaporize bonus-attributed damage if separately measurable, Melt bonus-attributed damage if separately measurable, Swirl, Electro-Charged, Overloaded, Superconduct, Shatter, Burning, Bloom, Hyperbloom, Burgeon, Aggravate bonus damage if separately measurable, Spread bonus damage if separately measurable, Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize.
- Direct hit damage:
  Damage produced by an action hit, even if the hit has a reaction label in the timeline.
- Reaction-labeled direct damage:
  Full hit damage from direct attacks where the event label mentions a reaction but the report cannot separate the reaction component. This must not be included in reaction damage totals.
- Known limitation:
  If additive or amplifying reaction bonus damage is not separately recorded, do not fold the full direct hit into reaction damage. Show it only in timeline/action damage, or expose a clear limitation note.

Design direction:

- Remove the separate `Reaction-labeled Direct Damage` section from the main chart area.
- Rename or clarify the remaining reaction section as `Elemental Reaction Damage`.
- Use one presentation style for all included reaction damage:
  - one primary chart
  - one optional detail table/list using the same data
  - consistent colors and labels
- Include a short report note explaining that direct hits with reaction labels are excluded unless the reaction component is separately recorded.
- Keep timeline rows free to show reaction labels for diagnostics, but do not let those labels define reaction damage totals.

### Phase 1: Audit Current Reaction Report Data Sources - Done

Why first:

The current report appears to build both `reactionDamageTotals` and `reactionLabeledDamageTotals`. Before changing rendering, identify exactly which `SimulationRecord` fields and builder branches populate each dataset.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/visualization/SimulationRecord.java`
- `src/java/sample/ReportRegressionTest.java`

Tasks:

- Trace current aggregation for:
  - `Reaction Damage`
  - `Reaction-labeled Direct Damage`
  - timeline reaction labels
  - action damage totals
- Identify which fields represent separately recorded reaction damage, such as `reactionDamage`.
- Identify which fields represent full direct hit damage, such as `damage`.
- List every reaction label currently emitted by sample parties and whether it should be included in elemental reaction damage.
- Decide whether a typed helper is needed to classify report reaction labels instead of relying on display-label branching in multiple places.

Acceptance criteria:

- The plan has a concrete source of truth for included reaction damage.
- Every existing reaction report dataset is classified as keep, remove, or rename.
- Ambiguous labels are documented before implementation.

Test cases to add or update:

- No code changes required in this phase unless a small helper test is added for discovered classification logic.

Verification:

- `./gradlew classes`
- Optional: `./gradlew ReportRegressionTest` if exploratory assertions are added

### Phase 2: Define a Single Reaction Damage Contract - Done

Why second:

The renderer should consume one well-defined dataset rather than deciding semantics from multiple partially overlapping lists.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportViewAdapter.java` if display adaptation is needed
- `src/java/sample/ReportRegressionTest.java`

Tasks:

- Introduce or standardize one report field for elemental reaction damage totals.
- Keep the dataset limited to separately recorded reaction damage values.
- Exclude full direct-hit damage from reaction totals, even when the timeline event has a reaction label.
- Preserve direct-hit reaction labels only in timeline and action damage views.
- Add a centralized classification helper if needed:
  - included: Genshin-defined reaction damage records with explicit reaction damage values
  - excluded: direct hits, reaction-labeled action damage, labels with zero or missing reaction damage
  - ignored: `None`, empty labels, non-reaction diagnostic labels

Acceptance criteria:

- The report builder exposes exactly one reaction damage aggregate for the chart.
- `reactionLabeledDamageTotals` is removed, deprecated, or no longer rendered.
- No full direct-hit `damage` value is mixed into reaction damage totals.

Test cases to add or update:

- Normal path: explicit reaction damage contributes to the unified reaction total.
- Error path: reaction-labeled direct hit with `reactionDamage == 0` does not contribute.
- Boundary values: empty reaction label, `None`, zero damage, and duplicate reaction labels aggregate predictably.
- Major logic unit test: classification helper includes/excludes labels and values as defined.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew classes`

### Phase 3: Renderer Unification and Label Cleanup - Done

Why third:

Once the data contract is clean, update the HTML so users see one reaction damage concept instead of two competing sections.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/sample/ReportRegressionTest.java`

Tasks:

- Replace `Reaction Damage` and `Reaction-labeled Direct Damage` display with one `Elemental Reaction Damage` section.
- Use the same chart style and color strategy for all included reaction damage.
- Remove the separate `Reaction-labeled Direct Damage` chart and any related explanatory copy.
- Add a concise note near the chart:
  - only separately recorded elemental reaction damage is included
  - direct hits with reaction labels remain in timeline/action damage and are excluded from this chart
- Keep empty-state behavior clear when no reaction damage is present.

Acceptance criteria:

- The generated report has one reaction damage section.
- There is no visible `Reaction-labeled Direct Damage` section.
- The chart title and note accurately describe the data.
- Existing global charts still render.

Test cases to add or update:

- Renderer test: `Elemental Reaction Damage` appears.
- Renderer test: `Reaction-labeled Direct Damage` does not appear.
- Renderer test: empty reaction data renders a non-crashing empty chart or empty state.
- Escaping test: reaction labels containing special characters remain HTML/JS safe.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

### Phase 4: Sample Validation Against Timeline and Action Damage - Done

Why fourth:

This change is semantic. The key risk is accidentally changing totals or hiding useful direct-hit data from timeline/action views.

Target files:

- `src/java/sample/ReportRegressionTest.java`
- `src/java/sample/ReactionRegressionTest.java` only if reaction recording semantics need new coverage
- `src/java/visualization/ReportDataBuilder.java`

Tasks:

- Validate that unified reaction totals are independent from action damage totals.
- Validate that timeline still shows reaction labels for diagnostic rows.
- Validate that direct-hit reaction labels do not increase the elemental reaction chart.
- Compare sample report behavior for:
  - `RaidenParty` conventional reactions such as Vaporize, Overloaded, Electro-Charged
  - `FlinsParty2` Lunar-Charged and related Lunar damage
- Add regression assertions for at least one explicit reaction damage record and one excluded reaction-labeled direct hit.

Acceptance criteria:

- Total DPS and action damage charts are unchanged except for any intentional label cleanup.
- Timeline still exposes reaction labels where useful.
- Unified reaction chart contains only included elemental reaction damage.
- Lunar reaction damage appears in the unified reaction chart when separately recorded as reaction damage.

Test cases to add or update:

- Normal integration: generated sample report contains expected included reaction labels.
- Error integration: direct-hit records with reaction labels are excluded from reaction totals.
- Boundary integration: sample with no reaction damage still produces a valid report.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

### Phase 5: Documentation and Manual Browser Validation - Implemented, Manual Check Pending

Why last:

After the data and UI are unified, record the exact semantics so future report work does not reintroduce mixed reaction/direct-hit totals.

Target files:

- `README.md` if report semantics are documented there
- `TASKS.md`
- `src/java/visualization/ReportHtmlRenderer.java` for inline report copy only

Tasks:

- Document the unified reaction damage definition in the report-facing docs or report note.
- Record known limitations for amplifying/additive reactions when bonus damage is not separately available.
- Manually inspect generated reports for readability and chart consistency.
- Confirm browser console has no JavaScript errors.

Acceptance criteria:

- Future readers can tell why reaction-labeled direct hits are excluded.
- The report no longer presents two competing reaction-damage concepts.
- Manual inspection confirms the chart and detail text are understandable.

Manual inspection checklist:

- Only one reaction damage section is visible.
- Chart labels match known reaction names.
- Timeline still shows reaction labels for relevant events.
- Action Damage still contains direct action hits.
- Direct-hit reaction labels do not appear as separate reaction damage totals.
- Lunar reaction damage appears when explicitly recorded.
- Empty/no-reaction reports remain readable.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- Manual browser inspection of `output/simulation_report.html`

Status note:

- Report copy and automated validation are complete.
- Manual browser console inspection remains pending in a local browser session.

## Cross-Cutting Rules

### Testing

- Use `./gradlew ReactionRegressionTest` for reaction, aura, ICD, Lunar,
  character-hook, weapon-hook, artifact-hook, and formula regressions.
- Use `./gradlew RaidenParty` for conventional sample regressions.
- Use `./gradlew FlinsParty2` for Lunar sample regressions.
- Use RL commands only when RL-facing behavior, profile data, or protocol
  contracts change.
- For each implementation phase, decide whether new tests are required before
  editing. At minimum, cover the normal path and an invalid/no-trigger path for a
  changed mechanic.
- Add boundary-value tests when timing, counters, caps, cooldowns, energy
  thresholds, ICD windows, or buff expiry are part of the behavior.
- Add unit-level tests for major helper logic when it can be isolated without a
  full simulator run.
- Add representative integration tests or executable sample checks when multiple
  systems interact, such as character hooks plus reactions plus item passives.
- If a phase intentionally skips new tests, record why in the final handoff.

### Implementation Style

- Prefer minimal local changes in the affected character, item, runtime, or
  mechanic package.
- Add helper methods only when they remove real duplication or clarify a
  mechanic.
- Keep runtime logic typed: use `CharacterId`, `CharacterActionKey`, `BuffId`,
  `ReactionResult.Kind`, and `ReactionResult.LunarType` instead of display
  labels.
- Preserve current single-target assumptions unless a target party needs a more
  detailed offensive abstraction.

### Reporting

- New or corrected mechanics should appear clearly in logs or HTML reports when
  they affect damage attribution, reaction labels, aura state, or timed effects.
- Presentation labels belong in sample, log, report, and data-boundary code; they
  should not become control-flow keys in simulator internals.

### RL Compatibility

- Treat damage, timing, action masks, observation layout, privileged observation
  layout, party ordering, and role profiles as RL-relevant contracts.
- Existing checkpoints may become behaviorally incompatible after simulator
  accuracy fixes even when tensor shapes remain unchanged.

## Deferred Systems

These systems remain deferred unless a current benchmark party's offensive kit
depends on them:

- defensive shield absorption and player damage intake
- enemy attacks, stagger, movement, and survival pressure
- multi-target geometry and positioning
- exploration-specific systems
- full open-world status interactions that do not affect offensive output in the
  current single-target simulator
