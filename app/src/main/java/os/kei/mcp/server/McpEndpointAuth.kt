package os.kei.mcp.server

internal object McpEndpointAuth {
    fun extractBearerToken(rawHeader: String): String {
        if (rawHeader.isBlank()) return ""
        val parts = rawHeader.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size < 2 || !parts[0].equals("Bearer", ignoreCase = true)) return ""
        return parts[1].trim().trim('"')
    }

    fun describeAuthHeader(rawHeader: String): String {
        if (rawHeader.isBlank()) return "missing"
        val token = extractBearerToken(rawHeader)
        if (token.isBlank()) return "invalid-format"
        return "bearer(len=${token.length})"
    }

    fun constantTimeEquals(expected: String, provided: String): Boolean {
        val expectedBytes = expected.encodeToByteArray()
        val providedBytes = provided.encodeToByteArray()
        var diff = expectedBytes.size xor providedBytes.size
        val max = maxOf(expectedBytes.size, providedBytes.size)
        for (index in 0 until max) {
            val left = expectedBytes.getOrNull(index)?.toInt() ?: 0
            val right = providedBytes.getOrNull(index)?.toInt() ?: 0
            diff = diff or (left xor right)
        }
        return diff == 0
    }
}
