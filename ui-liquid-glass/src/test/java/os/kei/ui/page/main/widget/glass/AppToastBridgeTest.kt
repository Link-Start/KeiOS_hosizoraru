package os.kei.ui.page.main.widget.glass

import android.app.Application
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class AppToastBridgeTest {
    @Test
    fun backgroundLiquidToastDispatchesToTheMainThread() {
        val state = LiquidToastState()
        val providerRanOnMainThread = AtomicBoolean(false)
        AppToastBridge.register(
            state = state,
            liquidToastEnabled = {
                providerRanOnMainThread.set(Looper.myLooper() == Looper.getMainLooper())
                true
            },
            reduceToastInterruptionEnabled = { false },
        )

        try {
            Thread {
                AppToastBridge.showLiquidOnly(message = "Background result")
            }.apply {
                start()
                join()
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(providerRanOnMainThread.get())
            assertEquals(listOf("Background result"), state.visibleSlots.map { it.data.message })
        } finally {
            AppToastBridge.unregister(state)
        }
    }
}
