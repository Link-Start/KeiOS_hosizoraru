package os.kei.ui.page.main.widget.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.kei.R
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppAronaLoadingPanel(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
    showProgress: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.q_862c2944),
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showProgress) {
                    LiquidCircularProgressBar(
                        progress = null,
                        size = 18.dp,
                        strokeWidth = 2.dp,
                        activeColor = accent,
                        inactiveColor = accent.copy(alpha = 0.26f),
                        contentDescription = stringResource(R.string.ba_syncing),
                    )
                }
                Text(
                    text = stringResource(R.string.guide_loading_title),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
