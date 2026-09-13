---
name: keios-baseline-profile
description: >-
  Collect or regenerate KeiOS Baseline Profiles and verify release packaging.
  Use for profile collection or packaging failures, not general UI performance audits.
---

# KeiOS Baseline Profile

Deliver fresh generated profiles for the intended source and verify that the
release APK packages them. Collection, packaging, and measured performance are
separate outcomes. Paths in commands are relative to the repository root.

## Inputs and scope

- Read [coverage and collection plan](../../../docs/planning/baseline-profile-coverage.md)
  for the current journeys, replay limits, and acceptance evidence. Confirm them
  against `baselineprofile/src/` rather than copying historical counts or timings.
- Inspect [producer configuration](../../../baselineprofile/build.gradle.kts)
  and [app configuration](../../../app/build.gradle.kts) for connected-device,
  variant, and generated-source wiring. Use [build guide](../../../readme/BUILD.md)
  when build commands or environment setup need resolving.
- State the intended AVD, journey set, and runtime budget before expensive capture.
  Keep journeys deterministic and bounded around useful hot paths. Changes to
  journey/replay limits must update their existing contract test and coverage plan.
- Bind the intended AVD with `ANDROID_SERIAL` and use one SDK ADB installation/server.
  Preserve other tasks' device, ADB, and Gradle sessions; use an isolated available
  target or report the specific conflict.

## Collection and acceptance

Use the explicit collection task with the selected serial:

```bash
ANDROID_SERIAL=<selected-avd-serial> ./gradlew :app:generateReleaseBaselineProfile
```

Instrumentation success alone is insufficient. Verify the true Gradle exit status,
fresh per-journey and merged outputs, and both generated files under
`app/src/release/generated/baselineProfiles/`: `baseline-prof.txt` and
`startup-prof.txt`. Correlate outputs with this run's source and target so old
files cannot stand in for a completed capture.

Build the affected release APK using the current build guide and check its
`assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm` entries. Record
source revision plus relevant working-tree changes, target/API, task result,
generated outputs, and APK identity in the task evidence. Resolve failures caused
by the requested changes and rerun only invalidated checks.

For the release freshness gate, use
[scripts/qa/baseline_profile_freshness.sh](../../../scripts/qa/baseline_profile_freshness.sh).
It compares committed runtime sources with the profile commit; inspect uncommitted
runtime changes separately. A pre-commit stale result is not by itself proof that
fresh local collection failed, and a passing committed-ref check does not cover
uncommitted changes. Keep both facts explicit before delivery.

A controlled AVD proves collection and packaging. If performance comparison is
requested, use comparable release/benchmark conditions and the relevant performance
skill; do not turn collection duration or profile rule counts into a speedup claim.
