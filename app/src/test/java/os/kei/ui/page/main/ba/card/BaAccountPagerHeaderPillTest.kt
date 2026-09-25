package os.kei.ui.page.main.ba.card

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import os.kei.ui.page.main.ba.BaOfficeAccountCardUiState
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.widget.status.AppStatusColors
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
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class BaAccountPagerHeaderPillTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactPillsKeepLegacyDensityAndReadOnlySemanticsAtLargeFont() {
        var density = 1f

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                density = baseDensity.density
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    Column {
                        BaAccountServerBadge(
                            text = SERVER_LABEL,
                            accentColor = AppStatusColors.Cached,
                            modifier = Modifier.testTag(SERVER_PILL_TAG),
                        )
                        BaAccountCountChip(
                            text = PAGE_LABEL,
                            accentColor = AppStatusColors.Cached,
                            modifier = Modifier.testTag(PAGE_PILL_TAG),
                        )
                        BaAccountDisabledBadge(
                            modifier = Modifier.testTag(DISABLED_PILL_TAG),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(SERVER_PILL_TAG).assertHeightIsAtLeast(24.dp)
        composeRule
            .onNodeWithTag(PAGE_PILL_TAG)
            .assertHeightIsAtLeast(24.dp)
            .assertWidthIsAtLeast(42.dp)
        composeRule.onNodeWithTag(DISABLED_PILL_TAG).assertHeightIsAtLeast(24.dp)

        val pillBounds =
            listOf(SERVER_PILL_TAG, PAGE_PILL_TAG, DISABLED_PILL_TAG).map { tag ->
                composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            }
        composeRule.runOnIdle {
            pillBounds.forEach { bounds ->
                assertTrue(bounds.height < 36f * density)
            }
        }

        listOf(SERVER_PILL_TAG, PAGE_PILL_TAG, DISABLED_PILL_TAG).forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
                .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
                .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        }
    }

    @Test
    fun accountHeaderKeepsAllContentOrderedAt360DpAndLargeFont() {
        var density = 1f
        val disabledLabel =
            ApplicationProvider.getApplicationContext<Application>()
                .getString(R.string.ba_account_disabled_badge)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                density = baseDensity.density
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    Column(modifier = Modifier.width(328.dp)) {
                        BaAccountOfficeHeader(
                            modifier = Modifier.testTag(HEADER_TAG),
                            title = TITLE_LABEL,
                            displayName = DISPLAY_NAME_LABEL,
                            serverName = SERVER_LABEL,
                            enabled = false,
                            pageLabel = PAGE_LABEL,
                            accentColor = AppStatusColors.Cached,
                        )
                    }
                }
            }
        }

        val headerBounds = composeRule.onNodeWithTag(HEADER_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds = composeRule.onNodeWithText(TITLE_LABEL).fetchSemanticsNode().boundsInRoot
        val displayNameBounds =
            composeRule.onNodeWithText(DISPLAY_NAME_LABEL).fetchSemanticsNode().boundsInRoot
        val serverBounds = composeRule.onNodeWithText(SERVER_LABEL).fetchSemanticsNode().boundsInRoot
        val disabledBounds =
            composeRule.onNodeWithText(disabledLabel).fetchSemanticsNode().boundsInRoot
        val pageBounds = composeRule.onNodeWithText(PAGE_LABEL).fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            assertTrue(titleBounds.width > 0f)
            assertTrue(displayNameBounds.width > 0f)
            assertTrue(titleBounds.left >= headerBounds.left)
            assertTrue(titleBounds.right <= displayNameBounds.left)
            assertTrue(displayNameBounds.right <= serverBounds.left)
            assertTrue(serverBounds.right + 14f * density <= disabledBounds.left)
            assertTrue(disabledBounds.right + 14f * density <= pageBounds.left)
            assertTrue(pageBounds.right <= headerBounds.right)
            assertTrue(headerBounds.height < 40f * density)
        }
    }

    @Test
    fun accountHeaderTapAndLongPressBothOpenTheExistingEditor() {
        val editRequests = mutableListOf<BaAccountId>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val officeTitle = context.getString(R.string.ba_office_name_global)
        val editActionLabel = context.getString(R.string.ba_account_management_edit_action)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                BaAccountPagerCard(
                    backdrop = null,
                    accounts = listOf(INTERACTIVE_ACCOUNT),
                    activeAccountId = INTERACTIVE_ACCOUNT.id,
                    serverOptions = listOf("China", "Global", "Japan"),
                    onAccountSelected = {},
                    onEditAccount = editRequests::add,
                )
            }
        }

        val header = composeRule.onNodeWithText(officeTitle)
        header
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))

        val semantics = header.fetchSemanticsNode().config
        assertEquals(editActionLabel, semantics[SemanticsActions.OnClick].label)
        assertEquals(editActionLabel, semantics[SemanticsActions.OnLongClick].label)

        header.performClick()
        header.performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(
                listOf(INTERACTIVE_ACCOUNT.id, INTERACTIVE_ACCOUNT.id),
                editRequests,
            )
        }
    }

}

private const val TITLE_LABEL = "Schale Office (Global)"
private const val DISPLAY_NAME_LABEL = "Sensei Account"
private const val SERVER_LABEL = "Global"
private const val PAGE_LABEL = "10/10"
private const val HEADER_TAG = "ba-account-header"
private const val SERVER_PILL_TAG = "ba-account-server-pill"
private const val PAGE_PILL_TAG = "ba-account-page-pill"
private const val DISABLED_PILL_TAG = "ba-account-disabled-pill"
private val INTERACTIVE_ACCOUNT =
    BaOfficeAccountCardUiState(
        id = BaAccountId("interactive-account"),
        displayName = "Interactive account",
        nickname = "Sensei",
        friendCode = "GLOBAL01",
        serverIndex = 1,
        enabled = true,
        notificationMode = BaAccountNotificationMode.FollowGlobal,
        remindersEnabled = true,
        customReminderSettings = BaGlobalReminderSettings(),
    )