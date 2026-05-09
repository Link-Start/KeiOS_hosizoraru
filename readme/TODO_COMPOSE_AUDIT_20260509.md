# KeiOS Compose Audit TODO - 2026-05-09

Source report: `artifacts/compose-audit-20260509/COMPOSE-AUDIT-REPORT.md`

## Active Batch

- Status: idle after `2026-05-10C - value snapshot stability trim`.
- Started from latest stored audit before code batch: `totalComposables=2501`,
  `skippableComposables=1578`,
  `knownUnstableArguments=1372`, `inferredUnstableClasses=310`,
  `markedStableClasses=106`, `effectivelyStableClasses=481`.
- Finished with QA gate artifact:
  `artifacts/compose-audit-20260510-batch-stability-trim/`.
- End metrics: `totalComposables=2501`, `skippableComposables=1578`,
  `knownUnstableArguments=1370`, `inferredUnstableClasses=300`,
  `markedStableClasses=126`, `effectivelyStableClasses=493`.
- Verification command:
  `COMPOSE_AUDIT_STAMP=20260510-batch-stability-trim scripts/qa/compose_audit_gate.sh`
- Runtime evidence artifact:
  `artifacts/compose-runtime-20260510/RUNTIME-EVIDENCE.md`

## Metrics

### Original Audit Baseline

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

### Latest Audit Snapshot

- Date: 2026-05-10
- Command:
  `COMPOSE_AUDIT_STAMP=20260510-batch-stability-trim scripts/qa/compose_audit_gate.sh`
- Full reports:
  `artifacts/compose-audit-20260510-batch-stability-trim/app-classes.txt`,
  `artifacts/compose-audit-20260510-batch-stability-trim/app-composables.csv`,
  `artifacts/compose-audit-20260510-batch-stability-trim/app-composables.txt`,
  `artifacts/compose-audit-20260510-batch-stability-trim/app-module.json`
- JVM budget: project defaults use `org.gradle.jvmargs=-Xmx4096m` and
  `kotlin.daemon.jvmargs=-Xmx4096m`.
- Total composables: `2501`
- Skippable composables: `1578`
- Known unstable arguments: `1370`
- Inferred unstable classes: `300`
- Marked stable classes: `126`
- Effectively stable classes: `493`
- UI package class stability before this batch: `308` stable / `141` unstable.

## Completed

### P0 - Highest-Priority Fixes

- [x] Move Release Notes Markdown parsing out of composition-thread work.
    - Done in `AppMarkdownContent`.
    - `AppMarkdownContent` parses blocks with `produceState` on `Dispatchers.Default`.
    - Rendering is split into `AppMarkdownBlocksContent`.

- [x] Precompute student BGM displayed IDs and summary counts.
    - Done in `BaGuideStudentBgmTabContent`.
    - Favorite lookup uses normalized source URL and audio URL maps.
    - Displayed IDs, playable queue, resolved count, and loading count are grouped in
      `BaGuideStudentBgmDisplayedModel`.
    - `LaunchedEffect` uses stable displayed content IDs.

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

### P1 - Follow-Up Optimization

- [x] Rerun the Compose audit and compare metrics after the P0 fixes.
    - Full release compiler report moved `knownUnstableArguments` from `1427` to `1372`.
    - `inferredUnstableClasses` moved from `365` to `310`.

- [x] Add Release Notes parsed-block caching by source key.
    - Prevents repeated long-note parsing after sheet recreation or repeated open/close.

- [x] Hoist BGM displayed stats into a stable UI model closer to the coordinator or page state.
    - Keeps the composable closer to renderer shape.
    - Makes queue/count derivation easier to test.

- [x] Triage compiler-reported unstable shared types.
    - First pass marked high-traffic GitHub UI snapshot models as immutable:
      `VersionCheckUi`, release notes trust/profile UI models, Star Import UI state,
      share-import preview, and GitHub overview state.
    - Second pass covered Home / OS / BA / GitHub / MCP / Settings / Student snapshot
      models with explicit immutable contracts.
    - Mutable owners remain intentionally untreated: ViewModel, Repository, Activity,
      cache store, controller, CoroutineScope, Context, Mutex, and MMKV-backed state holders.

- [x] Sweep remaining production Lazy lists for missing `key` and `contentType`.
    - Extended list identity hygiene beyond the three audited call sites.

- [x] Replace boxed primitive route event tokens with typed primitive state.
    - Cleaned report evidence around `mutableStateOf(0)` event tokens.

- [x] Split dense Compose route files where the split reduces recomposition ownership.
    - GitHub import/export/Star Import transfer handling moved out of `GitHubPage`.
    - OS main-list callbacks moved out of `OsPage` into `OsPageListActions`.
    - `OsPage.kt` is now `428` lines and `OsPageListActions.kt` is `127` lines.
    - The latest compiler metrics kept `totalComposables=2501` and reduced unstable
      class evidence.

- [x] Continue low-risk stability convergence from compiler evidence.
    - Marked pure value snapshots for OS export sections, OS card import payloads,
      OS activity import merge results, shortcut suggestion options, OS suggestion UI state,
      and Settings search targets.
    - Latest full audit moved `inferredUnstableClasses` from `306` to `300`.
    - `markedStableClasses` moved from `115` to `126`.

- [x] Add a repeatable Compose QA gate.
    - Added `scripts/qa/compose_audit_gate.sh`.
    - Gate runs `:app:compileDebugKotlin`, Markdown parser tests, Release Compose audit,
      and `git diff --check`.
    - Gate writes logs and copied compiler outputs into ignored `artifacts/`.

- [x] Add a focused manual regression checklist for Compose performance passes.
    - GitHub Release Notes sheet: open, scroll, click a Markdown link, copy text.
    - BGM catalog: scroll list, toggle favorite, start playback queue.
    - BA pool/calendar: filter, scroll, open detail.
    - MCP skill list: scroll sections and expand/collapse.
    - Fullscreen gallery: rotate image, zoom/pan, predictive back.
    - Copy mode: expand/collapse and copy selected text.

### P2 - Measurement And Long-Term Quality

- [x] Capture runtime evidence for the optimized flows.
    - Priority: medium.
    - Scope: GitHub Release Notes sheet, BGM catalog scroll, fullscreen gallery rotation.
    - Captured emulator screenshots, UI trees, and `gfxinfo` snapshots.
    - Artifact target: `artifacts/compose-runtime-20260510/`.
    - Evidence note: `gfxinfo` deadline-missed percentages are noisy on the API 37 emulator;
      screenshots and UI trees are the functional smoke evidence.

## Next Queue

- [ ] Add a focused Perfetto pass for any flow that still feels slow during manual use.
    - Priority: medium.
    - Candidate scope: Release Notes loaded state and BGM list scroll.
    - Acceptance: trace has clear start/stop boundaries and app-owned slices or scheduler gaps.

- [ ] Continue low-risk stability convergence from the latest `app-classes.txt`.
    - Priority: medium.
    - Only mark clearly immutable snapshot/result/value-row types.
    - Continue skipping ViewModel, Repository, Activity, Store, Context, CoroutineScope,
      MMKV, Mutex, and mutable runtime owners.

- [ ] Continue structure splits with compiler metrics held stable.
    - Priority: medium.
    - Candidate surfaces: remaining GitHub page helpers and OS section rendering helpers.
    - Acceptance: `totalComposables`, `knownUnstableArguments`, and
      `inferredUnstableClasses` hold steady or decrease.

- [ ] Keep the Compose audit TODO updated after each audit run.
    - Priority: low.
    - Add the new score, date, changed findings, completed checkboxes, and artifact path.
