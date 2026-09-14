package os.kei.core.versioning

import java.util.Locale
import kotlin.math.abs

object VersioningEngine {
    private const val DEFAULT_LINK_SOURCE_PRIORITY = 2
    private const val VERSION_NORMALIZATION_CACHE_SIZE = 384
    private const val VERSION_COMPARABLE_CACHE_SIZE = 512
    private const val VERSION_PARTS_CACHE_SIZE = 512

    private val datePrefixedVersionRegex =
        Regex("""^(?:20\d{4}|\d{6,8})[._-]+([vV]?\d+(?:[._-]\d+)+.*)$""")
    private val versionCandidateRegex = Regex(
        """[vV]?\d+(?:[._-]\d+)*(?:\s*[-._ ]?\s*(?:dev|nightly|canary|snapshot|unstable|master|main|develop(?:ment)?|trunk|edge|alpha|beta|rc|preview|pre(?:-?release)?)(?![A-Za-z])(?:\s*[-._ ]?\s*\d+)?)?(?:\+[0-9A-Za-z.-]+)?""",
    )
    private val preReleaseKeywordRegex = Regex("""pre[- ]?release""", RegexOption.IGNORE_CASE)
    private val snapshotKeywordRegex = Regex("""snapshot""", RegexOption.IGNORE_CASE)
    private val nightlyKeywordRegex = Regex("""nightly""", RegexOption.IGNORE_CASE)
    private val canaryKeywordRegex = Regex("""canary""", RegexOption.IGNORE_CASE)
    private val rollingBranchKeywordRegex = Regex(
        """(?<![a-z])(?:master|main|develop(?:ment)?|trunk|edge)(?![a-z])""",
        RegexOption.IGNORE_CASE,
    )
    private val whitespaceRegex = Regex("""\s+""")
    private val separatorCleanupRegex = Regex("""\.\-|\-\.|--""")
    private val coreVersionRegex = Regex("""\d+(?:[._-]\d+)*""")
    private val channelSuffixRegex = Regex(
        """(?:^|[^a-z])(dev|nightly|canary|snapshot|unstable|alpha|beta|rc|preview|pre(?:-?release)?)(?=$|[^a-z])(?:[^a-z0-9]*(\d+)(?=$|[^a-z0-9]))?""",
    )
    private val revisionTokenRegex = Regex(
        """(?:^|[^a-z0-9])(?:fix|build|rev|revision|r|c)[._-]?(\d+)(?![a-z0-9])""",
    )
    private val buildMetadataNumberRegex = Regex("""\+(\d{3,10})(?=$|[^0-9])""")

    private val normalizedCandidateCache =
        BoundedVersionCache<String, List<String>>(VERSION_NORMALIZATION_CACHE_SIZE)
    private val comparableCandidateCache =
        BoundedVersionCache<ComparableCandidateKey, ComparableVersionCandidate?>(
            VERSION_COMPARABLE_CACHE_SIZE,
        )
    private val versionPartsCache =
        BoundedVersionCache<String, VersionParts?>(VERSION_PARTS_CACHE_SIZE)

    fun buildCandidates(inputs: Iterable<Pair<Int, String>>): List<VersionCandidate> {
        val dedup = linkedMapOf<String, VersionCandidate>()
        inputs.forEach { (sourcePriority, text) ->
            normalizeCandidates(text).forEach { candidate ->
                val existing = dedup[candidate]
                if (existing == null || sourcePriority < existing.sourcePriority) {
                    dedup[candidate] = VersionCandidate(candidate, sourcePriority)
                }
            }
        }
        return dedup.values.toList()
    }

    fun normalizeCandidates(text: String): List<String> {
        return normalizedCandidateCache.getOrPut(text) {
            normalizeCandidatesUncached(text)
        }
    }

    fun compareLocalVersionToRemote(
        localVersion: String,
        remoteCandidates: List<VersionCandidate>,
    ): VersionComparison? {
        return compareLocalCandidateSets(
            leftCandidates = normalizeCandidates(localVersion),
            rightCandidates = remoteCandidates,
        )
    }

    fun compareLocalVersionNameAndCodeToRemote(
        localVersion: String,
        localVersionCode: Long,
        remoteCandidates: List<VersionCandidate>,
    ): VersionComparison? {
        val localCandidates = normalizeCandidates(localVersion)
        return compareLocalVersionCodeEvidence(
            localCandidates = localCandidates,
            localVersionCode = localVersionCode,
            remoteCandidates = remoteCandidates,
        ) ?: compareLocalCandidateSets(
            leftCandidates = localCandidates,
            rightCandidates = remoteCandidates,
        )
    }

    fun compareLocalCandidateSets(
        leftCandidates: List<String>,
        rightCandidates: List<VersionCandidate>,
    ): VersionComparison? {
        val left = parseComparableLocalCandidates(leftCandidates)
        val right = parseComparableRemoteCandidates(preferredSourceCandidates(rightCandidates))
        if (left.isEmpty() || right.isEmpty()) return null

        var bestComparison: VersionComparison? = null
        var bestScore = Int.MIN_VALUE
        for (local in left) {
            for (remote in right) {
                val comparison = compareCandidates(local, remote)
                val score = similarityScore(local, remote)
                if (comparison.order == VersionOrder.Same && score >= bestScore) {
                    bestComparison = comparison
                    bestScore = score
                    continue
                }
                if (score > bestScore) {
                    bestComparison = comparison
                    bestScore = score
                }
            }
        }
        return bestComparison
    }

    fun compareRemoteCandidateSets(
        leftCandidates: List<VersionCandidate>,
        rightCandidates: List<VersionCandidate>,
    ): VersionComparison? {
        val left = selectReleaseRankingCandidate(leftCandidates) ?: return null
        val right = selectReleaseRankingCandidate(rightCandidates) ?: return null
        return compareCandidates(
            left = left,
            right = right,
            reason = VersionComparisonReason.ReleaseRanking,
        )
    }

    fun remoteCandidateMatchesLocalVersionNameAndCode(
        localVersion: String,
        localVersionCode: Long,
        remoteCandidates: List<VersionCandidate>,
    ): Boolean {
        return compareLocalVersionCodeEvidence(
            localCandidates = normalizeCandidates(localVersion),
            localVersionCode = localVersionCode,
            remoteCandidates = remoteCandidates,
        )?.order == VersionOrder.Same
    }

    fun referToSameReleaseVersion(
        leftCandidates: List<VersionCandidate>,
        rightCandidates: List<VersionCandidate>,
        maxSourcePriority: Int = DEFAULT_LINK_SOURCE_PRIORITY,
    ): Boolean {
        val left = releaseIdentityKeys(leftCandidates, maxSourcePriority)
        val right = releaseIdentityKeys(rightCandidates, maxSourcePriority)
        if (left.isEmpty() || right.isEmpty()) return false
        return left.any(right::contains)
    }

    fun releaseIdentityKey(
        candidates: List<VersionCandidate>,
        maxSourcePriority: Int = DEFAULT_LINK_SOURCE_PRIORITY,
    ): String? {
        val preferred = candidates.filter { candidate ->
            candidate.sourcePriority <= maxSourcePriority
        }
        val selected = selectReleaseRankingCandidate(preferred.ifEmpty { candidates })
            ?: return null
        return selected.parts
            .takeIf(::isMeaningfulReleaseIdentity)
            ?.let(::versionPartsIdentityKey)
    }

    fun hasComparableCandidates(
        candidates: List<VersionCandidate>,
        maxSourcePriority: Int = DEFAULT_LINK_SOURCE_PRIORITY,
    ): Boolean {
        return candidates.any { candidate ->
            candidate.sourcePriority <= maxSourcePriority &&
                normalizeCandidates(candidate.value).any { normalized ->
                    parseVersionParts(normalized) != null
                }
        }
    }

    fun hasMeaningfulPreReleaseCandidates(
        candidates: List<VersionCandidate>,
        maxSourcePriority: Int = DEFAULT_LINK_SOURCE_PRIORITY,
    ): Boolean {
        return candidates.any { candidate ->
            if (candidate.sourcePriority > maxSourcePriority) return@any false
            normalizeCandidates(candidate.value).any { normalized ->
                val parts = parseVersionParts(normalized) ?: return@any false
                parts.numbers.size >= 2 ||
                    (parts.channel.isPreRelease && parts.channelNumber > 0L)
            }
        }
    }

    /**
     * Whether a pre-release is still worth putting in front of the reader beside [stableCandidates].
     *
     * Both clocks are *freshness*, not publish dates — [ReleaseRankingEvidence.freshnessMillis] —
     * so a rolling tag is judged by when its artifacts last moved rather than by when its tag was
     * first cut. Passing publish dates here reintroduces the split this pipeline used to have.
     */
    fun isRelevantPreRelease(
        preReleaseCandidates: List<VersionCandidate>,
        stableCandidates: List<VersionCandidate>,
        preReleaseFreshnessMillis: Long? = null,
        stableFreshnessMillis: Long? = null,
        /**
         * How far apart the two clocks must be before the gap is evidence of anything.
         *
         * Zero for a source reporting real event times. A source reporting *edit* times needs a
         * window, because publishing a release edits the pre-release it supersedes, and the two then
         * land seconds apart in whichever order the writes committed. Inside the window the version
         * numbers decide and the clock says nothing, which is the honest reading of a clock that
         * cannot tell those two events apart.
         */
        clockToleranceMillis: Long = 0L,
    ): Boolean {
        val preRelease = selectReleaseRankingCandidate(preReleaseCandidates)
        val stable = selectReleaseRankingCandidate(stableCandidates)
        val comparison = if (preRelease != null && stable != null) {
            compareCandidates(
                left = preRelease,
                right = stable,
                reason = VersionComparisonReason.ReleaseRanking,
            )
        } else {
            null
        }
        val preReleaseIsRollingSibling = preRelease != null && stable != null &&
            preRelease.parts.numbers == stable.parts.numbers &&
            preRelease.isRollingDevelopmentCandidate()
        val tolerance = clockToleranceMillis.coerceAtLeast(0L)
        val preReleaseIsNewerByTime = preReleaseFreshnessMillis != null &&
            stableFreshnessMillis != null &&
            preReleaseFreshnessMillis - stableFreshnessMillis > tolerance
        // A pre-release *of* a version that has since shipped is spent, whatever its numbers say.
        //
        // `pre-1.4.2-20260202-1` parses as 1.4.2 with a build stamp appended, so it compares as newer
        // than `1.4.2` even though semver puts a pre-release before the release it precedes. Once
        // 1.4.2 itself is out — a week later, in the case this was written for — the preview is
        // behind the stable the user already has, and offering it is offering a downgrade.
        //
        // Narrow on purpose. It needs the pre-release's numbers to *begin with* the stable's whole
        // number, which is what makes it a pre-release of that exact version: `2.0.0-beta` against a
        // later `1.9.9` shares no such prefix and stays relevant, and so does `1.4.7-prerelease3`
        // against `1.4.4`. And it needs the pre-release to be no newer in time, so a rolling nightly
        // that keeps building past its stable is untouched — that case is the branch below.
        val preReleaseSupersededByStable = preRelease != null && stable != null &&
            preRelease.parts.channel.isPreRelease &&
            stable.parts.numbers.isNotEmpty() &&
            preRelease.parts.numbers.size > stable.parts.numbers.size &&
            preRelease.parts.numbers.take(stable.parts.numbers.size) == stable.parts.numbers &&
            preReleaseFreshnessMillis != null &&
            stableFreshnessMillis != null &&
            preReleaseFreshnessMillis - stableFreshnessMillis <= tolerance
        return when {
            preReleaseSupersededByStable -> false

            comparison != null && comparison.order != VersionOrder.Same ->
                comparison.order == VersionOrder.Newer ||
                    (preReleaseIsRollingSibling && preReleaseIsNewerByTime)

            preReleaseFreshnessMillis != null && stableFreshnessMillis != null ->
                preReleaseIsNewerByTime

            else ->
                (preReleaseFreshnessMillis ?: Long.MIN_VALUE) >
                    (stableFreshnessMillis ?: Long.MIN_VALUE)
        }
    }

    /**
     * Whether a preview line has gone quiet long enough to stop showing it.
     *
     * A policy about time rather than about versions, which is why it takes the clock instead of
     * reading it: a rule that calls `System.currentTimeMillis()` cannot be pinned by a test, and
     * every other decision in this file is.
     *
     * [preReleaseFreshnessMillis] must be the *later* of the release's publish time and its newest
     * asset's update time. Release date alone is the wrong clock: a pre-release used to host CI
     * builds is published once and then only its artifacts move, so by `published_at` such a line
     * reads as years dead while it is still producing builds newer than the stable.
     *
     * Separate from [isRelevantPreRelease], which asks whether a pre-release is *ahead* of the
     * stable. This asks whether anyone is still feeding it. Both have to be false for a preview to
     * earn a row.
     */
    fun isAbandonedPreRelease(
        preReleaseFreshnessMillis: Long?,
        nowMillis: Long,
    ): Boolean =
        preReleaseFreshnessMillis != null &&
            nowMillis - preReleaseFreshnessMillis > ABANDONED_PRE_RELEASE_GAP_MILLIS

    /** @see isRelevantPreRelease */
    private const val ABANDONED_PRE_RELEASE_GAP_DAYS = 14L
    private const val ABANDONED_PRE_RELEASE_GAP_MILLIS =
        ABANDONED_PRE_RELEASE_GAP_DAYS * 24L * 60L * 60L * 1000L

    fun classifyChannel(text: String): VersionChannel? {
        var bestChannel: VersionChannel? = null
        var bestScore = Int.MIN_VALUE
        normalizeCandidates(text).forEach { normalized ->
            val parts = parseVersionParts(normalized) ?: return@forEach
            val score = versionPartsSpecificityScore(parts)
            if (score > bestScore) {
                bestScore = score
                bestChannel = parts.channel
            }
        }
        return bestChannel
    }

    private fun normalizeCandidatesUncached(text: String): List<String> {
        val base = text.trim()
        if (base.isBlank()) return emptyList()
        val tokens = linkedSetOf<String>()

        fun push(candidate: String) {
            val normalized = candidate.trim().lowercase(Locale.ROOT)
            if (normalized.isNotBlank()) tokens += normalized
        }

        fun addCandidate(value: String) {
            val trimmed = value.trim()
                .trim('"', '\'', '(', ')', '[', ']', '{', '}', ',', ';', ':')
            if (trimmed.isBlank()) return
            val canonical = canonicalizeCandidate(trimmed)
            if (canonical.isBlank()) return
            push(canonical)
            push(canonical.removePrefix("v"))
            val withoutBuild = canonical.substringBefore('+')
            if (withoutBuild != canonical) {
                push(withoutBuild)
                push(withoutBuild.removePrefix("v"))
            }
        }

        addCandidate(base)
        datePrefixedVersionRegex
            .matchEntire(base)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::addCandidate)
        versionCandidateRegex.findAll(base).forEach { addCandidate(it.value) }
        return filterLessSpecificCandidates(tokens.toList())
    }

    private fun preferredSourceCandidates(
        candidates: List<VersionCandidate>,
    ): List<VersionCandidate> {
        val preferred = candidates.filter { it.sourcePriority <= DEFAULT_LINK_SOURCE_PRIORITY }
        return preferred.ifEmpty { candidates }
    }

    private fun parseComparableLocalCandidates(
        candidates: List<String>,
    ): List<ComparableVersionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        val parsed = ArrayList<ComparableVersionCandidate>()
        candidates.forEach { candidate ->
            normalizeCandidates(candidate).forEach { normalized ->
                if (seen.add(normalized)) {
                    parseComparableCandidate(
                        raw = normalized,
                        sourcePriority = 0,
                        channelHint = null,
                    )?.let(parsed::add)
                }
            }
        }
        return parsed
    }

    private fun parseComparableRemoteCandidates(
        candidates: List<VersionCandidate>,
    ): List<ComparableVersionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val seen = linkedSetOf<ComparableCandidateKey>()
        val parsed = ArrayList<ComparableVersionCandidate>()
        candidates.forEach { candidate ->
            normalizeCandidates(candidate.value).forEach { normalized ->
                val key = ComparableCandidateKey(
                    raw = normalized,
                    sourcePriority = candidate.sourcePriority,
                    channelHint = candidate.channelHint,
                )
                if (seen.add(key)) {
                    parseComparableCandidate(
                        raw = normalized,
                        sourcePriority = candidate.sourcePriority,
                        channelHint = candidate.channelHint,
                    )?.let(parsed::add)
                }
            }
        }
        return parsed
    }

    private fun compareLocalVersionCodeEvidence(
        localCandidates: List<String>,
        localVersionCode: Long,
        remoteCandidates: List<VersionCandidate>,
    ): VersionComparison? {
        if (localVersionCode < MIN_COMPARABLE_VERSION_CODE) return null
        val local = parseComparableLocalCandidates(localCandidates)
        val remote = parseComparableRemoteCandidates(preferredSourceCandidates(remoteCandidates))
        if (local.isEmpty() || remote.isEmpty()) return null

        var bestMatch: VersionCodeMatch? = null
        for (localCandidate in local) {
            for (remoteCandidate in remote) {
                val evidence = remoteCandidate.buildCodeEvidenceFor(localCandidate.parts.numbers)
                    ?: continue
                if (!versionCodesShareScheme(localVersionCode, evidence.value)) continue
                val score = similarityScore(localCandidate, remoteCandidate) + evidence.quality
                if (bestMatch == null || score > bestMatch.score) {
                    bestMatch = VersionCodeMatch(
                        local = localCandidate,
                        remote = remoteCandidate,
                        remoteVersionCode = evidence.value,
                        score = score,
                    )
                }
            }
        }
        val match = bestMatch ?: return null
        val order = localVersionCode.compareTo(match.remoteVersionCode).toVersionOrder()
        return VersionComparison(
            order = order,
            confidence = if (order == VersionOrder.Same) {
                VersionConfidence.Exact
            } else {
                VersionConfidence.High
            },
            reason = VersionComparisonReason.VersionCode,
            leftEvidence = "${match.local.normalized}+$localVersionCode",
            rightEvidence = "${match.remote.normalized}+${match.remoteVersionCode}",
        )
    }

    private fun ComparableVersionCandidate.buildCodeEvidenceFor(
        localBaseNumbers: List<Long>,
    ): BuildCodeEvidence? {
        if (localBaseNumbers.isEmpty()) return null
        val candidates = buildList {
            if (
                parts.numbers == localBaseNumbers &&
                parts.channel.isPreRelease &&
                parts.channelNumber >= MIN_COMPARABLE_VERSION_CODE
            ) {
                add(BuildCodeEvidence(parts.channelNumber, quality = 520))
            }
            if (parts.numbers == localBaseNumbers) {
                parts.revisionNumbers.lastOrNull()
                    ?.takeIf { it >= MIN_COMPARABLE_VERSION_CODE }
                    ?.let { add(BuildCodeEvidence(it, quality = 480)) }
                buildMetadataNumberRegex.find(normalized)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                    ?.takeIf { it >= MIN_COMPARABLE_VERSION_CODE }
                    ?.let { add(BuildCodeEvidence(it, quality = 460)) }
            }
            if (
                parts.numbers.size == localBaseNumbers.size + 1 &&
                parts.numbers.take(localBaseNumbers.size) == localBaseNumbers
            ) {
                parts.numbers.lastOrNull()
                    ?.takeIf { it >= MIN_COMPARABLE_VERSION_CODE }
                    ?.let { add(BuildCodeEvidence(it, quality = 400)) }
            }
        }
        return candidates.maxByOrNull { evidence -> evidence.quality }
    }

    private fun versionCodesShareScheme(left: Long, right: Long): Boolean {
        if (left < MIN_COMPARABLE_VERSION_CODE || right < MIN_COMPARABLE_VERSION_CODE) {
            return false
        }
        val leftCalendarScheme = left.calendarVersionCodeScheme()
        val rightCalendarScheme = right.calendarVersionCodeScheme()
        if (leftCalendarScheme != null || rightCalendarScheme != null) {
            return leftCalendarScheme == rightCalendarScheme
        }
        return left.toString().length == right.toString().length
    }

    private fun Long.calendarVersionCodeScheme(): CalendarVersionCodeScheme? {
        val digits = toString()
        return when (digits.length) {
            6 -> digits.parseCalendarCode(
                yearRange = 0..1,
                monthRange = 2..3,
                dayRange = 4..5,
                hourRange = null,
                scheme = CalendarVersionCodeScheme.YYMMDD,
            )
            8 -> digits.parseCalendarCode(
                yearRange = 0..3,
                monthRange = 4..5,
                dayRange = 6..7,
                hourRange = null,
                scheme = CalendarVersionCodeScheme.YYYYMMDD,
            ) ?: digits.parseCalendarCode(
                yearRange = 0..1,
                monthRange = 2..3,
                dayRange = 4..5,
                hourRange = 6..7,
                scheme = CalendarVersionCodeScheme.YYMMDDHH,
            )
            10 -> digits.parseCalendarCode(
                yearRange = 0..3,
                monthRange = 4..5,
                dayRange = 6..7,
                hourRange = 8..9,
                scheme = CalendarVersionCodeScheme.YYYYMMDDHH,
            )
            else -> null
        }
    }

    private fun String.parseCalendarCode(
        yearRange: IntRange,
        monthRange: IntRange,
        dayRange: IntRange,
        hourRange: IntRange?,
        scheme: CalendarVersionCodeScheme,
    ): CalendarVersionCodeScheme? {
        val year = substring(yearRange).toIntOrNull() ?: return null
        val fullYear = if (yearRange.last - yearRange.first == 1) {
            if (year < MIN_SHORT_CALENDAR_YEAR) return null
            2000 + year
        } else {
            year
        }
        if (fullYear !in MIN_FULL_CALENDAR_YEAR..MAX_FULL_CALENDAR_YEAR) return null
        val month = substring(monthRange).toIntOrNull() ?: return null
        val day = substring(dayRange).toIntOrNull() ?: return null
        if (!isValidCalendarDate(fullYear, month, day)) return null
        val hour = hourRange?.let { substring(it).toIntOrNull() ?: return null }
        if (hour != null && hour !in 0..23) return null
        return scheme
    }

    private fun isValidCalendarDate(year: Int, month: Int, day: Int): Boolean {
        if (month !in 1..12 || day < 1) return false
        val maxDay = when (month) {
            2 -> if (year.isLeapYear()) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= maxDay
    }

    private fun Int.isLeapYear(): Boolean {
        return this % 400 == 0 || (this % 4 == 0 && this % 100 != 0)
    }

    private fun selectReleaseRankingCandidate(
        candidates: List<VersionCandidate>,
    ): ComparableVersionCandidate? {
        return parseComparableRemoteCandidates(preferredSourceCandidates(candidates))
            .maxWithOrNull(
                compareBy<ComparableVersionCandidate> { releaseCandidateQualityScore(it) }
                    .thenBy { it.normalized },
            )
    }

    private fun releaseCandidateQualityScore(candidate: ComparableVersionCandidate): Int {
        val datePenalty = when {
            candidate.looksLikeDatePrefixedSemantic -> 520
            candidate.looksLikeDateStamp -> 420
            else -> 0
        }
        val revisionAliasPenalty = if (candidate.looksLikeRevisionOnlyAlias) 220 else 0
        return sourceReliabilityBonus(candidate.sourcePriority) +
            candidate.semanticDepth * 70 +
            candidate.parts.revisionNumbers.size * 15 -
            datePenalty -
            revisionAliasPenalty
    }

    private fun canonicalizeCandidate(raw: String): String {
        return raw
            .replace(preReleaseKeywordRegex, "preview")
            .replace(snapshotKeywordRegex, "dev")
            .replace(nightlyKeywordRegex, "dev")
            .replace(canaryKeywordRegex, "dev")
            .replace(rollingBranchKeywordRegex, "dev")
            .replace('_', '.')
            .replace(whitespaceRegex, "")
            .replace(separatorCleanupRegex, "-")
    }

    private fun filterLessSpecificCandidates(candidates: List<String>): List<String> {
        if (candidates.size <= 1) return candidates
        val parsedCandidates = candidates.map { candidate -> candidate to parseVersionParts(candidate) }
        val richerKeys = linkedSetOf<List<Long>>()
        parsedCandidates.forEach { (_, parts) ->
            if (
                parts != null &&
                (parts.channel != VersionChannel.STABLE ||
                    parts.channelNumber > 0L ||
                    parts.revisionNumbers.isNotEmpty())
            ) {
                richerKeys += parts.numbers
            }
        }
        return buildList {
            parsedCandidates.forEach { (candidate, parts) ->
                if (parts == null) {
                    add(candidate)
                } else {
                    val truncatedStable =
                        parts.channel == VersionChannel.STABLE &&
                            parts.channelNumber == 0L &&
                            parts.revisionNumbers.isEmpty() &&
                            parts.numbers in richerKeys
                    if (!truncatedStable) add(candidate)
                }
            }
        }.distinct()
    }

    private fun parseComparableCandidate(
        raw: String,
        sourcePriority: Int,
        channelHint: VersionChannel?,
    ): ComparableVersionCandidate? {
        return comparableCandidateCache.getOrPut(
            ComparableCandidateKey(raw, sourcePriority, channelHint),
        ) {
            val normalized = canonicalizeCandidate(raw).lowercase(Locale.ROOT)
            val parsedParts = parseVersionParts(normalized) ?: return@getOrPut null
            val parts = parsedParts.withChannelHint(channelHint)
            ComparableVersionCandidate(
                normalized = normalized,
                parts = parts,
                sourcePriority = sourcePriority,
                semanticDepth = parts.numbers.size +
                    if (parts.channel != VersionChannel.STABLE) 1 else 0,
                looksLikeDateStamp = parts.numbers.size == 1 && parts.numbers.first().isDateStamp(),
                looksLikeDatePrefixedSemantic =
                    parts.numbers.size >= 3 && parts.numbers.first().isDateStamp(),
                looksLikeRevisionOnlyAlias =
                    parts.numbers.size == 1 && revisionTokenRegex.containsMatchIn(normalized),
                channelWasHinted = parts.channel != parsedParts.channel,
            )
        }
    }

    private fun VersionParts.withChannelHint(channelHint: VersionChannel?): VersionParts {
        val hint = channelHint?.takeUnless { it == VersionChannel.UNKNOWN } ?: return this
        return when {
            hint == VersionChannel.STABLE -> copy(
                channel = VersionChannel.STABLE,
                channelNumber = 0L,
            )
            channel == VersionChannel.STABLE || channel == VersionChannel.UNKNOWN ->
                copy(channel = hint)
            else -> this
        }
    }

    private fun compareCandidates(
        left: ComparableVersionCandidate,
        right: ComparableVersionCandidate,
        reason: VersionComparisonReason = VersionComparisonReason.SemanticVersion,
    ): VersionComparison {
        val comparison = compareParsedVersionParts(left.parts, right.parts)
        val order = comparison.toVersionOrder()
        val exact = left.normalized == right.normalized
        val sharedPrefix = sharedNumericPrefix(left.parts.numbers, right.parts.numbers)
        val confidence = when {
            exact -> VersionConfidence.Exact
            sharedPrefix >= 2 -> VersionConfidence.High
            sharedPrefix == 1 -> VersionConfidence.Medium
            reason == VersionComparisonReason.ReleaseRanking &&
                left.isStructuredReleaseCandidate() &&
                right.isStructuredReleaseCandidate() -> VersionConfidence.Medium
            else -> VersionConfidence.Low
        }
        return VersionComparison(
            order = order,
            confidence = confidence,
            reason = if (exact) VersionComparisonReason.ExactCandidate else reason,
            leftEvidence = left.normalized,
            rightEvidence = right.normalized,
        )
    }

    private fun similarityScore(
        left: ComparableVersionCandidate,
        right: ComparableVersionCandidate,
    ): Int {
        val sameRawBonus = if (left.normalized == right.normalized) 180 else 0
        val sharedNumericPrefix = sharedNumericPrefix(left.parts.numbers, right.parts.numbers)
        val sameNumericLengthBonus = if (left.parts.numbers.size == right.parts.numbers.size) 30 else 0
        val sameChannelBonus = if (left.parts.channel == right.parts.channel) 50 else 0
        val sameChannelNumberBonus = if (left.parts.channelNumber == right.parts.channelNumber) 25 else 0
        val sameRevisionBonus = if (left.parts.revisionNumbers == right.parts.revisionNumbers) 25 else 0
        val sourceBonus = sourceReliabilityBonus(right.sourcePriority)
        val semanticDepthBonus = right.semanticDepth * 70
        val numericLengthPenalty = abs(left.parts.numbers.size - right.parts.numbers.size) * 10
        val channelNumberPenalty = abs(left.parts.channelNumber - right.parts.channelNumber)
            .coerceAtMost(20L)
            .toInt() * 6
        val dateStampPenalty = if (right.looksLikeDateStamp && left.parts.numbers.size >= 2) 420 else 0
        val datePrefixPenalty = if (right.looksLikeDatePrefixedSemantic && !left.looksLikeDatePrefixedSemantic) 520 else 0
        return sameRawBonus +
            sharedNumericPrefix * 160 +
            sameNumericLengthBonus +
            sameChannelBonus +
            sameChannelNumberBonus +
            sameRevisionBonus +
            sourceBonus +
            semanticDepthBonus -
            numericLengthPenalty -
            channelNumberPenalty -
            dateStampPenalty -
            datePrefixPenalty
    }

    private fun sourceReliabilityBonus(sourcePriority: Int): Int {
        return when (sourcePriority) {
            0 -> 520
            1 -> 420
            2 -> 280
            3 -> 160
            else -> 40
        }
    }

    private fun ComparableVersionCandidate.isStructuredReleaseCandidate(): Boolean {
        return semanticDepth >= 2 &&
            !looksLikeDateStamp &&
            !looksLikeDatePrefixedSemantic &&
            !looksLikeRevisionOnlyAlias
    }

    private fun ComparableVersionCandidate.isRollingDevelopmentCandidate(): Boolean {
        return parts.channel == VersionChannel.DEV ||
            (channelWasHinted && parts.channel.isPreRelease)
    }

    private fun versionPartsSpecificityScore(parts: VersionParts): Int {
        val channelBonus = if (parts.channel != VersionChannel.STABLE) 100 else 0
        return channelBonus +
            parts.numbers.size * 10 +
            parts.revisionNumbers.size * 5 +
            parts.channelNumber.coerceAtMost(9L).toInt()
    }

    private fun sharedNumericPrefix(left: List<Long>, right: List<Long>): Int {
        val max = minOf(left.size, right.size)
        var count = 0
        for (index in 0 until max) {
            if (left[index] != right[index]) break
            count++
        }
        return count
    }

    private fun compareParsedVersionParts(left: VersionParts, right: VersionParts): Int {
        val numericComparison = compareLongParts(left.numbers, right.numbers)
        if (numericComparison != 0) return numericComparison

        val channelComparison = channelRank(left.channel).compareTo(channelRank(right.channel))
        if (channelComparison != 0) return channelComparison

        val channelNumberComparison = left.channelNumber.compareTo(right.channelNumber)
        if (channelNumberComparison != 0) return channelNumberComparison

        if (left.revisionNumbers.isNotEmpty() || right.revisionNumbers.isNotEmpty()) {
            return compareLongParts(left.revisionNumbers, right.revisionNumbers)
        }
        return 0
    }

    private fun compareLongParts(left: List<Long>, right: List<Long>): Int {
        val max = maxOf(left.size, right.size)
        for (index in 0 until max) {
            val leftPart = left.getOrElse(index) { 0L }
            val rightPart = right.getOrElse(index) { 0L }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }

    private fun channelRank(channel: VersionChannel): Int {
        return when (channel) {
            VersionChannel.DEV -> 0
            VersionChannel.ALPHA -> 1
            VersionChannel.BETA -> 2
            VersionChannel.RC -> 3
            VersionChannel.PREVIEW -> 4
            VersionChannel.STABLE,
            VersionChannel.UNKNOWN,
            -> 5
        }
    }

    private fun releaseIdentityKeys(
        candidates: List<VersionCandidate>,
        maxSourcePriority: Int,
    ): Set<String> {
        val keys = linkedSetOf<String>()
        candidates.forEach { candidate ->
            if (candidate.sourcePriority <= maxSourcePriority) {
                normalizeCandidates(candidate.value).forEach { normalized ->
                    val parts = parseVersionParts(normalized)
                        ?.withChannelHint(candidate.channelHint)
                        ?: return@forEach
                    if (isMeaningfulReleaseIdentity(parts)) keys += versionPartsIdentityKey(parts)
                }
            }
        }
        return keys
    }

    private fun isMeaningfulReleaseIdentity(parts: VersionParts): Boolean {
        return parts.numbers.size >= 2 ||
            (parts.channel.isPreRelease && parts.channelNumber > 0L)
    }

    private fun versionPartsIdentityKey(parts: VersionParts): String {
        return buildString {
            append(parts.numbers.joinToString("."))
            append('|')
            append(parts.channel.name)
            append('|')
            append(parts.channelNumber)
            append('|')
            append(parts.revisionNumbers.joinToString("."))
        }
    }

    private fun parseVersionParts(raw: String): VersionParts? {
        return versionPartsCache.getOrPut(raw) {
            val source = raw.trim().lowercase(Locale.ROOT)
            if (source.isBlank()) return@getOrPut null
            val normalized = source.removePrefix("v")
            val coreMatch = coreVersionRegex.find(normalized) ?: return@getOrPut null
            val coreNumbers = coreMatch.value
                .removeTrailingHyphenatedReleaseDate()
                .split('.', '_', '-')
                .mapNotNull { it.toLongOrNull() }
            if (coreNumbers.isEmpty()) return@getOrPut null
            val suffix = normalized.substring(coreMatch.range.last + 1)
            val channelMatch = channelSuffixRegex.find(suffix.ifBlank { normalized })
            val channel = when (channelMatch?.groupValues?.getOrNull(1).orEmpty()) {
                "dev", "nightly", "canary", "snapshot", "unstable" -> VersionChannel.DEV
                "alpha" -> VersionChannel.ALPHA
                "beta" -> VersionChannel.BETA
                "rc" -> VersionChannel.RC
                "preview", "pre", "prerelease", "pre-release" -> VersionChannel.PREVIEW
                else -> VersionChannel.STABLE
            }
            val channelNumber = channelMatch
                ?.groupValues
                ?.getOrNull(2)
                ?.toLongOrNull()
                ?: 0L
            val revisionNumbers = revisionTokenRegex
                .findAll(suffix)
                .mapNotNull { match -> match.groupValues.getOrNull(1)?.toLongOrNull() }
                .toList()
            VersionParts(
                numbers = coreNumbers,
                channel = channel,
                channelNumber = channelNumber,
                revisionNumbers = revisionNumbers,
            )
        }
    }

    private fun Long.isDateStamp(): Boolean {
        return this in 200_000L..299_999L || this in 20_000_000L..29_999_999L
    }

    private fun String.removeTrailingHyphenatedReleaseDate(): String {
        val separatorIndex = lastIndexOf('-')
        if (separatorIndex <= 0) return this
        val suffix = substring(separatorIndex + 1).toLongOrNull() ?: return this
        return if (suffix.isDateStamp()) substring(0, separatorIndex) else this
    }

    private fun Int.toVersionOrder(): VersionOrder {
        return when {
            this < 0 -> VersionOrder.Older
            this > 0 -> VersionOrder.Newer
            else -> VersionOrder.Same
        }
    }

    private data class ComparableVersionCandidate(
        val normalized: String,
        val parts: VersionParts,
        val sourcePriority: Int,
        val semanticDepth: Int,
        val looksLikeDateStamp: Boolean,
        val looksLikeDatePrefixedSemantic: Boolean,
        val looksLikeRevisionOnlyAlias: Boolean,
        val channelWasHinted: Boolean,
    )

    private data class ComparableCandidateKey(
        val raw: String,
        val sourcePriority: Int,
        val channelHint: VersionChannel?,
    )

    private data class BuildCodeEvidence(
        val value: Long,
        val quality: Int,
    )

    private data class VersionCodeMatch(
        val local: ComparableVersionCandidate,
        val remote: ComparableVersionCandidate,
        val remoteVersionCode: Long,
        val score: Int,
    )

    private data class VersionParts(
        val numbers: List<Long>,
        val channel: VersionChannel,
        val channelNumber: Long,
        val revisionNumbers: List<Long>,
    )

    private enum class CalendarVersionCodeScheme {
        YYMMDD,
        YYMMDDHH,
        YYYYMMDD,
        YYYYMMDDHH,
    }

    private class BoundedVersionCache<K, V>(
        private val maxSize: Int,
    ) {
        private val values = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        fun getOrPut(key: K, createValue: () -> V): V {
            synchronized(values) {
                if (values.containsKey(key)) {
                    @Suppress("UNCHECKED_CAST")
                    return values[key] as V
                }
            }
            val created = createValue()
            synchronized(values) {
                if (values.containsKey(key)) {
                    @Suppress("UNCHECKED_CAST")
                    return values[key] as V
                }
                values[key] = created
                return created
            }
        }
    }

    private const val MIN_COMPARABLE_VERSION_CODE = 100L
    private const val MIN_SHORT_CALENDAR_YEAR = 20
    private const val MIN_FULL_CALENDAR_YEAR = 2000
    private const val MAX_FULL_CALENDAR_YEAR = 2099
}
