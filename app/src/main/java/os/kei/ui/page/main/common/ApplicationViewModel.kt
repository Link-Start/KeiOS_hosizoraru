package os.kei.ui.page.main.common

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal inline fun <reified VM : ViewModel> applicationViewModel(
    key: String? = null,
    crossinline create: (Application) -> VM,
): VM {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as Application }
    val factory =
        remember(application) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = create(application) as T
            }
        }
    return viewModel(
        modelClass = VM::class.java,
        key = key,
        factory = factory,
    )
}
