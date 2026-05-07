package os.kei.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for KeiOS.
 */
sealed interface KeiosRoute : NavKey {
    @Serializable
    data object Main : KeiosRoute

    @Serializable
    data object Settings : KeiosRoute

    @Serializable
    data object McpSkill : KeiosRoute

    @Serializable
    data object About : KeiosRoute

    @Serializable
    data class BaStudentGuide(
        val nonce: Long = 0L
    ) : KeiosRoute

    @Serializable
    data class BaGuideCatalog(
        val openBgmPlaybackToken: Long = 0L
    ) : KeiosRoute
}
