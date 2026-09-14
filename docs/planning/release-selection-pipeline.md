# How a tracked repository becomes one version on a card

API mode only. The Atom strategy reduces its feed its own way and none of this describes it.

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
| fetch, page window | `GitHubApiTokenReleaseStrategy` | how many releases are read, and whether the page came back full |
| parse | `GitHubApiTokenReleaseStrategy.parseReleaseEntry` | which JSON fields become candidates, assets, channel |
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

**There is one clock.** `ReleaseRankingEvidence.freshnessMillis` — the later of `published_at` and
the newest asset's `updated_at`. Ranking, reset detection, pre-release relevance and the
abandonment rule all read it. A rolling tag is published once and then only its artifacts move, so
`published_at` alone ranks it behind every tag cut since. Parameter names say `freshness`, not
`updatedAt`, to make handing one of them a publish date look wrong.

**`releases/latest` is not "the newest".** It honours the maintainer's own *Set as the latest
release* flag, which makes it the only authority on a repository that restarted its numbering. It
is also a second request per repository per refresh, so `plan.shouldConsultForgeLatest` spends it
on exactly two shapes: no stable release in the window at all, and a highest version number that
reads as one a rename left behind. `GitHubReleaseSelectionEndToEndTest` counts the requests.

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
and a preview line gone quiet — are recorded by the evaluator as `preReleaseRejection`. Between the
two lists, every release the repository published and the card does not show is accounted for.

Only the parts that change what a reader would do reach the card, via
`GitHubReleaseDecisionNote`: a restarted project's release, a choice made on time rather than
number, and a retired preview line. Everything else stays in the record.

## Fixtures

`scripts/qa/capture_release_fixture.sh <owner/repo>` fetches and projects to exactly the fields
`parseReleaseEntry` reads. That projection is the only written-down copy of the field list; the two
corpora here were hand-trimmed before it existed, and one of them
(`stratumauth-releases.json`) was captured before the parser read asset lists, so every release in
it claimed to have nothing attached and silently switched off the installability rule for the whole
corpus. Assets were backfilled and the corpus now asserts they are present.

Each corpus pins `nowMillis` to its capture date. A fixture judged against the day the test runs
starts failing for no reason but the calendar.
