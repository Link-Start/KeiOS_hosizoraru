package os.kei.ui.page.main.os.shortcut

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal fun defaultBuiltInActivityShortcutCards(builtInSampleDefaults: OsGoogleSystemServiceConfig): List<OsActivityShortcutCard> =
    googleSettingsBuiltInCard(builtInSampleDefaults)

internal fun resolveBuiltInActivityShortcutCards(
    builtInSampleDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
): List<OsActivityShortcutCard> =
    builtInActivityShortcutCards.ifEmpty {
        defaultBuiltInActivityShortcutCards(builtInSampleDefaults)
    }
