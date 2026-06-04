package os.kei.mcp.notification

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(
    application = McpNotificationOpenIntentTestApp::class,
    sdk = [35],
)
class McpNotificationOpenIntentTest {
    @Test
    fun `ba open intent carries target account id`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val intent =
            McpNotificationHelper.buildOpenIntent(
                context = context,
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                targetBaAccountId = "  cn-alt  ",
            )

        assertEquals(
            McpAppIntentContract.TARGET_BOTTOM_PAGE_BA,
            intent.getStringExtra(McpAppIntentContract.EXTRA_TARGET_BOTTOM_PAGE),
        )
        assertEquals("cn-alt", intent.getStringExtra(McpAppIntentContract.EXTRA_BA_ACCOUNT_ID))
    }

    @Test
    fun `non ba open intent drops target account id`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val intent =
            McpNotificationHelper.buildOpenIntent(
                context = context,
                serverName = "KeiOS MCP",
                targetBaAccountId = "cn-alt",
            )

        assertEquals(
            McpAppIntentContract.TARGET_BOTTOM_PAGE_MCP,
            intent.getStringExtra(McpAppIntentContract.EXTRA_TARGET_BOTTOM_PAGE),
        )
        assertNull(intent.getStringExtra(McpAppIntentContract.EXTRA_BA_ACCOUNT_ID))
    }
}

class McpNotificationOpenIntentTestApp : Application()
