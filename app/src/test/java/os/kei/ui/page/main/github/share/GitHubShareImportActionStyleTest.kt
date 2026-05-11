package os.kei.ui.page.main.github.share

import org.junit.Test
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.widget.glass.GlassVariant
import kotlin.test.assertEquals

class GitHubShareImportActionStyleTest {
    @Test
    fun `cancel action uses danger semantic style`() {
        assertEquals(GitHubStatusPalette.Error, GitHubShareImportActionStyle.cancelContainerColor)
        assertEquals(GlassVariant.SheetDangerAction, GitHubShareImportActionStyle.cancelVariant)
    }
}
