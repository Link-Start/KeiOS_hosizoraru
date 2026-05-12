package os.kei.core.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AppShortcutActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchShortcut(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchShortcut(intent)
        finish()
    }

    private fun dispatchShortcut(source: Intent?) {
        val shortcutIntent = Intent(this, AppShortcutActionReceiver::class.java).apply {
            action = AppShortcutActionReceiver.ACTION_HANDLE_SHORTCUT
            source?.extras?.let(::putExtras)
        }
        sendBroadcast(shortcutIntent)
    }
}
