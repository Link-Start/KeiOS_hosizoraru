package os.kei.ui.page.main.os.shortcut

internal const val OS_ACTIVITY_SHORTCUT_CARD_KV_ID = "os_activity_shortcut_cards"
internal const val OS_ACTIVITY_SHORTCUT_CARD_LEGACY_KV_ID = "os_ui_state"
internal const val OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS = "activity_shortcut_cards_v1"

internal const val OS_ACTIVITY_CARD_KEY_ID = "id"
internal const val OS_ACTIVITY_CARD_KEY_VISIBLE = "visible"
internal const val OS_ACTIVITY_CARD_KEY_IS_BUILT_IN_SAMPLE = "isBuiltInSample"
internal const val OS_ACTIVITY_CARD_KEY_TITLE = "title"
internal const val OS_ACTIVITY_CARD_KEY_SUBTITLE = "subtitle"
internal const val OS_ACTIVITY_CARD_KEY_APP_NAME = "appName"
internal const val OS_ACTIVITY_CARD_KEY_PACKAGE_NAME = "packageName"
internal const val OS_ACTIVITY_CARD_KEY_CLASS_NAME = "className"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_ACTION = "intentAction"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_CATEGORY = "intentCategory"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_FLAGS = "intentFlags"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_URI_DATA = "intentUriData"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_MIME_TYPE = "intentMimeType"
internal const val OS_ACTIVITY_CARD_KEY_INTENT_EXTRAS = "intentExtras"
internal const val OS_ACTIVITY_CARD_KEY_EXTRA_KEY = "key"
internal const val OS_ACTIVITY_CARD_KEY_EXTRA_TYPE = "type"
internal const val OS_ACTIVITY_CARD_KEY_EXTRA_VALUE = "value"

internal const val OS_ACTIVITY_CARD_KEY_EXPORT_SCHEMA = "schema"
internal const val OS_ACTIVITY_CARD_KEY_EXPORT_SCHEMA_VERSION = "schemaVersion"
internal const val OS_ACTIVITY_CARD_KEY_EXPORT_EXPORTED_AT = "exportedAtMillis"
internal const val OS_ACTIVITY_CARD_KEY_EXPORT_ITEM_COUNT = "itemCount"
internal const val OS_ACTIVITY_CARD_KEY_EXPORT_ITEMS = "items"

internal const val LEGACY_GOOGLE_SETTINGS_ACTIVITY_CLASS = "com.google.android.gms.app.settings.GoogleSettingsActivity"
internal const val DEPRECATED_BUILTIN_DEFAULT_APPS_CARD_ID = "builtin-settings-default-apps"
internal const val DEPRECATED_BUILTIN_APP_LANGUAGE_CARD_ID = "builtin-settings-app-language"
internal const val DEPRECATED_BUILTIN_RUNNING_SERVICES_CARD_ID = "builtin-settings-running-services"

internal val deprecatedBuiltInActivityCardIds =
    setOf(
        DEPRECATED_BUILTIN_DEFAULT_APPS_CARD_ID,
        DEPRECATED_BUILTIN_APP_LANGUAGE_CARD_ID,
        DEPRECATED_BUILTIN_RUNNING_SERVICES_CARD_ID,
    )
