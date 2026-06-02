package os.kei.ui.page.main.ba.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
@JvmInline
internal value class BaAccountId(val value: String)

@Serializable
internal enum class BaAccountNotificationMode {
    @SerialName("follow_global")
    FollowGlobal,

    @SerialName("custom")
    Custom,
}

@Serializable
internal data class BaAccountProfile(
    val id: BaAccountId,
    val serverIndex: Int,
    val displayName: String,
    val nickname: String,
    val friendCode: String,
    val notificationMode: BaAccountNotificationMode = BaAccountNotificationMode.FollowGlobal,
    val remindersEnabled: Boolean = true,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Serializable
internal data class BaAccountRuntime(
    val apLimit: Int = DEFAULT_AP_LIMIT,
    val apCurrent: Double = DEFAULT_AP_CURRENT,
    val apRegenBaseMs: Long = 0L,
    val apSyncMs: Long = 0L,
    val cafeLevel: Int = DEFAULT_CAFE_LEVEL,
    val cafeStoredAp: Double = DEFAULT_CAFE_STORED_AP,
    val cafeLastHourMs: Long = 0L,
    val coffeeHeadpatMs: Long = 0L,
    val coffeeInvite1UsedMs: Long = 0L,
    val coffeeInvite2UsedMs: Long = 0L,
)

@Serializable
internal data class BaGlobalReminderSettings(
    val apNotifyEnabled: Boolean = false,
    val apNotifyThreshold: Int = DEFAULT_AP_NOTIFY_THRESHOLD,
    val cafeApNotifyEnabled: Boolean = false,
    val cafeApNotifyThreshold: Int = DEFAULT_CAFE_AP_NOTIFY_THRESHOLD,
    val arenaRefreshNotifyEnabled: Boolean = false,
    val cafeVisitNotifyEnabled: Boolean = false,
)

@Serializable
internal data class BaAccountReminderOverride(
    val accountId: BaAccountId,
    val apNotifyEnabled: Boolean = false,
    val apNotifyThreshold: Int = DEFAULT_AP_NOTIFY_THRESHOLD,
    val cafeApNotifyEnabled: Boolean = false,
    val cafeApNotifyThreshold: Int = DEFAULT_CAFE_AP_NOTIFY_THRESHOLD,
    val arenaRefreshNotifyEnabled: Boolean = false,
    val cafeVisitNotifyEnabled: Boolean = false,
)

internal data class BaAccountProfileInput(
    val serverIndex: Int,
    val displayName: String,
    val nickname: String,
    val friendCode: String,
    val notificationMode: BaAccountNotificationMode = BaAccountNotificationMode.FollowGlobal,
    val remindersEnabled: Boolean = true,
    val customReminderSettings: BaGlobalReminderSettings = BaGlobalReminderSettings(),
)

@Serializable
internal data class BaAccountReminderRuntime(
    val apLastNotifiedLevel: Int = -1,
    val cafeApLastNotifiedLevel: Int = -1,
    val arenaRefreshLastNotifiedSlotMs: Long = 0L,
    val cafeVisitLastNotifiedSlotMs: Long = 0L,
)

@Serializable
internal data class BaAccountRecord(
    val profile: BaAccountProfile,
    val runtime: BaAccountRuntime = BaAccountRuntime(),
    val reminderRuntime: BaAccountReminderRuntime = BaAccountReminderRuntime(),
    val reminderOverride: BaAccountReminderOverride? = null,
)

internal data class BaAccountStoreSnapshot(
    val accounts: List<BaAccountRecord>,
    val activeAccountId: BaAccountId?,
    val allAccountsFollowGlobalNotificationSettings: Boolean,
    val globalReminderSettings: BaGlobalReminderSettings,
)

internal fun BaAccountStoreSnapshot.enabledServerIndices(): List<Int> =
    accounts
        .asSequence()
        .filter { it.profile.enabled }
        .map { it.profile.serverIndex.coerceIn(0, 2) }
        .distinct()
        .toList()

internal data class BaAccountReminderSnapshot(
    val accountId: BaAccountId,
    val displayName: String,
    val snapshot: BaPageSnapshot,
)

internal fun sanitizeBaAccountNickname(name: String): String =
    name.trim().take(10).ifEmpty { BA_DEFAULT_NICKNAME }

internal fun normalizeBaAccountFriendCodeInput(code: String): String =
    code
        .trim()
        .uppercase(Locale.ROOT)
        .filter { it in 'A'..'Z' || it in '0'..'9' }
        .take(8)

internal fun sanitizeBaAccountFriendCode(code: String): String {
    val normalized = normalizeBaAccountFriendCodeInput(code)
    return if (normalized.length == 8) normalized else BA_DEFAULT_FRIEND_CODE
}

internal fun sanitizeBaAccountDisplayName(
    displayName: String,
    nickname: String,
): String =
    displayName.trim().take(24).ifEmpty { sanitizeBaAccountNickname(nickname) }

internal fun BaAccountRecord.normalized(defaultSortOrder: Int): BaAccountRecord? {
    val accountId = BaAccountId(profile.id.value.trim())
    if (accountId.value.isBlank()) return null
    val nickname = sanitizeBaAccountNickname(profile.nickname)
    val friendCode = sanitizeBaAccountFriendCode(profile.friendCode)
    val normalizedProfile =
        profile.copy(
            id = accountId,
            serverIndex = profile.serverIndex.coerceIn(0, 2),
            displayName = sanitizeBaAccountDisplayName(profile.displayName, nickname),
            nickname = nickname,
            friendCode = friendCode,
            sortOrder = profile.sortOrder.takeIf { it >= 0 } ?: defaultSortOrder,
        )
    return copy(
        profile = normalizedProfile,
        runtime = runtime.normalized(),
        reminderRuntime = reminderRuntime.normalized(),
        reminderOverride = reminderOverride?.normalized(accountId),
    )
}

internal fun BaAccountRuntime.normalized(): BaAccountRuntime =
    copy(
        apLimit = apLimit.coerceIn(0, BA_AP_LIMIT_MAX),
        apCurrent = normalizeAp(apCurrent),
        apRegenBaseMs = apRegenBaseMs.coerceAtLeast(0L),
        apSyncMs = apSyncMs.coerceAtLeast(0L),
        cafeLevel = cafeLevel.coerceIn(1, 10),
        cafeStoredAp = normalizeAp(cafeStoredAp),
        cafeLastHourMs = cafeLastHourMs.coerceAtLeast(0L),
        coffeeHeadpatMs = coffeeHeadpatMs.coerceAtLeast(0L),
        coffeeInvite1UsedMs = coffeeInvite1UsedMs.coerceAtLeast(0L),
        coffeeInvite2UsedMs = coffeeInvite2UsedMs.coerceAtLeast(0L),
    )

internal fun BaGlobalReminderSettings.normalized(): BaGlobalReminderSettings =
    copy(
        apNotifyThreshold = apNotifyThreshold.coerceIn(0, BA_AP_MAX),
        cafeApNotifyThreshold = cafeApNotifyThreshold.coerceIn(0, BA_AP_MAX),
    )

internal fun BaAccountReminderOverride.normalized(accountId: BaAccountId): BaAccountReminderOverride =
    copy(
        accountId = accountId,
        apNotifyThreshold = apNotifyThreshold.coerceIn(0, BA_AP_MAX),
        cafeApNotifyThreshold = cafeApNotifyThreshold.coerceIn(0, BA_AP_MAX),
    )

internal fun BaAccountReminderRuntime.normalized(): BaAccountReminderRuntime =
    copy(
        apLastNotifiedLevel = apLastNotifiedLevel.coerceIn(-1, BA_AP_MAX),
        cafeApLastNotifiedLevel = cafeApLastNotifiedLevel.coerceIn(-1, BA_AP_MAX),
        arenaRefreshLastNotifiedSlotMs = arenaRefreshLastNotifiedSlotMs.coerceAtLeast(0L),
        cafeVisitLastNotifiedSlotMs = cafeVisitLastNotifiedSlotMs.coerceAtLeast(0L),
    )

internal fun BaAccountRecord.effectiveReminderSettings(
    globalSettings: BaGlobalReminderSettings,
    allAccountsFollowGlobalNotificationSettings: Boolean,
): BaGlobalReminderSettings {
    if (!profile.enabled || !profile.remindersEnabled) return BaGlobalReminderSettings()
    val normalizedGlobal = globalSettings.normalized()
    if (allAccountsFollowGlobalNotificationSettings) return normalizedGlobal
    val override = reminderOverride?.normalized(profile.id)
    return when (profile.notificationMode) {
        BaAccountNotificationMode.FollowGlobal -> normalizedGlobal
        BaAccountNotificationMode.Custom ->
            override?.let {
                BaGlobalReminderSettings(
                    apNotifyEnabled = it.apNotifyEnabled,
                    apNotifyThreshold = it.apNotifyThreshold,
                    cafeApNotifyEnabled = it.cafeApNotifyEnabled,
                    cafeApNotifyThreshold = it.cafeApNotifyThreshold,
                    arenaRefreshNotifyEnabled = it.arenaRefreshNotifyEnabled,
                    cafeVisitNotifyEnabled = it.cafeVisitNotifyEnabled,
                )
            } ?: normalizedGlobal
    }
}

internal fun BaGlobalReminderSettings.toAccountReminderOverride(accountId: BaAccountId): BaAccountReminderOverride {
    val normalized = normalized()
    return BaAccountReminderOverride(
        accountId = accountId,
        apNotifyEnabled = normalized.apNotifyEnabled,
        apNotifyThreshold = normalized.apNotifyThreshold,
        cafeApNotifyEnabled = normalized.cafeApNotifyEnabled,
        cafeApNotifyThreshold = normalized.cafeApNotifyThreshold,
        arenaRefreshNotifyEnabled = normalized.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = normalized.cafeVisitNotifyEnabled,
    )
}
