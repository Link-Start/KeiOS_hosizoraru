package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppTextInputContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editableFieldKeepsLeadingContentOutsideItsValueAndSelectionSemantics() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("echo ready") }
            AppTextInputContent(
                value = valueState.value,
                onValueChange = { valueState.value = it },
                label = "Command",
                style = inputStyle(),
                modifier = Modifier.testTag(ROOT_TAG),
                fieldModifier = Modifier.testTag(FIELD_TAG),
                singleLine = false,
                leadingContent = {
                    BasicText(
                        text = "$",
                        modifier = Modifier.testTag(LEADING_TAG),
                    )
                },
            )
        }

        composeRule.onAllNodesWithText("$", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag(LEADING_TAG, useUnmergedTree = true)
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        val initialSemantics = field.fetchSemanticsNode().config
        assertEquals(
            AnnotatedString("echo ready"),
            initialSemantics[SemanticsProperties.EditableText],
        )
        assertTrue(initialSemantics.contains(SemanticsProperties.TextSelectionRange))
        assertTrue(initialSemantics.contains(SemanticsActions.SetText))

        field.performTextReplacement("pwd")

        composeRule.runOnIdle { assertEquals("pwd", valueState.value) }
        assertEquals(
            AnnotatedString("pwd"),
            field.fetchSemanticsNode().config[SemanticsProperties.EditableText],
        )
    }

    @Test
    fun focusAndImeActionAreForwardedThroughTheBareContentLayer() {
        var focused = false
        var searchActions = 0
        composeRule.setContent {
            AppTextInputContent(
                value = "query",
                onValueChange = {},
                label = "Search",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchActions++ }),
                onFocusActiveChange = { focused = it },
            )
        }

        composeRule
            .onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
            .performClick()
            .assertIsFocused()
            .performImeAction()

        composeRule.runOnIdle {
            assertTrue(focused)
            assertEquals(1, searchActions)
        }
    }

    @Test
    fun disabledFieldRetainsTextAndReportsDisabledWithoutSetText() {
        composeRule.setContent {
            AppTextInputContent(
                value = "disabled",
                onValueChange = {},
                label = "Command",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
                enabled = false,
            )
        }

        val field =
            composeRule
                .onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
                .assertIsNotEnabled()
        val semantics = field.fetchSemanticsNode().config
        assertEquals(AnnotatedString("disabled"), semantics[SemanticsProperties.EditableText])
        assertFalse(semantics.contains(SemanticsActions.SetText))
    }

    @Test
    fun readOnlyFieldRetainsSelectionSemanticsWithoutSetText() {
        composeRule.setContent {
            AppTextInputContent(
                value = "read only",
                onValueChange = {},
                label = "Command",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
                readOnly = true,
            )
        }

        val field =
            composeRule
                .onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
                .assertIsEnabled()
        val semantics = field.fetchSemanticsNode().config
        assertEquals(AnnotatedString("read only"), semantics[SemanticsProperties.EditableText])
        assertTrue(semantics.contains(SemanticsProperties.TextSelectionRange))
        assertFalse(semantics.contains(SemanticsActions.SetText))
    }
}

private fun inputStyle(): AppTextInputContentStyle =
    AppTextInputContentStyle(
        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, lineHeight = 22.sp),
        placeholderColor = Color.Gray,
        cursorColor = Color.Blue,
        leadingContentGap = 8.dp,
    )

private const val ROOT_TAG = "app-text-input-content"
private const val FIELD_TAG = "app-text-input-field"
private const val LEADING_TAG = "app-text-input-leading"
