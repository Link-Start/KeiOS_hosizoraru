package os.kei.ui.page.main.settings.page

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal fun LazyListState.canMoveForSettingsChrome(deltaY: Float): Boolean {
    return when {
        deltaY < -1f -> canScrollForward
        deltaY > 1f -> canScrollBackward
        else -> true
    }
}

internal fun settingsChromeNestedScrollConnection(
    listState: LazyListState,
    delegate: NestedScrollConnection
): NestedScrollConnection {
    return object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!listState.canMoveForSettingsChrome(available.y)) return Offset.Zero
            return delegate.onPreScroll(available, source)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val canMove = listState.canMoveForSettingsChrome(consumed.y) ||
                    listState.canMoveForSettingsChrome(available.y)
            if (!canMove) return Offset.Zero
            return delegate.onPostScroll(consumed, available, source)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!listState.canMoveForSettingsChrome(available.y)) return Velocity.Zero
            return delegate.onPreFling(available)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val canMove = listState.canMoveForSettingsChrome(consumed.y) ||
                    listState.canMoveForSettingsChrome(available.y)
            if (!canMove) return Velocity.Zero
            return delegate.onPostFling(consumed, available)
        }
    }
}

internal fun settingsPagerSwitchDurationMillis(distance: Int): Int {
    return (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)
}

internal fun SettingsCategory.keepsChromeVisibleOnBounds(): Boolean {
    return this == SettingsCategory.Access || this == SettingsCategory.Notify
}
