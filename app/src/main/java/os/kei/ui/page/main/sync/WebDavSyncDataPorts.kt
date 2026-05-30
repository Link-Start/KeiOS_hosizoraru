package os.kei.ui.page.main.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
    val defaults = OsGoogleSystemServiceConfig(
        intentFlags = context.getString(R.string.os_google_system_service_default_intent_flags),
    ).normalized()
    return buildWebDavSyncDataPorts(
        googleSystemServiceDefaults = defaults,
        // Built-in cards default to the single Google-settings sample; export includes whatever the
        // user actually has, and import merges by identity, so this is sufficient off the UI thread.
        builtInActivityShortcutCards = emptyList(),
        builtInShellCommandCards = emptyList(),
    )
}

private fun buildWebDavSyncDataPorts(
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    builtInShellCommandCards: List<OsShellCommandCard>,
): Map<WebDavSyncItem, WebDavSyncDataPort> =
    mapOf(
        WebDavSyncItem.GitHubTracked to WebDavSyncDataPort(
            exportJson = {
                GitHubTrackStore.buildTrackedItemsExportJson(GitHubTrackStore.load())
            },
            merge = { raw ->
                val imported = GitHubTrackStore.parseTrackedItemsImport(raw).items
                val existing = GitHubTrackStore.load()
                // Union by tracked-item id; local config wins on conflict.
                val existingIds = existing.mapTo(HashSet()) { it.id }
                val merged = existing + imported.filter { it.id !in existingIds }
                GitHubTrackStore.save(merged)
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
        ),
        WebDavSyncItem.BaBgmFavorites to WebDavSyncDataPort(
            exportJson = { GuideBgmFavoriteStore.buildFavoritesExportJson() },
            merge = { raw -> GuideBgmFavoriteStore.importFavoritesJsonMerged(raw) },
        ),
        WebDavSyncItem.OsActivityCards to WebDavSyncDataPort(
            exportJson = {
                val cards = OsActivityShortcutCardStore.loadCards(
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSystemServiceDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                )
                OsActivityShortcutCardStore.buildCardsExportJson(cards, googleSystemServiceDefaults)
            },
            merge = { raw ->
                OsActivityShortcutCardStore.importCardsFromJsonMerged(
                    raw = raw,
                    defaults = googleSystemServiceDefaults,
                    builtInSampleDefaults = googleSystemServiceDefaults,
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
            merge = { raw ->
                OsShellCommandCardStore.importCardsFromJsonMerged(raw)
            },
        ),
    )
