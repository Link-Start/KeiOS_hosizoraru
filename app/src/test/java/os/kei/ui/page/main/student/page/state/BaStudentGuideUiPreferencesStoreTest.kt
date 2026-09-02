package os.kei.ui.page.main.student.page.state

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The guide remembers the shape it was left in, in its own file.
 *
 * It first shipped on `UiPrefs.isSidebarNavigationPreferred` — the pager's key — which made the choice
 * unusable in one direction: five sections a bar labels comfortably and six that it cannot are not the
 * same question, so a reader who converted the guide converted the pager with it. Moving it to the
 * guide's own store fixed that and, through the key-value seam, made it checkable without a device.
 */
class BaStudentGuideUiPreferencesStoreTest {
    @Test
    fun `the bottom bar is the shape a fresh install starts in`() {
        assertFalse(BaStudentGuideUiPreferencesStore.isSidebarPreferred(FakeKeyValueStore()))
    }

    @Test
    fun `converting is remembered, and converting back is too`() {
        val kv = FakeKeyValueStore()

        BaStudentGuideUiPreferencesStore.setSidebarPreferred(true, kv)
        assertTrue(BaStudentGuideUiPreferencesStore.isSidebarPreferred(kv))

        BaStudentGuideUiPreferencesStore.setSidebarPreferred(false, kv)
        assertFalse(BaStudentGuideUiPreferencesStore.isSidebarPreferred(kv))
    }

    @Test
    fun `the guide writes one key, and it is its own`() {
        val kv = FakeKeyValueStore()

        BaStudentGuideUiPreferencesStore.setSidebarPreferred(true, kv)

        assertEquals(1, kv.written.size, "one preference, one key: ${kv.written.keys}")
        // The pager reads "sidebar_navigation_preferred" out of the *app* preference file. This store has
        // its own MMKV id, so the same key name here is a different fact -- and nothing in this store may
        // reach for the app-wide one.
        assertEquals(setOf("sidebar_navigation_preferred"), kv.written.keys)
    }

    @Test
    fun `an unrelated key in the same file does not answer for the shape`() {
        val kv = FakeKeyValueStore(initial = mapOf("some_other_guide_preference" to true))

        assertFalse(
            BaStudentGuideUiPreferencesStore.isSidebarPreferred(kv),
            "the shape must come from its own key, not from whatever else the file holds",
        )
    }
}

private class FakeKeyValueStore(
    initial: Map<String, Boolean> = emptyMap(),
) : BaStudentGuideUiPreferencesKeyValueStore {
    private val values = initial.toMutableMap()
    val written = mutableMapOf<String, Boolean>()

    override fun decodeBool(
        key: String,
        defaultValue: Boolean,
    ): Boolean = values[key] ?: defaultValue

    override fun encode(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
        written[key] = value
    }
}
