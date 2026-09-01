package os.kei.ui.page.main.ba

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaDailyDoneConfig
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dock's daily-done entry point, which is the one trigger that runs the template while the page that
 * shows its result is on screen. Everything checked here is about that overlap.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BaDailyDoneSheetLifecycleTest {
    @Test
    fun `opening the sheet re-reads the template every time`() =
        runViewModelTest { viewModel, repository ->
            repository.dailyDoneConfig = BaDailyDoneConfig(apRemaining = 37)

            viewModel.showDailyDoneSheet()
            advanceUntilIdle()

            // Loaded before the sheet is shown, not after: a sheet that appears first would draw one
            // frame of compiled-in defaults and then snap to the teacher's numbers.
            val opened = viewModel.chromeUiState.value.dailyDoneSheet
            assertTrue(opened.show)
            assertEquals(37, opened.config.apRemaining)

            viewModel.hideDailyDoneSheet()
            assertFalse(viewModel.chromeUiState.value.dailyDoneSheet.show)

            // The template is global — a tile long-press or a WebDAV merge can rewrite it while this
            // page stays up — so a reopen must not serve what the last opening read.
            repository.dailyDoneConfig = BaDailyDoneConfig(apRemaining = 120)
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()

            assertEquals(120, viewModel.chromeUiState.value.dailyDoneSheet.config.apRemaining)
        }

    @Test
    fun `applying flushes the page's pending tick, then saves, runs and re-reads`() =
        runViewModelTest { viewModel, repository ->
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()
            repository.log.clear()

            viewModel.applyDailyDoneTemplate(
                config = BaDailyDoneConfig(apRemaining = 12),
                currentRuntimeUpdate = BaRuntimePersistenceUpdate(apCurrent = 88.0),
            )
            advanceUntilIdle()

            // The flush has to land first or the run plans against a staler AP pool than the one on
            // screen; the reload has to follow the run or the office keeps the pre-run values and the
            // next runtime tick writes them back over the template. The trailing flush is the reload's
            // own tick, persisted once the office has been re-seeded from what the run wrote.
            assertEquals(listOf("flush", "save", "apply", "reload", "flush"), repository.log)
            assertEquals(12, repository.savedDailyDoneConfig?.apRemaining)
        }

    @Test
    fun `the sheet closes and stops reporting itself busy once the run finishes`() =
        runViewModelTest { viewModel, _ ->
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()

            viewModel.applyDailyDoneTemplate(
                config = BaDailyDoneConfig(),
                currentRuntimeUpdate = null,
            )
            advanceUntilIdle()

            val settled = viewModel.chromeUiState.value.dailyDoneSheet
            assertFalse(settled.show)
            assertFalse(settled.applying)
        }

    @Test
    fun `a second apply is dropped while the first is still running`() {
        val gate = CompletableDeferred<Unit>()
        runViewModelTest(
            applyDailyDone = { gate.await() },
        ) { viewModel, repository ->
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()
            repository.log.clear()

            viewModel.applyDailyDoneTemplate(BaDailyDoneConfig(apRemaining = 1), null)
            advanceUntilIdle()
            assertTrue(viewModel.chromeUiState.value.dailyDoneSheet.applying)

            // The sheet disables its own apply button while a run is in flight, but the guard has to
            // hold in the view model too: the run outlives the sheet on purpose.
            viewModel.applyDailyDoneTemplate(BaDailyDoneConfig(apRemaining = 2), null)
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("save", "apply", "reload", "flush"), repository.log)
            assertEquals(1, repository.savedDailyDoneConfig?.apRemaining)
        }
    }

    @Test
    fun `a failed run leaves the sheet open to retry from`() =
        runViewModelTest(
            applyDailyDone = { error("store unavailable") },
        ) { viewModel, _ ->
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()

            viewModel.applyDailyDoneTemplate(BaDailyDoneConfig(apRemaining = 9), null)
            advanceUntilIdle()

            // Closing under the error toast would take the teacher's edits with it, and the stored
            // template is untouched by a failed run, so there is something to retry from.
            val settled = viewModel.chromeUiState.value.dailyDoneSheet
            assertTrue(settled.show)
            assertFalse(settled.applying)
        }

    @Test
    fun `saving records the template without running it`() =
        runViewModelTest { viewModel, repository ->
            viewModel.showDailyDoneSheet()
            advanceUntilIdle()
            repository.log.clear()

            viewModel.saveDailyDoneTemplate(BaDailyDoneConfig(apRemaining = 5))
            advanceUntilIdle()

            assertEquals(listOf("save"), repository.log)
            assertEquals(5, repository.savedDailyDoneConfig?.apRemaining)
            assertFalse(viewModel.chromeUiState.value.dailyDoneSheet.show)
        }
}

private fun runViewModelTest(
    applyDailyDone: suspend (BaAccountId?) -> Unit = {},
    body: suspend kotlinx.coroutines.test.TestScope.(
        BaOfficeViewModel,
        RecordingDailyDoneRepository,
    ) -> Unit,
) = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
        val repository = RecordingDailyDoneRepository()
        val viewModel =
            BaOfficeViewModel.createForTest(
                application = ApplicationProvider.getApplicationContext<Application>(),
                repository = repository,
                persistRuntimeUpdate = { repository.log += "flush" },
                scheduleBaApThreshold = {},
                applyDailyDone = { accountId ->
                    repository.log += "apply"
                    applyDailyDone(accountId)
                },
            )
        advanceUntilIdle()
        repository.log.clear()
        body(viewModel, repository)
    } finally {
        Dispatchers.resetMain()
    }
}

private class RecordingDailyDoneRepository : BaOfficePageRepository() {
    val log = mutableListOf<String>()
    var dailyDoneConfig: BaDailyDoneConfig = BaDailyDoneConfig()
    var savedDailyDoneConfig: BaDailyDoneConfig? = null

    override suspend fun loadInitialSnapshot(): BaPageSnapshot {
        log += "reload"
        return BaPageSnapshot()
    }

    override suspend fun loadAccountState(): BaAccountStoreSnapshot =
        BaAccountStoreSnapshot(
            accounts = emptyList(),
            activeAccountId = null,
            allAccountsFollowGlobalNotificationSettings = true,
            globalReminderSettings = BaGlobalReminderSettings(),
        )

    override suspend fun loadDailyDoneConfig(): BaDailyDoneConfig = dailyDoneConfig

    override suspend fun saveDailyDoneConfig(config: BaDailyDoneConfig) {
        log += "save"
        savedDailyDoneConfig = config
        dailyDoneConfig = config
    }
}
