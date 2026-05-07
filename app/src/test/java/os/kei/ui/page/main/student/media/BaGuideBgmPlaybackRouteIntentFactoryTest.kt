package os.kei.ui.page.main.student

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.MainActivityIntentRouting
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmPlaybackRouteIntentFactoryTest {
    @Test
    fun `playback intent routes to BA BGM playback tab`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val intent = BaGuideBgmPlaybackRouteIntentFactory.createIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertFlag(intent, Intent.FLAG_ACTIVITY_NEW_TASK)
        assertFlag(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP)
        assertFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP)
        assertEquals(
            MainActivity.TARGET_BOTTOM_PAGE_BA,
            intent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE)
        )
        assertEquals(
            MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK,
            intent.getStringExtra(MainActivity.EXTRA_SHORTCUT_ACTION)
        )

        val sanitized = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = intent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
            rawMcpServerAction = null,
            rawShortcutAction = intent.getStringExtra(MainActivity.EXTRA_SHORTCUT_ACTION)
        )
        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_BA, sanitized?.targetBottomPage)
        assertEquals(
            MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK,
            sanitized?.shortcutAction
        )
    }

    @Test
    fun `playback pending intent uses stable activity request`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pendingIntent = BaGuideBgmPlaybackRouteIntentFactory.createPendingIntent(context)
        val shadow = shadowOf(pendingIntent)

        assertTrue(shadow.isActivity)
        assertTrue(shadow.isImmutable)
        assertEquals(BA_GUIDE_BGM_PLAYBACK_PENDING_INTENT_REQUEST_CODE, shadow.requestCode)
        assertTrue(shadow.flags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertEquals(
            MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK,
            shadow.savedIntent.getStringExtra(MainActivity.EXTRA_SHORTCUT_ACTION)
        )
    }

    private fun assertFlag(intent: Intent, flag: Int) {
        assertTrue(intent.flags and flag != 0)
    }
}
