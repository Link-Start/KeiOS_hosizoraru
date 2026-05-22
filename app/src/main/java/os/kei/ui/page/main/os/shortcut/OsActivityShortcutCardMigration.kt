package os.kei.ui.page.main.os.shortcut

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal object OsActivityShortcutCardMigration {
    fun migrateBuiltInSampleCards(
        cards: List<OsActivityShortcutCard>,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            defaultBuiltInActivityShortcutCards(builtInSampleDefaults),
        appendMissingBuiltIns: Boolean = false,
    ): List<OsActivityShortcutCard> =
        migrateBuiltInActivityShortcutCards(
            cards = cards,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
            appendMissingBuiltIns = appendMissingBuiltIns,
        )

    fun migrateBuiltInActivityShortcutCards(
        cards: List<OsActivityShortcutCard>,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            defaultBuiltInActivityShortcutCards(builtInSampleDefaults),
        appendMissingBuiltIns: Boolean = false,
    ): List<OsActivityShortcutCard> {
        val builtIns =
            resolveBuiltInActivityShortcutCards(
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        val migrated =
            cards
                .filterNot { card -> card.id in deprecatedBuiltInActivityCardIds }
                .map { card ->
                    val matchingBuiltIn =
                        builtIns.firstOrNull { builtIn ->
                            isBuiltInActivityShortcutCard(
                                card = card,
                                builtInCard = builtIn,
                                builtInSampleDefaults = builtInSampleDefaults,
                            )
                        }
                    if (matchingBuiltIn != null) {
                        upgradeBuiltInActivityShortcutCard(
                            card = card,
                            builtInCard = matchingBuiltIn,
                            builtInSampleDefaults = builtInSampleDefaults,
                        )
                    } else {
                        card.copy(isBuiltInSample = false)
                    }
                }.let(::deduplicateActivityShortcutCardsById)
                .toMutableList()
        if (appendMissingBuiltIns) {
            builtIns.forEach { builtIn ->
                val alreadyPresent =
                    migrated.any { card ->
                        card.id == builtIn.id || osActivityShortcutMergeKey(card) == osActivityShortcutMergeKey(builtIn)
                    }
                if (!alreadyPresent) {
                    migrated += builtIn
                }
            }
        }
        return migrated
    }

    private fun deduplicateActivityShortcutCardsById(cards: List<OsActivityShortcutCard>): List<OsActivityShortcutCard> {
        val seenIds = mutableSetOf<String>()
        return cards.filter { card ->
            seenIds.add(card.id)
        }
    }

    private fun upgradeBuiltInActivityShortcutCard(
        card: OsActivityShortcutCard,
        builtInCard: OsActivityShortcutCard,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
    ): OsActivityShortcutCard {
        val builtInConfig = builtInCard.config
        val upgradedClassName =
            when {
                builtInCard.id == BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID &&
                    card.config.className
                        .trim()
                        .equals(LEGACY_GOOGLE_SETTINGS_ACTIVITY_CLASS, ignoreCase = true) -> {
                    builtInConfig.className
                }

                else -> {
                    card.config.className
                        .trim()
                        .ifBlank { builtInConfig.className }
                }
            }
        val upgradedConfig =
            normalizeActivityShortcutConfig(
                config =
                    card.config.copy(
                        title = builtInConfig.title,
                        subtitle = builtInConfig.subtitle,
                        appName = builtInConfig.appName,
                        packageName =
                            card.config.packageName
                                .trim()
                                .ifBlank { builtInConfig.packageName },
                        className = upgradedClassName,
                        intentAction =
                            card.config.intentAction
                                .trim()
                                .ifBlank { builtInConfig.intentAction },
                        intentCategory =
                            card.config.intentCategory
                                .trim()
                                .ifBlank { builtInConfig.intentCategory },
                        intentFlags =
                            card.config.intentFlags
                                .trim()
                                .ifBlank { builtInConfig.intentFlags },
                        intentUriData =
                            card.config.intentUriData
                                .trim()
                                .ifBlank { builtInConfig.intentUriData },
                        intentMimeType =
                            card.config.intentMimeType
                                .trim()
                                .ifBlank { builtInConfig.intentMimeType },
                        intentExtras = card.config.intentExtras.ifEmpty { builtInConfig.intentExtras },
                    ),
                defaults = builtInSampleDefaults,
            )
        return card.copy(
            id = builtInCard.id,
            isBuiltInSample = true,
            config = upgradedConfig,
        )
    }

    private fun isBuiltInActivityShortcutCard(
        card: OsActivityShortcutCard,
        builtInCard: OsActivityShortcutCard,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
    ): Boolean {
        if (card.id == builtInCard.id) return true
        if (card.isBuiltInSample && osActivityShortcutMergeKey(card) == osActivityShortcutMergeKey(builtInCard)) return true
        if (builtInCard.id != BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID) return false
        return isGoogleSettingsSampleCard(
            card = card,
            builtInSampleDefaults = builtInSampleDefaults,
        )
    }

    private fun isGoogleSettingsSampleCard(
        card: OsActivityShortcutCard,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
    ): Boolean {
        if (card.isBuiltInSample) return true
        if (card.id == BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID) return true
        val targetTitle = builtInSampleDefaults.title.trim()
        if (targetTitle.isBlank()) return false
        return card.config.title.trim() == targetTitle
    }
}
