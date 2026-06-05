package os.kei.ui.page.main.sync

import os.kei.R
import os.kei.ui.page.main.widget.chrome.TabbedPageCategory
import com.composables.icons.lucide.R as LucideR

internal enum class WebDavSyncCategory(
    override val iconRes: Int,
    override val labelRes: Int,
) : TabbedPageCategory {
    Connection(LucideR.drawable.lucide_ic_server_cog, R.string.webdav_sync_section_connection),
    Data(LucideR.drawable.lucide_ic_cloud_sync, R.string.webdav_sync_section_data),
    Advanced(LucideR.drawable.lucide_ic_database_backup, R.string.webdav_sync_section_advanced),
}
