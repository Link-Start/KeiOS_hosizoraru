package os.kei.ui.page.main.os

import android.graphics.Bitmap
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption

internal data class OsPageRuntimeState(
    val sectionStates: Map<SectionKind, SectionState> = defaultOsSectionStates(),
    val cacheLoaded: Boolean = false,
    val cachePersisted: Boolean = false,
    val uiStatePersistenceReady: Boolean = false,
    val refreshing: Boolean = false,
    val refreshProgress: Float = 0f,
    val runningShellCommandCardIds: Set<String> = emptySet(),
    val exportingCard: OsSectionCard? = null,
)

internal data class OsActivitySuggestionUiState(
    val packageSuggestions: List<ShortcutInstalledAppOption> = emptyList(),
    val packageSuggestionsLoading: Boolean = false,
    val classSuggestions: List<ShortcutActivityClassOption> = emptyList(),
    val classSuggestionsLoading: Boolean = false,
)

internal data class OsActivityShortcutIconUiState(
    val bitmaps: Map<String, Bitmap> = emptyMap(),
    val missingKeys: Set<String> = emptySet(),
    val packageBitmaps: Map<String, Bitmap> = emptyMap(),
    val missingPackages: Set<String> = emptySet(),
)

internal fun defaultOsSectionStates(): Map<SectionKind, SectionState> =
    mapOf(
        SectionKind.SYSTEM to SectionState(),
        SectionKind.SECURE to SectionState(),
        SectionKind.GLOBAL to SectionState(),
        SectionKind.ANDROID to SectionState(),
        SectionKind.JAVA to SectionState(),
        SectionKind.LINUX to SectionState(),
    )
