package os.kei.ui.page.main.os.shortcut

import org.json.JSONArray
import org.json.JSONObject
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal object OsActivityShortcutCardCodec {
    fun encodeCards(cards: List<OsActivityShortcutCard>): String {
        val array = JSONArray()
        cards.forEach { card ->
            val normalizedId = card.id.trim().ifBlank { newOsActivityShortcutCardId() }
            val normalizedConfig = card.config
            array.put(
                JSONObject().apply {
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
        return array.toString()
    }

    fun decodeCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
    ): List<OsActivityShortcutCard> =
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    decodeCard(item, defaults)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

    fun decodeCard(
        item: JSONObject,
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
                intentExtras = decodeIntentExtras(item.optJSONArray(OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS)),
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

    private fun isActivityCardItem(item: JSONObject): Boolean =
        item.has(OS_ACTIVITY_CARD_KEY_APP_NAME) ||
            item.has(OS_ACTIVITY_CARD_KEY_PACKAGE_NAME) ||
            item.has(OS_ACTIVITY_CARD_KEY_CLASS_NAME) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_ACTION) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_CATEGORY) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_FLAGS) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_URI_DATA) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_MIME_TYPE) ||
            item.has(OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS)

    private fun encodeIntentExtras(extras: List<ShortcutIntentExtra>): JSONArray {
        val array = JSONArray()
        normalizeShortcutIntentExtras(extras).forEach { extra ->
            array.put(
                JSONObject().apply {
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_KEY, extra.key)
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_TYPE, extra.type.rawValue)
                    put(OS_ACTIVITY_CARD_KEY_EXTRA_VALUE, extra.value)
                },
            )
        }
        return array
    }

    private fun decodeIntentExtras(raw: JSONArray?): List<ShortcutIntentExtra> {
        if (raw == null) return emptyList()
        return buildList {
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
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
