@file:Suppress("FunctionName")
package os.kei.ui.page.main.student.model3d

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyLoading
import os.kei.BuildConfig
import os.kei.core.log.AppLogger
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

private const val MODEL_ORIGIN = "https://appassets.androidplatform.net"
private const val MODEL_PAGE = "$MODEL_ORIGIN/ba3d/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun GuideModel3dPlayer(
    resource: BaModel3dResource,
    model: BaModel3dAsset,
    playing: Boolean,
    actionRequest: Pair<Int, String>,
    resetRequest: Int,
    retry: Int,
    options: BaModel3dOptions,
    seekRequest: Pair<Int, Float>,
    pollProgress: Boolean,
    onState: (BaModel3dPlaybackState) -> Unit,
    onError: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notifyState by rememberUpdatedState(onState)
    val notifyError by rememberUpdatedState(onError)
    val shouldPollProgress by rememberUpdatedState(pollProgress && resumed)
    val isResumed by rememberUpdatedState(resumed)
    key(resource.contentId, retry) {
        var view by remember { mutableStateOf<WebView?>(null) }
        var ready by remember { mutableStateOf(false) }
        var scriptReady by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        val session = remember(model.gitBlob) { BaModel3dCacheSession() }
        val currentSession by rememberUpdatedState(session)
        val currentModel by rememberUpdatedState(model)
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(view, retry, model) {
            val web = view ?: return@LaunchedEffect
            failed = false
            notifyError(false)
            var waiting = 0
            while (!failed) {
                if (!isResumed) { delay(500); continue }
                val raw = suspendCancellableCoroutine { c ->
                    web.evaluateJavascript("window.keiosModel ? window.keiosModel.state() : null") { if (c.isActive) c.resume(it) }
                }
                val state = runCatching { JSONObject(raw) }.getOrNull()
                if (state != null) {
                    scriptReady = true
                  if (state.optString("url") == "$MODEL_ORIGIN/ba3d/models/${model.gitBlob}.glb") {
                    ready = state.optBoolean("ready")
                    if (state.optString("error").isNotBlank()) { failed = true; notifyError(true) }
                    val array = state.optJSONArray("actions")
                    val actions = if (array == null) emptyList() else List(minOf(array.length(), 100)) { array.optString(it) }
                    val durations = state.optJSONArray("durations")
                    notifyState(BaModel3dPlaybackState(actions, List(actions.size) { durations?.optDouble(it, 0.0)?.toFloat() ?: 0f },
                        state.optString("selected"), ready, state.optDouble("time", 0.0).toFloat(),
                        state.optDouble("duration", 0.0).toFloat(), state.optBoolean("ended")))
                  }
                }
                waiting = if (ready) 0 else waiting + 1
                if (waiting >= 300) { failed = true; notifyError(true) }
                delay(if (ready && !shouldPollProgress) 1_000 else 200)
            }
        }
        LaunchedEffect(view, scriptReady, model) {
            if (!scriptReady) return@LaunchedEffect
            ready = false; notifyState(BaModel3dPlaybackState())
            view?.evaluateJavascript("window.keiosModel.load(${JSONObject().put("url", "$MODEL_ORIGIN/ba3d/models/${model.gitBlob}.glb")})", null)
        }
        LaunchedEffect(view, scriptReady, resumed, playing) {
            view?.let { web ->
                if (resumed) web.onResume() else web.onPause()
                if (scriptReady) web.evaluateJavascript("window.keiosModel.setPlaying($playing);window.keiosModel.setForeground($resumed)", null)
            }
        }
        LaunchedEffect(view, scriptReady, actionRequest) {
            if (scriptReady && actionRequest.first > 0) view?.evaluateJavascript(
                "window.keiosModel.selectAnimation(${JSONObject.quote(actionRequest.second)})", null,
            )
        }
        LaunchedEffect(view, scriptReady, options) {
            if (scriptReady) view?.evaluateJavascript("window.keiosModel.setOptions(${JSONObject()
                .put("speed", options.speed).put("loop", options.loop).put("outline", options.outline)
                .put("outlineWidth", options.outlineWidth).put("scrubbing", options.scrubbing)})", null)
        }
        LaunchedEffect(view, scriptReady, seekRequest) {
            if (scriptReady && seekRequest.first > 0) view?.evaluateJavascript("window.keiosModel.seek(${seekRequest.second})", null)
        }
        LaunchedEffect(view, scriptReady, resetRequest) {
            if (scriptReady && resetRequest > 0) view?.evaluateJavascript("window.keiosModel.resetCamera()", null)
        }
        Box(modifier) {
            AndroidView(modifier = modifier, factory = { context ->
                WebView(context).apply {
                    if (BuildConfig.DEBUG || BuildConfig.APPLICATION_ID.endsWith(".diag")) WebView.setWebContentsDebuggingEnabled(true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (message.messageLevel() in listOf(ConsoleMessage.MessageLevel.ERROR, ConsoleMessage.MessageLevel.WARNING)) {
                                AppLogger.w("BaModel3d", message.message().take(2_000))
                            }
                            return true
                        }
                    }
                    setBackgroundColor(Color.rgb(12, 20, 36))
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    settings.apply {
                        javaScriptEnabled = true; domStorageEnabled = false
                        allowFileAccess = false; allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setGeolocationEnabled(false); mediaPlaybackRequiresUserGesture = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.toString() != MODEL_PAGE
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                            val path = request.url.path.orEmpty()
                            if (request.url.scheme != "https" || request.url.host != "appassets.androidplatform.net" || request.method != "GET") return unavailable()
                            return try {
                                if (path.startsWith("/ba3d/models/")) {
                                    val asset = currentModel.takeIf { path == "/ba3d/models/${it.gitBlob}.glb" } ?: return unavailable()
                                    WebResourceResponse("model/gltf-binary", null, BaModel3dCache.get(context).open(asset, currentSession))
                                } else {
                                    if (!path.startsWith("/ba3d/") || ".." in path || '%' in path || '\\' in path) return unavailable()
                                    val mime = when {
                                        path.endsWith(".html") -> "text/html"
                                        path.endsWith(".js") -> "application/javascript"
                                        else -> return unavailable()
                                    }
                                    WebResourceResponse(mime, "UTF-8", context.assets.open(path.removePrefix("/")))
                                }
                            } catch (_: Exception) { unavailable() }
                        }
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            failed = true; notifyError(true); return true
                        }
                    }
                    view = this
                    loadUrl(MODEL_PAGE)
                }
            }, onRelease = { web ->
                currentSession.close(); web.stopLoading(); web.onPause(); web.webViewClient = WebViewClient(); web.removeAllViews(); web.destroy()
            })
            if (!failed) GuideWebMemoryLobbyLoading(visible = !ready)
        }
    }
}

private fun unavailable() = WebResourceResponse("text/plain", "UTF-8", 503, "Model unavailable",
    mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(byteArrayOf()))
