package os.kei

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import os.kei.core.prefs.AppThemeMode
import os.kei.ui.page.main.host.main.MainStartupSnapshot

private const val SPLASH_EXIT_DURATION_MS = 220L
private const val MAX_FIRST_DRAW_WAIT_MS = 2_000L
private const val MAX_PAGE_PREPARATION_WAIT_MS = 200L

internal val LocalMainStartupSnapshot = staticCompositionLocalOf<MainStartupSnapshot?> { null }
internal val LocalMainStartupTransition = staticCompositionLocalOf<MainStartupTransition?> { null }

/** Holds the system launch surface until local state and the requested destination have composed. */
@Stable
internal class MainStartupTransition(activity: ComponentActivity) {
    var hasPresented by mutableStateOf(false)
        private set
    private var contentReady = false
    private var destinationReady = false
    private var mainPageRequired = true
    private var mainPageReady = false
    private var animationsEnabled = true
    private val view = activity.window.decorView
    private val deadline = SystemClock.uptimeMillis() + MAX_FIRST_DRAW_WAIT_MS
    private var splashView: SplashScreenView? = null
    private var exitAnimator: ValueAnimator? = null
    private var revealScheduled = false
    private val timeout = Runnable { view.invalidate() }
    private val reveal = Runnable { animateSplashExit() }
    private val prepareReveal = Runnable { view.postOnAnimation(reveal) }
    private val revealTimeout = Runnable { scheduleReveal(force = true) }
    private val drawGate = ViewTreeObserver.OnPreDrawListener {
        if (contentReady || SystemClock.uptimeMillis() >= deadline) {
            hasPresented = true
            removeDrawGate()
            true
        } else {
            false
        }
    }

    init {
        view.viewTreeObserver.addOnPreDrawListener(drawGate)
        view.postDelayed(timeout, MAX_FIRST_DRAW_WAIT_MS)
        activity.splashScreen.setOnExitAnimationListener { splash ->
            splashView = splash
            if (!contentReady) {
                removeSplash()
            } else {
                view.postDelayed(revealTimeout, MAX_PAGE_PREPARATION_WAIT_MS)
                scheduleReveal()
            }
        }
    }

    fun onContentReady(ready: Boolean, transitionAnimationsEnabled: Boolean, waitForMainPage: Boolean) {
        destinationReady = ready
        mainPageRequired = waitForMainPage
        animationsEnabled = transitionAnimationsEnabled
        updateContentReady()
    }

    fun onMainPageReady() {
        mainPageReady = true
        updateContentReady()
    }

    private fun updateContentReady() {
        // Pager activation uses a frame clock, so it must never block the first draw. It can
        // prepare behind the transferred splash view once that draw has been permitted.
        contentReady = destinationReady
        if (contentReady && !hasPresented) view.invalidate()
        scheduleReveal()
    }

    fun dispose() {
        removeDrawGate()
        view.removeCallbacks(prepareReveal)
        view.removeCallbacks(reveal)
        view.removeCallbacks(revealTimeout)
        exitAnimator?.cancel()
        exitAnimator = null
        removeSplash()
    }

    private fun scheduleReveal(force: Boolean = false) {
        if (splashView == null || revealScheduled) return
        if (!force && mainPageRequired && !mainPageReady) return
        revealScheduled = true
        view.removeCallbacks(revealTimeout)
        if (!animationsEnabled || !ValueAnimator.areAnimatorsEnabled()) {
            removeSplash()
        } else {
            // First measurement and Liquid producer capture settle across frame boundaries.
            view.postOnAnimation(prepareReveal)
        }
    }

    private fun animateSplashExit() {
        val splash = splashView ?: return
        if (!animationsEnabled || !ValueAnimator.areAnimatorsEnabled()) {
            removeSplash()
            return
        }
        exitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SPLASH_EXIT_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { splash.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = removeSplash()
            })
            start()
        }
    }

    private fun removeDrawGate() {
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(drawGate)
        view.removeCallbacks(timeout)
    }

    private fun removeSplash() {
        splashView?.remove()
        splashView = null
    }
}

internal fun ComponentActivity.persistStartupTheme(mode: AppThemeMode) {
    // Stable resource names: Android persists this override for the next process launch.
    splashScreen.setSplashScreenTheme(when (mode) {
        AppThemeMode.FOLLOW_SYSTEM -> R.style.Theme_KeiOS_Starting
        AppThemeMode.LIGHT -> R.style.Theme_KeiOS_Starting_Light
        AppThemeMode.DARK -> R.style.Theme_KeiOS_Starting_Dark
    })
}
