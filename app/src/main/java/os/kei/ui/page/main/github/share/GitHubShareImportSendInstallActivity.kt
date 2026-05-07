package os.kei.ui.page.main.github.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class GitHubShareImportSendInstallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressActivityTransition()
        lifecycleScope.launch {
            GitHubShareImportFlowCoordinator.sendActivePreviewAssetToInstaller(
                context = this@GitHubShareImportSendInstallActivity
            )
            finishWithoutTransition()
        }
    }

    override fun finish() {
        super.finish()
        suppressActivityTransition()
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutTransition() {
        if (!isFinishing) {
            finish()
        }
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityTransition() {
        overridePendingTransition(0, 0)
    }
}
