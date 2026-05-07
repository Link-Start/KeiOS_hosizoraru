package os.kei.ui.page.main.student

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import os.kei.R
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmQueueMode

internal const val BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT =
    "os.kei.ba.bgm.media.TOGGLE_REPEAT"
internal const val BA_GUIDE_BGM_COMMAND_OPEN_PLAYER =
    "os.kei.ba.bgm.media.OPEN_PLAYER"

@OptIn(UnstableApi::class)
internal object BaGuideBgmMediaButtonPreferences {
    fun repeatCommand(): SessionCommand {
        return SessionCommand(BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT, Bundle.EMPTY)
    }

    fun openPlayerCommand(): SessionCommand {
        return SessionCommand(BA_GUIDE_BGM_COMMAND_OPEN_PLAYER, Bundle.EMPTY)
    }

    fun availableSessionCommands(
        base: SessionCommands
    ): SessionCommands {
        return base.buildUpon()
            .add(repeatCommand())
            .add(openPlayerCommand())
            .build()
    }

    fun mediaButtonPreferences(
        context: Context,
        queueMode: BaGuideBgmQueueMode
    ): List<CommandButton> {
        return listOf(
            repeatButton(context, queueMode),
            openPlayerButton(context)
        )
    }

    fun queueModeFromNativeRepeatMode(repeatMode: Int): BaGuideBgmQueueMode {
        return if (repeatMode == Player.REPEAT_MODE_ONE) {
            BaGuideBgmQueueMode.SingleLoop
        } else {
            BaGuideBgmQueueMode.Continuous
        }
    }

    fun nativeRepeatMode(queueMode: BaGuideBgmQueueMode): Int {
        return if (queueMode == BaGuideBgmQueueMode.SingleLoop) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_ALL
        }
    }

    fun nextQueueMode(queueMode: BaGuideBgmQueueMode): BaGuideBgmQueueMode {
        return if (queueMode == BaGuideBgmQueueMode.Continuous) {
            BaGuideBgmQueueMode.SingleLoop
        } else {
            BaGuideBgmQueueMode.Continuous
        }
    }

    private fun repeatButton(
        context: Context,
        queueMode: BaGuideBgmQueueMode
    ): CommandButton {
        val icon = if (queueMode == BaGuideBgmQueueMode.SingleLoop) {
            CommandButton.ICON_REPEAT_ONE
        } else {
            CommandButton.ICON_REPEAT_ALL
        }
        return CommandButton.Builder(icon)
            .setSessionCommand(repeatCommand())
            .setDisplayName(context.getString(queueMode.labelRes))
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }

    private fun openPlayerButton(context: Context): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_QUEUE_NEXT)
            .setSessionCommand(openPlayerCommand())
            .setDisplayName(context.getString(R.string.ba_catalog_bgm_action_open_player))
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build()
    }
}
