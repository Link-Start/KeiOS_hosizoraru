package os.kei.ui.page.main.host.main

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEvent.Companion.EDGE_RIGHT
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationEventState
import kotlinx.coroutines.flow.collectLatest
import os.kei.ui.page.main.back.BACK_GESTURE_CANCEL_SETTLE_DURATION_MS
import os.kei.ui.page.main.back.BACK_GESTURE_COMMIT_SETTLE_DURATION_MS
import os.kei.ui.page.main.back.resolveBackGestureVisualProgress
import os.kei.ui.page.main.back.settleBackGestureProgress

@Stable
internal class RoutePredictiveBackDepthState internal constructor() {
    private val progress = Animatable(0f)
    private var activeTopContentKey by mutableStateOf<String?>(null)
    private var activePreviousContentKey by mutableStateOf<String?>(null)
    private var swipeEdge by mutableIntStateOf(0)
    private var touchY by mutableFloatStateOf(0f)

    fun isDepthCandidate(contentKey: Any): Boolean {
        val key = contentKey.toRouteDepthContentKey()
        return key != null && key == activeTopContentKey
    }

    fun motionValues(
        contentKey: Any,
        containerHeightPx: Int,
    ): RoutePredictiveBackDepthValues {
        val role =
            when (contentKey.toRouteDepthContentKey()) {
                activeTopContentKey -> RoutePredictiveBackDepthRole.Top
                else -> RoutePredictiveBackDepthRole.Idle
            }
        return resolveRoutePredictiveBackDepthValues(
            role = role,
            progress = progress.value,
            swipeEdge = swipeEdge,
            touchY = touchY,
            containerHeightPx = containerHeightPx,
        )
    }

    suspend fun updateGesture(
        currentContentKey: String?,
        previousContentKey: String?,
        progress: Float,
        swipeEdge: Int,
        touchY: Float,
    ) {
        if (currentContentKey == null || previousContentKey == null) {
            reset()
            return
        }
        if (activeTopContentKey == null || activePreviousContentKey == null) {
            activeTopContentKey = currentContentKey
            activePreviousContentKey = previousContentKey
        }
        this.swipeEdge = swipeEdge
        this.touchY = touchY
        this.progress.snapTo(progress.coerceIn(0f, 1f))
    }

    suspend fun settle(
        committed: Boolean,
    ) {
        if (activeTopContentKey == null && activePreviousContentKey == null) return
        progress.settleBackGestureProgress(
            targetProgress = if (committed) 1f else 0f,
            maxDurationMillis =
                if (committed) {
                    BACK_GESTURE_COMMIT_SETTLE_DURATION_MS
                } else {
                    BACK_GESTURE_CANCEL_SETTLE_DURATION_MS
                },
        )
        reset()
    }

    fun isCommittedTarget(currentContentKey: String?): Boolean =
        currentContentKey != null && currentContentKey == activePreviousContentKey

    suspend fun reset() {
        progress.snapTo(0f)
        activeTopContentKey = null
        activePreviousContentKey = null
        swipeEdge = 0
        touchY = 0f
    }
}

@Composable
internal fun rememberRoutePredictiveBackDepthState(): RoutePredictiveBackDepthState =
    remember { RoutePredictiveBackDepthState() }

@Composable
internal fun BindRoutePredictiveBackDepthState(
    enabled: Boolean,
    navigationEventState: NavigationEventState<*>,
    currentContentKey: String?,
    previousContentKey: String?,
    state: RoutePredictiveBackDepthState,
) {
    val latestCurrentContentKey by rememberUpdatedState(currentContentKey)
    val latestPreviousContentKey by rememberUpdatedState(previousContentKey)
    androidx.compose.runtime.LaunchedEffect(enabled, navigationEventState, state) {
        if (!enabled) {
            state.reset()
            return@LaunchedEffect
        }
        snapshotFlow { navigationEventState.transitionState }
            .collectLatest { transitionState ->
                when (transitionState) {
                    is InProgress -> {
                        val event = transitionState.latestEvent
                        state.updateGesture(
                            currentContentKey = latestCurrentContentKey,
                            previousContentKey = latestPreviousContentKey,
                            progress = event.progress,
                            swipeEdge = event.swipeEdge,
                            touchY = event.touchY,
                        )
                    }

                    else -> {
                        state.settle(
                            committed = state.isCommittedTarget(latestCurrentContentKey),
                        )
                    }
                }
            }
    }
}

@Composable
internal fun RoutePredictiveBackDepthEntry(
    state: RoutePredictiveBackDepthState,
    contentKey: Any,
    content: @Composable () -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val depthActive = state.isDepthCandidate(contentKey)
    Box(
        modifier =
            if (depthActive) {
                Modifier.graphicsLayer {
                    val values =
                        state.motionValues(
                            contentKey = contentKey,
                            containerHeightPx = windowInfo.containerSize.height,
                        )
                    transformOrigin = TransformOrigin(values.pivotX, values.pivotY)
                    scaleX = values.scale
                    scaleY = values.scale
                    alpha = values.alpha
                }
            } else {
                Modifier
            },
    ) {
        content()
    }
}

internal enum class RoutePredictiveBackDepthRole {
    Idle,
    Top,
}

internal data class RoutePredictiveBackDepthValues(
    val scale: Float,
    val alpha: Float,
    val pivotX: Float,
    val pivotY: Float,
)

internal fun resolveRoutePredictiveBackDepthValues(
    role: RoutePredictiveBackDepthRole,
    progress: Float,
    swipeEdge: Int,
    touchY: Float,
    containerHeightPx: Int,
): RoutePredictiveBackDepthValues {
    val visualProgress = resolveBackGestureVisualProgress(progress)
    val pivotX =
        when (swipeEdge) {
            EDGE_LEFT -> ROUTE_DEPTH_PIVOT_EDGE_BIAS
            EDGE_RIGHT -> 1f - ROUTE_DEPTH_PIVOT_EDGE_BIAS
            else -> 0.5f
        }
    val pivotY =
        if (containerHeightPx > 0) {
            (touchY / containerHeightPx.toFloat()).coerceIn(
                ROUTE_DEPTH_MIN_PIVOT_Y,
                ROUTE_DEPTH_MAX_PIVOT_Y,
            )
        } else {
            0.5f
        }

    return when (role) {
        RoutePredictiveBackDepthRole.Top ->
            RoutePredictiveBackDepthValues(
                scale = 1f - (1f - ROUTE_DEPTH_TOP_MIN_SCALE) * visualProgress,
                alpha = 1f - (1f - ROUTE_DEPTH_TOP_MIN_ALPHA) * visualProgress,
                pivotX = pivotX,
                pivotY = pivotY,
            )

        RoutePredictiveBackDepthRole.Idle ->
            RoutePredictiveBackDepthValues(
                scale = 1f,
                alpha = 1f,
                pivotX = 0.5f,
                pivotY = 0.5f,
            )
    }
}

private fun Any?.toRouteDepthContentKey(): String? = this?.toString()

private const val ROUTE_DEPTH_TOP_MIN_SCALE = 0.982f
private const val ROUTE_DEPTH_TOP_MIN_ALPHA = 0.96f
private const val ROUTE_DEPTH_PIVOT_EDGE_BIAS = 0.82f
private const val ROUTE_DEPTH_MIN_PIVOT_Y = 0.12f
private const val ROUTE_DEPTH_MAX_PIVOT_Y = 0.88f
