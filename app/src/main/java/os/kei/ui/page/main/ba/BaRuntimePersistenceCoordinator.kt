package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BaAccountId
import kotlin.time.Duration.Companion.milliseconds

internal data class BaRuntimePersistenceUpdate(
    val accountId: BaAccountId? = null,
    val apCurrent: Double? = null,
    val apRegenBaseMs: Long? = null,
    val apSyncMs: Long? = null,
    val cafeStoredAp: Double? = null,
    val cafeLastHourMs: Long? = null,
    val cafeApLastNotifiedLevel: Int? = null,
    val apLastNotifiedLevel: Int? = null,
    val notifyHomeOverview: Boolean = false,
) {
    fun withAccountId(accountId: BaAccountId?): BaRuntimePersistenceUpdate =
        if (this.accountId != null || accountId == null) {
            this
        } else {
            copy(accountId = accountId)
        }

    fun mergedWith(newer: BaRuntimePersistenceUpdate): BaRuntimePersistenceUpdate =
        BaRuntimePersistenceUpdate(
            accountId = newer.accountId ?: accountId,
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
                accountId = accountId,
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                apSyncMs = apSyncMs,
                cafeStoredAp = cafeStoredAp,
                cafeLastHourMs = cafeLastHourMs,
                notifyHomeOverview = notifyHomeOverview,
            )
        }
        cafeApLastNotifiedLevel?.let { level ->
            if (accountId == null) {
                BaOfficeRepository.saveCafeApLastNotifiedLevel(level)
            } else {
                BaOfficeRepository.saveAccountCafeApLastNotifiedLevel(accountId, level)
            }
        }
        apLastNotifiedLevel?.let { level ->
            if (accountId == null) {
                BaOfficeRepository.saveApLastNotifiedLevel(level)
            } else {
                BaOfficeRepository.saveAccountApLastNotifiedLevel(accountId, level)
            }
        }
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
    private val accountIdProvider: () -> BaAccountId? = { null },
) {
    private val updates = Channel<BaRuntimePersistenceUpdate>(capacity = Channel.BUFFERED)

    fun submit(update: BaRuntimePersistenceUpdate?) {
        if (update == null) return
        updates.trySend(update.withAccountId(accountIdProvider()))
    }

    suspend fun run() {
        for (first in updates) {
            var merged = first
            if (debounceMs > 0L) {
                delay(debounceMs.milliseconds)
            }
            while (true) {
                val next = updates.tryReceive().getOrNull() ?: break
                if (next.accountId == merged.accountId) {
                    merged = merged.mergedWith(next)
                } else {
                    withContext(ioDispatcher) {
                        merged.persist()
                    }
                    merged = next
                }
            }
            withContext(ioDispatcher) {
                merged.persist()
            }
        }
    }
}

@Composable
internal fun rememberBaRuntimePersistenceCoordinator(accountIdProvider: () -> BaAccountId? = { null }): BaRuntimePersistenceCoordinator {
    val currentAccountIdProvider = rememberUpdatedState(accountIdProvider)
    return remember {
        BaRuntimePersistenceCoordinator(
            accountIdProvider = { currentAccountIdProvider.value() },
        )
    }
}
