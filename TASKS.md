# Accuracy Implementation Plan

## Current Status

The simulator accuracy audit and high-impact fixes for `RaidenParty`,
`FlinsParty2`, and RL-facing metadata are complete.

The HTML report detail and UI upgrade has been approved and implemented through
Report Phase 7. Remaining report work should start from the deferred items and
known limitations recorded in that section.

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

The next work should improve simulator accuracy by auditing and filling gaps in
the currently used parties, weapons, artifacts, and RL party metadata. The goal is
to improve offensive-output accuracy while preserving the current single-target,
non-attacking enemy scope.

Out of scope for this pass:

- enemy attacks, survival pressure, healing prevention, and defensive shield HP
- multi-target positioning, AoE geometry, and enemy movement
- exploration systems such as Arkhe, Nightsoul, or Phlogiston unless an offensive
  kit explicitly depends on them
- new party additions before the existing benchmark parties are audited

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

## Implementation Order

### Phase 1: RaidenParty Accuracy Audit - Done

Why first:

`RaidenParty` is the conventional non-Lunar benchmark team. It is the best place
to find remaining ordinary combat accuracy gaps before touching custom Lunar
logic again.

Target files:

- `src/java/sample/RaidenParty.java`
- `src/java/model/character/RaidenShogun.java`
- `src/java/model/character/Xiangling.java`
- `src/java/model/character/Xingqiu.java`
- `src/java/model/character/Bennett.java`
- relevant files under `src/java/model/weapon/`
- relevant files under `src/java/model/artifact/`
- `config/characters/`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Inventory which Raiden National mechanics are currently exact, simplified, or
  missing.
- Check action timings, cooldown assumptions, burst windows, summon durations,
  snapshot behavior, and particle generation.
- Check Resolve generation and consumption for Raiden.
- Check Xiangling Pyronado and Guoba snapshot/tick behavior.
- Check Xingqiu Raincutter trigger cadence, wave pattern, and Hydro application.
- Check Bennett burst ATK buff timing, field behavior, and interaction with
  snapshotting.
- Check currently equipped weapons and artifacts for missing offensive passives.
- Record high-impact gaps directly in this file before implementing broad
  changes.

Acceptance criteria:

- `RaidenParty` has an explicit accuracy inventory.
- High-impact missing mechanics are listed as implementation tasks.
- Existing reaction regressions still pass.

Implementation status:

- Audited `RaidenParty`, Raiden, Xiangling, Xingqiu, Bennett, and the equipped
  weapons/artifacts.
- Confirmed implemented baseline coverage for Raiden Resolve and Musou energy,
  Xiangling Pyronado snapshot and Guoba/Chili approximation, Xingqiu Raincutter
  C6 wave pattern and C4 skill multiplier, Bennett burst field/Noblesse trigger,
  Emblem, The Catch, Wolf-Fang, Skyward Spine, and Skyward Blade.
- Found one high-impact Resolve issue: Raiden's action listener credited every
  `ActionType.BURST` hit, so Xiangling's multi-hit burst cast granted Resolve
  more than once.
- Remaining simplifications to track later: Xingqiu orbital rain swords are
  modeled as zero-damage Hydro aura ticks; Xiangling Chili pickup is assumed;
  Skyward Spine uses random Vacuum Blade procs, which can make optimizer/sample
  output nondeterministic.

Test cases to add or update:

- Add tests only when the audit identifies an untested behavior or a likely
  regression point.
- Normal path: fixed-script checks for Resolve gain/consumption, burst window
  uptime, snapshot creation, and expected follow-up trigger counts.
- Error/invalid path: action attempts during cooldown, burst attempts without
  enough energy, and reaction/listener hooks that should not fire outside their
  documented window.
- Boundary values: buff expiry at the exact hit time, summon final tick timing,
  Resolve at zero and cap, and energy values just below and just above burst
  cost.
- Unit-level logic: helper methods or character state transitions that can be
  checked without a full sample rotation.
- Integration path: a representative shortened Raiden National rotation that
  verifies damage attribution, reaction labels, energy flow, and report logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Phase 2: RaidenParty High-Impact Fixes - Done

Why second:

The audit should determine the exact order, but fixes should prioritize mechanics
that materially affect DPS, rotation timing, energy, or snapshot behavior.

Candidate tasks:

- Fix Resolve generation or burst damage contribution if the audit finds drift.
- Fix Xingqiu burst follow-up timing or application behavior if it changes
  Vaporize/Electro-Charged frequency.
- Fix Xiangling snapshot behavior if active buffs are not captured correctly.
- Fix Bennett burst buff lifecycle if it affects Pyronado or Raiden burst
  snapshots.
- Add or update focused regression tests for each fixed mechanic.

Acceptance criteria:

- Each implemented fix has a focused regression in `ReactionRegressionTest` or a
  similarly lightweight executable sample.
- `RaidenParty` still runs and any DPS delta is explained in final notes.

Implementation status:

- Fixed Raiden Resolve crediting so each teammate contributes once per actual
  burst cast, even when that burst has multiple `ActionType.BURST` damage hits.
- Added a regression that verifies Xiangling's multi-hit burst cast grants one
  Resolve contribution plus the expected Wishes Unnumbered particle trigger.
- `RaidenParty` still runs. The final sample changed from 1,389,957 total damage
  / 66,188 DPS to 1,362,938 total damage / 64,902 DPS because Raiden now consumes
  48 Resolve instead of the incorrect capped 60 Resolve in the benchmark script.

Test cases to add or update:

- Add at least one deterministic regression for every implemented fix.
- Normal path: the corrected mechanic triggers under expected rotation
  conditions and produces the expected state, damage, or energy delta.
- Error/invalid path: the same mechanic does not trigger for the wrong action
  type, wrong active character, missing buff, missing energy, cooldown lockout, or
  expired state.
- Boundary values: just-before and just-after timing around buff expiry, summon
  tick cadence, ICD windows, and burst/state end times.
- Unit-level logic: isolate core calculations such as Resolve contribution,
  snapshot stat capture, Raincutter wave counting, or Pyronado tick scheduling
  when possible.
- Integration path: run a compact RaidenParty script that exercises the changed
  mechanic together with reactions, buffs, energy, and logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

### Phase 3: FlinsParty2 Lunar Accuracy Audit - Done

Why third:

Lunar reaction infrastructure is implemented, but `FlinsParty2` depends on many
custom character hooks, Lunar-specific stats, reaction listeners, and item
effects. These should be audited after the conventional team is stable.

Target files:

- `src/java/sample/FlinsParty2.java`
- `src/java/model/character/Flins.java`
- `src/java/model/character/Ineffa.java`
- `src/java/model/character/Columbina.java`
- `src/java/model/character/Sucrose.java`
- relevant Lunar weapons under `src/java/model/weapon/`
- relevant Lunar artifacts under `src/java/model/artifact/`
- `src/java/mechanics/reaction/`
- `src/java/mechanics/formula/LunarDamageStrategy.java`
- `src/java/sample/ReactionRegressionTest.java`

Tasks:

- Inventory implemented, simplified, and missing Lunar character mechanics.
- Check Lunar-Charged Thundercloud ownership, tick cadence, crit rules, and
  conditional follow-up behavior.
- Check Lunar-Bloom Dew state creation, consumption hooks, and direct damage
  conversion hooks.
- Check Lunar-Crystallize Moondrift counters and Harmony cadence.
- Check Columbina Gravity accumulation, Interference triggers, Dew resources, and
  Lunar Domain conditions.
- Check Flins form state, Thundercloud-dependent hits, energy behavior, and
  constellation-triggered reaction hooks.
- Check Ineffa summon behavior, EM sharing, Lunar base-bonus support, and shielded
  skill assumptions.
- Check Lunar-aware weapons and artifacts for trigger conditions and stat routing.

Acceptance criteria:

- `FlinsParty2` has an explicit accuracy inventory.
- Lunar-specific high-impact gaps are listed as implementation tasks.
- Existing Lunar regressions still pass.

Implementation status:

- Audited `FlinsParty2`, Flins, Ineffa, Columbina, Sucrose, Lunar reaction
  routing, Lunar damage strategy, and the equipped Lunar weapons/artifacts.
- Confirmed implemented baseline coverage for auto-detected
  `ASCENDANT_GLEAM`, Lunar-Charged Thundercloud ownership/ticks/crit routing,
  Lunar-Bloom Dew conversion hooks, Lunar-Crystallize Moondrift/Harmony cadence,
  Columbina Gravity/Interference/Dew consumption, Ineffa Overclock/Birgitta
  hooks, and Flins Thunderous Symphony state.
- Found one high-impact timing issue: Flins standard burst delayed hits returned
  `currentTime + delay` from each timer poll, so their scheduled time could drift
  forward as combat time advanced.
- Remaining simplifications to track later: defensive shield HP is logged but
  not consumed by enemy attacks, some custom character effects use deterministic
  stand-ins for random or field-position behavior, and the sample can still fire
  Flins burst with insufficient energy in the scripted rotation while warning.

Test cases to add or update:

- Add tests only when the audit identifies missing coverage or ambiguous Lunar
  behavior.
- Normal path: Lunar-Charged, Lunar-Bloom, and Lunar-Crystallize trigger under
  valid Moonsign and party conditions with expected counters or state changes.
- Error/invalid path: Lunar conversion does not occur without the required
  party-state source, wrong aura pair, expired domain, missing Thundercloud, or
  non-Lunar trigger condition.
- Boundary values: Thundercloud refresh at expiry edge, Dew cooldown/cap edges,
  Moondrift every-third-trigger cadence, Gravity threshold crossing, and domain
  start/end timing.
- Unit-level logic: direct checks for Lunar damage stat routing, state counters,
  Dew/Gravity accumulation, and conversion eligibility helpers.
- Integration path: a representative shortened FlinsParty2 rotation that verifies
  Lunar reaction events, custom character hooks, item triggers, and report
  logging together.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`

### Phase 4: FlinsParty2 High-Impact Fixes - Done

Why fourth:

Lunar mechanics affect custom team behavior and RL training distribution. Fixes
should be isolated and measured against `FlinsParty2`.

Candidate tasks:

- Fix Thundercloud conditional hit logic if character hooks do not match runtime
  Lunar state.
- Fix Columbina Gravity or Dew behavior if counters, cooldowns, or domain
  conditions are incomplete.
- Fix Lunar item trigger conditions if they still depend on display labels rather
  than typed reaction metadata.
- Fix Lunar damage stat routing if a direct Lunar action or reaction uses the
  wrong bonus bucket.
- Add focused regression coverage for each fixed custom mechanic.

Acceptance criteria:

- Each implemented fix has deterministic regression coverage.
- `FlinsParty2` still runs and any DPS delta is explained in final notes.
- Standard reaction behavior remains unchanged unless intentionally corrected.

Implementation status:

- Fixed Flins standard burst delayed hit scheduling by capturing absolute target
  times when the timer events are created.
- Added a boundary regression that verifies the no-Thundercloud standard burst
  delayed hits occur at 2.5s, 2.8s, and 3.6s instead of drifting with current
  simulator time.
- Removed leftover `FlinsParty2` debug stat dumping from party setup so sample
  validation no longer writes `stats_dump.txt`.
- `FlinsParty2` still runs at 17,044,468 total damage / 246,664 DPS after the
  fix and debug-output cleanup.

Test cases to add or update:

- Add at least one deterministic regression for every implemented Lunar fix.
- Normal path: corrected Lunar hooks trigger with the expected owner, stat bucket,
  counter change, and damage or resource result.
- Error/invalid path: hooks do not trigger for standard reactions, wrong Lunar
  subtype, off-window domain state, missing Moonsign condition, or display-label
  mismatches.
- Boundary values: exact Thundercloud tick/expiry times, Dew caps, Gravity caps,
  Moondrift third-trigger boundary, and Lunar buff expiry on the hit frame.
- Unit-level logic: isolate conversion eligibility, Lunar stat selection, state
  increment/reset behavior, and reaction-listener filters.
- Integration path: a compact FlinsParty2 script that exercises the changed
  Lunar mechanic with reactions, character hooks, item hooks, damage attribution,
  and logging.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew FlinsParty2`

### Phase 5: RL Party and Capability Profile Audit - Done

Why fifth:

RL parties consume the same simulator but add observation, action-mask, reward,
role-profile, and report-generation contracts. Accuracy changes can silently
shift training behavior even when tensor shapes do not change.

Target files:

- `src/java/mechanics/rl/`
- `src/java/mechanics/rl/bridge/`
- `src/java/sample/ServeRLJava.java`
- `src/java/sample/BenchmarkRLJava.java`
- `src/java/sample/ProfileCharacterCapabilities.java`
- `src/python/rl/`
- `config/capability_profiles/`

Tasks:

- Verify registered RL parties match the audited simulator parties.
- Check capability profiles for stale role expectations after character or item
  fixes.
- Check action masks for newly adjusted cooldown, burst, or form-state behavior.
- Check observation and privileged-observation fields that expose reaction or
  role state.
- Check report generation paths for deterministic evaluation.
- Regenerate capability profiles only when role behavior changes materially.

Acceptance criteria:

- RL registry entries remain the single source of party selection.
- Capability profiles are either confirmed current or regenerated with a clear
  reason.
- Python evaluation still derives party names and summaries from service
  metadata.

Implementation status:

- Audited `RLPartyRegistry`, `RLPartySpec`, the FlinsParty2 and RaidenParty RL
  factories, multi-party episode selection, action masks, observation encoding,
  privileged-state encoding, Java benchmark entry point, and Python evaluation
  metadata usage.
- Confirmed registered RL parties still match the audited simulator parties:
  `FlinsParty2` and `RaidenParty`.
- No observation layout, privileged observation layout, action ID, action-mask,
  reward, or binary protocol change was required.
- Regenerated `config/capability_profiles/profiles.json` because Phase 2 and
  Phase 4 changed simulator behavior used by role profiling. Flins role values
  changed materially after fixed burst scheduling, and Raiden values changed
  slightly after Resolve crediting was corrected.
- Confirmed Java rollout benchmark succeeds with the regenerated profile file.
  Python checkpoint evaluation was not run because no usable checkpoint was
  required or validated as part of this pass.

Test cases to add or update:

- Add or update tests only when RL-facing behavior or metadata changes.
- Normal path: registered parties can be selected by name, default, and all; the
  service exposes consistent observation/action/role metadata; valid actions step
  successfully.
- Error/invalid path: unknown party selections, invalid action IDs, masked
  actions, unavailable checkpoints, and service/client shape mismatches fail
  clearly.
- Boundary values: one-party versus multi-party catalogs, one environment versus
  vectorized environments, episode termination at the configured time limit, and
  action-mask behavior around cooldown or energy thresholds.
- Unit-level logic: action mask generation, reward calculation, role-alignment
  scoring, capability profile parsing, and protocol encode/decode helpers.
- Integration path: Java rollout benchmark plus Python evaluation against a
  running service when a usable checkpoint exists.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew BenchmarkRLJava`
- `./gradlew ProfileCapabilities` when role profiles change
- `python3 src/python/rl/evaluate_policy.py --mode both --summary` when a usable
  checkpoint exists

### Phase 6: Documentation and Accuracy Notes - Done

Why last:

After the benchmark parties are audited and fixed, the project should clearly
state which mechanics are exact, simplified, or intentionally deferred.

Target files:

- `README.md`
- `TASKS.md`
- relevant package `AGENTS.md`
- optional docs or generated reports only when explicitly requested

Tasks:

- Update this plan with completed items and remaining gaps.
- Document known simplifications for RaidenParty and FlinsParty2.
- Keep validation commands and expected sample outputs current.
- Avoid editing generated `docs/` unless the task is specifically about
  published documentation.

Acceptance criteria:

- Future implementation work can start from a current gap list instead of
  re-auditing the whole project.
- Handoff notes clearly state which commands were run and which were skipped.

Implementation status:

- Updated this plan with completed phase notes, known simplifications, validation
  commands, and sample DPS baselines.
- Updated `README.md` with current accuracy notes for the audited benchmark
  parties and the latest validation baseline.
- Did not edit generated `docs/` output.

Test cases to add or update:

- Documentation-only changes do not require new tests unless they document a new
  expected behavior that is not covered.
- When docs record a newly fixed mechanic, make sure the corresponding phase
  added or updated normal, invalid, boundary, unit-level, or integration coverage
  as appropriate.
- If validation commands, report paths, or RL workflows are changed, run the
  smallest command that proves the documented workflow still works.

## Next Implementation Order: HTML Report Detail and UI Upgrade

Status:

- Implemented through Report Phase 7 after explicit user approval.
- Generated reports now include the requested charts, denser dashboard layout,
  timeline filters, formula detail panels, and renderer escaping coverage.

Scope:

- Improve the generated HTML simulation report under `output/`.
- Add richer combat-analysis charts:
  - character cumulative damage
  - reaction damage pie chart
  - action damage horizontal bar chart
  - rolling DPS over time
  - aura timeline
  - energy timeline
  - buff uptime chart
- Improve report readability, visual hierarchy, table density, timeline scanning,
  tooltip/detail readability, and responsive layout.
- Keep the simulator and model layers downstream-clean: report presentation must
  not become runtime control flow.

Out of scope for this pass:

- Changing combat mechanics, damage formulas, or optimizer behavior.
- Rewriting the report as a separate frontend build pipeline.
- Adding external network dependencies beyond the existing CDN-loaded Chart.js
  unless explicitly approved.
- Editing generated `docs/` or committed report output unless explicitly
  requested.
- Persisting report data as a separate JSON artifact unless explicitly requested.
- Introducing a frontend framework or build step.
- Adding server-side report viewing.
- Treating report chart output as simulator correctness assertions.

Chart data source plan:

| Chart | Existing data coverage | Additional implementation |
| --- | --- | --- |
| character cumulative damage | Existing `SimulationRecord` actor/damage data is enough | Small aggregation cleanup |
| reaction-labeled damage pie | Existing reaction labels and `reactionDamage` are partly enough | Medium, because additive/direct reaction labels need clear semantics |
| action damage horizontal bar | Existing action labels and damage data are enough | Small aggregation cleanup plus label truncation |
| rolling DPS | Existing time/damage records are enough | Small aggregation with fixed sampling rules |
| aura timeline | Existing per-record enemy aura snapshots are enough when records are dense enough | Medium, because sparse records need step-style rendering semantics |
| energy timeline | Current stat snapshots do not expose current energy | Medium, likely requires report-facing `StatsSnapshot` extension |
| buff uptime chart | Current stat snapshots expose active buff labels | Medium to large, because uptime must integrate sampled intervals and handle duplicate labels |

Reaction chart semantics:

- Transformative reaction damage uses explicit `reactionDamage` values.
- Additive or direct reaction-labeled hits, such as Aggravate and Spread, must
  not be mixed into a chart called "Reaction Damage" unless the additive bonus is
  separately available.
- If true additive bonus damage is not separately available, expose those values
  under a clearly named "Reaction-labeled Damage" chart or table and document the
  limitation in the report.
- The chart title and tooltip must make the chosen definition explicit.

Rolling DPS definition:

- Compute rolling DPS every 0.5 seconds.
- Use damage in the previous 5.0 second window.
- For timestamps earlier than 5.0 seconds, divide by the actual elapsed window
  length instead of the full 5.0 seconds.
- Include points even in quiet intervals so the chart can show DPS falling when
  no damage occurs.
- Do not extend beyond the effective rotation duration.

Large-report performance rules:

- Downsample dense time-series for chart rendering when record count exceeds a
  configurable threshold.
- Preserve full timeline data for filtering when practical, but cap initial
  visible rows or render rows lazily if the generated report becomes heavy.
- Keep action and buff charts to a readable Top N, with the limit documented in
  chart labels or captions.
- Track generated HTML size and browser responsiveness during `FlinsParty2`
  validation.

Color rules:

- The same character should use the same color across all character-based
  charts.
- Aura timeline series should use element colors, even if those colors differ
  from character chart colors.
- Reaction categories should use stable colors when feasible; otherwise they
  should use a neutral palette distinct from element aura colors.
- The report remains a dark, analysis-focused dashboard unless a separate theme
  request is made.

### Report Phase 1: Report Data Inventory and Contract Design - Done

Why first:

The requested charts need both existing event records and additional sampled
state. Before changing rendering, define which values come from existing
`SimulationRecord` data and which require report-only snapshot extensions.

Target files:

- `src/java/visualization/SimulationRecord.java`
- `src/java/visualization/VisualLogger.java`
- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportViewAdapter.java`
- `src/java/mechanics/analysis/StatsSnapshot.java`
- `src/java/mechanics/analysis/StatsRecorder.java`

Tasks:

- Inventory existing report data:
  - per-event time, actor, action, direct damage, reaction label, reaction
    damage, enemy aura snapshot, and formula text
  - stat snapshots and active buff labels from `StatsRecorder`
  - party member names, artifact rolls, and chart colors
- Define report-only aggregate data structures for:
  - action damage totals
  - reaction damage totals
  - rolling DPS series
  - aura timeline series
  - energy timeline series
  - buff uptime summaries
- Decide whether energy timeline can be added through `StatsSnapshot` without
  breaking existing consumers.
- Keep data additions backward-compatible where practical.

Acceptance criteria:

- Each requested chart has a clear data source.
- Existing-data versus additional-measurement requirements are listed for each
  chart before implementation starts.
- Any new field added to a shared data carrier is documented as report-facing.
- No simulator control path depends on display labels added for reporting.

Test cases to add or update:

- Unit-level or lightweight executable checks only if new aggregation helpers are
  non-trivial.
- Normal path: a small record list produces expected action/reaction totals and
  cumulative/rolling series.
- Boundary values: empty records, zero-damage records, missing stats history, and
  final timestamp with no further snapshot.

Verification:

- `./gradlew ReactionRegressionTest`

Implementation status:

- Done. Data sources were mapped to existing `SimulationRecord` fields and
  stat snapshot history. Energy percentage was added as a report-facing
  `StatsSnapshot` value.

### Report Phase 2: Aggregate Chart Data Implementation - Done

Why second:

Once contracts are defined, implement chart inputs before touching the UI. This
keeps rendering changes small and testable.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportViewAdapter.java`
- `src/java/mechanics/analysis/StatsSnapshot.java`
- `src/java/mechanics/analysis/StatsRecorder.java`

Tasks:

- Build character cumulative damage series from existing records.
- Build transformative reaction damage totals from explicit `reactionDamage`
  values.
- Build additive/direct reaction-labeled summaries separately unless true
  additive bonus damage is made available as a distinct value.
- Build top action damage totals from action labels.
- Build rolling DPS using the documented 0.5 second sample interval and 5.0
  second lookback window.
- Build aura timeline series from each record's enemy aura snapshot.
- Extend stat snapshots for current energy percentage if needed, then build
  energy timeline series per character.
- Build buff uptime by integrating active buff labels over snapshot intervals.
- Limit long action/buff lists to a readable Top N and keep full data available
  only if the renderer can display it cleanly.
- Add chart downsampling for dense time-series before rendering if generated
  data arrays become too large.

Acceptance criteria:

- All requested chart datasets are present in `ReportData`.
- Missing optional history data disables only dependent charts, not the whole
  report.
- Existing pie and cumulative damage charts still render.

Test cases to add or update:

- Normal path: deterministic aggregate values from a small synthetic record list.
- Error/invalid path: null or empty records/stats history produce empty datasets
  without exceptions.
- Boundary values: one record, repeated timestamps, zero rotation duration, and
  buff active at the final snapshot.
- Renderer-facing data labels containing quotes, angle brackets, ampersands, and
  script-like substrings must be covered before being embedded in HTML/JS.

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`

Implementation status:

- Done. `ReportDataBuilder` now prepares action totals, strict transformative
  reaction totals, separate reaction-labeled direct damage totals, rolling DPS,
  aura series, energy series, and buff uptime summaries.
- Dense chart series are capped with a 600-point downsampling limit. Long action,
  reaction, and buff summaries are capped to Top 16.

### Report Phase 3: Renderer Escaping and HTML Skeleton Tests - Done

Why third:

The report embeds action labels, buff labels, formula text, and chart data into a
self-contained HTML/JavaScript document. Escaping must be reliable before adding
more charts and controls.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/sample/ReactionRegressionTest.java` or a similarly lightweight
  executable regression harness

Tasks:

- Add renderer helper methods for HTML text, HTML attributes, and JavaScript
  string/data escaping where needed.
- Add a lightweight regression that renders labels containing:
  - quotes
  - angle brackets
  - ampersands
  - newlines
  - script-like substrings
- Verify generated HTML contains expected chart containers and does not break the
  `<script>` block.
- Verify empty records and missing stats history still render a complete HTML
  document.

Acceptance criteria:

- User-facing labels cannot break HTML structure or JavaScript arrays.
- Renderer tests cover action labels, buff labels, and formula text.
- Empty reports render without exceptions.

Test cases to add or update:

- Normal path: a minimal report renders with expected section IDs and chart
  container IDs.
- Error/invalid path: null/empty data renders empty states instead of throwing.
- Boundary values: labels with HTML/JS-sensitive characters remain escaped.

Verification:

- `./gradlew ReactionRegressionTest`

Implementation status:

- Done. `sample.ReportRegressionTest` covers special-character labels,
  script-like substrings, expected chart containers, and empty-report rendering.

### Report Phase 4: Chart Rendering - Done

Why fourth:

After datasets exist, add the requested charts in the HTML renderer without
changing simulation behavior.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/visualization/ElementColorPalette.java` if additional stable colors
  are needed

Tasks:

- Render character cumulative damage as the primary time-series chart.
- Add reaction damage pie chart.
- Add action damage horizontal bar chart.
- Add rolling DPS time-series chart.
- Add aura timeline chart with one series per active element.
- Add energy timeline chart with one series per party member.
- Add buff uptime horizontal bar chart.
- Use stable colors for characters and element colors for aura series.
- Keep chart labels short enough to avoid overlap; truncate or wrap long action
  and buff labels if needed.

Acceptance criteria:

- The generated report contains every requested chart.
- Charts render when datasets are present and show an empty-state message or
  omit gracefully when optional data is missing.
- Existing stats table, artifact table, and timeline still render.

Test cases to add or update:

- No Java unit test is required for Chart.js rendering unless a renderer helper
  becomes complex.
- Integration path: generate a report from `RaidenParty` and `FlinsParty2` and
  verify that the HTML includes the expected chart containers and data arrays.

Verification:

- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

Implementation status:

- Done. The report renders character cumulative damage, transformative reaction
  damage, reaction-labeled direct damage where present, action damage, rolling
  DPS, aura timeline, energy timeline, and buff uptime charts.

### Report Phase 5: UI Readability and Layout Upgrade - Done

Why fifth:

The report will contain more information after the new charts are added. The
layout needs to become easier to scan before adding more timeline interactions.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`

Tasks:

- Convert the top summary into compact KPI cards:
  - DPS
  - total damage
  - duration
  - top damage dealer
  - top reaction
  - max hit
- Rework the layout into dashboard-style sections with clearer visual hierarchy.
- Improve table readability:
  - numeric columns right-aligned
  - sticky headers where useful
  - denser spacing without cramping labels
- Improve timeline readability:
  - tighter rows
  - stable columns for time, actor, action, damage
  - clearer reaction badges
  - formula detail shown in an expandable or otherwise readable panel rather
    than only a single-line tooltip where feasible
- Improve responsive behavior for narrower screens without optimizing primarily
  for mobile.
- Avoid decorative gradients/orbs and keep the UI quiet and analysis-focused.

Acceptance criteria:

- The first viewport shows high-level result and chart context clearly.
- Long labels do not overflow incoherently.
- Timeline rows remain scannable in long reports.
- Existing report generation remains a single self-contained HTML file.

Test cases to add or update:

- Integration path: generate at least one report and visually inspect desktop
  layout.
- If browser automation is available, capture screenshots for desktop and narrow
  viewport and check for JavaScript console errors.
- Manual inspection checklist:
  - charts render without JavaScript console errors
  - top KPI cards match known sample totals
  - long formulas are readable
  - tables and chart containers do not overflow at a narrow viewport
  - timeline rows remain scannable after adding chart sections

Verification:

- `./gradlew FlinsParty2`
- Manual browser inspection of `output/simulation_report.html`

Implementation status:

- Done. The report uses KPI cards, dashboard-style sections, denser tables,
  responsive chart grids, clearer timeline rows, and expandable formula details.
- Manual browser inspection is still recommended before publishing generated
  report output.

### Report Phase 6: Timeline Filters and Detail Panels - Done

Why sixth:

Once chart and layout density increase, timeline navigation becomes the next
largest usability bottleneck.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`

Tasks:

- Add client-side filters:
  - actor
  - reaction
  - damage-only
  - minimum damage
  - text search over action labels
- Add event count summary for filtered results.
- Ensure formula detail panels remain readable after filters are applied.
- Preserve aura bar and reaction labels on each event.
- Keep all filtering client-side inside the generated HTML.

Acceptance criteria:

- Users can quickly find high-impact events, reaction events, and specific
  actions.
- Filters do not require regenerating the report.
- Timeline remains usable when filters match no rows.

Test cases to add or update:

- Integration path: generated HTML includes filter controls and timeline records
  carry data attributes needed by the filters.
- Manual browser check that each filter changes visible rows correctly.
- Manual inspection checklist:
  - actor filter works
  - reaction filter works
  - damage-only filter works
  - minimum damage filter works
  - text search works
  - no-match state is clear

Verification:

- `./gradlew RaidenParty`
- Manual browser inspection of `output/simulation_report.html`

Implementation status:

- Done. Timeline rows expose filter data attributes and the report includes
  actor, reaction, damage-only, minimum-damage, and text-search filters plus a
  filtered event count.

### Report Phase 7: Report Validation and Documentation - Done

Why last:

The report is user-facing HTML. Final validation should cover sample generation,
visual inspection, and documentation of the new report features.

Target files:

- `README.md`
- `TASKS.md`
- `src/java/visualization/AGENTS.md` if report ownership guidance changes

Tasks:

- Update README report feature list to mention the new charts and filters.
- Record final implementation status and any deferred report features in this
  file.
- Run representative samples and inspect generated reports.
- Do not commit generated report output unless explicitly requested.
- Record any chart downsampling threshold, Top N limits, and known semantic
  limitations such as additive reaction bonus separation.

Acceptance criteria:

- Future work can distinguish implemented report features from deferred UI
  polish.
- Handoff notes list exact commands run and any manual browser checks.

Test cases to add or update:

- Documentation-only changes require no new tests.
- If documented commands change, run the smallest relevant command.
- Manual inspection checklist:
  - charts render without JavaScript console errors
  - KPI cards match sample console totals
  - timeline filtering works for actor, reaction, damage-only, minimum damage,
    and text search
  - long formulas are readable
  - narrow viewport does not break tables or chart containers

Verification:

- `./gradlew ReactionRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

Implementation status:

- Done. README and visualization ownership guidance have been updated for the
  richer report. Generated report artifacts remain uncommitted output.

Known limitations and deferred work:

- Additive reaction bonus damage is not separated from full hit damage, so those
  entries are shown as "Reaction-labeled Direct Damage" rather than strict
  "Reaction Damage".
- Browser-based manual inspection remains the final publication check for
  console errors, filter interaction, and narrow viewport behavior.
- The report is still a self-contained HTML file with CDN-loaded Chart.js; no
  frontend framework, server view, or separate JSON artifact has been added.

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
