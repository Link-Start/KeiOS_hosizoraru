package os.kei.core.versioning

object ReleaseCandidateRanker {
    fun compare(
        left: ReleaseRankingEvidence,
        right: ReleaseRankingEvidence,
    ): Int {
        val versionComparison = VersioningEngine.compareRemoteCandidateSets(
            leftCandidates = left.versionCandidates,
            rightCandidates = right.versionCandidates,
        )
        if (
            versionComparison != null &&
            versionComparison.order != VersionOrder.Same &&
            versionComparison.confidence != VersionConfidence.Low
        ) {
            return versionComparison.order.legacyValue
        }

        val publishedComparison = compareValues(
            left.freshnessMillis ?: Long.MIN_VALUE,
            right.freshnessMillis ?: Long.MIN_VALUE,
        )
        if (publishedComparison != 0) return publishedComparison

        if (versionComparison != null && versionComparison.order != VersionOrder.Same) {
            return versionComparison.order.legacyValue
        }

        return 0
    }

    /**
     * The newest release in [candidates], by version, unless the project has restarted its numbering.
     *
     * [compare] ranks by version and only consults [ReleaseRankingEvidence.freshnessMillis] when the
     * versions cannot be ordered. That is the right default and it has one failure mode: a project that renames or
     * rewrites itself and starts again from a lower number. Its old high tags then outrank everything
     * it has shipped since, permanently — the highest number is a release from years ago, and no
     * future release can ever beat it, so the track silently stops reporting updates rather than
     * reporting a wrong one loudly.
     *
     * Reported against `stratumauth/app`, which shipped up to `1.25.2` as Authenticator Pro in June
     * 2024, was rebranded, and has released `v1.0.1` through `v1.6.2` since. Every strategy picked
     * `1.25.2`; `v1.6.2` is what GitHub's own `releases/latest` returns.
     *
     * A single pairwise rule cannot see this — one comparison of `1.25.2` against `v1.6.2` is
     * indistinguishable from a maintenance release on an old branch, which is a case where the old
     * high number is genuinely still the newest software. The difference is only visible across the
     * whole list, which is why this takes one, and why the decision lives here rather than inside
     * [compare], where a non-transitive rule would corrupt every sort that uses it.
     */
    fun pickLatest(candidates: List<ReleaseRankingEvidence>): ReleaseRankingEvidence? =
        when (val suspicion = suspectVersioningReset(candidates)) {
            null -> candidates.reduceOrNull { best, next -> if (compare(best, next) < 0) next else best }
            else -> suspicion.newest
        }

    /**
     * What [pickLatest] would override, and why — `null` when the list reads normally.
     *
     * Split out because the caller that can settle it authoritatively should not have to pay for the
     * answer on every repository. A source with a "which release is current" endpoint — GitHub's
     * `releases/latest`, which honours the maintainer's own *Set as the latest release* flag rather
     * than merely sorting by date — can spend a request **only** when this returns non-null, and
     * keep the common path at zero extra network. A source without one acts on the suspicion alone,
     * which is what [pickLatest] does.
     */
    fun suspectVersioningReset(candidates: List<ReleaseRankingEvidence>): VersioningResetSuspicion? {
        val byVersion = candidates.reduceOrNull { best, next ->
            if (compare(best, next) < 0) next else best
        } ?: return null
        val newest = candidates.maxByOrNull { it.freshnessMillis ?: Long.MIN_VALUE } ?: return null
        if (newest === byVersion) return null
        if (!isVersioningReset(byVersion = byVersion, newest = newest, candidates = candidates)) return null
        return VersioningResetSuspicion(outranking = byVersion, newest = newest)
    }

    /**
     * Whether the releases published after [byVersion] read as a restart rather than as a backport.
     *
     * Deliberately hard to trigger, because a false positive hands the user an *older* build while
     * claiming it is newer — worse than the bug it fixes. Three things have to hold at once:
     *
     *  - **A long silence.** The version winner predates the newest release by [RESET_MIN_GAP_DAYS],
     *    measured on [ReleaseRankingEvidence.freshnessMillis].
     *    A backport lands weeks after the release it backports from, not months.
     *  - **A sustained run.** At least [RESET_MIN_RUN] releases have been published since, so one
     *    stray maintenance tag cannot decide this. `stratumauth/app` has fifteen.
     *  - **No comeback.** Not one of those later releases outranks or matches the version winner. A
     *    project still shipping on the high line would have produced one.
     *
     * The run is counted from confident comparisons only, while the veto in the third point accepts
     * any comparison it can make. Tags that cannot be ordered at all therefore neither prove a reset
     * nor prevent one, which is the right way round: unreadable tags are common, and they are not
     * evidence of anything.
     */
    private fun isVersioningReset(
        byVersion: ReleaseRankingEvidence,
        newest: ReleaseRankingEvidence,
        candidates: List<ReleaseRankingEvidence>,
    ): Boolean {
        val winnerPublishedAt = byVersion.freshnessMillis ?: return false
        val newestPublishedAt = newest.freshnessMillis ?: return false
        if (newestPublishedAt - winnerPublishedAt < RESET_MIN_GAP_MILLIS) return false

        val publishedSince = candidates.filter { candidate ->
            (candidate.freshnessMillis ?: Long.MIN_VALUE) > winnerPublishedAt
        }
        var confidentlyLower = 0
        publishedSince.forEach { candidate ->
            val comparison = VersioningEngine.compareRemoteCandidateSets(
                leftCandidates = candidate.versionCandidates,
                rightCandidates = byVersion.versionCandidates,
            ) ?: return@forEach
            if (comparison.order != VersionOrder.Older) return false
            if (comparison.confidence != VersionConfidence.Low) confidentlyLower++
        }
        return confidentlyLower >= RESET_MIN_RUN
    }

    private const val RESET_MIN_RUN = 3
    private const val RESET_MIN_GAP_DAYS = 180L
    private const val RESET_MIN_GAP_MILLIS = RESET_MIN_GAP_DAYS * 24L * 60L * 60L * 1000L
}
