package com.shortscap.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Font family fallback chain approximates the RN `--font` stack:
 * Inter, SF Pro Display, -apple-system, Segoe UI, Roboto, sans-serif.
 * Bundle an Inter variable font under res/font/inter.ttf for pixel parity;
 * FontFamily.Default (Roboto) is used here as the safe fallback.
 */
val ScFontFamily = FontFamily.Default

object ScTextStyles {
    // .sc-h1 { font-size:22px; font-weight:700; letter-spacing:-0.3px; }
    val H1 = TextStyle(fontFamily = ScFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)

    // .sc-label { font-size:13px; color:var(--text2); }
    val Label = TextStyle(fontFamily = ScFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal)

    // .sc-section-title { font-size:13px; font-weight:600; uppercase; letter-spacing:0.4px; }
    val SectionTitle = TextStyle(fontFamily = ScFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)

    // .sc-logo-text { font-weight:700; font-size:16px; letter-spacing:-0.2px; }
    val LogoText = TextStyle(fontFamily = ScFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

    val Body = TextStyle(fontFamily = ScFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
    val BodySemiBold = TextStyle(fontFamily = ScFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontFamily = ScFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
    val ButtonLabel = TextStyle(fontFamily = ScFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val StatValue = TextStyle(fontFamily = ScFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val BigStat = TextStyle(fontFamily = ScFontFamily, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
}

val ScTypography = Typography(
    headlineSmall = ScTextStyles.H1,
    titleSmall = ScTextStyles.SectionTitle,
    bodyMedium = ScTextStyles.Body,
    bodySmall = ScTextStyles.Label,
    labelSmall = ScTextStyles.Caption,
)
