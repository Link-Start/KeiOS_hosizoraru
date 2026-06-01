package os.kei.ui.page.main.widget.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

enum class AppFloatingDockSide {
    Start,
    End
}

@Stable
class AppGripAwareDockState internal constructor() {
    var side by mutableStateOf(AppFloatingDockSide.End)
        private set

    private var smoothedGravityX by mutableFloatStateOf(0f)
    private var sensorCandidate: AppFloatingDockSide? = null
    private var sensorCandidateSinceMs = 0L
    private var confirmedSensorSide: AppFloatingDockSide? = null
    private var touchCandidate: AppFloatingDockSide? = null
    private var touchCandidateCount = 0
    private var lastTouchMs = 0L

    fun resetToDefault() {
        side = AppFloatingDockSide.End
        smoothedGravityX = 0f
        sensorCandidate = null
        sensorCandidateSinceMs = 0L
        confirmedSensorSide = null
        touchCandidate = null
        touchCandidateCount = 0
        lastTouchMs = 0L
    }

    fun recordSensorGravityX(gravityX: Float) {
        val nowMs = SystemClock.elapsedRealtime()
        smoothedGravityX = if (smoothedGravityX == 0f) {
            gravityX
        } else {
            smoothedGravityX * SENSOR_SMOOTHING_RETAIN + gravityX * SENSOR_SMOOTHING_INCOMING
        }
        val nextCandidate = when {
            smoothedGravityX <= SENSOR_START_THRESHOLD -> AppFloatingDockSide.Start
            smoothedGravityX >= SENSOR_END_THRESHOLD -> AppFloatingDockSide.End
            smoothedGravityX in SENSOR_NEUTRAL_MIN..SENSOR_NEUTRAL_MAX -> null
            else -> sensorCandidate
        }
        if (nextCandidate == null) {
            sensorCandidate = null
            sensorCandidateSinceMs = 0L
            confirmedSensorSide = null
            return
        }
        if (sensorCandidate != nextCandidate) {
            sensorCandidate = nextCandidate
            sensorCandidateSinceMs = nowMs
            return
        }
        val candidateAgeMs = nowMs - sensorCandidateSinceMs
        if (candidateAgeMs >= SENSOR_CONFIRM_DELAY_MS && side != nextCandidate) {
            side = nextCandidate
            confirmedSensorSide = nextCandidate
            resetTouchMemory()
        }
    }

    fun recordTouchSide(touchSide: AppFloatingDockSide) {
        if (confirmedSensorSide != null && confirmedSensorSide != touchSide) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastTouchMs > TOUCH_MEMORY_MS || touchCandidate != touchSide) {
            touchCandidate = touchSide
            touchCandidateCount = 1
        } else {
            touchCandidateCount += 1
        }
        lastTouchMs = nowMs
        if (confirmedSensorSide == null && touchCandidateCount >= TOUCH_CONFIRM_COUNT) {
            side = touchSide
            resetTouchMemory()
        }
    }

    private fun resetTouchMemory() {
        touchCandidate = null
        touchCandidateCount = 0
        lastTouchMs = 0L
    }

    private companion object {
        private const val SENSOR_SMOOTHING_RETAIN = 0.86f
        private const val SENSOR_SMOOTHING_INCOMING = 0.14f
        private const val SENSOR_START_THRESHOLD = -1.25f
        private const val SENSOR_END_THRESHOLD = 1.25f
        private const val SENSOR_NEUTRAL_MIN = -0.62f
        private const val SENSOR_NEUTRAL_MAX = 0.62f
        private const val SENSOR_CONFIRM_DELAY_MS = 620L
        private const val TOUCH_CONFIRM_COUNT = 3
        private const val TOUCH_MEMORY_MS = 1_800L
    }
}

@Composable
fun rememberAppGripAwareDockState(enabled: Boolean): AppGripAwareDockState {
    val context = LocalContext.current
    val state = remember { AppGripAwareDockState() }
    LaunchedEffect(enabled) {
        if (!enabled) state.resetToDefault()
    }
    DisposableEffect(context, enabled, state) {
        if (!enabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensorManager == null || sensor == null) {
                onDispose { }
            } else {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        state.recordSensorGravityX(event.values.getOrElse(0) { 0f })
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }
    return state
}

fun Modifier.appGripAwareDockTouchObserver(
    enabled: Boolean,
    onDockSideTouch: (AppFloatingDockSide) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(onDockSideTouch) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val down = event.changes.firstOrNull { change ->
                    change.pressed && !change.previousPressed
                } ?: continue
                val hotZoneTop = size.height * 0.56f
                if (down.position.y < hotZoneTop) continue
                val touchedSide = when {
                    down.position.x < size.width * 0.42f -> AppFloatingDockSide.Start
                    down.position.x > size.width * 0.58f -> AppFloatingDockSide.End
                    else -> null
                }
                touchedSide?.let(onDockSideTouch)
            }
        }
    }
}
