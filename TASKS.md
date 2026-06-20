# Accuracy Implementation Plan

## Current Status

The simulator accuracy audit and high-impact fixes for `RaidenParty`,
`FlinsParty2`, and RL-facing metadata are complete.

The previous HTML report detail and UI upgrade is complete. The character detail
tab UI using the downloaded `face.png` assets is implemented through the final
planned phase.

## Scope

The reaction core, aura/ICD detail passes, Bloom-family behavior, Quicken-family
behavior, Lunar reaction handling, and current regression coverage are treated as
the baseline implementation.

The next work should improve generated HTML report readability by grouping
character-specific details into face-icon tabs. The goal is to make damage,
action, energy, buff, artifact, and event details easier to inspect per
character while preserving the existing simulator behavior and global report
sections.

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

## Implementation Order: Character Detail Tabs with Face Icons

Status:

- Done.
- This replaces the previous completed phase list as the next report-focused work.
- The generated HTML report is easier to scan by grouping per-character details behind face-icon tabs.

Scope:

- Improve the generated HTML report under `output/`.
- Use `config/characters/<CharacterName>/face.png` assets for character-level navigation and section identity.
- Introduce a character detail section with face-icon tabs.
- Move or mirror character-specific report content into each character tab:
  - damage summary
  - action damage breakdown
  - energy timeline
  - buff uptime relevant to that character
  - artifact substat rolls
  - representative event rows for that character
- Preserve existing global charts and timeline filters unless a later phase explicitly replaces them.

Out of scope for this pass:

- Changing simulator mechanics, damage formulas, reactions, optimizer behavior, or RL behavior.
- Introducing a frontend framework, bundler, or server-side report viewer.
- Persisting report data as a separate JSON artifact unless explicitly requested.
- Downloading new images or changing the existing `face.png` asset set.
- Making face icons required for report generation. Missing icons must fall back gracefully.
- Reworking every chart into a character-specific version in the first implementation.

Design direction:

- Add a `Character Details` section after the top KPI/global chart area.
- Render tabs as compact buttons with face icon, display name, damage total, and damage share.
- The active tab shows a dense per-character panel rather than forcing users to scroll through every character section.
- Keep the UI analysis-focused: dark dashboard style, stable spacing, readable tables, and no decorative image treatment.
- Use the same character color across charts, badges, and tab accents.
- Face icons should help recognition, not dominate the report.

Data and path rules:

- Character identity should come from typed report data where possible, preferably `CharacterId` or canonical display name.
- Face path resolution should be centralized in a report helper, not duplicated across rendering snippets.
- Expected path format: `config/characters/<CharacterName>/face.png`.
- Character names containing spaces or special characters must be HTML-attribute escaped and URL/path safe for generated HTML references.
- Missing or unreadable face icons should render a deterministic initials/avatar fallback and should not break report generation.

### Phase 1: Data Inventory and Character View Contract - Done

Why first:

The report already has global datasets, but the tab UI needs a clean per-character view model. Define exactly which existing values can be reused before editing the renderer heavily.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportViewAdapter.java`
- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/model/type/CharacterId.java` if display/id mapping is needed

Tasks:

- Inventory current report data for character-level content:
  - total damage and DPS contribution
  - cumulative damage series
  - action damage totals
  - energy timeline series
  - buff uptime summaries
  - artifact roll data
  - timeline events by actor
- Define a per-character report view contract, for example `CharacterReportView` or equivalent nested data.
- Decide whether buff uptime should show buffs owned by the character, buffs affecting the character, or both if the data supports it.
- Decide which event rows appear inside a tab initially, such as top damage events and recent actor-matched events.
- Keep global chart data intact so existing report behavior remains available.

Acceptance criteria:

- Each planned tab field has a known source or an explicit limitation.
- Missing optional data only hides that widget inside the tab, not the entire report.
- The contract avoids using display labels as internal control-flow keys.

Test cases to add or update:

- Normal path: a report with four party members builds four character detail entries.
- Error path: empty records or missing stats history still build an empty-but-renderable detail list.
- Boundary values: zero-damage characters, characters with no action damage, and missing face icons.

Verification:

- `./gradlew classes`
- `./gradlew ReactionRegressionTest` if report data builder behavior changes non-trivially

Implementation status:

- Added a `ReportData.CharacterReportView` contract for the planned face-icon tab UI.
- The builder now creates one character detail entry per party member from existing report data.
- Each entry currently includes damage total, DPS contribution, damage share, max hit, top action, per-character action totals, energy series, sampled buff uptime rows, artifact rolls, top damage events, and recent actor events.
- Buff uptime remains based on the existing sampled active-buff labels; owner-versus-affected-character semantics are not expanded in this phase.

### Phase 2: Face Icon Resolution and Renderer Safety - Done

Why second:

Image paths and character labels are user-visible HTML inputs. Escaping and fallback behavior should be reliable before adding the visible tab UI.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/sample/ReportRegressionTest.java`

Tasks:

- Add a centralized helper for resolving face image paths from character identity.
- Add an initials/avatar fallback for missing `face.png`.
- Ensure image paths, alt text, tab labels, and data attributes are escaped correctly.
- Add renderer regression coverage for names and labels containing quotes, angle brackets, ampersands, and script-like substrings.
- Keep generated HTML self-contained except for local image references and existing Chart.js CDN usage.

Acceptance criteria:

- Reports render when all icons exist.
- Reports render when one or more icons are missing.
- Labels and paths cannot break HTML attributes or JavaScript blocks.

Test cases to add or update:

- Normal path: known character face path appears in generated HTML.
- Error path: missing icon uses fallback markup.
- Boundary values: special-character labels remain escaped in tab labels, alt text, and data attributes.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew classes`

Implementation status:

- Added centralized face image path resolution to `ReportViewAdapter.ReportCharacterView`.
- Report-facing image paths are generated relative to HTML files under `output/`, while existence checks use repository-local `config/characters/<CharacterName>/face.png`.
- Added deterministic fallback text for characters without a local `face.png`.
- Added hidden character asset metadata and JavaScript data in the renderer so Phase 3 can build the tab UI without duplicating path logic.
- Extended `ReportRegressionTest` to cover known icon paths, missing-icon fallback markup, HTML attribute escaping, JavaScript string escaping, and URL-encoded face paths.

### Phase 3: Character Tab Shell UI - Done

Why third:

Build the interaction shell before moving detailed widgets into it. This keeps layout and JavaScript behavior isolated.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`

Tasks:

- Add a `Character Details` section with face-icon tab buttons.
- Render one panel per character and show only the active panel.
- Include tab metadata:
  - face icon or fallback
  - character display name
  - total damage
  - damage share
- Add lightweight client-side tab switching without external dependencies.
- Ensure keyboard and accessibility basics:
  - buttons are real `<button>` elements
  - selected state is represented with `aria-selected` or equivalent
  - panels are hidden without removing their content from the document
- Keep layout stable at desktop and narrow widths.

Acceptance criteria:

- The generated report has one tab per party member.
- Clicking a tab changes the visible character panel.
- The first tab is selected by default.
- Existing global charts and timeline still render.

Test cases to add or update:

- Renderer skeleton test checks tab container, tab buttons, and panels exist.
- Empty/partial character data renders a clear empty state.
- Manual check that tab switching works in the browser.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew RaidenParty`
- Manual browser inspection of `output/simulation_report.html`

Implementation status:

- Added a `Character Details` section after the global charts.
- Rendered one face-icon tab and one detail panel per party member.
- The first character tab is selected by default; inactive panels use the `hidden` attribute.
- Added click and left/right arrow-key tab switching with synchronized `aria-selected`, `tabindex`, and panel visibility.
- The shell currently shows character identity and summary metrics only; detailed action, artifact, energy, buff, and event widgets are deferred to later phases.
- Added renderer regression checks for tablist, tab buttons, panels, default selected state, hidden inactive panel, and tab switching script.

### Phase 4: Per-Character Damage and Artifact Widgets - Done

Why fourth:

Damage and artifact information are the most immediately useful tab contents and are mostly available from existing report data.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportHtmlRenderer.java`

Tasks:

- Add a compact damage summary inside each tab:
  - total damage
  - DPS contribution
  - damage share
  - top action
  - max hit if available
- Move or mirror action damage into a per-character horizontal bar/table.
- Show artifact substat rolls inside the character tab with readable alignment.
- Fix numeric alignment so artifact substat values do not visually drift to the far right in cramped layouts.
- Preserve the existing global action damage chart unless explicitly removed later.

Acceptance criteria:

- Action damage can be viewed per character from the tab UI.
- Artifact rolls are visually tied to the selected character.
- Long action labels and stat labels do not overflow incoherently.

Test cases to add or update:

- Normal path: per-character action totals match the global actor/action source data.
- Error path: character with no action damage shows an empty state.
- Boundary values: long action labels, zero values, and many artifact stats.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`

Implementation status:

- Added per-character summary metrics for top action in each character panel.
- Added a per-character action damage widget with compact horizontal bars and fixed numeric columns.
- Added a per-character artifact substat rolls table scoped to the selected character.
- Added empty states for characters without action damage or artifact roll data.
- Tightened artifact roll table layout so roll counts stay near their stat labels instead of drifting to the far right.
- Extended renderer regression coverage for per-character action and artifact widgets.

### Phase 5: Per-Character Energy, Buff, and Event Detail - Done

Why fifth:

Energy timelines, buff uptime, and representative events give each tab diagnostic depth, but they depend on correctly interpreting existing sampled/report data.

Target files:

- `src/java/visualization/ReportData.java`
- `src/java/visualization/ReportDataBuilder.java`
- `src/java/visualization/ReportHtmlRenderer.java`

Tasks:

- Add a per-character energy timeline view or compact sparkline if the current chart infrastructure supports it cleanly.
- Add a buff uptime list/table filtered to relevant character data.
- Clearly label the current buff uptime semantics:
  - sampled active labels
  - owner-only, affected-character, or global visibility depending on available data
- Add representative event rows for the selected character:
  - highest damage events
  - reaction-labeled events caused by the character
  - recent actor-matched timeline entries if useful
- Avoid duplicating the full global timeline inside every tab.

Acceptance criteria:

- Energy and buff information is understandable from inside the selected character tab.
- Buff uptime labels do not imply precision beyond the available sampled data.
- Event details help explain the selected character's contribution without making the page excessively long.

Test cases to add or update:

- Normal path: energy series is filtered to the selected character.
- Error path: missing energy or buff history hides only that widget.
- Boundary values: buffs active at final snapshot, duplicate buff labels, and character with no matching events.

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew FlinsParty2`
- Manual browser inspection of `output/simulation_report.html`

Implementation status:

- Added a per-character energy widget to each tab using sampled stat-history energy percent.
- Added a per-character buff uptime widget with an explicit note that values are sampled active-buff labels.
- Added top damage event and recent actor-matched event widgets for each character.
- Kept event rows capped to representative data instead of duplicating the full global timeline in every tab.
- Added empty states for missing energy samples, sampled buffs, damage events, and actor-matched timeline rows.
- Extended renderer regression coverage for energy, buff, and event widgets, including escaped buff labels.

### Phase 6: Layout Polish, Responsiveness, and Validation - Done

Why last:

The tab UI combines images, charts, tables, and event rows. Final validation should focus on readability, layout stability, and regressions in existing report features.

Target files:

- `src/java/visualization/ReportHtmlRenderer.java`
- `README.md` if report feature documentation is updated
- `TASKS.md`

Tasks:

- Tune tab sizing, image dimensions, table density, and chart heights.
- Ensure text does not overlap icons, chart containers, or table cells.
- Keep chart containers bounded so the report does not grow vertically because of chart canvas feedback loops.
- Verify narrow viewport behavior:
  - tabs wrap or scroll cleanly
  - panels remain readable
  - artifact/action tables do not break layout
- Update documentation only after implementation is approved and complete.
- Record any known limitations or deferred improvements.

Acceptance criteria:

- Character detail tabs are visually useful with `face.png` assets.
- Existing global report charts, filters, and formula details still work.
- The report remains a single generated HTML file.
- Manual browser inspection finds no obvious vertical stretching, overlap, or unusable narrow layout.

Test cases to add or update:

- Renderer regression for expected tab markup and fallback icon behavior.
- Integration report generation for `RaidenParty` and `FlinsParty2`.
- Manual inspection checklist:
  - face-icon tabs render for every party member
  - tab switching works
  - per-character action damage is readable
  - artifact rolls are aligned and tied to the selected character
  - energy and buff widgets show useful empty states when data is missing
  - existing timeline filters still work
  - browser console has no JavaScript errors
  - chart canvases do not stretch the page vertically

Verification:

- `./gradlew ReportRegressionTest`
- `./gradlew RaidenParty`
- `./gradlew FlinsParty2`
- Manual browser inspection of `output/simulation_report.html`

Implementation status:

- Kept global chart canvases bounded with fixed report CSS heights so Chart.js does not stretch the page vertically.
- Added responsive rules for the expanded character-tab widget grid and energy summary.
- Added fixed-layout compact tables for per-character artifact rolls and event rows to avoid label/value drift.
- Preserved the existing single-file HTML report behavior with local `face.png` references and existing Chart.js CDN usage.
- Known limitation: browser manual inspection is still required for final visual acceptance after generated sample reports are opened locally.

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
