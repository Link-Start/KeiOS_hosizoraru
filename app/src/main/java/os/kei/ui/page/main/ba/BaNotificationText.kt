package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.R

internal fun baAccountNotificationContent(
    context: Context,
    accountDisplayName: String,
    content: String,
): String {
    val accountLabel = accountDisplayName.trim()
    val normalizedContent = content.trim()
    return if (accountLabel.isBlank() || normalizedContent.isBlank()) {
        normalizedContent
    } else {
        context.getString(
            R.string.ba_account_notification_content,
            accountLabel,
            normalizedContent,
        )
    }
}
