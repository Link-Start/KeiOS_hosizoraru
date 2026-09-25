package os.kei.ui.testing

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * How every screenshot test compares against its golden.
 *
 * The goldens are recorded on macOS and verified on CI's Linux runner, and Robolectric's native
 * graphics round a colour one or two 8-bit steps apart on the two. Measured on D#209 (2026-09-25):
 * seven of fifteen goldens differed, every differing pixel by at most 2/255 in any channel, the
 * worst a differ distance of 0.0136 (2/255 in R, G and B). The default comparator allows 0.007, so
 * all seven failed without a visible change.
 *
 * 0.016 is about 2/255 in every channel, or 4/255 in one: invisible, and far below what a real
 * change produces. It is a per-pixel colour tolerance, not an area one, so a small element that
 * changes colour or moves still fails. A golden replaced with a different image still fails.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal val KeiOSScreenshotOptions =
    RoborazziOptions(
        compareOptions =
            RoborazziOptions.CompareOptions(
                imageComparator = SimpleImageComparator(maxDistance = 0.016f),
            ),
    )
