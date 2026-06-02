package os.kei.ui.page.main.student.catalog

import org.junit.Test
import kotlin.test.assertEquals

class BaGuideCatalogIconCacheTest {
    @Test
    fun `icon disk cache freshness uses long lived catalog ttl`() {
        val nowMs = 60L * 24L * 60L * 60L * 1000L

        assertEquals(
            true,
            isBaGuideCatalogIconDiskCacheFresh(
                length = 16L,
                lastModifiedMs = nowMs - BA_GUIDE_CATALOG_ICON_DISK_CACHE_MAX_AGE_MS + 1L,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            false,
            isBaGuideCatalogIconDiskCacheFresh(
                length = 16L,
                lastModifiedMs = nowMs - BA_GUIDE_CATALOG_ICON_DISK_CACHE_MAX_AGE_MS,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            false,
            isBaGuideCatalogIconDiskCacheFresh(
                length = 0L,
                lastModifiedMs = nowMs,
                nowMs = nowMs,
            ),
        )
    }
}
