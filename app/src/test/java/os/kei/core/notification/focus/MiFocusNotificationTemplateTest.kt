package os.kei.core.notification.focus

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = MiFocusNotificationTemplateTestApp::class,
    sdk = [35]
)
class MiFocusNotificationTemplateTest {
    @Test
    fun `summary island facade emits every supported big and small template`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val now = 1778000000000L
        val cases = listOf(
            "textInfo" to MiFocusIslandBigTemplate.Text(MiFocusIslandText(title = "完成")),
            "picInfo" to MiFocusIslandBigTemplate.Picture(),
            "imageTextInfoLeft" to MiFocusIslandBigTemplate.ImageTextLeft(),
            "imageTextInfoRight" to MiFocusIslandBigTemplate.ImageTextRight(
                text = MiFocusIslandText(title = "失败")
            ),
            "progressTextInfo" to MiFocusIslandBigTemplate.ProgressText(
                text = MiFocusIslandText(title = "72%"),
                progress = MiFocusIslandProgress(72, "#2563EB", "#334155")
            ),
            "fixedWidthDigitInfo" to MiFocusIslandBigTemplate.FixedWidthDigit(
                digit = "128",
                content = "AP"
            ),
            "sameWidthDigitInfo" to MiFocusIslandBigTemplate.SameWidthDigit(
                content = "活动",
                timer = MiFocusTimer.countdown(deadlineAtMs = now + 60000L, nowMs = now)
            )
        )

        cases.forEach { (token, bigTemplate) ->
            val bundle = MiFocusNotificationTemplate.build(
                context = context,
                spec = baseSpec(
                    island = MiFocusIslandSpec(
                        bigTemplates = listOf(bigTemplate),
                        smallTemplate = MiFocusIslandSmallTemplate.CombinePic(
                            progress = MiFocusIslandProgress(72, "#2563EB", "#334155")
                        )
                    )
                )
            )
            val focusParam = bundle.getString("miui.focus.param").orEmpty()

            assertTrue(focusParam.contains(token), "Missing $token in $focusParam")
            assertTrue(
                focusParam.contains("combinePicInfo"),
                "Missing combinePicInfo in $focusParam"
            )
        }
    }

    @Test
    fun `expanded facade emits all supported expanded templates and actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val openPendingIntent = buildOpenPendingIntent(context)
        val action = MiFocusNotificationAction(
            key = "focus_test_open",
            title = "Open",
            pendingIntent = openPendingIntent,
            isHighlighted = true
        )
        val bundle = MiFocusNotificationTemplate.build(
            context = context,
            spec = baseSpec(
                expanded = MiFocusExpandedSpec(
                    components = listOf(
                        MiFocusExpandedComponent.Base(MiFocusExpandedText(title = "Base")),
                        MiFocusExpandedComponent.Chat(MiFocusExpandedText(title = "Chat")),
                        MiFocusExpandedComponent.Highlight(MiFocusExpandedText(title = "Highlight")),
                        MiFocusExpandedComponent.Hint(
                            text = MiFocusExpandedText(title = "Hint"),
                            action = action
                        ),
                        MiFocusExpandedComponent.Progress(MiFocusExpandedProgress(64, "#2563EB")),
                        MiFocusExpandedComponent.Picture(action = action),
                        MiFocusExpandedComponent.Background(color = "#101010"),
                        MiFocusExpandedComponent.Cover(
                            text = MiFocusExpandedText(title = "Cover"),
                            pic = MiFocusPictureRef.Expanded
                        ),
                        MiFocusExpandedComponent.HighlightV3(
                            text = MiFocusExpandedText(title = "V3"),
                            label = "HOT",
                            action = action
                        ),
                        MiFocusExpandedComponent.IconText(
                            text = MiFocusExpandedText(title = "Icon"),
                            icon = MiFocusAnimIcon()
                        ),
                        MiFocusExpandedComponent.MultiProgress(
                            progressPercent = 64,
                            color = "#2563EB",
                            points = 2
                        ),
                        MiFocusExpandedComponent.AnimText(
                            text = MiFocusExpandedText(title = "Anim"),
                            icon = MiFocusAnimIcon(),
                            timer = MiFocusTimer.countdown(1778000060000L, 1778000000000L)
                        ),
                        MiFocusExpandedComponent.TextButtons(listOf(action))
                    )
                )
            )
        )
        val focusParam = bundle.getString("miui.focus.param").orEmpty()
        val actionBundle = bundle.getBundle("miui.focus.actions")

        listOf(
            "baseInfo",
            "chatInfo",
            "highlightInfo",
            "hintInfo",
            "progressInfo",
            "picInfo",
            "bgInfo",
            "coverInfo",
            "highlightInfoV3",
            "iconTextInfo",
            "multiProgressInfo",
            "animTextInfo",
            "textButton"
        ).forEach { token ->
            assertTrue(focusParam.contains(token), "Missing $token in $focusParam")
        }
        assertNotNull(actionBundle?.getActionCompat("focus_test_open"))
    }

    private fun baseSpec(
        island: MiFocusIslandSpec = MiFocusIslandSpec.summaryText(title = "运行"),
        expanded: MiFocusExpandedSpec = MiFocusExpandedSpec.base("Title", "Content")
    ) = MiFocusNotificationSpec(
        title = "Title",
        content = "Content",
        displayIconResId = R.drawable.ic_kei_logo_island,
        island = island,
        expanded = expanded,
        outerGlow = true
    )

    private fun buildOpenPendingIntent(context: Application): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            9301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getActionCompat(key: String): Notification.Action {
        return getParcelable<Notification.Action>(key)
            ?: error("Missing focus action: $key")
    }
}

class MiFocusNotificationTemplateTestApp : Application()
