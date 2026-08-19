# BA Craft Chamber timers and the daily-done tile

Record of what landed for issue #24 and the daily-done shortcut that grew out of it, kept for the
parts that are **not** recoverable from the code: where the game numbers came from, and which designs
were tried and rejected.

## Craft Chamber: the mechanic, and why one formula covers both halves

The game's 製造 screen has two functions, each with three independent slots — six timers per account.
They look like two mechanics and are one.

| Grade | Per-item duration |
|---|---|
| 下級 / 하급 | 30m |
| 一般 (中級) / 일반 | 1h 30m |
| 上級 / 상급 | 3h |
| 最上級 / 최상급 | 6h |

Source: namu.wiki's Craft Chamber page — *「등급이 높을수록 제작시간이 길어진다. (하급: 30분,
일반: 1시간 30분, 상급: 3시간, 최상급: 6시간)」* — cross-checked against the game8 and kamigame
tables. The same ladder drives both functions.

**A slot's total is the sum of the grades of every item it will produce.**

- **Generate** opens 1–3 nodes ("解"), each producing one item, freely mixed grades. namu.wiki:
  *「3차 노드까지 전부 개방하면 … 총 3개의 아이템을 제작하며, 제작 시간도 3개 분량을, 제조 부스터
  티켓도 3개를 사용한다」* — three items, three items' worth of time, three booster tickets.
- **Fusion** produces 1–5 copies of one recipe. The cap is **5 at every grade** — verified in-game by
  the reporter, not from a guide, because no guide states it.

That is why `BaCraftSlot.grades` is a `List<BaCraftGrade>` rather than a grade plus a count: Fusion
stores its grade repeated N times, and `computedDurationMs()` is `grades.sumOf { it.durationMs }` with
no branches. The difference between the two functions collapses into one validation rule in
`normalized()` — Fusion forces every entry onto the first grade — not a second code path.

### A misreading worth recording

The first pass had the node count *not* affecting the slot total, on the strength of game8 and
kamigame wording. Those sources mean the **per-item** duration is unchanged by node count. The slot
total is the sum. The reporter corrected this twice before it stuck; namu.wiki settles it.

### Deliberate bounds

- `BA_CRAFT_MAX_DURATION_MS = 48h`. The longest reachable real craft is Fusion at 5 × 最上級 = 30h.
  The override is loosened to 48h so a booster ticket spent partway or a clock correction still fits,
  while a corrupt value cannot arm an alarm months out.
- A hand-entered total wins over the sum, because the game only ever displays the slot's total. Blank
  clears the override rather than meaning zero, so emptying the field cannot make a slot unstartable.

### Reminder plumbing

- `startedAtMs` is a wall-clock anchor, never a countdown — same choice AP and the cafe already make,
  so a killed process or a skipped frame cannot make it drift. `startedAtMs == 0L` is the idle
  sentinel, which is why no test may use the epoch as a start.
- `BaCraftNotifiedMarkers` keys on the **completion instant**, not a boolean. Loading a slot with a
  different craft moves its end and re-arms the reminder for free, while re-evaluating the same craft
  stays silent. No explicit reset, and no way for a stale flag to suppress a real completion.
- Those markers live in the reminder runtime, not in `BaCraftSlot`, so posting a notification cannot
  bump `runtimeUpdatedAtMs` — that field arbitrates WebDAV merge, and a reminder must not make one
  device's game state look newer than another's.
- Craft always schedules at `BackgroundAlarmPrecision.Prompt`, never windowed. Android 17's windowed
  alarms slip 10–30 minutes, which is fine for an AP threshold and useless for "your craft is done".
- The completion sweep collects successes and writes them in **one** batched store call. Six separate
  writes per sweep meant six JSON re-encodes and, worse, a partial write on failure would re-fire the
  15-second alarm retry.

## Daily-done: template

`planBaDailyDone` applies the common Sensei routine in one tap:

- Both AP pools to 0.0, anchors re-based to now, notified levels to -1. Rationale from the reporter:
  a teacher who opens the game will collect the cafe AP into the main pool and spend it, so both
  really are zero.
- Headpat and both invite tickets start their cooldowns **only if already elapsed**. Anything still
  on cooldown is left alone — the tap must never look like it un-spent something.
- Generate slots 1 and 2 load one 上級 node (3h) each, only when free. Chosen because one node is the
  best value per booster ticket, and 上級 items and gifts are the common 3h case.

Note that headpat comes back at `min(3h cooldown, next cafe student refresh)`, so the visible number
after a tap is frequently *not* 3h. That rule predates this feature and is intact.

## The quick-settings tile: what the platform actually allows

The reporter pushed back on an early framing that declaring a tile "pollutes" the picker. They were
right, and the corrected facts are the design:

1. Declaring a `TileService` does **not** put it in the quick-settings panel. Official wording: the
   tile appears only *after the user has added it*. It does appear in the panel's **edit list**.
2. `StatusBarManager.requestAddTileService()` (API 33+) is the sanctioned in-app prompt.
3. Every component here additionally ships `android:enabled="false"`, so it is absent even from the
   edit list until claimed. Claiming enables it via `setComponentEnabledSetting` first — an add
   request for a disabled component returns `TILE_ADD_REQUEST_ERROR_BAD_COMPONENT`.
4. The add request is rate limited **per ComponentName**, and the platform "can choose to auto-deny a
   request if the user has denied that specific request (user, ComponentName) enough times before" —
   permanently. So it fires only from an explicit tap, never on composition or an account change, and
   a slot keeps its component identity rather than being recycled between accounts.
5. The requesting app must be in the foreground (`TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND`).

Points 4 and 5 came from `~/Library/Android/sdk/sources/android-37.1/android/app/StatusBarManager.java`
after web javadoc lookups kept failing. The local SDK sources are the better reference for this API.

### The one hard constraint

A tile is a manifest component and cannot be created at runtime. "One tile per account" is therefore a
**fixed pool of 3** subclasses reading their binding from `BASettingsStore` — which happens to match
`serverIndex`'s own 0..2 range. The UI says so when the pool is full rather than failing quietly.
Launcher shortcuts have no such limit and are generated per account, bounded only by
`getMaxShortcutCountPerActivity`, with truncation logged rather than silent.

Deleting an account frees its slot; **disabling** one does not. Disabling is reversible, and handing
the tile back may not be.

### Three bugs found only on a device

- **No feedback at all.** Android 12+ drops toasts from a background app and a tile click is not
  foreground; and at the icon-only tile size the panel renders neither label nor subtitle. Both cheap
  channels are unavailable, so the tile reports through `BaDailyDoneNotificationDispatcher` — one
  fixed id, replacing rather than stacking, silent when nothing changed. The Shortcut path keeps its
  toast, because there the app really is foreground.
- **Dismissing the dialog was reported as "unavailable".** `TILE_ADD_REQUEST_RESULT_DIALOG_DISMISSED`
  is `3`, is `@hide` (so it cannot be named in app code), is absent from the `@IntDef`, and still
  reaches the callback when the dialog is swiped away. Enumerating the known codes sent it to the
  error branch, telling the teacher their device could not host a tile it had just offered them.
  `baDailyTileAddResultOf` now splits on the documented boundary instead — *"Values greater or equal
  to [1000] indicate an error in the request"* — so anything below it is a decline and any result code
  the platform adds later lands in the right bucket on its own.
- **A declined request left the claim in place**, found in the same session by noticing the settings
  row still said "Remove tile" after cancelling. Claiming *has* to precede the request (an add for a
  disabled component fails), and nothing undid it. Three consequences, worst first: the component
  stayed in the quick-settings editor the teacher had just declined it from, which is the exact
  property this whole design exists to protect; the row lied about a tile that was not there, because
  it derives from the component-enabled state — the only part that survives process death; and a
  declined per-account request permanently burned one of the three pool slots. Both request paths now
  roll back to their pre-request state unless `keepsTile`, and only if they were the ones that changed
  it, so re-requesting an already-added tile cannot disable a working one. Verified on the AVD in both
  directions: cancelling leaves `enabledComponents` without the service and the row offering "Add
  tile"; accepting keeps both.

## Card expansion

Six rows made the Craft Chamber the tallest card on the page, and most of the time all six are idle,
so the header is a disclosure control.

Collapsing is non-lossy by design: a plain hide button would trade the card's meaning for vertical
space, so the header carries a one-line summary. Ready outranks running — it is the only state needing
action — and anything running reports the nearest completion, the single number the rows were scanned
for.

`craftCardExpanded` is **global**, not per-account: it is one card's layout on one page, unlike `craft`
itself. It rides `BaPageSnapshot` the way `showEndedActivities` does, so `withBaAccount` leaves it
alone. The two sit adjacent in the snapshot and are easy to confuse, so a mapper test pins it.

Its store write deliberately skips `notifyChanged()`. That signal re-labels the daily-done tiles and
shortcuts and wakes the home overview; folding a card changes none of that.

Default is expanded — hiding rows an existing install was already using would read as data loss.

## Long-press: the template stopped being fixed (2026-08-19)

The last "Open" item below — no entry for the AP left after clearing — turned out to be the smaller
half of the same gap. A tile's long-press was falling through to the system app-info screen, which is
the platform's fallback for a tile with nothing to configure, so the tile had both a fixed template
and no place to change it.

**The long-press hook.** SystemUI resolves `TileService.ACTION_QS_TILE_PREFERENCES` against the tile's
own package and launches the first activity that handles it, attaching the pressed component as
`Intent.EXTRA_COMPONENT_NAME` (and the tile state as `TileService.EXTRA_STATE`); it falls back to app
info only when nothing resolves. `BaDailyDoneTemplateActivity` handles it for all four tiles and reads
its target off the extra. It has to be `exported="true"` for the shade to launch it, which is why the
component name is treated as untrusted input — the package is checked, and anything unrecognised
resolves to the all-accounts scope rather than to a guessed account. Every failure mode here is
invisible (no crash, no log — the long-press just goes back to app info), so the manifest half is
pinned by `BaDailyTilePreferencesManifestTest` and the component mapping by `BaDailyTileKindTest`.

**What the template now holds.** `BaDailyDoneConfig`, one record for every trigger:

| field | why it is a choice and not a fact |
|---|---|
| `apRemaining` | only the cafe pool can be emptied exactly; a teacher who stops at 37 would otherwise correct it by hand after every run. A value *above* the current pool is accepted — that is a sync correction, and refusing it would be the surprising half |
| `startHeadpat` / `startInvite1` / `startInvite2` | "I did not pat today" is not derivable from a cooldown |
| `craftFunction` | Generate or Fusion (物質合成). One run loads one function; the other is untouched |
| `craftSlots` | 0..3, counting from the first. Zero means "leave my crafts alone" |
| `craftGrade` + `craftEntriesPerSlot` | the two together *are* the duration, by the same summed-multiset formula `BaCraftSlot.computedDurationMs` already uses. 6h is one Superlative item; 18h is three; a five-copy Fusion of Superlative is the 30h ceiling. No second unit for the teacher to reason about |

Deliberately **not** in the record: cafe AP (one action, always lands on zero, so a remainder would be
a setting with one correct value), and every idempotence rule — "is this cooldown spent", "is this slot
still counting down" are facts about the account and stay in `planBaDailyDone`.

Every default reproduces the pre-configurable template, which is the compatibility contract an install
that never opens the editor relies on; `BaDailyDoneConfigTest` pins it against the old constants.

**One record, not one per trigger.** The tile, the launcher shortcut and the MCP tool are three ways to
say the same sentence, so a per-trigger copy would make "my dailies" mean different things depending
on which one the teacher reached for. It is also not per account, so the all-accounts tile has one
answer to apply. The price: a teacher whose accounts end the day at different AP has to pick a number.
The long-press makes correcting it two taps, which is why that trade was taken.

**Two actions, because the intents differ.** *Save* records what a later tap will apply; *apply now*
does that and runs it. Apply saves first, so what just ran is also what the plain tap will run — there
is never a second template in play. Neither is a preview.

**`apCleared` became `apAdjusted`.** With a remainder the field would have been lying in two
directions: `changedAnything` has to be true when the pool moves 12 → 40 (else the tile posts nothing
for a run that did change something), and "cleared" is false when the pool goes *up*. The MCP line
prints the new name.

**The notified-level reset got narrower.** The old plan always wrote `apLastNotifiedLevel = -1`. A
configured remainder can sit above the teacher's own reminder threshold, where clearing the marker
re-announces a level they have already been told about — so it is cleared only when the pool really
lands on zero. Nothing is lost by keeping it: the dedup is an equality against the level, a changed
pool no longer matches, and the reminder sweep clears the marker itself once AP is back under the
threshold.

**Verified on the API 37 phone AVD**, end to end: `cmd package resolve-activity` returns the activity
for the action; a real long-press on the added tile opens the sheet with the all-accounts scope; a
per-account tile whose account is gone renders "no account bound" with apply disabled and editing
still allowed; a run with `apRemaining = 37`, Superlative × 3 left the account at **AP 37/240**, cafe
**0/740**, headpat and both tickets started, and Craft Chamber "2 running · next 17h 59m"; the
unsaved-changes action sheet fires on back and discarding really does not write.

Two things a sheet-only window cannot do, both accepted rather than worked around: the backdrop behind
the sheet is opaque (`SceneBackdropHost` pre-paints the theme background, and no app window can sample
the shade's pixels anyway — the same compromise `GitHubShareImportActivity` already makes), and the
platform dim is off because the sheet draws its own scrim.

## Open

- `BaDailyTileManager` and `BaDailyShortcutSync` have device verification but no unit tests beyond the
  pure parts (`baDailyTileAddResultOf`, `kindOf`, and the binding model's own 13 tests). What is left
  is almost entirely `PackageManager` / `StatusBarManager` calls; testing it needs a seam that does not
  exist yet.
- The template is global, so a per-account AP remainder is still not expressible. Nobody has asked for
  one; if they do, the shape is a per-account override on top of this record, not a second record.
