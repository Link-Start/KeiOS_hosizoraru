package os.kei.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.os.Parcel
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The HyperOS fair-memory contract, transcribed from *公平运行内存适配：开发者文档* and pinned here.
 *
 * These are not tests of the mechanism — only a HyperOS build can deliver the broadcast — they are tests of
 * the parts that would silently disagree with the system if someone edited them: the parcel field order in
 * the reply, and which bundle keys are read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ItgsaFairMemoryTest {
    @Test
    fun `the reply parcel carries notifyType notifyId result extra in that order`() {
        val data = Parcel.obtain()
        try {
            writeItgsaFairMemoryReply(
                data = data,
                notifyType = ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL,
                notifyId = 4321,
                result = ItgsaFairMemory.RESULT_HANDLED,
                extra = Bundle().apply { putString(ItgsaFairMemory.REPLY_KEY_MESSAGE, "freedKb=2048") },
            )
            // There is no AIDL to compile against, so this ordering IS the contract with the system side.
            data.setDataPosition(0)
            assertEquals(ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL, data.readInt())
            assertEquals(4321, data.readInt())
            assertEquals(ItgsaFairMemory.RESULT_HANDLED, data.readInt())
            val extra = data.readBundle(ItgsaFairMemoryTest::class.java.classLoader)
            assertEquals("freedKb=2048", extra?.getString(ItgsaFairMemory.REPLY_KEY_MESSAGE))
        } finally {
            data.recycle()
        }
    }

    @Test
    fun `a trim notification parses out of the two nested bundles`() {
        val parsed =
            parseItgsaFairMemoryNotification(
                action = ItgsaFairMemory.ACTION_TRIM,
                extras = notificationExtras(notifyType = ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL),
            )

        assertTrue(parsed != null)
        assertEquals(false, parsed.kill)
        assertEquals(ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL, parsed.notifyType)
        assertEquals(99, parsed.notifyId)
        assertEquals("Excessive PSS Usage", parsed.reason)
        assertEquals(600_000, parsed.pssKb)
        assertEquals(800_000, parsed.pssLimitKb)
        assertTrue(parsed.physicalMemoryException)
    }

    @Test
    fun `a kill notification is recognised by its action`() {
        val parsed =
            parseItgsaFairMemoryNotification(
                action = ItgsaFairMemory.ACTION_KILL,
                extras = notificationExtras(notifyType = ItgsaFairMemory.NOTIFY_TYPE_JAVA_HEAP),
            )

        assertTrue(parsed != null)
        assertTrue(parsed.kill)
        assertTrue(parsed.javaHeapException)
    }

    @Test
    fun `an unrelated broadcast is not a fair-memory notification`() {
        assertNull(
            parseItgsaFairMemoryNotification(
                action = "android.intent.action.BATTERY_LOW",
                extras = notificationExtras(),
            ),
        )
        // No `common` bundle means no notifyId and no callback, so there is nothing to answer.
        assertNull(
            parseItgsaFairMemoryNotification(
                action = ItgsaFairMemory.ACTION_TRIM,
                extras = Bundle(),
            ),
        )
    }

    /**
     * The documentation contradicts itself here: its field table names this key `heapSize`, its own example
     * code reads `heapAlloc`. Both spellings have to work, because there is no way to tell from the document
     * which one the shipped system puts in the bundle.
     */
    @Test
    fun `the java heap size is read under either documented spelling`() {
        val fromTable = Bundle().apply { putInt(ItgsaFairMemory.KEY_HEAP_SIZE, 111) }
        val fromSample = Bundle().apply { putInt(ItgsaFairMemory.KEY_HEAP_ALLOC, 222) }

        assertEquals(111, readHeapUsedKb(fromTable))
        assertEquals(222, readHeapUsedKb(fromSample))
        assertNull(readHeapUsedKb(Bundle()))
    }

    /** A missing measurement must not stop the release; the notification still means "free memory now". */
    @Test
    fun `a notification with no measurements still parses`() {
        val extras =
            Bundle().apply {
                putBundle(
                    ItgsaFairMemory.KEY_COMMON,
                    Bundle().apply {
                        putInt(ItgsaFairMemory.KEY_NOTIFY_TYPE, ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL)
                        putInt(ItgsaFairMemory.KEY_NOTIFY_ID, 7)
                    },
                )
            }

        val parsed = parseItgsaFairMemoryNotification(ItgsaFairMemory.ACTION_TRIM, extras)

        assertTrue(parsed != null)
        assertNull(parsed.pssKb)
        assertNull(parsed.usageFraction)
        assertEquals(AppMemoryReleaseLevel.Critical, releaseLevelFor(parsed))
    }

    /**
     * A physical-memory TRIM gets the same treatment as a KILL, and that is deliberate.
     *
     * The doc is explicit that on the physical-memory path the system **kills the process first and notifies
     * the user afterwards**, so there is no second warning to save a gentler response for. The Java-heap path
     * does warn the user instead of killing, so it keeps the on-screen images.
     */
    @Test
    fun `a physical memory trim releases as hard as a kill`() {
        fun notification(
            kill: Boolean,
            type: Int,
        ) = ItgsaFairMemoryNotification(
            kill = kill,
            notifyType = type,
            notifyId = 1,
            reason = "",
            heapUsedKb = null,
            heapCapacityKb = null,
            pssKb = null,
            pssLimitKb = null,
        )

        assertEquals(
            AppMemoryReleaseLevel.Critical,
            releaseLevelFor(notification(kill = false, type = ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL)),
        )
        assertEquals(
            AppMemoryReleaseLevel.Moderate,
            releaseLevelFor(notification(kill = false, type = ItgsaFairMemory.NOTIFY_TYPE_JAVA_HEAP)),
        )
        assertEquals(
            AppMemoryReleaseLevel.Critical,
            releaseLevelFor(notification(kill = true, type = ItgsaFairMemory.NOTIFY_TYPE_JAVA_HEAP)),
        )
    }

    @Test
    fun `usage fraction reads the pool that actually tripped`() {
        val heap =
            ItgsaFairMemoryNotification(
                kill = false,
                notifyType = ItgsaFairMemory.NOTIFY_TYPE_JAVA_HEAP,
                notifyId = 1,
                reason = "Excessive Java Heap Usage",
                heapUsedKb = 180_000,
                heapCapacityKb = 200_000,
                // Present but irrelevant: a heap exception must not be judged by the PSS pair.
                pssKb = 10,
                pssLimitKb = 1_000_000,
            )

        assertEquals(0.9f, heap.usageFraction!!, 1e-4f)
    }

    /**
     * The two `RUNNING_*` levels arrive while the app is **visible**. Emptying the decoded bitmaps of the
     * list the user is looking at would cause a re-decode they can see, so those are answered by having
     * bounded caches rather than by dropping them.
     */
    @Test
    fun `visible-process trim levels release nothing`() {
        assertNull(AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertNull(AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertNull(AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))

        assertEquals(
            AppMemoryReleaseLevel.Critical,
            AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
        assertEquals(
            AppMemoryReleaseLevel.Critical,
            AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
        )
        assertEquals(
            AppMemoryReleaseLevel.Moderate,
            AppMemoryRelease.levelForTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
        )
    }

    /**
     * The release path must never delete a disk cache.
     *
     * Both bitmap caches expose a `clear`/`clearAll` that evicts memory **and** `deleteRecursively()`s the
     * files. Calling one of those from a memory-pressure handler is wrong twice: disk is not the resource
     * under pressure, and deleting it turns a memory problem into a network one as soon as the user scrolls
     * back. Asserted on the source because the mistake is a one-word edit — passing a context where `null`
     * belongs.
     */
    @Test
    fun `release never touches disk caches`() {
        // Comments stripped first: this file's own notes explain what `clearAll(context)` and
        // `deleteRecursively()` would do wrong, and a naive text search finds that prose and fails on the
        // explanation rather than on the code. Only executable lines can violate the rule.
        val source = codeOnly(File(RELEASE_SOURCE).readText())
        val registrations = source.substringAfter("fun registerAppCaches()")

        assertTrue(
            "BaGuideCatalogIconCache.clear(context = null)" in registrations,
            "The icon cache must be cleared with a null context, which is its memory-only path",
        )
        assertTrue(
            "BaGuideImageCache.evictMemory()" in registrations,
            "The guide image cache must use its memory-only eviction",
        )
        assertTrue(
            "clearAll(" !in registrations,
            "clearAll deletes the disk cache; a memory-pressure release must not call it",
        )
        assertTrue(
            "deleteRecursively" !in source,
            "Nothing in the release path may delete files",
        )
    }

    private fun notificationExtras(notifyType: Int = ItgsaFairMemory.NOTIFY_TYPE_PHYSICAL): Bundle =
        Bundle().apply {
            putBundle(
                ItgsaFairMemory.KEY_COMMON,
                Bundle().apply {
                    putInt(ItgsaFairMemory.KEY_NOTIFY_TYPE, notifyType)
                    putInt(ItgsaFairMemory.KEY_NOTIFY_ID, 99)
                    putString(ItgsaFairMemory.KEY_REASON, "Excessive PSS Usage")
                    putString(ItgsaFairMemory.KEY_ACTION, "trim")
                },
            )
            putBundle(
                ItgsaFairMemory.KEY_EXTRA,
                Bundle().apply {
                    putInt(ItgsaFairMemory.KEY_PSS, 600_000)
                    putInt(ItgsaFairMemory.KEY_PSS_LIMIT, 800_000)
                    putInt(ItgsaFairMemory.KEY_HEAP_SIZE, 120_000)
                    putInt(ItgsaFairMemory.KEY_HEAP_CAPACITY, 256_000)
                },
            )
        }
}

private const val RELEASE_SOURCE = "src/main/java/os/kei/memory/AppMemoryRelease.kt"

// Drops line comments and block comments, leaving only lines that run. Written as a line comment on
// purpose: Kotlin nests block comments, so spelling the delimiters inside a KDoc opens a nested comment
// that swallows the rest of the file.
private fun codeOnly(source: String): String =
    source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .filterNot { line -> line.trimStart().startsWith("//") }
        .joinToString("\n")

/**
 * The rate limit that replaced the vendor gate.
 *
 * Registration used to be gated behind Xiaomi's system properties, which was wrong — `itgsa` is the 金标联盟's
 * namespace, not HyperOS's, so the gate declined to register on the other alliance members. Registration is now
 * unconditional, which means the receiver is exported on every device and anything local can trigger it, so the
 * defence is here instead.
 */
class ItgsaFairMemoryRateLimitTest {
    @Test
    fun `the first release of a session always runs`() {
        assertTrue(shouldRunItgsaRelease(nowMs = 10_000, lastReleaseAtMs = 0L, minIntervalMs = 4_000))
    }

    @Test
    fun `a second release inside the window is suppressed`() {
        assertTrue(
            shouldRunItgsaRelease(nowMs = 13_999, lastReleaseAtMs = 10_000, minIntervalMs = 4_000).not(),
        )
        assertTrue(shouldRunItgsaRelease(nowMs = 14_000, lastReleaseAtMs = 10_000, minIntervalMs = 4_000))
    }

    /**
     * A clock that moved backwards must not wedge the limit shut.
     *
     * `System.currentTimeMillis()` is not monotonic — an NTP correction can put "now" before the last release,
     * and treating that as "no time has passed" would suppress every release until the clock caught up, which
     * on the physical-memory path means being killed instead.
     */
    @Test
    fun `a backwards clock does not suppress the release`() {
        assertTrue(shouldRunItgsaRelease(nowMs = 5_000, lastReleaseAtMs = 10_000, minIntervalMs = 4_000))
    }

    /**
     * A KILL is never rate-limited: it is the last chance to save state and release before the process goes.
     *
     * Driven through the real limiter, inside its window, so the only thing that differs between the two
     * calls is the kill flag. The KILL must also leave the limiter untouched — not consulted, so it neither
     * blocks on its lock nor records itself as the last release.
     */
    @Test
    fun `a kill runs inside the rate-limit window and a trim does not`() {
        var consulted = 0
        val insideWindow = {
            consulted++
            shouldRunItgsaRelease(nowMs = 13_999, lastReleaseAtMs = 10_000, minIntervalMs = 4_000)
        }

        assertTrue(shouldRunItgsaRelease(kill = true, rateLimitAllows = insideWindow), "a KILL must skip the limit")
        assertEquals(0, consulted, "a KILL must not consult, and so not advance, the rate limit")

        assertFalse(shouldRunItgsaRelease(kill = false, rateLimitAllows = insideWindow), "a TRIM must be limited")
        assertEquals(1, consulted)
    }
}
