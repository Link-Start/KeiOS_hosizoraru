# KeiOS

## The compose-expert skill: what applies here and what does not

`compose-expert@aldefy-compose-skill` is installed. It is good on Compose the framework and wrong
about this app's UI layer, because it assumes androidx Material3 and androidx Navigation and KeiOS
uses **neither**. Counted in the tree on 2026-09-23, not assumed:

| | files using it |
|---|---|
| `MiuixTheme` | 368 |
| `MaterialTheme` | **0** |
| `androidx.compose.material3` | **0** |
| `androidx.compose.material.` (M2) | **0** |
| `top.yukonga.miuix.kmp.nav` | 6 (production) |
| androidx Navigation / Navigation 3 | **0** |
| `androidx.paging` | **0** |
| `androidx.tv` | **0** |

(The three files matching `androidx.navigation*` are `androidx.navigationevent`, the predictive-back
event library. That is not Navigation.)

### Reference only — never apply directly

These reference files describe libraries this app does not depend on. Read them for the *idea*;
never port their code, API names, or migration steps into KeiOS.

- `references/navigation.md`, `references/navigation-migration.md`,
  `references/source-code/navigation-source.md`
- `references/theming-material3.md`, `references/material3-motion.md`,
  `references/source-code/material3-source.md`

**The navigation one is the actual trap.** miuix-nav exports `NavDisplay`, `NavKey`,
`NavBackStack` and `rememberNavBackStack` — the *same names* as androidx Navigation 3, from
`top.yukonga.miuix.kmp.nav`. Guidance written for one will look like it compiles against the other
and does not. miuix-nav additionally has `opaqueDepth`, `navSwipeDismiss`, `NavSettleSpec`,
`NavMotion` and `navMaxLifecycleFor`, which androidx has no equivalent of — and issue #21 was
caused by `navSwipeDismiss` specifically. For anything navigation-shaped, read
`app/src/main/java/os/kei/ui/page/main/host/main/MainScreenNavHost.kt` and the miuix-nav sources,
not the skill.

Same shape for theming: KeiOS reads `MiuixTheme.colorScheme` and its own `AppMotionTokens`, so
`MotionScheme`, `MotionTokens` and M3 Expressive components do not exist here.

### Not relevant at all

`paging*.md`, `tv-compose.md`, `multiplatform.md`, `platform-specifics.md`,
`source-code/cmp-source.md`, `styles-experimental.md` — no Paging, no TV, Android-only, no
Compose Multiplatform.

### Genuinely useful

Framework-level and library-agnostic, so it applies unchanged: `state-management.md`,
`side-effects.md`, `performance.md`, `modifiers.md`, `lists-scrolling.md`,
`composition-locals.md`, `animation.md` (the core animation APIs, not the M3 tokens),
`accessibility.md`, `production-crash-playbook.md`, `view-composition.md`, and the
`source-code/` receipts for runtime, ui and foundation.

### Updating

Installed as a plugin, so it updates in place and this file survives:

```bash
claude plugin marketplace update aldefy-compose-skill
```

## Performance work

**Anything frame-time shaped goes through the `keios-frame-performance` skill first.** One
number is why: everything Compose does — input, animation, measure, layout, recording draw —
is about **0.4ms of a 20ms frame** (2026-09-15; the skill keeps the current table), and
RenderThread plus GPU is over 90%. General Compose advice
about recomposition is correct about Compose and cannot move this app, and following it here
has already cost two rounds that measured no change. The skill carries the measurement
procedure, the levers already rejected, and the constraint that visual quality is not
tradeable.

Frame-time investigations are written up in `docs/planning/` — start with
`hwui-frame-budget.md`, which also records the measurement noise floor and several
already-rejected optimisations. Harness lives in `scripts/perf/`.

The baseline profile is the other half, and it has its own document:
`docs/planning/baseline-profile-coverage.md`, which opens with the current state and then keeps the
chronology, corrections included. Three things about it are worth knowing before touching a journey.
A complete six-journey capture is **about 9-17 minutes** on the current A17 Phone AVD
(`ANDROID_SERIAL=<avd> ./gradlew :app:generateReleaseBaselineProfile`), so
reproduce failures by driving a subset of journeys through one `am instrument` pass instead — a late
journey only fails once the app carries state from earlier ones. Judge a change by the **named
component's** rule count, never by the profile total, which is dominated by a background refresh no
journey drives and by journeys that fetch over the network. And several journeys install fixtures —
tracked repositories that cannot resolve, a loopback HTTP server, a package hidden and unhidden — so a
journey that fails partway can leave device state behind; the helpers restore it, and the doc says what
each one touches.

The AVD cannot settle a frame-time question: one unchanged build swings about ±40% between runs.
Frame-time claims are measured on the phone, or at least interleaved within one build, as the skill
describes. A motion change can still be judged on the AVD by what it draws, for example frames per
tab switch from `dumpsys gfxinfo`, as long as the report does not call that a timing.

## Working in this repository

These sessions are long, touch several subsystems, and are usually owner-in-the-loop. What the last
ones kept relearning:

- **Read the record before acting.** Most areas have a `docs/planning/<topic>.md` that opens with the
  current state and lists what was already tried and rejected, and long tasks keep a handoff in the
  ignored `.planning/`. On a loosely specified request, look there and in the owning code before
  proposing a change: the obvious fix has often been measured and rejected already.
- **Evidence over the rule.** When a task suggests a rule ("treat a leading slash as…"), check it
  against the live source of truth first: a live repository, a published artifact, the shipped
  bytecode. Say which way the evidence went and why, even when it contradicts the suggestion.
- **Device.** Acceptance runs on the Android 17 AVD `KeiOS_API37_Validation`. A physical phone is
  often attached too, so always set `ANDROID_SERIAL=emulator-5554`. An emulator started from a
  session's background shell dies with that session; start it with `nohup … & disown`. Give DNS a
  few seconds after boot, and cold-restart after Tailscale, VPN or proxy changes (the DNS server is
  fixed at launch).
- **Miuix snapshots.** Bump with `scripts/deps/miuix_snapshot_check.sh` (`--files`, `--diff`,
  `--update`). Then verify the published sources jars against the compare, and diff
  `:app:debugRuntimeClasspath` before and after. Record each follow-up in
  `docs/planning/miuix-modernization-landing-plan.md`, including what was deliberately not adopted.
- **Commits.** Commit or push only when asked. When asked, split along review boundaries, and
  compile and test each intermediate state on its own rather than only the tip. Rebuild those states
  from byte-identical backups and check them with `cmp`, not by hand.
- **Reports.** A finished investigation gets a durable write-up in `docs/planning/` (what was found,
  the decision, the evidence, what was left alone). The owner treats these as part of the work.

## Tests

Tests are for behaviour and for regressions that would otherwise ship. A test that reads one file's
source and asserts that some expression, argument, call order or comment is present is a *mirror*: any
refactor breaks it, and it cannot catch a behaviour bug. On 2026-09-23 about 240 of these were removed
(`docs/planning/test-suite-cleanup.md`). Do not add new ones.

Source scans are still the right tool for a few things:

- a **repo-wide ban** with an explicit allow-list and a check that the allow-list still matches
  (`AppThemeSourceContractTest`, `PlatformWindowSourceContractTest`, `GitHubAssetHandoffSourceTest`);
- a **cross-file contract** the compiler cannot see, such as the baseline-profile test tags, locale
  parity, the CI workflow, or the release version;
- the one guard for a **documented bug** that cannot be rendered in a unit test. Say which bug in the
  test's comment. If a Compose or ViewModel test could reproduce it, write that test instead.

## Writing instructions for agents here

This file, `AGENTS.md` and the skills under `.agents/skills/` and `.claude/skills/` are read by
current models, which get to work quickly and follow instructions closely. Instructions that name
concrete failure modes and the evidence behind them work better than general exhortations. Lines
such as "think carefully" or "be thorough" add nothing: effort is set in the harness, not in prose.
Keep counts and measurements dated, and point to the document that owns them rather than copying
them, so they do not go stale here.
