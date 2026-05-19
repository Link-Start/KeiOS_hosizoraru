package os.kei.core.prefs

/**
 * Application theme mode selection.
 *
 * Extracted to a standalone file to reduce coupling on [UiPrefs] and enable future
 * `:core-prefs` module extraction without pulling in the full preferences surface.
 */
enum class AppThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}
