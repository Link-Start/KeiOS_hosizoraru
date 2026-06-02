package os.kei.ui.page.main.ba.support

internal fun BaPageSnapshot.withActiveBaAccount(
    accountState: BaAccountStoreSnapshot,
): BaPageSnapshot {
    val activeAccount =
        accountState.accounts.firstOrNull { account ->
            account.profile.id == accountState.activeAccountId
        } ?: return this
    val runtime = activeAccount.runtime.normalized()
    val reminderRuntime = activeAccount.reminderRuntime.normalized()
    val reminderSettings =
        activeAccount.effectiveReminderSettings(
            globalSettings = accountState.globalReminderSettings,
            allAccountsFollowGlobalNotificationSettings =
                accountState.allAccountsFollowGlobalNotificationSettings,
        )
    return copy(
        serverIndex = activeAccount.profile.serverIndex.coerceIn(0, 2),
        cafeLevel = runtime.cafeLevel,
        cafeStoredAp = runtime.cafeStoredAp,
        cafeLastHourMs = runtime.cafeLastHourMs,
        cafeApNotifyEnabled = reminderSettings.cafeApNotifyEnabled,
        cafeApNotifyThreshold = reminderSettings.cafeApNotifyThreshold,
        cafeApLastNotifiedLevel = reminderRuntime.cafeApLastNotifiedLevel,
        idNickname = activeAccount.profile.nickname,
        idFriendCode = activeAccount.profile.friendCode,
        idIndependentByServer = false,
        apLimit = runtime.apLimit,
        apCurrent = runtime.apCurrent,
        apRegenBaseMs = runtime.apRegenBaseMs,
        apSyncMs = runtime.apSyncMs,
        apNotifyEnabled = reminderSettings.apNotifyEnabled,
        apNotifyThreshold = reminderSettings.apNotifyThreshold,
        apLastNotifiedLevel = reminderRuntime.apLastNotifiedLevel,
        arenaRefreshNotifyEnabled = reminderSettings.arenaRefreshNotifyEnabled,
        arenaRefreshLastNotifiedSlotMs = reminderRuntime.arenaRefreshLastNotifiedSlotMs,
        cafeVisitNotifyEnabled = reminderSettings.cafeVisitNotifyEnabled,
        cafeVisitLastNotifiedSlotMs = reminderRuntime.cafeVisitLastNotifiedSlotMs,
        coffeeHeadpatMs = runtime.coffeeHeadpatMs,
        coffeeInvite1UsedMs = runtime.coffeeInvite1UsedMs,
        coffeeInvite2UsedMs = runtime.coffeeInvite2UsedMs,
    )
}
