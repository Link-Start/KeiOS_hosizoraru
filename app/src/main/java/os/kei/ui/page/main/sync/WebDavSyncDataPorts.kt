package os.kei.ui.page.main.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shell.rememberBuiltInShellCommandCards
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.rememberBuiltInActivityShortcutCards
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.page.buildCatalogFavoritesExportJson
import os.kei.ui.page.main.student.catalog.page.parseCatalogFavoritesExport

/**
 * Remembers [WebDavSyncDataPort] for each syncable data domain.
 * Must be called from a @Composable context to access string resources
 * for built-in OS card defaults.
 */
@Composable
internal fun rememberWebDavSyncDataPorts(): Map<WebDavSyncItem, WebDavSyncDataPort> {
    val defaultIntentFlags = stringResource(R.string.os_google_system_service_default_intent_flags)
    val defaults = remember(defaultIntentFlags) {
        OsGoogleSystemServiceConfig(intentFlags = defaultIntentFlags).normalized()
    }
    val builtInActivityCards = rememberBuiltInActivityShortcutCards(
        defaults = defaults,
        defaultIntentFlags = defaultIntentFlags,
    )
    val builtInShellCards = rememberBuiltInShellCommandCards()
    return remember(builtInActivityCards, builtInShellCards, defaults) {
        buildWebDavSyncDataPorts(
            googleSystemServiceDefaults = defaults,
            builtInSampleDefaults = defaults,
            builtInActivityShortcutCards = builtInActivityCards,
            builtInShellCommandCards = builtInShellCards,
        )
    }
}

private fun buildWebDavSyncDataPorts(
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    builtInSampleDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    builtInShellCommandCards: List<OsShellCommandCard>,
): Map<WebDavSyncItem, WebDavSyncDataPort> =
    mapOf(
        WebDavSyncItem.GitHubTracked to WebDavSyncDataPort(
            exportJson = {
                val items = GitHubTrackStore.load()
                GitHubTrackStore.buildTrackedItemsExportJson(items)
            },
            importJson = { raw ->
                val payload = GitHubTrackStore.parseTrackedItemsImport(raw)
                GitHubTrackStore.save(payload.items)
            },
        ),
        WebDavSyncItem.BaCatalogFavorites to WebDavSyncDataPort(
            exportJson = {
                val favorites = BaGuideCatalogStore.loadFavorites()
                buildCatalogFavoritesExportJson(favorites)
            },
            importJson = { raw ->
                val imported = parseCatalogFavoritesExport(raw)
                BaGuideCatalogStore.saveFavorites(imported)
            },
        ),
        WebDavSyncItem.BaBgmFavorites to WebDavSyncDataPort(
            exportJson = { GuideBgmFavoriteStore.buildFavoritesExportJson() },
            importJson = { raw -> GuideBgmFavoriteStore.importFavoritesJsonMerged(raw) },
        ),
        WebDavSyncItem.OsActivityCards to WebDavSyncDataPort(
            exportJson = {
                val cards = OsActivityShortcutCardStore.loadCards(
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = builtInSampleDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
                OsActivityShortcutCardStore.buildCardsExportJson(cards)
            },
            importJson = { raw ->
                OsActivityShortcutCardStore.importCardsFromJsonMerged(
                    raw = raw,
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = builtInSampleDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
            },
        ),
        WebDavSyncItem.OsShellCards to WebDavSyncDataPort(
            exportJson = {
                val cards = OsShellCommandCardStore.loadCards(
                    builtInShellCommandCards = builtInShellCommandCards,
                )
                OsShellCommandCardStore.buildCardsExportJson(cards)
            },
            importJson = { raw ->
                OsShellCommandCardStore.importCardsFromJsonMerged(raw)
            },
        ),
    )
