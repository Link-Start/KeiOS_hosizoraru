package os.kei.ui.page.main.github.share

import android.app.Application
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@Suppress("DEPRECATION")
class GitHubShareImportWindowChromeTest {
    @Test
    fun shareImportWindowThemeKeepsHostWindowTransparent() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val themedContext = ContextThemeWrapper(
            context,
            R.style.Theme_KeiOS_ShareImportWindow
        )
        val attributes = intArrayOf(
            android.R.attr.windowIsTranslucent,
            android.R.attr.backgroundDimEnabled,
            android.R.attr.statusBarColor,
            android.R.attr.navigationBarColor
        )

        val typedArray = themedContext.obtainStyledAttributes(attributes)
        try {
            assertTrue(typedArray.getBoolean(0, false))
            assertFalse(typedArray.getBoolean(1, true))
            assertEquals(Color.TRANSPARENT, typedArray.getColor(2, Color.BLACK))
            assertEquals(Color.TRANSPARENT, typedArray.getColor(3, Color.BLACK))
        } finally {
            typedArray.recycle()
        }
    }

    @Test
    fun shareImportWindowBlurUsesInstallerStyleRadius() {
        assertEquals(30, GitHubShareImportWindowChrome.BlurBehindRadius)
    }
}
