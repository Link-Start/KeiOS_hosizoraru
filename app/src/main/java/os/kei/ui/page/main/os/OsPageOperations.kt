package os.kei.ui.page.main.os

import android.content.Context
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ensureEditorActivityShortcutDraft
import os.kei.ui.page.main.os.shortcut.launchGoogleSystemServiceActivity
import os.kei.ui.page.main.os.shortcut.normalizeActivityShortcutConfig

internal fun openOsActivityShortcutCard(
    context: Context,
    card: OsActivityShortcutCard,
    defaults: OsGoogleSystemServiceConfig,
    invalidTargetMessage: String,
    openFailedMessage: (Throwable) -> String,
) {
    val normalized =
        normalizeActivityShortcutConfig(
            config = card.config,
            defaults = defaults,
        )
    if (normalized.packageName.isBlank()) {
        context.showToast(invalidTargetMessage)
        return
    }
    runCatching {
        launchGoogleSystemServiceActivity(
            context = context,
            config = normalized,
            defaults = defaults,
        )
    }.onFailure { error ->
        context.showToast(openFailedMessage(error))
    }
}

internal fun beginEditingOsActivityShortcutCard(
    card: OsActivityShortcutCard,
    defaults: OsGoogleSystemServiceConfig,
    onEditModeChange: (OsActivityCardEditMode) -> Unit,
    onEditingCardIdChange: (String?) -> Unit,
    onEditingBuiltInChange: (Boolean) -> Unit,
    onDraftChange: (OsGoogleSystemServiceConfig) -> Unit,
    onShowEditorChange: (Boolean) -> Unit,
) {
    onEditModeChange(OsActivityCardEditMode.Edit)
    onEditingCardIdChange(card.id)
    onEditingBuiltInChange(card.isBuiltInSample)
    onDraftChange(
        ensureEditorActivityShortcutDraft(
            normalizeActivityShortcutConfig(
                config = card.config,
                defaults = defaults,
            ),
        ),
    )
    onShowEditorChange(true)
}
