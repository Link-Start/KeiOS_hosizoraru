package os.kei.ui.page.main.debug.liquidv2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import os.kei.ui.page.main.debug.DebugActivityTheme
import os.kei.ui.page.main.debug.launchDebugActivity

class DebugV2LiquidGlassSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DebugActivityTheme {
                DebugV2LiquidGlassSamplePage(onClose = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.launchDebugActivity(DebugV2LiquidGlassSampleActivity::class.java)
        }
    }
}
