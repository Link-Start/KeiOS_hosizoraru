package os.kei.core.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.snapshotFlow as composeSnapshotFlow

class AppSnapshotFlowManager {
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
    }

    fun <T> snapshotFlow(block: () -> T): Flow<T> {
        // Keep this wrapper on Compose's stable snapshotFlow path during 1.11.x migration.
        return composeSnapshotFlow(block)
    }
}

@Composable
fun rememberAppSnapshotFlowManager(): AppSnapshotFlowManager {
    val manager = remember { AppSnapshotFlowManager() }
    DisposableEffect(manager) {
        onDispose { manager.dispose() }
    }
    return manager
}
