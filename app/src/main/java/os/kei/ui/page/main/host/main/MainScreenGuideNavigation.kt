package os.kei.ui.page.main.host.main

import android.content.Intent
import os.kei.core.ext.showToast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import os.kei.core.intent.SafeExternalIntents
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl

private val mainScreenGuideDetailPathRegex = Regex("""^/ba/tj/\d+(?:\.html)?$""", RegexOption.IGNORE_CASE)

private fun isMainScreenGuideDetailLink(rawUrl: String): Boolean {
    val normalized = normalizeGuideUrl(rawUrl)
    if (normalized.isBlank()) return false
    val uri = runCatching { normalized.toUri() }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    val hostAccepted = host == "www.gamekee.com" || host == "gamekee.com"
    if (!hostAccepted) return false
    return mainScreenGuideDetailPathRegex.matches(uri.path.orEmpty())
}

private fun openMainScreenExternalLink(url: String, onFailure: () -> Unit, launch: (Intent) -> Unit) {
    val intent = SafeExternalIntents.browsableViewIntent(url, newTask = true)
    if (intent == null) {
        onFailure()
        return
    }
    runCatching { launch(intent) }.onFailure {
        onFailure()
    }
}

@Composable
internal fun rememberMainScreenOpenGuideDetailAction(
    poolGuideMissingText: String,
    externalOpenFailureText: String,
    onNavigateToCanonicalGuide: (String) -> Unit
): (String) -> Unit {
    val context = LocalContext.current
    val latestNavigate by rememberUpdatedState(onNavigateToCanonicalGuide)
    val latestMissingToastText by rememberUpdatedState(poolGuideMissingText)
    val latestExternalOpenFailureText by rememberUpdatedState(externalOpenFailureText)
    return remember(context) {
        { rawUrl ->
            val normalized = normalizeGuideUrl(rawUrl)
            if (normalized.isBlank()) {
                context.showToast(latestMissingToastText)
            } else if (isMainScreenGuideDetailLink(normalized)) {
                val contentId = extractGuideContentIdFromUrl(normalized)
                if (contentId == null || contentId <= 0L) {
                    context.showToast(latestMissingToastText)
                } else {
                    latestNavigate("https://www.gamekee.com/ba/tj/$contentId.html")
                }
            } else {
                openMainScreenExternalLink(
                    url = normalized,
                    onFailure = {
                        context.showToast(latestExternalOpenFailureText)
                    },
                    launch = { intent -> context.startActivity(intent) }
                )
            }
        }
    }
}
