package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.ui.page.main.student.BaGuideBgmFavoriteRepository
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.page.parseCatalogFavoritesExport
import os.kei.ui.page.main.student.catalog.page.previewCatalogFavoritesImport

internal class KeiOSJsonImportBaPlanner(
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
    private val bgmFavoriteRepository: BaGuideBgmFavoriteRepository = BaGuideBgmFavoriteRepository(),
) {
    suspend fun buildCatalogPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val current = withContext(ioDispatcher) { BaGuideCatalogStore.loadFavorites() }
        val (imported, result) = withContext(defaultDispatcher) {
            val imported = parseCatalogFavoritesExport(file.raw)
            imported to previewCatalogFavoritesImport(imported, current)
        }
        val preview = buildJsonImportBaPreview(
            context = context,
            header = header,
            totalCount = imported.size,
            validCount = imported.size,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            samples = imported.entries.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(
                    it.key.toString(),
                    context.getString(R.string.json_import_favorited_at_value, it.value)
                )
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            val currentFavorites = withContext(ioDispatcher) {
                BaGuideCatalogStore.loadFavorites()
            }
            val (nextImported, nextPreview) = withContext(defaultDispatcher) {
                val parsed = parseCatalogFavoritesExport(file.raw)
                parsed to previewCatalogFavoritesImport(parsed, currentFavorites)
            }
            if (nextImported.isNotEmpty()) {
                withContext(ioDispatcher) {
                    BaGuideCatalogStore.saveFavorites(currentFavorites + nextImported)
                }
            }
            KeiOSJsonImportApplyResult(
                addedCount = nextPreview.addedCount,
                updatedCount = nextPreview.updatedCount,
                unchangedCount = nextImported.size - nextPreview.addedCount - nextPreview.updatedCount
            )
        }
    }

    suspend fun buildBgmPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val (previewResult, samples) =
            coroutineScope {
                val previewDeferred = async { bgmFavoriteRepository.previewImport(file.raw) }
                val samplesDeferred = async(defaultDispatcher) { buildJsonImportBgmSamples(file.raw) }
                previewDeferred.await() to samplesDeferred.await()
            }
        val preview = buildJsonImportBaPreview(
            context = context,
            header = header,
            totalCount = previewResult.importedCount,
            validCount = previewResult.importedCount,
            addedCount = previewResult.addedCount,
            updatedCount = previewResult.updatedCount,
            samples = samples
        )
        return ImportableKeiOSJsonPlan(preview) {
            val result = bgmFavoriteRepository.importMerged(file.raw)
            KeiOSJsonImportApplyResult(
                addedCount = result.addedCount,
                updatedCount = result.updatedCount,
                unchangedCount = result.importedCount - result.addedCount - result.updatedCount
            )
        }
    }

    suspend fun buildAllPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val currentCatalog = withContext(ioDispatcher) { BaGuideCatalogStore.loadFavorites() }
        val (catalogPreview, bgmPreview) = coroutineScope {
            val catalogDeferred = async(defaultDispatcher) {
                val imported = parseCatalogFavoritesExport(file.raw)
                imported to previewCatalogFavoritesImport(imported, currentCatalog)
            }
            val bgmPreviewDeferred = async { bgmFavoriteRepository.previewImport(file.raw) }
            val bgmSamplesDeferred = async(defaultDispatcher) {
                buildJsonImportBgmSamples(file.raw)
            }
            catalogDeferred.await() to (bgmPreviewDeferred.await() to bgmSamplesDeferred.await())
        }
        val importedCatalog = catalogPreview.first
        val catalogResult = catalogPreview.second
        val bgmResult = bgmPreview.first
        val bgmSamples = bgmPreview.second
        val totalCount = importedCatalog.size + bgmResult.importedCount
        val preview = buildJsonImportBaPreview(
            context = context,
            header = header,
            totalCount = totalCount,
            validCount = totalCount,
            addedCount = catalogResult.addedCount + bgmResult.addedCount,
            updatedCount = catalogResult.updatedCount + bgmResult.updatedCount,
            samples = importedCatalog.entries.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(
                    it.key.toString(),
                    context.getString(R.string.json_import_ba_catalog_sample)
                )
            }.let { samples ->
                if (samples.size >= KEIOS_JSON_IMPORT_SAMPLE_LIMIT) {
                    samples
                } else {
                    samples + bgmSamples.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT - samples.size)
                }
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            val currentFavorites = withContext(ioDispatcher) {
                BaGuideCatalogStore.loadFavorites()
            }
            val (imported, catalogApplyPreview) = withContext(defaultDispatcher) {
                val parsed = parseCatalogFavoritesExport(file.raw)
                parsed to previewCatalogFavoritesImport(parsed, currentFavorites)
            }
            if (imported.isNotEmpty()) {
                withContext(ioDispatcher) {
                    BaGuideCatalogStore.saveFavorites(currentFavorites + imported)
                }
            }
            val bgmResult = bgmFavoriteRepository.importMerged(file.raw)
            KeiOSJsonImportApplyResult(
                addedCount = catalogApplyPreview.addedCount + bgmResult.addedCount,
                updatedCount = catalogApplyPreview.updatedCount + bgmResult.updatedCount,
                unchangedCount = totalCount -
                        catalogApplyPreview.addedCount -
                        catalogApplyPreview.updatedCount -
                        bgmResult.addedCount -
                        bgmResult.updatedCount
            )
        }
    }
}
