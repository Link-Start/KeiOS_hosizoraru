package os.kei.ui.page.main.back

import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled

internal enum class BackNavigationSource {
    Idle,
    MainPager,
    MainRoute,
    Activity,
    Fullscreen,
    Modal
}

internal data class BackNavigationRuntimeState(
    val isGestureInProgress: Boolean = false,
    val isCommitRunning: Boolean = false,
    val source: BackNavigationSource = BackNavigationSource.Idle,
    val progress: Float = 0f,
    val swipeEdge: Int = BackEventCompat.EDGE_NONE,
    val policy: PredictiveBackOemCompat.Policy? = null
) {
    val contentWorkAllowed: Boolean
        get() = !isGestureInProgress && !isCommitRunning
}

@Stable
internal class BackNavigationRuntimeController {
    var state by mutableStateOf(BackNavigationRuntimeState())
        private set

    fun updatePolicy(policy: PredictiveBackOemCompat.Policy) {
        state = state.copy(policy = policy)
    }

    fun beginGesture(source: BackNavigationSource) {
        state = state.copy(
            isGestureInProgress = true,
            source = source,
            progress = 0f,
            swipeEdge = BackEventCompat.EDGE_NONE
        )
    }

    fun updateGesture(event: BackEventCompat, source: BackNavigationSource) {
        state = state.copy(
            isGestureInProgress = true,
            source = source,
            progress = event.progress.coerceIn(0f, 1f),
            swipeEdge = event.swipeEdge
        )
    }

    fun beginCommit(source: BackNavigationSource) {
        state = state.copy(
            isGestureInProgress = false,
            isCommitRunning = true,
            source = source,
            progress = 1f
        )
    }

    fun reset() {
        val policy = state.policy
        state = BackNavigationRuntimeState(policy = policy)
    }
}

internal val LocalBackNavigationRuntimeController = compositionLocalOf {
    BackNavigationRuntimeController()
}

internal val LocalBackNavigationRuntimeState = compositionLocalOf {
    BackNavigationRuntimeState()
}

internal class BackNavigationCommitGate {
    private var committed = false

    fun tryCommit(block: () -> Unit): Boolean {
        if (committed) return false
        committed = true
        block()
        return true
    }

    fun reset() {
        committed = false
    }
}

internal data class FullscreenBackNavigationGestureState(
    val translationX: Float,
    val contentAlpha: Float,
    val scrimAlpha: Float,
    val onContainerWidthChanged: (Int) -> Unit
)

@OptIn(ExperimentalActivityApi::class)
@Composable
internal fun rememberFullscreenBackNavigationGestureState(
    enabled: Boolean = true,
    onBack: () -> Unit
): FullscreenBackNavigationGestureState {
    val latestOnBack by rememberUpdatedState(onBack)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val predictiveEnabled =
        enabled && transitionAnimationsEnabled && predictiveBackAnimationsEnabled
    val commitGate = remember { BackNavigationCommitGate() }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var predictiveBackSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_NONE) }
    var containerWidthPx by remember { mutableIntStateOf(0) }

    BackHandler(enabled = enabled && !predictiveEnabled) {
        commitGate.reset()
        runtimeController.beginCommit(BackNavigationSource.Fullscreen)
        try {
            commitGate.tryCommit(latestOnBack)
        } finally {
            runtimeController.reset()
        }
    }

    PredictiveBackHandler(enabled = predictiveEnabled) { backEvents ->
        commitGate.reset()
        runtimeController.beginGesture(BackNavigationSource.Fullscreen)
        var completedByProgress = false
        try {
            backEvents.collect { event ->
                predictiveBackProgress = event.progress.coerceIn(0f, 1f)
                predictiveBackSwipeEdge = event.swipeEdge
                runtimeController.updateGesture(event, BackNavigationSource.Fullscreen)
                if (event.progress >= 0.995f) {
                    completedByProgress = true
                    runtimeController.beginCommit(BackNavigationSource.Fullscreen)
                    commitGate.tryCommit(latestOnBack)
                }
            }
            if (!completedByProgress) {
                runtimeController.beginCommit(BackNavigationSource.Fullscreen)
                commitGate.tryCommit(latestOnBack)
            }
        } catch (_: CancellationException) {
        } finally {
            predictiveBackProgress = 0f
            predictiveBackSwipeEdge = BackEventCompat.EDGE_NONE
            runtimeController.reset()
        }
    }

    val clampedBackProgress = predictiveBackProgress.coerceIn(0f, 1f)
    val easedBackProgress =
        clampedBackProgress * clampedBackProgress * (3f - 2f * clampedBackProgress)
    val edgeDirection = when (predictiveBackSwipeEdge) {
        BackEventCompat.EDGE_LEFT -> 1f
        BackEventCompat.EDGE_RIGHT -> -1f
        else -> 0f
    }
    return FullscreenBackNavigationGestureState(
        translationX = containerWidthPx.toFloat() * FULLSCREEN_BACK_TRANSLATION_FACTOR *
                edgeDirection * easedBackProgress,
        contentAlpha = (1f - easedBackProgress * FULLSCREEN_BACK_CONTENT_FADE_FACTOR)
            .coerceIn(0f, 1f),
        scrimAlpha = (1f - easedBackProgress * FULLSCREEN_BACK_SCRIM_FADE_FACTOR)
            .coerceIn(0f, 1f),
        onContainerWidthChanged = { width -> containerWidthPx = width }
    )
}

internal enum class MainBackNavigationAction {
    None,
    NavigateHome,
    PopRoute
}

private const val FULLSCREEN_BACK_TRANSLATION_FACTOR = 0.16f
private const val FULLSCREEN_BACK_CONTENT_FADE_FACTOR = 0.34f
private const val FULLSCREEN_BACK_SCRIM_FADE_FACTOR = 0.42f

internal fun resolveMainBackNavigationAction(
    backStackSize: Int,
    targetPageIndex: Int,
    homePageIndex: Int
): MainBackNavigationAction {
    return when {
        backStackSize > 1 -> MainBackNavigationAction.PopRoute
        targetPageIndex != homePageIndex -> MainBackNavigationAction.NavigateHome
        else -> MainBackNavigationAction.None
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
internal fun KeiOSBackNavigationHandler(
    enabled: Boolean,
    source: BackNavigationSource,
    onBack: () -> Unit
) {
    val latestOnBack by rememberUpdatedState(onBack)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val predictiveEnabled =
        enabled && transitionAnimationsEnabled && predictiveBackAnimationsEnabled
    val commitGate = remember { BackNavigationCommitGate() }

    BackHandler(enabled = enabled && !predictiveEnabled) {
        commitGate.reset()
        runtimeController.beginCommit(source)
        try {
            commitGate.tryCommit(latestOnBack)
        } finally {
            runtimeController.reset()
        }
    }

    PredictiveBackHandler(enabled = predictiveEnabled) { backEvents ->
        commitGate.reset()
        runtimeController.beginGesture(source)
        try {
            backEvents.collect { event ->
                runtimeController.updateGesture(event, source)
            }
            runtimeController.beginCommit(source)
            commitGate.tryCommit(latestOnBack)
        } catch (_: CancellationException) {
        } finally {
            runtimeController.reset()
        }
    }
}

@Composable
internal fun KeiOSActivityBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    KeiOSBackNavigationHandler(
        enabled = enabled,
        source = BackNavigationSource.Activity,
        onBack = onBack
    )
}
