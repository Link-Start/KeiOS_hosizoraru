package os.kei.ui.page.main.back

import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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

    fun updateGestureProgress(progress: Float, source: BackNavigationSource) {
        state = state.copy(
            isGestureInProgress = true,
            source = source,
            progress = progress.coerceIn(0f, 1f),
            swipeEdge = BackEventCompat.EDGE_NONE
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

internal enum class BackNavigationHandlerMode {
    ComposePredictive,
    CommitOnly
}

internal enum class ActivityBackHandlerMode {
    FrameworkFinish,
    CommitCallback
}

@Composable
internal fun ProvideBackNavigationRuntime(
    policy: PredictiveBackOemCompat.Policy,
    content: @Composable () -> Unit
) {
    val controller = remember { BackNavigationRuntimeController() }
    SideEffect {
        controller.updatePolicy(policy)
    }
    CompositionLocalProvider(
        LocalBackNavigationRuntimeController provides controller,
        LocalBackNavigationRuntimeState provides controller.state,
        content = content
    )
}

internal fun resolveBackNavigationHandlerMode(
    policy: PredictiveBackOemCompat.Policy?,
    transitionAnimationsEnabled: Boolean,
    predictiveBackAnimationsEnabled: Boolean
): BackNavigationHandlerMode {
    val localBackPipeline = policy?.localBackPipeline
        ?: PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive
    val policyAnimationsEnabled = policy?.frameworkAnimationsEnabled
        ?: (transitionAnimationsEnabled && predictiveBackAnimationsEnabled)
    return if (
        transitionAnimationsEnabled &&
        predictiveBackAnimationsEnabled &&
        policyAnimationsEnabled &&
        localBackPipeline == PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive
    ) {
        BackNavigationHandlerMode.ComposePredictive
    } else {
        BackNavigationHandlerMode.CommitOnly
    }
}

internal fun resolveActivityBackHandlerMode(
    policy: PredictiveBackOemCompat.Policy?,
    transitionAnimationsEnabled: Boolean,
    predictiveBackAnimationsEnabled: Boolean,
    needsInterception: Boolean
): ActivityBackHandlerMode {
    if (needsInterception) return ActivityBackHandlerMode.CommitCallback
    val policyAnimationsEnabled = policy?.frameworkAnimationsEnabled
        ?: (transitionAnimationsEnabled && predictiveBackAnimationsEnabled)
    val activityBackPipeline = policy?.activityBackPipeline
        ?: if (policyAnimationsEnabled) {
            PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish
        } else {
            PredictiveBackOemCompat.ActivityBackPipeline.CommitCallback
        }
    return if (
        transitionAnimationsEnabled &&
        policyAnimationsEnabled &&
        activityBackPipeline == PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish
    ) {
        ActivityBackHandlerMode.FrameworkFinish
    } else {
        ActivityBackHandlerMode.CommitCallback
    }
}

internal fun shouldInstallActivityBackCallback(
    policy: PredictiveBackOemCompat.Policy?,
    transitionAnimationsEnabled: Boolean,
    predictiveBackAnimationsEnabled: Boolean,
    needsInterception: Boolean
): Boolean {
    return resolveActivityBackHandlerMode(
        policy = policy,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
        needsInterception = needsInterception
    ) == ActivityBackHandlerMode.CommitCallback
}

internal data class FullscreenBackNavigationGestureState(
    val motionValues: () -> BackGestureMotionValues,
    val onContainerSizeChanged: (width: Int, height: Int) -> Unit
)

@OptIn(ExperimentalActivityApi::class)
@Composable
internal fun rememberFullscreenBackNavigationGestureState(
    enabled: Boolean = true,
    allowActivityFrameworkFinish: Boolean = false,
    onBack: () -> Unit
): FullscreenBackNavigationGestureState {
    val latestOnBack by rememberUpdatedState(onBack)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val runtimeState = LocalBackNavigationRuntimeState.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val predictiveEnabled = enabled && resolveBackNavigationHandlerMode(
        policy = runtimeState.policy,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled
    ) == BackNavigationHandlerMode.ComposePredictive
    val frameworkFinishEnabled = allowActivityFrameworkFinish &&
            enabled &&
            !predictiveEnabled &&
            resolveActivityBackHandlerMode(
                policy = runtimeState.policy,
                transitionAnimationsEnabled = transitionAnimationsEnabled,
                predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
                needsInterception = false
            ) == ActivityBackHandlerMode.FrameworkFinish
    val commitGate = remember { BackNavigationCommitGate() }
    var predictiveBackSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_NONE) }
    var predictiveBackTouchY by remember { mutableFloatStateOf(0f) }
    val predictiveBackProgress = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    val motionConfig = remember {
        BackGestureMotionConfig(
            translationFactor = FULLSCREEN_BACK_TRANSLATION_FACTOR,
            contentFadeFactor = FULLSCREEN_BACK_CONTENT_FADE_FACTOR,
            scrimFadeFactor = FULLSCREEN_BACK_SCRIM_FADE_FACTOR,
        )
    }

    if (enabled && !predictiveEnabled && !frameworkFinishEnabled) {
        BackHandler {
            commitGate.reset()
            runtimeController.beginCommit(BackNavigationSource.Fullscreen)
            try {
                commitGate.tryCommit(latestOnBack)
            } finally {
                runtimeController.reset()
            }
        }
    }

    if (predictiveEnabled) {
        PredictiveBackHandler { backEvents ->
            commitGate.reset()
            runtimeController.beginGesture(BackNavigationSource.Fullscreen)
            var committed = false
            try {
                backEvents.collect { event ->
                    predictiveBackSwipeEdge = event.swipeEdge
                    predictiveBackTouchY = event.touchY
                    predictiveBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
                    runtimeController.updateGesture(event, BackNavigationSource.Fullscreen)
                }
                runtimeController.beginCommit(BackNavigationSource.Fullscreen)
                predictiveBackProgress.settleBackGestureProgress(
                    targetProgress = 1f,
                    maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
                )
                committed = commitGate.tryCommit(latestOnBack)
            } catch (_: CancellationException) {
                predictiveBackProgress.settleBackGestureProgress(
                    targetProgress = 0f,
                    maxDurationMillis = BACK_GESTURE_CANCEL_SETTLE_DURATION_MS,
                )
            } finally {
                if (!committed) {
                    predictiveBackProgress.snapTo(0f)
                    predictiveBackSwipeEdge = BackEventCompat.EDGE_NONE
                    predictiveBackTouchY = 0f
                }
                runtimeController.reset()
            }
        }
    }

    return FullscreenBackNavigationGestureState(
        motionValues = {
            resolveBackGestureMotion(
                progress = predictiveBackProgress.value,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                swipeEdge = predictiveBackSwipeEdge,
                touchY = predictiveBackTouchY,
                config = motionConfig,
            )
        },
        onContainerSizeChanged = { width, height ->
            containerWidthPx = width
            containerHeightPx = height
        }
    )
}

internal enum class MainBackNavigationAction {
    None,
    NavigateHome,
    PopRoute
}

private const val FULLSCREEN_BACK_TRANSLATION_FACTOR = 0.13f
private const val FULLSCREEN_BACK_CONTENT_FADE_FACTOR = 0.18f
private const val FULLSCREEN_BACK_SCRIM_FADE_FACTOR = 0.38f

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
    if (!enabled) return
    val latestOnBack by rememberUpdatedState(onBack)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val runtimeState = LocalBackNavigationRuntimeState.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val predictiveEnabled = enabled && resolveBackNavigationHandlerMode(
        policy = runtimeState.policy,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled
    ) == BackNavigationHandlerMode.ComposePredictive
    val commitGate = remember { BackNavigationCommitGate() }

    if (!predictiveEnabled) {
        BackHandler {
            commitGate.reset()
            runtimeController.beginCommit(source)
            try {
                commitGate.tryCommit(latestOnBack)
            } finally {
                runtimeController.reset()
            }
        }
    }

    if (predictiveEnabled) {
        PredictiveBackHandler { backEvents ->
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
}

@Composable
internal fun KeiOSActivityRootBackHandler(
    enabled: Boolean = true,
    needsInterception: Boolean,
    onBack: () -> Unit
) {
    val latestOnBack by rememberUpdatedState(onBack)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val runtimeState = LocalBackNavigationRuntimeState.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val shouldInstallCallback = enabled && shouldInstallActivityBackCallback(
        policy = runtimeState.policy,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
        needsInterception = needsInterception
    )
    if (!shouldInstallCallback) return
    val commitGate = remember { BackNavigationCommitGate() }

    BackHandler {
        commitGate.reset()
        runtimeController.beginCommit(BackNavigationSource.Activity)
        try {
            commitGate.tryCommit(latestOnBack)
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
