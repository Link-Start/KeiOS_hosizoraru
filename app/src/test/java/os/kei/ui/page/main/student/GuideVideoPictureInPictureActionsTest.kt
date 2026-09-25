package os.kei.ui.page.main.student

import android.app.Application
import android.content.pm.PackageManager
import android.util.Rational
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.R
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Config(application = Application::class, sdk = [35])
@RunWith(AndroidJUnit4::class)
class GuideVideoPictureInPictureActionsTest {
    @Test
    fun `close action stays out of visible actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val actionSet =
            buildGuidePictureInPictureActionSet(
                context = context,
                sessionId = 42L,
                playWhenReady = true,
                repeatEnabled = true,
            )

        val closeAction = assertNotNull(actionSet.closeAction)
        val visibleCloseAction =
            actionSet.actions.singleOrNull { action ->
                action.actionIntent == closeAction.actionIntent
            }

        assertEquals(4, actionSet.actions.size)
        assertEquals(
            GUIDE_VIDEO_ACTION_SEEK_BACK_10S,
            shadowOf(actionSet.actions[0].actionIntent).savedIntent.action,
        )
        assertEquals(
            GUIDE_VIDEO_ACTION_TOGGLE_PIP_PLAYBACK,
            shadowOf(actionSet.actions[1].actionIntent).savedIntent.action,
        )
        assertEquals(
            GUIDE_VIDEO_ACTION_SEEK_FORWARD_10S,
            shadowOf(actionSet.actions[2].actionIntent).savedIntent.action,
        )
        assertEquals(
            GUIDE_VIDEO_ACTION_TOGGLE_PIP_LOOP,
            shadowOf(actionSet.actions.last().actionIntent).savedIntent.action,
        )
        assertNull(visibleCloseAction)
    }

    @Test
    fun `close action survives visible action trimming`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val actionSet =
            buildGuidePictureInPictureActionSet(
                context = context,
                sessionId = 43L,
                playWhenReady = false,
                repeatEnabled = false,
                maxActions = 0,
        )

        assertNotNull(actionSet.closeAction)
        assertEquals(0, actionSet.actions.size)
    }

    @Test
    fun `loop action reflects repeat state`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repeatEnabledActionSet =
            buildGuidePictureInPictureActionSet(
                context = context,
                sessionId = 44L,
                playWhenReady = true,
                repeatEnabled = true,
            )
        val repeatDisabledActionSet =
            buildGuidePictureInPictureActionSet(
                context = context,
                sessionId = 45L,
                playWhenReady = true,
                repeatEnabled = false,
            )

        val enabledLoopAction = repeatEnabledActionSet.actions.last()
        val disabledLoopAction = repeatDisabledActionSet.actions.last()

        assertEquals(
            GUIDE_VIDEO_ACTION_TOGGLE_PIP_LOOP,
            shadowOf(enabledLoopAction.actionIntent).savedIntent.action,
        )
        assertEquals(
            context.getString(R.string.guide_gallery_memorial_lobby_pip_loop_disable),
            enabledLoopAction.title,
        )
        assertEquals(
            context.getString(R.string.guide_gallery_memorial_lobby_pip_loop_enable),
            disabledLoopAction.title,
        )
    }

    @Test
    fun `expanded pip ratio is declared when device supports expanded pip`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE, true)

        val params = buildGuidePictureInPictureParams(context = context)

        assertEquals(Rational(12, 5), params.expandedAspectRatio)
        assertTrue(params.isSeamlessResizeEnabled)
    }

    @Test
    fun `expanded pip ratio is skipped when device does not support expanded pip`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE, false)

        val params = buildGuidePictureInPictureParams(context = context)

        assertNull(params.expandedAspectRatio)
        assertTrue(params.isSeamlessResizeEnabled)
    }

    @Test
    fun `auto enter follows the requested playback policy`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val playingParams =
            buildGuidePictureInPictureParams(
                context = context,
                autoEnterEnabled = true,
            )
        val pausedParams =
            buildGuidePictureInPictureParams(
                context = context,
                autoEnterEnabled = false,
            )

        assertTrue(playingParams.isAutoEnterEnabled)
        assertFalse(pausedParams.isAutoEnterEnabled)
    }
}
