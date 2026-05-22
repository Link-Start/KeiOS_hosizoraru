package os.kei.ui.page.main.os.shell

import com.tencent.mmkv.MMKV
import org.json.JSONArray

internal object OsShellCommandBuiltInMerge {
    fun appendMissingBuiltInShellCommandCards(
        cards: List<OsShellCommandCard>,
        builtInShellCommandCards: List<OsShellCommandCard>,
        store: MMKV,
        persistCards: (List<OsShellCommandCard>) -> Unit,
    ): List<OsShellCommandCard> {
        val builtIns = builtInShellCommandCards.mapNotNull(OsShellCommandCardCodec::normalizeCard)
        if (builtIns.isEmpty()) return cards
        val deletedBuiltInIds = readDeletedBuiltInShellCardIds(store)
        val merged = cards.toMutableList()
        builtIns.forEach { builtIn ->
            val alreadyPresent =
                merged.any { card ->
                    card.id == builtIn.id ||
                        OsShellCommandCardCodec.mergeKeyFor(card) == OsShellCommandCardCodec.mergeKeyFor(builtIn)
                }
            if (!alreadyPresent && builtIn.id !in deletedBuiltInIds) {
                merged += builtIn
            }
        }
        val normalized = merged.mapNotNull(OsShellCommandCardCodec::normalizeCard)
        if (normalized != cards) {
            persistCards(normalized)
        }
        return normalized
    }

    fun readDeletedBuiltInShellCardIds(store: MMKV): Set<String> {
        val raw = store.decodeString(OS_SHELL_COMMAND_CARD_KEY_DELETED_BUILT_INS).orEmpty().trim()
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val id = array.optString(index).trim()
                    if (id in BUILT_IN_SHELL_COMMAND_CARD_IDS) add(id)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun persistDeletedBuiltInShellCardIds(
        store: MMKV,
        ids: Set<String>,
    ) {
        val normalized = ids.filter { id -> id in BUILT_IN_SHELL_COMMAND_CARD_IDS }.sorted()
        if (normalized.isEmpty()) {
            store.removeValueForKey(OS_SHELL_COMMAND_CARD_KEY_DELETED_BUILT_INS)
            return
        }
        val array = JSONArray()
        normalized.forEach(array::put)
        store.encode(OS_SHELL_COMMAND_CARD_KEY_DELETED_BUILT_INS, array.toString())
    }
}
