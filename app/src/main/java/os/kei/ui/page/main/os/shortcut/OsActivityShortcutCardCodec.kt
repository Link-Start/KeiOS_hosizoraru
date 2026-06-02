package os.kei.ui.page.main.os.shortcut

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optArray
import os.kei.core.json.optBoolean
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal object OsActivityShortcutCardCodec {
    fun encodeCards(cards: List<OsActivityShortcutCard>): String {
        val array = buildJsonArray {
            cards.forEach { card ->
                val normalizedId = card.id.trim().ifBlank { newOsActivityShortcutCardId() }
                val normalizedConfig = card.config
                add(
                    buildJsonObject {
                    put(OS_ACTIVITY_CARD_KEY_ID, normalizedId)
                    put(OS_ACTIVITY_CARD_KEY_VISIBLE, card.visible)
                    put(OS_ACTIVITY_CARD_KEY_IS_BUILT_IN_SAMPLE, card.isBuiltInSample)
                    put(OS_ACTIVITY_CARD_KEY_TITLE, normalizedConfig.title)
                    put(OS_ACTIVITY_CARD_KEY_SUBTITLE, normalizedConfig.subtitle)
                    put(OS_ACTIVITY_CARD_KEY_APP_NAME, normalizedConfig.appName)
                    put(OS_ACTIVITY_CARD_KEY_PACKAGE_NAME, normalizedConfig.packageName)
                    put(OS_ACTIVITY_CARD_KEY_CLASS_NAME, normalizedConfig.className)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_ACTION, normalizedConfig.intentAction)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_CATEGORY, normalizedConfig.intentCategory)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_FLAGS, normalizedConfig.intentFlags)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_URI_DATA, normalizedConfig.intentUriData)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_MIME_TYPE, normalizedConfig.intentMimeType)
                    put(OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS, encodeIntentExtras(normalizedConfig.intentExtras))
                },
            )
            }
        }
        return array.encodeCompact()
    }

    fun decodeCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
    ): List<OsActivityShortcutCard> =
        runCatching {
            val array = raw.parseJsonArrayOrNull() ?: return@runCatching emptyList()
            buildList {
                for (element in array) {
                    val item = element as? JsonObject ?: continue
                    decodeCard(item, defaults)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

    fun decodeCard(
        item: JsonObject,
        defaults: OsGoogleSystemServiceConfig,
    ): OsActivityShortcutCard? {
        if (!isActivityCardItem(item)) return null
        val config =
            OsGoogleSystemServiceConfig(
                title = item.optString(OS_ACTIVITY_CARD_KEY_TITLE),
                subtitle = item.optString(OS_ACTIVITY_CARD_KEY_SUBTITLE),
                appName = item.optString(OS_ACTIVITY_CARD_KEY_APP_NAME),
                packageName = item.optString(OS_ACTIVITY_CARD_KEY_PACKAGE_NAME),
                className = item.optString(OS_ACTIVITY_CARD_KEY_CLASS_NAME),
                intentAction = item.optString(OS_ACTIVITY_CARD_KEY_INTENT_ACTION),
                intentCategory = item.optString(OS_ACTIVITY_CARD_KEY_INTENT_CATEGORY),
                intentFlags = item.optString(OS_ACTIVITY_CARD_KEY_INTENT_FLAGS),
                intentUriData = item.optString(OS_ACTIVITY_CARD_KEY_INTENT_URI_DATA),
                intentMimeType = item.optString(OS_ACTIVITY_CARD_KEY_INTENT_MIME_TYPE),
                intentExtras = decodeIntentExtras(item.optArray(OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS)),
            )
        return OsActivityShortcutCard(
            id =
                item
                    .optString(OS_ACTIVITY_CARD_KEY_ID)
                    .trim()
                    .ifBlank { newOsActivityShortcutCardId() },
            visible = item.optBoolean(OS_ACTIVITY_CARD_KEY_VISIBLE, true),
            isBuiltInSample = item.optBoolean(OS_ACTIVITY_CARD_KEY_IS_BUILT_IN_SAMPLE, false),
            config = normalizeActivityShortcutConfig(config, defaults),
        )
    }

    private fun isActivityCardItem(item: JsonObject): Boolean =
        item.containsKey(OS_ACTIVITY_CARD_KEY_APP_NAME) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_PACKAGE_NAME) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_CLASS_NAME) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_ACTION) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_CATEGORY) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_FLAGS) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_URI_DATA) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_MIME_TYPE) ||
            item.containsKey(OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS)

    private fun encodeIntentExtras(extras: List<ShortcutIntentExtra>): JsonArray {
        val array = buildJsonArray {
            normalizeShortcutIntentExtras(extras).forEach { extra ->
                add(
                    buildJsonObject {
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_KEY, extra.key)
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_TYPE, extra.type.rawValue)
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_VALUE, extra.value)
                },
            )
            }
        }
        return array
    }

    private fun decodeIntentExtras(raw: JsonArray?): List<ShortcutIntentExtra> {
        if (raw == null) return emptyList()
        return buildList {
            for (element in raw) {
                val item = element as? JsonObject ?: continue
                add(
                    ShortcutIntentExtra(
                        key = item.optString(OS_ACTIVITY_CARD_KEY_EXTRA_KEY),
                        type = ShortcutIntentExtraType.fromRaw(item.optString(OS_ACTIVITY_CARD_KEY_EXTRA_TYPE)),
                        value = item.optString(OS_ACTIVITY_CARD_KEY_EXTRA_VALUE),
                    ),
                )
            }
        }.let(::normalizeShortcutIntentExtras)
    }
}
