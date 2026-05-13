package os.kei.feature.github.model

import org.junit.Test
import os.kei.BuildConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackModelsTest {
    @Test
    fun `default kei os tracked app points at current app package and repo`() {
        val item = defaultKeiOsTrackedApp()

        assertEquals("https://github.com/hosizoraru/KeiOS", item.repoUrl)
        assertEquals("hosizoraru", item.owner)
        assertEquals("KeiOS", item.repo)
        assertEquals(BuildConfig.APPLICATION_ID, item.packageName)
        assertEquals("KeiOS", item.appLabel)
    }

    @Test
    fun `kei os self track badge matches current app package and repo`() {
        val item = defaultKeiOsTrackedApp()

        assertTrue(item.isKeiOsSelfTrack())
    }

    @Test
    fun `kei os actions lookup target uses release package name`() {
        val item = defaultKeiOsTrackedApp()
        val lookupItem = item.asKeiOsActionsRunLookupItem()

        assertEquals("hosizoraru", lookupItem.owner)
        assertEquals("KeiOS", lookupItem.repo)
        assertEquals(KEI_OS_RELEASE_PACKAGE_NAME, lookupItem.packageName)
        assertEquals("hosizoraru/KeiOS|os.kei", lookupItem.id)
        assertTrue(lookupItem.isKeiOsReleaseTrack())
    }

    @Test
    fun `direct apk identity preserves host and full path`() {
        val identity =
            buildDirectApkTrackIdentity("https://telegram.org/dl/android/apk-public-beta")

        assertEquals("telegram.org", identity?.owner)
        assertEquals("dl-android-apk-public-beta", identity?.repo)
        assertEquals("telegram.org/dl/android/apk-public-beta", identity?.displayName)
        assertEquals("apk-public-beta.apk", identity?.assetName)
    }

    @Test
    fun `direct apk track id keeps repository ids stable`() {
        val direct = GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk",
            owner = "telegram.org",
            repo = "dl-android-apk",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )
        val repository = GitHubTrackedApp(
            repoUrl = "https://github.com/telegram/telegram-android",
            owner = "telegram",
            repo = "telegram-android",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram"
        )

        assertEquals("direct_apk|telegram.org/dl-android-apk|org.telegram.messenger", direct.id)
        assertEquals("telegram/telegram-android|org.telegram.messenger", repository.id)
    }
}
