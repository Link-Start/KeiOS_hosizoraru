package os.kei.ui.page.main.ba.support

import com.tencent.mmkv.MMKV

internal class MmkvBaSettingsKeyValueStore(
    private val store: MMKV,
) : BaIdKeyValueStore,
    BaNativeBgmMediaNotificationKeyValueStore {
    override fun decodeBool(
        key: String,
        defaultValue: Boolean,
    ): Boolean = store.decodeBool(key, defaultValue)

    override fun encode(
        key: String,
        value: Boolean,
    ) {
        store.encode(key, value)
    }

    override fun decodeString(
        key: String,
        defaultValue: String,
    ): String? = store.decodeString(key, defaultValue)

    override fun encode(
        key: String,
        value: String,
    ) {
        store.encode(key, value)
    }
}
