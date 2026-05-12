package os.kei.core.ui.resource

import android.content.Context
import androidx.annotation.StringRes

fun Context.resolveString(@StringRes resId: Int, vararg formatArgs: Any?): String {
    return if (formatArgs.isEmpty()) {
        getString(resId)
    } else {
        getString(resId, *formatArgs)
    }
}
