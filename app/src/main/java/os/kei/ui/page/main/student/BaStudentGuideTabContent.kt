package os.kei.ui.page.main.student

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import os.kei.ui.page.main.widget.support.buildTextCopyPayload
import os.kei.ui.page.main.widget.support.copyModeAwareRow
import os.kei.ui.page.main.widget.support.rememberLightTextCopyAction

internal fun buildGuideTabCopyPayload(
    key: String,
    value: String,
): String = buildTextCopyPayload(key, value)

@Composable
internal fun rememberGuideTabCopyAction(copyPayload: String): () -> Unit {
    val quickCopyAction = rememberLightTextCopyAction(copyPayload)
    return remember(quickCopyAction) {
        { quickCopyAction?.invoke() }
    }
}

internal fun Modifier.guideTabCopyable(
    copyPayload: String,
    onClick: (() -> Unit)? = null,
): Modifier =
    composed {
        this.copyModeAwareRow(
            copyPayload = copyPayload,
            onClick = onClick,
        )
    }
