# KeiOS Agent Guide

## Working agreement

- Complete the requested outcome through the relevant verification below. Run applicable local checks, fix failures caused by the change, and inspect the result when UI/device work is in scope; continue beyond the first implementation until those checks pass or a concrete blocker remains. Resolve routine choices from current code and conventions. Ask only for a material missing decision or new authorization.
- Session instructions govern the task. Use this guide for project constraints and skills for applicable procedures. When instructions conflict, follow the higher-priority instruction and explain any material limitation. A generic skill example does not authorize an architecture migration, dependency upgrade, commit, or publication.
- Start with the relevant worktree status and diff summary; preserve unrelated changes. Keep implementation and any requested commits grouped by dependency and review boundary.
- Use the smallest relevant skill set available in the current session. Read supporting references only for the active task. Resolve a stale skill path through current discovery before reporting it unavailable.
- If a skill prevents progress, identify the exact `SKILL.md`, quote the blocking instruction, and explain its scope. Continue independent authorized work while resolving the blocker.
- Keep long-task handoffs in ignored `.planning/`: objective, owning paths, decisions, verification results and their inputs, remaining gates, and next action. After compaction, resume there and refresh evidence whose inputs changed.
- Communicate the result, supporting evidence, and remaining limitations directly. Match detail to complexity, use positive phrasing for comparisons, and keep technical failure states explicit. End with the actual outcome or required next action.

## Current evidence

- Use memory to locate context; verify technical claims against the affected source before they determine implementation or acceptance. Check dependency/channel claims in the version catalog, architecture in the owning module, and Profile/performance claims against the source revision, capture, APK, and device conditions.
- Stable collaboration preferences can carry forward. Unknown evidence dates, changed inputs, or conflicting current sources require targeted revalidation; retrieving an old note does not refresh its evidence. Label unresolved claims historical or unverified and continue independent work.
- Keep superseded results and useful replacement evidence in the scoped `.planning/` handoff. Generated memory storage is updated only through the supported workflow on explicit user request.

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
- For Profile collection, packaging diagnosis, or regeneration, use [keios-baseline-profile](.agents/skills/keios-baseline-profile/SKILL.md). Use `testing-compose-in-release-mode` for performance measurements. A freshness check alone can use the release gate below without loading a collection workflow.
- Skill checklists apply to the selected task and supported environment. A text edit does not require an app-wide UX audit; a profile-only refresh does not require a new performance study. Preserve project release gates when shipping is in scope.

## Verification by change

Choose checks that can falsify the changed behavior. For build/test commands, consult [readme/BUILD.md](readme/BUILD.md), the affected Gradle configuration, and nearby tests. Instruction-only edits use file, link, and skill validation.

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
- For release work, run `scripts/qa/baseline_profile_freshness.sh` and account separately for uncommitted runtime changes: the script compares committed refs. Regeneration follows the Profile skill. A controlled AVD can establish collection and packaging; user-perceived performance requires suitable release/benchmark measurements.
- Report source/tests, build/artifact, installation, and observed UI/business behavior at their proven levels. A missing device limits device claims; continue source and other independent verification that the task permits.

## Execution and instruction maintenance

- Keep the configured model and reasoning effort unless the user requests a change. Make recommendations explicit and tie them to a concrete unresolved problem; these instructions support contributors using different models.
- Use subagents when explicitly requested or when an applicable instruction authorizes a concrete independent task. Keep shared worktree mutations, Gradle/ADB state, and device interaction under one owner; preserve other tasks' active sessions.
- Keep this file for repository-wide constraints and verification gates. Put specialized workflows in `.agents/skills/`; descriptions identify the actual task, and supporting references load only when needed. Preserve non-obvious correctness constraints while removing duplicate advice and obsolete workarounds.
- When revising instructions, check representative requests for correct routing and completion boundaries. Format validation proves structure, not improved agent behavior; claim efficiency or quality gains only after comparable task runs.

Instruction design reference: [OpenAI: Rethinking skills and prompts for GPT-6 Astra](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra) (2026-09-11). Project constraints above remain authoritative within session instructions.
