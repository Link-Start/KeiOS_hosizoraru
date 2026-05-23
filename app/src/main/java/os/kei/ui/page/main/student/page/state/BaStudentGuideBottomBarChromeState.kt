@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController

@Stable
internal class BaStudentGuideBottomBarChromeState {
    var visible by mutableStateOf(true)
        private set
    var activePageCanScrollBackward by mutableStateOf(false)
        private set
    var activePageCanScrollForward by mutableStateOf(false)
        private set

    fun updateVisible(nextVisible: Boolean) {
        if (visible != nextVisible) {
            visible = nextVisible
        }
    }

    fun showNow(controller: ScrollChromeVisibilityController) {
        controller.showNow(visible, ::updateVisible)
    }

    fun updateScrollBounds(
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
    ) {
        if (
            activePageCanScrollBackward == canScrollBackward &&
            activePageCanScrollForward == canScrollForward
        ) {
            return
        }
        activePageCanScrollBackward = canScrollBackward
        activePageCanScrollForward = canScrollForward
    }

    fun showForStaticContent(controller: ScrollChromeVisibilityController) {
        controller.showForStaticContent(
            visible = visible,
            canScrollBackward = activePageCanScrollBackward,
            canScrollForward = activePageCanScrollForward,
            onVisibleChange = ::updateVisible,
        )
    }
}

@Composable
internal fun rememberBaStudentGuideBottomBarChromeState(): BaStudentGuideBottomBarChromeState =
    remember {
        BaStudentGuideBottomBarChromeState()
    }
