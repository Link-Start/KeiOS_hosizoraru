package os.kei.mcp.server

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.shizuku.ShizukuApiUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = McpServerTestApp::class, sdk = [35])
class McpToolExecutionTest {
    @Test
    fun timeoutReturnsToolErrorResult() = runBlocking {
        val logs = mutableListOf<String>()
        val result = executeMcpTextTool(
            environment = testEnvironment(logs),
            name = "test.timeout",
            profile = McpToolExecutionProfile.CacheRead,
            outputContract = McpToolOutputContract.KeyValueText,
            structuredOutputEnabled = false,
            request = CallToolRequest(CallToolRequestParams(name = "test.timeout"))
        ) {
            delay(4_500)
            "late"
        }

        assertEquals(true, result.isError)
        val text = (result.content.first() as TextContent).text
        assertTrue(text.contains("timeout_4000ms"))
        assertTrue(logs.any { it.contains("test.timeout:false") })
    }

    @Test
    fun businessErrorTextReturnsToolErrorResult() = runBlocking {
        val result = executeMcpTextTool(
            environment = testEnvironment(mutableListOf()),
            name = "test.business",
            profile = McpToolExecutionProfile.CacheRead,
            outputContract = McpToolOutputContract.KeyValueText,
            structuredOutputEnabled = false,
            request = CallToolRequest(CallToolRequestParams(name = "test.business"))
        ) {
            "ok=false\nmessage=query_required"
        }

        assertEquals(true, result.isError)
    }

    @Test
    fun businessErrorDetectorCoversKnownToolErrorShapes() {
        assertEquals(true, McpToolBusinessErrors.isBusinessError("target=unknown\nmessage=no_target"))
        assertEquals(true, McpToolBusinessErrors.isBusinessError("hasTarget=false\nmessage=missing"))
        assertEquals(true, McpToolBusinessErrors.isBusinessError("ok=true\nmessage=repoUrls_required"))
        assertEquals(false, McpToolBusinessErrors.isBusinessError("ok=true\nmessage=ready"))
    }

    private fun testEnvironment(logs: MutableList<String>): McpToolEnvironment {
        return McpToolEnvironment(
            appContext = ApplicationProvider.getApplicationContext(),
            shizukuApiUtils = ShizukuApiUtils(),
            appVersionName = "test",
            appVersionCode = 1L,
            appPackageName = "os.kei.test",
            appLabel = "KeiOS",
            stateProvider = { McpServerUiState(authToken = "token") },
            toolCallLogger = { name, _, _, success, _ ->
                logs += "$name:$success"
            }
        )
    }
}
