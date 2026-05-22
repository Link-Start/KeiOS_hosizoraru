package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.createDefaultActivityShortcutDraft
import os.kei.ui.page.main.os.shortcut.ensureEditorActivityShortcutDraft
import os.kei.ui.page.main.os.shortcut.normalizeActivityShortcutConfig

internal data class OsActivityShortcutEditorRequest(
    val editMode: OsActivityCardEditMode,
    val editingCardId: String?,
    val editingBuiltIn: Boolean,
    val draft: OsGoogleSystemServiceConfig,
)

internal class OsPageActivityShortcutRepository(
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun normalizeForOpen(
        card: OsActivityShortcutCard,
        defaults: OsGoogleSystemServiceConfig,
    ): OsGoogleSystemServiceConfig =
        withContext(defaultDispatcher) {
            normalizeActivityShortcutConfig(
                config = card.config,
                defaults = defaults,
            )
        }

    suspend fun buildEditRequest(
        card: OsActivityShortcutCard,
        defaults: OsGoogleSystemServiceConfig,
    ): OsActivityShortcutEditorRequest =
        withContext(defaultDispatcher) {
            OsActivityShortcutEditorRequest(
                editMode = OsActivityCardEditMode.Edit,
                editingCardId = card.id,
                editingBuiltIn = card.isBuiltInSample,
                draft =
                    ensureEditorActivityShortcutDraft(
                        normalizeActivityShortcutConfig(
                            config = card.config,
                            defaults = defaults,
                        ),
                    ),
            )
        }

    suspend fun buildAddRequest(
        defaults: OsGoogleSystemServiceConfig,
    ): OsActivityShortcutEditorRequest =
        withContext(defaultDispatcher) {
            OsActivityShortcutEditorRequest(
                editMode = OsActivityCardEditMode.Add,
                editingCardId = null,
                editingBuiltIn = false,
                draft = createDefaultActivityShortcutDraft(defaults),
            )
        }
}
