package os.kei.ui.page.main.jsonimport

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.hasSameGitHubTrackingConfigIgnoringLocalAppType
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.transfer.OsCardBundleImportPayload
import os.kei.ui.page.main.os.transfer.OsCardTransferService
import os.kei.ui.page.main.settings.support.formatBytes
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.page.parseCatalogFavoritesExport
import os.kei.ui.page.main.student.catalog.page.previewCatalogFavoritesImport
import java.io.ByteArrayOutputStream

internal class KeiOSJsonImportRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun buildPlan(
        context: Context,
        source: KeiOSJsonImportIntentSource,
        onStage: (KeiOSJsonImportStage) -> Unit
    ): KeiOSJsonImportPlan {
        val appContext = context.applicationContext
        val startedAtMs = System.currentTimeMillis()
        AppLogger.i(TAG, "json import buildPlan start source=${source.displayName}")
        onStage(KeiOSJsonImportStage.Reading)
        val file = readSource(appContext, source)
        val plan = try {
            onStage(KeiOSJsonImportStage.Detecting)
            val header = withContext(defaultDispatcher) {
                KeiOSJsonImportRouter.inspect(file.raw)
            }
            onStage(KeiOSJsonImportStage.Parsing)
            when (header.kind) {
                KeiOSJsonImportKind.GitHubTracked -> buildGitHubPlan(appContext, file, header)
                KeiOSJsonImportKind.OsActivityCards -> buildOsActivityPlan(appContext, file, header)
                KeiOSJsonImportKind.OsShellCards -> buildOsShellPlan(appContext, file, header)
                KeiOSJsonImportKind.OsCardsBundle -> buildOsBundlePlan(appContext, file, header)
                KeiOSJsonImportKind.BaCatalogFavorites -> buildBaCatalogPlan(
                    appContext,
                    file,
                    header
                )

                KeiOSJsonImportKind.BaBgmFavorites -> buildBaBgmPlan(appContext, file, header)
                KeiOSJsonImportKind.BaAllFavorites -> buildBaAllPlan(appContext, file, header)
                KeiOSJsonImportKind.McpLogs,
                KeiOSJsonImportKind.OsInfoCard -> buildReadOnlyPlan(appContext, file, header)

                KeiOSJsonImportKind.Unknown -> throw KeiOSJsonImportException(
                    KeiOSJsonImportFailureReason.UnsupportedFormat
                )
            }
        } catch (error: KeiOSJsonImportException) {
            throw error
        } catch (error: Throwable) {
            throw KeiOSJsonImportException(
                reason = KeiOSJsonImportFailureReason.ParseFailed,
                cause = error
            )
        }
        AppLogger.i(
            TAG,
            "json import preview ready kind=${plan.preview.kind} count=${plan.preview.totalCount} ms=${System.currentTimeMillis() - startedAtMs}"
        )
        return plan
    }

    fun sourceFromIntent(context: Context, intent: Intent?): KeiOSJsonImportIntentSource {
        if (intent == null) {
            return KeiOSJsonImportIntentSource(displayName = context.getString(R.string.json_import_source_unknown))
        }
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: intent.data
        if (streamUri != null) {
            val metadata = queryUriMetadata(context, streamUri)
            return KeiOSJsonImportIntentSource(
                uri = streamUri,
                displayName = metadata.displayName.ifBlank {
                    streamUri.lastPathSegment?.substringAfterLast('/').orEmpty()
                },
                mimeType = intent.type.orEmpty().ifBlank { metadata.mimeType },
                declaredSizeBytes = metadata.sizeBytes
            )
        }
        val inlineText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (inlineText != null) {
            return KeiOSJsonImportIntentSource(
                inlineText = inlineText,
                displayName = context.getString(R.string.json_import_inline_text_name),
                mimeType = intent.type.orEmpty(),
                declaredSizeBytes = inlineText.toByteArray(Charsets.UTF_8).size.toLong()
            )
        }
        return KeiOSJsonImportIntentSource(displayName = context.getString(R.string.json_import_source_unknown))
    }

    private suspend fun readSource(
        context: Context,
        source: KeiOSJsonImportIntentSource
    ): KeiOSJsonImportFile {
        return withContext(ioDispatcher) {
            val inlineText = source.inlineText
            if (inlineText != null) {
                val bytes = inlineText.toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty()) {
                    throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.EmptyFile)
                }
                if (bytes.size > KEIOS_JSON_IMPORT_MAX_BYTES) {
                    throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.FileTooLarge)
                }
                return@withContext KeiOSJsonImportFile(
                    raw = inlineText,
                    displayName = source.displayName,
                    mimeType = source.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    sourceDescription = context.getString(R.string.json_import_source_inline_text)
                )
            }
            val uri = source.uri
                ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.MissingSource)
            val metadata = queryUriMetadata(context, uri)
            val declaredSize = listOf(source.declaredSizeBytes, metadata.sizeBytes)
                .firstOrNull { it > 0L }
                ?: -1L
            if (declaredSize > KEIOS_JSON_IMPORT_MAX_BYTES) {
                throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.FileTooLarge)
            }
            val bytes = readUriBytesLimited(context, uri)
            if (bytes.isEmpty()) {
                throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.EmptyFile)
            }
            KeiOSJsonImportFile(
                raw = bytes.toString(Charsets.UTF_8),
                displayName = source.displayName.ifBlank { metadata.displayName },
                mimeType = source.mimeType.ifBlank { metadata.mimeType },
                sizeBytes = bytes.size.toLong(),
                sourceDescription = uri.toString()
            )
        }
    }

    private suspend fun readUriBytesLimited(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > KEIOS_JSON_IMPORT_MAX_BYTES) {
                    throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.FileTooLarge)
                }
                output.write(buffer, 0, read)
                if (total % YIELD_EVERY_BYTES < read) {
                    yield()
                }
            }
            output.toByteArray()
        } ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.ReadFailed)
    }

    private suspend fun buildGitHubPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val payload =
            withContext(defaultDispatcher) { GitHubTrackStore.parseTrackedItemsImport(file.raw) }
        val existing = withContext(ioDispatcher) { GitHubTrackStore.load() }
        val preview = withContext(defaultDispatcher) {
            buildGitHubPreview(
                context,
                header,
                payload,
                existing
            )
        }
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) { applyGitHubImport(context, payload) }
        }
    }

    private suspend fun buildOsActivityPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val defaults = buildOsDefaults(context)
        val payload = withContext(defaultDispatcher) {
            OsCardTransferService.parseActivityImportPayload(
                raw = file.raw,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val existing = withContext(ioDispatcher) {
            OsActivityShortcutCardStore.loadCards(defaults.system, defaults.googleSettingsSample)
        }
        val result = withContext(defaultDispatcher) {
            OsCardTransferService.previewActivityImport(
                payload = payload,
                existingCards = existing,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val preview = buildOsPreview(
            context = context,
            header = header,
            totalCount = payload.sourceCount,
            validCount = payload.cards.size,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            samples = payload.cards.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(it.config.title, it.config.packageName)
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val current = OsActivityShortcutCardStore.loadCards(
                    defaults.system,
                    defaults.googleSettingsSample
                )
                val applyResult = OsCardTransferService.applyActivityImport(
                    payload = payload,
                    existingCards = current,
                    defaults = defaults.system,
                    builtInSampleDefaults = defaults.googleSettingsSample
                )
                applyResult.toJsonImportResult(payload.invalidCount, payload.duplicateCount)
            }
        }
    }

    private suspend fun buildOsShellPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val payload =
            withContext(defaultDispatcher) { OsCardTransferService.parseShellImportPayload(file.raw) }
        val existing = withContext(ioDispatcher) { OsShellCommandCardStore.loadCards() }
        val result = withContext(defaultDispatcher) {
            OsCardTransferService.previewShellImport(payload = payload, existingCards = existing)
        }
        val preview = buildOsPreview(
            context = context,
            header = header,
            totalCount = payload.sourceCount,
            validCount = payload.cards.size,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount,
            addedCount = result.addedCount,
            updatedCount = result.updatedCount,
            unchangedCount = result.unchangedCount,
            mergedCount = result.cards.size,
            samples = payload.cards.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(it.title.ifBlank { it.command }, it.command)
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val current = OsShellCommandCardStore.loadCards()
                val applyResult = OsCardTransferService.applyShellImport(payload, current)
                applyResult.toJsonImportResult(payload.invalidCount, payload.duplicateCount)
            }
        }
    }

    private suspend fun buildOsBundlePlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val defaults = buildOsDefaults(context)
        val currentActivity = withContext(ioDispatcher) {
            OsActivityShortcutCardStore.loadCards(defaults.system, defaults.googleSettingsSample)
        }
        val currentShell = withContext(ioDispatcher) { OsShellCommandCardStore.loadCards() }
        val bundlePreview = withContext(defaultDispatcher) {
            OsCardTransferService.buildBundleImportPreview(
                raw = file.raw,
                activityShortcutCards = currentActivity,
                shellCommandCards = currentShell,
                defaults = defaults.system,
                builtInSampleDefaults = defaults.googleSettingsSample
            )
        }
        val preview = buildOsPreview(
            context = context,
            header = header,
            totalCount = bundlePreview.fileItemCount,
            validCount = bundlePreview.validCount,
            invalidCount = bundlePreview.invalidCount,
            duplicateCount = bundlePreview.duplicateCount,
            addedCount = bundlePreview.newCount,
            updatedCount = bundlePreview.updatedCount,
            unchangedCount = bundlePreview.unchangedCount,
            mergedCount = bundlePreview.mergedCount,
            samples = buildOsBundleSamples(bundlePreview.payload)
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val activityCards = OsActivityShortcutCardStore.loadCards(
                    defaults.system,
                    defaults.googleSettingsSample
                )
                val shellCards = OsShellCommandCardStore.loadCards()
                val result = OsCardTransferService.applyBundleImport(
                    payload = bundlePreview.payload,
                    activityShortcutCards = activityCards,
                    shellCommandCards = shellCards,
                    defaults = defaults.system,
                    builtInSampleDefaults = defaults.googleSettingsSample
                )
                KeiOSJsonImportApplyResult(
                    addedCount = result.addedCount,
                    updatedCount = result.updatedCount,
                    unchangedCount = result.unchangedCount,
                    invalidCount = result.invalidCount,
                    duplicateCount = result.duplicateCount
                )
            }
        }
    }

    private suspend fun buildBaCatalogPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val current = withContext(ioDispatcher) { BaGuideCatalogStore.loadFavorites() }
        val preview = withContext(defaultDispatcher) {
            val imported = parseCatalogFavoritesExport(file.raw)
            val result = previewCatalogFavoritesImport(file.raw, current)
            buildBaPreview(
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
        }
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val currentFavorites = BaGuideCatalogStore.loadFavorites()
                val imported = parseCatalogFavoritesExport(file.raw)
                val result = previewCatalogFavoritesImport(file.raw, currentFavorites)
                if (imported.isNotEmpty()) {
                    BaGuideCatalogStore.saveFavorites(currentFavorites + imported)
                }
                KeiOSJsonImportApplyResult(
                    addedCount = result.addedCount,
                    updatedCount = result.updatedCount,
                    unchangedCount = imported.size - result.addedCount - result.updatedCount
                )
            }
        }
    }

    private suspend fun buildBaBgmPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val previewResult = withContext(defaultDispatcher) {
            GuideBgmFavoriteStore.previewFavoritesJsonImport(file.raw)
        }
        val preview = buildBaPreview(
            context = context,
            header = header,
            totalCount = previewResult.importedCount,
            validCount = previewResult.importedCount,
            addedCount = previewResult.addedCount,
            updatedCount = previewResult.updatedCount,
            samples = buildBgmSamples(file.raw)
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val result = GuideBgmFavoriteStore.importFavoritesJsonMerged(file.raw)
                KeiOSJsonImportApplyResult(
                    addedCount = result.addedCount,
                    updatedCount = result.updatedCount,
                    unchangedCount = result.importedCount - result.addedCount - result.updatedCount
                )
            }
        }
    }

    private suspend fun buildBaAllPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val currentCatalog = withContext(ioDispatcher) { BaGuideCatalogStore.loadFavorites() }
        val (catalogPreview, bgmPreview) = coroutineScope {
            val catalogDeferred = async(defaultDispatcher) {
                val imported = parseCatalogFavoritesExport(file.raw)
                imported to previewCatalogFavoritesImport(file.raw, currentCatalog)
            }
            val bgmDeferred = async(defaultDispatcher) {
                GuideBgmFavoriteStore.previewFavoritesJsonImport(file.raw)
            }
            catalogDeferred.await() to bgmDeferred.await()
        }
        val importedCatalog = catalogPreview.first
        val catalogResult = catalogPreview.second
        val totalCount = importedCatalog.size + bgmPreview.importedCount
        val preview = buildBaPreview(
            context = context,
            header = header,
            totalCount = totalCount,
            validCount = totalCount,
            addedCount = catalogResult.addedCount + bgmPreview.addedCount,
            updatedCount = catalogResult.updatedCount + bgmPreview.updatedCount,
            samples = importedCatalog.entries.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(
                    it.key.toString(),
                    context.getString(R.string.json_import_ba_catalog_sample)
                )
            }.let { samples ->
                if (samples.size >= KEIOS_JSON_IMPORT_SAMPLE_LIMIT) samples
                else samples + buildBgmSamples(file.raw).take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT - samples.size)
            }
        )
        return ImportableKeiOSJsonPlan(preview) {
            withContext(ioDispatcher) {
                val currentFavorites = BaGuideCatalogStore.loadFavorites()
                val imported = parseCatalogFavoritesExport(file.raw)
                val catalogApplyPreview = previewCatalogFavoritesImport(file.raw, currentFavorites)
                if (imported.isNotEmpty()) {
                    BaGuideCatalogStore.saveFavorites(currentFavorites + imported)
                }
                val bgmResult = GuideBgmFavoriteStore.importFavoritesJsonMerged(file.raw)
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

    private fun buildReadOnlyPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val root = JSONObject(file.raw)
        val count = when (header.kind) {
            KeiOSJsonImportKind.McpLogs -> root.optInt(
                "logCount",
                root.optJSONArray("logs")?.length() ?: 0
            )

            KeiOSJsonImportKind.OsInfoCard -> root.optInt(
                "rowCount",
                root.optJSONArray("rows")?.length() ?: 0
            )

            else -> 0
        }
        return ReadOnlyKeiOSJsonPlan(
            KeiOSJsonImportPreview(
                kind = header.kind,
                marker = header.marker,
                version = header.version,
                highVersion = header.highVersion,
                readOnly = true,
                legacyFormat = header.legacyFormat,
                canImport = false,
                totalCount = count,
                validCount = count,
                stats = listOf(
                    KeiOSJsonImportStat(
                        context.getString(R.string.json_import_stat_total),
                        count.toString()
                    ),
                    KeiOSJsonImportStat(
                        context.getString(R.string.json_import_stat_mode),
                        context.getString(R.string.json_import_mode_read_only)
                    )
                ),
                samples = buildReadOnlySamples(root, header.kind)
            )
        )
    }

    private suspend fun applyGitHubImport(
        context: Context,
        payload: GitHubTrackedItemsImportPayload
    ): KeiOSJsonImportApplyResult {
        if (payload.items.isEmpty()) {
            return KeiOSJsonImportApplyResult(
                invalidCount = payload.invalidCount,
                duplicateCount = payload.duplicateCount
            )
        }
        val nowMillis = System.currentTimeMillis()
        val existing = GitHubTrackStore.load()
        val mergedItems = existing.toMutableList()
        val indexById = mergedItems.withIndex().associate { it.value.id to it.index }.toMutableMap()
        val trackedAddedAt = GitHubTrackStore.loadTrackedAddedAtById().toMutableMap()
        val (checkCache, refreshTimestamp) = GitHubTrackStore.loadCheckCache()
        val nextCheckCache = checkCache.toMutableMap()
        val changedIds = linkedSetOf<String>()
        var added = 0
        var updated = 0
        var unchanged = 0
        payload.items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            if (index % YIELD_EVERY_ITEMS == 0) yield()
            val existingIndex = indexById[item.id]
            when {
                existingIndex == null -> {
                    mergedItems += item
                    indexById[item.id] = mergedItems.lastIndex
                    trackedAddedAt[item.id] = nowMillis
                    changedIds += item.id
                    added += 1
                }

                mergedItems[existingIndex] != item -> {
                    val existingItem = mergedItems[existingIndex]
                    val mergedItem = item.withTrackedLocalAppTypeFallback(existingItem)
                    val trackingConfigChanged =
                        !existingItem.hasSameGitHubTrackingConfigIgnoringLocalAppType(mergedItem)
                    mergedItems[existingIndex] = mergedItem
                    if (trackingConfigChanged) {
                        nextCheckCache.remove(item.id)
                        changedIds += item.id
                    }
                    updated += 1
                }

                else -> unchanged += 1
            }
        }
        if (added > 0 || updated > 0) {
            GitHubTrackStore.save(mergedItems)
            GitHubTrackStore.saveTrackedAddedAtById(trackedAddedAt)
            if (nextCheckCache.size != checkCache.size) {
                GitHubTrackStore.saveCheckCache(nextCheckCache, refreshTimestamp)
            }
            changedIds.forEach { id ->
                GitHubTrackStoreSignals.requestTrackRefresh(
                    trackId = id,
                    notifyChangeSignal = false
                )
            }
            GitHubTrackStoreSignals.notifyChanged()
            AppBackgroundScheduler.scheduleGitHubRefresh(context)
        }
        return KeiOSJsonImportApplyResult(
            addedCount = added,
            updatedCount = updated,
            unchangedCount = unchanged,
            invalidCount = payload.invalidCount,
            duplicateCount = payload.duplicateCount
        )
    }

    private suspend fun buildGitHubPreview(
        context: Context,
        header: KeiOSJsonImportHeader,
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): KeiOSJsonImportPreview {
        val existingById = existingItems.associateBy { it.id }
        var newCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        payload.items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            if (index % YIELD_EVERY_ITEMS == 0) yield()
            when (existingById[item.id]) {
                null -> newCount += 1
                item -> unchangedCount += 1
                else -> updatedCount += 1
            }
        }
        val optionCounts = GitHubTrackStore.calculateTrackedItemsOptionCounts(payload.items)
        return KeiOSJsonImportPreview(
            kind = header.kind,
            marker = header.marker.ifBlank { payload.format },
            version = header.version.takeIf { it > 0 } ?: payload.schemaVersion,
            highVersion = header.highVersion,
            readOnly = false,
            legacyFormat = header.legacyFormat,
            canImport = payload.items.isNotEmpty(),
            totalCount = payload.sourceCount,
            validCount = payload.items.size,
            newCount = newCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            duplicateCount = payload.duplicateCount,
            invalidCount = payload.invalidCount,
            stats = buildStats(
                context,
                payload.sourceCount,
                payload.items.size,
                newCount,
                updatedCount,
                unchangedCount,
                payload.invalidCount,
                payload.duplicateCount
            ) +
                    listOf(
                        KeiOSJsonImportStat(
                            context.getString(R.string.json_import_stat_actions),
                            optionCounts.actionsUpdateCount.toString()
                        ),
                        KeiOSJsonImportStat(
                            context.getString(R.string.json_import_stat_precise_version),
                            optionCounts.preciseApkVersionOverrideCount.toString()
                        )
                    ),
            samples = payload.items.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(
                    title = "${it.owner}/${it.repo}".ifBlank { it.repoUrl },
                    subtitle = listOf(
                        it.appLabel,
                        it.packageName
                    ).filter { value -> value.isNotBlank() }.joinToString(" · ")
                )
            }
        )
    }

    private fun buildOsPreview(
        context: Context,
        header: KeiOSJsonImportHeader,
        totalCount: Int,
        validCount: Int,
        invalidCount: Int,
        duplicateCount: Int,
        addedCount: Int,
        updatedCount: Int,
        unchangedCount: Int,
        mergedCount: Int,
        samples: List<KeiOSJsonImportSample>
    ): KeiOSJsonImportPreview {
        return KeiOSJsonImportPreview(
            kind = header.kind,
            marker = header.marker,
            version = header.version,
            highVersion = header.highVersion,
            readOnly = false,
            legacyFormat = header.legacyFormat,
            canImport = validCount > 0,
            totalCount = totalCount,
            validCount = validCount,
            newCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            duplicateCount = duplicateCount,
            invalidCount = invalidCount,
            stats = buildStats(
                context,
                totalCount,
                validCount,
                addedCount,
                updatedCount,
                unchangedCount,
                invalidCount,
                duplicateCount
            ) +
                    KeiOSJsonImportStat(
                        context.getString(R.string.json_import_stat_merged),
                        mergedCount.toString()
                    ),
            samples = samples
        )
    }

    private fun buildBaPreview(
        context: Context,
        header: KeiOSJsonImportHeader,
        totalCount: Int,
        validCount: Int,
        addedCount: Int,
        updatedCount: Int,
        samples: List<KeiOSJsonImportSample>
    ): KeiOSJsonImportPreview {
        val unchangedCount = (validCount - addedCount - updatedCount).coerceAtLeast(0)
        return KeiOSJsonImportPreview(
            kind = header.kind,
            marker = header.marker,
            version = header.version,
            highVersion = header.highVersion,
            readOnly = false,
            legacyFormat = header.legacyFormat,
            canImport = validCount > 0,
            totalCount = totalCount,
            validCount = validCount,
            newCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            stats = buildStats(
                context,
                totalCount,
                validCount,
                addedCount,
                updatedCount,
                unchangedCount,
                0,
                0
            ),
            samples = samples
        )
    }

    private fun buildStats(
        context: Context,
        totalCount: Int,
        validCount: Int,
        addedCount: Int,
        updatedCount: Int,
        unchangedCount: Int,
        invalidCount: Int,
        duplicateCount: Int
    ): List<KeiOSJsonImportStat> {
        return listOf(
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_total),
                totalCount.toString(),
                emphasized = true
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_valid),
                validCount.toString()
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_new),
                addedCount.toString(),
                emphasized = addedCount > 0
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_updated),
                updatedCount.toString(),
                emphasized = updatedCount > 0
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_unchanged),
                unchangedCount.toString()
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_invalid),
                invalidCount.toString()
            ),
            KeiOSJsonImportStat(
                context.getString(R.string.json_import_stat_duplicate),
                duplicateCount.toString()
            )
        )
    }

    private fun buildOsBundleSamples(payload: OsCardBundleImportPayload): List<KeiOSJsonImportSample> {
        val activitySamples = payload.activityPayload?.cards.orEmpty()
            .take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT)
            .map { KeiOSJsonImportSample(it.config.title, it.config.packageName) }
        val remaining = (KEIOS_JSON_IMPORT_SAMPLE_LIMIT - activitySamples.size).coerceAtLeast(0)
        val shellSamples = payload.shellPayload?.cards.orEmpty()
            .take(remaining)
            .map { KeiOSJsonImportSample(it.title.ifBlank { it.command }, it.command) }
        return activitySamples + shellSamples
    }

    private fun buildReadOnlySamples(
        root: JSONObject,
        kind: KeiOSJsonImportKind
    ): List<KeiOSJsonImportSample> {
        val array = when (kind) {
            KeiOSJsonImportKind.McpLogs -> root.optJSONArray("logs")
            KeiOSJsonImportKind.OsInfoCard -> root.optJSONArray("rows")
            else -> null
        } ?: return emptyList()
        return buildList {
            repeat(minOf(array.length(), KEIOS_JSON_IMPORT_SAMPLE_LIMIT)) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                add(
                    when (kind) {
                        KeiOSJsonImportKind.McpLogs -> KeiOSJsonImportSample(
                            title = item.optString("message"),
                            subtitle = listOf(item.optString("time"), item.optString("level"))
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                        )

                        else -> KeiOSJsonImportSample(
                            title = item.optString("key"),
                            subtitle = item.optString("value")
                        )
                    }
                )
            }
        }
    }

    private fun buildBgmSamples(raw: String): List<KeiOSJsonImportSample> {
        return runCatching {
            val trimmed = raw.trim()
            val array = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("favorites")
                    ?: root.optJSONArray("bgmFavorites")
                    ?: org.json.JSONArray()
            }
            buildList {
                repeat(minOf(array.length(), KEIOS_JSON_IMPORT_SAMPLE_LIMIT)) { index ->
                    val item = array.optJSONObject(index) ?: return@repeat
                    add(
                        KeiOSJsonImportSample(
                            title = item.optString("title").ifBlank { item.optString("audioUrl") },
                            subtitle = item.optString("studentTitle")
                                .ifBlank { item.optString("audioUrl") }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun queryUriMetadata(context: Context, uri: Uri): UriMetadata {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                UriMetadata(
                    displayName = cursor.stringColumn(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = cursor.longColumn(OpenableColumns.SIZE),
                    mimeType = resolver.getType(uri).orEmpty()
                )
            }
        }.getOrNull() ?: UriMetadata(mimeType = resolver.getType(uri).orEmpty())
    }

    private fun Cursor.stringColumn(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0 && moveToFirst()) getString(index).orEmpty() else ""
    }

    private fun Cursor.longColumn(name: String): Long {
        val index = getColumnIndex(name)
        return if (index >= 0 && moveToFirst()) getLong(index) else -1L
    }

    private fun buildOsDefaults(context: Context): OsDefaults {
        val systemDefaults = OsGoogleSystemServiceConfig(
            title = context.getString(R.string.os_section_google_system_service_title),
            subtitle = context.getString(R.string.os_google_system_service_default_subtitle),
            appName = context.getString(R.string.os_google_system_service_default_app_name),
            intentFlags = context.getString(R.string.os_google_system_service_default_intent_flags)
        ).normalized()
        val googleSettingsSample = OsGoogleSystemServiceConfig(
            title = context.getString(R.string.os_activity_builtin_google_settings_title),
            subtitle = context.getString(R.string.os_activity_builtin_google_settings_subtitle),
            appName = context.getString(R.string.os_activity_builtin_google_settings_app_name),
            packageName = context.getString(R.string.os_activity_builtin_google_settings_package),
            className = context.getString(R.string.os_activity_builtin_google_settings_class),
            intentAction = Intent.ACTION_VIEW,
            intentFlags = context.getString(R.string.os_google_system_service_default_intent_flags)
        ).normalized(systemDefaults)
        return OsDefaults(systemDefaults, googleSettingsSample)
    }

    fun errorMessage(context: Context, error: Throwable): String {
        val reason = (error as? KeiOSJsonImportException)?.reason
        return when (reason) {
            KeiOSJsonImportFailureReason.MissingSource -> context.getString(R.string.json_import_error_missing_source)
            KeiOSJsonImportFailureReason.EmptyFile -> context.getString(R.string.json_import_error_empty_file)
            KeiOSJsonImportFailureReason.FileTooLarge -> context.getString(
                R.string.json_import_error_file_too_large,
                formatBytes(KEIOS_JSON_IMPORT_MAX_BYTES)
            )

            KeiOSJsonImportFailureReason.UnsupportedFormat -> context.getString(R.string.json_import_error_unsupported_format)
            KeiOSJsonImportFailureReason.ReadFailed -> context.getString(R.string.json_import_error_read_failed)
            KeiOSJsonImportFailureReason.ParseFailed -> context.getString(R.string.json_import_error_parse_failed)
            KeiOSJsonImportFailureReason.ApplyFailed -> context.getString(R.string.json_import_error_apply_failed)
            null -> error.message ?: error.javaClass.simpleName
        }
    }

    private fun GitHubTrackedApp.withTrackedLocalAppTypeFallback(
        existingItem: GitHubTrackedApp
    ): GitHubTrackedApp {
        return if (localAppType == GitHubTrackedLocalAppType.Unknown) {
            copy(localAppType = existingItem.localAppType)
        } else {
            this
        }
    }

    private fun os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult.toJsonImportResult(
        invalidCount: Int,
        duplicateCount: Int
    ): KeiOSJsonImportApplyResult {
        return KeiOSJsonImportApplyResult(
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount
        )
    }

    private fun os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult.toJsonImportResult(
        invalidCount: Int,
        duplicateCount: Int
    ): KeiOSJsonImportApplyResult {
        return KeiOSJsonImportApplyResult(
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount
        )
    }

    private data class UriMetadata(
        val displayName: String = "",
        val sizeBytes: Long = -1L,
        val mimeType: String = ""
    )

    private data class OsDefaults(
        val system: OsGoogleSystemServiceConfig,
        val googleSettingsSample: OsGoogleSystemServiceConfig
    )

    private companion object {
        private const val TAG = "KeiOSJsonImport"
        private const val YIELD_EVERY_ITEMS = 64
        private const val YIELD_EVERY_BYTES = 64L * 1024L
    }
}
