package os.kei.core.background

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger

object BackgroundAsyncReceiverRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(
        receiver: BroadcastReceiver,
        context: Context,
        tag: String,
        block: suspend (Context) -> Unit
    ) {
        val pendingResult = receiver.goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                block(appContext)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(tag, "async receiver failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
