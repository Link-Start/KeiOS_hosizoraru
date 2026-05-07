package os.kei.ui.page.main.ba.support

internal const val BA_NATIVE_BGM_MEDIA_NOTIFICATION_KEY =
    "native_bgm_media_notification_enabled"

internal interface BaNativeBgmMediaNotificationKeyValueStore {
    fun decodeBool(key: String, defaultValue: Boolean): Boolean
    fun encode(key: String, value: Boolean)
}

internal class BaNativeBgmMediaNotificationPrefs(
    private val store: BaNativeBgmMediaNotificationKeyValueStore
) {
    fun loadEnabled(): Boolean {
        return store.decodeBool(BA_NATIVE_BGM_MEDIA_NOTIFICATION_KEY, false)
    }

    fun saveEnabled(enabled: Boolean) {
        store.encode(BA_NATIVE_BGM_MEDIA_NOTIFICATION_KEY, enabled)
    }
}
