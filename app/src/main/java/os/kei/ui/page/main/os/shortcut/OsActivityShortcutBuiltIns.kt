package os.kei.ui.page.main.os.shortcut

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal fun googleSettingsBuiltInCard(builtInSampleDefaults: OsGoogleSystemServiceConfig): List<OsActivityShortcutCard> =
    listOf(
        OsActivityShortcutCard(
            id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
            visible = true,
            isBuiltInSample = true,
            config = builtInSampleDefaults,
        ),
    )

internal fun osActivityShortcutMergeKey(card: OsActivityShortcutCard): String {
    val config = card.config
    return listOf(
        config.packageName.trim().lowercase(),
        config.className.trim().lowercase(),
        config.intentAction.trim().lowercase(),
        config.intentUriData.trim().lowercase(),
        config.title.trim().lowercase(),
    ).joinToString("|")
}

internal fun osActivityShortcutCardsEquivalent(
    old: OsActivityShortcutCard,
    new: OsActivityShortcutCard,
    defaults: OsGoogleSystemServiceConfig,
): Boolean {
    val oldConfig = normalizeActivityShortcutConfig(old.config, defaults)
    val newConfig = normalizeActivityShortcutConfig(new.config, defaults)
    return old.visible == new.visible &&
        old.isBuiltInSample == new.isBuiltInSample &&
        oldConfig == newConfig
}
