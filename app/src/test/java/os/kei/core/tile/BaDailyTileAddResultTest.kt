package os.kei.core.tile

import android.app.StatusBarManager
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The `requestAddTileService` result codes, which the teacher only ever sees as one of four toasts.
 *
 * These read the platform constants rather than literals on purpose — they are `static final int`, so
 * the compiler inlines them and no Android runtime is needed, while the test still fails if a future
 * SDK renumbers them.
 */
class BaDailyTileAddResultTest {
    @Test
    fun `the two success codes are distinguished`() {
        assertEquals(
            BaDailyTileAddResult.Added,
            baDailyTileAddResultOf(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED),
        )
        // Already added is not a failure: the tile is on the panel, which is what the tap wanted.
        assertEquals(
            BaDailyTileAddResult.AlreadyAdded,
            baDailyTileAddResultOf(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED),
        )
    }

    @Test
    fun `not added is a decline`() {
        assertEquals(
            BaDailyTileAddResult.Declined,
            baDailyTileAddResultOf(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED),
        )
    }

    @Test
    fun `dismissing the dialog is a decline, not an unavailable device`() {
        // TILE_ADD_REQUEST_RESULT_DIALOG_DISMISSED == 3. It is @hide, so it cannot be named, but it
        // does reach the callback when the dialog is swiped away. Calling that "unavailable" would tell
        // the teacher their device cannot host a tile it had just offered them.
        assertEquals(BaDailyTileAddResult.Declined, baDailyTileAddResultOf(3))
    }

    @Test
    fun `every documented error code is unavailable`() {
        listOf(
            StatusBarManager.TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE,
            StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS,
            StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT,
            StatusBarManager.TILE_ADD_REQUEST_ERROR_NOT_CURRENT_USER,
            StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND,
            StatusBarManager.TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE,
        ).forEach { code ->
            assertEquals(BaDailyTileAddResult.Unavailable, baDailyTileAddResultOf(code), "code $code")
        }
    }

    @Test
    fun `the error boundary is the documented 1000, not the known codes`() {
        // "Values greater or equal to this value indicate an error in the request."
        assertEquals(BaDailyTileAddResult.Declined, baDailyTileAddResultOf(999))
        assertEquals(BaDailyTileAddResult.Unavailable, baDailyTileAddResultOf(1000))
        assertEquals(BaDailyTileAddResult.Unavailable, baDailyTileAddResultOf(9999))
    }

    @Test
    fun `an unknown future result stays a decline rather than claiming the device cannot`() {
        assertEquals(BaDailyTileAddResult.Declined, baDailyTileAddResultOf(4))
        // Nothing sends a negative code, but a decline is still the safer of the two lies.
        assertEquals(BaDailyTileAddResult.Declined, baDailyTileAddResultOf(-1))
    }

    @Test
    fun `only the two success codes keep the component enabled and the slot claimed`() {
        // Claiming happens before the request, so anything else has to be rolled back. Getting this
        // backwards leaves a declined component in the quick-settings editor and burns a pool slot.
        assertEquals(true, BaDailyTileAddResult.Added.keepsTile)
        assertEquals(true, BaDailyTileAddResult.AlreadyAdded.keepsTile)
        assertEquals(false, BaDailyTileAddResult.Declined.keepsTile)
        assertEquals(false, BaDailyTileAddResult.Unavailable.keepsTile)
    }
}
