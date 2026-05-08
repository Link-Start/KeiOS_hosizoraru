package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import os.kei.ui.page.main.model.BottomPage

@Stable
internal class MainPageActivationState internal constructor(
    private val activatedPages: Map<BottomPage, Boolean>,
    private val readyPages: Map<BottomPage, Boolean>
) {
    fun hasActivated(page: BottomPage): Boolean = activatedPages[page] == true

    fun contentReady(page: BottomPage): Boolean = readyPages[page] == true
}

@Composable
internal fun rememberMainPageActivationState(
    tabs: List<BottomPage>,
    settledPageIndex: Int
): MainPageActivationState {
    val activatedPages = remember { mutableStateMapOf<BottomPage, Boolean>() }
    val readyPages = remember { mutableStateMapOf<BottomPage, Boolean>() }
    val settledPage = tabs.getOrNull(settledPageIndex)

    LaunchedEffect(tabs, settledPage) {
        val page = settledPage ?: return@LaunchedEffect
        activatedPages[page] = true
        if (readyPages[page] != true) {
            withFrameNanos { }
            readyPages[page] = true
        }
    }

    return remember(
        activatedPages.toMap(),
        readyPages.toMap()
    ) {
        MainPageActivationState(
            activatedPages = activatedPages.toMap(),
            readyPages = readyPages.toMap()
        )
    }
}
