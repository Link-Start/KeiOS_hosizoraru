package os.kei.ui.page.main.github.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

internal object GitHubShareImportWindowChrome {
    const val BlurBehindRadius = 30
}

@Composable
internal fun GitHubShareImportWindowBlurEffect(
    useBlur: Boolean = true,
    blurRadius: Int = GitHubShareImportWindowChrome.BlurBehindRadius
) {
    val window = currentShareImportWindow() ?: return
    val blurEnabledBySystem = rememberCrossWindowBlurEnabled()

    DisposableEffect(window, useBlur, blurRadius, blurEnabledBySystem) {
        if (useBlur && blurEnabledBySystem) {
            window.applyShareImportBlur(blurRadius)
        } else {
            window.clearShareImportBlur()
        }
        onDispose {
            window.clearShareImportBlur()
        }
    }
}

@Composable
private fun currentShareImportWindow(): Window? {
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    return dialogWindow ?: view.context.findActivity()?.window
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun rememberCrossWindowBlurEnabled(): Boolean {
    val context = LocalContext.current
    val windowManager = remember(context) {
        context.getSystemService(WindowManager::class.java)
    }
    var enabled by remember(windowManager) {
        mutableStateOf(windowManager.isCrossWindowBlurEnabled)
    }

    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { value ->
            enabled = value
        }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
        }
    }

    return enabled
}

private fun Window.applyShareImportBlur(radius: Int) {
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes = attributes.apply {
        dimAmount = 0f
        blurBehindRadius = radius.coerceIn(0, 150)
    }
}

private fun Window.clearShareImportBlur() {
    clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes = attributes.apply {
        blurBehindRadius = 0
    }
}
