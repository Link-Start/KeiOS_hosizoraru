package os.kei.ui.page.main.os.shell

internal interface OsShellCommandCardDataSource {
    fun loadCards(builtInShellCommandCards: List<OsShellCommandCard> = emptyList()): List<OsShellCommandCard>

    fun updateCard(
        cardId: String,
        title: String,
        subtitle: String,
        command: String,
    ): OsShellCommandCard?

    fun setCardVisible(
        cardId: String,
        visible: Boolean,
    ): List<OsShellCommandCard>

    fun deleteCard(cardId: String): List<OsShellCommandCard>

    fun updateCardRunResult(
        cardId: String,
        runOutput: String,
        runAtMillis: Long = OsShellCommandCardSystemClock.nowMs(),
    ): OsShellCommandCard?
}

internal object OsShellCommandCardStoreDataSource : OsShellCommandCardDataSource {
    override fun loadCards(builtInShellCommandCards: List<OsShellCommandCard>): List<OsShellCommandCard> =
        OsShellCommandCardStore.loadCards(builtInShellCommandCards)

    override fun updateCard(
        cardId: String,
        title: String,
        subtitle: String,
        command: String,
    ): OsShellCommandCard? =
        OsShellCommandCardStore.updateCard(
            cardId = cardId,
            title = title,
            subtitle = subtitle,
            command = command,
        )

    override fun setCardVisible(
        cardId: String,
        visible: Boolean,
    ): List<OsShellCommandCard> =
        OsShellCommandCardStore.setCardVisible(
            cardId = cardId,
            visible = visible,
        )

    override fun deleteCard(cardId: String): List<OsShellCommandCard> = OsShellCommandCardStore.deleteCard(cardId)

    override fun updateCardRunResult(
        cardId: String,
        runOutput: String,
        runAtMillis: Long,
    ): OsShellCommandCard? =
        OsShellCommandCardStore.updateCardRunResult(
            cardId = cardId,
            runOutput = runOutput,
            runAtMillis = runAtMillis,
        )
}
