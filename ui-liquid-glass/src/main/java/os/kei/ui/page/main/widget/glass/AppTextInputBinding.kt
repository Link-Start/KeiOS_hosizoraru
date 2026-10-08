package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.input.TextFieldValue

internal class AppTextInputBinding(
    val value: TextFieldValue,
    val onValueChange: (TextFieldValue) -> Unit,
)

/** Keeps editor metadata across the extra composition needed to publish text into an overlay. */
@Composable
internal fun rememberAppTextInputBinding(
    value: String,
    onValueChange: (String) -> Unit,
): AppTextInputBinding {
    var editorValue by remember { mutableStateOf(TextFieldValue(value)) }
    var lastModelText by remember { mutableStateOf(value) }
    var editRevision by remember { mutableIntStateOf(0) }
    val pendingTexts = remember { mutableListOf<String>() }
    val currentModelText by rememberUpdatedState(value)

    SideEffect {
        if (value != lastModelText) {
            lastModelText = value
            val acknowledgedEdit = pendingTexts.indexOf(value)
            if (acknowledgedEdit >= 0) {
                // An older acknowledged edit must not replace newer text still being typed.
                repeat(acknowledgedEdit + 1) { pendingTexts.removeAt(0) }
            } else if (value != editorValue.text) {
                pendingTexts.clear()
                editorValue = editorValue.copy(text = value, composition = null)
            }
        }
    }

    LaunchedEffect(editRevision) {
        if (editRevision == 0) return@LaunchedEffect
        // String callbacks promise a model echo by the next frame. A portal adds one composition
        // hop. Keep the live editor intact through both, then honor a rejected/normalized edit
        // even when the owner deliberately kept exactly the same String (e.g. digits-only input).
        withFrameNanos { }
        withFrameNanos { }
        if (pendingTexts.isNotEmpty()) {
            pendingTexts.clear()
            if (currentModelText != editorValue.text) {
                editorValue = editorValue.copy(text = currentModelText, composition = null)
            }
        }
    }

    return AppTextInputBinding(editorValue) { next ->
        val textChanged = next.text != editorValue.text
        editorValue = next
        if (textChanged) {
            pendingTexts.add(next.text)
            editRevision++
            onValueChange(next.text)
        }
    }
}
