# KeiOS Agent Guide

## Working agreement

- Complete the user's requested outcome within the authorized scope. Resolve routine implementation choices from the current code and project conventions; ask only when missing information materially changes the outcome or an action needs new authorization.
- Session instructions govern the task. Use this guide for project constraints and skills for applicable procedures. When instructions conflict, follow the higher-priority instruction and explain any material limitation. A generic skill example does not authorize an architecture migration, dependency upgrade, commit, or publication.
- Start with the relevant worktree status and diff summary; preserve unrelated changes. Keep implementation and any requested commits grouped by dependency and review boundary.
- Use the smallest relevant skill set available in the current session. Read supporting references only for the active task. Resolve a stale skill path through current discovery before reporting it unavailable.
- If a skill prevents progress, identify the exact `SKILL.md`, quote the blocking instruction, and explain its scope. Continue independent authorized work while resolving the blocker.
- Keep long-task handoffs in ignored `.planning/`: objective, owning paths, decisions, verification results and their inputs, remaining gates, and next action. After compaction, resume there and refresh evidence whose inputs changed.
- Communicate the result, supporting evidence, and remaining limitations directly. Match detail to complexity, use positive phrasing for comparisons, and keep technical failure states explicit. End with the actual outcome or required next action.

## Memory freshness

- Treat memory as a lead for locating relevant context. Follow current session instructions and project guidance; verify remembered technical facts against the current owning source before they determine an implementation or acceptance claim.
- Before relying on an older memory, check its evidence date and scope (revision, dependency, artifact, or environment), then confirm that its assumptions still apply. An unknown date, a relevant change since that evidence, or a conflict with current sources requires targeted revalidation. Recent retrieval or rewritten summary text does not renew the underlying evidence.
- Match verification to volatility: stable collaboration preferences can carry forward until the user changes them; architecture and workarounds need current source checks; versions, runtime state, and acceptance results need matching current inputs. Age prompts review rather than automatic rejection, and a recent timestamp alone does not establish validity.
- When current evidence supersedes a memory, use the current result and record the old assumption, replacement, and source/date in the task's scoped handoff when useful. Keep historical failures and unfinished investigations tied to their original run; verify whether they remain unresolved before treating them as current blockers.
- If a remembered claim cannot be verified, label it historical or unverified and keep it out of decisions that depend on its truth. Continue independent authorized work. Check the smallest relevant owner or reproduction; reuse still-valid checks instead of restarting a repository-wide investigation.
- This policy governs use of memory. Editing or deleting generated memory storage requires an explicit user request and the supported memory-update workflow; ordinary project work does not authorize rewriting memory files.

- For KeiOS, recheck dependency/channel claims in the version catalog and current project rules, architecture claims in the affected module, and Profile/performance claims against the relevant source revision, capture, APK, and device conditions. Old journey counts, timings, or build failures remain dated results until revalidated.

## Architecture and dependencies

- Read SDK, Kotlin, Compose, and dependency versions from `gradle/libs.versions.toml` and the affected Gradle files. The project minimum is Android API 35; preserve it unless requirements change.
- Preserve the current module ownership, Navigation 3, StateFlow, Compose, Miuix, Coil 3, Roborazzi, and custom stores. Fit new work to these choices; introduce a parallel framework only when the task requires it.
- Keep an existing Route/Screen/leaf split: route obtains ViewModel and platform services, screen renders state plus callbacks, leaves receive narrow props and emit specific events.
- MIUIX must use the latest SNAPSHOT: this UI framework intentionally tracks upstream snapshot updates. For other dependencies, use the latest RC or final release. During dependency maintenance, verify current coordinates, API compatibility, Android support, and the Miuix/Compose relationship documented in the version catalog. If the required versions conflict, report the concrete conflict and resolve it within the task scope; preserve the required release channel. A focused feature or documentation edit does not itself require upgrading every dependency.
- For Android platform behavior, inspect local SDK sources matching the relevant compile or device API. Check the installed source directories instead of assuming a fixed SDK revision; use official source references when local sources are incomplete.
- Add notification types through the existing notification framework and channels. Framework changes need a concrete requirement and verification of system recognition.
- Put new user-visible strings in the existing domain resources and keep supported locale keys, placeholders, and meaning aligned. Use `.agents/skills/keios-product-ux-writing/SKILL.md` when wording or a public text contract changes.
- Keep files cohesive. Around 1,000 lines is a signal to inspect responsibility boundaries; split along real ownership when useful to the task, without spreading unrelated refactors into a focused fix.

## Skill routing

- Android visual design and UX: `android-ux-design`; Compose implementation and review: `compose-expert`. Preserve Miuix and existing custom surfaces when applying general Material examples.
- Use focused platform, accessibility, navigation, coroutine, and ViewModel skills for the specific behavior being changed.
- Use a focused performance skill for a known issue. Use `auditing-compose-performance` for a requested broad audit or a broad symptom that needs measurement and diagnosis.
- Use `generating-baseline-profiles` for profile generation and `testing-compose-in-release-mode` for performance measurements. Profile collection, packaging verification, and performance comparison have distinct acceptance criteria.
- Skill checklists apply to the selected task and supported environment. A text edit does not require an app-wide UX audit; a profile-only refresh does not require a new performance study. Preserve project release gates when shipping is in scope.

## Verification by change

Choose checks that can falsify the changed behavior. Inspect `readme/BUILD.md`, the affected module's Gradle configuration, and existing nearby tests for current commands.

| Change | Relevant evidence |
| --- | --- |
| Instructions or documentation only | Diff, referenced paths, links, and claim consistency; skill validation when a skill changes |
| Kotlin logic or state flow | Focused existing tests, meaningful regression coverage for changed behavior, and compilation of affected consumers |
| Compose UI or localized copy | Relevant screenshot/semantics/resource checks; device or emulator inspection when layout, input, insets, or transitions change |
| R8, dependencies, or release packaging | Affected release build and packaged-artifact checks; debug success alone cannot establish release behavior |
| Performance | Release/benchmark measurements for the affected journey with comparable device and runtime conditions |
| Baseline Profile generation | Actual collection task completion, fresh generated files, and packaged profile verification |

- Run the required checks once for the final relevant inputs. Expand or repeat them for changes, failures, or unresolved concerns. Reuse still-valid results and state what they cover.
- Add tests for observable behavior and meaningful regressions. Avoid tests that merely mirror implementation text or instruction wording. Review screenshot differences before updating goldens.
- For adaptive UI changes, cover the affected phone/tablet window classes and input modes; use the task's device matrix rather than exercising unrelated screens.
- For Baseline Profiles, inspect `scripts/qa/baseline_profile_freshness.sh`, `baselineprofile/`, and `docs/planning/baseline-profile-coverage.md`. Use the explicit `:app:generateReleaseBaselineProfile` task and bind the intended AVD with `ANDROID_SERIAL`. Use one SDK ADB installation/server.
- Plan bounded, deterministic profile journeys around useful hot paths. State the journey set and runtime budget before expensive capture. Passing instrumentation tests alone does not establish collection; verify fresh per-journey/merged outputs, generated `baseline-prof.txt`/`startup-prof.txt`, the true Gradle exit, and `assets/dexopt/baseline.prof`/`baseline.profm` in the release APK.
- A controlled AVD can establish profile collection and packaging. Claims about user-perceived performance need suitable release/benchmark device measurements. Run the freshness gate for release work and account separately for uncommitted runtime changes, which the script's committed-ref comparison does not cover.
- Report source/tests, build/artifact, installation, and observed UI/business behavior at their proven levels. A missing device limits device claims; continue source and other independent verification that the task permits.

## Reasoning and execution

- Keep the configured model and reasoning effort unless the user requests a change. `low` is a useful starting point for scoped edits, routine maintenance, and running an established check.
- Consider `medium` for unfamiliar cross-module behavior and `high` for architecture tradeoffs, concurrency, or a failed diagnosis with competing explanations. Reserve higher effort for a concrete unresolved problem; more reasoning cannot supply missing logs, source, or device evidence.
- `medium` is also a practical choice for everyday feature implementation, review, and coordinated code/test/copy changes when several project conventions must be reconciled. Preserve a user's established `medium` choice; a model migration alone is no reason to lower it.
- Choose `xhigh` when the user wants deep analysis or the task needs sustained reasoning across interacting constraints: a cross-module state/ownership migration, subtle cancellation or ordering defects, a release/R8 compatibility problem with conflicting evidence, or a design review with several credible alternatives. It can be selected directly; trying lower levels first is optional. Identify the concrete uncertainty, compare the viable explanations or designs, and produce a decision, invariants, and a bounded verification plan.
- Effort follows the current phase and user preference. Simple edits and established command execution still suit Light/low; analysis or difficult review may justify `xhigh`. Keep the user's chosen setting and make any recommendation explicit rather than changing it automatically. File count, task duration, or routine test failures alone do not justify higher effort. After resolving the hard question, carry out the agreed plan without expanding scope or repeatedly reopening settled decisions.
- These are project heuristics, not automatic setting changes or measured model benchmarks. Compare similar tasks by correctness, rework, elapsed time, and usage before changing a default.
- Use subagents when explicitly requested or when an applicable instruction authorizes a concrete independent task. Keep shared worktree mutations, Gradle/ADB state, and device interaction under one owner.
