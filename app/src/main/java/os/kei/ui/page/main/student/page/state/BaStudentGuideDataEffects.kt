package os.kei.ui.page.main.student.page.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBottomTab

@Composable
internal fun BindBaStudentGuidePrefetchEffects(
    info: BaStudentGuideInfo?,
    activeBottomTab: GuideBottomTab,
    initialPrefetchCount: Int,
    galleryExtraPrefetchCount: Int,
    onSyncPrefetch: (
        BaStudentGuideInfo?,
        GuideBottomTab,
        Int,
        Int
    ) -> Unit
) {
    LaunchedEffect(
        info?.sourceUrl,
        info?.syncedAtMs,
        activeBottomTab,
        initialPrefetchCount,
        galleryExtraPrefetchCount
    ) {
        onSyncPrefetch(
            info,
            activeBottomTab,
            initialPrefetchCount,
            galleryExtraPrefetchCount
        )
    }
}
