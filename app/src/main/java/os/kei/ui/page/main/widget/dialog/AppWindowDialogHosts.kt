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
    content: @Composable () -> Unit
) {
    if (!show) return

    // Route to Liquid Glass Dialog when the user preference is enabled.
    if (UiPrefs.isLiquidDialogEnabled()) {
        LiquidGlassDialog(
            show = true,
            onDismissRequest = onDismissRequest,
            dismissible = dismissible,
            content = content
        )
        return
    }

    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismissRequest?.invoke()
            }
        },
        properties = platformDialogProperties()
    ) {
        RemovePlatformDialogDefaultEffects()
        content()
    }
}
