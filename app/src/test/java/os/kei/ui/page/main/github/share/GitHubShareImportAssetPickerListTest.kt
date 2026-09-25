package os.kei.ui.page.main.github.share

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubShareImportAssetPickerListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledRowExposesOneSelectedDisabledRadioAndDoesNotClick() {
        var selectCount = 0
        setAssetPickerContent(selectionEnabled = false) { selectCount++ }

        composeRule.onAllNodes(RADIO_BUTTON).assertCountEquals(1)
        composeRule.onAllNodes(RADIO_BUTTON, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onNode(RADIO_BUTTON)
            .assertIsSelected()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(0, selectCount) }
    }

    @Test
    fun enabledRowExposesOneRadioAndClicksOnceFromTheParentCard() {
        var selectCount = 0
        setAssetPickerContent(selectionEnabled = true) { selectCount++ }

        composeRule.onAllNodes(RADIO_BUTTON).assertCountEquals(1)
        composeRule.onAllNodes(RADIO_BUTTON, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onNode(RADIO_BUTTON)
            .assertIsSelected()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, selectCount) }
    }

    @Test
    fun assetListExposesSelectableGroupSemantics() {
        setAssetPickerContent(selectionEnabled = true) {}

        composeRule.onAllNodes(SELECTABLE_GROUP).assertCountEquals(1)
    }

    @Test
    fun rowKeepsLegacyCompactHeightAndMinimumInteractionSurface() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubShareImportAssetPickerRow(
                    asset = asset,
                    supportedAbis = listOf("arm64-v8a"),
                    selected = true,
                    selectionEnabled = true,
                    onSelect = {},
                    modifier = Modifier.testTag(ROW_TAG),
                )
            }
        }

        composeRule
            .onNodeWithTag(ROW_TAG)
            .assertHeightIsEqualTo(52.dp)
        composeRule
            .onNode(RADIO_BUTTON)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun longAssetAtLargeFontKeepsTextAndCompatibilityBadgeSeparated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val compatibilityHint =
            context.getString(R.string.github_share_import_dialog_asset_hint_maybe_incompatible)
        val compatibilityBadge =
            context.getString(R.string.github_share_import_dialog_asset_badge_incompatible)
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    GitHubShareImportAssetPickerRow(
                        asset = longIncompatibleAsset,
                        supportedAbis = listOf("arm64-v8a"),
                        selected = false,
                        selectionEnabled = true,
                        onSelect = {},
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(LARGE_ROW_TAG),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag(LARGE_ROW_TAG)
            .assertWidthIsEqualTo(360.dp)

        val rowBounds =
            composeRule
                .onNodeWithTag(LARGE_ROW_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(longIncompatibleAsset.name, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNode(hasText(compatibilityHint, substring = true), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val badgeBounds =
            composeRule
                .onNodeWithText(compatibilityBadge, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(titleBounds.bottom <= summaryBounds.top)
        assertTrue(titleBounds.right <= badgeBounds.left)
        assertTrue(summaryBounds.right <= badgeBounds.left)
        listOf(titleBounds, summaryBounds, badgeBounds).forEach { bounds ->
            assertTrue(bounds.left >= rowBounds.left)
            assertTrue(bounds.top >= rowBounds.top)
            assertTrue(bounds.right <= rowBounds.right)
            assertTrue(bounds.bottom <= rowBounds.bottom)
        }
        with(composeRule.density) {
            assertTrue(rowBounds.height.toDp() <= 72.dp)
        }
    }

    private fun setAssetPickerContent(
        selectionEnabled: Boolean,
        onSelect: () -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                GitHubShareImportAssetPickerList(
                    assets = listOf(asset),
                    supportedAbis = listOf("arm64-v8a"),
                    selectedIndex = 0,
                    selectionEnabled = selectionEnabled,
                    onSelect = { onSelect() },
                )
            }
        }
    }

    private companion object {
        const val ROW_TAG = "github-share-asset-row"
        const val LARGE_ROW_TAG = "github-share-large-asset-row"
        val RADIO_BUTTON =
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        val SELECTABLE_GROUP =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        val asset =
            GitHubReleaseAssetFile(
                name = "KeiOS-arm64-v8a.apk",
                downloadUrl = "https://example.invalid/KeiOS-arm64-v8a.apk",
                sizeBytes = 1024L,
                downloadCount = 1,
            )
        val longIncompatibleAsset =
            GitHubReleaseAssetFile(
                name =
                    "KeiOS-a-deliberately-long-preview-build-for-compatibility-review-x86_64.apk",
                downloadUrl = "https://example.invalid/KeiOS-preview-x86_64.apk",
                sizeBytes = 128_000_000L,
                downloadCount = 1,
                apiAssetUrl = "https://api.example.invalid/assets/42",
            )
    }
}
