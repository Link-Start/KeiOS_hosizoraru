package os.kei.mcp.bridge

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.ui.testing.repoSource

class AppMcpWebDavToolPluginSourceTest {
    @Test
    fun statusOutputKeepsWebDavCredentialsOutOfTheToolContract() {
        val source = repoSource(WEB_DAV_PLUGIN_SOURCE)

        assertTrue("safeWebDavHost()" in source)
        assertTrue("serverHost=" in source)
        assertFalse(".username" in source)
        assertFalse(".appPassword" in source)
        assertFalse("authorization" in source.lowercase())
    }

    @Test
    fun toolsStayReadOnlyAndExposeStatusAndHistory() {
        val source = repoSource(WEB_DAV_PLUGIN_SOURCE)

        assertTrue("keios.webdav.status" in source)
        assertTrue("keios.webdav.history" in source)
        assertFalse("saveConfig(" in source)
        assertFalse("appendHistory(" in source)
        assertFalse("clearHistory(" in source)
    }
}

private const val WEB_DAV_PLUGIN_SOURCE =
    "app/src/main/java/os/kei/mcp/bridge/AppMcpWebDavToolPlugin.kt"
