package os.kei.ui.page.main.ba.card

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.baCalendarKindLabel
import os.kei.ui.page.main.ba.support.baPoolTagLabel
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaCalendarPoolEntryStatusPillTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class BaCalendarPoolEntryStatusPillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarEntryKeepsStatusCountdownAndDatesOrderedWithoutAnImageAtLargeFont() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val timeZone = serverRefreshTimeZone(SERVER_INDEX)
        val status = application.getString(R.string.ba_status_running)
        val countdown = formatBaRemainingTime(CALENDAR_END_MS, NOW_MS)
        val title =
            "${application.baCalendarKindLabel(CALENDAR_KIND_ID, CALENDAR_KIND)} · $CALENDAR_TITLE"
        val dateRange =
            "${formatBaDateTimeNoYearInTimeZone(CALENDAR_BEGIN_MS, timeZone)} - " +
                formatBaDateTimeNoYearInTimeZone(CALENDAR_END_MS, timeZone)

        setLargeFontContent { backdrop ->
            Box(
                modifier =
                    Modifier
                        .width(360.dp)
                        .testTag(CALENDAR_HOST_TAG),
            ) {
                BaCalendarEntryPanel(
                    backdrop = backdrop,
                    isPageActive = false,
                    serverIndex = SERVER_INDEX,
                    activity =
                        BaCalendarEntry(
                            id = 1,
                            title = CALENDAR_TITLE,
                            kindId = CALENDAR_KIND_ID,
                            kindName = CALENDAR_KIND,
                            beginAtMs = CALENDAR_BEGIN_MS,
                            endAtMs = CALENDAR_END_MS,
                            linkUrl = "https://example.com/calendar",
                            imageUrl = "",
                            isRunning = true,
                        ),
                    nowMs = NOW_MS,
                    showCalendarPoolImages = false,
                    effectsEnabled = true,
                    onOpenCalendarLink = {},
                )
            }
        }

        composeRule.onNodeWithTag(CALENDAR_HOST_TAG).assertWidthIsEqualTo(360.dp)
        val hostBounds = composeRule.onNodeWithTag(CALENDAR_HOST_TAG).bounds()
        val statusNode = composeRule.onNodeWithText(status, useUnmergedTree = true)
        val statusBounds = statusNode.bounds()
        val countdownBounds = composeRule.onNodeWithText(countdown, useUnmergedTree = true).bounds()
        val titleBounds = composeRule.onNodeWithText(title, useUnmergedTree = true).bounds()
        val dateBounds = composeRule.onNodeWithText(dateRange, useUnmergedTree = true).bounds()
        val tolerance = with(composeRule.density) { 1.dp.toPx() }

        assertReadOnly(statusNode)
        assertTrue(statusBounds.right <= countdownBounds.left + tolerance)
        assertTrue(
            titleBounds.top >= maxOf(statusBounds.bottom, countdownBounds.bottom) - tolerance,
        )
        assertTrue(dateBounds.top >= titleBounds.bottom - tolerance)
        listOf(statusBounds, countdownBounds, titleBounds, dateBounds).forEach { bounds ->
            assertInside(hostBounds, bounds, tolerance)
        }
    }

    @Test
    fun poolEntryPreservesTheCoverColumnAndMetadataOrderAtLargeFont() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val timeZone = serverRefreshTimeZone(SERVER_INDEX)
        val status = application.getString(R.string.ba_status_upcoming)
        val countdown = formatBaRemainingTime(POOL_START_MS, NOW_MS)
        val title = "${application.baPoolTagLabel(POOL_TAG_ID, POOL_TAG)} · $POOL_TITLE"
        val dateRange =
            "${formatBaDateTimeNoYearInTimeZone(POOL_START_MS, timeZone)} - " +
                formatBaDateTimeNoYearInTimeZone(POOL_END_MS, timeZone)

        setLargeFontContent { backdrop ->
            Box(
                modifier =
                    Modifier
                        .width(360.dp)
                        .testTag(POOL_HOST_TAG),
            ) {
                BaPoolEntryPanel(
                    backdrop = backdrop,
                    isPageActive = false,
                    serverIndex = SERVER_INDEX,
                    pool =
                        BaPoolEntry(
                            id = 2,
                            name = POOL_TITLE,
                            tagId = POOL_TAG_ID,
                            tagName = POOL_TAG,
                            startAtMs = POOL_START_MS,
                            endAtMs = POOL_END_MS,
                            linkUrl = "https://example.com/pool",
                            imageUrl = "https://example.com/pool.png",
                            isRunning = false,
                        ),
                    nowMs = NOW_MS,
                    showCalendarPoolImages = true,
                    effectsEnabled = true,
                    onOpenPoolStudentGuide = {},
                    onOpenCalendarLink = {},
                )
            }
        }

        composeRule.onNodeWithTag(POOL_HOST_TAG).assertWidthIsEqualTo(360.dp)
        val hostBounds = composeRule.onNodeWithTag(POOL_HOST_TAG).bounds()
        val statusNode = composeRule.onNodeWithText(status, useUnmergedTree = true)
        val statusBounds = statusNode.bounds()
        val titleBounds = composeRule.onNodeWithText(title, useUnmergedTree = true).bounds()
        val dateBounds = composeRule.onNodeWithText(dateRange, useUnmergedTree = true).bounds()
        val countdownBounds = composeRule.onNodeWithText(countdown, useUnmergedTree = true).bounds()
        val tolerance = with(composeRule.density) { 1.dp.toPx() }
        val minimumCoverColumnOffset = with(composeRule.density) { 110.dp.toPx() }

        assertReadOnly(statusNode)
        assertTrue(statusBounds.left >= hostBounds.left + minimumCoverColumnOffset)
        assertTrue(titleBounds.top >= statusBounds.bottom - tolerance)
        assertTrue(dateBounds.top >= titleBounds.bottom - tolerance)
        assertTrue(countdownBounds.top >= dateBounds.bottom - tolerance)
        listOf(statusBounds, titleBounds, dateBounds, countdownBounds).forEach { bounds ->
            assertInside(hostBounds, bounds, tolerance)
        }
    }

    @Test
    fun longLocalizedStatusStaysCompactEllipsizedAndSeparateFromCountdown() {
        setLargeFontContent { backdrop ->
            BaLiquidPanel(
                backdrop = backdrop,
                modifier =
                    Modifier
                        .width(360.dp)
                        .testTag(LONG_STATUS_PANEL_TAG),
                accentColor = AppStatusColors.Cached,
                effectsEnabled = true,
                pressFeedback = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BaCalendarPoolEntryStatusPill(
                        text = LONG_LOCALIZED_STATUS,
                        accentColor = AppStatusColors.Cached,
                        modifier = Modifier.testTag(LONG_STATUS_PILL_TAG),
                    )
                    Text(
                        text = LONG_COUNTDOWN,
                        color = AppStatusColors.Refreshing,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        val panelBounds = composeRule.onNodeWithTag(LONG_STATUS_PANEL_TAG).bounds()
        val pillNode = composeRule.onNodeWithTag(LONG_STATUS_PILL_TAG)
        val pillBounds = pillNode.bounds()
        val countdownBounds =
            composeRule.onNodeWithText(LONG_COUNTDOWN, useUnmergedTree = true).bounds()
        val tolerance = with(composeRule.density) { 1.dp.toPx() }
        val maximumPillWidth = with(composeRule.density) { 128.dp.toPx() }

        pillNode.assertHeightIsAtLeast(28.dp)
        composeRule.onNodeWithText(LONG_LOCALIZED_STATUS, useUnmergedTree = true).assertExists()
        assertReadOnly(pillNode)
        assertTrue(pillBounds.width <= maximumPillWidth + tolerance)
        assertTrue(pillBounds.right <= countdownBounds.left + tolerance)
        assertInside(panelBounds, pillBounds, tolerance)
        assertInside(panelBounds, countdownBounds, tolerance)
    }

    private fun setLargeFontContent(content: @Composable (Backdrop) -> Unit) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    content(rememberLayerBackdrop())
                }
            }
        }
    }
}

class BaCalendarPoolEntryStatusPillTestApp : Application()

private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

private fun assertReadOnly(node: SemanticsNodeInteraction) {
    node
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
}

private fun assertInside(outer: Rect, inner: Rect, tolerance: Float) {
    assertTrue(inner.left >= outer.left - tolerance, "Left edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.top >= outer.top - tolerance, "Top edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.right <= outer.right + tolerance, "Right edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.bottom <= outer.bottom + tolerance, "Bottom edge escaped: outer=$outer, inner=$inner")
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

private const val SERVER_INDEX = 0
private const val NOW_MS = 1_800_000_000_000L
private const val CALENDAR_BEGIN_MS = NOW_MS - 3_600_000L
private const val CALENDAR_END_MS = NOW_MS + 7_200_000L
private const val POOL_START_MS = NOW_MS + 86_400_000L
private const val POOL_END_MS = NOW_MS + 259_200_000L
private const val CALENDAR_KIND_ID = 14
private const val CALENDAR_KIND = "Event"
private const val CALENDAR_TITLE = "Long-running academy collaboration event"
private const val POOL_TAG_ID = 6
private const val POOL_TAG = "Limited"
private const val POOL_TITLE = "Featured recruitment with complete localized student names"
private const val CALENDAR_HOST_TAG = "ba-calendar-entry-host"
private const val POOL_HOST_TAG = "ba-pool-entry-host"
private const val LONG_STATUS_PANEL_TAG = "ba-calendar-pool-long-status-panel"
private const val LONG_STATUS_PILL_TAG = "ba-calendar-pool-long-status-pill"
private const val LONG_LOCALIZED_STATUS = "即将开始的长期限定活动状态说明（国际服本地化文本）"
private const val LONG_COUNTDOWN = "123天23小时后"