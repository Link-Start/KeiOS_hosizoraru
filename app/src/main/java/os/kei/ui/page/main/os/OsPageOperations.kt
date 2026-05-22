package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ensureEditorActivityShortcutDraft
import os.kei.ui.page.main.os.shortcut.launchGoogleSystemServiceActivity
import os.kei.ui.page.main.os.shortcut.normalizeActivityShortcutConfig

internal suspend fun ensureOsSectionLoaded(
    section: SectionKind,
    forceRefresh: Boolean,
    visibleCardsProvider: () -> Set<OsSectionCard>,
    sectionStatesProvider: () -> Map<SectionKind, SectionState>,
    sectionLoadMutex: Mutex,
    sectionLoadDeferreds: MutableMap<SectionKind, Deferred<List<InfoRow>>>,
    scope: CoroutineScope,
    context: Context,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    updateSection: (SectionKind, (SectionState) -> SectionState) -> Unit,
    onCachePersistedChanged: (Boolean) -> Unit,
) {
    if (!visibleSectionKinds(visibleCardsProvider()).contains(section)) return
    val current = sectionStatesProvider()[section] ?: SectionState()
    if (!forceRefresh) {
        if (current.loadedFresh) return
        if (current.rows.isNotEmpty()) return
    }
    var isLoadOwner = false
    lateinit var loadDeferred: Deferred<List<InfoRow>>
    sectionLoadMutex.withLock {
        val inFlight = sectionLoadDeferreds[section]
        if (inFlight != null && inFlight.isActive && !forceRefresh) {
            loadDeferred = inFlight
        } else {
            if (forceRefresh) {
                inFlight?.cancel()
            }
            updateSection(section) { it.copy(loading = true, loadFailed = false) }
            loadDeferred =
                scope.async {
                    buildSectionRowsAsync(
                        section = section,
                        context = context,
                        shizukuStatus = shizukuStatus,
                        shizukuApiUtils = shizukuApiUtils,
                        forceRefresh = forceRefresh,
                    )
                }
            sectionLoadDeferreds[section] = loadDeferred
            isLoadOwner = true
        }
    }
    if (!isLoadOwner) {
        runCatching { loadDeferred.await() }
        return
    }
    try {
        val fresh = loadDeferred.await()
        updateSection(section) {
            it.copy(rows = fresh, loading = false, loadedFresh = true, loadFailed = false)
        }
        val hasPersistedCache =
            withContext(AppDispatchers.osOperations) {
                OsInfoCache.write(section, fresh)
                OsInfoCache.readSnapshot(visibleSectionKinds(visibleCardsProvider())).hasPersistedCache
            }
        onCachePersistedChanged(hasPersistedCache)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        updateSection(section) { it.copy(loading = false, loadFailed = true) }
    } finally {
        sectionLoadMutex.withLock {
            if (sectionLoadDeferreds[section] === loadDeferred) {
                sectionLoadDeferreds.remove(section)
            }
        }
    }
}

internal suspend fun runOsShellCommandCard(
    card: OsShellCommandCard,
    context: Context,
    shizukuApiUtils: ShizukuApiUtils,
    shellCardCommandRequiredToast: String,
    shellCardRunCompletedToast: String,
    shellRunNoPermissionToast: String,
    shellRunNoOutputText: String,
    runningCardIdsProvider: () -> Set<String>,
    updateRunningCardIds: (Set<String>) -> Unit,
    onCardsReload: (List<OsShellCommandCard>) -> Unit,
    runFailedMessage: (Throwable) -> String,
) {
    val command = card.command.trim()
    if (command.isBlank()) {
        context.showToast(shellCardCommandRequiredToast)
        return
    }
    if (runningCardIdsProvider().contains(card.id)) return
    if (!shizukuApiUtils.canUseCommand()) {
        shizukuApiUtils.requestPermissionIfNeeded()
        context.showToast(shellRunNoPermissionToast)
        return
    }
    updateRunningCardIds(runningCardIdsProvider() + card.id)
    try {
        val output =
            withContext(AppDispatchers.osOperations) {
                shizukuApiUtils.execCommandCancellable(
                    command = command,
                    timeoutMs = 300_000L,
                )
            }.orEmpty().trim().ifBlank { shellRunNoOutputText }
        val updatedCards = withContext(AppDispatchers.osOperations) {
            OsShellCommandCardStore.updateCardRunResult(
                cardId = card.id,
                runOutput = output,
            )
            OsShellCommandCardStore.loadCards()
        }
        onCardsReload(updatedCards)
        context.showLiquidToastOnly(shellCardRunCompletedToast)
    } catch (throwable: CancellationException) {
        throw throwable
    } catch (throwable: Throwable) {
        context.showToast(runFailedMessage(throwable))
    } finally {
        updateRunningCardIds(runningCardIdsProvider() - card.id)
    }
}

internal suspend fun refreshAllOsSections(
    context: Context,
    visibleCardsProvider: () -> Set<OsSectionCard>,
    setRefreshing: (Boolean) -> Unit,
    setRefreshProgress: (Float) -> Unit,
    ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    noRefreshableCardText: String,
    refreshCompletedText: String,
) {
    setRefreshing(true)
    setRefreshProgress(0f)
    try {
        val targets = SectionKind.entries.filter { visibleSectionKinds(visibleCardsProvider()).contains(it) }
        val sectionCount = targets.size.coerceAtLeast(1)
        targets.forEachIndexed { index, section ->
            ensureLoad(section, true)
            setRefreshProgress((index + 1).toFloat() / sectionCount.toFloat())
        }
        context.showToast(
            if (targets.isEmpty()) noRefreshableCardText else refreshCompletedText,
        )
    } finally {
        setRefreshing(false)
    }
}

internal fun openOsActivityShortcutCard(
    context: Context,
    card: OsActivityShortcutCard,
    defaults: OsGoogleSystemServiceConfig,
    invalidTargetMessage: String,
    openFailedMessage: (Throwable) -> String,
) {
    val normalized =
        normalizeActivityShortcutConfig(
            config = card.config,
            defaults = defaults,
        )
    if (normalized.packageName.isBlank()) {
        context.showToast(invalidTargetMessage)
        return
    }
    runCatching {
        launchGoogleSystemServiceActivity(
            context = context,
            config = normalized,
            defaults = defaults,
        )
    }.onFailure { error ->
        context.showToast(openFailedMessage(error))
    }
}

internal fun beginEditingOsActivityShortcutCard(
    card: OsActivityShortcutCard,
    defaults: OsGoogleSystemServiceConfig,
    onEditModeChange: (OsActivityCardEditMode) -> Unit,
    onEditingCardIdChange: (String?) -> Unit,
    onEditingBuiltInChange: (Boolean) -> Unit,
    onDraftChange: (OsGoogleSystemServiceConfig) -> Unit,
    onShowEditorChange: (Boolean) -> Unit,
) {
    onEditModeChange(OsActivityCardEditMode.Edit)
    onEditingCardIdChange(card.id)
    onEditingBuiltInChange(card.isBuiltInSample)
    onDraftChange(
        ensureEditorActivityShortcutDraft(
            normalizeActivityShortcutConfig(
                config = card.config,
                defaults = defaults,
            ),
        ),
    )
    onShowEditorChange(true)
}
