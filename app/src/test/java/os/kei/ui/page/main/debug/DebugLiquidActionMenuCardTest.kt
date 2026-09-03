package os.kei.ui.page.main.debug

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = DebugLiquidActionMenuCardTestApp::class,
    sdk = [35],
    qualifiers = "zh-rCN-w360dp-h800dp-xxhdpi",
)
class DebugLiquidActionMenuCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun componentLabKeepsQualitySingleChoiceAndAddsLocalizedNestedMultipleChoice() {
        val source = sourceFile(DEBUG_LIQUID_ACTION_MENU_SOURCE)
        val qualitySubmenu =
            source.itemBody(
                constructor = "LiquidGlassActionMenuSubmenuRow",
                id = "quality",
            )
        val playbackOptionsSubmenu =
            source.itemBody(
                constructor = "LiquidGlassActionMenuSubmenuRow",
                id = "playback_options",
            )

        assertTrue("LiquidGlassActionMenuSingleChoiceRow(" in qualitySubmenu)
        assertTrue("selectedQualityIndex == index" in qualitySubmenu)
        assertEquals(
            3,
            source.occurrencesOf(
                "stringResource(R.string.debug_component_lab_liquid_action_menu_quality_",
            ),
        )
        assertEquals(
            2,
            playbackOptionsSubmenu.occurrencesOf("LiquidGlassActionMenuMultipleChoiceRow("),
        )
        assertTrue("id = \"show_lyrics\"" in playbackOptionsSubmenu)
        assertTrue("checked = showLyricsSelected" in playbackOptionsSubmenu)
        assertTrue("id = \"normalize_volume\"" in playbackOptionsSubmenu)
        assertTrue("checked = normalizeVolumeSelected" in playbackOptionsSubmenu)
        assertTrue(
            "highlighted = showLyricsSelected || normalizeVolumeSelected" in
                playbackOptionsSubmenu,
        )
        assertFalse("onDismissRequest" in playbackOptionsSubmenu)
        assertFalse("expanded = false" in playbackOptionsSubmenu)

        ACTION_MENU_STRING_SOURCES.forEach { path ->
            val strings = sourceFile(path)
            ACTION_MENU_MULTIPLE_CHOICE_STRING_NAMES.forEach { name ->
                assertTrue("name=\"$name\"" in strings, "$path is missing $name")
            }
        }
    }

    @Test
    fun nestedMultipleChoiceExposesTwoCheckboxesAndStaysOpenAfterToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openLabel = context.getString(R.string.debug_component_lab_liquid_action_menu_open)
        val playbackOptionsLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_playback_options)
        val showLyricsLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_show_lyrics)
        val normalizeVolumeLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_normalize_volume)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density, fontScale = 1.5f),
                    LocalTransitionAnimationsEnabled provides false,
                ) {
                    val backdrop = rememberLayerBackdrop()
                    Box(
                        modifier =
                            Modifier
                                .size(width = 360.dp, height = 800.dp)
                                .background(Color(0xFFF3F4F6))
                                .layerBackdrop(backdrop),
                    ) {
                        DebugLiquidActionMenuCard(
                            accent = MiuixTheme.colorScheme.primary,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasText(openLabel) and buttonRoleMatcher).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasText(playbackOptionsLabel) and buttonRoleMatcher)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNode(hasText(playbackOptionsLabel) and buttonRoleMatcher)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(checkboxRoleMatcher).fetchSemanticsNodes().size == 2
        }

        composeRule.onAllNodes(checkboxRoleMatcher).assertCountEquals(2)
        composeRule
            .onNode(hasText(showLyricsLabel) and checkboxRoleMatcher)
            .assertIsOn()
            .performClick()
            .assertIsOff()
            .assertIsDisplayed()
        composeRule
            .onNode(hasText(normalizeVolumeLabel) and checkboxRoleMatcher)
            .assertIsOff()
            .performClick()
            .assertIsOn()
            .assertIsDisplayed()

        composeRule.onAllNodes(checkboxRoleMatcher).assertCountEquals(2)
        composeRule
            .onNode(hasText(playbackOptionsLabel) and buttonRoleMatcher)
            .assertIsDisplayed()
    }

    private companion object {
        val buttonRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val checkboxRoleMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasClickAction()
    }
}

class DebugLiquidActionMenuCardTestApp : Application()

private fun String.itemBody(
    constructor: String,
    id: String,
): String {
    val idIndex = indexOf("id = \"$id\",")
    require(idIndex >= 0) { "Unable to locate item id=$id" }
    val itemStart = lastIndexOf("$constructor(", startIndex = idIndex)
    require(itemStart >= 0) { "Unable to locate $constructor with id=$id" }

    var depth = 0
    var insideString = false
    var escaped = false
    for (index in itemStart until length) {
        val character = this[index]
        when {
            escaped -> escaped = false
            character == '\\' && insideString -> escaped = true
            character == '"' -> insideString = !insideString
            insideString -> Unit
            character == '(' -> depth += 1
            character == ')' -> {
                depth -= 1
                if (depth == 0) return substring(itemStart, index + 1)
            }
        }
    }
    error("Unbalanced $constructor call with id=$id")
}

private fun String.occurrencesOf(needle: String): Int =
    windowed(needle.length).count { candidate -> candidate == needle }

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

private const val DEBUG_LIQUID_ACTION_MENU_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/debug/DebugLiquidActionMenuCard.kt"
private val ACTION_MENU_STRING_SOURCES =
    listOf(
        "app/src/main/res/values/strings_about.xml",
        "app/src/main/res/values-zh-rCN/strings_about.xml",
        "app/src/main/res/values-en/strings_about.xml",
        "app/src/main/res/values-ja/strings_about.xml",
    )
private val ACTION_MENU_MULTIPLE_CHOICE_STRING_NAMES =
    listOf(
        "debug_component_lab_liquid_action_menu_playback_options",
        "debug_component_lab_liquid_action_menu_playback_options_summary",
        "debug_component_lab_liquid_action_menu_show_lyrics",
        "debug_component_lab_liquid_action_menu_normalize_volume",
    )
