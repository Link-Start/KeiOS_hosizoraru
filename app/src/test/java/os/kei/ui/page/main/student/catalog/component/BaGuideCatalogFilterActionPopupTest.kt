package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterOption
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaGuideCatalogFilterActionPopupTestApp::class,
    sdk = [35],
    qualifiers = "zh-rCN-w360dp-h800dp-xxhdpi",
)
class BaGuideCatalogFilterActionPopupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontPopupKeepsCheckboxSubmenuOpenAndClearActionInPlace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clearText = context.getString(R.string.ba_catalog_filter_clear)
        var selectedOptions by mutableStateOf(mapOf(LONG_FILTER_ID to setOf(SELECTED_OPTION_ID)))
        var clearCount = 0
        var dismissCount = 0

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                LocalTransitionAnimationsEnabled provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    BaGuideCatalogFilterActionPopup(
                        show = true,
                        anchorBounds = IntRect(left = 180, top = 100, right = 300, bottom = 180),
                        definitions = filterDefinitions,
                        selectedOptionIdsByFilterId = selectedOptions,
                        onDismissRequest = { dismissCount += 1 },
                        onToggleOption = { filterId, optionId ->
                            val current = selectedOptions[filterId].orEmpty()
                            val updated =
                                if (optionId in current) {
                                    current - optionId
                                } else {
                                    current + optionId
                                }
                            selectedOptions = selectedOptions + (filterId to updated)
                        },
                        onClearFilters = {
                            clearCount += 1
                            selectedOptions = emptyMap()
                        },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasText(LONG_FILTER_LABEL) and buttonRoleMatcher)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNode(hasText(LONG_FILTER_LABEL) and buttonRoleMatcher)
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(checkboxRoleMatcher).fetchSemanticsNodes().size == LONG_OPTION_COUNT
        }

        composeRule
            .onNode(hasText(SELECTED_OPTION_LABEL) and checkboxRoleMatcher)
            .assertIsOn()
        composeRule
            .onNode(hasText(LONG_OPTION_LABEL) and checkboxRoleMatcher)
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
            .assertIsDisplayed()
        assertEquals(0, dismissCount)

        val menuGeometry =
            composeRule
                .onNodeWithTag(BaCatalogFilterMenuTestTag)
                .fetchSemanticsNode()
                .boundsInRoot
                .let { bounds ->
                    with(composeRule.density) {
                        bounds.width.toDp() to bounds.height.toDp()
                    }
                }
        assertTrue(
            menuGeometry.first in EXPECTED_MENU_MIN_WIDTH..EXPECTED_MENU_MAX_WIDTH,
            "Expected compact popup width, geometry=$menuGeometry",
        )

        composeRule
            .onNode(hasText(LONG_FILTER_LABEL) and buttonRoleMatcher)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(checkboxRoleMatcher).fetchSemanticsNodes().isEmpty()
        }
        composeRule
            .onNode(hasText(clearText) and buttonRoleMatcher)
            .performClick()
            .assertIsNotEnabled()
            .assertIsDisplayed()

        assertEquals(1, clearCount)
        assertEquals(0, dismissCount)
        assertTrue(selectedOptions.isEmpty())
    }

    private companion object {
        val buttonRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val checkboxRoleMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasClickAction()
    }
}

class BaGuideCatalogFilterActionPopupTestApp : Application()

private const val LONG_FILTER_ID = 1
private const val SELECTED_OPTION_ID = 101
private const val LONG_OPTION_COUNT = 12
private val EXPECTED_MENU_MIN_WIDTH = 160.dp
private val EXPECTED_MENU_MAX_WIDTH = 200.dp
private const val LONG_FILTER_LABEL = "非常长的学生所属学院筛选分类名称"
private const val SELECTED_OPTION_LABEL = "阿拜多斯高中"
private const val LONG_OPTION_LABEL = "一个用于验证放大字体和紧凑弹层宽度的非常长选项名称"

private val filterDefinitions =
    listOf(
        BaGuideCatalogFilterDefinition(
            id = LONG_FILTER_ID,
            name = LONG_FILTER_LABEL,
            type = 0,
            options =
                List(LONG_OPTION_COUNT) { index ->
                    BaGuideCatalogFilterOption(
                        id = SELECTED_OPTION_ID + index,
                        name =
                            when (index) {
                                0 -> SELECTED_OPTION_LABEL
                                LONG_OPTION_COUNT - 1 -> LONG_OPTION_LABEL
                                else -> "学院选项 ${index + 1}"
                            },
                    )
                },
        ),
        BaGuideCatalogFilterDefinition(
            id = 2,
            name = "星级",
            type = 0,
            options = listOf(BaGuideCatalogFilterOption(id = 201, name = "三星")),
        ),
    )

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
