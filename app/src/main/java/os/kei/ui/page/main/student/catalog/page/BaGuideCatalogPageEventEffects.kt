@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogEvent

@Composable
internal fun BaGuideCatalogPageEventEffects(
    context: Context,
    events: Flow<BaGuideCatalogEvent>,
    onImportPreviewStateChange: (BaGuideCatalogImportPreviewState?) -> Unit,
) {
    val latestOnImportPreviewStateChange = rememberUpdatedState(onImportPreviewStateChange)
    LaunchedEffect(context, events) {
        events.collect { event ->
            when (event) {
                is BaGuideCatalogEvent.BgmCacheBatchDone -> {
                    context.showToast(
                        context.resolveString(
                            R.string.ba_catalog_bgm_cache_batch_done,
                            event.targetCount,
                        ),
                    )
                }

                is BaGuideCatalogEvent.BgmCacheCleaned -> {
                    context.showToast(
                        context.resolveString(
                            R.string.ba_catalog_bgm_cache_cleaned,
                            event.cleanedCount,
                        ),
                    )
                }

                BaGuideCatalogEvent.FavoriteBgmCacheFailed -> {
                    context.showToast(context.resolveString(R.string.ba_catalog_bgm_cache_failed))
                }

                BaGuideCatalogEvent.FavoriteBgmCacheRemoved -> {
                    context.showToast(context.resolveString(R.string.ba_catalog_bgm_cache_removed))
                }

                BaGuideCatalogEvent.FavoriteBgmCacheSuccess -> {
                    context.showToast(context.resolveString(R.string.ba_catalog_bgm_cache_success))
                }

                BaGuideCatalogEvent.BgmFavoriteAdded -> {
                    context.showToast(context.resolveString(R.string.guide_bgm_toast_favorite_added))
                }

                BaGuideCatalogEvent.BgmFavoriteRemoved -> {
                    context.showToast(context.resolveString(R.string.guide_bgm_toast_favorite_removed))
                }

                is BaGuideCatalogEvent.CatalogImportApplied -> {
                    latestOnImportPreviewStateChange.value(null)
                    context.showToast(context.resolveCatalogImportAppliedMessage(event))
                }

                is BaGuideCatalogEvent.CatalogImportFailed -> {
                    val errorRes =
                        if (event.kind == BaGuideCatalogImportKind.Bgm) {
                            R.string.ba_catalog_bgm_import_failed
                        } else {
                            R.string.ba_catalog_transfer_import_failed
                        }
                    context.showToast(context.resolveString(errorRes))
                }

                is BaGuideCatalogEvent.CatalogImportPreviewReady -> {
                    latestOnImportPreviewStateChange.value(event.preview)
                }
            }
        }
    }
}

private fun Context.resolveCatalogImportAppliedMessage(event: BaGuideCatalogEvent.CatalogImportApplied): String =
    when (event.kind) {
        BaGuideCatalogImportKind.All -> {
            resolveString(
                R.string.ba_catalog_transfer_all_import_success,
                event.studentCount,
                event.bgmAddedCount,
                event.bgmUpdatedCount,
            )
        }

        BaGuideCatalogImportKind.Student -> {
            resolveString(
                R.string.ba_catalog_transfer_student_import_success,
                event.studentCount,
            )
        }

        BaGuideCatalogImportKind.Bgm -> {
            resolveString(
                R.string.ba_catalog_bgm_import_success,
                event.bgmAddedCount,
                event.bgmUpdatedCount,
            )
        }
    }
