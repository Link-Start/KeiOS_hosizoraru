package os.kei

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class MainStartupTransitionTest {
    @Test fun `prepared destination draws even before pager frame activation`() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { activity ->
            val transition = MainStartupTransition(activity.get())
            transition.onContentReady(ready = true, transitionAnimationsEnabled = true, waitForMainPage = true)
            // Android returns true when any pre-draw listener cancels this draw. Blocking it
            // here would also prevent the pager from receiving the frame it needs to activate.
            assertFalse(activity.get().window.decorView.viewTreeObserver.dispatchOnPreDraw())
            assertTrue(transition.hasPresented)
            transition.dispose()
        }
    }

    @Test fun `pending local state holds the first draw but releases after the deadline`() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { activity ->
            val transition = MainStartupTransition(activity.get())
            val observer = activity.get().window.decorView.viewTreeObserver
            assertTrue(observer.dispatchOnPreDraw())
            assertFalse(transition.hasPresented)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
            assertFalse(observer.dispatchOnPreDraw())
            assertTrue(transition.hasPresented)
            transition.dispose()
        }
    }
}
