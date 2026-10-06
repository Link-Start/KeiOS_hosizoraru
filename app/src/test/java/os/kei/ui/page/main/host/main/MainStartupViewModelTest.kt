package os.kei.ui.page.main.host.main

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeOverviewSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class MainStartupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ready = MainStartupSnapshot(
        preferences = UiPrefs.defaultSnapshot().copy(
            appThemeMode = AppThemeMode.DARK,
            visibleBottomPageNames = setOf("Home", "Ba"),
        ),
        homeOverview = HomeOverviewSnapshot(appOverview = HomeAppOverview("1.16.0", 11600999, true)),
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `no placeholder Home snapshot is emitted while storage is pending`() = runTest {
        val pending = CompletableDeferred<MainStartupSnapshot>()
        val model = MainStartupViewModel(load = { pending.await() }, fallback = { error("Unexpected fallback") })
        runCurrent()
        assertNull(model.snapshot.value)
        pending.complete(ready)
        advanceUntilIdle()
        assertSame(ready, model.snapshot.value)
        assertEquals(setOf("Home", "Ba"), MainScreenPrefsViewModel(ready.preferences).snapshot.value.visibleBottomPageNames)
    }

    @Test fun `storage failure releases launch with the supplied recovery snapshot`() = runTest {
        val model = MainStartupViewModel(load = { error("Unreadable storage") }, fallback = { ready })
        advanceUntilIdle()
        assertSame(ready, model.snapshot.value)
    }

    @Test fun `cancelling startup never publishes fallback defaults`() = runTest {
        val model = MainStartupViewModel(
            load = { throw CancellationException("Activity store cleared") },
            fallback = { error("Cancellation must not become a fallback") },
        )
        advanceUntilIdle()
        assertNull(model.snapshot.value)
    }

    @Test fun `recreating the activity reuses the completed local snapshot`() = runTest {
        var reads = 0
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainStartupViewModel(load = { reads++; ready }, fallback = { error("Unexpected fallback") }) as T
        }
        val first = ViewModelProvider(store, factory)[MainStartupViewModel::class.java]
        advanceUntilIdle()
        val recreated = ViewModelProvider(store, factory)[MainStartupViewModel::class.java]
        assertSame(first, recreated)
        assertSame(ready, recreated.snapshot.value)
        assertEquals(1, reads)
        store.clear()
    }
}
