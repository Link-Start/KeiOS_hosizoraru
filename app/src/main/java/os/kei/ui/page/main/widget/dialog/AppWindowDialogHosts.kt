@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import os.kei.core.prefs.UiPrefs
import top.yukonga.miuix.kmp.utils.RemovePlatformDialogDefaultEffects
import top.yukonga.miuix.kmp.utils.platformDialogProperties

@Composable
fun AppWindowDialogHost(
    show: Boolean,
    onDismissRequest: (() -> Unit)? = null,
    dismissible: Boolean = true,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Route to Liquid Glass Dialog when the user preference is enabled.
    if (UiPrefs.isLiquidDialogEnabled()) {
        LiquidGlassDialog(
            show = show,
            onDismissRequest = onDismissRequest,
            dismissible = dismissible,
            onDismissFinished = onDismissFinished,
            content = content,
        )
        return
    }

    if (!show) return

    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismissRequest?.invoke()
            }
        },
        properties = platformDialogProperties(),
    ) {
        RemovePlatformDialogDefaultEffects()
        content()
    }
}
