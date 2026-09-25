package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
class AppLiquidButtonCustomizationContractTest {
    @Test
    fun explicitContainerAlphaKeepsNeutralThemeFilmInBothThemes() {
        val lightOverlay =
            requireNotNull(
                resolveLiquidButtonContainerOverlay(
                    containerColor = Color.White.copy(alpha = 0.34f),
                    containerAlphaOverride = 0.34f,
                    variant = GlassVariant.SheetAction,
                    isDark = false,
                ),
            )
        val darkOverlay =
            requireNotNull(
                resolveLiquidButtonContainerOverlay(
                    containerColor = Color.White.copy(alpha = 0.14f),
                    containerAlphaOverride = 0.14f,
                    variant = GlassVariant.SheetAction,
                    isDark = true,
                ),
            )

        assertEquals(
            Color.White.copy(alpha = 0.34f).alpha,
            lightOverlay.alpha,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            Color.White.copy(alpha = 0.14f).alpha,
            darkOverlay.alpha,
            absoluteTolerance = 0.0001f,
        )
        listOf(lightOverlay, darkOverlay).forEach { overlay ->
            assertEquals(1f, overlay.red, absoluteTolerance = 0.001f)
            assertEquals(1f, overlay.green, absoluteTolerance = 0.001f)
            assertEquals(1f, overlay.blue, absoluteTolerance = 0.001f)
        }
    }

    @Test
    fun defaultContainerResolutionKeepsExistingVariantBehavior() {
        assertNull(
            resolveLiquidButtonContainerOverlay(
                containerColor = Color.White.copy(alpha = 0.14f),
                containerAlphaOverride = null,
                variant = GlassVariant.SheetAction,
                isDark = true,
            ),
        )

        val lightOverlay =
            resolveLiquidButtonContainerOverlay(
                containerColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                containerAlphaOverride = null,
                variant = GlassVariant.SheetAction,
                isDark = false,
            )
        val darkOverlay =
            resolveLiquidButtonContainerOverlay(
                containerColor = Color(0xFF3B82F6).copy(alpha = 0.12f),
                containerAlphaOverride = null,
                variant = GlassVariant.SheetAction,
                isDark = true,
            )

        assertEquals(
            Color.White.copy(alpha = 0.34f).alpha,
            requireNotNull(lightOverlay).alpha,
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            Color.White.copy(alpha = 0.24f).alpha,
            requireNotNull(darkOverlay).alpha,
            absoluteTolerance = 0.0001f,
        )
    }

}
