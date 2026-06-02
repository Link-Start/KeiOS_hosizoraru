package os.kei.ui.page.main.ba

internal data class BaOfficeIdentityPersistenceUpdate(
    val nickname: String? = null,
    val friendCode: String? = null,
    val serverIndex: Int? = null,
) {
    suspend fun persistAsync() {
        nickname?.let { BaOfficeRepository.saveIdNicknameAsync(it, serverIndex) }
        friendCode?.let { BaOfficeRepository.saveIdFriendCodeAsync(it, serverIndex) }
    }
}

internal data class BaOfficeApLimitUpdate(
    val limit: Int,
    val runtimeUpdate: BaRuntimePersistenceUpdate?,
)

internal data class BaOfficeCooldownPersistenceUpdate(
    val headpatMs: Long? = null,
    val invite1Ms: Long? = null,
    val invite2Ms: Long? = null,
) {
    suspend fun persistAsync() {
        headpatMs?.let { BaOfficeRepository.saveCoffeeHeadpatMsAsync(it) }
        invite1Ms?.let { BaOfficeRepository.saveCoffeeInvite1UsedMsAsync(it) }
        invite2Ms?.let { BaOfficeRepository.saveCoffeeInvite2UsedMsAsync(it) }
    }
}
