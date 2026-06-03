package os.kei.ui.page.main.ba.support

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import os.kei.core.json.KeiJson

private const val BA_ACCOUNTS_TRANSFER_VERSION = 1

@Serializable
internal data class BaAccountsTransferPayload(
    val version: Int = BA_ACCOUNTS_TRANSFER_VERSION,
    val exportedAtMs: Long = 0L,
    val accounts: List<BaAccountRecord> = emptyList(),
    val activeAccountId: BaAccountId? = null,
    val allAccountsFollowGlobalNotificationSettings: Boolean = true,
    val globalReminderSettings: BaGlobalReminderSettings = BaGlobalReminderSettings(),
)

internal fun buildBaAccountsExportJson(
    snapshot: BaAccountStoreSnapshot,
    nowMs: Long = System.currentTimeMillis(),
): String =
    KeiJson.pretty.encodeToString(
        BaAccountsTransferPayload(
            version = BA_ACCOUNTS_TRANSFER_VERSION,
            exportedAtMs = nowMs.coerceAtLeast(0L),
            accounts = snapshot.accounts,
            activeAccountId = snapshot.activeAccountId,
            allAccountsFollowGlobalNotificationSettings =
                snapshot.allAccountsFollowGlobalNotificationSettings,
            globalReminderSettings = snapshot.globalReminderSettings,
        ).normalized(),
    )

internal fun parseBaAccountsExportJson(raw: String): BaAccountsTransferPayload =
    KeiJson.lenient
        .decodeFromString<BaAccountsTransferPayload>(raw)
        .normalized()

internal fun countBaAccountsExportJson(raw: String): Int =
    parseBaAccountsExportJson(raw).accounts.size

internal fun mergeBaAccountsForSync(
    local: BaAccountStoreSnapshot,
    remote: BaAccountsTransferPayload,
    nowMs: Long = System.currentTimeMillis(),
): BaAccountsTransferPayload {
    val localAccounts = local.accounts.normalizedAccounts()
    val remotePayload = remote.normalized()
    val mergedById = LinkedHashMap<String, BaAccountRecord>()
    remotePayload.accounts.forEach { account ->
        mergedById[account.profile.id.value] = account
    }
    localAccounts.forEach { account ->
        mergedById.putIfAbsent(account.profile.id.value, account)
    }
    val mergedAccounts =
        mergedById
            .values
            .mapIndexed { index, account ->
                account.copy(profile = account.profile.copy(sortOrder = index))
            }
            .normalizedAccounts()
    val activeAccountId =
        remotePayload.activeAccountId?.takeIf { candidate ->
            mergedAccounts.any { it.profile.id == candidate }
        } ?: local.activeAccountId?.takeIf { candidate ->
            mergedAccounts.any { it.profile.id == candidate }
        } ?: mergedAccounts.firstOrNull()?.profile?.id

    return BaAccountsTransferPayload(
        version = BA_ACCOUNTS_TRANSFER_VERSION,
        exportedAtMs = nowMs.coerceAtLeast(remotePayload.exportedAtMs.coerceAtLeast(0L)),
        accounts = mergedAccounts,
        activeAccountId = activeAccountId,
        allAccountsFollowGlobalNotificationSettings =
            remotePayload.allAccountsFollowGlobalNotificationSettings,
        globalReminderSettings = remotePayload.globalReminderSettings.normalized(),
    ).normalized()
}

private fun BaAccountsTransferPayload.normalized(): BaAccountsTransferPayload {
    val normalizedAccounts = accounts.normalizedAccounts()
    val resolvedActiveAccountId =
        activeAccountId?.takeIf { candidate ->
            normalizedAccounts.any { it.profile.id == candidate }
        }
    return copy(
        version = version.coerceAtLeast(BA_ACCOUNTS_TRANSFER_VERSION),
        exportedAtMs = exportedAtMs.coerceAtLeast(0L),
        accounts = normalizedAccounts,
        activeAccountId = resolvedActiveAccountId,
        globalReminderSettings = globalReminderSettings.normalized(),
    )
}
