package os.kei.ui.page.main.sync

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.page.buildCatalogFavoritesExportJson
import os.kei.ui.page.main.student.catalog.page.parseCatalogFavoritesExport
import os.kei.ui.page.main.student.GuideBgmFavoriteStore

/**
 * Constructs [WebDavSyncDataPort] for each syncable data domain.
 * Bridges the WebDAV sync layer to the domain stores.
 *
 * OS activity/shell cards are excluded for now because their export/import
 * requires complex built-in card defaults that are not easily accessible
 * from this context. They can be added later.
 */
internal fun buildWebDavSyncDataPorts(): Map<WebDavSyncItem, WebDavSyncDataPort> =
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
    )
