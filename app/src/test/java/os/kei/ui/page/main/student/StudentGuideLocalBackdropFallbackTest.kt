package os.kei.ui.page.main.student

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.student.section.GuideSkillVariantBadge
import os.kei.ui.page.main.student.section.gallery.GuideAudioSeekBar
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileValueCapsule
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
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
class StudentGuideLocalBackdropFallbackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standaloneFallbacksKeepBadgeCapsuleAndSeekSemantics() {
        var capsuleClicks = 0
        var capsuleLongClicks = 0
        var changedProgress = 0f
        var finishedProgress = 0f

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(
                    LocalLiquidControlsEnabled provides true,
                    LocalLiquidParentBackdrop provides null,
                    LocalTextCopyExpandedOverride provides false,
                ) {
                    Column {
                        GuideSkillVariantBadge(
                            label = "3",
                            modifier = Modifier.testTag("guide-skill-variant-badge"),
                        )
                        GuideProfileValueCapsule(
                            label = "Profile value",
                            tint = Color(0xFF3B82F6),
                            onClick = { capsuleClicks++ },
                            onLongClick = { capsuleLongClicks++ },
                        )
                        GuideAudioSeekBar(
                            progress = 0.25f,
                            enabled = true,
                            onSeekStarted = {},
                            onSeekChanged = { changedProgress = it },
                            onSeekFinished = { finishedProgress = it },
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("guide-skill-variant-badge")
            .assertWidthIsEqualTo(26.dp)
            .assertHeightIsEqualTo(26.dp)
        composeRule.onNodeWithText("3").assertExists()
        composeRule
            .onNodeWithText("Profile value")
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithText("Profile value")
            .performTouchInput { longClick() }

        val context = ApplicationProvider.getApplicationContext<Application>()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.ba_catalog_bgm_seekbar))
            .assertHeightIsEqualTo(48.dp)
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(1, capsuleClicks)
            assertEquals(1, capsuleLongClicks)
            assertTrue(changedProgress > 0f)
            assertTrue(finishedProgress > 0f)
        }
    }

    @Test
    fun studentGuideComponentsContainNoLocalLayerBackdropProducer() {
        val skillSource = sourceFile(GUIDE_SECTION_SKILL_SOURCE)
        val profileSource = sourceFile(GUIDE_PROFILE_UI_SOURCE)
        val gallerySource = sourceFile(GUIDE_GALLERY_EXPRESSION_SOURCE)

        listOf(skillSource, profileSource, gallerySource).forEach { source ->
            assertFalse("rememberLayerBackdrop" in source)
            assertFalse(".layerBackdrop(" in source)
        }
        val profileSurfaceSource =
            profileSource
                .substringAfter("private fun GuideProfileLiquidSurfaceBox(")
                .substringBefore("internal fun GuideProfileRowsSection(")
        assertTrue("AppSurfaceBox(" in profileSurfaceSource)
        assertFalse("LiquidSurface(" in profileSurfaceSource)
        assertFalse("activeGlassBackdrop(" in profileSurfaceSource)
        assertFalse("appSquircleBackground" in profileSurfaceSource)
        assertTrue("isInteractive = false" in profileSurfaceSource)
        assertTrue("shadow = false" in profileSurfaceSource)
        assertTrue("clipContent = false" in profileSurfaceSource)
        assertTrue("pressSafePadding = 0.dp" in profileSurfaceSource)
        assertTrue("effectVariant = GlassVariant.Compact" in profileSurfaceSource)
        assertTrue(".matchParentSize()\n                    .padding(contentPadding)" in profileSurfaceSource)
        assertTrue(
            "val sliderBackdrop = activeGlassBackdrop(LocalLiquidParentBackdrop.current)" in gallerySource,
        )
        assertTrue(".height(48.dp)" in gallerySource)
        assertTrue(".matchParentSize()\n                    .padding(horizontal = 4.dp)" in gallerySource)
    }

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

private const val GUIDE_SECTION_SKILL_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/section/GuideSectionSkill.kt"

private const val GUIDE_PROFILE_UI_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/tabcontent/profile/GuideProfileUi.kt"

private const val GUIDE_GALLERY_EXPRESSION_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/section/gallery/GuideGalleryExpressionSection.kt"
