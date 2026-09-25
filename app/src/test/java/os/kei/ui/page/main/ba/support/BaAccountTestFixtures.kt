package os.kei.ui.page.main.ba.support

internal fun testBaAccountRecord(
    id: String,
    serverIndex: Int,
    nickname: String = "Kei",
    displayName: String = nickname,
    friendCode: String = "ABCDEFGH",
    sortOrder: Int = 0,
    enabled: Boolean = true,
    notificationMode: BaAccountNotificationMode = BaAccountNotificationMode.FollowGlobal,
    runtime: BaAccountRuntime = BaAccountRuntime(),
    reminderRuntime: BaAccountReminderRuntime = BaAccountReminderRuntime(),
    reminderOverride: BaAccountReminderOverride? = null,
    profileUpdatedAtMs: Long = 0L,
    runtimeUpdatedAtMs: Long = 0L,
): BaAccountRecord =
    BaAccountRecord(
        profile =
            BaAccountProfile(
                id = BaAccountId(id),
                serverIndex = serverIndex,
                displayName = displayName,
                nickname = nickname,
                friendCode = friendCode,
                notificationMode = notificationMode,
                enabled = enabled,
                sortOrder = sortOrder,
            ),
        runtime = runtime,
        reminderRuntime = reminderRuntime,
        reminderOverride = reminderOverride,
        profileUpdatedAtMs = profileUpdatedAtMs,
        runtimeUpdatedAtMs = runtimeUpdatedAtMs,
    )

internal fun testBaAccountState(
    accounts: List<BaAccountRecord>,
    activeAccountId: BaAccountId? = accounts.firstOrNull()?.profile?.id,
    allAccountsFollowGlobalNotificationSettings: Boolean = true,
    globalReminderSettings: BaGlobalReminderSettings = BaGlobalReminderSettings(),
): BaAccountStoreSnapshot =
    BaAccountStoreSnapshot(
        accounts = accounts,
        activeAccountId = activeAccountId,
        allAccountsFollowGlobalNotificationSettings = allAccountsFollowGlobalNotificationSettings,
        globalReminderSettings = globalReminderSettings,
    )
