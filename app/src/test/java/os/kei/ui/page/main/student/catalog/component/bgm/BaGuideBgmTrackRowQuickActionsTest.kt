package os.kei.ui.page.main.student.catalog.component.bgm

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaGuideBgmTrackRowQuickActionsTestApp::class,
    sdk = [35],
    qualifiers = "en-w360dp-h800dp-xxhdpi",
)
class BaGuideBgmTrackRowQuickActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontPopupKeepsThreeQuickActionsInsideTheMenu() {
        setPopup()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val playAction = context.getString(R.string.ba_catalog_bgm_action_play)
        val favoriteAction = context.getString(R.string.ba_catalog_bgm_action_favorite)
        val offlineAction = context.getString(R.string.ba_catalog_bgm_action_save_offline)
        val openGalleryAction = context.getString(R.string.ba_catalog_bgm_action_open_gallery)
        val menuBounds =
            composeRule
                .onNodeWithTag(BA_GUIDE_BGM_TRACK_MENU_TEST_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val actionBounds =
            listOf(
                QUICK_PLAY_TEST_TAG to playAction,
                QUICK_FAVORITE_TEST_TAG to favoriteAction,
                QUICK_OFFLINE_TEST_TAG to offlineAction,
            ).map { (tag, contentDescription) ->
                composeRule
                    .onNodeWithTag(tag)
                    .assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp)
                    .assert(buttonRoleMatcher)
                composeRule
                    .onNodeWithContentDescription(contentDescription)
                    .assertIsDisplayed()
                    .assert(buttonRoleMatcher)
                composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            }
        val openGalleryBounds =
            composeRule
                .onNode(hasText(openGalleryAction) and buttonRoleMatcher)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val density = context.resources.displayMetrics.density
        val tolerance = density

        assertTrue(menuBounds.width >= 252.dp.value * density - tolerance)
        assertTrue(menuBounds.width <= 280.dp.value * density + tolerance)
        assertTrue(menuBounds.height <= 336.dp.value * density + tolerance)
        actionBounds.forEach { bounds ->
            assertTrue(bounds.left >= menuBounds.left - tolerance)
            assertTrue(bounds.right <= menuBounds.right + tolerance)
            assertTrue(bounds.top >= menuBounds.top - tolerance)
            assertTrue(bounds.bottom <= menuBounds.bottom + tolerance)
        }
        assertTrue(actionBounds[0].right <= actionBounds[1].left + tolerance)
        assertTrue(actionBounds[1].right <= actionBounds[2].left + tolerance)
        assertTrue(kotlin.math.abs(actionBounds[0].center.y - actionBounds[1].center.y) <= tolerance)
        assertTrue(kotlin.math.abs(actionBounds[1].center.y - actionBounds[2].center.y) <= tolerance)
        assertTrue(openGalleryBounds.left >= menuBounds.left - tolerance)
        assertTrue(openGalleryBounds.right <= menuBounds.right + tolerance)
        assertTrue(openGalleryBounds.top >= actionBounds.maxOf { bounds -> bounds.bottom } - tolerance)
        assertTrue(openGalleryBounds.bottom <= menuBounds.bottom + tolerance)
    }

    @Test
    fun openPopupSynchronizesFavoriteAndOfflineActionSemantics() {
        val favorite = mutableStateOf(false)
        val offlineSaved = mutableStateOf(false)
        setPopup(
            favorite = favorite,
            offlineSaved = offlineSaved,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        val favoriteAction = context.getString(R.string.ba_catalog_bgm_action_favorite)
        val unfavoriteAction = context.getString(R.string.ba_catalog_bgm_action_unfavorite)
        val saveOfflineAction = context.getString(R.string.ba_catalog_bgm_action_save_offline)
        val removeOfflineAction = context.getString(R.string.ba_catalog_bgm_action_remove_offline)

        composeRule.onNodeWithContentDescription(favoriteAction).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(saveOfflineAction).assertIsDisplayed()

        composeRule.runOnIdle {
            favorite.value = true
            offlineSaved.value = true
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasContentDescriptionExactly(unfavoriteAction))
                .fetchSemanticsNodes()
                .isNotEmpty() &&
                composeRule
                    .onAllNodes(hasContentDescriptionExactly(removeOfflineAction))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(favoriteAction).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(saveOfflineAction).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(unfavoriteAction)
            .assertIsDisplayed()
            .assert(buttonRoleMatcher)
        composeRule
            .onNodeWithContentDescription(removeOfflineAction)
            .assertIsDisplayed()
            .assert(buttonRoleMatcher)
    }

    @Test
    fun playQuickActionInvokesDomainActionThenDismissesOnce() {
        assertQuickActionClick(
            actionLabelRes = R.string.ba_catalog_bgm_action_play,
            expectedEvent = "play",
            onPlayClick = { events -> events += "play" },
        )
    }

    @Test
    fun favoriteQuickActionInvokesDomainActionThenDismissesOnce() {
        assertQuickActionClick(
            actionLabelRes = R.string.ba_catalog_bgm_action_favorite,
            expectedEvent = "favorite",
            onFavoriteClick = { events -> events += "favorite" },
        )
    }

    @Test
    fun offlineQuickActionInvokesDomainActionThenDismissesOnce() {
        assertQuickActionClick(
            actionLabelRes = R.string.ba_catalog_bgm_action_save_offline,
            expectedEvent = "offline",
            onOfflineClick = { events -> events += "offline" },
        )
    }

    private fun assertQuickActionClick(
        actionLabelRes: Int,
        expectedEvent: String,
        onPlayClick: (MutableList<String>) -> Unit = {},
        onFavoriteClick: (MutableList<String>) -> Unit = {},
        onOfflineClick: (MutableList<String>) -> Unit = {},
    ) {
        val show = mutableStateOf(true)
        val events = mutableListOf<String>()
        setPopup(
            show = show,
            onDismissRequest = {
                events += "dismiss"
                show.value = false
            },
            onPlayClick = { onPlayClick(events) },
            onFavoriteClick = { onFavoriteClick(events) },
            onOfflineClick = { onOfflineClick(events) },
        )
        val context = ApplicationProvider.getApplicationContext<Application>()

        composeRule
            .onNodeWithContentDescription(context.getString(actionLabelRes))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag(BA_GUIDE_BGM_TRACK_MENU_TEST_TAG))
                .fetchSemanticsNodes()
                .isEmpty()
        }

        assertEquals(listOf(expectedEvent, "dismiss"), events)
    }

    private fun setPopup(
        show: State<Boolean> = mutableStateOf(true),
        favorite: State<Boolean> = mutableStateOf(false),
        offlineSaved: State<Boolean> = mutableStateOf(false),
        onDismissRequest: () -> Unit = {},
        onPlayClick: () -> Unit = {},
        onFavoriteClick: () -> Unit = {},
        onOfflineClick: () -> Unit = {},
        onOpenGuideClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                LocalTransitionAnimationsEnabled provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    val density = LocalDensity.current
                    val anchorBounds =
                        with(density) {
                            IntRect(
                                left = 304.dp.roundToPx(),
                                top = 96.dp.roundToPx(),
                                right = 352.dp.roundToPx(),
                                bottom = 144.dp.roundToPx(),
                            )
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF3F7FB)),
                    ) {
                        BaGuideBgmTrackMorePopup(
                            show = show.value,
                            anchorBounds = anchorBounds,
                            favorite = favorite.value,
                            offlineSaved = offlineSaved.value,
                            onDismissRequest = onDismissRequest,
                            onPlayClick = onPlayClick,
                            onFavoriteClick = onFavoriteClick,
                            onOfflineClick = onOfflineClick,
                            onOpenGuideClick = onOpenGuideClick,
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasTestTag(BA_GUIDE_BGM_TRACK_MENU_TEST_TAG))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private companion object {
        val buttonRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

        const val QUICK_PLAY_TEST_TAG = "liquid_action_menu_quick_play"
        const val QUICK_FAVORITE_TEST_TAG = "liquid_action_menu_quick_favorite"
        const val QUICK_OFFLINE_TEST_TAG = "liquid_action_menu_quick_offline"
        const val BGM_TRACK_ROW_SOURCE =
            "app/src/main/java/os/kei/ui/page/main/student/catalog/component/bgm/BaGuideBgmTrackRow.kt"
        const val LIQUID_ACTION_MENU_SOURCE =
            "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/LiquidGlassActionMenu.kt"
    }
}

class BaGuideBgmTrackRowQuickActionsTestApp : Application()

private fun hasContentDescriptionExactly(value: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(value))

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
