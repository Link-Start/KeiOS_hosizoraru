package os.kei.ui.page.main.ba.support

internal const val BA_SETTINGS_KV_ID = "ba_page_settings"
internal const val KEY_SERVER_INDEX = "server_index"
internal const val KEY_CALENDAR_POOL_SERVER_INDEX = "calendar_pool_server_index"
internal const val KEY_CAFE_LEVEL = "cafe_level"
internal const val KEY_CAFE_STORED_AP = "cafe_stored_ap"
internal const val KEY_CAFE_LAST_HOUR_MS = "cafe_last_hour_ms"
internal const val KEY_CAFE_AP_NOTIFY_ENABLED = "cafe_ap_notify_enabled"
internal const val KEY_CAFE_AP_NOTIFY_THRESHOLD = "cafe_ap_notify_threshold"
internal const val KEY_CAFE_AP_LAST_NOTIFIED_LEVEL = "cafe_ap_last_notified_level"
internal const val KEY_AP_LIMIT = "ap_limit"
internal const val KEY_AP_NOTIFY_ENABLED = "ap_notify_enabled"
internal const val KEY_AP_NOTIFY_THRESHOLD = "ap_notify_threshold"
internal const val KEY_AP_LAST_NOTIFIED_LEVEL = "ap_last_notified_level"
internal const val KEY_ARENA_REFRESH_NOTIFY_ENABLED = "arena_refresh_notify_enabled"
internal const val KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS = "arena_refresh_last_notified_slot_ms"
internal const val KEY_CAFE_VISIT_NOTIFY_ENABLED = "cafe_visit_notify_enabled"
internal const val KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS = "cafe_visit_last_notified_slot_ms"
internal const val KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED = "calendar_upcoming_notify_enabled"
internal const val KEY_CALENDAR_ENDING_NOTIFY_ENABLED = "calendar_ending_notify_enabled"
internal const val KEY_POOL_UPCOMING_NOTIFY_ENABLED = "pool_upcoming_notify_enabled"
internal const val KEY_POOL_ENDING_NOTIFY_ENABLED = "pool_ending_notify_enabled"
internal const val KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED = "calendar_pool_change_notify_enabled"
internal const val KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS = "calendar_pool_notify_lead_hours"
internal const val KEY_CALENDAR_POOL_NOTIFIED_KEYS = "calendar_pool_notified_keys"
internal const val KEY_AP_CURRENT = "ap_current"
internal const val KEY_AP_CURRENT_EXACT = "ap_current_exact"
internal const val KEY_AP_REGEN_BASE_MS = "ap_regen_base_ms"
internal const val KEY_AP_SYNC_MS = "ap_sync_ms"
internal const val KEY_POOL_SHOW_ENDED = "pool_show_ended"
internal const val KEY_ACTIVITY_SHOW_ENDED = "activity_show_ended"
internal const val KEY_SHOW_CALENDAR_POOL_IMAGES = "show_calendar_pool_images"
internal const val KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED = "media_adaptive_rotation_enabled"
internal const val KEY_MEDIA_SAVE_CUSTOM_ENABLED = "media_save_custom_enabled"
internal const val KEY_MEDIA_SAVE_FIXED_TREE_URI = "media_save_fixed_tree_uri"
internal const val KEY_COFFEE_HEADPAT_MS = "coffee_headpat_ms"
internal const val KEY_COFFEE_INVITE1_USED_MS = "coffee_invite1_used_ms"
internal const val KEY_COFFEE_INVITE2_USED_MS = "coffee_invite2_used_ms"
internal const val KEY_LIST_SCROLL_INDEX = "list_scroll_index"
internal const val KEY_LIST_SCROLL_OFFSET = "list_scroll_offset"
internal const val KEY_CALENDAR_REFRESH_INTERVAL_HOURS = "calendar_refresh_interval_hours"

internal const val DEFAULT_SERVER_INDEX = 2
internal const val DEFAULT_CAFE_LEVEL = 10
internal const val DEFAULT_CAFE_STORED_AP = 0.0
internal const val DEFAULT_CAFE_AP_NOTIFY_THRESHOLD = 120
internal const val DEFAULT_AP_LIMIT = BA_AP_LIMIT_MAX
internal const val DEFAULT_AP_NOTIFY_THRESHOLD = 120
internal const val DEFAULT_AP_CURRENT = 0.0
internal const val DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS = 12

private const val KEY_CALENDAR_CACHE_PREFIX = "calendar_cache_"
private const val KEY_CALENDAR_SYNC_PREFIX = "calendar_sync_"
private const val KEY_CALENDAR_CACHE_VERSION_PREFIX = "calendar_cache_version_"
private const val KEY_POOL_CACHE_PREFIX = "pool_cache_"
private const val KEY_POOL_SYNC_PREFIX = "pool_sync_"
private const val KEY_POOL_CACHE_VERSION_PREFIX = "pool_cache_version_"

internal fun calendarCacheKey(serverIndex: Int): String = "$KEY_CALENDAR_CACHE_PREFIX${serverIndex.coerceIn(0, 2)}"

internal fun calendarSyncKey(serverIndex: Int): String = "$KEY_CALENDAR_SYNC_PREFIX${serverIndex.coerceIn(0, 2)}"

internal fun calendarCacheVersionKey(serverIndex: Int): String = "$KEY_CALENDAR_CACHE_VERSION_PREFIX${serverIndex.coerceIn(0, 2)}"

internal fun poolCacheKey(serverIndex: Int): String = "$KEY_POOL_CACHE_PREFIX${serverIndex.coerceIn(0, 2)}"

internal fun poolSyncKey(serverIndex: Int): String = "$KEY_POOL_SYNC_PREFIX${serverIndex.coerceIn(0, 2)}"

internal fun poolCacheVersionKey(serverIndex: Int): String = "$KEY_POOL_CACHE_VERSION_PREFIX${serverIndex.coerceIn(0, 2)}"
