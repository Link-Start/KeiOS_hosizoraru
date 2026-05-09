# KeiOS Compose Audit TODO - 2026-05-09

Source report: `artifacts/compose-audit-20260509/COMPOSE-AUDIT-REPORT.md`

## Audit Baseline

- Overall score: `73/100`
- Performance: `7/10`
- State management: `8/10`
- Side effects: `7/10`
- Composable API quality: `7/10`
- Strong Skipping: enabled
- Module-wide skippable: `1575/2174 = 72.45%`
- Named-only skippable: `608/608 = 100.00%`
- Known unstable arguments: `1427`
- Inferred unstable classes: `365`

## P0 - Completed Highest-Priority Fixes

- [x] Move Release Notes Markdown parsing out of composition-thread work.
    - Done in `AppMarkdownContent`.
    - `AppMarkdownContent` now parses blocks with `produceState` on `Dispatchers.Default`.
    - Rendering is split into `AppMarkdownBlocksContent`.

- [x] Precompute student BGM displayed IDs and summary counts.
    - Done in `BaGuideStudentBgmTabContent`.
    - Favorite lookup now uses normalized source URL and audio URL maps.
    - Displayed IDs, playable queue, resolved count, and loading count are grouped in
      `BaGuideStudentBgmDisplayedModel`.
    - `LaunchedEffect` now uses stable displayed content IDs.

- [x] Add stable Lazy list keys and content types for audited BA and MCP lists.
    - Done in `BaPoolActivity`.
    - Done in `BaActivityCalendarActivity`.
    - Done in `McpSkillContentList`.

- [x] Move fullscreen gallery rotation to the layer phase.
    - Done in `GuideGalleryFullscreen`.
    - `Modifier.rotate(rotationTransition.value)` was replaced with
      `graphicsLayer { rotationZ = rotationTransition.value }`.

- [x] Make shared copy-mode Flow lifecycle-aware.
    - Done in `TextCopySupport`.
    - `collectAsState` was replaced with `collectAsStateWithLifecycle`.

## P1 - Recommended Next Optimization Options

- [ ] Rerun the Compose audit and compare metrics after the P0 fixes.
    - Priority: highest.
    - Goal: confirm the 5 source-level findings disappeared and capture the new baseline.
    - Suggested gate: `:app:compileReleaseKotlin` with the audit init script, plus a short
      release-notes sheet render check.

- [x] Add Release Notes parsed-block caching by source key.
    - Priority: high.
    - Goal: avoid reparsing the same long release notes after sheet recreation or repeated
      open/close.
    - Candidate owner: GitHub detail sheet state or a small Markdown cache helper.

- [x] Hoist BGM displayed stats into a stable UI model closer to the coordinator or page state.
    - Priority: high.
    - Goal: keep the composable as a renderer and make queue/count derivation easier to test.
    - Candidate output: immutable displayed model with content IDs, resolved count, loading count,
      and playable favorites.

- [ ] Triage compiler-reported unstable shared types.
    - Priority: high.
    - Goal: reduce the `365` inferred unstable classes and `1427` known unstable arguments that
      still cap broad recomposition quality.
    - Start with high-traffic UI-state models and list item models used by Home, GitHub, BA, OS, and
      shared chrome.

- [x] Sweep remaining production Lazy lists for missing `key` and `contentType`.
    - Priority: medium.
    - Goal: extend the same list identity hygiene beyond the three audited call sites.
    - Suggested scan: production `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, and `items(size)`
      usages under `app/src/main`.

- [x] Replace boxed primitive route event tokens with typed primitive state.
    - Priority: medium.
    - Goal: clean up report evidence around `mutableStateOf(0)` event tokens.
    - Candidate owner: `MainActivity` event-token fields.

- [ ] Split dense Compose route files only where the split reduces recomposition ownership.
    - Priority: medium.
    - Goal: keep page orchestration, derived UI models, and card rendering separated.
    - Candidate surfaces: large GitHub, BA, OS, and settings route files that still mix state
      derivation with rendering.

## P2 - Measurement And Long-Term Quality

- [ ] Capture runtime evidence for the optimized flows.
    - Priority: medium.
    - Scope: GitHub Release Notes sheet, BGM catalog scroll, fullscreen gallery rotation.
    - Suggested evidence: emulator screenshots plus JankStats, Perfetto, or `gfxinfo` where
      practical.

- [ ] Add a small regression checklist for Compose performance passes.
    - Priority: low.
    - Include: release-notes open, BGM list scroll/play queue, BA pool/calendar filtering, MCP skill
      scroll, fullscreen image rotation, copy-mode toggle.

- [ ] Keep the Compose audit TODO updated after each audit run.
    - Priority: low.
    - Add the new score, date, changed findings, and completed checkboxes.
