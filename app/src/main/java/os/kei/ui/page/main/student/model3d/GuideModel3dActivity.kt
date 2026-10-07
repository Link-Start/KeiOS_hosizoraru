package os.kei.ui.page.main.student.model3d

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.BaStandaloneActivityTheme
import os.kei.ui.page.main.widget.sheet.SceneBackdropHost
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyLoading
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Only a bundled catalog identity crosses the intent; callers cannot supply executable pages or arbitrary asset URLs. */
class GuideModel3dActivity : ComponentActivity() {
    private var resource by mutableStateOf<BaModel3dResource?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val contentId = intent.getLongExtra("content_id", 0)
        lifecycleScope.launch {
            resource = withContext(AppDispatchers.fileIo) { BaModel3dCatalog.load(applicationContext).forContentId(contentId) }
            if (resource == null) finish()
        }
        setContent {
            BaStandaloneActivityTheme {
              // This media viewport is always dark, including when the rest of the app uses light mode.
              MiuixTheme(controller = remember { ThemeController(ColorSchemeMode.Dark) }) {
                SceneBackdropHost(backgroundColor = androidx.compose.ui.graphics.Color(0xFF0C1424)) {
                  Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0C1424))) {
                    val model = resource
                    if (model == null) GuideWebMemoryLobbyLoading(true)
                    else GuideModel3dScreen(model, ::finish) { visible ->
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                  }
                }
              }
            }
        }
    }
    companion object {
        internal fun launch(context: Context, resource: BaModel3dResource) {
            context.startActivity(Intent(context, GuideModel3dActivity::class.java).putExtra("content_id", resource.contentId))
        }
    }
}
