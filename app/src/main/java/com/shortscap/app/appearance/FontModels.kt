package com.shortscap.app.appearance

/**
 * Global font-family preference (Settings → Appearance → Font).
 *
 * Each option maps to a bundled font (or the system default) in
 * [com.shortscap.app.theme.ScFonts]; the active family is applied app-wide
 * through the centralized typography system — every
 * [com.shortscap.app.theme.ScTextStyles] member reads the current family
 * through a getter, so Dashboard, Settings, Profile, Monitoring, Activity,
 * Web, charts, dialogs, toasts and all future screens re-render in the
 * selected font instantly, with zero per-screen changes.
 *
 * The five options:
 *  - [SIMPLE] — Default / Simple: restores the original ShortsCap font
 *    (the Android system default).
 *  - [NUNITO] — the bundled Nunito static font family (Regular–ExtraBold,
 *    generated from Google's official variable source).
 *  - [PATRICK_HAND] — the bundled Patrick Hand handwriting font.
 *  - [ROBOTO] — the bundled Roboto static font family (Regular–ExtraBold,
 *    generated from Google's official variable source).
 *  - [TIMES_NEW_ROMAN] — a serif face metrically identical to Times New
 *    Roman (Tinos is bundled, since Times New Roman itself is a licensed
 *    Microsoft font and not freely distributable).
 *
 * Multilingual coverage: the families are Latin typefaces. Hindi
 * (Devanagari), Urdu (Arabic script) and Chinese (CJK) glyphs are rendered
 * by Android's platform font fallback — a centralized, system-level fallback
 * for unsupported characters only, so no language ever shows empty boxes or
 * broken glyphs and the fallback never needs per-screen handling.
 *
 * Font selection is independent from the Language setting: changing one
 * never affects the other. [SIMPLE] is the default (matching the original
 * design). The enum maps 1:1 to a future backend
 * `UserPreferences.fontFamily` entry.
 */
enum class FontMode {
    SIMPLE,
    NUNITO,
    PATRICK_HAND,
    ROBOTO,
    TIMES_NEW_ROMAN;

    companion object {
        /** Default family — first install and after Reset All Settings. */
        val DEFAULT: FontMode = SIMPLE
    }
}
