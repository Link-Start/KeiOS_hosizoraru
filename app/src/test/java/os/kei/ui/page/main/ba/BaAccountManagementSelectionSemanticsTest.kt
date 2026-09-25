package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaAccountManagementSelectionSemanticsTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class BaAccountManagementSelectionSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accountChoicesExposeOneRadioGroupAndExactlyOneSelectedAccount() {
        val selectedAccounts = mutableListOf<BaAccountId>()
        setAccountChoices(onSelectAccount = selectedAccounts::add)

        composeRule.onAllNodes(SELECTABLE_GROUP, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodes(RADIO_BUTTON, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onAllNodes(SELECTED_RADIO, useUnmergedTree = true).assertCountEquals(1)

        val activeChoice = composeRule.onNodeWithText(activeActionLabel())
        activeChoice
            .assert(RADIO_BUTTON)
            .assertIsSelected()
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }

        val availableChoice = composeRule.onNodeWithText(useActionLabel())
        availableChoice
            .assert(RADIO_BUTTON)
            .assertIsNotSelected()
            .assertIsEnabled()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(SECOND_ACCOUNT.id), selectedAccounts)
        }
    }

    @Test
    fun accountChoiceKeepsPrimaryInformationClearAt360DpAndLargeFont() {
        var density = 1f
        setAccountChoices(
            onDensity = { density = it },
            onSelectAccount = {},
        )

        val groupBounds = composeRule.onNodeWithTag(GROUP_TAG).bounds()
        val displayNameBounds = composeRule.onNodeWithText(FIRST_ACCOUNT.displayName).bounds()
        val summaryBounds =
            composeRule
                .onNode(hasText(FIRST_ACCOUNT.friendCode, substring = true))
                .bounds()
        val choiceBounds = composeRule.onNodeWithText(activeActionLabel()).bounds()

        composeRule.runOnIdle {
            assertTrue(displayNameBounds.width >= 72f * density)
            assertTrue(summaryBounds.width >= 72f * density)
            assertTrue(displayNameBounds.right <= choiceBounds.left)
            assertTrue(summaryBounds.right <= choiceBounds.left)
            assertTrue(choiceBounds.right <= groupBounds.right)
            assertTrue(displayNameBounds.left >= groupBounds.left)
        }
    }

    private fun setAccountChoices(
        onDensity: (Float) -> Unit = {},
        onSelectAccount: (BaAccountId) -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                onDensity(baseDensity.density)
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    BaAccountSelectableGroup(
                        modifier = Modifier.testTag(GROUP_TAG),
                    ) {
                        TestAccountRow(
                            account = FIRST_ACCOUNT,
                            active = true,
                            canMoveUp = false,
                            canMoveDown = true,
                            onSelectAccount = onSelectAccount,
                        )
                        TestAccountRow(
                            account = SECOND_ACCOUNT,
                            active = false,
                            canMoveUp = true,
                            canMoveDown = false,
                            onSelectAccount = onSelectAccount,
                        )
                    }
                }
            }
        }
    }

    private fun activeActionLabel(): String =
        ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.ba_account_management_active_action)

    private fun useActionLabel(): String =
        ApplicationProvider.getApplicationContext<Application>()
            .getString(R.string.ba_account_management_use_action)

    private companion object {
        val SELECTABLE_GROUP =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
        val RADIO_BUTTON =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val SELECTED_RADIO =
            RADIO_BUTTON and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    }
}

@androidx.compose.runtime.Composable
private fun TestAccountRow(
    account: BaOfficeAccountCardUiState,
    active: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectAccount: (BaAccountId) -> Unit,
) {
    BaAccountManagementAccountRow(
        backdrop = null,
        account = account,
        active = active,
        settingsAccent = Color(0xFF3B82F6),
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        canDelete = true,
        onAccountEnabledChange = { _, _ -> },
        onSelectAccount = onSelectAccount,
        onEditAccount = {},
        onMoveAccount = {},
        onDeleteRequest = {},
    )
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.bounds(): Rect =
    fetchSemanticsNode().boundsInRoot

class BaAccountManagementSelectionSemanticsTestApp : Application()

private val FIRST_ACCOUNT =
    testAccount(
        id = "primary-account",
        displayName = "Primary Sensei Account With A Long Name",
        friendCode = "PRIMARY1",
    )
private val SECOND_ACCOUNT =
    testAccount(
        id = "secondary-account",
        displayName = "Secondary Sensei",
        friendCode = "SECOND2",
    )

private fun testAccount(
    id: String,
    displayName: String,
    friendCode: String,
): BaOfficeAccountCardUiState =
    BaOfficeAccountCardUiState(
        id = BaAccountId(id),
        displayName = displayName,
        nickname = "Sensei",
        friendCode = friendCode,
        serverIndex = 1,
        enabled = true,
        notificationMode = BaAccountNotificationMode.FollowGlobal,
        remindersEnabled = true,
        customReminderSettings = BaGlobalReminderSettings(),
    )

private const val GROUP_TAG = "ba-account-selection-group"