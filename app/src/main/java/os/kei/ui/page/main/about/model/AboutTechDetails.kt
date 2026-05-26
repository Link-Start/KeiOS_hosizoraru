package os.kei.ui.page.main.about.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
internal enum class AboutInfoIcon {
    Alert,
    AppWindow,
    Config,
    Filter,
    Info,
    Layers,
    List,
    Lock,
    Media,
    Notes,
    Refresh,
    Settings,
    Version,
}

@Immutable
internal data class AboutInfoRowModel(
    @get:StringRes val titleRes: Int,
    val value: String,
    val icon: AboutInfoIcon,
)

@Immutable
internal data class AboutTechDetails(
    val buildRows: List<AboutInfoRowModel> = emptyList(),
    val uiRows: List<AboutInfoRowModel> = emptyList(),
    val networkRows: List<AboutInfoRowModel> = emptyList(),
    val mediaRows: List<AboutInfoRowModel> = emptyList(),
    val githubProjectUrl: String = "",
    val githubRows: List<AboutInfoRowModel> = emptyList(),
)
