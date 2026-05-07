package os.kei.feature.ba.data.remote

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameKeeRepositoryTest {
    @Test
    fun `content detail path normalizes invalid ids`() {
        assertEquals("/v1/content/detail/609145", GameKeeRepository.contentDetailApiPath(609145L))
        assertEquals("/v1/content/detail/0", GameKeeRepository.contentDetailApiPath(-1L))
    }

    @Test
    fun `ba api headers keep GameKee mobile contract`() {
        assertEquals(
            mapOf(
                "device-num" to "1",
                "game-alias" to "ba"
            ),
            GameKeeRepository.baApiHeaders()
        )
    }

    @Test
    fun `network result failure keeps compact preview and throwable`() {
        val result = GameKeeNetworkResult.Failure(
            request = GameKeeNetworkRequest(
                pathOrUrl = "/v1/content/detail/1",
                refererPath = "/ba/tj/1.html"
            ),
            errorPreview = "IOException:http=403 body=<html>blocked...</html>",
            throwable = IllegalStateException("blocked")
        )

        val error = assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }
        assertEquals("blocked", error.message)
        assertTrue(result.errorPreview.length < 220)
    }
}
