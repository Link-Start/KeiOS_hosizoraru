package os.kei.ui.page.main.student.page.state

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv

/**
 * The guide page's own view preferences.
 *
 * Not `UiPrefs`, which is the app's settings surface: everything in there is something the Settings page
 * shows and describes, and a shape one page was last left in is not that. It sits beside
 * `BaGuideCatalogUiPreferencesStore` instead — the catalog's equivalent — in its own MMKV file, so the
 * guide's answer and the pager's are separate facts rather than one key two pages fight over. Five
 * sections a bar labels comfortably and six that it cannot are not the same question.
 *
 * The key-value seam is the other reason to build it this way rather than reach for `UiPrefs`: it makes
 * the behaviour testable off-device, which a global MMKV singleton is not.
 */
internal object BaStudentGuideUiPreferencesStore {
    private const val KV_ID = "ba_student_guide_ui_preferences"
    private const val KEY_SIDEBAR_NAVIGATION = "sidebar_navigation_preferred"

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    /**
     * Whether this page was last left in its rail form.
     *
     * Defaults to the bottom bar, and deliberately does not inherit the pager's setting: a default that
     * changed because of an unrelated page would be a surprise, not a convenience.
     */
    fun isSidebarPreferred(
        keyValueStore: BaStudentGuideUiPreferencesKeyValueStore = mmkvStore(),
    ): Boolean = keyValueStore.decodeBool(KEY_SIDEBAR_NAVIGATION, false)

    fun setSidebarPreferred(
        value: Boolean,
        keyValueStore: BaStudentGuideUiPreferencesKeyValueStore = mmkvStore(),
    ) {
        keyValueStore.encode(KEY_SIDEBAR_NAVIGATION, value)
    }

    private fun mmkvStore(): BaStudentGuideUiPreferencesKeyValueStore =
        MmkvBaStudentGuideUiPreferencesKeyValueStore(store)
}

internal interface BaStudentGuideUiPreferencesKeyValueStore {
    fun decodeBool(
        key: String,
        defaultValue: Boolean,
    ): Boolean

    fun encode(
        key: String,
        value: Boolean,
    )
}

private class MmkvBaStudentGuideUiPreferencesKeyValueStore(
    private val kv: MMKV,
) : BaStudentGuideUiPreferencesKeyValueStore {
    override fun decodeBool(
        key: String,
        defaultValue: Boolean,
    ): Boolean = kv.decodeBool(key, defaultValue)

    override fun encode(
        key: String,
        value: Boolean,
    ) {
        kv.encode(key, value)
    }
}
