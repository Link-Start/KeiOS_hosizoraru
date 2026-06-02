package os.kei.ui.page.main.os

import os.kei.ui.page.main.os.shortcut.ShortcutIntentExtra
import os.kei.ui.page.main.os.shortcut.ShortcutIntentExtraType
import os.kei.ui.page.main.os.shortcut.normalizeShortcutIntentExtras
import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.core.prefs.KeiMmkv

internal object OsShortcutCardStore {
    private const val KV_ID = "os_ui_state"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_TITLE = "google_system_service_title"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_SUBTITLE = "google_system_service_subtitle"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_APP_NAME = "google_system_service_app_name"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_PACKAGE = "google_system_service_package"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_CLASS = "google_system_service_class"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_ACTION = "google_system_service_action"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_CATEGORY = "google_system_service_category"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_FLAGS = "google_system_service_flags"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_URI_DATA = "google_system_service_uri_data"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_DATA = "google_system_service_data"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_MIME_TYPE = "google_system_service_mime_type"
    private const val KEY_GOOGLE_SYSTEM_SERVICE_INTENT_EXTRAS = "google_system_service_intent_extras"
    private const val KEY_EXTRA_KEY = "key"
    private const val KEY_EXTRA_TYPE = "type"
    private const val KEY_EXTRA_VALUE = "value"
    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    fun loadGoogleSystemServiceConfig(
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig()
    ): OsGoogleSystemServiceConfig {
        return OsGoogleSystemServiceConfig(
            title = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_TITLE, defaults.title).orEmpty(),
            subtitle = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_SUBTITLE, defaults.subtitle)
                .orEmpty(),
            appName = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_APP_NAME, defaults.appName)
                .orEmpty(),
            packageName = store.decodeString(
                KEY_GOOGLE_SYSTEM_SERVICE_PACKAGE,
                defaults.packageName
            ).orEmpty(),
            className = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_CLASS, defaults.className)
                .orEmpty(),
            intentAction = store.decodeString(
                KEY_GOOGLE_SYSTEM_SERVICE_ACTION,
                defaults.intentAction
            ).orEmpty(),
            intentCategory = store.decodeString(
                KEY_GOOGLE_SYSTEM_SERVICE_CATEGORY,
                defaults.intentCategory
            ).orEmpty(),
            intentFlags = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_FLAGS, defaults.intentFlags)
                .orEmpty(),
            intentUriData = store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_URI_DATA)
                ?: store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_DATA, defaults.intentUriData)
                    .orEmpty(),
            intentMimeType = store.decodeString(
                KEY_GOOGLE_SYSTEM_SERVICE_MIME_TYPE,
                defaults.intentMimeType
            ).orEmpty(),
            intentExtras = decodeIntentExtras(
                store.decodeString(KEY_GOOGLE_SYSTEM_SERVICE_INTENT_EXTRAS)
            )
        ).normalized(defaults)
    }

    fun saveGoogleSystemServiceConfig(
        config: OsGoogleSystemServiceConfig,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig()
    ) {
        val normalized = config.normalized(defaults)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_TITLE, normalized.title)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_SUBTITLE, normalized.subtitle)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_APP_NAME, normalized.appName)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_PACKAGE, normalized.packageName)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_CLASS, normalized.className)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_ACTION, normalized.intentAction)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_CATEGORY, normalized.intentCategory)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_FLAGS, normalized.intentFlags)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_URI_DATA, normalized.intentUriData)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_DATA, normalized.intentUriData)
        store.encode(KEY_GOOGLE_SYSTEM_SERVICE_MIME_TYPE, normalized.intentMimeType)
        store.encode(
            KEY_GOOGLE_SYSTEM_SERVICE_INTENT_EXTRAS,
            encodeIntentExtras(normalized.intentExtras)
        )
    }

    private fun encodeIntentExtras(extras: List<ShortcutIntentExtra>): String {
        val normalized = normalizeShortcutIntentExtras(extras)
        val array = buildJsonArray {
            normalized.forEach { extra ->
                add(
                    buildJsonObject {
                    put(KEY_EXTRA_KEY, extra.key)
                    put(KEY_EXTRA_TYPE, extra.type.rawValue)
                    put(KEY_EXTRA_VALUE, extra.value)
                }
            )
            }
        }
        return array.encodeCompact()
    }

    private fun decodeIntentExtras(raw: String?): List<ShortcutIntentExtra> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = raw.parseJsonArrayOrNull() ?: return@runCatching emptyList()
            buildList {
                for (element in array) {
                    val item = element as? JsonObject ?: continue
                    add(
                        ShortcutIntentExtra(
                            key = item.optString(KEY_EXTRA_KEY),
                            type = ShortcutIntentExtraType.fromRaw(item.optString(KEY_EXTRA_TYPE)),
                            value = item.optString(KEY_EXTRA_VALUE)
                        )
                    )
                }
            }.let(::normalizeShortcutIntentExtras)
        }.getOrDefault(emptyList())
    }
}
