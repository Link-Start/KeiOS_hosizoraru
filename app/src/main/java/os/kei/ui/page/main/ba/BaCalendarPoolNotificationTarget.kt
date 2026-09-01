package os.kei.ui.page.main.ba

import android.content.Intent
import os.kei.ui.navigation.KeiosRoute

internal const val EXTRA_BA_CALENDAR_POOL_SERVER_INDEX =
    "os.kei.extra.BA_CALENDAR_POOL_SERVER_INDEX"

internal enum class BaCalendarPoolNotificationDestination {
    Calendar,
    Pool,
}

internal data class BaCalendarPoolInitialServerSelection(
    val serverIndex: Int?,
    val token: Long,
)

internal fun Intent.withBaCalendarPoolServerIndex(serverIndex: Int?): Intent = apply {
    serverIndex?.let {
        putExtra(EXTRA_BA_CALENDAR_POOL_SERVER_INDEX, it.coerceIn(0, 2))
    }
}

internal fun Intent.baCalendarPoolServerIndexOrNull(): Int? {
    if (!hasExtra(EXTRA_BA_CALENDAR_POOL_SERVER_INDEX)) return null
    val raw = getIntExtra(EXTRA_BA_CALENDAR_POOL_SERVER_INDEX, -1)
    return raw.takeIf { it in 0..2 }
}

internal fun Intent?.toBaCalendarPoolInitialServerSelection(token: Long): BaCalendarPoolInitialServerSelection =
    BaCalendarPoolInitialServerSelection(
        serverIndex = this?.baCalendarPoolServerIndexOrNull(),
        token = token,
    )

/**
 * The route carries what the intent extra used to: which server to jump to, and a nonce that tells
 * the page a fresh request arrived even when the server index is unchanged.
 */
internal fun KeiosRoute.BaCalendarPool.toInitialServerSelection(): BaCalendarPoolInitialServerSelection =
    BaCalendarPoolInitialServerSelection(
        serverIndex = serverIndex?.takeIf { it in 0..2 },
        token = nonce,
    )
