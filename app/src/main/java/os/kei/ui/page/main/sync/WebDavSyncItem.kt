package os.kei.ui.page.main.sync

import os.kei.R

/**
 * Each syncable data domain. [fileName] is the remote JSON file name on the WebDAV server.
 */
internal enum class WebDavSyncItem(
    val fileName: String,
    val labelRes: Int,
    val descriptionRes: Int,
) {
    GitHubTracked(
        fileName = "github_tracked.json",
        labelRes = R.string.webdav_sync_item_github_tracked,
        descriptionRes = R.string.webdav_sync_item_github_tracked_desc,
    ),
    BaCatalogFavorites(
        fileName = "ba_catalog_favorites.json",
        labelRes = R.string.webdav_sync_item_ba_catalog_fav,
        descriptionRes = R.string.webdav_sync_item_ba_catalog_fav_desc,
    ),
    BaBgmFavorites(
        fileName = "ba_bgm_favorites.json",
        labelRes = R.string.webdav_sync_item_ba_bgm_fav,
        descriptionRes = R.string.webdav_sync_item_ba_bgm_fav_desc,
    ),
    OsActivityCards(
        fileName = "os_activity_cards.json",
        labelRes = R.string.webdav_sync_item_os_activity,
        descriptionRes = R.string.webdav_sync_item_os_activity_desc,
    ),
    OsShellCards(
        fileName = "os_shell_cards.json",
        labelRes = R.string.webdav_sync_item_os_shell,
        descriptionRes = R.string.webdav_sync_item_os_shell_desc,
    ),
}
