# How a tracked repository becomes one version on a card

Both source modes. They share the selector and the evaluator now; what differs is what each source
can tell them, which is the subject of the second half.

Written after the third report in a row was diagnosed by fetching the repository's real API
response, hand-trimming it, and writing a throwaway probe to print the candidates the pipeline had
already discarded. The rules were not especially wrong. What was wrong is that the pipeline made a
decision and kept no record of it, so each new shape of repository started that work from nothing.

## The stages, and who owns each rule

```
releases?per_page=N ──▶ parse ──▶ plan ──▶ (releases/latest) ──▶ resolve ──▶ evaluate ──▶ card
```

| stage | owner | decides |
|---|---|---|
| fetch, page window | the strategy | how many releases are read, and whether the page came back full |
| parse | the strategy | which fields become candidates, assets, channel |
| rank | `ReleaseCandidateRanker` (core-versioning) | which of two releases is newer, and by which rule |
| plan | `GitHubReleaseSelector.plan` | candidate filtering, ranking, **and whether `releases/latest` is worth a request** |
| resolve | `GitHubReleaseSelectionPlan.resolve` | the forge's flag overriding the ranking; the duplicate-version pre-release |
| evaluate | `GitHubReleaseEvaluationEngine` | everything needing the reader's own build or the clock |
| explain | `GitHubReleaseDecisionNote` | the part of all of the above that belongs on a card |

**A new rule goes in `plan`/`resolve` if it is about the releases, and in `evaluate` if it needs
the local build or the current time.** That is the only division. Before the selector existed the
rules were spread across the strategy, the ranker and the evaluator with no principle separating
them, which is why each of the last three fixes landed in whichever layer the symptom surfaced in.

## Things that are true and easy to get wrong

**There is one clock.** The later of `published_at` and the newest asset's `updated_at`. Ranking,
reset detection, pre-release relevance and the abandonment rule all read it. A rolling tag is
published once and then only its artifacts move, so `published_at` alone ranks it behind every tag
cut since. Parameter names say `freshness`, not `updatedAt`, to make handing one of them a publish
date look wrong.

One rule, two properties, and grepping for either alone will miss half of it:
`ReleaseRankingEvidence.freshnessMillis` in `core-versioning` is what the ranker and the reset
detector read; `GitHubReleaseVersionSignals.effectiveFreshnessMillis` in `feature-github-engine` is
the same `max` over the engine's own model, and is what relevance and abandonment read. They agree
by construction rather than by sharing a definition, which is a seam worth knowing about before
changing either.

**`releases/latest` is not "the newest".** It honours the maintainer's own *Set as the latest
release* flag, which makes it the only authority on a repository that restarted its numbering. It
is also a second request per repository per refresh, so in **API mode**
`plan.shouldConsultForgeLatest` spends it on exactly two shapes: no stable release in the window at
all, and a highest version number that reads as one a rename left behind.
`GitHubReleaseSelectionEndToEndTest` counts the requests.

**Atom mode pays for it on every repository, every refresh**, and that is not an oversight: the feed
carries no pre-release flag, so the redirect is the only thing that tells Atom which entry is
stable. It is issued concurrently with the feed rather than after it. The consequence shows up in
the history page — an Atom item makes two or three calls where an API item makes one — and it is why
a flag that merely agrees with the ranking is not reported as a decision (see
`GitHubReleaseDecisionNote`): in this mode agreement is the norm, not news.

**The window is one page.** `per_page` follows the caller's limit (30 by default) and
`GitHubReleaseWindow.windowWasFull` records whether it came back at the limit. `iebb/mithka`
publishes 119 releases, so its window covers about three months. Fine for "what is the newest";
not a basis for any claim about the shape of the list.

**`hasDownloadableAsset` has three states and only one of them is a claim.** `null` is "this
source cannot see assets" — every Atom entry. `false` is the GitHub API saying the maintainer
attached nothing, which means there is nothing to update *to*.

**Confidence is not trustworthiness.** `VersionConfidence.Low` means the two version strings share
no leading number, which is ordinary across a major bump. It is used to refuse a *reassuring*
answer (`ComparisonUncertain` instead of `UpToDate`), never to suppress an update.

**A tag with no version in it is a rolling tag.** Its name carries the build stamp and moves on
every CI run, so the release ignore key anchors on the tag — ahead of the APK identity, which moves
for the same reason. Otherwise "ignore this pre-release" expires overnight.

## Reading the record

`GitHubReleaseSelection` hangs off the snapshot and the check. `summary()` gives one line:

```
stable=v1.6.2 by VersioningReset over 1.25.2; pre=none; considered=30 (window full); rejected=...
```

The two rules the selector cannot run — a pre-release superseded by the stable that followed it,
and a preview line gone quiet — are recorded by the evaluator as `preReleaseRejection`.

What the record is **not** is a ledger with a line per release. `rejected` holds the runner-up in
each lane, a pre-release carrying nothing comparable to rank, one that is the stable under another
tag, and the evaluator's verdict. The twenty-something releases that simply lost on version number
are counted in `consideredCount` and not named, deliberately: `GitHubReleaseSelector.plan` says so
where it filters the pre-release lane, because a row per entry buried the handful that say
something under ten rows of noise.

Only the parts that change what a reader would do reach the card, via
`GitHubReleaseDecisionNote`: a restarted project's release, a choice made on time rather than
number, and a retired preview line. Everything else stays in the record.

## What Atom mode is not told, and what it does instead

`releases.atom` is a syndication feed. Four facts the decision turns on are simply not in it, and
every Atom-specific rule exists because of one of them.

| The question | API mode | Atom mode |
|---|---|---|
| Is this a pre-release? | `"prerelease": true`, per release | keyword match over tag, then title, then the rendered body |
| Is there anything to install? | `assets[]`; an empty list is a claim | no asset data at all, so `hasDownloadableAsset` is always `null` |
| When did it move? | `published_at` plus each asset's `updated_at` | `<updated>`, which is the release's **edit** time |
| Which one is current? | `/releases/latest` as JSON, on demand | the same URL's 302, read as an HTML redirect |

**`<updated>` is an edit clock, and the ordering it gives can invert.** Measured on NekoBox: its
spent `preview` reads 29 seconds *newer* than the `1.4.2` that replaced it, because shipping `1.4.2`
is what edited the preview; the API puts the same pair a week apart the other way.
`GitHubReleaseSignalSource.clockToleranceMillis` is the answer — a day for the Atom sources, zero for
the API — and inside that window the clock is treated as no information and the version numbers
decide. Another entry in that same feed is five months off its publish date.

**The redirect has three outcomes, not two.** Only a 404 is GitHub stating something:
`/releases/latest` skips pre-releases, so no latest release means no stable release. Everything else
— a rate limit, a 5xx, an HTML 200, an unreachable host — is *unknown*, falls back to the feed, and
is not cached. Reading those as "this repository has no stable release" is what used to put "may
only have pre-releases" on a card directly above the stable release it had just found, and to widen
the pre-release filter so that same release filled both rows.

**The 404 is also the one fact Atom can recover.** No stable release means every entry the feed's
prose made look stable was misread, so the lanes are corrected from a request already being made.
Only the lane, not the channel: the channel is what the release's own text claims, and that is still
true.

**Two guesses do not make an update.** A lane read out of prose plus a `Low` confidence comparison —
no shared leading digit with the reader's build — is refused rather than badged. That combination is
how `iebb/mithka` offered `play-version-code-1789096599` to somebody on 1.4.6: a tag its CI writes
for bookkeeping, parsed as version 1,789,096,599, which nothing the project ships can ever beat.
Anything `releases/latest` confirmed is exempt, because that is stated rather than inferred.

**The window is ten, fixed.** `releases.atom` takes no page parameter. For a repository publishing CI
builds as releases the whole window can be rolling builds — mithka's ten entries currently hold no
stable release at all — which is why the redirect is load-bearing here rather than a nicety.

What stays unknowable: assets. No amount of rule work adds an asset list to a syndication feed, so
the "an update you cannot install is not an update" rule cannot fire in Atom mode. The NekoBox case
is closed here by the clock tolerance instead, via the version relationship.

## What makes a refresh of forty repositories slow

Measured with a harness that drives the real strategies over `MockWebServer` with a fixed 120ms
server, forty repositories, at the concurrency the batch scheduler actually picks. Device numbers
cannot show this — six tracked items never approach the limits below.

The harness was throwaway and is **not in the tree**: it existed to find the ceiling, and what it
found is pinned by `OkHttpCallConcurrencyTest` instead, which is the part worth keeping green. The
figures below are therefore a record of one measurement, not something a later reader can re-run
unchanged — rebuild the harness if you need to compare against them.

| | before | after |
|---|---|---|
| API mode | 803ms | **402ms** |
| Atom mode | 1973ms | **522ms** |

Three things were in the way, and only one of them was a number anybody had chosen.

**A request used to own the thread that started it.** `executeCancellable` wrapped OkHttp's blocking
`execute()` inside `suspendCancellableCoroutine`, which reads as suspending and is not: the caller's
thread sat in a socket read for the whole round trip. The refresh runs on a ten-thread dispatcher, so
ten was the most requests that could ever be in the air — asking for 16, 24 or 32 produced ten in
flight and the same wall clock every time. `enqueue` costs no thread while a request is in the air.
`OkHttpCallConcurrencyTest` pins it by holding every request until more have arrived than the caller
has threads, which the blocking version cannot do.

**The budget that replaces it is per host, and everything here is one host.** OkHttp's default is
five concurrent calls per host, which would have been *worse* than the accidental ten.
`SharedHttpClient.MAX_CONCURRENT_CALLS_PER_HOST` states it instead. Both GitHub hosts speak HTTP/2,
so these are streams on one connection rather than sockets to open. It changes no request counts, so
an hourly rate limit sees exactly what it saw before.

**Atom mode asked its two questions in sequence.** The feed and the `releases/latest` lookup need
nothing from each other, so every repository cost two round trips where API mode costs one. They now
go out together. That is most of Atom's 3.8x.

The batch tiers count *items*, not requests: an API-mode repository is one call and an Atom-mode one
is two. Anything the tiers ask for beyond the per-host budget queues inside OkHttp rather than piling
onto the radio, which is why the top tier stops there, and why the scheduler tests assert
relationships rather than the numbers.

Two things that are already cheap and were checked rather than assumed: the precise-APK path is
behind a persistent asset cache plus single-flight de-duplication (3-7ms per item on a warm device),
and installed-app lookups are cached with a TTL rather than re-scanning packages per item.

## Telling a slow refresh apart from a slow network

The three fixes above were found with a harness against a 120ms `MockWebServer`. That is the right
instrument for a concurrency ceiling and the wrong one for everything else: an emulator resolves
names instantly, hands out TLS sessions for free, has no radio to wake, and never meters anything.
A green benchmark there says nothing about somebody's phone on a shaped cellular link.

So the refresh history now records what the network actually did, per tracked item:

| phase | what it means | what to do about it |
|---|---|---|
| `queued` | waiting for a slot in our own per-host budget | a concurrency setting — ours |
| `dns` | name resolution | usually the network's, occasionally a connection-reuse problem |
| `connect` | TCP plus TLS | the pool is not being reused; check the keep-alive and the burst shape |
| `waiting` | request sent, nothing back yet | GitHub's own time, or a rate limit. Not ours |
| `body` | first byte to last | bandwidth, and the only phase that scales with how much we asked for |

Measured on a physical phone over real wifi, the same repository in Atom mode on two consecutive
refreshes: `Server wait 74% · 3 requests · 59.95 KB · 0/3 connections reused` on the cold start after
an install, then `Connecting 61% · 2 requests · 58.51 KB · 0/2 connections reused` two minutes later.
Before this, both read `Release Ns` and were indistinguishable.

The reuse figure in those lines is per *process*, not per refresh. A third refresh, in the same
process a minute later, came back in 366ms with `Server wait 78%` and no reuse pill at all — the
pool had both connections and the pill only appears when a handshake was paid for. So the cost is
paid by the first items after a cold start, and the concurrent calls an Atom item makes race each
other for it: issued together, neither is in the pool yet for the other to find. With forty
repositories that is a handful of handshakes at the front of the batch, not forty.

The cause pill reports a **share, not a duration**, and the distinction is not cosmetic.
`NetworkTimingSummary` adds each phase up across every call in the scope, and those calls overlap —
Atom mode asks the feed and `releases/latest` at the same time, and a retry runs the pair again. Two
overlapping three-second waits are six seconds of waiting inside a three-second stage, so a duration
here would sit next to `Release 3s` reading `Server wait 6s` and read as a broken clock. How long the
item took is already on the row; what this pill owes the reader is which phase took it.

Three pieces make that possible:

- **`NetworkTimingScope`** (core-io), carried in the coroutine context. `executeCancellable` tags the
  request with it and an OkHttp `EventListener` splits the call. The scope is installed per tracked
  item by the batch runner, so a call made eight layers down in a strategy still lands on the right
  row. A call made outside a scope records nothing.
- **`NetworkCallGauge`**, which counts calls that were *actually* in the air. The batch already
  recorded the concurrency it asked for; those two were silently different for months and that is
  precisely the bug the harness found. They are deliberately *not* shown side by side on the card:
  one counts repositories and the other counts HTTP calls, so `Concurrency 16 (actual 20)` compared
  two different units and read as a contradiction. The schedule row keeps the requested figure and
  the network row states its own unit — `Wi-Fi · up to 6 requests at once`. The export carries both,
  as `maxRequestedConcurrency` and `maxPeakConcurrentCalls`, rather than the old
  `maxObservedConcurrency`, which was neither observed nor a measurement.
- **The connection kind and whether it was metered**, on the record. Nine seconds on wifi and nine
  seconds on a metered cellular link are not the same finding.

Nothing is written when nothing was measured: an item answered from cache made no calls, and a row
of zeroes reads like a measurement. The whole thing rides in the existing history export, so a user
reporting "refreshing is slow on my phone" can send a file that says which project, which phase, on
what connection, and how much of the requested concurrency the device managed.

## Fixtures

`scripts/qa/capture_release_fixture.sh <owner/repo>` fetches and projects to exactly the fields
`parseReleaseEntry` reads. `--atom` captures the feed instead, verbatim and anonymously — no `gh`,
no token, the same request Atom mode makes. That projection is the only written-down copy of the field list; the two
corpora here were hand-trimmed before it existed, and one of them
(`stratumauth-releases.json`) was captured before the parser read asset lists, so every release in
it claimed to have nothing attached and silently switched off the installability rule for the whole
corpus. Assets were backfilled and the corpus now asserts they are present.

Each corpus pins `nowMillis` to its capture date. A fixture judged against the day the test runs
starts failing for no reason but the calendar.
