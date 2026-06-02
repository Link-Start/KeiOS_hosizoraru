package os.kei.ui.page.main.ba

import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot

internal data class BaAccountNotificationContext(
    val accountId: BaAccountId?,
    val accountDisplayName: String,
) {
    fun notificationId(kind: BaAccountNotificationKind): Int =
        accountId?.let(kind::notificationId) ?: kind.legacyId
}

internal fun BaOfficeAccountUiState.activeNotificationContext(): BaAccountNotificationContext {
    val activeAccount = accounts.firstOrNull { it.id == activeAccountId }
    return BaAccountNotificationContext(
        accountId = activeAccount?.id,
        accountDisplayName = activeAccount?.displayName.orEmpty(),
    )
}

internal fun BaAccountStoreSnapshot.activeNotificationContext(): BaAccountNotificationContext {
    val activeAccount = accounts.firstOrNull { it.profile.id == activeAccountId }
    return BaAccountNotificationContext(
        accountId = activeAccount?.profile?.id,
        accountDisplayName = activeAccount?.profile?.displayName.orEmpty(),
    )
}
