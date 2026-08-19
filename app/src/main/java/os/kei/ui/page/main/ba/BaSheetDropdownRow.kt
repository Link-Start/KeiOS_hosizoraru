@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.sheet.SheetControlRow

/**
 * A labelled row whose control is a dropdown over [options].
 *
 * The popup's expansion and anchor live here rather than in the caller, because they are presentation
 * with no bearing on what the sheet is editing — hoisting them would put two pieces of throwaway state
 * per row into every sheet that wants a picker. Shared by the craft slot editor and the daily-done
 * template editor, which between them are the same row eight times over.
 */
@Composable
internal fun BaSheetDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    SheetControlRow(label = label) {
        AppDropdownSelector(
            selectedText = options.getOrElse(selectedIndex) { options.first() },
            options = options,
            selectedIndex = selectedIndex,
            expanded = expanded && enabled,
            anchorBounds = anchorBounds,
            onExpandedChange = { expanded = it && enabled },
            onSelectedIndexChange = {
                expanded = false
                onSelectedIndexChange(it)
            },
            onAnchorBoundsChange = { anchorBounds = it },
            backdrop = backdrop,
        )
    }
}
