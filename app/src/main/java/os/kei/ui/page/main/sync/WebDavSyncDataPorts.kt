package os.kei.ui.page.main.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import os.kei.BuildConfig
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.domain.GitHubTrackedItemsTransferService
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.defaultKeiOsTrackedApp
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shell.buildBuiltInShellCommandCards
import os.kei.ui.page.main.os.shell.rememberBuiltInShellCommandCards
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.buildBuiltInActivityShortcutCards
import os.kei.ui.page.main.os.shortcut.rememberBuiltInActivityShortcutCards
import os.kei.ui.page.main.os.transfer.OsCardTransferService
import os.kei.ui.page.main.os.transfer.parseOsCardImportRoot
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.page.buildCatalogFavoritesExportJson
import os.kei.ui.page.main.student.catalog.page.parseCatalogFavoritesExport

/**
 * Builds the [WebDavSyncDataPort] for every [WebDavSyncItem].
 *
 * Two entry points share the same port-building logic:
 *  - [rememberWebDavSyncDataPorts] for the Compose UI (resolves string resources + built-in cards
 *    via [androidx.compose.runtime.Composable] helpers).
 *  - [buildWebDavSyncDataPorts] (Context overload) for background callers such as the auto-sync
 *    coordinator, which cannot enter composition.
 *
 * Every port's `merge` performs a union merge so two-way sync can never drop local-only data.
 */
@Composable
internal fun rememberWebDavSyncDataPorts(): Map<WebDavSyncItem, WebDavSyncDataPort> {
    val context = LocalContext.current
    val defaultIntentFlags = stringResource(R.string.os_google_system_service_default_intent_flags)
    val defaults = remember(defaultIntentFlags) {
        OsGoogleSystemServiceConfig(intentFlags = defaultIntentFlags).normalized()
    }
    val builtInActivityCards = rememberBuiltInActivityShortcutCards(
        defaults = defaults,
        defaultIntentFlags = defaultIntentFlags,
    )
    val builtInShellCards = rememberBuiltInShellCommandCards()
    return remember(context, builtInActivityCards, builtInShellCards, defaults) {
        buildWebDavSyncDataPorts(
            context = context,
            googleSystemServiceDefaults = defaults,
            builtInActivityShortcutCards = builtInActivityCards,
            builtInShellCommandCards = builtInShellCards,
        )
    }
}

/**
 * Non-Compose factory: resolve built-in defaults from string resources via [context] so auto-sync
 * can build ports from application scope.
 */
internal fun buildWebDavSyncDataPorts(context: Context): Map<WebDavSyncItem, WebDavSyncDataPort> {
    val builtIns = resolveWebDavBuiltInCardSets(context)
    return buildWebDavSyncDataPorts(
        context = context,
        googleSystemServiceDefaults = builtIns.googleSystemServiceDefaults,
        builtInActivityShortcutCards = builtIns.activityShortcutCards,
        builtInShellCommandCards = builtIns.shellCommandCards,
    )
}

private fun buildWebDavSyncDataPorts(
    context: Context,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    builtInShellCommandCards: List<OsShellCommandCard>,
): Map<WebDavSyncItem, WebDavSyncDataPort> =
    mapOf(
        WebDavSyncItem.GitHubTracked to WebDavSyncDataPort(
            exportJson = {
                val items =
                    normalizeGitHubTrackedItemsForSync(
                        GitHubTrackedItemsTransferService.loadItems(),
                    )
                GitHubTrackedItemsTransferService.buildExportJson(items)
            },
            merge = { raw ->
                val payload = GitHubTrackedItemsTransferService.parseImport(raw)
                GitHubTrackedItemsTransferService.applyImport(
                    payload = payload,
                    onRefreshNeeded = { AppBackgroundScheduler.scheduleGitHubRefresh(context) },
                    existingItems = GitHubTrackedItemsTransferService.loadItems(),
                )
            },
            localCount = {
                normalizeGitHubTrackedItemsForSync(
                    GitHubTrackedItemsTransferService.loadItems(),
                ).size
            },
            countRemoteItems = { raw ->
                runCatching {
                    normalizeGitHubTrackedItemsForSync(
                        GitHubTrackedItemsTransferService.parseImport(raw).items,
                    ).size
                }
                    .getOrDefault(0)
            },
        ),
        WebDavSyncItem.BaCatalogFavorites to WebDavSyncDataPort(
            exportJson = {
                buildCatalogFavoritesExportJson(BaGuideCatalogStore.loadFavorites())
            },
            merge = { raw ->
                val imported = parseCatalogFavoritesExport(raw)
                val existing = BaGuideCatalogStore.loadFavorites()
                // Union of favorited content; keep the earliest favorited timestamp on conflict.
                val merged = HashMap(existing)
                imported.forEach { (contentId, favoritedAtMs) ->
                    val current = merged[contentId]
                    merged[contentId] = when {
                        current == null -> favoritedAtMs
                        favoritedAtMs <= 0L -> current
                        else -> minOf(current, favoritedAtMs)
                    }
                }
                BaGuideCatalogStore.saveFavorites(merged)
            },
            localCount = { BaGuideCatalogStore.loadFavorites().size },
            countRemoteItems = { raw ->
                runCatching { parseCatalogFavoritesExport(raw).size }.getOrDefault(0)
            },
        ),
        WebDavSyncItem.BaBgmFavorites to WebDavSyncDataPort(
            exportJson = { GuideBgmFavoriteStore.buildFavoritesExportJson() },
            merge = { raw -> GuideBgmFavoriteStore.importFavoritesJsonMerged(raw) },
            localCount = { GuideBgmFavoriteStore.favoritesSnapshot().size },
            countRemoteItems = { raw ->
                runCatching { GuideBgmFavoriteStore.previewFavoritesJsonImport(raw).importedCount }
                    .getOrDefault(0)
            },
        ),
        WebDavSyncItem.OsActivityCards to WebDavSyncDataPort(
            exportJson = {
                val cards = OsActivityShortcutCardStore.loadCards(
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSystemServiceDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
                OsCardTransferService.buildActivityCardsExportJson(
                    cards = cards,
                    defaults = googleSystemServiceDefaults,
                )
            },
            merge = { raw ->
                val payload =
                    OsCardTransferService.parseActivityImportPayload(
                        raw = raw,
                        defaults = googleSystemServiceDefaults,
                        builtInSampleDefaults = googleSystemServiceDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    )
                OsCardTransferService.applyActivityImport(
                    payload = payload,
                    existingCards =
                        OsActivityShortcutCardStore.loadCards(
                            defaults = googleSystemServiceDefaults,
                            builtInSampleDefaults = googleSystemServiceDefaults,
                            builtInActivityShortcutCards = builtInActivityShortcutCards,
                        ),
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSystemServiceDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
            },
            localCount = {
                OsActivityShortcutCardStore.loadCards(
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSystemServiceDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                ).size
            },
            countRemoteItems = { raw ->
                runCatching {
                    OsActivityShortcutCardStore.parseCardsImport(
                        root = parseOsCardImportRoot(raw),
                        defaults = googleSystemServiceDefaults,
                        builtInSampleDefaults = googleSystemServiceDefaults,
                        builtInActivityShortcutCards = builtInActivityShortcutCards,
                    ).cards.size
                }.getOrDefault(0)
            },
        ),
        WebDavSyncItem.OsShellCards to WebDavSyncDataPort(
            exportJson = {
                val cards = OsShellCommandCardStore.loadCards(
                    builtInShellCommandCards = builtInShellCommandCards,
                )
                OsCardTransferService.buildShellCardsExportJson(cards)
            },
            merge = { raw ->
                val payload = OsCardTransferService.parseShellImportPayload(raw)
                OsCardTransferService.applyShellImport(
                    payload = payload,
                    existingCards =
                        OsShellCommandCardStore.loadCards(
                            builtInShellCommandCards = builtInShellCommandCards,
                        ),
                )
            },
            localCount = {
                OsShellCommandCardStore.loadCards(
                    builtInShellCommandCards = builtInShellCommandCards,
                ).size
            },
            countRemoteItems = { raw ->
                runCatching {
                    OsShellCommandCardStore.parseCardsImport(
                        root = parseOsCardImportRoot(raw),
                    ).cards.size
                }.getOrDefault(0)
            },
        ),
    )

internal fun resolveWebDavBuiltInCardSets(context: Context): WebDavBuiltInCardSets {
    val defaultIntentFlags =
        context.getString(R.string.os_google_system_service_default_intent_flags)
    val defaults = OsGoogleSystemServiceConfig(intentFlags = defaultIntentFlags).normalized()
    return WebDavBuiltInCardSets(
        googleSystemServiceDefaults = defaults,
        activityShortcutCards =
            buildBuiltInActivityShortcutCards(
                context = context,
                defaults = defaults,
                defaultIntentFlags = defaultIntentFlags,
            ),
        shellCommandCards = buildBuiltInShellCommandCards(context),
    )
}

internal data class WebDavBuiltInCardSets(
    val googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    val activityShortcutCards: List<OsActivityShortcutCard>,
    val shellCommandCards: List<OsShellCommandCard>,
)

internal fun normalizeGitHubTrackedItemsForSync(items: List<GitHubTrackedApp>): List<GitHubTrackedApp> {
    val deduplicated = LinkedHashMap<String, GitHubTrackedApp>()
    items.forEach { item ->
        deduplicated[item.id] = item
    }
    val selfTrack = defaultKeiOsTrackedApp(packageName = BuildConfig.APPLICATION_ID)
    if (deduplicated.containsKey(selfTrack.id)) {
        return deduplicated.values.toList()
    }
    return listOf(selfTrack) + deduplicated.values
}

internal fun mergeGitHubTrackedItemsForSync(
    existingItems: List<GitHubTrackedApp>,
    payload: GitHubTrackedItemsImportPayload,
): List<GitHubTrackedApp> = mergeGitHubTrackedItemsForSync(existingItems, payload.items)

internal fun mergeGitHubTrackedItemsForSync(
    existingItems: List<GitHubTrackedApp>,
    importedItems: List<GitHubTrackedApp>,
): List<GitHubTrackedApp> {
    if (importedItems.isEmpty()) {
        return normalizeGitHubTrackedItemsForSync(existingItems)
    }
    val mergedItems = existingItems.toMutableList()
    val indexById =
        mergedItems
            .withIndex()
            .associate { it.value.id to it.index }
            .toMutableMap()
    importedItems.forEach { item ->
        val existingIndex = indexById[item.id]
        if (existingIndex == null) {
            mergedItems += item
            indexById[item.id] = mergedItems.lastIndex
        } else {
            val existingItem = mergedItems[existingIndex]
            mergedItems[existingIndex] = item.withTrackedLocalAppTypeFallback(existingItem)
        }
    }
    return normalizeGitHubTrackedItemsForSync(mergedItems)
}

private fun GitHubTrackedApp.withTrackedLocalAppTypeFallback(existingItem: GitHubTrackedApp): GitHubTrackedApp =
    if (localAppType == GitHubTrackedLocalAppType.Unknown) {
        copy(localAppType = existingItem.localAppType)
    } else {
        this
    }
