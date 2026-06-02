package os.kei.ui.page.main.ba.support

internal class InMemoryBaAccountKeyValueStore : BaAccountKeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun decodeBool(
        key: String,
        defaultValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defaultValue

    override fun encode(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override fun decodeString(
        key: String,
        defaultValue: String,
    ): String? = values[key] as? String ?: defaultValue

    override fun encode(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun decodeInt(
        key: String,
        defaultValue: Int,
    ): Int = values[key] as? Int ?: defaultValue

    override fun encode(
        key: String,
        value: Int,
    ) {
        values[key] = value
    }

    override fun decodeLong(
        key: String,
        defaultValue: Long,
    ): Long = values[key] as? Long ?: defaultValue

    override fun encode(
        key: String,
        value: Long,
    ) {
        values[key] = value
    }

    override fun containsKey(key: String): Boolean = values.containsKey(key)

    override fun removeValueForKey(key: String) {
        values.remove(key)
    }
}
