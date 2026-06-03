package os.kei.feature.github.domain

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GitHubRefreshRuntimePhase {
    Idle,
    Running,
    Completed,
    Cancelled,
}

enum class GitHubRefreshScope {
    AllTracked,
    DueTracked,
    VisibleTracked,
    RequestedTracked,
    MissingCache,
    SingleTracked,
    ShortcutAllTracked,
}

enum class GitHubRefreshSource {
    Page,
    BackgroundTick,
    Shortcut,
    Debug,
}

enum class GitHubRefreshBeginPolicy {
    SupersedeRunning,
    SkipWhenRunning,
}

data class GitHubRefreshRuntimeSession(
    val id: Long,
    val scope: GitHubRefreshScope,
    val source: GitHubRefreshSource,
)

data class GitHubRefreshRuntimeState(
    val sessionId: Long = 0L,
    val phase: GitHubRefreshRuntimePhase = GitHubRefreshRuntimePhase.Idle,
    val scope: GitHubRefreshScope = GitHubRefreshScope.AllTracked,
    val source: GitHubRefreshSource = GitHubRefreshSource.Page,
    val running: Boolean = false,
    val totalTrackedCount: Int = 0,
    val targetCount: Int = 0,
    val completedCount: Int = 0,
    val updatableCount: Int = 0,
    val preReleaseUpdateCount: Int = 0,
    val failedCount: Int = 0,
    val startedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val finishedAtMs: Long = 0L,
) {
    val safeTargetCount: Int
        get() = targetCount.coerceAtLeast(1)

    val safeCompletedCount: Int
        get() = completedCount.coerceIn(0, safeTargetCount)

    val progressFraction: Float
        get() = if (targetCount <= 0) 0f else safeCompletedCount.toFloat() / safeTargetCount.toFloat()
}

object GitHubRefreshRuntimeStore {
    private val sessionIds = AtomicLong(0L)
    private val beginLock = Any()
    private val _state = MutableStateFlow(GitHubRefreshRuntimeState())
    val state: StateFlow<GitHubRefreshRuntimeState> = _state.asStateFlow()

    fun begin(
        scope: GitHubRefreshScope,
        source: GitHubRefreshSource,
        totalTrackedCount: Int,
        targetCount: Int,
        policy: GitHubRefreshBeginPolicy = GitHubRefreshBeginPolicy.SupersedeRunning,
        nowMs: Long = System.currentTimeMillis(),
    ): GitHubRefreshRuntimeSession? =
        synchronized(beginLock) {
            val current = _state.value
            if (policy == GitHubRefreshBeginPolicy.SkipWhenRunning && current.running) {
                return@synchronized null
            }
            val sessionId = sessionIds.incrementAndGet()
            _state.value =
                GitHubRefreshRuntimeState(
                    sessionId = sessionId,
                    phase = GitHubRefreshRuntimePhase.Running,
                    scope = scope,
                    source = source,
                    running = true,
                    totalTrackedCount = totalTrackedCount.coerceAtLeast(0),
                    targetCount = targetCount.coerceAtLeast(0),
                    completedCount = 0,
                    startedAtMs = nowMs,
                    updatedAtMs = nowMs,
                )
            GitHubRefreshRuntimeSession(
                id = sessionId,
                scope = scope,
                source = source,
            )
        }

    fun progress(
        sessionId: Long,
        completedCount: Int,
        updatableCount: Int,
        preReleaseUpdateCount: Int,
        failedCount: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        _state.update { current ->
            if (current.sessionId != sessionId || !current.running) {
                current
            } else {
                current.copy(
                    completedCount = completedCount.coerceIn(0, current.safeTargetCount),
                    updatableCount = updatableCount.coerceAtLeast(0),
                    preReleaseUpdateCount = preReleaseUpdateCount.coerceAtLeast(0),
                    failedCount = failedCount.coerceAtLeast(0),
                    updatedAtMs = nowMs.coerceAtLeast(current.updatedAtMs),
                )
            }
        }
    }

    fun complete(
        sessionId: Long,
        completedCount: Int,
        updatableCount: Int,
        preReleaseUpdateCount: Int,
        failedCount: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        _state.update { current ->
            if (current.sessionId != sessionId) {
                current
            } else {
                current.copy(
                    phase = GitHubRefreshRuntimePhase.Completed,
                    running = false,
                    completedCount = completedCount.coerceIn(0, current.safeTargetCount),
                    updatableCount = updatableCount.coerceAtLeast(0),
                    preReleaseUpdateCount = preReleaseUpdateCount.coerceAtLeast(0),
                    failedCount = failedCount.coerceAtLeast(0),
                    updatedAtMs = nowMs.coerceAtLeast(current.updatedAtMs),
                    finishedAtMs = nowMs.coerceAtLeast(current.startedAtMs),
                )
            }
        }
    }

    fun cancel(
        sessionId: Long,
        completedCount: Int,
        updatableCount: Int,
        preReleaseUpdateCount: Int,
        failedCount: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        _state.update { current ->
            if (current.sessionId != sessionId) {
                current
            } else {
                current.copy(
                    phase = GitHubRefreshRuntimePhase.Cancelled,
                    running = false,
                    completedCount = completedCount.coerceIn(0, current.safeTargetCount),
                    updatableCount = updatableCount.coerceAtLeast(0),
                    preReleaseUpdateCount = preReleaseUpdateCount.coerceAtLeast(0),
                    failedCount = failedCount.coerceAtLeast(0),
                    updatedAtMs = nowMs.coerceAtLeast(current.updatedAtMs),
                    finishedAtMs = nowMs.coerceAtLeast(current.startedAtMs),
                )
            }
        }
    }

    fun clear(sessionId: Long? = null) {
        _state.update { current ->
            if (sessionId == null || current.sessionId == sessionId) {
                GitHubRefreshRuntimeState()
            } else {
                current
            }
        }
    }
}
