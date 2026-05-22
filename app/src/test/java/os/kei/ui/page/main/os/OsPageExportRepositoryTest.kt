package os.kei.ui.page.main.os

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class OsPageExportRepositoryTest {
    @Test
    fun `section card export builds json off caller thread`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = OsPageExportRepository(defaultDispatcher = Dispatchers.Unconfined)
        val document =
            repository.buildSectionCardExport(
                OsPageSectionCardExportRequest(
                    card = OsSectionCard.SYSTEM,
                    sectionStates =
                        mapOf(
                            SectionKind.SYSTEM to
                                SectionState(
                                    rows =
                                        listOf(
                                            InfoRow("device.model", "demo"),
                                            InfoRow("top.info.filtered", "removed"),
                                        ),
                                ),
                        ),
                    activityShortcutCards = emptyList(),
                    googleSystemServiceDefaults = OsGoogleSystemServiceConfig(),
                    context = context,
                    shizukuStatus = "granted",
                ),
            )

        val json = JSONObject(document.content)

        assertTrue(document.fileName.startsWith("keios-os-system-table-"))
        assertEquals("keios.os.card.v1", json.optString("schema"))
        assertEquals(context.getString(OsSectionCard.SYSTEM.titleRes), json.optString("cardTitle"))
        assertEquals("granted", json.optString("shizukuStatus"))
        assertTrue(assertNotNull(json.optJSONArray("rows")).length() >= 1)
    }

    @Test
    fun `google service export uses activity card config`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val repository = OsPageExportRepository(defaultDispatcher = Dispatchers.Unconfined)
        val document =
            repository.buildSectionCardExport(
                OsPageSectionCardExportRequest(
                    card = OsSectionCard.GOOGLE_SYSTEM_SERVICE,
                    sectionStates = emptyMap(),
                    activityShortcutCards =
                        listOf(
                            OsActivityShortcutCard(
                                id = "activity-1",
                                config =
                                    defaults.copy(
                                        appName = "Settings",
                                        packageName = "com.android.settings",
                                        className = "SettingsActivity",
                                    ),
                            ),
                        ),
                    googleSystemServiceDefaults = defaults,
                    context = context,
                    shizukuStatus = "granted",
                ),
            )
        val rows = assertNotNull(JSONObject(document.content).optJSONArray("rows")).toString()

        assertTrue(rows.contains("com.android.settings"))
        assertTrue(rows.contains("SettingsActivity"))
    }
}
