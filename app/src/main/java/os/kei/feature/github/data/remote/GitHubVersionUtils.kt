package os.kei.feature.github.data.remote

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import os.kei.core.system.HyperOsSettingsIntents
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubVersionCandidate
import os.kei.feature.github.model.GitHubVersionCandidateSource
import os.kei.feature.github.model.InstalledAppItem
import java.net.URI
import java.util.Locale
import kotlin.math.abs

object GitHubVersionUtils {
    private const val INSTALLED_APPS_CACHE_TTL_MS = 5L * 60L * 1000L
    private const val VERSION_NORMALIZATION_CACHE_SIZE = 384
    private const val VERSION_COMPARABLE_CACHE_SIZE = 512
    private const val VERSION_PARTS_CACHE_SIZE = 512

    private val datePrefixedVersionRegex =
        Regex("""^(?:20\d{4}|\d{6,8})[._-]+([vV]?\d+(?:[._-]\d+)+.*)$""")
    private val versionCandidateRegex = Regex(
        """[vV]?\d+(?:[._-]\d+)*(?:\s*[-._ ]?\s*(?:dev|nightly|canary|snapshot|alpha|beta|rc|preview|pre(?:-release)?)(?:\s*[-._ ]?\s*\d+)?)?(?:\+[0-9A-Za-z.-]+)?"""
    )
    private val preReleaseKeywordRegex = Regex("""pre[- ]release""", RegexOption.IGNORE_CASE)
    private val snapshotKeywordRegex = Regex("""snapshot""", RegexOption.IGNORE_CASE)
    private val nightlyKeywordRegex = Regex("""nightly""", RegexOption.IGNORE_CASE)
    private val canaryKeywordRegex = Regex("""canary""", RegexOption.IGNORE_CASE)
    private val whitespaceRegex = Regex("""\s+""")
    private val separatorCleanupRegex = Regex("""\.\-|\-\.|--""")
    private val coreVersionRegex = Regex("""\d+(?:[._]\d+)*""")
    private val channelSuffixRegex = Regex(
        """(?:^|[^a-z])(dev|nightly|canary|snapshot|alpha|beta|rc|preview|pre(?:-release)?)(?:[^a-z0-9]*(\d+))?"""
    )

    @Volatile
    private var installedAppsCache: CachedInstalledApps? = null

    private val normalizedCandidateCache =
        BoundedVersionCache<String, List<String>>(VERSION_NORMALIZATION_CACHE_SIZE)
    private val comparableCandidateCache =
        BoundedVersionCache<ComparableCandidateKey, ComparableVersionCandidate?>(
            VERSION_COMPARABLE_CACHE_SIZE
        )
    private val versionPartsCache =
        BoundedVersionCache<String, VersionParts?>(VERSION_PARTS_CACHE_SIZE)

    private data class CachedInstalledApps(
        val updatedAtMs: Long,
        val apps: List<InstalledAppItem>
    )

    private data class InstalledAppSortEntry(
        val item: InstalledAppItem,
        val labelSortKey: String,
        val packageSortKey: String
    )

    data class LocalVersionInfo(
        val versionName: String,
        val versionCode: Long
    )

    fun buildRepositoryUrl(owner: String, repo: String): String {
        return "https://github.com/$owner/$repo"
    }

    fun buildReleaseUrl(owner: String, repo: String): String {
        return "${buildRepositoryUrl(owner, repo)}/releases"
    }

    fun buildReleaseTagUrl(owner: String, repo: String, tag: String): String {
        val normalized = tag.trim()
        if (normalized.isBlank()) return buildReleaseUrl(owner, repo)
        val encodedTag = java.net.URLEncoder.encode(normalized, Charsets.UTF_8.name())
            .replace("+", "%20")
        return "https://github.com/$owner/$repo/releases/tag/$encodedTag"
    }

    fun buildAppListPermissionIntent(context: Context): Intent? {
        return HyperOsSettingsIntents.buildAppListPermissionIntent(context)
    }

    fun queryInstalledLaunchableApps(
        context: Context,
        forceRefresh: Boolean = false,
        ttlMs: Long = INSTALLED_APPS_CACHE_TTL_MS
    ): List<InstalledAppItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            installedAppsCache?.takeIf { cache ->
                (now - cache.updatedAtMs).coerceAtLeast(0L) < ttlMs.coerceAtLeast(0L)
            }?.let { return it.apps }
        }

        val pm = context.packageManager
        val overlayFlagMask = installedAppResourceOverlayFlagMask()
        val installSourceLabelCache = mutableMapOf<String, String>()
        val labelSortLocale = Locale.getDefault()
        val apps = pm.queryInstalledPackageInfos()
            .asSequence()
            .mapNotNull { pkgInfo ->
                val packageName = pkgInfo.packageName.trim()
                if (packageName.isBlank()) return@mapNotNull null
                val appInfo = pkgInfo.applicationInfo ?: pm.getApplicationInfoCompat(packageName)
                    ?: return@mapNotNull null
                if (shouldIgnoreInstalledAppForGitHubList(appInfo, overlayFlagMask)) {
                    return@mapNotNull null
                }

                val label = runCatching {
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName).trim().ifBlank { packageName }
                val installSource = pm.resolveInstallSource(packageName, installSourceLabelCache)
                val item = InstalledAppItem(
                    label = label,
                    packageName = packageName,
                    firstInstallTimeMs = pkgInfo.firstInstallTime,
                    lastUpdateTimeMs = pkgInfo.lastUpdateTime,
                    isSystemApp = appInfo.isSystemAppForGitHubPicker(),
                    installSourcePackageName = installSource.packageName,
                    installSourceLabel = installSource.label
                )
                InstalledAppSortEntry(
                    item = item,
                    labelSortKey = label.lowercase(labelSortLocale),
                    packageSortKey = packageName.lowercase(Locale.ROOT)
                )
            }
            .distinctBy { it.item.packageName }
            .sortedWith(
                compareBy<InstalledAppSortEntry> { it.labelSortKey }
                    .thenBy { it.packageSortKey }
            )
            .map { it.item }
            .toList()
        installedAppsCache = CachedInstalledApps(
            updatedAtMs = now,
            apps = apps
        )
        return apps
    }

    fun invalidateInstalledLaunchableAppsCache() {
        installedAppsCache = null
    }

    fun localVersionName(context: Context, packageName: String): String {
        return localVersionInfoOrNull(context, packageName)?.versionName.orEmpty().ifBlank { "unknown" }
    }

    fun localVersionCode(context: Context, packageName: String): Long {
        return localVersionInfoOrNull(context, packageName)?.versionCode ?: -1L
    }

    fun localVersionInfoOrNull(context: Context, packageName: String): LocalVersionInfo? {
        val pm = context.packageManager
        val pkgInfo = runCatching {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        }.recoverCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }.getOrElse { error ->
            if (error is PackageManager.NameNotFoundException) {
                return null
            }
            throw error
        }
        val versionName = pkgInfo.versionName?.trim().orEmpty().ifBlank { "unknown" }
        val versionCode = pkgInfo.longVersionCode
        return LocalVersionInfo(
            versionName = versionName,
            versionCode = versionCode
        )
    }

    fun parseOwnerRepo(urlOrPath: String): Pair<String, String>? {
        val raw = urlOrPath.trim()
            .removePrefix("git+")
            .removeSuffix(".git")
            .trimEnd('/')
        if (raw.isBlank()) return null

        if (raw.contains(":") && raw.contains("@") && raw.contains("github.com")) {
            val afterColon = raw.substringAfter(':', "")
            val ownerRepo = afterColon.removePrefix("/").split("/")
            if (ownerRepo.size >= 2) return ownerRepo[0] to ownerRepo[1]
        }

        val asUri = runCatching { URI(raw) }.getOrNull()
        if (asUri != null && asUri.host?.contains("github.com", ignoreCase = true) == true) {
            val segments = asUri.path.trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size >= 2) return segments[0] to segments[1].removeSuffix(".git")
        }

        val normalized = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("github.com/")
            .trim('/')
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.size >= 2) return parts[0] to parts[1].removeSuffix(".git")
        return null
    }

    fun buildVersionCandidates(vararg inputs: Pair<GitHubVersionCandidateSource, String>): List<GitHubVersionCandidate> {
        val dedup = linkedMapOf<String, GitHubVersionCandidate>()
        inputs.forEach { (source, text) ->
            normalizeVersionCandidates(text).forEach { candidate ->
                val existing = dedup[candidate]
                if (existing == null || source.priority < existing.source.priority) {
                    dedup[candidate] = GitHubVersionCandidate(candidate, source)
                }
            }
        }
        return dedup.values.toList()
    }

    fun normalizeVersionCandidates(text: String): List<String> {
        return normalizedCandidateCache.getOrPut(text) {
            normalizeVersionCandidatesUncached(text)
        }
    }

    private fun normalizeVersionCandidatesUncached(text: String): List<String> {
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
            push(canonical.removePrefix("V"))

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

    fun compareVersionToCandidates(localVersion: String, candidates: List<String>): Int? {
        return compareCandidateSets(normalizeVersionCandidates(localVersion), candidates)
    }

    fun compareVersionToStructuredCandidates(localVersion: String, candidates: List<GitHubVersionCandidate>): Int? {
        return compareCandidateSetsWithSources(normalizeVersionCandidates(localVersion), candidates)
    }

    fun remoteCandidateMatchesLocalVersionNameAndCode(
        localVersion: String,
        localVersionCode: Long,
        remoteCandidates: List<GitHubVersionCandidate>
    ): Boolean {
        if (localVersionCode < 100L) return false
        val code = localVersionCode.toString()
        if (code.length < 3) return false
        val localCandidates = linkedSetOf<String>()
        normalizeVersionCandidates(localVersion).forEach { candidate ->
            val normalized = canonicalizeCandidate(candidate)
                .lowercase(Locale.ROOT)
                .removePrefix("v")
            if (normalized.isNotBlank()) {
                localCandidates += normalized
            }
        }
        if (localCandidates.isEmpty()) return false

        remoteCandidates.forEach { remoteCandidate ->
            normalizeVersionCandidates(remoteCandidate.value).forEach { candidate ->
                val remote = canonicalizeCandidate(candidate)
                    .lowercase(Locale.ROOT)
                    .removePrefix("v")
                if (remote.isBlank()) return@forEach
                localCandidates.forEach { local ->
                    if (remote == "$local.$code" ||
                        remote == "$local-$code" ||
                        remote == "$local+$code"
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun compareStructuredCandidateSets(
        leftCandidates: List<GitHubVersionCandidate>,
        rightCandidates: List<GitHubVersionCandidate>
    ): Int? {
        val left = leftCandidates.map { it.value }
        return compareCandidateSetsWithSources(left, rightCandidates)
    }

    internal fun referToSameReleaseVersion(
        leftCandidates: List<GitHubVersionCandidate>,
        rightCandidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority
    ): Boolean {
        val left = releaseIdentityKeys(leftCandidates, maxSourcePriority)
        val right = releaseIdentityKeys(rightCandidates, maxSourcePriority)
        if (left.isEmpty() || right.isEmpty()) return false
        return left.any(right::contains)
    }

    internal fun hasComparableVersionCandidates(
        candidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority
    ): Boolean {
        return candidates.any { candidate ->
            candidate.source.priority <= maxSourcePriority &&
                normalizeVersionCandidates(candidate.value).any { normalized ->
                    parseVersionParts(normalized) != null
                }
        }
    }

    internal fun hasMeaningfulPreReleaseVersionCandidates(
        candidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority
    ): Boolean {
        return candidates.any { candidate ->
            if (candidate.source.priority > maxSourcePriority) return@any false
            normalizeVersionCandidates(candidate.value).any { normalized ->
                val parts = parseVersionParts(normalized) ?: return@any false
                parts.numbers.size >= 2 ||
                        (parts.channel.isPreRelease && parts.channelNumber > 0)
            }
        }
    }

    private fun releaseIdentityKeys(
        candidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int
    ): Set<String> {
        if (candidates.isEmpty()) return emptySet()
        val keys = linkedSetOf<String>()
        candidates.forEach { candidate ->
            if (candidate.source.priority <= maxSourcePriority) {
                normalizeVersionCandidates(candidate.value).forEach { normalized ->
                    val parts = parseVersionParts(normalized) ?: return@forEach
                    if (isMeaningfulReleaseIdentity(parts)) {
                        keys += releaseIdentityKey(parts)
                    }
                }
            }
        }
        return keys
    }

    internal fun isRelevantPreRelease(
        preReleaseCandidates: List<GitHubVersionCandidate>,
        stableCandidates: List<GitHubVersionCandidate>,
        preReleaseUpdatedAtMillis: Long? = null,
        stableUpdatedAtMillis: Long? = null
    ): Boolean {
        val compare = compareStructuredCandidateSets(preReleaseCandidates, stableCandidates)
        return when {
            preReleaseUpdatedAtMillis != null && stableUpdatedAtMillis != null &&
                preReleaseUpdatedAtMillis > stableUpdatedAtMillis -> true
            compare != null -> compare > 0
            else -> (preReleaseUpdatedAtMillis ?: Long.MIN_VALUE) > (stableUpdatedAtMillis ?: Long.MIN_VALUE)
        }
    }

    fun classifyVersionChannel(text: String): GitHubReleaseChannel? {
        var bestChannel: GitHubReleaseChannel? = null
        var bestScore = Int.MIN_VALUE
        normalizeVersionCandidates(text).forEach { normalized ->
            val parts = parseVersionParts(normalized) ?: return@forEach
            val score = versionPartsSpecificityScore(parts)
            if (score > bestScore) {
                bestScore = score
                bestChannel = parts.channel
            }
        }
        return bestChannel
    }

    fun compareCandidateSets(
        leftCandidates: List<String>,
        rightCandidates: List<String>
    ): Int? {
        val comparableRight = rightCandidates.map { GitHubVersionCandidate(it, GitHubVersionCandidateSource.Content) }
        return compareCandidateSetsWithSources(leftCandidates, comparableRight)
    }

    fun compareCandidateSetsWithSources(
        leftCandidates: List<String>,
        rightCandidates: List<GitHubVersionCandidate>
    ): Int? {
        val left = parseComparableLocalCandidates(leftCandidates)
        val parsedRight = parseComparableRemoteCandidates(rightCandidates)
        val preferredRight = parsedRight.filterTo(ArrayList()) {
            it.sourcePriority <= GitHubVersionCandidateSource.Link.priority
        }
        val right = preferredRight.ifEmpty { parsedRight }

        if (left.isEmpty() || right.isEmpty()) return null

        var bestCmp: Int? = null
        var bestScore = Int.MIN_VALUE
        for (local in left) {
            for (remote in right) {
                val cmp = compareParsedVersionParts(local.parts, remote.parts)
                val score = similarityScore(local, remote)
                if (cmp == 0 && score >= bestScore) {
                    bestCmp = 0
                    bestScore = score
                    continue
                }
                if (score > bestScore) {
                    bestScore = score
                    bestCmp = cmp
                }
            }
        }
        return bestCmp
    }

    private fun parseComparableLocalCandidates(
        candidates: List<String>
    ): List<ComparableVersionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        val parsed = ArrayList<ComparableVersionCandidate>()
        candidates.forEach { candidate ->
            normalizeVersionCandidates(candidate).forEach { normalized ->
                if (seen.add(normalized)) {
                    parseComparableCandidate(normalized, sourcePriority = 0)?.let(parsed::add)
                }
            }
        }
        return parsed
    }

    private fun parseComparableRemoteCandidates(
        candidates: List<GitHubVersionCandidate>
    ): List<ComparableVersionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val seen = linkedSetOf<ComparableCandidateKey>()
        val parsed = ArrayList<ComparableVersionCandidate>()
        candidates.forEach { candidate ->
            normalizeVersionCandidates(candidate.value).forEach { normalized ->
                val key = ComparableCandidateKey(normalized, candidate.source.priority)
                if (seen.add(key)) {
                    parseComparableCandidate(
                        normalized,
                        candidate.source.priority
                    )?.let(parsed::add)
                }
            }
        }
        return parsed
    }

    private fun canonicalizeCandidate(raw: String): String {
        return raw
            .replace(preReleaseKeywordRegex, "preview")
            .replace(snapshotKeywordRegex, "dev")
            .replace(nightlyKeywordRegex, "dev")
            .replace(canaryKeywordRegex, "dev")
            .replace('_', '.')
            .replace(whitespaceRegex, "")
            .replace(separatorCleanupRegex, "-")
    }

    private fun filterLessSpecificCandidates(candidates: List<String>): List<String> {
        if (candidates.size <= 1) return candidates

        val parsedCandidates = candidates.map { candidate ->
            candidate to parseVersionParts(candidate)
        }
        val richerKeys = linkedSetOf<List<Int>>()
        parsedCandidates.forEach { (_, parts) ->
            if (parts != null &&
                (parts.channel != GitHubReleaseChannel.STABLE || parts.channelNumber > 0)
            ) {
                richerKeys += parts.numbers
            }
        }

        val filtered = linkedSetOf<String>()
        parsedCandidates.forEach { (candidate, parts) ->
            if (parts == null) {
                filtered += candidate
                return@forEach
            }
            val isTruncatedStable = parts.channel == GitHubReleaseChannel.STABLE &&
                parts.channelNumber == 0 &&
                parts.numbers in richerKeys
            if (!isTruncatedStable) {
                filtered += candidate
            }
        }
        return filtered.toList()
    }

    private data class ComparableVersionCandidate(
        val normalized: String,
        val parts: VersionParts,
        val sourcePriority: Int,
        val semanticDepth: Int,
        val looksLikeDateStamp: Boolean
    )

    private fun parseComparableCandidate(
        raw: String,
        sourcePriority: Int
    ): ComparableVersionCandidate? {
        return comparableCandidateCache.getOrPut(ComparableCandidateKey(raw, sourcePriority)) {
            parseComparableCandidateUncached(raw, sourcePriority)
        }
    }

    private fun parseComparableCandidateUncached(
        raw: String,
        sourcePriority: Int
    ): ComparableVersionCandidate? {
        val normalized = canonicalizeCandidate(raw).lowercase(Locale.ROOT)
        val parts = parseVersionParts(normalized) ?: return null
        return ComparableVersionCandidate(
            normalized = normalized,
            parts = parts,
            sourcePriority = sourcePriority,
            semanticDepth = parts.numbers.size + if (parts.channel != GitHubReleaseChannel.STABLE) 1 else 0,
            looksLikeDateStamp = parts.channel == GitHubReleaseChannel.STABLE &&
                parts.numbers.size == 1 &&
                parts.numbers.firstOrNull() in 20_000_000..29_999_999
        )
    }

    private fun similarityScore(
        left: ComparableVersionCandidate,
        right: ComparableVersionCandidate
    ): Int {
        val sameRawBonus = if (left.normalized == right.normalized) 180 else 0
        val sharedNumericPrefix = sharedNumericPrefix(left.parts.numbers, right.parts.numbers)
        val sameNumericLengthBonus = if (left.parts.numbers.size == right.parts.numbers.size) 30 else 0
        val sameChannelBonus = if (left.parts.channel == right.parts.channel) 50 else 0
        val sameChannelNumberBonus = if (left.parts.channelNumber == right.parts.channelNumber) 25 else 0
        val sourceBonus = sourceReliabilityBonus(right.sourcePriority)
        val semanticDepthBonus = right.semanticDepth * 70
        val numericLengthPenalty = abs(left.parts.numbers.size - right.parts.numbers.size) * 10
        val channelNumberPenalty = abs(left.parts.channelNumber - right.parts.channelNumber).coerceAtMost(20) * 6
        val dateStampPenalty = if (right.looksLikeDateStamp && left.parts.numbers.size >= 2) 420 else 0
        return sameRawBonus +
            sharedNumericPrefix * 160 +
            sameNumericLengthBonus +
            sameChannelBonus +
            sameChannelNumberBonus +
            sourceBonus -
            dateStampPenalty +
            semanticDepthBonus -
            numericLengthPenalty -
            channelNumberPenalty
    }

    private fun sourceReliabilityBonus(sourcePriority: Int): Int {
        return when (sourcePriority) {
            GitHubVersionCandidateSource.Tag.priority -> 520
            GitHubVersionCandidateSource.Title.priority -> 420
            GitHubVersionCandidateSource.Link.priority -> 280
            GitHubVersionCandidateSource.Id.priority -> 160
            else -> 40
        }
    }

    private fun versionPartsSpecificityScore(parts: VersionParts): Int {
        val channelBonus = if (parts.channel != GitHubReleaseChannel.STABLE) 100 else 0
        val depthBonus = parts.numbers.size * 10
        val channelNumberBonus = parts.channelNumber.coerceAtMost(9)
        return channelBonus + depthBonus + channelNumberBonus
    }

    private fun sharedNumericPrefix(left: List<Int>, right: List<Int>): Int {
        val max = minOf(left.size, right.size)
        var count = 0
        for (index in 0 until max) {
            if (left[index] != right[index]) break
            count++
        }
        return count
    }

    private fun compareParsedVersionParts(a: VersionParts, b: VersionParts): Int {
        val max = maxOf(a.numbers.size, b.numbers.size)
        for (index in 0 until max) {
            val av = a.numbers.getOrElse(index) { 0 }
            val bv = b.numbers.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }

        val channelCmp = channelRank(a.channel).compareTo(channelRank(b.channel))
        if (channelCmp != 0) return channelCmp

        if (a.channelNumber != b.channelNumber) {
            return a.channelNumber.compareTo(b.channelNumber)
        }

        return 0
    }

    private fun channelRank(channel: GitHubReleaseChannel): Int {
        return when (channel) {
            GitHubReleaseChannel.DEV -> 0
            GitHubReleaseChannel.ALPHA -> 1
            GitHubReleaseChannel.BETA -> 2
            GitHubReleaseChannel.RC -> 3
            GitHubReleaseChannel.PREVIEW -> 4
            GitHubReleaseChannel.STABLE -> 5
            GitHubReleaseChannel.UNKNOWN -> 5
        }
    }

    private data class VersionParts(
        val numbers: List<Int>,
        val channel: GitHubReleaseChannel,
        val channelNumber: Int
    )

    private data class ComparableCandidateKey(
        val raw: String,
        val sourcePriority: Int
    )

    private fun isMeaningfulReleaseIdentity(parts: VersionParts): Boolean {
        return parts.numbers.size >= 2 || (parts.channel.isPreRelease && parts.channelNumber > 0)
    }

    private fun releaseIdentityKey(parts: VersionParts): String {
        return buildString {
            append(parts.numbers.joinToString("."))
            append('|')
            append(parts.channel.name)
            append('|')
            append(parts.channelNumber)
        }
    }

    private fun parseVersionParts(raw: String): VersionParts? {
        return versionPartsCache.getOrPut(raw) {
            parseVersionPartsUncached(raw)
        }
    }

    private fun parseVersionPartsUncached(raw: String): VersionParts? {
        val src = raw.trim().lowercase(Locale.ROOT)
        if (src.isBlank()) return null

        val normalized = src.removePrefix("v")
        val coreMatch = coreVersionRegex.find(normalized) ?: return null
        val coreNumbers = coreMatch.value
            .split('.', '_')
            .mapNotNull { it.toIntOrNull() }
        if (coreNumbers.isEmpty()) return null

        val suffix = normalized.substring(coreMatch.range.last + 1)
        val channelMatch = channelSuffixRegex.find(suffix.ifBlank { normalized })

        val channel = when (channelMatch?.groupValues?.getOrNull(1).orEmpty()) {
            "dev", "nightly", "canary", "snapshot" -> GitHubReleaseChannel.DEV
            "alpha" -> GitHubReleaseChannel.ALPHA
            "beta" -> GitHubReleaseChannel.BETA
            "rc" -> GitHubReleaseChannel.RC
            "preview", "pre", "pre-release" -> GitHubReleaseChannel.PREVIEW
            else -> GitHubReleaseChannel.STABLE
        }

        val channelNumber = channelMatch
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
            ?: 0

        return VersionParts(
            numbers = coreNumbers,
            channel = channel,
            channelNumber = channelNumber
        )
    }

    private class BoundedVersionCache<K, V>(
        private val maxSize: Int
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
                return created.also { value ->
                    values[key] = value
                }
            }
        }
    }
}

private fun PackageManager.queryInstalledPackageInfos() =
    getInstalledPackages(PackageManager.PackageInfoFlags.of(0))

private fun PackageManager.getApplicationInfoCompat(packageName: String): ApplicationInfo? {
    return runCatching {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    }.getOrNull()
}

private data class InstalledAppInstallSource(
    val packageName: String,
    val label: String
)

private fun PackageManager.resolveInstallSource(
    packageName: String,
    labelCache: MutableMap<String, String>
): InstalledAppInstallSource {
    val sourcePackageName = runCatching {
        val sourceInfo = getInstallSourceInfo(packageName)
        sourceInfo.installingPackageName
            ?: sourceInfo.initiatingPackageName
            ?: sourceInfo.originatingPackageName
    }.getOrNull()?.trim().orEmpty()
    if (sourcePackageName.isBlank()) return InstalledAppInstallSource("", "")
    val sourceLabel = labelCache.getOrPut(sourcePackageName) {
        runCatching {
            val sourceInfo = getApplicationInfoCompat(sourcePackageName)
            if (sourceInfo == null) {
                sourcePackageName
            } else {
                getApplicationLabel(sourceInfo).toString()
            }
        }.getOrDefault(sourcePackageName).trim().ifBlank { sourcePackageName }
    }
    return InstalledAppInstallSource(
        packageName = sourcePackageName,
        label = sourceLabel
    )
}

private fun installedAppResourceOverlayFlagMask(): Int {
    return runCatching {
        ApplicationInfo::class.java.getField("FLAG_IS_RESOURCE_OVERLAY").getInt(null)
    }.getOrDefault(0)
}

private fun shouldIgnoreInstalledAppForGitHubList(
    appInfo: ApplicationInfo,
    overlayFlagMask: Int
): Boolean {
    if (!appInfo.enabled) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_HAS_CODE) == 0) return true
    if ((appInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) return true
    if (overlayFlagMask != 0 && (appInfo.flags and overlayFlagMask) != 0) return true
    return false
}

private fun ApplicationInfo.isSystemAppForGitHubPicker(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}
