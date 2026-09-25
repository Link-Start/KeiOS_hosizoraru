package os.kei.core.ui.debug

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import os.kei.ui.testing.repoSource

class DebugFpsOverlayBackdropContractTest {
    @Test
    fun debugTelemetryStaysOutsideTheSceneBackdropProducer() {
        val source = repoSource(MAIN_ACTIVITY_SOURCE)
        val sceneBackdropBlock = source.trailingLambdaBlock("SceneBackdropHost")

        assertTrue(
            "MainScreen(" in sceneBackdropBlock,
            "The scene backdrop must continue capturing the real app content",
        )
        assertFalse(
            "DebugFpsOverlay(" in sceneBackdropBlock,
            "Continuously updating debug telemetry must stay outside Liquid Glass samples",
        )
        assertTrue(
            source.indexOf("DebugFpsOverlay()") > source.indexOf(sceneBackdropBlock),
            "The FPS overlay must still render after the scene producer",
        )
    }
}

private fun String.trailingLambdaBlock(functionName: String): String {
    val callStart = indexOf("$functionName(")
    require(callStart >= 0) { "Unable to locate $functionName(" }
    val blockStart = indexOf('{', startIndex = callStart)
    require(blockStart >= 0) { "Unable to locate the trailing lambda for $functionName" }

    var depth = 1
    var index = blockStart + 1
    while (index < length && depth > 0) {
        when (this[index]) {
            '{' -> depth += 1
            '}' -> depth -= 1
        }
        index += 1
    }
    require(depth == 0) { "Unable to locate the closing brace for $functionName" }
    return substring(blockStart, index)
}

private const val MAIN_ACTIVITY_SOURCE = "app/src/main/java/os/kei/MainActivity.kt"
