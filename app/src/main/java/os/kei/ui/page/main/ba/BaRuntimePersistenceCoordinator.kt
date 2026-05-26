package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import kotlin.time.Duration.Companion.milliseconds

internal data class BaRuntimePersistenceUpdate(
    val apCurrent: Double? = null,
    val apRegenBaseMs: Long? = null,
    val apSyncMs: Long? = null,
    val cafeStoredAp: Double? = null,
    val cafeLastHourMs: Long? = null,
    val cafeApLastNotifiedLevel: Int? = null,
    val apLastNotifiedLevel: Int? = null,
    val notifyHomeOverview: Boolean = false,
) {
    fun mergedWith(newer: BaRuntimePersistenceUpdate): BaRuntimePersistenceUpdate =
        BaRuntimePersistenceUpdate(
            apCurrent = newer.apCurrent ?: apCurrent,
            apRegenBaseMs = newer.apRegenBaseMs ?: apRegenBaseMs,
            apSyncMs = newer.apSyncMs ?: apSyncMs,
            cafeStoredAp = newer.cafeStoredAp ?: cafeStoredAp,
            cafeLastHourMs = newer.cafeLastHourMs ?: cafeLastHourMs,
            cafeApLastNotifiedLevel = newer.cafeApLastNotifiedLevel ?: cafeApLastNotifiedLevel,
            apLastNotifiedLevel = newer.apLastNotifiedLevel ?: apLastNotifiedLevel,
            notifyHomeOverview = notifyHomeOverview || newer.notifyHomeOverview,
        )

    fun persist() {
        if (
            apCurrent != null ||
            apRegenBaseMs != null ||
            apSyncMs != null ||
            cafeStoredAp != null ||
            cafeLastHourMs != null
        ) {
            BaOfficeRepository.saveRuntimeState(
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                apSyncMs = apSyncMs,
                cafeStoredAp = cafeStoredAp,
                cafeLastHourMs = cafeLastHourMs,
                notifyHomeOverview = notifyHomeOverview,
            )
        }
        cafeApLastNotifiedLevel?.let(BaOfficeRepository::saveCafeApLastNotifiedLevel)
        apLastNotifiedLevel?.let(BaOfficeRepository::saveApLastNotifiedLevel)
    }

    suspend fun persistAsync(ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch) {
        withContext(ioDispatcher) {
            persist()
        }
    }
}

internal class BaRuntimePersistenceCoordinator(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val debounceMs: Long = 250L,
) {
    private val updates = Channel<BaRuntimePersistenceUpdate>(capacity = Channel.BUFFERED)

    fun submit(update: BaRuntimePersistenceUpdate?) {
        if (update == null) return
        updates.trySend(update)
    }

    suspend fun run() {
        for (first in updates) {
            var merged = first
            if (debounceMs > 0L) {
                delay(debounceMs.milliseconds)
            }
            while (true) {
                val next = updates.tryReceive().getOrNull() ?: break
                merged = merged.mergedWith(next)
            }
            withContext(ioDispatcher) {
                merged.persist()
            }
        }
    }
}

@Composable
internal fun rememberBaRuntimePersistenceCoordinator(): BaRuntimePersistenceCoordinator = remember { BaRuntimePersistenceCoordinator() }
