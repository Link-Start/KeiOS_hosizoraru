package os.kei.ui.page.main.student.tabcontent.profile

/**
 * Repository surface for resolving profile external link titles. Hides the file-level
 * `resolveProfileLinkTitleAsync` helper behind an injectable seam so the loader (and any
 * future consumer) doesn't reach into module-private free functions.
 */
internal class GuideProfileLinkTitleRepository {
    /** Cached value if the title has already been resolved, else `null`. */
    fun cachedTitle(link: String): String? =
        if (link.isBlank()) null else profileLinkTitleCache[link]?.takeIf { it.isNotBlank() }

    /**
     * Resolve a single link's title from the network (or shared cache). Suspends on the
     * BA-fetch dispatcher so callers don't have to manage the dispatcher hop themselves.
     */
    suspend fun resolveTitle(link: String): String =
        if (link.isBlank()) "" else resolveProfileLinkTitleAsync(link)
}
