package os.kei.feature.github.data.local

import android.os.BadParcelableException
import android.os.DeadObjectException
import android.os.TransactionTooLargeException
import org.junit.Test
import os.kei.core.system.isPackageManagerBulkQueryFailure
import os.kei.feature.github.model.InstalledAppItem
import kotlin.test.assertEquals

class GitHubInstalledAppRepositoryTest {
    @Test
    fun `installed package query tells binder parcel failures from ordinary ones`() {
        val rows = listOf(
            "short parcel" to (BadParcelableException("short package list") to true),
            "dead binder" to (DeadObjectException() to true),
            "transaction too large" to (TransactionTooLargeException() to true),
            "wrapped parcel failure" to
                (IllegalStateException("package manager failed", BadParcelableException("partial list")) to true),
            "ordinary failure" to (IllegalArgumentException("bad package flag") to false),
        )

        rows.forEach { (label, row) ->
            assertEquals(row.second, row.first.isPackageManagerBulkQueryFailure(), label)
        }
    }

    @Test
    fun `installed app scan scope keeps user apps and pinned system exceptions`() {
        val userApp = InstalledAppItem(
            label = "User",
            packageName = "com.example.user",
            isSystemApp = false,
        )
        val systemApp = InstalledAppItem(
            label = "System",
            packageName = "com.example.system",
            isSystemApp = true,
        )
        val pinnedSystemApp = InstalledAppItem(
            label = "Pinned",
            packageName = "com.example.pinned",
            isSystemApp = true,
        )

        val filtered = GitHubInstalledAppRepository.filterByScanScope(
            apps = listOf(userApp, systemApp, pinnedSystemApp),
            includeSystemApps = false,
            pinnedSystemPackageNames = setOf("COM.EXAMPLE.PINNED"),
        )

        assertEquals(listOf(userApp, pinnedSystemApp), filtered)
    }
}
