package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.max

internal const val STUDENT_BGM_BATCH_SIZE = 20
internal const val STUDENT_BGM_LOAD_MORE_THRESHOLD = 10

@Stable
internal class BaGuideStudentBgmTabStateHolder(
    private val visibleCountState: MutableIntState,
) {
    var visibleCount: Int
        get() = visibleCountState.intValue
        private set(value) {
            visibleCountState.intValue = value.coerceAtLeast(0)
        }

    fun resetVisibleCount(totalCount: Int) {
        visibleCount = minOf(totalCount.coerceAtLeast(0), STUDENT_BGM_BATCH_SIZE)
    }

    fun appendVisibleBatch(
        totalCount: Int,
        viewportItems: Int,
    ) {
        val appendBatch =
            max(STUDENT_BGM_BATCH_SIZE, viewportItems.coerceAtLeast(1) * 3)
                .coerceAtMost(STUDENT_BGM_BATCH_SIZE * 3)
        visibleCount = minOf(visibleCount + appendBatch, totalCount.coerceAtLeast(0))
    }
}

@Composable
internal fun rememberBaGuideStudentBgmTabStateHolder(searchQuery: String): BaGuideStudentBgmTabStateHolder {
    val visibleCountState = rememberSaveable(searchQuery) { mutableIntStateOf(0) }
    return remember(visibleCountState) {
        BaGuideStudentBgmTabStateHolder(visibleCountState)
    }
}
