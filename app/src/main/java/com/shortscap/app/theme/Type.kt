package com.shortscap.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ScFontFamily resolves to the ACTIVE global font (Settings → Appearance →
 * Font). Every [ScTextStyles] member is a GETTER that reads the current
 * family at use time, so switching the font re-renders the entire application
 * instantly — no per-screen changes, and future screens inherit it for free.
 */
val ScFontFamily: FontFamily
    get() = ScFonts.selectedFamily

object ScTextStyles {
    // .sc-h1 { font-size:22px; font-weight:700; letter-spacing:-0.3px; }
    val H1: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)

    // .sc-label { font-size:13px; color:var(--text2); }
    val Label: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal)

    // .sc-section-title { font-size:13px; font-weight:600; uppercase; letter-spacing:0.4px; }
    val SectionTitle: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)

    // .sc-logo-text { font-weight:700; font-size:16px; letter-spacing:-0.2px; }
    val LogoText: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

    val Body: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
    val BodySemiBold: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    val Caption: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
    val ButtonLabel: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val StatValue: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val BigStat: TextStyle get() = TextStyle(fontFamily = ScFontFamily, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
}

/**
 * Material3 typography resolved at ACCESS time so the active font also reaches
 * Material components that read the default text style (built per composition
 * inside ShortsCapTheme). Reading it registers the snapshot dependency, so the
 * theme re-provides typography the moment the font changes.
 */
val ScTypography: Typography
    get() = Typography(
        headlineSmall = ScTextStyles.H1,
        titleSmall = ScTextStyles.SectionTitle,
        bodyMedium = ScTextStyles.Body,
        bodySmall = ScTextStyles.Label,
        labelSmall = ScTextStyles.Caption,
    )
