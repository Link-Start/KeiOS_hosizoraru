package os.kei.ui.page.main.widget.glass

import android.app.Application
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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
    fun successiveEditsKeepTheCursorAtTheInsertionPoint() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            AppTextInputContent(
                value = valueState.value,
                onValueChange = { valueState.value = it },
                label = "Search",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
            )
        }

        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick().performTextInput("g")
        assertEquals(TextRange(1), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        field.performTextInput("i")
        field.performTextInput("t")
        composeRule.runOnIdle { assertEquals("git", valueState.value) }
        assertEquals(TextRange(3), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])

        field.performTextInputSelection(TextRange(1))
        field.performTextInput("hub")
        composeRule.runOnIdle { assertEquals("ghubit", valueState.value) }
        assertEquals(TextRange(4), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
    }

    @Test
    fun sheetSearchFieldKeepsSelectionThroughMaterialRecomposition() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppLiquidSearchField(
                    value = valueState.value,
                    onValueChange = { valueState.value = it },
                    label = "Name or package",
                    backdrop = null,
                    variant = GlassVariant.SheetInput,
                )
            }
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.performClick()
        for ((index, letter) in "git".withIndex()) {
            field.performTextInput(letter.toString())
            assertEquals(TextRange(index + 1), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        }
        composeRule.runOnIdle { assertEquals("git", valueState.value) }
    }

    @Test
    fun imeComposingTextKeepsTheInsertionPointAndReplacesItsOwnComposition() {
        lateinit var valueState: MutableState<String>
        var connection: InputConnection? = null
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            InterceptPlatformTextInput(
                interceptor = { request, _ ->
                    connection = request.createInputConnection(EditorInfo())
                    awaitCancellation()
                },
            ) {
                AppTextInputContent(
                    value = valueState.value,
                    onValueChange = { valueState.value = it },
                    label = "Search",
                    style = inputStyle(),
                    fieldModifier = Modifier.testTag(FIELD_TAG),
                )
            }
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        composeRule.waitUntil { connection != null }
        for (text in listOf("g", "gi", "git")) {
            composeRule.runOnIdle { assertTrue(connection!!.setComposingText(text, 1)) }
            composeRule.waitForIdle()
            assertEquals(TextRange(text.length), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            composeRule.runOnIdle { assertEquals(text, valueState.value) }
        }
        composeRule.runOnIdle { assertTrue(connection!!.commitText("git", 1)) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("git", valueState.value) }
    }

    @Test
    fun portalledFieldKeepsSelectionWhenItsOwnerEchoesTextBack() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            val host = remember { LiquidOverlayHostState() }
            val value = valueState.value
            CompositionLocalProvider(LocalLiquidOverlayHost provides host) {
                LiquidOverlayPortal {
                    AppTextInputContent(
                        value = value,
                        onValueChange = { valueState.value = it },
                        label = "Search",
                        style = inputStyle(),
                        fieldModifier = Modifier.testTag(FIELD_TAG),
                    )
                }
            }
            host.Content()
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        for ((index, letter) in "git".withIndex()) {
            field.performTextInput(letter.toString())
            assertEquals(TextRange(index + 1), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        }
        composeRule.runOnIdle { assertEquals("git", valueState.value) }
    }

    @Test
    fun aDelayedModelEchoDoesNotClampAwayTheEditorsSelection() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            AppTextInputContent(
                value = valueState.value,
                onValueChange = { text ->
                    scope.launch { withFrameNanos { }; valueState.value = text }
                },
                label = "Search",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
            )
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        for ((index, letter) in "git".withIndex()) {
            field.performTextInput(letter.toString())
            assertEquals(TextRange(index + 1), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        }
        composeRule.runOnIdle { assertEquals("git", valueState.value) }
    }

    @Test
    fun delayedImeEchoPreservesTheCompositionUntilItIsCommitted() {
        lateinit var valueState: MutableState<String>
        var connection: InputConnection? = null
        composeRule.setContent {
            valueState = remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            InterceptPlatformTextInput(
                interceptor = { request, _ ->
                    connection = request.createInputConnection(EditorInfo())
                    awaitCancellation()
                },
            ) {
                AppTextInputContent(
                    value = valueState.value,
                    onValueChange = { text ->
                        scope.launch { withFrameNanos { }; valueState.value = text }
                    },
                    label = "Search",
                    style = inputStyle(),
                    fieldModifier = Modifier.testTag(FIELD_TAG),
                )
            }
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        composeRule.waitUntil { connection != null }
        for (text in listOf("n", "ni", "nih", "nihao")) {
            composeRule.runOnIdle { assertTrue(connection!!.setComposingText(text, 1)) }
            composeRule.waitForIdle()
            assertEquals(TextRange(text.length), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            composeRule.runOnIdle { assertEquals(text, valueState.value) }
        }
        composeRule.runOnIdle { assertTrue(connection!!.commitText("你好", 1)) }
        composeRule.waitForIdle()
        assertEquals(AnnotatedString("你好"), field.fetchSemanticsNode().config[SemanticsProperties.EditableText])
        assertEquals(TextRange(2), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
    }

    @Test
    fun normalizationCanRejectInputWithoutLeavingTheRejectedTextInTheEditor() {
        lateinit var valueState: MutableState<String>
        composeRule.setContent {
            valueState = remember { mutableStateOf("12") }
            AppTextInputContent(
                value = valueState.value,
                onValueChange = { valueState.value = it.filter(Char::isDigit) },
                label = "Number",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
            )
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        field.performTextInputSelection(TextRange(2))
        field.performTextInput("a")
        assertEquals(AnnotatedString("12"), field.fetchSemanticsNode().config[SemanticsProperties.EditableText])
        field.performTextInput("3")
        composeRule.runOnIdle { assertEquals("123", valueState.value) }
        assertEquals(TextRange(3), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
    }

    @Test
    fun externalClearResetsSelectionAndSelectionOnlyEditsDoNotPublishText() {
        lateinit var valueState: MutableState<String>
        var changes = 0
        composeRule.setContent {
            valueState = remember { mutableStateOf("hello") }
            AppTextInputContent(
                value = valueState.value,
                onValueChange = { changes++; valueState.value = it },
                label = "Search",
                style = inputStyle(),
                fieldModifier = Modifier.testTag(FIELD_TAG),
            )
        }
        val field = composeRule.onNodeWithTag(FIELD_TAG, useUnmergedTree = true)
        field.performClick()
        field.performTextInputSelection(TextRange(2, 4))
        composeRule.runOnIdle { assertEquals(0, changes); valueState.value = "" }
        assertEquals(TextRange(0), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        field.performTextInput("g")
        composeRule.runOnIdle { assertEquals("g", valueState.value); assertEquals(1, changes) }
    }

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
