package os.kei.core.tile

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The component the shade hands the long-press editor, mapped back onto a tile.
 *
 * Worth pinning because the editor's activity has to be exported for SystemUI to launch it, which means
 * the component name it reads is attacker-controllable in principle, and because the slot indices decide
 * which account a run is applied to — an off-by-one here would silently mark the wrong account's dailies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BaDailyTileKindTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `each declared tile maps to its own kind`() {
        assertEquals(BaDailyTileKind.AllAccounts, kindOf(BaDailyDoneAllTileService::class.java))
        assertEquals(BaDailyTileKind.AccountSlot(0), kindOf(BaDailyDoneAccountTileService1::class.java))
        assertEquals(BaDailyTileKind.AccountSlot(1), kindOf(BaDailyDoneAccountTileService2::class.java))
        assertEquals(BaDailyTileKind.AccountSlot(2), kindOf(BaDailyDoneAccountTileService3::class.java))
    }

    @Test
    fun `a missing component is not a tile`() {
        // The in-app entry point launches the same activity with no extra at all.
        assertNull(BaDailyTileManager.kindOf(context, component = null))
    }

    @Test
    fun `a component from another package is refused rather than matched on class name`() {
        val impostor =
            ComponentName("com.example.other", BaDailyDoneAccountTileService1::class.java.name)
        assertNull(BaDailyTileManager.kindOf(context, impostor))
    }

    @Test
    fun `one of our own components that is not a daily tile is not a tile either`() {
        assertNull(BaDailyTileManager.kindOf(context, ComponentName(context, BaDailyTileManager::class.java)))
    }

    private fun kindOf(cls: Class<*>): BaDailyTileKind? =
        BaDailyTileManager.kindOf(context, ComponentName(context, cls))
}
