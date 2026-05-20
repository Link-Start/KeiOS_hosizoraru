package os.kei.core.prefs

import com.tencent.mmkv.MMKV

object KeiMmkv {
    private val lock = Any()
    private val stores = linkedMapOf<Key, MMKV>()

    fun byId(id: String): MMKV {
        return byId(key = Key(id = id, mode = null)) {
            MMKV.mmkvWithID(id)
        }
    }

    fun byId(id: String, mode: Int): MMKV {
        return byId(key = Key(id = id, mode = mode)) {
            MMKV.mmkvWithID(id, mode)
        }
    }

    private fun byId(key: Key, factory: () -> MMKV): MMKV {
        return synchronized(lock) {
            stores[key] ?: factory().also { stores[key] = it }
        }
    }

    private data class Key(
        val id: String,
        val mode: Int?
    )
}
