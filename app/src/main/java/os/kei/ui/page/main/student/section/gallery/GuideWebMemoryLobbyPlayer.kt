@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import os.kei.R
import os.kei.ui.page.main.student.gameKeeMemoryLobbyViewerUrl
import os.kei.ui.page.main.student.BaGuideWebMemoryLobby
import os.kei.ui.page.main.student.BaGuideSpineWebCache
import os.kei.ui.page.main.student.BaGuideSpineWebCacheSession
import kotlin.coroutines.resume

/** Reuses the Wiki renderer through a narrow playback controller. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GuideWebMemoryLobbyPlayer(
    resource: BaGuideWebMemoryLobby,
    playing: Boolean,
    retryToken: Int,
    stateRequest: Int,
    actionRequest: Int,
    selectedAction: String,
    onActionsAvailable: (List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerUrl = resource.viewerUrl
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notifyActions by rememberUpdatedState(onActionsAvailable)
    key(resource, retryToken) {
        var view by remember { mutableStateOf<WebView?>(null) }
        var ready by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var cacheSession by remember { mutableStateOf<BaGuideSpineWebCacheSession?>(null) }
        LaunchedEffect(view, failed) {
            notifyActions(emptyList(), "")
            val web = view ?: return@LaunchedEffect
            if (failed) return@LaunchedEffect
            repeat(90) {
                val state = suspendCancellableCoroutine { continuation ->
                    web.evaluateJavascript(GameKeeLobbyFocusScript) { result ->
                        if (continuation.isActive) continuation.resume(result.orEmpty())
                    }
                }
                if (state.startsWith("\"ready")) {
                    ready = true
                    return@LaunchedEffect
                }
                delay(1_000)
            }
            failed = true
        }
        // Refresh on opening the picker and returning to the foreground: the Wiki can transition
        // from its introduction to Idle without a native action request.
        LaunchedEffect(view, ready, resumed, stateRequest) {
            if (ready && resumed) view?.evaluateJavascript("window.keiosLobby.state()") { result ->
                val actions = runCatching {
                    val state = JSONObject(result.orEmpty())
                    val array = state.getJSONArray("actions")
                    val names = (0 until minOf(array.length(), 100)).map { array.optString(it).trim() }
                        .filter(String::isNotBlank).distinct()
                    names to state.optString("action")
                }.getOrDefault(emptyList<String>() to "")
                notifyActions(actions.first, actions.second)
            }
        }
        LaunchedEffect(view, ready, resumed, playing) {
            view?.let { web ->
                if (resumed) web.onResume() else web.onPause()
                if (ready) web.evaluateJavascript("window.keiosLobby.setPlaying(${playing && resumed})", null)
            }
        }
        // The page controller only exposes playback; no native app services are exposed to JS.
        LaunchedEffect(actionRequest) {
            if (actionRequest > 0 && selectedAction.isNotBlank()) {
                view?.evaluateJavascript(gameKeeLobbySelectActionScript(selectedAction), null)
            }
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            if (!failed) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (ready) 1f else 0f },
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                setGeolocationEnabled(false)
                                mediaPlaybackRequiresUserGesture = true
                                // The desktop SPA owns BA's interactive background player.
                                userAgentString = userAgentString
                                    .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
                                    .replace(" Mobile", "").replace("Version/4.0 ", "")
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            val session = BaGuideSpineWebCacheSession(
                                resource, { BaGuideSpineWebCache.get(context.applicationContext) },
                                settings.userAgentString, revalidate = retryToken > 0,
                            )
                            cacheSession = session
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                    session.intercept(request)

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    gameKeeMemoryLobbyViewerUrl(request.url.toString()) != viewerUrl

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) failed = true
                                }

                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                    if (request.isForMainFrame) failed = true
                                }

                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    failed = true
                                    return true
                                }
                            }
                            view = this
                            loadUrl(viewerUrl)
                        }
                    },
                    onRelease = { web ->
                        web.stopLoading()
                        cacheSession?.close()
                        web.onPause()
                        web.webViewClient = WebViewClient()
                        web.removeAllViews()
                        web.destroy()
                        notifyActions(emptyList(), "")
                    },
                )
            }
            if (!ready || failed) {
                GuideWebMemoryLobbyStatus(
                    stringResource(
                        when {
                            failed -> R.string.guide_gallery_dynamic_lobby_failed
                            else -> R.string.guide_gallery_dynamic_lobby_loading
                        },
                    ),
                )
            }
        }
    }
}
