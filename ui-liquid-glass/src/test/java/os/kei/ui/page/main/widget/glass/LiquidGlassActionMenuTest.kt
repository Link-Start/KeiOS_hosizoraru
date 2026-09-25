@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidGlassActionMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingSubmenuChoiceCallsChoiceAndDismissesMenu() {
        var selectedSort = "update"
        var selectedInterval = "3h"
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 360.dp, height = 420.dp)
                            .background(Color(0xFFF3F4F6))
                            .padding(24.dp),
                ) {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort",
                                    text = "排序",
                                    subtitle = "更新优先",
                                    submenuItems =
                                        listOf(
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "update",
                                                text = "更新优先",
                                                selected = selectedSort == "update",
                                                onClick = { selectedSort = "update" },
                                            ),
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "name",
                                                text = "名称 A-Z",
                                                selected = selectedSort == "name",
                                                onClick = { selectedSort = "name" },
                                            ),
                                        ),
                                ),
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "interval",
                                    text = "更新间隔",
                                    subtitle = "3 小时",
                                    submenuItems =
                                        listOf(
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "3h",
                                                text = "3 小时",
                                                selected = selectedInterval == "3h",
                                                onClick = { selectedInterval = "3h" },
                                            ),
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "6h",
                                                text = "6 小时",
                                                selected = selectedInterval == "6h",
                                                onClick = { selectedInterval = "6h" },
                                            ),
                                        ),
                                ),
                            ),
                        onDismissRequest = { dismissCount += 1 },
                    )
                }
            }
        }

        composeRule.onNode(hasText("排序") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("名称 A-Z").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNode(hasText("名称 A-Z") and hasClickAction())
            .assertIsDisplayed()
            .performClick()

        assertEquals("name", selectedSort)
        assertEquals(1, dismissCount)

        composeRule.onNode(hasText("更新间隔") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("6 小时").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNode(hasText("6 小时") and hasClickAction())
            .assertIsDisplayed()
            .performClick()

        assertEquals("6h", selectedInterval)
        assertEquals(2, dismissCount)
    }

    @Test
    fun multipleChoiceRowExposesCheckboxStateAndReportsTheNextValue() {
        var requestedValue: Boolean? = null
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuMultipleChoiceRow(
                                    id = "compact_rows",
                                    text = "紧凑行",
                                    checked = true,
                                    onCheckedChange = { checked -> requestedValue = checked },
                                ),
                            ),
                        onDismissRequest = { dismissCount += 1 },
                    )
                }
            }
        }

        val checkboxRole =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        composeRule
            .onNode(hasText("紧凑行") and hasClickAction())
            .assert(checkboxRole)
            .assertIsOn()
            .performClick()

        assertEquals(false, requestedValue)
        assertEquals(1, dismissCount)
    }

    @Test
    fun submenuMultipleChoiceUpdatesInPlaceAndKeepsMenuOpen() {
        var checked by mutableStateOf(true)
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "rarity",
                                    text = "星级",
                                    submenuItems =
                                        listOf(
                                            LiquidGlassActionMenuMultipleChoiceRow(
                                                id = "three_star",
                                                text = "三星",
                                                checked = checked,
                                                onCheckedChange = { nextValue -> checked = nextValue },
                                            ),
                                        ),
                                ),
                            ),
                        onDismissRequest = { dismissCount += 1 },
                    )
                }
            }
        }

        composeRule.onNode(hasText("星级") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("三星").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule
            .onNode(hasText("三星") and hasClickAction())
            .assertIsOn()
            .performClick()
        composeRule
            .onNode(hasText("三星") and hasClickAction())
            .assertIsOff()
            .assertIsDisplayed()

        assertEquals(false, checked)
        assertEquals(0, dismissCount)
        composeRule.onNode(hasText("星级") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun actionRowCanRunWithoutDismissingMenu() {
        var actionCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuActionRow(
                                    id = "clear",
                                    text = "清除筛选",
                                    dismissOnClick = false,
                                    onClick = { actionCount += 1 },
                                ),
                            ),
                        onDismissRequest = { dismissCount += 1 },
                    )
                }
            }
        }

        composeRule
            .onNode(hasText("清除筛选") and hasClickAction())
            .performClick()
            .assertIsDisplayed()

        assertEquals(1, actionCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun replacingItemsWithSameSubmenuIdKeepsSubmenuExpanded() {
        var itemRevision by mutableIntStateOf(0)
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    val revision = itemRevision
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort",
                                    text = "排序 ${revision + 1}",
                                    submenuItems =
                                        listOf(
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "name",
                                                text = "名称版本 ${revision + 1}",
                                                selected = false,
                                                onClick = { itemRevision = revision + 1 },
                                            ),
                                        ),
                                ),
                            ),
                    )
                }
            }
        }

        composeRule.onNode(hasText("排序 1") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("名称版本 1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { itemRevision = 1 }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("名称版本 2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("名称版本 2") and hasClickAction()).assertIsDisplayed()
        composeRule.onNode(hasText("排序 2") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun backFromSubmenuReturnsToMainMenuBeforeOuterDismiss() {
        var dismissCount = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val localBackDispatcher =
                    checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                        .onBackPressedDispatcher
                SideEffect {
                    backDispatcher = localBackDispatcher
                }
                BackHandler { dismissCount += 1 }
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuSubmenuRow(
                                    id = "sort",
                                    text = "排序",
                                    submenuItems =
                                        listOf(
                                            LiquidGlassActionMenuSingleChoiceRow(
                                                id = "name",
                                                text = "名称 A-Z",
                                                selected = false,
                                                onClick = {},
                                            ),
                                        ),
                                ),
                                LiquidGlassActionMenuActionRow(
                                    id = "refresh",
                                    text = "刷新",
                                    onClick = {},
                                ),
                            ),
                        onDismissRequest = { dismissCount += 1 },
                    )
                }
            }
        }

        composeRule.onNode(hasText("排序") and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("名称 A-Z").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("刷新").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, dismissCount)
        composeRule.onNode(hasText("刷新") and hasClickAction()).assertIsDisplayed()

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, dismissCount)
    }

    @Test
    fun quickActionExposesExactlyOneButtonLabelAndDisabledState() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        quickActions =
                            listOf(
                                LiquidGlassActionMenuQuickAction(
                                    id = "info",
                                    icon = MiuixIcons.Regular.Info,
                                    label = "信息",
                                    contentDescription = "查看信息",
                                    enabled = false,
                                    testTag = "quick-info",
                                    onClick = {},
                                ),
                            ),
                        items = emptyList(),
                    )
                }
            }
        }

        val expectedQuickActionSemantics =
            hasContentDescription("查看信息") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
                isNotEnabled()
        val quickActionNodes =
            composeRule
                .onAllNodes(
                    hasTestTag("quick-info"),
                ).assertAll(expectedQuickActionSemantics)
                .fetchSemanticsNodes()
        val labelledNodes =
            composeRule
                .onAllNodes(
                    hasContentDescription("查看信息"),
                ).fetchSemanticsNodes()
        assertEquals(1, quickActionNodes.size)
        assertEquals(1, labelledNodes.size)
        composeRule.onAllNodesWithText("信息", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun infoRowReadsAsPassiveTextWhileDisabledActionKeepsButtonState() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                ActionMenuTestSurface {
                    LiquidGlassActionMenu(
                        items =
                            listOf(
                                LiquidGlassActionMenuInfoRow(
                                    id = "refresh_scope",
                                    text = INFO_TITLE,
                                    modifier = Modifier.testTag(INFO_ROW_TAG),
                                    leadingIcon = MiuixIcons.Regular.Info,
                                    subtitle = INFO_SUBTITLE,
                                ),
                                LiquidGlassActionMenuActionRow(
                                    id = "full_refresh",
                                    text = DISABLED_ACTION_TITLE,
                                    subtitle = DISABLED_ACTION_SUBTITLE,
                                    leadingIcon = MiuixIcons.Regular.Info,
                                    enabled = false,
                                    onClick = {},
                                ),
                            ),
                    )
                }
            }
        }

        val infoNode = composeRule.onNode(hasTestTag(INFO_ROW_TAG) and hasText(INFO_TITLE))
        val spokenText =
            infoNode
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString(separator = " ") { text -> text.text }

        assertPassiveInfoSemantics(infoNode)
        assertTrue(INFO_TITLE in spokenText)
        assertTrue(INFO_SUBTITLE in spokenText)

        composeRule
            .onNode(hasText(DISABLED_ACTION_TITLE) and isNotEnabled())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }

    @Test
    fun infoRowMatchesCompactActionGeometryAtLargeFontOn360Dp() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 360.dp, height = 420.dp)
                                .testTag(LARGE_FONT_HOST_TAG)
                                .background(Color(0xFFF3F4F6))
                                .padding(24.dp),
                    ) {
                        LiquidGlassActionMenu(
                            modifier = Modifier.testTag(LARGE_FONT_MENU_TAG),
                            items =
                                listOf(
                                    LiquidGlassActionMenuInfoRow(
                                        id = "long_info",
                                        text = LONG_INFO_TITLE,
                                        modifier = Modifier.testTag(LARGE_FONT_INFO_TAG),
                                        leadingIcon = MiuixIcons.Regular.Info,
                                        subtitle = LONG_INFO_SUBTITLE,
                                    ),
                                    LiquidGlassActionMenuActionRow(
                                        id = "long_disabled",
                                        text = LONG_DISABLED_TITLE,
                                        subtitle = LONG_DISABLED_SUBTITLE,
                                        leadingIcon = MiuixIcons.Regular.Info,
                                        enabled = false,
                                        onClick = {},
                                    ),
                                ),
                        )
                    }
                }
            }
        }

        val hostBounds = composeRule.onNodeWithTag(LARGE_FONT_HOST_TAG).bounds()
        val menuBounds = composeRule.onNodeWithTag(LARGE_FONT_MENU_TAG).bounds()
        val infoNode =
            composeRule.onNode(
                hasTestTag(LARGE_FONT_INFO_TAG) and hasText(LONG_INFO_TITLE),
            )
        val infoBounds = infoNode.bounds()
        val disabledBounds =
            composeRule
                .onNode(hasText(LONG_DISABLED_TITLE) and isNotEnabled())
                .bounds()
        val tolerance = with(composeRule.density) { 1.dp.toPx() }
        val maximumMenuWidth = with(composeRule.density) { 312.dp.toPx() }

        fun textBoundsInside(
            text: String,
            parent: Rect,
        ): Rect =
            composeRule
                .onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { node -> node.boundsInRoot }
                .single { bounds -> bounds.isInside(parent, tolerance) }
        val infoTitleBounds = textBoundsInside(LONG_INFO_TITLE, infoBounds)
        val infoSubtitleBounds = textBoundsInside(LONG_INFO_SUBTITLE, infoBounds)
        val disabledTitleBounds = textBoundsInside(LONG_DISABLED_TITLE, disabledBounds)
        val disabledSubtitleBounds = textBoundsInside(LONG_DISABLED_SUBTITLE, disabledBounds)

        assertPassiveInfoSemantics(infoNode)
        infoNode.assertHeightIsAtLeast(42.dp)
        assertInside(hostBounds, menuBounds, tolerance)
        assertInside(menuBounds, infoBounds, tolerance)
        assertInside(menuBounds, disabledBounds, tolerance)
        assertTrue(menuBounds.width <= maximumMenuWidth + tolerance)
        assertTrue(infoBounds.bottom <= disabledBounds.top + tolerance)
        assertTrue(abs(infoBounds.height - disabledBounds.height) <= tolerance)
        assertTrue(abs(infoTitleBounds.left - disabledTitleBounds.left) <= tolerance)
        assertTrue(abs(infoSubtitleBounds.left - disabledSubtitleBounds.left) <= tolerance)
        assertTrue(infoSubtitleBounds.top >= infoTitleBounds.bottom - tolerance)
        assertTrue(disabledSubtitleBounds.top >= disabledTitleBounds.bottom - tolerance)
    }
}

@Composable
private fun ActionMenuTestSurface(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(width = 360.dp, height = 420.dp)
                .background(Color(0xFFF3F4F6))
                .padding(24.dp),
    ) {
        content()
    }
}

private fun assertPassiveInfoSemantics(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
    node
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        .assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

private fun assertInside(
    outer: Rect,
    inner: Rect,
    tolerance: Float,
) {
    assertTrue(inner.left >= outer.left - tolerance, "Left edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.top >= outer.top - tolerance, "Top edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.right <= outer.right + tolerance, "Right edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.bottom <= outer.bottom + tolerance, "Bottom edge escaped: outer=$outer, inner=$inner")
}

private fun Rect.isInside(
    outer: Rect,
    tolerance: Float,
): Boolean =
    left >= outer.left - tolerance &&
        top >= outer.top - tolerance &&
        right <= outer.right + tolerance &&
        bottom <= outer.bottom + tolerance

private const val INFO_ROW_TAG = "liquid-action-menu-info-row"
private const val LARGE_FONT_HOST_TAG = "liquid-action-menu-large-font-host"
private const val LARGE_FONT_MENU_TAG = "liquid-action-menu-large-font-menu"
private const val LARGE_FONT_INFO_TAG = "liquid-action-menu-large-font-info"
private const val INFO_TITLE = "刷新范围"
private const val INFO_SUBTITLE = "图鉴总览专用；活动与卡池请前往 BA 设置"
private const val DISABLED_ACTION_TITLE = "自动全量刷新"
private const val DISABLED_ACTION_SUBTITLE = "功能仍在准备中"
private const val LONG_INFO_TITLE = "当前页面展示范围与数据刷新来源说明"
private const val LONG_INFO_SUBTITLE = "这是一段较长的本地化被动说明文本"
private const val LONG_DISABLED_TITLE = "当前不可使用的自动全量刷新动作"
private const val LONG_DISABLED_SUBTITLE = "功能准备完成后即可执行"
