package os.kei.ui.page.main.widget.core

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class AppTypographyToken(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight = FontWeight.Normal
)

object AppTypographyTokens {
    val Eyebrow = AppTypographyToken(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )

    val Caption = AppTypographyToken(
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val Supporting = AppTypographyToken(
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val Body = AppTypographyToken(
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val BodyEmphasis = AppTypographyToken(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )

    val CardHeader = AppTypographyToken(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    )

    val CompactTitle = AppTypographyToken(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    )

    val SectionTitle = AppTypographyToken(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
}
