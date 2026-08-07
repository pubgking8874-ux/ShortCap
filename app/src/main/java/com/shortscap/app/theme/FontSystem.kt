package com.shortscap.app.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.shortscap.app.R
import com.shortscap.app.appearance.FontMode

/**
 * ScFonts — the centralized font configuration of ShortsCap.
 *
 * The selected family lives in snapshot state ([selectedFamily]) and every
 * text style in [ScTextStyles] reads it through a property getter, so the
 * moment [apply] runs the ENTIRE application re-renders in the new font —
 * Dashboard, Settings, Profile, Monitoring, Activity, Web, charts, reports,
 * navigation, dialogs, toasts and all future components. Nothing is assigned
 * per screen.
 *
 * Every family below is built from REAL static font files bundled in
 * `res/font` — one TTF per weight, declared plainly (no variable-font
 * variation settings). The platform therefore loads the exact face for the
 * requested weight on every device and every Compose version, which is what
 * makes selecting Nunito / Patrick Hand / Roboto / Times New Roman visibly
 * change the typography of the whole app.
 *
 * Licensing (all free for redistribution, verified):
 *  - Nunito — SIL Open Font License 1.1 (static instances generated from
 *    Google's official variable source with fonttools varLib.instancer).
 *  - Patrick Hand — SIL Open Font License 1.1.
 *  - Roboto — Apache License 2.0 (static instances generated from Google's
 *    official variable source with fonttools varLib.instancer).
 *  - "Times New Roman" is delivered as Tinos — Apache License 2.0, the
 *    metric-compatible open substitute — because the Microsoft font itself
 *    is proprietary and cannot be legally bundled.
 *
 * Multilingual coverage: the bundled families are Latin typefaces. Hindi
 * (Devanagari), Urdu (Arabic script) and Chinese (CJK) glyphs are handled by
 * Android's platform font fallback — a controlled, centralized fallback for
 * unsupported characters only. No language ever shows empty boxes, and no
 * per-screen fallback logic is needed.
 *
 * The preference itself is a UI preference stored independently from activity,
 * monitoring, web-blocking, auth and account data (see AppearanceRepository),
 * so a future backend can synchronize `UserPreferences.fontFamily` without
 * touching any other system.
 */
object ScFonts {

    /** The active font family — read by every [ScTextStyles] getter. */
    var selectedFamily by mutableStateOf<FontFamily>(FontFamily.Default)
        private set

    /** Applies [mode] globally — recomposes every screen instantly. */
    fun apply(mode: FontMode) {
        selectedFamily = ScFontFamilies.familyFor(mode)
    }
}

/**
 * Bundled font families for Settings → Appearance → Font.
 *
 * Each family maps a [FontWeight] to its REAL static TTF file. Weights that a
 * family genuinely does not ship (e.g. Tinos has only Regular + Bold, Patrick
 * Hand only Regular) are simply omitted — Compose picks the closest bundled
 * weight, exactly as with any professionally shipped app.
 */
object ScFontFamilies {

    /** Default / Simple — the original ShortsCap font (system default). */
    val Simple: FontFamily = FontFamily.Default

    /** Nunito — rounded, friendly sans; real static faces 400–800. */
    val Nunito: FontFamily = FontFamily(
        Font(R.font.nunito_regular, weight = FontWeight.Normal),
        Font(R.font.nunito_medium, weight = FontWeight.Medium),
        Font(R.font.nunito_semibold, weight = FontWeight.SemiBold),
        Font(R.font.nunito_bold, weight = FontWeight.Bold),
        Font(R.font.nunito_extrabold, weight = FontWeight.ExtraBold),
    )

    /** Patrick Hand — casual handwriting; ships its single Regular face. */
    val PatrickHand: FontFamily = FontFamily(
        Font(R.font.patrick_hand_regular, weight = FontWeight.Normal),
    )

    /** Roboto — the classic Android sans; real static faces 400–800. */
    val Roboto: FontFamily = FontFamily(
        Font(R.font.roboto_regular, weight = FontWeight.Normal),
        Font(R.font.roboto_medium, weight = FontWeight.Medium),
        Font(R.font.roboto_semibold, weight = FontWeight.SemiBold),
        Font(R.font.roboto_bold, weight = FontWeight.Bold),
        Font(R.font.roboto_extrabold, weight = FontWeight.ExtraBold),
    )

    /** Times New Roman look — Tinos (free, metric-compatible serif). */
    val TimesNewRoman: FontFamily = FontFamily(
        Font(R.font.tinos_regular, weight = FontWeight.Normal),
        Font(R.font.tinos_bold, weight = FontWeight.Bold),
    )

    /** Resolves the bundled family for a [FontMode] selection. */
    fun familyFor(mode: FontMode): FontFamily = when (mode) {
        FontMode.SIMPLE -> Simple
        FontMode.NUNITO -> Nunito
        FontMode.PATRICK_HAND -> PatrickHand
        FontMode.ROBOTO -> Roboto
        FontMode.TIMES_NEW_ROMAN -> TimesNewRoman
    }
}
