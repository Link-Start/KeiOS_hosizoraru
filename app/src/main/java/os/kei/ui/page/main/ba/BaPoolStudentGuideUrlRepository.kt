package os.kei.ui.page.main.ba

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.BaPoolStudentGuideUrlResolver
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.fetchBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.loadCachedBaGuideCatalogBundle

internal class BaPoolStudentGuideUrlRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var cachedCatalogResolver: BaPoolStudentGuideUrlResolver? = null
    private var networkCatalogResolver: BaPoolStudentGuideUrlResolver? = null

    suspend fun resolve(
        serverIndex: Int,
        entries: List<BaPoolEntry>,
        allowCatalogNetwork: Boolean,
    ): List<BaPoolEntry> {
        if (entries.isEmpty()) return entries
        val directlyResolved = entries.map { BaPoolStudentGuideUrlResolver.Empty.resolve(it) }
        if (serverIndex != 0 || directlyResolved.all { it.studentGuideUrl.isNotBlank() }) {
            return directlyResolved
        }

        val cachedResolved = resolveWithCachedCatalog(directlyResolved)
        if (!allowCatalogNetwork || cachedResolved.all { it.studentGuideUrl.isNotBlank() }) {
            return cachedResolved
        }

        return resolveWithNetworkCatalog(cachedResolved)
    }

    private suspend fun resolveWithCachedCatalog(entries: List<BaPoolEntry>): List<BaPoolEntry> {
        val resolver = mutex.withLock {
            cachedCatalogResolver
        } ?: loadCachedResolver().also { loaded ->
            mutex.withLock {
                cachedCatalogResolver = loaded
            }
        }
        return entries.map(resolver::resolve)
    }

    private suspend fun resolveWithNetworkCatalog(entries: List<BaPoolEntry>): List<BaPoolEntry> {
        val resolver = mutex.withLock {
            networkCatalogResolver
        } ?: loadNetworkResolver().also { loaded ->
            mutex.withLock {
                networkCatalogResolver = loaded
                cachedCatalogResolver = loaded
            }
        }
        return entries.map(resolver::resolve)
    }

    private suspend fun loadCachedResolver(): BaPoolStudentGuideUrlResolver {
        return withContext(ioDispatcher) {
            val entries = loadCachedBaGuideCatalogBundle()
                ?.entries(BaGuideCatalogTab.Student)
                .orEmpty()
            BaPoolStudentGuideUrlResolver.fromCatalogEntries(entries)
        }
    }

    private suspend fun loadNetworkResolver(): BaPoolStudentGuideUrlResolver {
        return withContext(ioDispatcher) {
            val entries = runCatching {
                fetchBaGuideCatalogBundle(forceRefresh = false)
                    .entries(BaGuideCatalogTab.Student)
            }.getOrDefault(emptyList())
            BaPoolStudentGuideUrlResolver.fromCatalogEntries(entries)
        }
    }
}
