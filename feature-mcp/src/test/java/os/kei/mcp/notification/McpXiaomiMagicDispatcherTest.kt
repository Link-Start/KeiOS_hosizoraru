package os.kei.mcp.notification

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class McpXiaomiMagicDispatcherTest {
    @Test
    fun `cancellation before the initial post prevents delivery`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shouldExecuteStarted = CompletableDeferred<Unit>()
        val releaseShouldExecute = CompletableDeferred<Unit>()
        var postAttempts = 0
        var deliveryCommits = 0
        var restoreAttempts = 0
        val environment =
            McpXiaomiMagicDispatchEnvironment(
                canPostNotifications = { true },
                resolveTargetUid = { 12_345 },
                canUseCommand = { true },
                shouldExecute = {
                    shouldExecuteStarted.complete(Unit)
                    releaseShouldExecute.await()
                    true
                },
                healNetworking = {},
                blockNetworking = { true },
                postNotification = { _, _, _, _ ->
                    postAttempts += 1
                    true
                },
                awaitRestore = {},
                restoreNetworking = { restoreAttempts += 1 },
                needsRestore = { false },
            )

        val caller =
            backgroundScope.async {
                McpXiaomiMagicDispatcher.notify(
                    context = context,
                    notificationId = 40,
                    notification = Notification(),
                    environment = environment,
                    dispatchScope = backgroundScope,
                    mutex = Mutex(),
                    onDelivered = { deliveryCommits += 1 },
                )
            }

        shouldExecuteStarted.await()
        caller.cancelAndJoin()

        assertEquals(0, postAttempts)
        assertEquals(0, deliveryCommits)
        assertEquals(0, restoreAttempts)
    }

    @Test
    fun `successful post commits delivery and restores networking when caller is cancelled`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val deliveryStarted = CompletableDeferred<Unit>()
        val releaseDelivery = CompletableDeferred<Unit>()
        val restoreCompleted = CompletableDeferred<Unit>()
        var postAttempts = 0
        var callerJob: kotlinx.coroutines.Job? = null
        val environment =
            McpXiaomiMagicDispatchEnvironment(
                canPostNotifications = { true },
                resolveTargetUid = { 12_345 },
                canUseCommand = { true },
                shouldExecute = { true },
                healNetworking = {},
                blockNetworking = { true },
                postNotification = { _, _, _, _ ->
                    postAttempts += 1
                    callerJob?.cancel()
                    true
                },
                awaitRestore = {},
                restoreNetworking = { restoreCompleted.complete(Unit) },
                needsRestore = { true },
            )

        val caller =
            backgroundScope.launch {
                McpXiaomiMagicDispatcher.notify(
                    context = context,
                    notificationId = 41,
                    notification = Notification(),
                    environment = environment,
                    dispatchScope = backgroundScope,
                    mutex = Mutex(),
                    onDelivered = {
                        deliveryStarted.complete(Unit)
                        releaseDelivery.await()
                    },
                )
            }
        callerJob = caller

        deliveryStarted.await()
        runCurrent()
        assertFalse(caller.isCompleted)
        assertTrue(restoreCompleted.isCompleted)
        releaseDelivery.complete(Unit)
        caller.join()

        assertEquals(1, postAttempts)
        assertTrue(caller.isCancelled)
    }

    @Test
    fun `delivery commit failure fails the awaited transaction and restores networking`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val expectedFailure = IllegalStateException("durable commit failed")
        val restoreCompleted = CompletableDeferred<Unit>()
        val environment =
            McpXiaomiMagicDispatchEnvironment(
                canPostNotifications = { true },
                resolveTargetUid = { 12_345 },
                canUseCommand = { true },
                shouldExecute = { true },
                healNetworking = {},
                blockNetworking = { true },
                postNotification = { _, _, _, _ -> true },
                awaitRestore = {},
                restoreNetworking = { restoreCompleted.complete(Unit) },
                needsRestore = { true },
            )

        val actualFailure =
            assertFailsWith<IllegalStateException> {
                McpXiaomiMagicDispatcher.notify(
                    context = context,
                    notificationId = 44,
                    notification = Notification(),
                    environment = environment,
                    dispatchScope = backgroundScope,
                    mutex = Mutex(),
                    onDelivered = { throw expectedFailure },
                )
        }

        restoreCompleted.await()
        assertEquals(expectedFailure.message, actualFailure.message)
    }

    @Test
    fun `standalone delivery commit wraps failures for active-state recovery`() = runTest {
        val expectedFailure = IllegalStateException("snapshot or BA commit failed")
        var commitAttempts = 0

        val actualFailure =
            assertFailsWith<McpNotificationDeliveryCommitException> {
                runStandaloneEventDeliveryCommit {
                    commitAttempts += 1
                    throw expectedFailure
                }
            }

        assertEquals(expectedFailure.message, actualFailure.cause?.message)
        assertEquals(2, commitAttempts)
    }

    @Test
    fun `standalone delivery commit retries a slow callback once without republishing`() = runTest {
        var commitAttempts = 0

        runStandaloneEventDeliveryCommit {
            commitAttempts += 1
            if (commitAttempts == 1) {
                delay(1_300L)
                error("first commit attempt failed")
            }
        }

        assertEquals(2, commitAttempts)
    }

    @Test
    fun `standalone delivery commit bounds a stalled attempt before retry`() = runTest {
        var commitAttempts = 0

        runStandaloneEventDeliveryCommit {
            commitAttempts += 1
            if (commitAttempts == 1) delay(Long.MAX_VALUE)
        }

        assertEquals(2, commitAttempts)
    }

    @Test
    fun `notify returns false when primary and fallback posts fail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var postAttempts = 0
        val environment =
            McpXiaomiMagicDispatchEnvironment(
                canPostNotifications = { true },
                resolveTargetUid = { 12_345 },
                canUseCommand = { true },
                shouldExecute = { true },
                healNetworking = {},
                blockNetworking = { true },
                postNotification = { _, _, _, _ ->
                    postAttempts += 1
                    false
                },
                awaitRestore = {},
                restoreNetworking = {},
                needsRestore = { true },
            )

        val result =
            McpXiaomiMagicDispatcher.notify(
                context = context,
                notificationId = 42,
                notification = Notification(),
                environment = environment,
                dispatchScope = this,
                mutex = Mutex(),
            )

        assertFalse(result)
        assertEquals(2, postAttempts)
    }

    @Test
    fun `notify returns initial post result before delayed network restoration completes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val restoreGate = CompletableDeferred<Unit>()
        val restoreCompleted = CompletableDeferred<Unit>()
        var restored = false
        val environment =
            McpXiaomiMagicDispatchEnvironment(
                canPostNotifications = { true },
                resolveTargetUid = { 12_345 },
                canUseCommand = { true },
                shouldExecute = { true },
                healNetworking = {},
                blockNetworking = { true },
                postNotification = { _, _, _, _ -> true },
                awaitRestore = { restoreGate.await() },
                restoreNetworking = {
                    restored = true
                    restoreCompleted.complete(Unit)
                },
                needsRestore = { true },
            )

        val result =
            McpXiaomiMagicDispatcher.notify(
                context = context,
                notificationId = 43,
                notification = Notification(),
                environment = environment,
                dispatchScope = backgroundScope,
                mutex = Mutex(),
            )

        assertTrue(result)
        assertFalse(restored)
        restoreGate.complete(Unit)
        restoreCompleted.await()
        assertTrue(restored)
    }
}
