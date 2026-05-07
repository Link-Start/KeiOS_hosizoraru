package os.kei.ui.page.main.student

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaGuideBgmMediaNotificationProviderFactoryTest {
    @Test
    fun `AOSP devices use monochrome media small icon`() {
        val iconRes = BaGuideBgmMediaOemCompat.mediaSmallIconRes(
            BaGuideBgmMediaOemCompat.DeviceSignals(
                brand = "google",
                manufacturer = "Google",
                display = "BP2A",
                model = "Pixel 10 Pro"
            )
        )

        assertEquals(R.drawable.ic_launcher_monochrome, iconRes)
    }

    @Test
    fun `Xiaomi family devices use colored KeiOS media small icon`() {
        val hyperOsIcon = BaGuideBgmMediaOemCompat.mediaSmallIconRes(
            BaGuideBgmMediaOemCompat.DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "OS2.0.1",
                model = "Xiaomi 17 Pro",
                properties = mapOf("ro.mi.os.version.name" to "OS2")
            )
        )
        val miuiIcon = BaGuideBgmMediaOemCompat.mediaSmallIconRes(
            BaGuideBgmMediaOemCompat.DeviceSignals(
                brand = "Redmi",
                manufacturer = "Xiaomi",
                display = "V816",
                model = "Redmi",
                properties = mapOf("ro.miui.ui.version.name" to "V816")
            )
        )

        assertEquals(R.drawable.ic_kei_logo_notification, hyperOsIcon)
        assertEquals(R.drawable.ic_kei_logo_notification, miuiIcon)
    }

    @Test
    fun `provider uses the default Media3 notification implementation`() {
        val provider = BaGuideBgmMediaNotificationProviderFactory.create(
            ApplicationProvider.getApplicationContext()
        )

        assertNotNull(provider)
    }
}
