package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmAlbumFooter(
    sectionTitle: String,
    trackCount: Int,
    offlineTrackCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.ba_catalog_bgm_section_footer_current, sectionTitle),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight
        )
        Text(
            text = stringResource(
                R.string.ba_catalog_bgm_section_footer_state,
                trackCount,
                offlineTrackCount
            ),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight
        )
    }
}

@Composable
internal fun rememberBaGuideBgmDockSectionText(selectedDockKey: String): BaGuideBgmDockSectionText {
    val libraryLabel = stringResource(R.string.ba_catalog_bgm_nav_library)
    return when (selectedDockKey) {
        BaGuideBgmDockKeys.Home -> BaGuideBgmDockSectionText(
            heroTitle = stringResource(R.string.ba_catalog_bgm_section_home_title),
            heroMeta = stringResource(R.string.ba_catalog_bgm_section_home_meta),
            footerTitle = stringResource(R.string.ba_catalog_bgm_nav_home)
        )
        BaGuideBgmDockKeys.Discover -> BaGuideBgmDockSectionText(
            heroTitle = stringResource(R.string.ba_catalog_bgm_section_discover_title),
            heroMeta = stringResource(R.string.ba_catalog_bgm_section_discover_meta),
            footerTitle = stringResource(R.string.ba_catalog_bgm_nav_discover)
        )
        BaGuideBgmDockKeys.Radio -> BaGuideBgmDockSectionText(
            heroTitle = stringResource(R.string.ba_catalog_bgm_section_radio_title),
            heroMeta = stringResource(R.string.ba_catalog_bgm_section_radio_meta),
            footerTitle = stringResource(R.string.ba_catalog_bgm_nav_radio)
        )
        else -> BaGuideBgmDockSectionText(
            heroTitle = stringResource(R.string.ba_catalog_bgm_album_artist),
            heroMeta = stringResource(R.string.ba_catalog_bgm_album_meta),
            footerTitle = libraryLabel
        )
    }
}

internal data class BaGuideBgmDockSectionText(
    val heroTitle: String,
    val heroMeta: String,
    val footerTitle: String
)
