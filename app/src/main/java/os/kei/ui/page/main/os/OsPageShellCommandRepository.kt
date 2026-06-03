package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardDataSource
import os.kei.ui.page.main.os.shell.OsShellCommandCardStoreDataSource

internal class OsPageShellCommandRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
    private val shellCommandCards: OsShellCommandCardDataSource = OsShellCommandCardStoreDataSource,
) {
    suspend fun runCommand(
        shizukuApiUtils: ShizukuApiUtils,
        command: String,
        timeoutMs: Long = 300_000L,
    ): String? =
        withContext(ioDispatcher) {
            shizukuApiUtils.execCommandCancellable(
                command = command,
                timeoutMs = timeoutMs,
            )
        }

    suspend fun saveRunResult(
        cardId: String,
        output: String,
        builtInShellCommandCards: List<OsShellCommandCard>,
    ): List<OsShellCommandCard> =
        withContext(ioDispatcher) {
            shellCommandCards.updateCardRunResult(
                cardId = cardId,
                runOutput = output,
            )
            shellCommandCards.loadCards(builtInShellCommandCards)
        }
}
