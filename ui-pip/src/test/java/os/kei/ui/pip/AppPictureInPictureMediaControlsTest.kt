package os.kei.ui.pip

import org.junit.Test
import kotlin.test.assertEquals

class AppPictureInPictureMediaControlsTest {
    private val controls =
        AppPictureInPictureMediaControlActions(
            playbackAction = action("playback"),
            seekBackAction = action("seek_back"),
            seekForwardAction = action("seek_forward"),
            secondaryAction = action("secondary"),
        )

    @Test
    fun `media controls keep the highest priority actions for each action limit`() {
        listOf(
            Triple(
                "unconstrained keeps the balanced four action layout",
                null,
                listOf("seek_back", "playback", "seek_forward", "secondary"),
            ),
            Triple("three keep the seek pair around playback", 3, listOf("seek_back", "playback", "seek_forward")),
            Triple("two keep playback and the secondary action", 2, listOf("playback", "secondary")),
            Triple("one keeps playback only", 1, listOf("playback")),
            Triple("a negative limit is treated as zero", -1, emptyList<String>()),
        ).forEach { (label, maxActions, expected) ->
            assertEquals(
                expected,
                controls.resolveVisibleActions(maxActions = maxActions).map { it.action },
                label,
            )
        }
    }

    private fun action(action: String): AppPictureInPictureRemoteActionSpec {
        return AppPictureInPictureRemoteActionSpec(
            action = action,
            iconRes = 0,
            title = action,
            requestCode = action.hashCode(),
        )
    }
}
