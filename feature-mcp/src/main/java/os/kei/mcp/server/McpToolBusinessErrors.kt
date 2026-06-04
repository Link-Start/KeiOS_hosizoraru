package os.kei.mcp.server

internal object McpToolBusinessErrors {
    private val exactFirstLines = setOf(
        "ok=false",
        "target=unknown"
    )

    private val firstLinePrefixes = listOf(
        "hasTarget=false"
    )

    private val messageTokens = setOf(
        "message=query_required",
        "message=url_required",
        "message=repoUrl_required",
        "message=repoUrls_required",
        "message=invalid_package_name",
        "message=url_required_for_ba_guide_url"
    )

    fun isBusinessError(text: String): Boolean {
        val firstLine = text.lineSequence().firstOrNull().orEmpty().trim()
        return firstLine in exactFirstLines ||
            firstLinePrefixes.any(firstLine::startsWith) ||
            messageTokens.any(text::contains)
    }
}
