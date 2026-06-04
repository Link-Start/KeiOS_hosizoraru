package os.kei.mcp.server

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpEndpointAuthTest {
    @Test
    fun extractsBearerTokenWithQuotesAndSpacing() {
        assertEquals("abc123", McpEndpointAuth.extractBearerToken("  Bearer   \"abc123\" "))
        assertEquals("", McpEndpointAuth.extractBearerToken("Token abc123"))
        assertEquals("", McpEndpointAuth.extractBearerToken(""))
    }

    @Test
    fun constantTimeEqualsRequiresExactValue() {
        assertTrue(McpEndpointAuth.constantTimeEquals("secret", "secret"))
        assertFalse(McpEndpointAuth.constantTimeEquals("secret", "SECRET"))
        assertFalse(McpEndpointAuth.constantTimeEquals("secret", "secret1"))
        assertFalse(McpEndpointAuth.constantTimeEquals("secret", ""))
    }

    @Test
    fun authHeaderDescriptionDoesNotExposeToken() {
        assertEquals("missing", McpEndpointAuth.describeAuthHeader(""))
        assertEquals("invalid-format", McpEndpointAuth.describeAuthHeader("Token secret"))
        assertEquals("bearer(len=6)", McpEndpointAuth.describeAuthHeader("Bearer secret"))
    }
}
