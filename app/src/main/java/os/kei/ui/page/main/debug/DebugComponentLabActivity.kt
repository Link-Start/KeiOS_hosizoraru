package os.kei.ui.page.main.debug

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import os.kei.ui.page.main.debug.liquidv2.DebugV2LiquidGlassSampleActivity

class DebugComponentLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DebugActivityTheme {
                DebugComponentLabPage(
                    onClose = { finish() },
                    onOpenV2LiquidSample = { DebugV2LiquidGlassSampleActivity.launch(this) },
                    onOpenLiquidCatalog = { DebugLiquidCatalogActivity.launch(this) }
                )
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.launchDebugActivity(DebugComponentLabActivity::class.java)
        }
    }
}
