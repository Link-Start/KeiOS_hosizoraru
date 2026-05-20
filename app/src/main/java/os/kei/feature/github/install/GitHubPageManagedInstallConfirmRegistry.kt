package os.kei.feature.github.install

import android.content.Context

internal typealias GitHubPageManagedInstallConfirmHandler = suspend (Context) -> Boolean

internal object GitHubPageManagedInstallConfirmRegistry {
    private val lock = Any()
    private var token: Long = 0L
    private var handler: GitHubPageManagedInstallConfirmHandler? = null

    fun register(handler: GitHubPageManagedInstallConfirmHandler): Long =
        synchronized(lock) {
            token += 1L
            this.handler = handler
            token
        }

    fun clear(registeredToken: Long) {
        synchronized(lock) {
            if (token == registeredToken) {
                handler = null
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            handler = null
        }
    }

    suspend fun confirm(context: Context): Boolean {
        val currentHandler =
            synchronized(lock) {
                val current = handler ?: return false
                handler = null
                current
            }
        return currentHandler(context.applicationContext)
    }
}
