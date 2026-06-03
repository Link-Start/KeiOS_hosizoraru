package os.kei.core.background

import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubBackgroundScheduleSnapshot
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_HOURLY_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_STUDENT_REFRESH_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import os.kei.ui.page.main.ba.support.floorToHourMs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppBackgroundSchedulePolicyTest {
    @Test
    fun `github schedule cancels when no tracked item exists`() {
        val schedule = AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = 0,
            lastRefreshMs = 0L,
            refreshIntervalHours = 3,
            nowMs = NOW_MS
        )

        assertNull(schedule)
    }

    @Test
    fun `ba cafe ap threshold schedules hourly threshold crossing event`() {
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshot = BaPageSnapshot(
                cafeApNotifyEnabled = true,
                cafeStoredAp = 60.0,
                cafeLastHourMs = floorToHourMs(NOW_MS),
                cafeApNotifyThreshold = 120,
                cafeApLastNotifiedLevel = -1,
                cafeLevel = 10
            ),
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(
            floorToHourMs(NOW_MS) + 2L * BA_CAFE_HOURLY_INTERVAL_MS,
            schedule.triggerAtMillis
        )
        assertEquals(BackgroundAlarmWorkload.UserReminder, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `ba cafe ap above threshold prompts when level has not been notified`() {
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshot = BaPageSnapshot(
                cafeApNotifyEnabled = true,
                cafeStoredAp = 130.0,
                cafeLastHourMs = floorToHourMs(NOW_MS),
                cafeApNotifyThreshold = 120,
                cafeApLastNotifiedLevel = 120,
                cafeLevel = 10
            ),
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmPrecision.Prompt, schedule.precision)
    }

    @Test
    fun `github first refresh uses short startup delay`() {
        val schedule = AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = 2,
            lastRefreshMs = 0L,
            refreshIntervalHours = 3,
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS + 2L * 60L * 1000L, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `github overdue refresh becomes prompt routine work`() {
        val schedule = AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = 1,
            lastRefreshMs = NOW_MS - 2L * 60L * 60L * 1000L,
            refreshIntervalHours = 1,
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS + AppBackgroundSchedulePolicy.MIN_ALARM_DELAY_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Prompt, schedule.precision)
    }

    @Test
    fun `github actions due earlier than release refresh schedules earlier`() {
        val actionsDueAtMs = NOW_MS + 30L * 60L * 1000L
        val schedule = AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = 1,
            lastRefreshMs = NOW_MS,
            refreshIntervalHours = 3,
            nextActionsUpdateDueAtMs = actionsDueAtMs,
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(actionsDueAtMs, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `github scheduler uses actions cache timestamp when actions tracking is enabled`() {
        val item = trackedApp(checkActionsUpdates = true)
        val schedule = AppBackgroundScheduler.buildGitHubRefreshSchedule(
            scheduleSnapshot =
                GitHubBackgroundScheduleSnapshot(
                    trackSnapshot =
                        GitHubTrackSnapshot(
                            items = listOf(item),
                            lastRefreshMs = NOW_MS,
                            refreshIntervalHours = 12,
                        ),
                    actionsRecommendedRunsByTrackId =
                        mapOf(
                            item.id to actionsRecommendedRun(
                                trackId = item.id,
                                checkedAtMillis = NOW_MS - 30L * 60L * 1000L,
                            ),
                        ),
                ),
            nowMs = NOW_MS,
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS + 30L * 60L * 1000L, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `github scheduler ignores actions cache when actions tracking is disabled`() {
        val item = trackedApp(checkActionsUpdates = false)
        val schedule = AppBackgroundScheduler.buildGitHubRefreshSchedule(
            scheduleSnapshot =
                GitHubBackgroundScheduleSnapshot(
                    trackSnapshot =
                        GitHubTrackSnapshot(
                            items = listOf(item),
                            lastRefreshMs = NOW_MS,
                            refreshIntervalHours = 12,
                        ),
                    actionsRecommendedRunsByTrackId =
                        mapOf(
                            item.id to actionsRecommendedRun(
                                trackId = item.id,
                                checkedAtMillis = NOW_MS - 30L * 60L * 1000L,
                            ),
                        ),
                ),
            nowMs = NOW_MS,
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS + 12L * 60L * 60L * 1000L, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `github tracked item due earlier than global refresh schedules earlier`() {
        val itemDueAtMs = NOW_MS + 60L * 60L * 1000L
        val schedule = AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = 1,
            lastRefreshMs = NOW_MS,
            refreshIntervalHours = 6,
            nextTrackedUpdateDueAtMs = itemDueAtMs,
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(itemDueAtMs, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.RoutineSync, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `ba ap threshold schedules exact threshold crossing event`() {
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshot = BaPageSnapshot(
                apNotifyEnabled = true,
                apCurrent = 118.0,
                apRegenBaseMs = NOW_MS,
                apNotifyThreshold = 120,
                apLimit = 240,
                apLastNotifiedLevel = -1
            ),
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS + 2L * BA_AP_REGEN_INTERVAL_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmWorkload.UserReminder, schedule.workload)
        assertEquals(BackgroundAlarmPrecision.Windowed, schedule.precision)
    }

    @Test
    fun `ba ap already above threshold prompts when level has not been notified`() {
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshot = BaPageSnapshot(
                apNotifyEnabled = true,
                apCurrent = 121.0,
                apRegenBaseMs = NOW_MS,
                apNotifyThreshold = 120,
                apLimit = 240,
                apLastNotifiedLevel = 120
            ),
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmPrecision.Prompt, schedule.precision)
    }

    @Test
    fun `ba cafe visit prompts when stored slot is stale`() {
        val currentSlot = currentCafeStudentRefreshSlotMs(
            nowMs = NOW_MS,
            serverIndex = 2
        )
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshot = BaPageSnapshot(
                cafeVisitNotifyEnabled = true,
                cafeVisitLastNotifiedSlotMs = currentSlot - BA_CAFE_STUDENT_REFRESH_INTERVAL_MS,
                serverIndex = 2
            ),
            nowMs = NOW_MS
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmPrecision.Prompt, schedule.precision)
    }

    @Test
    fun `ba multi account schedule picks earliest account reminder`() {
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshots =
                listOf(
                    BaPageSnapshot(
                        apNotifyEnabled = true,
                        apCurrent = 118.0,
                        apRegenBaseMs = NOW_MS,
                        apNotifyThreshold = 120,
                        apLimit = 240,
                        apLastNotifiedLevel = -1,
                    ),
                    BaPageSnapshot(
                        cafeApNotifyEnabled = true,
                        cafeStoredAp = 130.0,
                        cafeLastHourMs = floorToHourMs(NOW_MS),
                        cafeApNotifyThreshold = 120,
                        cafeApLastNotifiedLevel = 120,
                        cafeLevel = 10,
                    ),
                ),
            nowMs = NOW_MS,
        )

        assertNotNull(schedule)
        assertEquals(NOW_MS, schedule.triggerAtMillis)
        assertEquals(BackgroundAlarmPrecision.Prompt, schedule.precision)
    }

    @Test
    fun `ba reminder enabled predicate scans every account snapshot`() {
        assertFalse(AppBackgroundSchedulePolicy.hasEnabledBaReminder(emptyList()))
        assertFalse(AppBackgroundSchedulePolicy.hasEnabledBaReminder(listOf(BaPageSnapshot())))
        assertTrue(
            AppBackgroundSchedulePolicy.hasEnabledBaReminder(
                listOf(
                    BaPageSnapshot(),
                    BaPageSnapshot(arenaRefreshNotifyEnabled = true),
                ),
            ),
        )
    }

    @Test
    fun `android17 alarm window is wider for long delays`() {
        assertEquals(
            10L * 60L * 1000L,
            AppBackgroundSchedulePolicy.android17WindowLength(
                triggerAtMillis = NOW_MS + 30L * 60L * 1000L,
                nowMs = NOW_MS
            )
        )
        assertEquals(
            30L * 60L * 1000L,
            AppBackgroundSchedulePolicy.android17WindowLength(
                triggerAtMillis = NOW_MS + 2L * 60L * 60L * 1000L,
                nowMs = NOW_MS
            )
        )
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L

        private fun trackedApp(checkActionsUpdates: Boolean): GitHubTrackedApp =
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/repo",
                owner = "owner",
                repo = "repo",
                packageName = "os.kei.test",
                appLabel = "Test",
                checkActionsUpdates = checkActionsUpdates,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Hour1,
            )

        private fun actionsRecommendedRun(
            trackId: String,
            checkedAtMillis: Long,
        ): GitHubActionsRecommendedRunSnapshot =
            GitHubActionsRecommendedRunSnapshot(
                trackId = trackId,
                owner = "owner",
                repo = "repo",
                appLabel = "Test",
                workflowId = 1L,
                workflowName = "Build",
                workflowPath = ".github/workflows/build.yml",
                runId = 100L,
                runNumber = 10L,
                runAttempt = 1,
                runDisplayName = "Build",
                headBranch = "main",
                headSha = "abc",
                event = "push",
                status = "completed",
                conclusion = "success",
                htmlUrl = "https://github.com/owner/repo/actions/runs/100",
                artifactCount = 1,
                androidArtifactCount = 1,
                createdAtMillis = checkedAtMillis - 60_000L,
                updatedAtMillis = checkedAtMillis - 30_000L,
                checkedAtMillis = checkedAtMillis,
            )
    }
}
