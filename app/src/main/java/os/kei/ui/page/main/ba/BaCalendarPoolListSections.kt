@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.card.BaCalendarEntryPanel
import os.kei.ui.page.main.ba.card.BaCalendarStatePanel
import os.kei.ui.page.main.ba.card.BaPoolEntryPanel
import os.kei.ui.page.main.ba.card.BaPoolStatePanel
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BaCalendarPoolContentStatus {
    Loading,
    Error,
    Refreshing,
    Empty,
}

internal fun resolveBaCalendarPoolContentStatus(
    visibleEntryCount: Int,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
): BaCalendarPoolContentStatus? {
    val hasVisibleEntries = visibleEntryCount > 0
    return if (hasVisibleEntries) {
        when {
            !error.isNullOrBlank() -> BaCalendarPoolContentStatus.Error
            loading || refreshing -> BaCalendarPoolContentStatus.Refreshing
            else -> null
        }
    } else {
        when {
            loading || refreshing -> BaCalendarPoolContentStatus.Loading
            !error.isNullOrBlank() -> BaCalendarPoolContentStatus.Error
            else -> BaCalendarPoolContentStatus.Empty
        }
    }
}

internal fun baCalendarPoolSyncNoticeColor(hasVisibleEntries: Boolean): Color =
    if (hasVisibleEntries) AppStatusColors.Cached else AppStatusColors.Failed

internal fun LazyListScope.baActivityCalendarEntryItems(
    backdrop: Backdrop,
    serverIndex: Int,
    visibleEntries: List<BaCalendarEntry>,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    showEndedActivities: Boolean,
    showCalendarPoolImages: Boolean,
    nowMs: Long,
    syncTextColor: Color,
    onOpenCalendarLink: (String) -> Unit,
) {
    val status =
        resolveBaCalendarPoolContentStatus(
            visibleEntryCount = visibleEntries.size,
            loading = loading,
            refreshing = refreshing,
            error = error,
        )
    when (status) {
        BaCalendarPoolContentStatus.Loading -> {
            item(
                key = "ba-calendar-loading",
                contentType = "ba_calendar_status",
            ) {
                AppAronaLoadingPanel(accent = syncTextColor)
            }
        }

        BaCalendarPoolContentStatus.Error -> {
            item(
                key = "ba-calendar-error",
                contentType = "ba_calendar_status",
            ) {
                BaCalendarStatePanel(
                    backdrop = backdrop,
                    text = error.orEmpty(),
                    accentColor =
                        baCalendarPoolSyncNoticeColor(
                            hasVisibleEntries = visibleEntries.isNotEmpty(),
                        ),
                    effectsEnabled = true,
                )
            }
        }

        BaCalendarPoolContentStatus.Refreshing -> {
            item(
                key = "ba-calendar-refreshing",
                contentType = "ba_calendar_status",
            ) {
                BaCalendarStatePanel(
                    backdrop = backdrop,
                    text = stringResource(R.string.ba_syncing),
                    accentColor = syncTextColor,
                    effectsEnabled = true,
                )
            }
        }

        BaCalendarPoolContentStatus.Empty -> {
            item(
                key = "ba-calendar-empty-$showEndedActivities",
                contentType = "ba_calendar_status",
            ) {
                BaCalendarStatePanel(
                    backdrop = backdrop,
                    text =
                        if (showEndedActivities) {
                            stringResource(R.string.ba_calendar_empty_all)
                        } else {
                            stringResource(R.string.ba_calendar_empty_active)
                        },
                    accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    effectsEnabled = true,
                )
            }
        }

        null -> Unit
    }
    items(
        items = visibleEntries,
        key = { activity -> activity.id },
        contentType = { "ba_calendar_entry" },
    ) { activity ->
        BaCalendarEntryPanel(
            backdrop = backdrop,
            isPageActive = true,
            serverIndex = serverIndex,
            activity = activity,
            nowMs = nowMs,
            showCalendarPoolImages = showCalendarPoolImages,
            effectsEnabled = true,
            onOpenCalendarLink = onOpenCalendarLink,
        )
    }
}

internal fun LazyListScope.baPoolEntryItems(
    backdrop: Backdrop,
    serverIndex: Int,
    visibleEntries: List<BaPoolEntry>,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    showEndedPools: Boolean,
    showCalendarPoolImages: Boolean,
    nowMs: Long,
    syncTextColor: Color,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenCalendarLink: (String) -> Unit,
) {
    val status =
        resolveBaCalendarPoolContentStatus(
            visibleEntryCount = visibleEntries.size,
            loading = loading,
            refreshing = refreshing,
            error = error,
        )
    when (status) {
        BaCalendarPoolContentStatus.Loading -> {
            item(
                key = "ba-pool-loading",
                contentType = "ba_pool_status",
            ) {
                AppAronaLoadingPanel(accent = syncTextColor)
            }
        }

        BaCalendarPoolContentStatus.Error -> {
            item(
                key = "ba-pool-error",
                contentType = "ba_pool_status",
            ) {
                BaPoolStatePanel(
                    backdrop = backdrop,
                    text = error.orEmpty(),
                    accentColor =
                        baCalendarPoolSyncNoticeColor(
                            hasVisibleEntries = visibleEntries.isNotEmpty(),
                        ),
                    effectsEnabled = true,
                )
            }
        }

        BaCalendarPoolContentStatus.Refreshing -> {
            item(
                key = "ba-pool-refreshing",
                contentType = "ba_pool_status",
            ) {
                BaPoolStatePanel(
                    backdrop = backdrop,
                    text = stringResource(R.string.ba_syncing),
                    accentColor = syncTextColor,
                    effectsEnabled = true,
                )
            }
        }

        BaCalendarPoolContentStatus.Empty -> {
            item(
                key = "ba-pool-empty-$showEndedPools",
                contentType = "ba_pool_status",
            ) {
                BaPoolStatePanel(
                    backdrop = backdrop,
                    text =
                        if (showEndedPools) {
                            stringResource(R.string.ba_pool_empty_all)
                        } else {
                            stringResource(R.string.ba_pool_empty_active)
                        },
                    accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    effectsEnabled = true,
                )
            }
        }

        null -> Unit
    }
    items(
        items = visibleEntries,
        key = { pool -> pool.id },
        contentType = { "ba_pool_entry" },
    ) { pool ->
        BaPoolEntryPanel(
            backdrop = backdrop,
            isPageActive = true,
            serverIndex = serverIndex,
            pool = pool,
            nowMs = nowMs,
            showCalendarPoolImages = showCalendarPoolImages,
            effectsEnabled = true,
            onOpenPoolStudentGuide = onOpenPoolStudentGuide,
            onOpenCalendarLink = onOpenCalendarLink,
        )
    }
}
